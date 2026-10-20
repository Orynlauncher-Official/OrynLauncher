package net.kdt.pojavlaunch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * Cinematic OrynLauncher Plus loading screen (monochrome, matches OrynIntroView).
 * Forward-flight starfield, slow rotating octagon tunnel, pulsing silver Q with a
 * circular progress ring, ORYN / LAUNCHER / PLUS wordmark, status text and a glowing bar.
 * Drive it with setProgress(0..100) and optionally setStatus(text).
 */
public final class OrynLoadingView extends View {
    private static final int STARS = 140;
    private static final int RINGS = 7;
    private static final String[] STATUS = {
            "Preparing your adventure\u2026",
            "Checking game files\u2026",
            "Loading Oryn modules\u2026",
            "Warming up the engine\u2026",
            "Almost there\u2026"
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint qPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wordPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private final float[] starAngle = new float[STARS];
    private final float[] starSeed = new float[STARS];
    private final float[] starSpeed = new float[STARS];

    private long startMs = 0;
    private float lastT = 0f;
    private float target = 0f;
    private float shown = 0f;
    private String statusOverride = null;
    private int lastStatusIdx = -1;
    private float statusChangeT = 0f;

    private float cx, cy, qy, radius, maxR, barW, barY, barH, density = 1f;
    private Shader qShader, wordShader, barShader;

    public OrynLoadingView(Context context) {
        super(context);
        init();
    }

    public OrynLoadingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        Random r = new Random(11);
        for (int i = 0; i < STARS; i++) {
            starAngle[i] = r.nextFloat() * (float) (Math.PI * 2);
            starSeed[i] = r.nextFloat();
            starSpeed[i] = 0.04f + r.nextFloat() * 0.08f;
        }
        paint.setStrokeCap(Paint.Cap.ROUND);
        wordPaint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        smallPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        smallPaint.setTextAlign(Paint.Align.CENTER);
        setBackgroundColor(Color.BLACK);
    }

    /** 0..100 */
    public void setProgress(float percent) {
        target = Math.max(0f, Math.min(100f, percent));
    }

    /** Overrides the automatic status text. Pass null to go back to automatic. */
    public void setStatus(String status) {
        statusOverride = status;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cx = w / 2f;
        cy = h / 2f;
        qy = h * 0.30f;
        radius = h * 0.115f;
        maxR = (float) Math.hypot(cx, cy);
        barW = Math.min(w * 0.5f, h * 1.1f);
        barY = h * 0.875f;
        barH = Math.max(3f * density, h * 0.007f);

        glowPaint.setShader(new RadialGradient(0, 0, Math.max(1f, maxR),
                new int[]{Color.WHITE, Color.argb(110, 255, 255, 255), Color.TRANSPARENT},
                new float[]{0f, 0.22f, 1f}, Shader.TileMode.CLAMP));
        qShader = new LinearGradient(0, qy - radius, 0, qy + radius,
                new int[]{Color.WHITE, Color.rgb(150, 150, 155), Color.WHITE, Color.rgb(110, 110, 115)},
                new float[]{0f, 0.45f, 0.7f, 1f}, Shader.TileMode.CLAMP);
        wordShader = new LinearGradient(0, h * 0.48f, 0, h * 0.74f,
                new int[]{Color.WHITE, Color.rgb(140, 140, 145)}, null, Shader.TileMode.CLAMP);
        barShader = new LinearGradient(cx - barW / 2f, 0, cx + barW / 2f, 0,
                Color.rgb(110, 110, 115), Color.WHITE, Shader.TileMode.CLAMP);
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static float smooth(float a, float b, float t) {
        float x = clamp01((t - a) / (b - a));
        return x * x * (3 - 2 * x);
    }

    private static float fract(float v) {
        return v - (float) Math.floor(v);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = SystemClock.uptimeMillis();
        if (startMs == 0) startMs = now;
        float t = (now - startMs) / 1000f;
        float dt = Math.min(0.1f, Math.max(0f, t - lastT));
        lastT = t;
        shown += (target - shown) * Math.min(1f, dt * 5f);

        canvas.drawColor(Color.BLACK);
        drawAmbient(canvas, t);
        drawTunnel(canvas, t);
        drawStars(canvas, t);
        drawQ(canvas, t);
        drawWordmark(canvas, t);
        drawBar(canvas, t);

        postInvalidateOnAnimation();
    }

    private void drawAmbient(Canvas c, float t) {
        float s = 0.55f + 0.05f * (float) Math.sin(t * 1.6f);
        c.save();
        c.translate(cx, qy);
        c.scale(s, s);
        glowPaint.setAlpha(60);
        c.drawCircle(0, 0, maxR, glowPaint);
        c.restore();
    }

    private void drawTunnel(Canvas c, float t) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        for (int k = 0; k < RINGS; k++) {
            float p = fract(k / (float) RINGS + t * 0.08f);
            float r = (float) Math.pow(p, 2.0) * maxR * 1.3f;
            if (r < 6f) continue;
            float alpha = (float) Math.sin(Math.PI * p) * 0.30f;
            float rot = t * 0.15f + k * 0.07f;
            path.reset();
            for (int s = 0; s < 8; s++) {
                float a = rot + (float) (s * Math.PI / 4);
                float px = cx + (float) Math.cos(a) * r;
                float py = qy + (float) Math.sin(a) * r * 0.82f;
                if (s == 0) path.moveTo(px, py); else path.lineTo(px, py);
            }
            path.close();
            int g = 160 + (int) (95 * p);
            paint.setColor(Color.argb((int) (255 * alpha), g, g, g));
            paint.setStrokeWidth(1.2f * density + 3f * density * p);
            c.drawPath(path, paint);
        }
    }

