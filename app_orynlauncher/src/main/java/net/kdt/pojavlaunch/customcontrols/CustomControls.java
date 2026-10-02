package net.kdt.pojavlaunch.customcontrols;
import android.content.*;
import android.view.KeyEvent;

import androidx.annotation.Keep;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import net.kdt.pojavlaunch.*;

import git.artdeell.mojo.R;

@Keep
public class CustomControls {
	public int version = -1;
    public float scaledAt;
	public List<ControlData> mControlDataList;
	public List<ControlDrawerData> mDrawerDataList;
	public List<ControlJoystickData> mJoystickDataList;
	public transient LayoutBitmaps mLayoutBitmaps;
	public CustomControls() {
		this(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
	}



	public CustomControls(List<ControlData> mControlDataList, List<ControlDrawerData> mDrawerDataList, List<ControlJoystickData> mJoystickDataList) {
		this.mControlDataList = mControlDataList;
		this.mDrawerDataList = mDrawerDataList;
		this.mJoystickDataList = mJoystickDataList;
		this.scaledAt = 100f;
	}
	
	// Generate default control
	// Here for historical reasons
	// Just admire it idk
	@SuppressWarnings("unused")
	public CustomControls(Context ctx) {
		this();

		// OrynLauncher control preset based on the supplied reference.
		final String m = "${margin}";
		final String gap = "dp(8)";
		final String arrow = "44";

		// Top-left keyboard/debug/chat/perspective controls.
		this.mControlDataList.add(new ControlData("ESC", new int[]{KeyEvent.KEYCODE_ESCAPE}, m, m, 58, 36, false));
		this.mControlDataList.add(new ControlData("F3", new int[]{KeyEvent.KEYCODE_F3}, m + " + 66", m, 58, 36, false));
		this.mControlDataList.add(new ControlData("F5", new int[]{KeyEvent.KEYCODE_F5}, m + " + 132", m, 58, 36, false));
		this.mControlDataList.add(new ControlData("TAB", new int[]{KeyEvent.KEYCODE_TAB}, m, m + " + 52", 58, 36, false));
		this.mControlDataList.add(new ControlData("T", new int[]{KeyEvent.KEYCODE_T}, m + " + 66", m + " + 52", 58, 36, false));
		this.mControlDataList.add(new ControlData("Pout", new int[]{KeyEvent.KEYCODE_P}, m + " + 132", m + " + 52", 70, 36, false));

		// Modifier controls on the left.
		this.mControlDataList.add(new ControlData("Ctrl", new int[]{KeyEvent.KEYCODE_CTRL_LEFT}, m + " + 22", "${screen_height} * 0.46", 74, 44, false));
		this.mControlDataList.add(new ControlData("Alt", new int[]{KeyEvent.KEYCODE_ALT_LEFT}, m + " + 104", "${screen_height} * 0.46", 74, 44, false));

		// Eight-direction movement pad.
		String leftX = m;
		String centerX = m + " + " + arrow + " + " + gap;
		String rightX = m + " + 2 * (" + arrow + " + " + gap + ")";
		String bottom = "${screen_height} - " + m + " - " + arrow;
		String mid = bottom + " - " + arrow + " - " + gap;
		String top = mid + " - " + arrow + " - " + gap;
		this.mControlDataList.add(new ControlData("↖", new int[]{KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_A}, leftX, top, 44, 44, false));
		this.mControlDataList.add(new ControlData("↑", new int[]{KeyEvent.KEYCODE_W}, centerX, top, 44, 44, false));
		this.mControlDataList.add(new ControlData("↗", new int[]{KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_D}, rightX, top, 44, 44, false));
		this.mControlDataList.add(new ControlData("←", new int[]{KeyEvent.KEYCODE_A}, leftX, mid, 44, 44, false));
		this.mControlDataList.add(new ControlData("→", new int[]{KeyEvent.KEYCODE_D}, rightX, mid, 44, 44, false));
		this.mControlDataList.add(new ControlData("↙", new int[]{KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_A}, leftX, bottom, 44, 44, false));
		this.mControlDataList.add(new ControlData("↓", new int[]{KeyEvent.KEYCODE_S}, centerX, bottom, 44, 44, false));
		this.mControlDataList.add(new ControlData("↘", new int[]{KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D}, rightX, bottom, 44, 44, false));

		// Mouse/scroll controls on the right edge.
		this.mControlDataList.add(new ControlData("Mouse", new int[]{ControlData.SPECIALBTN_VIRTUALMOUSE}, "${right} - 58", m, 58, 36, false));
		this.mControlDataList.add(new ControlData("SCROLL UP", new int[]{ControlData.SPECIALBTN_SCROLLUP}, "${right} - 58", m + " + 52", 58, 52, false));
		this.mControlDataList.add(new ControlData("SCROLL DOWN", new int[]{ControlData.SPECIALBTN_SCROLLDOWN}, "${right} - 58", m + " + 108", 58, 52, false));
		this.mControlDataList.add(new ControlData("PRI", new int[]{ControlData.SPECIALBTN_MOUSEPRI}, "${right} - 190", "${screen_height} * 0.48", 74, 48, false));
		this.mControlDataList.add(new ControlData("SEC", new int[]{ControlData.SPECIALBTN_MOUSESEC}, "${right} - 104", "${screen_height} * 0.48", 74, 48, false));
		this.mControlDataList.add(new ControlData("E", new int[]{KeyEvent.KEYCODE_E}, "${right} - 58", "${screen_height} - " + m + " - 58", 58, 44, false));

		// Center gear/menu control.
		this.mControlDataList.add(new ControlData("⚙", new int[]{ControlData.SPECIALBTN_MENU}, "${screen_width} * 0.5 - 28", "${screen_height} * 0.5 - 28", 56, 56, false));

		version = 10;
	}
	public void save(String path) throws IOException {
		//Current version is the V3.2 so the version as to be marked as 8 !
		version = 9;
		String jsonControls = Tools.GLOBAL_GSON.toJson(this);
		try(FileOutputStream fileOutputStream = new FileOutputStream(path)) {
			LayoutBitmaps.store(fileOutputStream, new LayoutBitmaps.ControlsContainer(
					jsonControls,
					mLayoutBitmaps
			));
		}
	}
}
