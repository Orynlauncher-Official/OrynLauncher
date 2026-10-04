package net.kdt.pojavlaunch.utils;

import android.os.Build;
import android.os.Process;
import android.util.Log;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * OrynLauncher performance engine.
 *
 * This mode deliberately does NOT modify Minecraft resolution, render distance,
 * simulation distance, graphics quality, particles, VSync, or any other
 * user-facing video option. It optimizes the JVM/process launch path instead.
 */
public final class OrynFpsBoostUtils {
    private static final String KEY_ENABLED = "orynFpsBoost";

    private OrynFpsBoostUtils() {}

    public static void setEnabled(boolean enabled) {
        if (LauncherPreferences.DEFAULT_PREF == null) return;
        LauncherPreferences.DEFAULT_PREF.edit().putBoolean(KEY_ENABLED, enabled).apply();
        LauncherPreferences.PREF_ORYN_FPS_BOOST = enabled;
    }

    /** Apply only process-level optimizations that are safe before Minecraft starts. */
    public static void prepareForGameLaunch() {
        if (!LauncherPreferences.PREF_ORYN_FPS_BOOST) return;

        try {
            // Give the launcher thread a foreground priority while it hands off to the JVM.
            // The game JVM inherits the normal Android app scheduling class; this does not
            // require root or privileged CPU controls.
            Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);
        } catch (Throwable ignored) {
        }

        Log.i("OrynFpsBoost", "Oryn Performance Engine enabled: video settings untouched");
    }

    /**
     * JVM arguments chosen dynamically for the device. These improve garbage collection
     * behavior and reduce allocation-related frame stalls without changing Minecraft's
     * visual settings.
     */
    public static List<String> getJvmArgs(int javaMajor) {
        ArrayList<String> args = new ArrayList<>();
        if (!LauncherPreferences.PREF_ORYN_FPS_BOOST) return args;

        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());

        // G1 is available on Java 8+ and is a good general-purpose collector for
        // Minecraft's allocation-heavy client. Keep the flags conservative so they
        // work across the bundled Java 8/17/21 runtimes.
        args.add("-XX:+UseG1GC");
        args.add("-XX:MaxGCPauseMillis=40");
        args.add("-XX:+ParallelRefProcEnabled");

        if (javaMajor >= 8) {
            args.add("-XX:+UseStringDeduplication");
        }

        // Don't over-create compiler threads on low-end phones. More threads are
        // allowed on high-end devices where compilation can run in parallel.
        int compilerThreads = Math.max(1, Math.min(4, cores / 2));
        args.add("-XX:CICompilerCount=" + compilerThreads);

        // Keep class unloading enabled to reduce long-session memory pressure.
        args.add("-XX:+ClassUnloadingWithConcurrentMark");

        return args;
    }
}
