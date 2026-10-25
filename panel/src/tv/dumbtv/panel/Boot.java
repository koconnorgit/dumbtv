package tv.dumbtv.panel;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Starts the alert service at boot, and relays a popup request sent from a PC for testing:
 *   adb shell am broadcast -n tv.dumbtv.panel/.Boot -a tv.dumbtv.panel.POPUP --es camera camera.x
 * (the service itself is not exported, so a broadcast is the only way in from outside).
 */
public final class Boot extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (Alerts.ACTION_POPUP.equals(i.getAction())) {
            Intent s = new Intent(c, Alerts.class).setAction(Alerts.ACTION_POPUP);
            if (i.getExtras() != null) s.putExtras(i.getExtras());
            c.startForegroundService(s);
            return;
        }
        Alerts.start(c);
    }
}
