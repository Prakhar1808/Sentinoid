package com.sentinoid.app.security

import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.Base64
import java.util.Date
import kotlin.random.Random

class HoneypotEngine(private val context: Context) {

    companion object {
        private const val HONEYPOT_DIR = "honeypot_data"
        private const val ACCESS_LOG_MAX = 100
        
        // Decoy content types
        private val DECOY_PASSWORDS: List<String> = listOf(
            "Password123!", "SecurePass2024!", "Admin@12345", 
            "Qwerty!2345", "LetMeIn#2024"
        )
        
        private val DECOY_API_KEYS: List<String> = listOf(
            "sk_live_51HyG...2xYz9",
            "AKIAIOSFODNN7EXAMPLE",
            "ghp_xxxxxxxxxxxxxxxxxxxx",
            "bearer_token_abc123xyz"
        )
        
        private val DECOY_CREDIT_CARDS: List<String> = listOf(
            "4532015112830366", "5425233430109903", "374245455400126"
        )
        
        private val DECOY_CONTACTS: List<String> = listOf(
            "John Doe,john@example.com,+1-555-0123",
            "Jane Smith,jane@example.com,+1-555-0456",
            "Bob Johnson,bob@example.com,+1-555-0789"
        )
    }

    data class AccessEvent(
        val timestamp: Long,
        val filePath: String,
        val operation: String,
        val processInfo: String?
    )

    data class HoneypotStats(
        val decoyFilesCreated: Int,
        val accessAttempts: Int,
        val uniqueAttackers: Int,
        val lastAccessTime: Long?
    )

