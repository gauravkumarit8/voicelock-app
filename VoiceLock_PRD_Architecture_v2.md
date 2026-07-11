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
