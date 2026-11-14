package com.aistudio.vortexpc.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.aistudio.vortexpc.core.model.TelemetrySnapshot
import com.aistudio.vortexpc.graphics.X11DisplayState
import com.aistudio.vortexpc.input.ControlElement
import com.aistudio.vortexpc.input.ControlElementType
import com.aistudio.vortexpc.ui.theme.LocalVortexAccents
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun VortexConsoleCard(
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  hapticsEnabled: Boolean = true,
  selected: Boolean = false,
  contentDescriptionText: String? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  var focused by remember { mutableStateOf(false) }
  val accents = LocalVortexAccents.current
  val haptics = LocalHapticFeedback.current

  val borderColor by animateColorAsState(
    targetValue = when {
      focused || selected -> accents.focusGlow
      else -> accents.hairline
    },
    animationSpec = spring(),
    label = "card_border",
  )

  Surface(
    modifier = modifier
      .minimumInteractiveComponentSize()
      .onFocusChanged { focused = it.isFocused }
      .focusable(enabled = onClick != null)
      .then(
        if (onClick != null) {
          Modifier.clickable {
            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
          }
        } else {
          Modifier
        }
      )
      .then(
        if (contentDescriptionText != null) {
          Modifier.semantics { contentDescription = contentDescriptionText }
        } else {
          Modifier
        }
      ),
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(if (focused || selected) 1.5.dp else 1.dp, borderColor),
    tonalElevation = if (selected) 4.dp else 1.dp,
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      content = content,
    )
  }
}

@Composable
fun VortexTagPill(
  text: String,
  accentColor: Color = MaterialTheme.colorScheme.primary,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(8.dp),
    color = accentColor.copy(alpha = 0.14f),
    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.42f)),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = accentColor,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
  }
}

@Composable
fun PerformanceHudOverlay(
  telemetry: TelemetrySnapshot,
  fpsCapLabel: String,
  modifier: Modifier = Modifier,
) {
  val accents = LocalVortexAccents.current
  val fpsColor = when {
    telemetry.fps >= 45f -> accents.telemetryOk
    telemetry.fps >= 24f -> accents.telemetryWarn
    else -> MaterialTheme.colorScheme.primary
  }
  val tempColor = when {
    telemetry.batteryTempC >= 43f -> accents.telemetryHot
    telemetry.batteryTempC >= 38f -> accents.telemetryWarn
    else -> accents.telemetryOk
  }

  Surface(
    modifier = modifier.testTag("performance_hud"),
    shape = RoundedCornerShape(14.dp),
    color = Color(0xCC05070B),
    border = BorderStroke(1.dp, accents.hairline),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Column {
        Text(
          text = "${telemetry.fps.roundToInt()} FPS",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = fpsColor,
        )
        Text(
          text = "${String.format("%.1f", telemetry.frameTimeMs)} ms • $fpsCapLabel",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (telemetry.fpsHistory.size >= 2) {
        Canvas(
          modifier = Modifier
            .width(56.dp)
            .height(24.dp)
        ) {
          val pts = telemetry.fpsHistory
          val maxFps = (pts.maxOrNull() ?: 60f).coerceAtLeast(60f)
          val path = Path()
          pts.forEachIndexed { idx, value ->
            val x = (idx.toFloat() / (pts.size - 1).coerceAtLeast(1)) * size.width
            val y = size.height - ((value / maxFps).coerceIn(0f, 1f) * size.height)
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
          }
          drawPath(path, color = fpsColor, style = Stroke(width = 2.dp.toPx()))
        }
      }

      HudMetricColumn("CPU", "${telemetry.cpuUsagePercent}%", MaterialTheme.colorScheme.primary)
      HudMetricColumn("GPU", "${telemetry.gpuLoadEstimatePercent}%", MaterialTheme.colorScheme.secondary)
      HudMetricColumn(
        "RAM",
        "${String.format("%.1f", telemetry.ramUsedMb / 1024f)}/${String.format("%.0f", telemetry.ramTotalMb / 1024f)}G",
        MaterialTheme.colorScheme.onSurface,
      )
      HudMetricColumn(
        "BAT",
        "${telemetry.batteryPercent}% ${String.format("%.0f", telemetry.batteryTempC)}°C",
        tempColor,
      )
    }
  }
}

@Composable
private fun HudMetricColumn(label: String, value: String, color: Color) {
  Column(horizontalAlignment = Alignment.Start) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
      color = color,
    )
  }
}

