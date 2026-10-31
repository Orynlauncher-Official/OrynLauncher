package net.kdt.pojavlaunch.profiles;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListAdapter;
import android.widget.TextView;

import net.kdt.pojavlaunch.JVersionList;
import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.FilteredSubList;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class VersionListAdapter extends BaseExpandableListAdapter implements ExpandableListAdapter {

    private final LayoutInflater mLayoutInflater;
    private final String[] mGroups;
    private final String[] mInstalledVersions;
    private List<?>[] mData;
    private List<?>[] mOriginalData;
    private final boolean mHideCustomVersions;
    private final int mSnapshotListPosition;

    public VersionListAdapter(JVersionList.Version[] versionList, boolean hideCustomVersions, Context ctx) {
        mHideCustomVersions = hideCustomVersions;
        mLayoutInflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        List<JVersionList.Version> releaseList = new FilteredSubList<>(versionList, item -> item.type.equals("release"));
        List<JVersionList.Version> snapshotList = new FilteredSubList<>(versionList, item -> item.type.equals("snapshot"));
        List<JVersionList.Version> betaList = new FilteredSubList<>(versionList, item -> item.type.equals("old_beta"));
        List<JVersionList.Version> alphaList = new FilteredSubList<>(versionList, item -> item.type.equals("old_alpha"));

        mInstalledVersions = new File(Tools.DIR_GAME_NEW + "/versions").list();
        if (mInstalledVersions != null) Arrays.sort(mInstalledVersions);

        if (!areInstalledVersionsAvailable()) {
            mGroups = new String[]{
                    ctx.getString(R.string.mcl_setting_veroption_release),
                    ctx.getString(R.string.mcl_setting_veroption_snapshot),
                    ctx.getString(R.string.mcl_setting_veroption_oldbeta),
                    ctx.getString(R.string.mcl_setting_veroption_oldalpha)
            };
            mData = new List[]{releaseList, snapshotList, betaList, alphaList};
            mSnapshotListPosition = 1;
        } else {
            mGroups = new String[]{
                    ctx.getString(R.string.mcl_setting_veroption_installed),
                    ctx.getString(R.string.mcl_setting_veroption_release),
                    ctx.getString(R.string.mcl_setting_veroption_snapshot),
                    ctx.getString(R.string.mcl_setting_veroption_oldbeta),
                    ctx.getString(R.string.mcl_setting_veroption_oldalpha)
            };
            mData = new List[]{Arrays.asList(mInstalledVersions), releaseList, snapshotList, betaList, alphaList};
            mSnapshotListPosition = 2;
        }
        mOriginalData = mData.clone();
    }

    public void applyFilter(String query) {
        String filter = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (filter.isEmpty()) {
            mData = mOriginalData.clone();
        } else {
            List<?>[] filtered = new List<?>[mOriginalData.length];
            for (int group = 0; group < mOriginalData.length; group++) {
                List<Object> matches = new ArrayList<>();
                for (Object item : mOriginalData[group]) {
                    String label = item instanceof JVersionList.Version
                            ? ((JVersionList.Version) item).id : String.valueOf(item);
                    if (label.toLowerCase(Locale.ROOT).contains(filter)) matches.add(item);
                }
                filtered[group] = matches;
            }
            mData = filtered;
        }
        notifyDataSetChanged();
    }

    @Override public int getGroupCount() { return mGroups.length; }
    @Override public int getChildrenCount(int groupPosition) { return mData[groupPosition].size(); }
    @Override public Object getGroup(int groupPosition) { return mData[groupPosition]; }

    @Override
    public String getChild(int groupPosition, int childPosition) {
        Object item = mData[groupPosition].get(childPosition);
        return item instanceof JVersionList.Version ? ((JVersionList.Version) item).id : String.valueOf(item);
    }

    @Override public long getGroupId(int groupPosition) { return groupPosition; }
    @Override public long getChildId(int groupPosition, int childPosition) { return childPosition; }
    @Override public boolean hasStableIds() { return true; }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        if (convertView == null)
            convertView = mLayoutInflater.inflate(android.R.layout.simple_expandable_list_item_1, parent, false);
        TextView label = (TextView) convertView;
        label.setText(mGroups[groupPosition]);
        label.setTextColor(0xFFF1F4F7);
        label.setTextSize(13);
        label.setPadding(14, 10, 10, 10);
        label.setBackgroundColor(isExpanded ? 0xFF26343D : 0xFF1B222A);
        return convertView;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        if (convertView == null)
            convertView = mLayoutInflater.inflate(android.R.layout.simple_expandable_list_item_1, parent, false);
        TextView label = (TextView) convertView;
        label.setText(getChild(groupPosition, childPosition));
        label.setTextColor(0xFFD5DDE4);
        label.setTextSize(12);
        label.setPadding(28, 9, 10, 9);
        label.setBackgroundColor(0xFF171B21);
        return convertView;
    }

    @Override public boolean isChildSelectable(int groupPosition, int childPosition) { return true; }
    public boolean isSnapshotSelected(int groupPosition) { return groupPosition == mSnapshotListPosition; }

    private boolean areInstalledVersionsAvailable() {
        return !mHideCustomVersions && mInstalledVersions != null && mInstalledVersions.length > 0;
    }
}
