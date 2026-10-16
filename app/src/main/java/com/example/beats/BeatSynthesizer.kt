package com.example.beats

import android.content.Context
import com.example.audio.WavAudioUtil
import java.io.File
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

object BeatSynthesizer {

    private val BUILTIN_BEATS = listOf(
        StudioBeat(
            id = "hiphop_midnight",
            title = "Midnight Boom-Bap",
            genre = BeatGenre.HIP_HOP,
            bpm = 90,
            rootKey = "D",
            scaleName = "Minor",
            durationSeconds = 21,
            description = "Crisp vintage drums, warm electric piano chords, and deep groove bass"
        ),
        StudioBeat(
            id = "rap_808_drill",
            title = "808 Drill Anthem",
            genre = BeatGenre.RAP,
            bpm = 140,
            rootKey = "F#",
            scaleName = "Minor",
            durationSeconds = 27,
            description = "Aggressive gliding 808 sub bass, rapid rolling hi-hats, and drill snare"
        ),
        StudioBeat(
            id = "punjabi_dhol_fire",
            title = "Desi Dhol Fire",
            genre = BeatGenre.PUNJABI,
            bpm = 98,
            rootKey = "C",
            scaleName = "Major",
            durationSeconds = 20,
            description = "Energetic traditional dhol rhythm, high slap groove, and folk melody hook"
        ),
        StudioBeat(
            id = "trap_neon_savage",
            title = "Neon Savage",
            genre = BeatGenre.TRAP,
            bpm = 130,
            rootKey = "A",
            scaleName = "Minor",
            durationSeconds = 29,
            description = "Heavy distorted 808 kick, crisp snappy claps, and hypnotic trap bells"
        ),
        StudioBeat(
            id = "lofi_chill_cafe",
            title = "Chill Coffeehouse",
            genre = BeatGenre.LO_FI,
            bpm = 78,
            rootKey = "G",
            scaleName = "Major",
            durationSeconds = 24,
            description = "Mellow warm Rhodes chords, gentle tape flutter, and relaxed rimshot"
        ),
        StudioBeat(
            id = "romantic_soul",
            title = "Acoustic Soul",
            genre = BeatGenre.ROMANTIC,
            bpm = 84,
            rootKey = "E",
            scaleName = "Major",
            durationSeconds = 23,
            description = "Soulful melodic guitar chords, soft finger snaps, and warm acoustic warmth"
        ),
        StudioBeat(
            id = "sad_rainy_piano",
            title = "Rainy Melancholy",
            genre = BeatGenre.SAD,
            bpm = 72,
            rootKey = "C",
            scaleName = "Minor",
            durationSeconds = 26,
            description = "Emotional reverberant minor piano chords, deep cello sub pulse, and slow ambient groove"
        )
    )

    fun getBuiltinBeats(): List<StudioBeat> = BUILTIN_BEATS

    /**
     * Ensures beat WAV file is generated in app cache and returns the File.
     */
    fun getOrGenerateBeatFile(context: Context, beat: StudioBeat): File {
        val beatsDir = File(context.cacheDir, "beats").apply { mkdirs() }
        val targetFile = File(beatsDir, "${beat.id}.wav")

        if (targetFile.exists() && targetFile.length() > 1000) {
            return targetFile
        }

        // Synthesize audio
        val sampleRate = 44100
        val audioShorts = synthesizeBeat(beat, sampleRate)
        WavAudioUtil.saveShortsToWav(audioShorts, targetFile, sampleRate, 1)
        return targetFile
    }

