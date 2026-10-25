# AGENTS.md — instructions for an AI agent running this repo

You are about to change someone else's television. Read this whole file, then
**interview the user before touching anything**. The scripts are written so an
agent that follows this order never has to improvise.

## Platform scope

Fire OS is fully supported. Android TV and Google TV take the same package pass
with their own lists and a different launcher trick. Tizen, webOS, Roku, VIDAA,
Vizio, Panasonic and Philips Saphi give an owner no shell to work with: say so
plainly, point at `docs/OTHER-TVS.md`, and never invent a sequence — disabling
the wrong component on a TV takes the home screen or the input switcher with it.

## Hard rules

1. **Never touch `/system`.** No `adb remount`, no `adb root`, no hosts-file
   editing, no deleting system APKs. Fire OS verifies `/system` with dm-verity;
   writing to it bricks the device. The DNS filter exists so this is unnecessary.
2. **Never factory-reset the TV.**
3. **Never disable anything in `data/fireos/never-disable.txt`.** The scripts
   enforce it: that list is the launcher, settings, TV input/routing, device
   control, Bluetooth pairing and system UI.
4. **Ask before the first destructive step** unless the user has already said
   "go". Debloating uninstalls apps; say that out loud before you start.
5. **Never remove an app the user said they watch.** If they name an app and you
   are not sure which package it is, ask or look it up — do not guess.
6. **Do not invent package names.** Candidates come from
   `adb shell pm list packages` on that device.
7. **Keep identifiers out of the repo and out of public posts.** IPs, serial
   numbers, MAC addresses, account emails and Wi-Fi names never go in a commit,
   an issue, or a screenshot. `report/` is git-ignored; keep it that way.
8. **One device at a time.** If `adb devices` lists several, set `ADB_SERIAL`.
9. **Never remove someone's YouTube app unless they asked for it in those words.**
   Replacing YouTube is the most personal change in this repo — it is the app the
   whole household actually uses, their subscriptions live in it, and deleting it
   is the one step that cannot be undone from the command line. So:
   - It is **off by default**. `YOUTUBE=0` in `dumbtv.conf`, the interview question
     defaults to no, and `all.sh` skips it unless the answer was yes.
   - **Ask, do not assume.** "Do you want YouTube replaced with SmartTube? No ads
     and no Shorts, but it is a different app and your account still works." If
     they say no, drop it and never mention it again in that session.
   - **`scripts/youtube.sh` disables the stock app; it does not delete it.**
     `--remove` deletes, and it exists only for the person who says "get it off
     the TV". Never pass `--remove` because it seems tidier.
   - When they do want it gone, say what it costs: the APK lives in `/data`, so
     the Appstore is the only way back. Offer the reversible version first.
   - Their YouTube **account and subscriptions are never touched** — SmartTube
     signs in separately, and nothing here can read or change a Google account.

## Step 0 — interview the user (do this first)

Everything that only does ads, telemetry, experiments or Alexa goes regardless.
The only real question is **which apps they actually watch**, because that
decides what is removed and what the filter must not block.

Ask these, in plain language, and wait for answers:

1. "Which of these apps do you actually use?" — read the removals from
   `data/fireos/user-apps.txt`, translate each package to a name with
   `data/apps.txt` (e.g. `in.startv.hotstar` → JioHotstar), and list them.
2. "Do you watch Prime Video?" — it stays installed either way, but if they say
   yes the video CDN is kept out of the blocklist (otherwise playback breaks).
3. "Do you want Amazon's home screen replaced with dumbtv home, or kept?" —
   dumbtv home is this repo's own launcher (`launcher/`): apps and HDMI inputs
   only, open source, no internet permission (no permissions at all on Fire OS 7/8). Replacing removes the promo tiles;
   keeping Amazon's is less intrusive. Do not install Projectivy or another
   closed-source launcher instead.
4. "Do you want the on-TV DNS filter?" — that is what actually stops telemetry
   and updates; if they say no, say what they lose.
5. "Anything else you want kept, or that you want removed?" — free text. Map
   names to packages with `data/apps.txt` and `adb shell pm list packages -3`.
6. "Do you want the YouTube app replaced?" — **ask it as a question, never as a
   plan step.** Default is **no**: the stock app stays, ads and all. If they say
   yes, say it is a different app (SmartTube) that has no ad code and can hide
   Shorts, that their Google account is unaffected, and that it will be disabled
   rather than deleted unless they ask for it gone. `docs/YOUTUBE.md` has the
   full explanation of why the stock app's ads cannot be blocked instead.
   Never bring this up unprompted beyond that one question — somebody's YouTube
   is not a tidy-up opportunity.

Then record the answers:

```sh
./scripts/configure.sh                  # asks the same questions on the terminal
# or, non-interactive, if you already have the answers:
./scripts/configure.sh --keep "com.netflix.ninja org.videolan.vlc" --remove "com.spotify.tv.android"
```

That writes `dumbtv.conf` (git-ignored). `debloat.sh`, `dnsfilter.sh`,
`launcher.sh` and `youtube.sh` read it: a package in `KEEP_APPS` is never
uninstalled or disabled, `PRIME=1` keeps Prime's CDN out of the blocklist, and
`LAUNCHER=0`, `DNSFILTER=0`, `YOUTUBE=0` skip those steps entirely.

Re-run `configure.sh` if the user changes their mind. Do not hand-edit the
scripts for preferences.

## Preconditions

```sh
adb connect <TV_IP>:5555
adb devices                                  # the line must end in "device"
adb shell getprop ro.product.manufacturer    # "Amazon" = Fire OS
adb shell getprop ro.build.version.sdk       # 25 = Fire OS 6, 28 = Fire OS 7, 30 = Fire OS 8
```

