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
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModrinthApi;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;

public class OrynDownloadActivity extends AppCompatActivity {
    private enum Category {
        MOD("Mods", "mod", "mods"),
        RESOURCEPACK("Resource Packs", "resourcepack", "resourcepacks"),
        SHADER("Shaders", "shader", "shaderpacks"),
        MODPACK("Modpacks", "modpack", "");

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
    private final ModrinthApi modrinthModpackApi = new ModrinthApi();
    private Category category = Category.MOD;
    private EditText search;
    private LinearLayout results;
    private TextView status;
    private ProgressBar progress;
    private TextView detailTitle;
    private TextView detailVersion;
    private android.widget.Spinner loaderSpinner;
    private android.widget.Spinner versionSpinner;
    private String selectedMinecraftVersion;
    private String selectedLoader;
    /** Optional exact Modrinth version selected from the Zalith-style version picker. */
    private String forcedVersionId;
    private String forcedModpackFileUrl;
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
        // V4 Download Center: clean Zalith-style three-pane layout.
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Color.rgb(11, 12, 15));

        LinearLayout sidebar = new LinearLayout(this);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setPadding(dp(14), dp(18), dp(14), dp(14));
        sidebar.setBackgroundColor(Color.rgb(18, 19, 23));
        root.addView(sidebar, new LinearLayout.LayoutParams(dp(190), -1));

        TextView brand = label("ORYNLAUNCHER", 12);
        brand.setTypeface(null, android.graphics.Typeface.BOLD);
        brand.setTextColor(0xFFBFC3CC);
        sidebar.addView(brand, new LinearLayout.LayoutParams(-1, dp(34)));

