package net.kdt.pojavlaunch.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * OrynLauncher FPS Engine.
 *
 * This is intentionally NOT a graphics-settings preset. It never edits Minecraft's
 * resolution scale, render distance, simulation distance, particles, graphics mode,
 * VSync, FPS limit, or other video options.
 *
 * The engine targets the parts that can actually be controlled by the launcher:
 * JVM garbage collection/compiler behavior and Android process scheduling.
 */
public final class OrynFpsBoostUtils {
    private static final String KEY_ENABLED = "orynFpsBoost";
    private static final String TAG = "OrynFpsBoost";

    private OrynFpsBoostUtils() {}

    public static void setEnabled(boolean enabled) {
        if (LauncherPreferences.DEFAULT_PREF == null) return;
        LauncherPreferences.DEFAULT_PREF.edit().putBoolean(KEY_ENABLED, enabled).apply();
        LauncherPreferences.PREF_ORYN_FPS_BOOST = enabled;
    }

    /**
     * Apply Android process-side performance hints before the embedded Minecraft JVM starts.
     * Android does not expose a public API for forcing CPU/GPU clocks without privileged access,
     * so this deliberately uses only safe public APIs.
     */
    public static void prepareForGameLaunch(Context context) {
        if (!LauncherPreferences.PREF_ORYN_FPS_BOOST) return;

        try {
            // Keep the launcher/game process out of the Android background scheduling class
            // while it is handing control to Minecraft. This is reversible and needs no root.
            Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);
        } catch (Throwable ignored) {
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null && am.isLowRamDevice()) {
                    // Low-RAM devices benefit more from avoiding unnecessary launcher-side
                    // allocations than from aggressive heap expansion.
                    System.gc();
                }
            } catch (Throwable ignored) {
            }
        }

        Log.i(TAG, "FPS Engine enabled: JVM/process optimization only; Minecraft video settings untouched");
    }

    /**
     * Build a device-adaptive JVM profile. The profile is intentionally conservative about
     * flags that differ between the Java 8/17/21 runtimes bundled with the launcher.
     */
    public static List<String> getJvmArgs(int javaMajor) {
        ArrayList<String> args = new ArrayList<>();
        if (!LauncherPreferences.PREF_ORYN_FPS_BOOST) return args;

        final int cores = Math.max(1, Runtime.getRuntime().availableProcessors());

        // G1 reduces long stop-the-world collection spikes on allocation-heavy Minecraft clients.
        args.add("-XX:+UseG1GC");
        args.add("-XX:MaxGCPauseMillis=20");
        args.add("-XX:+ParallelRefProcEnabled");
        args.add("-XX:+DisableExplicitGC");

        // String deduplication reduces duplicate String memory/allocation pressure.
        if (javaMajor >= 8) {
            args.add("-XX:+UseStringDeduplication");
        }

        // Give the JIT enough compiler parallelism without creating a thread storm on
        // low-end phones. High-core devices get the maximum useful value here.
        int compilerThreads;
        if (cores <= 2) compilerThreads = 1;
        else if (cores <= 4) compilerThreads = 2;
        else if (cores <= 6) compilerThreads = 3;
        else compilerThreads = 4;
        args.add("-XX:CICompilerCount=" + compilerThreads);

        // Keep class unloading enabled to reduce memory pressure over long sessions.
        args.add("-XX:+ClassUnloadingWithConcurrentMark");

        // Prefer parallel reference processing on all supported runtimes.
        if (javaMajor >= 9) {
            args.add("-XX:+UseDynamicNumberOfGCThreads");
        }

        return args;
    }
}
