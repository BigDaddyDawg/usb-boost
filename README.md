# USB Boost

Free Android app for your **Pixel 8** that boosts music when you're on **USB / Android Auto** — the same approach Poweramp Equalizer uses, without a subscription.

## How it works

Poweramp doesn't re-route your audio. It attaches Android's built-in effects to whatever app is playing:

1. **Find the active audio session** (Spotify, YouTube Music, etc.)
2. **Insert effects on that session** — `LoudnessEnhancer` (preamp), `Equalizer`, `BassBoost`
3. **Switch on when USB is connected** so car audio gets the boost, not your pocket

USB Boost does the same:

- Foreground service keeps effects alive while you drive
- Detects **USB / wired / car Bluetooth** output
- Tracks playback sessions (broadcast + playback callback + optional enhanced dump)
- Applies your boost + bass sliders automatically

## Install on your phone

1. Open **[Releases](https://github.com/BigDaddyDawg/usb-boost/releases)** on your Pixel
2. Download the latest **`app-release.apk`**
3. Install, open **USB Boost**, tap **Set up & start**
4. Plug into the car via USB and play music in Spotify (or your usual app)

No PC or ADB command required — the app configures itself for phone-only use.

## One-time setup (optional, for power users)

Enhanced session detection via `DUMP` is optional. PC ADB (only if you want every app covered):

```bash
adb shell pm grant com.usbboost.app android.permission.DUMP
```

## Settings

| Setting | What it does |
|--------|----------------|
| **Enable boost** | Master on/off |
| **Only boost in car / USB** | Skips boost on phone speaker |
| **Preamp boost** | Loudness gain (up to ~8 dB) |
| **Bass lift** | Low-end EQ + bass boost |
| **Enhanced session detection** | Uses DUMP to find all players |
| **Legacy mode** | Attach to global mix (session 0) — try if a player is stubborn |
| **Start when phone reboots** | Auto-start service after boot |

## Build locally

Requires Android SDK + JDK 17:

```bash
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release-unsigned.apk`

## Notes

- This is **not** on the Play Store — sideload only (your personal app).
- USB/Android Auto volume quirks vary by car; tweak sliders to taste.
- If audio goes quiet after an Android update, re-run the DUMP grant command.
