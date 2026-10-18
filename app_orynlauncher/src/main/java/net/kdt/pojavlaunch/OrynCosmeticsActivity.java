package net.kdt.pojavlaunch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;

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
    private boolean onlineMode=false, onlineLoading=false;
    private final List<OnlineSkin> onlineSkins=new ArrayList<>();
    private final java.util.Map<String,Bitmap> onlineThumbCache=new java.util.concurrent.ConcurrentHashMap<>();
    private static class OnlineSkin { String name, id, url; OnlineSkin(String n,String i,String imageUrl){name=n;id=i;url=imageUrl==null||imageUrl.isEmpty()?"https://novask.in/"+i+".png":imageUrl;} }

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
        Button onlineButton=button("Online Skins",false); LinearLayout.LayoutParams onlineLp=lp(100,42); onlineLp.leftMargin=d(6); searchRow.addView(onlineButton,onlineLp);
        onlineButton.setOnClickListener(v->{onlineMode=true; showingCapes=false; updateTabs(); updateImportButton(); loadOnlineSkins(search.getText().toString().trim());});
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

        skinsTab.setOnClickListener(v->{showingCapes=false;onlineMode=false;query="";search.setText("");updateTabs();updateImportButton();renderGrid();});
        capesTab.setOnClickListener(v->{showingCapes=true;onlineMode=false;query="";search.setText("");updateTabs();updateImportButton();renderGrid();});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){query=s.toString().trim().toLowerCase(Locale.ROOT);renderGrid();}
            public void afterTextChanged(Editable e){}});
        search.setOnEditorActionListener((v, actionId, event)->{ if(onlineMode){loadOnlineSkins(search.getText().toString().trim());return true;} return false; });
        updateTabs();
    }

    /** Fetch NovaSkin search results and show them in our own native grid (no WebView/browser). */
    private void loadOnlineSkins(String term) {
        if (showingCapes) { showingCapes=false; updateTabs(); }
        onlineMode=true;
        onlineLoading=true;
        onlineSkins.clear();
        status.setText("Loading online skins…");
        renderGrid();
        new android.os.AsyncTask<Void,Void,List<OnlineSkin>>() {
            String error;
            protected List<OnlineSkin> doInBackground(Void... unused) {
                List<OnlineSkin> result=new ArrayList<>();
                HttpURLConnection connection=null;
                try {
                    String encoded=Uri.encode(term==null?"":term.trim());
                    URL url=new URL("https://minecraft.novaskin.me/search?lang=en&q="+encoded);
                    connection=(HttpURLConnection)url.openConnection();
                    connection.setConnectTimeout(6500); connection.setReadTimeout(8000);
                    connection.setRequestProperty("User-Agent","OrynLauncherPlus/5.0 (Android)");
                    StringBuilder html=new StringBuilder();
                    try(BufferedReader reader=new BufferedReader(new InputStreamReader(connection.getInputStream(),"UTF-8"))) {
                        String line; int total=0;
                        while((line=reader.readLine())!=null && total<1024*1024) { html.append(line).append('\n'); total+=line.length(); }
                    }
                    String page=html.toString();
                    Pattern imagePattern=Pattern.compile("(?:https?:)?//(?:www\\.)?novask\\.in/([A-Za-z0-9_-]+)\\.png",Pattern.CASE_INSENSITIVE);
                    Matcher images=imagePattern.matcher(page);
                    java.util.HashSet<String> seen=new java.util.HashSet<>();
                    while(images.find() && result.size()<24) {
                        String id=images.group(1);
                        if(!seen.add(id)) continue;
                        String imageUrl="https://novask.in/"+id+".png";
                        String name="Minecraft Skin "+id;
                        int from=Math.max(0,images.start()-300), to=Math.min(page.length(),images.end()+100);
                        String around=page.substring(from,to).replaceAll("<[^>]+>"," ").replaceAll("\\s+"," ").trim();
                        if(around.length()>2) {
                            around=around.replaceAll("&amp;","&").trim();
                            if(around.length()>32) around=around.substring(0,32).trim();
                            if(!around.isEmpty()) name=around;
                        }
                        result.add(new OnlineSkin(name,id,imageUrl));
                    }
                    if(result.isEmpty()) {
                        Pattern p=Pattern.compile("href=[\\"']/(?:skin|post)/([A-Za-z0-9_-]+)/([^\\"'#?]+)",Pattern.CASE_INSENSITIVE);
                        Matcher m=p.matcher(page);
                        while(m.find() && result.size()<24) {
                            String routeId=m.group(1), slug=m.group(2);
                            Matcher numeric=Pattern.compile("(\\d{5,})").matcher(slug);
                            String id=numeric.find()?numeric.group(1):routeId;
                            if(!seen.add(id)) continue;
                            String name=slug.replaceAll("(?i)\\.(html|png)$","").replace('-',' ').replace('_',' ');
                            result.add(new OnlineSkin(name.isEmpty()?"Minecraft Skin":name,id,"https://novask.in/"+id+".png"));
                        }
                    }
                    if(result.isEmpty()) error="NovaSkin returned no readable results. Try another search.";
                } catch(Exception e) { error=e.getMessage()==null?"Could not connect to NovaSkin":e.getMessage(); }
                finally { if(connection!=null) connection.disconnect(); }
                return result;
            }
            protected void onPostExecute(List<OnlineSkin> results) {
                onlineLoading=false; onlineSkins.addAll(results); renderGrid();
                status.setText(results.isEmpty()?"Online search failed: "+(error==null?"No skins found":error):"Found "+results.size()+" skins • Tap once to apply");
            }
        }.execute();
    }

    private void applyOnlineSkin(OnlineSkin skin, Button apply) {
        if(skin==null)return;
        apply.setEnabled(false); apply.setText("Applying…");
        status.setText("Applying "+skin.name+"…");
        new android.os.AsyncTask<Void,Void,File>() {
            String error;
            protected File doInBackground(Void... unused) {
                try { return store.importRemoteSkin(skin.url,"NovaSkin_"+skin.id); }
                catch(Exception e) { error=e.getMessage(); return null; }
            }
            protected void onPostExecute(File file) {
                if(file==null) {
                    apply.setEnabled(true); apply.setText("Tap to retry");
                    status.setText("Download failed: "+(error==null?"Skin texture unavailable":error));
                    return;
                }
                active.skin=file.getName(); active.skinEnabled=true;
                String n=skin.name.toLowerCase(Locale.ROOT);
                if(n.contains("alex")||n.contains("slim")||n.contains("girl"))active.model="slim";
                else active.model="classic";
                onlineMode=false;
                search.setText("");
                saveCurrent("Applied "+skin.name);
            }
        }.execute();
    }

    private void updateImportButton(){ if(importButton!=null) importButton.setText(showingCapes?"＋  Import / Replace Cape":"＋  Import / Replace Skin"); }

    private void updateTabs(){
        skinsTab.setBackground(shape(showingCapes?CARD:BLUE,10,showingCapes?Color.rgb(0,91,210):BLUE,1));
        capesTab.setBackground(shape(showingCapes?BLUE:CARD,10,showingCapes?BLUE:Color.rgb(31,53,83),1));
        search.setHint(showingCapes?"Search capes...":"Search skins...");
    }

    private void renderGrid(){
        if(grid==null)return;grid.removeAllViews();
        if(onlineMode){renderOnlineGrid();return;}
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

        private void updateImportButton(){ if(importButton!=null) importButton.setText(showingCapes?"＋  Import / Replace Cape":"＋  Import / Replace Skin"); }

    private void updateTabs(){
        skinsTab.setBackground(shape(showingCapes?CARD:BLUE,10,showingCapes?Color.rgb(0,91,210):BLUE,1));
        capesTab.setBackground(shape(showingCapes?BLUE:CARD,10,showingCapes?BLUE:Color.rgb(31,53,83),1));
        search.setHint(showingCapes?"Search capes...":"Search skins...");
    }

    private void renderGrid(){
        if(grid==null)return;grid.removeAllViews();
        if(onlineMode){renderOnlineGrid();return;}
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

    private void renderOnlineGrid() {
        if(onlineLoading) {
            TextView loading=label("Loading NovaSkin results…",14,MUTED); loading.setGravity(Gravity.CENTER);
            grid.addView(loading,lp(-1,90)); return;
        }
        List<OnlineSkin> visible=new ArrayList<>();
        for(OnlineSkin item:onlineSkins) if(item.name.toLowerCase(Locale.ROOT).contains(query)) visible.add(item);
        if(visible.isEmpty()) {
            TextView empty=label("No online skins loaded. Search a name, then tap Online Skins.",13,MUTED);
            empty.setGravity(Gravity.CENTER); grid.addView(empty,lp(-1,90)); return;
        }
        int columns=4;
        for(int start=0;start<visible.size();start+=columns) {
            LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
            for(int col=0;col<columns && start+col<visible.size();col++) {
                OnlineSkin item=visible.get(start+col);
                LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setGravity(Gravity.CENTER_HORIZONTAL);
                card.setPadding(d(5),d(5),d(5),d(5)); card.setBackground(shape(CARD,12,Color.rgb(20,48,83),1));
                ImageView thumb=new ImageView(this); thumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
                // Native image download: the texture URL is cached in app memory only for this screen.
                Bitmap cached=onlineThumbCache.get(item.url); if(cached!=null&&!cached.isRecycled()) thumb.setImageBitmap(cached); else thumb.setImageDrawable(new android.graphics.drawable.ColorDrawable(Color.rgb(10,20,35)));
                card.addView(thumb,lp(-1,70));
                TextView name=label(item.name,10,WHITE); name.setGravity(Gravity.CENTER); name.setMaxLines(1);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END); card.addView(name,lp(-1,24));
                Button apply=button("Apply",true); apply.setTextSize(10); card.addView(apply,lp(-1,29));
                apply.setOnClickListener(v->applyOnlineSkin(item,apply));
                LinearLayout.LayoutParams cardLp=new LinearLayout.LayoutParams(0,d(145),1);
                cardLp.setMargins(d(3),d(3),d(3),d(3)); row.addView(card,cardLp);
                new android.os.AsyncTask<Void,Void,Bitmap>() {
                    protected Bitmap doInBackground(Void... unused) {
                        HttpURLConnection con=null;
                        try {
                            con=(HttpURLConnection)new URL(item.url).openConnection();
                            con.setConnectTimeout(7000); con.setReadTimeout(9000);
                            con.setRequestProperty("User-Agent","OrynLauncherPlus/5.0");
                            try(java.io.InputStream in=con.getInputStream()) { return BitmapFactory.decodeStream(in); }
                        } catch(Exception ignored) { return null; }
                        finally { if(con!=null)con.disconnect(); }
                    }
                    protected void onPostExecute(Bitmap b) {
                        if(b!=null && thumb.getParent()!=null) {
                            int w=b.getWidth(),h=b.getHeight();
                            Bitmap face=null;
                            int unit=Math.max(1,Math.min(w,h)/64);
                            int left=8*unit, top=8*unit, size=8*unit;
                            if(w>=left+size && h>=top+size) face=Bitmap.createBitmap(b,left,top,size,size);
                            if(face!=null) {
                                Bitmap crisp=Bitmap.createScaledBitmap(face,128,128,false);
                                if(face!=b)face.recycle(); b.recycle();
                                onlineThumbCache.put(item.url,crisp); thumb.setImageBitmap(crisp);
                            } else { onlineThumbCache.put(item.url,b); thumb.setImageBitmap(b); }
                        } else if(b!=null)b.recycle();
                    }
                }.executeOnExecutor(android.os.AsyncTask.THREAD_POOL_EXECUTOR);
            }
            while(row.getChildCount()<columns) row.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
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
