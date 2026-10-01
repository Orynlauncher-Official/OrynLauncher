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
    private Spinner profiles, models, skins, capes;
    private TextView status, selectedSkin, selectedCape, tabTitle;
    private Button skinTab, capeTab, applyButton, saveButton, removeButton, unequipButton;
    private OrynCosmeticsStore.CosmeticProfile active;
    private boolean capeTabSelected;
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
        b.setBackground(bg(CARD, 10, Color.rgb(42, 49, 62), 1));
        return b;
    }

    private void primary(Button b) {
        b.setBackground(bg(BLUE, 10, BLUE, 1));
        b.setTextColor(Color.WHITE);
    }

    private Spinner spinner() {
        Spinner s = new Spinner(this);
        s.setBackground(bg(Color.rgb(12, 15, 20), 9, Color.rgb(48, 56, 70), 1));
        s.setPadding(d(8), 0, d(8), 0);
        return s;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w == -1 ? -1 : d(w), h == -1 ? -1 : d(h));
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(d(14), d(12), d(14), d(12));
        root.setBackgroundColor(BG);

        // Left navigation
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setPadding(d(10), d(10), d(10), d(10));
        left.setBackground(bg(PANEL, 14, Color.rgb(29, 35, 45), 1));

        TextView title = text("ORYN", 22, WHITE);
        left.addView(title, lp(-1, 30));
        TextView sub = text("COSMETICS", 11, MUTED);
        left.addView(sub, lp(-1, 22));
        Space gap = new Space(this);
        left.addView(gap, lp(-1, 10));

        skinTab = button("▣   Skins");
        capeTab = button("▰   Capes");
        left.addView(skinTab, lp(-1, 48));
        left.addView(capeTab, lp(-1, 48));

        Space gap2 = new Space(this);
        left.addView(gap2, lp(-1, 12));
        Button importSkin = button("＋  Import Skin");
        Button importCape = button("＋  Import Cape");
        left.addView(importSkin, lp(-1, 46));
        left.addView(importCape, lp(-1, 46));

        Space push = new Space(this);
        left.addView(push, new LinearLayout.LayoutParams(1, 0, 1));
        Button back = button("‹  Back");
        left.addView(back, lp(-1, 44));
        root.addView(left, new LinearLayout.LayoutParams(d(190), -1));

        // Center 3D preview
        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setPadding(d(12), 0, d(12), 0);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        tabTitle = text("Skin Preview", 17, WHITE);
        head.addView(tabTitle, new LinearLayout.LayoutParams(0, d(36), 1));
        TextView hint = text("Drag to rotate  •  Classic / Slim", 11, MUTED);
        head.addView(hint, lp(-1, 42));
        center.addView(head, lp(-1, 42));

        preview = new OrynCosmeticPreviewView(this);
        preview.setBackground(bg(Color.rgb(11, 14, 19), 16, Color.rgb(31, 38, 49), 1));
        center.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));

        status = text("Ready", 12, MUTED);
        status.setGravity(Gravity.CENTER);
        center.addView(status, lp(-1, 34));
        root.addView(center, new LinearLayout.LayoutParams(0, -1, 1));

        // Right control panel
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(d(12), d(8), d(8), d(8));
        right.setClipChildren(false);
        right.setBackground(bg(PANEL, 14, Color.rgb(29, 35, 45), 1));

        right.addView(text("Cosmetic Profile", 18, WHITE), lp(-1, 28));
        profiles = spinner();
        right.addView(profiles, lp(-1, 42));

        Button newProfile = button("＋  New Profile");
        right.addView(newProfile, lp(-1, 36));

        right.addView(text("SKIN", 10, MUTED), lp(-1, 20));
        skins = spinner();
        right.addView(skins, lp(-1, 40));

        right.addView(text("CAPE", 10, MUTED), lp(-1, 20));
        capes = spinner();
        right.addView(capes, lp(-1, 40));

        right.addView(text("PLAYER MODEL", 10, MUTED), lp(-1, 20));
        models = spinner();
        right.addView(models, lp(-1, 40));

        selectedSkin = text("Skin: None", 11, MUTED);
        selectedCape = text("Cape: None", 11, MUTED);
        right.addView(selectedSkin, lp(-1, 20));
        right.addView(selectedCape, lp(-1, 20));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        applyButton = button("Apply Skin");
        saveButton = button("Save Skin");
        primary(applyButton);
        actions.addView(applyButton, new LinearLayout.LayoutParams(0, d(40), 1));
        actions.addView(saveButton, new LinearLayout.LayoutParams(0, d(40), 1));
        right.addView(actions, lp(-1, 40));

        LinearLayout actions2 = new LinearLayout(this);
        actions2.setOrientation(LinearLayout.HORIZONTAL);
        removeButton = button("Remove Skin");
        unequipButton = button("Unequip Cape");
        actions2.addView(removeButton, new LinearLayout.LayoutParams(0, d(36), 1));
        actions2.addView(unequipButton, new LinearLayout.LayoutParams(0, d(36), 1));
        right.addView(actions2, lp(-1, 36));

        TextView note = text("Local cosmetics work offline. Official capes are only shown when owned by the account.", 9, MUTED);
        note.setGravity(Gravity.BOTTOM);
        right.addView(note, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(right, new LinearLayout.LayoutParams(d(365), -1));

        setContentView(root);

        skinTab.setClickable(true);
        skinTab.setFocusable(true);
        capeTab.setClickable(true);
        capeTab.setFocusable(true);

        skinTab.setOnClickListener(v -> setTab(false));
        capeTab.setOnClickListener(v -> setTab(true));
        importSkin.setOnClickListener(v -> pick(PICK_SKIN));
        importCape.setOnClickListener(v -> pick(PICK_CAPE));
        back.setOnClickListener(v -> finish());

        newProfile.setOnClickListener(v -> {
            final EditText input = new EditText(this);
            input.setSingleLine(true);
            input.setHint("Profile name");
            input.setTextColor(WHITE);
            input.setHintTextColor(MUTED);
            new AlertDialog.Builder(this).setTitle("New Cosmetic Profile").setView(input)
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

        profiles.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> p) {}
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (refreshing) return;
                List<OrynCosmeticsStore.CosmeticProfile> ps = store.listProfiles();
                if (pos < ps.size()) {
                    active = ps.get(pos);
                    refresh();
                }
            }
        });

        models.setAdapter(adapter(new String[]{"Classic (Steve)", "Slim (Alex)"}));

        // Android Spinner popups are unreliable on some launcher/device combinations.
        // Use a touch-driven selection dialog so every cosmetic selector is always clickable.
        installSelectorTouch(models, () -> {
            final String[] values = {"Classic (Steve)", "Slim (Alex)"};
            showChoice("Player Model", values, models.getSelectedItemPosition(), pos -> {
                if (active == null) return;
                active.model = pos == 1 ? "slim" : "classic";
                models.setSelection(pos);
                refreshPreview();
                status.setText(pos == 1 ? "Slim (Alex) selected" : "Classic (Steve) selected");
            });
        });

        installSelectorTouch(profiles, () -> {
            List<OrynCosmeticsStore.CosmeticProfile> ps = store.listProfiles();
            String[] values = new String[ps.size()];
            for (int i = 0; i < ps.size(); i++) values[i] = ps.get(i).name;
            showChoice("Cosmetic Profile", values, Math.max(0, profiles.getSelectedItemPosition()), pos -> {
                if (pos < ps.size()) {
                    active = ps.get(pos);
                    refresh();
                    status.setText("Profile: " + active.name);
                }
            });
        });

        installSelectorTouch(skins, () -> {
            List<File> fs = store.listSkins();
            String[] values = new String[fs.size() + 1];
            values[0] = "None";
            for (int i = 0; i < fs.size(); i++) values[i + 1] = displayName(fs.get(i));
            showChoice("Skin", values, Math.max(0, skins.getSelectedItemPosition()), pos -> {
                if (active == null) return;
                active.skin = pos == 0 ? "" : fs.get(pos - 1).getName();
                skins.setSelection(pos);
                refreshPreview();
                status.setText(pos == 0 ? "Skin unequipped" : "Skin selected");
            });
        });

        installSelectorTouch(capes, () -> {
            List<File> fs = store.listCapes();
            String[] values = new String[fs.size() + 1];
            values[0] = "None";
            for (int i = 0; i < fs.size(); i++) values[i + 1] = displayName(fs.get(i));
            showChoice("Cape", values, Math.max(0, capes.getSelectedItemPosition()), pos -> {
                if (active == null) return;
                active.cape = pos == 0 ? "" : fs.get(pos - 1).getName();
                capes.setSelection(pos);
                refreshPreview();
                status.setText(pos == 0 ? "Cape unequipped" : "Cape selected");
            });
        });

        // Keep the legacy listeners for programmatic refresh/setSelection only.
        models.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> p) {}
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (active != null && !refreshing) {
                    active.model = pos == 1 ? "slim" : "classic";
                    refreshPreview();
                }
            }
        });

        skins.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> p) {}
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (active == null || refreshing) return;
                List<File> fs = store.listSkins();
                active.skin = pos == 0 ? "" : (pos - 1 < fs.size() ? fs.get(pos - 1).getName() : "");
                refreshPreview();
            }
        });

        capes.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> p) {}
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (active == null || refreshing) return;
                List<File> fs = store.listCapes();
                active.cape = pos == 0 ? "" : (pos - 1 < fs.size() ? fs.get(pos - 1).getName() : "");
                refreshPreview();
            }
        });

        applyButton.setOnClickListener(v -> {
            saveCurrent("Applied " + (capeTabSelected ? "cape" : "skin"));
        });
        saveButton.setOnClickListener(v -> saveCurrent(capeTabSelected ? "Cape saved" : "Skin saved"));
        removeButton.setOnClickListener(v -> removeSelected());
        unequipButton.setOnClickListener(v -> {
            active.cape = "";
            saveCurrent("Cape unequipped");
        });
    }

    private ArrayAdapter<String> adapter(String[] values) {
        return new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, values) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                v.setTextColor(WHITE);
                v.setTextSize(13);
                v.setGravity(Gravity.CENTER_VERTICAL);
                v.setPadding(d(10), 0, d(8), 0);
                return v;
            }
            @Override public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                TextView v = (TextView) super.getDropDownView(position, convertView, parent);
                v.setTextColor(WHITE);
                v.setTextSize(13);
                v.setPadding(d(12), d(8), d(8), d(8));
                v.setBackgroundColor(Color.rgb(18, 22, 29));
                return v;
            }
        };
    }

    private interface ChoiceAction { void run(int position); }

    private void installSelectorTouch(Spinner spinner, final Runnable opener) {
        spinner.setClickable(true);
        spinner.setFocusable(true);
        spinner.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                v.performClick();
                opener.run();
            }
            return true;
        });
        spinner.setOnClickListener(v -> {
            // Touch handler opens the dialog; this keeps accessibility/click semantics intact.
        });
    }

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

    private void setTab(boolean capesTab) {
        capeTabSelected = capesTab;

        // Make the left rail a real tab switch, not just a visual toggle.
        tabTitle.setText(capesTab ? "Cape Preview" : "Skin Preview");
        skinTab.setBackground(bg(capesTab ? CARD : BLUE, 10, Color.rgb(42,49,62), 1));
        capeTab.setBackground(bg(capesTab ? BLUE : CARD, 10, Color.rgb(42,49,62), 1));

        // Only show the selector relevant to the active tab.
        if (skins != null) skins.setVisibility(capesTab ? View.GONE : View.VISIBLE);
        if (capes != null) capes.setVisibility(capesTab ? View.VISIBLE : View.GONE);

        applyButton.setText(capesTab ? "Equip Cape" : "Apply Skin");
        saveButton.setText(capesTab ? "Save Cape" : "Save Skin");
        removeButton.setText(capesTab ? "Remove Cape" : "Remove Skin");
        unequipButton.setVisibility(capesTab ? View.VISIBLE : View.GONE);

        status.setText(capesTab
                ? "Capes • local and compatible"
                : "Skins • 64×64 compatible PNG");
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
        String[] pn = new String[ps.size()];
        int profilePos = 0;
        for (int i = 0; i < ps.size(); i++) {
            pn[i] = ps.get(i).name;
            if (pn[i].equals(active.name)) profilePos = i;
        }
        profiles.setAdapter(adapter(pn));
        profiles.setSelection(profilePos);

        models.setSelection("slim".equals(active.model) ? 1 : 0);

        List<File> sf = store.listSkins();
        String[] sn = new String[sf.size() + 1];
        sn[0] = "None";
        int skinPos = 0;
        for (int i = 0; i < sf.size(); i++) {
            sn[i + 1] = displayName(sf.get(i));
            if (sf.get(i).getName().equals(active.skin)) skinPos = i + 1;
        }
        skins.setAdapter(adapter(sn));
        skins.setSelection(skinPos);

        List<File> cf = store.listCapes();
        String[] cn = new String[cf.size() + 1];
        cn[0] = "None";
        int capePos = 0;
        for (int i = 0; i < cf.size(); i++) {
            cn[i + 1] = displayName(cf.get(i));
            if (cf.get(i).getName().equals(active.cape)) capePos = i + 1;
        }
        capes.setAdapter(adapter(cn));
        capes.setSelection(capePos);

        refreshing = false;
        refreshPreview();
        setTab(capeTabSelected);
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
        preview.setSkin(s, "slim".equals(active.model));
        preview.setCape(c);
        selectedSkin.setText("Skin  •  " + (active.skin.isEmpty() ? "None" : displayName(new File(active.skin))));
        selectedCape.setText("Cape  •  " + (active.cape.isEmpty() ? "None" : displayName(new File(active.cape))));
    }

    private void removeSelected() {
        if (capeTabSelected) {
            if (!active.cape.isEmpty()) {
                store.delete(new File(getFilesDir(), "cosmetics/capes/" + active.cape));
                active.cape = "";
                saveCurrent("Cape removed");
            }
        } else {
            if (!active.skin.isEmpty()) {
                store.delete(new File(getFilesDir(), "cosmetics/skins/" + active.skin));
                active.skin = "";
                saveCurrent("Skin removed");
            }
        }
    }

    private void saveCurrent(String message) {
        active.model = models.getSelectedItemPosition() == 1 ? "slim" : "classic";
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
            if (requestCode == PICK_SKIN) active.skin = f.getName();
            else active.cape = f.getName();
            saveCurrent("Imported " + displayName(f));
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            status.setText("Import failed");
        }
    }
}
