package net.kdt.pojavlaunch.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.JREUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * OrynLauncher FPS Engine.
 *
 * This engine improves CPU/JVM-side Minecraft performance without changing the
 * player's Minecraft video settings. It does NOT modify resolution scale,
 * render distance, simulation distance, particles, graphics mode, VSync or
 * the Minecraft FPS limit.
 */
public final class OrynFpsBoostUtils {
    private static final String KEY_ENABLED = "orynFpsBoost";
    private static final String TAG = "OrynFpsBoost";
    private static final String ARG_PREFIX = "-XX:";

    private OrynFpsBoostUtils() {}

    public static void setEnabled(boolean enabled) {
        if (LauncherPreferences.DEFAULT_PREF == null) return;
        LauncherPreferences.DEFAULT_PREF.edit().putBoolean(KEY_ENABLED, enabled).apply();
        LauncherPreferences.PREF_ORYN_FPS_BOOST = enabled;
    }

    /**
     * Prepare the process and inject the actual JVM performance arguments used by
     * Minecraft's Java runtime. This is intentionally limited to JVM/runtime
     * optimization and never rewrites Minecraft video options.
     */
    public static void prepareForGameLaunch(Context context) {
        if (!LauncherPreferences.PREF_ORYN_FPS_BOOST) {
            removeInjectedJvmArgs();
            return;
        }

        try {
            // Give the thread handing off to the game a foreground/display priority.
            Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);
        } catch (Throwable ignored) {
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                ActivityManager am =
                        (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null && am.isLowRamDevice()) {
                    // Clean launcher-side garbage before the memory-heavy game starts.
                    System.gc();
                }
            } catch (Throwable ignored) {
            }
        }

        // These arguments are deliberately Java 8-compatible so they also work when
        // the selected Minecraft runtime is Java 8, 17 or 21.
        applyInjectedJvmArgs();

        Log.i(TAG, "FPS Engine enabled: JVM optimizations injected; Minecraft video settings untouched");
    }

    /**
     * Inject the optimization flags into the same custom-JVM-argument preference
     * consumed by the launcher Java runtime. Existing user arguments are preserved.
     */
    private static void applyInjectedJvmArgs() {
        if (LauncherPreferences.DEFAULT_PREF == null) return;

        String current = LauncherPreferences.DEFAULT_PREF.getString("javaArgs", "");
        List<String> userArgs = new ArrayList<>();

        try {
            for (String arg : JREUtils.parseJavaArguments(current)) {
                if (!isOrynJvmArg(arg)) {
                    userArgs.add(arg);
                }
            }
        } catch (Throwable ignored) {
            // If an older argument string cannot be parsed, do not destroy it.
            return;
        }

        userArgs.addAll(getJvmArgs(8));

        StringBuilder merged = new StringBuilder();
        for (String arg : userArgs) {
            if (arg == null || arg.trim().isEmpty()) continue;
            if (merged.length() > 0) merged.append(' ');
            merged.append(arg);
        }

        String value = merged.toString();
        LauncherPreferences.DEFAULT_PREF.edit().putString("javaArgs", value).apply();
        LauncherPreferences.PREF_CUSTOM_JAVA_ARGS = value;
    }

    /**
     * Remove only arguments owned by Oryn FPS Boost, leaving the user's custom
     * Java arguments untouched when the feature is disabled.
     */
    private static void removeInjectedJvmArgs() {
        if (LauncherPreferences.DEFAULT_PREF == null) return;

        String current = LauncherPreferences.DEFAULT_PREF.getString("javaArgs", "");
        try {
            List<String> remaining = new ArrayList<>();
            for (String arg : JREUtils.parseJavaArguments(current)) {
                if (!isOrynJvmArg(arg)) {
                    remaining.add(arg);
                }
            }

            StringBuilder merged = new StringBuilder();
            for (String arg : remaining) {
                if (arg == null || arg.trim().isEmpty()) continue;
                if (merged.length() > 0) merged.append(' ');
                merged.append(arg);
            }

            String value = merged.toString();
            LauncherPreferences.DEFAULT_PREF.edit().putString("javaArgs", value).apply();
            LauncherPreferences.PREF_CUSTOM_JAVA_ARGS = value;
        } catch (Throwable ignored) {
        }
    }

    private static boolean isOrynJvmArg(String arg) {
        if (arg == null || !arg.startsWith(ARG_PREFIX)) return false;

        return arg.equals("-XX:+UseG1GC")
                || arg.equals("-XX:MaxGCPauseMillis=20")
                || arg.equals("-XX:+ParallelRefProcEnabled")
                || arg.equals("-XX:+DisableExplicitGC")
                || arg.equals("-XX:+UseStringDeduplication")
                || arg.startsWith("-XX:CICompilerCount=")
                || arg.equals("-XX:+ClassUnloadingWithConcurrentMark")
                || arg.equals("-XX:+UseDynamicNumberOfGCThreads");
    }

    /**
     * Build a device-adaptive JVM performance profile.
     *
     * These options target garbage collection and JIT compilation overhead.
     * They do not lower Minecraft graphics quality.
     */
    public static List<String> getJvmArgs(int javaMajor) {
        ArrayList<String> args = new ArrayList<>();
        if (!LauncherPreferences.PREF_ORYN_FPS_BOOST) return args;

        final int cores = Math.max(1, Runtime.getRuntime().availableProcessors());

        // G1 is well suited to Minecraft's allocation-heavy workload and helps
        // reduce long GC pauses that appear as FPS/stutter drops.
        args.add("-XX:+UseG1GC");
        args.add("-XX:MaxGCPauseMillis=20");
        args.add("-XX:+ParallelRefProcEnabled");
        args.add("-XX:+DisableExplicitGC");

        // Reduce duplicate String memory/allocation pressure.
        if (javaMajor >= 8) {
            args.add("-XX:+UseStringDeduplication");
        }

        // Scale JIT compiler threads with CPU count instead of creating a thread storm.
        int compilerThreads;
        if (cores <= 2) compilerThreads = 1;
        else if (cores <= 4) compilerThreads = 2;
        else if (cores <= 6) compilerThreads = 3;
        else compilerThreads = 4;
        args.add("-XX:CICompilerCount=" + compilerThreads);

        // Keep class unloading active to control memory growth during long sessions.
        args.add("-XX:+ClassUnloadingWithConcurrentMark");

        if (javaMajor >= 9) {
            args.add("-XX:+UseDynamicNumberOfGCThreads");
        }

        return args;
    }
}
