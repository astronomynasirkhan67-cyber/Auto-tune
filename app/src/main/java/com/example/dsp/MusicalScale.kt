package com.example.dsp

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

enum class ScaleType(val displayName: String, val intervals: IntArray) {
    CHROMATIC("Chromatic", intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)),
    MAJOR("Major", intArrayOf(0, 2, 4, 5, 7, 9, 11)),
    MINOR("Minor (Natural)", intArrayOf(0, 2, 3, 5, 7, 8, 10)),
    HARMONIC_MINOR("Harmonic Minor", intArrayOf(0, 2, 3, 5, 7, 8, 11)),
    MELODIC_MINOR("Melodic Minor", intArrayOf(0, 2, 3, 5, 7, 9, 11)),
    PENTATONIC_MAJOR("Pentatonic Major", intArrayOf(0, 2, 4, 7, 9)),
    PENTATONIC_MINOR("Pentatonic Minor / Blues", intArrayOf(0, 3, 5, 7, 10)),
    DORIAN("Dorian", intArrayOf(0, 2, 3, 5, 7, 9, 10)),
    MIXOLYDIAN("Mixolydian", intArrayOf(0, 2, 4, 5, 7, 9, 10))
}

object MusicalScale {
    val ROOT_NOTES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun midiToFrequency(midi: Double): Double {
        return 440.0 * 2.0.pow((midi - 69.0) / 12.0)
    }

    fun frequencyToMidi(frequency: Double): Double {
        if (frequency <= 10.0) return 0.0
        return 69.0 + 12.0 * (log2(frequency / 440.0))
    }

    fun getMidiNoteName(midiNumber: Int): String {
        val noteIndex = ((midiNumber % 12) + 12) % 12
        val octave = (midiNumber / 12) - 1
        return "${ROOT_NOTES[noteIndex]}$octave"
    }

    data class PitchSnapResult(
        val originalFreq: Double,
        val targetFreq: Double,
        val originalMidi: Double,
        val targetMidi: Int,
        val noteName: String,
        val centsDiff: Double,
        val pitchRatio: Double
    )

    fun snapPitchToScale(
        frequency: Double,
        rootIndex: Int,
        scaleType: ScaleType,
        correctionStrength: Float, // 0.0 (no correction) to 1.0 (full snap)
        isHardAutoTune: Boolean
    ): PitchSnapResult {
        if (frequency < 40.0 || frequency > 2000.0) {
            return PitchSnapResult(
                originalFreq = frequency,
                targetFreq = frequency,
                originalMidi = 0.0,
                targetMidi = 0,
                noteName = "--",
                centsDiff = 0.0,
                pitchRatio = 1.0
            )
        }

        val originalMidi = frequencyToMidi(frequency)
        val roundedMidi = originalMidi.roundToInt()

        // Find nearest note that fits into the scale
        var closestMidi = roundedMidi
        var minDistance = Double.MAX_VALUE

        // Check within +/- 12 semitones
        for (candidate in (roundedMidi - 6)..(roundedMidi + 6)) {
            val semitoneInOctave = ((candidate - rootIndex) % 12 + 12) % 12
            if (scaleType.intervals.contains(semitoneInOctave)) {
                val dist = kotlin.math.abs(candidate - originalMidi)
                if (dist < minDistance) {
                    minDistance = dist
                    closestMidi = candidate
                }
            }
        }

        val targetExactMidi = closestMidi.toDouble()
        val deltaMidi = targetExactMidi - originalMidi

        // Apply strength and curve
        val effectiveDelta = if (isHardAutoTune) {
            // Hard snap: locks on immediately to closest pitch
            deltaMidi * correctionStrength
        } else {
            // Natural Auto-Tune: deadband of 10 cents around note center, gentle glide
            val deadband = 0.10 // 10 cents
            val absDelta = kotlin.math.abs(deltaMidi)
            if (absDelta < deadband) {
                0.0
            } else {
                val sign = if (deltaMidi > 0) 1.0 else -1.0
                sign * (absDelta - deadband) * correctionStrength * 0.85
            }
        }

        val correctedMidi = originalMidi + effectiveDelta
        val targetFreq = midiToFrequency(correctedMidi)
        val pitchRatio = targetFreq / frequency
        val cents = (originalMidi - targetExactMidi) * 100.0

        return PitchSnapResult(
            originalFreq = frequency,
            targetFreq = targetFreq,
            originalMidi = originalMidi,
            targetMidi = closestMidi,
            noteName = getMidiNoteName(closestMidi),
            centsDiff = cents,
            pitchRatio = pitchRatio
        )
    }
}
