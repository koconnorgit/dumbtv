package tv.dumbtv.home;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * Darkens a picture behind the home screen: a band at the top for the clock, clear in the
 * middle where the picture shows, and deep at the bottom where the rows sit. {@code strength}
 * 1 is the Options default (Image dim 40%); the dim setting scales every band.
 */
final class Scrim extends Drawable {
    private static final float[] AT = {0f, 0.26f, 0.44f, 0.62f, 1f};
    private static final float[] ALPHA = {0.28f, 0f, 0f, 0.58f, 0.93f};

    private final Paint paint = new Paint();
    private final int[] colors = new int[AT.length];

    Scrim(float strength) {
        for (int i = 0; i < AT.length; i++) {
            colors[i] = Color.argb(Math.round(255 * Math.min(1f, ALPHA[i] * strength)), 0, 0, 0);
        }
    }

    @Override
    protected void onBoundsChange(Rect b) {
        if (b.height() == 0) return;
        paint.setShader(new LinearGradient(0, b.top, 0, b.bottom, colors, AT, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas canvas) {
        if (paint.getShader() != null) canvas.drawRect(getBounds(), paint);
    }

    @Override
    public void setAlpha(int alpha) {}

    @Override
    public void setColorFilter(ColorFilter filter) {}

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
