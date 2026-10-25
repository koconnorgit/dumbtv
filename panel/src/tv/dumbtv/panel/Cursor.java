package tv.dumbtv.panel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;

/**
 * A pointer driven by the D-pad, drawn over the WebView. OK presses become touches at the
 * pointer, so a dashboard built for fingers works from the couch: a short press taps, holding
 * OK long-presses. Pushing against a screen edge scrolls the page instead.
 */
final class Cursor extends View {
    private static final long HIDE_AFTER_MS = 5000;

    private final WebView web;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private final float size;
    private final Runnable hide = () -> {
        shown = false;
        invalidate();
    };
    private float x, y;
    private boolean shown, pressed;
    private long downAt;

    Cursor(Context c, WebView web) {
        super(c);
        this.web = web;
        size = dp(22);
        fill.setColor(0xFFFFFFFF);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(dp(1.5f));
        edge.setColor(0xFF000000);
        fill.setShadowLayer(dp(4), 0, dp(1), 0x80000000);
        setFocusable(false);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        if (oldW == 0) {
            x = w / 2f;
            y = h / 2f;
        }
    }

    /** True when the key was the cursor's to handle. */
    boolean key(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) move(code, e.getRepeatCount());
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                if (down && e.getRepeatCount() == 0) press();
                else if (!down) release();
                return true;
            default:
                return false;
        }
    }

    private void move(int code, int repeat) {
        float step = dp(10) * (1f + Math.min(repeat, 24) / 4f);
        float nx = x, ny = y;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: ny -= step; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: ny += step; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: nx -= step; break;
            default: nx += step;
        }
        int w = getWidth(), h = getHeight();
        // Against an edge the page scrolls, so the pointer can reach everything below the fold.
        if (ny < 0 || ny > h) web.scrollBy(0, Math.round(ny < 0 ? -step * 2 : step * 2));
        if (nx < 0 || nx > w) web.scrollBy(Math.round(nx < 0 ? -step * 2 : step * 2), 0);
        x = Math.max(0, Math.min(w, nx));
        y = Math.max(0, Math.min(h, ny));
        if (pressed) touch(MotionEvent.ACTION_MOVE);
        show();
    }

    private void press() {
        pressed = true;
        downAt = SystemClock.uptimeMillis();
        touch(MotionEvent.ACTION_DOWN);
        show();
    }

    private void release() {
        if (!pressed) return;
        pressed = false;
        touch(MotionEvent.ACTION_UP);
        show();
    }

    private void touch(int action) {
        long now = SystemClock.uptimeMillis();
        MotionEvent ev = MotionEvent.obtain(downAt, now, action, x, y, 0);
        ev.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        web.dispatchTouchEvent(ev);
        ev.recycle();
    }

    private void show() {
        shown = true;
        removeCallbacks(hide);
        postDelayed(hide, HIDE_AFTER_MS);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!shown) return;
        float s = pressed ? size * 0.85f : size;
        arrow.reset();
        arrow.moveTo(x, y);
        arrow.lineTo(x, y + s);
        arrow.lineTo(x + s * 0.27f, y + s * 0.76f);
        arrow.lineTo(x + s * 0.47f, y + s * 1.1f);
        arrow.lineTo(x + s * 0.62f, y + s * 1.02f);
        arrow.lineTo(x + s * 0.42f, y + s * 0.68f);
        arrow.lineTo(x + s * 0.72f, y + s * 0.68f);
        arrow.close();
        canvas.drawPath(arrow, fill);
        canvas.drawPath(arrow, edge);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
