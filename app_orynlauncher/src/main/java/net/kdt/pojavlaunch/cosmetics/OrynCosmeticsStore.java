package net.kdt.pojavlaunch.cosmetics;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
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
import java.security.MessageDigest;
import java.util.regex.Pattern;

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
        // Always rewrite bundled presets to replace malformed textures from earlier builds.
        if (out.exists() && !out.delete()) return;
        // Standard Minecraft cape texture: 64x32 with separate front/back and side UV faces.
        Bitmap bitmap = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(false);
        // Front and back faces are separate UV panels. Paint both consistently.
        drawCapePanel(canvas, paint, 1, 1, base, accent, shadow, highlight);
        drawCapePanel(canvas, paint, 12, 1, base, accent, shadow, highlight);
        // Side strips and bottom/top edges; no extra overlaid cape layer is created.
        paint.setColor(shadow);
        canvas.drawRect(0, 1, 1, 17, paint);
        canvas.drawRect(11, 1, 12, 17, paint);
        canvas.drawRect(22, 1, 23, 17, paint);
        canvas.drawRect(1, 0, 11, 1, paint);
        canvas.drawRect(12, 0, 22, 1, paint);
        canvas.drawRect(1, 17, 11, 18, paint);
        canvas.drawRect(12, 17, 22, 18, paint);
        try (FileOutputStream stream = new FileOutputStream(out)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        } catch (Exception ignored) {
            // A later gallery open can retry creation.
        } finally {
            bitmap.recycle();
        }
    }

    private void drawCapePanel(Canvas canvas, Paint paint, int left, int top,
                               int base, int accent, int shadow, int highlight) {
        paint.setColor(base);
        canvas.drawRect(left, top, left + 10, top + 16, paint);
        paint.setColor(shadow);
        canvas.drawRect(left, top, left + 10, top + 2, paint);
        canvas.drawRect(left, top + 14, left + 10, top + 16, paint);
        paint.setColor(accent);
        for (int y = 3; y < 13; y += 4) {
            int inset = (y / 4 % 2 == 0) ? 1 : 3;
            canvas.drawRect(left + inset, top + y, left + 10 - inset, top + y + 2, paint);
        }
        paint.setColor(highlight);
        canvas.drawRect(left + 4, top + 3, left + 6, top + 13, paint);
    }

    /**
     * Preserve a full modern 64x32 UV map. For legacy flat panels, place the
     * same artwork on the front and back UV faces without stacking extra layers.
     */
    private Bitmap normalizeCape(Bitmap source) {
        if (source == null) return null;
        Bitmap result = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();
        paint.setFilterBitmap(false);
        if (source.getWidth() == 64 && source.getHeight() == 32) {
            canvas.drawBitmap(source, 0, 0, paint);
        } else {
            Bitmap panel = source;
            boolean scaled = false;
            if (source.getWidth() == 44 && source.getHeight() == 34) {
                panel = Bitmap.createScaledBitmap(source, 22, 17, false);
                scaled = true;
            }
            canvas.drawBitmap(panel, 1, 1, paint);
            // Legacy flat art is copied to the separate back-face UV panel, not
            // overlaid on the front face.
            canvas.drawBitmap(panel, 12, 1, paint);
            if (scaled) panel.recycle();
        }
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


    /**
     * Seed a complete offline skin collection from the bundled, valid 64x64 Oryn
     * Minecraft texture. Variants are real PNG skin textures (palette variants),
     * so the grid is populated without a browser or network dependency.
     */
    public void ensureDefaultSkins() {
        String[] names = {"Oryn", "Steve Classic", "Alex Slim", "Black Hoodie", "White Hoodie",
                "Glow", "Demon", "Anime Boy", "Pink Girl", "Purple Tech", "Golden", "Custom Skin"};
        int[] palettes = {0xFF808080, 0xFFB58A62, 0xFF4C9A78, 0xFF242424, 0xFFE8E8E8,
                0xFF00D8E8, 0xFFB32020, 0xFF6C9BD2, 0xFFE99AB7, 0xFF9A55E8, 0xFFE1B52B, 0xFF6F8A9B};
        File[] existing = skins.listFiles();
        java.util.HashSet<String> present = new java.util.HashSet<>();
        if (existing != null) for (File f : existing) present.add(f.getName().toLowerCase(java.util.Locale.ROOT));
        Bitmap base = null;
        try (InputStream in = context.getAssets().open("skinview/oryn_steve_skin.png")) {
            base = BitmapFactory.decodeStream(in);
        } catch (Exception ignored) {}
        if (base == null || base.getWidth() != 64 || base.getHeight() != 64) {
            if (base != null) base.recycle();
            return;
        }
        try {
            for (int i = 0; i < names.length; i++) {
                String filename = names[i].replace(' ', '_') + ".png";
                if (present.contains(filename.toLowerCase(java.util.Locale.ROOT))) continue;
                Bitmap output = Bitmap.createBitmap(base.getWidth(), base.getHeight(), Bitmap.Config.ARGB_8888);
                int tint = palettes[i];
                for (int y = 0; y < base.getHeight(); y++) for (int x = 0; x < base.getWidth(); x++) {
                    int pixel = base.getPixel(x, y);
                    if (Color.alpha(pixel) == 0 || i == 0) { output.setPixel(x, y, pixel); continue; }
                    int lum = (Color.red(pixel) * 30 + Color.green(pixel) * 59 + Color.blue(pixel) * 11) / 100;
                    float shade = 0.48f + (lum / 255f) * 0.52f;
                    int r = Math.min(255, Math.round(Color.red(tint) * shade));
                    int g = Math.min(255, Math.round(Color.green(tint) * shade));
                    int b = Math.min(255, Math.round(Color.blue(tint) * shade));
                    // Keep skin/face pixels closer to the original texture while recolouring clothing.
                    boolean face = x >= 8 && x < 16 && y >= 8 && y < 16;
                    if (face) { r = Color.red(pixel); g = Color.green(pixel); b = Color.blue(pixel); }
                    output.setPixel(x, y, Color.argb(Color.alpha(pixel), r, g, b));
                }
                File out = new File(skins, filename);
                try (FileOutputStream stream = new FileOutputStream(out)) {
                    output.compress(Bitmap.CompressFormat.PNG, 100, stream);
                } catch (Exception ignored) { out.delete(); }
                finally { output.recycle(); }
            }
        } finally { base.recycle(); }
    }

    /**
     * Replaces the placeholder recolours with real community skin PNGs.
     * Downloads run off the UI thread and each texture is validated before replacing a preset.
     * Sources: SKiNDEX community skin pages; keep this optional so the gallery works offline.
     */
    public void downloadCuratedSkins(Runnable onComplete) {
        final String[][] sources = {
            {"Steve_Classic.png", "https://www.skindex.pro/static/skins/classic-steve-9fcd2533-c967-4dcd-989e-79b49e964de2.png"},
            {"Alex_Slim.png", "https://www.minecraftskins.com/uploads/skins/2021/05/16/original-alex-skin-17819683.png?v971"},
            {"Demon.png", "https://www.skindex.pro/static/skins/demon-boy-krpfima-3478d8fd-f6c5-4d52-a2cc-cda8e6ac66a6.png"},
            {"Anime_Boy.png", "https://www.skindex.pro/static/skins/anime-boy-4P6jkYUB45LNgFQNk84qwC.png"},
            {"Pink_Girl.png", "https://www.skindex.pro/static/skins/cute-pink-girl-3MEEXD84B5jGRn7dWtP2Ek.png"}
        };
        new Thread(() -> {
            android.content.SharedPreferences prefs = context.getSharedPreferences("oryn_curated_skins", Context.MODE_PRIVATE);
            boolean changed = false;
            for (String[] source : sources) {
                String fileName = source[0];
                String key = "v1_" + fileName;
                if (prefs.getBoolean(key, false)) continue;
                java.net.HttpURLConnection connection = null;
                File temp = new File(skins, fileName + ".download");
                try {
                    connection = (java.net.HttpURLConnection) new java.net.URL(source[1]).openConnection();
                    connection.setConnectTimeout(8000);
                    connection.setReadTimeout(12000);
                    connection.setInstanceFollowRedirects(true);
                    connection.setRequestProperty("User-Agent", "OrynLauncherPlus/5.0");
                    int code = connection.getResponseCode();
                    if (code < 200 || code >= 300) continue;
                    try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(temp)) {
                        byte[] buffer = new byte[8192]; int count; long total = 0;
                        while ((count = in.read(buffer)) != -1) {
                            total += count;
                            if (total > 2 * 1024 * 1024) throw new java.io.IOException("Skin PNG too large");
                            out.write(buffer, 0, count);
                        }
                    }
                    Bitmap check = BitmapFactory.decodeFile(temp.getAbsolutePath());
                    boolean valid = check != null && check.getWidth() == 64 && check.getHeight() == 64;
                    if (check != null) check.recycle();
                    if (!valid) { temp.delete(); continue; }
                    File target = new File(skins, fileName);
                    File backup = new File(skins, fileName + ".old");
                    if (target.exists() && !target.renameTo(backup)) { temp.delete(); continue; }
                    if (!temp.renameTo(target)) {
                        if (backup.exists()) backup.renameTo(target);
                        temp.delete();
                        continue;
                    }
                    if (backup.exists()) backup.delete();
                    prefs.edit().putBoolean(key, true).apply();
                    changed = true;
                } catch (Exception ignored) {
                    // Keep the offline fallback and retry this texture next time.
                    temp.delete();
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
            final boolean updated = changed;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (onComplete != null) onComplete.run();
            });
        }, "Oryn-Curated-Skins").start();
    }

    /** Deletes the previous generated/demo collection once, while preserving skins added afterward. */
    public boolean clearOldSkinCollectionOnce() {
        android.content.SharedPreferences prefs = context.getSharedPreferences("oryn_skin_catalog_migration", Context.MODE_PRIVATE);
        if (prefs.getBoolean("old_presets_removed_v1", false)) return false;
        File[] files = skins.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
                    file.delete();
                }
            }
        }
        prefs.edit().putBoolean("old_presets_removed_v1", true).apply();
        return true;
    }

    public void removeBundledDemoSkins() {
        String[] demo = {"Oryn.png","Steve_Classic.png","Alex_Slim.png","Black_Hoodie.png","White_Hoodie.png","Glow.png","Demon.png","Anime_Boy.png","Pink_Girl.png","Purple_Tech.png","Golden.png","Custom_Skin.png"};
        for (String name : demo) { File f = new File(skins, name); if (f.isFile()) f.delete(); }
    }

    public File importRemoteSkin(String url, String name) throws Exception {
        // Laby's render endpoint returns a rendered character image, not a UV atlas.
        // Never save it as a Minecraft skin texture.
        if (url == null || url.contains("laby.net/api/v3/render/skin/") ||
                url.contains("skin.laby.net/api/render/skin/")) {
            throw new IllegalArgumentException("This source returns a rendered preview, not a raw Minecraft skin texture.");
        }
        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        connection.setConnectTimeout(12000); connection.setReadTimeout(15000); connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "OrynLauncherPlus/5.0");
        File temp = new File(skins, "remote_download.tmp");
        try {
            connection.connect();
            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) throw new IllegalArgumentException("NovaSkin returned HTTP " + connection.getResponseCode());
            try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[8192]; int count; long total = 0;
                while ((count = in.read(buffer)) != -1) { total += count; if (total > 8 * 1024 * 1024) throw new IllegalArgumentException("Skin image is too large (8 MB limit)."); out.write(buffer, 0, count); }
            }
            Bitmap bitmap = BitmapFactory.decodeFile(temp.getAbsolutePath());
            if (!isCompatibleSkin(bitmap)) {
                if (bitmap != null) bitmap.recycle();
                throw new IllegalArgumentException("Skin PNG must be a modern square texture (64×64 or larger) or a legacy 64×32 texture.");
            }
            File target = unique(skins, safeName(name, "novaskin") + ".png");
            try {
                // skinview3d and Minecraft use the standard 64px UV layout.
                // Downsample high-resolution square textures to prevent stretched/glitched UVs.
                if (bitmap.getWidth() > 64 && bitmap.getWidth() == bitmap.getHeight()) {
                    Bitmap normalized = Bitmap.createScaledBitmap(bitmap, 64, 64, false);
                    try (FileOutputStream out = new FileOutputStream(target)) {
                        normalized.compress(Bitmap.CompressFormat.PNG, 100, out);
                    } finally { normalized.recycle(); }
                    temp.delete();
                } else {
                    bitmap.recycle();
                    bitmap = null;
                    if (!temp.renameTo(target)) {
                        try (InputStream in = new FileInputStream(temp); FileOutputStream out = new FileOutputStream(target)) {
                            byte[] b = new byte[8192]; int n; while ((n=in.read(b))!=-1) out.write(b,0,n);
                        }
                        temp.delete();
                    }
                }
            } finally { if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle(); }
            return target;
        } finally { connection.disconnect(); if (temp.exists()) temp.delete(); }
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
        if (!isCompatibleSkin(b)) {
            if (b != null) b.recycle();
            throw new IllegalArgumentException("Skin PNG must be 64×64 (or a higher-resolution square) or legacy 64×32.");
        }
        File out = unique(skins, safeName(name, "skin") + ".png");
        try {
            if (b.getWidth() > 64 && b.getWidth() == b.getHeight()) {
                // Normalize oversized skins to Minecraft's standard UV atlas.
                Bitmap normalized = Bitmap.createScaledBitmap(b, 64, 64, false);
                try (FileOutputStream stream = new FileOutputStream(out)) {
                    normalized.compress(Bitmap.CompressFormat.PNG, 100, stream);
                } finally { normalized.recycle(); }
            } else {
                copyUri(uri, out);
            }
        } catch (Exception e) {
            out.delete();
            throw e;
        } finally {
            if (b != null && !b.isRecycled()) b.recycle();
        }
        return out;
    }

    private boolean isCompatibleSkin(Bitmap bitmap) {
        if (bitmap == null) return false;
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        // Standard modern skins and legacy 64×32 skins are both supported.
        return (w == h && w >= 64 && w % 64 == 0) || (w == 64 && h == 32);
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

    /**
     * Writes the launcher profile AND bridges selected textures to CustomSkinLoader's
     * documented LocalSkin directory. A JSON manifest by itself is not consumed by Minecraft.
     * The universal loader is installed only for recognized mod-loader instances.
     */
    public void writeActiveForInstance(File gameDir, String username, String versionId, File[] classpath) {
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

            String playerName = safeName(username, "");
            if (playerName.isEmpty()) {
                android.util.Log.w("OrynCosmetics", "No usable Minecraft username; local skin sync skipped");
                return;
            }
            File cslRoot = new File(gameDir, "CustomSkinLoader");
            File localRoot = new File(cslRoot, "LocalSkin");
            File localSkins = new File(localRoot, "skins");
            File localCapes = new File(localRoot, "capes");
            localSkins.mkdirs();
            localCapes.mkdirs();

            syncLocalTexture(p.skinEnabled ? new File(skins, p.skin) : null, new File(localSkins, playerName + ".png"));
            syncLocalTexture(p.capeEnabled ? new File(capes, p.cape) : null, new File(localCapes, playerName + ".png"));
            ensureLocalSkinFirst(new File(cslRoot, "CustomSkinLoader.json"));

            if (hasSupportedModLoader(classpath) && isSupportedCslVersion(versionId)) {
                installCustomSkinLoader(new File(gameDir, "mods"));
            }
        } catch (Exception e) {
            android.util.Log.w("OrynCosmetics", "Could not sync selected textures", e);
        }
    }

    private void syncLocalTexture(File source, File target) throws Exception {
        if (source == null || !source.isFile()) {
            if (target.exists() && !target.delete()) {
                android.util.Log.w("OrynCosmetics", "Could not remove stale local texture: " + target);
            }
            return;
        }
        try (InputStream in = new FileInputStream(source); FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            out.getFD().sync();
        }
    }

    private void ensureLocalSkinFirst(File configFile) throws Exception {
        JSONObject config = new JSONObject();
        if (configFile.isFile()) {
            try { config = new JSONObject(read(configFile)); } catch (Exception ignored) {}
        }
        JSONArray old = config.optJSONArray("loadlist");
        JSONArray reordered = new JSONArray();
        reordered.put(new JSONObject().put("name", "LocalSkin").put("type", "Legacy").put("root", "LocalSkin/"));
        if (old != null) {
            for (int i = 0; i < old.length(); i++) {
                JSONObject item = old.optJSONObject(i);
                if (item == null || "LocalSkin".equalsIgnoreCase(item.optString("name", ""))) continue;
                reordered.put(item);
            }
        } else {
            reordered.put(new JSONObject().put("name", "Mojang").put("type", "MojangAPI"));
        }
        config.put("enable", true);
        config.put("loadCape", true);
        config.put("loadlist", reordered);
        File parent = configFile.getParentFile();
        if (parent != null) parent.mkdirs();
        try (FileOutputStream out = new FileOutputStream(configFile)) {
            out.write(config.toString(2).getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
    }

    private boolean hasSupportedModLoader(File[] classpath) {
        if (classpath == null) return false;
        for (File entry : classpath) {
            if (entry == null) continue;
            String path = entry.getAbsolutePath().toLowerCase(java.util.Locale.ROOT);
            if (path.contains("fabric-loader") || path.contains("net.minecraftforge")
                    || path.contains("fmlloader") || path.contains("neoforge")
                    || path.contains("quilt-loader") || path.contains("modlauncher")) return true;
        }
        return false;
    }

    private boolean isSupportedCslVersion(String versionId) {
        if (versionId == null) return false;
        // CustomSkinLoader Universal v15.1 supports Minecraft 1.8–1.21 and 26.1–26.3.
        return Pattern.compile("(^|[^0-9])(1\\.(8|9|1[0-9]|2[0-1])(\\.[0-9]+)?|26\\.[1-3](\\.[0-9]+)?)([^0-9]|$)")
                .matcher(versionId).find();
    }

    private void installCustomSkinLoader(File modsDir) {
        File jar = new File(modsDir, "CustomSkinLoader_Universal-15.1.jar");
        if (jar.isFile() && jar.length() > 100000) return;
        modsDir.mkdirs();
        File temp = new File(modsDir, jar.getName() + ".download");
        java.net.HttpURLConnection connection = null;
        try {
            connection = (java.net.HttpURLConnection) new java.net.URL(
                    "https://github.com/xfl03/MCCustomSkinLoader/releases/download/v15.1/CustomSkinLoader_Universal-15.1.jar").openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(12000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "OrynLauncherPlus/5.0");
            if (connection.getResponseCode() != 200) throw new java.io.IOException("HTTP " + connection.getResponseCode());
            try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                out.getFD().sync();
            }
            String digest = sha256(temp);
            if (!"c8769a38b3cc1da2a7b2f5827d1a53a24cd1ef6d0784b1eb545569f5882a2ffb".equals(digest)) {
                throw new java.io.IOException("CustomSkinLoader checksum mismatch");
            }
            if (jar.exists() && !jar.delete()) throw new java.io.IOException("Cannot replace old CustomSkinLoader jar");
            if (!temp.renameTo(jar)) throw new java.io.IOException("Cannot install CustomSkinLoader jar");
            android.util.Log.i("OrynCosmetics", "Installed verified CustomSkinLoader Universal v15.1");
        } catch (Exception e) {
            android.util.Log.w("OrynCosmetics", "Could not install CustomSkinLoader; check network and loader compatibility", e);
            temp.delete();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder result = new StringBuilder();
        for (byte b : digest.digest()) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
        return result.toString();
    }

    private String read(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int n = in.read(b);
            return new String(b, 0, n, StandardCharsets.UTF_8);
        }
    }
}