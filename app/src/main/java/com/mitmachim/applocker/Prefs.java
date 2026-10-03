package com.mitmachim.applocker;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

public final class Prefs {
    private static final String NAME = "locker_prefs";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_MODE = "mode";
    private static final String KEY_LOCKED_APPS = "locked_apps";
    private static final String KEY_EXCLUDED_APPS = "excluded_apps";
    private static final String KEY_TEMP_UNLOCK = "temp_unlock";
    private static final String KEY_AUTO_PIN = "auto_pin";
    public static final String MODE_SELECTED = "selected";
    public static final String MODE_ALL_EXCEPT = "all_except";
    private Prefs() {}
    private static SharedPreferences sp(Context c) { return c.getSharedPreferences(NAME, Context.MODE_PRIVATE); }
    public static boolean hasPin(Context c) { return sp(c).contains(KEY_PIN_HASH); }
    public static void setPinHash(Context c, String hash) { sp(c).edit().putString(KEY_PIN_HASH, hash).apply(); }
    public static String getPinHash(Context c) { return sp(c).getString(KEY_PIN_HASH, ""); }
    public static boolean isEnabled(Context c) { return sp(c).getBoolean(KEY_ENABLED, true); }
    public static void setEnabled(Context c, boolean value) { sp(c).edit().putBoolean(KEY_ENABLED, value).apply(); }
    public static String getMode(Context c) { return sp(c).getString(KEY_MODE, MODE_SELECTED); }
    public static void setMode(Context c, String mode) { sp(c).edit().putString(KEY_MODE, mode).apply(); }
    public static Set<String> getLockedApps(Context c) { return new HashSet<String>(sp(c).getStringSet(KEY_LOCKED_APPS, new HashSet<String>())); }
    public static void setLockedApps(Context c, Set<String> apps) { sp(c).edit().putStringSet(KEY_LOCKED_APPS, new HashSet<String>(apps)).apply(); }
    public static Set<String> getExcludedApps(Context c) { return new HashSet<String>(sp(c).getStringSet(KEY_EXCLUDED_APPS, new HashSet<String>())); }
    public static void setExcludedApps(Context c, Set<String> apps) { sp(c).edit().putStringSet(KEY_EXCLUDED_APPS, new HashSet<String>(apps)).apply(); }
    public static Set<String> getActiveAppSet(Context c) { return MODE_ALL_EXCEPT.equals(getMode(c)) ? getExcludedApps(c) : getLockedApps(c); }
    public static void setActiveAppSet(Context c, Set<String> apps) { if (MODE_ALL_EXCEPT.equals(getMode(c))) setExcludedApps(c, apps); else setLockedApps(c, apps); }
    public static String getTemporaryUnlock(Context c) { return sp(c).getString(KEY_TEMP_UNLOCK, ""); }
    public static void setTemporaryUnlock(Context c, String pkg) { sp(c).edit().putString(KEY_TEMP_UNLOCK, pkg == null ? "" : pkg).apply(); }
    public static boolean isAutoPin(Context c) { return sp(c).getBoolean(KEY_AUTO_PIN, true); }
    public static void setAutoPin(Context c, boolean value) { sp(c).edit().putBoolean(KEY_AUTO_PIN, value).apply(); }
}
