#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<'HELP'
Usage: fix-sony-home.sh [status | apply | restore]

  status   Show which activity Android sends Home to (default).
  apply    Set Dumb TV as Home and disable higher-priority Sony Home handlers if needed.
  restore  Restore the stock Google TV launcher and setup recovery package.

Use TV_SERIAL to select a device and ADB_BIN to select an adb executable.
HELP
}

action=${1:-status}
case "$action" in
    -h|--help) usage; exit 0 ;;
    status|apply|restore) ;;
    *) usage >&2; exit 2 ;;
esac
if (( $# > 1 )); then usage >&2; exit 2; fi

source "$(dirname "${BASH_SOURCE[0]}")/lib/adb.sh"
dumbtv_init_adb
TV_SERIAL=${TV_SERIAL:-$(dumbtv_adb get-serialno)}
tv_user=$(dumbtv_adb shell am get-current-user | tr -d '\r')
if [[ ! "$tv_user" =~ ^[0-9]+$ ]]; then
    printf 'Could not determine the TV user.\n' >&2
    exit 1
fi

stock=com.google.android.apps.tv.launcherx
recovery=com.google.android.tungsten.setupwraith

resolve_home() {
    dumbtv_adb shell cmd package resolve-activity --brief --user "$tv_user" \
        -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | tail -n 1
}

if [[ "$action" == status ]]; then
    home=$(resolve_home)
    printf 'Android Home for user %s routes to %s\n' "$tv_user" "$home"
    exit 0
fi

package_installed() {
    local packages
    packages=$(dumbtv_adb shell pm list packages --user "$tv_user" "$1" | tr -d '\r')
    grep -Fxq "package:$1" <<< "$packages"
}

manufacturer=$(dumbtv_adb shell getprop ro.product.manufacturer | tr -d '\r')
case "$manufacturer" in
    Sony|SONY|sony) ;;
    *) printf 'This Home fix is for Sony TVs. Detected manufacturer: %s\n' "$manufacturer" >&2; exit 1 ;;
esac

if [[ "$action" == restore ]]; then
    for package in "$recovery" "$stock"; do
        if package_installed "$package"; then
            dumbtv_adb shell pm default-state --user "$tv_user" "$package"
        fi
    done
    home=$(resolve_home)
    printf 'Android Home now routes to %s\n' "$home"
    exit 0
fi


home=$(resolve_home)
case "$home" in
    "$DUMBTV_PACKAGE/"*|dev.pk.bravia.launcher/*)
        printf 'Android already routes Home directly to %s\n' "$home"
        exit 0
        ;;
esac

if package_installed "$DUMBTV_PACKAGE"; then
    target="$DUMBTV_PACKAGE/.MainActivity"
elif package_installed dev.pk.bravia.launcher; then
    target=dev.pk.bravia.launcher/.MainActivity
else
    printf 'Install Dumb TV before changing the TV Home routing.\n' >&2
    exit 1
fi

dumbtv_adb shell cmd package set-home-activity --user "$tv_user" "$target"
target_package=${target%/*}
stock_changed=false
recovery_changed=false
rollback() {
    if "$recovery_changed"; then dumbtv_adb shell pm default-state --user "$tv_user" "$recovery" || true; fi
    if "$stock_changed"; then dumbtv_adb shell pm default-state --user "$tv_user" "$stock" || true; fi
}
trap rollback EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# A successful Home role assignment can still leave a higher-priority activity ahead.
while true; do
    home=$(resolve_home)
    case "$home" in
        "$target"|"$target_package/$target_package.MainActivity")
            trap - EXIT INT TERM
            printf 'Android now routes Home directly to %s\n' "$home"
            exit 0
            ;;
        "$stock/.home.HomeActivity")
            if "$stock_changed"; then break; fi
            stock_changed=true
            dumbtv_adb shell pm disable-user --user "$tv_user" "$stock"
            ;;
        "$recovery/.RecoveryActivity")
            if "$recovery_changed"; then break; fi
            recovery_changed=true
            dumbtv_adb shell pm disable-user --user "$tv_user" "$recovery"
            ;;
        *) break ;;
    esac
done

printf 'Android still routes Home to %s. Restoring any system packages changed by this run.\n' "$home" >&2
exit 1
