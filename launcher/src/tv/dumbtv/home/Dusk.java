package tv.dumbtv.home;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * A built-in wallpaper drawn from gradients: a dusk sky with a warm glow high on the right and
 * cool and violet pools lower down. Costs no file and no bytes in the APK; the GPU evaluates
 * the four shaders once per frame of the (static) home screen.
 */
final class Dusk extends Drawable {
    private static final int[] SKY = {0xFF17213D, 0xFF2C3F6B, 0xFF6D4A70, 0xFF241A2E};
    private static final float[] SKY_AT = {0f, 0.35f, 0.62f, 1f};
    /** x, y (fractions of the screen), rx, ry (fractions of width and height), color. */
    private static final float[][] GLOWS = {
        {0.72f, 0.18f, 0.39f, 0.42f},
        {0.15f, 0.55f, 0.34f, 0.46f},
        {0.92f, 0.85f, 0.26f, 0.37f},
    };
    private static final int[] GLOW_COLORS = {0x99FFAA64, 0x9E3C96E6, 0x80A050E6};

    private final Paint sky = new Paint();
    private final Paint[] glows = new Paint[GLOWS.length];

    Dusk() {
        for (int i = 0; i < glows.length; i++) glows[i] = new Paint();
    }

    @Override
    protected void onBoundsChange(Rect b) {
        int w = b.width(), h = b.height();
        if (w == 0 || h == 0) return;
        sky.setShader(new LinearGradient(0, b.top, 0, b.bottom, SKY, SKY_AT, Shader.TileMode.CLAMP));
        for (int i = 0; i < GLOWS.length; i++) {
            float cx = b.left + GLOWS[i][0] * w, cy = b.top + GLOWS[i][1] * h;
            float rx = GLOWS[i][2] * w, ry = GLOWS[i][3] * h;
            int color = GLOW_COLORS[i];
            // An eased falloff: a plain two-stop gradient reads as a spotlight with an edge.
            Shader s = new RadialGradient(cx, cy, rx,
                    new int[] {color, fade(color, 0.6f), fade(color, 0.22f), color & 0x00FFFFFF},
                    new float[] {0f, 0.3f, 0.6f, 1f}, Shader.TileMode.CLAMP);
            Matrix m = new Matrix();
            m.setScale(1f, ry / rx, cx, cy);
            s.setLocalMatrix(m);
            glows[i].setShader(s);
        }
    }

    private static int fade(int color, float by) {
        return (Math.round((color >>> 24) * by) << 24) | (color & 0x00FFFFFF);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (sky.getShader() == null) return;
        canvas.drawRect(b, sky);
        for (Paint p : glows) canvas.drawRect(b, p);
    }

    @Override
    public void setAlpha(int alpha) {}

    @Override
    public void setColorFilter(ColorFilter filter) {}

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
