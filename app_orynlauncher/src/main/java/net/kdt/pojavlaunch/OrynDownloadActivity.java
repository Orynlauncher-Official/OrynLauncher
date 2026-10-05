package net.kdt.pojavlaunch;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.download.DownloadState;
import net.kdt.pojavlaunch.download.InstalledProjectStore;
import net.kdt.pojavlaunch.download.ModrinthFile;
import net.kdt.pojavlaunch.download.ModrinthInstaller;
import net.kdt.pojavlaunch.download.ModrinthProject;
import net.kdt.pojavlaunch.download.ModrinthRepository;
import net.kdt.pojavlaunch.download.ModrinthSearchResult;
import net.kdt.pojavlaunch.download.ModrinthVersion;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;

public class OrynDownloadActivity extends AppCompatActivity {
    private enum Category {
        MOD("Mods", "mod", "mods", R.drawable.oryn_download_mod),
        RESOURCEPACK("Resource Packs", "resourcepack", "resourcepacks", R.drawable.oryn_download_resource),
        SHADER("Shaders", "shader", "shaderpacks", R.drawable.oryn_download_shader),
        MODPACK("Modpacks", "modpack", "", R.drawable.oryn_download_mod);

        final String title;
        final String projectType;
        final String folder;
        final int placeholder;

        Category(String title, String projectType, String folder, int placeholder) {
            this.title = title;
            this.projectType = projectType;
            this.folder = folder;
            this.placeholder = placeholder;
        }
    }

    private final ModrinthRepository repository = new ModrinthRepository();
    private final InstalledProjectStore installedStore = new InstalledProjectStore();
    private final ModrinthInstaller installer = new ModrinthInstaller(repository, installedStore);
    private final ExecutorService instanceExecutor = Executors.newSingleThreadExecutor();

    private Category category = Category.MOD;
    private Instance selectedInstance;
    private ModrinthProject selectedProject;
    private List<ModrinthVersion> selectedCompatibleVersions = new ArrayList<>();
    private ModrinthVersion selectedVersion;

    private final List<ModrinthProject> projects = new ArrayList<>();
    private int currentOffset = 0;
    private int generation = 0;
    private int totalHits = 0;
    private boolean suppressFilterCallbacks;

    private LinearLayout projectRows;
    private ScrollView projectScroll;
    private TextView status;
    private TextView categoryTitle;
    private TextView instanceButton;\n    private TextView instanceTargetView;
    private EditText search;
    private Spinner versionSpinner;
    private Spinner loaderSpinner;
    private Button loadMoreButton;
    private ProgressBar searchProgress;

    private ImageView detailIcon;
    private TextView detailTitle;
    private TextView detailAuthor;
    private TextView detailInfo;
    private TextView detailDescription;
    private Spinner detailVersionSpinner;
    private Button detailDownload;
    private ProgressBar detailProgress;

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

