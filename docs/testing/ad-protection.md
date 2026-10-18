# Continuous ad protection

Open Settings → General → Ad protection. Allow Display over other apps, return, and select Start protection. Approve Android screen sharing, then open any streaming app. Monitoring follows the display across app switches every three seconds until explicitly stopped, Android ends projection, or a guard failure stops it. No app is automatically launched and no package filter restricts capture to Prime. The detector currently recognizes readable Ad badges in the upper-right region; detection in other apps/layouts still needs validation. It has no 60/90-second deadline and does not silently restart screen sharing after reboot.

Run commands from the repository root with JDK 17 and Android platform-tools on `PATH`. See [the README](../../README.md) for SDK setup and [the script guide](../../scripts/README.md) for device selection and exports.

## Build and install

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

An ad badge triggers a black overlay covering the center 80% of the screen, leaving 10% margins on all sides, and attempts to mute the media stream. The overlay appears independently of mute support. Audio capabilities and failed mute commands are logged and visible in the settings status. Two consecutive fresh badge-absent OCR observations restore audio and remove the cover. Unreadable/stale OCR breaks the absence sequence; countdown expiry alone never restores audio. Existing mute is preserved.

Back or OK while covered stops protection and restores audio owned by the guard. Settings Stop and notification Stop also stop it. A main-thread watchdog exits if no fresh OCR arrives for 12 seconds while covered. Android process termination cannot run cleanup. The focusable overlay does not request audio focus, but playback continuing underneath and remote escape require TV verification. External receiver/soundbar mute support depends on the TV audio route.

## Verify a new run

1. Confirm Settings shows Ad protection and Monitoring every 3 seconds.
2. Play an ad; confirm the cover appears and media mutes. Let it end; confirm restoration after two absent checks. The upper-right badge must remain readable around the cover.
3. Keep watching past 90 seconds and through a second ad to confirm monitoring continues.
4. In another run, use Back or OK while covered to confirm immediate exit.
5. Stop protection before pulling files to avoid an export changing while copied:

```sh
bash scripts/pull-ad-capture.sh
```

The report must contain `implementation: continuous_guard_v1`, `mode: continuous`, and `sample_interval_ms: 3000`. Re-pulling a completed previous run does not create a new capture. Android screen-sharing approval is required when starting another session.

## Bounded local diagnostics

Only the latest 30 full PNG screenshots, badge crops, and matching JSON records are retained. Slots increase throughout a session, so filenames need not start at zero. The report, timeline and HTML preview describe this rolling window; report totals describe the whole session. Event logs rotate at roughly 2 MB, keeping one previous segment. Slow OCR skips sampling slots rather than building a backlog of obsolete control decisions.

`events.jsonl` records capture/tagging, audio capabilities, mute/restoration, overlay show/hide, remote exit, skips, failures and stop reasons. `report.json` includes the implementation marker, interval, totals, retained frames, and session result. `timeline.jsonl` and `index.html` show the retained sequence. OCR text/boxes, source freshness, timing, tags, raw/normalized countdowns, and per-frame errors are retained. Countdowns accept multi-digit minutes, seconds 00–59, spaces, and O/o→0 only within timer tokens. No confidence values are fabricated.

MediaProjection and bundled ML Kit Latin OCR run on the TV. Protected video may be black while player chrome stays readable. No general vision model or remote inference service is used. Screenshot/text files stay local until pulled by ADB. A new session replaces the previous session’s files. Google ML Kit may collect its own SDK diagnostics; this is separate from screenshot uploads.
