package net.orynlauncher.cosmetics.mixin;

import net.minecraft.client.render.entity.EntityRenderManager;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Intentionally empty.
 *
 * Minecraft selects the PlayerEntityRenderer from the SkinTextures model supplied
 * by PlayerSkinProvider. Calling TextureManager/registerTexture from
 * getPlayerRenderer was causing unnecessary repeated texture registrations and
 * could run on the wrong thread. Model selection therefore stays in vanilla.
 */
@Mixin(EntityRenderManager.class)
public abstract class OrynEntityRenderManagerMixin {
}
