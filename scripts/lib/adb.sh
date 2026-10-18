#!/usr/bin/env bash
# Shared ADB setup. Source from a script; do not execute directly.
DUMBTV_PACKAGE='org.dumbtv.launcher'

dumbtv_init_adb() {
    if [[ -n "${ADB_BIN:-}" ]]; then
        DUMBTV_ADB="$ADB_BIN"
    elif command -v adb >/dev/null 2>&1; then
        DUMBTV_ADB="$(command -v adb)"
    elif [[ -n "${ANDROID_HOME:-}" && -x "$ANDROID_HOME/platform-tools/adb" ]]; then
        DUMBTV_ADB="$ANDROID_HOME/platform-tools/adb"
    elif [[ -n "${ANDROID_SDK_ROOT:-}" && -x "$ANDROID_SDK_ROOT/platform-tools/adb" ]]; then
        DUMBTV_ADB="$ANDROID_SDK_ROOT/platform-tools/adb"
    else
        printf 'ADB not found. Add platform-tools to PATH or set ADB_BIN to its executable.\n' >&2
        return 1
    fi
    if ! command -v "$DUMBTV_ADB" >/dev/null 2>&1; then
        printf 'ADB executable not found: %s\n' "$DUMBTV_ADB" >&2
        return 1
    fi
    dumbtv_adb get-state >/dev/null
}

dumbtv_adb() {
    if [[ -n "${TV_SERIAL:-}" ]]; then
        "$DUMBTV_ADB" -s "$TV_SERIAL" "$@"
    else
        "$DUMBTV_ADB" "$@"
    fi
}
