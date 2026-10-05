package net.orynlauncher.cosmetics.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.texture.PlayerSkinProvider;
import net.minecraft.entity.player.SkinTextures;
import net.orynlauncher.cosmetics.OrynRuntimeProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

@Mixin(PlayerSkinProvider.class)
public abstract class OrynPlayerSkinProviderMixin {
    @Inject(method = "supplySkinTextures", at = @At("HEAD"), cancellable = true)
    private void oryn$customSkin(GameProfile profile, boolean requireSecure,
                                 CallbackInfoReturnable<Supplier<SkinTextures>> cir) {
        OrynRuntimeProfile runtime = OrynRuntimeProfile.load();
        if (runtime == null || !runtime.matches(profile)) return;
        cir.setReturnValue(() -> runtime.createSkinTextures());
    }
}
