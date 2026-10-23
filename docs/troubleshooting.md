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
