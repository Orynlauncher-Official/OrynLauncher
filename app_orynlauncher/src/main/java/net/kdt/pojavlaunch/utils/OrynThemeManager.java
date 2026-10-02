package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import git.artdeell.mojo.R;

public final class OrynThemeManager {
    private static final String PREF_KEY = "oryn_theme_color";
    private static final int DEFAULT_COLOR = Color.rgb(80, 150, 255);
    private static final String CUSTOM_PREFS = "oryn_customization";
    private static final String CUSTOM_ACCENT = "accent";

    private OrynThemeManager() {}

    public static int getColor(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("oryn_theme", Context.MODE_PRIVATE);
        SharedPreferences custom = context.getSharedPreferences(CUSTOM_PREFS, Context.MODE_PRIVATE);
        return custom.getInt(CUSTOM_ACCENT, prefs.getInt(PREF_KEY, DEFAULT_COLOR));
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
        int color = getColor(context);

        View root = activity.findViewById(android.R.id.content);
        if (root != null) applyView(root, color);
    }

    private static void applyView(View view, int color) {
        int id = view.getId();

        // V4 has its own fixed blue visual language from the reference design.
        // Do not let the legacy/custom accent preference recolor the V4 home screen.
        if (id == R.id.fragment_menu_main || id == R.id.oryn_brand || id == R.id.account_spinner) {
            if (id == R.id.fragment_menu_main) return;
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    applyView(group.getChildAt(i), color);
                }
            }
            return;
        }

        // Home screen: only the Launch button background changes.
        if (id == R.id.play_button) {
            setThemeButtonBackground(view, color);
            if (view instanceof TextView) {
                ((TextView) view).setTextColor(Color.WHITE);
            }
        }
        // Instance creation buttons: use the exact same theme color as Launch.
        else if (view instanceof com.kdt.mcgui.MineButton) {
            setThemeButtonBackground(view, color);
            if (view instanceof TextView) {
                ((TextView) view).setTextColor(Color.WHITE);
            }
        }
        // Everything else: text color only. Existing backgrounds stay untouched.
        else if (view instanceof TextView) {
            ((TextView) view).setTextColor(color);
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyView(group.getChildAt(i), color);
            }
        }
    }

    private static void setThemeButtonBackground(View view, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(40f * view.getResources().getDisplayMetrics().density);
        view.setBackground(drawable);
    }
}
