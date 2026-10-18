package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.util.Log;
import git.artdeell.mojo.BuildConfig;

/** Discord Social SDK Rich Presence for OrynLauncher Plus. */
public final class OrynDiscordPresence {
    private static final String TAG = "OrynDiscordPresence";
    private static volatile boolean nativeAvailable;
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

    public static void setBrowsing() {
        if (!nativeAvailable) return;
        try { nativeUpdate("Browsing OrynLauncher Plus", "Launcher"); }
        catch (Throwable error) { Log.w(TAG, "Could not update browsing presence.", error); }
    }

    public static void setPlaying(String version) {
        if (!nativeAvailable) return;
        String safeVersion = version == null || version.trim().isEmpty()
                ? "Minecraft" : "Version " + version.trim();
        try { nativeUpdate("Playing Minecraft", safeVersion); }
        catch (Throwable error) { Log.w(TAG, "Could not update Minecraft presence.", error); }
    }

    public static void stop() {
        if (!nativeAvailable) return;
        try { nativeStop(); }
        catch (Throwable error) { Log.w(TAG, "Could not stop Discord Rich Presence.", error); }
        finally { nativeAvailable = false; }
    }

    private static native void nativeStart();
    private static native void nativeUpdate(String details, String state);
    private static native void nativeStop();
}
