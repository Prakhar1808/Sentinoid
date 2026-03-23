package com.sentinoid.shield

object PermissionMapper {

    // Based on security research (e.g., SigPID), these are common in malware.
    private val significantPermissions = listOf(
        "android.permission.READ_SMS",
        "android.permission.SEND_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.CAMERA",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.READ_CONTACTS",
        "android.permission.WRITE_CONTACTS",
        "android.permission.RECORD_AUDIO",
        "android.permission.READ_CALL_LOG",
        "android.permission.WRITE_CALL_LOG",
        "android.permission.INTERNET"
    )

    fun createVector(permissions: Set<String>): FloatArray {
        val vector = FloatArray(significantPermissions.size)
        for (i in significantPermissions.indices) {
            if (permissions.contains(significantPermissions[i])) {
                vector[i] = 1.0f
            }
        }
        return vector
    }
}