package net.kdt.pojavlaunch.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Color;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import net.kdt.pojavlaunch.game.platform.Platform;
import net.kdt.pojavlaunch.game.platform.cursor.PlatformCursor;
import net.kdt.pojavlaunch.game.platform.cursor.PlatformCursorImplementor;

import git.artdeell.mojo.R;

/**
 * A view that draws the platform cursor on the screen
 */
public class GameCursorView extends View implements PlatformCursorImplementor {
    private final Paint customCursorPaint = new Paint();
    private final Drawable cursorDrawable;
    private boolean noDraw = false;
    private float mouseScale = 1f;

    public GameCursorView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public GameCursorView(Context context) {
        this(context, null);
    }

    public GameCursorView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GameCursorView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        cursorDrawable = ContextCompat.getDrawable(context, R.drawable.ic_mouse_pointer);
        assert cursorDrawable != null;
        cursorDrawable.setBounds(0, 0, 36, 54);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (noDraw) return;
        // Scale coordinates back to the full unresized screen size
        int dx = (int) (Platform.cursorX * ((GameView)getParent()).cursorRatioX);
        int dy = (int) (Platform.cursorY * ((GameView)getParent()).cursorRatioY);
        canvas.translate(dx, dy);
        PlatformCursor cursor = Platform.getCursor();
        canvas.scale(mouseScale, mouseScale);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        int color = prefs.getInt("oryn_custom_cursor_color", Color.WHITE);
        String shape = prefs.getString("oryn_custom_cursor_shape", "Arrow");
        String drawing = prefs.getString("oryn_custom_cursor_drawing", "");
        customCursorPaint.setColor(color);
        customCursorPaint.setStyle(Paint.Style.STROKE);
        customCursorPaint.setStrokeWidth(3f);
        customCursorPaint.setStrokeCap(Paint.Cap.ROUND);
        customCursorPaint.setStrokeJoin(Paint.Join.ROUND);
        customCursorPaint.setAntiAlias(true);
        if (!drawing.isEmpty()) {
            String[] points = drawing.split(";");
            Path path = new Path();
            boolean started = false;
            for (String point : points) {
                String[] xy = point.split(",");
                if (xy.length != 2) continue;
                try {
                    float x = Float.parseFloat(xy[0]);
                    float y = Float.parseFloat(xy[1]);
                    if (!started) { path.moveTo(x, y); started = true; }
                    else path.lineTo(x, y);
                } catch (NumberFormatException ignored) { }
            }
            if (started) canvas.drawPath(path, customCursorPaint);
        } else if ("Crosshair".equals(shape)) {
            canvas.drawLine(-10, 0, 10, 0, customCursorPaint);
            canvas.drawLine(0, -10, 0, 10, customCursorPaint);
            canvas.drawCircle(0, 0, 4, customCursorPaint);
        } else if ("Circle".equals(shape)) {
            canvas.drawCircle(5, 5, 9, customCursorPaint);
            canvas.drawLine(5, 5, 15, 15, customCursorPaint);
        } else if (cursor == null) {
            cursorDrawable.setTint(color);
            cursorDrawable.draw(canvas);
        } else {
            customCursorPaint.setStyle(Paint.Style.FILL);
            canvas.drawBitmap(cursor.bitmap, -cursor.hotX, -cursor.hotY, customCursorPaint);
        }
    }

    @Override
    public void onCursorPosition() {
        if (!noDraw) post(this::invalidate);
    }

    @Override
    public void onCursorChanged() {
        post(this::invalidate);
    }

    @Override
    public void onGrabState(boolean isGrabbing) {
        noDraw = isGrabbing;
        invalidate();
    }

    public void setCursorScale(float scale) {
        this.mouseScale = scale;
    }
}
