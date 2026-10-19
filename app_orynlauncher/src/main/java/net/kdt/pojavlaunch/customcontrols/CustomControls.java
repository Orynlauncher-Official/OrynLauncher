package net.kdt.pojavlaunch.customcontrols;
import android.content.*;
import android.view.KeyEvent;

import androidx.annotation.Keep;

import java.io.File;
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
	
	// OrynLauncher Plus default controls, based on the supplied control-layout JSON.
	// Positions are responsive expressions from the imported layout, so they scale
	// across screen sizes instead of relying on a single device's pixel dimensions.
	@SuppressWarnings("unused")
	public CustomControls(Context ctx) {
		this();
		mLayoutBitmaps = LayoutBitmaps.createEmpty();
		final int bg = 1711276032;
		final int stroke = 1090519039;
		final float opacity = 1f;
		final float strokeWidth = 1f;

		// Top-left shortcuts.
		addOrynButton("F3", new int[]{KeyEvent.KEYCODE_F3}, "px(10)", "px(5)", 45, 25, false, bg, stroke, opacity, strokeWidth, 25);
		addOrynButton("T", new int[]{KeyEvent.KEYCODE_T}, "px(60)", "px(5)", 45, 25, false, bg, stroke, opacity, strokeWidth, 25);
		addOrynButton("💬", new int[]{KeyEvent.KEYCODE_SLASH}, "px(110)", "px(5)", 45, 25, false, bg, stroke, opacity, strokeWidth, 25);
		addOrynButton("TAB", new int[]{KeyEvent.KEYCODE_TAB}, "px(160)", "px(5)", 45, 25, false, bg, stroke, opacity, strokeWidth, 25);
		addOrynButton("F5", new int[]{KeyEvent.KEYCODE_F5}, "px(10)", "px(33)", 45, 25, false, bg, stroke, opacity, strokeWidth, 25);
		addOrynButton("⌨\nKEYBOARD", new int[]{ControlData.SPECIALBTN_KEYBOARD}, "px(60)", "px(33)", 45, 25, false, bg, stroke, opacity, strokeWidth, 25);
		addOrynButton("🎒\nINVENTORY", new int[]{KeyEvent.KEYCODE_E}, "px(10)", "px(80)", 45, 36, false, bg, stroke, opacity, strokeWidth, 25);

		// Four-way movement pad and sprint modifier.
		addOrynButton("▲", new int[]{KeyEvent.KEYCODE_W}, "px(66)", "${bottom} - px(121)", 48, 42, false, bg, stroke, opacity, strokeWidth, 30);
		addOrynButton("◀", new int[]{KeyEvent.KEYCODE_A}, "px(24)", "${bottom} - px(78)", 46, 46, false, bg, stroke, opacity, strokeWidth, 30);
		addOrynButton("▼", new int[]{KeyEvent.KEYCODE_S}, "px(66)", "${bottom} - px(40)", 48, 42, false, bg, stroke, opacity, strokeWidth, 30);
		addOrynButton("▶", new int[]{KeyEvent.KEYCODE_D}, "px(111)", "${bottom} - px(78)", 46, 46, false, bg, stroke, opacity, strokeWidth, 30);
		addOrynButton("○", new int[]{KeyEvent.KEYCODE_CTRL_LEFT}, "px(76)", "${bottom} - px(87)", 29, 29, true, bg, stroke, opacity, strokeWidth, 50);

		// Right-side mouse, interaction, jump, sneak and inventory controls.
		addOrynButton("⚙", new int[]{ControlData.SPECIALBTN_TOGGLECTRL}, "${right} - px(121)", "px(5)", 32, 25, false, bg, stroke, opacity, strokeWidth, 25);
		addOrynButton("🖱 MOUSE", new int[]{ControlData.SPECIALBTN_VIRTUALMOUSE}, "${right} - px(10)", "px(5)", 69, 25, false, bg, stroke, opacity, strokeWidth, 25);
		addOrynButton("🖱\nCURSOR", new int[]{ControlData.SPECIALBTN_MOUSEMID}, "${right} - px(106)", "px(51)", 45, 36, false, bg, stroke, opacity, strokeWidth, 25);
		addOrynButton("⇅\nSCROLL", new int[]{ControlData.SPECIALBTN_SCROLLUP}, "${right} - px(55)", "px(51)", 45, 36, false, bg, stroke, opacity, strokeWidth, 25);
		addOrynButton("☰\nMENU", new int[]{ControlData.SPECIALBTN_MENU}, "${right} - px(5)", "px(51)", 45, 36, false, bg, stroke, opacity, strokeWidth, 25);
		addOrynButton("PRI\n(ATTACK)", new int[]{ControlData.SPECIALBTN_MOUSEPRI}, "${right} - px(67)", "px(116)", 49, 36, false, bg, stroke, opacity, strokeWidth, 25);
		addOrynButton("SEC\n(USE)", new int[]{ControlData.SPECIALBTN_MOUSESEC}, "${right} - px(10)", "px(116)", 50, 36, false, bg, stroke, opacity, strokeWidth, 25);
		addOrynButton("SPACE\n(JUMP)", new int[]{KeyEvent.KEYCODE_SPACE}, "${right} - px(12)", "px(181)", 83, 36, false, bg, stroke, opacity, strokeWidth, 25);
		addOrynButton("SHIFT\n(SNEAK)", new int[]{KeyEvent.KEYCODE_SHIFT_LEFT}, "${right} - px(71)", "px(225)", 53, 35, true, bg, stroke, opacity, strokeWidth, 25);
		addOrynButton("E\n(INVENTORY)", new int[]{KeyEvent.KEYCODE_E}, "${right} - px(12)", "px(225)", 53, 35, false, bg, stroke, opacity, strokeWidth, 25);

		version = 12;
	}

	private void addOrynButton(String name, int[] keycodes, String x, String y,
			float width, float height, boolean toggle, int bg, int stroke,
			float opacity, float strokeWidth, float radius) {
		mControlDataList.add(new ControlData(name, keycodes, x, y, width, height,
				toggle, opacity, bg, stroke, strokeWidth, radius,
				true, true, false, false, null));
	}
	
	public void save(String path) throws IOException {
		if (version < 12) version = 12;
		if (mLayoutBitmaps == null) mLayoutBitmaps = LayoutBitmaps.createEmpty();
		String jsonControls = Tools.GLOBAL_GSON.toJson(this);
		File target = new File(path);
		File tmp = new File(path + ".tmp");
		try (FileOutputStream out = new FileOutputStream(tmp)) {
			LayoutBitmaps.store(out, new LayoutBitmaps.ControlsContainer(jsonControls, mLayoutBitmaps));
			out.getFD().sync();
		} catch (IOException | RuntimeException e) {
			//noinspection ResultOfMethodCallIgnored
			tmp.delete();
			throw e;
		}
		if (!tmp.renameTo(target)) {
			if ((target.exists() && !target.delete()) || !tmp.renameTo(target)) {
				//noinspection ResultOfMethodCallIgnored
				tmp.delete();
				throw new IOException("Failed to replace control layout at " + path);
			}
		}
	}
}
