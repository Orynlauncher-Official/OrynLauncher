package net.kdt.pojavlaunch.modloaders.modpacks.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kdt.mcgui.ProgressLayout;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.InstanceInstaller;
import net.kdt.pojavlaunch.instances.OrynInstanceManager;
import net.kdt.pojavlaunch.downloader.Downloader;
import net.kdt.pojavlaunch.downloader.TaskMetadata;
import net.kdt.pojavlaunch.mirrors.DownloadMirror;
import net.kdt.pojavlaunch.modloaders.FabriclikeUtils;
import net.kdt.pojavlaunch.modloaders.ForgelikeUtils;
import net.kdt.pojavlaunch.modloaders.Lwjgl3ifyUtils;
import net.kdt.pojavlaunch.modloaders.modpacks.api.modloader.FabriclikeLoaderInstaller;
import net.kdt.pojavlaunch.modloaders.modpacks.api.modloader.ForgelikeLoaderInstaller;
import net.kdt.pojavlaunch.modloaders.modpacks.api.modloader.LoaderInstaller;
import net.kdt.pojavlaunch.modloaders.modpacks.api.modloader.Lwjgl3ifyLoaderInstaller;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModrinthIndex;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchResult;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.ModIconCache;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.utils.ZipUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;

public class ModrinthApi implements ModpackApi{
    private final ApiHandler mApiHandler;
    public ModrinthApi(){
        mApiHandler = new ApiHandler("https://api.modrinth.com/v2");
    }

