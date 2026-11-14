package com.aistudio.vortexpc.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.aistudio.vortexpc.core.model.BundledComponent
import com.aistudio.vortexpc.core.model.CustomDriverPackage
import com.aistudio.vortexpc.core.model.HardwareProfile
import com.aistudio.vortexpc.ui.components.VortexConsoleCard
import com.aistudio.vortexpc.ui.components.VortexTagPill
import com.aistudio.vortexpc.ui.theme.LocalVortexAccents

@Composable
fun DriversAndRuntimeScreen(
  hardwareProfile: HardwareProfile?,
  bundledComponents: List<BundledComponent>,
  customDrivers: List<CustomDriverPackage>,
  onImportDriverClick: () -> Unit,
  onDeleteCustomDriver: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
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
          Text("Drivers & Offline Runtime", style = MaterialTheme.typography.headlineMedium)
          Text(
            text = "Pre-bundled APK components + local AdrenoTools .zip import",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Button(
          onClick = onImportDriverClick,
          modifier = Modifier.testTag("import_driver_button"),
        ) {
          Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(4.dp))
          Text("Import .zip")
        }
      }
    }

    if (hardwareProfile != null) {
      item {
        VortexConsoleCard(modifier = Modifier.fillMaxWidth()) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Hardware & Kernel Diagnostics", style = MaterialTheme.typography.titleMedium)
          }
          Spacer(Modifier.height(8.dp))
          Text("SoC: ${hardwareProfile.socModel} (${hardwareProfile.deviceModel})", style = MaterialTheme.typography.bodyMedium)
          Text("GPU: ${hardwareProfile.gpuModel} (${hardwareProfile.gpuVendor.name})", style = MaterialTheme.typography.bodyMedium)
          Text("Vulkan: ${hardwareProfile.vulkanVersion} • Page Size: ${hardwareProfile.pageSizeBytes}B (${if (hardwareProfile.is16KbPageDevice) "16KB Aligned" else "4KB Standard"})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
          Text("ABIs: ${hardwareProfile.supportedAbis.joinToString()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }

    if (customDrivers.isNotEmpty()) {
      item {
        Text("User-Imported Vulkan Drivers", style = MaterialTheme.typography.titleMedium)
      }
      items(customDrivers, key = { it.id }) { drv ->
        VortexConsoleCard(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("${drv.name} (v${drv.version})", style = MaterialTheme.typography.titleSmall)
              Text("Author: ${drv.author} • ${drv.driverType.name}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onDeleteCustomDriver(drv.id) }) {
              Icon(Icons.Rounded.Delete, contentDescription = "Remove driver")
            }
          }
        }
      }
    }

    item {
      Text("Offline Pre-Bundled Runtime Stack", style = MaterialTheme.typography.titleMedium)
    }

    items(bundledComponents, key = { it.id }) { comp ->
      VortexConsoleCard(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(comp.name, style = MaterialTheme.typography.titleSmall)
            Text(
              text = "${comp.category.name} • v${comp.version} • ${comp.license}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
          if (comp.staged) {
            VortexTagPill("STAGED", accents.telemetryOk)
          } else {
            Icon(Icons.Rounded.CheckCircle, contentDescription = "Bundled", tint = MaterialTheme.colorScheme.primary)
          }
        }
        Spacer(Modifier.height(6.dp))
        Text(
          text = comp.description,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
