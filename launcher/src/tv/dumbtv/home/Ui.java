package tv.dumbtv.home;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.widget.TextView;

final class Ui {
    static final int TEXT = 0xFFF2F2F3;
    static final int MUTED = 0x99FFFFFF;
    static final int FAINT = 0x66FFFFFF;
    static final int SURFACE = 0xFF1C1D21;
    static final int DIALOG = android.R.style.Theme_DeviceDefault_Dialog_Alert;
    /**
     * Fire OS ships Amazon Ember and names its families in fonts.xml, so the home screen can
     * use the TV's own face; an Android TV box falls back to Roboto through the sans-serif alias.
     */
    static final Typeface LIGHT = family("amazon-ember-light", "sans-serif-light");
    static final Typeface MEDIUM = family("amazon-ember-medium", "sans-serif-medium");
    static final Typeface DISPLAY = family("amazon-ember-display-light", "sans-serif-light");

    private Ui() {}

    /** The named family, or the fallback alias when the device does not know the name. */
    private static Typeface family(String name, String fallback) {
        Typeface t = Typeface.create(name, Typeface.NORMAL);
        return t == null || t.equals(Typeface.DEFAULT) ? Typeface.create(fallback, Typeface.NORMAL) : t;
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** Black on light accents, white on dark ones. */
    static int ink(int color) {
        return Color.luminance(color) > 0.5f ? 0xFF000000 : 0xFFFFFFFF;
    }

    static TextView text(Context c, float sp, int color, Typeface face) {
        TextView v = new TextView(c);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        v.setTypeface(face);
        v.setIncludeFontPadding(false);
        return v;
    }

    /** A soft drop shadow that keeps text readable over a picture. */
    static void shadow(TextView v) {
        v.setShadowLayer(dp(v.getContext(), 7), 0, dp(v.getContext(), 1), 0x99000000);
    }

    static GradientDrawable round(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    /** Accent fill when focused, a faint fill otherwise. */
    static void focusStyle(TextView v, int accent, int idle, float radius) {
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, round(accent, radius));
        bg.addState(new int[0], round(idle, radius));
        v.setBackground(bg);
        v.setTextColor(new ColorStateList(
                new int[][] {{android.R.attr.state_focused}, {}},
                new int[] {ink(accent), 0xD9FFFFFF}));
    }
}
