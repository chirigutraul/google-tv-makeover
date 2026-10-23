# 7. Control the TV from your Mac: wake, sleep, mirror

> **Optional, macOS only.** Skip this part if you don't use a Mac. Nothing else in the guide depends on it, and the home screen works the same without it.

Four terminal commands for macOS:

| Command | Does |
|---|---|
| `wake-tv` | Turns the TV on from standby |
| `sleep-tv` | Puts the TV in standby (like the remote's power button) |
| `mirror-tv` | Shows the Mac's screen and sound on the TV |
| `stop-tv-mirror` | Stops mirroring (any mode); the TV goes back to what it showed before |

No paid or ad-supported apps. Mirroring uses the custom home screen app (part 3) as the receiver, so install that first. It doesn't have to be your home screen, only installed.

**macOS only.** `wake-tv` and `sleep-tv` are plain shell scripts and are easy to port. Mirroring uses macOS's ScreenCaptureKit and VideoToolbox.

## Requirements

- macOS 14 or newer, Apple Silicon recommended (tested on an M4 MacBook Air, macOS 27)
- Xcode or the Command Line Tools (`xcode-select --install`): `mirror-tv` compiles its Mac side on first run with `swiftc`
- ADB connected to the TV ([01-connect](01-connect.md))
- For mirroring: the home screen app installed ([03-home-screen](03-home-screen.md))
- **5 GHz Wi-Fi** for both the Mac and the TV. On 2.4 GHz, mirroring works but is soft and laggy.

## Setup

### 1. Save the TV's address

Add to `local.properties` (git-ignored):

```properties
tv.ip=192.168.1.10
tv.mac=e0:75:26:91:e7:65
```

- `tv.ip`: the TV's IP. If you don't know it, `adb devices -l`, or scan the network for port 5555 open:
  ```bash
  for i in $(seq 2 254); do (nc -z -G 1 192.168.1.$i 5555 >/dev/null 2>&1 && echo 192.168.1.$i) & done; wait
  ```
- `tv.mac`: the **Wi-Fi** MAC address (or Ethernet, if the TV is wired):
  ```bash
  adb -s $TV shell cat /sys/class/net/wlan0/address
  ```

Tip: give the TV a fixed IP in your router, so `tv.ip` never goes stale.

### 2. Put the commands on your PATH

```bash
mkdir -p ~/.local/bin
for c in wake-tv sleep-tv mirror-tv stop-tv-mirror; do ln -sf "$PWD/scripts/$c" ~/.local/bin/$c; done
```

`~/.local/bin` must be on your `PATH` (`echo $PATH`). The links point into this repo, so keep the repo where it is.

### 3. Turn on Wake-on-LAN on the TV (for `wake-tv`)

The setting's name depends on the brand:

| Brand | Setting |
|---|---|
| Philips | Settings → Wireless & networks → Wired or Wi-Fi → **Switch on with Wi-Fi (WoWLAN)** |
| Sony | Settings → Network → **Remote start** |
| TCL / others | Settings → System → Power → **Network standby** / **Wake on LAN / WLAN** |

To check from ADB: `adb -s $TV shell settings list global | grep -i wol` (Philips shows `M_isEnableWol=1`).

### 4. Screen Recording permission (for `mirror-tv`)

Run `mirror-tv` once **in your own terminal app** (Terminal, iTerm…). macOS asks to allow **Screen & System Audio Recording** for that app. Allow it, restart the terminal if asked, and run `mirror-tv` again.

## Using it

```bash
wake-tv                    # TV on
mirror-tv                  # Mac screen on the TV (lowest delay)
mirror-tv --smooth         # steady 60 fps for videos, ~150 ms more delay
mirror-tv --extend         # the TV becomes a second screen, above the Mac
stop-tv-mirror
sleep-tv                   # TV off
```

All `mirror-tv` options:

| Option | Effect |
|---|---|
| `--extend` | The TV gets its own 16:9 desktop (a virtual "TV" screen, 4K-sharp) placed **above** the Mac: move the mouse through the top edge, drag windows up. No black bars. |
| `--smooth` | The TV holds 150 ms of frames and plays them evenly. Hides Wi-Fi hiccups; adds delay. Best for watching videos. |
| `--1080` | At most 1920×1080 instead of the Mac's full resolution. Lighter for the Mac and the Wi-Fi, slightly softer text. |
| `--h264` | H.264 instead of HEVC (needs more bitrate) |
| `--mbps N` | Bitrate (defaults: 30 full-res, 35 `--extend`, 10 `--1080`) |
| `--fps N` | Frame rate (default 60) |
| `--no-audio` | Picture only |
| `--mac-sound` | Keep sound on the Mac's speakers too (by default they're muted while mirroring and unmuted after) |
| `--test` | Send a moving test pattern instead of the screen. Needs no Screen Recording permission; good for checking the TV side. |

