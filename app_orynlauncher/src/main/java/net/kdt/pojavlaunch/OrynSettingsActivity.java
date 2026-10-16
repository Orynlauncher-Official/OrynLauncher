package net.kdt.pojavlaunch;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import git.artdeell.mojo.R;

/**
 * Oryn V4 settings UI.
 *
 * This is a visual shell around the existing launcher settings/features:
 * existing feature activities and preference storage remain unchanged.
 */
public class OrynSettingsActivity extends BaseActivity {
    private static final int BG = Color.rgb(5, 13, 25);
    private static final int PANEL = Color.rgb(9, 23, 40);
    private static final int PANEL_2 = Color.rgb(13, 31, 52);
    private static final int BORDER = Color.rgb(35, 103, 166);
    private static final int TEXT = Color.rgb(241, 249, 255);
    private static final int MUTED = Color.rgb(133, 170, 207);
    private static final int BLUE = Color.rgb(39, 143, 247);

    private SharedPreferences uiPrefs;
    private LinearLayout content;
    private LinearLayout sidebar;
    private TextView pageTitle;
    private TextView pageSubtitle;
    private Button activeNav;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        uiPrefs = getSharedPreferences("oryn_settings_ui", MODE_PRIVATE);
        build();
        showGeneral();
    }

    @Override
    public boolean setFullscreen() {
        return true;
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int screenW() {
        return getResources().getDisplayMetrics().widthPixels;
    }

    private int refPx(float fraction) {
        return Math.max(dp(1), (int) (screenW() * fraction));
    }

    // Translucent glass panels: let the snowy wallpaper remain visible behind controls.
    private GradientDrawable bg(int color, float radius) {
        int top = Color.argb(142,
                Math.min(255, Color.red(color) + 8),
                Math.min(255, Color.green(color) + 18),
                Math.min(255, Color.blue(color) + 28));
        int middle = Color.argb(112, Color.red(color), Color.green(color), Color.blue(color));
        int bottom = Color.argb(132,
                Math.max(0, Color.red(color) - 2),
                Math.max(0, Color.green(color) - 3),
                Math.max(0, Color.blue(color) - 5));
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] { top, middle, bottom });
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), Color.argb(205, Color.red(BORDER), Color.green(BORDER), Color.blue(BORDER)));
        return d;
    }

    private TextView text(String value, float size, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(16), 0, dp(12), 0);
        r.setBackground(bg(PANEL_2, 16));
        return r;
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int sideMargin = Math.max(dp(28), (int) (screenW() * 0.085f));
        root.setPadding(sideMargin, dp(20), sideMargin, dp(20));
        // Keep the snowy wallpaper in its own full-screen ImageView layer,
        // behind the settings UI, so child panels cannot accidentally hide it.
        root.setBackgroundColor(Color.TRANSPARENT);
        android.widget.FrameLayout screen = new android.widget.FrameLayout(this);
        ImageView wallpaper = new ImageView(this);
        wallpaper.setImageResource(R.drawable.oryn_settings_bg);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        screen.addView(wallpaper, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        screen.addView(root, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView gear = new ImageView(this);
        gear.setImageResource(R.drawable.oryn_nav_settings);
        gear.setPadding(dp(12), dp(12), dp(12), dp(12));
        gear.setBackground(bg(Color.rgb(15, 62, 99), 18));
        gear.setElevation(dp(5));
        header.addView(gear, new LinearLayout.LayoutParams(dp(54), dp(54)));

        LinearLayout headerText = new LinearLayout(this);
        headerText.setOrientation(LinearLayout.VERTICAL);
        pageTitle = text("Settings", 25, TEXT);
        pageTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        pageSubtitle = text("Customize your launcher experience", 12, MUTED);
        headerText.addView(pageTitle, new LinearLayout.LayoutParams(-1, dp(31)));
        headerText.addView(pageSubtitle, new LinearLayout.LayoutParams(-1, dp(23)));
        LinearLayout.LayoutParams ht = new LinearLayout.LayoutParams(0, dp(58), 1);
        ht.leftMargin = dp(12);
        header.addView(headerText, ht);

        root.addView(header, new LinearLayout.LayoutParams(-1, dp(72)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setGravity(Gravity.TOP);
        body.setPadding(0, dp(8), 0, 0);

        sidebar = new LinearLayout(this);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setPadding(dp(10), dp(10), dp(10), dp(10));
        sidebar.setBackground(bg(PANEL, 22));

        // Keep every settings category reachable on short screens.
        ScrollView sidebarScroll = new ScrollView(this);
        sidebarScroll.setFillViewport(true);
        sidebarScroll.setVerticalScrollBarEnabled(true);
        sidebarScroll.setClipToPadding(false);
        sidebarScroll.addView(sidebar, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(refPx(0.225f), -1);
        body.addView(sidebarScroll, sp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), 0, 0, dp(12));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -1, 1);
        cp.leftMargin = dp(18);
        body.addView(scroll, cp);

        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(screen);

        addNav("General", R.drawable.oryn_nav_settings, v -> showGeneral());
        addNav("Appearance", R.drawable.ic_px_image, v -> showAppearance());
        addNav("Game", R.drawable.ic_px_gamepad, v -> showGame());
        addNav("JVM", R.drawable.ic_px_sliders, v -> showJvm());
        addNav("Storage", R.drawable.ic_px_file, v -> showStorage());
        addNav("Privacy", R.drawable.ic_px_bell, v -> showPrivacy());
        addNav("Advanced", R.drawable.ic_px_sliders, v -> showAdvanced());
    }

    private Button textButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setTextColor(TEXT);
        b.setAllCaps(false);
        b.setBackground(bg(PANEL_2, 14));
        return b;
    }

    private void addNav(String label, int icon, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setTextColor(TEXT);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        Drawable navIcon = getDrawable(icon);
        if (navIcon != null) {
            int iconSize = dp(23);
            navIcon.setBounds(0, 0, iconSize, iconSize);
            b.setCompoundDrawables(navIcon, null, null, null);
        }
        b.setCompoundDrawablePadding(dp(12));
        b.setPadding(dp(14), 0, dp(8), 0);
        b.setTextSize(14);
        b.setSingleLine(true);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        b.setBackground(bg(PANEL, 14));
        b.setOnClickListener(v -> {
            if (activeNav != null) activeNav.setBackground(bg(PANEL, 14));
            activeNav = b;
            b.setBackground(bg(Color.rgb(31, 48, 71), 14));
            listener.onClick(v);
        });
        sidebar.addView(b, new LinearLayout.LayoutParams(-1, dp(58)));
        LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) b.getLayoutParams();
        p.bottomMargin = dp(4);
        b.setLayoutParams(p);
        if (activeNav == null && "General".equals(label)) {
            activeNav = b;
            b.setBackground(bg(Color.rgb(31, 48, 71), 14));
        }
    }

    private void clear(String title, String subtitle) {
        content.removeAllViews();
        pageTitle.setText(title);
        pageSubtitle.setText(subtitle);
    }

    private void section(String title, int icon) {
        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        ImageView i = new ImageView(this);
        i.setImageResource(icon);
        i.setScaleType(ImageView.ScaleType.FIT_CENTER);
        i.setPadding(dp(3), dp(3), dp(3), dp(3));
        h.addView(i, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView t = text(title, 17, TEXT);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, dp(42), 1);
        tp.leftMargin = dp(10);
        h.addView(t, tp);
        content.addView(h, new LinearLayout.LayoutParams(-1, dp(46)));
        LinearLayout.LayoutParams sp = (LinearLayout.LayoutParams) h.getLayoutParams();
        sp.topMargin = dp(4);
        h.setLayoutParams(sp);
    }

    private void addGap() {
        content.addView(new View(this), new LinearLayout.LayoutParams(1, dp(12)));
    }

    private void addSetting(String title, String subtitle, View control) {
        LinearLayout r = row();
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        TextView a = text(title, 14, TEXT);
        TextView b = text(subtitle, 11, MUTED);
        labels.addView(a, new LinearLayout.LayoutParams(-1, dp(20)));
        labels.addView(b, new LinearLayout.LayoutParams(-1, dp(18)));
        r.addView(labels, new LinearLayout.LayoutParams(0, dp(56), 1));
        r.addView(control, new LinearLayout.LayoutParams(refPx(0.115f), dp(42)));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(56));
        rp.bottomMargin = dp(4);
        content.addView(r, rp);
    }

    private Switch toggle(boolean checked, android.widget.CompoundButton.OnCheckedChangeListener l) {
        Switch s = new Switch(this);
        s.setChecked(checked);
        s.setOnCheckedChangeListener(l);
        return s;
    }

    private Spinner spinner(String[] values, int selected) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_dropdown_item, values) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                v.setTextColor(TEXT);
                v.setTextSize(13);
                v.setGravity(Gravity.CENTER_VERTICAL);
                v.setPadding(dp(14), 0, dp(10), 0);
                return v;
            }
        };
        s.setAdapter(a);
        s.setSelection(Math.max(0, Math.min(selected, values.length - 1)));
        s.setBackground(bg(PANEL_2, 14));
        return s;
    }

    private void showGeneral() {
        clear("Settings", "Customize your launcher experience");

        section("General", R.drawable.oryn_nav_settings);
        addSetting("Language", "Choose your preferred language",
                spinner(new String[]{"English"}, 0));
        addSetting("Auto Update", "Automatically check for updates",
                toggle(uiPrefs.getBoolean("auto_update", true), (b, checked) ->
                        uiPrefs.edit().putBoolean("auto_update", checked).apply()));
        addSetting("Compact Mode", "Use a smaller layout for better performance",
                toggle(uiPrefs.getBoolean("compact_mode", false), (b, checked) ->
                        uiPrefs.edit().putBoolean("compact_mode", checked).apply()));

        addGap();

        section("Game", R.drawable.ic_px_gamepad);
        addSetting("Animations", "Enable smooth UI animations",
                toggle(uiPrefs.getBoolean("animations", true), (b, checked) ->
                        uiPrefs.edit().putBoolean("animations", checked).apply()));
        addActivitySetting("Control Layout", "Customize your in-game touch controls",
                new Intent(this, CustomControlsActivity.class));
    }

    private void showAppearance() {
        clear("Appearance", "Customize the look and feel of OrynLauncher");
        section("Appearance", R.drawable.ic_px_image);
        String[] scaleValues = new String[]{"Small", "Default", "Large"};
        int savedScale = uiPrefs.getInt("ui_scale", 1);
        Spinner scale = spinner(scaleValues, savedScale);
        scale.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                uiPrefs.edit().putInt("ui_scale", position).apply();
                Toast.makeText(OrynSettingsActivity.this,
                        "UI scale saved. Reopen the launcher to apply it everywhere.", Toast.LENGTH_SHORT).show();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        addSetting("UI Scale", "Save Small, Default or Large preference", scale);
    }

    private void showGame() {
        clear("Game", "Minecraft and launcher performance");
        section("Game", R.drawable.ic_px_gamepad);
        Switch animations = toggle(uiPrefs.getBoolean("animations", true), (b,c) ->
                uiPrefs.edit().putBoolean("animations", c).apply());
        addSetting("Animations", "Enable smooth UI animations", animations);
        Button controls = textButton("Open Controls");
        controls.setOnClickListener(v -> startActivity(new Intent(this, CustomControlsActivity.class)));
        addSetting("Game Controls", "Configure the existing in-game control layout", controls);
    }

    private void showStorage() {
        clear("Storage", "Manage launcher files and downloads");
        section("Storage", R.drawable.ic_px_file);
        Button files = textButton("Open");
        files.setOnClickListener(v -> startActivity(new Intent(this, OrynFileManagerActivity.class)));
        addSetting("Oryn Files", "Browse and manage launcher files", files);
        Button downloads = textButton("Open");
        downloads.setOnClickListener(v -> startActivity(new Intent(this, OrynDownloadActivity.class)));
        addSetting("Oryn Downloads", "Mods, resource packs, shaders and modpacks", downloads);
        Button cosmetics = textButton("Open");
        cosmetics.setOnClickListener(v -> startActivity(new Intent(this, OrynCosmeticsActivity.class)));
        addSetting("Oryn Cosmetics", "Manage your existing Skin and Cape", cosmetics);
    }

    private void showPrivacy() {
        clear("Privacy", "Permissions and local launcher preferences");
        section("Privacy", R.drawable.ic_px_bell);
        Button notification = textButton("App Settings");
        notification.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(intent);
            } catch (Exception e) {
                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        });
        addSetting("Notifications", "Open Android notification permission settings", notification);
        Button appInfo = textButton("Open");
        appInfo.setOnClickListener(v -> {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });
        addSetting("App Permissions", "Review OrynLauncher permissions in Android settings", appInfo);
    }

    private void showAdvanced() {
        clear("Advanced", "Developer and OrynLauncher tools");
        section("Advanced", R.drawable.ic_px_sliders);
        addActivitySetting("Oryn Recorder", "Video quality, FPS and saved recordings",
                new Intent(this, OrynLegacySettingsActivity.class));
        addActivitySetting("Oryn Crash Viewer", "View and inspect Minecraft crash logs",
                new Intent(this, OrynCrashViewerActivity.class));
        addActivitySetting("Experimental Settings", "Open the existing experimental preferences",
                new Intent(this, OrynLegacySettingsActivity.class));
    }

    private void showJvm() {
        clear("JVM", "Java runtime and virtual machine settings");
        section("JVM", R.drawable.ic_px_sliders);
        Button open = textButton("Not ready");
        // Avoid routing through OrynLegacySettingsActivity: that compatibility activity redirects here.
        open.setOnClickListener(v -> Toast.makeText(this,
                "The separate JVM editor is not wired up in this branch yet.", Toast.LENGTH_LONG).show());
        addSetting("JVM editor", "A dedicated JVM editor still needs implementation", open);
    }

    private void addActivitySetting(String title, String subtitle, Intent intent) {
        Button b = textButton(intent == null ? "Open" : "Open");
        if (intent != null) b.setOnClickListener(v -> startActivity(intent));
        else b.setOnClickListener(v -> Toast.makeText(this, "Use the existing launcher preference screen for this option.", Toast.LENGTH_SHORT).show());
        addSetting(title, subtitle, b);
    }
}
