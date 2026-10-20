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
        android.widget.LinearLayout root = new android.widget.LinearLayout(requireContext());
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad,pad,pad,pad);
        root.setBackgroundColor(0xFF0B1423);
        TextView subtitle = new TextView(requireContext());
        subtitle.setText("Pick a style, then tap a colour swatch.");
        subtitle.setTextColor(0xFFB8C9E5);
        subtitle.setTextSize(13);
        root.addView(subtitle);
        android.widget.Spinner shape = new android.widget.Spinner(requireContext());
        shape.setAdapter(new android.widget.ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item,new String[]{"Arrow","Crosshair","Circle"}));
        String saved = prefs.getString("oryn_custom_cursor_shape","Arrow");
        shape.setSelection("Crosshair".equals(saved)?1:"Circle".equals(saved)?2:0);
        root.addView(shape,new android.widget.LinearLayout.LayoutParams(-1,(int)(48*getResources().getDisplayMetrics().density)));
        shape.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
                prefs.edit().putString("oryn_custom_cursor_shape",new String[]{"Arrow","Crosshair","Circle"}[pos])
                    .putString("oryn_custom_cursor_drawing","").apply();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
        TextView label = new TextView(requireContext());
        label.setText("CURSOR COLOUR");
        label.setTextColor(0xFFFFFFFF);
        label.setTypeface(null,android.graphics.Typeface.BOLD);
        android.widget.LinearLayout.LayoutParams labelParams = new android.widget.LinearLayout.LayoutParams(-1,-2);
        labelParams.topMargin=pad/2;
        root.addView(label,labelParams);
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(requireContext());
        scroll.setHorizontalScrollBarEnabled(false);
        android.widget.LinearLayout swatches = new android.widget.LinearLayout(requireContext());
        swatches.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        for(int i=0;i<colors.length;i++){
            final int color=colors[i]; final String name=names[i];
            android.widget.LinearLayout col=new android.widget.LinearLayout(requireContext());
            col.setOrientation(android.widget.LinearLayout.VERTICAL); col.setGravity(android.view.Gravity.CENTER);
            android.widget.ImageButton chip=new android.widget.ImageButton(requireContext());
            android.graphics.drawable.GradientDrawable circle=new android.graphics.drawable.GradientDrawable();
            circle.setShape(android.graphics.drawable.GradientDrawable.OVAL); circle.setColor(color);
            circle.setStroke((int)(2*getResources().getDisplayMetrics().density),
                prefs.getInt("oryn_custom_cursor_color",0xFFFFFFFF)==color?0xFFFFFFFF:0xFF52647C);
            chip.setBackground(circle); chip.setContentDescription(name); chip.setPadding(0,0,0,0);
            chip.setOnClickListener(v->{
                prefs.edit().putInt("oryn_custom_cursor_color",color).putString("oryn_custom_cursor_drawing","").apply();
                Toast.makeText(requireContext(),name+" cursor selected",Toast.LENGTH_SHORT).show();
                showCursorStudio();
            });
            col.addView(chip,new android.widget.LinearLayout.LayoutParams((int)(34*getResources().getDisplayMetrics().density),(int)(34*getResources().getDisplayMetrics().density)));
            TextView t=new TextView(requireContext()); t.setText(name); t.setTextColor(0xFFD8E5F8); t.setTextSize(9); t.setGravity(android.view.Gravity.CENTER);
            col.addView(t,new android.widget.LinearLayout.LayoutParams((int)(48*getResources().getDisplayMetrics().density),-2));
            android.widget.LinearLayout.LayoutParams cp=new android.widget.LinearLayout.LayoutParams(-2,-2); cp.setMargins(0,pad/3,pad/3,0); swatches.addView(col,cp);
        }
        scroll.addView(swatches); root.addView(scroll);
        android.widget.LinearLayout actions=new android.widget.LinearLayout(requireContext());
        actions.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        Button draw=new Button(requireContext()); draw.setText("Custom drawing"); draw.setAllCaps(false);
        draw.setOnClickListener(v->{startActivity(new Intent(requireContext(),OrynSettingsActivity.class));
            Toast.makeText(requireContext(),"Settings → Game → Custom Cursor Drawing",Toast.LENGTH_LONG).show();});
        Button reset=new Button(requireContext()); reset.setText("Reset"); reset.setAllCaps(false);
        reset.setOnClickListener(v->{prefs.edit().remove("oryn_custom_cursor_color").remove("oryn_custom_cursor_shape").remove("oryn_custom_cursor_drawing").apply();
            Toast.makeText(requireContext(),"Cursor reset",Toast.LENGTH_SHORT).show(); showCursorStudio();});
        actions.addView(draw,new android.widget.LinearLayout.LayoutParams(0,-2,1));
        actions.addView(reset,new android.widget.LinearLayout.LayoutParams(0,-2,1));
        android.widget.LinearLayout.LayoutParams ap=new android.widget.LinearLayout.LayoutParams(-1,-2); ap.topMargin=pad/2; root.addView(actions,ap);
        new AlertDialog.Builder(requireContext()).setTitle("Cursor Studio").setView(root).setPositiveButton("Done",null).show();
    }

    /** Offline quick-help panel; it does not claim to be a cloud generative AI. */
    private void showOrynAssistant() {
        android.widget.LinearLayout panel=new android.widget.LinearLayout(requireContext());
        panel.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad=(int)(16*getResources().getDisplayMetrics().density); panel.setPadding(pad,pad,pad,pad);
        TextView intro=new TextView(requireContext());
        intro.setText("Hey! I'm Oryn Assistant. Choose a topic for quick launcher help.");
        intro.setTextColor(0xFFEAF3FF); intro.setTextSize(14); panel.addView(intro);
        String[] topics={"Improve FPS","Fix launch crash","Cursor help","Skin not showing","Install mods"};
        String[] answers={
          "Open Settings → Game → Video & Render. Try a lower resolution scale and enable Oryn FPS Boost if available. Change one option at a time.",
          "Open Settings → Advanced → Oryn Crash Viewer. Check the latest error and verify the Java runtime matches your Minecraft version.",
          "Tap the mouse icon on the home top bar. Choose a cursor shape and one of the side-by-side colour swatches.",
          "Check that the skin is enabled in Oryn Cosmetics, apply it, then restart the game. Some servers can override skins.",
          "Open Downloads and use mods that match both your Minecraft version and mod loader."
        };
        for(int i=0;i<topics.length;i++){
            final String answer=answers[i]; Button b=new Button(requireContext()); b.setText(topics[i]); b.setAllCaps(false);
            b.setOnClickListener(v->new AlertDialog.Builder(requireContext()).setTitle("Oryn Assistant").setMessage(answer).setPositiveButton("Got it",null).show());
            android.widget.LinearLayout.LayoutParams bp=new android.widget.LinearLayout.LayoutParams(-1,-2); bp.topMargin=pad/4; panel.addView(b,bp);
        }
        new AlertDialog.Builder(requireContext()).setTitle("✦ Oryn Assistant").setView(panel).setNegativeButton("Close",null).show();
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
