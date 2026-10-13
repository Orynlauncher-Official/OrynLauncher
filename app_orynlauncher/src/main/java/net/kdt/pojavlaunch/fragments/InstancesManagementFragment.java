package net.kdt.pojavlaunch.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.DisplayInstance;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.InstanceIconProvider;
import net.kdt.pojavlaunch.instances.Instances;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class InstancesManagementFragment extends Fragment {
    public static final String TAG = "InstancesManagementFragment";
    private static final int BLUE = 0xFF3197FF, SURFACE = 0xFF111B2B, CARD = 0xFF142031;
    private static final int TEXT = 0xFFEFF5FF, MUTED = 0xFF91A4C1;
    private LinearLayout list, filters;
    private TextView count;
    private List<DisplayInstance> profiles = new ArrayList<>();
    private String filter = "All";

    public InstancesManagementFragment() { super(R.layout.fragment_instances_management); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        list = view.findViewById(R.id.instances_list);
        filters = view.findViewById(R.id.instances_filter_row);
        count = view.findViewById(R.id.instances_count_label);
        ((TextView)view.findViewById(R.id.instances_directory_path)).setText(new File(Tools.DIR_GAME_HOME).getAbsolutePath());
        view.findViewById(R.id.instances_back).setOnClickListener(v -> Tools.backToMainMenu(requireActivity()));
        view.findViewById(R.id.instances_refresh).setOnClickListener(v -> refresh());
        view.findViewById(R.id.instances_open_directory).setOnClickListener(v -> openGameFolder());
        view.findViewById(R.id.instances_directory_card).setOnClickListener(v -> openGameFolder());
        view.findViewById(R.id.instances_install_new).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), ProfileTypeSelectFragment.class, ProfileTypeSelectFragment.TAG, null));
        for (String label : new String[]{"All", "Vanilla", "Modded"}) {
            TextView chip = new TextView(requireContext());
            chip.setText(label); chip.setTextSize(13); chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(15), dp(9), dp(15), dp(9));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(38));
            lp.rightMargin = dp(8); chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> { filter = label; styleFilters(); render(); });
            filters.addView(chip);
        }
        refresh();
    }

    @Override public void onResume() { super.onResume(); if (list != null) refresh(); }

    private void refresh() {
        if (!isAdded() || list == null) return;
        try {
            profiles = new ArrayList<>(Instances.loadDisplay().list);
            render();
        } catch (IOException | RuntimeException e) {
            list.removeAllViews();
            count.setText("INSTALLED PROFILES");
            message("Unable to read profiles. Check storage access and try Refresh.");
        }
    }

    private void styleFilters() {
        for (int i=0; i<filters.getChildCount(); i++) {
            TextView chip=(TextView)filters.getChildAt(i);
            boolean selected=chip.getText().toString().equals(filter);
            chip.setTextColor(selected ? Color.WHITE : MUTED);
            chip.setBackground(shape(selected ? 0xFF173A5B : SURFACE, selected ? BLUE : 0xFF26364C, 20));
        }
    }

    private void render() {
        if (!isAdded() || list == null) return;
        styleFilters(); list.removeAllViews();
        List<DisplayInstance> shown=new ArrayList<>();
        for (DisplayInstance p:profiles) {
            if ("All".equals(filter) || ("Modded".equals(filter) && modded(p))
                    || ("Vanilla".equals(filter) && !modded(p))) shown.add(p);
        }
        count.setText("INSTALLED PROFILES  •  " + shown.size());
        if (shown.isEmpty()) { message("No " + filter.toLowerCase(Locale.ROOT) + " profiles found. Use Install / Create to add one."); return; }
        for (DisplayInstance p:shown) addCard(p);
    }

    private void addCard(DisplayInstance p) {
        Context c=requireContext();
        LinearLayout card=new LinearLayout(c); card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL); card.setPadding(dp(12),dp(11),dp(8),dp(11));
        card.setBackground(shape(CARD,0xFF26364C,17));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2); cp.bottomMargin=dp(9); card.setLayoutParams(cp);

        ImageView icon=new ImageView(c); icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Drawable d=InstanceIconProvider.fetchIcon(getResources(),p); icon.setImageDrawable(d);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(46),dp(46)); ip.rightMargin=dp(12); card.addView(icon,ip);

        LinearLayout info=new LinearLayout(c); info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        TextView title=new TextView(c); title.setText(Tools.validOrNullString(p.name)==null?"Minecraft":p.name);
        title.setTextColor(TEXT); title.setTextSize(15); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        title.setMaxLines(1); title.setEllipsize(android.text.TextUtils.TruncateAt.END); info.addView(title);
        TextView sub=new TextView(c);
        sub.setText((Tools.validOrNullString(p.versionId)==null?"Version not selected":p.versionId)+(modded(p)?"  •  Modded":"  •  Vanilla"));
        sub.setTextColor(MUTED); sub.setTextSize(12); sub.setMaxLines(2); info.addView(sub);

        boolean active=isSelected(p);
        TextView badge=new TextView(c); badge.setText(active?"ACTIVE":"SELECT");
        badge.setTextColor(active?0xFF75C2FF:MUTED); badge.setTextSize(10); badge.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        badge.setPadding(dp(8),dp(6),dp(8),dp(6)); badge.setBackground(shape(active?0xFF143657:0xFF253348,0xFF30445C,16));
        card.addView(badge);
        TextView more=new TextView(c); more.setText("⋮"); more.setTextColor(TEXT); more.setTextSize(24);
        more.setGravity(Gravity.CENTER); more.setPadding(dp(12),0,dp(8),0); card.addView(more);
        card.setOnClickListener(v -> select(p)); badge.setOnClickListener(v -> select(p));
        more.setOnClickListener(v -> actions(p)); card.setOnLongClickListener(v -> { actions(p); return true; });
        list.addView(card);
    }

    private boolean isSelected(DisplayInstance p) {
        try {
            Instance selected = Instances.loadSelectedInstance();
            if (selected == null) return false;
            return safeEquals(selected.name, p.name) && safeEquals(selected.versionId, p.versionId);
        } catch (RuntimeException e) { return false; }
    }

    private boolean safeEquals(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }

    private void select(DisplayInstance p) {
        try { Instances.setSelectedInstance(p); refresh(); Toast.makeText(requireContext(),"Selected profile",Toast.LENGTH_SHORT).show(); }
        catch (RuntimeException e) { Toast.makeText(requireContext(),"Couldn't select this profile",Toast.LENGTH_SHORT).show(); }
    }

    private void actions(DisplayInstance p) {
        new AlertDialog.Builder(requireContext()).setTitle(Tools.validOrNullString(p.name)==null?"Minecraft":p.name)
                .setItems(new String[]{"Select profile","Edit settings","Rename profile","Open game folder","Delete profile"},(dialog,which)->{
                    if(which==0) select(p);
                    else if(which==1) { Instances.setSelectedInstance(p); Tools.swapFragment(requireActivity(),InstanceEditorFragment.class,InstanceEditorFragment.TAG,null); }
                    else if(which==2) rename(p);
                    else if(which==3) { Instances.setSelectedInstance(p); openGameFolder(); }
                    else if(which==4) delete(p);
                }).show();
    }

    private void rename(DisplayInstance p) {
        Instances.setSelectedInstance(p);
        EditText input=new EditText(requireContext()); input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setText(p.name==null?"":p.name); input.setSelection(input.length());
        new AlertDialog.Builder(requireContext()).setTitle("Rename profile").setView(input)
                .setNegativeButton("Cancel",null).setPositiveButton("Save",(dialog,which)->{
                    try {
                        Instance instance=Instances.loadSelectedInstance();
                        if(instance==null) throw new IOException("Profile is missing");
                        instance.name=input.getText().toString().trim();
                        Instances.renameInstanceDirectory(instance,instance.name);
                        instance.write(); refresh();
                    } catch(Exception e) { Toast.makeText(requireContext(),"Rename failed: "+e.getMessage(),Toast.LENGTH_LONG).show(); }
                }).show();
    }

    private void delete(DisplayInstance p) {
        new AlertDialog.Builder(requireContext()).setTitle("Delete profile?")
                .setMessage("This removes the selected instance and its instance data. This cannot be undone.")
                .setNegativeButton("Cancel",null).setPositiveButton("Delete",(dialog,which)->{
                    try {
                        Instances.setSelectedInstance(p);
                        Instance instance=Instances.loadSelectedInstance();
                        if(instance==null) throw new IOException("Profile is missing");
                        Instances.removeInstance(instance); refresh();
                    } catch(Exception e) { Toast.makeText(requireContext(),"Delete failed: "+e.getMessage(),Toast.LENGTH_LONG).show(); }
                }).show();
    }

    private void openGameFolder() {
        try {
            File folder=new File(Tools.DIR_GAME_HOME);
            if(!folder.exists()&&!folder.mkdirs()) throw new IOException("Cannot create game directory");
            Tools.openPath(requireContext(),folder,false);
        } catch(Exception e) { Toast.makeText(requireContext(),"Couldn't open game folder: "+e.getMessage(),Toast.LENGTH_LONG).show(); }
    }

    private void message(String text) {
        TextView empty=new TextView(requireContext()); empty.setText(text); empty.setTextColor(MUTED);
        empty.setTextSize(13); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(20),dp(24),dp(20),dp(24));
        empty.setBackground(shape(SURFACE,0xFF24354B,17)); list.addView(empty,new LinearLayout.LayoutParams(-1,-2));
    }

    private boolean modded(DisplayInstance p) {
        String v=p.versionId==null?"":p.versionId.toLowerCase(Locale.ROOT);
        return v.contains("forge")||v.contains("fabric")||v.contains("neoforge")||v.contains("quilt")||v.contains("optifine")||v.contains("liteloader");
    }
    private GradientDrawable shape(int fill,int stroke,int radius) {
        GradientDrawable d=new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius)); d.setStroke(dp(1),stroke); return d;
    }
    private int dp(float value) { return Math.round(value*getResources().getDisplayMetrics().density); }
}
