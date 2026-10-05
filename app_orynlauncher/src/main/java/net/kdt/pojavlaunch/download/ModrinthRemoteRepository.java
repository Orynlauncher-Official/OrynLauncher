package net.kdt.pojavlaunch.download;

import android.graphics.Bitmap;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class ModrinthRemoteRepository {
    private static final String TAG = "OrynDownload";
    private static final long CACHE_MS = 5 * 60 * 1000L;

    public interface SearchCallback {
        void onLoading();
        void onSuccess(ModrinthSearchResult result, boolean append);
        void onError(Exception error);
    }

    public interface DetailsCallback {
        void onLoading();
        void onSuccess(ModrinthProject project, List<ModrinthVersion> compatibleVersions);
        void onError(Exception error);
    }

    public interface IconCallback {
        void onSuccess(Bitmap bitmap);
        void onError();
    }

    public interface ValuesCallback {
        void onSuccess(List<String> values);
        void onError(Exception error);
    }

    private static final class CacheEntry {
        final long created;
        final ModrinthSearchResult result;
        CacheEntry(long created, ModrinthSearchResult result) {
            this.created = created;
            this.result = result;
        }
    }

    private static final class ValuesEntry {
        final long created;
        final List<String> values;
        ValuesEntry(long created, List<String> values) {
            this.created = created;
            this.values = values;
        }
    }

    private static final class DetailEntry {
        final long created;
        final ModrinthProject project;
        final List<ModrinthVersion> versions;
        DetailEntry(long created, ModrinthProject project, List<ModrinthVersion> versions) {
            this.created = created;
            this.project = project;
            this.versions = versions;
        }
    }

    private final ModrinthApiService api;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, CacheEntry> searchCache = new HashMap<>();
    private final Map<String, Bitmap> iconCache = new HashMap<>();
    private final Map<String, DetailEntry> detailCache = new HashMap<>();
    private final Map<String, ValuesEntry> valuesCache = new HashMap<>();
    private Future<?> activeSearch;

    public ModrinthRemoteRepository() {
        api = new ModrinthApiService();
    }

    public synchronized void cancelSearch() {
        if (activeSearch != null) {
            activeSearch.cancel(true);
            activeSearch = null;
        }
    }

    public synchronized void searchAsync(final String query, final String projectType,
                                         final String minecraftVersion, final String loader,
                                         final int offset, final SearchCallback callback) {
        cancelSearch();
        callback.onLoading();

        final String key = projectType + "|" + safe(query).toLowerCase(Locale.ROOT)
                + "|" + safe(minecraftVersion) + "|" + safe(loader) + "|" + offset;
        CacheEntry cached = searchCache.get(key);
        if (cached != null && System.currentTimeMillis() - cached.created < CACHE_MS) {
            Log.d(TAG, "Cache hit: " + key);
            callback.onSuccess(cached.result, offset > 0);
            return;
        }

        activeSearch = executor.submit(() -> {
            try {
                ModrinthSearchResult raw = api.search(query, projectType, minecraftVersion, loader, offset, 20);
                List<ModrinthProject> validProjects = new ArrayList<>();
                for (ModrinthProject project : raw.projects) {
                    if (project != null && projectType != null
                            && projectType.equalsIgnoreCase(project.projectType)) {
                        validProjects.add(project);
                    } else if (project != null) {
                        Log.d(TAG, "Repository rejected project " + project.id
                                + " type=" + project.projectType + " expected=" + projectType);
                    }
                }
                ModrinthSearchResult result = new ModrinthSearchResult(
                        validProjects, raw.offset, raw.totalHits);
                Log.d(TAG, "Repository received valid projects = " + result.projects.size()
                        + " • totalHits=" + result.totalHits);
                searchCache.put(key, new CacheEntry(System.currentTimeMillis(), result));
                callback.onSuccess(result, offset > 0);
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) return;
                callback.onError(e);
            }
        });
    }

    public void loadProjectDetailsAsync(final ModrinthProject selected,
                                        final String projectType,
                                        final String minecraftVersion,
                                        final String loader,
                                        final DetailsCallback callback) {
        callback.onLoading();
        final String key = selected.id + "|" + safe(projectType) + "|" + safe(minecraftVersion) + "|" + safe(loader);
        synchronized (this) {
            DetailEntry cached = detailCache.get(key);
            if (cached != null && System.currentTimeMillis() - cached.created < CACHE_MS) {
                Log.d(TAG, "Detail cache hit: " + key);
                callback.onSuccess(cached.project, cached.versions);
                return;
            }
        }
        executor.execute(() -> {
            try {
                ModrinthProject fullProject = api.getProject(selected.id);
                if (projectType == null || !projectType.equalsIgnoreCase(fullProject.projectType)) {
                    throw new Exception("Modrinth project type mismatch: expected "
                            + projectType + " but received " + fullProject.projectType);
                }
                List<ModrinthVersion> versions = compatibleVersions(
                        api.getProjectVersions(selected.id, minecraftVersion, loader), projectType, minecraftVersion, loader);
                Log.d(TAG, "Project detail: " + selected.id + " • compatible versions=" + versions.size());
                synchronized (this) {
                    detailCache.put(key, new DetailEntry(System.currentTimeMillis(), fullProject, versions));
                }
                callback.onSuccess(fullProject, versions);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    public void loadGameVersionsAsync(final ValuesCallback callback) {
        final String key = "game_versions";
        synchronized (this) {
            ValuesEntry cached = valuesCache.get(key);
            if (cached != null && System.currentTimeMillis() - cached.created < CACHE_MS) {
                callback.onSuccess(new ArrayList<>(cached.values));
                return;
            }
        }
        executor.execute(() -> {
            try {
                List<String> values = api.getGameVersions();
                synchronized (this) { valuesCache.put(key, new ValuesEntry(System.currentTimeMillis(), new ArrayList<>(values))); }
                callback.onSuccess(values);
            } catch (Exception e) { callback.onError(e); }
        });
    }

    public void loadLoadersAsync(final ValuesCallback callback) {
        final String key = "loaders";
        synchronized (this) {
            ValuesEntry cached = valuesCache.get(key);
            if (cached != null && System.currentTimeMillis() - cached.created < CACHE_MS) {
                callback.onSuccess(new ArrayList<>(cached.values));
                return;
            }
        }
        executor.execute(() -> {
            try {
                List<String> values = api.getLoadersForContent();
                synchronized (this) { valuesCache.put(key, new ValuesEntry(System.currentTimeMillis(), new ArrayList<>(values))); }
                callback.onSuccess(values);
            } catch (Exception e) { callback.onError(e); }
        });
    }

    public void loadIconAsync(final String url, final IconCallback callback) {
        if (url == null || url.trim().isEmpty()) {
            callback.onError();
            return;
        }
        synchronized (iconCache) {
            Bitmap cached = iconCache.get(url);
            if (cached != null && !cached.isRecycled()) {
                callback.onSuccess(cached);
                return;
            }
        }
        executor.execute(() -> {
            try {
                Bitmap bitmap = api.loadBitmap(url);
                if (bitmap == null) throw new Exception("No bitmap");
                synchronized (iconCache) { iconCache.put(url, bitmap); }
                callback.onSuccess(bitmap);
            } catch (Exception e) {
                callback.onError();
            }
        });
    }

    public List<ModrinthVersion> compatibleVersions(List<ModrinthVersion> versions,
                                                     String projectType,
                                                     String minecraftVersion,
                                                     String loader) {
        List<ModrinthVersion> result = new ArrayList<>();
        if (versions == null || minecraftVersion == null || minecraftVersion.isEmpty()) return result;

        for (ModrinthVersion version : versions) {
            if (!containsIgnoreCase(version.gameVersions, minecraftVersion)) continue;
            if (("mod".equals(projectType) || "modpack".equals(projectType))
                    && loader != null && !loader.isEmpty()
                    && !containsIgnoreCase(version.loaders, loader)) continue;
            if (selectFile(version, projectType) == null) continue;
            result.add(version);
        }

        // Keep the best release first, while preserving Modrinth's ordering.
        for (int i = 1; i < result.size(); i++) {
            if (result.get(i).featured && !result.get(0).featured) {
                ModrinthVersion featured = result.remove(i);
                result.add(0, featured);
                break;
            }
        }
        return result;
    }

    public ModrinthFile selectFile(ModrinthVersion version, String projectType) {
        if (version == null || version.files == null) return null;
        ModrinthFile fallback = null;
        for (ModrinthFile file : version.files) {
            if (file == null || file.url == null || file.url.isEmpty()) continue;
            if (!isValidExtension(file.filename, projectType)) continue;
            if (file.primary) return file;
            if (fallback == null) fallback = file;
        }
        return fallback;
    }

    private boolean isValidExtension(String filename, String projectType) {
        String lower = safe(filename).toLowerCase(Locale.ROOT);
        if ("mod".equals(projectType)) return lower.endsWith(".jar");
        if ("resourcepack".equals(projectType)) return lower.endsWith(".zip");
        if ("shader".equals(projectType)) return lower.endsWith(".zip");
        if ("modpack".equals(projectType)) return lower.endsWith(".mrpack");
        return false;
    }

    public boolean isDuplicate(List<ModrinthProject> current, String projectId) {
        for (ModrinthProject project : current) {
            if (projectId.equals(project.id)) return true;
        }
        return false;
    }

    public void shutdown() {
        cancelSearch();
        executor.shutdownNow();
        synchronized (iconCache) {
            iconCache.clear();
        }
        synchronized (this) {
            searchCache.clear();
            detailCache.clear();
            valuesCache.clear();
        }
    }

    private static boolean containsIgnoreCase(List<String> values, String wanted) {
        for (String value : values) if (wanted.equalsIgnoreCase(value)) return true;
        return false;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
