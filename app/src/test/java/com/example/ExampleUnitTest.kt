package com.example

import com.example.audio.WavAudioUtil
import com.example.dsp.MusicalScale
import com.example.dsp.PitchCorrector
import com.example.dsp.PitchDetector
import com.example.dsp.ScaleType
import com.example.dsp.SoftLimiter
import com.example.dsp.StudioCompressor
import com.example.dsp.StudioDelay
import com.example.dsp.StudioEqualizer
import com.example.dsp.StudioReverb
import com.example.dsp.VocalDoubler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class ExampleUnitTest {

    @Test
    fun testMusicalScaleConversions() {
        val a4Freq = 440.0
        val a4Midi = MusicalScale.frequencyToMidi(a4Freq)
        assertEquals(69.0, a4Midi, 0.01)

        val c4Midi = 60.0
        val c4Freq = MusicalScale.midiToFrequency(c4Midi)
        assertEquals(261.63, c4Freq, 0.1)

        assertEquals("A4", MusicalScale.getMidiNoteName(69))
        assertEquals("C4", MusicalScale.getMidiNoteName(60))
    }

    @Test
    fun testPitchSnapping() {
        // Slightly off A4 (445 Hz, +19 cents)
        val testFreq = 445.0
        val snap = MusicalScale.snapPitchToScale(
            frequency = testFreq,
            rootIndex = 0, // C
            scaleType = ScaleType.MAJOR,
            correctionStrength = 1.0f,
            isHardAutoTune = true
        )

        assertEquals("A4", snap.noteName)
        assertEquals(440.0, snap.targetFreq, 0.5)
        assertTrue(snap.centsDiff > 0.0)
        assertTrue(snap.pitchRatio < 1.0) // pitched down to snap to 440Hz
    }

    @Test
    fun testPitchDetectorWithSineWave() {
        val sampleRate = 44100
        val targetFreq = 440.0 // A4
        val bufferSize = 1024
        val buffer = FloatArray(bufferSize)

        // Generate clean 440Hz sine wave
        for (i in 0 until bufferSize) {
            buffer[i] = (sin(2.0 * PI * targetFreq * i / sampleRate) * 0.8f).toFloat()
        }

        val detector = PitchDetector(sampleRate, bufferSize)
        val detectedHz = detector.detectPitch(buffer, bufferSize)

        assertTrue("Detected pitch $detectedHz should be close to 440Hz", kotlin.math.abs(detectedHz - targetFreq) < 5.0)
        assertTrue("Confidence should be high", detector.lastConfidence > 0.70f)
    }

    @Test
    fun testPitchCorrectorProcessing() {
        val corrector = PitchCorrector(44100, 2048)
        val buffer = FloatArray(512) { 0.5f }

        corrector.setTargetRatio(1.1, 0.9f)
        corrector.process(buffer, buffer.size)

        // Ensure no NaN or Inf
        for (s in buffer) {
            assertFalse("Sample should not be NaN", s.isNaN())
            assertFalse("Sample should not be infinite", s.isInfinite())
        }
    }

    @Test
    fun testVocalEffectsSuite() {
        val comp = StudioCompressor(44100)
        val eq = StudioEqualizer(44100)
        val reverb = StudioReverb(44100)
        val delay = StudioDelay(44100)
        val doubler = VocalDoubler(44100)

        val buffer = FloatArray(512) { (sin(it.toDouble() * 0.1) * 0.5).toFloat() }

        comp.process(buffer, buffer.size)
        eq.process(buffer, buffer.size)
        doubler.process(buffer, buffer.size)
        delay.process(buffer, buffer.size)
        reverb.process(buffer, buffer.size)
        SoftLimiter.process(buffer, buffer.size)

        for (s in buffer) {
            assertFalse(s.isNaN())
            assertTrue(s in -1.0f..1.0f)
        }
    }

    @Test
    fun testWavAudioMixer() {
        val vocalShorts = ShortArray(1000) { 15000.toShort() }
        val beatShorts = ShortArray(1000) { 12000.toShort() }

        val mixedStereo = WavAudioUtil.mixVocalAndBeatToStereo(
            vocalPcm = vocalShorts,
            vocalChannels = 1,
            vocalVolume = 1.0f,
            beatPcm = beatShorts,
            beatChannels = 1,
            beatVolume = 0.8f,
            masterVolume = 1.0f
        )

        assertEquals(2000, mixedStereo.size) // Interleaved stereo
        assertTrue(mixedStereo[0] != 0.toShort())
    }
}
