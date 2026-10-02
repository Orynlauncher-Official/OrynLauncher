package net.kdt.pojavlaunch;

import android.os.Bundle;
import android.content.Intent;
import androidx.annotation.Nullable;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;

public class OrynLegacySettingsActivity extends BaseActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // The old preference screen is retained only as an internal compatibility
        // host. Always route users to the new V4 settings UI so the legacy
        // "OrynLauncher V3" list cannot appear anymore.
        startActivity(new Intent(this, OrynSettingsActivity.class));
        finish();
    }

    @Override
    public boolean setFullscreen() {
        return true;
    }
}