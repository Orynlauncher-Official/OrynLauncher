package net.kdt.pojavlaunch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.cosmetics.OrynCosmeticPreviewView;
import net.kdt.pojavlaunch.cosmetics.OrynCosmeticsStore;
import net.kdt.pojavlaunch.instances.Instances;

import java.io.File;
import java.util.List;

public class OrynCosmeticsActivity extends Activity {
    private static final int PICK_SKIN = 501, PICK_CAPE = 502;
    private static final int BG = Color.rgb(8, 10, 14);
    private static final int PANEL = Color.rgb(16, 20, 27);
    private static final int CARD = Color.rgb(22, 27, 36);
    private static final int BLUE = Color.rgb(55, 125, 235);
    private static final int WHITE = Color.rgb(242, 245, 249);
    private static final int MUTED = Color.rgb(155, 165, 180);

    private OrynCosmeticsStore store;
    private OrynCosmeticPreviewView preview;
    private Button profiles, models, skins, capes;
    private TextView status, selectedSkin, selectedCape, tabTitle;
    private Button skinToggle, capeToggle, applyButton, saveButton, removeButton, unequipButton;
    private OrynCosmeticsStore.CosmeticProfile active;
    private boolean refreshing;

    @Override public void onCreate(@Nullable Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        store = new OrynCosmeticsStore(this);
        active = store.getActiveProfile();
        buildUi();
        refresh();
    }

    private int d(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView text(String value, float size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(size);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(d(12), d(4), d(12), d(4));
        return t;
    }

    private GradientDrawable bg(int color, int radius, int strokeColor, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(d(radius));
        if (stroke > 0) g.setStroke(d(stroke), strokeColor);
        return g;
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(WHITE);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(d(8), 0, d(8), 0);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setBackground(bg(CARD, 10, Color.rgb(48, 57, 72), 1));
        if (android.os.Build.VERSION.SDK_INT >= 21) b.setElevation(d(1));
        return b;
    }

    private void primary(Button b) {
        b.setBackground(bg(BLUE, 10, BLUE, 1));
        b.setTextColor(Color.WHITE);
    }

    private Button selectorButton(String value) {
        Button b = button(value);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setPadding(d(10), 0, d(10), 0);
        b.setBackground(bg(Color.rgb(12, 15, 20), 9, Color.rgb(55, 64, 80), 1));
        return b;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w == -1 ? -1 : d(w), h == -1 ? -1 : d(h));
    }

    private TextView sectionLabel(String value) {
        TextView t = text(value.toUpperCase(), 10, MUTED);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setLetterSpacing(0.08f);
        t.setPadding(d(4), d(6), d(4), d(4));
        return t;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(Color.rgb(39, 46, 58));
        return v;
    }

