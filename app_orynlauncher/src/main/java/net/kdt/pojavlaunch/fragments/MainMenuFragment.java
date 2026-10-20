package net.kdt.pojavlaunch.fragments;

import net.kdt.pojavlaunch.OrynDownloadActivity;
import net.kdt.pojavlaunch.OrynCrashViewerActivity;
import net.kdt.pojavlaunch.OrynFileManagerActivity;
import net.kdt.pojavlaunch.OrynCosmeticsActivity;
import net.kdt.pojavlaunch.OrynSettingsActivity;

import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceVideoFragment;
import git.artdeell.mojo.R;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.instances.DisplayInstance;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.authenticator.accounts.Account;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.PojavApplication;

import java.io.File;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;

    private final ActivityResultLauncher<Object> mModInstallerLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("jar"), (data)->{
                if(data != null) Tools.launchModInstaller(requireContext(), data);
            });

    public MainMenuFragment(){
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // Wire the two home tools first, so later profile/skin initialization cannot prevent clicks.
        ImageButton cursorHomeButton = view.findViewById(R.id.oryn_cursor_home_button);
        ImageButton aiAssistantButton = view.findViewById(R.id.oryn_ai_assistant_button);
        if (cursorHomeButton != null) {
            cursorHomeButton.setClickable(true);
            cursorHomeButton.setFocusable(true);
            cursorHomeButton.setOnClickListener(v -> showCursorStudio());
        }
        if (aiAssistantButton != null) {
            aiAssistantButton.setClickable(true);
            aiAssistantButton.setFocusable(true);
            aiAssistantButton.setOnClickListener(v -> showOrynAssistant());
        }
        View mDiscordButton = view.findViewById(R.id.social_media_button);
        ImageButton mCustomControlButton = view.findViewById(R.id.custom_control_button);
        ImageButton mInstallJarButton = view.findViewById(R.id.install_jar_button);
        View mShareLogsButton = view.findViewById(R.id.share_logs_button);
        ImageButton mOpenDirectoryButton = view.findViewById(R.id.open_files_button);
        ImageButton mDownloadButton = view.findViewById(R.id.download_button);
        ImageButton mCrashButton = view.findViewById(R.id.crash_viewer_button);
        ImageButton mCosmeticsButton = view.findViewById(R.id.cosmetics_button);
        ImageButton mFilesNavButton = view.findViewById(R.id.oryn_files_nav);
        ImageButton mCrashNavButton = view.findViewById(R.id.oryn_crash_nav);
        View resourceLibraryCard = view.findViewById(R.id.oryn_news_card);
        View installationManagerCard = view.findViewById(R.id.oryn_about_card);
        loadCurrentAccountSkin(view);
        // Premium Edition badge: show the OrynLauncher Plus 1.1 offer.
        View premiumEditionButton = view.findViewById(R.id.oryn_premium_edition_button);
        if (premiumEditionButton != null) {
            premiumEditionButton.setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                    .setTitle("ORYNLAUNCHER PLUS")
                    .setMessage("OrynLauncher Plus 1.1 Edition\\n\\n₹100 only\\n\\nUnlock the Plus edition and get access to the OrynLauncher Plus experience.\\n\\nContact the OrynLauncher team to purchase.")
                    .setPositiveButton("Contact to purchase", (dialog, which) ->
                            Tools.openURL(requireActivity(), "https://discord.gg/cpcBKkESm3"))
                    .setNegativeButton("Not now", null)
                    .show());
        }

        // The Manage Account row replaces the removed top-right account header.
        View manageAccountRow = view.findViewById(R.id.oryn_manage_account);
        if (manageAccountRow != null) {
            manageAccountRow.setClickable(true);
            manageAccountRow.setFocusable(true);
            manageAccountRow.setOnClickListener(v ->
                    Tools.swapFragment(requireActivity(), SelectAuthFragment.class,
                            SelectAuthFragment.TAG, null));
        }

        ImageButton mEditProfileButton = view.findViewById(R.id.edit_profile_button);
        Button mPlayButton = view.findViewById(R.id.play_button);
        Button mOpenStoreButton = view.findViewById(R.id.oryn_open_store_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);
        TextView mSelectedVersion = view.findViewById(R.id.oryn_selected_version);
        TextView mSelectedInstanceName = view.findViewById(R.id.oryn_selected_instance_name);
        LinearLayout mInstanceName = view.findViewById(R.id.oryn_instance_name);
        TextView mInstanceVersion = view.findViewById(R.id.oryn_instance_version);
        // Render the real saved instance list and mark the active profile.
        try {
            net.kdt.pojavlaunch.instances.Instances instanceCollection =
                    net.kdt.pojavlaunch.instances.Instances.loadDisplay();
            if (mInstanceName != null) {
                mInstanceName.removeAllViews();
                for (DisplayInstance profile : instanceCollection.list) {
                    TextView profileCard = new TextView(requireContext());
                    String profileName = Tools.isValidString(profile.name) ? profile.name : "Minecraft";
                    String profileVersion = Tools.isValidString(profile.versionId) ? profile.versionId : "Version not selected";
                    profileCard.setText(profileName + "\n" + profileVersion);
                    profileCard.setTextColor(0xFFFFFFFF);
                    profileCard.setTextSize(11);
                    profileCard.setPadding(10, 9, 10, 9);
                    profileCard.setMaxLines(2);
                    profileCard.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    profileCard.setBackgroundResource(R.drawable.oryn_account_pill);
                    LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    cardParams.bottomMargin = (int) (5 * getResources().getDisplayMetrics().density);
                    profileCard.setLayoutParams(cardParams);
                    boolean isSelected = instanceCollection.list.indexOf(profile) == instanceCollection.selectedIndex;
                    if (isSelected) profileCard.setBackgroundResource(R.drawable.oryn_pill);
                    profileCard.setOnClickListener(v -> {
                        Instances.setSelectedInstance(profile);
                        updateSelectedProfile(profile, mSelectedInstanceName, mInstanceVersion, mSelectedVersion, mPlayButton);
                        for (int i = 0; i < mInstanceName.getChildCount(); i++) {
                            View child = mInstanceName.getChildAt(i);
                            child.setBackgroundResource(child == profileCard ? R.drawable.oryn_pill : R.drawable.oryn_account_pill);
                        }
                    });
                    mInstanceName.addView(profileCard);
                    if (isSelected) updateSelectedProfile(profile, mSelectedInstanceName, mInstanceVersion, mSelectedVersion, mPlayButton);
                }
            }
        } catch (java.io.IOException | RuntimeException exception) {
            if (mSelectedInstanceName != null) mSelectedInstanceName.setText("Minecraft");
            if (mInstanceVersion != null) mInstanceVersion.setText("Instance list unavailable");
        }

        if (mVersionSpinner != null) {
            mVersionSpinner.setOnSelectionChangedListener(instance -> {
                if (mSelectedVersion == null) return;
                if (instance != null) {
                    updateSelectedProfile(instance, mSelectedInstanceName, mInstanceVersion, mSelectedVersion, mPlayButton);
                } else {
                    mSelectedVersion.setText("No version installed");
                    if (mInstanceVersion != null) mInstanceVersion.setText("No version installed");
                    mPlayButton.setText("INSTALL GAME\nNo version installed");
                }
            });
        }

        if (mDiscordButton != null) mDiscordButton.setOnClickListener(v -> Tools.openURL(requireActivity(), "https://discord.gg/cpcBKkESm3"));
        if (mCustomControlButton != null) mCustomControlButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));

        if (mInstallJarButton != null) mInstallJarButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), OrynSettingsActivity.class)));
        if (mEditProfileButton != null && mVersionSpinner != null) mEditProfileButton.setOnClickListener(v -> mVersionSpinner.openProfileEditor(requireActivity()));

        if (mPlayButton != null) mPlayButton.setOnClickListener(v -> {
            // Update Discord as soon as the actual PLAY button is pressed, before the launcher
            // hands off to the game-loading flow (which can show a black screen for a while).
            try {
                Instance selectedInstance = Instances.loadSelectedInstance();
                if (selectedInstance != null) {
                    net.kdt.pojavlaunch.utils.OrynDiscordPresence.setPlaying(selectedInstance.versionId);
                } else {
                    net.kdt.pojavlaunch.utils.OrynDiscordPresence.setPlaying(null);
                }
            } catch (RuntimeException error) {
                net.kdt.pojavlaunch.utils.OrynDiscordPresence.setPlaying(null);
            }
            ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
        });
        if (mOpenStoreButton != null) mOpenStoreButton.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), OrynDownloadActivity.class)));

        if (mShareLogsButton != null) mShareLogsButton.setOnClickListener((v) -> shareLog(requireContext()));

        // Open the dedicated Oryn instance manager; creation and editing still use the existing backend.
        if (mOpenDirectoryButton != null) mOpenDirectoryButton.setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), InstancesManagementFragment.class,
                        InstancesManagementFragment.TAG, null));
        if (mDownloadButton != null) mDownloadButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), OrynDownloadActivity.class)));
        if (mCrashButton != null) mCrashButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), OrynCrashViewerActivity.class)));
        if (mCosmeticsButton != null) mCosmeticsButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), OrynCosmeticsActivity.class)));
        if (mFilesNavButton != null) mFilesNavButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), OrynFileManagerActivity.class)));
        if (mCrashNavButton != null) mCrashNavButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), OrynCrashViewerActivity.class)));

        // Resource Library opens Oryn Download Center (mods, resource packs and shaders).
        if (resourceLibraryCard != null) {
            resourceLibraryCard.setClickable(true);
            resourceLibraryCard.setFocusable(true);
            resourceLibraryCard.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), OrynDownloadActivity.class)));
        }

        // Installation Manager opens the existing instance/profile manager.
        if (installationManagerCard != null) {
            installationManagerCard.setClickable(true);
            installationManagerCard.setFocusable(true);
            installationManagerCard.setOnClickListener(v ->
                    Tools.swapFragment(requireActivity(), InstancesManagementFragment.class,
                            InstancesManagementFragment.TAG, null));
        }

    }




    private void showCursorStudio() {
        final android.content.SharedPreferences prefs =
                android.preference.PreferenceManager.getDefaultSharedPreferences(requireContext());
        final int[] colors = {0xFFFFFFFF,0xFF37DFFF,0xFF438BFF,0xFFAA66FF,0xFFFF66AA,
                0xFFFF5252,0xFF54E38E,0xFFFFD54F,0xFF111827,0xFFFF8A3D};
        final String[] names = {"White","Cyan","Blue","Purple","Pink","Red","Green","Yellow","Dark","Orange"};
        final String[] shapes = {"Arrow","Crosshair","Circle"};
        final int pad = (int)(16 * getResources().getDisplayMetrics().density);
        final int[] selectedColor = {prefs.getInt("oryn_custom_cursor_color",0xFFFFFFFF)};
        final String[] selectedShape = {prefs.getString("oryn_custom_cursor_shape","Crosshair")};

        android.widget.LinearLayout root = new android.widget.LinearLayout(requireContext());
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setPadding(pad,pad,pad,pad);
        root.setBackgroundColor(0xFF0B1423);

        android.widget.LinearLayout cursorHeader = new android.widget.LinearLayout(requireContext());
        cursorHeader.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        cursorHeader.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView cursorTitle = new TextView(requireContext());
        cursorTitle.setText("ORYN  /  CURSOR STUDIO");
        cursorTitle.setTextColor(0xFFFFFFFF);
        cursorTitle.setTextSize(20);
        cursorTitle.setTypeface(null,android.graphics.Typeface.BOLD);
        cursorHeader.addView(cursorTitle,new android.widget.LinearLayout.LayoutParams(0,-2,1));
        Button cursorClose = new Button(requireContext());
        cursorClose.setText("✕");
        cursorClose.setAllCaps(false);
        cursorHeader.addView(cursorClose,new android.widget.LinearLayout.LayoutParams(-2,-2));
        root.addView(cursorHeader);

        TextView subtitle = new TextView(requireContext());
        subtitle.setText("Customize your in-game pointer. Preview updates instantly.");
        subtitle.setTextColor(0xFFB8C9E5);
        subtitle.setTextSize(13);
        root.addView(subtitle);

        android.widget.LinearLayout previewRow = new android.widget.LinearLayout(requireContext());
        previewRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        previewRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        android.widget.Spinner shape = new android.widget.Spinner(requireContext());
        shape.setAdapter(new android.widget.ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item,shapes));
        shape.setSelection("Arrow".equals(selectedShape[0])?0:"Circle".equals(selectedShape[0])?2:1);
        previewRow.addView(shape,new android.widget.LinearLayout.LayoutParams(0,
                (int)(48*getResources().getDisplayMetrics().density),1));

        TextView previewLabel = new TextView(requireContext());
        previewLabel.setText("LIVE PREVIEW");
        previewLabel.setTextColor(0xFFB8C9E5);
        previewLabel.setTextSize(9);
        previewLabel.setGravity(android.view.Gravity.CENTER);
        android.widget.LinearLayout previewBox = new android.widget.LinearLayout(requireContext());
        previewBox.setOrientation(android.widget.LinearLayout.VERTICAL);
        previewBox.setGravity(android.view.Gravity.CENTER);
        android.graphics.drawable.GradientDrawable previewBg = new android.graphics.drawable.GradientDrawable();
        previewBg.setColor(0xFF111E31);
        previewBg.setCornerRadius(12*getResources().getDisplayMetrics().density);
        previewBox.setBackground(previewBg);
        previewBox.addView(previewLabel);
        View preview = new View(requireContext()) {
            private final android.graphics.Paint paint = new android.graphics.Paint(3);
            @Override protected void onDraw(android.graphics.Canvas canvas) {
                super.onDraw(canvas);
                float cx=getWidth()/2f, cy=getHeight()/2f;
                paint.setColor(selectedColor[0]); paint.setStrokeWidth(2.5f*getResources().getDisplayMetrics().density);
                paint.setStyle(android.graphics.Paint.Style.STROKE);
                if ("Circle".equals(selectedShape[0])) {
                    canvas.drawCircle(cx,cy,10*getResources().getDisplayMetrics().density,paint);
                    canvas.drawLine(cx-16*getResources().getDisplayMetrics().density,cy,cx-5*getResources().getDisplayMetrics().density,cy,paint);
                    canvas.drawLine(cx+5*getResources().getDisplayMetrics().density,cy,cx+16*getResources().getDisplayMetrics().density,cy,paint);
                } else if ("Arrow".equals(selectedShape[0])) {
                    paint.setStyle(android.graphics.Paint.Style.FILL);
                    android.graphics.Path path=new android.graphics.Path();
                    path.moveTo(cx-9,cy-14); path.lineTo(cx+8,cy+2); path.lineTo(cx,cy+2);
                    path.lineTo(cx+4,cy+11); path.lineTo(cx,cy+13); path.lineTo(cx-4,cy+4);
                    path.lineTo(cx-9,cy+10); path.close(); canvas.drawPath(path,paint);
                } else {
                    canvas.drawLine(cx-18*getResources().getDisplayMetrics().density,cy,cx-5*getResources().getDisplayMetrics().density,cy,paint);
                    canvas.drawLine(cx+5*getResources().getDisplayMetrics().density,cy,cx+18*getResources().getDisplayMetrics().density,cy,paint);
                    canvas.drawLine(cx,cy-18*getResources().getDisplayMetrics().density,cx,cy-5*getResources().getDisplayMetrics().density,paint);
                    canvas.drawLine(cx,cy+5*getResources().getDisplayMetrics().density,cx,cy+18*getResources().getDisplayMetrics().density,paint);
                    canvas.drawCircle(cx,cy,2*getResources().getDisplayMetrics().density,paint);
                }
            }
        };
        previewBox.addView(preview,new android.widget.LinearLayout.LayoutParams(
                (int)(84*getResources().getDisplayMetrics().density),(int)(66*getResources().getDisplayMetrics().density)));
        android.widget.LinearLayout.LayoutParams previewParams = new android.widget.LinearLayout.LayoutParams(
                (int)(112*getResources().getDisplayMetrics().density),-2);
        previewParams.leftMargin=pad/2;
        previewRow.addView(previewBox,previewParams);
        root.addView(previewRow);

        shape.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
                selectedShape[0]=shapes[pos];
                prefs.edit().putString("oryn_custom_cursor_shape",selectedShape[0])
                        .putString("oryn_custom_cursor_drawing","").apply();
                preview.invalidate();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p){}
        });

        TextView label = new TextView(requireContext());
        label.setText("COLOUR  ·  TAP A SWATCH");
        label.setTextColor(0xFFFFFFFF);
        label.setTypeface(null,android.graphics.Typeface.BOLD);
        android.widget.LinearLayout.LayoutParams labelParams = new android.widget.LinearLayout.LayoutParams(-1,-2);
        labelParams.topMargin=pad/2;
        root.addView(label,labelParams);

        android.widget.LinearLayout swatches = new android.widget.LinearLayout(requireContext());
        swatches.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        swatches.setGravity(android.view.Gravity.CENTER_VERTICAL);
        for(int i=0;i<colors.length;i++){
            final int color=colors[i]; final String name=names[i];
            android.widget.ImageButton chip=new android.widget.ImageButton(requireContext());
            android.graphics.drawable.GradientDrawable circle=new android.graphics.drawable.GradientDrawable();
            circle.setShape(android.graphics.drawable.GradientDrawable.OVAL); circle.setColor(color);
            circle.setStroke((int)(2*getResources().getDisplayMetrics().density),
                    selectedColor[0]==color?0xFFFFFFFF:0xFF52647C);
            chip.setBackground(circle); chip.setContentDescription(name); chip.setPadding(0,0,0,0);
            chip.setOnClickListener(v->{
                selectedColor[0]=color;
                prefs.edit().putInt("oryn_custom_cursor_color",color).putString("oryn_custom_cursor_drawing","").apply();
                for(int j=0;j<swatches.getChildCount();j++){
                    View item=swatches.getChildAt(j);
                    if(item instanceof ImageButton){
                        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
                        bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                        bg.setColor(colors[j]);
                        bg.setStroke((int)(2*getResources().getDisplayMetrics().density),
                                colors[j]==selectedColor[0]?0xFFFFFFFF:0xFF52647C);
                        item.setBackground(bg);
                    }
                }
                preview.invalidate();
            });
            android.widget.LinearLayout.LayoutParams cp=new android.widget.LinearLayout.LayoutParams(
                    (int)(25*getResources().getDisplayMetrics().density),(int)(25*getResources().getDisplayMetrics().density));
            cp.setMargins(0,pad/3,pad/3,0);
            swatches.addView(chip,cp);
        }
        android.widget.HorizontalScrollView colorScroll=new android.widget.HorizontalScrollView(requireContext());
        colorScroll.setHorizontalScrollBarEnabled(false);
        colorScroll.addView(swatches);
        root.addView(colorScroll);

        TextView note = new TextView(requireContext());
        note.setText("Tip: Crosshair is best for aiming; Circle gives a clean minimal pointer.");
        note.setTextColor(0xFF9EB3D2); note.setTextSize(12);
        android.widget.LinearLayout.LayoutParams noteParams=new android.widget.LinearLayout.LayoutParams(-1,-2);
        noteParams.topMargin=pad/2; root.addView(note,noteParams);

        Button reset=new Button(requireContext()); reset.setText("Reset cursor"); reset.setAllCaps(false);
        reset.setOnClickListener(v->{
            prefs.edit().remove("oryn_custom_cursor_color").putString("oryn_custom_cursor_shape","Crosshair")
                    .putString("oryn_custom_cursor_drawing","").apply();
            Toast.makeText(requireContext(),"Cursor reset to default crosshair",Toast.LENGTH_SHORT).show();
            showCursorStudio();
        });
        android.widget.LinearLayout.LayoutParams rp=new android.widget.LinearLayout.LayoutParams(-1,-2);
        rp.topMargin=pad/3; root.addView(reset,rp);
        Button saveCursor = new Button(requireContext());
        saveCursor.setText("SAVE CURSOR SETTINGS");
        saveCursor.setAllCaps(false);
        saveCursor.setTextColor(0xFFFFFFFF);
        saveCursor.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF1976D2));
        android.widget.LinearLayout.LayoutParams saveParams = new android.widget.LinearLayout.LayoutParams(-1,-2);
        saveParams.topMargin=pad;
        root.addView(saveCursor,saveParams);
        final AlertDialog cursorDialog = new AlertDialog.Builder(requireContext()).setView(root).create();
        cursorClose.setOnClickListener(v -> cursorDialog.dismiss());
        saveCursor.setOnClickListener(v -> {
            prefs.edit().putInt("oryn_custom_cursor_color",selectedColor[0])
                    .putString("oryn_custom_cursor_shape",selectedShape[0]).apply();
            Toast.makeText(requireContext(),"Cursor settings saved",Toast.LENGTH_SHORT).show();
            cursorDialog.dismiss();
        });
        cursorDialog.setOnShowListener(d -> {
            if (cursorDialog.getWindow() != null) {
                cursorDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                cursorDialog.getWindow().setDimAmount(0f);
                cursorDialog.getWindow().setLayout(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT);
                cursorDialog.getWindow().setGravity(android.view.Gravity.CENTER);
            }
        });
        cursorDialog.show();
    }

    /** Launcher-only conversational helper. This is an offline assistant, not a general-purpose AI. */
    private void showOrynAssistant() {
        final android.content.SharedPreferences prefs =
                android.preference.PreferenceManager.getDefaultSharedPreferences(requireContext());
        final int pad=(int)(14*getResources().getDisplayMetrics().density);
        android.widget.LinearLayout shell=new android.widget.LinearLayout(requireContext());
        shell.setOrientation(android.widget.LinearLayout.VERTICAL);
        shell.setPadding(pad,pad,pad,pad);
        shell.setBackgroundColor(0xFF081321);

        android.widget.LinearLayout assistantHeader=new android.widget.LinearLayout(requireContext());
        assistantHeader.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        assistantHeader.setGravity(android.view.Gravity.CENTER_VERTICAL);
        android.widget.LinearLayout brandStack=new android.widget.LinearLayout(requireContext());
        brandStack.setOrientation(android.widget.LinearLayout.VERTICAL);
        TextView brand=new TextView(requireContext());
        brand.setText("ORYN AI");
        brand.setTextColor(0xFFFFFFFF); brand.setTextSize(22);
        brand.setTypeface(null,android.graphics.Typeface.BOLD);
        TextView status=new TextView(requireContext());
        status.setText("LAUNCHER SUPPORT  ·  OFFLINE");
        status.setTextColor(0xFF55D6C2); status.setTextSize(10);
        brandStack.addView(brand); brandStack.addView(status);
        assistantHeader.addView(brandStack,new android.widget.LinearLayout.LayoutParams(0,-2,1));
        Button assistantClose=new Button(requireContext());
        assistantClose.setText("✕"); assistantClose.setAllCaps(false);
        assistantHeader.addView(assistantClose,new android.widget.LinearLayout.LayoutParams(-2,-2));
        shell.addView(assistantHeader);

        View divider=new View(requireContext());
        divider.setBackgroundColor(0xFF24364D);
        android.widget.LinearLayout.LayoutParams dividerParams=new android.widget.LinearLayout.LayoutParams(-1,(int)(1*getResources().getDisplayMetrics().density));
        dividerParams.topMargin=pad/2; dividerParams.bottomMargin=pad/2;
        shell.addView(divider,dividerParams);

        TextView intro=new TextView(requireContext());
        intro.setText("Hey! I'm Oryn Assistant ✦\nAsk me anything about OrynLauncher—settings, FPS, crashes, skins, mods or controls.");
        intro.setTextColor(0xFFEAF3FF); intro.setTextSize(14);
        shell.addView(intro);

        android.widget.ScrollView historyScroll=new android.widget.ScrollView(requireContext());
        android.widget.LinearLayout history=new android.widget.LinearLayout(requireContext());
        history.setOrientation(android.widget.LinearLayout.VERTICAL);
        history.setPadding(0,pad/2,0,pad/2);
        historyScroll.setFillViewport(true);
        historyScroll.addView(history);
        shell.addView(historyScroll,new android.widget.LinearLayout.LayoutParams(-1,0,1));

        android.widget.LinearLayout composer=new android.widget.LinearLayout(requireContext());
        composer.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        composer.setGravity(android.view.Gravity.CENTER_VERTICAL);
        android.widget.EditText input=new android.widget.EditText(requireContext());
        input.setSingleLine(true); input.setHint("Ask about the launcher…");
        input.setTextColor(0xFFFFFFFF); input.setHintTextColor(0xFF90A4BE);
        input.setPadding(pad/2,pad/3,pad/2,pad/3);
        input.setBackgroundColor(0xFF14243A);
        composer.addView(input,new android.widget.LinearLayout.LayoutParams(0,
                (int)(48*getResources().getDisplayMetrics().density),1));
        Button send=new Button(requireContext()); send.setText("Send"); send.setAllCaps(false);
        composer.addView(send,new android.widget.LinearLayout.LayoutParams(-2,-2));
        android.widget.LinearLayout.LayoutParams composerParams=new android.widget.LinearLayout.LayoutParams(-1,-2);
        composerParams.topMargin=pad/2; shell.addView(composer,composerParams);

        final AlertDialog dialog=new AlertDialog.Builder(requireContext()).setView(shell).create();
        assistantClose.setOnClickListener(v -> dialog.dismiss());

        android.view.View.OnClickListener sender=v->{
            String question=input.getText().toString().trim();
            if(question.isEmpty()) return;
            addAssistantBubble(history,question,true,pad);
            String answer=answerOrynLauncherQuestion(question,prefs);
            addAssistantBubble(history,answer,false,pad);
            input.setText("");
            historyScroll.post(()->historyScroll.fullScroll(View.FOCUS_DOWN));
        };
        send.setOnClickListener(sender);
        input.setOnEditorActionListener((v,actionId,event)->{
            sender.onClick(send);
            return true;
        });

        String[] prompts={"Improve FPS","Fix a crash","Skin not showing"};
        android.widget.LinearLayout quick=new android.widget.LinearLayout(requireContext());
        quick.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        for(String prompt:prompts){
            Button b=new Button(requireContext()); b.setText(prompt); b.setAllCaps(false); b.setTextSize(10);
            b.setOnClickListener(v->{input.setText(prompt); sender.onClick(send);});
            quick.addView(b,new android.widget.LinearLayout.LayoutParams(0,-2,1));
        }
        android.widget.LinearLayout.LayoutParams quickParams=new android.widget.LinearLayout.LayoutParams(-1,-2);
        quickParams.topMargin=pad/3; shell.addView(quick,quickParams);
        dialog.setOnShowListener(d->{
            if(dialog.getWindow()!=null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                dialog.getWindow().setDimAmount(0f);
                dialog.getWindow().setLayout(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT);
                dialog.getWindow().setGravity(android.view.Gravity.CENTER);
            }
            addAssistantBubble(history,"Hi! I'm here for OrynLauncher only. What's going on?",false,pad);
        });
        dialog.show();
    }

    private void addAssistantBubble(android.widget.LinearLayout history,String text,boolean user,int pad) {
        TextView bubble=new TextView(requireContext());
        bubble.setText((user?"YOU  ·  ":"ORYN AI  ·  ")+text);
        bubble.setTextColor(user?0xFFFFFFFF:0xFFD8E7FA);
        bubble.setTextSize(13);
        bubble.setPadding(pad/2,pad/3,pad/2,pad/3);
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(user?0xFF183D63:0xFF132238);
        bg.setCornerRadius(12*getResources().getDisplayMetrics().density);
        bubble.setBackground(bg);
        android.widget.LinearLayout.LayoutParams lp=new android.widget.LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=pad/3;
        history.addView(bubble,lp);
    }

    private String answerOrynLauncherQuestion(String question,android.content.SharedPreferences prefs) {
        String q=question.toLowerCase(java.util.Locale.ROOT);
        if(q.matches(".*\\b(hi|hello|hey|sup|yo)\\b.*"))
            return "Hey! 👋 Tell me what you need help with in OrynLauncher. I can help with settings, FPS, crashes, accounts, skins, mods, downloads and controls.";
        if(q.contains("fps")||q.contains("lag")||q.contains("slow")||q.contains("performance"))
            return "Let's tune performance: open Settings → Game → Video & Render. Try lowering resolution scale first, then test the game. Close background apps and use a Minecraft-compatible renderer. Change one setting at a time so you can undo anything that makes it worse.";
        if(q.contains("crash")||q.contains("close")||q.contains("launch fail")||q.contains("won't launch")||q.contains("not launch"))
            return "Open the launcher Crash Viewer and copy the latest error. Check that the selected Java runtime matches your Minecraft version, then temporarily disable recently added mods. Send me the first error line and Minecraft version and I'll help narrow it down.";
        if(q.contains("skin")||q.contains("cape")||q.contains("steve")||q.contains("alex"))
            return "Open Oryn Cosmetics, choose your skin or cape, apply/save it, then restart the game. If Steve/Alex still appears, tell me whether it happens in single-player, multiplayer, or both, plus your Minecraft version.";
        if(q.contains("mod")||q.contains("fabric")||q.contains("forge")||q.contains("install"))
            return "Use the launcher Downloads/Mod installer and make sure each mod matches the exact Minecraft version and loader (Fabric/Forge/NeoForge). Add one mod at a time if the game crashes, so the conflicting mod is easier to find.";
        if(q.contains("cursor")||q.contains("crosshair")||q.contains("mouse"))
            return "Tap the mouse icon on the home screen to open Cursor Studio. Choose Crosshair, Circle or Arrow, pick a colour, check the live preview beside the shape selector, then tap Save & close.";
        if(q.contains("account")||q.contains("microsoft")||q.contains("login")||q.contains("sign in"))
            return "Tap Manage Account on the home screen to add or switch your launcher account. If sign-in fails, check your connection and share the exact message shown—never share your password or access code.";
        if(q.contains("download")||q.contains("file")||q.contains("folder"))
            return "Use the Downloads or Files buttons on the home screen to find launcher downloads. If a file won't install, tell me its extension, Minecraft version and loader.";
        if(q.contains("control")||q.contains("touch"))
            return "Open Settings → Game → Control Layout to adjust your touch controls. You can also use the in-game control editor if it's available for your current profile.";
        if(q.contains("setting")||q.contains("video")||q.contains("render")||q.contains("resolution"))
            return "Open Settings and choose Game → Video & Render for graphics-related options. Tell me which setting you're looking for and I'll guide you through it.";
        if(q.contains("help")||q.contains("what can you do")||q.contains("who are you"))
            return "I'm Oryn Assistant, focused only on OrynLauncher. I can guide you through launcher settings, performance, crash logs, accounts, skins/capes, mods, downloads, controls and cursor customization. I'm an offline helper, so I don't have live internet or general ChatGPT abilities.";
        return "I can help with OrynLauncher only. Tell me which part you're using and what happens—e.g. Settings, FPS, crash, account, skin/cape, mods, downloads, controls or cursor. If there's an error, paste its exact text (but not passwords or login codes).";
    }

    /**
     * Display the selected Minecraft account's actual skin head in the home profile card.
     * Any skin refresh runs in the background so opening the home screen stays responsive.
     */
    private void loadCurrentAccountSkin(View root) {
        ImageView manageAccountAvatar = root.findViewById(R.id.oryn_manage_account_skin_avatar);
        if (manageAccountAvatar == null) return;

        // Set Steve immediately so an empty/local account never leaves the paper/user icon.
        Bitmap defaultFace = loadOrynDefaultSteveFace();
        if (defaultFace != null) {
            if (manageAccountAvatar != null) manageAccountAvatar.setImageBitmap(defaultFace);
        }

        PojavApplication.sExecutorService.execute(() -> {
            Bitmap face = null;
            String username = null;
            try {
                // Cosmetics skin takes priority: this is the skin the user selected in
                // Oryn Cosmetics, rather than only the Microsoft account skin.
                net.kdt.pojavlaunch.cosmetics.OrynCosmeticsStore store =
                        new net.kdt.pojavlaunch.cosmetics.OrynCosmeticsStore(requireContext());
                net.kdt.pojavlaunch.cosmetics.OrynCosmeticsStore.CosmeticProfile profile =
                        store.getActiveProfile();
                if (profile != null && profile.skinEnabled && Tools.isValidString(profile.skin)) {
                    File customSkin = new File(requireContext().getFilesDir(), "cosmetics/skins/" + profile.skin);
                    if (!customSkin.isFile()) customSkin = new File(profile.skin);
                    Bitmap skinBitmap = android.graphics.BitmapFactory.decodeFile(customSkin.getAbsolutePath());
                    if (skinBitmap != null) {
                        try {
                            face = new net.kdt.pojavlaunch.authenticator.accounts.SkinHeadRenderer()
                                    .render(128, skinBitmap);
                        } finally {
                            skinBitmap.recycle();
                        }
                    }
                }

                Account account = Accounts.getCurrent();
                if (face == null && account != null) {
                    face = account.getSkinFace();
                    if (face == null && !account.isLocal()) {
                        account.updateSkinFace();
                        face = account.getSkinFace();
                        if (face != null) ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
                    }
                }
                if (account != null) username = account.username;
            } catch (Exception ignored) {
                // Keep the already displayed Steve fallback if cosmetic/account loading fails.
            }

            final Bitmap finalFace = face;
            final String finalUsername = username;
            android.app.Activity activity = getActivity();
            if (activity == null) return;
            activity.runOnUiThread(() -> {
                if (finalFace != null) {
                    if (manageAccountAvatar != null) manageAccountAvatar.setImageBitmap(finalFace);
                }
            });
        });
    }

    private Bitmap loadOrynDefaultSteveFace() {
        Bitmap skin = null;
        try (java.io.InputStream input = requireContext().getAssets().open("skinview/oryn_steve_skin.png")) {
            skin = android.graphics.BitmapFactory.decodeStream(input);
        } catch (Exception ignored) {}
        if (skin == null) return null;
        try {
            return new net.kdt.pojavlaunch.authenticator.accounts.SkinHeadRenderer().render(128, skin);
        } catch (Exception ignored) {
            return null;
        } finally {
            skin.recycle();
        }
    }

    private void updateSelectedProfile(DisplayInstance profile, TextView heroName,
                                       TextView profileVersion, TextView selectedVersion,
                                       Button playButton) {
        String name = Tools.isValidString(profile.name) ? profile.name : "Minecraft";
        String versionId = Tools.isValidString(profile.versionId) ? profile.versionId : "";
        boolean installed = isVersionInstalled(versionId);
        String versionLabel = installed ? versionId : "No version installed";
        if (heroName != null) heroName.setText(name);
        if (profileVersion != null) profileVersion.setText(versionLabel);
        if (selectedVersion != null) selectedVersion.setText(versionLabel);
        if (playButton != null) {
            String shortVersion = shortVersion(versionId);
            playButton.setMaxLines(2);
            playButton.setEllipsize(android.text.TextUtils.TruncateAt.END);
            playButton.setTextSize(13);
            if (!Tools.isValidString(versionId)) {
                playButton.setText("SELECT VERSION\nNo version selected");
            } else if (installed) {
                playButton.setText("▶  LAUNCH\n" + shortVersion);
            } else {
                playButton.setText("INSTALL GAME\n" + shortVersion);
            }
        }
    }

    /**
     * Return the Minecraft version portion of a profile ID.
     * Loader IDs put their own version before the final Minecraft version.
     */
    private String shortVersion(String versionId) {
        if (!Tools.isValidString(versionId)) return "No version selected";
        String id = versionId.trim();

        if (id.startsWith("fabric-loader") || id.startsWith("quilt-loader")) {
            int lastHyphen = id.lastIndexOf('-');
            return lastHyphen >= 0 ? id.substring(lastHyphen + 1) : id;
        }

        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("1\\.\\d+(\\.\\d+)?").matcher(id);
        return matcher.find() ? matcher.group() : id;
    }

    /** Only display a game version as installed when its metadata exists on disk. */
    private boolean isVersionInstalled(String versionId) {
        if (!Tools.isValidString(versionId) || !Tools.isValidString(Tools.DIR_HOME_VERSION)) return false;
        File versionDirectory = new File(Tools.DIR_HOME_VERSION, versionId);
        File versionJson = new File(versionDirectory, versionId + ".json");
        return versionJson.isFile();
    }

    private void openGameDirectory(Context context) {
        Instance instance = Instances.loadSelectedInstance();
        if(instance == null) {
            Toast.makeText(context, R.string.no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        File gameDirectory = instance.getGameDirectory();
        if(FileUtils.ensureDirectorySilently(gameDirectory)) {
            openPath(context, gameDirectory, false);
        }else {
            Toast.makeText(context, R.string.gamedir_open_failed, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
        // Cosmetics Activity returns here after Apply; refresh both home avatars immediately.
        View root = getView();
        if (root != null) loadCurrentAccountSkin(root);
    }

    private void runInstallerWithConfirmation() {
        if (ProgressKeeper.getTaskCount() == 0) {
            mModInstallerLauncher.launch(null);
        } else Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
    }
}
