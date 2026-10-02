package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.widget.FrameLayout;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import git.artdeell.mojo.R;

public final class OrynCustomizationManager {
    private static final String PREFS = "oryn_customization";
    private static final String KEY_THEME = "theme";
    private static final String KEY_ACCENT = "accent";
    private static final String KEY_BG_URI = "background_uri";
    private static final String KEY_BG_OPACITY = "background_opacity";
    private static final String KEY_BG_MODE = "background_mode";
    private static final String KEY_ANIMATED = "animated_background";
    private static final String KEY_ANIM_INTENSITY = "animation_intensity";
    private static final String KEY_BUTTON_STYLE = "button_style";
    private static final String KEY_UI_SCALE = "ui_scale";
    private static final String KEY_HOME_LAYOUT = "home_layout";
    private static final String KEY_LOGO_URI = "logo_uri";
    private static final String KEY_PLAYER_URI = "player_uri";
    private static final String KEY_PAGE_ANIMATION = "page_animation";
    private static final String KEY_BUTTON_ANIMATION = "button_animation";
    private static final String KEY_GENERAL_ANIMATION = "general_animation";
    private static final int DEFAULT_ACCENT = Color.rgb(80, 150, 255);
    private static final ExecutorService IMAGE_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int ANIMATION_TAG = 0x4F52594E;

    public static final String THEME_DARK = "Oryn Dark";
    public static final String THEME_BLACK = "Oryn Black";
    public static final String THEME_GREY = "Oryn Grey";
    public static final String THEME_MIDNIGHT = "Midnight";
    public static final String THEME_AMOLED = "AMOLED";
    public static final String THEME_CUSTOM = "Custom";

    private OrynCustomizationManager() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String getTheme(Context c) { return prefs(c).getString(KEY_THEME, THEME_DARK); }
    public static void setTheme(Context c, String v) { prefs(c).edit().putString(KEY_THEME, v).apply(); }
    public static int getAccent(Context c) { return prefs(c).getInt(KEY_ACCENT, DEFAULT_ACCENT); }
    public static void setAccent(Context c, int v) {
        prefs(c).edit().putInt(KEY_ACCENT, v).apply();
        OrynThemeManager.setColor(c, v);
    }
    public static String getBackgroundUri(Context c) { return prefs(c).getString(KEY_BG_URI, ""); }
    public static void setBackgroundUri(Context c, String v) { prefs(c).edit().putString(KEY_BG_URI, v == null ? "" : v).apply(); }
    public static int getBackgroundOpacity(Context c) { return prefs(c).getInt(KEY_BG_OPACITY, 72); }
    public static void setBackgroundOpacity(Context c, int v) { prefs(c).edit().putInt(KEY_BG_OPACITY, Math.max(0, Math.min(100, v))).apply(); }
    public static String getBackgroundMode(Context c) { return prefs(c).getString(KEY_BG_MODE, "Crop"); }
    public static void setBackgroundMode(Context c, String v) { prefs(c).edit().putString(KEY_BG_MODE, v).apply(); }
    public static boolean isAnimatedBackground(Context c) { return prefs(c).getBoolean(KEY_ANIMATED, false); }
    public static void setAnimatedBackground(Context c, boolean v) { prefs(c).edit().putBoolean(KEY_ANIMATED, v).apply(); }
    public static String getAnimationIntensity(Context c) { return prefs(c).getString(KEY_ANIM_INTENSITY, "Medium"); }
    public static void setAnimationIntensity(Context c, String v) { prefs(c).edit().putString(KEY_ANIM_INTENSITY, v).apply(); }
    public static String getButtonStyle(Context c) { return prefs(c).getString(KEY_BUTTON_STYLE, "Default"); }
    public static void setButtonStyle(Context c, String v) { prefs(c).edit().putString(KEY_BUTTON_STYLE, v).apply(); }
    public static int getUiScale(Context c) { return prefs(c).getInt(KEY_UI_SCALE, 100); }
    public static void setUiScale(Context c, int v) { prefs(c).edit().putInt(KEY_UI_SCALE, Math.max(80, Math.min(120, v))).apply(); }
    public static String getHomeLayout(Context c) { return prefs(c).getString(KEY_HOME_LAYOUT, "Default"); }
    public static void setHomeLayout(Context c, String v) { prefs(c).edit().putString(KEY_HOME_LAYOUT, v).apply(); }
    public static String getPlayerUri(Context c) { return prefs(c).getString(KEY_PLAYER_URI, ""); }
    public static void setPlayerUri(Context c, String v) { prefs(c).edit().putString(KEY_PLAYER_URI, v == null ? "" : v).apply(); }
    public static String getPageAnimation(Context c) { return prefs(c).getString(KEY_PAGE_ANIMATION, "Fade"); }
    public static void setPageAnimation(Context c, String v) { prefs(c).edit().putString(KEY_PAGE_ANIMATION, v).apply(); }
    public static String getButtonAnimation(Context c) { return prefs(c).getString(KEY_BUTTON_ANIMATION, "Ripple"); }
    public static void setButtonAnimation(Context c, String v) { prefs(c).edit().putString(KEY_BUTTON_ANIMATION, v).apply(); }
    public static boolean isGeneralAnimation(Context c) { return prefs(c).getBoolean(KEY_GENERAL_ANIMATION, true); }
    public static void setGeneralAnimation(Context c, boolean v) { prefs(c).edit().putBoolean(KEY_GENERAL_ANIMATION, v).apply(); }

