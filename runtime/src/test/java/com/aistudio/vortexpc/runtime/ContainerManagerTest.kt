package com.aistudio.vortexpc.runtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aistudio.vortexpc.core.data.db.VortexDatabase
import com.aistudio.vortexpc.core.data.db.VortexRepository
import com.aistudio.vortexpc.core.logging.DiagnosticLogManager
import com.aistudio.vortexpc.core.model.ContainerConfig
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ContainerManagerTest {

  private lateinit var db: VortexDatabase
  private lateinit var manager: ContainerManager
  private lateinit var repo: VortexRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    db = Room.inMemoryDatabaseBuilder(context, VortexDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    repo = VortexRepository(
      containerDao = db.containerDao(),
      gameDao = db.gameDao(),
      inputProfileDao = db.inputProfileDao(),
      playSessionDao = db.playSessionDao(),
      customDriverDao = db.customDriverDao(),
      json = json,
    )
    manager = ContainerManager(
      context = context,
      repository = repo,
      json = json,
      logger = DiagnosticLogManager(context),
    )
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `create clone winetricks and export import container`() = runTest {
    val created = manager.createContainer(ContainerConfig(id = "ctr-test-1", name = "Test Box"))
    val prefix = manager.getPrefixDir(created.id)
    assertTrue(prefix.resolve("drive_c/windows/system32").exists())
    assertTrue(prefix.resolve("system.reg").exists())

    val cloned = manager.cloneContainer(created)
    assertEquals("Test Box (Copy)", cloned.name)

    val withTricks = manager.installWinetricksComponent(created.id, "d3dx11")
    assertNotNull(withTricks)
    assertTrue("d3dx11" in withTricks!!.installedComponents)
    assertTrue(prefix.resolve("user.reg").readText().contains("d3dcompiler_47"))

    val archiveOut = ByteArrayOutputStream()
    manager.exportContainerArchive(withTricks, archiveOut)
    val imported = manager.importContainerArchive(ByteArrayInputStream(archiveOut.toByteArray()))
    assertNotNull(imported)
    assertTrue(imported!!.name.contains("(Imported)"))

    val all = repo.containers.first()
    assertEquals(3, all.size)
  }
}
