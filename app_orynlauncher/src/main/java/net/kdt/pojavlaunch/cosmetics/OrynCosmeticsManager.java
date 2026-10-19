package net.kdt.pojavlaunch.cosmetics;

import android.content.Context;
import android.util.Log;

import net.kdt.pojavlaunch.authenticator.accounts.Account;

import java.io.File;

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

            Log.i(TAG, "[ORYN-COSMETICS] Minecraft version = " + safe(minecraftVersion));
            Log.i(TAG, "[ORYN-COSMETICS] Selected adapter = " + adapter.id());
            Log.i(TAG, "[ORYN-COSMETICS] Adapter initialized = true");
            Log.i(TAG, "[ORYN-COSMETICS] account UUID = " + safeUuid(account));
            Log.i(TAG, "[ORYN-COSMETICS] skin enabled = " + profile.skinEnabled);
            Log.i(TAG, "[ORYN-COSMETICS] skin path = " + profile.skinFile);
            Log.i(TAG, "[ORYN-COSMETICS] skin model = " + profile.model);
            Log.i(TAG, "[ORYN-COSMETICS] cape enabled = " + profile.capeEnabled);
            Log.i(TAG, "[ORYN-COSMETICS] cape path = " + profile.capeFile);
            java.io.File capeFile = profile.capeFile.isEmpty() ? null : new java.io.File(profile.capeFile);
            Log.i(TAG, "[ORYN-COSMETICS] Cape enabled=" + profile.capeEnabled);
            Log.i(TAG, "[ORYN-COSMETICS] Cape file=" + (capeFile == null ? "" : capeFile.getAbsolutePath()));
            Log.i(TAG, "[ORYN-COSMETICS] Cape exists=" + (capeFile != null && capeFile.isFile()));
            Log.i(TAG, "[ORYN-COSMETICS] Cape size=" + (capeFile != null && capeFile.isFile() ? capeFile.length() : 0));
            Log.i(TAG, "[ORYN-COSMETICS] Cape texture=" + (capeFile == null ? "null" : "pending-runtime-registration"));
            Log.i(TAG, "[ORYN-COSMETICS] applySkin() called = delegated to runtime adapter");
            Log.i(TAG, "[ORYN-COSMETICS] applyCape() called = not invoked (skin-first debug phase)");

            if (profile.skinEnabled && profile.skinFile.isEmpty()) {
                Log.e(TAG, "[ORYN-COSMETICS] CUSTOM SKIN FAILED Reason: skin enabled but persisted skin path is empty");
            }

            return adapter.prepare(context, account, profile, gameDir, minecraftVersion);
        } catch (Throwable t) {
            Log.e(TAG, "[ORYN-COSMETICS] CUSTOM SKIN FAILED Reason: launch cosmetics handoff exception", t);
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
        if (account == null || account.profileId == null || account.profileId.isEmpty()) return "unknown";
        return account.profileId;
    }

    private static String safe(String value) {
        return value == null || value.isEmpty() ? "unknown" : value;
    }
}
