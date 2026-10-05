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
        if (profile == null || downloader == null) return DefaultSkinHelper.getSkinTextures(profile);
        SkinTextures vanilla = DefaultSkinHelper.getSkinTextures(profile); return vanilla;

        final String identity = profile.id() == null
                ? "name:" + profile.name()
                : profile.id().toString();

        Entry entry = CACHE.computeIfAbsent(identity, ignored -> new Entry());
        State state = entry.state;

        if (state == State.READY && entry.textures != null) {
            return entry.textures;
        }

        if (state == State.LOADING || state == State.FAILED) {
            return entry.textures != null ? entry.textures : vanilla;
        }

        synchronized (entry) {
            if (entry.state != State.UNINITIALIZED) {
                return entry.textures != null ? entry.textures : vanilla;
            }
            entry.state = State.LOADING;
            OrynCosmeticsDebugCounters.recordApplySkin();
            System.out.println("[ORYN-COSMETICS] cosmetic initialization started once account=" + identity);
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
            if (!runtime.matches(profile)) {
                fail(entry, "runtime profile does not match local player");
                return;
            }

            final OrynRuntimeProfile selectedRuntime = runtime;
            final boolean useSkin = selectedRuntime.skinEnabled;
            final boolean useCape = selectedRuntime.capeEnabled;

            if (!useSkin && !useCape) {
                fail(entry, "skin and cape disabled");
                return;
            }

            File skin = useSkin ? selectedRuntime.resolveSkinFile() : null;
            File cape = useCape ? selectedRuntime.resolveCapeFile() : null;

            if (useSkin && (skin == null || !skin.isFile() || !skin.canRead())) {
                fail(entry, "custom skin PNG missing/unreadable");
                return;
            }
            if (useCape && (cape == null || !cape.isFile() || !cape.canRead())) {
                fail(entry, "custom cape PNG missing/unreadable");
                return;
            }

            long skinModified = skin == null ? 0L : skin.lastModified();
            long capeModified = cape == null ? 0L : cape.lastModified();

            String cacheKey = identity
                    + "|skin=" + (skin == null ? "" : skin.getAbsolutePath() + ":" + skinModified)
                    + "|cape=" + (cape == null ? "" : cape.getAbsolutePath() + ":" + capeModified)
                    + "|model=" + selectedRuntime.model
                    + "|skinEnabled=" + useSkin
                    + "|capeEnabled=" + useCape;
            entry.cacheKey = cacheKey;

            String skinUrl = useSkin ? OrynRuntimeTextureServer.startSkin(skin) : null;
            String capeUrl = useCape ? OrynRuntimeTextureServer.startCape(cape) : null;

            if (useSkin && skinUrl == null) {
                fail(entry, "runtime skin texture server unavailable");
                return;
            }
            if (useCape && capeUrl == null) {
                fail(entry, "runtime cape texture server unavailable");
                return;
            }

            File cacheDir = new File(new File(System.getProperty("user.dir", ".")),
                    ".oryn/cosmetics/runtime-cache");
            if (!cacheDir.exists() && !cacheDir.mkdirs() && !cacheDir.isDirectory()) {
                fail(entry, "runtime cache directory unavailable");
                return;
            }

            CompletableFuture<AssetInfo.TextureAsset> skinFuture = useSkin
                    ? loadTexture(downloader, identity, "skin", skin, skinModified, skinUrl, cacheDir)
                    : CompletableFuture.completedFuture(null);

            CompletableFuture<AssetInfo.TextureAsset> capeFuture = useCape
                    ? loadTexture(downloader, identity, "cape", cape, capeModified, capeUrl, cacheDir)
                    : CompletableFuture.completedFuture(null);

            skinFuture.thenCombine(capeFuture, (skinAsset, capeAsset) -> {
                if (useSkin && skinAsset == null) {
                    throw new IllegalStateException("skin texture asset is null");
                }
                if (useCape && capeAsset == null) {
                    throw new IllegalStateException("cape texture asset is null");
                }

                PlayerSkinType model = "slim".equalsIgnoreCase(selectedRuntime.model)
                        ? PlayerSkinType.SLIM : PlayerSkinType.WIDE;

                // Preserve the working Oryn body texture exactly. Only the cape
                // component is added/replaced here.
                SkinTextures result;
                if (useSkin) {
                    result = new SkinTextures(
                            skinAsset,
                            capeAsset,
                            null,
                            model,
                            false
                    );
                } else {
                    SkinTextures vanillaTextures = DefaultSkinHelper.getSkinTextures(profile);
                    result = new SkinTextures(
                            vanillaTextures.body(),
                            capeAsset,
                            vanillaTextures.elytra(),
                            vanillaTextures.model(),
                            vanillaTextures.secure()
                    );
                }
                return result;
            }).whenComplete((result, error) -> {
                if (error != null || result == null) {
                    fail(entry, "native skin/cape texture pipeline failed: "
                            + (error == null ? "null SkinTextures" : error));
                    return;
                }

                synchronized (entry) {
                    entry.textures = result;
                    entry.skinTextureIdentifier = result.body() == null
                            ? "null" : result.body().texturePath().toString();
                    entry.capeTextureIdentifier = result.cape() == null
                            ? "null" : result.cape().texturePath().toString();
                    entry.state = State.READY;
                }

                printCapeDiagnostic(profile, selectedRuntime, cape, result, identity);
            });
        } catch (Throwable t) {
            fail(entry, "async cosmetic initialization failed: " + t);
        }
    }

    private static CompletableFuture<AssetInfo.TextureAsset> loadTexture(
            PlayerSkinTextureDownloader downloader,
            String identity,
            String type,
            File source,
            long modified,
            String url,
            File cacheDir) {

        Identifier id = Identifier.of(
                "orynlauncher",
                "cosmetics/" + type + "/" + safeId(identity)
        );

        File cacheFile = new File(
                cacheDir,
                type + "-" + safeId(identity) + "-" + modified + ".png"
        );

        CompletableFuture<AssetInfo.TextureAsset> future =
                downloader.downloadAndRegisterTexture(
                        id,
                        cacheFile.toPath(),
                        url,
                        false
                );

        if (future == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException(type + " downloader returned null future"));
        }

        return future.thenApply(asset -> {
            if (asset == null) {
                throw new IllegalStateException(type + " texture asset is null");
            }

            try {
                Map<Identifier, net.minecraft.client.texture.AbstractTexture> textures =
                        ((net.orynlauncher.cosmetics.mixin.OrynTextureManagerAccessor) (Object)
                                ((net.orynlauncher.cosmetics.mixin.OrynPlayerSkinTextureDownloaderAccessor) (Object)
                                        downloader).oryn$getTextureManager()).oryn$getTextures();

                boolean registered = textures.containsKey(asset.texturePath())
                        || textures.containsKey(id);

                if (!registered) {
                    throw new IllegalStateException(type + " TextureManager lookup failed after registration");
                }

                System.out.println("[ORYN-COSMETICS] " + type
                        + " texture registered once=" + asset.texturePath());
                return asset;
            } catch (Throwable t) {
                throw new IllegalStateException(type + " post-registration verification failed", t);
            }
        });
    }

    private static void printCapeDiagnostic(GameProfile profile,
                                            OrynRuntimeProfile runtime,
                                            File cape,
                                            SkinTextures textures,
                                            String identity) {
        String capeId = textures.cape() == null ? "null" : textures.cape().texturePath().toString();

        System.out.println("[ORYN-CAPE-DEBUG]");
        System.out.println("enabled=" + runtime.capeEnabled);
        System.out.println("selected=" + (runtime.capePath.isEmpty() ? "" : runtime.capePath));
        System.out.println("file=" + (cape == null ? "" : cape.getAbsolutePath()));
        System.out.println("exists=" + (cape != null && cape.isFile()));
        System.out.println("fileSize=" + (cape != null && cape.isFile() ? cape.length() : 0));
        System.out.println("textureIdentifier=" + capeId);
        System.out.println("skinTexturesCape=" + capeId);
        System.out.println("player=" + profile.name() + " (" + identity + ")");
        System.out.println("renderer=PlayerEntityRenderer -> CapeFeatureRenderer (native)");
        System.out.println("capeRenderPath=SkinTextures.cape -> PlayerEntityRenderState.capeVisible -> CapeFeatureRenderer");
        System.out.println("[/ORYN-CAPE-DEBUG]");

        System.out.println("[ORYN-CAPE] APPLIED");
        System.out.println("skin=" + (textures.body() == null ? "null" : textures.body().texturePath()));
        System.out.println("cape=" + capeId);
        System.out.println("player=" + profile.name());
        System.out.println("renderer=PlayerEntityRenderer/CapeFeatureRenderer");
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

    private static final class Entry {
        volatile State state = State.UNINITIALIZED;
        volatile SkinTextures textures;
        volatile String skinTextureIdentifier;
        volatile String capeTextureIdentifier;
        volatile String cacheKey;
    }
}
