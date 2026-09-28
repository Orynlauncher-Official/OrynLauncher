package net.kdt.pojavlaunch.prefs.screens;


import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import net.kdt.pojavlaunch.LauncherActivity;
import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

/**
 * Preference for the main screen, any sub-screen should inherit this class for consistent behavior,
 * overriding only onCreatePreferences
 */
public class LauncherPreferenceFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
    protected Runnable mVisibilityUpdater = () -> {};

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setBackgroundColor(Color.rgb(20, 24, 30));
        if (getListView() != null) {
            RecyclerView list = getListView();
            list.setClipToPadding(false);
            list.setPadding(dp(132), dp(108), dp(20), dp(22));
            list.setBackgroundColor(Color.rgb(20, 24, 30));
            list.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
                @Override public void onChildViewAttachedToWindow(@NonNull View child) {
                    int position = list.getChildAdapterPosition(child);
                    if (position == RecyclerView.NO_POSITION) return;
                    GradientDrawable bg = new GradientDrawable();
                    bg.setColor(Color.rgb(26, 31, 38));
                    bg.setCornerRadius(dp(19));
                    bg.setStroke(dp(1), Color.rgb(52, 59, 69));
                    child.setBackground(bg);
                    child.setMinimumHeight(dp(position == 1 ? 76 : 74));
                    RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) child.getLayoutParams();
                    lp.bottomMargin = dp(9);
                    child.setLayoutParams(lp);
                }
                @Override public void onChildViewDetachedFromWindow(@NonNull View child) {}
            });
            ViewGroup parent = (ViewGroup) list.getParent();
            if (parent instanceof android.widget.FrameLayout) {
                android.widget.FrameLayout frame = (android.widget.FrameLayout) parent;
                LinearLayout header = new LinearLayout(requireContext());
                header.setOrientation(LinearLayout.VERTICAL);
                header.setPadding(dp(22), dp(12), 0, 0);
                TextView title = new TextView(requireContext());
                title.setText("◉    Oryn Settings");
                title.setTextSize(23);
                title.setTextColor(Color.WHITE);
                header.addView(title);
                TextView subtitle = new TextView(requireContext());
                subtitle.setText("Oryn Launcher V2  •  Monochrome Theme");
                subtitle.setTextSize(12);
                subtitle.setTextColor(Color.rgb(146, 152, 163));
                header.addView(subtitle);
                frame.addView(header, new android.widget.FrameLayout.LayoutParams(-1, dp(90)));
                TextView sidebar = new TextView(requireContext());
                sidebar.setText("━\n◇\n⚑\n♜\n▣\n┃\n\n◉");
                sidebar.setTextSize(27);
                sidebar.setGravity(android.view.Gravity.CENTER);
                sidebar.setTextColor(Color.rgb(184, 193, 207));
                GradientDrawable sideBg = new GradientDrawable();
                sideBg.setColor(Color.rgb(26, 31, 38));
                sideBg.setCornerRadius(dp(22));
                sideBg.setStroke(dp(1), Color.rgb(52, 59, 69));
                sidebar.setBackground(sideBg);
                android.widget.FrameLayout.LayoutParams sp = new android.widget.FrameLayout.LayoutParams(dp(60), -1);
                sp.leftMargin = dp(8); sp.topMargin = dp(80); sp.bottomMargin = dp(12);
                frame.addView(sidebar, sp);
            }
        }
    }

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        mVisibilityUpdater = this::updateVisibility;
        addPreferencesFromResource(R.xml.pref_main);
        setupNotificationRequestPreference();
    }

    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }

    private void updateVisibility(){
        requirePreference("notification_permission_request").setVisible(!getLauncherActivity().checkForPermission(33, Manifest.permission.POST_NOTIFICATIONS));
    }

    private void setupNotificationRequestPreference() {
        Preference mRequestNotificationPermissionPreference = requirePreference("notification_permission_request");
        Activity activity = getActivity();
        if(activity instanceof LauncherActivity) {
            mRequestNotificationPermissionPreference.setOnPreferenceClickListener(preference -> {
                ((LauncherActivity) activity).askForPermission(33, Manifest.permission.POST_NOTIFICATIONS);
                return true;
            });
        }else{
            mRequestNotificationPermissionPreference.setVisible(false);
        }
        updateVisibility();
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        if(sharedPreferences != null) sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        mVisibilityUpdater.run();
    }

    @Override
    public void onPause() {
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        if(sharedPreferences != null) sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String s) {
        LauncherPreferences.loadPreferences(getContext());
    }

    protected Preference requirePreference(CharSequence key) {
        Preference preference = findPreference(key);
        if(preference != null) return preference;
        throw new IllegalStateException("Preference "+key+" is null");
    }
    @SuppressWarnings("unchecked")
    protected <T extends Preference> T requirePreference(CharSequence key, Class<T> preferenceClass) {
        Preference preference = requirePreference(key);
        if(preferenceClass.isInstance(preference)) return (T)preference;
        throw new IllegalStateException("Preference "+key+" is not an instance of "+preferenceClass.getSimpleName());
    }
    protected LauncherActivity getLauncherActivity(){
        return ((LauncherActivity) getActivity());
    }
}
