package net.kdt.pojavlaunch.instances;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.kdt.pojavlaunch.download.OrynInstanceContentScanner;

/**
 * Single ownership boundary for OrynLauncher instances.
 *
 * UI code should select instances through Instances, while installers use this
 * manager to create/configure isolated instances. A modpack/content installer
 * must never use the currently selected instance as its storage destination.
 */
public final class OrynInstanceManager {
    private static final String TAG = "OrynInstanceManager";
    private static final List<ContentListener> CONTENT_LISTENERS = new CopyOnWriteArrayList<>();

    public interface ContentListener {
        void onContentInstalled(OrynInstanceContentEvent event);
    }

    public static void addContentListener(ContentListener listener) {
        if (listener != null) CONTENT_LISTENERS.add(listener);
    }

    public static void removeContentListener(ContentListener listener) {
        CONTENT_LISTENERS.remove(listener);
    }

    public static void refreshInstanceContent(final String instanceId) {
        if (instanceId == null || instanceId.trim().isEmpty()) return;
        EXECUTOR.execute(() -> {
            try {
                for (Instance instance : Instances.loadAllInstances()) {
                    if (instanceId.equals(instance.id)) {
                        OrynInstanceContentScanner.refreshAsync(instance);
                        return;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Unable to refresh instance content", e);
            }
        });
    }

    private static final java.util.concurrent.ExecutorService EXECUTOR = java.util.concurrent.Executors.newSingleThreadExecutor();

    public static void publishContentInstalled(OrynInstanceContentEvent event) {
        if (event == null) return;
        refreshInstanceContent(event.instanceId);
        for (ContentListener listener : CONTENT_LISTENERS) {
            try { listener.onContentInstalled(event); } catch (Exception e) { Log.w(TAG, "Content listener failed", e); }
        }
    }

    private OrynInstanceManager() {}

    public static Instance create(String name, String minecraftVersion) throws IOException {
        return create(name, minecraftVersion, false);
    }

    public static Instance create(String name, String minecraftVersion, boolean modpack) throws IOException {
        final String safeName = name == null || name.trim().isEmpty()
                ? "Oryn Instance" : name.trim();
        final String safeMinecraft = minecraftVersion == null ? "" : minecraftVersion.trim();

        Instance instance = Instances.createInstance(i -> {
            i.id = UUID.randomUUID().toString();
            i.name = safeName;
            i.versionId = safeMinecraft;
            i.minecraftVersion = safeMinecraft;
            i.sharedData = false;
            i.isModpack = modpack;
            i.modpackName = modpack ? safeName : null;
            i.createdAt = System.currentTimeMillis();
            i.lastPlayedAt = 0L;
            i.metadata.sanitize();
        }, safeName, modpack);

        // The root created by Instances is the only game directory owned by
        // this instance. Never fall back to shared_dir for new instances.
        assertIsolated(instance);
        instance.write();
        Log.d(TAG, "[ORYN-INSTANCE] created id=" + instance.id
                + " name=" + instance.name
                + " minecraft=" + instance.minecraftVersion
                + " root=" + instance.getGameDirectory().getAbsolutePath());
        return instance;
    }

    public static void configureModpack(Instance instance, String packName,
                                         String packVersion, String projectId,
                                         String minecraftVersion, Map<String, String> dependencies) throws IOException {
        require(instance);
        instance.isModpack = true;
        instance.modpackName = packName;
        instance.modpackVersion = packVersion;
        instance.modpackProjectId = projectId;
        instance.minecraftVersion = minecraftVersion;
        instance.metadata.sanitize();

        if (dependencies != null) {
            String loaderVersion = dependencies.get("fabric-loader");
            if (loaderVersion != null) {
                instance.loaderType = "fabric";
                instance.loaderVersion = loaderVersion;
            } else if ((loaderVersion = dependencies.get("forge")) != null) {
                instance.loaderType = "forge";
                instance.loaderVersion = loaderVersion;
            } else if ((loaderVersion = dependencies.get("neoforge")) != null) {
                instance.loaderType = "neoforge";
                instance.loaderVersion = loaderVersion;
            } else if ((loaderVersion = dependencies.get("quilt-loader")) != null) {
                instance.loaderType = "quilt";
                instance.loaderVersion = loaderVersion;
            } else {
                instance.loaderType = "vanilla";
                instance.loaderVersion = null;
            }
        }
        instance.write();
    }

    public static void configureLoader(Instance instance, String loaderType, String loaderVersion,
                                       String launchVersionId) throws IOException {
        require(instance);
        instance.loaderType = loaderType;
        instance.loaderVersion = loaderVersion;
        if (launchVersionId != null && !launchVersionId.isEmpty()) {
            instance.versionId = launchVersionId;
        }
        instance.write();
    }

    public static void recordInstalledContent(Instance instance, String key, String value) throws IOException {
        require(instance);
        instance.metadata.sanitize();
        instance.metadata.installedContent.put(key, value == null ? "" : value);
        instance.write();
        refreshInstanceContent(instance.id);
    }

    public static void markPlayed(Instance instance) throws IOException {
        require(instance);
        instance.lastPlayedAt = System.currentTimeMillis();
        instance.write();
    }

    public static void select(Instance instance) {
        require(instance);
        assertIsolated(instance);
        Instances.setSelectedInstance(instance);
    }

    public static Instance selected() {
        return Instances.loadSelectedInstance();
    }

    public static void assertIsolated(Instance instance) {
        require(instance);
        File root = instance.getGameDirectory();
        if (!instance.isIsolated()) {
            throw new IllegalStateException("Oryn instance is not isolated: " + instance.name);
        }
        File instancesRoot = new File(net.kdt.pojavlaunch.Tools.DIR_GAME_HOME, "instances");
        try {
            if (!root.getCanonicalPath().startsWith(instancesRoot.getCanonicalPath() + File.separator)) {
                throw new IllegalStateException("Instance escapes Oryn instances directory: " + root);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to validate instance path", e);
        }
    }

    private static void require(Instance instance) {
        if (instance == null) throw new IllegalArgumentException("Instance is null");
        instance.metadata.sanitize();
    }
}
