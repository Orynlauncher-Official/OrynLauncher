package net.kdt.pojavlaunch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;
import java.util.Random;

/** Monochrome, eight-second procedural Oryn intro. Tap anywhere to skip. */
public final class OrynIntroView extends View {
    public interface Listener { void onIntroFinished(); }

    private static final float DURATION = 8.0f;
    private static final int STAR_COUNT = 180;
    private static final int PARTICLE_COUNT = 110;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint logoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float[] starAngles = new float[STAR_COUNT];
    private final float[] starSeeds = new float[STAR_COUNT];
    private final float[] particleAngles = new float[PARTICLE_COUNT];
    private final float[] particleSpeeds = new float[PARTICLE_COUNT];
    private final float[] particleSizes = new float[PARTICLE_COUNT];
    private final Random random = new Random(27);
    private long started;
    private boolean finished;
    private Listener listener;
    private float maxRadius;

    public OrynIntroView(Context context) {
        super(context);
        for (int i = 0; i < STAR_COUNT; i++) {
            starAngles[i] = random.nextFloat() * (float) (Math.PI * 2);
            starSeeds[i] = random.nextFloat();
        }
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particleAngles[i] = random.nextFloat() * (float) (Math.PI * 2);
            particleSpeeds[i] = 0.3f + random.nextFloat() * 1.1f;
            particleSizes[i] = 1f + random.nextFloat() * 2.5f;
        }
        paint.setStrokeCap(Paint.Cap.ROUND);
        textPaint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        setOnClickListener(v -> finishIntro());
        setFocusable(true);
    }

    public void setListener(Listener value) { listener = value; }

    private void finishIntro() {
        if (finished) return;
        finished = true;
        if (listener != null) listener.onIntroFinished();
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }

    private static float smooth(float a, float b, float t) {
        float x = clamp((t - a) / (b - a));
        return x * x * (3f - 2f * x);
    }

    private static float fract(float v) { return v - (float) Math.floor(v); }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        maxRadius = (float) Math.hypot(w / 2f, h / 2f);
        glowPaint.setShader(new RadialGradient(0, 0, Math.max(1f, maxRadius),
                new int[]{Color.WHITE, Color.argb(115, 255, 255, 255), Color.TRANSPARENT},
                new float[]{0f, 0.24f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (finished) return;
        if (started == 0) started = SystemClock.uptimeMillis();
        float t = (SystemClock.uptimeMillis() - started) / 1000f;
        if (t >= DURATION) { finishIntro(); return; }

        int w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        canvas.drawColor(Color.BLACK);

        // Phase 1: hyperspace streaks rush outwards and accelerate.
        drawStreaks(canvas, t, cx, cy);
        // Phase 2: streaks converge into a bright white flash.
        drawFlash(canvas, t, cx, cy);
        // Phase 3: a silver Q wordmark emerges through a particle burst.
        drawParticles(canvas, t, cx, cy);
        drawLogo(canvas, t, cx, cy, w, h);
        // Phase 4: a bright lens flare sweeps across the logo.
        drawLensFlare(canvas, t, cx, cy, h);
        // Phase 5: a rotating octagon tunnel appears and fades to black.
        drawTunnel(canvas, t, cx, cy);

        float fade = smooth(7.35f, DURATION, t);
        if (fade > 0f) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb((int) (fade * 255), 0, 0, 0));
            canvas.drawRect(0, 0, w, h, paint);
        }
        postInvalidateOnAnimation();
    }

    private void drawStreaks(Canvas c, float t, float cx, float cy) {
        if (t >= 3.4f) return;
        float visible = 1f - smooth(2.75f, 3.4f, t);
        float collapse = 1f - 0.9f * smooth(2.35f, 3.3f, t);
        float speed = clamp(t / 2.5f);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < STAR_COUNT; i++) {
            float p = fract(starSeeds[i] + 0.18f * t * t);
            float r = p * p * maxRadius * 1.15f * collapse;
            float len = (0.02f + 0.3f * speed * p) * maxRadius * collapse;
            float inner = Math.max(0f, r - len);
            float dx = (float) Math.cos(starAngles[i]);
            float dy = (float) Math.sin(starAngles[i]);
            int alpha = (int) (255f * clamp(p * 1.5f) * visible * clamp(t / 0.4f));
            int gray = 155 + (int) (100 * p);
            paint.setColor(Color.argb(alpha, gray, gray, gray));
            paint.setStrokeWidth(1f + 2.7f * p);
            c.drawLine(cx + dx * inner, cy + dy * inner, cx + dx * r, cy + dy * r, paint);
        }
    }

    private void drawFlash(Canvas c, float t, float cx, float cy) {
        if (t < 2.45f || t > 4.25f) return;
        float intensity = smooth(2.45f, 3.15f, t) * (1f - smooth(3.35f, 4.25f, t));
        if (intensity <= 0f) return;
        c.save();
        c.translate(cx, cy);
        float scale = 0.12f + intensity * 1.05f;
        c.scale(scale, scale);
        glowPaint.setAlpha((int) (255 * clamp(intensity * 1.25f)));
        c.drawCircle(0, 0, maxRadius, glowPaint);
        c.restore();
    }

    private void drawParticles(Canvas c, float t, float cx, float cy) {
        if (t < 3.05f) return;
        float dt = t - 3.05f;
        float life = 1f - smooth(6.1f, 7.3f, t);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            float distance = particleSpeeds[i] * maxRadius * 0.58f * (1f - (float) Math.exp(-1.5f * dt));
            float x = cx + (float) Math.cos(particleAngles[i]) * distance;
            float y = cy + (float) Math.sin(particleAngles[i]) * distance;
            int alpha = (int) (255f * life * smooth(3.05f, 3.45f, t));
            paint.setColor(Color.argb(alpha, 235, 235, 240));
            c.drawCircle(x, y, particleSizes[i], paint);
        }
    }

    private void drawLogo(Canvas c, float t, float cx, float cy, int w, int h) {
        float reveal = smooth(3.2f, 4.15f, t);
        if (reveal <= 0f) return;
        // The logo block is explicitly constrained by both dimensions.
        float u = Math.min(h * 0.8f, w * 0.7f);
        float scale = 0.88f + 0.12f * smooth(3.2f, 4.6f, t);
        float radius = u * 0.2f;
        float ringWidth = u * 0.067f;
        float qCy = -u * 0.2f;

        c.save();
        c.translate(cx, cy + u * 0.03f);
        c.scale(scale, scale);
        logoPaint.setShader(new LinearGradient(0, qCy - radius, 0, qCy + radius,
                new int[]{Color.WHITE, Color.rgb(145, 145, 150), Color.WHITE, Color.rgb(105, 105, 110)},
                new float[]{0f, 0.45f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        logoPaint.setAlpha((int) (255 * reveal));
        logoPaint.setStyle(Paint.Style.STROKE);
        logoPaint.setStrokeCap(Paint.Cap.ROUND);
        logoPaint.setStrokeWidth(ringWidth);
        c.drawCircle(0, qCy, radius, logoPaint);
        // Thick diagonal Q tail at the lower right.
        logoPaint.setStrokeWidth(ringWidth * 1.12f);
        c.drawLine(radius * 0.12f, qCy + radius * 0.16f,
                radius * 1.02f, qCy + radius * 1.08f, logoPaint);

        textPaint.setShader(new LinearGradient(0, u * 0.02f, 0, u * 0.4f,
                new int[]{Color.WHITE, Color.rgb(135, 135, 140)}, null, Shader.TileMode.CLAMP));
        textPaint.setAlpha((int) (255 * reveal));
        drawFixedWidthText(c, "ORYN", u * 0.25f, u * 0.62f, 0.055f);
        drawFixedWidthText(c, "LAUNCHER", u * 0.42f, u * 0.62f, 0.025f);
        c.restore();
    }

    /** Left-aligned, character-by-character text with a fixed total line width. */
    private void drawFixedWidthText(Canvas c, String value, float baseline, float targetWidth, float tracking) {
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(100f);
        float measured = 0f;
        for (int i = 0; i < value.length(); i++) measured += textPaint.measureText(value, i, i + 1);
        float size = targetWidth / (measured / 100f + tracking * Math.max(0, value.length() - 1));
        textPaint.setTextSize(size);
        float x = -targetWidth / 2f;
        for (int i = 0; i < value.length(); i++) {
            c.drawText(value, i, i + 1, x, baseline, textPaint);
            x += textPaint.measureText(value, i, i + 1) + tracking * size;
        }
    }

    private void drawLensFlare(Canvas c, float t, float cx, float cy, int h) {
        if (t < 4.85f || t > 6.0f) return;
        float k = (t - 4.85f) / 1.15f;
        float intensity = (float) Math.sin(Math.PI * k);
        c.save();
        c.translate(cx - h * 0.38f + k * h * 0.76f, cy - h * 0.12f + k * h * 0.1f);
        c.scale(0.2f + 0.08f * intensity, 0.2f + 0.08f * intensity);
        glowPaint.setAlpha((int) (190 * intensity));
        c.drawCircle(0, 0, maxRadius, glowPaint);
        c.restore();
    }

    private void drawTunnel(Canvas c, float t, float cx, float cy) {
        if (t < 4.9f) return;
        float visibility = smooth(4.9f, 5.55f, t) * (1f - smooth(7.0f, 7.75f, t));
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        for (int ring = 0; ring < 9; ring++) {
            float p = fract(ring / 9f + (t - 4.9f) * 0.3f);
            float r = (float) Math.pow(p, 2.2) * maxRadius * 1.5f;
            if (r < 4f) continue;
            float rotation = (t - 4.9f) * 0.38f + ring * 0.06f;
            path.reset();
            for (int side = 0; side < 8; side++) {
                float angle = rotation + side * (float) Math.PI / 4f;
                float x = cx + (float) Math.cos(angle) * r;
                float y = cy + (float) Math.sin(angle) * r * 0.82f;
                if (side == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            path.close();
            int gray = 170 + (int) (85 * p);
            paint.setColor(Color.argb((int) (160 * visibility * (float) Math.sin(Math.PI * p)), gray, gray, gray));
            paint.setStrokeWidth(1.4f + 4f * p);
            c.drawPath(path, paint);
        }
    }
}
