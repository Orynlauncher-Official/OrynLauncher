package net.kdt.pojavlaunch;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.download.ModrinthApiService;
import net.kdt.pojavlaunch.download.ModrinthProject;
import net.kdt.pojavlaunch.download.ModrinthVersion;
import net.kdt.pojavlaunch.download.OrynContentRepository;
import net.kdt.pojavlaunch.download.OrynDownloadState;
import net.kdt.pojavlaunch.download.OrynDownloadViewModel;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModrinthApi;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;

public class OrynDownloadActivity extends AppCompatActivity implements OrynDownloadViewModel.Observer {

    private static final int PICK_MODPACK = 4107;

    private LinearLayout root;
    private EditText search;
    private Spinner categorySpinner;
    private Spinner gameVersionSpinner;
    private Spinner loaderSpinner;
    private TextView status;
    private RecyclerView list;
    private ProjectAdapter adapter;
    private Button importButton;
    private ProgressBar loading;

    private OrynDownloadViewModel viewModel;
    private OrynContentRepository repository;
    private final Handler handler = new Handler();
    private Runnable searchRunnable;
    private boolean controlsReady;
    private boolean installingAfterTargetChoice;
    private String pendingProjectId;
    private String pendingVersionId;

    private final OrynDownloadState.Category[] categories = OrynDownloadState.Category.values();
    private List<String> gameVersions = new ArrayList<>();
    private List<String> loaders = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        Instance selected = null;
        try { selected = Instances.loadSelectedInstance(); } catch (Throwable ignored) {}

        repository = new OrynContentRepository();
        viewModel = new OrynDownloadViewModel(selected);

