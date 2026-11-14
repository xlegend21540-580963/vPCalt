package com.aistudio.vortexpc.graphics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aistudio.vortexpc.core.logging.DiagnosticLogManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class X11DisplayServerTest {

  @Test
  fun `cursor movement clamps within normalized viewport and updates scale`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val server = X11DisplayServer(DiagnosticLogManager(context))
    server.start(resolution = "1600x720", renderScale = 0.85f, fpsLimit = 40, targetLabel = "notepad.exe")

    try {
      val initial = server.state.value
      assertTrue(initial.running)
      assertEquals(1600, initial.displayWidth)
      assertEquals(720, initial.displayHeight)
      assertEquals(40, initial.fpsLimit)

      server.moveCursorDelta(2.0f, -2.0f)
      assertEquals(1.0f, server.state.value.cursorX, 0.001f)
      assertEquals(0.0f, server.state.value.cursorY, 0.001f)

      server.injectKeyText("Hello")
      assertTrue(server.state.value.typedBuffer.endsWith("Hello"))
    } finally {
      server.stop()
    }
  }
}
