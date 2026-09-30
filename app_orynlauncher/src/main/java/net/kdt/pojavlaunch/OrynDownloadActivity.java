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
    private ProgressBar progress;
    private TextView detailTitle;
    private TextView detailVersion;
    private TextView detailDesc;
    private ImageView detailIcon;
    private Button detailDownload;

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
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(dp(0), dp(0), dp(0), dp(0));
        root.setBackgroundColor(Color.rgb(18, 19, 23));

        // Old ZalithLauncher-inspired layout: slim category rail + results + detail panel.
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        rail.setPadding(dp(8), dp(14), dp(8), dp(10));
        rail.setBackgroundColor(Color.rgb(25, 26, 31));
        root.addView(rail, new LinearLayout.LayoutParams(dp(122), -1));

        TextView railTitle = label("DOWNLOAD", 10);
        railTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        railTitle.setTextColor(0xFF8F93A0);
        railTitle.setGravity(Gravity.CENTER);
        rail.addView(railTitle, new LinearLayout.LayoutParams(-1, dp(28)));

        addRailItem(rail, "Mods", "MODS", Category.MOD);
        addRailItem(rail, "Resource Packs", "PACKS", Category.RESOURCEPACK);
        addRailItem(rail, "Shaders", "SHADERS", Category.SHADER);

        TextView version = label("", 10);
        version.setTextColor(0xFF777B86);
        version.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams versionLp = new LinearLayout.LayoutParams(-1, dp(30));
        versionLp.topMargin = dp(12);
        rail.addView(version, versionLp);

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setPadding(dp(12), dp(10), dp(8), dp(8));
        root.addView(center, new LinearLayout.LayoutParams(0, -1, 6.5f));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("Download", 22);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1));

        TextView mcVersion = label(getSelectedMinecraftVersion() == null ? "Minecraft" : getSelectedMinecraftVersion(), 11);
        mcVersion.setTextColor(0xFF9FA3AE);
        mcVersion.setGravity(Gravity.CENTER);
        top.addView(mcVersion, new LinearLayout.LayoutParams(dp(100), dp(34)));
        center.addView(top);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search " + category.title.toLowerCase(Locale.ROOT));
        search.setHintTextColor(0xFF777B86);
        search.setTextColor(Color.WHITE);
        search.setTextSize(13);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setPadding(dp(12), 0, dp(12), 0);
        search.setBackground(roundBg(0xFF292B32, dp(9)));
        searchRow.addView(search, new LinearLayout.LayoutParams(0, dp(42), 1));

        Button searchButton = new Button(this);
        searchButton.setText("Search");
        searchButton.setTextColor(Color.WHITE);
        searchButton.setTextSize(12);
        searchButton.setAllCaps(false);
        searchButton.setBackground(roundBg(0xFF343740, dp(9)));
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(dp(82), dp(42));
        sbLp.leftMargin = dp(7);
        searchRow.addView(searchButton, sbLp);
        center.addView(searchRow);

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        center.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));

        status = label("Select a category and search for content.", 11);
        status.setTextColor(0xFF8F93A0);
        status.setGravity(Gravity.CENTER_VERTICAL);
        center.addView(status, new LinearLayout.LayoutParams(-1, dp(28)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setPadding(0, dp(2), dp(4), dp(10));
        scroll.addView(results);
        center.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout detail = new LinearLayout(this);
        detail.setOrientation(LinearLayout.VERTICAL);
        detail.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        detail.setPadding(dp(14), dp(16), dp(14), dp(10));
        detail.setBackgroundColor(Color.rgb(25, 26, 31));
        root.addView(detail, new LinearLayout.LayoutParams(0, -1, 3f));

        ImageView detailIcon = new ImageView(this);
        detailIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        detailIcon.setImageResource(R.drawable.oryn_download_mod);
        detail.addView(detailIcon, new LinearLayout.LayoutParams(dp(76), dp(76)));

        TextView detailTitle = label("Select a project", 17);
        detailTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        detailTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dtLp = new LinearLayout.LayoutParams(-1, -2);
        dtLp.topMargin = dp(10);
        detail.addView(detailTitle, dtLp);

        TextView detailVersion = label("", 10);
        detailVersion.setTextColor(0xFF8F93A0);
        detailVersion.setGravity(Gravity.CENTER);
        detail.addView(detailVersion, new LinearLayout.LayoutParams(-1, dp(24)));

        TextView detailDesc = label("Choose a result to view its details and install it into the selected Minecraft instance.", 12);
        detailDesc.setTextColor(0xFFB6B8C0);
        detailDesc.setGravity(Gravity.CENTER);
        detailDesc.setMaxLines(8);
        LinearLayout.LayoutParams ddLp = new LinearLayout.LayoutParams(-1, 0, 1);
        ddLp.topMargin = dp(8);
        detail.addView(detailDesc, ddLp);

        Button detailDownload = new Button(this);
        detailDownload.setText("Download");
        detailDownload.setTextColor(Color.WHITE);
        detailDownload.setTextSize(13);
        detailDownload.setAllCaps(false);
        detailDownload.setEnabled(false);
        detailDownload.setBackground(roundBg(0xFF3A3D48, dp(9)));
        detail.addView(detailDownload, new LinearLayout.LayoutParams(-1, dp(44)));

        searchButton.setOnClickListener(v -> searchProjects(search.getText().toString().trim()));
        search.setOnEditorActionListener((v, actionId, event) -> {
            searchProjects(search.getText().toString().trim());
            return true;
        });

        this.detailTitle = detailTitle;
        this.detailVersion = detailVersion;
        this.detailDesc = detailDesc;
        this.detailIcon = detailIcon;
        this.detailDownload = detailDownload;

        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(root);
    }

    private void addRailItem(LinearLayout parent, String text, String shortText, Category value) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(5), dp(8), dp(5), dp(8));
        item.setBackground(roundBg(value == category ? 0xFF363943 : 0x00252529, dp(9)));

        TextView icon = label(value == Category.MOD ? "▣" : value == Category.RESOURCEPACK ? "◆" : "◇", 22);
        icon.setGravity(Gravity.CENTER);
        icon.setTextColor(value == category ? Color.WHITE : 0xFF9DA1AC);
        item.addView(icon, new LinearLayout.LayoutParams(-1, dp(28)));

        TextView name = label(shortText, 9);
        name.setGravity(Gravity.CENTER);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        name.setTextColor(value == category ? Color.WHITE : 0xFF9DA1AC);
        item.addView(name, new LinearLayout.LayoutParams(-1, dp(20)));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(68));
        lp.topMargin = dp(4);
        parent.addView(item, lp);
        item.setOnClickListener(v -> {
            category = value;
            search.setHint("Search " + value.title.toLowerCase(Locale.ROOT));
            searchProjects(search.getText().toString().trim());
            for (int i = 1; i < parent.getChildCount() - 1; i++) {
                View child = parent.getChildAt(i);
                child.setBackground(roundBg(i == (value == Category.MOD ? 1 : value == Category.RESOURCEPACK ? 2 : 3)
                        ? 0xFF363943 : 0x00252529, dp(9)));
            }
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

                // Some Modrinth projects do not expose loader/version facets consistently.
                // Fall back progressively so the browser still shows content; the actual
                // download step remains strict and checks the selected Minecraft version.
                if (hits == null || hits.size() == 0) {
                    HashMap<String, Object> fallback = new HashMap<>();
                    fallback.put("query", query);
                    fallback.put("limit", 30);
                    fallback.put("index", "relevance");
                    fallback.put("facets", "[[\"project_type:" + category.projectType + "\"]]");
                    response = api.get("search", fallback, JsonObject.class);
                    hits = response == null ? null : response.getAsJsonArray("hits");
                }

                final JsonArray searchHits = hits;
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    if (searchHits == null || searchHits.size() == 0) {
                        status.setText("No " + category.title.toLowerCase() + " found.");
                        return;
                    }
                    status.setText(searchHits.size() + " results");
                    for (int i = 0; i < searchHits.size(); i++) {
                        addResult(searchHits.get(i).getAsJsonObject());
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
        final String description = hit.has("description") ? hit.get("description").getAsString() : "";
        final String iconUrl = hit.has("icon_url") && !hit.get("icon_url").isJsonNull()
                ? hit.get("icon_url").getAsString() : null;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(9), dp(10), dp(9));
        card.setBackground(roundBg(0xFF292B32, dp(9)));

        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setImageResource(category == Category.MOD ? R.drawable.oryn_download_mod
                : category == Category.RESOURCEPACK ? R.drawable.oryn_download_resource
                : R.drawable.oryn_download_shader);
        card.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(10), 0, dp(8), 0);
        TextView name = label(title, 14);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        info.addView(name, new LinearLayout.LayoutParams(-1, dp(22)));
        TextView desc = label(description, 10);
        desc.setTextColor(0xFFAEB1BA);
        desc.setMaxLines(2);
        info.addView(desc, new LinearLayout.LayoutParams(-1, dp(32)));
        TextView type = label(category.title + " • " + (getSelectedMinecraftVersion() == null ? "Any version" : getSelectedMinecraftVersion()), 9);
        type.setTextColor(0xFF7F8490);
        info.addView(type, new LinearLayout.LayoutParams(-1, dp(18)));
        card.addView(info, new LinearLayout.LayoutParams(0, dp(68), 1));

        TextView arrow = label("›", 26);
        arrow.setGravity(Gravity.CENTER);
        arrow.setTextColor(0xFFB9BCC6);
        card.addView(arrow, new LinearLayout.LayoutParams(dp(30), dp(68)));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, dp(78));
        cardLp.setMargins(0, 0, 0, dp(7));
        results.addView(card, cardLp);

        View.OnClickListener select = v -> showProjectDetails(projectId, title, description, iconUrl);
        card.setOnClickListener(select);
        if (iconUrl != null) loadImage(icon, iconUrl);
    }

    private android.graphics.drawable.Drawable roundBg(int color, int radius) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(radius);
        return bg;
    }

    private void showProjectDetails(final String projectId, String title, String description, String iconUrl) {
        detailTitle.setText(title);
        detailVersion.setText(category.title + " • " +
                (getSelectedMinecraftVersion() == null ? "Version will be scanned" : getSelectedMinecraftVersion()));
        detailDesc.setText(description == null || description.trim().isEmpty()
                ? "No description available." : description);
        detailDownload.setEnabled(true);
        detailDownload.setText("Download");
        detailDownload.setOnClickListener(v -> downloadProject(projectId, title, detailDownload));
        if (iconUrl != null) {
            loadImage(detailIcon, iconUrl);
        } else {
            detailIcon.setImageResource(category == Category.MOD ? R.drawable.oryn_download_mod
                    : category == Category.RESOURCEPACK ? R.drawable.oryn_download_resource
                    : R.drawable.oryn_download_shader);
        }
    }

    private void loadImage(final ImageView target, final String imageUrl) {
        executor.execute(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(imageUrl).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "OrynLauncher/2.2");
                InputStream in = connection.getInputStream();
                final android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(in);
                in.close();
                connection.disconnect();
                if (bitmap != null) runOnUiThread(() -> target.setImageBitmap(bitmap));
            } catch (Exception ignored) {
            }
        });
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
