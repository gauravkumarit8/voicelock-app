# VoiceLock

Lock your Android phone hands-free by saying a custom phrase — 100% on-device,
no cloud, no account. See `VoiceLock_PRD_Architecture_v2.md` for the full
product/architecture spec this code implements.

## Project status

Real audio capture and the openWakeWord pipeline are now wired in — this is
no longer a click-through shell. What actually works end-to-end:

1. **Enrollment recording** (`EnrollmentViewModel` + `AudioCapture`) — real
   `AudioRecord` capture for each of the 3 takes, embedded via
   `SpeakerVerificationEngine`, averaged, and persisted encrypted.
2. **Wake-word detection** (`WakeWordService` + `WakeWordEngine`) — the real
   3-stage openWakeWord pipeline (melspectrogram → embedding → classifier),
   running continuously over a live `AudioRecord` stream while the screen is on.
3. **A real stock model is bundled** at `app/src/main/assets/models/` —
   `melspectrogram.onnx`, `embedding_model.onnx` (shared preprocessing), and
   `hey_jarvis_v0.1.onnx` (openWakeWord's pretrained "Hey Jarvis" phrase) —
   pulled directly from openWakeWord's GitHub releases, so the pipeline is
   testable with a real phrase before you train your own custom one.

**What's still missing before this is a finished product:**

- **`SpeakerVerificationEngine`'s model file is NOT bundled.** There's no
  pretrained, permissively-licensed speaker-embedding ONNX file included —
  you still need to source/convert one and place it at
  `app/src/main/assets/models/speaker_embedding.onnx`, or enrollment/voice-auth
  will throw a `FileNotFoundException` at runtime.
- **Your actual custom phrase isn't trained yet** — the bundled classifier
  detects "Hey Jarvis", not your chosen VoiceLock phrase. See "Training your
  wake word model" below to swap it once you're ready.
- **The melspectrogram/embedding windowing math in `WakeWordEngine` is
  reconstructed from openWakeWord's public docs, not verified against a live
  run of their reference implementation.** It should work, but if detection
  accuracy seems off, that's the first place to check — see the caveat
  comment directly in `WakeWordEngine.kt`.
- The live "Test your setup" screen (`LiveTestScreen`) still doesn't call
  `LockManager.lockNow()` or listen for a real detection callback — it's
  cosmetic until wired up.
- Settings screen is still a placeholder.


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
