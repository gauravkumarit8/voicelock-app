package com.voicelock.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicelock.app.data.OnboardingStatusStore
import com.voicelock.app.data.VoiceprintStore
import com.voicelock.app.ml.AudioCapture
import com.voicelock.app.ml.SpeakerVerificationEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RecordingState { IDLE, RECORDING, PROCESSING, DONE }

@HiltViewModel
class EnrollmentViewModel @Inject constructor(
    private val speakerVerificationEngine: SpeakerVerificationEngine,
    private val voiceprintStore: VoiceprintStore,
    private val onboardingStatusStore: OnboardingStatusStore
) : ViewModel() {

    private val _state = MutableStateFlow(RecordingState.IDLE)
    val state: StateFlow<RecordingState> = _state

    private val capturedTakes = mutableListOf<FloatArray>()
    @Volatile private var isRecordingFlag = false

    /** Minimum viable sample count (~0.5s at 16kHz) — shorter takes are rejected, see PRD §17 Screen 5. */
    private val minSamples = AudioCapture.SAMPLE_RATE_HZ / 2

    fun startRecording() {
        _state.value = RecordingState.RECORDING
        isRecordingFlag = true
        viewModelScope.launch {
            val samples = AudioCapture.recordUntilStopped { isRecordingFlag }
            if (samples.size < minSamples) {
                // Too short / likely silent tap — discard and let the user redo this take.
                _state.value = RecordingState.IDLE
                return@launch
            }
            capturedTakes.add(samples)
            _state.value = RecordingState.IDLE
        }
    }

    fun stopRecording() {
        isRecordingFlag = false
    }

    val takesCompleted: Int get() = capturedTakes.size

    /** Call after all 3 takes are captured. Embeds each, averages, and persists the voiceprint. */
    fun finalizeEnrollment(onDone: () -> Unit) {
        _state.value = RecordingState.PROCESSING
        viewModelScope.launch {
            speakerVerificationEngine.loadModel()
            val embeddings = capturedTakes.map { speakerVerificationEngine.embed(it) }
            speakerVerificationEngine.release()

            val dim = embeddings.first().size
            val averaged = FloatArray(dim) { i -> embeddings.map { it[i] }.average().toFloat() }
            voiceprintStore.saveEmbedding(voiceprintStore.l2Normalize(averaged))
            onboardingStatusStore.setVoiceEnrolled(true)

            _state.value = RecordingState.DONE
            onDone()
        }
    }
}