    public static int getBackgroundColor(Context c) {
        String theme = getTheme(c);
        if (THEME_AMOLED.equals(theme)) return Color.BLACK;
        if (THEME_BLACK.equals(theme)) return Color.rgb(10, 10, 12);
        if (THEME_GREY.equals(theme)) return Color.rgb(30, 32, 36);
        if (THEME_MIDNIGHT.equals(theme)) return Color.rgb(12, 18, 34);
        return Color.rgb(24, 24, 28);
    }

    public static void reset(Context c) {
        prefs(c).edit().clear().apply();
        OrynThemeManager.setColor(c, DEFAULT_ACCENT);
        if (c instanceof Activity) apply((Activity) c);
    }

    public static void apply(Activity activity) {
        if (activity == null) return;
        View content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        int accent = getAccent(activity);
        OrynThemeManager.apply(activity);
        applyView(content, accent);

        View home = activity.findViewById(R.id.fragment_menu_main);
        if (home == null) home = findLauncherHome(content);
        if (home != null) {
            if (getBackgroundUri(activity).length() == 0) home.setBackgroundColor(getBackgroundColor(activity));
            else applyBackgroundAsync(activity, home);
            float scale = getUiScale(activity) / 100f;
            home.setScaleX(scale);
            home.setScaleY(scale);
            applyHomeLayout(activity);
            if (home instanceof ViewGroup) applyAnimatedBackground(activity, home, accent);
        }

        View player = activity.findViewById(R.id.oryn_custom_player_image);
        if (player instanceof ImageView) {
            loadOptionalImage(activity, getPlayerUri(activity), bitmap -> {
                ((ImageView) player).setImageBitmap(bitmap);
                player.setVisibility(bitmap == null ? View.GONE : View.VISIBLE);
            });
        }
    }


