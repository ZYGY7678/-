package com.mitmachim.applocker;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;

public class AppLockAccessibilityService extends AccessibilityService {
    private String lastPackage="";

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if(event==null) return;
        CharSequence pkg=event.getPackageName();
        if(pkg==null) return;
        enforce(pkg.toString());
    }

    private void enforce(String packageName) {
        if(!Prefs.isEnabled(this) || !Prefs.hasPin(this)) return;
        if(packageName.length()==0 || packageName.equals(getPackageName())
                || packageName.equals("com.android.systemui")
                || packageName.equals("com.android.launcher")
                || packageName.equals("com.android.launcher2")) return;

        if(Prefs.isGlobalUnlock(this) && Prefs.isGlobalUnlockActive(this)) return;

        String temporary=Prefs.getTemporaryUnlock(this);
        if(!temporary.isEmpty() && temporary.equals(packageName)) {
            lastPackage=packageName;
            return;
        }
        if(!temporary.isEmpty() && !temporary.equals(packageName)) {
            Prefs.setTemporaryUnlock(this,"");
        }

        boolean shouldLock;
        if(Prefs.MODE_ALL_EXCEPT.equals(Prefs.getMode(this))) {
            shouldLock=!Prefs.getExcludedApps(this).contains(packageName);
        } else {
            shouldLock=Prefs.getLockedApps(this).contains(packageName);
        }

        if(shouldLock && !packageName.equals(lastPackage)) {
            lastPackage=packageName;
            Intent lock=new Intent(this,LockActivity.class);
            lock.putExtra("package",packageName);
            lock.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(lock);
        } else if(!shouldLock) {
            lastPackage=packageName;
        }
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
    }

    @Override public void onInterrupt() {}

    @Override public boolean onUnbind(Intent intent) {
        lastPackage="";
        return super.onUnbind(intent);
    }
}
