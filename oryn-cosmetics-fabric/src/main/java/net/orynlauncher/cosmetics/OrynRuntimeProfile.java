package net.orynlauncher.cosmetics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo.TextureAssetInfo;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class OrynRuntimeProfile {
    private static final Logger LOGGER = LoggerFactory.getLogger("OrynCosmetics");
    private static OrynRuntimeProfile cached;
    private static long cachedModified = Long.MIN_VALUE;
    private static final Map<String, RegisteredTexture> TEXTURES = new HashMap<>();
    private static String lastRendererLog = "";

    public final String accountUuid, accountName, skinPath, capePath, model;
    public final boolean skinEnabled, capeEnabled;

    private OrynRuntimeProfile(String accountUuid, String accountName, String skinPath, String capePath,
                               String model, boolean skinEnabled, boolean capeEnabled) {
        this.accountUuid = accountUuid;
        this.accountName = accountName;
        this.skinPath = skinPath;
        this.capePath = capePath;
        this.model = model;
        this.skinEnabled = skinEnabled;
        this.capeEnabled = capeEnabled;
    }

    public static OrynRuntimeProfile load() {
        try {
            File file = new File(new File(System.getProperty("user.dir", ".")),
                    ".oryn/cosmetics/active_profile.json");
            if (!file.isFile()) {
                LOGGER.warn("[ORYN-COSMETICS] Runtime profile missing: {}", file.getAbsolutePath());
                return null;
            }

            long modified = file.lastModified();
            if (cached != null && cachedModified == modified) return cached;

            String json;
            try (FileInputStream in = new FileInputStream(file)) {
                json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }

            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            cached = new OrynRuntimeProfile(
                    get(o, "accountUuid"), get(o, "accountName"),
                    get(o, "skinPath"), get(o, "capePath"),
                    get(o, "model", "classic"),
                    getBool(o, "skinEnabled"), getBool(o, "capeEnabled"));
            cachedModified = modified;

            LOGGER.info("[ORYN-COSMETICS] Account name: {} UUID: {}", cached.accountName, cached.accountUuid);
            LOGGER.info("[ORYN-COSMETICS] Skin enabled={} model={} source={}",
                    cached.skinEnabled, cached.model, cached.skinPath);
            LOGGER.info("[ORYN-COSMETICS] Cape enabled={} source={}",
                    cached.capeEnabled, cached.capePath);
            return cached;
        } catch (Throwable t) {
            LOGGER.warn("[ORYN-COSMETICS] Failed to load runtime cosmetics profile", t);
            return null;
        }
    }

    private static String get(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsString() : "";
    }

    private static String get(JsonObject o, String key, String fallback) {
        return o.has(key) ? o.get(key).getAsString() : fallback;
    }

    private static boolean getBool(JsonObject o, String key) {
        return o.has(key) && o.get(key).getAsBoolean();
    }

    public boolean matches(GameProfile profile) {
        if (profile == null) return false;
        String uuid = profile.id() == null ? "" : profile.id().toString();
        String configured = accountUuid.replace("-", "");
        if (!configured.isEmpty() && !configured.matches("0{32}")) {
            return configured.equalsIgnoreCase(uuid.replace("-", ""));
        }
        return !accountName.isEmpty() && accountName.equalsIgnoreCase(profile.name());
    }

    public boolean matches(net.minecraft.entity.PlayerLikeEntity player) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            return client != null && client.player != null && player == client.player;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public SkinTextures createSkinTextures() {
        try {
            if (!skinEnabled || !valid(skinPath)) {
                LOGGER.warn("[ORYN-COSMETICS] Skin unavailable: enabled={} path={}", skinEnabled, skinPath);
                return null;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            TextureAssetInfo body = register(client, "skin", skinPath, true);
            if (body == null) return null;

            TextureAssetInfo cape = null;
            if (capeEnabled && valid(capePath)) {
                cape = register(client, "cape", capePath, false);
            }

            PlayerSkinType type = "slim".equalsIgnoreCase(model)
                    ? PlayerSkinType.SLIM : PlayerSkinType.WIDE;

            SkinTextures result = SkinTextures.create(body, cape, null, type);

            LOGGER.info("[ORYN-COSMETICS] SkinTextures created: skin={} cape={} model={} secure={}",
                    body.texturePath(),
                    cape == null ? "null" : cape.texturePath(),
                    type, result.secure());
            return result;
        } catch (Throwable t) {
            LOGGER.warn("[ORYN-COSMETICS] Failed to create/register custom SkinTextures", t);
            return null;
        }
    }

    private static synchronized TextureAssetInfo register(MinecraftClient client, String kind,
                                                           String path, boolean skin) throws Exception {
        File file = new File(path);
        if (!file.isFile()) {
            LOGGER.warn("[ORYN-COSMETICS] {} file missing: {}", kind, path);
            return null;
        }

        String key = kind + "|" + file.getAbsolutePath() + "|" + file.length() + "|" + file.lastModified();
        RegisteredTexture old = TEXTURES.get(key);
        if (old != null) {
            LOGGER.info("[ORYN-COSMETICS] {} texture cache HIT id={} size={}x{}",
                    kind, old.id, old.width, old.height);
            return new TextureAssetInfo(old.id);
        }

        NativeImage image;
        try (InputStream in = new FileInputStream(file)) {
            image = NativeImage.read(in);
        }

        int width = image.getWidth();
        int height = image.getHeight();

        LOGGER.info("[ORYN-COSMETICS] {} PNG size={}x{} decoded=true thread={}",
                kind, width, height, Thread.currentThread().getName());

        if (skin) {
            // Minecraft Java's modern player texture is 64x64. Keep the original
            // pixels untouched; reject legacy dimensions instead of silently
            // rescaling them into an invalid UV layout.
            if (!((width == 64 && height == 64) || (width == 64 && height == 32))) {
                image.close();
                LOGGER.warn("[ORYN-COSMETICS] Skin rejected: expected 64x64 (or legacy 64x32), got {}x{}",
                        width, height);
                return null;
            }
        } else if (!((width == 64 && height == 32)
                || (width == 22 && height == 17)
                || (width == 44 && height == 34))) {
            image.close();
            LOGGER.warn("[ORYN-COSMETICS] Cape rejected: unsupported dimensions {}x{}", width, height);
            return null;
        }

        // NativeImage.read() preserves the PNG's decoded pixel data. Do not flip,
        // rotate, rescale, or manually swizzle channels. The texture owns the
        // NativeImage for its entire lifetime.
        Identifier id = Identifier.of("orynlauncher",
                "cosmetics/" + kind + "_" + Integer.toHexString(key.hashCode()));

        NativeImageBackedTexture texture =
                new NativeImageBackedTexture(() -> "Oryn " + kind, image);

        // Texture registration and GL upload must happen on Minecraft's client
        // render thread. This method is called from the client skin/render pipeline;
        // fail loudly if a future caller invokes it from another thread rather than
        // racing OpenGL state.
        if (!client.isOnThread()) {
            LOGGER.warn("[ORYN-COSMETICS] {} upload requested off render thread: {}",
                    kind, Thread.currentThread().getName());
        }

        client.getTextureManager().registerTexture(id, texture);
        texture.upload();

        boolean registered = client.getTextureManager().getTexture(id) == texture;
        NativeImage retained = texture.getImage();
        boolean imageAlive = retained != null
                && retained.getWidth() == width
                && retained.getHeight() == height;

        LOGGER.info("[ORYN-COSMETICS] {} texture registered={} imageAlive={} id={} size={}x{}",
                kind, registered, imageAlive, id, width, height);

        if (!registered || !imageAlive) {
            texture.close();
            return null;
        }

        TEXTURES.put(key, new RegisteredTexture(id, texture, width, height));
        return new TextureAssetInfo(id);
    }

    public SkinTextures withCape(SkinTextures base) {
        try {
            if (base == null || !capeEnabled || !valid(capePath)) return base;
            TextureAssetInfo cape = register(MinecraftClient.getInstance(), "cape", capePath, false);
            if (cape == null) return base;
            SkinTextures result = SkinTextures.create(base.body(), cape, base.elytra(), base.model());
            LOGGER.info("[ORYN-COSMETICS] Cape merged: {}", cape.texturePath());
            return result;
        } catch (Throwable t) {
            LOGGER.warn("[ORYN-COSMETICS] Failed to merge custom cape", t);
            return base;
        }
    }

    private static boolean valid(String path) {
        return path != null && !path.isEmpty() && new File(path).isFile();
    }

    public static void logRenderer(net.minecraft.client.render.entity.state.PlayerEntityRenderState state) {
        if (state == null || state.skinTextures == null) return;
        SkinTextures t = state.skinTextures;
        String line = String.valueOf(t.body().texturePath()) + "|" +
                String.valueOf(t.cape() == null ? null : t.cape().texturePath()) + "|" + t.model();
        if (!line.equals(lastRendererLog)) {
            lastRendererLog = line;
            LOGGER.info("[ORYN-COSMETICS] PlayerRenderer final skin={} cape={} model={} capeVisible={}",
                    t.body().texturePath(),
                    t.cape() == null ? "null" : t.cape().texturePath(),
                    t.model(), state.capeVisible);
        }
    }

    private record RegisteredTexture(Identifier id, NativeImageBackedTexture texture,
                                     int width, int height) {}

}