Options combine: `mirror-tv --extend --smooth`.

Back on the remote also stops mirroring on the TV; the Mac side exits on its own.

## Expected quality

Measured on a Philips TA1 (MediaTek MT5896) and an M4 MacBook Air, both on 5 GHz Wi-Fi:

| Mode | Picture | Delay (capture → shown) |
|---|---|---|
| `mirror-tv` | 3024×1964 HEVC, up to 60 fps | ~20–30 ms average, occasional ~100 ms hitches |
| `mirror-tv --smooth` | same, steady 60 fps | ~165 ms, steady |
| `mirror-tv --extend` | 3840×2160 HEVC | as above; the Mac produces fewer frames at 4K |

The ~100 ms hitches came from the **Mac ↔ router** Wi-Fi link (pings to the router alone spike to ~93 ms). Turning off AWDL (AirDrop/Handoff) and keeping the TV's Wi-Fi out of power-save didn't remove them. `--smooth` hides them. An Ethernet adapter on the Mac most likely removes them.

For YouTube and Netflix, the TV's own apps always look better than mirroring.

## How it works

```
Mac: tvmirror (mac/tvmirror.swift)                 TV: MirrorActivity (app/.../Mirror.kt)
ScreenCaptureKit ─► VideoToolbox HEVC ─► TCP:7070 ─► MediaCodec (low-latency) ─► screen
   (screen+sound)     (hardware, low-latency)   │      AudioTrack (jitter buffer) ─► speakers
                                                 ◄──── timestamp of each shown frame (delay stats)
```

- `mirror-tv` opens the TV's Mirror screen over ADB (`am start -n dev.raul.tvhome/.MirrorActivity --es from <mac ip> --ei smoothMs <0|150>`), then starts `tvmirror` in the background. The PID is in `$TMPDIR/tvmirror.pid` and the log in `$TMPDIR/tvmirror.log`.
- The TV only accepts a stream from the Mac's IP, and closes the Mirror screen when the stream ends, after 30 s with no connection, or on Back.
- While the Mirror screen is open, the TV app holds a low-latency Wi-Fi lock and keeps the screen on.
- When the screen is still, the Mac repeats the last frame 10× per second; otherwise the TV's decoder would hold on to the first frame and show black.
- When Wi-Fi falls behind, video frames are skipped (never queued) and the next frame is a keyframe. Sound is never skipped.
- `--extend` uses macOS's private `CGVirtualDisplay` API (header in `mac/CGVirtualDisplay.h`, same as DeskPad/BetterDisplay). The virtual screen exists only while `tvmirror` runs. A macOS update could break it; plain mirroring doesn't depend on it.

### Reading the log

`$TMPDIR/tvmirror.log` prints every 2 s, for example:

```
last 2s: captured 118, frames in 120, frames out 120, kB sent 1600, repeats 2, delay avg 25 ms, worst 98 ms
```

- `captured`: real frames from macOS (only arrive when the screen changes)
- `repeats`: still-screen repeats
- `skipped` / `waiting for keyframe`: Wi-Fi couldn't keep up
- `delay`: capture → shown on the TV (the "worst" includes the trip back over Wi-Fi)

TV side: `adb -s $TV logcat -s Mirror:I`, which shows `frames in`, `shown` and `audio` per 2 s.

## Undo

- Commands: `rm ~/.local/bin/{wake-tv,sleep-tv,mirror-tv,stop-tv-mirror}`
- Screen Recording permission: System Settings → Privacy & Security → Screen & System Audio Recording
- Nothing else is changed on the TV; the Mirror screen is part of the home screen app.
