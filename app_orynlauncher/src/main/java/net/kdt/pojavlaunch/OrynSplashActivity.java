package net.kdt.pojavlaunch;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.VideoView;

import git.artdeell.mojo.R;

/**
 * OrynLauncher Plus startup intro. Uses the bundled H.264 video when available
 * and the procedural Canvas animation if the asset is absent or playback fails.
 */
public class OrynSplashActivity extends Activity {
    private VideoView mVideo;
    private boolean mLaunched;
    private boolean mUsingFallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawableResource(android.R.color.black);
        hideSystemBars();

        int videoRes = getResources().getIdentifier("oryn_intro", "raw", getPackageName());
        if (videoRes == 0) {
            showProceduralIntro();
            return;
        }

        setContentView(R.layout.activity_oryn_splash_video);
        mVideo = findViewById(R.id.oryn_splash_video);
        if (mVideo == null) {
            showProceduralIntro();
            return;
        }

        mVideo.setOnCompletionListener(mp -> launchMain());
        mVideo.setOnErrorListener((mp, what, extra) -> {
            showProceduralIntro();
            return true;
        });
        mVideo.setOnClickListener(v -> launchMain());
        mVideo.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + videoRes));
        mVideo.start();
    }

    private void showProceduralIntro() {
        if (mLaunched || mUsingFallback) return;
        mUsingFallback = true;
        if (mVideo != null) {
            mVideo.setOnCompletionListener(null);
            mVideo.setOnErrorListener(null);
            mVideo.setOnClickListener(null);
            mVideo.stopPlayback();
            mVideo = null;
        }
        OrynIntroView intro = new OrynIntroView(this);
        intro.setListener(this::launchMain);
        setContentView(intro);
        getWindow().setBackgroundDrawableResource(android.R.color.black);
        hideSystemBars();
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
        if (mVideo != null && !mLaunched && !mUsingFallback && !mVideo.isPlaying()) {
            mVideo.start();
        }
    }

    @Override
    protected void onDestroy() {
        if (mVideo != null) mVideo.stopPlayback();
        super.onDestroy();
    }
}