        selectedInstance = Instances.loadSelectedInstance();
        buildUi();
        loadFilters();
        updateInstanceUi();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, float size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(Color.WHITE);
        view.setTextSize(size);
        return view;
    }

    private void styleButton(Button button) {
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(12);
        button.setBackground(round(0xFF30343E, dp(10)));
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(0xFF0B0C0F);

        root.addView(buildSidebar(), new LinearLayout.LayoutParams(dp(178), -1));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setPadding(dp(16), dp(14), dp(10), dp(10));
        root.addView(center, new LinearLayout.LayoutParams(0, -1, 1));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        categoryTitle = text(category.title, 24);
        categoryTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(categoryTitle, new LinearLayout.LayoutParams(0, dp(42), 1));

        instanceButton = new Button(this);
        styleButton(instanceButton);
        instanceButton.setText("Select an instance");
        instanceButton.setOnClickListener(v -> chooseInstance());
        header.addView(instanceButton, new LinearLayout.LayoutParams(dp(190), dp(40)));
        center.addView(header);

        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setGravity(Gravity.CENTER_VERTICAL);
        filterRow.setPadding(0, dp(2), 0, dp(5));

        versionSpinner = new Spinner(this);
        filterRow.addView(versionSpinner, new LinearLayout.LayoutParams(dp(135), dp(38)));

        loaderSpinner = new Spinner(this);
        LinearLayout.LayoutParams loaderLp = new LinearLayout.LayoutParams(dp(115), dp(38));
        loaderLp.leftMargin = dp(6);
        filterRow.addView(loaderSpinner, loaderLp);

        center.addView(filterRow);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(0xFF737782);
        search.setTextSize(13);
        search.setHint("Search mods, resource packs, shaders, modpacks…");
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setPadding(dp(14), 0, dp(10), 0);
        search.setBackground(round(0xFF1D1F25, dp(10)));
        searchRow.addView(search, new LinearLayout.LayoutParams(0, dp(44), 1));

        Button searchButton = new Button(this);
        styleButton(searchButton);
        searchButton.setText("Search");
        LinearLayout.LayoutParams searchButtonLp = new LinearLayout.LayoutParams(dp(82), dp(44));
        searchButtonLp.leftMargin = dp(7);
        searchRow.addView(searchButton, searchButtonLp);
        center.addView(searchRow);

        searchProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        searchProgress.setMax(100);
        searchProgress.setIndeterminate(true);
        searchProgress.setVisibility(View.GONE);
        center.addView(searchProgress, new LinearLayout.LayoutParams(-1, dp(3)));

        status = text("Select an instance first", 11);
        status.setTextColor(0xFF8D929D);
        status.setPadding(0, dp(3), 0, dp(3));
        center.addView(status, new LinearLayout.LayoutParams(-1, dp(30)));

        projectScroll = new ScrollView(this);
        projectScroll.setFillViewport(true);
        projectRows = new LinearLayout(this);
        projectRows.setOrientation(LinearLayout.VERTICAL);
        projectRows.setPadding(0, dp(4), dp(6), dp(14));
        projectScroll.addView(projectRows);
        center.addView(projectScroll, new LinearLayout.LayoutParams(0, 0, 1));

        loadMoreButton = new Button(this);
        styleButton(loadMoreButton);
        loadMoreButton.setText("Load more");
        loadMoreButton.setVisibility(View.GONE);
        loadMoreButton.setOnClickListener(v -> performSearch(false));
        center.addView(loadMoreButton, new LinearLayout.LayoutParams(-1, dp(40)));

        root.addView(buildDetails(), new LinearLayout.LayoutParams(dp(305), -1));

        searchButton.setOnClickListener(v -> performSearch(true));
        search.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || event != null) {
                performSearch(true);
                return true;
            }
            return false;
        });

        versionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppressFilterCallbacks) return;
                Object value = parent.getItemAtPosition(position);
                if (value != null) {
                    performSearch(true);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        loaderSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppressFilterCallbacks) return;
                if (category == Category.MOD || category == Category.MODPACK) performSearch(true);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(root);
    }

    private LinearLayout buildSidebar() {
        LinearLayout sidebar = new LinearLayout(this);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setPadding(dp(14), dp(16), dp(12), dp(12));
        sidebar.setBackgroundColor(0xFF121317);

        TextView brand = text("ORYNLAUNCHER", 12);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brand.setTextColor(0xFFC8CBD3);
        sidebar.addView(brand, new LinearLayout.LayoutParams(-1, dp(32)));

        TextView heading = text("Download", 24);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        sidebar.addView(heading, new LinearLayout.LayoutParams(-1, dp(45)));

        TextView sub = text("Discover real Modrinth content", 11);
        sub.setTextColor(0xFF7F8490);
        sub.setMaxLines(2);
        sidebar.addView(sub, new LinearLayout.LayoutParams(-1, dp(38)));

        addCategory(sidebar, "Mods", Category.MOD);
        addCategory(sidebar, "Resource Packs", Category.RESOURCEPACK);
        addCategory(sidebar, "Shaders", Category.SHADER);
        addCategory(sidebar, "Modpacks", Category.MODPACK);

        TextView spacer = text("", 1);
        sidebar.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));

        TextView target = text("INSTALLING TO", 9);
        target.setTextColor(0xFF666B76);
        sidebar.addView(target, new LinearLayout.LayoutParams(-1, dp(20)));

        TextView targetValue = text("", 11);\n        instanceTargetView = targetValue;
        targetValue.setTextColor(0xFFD8DAE0);
        targetValue.setMaxLines(2);
        targetValue.setTag("instance_target");
        sidebar.addView(targetValue, new LinearLayout.LayoutParams(-1, dp(40)));

        return sidebar;
    }

    private void addCategory(LinearLayout sidebar, String title, Category value) {
        Button button = new Button(this);
        styleButton(button);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        button.setText(title);
        button.setOnClickListener(v -> {
            category = value;
            categoryTitle.setText(value.title);
            selectedProject = null;
            selectedCompatibleVersions.clear();
            selectedVersion = null;
            clearDetails();
            updateFilterVisibility();
            performSearch(true);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(44));
        lp.bottomMargin = dp(5);
        sidebar.addView(button, lp);
    }

    private LinearLayout buildDetails() {
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(18), dp(18), dp(18), dp(16));
        details.setBackgroundColor(0xFF121317);

        detailIcon = new ImageView(this);
        detailIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        detailIcon.setImageResource(category.placeholder);
        details.addView(detailIcon, new LinearLayout.LayoutParams(dp(88), dp(88)));

        detailTitle = text("Select a project", 19);
        detailTitle.setGravity(Gravity.CENTER);
        detailTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.topMargin = dp(12);
        details.addView(detailTitle, titleLp);

        detailAuthor = text("", 11);
        detailAuthor.setTextColor(0xFF8F949F);
        detailAuthor.setGravity(Gravity.CENTER);
        details.addView(detailAuthor, new LinearLayout.LayoutParams(-1, dp(24)));

        detailInfo = text("", 10);
        detailInfo.setTextColor(0xFFB4B7BF);
        detailInfo.setGravity(Gravity.CENTER);
        detailInfo.setMaxLines(8);
        details.addView(detailInfo, new LinearLayout.LayoutParams(-1, dp(72)));

        detailDescription = text("Choose a real Modrinth project to inspect compatible versions and install it into the selected instance.", 12);
        detailDescription.setTextColor(0xFFB8BBC3);
        detailDescription.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        detailDescription.setMaxLines(12);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(-1, 0, 1);
        descLp.topMargin = dp(8);
        details.addView(detailDescription, descLp);

        TextView versionLabel = text("COMPATIBLE VERSION", 9);
        versionLabel.setTextColor(0xFF707580);
        details.addView(versionLabel, new LinearLayout.LayoutParams(-1, dp(20)));

        detailVersionSpinner = new Spinner(this);
        details.addView(detailVersionSpinner, new LinearLayout.LayoutParams(-1, dp(38)));

        detailProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        detailProgress.setMax(100);
        detailProgress.setVisibility(View.GONE);
        details.addView(detailProgress, new LinearLayout.LayoutParams(-1, dp(3)));

        detailDownload = new Button(this);
        styleButton(detailDownload);
        detailDownload.setText("DOWNLOAD");
        detailDownload.setEnabled(false);
        detailDownload.setOnClickListener(v -> installSelectedProject());
        details.addView(detailDownload, new LinearLayout.LayoutParams(-1, dp(46)));

        return details;
    }

    private void loadFilters() {
        suppressFilterCallbacks = true;
        repository.loadGameVersionsAsync(new ModrinthRepository.ValuesCallback() {
            @Override public void onSuccess(List<String> values) {
                runOnUiThread(() -> {
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            OrynDownloadActivity.this, android.R.layout.simple_spinner_dropdown_item, values);
                    versionSpinner.setAdapter(adapter);
                    String current = selectedMinecraftVersion();
                    int index = current == null ? -1 : values.indexOf(current);
                    if (index < 0 && !values.isEmpty()) index = 0;
                    if (index >= 0) versionSpinner.setSelection(index);
                    suppressFilterCallbacks = false;
                    updateFilterVisibility();
                    performSearch(true);
                });
            }

            @Override public void onError(Exception error) {
                runOnUiThread(() -> {
                    suppressFilterCallbacks = false;
                    status.setText("Modrinth couldn't load Minecraft versions.");
                    updateFilterVisibility();
                });
            }
        });

        repository.loadLoadersAsync(new ModrinthRepository.ValuesCallback() {
            @Override public void onSuccess(List<String> values) {
                runOnUiThread(() -> {
                    List<String> sorted = new ArrayList<>(values);
                    Collections.sort(sorted);
                    ArrayList<String> display = new ArrayList<>();
                    display.add("Auto");
                    display.addAll(sorted);
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            OrynDownloadActivity.this, android.R.layout.simple_spinner_dropdown_item, display);
                    loaderSpinner.setAdapter(adapter);
                    String detected = detectLoader(selectedInstance);
                    if (detected != null) {
                        int index = display.indexOf(detected);
                        if (index >= 0) loaderSpinner.setSelection(index);
                    }
                    suppressFilterCallbacks = false;
                    updateFilterVisibility();
                });
            }

            @Override public void onError(Exception error) {
                runOnUiThread(() -> {
                    ArrayList<String> fallback = new ArrayList<>();
                    fallback.add("Auto");
                    fallback.add("fabric");
                    fallback.add("forge");
                    fallback.add("neoforge");
                    fallback.add("quilt");
                    loaderSpinner.setAdapter(new ArrayAdapter<>(
                            OrynDownloadActivity.this, android.R.layout.simple_spinner_dropdown_item, fallback));
                    suppressFilterCallbacks = false;
                    updateFilterVisibility();
                });
            }
        });
    }

    private void updateFilterVisibility() {
        boolean needsLoader = category == Category.MOD || category == Category.MODPACK;
        loaderSpinner.setVisibility(needsLoader ? View.VISIBLE : View.GONE);
        versionSpinner.setVisibility(View.VISIBLE);
    }

    private void performSearch(boolean reset) {
        if (selectedInstance == null) {
            status.setText("Select an instance first.");
            renderEmpty("Select an instance first");
            loadMoreButton.setVisibility(View.GONE);
            return;
        }

        String mcVersion = selectedMinecraftVersion();
        if (mcVersion == null || mcVersion.isEmpty()) {
            status.setText("Select a Minecraft version.");
            renderEmpty("Select a Minecraft version");
            return;
        }

        final int requestGeneration = ++generation;
        final int offset = reset ? 0 : currentOffset;
        final String query = search == null ? "" : search.getText().toString().trim();
        final String loader = selectedLoader();

        if (reset) {
            repository.cancelSearch();
            currentOffset = 0;
            totalHits = 0;
            projects.clear();
            selectedProject = null;
            selectedCompatibleVersions.clear();
            selectedVersion = null;
            clearDetails();
            renderSkeletons();
        }

        searchProgress.setVisibility(View.VISIBLE);
        loadMoreButton.setEnabled(false);
        status.setText("Loading Modrinth…");

        repository.searchAsync(query, category.projectType, mcVersion, loader, offset,
                new ModrinthRepository.SearchCallback() {
                    @Override public void onLoading() {
                        runOnUiThread(() -> {
                            searchProgress.setVisibility(View.VISIBLE);
                            if (reset) renderSkeletons();
                        });
                    }

                    @Override public void onSuccess(ModrinthSearchResult result, boolean append) {
                        runOnUiThread(() -> {
                            if (requestGeneration != generation) return;

                            Set<String> existing = new HashSet<>();
                            for (ModrinthProject project : projects) existing.add(project.id);
                            int before = projects.size();
                            for (ModrinthProject project : result.projects) {
                                if (!existing.contains(project.id)) {
                                    projects.add(project);
                                    existing.add(project.id);
                                }
                            }

                            currentOffset = result.offset + result.projects.size();
                            totalHits = result.totalHits;
                            searchProgress.setVisibility(View.GONE);
                            loadMoreButton.setEnabled(true);

                            android.util.Log.d("OrynDownload", "UI item count: " + projects.size());
                            renderProjects();

                            if (projects.isEmpty()) {
                                status.setText("No projects found");
                                loadMoreButton.setVisibility(View.GONE);
                            } else {
                                String filterText = mcVersion + (loader == null ? "" : " • " + loader);
                                status.setText(projects.size() + " projects • " + filterText);
                                loadMoreButton.setVisibility(result.hasMore() ? View.VISIBLE : View.GONE);
                            }

                            if (append && result.projects.isEmpty() && totalHits > currentOffset) {
                                status.setText("No additional projects returned. Try another search.");
                            }
                            if (before == projects.size() && append) {
                                loadMoreButton.setVisibility(View.GONE);
                            }
                        });
                    }

                    @Override public void onError(Exception error) {
                        runOnUiThread(() -> {
                            if (requestGeneration != generation) return;
                            searchProgress.setVisibility(View.GONE);
                            loadMoreButton.setEnabled(true);
                            if (projects.isEmpty()) {
                                renderEmpty("Modrinth couldn't be reached.");
                                status.setText("Modrinth couldn't be reached.");
                            } else {
                                status.setText("Modrinth couldn't load more projects.");
                            }
                            loadMoreButton.setVisibility(View.GONE);
                        });
                    }
                });
    }

    private void renderSkeletons() {
        projectRows.removeAllViews();
        int count = columnsForWidth();
        for (int start = 0; start < 6; start += count) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < count && start + i < 6; i++) {
                View skeleton = skeletonCard();
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(145), 1);
                lp.setMargins(dp(3), dp(3), dp(3), dp(3));
                row.addView(skeleton, lp);
            }
            projectRows.addView(row, new LinearLayout.LayoutParams(-1, dp(151)));
        }
    }

    private View skeletonCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(round(0xFF17191E, dp(12)));

        LinearLayout line = new LinearLayout(this);
        line.addView(block(dp(48), dp(48)), new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(block(-1, dp(13)), new LinearLayout.LayoutParams(-1, dp(13)));
        copy.addView(block(dp(110), dp(10)), new LinearLayout.LayoutParams(dp(110), dp(10)));
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, dp(48), 1);
        copyLp.leftMargin = dp(9);
        line.addView(copy, copyLp);
        card.addView(line);

        card.addView(block(-1, dp(10)), new LinearLayout.LayoutParams(-1, dp(10)));
        card.addView(block(dp(180), dp(10)), new LinearLayout.LayoutParams(dp(180), dp(10)));
        return card;
    }

    private View block(int width, int height) {
        View v = new View(this);
        v.setBackground(round(0xFF292C33, dp(5)));
        return v;
    }

    private void renderProjects() {
        projectRows.removeAllViews();
        if (projects.isEmpty()) {
            renderEmpty("No projects found");
            return;
        }

        int count = columnsForWidth();
        for (int start = 0; start < projects.size(); start += count) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            int rowCount = Math.min(count, projects.size() - start);
            for (int i = 0; i < rowCount; i++) {
                ModrinthProject project = projects.get(start + i);
                View card = createProjectCard(project);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(160), 1);
                lp.setMargins(dp(3), dp(3), dp(3), dp(3));
                row.addView(card, lp);
            }
            for (int i = rowCount; i < count; i++) {
                View spacer = new View(this);
                row.addView(spacer, new LinearLayout.LayoutParams(0, dp(160), 1));
            }
            projectRows.addView(row, new LinearLayout.LayoutParams(-1, dp(166)));
        }
        android.util.Log.d("OrynDownload", "UI item count: " + projects.size());
    }

    private int columnsForWidth() {
        int widthDp = getResources().getDisplayMetrics().widthPixels;
        int side = dp(178 + 305 + 32);
        int centerPx = Math.max(dp(280), widthDp - side);
        int centerDp = (int)(centerPx / getResources().getDisplayMetrics().density);
        if (centerDp >= 1000) return 3;
        if (centerDp >= 610) return 2;
        return 1;
    }

    private View createProjectCard(final ModrinthProject project) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.TOP);
        card.setPadding(dp(10), dp(10), dp(10), dp(8));
        card.setBackground(round(0xFF17191E, dp(12)));
        card.setClickable(true);
        card.setFocusable(true);

        final ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setImageResource(category.placeholder);
        card.addView(icon, new LinearLayout.LayoutParams(dp(54), dp(54)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(0, -1, 1);
        bodyLp.leftMargin = dp(9);
        card.addView(body, bodyLp);

        TextView title = text(project.title, 14);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setMaxLines(1);
        body.addView(title);

        TextView author = text(project.author, 10);
        author.setTextColor(0xFF8D929D);
        author.setMaxLines(1);
        body.addView(author);

        TextView description = text(project.description, 10);
        description.setTextColor(0xFFB5B8C0);
        description.setMaxLines(2);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(-1, 0, 1);
        descLp.topMargin = dp(4);
        body.addView(description, descLp);

        TextView meta = text(formatDownloads(project.downloads) + " downloads • " + categoryLabel(project.projectType), 9);
        meta.setTextColor(0xFF777C87);
        body.addView(meta);

        TextView compatibility = text(
                project.gameVersions.isEmpty() ? selectedMinecraftVersion() : selectedMinecraftVersion()
                        + (selectedLoader() == null ? "" : " • " + selectedLoader()), 9);
        compatibility.setTextColor(0xFF9EA2AC);
        body.addView(compatibility);

        card.setOnClickListener(v -> {
            v.animate().scaleX(0.98f).scaleY(0.98f).setDuration(70)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()).start();
            selectProject(project);
        });

        repository.loadIconAsync(project.iconUrl, new ModrinthRepository.IconCallback() {
            @Override public void onSuccess(Bitmap bitmap) {
                runOnUiThread(() -> {
                    if (bitmap != null && !bitmap.isRecycled()) icon.setImageBitmap(bitmap);
                });
            }

            @Override public void onError() {
                // The Oryn placeholder remains; an icon failure never removes the card.
            }
        });

        android.util.Log.d("OrynDownload", "Rendering project: " + project.id + " • " + project.title);
        android.util.Log.d("OrynDownload", "Project ID: " + project.id);
        android.util.Log.d("OrynDownload", "Project name: " + project.title);
        return card;
    }

    private void selectProject(ModrinthProject project) {
        selectedProject = project;
        selectedCompatibleVersions.clear();
        selectedVersion = null;
        detailTitle.setText(project.title);
        detailAuthor.setText("by " + project.author);
        detailDescription.setText(project.description);
        detailInfo.setText(buildProjectInfo(project));
        detailIcon.setImageResource(category.placeholder);
        detailDownload.setEnabled(false);
        detailDownload.setText("CHECKING…");
        detailProgress.setVisibility(View.VISIBLE);
        detailVersionSpinner.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item,
                Collections.singletonList("Checking compatible versions…")));

        repository.loadProjectDetailsAsync(project, category.projectType,
                selectedMinecraftVersion(), selectedLoader(),
                new ModrinthRepository.DetailsCallback() {
                    @Override public void onLoading() {}

                    @Override public void onSuccess(ModrinthProject fullProject, List<ModrinthVersion> compatibleVersions) {
                        runOnUiThread(() -> {
                            if (selectedProject != project) return;
                            selectedCompatibleVersions = compatibleVersions;
                            detailProgress.setVisibility(View.GONE);

                            ArrayList<String> labels = new ArrayList<>();
                            for (ModrinthVersion version : compatibleVersions) {
                                String loader = ("mod".equals(category.projectType) || "modpack".equals(category.projectType))
                                        ? firstLoader(version.loaders) : null;
                                labels.add(version.versionNumber.isEmpty() ? version.name
                                        : version.versionNumber + (loader == null ? "" : " • " + loader));
                            }
                            if (labels.isEmpty()) {
                                detailVersionSpinner.setAdapter(new ArrayAdapter<>(
                                        this, android.R.layout.simple_spinner_dropdown_item,
                                        Collections.singletonList("No compatible version/file")));
                                detailDownload.setEnabled(false);
                                detailDownload.setText("DOWNLOAD");
                                detailInfo.setText(buildProjectInfo(fullProject) + "\n\nNo compatible downloadable file.");
                            } else {
                                detailVersionSpinner.setAdapter(new ArrayAdapter<>(
                                        this, android.R.layout.simple_spinner_dropdown_item, labels));
                                selectedVersion = compatibleVersions.get(0);
                                detailVersionSpinner.setSelection(0);
                                boolean installed = isInstalled(project);
                                detailDownload.setEnabled(selectedInstance != null);
                                detailDownload.setText(installed ? "INSTALLED ✓" : "DOWNLOAD");
                                detailInfo.setText(buildProjectInfo(fullProject)
                                        + "\n\n" + compatibleVersions.size() + " compatible versions");
                            }

                            repository.loadIconAsync(fullProject.iconUrl, new ModrinthRepository.IconCallback() {
                                @Override public void onSuccess(Bitmap bitmap) {
                                    runOnUiThread(() -> {
                                        if (selectedProject == project && bitmap != null && !bitmap.isRecycled()) {
                                            detailIcon.setImageBitmap(bitmap);
                                        }
                                    });
                                }
                                @Override public void onError() {}
                            });
                        });
                    }

                    @Override public void onError(Exception error) {
                        runOnUiThread(() -> {
                            if (selectedProject != project) return;
                            detailProgress.setVisibility(View.GONE);
                            detailDownload.setEnabled(false);
                            detailDownload.setText("DOWNLOAD");
                            detailInfo.setText(buildProjectInfo(project) + "\n\nUnable to load versions.");
                            Toast.makeText(OrynDownloadActivity.this,
                                    "Could not load project versions", Toast.LENGTH_SHORT).show();
                        });
                    }
                });

        detailVersionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < selectedCompatibleVersions.size()) {
                    selectedVersion = selectedCompatibleVersions.get(position);
                    detailDownload.setEnabled(selectedInstance != null);
                    detailDownload.setText(isInstalled(project) ? "INSTALLED ✓" : "DOWNLOAD");
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void installSelectedProject() {
        if (selectedProject == null || selectedVersion == null) return;
        if (selectedInstance == null) {
            Toast.makeText(this, "Select an instance first", Toast.LENGTH_LONG).show();
            return;
        }

        if (isInstalled(selectedProject)) {
            detailDownload.setText("INSTALLED ✓");
            Toast.makeText(this, "Already installed", Toast.LENGTH_SHORT).show();
            return;
        }

        final ModrinthProject project = selectedProject;
        final ModrinthVersion version = selectedVersion;
        final String mcVersion = selectedMinecraftVersion();
        final String loader = selectedLoader();

        detailDownload.setEnabled(false);
        detailProgress.setVisibility(View.VISIBLE);

        installer.installAsync(project, version, category.projectType, mcVersion, loader,
                selectedInstance, new ModrinthInstaller.Callback() {
                    @Override public void onState(DownloadState state) {
                        runOnUiThread(() -> {
                            if (state.status == DownloadState.Status.DOWNLOADING) {
                                detailProgress.setIndeterminate(false);
                                detailProgress.setProgress(state.progress);
                                detailDownload.setText("Downloading " + state.progress + "%");
                                status.setText(project.title + " • " + state.progress + "%");
                            } else if (state.status == DownloadState.Status.INSTALLING) {
                                detailProgress.setIndeterminate(true);
                                detailDownload.setText("Installing…");
                                status.setText("Installing " + project.title + "…");
                            } else if (state.status == DownloadState.Status.CHECKING) {
                                detailProgress.setIndeterminate(true);
                                detailDownload.setText("Checking…");
                                status.setText("Checking " + project.title + "…");
                            }
                        });
                    }

                    @Override public void onSuccess(ModrinthFile file) {
                        runOnUiThread(() -> {
                            detailProgress.setVisibility(View.GONE);
                            detailDownload.setEnabled(true);
                            detailDownload.setText("INSTALLED ✓");
                            status.setText("Installed successfully");
                            Toast.makeText(OrynDownloadActivity.this,
                                    "Installed " + project.title, Toast.LENGTH_LONG).show();
                        });
                    }

                    @Override public void onError(Exception error) {
                        runOnUiThread(() -> {
                            detailProgress.setVisibility(View.GONE);
                            detailDownload.setEnabled(true);
                            detailDownload.setText("RETRY");
                            status.setText("Download failed");
                            Toast.makeText(OrynDownloadActivity.this,
                                    error.getMessage() == null ? "Download failed" : error.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    private boolean isInstalled(ModrinthProject project) {
        return selectedInstance != null
                && installedStore.isInstalled(selectedInstance.getGameDirectory(), project.id, category.projectType);
    }

    private void chooseInstance() {
        instanceExecutor.execute(() -> {
            try {
                final List<Instance> instances = Instances.loadAllInstances();
                final String[] names = new String[instances == null ? 0 : instances.size()];
                if (instances != null) {
                    for (int i = 0; i < instances.size(); i++) {
                        Instance item = instances.get(i);
                        names[i] = item.name == null || item.name.trim().isEmpty()
                                ? item.versionId : item.name;
                    }
                }
                runOnUiThread(() -> {
                    if (names.length == 0) {
                        Toast.makeText(this, "No Minecraft instances found", Toast.LENGTH_LONG).show();
                        return;
                    }
                    new AlertDialog.Builder(this)
                            .setTitle("Install into instance")
                            .setItems(names, (dialog, which) -> {
                                if (instances != null && which >= 0 && which < instances.size()) {
                                    selectedInstance = instances.get(which);
                                    updateInstanceUi();
                                    performSearch(true);
                                    if (selectedProject != null) selectProject(selectedProject);
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Could not load instances", Toast.LENGTH_LONG).show());
            }
        });
    }

    private void updateInstanceUi() {
        String name = selectedInstance == null ? "Select an instance" :
                (selectedInstance.name == null || selectedInstance.name.trim().isEmpty()
                        ? selectedInstance.versionId : selectedInstance.name);
        instanceButton.setText(name + " ▼");

        View target = findViewByTag("instance_target");
        if (target instanceof TextView) {
            TextView text = (TextView) target;
            if (selectedInstance == null) text.setText("Select an instance first");
            else text.setText(name + "\n" + selectedMinecraftVersion());
        }

        if (selectedProject != null) {
            detailDownload.setEnabled(selectedVersion != null && selectedInstance != null);
            detailDownload.setText(isInstalled(selectedProject) ? "INSTALLED ✓" : "DOWNLOAD");
        }
    }

    private String selectedMinecraftVersion() {
        if (versionSpinner != null && versionSpinner.getSelectedItem() != null) {
            return versionSpinner.getSelectedItem().toString();
        }
        return selectedInstance == null ? null : minecraftVersionFromInstance(selectedInstance.versionId);
    }

    private String selectedLoader() {
        if (category != Category.MOD && category != Category.MODPACK) return null;
        if (loaderSpinner != null && loaderSpinner.getSelectedItem() != null) {
            String value = loaderSpinner.getSelectedItem().toString();
            if (!"Auto".equalsIgnoreCase(value)) return value.toLowerCase(Locale.ROOT);
        }
        return detectLoader(selectedInstance);
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

    private void clearDetails() {
        detailIcon.setImageResource(category.placeholder);
        detailTitle.setText("Select a project");
        detailAuthor.setText("");
        detailInfo.setText("");
        detailDescription.setText("Choose a real Modrinth project to inspect compatible versions and install it into the selected instance.");
        detailVersionSpinner.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item,
                Collections.singletonList("Select a project")));
        detailDownload.setEnabled(false);
        detailDownload.setText("DOWNLOAD");
        detailProgress.setVisibility(View.GONE);
    }

    private void renderEmpty(String message) {
        projectRows.removeAllViews();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(20), dp(35), dp(20), dp(35));

        TextView title = text(message, 18);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        box.addView(title);

        TextView hint = text("Try another search, Minecraft version, or loader.", 11);
        hint.setTextColor(0xFF808590);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-1, dp(30));
        hintLp.topMargin = dp(6);
        box.addView(hint, hintLp);
        projectRows.addView(box, new LinearLayout.LayoutParams(-1, dp(150)));
    }

    private String buildProjectInfo(ModrinthProject project) {
        StringBuilder builder = new StringBuilder();
        builder.append(formatDownloads(project.downloads)).append(" downloads");
        if (project.followers > 0) builder.append(" • ").append(project.followers).append(" followers");
        builder.append("\nType: ").append(project.projectType);
        if (!project.categories.isEmpty()) builder.append("\nCategories: ").append(join(project.categories, 6));
        if (!project.gameVersions.isEmpty()) builder.append("\nVersions: ").append(join(project.gameVersions, 10));
        if (!project.loaders.isEmpty()) builder.append("\nLoaders: ").append(join(project.loaders, 8));
        return builder.toString();
    }

    private String firstLoader(List<String> values) {
        if (values == null || values.isEmpty()) return null;
        return values.get(0);
    }

    private String join(List<String> values, int max) {
        StringBuilder builder = new StringBuilder();
        int count = Math.min(max, values.size());
        for (int i = 0; i < count; i++) {
            if (i > 0) builder.append(", ");
            builder.append(values.get(i));
        }
        if (values.size() > count) builder.append("…");
        return builder.toString();
    }

    private String categoryLabel(String projectType) {
        if ("resourcepack".equals(projectType)) return "PACK";
        if ("modpack".equals(projectType)) return "MODPACK";
        if ("shader".equals(projectType)) return "SHADER";
        return "MOD";
    }

    private String formatDownloads(long value) {
        if (value >= 1000000000L) return String.format(Locale.ROOT, "%.1fB", value / 1000000000.0);
        if (value >= 1000000L) return String.format(Locale.ROOT, "%.1fM", value / 1000000.0);
        if (value >= 1000L) return String.format(Locale.ROOT, "%.1fK", value / 1000.0);
        return String.valueOf(value);
    }

    @Override
    protected void onDestroy() {
        repository.shutdown();
        installer.shutdown();
        instanceExecutor.shutdownNow();
        super.onDestroy();
    }
}
