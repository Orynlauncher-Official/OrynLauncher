package net.orynlauncher.cosmetics.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.texture.PlayerSkinProvider;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;
import net.orynlauncher.cosmetics.OrynRuntimeProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Mixin(PlayerSkinProvider.class)
public abstract class OrynPlayerSkinProviderMixin {
    @Inject(method = "supplySkinTextures", at = @At("HEAD"), cancellable = true)
    private void oryn$customSkin(GameProfile profile, boolean requireSecure,
                                 CallbackInfoReturnable<Supplier<SkinTextures>> cir) {
        OrynRuntimeProfile runtime = OrynRuntimeProfile.load();
        if (runtime == null || !runtime.matches(profile)) return;

        System.out.println("[ORYN-COSMETICS] GameProfile properties: textures property present="
                + (profile.properties().get("textures") != null)
                + " account=" + profile.name()
                + " uuid=" + profile.id());

        if (runtime.skinEnabled) {
            // Return the custom SkinTextures directly to Minecraft's normal skin
            // cache. No Mojang skin URL is involved.
            cir.setReturnValue(() -> {
                SkinTextures textures = runtime.createSkinTextures();
                System.out.println("[ORYN-COSMETICS] PlayerSkinProvider supplied custom skin="
                        + (textures == null ? "null" : textures.body().texturePath())
                        + " cape=" + (textures == null || textures.cape() == null
                        ? "null" : textures.cape().texturePath())
                        + " model=" + (textures == null ? "null" : textures.model()));
                return textures;
            });
            return;
        }

        if (runtime.capeEnabled) {
            // Let vanilla resolve the base skin, then merge only Oryn's local cape.
            // This keeps cape-only profiles compatible with normal accounts.
            cir.setReturnValue(() -> {
                SkinTextures base = DefaultSkinHelper.getSkinTextures(profile);
                return runtime.withCape(base);
            });
        }
    }

    @Inject(method = "fetchSkinTextures", at = @At("RETURN"), cancellable = true)
    private void oryn$diagnostic(GameProfile profile,
                                 CallbackInfoReturnable<CompletableFuture<Optional<SkinTextures>>> cir) {
        OrynRuntimeProfile runtime = OrynRuntimeProfile.load();
        if (runtime == null || !runtime.matches(profile)) return;

        CompletableFuture<Optional<SkinTextures>> future = cir.getReturnValue();
        if (future == null) return;

        cir.setReturnValue(future.thenApply(optional -> {
            SkinTextures textures = optional.orElse(null);
            System.out.println("[ORYN-COSMETICS] fetchSkinTextures result: skin="
                    + (textures == null ? "null" : textures.body().texturePath())
                    + " cape=" + (textures == null || textures.cape() == null
                    ? "null" : textures.cape().texturePath())
                    + " model=" + (textures == null ? "null" : textures.model()));
            return optional;
        }));
    }
}
