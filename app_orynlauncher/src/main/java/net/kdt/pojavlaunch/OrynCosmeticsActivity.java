package net.kdt.pojavlaunch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.cosmetics.OrynCosmeticPreviewView;
import net.kdt.pojavlaunch.cosmetics.OrynCosmeticsStore;
import net.kdt.pojavlaunch.instances.Instances;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fresh, local-first Skin Studio.
 * No remote scraping, no online thumbnail queue, and no network work in the UI.
 */
public class OrynCosmeticsActivity extends Activity {
    private static final int PICK_SKIN = 501, PICK_CAPE = 502;
    private static final int BG=Color.rgb(2,8,20), PANEL=Color.rgb(4,15,35);
    private static final int CARD=Color.rgb(7,24,51), BLUE=Color.rgb(0,112,255);
    private static final int WHITE=Color.rgb(239,246,255), MUTED=Color.rgb(133,165,211);

    private OrynCosmeticsStore store;
    private OrynCosmeticPreviewView preview;
    private OrynCosmeticsStore.CosmeticProfile active;
    private TextView selectedName, modelInfo, status;
    private EditText search;
    private Button skinsTab, capesTab, classicButton, slimButton, importButton;
    private LinearLayout grid;
    private boolean showingCapes;
    private String filter="";

    @Override public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        store=new OrynCosmeticsStore(this);
        store.ensureDefaultSkins();
        store.ensureOrynCapes();
        store.normalizeExistingCapes();
        active=store.getActiveProfile();
        if(active.skin==null) active.skin="";
        if(active.cape==null) active.cape="";
        if(active.model==null) active.model="classic";
        buildUi();
        refreshPreview();
    }

    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color,int radius,int stroke,int width){
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius));
        if(width>0)d.setStroke(dp(width),stroke); return d;
    }
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    private TextView text(String value,float size,int color){
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL); return t;
    }
    private Button button(String value,boolean primary){
        Button b=new Button(this); b.setText(value); b.setTextColor(WHITE); b.setTextSize(12);
        b.setAllCaps(false); b.setGravity(Gravity.CENTER); b.setMinHeight(0); b.setMinWidth(0);
        b.setPadding(dp(4),0,dp(4),0); b.setBackground(bg(primary?BLUE:CARD,11,primary?BLUE:Color.rgb(38,67,105),1));
        return b;
    }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(8),dp(16),dp(8)); root.setBackgroundColor(BG);
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        Button back=button("‹",false); top.addView(back,lp(42,40)); back.setOnClickListener(v->finish());
        TextView brand=text("  OrynLauncher",19,WHITE); brand.setTypeface(null,android.graphics.Typeface.BOLD);
        TextView plus=text("Plus",19,BLUE); plus.setTypeface(null,android.graphics.Typeface.BOLD);
        top.addView(brand,lp(-2,42)); top.addView(plus,lp(-2,42));
        View spacer=new View(this); top.addView(spacer,new LinearLayout.LayoutParams(0,1,1));
        top.addView(text("●  SKIN STUDIO",10,MUTED),lp(-2,38)); root.addView(top,lp(-1,46));

        LinearLayout body=new LinearLayout(this); body.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bodyLp=new LinearLayout.LayoutParams(-1,0,1); bodyLp.topMargin=dp(8); root.addView(body,bodyLp);

        LinearLayout left=new LinearLayout(this); left.setOrientation(LinearLayout.VERTICAL);
        left.setPadding(dp(12),dp(8),dp(12),dp(10)); left.setBackground(bg(PANEL,18,Color.rgb(30,57,93),1));
        LinearLayout.LayoutParams leftLp=new LinearLayout.LayoutParams(0,-1,0.46f); leftLp.rightMargin=dp(10); body.addView(left,leftLp);
        TextView title=text("Skin & Cape",23,WHITE); title.setTypeface(null,android.graphics.Typeface.BOLD); left.addView(title,lp(-1,34));
        left.addView(text("Preview, select and apply locally",11,MUTED),lp(-1,23));

        FrameLayout frame=new FrameLayout(this); frame.setBackground(bg(Color.rgb(5,17,39),15,Color.rgb(20,57,105),1));
        LinearLayout.LayoutParams frameLp=new LinearLayout.LayoutParams(-1,0,1); frameLp.topMargin=dp(6); left.addView(frame,frameLp);
        preview=new OrynCosmeticPreviewView(this); preview.setBackgroundColor(Color.TRANSPARENT);
        frame.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        TextView badge=text("◉  3D PREVIEW  •  DRAG TO ROTATE",10,MUTED);
        FrameLayout.LayoutParams badgeLp=new FrameLayout.LayoutParams(-2,dp(25),Gravity.TOP|Gravity.LEFT);
        badgeLp.leftMargin=dp(8); badgeLp.topMargin=dp(7); frame.addView(badge,badgeLp);
        Button rotateLeft=button("‹",false); rotateLeft.setTextSize(24);
        FrameLayout.LayoutParams rl=new FrameLayout.LayoutParams(dp(36),dp(42),Gravity.CENTER_VERTICAL|Gravity.LEFT);
        rl.leftMargin=dp(6); frame.addView(rotateLeft,rl);
        Button rotateRight=button("›",false); rotateRight.setTextSize(24);
        FrameLayout.LayoutParams rr=new FrameLayout.LayoutParams(dp(36),dp(42),Gravity.CENTER_VERTICAL|Gravity.RIGHT);
        rr.rightMargin=dp(6); frame.addView(rotateRight,rr);
        rotateLeft.setOnClickListener(v->preview.rotateBy(-0.55f)); rotateRight.setOnClickListener(v->preview.rotateBy(0.55f));
        selectedName=text("Oryn",17,WHITE); selectedName.setGravity(Gravity.CENTER);
        selectedName.setTypeface(null,android.graphics.Typeface.BOLD); left.addView(selectedName,lp(-1,27));
        modelInfo=text("Classic Model (4px Arms) • Drag to Rotate",11,MUTED);
        modelInfo.setGravity(Gravity.CENTER); left.addView(modelInfo,lp(-1,23));
        LinearLayout modelRow=new LinearLayout(this); modelRow.setPadding(dp(2),dp(4),dp(2),0);
        classicButton=button("✓ Classic",true); slimButton=button("Slim",false);
        modelRow.addView(classicButton,new LinearLayout.LayoutParams(0,dp(38),1));
        Space gap=new Space(this); modelRow.addView(gap,lp(6,1));
        modelRow.addView(slimButton,new LinearLayout.LayoutParams(0,dp(38),1)); left.addView(modelRow,lp(-1,44));
        classicButton.setOnClickListener(v->{active.model="classic";save("Classic model selected");});
        slimButton.setOnClickListener(v->{active.model="slim";save("Slim model selected");});

        LinearLayout right=new LinearLayout(this); right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(dp(10),dp(8),dp(8),dp(8)); right.setBackground(bg(PANEL,18,Color.rgb(20,57,105),1));
        body.addView(right,new LinearLayout.LayoutParams(0,-1,0.54f));
        LinearLayout controls=new LinearLayout(this); controls.setGravity(Gravity.CENTER_VERTICAL);
        search=new EditText(this); search.setSingleLine(true); search.setTextColor(WHITE); search.setHintTextColor(MUTED);
        search.setTextSize(12); search.setPadding(dp(12),0,dp(8),0); search.setHint("Search saved skins...");
        search.setBackground(bg(Color.rgb(6,15,29),12,Color.rgb(31,66,115),1));
        controls.addView(search,new LinearLayout.LayoutParams(0,dp(42),1));
        LinearLayout tabs=new LinearLayout(this); tabs.setPadding(dp(3),dp(3),dp(3),dp(3));
        tabs.setBackground(bg(Color.rgb(4,14,32),12,Color.rgb(25,44,72),1));
        LinearLayout.LayoutParams tabsLp=lp(220,42); tabsLp.leftMargin=dp(7); controls.addView(tabs,tabsLp);
        skinsTab=button("♧  Skins",true); capesTab=button("♧  Capes",false);
        tabs.addView(skinsTab,new LinearLayout.LayoutParams(0,-1,1)); tabs.addView(capesTab,new LinearLayout.LayoutParams(0,-1,1));
        right.addView(controls,lp(-1,46));
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(false); scroll.setVerticalScrollBarEnabled(false);
        grid=new LinearLayout(this); grid.setOrientation(LinearLayout.VERTICAL); grid.setPadding(0,dp(5),0,dp(5));
        scroll.addView(grid); right.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        importButton=button("＋  Import / Replace Skin",true); LinearLayout.LayoutParams imp=lp(-1,42);
        imp.topMargin=dp(5); right.addView(importButton,imp);
        importButton.setOnClickListener(v->pick(showingCapes?PICK_CAPE:PICK_SKIN));
        status=text("Ready • Choose a skin and tap Apply",10,MUTED); root.addView(status,lp(-1,20));
        setContentView(root);

        skinsTab.setOnClickListener(v->{showingCapes=false;filter="";search.setText("");updateTabs();renderGrid();});
        capesTab.setOnClickListener(v->{showingCapes=true;filter="";search.setText("");updateTabs();renderGrid();});
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){filter=s.toString().trim().toLowerCase(Locale.ROOT);renderGrid();}
            public void afterTextChanged(Editable e){}
        });
        updateTabs();
    }

    private void updateTabs(){
        skinsTab.setBackground(bg(showingCapes?CARD:BLUE,10,showingCapes?Color.rgb(38,67,105):BLUE,1));
        capesTab.setBackground(bg(showingCapes?BLUE:CARD,10,showingCapes?BLUE:Color.rgb(38,67,105),1));
        search.setHint(showingCapes?"Search saved capes...":"Search saved skins...");
        importButton.setText(showingCapes?"＋  Import / Replace Cape":"＋  Import / Replace Skin");
    }

    private List<File> allFiles(){
        List<File> source=showingCapes?store.listCapes():store.listSkins();
        List<File> result=new ArrayList<>();
        for(File f:source) if(f.getName().toLowerCase(Locale.ROOT).contains(filter)) result.add(f);
        return result;
    }

    private void renderGrid(){
        if(grid==null)return;
        grid.removeAllViews();
        List<File> files=allFiles();
        if(files.isEmpty()){
            TextView empty=text(showingCapes?"No capes yet. Import a cape PNG.":"No skins found. Import a skin PNG.",13,MUTED);
            empty.setGravity(Gravity.CENTER); grid.addView(empty,lp(-1,100)); return;
        }
        int columns=3;
        for(int start=0;start<files.size();start+=columns){
            LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
            for(int col=0;col<columns&&start+col<files.size();col++){
                File file=files.get(start+col);
                boolean equipped=showingCapes?file.getName().equals(active.cape):file.getName().equals(active.skin);
                LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
                card.setGravity(Gravity.CENTER_HORIZONTAL); card.setPadding(dp(5),dp(5),dp(5),dp(5));
                card.setBackground(bg(equipped?Color.rgb(7,36,76):CARD,12,equipped?BLUE:Color.rgb(20,48,83),equipped?2:1));
                ImageView image=new ImageView(this); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                Bitmap bitmap=store.load(file);
                if(bitmap!=null){
                    if(!showingCapes){
                        Bitmap head=headIcon(bitmap);
                        if(head!=null){image.setImageBitmap(head);bitmap.recycle();}
                        else {image.setImageBitmap(bitmap);}
                    } else image.setImageBitmap(bitmap);
                }
                card.addView(image,lp(-1,76));
                TextView name=text(displayName(file),10,WHITE); name.setGravity(Gravity.CENTER);
                name.setMaxLines(1); name.setEllipsize(android.text.TextUtils.TruncateAt.END); card.addView(name,lp(-1,24));
                Button apply=button(equipped?"✓ Applied":"Apply",equipped); apply.setTextSize(10);
                card.addView(apply,lp(-1,29));
                View.OnClickListener click=v->applyFile(file);
                apply.setOnClickListener(click); name.setOnClickListener(click); image.setOnClickListener(click);
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(139),1);
                cp.setMargins(dp(3),dp(3),dp(3),dp(3)); row.addView(card,cp);
            }
            while(row.getChildCount()<columns)row.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
            grid.addView(row,lp(-1,dp(145)));
        }
    }

    private Bitmap headIcon(Bitmap source){
        try{
            int w=source.getWidth(),h=source.getHeight();
            if(w<16||h<16)return null;
            Bitmap normalized=source;
            boolean own=false;
            if(w!=64||h!=64){normalized=Bitmap.createScaledBitmap(source,64,64,false);own=true;}
            Bitmap face=Bitmap.createBitmap(normalized,8,8,8,8);
            Bitmap out=Bitmap.createScaledBitmap(face,128,128,false);
            face.recycle();
            Bitmap hat=Bitmap.createBitmap(normalized,40,8,8,8);
            new android.graphics.Canvas(out).drawBitmap(Bitmap.createScaledBitmap(hat,128,128,false),0,0,new android.graphics.Paint());
            hat.recycle(); if(own)normalized.recycle(); return out;
        }catch(Exception e){return null;}
    }

    private File findFile(String name,boolean cape){
        if(name==null||name.isEmpty())return null;
        for(File f:(cape?store.listCapes():store.listSkins()))if(f.getName().equals(name))return f;
        return null;
    }
    private String displayName(File f){
        String n=f.getName(); if(n.toLowerCase(Locale.ROOT).endsWith(".png"))n=n.substring(0,n.length()-4);
        return n.replace('_',' ');
    }
    private void applyFile(File file){
        if(showingCapes){active.cape=file.getName();active.capeEnabled=true;}
        else{
            active.skin=file.getName();active.skinEnabled=true;
            String n=displayName(file).toLowerCase(Locale.ROOT);
            if(n.contains("alex")||n.contains("slim")||n.contains("girl"))active.model="slim";
            else active.model="classic";
        }
        save("Applied "+displayName(file));
    }
    private void refreshPreview(){
        if(active==null||preview==null)return;
        File skin=findFile(active.skin,false),cape=findFile(active.cape,true);
        Bitmap sb=skin==null?null:store.load(skin), cb=cape==null?null:store.load(cape);
        preview.setSkin(active.skinEnabled?sb:null,"slim".equals(active.model));
        preview.setCape(active.capeEnabled?cb:null); preview.setCapePreview(active.capeEnabled);
        selectedName.setText(skin==null?"Oryn":displayName(skin));
        boolean slim="slim".equals(active.model);
        modelInfo.setText((slim?"Slim Model (3px Arms)":"Classic Model (4px Arms)")+" • Drag to Rotate");
        classicButton.setText(slim?"Classic":"✓ Classic"); slimButton.setText(slim?"✓ Slim":"Slim");
        classicButton.setBackground(bg(slim?CARD:BLUE,10,BLUE,1)); slimButton.setBackground(bg(slim?BLUE:CARD,10,BLUE,1));
        renderGrid();
    }
    private void save(String message){
        try{
            store.setActiveProfile(active);
            net.kdt.pojavlaunch.instances.Instance instance=Instances.loadSelectedInstance();
            if(instance!=null)store.writeActiveForInstance(instance.getGameDirectory());
            refreshPreview(); status.setText("✓  "+message);
        }catch(Exception e){status.setText("Could not save: "+(e.getMessage()==null?"unknown error":e.getMessage()));}
    }
    private void pick(int code){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.setType("image/png");
        intent.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(intent,code);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        try{
            File file=requestCode==PICK_SKIN?store.importSkin(data.getData(),"skin"):store.importCape(data.getData(),"cape");
            if(requestCode==PICK_SKIN){active.skin=file.getName();active.skinEnabled=true;}
            else {active.cape=file.getName();active.capeEnabled=true;}
            save("Imported "+displayName(file));
        }catch(Exception e){
            new AlertDialog.Builder(this).setTitle("Import failed")
                    .setMessage(e.getMessage()==null?"Invalid PNG file":e.getMessage())
                    .setPositiveButton("OK",null).show();
        }
    }
    @Override protected void onDestroy(){
        if(preview!=null){preview.stopLoading();preview.destroy();preview=null;}
        super.onDestroy();
    }
}