    @Override
    public SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult) {
        ModrinthSearchResult modrinthSearchResult = (ModrinthSearchResult) previousPageResult;

        // Fixes an issue where the offset being equal or greater than total_hits is ignored
        if (modrinthSearchResult != null && modrinthSearchResult.previousOffset >= modrinthSearchResult.totalResultCount) {
            ModrinthSearchResult emptyResult = new ModrinthSearchResult();
            emptyResult.results = new ModItem[0];
            emptyResult.totalResultCount = modrinthSearchResult.totalResultCount;
            emptyResult.previousOffset = modrinthSearchResult.previousOffset;
            return emptyResult;
        }


        // Build the facets filters
        HashMap<String, Object> params = new HashMap<>();
        StringBuilder facetString = new StringBuilder();
        facetString.append("[");
        facetString.append(String.format("[\"project_type:%s\"]", searchFilters.isModpack ? "modpack" : "mod"));
        if(searchFilters.mcVersion != null && !searchFilters.mcVersion.isEmpty())
            facetString.append(String.format(",[\"versions:%s\"]", searchFilters.mcVersion));
        facetString.append("]");
        params.put("facets", facetString.toString());
        params.put("query", searchFilters.name);
        params.put("limit", 50);
        params.put("index", "relevance");
        if(modrinthSearchResult != null)
            params.put("offset", modrinthSearchResult.previousOffset);

        JsonObject response = mApiHandler.get("search", params, JsonObject.class);
        if(response == null) return null;
        JsonArray responseHits = response.getAsJsonArray("hits");
        if(responseHits == null) return null;

        ModItem[] items = new ModItem[responseHits.size()];
        for(int i=0; i<responseHits.size(); ++i){
            JsonObject hit = responseHits.get(i).getAsJsonObject();
            items[i] = new ModItem(
                    Constants.SOURCE_MODRINTH,
                    hit.get("project_type").getAsString().equals("modpack"),
                    hit.get("project_id").getAsString(),
                    hit.get("title").getAsString(),
                    hit.get("description").getAsString(),
                    hit.get("icon_url").getAsString()
            );
        }
        if(modrinthSearchResult == null) modrinthSearchResult = new ModrinthSearchResult();
        modrinthSearchResult.previousOffset += responseHits.size();
        modrinthSearchResult.results = items;
        modrinthSearchResult.totalResultCount = response.get("total_hits").getAsInt();
        return modrinthSearchResult;
    }

    @Override
    public ModDetail getModDetails(ModItem item) {

        JsonArray response = mApiHandler.get(String.format("project/%s/version", item.id), JsonArray.class);
        if(response == null) return null;
        System.out.println(response);
        // Expand each release across every Minecraft version it supports.
        // Modrinth can list multiple game versions for one release; only reading
        // game_versions[0] caused valid packs to fail the version selector.
        java.util.ArrayList<String> namesList = new java.util.ArrayList<>();
        java.util.ArrayList<String> mcNamesList = new java.util.ArrayList<>();
        java.util.ArrayList<String> urlsList = new java.util.ArrayList<>();
        java.util.ArrayList<String> hashesList = new java.util.ArrayList<>();

        for (int i = 0; i < response.size(); ++i) {
            JsonObject version = response.get(i).getAsJsonObject();
            if (!version.has("game_versions") || !version.get("game_versions").isJsonArray()
                    || version.getAsJsonArray("game_versions").size() == 0) continue;
            if (!version.has("files") || !version.get("files").isJsonArray()
                    || version.getAsJsonArray("files").size() == 0) continue;

            JsonObject file = version.getAsJsonArray("files").get(0).getAsJsonObject();
            for (int f = 0; f < version.getAsJsonArray("files").size(); f++) {
                JsonObject candidate = version.getAsJsonArray("files").get(f).getAsJsonObject();
                if (candidate.has("primary") && candidate.get("primary").getAsBoolean()) {
                    file = candidate;
                    break;
                }
            }

            String url = file.has("url") ? file.get("url").getAsString() : null;
            if (url == null || url.isEmpty()) continue;

            String hash = null;
            if (file.has("hashes") && file.get("hashes").isJsonObject()
                    && file.getAsJsonObject("hashes").has("sha1")) {
                hash = file.getAsJsonObject("hashes").get("sha1").getAsString();
            }

            JsonArray gameVersions = version.getAsJsonArray("game_versions");
            for (int v = 0; v < gameVersions.size(); v++) {
                namesList.add(version.get("name").getAsString());
                mcNamesList.add(gameVersions.get(v).getAsString());
                urlsList.add(url);
                hashesList.add(hash);
            }
        }

        String[] names = namesList.toArray(new String[0]);
        String[] mcNames = mcNamesList.toArray(new String[0]);
        String[] urls = urlsList.toArray(new String[0]);
        String[] hashes = hashesList.toArray(new String[0]);

        return new ModDetail(item, names, mcNames, urls, hashes);
    }

    /**
     * Install an already-downloaded .mrpack as a NEW launcher instance.
     * The currently selected instance is never used as the installation destination.
     */
    public Instance installMrpackAsNewInstance(File modpackFile, String icon) throws IOException {
        if (modpackFile == null || !modpackFile.isFile() || modpackFile.length() <= 0) {
            throw new IOException("Modpack file is missing or empty");
        }

        ModrinthIndex index;
        try (ZipFile zip = new ZipFile(modpackFile)) {
            java.io.InputStream stream = ZipUtils.getEntryStream(zip, "modrinth.index.json");
            if (stream == null) throw new IOException("Invalid modpack: modrinth.index.json is missing");
            index = Tools.GLOBAL_GSON.fromJson(Tools.read(stream), ModrinthIndex.class);
        }

        if (index == null || index.name == null || index.name.trim().isEmpty()) {
            throw new IOException("Invalid modpack: pack name is missing");
        }
        if (index.dependencies == null) {
            throw new IOException("Invalid modpack: dependencies are missing");
        }
        String minecraftVersion = index.dependencies.get("minecraft");
        if (minecraftVersion == null || minecraftVersion.trim().isEmpty()) {
            throw new IOException("Invalid modpack: Minecraft version is missing");
        }

        final String packName = index.name.trim();
        final String packVersion = index.versionId == null ? "" : index.versionId.trim();
        final File[] createdRoot = new File[1];

        Instance instance = OrynInstanceManager.create(packName, minecraftVersion, true);
        OrynInstanceManager.configureModpack(instance, packName, packVersion, null,
                minecraftVersion, index.dependencies);
        createdRoot[0] = instance.getGameDirectory();

        try {
            LoaderInstaller loaderInstaller = installMrpack(modpackFile, instance.getGameDirectory());
            if (loaderInstaller == null) {
                throw new IOException("Unknown modpack mod loader information");
            }
            if (index.dependencies.containsKey("fabric-loader")) {
                OrynInstanceManager.configureLoader(instance, "fabric",
                        index.dependencies.get("fabric-loader"), null);
            } else if (index.dependencies.containsKey("forge")) {
                OrynInstanceManager.configureLoader(instance, "forge",
                        index.dependencies.get("forge"), null);
            } else if (index.dependencies.containsKey("neoforge")) {
                OrynInstanceManager.configureLoader(instance, "neoforge",
                        index.dependencies.get("neoforge"), null);
            } else if (index.dependencies.containsKey("quilt-loader")) {
                OrynInstanceManager.configureLoader(instance, "quilt",
                        index.dependencies.get("quilt-loader"), null);
            }

            if (loaderInstaller.requiresGuiInstallation()) {
                InstanceInstaller instanceInstaller = loaderInstaller.createInstaller();
                if (instanceInstaller == null) throw new IOException("Failed to prepare data for instance installation");
                instance.installer = instanceInstaller;
            } else {
                String versionId = loaderInstaller.installHeadlessly();
                if (versionId == null) throw new IOException("Unknown mod loader version");
                instance.versionId = versionId;
                String loaderType = instance.loaderType == null ? "vanilla" : instance.loaderType;
                OrynInstanceManager.configureLoader(instance, loaderType,
                        instance.loaderVersion, versionId);
            }
            instance.write();
            ModIconCache.writeInstanceImage(instance, icon);
            if (loaderInstaller.requiresGuiInstallation()) instance.installer.start();

            android.util.Log.d("OrynDownload", "[ORYN-MODPACK] name=" + packName
                    + " packVersion=" + packVersion
                    + " minecraft=" + minecraftVersion
                    + " loader=" + index.dependencies
                    + " newInstance=" + instance.getGameDirectory().getAbsolutePath());
            return instance;
        } catch (Exception error) {
            try { net.kdt.pojavlaunch.instances.Instances.removeInstance(instance); } catch (Exception ignored) { }
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("Modpack installation failed", error);
        } finally {
            if (modpackFile.exists()) modpackFile.delete();
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
        }
    }

    /**
     * Legacy compatibility entry point.
     *
     * Complete .mrpack files are full instances. The target instance argument
     * is intentionally ignored so old callers cannot mutate another instance.
     */
    @Deprecated
    public LoaderInstaller installMrpackIntoExistingInstance(File modpackFile, Instance ignoredTargetInstance, String icon) throws IOException {
        Instance created = installMrpackAsNewInstance(modpackFile, icon);
        net.kdt.pojavlaunch.instances.OrynInstanceManager.select(created);
        // Legacy callers cannot safely consume the new instance-owned GUI installer.\n        // The new architecture owns the installer through the created instance.\n        return null;
    }

    public LoaderInstaller installModpack(ModDetail modDetail, int selectedVersion) throws IOException{
        //TODO considering only modpacks for now
        return ModpackInstaller.downloadModpack(modDetail, selectedVersion, this::installMrpack);
    }

    public LoaderInstaller installLocalModpack(String modpackName, File modpackFile, String icon) throws IOException {
        return ModpackInstaller.installModpack(modpackName, modpackName, modpackFile, icon, this::installMrpack);
    }

    private static LoaderInstaller createInfo(ModrinthIndex modrinthIndex, File installDestination) throws IOException {
        if(modrinthIndex == null) return null;
        Map<String, String> dependencies = modrinthIndex.dependencies;
        String mcVersion = dependencies.get("minecraft");
        if(mcVersion == null) return null;
        String modLoaderVersion;
        if((modLoaderVersion = dependencies.get("forge")) != null) {
            return new ForgelikeLoaderInstaller(ForgelikeUtils.FORGE_UTILS, mcVersion, modLoaderVersion);
        } else if((modLoaderVersion = dependencies.get("fabric-loader")) != null) {
            return new FabriclikeLoaderInstaller(FabriclikeUtils.FABRIC_UTILS, mcVersion, modLoaderVersion);
        } else if((modLoaderVersion = dependencies.get("quilt-loader")) != null) {
            return new FabriclikeLoaderInstaller(FabriclikeUtils.QUILT_UTILS, mcVersion, modLoaderVersion);
        } else if((modLoaderVersion = dependencies.get("neoforge")) != null) {
            return new ForgelikeLoaderInstaller(ForgelikeUtils.NEOFORGE_UTILS, mcVersion, modLoaderVersion);
        } else if(dependencies.size() == 1) {
            // "Vanilla" pack. Possibly GT:NH, let's try to detect lwjgl3ify
            File lwjgl3ifyJar = Lwjgl3ifyUtils.detectLwjgl3ifyJar(installDestination);
            if(lwjgl3ifyJar != null) return new Lwjgl3ifyLoaderInstaller(lwjgl3ifyJar);
        }

        return null;
    }

    private LoaderInstaller installMrpack(File mrpackFile, File instanceDestination) throws IOException {
        try (ZipFile modpackZipFile = new ZipFile(mrpackFile)){
            ModrinthIndex modrinthIndex = Tools.GLOBAL_GSON.fromJson(
                    Tools.read(ZipUtils.getEntryStream(modpackZipFile, "modrinth.index.json")),
                    ModrinthIndex.class);
            try {
                new ModrinthDownloader().startDownloads(modrinthIndex.files, instanceDestination);
            }catch (InterruptedException e) {
                throw new IOException("NIY: InterruptedException", e);
            }
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.modpack_download_applying_overrides, 1, 2);
            ZipUtils.zipExtract(modpackZipFile, "overrides/", instanceDestination);
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 50, R.string.modpack_download_applying_overrides, 2, 2);
            ZipUtils.zipExtract(modpackZipFile, "client-overrides/", instanceDestination);
            return createInfo(modrinthIndex, instanceDestination);
        }
    }

    class ModrinthSearchResult extends SearchResult {
        int previousOffset;
    }

    static class ModrinthDownloader extends Downloader {
        public ModrinthDownloader() {
            super(ProgressLayout.INSTALL_MODPACK);
        }

        protected void startDownloads(ModrinthIndex.ModrinthIndexFile[] indexFiles, File instanceDestination) throws IOException, InterruptedException {
            String absoluteInstancePath = instanceDestination.getAbsolutePath();
            ArrayList<TaskMetadata> taskMetadatas = new ArrayList<>(indexFiles.length);
            for(ModrinthIndex.ModrinthIndexFile file : indexFiles) {
                File targetPath = new File(instanceDestination, file.path);
                if(!targetPath.getAbsolutePath().startsWith(absoluteInstancePath)) throw new IOException("Bad path!");
                FileUtils.ensureParentDirectory(targetPath);
                taskMetadatas.add(new TaskMetadata(
                        targetPath, new URL(file.downloads[0]), // TODO source selection
                        file.fileSize, file.hashes.sha1,
                        DownloadMirror.DOWNLOAD_CLASS_NONE
                ));
            }
            runDownloads(taskMetadatas);
        }
    }
}
