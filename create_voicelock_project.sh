#!/usr/bin/env bash
# Run this from inside your empty GitHub Codespace / repo root.
# It creates every directory and file for the VoiceLock project scaffold.
set -euo pipefail

echo "Creating directory structure..."
mkdir -p ".devcontainer"
mkdir -p "app"
mkdir -p "app/src"
mkdir -p "app/src/main"
mkdir -p "app/src/main/assets"
mkdir -p "app/src/main/assets/models"
mkdir -p "app/src/main/java"
mkdir -p "app/src/main/java/com"
mkdir -p "app/src/main/java/com/voicelock"
mkdir -p "app/src/main/java/com/voicelock/app"
mkdir -p "app/src/main/java/com/voicelock/app/admin"
mkdir -p "app/src/main/java/com/voicelock/app/data"
mkdir -p "app/src/main/java/com/voicelock/app/ml"
mkdir -p "app/src/main/java/com/voicelock/app/receivers"
mkdir -p "app/src/main/java/com/voicelock/app/services"
mkdir -p "app/src/main/java/com/voicelock/app/ui"
mkdir -p "app/src/main/java/com/voicelock/app/ui/onboarding"
mkdir -p "app/src/main/java/com/voicelock/app/ui/theme"
mkdir -p "app/src/main/java/com/voicelock/app/util"
mkdir -p "app/src/main/java/com/voicelock/app/{admin,receivers,services,ui"
mkdir -p "app/src/main/java/com/voicelock/app/{admin,receivers,services,ui/onboarding,ui"
mkdir -p "app/src/main/java/com/voicelock/app/{admin,receivers,services,ui/onboarding,ui/theme,data,ml,util}"
mkdir -p "app/src/main/res"
mkdir -p "app/src/main/res/drawable"
mkdir -p "app/src/main/res/mipmap-anydpi-v26"
mkdir -p "app/src/main/res/values"
mkdir -p "app/src/main/res/xml"
mkdir -p "app/src/main/res/{values,drawable,xml,mipmap-anydpi-v26}"
mkdir -p "gradle"
mkdir -p "gradle/wrapper"

echo "Writing files..."
cat > ".devcontainer/devcontainer.json" << 'VOICELOCK_EOF_MARKER'
{
  "name": "VoiceLock Android Dev",
  "image": "mcr.microsoft.com/devcontainers/java:17",
  "features": {
    "ghcr.io/devcontainers/features/java:1": {
      "version": "17",
      "installGradle": true
    }
  },
  "postCreateCommand": "bash .devcontainer/setup-android-sdk.sh",
  "customizations": {
    "vscode": {
      "extensions": [
        "vscjava.vscode-java-pack",
        "redhat.vscode-yaml",
        "adelphes.android-dev-ext",
        "ms-vscode.cpptools"
      ]
    }
  },
  "remoteEnv": {
    "ANDROID_HOME": "/home/vscode/android-sdk",
    "ANDROID_SDK_ROOT": "/home/vscode/android-sdk"
  },
  "forwardPorts": []
}
VOICELOCK_EOF_MARKER

cat > ".devcontainer/setup-android-sdk.sh" << 'VOICELOCK_EOF_MARKER'
#!/usr/bin/env bash
# Runs once when the Codespace is created. Downloads Android cmdline-tools
# and the SDK packages this project needs (API 35 platform + build-tools).
# Codespaces has full internet access, unlike some sandboxed dev environments,
# so this pulls directly from Google's hosted SDK manager.
set -euo pipefail

SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
mkdir -p "$SDK_ROOT/cmdline-tools"
cd "$SDK_ROOT/cmdline-tools"

if [ ! -d "latest" ]; then
  echo "Downloading Android cmdline-tools..."
  curl -sSL -o cmdline-tools.zip \
    "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  unzip -q cmdline-tools.zip
  rm cmdline-tools.zip
  mv cmdline-tools latest
fi

export PATH="$SDK_ROOT/cmdline-tools/latest/bin:$SDK_ROOT/platform-tools:$PATH"

yes | sdkmanager --sdk_root="$SDK_ROOT" --licenses > /dev/null || true
sdkmanager --sdk_root="$SDK_ROOT" \
  "platform-tools" \
  "platforms;android-35" \
  "build-tools;35.0.0"

# Persist env vars for future shells in this Codespace
{
  echo "export ANDROID_HOME=$SDK_ROOT"
  echo "export ANDROID_SDK_ROOT=$SDK_ROOT"
  echo "export PATH=\$PATH:$SDK_ROOT/platform-tools:$SDK_ROOT/cmdline-tools/latest/bin"
} >> ~/.bashrc

# Generate the Gradle wrapper (kept out of source control until first boot
# so the repo doesn't need to vendor the wrapper jar)
if [ ! -f "$(dirname "$0")/../gradlew" ]; then
  cd "$(dirname "$0")/.."
  gradle wrapper --gradle-version 8.9
fi

echo "Android SDK ready at $SDK_ROOT"
VOICELOCK_EOF_MARKER

cat > "README.md" << 'VOICELOCK_EOF_MARKER'
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
VOICELOCK_EOF_MARKER

cat > "VoiceLock_PRD_Architecture_v2.md" << 'VOICELOCK_EOF_MARKER'
# VoiceLock — PRD & Architecture (v2, research-validated)

*Revision note: v2 corrects three platform-level issues in the original spec that would have blocked a production release on Android 14/15 — background microphone foreground-service restrictions, Porcupine licensing cost, and Device Admin's actual (non-deprecated) status. Sources checked: Android Developers docs, Google Play Console Help, Picovoice pricing pages — all as of July 2026.*

---

## 1. Product Goal
Let users instantly lock their Android phone hands-free by saying a custom phrase, without killing battery or letting others trigger it.

## 2. Target Users
People who need quick one-hand locking: while cooking, driving, working with gloves, or for privacy.

## 3. Core User Stories
1. As a user, I can enroll my voice once so only I can lock the phone.
2. As a user, I can say my custom phrase when the screen is on and my phone locks in <2s.
3. As a user, the app uses <3% battery per day.
4. As a user, the app does NOT listen when the screen is off.
5. As a user, if someone else says the phrase, nothing happens.
6. **As a user (new)**, I'm walked through a one-time setup step that lets VoiceLock keep listening reliably, so the feature doesn't silently stop working after a day.

## 4. Functional Requirements

| Feature | Requirement |
|---|---|
| Voice Enrollment | Record user's phrase 3x. Generate on-device voice embedding. |
| Wake Word Detection | Detect custom phrase only when screen is ON. Fully offline. |
| Speaker Verification | Verify voice matches enrolled embedding via cosine similarity. Default threshold 85%, user-adjustable. |
| Screen Lock | Device Admin API, `lockNow()`. No root. |
| Battery Optimization Exemption | **(new, required)** One-time onboarding request so the app is exempt from background-start restrictions on the microphone service. |
| Battery Optimization (runtime) | Stop all listeners on `SCREEN_OFF`. Duty-cycled wake-word engine. |
| Settings | Change phrase, re-enroll voice, sensitivity, toggle on/off. |
| Security | Voiceprints encrypted via Android Keystore. No cloud upload. |

## 5. Non-Functional Requirements
- **Performance:** Lock action <2s from end of phrase.
- **Battery:** <3%/24h with normal use — *contingent on the FGS lifecycle working correctly; see Risk #1.*
- **Privacy:** 100% on-device, no audio leaves the phone.
- **Compatibility:** Android 8.0+ minimum; **must target API 35 (Android 15) for new submissions and existing-app updates as of Aug/Nov 2025** — this is now a hard Play Store floor, not optional.
- **Languages:** English v1.

## 6. Out of Scope v1
iOS, always-on listening, cloud sync, remote lock, unlock by voice.

