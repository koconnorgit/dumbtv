package tv.dumbtv.panel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Keeps one websocket open to Home Assistant and shows a camera in the corner of the screen,
 * over whatever is playing, when an automation fires the {@code dumbtv_popup} event:
 *
 *   event: dumbtv_popup
 *   event_data:
 *     camera: camera.front_doorbell      # shown as snapshots, a few frames a second
 *     title: Front door                  # optional, defaults to the camera's name
 *     seconds: 20                        # optional, how long it stays
 *
 * A camera shows its snapshot at once, refreshed a few times a second, while Home Assistant's
 * stream integration spins up; the signed HLS stream it hands back plays through Android's own
 * MediaPlayer and replaces the snapshot on the first rendered frame (muted). The stream is
 * requested the moment the event arrives, so the player finds the first segment ready.
 *
 * {@code url} instead of {@code camera} shows any page, and {@code dismiss: true} hides it.
 * Only outbound: nothing listens on the TV. For a test from a PC:
 *   adb shell am broadcast -n tv.dumbtv.panel/.Boot -a tv.dumbtv.panel.POPUP --es url ...
 */
public final class Alerts extends Service {
    static final String TAG = "dumbtv.panel";
    static final String ACTION_POPUP = "tv.dumbtv.panel.POPUP";
    static final String ACTION_RESTART = "tv.dumbtv.panel.RESTART";
    private static final String EVENT = "dumbtv_popup";
    private static final String CHANNEL = "alerts";
    private static final int PING_MS = 30000;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable hide = this::hide;
    private Thread link;
    private volatile boolean running;
    private FrameLayout popup;
    private WebView view;
    private TextureView video;
    private TextView title;
    private MediaPlayer player;
    private Surface surface;
    private String pendingStream;
    private int generation;

    static void start(Context c) {
        c.startForegroundService(new Intent(c, Alerts.class));
    }

    static void restart(Context c) {
        c.startForegroundService(new Intent(c, Alerts.class).setAction(ACTION_RESTART));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Alerts", NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle("Home Assistant alerts")
                .setContentText("Listening for motion popups")
                .build();
        startForeground(1, n);
    }

