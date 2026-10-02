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

		// OrynLauncher control preset — matched to the supplied reference.
		final String m = "${margin}";
		final String left = m + " + 56";
		final String arrow = "64";
		final String arrowGap = "22";

		// Top-left keyboard/debug/chat/perspective controls.
		this.mControlDataList.add(new ControlData("ESC", new int[]{KeyEvent.KEYCODE_ESCAPE}, m + " + 34", m, 58, 36, false));
		this.mControlDataList.add(new ControlData("F3", new int[]{KeyEvent.KEYCODE_F3}, m + " + 182", m, 58, 36, false));
		this.mControlDataList.add(new ControlData("F5", new int[]{KeyEvent.KEYCODE_F5}, m + " + 324", m, 58, 36, false));
		this.mControlDataList.add(new ControlData("TAB", new int[]{KeyEvent.KEYCODE_TAB}, m + " + 34", m + " + 52", 58, 36, false));
		this.mControlDataList.add(new ControlData("T", new int[]{KeyEvent.KEYCODE_T}, m + " + 184", m + " + 52", 58, 36, false));
		this.mControlDataList.add(new ControlData("Pout", new int[]{KeyEvent.KEYCODE_P}, m + " + 312", m + " + 52", 70, 36, false));

		// Modifier controls.
		this.mControlDataList.add(new ControlData("Ctrl", new int[]{KeyEvent.KEYCODE_CTRL_LEFT}, m + " + 56", "${screen_height} * 0.31", 74, 44, false));
		this.mControlDataList.add(new ControlData("Alt", new int[]{KeyEvent.KEYCODE_ALT_LEFT}, m + " + 256", "${screen_height} * 0.31", 74, 44, false));

		// Eight-direction movement pad, centered vertically on the left side.
		String leftX = left;
		String centerX = left + " + " + arrow + " + " + arrowGap;
		String rightX = left + " + 2 * (" + arrow + " + " + arrowGap + ")";
		String top = "${screen_height} * 0.46";
		String mid = "${screen_height} * 0.555";
		String bottom = "${screen_height} * 0.65";
		this.mControlDataList.add(new ControlData("↖", new int[]{KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_A}, leftX, top, 64, 64, false));
		this.mControlDataList.add(new ControlData("↑", new int[]{KeyEvent.KEYCODE_W}, centerX, top, 64, 64, false));
		this.mControlDataList.add(new ControlData("↗", new int[]{KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_D}, rightX, top, 64, 64, false));
		this.mControlDataList.add(new ControlData("←", new int[]{KeyEvent.KEYCODE_A}, leftX, mid, 64, 64, false));
		this.mControlDataList.add(new ControlData("→", new int[]{KeyEvent.KEYCODE_D}, rightX, mid, 64, 64, false));
		this.mControlDataList.add(new ControlData("↙", new int[]{KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_A}, leftX, bottom, 64, 64, false));
		this.mControlDataList.add(new ControlData("↓", new int[]{KeyEvent.KEYCODE_S}, centerX, bottom, 64, 64, false));
		this.mControlDataList.add(new ControlData("↘", new int[]{KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D}, rightX, bottom, 64, 64, false));

		// Right-edge mouse controls. The right expression already accounts for button width.
		this.mControlDataList.add(new ControlData("Mouse", new int[]{ControlData.SPECIALBTN_VIRTUALMOUSE}, "${right} - " + m, m, 58, 36, false));
		this.mControlDataList.add(new ControlData("SCROLL UP", new int[]{ControlData.SPECIALBTN_SCROLLUP}, "${right} - " + m, m + " + 52", 58, 58, false));
		this.mControlDataList.add(new ControlData("SCROLL DOWN", new int[]{ControlData.SPECIALBTN_SCROLLDOWN}, "${right} - " + m, m + " + 116", 58, 58, false));
		this.mControlDataList.add(new ControlData("MOUSE MID", new int[]{ControlData.SPECIALBTN_MOUSEMID}, "${right} - " + m, m + " + 184", 58, 58, false));

		// Primary/secondary mouse buttons.
		this.mControlDataList.add(new ControlData("PRI", new int[]{ControlData.SPECIALBTN_MOUSEPRI}, "${screen_width} * 0.66", "${screen_height} * 0.49", 74, 48, false));
		this.mControlDataList.add(new ControlData("SEC", new int[]{ControlData.SPECIALBTN_MOUSESEC}, "${screen_width} * 0.785", "${screen_height} * 0.49", 74, 48, false));

		// Inventory key near the lower-right middle.
		this.mControlDataList.add(new ControlData("E", new int[]{KeyEvent.KEYCODE_E}, "${screen_width} * 0.66", "${screen_height} * 0.815", 58, 44, false));

		// Floating gear/menu button. It is intentionally circular and uses the
		// free-positioning path in ControlInterface instead of snapping to other controls.
		ControlData floatingMenu = new ControlData(
				"⚙",
				new int[]{ControlData.SPECIALBTN_MENU},
				"${screen_width} * 0.5 - 28",
				"${screen_height} * 0.49",
				56,
				56,
				false
		);
		floatingMenu.cornerRadius = 50;
		this.mControlDataList.add(floatingMenu);
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
