package net.orynlauncher.cosmetics.mixin;

import net.minecraft.client.texture.PlayerSkinTextureDownloader;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import net.minecraft.client.texture.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

@Mixin(PlayerSkinTextureDownloader.class)
public abstract class OrynPlayerSkinTextureDownloaderMixin {
    @Inject(method = "downloadAndRegisterTexture", at = @At("HEAD"))
    private void oryn$downloadStart(Identifier id, Path path, String url, boolean remap,
                                    CallbackInfoReturnable<CompletableFuture<AssetInfo.TextureAsset>> cir) {
        if (url != null && url.startsWith("http://127.0.0.1:")) {
            System.out.println("[ORYN-COSMETICS] Native downloader applySkin URL accepted = " + url);
            System.out.println("[ORYN-COSMETICS] Texture Identifier = " + id);
            System.out.println("[ORYN-COSMETICS] Cache path = " + path);
        }
    }

    @Inject(method = "download", at = @At("RETURN"))
    private void oryn$decoded(Path path, String url,
                              CallbackInfoReturnable<NativeImage> cir) {
        if (url != null && url.startsWith("http://127.0.0.1:")) {
            NativeImage image = cir.getReturnValue();
            System.out.println("[ORYN-COSMETICS] PNG decoded = " + (image != null));
            if (image != null) {
                System.out.println("[ORYN-COSMETICS] PNG width = " + image.getWidth());
                System.out.println("[ORYN-COSMETICS] PNG height = " + image.getHeight());
            }
        }
    }

    @Inject(method = "registerTexture", at = @At("HEAD"))
    private void oryn$registerStart(AssetInfo.TextureAsset asset, NativeImage image,
                                    CallbackInfoReturnable<CompletableFuture<AssetInfo.TextureAsset>> cir) {
        if (asset != null && asset.texturePath() != null
                && asset.texturePath().toString().startsWith("orynlauncher:")) {
            System.out.println("[ORYN-COSMETICS] Texture registration entered id="
                    + asset.id() + " texturePath=" + asset.texturePath()
                    + " image=" + (image == null ? "null" : image.getWidth() + "x" + image.getHeight()));
        }
    }

    @Inject(method = "registerTexture", at = @At("RETURN"))
    private void oryn$registerEnd(AssetInfo.TextureAsset asset, NativeImage image,
                                  CallbackInfoReturnable<CompletableFuture<AssetInfo.TextureAsset>> cir) {
        if (asset != null && asset.texturePath() != null
                && asset.texturePath().toString().startsWith("orynlauncher:")) {
            System.out.println("[ORYN-COSMETICS] Texture registration future returned = "
                    + (cir.getReturnValue() != null));
        }
    }
}
