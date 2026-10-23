# 3. Install the custom home screen

A light home screen with your app dock over full-screen photos. It installs **next to** Google's home screen; nothing is replaced until [step 4](04-home-button.md).

## 1. Configure

```bash
cp local.properties.example local.properties
```

Edit `local.properties`:

| Setting | What it does |
|---|---|
| `sdk.dir` | Path to your Android SDK (Android Studio → Settings → Android SDK shows it) |
| `dock.apps` | Apps in the dock on first launch, by package name, comma-separated. Empty = YouTube, YouTube Music, Twitch, Plex, Netflix. Find yours with `adb -s $TV shell pm list packages -3` |
| `twitch.*` | Optional, see [6. Twitch card](06-twitch.md) |

`local.properties` is git-ignored; it never leaves your computer.

## 2. Build

Needs JDK 17+ (Android Studio includes one).

```bash
# macOS with Android Studio:
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleRelease
```

The APK lands in `app/build/outputs/apk/release/app-release.apk` (about 2 MB). The release build is optimised (R8), which matters on slow TV processors.

## 3. Install and try it

```bash
adb -s $TV install -r app/build/outputs/apk/release/app-release.apk
adb -s $TV shell pm grant dev.raul.tvhome android.permission.READ_MEDIA_IMAGES   # read screensaver photos
adb -s $TV shell cmd package compile -m speed -f dev.raul.tvhome                  # smoother first launches
adb -s $TV shell am start -n dev.raul.tvhome/.MainActivity
```

It also appears in your apps list as **Home**.

## Using it

| Remote | Does |
|---|---|
| ← → | Move along the dock |
| OK | Open the app |
| Hold OK on a tile | Edit the dock: move, remove, add apps (up to 8) |
| ↑ on Twitch | Select a live stream card, OK opens it |
| Gear button | Settings: HDMI inputs, Picture, Sound, Wi-Fi, all settings |
| Back | Close panels / leave edit mode |

After 2 minutes without a button press, everything fades except the photo and clock (screensaver). Any button wakes it.

## Customising the code

All in `app/src/main/java/dev/raul/tvhome/`:

| File | Change |
|---|---|
| `Apps.kt` | Taglines and tint colours per app (`KNOWN`), max dock size |
| `Home.kt` | Layout, idle time (`IDLE_MS`), photo interval (`PHOTO_MS`), animations |
| `SettingsPanel.kt` | Settings panel buttons |
| `Twitch.kt` | Live card data |

Rebuild and reinstall after changes; your dock order is kept.

Next: [4. Home button](04-home-button.md) (optional)
