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

public final class OrynRuntimeProfile {
    private static final Logger LOGGER = LoggerFactory.getLogger("OrynCosmetics");
    private static final Identifier SKIN_ID = Identifier.of("orynlauncher", "cosmetics/skin");
    private static final Identifier CAPE_ID = Identifier.of("orynlauncher", "cosmetics/cape");
    private static OrynRuntimeProfile cached;
    private static long cachedModified = Long.MIN_VALUE;
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
            File file = new File(new File(System.getProperty("user.dir", ".")), ".oryn/cosmetics/active_profile.json");
            if (!file.isFile()) return null;
            long modified = file.lastModified();
            if (cached != null && cachedModified == modified) return cached;
            String json;
            try (FileInputStream in = new FileInputStream(file)) {
                json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            cached = new OrynRuntimeProfile(
                    get(o, "accountUuid"), get(o, "accountName"), get(o, "skinPath"), get(o, "capePath"),
                    get(o, "model", "classic"), getBool(o, "skinEnabled"), getBool(o, "capeEnabled"));
            cachedModified = modified;
            LOGGER.info("[ORYN-COSMETICS] Account name: {} UUID: {} Skin enabled: {} model: {} Skin source: {} Cape enabled: {} Cape source: {}",
                    cached.accountName, cached.accountUuid, cached.skinEnabled, cached.model,
                    cached.skinPath, cached.capeEnabled, cached.capePath);
            return cached;
        } catch (Throwable t) {
            LOGGER.warn("[ORYN-COSMETICS] Failed to load runtime cosmetics profile", t);
            return null;
        }
    }

    private static String get(JsonObject o, String key) { return o.has(key) ? o.get(key).getAsString() : ""; }
    private static String get(JsonObject o, String key, String fallback) { return o.has(key) ? o.get(key).getAsString() : fallback; }
    private static boolean getBool(JsonObject o, String key) { return o.has(key) && o.get(key).getAsBoolean(); }

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
            MinecraftClient client = MinecraftClient.getInstance();
            TextureAssetInfo body = skinEnabled && valid(skinPath) ? register(client, SKIN_ID, skinPath) : null;
            TextureAssetInfo cape = capeEnabled && valid(capePath) ? register(client, CAPE_ID, capePath) : null;
            if (body == null) return null;
            PlayerSkinType type = "slim".equalsIgnoreCase(model) ? PlayerSkinType.SLIM : PlayerSkinType.WIDE;
            SkinTextures result = SkinTextures.create(body, cape, null, type);
            LOGGER.info("[ORYN-COSMETICS] PlayerSkinProvider result: skin texture={} cape texture={} model={}",
                    body.texturePath(), cape == null ? "null" : cape.texturePath(), type);
            return result;
        } catch (Throwable t) {
            LOGGER.warn("[ORYN-COSMETICS] Failed to create/register custom SkinTextures", t);
            return null;
        }
    }

    private TextureAssetInfo register(MinecraftClient client, Identifier id, String path) throws Exception {
        NativeImage image;
        try (InputStream in = new FileInputStream(path)) {
            image = NativeImage.read(in);
        }
        if (image.getWidth() <= 0 || image.getHeight() <= 0) {
            image.close();
            throw new IllegalArgumentException("Invalid image dimensions");
        }
        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> id.toString(), image);
        client.getTextureManager().registerTexture(id, texture);
        texture.upload();
        LOGGER.info("[ORYN-COSMETICS] TextureManager registered {} from {} ({}x{})", id, path, image.getWidth(), image.getHeight());
        return new TextureAssetInfo(id);
    }

    public SkinTextures withCape(SkinTextures base) {
        try {
            TextureAssetInfo cape = capeEnabled && valid(capePath) ? register(MinecraftClient.getInstance(), CAPE_ID, capePath) : null;
            if (cape == null) return base;
            SkinTextures result = new SkinTextures(base.body(), cape, base.elytra(), base.model(), false);
            LOGGER.info("[ORYN-COSMETICS] Cape merged into existing skin: skin texture={} cape texture={} model={}",
                    result.body().texturePath(), result.cape().texturePath(), result.model());
            return result;
        } catch (Throwable t) {
            LOGGER.warn("[ORYN-COSMETICS] Failed to merge custom cape", t);
            return base;
        }
    }

    private static boolean valid(String path) { return path != null && !path.isEmpty() && new File(path).isFile(); }

    public static void logRenderer(net.minecraft.client.render.entity.state.PlayerEntityRenderState state) {
        if (state == null || state.skinTextures == null) return;
        SkinTextures t = state.skinTextures;
        String line = String.valueOf(t.body().texturePath()) + "|" +
                String.valueOf(t.cape() == null ? null : t.cape().texturePath()) + "|" + t.model();
        if (!line.equals(lastRendererLog)) {
            lastRendererLog = line;
            LOGGER.info("[ORYN-COSMETICS] PlayerRenderer final skin texture={} cape texture={} model={} capeVisible={}",
                    t.body().texturePath(), t.cape() == null ? "null" : t.cape().texturePath(), t.model(), state.capeVisible);
        }
    }
}
