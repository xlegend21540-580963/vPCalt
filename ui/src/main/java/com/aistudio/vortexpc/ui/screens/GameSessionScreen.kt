package com.aistudio.vortexpc.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.aistudio.vortexpc.core.model.MouseMode
import com.aistudio.vortexpc.core.model.TelemetrySnapshot
import com.aistudio.vortexpc.graphics.X11DisplayState
import com.aistudio.vortexpc.input.InputProfile
import com.aistudio.vortexpc.runtime.ActiveSessionInfo
import com.aistudio.vortexpc.ui.components.PerformanceHudOverlay
import com.aistudio.vortexpc.ui.components.VirtualControlsOverlay
import com.aistudio.vortexpc.ui.components.X11ViewportCanvas

@Composable
fun GameSessionScreen(
  sessionInfo: ActiveSessionInfo,
  x11State: X11DisplayState,
  telemetry: TelemetrySnapshot,
  showHud: Boolean,
  activeProfile: InputProfile,
  allProfiles: List<InputProfile>,
  onFramePresented: () -> Unit,
  onTouchDrag: (Float, Float, Float, Float) -> Unit,
  onTouchTap: (Float, Float, Boolean) -> Unit,
  onVirtualButton: (String, Boolean) -> Unit,
  onVirtualStick: (Boolean, Float, Float) -> Unit,
  onInjectKeyText: (String) -> Unit,
  onUpdateScaleAndFps: (Float, Int) -> Unit,
  onToggleHud: (Boolean) -> Unit,
  onSwitchMouseMode: (MouseMode) -> Unit,
  onSelectProfile: (InputProfile) -> Unit,
  onExportLogs: () -> Unit,
  onExitSession: () -> Unit,
) {
  var showQuickMenu by remember { mutableStateOf(false) }
  var showVirtualControls by remember { mutableStateOf(true) }
  var showMiniKeyboard by remember { mutableStateOf(false) }

  BackHandler {
    if (showQuickMenu) {
      showQuickMenu = false
    } else {
      onExitSession()
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black),
  ) {
    X11ViewportCanvas(
      displayState = x11State,
      onFramePresented = onFramePresented,
      onTouchDrag = onTouchDrag,
      onTouchTap = onTouchTap,
    )

    if (showVirtualControls) {
      VirtualControlsOverlay(
        elements = activeProfile.elements,
        editMode = false,
        onButtonStateChange = onVirtualButton,
        onStickMove = onVirtualStick,
      )
    }

    if (showHud) {
      PerformanceHudOverlay(
        telemetry = telemetry,
        fpsCapLabel = if (x11State.fpsLimit <= 0) "UNCAPPED" else "${x11State.fpsLimit} CAP",
        modifier = Modifier
          .align(Alignment.TopStart)
          .safeDrawingPadding()
          .padding(10.dp),
      )
    }

    // Top-right Quick Menu & Keyboard buttons
    Row(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .safeDrawingPadding()
        .padding(10.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xCC0A0F18),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      ) {
        Row {
          IconButton(onClick = { showMiniKeyboard = !showMiniKeyboard }) {
            Icon(Icons.Rounded.Keyboard, contentDescription = "Toggle quick keyboard", tint = Color.White)
          }
          IconButton(
            onClick = { showQuickMenu = !showQuickMenu },
            modifier = Modifier.testTag("session_quick_menu_button"),
          ) {
            Icon(Icons.Rounded.Tune, contentDescription = "Open quick menu", tint = MaterialTheme.colorScheme.primary)
          }
        }
      }
    }

    // Optional mini keyboard bar for testing notepad.exe / terminal input
    AnimatedVisibility(
      visible = showMiniKeyboard,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .safeDrawingPadding()
        .padding(bottom = 12.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xE60B0F17),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
      ) {
        Row(
          modifier = Modifier.padding(8.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          listOf("V", "O", "R", "T", "E", "X", " ", "ENTER", "BACKSPACE").forEach { key ->
            OutlinedButton(onClick = { onInjectKeyText(key) }) {
              Text(if (key == " ") "SPC" else if (key == "BACKSPACE") "DEL" else key)
            }
          }
        }
      }
    }

    // Slide-out Quick Menu Overlay
    AnimatedVisibility(
      visible = showQuickMenu,
      modifier = Modifier.align(Alignment.CenterEnd),
    ) {
      Surface(
        modifier = Modifier
          .width(320.dp)
          .fillMaxHeight()
          .safeDrawingPadding()
          .padding(8.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xF20B0F18),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(sessionInfo.targetName.ifBlank { "Active Session" }, style = MaterialTheme.typography.titleMedium)
          Text(sessionInfo.executableCommand, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Performance HUD", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = showHud, onCheckedChange = onToggleHud)
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("On-Screen Controls", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = showVirtualControls, onCheckedChange = { showVirtualControls = it })
          }

          Text("FPS Limiter", style = MaterialTheme.typography.titleSmall)
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(30, 40, 60, 0).forEach { cap ->
              FilterChip(
                selected = x11State.fpsLimit == cap,
                onClick = { onUpdateScaleAndFps(x11State.renderScale, cap) },
                label = { Text(if (cap == 0) "Max" else "$cap") },
              )
            }
          }

          Text("Render Scale: ${(x11State.renderScale * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
          Slider(
            value = x11State.renderScale,
            onValueChange = { s -> onUpdateScaleAndFps(s, x11State.fpsLimit) },
            valueRange = 0.5f..1.0f,
          )

          Text("Mouse Emulation", style = MaterialTheme.typography.titleSmall)
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MouseMode.entries.forEach { mode ->
              FilterChip(
                selected = activeProfile.mouseMode == mode,
                onClick = { onSwitchMouseMode(mode) },
                label = { Text(mode.name.replace('_', ' ')) },
              )
            }
          }

          Text("Input Profile", style = MaterialTheme.typography.titleSmall)
          allProfiles.forEach { prof ->
            FilterChip(
              selected = prof.id == activeProfile.id,
              onClick = { onSelectProfile(prof) },
              label = { Text(prof.name) },
            )
          }

          Spacer(Modifier.height(6.dp))
          OutlinedButton(
            onClick = onExportLogs,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Icon(Icons.Rounded.FileDownload, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Export Diagnostic Logs")
          }
          Button(
            onClick = onExitSession,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("session_exit_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          ) {
            Icon(Icons.AutoMirrored.Rounded.ExitToApp, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Exit Container Session")
          }
        }
      }
    }
  }
}
