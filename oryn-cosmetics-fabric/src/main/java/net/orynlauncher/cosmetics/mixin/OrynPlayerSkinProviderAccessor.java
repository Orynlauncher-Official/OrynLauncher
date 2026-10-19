package net.orynlauncher.cosmetics.mixin;

import net.minecraft.client.texture.PlayerSkinProvider;
import net.minecraft.client.texture.PlayerSkinTextureDownloader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerSkinProvider.class)
public interface OrynPlayerSkinProviderAccessor {
    @Accessor("downloader")
    PlayerSkinTextureDownloader oryn$getDownloader();
}
