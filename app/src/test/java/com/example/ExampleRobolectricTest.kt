package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioAnalyzer
import com.example.audio.AudioDecoder
import com.example.data.PulseMixDatabase
import com.example.data.TrackEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("PulseMix", appName)
    }

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

    @Test
    fun `room database persists tracks and cues`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = PulseMixDatabase.getInstance(context)
        val id = db.trackDao().insertTrack(
            TrackEntity(
                uriString = "file:///test_track.wav",
                title = "Test Track",
                artist = "DJ Test",
                album = "Test",
                durationMs = 120_000L,
                bpm = 126.0f,
                firstBeatMs = 0L,
                musicalKey = "Am",
                camelotKey = "8A",
                waveformSerialized = "50,50,50,80"
            )
        )
        val loaded = db.trackDao().findByUri("file:///test_track.wav")
        assertTrue(id > 0)
        assertEquals("Test Track", loaded?.title)
        db.close()
    }
}
