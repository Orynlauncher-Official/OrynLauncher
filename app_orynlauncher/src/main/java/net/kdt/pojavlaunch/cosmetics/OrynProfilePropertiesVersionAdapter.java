package net.kdt.pojavlaunch.cosmetics;

import android.content.Context;
import android.util.Log;

import net.kdt.pojavlaunch.authenticator.accounts.Account;

import java.io.File;

/**
 * Adapter for Minecraft versions whose vanilla client resolves skins/capes from
 * the standard Authlib GameProfile "textures" property.
 *
 * The texture bytes are never decoded or re-mapped here. Minecraft's own
 * PlayerSkinProvider/TextureManager pipeline downloads and registers the PNG.
 */
public final class OrynProfilePropertiesVersionAdapter implements OrynCosmeticsVersionAdapter {
    private static final String TAG = "OrynCosmetics";

    @Override
    public String id() {
        return "authlib-gameprofile-textures";
    }

    @Override
    public boolean supportsSkin(String minecraftVersion) {
        return true;
    }

    @Override
    public boolean supportsCape(String minecraftVersion) {
        return true;
    }

    @Override
    public String prepare(Context context, Account account, OrynCosmeticsProfile profile,
                          File gameDir, String minecraftVersion) {
        Log.i(TAG, "[ORYN-COSMETICS] Minecraft version: " + safe(minecraftVersion));
        Log.i(TAG, "[ORYN-COSMETICS] Adapter: " + id());
        Log.i(TAG, "[ORYN-COSMETICS] Custom skin supported: true");
        Log.i(TAG, "[ORYN-COSMETICS] Custom cape supported: true");
        if (gameDir != null) {
            new OrynCosmeticsStore(context).writeActiveForInstance(gameDir, account);
        }
        return OrynLocalCosmeticsServer.prepare(context, account);
    }

    @Override
    public void removeCustomSkin(Context context, Account account, File gameDir, String minecraftVersion) {
        Log.i(TAG, "[ORYN-COSMETICS] Skin removed; vanilla skin pipeline restored.");
    }

    @Override
    public void removeCustomCape(Context context, Account account, File gameDir, String minecraftVersion) {
        Log.i(TAG, "[ORYN-COSMETICS] Cape removed; vanilla cape pipeline restored.");
    }

    @Override
    public void refreshPlayerTextures(Context context, Account account, File gameDir, String minecraftVersion) {
        if (gameDir != null) new OrynCosmeticsStore(context).writeActiveForInstance(gameDir, account);
        Log.i(TAG, "[ORYN-COSMETICS] Texture refresh requested for " + safe(minecraftVersion));
    }

    @Override
    public void clearCosmeticCache(Context context, Account account, File gameDir, String minecraftVersion) {
        Log.i(TAG, "[ORYN-COSMETICS] Adapter cache clear requested; launcher-owned PNG cache is preserved.");
    }

    private static String safe(String value) {
        return value == null || value.isEmpty() ? "unknown" : value;
    }
}
