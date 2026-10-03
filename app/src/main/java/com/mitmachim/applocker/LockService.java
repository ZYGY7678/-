package com.mitmachim.applocker;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import java.util.Set;

public class LockService extends Service {
    private static final int NOTIFICATION_ID=44;
    private final Handler handler=new Handler();
    private String lastTopPackage="";
    private final Runnable checker=new Runnable(){@Override public void run(){enforceLock();handler.postDelayed(this,400L);}};
    public static void ensureRunning(Context c){Intent i=new Intent(c,LockService.class);if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);}
    public static void stop(Context c){c.stopService(new Intent(c,LockService.class));}
    public static void grantTemporaryUnlock(Context c,String packageName){Prefs.setTemporaryUnlock(c,packageName);ensureRunning(c);}
    @Override public void onCreate(){super.onCreate();startAsForeground();handler.post(checker);}
    private void startAsForeground(){
        Intent launch=new Intent(this,MainActivity.class);
        PendingIntent pending=PendingIntent.getActivity(this,0,launch,PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b;
        if(Build.VERSION.SDK_INT>=26){
            NotificationManager nm=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
            android.app.NotificationChannel ch=new android.app.NotificationChannel("locker","נעילת אפליקציות",NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
            b=new Notification.Builder(this,"locker");
        } else {
            b=new Notification.Builder(this);
        }
        b.setSmallIcon(android.R.drawable.ic_lock_lock)
         .setContentTitle(getString(R.string.service_title))
         .setContentText(getString(R.string.service_text))
         .setContentIntent(pending)
         .setOngoing(true);
        startForeground(NOTIFICATION_ID,b.build());

        // On Android 4.4 and earlier, the foreground-service notification can be
        // removed after the service has been started. Newer Android versions
        // may require a visible foreground-service notification.
        if(Build.VERSION.SDK_INT < 26 && !Prefs.isShowNotification(this)){
            stopForeground(true);
        }
    }
    private void enforceLock(){
        if(!Prefs.isEnabled(this)||!Prefs.hasPin(this))return;
        if(Prefs.isGlobalUnlock(this) && Prefs.isGlobalUnlockActive(this)) return;
        ActivityManager am=(ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
        try{
            ActivityManager.RunningTaskInfo task=am.getRunningTasks(1).get(0);String top=task.topActivity==null?"":task.topActivity.getPackageName();String own=getPackageName();
            if(own.equals(top))return;
            String temporary=Prefs.getTemporaryUnlock(this);
            if(!temporary.isEmpty()&&temporary.equals(top)){lastTopPackage=top;return;}
            if(!temporary.isEmpty()&&!temporary.equals(top))Prefs.setTemporaryUnlock(this,"");
            if(shouldLock(top)){if(!top.equals(lastTopPackage)){lastTopPackage=top;Intent lock=new Intent(this,LockActivity.class);lock.putExtra("package",top);lock.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(lock);}}
            else lastTopPackage=top;
        }catch(Throwable ignored){}
    }
    private boolean shouldLock(String packageName){
        if(packageName==null||packageName.length()==0)return false;
        if(packageName.equals(getPackageName()))return false;
        if(packageName.equals("com.android.systemui"))return false;
        if(packageName.equals("com.android.launcher"))return false;
        if(packageName.equals("com.android.launcher2"))return false;
        if(Prefs.MODE_ALL_EXCEPT.equals(Prefs.getMode(this)))return !Prefs.getExcludedApps(this).contains(packageName);
        return Prefs.getLockedApps(this).contains(packageName);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){return START_STICKY;}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}