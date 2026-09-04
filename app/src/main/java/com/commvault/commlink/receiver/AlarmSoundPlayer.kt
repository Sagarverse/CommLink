package com.commvault.commlink.receiver

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*

object AlarmSoundPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun playStandard(context: Context) {
        stop()
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxSystemVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxSystemVolume, 0)

            var uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (uri == null) {
                uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("AlarmSoundPlayer", "Error playing standard alarm", e)
        }
    }

    fun playEscalating(context: Context, maxVolume: Int) {
        stop()
        job = scope.launch {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxSystemVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val targetVolume = ((maxVolume / 10f) * maxSystemVolume).toInt().coerceIn(0, maxSystemVolume)

            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0)

            try {
                var uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                if (uri == null) {
                    uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                }
                
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, uri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                Log.e("AlarmSoundPlayer", "Error playing escalating alarm", e)
            }

            var currentVol = 0
            while (isActive && currentVol < targetVolume) {
                delay(3000)
                if (!isActive) break
                currentVol++
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, currentVol, 0)
            }
        }
    }

    fun playInterval(context: Context, intervalSecs: Int, repeatCount: Int) {
        stop()
        job = scope.launch {
            var count = 0
            while (isActive && count < repeatCount) {
                try {
                    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    val r = RingtoneManager.getRingtone(context, uri)
                    r.play()
                } catch (e: Exception) {
                    Log.e("AlarmSoundPlayer", "Error playing interval sound", e)
                }
                
                delay(intervalSecs * 1000L)
                count++
            }
        }
    }

    fun stop() {
        try {
            job?.cancel()
            job = null
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e("AlarmSoundPlayer", "Error stopping alarm sound", e)
        }
    }
}