## 7. Success Metrics (revised)
- Day 1 retention > 40%
- Avg battery drain < 3%
- False positive rate: **target <5%** for v1 given realistic short-utterance speaker verification accuracy (see Risk #4); tighten in v2 with more enrollment data.

---

## 8. Platform Risks Found in Research (read before building)

### Risk 1 — Background mic foreground-service start will throw `SecurityException`
`ACTION_SCREEN_ON` fires to a `BroadcastReceiver` with no visible UI, which counts as "app in background" to the OS. `RECORD_AUDIO` is a while-in-use permission; on Android 14+, attempting to start a `foregroundServiceType="microphone"` service while in the background throws immediately rather than silently failing (as it did pre-Android 14).

**Fix:** One of Android's documented exemptions to this restriction is that the user has turned off battery optimizations for the app. VoiceLock must request this exemption explicitly during onboarding (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) and treat "not yet granted" as a blocking state with a clear in-app prompt — otherwise the core feature silently stops working on stock Android 14/15 devices, which is now most of the install base.

### Risk 2 — Porcupine is not viable at the pricing your architecture assumed
Free tier is a single watermarked device; **custom wake words require the paid Enterprise tier, starting at $6,000/year**. Budget for this explicitly or swap engines (see §10).

### Risk 3 — Device Admin `lockNow()` is fine, don't second-guess it
Google deprecated *enterprise* Device Admin policies (password quality, camera disable, etc.) in favor of Android Enterprise / work profiles. But the consumer use case — locking/wiping a personally-owned, non-managed device — is explicitly kept alive, and `lockNow()` is not on the deprecated list. Proceed with the original Device Admin approach; just expect the standard scary system permission dialog and design onboarding copy around it.

### Risk 4 — Speaker verification accuracy at ~1.5s utterances
A lightweight on-device GE2E/d-vector-style TFLite model on a ~1.5s clip realistically sits in the 5–10% equal-error-rate range in normal acoustic conditions (phone mic, background noise, distance from mic) — not the <1% false-positive target in the original spec. Plan for:
- A user-adjustable sensitivity slider (already in scope) as the primary mitigation.
- Continuous re-embedding on successful unlocks to adapt to the user's mic/environment over time.
- Setting user expectations honestly in-app rather than over-promising security.

### Risk 5 — Foreground service type declaration is now mandatory, not optional
Android 14+ requires declaring `android:foregroundServiceType="microphone"` in the manifest plus the `FOREGROUND_SERVICE_MICROPHONE` permission, and Play Console requires an explicit foreground-service-type declaration under **App Content** at submission time or the build can be rejected in review.

---

## 9. Architecture (v2)

```
[ACTION_SCREEN_ON broadcast received]
        |
[Check: battery-optimization exemption granted?]
        |                                  \
       YES                                  NO
        |                                    |
[startForegroundService()             [Show notification:
 type=microphone]                      "Enable to activate voice lock"]
        |
[Wake Word Engine — duty-cycled, offline]
        |
   phrase detected
        |
[VoiceAuthService — TFLite GE2E embedding, ~1.5s buffer]
        |
   cosine similarity ≥ threshold?
        |
       YES
        |
[DevicePolicyManager.lockNow()  — plain (non-profile-owner) device admin]
        |
   [Screen LOCKED]

[ACTION_SCREEN_OFF broadcast] --> kill WakeWordService + VerificationService, release mic
```

### 9.1 Core Services
- **OnboardingFlow** *(new)*: Device Admin grant → Mic permission grant → Battery optimization exemption grant → voice enrollment. Each step gated; app is non-functional (with a clear reason shown) until all four are complete.
- **ScreenStateService**: `BroadcastReceiver` for `ACTION_SCREEN_ON` / `ACTION_SCREEN_OFF`.
- **WakeWordService**: Foreground service, `foregroundServiceType="microphone"`. Duty-cycled keyword spotter.
- **VoiceAuthService**: Runs only after wake word fires. TFLite speaker-verification inference.
- **LockService**: Calls `DevicePolicyManager.lockNow()`.

### 9.2 Data Layer
- **VoiceprintStore**: `EncryptedSharedPreferences` + Android Keystore.
- **SettingsStore**: Room DB or DataStore (phrase, sensitivity, toggle, exemption status).

### 9.3 ML Models
- **Wake Word Model**: see §10 for engine choice — offline, small footprint, CPU-only.
- **Speaker Verification Model**: TFLite, GE2E-style, 256-dim embedding, cosine similarity comparison.

### 9.4 Permissions Required (updated)
```
RECORD_AUDIO
WAKE_LOCK
BIND_DEVICE_ADMIN
FOREGROUND_SERVICE
FOREGROUND_SERVICE_MICROPHONE      <-- new, mandatory on API 34+
REQUEST_IGNORE_BATTERY_OPTIMIZATIONS  <-- new
RECEIVE_BOOT_COMPLETED
```

---

## 10. Tech Stack (updated)

| Layer | Original | v2 Recommendation |
|---|---|---|
| Language | Kotlin | Kotlin (unchanged) |
| Wake Word | Porcupine | **openWakeWord** (free, self-trained ONNX/TFLite, fully offline) as default; Porcupine only if $6k/yr Enterprise budget is confirmed |
| Speaker Verification | TFLite + GE2E | Unchanged, expectations reset per Risk 4 |
| DI | Hilt | Unchanged |
| Background | Foreground Service + WorkManager | Unchanged, plus mandatory battery-optimization exemption flow |
| Encryption | AndroidX Security Crypto + Keystore | Unchanged |
| Target SDK | Not specified | **API 35 (Android 15)**, hard Play Store requirement |

---

## 11. Data Flow
1. **First Launch:** Device Admin grant → Mic permission → Battery optimization exemption → record phrase 3x → generate embedding → encrypt + save.
2. **Normal Use:** Screen ON → exemption check passes → wake-word engine starts → phrase detected → 1.5s buffer captured → TFLite inference → similarity check → `lockNow()` on pass.
3. **Screen OFF:** All listeners killed, 0% mic usage.

## 12. Security & Privacy
- Voiceprints never leave the device.
- Voiceprints wiped on factory reset (tied to app-private encrypted storage).
- App can only lock, never unlock — no bypass-of-lock-screen risk.
- Onboarding must clearly disclose the microphone is active while the screen is on (not full-time), to preempt privacy concerns and satisfy Play Data Safety disclosure requirements.

## 13. Next Steps to Build v1
1. Prototype **battery-optimization exemption + `foregroundServiceType=microphone`** lifecycle standalone, on a real Android 14/15 device — validate before investing in ML, since this is the single most likely silent-failure point.
2. Integrate openWakeWord (or confirm Porcupine budget) for wake-word detection.
3. Add TFLite speaker verification with a realistic, user-tunable threshold.
4. Wire up Device Admin + `lockNow()`.
5. Wire up Screen ON/OFF lifecycle against the validated FGS pattern from step 1.
6. Battery test with Battery Historian — only after step 1 is confirmed stable.
7. Play Console: declare `microphone` foreground service type under App Content before submission.

---

## 14. Is This Buildable for Production?

**Yes — technically sound, no blockers that require root, private APIs, or policy exceptions.** Every piece (Device Admin `lockNow()`, foreground service with mic, on-device TFLite inference, Keystore encryption) is standard, supported, Play-Store-legal Android API surface. Several real apps ship comparable "voice command while screen on" behavior today.

---

## 15. Mitigation Plan — Solving Each Risk

### 15.1 OEM background-kill (Xiaomi/MIUI, Huawei, OnePlus, Samsung, Oppo)
This is the single biggest real-world reliability threat and it's **not solvable purely in code** — some OEMs override stock Android's battery model entirely.

**Action plan:**
1. Integrate a small open-source helper library that detects the device manufacturer and deep-links the user straight to that OEM's specific "autostart" / "protected app" / "no restrictions" settings screen (community-maintained lists of these intents exist and are free to use — e.g. the open-source pattern documented at dontkillmyapp.com). Do **not** build this from scratch; the intents are already catalogued.
2. Add a **"Test your setup"** button in onboarding: lock the phone, wait 60s with the screen off then on, and confirm the wake-word engine is still alive. If not, resurface the OEM-specific settings deep link. This turns an invisible failure into a guided fix.
3. Track (locally, on-device only — no analytics server needed for this) whether the foreground service unexpectedly died since last screen-on, and show a one-time "Voice Lock stopped working — tap to fix" notification instead of failing silently.
4. Document known-bad OEMs in your Play Store listing / FAQ up front. Managing expectations costs nothing and reduces 1-star reviews more than any code fix will.

### 15.2 False-accept / enrollment quality
1. Ship v1 enrollment with **varied-condition prompts**, not 3 identical quiet-room takes: prompt "say it normally," "say it a bit faster," "say it with some background noise if you can" — three takes, three conditions, same phrase. Zero cost, just UX copy.
2. Add **silent re-embedding**: every successful unlock, blend the new verified sample into a running average embedding (weighted, e.g. 90% old / 10% new). This adapts to the user's voice/mic/environment over weeks for free — no retraining infra needed.
3. Ship the sensitivity slider **prominently** in first-run, not buried in Settings — let users self-correct false accepts/rejects immediately rather than filing a support ticket.
4. Before public launch, test with 10–15 real volunteers (friends/family, different accents/genders) instead of only synthetic data. This is free and catches embarrassing failure modes synthetic testing misses.

### 15.3 Realistic battery testing
1. Don't rely solely on Battery Historian lab runs. Ship a **closed beta (Play Console internal testing track, free)** to 10–20 real users for 1–2 weeks and pull battery stats from Android's own on-device **Battery Usage** screen (`adb shell dumpsys batterystats`) — free, no tooling cost.
2. Gate the wake-word engine's duty cycle so it backs off intelligently — e.g., pause listening after N minutes of continuous screen-on with no phrase detected in low-motion (pocket) states, using the free `ACCELEROMETER`/`SIGNIFICANT_MOTION` sensor as a cheap heuristic — optional v1.1 optimization, not a blocker.

### 15.4 Speaker-verification accuracy expectations
Already mitigated via the sensitivity slider and re-embedding above. Treat this as inherent to short-utterance on-device verification, not something to "fix" — position the feature honestly as convenience-lock, not a security-critical biometric.

---

## 16. Building at Minimal Cost — Full Cost Breakdown

**Bottom line: this app can be built and shipped for close to $0 in tooling/licensing, plus your own time, and roughly $25 total in unavoidable platform fees.**

| Item | Original assumption | Minimal-cost path | Cost |
|---|---|---|---|
| Wake word engine | Porcupine Enterprise | **openWakeWord** — free, open-source, MIT-licensed core. Train with the official Piper-TTS synthetic-clip pipeline (avoid the ACAV100M negative-data option some community forks use, which carries a non-commercial license — stick to openWakeWord's own documented free pipeline) | **$0** |
| Wake word training compute | N/A | Free tier of Google Colab (GPU) is sufficient for a single ~200KB phrase model; a few hours of training | **$0** |
| Speaker verification model | Custom GE2E | Use an existing **pretrained, permissively-licensed** speaker-embedding model (e.g. an Apache/MIT-licensed d-vector or ECAPA-style model already converted to TFLite by the open-source community) rather than training your own from scratch — fine-tune only if needed later | **$0** |
| IDE / language / DI / build tools | Kotlin, Hilt | Android Studio, Kotlin, Hilt, Room, DataStore — all free/open-source | **$0** |
| Encryption | AndroidX Security Crypto | Free, built into Jetpack | **$0** |
| Backend / cloud | None needed | Confirmed: 100% on-device design means **no server, no database hosting, no API costs, ever** — this is the app's biggest structural cost advantage | **$0** |
| OEM battery-kill handling | — | Existing open-source intent catalogs (dontkillmyapp.com-style), MIT/permissive | **$0** |
| Beta testing | — | Play Console **Internal Testing track** — free, up to 100 testers | **$0** |
| Device testing | Battery Historian | Free tool, bundled with Android Studio; test on your own phone + Android Studio emulator | **$0**, or ~$100–150 if you buy one cheap secondary Android phone for OEM-variance testing (optional, recommended) |
| App signing / release | — | Handled by Android Studio / Play App Signing | **$0** |
| **Google Play Developer account** | — | **One-time registration fee** | **$25 (one-time, unavoidable)** |
| Icon / store graphics | — | Free tools (Figma free tier, Canva free tier) | **$0** |

**Total unavoidable cash cost to ship v1: ~$25.** Everything else is your development time, plus an optional ~$100–150 if you want a second physical test device to catch OEM-specific battery-kill bugs before real users do (strongly recommended given §15.1, but skippable if you're comfortable relying on beta tester reports instead).

### What would break the "minimal cost" plan
- Choosing Porcupine over openWakeWord ($6,000/yr — avoid unless you have paying users first).
- Building custom GE2E training infra instead of reusing an existing pretrained model (real cost is your time, easily 1–2 weeks of extra work for marginal accuracy gain in v1).
- Skipping the beta-test step and finding OEM battery-kill bugs from 1-star reviews instead of free internal testers.

### Suggested minimal-cost build order
1. Core lock flow (Device Admin + manual trigger button, no voice yet) — prove the lock mechanism end-to-end.
2. Wire in openWakeWord with a stock/pretrained phrase first (validate the FGS + battery-exemption lifecycle from §8/Risk 1 before any custom ML work).
3. Train your actual custom phrase model (free Colab GPU, a few hours).
4. Add speaker verification with a pretrained embedding model.
5. OEM battery-kill mitigations + "test your setup" onboarding flow.
6. Internal free beta (10–20 testers, 1–2 weeks) → fix → public release ($25 Play fee at this point).

---

## 17. Onboarding Flow Spec — Screens, Copy, and Logic

This is the highest-leverage UI in the whole app: skip or fumble any one screen here and the core feature silently doesn't work. Design principle — **each permission is asked for right before the screen explains why, one at a time, never batched**, and the flow is **non-skippable but resumable** (user can back out and come back later; app clearly shows what's still incomplete on the home screen).

### Screen 0 — Welcome
**Purpose:** Set expectations before asking for anything.
- **Title:** "Lock your phone with your voice"
- **Body:** "VoiceLock listens for your phrase only while your screen is on, and only your voice can trigger it. Nothing is recorded or sent anywhere — it all happens on your phone."
- **Button:** "Get started"
- **Tech note:** No permissions requested yet. This screen exists purely to pre-frame the mic permission dialog that's coming next — apps that ask for mic access with no context get denied far more often.

---

### Screen 1 — Microphone permission
**Purpose:** Get `RECORD_AUDIO`.
- **Title:** "VoiceLock needs your microphone"
- **Body:** "This lets your phone hear your unlock phrase when the screen is on. VoiceLock never listens with the screen off, and audio never leaves your device."
- **Button:** "Allow microphone access" → triggers system permission dialog
- **Denied fallback screen:** "Without microphone access, VoiceLock can't hear your phrase. You can enable it anytime in phone Settings → Apps → VoiceLock → Permissions." + button "Open Settings" + "I'll do this later" (routes to home screen with a persistent "Setup incomplete" banner).

---

### Screen 2 — Device Admin permission
**Purpose:** Get Device Admin activation (needed for `lockNow()`).
- **Title:** "Let VoiceLock lock your screen"
- **Body:** "Android requires special permission for any app to lock your screen instantly. VoiceLock can only lock your phone — it can never unlock it, change your password, or access your data."
- **Button:** "Continue" → triggers `ACTION_ADD_DEVICE_ADMIN` system dialog
- **Tech note:** This is the scariest-looking system dialog in the flow (Android's Device Admin screen uses stock, generic, alarming language about "erase data," "lock screen," etc., that you can't customize). Pre-framing here matters more than on any other screen — consider a short inline diagram showing "VoiceLock can: lock your screen. VoiceLock cannot: unlock your screen, see your data, erase your phone" directly above the button.
- **Denied fallback:** "Without this permission, VoiceLock can't lock your screen at all — this one's required." + "Try again" button (Device Admin can't be silently worked around, so no "later" skip option here, unlike mic/battery).

---

### Screen 3 — Battery optimization exemption
**Purpose:** Get the app whitelisted from Android's Doze/App Standby battery restrictions — this is what keeps the listener alive when the screen turns on (see Risk 1 in §8).
- **Title:** "Keep voice lock reliable"
- **Body:** "Android aggressively limits background apps to save battery. If we skip this step, VoiceLock may randomly stop listening — sometimes after a few hours, sometimes after a restart. This one setting fixes that."
- **Button:** "Allow" → triggers `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` system dialog
- **Denied fallback:** "VoiceLock will likely stop working reliably without this. You can turn it on later in Settings → Battery → Unrestricted apps → VoiceLock." + "Open Settings" + "Skip for now" (allowed here, but home screen shows a persistent warning banner, not just a subtle icon — this is the #1 cause of "it stopped working" complaints, so under-communicating it is the wrong tradeoff).

---

### Screen 4 — OEM-specific settings (conditional, shown only on affected manufacturers)
**Purpose:** Cover Xiaomi/MIUI, Huawei, Oppo, Vivo, OnePlus, Samsung, and other OEMs that layer their own battery managers on top of stock Android.
- **Logic:** Detect `Build.MANUFACTURER` at runtime; only show this screen if the device matches a known-restrictive OEM. Otherwise skip straight to Screen 5.
- **Title:** "One more [Manufacturer name]-specific step"
- **Body:** "[Manufacturer] phones have an extra battery setting that can stop VoiceLock even after the last step. Tap below to open it — look for VoiceLock and enable 'Autostart' / 'No restrictions' / 'Allow background activity' (the exact wording varies)."
- **Button:** "Open [Manufacturer] settings" → deep-links to the manufacturer-specific settings activity (via the open-source intent catalog referenced in §15.1)
- **Fallback if deep link fails on a given firmware version:** "Couldn't open this automatically — go to Settings → Battery → App battery management → VoiceLock and disable any restrictions." + "Continue anyway"
- **Tech note:** These intents are per-OEM and sometimes per-firmware-version, and they change. Treat this screen's deep-link table as something you'll maintain over the app's life, not a one-time build.

---

### Screen 5 — Voice enrollment
**Purpose:** Capture 3 varied-condition samples (per §15.2).
- **Title:** "Teach VoiceLock your voice"
- **Body:** "Say your unlock phrase 3 times. We'll ask for slightly different conditions each time so it recognizes you reliably."
- **Take 1 — Title:** "Say it normally" / **Instruction:** "Speak your phrase in a normal, relaxed voice."
- **Take 2 — Title:** "Say it a bit quicker" / **Instruction:** "Now say it slightly faster, like you're in a hurry."
- **Take 3 — Title:** "Say it with some background noise, if you can" / **Instruction:** "If you can, do this one near a TV, fan, or other noise — otherwise just repeat it normally."
- **Each take:** live waveform/level indicator while recording, auto-stop after phrase-length silence, "Redo this one" option.
- **After all 3:** "All set — analyzing your voice…" (embedding generation, <1s) → confirmation screen with a "Test it now" button leading into Screen 6.
- **Tech note:** Reject takes that are too short (<0.5s) or silent (mic level near zero) with an inline retry prompt rather than silently accepting bad samples into the enrollment set.

---

### Screen 6 — "Test your setup" (live verification, closes the loop from §15.1)
**Purpose:** Prove the whole pipeline actually works end-to-end before declaring onboarding complete — this is the step most onboarding flows skip, and it's the one that would have caught every OEM battery-kill failure before the user ever hit it blind.
- **Title:** "Let's make sure it works"
- **Body:** "We're going to lock your screen and turn it back on. When you see the lock screen, unlock it and come back — then say your phrase to test voice lock live."
- **Button:** "Start test" → triggers `lockNow()` immediately as a live demo
- User unlocks phone normally (PIN/fingerprint), returns to VoiceLock (still-open activity) or is prompted with a notification: "Try saying your phrase now"
- **On success:** "🎉 It works! Setup complete." → home screen, all-green status.
- **On failure (timeout, ~15s):** "We didn't catch it. This usually means one of the earlier steps needs attention." → shows a checklist re-surfacing whichever of Screens 2–4 is most likely the cause (e.g., if battery exemption was skipped, deep-link back to Screen 3) + "Try the test again" + "Continue anyway, I'll fix this later."

---

### Home Screen — Persistent Setup Status
Not a one-time onboarding screen, but the permanent home for anything left incomplete:
- Green checkmark row per requirement (Mic / Device Admin / Battery exemption / OEM settings / Voice enrolled) — tapping an incomplete row jumps straight back into that step.
- If **everything** is green: show current phrase, sensitivity slider, and a big "Voice Lock is active" status — this is the state most users should land in and stay in.

### Why this ordering
Permissions before enrollment, enrollment before the live test — each step is a hard technical prerequisite for the next, so asking out of order just produces confusing failures. The live test (Screen 6) is the one piece of genuinely custom UX work beyond permission-dialog copywriting, and it's the cheapest possible insurance against the exact class of "works in the demo, dead by dinner" bug that kills apps like this in the wild.
VOICELOCK_EOF_MARKER

cat > "app/build.gradle.kts" << 'VOICELOCK_EOF_MARKER'
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.voicelock.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.voicelock.app"
        minSdk = 26          // Android 8.0 — matches PRD compatibility target
        targetSdk = 35       // Required Play Store floor as of Aug/Nov 2025
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // DI
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Persistence — Settings/enrollment metadata (not the voiceprint itself)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Encrypted storage for the voiceprint embedding — Android Keystore-backed
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // ONNX Runtime Mobile — runs the openWakeWord (.onnx) and speaker
    // embedding models fully on-device, no network calls.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.18.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
VOICELOCK_EOF_MARKER

cat > "app/proguard-rules.pro" << 'VOICELOCK_EOF_MARKER'
# Add project-specific ProGuard rules here.
# Keep ONNX Runtime and Hilt-generated classes from being stripped.
-keep class ai.onnxruntime.** { *; }
-keep class dagger.hilt.** { *; }
-keep class com.voicelock.app.** { *; }
VOICELOCK_EOF_MARKER

cat > "app/src/main/AndroidManifest.xml" << 'VOICELOCK_EOF_MARKER'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Core listening + lock permissions -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.BIND_DEVICE_ADMIN" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

    <!-- Required so the app can request exemption from Doze/App Standby —
         see PRD §8 Risk 1: without this, the mic foreground service throws
         SecurityException when started from the SCREEN_ON broadcast receiver. -->
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

    <uses-feature android:name="android.hardware.microphone" android:required="true" />

    <application
        android:name=".VoiceLockApplication"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.VoiceLock"
        android:supportsRtl="true">

        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:theme="@style/Theme.VoiceLock">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Device Admin receiver — backs DevicePolicyManager.lockNow().
             See PRD §8 Risk 3: this is NOT deprecated for the consumer
             lock/wipe use case, only enterprise policy management is. -->
        <receiver
            android:name=".admin.VoiceLockDeviceAdminReceiver"
            android:permission="android.permission.BIND_DEVICE_ADMIN"
            android:exported="true">
            <meta-data
                android:name="android.app.device_admin"
                android:resource="@xml/device_admin_policies" />
            <intent-filter>
                <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
            </intent-filter>
        </receiver>

        <!-- Screen ON/OFF gatekeeper. See PRD §8 Risk 1 — this receiver only
             STARTS the mic service if the battery-optimization exemption
             (Onboarding Screen 3) has already been granted; otherwise it
             would crash with a SecurityException on API 34+. -->
        <receiver
            android:name=".receivers.ScreenStateReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.SCREEN_ON" />
                <action android:name="android.intent.action.SCREEN_OFF" />
            </intent-filter>
        </receiver>

        <receiver
            android:name=".receivers.BootCompletedReceiver"
            android:exported="true"
            android:permission="android.permission.RECEIVE_BOOT_COMPLETED">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>

        <!-- Foreground service type is MANDATORY on API 34+ (PRD §8 Risk 5).
             Must also be declared under Play Console > App Content before
             submission or the build can be rejected in review. -->
        <service
            android:name=".services.WakeWordService"
            android:exported="false"
            android:foregroundServiceType="microphone" />

        <service
            android:name=".services.VoiceAuthService"
            android:exported="false" />

    </application>
</manifest>
VOICELOCK_EOF_MARKER

cat > "app/src/main/assets/models/README.md" << 'VOICELOCK_EOF_MARKER'
# Model files go here

This directory is intentionally empty in source control.

Place your trained/converted ONNX models here before building:
- `wakeword_phrase.onnx` — trained via the free openWakeWord + Piper-TTS
  pipeline (see main README, "Training your wake word model").
- `speaker_embedding.onnx` — a pretrained, permissively-licensed
  (Apache/MIT) speaker-embedding model converted to ONNX.

Without these two files, WakeWordEngine.loadModel() and
SpeakerVerificationEngine.loadModel() will throw a FileNotFoundException
at runtime — the app will still compile and install, but voice detection
won't function until the models are added.
VOICELOCK_EOF_MARKER

cat > "app/src/main/java/com/voicelock/app/VoiceLockApplication.kt" << 'VOICELOCK_EOF_MARKER'
package com.voicelock.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VoiceLockApplication : Application()
VOICELOCK_EOF_MARKER

cat > "app/src/main/java/com/voicelock/app/admin/LockManager.kt" << 'VOICELOCK_EOF_MARKER'
package com.voicelock.app.admin

import android.app.admin.DevicePolicyManager
import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LockManager @Inject constructor(
    private val context: Context
) {
    /** Returns true if the lock actually fired. */
    fun lockNow(): Boolean {
        if (!VoiceLockDeviceAdminReceiver.isActive(context)) return false
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.lockNow()
        return true
    }
}
VOICELOCK_EOF_MARKER

cat > "app/src/main/java/com/voicelock/app/admin/VoiceLockDeviceAdminReceiver.kt" << 'VOICELOCK_EOF_MARKER'
package com.voicelock.app.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Minimal Device Admin receiver. We only ever call lockNow() through this —
 * see PRD §8 Risk 3: this consumer lock/wipe use case is explicitly kept
 * alive by Google even though enterprise Device Admin policies (password
 * quality, camera disable, etc.) were deprecated in favor of Android
 * Enterprise / work profiles.
 */
class VoiceLockDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        // Device Admin was revoked (user did this manually in Settings).
        // The onboarding status store should reflect this so the home
        // screen re-surfaces the "incomplete setup" state (see PRD §17).
    }

    companion object {
        fun componentName(context: Context): ComponentName =
            ComponentName(context, VoiceLockDeviceAdminReceiver::class.java)

        fun isActive(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return dpm.isAdminActive(componentName(context))
        }

        fun requestActivationIntent(context: Context): Intent {
            return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName(context))
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "VoiceLock can only lock your screen — never unlock it, read your data, or erase your phone."
                )
            }
        }
    }
}
VOICELOCK_EOF_MARKER

