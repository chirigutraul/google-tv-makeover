# Instructions for Claude

This repo guides a person through optimising their Google TV / Android TV and (optionally) installing a custom home screen. You do the work over ADB from their computer. **They decide everything.**

## Ground rules (always)

1. **Nothing changes on the TV without the user's explicit OK for that specific change.** Reading (dumpsys, pm list, getprop, screenshots) is fine without asking. Installing, uninstalling, disabling, granting, settings changes, pushing files: ask first, every time.
2. **Explain before asking:** what it does, what they gain (RAM, speed), what they lose, how to undo it. Plain language; the user may not be technical.
3. **Offer choices, don't decide for them.** Present options from [docs/tweaks-catalog.md](docs/tweaks-catalog.md) as a list they can pick from. Skip anything they say they use.
4. **Before every disable/uninstall, record the undo command** in `my-tv-changes.md` in the repo root (git-ignored), so the user has a list of what was changed and how to revert it.
5. **Never print, commit or paste secrets.** Twitch keys live only in `local.properties` (git-ignored). Tell the user to edit that file themselves rather than pasting keys into chat.
6. **Don't press remote keys (`input keyevent`) while the user may be watching** without telling them first. Prefer screenshots and dumpsys to check results.
7. **Verify after each change** (process stopped, RAM, no crashes: `adb logcat -d -b crash`) and report what actually happened, including failures.

## The flow

Work through the parts in order. The user can stop after any part.

### 0. Check the setup
- `adb version`; if missing, offer to install (`brew install --cask android-platform-tools` on macOS).
- Guide the user through [docs/01-connect.md](docs/01-connect.md) (developer options, wireless debugging, IP:port). Try port 5555 if the shown one fails.
- Run `scripts/tv-report.sh <ip:port>` (read-only) and summarise: model, Android version, RAM, free RAM, swap, storage, biggest processes, installed apps. Explain *why* it's slow in one or two sentences (usually: RAM full, swap full).

### 1. Speed up: [docs/02-speed-up.md](docs/02-speed-up.md)
- Present the catalog sections the TV actually has (check each package exists first with `pm list packages`). Show RAM per running process where useful.
- Let the user pick. Apply only the picks. Brand-specific items: only offer the ones whose packages exist on this TV.

### 2. Home screen (optional): [docs/03-home-screen.md](docs/03-home-screen.md)
- Ask what they want in the dock (list their installed TV apps), whether they want the Twitch card, and which streamers. Write those into `local.properties` (copy from `local.properties.example`).
- Build with `./gradlew assembleRelease`, then ask before installing.
- Install as a normal app first so they can try it. Google's home screen stays the default until step 3.

### 3. Home button (optional, higher risk): [docs/04-home-button.md](docs/04-home-button.md)
- Explain the trade-off and the risks honestly before asking.
- **Order matters:** set the home role first, disable `setupwraith` **before** `launcherx`. Disabling `launcherx` alone made a test TV reboot into a recovery screen.
- After: test Home from another app, check Wi-Fi is still connected, ask the user to check HDMI switching and the remote's YouTube button.

### 4. Screensavers: [docs/05-screensavers.md](docs/05-screensavers.md)
- Offer: their own photos folder, or N random Chromecast backgrounds (`scripts/fetch-chromecast-backgrounds.sh`). Downloading files needs their OK (say source and roughly how many MB).