@Composable
fun X11ViewportCanvas(
  displayState: X11DisplayState,
  onFramePresented: () -> Unit,
  onTouchDrag: (dxNorm: Float, dyNorm: Float, absX: Float, absY: Float) -> Unit,
  onTouchTap: (absX: Float, absY: Float, isRightClick: Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val fpsDelayMs = remember(displayState.fpsLimit) {
    if (displayState.fpsLimit <= 0) 16L else (1000L / displayState.fpsLimit.coerceIn(15, 120))
  }

  LaunchedEffect(displayState.running, fpsDelayMs) {
    while (displayState.running) {
      onFramePresented()
      delay(fpsDelayMs)
    }
  }

  BoxWithConstraints(
    modifier = modifier
      .fillMaxSize()
      .background(
        Brush.radialGradient(
          colors = listOf(Color(0xFF0A1424), Color(0xFF030508), Color.Black),
        )
      )
      .pointerInput(displayState.displayWidth, displayState.displayHeight) {
        detectDragGestures { change, dragAmount ->
          change.consume()
          val w = size.width.toFloat().coerceAtLeast(1f)
          val h = size.height.toFloat().coerceAtLeast(1f)
          onTouchDrag(
            dragAmount.x / w,
            dragAmount.y / h,
            change.position.x / w,
            change.position.y / h,
          )
        }
      }
      .pointerInput(displayState.displayWidth, displayState.displayHeight) {
        detectTapGestures(
          onTap = { pos ->
            val w = size.width.toFloat().coerceAtLeast(1f)
            val h = size.height.toFloat().coerceAtLeast(1f)
            onTouchTap(pos.x / w, pos.y / h, false)
          },
          onLongPress = { pos ->
            val w = size.width.toFloat().coerceAtLeast(1f)
            val h = size.height.toFloat().coerceAtLeast(1f)
            onTouchTap(pos.x / w, pos.y / h, true)
          },
        )
      }
      .testTag("x11_viewport"),
  ) {
    val maxW = maxWidth
    val maxH = maxHeight

    // Subtle X11 technical grid background
    Canvas(modifier = Modifier.fillMaxSize()) {
      val step = 48.dp.toPx()
      var x = 0f
      while (x < size.width) {
        drawLine(Color(0x1400E5FF), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
      }
      var y = 0f
      while (y < size.height) {
        drawLine(Color(0x1400E5FF), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
      }
    }

    // Render active X11 application window
    val fgWindow = displayState.activeWindows.lastOrNull { !it.isDesktop }
    if (fgWindow != null) {
      Surface(
        modifier = Modifier
          .align(Alignment.Center)
          .width(maxW * 0.72f)
          .height(maxH * 0.68f),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xEB0E141F),
        border = BorderStroke(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.65f)),
        shadowElevation = 12.dp,
      ) {
        Column(modifier = Modifier.fillMaxSize()) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(Color(0xFF152236))
              .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = "X11 :0 — ${fgWindow.title}",
              style = MaterialTheme.typography.titleSmall,
              color = Color(0xFF00E5FF),
            )
            Text(
              text = "${(displayState.displayWidth * displayState.renderScale).roundToInt()}×${(displayState.displayHeight * displayState.renderScale).roundToInt()}",
              style = MaterialTheme.typography.labelSmall,
              color = Color(0xFF98A6C2),
            )
          }
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = displayState.typedBuffer.ifBlank { fgWindow.bodyLines.joinToString("\n") },
              style = MaterialTheme.typography.labelSmall.copy(
                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
              ),
              color = Color(0xFFE7EFFC),
            )
          }
        }
      }
    }

    // Render live hardware/X11 cursor reticle
    val density = LocalDensity.current
    val cursorPxX = with(density) { (maxW * displayState.cursorX).toPx().roundToInt() }
    val cursorPxY = with(density) { (maxH * displayState.cursorY).toPx().roundToInt() }
    Box(
      modifier = Modifier
        .offset { IntOffset(cursorPxX - 14, cursorPxY - 14) }
        .size(28.dp)
        .border(
          width = 2.dp,
          color = if (displayState.mouseButtonsMask != 0) Color(0xFF39FF88) else Color(0xFF00E5FF),
          shape = CircleShape,
        )
        .background(Color(0x3300E5FF), CircleShape),
    )
  }
}

