package net.kdt.pojavlaunch.fragments;

import net.kdt.pojavlaunch.OrynDownloadActivity;
import net.kdt.pojavlaunch.OrynCrashViewerActivity;
import net.kdt.pojavlaunch.OrynFileManagerActivity;
import net.kdt.pojavlaunch.OrynCosmeticsActivity;

import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
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

        ImageButton mEditProfileButton = view.findViewById(R.id.edit_profile_button);
        Button mPlayButton = view.findViewById(R.id.play_button);
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
                    profileCard.setTextColor(0xFFF0F5FC);
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
                        updateSelectedProfile(profile, mSelectedInstanceName, mInstanceVersion, mSelectedVersion);
                        for (int i = 0; i < mInstanceName.getChildCount(); i++) {
                            View child = mInstanceName.getChildAt(i);
                            child.setBackgroundResource(child == profileCard ? R.drawable.oryn_pill : R.drawable.oryn_account_pill);
                        }
                    });
                    mInstanceName.addView(profileCard);
                    if (isSelected) updateSelectedProfile(profile, mSelectedInstanceName, mInstanceVersion, mSelectedVersion);
                }
            }
        } catch (java.io.IOException | RuntimeException exception) {
            if (mSelectedInstanceName != null) mSelectedInstanceName.setText("Minecraft");
            if (mInstanceVersion != null) mInstanceVersion.setText("Instance list unavailable");
        }

        if (mVersionSpinner != null) {
            mVersionSpinner.setOnSelectionChangedListener(instance -> {
                if (mSelectedVersion == null) return;
                if (instance != null && Tools.isValidString(instance.versionId)) {
                    mSelectedVersion.setText(instance.versionId);
                    if (mInstanceVersion != null) mInstanceVersion.setText(instance.versionId);
                } else {
                    mSelectedVersion.setText("1.12.2");
                    if (mInstanceVersion != null) mInstanceVersion.setText("1.12.2");
                }
            });
        }

        if (mDiscordButton != null) mDiscordButton.setOnClickListener(v -> Tools.openURL(requireActivity(), getString(R.string.social_media_invite)));
        if (mCustomControlButton != null) mCustomControlButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        if (mInstallJarButton != null) mInstallJarButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), LauncherPreferenceFragment.class, LauncherActivity.SETTING_FRAGMENT_TAG, null));
        if (mEditProfileButton != null && mVersionSpinner != null) mEditProfileButton.setOnClickListener(v -> mVersionSpinner.openProfileEditor(requireActivity()));

        if (mPlayButton != null) mPlayButton.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));

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


    }


    private void updateSelectedProfile(DisplayInstance profile, TextView heroName,
                                       TextView profileVersion, TextView selectedVersion) {
        String name = Tools.isValidString(profile.name) ? profile.name : "Minecraft";
        String version = Tools.isValidString(profile.versionId) ? profile.versionId : "Version not selected";
        if (heroName != null) heroName.setText(name);
        if (profileVersion != null) profileVersion.setText(version);
        if (selectedVersion != null) selectedVersion.setText(version);
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
    }

    private void runInstallerWithConfirmation() {
        if (ProgressKeeper.getTaskCount() == 0) {
            mModInstallerLauncher.launch(null);
        } else Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
    }
}
