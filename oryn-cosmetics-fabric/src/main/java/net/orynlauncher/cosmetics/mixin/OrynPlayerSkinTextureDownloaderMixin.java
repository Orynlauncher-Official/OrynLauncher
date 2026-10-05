package net.orynlauncher.cosmetics.mixin;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.PlayerSkinTextureDownloader;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

@Mixin(PlayerSkinTextureDownloader.class)
public abstract class OrynPlayerSkinTextureDownloaderMixin {
    private static int oryn$downloaderRequestCount;
    private static int oryn$pngDecodeCount;
    private static int oryn$textureRegistrationCount;

    @Inject(method = "downloadAndRegisterTexture", at = @At("HEAD"))
    private void oryn$downloadStart(Identifier id, Path path, String url, boolean remap,
                                    CallbackInfoReturnable<CompletableFuture<AssetInfo.TextureAsset>> cir) {
        if (isOrynUrl(url)) {
            oryn$downloaderRequestCount++;
            OrynPlayerSkinProviderMixin.recordDownloaderRequest();
            System.out.println("[ORYN-COSMETICS] Native downloader request count = "
                    + oryn$downloaderRequestCount);
            System.out.println("[ORYN-COSMETICS] Native downloader applySkin URL accepted = " + url);
            System.out.println("[ORYN-COSMETICS] Texture Identifier = " + id);
            System.out.println("[ORYN-COSMETICS] Cache path = " + path);
        }
    }

    @Inject(method = "download", at = @At("RETURN"))
    private void oryn$decoded(Path path, String url,
                              CallbackInfoReturnable<NativeImage> cir) {
        if (isOrynUrl(url)) {
            NativeImage image = cir.getReturnValue();
            if (image != null) {
                oryn$pngDecodeCount++;
                OrynPlayerSkinProviderMixin.recordPngDecode();
            }
            System.out.println("[ORYN-COSMETICS] PNG decoded = " + (image != null));
            System.out.println("[ORYN-COSMETICS] pngDecodeCount = " + oryn$pngDecodeCount);
            if (image != null) {
                System.out.println("[ORYN-COSMETICS] PNG width = " + image.getWidth());
                System.out.println("[ORYN-COSMETICS] PNG height = " + image.getHeight());
            }
        }
    }

    @Inject(method = "registerTexture", at = @At("HEAD"))
    private void oryn$registerStart(AssetInfo.TextureAsset asset, NativeImage image,
                                    CallbackInfoReturnable<CompletableFuture<AssetInfo.TextureAsset>> cir) {
        if (isOrynAsset(asset)) {
            oryn$textureRegistrationCount++;
            OrynPlayerSkinProviderMixin.recordTextureRegistration();
            System.out.println("[ORYN-COSMETICS] Texture registration count = "
                    + oryn$textureRegistrationCount);
            System.out.println("[ORYN-COSMETICS] Texture registration entered id="
                    + asset.id() + " texturePath=" + asset.texturePath()
                    + " image=" + (image == null ? "null" : image.getWidth() + "x" + image.getHeight()));
        }
    }

    @Inject(method = "registerTexture", at = @At("RETURN"))
    private void oryn$registerEnd(AssetInfo.TextureAsset asset, NativeImage image,
                                  CallbackInfoReturnable<CompletableFuture<AssetInfo.TextureAsset>> cir) {
        if (isOrynAsset(asset)) {
            System.out.println("[ORYN-COSMETICS] Texture registration future returned = "
                    + (cir.getReturnValue() != null));
        }
    }

    private static boolean isOrynUrl(String url) {
        return url != null && url.startsWith("http://127.0.0.1:");
    }

    private static boolean isOrynAsset(AssetInfo.TextureAsset asset) {
        return asset != null && asset.texturePath() != null
                && asset.texturePath().getNamespace().equals("orynlauncher");
    }
}
