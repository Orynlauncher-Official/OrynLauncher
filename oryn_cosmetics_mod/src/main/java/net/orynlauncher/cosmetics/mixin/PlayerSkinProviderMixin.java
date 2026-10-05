package net.orynlauncher.cosmetics.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.PlayerSkinProvider;
import net.minecraft.entity.player.SkinTextures;
import net.orynlauncher.cosmetics.OrynCosmeticsRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Mixin(PlayerSkinProvider.class)
public abstract class PlayerSkinProviderMixin {
    @Inject(method = "fetchSkinTextures", at = @At("HEAD"), cancellable = true)
    private void oryn$fetch(GameProfile profile,
            CallbackInfoReturnable<CompletableFuture<Optional<SkinTextures>>> cir) {
        if (!OrynCosmeticsRuntime.enabled()) return;
        System.out.println("[ORYN-COSMETICS] GameProfile properties: textures property present="
                + (profile.getProperties().get("textures") != null));
        CompletableFuture<Optional<SkinTextures>> result = new CompletableFuture<>();
        MinecraftClient.getInstance().execute(() -> {
            SkinTextures textures = OrynCosmeticsRuntime.getOrCreateTextures();
            OrynCosmeticsRuntime.logProviderResult(textures);
            result.complete(textures == null ? Optional.empty() : Optional.of(textures));
        });
        cir.setReturnValue(result);
    }
}
