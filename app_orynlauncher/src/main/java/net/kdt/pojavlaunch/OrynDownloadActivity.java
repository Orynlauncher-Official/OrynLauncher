package net.kdt.pojavlaunch;

import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ApiHandler;

public class OrynDownloadActivity extends AppCompatActivity {
    private enum Category {
        MOD("Mods", "mod", "mods"),
        RESOURCEPACK("Resource Packs", "resourcepack", "resourcepacks"),
        SHADER("Shaders", "shader", "shaderpacks");

        final String title;
        final String projectType;
        final String folder;

        Category(String title, String projectType, String folder) {
            this.title = title;
            this.projectType = projectType;
            this.folder = folder;
        }
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ApiHandler api = new ApiHandler("https://api.modrinth.com/v2");
    private Category category = Category.MOD;
    private EditText search;
    private LinearLayout results;
    private TextView status;
    private TextView selectedCategory;
    private ProgressBar progress;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        buildUi();
        searchProjects("");
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView label(String text, float size) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(Color.WHITE);
        v.setTextSize(size);
        return v;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8), dp(6), dp(8), dp(6));
        root.setBackgroundResource(R.drawable.oryn_home_bg);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        Button back = new Button(this);
        back.setText("‹");
        back.setTextSize(28);
        back.setTextColor(Color.WHITE);
        back.setBackgroundResource(android.R.color.transparent);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(38), dp(38)));

        TextView title = label("Downloads", 19);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(38), 1));

        root.addView(header);

        selectedCategory = label("Mods", 12);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER);
        tabs.setPadding(0, 0, 0, dp(4));

        addTab(tabs, "Mods", Category.MOD);
        addTab(tabs, "Resource Packs", Category.RESOURCEPACK);
        addTab(tabs, "Shaders", Category.SHADER);
        root.addView(tabs);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search " + category.title.toLowerCase());
        search.setHintTextColor(0xFF9EA0A8);
        search.setTextColor(Color.WHITE);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setPadding(dp(10), 0, dp(10), 0);
        search.setBackgroundResource(R.drawable.oryn_pill);
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.addView(search, new LinearLayout.LayoutParams(0, dp(38), 1));
        root.addView(searchRow);

        Button searchButton = new Button(this);
        searchButton.setText("Search");
        searchButton.setTextColor(Color.WHITE);
        searchButton.setAllCaps(false);
        searchButton.setBackgroundResource(R.drawable.oryn_pill);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(dp(82), dp(38));
        searchLp.leftMargin = dp(6);
        searchRow.addView(searchButton, searchLp);
        searchButton.setOnClickListener(v -> searchProjects(search.getText().toString().trim()));
        search.setOnEditorActionListener((v, actionId, event) -> {
            searchProjects(search.getText().toString().trim());
            return true;
        });

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(4)));

        status = label("Choose a category and search for content.", 14);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, dp(28));
        statusLp.topMargin = dp(2);
        root.addView(status, statusLp);

        ScrollView scroll = new ScrollView(this);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(results);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(root);
    }

    private void addTab(LinearLayout parent, String text, Category value) {
        LinearLayout button = new LinearLayout(this);
        button.setOrientation(LinearLayout.HORIZONTAL);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(6), 0, dp(6), 0);
        button.setBackgroundResource(R.drawable.oryn_pill);

        ImageView icon = new ImageView(this);
        if (value == Category.MOD) icon.setImageResource(R.drawable.oryn_download_mod);
        else if (value == Category.RESOURCEPACK) icon.setImageResource(R.drawable.oryn_download_resource);
        else icon.setImageResource(R.drawable.oryn_download_shader);
        button.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));

        TextView textView = label(text, 11);
        textView.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(-2, -1);
        textLp.leftMargin = dp(4);
        button.addView(textView, textLp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(34), 1);
        lp.setMargins(dp(2), 0, dp(2), 0);
        parent.addView(button, lp);
        button.setOnClickListener(v -> {
            category = value;
            selectedCategory.setText(value.title);
            search.setHint("Search " + value.title.toLowerCase());
            searchProjects(search.getText().toString().trim());
        });
    }

    private void searchProjects(final String query) {
        progress.setVisibility(View.VISIBLE);
        status.setText("Searching…");
        results.removeAllViews();

        executor.execute(() -> {
            try {
                HashMap<String, Object> params = new HashMap<>();
                params.put("query", query);
                params.put("limit", 30);
                params.put("index", "relevance");

                // ZalithLauncher-style Modrinth search: filter the project list
                // itself by the selected Minecraft version and, for mods, the
                // selected instance loader.
                String minecraftVersion = getSelectedMinecraftVersion();
                String loader = getModrinthLoader(Instances.loadSelectedInstance());
                StringBuilder facets = new StringBuilder("[[\"project_type:")
                        .append(category.projectType).append("\"]");
                if (minecraftVersion != null) {
                    facets.append(",\"versions:").append(minecraftVersion).append("\"");
                }
                if (loader != null && category == Category.MOD) {
                    facets.append(",\"categories:").append(loader).append("\"");
                }
                facets.append("]]");
                params.put("facets", facets.toString());

                JsonObject response = api.get("search", params, JsonObject.class);
                JsonArray hits = response == null ? null : response.getAsJsonArray("hits");

                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    if (hits == null || hits.size() == 0) {
                        status.setText("No " + category.title.toLowerCase() + " found.");
                        return;
                    }
                    status.setText(hits.size() + " results");
                    for (int i = 0; i < hits.size(); i++) {
                        addResult(hits.get(i).getAsJsonObject());
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    status.setText("Search failed. Check your Internet connection.");
                    Toast.makeText(this, e.getMessage() == null ? "Download service error" : e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void addResult(JsonObject hit) {
        final String projectId = hit.has("project_id") ? hit.get("project_id").getAsString() : "";
        final String title = hit.has("title") ? hit.get("title").getAsString() : "Unknown";
        String description = hit.has("description") ? hit.get("description").getAsString() : "";

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundResource(R.drawable.oryn_pill);

        TextView name = label(title, 17);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(name);

        TextView desc = label(description, 12);
        desc.setTextColor(0xFFB8BAC2);
        desc.setMaxLines(3);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(-1, -2);
        descLp.topMargin = dp(4);
        card.addView(desc, descLp);

        Button download = new Button(this);
        download.setText("Download");
        download.setTextColor(Color.WHITE);
        download.setAllCaps(false);
        download.setBackgroundResource(R.drawable.oryn_pill);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-1, dp(42));
        btnLp.topMargin = dp(8);
        card.addView(download, btnLp);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(0, 0, 0, dp(10));
        results.addView(card, cardLp);

        download.setOnClickListener(v -> downloadProject(projectId, title, download));
    }

    private void downloadProject(final String projectId, final String projectTitle, final Button button) {
        Instance instance = Instances.loadSelectedInstance();
        if (instance == null) {
            Toast.makeText(this, R.string.no_instance, Toast.LENGTH_LONG).show();
            return;
        }

        final File targetDirectory = new File(instance.getGameDirectory(), category.folder);
        if (!targetDirectory.exists() && !targetDirectory.mkdirs()) {
            Toast.makeText(this, "Could not create " + category.folder + " folder", Toast.LENGTH_LONG).show();
            return;
        }

        button.setEnabled(false);
        button.setText("Downloading…");

        executor.execute(() -> {
            File output = null;
            try {
                // Scan the selected instance first. Never download an arbitrary/latest
                // project version: it must explicitly support this Minecraft version.
                final String minecraftVersion = instance.versionId;
                if (minecraftVersion == null || minecraftVersion.trim().isEmpty()) {
                    throw new Exception("Could not determine the Minecraft version of this instance");
                }

                runOnUiThread(() -> {
                    status.setText("Checking " + minecraftVersion + " compatibility…");
                    button.setText("Checking…");
                });

                // ZalithLauncher-style: fetch the project's versions, then
                // choose a version whose metadata explicitly matches the selected
                // Minecraft version and loader.
                JsonArray versions = api.get(
                        "project/" + URLEncoder.encode(projectId, "UTF-8") + "/version",
                        JsonArray.class
                );

                String loader = getModrinthLoader(instance);
                JsonObject version = null;
                for (int i = 0; versions != null && i < versions.size(); i++) {
                    JsonObject candidate = versions.get(i).getAsJsonObject();
                    boolean gameMatch = false;
                    if (candidate.has("game_versions")) {
                        JsonArray gameVersions = candidate.getAsJsonArray("game_versions");
                        for (int j = 0; j < gameVersions.size(); j++) {
                            if (minecraftVersion.equals(gameVersions.get(j).getAsString())) {
                                gameMatch = true;
                                break;
                            }
                        }
                    }
                    if (!gameMatch) continue;

                    if (loader != null && category == Category.MOD) {
                        boolean loaderMatch = false;
                        if (candidate.has("loaders")) {
                            JsonArray loaders = candidate.getAsJsonArray("loaders");
                            for (int j = 0; j < loaders.size(); j++) {
                                if (loader.equalsIgnoreCase(loaders.get(j).getAsString())) {
                                    loaderMatch = true;
                                    break;
                                }
                            }
                        }
                        if (!loaderMatch) continue;
                    }

                    // Prefer a featured release, otherwise keep the first
                    // compatible version returned by Modrinth.
                    if (version == null) {
                        version = candidate;
                    } else if (candidate.has("featured") && candidate.get("featured").getAsBoolean()) {
                        version = candidate;
                        break;
                    }
                }

                if (version == null) {
                    throw new Exception("No compatible " + category.title.toLowerCase()
                            + " version for Minecraft " + minecraftVersion
                            + (loader == null || category != Category.MOD ? "" : " (" + loader + ")"));
                }

                // The version selected above is already filtered for the
                // instance Minecraft version and loader.
                JsonArray files = version.getAsJsonArray("files");
                if (files == null || files.size() == 0) throw new Exception("No downloadable file found");

                // Prefer Modrinth's primary file, like ZalithLauncher does.
                JsonObject file = files.get(0).getAsJsonObject();
                for (int i = 0; i < files.size(); i++) {
                    JsonObject candidate = files.get(i).getAsJsonObject();
                    if (candidate.has("primary") && candidate.get("primary").getAsBoolean()) {
                        file = candidate;
                        break;
                    }
                }

                String url = file.get("url").getAsString();
                String filename = file.has("filename") ? file.get("filename").getAsString() : projectId + ".download";
                filename = new File(filename).getName();
                String sha1 = null;
                if (file.has("hashes") && file.getAsJsonObject("hashes").has("sha1")) {
                    sha1 = file.getAsJsonObject("hashes").get("sha1").getAsString();
                }
                long expectedSize = file.has("size") ? file.get("size").getAsLong() : -1L;

                output = new File(targetDirectory, filename);
                downloadFile(url, output, sha1, expectedSize, button, projectTitle);

                final String saved = output.getName();
                runOnUiThread(() -> {
                    button.setText("Downloaded");
                    Toast.makeText(this, projectTitle + " saved to " + category.folder + "/", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                if (output != null && output.isFile()) output.delete();
                final String message = e.getMessage() == null ? "Download failed" : e.getMessage();
                runOnUiThread(() -> {
                    button.setEnabled(true);
                    button.setText("Download");
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private String getSelectedMinecraftVersion() {
        try {
            Instance instance = Instances.loadSelectedInstance();
            return instance == null ? null : getVersionOrNull(instance.versionId);
        } catch (Exception e) {
            return null;
        }
    }

    private String getVersionOrNull(String version) {
        if (version == null || version.trim().isEmpty()) return null;
        return version.trim();
    }

    private String getModrinthLoader(Instance instance) {
        if (instance == null) return null;
        if (category == Category.RESOURCEPACK || category == Category.SHADER) {
            return "minecraft";
        }

        if (instance.installer != null) {
            String url = instance.installer.installerDownloadUrl;
            if (url != null) {
                String lower = url.toLowerCase(Locale.ROOT);
                if (lower.contains("neoforge")) return "neoforge";
                if (lower.contains("forge")) return "forge";
                if (lower.contains("fabric")) return "fabric";
                if (lower.contains("quilt")) return "quilt";
            }

            if (instance.installer.commandLineArgs != null) {
                for (String arg : instance.installer.commandLineArgs) {
                    if (arg == null) continue;
                    String lower = arg.toLowerCase(Locale.ROOT);
                    if (lower.contains("neoforge")) return "neoforge";
                    if (lower.contains("forge")) return "forge";
                    if (lower.contains("fabric")) return "fabric";
                    if (lower.contains("quilt")) return "quilt";
                }
            }
        }
        return null;
    }

    private void downloadFile(String urlString, File output, String expectedSha1,
                              long expectedSize, Button button, String projectTitle) throws Exception {
        Exception last = null;

        for (int attempt = 1; attempt <= 3; attempt++) {
            File temp = new File(output.getParentFile(), output.getName() + ".part");
            if (temp.exists()) temp.delete();

            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "OrynLauncher/2.2 (Zalith-style Modrinth downloader)");
                connection.connect();

                if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                    throw new Exception("Download server returned HTTP " + connection.getResponseCode());
                }

                long total = connection.getContentLengthLong() > 0
                        ? connection.getContentLengthLong() : expectedSize;
                long done = 0L;
                MessageDigest digest = MessageDigest.getInstance("SHA-1");

                try (InputStream in = new BufferedInputStream(connection.getInputStream());
                     FileOutputStream out = new FileOutputStream(temp)) {
                    byte[] buffer = new byte[32768];
                    int read;
                    long lastUi = 0;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        digest.update(buffer, 0, read);
                        done += read;

                        long now = android.os.SystemClock.elapsedRealtime();
                        if (now - lastUi > 150 || (total > 0 && done >= total)) {
                            final long progressDone = done;
                            final long progressTotal = total;
                            runOnUiThread(() -> {
                                if (progressTotal > 0) {
                                    int pct = (int) Math.min(100, progressDone * 100L / progressTotal);
                                    button.setText("Downloading " + pct + "%");
                                    status.setText(projectTitle + " • " + pct + "%");
                                } else {
                                    button.setText("Downloading…");
                                    status.setText(projectTitle + " • " + progressDone + " bytes");
                                }
                            });
                            lastUi = now;
                        }
                    }
                    out.flush();
                } finally {
                    connection.disconnect();
                }

                if (expectedSize > 0 && temp.length() != expectedSize) {
                    throw new Exception("Downloaded file size does not match Modrinth metadata");
                }

                if (expectedSha1 != null && !expectedSha1.isEmpty()) {
                    String actual = toHex(digest.digest());
                    if (!expectedSha1.equalsIgnoreCase(actual)) {
                        throw new Exception("SHA-1 verification failed");
                    }
                }

                if (output.exists() && !output.delete()) {
                    throw new Exception("Could not replace existing file");
                }
                if (!temp.renameTo(output)) {
                    throw new Exception("Could not finalize downloaded file");
                }
                return;
            } catch (Exception e) {
                last = e;
                if (temp.exists()) temp.delete();
                if (attempt < 3) {
                    try {
                        Thread.sleep(500L * attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new Exception("Download cancelled", interrupted);
                    }
                }
            }
        }

        throw last == null ? new Exception("Download failed") : last;
    }

    private String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) result.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return result.toString();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
