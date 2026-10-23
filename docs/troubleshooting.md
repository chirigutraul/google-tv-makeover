# Troubleshooting

| Problem | Fix |
|---|---|
| `adb connect` says "No route to host" or "failed to connect" | Check the TV is on and on the same Wi-Fi; try port `5555`; turn Wireless debugging off and on |
| `adb devices` shows `unauthorized` | Accept "Allow debugging?" on the TV screen |
| `device offline` / connection drops after a change | Normal on some TVs. `adb disconnect $TV && adb connect $TV`; check `adb -s $TV shell uptime` to see if it rebooted |
| TV rebooted into a recovery screen after disabling Google's launcher | `setupwraith` was still enabled. Re-enable `launcherx` (`pm enable`), then follow [04-home-button](04-home-button.md) in order |
| Home button still opens Google's home | `setupwraith` or `launcherx` is still enabled, or the home role isn't set. See [04-home-button](04-home-button.md) |
| No photos, only a dark gradient | Push photos ([05-screensavers](05-screensavers.md)), grant `READ_MEDIA_IMAGES`, restart the home screen |
| Twitch card never appears | Keys or `twitch.streamers` missing in `local.properties`, nobody is live, or you didn't rebuild after editing |
| Dock tiles show only a colour | The app has no TV banner; it falls back to its icon or name |
| Animations stutter | Use the release build, run `cmd package compile -m speed -f dev.raul.tvhome`, apply the speed-up tweaks |
| No sound after a restart (Philips) | You disabled `org.droidtv.tv.playFiAudioService`; `pm enable` it |
| Build fails: "SDK location not found" | Set `sdk.dir` in `local.properties` |
| Build fails: Java version | Use JDK 17+: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` |
| `wake-tv`: "No answer from the TV after 30s" | Wake-on-LAN is off ([07-mac-control](07-mac-control.md#3-turn-on-wake-on-lan-on-the-tv-for-wake-tv)), `tv.ip`/`tv.mac` are wrong, or the TV is in deep standby / unplugged |
| `mirror-tv`: "No permission to record the screen" | Allow your terminal app in System Settings → Privacy & Security → Screen & System Audio Recording, restart it, run again |
| `mirror-tv`: "Can't reach the TV at …:7070" | The home screen app (with the Mirror screen) isn't installed or is outdated; or you ran it from inside the Claude app (run it in your own terminal) |
| Mirroring shows black until the mouse moves | Pull the latest version: the Mac now repeats still frames. Check `repeats` in `$TMPDIR/tvmirror.log` |
| Mirroring is choppy or has small freezes | Use 5 GHz Wi-Fi; try `mirror-tv --smooth`; check `skipped` and `delay` in `$TMPDIR/tvmirror.log` |
| Mac stays muted after mirroring | `osascript -e "set volume without output muted"` |
