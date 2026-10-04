package net.kdt.pojavlaunch.discord;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import com.discord.socialsdk.rpc.IDiscordRpcCallback;
import com.discord.socialsdk.rpc.IDiscordRpcConnection;
import com.discord.socialsdk.rpc.IDiscordRpcService;

import java.util.UUID;

public final class OrynDiscordPresence {
    private static final String TAG = "OrynDiscordPresence";
    private static final long APPLICATION_ID = 1556187212620763238L;
    private static final String DISCORD_PACKAGE = "com.discord";
    private static final String RPC_ACTION = "com.discord.socialsdk.rpc.IDiscordRpcService";

    private final Activity activity;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private IDiscordRpcService service;
    private IDiscordRpcConnection connection;
    private boolean bound;
    private String pendingVersion = "Unknown";

    public OrynDiscordPresence(Activity activity) {
        this.activity = activity;
    }

    public void start(String minecraftVersion) {
        pendingVersion = minecraftVersion == null ? "Unknown" : minecraftVersion;
        mainHandler.post(() -> {
            if (bound || !isDiscordInstalled()) return;

            Intent intent = new Intent(RPC_ACTION);
            intent.setPackage(DISCORD_PACKAGE);

            try {
                bound = activity.bindService(intent, serviceConnection, Activity.BIND_AUTO_CREATE);
                if (!bound) Log.d(TAG, "Discord RPC service unavailable");
            } catch (SecurityException e) {
                Log.d(TAG, "Discord RPC bind denied", e);
            }
        });
    }

    private final IDiscordRpcCallback callback = new IDiscordRpcCallback.Stub() {
        @Override
        public void onFrame(String frame) {
            Log.d(TAG, "RPC frame: " + frame);
        }

        @Override
        public void onClose(int code, String message) {
            Log.d(TAG, "RPC closed: " + code + " " + message);
        }
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = IDiscordRpcService.Stub.asInterface(binder);
            try {
                connection = service.connect(APPLICATION_ID, "1", callback);
                if (connection != null) sendActivity(pendingVersion);
            } catch (RemoteException e) {
                Log.w(TAG, "Discord RPC handshake failed", e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            connection = null;
            bound = false;
        }
    };

    private void sendActivity(String minecraftVersion) {
        if (connection == null) return;

        String payload =
                "{"
                + "\"cmd\":\"SET_ACTIVITY\","
                + "\"args\":{"
                + "\"pid\":" + android.os.Process.myPid() + ","
                + "\"activity\":{"
                + "\"details\":\"Minecraft " + escape(minecraftVersion) + "\","
                + "\"state\":\"Playing OrynLauncher V4\","
                + "\"timestamps\":{\"start\":" + System.currentTimeMillis() + "},"
                + "\"assets\":{"
                + "\"large_image\":\"orynlauncher\","
                + "\"large_text\":\"OrynLauncher V4\""
                + "}"
                + "}"
                + "},"
                + "\"nonce\":\"" + UUID.randomUUID() + "\""
                + "}";

        try {
            connection.sendFrame(payload);
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to publish Discord Rich Presence", e);
        }
    }

    public void stop() {
        mainHandler.post(() -> {
            IDiscordRpcConnection current = connection;
            connection = null;

            if (current != null) {
                try {
                    String clear =
                            "{"
                            + "\"cmd\":\"SET_ACTIVITY\","
                            + "\"args\":{\"pid\":" + android.os.Process.myPid() + ",\"activity\":null},"
                            + "\"nonce\":\"" + UUID.randomUUID() + "\""
                            + "}";
                    current.sendFrame(clear);
                    current.disconnect();
                } catch (RemoteException ignored) {
                }
            }

            service = null;
            if (bound) {
                try {
                    activity.unbindService(serviceConnection);
                } catch (IllegalArgumentException ignored) {
                }
                bound = false;
            }
        });
    }

    private boolean isDiscordInstalled() {
        try {
            activity.getPackageManager().getPackageInfo(DISCORD_PACKAGE, 0);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String escape(String value) {
        if (value == null) return "Unknown";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
