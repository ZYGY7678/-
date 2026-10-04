package com.mitmachim.applocker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.CheckBox;

public class MainActivity extends Activity {
    private TextView status;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        status=(TextView)findViewById(R.id.statusText);

        CheckBox globalUnlock=(CheckBox)findViewById(R.id.globalUnlockCheck);
        final TextView globalTimer=(TextView)findViewById(R.id.globalUnlockTimerText);
        CheckBox showNotification=(CheckBox)findViewById(R.id.showNotificationCheck);

        globalUnlock.setChecked(Prefs.isGlobalUnlock(this));
        globalUnlock.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener(){
            public void onCheckedChanged(android.widget.CompoundButton b, boolean checked){
                Prefs.setGlobalUnlock(MainActivity.this,checked);
            }
        });

        showNotification.setChecked(Prefs.isShowNotification(this));
        showNotification.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener(){
            public void onCheckedChanged(android.widget.CompoundButton b, boolean checked){
                Prefs.setShowNotification(MainActivity.this,checked);
                if(Prefs.hasPin(MainActivity.this)&&Prefs.isEnabled(MainActivity.this)&&!AccessibilityUtil.isEnabled(MainActivity.this)){
                    LockService.stop(MainActivity.this);
                    LockService.ensureRunning(MainActivity.this);
                }
            }
        });

        ((Button)findViewById(R.id.accessibilityButton)).setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View v){ AccessibilityUtil.openSettings(MainActivity.this); }
        });

        if(Prefs.isGlobalUnlockActive(this)) globalTimer.setText("חסימה מושהית: "+((Prefs.getGlobalUnlockRemaining(this)+999)/1000)+" שניות");

        ((Button)findViewById(R.id.manageButton)).setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){requirePinThenOpen(new Intent(MainActivity.this,AppListActivity.class));}});
        ((Button)findViewById(R.id.pinButton)).setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){startActivity(new Intent(MainActivity.this,PinActivity.class).putExtra("mode",Prefs.hasPin(MainActivity.this)?"change":"setup"));}});
        ((Button)findViewById(R.id.toggleButton)).setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){if(!Prefs.hasPin(MainActivity.this)){startActivity(new Intent(MainActivity.this,PinActivity.class).putExtra("mode","setup"));}else startActivity(new Intent(MainActivity.this,PinActivity.class).putExtra("mode","verify_toggle"));}});
        ((Button)findViewById(R.id.adminButton)).setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){
            Intent i=new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,new ComponentName(MainActivity.this,DeviceAdminReceiver.class));
            i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,"הפעלת הגנת מנהל מכשיר מקשה על הסרת אפליקציית הנעילה.");
            startActivity(i);
        }});
        ((Button)findViewById(R.id.aboutButton)).setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){
            new AlertDialog.Builder(MainActivity.this).setTitle("אודות").setMessage("נעילת אפליקציות\\n\\nפותח ע"י חייא שיאומי ממצמחים טופ!\\n\\nמיועד במיוחד למכשירי Android 4.4.4 ומעלה.\\nהנעילה יכולה לעבוד עם שירות נגישות, ובמכשירים ישנים יש גם מנגנון גיבוי.").setPositiveButton("סגור",null).show();
        }});
    }

    @Override protected void onResume(){
        super.onResume();
        refreshUi();
        if(Prefs.hasPin(this)&&Prefs.isEnabled(this)){
            if(AccessibilityUtil.isEnabled(this)) LockService.stop(this);
            else LockService.ensureRunning(this);
        }
    }

    private void refreshUi(){
        if(!Prefs.hasPin(this)){
            status.setText("מצב: יש להגדיר קוד PIN");
        } else if(!Prefs.isEnabled(this)){
            status.setText("מצב: נעילה כבויה");
        } else if(AccessibilityUtil.isEnabled(this)){
            status.setText("מצב: נעילה פעילה • שירות נגישות פעיל");
        } else {
            status.setText("מצב: נעילה פעילה • מנגנון גיבוי פעיל");
        }
    }

    private void requirePinThenOpen(Intent intent){
        startActivity(new Intent(this,PinActivity.class).putExtra("mode","verify_then_open").putExtra("target",intent.getComponent().getClassName()));
    }

    public static void finishVerifyTarget(Activity a,String targetClass){
        if("com.mitmachim.applocker.AppListActivity".equals(targetClass))a.startActivity(new Intent(a,AppListActivity.class));
    }
}
