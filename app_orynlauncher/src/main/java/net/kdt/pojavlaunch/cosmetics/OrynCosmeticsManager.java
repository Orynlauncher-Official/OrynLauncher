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
            Log.i(TAG, "Skin selected: " + (profile.skin == null ? "" : profile.skin));
            Log.i(TAG, "Skin model: " + ("slim".equalsIgnoreCase(profile.model) ? "slim" : "classic"));
            Log.i(TAG, "Cape selected: " + (profile.cape == null ? "" : profile.cape));

            String properties = OrynLocalCosmeticsServer.prepare(context, account);
            if (properties == null || properties.trim().isEmpty() || "{}".equals(properties.trim())) {
                Log.i(TAG, "No enabled custom cosmetics will be supplied; Minecraft falls back normally");
                return "{}";
            }

            Log.i(TAG, "Cosmetics applied to Minecraft launch profile");
            Log.i(TAG, "Minecraft cosmetic provider initialized");
            return properties;
        } catch (Throwable t) {
            // Cosmetics must never prevent normal Minecraft startup.
            Log.w(TAG, "Cosmetics preparation failed; continuing with normal profile", t);
            return "{}";
        }
    }

    private static String safeUuid(Account account) {
        if (account == null || account.profileId == null || account.profileId.isEmpty()) {
            return "unknown";
        }
        return account.profileId;
    }
}
