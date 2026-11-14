package com.aistudio.vortexpc.input

import android.view.KeyEvent
import com.aistudio.vortexpc.core.model.MouseMode
import kotlinx.serialization.Serializable

@Serializable
enum class ControlElementType {
  BUTTON,
  DPAD,
  THUMBSTICK_LEFT,
  THUMBSTICK_RIGHT,
  TRACKPAD,
}

@Serializable
enum class GyroActivationMode {
  ALWAYS,
  ON_TOUCH_AIM,
  ON_LEFT_TRIGGER,
}

@Serializable
data class ControlElement(
  val id: String,
  val label: String,
  val type: ControlElementType,
  val xFraction: Float,
  val yFraction: Float,
  val sizeDp: Float = 56f,
  val opacity: Float = 0.72f,
  val bindingKey: String = "KEY_SPACE",
  val isToggle: Boolean = false,
)

@Serializable
data class GamepadBinding(
  val androidKeyCode: Int,
  val physicalLabel: String,
  val targetAction: String,
)

@Serializable
data class GyroConfig(
  val enabled: Boolean = false,
  val sensitivity: Float = 1.8f,
  val deadzoneRadPerSec: Float = 0.04f,
  val invertX: Boolean = false,
  val invertY: Boolean = false,
  val activationMode: GyroActivationMode = GyroActivationMode.ALWAYS,
)

@Serializable
data class InputProfile(
  val id: String,
  val name: String,
  val description: String,
  val mouseMode: MouseMode = MouseMode.TOUCHPAD,
  val touchpadSensitivity: Float = 1.4f,
  val elements: List<ControlElement>,
  val gamepadBindings: List<GamepadBinding> = defaultGamepadBindings(),
  val gyroConfig: GyroConfig = GyroConfig(),
) {
  companion object {
    fun defaultGamepadBindings(): List<GamepadBinding> = listOf(
      GamepadBinding(KeyEvent.KEYCODE_BUTTON_A, "A", "XINPUT_A"),
      GamepadBinding(KeyEvent.KEYCODE_BUTTON_B, "B", "XINPUT_B"),
      GamepadBinding(KeyEvent.KEYCODE_BUTTON_X, "X", "XINPUT_X"),
      GamepadBinding(KeyEvent.KEYCODE_BUTTON_Y, "Y", "XINPUT_Y"),
      GamepadBinding(KeyEvent.KEYCODE_BUTTON_L1, "LB", "XINPUT_LB"),
      GamepadBinding(KeyEvent.KEYCODE_BUTTON_R1, "RB", "XINPUT_RB"),
      GamepadBinding(KeyEvent.KEYCODE_BUTTON_START, "START", "KEY_ESC"),
      GamepadBinding(KeyEvent.KEYCODE_BUTTON_SELECT, "SELECT", "KEY_TAB"),
    )

    fun presets(): List<InputProfile> = listOf(
      InputProfile(
        id = "profile-gamepad-pro",
        name = "Handheld Console (XInput)",
        description = "Dual analog sticks, D-Pad, ABXY, triggers, and touchpad mouse fallback.",
        mouseMode = MouseMode.TOUCHPAD,
        elements = listOf(
          ControlElement("ls", "LS", ControlElementType.THUMBSTICK_LEFT, 0.12f, 0.68f, 108f, 0.68f, "WASD"),
          ControlElement("dpad", "D-PAD", ControlElementType.DPAD, 0.24f, 0.36f, 96f, 0.65f, "ARROWS"),
          ControlElement("rs", "RS", ControlElementType.THUMBSTICK_RIGHT, 0.78f, 0.68f, 104f, 0.68f, "MOUSE_LOOK"),
          ControlElement("btn_a", "A", ControlElementType.BUTTON, 0.88f, 0.56f, 52f, 0.78f, "KEY_ENTER"),
          ControlElement("btn_b", "B", ControlElementType.BUTTON, 0.93f, 0.44f, 52f, 0.78f, "KEY_ESC"),
          ControlElement("btn_x", "X", ControlElementType.BUTTON, 0.83f, 0.44f, 52f, 0.78f, "KEY_R"),
          ControlElement("btn_y", "Y", ControlElementType.BUTTON, 0.88f, 0.32f, 52f, 0.78f, "KEY_E"),
          ControlElement("btn_lb", "L1", ControlElementType.BUTTON, 0.10f, 0.16f, 56f, 0.72f, "MOUSE_RIGHT"),
          ControlElement("btn_rb", "R1", ControlElementType.BUTTON, 0.90f, 0.16f, 56f, 0.72f, "MOUSE_LEFT"),
        ),
        gyroConfig = GyroConfig(enabled = false, sensitivity = 1.6f),
      ),
      InputProfile(
        id = "profile-fps-gyro",
        name = "FPS Touch + Gyro Aim",
        description = "WASD stick, precision gyro aim, quick reload/jump, and direct fire button.",
        mouseMode = MouseMode.TOUCHPAD,
        touchpadSensitivity = 1.7f,
        elements = listOf(
          ControlElement("ls", "MOVE", ControlElementType.THUMBSTICK_LEFT, 0.14f, 0.70f, 114f, 0.70f, "WASD"),
          ControlElement("fire", "FIRE", ControlElementType.BUTTON, 0.88f, 0.64f, 68f, 0.84f, "MOUSE_LEFT"),
          ControlElement("aim", "ADS", ControlElementType.BUTTON, 0.76f, 0.68f, 54f, 0.76f, "MOUSE_RIGHT", isToggle = true),
          ControlElement("jump", "JUMP", ControlElementType.BUTTON, 0.90f, 0.42f, 52f, 0.76f, "KEY_SPACE"),
          ControlElement("reload", "R", ControlElementType.BUTTON, 0.79f, 0.48f, 48f, 0.74f, "KEY_R"),
          ControlElement("shift", "RUN", ControlElementType.BUTTON, 0.14f, 0.34f, 50f, 0.70f, "KEY_SHIFT", isToggle = true),
        ),
        gyroConfig = GyroConfig(enabled = true, sensitivity = 2.2f, deadzoneRadPerSec = 0.03f),
      ),
      InputProfile(
        id = "profile-rts-mouse",
        name = "Strategy & Direct Mouse",
        description = "Direct touch cursor with left/right click triggers and quick hotkeys.",
        mouseMode = MouseMode.DIRECT_TOUCH,
        touchpadSensitivity = 1.2f,
        elements = listOf(
          ControlElement("lmb", "LMB", ControlElementType.BUTTON, 0.10f, 0.76f, 58f, 0.76f, "MOUSE_LEFT"),
          ControlElement("rmb", "RMB", ControlElementType.BUTTON, 0.20f, 0.76f, 58f, 0.76f, "MOUSE_RIGHT"),
          ControlElement("esc", "ESC", ControlElementType.BUTTON, 0.90f, 0.20f, 50f, 0.72f, "KEY_ESC"),
          ControlElement("space", "SPACE", ControlElementType.BUTTON, 0.88f, 0.76f, 60f, 0.74f, "KEY_SPACE"),
        ),
        gyroConfig = GyroConfig(enabled = false),
      ),
    )
  }
}
