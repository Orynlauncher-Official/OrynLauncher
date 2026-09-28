package net.kdt.pojavlaunch.prefs.screens;


import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.graphics.Color;
import androidx.recyclerview.widget.RecyclerView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.utils.OrynColorPickerDialog;
import net.kdt.pojavlaunch.utils.OrynThemeManager;
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
        // Match the home screen's charcoal background and rounded sidebar/pill surfaces.
        view.setBackgroundResource(R.drawable.oryn_home_bg);
        RecyclerView list = getListView();
        if (list == null) return;
        list.setBackgroundResource(R.drawable.oryn_home_bg);
        list.setClipToPadding(false);
        list.setPadding(dp(24), dp(16), dp(24), dp(24));
        list.setItemAnimator(null);
        list.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull android.graphics.Rect outRect,
                    @NonNull View child, @NonNull RecyclerView parent,
                    @NonNull RecyclerView.State state) {
                outRect.bottom = dp(8);
            }
        });
        list.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override public void onChildViewAttachedToWindow(@NonNull View child) {
                child.setBackgroundResource(R.drawable.oryn_pill);
                OrynThemeManager.stylePreferenceItem(requireContext(), child);
                child.setMinimumHeight(dp(64));
                child.setPadding(dp(12), child.getPaddingTop(), dp(12), child.getPaddingBottom());
            }
            @Override public void onChildViewDetachedFromWindow(@NonNull View child) {}
        });
        OrynThemeManager.apply(requireActivity());
        list.post(() -> OrynThemeManager.apply(requireActivity()));
    }

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        mVisibilityUpdater = this::updateVisibility;
        addPreferencesFromResource(R.xml.pref_main);
        setupNotificationRequestPreference();
        setupThemePreference();
    }

    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }

    private void updateVisibility(){
        requirePreference("notification_permission_request").setVisible(!getLauncherActivity().checkForPermission(33, Manifest.permission.POST_NOTIFICATIONS));
    }

    private void setupThemePreference() {
        Preference preference = requirePreference("theme_color");
        preference.setOnPreferenceClickListener(p -> {
            OrynColorPickerDialog.show(requireContext());
            return true;
        });
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
        if (getActivity() != null) OrynThemeManager.apply(getActivity());
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
