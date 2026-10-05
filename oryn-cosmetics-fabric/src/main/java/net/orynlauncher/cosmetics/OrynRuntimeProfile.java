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
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

public final class OrynRuntimeProfile {
    private static final Logger LOGGER = LoggerFactory.getLogger("OrynCosmetics");
    private static final String TEST_SKIN_PROPERTY = "oryn.cosmetics.testSkin";
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
            if (Boolean.getBoolean(TEST_SKIN_PROPERTY)) {
                LOGGER.warn("[ORYN-COSMETICS] ORYN TEST SKIN mode enabled by -D{}=true", TEST_SKIN_PROPERTY);
            }
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
            if (!skinEnabled && !Boolean.getBoolean(TEST_SKIN_PROPERTY)) {
                LOGGER.warn("[ORYN-COSMETICS] Skin unavailable: enabled={} path={}", skinEnabled, skinPath);
                return null;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) return null;

            TextureAssetInfo body;
            if (Boolean.getBoolean(TEST_SKIN_PROPERTY)) {
                body = registerBundled(client, "skin", "/orynlauncher/cosmetics/test_skin.png");
            } else {
                if (!valid(skinPath)) {
                    LOGGER.warn("[ORYN-COSMETICS] Skin unavailable: enabled={} path={}", skinEnabled, skinPath);
                    return null;
                }
                body = registerFile(client, "skin", skinPath, true);
            }
            if (body == null) return null;

            TextureAssetInfo cape = null;
            if (capeEnabled && valid(capePath)) {
                cape = registerFile(client, "cape", capePath, false);
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

    private static TextureAssetInfo registerFile(MinecraftClient client, String kind,
                                                  String path, boolean skin) throws Exception {
        File file = new File(path);
        if (!file.isFile()) {
            LOGGER.warn("[ORYN-COSMETICS] {} file missing: {}", kind, path);
            return null;
        }

        String digest = sha256(file);
        String account = accountKey();
        String key = kind + "|" + account + "|" + digest;
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

        logDecodedImage(kind, file.getAbsolutePath(), image, digest);

        if (!validDimensions(kind, image.getWidth(), image.getHeight(), skin)) {
            image.close();
            return null;
        }

        return registerDecoded(client, kind, key, image, image.getWidth(), image.getHeight());
    }

    private static TextureAssetInfo registerBundled(MinecraftClient client, String kind, String resource) throws Exception {
        String key = kind + "|" + accountKey() + "|bundled-test";
        RegisteredTexture old = TEXTURES.get(key);
        if (old != null) {
            LOGGER.info("[ORYN-COSMETICS] {} bundled test texture cache HIT id={} size={}x{}",
                    kind, old.id, old.width, old.height);
            return new TextureAssetInfo(old.id);
        }

        InputStream stream = OrynRuntimeProfile.class.getResourceAsStream(resource);
        if (stream == null) {
            LOGGER.warn("[ORYN-COSMETICS] Bundled test texture missing: {}", resource);
            return null;
        }

        NativeImage image;
        try (InputStream in = stream) {
            image = NativeImage.read(in);
        }

        logDecodedImage(kind + " TEST", resource, image, "bundled");
        if (!validDimensions(kind, image.getWidth(), image.getHeight(), true)) {
            image.close();
            LOGGER.warn("[ORYN-COSMETICS] Bundled test skin has invalid dimensions");
            return null;
        }

        return registerDecoded(client, kind, key, image, image.getWidth(), image.getHeight());
    }

    private static TextureAssetInfo registerDecoded(MinecraftClient client, String kind, String key,
                                                     NativeImage image, int width, int height) throws Exception {
        String idPath = "cosmetics/" + accountKey() + "/" + kind + "_" + keyDigest(key);
        Identifier id = Identifier.of("orynlauncher", idPath);

        LOGGER.info("[ORYN-COSMETICS] {} Identifier before upload={}", kind, id);
        LOGGER.info("[ORYN-COSMETICS] {} upload thread request={} clientThread={} renderThread={}",
                kind, Thread.currentThread().getName(), client.isOnThread(),
                com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread());

        RegistrationHolder holder = new RegistrationHolder();
        Runnable upload = () -> {
            try {
                LOGGER.info("[ORYN-COSMETICS] {} GL registration thread={} renderThread={}",
                        kind, Thread.currentThread().getName(),
                        com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread());

                NativeImageBackedTexture texture =
                        new NativeImageBackedTexture(() -> "Oryn " + kind, image);

                client.getTextureManager().registerTexture(id, texture);
                LOGGER.info("[ORYN-COSMETICS] {} TextureManager.registerTexture id={}", kind, id);

                texture.upload();

                boolean registered = client.getTextureManager().getTexture(id) == texture;
                NativeImage retained = texture.getImage();
                boolean imageAlive = retained != null
                        && retained.getWidth() == width
                        && retained.getHeight() == height;

                LOGGER.info("[ORYN-COSMETICS] {} upload complete registered={} imageAlive={} id={} size={}x{}",
                        kind, registered, imageAlive, id, width, height);

                if (!registered || !imageAlive) {
                    texture.close();
                    return;
                }

                TEXTURES.put(key, new RegisteredTexture(id, texture, width, height));
                holder.texture = texture;
                holder.success = true;
            } catch (Throwable t) {
                LOGGER.warn("[ORYN-COSMETICS] {} GL registration/upload failed id={}", kind, id, t);
                holder.error = t;
            }
        };

        if (client.isOnThread()) {
            upload.run();
        } else {
            LOGGER.info("[ORYN-COSMETICS] {} marshaling registration/upload to Minecraft client thread", kind);
            client.executeSync(upload);
        }

        if (!holder.success) {
            if (holder.error != null) {
                image.close();
                throw new IllegalStateException("Texture registration failed for " + id, holder.error);
            }
            image.close();
            return null;
        }

        LOGGER.info("[ORYN-COSMETICS] {} exact SkinTextures identifier={}", kind, id);
        return new TextureAssetInfo(id);
    }

    private static void logDecodedImage(String kind, String source, NativeImage image, String digest) {
        NativeImage.Format format = image.getFormat();
        LOGGER.info("[ORYN-COSMETICS] Original PNG: source={} width={} height={} format={} hasAlpha={} digest={}",
                source, image.getWidth(), image.getHeight(), format, format.hasAlpha(), digest);
        logPixel(image, kind, 0, 0);
        logPixel(image, kind, 32, 0);
        logPixel(image, kind, 0, 32);
        logPixel(image, kind, 32, 32);
        logPixel(image, kind, image.getWidth() - 1, image.getHeight() - 1);
    }

    private static void logPixel(NativeImage image, String kind, int x, int y) {
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) return;
        LOGGER.info("[ORYN-COSMETICS] {} NativeImage pixel({},{})=0x{}",
                kind, x, y, Integer.toHexString(image.getColorArgb(x, y)));
    }

