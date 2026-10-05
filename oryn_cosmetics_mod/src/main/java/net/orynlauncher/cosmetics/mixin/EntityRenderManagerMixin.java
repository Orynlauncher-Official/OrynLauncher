package net.orynlauncher.cosmetics.mixin;

import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.orynlauncher.cosmetics.OrynCosmeticsRuntime;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(EntityRenderManager.class)
public abstract class EntityRenderManagerMixin {
    @Shadow @Final
    private Map<PlayerSkinType, PlayerEntityRenderer<AbstractClientPlayerEntity>> playerRenderers;

    @Inject(method = "getPlayerRenderer", at = @At("HEAD"), cancellable = true)
    private void oryn$selectModel(AbstractClientPlayerEntity player,
            CallbackInfoReturnable<PlayerEntityRenderer<AbstractClientPlayerEntity>> cir) {
        if (!OrynCosmeticsRuntime.enabled()) return;
        SkinTextures textures = OrynCosmeticsRuntime.getOrCreateTextures();
        if (textures == null) return;
        PlayerEntityRenderer<AbstractClientPlayerEntity> renderer = playerRenderers.get(textures.model());
        if (renderer != null) {
            System.out.println("[ORYN-COSMETICS] PlayerRenderer selected for model=" + textures.model());
            cir.setReturnValue(renderer);
        }
    }
}
