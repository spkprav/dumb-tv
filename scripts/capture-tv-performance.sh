#!/usr/bin/env bash
# Collect local launcher performance diagnostics; see scripts/README.md.
set -euo pipefail
if [[ "${1:-}" == '--help' || "${1:-}" == '-h' ]]; then
    printf 'Usage: bash scripts/capture-tv-performance.sh [NEW_OUTPUT_DIRECTORY]\nCollect interactive frame, memory, and input diagnostics. Requires ADB.\nEnvironment: ADB_BIN, TV_SERIAL. See scripts/README.md.\n'
    exit 0
fi
if [[ $# -gt 1 ]]; then
    printf 'Expected at most one output directory. Use --help for usage.\n' >&2
    exit 1
fi
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/adb.sh"
dumbtv_init_adb
REPORT_DIR="${1:-./artifacts/performance-$(date +%Y%m%d-%H%M%S)}"
if [[ -e "$REPORT_DIR" ]]; then
    printf 'Choose a new output directory: %s\n' "$REPORT_DIR" >&2
    exit 1
fi
mkdir -p -- "$REPORT_DIR"
DUMBTV_LOG_PID=''
DUMBTV_PREVIOUS_LOG_LEVEL="$(dumbtv_adb shell getprop log.tag.DumbTvInput | tr -d '\r\n')"
cleanup() {
    if [[ -n "$DUMBTV_LOG_PID" ]]; then
        kill "$DUMBTV_LOG_PID" 2>/dev/null || true
        wait "$DUMBTV_LOG_PID" 2>/dev/null || true
    fi
    dumbtv_adb shell setprop log.tag.DumbTvInput "\"$DUMBTV_PREVIOUS_LOG_LEVEL\"" >/dev/null 2>&1 || true
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
dumbtv_adb shell setprop log.tag.DumbTvInput DEBUG
dumbtv_adb shell dumpsys gfxinfo "$DUMBTV_PACKAGE" reset > "$REPORT_DIR/reset.txt"
dumbtv_adb logcat -v threadtime -T 1 -s DumbTvInput:D '*:S' > "$REPORT_DIR/input.txt" &
DUMBTV_LOG_PID=$!
printf 'On the TV: navigate apps, tap/hold OK, open/close menus, and return from another app.\n'
read -r -p 'Press Enter here when finished: ' DUMBTV_CAPTURE_DONE
dumbtv_adb shell dumpsys gfxinfo "$DUMBTV_PACKAGE" framestats > "$REPORT_DIR/frames.txt"
dumbtv_adb shell dumpsys meminfo "$DUMBTV_PACKAGE" > "$REPORT_DIR/memory.txt"
printf 'Saved diagnostics to %s\n' "$REPORT_DIR"
