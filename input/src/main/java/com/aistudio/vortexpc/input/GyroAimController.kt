package com.aistudio.vortexpc.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class GyroAimController @Inject constructor(
  @ApplicationContext private val context: Context,
) : SensorEventListener {

  private val sensorManager: SensorManager? =
    context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
  private val gyroSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

  private var config = GyroConfig()
  private var onDelta: ((Float, Float) -> Unit)? = null
  private var lastTimestampNs = 0L
  private var registered = false

  val isHardwareSupported: Boolean get() = gyroSensor != null

  fun start(gyroConfig: GyroConfig, onAimDelta: (dxNorm: Float, dyNorm: Float) -> Unit) {
    config = gyroConfig
    onDelta = onAimDelta
    if (!gyroConfig.enabled || gyroSensor == null) {
      stop()
      return
    }
    if (!registered) {
      lastTimestampNs = 0L
      sensorManager?.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_GAME)
      registered = true
    }
  }

  fun updateConfig(gyroConfig: GyroConfig) {
    val wasEnabled = config.enabled
    config = gyroConfig
    if (!gyroConfig.enabled) {
      stop()
    } else if (!wasEnabled && onDelta != null) {
      start(gyroConfig, onDelta!!)
    }
  }

  fun stop() {
    if (registered) {
      sensorManager?.unregisterListener(this)
      registered = false
    }
    lastTimestampNs = 0L
  }

  override fun onSensorChanged(event: SensorEvent) {
    if (!config.enabled || event.sensor.type != Sensor.TYPE_GYROSCOPE) return
    if (lastTimestampNs == 0L) {
      lastTimestampNs = event.timestamp
      return
    }
    val dt = ((event.timestamp - lastTimestampNs) / 1_000_000_000f).coerceIn(0f, 0.05f)
    lastTimestampNs = event.timestamp

    val (dx, dy) = computeFilteredDelta(
      omegaX = event.values[0],
      omegaY = event.values[1],
      omegaZ = event.values[2],
      dtSec = dt,
      cfg = config,
    )
    if (dx != 0f || dy != 0f) {
      onDelta?.invoke(dx, dy)
    }
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

  companion object {
    fun computeFilteredDelta(
      omegaX: Float,
      omegaY: Float,
      omegaZ: Float,
      dtSec: Float,
      cfg: GyroConfig,
    ): Pair<Float, Float> {
      val yaw = if (abs(omegaZ) > cfg.deadzoneRadPerSec) omegaZ else if (abs(omegaY) > cfg.deadzoneRadPerSec) omegaY else 0f
      val pitch = if (abs(omegaX) > cfg.deadzoneRadPerSec) omegaX else 0f
      val signX = if (cfg.invertX) 1f else -1f
      val signY = if (cfg.invertY) 1f else -1f
      val scale = cfg.sensitivity * 0.08f * dtSec
      return (yaw * signX * scale) to (pitch * signY * scale)
    }
  }
}
