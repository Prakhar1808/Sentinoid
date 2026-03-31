package com.sentinoid.app.security

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.Base64
import java.util.Date

class HoneypotEngine(private val context: Context) {
    companion object {
        private const val TAG = "HoneypotEngine"
        private const val ACCESS_LOG_MAX = 100
        private const val PREF_INITIALIZED = "honeypot_initialized_v3"
        private const val PREFS_HONEYPOT_DIR = "honeypot_v3"
    }

    data class AccessEvent(
        val timestamp: Long,
        val filePath: String,
        val operation: String
    )

    data class HoneypotStats(
        val decoyFilesCreated: Int,
        val accessAttempts: Int,
        val uniqueAttackers: Int,
        val lastAccessTime: Long?
    )

    private val activityLogger by lazy { ActivityLogger.getInstance(context) }
    private val securePreferences = SecurePreferences(context)
    private val accessLog: MutableList<AccessEvent> = mutableListOf()
    private val handler = Handler(Looper.getMainLooper())
    
    // Use internal app storage - no permissions needed
    private val honeypotDir: File by lazy {
        File(context.filesDir, PREFS_HONEYPOT_DIR).apply {
            if (!exists()) mkdirs()
        }
    }

    fun initializeHoneypot() {
        try {
            if (!isHoneypotInitialized()) {
                Log.i(TAG, "Initializing honeypot in: ${honeypotDir.absolutePath}")
                createDecoyFiles()
                securePreferences.putBoolean(PREF_INITIALIZED, true)
                activityLogger.logHoneypot("Honeypot initialized with ${getDecoyCount()} files", ActivityLogger.SEVERITY_INFO)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize: ${e.message}")
        }
    }

    private fun getDecoyCount(): Int {
        return try {
            honeypotDir.listFiles()?.count { it.isFile } ?: 0
        } catch (e: Exception) { 0 }
    }

    fun createDecoyFiles() {
        val files = listOf(
            "passwords.txt" to generateFakePasswords(),
            "api_keys.txt" to generateFakeApiKeys(),
            "wallet.dat" to generateFakeWallet(),
            "contacts.csv" to generateFakeContacts(),
            "config.ini" to generateFakeConfig(),
            "database.sql" to generateFakeDatabase(),
            "ssh_key.pem" to generateFakeSSHKey(),
            "2fa_codes.txt" to generateFake2FA(),
            "banking.txt" to generateFakeBanking(),
            "tokens.log" to generateFakeTokens()
        )

        files.forEach { (name, content) ->
            createFile(name, content)
        }
    }

    private fun createFile(name: String, content: String) {
        try {
            val file = File(honeypotDir, name)
            FileOutputStream(file).use { it.write(content.toByteArray()) }
            
            val timestamp = System.currentTimeMillis()
            securePreferences.putLong("honeypot_${file.name}", timestamp)
            
            // Broadcast creation
            broadcastDecoyCreated(file.absolutePath, timestamp)
            Log.i(TAG, "Created: $name")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create $name: ${e.message}")
        }
    }

    private fun broadcastDecoyCreated(path: String, timestamp: Long) {
        handler.post {
            try {
                val intent = Intent("com.sentinoid.app.HONEYPOT_DECOY_CREATED").apply {
                    putExtra("timestamp", timestamp)
                    putExtra("file", path)
                    putExtra("operation", "DECOY_CREATED")
                    setPackage(context.packageName)
                }
                context.sendBroadcast(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Broadcast failed: ${e.message}")
            }
        }
    }

    private fun generateFakePasswords(): String {
        return """# Password Vault
admin:Password123!
root:SecurePass2024@
user:Qwerty789#
john:LetMeIn2024$"""
    }

    private fun generateFakeApiKeys(): String {
        val key1 = randomString(32)
        val key2 = randomString(16).uppercase()
        return """STRIPE_API_KEY=sk_live_$key1
AWS_ACCESS_KEY=AKIA$key2
GITHUB_TOKEN=ghp_${randomString(36)}"""
    }

    private fun generateFakeWallet(): String {
        val entropy = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        return "BIP39_SEED: ${entropy.joinToString("") { "%02x".format(it) }}"
    }

    private fun generateFakeContacts(): String {
        return """Alex Smith,alex.smith@gmail.com,+1-555-1234
Jordan Johnson,jordan.j@yahoo.com,+1-555-5678
Casey Williams,casey.w@outlook.com,+1-555-9012"""
    }

    private fun generateFakeConfig(): String {
        return """[Database]
Host=prod-db.internal.com
User=db_admin
Pass=SecurePass123!

[API]
SecretKey=${randomString(32)}
Endpoint=https://api.internal.com"""
    }

    private fun generateFakeDatabase(): String {
        return """-- Users
INSERT INTO users VALUES (1,'admin','Secret123!','admin@company.com');
INSERT INTO users VALUES (2,'john','Pass456!','john@company.com');

-- Cards
INSERT INTO cards VALUES (1,'4532${randomDigits(12)}','12/25','123');"""
    }

    private fun generateFakeSSHKey(): String {
        return """-----BEGIN OPENSSH PRIVATE KEY-----
${randomBase64(400)}
-----END OPENSSH PRIVATE KEY-----"""
    }

    private fun generateFake2FA(): String {
        return (1..10).joinToString("\n") { "CODE${it}: ${10000000 + SecureRandom().nextInt(90000000)}" }
    }

    private fun generateFakeBanking(): String {
        return """Account: ****4532
Routing: 021000021
Balance: $12,450.00

Transaction:
- Wire: -$5,000.00
- Deposit: +$2,500.00"""
    }

    private fun generateFakeTokens(): String {
        return (1..5).joinToString("\n") { i ->
            val time = System.currentTimeMillis() - (i * 86400000L)
            "[${Date(time)}] TOKEN: ${randomBase64(40)}"
        }
    }

    private fun randomString(len: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..len).map { chars[SecureRandom().nextInt(chars.length)] }.joinToString("")
    }

    private fun randomDigits(len: Int): String {
        return (1..len).map { SecureRandom().nextInt(10) }.joinToString("")
    }

    private fun randomBase64(len: Int): String {
        val bytes = ByteArray(len).apply { SecureRandom().nextBytes(this) }
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun checkForAccess(): List<AccessEvent> {
        // Scan for file access by checking modification times
        val events = mutableListOf<AccessEvent>()
        try {
            honeypotDir.listFiles()?.forEach { file ->
                val created = securePreferences.getLong("honeypot_${file.name}", 0)
                val modified = file.lastModified()
                
                if (created > 0 && modified > created + 1000) {
                    val event = AccessEvent(modified, file.absolutePath, "ACCESSED")
                    synchronized(accessLog) {
                        if (!accessLog.any { it.filePath == event.filePath && (modified - it.timestamp) < 5000 }) {
                            accessLog.add(event)
                            if (accessLog.size > ACCESS_LOG_MAX) accessLog.removeAt(0)
                            
                            // Broadcast
                            handler.post {
                                try {
                                    val intent = Intent("com.sentinoid.app.HONEYPOT_ACCESS").apply {
                                        putExtra("timestamp", modified)
                                        putExtra("file", file.absolutePath)
                                        putExtra("operation", "ACCESSED")
                                        setPackage(context.packageName)
                                    }
                                    context.sendBroadcast(intent)
                                } catch (e: Exception) {}
                            }
                            
                            activityLogger.logHoneypot("File accessed: ${file.name}", ActivityLogger.SEVERITY_WARNING)
                        }
                    }
                    events.add(event)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Check access error: ${e.message}")
        }
        return events
    }

    fun getHoneypotStats(): HoneypotStats {
        val files = getDecoyCount()
        synchronized(accessLog) {
            val unique = accessLog.map { it.filePath }.toSet()
            val lastTime = accessLog.maxOfOrNull { it.timestamp }
            return HoneypotStats(files, accessLog.size, unique.size, lastTime)
        }
    }

    fun getAccessLog(): List<AccessEvent> = synchronized(accessLog) { accessLog.toList() }
    fun isHoneypotInitialized(): Boolean = securePreferences.getBoolean(PREF_INITIALIZED, false)

    fun purgeHoneypot() {
        try {
            honeypotDir.listFiles()?.forEach { it.delete() }
            synchronized(accessLog) { accessLog.clear() }
            securePreferences.putBoolean(PREF_INITIALIZED, false)
            activityLogger.logHoneypot("Honeypot purged", ActivityLogger.SEVERITY_WARNING)
        } catch (e: Exception) {
            Log.e(TAG, "Purge error: ${e.message}")
        }
    }

    fun getHoneypotDirectory(): String = honeypotDir.absolutePath

    fun regenerateDecoys(): List<String> {
        val filePaths = mutableListOf<String>()
        try {
            purgeHoneypot()
            createDecoyFiles()
            securePreferences.putBoolean(PREF_INITIALIZED, true)
            // Collect all created file paths
            honeypotDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    filePaths.add(file.absolutePath)
                }
            }
            activityLogger.logHoneypot("Decoys regenerated: ${filePaths.size} files in ${honeypotDir.absolutePath}", ActivityLogger.SEVERITY_INFO)
        } catch (e: Exception) {
            Log.e(TAG, "Regenerate error: ${e.message}")
        }
        return filePaths
    }
}
