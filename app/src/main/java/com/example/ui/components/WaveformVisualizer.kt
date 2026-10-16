package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.LiveAudioMetrics
import com.example.audio.PitchFeedback
import com.example.ui.theme.ActiveGreen
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonCyanDark
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonPurpleBright
import com.example.ui.theme.RecordRed
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextLight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun WaveformVisualizer(
    audioMetrics: LiveAudioMetrics,
    pitchFeedback: PitchFeedback,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_anim")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(StudioCard)
            .border(1.dp, StudioBorder, RoundedCornerShape(18.dp))
            .testTag("waveform_visualizer")
    ) {
        // Grid lines background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val midY = h / 2f

            // Center baseline
            drawLine(
                color = StudioBorder.copy(alpha = 0.6f),
                start = Offset(0f, midY),
                end = Offset(w, midY),
                strokeWidth = 1.dp.toPx()
            )

            // Dynamic Live Waveform Curve
            val points = audioMetrics.waveformPoints
            if (points.isNotEmpty()) {
                val path = Path()
                val stepX = w / (points.size - 1).coerceAtLeast(1)

                for (i in points.indices) {
                    val normVal = points[i]
                    // Alternate positive/negative for realistic audio oscilloscope look
                    val sign = if (i % 2 == 0) 1.0f else -1.0f
                    val y = midY - (sign * normVal * (midY * 0.85f))
                    val x = i * stepX

                    if (i == 0) {
                        path.moveTo(x, y)
                    } else {
                        val prevX = (i - 1) * stepX
                        val prevVal = points[i - 1]
                        val prevSign = if ((i - 1) % 2 == 0) 1.0f else -1.0f
                        val prevY = midY - (prevSign * prevVal * (midY * 0.85f))
                        val cx = (prevX + x) / 2f
                        path.quadraticTo(prevX, prevY, cx, (prevY + y) / 2f)
                    }
                }

                // Draw glow halo
                drawPath(
                    path = path,
                    brush = Brush.horizontalGradient(
                        listOf(NeonPurple.copy(alpha = 0.35f), NeonCyan.copy(alpha = 0.5f), NeonPurpleBright.copy(alpha = 0.35f))
                    ),
                    style = Stroke(
                        width = 8.dp.toPx() * pulseGlow,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Draw crisp foreground waveform
                drawPath(
                    path = path,
                    brush = Brush.horizontalGradient(
                        listOf(NeonPurpleBright, NeonCyan, NeonPink)
                    ),
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }

        // Overlay Pitch & Cent HUD Display
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Note Badge & Frequency
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Detected Note Badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (pitchFeedback.noteName != "--") NeonCyan.copy(alpha = 0.2f)
                                else StudioSurface
                            )
                            .border(
                                1.dp,
                                if (pitchFeedback.noteName != "--") NeonCyan else StudioBorder,
                                RoundedCornerShape(12.dp)
                            )
                    ) {
                        Text(
                            text = pitchFeedback.noteName,
                            color = if (pitchFeedback.noteName != "--") NeonCyan else TextDim,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = if (pitchFeedback.detectedPitchHz > 40.0) {
                                String.format(java.util.Locale.US, "%.1f Hz", pitchFeedback.detectedPitchHz)
                            } else {
                                "Listening..."
                            },
                            color = TextWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (pitchFeedback.noteName != "--") "Auto-Tuned to ${pitchFeedback.noteName}" else "Sing into microphone",
                            color = if (pitchFeedback.noteName != "--") NeonPurpleBright else TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                // Status Badge (Live Lock / Recording)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isRecording) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(RecordRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "REC",
                            color = RecordRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    if (pitchFeedback.confidence > 0.45f) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ActiveGreen.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "LOCKED",
                                color = ActiveGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            // Bottom Cent Deviation Meter (-50 cents to +50 cents)
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "-50♭", color = TextDim, fontSize = 9.sp)
                    Text(
                        text = if (pitchFeedback.noteName != "--") {
                            val cents = pitchFeedback.centsDiff
                            val sign = if (cents >= 0) "+" else ""
                            String.format(java.util.Locale.US, "%s%.1f cents", sign, cents)
                        } else {
                            "0 cents"
                        },
                        color = if (kotlin.math.abs(pitchFeedback.centsDiff) < 10.0 && pitchFeedback.noteName != "--") ActiveGreen else TextLight,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = "+50♯", color = TextDim, fontSize = 9.sp)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Cent deviation bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(StudioSurface)
                ) {
                    // Center marker
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(StudioBorder)
                    )

                    // Target indicator dot
                    if (pitchFeedback.noteName != "--") {
                        val norm = ((pitchFeedback.centsDiff.coerceIn(-50.0, 50.0) + 50.0) / 100.0).toFloat()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(norm)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(NeonCyan)
                            )
                        }
                    }
                }
            }
        }
    }
}
