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

Same link every time (the APK on that page is replaced when we ship a fix):

**https://github.com/BigDaddyDawg/usb-boost/releases/tag/v2.1.1**

1. Download **`app-release.apk`**
2. Open **USB Boost** → **Turn on** (allow notifications if asked)
3. Play Spotify, then **pause and press play** if the volume does not jump
4. Drag **Boost level** until it sounds right

Android only hands another app’s sound over when playback starts. If Spotify was already playing, pause/play once so boost can lock on.

That's it. No PC, no ADB, no extra buttons.

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
