package com.zygy7678.contactdeduplicator;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
public class MainActivity extends Activity {
  public void onCreate(Bundle b){super.onCreate(b); TextView t=new TextView(this); t.setText("מנקה אנשי קשר כפולים"); setContentView(t);}
}