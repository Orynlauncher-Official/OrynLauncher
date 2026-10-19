package net.orynlauncher.cosmetics.mixin;

import net.minecraft.client.texture.PlayerSkinTextureDownloader;
import net.minecraft.client.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerSkinTextureDownloader.class)
public interface OrynPlayerSkinTextureDownloaderAccessor {
    @Accessor("textureManager")
    TextureManager oryn$getTextureManager();
}
