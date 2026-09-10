# Shake Detector Light

[![CI](https://github.com/jpcottin/ShakeDetectorLight/actions/workflows/ci.yml/badge.svg)](https://github.com/jpcottin/ShakeDetectorLight/actions/workflows/ci.yml)

An Android app that detects when you shake your phone, classifies the shake as
**small** or **big**, gives haptic feedback (vibration), and shows the live
accelerometer magnitude on screen.

Its distinguishing feature is not the app itself but **how it is built**: it is
a fully working example of a project using **Lightbuild**, Google's new
declarative build system, driven entirely from the **Android CLI** — no Gradle
files anywhere in the repository.

```
Shake your phone!          →  resting (magnitude ≈ 9.81, gravity)
Small Shake Detected!      →  magnitude > 11   (pink, vibrates)
Big Shake Detected!        →  magnitude > 16   (purple, vibrates)
```

| Idle | Small shake | Big shake |
|:---:|:---:|:---:|
| <img src="docs/idle.png" width="250" alt="Idle state"> | <img src="docs/small-shake.png" width="250" alt="Small shake state"> | <img src="docs/big-shake.png" width="250" alt="Big shake state"> |

All three captures were taken on an emulator while driving the virtual
accelerometer (`adb emu sensor set acceleration x:y:z`) — a handy way to test
sensor apps without physically shaking anything. Note that the shake colours
come from the Material 3 scheme, so with dynamic colour on Android 12+ they
follow the device wallpaper rather than being fixed pink/purple.

## Technologies used

### Build system: Lightbuild

[Lightbuild](https://developer.android.com/tools/agents/lightbuild) is a
declarative, YAML-based build configuration currently available to trusted
testers. Instead of imperative Gradle scripts, the whole build is described by:

| File | Role |
|---|---|
| `project.lightbuild.yaml` | Project name, module list, repositories, Kotlin/Java versions, Lightbuild version |
| `app/lightbuild.yaml` | Application module: `applicationId`, SDK levels, R8 release optimization, dependency on `//ui` |
| `ui/lightbuild.yaml` | Library module: Maven dependencies, unit/instrumented test configuration |
| `*/resolved.deps` | Pinned dependency resolution files for reproducible, offline-capable builds |

The full set of keys Lightbuild accepts is described by the JSON schemas bundled
inside its own jar (`lightbuild.yaml.schema.json` and
`project.lightbuild.yaml.schema.json`) — handy when the online docs are thin.
You can also read exactly what your YAML was translated into: Lightbuild writes
the generated Gradle build to `.lightbuild/gradle/` (gitignored), which is the
quickest way to confirm a key actually took effect.

#### R8 / release optimization

Release builds are shrunk and obfuscated:

```yaml
android:
  packaging:
    release:
      optimization:
        enable: true
        keepRules:
          includeDefault: true
```

`includeDefault` pulls in the default keep rules, which matter here because the
Navigation 3 back stack is serialized via `kotlinx.serialization`. The effect is
large for a demo app — **935 KB** release APK against 11.3 MB for debug — and
`app/build/outputs/mapping/release/mapping.txt` is produced for deobfuscating
release stack traces (CI archives it).

#### Alpha rough edges found while building this

Two schema-valid keys that currently do nothing, both verified against
0.0.10-alpha01 by inspecting the generated Gradle and by experiment:

| Key | Behaviour |
|---|---|
| `kotlin.allWarningsAsErrors` | Accepted; never reaches the compiler — a deliberate unused-variable warning still builds. Left in the config so it takes effect once implemented. |
| `kotlin.jvmTarget` | Fails validation as *"integer found, string expected"* whether written `17` or `"17"` — the YAML parser coerces the string to an integer before the schema check. Project-level `build.java.version` works instead. |

Two behavioural surprises, both triggered by simply declaring an
`android.packaging.release` block and neither warned about:

1. **It used to change what a bare `android build` builds**, from
   `//app:buildDebug` to `//app:buildRelease`, so the debug APK stopped
   appearing under `app/build/outputs/apk/debug/`. Since Android CLI
   1.0.16261425 the story is different but no better: a bare `android build`
   builds *nothing* — it prints the command's usage and exits 0, so a CI step
   that relies on it silently does no work. `android build "//app"` (an alias
   for `//app:main:apk:debug`) builds the debug APK regardless of the packaging
   block. CI names the debug target explicitly either way.
2. **It drops the `.debug` applicationId suffix from debug builds.** The debug
   applicationId goes from `com.jpcottin.shakedetectortest.debug` back to
   `com.jpcottin.shakedetectortest`, so debug and release builds can no longer
   be installed side by side. Lightbuild exposes no `applicationIdSuffix` key,
   so this cannot currently be configured back.

#### Discovering targets

`android build query "//..."` lists every target with its command and a
description (it returned "No targets found" on CLIs before 1.0.16261425):

| Target | Builds |
|---|---|
| `//app`, `//app:main:apk`, `//app:main:apk:debug` | Debug APK |
| `//app:main:apk:release` | Release APK (R8) |
| `//app:main:bundle`, `//app:main:bundle:release`, `//app:main:bundle:debug` | App bundles — note the bare bundle target is the *release* one, unlike the bare APK target |
| `//ui`, `//ui:main:aar`, `//ui:main:aar:release` | Release AAR |
| `//ui:test`, `//ui:androidTest` | Unit / instrumented tests (`android build test <target>` runs them) |

Wildcards only work for `query`: `android build "//..."` fails with *"Target
//... not found"*, although the error helpfully lists every valid target.
`android describe`, the CLI's project-metadata command, does not understand
Lightbuild projects at all (*"gradlew not found"*).

#### The CLI always exits 0

The biggest trap so far: **`android build` returns exit code 0 whatever
happened.** A compile error, a failing unit test under `android build test`,
a target that does not exist, and the bare no-op above all print Lightbuild's
*"BUILD FAILED"* / *"Error: Lightbuild build failed with exit code 1"* and
then exit 0 (verified on CLI 1.0.16261425). In CI that means a build step can
never go red on its own: a transient Maven outage broke the debug build in one
job here, the step stayed green, and the failure only surfaced two steps later
as `aapt2` complaining that `app-debug.apk` did not exist. Every CI build now
goes through `.github/scripts/android-build.sh`, a five-line wrapper that
tees the output and fails on a reported failure or a missing *"BUILD
SUCCESS"* line. Do the same in any script that relies on the exit code.

The second one is easy to miss, because a hardcoded `adb shell am start -n
<pkg>/<activity>` then fails with *"Activity class does not exist"* while any
`|| true` around it keeps the job green. CI now reads the applicationId out of
the built APK with `aapt2 dump packagename` and resolves the launcher component
with `cmd package resolve-activity` instead of assuming either.

`android run` is unaffected — it resolves the component itself, which is a good
reason to prefer it over hand-rolled `adb` invocations.

Behind the scenes Lightbuild converts these files to another build system that
performs the actual build; you only ever edit the YAML.

**Enabling it:** the feature is gated behind an environment variable:

```sh
export ANDROID_CLI_BUILD=true
```

### Tooling: Android CLI

The [Android CLI](https://developer.android.com/tools/agents/android-cli)
(`android`) is used for the complete development loop:

```sh
android create empty-activity-lightbuild --name="..." --output=...   # scaffold
android build query "//..."  # list build targets
android build "//app"        # build the debug APK (and //ui, its dependency)
android build test           # run unit tests
android build clean          # clean outputs and caches
android run --apks=app/build/outputs/apk/debug/app-debug.apk         # deploy
```

### App stack

- **[Kotlin](https://kotlinlang.org/)** — 100% Kotlin sources.
- **[Jetpack Compose](https://developer.android.com/compose)** — declarative UI toolkit; the whole UI is composables, no XML layouts.
- **[Material 3](https://developer.android.com/develop/ui/compose/designsystems/material3)** — theming (dynamic color on Android 12+, light/dark themes) and typography.
- **[Navigation 3](https://developer.android.com/guide/navigation/navigation-3)** — the new Compose-native navigation library (`NavDisplay` + a type-safe, serializable back stack).
- **[SensorManager](https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion)** — raw `TYPE_ACCELEROMETER` events; the shake magnitude is `sqrt(x² + y² + z²)`. `AccelerometerDataSource` exposes the sensor as a cold `Flow` built with `callbackFlow`, so the listener is registered on collection and unregistered in `awaitClose`.
- **[ViewModel](https://developer.android.com/topic/libraries/architecture/viewmodel) + `StateFlow`** — `ShakeDetectorViewModel` turns the raw stream into a `ShakeUiState`. `stateIn(WhileSubscribed(5_000))` plus `collectAsStateWithLifecycle()` means the accelerometer is only subscribed while the UI is actually visible: backgrounding the app releases the sensor instead of draining the battery. Verified with `adb shell dumpsys sensorservice`, which logs the matching `+`/`−` registration pair.
- **[Vibrator / VibratorManager](https://developer.android.com/reference/android/os/VibratorManager)** — haptic feedback on shake detection, with API-level-aware fallbacks down to `minSdk 24`.
- **Edge-to-edge** — `enableEdgeToEdge()` + `safeDrawingPadding()`.

### Module structure

```
app/   → application shell (manifest, applicationId, R8 config, depends on //ui)
ui/    → library module with all Compose UI, sensor logic, and tests
```

Inside `ui/`, the shake feature follows the standard
[Android architecture](https://developer.android.com/topic/architecture) split:

```
AccelerometerDataSource   → SensorManager wrapped as a cold Flow<Float>
ShakeDetectorViewModel    → Flow → StateFlow<ShakeUiState> (+ pure `reduce`)
ShakeDetectorScreen       → stateful: collects the ViewModel, fires haptics
ShakeDetectorContent      → stateless: drives previews and UI tests
```

`ShakeUiState.reduce()` is a pure, clock-injected function, so the "hold the
label for a second after the last shake" behaviour is unit tested on the JVM
rather than by waiting on a real device.

## Testing

### Unit tests (`ui/src/test`)

All the shake logic is pure, so it is tested on the JVM without any device:

```sh
android build test
```

- `ShakeLevelTest` covers `classifyShake`: rest/gravity, both thresholds as
  exclusive bounds, and values just above each threshold.
- `ShakeUiStateTest` covers `ShakeUiState.reduce`: a shake being detected, the
  label being held while the device settles, the hold expiring back to idle, and
  a second shake restarting the hold window. Time is injected, so none of these
  tests sleep.

12 unit tests total.

### UI tests (`ui/src/androidTest`)

`ShakeDetectorScreenTest` uses the **Compose testing APIs**
(`createAndroidComposeRule`, `onNodeWithText`) with **AndroidJUnitRunner** to
verify each UI state renders the right message, including the no-accelerometer
fallback. The screen is split into a stateful wrapper (`ShakeDetectorScreen`)
and a stateless `ShakeDetectorContent(uiState)` so tests can drive every state
deterministically — no need to physically shake the test device. Assertions read
their expected text from `strings.xml`, so they don't drift from the UI.

Run them on a connected device/emulator with:

```sh
android build "//ui:androidTest"   # assembles ui-debug-androidTest.apk
adb install -r ui/build/outputs/apk/androidTest/debug/ui-debug-androidTest.apk
adb shell am instrument -w com.jpcottin.shakedetectortest.test/androidx.test.runner.AndroidJUnitRunner
```

### Journey tests

`docs/journeys/` holds XML journeys — agent-evaluated end-to-end tests where
each `<action>` is performed against a live device and judged from what is
actually on screen:

- `shake-journey.xml` — for a physical device; the shaking is real, so an agent
  runs it interactively (screenshot polling plus the vibrator history as ground
  truth, since the app fires a 150 ms vibration per detected shake).
- `shake-journey-emulator.xml` — for an emulator, with the accelerometer driven
  by `adb emu sensor set acceleration x:y:z`; fully deterministic, encoded as a
  shared script (`.github/scripts/shake-journey.sh`) that every
  emulator-booting CI job runs (see below). It is a blocking check in the
  Instrumented Tests jobs and part of every experimental emulator job,
  including each cycle of the snapshot multi-run experiments.

### Composable previews

`ShakeDetectorPreviews.kt` provides `@Preview`s for every state (idle, small
shake, big shake, dark mode, the no-accelerometer fallback, and a 2× font-scale
variant that catches accessibility clipping) rendered in Android Studio's
preview panel — another benefit of the stateless-content split.

### Accessibility & theming

The shake label is a `liveRegion`, so TalkBack announces state changes that a
sighted user perceives as a colour change. Colours and text sizes come from
`MaterialTheme.colorScheme` / `typography` rather than hardcoded values, so the
UI honours dynamic colour on Android 12+ and scales with the user's font-size
setting.

## Continuous integration

`.github/workflows/ci.yml` runs on every push and pull request to `main`. Every
job installs the Android CLI from scratch and builds with Lightbuild
(`ANDROID_CLI_BUILD=true` is set workflow-wide), so CI also acts as a daily
check that the alpha toolchain still installs and builds cleanly on a stock
Ubuntu runner.

### Core jobs

| Job | What it does |
|---|---|
| **Build + Unit Tests (Lightbuild)** | Builds `//ui`, `//ui:androidTest` and `//app:main:apk:debug` (every target named explicitly, and every build through the exit-code wrapper — see the rough edges above), runs the JVM unit tests with `android build test`, and uploads the test results and both APKs. |
| **Release Build** | Builds `//app:main:apk:release` to guard the R8 configuration, and archives the release APK together with `mapping.txt` — without the mapping file a release stack trace is undecodable. |
| **Instrumented Tests (API 34, 36)** | Boots emulators with `reactivecircus/android-emulator-runner`, installs the self-instrumenting `androidTest` APK, and runs the Compose UI tests. `am instrument -w` exits 0 even when tests fail, so the job greps the output for the `OK (N tests)` summary line. The **shake journey** then reuses the booted emulator as a blocking end-to-end check (sensor → detection → UI), giving the journey coverage on API 34 and 36 alongside the experimental jobs' API 37. |

### Experimental jobs

Four additional jobs (all `continue-on-error`, so they never block a merge)
probe the newest emulator tooling:

| Job | What it does |
|---|---|
| **Android CLI experiment** | Drives the whole emulator flow with the CLI: `android sdk install --canary` for the canary emulator and the API 37.0 16 KB-page-size image, `android emulator create` + `start`, and the instrumented tests through the CLI's native runner (`android build test "//ui:androidTest"`) instead of adb + `am instrument`. It then reuses the still-running emulator for the **shake journey**. |
| **Emulator Preview experiment** | Runs the separate **Android Emulator (Preview)** SDK package (`emulators;latest`, installs under `emulators/latest/`, currently API 37+ only) by launching its binary directly, then runs the instrumented tests and the **shake journey** on it. |
| **Emulator Preview experiment multi-run** | Snapshot save/restore of the *live app* on the preview emulator: four boot cycles with snapshots enabled, the app launched only in cycle 1, each cycle shut down gracefully so a snapshot is saved. Later cycles verify the app came back by itself — process alive, window focused, and a non-empty Compose layout tree (`android layout`), since a mostly-static screen can't be judged by screenshot diffing. Each cycle then runs the **shake journey** against the restored instance before its snapshot is saved — the app is genuinely used every cycle, so later cycles prove a *used* app survives restore and still detects shakes. |
| **Android CLI experiment multi-run** | The same four-cycle snapshot experiment, but driven entirely by the CLI (`android emulator start`/`stop`, `android run`, `android screen capture`, `android layout`) against the canary emulator — a direct comparison of the CLI tooling against the preview-emulator job. Also runs the **shake journey** in every cycle before the snapshot save. |

The two preview-emulator jobs share their setup (KVM, cmdline-tools, system
image, AVD, and the preview package) through a local
composite action, `.github/actions/preview-emulator`.

All the emulator jobs run the same shake journey through one shared script,
`.github/scripts/shake-journey.sh`: it optionally installs and launches the
app with `android run` (applicationId and launcher activity resolved from the
APK), injects 13 → 20 → 9.81 m/s² with `adb emu sensor set acceleration`, and
asserts the expected label at each step via the `android layout` tree, saving
a screenshot and layout dump per step (uploaded as `shake-journey-evidence-*`
artifacts, or inside the multi-run screenshot artifacts). Injected sensor
values are constant, so the layout tree stays enumerable — on a physical
device the ever-changing acceleration text keeps UiAutomator from idling. In
the multi-run jobs the script runs in assert-only mode against the restored
instance, so it never disturbs the launched-once premise of the snapshot
experiment. Not every emulator can run it: the preview package
(`emulators/latest`) answers `KO: not implemented` to `sensor set` console
commands (the stable SDK and canary emulators accept them), so the script
probes first and skips loudly rather than failing — a finding in itself for
the preview-emulator experiments.

Notes that came out of building these jobs:

- `android emulator create` is profile-based and cannot pin a system image — it
  picks `google_apis_playstore`, whose first-boot Play overlays steal window
  focus and break Espresso. An AVD is just ini files, so the jobs create the
  profile with the CLI and retarget it at the pinned `google_apis_ps16k` image.
- `android emulator start` passes neither `-noaudio` nor `-no-window`, so the
  headless runner needs `libpulse0` and an Xvfb display; it also has no
  disable-animations equivalent, so the jobs turn animations off via
  `adb shell settings` for Espresso.
- The experiments launch the app once and read state through the platform
  (`pidof`, `dumpsys window`, layout tree) rather than hardcoding component
  names — the applicationId is read from the APK with `aapt2 dump packagename`,
  for the reasons described under the Lightbuild rough edges.

## Getting started

1. Install the [Android CLI](https://developer.android.com/tools/agents/android-cli)
   for your platform:
   ```sh
   # macOS (Apple Silicon)
   curl -fsSL https://dl.google.com/android/cli/latest/darwin_arm64/install.sh | bash

   # macOS (Intel)
   curl -fsSL https://dl.google.com/android/cli/latest/darwin_x86_64/install.sh | bash

   # Linux (x86_64)
   curl -fsSL https://dl.google.com/android/cli/latest/linux_x86_64/install.sh | bash
   ```
   ```bat
   :: Windows (x86_64)
   curl -fsSL https://dl.google.com/android/cli/latest/windows_x86_64/install.cmd -o "%TEMP%\i.cmd" && "%TEMP%\i.cmd"
   ```
2. Enable Lightbuild support:
   ```sh
   export ANDROID_CLI_BUILD=true
   ```
3. Build, test, and deploy:
   ```sh
   android build "//app"
   android build test
   android run --apks=app/build/outputs/apk/debug/app-debug.apk
   ```

The project also opens in recent Android Studio preview builds with Lightbuild
support.

## App icon

The launcher icon is a shaking Android robot: the bugdroid head tilted
mid-shake with motion arcs on both sides, drawn as adaptive-icon vector
drawables (with a monochrome layer for Android 13+ themed icons) plus
regenerated legacy webps for API 24–25.

The Android robot is reproduced or modified from work created and shared by
Google and used according to terms described in the
[Creative Commons 3.0 Attribution License](https://creativecommons.org/licenses/by/3.0/).
