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

    val sensitivity: Flow<Float> = context.dataStore.data.map {
        // Default lowered from the PRD's original 0.85 — that threshold assumed a trained
        // neural speaker embedding (GE2E/ECAPA). The current SpeakerVerificationEngine is a
        // classical DSP feature extractor (see its class doc) with a different, generally
        // less cleanly-separated similarity distribution between same-speaker/different-speaker
        // pairs. 0.6 is a starting point for testing, not a validated value — use the Settings
        // slider to tune it against your own voice once you're testing for real.
        it[Keys.SENSITIVITY] ?: 0.6f
    }

    suspend fun setMicGranted(v: Boolean) = context.dataStore.edit { it[Keys.MIC_GRANTED] = v }
    suspend fun setDeviceAdminActive(v: Boolean) = context.dataStore.edit { it[Keys.DEVICE_ADMIN_ACTIVE] = v }
    suspend fun setBatteryExempt(v: Boolean) = context.dataStore.edit { it[Keys.BATTERY_EXEMPT] = v }
    suspend fun setOemStepAcknowledged(v: Boolean) = context.dataStore.edit { it[Keys.OEM_STEP_ACKNOWLEDGED] = v }
    suspend fun setVoiceEnrolled(v: Boolean) = context.dataStore.edit { it[Keys.VOICE_ENROLLED] = v }
    suspend fun setLiveTestPassed(v: Boolean) = context.dataStore.edit { it[Keys.LIVE_TEST_PASSED] = v }
    suspend fun setSensitivity(v: Float) = context.dataStore.edit { it[Keys.SENSITIVITY] = v }
}
