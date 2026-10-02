package net.kdt.pojavlaunch.utils;
import static net.kdt.pojavlaunch.CallbackBridge.windowHeight;
import static net.kdt.pojavlaunch.CallbackBridge.windowWidth;

import android.os.Build;
import android.os.FileObserver;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.Tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class MCOptionUtils {
    private static final HashMap<String,String> sParameterMap = new HashMap<>();
    private static final ArrayList<WeakReference<MCOptionListener>> sOptionListeners = new ArrayList<>();
    private static FileObserver sFileObserver;
    private static String sOptionFolderPath = null;
    public interface MCOptionListener {
        /** Called when an option is changed. Don't know which one though */
        void onOptionChanged();
    }


    public static void load(){
        load(sOptionFolderPath == null
                ? Tools.DIR_GAME_NEW
                : sOptionFolderPath);
    }

    public static void load(@NonNull String folderPath) {
        File optionFile = new File(folderPath + "/options.txt");
        if(!optionFile.exists()) {
            try { // Needed for new instances I guess  :think:
                optionFile.createNewFile();
            } catch (IOException e) { e.printStackTrace(); }
        }

        if(sFileObserver == null || !Objects.equals(sOptionFolderPath, folderPath)){
            sOptionFolderPath = folderPath;
            setupFileObserver();
        }
        sOptionFolderPath = folderPath; // Yeah I know, it may be redundant

        sParameterMap.clear();

        try {
            BufferedReader reader = new BufferedReader(new FileReader(optionFile));
            String line;
            while ((line = reader.readLine()) != null) {
                int firstColonIndex = line.indexOf(':');
                if(firstColonIndex < 0) {
                    Log.w(Tools.APP_NAME, "No colon on line \""+line+"\", skipping");
                    continue;
                }
                sParameterMap.put(line.substring(0,firstColonIndex), line.substring(firstColonIndex+1));
            }
            reader.close();
        } catch (IOException e) {
            Log.w(Tools.APP_NAME, "Could not load options.txt", e);
        }
    }

    public static void set(String key, String value) {
        sParameterMap.put(key,value);
    }

    public static void remove(String key) {
        sParameterMap.remove(key);
    }

    /**
     * Apply OrynLauncher FPS Boost directly to Minecraft's options.txt.
     * Only options that already exist in the instance are changed, which keeps
     * this safe across legacy and modern Minecraft option formats.
     * Original values are backed up per instance and restored when disabled.
     */
    public static void applyOrynFpsBoost(@NonNull String folderPath) {
        final Map<String, String> targets = new LinkedHashMap<>();
        targets.put("enableVsync", "false");
        targets.put("renderDistance", "8");
        targets.put("simulationDistance", "6");
        targets.put("entityDistanceScaling", "0.5");
        targets.put("particles", "2");
        targets.put("entityShadows", "false");
        targets.put("biomeBlendRadius", "0");
        targets.put("mipmapLevels", "0");
        targets.put("ao", "0");
        targets.put("fancyGraphics", "false");
        targets.put("graphicsMode", "0");
        targets.put("graphicsPreset", "fast");
        targets.put("clouds", "false");
        targets.put("renderClouds", "false");
        targets.put("cloudStatus", "fast");
        targets.put("prioritizeChunkUpdates", "0");
        targets.put("maxFps", "260");
        targets.put("screenEffectScale", "0.0");
        targets.put("fovEffectScale", "0.5");

        File backup = new File(folderPath, ".oryn/fps_boost_backup.properties");
        try {
            load(folderPath);
            if (!backup.exists()) {
                File parent = backup.getParentFile();
                if (parent != null) parent.mkdirs();
                Properties saved = new Properties();
                for (String key : targets.keySet()) {
                    String current = get(key);
                    if (current != null) saved.setProperty(key, current);
                }
                try (FileOutputStream out = new FileOutputStream(backup)) {
                    saved.store(out, "OrynLauncher FPS Boost backup");
                }
            }

            boolean changed = false;
            for (Map.Entry<String, String> entry : targets.entrySet()) {
                if (get(entry.getKey()) != null) {
                    set(entry.getKey(), entry.getValue());
                    changed = true;
                }
            }
            if (changed) save();
            Log.i("MCOptionUtils", "Oryn FPS Boost applied to " + folderPath);
        } catch (Throwable e) {
            Log.e("MCOptionUtils", "Failed to apply Oryn FPS Boost", e);
        }
    }

    public static void restoreOrynFpsBoost(@NonNull String folderPath) {
        File backup = new File(folderPath, ".oryn/fps_boost_backup.properties");
        if (!backup.exists()) return;
        try {
            load(folderPath);
            Properties saved = new Properties();
            try (FileInputStream in = new FileInputStream(backup)) {
                saved.load(in);
            }
            for (String key : saved.stringPropertyNames()) {
                set(key, saved.getProperty(key));
            }
            save();
            //noinspection ResultOfMethodCallIgnored
            backup.delete();
            Log.i("MCOptionUtils", "Oryn FPS Boost settings restored");
        } catch (Throwable e) {
            Log.e("MCOptionUtils", "Failed to restore Oryn FPS Boost", e);
        }
    }

    /** Set an array of String, instead of a simple value. Not supported on all options */
    public static void set(String key, List<String> values){
        sParameterMap.put(key, values.toString());
    }

    public static String get(String key){
        return sParameterMap.get(key);
    }

    /** @return A list of values from an array stored as a string */
    public static List<String> getAsList(String key){
        String value = get(key);

        // Fallback if the value doesn't exist
        if (value == null) return new ArrayList<>();

        // Remove the edges
        value = value.replace("[", "").replace("]", "");
        if (value.isEmpty()) return new ArrayList<>();

        return Arrays.asList(value.split(","));
    }

    public static void save() {
        StringBuilder result = new StringBuilder();
        for(String key : sParameterMap.keySet())
            result.append(key)
                    .append(':')
                    .append(sParameterMap.get(key))
                    .append('\n');

        try {
            sFileObserver.stopWatching();
            Tools.write(sOptionFolderPath + "/options.txt", result.toString());
            sFileObserver.startWatching();
        } catch (IOException e) {
            Log.w(Tools.APP_NAME, "Could not save options.txt", e);
        }
    }

    /** @return The stored Minecraft GUI scale, also auto-computed if on auto-mode or improper setting */
    public static int getMcScale() {
        String str = MCOptionUtils.get("guiScale");
        int guiScale = (str == null ? 0 :Integer.parseInt(str));

        int scale = Math.max(Math.min(windowWidth / 320, windowHeight / 240), 1);
        if(scale < guiScale || guiScale == 0){
            guiScale = scale;
        }

        return guiScale;
    }

    /** Add a file observer to reload options on file change
     * Listeners get notified of the change */
    private static void setupFileObserver(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
            sFileObserver = new FileObserver(new File(sOptionFolderPath + "/options.txt"), FileObserver.MODIFY) {
                @Override
                public void onEvent(int i, @Nullable String s) {
                    MCOptionUtils.load();
                    notifyListeners();
                }
            };
        }else{
            sFileObserver = new FileObserver(sOptionFolderPath + "/options.txt", FileObserver.MODIFY) {
                @Override
                public void onEvent(int i, @Nullable String s) {
                    MCOptionUtils.load();
                    notifyListeners();
                }
            };
        }

        sFileObserver.startWatching();
    }

    /** Notify the option listeners */
    public static void notifyListeners(){
        for(WeakReference<MCOptionListener> weakReference : sOptionListeners){
            MCOptionListener optionListener = weakReference.get();
            if(optionListener == null) continue;

            optionListener.onOptionChanged();
        }
    }

    /** Add an option listener, notice how we don't have a reference to it */
    public static void addMCOptionListener(MCOptionListener listener){
        sOptionListeners.add(new WeakReference<>(listener));
    }

    /** Remove a listener from existence, or at least, its reference here */
    public static void removeMCOptionListener(MCOptionListener listener){
        for(WeakReference<MCOptionListener> weakReference : sOptionListeners){
            MCOptionListener optionListener = weakReference.get();
            if(optionListener == null) continue;
            if(optionListener == listener){
                sOptionListeners.remove(weakReference);
                return;
            }
        }
    }

}
