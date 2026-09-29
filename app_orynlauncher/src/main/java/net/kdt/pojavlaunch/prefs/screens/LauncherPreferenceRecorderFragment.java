package net.kdt.pojavlaunch.prefs.screens;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.media.MediaMetadataRetriever;
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

        android.content.SharedPreferences prefs = requireContext().getSharedPreferences("oryn_recorder_library", android.content.Context.MODE_PRIVATE);
        java.util.Set<String> entries = prefs.getStringSet("recordings", java.util.Collections.emptySet());
        java.util.ArrayList<String> valid = new java.util.ArrayList<>();

        for (String entry : entries) {
            String[] parts = entry.split("\\|", 2);
            if (parts.length != 2) continue;
            Uri uri;
            try { uri = Uri.parse(parts[0]); } catch (Exception e) { continue; }
            String name = parts[1];
            if (!name.startsWith("OrynLauncher_Recording_") || !name.endsWith(".mp4")) continue;

            try (android.content.res.AssetFileDescriptor afd = requireContext().getContentResolver().openAssetFileDescriptor(uri, "r")) {
                if (afd == null) continue;
            } catch (Exception e) { continue; }

            valid.add(entry);
            Preference video = new Preference(requireContext());
            video.setTitle(name);
            long duration = 0L;
            try {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(requireContext(), uri);
                String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                duration = value == null ? 0L : Long.parseLong(value);
                retriever.release();
            } catch (Exception ignored) { }
            final Uri finalUri = uri;
            final String finalName = name;
            video.setSummary(formatDuration(duration) + " • Tap for options");
            video.setOnPreferenceClickListener(p -> {
                new AlertDialog.Builder(requireContext())
                    .setTitle(finalName)
                    .setItems(new String[]{"▶ Play", "🗑 Delete"}, (dialog, which) -> {
                        if (which == 0) {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setDataAndType(finalUri, "video/mp4");
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            try { startActivity(intent); }
                            catch (Exception e) { Toast.makeText(requireContext(), "No video player found for this recording.", Toast.LENGTH_SHORT).show(); }
                        } else {
                            new AlertDialog.Builder(requireContext())
                                .setTitle("Delete recording?")
                                .setMessage("This will permanently delete the OrynLauncher video.")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Delete", (d, w) -> {
                                    try {
                                        requireContext().getContentResolver().delete(finalUri, null, null);
                                        valid.remove(entry);
                                        prefs.edit().putStringSet("recordings", new java.util.HashSet<>(valid)).apply();
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

        if (valid.isEmpty()) addEmptyMessage("No OrynLauncher recordings yet. Record a video and open this page again.");
        else prefs.edit().putStringSet("recordings", new java.util.HashSet<>(valid)).apply();
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
