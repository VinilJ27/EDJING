package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.PulseMixDatabase
import com.example.data.TrackEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("PulseMix", appName)
    }

    @Test
    fun `room database persists tracks and cues`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = PulseMixDatabase.getInstance(context)
        try {
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
        } finally {
            db.close()
        }
    }
}
