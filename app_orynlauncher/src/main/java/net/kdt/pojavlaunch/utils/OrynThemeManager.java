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

    private OrynThemeManager() {}

    public static int getColor(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("oryn_theme", Context.MODE_PRIVATE);
        return prefs.getInt(PREF_KEY, DEFAULT_COLOR);
    }

    // Kept for LauncherPreferenceFragment compatibility.
    // Theme changes must not replace preference/button backgrounds.
    public static void stylePreferenceItem(Context context, View view) {
        if (view instanceof TextView) {
            ((TextView) view).setTextColor(getColor(context));
        }
    }

    public static void setColor(Context context, int color) {
        context.getSharedPreferences("oryn_theme", Context.MODE_PRIVATE)
                .edit().putInt(PREF_KEY, color).apply();
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

        // Only the home-screen Launch button gets the selected theme background.
        if (id == R.id.play_button) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(color);
            drawable.setCornerRadius(40f * view.getResources().getDisplayMetrics().density);
            view.setBackground(drawable);
        }

        // Theme color changes text only everywhere else.
        if (view instanceof TextView) {
            ((TextView) view).setTextColor(color);
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyView(group.getChildAt(i), color);
            }
        }
    }
}
