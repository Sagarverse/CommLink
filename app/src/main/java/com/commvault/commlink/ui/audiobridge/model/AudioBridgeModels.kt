package com.commvault.commlink.ui.audiobridge.model

import kotlin.time.Duration

enum class Transport {
    USB, BLUETOOTH
}

sealed interface RootState {
    object Unknown : RootState
    object Checking : RootState
    object Available : RootState
    data class Denied(val reason: String) : RootState
    data class Unavailable(val reason: String) : RootState
}

sealed interface CapabilityResult {
    object Unchecked : CapabilityResult
    object Checking : CapabilityResult
    object Supported : CapabilityResult
    data class PartiallySupported(val details: String) : CapabilityResult
    data class Unsupported(val reason: String) : CapabilityResult
}

data class TransportCapability(
    val usb: CapabilityResult = CapabilityResult.Unchecked,
    val bluetooth: CapabilityResult = CapabilityResult.Unchecked,
    val diagnostics: String = ""
)

sealed interface RouteState {
    object Stopped : RouteState
    object Starting : RouteState
    object Active : RouteState
    data class Error(val message: String) : RouteState
}

sealed interface ConnectionState {
    object Disconnected : ConnectionState
    object Connecting : ConnectionState
    object Connected : ConnectionState
    data class Error(val message: String) : ConnectionState
}

sealed interface BridgeError {
    val title: String
    val message: String
    val technicalDetails: String?

    data class RootDenied(override val technicalDetails: String? = null) : BridgeError {
        override val title = "Root Access Denied"
        override val message = "Audio Bridge requires root access to configure USB Gadget mode or inject audio."
    }
    data class CapabilityMissing(override val title: String, override val message: String, override val technicalDetails: String? = null) : BridgeError
    data class GenericError(override val title: String, override val message: String, override val technicalDetails: String? = null) : BridgeError
}

enum class CaptureSource(val label: String) {
    SYSTEM_PLAYBACK("System Playback"),
    APP_PLAYBACK("Selected App Playback"),
    DEVICE_MIC("Device Microphone"),
    MIXED("Mixed Playback & Mic")
}

enum class VirtualMicMethod(val label: String, val description: String) {
    ACOUSTIC("Acoustic Workaround", "Plays audio via speaker. Works universally on any ROM."),
    NATIVE_HAL("Native Virtual Device", "Writes to a system pipe. Requires a Custom ROM with a Virtual Audio HAL.")
}

data class AudioConfiguration(
    val sampleRate: Int = 48000,
    val stereo: Boolean = true,
    val uacVersion: Int = 2,
    val bufferSize: Int = 1024,
    val playbackGain: Float = 1.0f,
    val micGain: Float = 1.0f,
    val monitoringVolume: Float = 0.0f,
    val keepAdb: Boolean = true,
    val restoreOnExit: Boolean = true,
    val debugLogging: Boolean = false
)

data class AudioBridgeUiState(
    val rootState: RootState = RootState.Unknown,
    val selectedTransport: Transport = Transport.USB,
    val transportCapability: TransportCapability = TransportCapability(),
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    
    val laptopToPhoneState: RouteState = RouteState.Stopped,
    val phoneToLaptopState: RouteState = RouteState.Stopped,
    
    val laptopToPhoneMuted: Boolean = false,
    val phoneToLaptopMuted: Boolean = false,
    
    val incomingLevel: Float = 0.0f,
    val outgoingLevel: Float = 0.0f,
    
    val selectedTargetPackage: String? = null,
    val selectedCaptureSource: CaptureSource? = null,
    val selectedVirtualMicMethod: VirtualMicMethod = VirtualMicMethod.ACOUSTIC,
    
    val audioConfiguration: AudioConfiguration = AudioConfiguration(),
    
    val activeDurationMs: Long = 0L,
    val recoverableError: BridgeError? = null,
    val emergencyStopped: Boolean = false
)
