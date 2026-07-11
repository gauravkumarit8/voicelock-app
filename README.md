# VoiceLock

Lock your Android phone hands-free by saying a custom phrase — 100% on-device,
no cloud, no account. See `VoiceLock_PRD_Architecture_v2.md` for the full
product/architecture spec this code implements.

## Project status

This is a **working scaffold**, not a finished app. Everything needed to
compile, install, and walk through onboarding is here. Three things are
intentionally left as marked `TODO`s because they're substantial, separately
reviewable pieces of work:

1. **Audio capture loop** in `WakeWordService` (AudioRecord → melspectrogram
   features → `WakeWordEngine.detect()`).
2. **Enrollment capture** in `EnrollmentScreen` (recording the 3 takes into
   real audio buffers instead of a tap-through placeholder).
3. **The two ONNX model files themselves** — see
   `app/src/main/assets/models/README.md`.

Everything else — permissions, manifest, Device Admin, the battery-exemption
gatekeeping logic, OEM deep links, encrypted voiceprint storage, Hilt wiring,
and the full onboarding screen flow — is implemented per the PRD.

## Developing in GitHub Codespaces

1. Push this project to a GitHub repo.
2. On the repo page: **Code → Codespaces → Create codespace on main**.
3. The `.devcontainer/setup-android-sdk.sh` script runs automatically on
   first boot. It downloads the Android command-line tools, accepts SDK
   licenses, installs the API 35 platform + build-tools, and generates the
   Gradle wrapper. This takes a few minutes the first time.
4. Once setup finishes, build from the Codespace terminal:
   ```bash
   ./gradlew assembleDebug
   ```
5. To install on a device/emulator, you'll need `adb` connectivity from the
   Codespace — either:
   - Use **Android Studio's "Running Devices" mirroring** via a local ADB
     connection (`adb connect`), or
   - Build the APK in the Codespace (`./gradlew assembleDebug`), download the
     resulting `.apk` from `app/build/outputs/apk/debug/`, and sideload it
     onto a physical phone for testing — this is the simplest path since
     Codespaces has no GPU/display for an emulator by default.

### Why Codespaces needs a setup script instead of a pre-baked SDK image
The Android SDK license terms require explicit acceptance and Google doesn't
publish a devcontainer feature that bundles it pre-accepted, so the script
approach (rather than a heavier custom Docker image) keeps the repo portable
and avoids vendoring gigabytes of SDK binaries into source control.

## Training your wake word model (free — see PRD §16)

1. Use the official [openWakeWord](https://github.com/dscripka/openWakeWord)
   training pipeline with Piper-TTS synthetic clips (MIT-licensed, free).
   Avoid community forks that pull in the ACAV100M negative-data set — it
   carries a CC-BY-NC-SA non-commercial license that would restrict your app.
2. Free GPU compute: Google Colab's free tier is sufficient for a single
   ~200KB phrase model (a few hours of training).
3. Export to ONNX, drop the file at
   `app/src/main/assets/models/wakeword_phrase.onnx`.

## Speaker verification model (free — see PRD §16)

Reuse an existing pretrained, permissively-licensed (Apache/MIT) speaker
embedding model converted to ONNX rather than training your own from scratch
for v1. Drop it at `app/src/main/assets/models/speaker_embedding.onnx`.

## Build order (recommended — see PRD §16)

1. Core lock flow (Device Admin + `LockManager.lockNow()`) — already scaffolded.
2. Wire in a **stock/pretrained** wake word phrase first to validate the
   foreground-service + battery-exemption lifecycle (PRD §8 Risk 1) before
   any custom ML work.
3. Train your actual custom phrase model.
4. Wire up real audio capture in `WakeWordService` and `EnrollmentScreen`.
5. Add the speaker verification model.
6. OEM battery-kill mitigations — already scaffolded in `OemBatterySettings`.
7. Internal free beta via Play Console (up to 100 testers) before public release.

## Known scaffolding limitations

- `OemBatterySettings` component names are the commonly-documented ones as
  of 2026 and **will need maintenance** across OEM firmware updates — see
  PRD §15.1 and §17 Screen 4.
- The Gradle wrapper jar is generated on first Codespace boot rather than
  committed, to keep the repo lightweight — run
  `gradle wrapper --gradle-version 8.9` manually if you're not using the
  provided devcontainer.
- This scaffold has **not been compiled** in an environment with the Android
  SDK available (the sandbox used to generate it has no access to Google's
  Maven/SDK repositories). Expect to fix minor issues — missing imports,
  version mismatches — on first build inside Codespaces.
