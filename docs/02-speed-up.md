# 2. Speed up the TV

Pick what you want from the [tweaks catalog](tweaks-catalog.md). Every tweak is reversible; the undo command is listed next to it.

In all commands, replace `$TV` with your TV's address, e.g. `export TV=192.168.1.50:5555`.

## Recommended for almost everyone

| Tweak | Effect | Undo |
|---|---|---|
| Animations at 0.5x | Menus feel about twice as fast | set back to `1.0` |
| Limit parked apps to 6 | Frees 250–350 MB; apps you haven't used in a while reload when reopened | `device_config delete activity_manager max_cached_processes` |

```bash
# Faster animations
for k in window_animation_scale transition_animation_scale animator_duration_scale; do
  adb -s $TV shell settings put global $k 0.5
done

# Keep at most 6 parked apps in memory (Android's default is 32)
adb -s $TV shell device_config put activity_manager max_cached_processes 6
# Optional: stop Google's background flag updates from resetting it
adb -s $TV shell device_config set_sync_disabled_for_tests persistent
```

The last line also pauses Google's remote "feature switches" (gradual experiments). App, system and firmware updates still install. Undo: `device_config set_sync_disabled_for_tests none`.

## Your choice

- **Remove apps you don't use.** Pre-installed apps are removed for your user only (`pm uninstall --user 0`) and come back with `cmd package install-existing <package>`. Apps you installed yourself can be reinstalled from the Play Store. See the catalog's "Pre-installed apps" section.
- **Turn off brand extras** that run all the time (demo mode, home-automation bridges, satellite installer, wireless-speaker services). See the catalog's brand sections; only turn off what you don't use.

## Check the result

```bash
adb -s $TV shell cat /proc/meminfo | grep -E "MemAvailable|SwapFree"
adb -s $TV shell dumpsys meminfo | sed -n '/Total PSS by process/,/Total PSS by OOM/p' | head -20
```

"Available" RAM fluctuates because Android quickly refills freed memory with parked apps. What matters is that disabled services stay stopped and the TV stutters less.

Next: [3. Home screen](03-home-screen.md) (optional)
