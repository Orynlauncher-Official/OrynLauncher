package net.orynlauncher.cosmetics.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.client.texture.PlayerSkinProvider;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;
import net.orynlauncher.cosmetics.OrynRuntimeProfile;
import net.orynlauncher.cosmetics.OrynRuntimeTextureServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 1.21.11 runtime skin bridge.
 *
 * Vanilla PlayerRenderer remains completely untouched. Minecraft's own
 * PlayerSkinTextureDownloader still downloads/decodes/registers the PNG.
 * Any Oryn failure immediately returns the normal vanilla skin.
 *
 * Cape is intentionally not handled in this phase.
 */
@Mixin(PlayerSkinProvider.class)
public abstract class OrynPlayerSkinProviderMixin {

    @Inject(method = "supplySkinTextures", at = @At("HEAD"), cancellable = true)
    private void oryn$customSkin(GameProfile profile, boolean requireSecure,
                                 CallbackInfoReturnable<Supplier<SkinTextures>> cir) {
        OrynRuntimeProfile runtime = OrynRuntimeProfile.load();

        if (runtime == null || !runtime.skinEnabled || !runtime.matches(profile)) {
            return;
        }

        System.out.println("[ORYN-COSMETICS] Skin override candidate"
                + " account=" + profile.name()
                + " uuid=" + profile.id()
                + " model=" + runtime.model
                + " path=" + runtime.skinPath
                + " requireSecure=" + requireSecure);

        cir.setReturnValue(() -> {
            try {
                File skin = new File(runtime.skinPath);
                if (!skin.isFile() || !skin.canRead()) {
                    System.out.println("[ORYN-COSMETICS] Custom skin file unavailable; vanilla fallback.");
                    return DefaultSkinHelper.getSkinTextures(profile);
                }

                String url = OrynRuntimeTextureServer.startSkin(skin);
                if (url == null) {
                    System.out.println("[ORYN-COSMETICS] Custom skin transport unavailable; vanilla fallback.");
                    return DefaultSkinHelper.getSkinTextures(profile);
                }

                /*
                 * The launcher process exits after Minecraft starts, so the
                 * previous launcher-side HTTP server was not a valid runtime
                 * source. This property points at a server living inside the
                 * Minecraft JVM instead.
                 *
                 * PNG decode, texture registration, UV mapping and rendering
                 * remain entirely inside Minecraft's vanilla pipeline.
                 */
                String payload = buildSkinPayload(profile, url, runtime.model);
                profile.properties().removeAll("textures");
                profile.properties().put("textures", new Property("textures", payload));

                System.out.println("[ORYN-COSMETICS] Custom skin property installed"
                        + " transport=runtime-http"
                        + " model=" + runtime.model);

                PlayerSkinProvider provider = (PlayerSkinProvider) (Object) this;
                CompletableFuture<Optional<SkinTextures>> future =
                        provider.fetchSkinTextures(profile);
                Optional<SkinTextures> loaded =
                        future == null ? Optional.empty() : future.join();

                if (loaded.isPresent()) {
                    SkinTextures custom = loaded.get();
                    if (custom.body() != null) {
                        System.out.println("[ORYN-COSMETICS] Vanilla skin provider loaded custom skin"
                                + " body=" + custom.body().texturePath()
                                + " model=" + custom.model()
                                + " secure=" + custom.secure());
                        return custom;
                    }
                }

                System.out.println("[ORYN-COSMETICS] Vanilla provider returned no custom skin; vanilla fallback.");
                return DefaultSkinHelper.getSkinTextures(profile);
            } catch (Throwable t) {
                System.out.println("[ORYN-COSMETICS] Custom skin failed; vanilla fallback: " + t);
                return DefaultSkinHelper.getSkinTextures(profile);
            }
        });
    }

    @Inject(method = "fetchSkinTextures", at = @At("RETURN"))
    private void oryn$diagnostic(GameProfile profile,
                                 CallbackInfoReturnable<CompletableFuture<Optional<SkinTextures>>> cir) {
        OrynRuntimeProfile runtime = OrynRuntimeProfile.load();
        if (runtime == null || !runtime.skinEnabled || !runtime.matches(profile)) return;

        CompletableFuture<Optional<SkinTextures>> future = cir.getReturnValue();
        if (future == null) return;

        future.thenAccept(optional -> {
            SkinTextures textures = optional.orElse(null);
            System.out.println("[ORYN-COSMETICS] vanilla fetch result: skin="
                    + (textures == null || textures.body() == null
                    ? "null" : textures.body().texturePath())
                    + " model=" + (textures == null ? "null" : textures.model()));
        });
    }

    private static String buildSkinPayload(GameProfile profile, String url, String model) {
        String id = profile.id() == null
                ? "00000000-0000-0000-0000-000000000000"
                : profile.id().toString();
        String name = profile.name() == null ? "Player" : profile.name();
        String normalizedModel = "slim".equalsIgnoreCase(model) ? "slim" : "classic";

        String json = "{"
                + "\"timestamp\":" + System.currentTimeMillis() + ","
                + "\"profileId\":\"" + escape(id) + "\","
                + "\"profileName\":\"" + escape(name) + "\","
                + "\"textures\":{"
                + "\"SKIN\":{"
                + "\"url\":\"" + escape(url) + "\","
                + "\"metadata\":{\"model\":\"" + normalizedModel + "\"}"
                + "}"
                + "}"
                + "}";

        return Base64.getEncoder().encodeToString(
                json.getBytes(StandardCharsets.UTF_8));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
