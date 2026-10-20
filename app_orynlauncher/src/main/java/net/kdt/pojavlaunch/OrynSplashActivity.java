package net.kdt.pojavlaunch;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.VideoView;

import git.artdeell.mojo.R;

/** OrynLauncher Plus intro: bundled video when present, procedural animation as fallback. */
public class OrynSplashActivity extends Activity {
    private VideoView mVideo;
    private boolean mLaunched;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
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
        mVideo.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + videoRes));
        mVideo.setOnCompletionListener(mp -> launchMain());
        mVideo.setOnErrorListener((mp, what, extra) -> { showProceduralIntro(); return true; });
        mVideo.start();
    }

    private void showProceduralIntro() {
        OrynIntroView intro = new OrynIntroView(this);
        intro.setListener(this::launchMain);
        setContentView(intro);
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
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override protected void onPause() {
        if (mVideo != null && mVideo.isPlaying()) mVideo.pause();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (mVideo != null && !mLaunched && !mVideo.isPlaying()) mVideo.start();
    }

    @Override protected void onDestroy() {
        if (mVideo != null) mVideo.stopPlayback();
        super.onDestroy();
    }
}