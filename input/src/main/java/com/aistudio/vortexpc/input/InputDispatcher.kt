package com.aistudio.vortexpc.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.aistudio.vortexpc.core.logging.DiagnosticLogManager
import com.aistudio.vortexpc.core.model.LogCategory
import com.aistudio.vortexpc.core.model.MouseMode
import com.aistudio.vortexpc.graphics.X11DisplayServer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiveInputDebugState(
  val lastAction: String = "Ready",
  val activeButtons: Set<String> = emptySet(),
  val leftStickX: Float = 0f,
  val leftStickY: Float = 0f,
  val rightStickX: Float = 0f,
  val rightStickY: Float = 0f,
)

@Singleton
class InputDispatcher @Inject constructor(
  private val x11DisplayServer: X11DisplayServer,
  private val gyroAimController: GyroAimController,
  private val logger: DiagnosticLogManager,
) {
  private var activeProfile: InputProfile = InputProfile.presets().first()

  private val _debugState = MutableStateFlow(LiveInputDebugState())
  val debugState: StateFlow<LiveInputDebugState> = _debugState.asStateFlow()

  fun bindProfile(profile: InputProfile) {
    activeProfile = profile
    gyroAimController.start(profile.gyroConfig) { dx, dy ->
      x11DisplayServer.moveCursorDelta(dx, dy)
    }
    logger.info(LogCategory.INPUT, "InputDispatcher", "Bound profile '${profile.name}' (mouse=${profile.mouseMode}, gyro=${profile.gyroConfig.enabled})")
  }

  fun unbind() {
    gyroAimController.stop()
  }

  fun onTouchSurfaceDrag(dxNorm: Float, dyNorm: Float, absXNorm: Float, absYNorm: Float) {
    when (activeProfile.mouseMode) {
      MouseMode.TOUCHPAD -> {
        val sens = activeProfile.touchpadSensitivity
        x11DisplayServer.moveCursorDelta(dxNorm * sens, dyNorm * sens)
      }
      MouseMode.DIRECT_TOUCH -> {
        x11DisplayServer.moveCursorNormalized(absXNorm, absYNorm)
      }
    }
  }

  fun onTouchSurfaceTap(absXNorm: Float, absYNorm: Float, isRightClick: Boolean = false) {
    if (activeProfile.mouseMode == MouseMode.DIRECT_TOUCH) {
      x11DisplayServer.moveCursorNormalized(absXNorm, absYNorm)
    }
    val mask = if (isRightClick) 4 else 1
    x11DisplayServer.setMouseButton(mask, true)
    x11DisplayServer.setMouseButton(mask, false)
    _debugState.value = _debugState.value.copy(
      lastAction = if (isRightClick) "Mouse Right Click" else "Mouse Left Click",
    )
  }

  fun onVirtualControlEvent(bindingKey: String, pressed: Boolean) {
    val active = _debugState.value.activeButtons.toMutableSet()
    if (pressed) active.add(bindingKey) else active.remove(bindingKey)
    _debugState.value = _debugState.value.copy(
      lastAction = "${if (pressed) "Down" else "Up"}: $bindingKey",
      activeButtons = active,
    )

    when (bindingKey) {
      "MOUSE_LEFT" -> x11DisplayServer.setMouseButton(1, pressed)
      "MOUSE_RIGHT" -> x11DisplayServer.setMouseButton(4, pressed)
      else -> if (pressed) {
        val text = when (bindingKey) {
          "KEY_ENTER" -> "ENTER"
          "KEY_SPACE" -> " "
          "KEY_BACKSPACE" -> "BACKSPACE"
          else -> if (bindingKey.startsWith("KEY_") && bindingKey.length == 5) {
            bindingKey.last().lowercase()
          } else {
            ""
          }
        }
        if (text.isNotEmpty()) {
          x11DisplayServer.injectKeyText(text)
        }
      }
    }
  }

  fun onVirtualStickMove(isRightStick: Boolean, nx: Float, ny: Float) {
    val fx = applyDeadzone(nx, 0.12f)
    val fy = applyDeadzone(ny, 0.12f)
    if (isRightStick) {
      _debugState.value = _debugState.value.copy(rightStickX = fx, rightStickY = fy)
      if (fx != 0f || fy != 0f) {
        x11DisplayServer.moveCursorDelta(fx * 0.015f, fy * 0.015f)
      }
    } else {
      _debugState.value = _debugState.value.copy(leftStickX = fx, leftStickY = fy)
    }
  }

  fun handleHardwareKeyEvent(event: KeyEvent): Boolean {
    val isGamepad = (event.source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
      (event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
    if (!isGamepad) return false

    val binding = activeProfile.gamepadBindings.firstOrNull { it.androidKeyCode == event.keyCode }
      ?: return false

    val pressed = event.action == KeyEvent.ACTION_DOWN
    onVirtualControlEvent(binding.targetAction, pressed)
    return true
  }

  fun handleHardwareMotionEvent(event: MotionEvent): Boolean {
    if ((event.source and InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) return false
    val lx = applyDeadzone(event.getAxisValue(MotionEvent.AXIS_X), 0.14f)
    val ly = applyDeadzone(event.getAxisValue(MotionEvent.AXIS_Y), 0.14f)
    val rx = applyDeadzone(event.getAxisValue(MotionEvent.AXIS_Z), 0.14f)
    val ry = applyDeadzone(event.getAxisValue(MotionEvent.AXIS_RZ), 0.14f)

    onVirtualStickMove(isRightStick = false, nx = lx, ny = ly)
    onVirtualStickMove(isRightStick = true, nx = rx, ny = ry)
    return true
  }

  companion object {
    fun applyDeadzone(value: Float, deadzone: Float): Float {
      val clamped = value.coerceIn(-1f, 1f)
      return if (abs(clamped) < deadzone) 0f else (clamped - kotlin.math.sign(clamped) * deadzone) / (1f - deadzone)
    }
  }
}
