#!/bin/sh
# Installs the Lightbuild version pinned in project.lightbuild.yaml together
# with its `lightbuild` CLI, and puts the CLI on the job's PATH.
#
#   sh .github/scripts/install-lightbuild.sh
#
# Lightbuild ships as an SDK package (build/lightbuild/<version>) that the
# Android CLI installs; since 0.0.30 the package carries the standalone
# `lightbuild` CLI under cli/, with an installer that copies it to
# ~/.local/bin. The pin in project.lightbuild.yaml is the only place the
# version is written, so it is read from there. The SDK download drops now
# and then on a loaded runner, so the install is retried.
set -u
version="$(sed -n 's/^  *version: *"\(.*\)".*/\1/p' project.lightbuild.yaml | head -n 1)"
if [ -z "$version" ]; then
  echo "ERROR: no lightbuild.version pin in project.lightbuild.yaml" >&2
  exit 1
fi
attempts="${LIGHTBUILD_INSTALL_ATTEMPTS:-3}"
i=1
while :; do
  if android sdk install "build/lightbuild/$version"; then
    break
  fi
  if [ "$i" -ge "$attempts" ]; then
    echo "ERROR: build/lightbuild/$version install failed after $attempts attempts" >&2
    exit 1
  fi
  echo "build/lightbuild/$version install attempt $i failed, retrying in 15 s"
  i=$((i + 1))
  sleep 15
done
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$(android info sdk)}}"
installer="$sdk/build/lightbuild/$version/cli/install.sh"
if [ ! -f "$installer" ]; then
  echo "ERROR: $installer not found: Lightbuild $version ships no CLI (0.0.30 is the first that does)" >&2
  exit 1
fi
bash "$installer"
echo "$HOME/.local/bin" >> "${GITHUB_PATH:-/dev/null}"
"$HOME/.local/bin/lightbuild" --version
