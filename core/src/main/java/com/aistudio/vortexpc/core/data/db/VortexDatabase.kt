package com.aistudio.vortexpc.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
  entities = [
    ContainerEntity::class,
    GameEntity::class,
    InputProfileEntity::class,
    PlaySessionEntity::class,
    CustomDriverEntity::class,
  ],
  version = 1,
  exportSchema = false,
)
abstract class VortexDatabase : RoomDatabase() {
  abstract fun containerDao(): ContainerDao
  abstract fun gameDao(): GameDao
  abstract fun inputProfileDao(): InputProfileDao
  abstract fun playSessionDao(): PlaySessionDao
  abstract fun customDriverDao(): CustomDriverDao
}
