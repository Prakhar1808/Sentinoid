package com.sentinoid.shield

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import javax.crypto.KeyGenerator

fun createSelfDestructingKey() {
    val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    val builder =
        KeyGenParameterSpec.Builder(
            "SentinoidMasterKey",
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        builder.setUserAuthenticationRequired(true) // Force Biometric
        builder.setInvalidatedByBiometricEnrollment(true) // WIPES KEY if a new finger is added
    }
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        builder.setIsStrongBoxBacked(true) // Use Dedicated Security Chip if available
    }
    
    keyGenerator.init(builder.build())
    keyGenerator.generateKey()
}
