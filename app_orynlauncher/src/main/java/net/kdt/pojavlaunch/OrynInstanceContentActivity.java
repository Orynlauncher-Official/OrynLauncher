package net.kdt.pojavlaunch;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.FileObserver;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.download.OrynInstanceContentScanner;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;

public class OrynInstanceContentActivity extends AppCompatActivity
        implements OrynInstanceContentScanner.Listener {

    private Instance instance;
    private RecyclerView list;
    private ContentAdapter adapter;
    private TextView title;
    private TextView subtitle;
    private TextView counts;
    private LinearLayout tabs;
    private OrynInstanceContentScanner.ContentType selected = OrynInstanceContentScanner.ContentType.MODS;
    private final List<FileObserver> observers = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String id = getIntent().getStringExtra("instance_id");
        instance = findInstance(id);
        if (instance == null) {
            Toast.makeText(this, "Instance is no longer available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        buildUi();
        String requested = getIntent().getStringExtra("content_type");
        if ("mods".equals(requested)) showType(OrynInstanceContentScanner.ContentType.MODS);
        else if ("shaders".equals(requested)) showType(OrynInstanceContentScanner.ContentType.SHADERS);
        else if ("resourcepacks".equals(requested)) showType(OrynInstanceContentScanner.ContentType.RESOURCE_PACKS);
        else if ("saves".equals(requested)) showType(OrynInstanceContentScanner.ContentType.SAVES);
        OrynInstanceContentScanner.observe(instance.id, this);
        OrynInstanceContentScanner.refreshAsync(instance);
        startWatchers();
    }

    private Instance findInstance(String id) {
        if (id == null) return null;
        try {
            for (Instance value : Instances.loadAllInstances()) {
                if (id.equals(value.id)) return value;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView label(String value, int size) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(Color.WHITE);
        v.setTextSize(size);
        return v;
    }

    private Button tab(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(12);
        b.setBackgroundColor(0xFF20242C);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        return b;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(10));
        root.setBackgroundColor(0xFF0A0B0E);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        Button back = tab("‹ Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(72), dp(40)));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(10), 0, dp(8), 0);
        title = label(instance.name == null ? "Instance" : instance.name, 20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        subtitle = label((instance.minecraftVersion == null ? instance.versionId : instance.minecraftVersion)
                + " • " + (instance.loaderType == null ? "Vanilla" : instance.loaderType), 11);
        subtitle.setTextColor(0xFF9EA3AD);
        heading.addView(title);
        heading.addView(subtitle);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));

        Button play = tab("PLAY");
        play.setOnClickListener(v -> {
            Instances.setSelectedInstance(instance);
            ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
        });
        header.addView(play, new LinearLayout.LayoutParams(dp(70), dp(40)));
        root.addView(header);

        counts = label("", 11);
        counts.setTextColor(0xFFB9BDC6);
        counts.setPadding(dp(4), dp(8), dp(4), dp(6));
        root.addView(counts);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        addTab("Overview", null);
        addTab("Mods", OrynInstanceContentScanner.ContentType.MODS);
        addTab("Resource Packs", OrynInstanceContentScanner.ContentType.RESOURCE_PACKS);
        addTab("Shaders", OrynInstanceContentScanner.ContentType.SHADERS);
        addTab("Saves", OrynInstanceContentScanner.ContentType.SAVES);
        scroll.addView(tabs);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, dp(48)));

        list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ContentAdapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        showOverview();
    }

    private void addTab(String text, OrynInstanceContentScanner.ContentType type) {
        Button b = tab(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(40));
        lp.rightMargin = dp(6);
        b.setOnClickListener(v -> {
            if (type == null) showOverview();
            else showType(type);
        });
        tabs.addView(b, lp);
    }

    private void showOverview() {
        selected = null;
        adapter.setOverview(true, null);
        OrynInstanceContentScanner.State state = OrynInstanceContentScanner.getCached(instance.id);
        if (state != null) updateCounts(state);
    }

    private void showType(OrynInstanceContentScanner.ContentType type) {
        selected = type;
        adapter.setOverview(false, type);
        OrynInstanceContentScanner.State state = OrynInstanceContentScanner.getCached(instance.id);
        if (state != null) adapter.setItems(state.get(type));
    }

    private void updateCounts(OrynInstanceContentScanner.State state) {
        counts.setText("Mods " + state.count(OrynInstanceContentScanner.ContentType.MODS)
                + "   •   Packs " + state.count(OrynInstanceContentScanner.ContentType.RESOURCE_PACKS)
                + "   •   Shaders " + state.count(OrynInstanceContentScanner.ContentType.SHADERS)
                + "   •   Saves " + state.count(OrynInstanceContentScanner.ContentType.SAVES));
    }

    private void startWatchers() {
        String[] dirs = {"mods", "resourcepacks", "shaderpacks"};
        for (String name : dirs) {
            File dir = new File(instance.getGameDirectory(), name);
            if (!dir.isDirectory()) dir.mkdirs();
            FileObserver observer = new FileObserver(dir.getAbsolutePath(),
                    FileObserver.CREATE | FileObserver.DELETE | FileObserver.MOVED_TO
                            | FileObserver.MOVED_FROM | FileObserver.CLOSE_WRITE) {
                @Override public void onEvent(int event, String path) {
                    OrynInstanceContentScanner.refreshAsync(instance);
                }
            };
            observer.startWatching();
            observers.add(observer);
        }
    }

    @Override public void onContentStateChanged(OrynInstanceContentScanner.State state) {
        if (!isFinishing()) {
            updateCounts(state);
            if (selected != null) adapter.setItems(state.get(selected));
        }
    }

    @Override protected void onDestroy() {
        OrynInstanceContentScanner.removeObserver(instance == null ? null : instance.id, this);
        for (FileObserver observer : observers) observer.stopWatching();
        observers.clear();
        super.onDestroy();
    }

    private final class ContentAdapter extends RecyclerView.Adapter<ContentAdapter.Holder> {
        private final List<OrynInstanceContentScanner.ContentItem> items = new ArrayList<>();
        private boolean overview;

        void setOverview(boolean value, OrynInstanceContentScanner.ContentType type) {
            overview = value;
            if (overview) {
                items.clear();
                notifyDataSetChanged();
            }
        }

        void setItems(List<OrynInstanceContentScanner.ContentItem> value) {
            overview = false;
            items.clear();
            if (value != null) items.addAll(value);
            notifyDataSetChanged();
        }

        @Override public Holder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            TextView v = label("", 14);
            v.setPadding(dp(14), dp(14), dp(14), dp(14));
            v.setBackgroundColor(0xFF15181E);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, dp(62));
            lp.bottomMargin = dp(6);
            v.setLayoutParams(lp);
            return new Holder(v);
        }

        @Override public void onBindViewHolder(Holder holder, int position) {
            if (overview) {
                holder.text.setText("Select Mods, Resource Packs, Shaders or Saves above.");
            } else {
                OrynInstanceContentScanner.ContentItem item = items.get(position);
                holder.text.setText(item.name + "\n" + (item.enabled ? "Enabled" : "Disabled")
                        + " • " + formatBytes(item.size));
            }
        }

        @Override public int getItemCount() {
            return overview ? 1 : items.size();
        }

        private String formatBytes(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
            return (bytes / (1024 * 1024)) + " MB";
        }

        final class Holder extends RecyclerView.ViewHolder {
            final TextView text;
            Holder(View itemView) {
                super(itemView);
                text = (TextView) itemView;
            }
        }
    }
}
