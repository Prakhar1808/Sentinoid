package com.sentinoid.app.security

import android.content.Context
import android.os.Debug
import android.os.Process
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Anti-Debugging Detection
 * Detects if app is being debugged by:
 * - JDWP (Java Debug Wire Protocol)
 * - GDB/LLDB native debugger
 * - Tracing (strace, ptrace)
 * - Frida/Xposed hooking frameworks
 * - Emulator detection
 */
class AntiDebugDetection(private val context: Context) {

    companion object {
        private const val TAG = "AntiDebugDetection"

        // Debug check intervals (milliseconds)
        private const val CHECK_INTERVAL_NORMAL = 2000L
        private const val CHECK_INTERVAL_SUSPICIOUS = 500L

        // Suspicious process names
        private val DEBUG_PROCESSES = listOf(
            "gdb", "lldb", "strace", "ltrace", "frida-server",
            "xposed", "edxp", "magiskd", "supersu"
        )

        // Debug property indicators
        private val DEBUG_PROPERTIES = listOf(
            "ro.debuggable",
            "init.svc.adbd",
            "persist.adb.trace",
            "service.adb.tcp.port"
        )
    }

    data class DebugStatus(
        val isDebugged: Boolean,
        val detectedMethods: List<DebugMethod>,
        val threatLevel: ThreatLevel,
        val timestamp: Long
    )

    enum class DebugMethod {
        JDWP_DEBUGGER,
        NATIVE_DEBUGGER,
        TRACING_DETECTED,
        EMULATOR_ENVIRONMENT,
        HOOKING_FRAMEWORK,
        TAMPERED_BINARY,
        DEBUG_PROPERTIES
    }

    enum class ThreatLevel {
        NONE,       // No debugging detected
        LOW,        // Suspicious but not confirmed
        MEDIUM,     // Likely debugging
        HIGH,       // Active debugging detected
        CRITICAL    // Multiple indicators, self-destruct triggered
    }

    private val securePreferences = SecurePreferences(context)

