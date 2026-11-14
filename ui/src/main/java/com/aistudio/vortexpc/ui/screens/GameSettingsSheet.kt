package com.aistudio.vortexpc.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.aistudio.vortexpc.core.model.AudioDriverType
import com.aistudio.vortexpc.core.model.AutoConfigTier
import com.aistudio.vortexpc.core.model.Box64Profile
import com.aistudio.vortexpc.core.model.ContainerConfig
import com.aistudio.vortexpc.core.model.DxWrapperType
import com.aistudio.vortexpc.core.model.GameEntry
import com.aistudio.vortexpc.core.model.GraphicsDriverType
import com.aistudio.vortexpc.core.model.TranslatorEngine
import com.aistudio.vortexpc.input.InputProfile
import com.aistudio.vortexpc.ui.components.VortexConsoleCard
import com.aistudio.vortexpc.ui.components.VortexTagPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameSettingsSheet(
  game: GameEntry,
  container: ContainerConfig,
  inputProfiles: List<InputProfile>,
  onDismiss: () -> Unit,
  onAutoTune: (AutoConfigTier) -> Unit,
  onSaveContainer: (ContainerConfig) -> Unit,
  onUpdateGame: (GameEntry) -> Unit,
  onPinShortcut: () -> Unit,
  onDeleteGame: () -> Unit,
  onLaunch: () -> Unit,
) {
  var tabIndex by remember { mutableIntStateOf(if (game.useAdvancedOverride) 1 else 0) }
  var editableConfig by remember(container) { mutableStateOf(container) }
  var launchArgs by remember(game) { mutableStateOf(game.launchArgs) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(game.title, style = MaterialTheme.typography.headlineSmall)
          Text(
            text = "${game.exeFileName} • ${game.peArchitecture.name}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        Button(
          onClick = onLaunch,
          modifier = Modifier.testTag("sheet_launch_button"),
        ) {
          Icon(Icons.Rounded.PlayArrow, contentDescription = null)
          Spacer(Modifier.width(4.dp))
          Text("Launch")
        }
      }

      TabRow(selectedTabIndex = tabIndex) {
        Tab(
          selected = tabIndex == 0,
          onClick = { tabIndex = 0 },
          text = { Text("One-Tap Auto Config") },
        )
        Tab(
          selected = tabIndex == 1,
          onClick = { tabIndex = 1 },
          text = { Text("Advanced Winlator") },
        )
      }

      if (tabIndex == 0) {
        if (game.autoConfigReason.isNotBlank()) {
          VortexTagPill(game.autoConfigReason, MaterialTheme.colorScheme.primary)
        }
        AutoConfigTier.entries.forEach { tier ->
          VortexConsoleCard(
            onClick = { onAutoTune(tier) },
            selected = game.autoConfigTier == tier,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Column {
                Text(tier.name.replace('_', ' '), style = MaterialTheme.typography.titleSmall)
                Text(
                  text = when (tier) {
                    AutoConfigTier.BATTERY_SAVER -> "960×540 • 30 FPS • Low thermal draw"
                    AutoConfigTier.BALANCED -> "1280×720 • 60 FPS • Balanced DXVK + Box64"
                    AutoConfigTier.PERFORMANCE -> "Uncapped FPS • BigBlock 3 Dynarec • High-core affinity"
                  },
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      } else {
        Text("Graphics Driver", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          GraphicsDriverType.entries.forEach { drv ->
            FilterChip(
              selected = editableConfig.graphicsDriver == drv,
              onClick = { editableConfig = editableConfig.copy(graphicsDriver = drv) },
              label = { Text(drv.name) },
            )
          }
        }

        Text("DX Wrapper", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          DxWrapperType.entries.forEach { dx ->
            FilterChip(
              selected = editableConfig.dxWrapper == dx,
              onClick = { editableConfig = editableConfig.copy(dxWrapper = dx) },
              label = { Text(dx.name) },
            )
          }
        }

        Text("CPU Translator & Box64 Profile", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TranslatorEngine.entries.forEach { tr ->
            FilterChip(
              selected = editableConfig.translator == tr,
              onClick = { editableConfig = editableConfig.copy(translator = tr) },
              label = { Text(tr.name) },
            )
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Box64Profile.entries.forEach { prof ->
            FilterChip(
              selected = editableConfig.box64Profile == prof,
              onClick = { editableConfig = editableConfig.copy(box64Profile = prof) },
              label = { Text(prof.name) },
            )
          }
        }

        Text("Resolution & FPS Cap", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf("960x540", "1280x720", "1600x720", "1920x1080").forEach { res ->
            FilterChip(
              selected = editableConfig.screenResolution == res,
              onClick = { editableConfig = editableConfig.copy(screenResolution = res) },
              label = { Text(res) },
            )
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(0, 30, 40, 60).forEach { cap ->
            FilterChip(
              selected = editableConfig.fpsLimit == cap,
              onClick = { editableConfig = editableConfig.copy(fpsLimit = cap) },
              label = { Text(if (cap == 0) "Uncapped" else "$cap FPS") },
            )
          }
        }

        Text("Audio Driver", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          AudioDriverType.entries.forEach { audio ->
            FilterChip(
              selected = editableConfig.audioDriver == audio,
              onClick = { editableConfig = editableConfig.copy(audioDriver = audio) },
              label = { Text(audio.name) },
            )
          }
        }

        Text("Input Profile", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          inputProfiles.forEach { prof ->
            FilterChip(
              selected = game.inputProfileId == prof.id,
              onClick = { onUpdateGame(game.copy(inputProfileId = prof.id)) },
              label = { Text(prof.name) },
            )
          }
        }

        OutlinedTextField(
          value = launchArgs,
          onValueChange = { launchArgs = it },
          label = { Text("Launch Arguments") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
        )

        Button(
          onClick = {
            onSaveContainer(editableConfig)
            onUpdateGame(game.copy(launchArgs = launchArgs, useAdvancedOverride = true))
          },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Apply Advanced Settings")
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = onPinShortcut,
          modifier = Modifier.weight(1f),
        ) {
          Icon(Icons.Rounded.AddToHomeScreen, contentDescription = null)
          Spacer(Modifier.width(6.dp))
          Text("Pin Shortcut")
        }
        OutlinedButton(
          onClick = onDeleteGame,
          modifier = Modifier.weight(1f),
        ) {
          Icon(Icons.Rounded.Delete, contentDescription = null)
          Spacer(Modifier.width(6.dp))
          Text("Remove")
        }
      }
      Spacer(Modifier.height(18.dp))
    }
  }
}