        TextView heading = label("Download", 24);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        sidebar.addView(heading, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView sub = label("Discover content for your instance", 11);
        sub.setTextColor(0xFF858A95);
        sub.setMaxLines(2);
        sidebar.addView(sub, new LinearLayout.LayoutParams(-1, dp(42)));

        addRailItem(sidebar, "Mods", "MODS", Category.MOD);
        addRailItem(sidebar, "Resource Packs", "PACKS", Category.RESOURCEPACK);
        addRailItem(sidebar, "Shaders", "SHADERS", Category.SHADER);
        addRailItem(sidebar, "Modpacks", "MODPACKS", Category.MODPACK);

        TextView spacer = label("", 1);
        sidebar.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));

        TextView versionHint = label("SELECTED INSTANCE", 9);
        versionHint.setTextColor(0xFF686D78);
        sidebar.addView(versionHint, new LinearLayout.LayoutParams(-1, dp(20)));

        TextView instanceVersion = label(getSelectedMinecraftVersion() == null ? "Select an instance" : getSelectedMinecraftVersion(), 12);
        instanceVersion.setTextColor(0xFFD7D9DE);
        sidebar.addView(instanceVersion, new LinearLayout.LayoutParams(-1, dp(28)));

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(20), dp(16), dp(12), dp(12));
        root.addView(main, new LinearLayout.LayoutParams(0, -1, 1));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label(category.title, 25);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        this.categoryTitleView = title;
        top.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1));

        versionSpinner = new android.widget.Spinner(this);
        versionSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                Object value = parent.getItemAtPosition(position);
                if (value != null) {
                    selectedMinecraftVersion = value.toString();
                    if (search != null) searchProjects(search.getText().toString().trim());
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        top.addView(versionSpinner, new LinearLayout.LayoutParams(dp(125), dp(38)));

        loaderSpinner = new android.widget.Spinner(this);
        String[] loaderChoices = new String[]{"Auto", "Fabric", "Forge", "NeoForge", "Quilt"};
        loaderSpinner.setAdapter(new android.widget.ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_dropdown_item, loaderChoices));
        String detectedLoader = getModrinthLoader(Instances.loadSelectedInstance());
        selectedLoader = detectedLoader;
        if ("fabric".equals(detectedLoader)) loaderSpinner.setSelection(1);
        else if ("forge".equals(detectedLoader)) loaderSpinner.setSelection(2);
        else if ("neoforge".equals(detectedLoader)) loaderSpinner.setSelection(3);
        else if ("quilt".equals(detectedLoader)) loaderSpinner.setSelection(4);
        loaderSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                selectedLoader = position == 1 ? "fabric" : position == 2 ? "forge" :
                        position == 3 ? "neoforge" : position == 4 ? "quilt" :
                        getModrinthLoader(Instances.loadSelectedInstance());
                searchProjects(search == null ? "" : search.getText().toString().trim());
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        top.addView(loaderSpinner, new LinearLayout.LayoutParams(dp(105), dp(38)));
        main.addView(top);

        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search " + category.title.toLowerCase(Locale.ROOT) + "…");
        search.setHintTextColor(0xFF747984);
        search.setTextColor(Color.WHITE);
        search.setTextSize(13);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setPadding(dp(16), 0, dp(12), 0);
        search.setBackground(roundBg(0xFF1D1F25, dp(10)));
        searchBar.addView(search, new LinearLayout.LayoutParams(0, dp(44), 1));

        Button searchButton = new Button(this);
        searchButton.setText("Search");
        searchButton.setTextColor(Color.WHITE);
        searchButton.setTextSize(12);
        searchButton.setAllCaps(false);
        searchButton.setBackground(roundBg(0xFF343843, dp(10)));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(dp(86), dp(44));
        searchLp.leftMargin = dp(8);
        searchBar.addView(searchButton, searchLp);
        main.addView(searchBar);

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        main.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));

        status = label("Loading content…", 11);
        status.setTextColor(0xFF858A95);
        main.addView(status, new LinearLayout.LayoutParams(-1, dp(28)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setPadding(0, dp(4), dp(8), dp(12));
        scroll.addView(results);
        main.addView(scroll, new LinearLayout.LayoutParams(0, 0, 1));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        details.setPadding(dp(20), dp(22), dp(20), dp(18));
        details.setBackgroundColor(Color.rgb(18, 19, 23));
        root.addView(details, new LinearLayout.LayoutParams(dp(300), -1));

        ImageView detailIcon = new ImageView(this);
        detailIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        detailIcon.setImageResource(R.drawable.oryn_download_mod);
        details.addView(detailIcon, new LinearLayout.LayoutParams(dp(96), dp(96)));

        TextView detailTitle = label("Select a project", 19);
        detailTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        detailTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams detailTitleLp = new LinearLayout.LayoutParams(-1, -2);
        detailTitleLp.topMargin = dp(14);
        details.addView(detailTitle, detailTitleLp);

        TextView detailVersion = label("", 10);
        detailVersion.setTextColor(0xFF858A95);
        detailVersion.setGravity(Gravity.CENTER);
        details.addView(detailVersion, new LinearLayout.LayoutParams(-1, dp(26)));

        TextView detailDesc = label("Choose a project to see its compatible version and install it into the selected instance.", 12);
        detailDesc.setTextColor(0xFFB4B7BF);
        detailDesc.setGravity(Gravity.CENTER);
        detailDesc.setMaxLines(10);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(-1, 0, 1);
        descLp.topMargin = dp(10);
        details.addView(detailDesc, descLp);

        Button detailDownload = new Button(this);
        detailDownload.setText("Download");
        detailDownload.setTextColor(Color.WHITE);
        detailDownload.setTextSize(13);
        detailDownload.setAllCaps(false);
        detailDownload.setEnabled(false);
        detailDownload.setBackground(roundBg(0xFF3B4050, dp(10)));
        details.addView(detailDownload, new LinearLayout.LayoutParams(-1, dp(46)));

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
        loadMinecraftVersions();
    }

    private void loadMinecraftVersions() {
        executor.execute(() -> {
            try {
                URL url = new URL("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(15000);
                connection.setInstanceFollowRedirects(true);
                connection.connect();
                InputStream in = new BufferedInputStream(connection.getInputStream());
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                in.close();
                connection.disconnect();

                JsonObject manifest = new com.google.gson.JsonParser().parse(
                        new String(out.toByteArray(), "UTF-8")).getAsJsonObject();
                JsonArray versions = manifest.getAsJsonArray("versions");
                java.util.ArrayList<String> values = new java.util.ArrayList<>();
                String current = getSelectedMinecraftVersion();

                for (int i = 0; i < versions.size(); i++) {
                    JsonObject v = versions.get(i).getAsJsonObject();
                    if (!"release".equalsIgnoreCase(v.has("type") ? v.get("type").getAsString() : "")) continue;
                    values.add(v.get("id").getAsString());
                }

                runOnUiThread(() -> {
                    if (values.isEmpty()) return;
                    android.widget.ArrayAdapter<String> adapter =
                            new android.widget.ArrayAdapter<String>(
                                    this, android.R.layout.simple_spinner_dropdown_item, values);
                    versionSpinner.setAdapter(adapter);
                    int index = current == null ? -1 : values.indexOf(current);
                    if (index < 0) index = 0;
                    selectedMinecraftVersion = values.get(index);
                    versionSpinner.setSelection(index);
                    updateVersionSpinnerVisibility();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    String current = getSelectedMinecraftVersion();
                    if (current != null && !current.isEmpty()) {
                        java.util.ArrayList<String> fallback = new java.util.ArrayList<>();
                        fallback.add(current);
                        android.widget.ArrayAdapter<String> adapter =
                                new android.widget.ArrayAdapter<String>(
                                        this, android.R.layout.simple_spinner_dropdown_item, fallback);
                        versionSpinner.setAdapter(adapter);
                        selectedMinecraftVersion = current;
                    }
                    updateVersionSpinnerVisibility();
                });
            }
        });
    }

    private void updateVersionSpinnerVisibility() {
        if (versionSpinner == null) return;
        versionSpinner.setVisibility(View.VISIBLE);
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
        item.setTag(value);
        parent.addView(item, lp);
        item.setOnClickListener(v -> {
            category = value;
            if (categoryTitleView != null) categoryTitleView.setText(value.title);
            updateVersionSpinnerVisibility();
            search.setHint("Search " + value.title.toLowerCase(Locale.ROOT));
            searchProjects(search.getText().toString().trim());
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                Object tag = child.getTag();
                boolean selected = tag instanceof Category && tag == value;
                if (selected) {
                    child.setBackground(roundBg(0xFF363943, dp(9)));
                } else if (tag instanceof Category) {
                    child.setBackground(roundBg(0x00252529, dp(9)));
                }
            }
        });
    }

    private void searchProjects(final String query) {
        if (progress == null || status == null || results == null) return;

        final int requestId = ++searchGeneration;
        final Category requestedCategory = category;
        final String requestedQuery = query == null ? "" : query.trim();
        final String requestedVersion = getDownloadMinecraftVersion();
        final String requestedLoader = getSelectedLoader();

        progress.setVisibility(View.VISIBLE);
        status.setText("Loading " + requestedCategory.title.toLowerCase(Locale.ROOT) + "…");
        results.removeAllViews();

        Instance selectedInstance = null;
        try {
            selectedInstance = Instances.loadSelectedInstance();
        } catch (Throwable ignored) {}

        if (selectedInstance == null) {
            progress.setVisibility(View.GONE);
            status.setText("Select an instance first");
            return;
        }

        if (requestedVersion == null || requestedVersion.trim().isEmpty()) {
            progress.setVisibility(View.GONE);
            status.setText("Select a Minecraft version");
            return;
        }

        executor.execute(() -> {
            try {
                HashMap<String, Object> params = new HashMap<>();
                params.put("query", requestedQuery);
                params.put("limit", 30);
                params.put("index", requestedQuery.isEmpty() ? "downloads" : "relevance");

                // Modrinth supports project_type and versions as search facets.
                // Use the selected Minecraft version for discovery so incompatible
                // projects do not flood the result list.
                params.put("facets", buildSearchFacets(requestedCategory, requestedVersion, requestedLoader));

                JsonObject response;
                try {
                    response = api.get("search", params, JsonObject.class);
                } catch (Exception preciseError) {
                    // Network/API fallback: search by project type only, then let
                    // the version picker perform the authoritative compatibility check.
                    HashMap<String, Object> fallback = new HashMap<>();
                    fallback.put("query", requestedQuery);
                    fallback.put("limit", 50);
                    fallback.put("index", requestedQuery.isEmpty() ? "downloads" : "relevance");
                    fallback.put("facets", String.format("[[\"project_type:%s\"]]", requestedCategory.projectType));
                    response = api.get("search", fallback, JsonObject.class);
                }

                JsonArray rawHits = response == null ? null : response.getAsJsonArray("hits");
                JsonArray searchHits = new JsonArray();

                if (rawHits != null) {
                    for (int i = 0; i < rawHits.size(); i++) {
                        JsonObject hit = rawHits.get(i).getAsJsonObject();
                        String type = hit.has("project_type") && !hit.get("project_type").isJsonNull()
                                ? hit.get("project_type").getAsString() : "";
                        if (!requestedCategory.projectType.equalsIgnoreCase(type)) continue;
                        searchHits.add(hit);
                        if (searchHits.size() >= 30) break;
                    }
                }

                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);

                    if (requestId != searchGeneration || category != requestedCategory) return;

                    if (searchHits.size() == 0) {
                        status.setText(requestedQuery.isEmpty()
                                ? "No compatible " + requestedCategory.title.toLowerCase(Locale.ROOT) + " found"
                                : "No compatible results found");
                        return;
                    }

                    status.setText(searchHits.size() + " compatible projects • "
                            + requestedVersion
                            + (requestedCategory == Category.MOD && requestedLoader != null
                            ? " • " + requestedLoader : ""));

                    for (int i = 0; i < searchHits.size(); i++) {
                        addResult(searchHits.get(i).getAsJsonObject());
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    if (requestId != searchGeneration || category != requestedCategory) return;
                    status.setText("Unable to connect to Modrinth • Tap Search to retry");
                    Toast.makeText(this,
                            e.getMessage() == null ? "Modrinth request failed" : e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private String buildSearchFacets(Category requestedCategory, String minecraftVersion, String loader) {
        StringBuilder facets = new StringBuilder(
                String.format("[[\"project_type:%s\"],[\"versions:%s\"]]",
                        requestedCategory.projectType, minecraftVersion));

        // Modrinth search treats loaders as categories for project discovery.
        // The final version endpoint still performs the authoritative loader check.
        if (requestedCategory == Category.MOD && loader != null && !loader.isEmpty()) {
            facets.setLength(facets.length() - 1);
            facets.append(String.format(",[\"categories:%s\"]]", loader));
        }
        return facets.toString();
    }

    private void addResult(JsonObject hit) {
        final String projectId = hit.has("project_id") ? hit.get("project_id").getAsString() : "";
        if (projectId.isEmpty()) return;

        final String title = hit.has("title") ? hit.get("title").getAsString() : "Unknown";
        final String description = hit.has("description") ? hit.get("description").getAsString() : "";
        final String iconUrl = hit.has("icon_url") && !hit.get("icon_url").isJsonNull()
                ? hit.get("icon_url").getAsString() : null;
        final long downloads = hit.has("downloads") && !hit.get("downloads").isJsonNull()
                ? hit.get("downloads").getAsLong() : -1L;
        final String author = hit.has("author") && !hit.get("author").isJsonNull()
                ? hit.get("author").getAsString() : "";

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(9), dp(10), dp(9));
        card.setBackground(roundBg(0xFF292B32, dp(9)));

        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setImageResource(category == Category.MOD ? R.drawable.oryn_download_mod
                : category == Category.RESOURCEPACK ? R.drawable.oryn_download_resource
                : category == Category.SHADER ? R.drawable.oryn_download_shader
                : R.drawable.oryn_download_mod);
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
        info.addView(desc, new LinearLayout.LayoutParams(-1, dp(30)));

        StringBuilder meta = new StringBuilder(category.title);
        if (!author.isEmpty()) meta.append(" • ").append(author);
        if (downloads >= 0) meta.append(" • ").append(formatDownloads(downloads));
        TextView type = label(meta.toString(), 9);
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

    private String formatDownloads(long value) {
        if (value >= 1000000L) return String.format(Locale.ROOT, "%.1fM downloads", value / 1000000.0);
        if (value >= 1000L) return String.format(Locale.ROOT, "%.1fk downloads", value / 1000.0);
        return value + " downloads";
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
                (getDownloadMinecraftVersion() == null ? "Version will be scanned" : getDownloadMinecraftVersion()));
        detailDesc.setText(description == null || description.trim().isEmpty()
                ? "No description available." : description);
        detailDownload.setEnabled(true);
        detailDownload.setText(category == Category.MODPACK ? "Install Modpack" : "Download");
        detailDownload.setOnClickListener(v -> {
            // ZalithLauncher-style flow: never silently pick an arbitrary/latest
            // file. Let the user inspect compatible project versions first.
            chooseCompatibleVersion(projectId, title, iconUrl, detailDownload);
        });
        if (iconUrl != null) {
            loadImage(detailIcon, iconUrl);
        } else {
            detailIcon.setImageResource(category == Category.MOD ? R.drawable.oryn_download_mod
                    : category == Category.RESOURCEPACK ? R.drawable.oryn_download_resource
                    : category == Category.SHADER ? R.drawable.oryn_download_shader
                    : R.drawable.oryn_download_mod);
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

    private void chooseCompatibleVersion(final String projectId, final String projectTitle,
                                          final String iconUrl, final Button button) {
        button.setEnabled(false);
        button.setText("Checking versions…");
        executor.execute(() -> {
            try {
                HashMap<String, Object> versionParams = new HashMap<>();
                if (minecraftVersionForRequest() != null) {
                    versionParams.put("game_versions", "[\\"" + minecraftVersionForRequest() + "\\"]");
                }
                String loaderForRequest = getSelectedLoader();
                if (category == Category.MOD && loaderForRequest != null && !loaderForRequest.isEmpty()) {
                    versionParams.put("loaders", "[\\"" + loaderForRequest + "\\"]");
                } else if (category == Category.RESOURCEPACK || category == Category.SHADER) {
                    versionParams.put("loaders", "[\\"minecraft\\"]");
                }
                versionParams.put("include_changelog", false);
                JsonArray versions = api.get(
                        "project/" + URLEncoder.encode(projectId, "UTF-8") + "/version",
                        versionParams, JsonArray.class);
                final String minecraftVersion = getDownloadMinecraftVersion();
                final String loader = getSelectedLoader();
                final java.util.ArrayList<JsonObject> compatible = new java.util.ArrayList<>();

                for (int i = 0; versions != null && i < versions.size(); i++) {
                    JsonObject candidate = versions.get(i).getAsJsonObject();
                    if (!supportsMinecraftAndLoader(candidate, minecraftVersion,
                            category == Category.MOD ? loader : null)) continue;
                    compatible.add(candidate);
                }

                java.util.Collections.sort(compatible, (left, right) -> {
                    String lt = left.has("version_type") ? left.get("version_type").getAsString() : "release";
                    String rt = right.has("version_type") ? right.get("version_type").getAsString() : "release";
                    int lp = "release".equalsIgnoreCase(lt) ? 0 : "beta".equalsIgnoreCase(lt) ? 1 : 2;
                    int rp = "release".equalsIgnoreCase(rt) ? 0 : "beta".equalsIgnoreCase(rt) ? 1 : 2;
                    if (lp != rp) return Integer.compare(lp, rp);
                    return 0;
                });
                if (compatible.size() > 20) {
                    compatible.subList(20, compatible.size()).clear();
                }

                runOnUiThread(() -> {
                    button.setEnabled(true);
                    button.setText(category == Category.MODPACK ? "Install Modpack" : "Download");
                    if (compatible.isEmpty()) {
                        Toast.makeText(this,
                                "No compatible versions found for Minecraft " +
                                        (minecraftVersion == null ? "" : minecraftVersion),
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    String[] labels = new String[compatible.size()];
                    for (int i = 0; i < compatible.size(); i++) {
                        JsonObject v = compatible.get(i);
                        String name = v.has("name") && !v.get("name").isJsonNull()
                                ? v.get("name").getAsString()
                                : v.has("version_number") ? v.get("version_number").getAsString() : "Version";
                        String mc = minecraftVersion == null ? "Minecraft" : minecraftVersion;
                        String loaderLabel = "";
                        if (category == Category.MOD && v.has("loaders") && v.get("loaders").isJsonArray()) {
                            JsonArray ls = v.getAsJsonArray("loaders");
                            if (ls.size() > 0) loaderLabel = " • " + ls.get(0).getAsString();
                        }
                        String type = v.has("version_type") ? v.get("version_type").getAsString() : "release";
                        String fileInfo = "";
                        if (v.has("files") && v.get("files").isJsonArray() && v.getAsJsonArray("files").size() > 0) {
                            JsonObject firstFile = v.getAsJsonArray("files").get(0).getAsJsonObject();
                            String fn = firstFile.has("filename") ? firstFile.get("filename").getAsString() : "";
                            long size = firstFile.has("size") ? firstFile.get("size").getAsLong() : -1L;
                            if (!fn.isEmpty()) fileInfo = "\n" + fn + (size > 0 ? " • " + formatFileSize(size) : "");
                        }
                        labels[i] = name + " • " + type + "\n" + mc + loaderLabel + fileInfo;
                    }

                    new android.app.AlertDialog.Builder(this)
                            .setTitle(category.title + " versions")
                            .setSingleChoiceItems(labels, 0, null)
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton(category == Category.MODPACK ? "Install" : "Download", (dialog, which) -> {
                                android.app.AlertDialog alert = (android.app.AlertDialog) dialog;
                                int checked = alert.getListView().getCheckedItemPosition();
                                if (checked < 0 || checked >= compatible.size()) checked = 0;
                                JsonObject selected = compatible.get(checked);
                                forcedVersionId = selected.has("id") ? selected.get("id").getAsString() : null;
                                if (category == Category.MODPACK) {
                                    forcedModpackFileUrl = null;
                                    if (selected.has("files") && selected.get("files").isJsonArray()) {
                                        JsonArray selectedFiles = selected.getAsJsonArray("files");
                                        for (int fi = 0; fi < selectedFiles.size(); fi++) {
                                            JsonObject sf = selectedFiles.get(fi).getAsJsonObject();
                                            if (sf.has("primary") && sf.get("primary").getAsBoolean()) {
                                                forcedModpackFileUrl = sf.has("url") ? sf.get("url").getAsString() : null;
                                                break;
                                            }
                                        }
                                        if (forcedModpackFileUrl == null && selectedFiles.size() > 0) {
                                            JsonObject sf = selectedFiles.get(0).getAsJsonObject();
                                            forcedModpackFileUrl = sf.has("url") ? sf.get("url").getAsString() : null;
                                        }
                                    }
                                    installModpack(projectId, projectTitle, iconUrl, button);
                                } else {
                                    downloadProject(projectId, projectTitle, button);
                                }
                            }).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    button.setEnabled(true);
                    button.setText(category == Category.MODPACK ? "Install Modpack" : "Download");
                    Toast.makeText(this, e.getMessage() == null ? "Could not load versions" : e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void installModpack(final String projectId, final String projectTitle, final String iconUrl, final Button button) {
        button.setEnabled(false);
        button.setText("Checking version…");
        executor.execute(() -> {
            try {
                ModItem item = new ModItem(net.kdt.pojavlaunch.modloaders.modpacks.models.Constants.SOURCE_MODRINTH, true, projectId, projectTitle,
                        projectTitle, iconUrl == null ? "" : iconUrl);
                ModDetail detail = modrinthModpackApi.getModDetails(item);
                if (detail == null || detail.versionUrls == null || detail.versionUrls.length == 0) {
                    throw new Exception("No modpack versions found");
                }

                String mcVersion = getDownloadMinecraftVersion();
                int selected = -1;
                if (forcedModpackFileUrl != null) {
                    for (int i = 0; i < detail.versionUrls.length; i++) {
                        if (forcedModpackFileUrl.equals(detail.versionUrls[i])) {
                            selected = i;
                            break;
                        }
                    }
                }
                if (selected < 0) {
                    for (int i = 0; i < detail.mcVersionNames.length; i++) {
                        if (mcVersion != null && mcVersion.equals(detail.mcVersionNames[i])) {
                            selected = i;
                            break;
                        }
                    }
                }
                if (selected < 0) {
                    throw new Exception("This modpack is not made for your Minecraft version"
                            + (mcVersion == null ? "" : " (" + mcVersion + ")"));
                }

                final int versionIndex = selected;
                runOnUiThread(() -> button.setText("Installing…"));
                modrinthModpackApi.handleModpackInstallation(this, detail, versionIndex);

                runOnUiThread(() -> {
                    button.setText("Install started");
                    Toast.makeText(this, "Installing " + projectTitle + " for Minecraft "
                            + detail.mcVersionNames[versionIndex], Toast.LENGTH_LONG).show();
                    forcedVersionId = null;
                    forcedModpackFileUrl = null;
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    button.setEnabled(true);
                    button.setText("Install Modpack");
                    Toast.makeText(this, e.getMessage() == null ? "Modpack installation failed" : e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    forcedVersionId = null;
                    forcedModpackFileUrl = null;
                });
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
                final String minecraftVersion = getDownloadMinecraftVersion();
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

                String loader = getSelectedLoader();
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

                    if (forcedVersionId != null && candidate.has("id")
                            && forcedVersionId.equals(candidate.get("id").getAsString())) {
                        version = candidate;
                        break;
                    }

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

                // Oryn AI Dependency Scanner:
                // inspect Modrinth's dependency graph before saving the selected
                // mod. Required dependencies are resolved recursively and missing
                // files are downloaded automatically. Optional dependencies are
                // deliberately not installed.
                if (category == Category.MOD) {
                    scanAndInstallRequiredDependencies(projectId, version, instance, button);
                }

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
                    forcedVersionId = null;
                });
            } catch (Exception e) {
                if (output != null && output.isFile()) output.delete();
                final String message = e.getMessage() == null ? "Download failed" : e.getMessage();
                runOnUiThread(() -> {
                    button.setEnabled(true);
                    button.setText("Download");
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    forcedVersionId = null;
                });
            }
        });
    }

    private void scanAndInstallRequiredDependencies(String rootProjectId, JsonObject rootVersion,
                                                         Instance instance, Button button) throws Exception {
        java.util.HashSet<String> visited = new java.util.HashSet<>();
        java.util.HashSet<String> installedFiles = new java.util.HashSet<>();
        File modsDir = new File(instance.getGameDirectory(), "mods");
        if (modsDir.isDirectory()) {
            File[] files = modsDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile()) installedFiles.add(f.getName().toLowerCase(Locale.ROOT));
                }
            }
        }

        scanDependencyNode(rootProjectId, rootVersion, instance, installedFiles, visited, button);
    }

    private void scanDependencyNode(String projectId, JsonObject version, Instance instance,
                                    java.util.Set<String> installedFiles,
                                    java.util.Set<String> visited, Button button) throws Exception {
        String versionKey = version.has("id") ? version.get("id").getAsString() : projectId;
        if (!visited.add(versionKey)) return;

        if (!version.has("dependencies") || !version.get("dependencies").isJsonArray()) return;
        JsonArray dependencies = version.getAsJsonArray("dependencies");

        for (int i = 0; i < dependencies.size(); i++) {
            JsonObject dep = dependencies.get(i).getAsJsonObject();
            String type = dep.has("dependency_type") ? dep.get("dependency_type").getAsString() : "required";
            if (!"required".equalsIgnoreCase(type)) continue;

            String depProjectId = dep.has("project_id") && !dep.get("project_id").isJsonNull()
                    ? dep.get("project_id").getAsString() : null;
            String depVersionId = dep.has("version_id") && !dep.get("version_id").isJsonNull()
                    ? dep.get("version_id").getAsString() : null;

            if (depProjectId == null && depVersionId == null) continue;

            JsonObject depVersion = null;
            if (depVersionId != null && !depVersionId.isEmpty()) {
                depVersion = api.get("version/" + URLEncoder.encode(depVersionId, "UTF-8"),
                        JsonObject.class);
            }

            if (depVersion == null && depProjectId != null) {
                JsonArray depVersions = api.get(
                        "project/" + URLEncoder.encode(depProjectId, "UTF-8") + "/version",
                        JsonArray.class);
                String minecraftVersion = getMinecraftVersionFromInstanceId(instance.versionId);
                String loader = getModrinthLoader(instance);

                for (int j = 0; depVersions != null && j < depVersions.size(); j++) {
                    JsonObject candidate = depVersions.get(j).getAsJsonObject();
                    if (!supportsMinecraftAndLoader(candidate, minecraftVersion, loader)) continue;
                    depVersion = candidate;
                    if (candidate.has("featured") && candidate.get("featured").getAsBoolean()) break;
                }
            }

            if (depVersion == null) {
                throw new Exception("Required dependency is unavailable for this Minecraft version");
            }

            JsonArray depFiles = depVersion.getAsJsonArray("files");
            if (depFiles == null || depFiles.size() == 0) {
                throw new Exception("Required dependency has no downloadable file");
            }

            JsonObject depFile = depFiles.get(0).getAsJsonObject();
            for (int j = 0; j < depFiles.size(); j++) {
                JsonObject candidate = depFiles.get(j).getAsJsonObject();
                if (candidate.has("primary") && candidate.get("primary").getAsBoolean()) {
                    depFile = candidate;
                    break;
                }
            }

            String filename = depFile.has("filename")
                    ? new File(depFile.get("filename").getAsString()).getName()
                    : (depProjectId == null ? depVersion.get("id").getAsString() : depProjectId) + ".jar";

            if (!installedFiles.contains(filename.toLowerCase(Locale.ROOT))) {
                File modsDir = new File(instance.getGameDirectory(), "mods");
                if (!modsDir.exists() && !modsDir.mkdirs()) {
                    throw new Exception("Could not create mods folder for dependency");
                }

                String url = depFile.get("url").getAsString();
                String sha1 = null;
                if (depFile.has("hashes") && depFile.get("hashes").isJsonObject()
                        && depFile.getAsJsonObject("hashes").has("sha1")) {
                    sha1 = depFile.getAsJsonObject("hashes").get("sha1").getAsString();
                }
                long expectedSize = depFile.has("size") ? depFile.get("size").getAsLong() : -1L;
                File output = new File(modsDir, filename);

                final String dependencyName = depProjectId == null ? filename : depProjectId;
                runOnUiThread(() -> {
                    status.setText("AI Scanner: installing dependency " + dependencyName);
                    button.setText("Dependency…");
                });
                downloadFile(url, output, sha1, expectedSize, button, dependencyName);
                installedFiles.add(filename.toLowerCase(Locale.ROOT));
            }

            scanDependencyNode(
                    depProjectId == null ? "version:" + depVersion.get("id").getAsString() : depProjectId,
                    depVersion, instance, installedFiles, visited, button);
        }
    }

    private boolean supportsMinecraftAndLoader(JsonObject version, String minecraftVersion, String loader) {
        if (minecraftVersion == null || !version.has("game_versions")) return false;
        JsonArray gameVersions = version.getAsJsonArray("game_versions");
        boolean gameMatch = false;
        for (int i = 0; i < gameVersions.size(); i++) {
            if (minecraftVersion.equals(gameVersions.get(i).getAsString())) {
                gameMatch = true;
                break;
            }
        }
        if (!gameMatch) return false;

        if (loader == null) return true;
        if (!version.has("loaders")) return true;
        JsonArray loaders = version.getAsJsonArray("loaders");
        for (int i = 0; i < loaders.size(); i++) {
            if (loader.equalsIgnoreCase(loaders.get(i).getAsString())) return true;
        }
        return false;
    }

    private String minecraftVersionForRequest() {
        return getDownloadMinecraftVersion();
    }

    private String getDownloadMinecraftVersion() {
        if (selectedMinecraftVersion != null && !selectedMinecraftVersion.isEmpty()) {
            return selectedMinecraftVersion;
        }
        return getSelectedMinecraftVersion();
    }

    private String getSelectedMinecraftVersion() {
        try {
            Instance instance = Instances.loadSelectedInstance();
            if (instance == null) return null;
            return getMinecraftVersionFromInstanceId(instance.versionId);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Instance.versionId may be a loader profile id, not the raw Minecraft
     * version, e.g. fabric-loader-0.19.5-1.21.11.
     */
    private String getMinecraftVersionFromInstanceId(String versionId) {
        if (versionId == null || versionId.trim().isEmpty()) return null;
        String id = versionId.trim();

        if (id.startsWith("fabric-loader-")) {
            int lastDash = id.lastIndexOf('-');
            if (lastDash >= 0 && lastDash + 1 < id.length()) {
                String candidate = id.substring(lastDash + 1);
                if (candidate.matches("\\d+\\.\\d+(?:\\.\\d+)?(?:[-+].*)?")) {
                    return candidate;
                }
            }
        }

        java.util.regex.Matcher prefix = java.util.regex.Pattern
                .compile("^(\\d+\\.\\d+(?:\\.\\d+)?)")
                .matcher(id);
        if (prefix.find()) return prefix.group(1);

        return id;
    }

    private String getSelectedLoader() {
        if (category != Category.MOD) return null;
        if (selectedLoader != null && !selectedLoader.isEmpty()) return selectedLoader;
        return getModrinthLoader(Instances.loadSelectedInstance());
    }

    private String getModrinthLoader(Instance instance) {
        if (instance == null) return null;
        if (category == Category.RESOURCEPACK || category == Category.SHADER || category == Category.MODPACK) {
            return "minecraft";
        }

        String profileId = instance.versionId == null ? "" : instance.versionId.toLowerCase(Locale.ROOT);
        if (profileId.contains("neoforge")) return "neoforge";
        if (profileId.contains("forge")) return "forge";
        if (profileId.contains("fabric")) return "fabric";
        if (profileId.contains("quilt")) return "quilt";

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

    private String formatFileSize(long bytes) {
        if (bytes >= 1024L * 1024L) return String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0);
        if (bytes >= 1024L) return String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0);
        return bytes + " B";
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
