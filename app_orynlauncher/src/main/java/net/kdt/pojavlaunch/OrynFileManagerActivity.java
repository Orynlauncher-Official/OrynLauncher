package net.kdt.pojavlaunch;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import git.artdeell.mojo.R;

/**
 * Oryn's built-in instance file manager.
 * Designed to be usable from the launcher and from the in-game controls drawer.
 */
public class OrynFileManagerActivity extends AppCompatActivity {
    private LinearLayout list;
    private TextView pathView;
    private Button upButton;
    private Button pasteButton;
    private File currentDir;
    private File rootDir;
    private File pendingCopy;
    private boolean pendingCut;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        Instance instance = Instances.loadSelectedInstance();
        if (instance == null) {
            Toast.makeText(this, R.string.no_instance, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Start at OrynLauncher storage root, not the selected instance directory.
        // The old implementation opened the selected instance directly, which could
        // make OrynFiles appear to contain only .oryn when that instance had no other
        // top-level files. Users should be able to browse the complete launcher storage.
        rootDir = new File(Tools.DIR_GAME_HOME);
        if (!rootDir.exists() && !rootDir.mkdirs()) {
            Toast.makeText(this, "Unable to access OrynLauncher storage", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        currentDir = rootDir;
        setTitle("Oryn File Manager");
        getWindow().setStatusBarColor(Color.rgb(18, 18, 20));
        getWindow().setNavigationBarColor(Color.rgb(18, 18, 20));

        buildUi();
        refresh();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(10), dp(14), dp(10));
        root.setBackgroundColor(Color.rgb(16, 16, 18));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);

        upButton = actionButton("↑");
        upButton.setOnClickListener(v -> goUp());
        toolbar.addView(upButton, new LinearLayout.LayoutParams(dp(48), dp(44)));

        pathView = new TextView(this);
        pathView.setTextColor(Color.WHITE);
        pathView.setTextSize(15);
        pathView.setSingleLine(true);
        pathView.setGravity(Gravity.CENTER_VERTICAL);
        pathView.setPadding(dp(12), 0, dp(12), 0);
        toolbar.addView(pathView, new LinearLayout.LayoutParams(0, dp(44), 1));

        Button newFolder = actionButton("+ Folder");
        newFolder.setOnClickListener(v -> createFolder());
        toolbar.addView(newFolder, new LinearLayout.LayoutParams(dp(100), dp(44)));

        pasteButton = actionButton("Paste");
        pasteButton.setOnClickListener(v -> paste());
        toolbar.addView(pasteButton, new LinearLayout.LayoutParams(dp(90), dp(44)));

        root.addView(toolbar);

        TextView hint = new TextView(this);
        hint.setText("Tap a folder to open • Long-press a file/folder for actions");
        hint.setTextColor(Color.rgb(155, 155, 160));
        hint.setTextSize(12);
        hint.setPadding(dp(4), dp(5), 0, dp(8));
        root.addView(hint);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        androidx.core.widget.NestedScrollView scroll = new androidx.core.widget.NestedScrollView(this);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
    }

    private void refresh() {
        pathView.setText(currentDir.getAbsolutePath());
        upButton.setEnabled(!sameFile(currentDir, rootDir) && currentDir.getParentFile() != null);
        pasteButton.setEnabled(pendingCopy != null && pendingCopy.exists());

        list.removeAllViews();
        File[] children = currentDir.listFiles();
        if (children == null) {
            emptyMessage("Unable to read this directory.");
            return;
        }

        Arrays.sort(children, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        if (children.length == 0) {
            emptyMessage("This folder is empty.");
            return;
        }

        for (File file : children) addEntry(file);
    }

    private void addEntry(File file) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(7), dp(10), dp(7));
        row.setBackgroundColor(Color.rgb(28, 28, 31));

        TextView icon = new TextView(this);
        icon.setText(file.isDirectory() ? "📁" : "📄");
        icon.setTextSize(22);
        row.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(48)));

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);

        TextView name = new TextView(this);
        name.setText(file.getName());
        name.setTextColor(Color.WHITE);
        name.setTextSize(15);
        name.setSingleLine(true);
        textBox.addView(name);

        TextView meta = new TextView(this);
        meta.setText(file.isDirectory() ? "Folder" : formatSize(file.length()));
        meta.setTextColor(Color.rgb(145, 145, 150));
        meta.setTextSize(11);
        textBox.addView(meta);

        row.addView(textBox, new LinearLayout.LayoutParams(0, dp(48), 1));
        row.setOnClickListener(v -> {
            if (file.isDirectory()) {
                currentDir = file;
                refresh();
            } else {
                showActions(file);
            }
        });
        row.setOnLongClickListener(v -> {
            showActions(file);
            return true;
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(62));
        params.setMargins(0, 0, 0, dp(2));
        list.addView(row, params);
    }

    private void showActions(File file) {
        String[] actions = file.isDirectory()
                ? new String[]{"Open", "Copy", "Rename", "Delete"}
                : new String[]{"Copy", "Rename", "Delete"};

        new AlertDialog.Builder(this)
                .setTitle(file.getName())
                .setItems(actions, (dialog, which) -> {
                    String action = actions[which];
                    if ("Open".equals(action)) {
                        currentDir = file;
                        refresh();
                    } else if ("Copy".equals(action)) {
                        pendingCopy = file;
                        pendingCut = false;
                        Toast.makeText(this, "Copied to Oryn clipboard: " + file.getName(), Toast.LENGTH_SHORT).show();
                        refresh();
                    } else if ("Rename".equals(action)) {
                        rename(file);
                    } else if ("Delete".equals(action)) {
                        confirmDelete(file);
                    }
                })
                .show();
    }

    private void paste() {
        if (pendingCopy == null || !pendingCopy.exists()) {
            pendingCopy = null;
            refresh();
            return;
        }

        File target = new File(currentDir, pendingCopy.getName());
        if (sameFile(pendingCopy, target)) {
            Toast.makeText(this, "Already in this folder", Toast.LENGTH_SHORT).show();
            return;
        }
        if (pendingCopy.isDirectory() && isInside(currentDir, pendingCopy)) {
            Toast.makeText(this, "Cannot paste a folder inside itself", Toast.LENGTH_LONG).show();
            return;
        }

        if (target.exists()) {
            new AlertDialog.Builder(this)
                    .setTitle("Replace existing item?")
                    .setMessage(target.getName() + " already exists here.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Replace", (d, w) -> {
                        if (deleteRecursive(target)) performPaste(target);
                    })
                    .show();
            return;
        }
        performPaste(target);
    }

    private void performPaste(File target) {
        try {
            copyRecursive(pendingCopy, target);
            Toast.makeText(this, "Pasted " + target.getName(), Toast.LENGTH_SHORT).show();
            if (pendingCut) deleteRecursive(pendingCopy);
            pendingCopy = null;
            pendingCut = false;
            refresh();
        } catch (IOException e) {
            Toast.makeText(this, "Paste failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void copyRecursive(File source, File target) throws IOException {
        if (source.isDirectory()) {
            if (!target.exists() && !target.mkdirs()) throw new IOException("Could not create folder");
            File[] children = source.listFiles();
            if (children != null) {
                for (File child : children)
                    copyRecursive(child, new File(target, child.getName()));
            }
            return;
        }

        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs())
            throw new IOException("Could not create destination");

        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[1024 * 1024];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        }
    }

    private void rename(File file) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(file.getName());
        input.setSelectAllOnFocus(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);

        new AlertDialog.Builder(this)
                .setTitle("Rename")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Rename", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty() || name.contains("/") || name.contains("\\") ||
                            ".".equals(name) || "..".equals(name)) {
                        Toast.makeText(this, "Invalid name", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    File target = new File(file.getParentFile(), name);
                    if (target.exists()) {
                        Toast.makeText(this, "That name already exists", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!file.renameTo(target))
                        Toast.makeText(this, "Rename failed", Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .show();
    }

    private void createFolder() {
        EditText input = new EditText(this);
        input.setHint("Folder name");
        input.setSingleLine(true);

        new AlertDialog.Builder(this)
                .setTitle("New folder")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty() || name.contains("/") || name.contains("\\") ||
                            ".".equals(name) || "..".equals(name)) {
                        Toast.makeText(this, "Invalid folder name", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    File folder = new File(currentDir, name);
                    if (folder.exists() || !folder.mkdirs())
                        Toast.makeText(this, "Could not create folder", Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .show();
    }

    private void confirmDelete(File file) {
        new AlertDialog.Builder(this)
                .setTitle("Delete " + file.getName() + "?")
                .setMessage("This permanently deletes the selected item.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    if (!deleteRecursive(file))
                        Toast.makeText(this, "Delete failed", Toast.LENGTH_LONG).show();
                    refresh();
                })
                .show();
    }

    private boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) if (!deleteRecursive(child)) return false;
            }
        }
        return file.delete();
    }

    private void goUp() {
        if (sameFile(currentDir, rootDir)) return;
        File parent = currentDir.getParentFile();
        if (parent != null && (isInside(parent, rootDir) || sameFile(parent, rootDir))) {
            currentDir = parent;
            refresh();
        }
    }

    private boolean isInside(File child, File parent) {
        try {
            String childPath = child.getCanonicalPath();
            String parentPath = parent.getCanonicalPath();
            return childPath.equals(parentPath) || childPath.startsWith(parentPath + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    private boolean sameFile(File a, File b) {
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (IOException e) {
            return a.equals(b);
        }
    }

    private void emptyMessage(String message) {
        TextView empty = new TextView(this);
        empty.setText(message);
        empty.setTextColor(Color.rgb(150, 150, 155));
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(20), dp(40), dp(20), dp(40));
        list.addView(empty, new LinearLayout.LayoutParams(-1, dp(100)));
    }

    private Button actionButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setBackgroundColor(Color.rgb(42, 42, 46));
        return b;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024L * 1024L * 1024L) return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024d * 1024d));
        return String.format(java.util.Locale.US, "%.1f GB", bytes / (1024d * 1024d * 1024d));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
