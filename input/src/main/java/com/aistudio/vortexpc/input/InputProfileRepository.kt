package com.aistudio.vortexpc.input

import com.aistudio.vortexpc.core.data.db.VortexRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

@Singleton
class InputProfileRepository @Inject constructor(
  private val repository: VortexRepository,
  private val json: Json,
) {
  val profiles: Flow<List<InputProfile>> = repository.rawInputProfiles.map { rows ->
    val decoded = rows.mapNotNull { row ->
      runCatching { json.decodeFromString<InputProfile>(row.payloadJson) }.getOrNull()
    }
    if (decoded.isEmpty()) InputProfile.presets() else decoded
  }

  suspend fun ensureDefaultsSeeded() {
    val rows = repository.rawInputProfiles.firstOrNull()
    if (rows.isNullOrEmpty()) {
      InputProfile.presets().forEach { preset ->
        saveProfile(preset)
      }
    }
  }

  suspend fun saveProfile(profile: InputProfile) {
    repository.saveRawInputProfile(
      id = profile.id,
      name = profile.name,
      payloadJson = json.encodeToString(InputProfile.serializer(), profile),
    )
  }

  suspend fun deleteProfile(profileId: String) {
    repository.deleteInputProfile(profileId)
  }

  fun exportProfileJson(profile: InputProfile): String =
    json.encodeToString(InputProfile.serializer(), profile)

  fun importProfileJson(rawJson: String): InputProfile? =
    runCatching { json.decodeFromString<InputProfile>(rawJson) }.getOrNull()
}
