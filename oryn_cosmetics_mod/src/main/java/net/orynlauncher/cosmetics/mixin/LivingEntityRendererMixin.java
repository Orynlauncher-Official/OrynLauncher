package net.orynlauncher.cosmetics.mixin;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.entity.player.SkinTextures;
import net.orynlauncher.cosmetics.OrynCosmeticsRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void oryn$finalize(LivingEntityRenderState state, MatrixStack matrices,
            OrderedRenderCommandQueue queue, CameraRenderState camera, CallbackInfo ci) {
        if (!(state instanceof PlayerEntityRenderState playerState)) return;
        if (!OrynCosmeticsRuntime.enabled()) return;
        SkinTextures textures = OrynCosmeticsRuntime.getOrCreateTextures();
        if (textures == null) return;
        playerState.skinTextures = textures;
        OrynCosmeticsRuntime.logRendererResult(textures);
    }
}
