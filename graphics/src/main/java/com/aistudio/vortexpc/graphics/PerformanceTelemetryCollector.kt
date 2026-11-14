package com.aistudio.vortexpc.graphics

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.aistudio.vortexpc.core.model.TelemetrySnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Singleton
class PerformanceTelemetryCollector @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var job: Job? = null

  private val _snapshot = MutableStateFlow(TelemetrySnapshot())
  val snapshot: StateFlow<TelemetrySnapshot> = _snapshot.asStateFlow()

  private var prevCpuTotal = 0L
  private var prevCpuIdle = 0L
  private val fpsLock = Any()
  private var frameTimestampsNs = ArrayDeque<Long>(120)
  private val fpsHistory = ArrayDeque<Float>(30)

  fun recordPresentedFrame(timestampNs: Long = System.nanoTime()) {
    synchronized(fpsLock) {
      frameTimestampsNs.addLast(timestampNs)
      while (frameTimestampsNs.size > 120) {
        frameTimestampsNs.removeFirst()
      }
    }
  }

  fun startPolling() {
    if (job?.isActive == true) return
    job = scope.launch {
      while (isActive) {
        _snapshot.value = sampleNow()
        delay(500L)
      }
    }
  }

  fun stopPolling() {
    job?.cancel()
    job = null
  }

  fun sampleNow(): TelemetrySnapshot {
    val (fps, frameMs) = computeFpsAndFrameTime()
    val cpuPct = readCpuUsagePercent()
    val (usedMb, totalMb) = readMemoryUsageMb()
    val (batteryPct, batteryTemp, charging) = readBatteryInfo()
    val thermalTemp = readThermalFallbackTemp(batteryTemp)

    synchronized(fpsLock) {
      if (fpsHistory.size >= 30) fpsHistory.removeFirst()
      fpsHistory.addLast(fps)
    }

    val gpuEstimate = ((fps / 60f) * 58f + (cpuPct * 0.22f)).toInt().coerceIn(4, 98)

    return TelemetrySnapshot(
      fps = fps,
      frameTimeMs = frameMs,
      cpuUsagePercent = cpuPct,
      gpuLoadEstimatePercent = gpuEstimate,
      ramUsedMb = usedMb,
      ramTotalMb = totalMb,
      batteryPercent = batteryPct,
      batteryTempC = thermalTemp,
      isCharging = charging,
      fpsHistory = synchronized(fpsLock) { fpsHistory.toList() },
    )
  }

  private fun computeFpsAndFrameTime(): Pair<Float, Float> {
    val stamps = synchronized(fpsLock) { frameTimestampsNs.toList() }
    if (stamps.size < 2) return 0f to 0f
    val now = stamps.last()
    val windowStart = now - 1_000_000_000L
    val recent = stamps.filter { it >= windowStart }
    if (recent.size < 2) return 0f to 0f
    val spanSec = (recent.last() - recent.first()) / 1_000_000_000f
    if (spanSec <= 0.001f) return 0f to 0f
    val fps = (recent.size - 1) / spanSec
    val frameMs = 1000f / fps.coerceAtLeast(1f)
    return fps to frameMs
  }

  private fun readCpuUsagePercent(): Int {
    return runCatching {
      val line = File("/proc/stat").useLines { it.firstOrNull() } ?: return@runCatching 12
      val parts = line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
      if (parts.size < 4) return@runCatching 12
      val idle = parts[3] + parts.getOrElse(4) { 0L }
      val total = parts.sum()
      val dTotal = (total - prevCpuTotal).coerceAtLeast(1L)
      val dIdle = (idle - prevCpuIdle).coerceAtLeast(0L)
      prevCpuTotal = total
      prevCpuIdle = idle
      (((dTotal - dIdle).toFloat() / dTotal.toFloat()) * 100f).toInt().coerceIn(1, 100)
    }.getOrDefault(15)
  }

  private fun readMemoryUsageMb(): Pair<Long, Long> {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val info = ActivityManager.MemoryInfo()
    am?.getMemoryInfo(info)
    val totalMb = (info.totalMem / (1024L * 1024L)).coerceAtLeast(1024L)
    val availMb = (info.availMem / (1024L * 1024L)).coerceAtLeast(0L)
    return (totalMb - availMb).coerceAtLeast(256L) to totalMb
  }

  private fun readBatteryInfo(): Triple<Int, Float, Boolean> {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 80) ?: 80
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.coerceAtLeast(1) ?: 100
    val pct = ((level * 100f) / scale).toInt().coerceIn(0, 100)
    val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 320) ?: 320
    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    return Triple(pct, tempTenths / 10f, charging)
  }

  private fun readThermalFallbackTemp(batteryTempC: Float): Float {
    val sysTemp = runCatching {
      File("/sys/class/thermal/thermal_zone0/temp")
        .takeIf { it.exists() && it.canRead() }
        ?.readText()?.trim()?.toFloatOrNull()?.let { raw ->
          if (raw > 1000f) raw / 1000f else raw
        }
    }.getOrNull()
    return sysTemp?.takeIf { it in 15f..110f } ?: batteryTempC
  }
}
