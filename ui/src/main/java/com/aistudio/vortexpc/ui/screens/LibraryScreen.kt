package com.aistudio.vortexpc.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aistudio.vortexpc.core.model.AutoConfigTier
import com.aistudio.vortexpc.core.model.ContainerConfig
import com.aistudio.vortexpc.core.model.GameEntry
import com.aistudio.vortexpc.core.model.HardwareProfile
import com.aistudio.vortexpc.core.model.PeArchitecture
import com.aistudio.vortexpc.core.model.ThemeMode
import com.aistudio.vortexpc.input.InputProfile
import com.aistudio.vortexpc.ui.R
import com.aistudio.vortexpc.ui.components.VortexConsoleCard
import com.aistudio.vortexpc.ui.components.VortexTagPill
import com.aistudio.vortexpc.ui.theme.LocalVortexAccents
import com.aistudio.vortexpc.ui.theme.VortexTheme
import com.aistudio.vortexpc.ui.viewmodel.LibrarySortMode

@Composable
fun LibraryScreen(
  games: List<GameEntry>,
  containers: List<ContainerConfig>,
  inputProfiles: List<InputProfile>,
  hardwareProfile: HardwareProfile?,
  onScanFolderClick: () -> Unit,
  onAddExeClick: () -> Unit,
  onLaunchGame: (GameEntry) -> Unit,
  onLaunchNotepadDemo: (ContainerConfig) -> Unit,
  onAutoTuneGame: (GameEntry, AutoConfigTier) -> Unit,
  onUpdateGame: (GameEntry) -> Unit,
  onSaveContainer: (ContainerConfig) -> Unit,
  onDeleteGame: (String) -> Unit,
  onToggleFavorite: (GameEntry) -> Unit,
  onPinShortcut: (GameEntry) -> Unit,
  modifier: Modifier = Modifier,
) {
  var searchQuery by remember { mutableStateOf("") }
  var favoritesOnly by remember { mutableStateOf(false) }
  var sortMode by remember { mutableStateOf(LibrarySortMode.RECENT) }
  var selectedGameForSheet by remember { mutableStateOf<GameEntry?>(null) }

  val filteredGames = remember(games, searchQuery, favoritesOnly, sortMode) {
    games
      .filter { g ->
        (searchQuery.isBlank() || g.title.contains(searchQuery, ignoreCase = true) || g.exeFileName.contains(searchQuery, ignoreCase = true)) &&
          (!favoritesOnly || g.favorite)
      }
      .let { list ->
        when (sortMode) {
          LibrarySortMode.RECENT -> list.sortedByDescending { it.lastPlayedAtEpochMs }
          LibrarySortMode.PLAYTIME -> list.sortedByDescending { it.playTimeSeconds }
          LibrarySortMode.TITLE -> list.sortedBy { it.title.lowercase() }
        }
      }
  }

  val heroGame = filteredGames.firstOrNull()
  val accents = LocalVortexAccents.current
  val defaultContainer = containers.firstOrNull()

  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 185.dp),
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // Top console header & action bar
    item(span = { GridItemSpan(maxLineSpan) }) {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column {
            Text(
              text = "VortexPC",
              style = MaterialTheme.typography.headlineMedium,
            )
            if (hardwareProfile != null) {
              Text(
                text = "${hardwareProfile.gpuModel} • Vulkan ${hardwareProfile.vulkanVersion}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
              onClick = onScanFolderClick,
              modifier = Modifier.testTag("scan_folder_button"),
            ) {
              Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text(stringResource(R.string.action_scan_folder))
            }
            Button(
              onClick = onAddExeClick,
              modifier = Modifier.testTag("add_exe_button"),
            ) {
              Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text(stringResource(R.string.action_add_exe))
            }
          }
        }

        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("library_search_input"),
          placeholder = { Text("Search Windows .exe titles...") },
          leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { searchQuery = "" }) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
              }
            }
          },
          singleLine = true,
          shape = MaterialTheme.shapes.medium,
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          FilterChip(
            selected = favoritesOnly,
            onClick = { favoritesOnly = !favoritesOnly },
            label = { Text("Favorites") },
            leadingIcon = {
              Icon(
                if (favoritesOnly) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
            },
          )
          LibrarySortMode.entries.forEach { mode ->
            FilterChip(
              selected = sortMode == mode,
              onClick = { sortMode = mode },
              label = { Text(mode.name.lowercase().replaceFirstChar { it.titlecase() }) },
            )
          }
        }
      }
    }

    // Hero Continue Playing Banner
    if (heroGame != null) {
      item(span = { GridItemSpan(maxLineSpan) }) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
          if (heroGame.coverArtUri != null) {
            AsyncImage(
              model = heroGame.coverArtUri,
              contentDescription = heroGame.title,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
          } else {
            Image(
              painter = painterResource(id = R.drawable.img_default_game_cover),
              contentDescription = heroGame.title,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
          }
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(
                Brush.horizontalGradient(
                  colors = listOf(Color(0xEE000000), Color(0x99000000), Color(0x33000000)),
                )
              )
          )
          Column(
            modifier = Modifier
              .align(Alignment.BottomStart)
              .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              VortexTagPill("CONTINUE PLAYING", accents.telemetryOk)
              VortexTagPill(heroGame.peArchitecture.name, MaterialTheme.colorScheme.primary)
              VortexTagPill(formatPlaytime(heroGame.playTimeSeconds), Color(0xFFE0E7FF))
            }
            Text(
              text = heroGame.title,
              style = MaterialTheme.typography.headlineMedium,
              color = Color.White,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Button(
                onClick = { onLaunchGame(heroGame) },
                modifier = Modifier.testTag("hero_launch_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
              ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_launch))
              }
              OutlinedButton(
                onClick = { selectedGameForSheet = heroGame },
                modifier = Modifier.testTag("hero_settings_button"),
              ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Auto / Config")
              }
            }
          }
        }
      }
    }

    // Empty state when user hasn't added any .exe files yet (no fake games!)
    if (filteredGames.isEmpty()) {
      item(span = { GridItemSpan(maxLineSpan) }) {
        VortexConsoleCard(modifier = Modifier.fillMaxWidth()) {
          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = stringResource(R.string.empty_library_title),
              style = MaterialTheme.typography.headlineSmall,
            )
            Text(
              text = stringResource(R.string.empty_library_subtitle),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Button(onClick = onScanFolderClick) {
                Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Select Games Folder")
              }
              if (defaultContainer != null) {
                OutlinedButton(
                  onClick = { onLaunchNotepadDemo(defaultContainer) },
                  modifier = Modifier.testTag("launch_notepad_demo_button"),
                ) {
                  Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Test X11 notepad.exe")
                }
              }
            }
          }
        }
      }
    } else {
      if (filteredGames.size > 1) {
        item(span = { GridItemSpan(maxLineSpan) }) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Quick Launch Carousel", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              items(filteredGames, key = { "carousel_${it.id}" }) { game ->
                VortexConsoleCard(
                  onClick = { onLaunchGame(game) },
                  modifier = Modifier.width(210.dp),
                ) {
                  Text(
                    text = game.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Spacer(Modifier.height(4.dp))
                  Text(
                    text = "${game.autoConfigTier.name} • ${formatPlaytime(game.playTimeSeconds)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }
      }

      items(filteredGames, key = { it.id }) { game ->
        VortexConsoleCard(
          onClick = { onLaunchGame(game) },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("game_card_${game.id}"),
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .aspectRatio(4f / 3f)
              .clip(RoundedCornerShape(12.dp)),
          ) {
            if (game.coverArtUri != null) {
              AsyncImage(
                model = game.coverArtUri,
                contentDescription = game.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
              )
            } else {
              Image(
                painter = painterResource(id = R.drawable.img_default_game_cover),
                contentDescription = game.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
              )
            }
            Row(
              modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp),
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              IconButton(onClick = { onToggleFavorite(game) }) {
                Icon(
                  imageVector = if (game.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                  contentDescription = "Favorite",
                  tint = if (game.favorite) Color(0xFFFF4D5A) else Color.White,
                )
              }
              IconButton(onClick = { selectedGameForSheet = game }) {
                Icon(
                  imageVector = Icons.Rounded.Settings,
                  contentDescription = "Game Settings",
                  tint = Color.White,
                )
              }
            }
          }
          Spacer(Modifier.height(10.dp))
          Text(
            text = game.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "${game.exeFileName} • ${game.peArchitecture.name}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Spacer(Modifier.height(6.dp))
          VortexTagPill(
            text = game.autoConfigTier.name.replace('_', ' '),
            accentColor = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }
  }

  val activeSheetGame = selectedGameForSheet?.let { selected ->
    games.firstOrNull { it.id == selected.id } ?: selected
  }
  if (activeSheetGame != null) {
    val container = containers.firstOrNull { it.id == activeSheetGame.containerId } ?: containers.firstOrNull()
    if (container != null) {
      GameSettingsSheet(
        game = activeSheetGame,
        container = container,
        inputProfiles = inputProfiles,
        onDismiss = { selectedGameForSheet = null },
        onAutoTune = { tier -> onAutoTuneGame(activeSheetGame, tier) },
        onSaveContainer = onSaveContainer,
        onUpdateGame = onUpdateGame,
        onPinShortcut = { onPinShortcut(activeSheetGame) },
        onDeleteGame = {
          onDeleteGame(activeSheetGame.id)
          selectedGameForSheet = null
        },
        onLaunch = {
          selectedGameForSheet = null
          onLaunchGame(activeSheetGame)
        },
      )
    }
  }
}

fun formatPlaytime(seconds: Long): String {
  if (seconds < 60L) return "${seconds}s played"
  val mins = seconds / 60L
  val hrs = mins / 60L
  return if (hrs > 0L) "${hrs}h ${mins % 60}m" else "${mins}m played"
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun LibraryPreview() {
  VortexTheme(ThemeMode.AMOLED) {
    LibraryScreen(
      games = listOf(
        GameEntry(
          id = "g1",
          title = "Sample Tool",
          exeUri = "content://sample/tool.exe",
          exeFileName = "tool.exe",
          peArchitecture = PeArchitecture.X64,
          containerId = "c1",
          playTimeSeconds = 3720L,
        )
      ),
      containers = listOf(ContainerConfig(id = "c1", name = "Default Container")),
      inputProfiles = InputProfile.presets(),
      hardwareProfile = null,
      onScanFolderClick = {},
      onAddExeClick = {},
      onLaunchGame = {},
      onLaunchNotepadDemo = {},
      onAutoTuneGame = { _, _ -> },
      onUpdateGame = {},
      onSaveContainer = {},
      onDeleteGame = {},
      onToggleFavorite = {},
      onPinShortcut = {},
    )
  }
}
