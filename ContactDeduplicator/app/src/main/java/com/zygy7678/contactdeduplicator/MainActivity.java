package com.zygy7678.contactdeduplicator;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQUEST_CONTACTS = 42;

    private final List<ContactInfo> duplicateGroups = new ArrayList<ContactInfo>();
    private LinearLayout listLayout;
    private TextView status;
    private Button mergeButton;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        buildUi();

        if (Build.VERSION.SDK_INT >= 23 &&
                (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED ||
                 checkSelfPermission(Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(new String[] {
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_CONTACTS
            }, REQUEST_CONTACTS);
        } else {
            scan();
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        TextView title = new TextView(this);
        title.setText("מנקה אנשי קשר כפולים");
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView rule = new TextView(this);
        rule.setText("כפילויות נקבעות לפי מספר טלפון זהה, גם כאשר השמות שונים. בעת מיזוג נשמרים פרטי שני אנשי הקשר.");
        rule.setTextSize(16);
        rule.setPadding(0, 20, 0, 20);
        root.addView(rule, new LinearLayout.LayoutParams(-1, -2));

        Button scanButton = new Button(this);
        scanButton.setText("מצא כפילויות");
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                scan();
            }
        });
        root.addView(scanButton, new LinearLayout.LayoutParams(-1, -2));

        mergeButton = new Button(this);
        mergeButton.setText("מזג את כל הכפילויות");
        mergeButton.setEnabled(false);
        mergeButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mergeAll();
            }
        });
        root.addView(mergeButton, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setTextSize(16);
        status.setPadding(0, 20, 0, 10);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listLayout, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CONTACTS) {
            boolean ok = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                scan();
            } else {
                status.setText("נדרשת הרשאת אנשי קשר כדי למצוא ולמזג כפילויות.");
            }
        }
    }

    private void scan() {
        new Thread(new Runnable() {
            @Override public void run() {
                final List<ContactInfo> groups = findDuplicateGroups();
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        duplicateGroups.clear();
                        duplicateGroups.addAll(groups);
                        renderGroups();
                    }
                });
            }
        }).start();
    }

    private List<ContactInfo> findDuplicateGroups() {
        ContentResolver resolver = getContentResolver();
        Map<Long, ContactInfo> contacts = new HashMap<Long, ContactInfo>();
        Map<String, Set<Long>> phoneBuckets = new HashMap<String, Set<Long>>();

        Cursor cursor = resolver.query(
                ContactsContract.Data.CONTENT_URI,
                new String[] {
                        ContactsContract.Data.CONTACT_ID,
                        ContactsContract.Data.RAW_CONTACT_ID,
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.Data.DATA1,
                        ContactsContract.Contacts.DISPLAY_NAME
                },
                ContactsContract.Data.MIMETYPE + "=?",
                new String[] { ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE },
                null
        );

        if (cursor == null) return new ArrayList<ContactInfo>();

        try {
            while (cursor.moveToNext()) {
                long contactId = cursor.getLong(0);
                String number = cursor.getString(3);
                String name = cursor.getString(4);
                if (number == null || number.trim().length() == 0) continue;

                ContactInfo info = contacts.get(contactId);
                if (info == null) {
                    info = new ContactInfo(contactId, name == null ? "" : name);
                    contacts.put(contactId, info);
                }

                if (!containsNumber(info.numbers, number)) {
                    info.numbers.add(number);
                }

                for (String key : phoneKeys(number)) {
                    Set<Long> ids = phoneBuckets.get(key);
                    if (ids == null) {
                        ids = new HashSet<Long>();
                        phoneBuckets.put(key, ids);
                    }
                    ids.add(contactId);
                }
            }
        } finally {
            cursor.close();
        }

        List<ContactInfo> all = new ArrayList<ContactInfo>(contacts.values());
        if (all.size() < 2) return new ArrayList<ContactInfo>();

        UnionFind uf = new UnionFind(all.size());
        Map<Long, Integer> indexByContactId = new HashMap<Long, Integer>();

        for (int i = 0; i < all.size(); i++) {
            indexByContactId.put(all.get(i).id, i);
        }

        // First pass: exact canonical matches. This catches formatting differences
        // such as spaces, dashes, parentheses, +972 vs 00972, and Israeli 05x vs +9725x.
        for (Set<Long> ids : phoneBuckets.values()) {
            if (ids.size() < 2) continue;
            Long first = null;
            for (Long id : ids) {
                if (first == null) {
                    first = id;
                } else {
                    Integer a = indexByContactId.get(first);
                    Integer b = indexByContactId.get(id);
                    if (a != null && b != null) uf.union(a, b);
                }
            }
        }

        // Second pass: Android's own phone-number equivalence, restricted to
        // contacts sharing a useful digit suffix so large contact lists stay fast.
        Map<String, List<Integer>> suffixBuckets = new HashMap<String, List<Integer>>();
        for (int i = 0; i < all.size(); i++) {
            for (String number : all.get(i).numbers) {
                String digits = digitsOnly(number);
                if (digits.length() < 7) continue;
                String suffix = digits.substring(digits.length() - 7);
                List<Integer> ids = suffixBuckets.get(suffix);
                if (ids == null) {
                    ids = new ArrayList<Integer>();
                    suffixBuckets.put(suffix, ids);
                }
                if (!ids.contains(i)) ids.add(i);
            }
        }

        for (List<Integer> ids : suffixBuckets.values()) {
            if (ids.size() < 2) continue;
            for (int a = 0; a < ids.size(); a++) {
                ContactInfo first = all.get(ids.get(a));
                for (int b = a + 1; b < ids.size(); b++) {
                    ContactInfo second = all.get(ids.get(b));
                    if (sharePhone(first, second)) {
                        uf.union(ids.get(a), ids.get(b));
                    }
                }
            }
        }

        Map<Integer, ContactInfo> grouped = new HashMap<Integer, ContactInfo>();
        Map<Integer, Integer> counts = new HashMap<Integer, Integer>();

        for (int i = 0; i < all.size(); i++) {
            int root = uf.find(i);
            ContactInfo group = grouped.get(root);
            if (group == null) {
                group = new ContactInfo(-1, "");
                grouped.put(root, group);
                counts.put(root, 0);
            }
            group.memberIds.add(all.get(i).id);
            group.memberNames.add(all.get(i).name);
            group.numbers.addAll(all.get(i).numbers);
            counts.put(root, counts.get(root) + 1);
        }

        List<ContactInfo> result = new ArrayList<ContactInfo>();
        for (Map.Entry<Integer, ContactInfo> entry : grouped.entrySet()) {
            Integer count = counts.get(entry.getKey());
            if (count != null && count > 1) {
                ContactInfo g = entry.getValue();
                Collections.sort(g.memberNames);
                result.add(g);
            }
        }

        Collections.sort(result, new java.util.Comparator<ContactInfo>() {
            @Override public int compare(ContactInfo a, ContactInfo b) {
                return b.memberIds.size() - a.memberIds.size();
            }
        });
        return result;
    }

    private boolean containsNumber(List<String> numbers, String candidate) {
        for (String number : numbers) {
            if (PhoneNumberUtils.compare(number, candidate)) return true;
            if (canonicalPhone(number).equals(canonicalPhone(candidate))) return true;
        }
        return false;
    }

    private List<String> phoneKeys(String number) {
        List<String> keys = new ArrayList<String>();
        String digits = digitsOnly(number);
        String canonical = canonicalPhone(number);
        if (digits.length() > 0 && !keys.contains(digits)) keys.add(digits);
        if (canonical.length() > 0 && !keys.contains(canonical)) keys.add(canonical);

        // International dialing form without the international access prefix.
        if (digits.startsWith("00") && digits.length() > 2) {
            String k = digits.substring(2);
            if (!keys.contains(k)) keys.add(k);
        }

        return keys;
    }

    private String canonicalPhone(String number) {
        String digits = digitsOnly(number);
        if (digits.length() == 0) return "";

        if (digits.startsWith("00") && digits.length() > 2) {
            digits = digits.substring(2);
        }

        // Israel: treat 05x1234567 and +972 5x1234567 as the same number.
        if (digits.startsWith("0") && digits.length() == 10) {
            return "972" + digits.substring(1);
        }
        if (digits.startsWith("972") && digits.length() == 12) {
            return digits;
        }

        return digits;
    }

    private String digitsOnly(String number) {
        String digits = PhoneNumberUtils.normalizeNumber(number);
        if (digits == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            if (c >= '0' && c <= '9') out.append(c);
        }
        return out.toString();
    }

    private boolean sharePhone(ContactInfo a, ContactInfo b) {
        for (String first : a.numbers) {
            for (String second : b.numbers) {
                if (PhoneNumberUtils.compare(first, second)) return true;
                if (canonicalPhone(first).equals(canonicalPhone(second))) return true;
            }
        }
        return false;
    }

    private void renderGroups() {
        listLayout.removeAllViews();

        if (duplicateGroups.isEmpty()) {
            status.setText("לא נמצאו קבוצות כפולות.");
            mergeButton.setEnabled(false);
            return;
        }

        status.setText("נמצאו " + duplicateGroups.size() + " קבוצות כפולות.");
        mergeButton.setEnabled(true);

        int index = 1;
        for (ContactInfo group : duplicateGroups) {
            TextView row = new TextView(this);
            row.setText(index + ". " + joinNames(group.memberNames) +
                    "\nטלפונים: " + joinUniqueNumbers(group.numbers) +
                    "\nאנשי קשר בקבוצה: " + group.memberIds.size());
            row.setTextSize(16);
            row.setPadding(0, 18, 0, 18);
            listLayout.addView(row, new LinearLayout.LayoutParams(-1, -2));
            index++;
        }
    }

    private String joinNames(List<String> names) {
        StringBuilder out = new StringBuilder();
        Set<String> seen = new HashSet<String>();
        for (String name : names) {
            if (name == null) continue;
            String n = name.trim();
            if (n.length() == 0 || seen.contains(n)) continue;
            if (out.length() > 0) out.append(" / ");
            out.append(n);
            seen.add(n);
        }
        return out.length() == 0 ? "ללא שם" : out.toString();
    }

    private String joinUniqueNumbers(List<String> numbers) {
        StringBuilder out = new StringBuilder();
        Set<String> seen = new HashSet<String>();
        for (String num : numbers) {
            if (num == null) continue;
            if (seen.add(num)) {
                if (out.length() > 0) out.append(", ");
                out.append(num);
            }
        }
        return out.toString();
    }

    private void mergeAll() {
        mergeButton.setEnabled(false);
        status.setText("ממזג אנשי קשר...");

        new Thread(new Runnable() {
            @Override public void run() {
                int mergedGroups = 0;
                int failedGroups = 0;

                for (ContactInfo group : new ArrayList<ContactInfo>(duplicateGroups)) {
                    try {
                        if (mergeGroup(group)) mergedGroups++;
                        else failedGroups++;
                    } catch (Exception e) {
                        failedGroups++;
                    }
                }

                final int ok = mergedGroups;
                final int fail = failedGroups;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        Toast.makeText(MainActivity.this,
                                "מיזוג הסתיים: " + ok + " קבוצות. כשלונות: " + fail,
                                Toast.LENGTH_LONG).show();
                        scan();
                    }
                });
            }
        }).start();
    }

    private boolean mergeGroup(ContactInfo group) {
        if (group.memberIds.size() < 2) return false;

        ContentResolver resolver = getContentResolver();
        List<Long> rawIds = new ArrayList<Long>();

        for (Long contactId : group.memberIds) {
            Cursor c = resolver.query(
                    ContactsContract.RawContacts.CONTENT_URI,
                    new String[] { ContactsContract.RawContacts._ID },
                    ContactsContract.RawContacts.CONTACT_ID + "=?",
                    new String[] { String.valueOf(contactId) },
                    null
            );
            if (c == null) continue;
            try {
                while (c.moveToNext()) rawIds.add(c.getLong(0));
            } finally {
                c.close();
            }
        }

        if (rawIds.size() < 2) return false;

        long targetRawId = rawIds.get(0);
        Set<String> existing = new HashSet<String>();
        Cursor targetData = resolver.query(
                ContactsContract.Data.CONTENT_URI,
                dataProjection(),
                ContactsContract.Data.RAW_CONTACT_ID + "=?",
                new String[] { String.valueOf(targetRawId) },
                null
        );

        if (targetData != null) {
            try {
                while (targetData.moveToNext()) existing.add(dataSignature(targetData));
            } finally {
                targetData.close();
            }
        }

        List<String> sourceNames = new ArrayList<String>();
        for (Long contactId : group.memberIds) {
            Cursor c = resolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    new String[] { ContactsContract.Contacts.DISPLAY_NAME },
                    ContactsContract.Contacts._ID + "=?",
                    new String[] { String.valueOf(contactId) },
                    null
            );
            if (c != null) {
                try {
                    if (c.moveToFirst()) {
                        String name = c.getString(0);
                        if (name != null && name.trim().length() > 0) sourceNames.add(name.trim());
                    }
                } finally {
                    c.close();
                }
            }
        }

        for (Long rawId : rawIds) {
            if (rawId == targetRawId) continue;

            Cursor data = resolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    dataProjection(),
                    ContactsContract.Data.RAW_CONTACT_ID + "=?",
                    new String[] { String.valueOf(rawId) },
                    null
            );
            if (data == null) continue;

            try {
                while (data.moveToNext()) {
                    String mime = data.getString(data.getColumnIndex(ContactsContract.Data.MIMETYPE));
                    if (mime == null) continue;
                    if (ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE.equals(mime)) {
                        continue;
                    }

                    String signature = dataSignature(data);
                    if (existing.contains(signature)) continue;

                    ContentValues values = copyDataValues(data, targetRawId);
                    try {
                        resolver.insert(ContactsContract.Data.CONTENT_URI, values);
                        existing.add(signature);
                    } catch (Exception ignored) {
                        // Some account/provider rows are read-only. Keep processing the rest.
                    }
                }
            } finally {
                data.close();
            }
        }

        String mergedName = joinNames(sourceNames);
        if (!"ללא שם".equals(mergedName)) {
            Cursor nameCursor = resolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    new String[] { ContactsContract.Data._ID },
                    ContactsContract.Data.RAW_CONTACT_ID + "=? AND " +
                            ContactsContract.Data.MIMETYPE + "=?",
                    new String[] {
                            String.valueOf(targetRawId),
                            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                    },
                    null
            );

            boolean updated = false;
            if (nameCursor != null) {
                try {
                    if (nameCursor.moveToFirst()) {
                        long dataId = nameCursor.getLong(0);
                        ContentValues values = new ContentValues();
                        values.put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, mergedName);
                        resolver.update(
                                ContactsContract.Data.CONTENT_URI,
                                values,
                                ContactsContract.Data._ID + "=?",
                                new String[] { String.valueOf(dataId) }
                        );
                        updated = true;
                    }
                } finally {
                    nameCursor.close();
                }
            }

            if (!updated) {
                ContentValues values = new ContentValues();
                values.put(ContactsContract.Data.RAW_CONTACT_ID, targetRawId);
                values.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
                values.put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, mergedName);
                resolver.insert(ContactsContract.Data.CONTENT_URI, values);
            }
        }

        boolean deletedAny = false;
        for (Long rawId : rawIds) {
            if (rawId == targetRawId) continue;
            try {
                int deleted = resolver.delete(
                        ContactsContract.RawContacts.CONTENT_URI,
                        ContactsContract.RawContacts._ID + "=?",
                        new String[] { String.valueOf(rawId) }
                );
                if (deleted > 0) deletedAny = true;
            } catch (Exception ignored) {
                // Keep the merged target if this account does not permit deletion.
            }
        }

        return deletedAny;
    }

    private String[] dataProjection() {
        return new String[] {
                ContactsContract.Data._ID,
                ContactsContract.Data.MIMETYPE,
                ContactsContract.Data.DATA1,
                ContactsContract.Data.DATA2,
                ContactsContract.Data.DATA3,
                ContactsContract.Data.DATA4,
                ContactsContract.Data.DATA5,
                ContactsContract.Data.DATA6,
                ContactsContract.Data.DATA7,
                ContactsContract.Data.DATA8,
                ContactsContract.Data.DATA9,
                ContactsContract.Data.DATA10,
                ContactsContract.Data.DATA11,
                ContactsContract.Data.DATA12,
                ContactsContract.Data.DATA13,
                ContactsContract.Data.DATA14,
                ContactsContract.Data.DATA15
        };
    }

    private String dataSignature(Cursor c) {
        StringBuilder s = new StringBuilder();
        int mimeIndex = c.getColumnIndex(ContactsContract.Data.MIMETYPE);
        s.append(mimeIndex >= 0 ? c.getString(mimeIndex) : "").append("|");

        for (int i = 1; i <= 15; i++) {
            int idx = c.getColumnIndex("data" + i);
            if (idx >= 0) {
                s.append(c.getString(idx));
            }
            s.append("|");
        }
        return s.toString();
    }

    private ContentValues copyDataValues(Cursor c, long targetRawId) {
        ContentValues values = new ContentValues();
        values.put(ContactsContract.Data.RAW_CONTACT_ID, targetRawId);

        int mimeIndex = c.getColumnIndex(ContactsContract.Data.MIMETYPE);
        values.put(ContactsContract.Data.MIMETYPE, c.getString(mimeIndex));

        for (int i = 1; i <= 15; i++) {
            int idx = c.getColumnIndex("data" + i);
            if (idx < 0) continue;

            String value = c.getString(idx);
            if (value != null) values.put("data" + i, value);
        }

        return values;
    }

    private static class ContactInfo {
        final long id;
        final String name;
        final List<String> numbers = new ArrayList<String>();
        final List<Long> memberIds = new ArrayList<Long>();
        final List<String> memberNames = new ArrayList<String>();

        ContactInfo(long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static class UnionFind {
        private final int[] parent;
        private final int[] rank;

        UnionFind(int n) {
            parent = new int[n];
            rank = new int[n];
            for (int i = 0; i < n; i++) parent[i] = i;
        }

        int find(int x) {
            while (parent[x] != x) {
                parent[x] = parent[parent[x]];
                x = parent[x];
            }
            return x;
        }

        void union(int a, int b) {
            int ra = find(a);
            int rb = find(b);
            if (ra == rb) return;

            if (rank[ra] < rank[rb]) {
                parent[ra] = rb;
            } else if (rank[ra] > rank[rb]) {
                parent[rb] = ra;
            } else {
                parent[rb] = ra;
                rank[ra]++;
            }
        }
    }
}
