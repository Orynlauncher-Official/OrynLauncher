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
        Button mDiscordButton = view.findViewById(R.id.social_media_button);
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
        TextView mStatusVersion = view.findViewById(R.id.oryn_status_version);
        if (mVersionSpinner != null) {
            mVersionSpinner.setOnSelectionChangedListener(instance -> {
                String version = (instance != null && Tools.isValidString(instance.versionId))
                        ? instance.versionId : "1.12.2";
                if (mSelectedVersion != null) mSelectedVersion.setText(version);
                if (mStatusVersion != null) mStatusVersion.setText(version);
            });
        }

        mDiscordButton.setOnClickListener(v -> Tools.openURL(requireActivity(), getString(R.string.social_media_invite)));
        mCustomControlButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        // V4 Settings sidebar must open the new Settings dashboard, not the legacy PreferenceFragment.
        mInstallJarButton.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), net.kdt.pojavlaunch.OrynSettingsActivity.class)));
        mEditProfileButton.setOnClickListener(v -> mVersionSpinner.openProfileEditor(requireActivity()));

        mPlayButton.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));

        mShareLogsButton.setOnClickListener((v) -> shareLog(requireContext()));

        mOpenDirectoryButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), OrynFileManagerActivity.class)));
        mDownloadButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), OrynDownloadActivity.class)));
        mCrashButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), OrynCrashViewerActivity.class)));
        mCosmeticsButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), OrynCosmeticsActivity.class)));
        mFilesNavButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), OrynFileManagerActivity.class)));
        mCrashNavButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), OrynCrashViewerActivity.class)));


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