    private fun synthesizeBeat(beat: StudioBeat, sampleRate: Int): ShortArray {
        val bpm = beat.bpm
        val secondsPerBeat = 60.0 / bpm
        val numBars = 8
        val totalSeconds = (secondsPerBeat * 4 * numBars).toInt()
        val totalSamples = totalSeconds * sampleRate

        val outBuffer = FloatArray(totalSamples)
        val rand = Random(42) // Deterministic seed for consistent high-quality sounds

        val samplesPer16th = (sampleRate * secondsPerBeat / 4.0).toInt()
        val total16ths = (totalSamples / samplesPer16th)

        for (step in 0 until total16ths) {
            val startSample = step * samplesPer16th
            val stepInBar = step % 16

            when (beat.genre) {
                BeatGenre.HIP_HOP -> {
                    // Kick on 0, 10
                    if (stepInBar == 0 || stepInBar == 10) {
                        renderKick(outBuffer, startSample, sampleRate, 120.0f, 48.0f, 0.28f, 0.85f)
                    }
                    // Snare on 4, 12
                    if (stepInBar == 4 || stepInBar == 12) {
                        renderSnare(outBuffer, startSample, sampleRate, 0.22f, 0.65f, rand)
                    }
                    // Swung Hats on even steps
                    if (stepInBar % 2 == 0) {
                        val hatVol = if (stepInBar % 4 == 0) 0.35f else 0.22f
                        renderHiHat(outBuffer, startSample, sampleRate, 0.04f, hatVol, rand)
                    }
                    // Jazzy Rhodes chords in D Minor (D, F, A, C) on beat 1 and 3
                    if (stepInBar == 0 || stepInBar == 8) {
                        renderChord(outBuffer, startSample, sampleRate, doubleArrayOf(146.83, 174.61, 220.0, 261.63), 1.2f, 0.40f)
                    }
                }

                BeatGenre.RAP -> {
                    // 808 Drill Anthem: Kick on 0, 7, 12
                    if (stepInBar == 0 || stepInBar == 7 || stepInBar == 12) {
                        render808Bass(outBuffer, startSample, sampleRate, 46.25, 0.55f, 0.90f) // F# sub
                    }
                    // Snare on 6, 14 (drill cadence)
                    if (stepInBar == 6 || stepInBar == 14) {
                        renderSnare(outBuffer, startSample, sampleRate, 0.18f, 0.70f, rand)
                    }
                    // Fast hi-hats with roll on 10, 11
                    renderHiHat(outBuffer, startSample, sampleRate, 0.03f, 0.28f, rand)
                    if (stepInBar == 10 || stepInBar == 11) {
                        val subSample = startSample + (samplesPer16th / 2)
                        renderHiHat(outBuffer, subSample, sampleRate, 0.02f, 0.22f, rand)
                    }
                    // Dark Drill pluck in F# Minor (F#, A, C#)
                    if (stepInBar % 4 == 0) {
                        renderPluck(outBuffer, startSample, sampleRate, 370.0, 0.35f, 0.45f)
                    }
                }

                BeatGenre.PUNJABI -> {
                    // Desi Dhol: heavy bass dhol on 0, 3, 6, 8, 11, 14 (bhangra chaal groove)
                    if (stepInBar == 0 || stepInBar == 8) {
                        renderDholBass(outBuffer, startSample, sampleRate, 75.0f, 0.40f, 0.90f)
                    } else if (stepInBar == 3 || stepInBar == 11) {
                        renderDholBass(outBuffer, startSample, sampleRate, 90.0f, 0.25f, 0.60f)
                    }
                    // High Dhol slap / dagga on 4, 12
                    if (stepInBar == 4 || stepInBar == 12) {
                        renderDholSlap(outBuffer, startSample, sampleRate, 0.15f, 0.75f, rand)
                    }
                    // Folk Tumbi melody hook in C Major
                    val melodyNotes = doubleArrayOf(523.25, 587.33, 659.25, 783.99) // C5, D5, E5, G5
                    val noteIdx = (step / 2) % melodyNotes.size
                    if (stepInBar % 2 == 0) {
                        renderPluck(outBuffer, startSample, sampleRate, melodyNotes[noteIdx], 0.16f, 0.40f)
                    }
                }

                BeatGenre.TRAP -> {
                    // Heavy 808 Trap Kick on 0, 8, 11
                    if (stepInBar == 0 || stepInBar == 8 || stepInBar == 11) {
                        render808Bass(outBuffer, startSample, sampleRate, 55.0, 0.45f, 0.95f) // A1 sub
                    }
                    // Snappy Trap Clap on 4, 12
                    if (stepInBar == 4 || stepInBar == 12) {
                        renderClap(outBuffer, startSample, sampleRate, 0.15f, 0.80f, rand)
                    }
                    // Trap Hi-Hats
                    renderHiHat(outBuffer, startSample, sampleRate, 0.025f, 0.25f, rand)
                    // Minor Trap Bell (A4, C5, E5)
                    if (stepInBar == 0 || stepInBar == 6 || stepInBar == 12) {
                        renderBell(outBuffer, startSample, sampleRate, 440.0, 0.6f, 0.35f)
                    }
                }

                BeatGenre.LO_FI -> {
                    // Soft kick on 0, 10
                    if (stepInBar == 0 || stepInBar == 10) {
                        renderKick(outBuffer, startSample, sampleRate, 90.0f, 42.0f, 0.25f, 0.65f)
                    }
                    // Rimshot on 4, 12
                    if (stepInBar == 4 || stepInBar == 12) {
                        renderRimshot(outBuffer, startSample, sampleRate, 0.12f, 0.50f, rand)
                    }
                    // Gentle shaker
                    if (stepInBar % 2 == 0) {
                        renderHiHat(outBuffer, startSample, sampleRate, 0.05f, 0.15f, rand)
                    }
                    // G Major 7th chords (G, B, D, F#)
                    if (stepInBar == 0 || stepInBar == 8) {
                        renderChord(outBuffer, startSample, sampleRate, doubleArrayOf(196.0, 246.94, 293.66, 369.99), 1.6f, 0.32f)
                    }
                }

                BeatGenre.ROMANTIC -> {
                    // Romantic Acoustic Soul: finger snap on 4, 12
                    if (stepInBar == 4 || stepInBar == 12) {
                        renderClap(outBuffer, startSample, sampleRate, 0.10f, 0.50f, rand)
                    }
                    // Soft acoustic kick on 0, 6, 10
                    if (stepInBar == 0 || stepInBar == 6 || stepInBar == 10) {
                        renderKick(outBuffer, startSample, sampleRate, 85.0f, 44.0f, 0.22f, 0.60f)
                    }
                    // Acoustic Guitar arpeggio in E Major (E, G#, B, D#)
                    val arpeg = doubleArrayOf(164.81, 207.65, 246.94, 311.13)
                    val aNote = arpeg[stepInBar % 4]
                    renderPluck(outBuffer, startSample, sampleRate, aNote, 0.35f, 0.38f)
                }

                BeatGenre.SAD -> {
                    // Sad Rainy Melancholy: slow ambient sub pulse on 0
                    if (stepInBar == 0) {
                        render808Bass(outBuffer, startSample, sampleRate, 65.41, 0.8f, 0.70f) // C2 sub
                    }
                    // Soft brushed snare on 4, 12
                    if (stepInBar == 4 || stepInBar == 12) {
                        renderSnare(outBuffer, startSample, sampleRate, 0.25f, 0.40f, rand)
                    }
                    // Emotional C Minor piano chords (C, Eb, G)
                    if (stepInBar == 0 || stepInBar == 8) {
                        renderChord(outBuffer, startSample, sampleRate, doubleArrayOf(130.81, 155.56, 196.0, 261.63), 1.8f, 0.38f)
                    }
                }

                else -> {}
            }
        }

        // Convert to ShortArray with soft saturation
        val shortOut = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            val sample = tanh(outBuffer[i])
            shortOut[i] = (sample * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
        }

        return shortOut
    }

