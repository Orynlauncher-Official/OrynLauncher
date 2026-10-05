package net.orynlauncher.cosmetics;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runtime-only diagnostics. Kept outside Mixin classes so Mixin cannot
 * interpret helper methods as target methods.
 */
public final class OrynCosmeticsDebugCounters {
    private static final AtomicInteger APPLY_SKIN = new AtomicInteger();
    private static final AtomicInteger DOWNLOADER_REQUESTS = new AtomicInteger();
    private static final AtomicInteger PNG_DECODES = new AtomicInteger();
    private static final AtomicInteger TEXTURE_REGISTRATIONS = new AtomicInteger();

    private OrynCosmeticsDebugCounters() {}

    public static void recordApplySkin() { APPLY_SKIN.incrementAndGet(); }

    public static int applySkinCount() { return APPLY_SKIN.get(); }

    public static void recordDownloaderRequest() {
        DOWNLOADER_REQUESTS.incrementAndGet();
    }

    public static void recordPngDecode() {
        PNG_DECODES.incrementAndGet();
    }

    public static void recordTextureRegistration() {
        TEXTURE_REGISTRATIONS.incrementAndGet();
    }

    public static int downloaderRequests() {
        return DOWNLOADER_REQUESTS.get();
    }

    public static int pngDecodes() {
        return PNG_DECODES.get();
    }

    public static int textureRegistrations() {
        return TEXTURE_REGISTRATIONS.get();
    }
}
