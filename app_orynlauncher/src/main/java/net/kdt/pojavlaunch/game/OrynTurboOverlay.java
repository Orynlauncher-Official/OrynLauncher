package net.kdt.pojavlaunch.game;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Debug;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

/**
 * In-activity Oryn Turbo edge panel. It deliberately does not use SYSTEM_ALERT_WINDOW:
 * the panel exists only over GameActivity, so no special overlay permission or background
 * service is needed and it is removed with the game activity.
 */
public final class OrynTurboOverlay {
    public interface Actions {
        void onModeChanged(int mode);
        void onScreenshot();
        void onRecording();
        void onPerformanceSettings();
    }

    public static final int BALANCED = 0, PERFORMANCE = 1, BATTERY_SAVER = 2;
    private final Activity activity;
    private final Actions actions;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout decor, container;
    private LinearLayout panel;
    private TextView monitor, modeStatus;
    private long sessionStart = System.currentTimeMillis();
    private int mode = BALANCED;
    private boolean opened, stopped;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (stopped) return;
            updateMonitor();
            handler.postDelayed(this, 1000L);
        }
    };

    public OrynTurboOverlay(Activity activity, Actions actions) {
        this.activity = activity;
        this.actions = actions;
    }

    public void show() {
        if (stopped || container != null || activity.isFinishing()) return;
        decor = activity.getWindow().getDecorView() instanceof FrameLayout
                ? (FrameLayout) activity.getWindow().getDecorView() : null;
        if (decor == null) return;
        container = new FrameLayout(activity);
        container.setClipChildren(false);
        container.setClipToPadding(false);

        TextView handle = new TextView(activity);
        handle.setText("O\nR");
        handle.setGravity(Gravity.CENTER);
        handle.setTextColor(Color.WHITE);
        handle.setTextSize(12);
        handle.setTypeface(Typeface.DEFAULT_BOLD);
        handle.setBackground(shape(0xEE171827, 0xFF7657F6, 2, 22));
        handle.setElevation(dp(10));
        handle.setContentDescription("Swipe inward to open Oryn Turbo");
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(dp(30), dp(72), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        hp.rightMargin = 0;
        container.addView(handle, hp);
        handle.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    downX = e.getRawX(); downY = e.getRawY(); return true;
                }
                if (e.getAction() == MotionEvent.ACTION_UP) {
                    float dx = e.getRawX() - downX;
                    if (dx < -dp(24)) openPanel();
                    else if (opened) closePanel();
                    return true;
                }
                return true;
            }
        });

        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(14), dp(14), dp(14));
        panel.setBackground(shape(0xF511121B, 0xFF343044, 1, 20));
        panel.setElevation(dp(16));
        panel.setVisibility(View.GONE);
        panel.setClipToPadding(false);

        TextView title = label("ORYN TURBO", 17, 0xFFF7F6FF, true);
        panel.addView(title, matchWrap());
        TextView subtitle = label("GAME SESSION", 9, 0xFFA9A6C0, true);
        LinearLayout.LayoutParams sp = matchWrap(); sp.topMargin = dp(2);
        panel.addView(subtitle, sp);

        modeStatus = label("BALANCED MODE · ANDROID MANAGED", 10, 0xFFB8B2D8, true);
        LinearLayout.LayoutParams mp = matchWrap(); mp.topMargin = dp(12);
        panel.addView(modeStatus, mp);
        addAction("Balanced", () -> setMode(BALANCED), 0xFF282638);
        addAction("Performance", () -> setMode(PERFORMANCE), 0xFF27213E);
        addAction("Battery saver", () -> setMode(BATTERY_SAVER), 0xFF202A32);

        monitor = label("RAM  —\nCPU  —\nFPS  unavailable\nSESSION  00:00", 12, 0xFFE9E8F2, false);
        monitor.setLineSpacing(dp(5), 1f);
        monitor.setPadding(dp(11), dp(10), dp(11), dp(10));
        monitor.setBackground(shape(0xFF1A1A26, 0xFF333044, 1, 12));
        LinearLayout.LayoutParams rp = matchWrap(); rp.topMargin = dp(10);
        panel.addView(monitor, rp);

        addAction("Screenshot", () -> actions.onScreenshot(), 0xFF282638);
        addAction("Screen recording  ↗", () -> actions.onRecording(), 0xFF282638);
        addAction("Performance settings  ↗", () -> actions.onPerformanceSettings(), 0xFF282638);
        TextView close = label("CLOSE  ×", 11, 0xFFB7B2D0, true);
        close.setGravity(Gravity.CENTER);
        close.setPadding(0, dp(10), 0, dp(2));
        close.setOnClickListener(v -> closePanel());
        panel.addView(close, matchWrap());

        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(250), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        pp.rightMargin = dp(8);
        container.addView(panel, pp);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        decor.addView(container, cp);
        handler.post(refresh);
    }

    private void addAction(String text, Runnable action, int color) {
        TextView row = label(text, 12, 0xFFF5F3FF, true);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(11), 0, dp(8), 0);
        row.setBackground(shape(color, 0xFF38334D, 1, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36));
        lp.topMargin = dp(6);
        panel.addView(row, lp);
        row.setOnClickListener(v -> action.run());
    }

    private void setMode(int value) {
        mode = value;
        if (actions != null) actions.onModeChanged(value);
        if (modeStatus != null) modeStatus.setText(
                (value == PERFORMANCE ? "PERFORMANCE" : value == BATTERY_SAVER ? "BATTERY SAVER" : "BALANCED")
                + " · ANDROID MANAGED");
    }

    private void updateMonitor() {
        if (monitor == null || stopped) return;
        long elapsed = Math.max(0, (System.currentTimeMillis() - sessionStart) / 1000);
        long mins = elapsed / 60, secs = elapsed % 60;
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long max = runtime.maxMemory();
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        ActivityManager am = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) am.getMemoryInfo(info);
        monitor.setText(String.format(Locale.US,
                "APP RAM  %.0f / %.0f MB\nDEVICE AVAILABLE  %.0f MB\nCPU  unavailable reliably\nFPS  renderer counter unavailable\nSESSION  %02d:%02d",
                used / 1048576f, max / 1048576f, info.availMem / 1048576f, mins, secs));
    }

    private void openPanel() {
        if (panel == null) return;
        opened = true;
        panel.setVisibility(View.VISIBLE);
        panel.setAlpha(0f);
        panel.setTranslationX(dp(18));
        panel.animate().alpha(1f).translationX(0).setDuration(180).start();
    }

    private void closePanel() {
        if (panel == null) return;
        opened = false;
        panel.animate().alpha(0f).translationX(dp(18)).setDuration(140)
                .withEndAction(() -> { if (panel != null && !opened) panel.setVisibility(View.GONE); }).start();
    }

    public void destroy() {
        stopped = true;
        handler.removeCallbacksAndMessages(null);
        if (container != null && container.getParent() instanceof ViewGroup)
            ((ViewGroup) container.getParent()).removeView(container);
        container = null; panel = null; monitor = null; decor = null;
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView v = new TextView(activity);
        v.setText(text); v.setTextSize(size); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }
    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
    private int dp(float value) { return (int)(value * activity.getResources().getDisplayMetrics().density + 0.5f); }
    private GradientDrawable shape(int fill, int stroke, int width, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill); d.setCornerRadius(dp(radius)); d.setStroke(dp(width), stroke);
        return d;
    }
}
