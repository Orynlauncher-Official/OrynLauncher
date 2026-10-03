package net.kdt.pojavlaunch;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceRecorderFragment;

public class OrynRecorderActivity extends BaseActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);

        FrameLayout container = new FrameLayout(this);
        container.setId(View.generateViewId());
        container.setBackgroundColor(Color.rgb(5, 12, 20));
        setContentView(container);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(container.getId(), new LauncherPreferenceRecorderFragment())
                    .commit();
        }
    }

    @Override
    public boolean setFullscreen() {
        return true;
    }
}
