package net.kdt.pojavlaunch.cosmetics;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;

import net.kdt.pojavlaunch.authenticator.AuthType;
import net.kdt.pojavlaunch.authenticator.accounts.Account;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class OrynCosmeticsStore {
    public static final String DEFAULT_PROFILE = "Default";
    private final Context context;
    private final File root, skins, capes, profiles, active;
    private final File legacyRoot, legacyProfiles, legacyActive;

    public static final class CosmeticProfile {
        public String name = DEFAULT_PROFILE;
        public String skin = "";
        public String model = "classic";
        public String cape = "";
        public boolean skinEnabled = true;
        public boolean capeEnabled = true;
    }

    public OrynCosmeticsStore(Context context) {
        this.context = context.getApplicationContext();
        legacyRoot = new File(this.context.getFilesDir(), "cosmetics");
        legacyProfiles = new File(legacyRoot, "profiles");
        legacyActive = new File(legacyRoot, "active_profile.json");

        root = new File(legacyRoot, "accounts/" + accountKey());
        skins = new File(legacyRoot, "skins");
        capes = new File(legacyRoot, "capes");
        profiles = new File(root, "profiles");
        active = new File(root, "active_profile.json");
        ensureDirs();
        migrateLegacyProfilesIfNeeded();
    }

    private void ensureDirs() {
        skins.mkdirs(); capes.mkdirs(); profiles.mkdirs();
    }

    private String accountKey() {
        try {
            Account a = Accounts.getCurrent();
            if (a != null) {
                String uuid = a.profileId == null ? "" : a.profileId.replace("-", "");
                boolean zeroUuid = uuid.isEmpty() || uuid.matches("0+");
                String identity = zeroUuid
                        ? ((a.authType == null ? AuthType.LOCAL.name() : a.authType.name()) + "_" + (a.username == null ? "unknown" : a.username))
                        : uuid;
                return safeName(identity, "unbound");
            }
        } catch (Throwable ignored) {}
        return "unbound";
    }

    private void migrateLegacyProfilesIfNeeded() {
        if (profiles.exists() && profiles.listFiles() != null && profiles.listFiles().length > 0) return;
        File[] old = legacyProfiles.listFiles();
        if (old != null) {
            for (File f : old) {
                if (!f.isFile() || !f.getName().endsWith(".json")) continue;
                try {
                    String data = read(f);
                    try (FileOutputStream out = new FileOutputStream(new File(profiles, f.getName()))) {
                        out.write(data.getBytes(StandardCharsets.UTF_8));
                    }
                } catch (Exception ignored) {}
            }
        }
        if (!active.exists() && legacyActive.exists()) {
            try {
                String data = read(legacyActive);
                try (FileOutputStream out = new FileOutputStream(active)) {
                    out.write(data.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception ignored) {}
        }
    }

    /** Creates bundled Oryn cape textures on first use so the Oryn Capes tab is populated offline. */
    public void ensureOrynCapes() {
        createOrynCape("oryn_ember.png", 0xFF10131B, 0xFFFF2424, 0xFF7A0000, 0xFFF5F5F5);
        createOrynCape("oryn_ice.png", 0xFF071827, 0xFF39C5FF, 0xFF1456A0, 0xFFEAFBFF);
        createOrynCape("oryn_void.png", 0xFF09090D, 0xFF7B3CFF, 0xFF35106E, 0xFFE7D9FF);
        createOrynCape("oryn_forest.png", 0xFF0B1B13, 0xFF34E27A, 0xFF12643A, 0xFFD6FFE4);
    }

    private void createOrynCape(String name, int base, int accent, int shadow, int highlight) {
        File out = new File(capes, name);
        // Rewrite presets on every gallery open so previously generated bad textures are repaired.
        if (out.exists() && !out.delete()) return;
        // Minecraft cape UV map: use the upper-left 22x17 area of a 64x32 texture.
        // Keep unused UV space transparent to avoid streaks and wrapped pixels.
        Bitmap bitmap = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(false);
        paint.setColor(base);
        canvas.drawRect(1, 1, 11, 17, paint);
        canvas.drawRect(12, 1, 22, 17, paint);
        paint.setColor(shadow);
        canvas.drawRect(1, 1, 11, 3, paint);
        canvas.drawRect(12, 1, 22, 3, paint);
        canvas.drawRect(1, 15, 11, 17, paint);
        canvas.drawRect(12, 15, 22, 17, paint);
        paint.setColor(accent);
        for (int y = 4; y < 14; y += 4) {
            int x1 = (y / 4 % 2 == 0) ? 2 : 4;
            canvas.drawRect(x1, y, 12 - x1, y + 2, paint);
            canvas.drawRect(12 + x1, y, 24 - x1, y + 2, paint);
        }
        paint.setColor(highlight);
        canvas.drawRect(5, 4, 7, 14, paint);
        canvas.drawRect(16, 4, 18, 14, paint);
        try (FileOutputStream stream = new FileOutputStream(out)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        } catch (Exception ignored) {
            // A later gallery open can retry creation.
        } finally {
            bitmap.recycle();
        }
    }

    /** Normalizes legacy and modern cape PNGs into Minecraft's 64x32 texture size. */
    private Bitmap normalizeCape(Bitmap source) {
        if (source == null) return null;
        Bitmap panel;
        boolean recyclePanel = false;
        if (source.getWidth() == 64 && source.getHeight() == 32) {
            // Only the upper-left 22x17 UV area belongs to the cape mesh.
            panel = Bitmap.createBitmap(source, 0, 0, 22, 17);
            recyclePanel = true;
        } else if (source.getWidth() == 44 && source.getHeight() == 34) {
            panel = Bitmap.createScaledBitmap(source, 22, 17, false);
            recyclePanel = true;
        } else {
            panel = source;
        }
        Bitmap result = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();
        paint.setFilterBitmap(false);
        canvas.drawBitmap(panel, 0, 0, paint);
        if (recyclePanel) panel.recycle();
        return result;
    }

    /** Repairs capes created by older Oryn builds before they are previewed or applied. */
    public void normalizeExistingCapes() {
        File[] files = capes.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (!file.isFile() || !file.getName().toLowerCase().endsWith(".png")) continue;
            Bitmap decoded = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (decoded == null) continue;
            boolean valid = decoded.getWidth() == 64 && decoded.getHeight() == 32;
            if (!valid && !((decoded.getWidth() == 22 && decoded.getHeight() == 17) ||
                    (decoded.getWidth() == 44 && decoded.getHeight() == 34))) {
                decoded.recycle();
                continue;
            }
            Bitmap normalized = normalizeCape(decoded);
            decoded.recycle();
            if (normalized == null) continue;
            File temp = new File(file.getParentFile(), file.getName() + ".tmp");
            try (FileOutputStream stream = new FileOutputStream(temp)) {
                normalized.compress(Bitmap.CompressFormat.PNG, 100, stream);
                if (file.delete()) temp.renameTo(file);
                else temp.delete();
            } catch (Exception ignored) {
                temp.delete();
            } finally {
                normalized.recycle();
            }
        }
    }

    public List<File> listSkins() { return listPng(skins); }
    public List<File> listCapes() { return listPng(capes); }

    private List<File> listPng(File dir) {
        File[] files = dir.listFiles();
        List<File> out = new ArrayList<>();
        if (files != null) for (File f : files) if (f.isFile() && f.getName().toLowerCase().endsWith(".png")) out.add(f);
        return out;
    }

    public File importSkin(Uri uri, String name) throws Exception {
        Bitmap b = decode(uri);
        if (b == null || b.getWidth() != b.getHeight() || b.getWidth() < 64 || b.getWidth() % 64 != 0)
            throw new IllegalArgumentException("Skin must be a square 64×64 or compatible higher-resolution PNG.");
        File out = unique(skins, safeName(name, "skin") + ".png");
        copyUri(uri, out);
        return out;
    }

    public File importCape(Uri uri, String name) throws Exception {
        Bitmap decoded = decode(uri);
        if (decoded == null || !((decoded.getWidth() == 64 && decoded.getHeight() == 32) ||
                (decoded.getWidth() == 22 && decoded.getHeight() == 17) ||
                (decoded.getWidth() == 44 && decoded.getHeight() == 34))) {
            if (decoded != null) decoded.recycle();
            throw new IllegalArgumentException("Cape must be a compatible 64×32, 22×17, or 44×34 PNG.");
        }
        Bitmap normalized = normalizeCape(decoded);
        if (normalized == null) {
            decoded.recycle();
            throw new IllegalArgumentException("Unable to normalize this cape texture.");
        }
        File out = unique(capes, safeName(name, "cape") + ".png");
        try (FileOutputStream stream = new FileOutputStream(out)) {
            normalized.compress(Bitmap.CompressFormat.PNG, 100, stream);
        } finally {
            normalized.recycle();
            decoded.recycle();
        }
        return out;
    }

    private Bitmap decode(Uri uri) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(in);
        } catch (Exception e) { return null; }
    }

    private void copyUri(Uri uri, File out) throws Exception {
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(out)) {
            if (in == null) throw new IllegalArgumentException("Unable to read selected file.");
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
        }
    }

    private File unique(File dir, String name) {
        File f = new File(dir, name);
        int i = 2;
        while (f.exists()) f = new File(dir, name.replace(".png", "_" + i++ + ".png"));
        return f;
    }

    private String safeName(String value, String fallback) {
        String s = value == null ? "" : value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        return s.isEmpty() ? fallback : s;
    }

    public Bitmap load(File f) {
        return BitmapFactory.decodeFile(f.getAbsolutePath());
    }

    public boolean delete(File file) {
        return file != null && file.exists() && file.delete();
    }

    public void removeCosmetic(String fileName, boolean skin) throws Exception {
        if (fileName == null || fileName.isEmpty()) return;
        File target = new File(skin ? skins : capes, fileName);
        if (target.exists() && !target.delete()) {
            throw new IllegalStateException("Could not remove " + fileName);
        }

        File[] fs = profiles.listFiles();
        if (fs == null) return;
        for (File profileFile : fs) {
            if (!profileFile.getName().endsWith(".json")) continue;
            try {
                JSONObject o = new JSONObject(read(profileFile));
                String key = skin ? "skin" : "cape";
                if (fileName.equals(o.optString(key, ""))) {
                    o.put(key, "");
                    try (FileOutputStream out = new FileOutputStream(profileFile)) {
                        out.write(o.toString(2).getBytes(StandardCharsets.UTF_8));
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    public void saveProfile(CosmeticProfile p) throws Exception {
        JSONObject o = new JSONObject();
        o.put("name", p.name);
        o.put("skin", p.skin);
        o.put("model", p.model);
        o.put("cape", p.cape);
        o.put("skinEnabled", p.skinEnabled);
        o.put("capeEnabled", p.capeEnabled);
        try (FileOutputStream out = new FileOutputStream(new File(profiles, safeName(p.name, "profile") + ".json"))) {
            out.write(o.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    public List<CosmeticProfile> listProfiles() {
        List<CosmeticProfile> result = new ArrayList<>();
        File[] fs = profiles.listFiles();
        if (fs != null) for (File f : fs) if (f.getName().endsWith(".json")) {
            try {
                String s = read(f);
                JSONObject o = new JSONObject(s);
                CosmeticProfile p = new CosmeticProfile();
                p.name = o.optString("name", DEFAULT_PROFILE);
                p.skin = o.optString("skin", "");
                p.model = o.optString("model", "classic");
                p.cape = o.optString("cape", "");
                p.skinEnabled = o.has("skinEnabled") ? o.optBoolean("skinEnabled", true) : !p.skin.isEmpty();
                p.capeEnabled = o.has("capeEnabled") ? o.optBoolean("capeEnabled", true) : !p.cape.isEmpty();
                result.add(p);
            } catch (Exception ignored) {}
        }
        if (result.isEmpty()) {
            String[] defaults = {"Default", "PvP", "Survival", "Custom"};
            for (String name : defaults) {
                CosmeticProfile p = new CosmeticProfile(); p.name = name;
                try { saveProfile(p); } catch (Exception ignored) {}
                result.add(p);
            }
        }
        return result;
    }

    public CosmeticProfile getActiveProfile() {
        try {
            JSONObject o = new JSONObject(read(active));
            CosmeticProfile p = new CosmeticProfile();
            p.name = o.optString("name", DEFAULT_PROFILE);
            p.skin = o.optString("skin", "");
            p.model = o.optString("model", "classic");
            p.cape = o.optString("cape", "");
            p.skinEnabled = o.has("skinEnabled") ? o.optBoolean("skinEnabled", true) : !p.skin.isEmpty();
            p.capeEnabled = o.has("capeEnabled") ? o.optBoolean("capeEnabled", true) : !p.cape.isEmpty();
            return p;
        } catch (Exception e) {
            return listProfiles().get(0);
        }
    }

    public void setActiveProfile(CosmeticProfile p) throws Exception {
        saveProfile(p);
        JSONObject o = new JSONObject();
        o.put("name", p.name); o.put("skin", p.skin); o.put("model", p.model); o.put("cape", p.cape);
        o.put("skinEnabled", p.skinEnabled); o.put("capeEnabled", p.capeEnabled);
        try (FileOutputStream out = new FileOutputStream(active)) {
            out.write(o.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    public void writeActiveForInstance(File gameDir) {
        try {
            File dir = new File(gameDir, ".oryn/cosmetics");
            dir.mkdirs();
            CosmeticProfile p = getActiveProfile();
            JSONObject o = new JSONObject();
            o.put("name", p.name); o.put("skin", p.skin); o.put("model", p.model); o.put("cape", p.cape);
            o.put("skinPath", p.skin.isEmpty() ? "" : new File(skins, p.skin).getAbsolutePath());
            o.put("capePath", p.cape.isEmpty() ? "" : new File(capes, p.cape).getAbsolutePath());
            o.put("skinEnabled", p.skinEnabled);
            o.put("capeEnabled", p.capeEnabled);
            o.put("accountKey", accountKey());
            try (FileOutputStream out = new FileOutputStream(new File(dir, "active_profile.json"))) {
                out.write(o.toString(2).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {}
    }

    private String read(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int n = in.read(b);
            return new String(b, 0, n, StandardCharsets.UTF_8);
        }
    }
}