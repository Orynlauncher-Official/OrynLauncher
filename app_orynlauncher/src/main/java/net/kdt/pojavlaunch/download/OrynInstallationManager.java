package net.kdt.pojavlaunch.download;

import android.util.Log;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModrinthApi;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public final class OrynInstallationManager {
    private static final String TAG = "OrynDownload";
    private final InstalledProjectStore installedStore;

    public OrynInstallationManager(InstalledProjectStore installedStore) {
        this.installedStore = installedStore;
    }

    public void install(ModrinthProject project, ModrinthVersion version, String projectType,
                        ModrinthFile sourceFile, Instance instance, File downloadedFile) throws Exception {
        if (instance == null) throw new Exception("Select an instance before downloading");
        if (project == null || version == null || sourceFile == null) {
            throw new Exception("Invalid project or version");
        }

        File gameDirectory = instance.getGameDirectory();
        if (gameDirectory == null) throw new Exception("Selected instance has no game directory");
        String filename = new File(sourceFile.filename).getName();
        if (filename.contains("..") || filename.isEmpty()) throw new Exception("Invalid destination filename");
        Log.d(TAG, "[ORYN-DOWNLOAD] type=" + projectType + " project=" + project.id
                + " version=" + version.id + " instance=" + gameDirectory.getAbsolutePath());
        if (filename.isEmpty()) throw new Exception("Modrinth returned an invalid filename");

        if ("modpack".equals(projectType)) {
            try {
                new ModrinthApi().installMrpackIntoExistingInstance(downloadedFile, instance, null);
                if (!gameDirectory.isDirectory()) throw new Exception("Modpack installation did not create the instance directory");
                installedStore.markInstalled(gameDirectory, project.id, projectType, filename);
                Log.d(TAG, "[ORYN-DOWNLOAD] destination=" + gameDirectory.getAbsolutePath()
                        + " downloaded=true installed=true fileExists=true");
            } finally {
                if (downloadedFile != null && downloadedFile.exists()) downloadedFile.delete();
            }
            return;
        }

        File folder;
        if ("mod".equals(projectType)) folder = new File(gameDirectory, "mods");
        else if ("resourcepack".equals(projectType)) folder = new File(gameDirectory, "resourcepacks");
        else if ("shader".equals(projectType)) folder = new File(gameDirectory, "shaderpacks");
        else throw new Exception("Unsupported content type: " + projectType);

        if (!folder.isDirectory() && !folder.mkdirs()) {
            throw new Exception("Could not create " + folder.getName() + " folder");
        }

        File destination = new File(folder, filename);
        if (destination.exists() && !destination.delete()) {
            throw new Exception("Could not replace existing file");
        }
        if (!downloadedFile.isFile() || downloadedFile.length() <= 0) {
            throw new Exception("Downloaded file is missing or empty before installation");
        }

        if (!downloadedFile.renameTo(destination)) {
            copyFile(downloadedFile, destination);
            if (!downloadedFile.delete() && downloadedFile.exists()) {
                throw new Exception("Installed file but could not clean download cache");
            }
        }
        if (!destination.isFile() || destination.length() <= 0) {
            throw new Exception("Installation verification failed: destination file is missing or empty");
        }
        String lower = filename.toLowerCase(java.util.Locale.ROOT);
        boolean extensionOk = ("mod".equals(projectType) && lower.endsWith(".jar"))
                || (("resourcepack".equals(projectType) || "shader".equals(projectType)) && lower.endsWith(".zip"));
        if (!extensionOk) throw new Exception("Installation verification failed: invalid file type");
        installedStore.markInstalled(gameDirectory, project.id, projectType, filename);
        Log.d(TAG, "[ORYN-DOWNLOAD] destination=" + destination.getAbsolutePath()
                + " downloaded=true installed=true fileExists=" + destination.isFile());
    }

    public File createDownloadTarget(ModrinthProject project, ModrinthVersion version,
                                     String projectType, ModrinthFile file) throws Exception {
        if (file == null || file.filename == null || file.filename.isEmpty()) {
            throw new Exception("Invalid Modrinth file");
        }
        File cache = Tools.DIR_CACHE;
        if (!cache.isDirectory() && !cache.mkdirs()) {
            throw new Exception("Could not create download cache");
        }
        String safeProject = project == null ? "project" : project.id;
        String safeVersion = version == null ? "version" : version.id;
        String filename = new File(file.filename).getName();
        return new File(cache, "oryn-" + safeProject + "-" + safeVersion + "-" + filename);
    }

    private void copyFile(File source, File destination) throws Exception {
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(source));
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(destination))) {
            byte[] buffer = new byte[32768];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            out.flush();
        }
    }

    public boolean isInstalled(Instance instance, ModrinthProject project, String projectType) {
        return instance != null && project != null
                && installedStore.isInstalled(instance.getGameDirectory(), project.id, projectType);
    }
}
