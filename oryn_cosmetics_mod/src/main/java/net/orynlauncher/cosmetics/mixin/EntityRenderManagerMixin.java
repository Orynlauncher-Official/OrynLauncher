package net.orynlauncher.cosmetics.mixin;

import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.orynlauncher.cosmetics.OrynCosmeticsRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderManager.class)
public abstract class EntityRenderManagerMixin {
    @Inject(method = "getPlayerRenderer", at = @At("HEAD"))
    private void oryn$diagnostic(AbstractClientPlayerEntity player,
            CallbackInfoReturnable<PlayerEntityRenderer<AbstractClientPlayerEntity>> cir) {
        if (OrynCosmeticsRuntime.enabled()) {
            System.out.println("[ORYN-COSMETICS] PlayerRenderer selected; custom model applied in render state");
        }
    }
}
