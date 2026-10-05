package net.kdt.pojavlaunch.cosmetics;

import net.kdt.pojavlaunch.authenticator.accounts.Account;

/**
 * Minecraft-version-independent snapshot of the cosmetics selected for one
 * launcher account. No Minecraft client classes or mappings are referenced.
 */
public final class OrynCosmeticsProfile {
    public final String accountUuid;
    public final String accountName;
    public final String skinFile;
    public final String capeFile;
    public final String model;
    public final boolean skinEnabled;
    public final boolean capeEnabled;

    private OrynCosmeticsProfile(String accountUuid, String accountName,
                                 String skinFile, String capeFile, String model,
                                 boolean skinEnabled, boolean capeEnabled) {
        this.accountUuid = accountUuid == null ? "" : accountUuid;
        this.accountName = accountName == null ? "" : accountName;
        this.skinFile = skinFile == null ? "" : skinFile;
        this.capeFile = capeFile == null ? "" : capeFile;
        this.model = "slim".equalsIgnoreCase(model) ? "slim" : "classic";
        this.skinEnabled = skinEnabled;
        this.capeEnabled = capeEnabled;
    }

    public static OrynCosmeticsProfile from(Account account, OrynCosmeticsStore.CosmeticProfile profile) {
        return new OrynCosmeticsProfile(
                account == null ? "" : account.profileId,
                account == null ? "" : account.username,
                profile == null ? "" : profile.skin,
                profile == null ? "" : profile.cape,
                profile == null ? "classic" : profile.model,
                profile != null && profile.skinEnabled,
                profile != null && profile.capeEnabled
        );
    }

    public boolean hasAnyEnabledCosmetic() {
        return (skinEnabled && !skinFile.isEmpty()) || (capeEnabled && !capeFile.isEmpty());
    }
}
