package com.commvault.commlink.data.audiobridge

import android.annotation.SuppressLint
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UsbPcmBridge(private val audioManager: AudioManager) {

    private var laptopToPhoneJob: Job? = null
    private var phoneToLaptopJob: Job? = null

    // Find the USB Audio Gadget devices
    private fun getUsbAudioDevices(): Pair<AudioDeviceInfo?, AudioDeviceInfo?> {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL)
        // Look for TYPE_USB_DEVICE or TYPE_USB_ACCESSORY or just any USB device
        val usbIn = devices.firstOrNull { it.isSource && it.type == AudioDeviceInfo.TYPE_USB_DEVICE }
        val usbOut = devices.firstOrNull { it.isSink && it.type == AudioDeviceInfo.TYPE_USB_DEVICE }
        return Pair(usbIn, usbOut)
    }

    @SuppressLint("MissingPermission")
    suspend fun startLaptopToPhoneRouting(
        scope: kotlinx.coroutines.CoroutineScope,
        injector: VirtualMicInjector,
        sampleRate: Int = 48000, 
        channelConfig: Int = AudioFormat.CHANNEL_IN_STEREO
    ): Boolean = withContext(Dispatchers.IO) {
        val (usbIn, _) = getUsbAudioDevices()
        if (usbIn == null) return@withContext false // No USB Audio input detected by Android

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT)
        val audioRecord = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .build())
            .setBufferSizeInBytes(minBufferSize * 2)
            .build()
            
        // Explicitly route AudioRecord to the USB Gadget Input
        val routed = audioRecord.setPreferredDevice(usbIn)
        if (!routed) {
            audioRecord.release()
            return@withContext false
        }

        injector.prepare()
        injector.start()
        
        audioRecord.startRecording()
        val buffer = ShortArray(minBufferSize)
        
        laptopToPhoneJob = scope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        injector.write(buffer, read)
                    }
                }
            } finally {
                audioRecord.stop()
                audioRecord.release()
            }
        }
        
        return@withContext true
    }

    suspend fun stopLaptopToPhoneRouting() {
        laptopToPhoneJob?.cancel()
        laptopToPhoneJob = null
    }

    @SuppressLint("MissingPermission")
    suspend fun startPhoneToLaptopRouting(
        scope: kotlinx.coroutines.CoroutineScope,
        sampleRate: Int = 48000, 
        channelConfigIn: Int = AudioFormat.CHANNEL_IN_STEREO,
        channelConfigOut: Int = AudioFormat.CHANNEL_OUT_STEREO
    ): Boolean = withContext(Dispatchers.IO) {
        val (_, usbOut) = getUsbAudioDevices()
        if (usbOut == null) return@withContext false // No USB Audio output detected by Android

        val minRecordSize = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, AudioFormat.ENCODING_PCM_16BIT)
        val minTrackSize = AudioTrack.getMinBufferSize(sampleRate, channelConfigOut, AudioFormat.ENCODING_PCM_16BIT)
        val bufferSize = maxOf(minRecordSize, minTrackSize) * 2

        // Read from phone's built-in microphone
        val audioRecord = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfigIn)
                .build())
            .setBufferSizeInBytes(bufferSize)
            .build()

        // Write to USB Gadget OUT
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfigOut)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            
        val routed = audioTrack.setPreferredDevice(usbOut)
        if (!routed) {
            audioRecord.release()
            audioTrack.release()
            return@withContext false
        }

        audioRecord.startRecording()
        audioTrack.play()
        
        val buffer = ShortArray(bufferSize / 2)
        
        phoneToLaptopJob = scope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        audioTrack.write(buffer, 0, read)
                    }
                }
            } finally {
                audioRecord.stop()
                audioRecord.release()
                audioTrack.stop()
                audioTrack.release()
            }
        }
        
        return@withContext true
    }

    suspend fun stopPhoneToLaptopRouting() {
        phoneToLaptopJob?.cancel()
        phoneToLaptopJob = null
    }
}