cat > "app/src/main/java/com/voicelock/app/data/AppModule.kt" << 'VOICELOCK_EOF_MARKER'
package com.voicelock.app.data

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    fun provideContext(@ApplicationContext context: Context): Context = context
}
VOICELOCK_EOF_MARKER

cat > "app/src/main/java/com/voicelock/app/data/OnboardingStatusStore.kt" << 'VOICELOCK_EOF_MARKER'
package com.voicelock.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "voicelock_settings")

/**
 * Tracks the five onboarding requirements from PRD §17 so the home screen
 * can show per-item status and jump the user back into whichever step is
 * incomplete, instead of forcing a linear restart.
 */
@Singleton
class OnboardingStatusStore @Inject constructor(
    private val context: Context
) {
    private object Keys {
        val MIC_GRANTED = booleanPreferencesKey("mic_granted")
        val DEVICE_ADMIN_ACTIVE = booleanPreferencesKey("device_admin_active")
        val BATTERY_EXEMPT = booleanPreferencesKey("battery_exempt")
        val OEM_STEP_ACKNOWLEDGED = booleanPreferencesKey("oem_step_ack")
        val VOICE_ENROLLED = booleanPreferencesKey("voice_enrolled")
        val LIVE_TEST_PASSED = booleanPreferencesKey("live_test_passed")
        val SENSITIVITY = floatPreferencesKey("sensitivity_threshold")
    }

    val isFullySetUp: Flow<Boolean> = context.dataStore.data.map { prefs ->
        (prefs[Keys.MIC_GRANTED] ?: false) &&
            (prefs[Keys.DEVICE_ADMIN_ACTIVE] ?: false) &&
            (prefs[Keys.BATTERY_EXEMPT] ?: false) &&
            (prefs[Keys.VOICE_ENROLLED] ?: false)
    }

    val sensitivity: Flow<Float> = context.dataStore.data.map { it[Keys.SENSITIVITY] ?: 0.85f }

    suspend fun setMicGranted(v: Boolean) = context.dataStore.edit { it[Keys.MIC_GRANTED] = v }
    suspend fun setDeviceAdminActive(v: Boolean) = context.dataStore.edit { it[Keys.DEVICE_ADMIN_ACTIVE] = v }
    suspend fun setBatteryExempt(v: Boolean) = context.dataStore.edit { it[Keys.BATTERY_EXEMPT] = v }
    suspend fun setOemStepAcknowledged(v: Boolean) = context.dataStore.edit { it[Keys.OEM_STEP_ACKNOWLEDGED] = v }
    suspend fun setVoiceEnrolled(v: Boolean) = context.dataStore.edit { it[Keys.VOICE_ENROLLED] = v }
    suspend fun setLiveTestPassed(v: Boolean) = context.dataStore.edit { it[Keys.LIVE_TEST_PASSED] = v }
    suspend fun setSensitivity(v: Float) = context.dataStore.edit { it[Keys.SENSITIVITY] = v }
}
VOICELOCK_EOF_MARKER

