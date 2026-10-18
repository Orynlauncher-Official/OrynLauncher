package net.kdt.pojavlaunch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.cosmetics.OrynCosmeticPreviewView;
import net.kdt.pojavlaunch.cosmetics.OrynCosmeticsStore;
import net.kdt.pojavlaunch.instances.Instances;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OrynCosmeticsActivity extends Activity {
    private static final int PICK_SKIN = 501, PICK_CAPE = 502;
    private static final int BG=Color.rgb(2,8,20), PANEL=Color.rgb(4,15,35), CARD=Color.rgb(7,24,51);
    private static final int BLUE=Color.rgb(0,112,255), WHITE=Color.rgb(239,246,255), MUTED=Color.rgb(133,165,211);
    private OrynCosmeticsStore store;
    private OrynCosmeticPreviewView preview;
    private OrynCosmeticsStore.CosmeticProfile active;
    private TextView selectedName, modelInfo, status;
    private EditText search;
    private Button skinsTab, capesTab, modelButton, slimButton, importButton;
    private LinearLayout grid;
    private ScrollView gridScroll;
    private boolean showingCapes=false;
    private String query="";

    @Override public void onCreate(@Nullable Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        store=new OrynCosmeticsStore(this);
        store.removeBundledDemoSkins();
        store.ensureOrynCapes();
        store.normalizeExistingCapes();
        active=store.getActiveProfile();
        buildUi();
        renderGrid();
        refreshPreview();
    }

    private int d(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color,int radius,int stroke,int width){
        GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(d(radius));
        if(width>0)g.setStroke(d(width),stroke); return g;
    }
    private TextView label(String s,float size,int color){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }
    private Button button(String s,boolean primary){
        Button b=new Button(this);b.setText(s);b.setTextColor(primary?Color.BLACK:WHITE);b.setTextSize(12);b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);b.setMinHeight(0);b.setMinWidth(0);b.setPadding(d(5),0,d(5),0);
        b.setBackground(shape(primary?BLUE:CARD,11,primary?BLUE:Color.rgb(55,55,60),1));return b;
    }
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:d(w),h<0?h:d(h));}

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(d(16),d(8),d(16),d(10));root.setBackgroundColor(BG);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(d(4),0,d(4),0);
        Button back=button("‹",false);top.addView(back,lp(42,40));back.setOnClickListener(v->finish());
        ImageView logo=new ImageView(this);int logoId=getResources().getIdentifier("oryn_official_logo_v4","drawable",getPackageName());
        if(logoId==0)logoId=getResources().getIdentifier("oryn_cosmetics_logo","drawable",getPackageName());
        if(logoId!=0)logo.setImageResource(logoId);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoLp=lp(38,38);logoLp.leftMargin=d(10);top.addView(logo,logoLp);
        TextView brand=label("OrynLauncher",18,WHITE);brand.setTypeface(null,android.graphics.Typeface.BOLD);
        TextView plus=label("Plus",18,BLUE);plus.setTypeface(null,android.graphics.Typeface.BOLD);
        LinearLayout brandRow=new LinearLayout(this);brandRow.setGravity(Gravity.CENTER_VERTICAL);brandRow.addView(brand);brandRow.addView(plus);
        LinearLayout.LayoutParams brandLp=lp(-2,42);brandLp.leftMargin=d(5);top.addView(brandRow,brandLp);
        View spacer=new View(this);top.addView(spacer,new LinearLayout.LayoutParams(0,1,1));
        TextView topStatus=label("●  SKIN STUDIO",10,MUTED);top.addView(topStatus,lp(-2,36));
        root.addView(top,lp(-1,46));

        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.HORIZONTAL);LinearLayout.LayoutParams bodyLp=new LinearLayout.LayoutParams(-1,0,1);bodyLp.topMargin=d(8);root.addView(body,bodyLp);
        LinearLayout left=new LinearLayout(this);left.setOrientation(LinearLayout.VERTICAL);left.setPadding(d(12),d(8),d(12),d(10));left.setBackground(shape(PANEL,18,Color.rgb(45,45,50),1));
        LinearLayout.LayoutParams leftLp=new LinearLayout.LayoutParams(0,-1,0.43f);leftLp.rightMargin=d(12);body.addView(left,leftLp);
        TextView title=label("Skin & Cape",23,WHITE);title.setTypeface(null,android.graphics.Typeface.BOLD);left.addView(title,lp(-1,34));
        left.addView(label("Preview, select and apply your skins and capes",11,MUTED),lp(-1,23));
        LinearLayout previewFrame=new LinearLayout(this);previewFrame.setOrientation(LinearLayout.VERTICAL);previewFrame.setPadding(d(5),d(5),d(5),d(5));
        previewFrame.setBackground(shape(Color.rgb(5,17,39),15,Color.rgb(20,57,105),1));
        LinearLayout.LayoutParams pf=new LinearLayout.LayoutParams(-1,0,1);pf.topMargin=d(5);left.addView(previewFrame,pf);
        TextView previewBadge=label("◉  3D PREVIEW  •  DRAG TO ROTATE",10,MUTED);previewFrame.addView(previewBadge,lp(-1,25));
        preview=new OrynCosmeticPreviewView(this);preview.setBackground(shape(Color.rgb(5,12,25),12,Color.TRANSPARENT,0));
        previewFrame.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
        selectedName=label("Oryn User Skin",17,WHITE);selectedName.setGravity(Gravity.CENTER);selectedName.setTypeface(null,android.graphics.Typeface.BOLD);left.addView(selectedName,lp(-1,27));
        modelInfo=label("Classic Model (4px Arms)  •  Drag to Rotate",11,MUTED);modelInfo.setGravity(Gravity.CENTER);left.addView(modelInfo,lp(-1,23));
        LinearLayout modelRow=new LinearLayout(this);modelRow.setPadding(d(2),d(4),d(2),0);
        modelButton=button("Classic",true);slimButton=button("Slim",false);
        modelRow.addView(modelButton,new LinearLayout.LayoutParams(0,d(38),1));Space mg=new Space(this);modelRow.addView(mg,lp(6,1));modelRow.addView(slimButton,new LinearLayout.LayoutParams(0,d(38),1));
        left.addView(modelRow,lp(-1,44));
        modelButton.setOnClickListener(v->{active.model="classic";saveCurrent("Classic model selected");});
        slimButton.setOnClickListener(v->{active.model="slim";saveCurrent("Slim model selected");});

        LinearLayout right=new LinearLayout(this);right.setOrientation(LinearLayout.VERTICAL);right.setPadding(d(10),d(8),d(8),d(8));right.setBackground(shape(PANEL,18,Color.rgb(20,57,105),1));
        body.addView(right,new LinearLayout.LayoutParams(0,-1,0.57f));
        LinearLayout searchRow=new LinearLayout(this);searchRow.setGravity(Gravity.CENTER_VERTICAL);
        search=new EditText(this);search.setSingleLine(true);search.setTextColor(WHITE);search.setHintTextColor(MUTED);search.setTextSize(12);search.setPadding(d(12),0,d(10),0);
        search.setHint("Search skins or capes...");search.setBackground(shape(Color.rgb(10,10,12),12,Color.rgb(31,66,115),1));
        searchRow.addView(search,new LinearLayout.LayoutParams(0,d(42),1));
        Button onlineButton=button("NovaSkin ↗",false); LinearLayout.LayoutParams onlineLp=lp(94,42); onlineLp.leftMargin=d(6); searchRow.addView(onlineButton,onlineLp);
        onlineButton.setOnClickListener(v->openNovaSkinBrowser());
        LinearLayout tabs=new LinearLayout(this);tabs.setPadding(d(4),d(3),d(4),d(3));tabs.setBackground(shape(Color.rgb(4,14,32),12,Color.rgb(25,44,72),1));
        LinearLayout.LayoutParams tabsLp=lp(230,42);tabsLp.leftMargin=d(8);searchRow.addView(tabs,tabsLp);
        skinsTab=button("♧  Skins",true);capesTab=button("♧  Capes",false);
        tabs.addView(skinsTab,new LinearLayout.LayoutParams(0,-1,1));tabs.addView(capesTab,new LinearLayout.LayoutParams(0,-1,1));
        right.addView(searchRow,lp(-1,46));
        gridScroll=new ScrollView(this);gridScroll.setFillViewport(false);gridScroll.setVerticalScrollBarEnabled(false);gridScroll.setClipToPadding(false);
        grid=new LinearLayout(this);grid.setOrientation(LinearLayout.VERTICAL);grid.setPadding(0,d(5),0,d(5));gridScroll.addView(grid);
        right.addView(gridScroll,new LinearLayout.LayoutParams(-1,0,1));
        importButton=button("＋  Import / Replace Skin",true);LinearLayout.LayoutParams importLp=lp(-1,42);importLp.topMargin=d(5);right.addView(importButton,importLp);
        importButton.setOnClickListener(v->pick(PICK_SKIN));
        status=label("Ready",10,MUTED);root.addView(status,lp(-1,20));
        setContentView(root);

        skinsTab.setOnClickListener(v->{showingCapes=false;query="";search.setText("");updateTabs();updateImportButton();renderGrid();});
        capesTab.setOnClickListener(v->{showingCapes=true;query="";search.setText("");updateTabs();updateImportButton();renderGrid();});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){query=s.toString().trim().toLowerCase(Locale.ROOT);renderGrid();}
            public void afterTextChanged(Editable e){}});
        updateTabs();
    }

    private void openNovaSkinBrowser(){
        final EditText onlineSearch=new EditText(this); onlineSearch.setSingleLine(true); onlineSearch.setTextColor(WHITE); onlineSearch.setHintTextColor(MUTED); onlineSearch.setHint("Search NovaSkin (anime, hoodie, etc.)"); onlineSearch.setText(query);
        LinearLayout wrap=new LinearLayout(this); wrap.setPadding(d(14),d(4),d(14),d(4)); wrap.addView(onlineSearch,lp(-1,44));
        WebView web=new WebView(this); web.getSettings().setJavaScriptEnabled(true); web.getSettings().setDomStorageEnabled(true); web.setBackgroundColor(BG);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Browse NovaSkin").setView(web).setNegativeButton("Close",null).setNeutralButton("Search",null).create();
        dialog.setOnShowListener(x->{ dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{String q=Uri.encode(onlineSearch.getText().toString().trim()); web.loadUrl("https://minecraft.novaskin.me/search?q="+q);}); });
        web.setDownloadListener((url,userAgent,contentDisposition,mimeType,contentLength)->{
            if(url==null||!(url.startsWith("https://")||url.startsWith("http://"))) { status.setText("NovaSkin download URL was not valid"); return; }
            status.setText("Downloading skin from NovaSkin…");
            new android.os.AsyncTask<Void,Void,File>(){String error; protected File doInBackground(Void... v){try{return store.importRemoteSkin(url,"NovaSkin_"+System.currentTimeMillis());}catch(Exception e){error=e.getMessage();return null;}}
                protected void onPostExecute(File f){if(f!=null){if(!showingCapes){active.skin=f.getName();active.skinEnabled=true;saveCurrent("Downloaded and applied "+displayName(f));}else{status.setText("Skin saved. Switch to Skins to apply it.");renderGrid();}}else status.setText("Download failed: "+(error==null?"unsupported NovaSkin image":error));}
            }.execute();
        });
        web.loadUrl("https://minecraft.novaskin.me/skins?lang=en-US");
        LinearLayout browser=new LinearLayout(this);browser.setOrientation(LinearLayout.VERTICAL);browser.addView(wrap,lp(-1,50));browser.addView(web,new LinearLayout.LayoutParams(-1,d(420)));dialog.setView(browser);dialog.show();
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setText("Search");
    }

    private void updateImportButton(){ if(importButton!=null) importButton.setText(showingCapes?"＋  Import / Replace Cape":"＋  Import / Replace Skin"); }

    private void updateTabs(){
        skinsTab.setBackground(shape(showingCapes?CARD:BLUE,10,showingCapes?Color.rgb(0,91,210):BLUE,1));
        capesTab.setBackground(shape(showingCapes?BLUE:CARD,10,showingCapes?BLUE:Color.rgb(31,53,83),1));
        search.setHint(showingCapes?"Search capes...":"Search skins...");
    }

    private void renderGrid(){
        if(grid==null)return;grid.removeAllViews();
        List<File> all=showingCapes?store.listCapes():store.listSkins();List<File> visible=new ArrayList<>();
        for(File f:all)if(displayName(f).toLowerCase(Locale.ROOT).contains(query))visible.add(f);
        if(visible.isEmpty()){
            LinearLayout empty=new LinearLayout(this);empty.setOrientation(LinearLayout.VERTICAL);empty.setGravity(Gravity.CENTER);empty.setPadding(d(12),d(20),d(12),d(20));
            TextView title=label(query.isEmpty()?(showingCapes?"No capes yet":"No saved skins yet"):"No "+(showingCapes?"capes":"skins")+" found",15,WHITE);title.setGravity(Gravity.CENTER);
            empty.addView(title,lp(-1,40));TextView desc=label(showingCapes?"No cape assets available yet.":"Search online with NovaSkin, download a skin, and it will appear here.",11,MUTED);desc.setGravity(Gravity.CENTER);
            empty.addView(desc,lp(-1,45));grid.addView(empty,lp(-1,-2));
            if(!showingCapes&&!query.isEmpty()){} return;
        }
        int columns=4;
        for(int start=0;start<visible.size();start+=columns){
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
            for(int col=0;col<columns&&start+col<visible.size();col++){
                File file=visible.get(start+col);boolean selected=showingCapes?file.getName().equals(active.cape):file.getName().equals(active.skin);
                LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setGravity(Gravity.CENTER_HORIZONTAL);card.setPadding(d(5),d(5),d(5),d(5));
                card.setBackground(shape(selected?Color.rgb(8,43,91):CARD,12,selected?WHITE:Color.rgb(20,48,83),selected?2:1));
                ImageView thumb=new ImageView(this);thumb.setScaleType(ImageView.ScaleType.FIT_CENTER);Bitmap bitmap=store.load(file);
                if(bitmap!=null){
                    Bitmap cropped=null;
                    if(showingCapes&&bitmap.getWidth()>=12&&bitmap.getHeight()>=17)cropped=Bitmap.createBitmap(bitmap,1,1,10,16);
                    else if(!showingCapes&&bitmap.getWidth()>=8&&bitmap.getHeight()>=8)cropped=Bitmap.createBitmap(bitmap,8,8,Math.max(1,Math.min(8,bitmap.getWidth()-8)),Math.max(1,Math.min(8,bitmap.getHeight()-8)));
                    if(cropped!=null){
                        // Upscale Minecraft's tiny 8x8 face using nearest-neighbour
                        // so the skin stays crisp instead of becoming a blurry photo.
                        Bitmap crisp=Bitmap.createScaledBitmap(cropped,128,128,false);
                        if(cropped!=bitmap)cropped.recycle();
                        if(bitmap!=crisp)bitmap.recycle();
                        thumb.setImageBitmap(crisp);
                        if(android.os.Build.VERSION.SDK_INT>=16)thumb.setLayerType(View.LAYER_TYPE_SOFTWARE,null);
                    }else thumb.setImageBitmap(bitmap);
                }
                LinearLayout.LayoutParams thumbLp=lp(-1,70);card.addView(thumb,thumbLp);
                TextView name=label(displayName(file),10,WHITE);name.setGravity(Gravity.CENTER);name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(name,lp(-1,24));
                Button apply=button(selected?"✓  Equipped":"Apply",selected);apply.setTextSize(10);card.addView(apply,lp(-1,29));
                View.OnClickListener choose=v->{if(showingCapes){active.cape=file.getName();active.capeEnabled=true;}else{active.skin=file.getName();active.skinEnabled=true;
                    String skinName=displayName(file).toLowerCase(Locale.ROOT);
                    if(skinName.contains("alex")||skinName.contains("slim"))active.model="slim";
                    else if(skinName.contains("steve")||skinName.equals("oryn"))active.model="classic";
                }saveCurrent((showingCapes?"Cape":"Skin")+" applied locally: "+displayName(file));};
                apply.setOnClickListener(choose);thumb.setOnClickListener(choose);name.setOnClickListener(choose);
                LinearLayout.LayoutParams cardLp=new LinearLayout.LayoutParams(0,d(145),1);cardLp.setMargins(d(3),d(3),d(3),d(3));row.addView(card,cardLp);
            }
            while(row.getChildCount()<columns){View blank=new View(this);row.addView(blank,new LinearLayout.LayoutParams(0,1,1));}
            grid.addView(row,lp(-1,151));
        }
    }

    private void refreshPreview(){
        if(active==null||preview==null)return;
        Bitmap s=null,c=null;
        if(!active.skin.isEmpty()){File f=new File(getFilesDir(),"cosmetics/skins/"+active.skin);if(f.exists())s=store.load(f);}
        if(!active.cape.isEmpty()){File f=new File(getFilesDir(),"cosmetics/capes/"+active.cape);if(f.exists())c=store.load(f);}
        preview.setSkin(active.skinEnabled?s:null,"slim".equals(active.model));preview.setCape(active.capeEnabled?c:null);preview.setCapePreview(active.capeEnabled);
        selectedName.setText(active.skin.isEmpty()?"Oryn User Skin":displayName(new File(active.skin)));
        boolean slim="slim".equals(active.model);modelInfo.setText((slim?"Slim Model (3px Arms)":"Classic Model (4px Arms)")+"  •  Drag to Rotate");
        modelButton.setText(slim?"Classic":"✓  Classic");modelButton.setBackground(shape(slim?CARD:BLUE,10,BLUE,1));modelButton.setTextColor(slim?WHITE:Color.WHITE);
        if(slimButton!=null){slimButton.setText(slim?"✓  Slim":"Slim");slimButton.setBackground(shape(slim?BLUE:CARD,10,BLUE,1));slimButton.setTextColor(Color.WHITE);}
        renderGrid();
    }
    private String displayName(File f){String n=f.getName();if(n.toLowerCase(Locale.ROOT).endsWith(".png"))n=n.substring(0,n.length()-4);return n.replace('_',' ');}
    private void saveCurrent(String message){
        try{store.setActiveProfile(active);net.kdt.pojavlaunch.instances.Instance i=Instances.loadSelectedInstance();if(i!=null)store.writeActiveForInstance(i.getGameDirectory());
            refreshPreview();status.setText("✓  "+message);
        }catch(Exception e){status.setText("Could not save: "+e.getMessage());}
    }
    private void pick(int code){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/png");i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,code);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        try{
            File f=requestCode==PICK_SKIN?store.importSkin(uri,"skin"):store.importCape(uri,"cape");
            if(requestCode==PICK_SKIN){active.skin=f.getName();active.skinEnabled=true;}else{active.cape=f.getName();active.capeEnabled=true;}
            saveCurrent("Imported "+displayName(f));
        }catch(Exception e){new AlertDialog.Builder(this).setTitle("Import failed").setMessage(e.getMessage()==null?"Invalid PNG file":e.getMessage()).setPositiveButton("OK",null).show();}
    }
    @Override protected void onDestroy(){if(preview!=null){preview.stopLoading();preview.destroy();preview=null;}super.onDestroy();}
}
