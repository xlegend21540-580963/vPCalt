package com.aistudio.vortexpc.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InputDispatcherTest {

  @Test
  fun `deadzone clamps small stick noise to zero`() {
    assertEquals(0f, InputDispatcher.applyDeadzone(0.08f, 0.12f), 0.0001f)
    assertEquals(0f, InputDispatcher.applyDeadzone(-0.10f, 0.12f), 0.0001f)
    assertEquals(1f, InputDispatcher.applyDeadzone(1.5f, 0.12f), 0.0001f)
  }

  @Test
  fun `gyro filter respects deadzone and sensitivity`() {
    val cfg = GyroConfig(enabled = true, sensitivity = 2.0f, deadzoneRadPerSec = 0.05f)
    val zero = GyroAimController.computeFilteredDelta(0.01f, 0.01f, 0.02f, 0.016f, cfg)
    assertEquals(0f, zero.first, 0.0001f)
    assertEquals(0f, zero.second, 0.0001f)

    val active = GyroAimController.computeFilteredDelta(0.5f, 0f, 1.0f, 0.016f, cfg)
    assertTrue(active.first != 0f)
    assertTrue(active.second != 0f)
  }
}