cat > "app/src/main/java/com/voicelock/app/data/VoiceprintStore.kt" << 'VOICELOCK_EOF_MARKER'
package com.voicelock.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the enrolled voice embedding, encrypted via Android Keystore.
 * Never leaves the device, never touches the network (PRD §12 Security).
 */
@Singleton
class VoiceprintStore @Inject constructor(
    private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "voicelock_voiceprint",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveEmbedding(embedding: FloatArray) {
        val bytes = ByteArray(embedding.size * 4)
        java.nio.ByteBuffer.wrap(bytes).asFloatBuffer().put(embedding)
        prefs.edit().putString(KEY_EMBEDDING, Base64.encodeToString(bytes, Base64.NO_WRAP)).apply()
    }

    fun loadEmbedding(): FloatArray? {
        val encoded = prefs.getString(KEY_EMBEDDING, null) ?: return null
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val floatBuffer = java.nio.ByteBuffer.wrap(bytes).asFloatBuffer()
        val out = FloatArray(floatBuffer.remaining())
        floatBuffer.get(out)
        return out
    }

    /**
     * Blends a newly-verified sample into the stored embedding (PRD §15.2 —
     * silent re-embedding). Weighted average keeps the voiceprint adapting
     * to the user's mic/environment over time without any retraining infra.
     */
    fun reinforceEmbedding(newSample: FloatArray, existingWeight: Float = 0.9f) {
        val existing = loadEmbedding() ?: run { saveEmbedding(newSample); return }
        if (existing.size != newSample.size) return
        val blended = FloatArray(existing.size) { i ->
            existing[i] * existingWeight + newSample[i] * (1f - existingWeight)
        }
        saveEmbedding(blended)
    }

    fun clear() = prefs.edit().remove(KEY_EMBEDDING).apply()
    fun hasEnrollment(): Boolean = prefs.contains(KEY_EMBEDDING)

    companion object {
        private const val KEY_EMBEDDING = "embedding"
    }
}
VOICELOCK_EOF_MARKER

