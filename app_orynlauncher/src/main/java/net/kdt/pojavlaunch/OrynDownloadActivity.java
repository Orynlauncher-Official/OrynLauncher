package net.kdt.pojavlaunch;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.download.ModrinthProject;
import net.kdt.pojavlaunch.download.ModrinthVersion;
import net.kdt.pojavlaunch.download.OrynContentRepository;
import net.kdt.pojavlaunch.download.OrynDownloadState;
import net.kdt.pojavlaunch.download.OrynDownloadViewModel;
import net.kdt.pojavlaunch.download.OrynInstallState;
import net.kdt.pojavlaunch.download.OrynProjectAdapter;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;

/**
 * Single, clean Modrinth browser.
 *
 * Navigation is deliberately separated into:
 * HOME -> PROJECTS -> DETAILS -> VERSIONS -> INSTALL.
 *
 * The existing OrynDownloadViewModel/OrynInstallationManager remains the
 * installation boundary. This activity owns browsing/navigation only.
 */
public class OrynDownloadActivity extends AppCompatActivity {
    private enum Screen { HOME, PROJECTS, DETAILS, VERSIONS }

    private OrynDownloadViewModel viewModel;
    private OrynContentRepository repository;
    private OrynProjectAdapter projectAdapter;
    private ExecutorService instanceExecutor;

    private Screen screen = Screen.HOME;
    private OrynDownloadState.Category category = OrynDownloadState.Category.MOD;
    private Instance selectedInstance;

    private LinearLayout root;
    private LinearLayout content;
    private TextView title;
    private TextView subtitle;
    private Button instanceButton;
    private EditText search;
    private Spinner minecraftSpinner;
    private Spinner loaderSpinner;
    private RecyclerView projectList;
    private Button loadMore;
    private ProgressBar progress;
    private TextView message;

    private ImageView detailIcon;
    private TextView detailTitle;
    private TextView detailAuthor;
    private TextView detailMeta;
    private TextView detailDescription;
    private Button versionsButton;
    private Button detailInstallButton;

    private Spinner versionMinecraftSpinner;
    private Spinner versionLoaderSpinner;
    private RecyclerView versionList;
    private ProgressBar versionProgress;
    private Button versionInstallButton;

