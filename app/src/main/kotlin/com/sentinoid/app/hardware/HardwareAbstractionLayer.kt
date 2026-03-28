package com.sentinoid.app.hardware

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Hardware Abstraction Layer for Sentinoid
 * Auto-detects and selects the appropriate atmosphere:
 * - LITE: Universal Android (fallback)
 * - ULTRA: AMD PC with SEV-SNP
 * - MOBILE-A: Samsung S26 with AMD accelerator
 */
class HardwareAbstractionLayer(private val context: Context) {

    enum class Atmosphere {
        LITE,      // Universal Android/ARM
        ULTRA,     // AMD PC (High-performance)
        MOBILE_A   // Samsung S26 + AMD accelerator
    }

    data class HardwareCapabilities(
        val atmosphere: Atmosphere,
        val hasNpu: Boolean,
        val hasAmdAccelerator: Boolean,
        val hasSevSnp: Boolean,
        val hasRdna: Boolean,
        val isSamsungS26: Boolean,
        val supportsSideChannelJamming: Boolean,
        val supportsGaitLock: Boolean
    )

    companion object {
        // AMD vendor ID
        private const val AMD_VENDOR_ID = "AuthenticAMD"
        private const val AMD_VENDOR_ID_ALT = "Advanced Micro Devices"

        // Samsung S26 identifiers
        private const val SAMSUNG_S26_MODELS = "SM-S931,SM-S936,SM-S938"
        private const val SAMSUNG_MANUFACTURER = "samsung"

        // Hardware detection paths
        private const val PROC_CPUINFO = "/proc/cpuinfo"
        private const val SYS_CLASS_DRM = "/sys/class/drm"
        private const val AMD_SEV_PATH = "/sys/sev"
    }

    fun detectAtmosphere(): Atmosphere {
        return when {
            detectMobileA() -> Atmosphere.MOBILE_A
            detectUltra() -> Atmosphere.ULTRA
            else -> Atmosphere.LITE
        }
    }

    fun getCapabilities(): HardwareCapabilities {
        val atmosphere = detectAtmosphere()

        return HardwareCapabilities(
            atmosphere = atmosphere,
            hasNpu = detectNpuSupport(),
            hasAmdAccelerator = hasAmdAccelerator(),
            hasSevSnp = detectSevSnpSupport(),
            hasRdna = detectRdnaSupport(),
            isSamsungS26 = isSamsungS26(),
            supportsSideChannelJamming = atmosphere == Atmosphere.ULTRA || atmosphere == Atmosphere.MOBILE_A,
            supportsGaitLock = atmosphere == Atmosphere.MOBILE_A
        )
    }

    private fun detectUltra(): Boolean {
        // ULTRA: AMD PC with SEV-SNP support
        return hasAmdProcessor() &&
               !isAndroidDevice() &&
               detectSevSnpSupport()
    }

    private fun detectMobileA(): Boolean {
        // MOBILE-A: Samsung S26 with AMD accelerator
        return isSamsungS26() &&
               hasAmdAccelerator()
    }

    private fun hasAmdProcessor(): Boolean {
        return try {
            val cpuInfo = File(PROC_CPUINFO).readText()
            cpuInfo.contains(AMD_VENDOR_ID, ignoreCase = true) ||
            cpuInfo.contains(AMD_VENDOR_ID_ALT, ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    private fun isAndroidDevice(): Boolean {
        return try {
            Class.forName("android.app.Activity")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    private fun isSamsungDevice(): Boolean {
        return Build.MANUFACTURER.equals(SAMSUNG_MANUFACTURER, ignoreCase = true)
    }

    private fun isSamsungS26(): Boolean {
        if (!isSamsungDevice()) return false

        val model = Build.MODEL ?: return false
        val s26Models = SAMSUNG_S26_MODELS.split(",")

        return s26Models.any { model.contains(it, ignoreCase = true) }
    }

    private fun hasAmdAccelerator(): Boolean {
        // Check for AMD mobile accelerator (RDNA-based)
        return try {
            // Check GPU info
            val drmPath = File(SYS_CLASS_DRM)
            if (drmPath.exists()) {
                drmPath.listFiles()?.any { file ->
                    val vendor = File(file, "vendor").readTextOrNull()
                    vendor?.contains("AMD", ignoreCase = true) == true ||
                    vendor?.contains("1002", ignoreCase = true) == true // AMD PCI vendor ID
                } ?: false
            } else {
                // Check for AMD mobile GPU in properties
                Build.HARDWARE.contains("qcom", ignoreCase = true) &&
                hasAmdProcessor()
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun detectNpuSupport(): Boolean {
        // Check for NPU availability using Neural Networks API
        return try {
            context.getSystemService("neuralnetworks") != null
        } catch (e: Exception) {
            false
        }
    }

    private fun detectSevSnpSupport(): Boolean {
        // Check for AMD SEV-SNP support
        return try {
            val sevPath = File(AMD_SEV_PATH)
            sevPath.exists() && sevPath.isDirectory
        } catch (e: Exception) {
            false
        }
    }

    private fun detectRdnaSupport(): Boolean {
        // Check for RDNA GPU architecture
        return try {
            val gpuInfo = File("/sys/class/kgsl/kgsl-3d0/gpu_model").readTextOrNull()
                ?: File("/sys/class/misc/mali0/device/utgard_clock").readTextOrNull()
                ?: ""

            gpuInfo.contains("RDNA", ignoreCase = true) ||
            gpuInfo.contains("Adreno", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    private fun File.readTextOrNull(): String? {
        return try {
            if (exists() && canRead()) readText() else null
        } catch (e: Exception) {
            null
        }
    }

    fun getAtmosphereName(): String {
        return when (detectAtmosphere()) {
            Atmosphere.LITE -> "LITE (Universal Android)"
            Atmosphere.ULTRA -> "ULTRA (AMD PC)"
            Atmosphere.MOBILE_A -> "MOBILE-A (Samsung S26 + AMD)"
        }
    }

    fun isPremiumHardware(): Boolean {
        val cap = getCapabilities()
        return cap.atmosphere != Atmosphere.LITE
    }
}