    // DSP Instrument Synthesizers

    private fun renderKick(buf: FloatArray, start: Int, sr: Int, startHz: Float, endHz: Float, durSec: Float, vol: Float) {
        val len = (sr * durSec).toInt().coerceAtMost(buf.size - start)
        var phase = 0.0
        for (i in 0 until len) {
            val t = i.toFloat() / len
            val freq = startHz * (1.0f - t) + endHz * t
            phase += 2.0 * PI * freq / sr
            val env = exp(-t * 6.5f)
            if (start + i < buf.size) {
                buf[start + i] += sin(phase).toFloat() * env * vol
            }
        }
    }

    private fun render808Bass(buf: FloatArray, start: Int, sr: Int, freq: Double, durSec: Float, vol: Float) {
        val len = (sr * durSec).toInt().coerceAtMost(buf.size - start)
        var phase = 0.0
        for (i in 0 until len) {
            val t = i.toFloat() / len
            phase += 2.0 * PI * freq / sr
            val env = exp(-t * 2.8f)
            val s = sin(phase).toFloat()
            val sat = tanh(s * 1.5f) // Warm harmonic saturation
            if (start + i < buf.size) {
                buf[start + i] += sat * env * vol
            }
        }
    }

    private fun renderSnare(buf: FloatArray, start: Int, sr: Int, durSec: Float, vol: Float, rand: Random) {
        val len = (sr * durSec).toInt().coerceAtMost(buf.size - start)
        var tonePhase = 0.0
        for (i in 0 until len) {
            val t = i.toFloat() / len
            tonePhase += 2.0 * PI * 185.0 / sr
            val tone = sin(tonePhase).toFloat() * exp(-t * 12.0f) * 0.4f
            val noise = (rand.nextFloat() * 2.0f - 1.0f) * exp(-t * 8.0f) * 0.7f
            if (start + i < buf.size) {
                buf[start + i] += (tone + noise) * vol
            }
        }
    }

    private fun renderHiHat(buf: FloatArray, start: Int, sr: Int, durSec: Float, vol: Float, rand: Random) {
        val len = (sr * durSec).toInt().coerceAtMost(buf.size - start)
        var prevNoise = 0.0f
        for (i in 0 until len) {
            val t = i.toFloat() / len
            val raw = rand.nextFloat() * 2.0f - 1.0f
            val hp = raw - prevNoise // High-pass differentiator
            prevNoise = raw
            val env = exp(-t * 25.0f)
            if (start + i < buf.size) {
                buf[start + i] += hp * env * vol
            }
        }
    }