cat > "app/src/main/java/com/voicelock/app/ml/SpeakerVerificationEngine.kt" << 'VOICELOCK_EOF_MARKER'
package com.voicelock.app.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Produces a 256-dim speaker embedding from ~1.5s of 16kHz mono audio and
 * compares it against the enrolled voiceprint via cosine similarity.
 *
 * MODEL FILE NOT INCLUDED: place a pretrained, permissively-licensed
 * (Apache/MIT) speaker-embedding model, converted to ONNX, at
 * app/src/main/assets/models/speaker_embedding.onnx.
 * See PRD §16 — reuse an existing pretrained model rather than training
 * your own from scratch for v1.
 *
 * Realistic accuracy expectations for short utterances are documented in
 * PRD §8 Risk 4 — this is a convenience lock, not a security-critical
 * biometric, and the sensitivity threshold should stay user-adjustable.
 */
@Singleton
class SpeakerVerificationEngine @Inject constructor(
    private val context: Context
) {
    private var session: OrtSession? = null
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()

    fun loadModel(assetPath: String = "models/speaker_embedding.onnx") {
        val bytes = context.assets.open(assetPath).use { it.readBytes() }
        session = env.createSession(bytes)
    }

    fun release() {
        session?.close()
        session = null
    }

    /** @param audioSamples raw 16kHz mono PCM float samples, ~1.5s of audio. */
    fun embed(audioSamples: FloatArray): FloatArray {
        val activeSession = session ?: error("SpeakerVerificationEngine.loadModel() must be called first")
        val inputName = activeSession.inputNames.iterator().next()
        val shape = longArrayOf(1, audioSamples.size.toLong())
        OnnxTensor.createTensor(env, FloatBuffer.wrap(audioSamples), shape).use { tensor ->
            activeSession.run(mapOf(inputName to tensor)).use { result ->
                val output = result[0].value as Array<FloatArray>
                return output[0]
            }
        }
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size)
        var dot = 0f; var normA = 0f; var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        return dot / (sqrt(normA) * sqrt(normB) + 1e-6f)
    }
}
VOICELOCK_EOF_MARKER