    private void drawStars(Canvas c, float t) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < STARS; i++) {
            float p = fract(starSeed[i] + t * starSpeed[i]);
            float r = p * p * maxR * 1.1f;
            float x = cx + (float) Math.cos(starAngle[i]) * r;
            float y = cy + (float) Math.sin(starAngle[i]) * r;
            paint.setColor(Color.argb((int) (230 * p), 255, 255, 255));
            c.drawCircle(x, y, (0.6f + 1.8f * p) * density, paint);
        }
    }

    private void drawQ(Canvas c, float t) {
        float pulse = 1f + 0.025f * (float) Math.sin(t * 2.2f);
        float ring = radius * 0.33f;

        // Progress ring + rotating ticks (not scaled, so the ring stays crisp).
        float rr = radius * 1.42f;
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f * density);
        paint.setColor(Color.argb(60, 255, 255, 255));
        c.drawCircle(cx, qy, rr, paint);

        for (int i = 0; i < 36; i++) {
            float a = t * 0.2f + i * (float) (Math.PI / 18);
            boolean major = i % 6 == 0;
            float r1 = radius * 1.62f;
            float r2 = r1 + radius * (major ? 0.14f : 0.07f);
            paint.setStrokeWidth((major ? 1.6f : 1.0f) * density);
            paint.setColor(Color.argb(major ? 130 : 70, 255, 255, 255));
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            c.drawLine(cx + cos * r1, qy + sin * r1, cx + cos * r2, qy + sin * r2, paint);
        }

        float sweep = 3.6f * shown;
        if (sweep > 0.5f) {
            rect.set(cx - rr, qy - rr, cx + rr, qy + rr);
            paint.setStrokeWidth(3.2f * density);
            paint.setColor(Color.WHITE);
            c.drawArc(rect, -90f, sweep, false, paint);
            float ea = (float) Math.toRadians(-90f + sweep);
            float ex = cx + (float) Math.cos(ea) * rr;
            float ey = qy + (float) Math.sin(ea) * rr;
            c.save();
            c.translate(ex, ey);
            c.scale(0.05f, 0.05f);
            glowPaint.setAlpha(230);
            c.drawCircle(0, 0, maxR, glowPaint);
            c.restore();
        }

        // Pulsing silver Q.
        c.save();
        c.scale(pulse, pulse, cx, qy);
        qPaint.setShader(qShader);
        qPaint.setStyle(Paint.Style.STROKE);
        qPaint.setStrokeCap(Paint.Cap.BUTT);
        qPaint.setStrokeWidth(ring);
        c.drawCircle(cx, qy, radius, qPaint);
        qPaint.setStrokeWidth(ring * 1.12f);
        c.drawLine(cx + radius * 0.12f, qy + radius * 0.16f,
                cx + radius * 1.02f, qy + radius * 1.08f, qPaint);
        c.restore();
    }

    private void drawWordmark(Canvas c, float t) {
        float h = getHeight();
        wordPaint.setShader(wordShader);
        wordPaint.setAlpha(235 + (int) (20 * Math.sin(t * 1.8f)));
        drawFixed(c, wordPaint, "ORYN", h * 0.625f, h * 0.50f, 0.055f);
        drawFixed(c, wordPaint, "LAUNCHER", h * 0.705f, h * 0.50f, 0.03f);

        // PLUS with thin side lines.
        drawFixed(c, wordPaint, "PLUS", h * 0.765f, h * 0.10f, 0.55f);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f * density);
        paint.setColor(Color.argb(130, 255, 255, 255));
        float ly = h * 0.752f;
        c.drawLine(cx - h * 0.21f, ly, cx - h * 0.09f, ly, paint);
        c.drawLine(cx + h * 0.09f, ly, cx + h * 0.21f, ly, paint);
    }

    private void drawBar(Canvas c, float t) {
        float h = getHeight();
        float x0 = cx - barW / 2f;

        // Status text (fades in whenever it changes).
        int idx = Math.min(STATUS.length - 1, (int) (shown / 20f));
        String text = statusOverride != null ? statusOverride : STATUS[idx];
        int key = statusOverride != null ? -2 : idx;
        if (key != lastStatusIdx) {
            lastStatusIdx = key;
            statusChangeT = t;
        }
        smallPaint.setShader(null);
        smallPaint.setTextSize(h * 0.034f);
        smallPaint.setColor(Color.WHITE);
        smallPaint.setAlpha((int) (230 * smooth(0f, 0.45f, t - statusChangeT)));
        c.drawText(text, cx, h * 0.835f, smallPaint);

        // Track.
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(50, 255, 255, 255));
        rect.set(x0, barY - barH / 2f, x0 + barW, barY + barH / 2f);
        c.drawRoundRect(rect, barH / 2f, barH / 2f, paint);

        // Fill with silver gradient.
        float fillW = barW * shown / 100f;
        if (fillW > 1f) {
            c.save();
            c.clipRect(x0, barY - barH, x0 + fillW, barY + barH);
            barPaint.setShader(barShader);
            barPaint.setStyle(Paint.Style.FILL);
            c.drawRoundRect(rect, barH / 2f, barH / 2f, barPaint);
            c.restore();

            // Glow at the leading edge.
            c.save();
            c.translate(x0 + fillW, barY);
            c.scale(0.06f, 0.06f);
            glowPaint.setAlpha(210);
            c.drawCircle(0, 0, maxR, glowPaint);
            c.restore();
        }

        smallPaint.setTextSize(h * 0.030f);
        smallPaint.setAlpha(210);
        c.drawText((int) shown + "%", cx, h * 0.935f, smallPaint);
    }

    /** Centred text drawn character by character, scaled to an exact line width (never clipped). */
    private void drawFixed(Canvas c, Paint p, String s, float baseline, float targetW, float tracking) {
        p.setTextAlign(Paint.Align.LEFT);
        p.setLetterSpacing(0f);
        p.setTextSize(100f);
        float adv = 0f;
        for (int i = 0; i < s.length(); i++) adv += p.measureText(s, i, i + 1);
        float size = targetW / (adv / 100f + tracking * Math.max(1, s.length() - 1));
        p.setTextSize(size);
        float x = cx - targetW / 2f;
        for (int i = 0; i < s.length(); i++) {
            c.drawText(s, i, i + 1, x, baseline, p);
            x += p.measureText(s, i, i + 1) + tracking * size;
        }
    }
}