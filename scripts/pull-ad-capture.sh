#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" == '--help' || "${1:-}" == '-h' ]]; then
    printf 'Usage: bash scripts/pull-ad-capture.sh [NEW_OUTPUT_DIRECTORY]\nExport the most recent stopped ad-protection session. Requires ADB and Python 3.\nEnvironment: ADB_BIN, TV_SERIAL. See scripts/README.md.\n'
    exit 0
fi
if [[ $# -gt 1 ]]; then
    printf 'Expected at most one output directory. Use --help for usage.\n' >&2
    exit 1
fi
command -v python3 >/dev/null 2>&1 || { printf 'Python 3 is required.\n' >&2; exit 1; }
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/adb.sh"
dumbtv_init_adb
DUMBTV_OUTPUT="${1:-./artifacts/ad-capture-$(date +%Y%m%d-%H%M%S)}"
DUMBTV_REMOTE="/sdcard/Android/data/$DUMBTV_PACKAGE/files/ad-capture"
if [[ -e "$DUMBTV_OUTPUT" ]]; then
    printf 'Use a new output directory so old frames cannot mix with this session: %s\n' "$DUMBTV_OUTPUT" >&2
    exit 1
fi
mkdir -p -- "$DUMBTV_OUTPUT"
if ! dumbtv_adb pull "$DUMBTV_REMOTE/." "$DUMBTV_OUTPUT/"; then
    DUMBTV_ARCHIVE="$(mktemp /tmp/dumbtv-ad-capture.XXXXXX)"
    trap 'rm -f "$DUMBTV_ARCHIVE"' EXIT
    dumbtv_adb exec-out run-as "$DUMBTV_PACKAGE" tar -C "$DUMBTV_REMOTE" -cf - . > "$DUMBTV_ARCHIVE"
    tar -xf "$DUMBTV_ARCHIVE" -C "$DUMBTV_OUTPUT"
fi
python3 - "$DUMBTV_OUTPUT" <<'PY'
import json, pathlib, sys
folder = pathlib.Path(sys.argv[1]).resolve()
report = json.loads((folder / 'report.json').read_text())
frames = report['frames']
print(f"Saved to {folder}")
print(f"Retained screenshots: {report['captured_frames']}; tagged: {report['tagged_frames']}")
print("Implementation:", report.get("implementation", "diagnostic"), "Interval:", report["sample_interval_ms"], "ms")
print(f"Result: {report['result']}")
print("Ad slots:", [frame['slot'] for frame in frames if frame.get('ad_detected') is True])
print(f"Preview: {folder / 'index.html'}")
PY
