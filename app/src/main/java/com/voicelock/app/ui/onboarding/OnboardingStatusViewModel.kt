package com.voicelock.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicelock.app.data.OnboardingStatusStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Persists each onboarding step's completion so isFullySetUp can actually become true. */
@HiltViewModel
class OnboardingStatusViewModel @Inject constructor(
    private val store: OnboardingStatusStore
) : ViewModel() {
    val isFullySetUp = store.isFullySetUp
    val userPaused = store.userPaused
    fun setUserPaused(v: Boolean) { viewModelScope.launch { store.setUserPaused(v) } }
    fun setMicGranted(v: Boolean) { viewModelScope.launch { store.setMicGranted(v) } }
    fun setDeviceAdminActive(v: Boolean) { viewModelScope.launch { store.setDeviceAdminActive(v) } }
    fun setBatteryExempt(v: Boolean) { viewModelScope.launch { store.setBatteryExempt(v) } }
    fun setOemStepAcknowledged(v: Boolean) { viewModelScope.launch { store.setOemStepAcknowledged(v) } }
    fun setLiveTestPassed(v: Boolean) { viewModelScope.launch { store.setLiveTestPassed(v) } }
}
