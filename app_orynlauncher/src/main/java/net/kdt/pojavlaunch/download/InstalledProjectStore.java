package net.kdt.pojavlaunch.download;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public final class InstalledProjectStore {
    private static final String FILE_NAME = ".oryn_modrinth_installed.json";

    public List<InstalledProject> read(File gameDirectory) {
        List<InstalledProject> result = new ArrayList<>();
        File file = new File(gameDirectory, FILE_NAME);
        if (!file.isFile()) return result;
        try (FileReader reader = new FileReader(file)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray array = root.has("projects") && root.get("projects").isJsonArray()
                    ? root.getAsJsonArray("projects") : new JsonArray();
            for (int i = 0; i < array.size(); i++) {
                JsonObject item = array.get(i).getAsJsonObject();
                result.add(new InstalledProject(
                        item.has("project_id") ? item.get("project_id").getAsString() : "",
                        item.has("category") ? item.get("category").getAsString() : "",
                        item.has("filename") ? item.get("filename").getAsString() : "",
                        item.has("installed_at") ? item.get("installed_at").getAsLong() : 0L
                ));
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    public boolean isInstalled(File gameDirectory, String projectId, String category) {
        for (InstalledProject project : read(gameDirectory)) {
            if (projectId.equals(project.projectId) && category.equals(project.category)) {
                if (project.filename == null || project.filename.isEmpty()) return true;
                if ("modpack".equals(category)) return true;
                File folder = folderForCategory(gameDirectory, category);
                return new File(folder, project.filename).isFile();
            }
        }
        return false;
    }

    public void markInstalled(File gameDirectory, String projectId, String category, String filename) throws Exception {
        List<InstalledProject> projects = read(gameDirectory);
        List<InstalledProject> updated = new ArrayList<>();
        boolean replaced = false;
        for (InstalledProject project : projects) {
            if (projectId.equals(project.projectId) && category.equals(project.category)) {
                updated.add(new InstalledProject(projectId, category, filename, System.currentTimeMillis()));
                replaced = true;
            } else {
                updated.add(project);
            }
        }
        if (!replaced) updated.add(new InstalledProject(projectId, category, filename, System.currentTimeMillis()));

        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();
        for (InstalledProject project : updated) {
            JsonObject item = new JsonObject();
            item.addProperty("project_id", project.projectId);
            item.addProperty("category", project.category);
            item.addProperty("filename", project.filename == null ? "" : project.filename);
            item.addProperty("installed_at", project.installedAt);
            array.add(item);
        }
        root.add("projects", array);

        File file = new File(gameDirectory, FILE_NAME);
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new Exception("Could not create instance directory");
        }
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(root.toString());
        }
    }

    private File folderForCategory(File gameDirectory, String category) {
        if ("mod".equals(category)) return new File(gameDirectory, "mods");
        if ("resourcepack".equals(category)) return new File(gameDirectory, "resourcepacks");
        if ("shader".equals(category)) return new File(gameDirectory, "shaderpacks");
        return gameDirectory;
    }
}
