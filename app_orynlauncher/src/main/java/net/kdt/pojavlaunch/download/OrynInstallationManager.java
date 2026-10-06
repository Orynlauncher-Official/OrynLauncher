package net.kdt.pojavlaunch.download;

import android.util.Log;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModrinthApi;
import net.kdt.pojavlaunch.instances.OrynInstanceManager;
import net.kdt.pojavlaunch.instances.OrynInstanceContentEvent;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;

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
        if (project == null || version == null || sourceFile == null) {
            throw new Exception("Invalid project or version");
        }
        // Complete modpacks are independent instances and do not require a
        // currently selected target instance at all.
        if (instance == null && !"modpack".equals(projectType)) {
            throw new Exception("Select an instance before downloading");
        }

        File gameDirectory = instance == null ? null : instance.getGameDirectory();
        if (gameDirectory == null && !"modpack".equals(projectType)) {
            throw new Exception("Selected instance has no game directory");
        }
        String filename = new File(sourceFile.filename).getName();
        if (filename.contains("..") || filename.isEmpty()) throw new Exception("Invalid destination filename");
        Log.d(TAG, "[ORYN-DOWNLOAD] type=" + projectType + " project=" + project.id
                + " version=" + version.id + " instance="
                + (gameDirectory == null ? "<new-instance>" : gameDirectory.getAbsolutePath()));
        if (filename.isEmpty()) throw new Exception("Modrinth returned an invalid filename");

        if ("modpack".equals(projectType)) {
            // A .mrpack is a complete instance, never install it into the currently selected instance.
            try {
                Instance newInstance = new ModrinthApi().installMrpackAsNewInstance(downloadedFile, null);
                if (newInstance == null || newInstance.getGameDirectory() == null
                        || !newInstance.getGameDirectory().isDirectory()) {
                    throw new Exception("Modpack installation did not create a new instance");
                }
                newInstance.modpackProjectId = project.id;
                newInstance.write();
                installedStore.markInstalled(newInstance.getGameDirectory(), project.id, projectType, filename);
                OrynInstanceManager.recordInstalledContent(newInstance, projectType + ":" + project.id, filename);
                OrynInstanceManager.publishContentInstalled(new OrynInstanceContentEvent(
                        newInstance.id, projectType, filename, project.id, version.id));
                Log.d(TAG, "[ORYN-DOWNLOAD] destination=" + newInstance.getGameDirectory().getAbsolutePath()
                        + " downloaded=true installed=true fileExists=true");
                // Make the newly installed pack the selected instance only after installation succeeds.
                OrynInstanceManager.select(newInstance);
                ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, null);
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

        String lower = filename.toLowerCase(java.util.Locale.ROOT);
        boolean extensionOk = ("mod".equals(projectType) && lower.endsWith(".jar"))
                || (("resourcepack".equals(projectType) || "shader".equals(projectType)) && lower.endsWith(".zip"));
        if (!extensionOk) {
            throw new Exception("Installation rejected: invalid file type for " + projectType);
        }

        File destination = new File(folder, filename);
        File temporary = new File(folder, "." + filename + ".oryn-part");
        if (temporary.exists() && !temporary.delete()) {
            throw new Exception("Could not clear previous temporary installation");
        }
        if (!downloadedFile.isFile() || downloadedFile.length() <= 0) {
            throw new Exception("Downloaded file is missing or empty before installation");
        }

        // Never expose a half-written file to the instance scanner.
        copyFile(downloadedFile, temporary);
        if (!temporary.isFile() || temporary.length() != downloadedFile.length()) {
            if (temporary.exists()) temporary.delete();
            throw new Exception("Temporary installation verification failed");
        }
        if (destination.exists() && !destination.delete()) {
            temporary.delete();
            throw new Exception("Could not replace existing file");
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete();
            throw new Exception("Could not atomically publish installed file");
        }
        if (!destination.isFile() || destination.length() <= 0) {
            throw new Exception("Installation verification failed: destination file is missing or empty");
        }
        if (!downloadedFile.delete() && downloadedFile.exists()) {
            Log.w(TAG, "Installed file but could not clean download cache: " + downloadedFile);
        }
        // Extension was validated before the destination was touched.
        installedStore.markInstalled(gameDirectory, project.id, projectType, filename);
        OrynInstanceManager.recordInstalledContent(instance, projectType + ":" + project.id, filename);
        OrynInstanceManager.publishContentInstalled(new OrynInstanceContentEvent(
                instance.id, projectType, filename, project.id, version.id));
        OrynInstanceContentScanner.refreshAsync(instance);
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
