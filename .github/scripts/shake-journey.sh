#!/bin/sh
# Deterministic translation of docs/journeys/shake-journey-emulator.xml.
#
# Drives the emulator's virtual accelerometer through the emulator console
# (`adb emu sensor set acceleration x:y:z`) and asserts the label shown for
# each state via the layout tree (`android layout`). The layout tree stays
# enumerable here because an injected sensor value is constant -- unlike a
# physical device, where the ever-changing acceleration text keeps the UI
# from idling. Thresholds: >11 m/s^2 = small shake, >16 = big, 1s hold
# before returning to idle.
#
# Usage: shake-journey.sh <evidence-dir> [--install <apk>]
#   <evidence-dir>   where per-step screenshots and layout dumps are written
#   --install <apk>  install and launch the APK first (applicationId and
#                    launcher activity resolved from the APK, not hardcoded);
#                    without it the app must already be on screen, e.g. when
#                    running against an instance restored from a snapshot.
set -eu

EVIDENCE="$1"; shift
mkdir -p "$EVIDENCE"

if [ "${1:-}" = "--install" ]; then
  APK="$2"
  SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
  # Read the applicationId out of the APK rather than hardcoding it: a
  # release packaging block drops the .debug suffix and a hardcoded name
  # silently stops resolving.
  AAPT="$(ls -d "$SDK"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1)"
  [ -n "$AAPT" ] || { echo "ERROR: no aapt2 found under $SDK/build-tools"; exit 1; }
  PKG="$("$AAPT" dump packagename "$APK")"
  [ -n "$PKG" ] || { echo "ERROR: could not read applicationId from $APK"; exit 1; }
  echo "shake journey: installing and launching $PKG"
  adb shell settings put global hide_error_dialogs 1 || true
  adb shell input keyevent KEYCODE_WAKEUP || true
  adb shell wm dismiss-keyguard || true
  android run --apks "$APK" || {
    echo "android run failed; falling back to adb"
    adb install -r "$APK"
    CMP="$(adb shell cmd package resolve-activity --brief "$PKG" 2>/dev/null | tail -1 | tr -d '\r')"
    case "$CMP" in
      */*) ;;
      *) echo "ERROR: could not resolve a launcher activity for $PKG (got '$CMP')"; exit 1 ;;
    esac
    adb shell am start -n "$CMP"
  }
fi

# Not every emulator implements the sensor console commands: the preview
# package (emulators/latest) answers "KO: not implemented" as of 41.1.9,
# while the stable SDK and canary emulators accept them. Probe with a
# harmless rest-value write and skip the journey loudly when unsupported —
# a misleading FAIL would read as an app regression.
PROBE="$(adb emu sensor set acceleration 0:9.81:0 2>&1 || true)"
case "$PROBE" in
  *KO*|*"not implemented"*)
    echo "SKIPPED: this emulator does not implement 'sensor set' console commands ($PROBE)"
    exit 0
    ;;
esac

# Poll the layout tree until the expected label is on screen.
expect() {
  LABEL="$1"; STEP="$2"
  for _ in $(seq 1 30); do
    android layout -o "$EVIDENCE/layout-$STEP.json" >/dev/null 2>&1 || true
    if grep -qF "$LABEL" "$EVIDENCE/layout-$STEP.json" 2>/dev/null; then
      android screen capture -o "$EVIDENCE/$STEP.png" || true
      echo "PASS: '$LABEL'"
      return 0
    fi
    sleep 1
  done
  echo "FAIL: expected '$LABEL' on screen (step $STEP)"
  android screen capture -o "$EVIDENCE/$STEP-FAIL.png" || true
  head -c 4000 "$EVIDENCE/layout-$STEP.json" 2>/dev/null || echo "(no layout dump)"
  exit 1
}

expect "Shake your phone!" 1-idle
adb emu sensor set acceleration 0:13:0
expect "Small Shake Detected!" 2-small
adb emu sensor set acceleration 0:20:0
expect "Big Shake Detected!" 3-big
adb emu sensor set acceleration 0:9.81:0
expect "Shake your phone!" 4-reset
echo "Shake journey PASSED"
