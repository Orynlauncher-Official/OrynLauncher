package net.kdt.pojavlaunch.cosmetics;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.BitmapFactory;
import java.io.InputStream;
import android.graphics.drawable.ColorDrawable;
import android.view.MotionEvent;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.JavascriptInterface;
import android.webkit.WebViewClient;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.util.Base64;

/**
 * Real Minecraft player preview powered by skinview3d, the same 3D skin
 * renderer used by ZalithLauncher2. This replaces the approximate OpenGL
 * cuboid renderer with the actual Minecraft player model, skin UV mapping,
 * slim/classic arms, cape geometry, animations and touch rotation.
 */
@SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
public class OrynCosmeticPreviewView extends WebView {
    private Bitmap pendingSkin;
    private Bitmap pendingCape;
    private boolean pageReady;
    private boolean pendingSlim;
    private boolean pendingCapeBack;
    private float lastTouchX;
    private boolean draggingPreview;
    private int thumbnailSequence;
    private final Map<String, ThumbnailCallback> thumbnailCallbacks = new HashMap<>();
    private final List<String> pendingThumbnailScripts = new ArrayList<>();

    public interface ThumbnailCallback { void onRendered(Bitmap bitmap); }

    public OrynCosmeticPreviewView(Context context) {
        super(context);
        setBackground(new ColorDrawable(Color.TRANSPARENT));
        setLayerType(WebView.LAYER_TYPE_HARDWARE, null);

        WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(false);

        setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(false);

        addJavascriptInterface(new Object() {
            @JavascriptInterface public void onThumbnailRendered(String id, String dataUrl) {
                Bitmap bitmap = null;
                try {
                    int comma = dataUrl == null ? -1 : dataUrl.indexOf(',');
                    if (comma >= 0) {
                        byte[] bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT);
                        bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    }
                } catch (Throwable ignored) {}
                final Bitmap rendered = bitmap;
                post(() -> {
                    ThumbnailCallback callback = thumbnailCallbacks.remove(id);
                    if (callback != null) callback.onRendered(rendered);
                    else if (rendered != null && !rendered.isRecycled()) rendered.recycle();
                });
            }
        }, "OrynThumbnailBridge");

        setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                pageReady = true;
                applyPending();
                for (String script : new ArrayList<>(pendingThumbnailScripts)) evaluateJavascript(script, null);
                pendingThumbnailScripts.clear();
            }
        });

        loadUrl("file:///android_asset/skinview/skinview.html");
    }

    public void setSkin(Bitmap bitmap, boolean slim) {
        // Always keep a real player visible. When the profile has no imported skin,
        // use the bundled Oryn Steve texture instead of passing a null texture.
        pendingSkin = bitmap != null ? bitmap : loadDefaultSkin();
        pendingSlim = slim;
        String model = slim ? "slim" : "default";
        if (pageReady) {
            // Use pendingSkin so "None" immediately falls back to the bundled Steve.
            evaluateJavascript("loadSkin(" + toJsString(bitmapToDataUrl(pendingSkin)) + "," +
                    toJsString(model) + ")", null);
        }
    }

    public void setCape(Bitmap bitmap) {
        pendingCape = bitmap;
        if (pageReady) {
            evaluateJavascript("loadCape(" + toJsString(bitmapToDataUrl(bitmap)) + ")", null);
        }
    }

    public void setCapePreview(boolean showBack) {
        pendingCapeBack = showBack;
        if (pageReady) {
            evaluateJavascript("setCapePreview(" + (showBack ? "true" : "false") + ")", null);
        }
    }

    public void rotateBy(float radians) {
        if (pageReady) evaluateJavascript("rotatePreview(" + radians + ")", null);
    }

    /** Render a real front-facing Minecraft player thumbnail using the same skinview3d renderer. */
    public void renderThumbnail(Bitmap skin, boolean slim, ThumbnailCallback callback) {
        if (skin == null || skin.isRecycled() || callback == null) {
            if (callback != null) callback.onRendered(null);
            return;
        }
        String data = bitmapToDataUrl(skin);
        if (data == null) { callback.onRendered(null); return; }
        String id = "thumb" + (++thumbnailSequence);
        thumbnailCallbacks.put(id, callback);
        String script = "renderSkinThumbnail(" + toJsString(id) + "," + toJsString(data) + "," +
                toJsString(slim ? "slim" : "default") + ")";
        if (pageReady) evaluateJavascript(script, null);
        else pendingThumbnailScripts.add(script);
    }

    private void applyPending() {
        String skin = bitmapToDataUrl(pendingSkin);
        String model = pendingSlim ? "slim" : "default";
        evaluateJavascript("loadSkin(" + toJsString(skin) + "," + toJsString(model) + ")", null);
        evaluateJavascript("setCapePreview(" + (pendingCapeBack ? "true" : "false") + ")", null);
        evaluateJavascript("loadCape(" + toJsString(bitmapToDataUrl(pendingCape)) + ")", null);
    }

    private Bitmap loadDefaultSkin() {
        try (InputStream in = getContext().getAssets().open("skinview/oryn_steve_skin.png")) {
            return BitmapFactory.decodeStream(in);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String bitmapToDataUrl(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return null;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String toJsString(String value) {
        if (value == null) return "null";
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        // Rotate the player model directly while dragging. This avoids WebView/OrbitControls
        // gesture conflicts and keeps drag responsive on Android landscape screens.
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                draggingPreview = true;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (draggingPreview) {
                    float x = event.getX();
                    float delta = x - lastTouchX;
                    lastTouchX = x;
                    if (pageReady && Math.abs(delta) > 0.1f) {
                        evaluateJavascript("rotatePreview(" + (-delta * 0.012f) + ")", null);
                    }
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                draggingPreview = false;
                performClick();
                return true;
        }
        return true;
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override protected void onDetachedFromWindow() {
        pageReady = false;
        stopLoading();
        super.onDetachedFromWindow();
    }
}
