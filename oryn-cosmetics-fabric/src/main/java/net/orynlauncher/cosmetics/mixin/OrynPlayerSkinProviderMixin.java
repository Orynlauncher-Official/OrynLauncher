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

/**
 * 1.21.11-only bridge.
 *
 * Important: this does NOT decode PNGs, register NativeImage textures, create
 * UVs, replace PlayerRenderer, or manually draw a cape. It only bypasses the
 * secure-property gate for Oryn's local GameProfile payload and then asks the
 * real 1.21.11 PlayerSkinProvider to load/register the textures.
 */
@Mixin(PlayerSkinProvider.class)
public abstract class OrynPlayerSkinProviderMixin {
    @Inject(method = "supplySkinTextures", at = @At("HEAD"), cancellable = true)
    private void oryn$customSkin(GameProfile profile, boolean requireSecure,
                                 CallbackInfoReturnable<Supplier<SkinTextures>> cir) {
        OrynRuntimeProfile runtime = OrynRuntimeProfile.load();
        if (runtime == null || !runtime.matches(profile)) return;

        System.out.println("[ORYN-COSMETICS] 1.21.11 adapter active"
                + " account=" + profile.name()
                + " uuid=" + profile.id()
                + " requireSecure=" + requireSecure
                + " texturesProperty=" + (profile.properties().get("textures") != null));

        cir.setReturnValue(() -> {
            try {
                PlayerSkinProvider provider = (PlayerSkinProvider) (Object) this;
                SkinTextures vanilla = DefaultSkinHelper.getSkinTextures(profile);

                CompletableFuture<Optional<SkinTextures>> future =
                        provider.fetchSkinTextures(profile);
                Optional<SkinTextures> loaded = future == null ? Optional.empty() : future.join();

                if (loaded.isPresent()) {
                    SkinTextures custom = loaded.get();

                    // When Oryn has a skin, use the complete result produced by
                    // vanilla's downloader/cache. No pixel manipulation occurs.
                    if (runtime.skinEnabled) {
                        System.out.println("[ORYN-COSMETICS] Vanilla provider loaded Oryn skin"
                                + " body=" + custom.body().texturePath()
                                + " cape=" + (custom.cape() == null
                                ? "null" : custom.cape().texturePath())
                                + " model=" + custom.model());
                        return custom;
                    }

                    // Cape-only profile: retain the account's vanilla skin/model
                    // and use only the cape asset loaded by vanilla.
                    if (runtime.capeEnabled && custom.cape() != null) {
                        SkinTextures capeOnly = SkinTextures.create(
                                vanilla.body(),
                                custom.cape(),
                                custom.elytra(),
                                vanilla.model()
                        );
                        System.out.println("[ORYN-COSMETICS] Vanilla provider loaded Oryn cape"
                                + " cape=" + custom.cape().texturePath());
                        return capeOnly;
                    }
                }

                System.out.println("[ORYN-COSMETICS] Oryn texture load returned no usable asset; "
                        + "falling back to vanilla skin.");
                return vanilla;
            } catch (Throwable t) {
                System.out.println("[ORYN-COSMETICS] Native vanilla skin pipeline failed; "
                        + "falling back to vanilla: " + t);
                return DefaultSkinHelper.getSkinTextures(profile);
            }
        });
    }

    @Inject(method = "fetchSkinTextures", at = @At("RETURN"))
    private void oryn$diagnostic(GameProfile profile,
                                 CallbackInfoReturnable<CompletableFuture<Optional<SkinTextures>>> cir) {
        OrynRuntimeProfile runtime = OrynRuntimeProfile.load();
        if (runtime == null || !runtime.matches(profile)) return;

        CompletableFuture<Optional<SkinTextures>> future = cir.getReturnValue();
        if (future == null) return;

        future.thenAccept(optional -> {
            SkinTextures textures = optional.orElse(null);
            System.out.println("[ORYN-COSMETICS] vanilla fetch result: skin="
                    + (textures == null ? "null" : textures.body().texturePath())
                    + " cape=" + (textures == null || textures.cape() == null
                    ? "null" : textures.cape().texturePath())
                    + " model=" + (textures == null ? "null" : textures.model()));
        });
    }
}
