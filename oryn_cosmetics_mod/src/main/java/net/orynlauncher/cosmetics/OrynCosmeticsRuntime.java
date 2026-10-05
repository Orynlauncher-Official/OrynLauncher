package net.orynlauncher.cosmetics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public final class OrynCosmeticsRuntime {
    private static final String LOG = "[ORYN-COSMETICS]";
    private static final String PROFILE_PROPERTY = "oryn.cosmetics.profile";
    private static volatile SkinTextures cachedTextures;
    private static volatile String cachedSignature;
    private OrynCosmeticsRuntime() {}

    public static boolean enabled() { return profileFile() != null; }

    public static SkinTextures getOrCreateTextures() {
        SkinTextures existing = cachedTextures;
        String sig = signature();
        if (existing != null && sig.equals(cachedSignature)) return existing;
        Profile p = readProfile();
        if (p == null || !p.skinEnabled || p.skinFile == null) {
            log("No enabled Oryn skin profile available");
            return null;
        }
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            NativeImage skinImage = readImage(p.skinFile);
            if (skinImage == null) { log("Skin decode FAILED: " + p.skinFile); return null; }
            if (skinImage.getWidth() < 64 || skinImage.getWidth() != skinImage.getHeight()
                    || skinImage.getWidth() % 64 != 0) {
                skinImage.close();
                log("Skin dimensions rejected: " + p.skinFile);
                return null;
            }
            Identifier skinId = Identifier.of("oryn_cosmetics", "skins/" + stableName(p.skinFile));
            client.getTextureManager().registerTexture(skinId,
                    new NativeImageBackedTexture(() -> "Oryn skin", skinImage));
            AssetInfo.TextureAssetInfo body = new AssetInfo.TextureAssetInfo(skinId);

            AssetInfo.TextureAssetInfo cape = null;
            if (p.capeEnabled && p.capeFile != null) {
                NativeImage capeImage = readImage(p.capeFile);
                if (capeImage != null && validCape(capeImage)) {
                    Identifier capeId = Identifier.of("oryn_cosmetics", "capes/" + stableName(p.capeFile));
                    client.getTextureManager().registerTexture(capeId,
                            new NativeImageBackedTexture(() -> "Oryn cape", capeImage));
                    cape = new AssetInfo.TextureAssetInfo(capeId);
                    log("Cape uploaded: " + capeId);
                } else {
                    if (capeImage != null) capeImage.close();
                    log("Cape decode/validation FAILED: " + p.capeFile);
                }
            }

            PlayerSkinType model = "slim".equalsIgnoreCase(p.model)
                    ? PlayerSkinType.SLIM : PlayerSkinType.WIDE;
            SkinTextures textures = new SkinTextures(body, cape, null, model, false);
            cachedTextures = textures;
            cachedSignature = sig;
            log("TextureManager: skin registered=" + skinId);
            log("TextureManager: cape registered=" + (cape == null ? "<none>" : cape.id()));
            log("SkinTextures: skin=" + body.id() + " cape="
                    + (cape == null ? "<none>" : cape.id()) + " model=" + model + " secure=false");
            return textures;
        } catch (Throwable t) {
            log("Texture registration FAILED: " + t);
            return null;
        }
    }

    public static void logProviderResult(SkinTextures textures) {
        log("PlayerSkinProvider result: " + (textures == null ? "<null>"
                : "skin=" + textures.body().id() + " cape="
                + (textures.cape() == null ? "<none>" : textures.cape().id())
                + " model=" + textures.model()));
    }

    public static void logRendererResult(SkinTextures textures) {
        log("PlayerRenderer final: " + (textures == null ? "<null>"
                : "skin=" + textures.body().id() + " cape="
                + (textures.cape() == null ? "<none>" : textures.cape().id())
                + " model=" + textures.model()));
    }

    private static Profile readProfile() {
        File file = profileFile();
        if (file == null || !file.isFile()) return null;
        try (InputStream in = new FileInputStream(file)) {
            JsonObject o = JsonParser.parseReader(in).getAsJsonObject();
            Profile p = new Profile();
            p.accountUuid = o.has("accountUuid") ? o.get("accountUuid").getAsString() : "";
            p.name = o.has("name") ? o.get("name").getAsString() : "";
            p.skinEnabled = !o.has("skinEnabled") || o.get("skinEnabled").getAsBoolean();
            p.capeEnabled = !o.has("capeEnabled") || o.get("capeEnabled").getAsBoolean();
            p.model = o.has("model") ? o.get("model").getAsString() : "classic";
            p.skinFile = resolve(o.has("skinPath") ? o.get("skinPath").getAsString() : "");
            p.capeFile = resolve(o.has("capePath") ? o.get("capePath").getAsString() : "");
            log("Account name: " + p.name);
            log("Account UUID: " + p.accountUuid);
            log("Skin enabled: " + p.skinEnabled);
            log("Skin model: " + p.model);
            log("Skin source/path: " + (p.skinFile == null ? "<none>" : p.skinFile));
            log("Cape enabled: " + p.capeEnabled);
            log("Cape source/path: " + (p.capeFile == null ? "<none>" : p.capeFile));
            return p;
        } catch (Throwable t) { log("Profile read FAILED: " + t); return null; }
    }

    private static File profileFile() {
        String value = System.getProperty(PROFILE_PROPERTY, "").trim();
        return value.isEmpty() ? null : new File(value);
    }
    private static File resolve(String path) {
        if (path == null || path.isEmpty()) return null;
        File f = new File(path);
        return f.isFile() ? f : null;
    }
    private static NativeImage readImage(File file) {
        try (InputStream in = new FileInputStream(file)) { return NativeImage.read(in); }
        catch (Throwable t) { return null; }
    }
    private static boolean validCape(NativeImage i) {
        return (i.getWidth() == 64 && i.getHeight() == 32)
                || (i.getWidth() == 22 && i.getHeight() == 17)
                || (i.getWidth() == 44 && i.getHeight() == 34);
    }
    private static String stableName(File file) {
        return Integer.toHexString(file.getAbsolutePath().hashCode()) + "_" + file.lastModified() + ".png";
    }
    private static String signature() {
        File f = profileFile();
        return f == null ? "" : f.getAbsolutePath() + ":" + f.lastModified() + ":" + f.length();
    }
    private static void log(String s) { System.out.println(LOG + " " + s); }
    private static final class Profile {
        String accountUuid, name, model; boolean skinEnabled, capeEnabled; File skinFile, capeFile;
    }
}
