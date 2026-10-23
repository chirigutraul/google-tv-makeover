# 4. Make the Home button open the new home screen

Google TV sends the Home button to its own launcher even when another app is the default home. The reliable fix is to disable Google's launcher **and** its fallback, `setupwraith`.

**Gain:** Home opens your home screen; frees ~150 MB of RAM (Google's home screen and its helper).
**Lose while disabled:** Google TV's home, "For you" recommendations and its Live/Library tabs. Settings, Play Store and all apps keep working.

## Risks (read first)

- **Order matters.** On a test TV, disabling only `launcherx` made `setupwraith` take over Home with a recovery screen, and the TV **rebooted**. Disabling `setupwraith` first, then `launcherx`, worked.
- Reported by others on some devices: **saved Wi-Fi forgotten** (reconnect with the remote; you may need to re-enable wireless debugging), the remote's **YouTube button** stops working, **YouTube sign-in** can hang (re-enable the launcher briefly to sign in), **HDMI switching** may depend on the launcher on some brands.
- A big firmware update may re-enable the launcher; just repeat the steps.

## Steps

```bash
# 1. Make the custom home screen the default home first
adb -s $TV shell cmd role add-role-holder android.app.role.HOME dev.raul.tvhome 0
adb -s $TV shell cmd package set-home-activity dev.raul.tvhome/.MainActivity

# 2. Disable the fallback FIRST, then Google's launcher
adb -s $TV shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
adb -s $TV shell pm disable-user --user 0 com.google.android.apps.tv.launcherx

# 3. Check: should print dev.raul.tvhome
adb -s $TV shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME | grep packageName
```

The ADB connection may drop for a moment; reconnect with `adb connect $TV`.

Then test: open any app, press **Home** on the remote, and check:
- Your home screen appears
- Wi-Fi is still connected
- Switching HDMI inputs works
- The remote's app buttons (YouTube, Netflix) still work

## Undo

```bash
adb -s $TV shell pm enable com.google.android.apps.tv.launcherx
adb -s $TV shell pm enable com.google.android.tungsten.setupwraith
adb -s $TV shell cmd role add-role-holder android.app.role.HOME com.google.android.apps.tv.launcherx 0
```

## Alternative without disabling anything

An accessibility service can catch the Home press and open the custom home screen instead (this is how Projectivy Launcher does it). Google's home screen may flash briefly and keeps using its RAM. Not built into this app yet.
