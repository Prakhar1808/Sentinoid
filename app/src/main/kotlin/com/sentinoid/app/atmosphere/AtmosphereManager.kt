package com.sentinoid.app.atmosphere

import android.content.Context
import com.sentinoid.app.hardware.HardwareAbstractionLayer
import com.sentinoid.app.ultra.UltraModule
import com.sentinoid.app.mobilea.MobileAModule

/**
 * Unified Atmosphere Manager
 * Coordinates between LITE, ULTRA, and MOBILE-A atmospheres
 * Automatically selects appropriate atmosphere based on hardware detection
 */
class AtmosphereManager(private val context: Context) {

    private val hal = HardwareAbstractionLayer(context)
    private var ultraModule: UltraModule? = null
    private var mobileAModule: MobileAModule? = null

    data class AtmosphereStatus(
        val currentAtmosphere: HardwareAbstractionLayer.Atmosphere,
        val atmosphereName: String,
        val features: List<String>,
        val isPremium: Boolean,
        val hardwareCapabilities: HardwareAbstractionLayer.HardwareCapabilities,
        val moduleStatus: ModuleStatus
    )

    data class ModuleStatus(
        val sevSnpActive: Boolean,
        val acceleratorAvailable: Boolean,
        val sideChannelJamming: Boolean,
        val rdnaObfuscation: Boolean,
        val gaitLock: Boolean
    )

    init {
        initializeModules()
    }

    private fun initializeModules() {
        val atmosphere = hal.detectAtmosphere()

        when (atmosphere) {
            HardwareAbstractionLayer.Atmosphere.ULTRA -> {
                ultraModule = UltraModule(context)
                ultraModule?.initializeSevSnp()
                ultraModule?.initializeSideChannelJamming()
            }
            HardwareAbstractionLayer.Atmosphere.MOBILE_A -> {
                mobileAModule = MobileAModule(context)
                if (mobileAModule?.verifyDeviceAuthenticity() == true) {
                    mobileAModule?.initializeRdnaObfuscation()
                    mobileAModule?.startGaitTracking()
                }
            }
            else -> {
                // LITE atmosphere - no special modules needed
            }
        }
    }

    fun getStatus(): AtmosphereStatus {
        val atmosphere = hal.detectAtmosphere()
        val capabilities = hal.getCapabilities()

        val features = when (atmosphere) {
            HardwareAbstractionLayer.Atmosphere.ULTRA -> listOf(
                "SEV-SNP Memory Encryption",
                "AMD Accelerator Offload",
                "Side-Channel Jamming",
                "NPU Task Delegation"
            )
            HardwareAbstractionLayer.Atmosphere.MOBILE_A -> listOf(
                "RDNA Frame-Buffer Obfuscation",
                "Gait-Based Proximity Lock",
                "NPU Behavioral Analysis",
                "Samsung Knox Integration",
                "Side-Channel Jamming"
            )
            else -> listOf(
                "BIP39 + Shamir Recovery",
                "Honeypot Detection",
                "Hardware Call Masking",
                "Watchdog Monitoring"
            )
        }

        return AtmosphereStatus(
            currentAtmosphere = atmosphere,
            atmosphereName = hal.getAtmosphereName(),
            features = features,
            isPremium = atmosphere != HardwareAbstractionLayer.Atmosphere.LITE,
            hardwareCapabilities = capabilities,
            moduleStatus = getModuleStatus()
        )
    }

    private fun getModuleStatus(): ModuleStatus {
        val ultra = ultraModule
        val mobileA = mobileAModule

        return ModuleStatus(
            sevSnpActive = ultra?.getSevStatus()?.enabled ?: false,
            acceleratorAvailable = ultra?.getAcceleratorStatus()?.available ?: false,
            sideChannelJamming = ultra?.getJammingStatus()?.active
                ?: mobileA?.detectProximity()?.threatLevel?.ordinal?.let { it > 0 } ?: false,
            rdnaObfuscation = mobileA?.getRdnaStatus()?.active ?: false,
            gaitLock = mobileA?.verifyGait()?.gaitMatched ?: false
        )
    }

    fun performOffload(data: ByteArray): ByteArray {
        val ultra = ultraModule
        if (ultra != null) {
            val result = ultra.offloadTask("crypto_verify", data)
            if (result is UltraModule.OffloadResult.Success) {
                return result.data
            }
        }
        return data
    }

    fun getCurrentAtmosphere(): HardwareAbstractionLayer.Atmosphere {
        return hal.detectAtmosphere()
    }

    fun cleanup() {
        mobileAModule?.stopGaitTracking()
    }
}
