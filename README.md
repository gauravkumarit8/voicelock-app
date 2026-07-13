# VoiceLock

Lock your Android phone hands-free by saying a custom phrase — 100% on-device,
no cloud, no account. See `VoiceLock_PRD_Architecture_v2.md` for the full
product/architecture spec this code implements.

## Project status

**The full pipeline now runs end-to-end with no missing files** — you can
test the complete flow today:

1. **Enrollment** (`EnrollmentViewModel` + `AudioCapture`) — real `AudioRecord`
   capture for each of the 3 takes.
2. **Wake-word detection** (`WakeWordService` + `WakeWordEngine`) — the real
   3-stage openWakeWord pipeline (melspectrogram → embedding → classifier),
   bundled with a real pretrained phrase, **"Hey Jarvis"** (pulled from
   openWakeWord's GitHub releases — your actual custom phrase isn't trained
   yet, see "Training your wake word model" below).
3. **Speaker verification** (`SpeakerVerificationEngine`) — deliberately
   **not** a trained neural model. It's a self-contained classical DSP
   feature extractor (log-mel-filterbank mean/variance, pure Kotlin, zero
   external model file) so there's nothing left to source/download before
   testing works. Stated plainly: this will be meaningfully less accurate
   at telling similar voices apart than a real trained embedding (GE2E/
   ECAPA-TDNN) — it's good enough to validate the whole pipeline and use for
   real testing, not a final-quality biometric. Swap it for a trained ONNX
   model later without touching any caller — see the class doc for the
   pattern to follow (same shape as `WakeWordEngine`).
4. **Lock mechanism** — the Live Test onboarding screen now actually calls
   `LockManager.lockNow()`, not a cosmetic placeholder.
5. **Settings screen** — real sensitivity slider wired to `OnboardingStatusStore`,
   plus a re-enroll shortcut.
6. **Returning-user flow** — the app remembers completed onboarding across
   restarts (`MainActivity` checks `OnboardingStatusStore.isFullySetUp`) and
   skips straight to Home instead of forcing you through setup every launch.

### How to actually test it
1. Complete onboarding — when it asks you to record your phrase, **say "Hey
   Jarvis"** (all 3 takes), since that's the only phrase the bundled
   classifier recognizes right now.
2. On the "Let's make sure it works" screen, tap "Lock now" to confirm
   Device Admin + `lockNow()` work in isolation.
3. Back out to Home (or relaunch the app — it'll skip straight there now).
4. With the screen on, say **"Hey Jarvis"** out loud. Watch logs if it
   doesn't lock:
   ```bash
   adb logcat | grep -E "WakeWordService|VoiceAuthService"
   ```
   You should see a wake-word confidence score, then a speaker-similarity
   score. If similarity is consistently below the sensitivity threshold even
   when it's really you, lower the slider in Settings — the DSP-based
   embedding's similarity distribution is not the same as a trained model's,
   so the right threshold for your voice/device/mic is something you'll need
   to find empirically, not something pre-tuned for you.

**What's still not real/finished:**
- Your actual chosen phrase isn't trained — everything currently runs on
  "Hey Jarvis".
- The melspectrogram/embedding windowing math in `WakeWordEngine` is
  reconstructed from openWakeWord's public docs, not verified against a live
  run of their reference implementation — see the caveat comment in that file.
- `SpeakerVerificationEngine`'s DSP approach is a testing baseline, not a
  production-quality biometric — revisit before shipping (PRD §8 Risk 4 has
  more on realistic accuracy expectations even for trained models).
- OEM battery-kill deep links (`OemBatterySettings`) are unverified against
  real device firmware.


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
