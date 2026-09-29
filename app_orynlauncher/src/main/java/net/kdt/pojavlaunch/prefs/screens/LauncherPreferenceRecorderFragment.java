package net.kdt.pojavlaunch.prefs.screens;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.media.MediaMetadataRetriever;
import android.media.MediaScannerConnection;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import java.util.Locale;

public class LauncherPreferenceRecorderFragment extends LauncherPreferenceFragment {
    private PreferenceCategory recordingsCategory;

    @Override
    public void onCreatePreferences(Bundle b, String rootKey) {
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(requireContext());
        setPreferenceScreen(screen);

        PreferenceCategory qualityCategory = new PreferenceCategory(requireContext());
        qualityCategory.setTitle("OrynLauncher Recorder");
        screen.addPreference(qualityCategory);

        ListPreference quality = new ListPreference(requireContext());
        quality.setKey("recorder_quality");
        quality.setTitle("Video Quality");
        quality.setEntries(new CharSequence[]{"50% — Performance", "75% — Balanced", "100% — Full"});
        quality.setEntryValues(new CharSequence[]{"50", "75", "100"});
        quality.setDefaultValue("100");
        quality.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        qualityCategory.addPreference(quality);

        ListPreference fps = new ListPreference(requireContext());
        fps.setKey("recorder_fps");
        fps.setTitle("Frame Rate");
        fps.setEntries(new CharSequence[]{"24 FPS", "30 FPS", "60 FPS"});
        fps.setEntryValues(new CharSequence[]{"24", "30", "60"});
        fps.setDefaultValue("30");
        fps.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        qualityCategory.addPreference(fps);

        Preference microphone = new Preference(requireContext());
        microphone.setTitle("Microphone");
        microphone.setSummary("Microphone is controlled from the in-game recorder.");
        qualityCategory.addPreference(microphone);

        recordingsCategory = new PreferenceCategory(requireContext());
        recordingsCategory.setTitle("OrynLauncher Recordings");
        screen.addPreference(recordingsCategory);

        Preference refresh = new Preference(requireContext());
        refresh.setTitle("Refresh Recordings");
        refresh.setSummary("Scan Movies/OrynLauncher Recordings");
        refresh.setOnPreferenceClickListener(p -> {
            loadRecordings();
            return true;
        });
        screen.addPreference(refresh);

        Preference location = new Preference(requireContext());
        location.setTitle("Recording Location");
        location.setSummary("Movies/OrynLauncher Recordings — OrynLauncher videos only");
        screen.addPreference(location);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadRecordings();
    }

    private void loadRecordings() {
        if (recordingsCategory == null || !isAdded()) return;

        while (recordingsCategory.getPreferenceCount() > 0) {
            recordingsCategory.removePreference(recordingsCategory.getPreference(0));
        }

        final android.content.ContentResolver resolver = requireContext().getContentResolver();
        // Legacy recordings can exist as real files before MediaStore has indexed them.
        // Ask Android's media scanner to index the public recordings directory before querying.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            scanLegacyRecordingFiles();
        }

        final java.util.LinkedHashMap<String, Uri> recordings = new java.util.LinkedHashMap<>();

