package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import git.artdeell.mojo.R;

/**
 * Checks the official OrynLauncher GitHub release for a newer launcher version.
 *
 * This is deliberately independent from the generated Gradle versionName because
 * that value is currently build/commit based. The public release version is kept
 * here so v2.1 can correctly detect v2.2+ releases.
 */
public final class OrynUpdateChecker {
    private static final String TAG = "OrynUpdateChecker";
    private static final String CURRENT_VERSION = "2.1";
    private static final String LATEST_RELEASE_API =
            "https://api.github.com/repos/Orynlauncher-Official/OrynLauncher/releases/latest";

    private OrynUpdateChecker() {}

    public static void check(Context context) {
        Context appContext = context.getApplicationContext();

        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(LATEST_RELEASE_API);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(7000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "OrynLauncher-UpdateChecker");
                connection.setInstanceFollowRedirects(true);

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "GitHub release check returned HTTP " + connection.getResponseCode());
                    return;
                }

                String response = readFully(connection.getInputStream());
                JSONObject release = new JSONObject(response);

                String tag = release.optString("tag_name", "");
                String latestVersion = normalizeVersion(tag);
                String releaseUrl = release.optString(
                        "html_url",
                        "https://github.com/Orynlauncher-Official/OrynLauncher/releases"
                );

                if (latestVersion.isEmpty() || !isNewer(latestVersion, CURRENT_VERSION)) {
                    return;
                }

                String releaseName = release.optString("name", "");
                if (releaseName.isEmpty()) releaseName = "OrynLauncher " + latestVersion;

                final String finalReleaseName = releaseName;
                final String finalVersion = latestVersion;
                final String finalUrl = releaseUrl;

                new Handler(Looper.getMainLooper()).post(() ->
                        showUpdateDialog(appContext, finalReleaseName, finalVersion, finalUrl));
            } catch (Exception e) {
                Log.w(TAG, "Unable to check for OrynLauncher updates", e);
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "OrynLauncher-UpdateCheck").start();
    }

    private static void showUpdateDialog(
            Context context, String releaseName, String version, String releaseUrl) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.oryn_update_title)
                .setMessage(context.getString(R.string.oryn_update_message, releaseName, version))
                .setPositiveButton(R.string.oryn_update_button,
                        (dialog, which) -> {
                            try {
                                context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl)));
                            } catch (Exception e) {
                                Log.w(TAG, "Unable to open release page", e);
                            }
                        })
                .setNegativeButton(R.string.oryn_update_later, null)
                .setCancelable(true)
                .show();
    }

    private static String normalizeVersion(String value) {
        if (value == null) return "";
        String version = value.trim();
        if (version.startsWith("v") || version.startsWith("V")) {
            version = version.substring(1);
        }
        return version;
    }

    private static boolean isNewer(String remote, String local) {
        try {
            String[] remoteParts = remote.split("[.-]");
            String[] localParts = local.split("[.-]");
            int count = Math.max(remoteParts.length, localParts.length);

            for (int i = 0; i < count; i++) {
                int remoteNumber = i < remoteParts.length ? numberPart(remoteParts[i]) : 0;
                int localNumber = i < localParts.length ? numberPart(localParts[i]) : 0;
                if (remoteNumber != localNumber) return remoteNumber > localNumber;
            }
        } catch (Exception ignored) {
            // If a release tag isn't numeric, don't interrupt launcher startup.
        }
        return false;
    }

    private static int numberPart(String value) {
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isDigit(c)) digits.append(c);
            else break;
        }
        return digits.length() == 0 ? 0 : Integer.parseInt(digits.toString());
    }

    private static String readFully(InputStream input) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }
}
