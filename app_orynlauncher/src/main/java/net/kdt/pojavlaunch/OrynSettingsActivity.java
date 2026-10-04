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
import android.widget.AdapterView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.multirt.MultiRTConfigDialog;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.utils.OrynThemeManager;
import net.kdt.pojavlaunch.utils.OrynFpsBoostUtils;

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
    private View activeNav;
    private MultiRTConfigDialog runtimeDialog;
    private final androidx.activity.result.ActivityResultLauncher<Object> runtimeInstallLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("xz"), data -> {
                if (data != null) Tools.installRuntimeFromUri(this, data);
            });

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
        int sideMargin = Math.max(dp(34), (int) (screenW() * 0.085f));
        root.setPadding(sideMargin, dp(18), sideMargin, dp(18));
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView gear = new ImageView(this);
        gear.setImageResource(R.drawable.oryn_nav_settings);
        gear.setPadding(dp(3), dp(3), dp(3), dp(3));
        header.addView(gear, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout headerText = new LinearLayout(this);
        headerText.setOrientation(LinearLayout.VERTICAL);
        pageTitle = text("Settings", 26, TEXT);
        pageTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        pageSubtitle = text("Customize your launcher experience", 12, MUTED);
        headerText.addView(pageTitle, new LinearLayout.LayoutParams(-1, dp(30)));
        headerText.addView(pageSubtitle, new LinearLayout.LayoutParams(-1, dp(20)));
        LinearLayout.LayoutParams ht = new LinearLayout.LayoutParams(0, dp(54), 1);
        ht.leftMargin = dp(12);
        header.addView(headerText, ht);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(66)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setGravity(Gravity.TOP);

        sidebar = new LinearLayout(this);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setPadding(dp(8), dp(8), dp(8), dp(8));
        sidebar.setBackground(bg(Color.rgb(8, 16, 27), 18));
        body.addView(sidebar, new LinearLayout.LayoutParams(refPx(0.195f), -1));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), 0, 0, dp(12));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -1, 1);
        body.addView(scroll, cp);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        addNav("General", R.drawable.oryn_nav_settings, v -> showGeneral());
        addNav("Appearance", R.drawable.ic_px_image, v -> showAppearance());
        addNav("Game", R.drawable.ic_px_gamepad, v -> showGame());
        addNav("Video", R.drawable.ic_px_image, v -> showVideo());
        addNav("Renderer", R.drawable.ic_px_image_renderer, v -> showRenderer());
        addNav("Storage", R.drawable.ic_px_file, v -> showStorage());
        addNav("Privacy", R.drawable.ic_px_bell, v -> showPrivacy());
        addNav("Java", R.drawable.ic_px_runtime_mgr, v -> showJava());
        addNav("Advanced", R.drawable.ic_px_sliders, v -> showAdvanced());
    }

    private void addNav(String label, int icon, View.OnClickListener listener) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(12), 0, dp(10), 0);

        ImageView image = new ImageView(this);
        image.setImageResource(icon);
        image.setPadding(dp(3), dp(3), dp(3), dp(3));
        item.addView(image, new LinearLayout.LayoutParams(dp(34), dp(34)));

        TextView title = text(label, 14, TEXT);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, dp(48), 1);
        tp.leftMargin = dp(10);
        item.addView(title, tp);

        item.setBackground(bg(Color.TRANSPARENT, 12));
        item.setOnClickListener(v -> {
            if (activeNav != null) activeNav.setBackground(bg(Color.TRANSPARENT, 12));
            activeNav = item;
            item.setBackground(bg(Color.rgb(31, 48, 71), 12));
            listener.onClick(v);
        });

        sidebar.addView(item, new LinearLayout.LayoutParams(-1, dp(52)));
        LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) item.getLayoutParams();
        p.bottomMargin = dp(3);
        item.setLayoutParams(p);

        if (activeNav == null && "General".equals(label)) {
            activeNav = item;
            item.setBackground(bg(Color.rgb(31, 48, 71), 12));
        }
    }

    private void clear(String title, String subtitle) {
        content.removeAllViews();
        // Keep the page header identical to the reference design.
        pageTitle.setText("Settings");
        pageSubtitle.setText("Customize your launcher experience");
    }

    private LinearLayout sectionCard(String title, int icon) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(bg(Color.rgb(7, 15, 25), 14));
        card.setPadding(dp(12), dp(8), dp(12), dp(8));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView i = new ImageView(this);
        i.setImageResource(icon);
        i.setPadding(dp(3), dp(3), dp(3), dp(3));
        header.addView(i, new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView t = text(title, 16, TEXT);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, dp(34), 1);
        tp.leftMargin = dp(8);
        header.addView(t, tp);
        card.addView(header, new LinearLayout.LayoutParams(-1, dp(40)));
        content.addView(card, new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    private void addGap() {
        content.addView(new View(this), new LinearLayout.LayoutParams(1, dp(14)));
    }

    private void addSettingToCard(LinearLayout card, String title, String subtitle, View control) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(10), 0, dp(8), 0);
        r.setBackground(bg(Color.rgb(9, 18, 29), 10));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        TextView a = text(title, 13, TEXT);
        TextView b = text(subtitle, 10, MUTED);
        labels.addView(a, new LinearLayout.LayoutParams(-1, dp(19)));
        labels.addView(b, new LinearLayout.LayoutParams(-1, dp(16)));
        r.addView(labels, new LinearLayout.LayoutParams(0, dp(50), 1));
        r.addView(control, new LinearLayout.LayoutParams(refPx(0.12f), dp(40)));

        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(52));
        rp.bottomMargin = dp(3);
        card.addView(r, rp);
    }

    private void section(String title, int icon) {
        sectionCard(title, icon);
    }

    private void addSetting(String title, String subtitle, View control) {
        LinearLayout card = (LinearLayout) content.getChildAt(content.getChildCount() - 1);
        if (card != null) addSettingToCard(card, title, subtitle, control);
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
        Switch animations = toggle(uiPrefs.getBoolean("animations", true), (b, checked) ->
                uiPrefs.edit().putBoolean("animations", checked).apply());
        addSetting("Animations", "Enable smooth UI animations", animations);
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
        clear("Game", "Minecraft performance and controls");
        section("Game", R.drawable.ic_px_gamepad);
        // Do not impose an artificial launcher FPS cap here.
        // The launcher UI should follow the device display refresh rate.
        Switch animations = toggle(uiPrefs.getBoolean("animations", true), (b,c) ->
                uiPrefs.edit().putBoolean("animations", c).apply());
        addSetting("Animations", "Enable smooth UI animations", animations);
        Switch boost = toggle(LauncherPreferences.PREF_ORYN_FPS_BOOST, (b,c) ->
                OrynFpsBoostUtils.setEnabled(c));
        addSetting("Oryn FPS Boost", "75% render scale + lowest Minecraft graphics + reduced render/simulation workload", boost);
        Button controls = textButton("Open Controls");
        controls.setOnClickListener(v -> startActivity(new Intent(this, CustomControlsActivity.class)));
        addSetting("Game Controls", "Configure the existing in-game control layout", controls);
    }

    private void showVideo() {
        clear("Video", "Minecraft graphics and display settings");
        section("Video", R.drawable.ic_px_image);

        int scale = Math.max(25, Math.min(100, (int) (LauncherPreferences.PREF_SCALE_FACTOR * 100)));
        Spinner resolution = spinner(new String[]{"25%", "50%", "75%", "100%"}, scale >= 90 ? 3 : scale >= 62 ? 2 : scale >= 37 ? 1 : 0);
        resolution.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int value = position == 0 ? 25 : position == 1 ? 50 : position == 2 ? 75 : 100;
                LauncherPreferences.PREF_SCALE_FACTOR = value / 100f;
                LauncherPreferences.DEFAULT_PREF.edit().putInt("resolutionRatio", value).apply();
            }
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        addSetting("Resolution Scale", "Change the Minecraft render resolution", resolution);

        Switch notch = toggle(LauncherPreferences.PREF_IGNORE_NOTCH, (b, checked) ->
                LauncherPreferences.DEFAULT_PREF.edit().putBoolean("ignoreNotch", checked).apply());
        addSetting("Ignore Display Cutout", "Use the full screen area when supported", notch);

        Switch surface = toggle(LauncherPreferences.PREF_USE_ALTERNATE_SURFACE, (b, checked) ->
                LauncherPreferences.DEFAULT_PREF.edit().putBoolean("alternate_surface", checked).apply());
        addSetting("Alternate Surface", "Use the alternate Android game surface", surface);

        Switch vsync = toggle(LauncherPreferences.PREF_FORCE_VSYNC, (b, checked) ->
                LauncherPreferences.DEFAULT_PREF.edit().putBoolean("force_vsync", checked).apply());
        addSetting("Force VSync", "Synchronize rendering to the display", vsync);

        Switch sustained = toggle(LauncherPreferences.PREF_SUSTAINED_PERFORMANCE, (b, checked) ->
                LauncherPreferences.DEFAULT_PREF.edit().putBoolean("sustainedPerformance", checked).apply());
        addSetting("Sustained Performance", "Request sustained device performance mode", sustained);
    }

    private void showRenderer() {
        clear("Renderer", "Choose the Minecraft graphics backend");
        section("Renderer", R.drawable.ic_px_image_renderer);

        net.kdt.pojavlaunch.game.renderer.RendererCache cache =
                net.kdt.pojavlaunch.game.renderer.RendererCache.getCompatibleRenderers(this);

        String[] names = cache.rendererDisplayNames;
        int selected = 0;
        String current = LauncherPreferences.PREF_RENDERER;
        for (int i = 0; i < cache.rendererIds.size(); i++) {
            if (cache.rendererIds.get(i).equals(current)) {
                selected = i;
                break;
            }
        }

        Spinner renderer = spinner(names, selected);
        renderer.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < cache.rendererIds.size()) {
                    LauncherPreferences.PREF_RENDERER = cache.rendererIds.get(position);
                    LauncherPreferences.DEFAULT_PREF.edit()
                            .putString("renderer", cache.rendererIds.get(position))
                            .apply();
                }
            }
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        addSetting("Global Renderer", "Select the graphics backend used by Minecraft", renderer);

        Switch angle = toggle(LauncherPreferences.PREF_USE_ANGLE, (b, checked) ->
                LauncherPreferences.DEFAULT_PREF.edit().putBoolean("use_angle", checked).apply());
        addSetting("ANGLE", "Use ANGLE when an ANGLE provider is available", angle);

        Switch zink = toggle(LauncherPreferences.PREF_ZINK_PREFER_SYSTEM_DRIVER, (b, checked) ->
                LauncherPreferences.DEFAULT_PREF.edit().putBoolean("zinkPreferSystemDriver", checked).apply());
        addSetting("Vulkan Driver", "Prefer the system Vulkan driver when supported", zink);
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

    private void showJava() {
        clear("Java Runtime", "Manage the Java runtimes used by Minecraft");
        section("Java Runtime", R.drawable.ic_px_runtime_mgr);

        String current = LauncherPreferences.PREF_DEFAULT_RUNTIME;
        if (current == null || current.trim().isEmpty()) current = "Automatic / recommended";
        addSetting("Current Runtime", current, textButton("Manage"));
        LinearLayout card = (LinearLayout) content.getChildAt(content.getChildCount() - 1);
        if (card != null) {
            View row = card.getChildAt(card.getChildCount() - 1);
            if (row instanceof LinearLayout) {
                View control = ((LinearLayout) row).getChildAt(1);
                if (control != null) control.setOnClickListener(v -> openRuntimeManager());
            }
        }

        Button manage = textButton("Open Runtime Manager");
        manage.setOnClickListener(v -> openRuntimeManager());
        addSetting("Installed Runtimes", "Install, remove, or choose the default Java runtime", manage);

        StringBuilder installed = new StringBuilder();
        try {
            for (net.kdt.pojavlaunch.multirt.Runtime rt : MultiRTUtils.getRuntimes()) {
                if (installed.length() > 0) installed.append(" • ");
                installed.append(rt.name.replace(".tar.xz", ""));
            }
        } catch (Throwable ignored) {}
        if (installed.length() == 0) installed.append("No managed runtimes detected");
        addSetting("Detected Runtimes", installed.toString(), textButton("Refresh"));
    }

    private void openRuntimeManager() {
        if (runtimeDialog == null) {
            runtimeDialog = new MultiRTConfigDialog();
            runtimeDialog.prepare(this, runtimeInstallLauncher);
        }
        runtimeDialog.show();
    }

    private void showAdvanced() {
        clear("Advanced", "Developer and OrynLauncher tools");
        section("Advanced", R.drawable.ic_px_sliders);
        addActivitySetting("Oryn Crash Viewer", "View and inspect Minecraft crash logs",
                new Intent(this, OrynCrashViewerActivity.class));
        addActivitySetting("Oryn Customization", "Open the full Oryn customization center",
                new Intent(this, OrynCustomizationActivity.class));
        Button runtime = textButton("Open");
        runtime.setOnClickListener(v -> openRuntimeManager());
        addSetting("Java / Runtime", "Manage installed Java runtimes and choose the default", runtime);
        addActivitySetting("Experimental Settings", "Open the existing experimental preferences",
                new Intent(this, OrynLegacySettingsActivity.class));
    }

    private Spinner spinner(String[] values, int selected) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_dropdown_item, values);
        s.setAdapter(adapter);
        if (selected >= 0 && selected < values.length) s.setSelection(selected);
        s.setBackground(bg(PANEL_2, 12));
        s.setPadding(dp(8), 0, dp(8), 0);
        return s;
    }

    private Switch toggle(boolean checked, android.widget.CompoundButton.OnCheckedChangeListener listener) {
        Switch s = new Switch(this);
        s.setChecked(checked);
        s.setText("");
        s.setGravity(Gravity.CENTER);
        s.setButtonTintList(null);
        s.setOnCheckedChangeListener(listener);
        return s;
    }

    private Button textButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(TEXT);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setBackground(bg(BLUE, 12));
        return b;
    }

    private void addActivitySetting(String title, String subtitle, Intent intent) {
        Button b = textButton(intent == null ? "Open" : "Open");
        if (intent != null) b.setOnClickListener(v -> startActivity(intent));
        else b.setOnClickListener(v -> Toast.makeText(this, "Use the existing launcher preference screen for this option.", Toast.LENGTH_SHORT).show());
        addSetting(title, subtitle, b);
    }
}
