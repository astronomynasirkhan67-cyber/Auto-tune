package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MeterHigh
import com.example.ui.theme.MeterLow
import com.example.ui.theme.MeterMid
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonPurpleBright
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun StudioKnob(
    label: String,
    value: Float, // 0.0f to 1.0f
    onValueChange: (Float) -> Unit,
    valueDisplay: String,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    activeColor: Color = NeonCyan,
    testTag: String = "studio_knob"
) {
    var dragProgress by remember(value) { mutableFloatStateOf(value) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.testTag(testTag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        // Drag up/right increases, drag down/left decreases
                        val delta = (-dragAmount.y + dragAmount.x) * 0.006f
                        val newVal = (dragProgress + delta).coerceIn(0.0f, 1.0f)
                        dragProgress = newVal
                        onValueChange(newVal)
                    }
                }
        ) {
            Canvas(modifier = Modifier.size(size)) {
                val strokeWidth = 5.dp.toPx()
                val radius = (this.size.minDimension - strokeWidth) / 2f
                val centerOffset = Offset(this.size.width / 2f, this.size.height / 2f)

                // Background track arc (from 135 deg to 405 deg = 270 deg sweep)
                val startAngle = 135f
                val totalSweep = 270f

                drawArc(
                    color = StudioBorder,
                    startAngle = startAngle,
                    sweepAngle = totalSweep,
                    useCenter = false,
                    topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Active value arc
                val currentSweep = totalSweep * value.coerceIn(0f, 1f)
                if (currentSweep > 0f) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(NeonPurple, activeColor, NeonPurpleBright)
                        ),
                        startAngle = startAngle,
                        sweepAngle = currentSweep,
                        useCenter = false,
                        topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                // Inner knob disc
                val innerRadius = radius * 0.72f
                drawCircle(
                    color = StudioCardElevated,
                    radius = innerRadius,
                    center = centerOffset
                )

                // Indicator needle/dot
                val angleRad = (startAngle + currentSweep) * (PI / 180.0)
                val needleDistance = innerRadius * 0.68f
                val dotX = centerOffset.x + (needleDistance * cos(angleRad)).toFloat()
                val dotY = centerOffset.y + (needleDistance * sin(angleRad)).toFloat()

                drawCircle(
                    color = activeColor,
                    radius = 3.5.dp.toPx(),
                    center = Offset(dotX, dotY)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = valueDisplay,
            color = activeColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun StudioFader(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueDisplay: String,
    modifier: Modifier = Modifier,
    activeColor: Color = NeonCyan,
    valueRange: ClosedFloatingPointRange<Float> = 0.0f..1.0f,
    testTag: String = "studio_fader"
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = TextWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = valueDisplay,
                color = activeColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = activeColor,
                activeTrackColor = activeColor,
                inactiveTrackColor = StudioBorder
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun NeonVuMeter(
    peak: Float, // 0.0f to 1.0f
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    testTag: String = "vu_meter"
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(StudioSurface)
            .testTag(testTag)
    ) {
        val clampedPeak = peak.coerceIn(0.0f, 1.0f)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(clampedPeak)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(MeterLow, MeterMid, MeterHigh)
                    )
                )
        )
    }
}
