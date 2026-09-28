package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.ImageButton;
import android.widget.ImageView;
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

    public static int getSurfaceColor(Context context) {
        return darken(getColor(context), 0.70f);
    }

    public static void stylePreferenceItem(Context context, View view) {
        setRoundedBackground(view, getSurfaceColor(context), 40);
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

        activity.getWindow().setStatusBarColor(darken(color, 0.62f));
        activity.getWindow().setNavigationBarColor(darken(color, 0.78f));

        View root = activity.findViewById(android.R.id.content);
        if (root != null) applyView(root, color);

        View fragment = activity.findViewById(R.id.fragment_menu_main);
        if (fragment != null) applyView(fragment, color);

        View settings = activity.findViewById(R.id.container_fragment);
        if (settings != null) settings.setBackgroundColor(darken(color, 0.84f));
    }

    private static void applyView(View view, int color) {
        int id = view.getId();

        if (view.getParent() instanceof RecyclerView) {
            stylePreferenceItem(view.getContext(), view);
        }

        if (id == R.id.fragment_menu_main || id == R.id.container_fragment) {
            view.setBackgroundColor(darken(color, 0.84f));
        } else if (id == R.id.oryn_sidebar) {
            setRoundedBackground(view, darken(color, 0.72f), 28);
        } else if (id == R.id.oryn_launch_group || id == R.id.oryn_bottom ||
                id == R.id.setting_button || id == R.id.account_spinner ||
                id == R.id.oryn_home) {
            setRoundedBackground(view, darken(color, 0.70f), 40);
        }

        if (id == R.id.oryn_brand) {
            ((TextView) view).setTextColor(color);
        } else if (id == R.id.play_button) {
            ((TextView) view).setTextColor(color);
        } else if (id == R.id.news_button) {
            ((TextView) view).setTextColor(color);
        }

        if (id == R.id.setting_button || id == R.id.oryn_home ||
                id == R.id.custom_control_button || id == R.id.open_files_button ||
                id == R.id.install_jar_button || id == R.id.edit_profile_button) {
            if (view instanceof ImageView) ((ImageView) view).setColorFilter(color);
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyView(group.getChildAt(i), color);
            }
        }
    }

    private static void setRoundedBackground(View view, int color, float radiusDp) {
        float density = view.getResources().getDisplayMetrics().density;
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusDp * density);
        view.setBackground(drawable);
    }

    private static int darken(int color, float amount) {
        return Color.rgb(
                (int) (Color.red(color) * amount),
                (int) (Color.green(color) * amount),
                (int) (Color.blue(color) * amount)
        );
    }
}