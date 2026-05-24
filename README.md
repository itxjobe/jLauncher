# jLauncher

A fold-aware Android launcher.

Forked from [Lawnchair Launcher](https://github.com/lawnchairlauncher/lawnchair) (14-dev branch), which is itself a fork of AOSP Launcher3. Lawnchair's solved CI/build infrastructure is the practical reason for the base choice — vanilla AOSP Launcher3 derivatives don't build cleanly in headless CI without significant engineering of their hidden-API and auto-generated-Flags shims. Lawnchair has all that already, so jLauncher inherits a working build day-one and focuses energy on what actually matters here: making the launcher understand foldable devices.

## Status

Pre-alpha. CI green on baseline; foldable features land progressively in `lawnchair/src/com/jlauncher/fold/`.

## Build

Pushed to CI on every change — Actions tab produces a debug APK as an artifact, ready to sideload.

Local:

```
./gradlew assembleLawnWithQuickstepGithubDebug
```

Requirements: JDK 21 (Zulu/Temurin), Android SDK platform 34+, Gradle 8.10.2 (wrapper-managed).

The submodule `platform_frameworks_libs_systemui` is required — clone with `--recurse-submodules` or run `git submodule update --init`.

## Layout

The Lawnchair structure (paths inherited):

- `lawnchair/` — Lawnchair launcher sources (replaces AOSP defaults where overridden)
- `lawnchair/src/com/jlauncher/fold/` — posture detection + adaptive layout (planned)
- `quickstep/` — gesture / recents
- `src/` — original AOSP Launcher3 sources
- `compatLib/` — per-API-level compatibility shims
- `compose/` — Jetpack Compose UI used by Lawnchair
- `platform_frameworks_libs_systemui/` — submodule, prebuilt SystemUI sources
- `.github/workflows/` — build CI

## License

Apache 2.0 — see `LICENSE.txt` and `NOTICE`.
