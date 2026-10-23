# Tweaks catalog

Pick what applies to you. **Check a package exists before touching it:** `adb -s $TV shell pm list packages | grep <name>`. Package names vary by brand and firmware.

- **Disable** = `adb -s $TV shell pm disable-user --user 0 <package>` → undo: `pm enable <package>`
- **Remove** = `adb -s $TV shell pm uninstall --user 0 <package>` → undo: `cmd package install-existing <package>`

RAM figures are from a Philips 2.8 GB TV and will differ on yours.

## System (any Google TV)

| Tweak | Gain | Downside | Command |
|---|---|---|---|
| Animations 0.5x | Snappier menus | none | see [02-speed-up](02-speed-up.md) |
| Parked-app limit (4–8) | 250–350 MB | apps reload after a while | `device_config put activity_manager max_cached_processes 6` |
| Lock that limit | limit survives Google flag updates | pauses Google's remote experiments | `device_config set_sync_disabled_for_tests persistent` |
| Google TV screensaver off | ~65–85 MB (if you use the custom home screen's screensaver) | no Google Ambient mode | Disable `com.google.android.apps.tv.dreamx` |

## Pre-installed apps (remove what you don't use)

| App | Package | Notes |
|---|---|---|
| Amazon Alexa | `org.droidtv.amazonalexa` (Philips) | |
| Rakuten TV | `tv.wuaki.apptv` | often runs in background |
| Prime Video | `com.amazon.amazonvideo.livingroom` | |
| Netflix | `com.netflix.ninja` | often runs in background |
| YouTube Music | `com.google.android.youtube.tvmusic` | |
| Google Play Games | `com.google.android.play.games` | |
| Feedback apps | `com.google.android.feedback`, `com.android.tv.feedbackconsent` | |

List everything you installed yourself: `adb -s $TV shell pm list packages -3`.

## Philips extras (disable what you don't use)

| What | Packages | ~RAM | Keep it if… |
|---|---|---|---|
| Demo mode | `org.droidtv.demome` | 10 MB | never needed at home |
| Control4 home automation | `org.droidtv.control4app`, `org.droidtv.control4sddp`, `org.droidtv.control4adapterservice`, `org.droidtv.control4UcService` | 20 MB | you use a Control4 system |
| Satellite installer | `org.droidtv.euinstallersat` | 16 MB | you tune satellite channels |
| DTS Play-Fi | `com.phorus.playfi.tv`, `com.phorus.playfi.softap`, `org.droidtv.tv.playFiAudioService` | 38 MB | you use Play-Fi wireless speakers |
| NFT Gallery, Screenshot share | `com.tpv.nftgallery`, `com.tpv.screenshotshare` | small | |

Notes:
- `org.droidtv.tv.playFiAudioService` is a persistent system service: it only stops after the next restart. **Check that sound works after that restart**; re-enable it if not.
- Don't disable `org.droidtv.tv.tpvaudiomanager`, `tpvvideomanager`, `hidwizard` (remote/keyboard) or anything with "settings", "input" or "tuner" in the name.

## Other brands

Look at the biggest processes (`scripts/tv-report.sh`) and search the package name before disabling anything unfamiliar. Contributions with tested lists for Sony, TCL, Hisense etc. are welcome.

## Never disable alone

- `com.google.android.apps.tv.launcherx` (Google TV home): only together with `setupwraith`, in the order in [04-home-button](04-home-button.md).
- `com.google.android.gms` (Play Services), `com.android.vending` (Play Store), `com.android.systemui`, `com.android.tv.settings`: core system.
