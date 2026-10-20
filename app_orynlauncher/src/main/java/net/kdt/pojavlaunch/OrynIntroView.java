package net.kdt.pojavlaunch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;
import java.util.Random;

/** Lightweight animated Oryn logo intro. Tap to skip; automatically ends after 7 seconds. */
public final class OrynIntroView extends View {
    public interface Listener { void onIntroFinished(); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random(27);
    private final float[] angles = new float[150];
    private final float[] seeds = new float[150];
    private final float[] speeds = new float[150];
    private long started;
    private boolean finished;
    private Listener listener;

    public OrynIntroView(Context context) {
        super(context);
        for (int i = 0; i < angles.length; i++) {
            angles[i] = (float)(random.nextFloat() * Math.PI * 2);
            seeds[i] = random.nextFloat();
            speeds[i] = 0.4f + random.nextFloat() * 1.4f;
        }
        paint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        setOnClickListener(v -> finishIntro());
        setFocusable(true);
    }

    public void setListener(Listener value) { listener = value; }

    private void finishIntro() {
        if (finished) return;
        finished = true;
        if (listener != null) listener.onIntroFinished();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (finished) return;
        if (started == 0) started = SystemClock.uptimeMillis();
        float t = (SystemClock.uptimeMillis() - started) / 1000f;
        if (t >= 7f) { finishIntro(); return; }
        int w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f, radius = (float)Math.hypot(w, h) * .55f;
        c.drawColor(Color.BLACK);

        // Monochrome hyperspace streaks converge into the logo.
        if (t < 3.4f) {
            paint.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < angles.length; i++) {
                float p = (seeds[i] + t * (.22f + t * .12f)) % 1f;
                float r = radius * p * (1f - .65f * Math.min(1f, t / 3.4f));
                float x = (float)Math.cos(angles[i]), y = (float)Math.sin(angles[i]);
                int alpha = (int)(220 * Math.min(1f, t * 1.5f) * (1f - t / 4f));
                paint.setColor(Color.argb(Math.max(0, alpha), 220, 220, 225));
                paint.setStrokeWidth(.8f + p * 2.2f);
                c.drawLine(cx + x * r, cy + y * r,
                    cx + x * Math.max(0, r - speeds[i] * (8f + t * 16f)),
                    cy + y * Math.max(0, r - speeds[i] * (8f + t * 16f)), paint);
            }
        }

        float reveal = Math.max(0f, Math.min(1f, (t - 2.8f) / 1.1f));
        if (reveal > 0f) {
            c.save();
            c.translate(cx, cy);
            c.scale(.88f + .12f * reveal, .88f + .12f * reveal);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(3f, h * .012f));
            paint.setColor(Color.argb((int)(255 * reveal), 235, 235, 240));
            c.drawCircle(0, -h * .105f, Math.min(h, w) * .105f, paint);
            c.drawLine(w * .035f, -h * .045f, w * .10f, h * .025f, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb((int)(255 * reveal), 255, 255, 255));
            paint.setTextSize(Math.min(h * .15f, w * .20f));
            c.drawText("ORYN", cx, h * .57f - cy, paint);
            paint.setTextSize(Math.min(h * .045f, w * .06f));
            paint.setLetterSpacing(.22f);
            c.drawText("LAUNCHER", 0, h * .64f - cy, paint);
            paint.setLetterSpacing(0f);
            c.restore();
        }
        if (t > 6.25f) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb((int)(255 * Math.min(1f, (t - 6.25f) / .75f)), 0, 0, 0));
            c.drawRect(0, 0, w, h, paint);
        }
        postInvalidateOnAnimation();
    }
}