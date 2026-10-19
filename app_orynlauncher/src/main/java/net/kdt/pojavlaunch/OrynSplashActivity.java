package net.kdt.pojavlaunch;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.VideoView;

import git.artdeell.mojo.R;

/** OrynLauncher Plus startup splash: plays res/raw/oryn_intro.mp4, then opens the launcher. */
public class OrynSplashActivity extends Activity {
    private VideoView mVideo;
    private boolean mLaunched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_oryn_splash_video);
        hideSystemBars();

        mVideo = findViewById(R.id.oryn_splash_video);

        // Looked up at runtime so the app still starts if the video file is missing.
        int videoRes = getResources().getIdentifier("oryn_intro", "raw", getPackageName());
        if (videoRes == 0) {
            launchMain();
            return;
        }

        mVideo.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + videoRes));
        mVideo.setOnCompletionListener(mp -> launchMain());
        mVideo.setOnErrorListener((mp, what, extra) -> {
            launchMain();
            return true;
        });
        mVideo.start();
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
    protected void onPause() {
        if (mVideo != null && mVideo.isPlaying()) mVideo.pause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mVideo != null && !mLaunched && !mVideo.isPlaying()) mVideo.start();
    }

    @Override
    protected void onDestroy() {
        if (mVideo != null) mVideo.stopPlayback();
        super.onDestroy();
    }
}