cat > "app/src/main/java/com/voicelock/app/ml/WakeWordEngine.kt" << 'VOICELOCK_EOF_MARKER'
package com.voicelock.app.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps an openWakeWord-trained ONNX model (see PRD §16 — free, MIT-licensed,
 * trained via the official Piper-TTS synthetic pipeline). Expects the
 * standard openWakeWord feature pipeline: 16kHz mono audio -> melspectrogram
 * -> shared embedding backbone -> this classifier head.
 *
 * MODEL FILE NOT INCLUDED: place your trained model at
 * app/src/main/assets/models/wakeword_phrase.onnx before building.
 * See PRD §16 build order steps 2-3 for the free training pipeline.
 */
@Singleton
class WakeWordEngine @Inject constructor(
    private val context: Context
) {
    private var session: OrtSession? = null
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()

    fun loadModel(assetPath: String = "models/wakeword_phrase.onnx") {
        val bytes = context.assets.open(assetPath).use { it.readBytes() }
        session = env.createSession(bytes)
    }

    fun release() {
        session?.close()
        session = null
    }

    /**
     * @param melFeatures precomputed melspectrogram features for the current
     *   audio frame, shape matching the model's expected input.
     * @return detection confidence in [0, 1]; caller compares against the
     *   openWakeWord-recommended default threshold of 0.5 (tune per PRD §15.2).
     */
    fun detect(melFeatures: FloatArray): Float {
        val activeSession = session ?: error("WakeWordEngine.loadModel() must be called first")
        val inputName = activeSession.inputNames.iterator().next()
        val shape = longArrayOf(1, melFeatures.size.toLong())
        OnnxTensor.createTensor(env, FloatBuffer.wrap(melFeatures), shape).use { tensor ->
            activeSession.run(mapOf(inputName to tensor)).use { result ->
                val output = result[0].value as Array<FloatArray>
                return output[0][0]
            }
        }
    }
}
VOICELOCK_EOF_MARKER

cat > "app/src/main/java/com/voicelock/app/receivers/BootCompletedReceiver.kt" << 'VOICELOCK_EOF_MARKER'
package com.voicelock.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Deliberately does nothing beyond being present in the manifest.
 *
 * Android 15+ restricts BOOT_COMPLETED receivers from launching several
 * foreground service types directly (a ForegroundServiceStartNotAllowedException
 * is thrown for the restricted types). We rely instead on ScreenStateReceiver
 * reacting the next time the user turns the screen on post-reboot — no mic
 * service needs to survive a reboot on its own, since it's screen-gated by
 * design anyway (PRD core requirement: never listens with the screen off).
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Intentionally empty. See class doc.
    }
}
VOICELOCK_EOF_MARKER

cat > "app/src/main/java/com/voicelock/app/receivers/ScreenStateReceiver.kt" << 'VOICELOCK_EOF_MARKER'
package com.voicelock.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.voicelock.app.services.WakeWordService
import com.voicelock.app.util.BatteryExemption

/**
 * Core gatekeeper for PRD §8 Risk 1. A BroadcastReceiver has no visible UI,
 * so the app is considered "in the background" here. On API 34+, starting a
 * foregroundServiceType="microphone" service from the background throws
 * SecurityException UNLESS the app is exempt from battery optimizations.
 *
 * This receiver therefore checks the exemption BEFORE attempting to start
 * the service, rather than letting it crash and finding out from a crash
 * report. If the exemption is missing, it does not attempt to start the
 * service at all — the persistent home-screen warning banner (PRD §17)
 * is the correct place to prompt the user, not a crash loop here.
 */
class ScreenStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> {
                if (!BatteryExemption.isExempt(context)) {
                    Log.w(TAG, "Battery exemption not granted — skipping WakeWordService start to avoid SecurityException")
                    return
                }
                WakeWordService.start(context)
            }
            Intent.ACTION_SCREEN_OFF -> {
                WakeWordService.stop(context)
            }
        }
    }

    companion object {
        private const val TAG = "ScreenStateReceiver"
    }
}
VOICELOCK_EOF_MARKER

cat > "app/src/main/java/com/voicelock/app/services/VoiceAuthService.kt" << 'VOICELOCK_EOF_MARKER'
package com.voicelock.app.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.voicelock.app.admin.LockManager
import com.voicelock.app.data.OnboardingStatusStore
import com.voicelock.app.data.VoiceprintStore
import com.voicelock.app.ml.SpeakerVerificationEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Runs only after WakeWordService detects the phrase. Takes the trailing
 * audio buffer, embeds it, compares against the enrolled voiceprint, and
 * calls LockManager.lockNow() on a pass. See PRD §15.2 for the re-embedding
 * reinforcement behavior on every successful match.
 */
@AndroidEntryPoint
class VoiceAuthService : Service() {

    @Inject lateinit var speakerVerificationEngine: SpeakerVerificationEngine
    @Inject lateinit var voiceprintStore: VoiceprintStore
    @Inject lateinit var onboardingStatusStore: OnboardingStatusStore
    @Inject lateinit var lockManager: LockManager

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val audioSamples = intent?.getFloatArrayExtra(EXTRA_AUDIO_SAMPLES)
        if (audioSamples != null) {
            serviceScope.launch { verifyAndLock(audioSamples) }
        } else {
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private suspend fun verifyAndLock(audioSamples: FloatArray) {
        val enrolled = voiceprintStore.loadEmbedding()
        if (enrolled == null) {
            stopSelf()
            return
        }

        speakerVerificationEngine.loadModel()
        val candidate = speakerVerificationEngine.embed(audioSamples)
        val similarity = speakerVerificationEngine.cosineSimilarity(enrolled, candidate)
        val threshold = onboardingStatusStore.sensitivity.first()

        if (similarity >= threshold) {
            lockManager.lockNow()
            // Silent re-embedding — PRD §15.2, adapts the voiceprint over time.
            voiceprintStore.reinforceEmbedding(candidate)
        }

        speakerVerificationEngine.release()
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_AUDIO_SAMPLES = "audio_samples"
    }
}
VOICELOCK_EOF_MARKER

cat > "app/src/main/java/com/voicelock/app/services/WakeWordService.kt" << 'VOICELOCK_EOF_MARKER'
package com.voicelock.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.voicelock.app.ml.WakeWordEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service, type=microphone (mandatory manifest declaration on
 * API 34+, see PRD §8 Risk 5). Only ever started while the screen is on and
 * the battery-optimization exemption is granted — see ScreenStateReceiver.
 *
 * NOTE: actual audio capture / duty-cycling implementation (AudioRecord loop,
 * melspectrogram feature extraction, 16ms-frame processing) is intentionally
 * left as a TODO here — it's mechanical but lengthy, and belongs in its own
 * reviewed PR rather than scaffolded blind. WakeWordEngine.detect() is ready
 * to be wired up to a real AudioRecord pipeline.
 */
@AndroidEntryPoint
class WakeWordService : Service() {

