package net.kdt.pojavlaunch;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;

import git.artdeell.mojo.R;

/** Monochrome OrynLauncher Plus startup splash. Progress is a short visual animation, not actual unpacking progress. */
public class OrynSplashActivity extends Activity {
    private static final long DURATION_MS = 1600;
    private ValueAnimator mAnimator;
    private boolean mLaunched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_oryn_splash);
        hideSystemBars();

        final ProgressBar bar = findViewById(R.id.oryn_splash_progress);
        final TextView percent = findViewById(R.id.oryn_splash_percent);
        mAnimator = ValueAnimator.ofInt(0, 100);
        mAnimator.setDuration(DURATION_MS);
        mAnimator.setInterpolator(new DecelerateInterpolator());
        mAnimator.addUpdateListener(a -> {
            int value = (int) a.getAnimatedValue();
            bar.setProgress(value);
            percent.setText(value + "%");
            if (value >= 100) launchMain();
        });
        mAnimator.start();
    }

    private void launchMain() {
        if (mLaunched) return;
        mLaunched = true;
        startActivity(new Intent(this, LauncherActivity.class));
        finish();
    }

    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    protected void onDestroy() {
        if (mAnimator != null) mAnimator.cancel();
        super.onDestroy();
    }
}