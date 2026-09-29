package net.kdt.pojavlaunch.game.recorder;

import android.view.View;
import java.lang.ref.WeakReference;

public final class GameSurfaceRegistry {
    private static volatile WeakReference<View> viewRef;

    private GameSurfaceRegistry() {}

    public static void register(View view) {
        viewRef = new WeakReference<>(view);
    }

    public static void unregister() {
        WeakReference<View> ref = viewRef;
        if (ref != null) ref.clear();
        viewRef = null;
    }

    public static View getView() {
        WeakReference<View> ref = viewRef;
        return ref == null ? null : ref.get();
    }
}