    private List<String> globalMinecraftVersions = new ArrayList<>();
    private boolean suppressFilterCallbacks;
    private boolean suppressVersionCallbacks;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);

        selectedInstance = Instances.loadSelectedInstance();
        viewModel = new OrynDownloadViewModel(selectedInstance);
        repository = new OrynContentRepository();
        instanceExecutor = Executors.newSingleThreadExecutor();

        viewModel.observe(this::renderState);
        buildShell();
        loadGlobalVersions();
        showHome();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, float size, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        return v;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(12);
        b.setBackground(round(0xFF20242C, dp(10)));
        b.setMinHeight(dp(40));
        return b;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF090A0D);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(6), dp(10), dp(6));
        header.setBackgroundColor(0xFF111318);

        Button back = button("‹");
        back.setTextSize(25);
        back.setOnClickListener(v -> goBack());
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(8), 0, dp(6), 0);
        title = text("ORYN DOWNLOAD CENTER", 17, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        subtitle = text("Browse real Modrinth projects", 10, 0xFF858A95);
        heading.addView(title, new LinearLayout.LayoutParams(-1, dp(24)));
        heading.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(18)));
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));

        instanceButton = button(instanceName());
        instanceButton.setOnClickListener(v -> chooseInstance());
        header.addView(instanceButton, new LinearLayout.LayoutParams(dp(145), dp(42)));

        root.addView(header);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(9), dp(8), dp(9), dp(8));
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        updateInstanceButton();
    }

    private void clearContent() {
        content.removeAllViews();
    }

    private void showHome() {
        screen = Screen.HOME;
        clearContent();
        title.setText("ORYN DOWNLOAD CENTER");
        subtitle.setText("Choose what you want to browse");

        TextView intro = text("MODRINTH", 10, 0xFF777D88);
        intro.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(intro, new LinearLayout.LayoutParams(-1, dp(22)));

        addCategoryCard(OrynDownloadState.Category.MOD, "Mods",
                "Minecraft mods • Fabric / Forge / NeoForge / Quilt");
        addCategoryCard(OrynDownloadState.Category.RESOURCEPACK, "Resource Packs",
                "Textures and resource packs for Minecraft");
        addCategoryCard(OrynDownloadState.Category.SHADER, "Shaders",
                "Shader packs and visual enhancements");
        addCategoryCard(OrynDownloadState.Category.MODPACK, "Modpacks",
                "Complete Modrinth .mrpack installations");

        TextView note = text("Nothing is downloaded from this screen. Tap a category, inspect a project, then choose a version.", 11, 0xFF747985);
        note.setPadding(0, dp(10), 0, 0);
        content.addView(note, new LinearLayout.LayoutParams(-1, dp(52)));
    }

    private void addCategoryCard(OrynDownloadState.Category value, String label, String desc) {
        Button card = button(label + "\n" + desc);
        card.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        card.setTextSize(13);
        card.setPadding(dp(14), 0, dp(8), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(68));
        lp.bottomMargin = dp(8);
        content.addView(card, lp);
        card.setOnClickListener(v -> {
            category = value;
            showProjects();
        });
    }

    private void showProjects() {
        screen = Screen.PROJECTS;
        clearContent();
        title.setText(category.title.toUpperCase(Locale.ROOT));
        subtitle.setText("Actual Modrinth projects • " + category.projectType);

        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setGravity(Gravity.CENTER_VERTICAL);

        minecraftSpinner = new Spinner(this);
        filterRow.addView(minecraftSpinner, new LinearLayout.LayoutParams(0, dp(42), 1));

        loaderSpinner = new Spinner(this);
        LinearLayout.LayoutParams loaderLp = new LinearLayout.LayoutParams(0, dp(42), 1);
        loaderLp.leftMargin = dp(6);
        filterRow.addView(loaderSpinner, loaderLp);

        content.addView(filterRow, new LinearLayout.LayoutParams(-1, dp(44)));
        updateProjectFilters();

        LinearLayout searchRow = new LinearLayout(this);
        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(0xFF6F7480);
        search.setHint("Search " + category.title.toLowerCase(Locale.ROOT) + " on Modrinth");
        search.setTextSize(12);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setBackground(round(0xFF181B21, dp(10)));
        search.setPadding(dp(12), 0, dp(8), 0);
        searchRow.addView(search, new LinearLayout.LayoutParams(0, dp(42), 1));

        Button searchButton = button("Search");
        LinearLayout.LayoutParams sb = new LinearLayout.LayoutParams(dp(82), dp(42));
        sb.leftMargin = dp(6);
        searchRow.addView(searchButton, sb);
        content.addView(searchRow, new LinearLayout.LayoutParams(-1, dp(46)));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        content.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));

        message = text("Loading projects…", 10, 0xFF858A95);
        content.addView(message, new LinearLayout.LayoutParams(-1, dp(28)));

        projectList = new RecyclerView(this);
        projectList.setClipToPadding(false);
        projectList.setPadding(0, 2, 0, dp(8));
        projectList.setLayoutManager(new GridLayoutManager(this, 1));
        projectAdapter = new OrynProjectAdapter(repository, project -> {
            viewModel.selectProject(project);
            showDetails();
        });
        projectList.setAdapter(projectAdapter);
        content.addView(projectList, new LinearLayout.LayoutParams(-1, 0, 1));

        loadMore = button("Load more projects");
        loadMore.setOnClickListener(v -> viewModel.loadMore());
        content.addView(loadMore, new LinearLayout.LayoutParams(-1, dp(42)));

        searchButton.setOnClickListener(v -> searchProjects());
        search.setOnEditorActionListener((v, actionId, event) -> {
            searchProjects();
            return true;
        });

        minecraftSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (!suppressFilterCallbacks) searchProjects();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        loaderSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (!suppressFilterCallbacks && category.usesLoader()) searchProjects();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        projectList.post(this::updateProjectColumns);
        searchProjects();
    }

    private void updateProjectColumns() {
        if (projectList == null) return;
        int width = (int) (projectList.getWidth() / getResources().getDisplayMetrics().density);
        int columns = getResources().getConfiguration().smallestScreenWidthDp < 600
                ? 1 : Math.max(1, Math.min(3, width / 320));
        RecyclerView.LayoutManager lm = projectList.getLayoutManager();
        if (!(lm instanceof GridLayoutManager) || ((GridLayoutManager) lm).getSpanCount() != columns) {
            projectList.setLayoutManager(new GridLayoutManager(this, columns));
        }
    }

    private void updateProjectFilters() {
        suppressFilterCallbacks = true;
        List<String> versions = new ArrayList<>();
        if (!globalMinecraftVersions.isEmpty()) versions.addAll(globalMinecraftVersions);
        if (versions.isEmpty()) versions.add(currentMinecraftVersion());

        setSpinner(minecraftSpinner, versions, currentMinecraftVersion());
        setSpinner(loaderSpinner, loaderChoices(), currentLoader());
        loaderSpinner.setVisibility(category.usesLoader() ? View.VISIBLE : View.GONE);
        suppressFilterCallbacks = false;
    }

    private List<String> loaderChoices() {
        ArrayList<String> values = new ArrayList<>();
        if (category.usesLoader()) {
            values.add("fabric");
            values.add("forge");
            values.add("neoforge");
            values.add("quilt");
        }
        return values;
    }

    private void searchProjects() {
        if (minecraftSpinner == null) return;
        String version = selectedItem(minecraftSpinner, currentMinecraftVersion());
        String loader = category.usesLoader() ? selectedItem(loaderSpinner, currentLoader()) : null;
        String query = search == null ? "" : search.getText().toString().trim();
        viewModel.setFilters(category, version, loader, query);
    }

    private void showDetails() {
        screen = Screen.DETAILS;
        clearContent();
        title.setText("PROJECT");
        subtitle.setText("Inspect the project before installing");

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);

        detailIcon = new ImageView(this);
        detailIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        detailIcon.setImageResource(placeholder());
        top.addView(detailIcon, new LinearLayout.LayoutParams(dp(82), dp(82)));

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(dp(12), 0, 0, 0);
        detailTitle = text("Loading project…", 20, Color.WHITE);
        detailTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        detailAuthor = text("", 11, 0xFF858A95);
        names.addView(detailTitle, new LinearLayout.LayoutParams(-1, dp(30)));
        names.addView(detailAuthor, new LinearLayout.LayoutParams(-1, dp(22)));
        top.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(top);

        detailMeta = text("", 10, 0xFFB8BCC4);
        detailMeta.setPadding(0, dp(10), 0, dp(4));
        content.addView(detailMeta, new LinearLayout.LayoutParams(-1, dp(75)));

        detailDescription = text("Loading details…", 11, 0xFFB7BBC3);
        detailDescription.setGravity(Gravity.TOP);
        content.addView(detailDescription, new LinearLayout.LayoutParams(-1, 0, 1));

        versionsButton = button("VIEW VERSIONS");
        versionsButton.setOnClickListener(v -> showVersions());
        content.addView(versionsButton, new LinearLayout.LayoutParams(-1, dp(46)));

        detailInstallButton = button("SELECT A VERSION");
        detailInstallButton.setEnabled(false);
        detailInstallButton.setOnClickListener(v -> showVersions());
        content.addView(detailInstallButton, new LinearLayout.LayoutParams(-1, dp(42)));

        renderState(viewModel.getState());
    }

    private void showVersions() {
        OrynDownloadState state = viewModel.getState();
        if (state.selectedProject == null) return;

        screen = Screen.VERSIONS;
        clearContent();
        title.setText("VERSIONS");
        subtitle.setText(state.selectedProject.title);

        LinearLayout filterRow = new LinearLayout(this);
        versionMinecraftSpinner = new Spinner(this);
        versionLoaderSpinner = new Spinner(this);
        filterRow.addView(versionMinecraftSpinner, new LinearLayout.LayoutParams(0, dp(42), 1));
        LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(0, dp(42), 1);
        vl.leftMargin = dp(6);
        filterRow.addView(versionLoaderSpinner, vl);
        content.addView(filterRow, new LinearLayout.LayoutParams(-1, dp(44)));

        versionProgress = new ProgressBar(this);
        versionProgress.setVisibility(View.GONE);
        content.addView(versionProgress, new LinearLayout.LayoutParams(-1, dp(3)));

        TextView hint = text("Choose Minecraft version and loader when applicable. Only real Modrinth versions/files can be installed.", 10, 0xFF7D828D);
        content.addView(hint, new LinearLayout.LayoutParams(-1, dp(34)));

        versionList = new RecyclerView(this);
        versionList.setLayoutManager(new GridLayoutManager(this, 1));
        content.addView(versionList, new LinearLayout.LayoutParams(-1, 0, 1));

        versionInstallButton = button("INSTALL SELECTED VERSION");
        versionInstallButton.setEnabled(false);
        versionInstallButton.setOnClickListener(v -> viewModel.installSelected());
        content.addView(versionInstallButton, new LinearLayout.LayoutParams(-1, dp(46)));

        setupVersionSelectors(state);
        renderVersionScreen(state);
    }

    private void setupVersionSelectors(OrynDownloadState state) {
        List<String> versions = supportedMinecraftVersions(state.compatibleVersions);
        if (versions.isEmpty()) versions.add(currentMinecraftVersion());

        suppressVersionCallbacks = true;
        setSpinner(versionMinecraftSpinner, versions,
                versions.contains(currentMinecraftVersion()) ? currentMinecraftVersion() : versions.get(0));
        updateVersionLoaderSpinner(state, selectedItem(versionMinecraftSpinner, versions.get(0)));
        suppressVersionCallbacks = false;

        versionMinecraftSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (suppressVersionCallbacks) return;
                updateVersionLoaderSpinner(viewModel.getState(), selectedItem(versionMinecraftSpinner, ""));
                selectFirstMatchingVersion();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        versionLoaderSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (suppressVersionCallbacks) return;
                selectFirstMatchingVersion();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private void updateVersionLoaderSpinner(OrynDownloadState state, String minecraftVersion) {
        if (versionLoaderSpinner == null) return;
        List<String> loaders = supportedLoaders(state.compatibleVersions, minecraftVersion);
        if (!category.usesLoader()) {
            versionLoaderSpinner.setVisibility(View.GONE);
            return;
        }
        versionLoaderSpinner.setVisibility(View.VISIBLE);
        if (loaders.isEmpty()) loaders.add("No loader");
        suppressVersionCallbacks = true;
        setSpinner(versionLoaderSpinner, loaders,
                loaders.contains(currentLoader()) ? currentLoader() : loaders.get(0));
        suppressVersionCallbacks = false;
    }

    private void selectFirstMatchingVersion() {
        OrynDownloadState state = viewModel.getState();
        if (state.compatibleVersions.isEmpty()) {
            renderVersionScreen(state);
            return;
        }
        String mc = selectedItem(versionMinecraftSpinner, "");
        String loader = category.usesLoader() ? selectedItem(versionLoaderSpinner, "") : null;

        ModrinthVersion chosen = null;
        for (ModrinthVersion v : state.compatibleVersions) {
            if (!contains(v.gameVersions, mc)) continue;
            if (category.usesLoader() && !contains(v.loaders, loader)) continue;
            chosen = v;
            break;
        }
        viewModel.setVersionContext(mc, category.usesLoader() ? loader : null);
        if (chosen != null) viewModel.selectVersion(chosen);
        renderVersionScreen(viewModel.getState());
    }

    private void renderVersionScreen(OrynDownloadState state) {
        if (versionList == null || state.selectedProject == null) return;
        if (state.detailStatus == OrynDownloadState.DetailStatus.LOADING) {
            if (versionProgress != null) versionProgress.setVisibility(View.VISIBLE);
            return;
        }
        if (versionProgress != null) versionProgress.setVisibility(View.GONE);

        final String mc = selectedItem(versionMinecraftSpinner, "");
        final String loader = category.usesLoader() ? selectedItem(versionLoaderSpinner, "") : null;
        ArrayList<ModrinthVersion> visible = new ArrayList<>();
        for (ModrinthVersion v : state.compatibleVersions) {
            if (!contains(v.gameVersions, mc)) continue;
            if (category.usesLoader() && (loader == null || !contains(v.loaders, loader))) continue;
            visible.add(v);
        }

        VersionAdapter adapter = new VersionAdapter(visible, state.selectedVersion, v -> {
            viewModel.selectVersion(v);
            renderVersionScreen(viewModel.getState());
        });
        versionList.setAdapter(adapter);

        boolean valid = state.selectedVersion != null
                && contains(state.selectedVersion.gameVersions, mc)
                && (!category.usesLoader() || contains(state.selectedVersion.loaders, loader))
                && viewModel.isVersionCompatible(state.selectedVersion, mc,
                        category.usesLoader() ? loader : null)
                && (state.selectedInstance != null || category == OrynDownloadState.Category.MODPACK);
        versionInstallButton.setEnabled(valid && state.installState.status != OrynInstallState.Status.DOWNLOADING
                && state.installState.status != OrynInstallState.Status.INSTALLING
                && state.installState.status != OrynInstallState.Status.CHECKING);

        switch (state.installState.status) {
            case DOWNLOADING:
                versionInstallButton.setEnabled(false);
                versionInstallButton.setText("DOWNLOADING " + state.installState.progress + "%");
                break;
            case INSTALLING:
                versionInstallButton.setEnabled(false);
                versionInstallButton.setText("INSTALLING…");
                break;
            case CHECKING:
                versionInstallButton.setEnabled(false);
                versionInstallButton.setText("CHECKING…");
                break;
            case INSTALLED:
                versionInstallButton.setEnabled(false);
                versionInstallButton.setText(category == OrynDownloadState.Category.MODPACK
                        ? "INSTANCE CREATED ✓" : "INSTALLED ✓");
                break;
            case FAILED:
                versionInstallButton.setEnabled(valid);
                versionInstallButton.setText("RETRY INSTALL");
                break;
            default:
                versionInstallButton.setText(valid ? "INSTALL SELECTED VERSION"
                        : "SELECT A COMPATIBLE VERSION");
        }
    }

    private void renderState(OrynDownloadState state) {
        if (isFinishing()) return;

        if (instanceButton != null) updateInstanceButton();

        if (screen == Screen.PROJECTS) {
            if (projectAdapter != null) {
                projectAdapter.setPlaceholder(placeholder());
                projectAdapter.submitList(state.projects);
                if (state.selectedProject != null) projectAdapter.setSelectedId(state.selectedProject.id);
            }
            if (progress != null) progress.setVisibility(
                    state.listStatus == OrynDownloadState.ListStatus.LOADING ? View.VISIBLE : View.GONE);
            if (message != null) {
                message.setText(state.listStatus == OrynDownloadState.ListStatus.ERROR
                        ? state.listMessage
                        : state.projects.size() + " " + category.title.toLowerCase(Locale.ROOT)
                                + (state.hasMore ? " • more available" : ""));
            }
            if (loadMore != null) {
                loadMore.setVisibility(state.hasMore ? View.VISIBLE : View.GONE);
                loadMore.setEnabled(state.listStatus != OrynDownloadState.ListStatus.LOADING);
            }
        }

        if (screen == Screen.DETAILS) renderDetails(state);
        if (screen == Screen.VERSIONS) renderVersionScreen(state);
    }

    private void renderDetails(OrynDownloadState state) {
        if (state.selectedProject == null) return;
        ModrinthProject p = state.selectedProject;
        detailTitle.setText(p.title);
        detailAuthor.setText("by " + safe(p.author));
        detailMeta.setText(formatProjectMeta(p));
        detailDescription.setText(p.description == null || p.description.isEmpty()
                ? "No description available." : p.description);

        if (state.detailStatus == OrynDownloadState.DetailStatus.LOADING) {
            versionsButton.setText("LOADING VERSIONS…");
            versionsButton.setEnabled(false);
            detailInstallButton.setEnabled(false);
            return;
        }

        versionsButton.setEnabled(!state.compatibleVersions.isEmpty());
        versionsButton.setText("VIEW " + state.compatibleVersions.size() + " VERSIONS");
        detailInstallButton.setText(state.compatibleVersions.isEmpty()
                ? "NO DOWNLOADABLE VERSIONS" : "SELECT A VERSION");
        detailInstallButton.setEnabled(!state.compatibleVersions.isEmpty());

        repository.loadIcon(p.iconUrl, new OrynContentRepository.Listener<Bitmap>() {
            @Override public void onSuccess(Bitmap bitmap) {
                runOnUiThread(() -> {
                    if (detailIcon != null && bitmap != null && !bitmap.isRecycled()) {
                        detailIcon.setImageBitmap(bitmap);
                    }
                });
            }
            @Override public void onError(Exception error) {}
        });
    }

    private void loadGlobalVersions() {
        repository.loadGameVersions(new OrynContentRepository.Listener<List<String>>() {
            @Override public void onSuccess(List<String> values) {
                runOnUiThread(() -> globalMinecraftVersions = new ArrayList<>(values));
            }
            @Override public void onError(Exception error) {}
        });
    }

    private void setSpinner(Spinner spinner, List<String> values, String selected) {
        if (spinner == null) return;
        ArrayList<String> safe = new ArrayList<>(values == null ? Collections.emptyList() : values);
        if (safe.isEmpty()) safe.add("—");
        spinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, safe));
        int index = selected == null ? -1 : safe.indexOf(selected);
        spinner.setSelection(index < 0 ? 0 : index);
    }

    private List<String> supportedMinecraftVersions(List<ModrinthVersion> versions) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (ModrinthVersion v : versions) {
            if (v.gameVersions != null) values.addAll(v.gameVersions);
        }
        return new ArrayList<>(values);
    }

    private List<String> supportedLoaders(List<ModrinthVersion> versions, String mc) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (ModrinthVersion v : versions) {
            if (contains(v.gameVersions, mc) && v.loaders != null) values.addAll(v.loaders);
        }
        return new ArrayList<>(values);
    }

    private boolean contains(List<String> values, String wanted) {
        if (values == null || wanted == null) return false;
        for (String value : values) if (wanted.equalsIgnoreCase(value)) return true;
        return false;
    }

    private String selectedItem(Spinner spinner, String fallback) {
        Object item = spinner == null ? null : spinner.getSelectedItem();
        return item == null ? fallback : item.toString();
    }

    private String currentMinecraftVersion() {
        return selectedInstance == null ? "1.21.11" : minecraftVersionFromInstance(selectedInstance.versionId);
    }

    private String currentLoader() {
        String id = selectedInstance == null || selectedInstance.versionId == null
                ? "" : selectedInstance.versionId.toLowerCase(Locale.ROOT);
        if (id.contains("neoforge")) return "neoforge";
        if (id.contains("forge")) return "forge";
        if (id.contains("quilt")) return "quilt";
        return "fabric";
    }

    private String minecraftVersionFromInstance(String id) {
        if (id == null || id.trim().isEmpty()) return "1.21.11";
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^(\\d+\\.\\d+(?:\\.\\d+)?)").matcher(id.trim());
        return m.find() ? m.group(1) : id.trim();
    }

    private String instanceName() {
        if (selectedInstance == null) return "Select instance";
        return selectedInstance.name == null || selectedInstance.name.trim().isEmpty()
                ? selectedInstance.versionId : selectedInstance.name;
    }

    private void updateInstanceButton() {
        if (instanceButton != null) instanceButton.setText(instanceName());
    }

    private void chooseInstance() {
        instanceExecutor.execute(() -> {
            List<Instance> values = Instances.loadAllInstances();
            runOnUiThread(() -> {
                if (values == null || values.isEmpty()) {
                    Toast.makeText(this, "No Minecraft instances found", Toast.LENGTH_LONG).show();
                    return;
                }
                String[] names = new String[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    names[i] = values.get(i).name == null || values.get(i).name.trim().isEmpty()
                            ? values.get(i).versionId : values.get(i).name;
                }
                new AlertDialog.Builder(this)
                        .setTitle("Install into instance")
                        .setItems(names, (dialog, which) -> {
                            selectedInstance = values.get(which);
                            viewModel.setInstance(selectedInstance);
                            updateInstanceButton();
                            if (screen == Screen.PROJECTS) searchProjects();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        });
    }

    private int placeholder() {
        if (category == OrynDownloadState.Category.RESOURCEPACK) return R.drawable.oryn_download_resource;
        if (category == OrynDownloadState.Category.SHADER) return R.drawable.oryn_download_shader;
        if (category == OrynDownloadState.Category.MODPACK) return R.drawable.oryn_download_mod;
        return R.drawable.oryn_download_mod;
    }

    private String formatProjectMeta(ModrinthProject p) {
        return compact(p.downloads) + " downloads • " + p.followers + " followers\n"
                + "Type: " + p.projectType
                + "\nMinecraft: " + join(p.gameVersions, 8)
                + (p.loaders == null || p.loaders.isEmpty() ? "" : "\nLoaders: " + join(p.loaders, 8));
    }

    private String join(List<String> values, int max) {
        if (values == null || values.isEmpty()) return "—";
        StringBuilder b = new StringBuilder();
        int n = Math.min(max, values.size());
        for (int i = 0; i < n; i++) {
            if (i > 0) b.append(", ");
            b.append(values.get(i));
        }
        if (values.size() > n) b.append("…");
        return b.toString();
    }

    private String compact(long value) {
        if (value >= 1000000000L) return String.format(Locale.ROOT, "%.1fB", value / 1000000000d);
        if (value >= 1000000L) return String.format(Locale.ROOT, "%.1fM", value / 1000000d);
        if (value >= 1000L) return String.format(Locale.ROOT, "%.1fK", value / 1000d);
        return String.valueOf(value);
    }

    private String safe(String value) {
        return value == null || value.isEmpty() ? "Unknown author" : value;
    }

    private void goBack() {
        if (screen == Screen.HOME) {
            finish();
        } else if (screen == Screen.PROJECTS) {
            showHome();
        } else if (screen == Screen.DETAILS) {
            showProjects();
        } else {
            showDetails();
        }
    }

    @Override public void onBackPressed() {
        goBack();
    }

    @Override protected void onDestroy() {
        if (viewModel != null) viewModel.shutdown();
        if (repository != null) repository.shutdown();
        if (instanceExecutor != null) instanceExecutor.shutdownNow();
        super.onDestroy();
    }

    private final class VersionAdapter extends RecyclerView.Adapter<VersionAdapter.Holder> {
        private final List<ModrinthVersion> items;
        private ModrinthVersion selected;
        private final Listener listener;

        interface Listener { void onClick(ModrinthVersion version); }

        VersionAdapter(List<ModrinthVersion> items, ModrinthVersion selected, Listener listener) {
            this.items = items;
            this.selected = selected;
            this.listener = listener;
        }

        @Override public Holder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            LinearLayout card = new LinearLayout(parent.getContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12), dp(9), dp(12), dp(9));
            card.setBackground(round(0xFF171A20, dp(12)));
            return new Holder(card);
        }

        @Override public void onBindViewHolder(Holder h, int position) {
            ModrinthVersion v = items.get(position);
            h.title.setText((v.versionNumber == null || v.versionNumber.isEmpty() ? v.name : v.versionNumber)
                    + (v.featured ? "  •  Latest" : ""));
            h.meta.setText("Modrinth version ID: " + v.id + "\n"
                    + "Minecraft: " + join(v.gameVersions, 8)
                    + (v.loaders == null || v.loaders.isEmpty() ? "" : " • " + join(v.loaders, 5))
                    + "\nFiles: " + (v.files == null ? 0 : v.files.size()));
            boolean compatible = viewModel.isVersionCompatible(v,
                    mc, category.usesLoader() ? loader : null);
            h.action.setText(compatible ? "SELECT" : "NOT COMPATIBLE");
            h.action.setEnabled(compatible);
            h.card.setBackground(round(v == selected ? 0xFF262C37 : 0xFF171A20, dp(12)));
            h.card.setOnClickListener(x -> {
                if (compatible) {
                    selected = v;
                    listener.onClick(v);
                }
            });
        }

        @Override public int getItemCount() { return items.size(); }

        final class Holder extends RecyclerView.ViewHolder {
            final LinearLayout card;
            final TextView title;
            final TextView meta;
            final Button action;
            Holder(View view) {
                super(view);
                card = (LinearLayout) view;
                title = text("", 14, Color.WHITE);
                title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                meta = text("", 10, 0xFF969BA5);
                action = button("SELECT");
                card.addView(title, new LinearLayout.LayoutParams(-1, dp(24)));
                card.addView(meta, new LinearLayout.LayoutParams(-1, dp(54)));
                card.addView(action, new LinearLayout.LayoutParams(-1, dp(38)));
                action.setOnClickListener(v -> card.performClick());
            }
        }
    }
}