### 5. Twitch card: [docs/06-twitch.md](docs/06-twitch.md)
- Walk them through getting keys; they paste keys into `local.properties` themselves. Verify keys with the curl check in the doc (don't echo the secret).

### 6. Mac commands (optional, macOS only): [docs/07-mac-control.md](docs/07-mac-control.md)
`wake-tv`, `sleep-tv`, `mirror-tv`, `stop-tv-mirror`. **An optional add-on.** Nothing in parts 0–5 depends on it.
- **Who gets offered it:** ask whether they use a Mac. On Windows or Linux, skip this part without mentioning mirroring. `wake-tv` and `sleep-tv` are plain bash and Python, so a Linux user can have those two if they ask. Mirroring is macOS only (ScreenCaptureKit, VideoToolbox). Don't try to port it during a setup session.
- **Requirements:** `wake-tv` and `sleep-tv` need only ADB and `local.properties`. Mirroring also needs the home screen app from part 2 *installed* (not necessarily set as home), macOS 14+, and `swiftc` (Xcode or Command Line Tools).
- **Files in the module:** `scripts/{wake-tv,sleep-tv,mirror-tv,stop-tv-mirror}`, `mac/` (Mac sender; compiled into `mac/.build/`, git-ignored), `app/.../Mirror.kt` and its `MirrorActivity` entry in the manifest (the TV receiver). `MirrorActivity` does nothing until `mirror-tv` opens it: no service, no background work. Users who skip this part can keep the stock app build.
- Offer the commands they want; each step below is read-only unless marked.
- **Address:** find the TV's IP (`adb devices -l`, or the port-5555 scan in the doc; the doc's `192.168.1.50` is only an example) and the Wi-Fi MAC (`cat /sys/class/net/wlan0/address`). Write `tv.ip` / `tv.mac` into `local.properties`.
- **Wake-on-LAN:** check the brand's setting (table in the doc). If it's off, the user turns it on in TV settings.
- **Wi-Fi band:** `dumpsys wifi | grep mWifiInfo` → `Frequency`. On 2.4 GHz, recommend moving both TV and Mac to 5 GHz before mirroring (the user does it; the Wi-Fi password never goes through you). After a switch, re-check the TV's IP.
- **Decoders:** `grep -A12 'c2.*hevc.decoder' /vendor/etc/media_codecs*.xml` should show a hardware HEVC/AVC decoder with `low-latency`.
- **Install (ask first):** symlink the four scripts into `~/.local/bin` (on their PATH); mirroring needs the home screen app installed or updated (it contains `MirrorActivity`). Back up the installed APK first (`pm path` → `adb pull` into `backups/`) and note the undo in `my-tv-changes.md`.
- **Verify the TV side yourself** with `mirror-tv --test` (a moving pattern, needs no permission), then read `$TMPDIR/tvmirror.log` and `adb logcat -s Mirror:I`. Tell them first: it briefly takes over the TV screen; `--extend --test` also briefly adds a screen to their Mac.
- **The real screen test is theirs:** `mirror-tv` must be run in *their* terminal app. It needs Screen & System Audio Recording permission for that app. The Claude app's shell also can't reach the local network ("No route to host").
- `wake-tv` / `sleep-tv` turn the TV off and on: ask before testing them while someone may be watching.

Lessons from building it (don't repeat these):
- A still Mac screen sends no frames, and the TV decoder holds the first frame → black screen. Fixed by repeating the last frame; if it comes back, check `repeats` in the log.
- The capture needs `queueDepth` 8; with 3 the Mac delivered only 20–40 fps.
- Sound stutter came from a tiny AudioTrack buffer with non-blocking writes; the jitter-buffer thread fixed it.
- Occasional ~100 ms hitches were the Mac ↔ router Wi-Fi link, not the TV. AWDL off and the TV Wi-Fi lock didn't help; `--smooth` hides them at ~150 ms delay. Suggest an Ethernet adapter on the Mac if they want both.
- `adb connect` sometimes says "No route to host" even though the TV answers ping: `adb kill-server` and connect again.

## Useful facts

- Commands: `adb -s <ip:port> shell ...`. Many TVs drop the ADB connection briefly after big changes; reconnect with `adb connect` and check `uptime` to see if it rebooted.
- The home screen app id is `dev.raul.tvhome`. It reads photos from `/sdcard/Pictures/Screensavers` (needs `pm grant dev.raul.tvhome android.permission.READ_MEDIA_IMAGES`), and reads the photo list at startup (restart it after pushing photos).
- Release builds are signed with the debug key, so they install over earlier builds.
- After installing, `adb shell cmd package compile -m speed -f dev.raul.tvhome` makes the first launches smoother.
