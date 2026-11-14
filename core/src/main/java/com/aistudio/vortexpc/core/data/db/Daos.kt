package com.aistudio.vortexpc.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContainerDao {
  @Query("SELECT * FROM containers ORDER BY updatedAtEpochMs DESC")
  fun observeAll(): Flow<List<ContainerEntity>>

  @Query("SELECT * FROM containers WHERE id = :id LIMIT 1")
  suspend fun getById(id: String): ContainerEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: ContainerEntity)

  @Query("DELETE FROM containers WHERE id = :id")
  suspend fun deleteById(id: String)
}

@Dao
interface GameDao {
  @Query("SELECT * FROM games ORDER BY favorite DESC, lastPlayedAtEpochMs DESC, title ASC")
  fun observeAll(): Flow<List<GameEntity>>

  @Query("SELECT * FROM games WHERE id = :id LIMIT 1")
  suspend fun getById(id: String): GameEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: GameEntity)

  @Query("DELETE FROM games WHERE id = :id")
  suspend fun deleteById(id: String)

  @Query("UPDATE games SET playTimeSeconds = playTimeSeconds + :deltaSeconds, lastPlayedAtEpochMs = :playedAtMs WHERE id = :id")
  suspend fun recordPlayTime(id: String, deltaSeconds: Long, playedAtMs: Long)
}

@Dao
interface InputProfileDao {
  @Query("SELECT * FROM input_profiles ORDER BY updatedAtEpochMs DESC")
  fun observeAll(): Flow<List<InputProfileEntity>>

  @Query("SELECT * FROM input_profiles WHERE id = :id LIMIT 1")
  suspend fun getById(id: String): InputProfileEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: InputProfileEntity)

  @Query("DELETE FROM input_profiles WHERE id = :id")
  suspend fun deleteById(id: String)
}

@Dao
interface PlaySessionDao {
  @Query("SELECT * FROM play_sessions ORDER BY startedAtEpochMs DESC LIMIT 50")
  fun observeRecent(): Flow<List<PlaySessionEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(entity: PlaySessionEntity)
}

@Dao
interface CustomDriverDao {
  @Query("SELECT * FROM custom_drivers ORDER BY importedAtEpochMs DESC")
  fun observeAll(): Flow<List<CustomDriverEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: CustomDriverEntity)

  @Query("DELETE FROM custom_drivers WHERE id = :id")
  suspend fun deleteById(id: String)
}
