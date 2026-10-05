package net.orynlauncher.cosmetics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

public final class OrynRuntimeProfile {
    private static final Logger LOGGER = LoggerFactory.getLogger("OrynCosmetics");
    private static OrynRuntimeProfile cached;
    private static long cachedModified = Long.MIN_VALUE;

    public final String accountUuid;
    public final String accountName;
    public final String skinPath;
    public final String capePath;
    public final String model;
    public final boolean skinEnabled;
    public final boolean capeEnabled;

    private OrynRuntimeProfile(String accountUuid, String accountName,
                               String skinPath, String capePath, String model,
                               boolean skinEnabled, boolean capeEnabled) {
        this.accountUuid = accountUuid == null ? "" : accountUuid;
        this.accountName = accountName == null ? "" : accountName;
        this.skinPath = skinPath == null ? "" : skinPath;
        this.capePath = capePath == null ? "" : capePath;
        this.model = "slim".equalsIgnoreCase(model) ? "slim" : "classic";
        this.skinEnabled = skinEnabled;
        this.capeEnabled = capeEnabled;
    }

    public static OrynRuntimeProfile load() {
        try {
            File file = new File(new File(System.getProperty("user.dir", ".")),
                    ".oryn/cosmetics/active_profile.json");
            if (!file.isFile()) {
                LOGGER.error("[ORYN-COSMETICS] CUSTOM SKIN FAILED Reason: runtime profile missing path={}", file.getAbsolutePath());
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
                    get(o, "accountUuid"),
                    get(o, "accountName"),
                    get(o, "skinPath"),
                    get(o, "capePath"),
                    get(o, "model", "classic"),
                    getBool(o, "skinEnabled"),
                    getBool(o, "capeEnabled"));
            cachedModified = modified;

            LOGGER.info("[ORYN-COSMETICS] account UUID = {}", cached.accountUuid);
            LOGGER.info("[ORYN-COSMETICS] skin enabled = {}", cached.skinEnabled);
            LOGGER.info("[ORYN-COSMETICS] skin path = {}", cached.skinPath);
            LOGGER.info("[ORYN-COSMETICS] skin model = {}", cached.model);
            LOGGER.info("[ORYN-COSMETICS] cape enabled = {}", cached.capeEnabled);
            LOGGER.info("[ORYN-COSMETICS] cape path = {}", cached.capePath);

            if (cached.skinEnabled && cached.skinPath.isEmpty()) {
                LOGGER.error("[ORYN-COSMETICS] CUSTOM SKIN FAILED Reason: skin enabled but skin path is empty");
            }
            return cached;
        } catch (Throwable t) {
            LOGGER.error("[ORYN-COSMETICS] CUSTOM SKIN FAILED Reason: runtime profile parse failed", t);
            return null;
        }
    }

    public File resolveSkinFile() {
        if (Boolean.getBoolean("oryn.cosmetics.testSkin")) {
            return OrynRuntimeTestSkin.ensure();
        }
        return skinPath.isEmpty() ? null : new File(skinPath);
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
}
