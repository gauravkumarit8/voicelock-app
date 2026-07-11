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
