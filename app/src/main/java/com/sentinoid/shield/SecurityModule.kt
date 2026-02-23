package com.sentinoid.shield

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator

fun createSelfDestructingKey() {
    val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    val builder = KeyGenParameterSpec.Builder("SentinoidMasterKey",
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .setUserAuthenticationRequired(true) // 🔒 Force Biometric
        .setInvalidatedByBiometricEnrollment(true) // 🧨 WIPES KEY if a new finger is added
        .setIsStrongBoxBacked(true) // 🏛️ Use Dedicated Security Chip if available
        .build()

    keyGenerator.init(builder)
    keyGenerator.generateKey()
}