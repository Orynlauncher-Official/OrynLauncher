package net.kdt.pojavlaunch;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.cosmetics.OrynCosmeticPreviewView;
import net.kdt.pojavlaunch.cosmetics.OrynCosmeticsStore;
import net.kdt.pojavlaunch.instances.Instances;

import java.io.File;
import java.util.List;

public class OrynCosmeticsActivity extends Activity {
    private static final int PICK_SKIN=501, PICK_CAPE=502;
    private OrynCosmeticsStore store;
    private OrynCosmeticPreviewView preview;
    private Spinner profiles, models, skins, capes;
    private TextView status, selectedSkin, selectedCape;
    private OrynCosmeticsStore.CosmeticProfile active;
    private boolean capeTab=false;

    @Override public void onCreate(@Nullable Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        store=new OrynCosmeticsStore(this);
        active=store.getActiveProfile();
        buildUi();
        refresh();
    }

    private TextView tv(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(size);t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(16,8,16,8);return t;}
    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setTextSize(14);return b;}

    private void buildUi(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.HORIZONTAL); root.setPadding(20,16,20,16); root.setBackgroundColor(Color.rgb(9,11,15));
        LinearLayout left=new LinearLayout(this);left.setOrientation(LinearLayout.VERTICAL);left.setPadding(10,10,10,10);left.setBackgroundColor(Color.rgb(18,21,27));
        TextView title=tv("ORYN  •  COSMETICS",20);left.addView(title,new LinearLayout.LayoutParams(210,-2));
        Button skinTab=btn("SKINS"); Button capeTabButton=btn("CAPES"); left.addView(skinTab,new LinearLayout.LayoutParams(-1,56));left.addView(capeTabButton,new LinearLayout.LayoutParams(-1,56));
        Button importSkin=btn("＋ Import Skin"); Button importCape=btn("＋ Import Cape");left.addView(importSkin,new LinearLayout.LayoutParams(-1,56));left.addView(importCape,new LinearLayout.LayoutParams(-1,56));
        Button delete=btn("Remove Selected");left.addView(delete,new LinearLayout.LayoutParams(-1,56));
        Button back=btn("Back");left.addView(back,new LinearLayout.LayoutParams(-1,56));
        root.addView(left,new LinearLayout.LayoutParams(230,-1));

        LinearLayout center=new LinearLayout(this);center.setOrientation(LinearLayout.VERTICAL);center.setGravity(Gravity.CENTER);preview=new OrynCosmeticPreviewView(this);center.addView(preview,new LinearLayout.LayoutParams(-1,0,1));status=tv("Ready",13);center.addView(status,new LinearLayout.LayoutParams(-1,40));root.addView(center,new LinearLayout.LayoutParams(0,-1,1));

        LinearLayout right=new LinearLayout(this);right.setOrientation(LinearLayout.VERTICAL);right.setPadding(14,8,8,8);right.setBackgroundColor(Color.rgb(18,21,27));
        right.addView(tv("Cosmetic Profile",18));
        profiles=new Spinner(this);right.addView(profiles,new LinearLayout.LayoutParams(-1,52));
        Button newProfile=btn("＋ New Profile");right.addView(newProfile,new LinearLayout.LayoutParams(-1,48));
        right.addView(tv("Skin",13)); skins=new Spinner(this);right.addView(skins,new LinearLayout.LayoutParams(-1,48));
        right.addView(tv("Cape",13)); capes=new Spinner(this);right.addView(capes,new LinearLayout.LayoutParams(-1,48));
        right.addView(tv("Player Model",13)); models=new Spinner(this);right.addView(models,new LinearLayout.LayoutParams(-1,48));
        selectedSkin=tv("",14);selectedCape=tv("",14);right.addView(selectedSkin);right.addView(selectedCape);
        Button save=btn("Save Profile");Button apply=btn("Apply Profile");right.addView(save,new LinearLayout.LayoutParams(-1,58));right.addView(apply,new LinearLayout.LayoutParams(-1,58));
        right.addView(tv("Custom capes are local cosmetics. Oryn does not claim an official Minecraft cape unless the account actually owns one.",11),new LinearLayout.LayoutParams(-1,0,1));
        root.addView(right,new LinearLayout.LayoutParams(300,-1));
        setContentView(root);

        skinTab.setOnClickListener(v->{capeTab=false;status.setText("Skins • 64×64 compatible PNG");});
        capeTabButton.setOnClickListener(v->{capeTab=true;status.setText("Capes • local and compatible");});
        importSkin.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/png");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK_SKIN);});
        importCape.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/png");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK_CAPE);});
        delete.setOnClickListener(v->removeSelected());
        back.setOnClickListener(v->finish());
        newProfile.setOnClickListener(v->{
            final EditText input=new EditText(this); input.setHint("Profile name");
            new android.app.AlertDialog.Builder(this).setTitle("New Cosmetic Profile").setView(input)
                .setNegativeButton("Cancel",null).setPositiveButton("Create",(d,w)->{
                    String n=input.getText().toString().trim(); if(n.isEmpty()) n="Custom";
                    OrynCosmeticsStore.CosmeticProfile p=new OrynCosmeticsStore.CosmeticProfile(); p.name=n;
                    try{store.saveProfile(p);active=p;refresh();}catch(Exception e){status.setText("Profile creation failed");}
                }).show();
        });
        save.setOnClickListener(v->saveCurrent());
        apply.setOnClickListener(v->saveCurrent());
        profiles.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){} public void onItemSelected(AdapterView<?> p,View v,int pos,long id){List<OrynCosmeticsStore.CosmeticProfile> ps=store.listProfiles();if(pos<ps.size()){active=ps.get(pos);refreshPreview();}}});
        models.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Classic (Steve)","Slim (Alex)"}));
        skins.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){} public void onItemSelected(AdapterView<?> p,View v,int pos,long id){List<File> fs=store.listSkins();if(pos==0)active.skin="";else if(pos-1<fs.size())active.skin=fs.get(pos-1).getName();refreshPreview();}});
        capes.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){} public void onItemSelected(AdapterView<?> p,View v,int pos,long id){List<File> fs=store.listCapes();if(pos==0)active.cape="";else if(pos-1<fs.size())active.cape=fs.get(pos-1).getName();refreshPreview();}});
    }

    private void refresh(){
        List<OrynCosmeticsStore.CosmeticProfile> ps=store.listProfiles();String[] names=new String[ps.size()];for(int i=0;i<ps.size();i++)names[i]=ps.get(i).name;
        profiles.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,names));
        models.setSelection("slim".equals(active.model)?1:0);
        List<File> sf=store.listSkins(); String[] sn=new String[sf.size()+1]; sn[0]="None"; for(int i=0;i<sf.size();i++)sn[i+1]=sf.get(i).getName();
        skins.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,sn));
        List<File> cf=store.listCapes(); String[] cn=new String[cf.size()+1]; cn[0]="None"; for(int i=0;i<cf.size();i++)cn[i+1]=cf.get(i).getName();
        capes.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,cn));
        refreshPreview();
        status.setText(capeTab?"Capes • local and compatible":"Skins • 64×64 compatible PNG");
    }

    private void refreshPreview(){
        Bitmap s=null,c=null;
        if(!active.skin.isEmpty()) s=store.load(new File(storeSkinDir(),active.skin));
        if(!active.cape.isEmpty()) c=store.load(new File(storeCapeDir(),active.cape));
        preview.setSkin(s,"slim".equals(active.model));preview.setCape(c);
        selectedSkin.setText("Skin: "+(active.skin.isEmpty()?"None":active.skin));
        selectedCape.setText("Cape: "+(active.cape.isEmpty()?"None":active.cape));
    }
    private File storeSkinDir(){return new File(getFilesDir(),"cosmetics/skins");}
    private File storeCapeDir(){return new File(getFilesDir(),"cosmetics/capes");}

    private void removeSelected(){
        if(capeTab){if(!active.cape.isEmpty()){store.delete(new File(storeCapeDir(),active.cape));active.cape="";}}
        else {if(!active.skin.isEmpty()){store.delete(new File(storeSkinDir(),active.skin));active.skin="";}}
        saveCurrent();
    }

    private void saveCurrent(){
        active.model=models.getSelectedItemPosition()==1?"slim":"classic";
        try{store.setActiveProfile(active);status.setText("✓ Profile saved: "+active.name);syncInstance();}catch(Exception e){status.setText("Save failed: "+e.getMessage());}
        refreshPreview();
    }

    private void syncInstance(){try{net.kdt.pojavlaunch.instances.Instance i=Instances.loadSelectedInstance();if(i!=null)store.writeActiveForInstance(i.getGameDirectory());}catch(Exception ignored){}}

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        try{
            File f=requestCode==PICK_SKIN?store.importSkin(uri,"skin"):store.importCape(uri,"cape");
            if(requestCode==PICK_SKIN)active.skin=f.getName();else active.cape=f.getName();
            saveCurrent();
            Toast.makeText(this,"Imported "+f.getName(),Toast.LENGTH_SHORT).show();
        }catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
    }
}