package net.kdt.pojavlaunch.cosmetics;

import android.content.Context;
import android.util.Log;

import net.kdt.pojavlaunch.authenticator.accounts.Account;

import java.io.File;

/**
 * Single launcher entry point for cosmetics.
 *
 * This class knows nothing about Minecraft mappings, PlayerRenderer,
 * SkinTextures, TextureManager, or renderer internals. It selects a
 * version adapter and hands the adapter an immutable OrynCosmeticsProfile.
 */
public final class OrynCosmeticsManager {
    private static final String TAG = "OrynCosmetics";

    private OrynCosmeticsManager() {}

    public static String prepareForLaunch(Context context, Account account) {
        return prepareForLaunch(context, account, null, null);
    }

    public static String prepareForLaunch(Context context, Account account, File gameDir) {
        return prepareForLaunch(context, account, gameDir, null);
    }

    public static String prepareForLaunch(Context context, Account account,
                                          File gameDir, String minecraftVersion) {
        try {
            OrynCosmeticsStore store = new OrynCosmeticsStore(context);
            OrynCosmeticsStore.CosmeticProfile stored = store.getActiveProfile(account);
            OrynCosmeticsProfile profile = OrynCosmeticsProfile.from(account, stored);

            OrynCosmeticsVersionAdapter adapter =
                    OrynCosmeticsVersionAdapterFactory.forVersion(minecraftVersion);

            Log.i(TAG, "[ORYN-COSMETICS] Minecraft version: " + safe(minecraftVersion));
            Log.i(TAG, "[ORYN-COSMETICS] Adapter: " + adapter.id());
            Log.i(TAG, "[ORYN-COSMETICS] Custom skin supported: "
                    + adapter.supportsSkin(minecraftVersion));
            Log.i(TAG, "[ORYN-COSMETICS] Custom cape supported: "
                    + adapter.supportsCape(minecraftVersion));
            Log.i(TAG, "[ORYN-COSMETICS] Account UUID: " + safeUuid(account));
            Log.i(TAG, "[ORYN-COSMETICS] Skin enabled: " + profile.skinEnabled);
            Log.i(TAG, "[ORYN-COSMETICS] Skin selected: " + profile.skinFile);
            Log.i(TAG, "[ORYN-COSMETICS] Skin model: " + profile.model);
            Log.i(TAG, "[ORYN-COSMETICS] Cape enabled: " + profile.capeEnabled);
            Log.i(TAG, "[ORYN-COSMETICS] Cape selected: " + profile.capeFile);

            return adapter.prepare(context, account, profile, gameDir, minecraftVersion);
        } catch (Throwable t) {
            Log.w(TAG, "[ORYN-COSMETICS] Adapter preparation failed; continuing with vanilla.", t);
            return "{}";
        }
    }

    public static void removeCustomSkin(Context context, Account account,
                                         File gameDir, String minecraftVersion) {
        OrynCosmeticsVersionAdapterFactory.forVersion(minecraftVersion)
                .removeCustomSkin(context, account, gameDir, minecraftVersion);
    }

    public static void removeCustomCape(Context context, Account account,
                                         File gameDir, String minecraftVersion) {
        OrynCosmeticsVersionAdapterFactory.forVersion(minecraftVersion)
                .removeCustomCape(context, account, gameDir, minecraftVersion);
    }

    public static void refreshPlayerTextures(Context context, Account account,
                                              File gameDir, String minecraftVersion) {
        OrynCosmeticsVersionAdapterFactory.forVersion(minecraftVersion)
                .refreshPlayerTextures(context, account, gameDir, minecraftVersion);
    }

    public static void clearCosmeticCache(Context context, Account account,
                                           File gameDir, String minecraftVersion) {
        OrynCosmeticsVersionAdapterFactory.forVersion(minecraftVersion)
                .clearCosmeticCache(context, account, gameDir, minecraftVersion);
    }

    private static String safeUuid(Account account) {
        if (account == null || account.profileId == null || account.profileId.isEmpty()) {
            return "unknown";
        }
        return account.profileId;
    }

    private static String safe(String value) {
        return value == null || value.isEmpty() ? "unknown" : value;
    }
}
