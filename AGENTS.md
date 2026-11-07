This project uses the Lightbuild build system: the version is pinned in
project.lightbuild.yaml (`lightbuild.version`), the module list lives there too,
and each module has a lightbuild.yaml.

Build and test it with the `lightbuild` CLI that ships inside the Lightbuild
SDK package. Install the pinned package once with the Android CLI, then its
CLI:

    android sdk install build/lightbuild/0.0.30
    bash "$(android info sdk)/build/lightbuild/0.0.30/cli/install.sh"   # ~/.local/bin/lightbuild

    lightbuild query "..."            # list targets (--output=files for where they write)
    lightbuild build "app"            # debug APK (and ui, its dependency)
    lightbuild test "ui:hostTest"     # unit tests on the JVM
    lightbuild test "ui:deviceTest"   # Compose UI tests on the connected device/emulator
    lightbuild resolve                # regenerate resolved.deps after a dependency change

The exit code is the verdict: 1 on a compile error, a failing test or an
unknown target. `android build "<target>"` (with ANDROID_CLI_BUILD=true) still
builds a named target through the same pinned daemon but cannot test or query;
CI uses `lightbuild` everywhere (`.github/scripts/install-lightbuild.sh`
installs it). Run `lightbuild help` for the commands.

Never add AI attribution (Co-Authored-By, "Generated with…") to commits, PRs or files.