    private static boolean validDimensions(String kind, int width, int height, boolean skin) {
        if (skin) {
            if (width != 64 || height != 64) {
                LOGGER.warn("[ORYN-COSMETICS] INVALID SKIN DIMENSIONS: {}x{}", width, height);
                return false;
            }
            return true;
        }

        if (width != 64 || height != 32) {
            LOGGER.warn("[ORYN-COSMETICS] INVALID CAPE DIMENSIONS: {}x{} (Oryn 1.21.11 runtime requires 64x32)",
                    width, height);
            return false;
        }
        return true;
    }

    private static String accountKey() {
        String uuid = cached == null ? "" : cached.accountUuid;
        String name = cached == null ? "" : cached.accountName;
        if (uuid != null) {
            String normalized = uuid.replace("-", "").trim();
            if (!normalized.isEmpty() && !normalized.matches("0{32}")) return safeId(normalized);
        }
        return safeId(name == null || name.isEmpty() ? "offline" : name.toLowerCase());
    }

    private static String safeId(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String keyDigest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(24);
            for (int i = 0; i < 12 && i < bytes.length; i++) {
                out.append(String.format("%02x", bytes[i]));
            }
            return out.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        StringBuilder out = new StringBuilder(64);
        for (byte b : digest.digest()) out.append(String.format("%02x", b));
        return out.toString();
    }

    private static boolean valid(String path) {
        return path != null && !path.isEmpty() && new File(path).isFile();
    }

    public SkinTextures withCape(SkinTextures base) {
        try {
            if (base == null || !capeEnabled || !valid(capePath)) return base;
            TextureAssetInfo cape = registerFile(MinecraftClient.getInstance(), "cape", capePath, false);
            if (cape == null) return base;
            SkinTextures result = SkinTextures.create(base.body(), cape, base.elytra(), base.model());
            LOGGER.info("[ORYN-COSMETICS] Cape merged: Identifier={} registered=true", cape.texturePath());
            return result;
        } catch (Throwable t) {
            LOGGER.warn("[ORYN-COSMETICS] Failed to merge custom cape", t);
            return base;
        }
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

    private static final class RegistrationHolder {
        NativeImageBackedTexture texture;
        Throwable error;
        boolean success;
    }

    private record RegisteredTexture(Identifier id, NativeImageBackedTexture texture,
                                     int width, int height) {}
}
