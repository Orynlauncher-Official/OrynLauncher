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
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
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
                params.put("facets", "[[\"project_type:" + category.projectType + "\"]]");

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

                // Let Modrinth do the compatibility filtering. This matches the
                // behavior of Modrinth-style launchers: request versions for the
                // selected Minecraft version (and loader when it is known).
                HashMap<String, Object> versionQuery = new HashMap<>();
                versionQuery.put("game_versions", "[\"" + minecraftVersion + "\"]");

                String loader = getModrinthLoader(instance);
                if (loader != null) {
                    versionQuery.put("loaders", "[\"" + loader + "\"]");
                }

                JsonArray versions = api.get(
                        "project/" + URLEncoder.encode(projectId, "UTF-8") + "/version",
                        versionQuery,
                        JsonArray.class
                );

                if (versions == null || versions.size() == 0) {
                    throw new Exception("This " + category.title.toLowerCase()
                            + " is not available for Minecraft " + minecraftVersion
                            + (loader == null ? "" : " (" + loader + ")"));
                }

                // Prefer a featured compatible release when Modrinth marks one.
                JsonObject version = versions.get(0).getAsJsonObject();
                for (int i = 0; i < versions.size(); i++) {
                    JsonObject candidate = versions.get(i).getAsJsonObject();
                    if (candidate.has("featured") && candidate.get("featured").getAsBoolean()) {
                        version = candidate;
                        break;
                    }
                }

                JsonArray files = version.getAsJsonArray("files");
                if (files == null || files.size() == 0) throw new Exception("No downloadable file found");

                JsonObject file = files.get(0).getAsJsonObject();
                String url = file.get("url").getAsString();
                String filename = file.has("filename") ? file.get("filename").getAsString() : projectId + ".download";
                filename = new File(filename).getName();

                output = new File(targetDirectory, filename);
                downloadFile(url, output);

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

    private String getModrinthLoader(Instance instance) {
        // Resource packs and shaders use the special Modrinth "minecraft" loader.
        if (category == Category.RESOURCEPACK || category == Category.SHADER) {
            return "minecraft";
        }

        // Mods are loader-specific. Infer the loader from the selected instance
        // when possible. If it cannot be determined, do not guess and reject a
        // valid mod; Modrinth will filter by Minecraft version only.
        if (instance.installer != null) {
            String url = instance.installer.installerDownloadUrl;
            if (url != null) {
                String lower = url.toLowerCase();
                if (lower.contains("neoforge")) return "neoforge";
                if (lower.contains("forge")) return "forge";
                if (lower.contains("fabric")) return "fabric";
                if (lower.contains("quilt")) return "quilt";
            }

            if (instance.installer.commandLineArgs != null) {
                for (String arg : instance.installer.commandLineArgs) {
                    if (arg == null) continue;
                    String lower = arg.toLowerCase();
                    if (lower.contains("neoforge")) return "neoforge";
                    if (lower.contains("forge")) return "forge";
                    if (lower.contains("fabric")) return "fabric";
                    if (lower.contains("quilt")) return "quilt";
                }
            }
        }
        return null;
    }

    private void downloadFile(String urlString, File output) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "OrynLauncher/2.2");
        connection.connect();

        if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
            throw new Exception("Download server returned HTTP " + connection.getResponseCode());
        }

        File parent = output.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new Exception("Could not create download directory");
        }

        File temp = new File(output.getParentFile(), output.getName() + ".part");
        try (InputStream in = new BufferedInputStream(connection.getInputStream());
             FileOutputStream out = new FileOutputStream(temp)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        } finally {
            connection.disconnect();
        }

        if (!temp.renameTo(output)) {
            if (output.exists()) output.delete();
            if (!temp.renameTo(output)) throw new Exception("Could not finalize downloaded file");
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
