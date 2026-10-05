package net.orynlauncher.cosmetics.mixin;

import net.minecraft.client.texture.PlayerSkinProvider;
import net.minecraft.client.texture.PlayerSkinTextureDownloader;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import com.mojang.authlib.GameProfile;
import net.orynlauncher.cosmetics.OrynRuntimeProfile;
import net.orynlauncher.cosmetics.OrynRuntimeTextureServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;

@Mixin(PlayerSkinProvider.class)
public abstract class OrynPlayerSkinProviderMixin {
    @Inject(method = "supplySkinTextures", at = @At("HEAD"), cancellable = true)
    private void oryn$applySkin(GameProfile profile, boolean requireSecure,
                                CallbackInfoReturnable<Supplier<SkinTextures>> cir) {
        OrynRuntimeProfile runtime = OrynRuntimeProfile.load();
        if (runtime == null || !runtime.skinEnabled || !runtime.matches(profile)) return;

        System.out.println("[ORYN-COSMETICS] applySkin() called = true");
        System.out.println("[ORYN-COSMETICS] Renderer hook = NONE (vanilla renderer untouched)");

        cir.setReturnValue(() -> loadSkin(runtime, profile));
    }

    private SkinTextures loadSkin(OrynRuntimeProfile runtime, GameProfile profile) {
        File skin = runtime.resolveSkinFile();
        if (skin == null) return fail(profile, "skin path empty");
        if (!skin.isFile() || !skin.canRead()) return fail(profile, "PNG missing/unreadable: " + skin.getAbsolutePath());

        System.out.println("[ORYN-COSMETICS] PNG exists = true path=" + skin.getAbsolutePath()
                + " bytes=" + skin.length() + " modified=" + skin.lastModified());

        try {
            String url = OrynRuntimeTextureServer.startSkin(skin);
            if (url == null) return fail(profile, "runtime texture server unavailable");

            PlayerSkinTextureDownloader downloader =
                    ((OrynPlayerSkinProviderAccessor) (Object) this).oryn$getDownloader();
            Identifier textureId = Identifier.of("orynlauncher",
                    "cosmetics/skin/" + safeId(profile.id() == null ? "unknown" : profile.id().toString()));

            File cacheDir = new File(new File(System.getProperty("user.dir", ".")),
                    ".oryn/cosmetics/runtime-cache");
            cacheDir.mkdirs();
            File cacheFile = new File(cacheDir,
                    "skin-" + safeId(profile.id() == null ? "unknown" : profile.id().toString())
                            + "-" + skin.lastModified() + ".png");

            System.out.println("[ORYN-COSMETICS] Texture Identifier = " + textureId);
            System.out.println("[ORYN-COSMETICS] Native downloader = " + downloader.getClass().getName());
            System.out.println("[ORYN-COSMETICS] Native texture pipeline = PlayerSkinTextureDownloader -> TextureManager");

            CompletableFuture<AssetInfo.TextureAsset> future =
                    downloader.downloadAndRegisterTexture(textureId, cacheFile.toPath(), url, false);
            if (future == null) return fail(profile, "native downloader returned null future");

            AssetInfo.TextureAsset asset = future.join();
            if (asset == null) return fail(profile, "native downloader returned null texture asset");

            System.out.println("[ORYN-COSMETICS] Texture registered = true");
            System.out.println("[ORYN-COSMETICS] Texture path = " + asset.texturePath());

            try {
                java.util.Map<Identifier, net.minecraft.client.texture.AbstractTexture> textures =
                        ((OrynTextureManagerAccessor) (Object)
                                ((OrynPlayerSkinTextureDownloaderAccessor) (Object) downloader)
                                        .oryn$getTextureManager()).oryn$getTextures();
                boolean lookup = textures.containsKey(asset.texturePath()) || textures.containsKey(textureId);
                System.out.println("[ORYN-COSMETICS] TextureManager lookup = " + lookup);
                System.out.println("[ORYN-COSMETICS] TextureManager entry class = "
                        + (textures.get(asset.texturePath()) == null ? "null"
                        : textures.get(asset.texturePath()).getClass().getName()));
            } catch (Throwable lookupError) {
                System.out.println("[ORYN-COSMETICS] TextureManager lookup = ERROR " + lookupError);
            }

            PlayerSkinType skinType = "slim".equalsIgnoreCase(runtime.model)
                    ? PlayerSkinType.SLIM : PlayerSkinType.WIDE;
            SkinTextures textures = new SkinTextures(asset, null, null, skinType, false);

            System.out.println("[ORYN-COSMETICS] SkinTextures override = true"
                    + " body=" + asset.texturePath()
                    + " model=" + skinType
                    + " secure=false");
            return textures;
        } catch (Throwable t) {
            return fail(profile, "native texture pipeline exception: " + t);
        }
    }

    private SkinTextures fail(GameProfile profile, String reason) {
        System.out.println("[ORYN-COSMETICS] CUSTOM SKIN FAILED");
        System.out.println("[ORYN-COSMETICS] Reason: " + reason);
        System.out.println("[ORYN-COSMETICS] Vanilla fallback = true");
        return DefaultSkinHelper.getSkinTextures(profile);
    }

    private static String safeId(String value) {
        return value.replace("-", "").replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
