package com.voicelock.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicelock.app.data.OnboardingStatusStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val onboardingStatusStore: OnboardingStatusStore
) : ViewModel() {

    val sensitivity: StateFlow<Float> = kotlinx.coroutines.flow.MutableStateFlow(0.6f).also { state ->
        viewModelScope.launch {
            onboardingStatusStore.sensitivity.collect { state.value = it }
        }
    }

    fun setSensitivity(value: Float) {
        viewModelScope.launch { onboardingStatusStore.setSensitivity(value) }
    }
}
