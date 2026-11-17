package com.example

import com.example.audio.AudioAnalyzer
import com.example.audio.AudioDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Pure JVM unit tests for DSP BPM analysis, Camelot harmonic matching, and PCM resampling.
 */
class AudioAnalyzerTest {

    @Test
    fun `audio analyzer detects 128 bpm click pulse`() {
        val sr = 48000
        val durationSec = 10
        val totalFrames = sr * durationSec
        val pcm = FloatArray(totalFrames * 2)
        val beatIntervalFrames = (sr * 60.0 / 128.0).toInt()

        var pos = 0
        while (pos < totalFrames) {
            for (i in 0 until 2400) {
                if (pos + i >= totalFrames) break
                val t = i.toFloat() / sr
                val s = sin(2.0 * PI * 60.0 * t).toFloat() * exp(-t * 25f)
                pcm[(pos + i) * 2] = s
                pcm[(pos + i) * 2 + 1] = s
            }
            pos += beatIntervalFrames
        }

        val (bpm, _) = AudioAnalyzer.detectBpmAndBeatGrid(pcm, sr)
        assertTrue("Expected ~128 BPM but got $bpm", bpm in 126.5f..129.5f)
    }

    @Test
    fun `camelot harmonic wheel matching works`() {
        assertTrue(AudioAnalyzer.areKeysHarmonic("8A", "8A"))
        assertTrue(AudioAnalyzer.areKeysHarmonic("8A", "8B"))
        assertTrue(AudioAnalyzer.areKeysHarmonic("8A", "9A"))
        assertTrue(AudioAnalyzer.areKeysHarmonic("12A", "1A"))
    }

    @Test
    fun `resampler converts 44100 mono to 48000 stereo`() {
        val mono44k = FloatArray(44100) { 0.5f }
        val stereo48k = AudioDecoder.convertTo48kStereo(mono44k, 44100, 1)
        assertEquals(48000 * 2, stereo48k.size)
    }
}
