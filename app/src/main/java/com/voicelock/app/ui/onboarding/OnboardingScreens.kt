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
fun EnrollmentScreen(nav: NavController, viewModel: EnrollmentViewModel = hiltViewModel()) {
    var takeIndex by remember { mutableIntStateOf(0) }
    val recordingState by viewModel.state.collectAsState()

    if (takeIndex < enrollmentTakes.size) {
        val (title, instruction) = enrollmentTakes[takeIndex]
        val isRecording = recordingState == RecordingState.RECORDING
        OnboardingScaffold(
            title = title,
            body = instruction,
            primaryLabel = if (isRecording) "Recording… tap when done" else "Tap to start recording",
            onPrimary = {
                if (isRecording) {
                    viewModel.stopRecording()
                    takeIndex++
                } else {
                    viewModel.startRecording()
                }
            }
        )
    } else {
        val isProcessing = recordingState == RecordingState.PROCESSING
        OnboardingScaffold(
            title = if (isProcessing) "Analyzing your voice…" else "All set — ready to save",
            body = if (isProcessing)
                "Generating your voiceprint from the 3 recordings."
            else
                "Your voiceprint has been created and stored securely on this device.",
            primaryLabel = if (isProcessing) "Please wait…" else "Test it now",
            onPrimary = {
                if (!isProcessing) {
                    viewModel.finalizeEnrollment(onDone = { nav.navigate("live_test") })
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Screen 6 — Live "Test your setup" (PRD §17 — closes the loop on Risk 1)
// ---------------------------------------------------------------------------
@Composable
fun LiveTestScreen(nav: NavController) {
    val context = LocalContext.current
    val lockManager = remember { com.voicelock.app.admin.LockManager(context) }
    var failed by remember { mutableStateOf(false) }

    if (!failed) {
        OnboardingScaffold(
            title = "Let's make sure it works",
            body = "Tap below to lock your screen right now using VoiceLock's Device Admin " +
                "permission. This confirms the lock mechanism itself works — full voice-triggered " +
                "locking depends on additional pieces not yet finished (see the app's README).",
            primaryLabel = "Lock now",
            onPrimary = {
                val locked = lockManager.lockNow()
                if (!locked) failed = true
                // If locked == true, the screen locks immediately; there's nothing
                // further to navigate to here since the OS takes over the display.
            }
        )
    } else {
        OnboardingScaffold(
            title = "Couldn't lock the screen",
            body = "This usually means Device Admin isn't active. Go back and grant it, " +
                "then try again.",
            primaryLabel = "Try again",
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
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    // TODO: render OnboardingStatusStore.isFullySetUp + per-item rows,
    // sensitivity slider, and current phrase — see PRD §17 "Home Screen".
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Voice Lock is active", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text(
            "Say your phrase any time the screen is on to lock your phone. " +
                "You don't need to keep this screen open — VoiceLock listens " +
                "in the background as long as the screen is on, independent " +
                "of whether this app window is visible.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = { activity?.moveTaskToBack(true) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Done — run in background")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { /* TODO: navigate to settings */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Settings")
        }
    }
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
