package net.kdt.pojavlaunch.cosmetics;

import android.util.Log;

/**
 * Selects the adapter from the exact Minecraft version ID supplied by
 * JVersionList.Version. The launcher UI never needs to know the selection.
 */
public final class OrynCosmeticsVersionAdapterFactory {
    private static final String TAG = "OrynCosmetics";

    private OrynCosmeticsVersionAdapterFactory() {}

    public static OrynCosmeticsVersionAdapter forVersion(String minecraftVersion) {
        String version = minecraftVersion == null ? "" : minecraftVersion.trim();

        if ("1.21.11".equals(version)) {
            return new OrynMinecraft12111VersionAdapter();
        }

        if (isAuthlibProfileVersion(version)) {
            return new OrynProfilePropertiesVersionAdapter();
        }

        return new OrynUnsupportedVersionAdapter();
    }

    private static boolean isAuthlibProfileVersion(String version) {
        if (version.isEmpty()) return false;

        // Stable releases and snapshots with a 1.x Java Edition base use the
        // standard GameProfile texture property path. We deliberately do not
        // guess mappings or load Minecraft classes in the launcher process.
        if (!version.startsWith("1.")) return false;

        try {
            String[] parts = version.split("\.");
            int minor = Integer.parseInt(parts[1]);
            return minor >= 7;
        } catch (Exception ignored) {
            // Snapshot IDs such as 24w05a are handled by the vanilla fallback
            // rather than pretending we know their mappings.
            return false;
        }
    }

    private static final class OrynUnsupportedVersionAdapter implements OrynCosmeticsVersionAdapter {
        @Override public String id() { return "vanilla-fallback"; }
        @Override public boolean supportsSkin(String minecraftVersion) { return false; }
        @Override public boolean supportsCape(String minecraftVersion) { return false; }

        @Override
        public String prepare(android.content.Context context,
                              net.kdt.pojavlaunch.authenticator.accounts.Account account,
                              OrynCosmeticsProfile profile,
                              java.io.File gameDir,
                              String minecraftVersion) {
            Log.w(TAG, "[ORYN-COSMETICS] Minecraft version: " + minecraftVersion);
            Log.w(TAG, "[ORYN-COSMETICS] Adapter: vanilla-fallback");
            Log.w(TAG, "[ORYN-COSMETICS] Custom skin supported: false");
            Log.w(TAG, "[ORYN-COSMETICS] Custom cape supported: false");
            Log.w(TAG, "[ORYN-COSMETICS] Keeping vanilla skin/cape behavior; Minecraft will not be modified.");
            return "{}";
        }

        @Override public void removeCustomSkin(android.content.Context c, net.kdt.pojavlaunch.authenticator.accounts.Account a, java.io.File g, String v) {}
        @Override public void removeCustomCape(android.content.Context c, net.kdt.pojavlaunch.authenticator.accounts.Account a, java.io.File g, String v) {}
        @Override public void refreshPlayerTextures(android.content.Context c, net.kdt.pojavlaunch.authenticator.accounts.Account a, java.io.File g, String v) {}
        @Override public void clearCosmeticCache(android.content.Context c, net.kdt.pojavlaunch.authenticator.accounts.Account a, java.io.File g, String v) {}
    }
}
