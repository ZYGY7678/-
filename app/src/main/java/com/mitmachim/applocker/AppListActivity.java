package com.mitmachim.applocker;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class AppListActivity extends Activity {
    private List<AppInfo> apps=new ArrayList<AppInfo>();
    private Set<String> selected=new HashSet<String>();
    private String mode;
    private LinearLayout listContainer; private Spinner modeSpinner;
    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);setContentView(R.layout.activity_app_list);getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        listContainer=(LinearLayout)findViewById(R.id.appListContainer);modeSpinner=(Spinner)findViewById(R.id.modeSpinner);
        ArrayAdapter<String>a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,new String[]{"נעילה לפי רשימה","נעל הכל חוץ מהחרגות"});
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);modeSpinner.setAdapter(a);
        mode=Prefs.getMode(this);modeSpinner.setSelection(Prefs.MODE_ALL_EXCEPT.equals(mode)?1:0);
        modeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){@Override public void onNothingSelected(android.widget.AdapterView<?>p){}@Override public void onItemSelected(android.widget.AdapterView<?>p,View v,int pos,long id){mode=pos==1?Prefs.MODE_ALL_EXCEPT:Prefs.MODE_SELECTED;Prefs.setMode(AppListActivity.this,mode);selected=Prefs.getActiveAppSet(AppListActivity.this);renderList();}});
        selected=Prefs.getActiveAppSet(this);loadApps();
    }
    private void loadApps(){
        PackageManager pm=getPackageManager();List<ApplicationInfo> installed=pm.getInstalledApplications(PackageManager.GET_META_DATA);
        for(ApplicationInfo ai:installed){if(getPackageName().equals(ai.packageName))continue;if(pm.getLaunchIntentForPackage(ai.packageName)==null)continue;apps.add(new AppInfo(ai.loadLabel(pm).toString(),ai.packageName));}
        Collections.sort(apps,new Comparator<AppInfo>(){@Override public int compare(AppInfo x,AppInfo y){return x.label.compareToIgnoreCase(y.label);}});renderList();
    }
    private void renderList(){
        listContainer.removeAllViews();TextView header=new TextView(this);header.setText(Prefs.MODE_ALL_EXCEPT.equals(mode)?"סמן אפליקציות שתרצה להחריג מהנעילה":"סמן אפליקציות שתרצה לנעול");header.setTextSize(16);header.setPadding(16,16,16,16);listContainer.addView(header);
        for(final AppInfo app:apps){final CheckBox cb=new CheckBox(this);cb.setText(app.label+"\n"+app.packageName);cb.setTextSize(15);cb.setPadding(16,10,16,10);cb.setGravity(android.view.Gravity.RIGHT|android.view.Gravity.CENTER_VERTICAL);cb.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);cb.setChecked(selected.contains(app.packageName));cb.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){if(cb.isChecked())selected.add(app.packageName);else selected.remove(app.packageName);Prefs.setActiveAppSet(AppListActivity.this,selected);}});listContainer.addView(cb,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));}
    }
    static class AppInfo{final String label,packageName;AppInfo(String l,String p){label=l;packageName=p;}}
}
