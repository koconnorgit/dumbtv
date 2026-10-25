# dumbtv panel

A Home Assistant dashboard on the TV, and a camera popup when something moves. 25 KB, plain
Java and one WebView, no libraries.

- **Dashboard.** The app opens one Home Assistant dashboard full screen. A pointer drawn over
  the page follows the D-pad; OK taps where it is, holding OK long-presses, and pushing against
  a screen edge scrolls. Menu reloads, Back goes back in the page or to the home screen.
- **Popups.** A small service keeps one outbound websocket to Home Assistant and, when an
  automation fires the `dumbtv_popup` event, draws the camera in the top-right corner over
  whatever is playing, Netflix and HDMI inputs included, for twenty seconds. Nothing listens
  on the TV.
- **Permissions.** INTERNET (the dashboard is on the network), draw-over-other-apps (the popup),
  foreground service and boot (so the listener survives). This is the one app in the repo that
  talks to the network, and it only talks to the Home Assistant address you give it.

## Install

Build it next to the launcher (same toolchain, same signing key):

```sh
./panel/build.sh
adb install -r panel/build/dumbtv-panel.apk
adb shell appops set tv.dumbtv.panel SYSTEM_ALERT_WINDOW allow   # Fire OS has no settings page for this
```

Then tell it where Home Assistant is. The token is a long-lived access token from your Home
Assistant profile (Security tab); with it the dashboard signs in by itself and the popup
listener can connect. Without it the dashboard shows the login page once and remembers the
session, but popups stay off.

```sh
adb shell am start -n tv.dumbtv.panel/.Panel \
    --es url http://<home-assistant>:8123/<dashboard> \
    --es token <long-lived token>
```

`--ez clear true` forgets everything. The settings live in the app's private storage.

It shows up in dumbtv home as "Home Assistant" like any other app.

## The automation

Any automation can show a popup by firing an event:

```yaml
actions:
  - event: dumbtv_popup
    event_data:
      camera: camera.front_doorbell      # shown as snapshots, a few frames a second
      title: Front door                  # optional
      seconds: 20                        # optional
```

`url` instead of `camera` shows any page in the box, `stream` plays a URL through Android's
MediaPlayer (RTSP, or HLS with MPEG-TS segments, for example from MediaMTX or go2rtc) over
the snapshot once its first frame is up, and `dismiss: true` hides the box early. To try it
without Home Assistant:

```sh
adb shell am broadcast -n tv.dumbtv.panel/.Boot -a tv.dumbtv.panel.POPUP \
    --es camera camera.front_doorbell --es seconds 15
```

## Limits

- The TV must be awake. In standby it is off the network, so there is nothing to wake it.
- The camera box polls Home Assistant's snapshot endpoint a few times a second, a flipbook
  rather than video, a second or so behind. Good enough for "someone is at the door". Real
  video from Home Assistant itself is not available on a Fire TV: Amazon's WebView draws
  nothing for an MJPEG stream and has Media Source Extensions switched off, and Android's
  MediaPlayer rejects Home Assistant's HLS because its segments are fragmented MP4. The app
  still asks Home Assistant for the HLS stream and tries it, so a TV whose player copes gets
  live video automatically; otherwise the snapshot stays. A plain RTSP or MPEG-TS HLS stream
  (`stream:` in the event, from MediaMTX, go2rtc or the camera) does play.
- Dashboards are built for fingers. A dedicated view with a few large tile cards is far
  nicer with a pointer than a dense phone layout.
