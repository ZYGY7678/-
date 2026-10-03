package com.mitmachim.applocker;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
public class LockActivity extends Activity {
    private String targetPackage; private EditText pin;
    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);setContentView(R.layout.activity_lock);getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        targetPackage=getIntent().getStringExtra("package");
        TextView msg=(TextView)findViewById(R.id.lockMessage);pin=(EditText)findViewById(R.id.lockPin);Button unlock=(Button)findViewById(R.id.unlockButton);
        pin.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);msg.setText("היישום מוגן בקוד PIN\n\nהזן PIN כדי להמשיך");
        unlock.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){verify();}});
        pin.addTextChangedListener(new TextWatcher(){ public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int before,int count){ if(Prefs.isAutoPin(LockActivity.this) && s.length()==4) verify(); } public void afterTextChanged(Editable e){} });
        pin.requestFocus();
    }
    private void verify(){String entered=pin.getText().toString();if(SecurityUtil.sha256(entered).equals(Prefs.getPinHash(this))){LockService.grantTemporaryUnlock(this,targetPackage);
            if(Prefs.isGlobalUnlock(this)) Prefs.startGlobalUnlock(this,60000L);
            launchTarget();finish();}else{pin.setText("");Toast.makeText(this,"PIN שגוי",Toast.LENGTH_SHORT).show();}}
    private void launchTarget(){try{Intent launch=getPackageManager().getLaunchIntentForPackage(targetPackage);if(launch!=null){launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);startActivity(launch);}}catch(Throwable ignored){}}
    @Override public void onBackPressed(){Intent home=new Intent(Intent.ACTION_MAIN);home.addCategory(Intent.CATEGORY_HOME);home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(home);}
}
