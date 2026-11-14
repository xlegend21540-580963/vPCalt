package com.aistudio.vortexpc.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aistudio.vortexpc.core.data.prefs.UserPreferences
import com.aistudio.vortexpc.core.model.DiagnosticLogEntry
import com.aistudio.vortexpc.core.model.LogLevel
import com.aistudio.vortexpc.core.model.PlaySessionRecord
import com.aistudio.vortexpc.core.model.ThemeMode
import com.aistudio.vortexpc.core.model.UpstreamAttributions
import com.aistudio.vortexpc.ui.R
import com.aistudio.vortexpc.ui.components.VortexConsoleCard
import com.aistudio.vortexpc.ui.theme.LocalVortexAccents

@Composable
fun SettingsAndLogsScreen(
  preferences: UserPreferences,
  logs: List<DiagnosticLogEntry>,
  recentSessions: List<PlaySessionRecord>,
  onSetThemeMode: (ThemeMode) -> Unit,
  onSetShowHud: (Boolean) -> Unit,
  onSetHaptics: (Boolean) -> Unit,
  onExportLogsClick: () -> Unit,
  onClearLogsClick: () -> Unit,
  onRerunOnboarding: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var showLicensesDialog by remember { mutableStateOf(false) }
  val accents = LocalVortexAccents.current

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Text("Settings, Telemetry & Logs", style = MaterialTheme.typography.headlineMedium)
    }

    item {
      VortexConsoleCard(modifier = Modifier.fillMaxWidth()) {
        Text("Appearance & Theme", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          ThemeMode.entries.forEach { mode ->
            FilterChip(
              selected = preferences.themeMode == mode,
              onClick = { onSetThemeMode(mode) },
              label = { Text(mode.name) },
            )
          }
        }
        Spacer(Modifier.height(10.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Show Live Performance HUD", style = MaterialTheme.typography.bodyLarge)
          Switch(checked = preferences.showHudOverlay, onCheckedChange = onSetShowHud)
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Console Haptic Feedback", style = MaterialTheme.typography.bodyLarge)
          Switch(checked = preferences.hapticsEnabled, onCheckedChange = onSetHaptics)
        }
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(
          onClick = onExportLogsClick,
          modifier = Modifier
            .weight(1f)
            .testTag("export_logs_button"),
        ) {
          Icon(Icons.Rounded.FileDownload, contentDescription = null)
          Spacer(Modifier.width(6.dp))
          Text(stringResource(R.string.action_export_logs))
        }
        OutlinedButton(
          onClick = { showLicensesDialog = true },
          modifier = Modifier
            .weight(1f)
            .testTag("open_licenses_button"),
        ) {
          Icon(Icons.Rounded.Gavel, contentDescription = null)
          Spacer(Modifier.width(6.dp))
          Text("GPLv3 & Licenses")
        }
      }
    }

    item {
      OutlinedButton(
        onClick = onRerunOnboarding,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Icon(Icons.Rounded.Replay, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text("Re-Run Hardware Setup Wizard")
      }
    }

    if (recentSessions.isNotEmpty()) {
      item {
        Text("Recent Play Sessions", style = MaterialTheme.typography.titleMedium)
      }
      items(recentSessions.take(5), key = { it.id }) { sess ->
        VortexConsoleCard(modifier = Modifier.fillMaxWidth()) {
          Text(sess.gameTitle, style = MaterialTheme.typography.titleSmall)
          Text(
            text = "${sess.durationSeconds}s • Avg ${String.format("%.1f", sess.avgFps)} FPS • Peak ${String.format("%.1f", sess.peakTempC)}°C",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Live Runtime & X11 Terminal Logs", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onClearLogsClick) {
          Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
          Spacer(Modifier.width(4.dp))
          Text("Clear")
        }
      }
    }

    items(logs.asReversed().take(80)) { entry ->
      val color = when (entry.level) {
        LogLevel.ERROR -> accents.telemetryHot
        LogLevel.WARN -> accents.telemetryWarn
        LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
        LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
      }
      Text(
        text = "[${entry.category.name}/${entry.tag}] ${entry.message}",
        style = MaterialTheme.typography.labelSmall,
        color = color,
      )
    }
  }

  if (showLicensesDialog) {
    AlertDialog(
      onDismissRequest = { showLicensesDialog = false },
      title = { Text(stringResource(R.string.gpl_notice_title)) },
      text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          item {
            Text(
              text = "VortexPC is licensed under GPLv3 and attributes the following upstream open-source projects:",
              style = MaterialTheme.typography.bodyMedium,
            )
          }
          items(UpstreamAttributions.all) { attr ->
            Column {
              Text("${attr.project} (${attr.license})", style = MaterialTheme.typography.titleSmall)
              Text(attr.role, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      },
      confirmButton = {
        Button(onClick = { showLicensesDialog = false }) { Text("Close") }
      },
    )
  }
}
