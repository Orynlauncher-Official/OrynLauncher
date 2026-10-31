package net.kdt.pojavlaunch.profiles;

import static net.kdt.pojavlaunch.extra.ExtraCore.getValue;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ExpandableListView;

import androidx.appcompat.app.AlertDialog;

import net.kdt.pojavlaunch.JVersionList;
import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.extra.ExtraConstants;

public class VersionSelectorDialog {
    public static void open(Context context, boolean hideCustomVersions, VersionSelectorListener listener) {
        View content = LayoutInflater.from(context).inflate(R.layout.dialog_oryn_version_selector, null);
        ExpandableListView versionList = content.findViewById(R.id.oryn_version_list);
        EditText search = content.findViewById(R.id.oryn_version_search);
        Button returnButton = content.findViewById(R.id.oryn_version_return);

        JVersionList jVersionList = (JVersionList) getValue(ExtraConstants.RELEASE_TABLE);
        JVersionList.Version[] versions = (jVersionList == null || jVersionList.versions == null)
                ? new JVersionList.Version[0] : jVersionList.versions;
        VersionListAdapter adapter = new VersionListAdapter(versions, hideCustomVersions, context);
        versionList.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(content)
                .create();

        versionList.setOnChildClickListener((parent, view, groupPosition, childPosition, id) -> {
            String selectedVersion = adapter.getChild(groupPosition, childPosition);
            listener.onVersionSelected(selectedVersion, adapter.isSnapshotSelected(groupPosition));
            dialog.dismiss();
            return true;
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.applyFilter(s == null ? "" : s.toString());
                for (int i = 0; i < adapter.getGroupCount(); i++) {
                    if (adapter.getChildrenCount(i) > 0) versionList.expandGroup(i);
                    else versionList.collapseGroup(i);
                }
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        returnButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }
}
