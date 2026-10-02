package net.kdt.pojavlaunch;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.OrynThemeManager;

/**
 * Oryn V4 settings UI.
 *
 * This is a visual shell around the existing launcher settings/features:
 * existing feature activities and preference storage remain unchanged.
 */
public class OrynSettingsActivity extends BaseActivity {
    private static final int BG = Color.rgb(5, 12, 20);
    private static final int PANEL = Color.rgb(11, 20, 31);
    private static final int PANEL_2 = Color.rgb(14, 25, 39);
    private static final int BORDER = Color.rgb(25, 40, 59);
    private static final int TEXT = Color.rgb(235, 241, 250);
    private static final int MUTED = Color.rgb(126, 148, 178);
    private static final int BLUE = Color.rgb(35, 145, 255);

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

    private GradientDrawable bg(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), BORDER);
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
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView gear = new ImageView(this);
        gear.setImageResource(R.drawable.oryn_nav_settings);
        gear.setPadding(dp(2), dp(2), dp(2), dp(2));
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
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(refPx(0.195f), -1);
        body.addView(sidebar, sp);

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
        setContentView(root);

        addNav("General", R.drawable.oryn_nav_settings, v -> showGeneral());
        addNav("Appearance", R.drawable.ic_px_image, v -> showAppearance());
        addNav("Game", R.drawable.ic_px_gamepad, v -> showGame());
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
        b.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
        b.setCompoundDrawablePadding(dp(16));
        b.setPadding(dp(18), 0, dp(10), 0);
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
        i.setPadding(dp(2), dp(2), dp(2), dp(2));
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

        section("Appearance", R.drawable.ic_px_image);
        addSetting("Theme", "Choose your preferred theme",
                spinner(new String[]{"Dark", "Oryn Blue", "Oryn Purple", "AMOLED"}, 0));
        addSetting("UI Scale", "Adjust the interface size",
                spinner(new String[]{"Small", "Default", "Large"}, 1));

        addGap();

        section("Game", R.drawable.ic_px_gamepad);
        addSetting("Max FPS (Launcher)", "Limit the launcher framerate (0 = Uncapped)",
                spinner(new String[]{"30 FPS", "60 FPS", "90 FPS", "120 FPS", "Uncapped"}, 1));
        addSetting("Animations", "Enable smooth UI animations",
                toggle(uiPrefs.getBoolean("animations", true), (b, checked) ->
                        uiPrefs.edit().putBoolean("animations", checked).apply()));
        addActivitySetting("Control Layout", "Customize your in-game touch controls",
                new Intent(this, CustomControlsActivity.class));
    }

    private void showAppearance() {
        clear("Appearance", "Customize the look and feel of OrynLauncher");
        section("Appearance", R.drawable.ic_px_image);
        Spinner theme = spinner(new String[]{"Dark", "Oryn Blue", "Oryn Purple", "AMOLED"}, 0);
        theme.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                if (pos == 1) OrynThemeManager.setColor(OrynSettingsActivity.this, Color.rgb(35,145,255));
                if (pos == 2) OrynThemeManager.setColor(OrynSettingsActivity.this, Color.rgb(150,90,255));
                if (pos == 3) OrynThemeManager.setColor(OrynSettingsActivity.this, Color.WHITE);
            }
            public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        addSetting("Theme", "Choose your preferred theme", theme);
        Spinner scale = spinner(new String[]{"Small", "Default", "Large"}, 1);
        addSetting("UI Scale", "Adjust the interface size", scale);
        Button customization = textButton("Customize");
        customization.setOnClickListener(v -> startActivity(new Intent(this, OrynCustomizationActivity.class)));
        addSetting("Oryn Customization", "Backgrounds, colors, buttons and animations", customization);
    }

    private void showGame() {
        clear("Game", "Minecraft and launcher performance");
        section("Game", R.drawable.ic_px_gamepad);
        Spinner fps = spinner(new String[]{"30 FPS", "60 FPS", "90 FPS", "120 FPS", "Uncapped"}, 1);
        addSetting("Max FPS (Launcher)", "Limit the launcher framerate (0 = Uncapped)", fps);
        Switch animations = toggle(uiPrefs.getBoolean("animations", true), (b,c) ->
                uiPrefs.edit().putBoolean("animations", c).apply());
        addSetting("Animations", "Enable smooth UI animations", animations);
        Switch boost = toggle(LauncherPreferences.PREF_ORYN_FPS_BOOST, (b,c) -> {
            LauncherPreferences.PREF_ORYN_FPS_BOOST = c;
            LauncherPreferences.DEFAULT_PREF.edit().putBoolean("orynFpsBoost", c).apply();
        });
        addSetting("Oryn FPS Boost", "Apply real Minecraft rendering optimizations before launch", boost);
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
        Button notification = textButton("Request");
        notification.setOnClickListener(v -> Toast.makeText(this, "Notification permission can be requested from the launcher.", Toast.LENGTH_SHORT).show());
        addSetting("Notifications", "Control notification permission for the launcher", notification);
        Button reset = textButton("Keep Data");
        reset.setOnClickListener(v -> Toast.makeText(this, "No account, instance or world data is removed here.", Toast.LENGTH_SHORT).show());
        addSetting("Data", "Launcher settings are stored locally on this device", reset);
    }

    private void showAdvanced() {
        clear("Advanced", "Developer and OrynLauncher tools");
        section("Advanced", R.drawable.ic_px_sliders);
        addActivitySetting("Oryn Recorder", "Video quality, FPS and saved recordings",
                new Intent(this, OrynLegacySettingsActivity.class));
        addActivitySetting("Oryn Crash Viewer", "View and inspect Minecraft crash logs",
                new Intent(this, OrynCrashViewerActivity.class));
        addActivitySetting("Oryn Customization", "Open the full Oryn customization center",
                new Intent(this, OrynCustomizationActivity.class));
        addActivitySetting("Java / Runtime Settings", "Open the existing launcher runtime preferences",
                new Intent(this, OrynLegacySettingsActivity.class));
        addActivitySetting("Experimental Settings", "Open the existing experimental preferences",
                new Intent(this, OrynLegacySettingsActivity.class));
    }

    private void addActivitySetting(String title, String subtitle, Intent intent) {
        Button b = textButton(intent == null ? "Open" : "Open");
        if (intent != null) b.setOnClickListener(v -> startActivity(intent));
        else b.setOnClickListener(v -> Toast.makeText(this, "Use the existing launcher preference screen for this option.", Toast.LENGTH_SHORT).show());
        addSetting(title, subtitle, b);
    }
}