@Composable
fun VirtualControlsOverlay(
  elements: List<ControlElement>,
  editMode: Boolean,
  selectedElementId: String? = null,
  onSelectElement: ((ControlElement) -> Unit)? = null,
  onMoveElement: ((id: String, newXFraction: Float, newYFraction: Float) -> Unit)? = null,
  onButtonStateChange: ((bindingKey: String, pressed: Boolean) -> Unit)? = null,
  onStickMove: ((isRight: Boolean, nx: Float, ny: Float) -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val containerW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
    val containerH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
    val density = LocalDensity.current

    elements.forEach { el ->
      val sizePx = with(density) { el.sizeDp.dp.toPx() }
      val xPx = (el.xFraction * containerW - sizePx / 2f).roundToInt()
      val yPx = (el.yFraction * containerH - sizePx / 2f).roundToInt()
      val isSelected = editMode && el.id == selectedElementId
      var pressed by remember(el.id) { mutableStateOf(false) }

      Box(
        modifier = Modifier
          .offset { IntOffset(xPx, yPx) }
          .size(el.sizeDp.dp)
          .clip(if (el.type == ControlElementType.DPAD) RoundedCornerShape(14.dp) else CircleShape)
          .background(
            when {
              isSelected -> Color(0x6600E5FF)
              pressed -> Color(0x8839FF88)
              else -> Color(0xFF0E1420).copy(alpha = el.opacity)
            }
          )
          .border(
            width = if (isSelected) 2.dp else 1.2.dp,
            color = when {
              isSelected -> Color(0xFF00E5FF)
              pressed -> Color(0xFF39FF88)
              else -> Color(0xFF4B6080)
            },
            shape = if (el.type == ControlElementType.DPAD) RoundedCornerShape(14.dp) else CircleShape,
          )
          .pointerInput(el.id, editMode) {
            if (editMode) {
              detectDragGestures(
                onDragStart = { onSelectElement?.invoke(el) },
                onDrag = { change, dragAmount ->
                  change.consume()
                  val nx = (el.xFraction + dragAmount.x / containerW).coerceIn(0.05f, 0.95f)
                  val ny = (el.yFraction + dragAmount.y / containerH).coerceIn(0.08f, 0.92f)
                  onMoveElement?.invoke(el.id, nx, ny)
                },
              )
            } else if (el.type == ControlElementType.THUMBSTICK_LEFT || el.type == ControlElementType.THUMBSTICK_RIGHT) {
              detectDragGestures(
                onDragEnd = {
                  onStickMove?.invoke(el.type == ControlElementType.THUMBSTICK_RIGHT, 0f, 0f)
                },
                onDragCancel = {
                  onStickMove?.invoke(el.type == ControlElementType.THUMBSTICK_RIGHT, 0f, 0f)
                },
                onDrag = { change, _ ->
                  change.consume()
                  val cx = sizePx / 2f
                  val cy = sizePx / 2f
                  val nx = ((change.position.x - cx) / cx).coerceIn(-1f, 1f)
                  val ny = ((change.position.y - cy) / cy).coerceIn(-1f, 1f)
                  onStickMove?.invoke(el.type == ControlElementType.THUMBSTICK_RIGHT, nx, ny)
                },
              )
            } else {
              detectTapGestures(
                onPress = {
                  pressed = true
                  onButtonStateChange?.invoke(el.bindingKey, true)
                  tryAwaitRelease()
                  pressed = false
                  onButtonStateChange?.invoke(el.bindingKey, false)
                },
              )
            }
          }
          .then(
            if (editMode) {
              Modifier.clickable { onSelectElement?.invoke(el) }
            } else {
              Modifier
            }
          )
          .testTag("virtual_ctrl_${el.id}"),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = el.label,
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
          color = Color.White,
        )
      }
    }
  }
}
