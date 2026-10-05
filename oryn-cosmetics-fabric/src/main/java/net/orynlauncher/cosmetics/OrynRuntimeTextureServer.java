package net.orynlauncher.cosmetics;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Runtime-only texture transport for the 1.21.11 adapter.
 *
 * The launcher process is allowed to exit after starting Minecraft, so a
 * launcher-owned HTTP server cannot be used for runtime textures. This server
 * lives inside the Minecraft JVM and only exposes the already-selected skin
 * PNG. Minecraft's own PlayerSkinTextureDownloader still performs PNG decode,
 * validation, texture registration and rendering.
 */
public final class OrynRuntimeTextureServer {
    private static ServerSocket serverSocket;
    private static Thread serverThread;
    private static File skinFile;

    private OrynRuntimeTextureServer() {}

    public static synchronized String startSkin(File file) {
        if (file == null || !file.isFile() || !file.canRead()) {
            return null;
        }

        try {
            if (serverSocket == null || serverSocket.isClosed()) {
                serverSocket = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
                skinFile = file;
                serverThread = new Thread(OrynRuntimeTextureServer::run, "Oryn-Cosmetics-TextureServer");
                serverThread.setDaemon(true);
                serverThread.start();
            } else {
                skinFile = file;
            }

            return "http://127.0.0.1:" + serverSocket.getLocalPort()
                    + "/skin.png?v=" + file.lastModified();
        } catch (Exception e) {
            System.out.println("[ORYN-COSMETICS] Runtime texture server failed: " + e);
            return null;
        }
    }

    private static void run() {
        while (serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                handle(socket);
            } catch (Exception e) {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    System.out.println("[ORYN-COSMETICS] Runtime texture request failed: " + e);
                }
            }
        }
    }

    private static void handle(Socket socket) {
        try (Socket s = socket) {
            s.setSoTimeout(5000);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
            String request = reader.readLine();
            if (request == null) return;

            String[] parts = request.split(" ");
            if (parts.length < 2 || !"GET".equals(parts[0])) {
                writeResponse(s, 405, null);
                return;
            }

            String path = parts[1];
            int query = path.indexOf('?');
            if (query >= 0) path = path.substring(0, query);

            if (!"/skin.png".equals(path)) {
                writeResponse(s, 404, null);
                return;
            }

            File file = skinFile;
            if (file == null || !file.isFile() || !file.canRead()) {
                writeResponse(s, 404, null);
                return;
            }

            writeResponse(s, 200, file);
        } catch (Exception e) {
            System.out.println("[ORYN-COSMETICS] Runtime texture response failed: " + e);
        }
    }

    private static void writeResponse(Socket socket, int code, File file) throws Exception {
        OutputStream out = socket.getOutputStream();
        String status = code == 200 ? "200 OK" : (code == 405 ? "405 Method Not Allowed" : "404 Not Found");
        long length = file == null ? 0 : file.length();

        String header = "HTTP/1.1 " + status + "\r\n"
                + "Content-Type: image/png\r\n"
                + "Content-Length: " + length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.US_ASCII));

        if (file != null && code == 200) {
            try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
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