        // Scan every external MediaStore volume. Do not require RELATIVE_PATH in SQL:
        // some Android providers normalize or omit that field even when insertion succeeded.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                java.util.Set<String> volumes = MediaStore.getExternalVolumeNames(requireContext());
                for (String volume : volumes) {
                    Uri collection = MediaStore.Video.Media.getContentUri(volume);
                    String[] projection = {
                        MediaStore.Video.Media._ID,
                        MediaStore.Video.Media.DISPLAY_NAME,
                        MediaStore.Video.Media.DURATION,
                        MediaStore.Video.Media.RELATIVE_PATH
                    };
                    String selection = MediaStore.Video.Media.DISPLAY_NAME + " LIKE ?";
                    String[] args = {"OrynLauncher_Recording_%"};

                    try (Cursor cursor = resolver.query(
                            collection, projection, selection, args,
                            MediaStore.Video.Media.DATE_ADDED + " DESC")) {
                        if (cursor == null) continue;

                        int idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID);
                        int nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME);
                        int durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION);
                        int pathCol = cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH);
                        if (idCol < 0 || nameCol < 0) continue;

                        while (cursor.moveToNext()) {
                            String name = cursor.getString(nameCol);
                            if (name == null
                                    || !name.startsWith("OrynLauncher_Recording_")
                                    || !name.endsWith(".mp4")) {
                                continue;
                            }

                            String relativePath = pathCol >= 0 ? cursor.getString(pathCol) : null;
                            if (relativePath != null
                                    && !relativePath.equals("Movies/OrynLauncher Recordings/")
                                    && !relativePath.equals("Movies/OrynLauncher Recordings")) {
                                continue;
                            }

                            long id = cursor.getLong(idCol);
                            Uri uri = Uri.withAppendedPath(collection, String.valueOf(id));
                            if (recordings.put(uri.toString(), uri) == null) {
                                long duration = durationCol >= 0 ? cursor.getLong(durationCol) : 0L;
                                addRecordingPreference(uri, name, duration);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                android.util.Log.w("OrynRecorder",
                        "MediaStore scan failed: " + e.getMessage(), e);
            }
        }

        // Always try the exact MediaStore URI recorded by GameRecorder first.
        // This is the most reliable path on Android 10+ because the provider may not
        // expose RELATIVE_PATH consistently across OEM MediaStore implementations.
        android.content.SharedPreferences prefs = requireContext()
                .getSharedPreferences("oryn_recorder_library", android.content.Context.MODE_PRIVATE);
        java.util.Set<String> entries = prefs.getStringSet(
                "recordings", java.util.Collections.emptySet());

        for (String entry : entries) {
            String[] parts = entry.split("\\|", 2);
            if (parts.length != 2) continue;

            try {
                Uri uri = Uri.parse(parts[0]);
                String name = parts[1];
                if (!name.startsWith("OrynLauncher_Recording_")
                        || !name.endsWith(".mp4")
                        || recordings.containsKey(uri.toString())) {
                    continue;
                }

                // The recorder stores only successfully published MediaStore URIs.
                // Trust this persisted library entry instead of probing it: some OEM
                // providers temporarily reject query()/AFD calls even though the URI
                // is valid and playable.
                recordings.put(uri.toString(), uri);
                addRecordingPreference(uri, name, 0L);
            } catch (Exception ignored) {
                // Try MediaStore discovery below.
            }
        }

        // Provider fallback: some devices expose the video only through the synthetic
        // external volume even though individual-volume queries return no rows.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && recordings.isEmpty()) {
            try {
                Uri collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
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
                    if (cursor != null) {
                        int idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID);
                        int nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME);
                        int durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION);
                        if (idCol >= 0 && nameCol >= 0) {
                            while (cursor.moveToNext()) {
                                String name = cursor.getString(nameCol);
                                if (name == null || !name.startsWith("OrynLauncher_Recording_")
                                        || !name.endsWith(".mp4")) continue;
                                long id = cursor.getLong(idCol);
                                Uri uri = Uri.withAppendedPath(collection, String.valueOf(id));
                                if (!recordings.containsKey(uri.toString())) {
                                    long duration = durationCol >= 0 ? cursor.getLong(durationCol) : 0L;
                                    recordings.put(uri.toString(), uri);
                                    addRecordingPreference(uri, name, duration);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                android.util.Log.w("OrynRecorder", "Synthetic MediaStore scan failed: " + e.getMessage());
            }
        }

        if (recordings.isEmpty()) {
            addEmptyMessage("No OrynLauncher recordings yet. Record a video and refresh this page.");
        }
    }

    private void scanLegacyRecordingFiles() {
        java.io.File movies = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_MOVIES);
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
        MediaScannerConnection.scanFile(requireContext(), paths, mimeTypes, null);
    }

    private void addRecordingPreference(Uri uri, String name, long duration) {
        Preference video = new Preference(requireContext());
        video.setTitle(name);
        long durationMs = duration;
        if (durationMs <= 0) durationMs = requireVideoDuration(uri, 0L);
        video.setSummary(formatDuration(durationMs) + " • Tap for options");
        final Uri finalUri = uri;
        final String finalName = name;
        video.setOnPreferenceClickListener(p -> {
            new AlertDialog.Builder(requireContext())
                .setTitle(finalName)
                .setItems(new String[]{"▶ Play", "🗑 Delete"}, (dialog, which) -> {
                    if (which == 0) {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(finalUri, "video/mp4");
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        try { startActivity(intent); }
                        catch (Exception e) {
                            Toast.makeText(requireContext(), "Can't open this video.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        new AlertDialog.Builder(requireContext())
                            .setTitle("Delete recording?")
                            .setMessage("This will permanently delete the OrynLauncher video.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete", (d, w) -> {
                                try {
                                    requireContext().getContentResolver().delete(finalUri, null, null);
                                    loadRecordings();
                                } catch (Exception e) {
                                    Toast.makeText(requireContext(), "Could not delete recording.", Toast.LENGTH_SHORT).show();
                                }
                            }).show();
                    }
                }).show();
            return true;
        });
        recordingsCategory.addPreference(video);
    }

    private long requireVideoDuration(Uri uri, long fallbackMs) {
        if (fallbackMs > 0) return fallbackMs;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(requireContext(), uri);
            String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long duration = value == null ? 0L : Long.parseLong(value);
            return duration > 0 ? duration : fallbackMs;
        } catch (Exception ignored) {
            return fallbackMs;
        } finally {
            try { retriever.release(); } catch (Exception ignored) {}
        }
    }

    private void addEmptyMessage(String message) {
        Preference empty = new Preference(requireContext());
        empty.setTitle("No recordings");
        empty.setSummary(message);
        recordingsCategory.addPreference(empty);
    }

    private static String formatDuration(long ms) {
        long seconds = Math.max(0L, ms / 1000L);
        return String.format(Locale.getDefault(), "%02d:%02d", seconds / 60L, seconds % 60L);
    }
}
