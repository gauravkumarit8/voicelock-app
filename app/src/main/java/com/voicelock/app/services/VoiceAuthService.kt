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
            android.util.Log.w(TAG, "No enrolled voiceprint found — skipping verification")
            stopSelf()
            return
        }

        try {
            speakerVerificationEngine.loadModel()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Speaker verification engine failed to initialize", e)
            stopSelf()
            return
        }

        val candidate = speakerVerificationEngine.embed(audioSamples)
        val similarity = speakerVerificationEngine.cosineSimilarity(enrolled, candidate)
        val threshold = onboardingStatusStore.sensitivity.first()
        android.util.Log.i(TAG, "Speaker similarity=$similarity threshold=$threshold")

        if (similarity >= threshold) {
            val locked = lockManager.lockNow()
            android.util.Log.i(TAG, "lockNow() called, result=$locked")
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
        private const val TAG = "VoiceAuthService"
        const val EXTRA_AUDIO_SAMPLES = "audio_samples"
    }
}
