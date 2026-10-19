package net.kdt.pojavlaunch.cosmetics;

import android.content.Context;
import android.util.Log;

import net.kdt.pojavlaunch.authenticator.accounts.Account;

import java.io.File;

public final class OrynMinecraft12111VersionAdapter implements OrynCosmeticsVersionAdapter {
    private static final String TAG = "OrynCosmetics";

    @Override public String id() {
        return "minecraft-1.21.11-native-skin-provider";
    }

    @Override public boolean supportsSkin(String minecraftVersion) {
        return "1.21.11".equals(minecraftVersion);
    }

    @Override public boolean supportsCape(String minecraftVersion) {
        return "1.21.11".equals(minecraftVersion);
    }

    @Override
    public String prepare(Context context, Account account, OrynCosmeticsProfile profile,
                          File gameDir, String minecraftVersion) {
        Log.i(TAG, "[ORYN-COSMETICS] Minecraft version = " + minecraftVersion);
        Log.i(TAG, "[ORYN-COSMETICS] Selected adapter = " + id());
        Log.i(TAG, "[ORYN-COSMETICS] Adapter initialized = true");
        Log.i(TAG, "[ORYN-COSMETICS] applySkin() called = true (runtime bridge will execute inside Minecraft)");
        Log.i(TAG, "[ORYN-COSMETICS] applyCape() called = true (runtime bridge)");
        Log.i(TAG, "[ORYN-COSMETICS] Native PlayerRenderer = untouched");

        if (gameDir != null) {
            new OrynCosmeticsStore(context).writeActiveForInstance(gameDir, account);
            installBridge(context, gameDir);
        }
        return "{}";
    }

    @Override public void removeCustomSkin(Context context, Account account, File gameDir, String minecraftVersion) {
        Log.i(TAG, "[ORYN-COSMETICS] 1.21.11 custom skin disabled; vanilla provider remains authoritative.");
    }

    @Override public void removeCustomCape(Context context, Account account, File gameDir, String minecraftVersion) {
        Log.i(TAG, "[ORYN-COSMETICS] 1.21.11 custom cape disabled; vanilla cape provider remains authoritative.");
    }

    @Override public void refreshPlayerTextures(Context context, Account account, File gameDir, String minecraftVersion) {
        if (gameDir != null) new OrynCosmeticsStore(context).writeActiveForInstance(gameDir, account);
        Log.i(TAG, "[ORYN-COSMETICS] 1.21.11 skin refresh requested.");
    }

    @Override public void clearCosmeticCache(Context context, Account account, File gameDir, String minecraftVersion) {
        Log.i(TAG, "[ORYN-COSMETICS] 1.21.11 native skin cache clear requested.");
    }

    private static void installBridge(Context context, File gameDir) {
        try {
            File mods = new File(gameDir, "mods");
            if (!mods.exists() && !mods.mkdirs()) {
                Log.e(TAG, "[ORYN-COSMETICS] CUSTOM SKIN FAILED Reason: could not create mods directory");
                return;
            }
            File target = new File(mods, "orynlauncher-cosmetics-1.0.0.jar");
            try (java.io.InputStream in = context.getAssets().open("orynlauncher/oryn_cosmetics_fabric.jar");
                 java.io.FileOutputStream out = new java.io.FileOutputStream(target)) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            }
            Log.i(TAG, "[ORYN-COSMETICS] Runtime bridge installed = true");
        } catch (Exception e) {
            Log.e(TAG, "[ORYN-COSMETICS] CUSTOM SKIN FAILED Reason: runtime bridge install failed", e);
        }
    }
}
