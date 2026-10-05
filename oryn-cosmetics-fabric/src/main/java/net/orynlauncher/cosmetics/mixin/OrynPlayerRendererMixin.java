package net.orynlauncher.cosmetics.mixin;

import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import net.orynlauncher.cosmetics.OrynRuntimeProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class OrynPlayerRendererMixin {
    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void oryn$rendererState(PlayerLikeEntity player, PlayerEntityRenderState state,
                                    float tickProgress, CallbackInfo ci) {
        OrynRuntimeProfile runtime = OrynRuntimeProfile.load();
        if (runtime != null && runtime.matches(player)) OrynRuntimeProfile.logRenderer(state);
    }
}