`unauthorized` means the TV is showing an "Allow USB debugging" prompt — a human
must accept it. Do not retry in a loop.

If the device is not Amazon-branded, stop and read `docs/OTHER-TVS.md`.

## Order of operations

```sh
./scripts/preflight.sh        # read-only, always first
./scripts/configure.sh        # the interview, writes dumbtv.conf
./scripts/all.sh              # everything else; asks before each destructive step, reboots twice
./scripts/verify.sh           # must exit 0
```

`all.sh` runs `configure.sh` itself when `dumbtv.conf` does not exist yet.

Stepwise, if you prefer:

1. `./scripts/debloat.sh -y` — then reboot and confirm the TV still reaches a
   home screen before continuing.
2. `./scripts/lockdown.sh -y` — no reboot.
3. `./scripts/dnsfilter.sh -y` — installs, reboots, self-tests.
4. `./scripts/launcher.sh -y` — home screen takeover.
5. `./scripts/youtube.sh -y` — **only if they asked for it** (`YOUTUBE=1`). It
   disables the stock app; add `--remove` only on an explicit request.
6. `./scripts/verify.sh`.

## What success looks like

`verify.sh` exits 0 and prints a table like:

```
ok   softwareupdates.amazon.com   blocked
ok   arcus-uswest.amazon.com      blocked
ok   unagi-zaz.amazon.com         blocked
ok   www.youtube.com              resolves
ok   appstore-tv-prod-na.amazon.com resolves
home armed, front is tv.dumbtv.home/.Home
packages 65 disabled
filter 396 entries
```

If a blocked host resolves to a public IP, the filter is not in the DNS path.
Read the recovery table instead of disabling more packages.

## Reading `debloat.sh` output

- `removed` — uninstalled for user 0.
- `disabled` — `pm disable-user` succeeded.
- `PROTECTED` — Amazon refuses (`Cannot disable a protected package`). Expected
  for `data/fireos/protected.txt`; `lockdown.sh` and the DNS filter handle them.
- `SUSPECT` — failed for another reason. Stop and report; do not add packages.
- `SKIP (keep list)` — the user asked to keep it. Correct, leave it alone.

## Recovery

| Symptom | Cause | Fix |
|---|---|---|
| Amazon's home is back | accessibility service cleared, or its process died | `./scripts/launcher.sh -y` |
| HOME works but shows Amazon's home for ~5 s first | `alarm_manager_constants` lost `min_futurity=0` (reset or update) | `./scripts/launcher.sh -y` |
| Settings or another screen bounces back to the home screen | something opened Amazon's home activity | open Fire OS settings with `adb shell am start -a android.intent.action.MAIN -c android.intent.category.LEANBACK_SETTINGS`; report the screen |
| Nothing blocked but `tun0` exists | the filter app was `force-stop`ped | reboot the TV — never `am force-stop dnsfilter.android` |
| "Unable to Detect Your Remote" over everything | `com.amazon.vizzini` was disabled | `adb shell pm enable --user 0 com.amazon.vizzini`, reboot |
| A streaming app stops working | a host it needs is blocked | unblock that exact host in `data/blocklists/extra-hosts.txt`, re-run `dnsfilter.sh` |
| TV sleeps about 5 minutes after the last key press | `com.amazon.ftv.screensaver` is disabled, so when the screen-saver timer fires the TV sleeps instead | `adb shell settings put system screen_off_timeout 1200000` (20 min; the value is milliseconds) |
| Start over | — | `./scripts/restore.sh -y` |

`restore.sh` re-enables packages, clears app-ops and network rules and restores
the settings in `data/fireos/settings.restore.txt`. Apps uninstalled from
`/data` come back from the store.

## Adding support for a new device

1. `./scripts/discover.sh` — read-only inventory that flags ads/analytics-looking
   packages and writes a commented block for `data/<platform>/packages.txt`.
2. Verify candidates by hand on that TV, then fill in the list.
3. Run `debloat.sh --dry-run`, then for real; add every `PROTECTED` to
   `data/fireos/protected.txt` (or the matching platform file).
4. Never add a package that is not on the device, and never remove a package
   from `never-disable.txt`.
5. Android TV: keep the Fire OS settings keys out of it and read
   `docs/OTHER-TVS.md` first.
6. Record what a human had to do (developer-mode path, where the IP is shown,
   which prompts blocked you) in `catalog/devices/<id>.yaml`, then run
   `python3 catalog/validate.py`. That file is the whole point: it is what saves
   the next person the same discovery.

## Answering "how do I do this on <some TV>"

Check `catalog/devices/` first. Each entry carries the developer-mode path, where
the IP is shown, and the dialogs that block a bot on that model. If there is no
entry, say so instead of guessing — guessing a menu path to a stranger is how
they end up in the wrong settings screen.

## After a successful session — contribute

The catalog only grows if every session leaves something behind. When
`verify.sh` exits 0, or when you hit something new and painful, run:

```sh
./scripts/contribute.sh
```

Answer, or pass as flags:

- how developer mode and ADB were enabled (exact menu path, how many clicks)
- where the TV shows its IP address
- each dialog that blocked automation, and what dismissed it
- anything surprising for the next person (blank screens, packages that stay
  running, buttons that do nothing)
- whether it fully worked (`tested`), partly (`partial`) or not at all (`untested`)

Then open the PR it prints. Rules:

- Never include an IP address, MAC address, serial number, Wi-Fi name or account
  email. The script refuses IP/MAC-shaped input; CI fails a PR that contains a
  private address.
- `tested` means the full pass ran and `verify.sh` exited 0 **on that device**.
- One device per PR. If the package lists differ, that is a second PR.

## Reporting

Do not paste `report/` anywhere public. Summarise the verify table and the
counts instead.
