# Cinema Cut Pro

Cinema Cut Pro is a native Android video-editor prototype built with Kotlin, Jetpack Compose, and a small C++/JNI layer. The repository contains substantial editor, timeline, color, media, and export code, but these subsystems are at different completion levels.

> **Current status:** prototype / active development. A bounded Media3 adapter now implements single-clip, constant-frame-rate H.264/MP4 export (trim/resize, with compatible embedded AAC where present) and rejects edits it cannot render. The Android/Kotlin path and an actual device-produced MP4 have not yet been build- or device-verified here. Multi-layer compositing, complete C++-to-export integration, multitrack audio, color grading, and HDR still need implementation and validation. See [the source review](docs/PROJECT_REVIEW.md) and [development roadmap](docs/ROADMAP.md).

## What is in the codebase

- Portrait editor and a separate landscape advanced-timeline workspace.
- Project, track, clip, edit-command, undo/redo, autosave, and versioning models.
- Media import/analysis, preview-engine adapters, and proxy-generation scaffolding.
- Transform/keyframe and color-adjustment/color-grade models and UI.
- A bounded Media3 H.264/MP4 single-clip export adapter; the other export paths remain experimental, and none has been production- or device-verified in this sandbox.
- A Gemini text endpoint intended for color-grading advice. This is **not** a text-to-video or video-generation model.

## Project layout

```text
app/src/main/java/com/example/
  timeline/core/       project data, editing commands, history, persistence
  timeline/engine/     preview, timeline math, native adapters, performance
  timeline/media/      import, media metadata, proxies, codec checks
  timeline/export/     export jobs, backends, resolver, codecs, service
  model/               adjustment and color-grade models/processors
  ui/                  Compose editor, timeline, color, transform, export UI
  ai/                  Gemini API/repository
app/src/main/cpp/      C++17/JNI timeline, transform, color, and export code
app/src/test/          local JVM/Robolectric tests
app/src/androidTest/   Android instrumentation tests
docs/                  current review, roadmap, and imported historical notes
archives/              original uploaded ZIP snapshot (not used by Gradle)
```

The Android application module is `:app`. There is no Node/web application; the unused AI-Studio `package.json` and generated build log from the uploaded archive were removed.

## Build locally

### Requirements

- Android Studio with a compatible Android Gradle Plugin 9.1.1 toolchain.
- JDK 17 or the JDK bundled with Android Studio.
- Android SDK Platform 36 (the project requests API 36, minor level 1), NDK, and CMake 3.22.1.
- An arm64-v8a emulator/device for the native build. This project currently filters native builds to `arm64-v8a` only.

Open the **repository root** in Android Studio, or run:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

The host-side C++ kernel suite can be run separately with Python 3 and `g++`:

```bash
python3 tools/run_native_export_tests.py
```

That host suite does not compile the JNI bridge or verify Android/Media3 integration. On Windows, use `gradlew.bat` instead. The debug build uses Android Gradle Plugin's generated debug signing key; no private debug keystore needs to be added to the repository. A release signing config is enabled only when all four environment variables (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) are set and the keystore file exists. Never commit a signing key or its passwords.

### Optional Gemini color-advice API

The editor does not need an API key. To try the Gemini color-advice path, copy `.env.example` to an ignored local `.env` file and replace the placeholder:

```properties
GEMINI_API_KEY=your_key_here
```

**Security:** the current Android client reads this value through `BuildConfig`, so a key supplied at build time can be recovered from a distributed APK. A local `.env` file prevents accidental Git commits; it does **not** make a mobile API key secret. Before public release, route Gemini requests through a controlled backend and apply provider-side restrictions and quotas.

## Documentation

- [Source review and architecture map](docs/PROJECT_REVIEW.md)
- [Prioritized development roadmap](docs/ROADMAP.md)
- [Documentation index](docs/README.md)
- [Imported source archive notes](archives/README.md)

The older audit notes have been preserved under `docs/archive/imported/`. They were copied from the uploaded project and may contain outdated or unverified claims; the current source review is the authoritative status summary.