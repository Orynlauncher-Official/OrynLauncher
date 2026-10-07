package net.kdt.pojavlaunch.profiles;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
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
import java.util.Arrays;
import java.util.List;

public class VersionListAdapter extends BaseExpandableListAdapter implements ExpandableListAdapter {
    
    private final LayoutInflater mLayoutInflater;

    private final String[] mGroups;
    private final String[] mInstalledVersions;
    private final List<?>[] mData;
    private final boolean mHideCustomVersions;
    private final int mSnapshotListPosition;

    public VersionListAdapter(JVersionList.Version[] versionList, boolean hideCustomVersions, Context ctx){
        mHideCustomVersions = hideCustomVersions;
        mLayoutInflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        List<JVersionList.Version> releaseList = new FilteredSubList<>(versionList, item -> item.type.equals("release"));
        List<JVersionList.Version> snapshotList = new FilteredSubList<>(versionList, item -> item.type.equals("snapshot"));
        List<JVersionList.Version> betaList = new FilteredSubList<>(versionList, item -> item.type.equals("old_beta"));
        List<JVersionList.Version> alphaList = new FilteredSubList<>(versionList, item -> item.type.equals("old_alpha"));

        // Query installed versions
        mInstalledVersions = new File(Tools.DIR_GAME_NEW + "/versions").list();
        if(mInstalledVersions != null)
            Arrays.sort(mInstalledVersions);

        if(!areInstalledVersionsAvailable()){
            mGroups = new String[]{
                    ctx.getString(R.string.mcl_setting_veroption_release),
                    ctx.getString(R.string.mcl_setting_veroption_snapshot),
                    ctx.getString(R.string.mcl_setting_veroption_oldbeta),
                    ctx.getString(R.string.mcl_setting_veroption_oldalpha)
            };
            mData = new List[]{ releaseList, snapshotList, betaList, alphaList};
            mSnapshotListPosition = 1;
        }else{
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
    }

    @Override
    public int getGroupCount() {
        return mGroups.length;
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        return mData[groupPosition].size();
    }

    @Override
    public Object getGroup(int groupPosition) {
        return mData[groupPosition];
    }

    @Override
    public String getChild(int groupPosition, int childPosition) {
        if(isInstalledVersionSelected(groupPosition)){
            return mInstalledVersions[childPosition];
        }
        return ((JVersionList.Version)mData[groupPosition].get(childPosition)).id;
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        TextView view = createRow(convertView, parent);
        view.setText(mGroups[groupPosition]);
        view.setTextColor(Color.WHITE);
        view.setTextSize(16);
        view.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        view.setGravity(Gravity.CENTER);
        view.setBackgroundColor(Color.TRANSPARENT);
        view.setPadding(dp(parent.getContext(), 8), dp(parent.getContext(), 10),
                dp(parent.getContext(), 8), dp(parent.getContext(), 10));
        view.setMinHeight(dp(parent.getContext(), 56));
        return view;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        TextView view = createRow(convertView, parent);
        view.setText(getChild(groupPosition, childPosition));
        view.setTextColor(Color.WHITE);
        view.setTextSize(14);
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        view.setPadding(dp(parent.getContext(), 24), dp(parent.getContext(), 10),
                dp(parent.getContext(), 16), dp(parent.getContext(), 10));
        view.setMinHeight(dp(parent.getContext(), 52));
        view.setBackgroundColor(Color.rgb(28, 28, 28));
        return view;
    }

    private TextView createRow(View convertView, ViewGroup parent) {
        if (convertView instanceof TextView) {
            return (TextView) convertView;
        }
        TextView view = new TextView(parent.getContext());
        view.setLayoutParams(new ExpandableListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        view.setIncludeFontPadding(false);
        view.setSingleLine(true);
        return view;
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }

    public boolean isSnapshotSelected(int groupPosition) {
        return groupPosition == mSnapshotListPosition;
    }

    private boolean areInstalledVersionsAvailable(){
        if(mHideCustomVersions) return false;
        return !(mInstalledVersions == null || mInstalledVersions.length == 0);
    }

    private boolean isInstalledVersionSelected(int groupPosition){
        return groupPosition == 0 && areInstalledVersionsAvailable();
    }
}
