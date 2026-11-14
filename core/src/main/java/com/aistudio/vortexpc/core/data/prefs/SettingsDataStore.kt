package com.aistudio.vortexpc.core.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aistudio.vortexpc.core.model.AutoConfigTier
import com.aistudio.vortexpc.core.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.vortexDataStore: DataStore<Preferences> by preferencesDataStore(name = "vortex_settings")

data class UserPreferences(
  val themeMode: ThemeMode = ThemeMode.AMOLED,
  val onboardingCompleted: Boolean = false,
  val runtimeStagedVersion: String = "",
  val defaultAutoTier: AutoConfigTier = AutoConfigTier.BALANCED,
  val showHudOverlay: Boolean = true,
  val hapticsEnabled: Boolean = true,
  val controllerFocusSounds: Boolean = true,
  val safTreeUris: Set<String> = emptySet(),
)

@Singleton
class SettingsDataStore @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  private object Keys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    val RUNTIME_STAGED_VERSION = stringPreferencesKey("runtime_staged_version")
    val DEFAULT_AUTO_TIER = stringPreferencesKey("default_auto_tier")
    val SHOW_HUD_OVERLAY = booleanPreferencesKey("show_hud_overlay")
    val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
    val CONTROLLER_SOUNDS = booleanPreferencesKey("controller_sounds")
    val SAF_TREE_URIS = stringPreferencesKey("saf_tree_uris")
  }

  val preferences: Flow<UserPreferences> = context.vortexDataStore.data.map { prefs ->
    UserPreferences(
      themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
        ?: ThemeMode.AMOLED,
      onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
      runtimeStagedVersion = prefs[Keys.RUNTIME_STAGED_VERSION] ?: "",
      defaultAutoTier = prefs[Keys.DEFAULT_AUTO_TIER]?.let {
        runCatching { AutoConfigTier.valueOf(it) }.getOrNull()
      } ?: AutoConfigTier.BALANCED,
      showHudOverlay = prefs[Keys.SHOW_HUD_OVERLAY] ?: true,
      hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
      controllerFocusSounds = prefs[Keys.CONTROLLER_SOUNDS] ?: true,
      safTreeUris = prefs[Keys.SAF_TREE_URIS]
        ?.split('\n')
        ?.filter { it.isNotBlank() }
        ?.toSet()
        ?: emptySet(),
    )
  }

  suspend fun setThemeMode(mode: ThemeMode) {
    context.vortexDataStore.edit { it[Keys.THEME_MODE] = mode.name }
  }

  suspend fun setOnboardingCompleted(completed: Boolean, stagedVersion: String? = null) {
    context.vortexDataStore.edit { prefs ->
      prefs[Keys.ONBOARDING_COMPLETED] = completed
      if (stagedVersion != null) {
        prefs[Keys.RUNTIME_STAGED_VERSION] = stagedVersion
      }
    }
  }

  suspend fun setDefaultAutoTier(tier: AutoConfigTier) {
    context.vortexDataStore.edit { it[Keys.DEFAULT_AUTO_TIER] = tier.name }
  }

  suspend fun setShowHudOverlay(enabled: Boolean) {
    context.vortexDataStore.edit { it[Keys.SHOW_HUD_OVERLAY] = enabled }
  }

  suspend fun setHapticsEnabled(enabled: Boolean) {
    context.vortexDataStore.edit { it[Keys.HAPTICS_ENABLED] = enabled }
  }

  suspend fun setControllerFocusSounds(enabled: Boolean) {
    context.vortexDataStore.edit { it[Keys.CONTROLLER_SOUNDS] = enabled }
  }

  suspend fun addSafTreeUri(uri: String) {
    context.vortexDataStore.edit { prefs ->
      val current = prefs[Keys.SAF_TREE_URIS]
        ?.split('\n')
        ?.filter { it.isNotBlank() }
        ?.toMutableSet()
        ?: mutableSetOf()
      current.add(uri)
      prefs[Keys.SAF_TREE_URIS] = current.joinToString("\n")
    }
  }
}
