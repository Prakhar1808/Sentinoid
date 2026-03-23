package com.sentinoid.shield

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SilentAlarmManager {

    private val _isLockdownActive = MutableStateFlow(false)
    val isLockdownActive: StateFlow<Boolean> = _isLockdownActive

    private val _tacticalLogs = MutableStateFlow<List<SecurityLogEntry>>(emptyList())
    val tacticalLogs: StateFlow<List<SecurityLogEntry>> = _tacticalLogs.asStateFlow()

    private val _isVaultLocked = MutableStateFlow(false)
    val isVaultLocked: StateFlow<Boolean> = _isVaultLocked

    private val _isBridgeConnected = MutableStateFlow(false)
    val isBridgeConnected: StateFlow<Boolean> = _isBridgeConnected.asStateFlow()

    fun setBridgeConnected(connected: Boolean) {
        _isBridgeConnected.value = connected
    }

    fun triggerLockdown(vector: String = "Unknown Threat") {
        if (_isLockdownActive.value) return

        _isLockdownActive.value = true
        _isVaultLocked.value = true
        addLog(vector)
        
        Log.e("SilentAlarmManager", "LOCKDOWN TRIGGERED: $vector")
    }

    fun addLog(vector: String) {
        val newEntry = SecurityLogEntry(vector = vector)
        _tacticalLogs.value = listOf(newEntry) + _tacticalLogs.value
    }

    fun resolveAllLogs() {
        _tacticalLogs.value = _tacticalLogs.value.map { it.copy(status = ThreatStatus.RESOLVED) }
        _isVaultLocked.value = false
    }

    fun setLockdown(isLocked: Boolean) {
        _isLockdownActive.value = isLocked
        _isVaultLocked.value = isLocked
        if (!isLocked) {
            resolveAllLogs()
        }
    }

    fun isLockdown(): Boolean {
        return isLockdownActive.value
    }
}
