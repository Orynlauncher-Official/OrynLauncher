package net.kdt.pojavlaunch;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;\nimport android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import net.kdt.pojavlaunch.utils.OrynColorPickerDialog;
import net.kdt.pojavlaunch.utils.OrynCustomizationManager;

public class OrynCustomizationActivity extends AppCompatActivity {
    private static final int PICK_BACKGROUND = 4101;
    private static final int PICK_PLAYER = 4103;
    private LinearLayout root;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Full-screen customization editor: use the entire landscape display.
        getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );

        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        OrynCustomizationManager.apply(this);
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }

    private TextView heading(String text, int size) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(Color.WHITE);
        t.setPadding(dp(4), dp(10), dp(4), dp(6));
        return t;
    }

    private TextView action(String text) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), dp(10), dp(12), dp(10));
        OrynCustomizationManager.stylePreview(b, this);
        return b;
    }

    private void addRow(String label, View control) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(3), dp(8), dp(3));
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(Color.WHITE);
        l.setTextSize(14);
        row.addView(l, new LinearLayout.LayoutParams(0, dp(54), 1));
        row.addView(control, new LinearLayout.LayoutParams(dp(230), dp(54)));
        root.addView(row);
    }

    private Spinner spinner(String[] values, String current) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values);
        s.setAdapter(a);
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) { s.setSelection(i); break; }
        }
        return s;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(18), dp(28), dp(28));
        root.setBackgroundColor(Color.TRANSPARENT);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        setContentView(scroll, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        root.setBackgroundColor(OrynCustomizationManager.getBackgroundColor(this));

        root.addView(heading("Oryn Customization", 25));
        TextView sub = heading("Customize the OrynLauncher v3 experience.", 13);
        sub.setTextColor(Color.LTGRAY);
        root.addView(sub);

        root.addView(heading("Appearance", 18));

        Spinner theme = spinner(new String[]{"Oryn Dark","Oryn Black","Oryn Grey","Midnight","AMOLED","Custom"},
                OrynCustomizationManager.getTheme(this));
        addRow("Theme", theme);
        theme.setOnItemSelectedListener(select(v -> {
            OrynCustomizationManager.setTheme(this, v); applyNow();
        }));

        TextView accent = action("Choose Accent Color");
        addRow("Accent Color", accent);
        accent.setOnClickListener(v -> {
            OrynColorPickerDialog.show(this);
            applyNow();
        });

        Spinner bgMode = spinner(new String[]{"Crop","Fit","Center"}, OrynCustomizationManager.getBackgroundMode(this));
        addRow("Background Mode", bgMode);
        bgMode.setOnItemSelectedListener(select(v -> {
            OrynCustomizationManager.setBackgroundMode(this, v); applyNow();
        }));

        SeekBar opacity = new SeekBar(this);
        opacity.setMax(100);
        opacity.setProgress(OrynCustomizationManager.getBackgroundOpacity(this));
        addRow("Background Opacity", opacity);
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int p, boolean fromUser) {
                OrynCustomizationManager.setBackgroundOpacity(OrynCustomizationActivity.this, p);
                if (fromUser) applyNow();
            }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });

        TextView bg = action("Choose Background");
        addRow("Custom Background", bg);
        bg.setOnClickListener(v -> pick(PICK_BACKGROUND));

        TextView clearBg = action("Remove Background");
        addRow("", clearBg);
        clearBg.setOnClickListener(v -> {
            OrynCustomizationManager.setBackgroundUri(this, "");
            applyNow();
        });

        Spinner buttons = spinner(new String[]{"Default","Rounded","Square","Glass","Outline"},
                OrynCustomizationManager.getButtonStyle(this));
        addRow("Button Style", buttons);
        buttons.setOnItemSelectedListener(select(v -> {
            OrynCustomizationManager.setButtonStyle(this, v); applyNow();
        }));

        SeekBar scale = new SeekBar(this);
        scale.setMax(40);
        scale.setProgress(OrynCustomizationManager.getUiScale(this) - 80);
        addRow("UI Scale 80–120%", scale);
        scale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int p, boolean fromUser) {
                OrynCustomizationManager.setUiScale(OrynCustomizationActivity.this, p + 80);
                if (fromUser) applyNow();
            }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });

        root.addView(heading("Personalization", 18));

        TextView player = action("Choose Profile Image");
        addRow("Profile / Player Image", player);
        player.setOnClickListener(v -> pick(PICK_PLAYER));

        TextView removePlayer = action("Remove Profile Image");
        addRow("", removePlayer);
        removePlayer.setOnClickListener(v -> {
            OrynCustomizationManager.setPlayerUri(this, ""); applyNow();
        });

        Spinner home = spinner(new String[]{"Compact","Default","Large"}, OrynCustomizationManager.getHomeLayout(this));
        addRow("Home Layout", home);
        home.setOnItemSelectedListener(select(v -> {
            OrynCustomizationManager.setHomeLayout(this, v); applyNow();
        }));

        root.addView(heading("Effects", 18));

        Switch animated = new Switch(this);
        animated.setChecked(OrynCustomizationManager.isAnimatedBackground(this));
        addRow("Animated Background", animated);
        animated.setOnCheckedChangeListener((b, checked) -> {
            OrynCustomizationManager.setAnimatedBackground(this, checked); applyNow();
        });

        Spinner intensity = spinner(new String[]{"Low","Medium","High"}, OrynCustomizationManager.getAnimationIntensity(this));
        addRow("Animation Intensity", intensity);
        intensity.setOnItemSelectedListener(select(v -> OrynCustomizationManager.setAnimationIntensity(this, v)));

        Spinner page = spinner(new String[]{"None","Fade","Slide"}, OrynCustomizationManager.getPageAnimation(this));
        addRow("Page Transitions", page);
        page.setOnItemSelectedListener(select(v -> OrynCustomizationManager.setPageAnimation(this, v)));

        Spinner buttonAnimation = spinner(new String[]{"None","Scale","Ripple","Glow"},
                OrynCustomizationManager.getButtonAnimation(this));
        addRow("Button Animation", buttonAnimation);
        buttonAnimation.setOnItemSelectedListener(select(v -> OrynCustomizationManager.setButtonAnimation(this, v)));

        Switch general = new Switch(this);
        general.setChecked(OrynCustomizationManager.isGeneralAnimation(this));
        addRow("General Animation", general);
        general.setOnCheckedChangeListener((b, checked) ->
                OrynCustomizationManager.setGeneralAnimation(this, checked));

        root.addView(heading("Live Preview", 18));
        LinearLayout preview = new LinearLayout(this);
        preview.setGravity(Gravity.CENTER);
        preview.setPadding(dp(16), dp(12), dp(16), dp(12));
        preview.setBackgroundColor(Color.argb(55, 255, 255, 255));
        TextView sample = action("▶  Launch Preview");
        preview.addView(sample, new LinearLayout.LayoutParams(-1, dp(58)));
        root.addView(preview, new LinearLayout.LayoutParams(-1, dp(90)));

        TextView reset = action("Reset All Customization");
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(-1, dp(58));
        resetParams.topMargin = dp(18);
        root.addView(reset, resetParams);
        reset.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Reset Customization")
                .setMessage("Reset all OrynLauncher customization settings?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset", (d, w) -> {
                    OrynCustomizationManager.reset(this);
                    buildUi();
                }).show());
    }

    private void applyNow() {
        root.setBackgroundColor(OrynCustomizationManager.getBackgroundColor(this));
        OrynCustomizationManager.apply(this);
    }

    private void pick(int request) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, request);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(
                    uri, data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}
        if (request == PICK_BACKGROUND) OrynCustomizationManager.setBackgroundUri(this, uri.toString());
        else if (request == PICK_PLAYER) OrynCustomizationManager.setPlayerUri(this, uri.toString());
        applyNow();
    }

    private interface Selection { void accept(String value); }

    private AdapterView.OnItemSelectedListener select(Selection selection) {
        return new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) {}
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selection.accept(parent.getItemAtPosition(position).toString());
            }
        };
    }
}
