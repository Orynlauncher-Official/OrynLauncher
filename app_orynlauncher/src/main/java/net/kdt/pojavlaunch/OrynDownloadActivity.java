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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.download.OrynContentRepository;
import net.kdt.pojavlaunch.download.OrynDownloadState;
import net.kdt.pojavlaunch.download.OrynInstallState;
import net.kdt.pojavlaunch.download.OrynDownloadViewModel;
import net.kdt.pojavlaunch.download.OrynProjectAdapter;
import net.kdt.pojavlaunch.download.ModrinthProject;
import net.kdt.pojavlaunch.download.ModrinthVersion;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;

public class OrynDownloadActivity extends AppCompatActivity {
    private OrynDownloadViewModel viewModel;
    private OrynProjectAdapter projectAdapter;
    private OrynContentRepository filterRepository;
    private ExecutorService instanceExecutor;

    private Spinner versionSpinner;
    private Spinner loaderSpinner;
    private EditText search;
    private Button instanceButton;
    private Button loadMoreButton;
    private Button downloadButton;
    private ProgressBar listProgress;
    private ProgressBar detailProgress;
    private TextView categoryTitle;
    private TextView status;
    private TextView instanceTarget;
    private RecyclerView projectList;

    private ImageView detailIcon;
    private TextView detailTitle;
    private TextView detailAuthor;
    private TextView detailInfo;
    private TextView detailDescription;
    private Spinner detailVersionSpinner;

    private OrynDownloadState.Category category = OrynDownloadState.Category.MOD;
    private Instance selectedInstance;
    private List<String> availableVersions = new ArrayList<>();
    private List<String> availableLoaders = new ArrayList<>();
    private boolean suppressFilters;
    private boolean suppressDetailSelection;

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
        viewModel = new OrynDownloadViewModel(selectedInstance);
        filterRepository = new OrynContentRepository();
        instanceExecutor = Executors.newSingleThreadExecutor();

