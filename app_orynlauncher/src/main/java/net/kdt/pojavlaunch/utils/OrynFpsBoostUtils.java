package net.kdt.pojavlaunch.utils;

import android.content.SharedPreferences;

import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

/**
 * Single source of truth for every Oryn FPS Boost toggle.
 * All Oryn settings switches use the same profile and resolution behavior.
 */
public final class OrynFpsBoostUtils {
    private static final String KEY_ENABLED = "orynFpsBoost";
    private static final String KEY_PREVIOUS_RESOLUTION = "orynFpsBoostPreviousResolution";

    private OrynFpsBoostUtils() {}

    public static void setEnabled(boolean enabled) {
        SharedPreferences prefs = LauncherPreferences.DEFAULT_PREF;
        if (prefs == null) return;

        SharedPreferences.Editor editor = prefs.edit();
        if (enabled) {
            if (!prefs.contains(KEY_PREVIOUS_RESOLUTION)) {
                int current = prefs.getInt("resolutionRatio",
                        Math.max(25, (int) (LauncherPreferences.PREF_SCALE_FACTOR * 100)));
                editor.putInt(KEY_PREVIOUS_RESOLUTION, Math.max(25, Math.min(100, current)));
            }
            editor.putInt("resolutionRatio", 75);
        } else {
            int restored = prefs.getInt(KEY_PREVIOUS_RESOLUTION, 100);
            editor.putInt("resolutionRatio", Math.max(25, Math.min(100, restored)));
            editor.remove(KEY_PREVIOUS_RESOLUTION);
        }

        editor.putBoolean(KEY_ENABLED, enabled).apply();

        LauncherPreferences.PREF_ORYN_FPS_BOOST = enabled;
        LauncherPreferences.PREF_SCALE_FACTOR = enabled
                ? 0.75f
                : Math.max(25, Math.min(100, prefs.getInt("resolutionRatio", 100))) / 100f;

        try {
            Instance instance = Instances.loadSelectedInstance();
            if (instance != null) {
                String gameDir = instance.getGameDirectory().getAbsolutePath();
                if (enabled) {
                    MCOptionUtils.applyOrynFpsBoost(gameDir);
                } else {
                    MCOptionUtils.restoreOrynFpsBoost(gameDir);
                }
            }
        } catch (Throwable ignored) {
            // GameRunner reapplies the same profile at launch.
        }
    }
}
