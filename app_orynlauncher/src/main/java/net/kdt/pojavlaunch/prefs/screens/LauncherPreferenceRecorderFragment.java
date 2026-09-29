package net.kdt.pojavlaunch.prefs.screens;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import git.artdeell.mojo.R;

public class LauncherPreferenceRecorderFragment extends LauncherPreferenceFragment {
    private PreferenceCategory recordingsCategory;

    @Override
    public void onCreatePreferences(Bundle b, String rootKey) {
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(requireContext());
        setPreferenceScreen(screen);

        PreferenceCategory qualityCategory = new PreferenceCategory(requireContext());
        qualityCategory.setTitle("Recorder Quality");
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

        Preference mic = new Preference(requireContext());
        mic.setTitle("Microphone");
        mic.setSummary("Enable microphone while recording from the in-game recorder");
        mic.setOnPreferenceClickListener(p -> {
            Toast.makeText(requireContext(), "Microphone is controlled from the recorder controls.", Toast.LENGTH_SHORT).show();
            return true;
        });
        qualityCategory.addPreference(mic);

        recordingsCategory = new PreferenceCategory(requireContext());
        recordingsCategory.setTitle("Your Recordings");
        screen.addPreference(recordingsCategory);

        Preference refresh = new Preference(requireContext());
        refresh.setTitle("Refresh Recordings");
        refresh.setSummary("Scan Movies/OrynLauncher Recordings");
        refresh.setOnPreferenceClickListener(p -> {
            loadRecordings();
            return true;
        });
        screen.addPreference(refresh);

        Preference info = new Preference(requireContext());
        info.setTitle("Where recordings are saved");
        info.setSummary("Movies/OrynLauncher Recordings");
        screen.addPreference(info);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadRecordings();
    }

    private void loadRecordings() {
        if (recordingsCategory == null) return;
        while (recordingsCategory.getPreferenceCount() > 0) {
            recordingsCategory.removePreference(recordingsCategory.getPreference(0));
        }

        List<RecordingItem> items = new ArrayList<>();
        String[] projection = {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DURATION
        };
        String selection = MediaStore.Video.Media.RELATIVE_PATH + "=?";
        String[] args = {"Movies/OrynLauncher Recordings/"};

        try (Cursor cursor = requireContext().getContentResolver().query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection, selection, args,
                MediaStore.Video.Media.DATE_ADDED + " DESC")) {
            if (cursor != null) {
                int idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                int nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
                int durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
                while (cursor.moveToNext()) {
                    Uri uri = Uri.withAppendedPath(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            cursor.getString(idIndex));
                    items.add(new RecordingItem(
                            uri,
                            cursor.getString(nameIndex),
                            cursor.getLong(durationIndex)));
                }
            }
        } catch (Exception e) {
            Preference error = new Preference(requireContext());
            error.setTitle("Unable to load recordings");
            error.setSummary(e.getMessage() == null ? "Storage access failed." : e.getMessage());
            recordingsCategory.addPreference(error);
            return;
        }

        if (items.isEmpty()) {
            Preference empty = new Preference(requireContext());
            empty.setTitle("No recordings yet");
            empty.setSummary("Start recording from the in-game menu.");
            recordingsCategory.addPreference(empty);
            return;
        }

        for (RecordingItem item : items) {
            Preference video = new Preference(requireContext());
            video.setTitle(item.name);
            video.setSummary(formatDuration(item.duration) + " • Tap to play");
            video.setOnPreferenceClickListener(p -> {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(item.uri, "video/mp4");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "No video player found.", Toast.LENGTH_SHORT).show();
                }
                return true;
            });
            recordingsCategory.addPreference(video);
        }
    }

    private static String formatDuration(long ms) {
        long seconds = Math.max(0, ms / 1000);
        return String.format(java.util.Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60);
    }

    private static class RecordingItem {
        final Uri uri;
        final String name;
        final long duration;

        RecordingItem(Uri uri, String name, long duration) {
            this.uri = uri;
            this.name = name;
            this.duration = duration;
        }
    }
}
