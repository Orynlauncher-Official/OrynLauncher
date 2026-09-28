package net.kdt.pojavlaunch.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import git.artdeell.mojo.R;

public final class OrynColorPickerDialog {
    private OrynColorPickerDialog() {}

    public static void show(Context context) {
        final int initial = OrynThemeManager.getColor(context);
        final int[] rgb = {Color.red(initial), Color.green(initial), Color.blue(initial)};

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 20);
        root.setPadding(pad, dp(context, 8), pad, pad);

        TextView preview = new TextView(context);
        preview.setGravity(android.view.Gravity.CENTER);
        preview.setText("LIVE PREVIEW");
        preview.setTextColor(Color.WHITE);
        preview.setTextSize(14);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, dp(context, 56));
        previewParams.bottomMargin = dp(context, 16);
        root.addView(preview, previewParams);

        EditText hex = new EditText(context);
        hex.setSingleLine(true);
        hex.setHint("#5096FF");
        hex.setInputType(InputType.TYPE_CLASS_TEXT);
        hex.setText(String.format("#%02X%02X%02X", rgb[0], rgb[1], rgb[2]));
        hex.setSelectAllOnFocus(true);
        root.addView(hex, new LinearLayout.LayoutParams(-1, dp(context, 52)));

        addChannel(context, root, "Red", rgb[0], rgb, 0, preview, hex);
        addChannel(context, root, "Green", rgb[1], rgb, 1, preview, hex);
        addChannel(context, root, "Blue", rgb[2], rgb, 2, preview, hex);

        TextView hint = new TextView(context);
        hint.setText("Changes are applied instantly across the launcher.");
        hint.setTextSize(12);
        hint.setTextColor(Color.GRAY);
        root.addView(hint);

        hex.setOnEditorActionListener((v, actionId, event) -> {
            applyHex(context, hex, rgb, preview);
            return false;
        });
        hex.setOnFocusChangeListener((v, focused) -> {
            if (!focused) applyHex(context, hex, rgb, preview);
        });

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Theme Color")
                .setView(root)
                .setPositiveButton("Done", null)
                .setNegativeButton("Reset", (d, w) -> {
                    OrynThemeManager.setColor(context, Color.rgb(80, 150, 255));
                })
                .create();
        dialog.setOnShowListener(d -> {
            if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> dialog.dismiss());
            }
        });
        dialog.show();
    }

    private static void addChannel(Context context, LinearLayout root, String label, int value,
                                   int[] rgb, int index, TextView preview, EditText hex) {
        TextView title = new TextView(context);
        title.setText(label);
        title.setTextSize(13);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(context, 28)));

        SeekBar bar = new SeekBar(context);
        bar.setMax(255);
        bar.setProgress(value);
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(context, 44)));

        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                rgb[index] = progress;
                int color = Color.rgb(rgb[0], rgb[1], rgb[2]);
                preview.setBackground(makePreview(color, context));
                hex.setText(String.format("#%02X%02X%02X", rgb[0], rgb[1], rgb[2]));
                OrynThemeManager.setColor(context, color);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        int color = Color.rgb(rgb[0], rgb[1], rgb[2]);
        preview.setBackground(makePreview(color, context));
    }

    private static void applyHex(Context context, EditText hex, int[] rgb, TextView preview) {
        try {
            String value = hex.getText().toString().trim();
            if (!value.startsWith("#")) value = "#" + value;
            int color = Color.parseColor(value);
            rgb[0] = Color.red(color);
            rgb[1] = Color.green(color);
            rgb[2] = Color.blue(color);
            hex.setText(String.format("#%02X%02X%02X", rgb[0], rgb[1], rgb[2]));
            preview.setBackground(makePreview(color, context));
            OrynThemeManager.setColor(context, color);
        } catch (IllegalArgumentException ignored) {
            hex.setText(String.format("#%02X%02X%02X", rgb[0], rgb[1], rgb[2]));
        }
    }

    private static GradientDrawable makePreview(int color, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, 18));
        return drawable;
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + .5f);
    }
}