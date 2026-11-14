package com.aistudio.vortexpc.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.aistudio.vortexpc.core.model.CompatibilityPreset
import com.aistudio.vortexpc.core.model.ContainerConfig
import com.aistudio.vortexpc.runtime.WinetricksComponent
import com.aistudio.vortexpc.ui.components.VortexConsoleCard
import com.aistudio.vortexpc.ui.components.VortexTagPill
import com.aistudio.vortexpc.ui.theme.LocalVortexAccents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainersScreen(
  containers: List<ContainerConfig>,
  presets: List<CompatibilityPreset>,
  winetricks: List<WinetricksComponent>,
  onCreateContainer: (name: String, preset: CompatibilityPreset?) -> Unit,
  onSaveContainer: (ContainerConfig) -> Unit,
  onCloneContainer: (ContainerConfig) -> Unit,
  onDeleteContainer: (String) -> Unit,
  onExportContainer: (ContainerConfig) -> Unit,
  onImportContainerClick: () -> Unit,
  onInstallWinetricks: (containerId: String, componentId: String) -> Unit,
  onLaunchWineTool: (toolExe: String, title: String, ContainerConfig) -> Unit,
  modifier: Modifier = Modifier,
) {
  var showCreateDialog by remember { mutableStateOf(false) }
  var editingContainer by remember { mutableStateOf<ContainerConfig?>(null) }
  var winetricksTarget by remember { mutableStateOf<ContainerConfig?>(null) }
  val accents = LocalVortexAccents.current

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text("Wine Containers", style = MaterialTheme.typography.headlineMedium)
          Text(
            text = "Isolated prefixes, registry, drives & CPU affinity",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = onImportContainerClick,
            modifier = Modifier.testTag("import_container_button"),
          ) {
            Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Import")
          }
          Button(
            onClick = { showCreateDialog = true },
            modifier = Modifier.testTag("create_container_button"),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("New")
          }
        }
      }
    }

    if (presets.isNotEmpty()) {
      item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Shareable Preset Templates", style = MaterialTheme.typography.titleMedium)
          LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(presets, key = { it.id }) { preset ->
              VortexConsoleCard(
                onClick = { onCreateContainer(preset.name, preset) },
                modifier = Modifier.width(240.dp),
              ) {
                VortexTagPill("${preset.translator.name} • ${preset.dxWrapper.name}", MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Text(preset.name, style = MaterialTheme.typography.titleSmall)
                Text(
                  text = preset.description,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    }

    items(containers, key = { it.id }) { ctr ->
      VortexConsoleCard(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(ctr.name, style = MaterialTheme.typography.titleMedium)
            Text(
              text = "${ctr.wineVersion} • ${ctr.screenResolution} • ${ctr.audioDriver.name}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
          Row {
            IconButton(onClick = { onCloneContainer(ctr) }) {
              Icon(Icons.Rounded.ContentCopy, contentDescription = "Clone container")
            }
            IconButton(onClick = { onExportContainer(ctr) }) {
              Icon(Icons.Rounded.Download, contentDescription = "Export container")
            }
            IconButton(onClick = { onDeleteContainer(ctr.id) }) {
              Icon(Icons.Rounded.Delete, contentDescription = "Delete container")
            }
          }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          VortexTagPill(ctr.graphicsDriver.name, accents.telemetryOk)
          VortexTagPill(ctr.dxWrapper.name, MaterialTheme.colorScheme.primary)
          VortexTagPill("${ctr.translator.name} (${ctr.box64Profile.name})", MaterialTheme.colorScheme.secondary)
        }

        Spacer(Modifier.height(10.dp))
        Text("Wine Tools & Prefix Actions", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          item {
            OutlinedButton(onClick = { onLaunchWineTool("notepad.exe", "Notepad", ctr) }) {
              Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("notepad.exe")
            }
          }
          item {
            OutlinedButton(onClick = { onLaunchWineTool("explorer.exe", "Wine File Explorer", ctr) }) {
              Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Explorer")
            }
          }
          item {
            OutlinedButton(onClick = { onLaunchWineTool("winecfg", "Wine Control Panel", ctr) }) {
              Icon(Icons.Rounded.Build, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("winecfg")
            }
          }
          item {
            OutlinedButton(onClick = { onLaunchWineTool("taskmgr.exe", "Task Manager", ctr) }) {
              Text("taskmgr")
            }
          }
          item {
            OutlinedButton(onClick = { onLaunchWineTool("regedit.exe", "Registry Editor", ctr) }) {
              Text("regedit")
            }
          }
          item {
            Button(onClick = { winetricksTarget = ctr }) {
              Icon(Icons.Rounded.Extension, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Winetricks")
            }
          }
          item {
            OutlinedButton(onClick = { editingContainer = ctr }) {
              Text("Env & Affinity")
            }
          }
        }
      }
    }
  }

  if (showCreateDialog) {
    var newName by remember { mutableStateOf("Windows Gaming Prefix") }
    AlertDialog(
      onDismissRequest = { showCreateDialog = false },
      title = { Text("Create Wine Container") },
      text = {
        OutlinedTextField(
          value = newName,
          onValueChange = { newName = it },
          label = { Text("Container Name") },
          singleLine = true,
        )
      },
      confirmButton = {
        Button(onClick = {
          onCreateContainer(newName, null)
          showCreateDialog = false
        }) {
          Text("Create")
        }
      },
      dismissButton = {
        TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
      },
    )
  }

  val activeWinetricksCtr = winetricksTarget?.let { target ->
    containers.firstOrNull { it.id == target.id } ?: target
  }
  if (activeWinetricksCtr != null) {
    ModalBottomSheet(onDismissRequest = { winetricksTarget = null }) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text("Winetricks Components — ${activeWinetricksCtr.name}", style = MaterialTheme.typography.titleMedium)
        winetricks.forEach { comp ->
          val installed = comp.id in activeWinetricksCtr.installedComponents
          VortexConsoleCard(
            onClick = { onInstallWinetricks(activeWinetricksCtr.id, comp.id) },
            selected = installed,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Checkbox(checked = installed, onCheckedChange = null)
              Spacer(Modifier.width(8.dp))
              Column {
                Text(comp.displayName, style = MaterialTheme.typography.titleSmall)
                Text(comp.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        }
        Spacer(Modifier.height(14.dp))
      }
    }
  }

  val ctrToEdit = editingContainer
  if (ctrToEdit != null) {
    var envText by remember(ctrToEdit) {
      mutableStateOf(ctrToEdit.envVars.entries.joinToString("\n") { "${it.key}=${it.value}" })
    }
    var mask by remember(ctrToEdit) { mutableStateOf(ctrToEdit.cpuAffinityMask) }
    ModalBottomSheet(onDismissRequest = { editingContainer = null }) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text("CPU Affinity & Environment — ${ctrToEdit.name}", style = MaterialTheme.typography.titleMedium)
        Text("CPU Core Affinity Mask", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          (0 until 8).forEach { core ->
            val enabled = (mask and (1 shl core)) != 0
            FilterChip(
              selected = enabled,
              onClick = { mask = mask xor (1 shl core) },
              label = { Text("C$core") },
            )
          }
        }
        OutlinedTextField(
          value = envText,
          onValueChange = { envText = it },
          label = { Text("Environment Variables (KEY=VALUE per line)") },
          modifier = Modifier
            .fillMaxWidth()
            .height(170.dp),
        )
        Button(
          onClick = {
            val parsed = envText.lines()
              .mapNotNull { line ->
                val idx = line.indexOf('=')
                if (idx > 0) line.substring(0, idx).trim() to line.substring(idx + 1).trim() else null
              }
              .toMap()
            onSaveContainer(ctrToEdit.copy(cpuAffinityMask = mask, envVars = parsed))
            editingContainer = null
          },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Save Container Environment")
        }
        Spacer(Modifier.height(16.dp))
      }
    }
  }
}
