#!/bin/sh
# Runs `android build "$@"` and fails the step when Lightbuild reports a failure.
#
# The CLI's exit code cannot be trusted for a verdict:
#   - up to 1.0.16261425 it exited 0 no matter what happened (compile error,
#     failing unit test, nonexistent target);
#   - 1.0.16457483 propagates Lightbuild's exit code, which fixes plain builds
#     but makes `android build test` / `query` exit 1 even when they succeed
#     (see the pre-flight note below);
#   - a bare `android build` still only prints usage and exits 0.
# Lightbuild does print "BUILD FAILED" / "Error: Lightbuild build failed with
# exit code 1", so this wrapper ignores the exit code, tees the output, and
# turns any reported failure, or the absence of a "BUILD SUCCESS" line, into
# exit 1. Without it a broken build only surfaces later, when a job tries to
# install an APK that was never written -- or a green test run goes red.
set -u

LOG="$(mktemp)"
android build "$@" 2>&1 | tee "$LOG"

# With Lightbuild 0.0.20-alpha01 the CLI runs a target-less `build` before
# `test` (and `query`), which Lightbuild rejects with "Target is required for
# command build" / "Fatal: Build Failed" before the real invocation starts.
# Every Lightbuild invocation opens with the "Lightbuild is experimental"
# banner, so only the output after the last banner decides the verdict.
RESULT="$(mktemp)"
awk '/Lightbuild is experimental/ { buf = "" } { buf = buf $0 "\n" } END { printf "%s", buf }' "$LOG" > "$RESULT"

if grep -qE 'BUILD FAILED|Lightbuild build failed|Fatal: Build Failed' "$RESULT" \
   || ! grep -q 'BUILD SUCCESS' "$RESULT"; then
  echo "ERROR: android build $*: Lightbuild reported a failure" >&2
  rm -f "$LOG" "$RESULT"
  exit 1
fi
rm -f "$LOG" "$RESULT"
