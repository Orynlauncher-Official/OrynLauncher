package net.kdt.pojavlaunch;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.system.Os;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentManager;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.extra.ExtraListener;
import net.kdt.pojavlaunch.fragments.MainMenuFragment;
import net.kdt.pojavlaunch.fragments.MicrosoftLoginFragment;
import net.kdt.pojavlaunch.fragments.SelectAuthFragment;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.InstanceInstaller;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.lifecycle.ContextAwareDoneListener;
import net.kdt.pojavlaunch.lifecycle.ContextExecutor;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.IconCacheJanitor;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.ProgressListener;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;
import net.kdt.pojavlaunch.services.ProgressServiceKeeper;
import net.kdt.pojavlaunch.tasks.MoJsonExtras;
import net.kdt.pojavlaunch.tasks.AsyncVersionList;
import net.kdt.pojavlaunch.tasks.MoJsonDownloader;
import net.kdt.pojavlaunch.utils.NotificationUtils;
import net.kdt.pojavlaunch.utils.OrynUpdateChecker;
import net.kdt.pojavlaunch.utils.OrynThemeManager;

import git.artdeell.mojo.R;

public class LauncherActivity extends BaseActivity {
    public static final String SETTING_FRAGMENT_TAG = "SETTINGS_FRAGMENT";

    private FragmentContainerView mFragmentView;
    private ImageButton mSettingsButton;
    private View mOrynBrand;
    private View mAccountHeader;
    private ProgressLayout mProgressLayout;
    private ProgressServiceKeeper mProgressServiceKeeper;
    private NotificationManager mNotificationManager;
    private View mLoadingScreen;
    private TextView mLoadingStatus;
    private static ActivityResultLauncher<String> mRequestPermissionLauncher;

    /** Progress keys whose status text is mirrored on the loading screen */
    private static final String[] LOADING_STATUS_KEYS = {
            ProgressLayout.DOWNLOAD_GAME,
            ProgressLayout.UNPACK_RUNTIME,
            ProgressLayout.INSTALL_MODPACK,
            ProgressLayout.AUTHENTICATE,
            ProgressLayout.DOWNLOAD_VERSION_LIST,
            ProgressLayout.INSTANCE_INSTALL,
            ProgressLayout.DATA_MIGRATION,
            ProgressLayout.EXTRACT_COMPONENTS,
            ProgressLayout.EXTRACT_SINGLE_FILES
    };

    /** Give the launcher a frame to swap screens before hiding the loading screen */
    private static final long LOADING_HIDE_DELAY_MS = 350L;
    private static final long LOADING_FADE_DURATION_MS = 160L;