    private static View findLauncherHome(View content) {
        if (content == null) return null;
        if (content.getId() == R.id.fragment_menu_main) return content;
        if (content instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) content;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findLauncherHome(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void applyView(View view, int accent) {
        if (view instanceof TextView && view.getId() != R.id.oryn_brand) {
            ((TextView) view).setTextColor(accent);
        }
        if (view instanceof Button || view instanceof com.kdt.mcgui.MineButton) {
            styleButton(view, accent);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) applyView(group.getChildAt(i), accent);
        }
    }

    public static void stylePreview(View view, Context c) { styleButton(view, getAccent(c)); }

    private static void styleButton(View view, int accent) {
        String style = getButtonStyle(view.getContext());
        GradientDrawable d = new GradientDrawable();
        if ("Glass".equals(style)) {
            d.setColor(Color.argb(45, Color.red(accent), Color.green(accent), Color.blue(accent)));
            d.setStroke(dp(view, 1), accent);
            d.setCornerRadius(dp(view, 18));
        } else if ("Outline".equals(style)) {
            d.setColor(Color.TRANSPARENT);
            d.setStroke(dp(view, 2), accent);
            d.setCornerRadius(dp(view, 18));
        } else if ("Square".equals(style)) {
            d.setColor(accent);
            d.setCornerRadius(dp(view, 5));
        } else {
            d.setColor(accent);
            d.setCornerRadius(dp(view, "Rounded".equals(style) ? 24 : 14));
        }
        view.setBackground(d);
    }

    private static void applyHomeLayout(Activity a) {
        View launch = a.findViewById(R.id.oryn_launch_group);
        View features = a.findViewById(R.id.oryn_feature_panel);
        if (launch == null) return;
        String layout = getHomeLayout(a);
        float factor = "Compact".equals(layout) ? .84f : ("Large".equals(layout) ? 1.10f : 1f);
        launch.setScaleX(factor); launch.setScaleY(factor);
        if (features != null) { features.setScaleX(factor); features.setScaleY(factor); }
    }

    private static void applyAnimatedBackground(Activity a, View home, int accent) {
        View old = (View) home.getTag(ANIMATION_TAG);
        if (old != null && old.getParent() instanceof ViewGroup) {
            ((ViewGroup) old.getParent()).removeView(old);
            old.animate().cancel();
        }
        if (!isAnimatedBackground(a) || !isGeneralAnimation(a)) return;
        final View overlay = new View(a);
        GradientDrawable glow = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(0, Color.red(accent), Color.green(accent), Color.blue(accent)),
                        Color.argb(45, Color.red(accent), Color.green(accent), Color.blue(accent)),
                        Color.argb(0, Color.red(accent), Color.green(accent), Color.blue(accent))});
        overlay.setBackground(glow);
        overlay.setClickable(false);
        overlay.setAlpha(0f);
        if (!(home instanceof ViewGroup)) return;
        ((ViewGroup) home).addView(overlay, 0, new ViewGroup.LayoutParams(-1, -1));
        home.setTag(ANIMATION_TAG, overlay);
        long duration = "Low".equals(getAnimationIntensity(a)) ? 4200L : ("High".equals(getAnimationIntensity(a)) ? 1800L : 2800L);
        overlay.animate().alpha(.9f).setDuration(duration).withEndAction(() ->
                overlay.animate().alpha(0f).setDuration(duration).withEndAction(() -> applyAnimatedBackground(a, home, accent)).start()
        ).start();
    }

    private static boolean isAnimationSafe(Context c) {
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) am.getMemoryInfo(info);
        return info.totalMem >= 3L * 1024L * 1024L * 1024L &&
                Runtime.getRuntime().availableProcessors() >= 4;
    }

    private static int dp(View v, int n) { return (int)(n * v.getResources().getDisplayMetrics().density + .5f); }

    private static void applyBackgroundAsync(final Context c, final View target) {
        final String uriString = getBackgroundUri(c);
        if (uriString.length() == 0) return;
        IMAGE_EXECUTOR.execute(() -> {
            Bitmap bitmap = decode(c, Uri.parse(uriString), 1920);
            MAIN.post(() -> {
                if (bitmap == null || target.getWindowToken() == null) return;
                BitmapDrawable drawable = new BitmapDrawable(c.getResources(), bitmap);
                String mode = getBackgroundMode(c);
                if ("Fit".equals(mode)) drawable.setGravity(android.view.Gravity.CENTER);
                else if ("Center".equals(mode)) drawable.setGravity(android.view.Gravity.CENTER);
                else drawable.setGravity(android.view.Gravity.FILL);
                drawable.setAlpha((int)(255f * getBackgroundOpacity(c) / 100f));
                target.setBackground(drawable);
            });
        });
    }

    private interface BitmapCallback { void onLoaded(Bitmap bitmap); }

    private static void loadOptionalImage(final Context c, final String uri, final BitmapCallback callback) {
        if (uri == null || uri.length() == 0) { callback.onLoaded(null); return; }
        IMAGE_EXECUTOR.execute(() -> {
            final Bitmap bitmap = decode(c, Uri.parse(uri), 1024);
            MAIN.post(() -> callback.onLoaded(bitmap));
        });
    }

    private static Bitmap decode(Context c, Uri uri, int maxSize) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = c.getContentResolver().openInputStream(uri)) {
            if (input == null) return null;
            BitmapFactory.decodeStream(input, null, bounds);
        } catch (Exception e) { return null; }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        while (largest / options.inSampleSize > maxSize) options.inSampleSize *= 2;
        try (InputStream input = c.getContentResolver().openInputStream(uri)) {
            return input == null ? null : BitmapFactory.decodeStream(input, null, options);
        } catch (Exception e) { return null; }
    }
}
