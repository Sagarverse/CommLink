package com.commvault.commlink.ui.components

import android.content.Context
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.commvault.commlink.ui.theme.CommvaultNavy
import com.commvault.commlink.ui.theme.LocalPrimaryColor
import com.commvault.commlink.ui.theme.LightBg

@Composable
fun BiometricLockOverlay(
    onUnlock: () -> Unit
) {
    val context = LocalContext.current
    var promptShowing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showBiometricPrompt(context, onUnlock, onFailed = {
            promptShowing = false
        })
        promptShowing = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = "Fingerprint",
                tint = LocalPrimaryColor.current,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "App Locked",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = CommvaultNavy
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Please authenticate to access CommLink",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = {
                    showBiometricPrompt(context, onUnlock, onFailed = {})
                },
                colors = ButtonDefaults.buttonColors(containerColor = CommvaultNavy)
            ) {
                Text("Unlock")
            }
        }
    }
}

private fun showBiometricPrompt(
    context: Context,
    onSuccess: () -> Unit,
    onFailed: () -> Unit
) {
    val activity = context as? FragmentActivity
    if (activity == null) {
        Toast.makeText(context, "Authentication unavailable", Toast.LENGTH_SHORT).show()
        onSuccess()
        return
    }

    val biometricManager = BiometricManager.from(context)
    when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
        BiometricManager.BIOMETRIC_SUCCESS -> {
            // Can authenticate
        }
        else -> {
            // If we can't authenticate, just let them in (or we could show an error)
            onSuccess()
            return
        }
    }

    val executor = ContextCompat.getMainExecutor(context)
    val biometricPrompt = BiometricPrompt(activity, executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Toast.makeText(context, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                onFailed()
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Toast.makeText(context, "Authentication failed", Toast.LENGTH_SHORT).show()
                onFailed()
            }
        })

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("CommLink Security")
        .setSubtitle("Log in using your biometric credential")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        .build()

    biometricPrompt.authenticate(promptInfo)
}
