package net.kdt.pojavlaunch.prefs.screens;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;
import androidx.preference.SwitchPreferenceCompat;

import git.artdeell.mojo.R;

import net.kdt.pojavlaunch.Architecture;
import net.kdt.pojavlaunch.game.renderer.RendererCache;
import net.kdt.pojavlaunch.game.renderer.extra.GLESProvider;
import net.kdt.pojavlaunch.plugins.LibraryPlugin;
import net.kdt.pojavlaunch.prefs.CustomSeekBarPreference;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.game.renderer.GameRenderer;
import net.kdt.pojavlaunch.utils.GpuUtils;
import net.kdt.pojavlaunch.utils.MCOptionUtils;
import net.kdt.pojavlaunch.utils.OrynFpsBoostUtils;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;

/**
 * Fragment for any settings video related
 */
public class LauncherPreferenceVideoFragment extends LauncherPreferenceFragment {
    private Boolean hasAngle = null;
    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_video);
        requirePreference("renderer").setTitle("Global Renderer");
        // The renderer ListPreference uses SimpleSummaryProvider from pref_video.xml.
        // Do not call setSummary() while a SummaryProvider is installed.
        requirePreference("resolutionRatio").setTitle("Resolution Scale");
        requirePreference("resolutionRatio").setSummary("Change the resolution scale of the game");
        SwitchPreferenceCompat fpsBoost = requirePreference("orynFpsBoost", SwitchPreferenceCompat.class);
        fpsBoost.setChecked(LauncherPreferences.PREF_ORYN_FPS_BOOST);
        fpsBoost.setSummary("Real Minecraft optimization: lower GPU/CPU workload for higher in-game FPS");
        requirePreference("zinkPreferSystemDriver").setTitle("Vulkan Driver");
        requirePreference("zinkPreferSystemDriver").setSummary("Use the system Vulkan driver when supported");
        int resolution = LauncherPreferences.PREF_ORYN_FPS_BOOST
                ? 75
                : (int) (LauncherPreferences.PREF_SCALE_FACTOR * 100);

        CustomSeekBarPreference resolutionSeekbar = requirePreference("resolutionRatio",
                CustomSeekBarPreference.class);
        resolutionSeekbar.setSuffix(" %");

        // #724 bug fix
        if (resolution < 25) {
            resolutionSeekbar.setValue(100);
        } else {
            resolutionSeekbar.setValue(resolution);
        }
        resolutionSeekbar.setEnabled(!LauncherPreferences.PREF_ORYN_FPS_BOOST);

        fpsBoost.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean enabled = Boolean.TRUE.equals(newValue);
            OrynFpsBoostUtils.setEnabled(enabled);
            int currentResolution = enabled ? 75 : getCurrentResolutionPreference();
            resolutionSeekbar.setValue(Math.max(25, currentResolution));
            resolutionSeekbar.setEnabled(!enabled);
            fpsBoost.setSummary(enabled
                    ? "Active • 75% resolution • 2 render distance • performance preset"
                    : "Applies a real Minecraft performance profile when enabled");
            return true;
        });

        // Sustained performance is only available since Nougat
        SwitchPreference sustainedPerfSwitch = requirePreference("sustainedPerformance",
                SwitchPreference.class);
        sustainedPerfSwitch.setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N);
        sustainedPerfSwitch.setChecked(LauncherPreferences.PREF_SUSTAINED_PERFORMANCE);

        requirePreference("alternate_surface", SwitchPreferenceCompat.class).setChecked(LauncherPreferences.PREF_USE_ALTERNATE_SURFACE);
        requirePreference("force_vsync", SwitchPreferenceCompat.class).setChecked(LauncherPreferences.PREF_FORCE_VSYNC);

        Preference driverPreference = requirePreference("zinkPreferSystemDriver");
        PackageManager packageManager = driverPreference.getContext().getPackageManager();
        boolean supportsTurnip = GpuUtils.checkVulkanSupport(packageManager) && GpuUtils.getGlInfo().isAdreno();
        driverPreference.setVisible(supportsTurnip);

        // Show ANGLE switch only if AnglePlugin is available
        if(hasAngle == null) {
            GLESProvider provider = GLESProvider.getGlesProvider(getContext(), true);
            hasAngle = provider instanceof GLESProvider.ExternalAngleProvider || provider instanceof GLESProvider.SystemAngleProvider;
        }
        SwitchPreferenceCompat angleSwitch = requirePreference("use_angle", SwitchPreferenceCompat.class);
        angleSwitch.setVisible(hasAngle);
        angleSwitch.setChecked(LauncherPreferences.PREF_USE_ANGLE);

        ListPreference rendererListPreference = requirePreference("renderer",
                ListPreference.class);
        RendererCache list = RendererCache.getCompatibleRenderers(getContext());
        rendererListPreference.setEntries(list.rendererDisplayNames);
        rendererListPreference.setEntryValues(list.rendererIds.toArray(new String[0]));

        computeVisibility();
    }

    private SharedPreferences pOrDefaultPreferences() {
        return LauncherPreferences.DEFAULT_PREF;
    }

    private int getCurrentResolutionPreference() {
        Object value = pOrDefaultPreferences().getAll().get("resolutionRatio");
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return Math.round(Float.parseFloat((String) value));
            } catch (NumberFormatException ignored) {
            }
        }
        return Math.max(25, (int) (LauncherPreferences.PREF_SCALE_FACTOR * 100));
    }

    private void applyFpsBoostToSelectedInstance(boolean enabled) {
        try {
            Instance instance = Instances.loadSelectedInstance();
            if (instance == null) return;
            String gameDir = instance.getGameDirectory().getAbsolutePath();
            if (enabled) {
                MCOptionUtils.applyOrynFpsBoost(gameDir);
            } else {
                MCOptionUtils.restoreOrynFpsBoost(gameDir);
            }
        } catch (Throwable e) {
            android.util.Log.w("OrynFPS", "Could not update Minecraft options immediately", e);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Activity activity = getActivity();
        if(activity != null) {
            requirePreference("ignoreNotch").setVisible(LauncherPreferences.hasNotch(activity));
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String s) {
        super.onSharedPreferenceChanged(p, s);
        computeVisibility();
    }

    private void computeVisibility(){
        requirePreference("force_vsync", SwitchPreferenceCompat.class)
                .setVisible(LauncherPreferences.PREF_USE_ALTERNATE_SURFACE);
    }
}
