package net.kdt.pojavlaunch.cosmetics;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

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

    public static final class CosmeticProfile {
        public String name = DEFAULT_PROFILE;
        public String skin = "";
        public String model = "classic";
        public String cape = "";
    }

    public OrynCosmeticsStore(Context context) {
        this.context = context.getApplicationContext();
        root = new File(this.context.getFilesDir(), "cosmetics");
        skins = new File(root, "skins");
        capes = new File(root, "capes");
        profiles = new File(root, "profiles");
        active = new File(root, "active_profile.json");
        ensureDirs();
    }

    private void ensureDirs() {
        skins.mkdirs(); capes.mkdirs(); profiles.mkdirs();
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
        Bitmap b = decode(uri);
        if (b == null || !((b.getWidth() == 64 && b.getHeight() == 32) ||
                (b.getWidth() == 22 && b.getHeight() == 17) ||
                (b.getWidth() == 44 && b.getHeight() == 34)))
            throw new IllegalArgumentException("Cape must be a compatible 64×32, 22×17, or 44×34 PNG.");
        File out = unique(capes, safeName(name, "cape") + ".png");
        copyUri(uri, out);
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

    public void delete(File file) {
        if (file != null) file.delete();
    }

    public void saveProfile(CosmeticProfile p) throws Exception {
        JSONObject o = new JSONObject();
        o.put("name", p.name);
        o.put("skin", p.skin);
        o.put("model", p.model);
        o.put("cape", p.cape);
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
            return p;
        } catch (Exception e) {
            return listProfiles().get(0);
        }
    }

    public void setActiveProfile(CosmeticProfile p) throws Exception {
        saveProfile(p);
        JSONObject o = new JSONObject();
        o.put("name", p.name); o.put("skin", p.skin); o.put("model", p.model); o.put("cape", p.cape);
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