package com.commvault.commlink.ui.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.commvault.commlink.data.bluetooth.HidDeviceManager
import com.commvault.commlink.data.bluetooth.HidService
import com.commvault.commlink.data.secure.SecureStorage
import com.commvault.commlink.domain.model.HidKeyCodes
import com.commvault.commlink.ui.theme.CommLinkTheme
import com.commvault.commlink.ui.theme.CommvaultNavy
import com.commvault.commlink.ui.theme.LocalPrimaryColor
import java.util.Locale
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.border
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.foundation.shape.CircleShape

class AssistantActivity : ComponentActivity() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening by mutableStateOf(false)
    private var recognizedText by mutableStateOf("Listening...")

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startListening()
        } else {
            Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("commlink_settings", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("voice_assistant_enabled", false)
        if (!isEnabled) {
            Toast.makeText(this, "Voice Assistant is disabled in settings.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Vibrate to indicate assistant has opened (power button long press feedback)
        vibratePhone()

        setContent {
            val primary = LocalPrimaryColor.current
            
            var offsetX by remember { mutableStateOf(0f) }
            var offsetY by remember { mutableStateOf(0f) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                offsetX += dragAmount.x
                                offsetY += dragAmount.y
                            }
                        }
                        .padding(16.dp)
                        .background(Color.White, RoundedCornerShape(32.dp))
                        .border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(32.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(if (isListening) primary else Color.Gray, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Mic", tint = Color.White)
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Text(
                        text = recognizedText,
                        color = CommvaultNavy,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun vibratePhone() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(100)
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        isListening = true
        recognizedText = "Listening for 'open', 'unlock', 'lock'..."
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
            }
            override fun onError(error: Int) {
                isListening = false
                recognizedText = "Error: $error"
                // Auto close after brief delay
                window.decorView.postDelayed({ finish() }, 1000)
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val primaryCommand = matches[0].lowercase()
                    recognizedText = "Heard: $primaryCommand"
                    processCommand(matches)
                } else {
                    finish()
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    private fun processCommand(matches: List<String>) {
        val lowerMatches = matches.map { it.lowercase() }
        val combinedText = lowerMatches.joinToString(" ")

        lifecycleScope.launch {
            if (lowerMatches.any { it.contains("open") || it.contains("direct unlock") || it.contains("direct open") || it.contains("direct") }) {
                recognizedText = "Directly Unlocking PC (Open)..."
                val intent = Intent(applicationContext, HidService::class.java).apply {
                    action = "UNLOCK_WINDOWS_DIRECT"
                }
                startService(intent)
            } else if (lowerMatches.any { it.contains("unlock") }) {
                recognizedText = "Unlocking PC..."
                val intent = Intent(applicationContext, HidService::class.java).apply {
                    action = "UNLOCK_WINDOWS_NORMAL"
                }
                startService(intent)
            } else if (lowerMatches.any { it.contains("lock") || it.contains("close") }) {
                recognizedText = "Locking PC..."
                val intent = Intent(applicationContext, HidService::class.java).apply {
                    action = "LOCK_WINDOWS"
                }
                startService(intent)
            } else if (lowerMatches.any { it.contains("sync") || it.contains("clipboard") || it.contains("paste") }) {
                recognizedText = "Syncing Clipboard to PC..."
                try {
                    val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clipData = clipboardManager.primaryClip
                    if (clipData != null && clipData.itemCount > 0) {
                        val text = clipData.getItemAt(0).text?.toString() ?: ""
                        if (text.isNotEmpty()) {
                            HidDeviceManager.getInstance(applicationContext).sendText(text)
                            Toast.makeText(this@AssistantActivity, "Clipboard sent to PC!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@AssistantActivity, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@AssistantActivity, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@AssistantActivity, "Sync error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                recognizedText = "Unknown command. Try 'open', 'unlock', 'lock', or 'sync'."
            }

            delay(1500)
            finish()
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        super.onDestroy()
    }
}
