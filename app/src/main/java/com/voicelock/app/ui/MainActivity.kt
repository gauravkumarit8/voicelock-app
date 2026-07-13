package com.voicelock.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.voicelock.app.data.OnboardingStatusStore
import com.voicelock.app.ui.onboarding.*
import com.voicelock.app.ui.theme.VoiceLockTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var onboardingStatusStore: OnboardingStatusStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VoiceLockTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Resolve whether onboarding was already completed on a prior
                    // launch before rendering the NavHost, so returning users land
                    // on Home directly instead of redoing the whole flow every time.
                    var startDestination by remember { mutableStateOf<String?>(null) }
                    LaunchedEffect(Unit) {
                        val alreadySetUp = onboardingStatusStore.isFullySetUp.first()
                        startDestination = if (alreadySetUp) "home" else "welcome"
                    }

                    val resolvedStart = startDestination
                    if (resolvedStart != null) {
                        val navController = rememberNavController()
                        NavHost(navController = navController, startDestination = resolvedStart) {
                            composable("welcome") { WelcomeScreen(navController) }
                            composable("mic_permission") { MicPermissionScreen(navController) }
                            composable("device_admin") { DeviceAdminScreen(navController) }
                            composable("battery_exemption") { BatteryExemptionScreen(navController) }
                            composable("oem_settings") { OemSettingsScreen(navController) }
                            composable("enrollment") { EnrollmentScreen(navController) }
                            composable("live_test") { LiveTestScreen(navController) }
                            composable("home") { HomeScreen(navController) }
                            composable("settings") { SettingsScreen(navController) }
                        }
                    }
                    // else: brief blank frame while startDestination resolves —
                    // DataStore reads are fast enough that this isn't visible in practice.
                }
            }
        }
    }
}
