package com.sentinoid.app

import android.app.Application
import android.content.Context
import com.sentinoid.app.security.CryptoManager
import com.sentinoid.app.security.SecurePreferences

class SentinoidApp : Application() {

    companion object {
        lateinit var instance: SentinoidApp
            private set

        fun getContext(): Context = instance.applicationContext
    }

    lateinit var cryptoManager: CryptoManager
        private set

    lateinit var securePreferences: SecurePreferences
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        cryptoManager = CryptoManager(this)
        securePreferences = SecurePreferences(this)

        initializeSecurity()
    }

    private fun initializeSecurity() {
        if (!cryptoManager.isVaultInitialized()) {
            cryptoManager.initializeVault()
        }
    }
}
