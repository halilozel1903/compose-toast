#!/usr/bin/env bash
# Captures README screenshots of the sample app on a running emulator.
# Taps can't be timed reliably through adb, so the sample shows each scene's toasts from the `scene` extra.
# They never time out, and every capture is checked for the expected text and for a blank image.
#
#   bash scripts/screenshots.sh          # phone: styles, top and action, light and dark
#   bash scripts/screenshots.sh tablet   # tablet: tablet-light.png
set -euo pipefail
source "$(dirname "$0")/screenshot-lib.sh"

device="${1:-phone}"

# The text each scene must show; the capture fails without it.
expected_text() {
  case "$1" in
    styles) echo "Payment received" ;;
    top) echo "Syncing" ;;
    action) echo "Undo" ;;
    tablet) echo "Payment received" ;;
  esac
}

install_sample
if [ "$device" = tablet ]; then
  set_night_mode light
  fresh_launch --es scene tablet
  capture "tablet-light" "$(expected_text tablet)"
else
  for mode in light dark; do
    set_night_mode "$mode"
    for scene in styles top action; do
      fresh_launch --es scene "$scene"
      capture "$scene-$mode" "$(expected_text "$scene")"
    done
  done
fi
