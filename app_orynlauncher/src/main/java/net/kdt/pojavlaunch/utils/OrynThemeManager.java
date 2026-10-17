package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import git.artdeell.mojo.R;

/**
 * Central monochrome styling for Oryn Launcher Plus.
 * Keeps the existing launch and instance button behavior while removing blue-only styling.
 */
public final class OrynThemeManager {
    private static final String PREF_KEY = "oryn_theme_color";
    private static final int DEFAULT_COLOR = Color.rgb(232, 232, 234);
    private static final String CUSTOM_PREFS = "oryn_customization";
    private static final String CUSTOM_ACCENT = "accent";
    private static final int BUTTON_COLOR = Color.rgb(226, 226, 229);
    private static final int BUTTON_TEXT_COLOR = Color.rgb(18, 18, 20);

    private OrynThemeManager() {}

    public static int getColor(Context context) {
        return DEFAULT_COLOR;
    }

    public static void stylePreferenceItem(Context context, View view) {
        if (view instanceof TextView) {
            ((TextView) view).setTextColor(getColor(context));
        }
    }

    public static void setColor(Context context, int color) {
        context.getSharedPreferences("oryn_theme", Context.MODE_PRIVATE)
                .edit().putInt(PREF_KEY, color).apply();
        context.getSharedPreferences(CUSTOM_PREFS, Context.MODE_PRIVATE)
                .edit().putInt(CUSTOM_ACCENT, color).apply();
        apply(context);
    }

    public static void apply(Context context) {
        if (!(context instanceof Activity)) return;
        Activity activity = (Activity) context;
        View root = activity.findViewById(android.R.id.content);
        if (root != null) applyView(root);
    }

    private static void applyView(View view) {
        int id = view.getId();
        if (id == R.id.play_button || view instanceof com.kdt.mcgui.MineButton) {
            setThemeButtonBackground(view);
            if (view instanceof TextView) {
                ((TextView) view).setTextColor(BUTTON_TEXT_COLOR);
            }
        } else if (view instanceof TextView) {
            ((TextView) view).setTextColor(DEFAULT_COLOR);
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyView(group.getChildAt(i));
            }
        }
    }

    private static void setThemeButtonBackground(View view) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(BUTTON_COLOR);
        drawable.setCornerRadius(16f * view.getResources().getDisplayMetrics().density);
        drawable.setStroke(
                Math.max(1, (int) (view.getResources().getDisplayMetrics().density)),
                Color.rgb(245, 245, 247));
        view.setBackground(drawable);
        view.setElevation(0f);
    }
}
