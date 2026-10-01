package net.kdt.pojavlaunch.cosmetics;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.MotionEvent;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

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

        setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                pageReady = true;
                applyPending();
            }
        });

        loadUrl("file:///android_asset/skinview/skinview.html");
    }

    public void setSkin(Bitmap bitmap, boolean slim) {
        pendingSkin = bitmap;
        String model = slim ? "slim" : "default";
        if (pageReady) {
            evaluateJavascript("loadSkin(" + toJsString(bitmapToDataUrl(bitmap)) + "," +
                    toJsString(model) + ")", null);
        }
    }

    public void setCape(Bitmap bitmap) {
        pendingCape = bitmap;
        if (pageReady) {
            evaluateJavascript("loadCape(" + toJsString(bitmapToDataUrl(bitmap)) + ")", null);
        }
    }

    private void applyPending() {
        String skin = bitmapToDataUrl(pendingSkin);
        String model = "default";
        evaluateJavascript("loadSkin(" + toJsString(skin) + "," + toJsString(model) + ")", null);
        evaluateJavascript("loadCape(" + toJsString(bitmapToDataUrl(pendingCape)) + ")", null);
    }

    private String bitmapToDataUrl(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return null;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String toJsString(String value) {
        if (value == null) return "null";
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        // Let skinview3d's OrbitControls handle drag rotation and double-tap reset.
        return super.onTouchEvent(event);
    }

    @Override protected void onDetachedFromWindow() {
        pageReady = false;
        stopLoading();
        super.onDetachedFromWindow();
    }
}