    fun checkDebugging(): DebugStatus {
        val detectedMethods = mutableListOf<DebugMethod>()

        // 1. Check JDWP (Java Debugger)
        if (isJdwpDebuggerAttached()) {
            detectedMethods.add(DebugMethod.JDWP_DEBUGGER)
        }

        // 2. Check for native debugger (GDB/LLDB)
        if (isNativeDebuggerAttached()) {
            detectedMethods.add(DebugMethod.NATIVE_DEBUGGER)
        }

        // 3. Check if being traced (ptrace, strace)
        if (isBeingTraced()) {
            detectedMethods.add(DebugMethod.TRACING_DETECTED)
        }

        // 4. Check for emulator environment
        if (isEmulatorEnvironment()) {
            detectedMethods.add(DebugMethod.EMULATOR_ENVIRONMENT)
        }

        // 5. Check for hooking frameworks (Frida, Xposed)
        if (detectHookingFrameworks()) {
            detectedMethods.add(DebugMethod.HOOKING_FRAMEWORK)
        }

        // 6. Check binary integrity
        if (detectTamperedBinary()) {
            detectedMethods.add(DebugMethod.TAMPERED_BINARY)
        }

        // 7. Check debug properties
        if (checkDebugProperties()) {
            detectedMethods.add(DebugMethod.DEBUG_PROPERTIES)
        }

        val threatLevel = calculateThreatLevel(detectedMethods)

        return DebugStatus(
            isDebugged = detectedMethods.isNotEmpty(),
            detectedMethods = detectedMethods,
            threatLevel = threatLevel,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Check if JDWP debugger is attached
     */
    private fun isJdwpDebuggerAttached(): Boolean {
        return Debug.isDebuggerConnected() ||
               Thread.currentThread().stackTrace.any { 
                   it.className.contains("jdwp") || 
                   it.methodName.contains("debug") 
               }
    }

    /**
     * Check for native debugger attachment using /proc/self/status
     */
    private fun isNativeDebuggerAttached(): Boolean {
        return try {
            val statusFile = File("/proc/self/status")
            if (!statusFile.exists()) return false

            val content = statusFile.readText()

            // Check TracerPid - non-zero means debugger attached
            val tracerPidMatch = Regex("TracerPid:\\s*(\\d+)").find(content)
            val tracerPid = tracerPidMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

            tracerPid != 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if process is being traced (strace, ptrace)
     */
    private fun isBeingTraced(): Boolean {
        // Method 1: Check if we can ptrace ourselves (fails if already traced)
        return try {
            // Attempt self-ptrace - fails if being traced by another process
            // PR_GET_DUMPABLE = 0
            val result = android.system.Os.prctl(
                0,
                0, 0, 0, 0
            )
            result == 0
        } catch (e: Exception) {
            // Alternative: check process state
            checkProcessStateForTracing()
        }
    }

    private fun checkProcessStateForTracing(): Boolean {
        return try {
            val statFile = File("/proc/self/stat")
            if (!statFile.exists()) return false

            val content = statFile.readText()
            // Process state 't' means traced
            content.contains("(t)")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Detect emulator environment
     */
    private fun isEmulatorEnvironment(): Boolean {
        val indicators = listOf(
            // Check hardware
            android.os.Build.HARDWARE.contains("goldfish"),
            android.os.Build.HARDWARE.contains("ranchu"),
            android.os.Build.HARDWARE.contains("emulator"),

            // Check product
            android.os.Build.PRODUCT.contains("sdk"),
            android.os.Build.PRODUCT.contains("emulator"),
            android.os.Build.PRODUCT.contains("simulator"),

            // Check device
            android.os.Build.DEVICE.contains("emulator"),
            android.os.Build.DEVICE.contains("generic"),

            // Check board
            android.os.Build.BOARD.lowercase().contains("goldfish"),
            android.os.Build.BOARD.lowercase().contains("unknown"),

            // Check for QEMU
            File("/dev/qemu_pipe").exists(),
            File("/dev/goldfish_pipe").exists(),

            // Check for common emulator files
            File("/system/bin/androVM-prop").exists(),
            File("/system/bin/nox-prop").exists(),
            File("/system/lib/libc_malloc_debug_qemu.so").exists(),

            // Check CPU info
            checkCpuInfoForEmulator()
        )

        return indicators.count { it } >= 2
    }

    private fun checkCpuInfoForEmulator(): Boolean {
        return try {
            val cpuInfo = File("/proc/cpuinfo").readText()
            cpuInfo.contains("hypervisor") ||
            cpuInfo.contains("qemu") ||
            cpuInfo.contains("kvm") ||
            cpuInfo.contains("VMware") ||
            cpuInfo.contains("VirtualBox")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Detect hooking frameworks (Frida, Xposed)
     */
    private fun detectHookingFrameworks(): Boolean {
        val indicators = mutableListOf<Boolean>()

        // Check for Frida
        indicators.add(checkFrida())

        // Check for Xposed
        indicators.add(checkXposed())

        // Check for unusual stack traces
        indicators.add(checkStackTraceAnomalies())

        // Check for hooked system calls
        indicators.add(checkSystemCallHooks())

        return indicators.count { it } >= 1
    }

    private fun checkFrida(): Boolean {
        val fridaIndicators = listOf(
            // Frida server ports
            isPortOpen(27042),

            // Frida named pipes
            File("/data/local/tmp/frida-server").exists(),
            File("/data/local/tmp/re.frida.server").exists(),

            // Frida agent in memory (simplified check)
            checkMemoryForFrida(),

            // Check for frida-specific strings in maps
            checkMapsForFrida()
        )

        return fridaIndicators.count { it } >= 1
    }

    private fun checkXposed(): Boolean {
        return try {
            val xposedIndicators = listOf(
                // Check for Xposed classes
                Class.forName("de.robv.android.xposed.XposedBridge"),
                Class.forName("de.robv.android.xposed.XposedHelpers"),

                // Check for Xposed installer files
                File("/data/data/de.robv.android.xposed.installer/bin/XposedBridge.jar").exists(),

                // Check for EdXposed
                File("/system/framework/edxp.jar").exists(),

                // Check for LSPosed
                File("/data/adb/lspd").exists()
            )
            xposedIndicators.any { 
                when (it) {
                    is Class<*> -> true
                    is Boolean -> it
                    else -> false
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun isPortOpen(port: Int): Boolean {
        return try {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress("localhost", port), 100)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun checkMemoryForFrida(): Boolean {
        // Simplified check - in production would scan memory regions
        return try {
            val maps = File("/proc/self/maps").readText()
            maps.contains("frida") || maps.contains("gadget")
        } catch (e: Exception) {
            false
        }
    }

    private fun checkMapsForFrida(): Boolean {
        return try {
            val maps = File("/proc/self/maps").readText()
            maps.contains("linjector") || maps.contains("frida-gadget")
        } catch (e: Exception) {
            false
        }
    }

    private fun checkStackTraceAnomalies(): Boolean {
        val stackTrace = Thread.currentThread().stackTrace
        return stackTrace.any { element ->
            element.className.contains("xposed") ||
            element.className.contains("frida") ||
            element.methodName.contains("hook") ||
            element.methodName.contains("replace")
        }
    }

    private fun checkSystemCallHooks(): Boolean {
        // Check if critical system calls are hooked
        return try {
            // Compare gettimeofday results for timing anomalies
            val times = (0..5).map {
                val start = System.nanoTime()
                System.currentTimeMillis()
                System.nanoTime() - start
            }

            // If timing is too consistent, might be hooked
            val variance = times.zipWithNext { a, b -> kotlin.math.abs(a - b) }
            variance.all { it < 1000 } // Suspiciously consistent
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Detect tampered binary
     */
    private fun detectTamperedBinary(): Boolean {
        return try {
            // Check APK signature (simplified)
            val pm = context.packageManager
            val packageInfo = pm.getPackageInfo(context.packageName, 
                android.content.pm.PackageManager.GET_SIGNATURES)

            // In production: verify signature against known good hash
            // For now: check if debug build
            (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check debug-related system properties using reflection
     */
    private fun checkDebugProperties(): Boolean {
        val debugProps = DEBUG_PROPERTIES.map { prop ->
            try {
                val systemProperties = Class.forName("android.os.SystemProperties")
                val getMethod = systemProperties.getMethod("get", String::class.java, String::class.java)
                val value = getMethod.invoke(null, prop, "0") as String
                value == "1" || value == "true"
            } catch (e: Exception) {
                false
            }
        }

        return debugProps.count { it } >= 1
    }

    /**
     * Calculate threat level based on detected methods
     */
    private fun calculateThreatLevel(methods: List<DebugMethod>): ThreatLevel {
        return when {
            methods.contains(DebugMethod.JDWP_DEBUGGER) &&
            methods.contains(DebugMethod.NATIVE_DEBUGGER) -> ThreatLevel.CRITICAL

            methods.size >= 3 -> ThreatLevel.HIGH

            methods.contains(DebugMethod.JDWP_DEBUGGER) ||
            methods.contains(DebugMethod.NATIVE_DEBUGGER) -> ThreatLevel.HIGH

            methods.contains(DebugMethod.HOOKING_FRAMEWORK) -> ThreatLevel.MEDIUM

            methods.size >= 2 -> ThreatLevel.MEDIUM

            methods.isNotEmpty() -> ThreatLevel.LOW

            else -> ThreatLevel.NONE
        }
    }

    /**
     * Get recommended action based on threat level
     */
    fun getRecommendedAction(status: DebugStatus): String {
        return when (status.threatLevel) {
            ThreatLevel.NONE -> "Continue normal operation"
            ThreatLevel.LOW -> "Monitor and log"
            ThreatLevel.MEDIUM -> "Increase check frequency, alert user"
            ThreatLevel.HIGH -> "Lock vault, require re-authentication"
            ThreatLevel.CRITICAL -> "Self-destruct keys, purge vault"
        }
    }

    /**
     * Perform full security check and return summary
     */
    fun performFullCheck(): SecurityCheckResult {
        val debugStatus = checkDebugging()

        return SecurityCheckResult(
            debugStatus = debugStatus,
            isSecure = debugStatus.threatLevel <= ThreatLevel.LOW,
            shouldLockdown = debugStatus.threatLevel >= ThreatLevel.HIGH,
            shouldSelfDestruct = debugStatus.threatLevel == ThreatLevel.CRITICAL
        )
    }

    data class SecurityCheckResult(
        val debugStatus: DebugStatus,
        val isSecure: Boolean,
        val shouldLockdown: Boolean,
        val shouldSelfDestruct: Boolean
    )
}
