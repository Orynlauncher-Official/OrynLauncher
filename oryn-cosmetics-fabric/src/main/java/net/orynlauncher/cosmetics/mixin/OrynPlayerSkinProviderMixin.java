package net.orynlauncher.cosmetics.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.texture.PlayerSkinProvider;
import net.minecraft.client.texture.PlayerSkinTextureDownloader;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import net.orynlauncher.cosmetics.OrynRuntimeProfile;
import net.orynlauncher.cosmetics.OrynRuntimeTextureServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Map;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;

@Mixin(PlayerSkinProvider.class)
public abstract class OrynPlayerSkinProviderMixin {
    /*
     * The renderer may ask the provider for the same Supplier/SkinTextures many
     * times. The expensive cosmetic operation is therefore explicitly cached
     * by cosmetic state. Rendering is read-only after the first successful
     * initialization.
     */
    private static final Object CACHE_LOCK = new Object();
    private static String cachedStateKey;
    private static SkinTextures cachedSkinTextures;
    private static String cachedTextureIdentifier;
    private static String cachedSkinHash;
    private static long cachedSkinModified = Long.MIN_VALUE;

    private static int applySkinCount;
    private static int pngDecodeCount;
    private static int textureRegistrationCount;
    private static int downloaderRequestCount;

    @Inject(method = "supplySkinTextures", at = @At("HEAD"), cancellable = true)
    private void oryn$applySkin(GameProfile profile, boolean requireSecure,
                                CallbackInfoReturnable<Supplier<SkinTextures>> cir) {
        OrynRuntimeProfile runtime = OrynRuntimeProfile.load();
        if (runtime == null || !runtime.skinEnabled || !runtime.matches(profile)) return;

        final File skin = runtime.resolveSkinFile();
        final String stateKey = buildStateKey(runtime, profile, skin);

        /*
         * Do not perform any filesystem/network/decode/register work here.
         * This hook only returns a read-only supplier over the cached result.
         */
        synchronized (CACHE_LOCK) {
            if (cachedSkinTextures != null && stateKey.equals(cachedStateKey)) {
                cir.setReturnValue(() -> cachedSkinTextures);
                return;
            }
        }

        cir.setReturnValue(() -> getOrCreateSkin(runtime, profile, skin, stateKey));
    }

