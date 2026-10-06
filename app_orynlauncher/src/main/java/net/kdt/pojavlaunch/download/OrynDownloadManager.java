package net.kdt.pojavlaunch.download;

import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

public final class OrynDownloadManager {
    private static final String TAG = "OrynDownload";
    public interface Callback {
        void onProgress(int percent);
    }

    public File download(ModrinthFile file, File output, Callback callback) throws Exception {
        if (file == null || file.url == null || file.url.isEmpty()) {
            throw new Exception("No downloadable file was returned");
        }
        File parent = output.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new Exception("Could not create download directory");
        }

        Exception last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            if (Thread.currentThread().isInterrupted()) throw new Exception("Download cancelled");
            File temp = new File(parent, output.getName() + ".part");
            if (temp.exists()) temp.delete();

            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(file.url).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "*/*");
                connection.setRequestProperty("User-Agent",
                        "Orynlauncher-Official/OrynLauncher/4.1 (https://github.com/Orynlauncher-Official/OrynLauncher)");

                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) throw new Exception("HTTP " + status);

                long total = connection.getContentLengthLong() > 0
                        ? connection.getContentLengthLong() : file.size;
                long done = 0L;
                long lastUi = 0L;
                MessageDigest digest = MessageDigest.getInstance("SHA-1");

                try (InputStream in = new BufferedInputStream(connection.getInputStream());
                     FileOutputStream out = new FileOutputStream(temp)) {
                    byte[] buffer = new byte[256 * 1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) {
                            throw new Exception("Download cancelled");
                        }
                        out.write(buffer, 0, read);
                        digest.update(buffer, 0, read);
                        done += read;
                        long now = SystemClock.elapsedRealtime();
                        if (now - lastUi > 120 || (total > 0 && done >= total)) {
                            int percent = total > 0
                                    ? (int) Math.min(100, done * 100L / total) : 0;
                            if (callback != null) callback.onProgress(percent);
                            lastUi = now;
                        }
                    }
                    out.flush();
                }

                if (file.size > 0 && temp.length() != file.size) {
                    throw new Exception("Downloaded file size does not match Modrinth metadata");
                }
                if (file.sha1 != null && !file.sha1.isEmpty()) {
                    String actual = hex(digest.digest());
                    if (!file.sha1.equalsIgnoreCase(actual)) {
                        throw new Exception("SHA-1 verification failed");
                    }
                }
                if (!temp.isFile() || temp.length() <= 0) {
                    throw new Exception("Downloaded file is empty");
                }
                Log.d(TAG, "[ORYN-DOWNLOAD] downloaded=true destination=" + output.getAbsolutePath()
                        + " bytes=" + temp.length() + " fileExists=" + temp.isFile());

                if (output.exists() && !output.delete()) {
                    throw new Exception("Could not replace existing file");
                }
                if (!temp.renameTo(output)) {
                    throw new Exception("Could not finalize downloaded file");
                }
                if (!output.isFile() || output.length() <= 0) {
                    throw new Exception("Final downloaded file does not exist or is empty");
                }
                Log.d(TAG, "[ORYN-DOWNLOAD] finalized=true destination=" + output.getAbsolutePath()
                        + " fileExists=true");
                if (callback != null) callback.onProgress(100);
                return output;
            } catch (Exception error) {
                last = error;
                if (temp.exists()) temp.delete();
                if (attempt < 3) {
                    try {
                        Thread.sleep(500L * attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new Exception("Download cancelled", interrupted);
                    }
                }
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        throw last == null ? new Exception("Download failed") : last;
    }

    private String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return out.toString();
    }
}
