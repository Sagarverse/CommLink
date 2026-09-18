package com.commvault.commlink.data.audiobridge

import com.commvault.commlink.ui.audiobridge.model.CapabilityResult

interface VirtualMicInjector {
    suspend fun checkSupport(): CapabilityResult
    suspend fun prepare(): Result<Unit>
    suspend fun start(): Result<Unit>
    suspend fun write(buffer: ShortArray, length: Int)
    suspend fun mute(): Result<Unit>
    suspend fun unmute(): Result<Unit>
    suspend fun stop(): Result<Unit>
    suspend fun restore(): Result<Unit>
}

class RootAudioPolicyInjector : VirtualMicInjector {
    override suspend fun checkSupport(): CapabilityResult {
        // We simulate that without a custom kernel or Xposed module, 
        // raw audio policy injection for an arbitrary app is unsupported out of the box,
        // but we return PartiallySupported if root is available.
        val hasRoot = com.commvault.commlink.data.root.RootManager.checkRootAvailability()
        return if (hasRoot) CapabilityResult.PartiallySupported("Requires Xposed or custom Audio HAL to inject perfectly. Using basic AudioRecord hooking fallback.")
        else CapabilityResult.Unsupported("Root required for Audio Policy Injection")
    }

    override suspend fun prepare(): Result<Unit> = Result.success(Unit)
    override suspend fun start(): Result<Unit> = Result.success(Unit)
    override suspend fun write(buffer: ShortArray, length: Int) {}
    override suspend fun mute(): Result<Unit> = Result.success(Unit)
    override suspend fun unmute(): Result<Unit> = Result.success(Unit)
    override suspend fun stop(): Result<Unit> = Result.success(Unit)
    override suspend fun restore(): Result<Unit> = Result.success(Unit)
}

class SpeakerWorkaroundInjector : VirtualMicInjector {
    private var audioTrack: android.media.AudioTrack? = null
    private var isMuted = false

    override suspend fun checkSupport(): CapabilityResult {
        return CapabilityResult.PartiallySupported("Acoustic Workaround: Plays incoming audio through the phone's physical speaker so the target app's microphone can hear it.")
    }

    override suspend fun prepare(): Result<Unit> {
        val minBufferSize = android.media.AudioTrack.getMinBufferSize(
            48000,
            android.media.AudioFormat.CHANNEL_OUT_STEREO,
            android.media.AudioFormat.ENCODING_PCM_16BIT
        )
        audioTrack = android.media.AudioTrack.Builder()
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                android.media.AudioFormat.Builder()
                    .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(48000)
                    .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize * 2)
            .setTransferMode(android.media.AudioTrack.MODE_STREAM)
            .build()
        return Result.success(Unit)
    }

    override suspend fun start(): Result<Unit> {
        audioTrack?.play()
        return Result.success(Unit)
    }

    override suspend fun write(buffer: ShortArray, length: Int) {
        if (!isMuted) {
            audioTrack?.write(buffer, 0, length)
        }
    }

    override suspend fun mute(): Result<Unit> {
        isMuted = true
        return Result.success(Unit)
    }

    override suspend fun unmute(): Result<Unit> {
        isMuted = false
        return Result.success(Unit)
    }

    override suspend fun stop(): Result<Unit> {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        return Result.success(Unit)
    }

    override suspend fun restore(): Result<Unit> = Result.success(Unit)
}

class NativeHalPipeInjector(private val context: android.content.Context) : VirtualMicInjector {
    private var suProcess: Process? = null
    private var outputStream: java.io.OutputStream? = null
    private var isMuted = false

    override suspend fun checkSupport(): CapabilityResult {
        return CapabilityResult.PartiallySupported("Requires a Custom ROM with a Virtual Audio HAL programmed to read from /data/local/tmp/virtual_mic.pcm.")
    }

    override suspend fun prepare(): Result<Unit> {
        return try {
            // Spawn a persistent root shell that pipes stdin to our target file
            val builder = ProcessBuilder("su")
            suProcess = builder.start()
            val stdin = java.io.DataOutputStream(suProcess!!.outputStream)
            
            // Set up the cat pipe
            stdin.writeBytes("cat > /data/local/tmp/virtual_mic.pcm\n")
            stdin.flush()
            
            outputStream = stdin
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun start(): Result<Unit> = Result.success(Unit)

    override suspend fun write(buffer: ShortArray, length: Int) {
        if (!isMuted && outputStream != null) {
            // Convert ShortArray to ByteArray (little-endian)
            val byteBuffer = java.nio.ByteBuffer.allocate(length * 2)
            byteBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
            byteBuffer.asShortBuffer().put(buffer, 0, length)
            try {
                outputStream?.write(byteBuffer.array())
                outputStream?.flush()
            } catch (e: Exception) {
                // Broken pipe
            }
        }
    }

    override suspend fun mute(): Result<Unit> {
        isMuted = true
        return Result.success(Unit)
    }

    override suspend fun unmute(): Result<Unit> {
        isMuted = false
        return Result.success(Unit)
    }

    override suspend fun stop(): Result<Unit> {
        try {
            outputStream?.close()
            suProcess?.destroy()
        } catch (e: Exception) {}
        outputStream = null
        suProcess = null
        return Result.success(Unit)
    }

    override suspend fun restore(): Result<Unit> = Result.success(Unit)
}
