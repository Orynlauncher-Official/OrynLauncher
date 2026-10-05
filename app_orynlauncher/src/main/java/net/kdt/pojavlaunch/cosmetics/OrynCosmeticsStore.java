package net.kdt.pojavlaunch.cosmetics;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import net.kdt.pojavlaunch.authenticator.accounts.Account;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent Oryn cosmetics storage.
 *
 * Textures are shared as immutable cached assets, while the equipped profile is
 * scoped by Minecraft account UUID. This prevents cosmetics from leaking when
 * the user switches accounts.
 */
public final class OrynCosmeticsStore {
    public static final String DEFAULT_PROFILE = "Default";

    private final Context context;
    private final File root;
    private final File skins;
    private final File capes;
    private final File metadata;
    private final File profiles;
    private final File legacyActive;

    public static final class CosmeticProfile {
        public String name = DEFAULT_PROFILE;
        public String accountUuid = "";
        public String skin = "";
        public String skinUrl = "";
        public String model = "classic";
        public boolean skinEnabled = true;
        public String cape = "";
        public String capeUrl = "";
        public boolean capeEnabled = true;
    }

    public OrynCosmeticsStore(Context context) {
        this.context = context.getApplicationContext();
        root = new File(this.context.getFilesDir(), "cosmetics");
        skins = new File(root, "skins");
        capes = new File(root, "capes");
        metadata = new File(root, "metadata");
        profiles = new File(root, "profiles");
        legacyActive = new File(root, "active_profile.json");
        ensureDirs();
    }

    private void ensureDirs() {
        skins.mkdirs();
        capes.mkdirs();
        metadata.mkdirs();
        profiles.mkdirs();
    }

    public List<File> listSkins() {
        return listPng(skins);
    }

    public List<File> listCapes() {
        return listPng(capes);
    }