    private val securePreferences = SecurePreferences(context)
    private val accessLog: MutableList<AccessEvent> = mutableListOf<AccessEvent>()
    private val honeypotDir: File by lazy {
        val dir = File(context.filesDir, HONEYPOT_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    fun initializeHoneypot() {
        if (!isHoneypotInitialized()) {
            createDecoyFiles()
            securePreferences.putBoolean("honeypot_initialized", true)
        }
    }

    fun createDecoyFiles() {
        createDecoyFile("passwords.txt", generateFakePasswords())
        createDecoyFile("api_keys.json", generateFakeApiKeys())
        createDecoyFile("wallet_backup.dat", generateFakeWalletData())
        createDecoyFile("contacts.csv", DECOY_CONTACTS.joinToString("\n"))
        createDecoyFile("config.ini", generateFakeConfig())
        createDecoyFile("db_dump.sql", generateFakeDatabase())
        createDecoyFile("id_rsa", generateFakeSSHKey())
        createDecoyFile("2fa_backup_codes.txt", generateFake2FABackup())
        createDecoyFile("banking_info.pdf", generateFakeBankingData())
        createDecoyFile("session_tokens.log", generateFakeTokens())
    }

    private fun createDecoyFile(filename: String, content: String) {
        val file = File(honeypotDir, filename)
        try {
            val fos = FileOutputStream(file)
            fos.write(content.toByteArray())
            fos.close()
            
            // Track creation time
            val creationTime = System.currentTimeMillis()
            file.setLastModified(creationTime)
            securePreferences.putLong("honeypot_created_${filename}", creationTime)
            
            // Set file as readable
            file.setReadable(true, false)
        } catch (e: Exception) {
            // Silent fail
        }
    }

    private fun generateFakePasswords(): String {
        val lines = mutableListOf<String>("# Passwords - DO NOT SHARE")
        for (i in DECOY_PASSWORDS.indices) {
            val pass = DECOY_PASSWORDS[i]
            val username = listOf("admin", "root", "user", "john", "administrator").random()
            lines.add("${i + 1}. $username:$pass")
        }
        return lines.joinToString("\n")
    }

    private fun generateFakeApiKeys(): String {
        return """{
            "stripe": "${DECOY_API_KEYS[0]}",
            "aws": "${DECOY_API_KEYS[1]}",
            "github": "${DECOY_API_KEYS[2]}",
            "api_bearer": "${DECOY_API_KEYS[3]}"
        }""".trimIndent()
    }

    private fun generateFakeWalletData(): String {
        val random = SecureRandom()
        val entropy = ByteArray(32)
        random.nextBytes(entropy)
        return "BIP39_ENTROPY:" + entropy.joinToString("") { "%02x".format(it) }
    }

    private fun generateFakeConfig(): String {
        return """[database]
        host=prod-db.internal.com
        username=db_admin
        password=${DECOY_PASSWORDS[0]}
        
        [api]
        secret_key=${DECOY_API_KEYS[3]}
        endpoint=https://api.internal.com/v1""".trimIndent()
    }

    private fun generateFakeDatabase(): String {
        return """-- Users table
        INSERT INTO users VALUES (1, 'admin', '${DECOY_PASSWORDS[0]}', 'admin@company.com');
        INSERT INTO users VALUES (2, 'john_doe', '${DECOY_PASSWORDS[1]}', 'john@company.com');
        
        -- Credit cards
        INSERT INTO payments VALUES (1, '${DECOY_CREDIT_CARDS[0]}', 'John Doe', '12/25', '123');
        INSERT INTO payments VALUES (2, '${DECOY_CREDIT_CARDS[1]}', 'Jane Smith', '11/26', '456');""".trimIndent()
    }

    private fun generateFakeSSHKey(): String {
        return """-----BEGIN OPENSSH PRIVATE KEY-----
        ${generateRandomBase64(600)}
        -----END OPENSSH PRIVATE KEY-----""".trimIndent()
    }

    private fun generateFake2FABackup(): String {
        val codes = mutableListOf<String>()
        for (i in 0 until 10) {
            codes.add(Random.nextInt(10000000, 99999999).toString())
        }
        return """# 2FA Backup Codes - Keep Safe!
        ${codes.joinToString("\n")}
        # Generated: 2024-01-15""".trimIndent()
    }

    private fun generateFakeBankingData(): String {
        return """Account: ****4532
        Routing: 021000021
        Balance: $12,450.00
        
        Recent Transactions:
        - Wire Transfer: -$5,000.00
        - Deposit: +$2,500.00""".trimIndent()
    }

    private fun generateFakeTokens(): String {
        val tokens = mutableListOf<String>()
        for (i in 0 until 5) {
            val timestamp = System.currentTimeMillis() - (i.toLong() * 86400000L)
            val token = generateRandomBase64(50)
            tokens.add("[${Date(timestamp)}] Session: $token")
        }
        return tokens.joinToString("\n")
    }

    private fun generateRandomBase64(length: Int): String {
        val bytes = ByteArray(length)
        SecureRandom().nextBytes(bytes)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Base64.getEncoder().encodeToString(bytes)
        } else {
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        }
    }

    fun checkForHoneypotAccess(): List<AccessEvent> {
        val suspiciousAccesses = mutableListOf<AccessEvent>()
        
        val files = honeypotDir.listFiles() ?: return suspiciousAccesses
        for (file in files) {
            val lastModified = file.lastModified()
            val created = securePreferences.getLong("honeypot_created_${file.name}", 0)
            
            // Allow 1 second buffer for file system timestamp precision
            if (lastModified > created + 1000 && created > 0) {
                val event = AccessEvent(
                    timestamp = lastModified,
                    filePath = file.absolutePath,
                    operation = "READ/MODIFY",
                    processInfo = null
                )
                
                // Only log if not already logged (avoid duplicates)
                var alreadyLogged = false
                for (loggedEvent in accessLog) {
                    if (loggedEvent.timestamp == event.timestamp && loggedEvent.filePath == event.filePath) {
                        alreadyLogged = true
                        break
                    }
                }
                
                if (!alreadyLogged) {
                    suspiciousAccesses.add(event)
                    logHoneypotAccess(event)
                }
            }
        }
        
        return suspiciousAccesses
    }

    private fun logHoneypotAccess(event: AccessEvent) {
        accessLog.add(event)
        if (accessLog.size > ACCESS_LOG_MAX) {
            accessLog.removeAt(0)
        }
        
        triggerSilentAlarm(event)
        
        val key = "honeypot_access_${event.timestamp}"
        securePreferences.putString(key, "${event.filePath}|${event.operation}")
    }

    private fun triggerSilentAlarm(event: AccessEvent) {
        val intent = Intent("com.sentinoid.app.HONEYPOT_ACCESS")
        intent.putExtra("timestamp", event.timestamp)
        intent.putExtra("file", event.filePath)
        intent.setPackage(context.packageName)
        context.sendBroadcast(intent)
    }

    fun getHoneypotStats(): HoneypotStats {
        val files = honeypotDir.listFiles()?.size ?: 0
        val accesses = accessLog.size
        
        val uniquePathsSet = mutableSetOf<String>()
        var lastTime: Long? = null
        for (event in accessLog) {
            uniquePathsSet.add(event.filePath)
            if (lastTime == null || event.timestamp > lastTime) {
                lastTime = event.timestamp
            }
        }
        
        return HoneypotStats(
            decoyFilesCreated = files,
            accessAttempts = accesses,
            uniqueAttackers = uniquePathsSet.size,
            lastAccessTime = lastTime
        )
    }

    fun isHoneypotInitialized(): Boolean {
        return securePreferences.getBoolean("honeypot_initialized", false)
    }

    fun purgeHoneypot() {
        honeypotDir.deleteRecursively()
        honeypotDir.mkdirs()
        accessLog.clear()
        securePreferences.putBoolean("honeypot_initialized", false)
    }

    fun getAccessLog(): List<AccessEvent> {
        return accessLog.toList()
    }

    fun regenerateDecoys() {
        purgeHoneypot()
        createDecoyFiles()
        securePreferences.putBoolean("honeypot_initialized", true)
    }
}
