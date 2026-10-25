package tv.dumbtv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.media.tv.TvInputManager;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewOutlineProvider;

/**
 * A card drawn in one pass. An app is its banner, or icon plus name, with a white ring and its
 * name underneath on focus. An input is a pill with its name and a green dot when something
 * is connected; focus fills it with the accent.
 */
final class Tile extends View {
    private static final float FOCUS_SCALE = 1.08f;

    final Item item;
    final String kind;
    private final CharSequence title;
    private final int cardW, cardH, labelH, accent;
    private final boolean app, labelAlways, labelOnFocus, connected;
    private final float radius, stroke, pad, dotR, elevation;
    private final RectF card = new RectF();
    private final Path clip = new Path();
    private final Path arrow = new Path();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint inner = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint under = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private boolean moving;

    Tile(Context c, Item item, String kind, CharSequence title, int cardW, int cardH,
            String labels, int accent) {
        super(c);
        this.item = item;
        this.kind = kind;
        this.title = title;
        this.accent = accent;
        app = Catalog.APPS.equals(kind);
        connected = item.state == TvInputManager.INPUT_STATE_CONNECTED;
        labelH = app ? Ui.dp(c, 26) : 0;
        labelAlways = "always".equals(labels);
        labelOnFocus = "focus".equals(labels);
        stroke = Ui.dp(c, 2);
        pad = Ui.dp(c, 16);
        dotR = Ui.dp(c, 3);
        elevation = Ui.dp(c, 6);

        fill.setColor(Ui.SURFACE);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(stroke);
        ring.setColor(0xFFFFFFFF);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Ui.dp(c, 1));
        edge.setColor(0x33FFFFFF);
        dot.setColor(0xFF34C759);
        inner.setColor(Ui.TEXT);
        inner.setTypeface(Ui.MEDIUM);
        inner.setTextAlign(Paint.Align.CENTER);
        inner.setTextSize(Ui.dp(c, app ? 14 : 13.5f));
        under.setColor(Ui.TEXT);
        under.setTypeface(Ui.MEDIUM);
        under.setTextAlign(Paint.Align.CENTER);
        under.setTextSize(Ui.dp(c, 13));
        under.setShadowLayer(Ui.dp(c, 5), 0, Ui.dp(c, 1), 0xB3000000);

        if (app) {
            this.cardW = cardW;
            this.cardH = cardH;
            radius = Ui.dp(c, 10);
        } else {
            // A pill as wide as its name, capped at an app card's width.
            float lead = connected ? dotR * 2 + Ui.dp(c, 7) : 0;
            this.cardH = Ui.dp(c, 31);
            this.cardW = (int) Math.min(cardW, Math.ceil(pad * 2 + lead + inner.measureText(title, 0, title.length())));
            radius = this.cardH / 2f;
        }

        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        setContentDescription(title);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, Tile.this.cardW, Tile.this.cardH, radius);
            }
        });
        setElevation(elevation);
    }

    void setMoving(boolean on) {
        moving = on;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(cardW, cardH + labelH);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        card.set(0, 0, cardW, cardH);
        clip.reset();
        clip.addRoundRect(card, radius, radius, Path.Direction.CW);
        setPivotX(cardW / 2f);
        setPivotY(cardH / 2f);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        float s = focused ? FOCUS_SCALE : 1f;
        animate().scaleX(s).scaleY(s).setDuration(110).start();
        setElevation(focused ? elevation * 2 : elevation);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!app) {
            drawPill(canvas);
            return;
        }
        Drawable banner = item.banner, icon = item.icon;
        canvas.save();
        canvas.clipPath(clip);
        canvas.drawRect(card, fill);
        if (banner != null) {
            banner.setBounds(0, 0, cardW, cardH);
            banner.draw(canvas);
        } else if (icon != null) {
            int size = cardH * 46 / 100;
            int left = (cardW - size) / 2, top = cardH * 12 / 100;
            icon.setBounds(left, top, left + size, top + size);
            icon.draw(canvas);
            drawLine(canvas, inner, cardW / 2f, cardH - cardH * 13 / 100f, cardW - pad);
        }
        if (moving) canvas.drawColor(0x99000000);
        canvas.restore();

        if (isFocused() || moving) {
            float half = stroke / 2;
            ring.setPathEffect(moving ? new DashPathEffect(new float[] {stroke * 3, stroke * 2}, 0) : null);
            canvas.drawRoundRect(half, half, cardW - half, cardH - half, radius - half, radius - half, ring);
        }
        if (moving) drawArrows(canvas);
        boolean label = banner != null && (labelAlways || labelOnFocus && isFocused());
        if (label) {
            drawLine(canvas, under, cardW / 2f, cardH + labelH - Ui.dp(getContext(), 6), cardW - pad);
        }
    }

    private void drawPill(Canvas canvas) {
        boolean hot = isFocused();
        fill.setColor(hot ? accent : 0x1FFFFFFF);
        canvas.drawRoundRect(card, radius, radius, fill);
        if (!hot) {
            float half = edge.getStrokeWidth() / 2;
            canvas.drawRoundRect(half, half, cardW - half, cardH - half, radius - half, radius - half, edge);
        }
        float lead = connected ? dotR * 2 + Ui.dp(getContext(), 7) : 0;
        if (connected) canvas.drawCircle(pad + dotR, cardH / 2f, dotR, dot);
        inner.setColor(hot ? Ui.ink(accent) : Ui.TEXT);
        float baseline = cardH / 2f - (inner.ascent() + inner.descent()) / 2;
        drawLine(canvas, inner, (cardW + lead) / 2f, baseline, cardW - pad * 2 - lead);
        if (moving) {
            canvas.drawRoundRect(card, radius, radius, shadeOf(0x99000000));
            float half = stroke / 2;
            ring.setPathEffect(new DashPathEffect(new float[] {stroke * 3, stroke * 2}, 0));
            canvas.drawRoundRect(half, half, cardW - half, cardH - half, radius - half, radius - half, ring);
        }
    }

    private Paint shadeOf(int color) {
        fill.setColor(color);
        return fill;
    }

    private void drawLine(Canvas canvas, TextPaint paint, float x, float baseline, float room) {
        CharSequence s = TextUtils.ellipsize(title, paint, room, TextUtils.TruncateAt.END);
        canvas.drawText(s, 0, s.length(), x, baseline, paint);
    }

    private void drawArrows(Canvas canvas) {
        float h = cardH / 6f, w = h * 0.7f, mid = cardH / 2f, pad = h * 0.8f;
        inner.setStyle(Paint.Style.FILL);
        arrow.reset();
        arrow.moveTo(pad, mid);
        arrow.lineTo(pad + w, mid - h / 2);
        arrow.lineTo(pad + w, mid + h / 2);
        arrow.close();
        arrow.moveTo(cardW - pad, mid);
        arrow.lineTo(cardW - pad - w, mid - h / 2);
        arrow.lineTo(cardW - pad - w, mid + h / 2);
        arrow.close();
        canvas.drawPath(arrow, inner);
    }
}
