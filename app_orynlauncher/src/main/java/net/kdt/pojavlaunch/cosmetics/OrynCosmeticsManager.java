package net.kdt.pojavlaunch.cosmetics;

import android.content.Context;
import android.util.Log;

import net.kdt.pojavlaunch.authenticator.accounts.Account;

/**
 * Launch-time bridge between Oryn's persistent cosmetic profile and Minecraft's
 * normal GameProfile texture pipeline.
 *
 * Authentication is deliberately untouched. The returned JSON is only the
 * Minecraft user/profile properties value used to expose the selected texture
 * URLs to the client.
 */
public final class OrynCosmeticsManager {
    private static final String TAG = "OrynCosmetics";

    private OrynCosmeticsManager() {}

    public static String prepareForLaunch(Context context, Account account) {
        try {
            OrynCosmeticsStore store = new OrynCosmeticsStore(context);
            OrynCosmeticsStore.CosmeticProfile profile = store.getActiveProfile(account);

            Log.i(TAG, "Launch preparation started");
            Log.i(TAG, "Account UUID: " + safeUuid(account));
            Log.i(TAG, "Skin enabled: " + profile.skinEnabled);
            Log.i(TAG, "Skin selected: " + (profile.skin == null ? "" : profile.skin));
            Log.i(TAG, "Skin model: " + ("slim".equalsIgnoreCase(profile.model) ? "slim" : "classic"));
            Log.i(TAG, "Cape enabled: " + profile.capeEnabled);
            Log.i(TAG, "Cape selected: " + (profile.cape == null ? "" : profile.cape));

            return "{}";
        } catch (Throwable t) {
            Log.w(TAG, "Cosmetics preparation failed; continuing with normal profile", t);
            return "{}";
        }
    }

    public static String prepareForLaunch(Context context, Account account, java.io.File gameDir) {
        return prepareForLaunch(context, account, gameDir, null);
    }

    public static String prepareForLaunch(Context context, Account account, java.io.File gameDir, String minecraftVersion) {
        try {
            OrynCosmeticsStore store = new OrynCosmeticsStore(context);
            OrynCosmeticsStore.CosmeticProfile profile = store.getActiveProfile(account);

            Log.i(TAG, "Launch preparation started");
            Log.i(TAG, "Account UUID: " + safeUuid(account));
            Log.i(TAG, "Skin enabled: " + profile.skinEnabled);
            Log.i(TAG, "Skin selected: " + (profile.skin == null ? "" : profile.skin));
            Log.i(TAG, "Skin model: " + ("slim".equalsIgnoreCase(profile.model) ? "slim" : "classic"));
            Log.i(TAG, "Cape enabled: " + profile.capeEnabled);
            Log.i(TAG, "Cape selected: " + (profile.cape == null ? "" : profile.cape));

            if (gameDir != null) {
                store.writeActiveForInstance(gameDir, account);
                if ("1.21.11".equals(minecraftVersion)) {
                    installFabricBridge(context, gameDir);
                    Log.i(TAG, "Minecraft 1.21.11 cosmetics bridge profile prepared in " + gameDir.getAbsolutePath());
                } else {
                    Log.i(TAG, "Minecraft cosmetics bridge skipped for unsupported version: " + minecraftVersion);
                }
            }
            return "{}";
        } catch (Throwable t) {
            Log.w(TAG, "Cosmetics preparation failed; continuing with normal profile", t);
            return "{}";
        }
    }

    private static void installFabricBridge(Context context, java.io.File gameDir) {
        try {
            if (gameDir == null) return;
            java.io.File mods = new java.io.File(gameDir, "mods");
            if (!mods.exists() && !mods.mkdirs()) return;
            java.io.File target = new java.io.File(mods, "orynlauncher-cosmetics-1.0.0.jar");
            try (java.io.InputStream in = context.getAssets().open("components/oryn-cosmetics/oryn-cosmetics-1.0.0.jar");
                 java.io.FileOutputStream out = new java.io.FileOutputStream(target)) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            }
            Log.i(TAG, "Minecraft client cosmetics bridge installed: " + target.getAbsolutePath());
        } catch (Exception e) {
            Log.w(TAG, "Fabric cosmetics bridge asset is unavailable; continuing without client hook", e);
        }
    }

    private static String safeUuid(Account account) {
        if (account == null || account.profileId == null || account.profileId.isEmpty()) {
            return "unknown";
        }
        return account.profileId;
    }
}
