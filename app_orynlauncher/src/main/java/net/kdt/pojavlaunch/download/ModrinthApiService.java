package net.kdt.pojavlaunch.download;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ModrinthApiService {
    private static final String TAG = "OrynDownload";
    private static final String BASE = "https://api.modrinth.com/v2";
    private static final String USER_AGENT =
            "Orynlauncher-Official/OrynLauncher/4.1 (https://github.com/Orynlauncher-Official/OrynLauncher)";

    private static final int CONNECT_TIMEOUT_MS = 12000;
    private static final int READ_TIMEOUT_MS = 20000;

    public ModrinthSearchResult search(String query, String projectType, String minecraftVersion,
                                       String loader, int offset, int limit) throws Exception {
        String facets = buildFacets(projectType, minecraftVersion, loader);
        StringBuilder url = new StringBuilder(BASE).append("/search");
        url.append("?query=").append(encode(query == null ? "" : query));
        url.append("&limit=").append(limit);
        url.append("&offset=").append(offset);
        url.append("&index=relevance");
        url.append("&facets=").append(encode(facets));

        JsonObject response = getObject(url.toString());
        JsonArray hits = response.has("hits") && response.get("hits").isJsonArray()
                ? response.getAsJsonArray("hits") : new JsonArray();
        int total = response.has("total_hits") ? response.get("total_hits").getAsInt() : 0;

        List<ModrinthProject> projects = new ArrayList<>();
        for (JsonElement element : hits) {
            if (!element.isJsonObject()) continue;
            ModrinthProject project = parseProject(element.getAsJsonObject());
            if (project.id == null || project.id.trim().isEmpty()) {
                Log.d(TAG, "Skipping hit without project_id");
                continue;
            }
            // The /search facets already perform the project-type/version/loader
            // discovery filtering. Do not apply a second compatibility-count
            // filter here: every valid returned hit becomes a real Project object.
            projects.add(project);
        }

        Log.d(TAG, "API results = " + hits.size());
        Log.d(TAG, "Raw result count: " + hits.size());
        Log.d(TAG, "Parsed projects = " + projects.size());
        Log.d(TAG, "Parsed result count: " + projects.size());
        Log.d(TAG, "Search state payload projects = " + projects.size()
                + " • offset=" + offset + " • totalHits=" + total);
        return new ModrinthSearchResult(projects, offset, total);
    }

    public ModrinthProject getProject(String projectId) throws Exception {
        JsonObject object = getObject(BASE + "/project/" + encodePath(projectId));
        return parseProject(object);
    }

    public List<ModrinthVersion> getProjectVersions(String projectId, String minecraftVersion,
                                                   String loader) throws Exception {
        StringBuilder url = new StringBuilder(BASE)
                .append("/project/").append(encodePath(projectId)).append("/version")
                .append("?include_changelog=false");
        if (minecraftVersion != null && !minecraftVersion.isEmpty()) {
            url.append("&game_versions=").append(encode("[\"" + minecraftVersion + "\"]"));
        }
        if (loader != null && !loader.isEmpty()) {
            url.append("&loaders=").append(encode("[\"" + loader + "\"]"));
        }
        JsonArray array = getArray(url.toString());
        List<ModrinthVersion> versions = new ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonObject()) versions.add(parseVersion(element.getAsJsonObject()));
        }
        return versions;
    }

    public List<String> getGameVersions() throws Exception {
        JsonArray array = getArray(BASE + "/tag/game_version");
        List<String> versions = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            String type = string(object, "version_type", "release");
            String version = string(object, "version", "");
            if (!version.isEmpty() && "release".equalsIgnoreCase(type)) versions.add(version);
        }
        return versions;
    }

    public List<String> getLoadersForContent() throws Exception {
        JsonArray array = getArray(BASE + "/tag/loader");
        List<String> loaders = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            String name = string(object, "name", "");
            if (name.isEmpty()) continue;
            JsonArray supported = object.has("supported_project_types")
                    && object.get("supported_project_types").isJsonArray()
                    ? object.getAsJsonArray("supported_project_types") : new JsonArray();
            boolean mod = containsIgnoreCase(supported, "mod");
            boolean modpack = containsIgnoreCase(supported, "modpack");
            if (mod || modpack) loaders.add(name.toLowerCase(Locale.ROOT));
        }
        return loaders;
    }

    public Bitmap loadBitmap(String urlString) throws Exception {
        if (urlString == null || urlString.trim().isEmpty()) return null;
        byte[] bytes = getBytes(urlString);
        Bitmap bitmap = BitmapFactory.decodeStream(new ByteArrayInputStream(bytes));
        if (bitmap == null) throw new Exception("Invalid project icon");
        return bitmap;
    }

    public byte[] getBytes(String urlString) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = open(urlString);
            int status = connection.getResponseCode();
            Log.d(TAG, "HTTP " + status);
            if (status < 200 || status >= 300) {
                throw new Exception("HTTP " + status + " from Modrinth");
            }
            try (InputStream in = new BufferedInputStream(connection.getInputStream());
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[32768];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                return out.toByteArray();
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private JsonObject getObject(String urlString) throws Exception {
        String body = getText(urlString);
        JsonElement parsed = JsonParser.parseString(body);
        if (!parsed.isJsonObject()) throw new Exception("Modrinth returned a non-object response");
        return parsed.getAsJsonObject();
    }

    private JsonArray getArray(String urlString) throws Exception {
        String body = getText(urlString);
        JsonElement parsed = JsonParser.parseString(body);
        if (!parsed.isJsonArray()) throw new Exception("Modrinth returned a non-array response");
        return parsed.getAsJsonArray();
    }

    private String getText(String urlString) throws Exception {
        HttpURLConnection connection = null;
        try {
            Log.d(TAG, "API URL: " + urlString);
            connection = open(urlString);
            int status = connection.getResponseCode();
            Log.d(TAG, "HTTP " + status);
            if (status < 200 || status >= 300) {
                throw new Exception("HTTP " + status + " from Modrinth");
            }
            try (InputStream in = new BufferedInputStream(connection.getInputStream());
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[32768];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                String body = new String(out.toByteArray(), "UTF-8");
                // Keep this bounded so Logcat remains usable while still exposing
                // the exact JSON shape that reached the parser.
                String rawLog = body.length() > 8000 ? body.substring(0, 8000) + "…[truncated]" : body;
                Log.d(TAG, "Raw Modrinth response: " + rawLog);
                return body;
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private HttpURLConnection open(String urlString) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        return connection;
    }

    private ModrinthProject parseProject(JsonObject object) {
        return new ModrinthProject(
                string(object, "project_id", string(object, "id", "")),
                string(object, "title", "Unknown project"),
                string(object, "author", "Unknown author"),
                string(object, "description", ""),
                string(object, "icon_url", ""),
                string(object, "project_type", ""),
                number(object, "downloads"),
                number(object, "followers", number(object, "follows")),
                strings(object, "categories", "display_categories"),
                strings(object, "versions"),
                strings(object, "loaders")
        );
    }

    private ModrinthVersion parseVersion(JsonObject object) {
        List<ModrinthFile> files = new ArrayList<>();
        if (object.has("files") && object.get("files").isJsonArray()) {
            for (JsonElement element : object.getAsJsonArray("files")) {
                if (!element.isJsonObject()) continue;
                JsonObject file = element.getAsJsonObject();
                JsonObject hashes = file.has("hashes") && file.get("hashes").isJsonObject()
                        ? file.getAsJsonObject("hashes") : new JsonObject();
                files.add(new ModrinthFile(
                        string(file, "url", ""),
                        string(file, "filename", ""),
                        string(hashes, "sha1", ""),
                        number(file, "size"),
                        file.has("primary") && file.get("primary").getAsBoolean()
                ));
            }
        }
        return new ModrinthVersion(
                string(object, "id", ""),
                string(object, "name", ""),
                string(object, "version_number", ""),
                object.has("featured") && object.get("featured").getAsBoolean(),
                strings(object, "game_versions"),
                strings(object, "loaders"),
                files
        );
    }

    private static List<String> strings(JsonObject object, String... keys) {
        List<String> result = new ArrayList<>();
        for (String key : keys) {
            if (!object.has(key) || !object.get(key).isJsonArray()) continue;
            for (JsonElement element : object.getAsJsonArray(key)) {
                if (element != null && !element.isJsonNull()) result.add(element.getAsString());
            }
            if (!result.isEmpty()) break;
        }
        return result;
    }

    private static long number(JsonObject object, String key) {
        return number(object, key, 0L);
    }

    private static long number(JsonObject object, String key, long fallback) {
        try { return object.has(key) ? object.get(key).getAsLong() : fallback; }
        catch (Exception ignored) { return fallback; }
    }

    private static String string(JsonObject object, String key, String fallback) {
        try { return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback; }
        catch (Exception ignored) { return fallback; }
    }

    private static boolean containsIgnoreCase(JsonArray array, String value) {
        for (JsonElement element : array) {
            if (value.equalsIgnoreCase(element.getAsString())) return true;
        }
        return false;
    }

    private static String buildFacets(String projectType, String minecraftVersion, String loader) {
        StringBuilder facets = new StringBuilder("[");
        facets.append("[\"project_type:").append(projectType).append("\"]");
        if (minecraftVersion != null && !minecraftVersion.isEmpty()) {
            facets.append(",[\"versions:").append(minecraftVersion).append("\"]");
        }
        if (loader != null && !loader.isEmpty()
                && ("mod".equals(projectType) || "modpack".equals(projectType))) {
            // Modrinth search exposes loader tags through the categories facet.
            facets.append(",[\"categories:").append(loader).append("\"]");
        }
        facets.append("]");
        return facets.toString();
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static String encodePath(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
    }
}
