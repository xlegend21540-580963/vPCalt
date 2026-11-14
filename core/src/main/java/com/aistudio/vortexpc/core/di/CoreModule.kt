package com.aistudio.vortexpc.core.di

import android.content.Context
import androidx.room.Room
import com.aistudio.vortexpc.core.data.db.ContainerDao
import com.aistudio.vortexpc.core.data.db.CustomDriverDao
import com.aistudio.vortexpc.core.data.db.GameDao
import com.aistudio.vortexpc.core.data.db.InputProfileDao
import com.aistudio.vortexpc.core.data.db.PlaySessionDao
import com.aistudio.vortexpc.core.data.db.VortexDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

  @Provides
  @Singleton
  fun provideJson(): Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
  }

  @Provides
  @Singleton
  fun provideVortexDatabase(
    @ApplicationContext context: Context,
  ): VortexDatabase = Room.databaseBuilder(
    context,
    VortexDatabase::class.java,
    "vortexpc.db",
  ).fallbackToDestructiveMigration(dropAllTables = true).build()

  @Provides
  fun provideContainerDao(db: VortexDatabase): ContainerDao = db.containerDao()

  @Provides
  fun provideGameDao(db: VortexDatabase): GameDao = db.gameDao()

  @Provides
  fun provideInputProfileDao(db: VortexDatabase): InputProfileDao = db.inputProfileDao()

  @Provides
  fun providePlaySessionDao(db: VortexDatabase): PlaySessionDao = db.playSessionDao()

  @Provides
  fun provideCustomDriverDao(db: VortexDatabase): CustomDriverDao = db.customDriverDao()
}