    private fun renderClap(buf: FloatArray, start: Int, sr: Int, durSec: Float, vol: Float, rand: Random) {
        val len = (sr * durSec).toInt().coerceAtMost(buf.size - start)
        for (i in 0 until len) {
            val t = i.toFloat() / len
            val noise = rand.nextFloat() * 2.0f - 1.0f
            val env = exp(-t * 14.0f)
            if (start + i < buf.size) {
                buf[start + i] += noise * env * vol
            }
        }
    }

    private fun renderRimshot(buf: FloatArray, start: Int, sr: Int, durSec: Float, vol: Float, rand: Random) {
        val len = (sr * durSec).toInt().coerceAtMost(buf.size - start)
        var phase = 0.0
        for (i in 0 until len) {
            val t = i.toFloat() / len
            phase += 2.0 * PI * 420.0 / sr
            val wood = sin(phase).toFloat() * exp(-t * 18.0f)
            val click = (rand.nextFloat() * 2.0f - 1.0f) * exp(-t * 30.0f) * 0.5f
            if (start + i < buf.size) {
                buf[start + i] += (wood + click) * vol
            }
        }
    }

    private fun renderDholBass(buf: FloatArray, start: Int, sr: Int, startHz: Float, durSec: Float, vol: Float) {
        val len = (sr * durSec).toInt().coerceAtMost(buf.size - start)
        var phase = 0.0
        for (i in 0 until len) {
            val t = i.toFloat() / len
            val freq = startHz * (1.0f - t * 0.3f)
            phase += 2.0 * PI * freq / sr
            val env = exp(-t * 5.0f)
            if (start + i < buf.size) {
                buf[start + i] += sin(phase).toFloat() * env * vol
            }
        }
    }

    private fun renderDholSlap(buf: FloatArray, start: Int, sr: Int, durSec: Float, vol: Float, rand: Random) {
        val len = (sr * durSec).toInt().coerceAtMost(buf.size - start)
        var phase = 0.0
        for (i in 0 until len) {
            val t = i.toFloat() / len
            phase += 2.0 * PI * 340.0 / sr
            val skin = sin(phase).toFloat() * exp(-t * 14.0f) * 0.6f
            val slapNoise = (rand.nextFloat() * 2.0f - 1.0f) * exp(-t * 18.0f) * 0.6f
            if (start + i < buf.size) {
                buf[start + i] += (skin + slapNoise) * vol
            }
        }
    }

    private fun renderChord(buf: FloatArray, start: Int, sr: Int, freqs: DoubleArray, durSec: Float, vol: Float) {
        val len = (sr * durSec).toInt().coerceAtMost(buf.size - start)
        val noteGain = vol / freqs.size
        for (f in freqs) {
            var phase = 0.0
            for (i in 0 until len) {
                val t = i.toFloat() / len
                phase += 2.0 * PI * f / sr
                val s1 = sin(phase).toFloat()
                val s2 = sin(phase * 2.0).toFloat() * 0.25f // 2nd harmonic
                val s3 = sin(phase * 3.0).toFloat() * 0.10f
                val env = exp(-t * 2.2f)
                if (start + i < buf.size) {
                    buf[start + i] += (s1 + s2 + s3) * env * noteGain
                }
            }
        }
    }

    private fun renderPluck(buf: FloatArray, start: Int, sr: Int, freq: Double, durSec: Float, vol: Float) {
        val len = (sr * durSec).toInt().coerceAtMost(buf.size - start)
        var phase = 0.0
        for (i in 0 until len) {
            val t = i.toFloat() / len
            phase += 2.0 * PI * freq / sr
            val s1 = sin(phase).toFloat()
            val s2 = sin(phase * 2.0).toFloat() * 0.35f
            val env = exp(-t * 6.0f)
            if (start + i < buf.size) {
                buf[start + i] += (s1 + s2) * env * vol
            }
        }
    }

    private fun renderBell(buf: FloatArray, start: Int, sr: Int, freq: Double, durSec: Float, vol: Float) {
        val len = (sr * durSec).toInt().coerceAtMost(buf.size - start)
        var phase1 = 0.0
        var phase2 = 0.0
        for (i in 0 until len) {
            val t = i.toFloat() / len
            phase1 += 2.0 * PI * freq / sr
            phase2 += 2.0 * PI * (freq * 2.76) / sr // Inharmonic metallic overtone
            val s = sin(phase1).toFloat() * 0.7f + sin(phase2).toFloat() * 0.3f
            val env = exp(-t * 4.0f)
            if (start + i < buf.size) {
                buf[start + i] += s * env * vol
            }
        }
    }
}
