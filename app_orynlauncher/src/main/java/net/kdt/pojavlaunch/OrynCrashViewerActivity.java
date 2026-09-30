package net.kdt.pojavlaunch;

import android.app.Activity;
import git.artdeell.mojo.R;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Comparator;

public class OrynCrashViewerActivity extends Activity {
    private LinearLayout list;
    private TextView viewer;
    private File crashDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.rgb(12, 12, 14));
        getWindow().setNavigationBarColor(Color.rgb(12, 12, 14));

        Instance instance = Instances.loadSelectedInstance();
        if (instance == null) {
            Toast.makeText(this, R.string.no_instance, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        crashDir = new File(instance.getGameDirectory(), "crash-reports");
        buildUi();
        loadCrashReports();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.setBackgroundColor(Color.rgb(12, 12, 14));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(R.string.oryn_crash_viewer);
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(null, 1);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1));

        Button refresh = new Button(this);
        refresh.setText(R.string.oryn_crash_refresh);
        refresh.setOnClickListener(v -> loadCrashReports());
        header.addView(refresh, new LinearLayout.LayoutParams(dp(100), dp(42)));

        root.addView(header);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView listScroll = new ScrollView(this);
        listScroll.addView(list);
        body.addView(listScroll, new LinearLayout.LayoutParams(dp(230), 0, 1));

        viewer = new TextView(this);
        viewer.setTextColor(Color.rgb(235, 235, 238));
        viewer.setTextSize(12);
        viewer.setPadding(dp(12), dp(8), dp(12), dp(8));
        viewer.setGravity(Gravity.TOP);
        viewer.setTextIsSelectable(true);
        viewer.setBackgroundColor(Color.rgb(20, 20, 23));

        ScrollView viewerScroll = new ScrollView(this);
        viewerScroll.addView(viewer);
        body.addView(viewerScroll, new LinearLayout.LayoutParams(0, 0, 2));

        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void loadCrashReports() {
        list.removeAllViews();
        viewer.setText(R.string.oryn_crash_select);

        if (!crashDir.exists() || !crashDir.isDirectory()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.oryn_crash_none);
            empty.setTextColor(Color.LTGRAY);
            empty.setPadding(dp(12), dp(12), dp(12), dp(12));
            list.addView(empty);
            return;
        }

        File[] files = crashDir.listFiles((dir, name) ->
                name != null && name.toLowerCase().endsWith(".txt"));

        if (files == null || files.length == 0) {
            TextView empty = new TextView(this);
            empty.setText(R.string.oryn_crash_none);
            empty.setTextColor(Color.LTGRAY);
            empty.setPadding(dp(12), dp(12), dp(12), dp(12));
            list.addView(empty);
            return;
        }

        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());

        for (File file : files) {
            Button item = new Button(this);
            item.setText(file.getName());
            item.setTextSize(11);
            item.setAllCaps(false);
            item.setOnClickListener(v -> showCrash(file));
            list.addView(item, new LinearLayout.LayoutParams(-1, dp(48)));
        }
    }

    private void showCrash(File file) {
        try {
            StringBuilder text = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append("\\n");
            }
            reader.close();
            viewer.setText(text.toString());
        } catch (Exception e) {
            viewer.setText(getString(R.string.oryn_crash_read_failed, e.getMessage()));
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
