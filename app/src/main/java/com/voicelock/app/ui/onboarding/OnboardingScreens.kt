package com.voicelock.app.ui.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.voicelock.app.admin.VoiceLockDeviceAdminReceiver
import com.voicelock.app.diagnostics.DiagnosticsLog
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
            "anywhere — it all happens on your phone.\n\nThis test build recognizes " +
            "the phrase \"Hey Jarvis\" only — custom phrase training comes later.",
        primaryLabel = "Get started",
        onPrimary = { nav.navigate("mic_permission") }
    )
}

// ---------------------------------------------------------------------------
// Screen 1 — Microphone permission
// ---------------------------------------------------------------------------
@Composable
fun MicPermissionScreen(nav: NavController, status: OnboardingStatusViewModel = hiltViewModel()) {
    var denied by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        status.setMicGranted(granted)
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
fun DeviceAdminScreen(nav: NavController, status: OnboardingStatusViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val active = VoiceLockDeviceAdminReceiver.isActive(context)
        status.setDeviceAdminActive(active)
        if (active) {
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
fun BatteryExemptionScreen(nav: NavController, status: OnboardingStatusViewModel = hiltViewModel()) {
    var denied by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val exempt = BatteryExemption.isExempt(context)
        status.setBatteryExempt(exempt)
        if (exempt) {
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
fun OemSettingsScreen(nav: NavController, status: OnboardingStatusViewModel = hiltViewModel()) {
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
        onSecondary = { status.setOemStepAcknowledged(true); nav.navigate("enrollment") }
    )
}

// ---------------------------------------------------------------------------
// Screen 5 — Voice enrollment (3 varied-condition takes — PRD §15.2)
// ---------------------------------------------------------------------------
private val enrollmentTakes = listOf(
    "Say \"Hey Jarvis\" normally" to
        "This test build recognizes the phrase \"Hey Jarvis\" (a stock demo phrase — " +
        "your own custom phrase isn't trained yet, see the app's README). Speak it in a " +
        "normal, relaxed voice.",
    "Say \"Hey Jarvis\" a bit quicker" to "Now say it slightly faster, like you're in a hurry.",
    "Say \"Hey Jarvis\" with some background noise, if you can" to
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
        val isFailed = recordingState == RecordingState.FAILED
        val errorMessage by viewModel.errorMessage.collectAsState()
        OnboardingScaffold(
            title = when {
                isProcessing -> "Analyzing your voice…"
                isFailed -> "Couldn't save your voiceprint"
                else -> "All set — ready to save"
            },
            body = when {
                isProcessing -> "Generating your voiceprint from the 3 recordings."
                isFailed -> errorMessage ?: "An unknown error occurred. You can try again."
                else -> "Your voiceprint has been created and stored securely on this device."
            },
            primaryLabel = when {
                isProcessing -> "Please wait…"
                isFailed -> "Try again"
                else -> "Test it now"
            },
            onPrimary = {
                when {
                    isProcessing -> { /* no-op while processing */ }
                    isFailed -> viewModel.retryFinalize()
                    else -> viewModel.finalizeEnrollment(onDone = { nav.navigate("live_test") })
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Screen 6 — Live "Test your setup" (PRD §17 — closes the loop on Risk 1)
// ---------------------------------------------------------------------------
@Composable
fun LiveTestScreen(nav: NavController, status: OnboardingStatusViewModel = hiltViewModel()) {
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
                if (!locked) failed = true else status.setLiveTestPassed(true)
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
fun HomeScreen(nav: NavController, status: OnboardingStatusViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    // Live OS state (not stored flags) so revocations outside the app are reflected.
    var micOk by remember { mutableStateOf(false) }
    var adminOk by remember { mutableStateOf(false) }
    var batteryOk by remember { mutableStateOf(false) }

    val refreshAndMaybeStart: () -> Unit = {
        micOk = androidx.core.content.ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        adminOk = VoiceLockDeviceAdminReceiver.isActive(context)
        batteryOk = BatteryExemption.isExempt(context)
        status.setMicGranted(micOk)
        status.setDeviceAdminActive(adminOk)
        status.setBatteryExempt(batteryOk)
        // App is in the foreground here, so starting the mic service is allowed.
        if (micOk && adminOk && batteryOk) {
            com.voicelock.app.services.WakeWordService.start(context)
        }
    }

    // Navigating here from onboarding (same Activity) does NOT fire ON_RESUME —
    // that only fires on a real pause/resume (backgrounding, screen lock, etc).
    // Run once immediately so the service starts the first time setup finishes,
    // not only after the user backgrounds and reopens the app.
    LaunchedEffect(Unit) { refreshAndMaybeStart() }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refreshAndMaybeStart()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val allGood = micOk && adminOk && batteryOk

    // Android 13+: without this the "VoiceLock is ready" notification is hidden.
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            if (allGood) "VoiceLock is active" else "Setup incomplete",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(16.dp))
        StatusRow("Microphone access", micOk)
        StatusRow("Screen-lock permission (Device Admin)", adminOk)
        StatusRow("Battery optimization exemption", batteryOk)
        Spacer(Modifier.height(16.dp))
        Text(
            if (allGood)
                "Say \"Hey Jarvis\" any time the screen is on to lock your phone. " +
                    "You don't need to keep this screen open."
            else
                "Fix the items marked ✗ — VoiceLock can't listen reliably until they're all done.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(32.dp))
        if (!allGood) {
            Button(
                onClick = { nav.navigate("mic_permission") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Finish setup") }
            Spacer(Modifier.height(8.dp))
        } else {
            Button(
                onClick = { activity?.moveTaskToBack(true) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Done — run in background") }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(
            onClick = { nav.navigate("settings") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Settings")
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean) {
    Text("${if (ok) "✓" else "✗"}  $label", style = MaterialTheme.typography.bodyLarge)
}

// ---------------------------------------------------------------------------
// Settings — sensitivity slider (PRD §4/§17)
// ---------------------------------------------------------------------------
@Composable
fun SettingsScreen(nav: NavController, viewModel: SettingsViewModel = hiltViewModel()) {
    val sensitivity by viewModel.sensitivity.collectAsState(initial = 0.6f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        Text("Voice match sensitivity", style = MaterialTheme.typography.titleMedium)
        Text(
            "Lower = easier to trigger, more false accepts. Higher = stricter, more false " +
                "rejects. Current: ${(sensitivity * 100).toInt()}%",
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = sensitivity,
            onValueChange = { viewModel.setSensitivity(it) },
            valueRange = 0.2f..0.95f
        )

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = { nav.navigate("enrollment") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Re-enroll voice")
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { nav.navigate("diagnostics") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Diagnostics")
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { nav.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

// ---------------------------------------------------------------------------
// Diagnostics — live view of what WakeWordService / VoiceAuthService are doing,
// so setup problems are visible on-device without adb.
// ---------------------------------------------------------------------------
@Composable
fun DiagnosticsScreen(nav: NavController) {
    val serviceRunning by DiagnosticsLog.serviceRunning.collectAsState()
    val micOpen by DiagnosticsLog.micOpen.collectAsState()
    val lines by DiagnosticsLog.lines.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Diagnostics", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text("Service: ${if (serviceRunning) "✓ running" else "✗ not running"}")
        Text("Microphone: ${if (micOpen) "✓ open (screen is on)" else "✗ closed (screen is off, or service isn't running)"}")
        Spacer(Modifier.height(4.dp))
        Text(
            "If the service isn't running, go back to Home — it starts there once " +
                "mic, admin, and battery are all ✓. If the mic is closed, turn the screen off " +
                "and back on.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(16.dp))
        Text("Event log (newest first):", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (lines.isEmpty()) {
                item { Text("No events yet. Try saying \"Hey Jarvis\" with the screen on.") }
            }
            items(lines) { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { DiagnosticsLog.clear() }, modifier = Modifier.weight(1f)) {
                Text("Clear")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { nav.popBackStack() }, modifier = Modifier.weight(1f)) {
                Text("Back")
            }
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
