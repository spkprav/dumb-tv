# Developer scripts

These are optional diagnostics for people developing Dumb TV. Installing and using the launcher does not require running them. Run examples from the repository root; the scripts also work from another directory, with relative output paths resolved from your current directory.

## Requirements

- Bash, Android SDK platform-tools (`adb`), and an authorized TV connection.
- Dumb TV installed as `org.dumbtv.launcher` and opened at least once.
- Python 3 and `tar` for exporting ad captures.
- A terminal with interactive input for performance capture.

Connect your TV using your firmware's USB/network debugging workflow. Approve the connection on the TV, then check it with `adb devices -l`. Scripts do not pair devices, enable debugging, install apps, grant permissions, or change the default launcher.

ADB lookup order is `ADB_BIN`, `adb` on `PATH`, `$ANDROID_HOME/platform-tools/adb`, then `$ANDROID_SDK_ROOT/platform-tools/adb`.

```sh
# Select one TV when multiple devices are connected.
TV_SERIAL=192.0.2.10:5555 bash scripts/capture-tv-performance.sh

# Or provide an ADB executable explicitly.
ADB_BIN="$ANDROID_HOME/platform-tools/adb" bash scripts/pull-ad-capture.sh
```

`192.0.2.10:5555` is a placeholder: use the identifier from `adb devices -l`. Both scripts support `--help`. They require a new output directory and refuse to overwrite an existing one. A failed run can leave partial output; choose another directory for a retry.

## Performance capture

Install the performance variant for representative measurements:

```sh
./gradlew assemblePerformance
adb install -r app/build/outputs/apk/performance/app-performance.apk
bash scripts/capture-tv-performance.sh
```

Open Dumb TV, navigate the dock, tap and hold OK, open Settings, and return from another app. Press Enter in the terminal when finished.

Default output: `artifacts/performance-<timestamp>/`.

| File | Contents |
| --- | --- |
| `reset.txt` | Output from resetting the launcher's graphics statistics |
| `input.txt` | Filtered `DumbTvInput` log messages |
| `frames.txt` | Android graphics/frame timing statistics |
| `memory.txt` | Android memory statistics for the launcher |

The script resets graphics statistics and temporarily sets `log.tag.DumbTvInput` to `DEBUG`. On normal exit, failure, Ctrl-C, or termination it attempts to stop its local logcat process and restore the prior log level. A lost connection, power loss, or forced kill can prevent restoration. Reconnect and clear the override manually if needed:

```sh
adb shell 'setprop log.tag.DumbTvInput ""'
```

Use the same `adb -s <serial>` device selection if multiple devices are attached. Raw frame statistics are evidence for investigation, not an automatic performance score.

## Export ad-protection diagnostics

Start Ad protection on the TV and approve the required overlay and screen-sharing prompts. Reproduce the behavior, then **stop protection before exporting** so the files do not change during transfer.

```sh
bash scripts/pull-ad-capture.sh
# Optional custom destination:
bash scripts/pull-ad-capture.sh ./artifacts/ad-investigation
```

Default output: `artifacts/ad-capture-<timestamp>/`.

The script copies `/sdcard/Android/data/org.dumbtv.launcher/files/ad-capture` from the selected TV. It first uses `adb pull`. If the firmware blocks that access, it tries `run-as` and a temporary tar archive; this fallback requires a debuggable build. The local temporary archive is deleted on exit. The script prints a summary from `report.json` and the location of `index.html` for local viewing.

The export can include screenshots, image crops, OCR text, JSON reports, and event logs. A new capture session replaces the previous session on the TV. Exporting does not start a capture or delete the TV's files. No files are uploaded by either script.

See [the ad-protection test guide](../docs/testing/ad-protection.md) for expected behavior, manual checks, retention, and report fields.

## Troubleshooting and cleanup

- **Unauthorized/offline device:** approve debugging on the TV and confirm `adb devices -l` shows `device`.
- **Multiple devices:** set `TV_SERIAL` to the exact device identifier.
- **Missing capture/report:** run Ad protection in the renamed Dumb TV app, stop it, and retry with a new output directory. Captures from the old development package are in a different app directory.
- **`run-as` denied:** install the debug build or use firmware that allows direct `adb pull`; the performance variant is not debuggable.
- **Interrupted export:** the directory may contain incomplete files. Inspect or delete that directory locally and retry with a new one.

Default outputs live under `artifacts/`, which Git ignores. Delete individual run directories when you no longer need them. Custom destinations outside `artifacts/` may not be ignored. Review screenshots and logs for profile names, titles, and other personal data before sharing them.

## Layout

- `capture-tv-performance.sh`: interactive performance collection.
- `pull-ad-capture.sh`: export and summarize an existing ad-protection session.
- `lib/adb.sh`: shared package ID, executable lookup, and device selection; sourced by both scripts.

Builds use the repository's Gradle wrapper directly. There are no publishing or release scripts.
