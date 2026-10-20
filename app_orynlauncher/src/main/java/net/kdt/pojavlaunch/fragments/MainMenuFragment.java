package net.kdt.pojavlaunch.fragments;

import net.kdt.pojavlaunch.OrynDownloadActivity;
import net.kdt.pojavlaunch.OrynCrashViewerActivity;
import net.kdt.pojavlaunch.OrynFileManagerActivity;
import net.kdt.pojavlaunch.OrynCosmeticsActivity;
import net.kdt.pojavlaunch.OrynSettingsActivity;

import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

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

        if (mDiscordButton != null) mDiscordButton.setOnClickListener(v -> Tools.openURL(requireActivity(), getString(R.string.social_media_invite)));
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



    /**
     * Display the selected Minecraft account's actual skin head in the home profile card.
     * Any skin refresh runs in the background so opening the home screen stays responsive.
     */
    private void loadCurrentAccountSkin(View root) {
        ImageView avatar = root.findViewById(R.id.oryn_home_skin_avatar);
        ImageView headerAvatar = requireActivity().findViewById(R.id.oryn_account_avatar_image);
        TextView accountName = root.findViewById(R.id.oryn_home_account_name);
        if (avatar == null) return;

        // Set Steve immediately so an empty/local account never leaves the paper/user icon.
        Bitmap defaultFace = loadOrynDefaultSteveFace();
        if (defaultFace != null) {
            avatar.setImageBitmap(defaultFace);
            if (headerAvatar != null) headerAvatar.setImageBitmap(defaultFace);
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
                    avatar.setImageBitmap(finalFace);
                    if (headerAvatar != null) headerAvatar.setImageBitmap(finalFace);
                }
                if (accountName != null && Tools.isValidString(finalUsername)) {
                    accountName.setText(finalUsername);
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
    }

    private void runInstallerWithConfirmation() {
        if (ProgressKeeper.getTaskCount() == 0) {
            mModInstallerLauncher.launch(null);
        } else Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
    }
}
