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

## Useful facts

- Commands: `adb -s <ip:port> shell ...`. Many TVs drop the ADB connection briefly after big changes; reconnect with `adb connect` and check `uptime` to see if it rebooted.
- The home screen app id is `dev.raul.tvhome`. It reads photos from `/sdcard/Pictures/Screensavers` (needs `pm grant dev.raul.tvhome android.permission.READ_MEDIA_IMAGES`), and reads the photo list at startup (restart it after pushing photos).
- Release builds are signed with the debug key, so they install over earlier builds.
- After installing, `adb shell cmd package compile -m speed -f dev.raul.tvhome` makes the first launches smoother.
