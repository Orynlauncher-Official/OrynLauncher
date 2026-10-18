package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.util.Log;

import git.artdeell.mojo.BuildConfig;

/**
 * Optional Discord Social SDK integration.
 *
 * The licensed Discord Partner SDK AAR is intentionally not bundled in this public
 * repository. Place the approved SDK at app_orynlauncher/libs/discord_partner_sdk.aar
 * to enable the native bridge; builds without it remain unaffected.
 */
public final class OrynDiscordPresence {
    private static final String TAG = "OrynDiscordPresence";
    private static boolean nativeAvailable;

    private OrynDiscordPresence() {}

    public static void start(Activity activity) {
        if (!BuildConfig.ORYN_DISCORD_SDK_AVAILABLE || activity == null) return;
        try {
            // The SDK uses the engine Activity for Android RPC/authentication plumbing.
            Class<?> init = Class.forName("com.discord.socialsdk.DiscordSocialSdkInit");
            init.getMethod("setEngineActivity", Activity.class).invoke(null, activity);
            System.loadLibrary("oryn_discord_presence");
            nativeAvailable = true;
            nativeStart("In OrynLauncher Plus", "Browsing the launcher");
        } catch (Throwable error) {
            nativeAvailable = false;
            Log.w(TAG, "Discord Rich Presence is unavailable; launcher startup will continue.", error);
        }
    }

    public static void setPlaying(String version) {
        if (!nativeAvailable) return;
        String safeVersion = version == null || version.trim().isEmpty()
                ? "Minecraft"
                : version.trim();
        try {
            nativeUpdate("Playing Minecraft", safeVersion);
        } catch (Throwable error) {
            Log.w(TAG, "Unable to update Discord Rich Presence.", error);
        }
    }

    private static native void nativeStart(String details, String state);
    private static native void nativeUpdate(String details, String state);
}
