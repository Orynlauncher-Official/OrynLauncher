package net.kdt.pojavlaunch;

import android.os.Bundle;
import androidx.annotation.Nullable;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;

public class OrynLegacySettingsActivity extends BaseActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.setId(android.view.View.generateViewId());
        frame.setBackgroundColor(android.graphics.Color.rgb(5, 12, 20));
        setContentView(frame, new android.view.ViewGroup.LayoutParams(-1, -1));
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(frame.getId(), new LauncherPreferenceFragment())
                    .commit();
        }
    }

    @Override
    public boolean setFullscreen() {
        return true;
    }
}