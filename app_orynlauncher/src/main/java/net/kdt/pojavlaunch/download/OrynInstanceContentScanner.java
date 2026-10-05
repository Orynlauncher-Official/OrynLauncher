package net.kdt.pojavlaunch.download;

import android.os.Handler;
import android.os.Looper;

import net.kdt.pojavlaunch.instances.Instance;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class OrynInstanceContentScanner {
    public enum ContentType {
        MODS, RESOURCE_PACKS, SHADERS, SAVES
    }

    public static final class ContentItem {
        public final String name;
        public final String fileName;
        public final long size;
        public final boolean enabled;

        public ContentItem(String name, String fileName, long size, boolean enabled) {
            this.name = name;
            this.fileName = fileName;
            this.size = size;
            this.enabled = enabled;
        }
    }

    public static final class State {
        public final String instanceId;
        public final Map<ContentType, List<ContentItem>> items;
        public final long scannedAt;

        private State(String instanceId, Map<ContentType, List<ContentItem>> items) {
            this.instanceId = instanceId;
            this.items = Collections.unmodifiableMap(items);
            this.scannedAt = System.currentTimeMillis();
        }

        public List<ContentItem> get(ContentType type) {
            List<ContentItem> result = items.get(type);
            return result == null ? Collections.<ContentItem>emptyList() : result;
        }

        public int count(ContentType type) {
            return get(type).size();
        }
    }

    public interface Listener {
        void onContentStateChanged(State state);
    }

    private static final Map<String, State> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, List<Listener>> LISTENERS = new ConcurrentHashMap<>();
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private OrynInstanceContentScanner() {}

    public static State getCached(String instanceId) {
        return instanceId == null ? null : CACHE.get(instanceId);
    }

    public static void observe(String instanceId, Listener listener) {
        if (instanceId == null || listener == null) return;
        LISTENERS.computeIfAbsent(instanceId, k -> Collections.synchronizedList(new ArrayList<Listener>())).add(listener);
        State cached = CACHE.get(instanceId);
        if (cached != null) MAIN.post(() -> listener.onContentStateChanged(cached));
    }

    public static void removeObserver(String instanceId, Listener listener) {
        List<Listener> listeners = LISTENERS.get(instanceId);
        if (listeners != null) {
            listeners.remove(listener);
            if (listeners.isEmpty()) LISTENERS.remove(instanceId);
        }
    }

    public static void refreshAsync(final Instance instance) {
        if (instance == null || instance.id == null) return;
        EXECUTOR.execute(() -> {
            State state = scan(instance);
            CACHE.put(instance.id, state);
            publish(instance.id, state);
        });
    }

    public static State scan(Instance instance) {
        Map<ContentType, List<ContentItem>> result = new LinkedHashMap<>();
        result.put(ContentType.MODS, scanDirectory(new File(instance.getGameDirectory(), "mods"), ".jar"));
        result.put(ContentType.RESOURCE_PACKS, scanDirectory(new File(instance.getGameDirectory(), "resourcepacks"), ".zip"));
        result.put(ContentType.SHADERS, scanDirectory(new File(instance.getGameDirectory(), "shaderpacks"), ".zip"));
        result.put(ContentType.SAVES, scanDirectory(new File(instance.getGameDirectory(), "saves"), null));
        return new State(instance.id, result);
    }

    public static void invalidate(String instanceId, ContentType type) {
        if (instanceId == null) return;
        State old = CACHE.get(instanceId);
        if (old == null) return;
        Map<ContentType, List<ContentItem>> copy = new LinkedHashMap<>(old.items);
        copy.put(type, Collections.<ContentItem>emptyList());
        CACHE.put(instanceId, new State(instanceId, copy));
    }

    public static void clear(String instanceId) {
        if (instanceId != null) CACHE.remove(instanceId);
    }

    private static List<ContentItem> scanDirectory(File directory, String extension) {
        if (!directory.isDirectory()) return Collections.emptyList();
        File[] files = directory.listFiles();
        if (files == null) return Collections.emptyList();
        ArrayList<ContentItem> result = new ArrayList<>();
        for (File file : files) {
            if (file == null || !file.isFile()) continue;
            String name = file.getName();
            if (extension != null && !name.toLowerCase(java.util.Locale.ROOT).endsWith(extension)) continue;
            result.add(new ContentItem(displayName(name), name, file.length(), true));
        }
        Collections.sort(result, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return Collections.unmodifiableList(result);
    }

    private static String displayName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static void publish(String instanceId, State state) {
        List<Listener> listeners = LISTENERS.get(instanceId);
        if (listeners == null) return;
        List<Listener> snapshot;
        synchronized (listeners) {
            snapshot = new ArrayList<>(listeners);
        }
        MAIN.post(() -> {
            for (Listener listener : snapshot) listener.onContentStateChanged(state);
        });
    }
}
