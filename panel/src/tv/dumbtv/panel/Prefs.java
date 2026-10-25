package tv.dumbtv.panel;

import android.content.Context;
import android.content.SharedPreferences;

/** The three settings: the dashboard URL, the Home Assistant base URL and a long-lived token. */
final class Prefs {
    static final String URL = "url", HA = "ha", TOKEN = "token";

    private Prefs() {}

    static SharedPreferences of(Context c) {
        return c.getSharedPreferences("panel", Context.MODE_PRIVATE);
    }

    static String url(Context c) {
        return of(c).getString(URL, "");
    }

    /** The Home Assistant origin: the explicit setting, else the dashboard URL's origin. */
    static String ha(Context c) {
        String ha = of(c).getString(HA, "");
        if (!ha.isEmpty()) return trim(ha);
        String url = url(c);
        int scheme = url.indexOf("://");
        if (scheme < 0) return "";
        int slash = url.indexOf('/', scheme + 3);
        return slash < 0 ? url : url.substring(0, slash);
    }

    static String token(Context c) {
        return of(c).getString(TOKEN, "");
    }

    static boolean ready(Context c) {
        return !url(c).isEmpty();
    }

    private static String trim(String s) {
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }
}
