#!/bin/sh
# Runs a Lightbuild target through the `android` CLI and fails the step when
# Lightbuild reports a failure.
#
#   sh .github/scripts/android-build.sh "app:app-debug.apk"   # build a target
#   sh .github/scripts/android-build.sh test "ui:hostTest"    # run tests
#   sh .github/scripts/android-build.sh query "..."           # list targets
#
# The CLI's exit code cannot be trusted for a verdict:
#   - up to 1.0.16261425 it exited 0 no matter what happened (compile error,
#     failing unit test, nonexistent target);
#   - 1.0.16457483 propagates Lightbuild's exit code, which fixes plain builds
#     but makes `android build test` / `query` exit 1 even when they succeed;
#   - a bare `android build` still only prints usage and exits 0.
# Lightbuild does print "BUILD FAILED" / "BUILD SUCCESS", so this wrapper
# ignores the exit code, collects the output, and turns any reported failure,
# or the absence of a "BUILD SUCCESS" line, into exit 1. Without it a broken
# build only surfaces later, when a job tries to install an APK that was
# never written -- or a green test run goes red.
#
# Android CLI 1.1 changed two things this wrapper absorbs:
#   - `android build` only ever sends `build <target>` to Lightbuild; the
#     `test`, `query` and `resolve` commands are gone, and `android build
#     "ui:hostTest"` merely assembles the tests. Those commands drive the
#     project's pinned Lightbuild daemon directly, the way the CLI used to.
#   - Lightbuild's output is no longer streamed: the CLI prints
#     "Build log: /tmp/build-log-<n>.log" and the verdict lines live in that
#     file, so it is shown and read.
set -u

LOG="$(mktemp)"
case "${1:-}" in
  test|query|resolve)
    SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$(android info 2>/dev/null | sed -n 's/^sdk: //p' | head -n 1)}}"
    VERSION="$(sed -n 's/^  *version: *"\(.*\)".*/\1/p' project.lightbuild.yaml | head -n 1)"
    DAEMON="$SDK/build/lightbuild/$VERSION/lightbuild-daemon"
    if [ ! -x "$DAEMON" ]; then
      # The CLI fetches the pinned daemon on its first build.
      android build "${2:-}" >/dev/null 2>&1 || true
    fi
    "$DAEMON" "$PWD" --caller_id=android-cli "$@" 2>&1 | tee "$LOG"
    ;;
  *)
    android build "$@" 2>&1 | tee "$LOG"
    ;;
esac

# With Lightbuild 0.0.20-alpha01 the 1.0 CLI ran a target-less `build` before
# `test` (and `query`), which Lightbuild rejects with "Target is required for
# command build" / "Fatal: Build Failed" before the real invocation starts.
# Every Lightbuild invocation opens with the "Lightbuild is experimental"
# banner, so only the output after the last banner decides the verdict.
RESULT="$(mktemp)"
awk '/Lightbuild is experimental/ { buf = "" } { buf = buf $0 "\n" } END { printf "%s", buf }' "$LOG" > "$RESULT"

BUILD_LOG="$(sed -n 's/^Build log: \(.*\)$/\1/p' "$LOG" | tail -n 1)"
if [ -n "$BUILD_LOG" ] && [ -f "$BUILD_LOG" ]; then
  echo "----- $BUILD_LOG -----"
  cat "$BUILD_LOG"
  cat "$BUILD_LOG" >> "$RESULT"
fi

if grep -qE 'BUILD FAILED|Lightbuild build failed|Fatal: Build Failed' "$RESULT" \
   || ! grep -q 'BUILD SUCCESS' "$RESULT"; then
  echo "ERROR: android build $*: Lightbuild reported a failure" >&2
  rm -f "$LOG" "$RESULT"
  exit 1
fi
rm -f "$LOG" "$RESULT"
