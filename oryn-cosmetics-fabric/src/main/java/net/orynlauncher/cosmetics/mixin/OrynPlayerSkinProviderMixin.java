package net.orynlauncher.cosmetics.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.texture.PlayerSkinProvider;
import net.minecraft.client.texture.PlayerSkinTextureDownloader;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;
import net.orynlauncher.cosmetics.OrynCosmeticsRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

@Mixin(PlayerSkinProvider.class)
public abstract class OrynPlayerSkinProviderMixin {
    @Inject(method = "supplySkinTextures", at = @At("HEAD"), cancellable = true)
    private void oryn$provideCachedSkin(GameProfile profile, boolean requireSecure,
                                        CallbackInfoReturnable<Supplier<SkinTextures>> cir) {
        if (profile == null) return;

        PlayerSkinTextureDownloader downloader =
                ((OrynPlayerSkinProviderAccessor) (Object) this).oryn$getDownloader();

        /*
         * This hook is intentionally non-blocking. The runtime starts cosmetic
         * initialization asynchronously and returns vanilla immediately until
         * a cached Oryn SkinTextures object is ready.
         */
        cir.setReturnValue(() -> {
            try {
                return OrynCosmeticsRuntime.getOrStart(profile, downloader);
            } catch (Throwable t) {
                System.out.println("[ORYN-COSMETICS] Cosmetic initialization failed: " + t);
                return DefaultSkinHelper.getSkinTextures(profile);
            }
        });
    }
}