    private TextView badge(String value) {
        TextView t = text(value, 10, Color.rgb(170, 202, 255));
        t.setGravity(Gravity.CENTER);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setBackground(bg(Color.rgb(22, 48, 84), 20, Color.rgb(48, 91, 145), 1));
        return t;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(d(16), d(14), d(16), d(14));
        root.setBackgroundColor(BG);

        // ── Left navigation rail ─────────────────────────────────────────
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setPadding(d(12), d(12), d(12), d(12));
        left.setBackground(bg(PANEL, 18, Color.rgb(35, 42, 54), 1));
        if (android.os.Build.VERSION.SDK_INT >= 21) left.setElevation(d(3));

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setPadding(d(2), 0, d(2), 0);

        // Use the supplied Oryn Launcher wordmark instead of the temporary blue O.
        ImageView brandLogo = new ImageView(this);
        brandLogo.setImageResource(getResources().getIdentifier("oryn_cosmetics_logo", "drawable", getPackageName()));
        brandLogo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        brandLogo.setAdjustViewBounds(true);
        brand.addView(brandLogo, lp(-1, 44));
        left.addView(brand, lp(-1, 44));
        left.addView(new Space(this), lp(-1, 14));

        left.addView(sectionLabel("Collection"), lp(-1, 24));

        Button combinedNav = button("  Skin & Cape");
        combinedNav.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        primary(combinedNav);
        left.addView(combinedNav, lp(-1, 46));

        left.addView(new Space(this), lp(-1, 10));
        left.addView(sectionLabel("Import"), lp(-1, 24));

        Button importSkin = button("+   Import Skin");
        Button importCape = button("+   Import Cape");
        importSkin.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        importCape.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        left.addView(importSkin, lp(-1, 42));
        left.addView(importCape, lp(-1, 42));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(d(10), d(9), d(10), d(9));
        info.setBackground(bg(Color.rgb(12, 16, 22), 11, Color.rgb(35, 42, 53), 1));
        TextView infoTitle = text("Personal cosmetics", 11, WHITE);
        infoTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView infoText = text("Your imported cosmetics stay local and work offline.", 9, MUTED);
        infoText.setMaxLines(3);
        info.addView(infoTitle, lp(-1, 20));
        info.addView(infoText, lp(-1, 38));
        left.addView(new Space(this), lp(-1, 10));
        left.addView(info, lp(-1, 68));

        Button back = button("<   Back to Settings");
        back.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        left.addView(back, lp(-1, 42));

        ScrollView leftScroll = new ScrollView(this);
        leftScroll.setFillViewport(true);
        leftScroll.setClipToPadding(false);
        leftScroll.setVerticalScrollBarEnabled(false);
        leftScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        leftScroll.addView(left, new ScrollView.LayoutParams(-1, -2));
        root.addView(leftScroll, new LinearLayout.LayoutParams(d(220), -1));

        // ── Main preview stage ───────────────────────────────────────────
        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setPadding(d(14), 0, d(14), 0);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout headerText = new LinearLayout(this);
        headerText.setOrientation(LinearLayout.VERTICAL);
        tabTitle = text("Skin & Cape Preview", 20, WHITE);
        tabTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView subtitle = text("Configure your skin and cape together, with independent controls.", 10, MUTED);
        headerText.addView(tabTitle, lp(-1, 27));
        headerText.addView(subtitle, lp(-1, 20));
        header.addView(headerText, new LinearLayout.LayoutParams(0, d(48), 1));

        TextView live = badge("LIVE PREVIEW");
        header.addView(live, lp(92, 30));
        center.addView(header, lp(-1, 52));

        LinearLayout stage = new LinearLayout(this);
        stage.setOrientation(LinearLayout.VERTICAL);
        stage.setPadding(d(10), d(10), d(10), d(10));
        stage.setBackground(bg(Color.rgb(11, 14, 19), 18, Color.rgb(34, 41, 53), 1));
        if (android.os.Build.VERSION.SDK_INT >= 21) stage.setElevation(d(2));

        LinearLayout stageTop = new LinearLayout(this);
        stageTop.setGravity(Gravity.CENTER_VERTICAL);
        TextView stageTitle = text("CHARACTER", 9, MUTED);
        stageTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        stageTop.addView(stageTitle, new LinearLayout.LayoutParams(0, d(24), 1));
        TextView stageHint = text("Drag to rotate", 9, MUTED);
        stageHint.setGravity(Gravity.CENTER);
        stageTop.addView(stageHint, lp(90, 24));
        stage.addView(stageTop, lp(-1, 26));

        preview = new OrynCosmeticPreviewView(this);
        preview.setBackground(bg(Color.rgb(14, 18, 25), 14, Color.rgb(27, 34, 45), 1));
        stage.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout equipped = new LinearLayout(this);
        equipped.setGravity(Gravity.CENTER_VERTICAL);
        equipped.setPadding(d(10), 0, d(10), 0);
        equipped.setBackground(bg(Color.rgb(15, 19, 26), 10, Color.rgb(31, 38, 49), 1));
        TextView equippedTitle = text("EQUIPPED", 9, MUTED);
        equippedTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        equipped.addView(equippedTitle, lp(62, 34));
        selectedSkin = text("Skin  •  None", 10, WHITE);
        equipped.addView(selectedSkin, new LinearLayout.LayoutParams(0, d(34), 1));
        selectedCape = text("Cape  •  None", 10, MUTED);
        equipped.addView(selectedCape, new LinearLayout.LayoutParams(0, d(34), 1));
        stage.addView(equipped, lp(-1, 36));

        center.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));

        status = text("Ready", 11, MUTED);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(d(4), 0, d(4), 0);
        center.addView(status, lp(-1, 30));
        root.addView(center, new LinearLayout.LayoutParams(0, -1, 1));

        // ── Right configuration panel ────────────────────────────────────
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(d(14), d(12), d(12), d(12));
        right.setBackground(bg(PANEL, 18, Color.rgb(35, 42, 54), 1));
        if (android.os.Build.VERSION.SDK_INT >= 21) right.setElevation(d(3));

        LinearLayout rightHeader = new LinearLayout(this);
        rightHeader.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout rightTitleBox = new LinearLayout(this);
        rightTitleBox.setOrientation(LinearLayout.VERTICAL);
        TextView rt = text("Appearance", 17, WHITE);
        rt.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView rs = text("Manage profile, model and cosmetics", 9, MUTED);
        rightTitleBox.addView(rt, lp(-1, 23));
        rightTitleBox.addView(rs, lp(-1, 18));
        rightHeader.addView(rightTitleBox, new LinearLayout.LayoutParams(0, d(44), 1));
        TextView v3 = badge("V3");
        rightHeader.addView(v3, lp(48, 28));
        right.addView(rightHeader, lp(-1, 46));

        right.addView(divider(), lp(-1, 1));
        right.addView(sectionLabel("Profile"), lp(-1, 23));
        profiles = selectorButton("Default");
        right.addView(profiles, lp(-1, 40));
        Button newProfile = button("+   New Profile");
        newProfile.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        right.addView(newProfile, lp(-1, 34));

        right.addView(sectionLabel("Skin & Cape"), lp(-1, 24));
        skins = selectorButton("None");
        capes = selectorButton("None");
        right.addView(skins, lp(-1, 38));
        right.addView(capes, lp(-1, 38));
        LinearLayout toggles = new LinearLayout(this);
        toggles.setOrientation(LinearLayout.HORIZONTAL);
        skinToggle = button("Skin: OFF");
        capeToggle = button("Cape: OFF");
        toggles.addView(skinToggle, new LinearLayout.LayoutParams(0, d(36), 1));
        Space toggleGap = new Space(this);
        toggles.addView(toggleGap, lp(8, 1));
        toggles.addView(capeToggle, new LinearLayout.LayoutParams(0, d(36), 1));
        right.addView(toggles, lp(-1, 40));

        right.addView(sectionLabel("Player Model"), lp(-1, 24));
        models = selectorButton("Classic (Steve)");
        right.addView(models, lp(-1, 38));

        right.addView(new Space(this), new LinearLayout.LayoutParams(1, 0, 1));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, d(2), 0, d(2));
        applyButton = button("Apply Cosmetics");
        saveButton = button("Save Cosmetics");
        primary(applyButton);
        actions.addView(applyButton, new LinearLayout.LayoutParams(0, d(42), 1));
        Space actionGap = new Space(this);
        actions.addView(actionGap, lp(8, 1));
        actions.addView(saveButton, new LinearLayout.LayoutParams(0, d(42), 1));
        right.addView(actions, lp(-1, 46));

        LinearLayout actions2 = new LinearLayout(this);
        actions2.setOrientation(LinearLayout.HORIZONTAL);
        removeButton = button("Remove Skin");
        unequipButton = button("Remove Cape");
        actions2.addView(removeButton, new LinearLayout.LayoutParams(0, d(34), 1));
        Space actionGap2 = new Space(this);
        actions2.addView(actionGap2, lp(8, 1));
        actions2.addView(unequipButton, new LinearLayout.LayoutParams(0, d(34), 1));
        right.addView(actions2, lp(-1, 38));

        TextView note = text("PNG cosmetics are stored locally. Existing account, instances and game data are not changed.", 8, MUTED);
        note.setMaxLines(2);
        note.setGravity(Gravity.CENTER_VERTICAL);
        right.addView(note, lp(-1, 30));

        ScrollView rightScroll = new ScrollView(this);
        rightScroll.setFillViewport(true);
        rightScroll.setClipToPadding(false);
        rightScroll.setVerticalScrollBarEnabled(false);
        rightScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        rightScroll.addView(right, new ScrollView.LayoutParams(-1, -2));
        root.addView(rightScroll, new LinearLayout.LayoutParams(d(320), -1));

        setContentView(root);

        combinedNav.setOnClickListener(v -> status.setText("Skin & Cape • combined cosmetics profile"));
        importSkin.setOnClickListener(v -> pick(PICK_SKIN));
        importCape.setOnClickListener(v -> pick(PICK_CAPE));
        back.setOnClickListener(v -> finish());

        newProfile.setOnClickListener(v -> {
            final EditText input = new EditText(this);
            input.setSingleLine(true);
            input.setHint("Profile name");
            input.setTextColor(WHITE);
            input.setHintTextColor(MUTED);
            input.setPadding(d(10), 0, d(10), 0);
            input.setBackground(bg(Color.rgb(12, 15, 20), 9, Color.rgb(48, 56, 70), 1));
            new AlertDialog.Builder(this)
                    .setTitle("New Cosmetic Profile")
                    .setView(input)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Create", (dialog, which) -> {
                        String n = input.getText().toString().trim();
                        if (n.isEmpty()) n = "Custom";
                        OrynCosmeticsStore.CosmeticProfile p = new OrynCosmeticsStore.CosmeticProfile();
                        p.name = n;
                        try {
                            store.saveProfile(p);
                            active = p;
                            refresh();
                            status.setText("Profile created");
                        } catch (Exception e) {
                            status.setText("Could not create profile");
                        }
                    }).show();
        });

        profiles.setOnClickListener(v -> {
            List<OrynCosmeticsStore.CosmeticProfile> ps = store.listProfiles();
            String[] values = new String[ps.size()];
            int selected = 0;
            for (int i = 0; i < ps.size(); i++) {
                values[i] = ps.get(i).name;
                if (active != null && values[i].equals(active.name)) selected = i;
            }
            showChoice("Cosmetic Profile", values, selected, pos -> {
                if (pos < ps.size()) {
                    active = ps.get(pos);
                    try {
                        store.setActiveProfile(active);
                        refresh();
                        status.setText("Profile: " + active.name);
                    } catch (Exception e) {
                        status.setText("Could not save profile");
                    }
                }
            });
        });

        models.setOnClickListener(v -> {
            final String[] values = {"Classic (Steve)", "Slim (Alex)"};
            int selected = "slim".equals(active.model) ? 1 : 0;
            showChoice("Player Model", values, selected, pos -> {
                active.model = pos == 1 ? "slim" : "classic";
                refresh();
                status.setText(pos == 1 ? "Slim (Alex) selected" : "Classic (Steve) selected");
            });
        });

        skins.setOnClickListener(v -> {
            List<File> fs = store.listSkins();
            String[] values = new String[fs.size() + 1];
            values[0] = "None";
            int selected = 0;
            for (int i = 0; i < fs.size(); i++) {
                values[i + 1] = displayName(fs.get(i));
                if (fs.get(i).getName().equals(active.skin)) selected = i + 1;
            }
            showChoice("Skin", values, selected, pos -> {
                active.skin = pos == 0 ? "" : fs.get(pos - 1).getName();
                active.skinEnabled = pos != 0;
                refresh();
                status.setText(pos == 0 ? "Skin unequipped" : "Skin selected");
            });
        });

        capes.setOnClickListener(v -> {
            List<File> fs = store.listCapes();
            String[] values = new String[fs.size() + 1];
            values[0] = "None";
            int selected = 0;
            for (int i = 0; i < fs.size(); i++) {
                values[i + 1] = displayName(fs.get(i));
                if (fs.get(i).getName().equals(active.cape)) selected = i + 1;
            }
            showChoice("Cape", values, selected, pos -> {
                active.cape = pos == 0 ? "" : fs.get(pos - 1).getName();
                active.capeEnabled = pos != 0;
                refresh();
                status.setText(pos == 0 ? "Cape unequipped" : "Cape selected");
            });
        });

        skinToggle.setOnClickListener(v -> {
            active.skinEnabled = !active.skinEnabled;
            saveCurrent(active.skinEnabled ? "Skin enabled" : "Skin disabled");
        });
        capeToggle.setOnClickListener(v -> {
            active.capeEnabled = !active.capeEnabled;
            saveCurrent(active.capeEnabled ? "Cape enabled" : "Cape disabled");
        });
        applyButton.setOnClickListener(v -> saveCurrent("Cosmetics applied"));
        saveButton.setOnClickListener(v -> saveCurrent("Cosmetics saved"));
        removeButton.setOnClickListener(v -> removeSkin());
        unequipButton.setOnClickListener(v -> removeCape());
    }

    private interface ChoiceAction { void run(int position); }

    private void showChoice(String title, String[] values, int selected, ChoiceAction action) {
        if (values == null || values.length == 0) return;
        int safe = Math.max(0, Math.min(selected, values.length - 1));
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(values, safe, (dialog, which) -> {
                    action.run(which);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pick(int requestCode) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/png");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, requestCode);
    }

    private void refresh() {
        refreshing = true;
        List<OrynCosmeticsStore.CosmeticProfile> ps = store.listProfiles();

        profiles.setText(active == null ? "Default" : active.name);

        models.setText("slim".equals(active.model) ? "Slim (Alex)" : "Classic (Steve)");

        List<File> sf = store.listSkins();
        String skinLabel = "None";
        for (File f : sf) {
            if (f.getName().equals(active.skin)) {
                skinLabel = displayName(f);
                break;
            }
        }
        skins.setText(skinLabel);

        List<File> cf = store.listCapes();
        String capeLabel = "None";
        for (File f : cf) {
            if (f.getName().equals(active.cape)) {
                capeLabel = displayName(f);
                break;
            }
        }
        capes.setText(capeLabel);

        skinToggle.setText(active.skinEnabled ? "Skin: ON" : "Skin: OFF");
        capeToggle.setText(active.capeEnabled ? "Cape: ON" : "Cape: OFF");
        skinToggle.setEnabled(!active.skin.isEmpty());
        capeToggle.setEnabled(!active.cape.isEmpty());
        refreshing = false;
        refreshPreview();
    }

    private String displayName(File f) {
        String n = f.getName();
        if (n.toLowerCase().endsWith(".png")) n = n.substring(0, n.length() - 4);
        return n.replace('_', ' ');
    }

    private void refreshPreview() {
        if (active == null) return;
        Bitmap s = active.skin.isEmpty() ? null : store.load(new File(getFilesDir(), "cosmetics/skins/" + active.skin));
        Bitmap c = active.cape.isEmpty() ? null : store.load(new File(getFilesDir(), "cosmetics/capes/" + active.cape));
        preview.setSkin(active.skinEnabled ? s : null, "slim".equals(active.model));
        preview.setCape(active.capeEnabled ? c : null);
        preview.setCapePreview(active.capeEnabled);
        selectedSkin.setText("Skin  •  " + (active.skin.isEmpty() ? "None" : displayName(new File(active.skin))));
        selectedCape.setText("Cape  •  " + (active.cape.isEmpty() ? "None" : displayName(new File(active.cape))));
    }

    private void removeSkin() {
        if (active.skin.isEmpty()) { status.setText("No skin selected"); return; }
        try {
            store.removeCosmetic(active.skin, true);
            active.skin = "";
            active.skinEnabled = false;
            saveCurrent("Skin removed");
        } catch (Exception e) { status.setText("Could not remove skin"); }
    }

    private void removeCape() {
        if (active.cape.isEmpty()) { status.setText("No cape selected"); return; }
        try {
            store.removeCosmetic(active.cape, false);
            active.cape = "";
            active.capeEnabled = false;
            saveCurrent("Cape removed");
        } catch (Exception e) { status.setText("Could not remove cape"); }
    }

    private void saveCurrent(String message) {
        active.model = "Slim (Alex)".equals(models.getText().toString()) ? "slim" : "classic";
        try {
            store.setActiveProfile(active);
            syncInstance();
            refresh();
            status.setText("✓  " + message);
        } catch (Exception e) {
            status.setText("Save failed: " + e.getMessage());
        }
    }

    private void syncInstance() {
        try {
            net.kdt.pojavlaunch.instances.Instance i = Instances.loadSelectedInstance();
            if (i != null) store.writeActiveForInstance(i.getGameDirectory());
        } catch (Exception ignored) {}
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            File f = requestCode == PICK_SKIN ? store.importSkin(uri, "skin") : store.importCape(uri, "cape");
            if (requestCode == PICK_SKIN) { active.skin = f.getName(); active.skinEnabled = true; }
            else { active.cape = f.getName(); active.capeEnabled = true; }
            saveCurrent("Imported " + displayName(f));
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            status.setText("Import failed");
        }
    }
}
