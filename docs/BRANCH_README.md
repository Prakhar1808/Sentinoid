# 🔐 Feature: Security Vault

## Overview

This branch implements the core cryptographic infrastructure for Sentinoid, providing hardware-backed encryption with biometric authentication and secure key management.

## What's Included

### Core Components
- **VaultService.kt** - AES-256-GCM encryption/decryption with Android Keystore
- **KeyManager.kt** - SQLCipher passphrase generation and storage
- **OfflineRecovery.kt** - BIP39 mnemonic generation + Shamir Secret Sharing
- **SecurityModule.kt** - Keystore initialization with biometric binding

### Data Layer
- **Secret.kt** - Entity for encrypted secrets storage
- **SecretDao.kt** - Room DAO for database operations
- **SecretRepository.kt** - Repository pattern implementation
- **SecretViewModel.kt** - MVVM ViewModel for UI interaction
- **SovereignDatabase.kt** - SQLCipher-encrypted Room database
- **SecurityModels.kt** - Data classes for security operations

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    BiometricPrompt                             │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│              Android Keystore / StrongBox                    │
│  • Keys never leave secure silicon                           │
│  • Biometric binding enforced at hardware level             │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                   AES-256-GCM Engine                         │
│  • VaultService: File and string encryption                │
│  • SQLCipher: Database encryption with passphrase            │
└─────────────────────────────────────────────────────────────┘
```

## Key Features

| Feature | Implementation | Security Property |
|---------|---------------|-------------------|
| Hardware-backed keys | Android Keystore / StrongBox | Keys never in application memory |
| Biometric binding | `setUserAuthenticationRequired(true)` | No biometric = no decryption |
| Anti-tamper | `setInvalidatedByBiometricEnrollment(true)` | Auto-key-wipe on new enrollment |
| Database encryption | SQLCipher + hardware-derived passphrase | Double encryption layer |
| Recovery | BIP39 24-word + Shamir Secret Sharing | Offline, distributed backup |

## Usage

```kotlin
// Initialize vault service
val vaultService = VaultService()

// Encrypt data (requires biometric)
val (cipherText, iv) = vaultService.encryptString("sensitive data")

// Decrypt (biometric prompt shown automatically)
val plainText = vaultService.decryptString(cipherText, iv)

// Database with encryption
val passphrase = KeyManager.getOrCreatePassphrase(context)
val db = Room.databaseBuilder(context, SovereignDatabase::class.java, "vault.db")
    .openHelperFactory(SupportFactory(passphrase))
    .build()
```

## Integration with Main

This branch provides the foundation that other features depend on:
- FPM Interceptor stores logs in encrypted database
- Honeypot uses vault for intrusion evidence
- Malware Scanner stores threat intelligence securely

## Dependencies

```kotlin
implementation("androidx.security:security-crypto:1.1.0-alpha06")
implementation("androidx.biometric:biometric:1.1.0")
implementation("net.zetetic:android-database-sqlcipher:4.5.4")
implementation("androidx.room:room-runtime:2.6.1")
implementation("cash.z.ecc.android:kotlin-bip39:1.0.4")
```

## Testing

```bash
# Test biometric binding
adb shell am start -n com.sentinoid.shield/.MainActivity

# Verify key invalidation after adding fingerprint
# Keys should be automatically invalidated
```

## Merge Checklist

- [ ] All cryptographic operations tested
- [ ] Biometric prompt works correctly
- [ ] Database encryption verified
- [ ] BIP39 recovery phrase generation tested
- [ ] Key invalidation on new biometric enrollment verified

## Security Considerations

- Keys are hardware-bound and cannot be exported
- Recovery phrase must be written down and stored physically secure
- SQLCipher provides defense-in-depth for database content
- Biometric requirements prevent unauthorized decryption even with root access