    /* Allows to switch from one button "type" to another */
    private final FragmentManager.FragmentLifecycleCallbacks mFragmentCallbackListener = new FragmentManager.FragmentLifecycleCallbacks() {
        @Override
        public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment f) {
            boolean isHome = f instanceof MainMenuFragment;
            boolean isAddAccount = f instanceof SelectAuthFragment;
            mSettingsButton.setVisibility(isAddAccount ? View.VISIBLE : View.GONE);
            if (isAddAccount) mSettingsButton.setImageResource(R.drawable.oryn_nav_home);

            // Smooth Oryn page entrance animation.
            View page = f.getView();
            if(page != null) {
                page.animate().cancel();
                page.setAlpha(0f);
                page.setTranslationY(18f);
                page.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(260L)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            }

            if (mOrynBrand != null) mOrynBrand.setVisibility(isHome ? View.VISIBLE : View.GONE);
            if (mAccountHeader != null) mAccountHeader.setVisibility(isHome ? View.VISIBLE : View.GONE);

            // Re-apply the selected Oryn theme whenever a fragment becomes active.
            // This is required because fragment views are created after LauncherActivity.onCreate().
            OrynThemeManager.apply(LauncherActivity.this);
        }
    };

    /* Listener for the back button in settings */
    private final ExtraListener<String> mBackPreferenceListener = (key, value) -> {
        if(value.equals("true")) onBackPressed();
        return false;
    };

    /* Listener for the auth method selection screen */
    private final ExtraListener<Boolean> mSelectAuthMethod = (key, value) -> {
        // The "false" value is used to stop auth method selection
        FragmentManager manager = getSupportFragmentManager();
        if(!value || manager.isStateSaved()) return false;
        Fragment fragment = manager.findFragmentById(mFragmentView.getId());
        // Allow starting the add account only from the main menu, should it be moved to fragment itself ?
        if(!(fragment instanceof MainMenuFragment)) return false;

        Tools.swapFragment(this, SelectAuthFragment.class, SelectAuthFragment.TAG, null);
        return false;
    };

    /* Listener for the settings fragment */
    private final View.OnClickListener mSettingButtonListener = v -> {
        FragmentManager manager = getSupportFragmentManager();
        if(manager.isStateSaved()) return;
        Fragment fragment = manager.findFragmentById(mFragmentView.getId());
        if(fragment instanceof MainMenuFragment){
            Tools.swapFragment(this, LauncherPreferenceFragment.class, SETTING_FRAGMENT_TAG, null);
        } else{
            // The setting button doubles as a home button now
            Tools.backToMainMenu(this);
        }
    };

    private final ExtraListener<Boolean> mLaunchGameListener = (key, value) -> {
        if(mProgressLayout.hasProcesses()){
            Toast.makeText(this, R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
            return false;
        }

        Instance selectedInstance = Instances.loadSelectedInstance();

        if(selectedInstance == null) {
            Toast.makeText(this, R.string.no_instance, Toast.LENGTH_LONG).show();
            return false;
        }

        if(selectedInstance.installer != null) {
            selectedInstance.installer.start();
            return false;
        }

        if (!Tools.isValidString(selectedInstance.versionId)){
            Toast.makeText(this, R.string.error_no_version, Toast.LENGTH_LONG).show();
            return false;
        }

        if(Accounts.getCurrent() == null){
            Toast.makeText(this, R.string.no_saved_accounts, Toast.LENGTH_LONG).show();
            ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD, true);
            return false;
        }
        String normalizedVersionId = MoJsonExtras.normalizeVersionId(selectedInstance.versionId);
        JVersionList.Version mcVersion = MoJsonExtras.getListedVersion(normalizedVersionId);
        // Show the loading screen right away, the download task takes a moment to start
        showLoadingScreen(getString(R.string.oryn_loading_launching));
        new MoJsonDownloader().start(
                this.getAssets(),
                mcVersion,
                normalizedVersionId,
                new ContextAwareDoneListener(this, normalizedVersionId)
        );
        return false;
    };

    private final TaskCountListener mDoubleLaunchPreventionListener = taskCount -> {
        // Hide the notification that starts the game if there are tasks executing.
        // Prevents the user from trying to launch the game with tasks ongoing.
        if(taskCount > 0) {
            Tools.runOnUiThread(() ->
                    mNotificationManager.cancel(NotificationUtils.NOTIFICATION_ID_GAME_START)
            );
        }
        return false;
    };

    /** Hides the loading screen, unless new tasks showed up in the meantime */
    private final Runnable mHideLoadingScreenRunnable = () -> {
        if(mLoadingScreen == null || ProgressKeeper.getTaskCount() > 0) return;
        mLoadingScreen.animate()
                .alpha(0f)
                .setDuration(LOADING_FADE_DURATION_MS)
                .withEndAction(() -> {
                    if(mLoadingScreen != null && ProgressKeeper.getTaskCount() <= 0) {
                        mLoadingScreen.setVisibility(View.GONE);
                    }
                })
                .start();
    };

    /** Keeps the fullscreen loading screen visible while any task or the game launch is ongoing */
    private final TaskCountListener mLoadingScreenTaskListener = taskCount -> {
        if(mLoadingScreen == null) return false;
        mLoadingScreen.post(() -> {
            if(taskCount > 0) {
                // Make sure a pending hide can not take the loading screen away again
                mLoadingScreen.removeCallbacks(mHideLoadingScreenRunnable);
                mLoadingScreen.animate().cancel();
                if(mLoadingScreen.getVisibility() != View.VISIBLE) {
                    showLoadingScreen(getString(R.string.oryn_loading_please_wait));
                }else {
                    // A fade-out may be in progress, bring the screen back right away
                    mLoadingScreen.setAlpha(1f);
                }
            }else {
                // Delay the hide a little: if a game launch just finished, the GameActivity
                // takes over before the loading screen ever disappears.
                mLoadingScreen.postDelayed(mHideLoadingScreenRunnable, LOADING_HIDE_DELAY_MS);
            }
        });
        return false;
    };

    /** Forwards the newest ongoing task status message to the loading screen */
    private final ProgressListener mLoadingStatusListener = new ProgressListener() {
        @Override
        public void onProgressStarted() {}

        @Override
        public void onProgressUpdated(int progress, int resid, Object... va) {
            String latestStatus = null;
            if(resid != -1) latestStatus = getString(resid, va);
            else if(va.length > 0 && va[0] != null) latestStatus = String.valueOf(va[0]);
            if(latestStatus == null || mLoadingStatus == null) return;
            final String status = latestStatus;
            mLoadingStatus.post(() -> {
                if(mLoadingStatus != null) mLoadingStatus.setText(status);
            });
        }

        @Override
        public void onProgressEnded() {}
    };

    /** Shows the fullscreen loading screen with the given status text */
    private void showLoadingScreen(String status) {
        if(mLoadingScreen == null) return;
        mLoadingScreen.removeCallbacks(mHideLoadingScreenRunnable);
        mLoadingScreen.animate().cancel();
        mLoadingScreen.setAlpha(1f);
        mLoadingScreen.setVisibility(View.VISIBLE);
        if(mLoadingStatus != null) mLoadingStatus.setText(status);
    }

    @Override
    protected boolean shouldIgnoreNotch() {
        return getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT;
    }

    @Override
    public boolean setFullscreen() {
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_pojav_launcher);
        MoJsonDownloader.prepareSubstitutionMap(getAssets());

        try {
            Os.setenv("TMPDIR", Tools.DIR_CACHE.getAbsolutePath(), true);
         }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        IconCacheJanitor.runJanitor();

        getWindow().setBackgroundDrawable(null);
        bindViews();
        OrynThemeManager.apply(this);

        // Check the official OrynLauncher GitHub release in the background.
        // The check never blocks launcher startup or game launching.
        OrynUpdateChecker.check(this);

        mRequestPermissionLauncher = this.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isAllowed -> {
                    if(!isAllowed) Tools.runOnUiThread(() -> Toast.makeText(this, R.string.notification_permission_toast, Toast.LENGTH_LONG).show());
                }
        );
        checkNotificationPermission();
        if(LauncherPreferences.PREF_MIGRATION_NOTICE)
            PojavApplication.sExecutorService.submit(this::checkPreviousInstalls);

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        ProgressKeeper.addTaskCountListener(mDoubleLaunchPreventionListener);
        ProgressKeeper.addTaskCountListener((mProgressServiceKeeper = new ProgressServiceKeeper(this)));

        mSettingsButton.setOnClickListener(mSettingButtonListener);
        ProgressKeeper.addTaskCountListener(mProgressLayout);
        // Register the loading screen before any progress listener so its show() runs first
        ProgressKeeper.addTaskCountListener(mLoadingScreenTaskListener);
        for(String progressKey : LOADING_STATUS_KEYS) {
            ProgressKeeper.addListener(progressKey, mLoadingStatusListener);
        }
        ExtraCore.addExtraListener(ExtraConstants.BACK_PREFERENCE, mBackPreferenceListener);
        ExtraCore.addExtraListener(ExtraConstants.SELECT_AUTH_METHOD, mSelectAuthMethod);

        ExtraCore.addExtraListener(ExtraConstants.LAUNCH_GAME, mLaunchGameListener);

        new AsyncVersionList().getVersionList(versions -> ExtraCore.setValue(ExtraConstants.RELEASE_TABLE, versions));

        mProgressLayout.observe(ProgressLayout.DOWNLOAD_GAME);
        mProgressLayout.observe(ProgressLayout.UNPACK_RUNTIME);
        mProgressLayout.observe(ProgressLayout.INSTALL_MODPACK);
        mProgressLayout.observe(ProgressLayout.AUTHENTICATE);
        mProgressLayout.observe(ProgressLayout.DOWNLOAD_VERSION_LIST);
        mProgressLayout.observe(ProgressLayout.INSTANCE_INSTALL);
        mProgressLayout.observe(ProgressLayout.DATA_MIGRATION);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ContextExecutor.setActivity(this);
        InstanceInstaller.postInstallCheck(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ContextExecutor.clearActivity();
    }

    @Override
    protected void onStart() {
        super.onStart();
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(mFragmentCallbackListener, true);
        // Also handle the fragment that was already restored before the callback was registered.
        OrynThemeManager.apply(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mProgressLayout.cleanUpObservers();
        ProgressKeeper.removeTaskCountListener(mProgressLayout);
        ProgressKeeper.removeTaskCountListener(mProgressServiceKeeper);
        ProgressKeeper.removeTaskCountListener(mLoadingScreenTaskListener);
        for(String progressKey : LOADING_STATUS_KEYS) {
            ProgressKeeper.removeListener(progressKey, mLoadingStatusListener);
        }
        if(mLoadingScreen != null) {
            mLoadingScreen.removeCallbacks(mHideLoadingScreenRunnable);
            mLoadingScreen.animate().cancel();
        }
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.BACK_PREFERENCE, mBackPreferenceListener);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.SELECT_AUTH_METHOD, mSelectAuthMethod);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.LAUNCH_GAME, mLaunchGameListener);

        getSupportFragmentManager().unregisterFragmentLifecycleCallbacks(mFragmentCallbackListener);
    }

    /** Custom implementation to feel more natural when a backstack isn't present */
    @Override
    public void onBackPressed() {
        MicrosoftLoginFragment fragment = (MicrosoftLoginFragment) getVisibleFragment(MicrosoftLoginFragment.TAG);
        if(fragment != null){
            if(fragment.canGoBack()){
                fragment.goBack();
                return;
            }
        }

        super.onBackPressed();
    }

    @SuppressWarnings("SameParameterValue")
    private Fragment getVisibleFragment(String tag){
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(tag);
        if(fragment != null && fragment.isVisible()) {
            return fragment;
        }
        return null;
    }

    @SuppressWarnings("unused")
    private Fragment getVisibleFragment(int id){
        Fragment fragment = getSupportFragmentManager().findFragmentById(id);
        if(fragment != null && fragment.isVisible()) {
            return fragment;
        }
        return null;
    }

    public void askForPermission(int minApi, final String permission) {
        if(Build.VERSION.SDK_INT < minApi) return;
        mRequestPermissionLauncher.launch(permission);
    }
    public boolean checkForPermission(int minApi, final String permission) {
        return Build.VERSION.SDK_INT < minApi ||
                ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_DENIED;
    }
    public boolean checkForPermissionRationale(int minApi, final String permission) {
        return checkForPermission(minApi, permission) || ActivityCompat.shouldShowRequestPermissionRationale(this, permission);
    }

    private void checkNotificationPermission() {
        if(LauncherPreferences.PREF_SKIP_NOTIFICATION_PERMISSION_CHECK ||
            this.checkForPermission(33, Manifest.permission.POST_NOTIFICATIONS)) {
            return;
        }
        showNotificationPermissionReasoning();
    }

    // Call async
    private void checkPreviousInstalls(){
        final String[] packages = {"git.artdeell.mojo", "git.artdeell.mojo.debug", "git.artdeell.mojo.pub"};
        for(String s : packages){
            Intent i = getPackageManager().getLaunchIntentForPackage(s);
            if(i == null) continue;
            Tools.runOnUiThread(() ->
                    new AlertDialog.Builder(this)
                        .setTitle(R.string.migration_progress_warning_title)
                        .setMessage(R.string.migration_notice)
                        .setPositiveButton(android.R.string.ok, (d, button) -> LauncherPreferences.DEFAULT_PREF.edit().putBoolean("migrationNotice", false).apply())
                        .setOnDismissListener(d -> LauncherPreferences.PREF_MIGRATION_NOTICE = false)
                        .show());
            break;
        }
    }

    private void showNotificationPermissionReasoning() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.notification_permission_dialog_title)
                .setMessage(R.string.notification_permission_dialog_text)
                .setPositiveButton(android.R.string.ok, (d, w) ->
                        askForPermission(33, Manifest.permission.POST_NOTIFICATIONS))
                .setNegativeButton(android.R.string.cancel, (d, w)-> handleNoNotificationPermission())
                .show();
    }

    private void handleNoNotificationPermission() {
        LauncherPreferences.PREF_SKIP_NOTIFICATION_PERMISSION_CHECK = true;
        LauncherPreferences.DEFAULT_PREF.edit()
                .putBoolean(LauncherPreferences.PREF_KEY_SKIP_NOTIFICATION_CHECK, true)
                .apply();
    }

    /** Stuff all the view boilerplate here */
    private void bindViews(){
        mFragmentView = findViewById(R.id.container_fragment);
        mSettingsButton = findViewById(R.id.setting_button);
        mOrynBrand = findViewById(R.id.oryn_brand);
        mAccountHeader = findViewById(R.id.oryn_account_header);
        mProgressLayout = findViewById(R.id.progress_layout);
        mLoadingScreen = findViewById(R.id.oryn_loading_screen);
        mLoadingStatus = findViewById(R.id.oryn_loading_status);
    }
}
