package net.kdt.pojavlaunch.discord;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

import java.util.UUID;

/**
 * Lightweight Android RPC bridge for Discord Rich Presence.
 *
 * This intentionally does not depend on Discord's large AAR at compile time.
 * It speaks the public Social SDK Binder/AIDL contract directly.
 */
public final class OrynDiscordPresence {
    private static final String TAG = "OrynDiscordPresence";
    private static final long APPLICATION_ID = 1556187212620763238L;
    private static final String DISCORD_PACKAGE = "com.discord";
    private static final String RPC_ACTION = "com.discord.socialsdk.rpc.IDiscordRpcService";
    private static final String SERVICE_DESCRIPTOR = RPC_ACTION;
    private static final String CONNECTION_DESCRIPTOR =
            "com.discord.socialsdk.rpc.IDiscordRpcConnection";
    private static final String CALLBACK_DESCRIPTOR =
            "com.discord.socialsdk.rpc.IDiscordRpcCallback";

    private final Activity activity;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private RpcService service;
    private RpcConnection connection;
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

    private final RpcCallback callback = new RpcCallback() {
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
            service = new RpcService(binder);
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
                + "\"activity\":{\"name\":\"OrynLauncher\","
                + "\"details\":\"Playing Minecraft " + escape(minecraftVersion) + "\","
                + "\"state\":\"Minecraft " + escape(minecraftVersion) + "\","
                + "\"timestamps\":{\"start\":" + System.currentTimeMillis() + "},"
                + "\"assets\":{"
                + "\"large_image\":\"orynlauncher\","
                + "\"large_text\":\"OrynLauncher\""
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
            RpcConnection current = connection;
            connection = null;

            if (current != null) {
                try {
                    String clear =
                            "{"
                            + "\"cmd\":\"SET_ACTIVITY\","
                            + "\"args\":{\"pid\":" + android.os.Process.myPid()
                            + ",\"activity\":null},"
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

    private static final class RpcService implements IInterface {
        private final IBinder binder;

        RpcService(IBinder binder) {
            this.binder = binder;
        }

        @Override
        public IBinder asBinder() {
            return binder;
        }

        RpcConnection connect(long applicationId, String version, RpcCallback callback)
                throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(SERVICE_DESCRIPTOR);
                data.writeLong(applicationId);
                data.writeString(version);
                data.writeStrongBinder(callback);
                binder.transact(1, data, reply, 0);
                reply.readException();
                IBinder connectionBinder = reply.readStrongBinder();
                return connectionBinder == null ? null : new RpcConnection(connectionBinder);
            } finally {
                reply.recycle();
                data.recycle();
            }
        }
    }

    private static final class RpcConnection implements IInterface {
        private final IBinder binder;

        RpcConnection(IBinder binder) {
            this.binder = binder;
        }

        @Override
        public IBinder asBinder() {
            return binder;
        }

        void sendFrame(String frame) throws RemoteException {
            transactString(1, frame);
        }

        void disconnect() throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(CONNECTION_DESCRIPTOR);
                binder.transact(2, data, reply, 0);
                reply.readException();
            } finally {
                reply.recycle();
                data.recycle();
            }
        }

        private void transactString(int code, String value) throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(CONNECTION_DESCRIPTOR);
                data.writeString(value);
                binder.transact(code, data, reply, 0);
                reply.readException();
            } finally {
                reply.recycle();
                data.recycle();
            }
        }
    }

    private abstract static class RpcCallback extends Binder {
        RpcCallback() {
            attachInterface(null, CALLBACK_DESCRIPTOR);
        }

        abstract void onFrame(String frame);
        abstract void onClose(int code, String message);

        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code >= 1 && code <= 0xFFFFFF) {
                data.enforceInterface(CALLBACK_DESCRIPTOR);
            }
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(CALLBACK_DESCRIPTOR);
                return true;
            }
            if (code == 1) {
                onFrame(data.readString());
                reply.writeNoException();
                return true;
            }
            if (code == 2) {
                onClose(data.readInt(), data.readString());
                reply.writeNoException();
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
    }
}