    private SkinTextures getOrCreateSkin(OrynRuntimeProfile runtime, GameProfile profile,
                                         File skin, String stateKey) {
        synchronized (CACHE_LOCK) {
            if (cachedSkinTextures != null && stateKey.equals(cachedStateKey)) {
                return cachedSkinTextures;
            }

            applySkinCount++;
            System.out.println("[ORYN-COSMETICS] applySkin() initialized = true count=" + applySkinCount);
            System.out.println("[ORYN-COSMETICS] Cosmetic state changed; initializing exactly once.");
            System.out.println("[ORYN-COSMETICS] Renderer hook = NONE (vanilla renderer untouched)");

            if (skin == null) return failLocked(profile, "skin path empty");
            if (!skin.isFile() || !skin.canRead()) {
                return failLocked(profile, "PNG missing/unreadable: " + skin.getAbsolutePath());
            }

            System.out.println("[ORYN-COSMETICS] PNG exists = true path=" + skin.getAbsolutePath()
                    + " bytes=" + skin.length() + " modified=" + skin.lastModified());

            try {
                String hash = sha256(skin);
                cachedSkinHash = hash;
                cachedSkinModified = skin.lastModified();
                System.out.println("[ORYN-COSMETICS] skin hash = " + hash);

                String url = OrynRuntimeTextureServer.startSkin(skin);
                if (url == null) return failLocked(profile, "runtime texture server unavailable");

                PlayerSkinTextureDownloader downloader =
                        ((OrynPlayerSkinProviderAccessor) (Object) this).oryn$getDownloader();

                String profileId = profile.id() == null ? "unknown" : profile.id().toString();
                Identifier textureId = Identifier.of("orynlauncher",
                        "cosmetics/skin/" + safeId(profileId));

                File cacheDir = new File(new File(System.getProperty("user.dir", ".")),
                        ".oryn/cosmetics/runtime-cache");
                cacheDir.mkdirs();
                File cacheFile = new File(cacheDir,
                        "skin-" + safeId(profileId) + "-" + skin.lastModified() + ".png");

                System.out.println("[ORYN-COSMETICS] Texture Identifier = " + textureId);
                System.out.println("[ORYN-COSMETICS] Native downloader = " + downloader.getClass().getName());
                System.out.println("[ORYN-COSMETICS] Native texture pipeline = PlayerSkinTextureDownloader -> TextureManager");

                /*
                 * Hard guard against duplicate registration even if the cache
                 * is invalidated/recreated during the same Minecraft process.
                 * An already-registered Oryn identifier is reused directly.
                 */
                try {
                    Map<Identifier, net.minecraft.client.texture.AbstractTexture> textures =
                            ((OrynTextureManagerAccessor) (Object)
                                    ((OrynPlayerSkinTextureDownloaderAccessor) (Object) downloader)
                                            .oryn$getTextureManager()).oryn$getTextures();
                    if (textures.containsKey(textureId)) {
                        AssetInfo.TextureAsset existing = new AssetInfo.TextureAssetInfo(textureId);
                        PlayerSkinType existingModel = "slim".equalsIgnoreCase(runtime.model)
                                ? PlayerSkinType.SLIM : PlayerSkinType.WIDE;
                        SkinTextures existingTextures =
                                new SkinTextures(existing, null, null, existingModel, false);
                        cachedStateKey = stateKey;
                        cachedSkinTextures = existingTextures;
                        cachedTextureIdentifier = textureId.toString();
                        cachedSkinHash = hash;
                        cachedSkinModified = skin.lastModified();
                        System.out.println("[ORYN-COSMETICS] Texture already registered = true");
                        System.out.println("[ORYN-COSMETICS] Texture registration skipped = true");
                        System.out.println("[ORYN-COSMETICS] Cached existing SkinTextures = true");
                        return existingTextures;
                    }
                } catch (Throwable guardError) {
                    System.out.println("[ORYN-COSMETICS] Existing texture guard failed = " + guardError);
                }

                /*
                 * This is the ONLY downloader invocation for this cosmetic
                 * state. The downloader mixin records its real decode/register
                 * counters; this method never calls it again for a cache hit.
                 */
                downloaderRequestCount++;
                System.out.println("[ORYN-COSMETICS] downloaderRequestCount = " + downloaderRequestCount);

                CompletableFuture<AssetInfo.TextureAsset> future =
                        downloader.downloadAndRegisterTexture(textureId, cacheFile.toPath(), url, false);
                if (future == null) return failLocked(profile, "native downloader returned null future");

                AssetInfo.TextureAsset asset = future.join();
                if (asset == null) return failLocked(profile, "native downloader returned null texture asset");

                System.out.println("[ORYN-COSMETICS] Texture registered = true");
                System.out.println("[ORYN-COSMETICS] Texture path = " + asset.texturePath());

                try {
                    Map<Identifier, net.minecraft.client.texture.AbstractTexture> textures =
                            ((OrynTextureManagerAccessor) (Object)
                                    ((OrynPlayerSkinTextureDownloaderAccessor) (Object) downloader)
                                            .oryn$getTextureManager()).oryn$getTextures();
                    boolean lookup = textures.containsKey(asset.texturePath()) || textures.containsKey(textureId);
                    System.out.println("[ORYN-COSMETICS] TextureManager lookup = " + lookup);
                    System.out.println("[ORYN-COSMETICS] TextureManager entry class = "
                            + (textures.get(asset.texturePath()) == null ? "null"
                            : textures.get(asset.texturePath()).getClass().getName()));
                    if (!lookup) return failLocked(profile, "Identifier lookup failed after registration");
                } catch (Throwable lookupError) {
                    return failLocked(profile, "TextureManager lookup exception: " + lookupError);
                }

                /*
                 * Classic/Steve is Minecraft's WIDE/default model. Slim/Alex
                 * is Minecraft's SLIM model. Do not invert these values.
                 */
                PlayerSkinType skinType = "slim".equalsIgnoreCase(runtime.model)
                        ? PlayerSkinType.SLIM : PlayerSkinType.WIDE;

                SkinTextures result = new SkinTextures(asset, null, null, skinType, false);

                cachedStateKey = stateKey;
                cachedSkinTextures = result;
                cachedTextureIdentifier = asset.texturePath().toString();

                System.out.println("[ORYN-COSMETICS] SkinTextures override = true"
                        + " body=" + asset.texturePath()
                        + " model=" + skinType
                        + " secure=false");
                System.out.println("[ORYN-COSMETICS] Cached SkinTextures = true");
                System.out.println("[ORYN-COSMETICS] applySkinCount = " + applySkinCount);
                System.out.println("[ORYN-COSMETICS] pngDecodeCount = " + pngDecodeCount);
                System.out.println("[ORYN-COSMETICS] textureRegistrationCount = " + textureRegistrationCount);
                System.out.println("[ORYN-COSMETICS] downloaderRequestCount = " + downloaderRequestCount);

                return result;
            } catch (Throwable t) {
                return failLocked(profile, "native texture pipeline exception: " + t);
            }
        }
    }

    private SkinTextures failLocked(GameProfile profile, String reason) {
        System.out.println("[ORYN-COSMETICS] CUSTOM SKIN FAILED");
        System.out.println("[ORYN-COSMETICS] Reason: " + reason);
        System.out.println("[ORYN-COSMETICS] FIRST FAILURE: cosmetic initialization did not produce a cached SkinTextures");
        System.out.println("[ORYN-COSMETICS] Vanilla fallback = true");
        return DefaultSkinHelper.getSkinTextures(profile);
    }

    private static String buildStateKey(OrynRuntimeProfile runtime, GameProfile profile, File skin) {
        String accountIdentity = runtime.accountUuid;
        if (accountIdentity == null || accountIdentity.isEmpty()
                || accountIdentity.replace("-", "").matches("0{32}")) {
            accountIdentity = "offline:" + runtime.accountName;
        }
        String profileIdentity = profile.id() == null
                ? "name:" + profile.name()
                : profile.id().toString();
        String path = skin == null ? "" : skin.getAbsolutePath();
        long modified = skin == null ? -1L : skin.lastModified();
        return accountIdentity + "|" + profileIdentity + "|" + path + "|" + modified
                + "|" + runtime.model + "|" + runtime.skinEnabled;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        return toHex(digest.digest());
    }

    private static String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format("%02x", b & 0xff));
        return out.toString();
    }

    private static String safeId(String value) {
        return value.replace("-", "").replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
