package com.voicelock.app.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-memory, in-process event log so setup problems are visible on-device
 * without adb. Not persisted, capped at MAX_LINES, newest first.
 */
object DiagnosticsLog {
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private const val MAX_LINES = 200

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines = _lines.asStateFlow()

    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning = _serviceRunning.asStateFlow()

    private val _micOpen = MutableStateFlow(false)
    val micOpen = _micOpen.asStateFlow()

    fun setServiceRunning(running: Boolean) { _serviceRunning.value = running }
    fun setMicOpen(open: Boolean) { _micOpen.value = open }

    @Synchronized
    fun log(tag: String, message: String) {
        val line = "${timeFmt.format(Date())}  [$tag]  $message"
        _lines.value = (listOf(line) + _lines.value).take(MAX_LINES)
    }

    fun clear() { _lines.value = emptyList() }
}
