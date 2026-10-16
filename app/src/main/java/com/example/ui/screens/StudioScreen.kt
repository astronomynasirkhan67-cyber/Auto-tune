package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HeadphonesBattery
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.RecordingState
import com.example.dsp.MusicalScale
import com.example.dsp.ScaleType
import com.example.dsp.VocalPresets
import com.example.ui.components.NeonVuMeter
import com.example.ui.components.StudioFader
import com.example.ui.components.StudioKnob
import com.example.ui.components.WaveformVisualizer
import com.example.ui.theme.ActiveGreen
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonCyanDark
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonPurpleBright
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.RecordRed
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextLight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.viewmodel.StudioTab
import com.example.viewmodel.StudioViewModel

@Composable
fun StudioScreen(
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    val audioMetrics by viewModel.audioEngine.audioMetrics.collectAsState()
    val pitchFeedback by viewModel.audioEngine.pitchFeedback.collectAsState()
    val recordingState by viewModel.audioEngine.recordingState.collectAsState()
    val isHardAutoTune by viewModel.isHardAutoTune.collectAsState()
    val rootKeyIndex by viewModel.rootKeyIndex.collectAsState()
    val scaleType by viewModel.scaleType.collectAsState()
    val correctionStrength by viewModel.correctionStrength.collectAsState()
    val pitchSpeed by viewModel.pitchSpeed.collectAsState()
    val isLiveMonitorEnabled by viewModel.isLiveMonitorEnabled.collectAsState()
    val activePresetName by viewModel.activePresetName.collectAsState()
    val selectedBeat by viewModel.selectedBeat.collectAsState()
    val beatVolume by viewModel.beatVolume.collectAsState()

    var scaleMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("studio_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Toolbar: Monitor switch & Mic Meter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "LIVE AUTO-TUNE",
                    color = NeonCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "Studio Vocal Tracking",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            // Live Mic Monitor Toggle
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isLiveMonitorEnabled) NeonCyan.copy(alpha = 0.2f) else StudioCard)
                    .border(1.dp, if (isLiveMonitorEnabled) NeonCyan else StudioBorder, RoundedCornerShape(20.dp))
                    .clickable { viewModel.toggleLiveMonitor() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("toggle_live_monitor")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = "Monitor",
                        tint = if (isLiveMonitorEnabled) NeonCyan else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isLiveMonitorEnabled) "Monitor ON" else "Monitor OFF",
                        color = if (isLiveMonitorEnabled) NeonCyan else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Live Audio VU Meter
        NeonVuMeter(
            peak = audioMetrics.peakAmplitude,
            modifier = Modifier.fillMaxWidth(),
            height = 6.dp
        )

        // Waveform & Pitch Cent Visualizer
        WaveformVisualizer(
            audioMetrics = audioMetrics,
            pitchFeedback = pitchFeedback,
            isRecording = recordingState == RecordingState.RECORDING
        )

        // ==========================================
        // KEY & SCALE SELECTOR
        // ==========================================
        Card(
            colors = CardDefaults.cardColors(containerColor = StudioCard),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MUSICAL KEY & SCALE",
                        color = TextLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    // Scale Type Selector
                    Box {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(StudioSurface)
                                .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
                                .clickable { scaleMenuExpanded = true }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                .testTag("scale_selector_btn")
                        ) {
                            Text(
                                text = "${scaleType.displayName} ▼",
                                color = NeonCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        DropdownMenu(
                            expanded = scaleMenuExpanded,
                            onDismissRequest = { scaleMenuExpanded = false },
                            modifier = Modifier.background(StudioCardElevated)
                        ) {
                            ScaleType.values().forEach { scale ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = scale.displayName,
                                            color = if (scale == scaleType) NeonCyan else TextWhite,
                                            fontWeight = if (scale == scaleType) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        viewModel.setScaleType(scale)
                                        scaleMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Root Note Selector Pills (C, C#, D, D#, E, etc.)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(MusicalScale.ROOT_NOTES) { idx, note ->
                        val isSelected = idx == rootKeyIndex
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) Brush.verticalGradient(listOf(NeonPurple, NeonCyanDark))
                                    else Brush.verticalGradient(listOf(StudioSurface, StudioCardElevated))
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) NeonCyan else StudioBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { viewModel.setRootKey(idx) }
                                .testTag("key_note_$note")
                        ) {
                            Text(
                                text = note,
                                color = if (isSelected) TextWhite else TextLight,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // AUTOTUNE ENGINE CONTROLS
        // ==========================================
        Card(
            colors = CardDefaults.cardColors(containerColor = StudioCard),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AUTOTUNE MODE",
                        color = TextLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    // Hard vs Natural Toggle Switch
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(StudioSurface)
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (isHardAutoTune) NeonPurple else Color.Transparent)
                                .clickable { viewModel.setAutoTuneMode(true) }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                .testTag("mode_hard_autotune")
                        ) {
                            Text(
                                text = "Hard Snap",
                                color = if (isHardAutoTune) TextWhite else TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (!isHardAutoTune) NeonCyanDark else Color.Transparent)
                                .clickable { viewModel.setAutoTuneMode(false) }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                .testTag("mode_natural_autotune")
                        ) {
                            Text(
                                text = "Natural",
                                color = if (!isHardAutoTune) TextWhite else TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Rotary Knobs: Correction Strength & Pitch Speed
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StudioKnob(
                        label = "STRENGTH",
                        value = correctionStrength,
                        onValueChange = { viewModel.setCorrectionStrength(it) },
                        valueDisplay = "${(correctionStrength * 100).toInt()}%",
                        activeColor = NeonCyan,
                        size = 72.dp,
                        testTag = "knob_strength"
                    )

                    StudioKnob(
                        label = "PITCH SPEED",
                        value = pitchSpeed,
                        onValueChange = { viewModel.setPitchSpeed(it) },
                        valueDisplay = if (pitchSpeed >= 0.95f) "0ms (Robot)" else "${((1f - pitchSpeed) * 80).toInt()}ms",
                        activeColor = NeonPurpleBright,
                        size = 72.dp,
                        testTag = "knob_speed"
                    )
                }
            }
        }

        // ==========================================
        // VOCAL PRESETS CAROUSEL
        // ==========================================
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "VOCAL PRESETS",
                color = TextLight,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(VocalPresets.ALL_PRESETS) { preset ->
                    val isSelected = preset.name == activePresetName
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) NeonPurple.copy(alpha = 0.25f) else StudioCard
                            )
                            .border(
                                1.dp,
                                if (isSelected) NeonPurpleBright else StudioBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { viewModel.applyPreset(preset) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("preset_${preset.name.replace(' ', '_')}")
                    ) {
                        Column {
                            Text(
                                text = preset.name,
                                color = if (isSelected) NeonPurpleBright else TextWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (preset.isHardAutoTune) "Hard • ${preset.name.split(' ').first()}" else "Natural",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // INSTRUMENTAL BEAT CARD
        // ==========================================
        Card(
            colors = CardDefaults.cardColors(containerColor = StudioCard),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LibraryMusic,
                            contentDescription = "Beat",
                            tint = NeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = selectedBeat?.title ?: "No Beat Selected",
                                color = TextWhite,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = selectedBeat?.let { "${it.genre.displayName} • ${it.bpm} BPM • Key ${it.rootKey}" } ?: "Select an instrumental beat",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.setTab(StudioTab.BEATS) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("change_beat_btn")
                    ) {
                        Text("Change", fontSize = 11.sp, color = NeonCyan)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Beat Volume Fader
                StudioFader(
                    label = "Beat Volume",
                    value = beatVolume,
                    onValueChange = { viewModel.setBeatVolume(it) },
                    valueDisplay = "${(beatVolume * 100).toInt()}%",
                    activeColor = NeonCyanDark,
                    valueRange = 0.0f..1.5f,
                    testTag = "fader_beat_volume"
                )
            }
        }

        // ==========================================
        // STUDIO RECORDING CONTROL BAR
        // ==========================================
        Card(
            colors = CardDefaults.cardColors(containerColor = StudioCardElevated),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Recording Timer Display (00:00.0)
                val totalMs = audioMetrics.recordingDurationMs
                val minutes = (totalMs / 1000) / 60
                val seconds = (totalMs / 1000) % 60
                val tenths = (totalMs % 1000) / 100
                val timerString = String.format(java.util.Locale.US, "%02d:%02d.%01d", minutes, seconds, tenths)

                Text(
                    text = timerString,
                    color = if (recordingState == RecordingState.RECORDING) RecordRed else TextWhite,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.testTag("recording_timer")
                )

                Text(
                    text = when (recordingState) {
                        RecordingState.RECORDING -> "● RECORDING LIVE"
                        RecordingState.PAUSED -> "❚❚ PAUSED"
                        else -> "READY TO RECORD"
                    },
                    color = when (recordingState) {
                        RecordingState.RECORDING -> RecordRed
                        RecordingState.PAUSED -> NeonPurpleBright
                        else -> TextMuted
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Transport Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (recordingState == RecordingState.IDLE) {
                        // Start Record Button
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(74.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(RecordRed, Color(0xFFB00020))
                                    )
                                )
                                .border(3.dp, RecordRed.copy(alpha = 0.5f), CircleShape)
                                .clickable { viewModel.startRecording() }
                                .testTag("record_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FiberManualRecord,
                                contentDescription = "Record",
                                tint = TextWhite,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    } else {
                        // Pause / Resume Button
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(StudioSurface)
                                .border(1.dp, StudioBorder, CircleShape)
                                .clickable {
                                    if (recordingState == RecordingState.RECORDING) {
                                        viewModel.pauseRecording()
                                    } else {
                                        viewModel.resumeRecording()
                                    }
                                }
                                .testTag("pause_resume_button")
                        ) {
                            Icon(
                                imageVector = if (recordingState == RecordingState.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = "Pause/Resume",
                                tint = TextWhite,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Big Stop & Save Button
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(74.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(NeonPurple, NeonViolet)
                                    )
                                )
                                .border(3.dp, NeonPurpleBright, CircleShape)
                                .clickable { viewModel.stopRecording() }
                                .testTag("stop_recording_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop",
                                tint = TextWhite,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}
