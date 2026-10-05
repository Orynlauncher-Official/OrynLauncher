package net.orynlauncher.cosmetics.mixin;

import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.entity.player.SkinTextures;
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
        if (runtime == null || !runtime.matches(player)) return;

        SkinTextures current = state.skinTextures;

        // PlayerSkinProvider is the authoritative source. This fallback only
        // repairs the render state if another cache path supplied vanilla data.
        if (runtime.skinEnabled) {
            SkinTextures custom = runtime.createSkinTextures();
            if (custom != null) {
                state.skinTextures = custom;
                current = custom;
            }
        } else if (runtime.capeEnabled && current != null) {
            SkinTextures customCape = runtime.withCape(current);
            if (customCape != null) {
                state.skinTextures = customCape;
                current = customCape;
            }
        }

        state.capeVisible = runtime.capeEnabled && current != null && current.cape() != null;

        OrynRuntimeProfile.logRenderer(state);
        System.out.println("[ORYN-COSMETICS] PlayerRenderer final skin="
                + (current == null ? "null" : current.body().texturePath())
                + " cape=" + (current == null || current.cape() == null
                ? "null" : current.cape().texturePath())
                + " model=" + (current == null ? "null" : current.model())
                + " capeVisible=" + state.capeVisible);
    }
}
