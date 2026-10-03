package net.kdt.pojavlaunch;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class OrynRecorderActivity extends BaseActivity {
    private static final int BG = Color.rgb(5, 12, 20);
    private static final int PANEL = Color.rgb(10, 19, 31);
    private static final int ROW = Color.rgb(13, 24, 38);
    private static final int BORDER = Color.rgb(28, 45, 65);
    private static final int TEXT = Color.rgb(238, 244, 252);
    private static final int MUTED = Color.rgb(132, 151, 177);
    private static final int ACCENT = Color.rgb(35, 145, 255);
    private static final int GREEN = Color.rgb(55, 196, 166);

    private LinearLayout recordingsList;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        prefs = getSharedPreferences("oryn_recorder_library", MODE_PRIVATE);
        buildUi();
        loadRecordings();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (recordingsList != null) loadRecordings();
    }

    @Override
    public boolean setFullscreen() {
        return true;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable bg(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), BORDER);
        return d;
    }

    private TextView text(String value, float size, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(28), dp(18), dp(28), dp(18));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView icon = text("●", 22, ACCENT);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(bg(Color.rgb(12, 29, 48), 14));
        header.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("Oryn Recorder", 25, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView subtitle = text("In-game recording settings and saved videos", 11, MUTED);
        titles.addView(title, new LinearLayout.LayoutParams(-1, dp(29)));
        titles.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(19)));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, dp(52), 1);
        tp.leftMargin = dp(12);
        header.addView(titles, tp);

        TextView status = text("  READY  ", 10, GREEN);
        status.setGravity(Gravity.CENTER);
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        status.setBackground(bg(Color.rgb(8, 34, 31), 12));
        header.addView(status, new LinearLayout.LayoutParams(dp(76), dp(32)));

        root.addView(header, new LinearLayout.LayoutParams(-1, dp(58)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(14), 0, dp(20));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        content.addView(sectionLabel("RECORDING"), new LinearLayout.LayoutParams(-1, dp(28)));

        LinearLayout settings = card();
        addSpinnerRow(settings, "Video Quality", "Output quality for recordings",
                "recorder_quality",
                new String[]{"50% — Performance", "75% — Balanced", "100% — Full"},
                new String[]{"50", "75", "100"}, "100");
        addSpinnerRow(settings, "Frame Rate", "Target capture frame rate",
                "recorder_fps",
                new String[]{"24 FPS", "30 FPS", "60 FPS"},
                new String[]{"24", "30", "60"}, "30");
        addSwitchRow(settings, "Microphone", "Audio is controlled from the in-game recorder",
                false, null);
        content.addView(settings);

        content.addView(sectionLabel("SAVED RECORDINGS"), new LinearLayout.LayoutParams(-1, dp(28)));
        LinearLayout recordingsCard = card();

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView location = text("Movies / OrynLauncher Recordings", 11, MUTED);
        actions.addView(location, new LinearLayout.LayoutParams(0, dp(42), 1));

        Button refresh = button("REFRESH");
        refresh.setOnClickListener(v -> loadRecordings());
        actions.addView(refresh, new LinearLayout.LayoutParams(dp(96), dp(38)));
        recordingsCard.addView(actions, new LinearLayout.LayoutParams(-1, dp(48)));

        recordingsList = new LinearLayout(this);
        recordingsList.setOrientation(LinearLayout.VERTICAL);
        recordingsCard.addView(recordingsList, new LinearLayout.LayoutParams(-1, -2));

        content.addView(recordingsCard);

        TextView hint = text("Recordings are created from the in-game recorder and appear here automatically.", 10, MUTED);
        hint.setPadding(dp(4), dp(10), dp(4), 0);
        content.addView(hint, new LinearLayout.LayoutParams(-1, dp(34)));

        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private TextView sectionLabel(String value) {
        TextView v = text(value, 10, MUTED);
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setPadding(dp(4), dp(4), 0, 0);
        return v;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(8), dp(10), dp(8));
        card.setBackground(bg(PANEL, 16));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(14);
        return card;
    }

    private void addSpinnerRow(LinearLayout parent, String title, String subtitle,
                               String key, String[] labels, String[] values, String def) {
        LinearLayout row = row();
        LinearLayout labelBox = labels(title, subtitle);
        row.addView(labelBox, new LinearLayout.LayoutParams(0, dp(58), 1));

        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item, labels);
        spinner.setAdapter(adapter);

        String current = prefs.getString(key, def);
        int selected = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                selected = i;
                break;
            }
        }
        spinner.setSelection(selected);
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int position, long id) {
                prefs.edit().putString(key, values[position]).apply();
            }
            public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });

        row.addView(spinner, new LinearLayout.LayoutParams(dp(190), dp(46)));
        parent.addView(row);
    }

    private void addSwitchRow(LinearLayout parent, String title, String subtitle,
                              boolean checked, View.OnClickListener listener) {
        LinearLayout row = row();
        row.addView(labels(title, subtitle), new LinearLayout.LayoutParams(0, dp(58), 1));
        Switch sw = new Switch(this);
        sw.setChecked(checked);
        if (listener != null) sw.setOnClickListener(listener);
        else sw.setEnabled(false);
        row.addView(sw, new LinearLayout.LayoutParams(dp(70), dp(46)));
        parent.addView(row);
    }

    private LinearLayout labels(String title, String subtitle) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(text(title, 13, TEXT), new LinearLayout.LayoutParams(-1, dp(22)));
        box.addView(text(subtitle, 10, MUTED), new LinearLayout.LayoutParams(-1, dp(20)));
        return box;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), 0, dp(8), 0);
        row.setBackground(bg(ROW, 11));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(62));
        p.bottomMargin = dp(5);
        row.setLayoutParams(p);
        return row;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(10);
        b.setTextColor(TEXT);
        b.setAllCaps(false);
        b.setBackground(bg(Color.rgb(21, 57, 91), 10));
        return b;
    }

    private void loadRecordings() {
        if (recordingsList == null) return;
        recordingsList.removeAllViews();

        Map<String, Uri> recordings = new LinkedHashMap<>();
        android.content.ContentResolver resolver = getContentResolver();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) scanLegacyRecordingFiles();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                Set<String> volumes = MediaStore.getExternalVolumeNames(this);
                for (String volume : volumes) {
                    Uri collection = MediaStore.Video.Media.getContentUri(volume);
                    queryRecordings(resolver, collection, recordings);
                }
            } catch (Exception e) {
                android.util.Log.w("OrynRecorder", "MediaStore scan failed", e);
            }
        }

        Set<String> entries = prefs.getStringSet("recordings", Collections.emptySet());
        for (String entry : entries) {
            String[] parts = entry.split("\\|", 2);
            if (parts.length != 2) continue;
            try {
                Uri uri = Uri.parse(parts[0]);
                String name = parts[1];
                if (name.startsWith("OrynLauncher_Recording_") && name.endsWith(".mp4")
                        && !recordings.containsKey(uri.toString())) {
                    recordings.put(uri.toString(), uri);
                    addRecording(uri, name, 0L);
                }
            } catch (Exception ignored) {}
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && recordings.isEmpty()) {
            try {
                queryRecordings(resolver,
                        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                        recordings);
            } catch (Exception ignored) {}
        }

        if (recordings.isEmpty()) {
            TextView empty = text("No recordings yet", 13, TEXT);
            empty.setGravity(Gravity.CENTER);
            TextView sub = text("Start an in-game recording, then return here to manage it.", 10, MUTED);
            sub.setGravity(Gravity.CENTER);
            recordingsList.addView(empty, new LinearLayout.LayoutParams(-1, dp(32)));
            recordingsList.addView(sub, new LinearLayout.LayoutParams(-1, dp(30)));
        }
    }

    private void queryRecordings(android.content.ContentResolver resolver, Uri collection,
                                 Map<String, Uri> recordings) {
        String[] projection = {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.RELATIVE_PATH
        };
        String selection = MediaStore.Video.Media.DISPLAY_NAME + " LIKE ?";
        String[] args = {"OrynLauncher_Recording_%"};

        try (Cursor cursor = resolver.query(collection, projection, selection, args,
                MediaStore.Video.Media.DATE_ADDED + " DESC")) {
            if (cursor == null) return;
            int idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID);
            int nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME);
            int durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION);
            if (idCol < 0 || nameCol < 0) return;

            while (cursor.moveToNext()) {
                String name = cursor.getString(nameCol);
                if (name == null || !name.startsWith("OrynLauncher_Recording_")
                        || !name.endsWith(".mp4")) continue;
                long id = cursor.getLong(idCol);
                Uri uri = Uri.withAppendedPath(collection, String.valueOf(id));
                if (!recordings.containsKey(uri.toString())) {
                    long duration = durationCol >= 0 ? cursor.getLong(durationCol) : 0L;
                    recordings.put(uri.toString(), uri);
                    addRecording(uri, name, duration);
                }
            }
        } catch (Exception e) {
            android.util.Log.w("OrynRecorder", "Query failed", e);
        }
    }

    private void addRecording(Uri uri, String name, long duration) {
        LinearLayout item = row();

        TextView play = text("▶", 16, ACCENT);
        play.setGravity(Gravity.CENTER);
        item.addView(play, new LinearLayout.LayoutParams(dp(42), dp(48)));

        LinearLayout labels = labels(name, formatDuration(duration) + "  •  MP4");
        item.addView(labels, new LinearLayout.LayoutParams(0, dp(58), 1));

        Button more = button("OPTIONS");
        more.setOnClickListener(v -> showRecordingOptions(uri, name));
        item.addView(more, new LinearLayout.LayoutParams(dp(96), dp(40)));

        item.setOnClickListener(v -> playRecording(uri));
        recordingsList.addView(item);
    }

    private void playRecording(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "video/mp4");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Can't open this video.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showRecordingOptions(Uri uri, String name) {
        new AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(new String[]{"▶  Play recording", "Delete recording"}, (dialog, which) -> {
                    if (which == 0) playRecording(uri);
                    else new AlertDialog.Builder(this)
                            .setTitle("Delete recording?")
                            .setMessage("This will permanently delete the OrynLauncher video.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete", (d, w) -> {
                                try {
                                    getContentResolver().delete(uri, null, null);
                                    loadRecordings();
                                } catch (Exception e) {
                                    Toast.makeText(this, "Could not delete recording.", Toast.LENGTH_SHORT).show();
                                }
                            }).show();
                }).show();
    }

    private void scanLegacyRecordingFiles() {
        java.io.File movies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        java.io.File directory = new java.io.File(movies, "OrynLauncher Recordings");
        java.io.File[] files = directory.listFiles((dir, name) ->
                name != null && name.startsWith("OrynLauncher_Recording_") && name.endsWith(".mp4"));
        if (files == null || files.length == 0) return;

        String[] paths = new String[files.length];
        String[] mimeTypes = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            paths[i] = files[i].getAbsolutePath();
            mimeTypes[i] = "video/mp4";
        }
        MediaScannerConnection.scanFile(this, paths, mimeTypes, null);
    }

    private static String formatDuration(long ms) {
        long seconds = Math.max(0L, ms / 1000L);
        return String.format(Locale.getDefault(), "%02d:%02d", seconds / 60L, seconds % 60L);
    }
}
