package tv.dumbtv.panel;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * One full-screen WebView showing a Home Assistant dashboard, with a D-pad cursor on top.
 *
 * Settings arrive as intent extras, so they can be set from a PC without typing on the TV:
 *   adb shell am start -n tv.dumbtv.panel/.Panel --es url http://ha:8123/dashboard-tv \
 *       --es token eyJ...
 * The token is a long-lived access token (Home Assistant profile, Security). With one, the
 * app signs the dashboard in by itself; without one, the dashboard shows HA's login page and
 * the WebView remembers the session afterwards.
 */
public final class Panel extends Activity {
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;

    private WebView web;
    private Cursor cursor;
    private TextView help;
    private String signedInFor;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        web.setBackgroundColor(Color.BLACK);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return false;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                signIn(url);
            }
        });
        root.addView(web, new FrameLayout.LayoutParams(MATCH, MATCH));
        cursor = new Cursor(this, web);
        root.addView(cursor, new FrameLayout.LayoutParams(MATCH, MATCH));
        help = new TextView(this);
        help.setTextColor(0xFFF2F2F3);
        help.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        help.setLineSpacing(0, 1.4f);
        help.setGravity(Gravity.CENTER);
        help.setPadding(dp(80), dp(40), dp(80), dp(40));
        root.addView(help, new FrameLayout.LayoutParams(MATCH, MATCH));
        setContentView(root);
        take(getIntent());
        Alerts.start(this);
        load();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (take(i)) load();
    }

    /** Saves any settings carried by {@code i}; true when something changed. */
    private boolean take(Intent i) {
        if (i == null) return false;
        boolean changed = false;
        for (String key : new String[] {Prefs.URL, Prefs.HA, Prefs.TOKEN}) {
            String v = i.getStringExtra(key);
            if (v == null) continue;
            Prefs.of(this).edit().putString(key, v.trim()).apply();
            changed = true;
        }
        if (i.getBooleanExtra("clear", false)) {
            Prefs.of(this).edit().clear().apply();
            web.clearCache(true);
            changed = true;
        }
        if (changed) {
            signedInFor = null;
            Alerts.restart(this);
        }
        return changed;
    }

    private void load() {
        if (!Prefs.ready(this)) {
            help.setText("No dashboard set.\n\nFrom a computer on the same network:\n\n"
                    + "adb shell am start -n tv.dumbtv.panel/.Panel "
                    + "--es url http://<home-assistant>:8123/<dashboard> --es token <long-lived token>\n\n"
                    + "The token is optional: without it the dashboard asks you to sign in once.");
            help.setVisibility(TextView.VISIBLE);
            return;
        }
        help.setVisibility(TextView.GONE);
        web.loadUrl(Prefs.url(this));
    }

    /**
     * Home Assistant's frontend keeps its session in localStorage under "hassTokens". When it
     * finds none it shows the login page; at that point the long-lived token is stored in the
     * same shape and the dashboard is loaded again, now signed in.
     */
    private void signIn(String url) {
        String token = Prefs.token(this), ha = Prefs.ha(this);
        if (token.isEmpty() || url == null || !url.contains("/auth/authorize")) return;
        if (ha.equals(signedInFor)) return;
        signedInFor = ha;
        JSONObject t = new JSONObject();
        try {
            t.put("hassUrl", ha);
            t.put("clientId", ha + "/");
            t.put("access_token", token);
            t.put("token_type", "Bearer");
            t.put("expires_in", 315360000);
            t.put("expires", System.currentTimeMillis() + 315360000L * 1000);
            t.put("refresh_token", "");
        } catch (JSONException e) {
            return;
        }
        web.evaluateJavascript("localStorage.setItem('hassTokens', " + JSONObject.quote(t.toString()) + ")",
                v -> web.loadUrl(Prefs.url(this)));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (Prefs.ready(this) && cursor.key(e)) return true;
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_MENU:
                    load();
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    if (web.canGoBack() && !web.getUrl().equals(Prefs.url(this))) web.goBack();
                    else finish();
                    return true;
                default:
            }
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
