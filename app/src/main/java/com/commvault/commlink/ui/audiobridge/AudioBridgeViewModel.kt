package com.commvault.commlink.ui.audiobridge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commvault.commlink.ui.audiobridge.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AudioBridgeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AudioBridgeUiState())
    val uiState: StateFlow<AudioBridgeUiState> = _uiState.asStateFlow()

    fun scanCapabilities(context: android.content.Context) {
        _uiState.update { it.copy(
            rootState = RootState.Checking,
            transportCapability = TransportCapability(
                usb = CapabilityResult.Checking,
                bluetooth = CapabilityResult.Checking
            )
        )}
        
        viewModelScope.launch {
            val hasRoot = com.commvault.commlink.data.root.RootManager.checkRootAvailability()
            val rootState = if (hasRoot) RootState.Available else RootState.Denied("Root access is required for USB Gadget Audio.")
            
            _uiState.update { it.copy(rootState = rootState) }
            
            val usbDetector = com.commvault.commlink.data.audiobridge.UsbCapabilityDetector()
            val btDetector = com.commvault.commlink.data.audiobridge.BluetoothCapabilityDetector(context)
            
            val usbResult = usbDetector.checkSupport()
            val btResult = btDetector.checkSupport()
            
            val diagnostics = "--- USB Diagnostics ---\n${usbDetector.getDiagnostics()}\n--- BT Diagnostics ---\n${btDetector.getDiagnostics()}"
            
            _uiState.update { it.copy(
                transportCapability = TransportCapability(
                    usb = usbResult,
                    bluetooth = btResult,
                    diagnostics = diagnostics
                )
            )}
        }
    }

    fun selectTransport(transport: Transport) {
        _uiState.update { it.copy(selectedTransport = transport) }
    }

    fun startLaptopToPhone(context: android.content.Context) {
        _uiState.update { it.copy(laptopToPhoneState = RouteState.Starting) }
        viewModelScope.launch {
            val controller = com.commvault.commlink.data.audiobridge.UsbGadgetController()
            val success = controller.configureAudioGadget()
            if (success) {
                // Start Foreground service
                val intent = android.content.Intent(context, com.commvault.commlink.data.audiobridge.UsbBridgeService::class.java)
                context.startForegroundService(intent)
                
                // Start PCM Routing
                val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                val bridge = com.commvault.commlink.data.audiobridge.UsbPcmBridge(audioManager)
                
                val injector = when (_uiState.value.selectedVirtualMicMethod) {
                    VirtualMicMethod.ACOUSTIC -> com.commvault.commlink.data.audiobridge.SpeakerWorkaroundInjector()
                    VirtualMicMethod.NATIVE_HAL -> com.commvault.commlink.data.audiobridge.NativeHalPipeInjector(context)
                }
                
                val pcmSuccess = bridge.startLaptopToPhoneRouting(this, injector)
                if (pcmSuccess) {
                    _uiState.update { it.copy(laptopToPhoneState = RouteState.Active) }
                } else {
                    _uiState.update { it.copy(laptopToPhoneState = RouteState.Error("Failed to route USB to Speaker")) }
                }
            } else {
                _uiState.update { it.copy(laptopToPhoneState = RouteState.Error("Failed to configure USB Gadget")) }
            }
        }
    }

    fun stopLaptopToPhone(context: android.content.Context) {
        viewModelScope.launch {
            val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            val bridge = com.commvault.commlink.data.audiobridge.UsbPcmBridge(audioManager)
            bridge.stopLaptopToPhoneRouting()
            
            val controller = com.commvault.commlink.data.audiobridge.UsbGadgetController()
            controller.restoreOriginalGadget()
            
            val intent = android.content.Intent(context, com.commvault.commlink.data.audiobridge.UsbBridgeService::class.java)
            context.stopService(intent)
            _uiState.update { it.copy(laptopToPhoneState = RouteState.Stopped) }
        }
    }

    fun startPhoneToLaptop(context: android.content.Context) {
        _uiState.update { it.copy(phoneToLaptopState = RouteState.Starting) }
        viewModelScope.launch {
            val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            val bridge = com.commvault.commlink.data.audiobridge.UsbPcmBridge(audioManager)
            val success = bridge.startPhoneToLaptopRouting(this)
            
            if (success) {
                _uiState.update { it.copy(phoneToLaptopState = RouteState.Active) }
            } else {
                _uiState.update { it.copy(phoneToLaptopState = RouteState.Error("Failed to route MIC to USB")) }
            }
        }
    }

    fun stopPhoneToLaptop(context: android.content.Context) {
        viewModelScope.launch {
            val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            val bridge = com.commvault.commlink.data.audiobridge.UsbPcmBridge(audioManager)
            bridge.stopPhoneToLaptopRouting()
            _uiState.update { it.copy(phoneToLaptopState = RouteState.Stopped) }
        }
    }

    fun setLaptopToPhoneMute(muted: Boolean) {
        _uiState.update { it.copy(laptopToPhoneMuted = muted) }
    }

    fun setPhoneToLaptopMute(muted: Boolean) {
        _uiState.update { it.copy(phoneToLaptopMuted = muted) }
    }

    fun startBothRoutes(context: android.content.Context) {
        startLaptopToPhone(context)
        startPhoneToLaptop(context)
    }

    fun stopAllRoutes(context: android.content.Context) {
        stopLaptopToPhone(context)
        stopPhoneToLaptop(context)
    }

    fun emergencyCutOff(context: android.content.Context) {
        viewModelScope.launch {
            val controller = com.commvault.commlink.data.audiobridge.UsbGadgetController()
            controller.restoreOriginalGadget()
            val intent = android.content.Intent(context, com.commvault.commlink.data.audiobridge.UsbBridgeService::class.java)
            context.stopService(intent)
            
            _uiState.update { it.copy(
                laptopToPhoneState = RouteState.Stopped,
                phoneToLaptopState = RouteState.Stopped,
                emergencyStopped = true,
                recoverableError = BridgeError.GenericError(
                    "Emergency Cut-off Triggered",
                    "All routes have been stopped and audio configuration has been safely restored."
                )
            )}
        }
    }

    fun restoreNormalAudio() {
        _uiState.update { it.copy(emergencyStopped = false, recoverableError = null) }
    }
    
    fun dismissError() {
        _uiState.update { it.copy(recoverableError = null) }
    }

    fun selectTargetPackage(packageName: String) {
        _uiState.update { it.copy(selectedTargetPackage = packageName) }
    }

    fun selectCaptureSource(source: CaptureSource) {
        _uiState.update { it.copy(selectedCaptureSource = source) }
    }

    fun selectVirtualMicMethod(method: VirtualMicMethod) {
        _uiState.update { it.copy(selectedVirtualMicMethod = method) }
    }

    fun updateAudioConfiguration(config: AudioConfiguration) {
        _uiState.update { it.copy(audioConfiguration = config) }
    }
}
