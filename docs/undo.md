# Undo everything

If you used Claude, it kept a list of your exact changes in `my-tv-changes.md`. Otherwise:

```bash
export TV=192.168.1.50:5555   # your TV

# Google's home screen back (see 04-home-button)
adb -s $TV shell pm enable com.google.android.apps.tv.launcherx
adb -s $TV shell pm enable com.google.android.tungsten.setupwraith
adb -s $TV shell cmd role add-role-holder android.app.role.HOME com.google.android.apps.tv.launcherx 0

# Remove the custom home screen
adb -s $TV uninstall dev.raul.tvhome

# Animations back to normal
for k in window_animation_scale transition_animation_scale animator_duration_scale; do
  adb -s $TV shell settings put global $k 1.0
done

# Parked-app limit and Google flag sync back to default
adb -s $TV shell device_config delete activity_manager max_cached_processes
adb -s $TV shell device_config set_sync_disabled_for_tests none

# Re-enable a disabled package / bring back a removed pre-installed app
adb -s $TV shell pm enable <package>
adb -s $TV shell cmd package install-existing <package>

# See what's currently disabled
adb -s $TV shell pm list packages -d
```

A factory reset also undoes all of it (and erases everything else on the TV).

When finished, you can turn off **Wireless debugging** in Developer options.
