package net.kdt.pojavlaunch;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceExperimentalFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceJavaFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceRecorderFragment;

/**
 * Hosts the existing preference fragments from Oryn Settings.
 * Uses a strict screen allow-list so unknown extra values cannot instantiate arbitrary classes.
 */
public class OrynPreferenceHostActivity extends BaseActivity {
    public static final String EXTRA_SCREEN = "oryn_preference_screen";
    private static final int CONTAINER_ID = 0x4F52594E;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout container = new FrameLayout(this);
        container.setId(CONTAINER_ID);
        container.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(container);

        if (savedInstanceState == null) {
            String screen = getIntent().getStringExtra(EXTRA_SCREEN);
            Fragment fragment;
            String title;
            if ("java".equals(screen)) {
                fragment = new LauncherPreferenceJavaFragment();
                title = "JVM / Java Runtime";
            } else if ("recorder".equals(screen)) {
                fragment = new LauncherPreferenceRecorderFragment();
                title = "Oryn Recorder";
            } else if ("experimental".equals(screen)) {
                fragment = new LauncherPreferenceExperimentalFragment();
                title = "Experimental Settings";
            } else {
                finish();
                return;
            }
            setTitle(title);
            getSupportFragmentManager().beginTransaction()
                    .replace(CONTAINER_ID, fragment)
                    .commit();
        }
    }

    @Override
    public boolean setFullscreen() {
        return true;
    }
}
