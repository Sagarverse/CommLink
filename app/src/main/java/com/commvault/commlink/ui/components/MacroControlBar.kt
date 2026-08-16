package com.commvault.commlink.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.commvault.commlink.ui.CommLinkViewModel
import com.commvault.commlink.ui.theme.ErrorRed
import com.commvault.commlink.ui.theme.LightSurfaceAlt
import com.commvault.commlink.ui.theme.TextPrimary

@Composable
fun MacroControlBar(viewModel: CommLinkViewModel) {
    val isMacroRunning by viewModel.isMacroRunning.collectAsState()
    val isMacroPaused by viewModel.isMacroPaused.collectAsState()

    if (isMacroRunning) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { 
                    if (isMacroPaused) viewModel.resumeMacro() else viewModel.pauseMacro()
                },
                modifier = Modifier
                    .padding(end = 4.dp)
                    .background(LightSurfaceAlt, CircleShape)
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = if (isMacroPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (isMacroPaused) "Resume" else "Pause",
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = { viewModel.stopMacro() },
                modifier = Modifier
                    .padding(end = 8.dp)
                    .background(ErrorRed.copy(alpha = 0.1f), CircleShape)
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop Macro",
                    tint = ErrorRed,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