        // Build and attach every view before observing the ViewModel.
        // observe() may immediately emit the current state, so onStateChanged()
        // must never run while loading/status/adapter are still null.
        buildUi();
        viewModel.observe(this);
        loadFilterValues();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, float size, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private android.graphics.drawable.GradientDrawable bg(int color, int radius) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private Button pill(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(Color.WHITE);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setBackground(bg(0xFF17151F, 10));
        return b;
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF09080D);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(10), dp(14), dp(6));

        Button back = pill("‹");
        back.setTextSize(24);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(44)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(dp(10), 0, 0, 0);
        TextView title = text("Oryn Downloads", 20, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView subtitle = text("Discover real Modrinth content", 10, 0xFF92939D);
        titleBox.addView(title, new LinearLayout.LayoutParams(-1, dp(25)));
        titleBox.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(20)));
        header.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1));

        Button refresh = pill("↻");
        refresh.setTextSize(19);
        refresh.setOnClickListener(v -> runSearch());
        header.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(44)));
        root.addView(header);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(dp(14), dp(4), dp(14), dp(4));

        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search for content");
        search.setHintTextColor(0xFF92929D);
        search.setTextColor(Color.WHITE);
        search.setTextSize(14);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setPadding(dp(14), 0, dp(14), 0);
        search.setBackground(bg(0xFF12111A, 8));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) {
                if (searchRunnable != null) handler.removeCallbacks(searchRunnable);
                searchRunnable = () -> runSearch();
                handler.postDelayed(searchRunnable, 420);
            }
            @Override public void afterTextChanged(Editable e) {}
        });
        searchRow.addView(search, new LinearLayout.LayoutParams(0, dp(54), 1));

        categorySpinner = spinner();
        searchRow.addView(categorySpinner, new LinearLayout.LayoutParams(dp(150), dp(54)));
        root.addView(searchRow);

        LinearLayout filters = new LinearLayout(this);
        filters.setPadding(dp(14), dp(2), dp(14), dp(7));
        filters.setGravity(Gravity.CENTER_VERTICAL);

        gameVersionSpinner = spinner();
        loaderSpinner = spinner();
        filters.addView(label("Minecraft", 10), new LinearLayout.LayoutParams(dp(76), dp(42)));
        filters.addView(gameVersionSpinner, new LinearLayout.LayoutParams(0, dp(42), 1));
        filters.addView(label("Loader", 10), new LinearLayout.LayoutParams(dp(58), dp(42)));
        filters.addView(loaderSpinner, new LinearLayout.LayoutParams(dp(125), dp(42)));
        root.addView(filters);

        status = text("Loading Modrinth content…", 10, 0xFF858691);
        status.setPadding(dp(16), 0, dp(16), dp(5));
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(28)));

        FrameLayoutCompat contentFrame = new FrameLayoutCompat(this);
        list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setClipToPadding(false);
        list.setPadding(dp(14), dp(4), dp(14), dp(8));
        adapter = new ProjectAdapter();
        list.setAdapter(adapter);
        contentFrame.addView(list, new android.widget.FrameLayout.LayoutParams(-1, -1));

        loading = new ProgressBar(this);
        loading.setVisibility(View.GONE);
        android.widget.FrameLayout.LayoutParams loadingLp =
                new android.widget.FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER);
        contentFrame.addView(loading, loadingLp);
        root.addView(contentFrame, new LinearLayout.LayoutParams(-1, 0, 1));

        importButton = pill("IMPORT LOCAL MODPACK");
        importButton.setTextSize(12);
        importButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        importButton.setBackground(bg(0xFF24202F, 18));
        importButton.setOnClickListener(v -> pickLocalModpack());
        LinearLayout.LayoutParams importLp = new LinearLayout.LayoutParams(-1, dp(52));
        importLp.setMargins(dp(14), dp(2), dp(14), dp(12));
        root.addView(importButton, importLp);

        setContentView(root);

        categorySpinner.setAdapter(new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item, categoryNames()));
        categorySpinner.setSelection(0);
        categorySpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                if (!controlsReady) return;
                runSearch();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });

        android.widget.AdapterView.OnItemSelectedListener filterListener =
                new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                        if (!controlsReady) return;
                        runSearch();
                    }
                    @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
                };
        gameVersionSpinner.setOnItemSelectedListener(filterListener);
        loaderSpinner.setOnItemSelectedListener(filterListener);

        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm != null && lm.findLastVisibleItemPosition() >= adapter.getItemCount() - 3) {
                    viewModel.loadMore();
                }
            }
        });
    }

    private Spinner spinner() {
        Spinner s = new Spinner(this);
        s.setBackground(bg(0xFF14131D, 8));
        s.setPadding(dp(5), 0, dp(5), 0);
        return s;
    }

    private TextView label(String value, int size) {
        TextView v = text(value, size, 0xFF92929D);
        v.setGravity(Gravity.CENTER);
        return v;
    }

    private String[] categoryNames() {
        String[] result = new String[categories.length];
        for (int i = 0; i < categories.length; i++) result[i] = categories[i].title;
        return result;
    }

    private void loadFilterValues() {
        repository.loadGameVersions(new OrynContentRepository.Listener<List<String>>() {
            @Override public void onSuccess(List<String> value) {
                gameVersions = value == null ? new ArrayList<String>() : value;
                runOnUiThread(() -> {
                    List<String> values = new ArrayList<>();
                    values.add("Select version");
                    values.addAll(gameVersions);
                    gameVersionSpinner.setAdapter(new ArrayAdapter<String>(OrynDownloadActivity.this,
                            android.R.layout.simple_spinner_dropdown_item, values));
                    String current = null;
                    try {
                        Instance i = Instances.loadSelectedInstance();
                        if (i != null) current = i.minecraftVersion;
                    } catch (Throwable ignored) {}
                    int pos = current == null ? 0 : values.indexOf(current);
                    if (pos < 0) pos = 0;
                    gameVersionSpinner.setSelection(pos);
                });
            }
            @Override public void onError(Exception error) {
                runOnUiThread(() -> Toast.makeText(OrynDownloadActivity.this,
                        "Could not load Minecraft versions", Toast.LENGTH_SHORT).show());
            }
        });

        repository.loadLoaders(new OrynContentRepository.Listener<List<String>>() {
            @Override public void onSuccess(List<String> value) {
                loaders = value == null ? new ArrayList<String>() : value;
                runOnUiThread(() -> {
                    List<String> values = new ArrayList<>();
                    values.add("Any loader");
                    values.addAll(loaders);
                    loaderSpinner.setAdapter(new ArrayAdapter<String>(OrynDownloadActivity.this,
                            android.R.layout.simple_spinner_dropdown_item, values));
                    loaderSpinner.setSelection(0);
                    controlsReady = true;
                    runSearch();
                });
            }
            @Override public void onError(Exception error) {
                runOnUiThread(() -> {
                    loaderSpinner.setAdapter(new ArrayAdapter<String>(OrynDownloadActivity.this,
                            android.R.layout.simple_spinner_dropdown_item,
                            new String[]{"Any loader", "fabric", "forge", "neoforge", "quilt"}));
                    controlsReady = true;
                    runSearch();
                });
            }
        });
    }

    private String selectedVersion() {
        if (gameVersionSpinner == null || gameVersionSpinner.getSelectedItem() == null) return "";
        String value = String.valueOf(gameVersionSpinner.getSelectedItem());
        return "Select version".equals(value) ? "" : value;
    }

    private String selectedLoader() {
        if (loaderSpinner == null || loaderSpinner.getSelectedItem() == null) return "";
        String value = String.valueOf(loaderSpinner.getSelectedItem());
        return "Any loader".equals(value) ? "" : value.toLowerCase(Locale.ROOT);
    }

    private OrynDownloadState.Category selectedCategory() {
        int p = categorySpinner == null ? 0 : categorySpinner.getSelectedItemPosition();
        if (p < 0 || p >= categories.length) p = 0;
        return categories[p];
    }

    private void runSearch() {
        if (!controlsReady || viewModel == null) return;
        String version = selectedVersion();
        if (version.isEmpty()) {
            try {
                Instance i = Instances.loadSelectedInstance();
                if (i != null && i.minecraftVersion != null) version = i.minecraftVersion;
            } catch (Throwable ignored) {}
        }
        viewModel.setFilters(selectedCategory(), version, selectedLoader(),
                search == null ? "" : search.getText().toString().trim());
    }

    @Override public void onStateChanged(OrynDownloadState state) {
        if (isFinishing()) return;
        runOnUiThread(() -> {
            boolean busy = state.listStatus == OrynDownloadState.ListStatus.LOADING;
            loading.setVisibility(busy ? View.VISIBLE : View.GONE);
            status.setText(state.listMessage == null ? "" : state.listMessage);
            adapter.setState(state);

            if (state.installState != null && state.installState.message != null) {
                if (state.installState.progress >= 0 && state.installState.progress < 100) {
                    status.setText(state.installState.message + " " + state.installState.progress + "%");
                } else if (state.installState.message.length() > 0) {
                    status.setText(state.installState.message);
                }
            }

            if (installingAfterTargetChoice && state.detailStatus == OrynDownloadState.DetailStatus.READY
                    && state.selectedProject != null && pendingProjectId != null
                    && pendingProjectId.equals(state.selectedProject.id)) {
                installingAfterTargetChoice = false;
                ModrinthVersion wanted = null;
                for (ModrinthVersion v : state.compatibleVersions) {
                    if (pendingVersionId != null && pendingVersionId.equals(v.id)) {
                        wanted = v;
                        break;
                    }
                }
                if (wanted == null) wanted = state.selectedVersion;
                pendingProjectId = null;
                pendingVersionId = null;
                if (wanted != null) {
                    viewModel.selectVersion(wanted);
                    handler.postDelayed(() -> viewModel.installSelected(), 80);
                }
            }
        });
    }

    private void showTargetDialog(ModrinthProject project, ModrinthVersion version) {
        final List<Instance> instances;
        try {
            instances = Instances.loadAllInstances();
        } catch (Exception e) {
            Toast.makeText(this, "Could not load instances", Toast.LENGTH_LONG).show();
            return;
        }
        if (instances.isEmpty()) {
            Toast.makeText(this, "Create an instance before installing this content", Toast.LENGTH_LONG).show();
            return;
        }

        String[] names = new String[instances.size()];
        for (int index = 0; index < instances.size(); index++) {
            Instance instance = instances.get(index);
            names[index] = (instance.name == null ? "Instance" : instance.name) + "  •  "
                    + (instance.minecraftVersion == null ? instance.versionId : instance.minecraftVersion)
                    + "  •  " + (instance.loaderType == null ? "Vanilla" : instance.loaderType);
        }

        new AlertDialog.Builder(this)
                .setTitle("Where should this be installed?")
                .setSingleChoiceItems(names, findCurrentInstance(instances),
                        (dialog, which) -> {
                            Instance target = instances.get(which);
                            dialog.dismiss();
                            installForTarget(target, project, version);
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int findCurrentInstance(List<Instance> instances) {
        Instance current = null;
        try { current = Instances.loadSelectedInstance(); } catch (Throwable ignored) {}
        if (current == null || current.id == null) return 0;
        for (int i = 0; i < instances.size(); i++) {
            if (current.id.equals(instances.get(i).id)) return i;
        }
        return 0;
    }

    private void installForTarget(Instance target, ModrinthProject project, ModrinthVersion version) {
        String versionName = target.minecraftVersion == null ? target.versionId : target.minecraftVersion;
        String loader = target.loaderType == null ? "" : target.loaderType.toLowerCase(Locale.ROOT);

        if (!versionName.equals(viewModel.getState().minecraftVersion)
                || !loader.equals(viewModel.getState().loader)) {
            viewModel.setVersionContext(versionName, loader);
        }
        pendingProjectId = project.id;
        pendingVersionId = version.id;
        installingAfterTargetChoice = true;
        viewModel.setInstance(target);
        viewModel.setFilters(selectedCategory(), versionName, loader,
                search.getText().toString().trim());
        viewModel.selectProject(project);
    }

    private void pickLocalModpack() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/*");
        startActivityForResult(intent, PICK_MODPACK);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_MODPACK || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        importLocalModpack(data.getData());
    }

    private void importLocalModpack(Uri uri) {
        new Thread(() -> {
            File temp = null;
            try {
                File cache = Tools.DIR_CACHE;
                if (!cache.isDirectory() && !cache.mkdirs()) throw new Exception("Could not create cache");
                temp = new File(cache, "oryn-local-" + System.currentTimeMillis() + ".mrpack");
                try (InputStream in = getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(temp)) {
                    if (in == null) throw new Exception("Could not open selected file");
                    byte[] buffer = new byte[32768];
                    int read;
                    while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                }
                File selectedFile = temp;
                runOnUiThread(() -> Toast.makeText(this, "Installing local modpack…", Toast.LENGTH_SHORT).show());
                Instance installed = new ModrinthApi().installMrpackAsNewInstance(selectedFile, null);
                if (installed == null) throw new Exception("Modpack installation returned no instance");
                runOnUiThread(() -> {
                    Instances.setSelectedInstance(installed);
                    ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, null);
                    Toast.makeText(this, "Modpack imported successfully", Toast.LENGTH_LONG).show();
                    runSearch();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Modpack import failed: " + (e.getMessage() == null ? "unknown error" : e.getMessage()),
                        Toast.LENGTH_LONG).show());
            } finally {
                if (temp != null && temp.exists()) temp.delete();
            }
        }).start();
    }

    @Override protected void onDestroy() {
        if (searchRunnable != null) handler.removeCallbacks(searchRunnable);
        if (viewModel != null) viewModel.shutdown();
        if (repository != null) repository.shutdown();
        super.onDestroy();
    }

    private final class ProjectAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<ModrinthProject> items = new ArrayList<>();
        private OrynDownloadState state;
        private String expandedId;

        void setState(OrynDownloadState value) {
            state = value;
            items.clear();
            if (value != null && value.projects != null) items.addAll(value.projects);
            if (value == null || value.selectedProject == null) {
                expandedId = null;
            } else {
                expandedId = value.selectedProject.id;
            }
            notifyDataSetChanged();
        }

        @Override public ProjectHolder onCreateViewHolder(ViewGroup parent, int type) {
            return new ProjectHolder(createCard(parent));
        }

        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ProjectHolder projectHolder = (ProjectHolder) holder;
            projectHolder.bind(items.get(position), items.get(position).id != null
                    && items.get(position).id.equals(expandedId));
        }

        @Override public int getItemCount() { return items.size(); }

        private LinearLayout createCard(ViewGroup parent) {
            LinearLayout card = new LinearLayout(parent.getContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(8), dp(7), dp(8), dp(8));
            card.setBackground(bg(0xFF0E0D14, 4));
            card.setClickable(true);

            LinearLayout summary = new LinearLayout(parent.getContext());
            summary.setGravity(Gravity.CENTER_VERTICAL);
            summary.setPadding(dp(2), dp(1), dp(2), dp(1));

            ImageView icon = new ImageView(parent.getContext());
            icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
            icon.setImageResource(R.drawable.oryn_download_mod);
            summary.addView(icon, new LinearLayout.LayoutParams(dp(76), dp(76)));

            LinearLayout body = new LinearLayout(parent.getContext());
            body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(dp(10), 0, dp(5), 0);
            TextView title = text("", 15, Color.WHITE);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            TextView author = text("", 10, 0xFF9697A1);
            TextView desc = text("", 11, 0xFFE0E0E4);
            desc.setMaxLines(2);
            TextView meta = text("", 9, 0xFF777983);
            body.addView(title, new LinearLayout.LayoutParams(-1, dp(25)));
            body.addView(author, new LinearLayout.LayoutParams(-1, dp(18)));
            body.addView(desc, new LinearLayout.LayoutParams(-1, dp(39)));
            body.addView(meta, new LinearLayout.LayoutParams(-1, dp(18)));
            summary.addView(body, new LinearLayout.LayoutParams(0, -2, 1));
            card.addView(summary);

            LinearLayout expanded = new LinearLayout(parent.getContext());
            expanded.setOrientation(LinearLayout.VERTICAL);
            expanded.setPadding(dp(2), dp(8), dp(2), dp(1));
            Spinner versions = spinner();
            TextView detail = text("Loading compatible versions…", 10, 0xFF9697A1);
            ProgressBar progress = new ProgressBar(parent.getContext());
            progress.setVisibility(View.GONE);
            Button install = pill("INSTALL");
            install.setTextSize(12);
            install.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            install.setBackground(bg(0xFF7F3FE0, 25));
            expanded.addView(versions, new LinearLayout.LayoutParams(-1, dp(45)));
            expanded.addView(detail, new LinearLayout.LayoutParams(-1, dp(28)));
            expanded.addView(progress, new LinearLayout.LayoutParams(-1, dp(4)));
            LinearLayout.LayoutParams installLp = new LinearLayout.LayoutParams(-1, dp(52));
            installLp.topMargin = dp(5);
            expanded.addView(install, installLp);
            card.addView(expanded);

            return card;
        }

        final class ProjectHolder extends RecyclerView.ViewHolder {
            final LinearLayout card;
            final LinearLayout summary;
            final ImageView icon;
            final TextView title;
            final TextView author;
            final TextView desc;
            final TextView meta;
            final LinearLayout expanded;
            final Spinner versions;
            final TextView detail;
            final ProgressBar progress;
            final Button install;

            ProjectHolder(View view) {
                super(view);
                card = (LinearLayout) view;
                summary = (LinearLayout) card.getChildAt(0);
                LinearLayout body = (LinearLayout) summary.getChildAt(1);
                icon = (ImageView) summary.getChildAt(0);
                title = (TextView) body.getChildAt(0);
                author = (TextView) body.getChildAt(1);
                desc = (TextView) body.getChildAt(2);
                meta = (TextView) body.getChildAt(3);
                expanded = (LinearLayout) card.getChildAt(1);
                versions = (Spinner) expanded.getChildAt(0);
                detail = (TextView) expanded.getChildAt(1);
                progress = (ProgressBar) expanded.getChildAt(2);
                install = (Button) expanded.getChildAt(3);

                summary.setOnClickListener(v -> {
                    int p = getBindingAdapterPosition();
                    if (p == RecyclerView.NO_POSITION) return;
                    viewModel.selectProject(items.get(p));
                });
            }

            void bind(ModrinthProject project, boolean isExpanded) {
                title.setText(project.title == null || project.title.isEmpty() ? "Unknown project" : project.title);
                author.setText("by " + safe(project.author));
                desc.setText(project.description == null || project.description.isEmpty()
                        ? "No description available." : project.description);
                meta.setText("Minecraft " + first(project.gameVersions)
                        + "  •  " + compact(project.downloads) + " downloads");

                icon.setTag(project.iconUrl);
                icon.setImageResource(R.drawable.oryn_download_mod);
                if (project.iconUrl != null && !project.iconUrl.isEmpty()) {
                    repository.loadIcon(project.iconUrl, new OrynContentRepository.Listener<Bitmap>() {
                        @Override public void onSuccess(Bitmap bitmap) {
                            if (project.iconUrl.equals(icon.getTag())) icon.setImageBitmap(bitmap);
                        }
                        @Override public void onError(Exception error) {}
                    });
                }

                expanded.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
                if (!isExpanded || state == null || state.selectedProject == null
                        || !project.id.equals(state.selectedProject.id)) return;

                if (state.detailStatus == OrynDownloadState.DetailStatus.LOADING) {
                    detail.setText("Loading compatible versions…");
                    versions.setAdapter(new ArrayAdapter<String>(OrynDownloadActivity.this,
                            android.R.layout.simple_spinner_dropdown_item,
                            new String[]{"Loading…"}));
                    install.setEnabled(false);
                    return;
                }

                List<String> labels = new ArrayList<>();
                if (state.compatibleVersions != null) {
                    for (ModrinthVersion v : state.compatibleVersions) {
                        String n = v.name == null || v.name.isEmpty() ? v.versionNumber : v.name;
                        labels.add(n + (v.featured ? "  ★" : ""));
                    }
                }
                if (labels.isEmpty()) labels.add("No compatible downloadable version");
                versions.setAdapter(new ArrayAdapter<String>(OrynDownloadActivity.this,
                        android.R.layout.simple_spinner_dropdown_item, labels));

                int selected = 0;
                if (state.selectedVersion != null && state.compatibleVersions != null) {
                    for (int i = 0; i < state.compatibleVersions.size(); i++) {
                        if (state.selectedVersion.id.equals(state.compatibleVersions.get(i).id)) {
                            selected = i;
                            break;
                        }
                    }
                }
                versions.setSelection(selected);
                versions.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                        if (state == null || state.compatibleVersions == null || pos >= state.compatibleVersions.size()) return;
                        viewModel.selectVersion(state.compatibleVersions.get(pos));
                    }
                    @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
                });

                detail.setText(state.compatibleVersions == null || state.compatibleVersions.isEmpty()
                        ? "No version matches Minecraft " + state.minecraftVersion
                        : "Minecraft " + state.minecraftVersion + " • " + state.compatibleVersions.size() + " compatible release(s)");

                String installMessage = state.installState == null ? "" : state.installState.message;
                progress.setVisibility(View.GONE);
                if (state.installState != null && state.installState.progress >= 0
                        && state.installState.progress < 100) {
                    progress.setVisibility(View.VISIBLE);
                    progress.setProgress(state.installState.progress);
                }
                if (installMessage != null && !installMessage.isEmpty()) detail.setText(installMessage);

                boolean canInstall = state.selectedVersion != null
                        && state.detailStatus == OrynDownloadState.DetailStatus.READY
                        && (state.installState == null || state.installState.status == net.kdt.pojavlaunch.download.OrynInstallState.Status.IDLE || state.installState.status == net.kdt.pojavlaunch.download.OrynInstallState.Status.FAILED);
                install.setEnabled(canInstall);
                install.setText(state.installState != null && state.installState.status == net.kdt.pojavlaunch.download.OrynInstallState.Status.INSTALLED
                        ? "INSTALLED" : "INSTALL");
                install.setOnClickListener(v -> {
                    if (state.selectedVersion == null || state.selectedProject == null) return;
                    if ("modpack".equals(state.category.projectType)) {
                        viewModel.installSelected();
                    } else {
                        showTargetDialog(state.selectedProject, state.selectedVersion);
                    }
                });
            }

            private String safe(String s) { return s == null || s.isEmpty() ? "Unknown author" : s; }
            private String first(List<String> values) { return values == null || values.isEmpty() ? "—" : values.get(0); }
            private String compact(long value) {
                if (value >= 1000000000L) return String.format(Locale.ROOT, "%.1fB", value / 1000000000.0);
                if (value >= 1000000L) return String.format(Locale.ROOT, "%.1fM", value / 1000000.0);
                if (value >= 1000L) return String.format(Locale.ROOT, "%.1fK", value / 1000.0);
                return String.valueOf(value);
            }
        }
    }

    // Small compatibility wrapper so the activity remains usable with the launcher's
    // existing Android view stack without adding another layout resource.
    private static final class FrameLayoutCompat extends android.widget.FrameLayout {
        FrameLayoutCompat(android.content.Context context) { super(context); }
    }
}
