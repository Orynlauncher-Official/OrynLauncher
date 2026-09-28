package net.kdt.pojavlaunch.fragments;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.kdt.mcgui.ProgressLayout;
import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;

/** Compact landscape account picker; the existing login fragments handle authentication. */
public final class CompactAccountDialog {
    private CompactAccountDialog() {}

    private static int dp(Activity activity, float value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + .5f);
    }

    private static GradientDrawable background(int color, int stroke, int radius, Activity activity) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(activity, radius));
        if (stroke != 0) shape.setStroke(dp(activity, 2), stroke);
        return shape;
    }

    public static void show(Activity activity) {
        Dialog dialog = new Dialog(activity);
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 22), dp(activity, 16), dp(activity, 22), dp(activity, 20));
        panel.setBackground(background(Color.rgb(49, 49, 51), Color.BLACK, 25, activity));

        TextView heading = new TextView(activity);
        heading.setText(R.string.main_add_account);
        heading.setTextColor(Color.rgb(108, 163, 234));
        heading.setTextSize(23);
        heading.setTypeface(null, Typeface.BOLD);
        panel.addView(heading);

        View rule = new View(activity);
        rule.setBackgroundColor(Color.BLACK);
        LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(-1, dp(activity, 2));
        ruleParams.topMargin = dp(activity, 10);
        ruleParams.bottomMargin = dp(activity, 13);
        panel.addView(rule, ruleParams);

        addOption(activity, dialog, panel, R.string.auth_select_microsoft,
                MicrosoftLoginFragment.class, MicrosoftLoginFragment.TAG);
        addOption(activity, dialog, panel, R.string.auth_select_elyby,
                ElyByLoginFragment.class, ElyByLoginFragment.TAG);
        addOption(activity, dialog, panel, R.string.auth_select_local,
                LocalLoginFragment.class, LocalLoginFragment.TAG);

        dialog.setContentView(panel);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = .5f;
            window.setAttributes(attrs);
            DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
            window.setLayout(Math.min(dp(activity, 460), (int)(metrics.widthPixels * .72f)),
                    WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }
        dialog.show();
        if (window != null) {
            DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
            window.setLayout(Math.min(dp(activity, 460), (int)(metrics.widthPixels * .72f)),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private static void addOption(Activity activity, Dialog dialog, LinearLayout panel,
                                  int title, Class<? extends Fragment> target, String tag) {
        TextView button = new TextView(activity);
        button.setText(title);
        button.setTextSize(14);
        button.setTypeface(null, Typeface.BOLD);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setBackground(background(Color.rgb(56, 56, 58), Color.BLACK, 22, activity));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(activity, 55));
        params.bottomMargin = dp(activity, 9);
        panel.addView(button, params);
        button.setOnClickListener(v -> {
            if (ProgressKeeper.hasProgressKey(ProgressLayout.AUTHENTICATE)) {
                Toast.makeText(activity, R.string.tasks_ongoing, Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            Tools.swapFragment(activity, target, tag, null);
        });
    }
}
