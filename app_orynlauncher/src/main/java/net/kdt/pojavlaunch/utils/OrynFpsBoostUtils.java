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
 * FPS Boost uses an ultra-low Minecraft video profile plus JVM/runtime tuning.
 * It intentionally does NOT change resolution scale.
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

    public static void prepareForGameLaunch(Context context) {
        if (!LauncherPreferences.PREF_ORYN_FPS_BOOST) {
            removeInjectedJvmArgs();
            return;
        }

        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);
        } catch (Throwable ignored) {
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                ActivityManager am =
                        (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null && am.isLowRamDevice()) {
                    System.gc();
                }
            } catch (Throwable ignored) {
            }
        }

        // Apply the in-game low graphics profile before Minecraft starts.
        applyUltraLowGraphicsProfile();

        // Keep the JVM-side optimizations as well.
        applyInjectedJvmArgs();

        Log.i(TAG, "FPS Boost enabled: ultra-low Minecraft profile + JVM optimizations");
    }

    /**
     * Forces the Minecraft options that have the biggest graphics/GPU/CPU cost
     * to their lowest practical values. Resolution/render scale is deliberately
     * left alone so the game is not made blurry.
     */
    private static void applyUltraLowGraphicsProfile() {
        try {
            MCOptionUtils.load();

            // Core graphics quality.
            MCOptionUtils.set("graphics", "0");              // Fast
            MCOptionUtils.set("renderDistance", "2");       // Minimum practical
            MCOptionUtils.set("simulationDistance", "5");   // Minimum supported by modern MC
            MCOptionUtils.set("particles", "2");            // Minimal
            MCOptionUtils.set("clouds", "false");
            MCOptionUtils.set("entityShadows", "false");
            MCOptionUtils.set("entityDistanceScaling", "0.5");
            MCOptionUtils.set("biomeBlendRadius", "0");
            MCOptionUtils.set("mipmapLevels", "0");
            MCOptionUtils.set("ao", "0");
            MCOptionUtils.set("enableVsync", "false");

            // Let Minecraft render as fast as possible instead of imposing a
            // launcher-side FPS cap.
            MCOptionUtils.set("maxFps", "260");

            // Expensive visual effects.
            MCOptionUtils.set("bobView", "false");
            MCOptionUtils.set("darknessEffectScale", "0.0");
            MCOptionUtils.set("glintSpeed", "0.0");
            MCOptionUtils.set("glintStrength", "0.0");
            MCOptionUtils.set("screenEffectScale", "0.0");
            MCOptionUtils.set("damageTiltStrength", "0.0");
            MCOptionUtils.set("highContrast", "false");

            MCOptionUtils.save();
        } catch (Throwable t) {
            Log.w(TAG, "Could not apply ultra-low Minecraft graphics profile", t);
        }
    }

    private static void applyInjectedJvmArgs() {
        if (LauncherPreferences.DEFAULT_PREF == null) return;

        String current = LauncherPreferences.DEFAULT_PREF.getString("javaArgs", "");
        List<String> userArgs = new ArrayList<>();

        try {
            for (String arg : net.kdt.pojavlaunch.utils.JREUtils.parseJavaArguments(current)) {
                if (!isOrynJvmArg(arg)) {
                    userArgs.add(arg);
                }
            }
        } catch (Throwable ignored) {
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

    private static void removeInjectedJvmArgs() {
        if (LauncherPreferences.DEFAULT_PREF == null) return;

        String current = LauncherPreferences.DEFAULT_PREF.getString("javaArgs", "");
        try {
            List<String> remaining = new ArrayList<>();
            for (String arg : net.kdt.pojavlaunch.utils.JREUtils.parseJavaArguments(current)) {
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

    public static List<String> getJvmArgs(int javaMajor) {
        ArrayList<String> args = new ArrayList<>();
        if (!LauncherPreferences.PREF_ORYN_FPS_BOOST) return args;

        final int cores = Math.max(1, Runtime.getRuntime().availableProcessors());

        args.add("-XX:+UseG1GC");
        args.add("-XX:MaxGCPauseMillis=20");
        args.add("-XX:+ParallelRefProcEnabled");
        args.add("-XX:+DisableExplicitGC");

        if (javaMajor >= 8) {
            args.add("-XX:+UseStringDeduplication");
        }

        int compilerThreads;
        if (cores <= 2) compilerThreads = 1;
        else if (cores <= 4) compilerThreads = 2;
        else if (cores <= 6) compilerThreads = 3;
        else compilerThreads = 4;
        args.add("-XX:CICompilerCount=" + compilerThreads);

        args.add("-XX:+ClassUnloadingWithConcurrentMark");

        if (javaMajor >= 9) {
            args.add("-XX:+UseDynamicNumberOfGCThreads");
        }

        return args;
    }
}
