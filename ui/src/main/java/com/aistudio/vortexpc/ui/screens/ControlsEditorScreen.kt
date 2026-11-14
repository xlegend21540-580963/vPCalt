package com.aistudio.vortexpc.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.aistudio.vortexpc.input.ControlElement
import com.aistudio.vortexpc.input.ControlElementType
import com.aistudio.vortexpc.input.InputProfile
import com.aistudio.vortexpc.input.LiveInputDebugState
import com.aistudio.vortexpc.ui.components.VirtualControlsOverlay
import com.aistudio.vortexpc.ui.components.VortexConsoleCard
import com.aistudio.vortexpc.ui.components.VortexTagPill

@Composable
fun ControlsEditorScreen(
  profiles: List<InputProfile>,
  inputDebug: LiveInputDebugState,
  onSaveProfile: (InputProfile) -> Unit,
  onAddElement: (InputProfile, ControlElementType) -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedProfileId by remember {
    mutableStateOf(profiles.firstOrNull()?.id ?: "")
  }
  val activeProfile = profiles.firstOrNull { it.id == selectedProfileId } ?: profiles.first()
  var workingProfile by remember(activeProfile) { mutableStateOf(activeProfile) }
  var selectedElementId by remember(activeProfile.id) {
    mutableStateOf(workingProfile.elements.firstOrNull()?.id)
  }

  val selectedElement: ControlElement? = workingProfile.elements.firstOrNull { it.id == selectedElementId }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column {
        Text("Visual Controls & Gyro", style = MaterialTheme.typography.headlineMedium)
        Text(
          text = "Drag elements on canvas • Configure mouse & gyro aim",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Button(
        onClick = { onSaveProfile(workingProfile) },
        modifier = Modifier.testTag("save_controls_profile_button"),
      ) {
        Icon(Icons.Rounded.Save, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text("Save")
      }
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      items(profiles, key = { it.id }) { prof ->
        FilterChip(
          selected = prof.id == workingProfile.id,
          onClick = {
            selectedProfileId = prof.id
            workingProfile = prof
            selectedElementId = prof.elements.firstOrNull()?.id
          },
          label = { Text(prof.name) },
        )
      }
    }

    // Interactive Drag-and-Drop Visual Canvas
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .height(280.dp)
        .testTag("controls_editor_canvas"),
      shape = RoundedCornerShape(18.dp),
      color = Color(0xFF05080F),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        Text(
          text = "TOUCH OVERLAY CANVAS (DRAG CONTROLS)",
          style = MaterialTheme.typography.labelSmall,
          color = Color(0xFF566885),
          modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 8.dp),
        )
        VirtualControlsOverlay(
          elements = workingProfile.elements,
          editMode = true,
          selectedElementId = selectedElementId,
          onSelectElement = { selectedElementId = it.id },
          onMoveElement = { id, nx, ny ->
            workingProfile = workingProfile.copy(
              elements = workingProfile.elements.map { el ->
                if (el.id == id) el.copy(xFraction = nx, yFraction = ny) else el
              }
            )
          },
        )
      }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(onClick = { onAddElement(workingProfile, ControlElementType.BUTTON) }) {
        Icon(Icons.Rounded.Add, contentDescription = null)
        Spacer(Modifier.width(4.dp))
        Text("Add Button")
      }
      OutlinedButton(onClick = { onAddElement(workingProfile, ControlElementType.DPAD) }) {
        Icon(Icons.Rounded.Add, contentDescription = null)
        Spacer(Modifier.width(4.dp))
        Text("Add D-Pad")
      }
    }

    // Selected element properties
    if (selectedElement != null) {
      VortexConsoleCard(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Selected: ${selectedElement.label} (${selectedElement.type.name})", style = MaterialTheme.typography.titleSmall)
          IconButton(
            onClick = {
              val remaining = workingProfile.elements.filterNot { it.id == selectedElement.id }
              workingProfile = workingProfile.copy(elements = remaining)
              selectedElementId = remaining.firstOrNull()?.id
            },
          ) {
            Icon(Icons.Rounded.Delete, contentDescription = "Remove control")
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = selectedElement.label,
            onValueChange = { lbl ->
              workingProfile = workingProfile.copy(
                elements = workingProfile.elements.map {
                  if (it.id == selectedElement.id) it.copy(label = lbl.take(8)) else it
                }
              )
            },
            label = { Text("Label") },
            modifier = Modifier.weight(1f),
            singleLine = true,
          )
          OutlinedTextField(
            value = selectedElement.bindingKey,
            onValueChange = { key ->
              workingProfile = workingProfile.copy(
                elements = workingProfile.elements.map {
                  if (it.id == selectedElement.id) it.copy(bindingKey = key) else it
                }
              )
            },
            label = { Text("X11 / XInput Key") },
            modifier = Modifier.weight(1f),
            singleLine = true,
          )
        }
        Spacer(Modifier.height(8.dp))
        Text("Size: ${selectedElement.sizeDp.toInt()} dp", style = MaterialTheme.typography.labelSmall)
        Slider(
          value = selectedElement.sizeDp,
          onValueChange = { sz ->
            workingProfile = workingProfile.copy(
              elements = workingProfile.elements.map {
                if (it.id == selectedElement.id) it.copy(sizeDp = sz) else it
              }
            )
          },
          valueRange = 42f..130f,
        )
        Text("Opacity: ${(selectedElement.opacity * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
        Slider(
          value = selectedElement.opacity,
          onValueChange = { op ->
            workingProfile = workingProfile.copy(
              elements = workingProfile.elements.map {
                if (it.id == selectedElement.id) it.copy(opacity = op) else it
              }
            )
          },
          valueRange = 0.2f..1.0f,
        )
      }
    }

    // Mouse Mode & Gyro Settings
    VortexConsoleCard(modifier = Modifier.fillMaxWidth()) {
      Text("Mouse Emulation & Gyro Aim", style = MaterialTheme.typography.titleMedium)
      Spacer(Modifier.height(8.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MouseMode.entries.forEach { mode ->
          FilterChip(
            selected = workingProfile.mouseMode == mode,
            onClick = { workingProfile = workingProfile.copy(mouseMode = mode) },
            label = { Text(mode.name.replace('_', ' ')) },
          )
        }
      }
      Spacer(Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Enable Gyroscope Aim", style = MaterialTheme.typography.bodyLarge)
        Switch(
          checked = workingProfile.gyroConfig.enabled,
          onCheckedChange = { enabled ->
            workingProfile = workingProfile.copy(
              gyroConfig = workingProfile.gyroConfig.copy(enabled = enabled)
            )
          },
        )
      }
      if (workingProfile.gyroConfig.enabled) {
        Text("Gyro Sensitivity: ${String.format("%.1fx", workingProfile.gyroConfig.sensitivity)}", style = MaterialTheme.typography.labelSmall)
        Slider(
          value = workingProfile.gyroConfig.sensitivity,
          onValueChange = { s ->
            workingProfile = workingProfile.copy(
              gyroConfig = workingProfile.gyroConfig.copy(sensitivity = s)
            )
          },
          valueRange = 0.5f..5.0f,
        )
      }
    }

    // Physical Gamepad Monitor
    VortexConsoleCard(modifier = Modifier.fillMaxWidth()) {
      Text("Live Controller & Input Monitor", style = MaterialTheme.typography.titleMedium)
      Spacer(Modifier.height(6.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VortexTagPill("Last: ${inputDebug.lastAction}", MaterialTheme.colorScheme.primary)
        VortexTagPill("LS (${String.format("%.2f", inputDebug.leftStickX)}, ${String.format("%.2f", inputDebug.leftStickY)})")
      }
    }
  }
}