    @Inject lateinit var wakeWordEngine: WakeWordEngine

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob)

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        wakeWordEngine.loadModel()
        serviceScope.launch {
            // TODO: AudioRecord capture loop -> melspectrogram features ->
            // wakeWordEngine.detect(features) -> on threshold hit, start
            // VoiceAuthService with the trailing ~1.5s audio buffer.
        }
    }

    override fun onDestroy() {
        wakeWordEngine.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val channelId = "voicelock_listening"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Voice Lock listening", NotificationManager.IMPORTANCE_LOW)
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("VoiceLock is listening")
            .setContentText("Say your phrase to lock your phone")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            context.startForegroundService(Intent(context, WakeWordService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WakeWordService::class.java))
        }
    }
}
VOICELOCK_EOF_MARKER

cat > "app/src/main/java/com/voicelock/app/ui/MainActivity.kt" << 'VOICELOCK_EOF_MARKER'
package com.voicelock.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.voicelock.app.ui.onboarding.*
import com.voicelock.app.ui.theme.VoiceLockTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VoiceLockTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "welcome") {
                        composable("welcome") { WelcomeScreen(navController) }
                        composable("mic_permission") { MicPermissionScreen(navController) }
                        composable("device_admin") { DeviceAdminScreen(navController) }
                        composable("battery_exemption") { BatteryExemptionScreen(navController) }
                        composable("oem_settings") { OemSettingsScreen(navController) }
                        composable("enrollment") { EnrollmentScreen(navController) }
                        composable("live_test") { LiveTestScreen(navController) }
                        composable("home") { HomeScreen(navController) }
                    }
                }
            }
        }
    }
}
VOICELOCK_EOF_MARKER

cat > "app/src/main/java/com/voicelock/app/ui/onboarding/OnboardingScreens.kt" << 'VOICELOCK_EOF_MARKER'
package com.voicelock.app.ui.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.voicelock.app.admin.VoiceLockDeviceAdminReceiver
import com.voicelock.app.util.BatteryExemption
import com.voicelock.app.util.OemBatterySettings

// ---------------------------------------------------------------------------
// Screen 0 — Welcome (PRD §17)
// ---------------------------------------------------------------------------
@Composable
fun WelcomeScreen(nav: NavController) {
    OnboardingScaffold(
        title = "Lock your phone with your voice",
        body = "VoiceLock listens for your phrase only while your screen is on, " +
            "and only your voice can trigger it. Nothing is recorded or sent " +
            "anywhere — it all happens on your phone.",
        primaryLabel = "Get started",
        onPrimary = { nav.navigate("mic_permission") }
    )
}

// ---------------------------------------------------------------------------
// Screen 1 — Microphone permission
// ---------------------------------------------------------------------------
@Composable
fun MicPermissionScreen(nav: NavController) {
    var denied by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) nav.navigate("device_admin") else denied = true
    }

    if (!denied) {
        OnboardingScaffold(
            title = "VoiceLock needs your microphone",
            body = "This lets your phone hear your unlock phrase when the screen is on. " +
                "VoiceLock never listens with the screen off, and audio never leaves your device.",
            primaryLabel = "Allow microphone access",
            onPrimary = { launcher.launch(Manifest.permission.RECORD_AUDIO) }
        )
    } else {
        OnboardingScaffold(
            title = "Microphone access needed",
            body = "Without microphone access, VoiceLock can't hear your phrase. " +
                "You can enable it anytime in phone Settings → Apps → VoiceLock → Permissions.",
            primaryLabel = "Open Settings",
            onPrimary = { openAppSettings(context) },
            secondaryLabel = "I'll do this later",
            onSecondary = { nav.navigate("home") { popUpTo("welcome") { inclusive = true } } }
        )
    }
}

// ---------------------------------------------------------------------------
// Screen 2 — Device Admin (required, no skip — see PRD §17)
// ---------------------------------------------------------------------------
@Composable
fun DeviceAdminScreen(nav: NavController) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (VoiceLockDeviceAdminReceiver.isActive(context)) {
            nav.navigate("battery_exemption")
        }
        // If not active, stay on this screen — "Try again" re-triggers below.
    }

    OnboardingScaffold(
        title = "Let VoiceLock lock your screen",
        body = "Android requires special permission for any app to lock your screen instantly. " +
            "VoiceLock can only lock your phone — it can never unlock it, change your password, " +
            "or access your data.\n\nVoiceLock can: lock your screen.\nVoiceLock cannot: unlock " +
            "your screen, see your data, erase your phone.",
        primaryLabel = "Continue",
        onPrimary = { launcher.launch(VoiceLockDeviceAdminReceiver.requestActivationIntent(context)) }
    )
}

// ---------------------------------------------------------------------------
// Screen 3 — Battery optimization exemption
// ---------------------------------------------------------------------------
@Composable
fun BatteryExemptionScreen(nav: NavController) {
    var denied by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (BatteryExemption.isExempt(context)) {
            nav.navigate(if (OemBatterySettings.isKnownRestrictiveOem()) "oem_settings" else "enrollment")
        } else {
            denied = true
        }
    }

    if (!denied) {
        OnboardingScaffold(
            title = "Keep voice lock reliable",
            body = "Android aggressively limits background apps to save battery. If we skip this " +
                "step, VoiceLock may randomly stop listening — sometimes after a few hours, sometimes " +
                "after a restart. This one setting fixes that.",
            primaryLabel = "Allow",
            onPrimary = { launcher.launch(BatteryExemption.requestExemptionIntent(context)) }
        )
    } else {
        OnboardingScaffold(
            title = "This step matters",
            body = "VoiceLock will likely stop working reliably without this. You can turn it on " +
                "later in Settings → Battery → Unrestricted apps → VoiceLock.",
            primaryLabel = "Open Settings",
            onPrimary = { context.startActivity(BatteryExemption.openBatterySettingsIntent()) },
            secondaryLabel = "Skip for now",
            onSecondary = {
                nav.navigate(if (OemBatterySettings.isKnownRestrictiveOem()) "oem_settings" else "enrollment")
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Screen 4 — OEM-specific settings (conditional — only known-restrictive OEMs)
// ---------------------------------------------------------------------------
@Composable
fun OemSettingsScreen(nav: NavController) {
    val context = LocalContext.current
    val manufacturer = OemBatterySettings.manufacturerDisplayName()

    OnboardingScaffold(
        title = "One more $manufacturer-specific step",
        body = "$manufacturer phones have an extra battery setting that can stop VoiceLock even " +
            "after the last step. Tap below to open it — look for VoiceLock and enable " +
            "'Autostart' / 'No restrictions' / 'Allow background activity' (the exact wording varies).",
        primaryLabel = "Open $manufacturer settings",
        onPrimary = {
            val opened = OemBatterySettings.openOemSettings(context)
            if (!opened) OemBatterySettings.openGenericBatterySettings(context)
        },
        secondaryLabel = "Continue anyway",
        onSecondary = { nav.navigate("enrollment") }
    )
}

// ---------------------------------------------------------------------------
// Screen 5 — Voice enrollment (3 varied-condition takes — PRD §15.2)
// ---------------------------------------------------------------------------
private val enrollmentTakes = listOf(
    "Say it normally" to "Speak your phrase in a normal, relaxed voice.",
    "Say it a bit quicker" to "Now say it slightly faster, like you're in a hurry.",
    "Say it with some background noise, if you can" to
        "If you can, do this one near a TV, fan, or other noise — otherwise just repeat it normally."
)

@Composable
fun EnrollmentScreen(nav: NavController) {
    var takeIndex by remember { mutableIntStateOf(0) }

    if (takeIndex < enrollmentTakes.size) {
        val (title, instruction) = enrollmentTakes[takeIndex]
        OnboardingScaffold(
            title = title,
            body = instruction,
            primaryLabel = "Recording… tap when done", // TODO: wire to real AudioRecord capture
            onPrimary = { takeIndex++ }
        )
    } else {
        // TODO: pass captured samples into SpeakerVerificationEngine.embed(),
        // average the 3 embeddings, and persist via VoiceprintStore.saveEmbedding().
        OnboardingScaffold(
            title = "All set — analyzing your voice…",
            body = "Your voiceprint has been created and stored securely on this device.",
            primaryLabel = "Test it now",
            onPrimary = { nav.navigate("live_test") }
        )
    }
}

// ---------------------------------------------------------------------------
// Screen 6 — Live "Test your setup" (PRD §17 — closes the loop on Risk 1)
// ---------------------------------------------------------------------------
@Composable
fun LiveTestScreen(nav: NavController) {
    var failed by remember { mutableStateOf(false) }

    if (!failed) {
        OnboardingScaffold(
            title = "Let's make sure it works",
            body = "We're going to lock your screen and turn it back on. When you see the lock " +
                "screen, unlock it and come back — then say your phrase to test voice lock live.",
            primaryLabel = "Start test",
            // TODO: trigger LockManager.lockNow() here, then listen for a
            // real VoiceAuthService success callback within ~15s.
            onPrimary = { nav.navigate("home") { popUpTo("welcome") { inclusive = true } } }
        )
    } else {
        OnboardingScaffold(
            title = "We didn't catch it",
            body = "This usually means one of the earlier steps needs attention.",
            primaryLabel = "Try the test again",
            onPrimary = { failed = false },
            secondaryLabel = "Continue anyway, I'll fix this later",
            onSecondary = { nav.navigate("home") { popUpTo("welcome") { inclusive = true } } }
        )
    }
}

// ---------------------------------------------------------------------------
// Home — persistent setup status (PRD §17)
// ---------------------------------------------------------------------------
@Composable
fun HomeScreen(nav: NavController) {
    // TODO: render OnboardingStatusStore.isFullySetUp + per-item rows,
    // sensitivity slider, and current phrase — see PRD §17 "Home Screen".
    OnboardingScaffold(
        title = "Voice Lock is active",
        body = "Say your phrase any time the screen is on to lock your phone.",
        primaryLabel = "Settings",
        onPrimary = { /* TODO: navigate to settings */ }
    )
}

// ---------------------------------------------------------------------------
// Shared scaffold
// ---------------------------------------------------------------------------
@Composable
private fun OnboardingScaffold(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) {
            Text(primaryLabel)
        }
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) {
                Text(secondaryLabel)
            }
        }
    }
}