    private List<File> listPng(File dir) {
        File[] files = dir.listFiles();
        List<File> out = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && f.getName().toLowerCase().endsWith(".png")) out.add(f);
            }
        }
        return out;
    }

    public File importSkin(Uri uri, String name) throws Exception {
        Bitmap b = decode(uri);
        if (!isValidSkin(b)) {
            throw new IllegalArgumentException(
                    "Skin must be a valid square 64×64 (or compatible higher-resolution) PNG.");
        }
        File out = unique(skins, safeName(name, "skin") + ".png");
        copyUri(uri, out);
        writeTextureMetadata(out, "skin");
        return out;
    }

    public File importCape(Uri uri, String name) throws Exception {
        Bitmap b = decode(uri);
        if (!isValidCape(b)) {
            throw new IllegalArgumentException(
                    "Cape must be a supported 64×32, 22×17, or 44×34 PNG.");
        }
        File out = unique(capes, safeName(name, "cape") + ".png");
        copyUri(uri, out);
        writeTextureMetadata(out, "cape");
        return out;
    }

    public boolean isValidSkin(File file) {
        return file != null && isValidSkin(load(file));
    }

    public boolean isValidCape(File file) {
        return file != null && isValidCape(load(file));
    }

    private boolean isValidSkin(Bitmap b) {
        return b != null
                && b.getWidth() == b.getHeight()
                && b.getWidth() >= 64
                && b.getWidth() % 64 == 0;
    }

    private boolean isValidCape(Bitmap b) {
        return b != null && ((b.getWidth() == 64 && b.getHeight() == 32)
                || (b.getWidth() == 22 && b.getHeight() == 17)
                || (b.getWidth() == 44 && b.getHeight() == 34));
    }

    private Bitmap decode(Uri uri) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            return null;
        }
    }

    private void copyUri(Uri uri, File out) throws Exception {
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(out)) {
            if (in == null) throw new IllegalArgumentException("Unable to read selected file.");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
        }
    }

    private File unique(File dir, String name) {
        File f = new File(dir, name);
        int i = 2;
        while (f.exists()) {
            f = new File(dir, name.replace(".png", "_" + i++ + ".png"));
        }
        return f;
    }

    private String safeName(String value, String fallback) {
        String s = value == null ? "" : value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        return s.isEmpty() ? fallback : s;
    }

    private String accountKey(Account account) {
        if (account == null) return "anonymous";
        String uuid = account.profileId == null ? "" : account.profileId.trim();
        if (!uuid.isEmpty() && !uuid.matches("0{8}-0{4}-0{4}-0{4}-0{12}")) {
            return safeName(uuid.replace("-", ""), "account");
        }
        String type = account.authType == null ? "local" : account.authType.name().toLowerCase();
        return safeName(type + "-" + (account.username == null ? "player" : account.username), "anonymous");
    }

    private File accountDirectory(Account account) {
        File dir = new File(profiles, accountKey(account));
        dir.mkdirs();
        return dir;
    }

    private File activeFile(Account account) {
        return new File(accountDirectory(account), "active_profile.json");
    }

    public Bitmap load(File f) {
        if (f == null || !f.isFile()) return null;
        return BitmapFactory.decodeFile(f.getAbsolutePath());
    }

    public boolean delete(File file) {
        return file != null && file.exists() && file.delete();
    }

    /**
     * Unequip a cosmetic only for the selected account. The shared cached asset is
     * deleted only when no other account/profile references it.
     */
    public void removeCosmetic(Account account, String fileName, boolean skin) throws Exception {
        if (fileName == null || fileName.isEmpty()) return;

        CosmeticProfile active = getActiveProfile(account);
        String key = skin ? "skin" : "cape";
        if (skin) {
            active.skin = "";
            active.skinUrl = "";
            active.skinEnabled = false;
        } else {
            active.cape = "";
            active.capeUrl = "";
            active.capeEnabled = false;
        }
        setActiveProfile(account, active);

        if (!isReferenced(profiles, key, fileName)) {
            File target = new File(skin ? skins : capes, fileName);
            if (target.exists() && !target.delete()) {
                throw new IllegalStateException("Could not remove " + fileName);
            }
            File meta = new File(metadata, fileName + ".json");
            if (meta.exists()) meta.delete();
        }
    }

    /** Legacy compatibility: only clears references; it never deletes a shared asset blindly. */
    public void removeCosmetic(String fileName, boolean skin) throws Exception {
        removeCosmetic(Account.getCurrent(), fileName, skin);
    }

    private boolean isReferenced(File dir, String key, String fileName) {
        File[] files = dir.listFiles();
        if (files == null) return false;
        for (File f : files) {
            if (f.isDirectory()) {
                if (isReferenced(f, key, fileName)) return true;
                continue;
            }
            if (!f.getName().endsWith(".json")) continue;
            try {
                JSONObject o = new JSONObject(read(f));
                if (fileName.equals(o.optString(key, ""))) return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private void clearReferences(File dir, String key, String fileName) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                clearReferences(f, key, fileName);
                continue;
            }
            if (!f.getName().endsWith(".json")) continue;
            try {
                JSONObject o = new JSONObject(read(f));
                if (fileName.equals(o.optString(key, ""))) {
                    o.put(key, "");
                    if ("skin".equals(key)) o.put("skinEnabled", false);
                    else o.put("capeEnabled", false);
                    writeJson(f, o);
                }
            } catch (Exception ignored) {
            }
        }
    }

    public void saveProfile(Account account, CosmeticProfile p) throws Exception {
        if (p == null) throw new IllegalArgumentException("Cosmetic profile is null");
        p.accountUuid = account == null || account.profileId == null ? "" : account.profileId;
        File dir = accountDirectory(account);
        writeJson(new File(dir, safeName(p.name, DEFAULT_PROFILE) + ".json"), toJson(p));
    }

    /** Compatibility overload for old callers; it is intentionally account-scoped to the current account. */
    public void saveProfile(CosmeticProfile p) throws Exception {
        saveProfile(Account.getCurrent(), p);
    }

    public List<CosmeticProfile> listProfiles(Account account) {
        List<CosmeticProfile> result = new ArrayList<>();
        File[] fs = accountDirectory(account).listFiles();
        if (fs != null) {
            for (File f : fs) {
                if (!f.isFile() || !f.getName().endsWith(".json")
                        || "active_profile.json".equals(f.getName())) continue;
                try {
                    result.add(fromJson(new JSONObject(read(f))));
                } catch (Exception ignored) {
                }
            }
        }
        if (result.isEmpty()) {
            String[] defaults = {"Default", "PvP", "Survival", "Custom"};
            for (String name : defaults) {
                CosmeticProfile p = new CosmeticProfile();
                p.name = name;
                try {
                    saveProfile(account, p);
                } catch (Exception ignored) {
                }
                result.add(p);
            }
        }
        return result;
    }

    public List<CosmeticProfile> listProfiles() {
        return listProfiles(Account.getCurrent());
    }

    public CosmeticProfile getActiveProfile(Account account) {
        File scopedActive = activeFile(account);
        try {
            if (scopedActive.isFile()) {
                CosmeticProfile p = fromJson(new JSONObject(read(scopedActive)));
                p.accountUuid = account == null || account.profileId == null ? "" : account.profileId;
                return p;
            }

            // Migrate the old global profile once, but only into the currently
            // selected account. Future reads never use the global file.
            if (legacyActive.isFile()) {
                CosmeticProfile migrated = fromJson(new JSONObject(read(legacyActive)));
                migrated.accountUuid = account == null || account.profileId == null ? "" : account.profileId;
                setActiveProfile(account, migrated);
                return migrated;
            }
        } catch (Exception ignored) {
        }

        return listProfiles(account).get(0);
    }

    public CosmeticProfile getActiveProfile() {
        return getActiveProfile(Account.getCurrent());
    }

    public void setActiveProfile(Account account, CosmeticProfile p) throws Exception {
        saveProfile(account, p);
        writeJson(activeFile(account), toJson(p));
    }

    public void setActiveProfile(CosmeticProfile p) throws Exception {
        setActiveProfile(Account.getCurrent(), p);
    }

    public void writeActiveForInstance(File gameDir, Account account) {
        try {
            if (gameDir == null) return;
            File dir = new File(gameDir, ".oryn/cosmetics");
            dir.mkdirs();
            CosmeticProfile p = getActiveProfile(account);
            JSONObject o = toJson(p);
            o.put("skinPath", p.skin.isEmpty()
                    ? "" : new File(skins, p.skin).getAbsolutePath());
            o.put("capePath", p.cape.isEmpty()
                    ? "" : new File(capes, p.cape).getAbsolutePath());
            writeJson(new File(dir, "active_profile.json"), o);
        } catch (Exception ignored) {
        }
    }

    public void writeActiveForInstance(File gameDir) {
        writeActiveForInstance(gameDir, Account.getCurrent());
    }

    private JSONObject toJson(CosmeticProfile p) throws Exception {
        JSONObject o = new JSONObject();
        o.put("name", p.name);
        o.put("accountUuid", p.accountUuid == null ? "" : p.accountUuid);
        o.put("skin", p.skin == null ? "" : p.skin);
        o.put("skinUrl", p.skinUrl == null ? "" : p.skinUrl);
        o.put("model", "slim".equalsIgnoreCase(p.model) ? "slim" : "classic");
        o.put("skinEnabled", p.skinEnabled);
        o.put("cape", p.cape == null ? "" : p.cape);
        o.put("capeUrl", p.capeUrl == null ? "" : p.capeUrl);
        o.put("capeEnabled", p.capeEnabled);
        return o;
    }

    private CosmeticProfile fromJson(JSONObject o) {
        CosmeticProfile p = new CosmeticProfile();
        p.name = o.optString("name", DEFAULT_PROFILE);
        p.accountUuid = o.optString("accountUuid", "");
        p.skin = o.optString("skin", "");
        p.skinUrl = o.optString("skinUrl", "");
        p.model = "slim".equalsIgnoreCase(o.optString("model", "classic"))
                ? "slim" : "classic";
        p.skinEnabled = o.optBoolean("skinEnabled", true);
        p.cape = o.optString("cape", "");
        p.capeUrl = o.optString("capeUrl", "");
        p.capeEnabled = o.optBoolean("capeEnabled", true);
        return p;
    }

    private void writeJson(File file, JSONObject object) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(object.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    private void writeTextureMetadata(File texture, String type) {
        try {
            JSONObject o = new JSONObject();
            o.put("type", type);
            o.put("filename", texture.getName());
            o.put("size", texture.length());
            o.put("lastModified", texture.lastModified());
            writeJson(new File(metadata, texture.getName() + ".json"), o);
        } catch (Exception ignored) {
        }
    }

    private String read(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int n = in.read(b);
            return new String(b, 0, n, StandardCharsets.UTF_8);
        }
    }
}
