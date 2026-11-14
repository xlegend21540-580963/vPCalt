package com.aistudio.vortexpc.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "containers")
data class ContainerEntity(
  @PrimaryKey val id: String,
  val name: String,
  val payloadJson: String,
  val updatedAtEpochMs: Long,
)

@Entity(tableName = "games")
data class GameEntity(
  @PrimaryKey val id: String,
  val title: String,
  val containerId: String,
  val lastPlayedAtEpochMs: Long,
  val playTimeSeconds: Long,
  val favorite: Boolean,
  val payloadJson: String,
)

@Entity(tableName = "input_profiles")
data class InputProfileEntity(
  @PrimaryKey val id: String,
  val name: String,
  val payloadJson: String,
  val updatedAtEpochMs: Long,
)

@Entity(tableName = "play_sessions")
data class PlaySessionEntity(
  @PrimaryKey val id: String,
  val gameId: String,
  val startedAtEpochMs: Long,
  val durationSeconds: Long,
  val payloadJson: String,
)

@Entity(tableName = "custom_drivers")
data class CustomDriverEntity(
  @PrimaryKey val id: String,
  val name: String,
  val driverType: String,
  val payloadJson: String,
  val importedAtEpochMs: Long,
)
