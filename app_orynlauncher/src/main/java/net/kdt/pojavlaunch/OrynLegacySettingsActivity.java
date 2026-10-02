package net.kdt.pojavlaunch;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;

public class OrynLegacySettingsActivity extends BaseActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startActivity(new Intent(this, OrynSettingsActivity.class));
        finish();
    }

    @Override
    public boolean setFullscreen() {
        return true;
    }
}
