package net.kdt.pojavlaunch.download;

import android.os.SystemClock;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModrinthApi;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ModrinthInstaller {
    public interface Callback {
        void onState(DownloadState state);
        void onSuccess(ModrinthFile file);
        void onError(Exception error);
    }

    private final ModrinthRepository repository;
    private final InstalledProjectStore installedStore;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ModrinthInstaller(ModrinthRepository repository, InstalledProjectStore installedStore) {
        this.repository = repository;
        this.installedStore = installedStore;
    }

    public void installAsync(final ModrinthProject project,
                             final ModrinthVersion version,
                             final String projectType,
                             final String minecraftVersion,
                             final String loader,
                             final Instance instance,
                             final Callback callback) {
        executor.execute(() -> {
            try {
                callback.onState(DownloadState.checking());
                if (instance == null) throw new Exception("Select an instance first");
                if (minecraftVersion == null || minecraftVersion.isEmpty()) {
                    throw new Exception("Could not determine the Minecraft version");
                }

                String actualMinecraft = minecraftVersionFromInstance(instance.versionId);
                if (!minecraftVersion.equals(actualMinecraft)) {
                    throw new Exception("Selected version does not match the target instance");
                }

                if (("mod".equals(projectType) || "modpack".equals(projectType))
                        && loader != null && !loader.isEmpty()
                        && !loaderMatches(instance, loader)) {
                    throw new Exception("Selected loader does not match the target instance");
                }

                List<ModrinthVersion> compatible = repository.compatibleVersions(
                        java.util.Collections.singletonList(version), projectType, minecraftVersion, loader);
                if (compatible.isEmpty()) throw new Exception("The selected version is no longer compatible");

                ModrinthFile file = repository.selectFile(version, projectType);
                if (file == null) throw new Exception("No downloadable file found");
                String filename = new File(file.filename).getName();
                if (filename.isEmpty()) throw new Exception("Modrinth returned an invalid filename");
                if (!isValidExtension(filename, projectType)) {
                    throw new Exception("Invalid file type for " + projectType);
                }

                File gameDirectory = instance.getGameDirectory();
                if (installedStore.isInstalled(gameDirectory, project.id, projectType)) {
                    callback.onState(DownloadState.installed());
                    callback.onSuccess(file);
                    return;
                }

                if ("modpack".equals(projectType)) {
                    installModpack(project, file, projectType, instance, callback);
                    return;
                }

                File targetDirectory = targetDirectory(gameDirectory, projectType);
                if (!targetDirectory.isDirectory() && !targetDirectory.mkdirs()) {
                    throw new Exception("Could not create " + targetDirectory.getName() + " folder");
                }

                File output = new File(targetDirectory, filename);
                if (output.isFile()) {
                    installedStore.markInstalled(gameDirectory, project.id, projectType, filename);
                    callback.onState(DownloadState.installed());
                    callback.onSuccess(file);
                    return;
                }

                download(file, output, callback, project.title);
                callback.onState(DownloadState.installing());
                installedStore.markInstalled(gameDirectory, project.id, projectType, filename);
                callback.onState(DownloadState.installed());
                callback.onSuccess(file);
            } catch (Exception e) {
                callback.onState(DownloadState.failed(e.getMessage() == null ? "Download failed" : e.getMessage()));
                callback.onError(e);
            }
        });
    }

    private void installModpack(ModrinthProject project, ModrinthFile file, String projectType,
                                Instance instance, Callback callback) throws Exception {
        File cacheDirectory = Tools.DIR_CACHE;
        if (!cacheDirectory.isDirectory() && !cacheDirectory.mkdirs()) {
            throw new Exception("Could not create download cache");
        }
        String filename = new File(file.filename).getName();
        File temp = new File(cacheDirectory, "oryn-" + project.id + "-" + filename + ".part");
        if (temp.exists()) temp.delete();

        download(file, temp, callback, project.title);
        callback.onState(DownloadState.installing());

        try {
            new ModrinthApi().installMrpackIntoExistingInstance(temp, instance, null);
            installedStore.markInstalled(instance.getGameDirectory(), project.id, projectType, filename);
        } finally {
            if (temp.exists()) temp.delete();
        }

        callback.onState(DownloadState.installed());
        callback.onSuccess(file);
    }

    private void download(ModrinthFile file, File output, Callback callback, String title) throws Exception {
        File parent = output.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new Exception("Could not create download directory");
        }

        Exception last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            if (Thread.currentThread().isInterrupted()) throw new Exception("Download cancelled");
            File temp = new File(parent, output.getName() + ".part");
            if (temp.exists()) temp.delete();

            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(file.url).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "*/*");
                connection.setRequestProperty("User-Agent",
                        "Orynlauncher-Official/OrynLauncher/4.1 (https://github.com/Orynlauncher-Official/OrynLauncher)");
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) throw new Exception("HTTP " + status);

                long total = connection.getContentLengthLong() > 0
                        ? connection.getContentLengthLong() : file.size;
                long done = 0L;
                long lastUi = 0L;
                MessageDigest digest = MessageDigest.getInstance("SHA-1");

                try (InputStream in = new BufferedInputStream(connection.getInputStream());
                     FileOutputStream out = new FileOutputStream(temp)) {
                    byte[] buffer = new byte[32768];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) throw new Exception("Download cancelled");
                        out.write(buffer, 0, read);
                        digest.update(buffer, 0, read);
                        done += read;
                        long now = SystemClock.elapsedRealtime();
                        if (now - lastUi > 150 || (total > 0 && done >= total)) {
                            int percent = total > 0 ? (int)Math.min(100, done * 100L / total) : 0;
                            callback.onState(DownloadState.downloading(percent));
                            lastUi = now;
                        }
                    }
                    out.flush();
                }

                if (file.size > 0 && temp.length() != file.size) {
                    throw new Exception("Downloaded file size does not match Modrinth metadata");
                }
                if (file.sha1 != null && !file.sha1.isEmpty()) {
                    String actual = hex(digest.digest());
                    if (!file.sha1.equalsIgnoreCase(actual)) throw new Exception("SHA-1 verification failed");
                }

                if (output.exists() && !output.delete()) throw new Exception("Could not replace existing file");
                if (!temp.renameTo(output)) throw new Exception("Could not finalize downloaded file");
                return;
            } catch (Exception e) {
                last = e;
                if (temp.exists()) temp.delete();
                if (attempt < 3) {
                    try { Thread.sleep(500L * attempt); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new Exception("Download cancelled", interrupted);
                    }
                }
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        throw last == null ? new Exception("Download failed") : last;
    }

    private File targetDirectory(File gameDirectory, String projectType) {
        if ("mod".equals(projectType)) return new File(gameDirectory, "mods");
        if ("resourcepack".equals(projectType)) return new File(gameDirectory, "resourcepacks");
        if ("shader".equals(projectType)) return new File(gameDirectory, "shaderpacks");
        return gameDirectory;
    }

    private boolean isValidExtension(String filename, String projectType) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if ("mod".equals(projectType)) return lower.endsWith(".jar");
        if ("resourcepack".equals(projectType)) return lower.endsWith(".zip");
        if ("shader".equals(projectType)) return lower.endsWith(".zip");
        if ("modpack".equals(projectType)) return lower.endsWith(".mrpack");
        return false;
    }

    private boolean loaderMatches(Instance instance, String wanted) {
        String detected = detectLoader(instance);
        return detected != null && wanted.equalsIgnoreCase(detected);
    }

    private String detectLoader(Instance instance) {
        if (instance == null) return null;
        String id = instance.versionId == null ? "" : instance.versionId.toLowerCase(Locale.ROOT);
        if (id.contains("neoforge")) return "neoforge";
        if (id.contains("forge")) return "forge";
        if (id.contains("fabric")) return "fabric";
        if (id.contains("quilt")) return "quilt";
        if (instance.installer != null) {
            String url = instance.installer.installerDownloadUrl;
            if (url != null) {
                String value = url.toLowerCase(Locale.ROOT);
                if (value.contains("neoforge")) return "neoforge";
                if (value.contains("forge")) return "forge";
                if (value.contains("fabric")) return "fabric";
                if (value.contains("quilt")) return "quilt";
            }
            if (instance.installer.commandLineArgs != null) {
                for (String arg : instance.installer.commandLineArgs) {
                    if (arg == null) continue;
                    String value = arg.toLowerCase(Locale.ROOT);
                    if (value.contains("neoforge")) return "neoforge";
                    if (value.contains("forge")) return "forge";
                    if (value.contains("fabric")) return "fabric";
                    if (value.contains("quilt")) return "quilt";
                }
            }
        }
        return null;
    }

    private String minecraftVersionFromInstance(String versionId) {
        if (versionId == null || versionId.trim().isEmpty()) return null;
        String id = versionId.trim();
        if (id.startsWith("fabric-loader-")) {
            int dash = id.lastIndexOf('-');
            if (dash >= 0 && dash + 1 < id.length()) return id.substring(dash + 1);
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^(\\d+\\.\\d+(?:\\.\\d+)?)").matcher(id);
        if (matcher.find()) return matcher.group(1);
        return id;
    }

    private String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return out.toString();
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
