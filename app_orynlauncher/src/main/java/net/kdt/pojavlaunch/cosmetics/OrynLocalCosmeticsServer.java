package net.kdt.pojavlaunch.cosmetics;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import net.kdt.pojavlaunch.authenticator.accounts.Account;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Supplies Oryn's locally imported cosmetics to vanilla Minecraft through the
 * launcher user_properties texture payload. No internet connection is required.
 */
public final class OrynLocalCosmeticsServer {
    private static final String TAG = "OrynCosmetics";
    private static ServerSocket serverSocket;
    private static Thread serverThread;
    private static File skinFile;
    private static File capeFile;
    private static boolean slim;

    private OrynLocalCosmeticsServer() {}

    public static synchronized String prepare(Context context, Account account) {
        try {
            OrynCosmeticsStore store = new OrynCosmeticsStore(context);
            OrynCosmeticsStore.CosmeticProfile profile = store.getActiveProfile();

            File skin = profile.skin == null || profile.skin.isEmpty()
                    ? null : new File(context.getFilesDir(), "cosmetics/skins/" + profile.skin);
            File cape = profile.cape == null || profile.cape.isEmpty()
                    ? null : new File(context.getFilesDir(), "cosmetics/capes/" + profile.cape);

            if (skin == null || !skin.isFile()) skin = null;
            if (cape == null || !cape.isFile()) cape = null;

            if (skin == null && cape == null) return "{}";

            skinFile = skin;
            capeFile = cape;
            slim = "slim".equalsIgnoreCase(profile.model);

            if (serverSocket == null || serverSocket.isClosed()) startServer();

            JSONObject textures = new JSONObject();
            if (skinFile != null) {
                JSONObject skinObject = new JSONObject();
                skinObject.put("url", "http://127.0.0.1:" + serverSocket.getLocalPort() + "/skin.png");
                if (slim) {
                    JSONObject metadata = new JSONObject();
                    metadata.put("model", "slim");
                    skinObject.put("metadata", metadata);
                }
                textures.put("SKIN", skinObject);
            }
            if (capeFile != null) {
                JSONObject capeObject = new JSONObject();
                capeObject.put("url", "http://127.0.0.1:" + serverSocket.getLocalPort() + "/cape.png");
                textures.put("CAPE", capeObject);
            }

            JSONObject payload = new JSONObject();
            payload.put("timestamp", System.currentTimeMillis());
            payload.put("profileId", account.profileId == null
                    ? "00000000000000000000000000000000"
                    : account.profileId.replace("-", ""));
            payload.put("profileName", account.username == null ? "Player" : account.username);
            payload.put("textures", textures);

            String encoded = Base64.encodeToString(
                    payload.toString().getBytes(StandardCharsets.UTF_8),
                    Base64.NO_WRAP
            );

            JSONObject property = new JSONObject();
            property.put("value", encoded);

            JSONObject userProperties = new JSONObject();
            org.json.JSONArray values = new org.json.JSONArray();
            values.put(property);
            userProperties.put("textures", values);

            Log.i(TAG, "Local cosmetics enabled: skin=" + (skinFile != null)
                    + ", cape=" + (capeFile != null)
                    + ", model=" + (slim ? "slim" : "classic"));
            return userProperties.toString();
        } catch (Exception e) {
            Log.w(TAG, "Could not prepare local cosmetics", e);
            return "{}";
        }
    }

    private static void startServer() throws Exception {
        serverSocket = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
        serverThread = new Thread(() -> {
            while (serverSocket != null && !serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    handle(socket);
                } catch (Exception e) {
                    if (serverSocket != null && !serverSocket.isClosed()) {
                        Log.w(TAG, "Local texture request failed", e);
                    }
                }
            }
        }, "Oryn-Cosmetics-Server");
        serverThread.setDaemon(true);
        serverThread.start();
        Log.i(TAG, "Local cosmetics server started on port " + serverSocket.getLocalPort());
    }

    private static void handle(Socket socket) {
        try (Socket s = socket) {
            s.setSoTimeout(5000);
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
            String request = reader.readLine();
            if (request == null) return;

            File file;
            if (request.startsWith("GET /skin.png ")) {
                file = skinFile;
            } else if (request.startsWith("GET /cape.png ")) {
                file = capeFile;
            } else {
                writeResponse(s, 404, null);
                return;
            }

            if (file == null || !file.isFile()) {
                writeResponse(s, 404, null);
                return;
            }

            writeResponse(s, 200, file);
        } catch (Exception e) {
            Log.w(TAG, "Local texture response failed", e);
        }
    }

    private static void writeResponse(Socket socket, int code, File file) throws Exception {
        OutputStream out = socket.getOutputStream();
        String status = code == 200 ? "200 OK" : "404 Not Found";
        long length = file == null ? 0 : file.length();
        String header = "HTTP/1.1 " + status + "\r\n"
                + "Content-Type: image/png\r\n"
                + "Content-Length: " + length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.US_ASCII));

        if (file != null && code == 200) {
            try (FileInputStream in = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                }
            }
        }
        out.flush();
    }
}
