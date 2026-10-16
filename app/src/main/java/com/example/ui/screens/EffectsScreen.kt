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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.StudioFader
import com.example.ui.components.StudioKnob
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonCyanDark
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonPurpleBright
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextLight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.viewmodel.StudioViewModel

@Composable
fun EffectsScreen(
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    val engine = viewModel.audioEngine
    val vocalVol by viewModel.vocalVolume.collectAsState()
    val beatVol by viewModel.beatVolume.collectAsState()

    // Reverb local reactive states
    var reverbEnabled by remember { mutableStateOf(engine.reverb.isEnabled) }
    var reverbRoom by remember { mutableFloatStateOf(engine.reverb.roomSize) }
    var reverbDamp by remember { mutableFloatStateOf(engine.reverb.damping) }
    var reverbWet by remember { mutableFloatStateOf(engine.reverb.wetMix) }

    // Delay local reactive states
    var delayEnabled by remember { mutableStateOf(engine.delay.isEnabled) }
    var delayTime by remember { mutableFloatStateOf(engine.delay.delayTimeMs) }
    var delayFeedback by remember { mutableFloatStateOf(engine.delay.feedback) }
    var delayWet by remember { mutableFloatStateOf(engine.delay.wetMix) }

    // EQ local reactive states
    var eqEnabled by remember { mutableStateOf(engine.equalizer.isEnabled) }
    var eqLow by remember { mutableFloatStateOf(engine.equalizer.lowGainDb) }
    var eqMid by remember { mutableFloatStateOf(engine.equalizer.midGainDb) }
    var eqHigh by remember { mutableFloatStateOf(engine.equalizer.highGainDb) }

    // Compressor local reactive states
    var compEnabled by remember { mutableStateOf(engine.compressor.isEnabled) }
    var compThresh by remember { mutableFloatStateOf(engine.compressor.thresholdDb) }
    var compRatio by remember { mutableFloatStateOf(engine.compressor.ratio) }
    var compGain by remember { mutableFloatStateOf(engine.compressor.makeupGainDb) }

    // Doubler local reactive states
    var doublerEnabled by remember { mutableStateOf(engine.doubler.isEnabled) }
    var doublerWidth by remember { mutableFloatStateOf(engine.doubler.width) }
    var doublerDetune by remember { mutableFloatStateOf(engine.doubler.detuneCents) }

    // Pitch Shift
    var pitchShiftSemitones by remember { mutableIntStateOf(engine.manualPitchShiftSemitones) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("effects_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Column {
            Text(
                text = "DSP VOCAL EFFECTS RACK",
                color = NeonCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp
            )
            Text(
                text = "Local Studio-Grade Audio Processing",
                color = TextMuted,
                fontSize = 11.sp
            )
        }

        // ==========================================
        // STUDIO MIXER FADERS (Vocal, Beat, Master)
        // ==========================================
        Card(
            colors = CardDefaults.cardColors(containerColor = StudioCard),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "MIXER CHANNELS",
                    color = TextLight,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                StudioFader(
                    label = "Vocal Channel Level",
                    value = vocalVol,
                    onValueChange = { viewModel.setVocalVolume(it) },
                    valueDisplay = "${(vocalVol * 100).toInt()}%",
                    activeColor = NeonCyan,
                    valueRange = 0.0f..1.5f,
                    testTag = "fader_vocal_level"
                )

                StudioFader(
                    label = "Beat Channel Level",
                    value = beatVol,
                    onValueChange = { viewModel.setBeatVolume(it) },
                    valueDisplay = "${(beatVol * 100).toInt()}%",
                    activeColor = NeonPurpleBright,
                    valueRange = 0.0f..1.5f,
                    testTag = "fader_beat_level"
                )
            }
        }

        // ==========================================
        // 1. STUDIO REVERB
        // ==========================================
        EffectRackCard(
            title = "STUDIO REVERB",
            subtitle = "Schroeder / Freeverb algorithmic room & hall acoustics",
            icon = Icons.Default.SurroundSound,
            isEnabled = reverbEnabled,
            onToggleEnabled = {
                reverbEnabled = it
                engine.reverb.isEnabled = it
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StudioKnob(
                    label = "ROOM SIZE",
                    value = reverbRoom,
                    onValueChange = {
                        reverbRoom = it
                        engine.reverb.roomSize = it
                    },
                    valueDisplay = "${(reverbRoom * 100).toInt()}%",
                    activeColor = NeonCyan,
                    testTag = "reverb_room_knob"
                )

                StudioKnob(
                    label = "DAMPING",
                    value = reverbDamp,
                    onValueChange = {
                        reverbDamp = it
                        engine.reverb.damping = it
                    },
                    valueDisplay = "${(reverbDamp * 100).toInt()}%",
                    activeColor = NeonPurpleBright,
                    testTag = "reverb_damp_knob"
                )

                StudioKnob(
                    label = "WET MIX",
                    value = reverbWet,
                    onValueChange = {
                        reverbWet = it
                        engine.reverb.wetMix = it
                    },
                    valueDisplay = "${(reverbWet * 100).toInt()}%",
                    activeColor = NeonPink,
                    testTag = "reverb_wet_knob"
                )
            }
        }

        // ==========================================
        // 2. ECHO / DELAY
        // ==========================================
        EffectRackCard(
            title = "ECHO & DELAY",
            subtitle = "Analog feedback delay line with high-cut damping",
            icon = Icons.Default.Waves,
            isEnabled = delayEnabled,
            onToggleEnabled = {
                delayEnabled = it
                engine.delay.isEnabled = it
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StudioKnob(
                    label = "TIME",
                    value = (delayTime - 50f) / 750f,
                    onValueChange = {
                        val ms = 50f + (it * 750f)
                        delayTime = ms
                        engine.delay.delayTimeMs = ms
                    },
                    valueDisplay = "${delayTime.toInt()} ms",
                    activeColor = NeonCyan,
                    testTag = "delay_time_knob"
                )

                StudioKnob(
                    label = "FEEDBACK",
                    value = delayFeedback / 0.85f,
                    onValueChange = {
                        val fb = it * 0.85f
                        delayFeedback = fb
                        engine.delay.feedback = fb
                    },
                    valueDisplay = "${(delayFeedback * 100).toInt()}%",
                    activeColor = NeonPurpleBright,
                    testTag = "delay_fb_knob"
                )

                StudioKnob(
                    label = "WET MIX",
                    value = delayWet,
                    onValueChange = {
                        delayWet = it
                        engine.delay.wetMix = it
                    },
                    valueDisplay = "${(delayWet * 100).toInt()}%",
                    activeColor = NeonPink,
                    testTag = "delay_wet_knob"
                )
            }
        }

        // ==========================================
        // 3. 3-BAND PARAMETRIC EQUALIZER
        // ==========================================
        EffectRackCard(
            title = "3-BAND EQUALIZER",
            subtitle = "Low Shelf (200Hz), Presence Bell (2.5kHz), Air Shelf (8kHz)",
            icon = Icons.Default.Equalizer,
            isEnabled = eqEnabled,
            onToggleEnabled = {
                eqEnabled = it
                engine.equalizer.isEnabled = it
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StudioKnob(
                    label = "BASS (200Hz)",
                    value = (eqLow + 12f) / 24f,
                    onValueChange = {
                        val db = -12f + (it * 24f)
                        eqLow = db
                        engine.equalizer.lowGainDb = db
                        engine.equalizer.updateFilters()
                    },
                    valueDisplay = String.format(java.util.Locale.US, "%+.1f dB", eqLow),
                    activeColor = NeonCyan,
                    testTag = "eq_low_knob"
                )

                StudioKnob(
                    label = "MID (2.5kHz)",
                    value = (eqMid + 12f) / 24f,
                    onValueChange = {
                        val db = -12f + (it * 24f)
                        eqMid = db
                        engine.equalizer.midGainDb = db
                        engine.equalizer.updateFilters()
                    },
                    valueDisplay = String.format(java.util.Locale.US, "%+.1f dB", eqMid),
                    activeColor = NeonPurpleBright,
                    testTag = "eq_mid_knob"
                )

                StudioKnob(
                    label = "AIR (8kHz)",
                    value = (eqHigh + 12f) / 24f,
                    onValueChange = {
                        val db = -12f + (it * 24f)
                        eqHigh = db
                        engine.equalizer.highGainDb = db
                        engine.equalizer.updateFilters()
                    },
                    valueDisplay = String.format(java.util.Locale.US, "%+.1f dB", eqHigh),
                    activeColor = NeonPink,
                    testTag = "eq_high_knob"
                )
            }
        }

        // ==========================================
        // 4. STUDIO COMPRESSOR
        // ==========================================
        EffectRackCard(
            title = "DYNAMIC COMPRESSOR",
            subtitle = "Punchy dynamic range control with peak envelope follower",
            icon = Icons.Default.GraphicEq,
            isEnabled = compEnabled,
            onToggleEnabled = {
                compEnabled = it
                engine.compressor.isEnabled = it
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StudioKnob(
                    label = "THRESHOLD",
                    value = (compThresh + 40f) / 40f,
                    onValueChange = {
                        val db = -40f + (it * 40f)
                        compThresh = db
                        engine.compressor.thresholdDb = db
                    },
                    valueDisplay = "${compThresh.toInt()} dB",
                    activeColor = NeonCyan,
                    testTag = "comp_thresh_knob"
                )

                StudioKnob(
                    label = "RATIO",
                    value = (compRatio - 1f) / 19f,
                    onValueChange = {
                        val r = 1f + (it * 19f)
                        compRatio = r
                        engine.compressor.ratio = r
                    },
                    valueDisplay = "${compRatio.toInt()}:1",
                    activeColor = NeonPurpleBright,
                    testTag = "comp_ratio_knob"
                )

                StudioKnob(
                    label = "GAIN BOOST",
                    value = compGain / 15f,
                    onValueChange = {
                        val g = it * 15f
                        compGain = g
                        engine.compressor.makeupGainDb = g
                    },
                    valueDisplay = String.format(java.util.Locale.US, "+%.1f dB", compGain),
                    activeColor = NeonPink,
                    testTag = "comp_gain_knob"
                )
            }
        }

        // ==========================================
        // 5. VOCAL DOUBLER
        // ==========================================
        EffectRackCard(
            title = "VOCAL DOUBLER",
            subtitle = "Micro-pitch detuned delay for wide stereo vocal presence",
            icon = Icons.Default.MusicNote,
            isEnabled = doublerEnabled,
            onToggleEnabled = {
                doublerEnabled = it
                engine.doubler.isEnabled = it
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StudioKnob(
                    label = "STEREO WIDTH",
                    value = doublerWidth,
                    onValueChange = {
                        doublerWidth = it
                        engine.doubler.width = it
                    },
                    valueDisplay = "${(doublerWidth * 100).toInt()}%",
                    activeColor = NeonCyan,
                    testTag = "doubler_width_knob"
                )

                StudioKnob(
                    label = "DETUNE",
                    value = (doublerDetune - 2f) / 18f,
                    onValueChange = {
                        val cents = 2f + (it * 18f)
                        doublerDetune = cents
                        engine.doubler.detuneCents = cents
                    },
                    valueDisplay = "${doublerDetune.toInt()} cents",
                    activeColor = NeonPurpleBright,
                    testTag = "doubler_detune_knob"
                )
            }
        }

        // ==========================================
        // 6. MANUAL PITCH SHIFT (TRANSPOSE)
        // ==========================================
        Card(
            colors = CardDefaults.cardColors(containerColor = StudioCard),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "MANUAL PITCH SHIFT",
                            color = TextWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Transpose vocal octave / semitones (-12 to +12)",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    IconButton(
                        onClick = {
                            pitchShiftSemitones = 0
                            viewModel.setManualPitchShift(0)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Pitch Shift",
                            tint = NeonCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                StudioFader(
                    label = "Transpose Semitones",
                    value = pitchShiftSemitones.toFloat(),
                    onValueChange = {
                        val semi = it.toInt()
                        pitchShiftSemitones = semi
                        viewModel.setManualPitchShift(semi)
                    },
                    valueDisplay = if (pitchShiftSemitones == 0) "Normal (0 st)" else String.format(java.util.Locale.US, "%+d st", pitchShiftSemitones),
                    activeColor = NeonCyan,
                    valueRange = -12f..12f,
                    testTag = "fader_pitch_shift"
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun EffectRackCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isEnabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) StudioCard else StudioCard.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (isEnabled) NeonCyan else TextDim,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = title,
                            color = if (isEnabled) TextWhite else TextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = subtitle,
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextWhite,
                        checkedTrackColor = NeonPurple,
                        uncheckedThumbColor = TextDim,
                        uncheckedTrackColor = StudioSurface
                    )
                )
            }

            if (isEnabled) {
                Spacer(modifier = Modifier.height(14.dp))
                content()
            }
        }
    }
}