private fun openAppSettings(context: android.content.Context) {
    val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = android.net.Uri.fromParts("package", context.packageName, null)
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
VOICELOCK_EOF_MARKER

cat > "app/src/main/java/com/voicelock/app/ui/theme/Theme.kt" << 'VOICELOCK_EOF_MARKER'
package com.voicelock.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

@Composable
fun VoiceLockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
VOICELOCK_EOF_MARKER

cat > "app/src/main/java/com/voicelock/app/util/BatteryExemption.kt" << 'VOICELOCK_EOF_MARKER'
package com.voicelock.app.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * See PRD §8 Risk 1: this exemption is what allows WakeWordService to start
 * from the SCREEN_ON background broadcast receiver at all on API 34+.
 * Without it, the OS treats the app as "in the background" with no
 * while-in-use RECORD_AUDIO grant and throws SecurityException immediately.
 */
object BatteryExemption {

    fun isExempt(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    @SuppressLint("BatteryLife")
    fun requestExemptionIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    fun openBatterySettingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}
VOICELOCK_EOF_MARKER

cat > "app/src/main/java/com/voicelock/app/util/OemBatterySettings.kt" << 'VOICELOCK_EOF_MARKER'
package com.voicelock.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Deep-links into manufacturer-specific "autostart" / "protected apps" /
 * "no restrictions" settings screens. See PRD §15.1 and §17 Screen 4 —
 * these paths change across firmware versions and must be maintained
 * over the app's lifetime, not treated as a one-time build.
 *
 * Component names below are the commonly-documented ones as of 2026;
 * always wrap the launch in try/catch and fall back to the generic
 * app-details settings screen if the specific activity isn't found.
 */
object OemBatterySettings {

    private data class OemIntent(val pkg: String, val cls: String)

    private val knownIntents: Map<String, List<OemIntent>> = mapOf(
        "xiaomi" to listOf(
            OemIntent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
        ),
        "huawei" to listOf(
            OemIntent("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
        ),
        "honor" to listOf(
            OemIntent("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
        ),
        "oppo" to listOf(
            OemIntent("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            OemIntent("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")
        ),
        "vivo" to listOf(
            OemIntent("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
        ),
        "oneplus" to listOf(
            OemIntent("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
        ),
        "samsung" to listOf(
            OemIntent("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")
        )
    )

    /** Only show Onboarding Screen 4 for manufacturers we actually have a deep link for. */
    fun isKnownRestrictiveOem(): Boolean =
        knownIntents.containsKey(Build.MANUFACTURER.lowercase())

    fun manufacturerDisplayName(): String =
        Build.MANUFACTURER.replaceFirstChar { it.uppercase() }

    /** Attempts each known intent for this OEM; returns true if one launched. */
    fun openOemSettings(context: Context): Boolean {
        val candidates = knownIntents[Build.MANUFACTURER.lowercase()] ?: return false
        for (candidate in candidates) {
            try {
                val intent = Intent().apply {
                    component = android.content.ComponentName(candidate.pkg, candidate.cls)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                continue
            } catch (_: Exception) {
                continue
            }
        }
        return false
    }

    /** Fallback: generic per-app battery settings screen, always available on stock Android. */
    fun openGenericBatterySettings(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
VOICELOCK_EOF_MARKER

cat > "app/src/main/res/drawable/ic_launcher_foreground.xml" << 'VOICELOCK_EOF_MARKER'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M54,30 C46,30 40,36 40,44 L40,50 L36,50 C33,50 30,53 30,56 L30,78 C30,81 33,84 36,84 L72,84 C75,84 78,81 78,78 L78,56 C78,53 75,50 72,50 L68,50 L68,44 C68,36 62,30 54,30 Z M54,38 C58,38 60,40 60,44 L60,50 L48,50 L48,44 C48,40 50,38 54,38 Z" />
</vector>
VOICELOCK_EOF_MARKER

cat > "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml" << 'VOICELOCK_EOF_MARKER'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
VOICELOCK_EOF_MARKER

cat > "app/src/main/res/values/colors.xml" << 'VOICELOCK_EOF_MARKER'
<resources>
    <color name="ic_launcher_background">#1565C0</color>
</resources>
VOICELOCK_EOF_MARKER

cat > "app/src/main/res/values/strings.xml" << 'VOICELOCK_EOF_MARKER'
<resources>
    <string name="app_name">VoiceLock</string>
</resources>
VOICELOCK_EOF_MARKER

cat > "app/src/main/res/values/themes.xml" << 'VOICELOCK_EOF_MARKER'
<resources>
    <style name="Theme.VoiceLock" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
VOICELOCK_EOF_MARKER

cat > "app/src/main/res/xml/device_admin_policies.xml" << 'VOICELOCK_EOF_MARKER'
<?xml version="1.0" encoding="utf-8"?>
<!--
  Deliberately minimal: we only need force-lock capability for lockNow().
  Requesting fewer policies here means Android's system Device Admin
  confirmation screen shows fewer scary bullet points to the user.
-->
<device-admin xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-policies>
        <force-lock />
    </uses-policies>
</device-admin>
VOICELOCK_EOF_MARKER

cat > "build.gradle.kts" << 'VOICELOCK_EOF_MARKER'
plugins {
    id("com.android.application") version "8.6.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}
VOICELOCK_EOF_MARKER

cat > "gradle.properties" << 'VOICELOCK_EOF_MARKER'
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
VOICELOCK_EOF_MARKER

cat > "gradle/wrapper/gradle-wrapper.properties" << 'VOICELOCK_EOF_MARKER'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
VOICELOCK_EOF_MARKER

cat > "settings.gradle.kts" << 'VOICELOCK_EOF_MARKER'
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "VoiceLock"
include(":app")
VOICELOCK_EOF_MARKER

echo "Done. VoiceLock project files created."
