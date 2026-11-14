package com.aistudio.vortexpc.core.data.db

import com.aistudio.vortexpc.core.model.ContainerConfig
import com.aistudio.vortexpc.core.model.CustomDriverPackage
import com.aistudio.vortexpc.core.model.GameEntry
import com.aistudio.vortexpc.core.model.PlaySessionRecord
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

@Singleton
class VortexRepository @Inject constructor(
  private val containerDao: ContainerDao,
  private val gameDao: GameDao,
  private val inputProfileDao: InputProfileDao,
  private val playSessionDao: PlaySessionDao,
  private val customDriverDao: CustomDriverDao,
  private val json: Json,
) {
  val containers: Flow<List<ContainerConfig>> = containerDao.observeAll().map { list ->
    list.mapNotNull { runCatching { json.decodeFromString<ContainerConfig>(it.payloadJson) }.getOrNull() }
  }

  val games: Flow<List<GameEntry>> = gameDao.observeAll().map { list ->
    list.mapNotNull { runCatching { json.decodeFromString<GameEntry>(it.payloadJson) }.getOrNull() }
  }

  val recentSessions: Flow<List<PlaySessionRecord>> = playSessionDao.observeRecent().map { list ->
    list.mapNotNull { runCatching { json.decodeFromString<PlaySessionRecord>(it.payloadJson) }.getOrNull() }
  }

  val customDrivers: Flow<List<CustomDriverPackage>> = customDriverDao.observeAll().map { list ->
    list.mapNotNull { runCatching { json.decodeFromString<CustomDriverPackage>(it.payloadJson) }.getOrNull() }
  }

  val rawInputProfiles: Flow<List<InputProfileEntity>> = inputProfileDao.observeAll()

  suspend fun getContainer(id: String): ContainerConfig? =
    containerDao.getById(id)?.let { runCatching { json.decodeFromString<ContainerConfig>(it.payloadJson) }.getOrNull() }

  suspend fun saveContainer(config: ContainerConfig) {
    containerDao.upsert(
      ContainerEntity(
        id = config.id,
        name = config.name,
        payloadJson = json.encodeToString(ContainerConfig.serializer(), config),
        updatedAtEpochMs = System.currentTimeMillis(),
      )
    )
  }

  suspend fun deleteContainer(id: String) = containerDao.deleteById(id)

  suspend fun getGame(id: String): GameEntry? =
    gameDao.getById(id)?.let { runCatching { json.decodeFromString<GameEntry>(it.payloadJson) }.getOrNull() }

  suspend fun saveGame(game: GameEntry) {
    gameDao.upsert(
      GameEntity(
        id = game.id,
        title = game.title,
        containerId = game.containerId,
        lastPlayedAtEpochMs = game.lastPlayedAtEpochMs,
        playTimeSeconds = game.playTimeSeconds,
        favorite = game.favorite,
        payloadJson = json.encodeToString(GameEntry.serializer(), game),
      )
    )
  }

  suspend fun deleteGame(id: String) = gameDao.deleteById(id)

  suspend fun recordPlaySession(session: PlaySessionRecord) {
    playSessionDao.insert(
      PlaySessionEntity(
        id = session.id,
        gameId = session.gameId,
        startedAtEpochMs = session.startedAtEpochMs,
        durationSeconds = session.durationSeconds,
        payloadJson = json.encodeToString(PlaySessionRecord.serializer(), session),
      )
    )
    val existing = getGame(session.gameId)
    if (existing != null) {
      saveGame(
        existing.copy(
          playTimeSeconds = existing.playTimeSeconds + session.durationSeconds,
          lastPlayedAtEpochMs = session.startedAtEpochMs + (session.durationSeconds * 1000L),
        )
      )
    } else {
      gameDao.recordPlayTime(
        id = session.gameId,
        deltaSeconds = session.durationSeconds,
        playedAtMs = session.startedAtEpochMs,
      )
    }
  }

  suspend fun saveRawInputProfile(id: String, name: String, payloadJson: String) {
    inputProfileDao.upsert(
      InputProfileEntity(
        id = id,
        name = name,
        payloadJson = payloadJson,
        updatedAtEpochMs = System.currentTimeMillis(),
      )
    )
  }

  suspend fun deleteInputProfile(id: String) = inputProfileDao.deleteById(id)

  suspend fun saveCustomDriver(driver: CustomDriverPackage) {
    customDriverDao.upsert(
      CustomDriverEntity(
        id = driver.id,
        name = driver.name,
        driverType = driver.driverType.name,
        payloadJson = json.encodeToString(CustomDriverPackage.serializer(), driver),
        importedAtEpochMs = driver.importedAtEpochMs,
      )
    )
  }

  suspend fun deleteCustomDriver(id: String) = customDriverDao.deleteById(id)
}
