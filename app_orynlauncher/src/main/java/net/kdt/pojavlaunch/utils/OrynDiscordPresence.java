package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.util.Log;
import git.artdeell.mojo.BuildConfig;

/** Discord Social SDK Rich Presence for OrynLauncher Plus. */
public final class OrynDiscordPresence {
    private static final String TAG = "OrynDiscordPresence";
    private static volatile boolean nativeAvailable;
    private static volatile boolean playing;
    private OrynDiscordPresence() {}

    public static void start(Activity activity) {
        if (!BuildConfig.ORYN_DISCORD_SDK_AVAILABLE || activity == null) return;
        try {
            Class<?> init = Class.forName("com.discord.socialsdk.DiscordSocialSdkInit");
            init.getMethod("setEngineActivity", Activity.class).invoke(null, activity);
            System.loadLibrary("oryn_discord_presence");
            nativeAvailable = true;
            nativeStart();
            setBrowsing();
        } catch (Throwable error) {
            nativeAvailable = false;
            Log.w(TAG, "Discord Rich Presence unavailable; launcher startup will continue.", error);
        }
    }

    /** Shows browsing when the launcher is opened or when the user returns after Minecraft closes. */
    public static void setBrowsing() {
        if (!nativeAvailable) return;
        try {
            nativeUpdate("Browsing OrynLauncher Plus", "");
            playing = false;
        } catch (Throwable error) {
            Log.w(TAG, "Could not update browsing presence.", error);
        }
    }

    /** Called when the user taps Play; the selected Minecraft version is shown in Discord. */
    public static void setPlaying(String version) {
        if (!nativeAvailable) return;
        String safeVersion = version == null || version.trim().isEmpty()
                ? "Minecraft" : "Version " + version.trim();
        try {
            playing = true;
            nativeUpdate("Playing OrynLauncher Plus", safeVersion);
        }
        catch (Throwable error) { Log.w(TAG, "Could not update playing presence.", error); }
    }

    public static void stop() {
        if (!nativeAvailable) return;
        try { nativeStop(); }
        catch (Throwable error) { Log.w(TAG, "Could not stop Discord Rich Presence.", error); }
        finally { nativeAvailable = false; playing = false; }
    }

    private static native void nativeStart();
    private static native void nativeUpdate(String details, String state);
    private static native void nativeStop();
}