    @Override
    public int onStartCommand(Intent i, int flags, int id) {
        String action = i == null ? null : i.getAction();
        if (ACTION_POPUP.equals(action)) {
            JSONObject data = new JSONObject();
            try {
                for (String k : new String[] {"url", "stream", "camera", "title", "seconds"}) {
                    if (i.hasExtra(k)) data.put(k, i.getStringExtra(k));
                }
                if (i.getBooleanExtra("dismiss", false)) data.put("dismiss", true);
            } catch (JSONException ignored) {
                // only strings go in
            }
            handle(data);
            return START_STICKY;
        }
        if (ACTION_RESTART.equals(action)) stopLink();
        startLink();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopLink();
        hide();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    private void startLink() {
        if (link != null && link.isAlive()) return;
        String ha = Prefs.ha(this), token = Prefs.token(this);
        if (ha.isEmpty() || token.isEmpty()) {
            Log.i(TAG, "no Home Assistant URL or token yet; popups only from adb");
            return;
        }
        running = true;
        link = new Thread(() -> loop(ha, token), "ha-link");
        link.start();
    }

    private void stopLink() {
        running = false;
        if (link != null) link.interrupt();
        link = null;
    }

    private void loop(String ha, String token) {
        String url = ha.replaceFirst("^http", "ws") + "/api/websocket";
        long backoff = 2000;
        while (running) {
            Ws ws = null;
            try {
                ws = Ws.open(url, 15000);
                expect(ws.text(), "auth_required");
                ws.send(new JSONObject().put("type", "auth").put("access_token", token).toString());
                expect(ws.text(), "auth_ok");
                ws.send(new JSONObject().put("id", 1).put("type", "subscribe_events").put("event_type", EVENT).toString());
                Log.i(TAG, "listening for " + EVENT + " on " + ha);
                backoff = 2000;
                ws.setTimeout(PING_MS);
                int id = 2, silent = 0;
                while (running) {
                    String msg;
                    try {
                        msg = ws.text();
                    } catch (java.net.SocketTimeoutException e) {
                        if (++silent > 2) throw new IOException("no answer to pings");
                        ws.send(new JSONObject().put("id", id++).put("type", "ping").toString());
                        continue;
                    }
                    silent = 0;
                    JSONObject o = new JSONObject(msg);
                    if (!"event".equals(o.optString("type"))) continue;
                    JSONObject data = o.getJSONObject("event").optJSONObject("data");
                    ui.post(() -> handle(data == null ? new JSONObject() : data));
                }
            } catch (IOException | JSONException e) {
                Log.w(TAG, "link: " + e);
            } finally {
                if (ws != null) ws.close();
            }
            if (!running) break;
            try {
                Thread.sleep(backoff);
            } catch (InterruptedException e) {
                break;
            }
            backoff = Math.min(backoff * 2, 60000);
        }
    }

    private static void expect(String msg, String type) throws IOException, JSONException {
        String got = new JSONObject(msg).optString("type");
        if (!type.equals(got)) throw new IOException("expected " + type + ", got " + msg);
    }

    /** Runs on the UI thread. Resolves a camera to its stream URL off it, then shows. */
    private void handle(JSONObject data) {
        if (data.optBoolean("dismiss")) {
            hide();
            return;
        }
        String url = data.optString("url", "");
        String stream = data.optString("stream", "");
        String camera = data.optString("camera", "");
        String name = data.optString("title", "");
        int seconds = data.optInt("seconds", 20);
        if (!stream.isEmpty()) {
            // A stream Android's MediaPlayer can play directly (HLS, RTSP, MP4), e.g. from go2rtc.
            show(url, name, seconds);
            play(stream);
            return;
        }
        if (!url.isEmpty()) {
            show(url, name, seconds);
            return;
        }
        if (camera.isEmpty()) return;
        String ha = Prefs.ha(this), token = Prefs.token(this);
        int gen = ++generation;
        new Thread(() -> {
            try {
                JSONObject state = new JSONObject(get(ha + "/api/states/" + camera, token));
                JSONObject attrs = state.getJSONObject("attributes");
                String access = attrs.optString("access_token", "");
                String label = name.isEmpty() ? attrs.optString("friendly_name", camera) : name;
                String snapshot = ha + "/api/camera_proxy/" + camera + "?token=" + access;
                ui.post(() -> show(snapshot, label, seconds));
            } catch (IOException | JSONException e) {
                Log.w(TAG, "camera " + camera + ": " + e);
                return;
            }
            try {
                String path = stream(ha, token, camera);
                Log.i(TAG, "stream ready for " + camera);
                ui.post(() -> {
                    if (gen == generation && popup != null && popup.getVisibility() == View.VISIBLE) play(ha + path);
                });
            } catch (IOException | JSONException e) {
                Log.w(TAG, "stream " + camera + ": " + e + " (snapshot stays)");
            }
        }, "camera").start();
    }

    /** The signed HLS path for {@code camera}, which only the websocket API hands out. */
    private static String stream(String ha, String token, String camera) throws IOException, JSONException {
        Ws ws = Ws.open(ha.replaceFirst("^http", "ws") + "/api/websocket", 10000);
        try {
            expect(ws.text(), "auth_required");
            ws.send(new JSONObject().put("type", "auth").put("access_token", token).toString());
            expect(ws.text(), "auth_ok");
            ws.send(new JSONObject().put("id", 1).put("type", "camera/stream")
                    .put("entity_id", camera).put("format", "hls").toString());
            while (true) {
                JSONObject o = new JSONObject(ws.text());
                if (!"result".equals(o.optString("type"))) continue;
                if (!o.optBoolean("success")) throw new IOException("camera/stream refused: " + o.optJSONObject("error"));
                return o.getJSONObject("result").getString("url");
            }
        } finally {
            ws.close();
        }
    }

    private static String get(String url, String token) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(5000);
        c.setRequestProperty("Authorization", "Bearer " + token);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder b = new StringBuilder();
            for (String line; (line = r.readLine()) != null; ) b.append(line);
            return b.toString();
        } finally {
            c.disconnect();
        }
    }

    private void show(String url, String label, int seconds) {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "no overlay permission: adb shell appops set tv.dumbtv.panel SYSTEM_ALERT_WINDOW allow");
            return;
        }
        WindowManager wm = getSystemService(WindowManager.class);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int w = screenW * 36 / 100, h = w * 9 / 16;
        if (popup == null) {
            popup = new FrameLayout(this);
            popup.setBackgroundColor(Color.BLACK);
            popup.setClipToOutline(true);
            popup.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(14));
                }
            });
            popup.setElevation(dp(12));
            view = new WebView(this);
            view.getSettings().setJavaScriptEnabled(true);
            view.getSettings().setMediaPlaybackRequiresUserGesture(false);
            view.getSettings().setLoadWithOverviewMode(true);
            view.getSettings().setUseWideViewPort(true);
            view.setBackgroundColor(Color.BLACK);
            view.setWebChromeClient(new WebChromeClient() {
                @Override
                public boolean onConsoleMessage(ConsoleMessage m) {
                    Log.i(TAG, "popup page: " + m.message());
                    return true;
                }
            });
            // The video sits behind the snapshot: a TextureView only gets its surface once it is
            // drawn, so it must stay visible, and the WebView on top hides it until the first frame.
            video = new TextureView(this);
            video.setOpaque(false);
            video.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture t, int w, int h) {
                    surface = new Surface(t);
                    if (pendingStream != null) play(pendingStream);
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture t, int w, int h) {}

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture t) {
                    stopVideo();
                    if (surface != null) surface.release();
                    surface = null;
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture t) {}
            });
            popup.addView(video, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            popup.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            title = new TextView(this);
            title.setTextColor(0xFFFFFFFF);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            title.setShadowLayer(dp(6), 0, dp(1), 0xCC000000);
            title.setPadding(dp(14), dp(22), dp(14), dp(10));
            GradientDrawable scrim = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] {0x00000000, 0xB3000000});
            title.setBackground(scrim);
            FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
            popup.addView(title, tl);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.x = dp(40);
            lp.y = dp(40);
            wm.addView(popup, lp);
        }
        title.setText(label);
        stopVideo();
        view.setVisibility(View.VISIBLE);
        if (url.isEmpty()) {
            view.loadUrl("about:blank");
        } else if (url.contains("/api/camera_proxy/")) {
            // Amazon's WebView draws nothing for an MJPEG stream, so the box polls snapshots:
            // the next one is requested as soon as the last has drawn, a few frames a second.
            String html = "<html><body style='margin:0;background:#000;overflow:hidden'>"
                    + "<img id=c style='width:100vw;height:100vh;object-fit:cover'>"
                    + "<script>var u=" + JSONObject.quote(url) + ",i=document.getElementById('c'),n=0;"
                    + "function f(){i.src=u+'&t='+(n++)}i.onload=function(){setTimeout(f,150)};"
                    + "i.onerror=function(){setTimeout(f,1000)};f()</script></body></html>";
            view.loadDataWithBaseURL(Prefs.ha(this), html, "text/html", "utf-8", null);
        } else {
            view.loadUrl(url);
        }
        popup.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hide);
        ui.postDelayed(hide, Math.max(3, seconds) * 1000L);
    }

    private void hide() {
        ui.removeCallbacks(hide);
        generation++;
        if (popup == null) return;
        stopVideo();
        view.loadUrl("about:blank");
        popup.setVisibility(View.GONE);
    }

    /** Starts the stream behind the snapshot; the snapshot gives way on the first drawn frame. */
    private void play(String url) {
        stopVideo();
        if (surface == null) {
            pendingStream = url;
            return;
        }
        pendingStream = null;
        MediaPlayer p = new MediaPlayer();
        player = p;
        p.setSurface(surface);
        p.setVolume(0f, 0f);
        p.setOnPreparedListener(mp -> {
            if (mp == player) mp.start();
        });
        p.setOnVideoSizeChangedListener((mp, w, h) -> {
            if (mp == player) fit(w, h);
        });
        p.setOnInfoListener((mp, what, extra) -> {
            if (mp == player && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                Log.i(TAG, "video: first frame " + mp.getVideoWidth() + "x" + mp.getVideoHeight());
                view.loadUrl("about:blank");
                view.setVisibility(View.INVISIBLE);
            }
            return true;
        });
        p.setOnErrorListener((mp, what, extra) -> {
            Log.w(TAG, "video: error " + what + "/" + extra + " (snapshot stays)");
            if (mp == player) stopVideo();
            return true;
        });
        try {
            p.setDataSource(url);
            p.prepareAsync();
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            Log.w(TAG, "video: " + e);
            stopVideo();
        }
    }

    private void stopVideo() {
        pendingStream = null;
        if (player == null) return;
        MediaPlayer p = player;
        player = null;
        try {
            p.reset();
        } catch (IllegalStateException ignored) {
            // releasing anyway
        }
        p.release();
    }

    /** Scales the picture to fill the box and crop, like the snapshot's object-fit: cover. */
    private void fit(int w, int h) {
        int bw = video.getWidth(), bh = video.getHeight();
        if (w == 0 || h == 0 || bw == 0 || bh == 0) return;
        float scale = Math.max(bw / (float) w, bh / (float) h);
        float sx = w * scale / bw, sy = h * scale / bh;
        Matrix m = new Matrix();
        m.setScale(sx, sy, bw / 2f, bh / 2f);
        video.setTransform(m);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
