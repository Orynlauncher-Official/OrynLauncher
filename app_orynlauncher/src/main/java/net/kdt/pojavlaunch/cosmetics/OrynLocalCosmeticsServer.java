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
            OrynCosmeticsStore.CosmeticProfile profile = store.getActiveProfile(account);

            File skin = profile.skin == null || profile.skin.isEmpty() || !profile.skinEnabled
                    ? null : new File(context.getFilesDir(), "cosmetics/skins/" + profile.skin);
            File cape = profile.cape == null || profile.cape.isEmpty() || !profile.capeEnabled
                    ? null : new File(context.getFilesDir(), "cosmetics/capes/" + profile.cape);

            if (skin != null && (!skin.isFile() || !store.isValidSkin(skin))) {
                Log.w(TAG, "Skin cache invalid or missing; continuing without custom skin");
                skin = null;
            }
            if (cape != null && (!cape.isFile() || !store.isValidCape(cape))) {
                Log.w(TAG, "Cape cache invalid or missing; continuing without custom cape");
                cape = null;
            }

            if (skin == null && cape == null) {
                Log.i(TAG, "No enabled cached cosmetics for account " + safeUuid(account));
                return "{}";
            }

            skinFile = skin;
            capeFile = cape;
            slim = "slim".equalsIgnoreCase(profile.model);

            if (serverSocket == null || serverSocket.isClosed()) startServer();

            JSONObject textures = new JSONObject();
            if (skinFile != null) {
                JSONObject skinObject = new JSONObject();
                skinObject.put("url", "http://127.0.0.1:" + serverSocket.getLocalPort() + "/skin.png?v=" + System.currentTimeMillis());
                if (slim) {
                    JSONObject metadata = new JSONObject();
                    metadata.put("model", "slim");
                    skinObject.put("metadata", metadata);
                }
                textures.put("SKIN", skinObject);
            }
            if (capeFile != null) {
                JSONObject capeObject = new JSONObject();
                capeObject.put("url", "http://127.0.0.1:" + serverSocket.getLocalPort() + "/cape.png?v=" + System.currentTimeMillis());
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

            Log.i(TAG, "Account UUID: " + safeUuid(account));
            Log.i(TAG, "Skin selected: " + (profile.skin == null ? "" : profile.skin));
            Log.i(TAG, "Skin model: " + (slim ? "slim" : "classic"));
            Log.i(TAG, "Cape selected: " + (profile.cape == null ? "" : profile.cape));
            Log.i(TAG, "Skin cache: " + (skinFile != null ? "HIT" : "MISS"));
            Log.i(TAG, "Cape cache: " + (capeFile != null ? "HIT" : "MISS"));
            Log.i(TAG, "Minecraft cosmetic provider initialized");
            Log.i(TAG, "Cosmetic texture payload prepared for Minecraft client");
            return userProperties.toString();
        } catch (Exception e) {
            Log.w(TAG, "Could not prepare local cosmetics", e);
            return "{}";
        }
    }

    private static String safeUuid(Account account) {
        if (account == null || account.profileId == null || account.profileId.isEmpty()) {
            return "unknown";
        }
        return account.profileId;
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

            // Strip the query string so cache-busting URLs such as
            // /skin.png?v=123 are still served by the local texture server.
            String[] requestParts = request.split(" ");
            if (requestParts.length < 2 || !"GET".equals(requestParts[0])) {
                writeResponse(s, 405, null);
                return;
            }

            String path = requestParts[1];
            int queryIndex = path.indexOf('?');
            if (queryIndex >= 0) {
                path = path.substring(0, queryIndex);
            }

            File file;
            if ("/skin.png".equals(path)) {
                file = skinFile;
            } else if ("/cape.png".equals(path)) {
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