        buildUi();
        viewModel.observe(this::renderState);
        loadFilters();
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

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private void styleButton(Button button) {
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(12);
        button.setBackground(round(0xFF2A2E38, dp(10)));
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(0xFF0A0B0E);

        root.addView(buildSidebar(), new LinearLayout.LayoutParams(dp(178), -1));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setPadding(dp(16), dp(14), dp(10), dp(10));
        root.addView(center, new LinearLayout.LayoutParams(0, -1, 1));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        categoryTitle = text("Mods", 24);
        categoryTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(categoryTitle, new LinearLayout.LayoutParams(0, dp(42), 1));

        instanceButton = new Button(this);
        styleButton(instanceButton);
        instanceButton.setOnClickListener(v -> chooseInstance());
        header.addView(instanceButton, new LinearLayout.LayoutParams(dp(205), dp(40)));
        center.addView(header);

        LinearLayout filters = new LinearLayout(this);
        filters.setGravity(Gravity.CENTER_VERTICAL);
        filters.setPadding(0, dp(3), 0, dp(5));

        versionSpinner = new Spinner(this);
        filters.addView(versionSpinner, new LinearLayout.LayoutParams(dp(140), dp(38)));

        loaderSpinner = new Spinner(this);
        LinearLayout.LayoutParams loaderLp = new LinearLayout.LayoutParams(dp(115), dp(38));
        loaderLp.leftMargin = dp(7);
        filters.addView(loaderSpinner, loaderLp);

        center.addView(filters);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(0xFF707580);
        search.setTextSize(13);
        search.setHint("Search Modrinth content…");
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setPadding(dp(13), 0, dp(10), 0);
        search.setBackground(round(0xFF1A1C22, dp(10)));
        searchRow.addView(search, new LinearLayout.LayoutParams(0, dp(42), 1));

        Button searchButton = new Button(this);
        styleButton(searchButton);
        searchButton.setText("Search");
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(dp(82), dp(42));
        searchLp.leftMargin = dp(7);
        searchRow.addView(searchButton, searchLp);
        center.addView(searchRow);

        listProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        listProgress.setIndeterminate(true);
        listProgress.setVisibility(View.GONE);
        center.addView(listProgress, new LinearLayout.LayoutParams(-1, dp(3)));

        status = text("Loading Modrinth…", 11);
        status.setTextColor(0xFF8B909B);
        status.setPadding(0, dp(3), 0, dp(3));
        center.addView(status, new LinearLayout.LayoutParams(-1, dp(29)));

        projectList = new RecyclerView(this);
        projectList.setClipToPadding(false);
        projectList.setPadding(0, dp(3), dp(4), dp(12));
        projectList.setItemAnimator(new androidx.recyclerview.widget.DefaultItemAnimator());
        projectAdapter = new OrynProjectAdapter(
                filterRepository,
                project -> viewModel.selectProject(project));
        projectList.setLayoutManager(new GridLayoutManager(this, 1));
        projectList.setAdapter(projectAdapter);
        // center is vertical: width must fill the available column; weight belongs to height.
        // Using width=0 here makes RecyclerView measure at zero width, so its cards are created
        // but have no visible viewport. Keep the list as a full-width, weighted-height child.
        center.addView(projectList, new LinearLayout.LayoutParams(-1, 0, 1));

        loadMoreButton = new Button(this);
        styleButton(loadMoreButton);
        loadMoreButton.setText("Load more");
        loadMoreButton.setVisibility(View.GONE);
        loadMoreButton.setOnClickListener(v -> viewModel.loadMore());
        center.addView(loadMoreButton, new LinearLayout.LayoutParams(-1, dp(40)));

        root.addView(buildDetails(), new LinearLayout.LayoutParams(dp(320), -1));

        searchButton.setOnClickListener(v -> refreshSearch());
        search.setOnEditorActionListener((v, actionId, event) -> {
            refreshSearch();
            return true;
        });

        versionSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (!suppressFilters && position >= 0) refreshSearch();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        loaderSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (!suppressFilters && category.usesLoader() && position >= 0) refreshSearch();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // A RecyclerView without a LayoutManager receives adapter data but renders zero child views.
        // Install a safe one-column manager immediately, then adapt after the real width is known.
        projectList.post(this::updateGridColumns);
        projectList.addOnLayoutChangeListener((v, left, top, right, bottom,
                                                oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft) updateGridColumns();
        });

        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(root);
        updateInstanceUi();
        updateFilterVisibility();
        projectList.post(this::updateGridColumns);
    }

    private LinearLayout buildSidebar() {
        LinearLayout sidebar = new LinearLayout(this);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setPadding(dp(14), dp(16), dp(12), dp(12));
        sidebar.setBackgroundColor(0xFF111318);

        TextView brand = text("ORYNLAUNCHER", 12);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brand.setTextColor(0xFFD5D8DE);
        sidebar.addView(brand, new LinearLayout.LayoutParams(-1, dp(32)));

        TextView heading = text("Download", 24);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        sidebar.addView(heading, new LinearLayout.LayoutParams(-1, dp(45)));

        TextView sub = text("Real Modrinth content", 11);
        sub.setTextColor(0xFF7E838E);
        sidebar.addView(sub, new LinearLayout.LayoutParams(-1, dp(30)));

        addCategory(sidebar, OrynDownloadState.Category.MOD);
        addCategory(sidebar, OrynDownloadState.Category.RESOURCEPACK);
        addCategory(sidebar, OrynDownloadState.Category.SHADER);
        addCategory(sidebar, OrynDownloadState.Category.MODPACK);

        TextView spacer = text("", 1);
        sidebar.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));

        TextView targetLabel = text("INSTALLING TO", 9);
        targetLabel.setTextColor(0xFF686D78);
        sidebar.addView(targetLabel, new LinearLayout.LayoutParams(-1, dp(20)));

        instanceTarget = text("", 11);
        instanceTarget.setTextColor(0xFFD4D7DE);
        instanceTarget.setMaxLines(2);
        sidebar.addView(instanceTarget, new LinearLayout.LayoutParams(-1, dp(45)));

        return sidebar;
    }

    private void addCategory(LinearLayout sidebar, OrynDownloadState.Category value) {
        Button button = new Button(this);
        styleButton(button);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        button.setText(value.title);
        button.setOnClickListener(v -> {
            category = value;
            categoryTitle.setText(value.title);
            updateFilterVisibility();
            refreshSearch();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(44));
        lp.bottomMargin = dp(5);
        sidebar.addView(button, lp);
    }

    private LinearLayout buildDetails() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(18), dp(18), dp(16));
        panel.setBackgroundColor(0xFF111318);

        detailIcon = new ImageView(this);
        detailIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        detailIcon.setImageResource(R.drawable.oryn_download_mod);
        panel.addView(detailIcon, new LinearLayout.LayoutParams(dp(84), dp(84)));

        detailTitle = text("Select a project", 19);
        detailTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        detailTitle.setMaxLines(2);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, dp(48));
        titleLp.topMargin = dp(11);
        panel.addView(detailTitle, titleLp);

        detailAuthor = text("", 11);
        detailAuthor.setTextColor(0xFF8C919C);
        panel.addView(detailAuthor, new LinearLayout.LayoutParams(-1, dp(22)));

        detailInfo = text("", 10);
        detailInfo.setTextColor(0xFFB9BCC4);
        detailInfo.setMaxLines(8);
        panel.addView(detailInfo, new LinearLayout.LayoutParams(-1, dp(105)));

        detailDescription = text(
                "Select a project to inspect its Modrinth details and compatible versions.",
                11);
        detailDescription.setTextColor(0xFFB5B8C0);
        detailDescription.setMaxLines(10);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(-1, 0, 1);
        descLp.topMargin = dp(6);
        panel.addView(detailDescription, descLp);

        TextView versionLabel = text("COMPATIBLE VERSIONS", 9);
        versionLabel.setTextColor(0xFF707580);
        panel.addView(versionLabel, new LinearLayout.LayoutParams(-1, dp(22)));

        detailVersionSpinner = new Spinner(this);
        panel.addView(detailVersionSpinner, new LinearLayout.LayoutParams(-1, dp(38)));

        detailProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        detailProgress.setMax(100);
        detailProgress.setVisibility(View.GONE);
        panel.addView(detailProgress, new LinearLayout.LayoutParams(-1, dp(3)));

        downloadButton = new Button(this);
        styleButton(downloadButton);
        downloadButton.setText("DOWNLOAD");
        downloadButton.setEnabled(false);
        downloadButton.setOnClickListener(v -> viewModel.installSelected());
        panel.addView(downloadButton, new LinearLayout.LayoutParams(-1, dp(46)));

        detailVersionSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                OrynDownloadState state = viewModel.getState();
                if (!suppressDetailSelection && position >= 0 && position < state.compatibleVersions.size()) {
                    viewModel.selectVersion(state.compatibleVersions.get(position));
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        return panel;
    }

    private void loadFilters() {
        suppressFilters = true;
        filterRepository.loadGameVersions(new OrynContentRepository.Listener<List<String>>() {
            @Override public void onSuccess(List<String> values) {
                runOnUiThread(() -> {
                    availableVersions = new ArrayList<>(values);
                    if (availableVersions.isEmpty()) fallbackVersions();
                    setupVersionSpinner();
                    loadLoaders();
                });
            }

            @Override public void onError(Exception error) {
                runOnUiThread(() -> {
                    fallbackVersions();
                    setupVersionSpinner();
                    loadLoaders();
                });
            }
        });
    }

    private void loadLoaders() {
        filterRepository.loadLoaders(new OrynContentRepository.Listener<List<String>>() {
            @Override public void onSuccess(List<String> values) {
                runOnUiThread(() -> {
                    availableLoaders = new ArrayList<>(values);
                    setupLoaderSpinner();
                    suppressFilters = false;
                    updateFilterVisibility();
                    refreshSearch();
                });
            }

            @Override public void onError(Exception error) {
                runOnUiThread(() -> {
                    availableLoaders.clear();
                    Collections.addAll(availableLoaders, "fabric", "forge", "neoforge", "quilt");
                    setupLoaderSpinner();
                    suppressFilters = false;
                    updateFilterVisibility();
                    refreshSearch();
                });
            }
        });
    }

    private void fallbackVersions() {
        availableVersions.clear();
        Collections.addAll(availableVersions, "1.21.11", "1.21.10", "1.21.9");
    }

    private void setupVersionSpinner() {
        String preferred = selectedInstance == null
                ? "1.21.11" : minecraftVersionFromInstance(selectedInstance.versionId);
        if (preferred != null && !availableVersions.contains(preferred)) {
            availableVersions.add(0, preferred);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, availableVersions);
        versionSpinner.setAdapter(adapter);
        int index = availableVersions.indexOf(preferred);
        if (index >= 0) versionSpinner.setSelection(index);
    }

    private void setupLoaderSpinner() {
        ArrayList<String> values = new ArrayList<>();
        values.add("Auto");
        for (String loader : availableLoaders) {
            if (loader != null && !values.contains(loader)) values.add(loader);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, values);
        loaderSpinner.setAdapter(adapter);

        String detected = detectLoader(selectedInstance);
        if (detected != null) {
            int index = values.indexOf(detected);
            if (index >= 0) loaderSpinner.setSelection(index);
        }
    }

    private void refreshSearch() {
        String version = selectedMinecraftVersion();
        if (version == null || version.isEmpty()) {
            status.setText("Select a Minecraft version");
            return;
        }
        String loader = selectedLoader();
        viewModel.setFilters(category, version, loader, search.getText().toString().trim());
    }

    private void renderState(OrynDownloadState state) {
        if (LooperHolder.isMainThread()) {
            renderStateOnMain(state);
        } else {
            runOnUiThread(() -> renderStateOnMain(state));
        }
    }

    private void renderStateOnMain(OrynDownloadState state) {
        listProgress.setVisibility(state.listStatus == OrynDownloadState.ListStatus.LOADING
                ? View.VISIBLE : View.GONE);

        android.util.Log.d("OrynDownload", "Rendering cards = " + state.projects.size());
        projectAdapter.setPlaceholder(placeholder());
        projectAdapter.submitList(state.projects);
        projectAdapter.setSelectedId(state.selectedProject == null ? null : state.selectedProject.id);
        projectList.post(() -> android.util.Log.d("OrynDownload",
                "UI metrics: recycler=" + projectList.getWidth() + "x" + projectList.getHeight()
                        + " • adapter=" + projectAdapter.getItemCount()
                        + " • visibleChildren=" + projectList.getChildCount()
                        + " • visibility=" + projectList.getVisibility()
                        + " • alpha=" + projectList.getAlpha()));

        if (state.listStatus == OrynDownloadState.ListStatus.ERROR && state.projects.isEmpty()) {
            status.setText(state.listMessage == null ? "Could not load projects" : state.listMessage);
        } else if (state.projects.isEmpty() && state.listStatus == OrynDownloadState.ListStatus.READY) {
            status.setText("No projects found");
        } else if (!state.projects.isEmpty()) {
            String loader = state.category.usesLoader() ? " • " + state.loader : "";
            status.setText(state.projects.size() + " projects • " + state.minecraftVersion + loader);
        } else {
            status.setText("Loading Modrinth projects…");
        }

        loadMoreButton.setVisibility(
                state.hasMore && !state.projects.isEmpty() ? View.VISIBLE : View.GONE);
        loadMoreButton.setEnabled(state.listStatus != OrynDownloadState.ListStatus.LOADING);

        renderDetails(state);
    }

    private void renderDetails(OrynDownloadState state) {
        if (state.selectedProject == null) {
            clearDetails();
            return;
        }

        ModrinthProject project = state.selectedProject;
        detailTitle.setText(project.title);
        detailAuthor.setText("by " + safe(project.author));
        detailInfo.setText(buildProjectInfo(project));
        detailDescription.setText(project.description == null || project.description.isEmpty()
                ? "No description available." : project.description);

        if (state.detailStatus == OrynDownloadState.DetailStatus.LOADING) {
            detailProgress.setVisibility(View.VISIBLE);
            detailProgress.setIndeterminate(true);
            suppressDetailSelection = true;
            detailVersionSpinner.setAdapter(new ArrayAdapter<>(
                    this, android.R.layout.simple_spinner_dropdown_item,
                    Collections.singletonList("Loading compatible versions…")));
            suppressDetailSelection = false;
            downloadButton.setEnabled(false);
            downloadButton.setText("CHECKING…");
            return;
        }

        detailProgress.setVisibility(View.GONE);
        ArrayList<String> labels = new ArrayList<>();
        for (ModrinthVersion version : state.compatibleVersions) {
            String loader = state.category.usesLoader() && version.loaders != null
                    && !version.loaders.isEmpty() ? " • " + join(version.loaders, 2) : "";
            String label = version.versionNumber == null || version.versionNumber.isEmpty()
                    ? version.name : version.versionNumber;
            labels.add(label + loader);
        }

        if (labels.isEmpty()) {
            suppressDetailSelection = true;
            detailVersionSpinner.setAdapter(new ArrayAdapter<>(
                    this, android.R.layout.simple_spinner_dropdown_item,
                    Collections.singletonList("No compatible version/file")));
            suppressDetailSelection = false;
            downloadButton.setEnabled(false);
            downloadButton.setText("DOWNLOAD");
            return;
        }

        suppressDetailSelection = true;
        detailVersionSpinner.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, labels));
        int selected = state.selectedVersion == null
                ? 0 : state.compatibleVersions.indexOf(state.selectedVersion);
        if (selected < 0) selected = 0;
        if (detailVersionSpinner.getSelectedItemPosition() != selected) {
            detailVersionSpinner.setSelection(selected);
        }
        suppressDetailSelection = false;

        if (state.installState.status == OrynInstallState.Status.DOWNLOADING) {
            detailProgress.setIndeterminate(false);
            detailProgress.setProgress(state.installState.progress);
            detailProgress.setVisibility(View.VISIBLE);
            downloadButton.setEnabled(false);
            downloadButton.setText("Downloading " + state.installState.progress + "%");
        } else if (state.installState.status == OrynInstallState.Status.INSTALLING
                || state.installState.status == OrynInstallState.Status.CHECKING) {
            detailProgress.setIndeterminate(true);
            detailProgress.setVisibility(View.VISIBLE);
            downloadButton.setEnabled(false);
            downloadButton.setText(state.installState.status == OrynInstallState.Status.CHECKING
                    ? "CHECKING…" : "INSTALLING…");
        } else if (state.installState.status == OrynInstallState.Status.INSTALLED) {
            detailProgress.setVisibility(View.GONE);
            downloadButton.setEnabled(false);
            downloadButton.setText("INSTALLED ✓");
            status.setText("Installed " + project.title);
        } else if (state.installState.status == OrynInstallState.Status.FAILED) {
            detailProgress.setVisibility(View.GONE);
            downloadButton.setEnabled(state.selectedInstance != null && state.selectedVersion != null);
            downloadButton.setText("RETRY");
        } else {
            detailProgress.setVisibility(View.GONE);
            boolean hasInstance = state.selectedInstance != null;
            downloadButton.setEnabled(hasInstance && state.selectedVersion != null);
            downloadButton.setText(hasInstance ? "DOWNLOAD" : "SELECT INSTANCE");
        }

        filterRepository.loadIcon(project.iconUrl,
                new OrynContentRepository.Listener<Bitmap>() {
                    @Override public void onSuccess(Bitmap bitmap) {
                        if (state.selectedProject == null || !state.selectedProject.id.equals(project.id)) return;
                        runOnUiThread(() -> {
                            if (bitmap != null && !bitmap.isRecycled()) detailIcon.setImageBitmap(bitmap);
                        });
                    }
                    @Override public void onError(Exception error) { }
                });
    }

    private void clearDetails() {
        detailIcon.setImageResource(placeholder());
        detailTitle.setText("Select a project");
        detailAuthor.setText("");
        detailInfo.setText("");
        detailDescription.setText("Select a project to inspect its Modrinth details and compatible versions.");
        suppressDetailSelection = true;
        detailVersionSpinner.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item,
                Collections.singletonList("Select a project")));
        suppressDetailSelection = false;
        detailProgress.setVisibility(View.GONE);
        downloadButton.setEnabled(false);
        downloadButton.setText("DOWNLOAD");
    }

    private void chooseInstance() {
        instanceExecutor.execute(() -> {
            try {
                List<Instance> instances = Instances.loadAllInstances();
                final List<Instance> result = instances == null
                        ? Collections.<Instance>emptyList() : new ArrayList<>(instances);
                final String[] names = new String[result.size()];
                for (int i = 0; i < result.size(); i++) {
                    Instance item = result.get(i);
                    names[i] = item.name == null || item.name.trim().isEmpty()
                            ? item.versionId : item.name;
                }

                runOnUiThread(() -> {
                    if (result.isEmpty()) {
                        Toast.makeText(this, "No Minecraft instances found", Toast.LENGTH_LONG).show();
                        return;
                    }
                    new AlertDialog.Builder(this)
                            .setTitle("Install into instance")
                            .setItems(names, (dialog, which) -> {
                                selectedInstance = result.get(which);
                                viewModel.setInstance(selectedInstance);
                                updateInstanceUi();
                                refreshSearch();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(
                        this, "Could not load Minecraft instances", Toast.LENGTH_LONG).show());
            }
        });
    }

    private void updateInstanceUi() {
        String name = selectedInstance == null ? "Select an instance"
                : (selectedInstance.name == null || selectedInstance.name.trim().isEmpty()
                ? selectedInstance.versionId : selectedInstance.name);
        instanceButton.setText(name + " ▼");
        instanceTarget.setText(selectedInstance == null
                ? "Select an instance first"
                : name + "\n" + minecraftVersionFromInstance(selectedInstance.versionId));
    }

    private void updateFilterVisibility() {
        loaderSpinner.setVisibility(category.usesLoader() ? View.VISIBLE : View.GONE);
    }

    private void updateGridColumns() {
        if (projectList == null) return;
        int widthDp = (int) (projectList.getWidth()
                / getResources().getDisplayMetrics().density);
        int columns;
        int smallest = getResources().getConfiguration().smallestScreenWidthDp;
        if (smallest < 600) {
            columns = 1;
        } else {
            columns = Math.max(1, Math.min(4, widthDp / 310));
        }
        RecyclerView.LayoutManager current = projectList.getLayoutManager();
        if (!(current instanceof GridLayoutManager)
                || ((GridLayoutManager) current).getSpanCount() != columns) {
            projectList.setLayoutManager(new GridLayoutManager(this, columns));
        }
    }

    private String selectedMinecraftVersion() {
        Object value = versionSpinner == null ? null : versionSpinner.getSelectedItem();
        if (value != null) return value.toString();
        return selectedInstance == null ? "1.21.11" : minecraftVersionFromInstance(selectedInstance.versionId);
    }

    private String selectedLoader() {
        if (!category.usesLoader()) return null;
        Object value = loaderSpinner == null ? null : loaderSpinner.getSelectedItem();
        if (value != null && !"Auto".equalsIgnoreCase(value.toString())) {
            return value.toString().toLowerCase(Locale.ROOT);
        }
        String detected = detectLoader(selectedInstance);
        return detected == null ? "fabric" : detected;
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
        if (versionId == null || versionId.trim().isEmpty()) return "1.21.11";
        String id = versionId.trim();
        if (id.startsWith("fabric-loader-")) {
            int dash = id.lastIndexOf('-');
            if (dash >= 0 && dash + 1 < id.length()) return id.substring(dash + 1);
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^(\\d+\\.\\d+(?:\\.\\d+)?)").matcher(id);
        return matcher.find() ? matcher.group(1) : id;
    }

    private int placeholder() {
        if (category == OrynDownloadState.Category.RESOURCEPACK) return R.drawable.oryn_download_resource;
        if (category == OrynDownloadState.Category.SHADER) return R.drawable.oryn_download_shader;
        return R.drawable.oryn_download_mod;
    }

    private String buildProjectInfo(ModrinthProject project) {
        StringBuilder out = new StringBuilder();
        out.append(formatDownloads(project.downloads)).append(" downloads");
        if (project.followers > 0) out.append(" • ").append(project.followers).append(" followers");
        out.append("\nType: ").append(project.projectType);
        if (!project.categories.isEmpty()) out.append("\nCategories: ").append(join(project.categories, 7));
        if (!project.gameVersions.isEmpty()) out.append("\nMinecraft: ").append(join(project.gameVersions, 8));
        if (!project.loaders.isEmpty()) out.append("\nLoaders: ").append(join(project.loaders, 7));
        return out.toString();
    }

    private String join(List<String> values, int max) {
        StringBuilder out = new StringBuilder();
        int count = Math.min(max, values == null ? 0 : values.size());
        for (int i = 0; i < count; i++) {
            if (i > 0) out.append(", ");
            out.append(values.get(i));
        }
        if (values != null && values.size() > count) out.append("…");
        return out.toString();
    }

    private String formatDownloads(long value) {
        if (value >= 1000000000L) return String.format(Locale.ROOT, "%.1fB", value / 1000000000.0);
        if (value >= 1000000L) return String.format(Locale.ROOT, "%.1fM", value / 1000000.0);
        if (value >= 1000L) return String.format(Locale.ROOT, "%.1fK", value / 1000.0);
        return String.valueOf(value);
    }

    private String safe(String value) {
        return value == null || value.isEmpty() ? "Unknown author" : value;
    }

    @Override
    protected void onDestroy() {
        if (viewModel != null) viewModel.shutdown();
        if (filterRepository != null) filterRepository.shutdown();
        if (instanceExecutor != null) instanceExecutor.shutdownNow();
        super.onDestroy();
    }

    private static final class LooperHolder {
        static boolean isMainThread() {
            return android.os.Looper.myLooper() == android.os.Looper.getMainLooper();
        }
    }
}
