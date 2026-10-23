# Google TV makeover

Make a laggy Google TV / Android TV faster and give it a clean, Apple TV–style home screen, all over Wi-Fi from your computer, no root.

Built and tested on a **Philips Ambilight Google TV** (Android TV 14, 2.8 GB RAM). Most steps work on any Google TV; brand-specific parts are marked.

## What's in here

| Part | What you get | Guide |
|---|---|---|
| 1. Connect | Control the TV from your Mac/PC over Wi-Fi with ADB | [docs/01-connect.md](docs/01-connect.md) |
| 2. Speed up | Faster animations, remove apps you don't use, limit background apps, turn off brand extras. **You pick each one.** | [docs/02-speed-up.md](docs/02-speed-up.md) · [catalog](docs/tweaks-catalog.md) |
| 3. Home screen | A light custom home screen: app dock, full-screen photos, screensaver, settings panel, dock editing | [docs/03-home-screen.md](docs/03-home-screen.md) |
| 4. Home button | Make the remote's Home button open the new home screen | [docs/04-home-button.md](docs/04-home-button.md) |
| 5. Screensavers | Load your own photos, or a pack of 700 Chromecast backgrounds | [docs/05-screensavers.md](docs/05-screensavers.md) |
| 6. Twitch card | A "Live now" card for your favourite streamers when you select Twitch | [docs/06-twitch.md](docs/06-twitch.md) |

Everything is reversible: [docs/undo.md](docs/undo.md). Problems: [docs/troubleshooting.md](docs/troubleshooting.md).

## Do it with Claude (recommended)

1. Install [Claude Code](https://claude.com/claude-code).
2. Clone this repo and open it:
   ```bash
   git clone https://github.com/chirigutraul/google-tv-makeover.git
   cd google-tv-makeover
   claude
   ```
3. Say: **"Help me optimise my TV"**.

Claude reads [CLAUDE.md](CLAUDE.md) and walks you through it: it checks your TV first (read-only), explains each option, and **changes nothing without your OK**. You can stop at any part, for example only speed it up and keep Google's home screen.

## Do it by hand

Follow the guides in order. Each one lists the exact commands, what they do, and how to undo them.

## The home screen

- **Dock** of your favourite apps using each app's real TV banner. Hold OK on a tile to move, remove or add apps.
- **Full-screen photos** that slowly drift in screensaver mode (after 2 minutes idle), with a clock.
- **Settings panel**: HDMI inputs, Picture, Sound, Wi-Fi, all TV settings.
- **Twitch "Live now"** card (optional).
- About **90 MB of RAM**, versus roughly 150 MB for the Google TV home screen.

Source: [app/](app/), Kotlin + Jetpack Compose. Your personal settings (dock, Twitch keys, streamers) go in `local.properties`, which is never committed. See [local.properties.example](local.properties.example).

## Requirements

- A Google TV / Android TV on the same Wi-Fi as your computer
- macOS, Linux or Windows with [Android platform tools](https://developer.android.com/tools/releases/platform-tools) (ADB)
- For the home screen: [Android Studio](https://developer.android.com/studio) (or just its SDK + JDK 17)

## Disclaimer

These are unofficial tweaks to your own TV. They use standard Android developer tools and are reversible, but you apply them at your own risk. Not affiliated with Google, Philips, Twitch or any app mentioned.

## License

[MIT](LICENSE)
