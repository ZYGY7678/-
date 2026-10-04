package com.mitmachim.applocker;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityManager;
import java.util.List;

public final class AccessibilityUtil {
    private AccessibilityUtil() {}

    public static boolean isEnabled(Context context) {
        try {
            AccessibilityManager am=(AccessibilityManager)context.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if(am==null || !am.isEnabled()) return false;
            List<AccessibilityServiceInfo> services=am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            String wanted=new ComponentName(context, AppLockAccessibilityService.class).flattenToString();
            for(AccessibilityServiceInfo info:services) {
                if(info.getResolveInfo()!=null && info.getResolveInfo().serviceInfo!=null) {
                    ComponentName cn=new ComponentName(info.getResolveInfo().serviceInfo.packageName,
                            info.getResolveInfo().serviceInfo.name);
                    if(wanted.equals(cn.flattenToString())) return true;
                }
            }
        } catch(Throwable ignored) {}
        return false;
    }

    public static void openSettings(Context context) {
        try {
            context.startActivity(new android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch(Throwable ignored) {}
    }
}
