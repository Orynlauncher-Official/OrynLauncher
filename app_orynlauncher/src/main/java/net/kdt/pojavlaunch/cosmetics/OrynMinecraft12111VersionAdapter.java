package net.kdt.pojavlaunch.cosmetics;

import android.content.Context;
import android.util.Log;

import net.kdt.pojavlaunch.authenticator.accounts.Account;

import java.io.File;

/**
 * Minecraft 1.21.11 adapter.
 *
 * Only account-scoped runtime state and the version-specific bridge are
 * prepared here. Minecraft remains responsible for decoding, registering
 * and rendering the texture.
 */
public final class OrynMinecraft12111VersionAdapter implements OrynCosmeticsVersionAdapter {
    private static final String TAG = "OrynCosmetics";

    @Override
    public String id() {
        return "minecraft-1.21.11-native-skin-provider";
    }

    @Override
    public boolean supportsSkin(String minecraftVersion) {
        return "1.21.11".equals(minecraftVersion);
    }

    @Override
    public boolean supportsCape(String minecraftVersion) {
        // Skin-first rollout: cape is intentionally gated for now.
        return false;
    }

    @Override
    public String prepare(Context context, Account account, OrynCosmeticsProfile profile,
                          File gameDir, String minecraftVersion) {
        Log.i(TAG, "[ORYN-COSMETICS] Minecraft version: " + minecraftVersion);
        Log.i(TAG, "[ORYN-COSMETICS] Adapter: " + id());
        Log.i(TAG, "[ORYN-COSMETICS] Custom skin supported: true");
        Log.i(TAG, "[ORYN-COSMETICS] Custom cape supported: false (skin-first rollout)");

        if (gameDir != null) {
            new OrynCosmeticsStore(context).writeActiveForInstance(gameDir, account);
            installBridge(context, gameDir);
        }

        // The launcher process exits after JVM startup. Runtime transport is
        // therefore created by the bridge inside the Minecraft JVM.
        return "{}";
    }

    @Override
    public void removeCustomSkin(Context context, Account account, File gameDir, String minecraftVersion) {
        Log.i(TAG, "[ORYN-COSMETICS] 1.21.11 custom skin disabled; vanilla provider remains authoritative.");
    }

    @Override
    public void removeCustomCape(Context context, Account account, File gameDir, String minecraftVersion) {
        Log.i(TAG, "[ORYN-COSMETICS] 1.21.11 cape is disabled during skin-first rollout.");
    }

    @Override
    public void refreshPlayerTextures(Context context, Account account, File gameDir, String minecraftVersion) {
        if (gameDir != null) new OrynCosmeticsStore(context).writeActiveForInstance(gameDir, account);
        Log.i(TAG, "[ORYN-COSMETICS] 1.21.11 skin refresh requested.");
    }

    @Override
    public void clearCosmeticCache(Context context, Account account, File gameDir, String minecraftVersion) {
        Log.i(TAG, "[ORYN-COSMETICS] 1.21.11 native skin cache clear requested.");
    }

    private static void installBridge(Context context, File gameDir) {
        try {
            File mods = new File(gameDir, "mods");
            if (!mods.exists() && !mods.mkdirs()) {
                Log.w(TAG, "[ORYN-COSMETICS] Could not create Minecraft mods directory.");
                return;
            }

            File target = new File(mods, "orynlauncher-cosmetics-1.0.0.jar");
            try (java.io.InputStream in =
                         context.getAssets().open("orynlauncher/oryn_cosmetics_fabric.jar");
                 java.io.FileOutputStream out = new java.io.FileOutputStream(target)) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            }
            Log.i(TAG, "[ORYN-COSMETICS] 1.21.11 native skin adapter installed.");
        } catch (Exception e) {
            Log.w(TAG, "[ORYN-COSMETICS] 1.21.11 bridge unavailable; falling back to vanilla.", e);
        }
    }
}
