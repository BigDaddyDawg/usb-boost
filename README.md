# USB Boost

Free Android app for your **Pixel 8** that boosts music when you're on **USB / Android Auto** — the same approach Poweramp Equalizer uses, without a subscription.

## How it works

Poweramp doesn't re-route your audio. It attaches Android's built-in effects to whatever app is playing:

1. **Find the active audio session** (Spotify, YouTube Music, etc.)
2. **Insert an equalizer on that session** — boost slider is volume, plus optional tone (Flat / Podcast / Rock / Country / Custom).
3. **Switch on when USB is connected** so car audio gets the boost, not your pocket

USB Boost does the same:

- Foreground service keeps effects alive while you drive
- Detects **USB / wired / car Bluetooth** output
- Tracks playback sessions
- Remembers **home vs car** levels
- In-app update from this GitHub release

## Install on your phone

Same link every time (the APK on that page is replaced when we ship a fix):

**https://github.com/BigDaddyDawg/usb-boost/releases/tag/v2.1.1**

1. Download **`app-release.apk`**
2. Open **USB Boost** → **Turn on** (allow notifications if asked)
3. Play Spotify. If volume does not jump, tap **Lock onto Spotify**
4. Drag **Boost level**. Try **Podcast / Rock / Country** if you want a different sound

After this version, **Check for update** in the app installs the next one (you still tap Install once).

That's it. No PC, no ADB.

## Settings

| Setting | What it does |
|--------|----------------|
| **Turn on / off** | Master on/off |
| **Lock onto Spotify** | Pause/play so Android hands over the sound |
| **Boost level** | Volume (up to 12 dB) |
| **Sound presets** | Flat, Podcast, Rock, Country, or Custom sliders |
| **Only when USB / car is connected** | Skips the phone speaker |
| **Turn on when I plug into the car** | Auto-on + car levels |
| **Check for update** | Download the latest APK in-app |
| **Quick Settings tile** | Add USB Boost in the shade for a one-tap toggle |

## Build locally

Requires Android SDK + JDK 17:

```bash
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release-unsigned.apk`

## Notes

- This is **not** on the Play Store — sideload only (your personal app).
- USB/Android Auto volume quirks vary by car; tweak sliders to taste.
