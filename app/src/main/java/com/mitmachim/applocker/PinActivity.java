package com.mitmachim.applocker;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class PinActivity extends Activity {
    private EditText pin1,pin2;
    private TextView title;
    private String mode,target;
    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);setContentView(R.layout.activity_pin);getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        mode=getIntent().getStringExtra("mode");target=getIntent().getStringExtra("target");if(mode==null)mode="setup";
        title=(TextView)findViewById(R.id.pinTitle);TextView hint=(TextView)findViewById(R.id.pinHint);pin1=(EditText)findViewById(R.id.pin1);pin2=(EditText)findViewById(R.id.pin2);Button save=(Button)findViewById(R.id.pinAction);
        pin1.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);pin2.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        if("setup".equals(mode)){title.setText("הגדרת קוד PIN");hint.setText("בחר קוד בן 4 עד 8 ספרות");pin2.setVisibility(View.VISIBLE);}
        else if("change".equals(mode)){title.setText("שינוי קוד PIN");hint.setText("הזן את הקוד הנוכחי ולאחר מכן קוד חדש");pin1.setHint("קוד נוכחי");pin2.setHint("קוד חדש");}
        else {title.setText("נדרש קוד PIN");hint.setText("הזן את הקוד כדי להמשיך");pin2.setVisibility(View.GONE);save.setText("אישור");}
        save.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){submit();}});
    }
    private void submit(){
        String a=pin1.getText().toString(),b=pin2.getText().toString();
        if("set_new_after_change".equals(mode)){if(!validPin(a)||!a.equals(b)){Toast.makeText(this,"יש להזין PIN תקין וזהה בשני השדות",Toast.LENGTH_SHORT).show();return;}Prefs.setPinHash(this,SecurityUtil.sha256(a));Toast.makeText(this,"ה-PIN החדש נשמר",Toast.LENGTH_SHORT).show();finish();return;}
        if("setup".equals(mode)){if(!validPin(a)||!a.equals(b)){Toast.makeText(this,"יש להזין PIN תקין וזהה בשני השדות",Toast.LENGTH_SHORT).show();return;}Prefs.setPinHash(this,SecurityUtil.sha256(a));Prefs.setEnabled(this,true);LockService.ensureRunning(this);finish();return;}
        if("change".equals(mode)){if(!SecurityUtil.sha256(a).equals(Prefs.getPinHash(this))){Toast.makeText(this,"הקוד הנוכחי שגוי",Toast.LENGTH_SHORT).show();return;}showNewPinStage();return;}
        if(!SecurityUtil.sha256(a).equals(Prefs.getPinHash(this))){Toast.makeText(this,"PIN שגוי",Toast.LENGTH_SHORT).show();pin1.setText("");return;}
        if("verify_then_open".equals(mode)){MainActivity.finishVerifyTarget(this,target);finish();}
        else if("verify_toggle".equals(mode)){Prefs.setEnabled(this,!Prefs.isEnabled(this));if(Prefs.isEnabled(this))LockService.ensureRunning(this);else LockService.stop(this);finish();}
        else if("unlock".equals(mode)){String pkg=getIntent().getStringExtra("package");LockService.grantTemporaryUnlock(this,pkg);finish();}
    }
    private void showNewPinStage(){title.setText("PIN חדש");pin1.setText("");pin2.setText("");pin2.setVisibility(View.VISIBLE);pin1.setHint("PIN חדש");pin2.setHint("אישור PIN חדש");mode="set_new_after_change";((Button)findViewById(R.id.pinAction)).setText("שמירה");}
    private boolean validPin(String s){return s!=null&&s.length()>=4&&s.length()<=8&&s.matches("[0-9]+");}
}
