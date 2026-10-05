package net.kdt.pojavlaunch.cosmetics;

import android.content.Context;

import net.kdt.pojavlaunch.authenticator.accounts.Account;

import java.io.File;

/**
 * Launcher-side contract for a Minecraft-version family.
 *
 * This interface intentionally contains zero Minecraft classes. A version
 * adapter prepares data for that version's native skin/cape pipeline; the
 * actual renderer remains vanilla Minecraft.
 */
public interface OrynCosmeticsVersionAdapter {
    String id();

    boolean supportsSkin(String minecraftVersion);

    boolean supportsCape(String minecraftVersion);

    String prepare(Context context, Account account, OrynCosmeticsProfile profile,
                   File gameDir, String minecraftVersion);

    void removeCustomSkin(Context context, Account account, File gameDir, String minecraftVersion);

    void removeCustomCape(Context context, Account account, File gameDir, String minecraftVersion);

    void refreshPlayerTextures(Context context, Account account, File gameDir, String minecraftVersion);

    void clearCosmeticCache(Context context, Account account, File gameDir, String minecraftVersion);
}
