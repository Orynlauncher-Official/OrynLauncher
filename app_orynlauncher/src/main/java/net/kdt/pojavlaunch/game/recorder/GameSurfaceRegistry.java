package net.kdt.pojavlaunch.game.recorder;

import android.view.View;
import java.lang.ref.WeakReference;

public final class GameSurfaceRegistry {
    private static volatile WeakReference<View> viewRef;
    private static volatile boolean surfaceReady;

    private GameSurfaceRegistry() {}

    public static void register(View view) {
        viewRef = new WeakReference<>(view);
        surfaceReady = false;
    }

    public static void markReady() { surfaceReady = true; }

    public static void markNotReady() { surfaceReady = false; }

    public static boolean isReady() { return surfaceReady; }

    public static void unregister() {
        WeakReference<View> ref = viewRef;
        if (ref != null) ref.clear();
        viewRef = null;
        surfaceReady = false;
    }

    public static View getView() {
        WeakReference<View> ref = viewRef;
        return ref == null ? null : ref.get();
    }
}
