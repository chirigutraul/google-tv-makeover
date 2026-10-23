# 1. Connect your computer to the TV

ADB (Android Debug Bridge) lets your computer send commands to the TV over Wi-Fi. Nothing changes on the TV just by connecting.

## Install ADB on your computer

- **macOS:** `brew install --cask android-platform-tools`
- **Windows / Linux:** download [platform tools](https://developer.android.com/tools/releases/platform-tools) and add the folder to your PATH.

Check: `adb version`

## Turn on developer mode on the TV

1. **Settings → System → About**
2. Scroll to **Android TV OS build** and press OK on it **7 times** until it says "You are now a developer".

## Turn on debugging

1. **Settings → System → Developer options**
2. Turn on **USB debugging** (needed even over Wi-Fi).
3. Turn on **Wireless debugging** (some brands call it **Network debugging**) and open it. Note the **IP address and port**, e.g. `192.168.1.50:5555`.

Can't find the IP? **Settings → Network & Internet →** your Wi-Fi network.

## Connect

```bash
adb connect 192.168.1.50:5555
```

The TV shows **"Allow debugging?"** Tick **Always allow from this computer** and choose **Allow**. Then:

```bash
adb devices
```

should list the TV as `device`. If it says `unauthorized`, accept the prompt on the TV. If the port shown on the TV doesn't work, try `5555`.

## See what's slowing it down (read-only)

```bash
scripts/tv-report.sh 192.168.1.50:5555
```

Shows the model, RAM, free RAM, swap, storage, the biggest processes and your installed apps. On a 2–3 GB TV the usual culprit is **memory**: RAM nearly full and swap (compressed memory) over 90% full, so the TV keeps compressing and reloading apps.

Next: [2. Speed up](02-speed-up.md)
