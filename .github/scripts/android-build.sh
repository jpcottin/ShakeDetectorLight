#!/bin/sh
# Runs `android build "$@"` and fails the step when Lightbuild reports a failure.
#
# The Android CLI (verified on 1.0.16261425) exits 0 no matter what happened:
# a compile error, a failing unit test under `android build test`, a target
# that does not exist, and even a bare `android build` that only prints usage
# all return 0. Lightbuild does print "BUILD FAILED" / "Error: Lightbuild build
# failed with exit code 1", so this wrapper tees the output and turns any
# reported failure, or the absence of a "BUILD SUCCESS" line, into exit 1.
# Without it a broken build only surfaces later, when a job tries to install
# an APK that was never written.
set -u

LOG="$(mktemp)"
android build "$@" 2>&1 | tee "$LOG"

if grep -qE 'BUILD FAILED|Lightbuild build failed|Fatal: Build Failed' "$LOG" \
   || ! grep -q 'BUILD SUCCESS' "$LOG"; then
  echo "ERROR: android build $*: Lightbuild reported a failure but the CLI exited 0" >&2
  rm -f "$LOG"
  exit 1
fi
rm -f "$LOG"
