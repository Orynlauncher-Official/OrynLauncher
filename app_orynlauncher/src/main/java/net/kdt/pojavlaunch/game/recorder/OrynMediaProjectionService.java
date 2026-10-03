package net.kdt.pojavlaunch.game.recorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import net.kdt.pojavlaunch.R;

/**
 * Holds the required Android 14+ mediaProjection foreground-service state while
 * OrynLauncher uses MediaProjection only for game playback-audio capture.
 *
 * This service does NOT capture the screen.
 */
public class OrynMediaProjectionService extends Service {
    private static final String CHANNEL_ID = "oryn_recorder_projection";
    private static final int NOTIFICATION_ID = 9042;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startAsForeground();
    }

    private void startAsForeground() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        Notification notification = builder
                .setContentTitle("Oryn Recorder")
                .setContentText("Capturing Minecraft game audio")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Oryn Recorder",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Foreground service used while recording Minecraft game audio.");
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // onCreate() already promoted the service to the required FGS type.
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
