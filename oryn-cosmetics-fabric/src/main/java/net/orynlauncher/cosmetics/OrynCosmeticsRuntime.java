package net.orynlauncher.cosmetics;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.texture.PlayerSkinTextureDownloader;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;

import java.io.File;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class OrynCosmeticsRuntime {
    private enum State { UNINITIALIZED, LOADING, READY, FAILED }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Oryn-Cosmetics-Loader");
        t.setDaemon(true);
        return t;
    });

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
    private static volatile OrynRuntimeProfile RUNTIME_PROFILE;

    private OrynCosmeticsRuntime() {}

    public static SkinTextures getOrStart(GameProfile profile, PlayerSkinTextureDownloader downloader) {
        SkinTextures vanilla = DefaultSkinHelper.getSkinTextures(profile);
        if (profile == null || downloader == null) return vanilla;

        final String identity = profile.id() == null
                ? "name:" + profile.name()
                : profile.id().toString();

        Entry entry = CACHE.computeIfAbsent(identity, ignored -> new Entry());
        State state = entry.state;

        if (state == State.READY && entry.textures != null) {
            System.out.println("[ORYN-COSMETICS] cache HIT texture=" + entry.textureIdentifier);
            return entry.textures;
        }

        if (state == State.LOADING || state == State.FAILED) {
            return vanilla;
        }

        synchronized (entry) {
            if (entry.state != State.UNINITIALIZED) return entry.textures != null ? entry.textures : vanilla;
            entry.state = State.LOADING;
            OrynCosmeticsDebugCounters.recordApplySkin();
            System.out.println("[ORYN-COSMETICS] applySkin() initialized once account=" + identity
                    + " count=" + OrynCosmeticsDebugCounters.applySkinCount());
            System.out.println("[ORYN-COSMETICS] async cosmetic initialization started account=" + identity);
        }

        EXECUTOR.execute(() -> initialize(entry, profile, downloader, identity));
        return vanilla;
    }

    private static void initialize(Entry entry, GameProfile profile,
                                   PlayerSkinTextureDownloader downloader, String identity) {
        try {
            OrynRuntimeProfile runtime = RUNTIME_PROFILE;
            if (runtime == null) {
                runtime = OrynRuntimeProfile.load();
                RUNTIME_PROFILE = runtime;
            }
            if (runtime == null) {
                fail(entry, "runtime profile missing");
                return;
            }
            if (!runtime.skinEnabled) {
                fail(entry, "skin disabled");
                return;
            }
            if (!runtime.matches(profile)) {
                fail(entry, "runtime profile does not match local player");
                return;
            }

            File skin = runtime.resolveSkinFile();
            if (skin == null || !skin.isFile() || !skin.canRead()) {
                fail(entry, "PNG missing/unreadable");
                return;
            }

            long modified = skin.lastModified();
            String cacheKey = identity + "|" + skin.getAbsolutePath() + "|" + modified
                    + "|" + runtime.model + "|" + runtime.skinEnabled;
            entry.cacheKey = cacheKey;

            System.out.println("[ORYN-COSMETICS] cache MISS account=" + identity);
            System.out.println("[ORYN-COSMETICS] PNG exists = true bytes=" + skin.length()
                    + " modified=" + modified);

            final String resolvedModel = runtime.model;

            String url = OrynRuntimeTextureServer.startSkin(skin);
            if (url == null) {
                fail(entry, "runtime texture server unavailable");
                return;
            }

            Identifier textureId = Identifier.of("orynlauncher", "cosmetics/skin/" + safeId(identity));
            System.out.println("[ORYN-COSMETICS] registering skin texture=" + textureId);
            System.out.println("[ORYN-COSMETICS] Native downloader request = 1 (async, not render thread)");

            File cacheDir = new File(new File(System.getProperty("user.dir", ".")),
                    ".oryn/cosmetics/runtime-cache");
            if (!cacheDir.exists() && !cacheDir.mkdirs() && !cacheDir.isDirectory()) {
                fail(entry, "runtime cache directory unavailable");
                return;
            }

            File cacheFile = new File(cacheDir, "skin-" + safeId(identity) + "-" + modified + ".png");

            CompletableFuture<AssetInfo.TextureAsset> future =
                    downloader.downloadAndRegisterTexture(textureId, cacheFile.toPath(), url, false);

            if (future == null) {
                fail(entry, "native downloader returned null future");
                return;
            }

            future.whenComplete((asset, error) -> {
                if (error != null || asset == null) {
                    fail(entry, "native texture pipeline failed: "
                            + (error == null ? "null texture asset" : error));
                    return;
                }

                try {
                    Map<Identifier, net.minecraft.client.texture.AbstractTexture> textures =
                            ((net.orynlauncher.cosmetics.mixin.OrynTextureManagerAccessor) (Object)
                                    ((net.orynlauncher.cosmetics.mixin.OrynPlayerSkinTextureDownloaderAccessor) (Object) downloader)
                                            .oryn$getTextureManager()).oryn$getTextures();

                    boolean lookup = textures.containsKey(asset.texturePath()) || textures.containsKey(textureId);
                    if (!lookup) {
                        fail(entry, "TextureManager lookup failed after registration");
                        return;
                    }

                    PlayerSkinType model = "slim".equalsIgnoreCase(resolvedModel)
                            ? PlayerSkinType.SLIM : PlayerSkinType.WIDE;
                    SkinTextures result = new SkinTextures(asset, null, null, model, false);

                    synchronized (entry) {
                        entry.textures = result;
                        entry.textureIdentifier = asset.texturePath().toString();
                        entry.state = State.READY;
                    }

                    System.out.println("[ORYN-COSMETICS] Texture registered = true");
                    System.out.println("[ORYN-COSMETICS] TextureManager lookup = true");
                    System.out.println("[ORYN-COSMETICS] SkinTextures cached = true model=" + model);
                    System.out.println("[ORYN-COSMETICS] skinTextureRegistrations = "
                            + OrynCosmeticsDebugCounters.textureRegistrations());
                } catch (Throwable t) {
                    fail(entry, "post-registration cache failure: " + t);
                }
            });
        } catch (Throwable t) {
            fail(entry, "async cosmetic initialization failed: " + t);
        }
    }

    private static void fail(Entry entry, String reason) {
        synchronized (entry) {
            entry.state = State.FAILED;
        }
        System.out.println("[ORYN-COSMETICS] Cosmetic initialization failed: " + reason);
        System.out.println("[ORYN-COSMETICS] Vanilla fallback = true");
    }

    public static void clear() {
        CACHE.clear();
        RUNTIME_PROFILE = null;
    }

    private static String safeId(String value) {
        return value.replace("-", "").replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static final class AssetPair {
        final AssetInfo.TextureAsset skin;
        final AssetInfo.TextureAsset cape;

        AssetPair(AssetInfo.TextureAsset skin, AssetInfo.TextureAsset cape) {
            this.skin = skin;
            this.cape = cape;
        }
    }

    private static final class Entry {
        volatile State state = State.UNINITIALIZED;
        volatile SkinTextures textures;
        volatile String textureIdentifier;
        volatile String cacheKey;
    }
}
