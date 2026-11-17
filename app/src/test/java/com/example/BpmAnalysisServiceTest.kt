package com.example

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import com.example.audio.BpmAnalysisService
import com.example.audio.RealtimeBpmResult
import com.example.data.TrackEntity
import com.example.ui.components.DualTopWaveformHeader
import com.example.ui.theme.PulseMixTheme
import com.example.viewmodel.DeckUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BpmAnalysisServiceTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `bpm analysis service binds and publishes per-deck real-time tempo snapshots`() {
        val controller = Robolectric.buildService(BpmAnalysisService::class.java).create()
        try {
            val service = controller.get()
            service.nativeAvailableProvider = { false } // disable background polling race during deterministic test
            service.analyzerProvider = { deckIndex ->
                if (deckIndex == 0) {
                    RealtimeBpmResult(
                        detectedBpm = 128.4f,
                        confidence = 0.88f,
                        beatPhase = 0.25f,
                        fromLiveStream = true,
                        analyzedHops = 500
                    )
                } else {
                    RealtimeBpmResult(
                        detectedBpm = 140.0f,
                        confidence = 0.76f,
                        beatPhase = 0.50f,
                        fromLiveStream = false,
                        analyzedHops = 600
                    )
                }
            }

            val binder = service.onBind(Intent()) as BpmAnalysisService.LocalBinder
            assertEquals(service, binder.getService())

            val resA = service.analyzeDeckNow(0)
            val resB = service.analyzeDeckNow(1)

            assertEquals(128.4f, resA.detectedBpm, 0.01f)
            assertTrue(resA.isLiveStream)
            assertEquals(140.0f, service.bpmState.value.deckB.detectedBpm, 0.01f)
            assertEquals(0.76f, service.bpmState.value.deckB.confidence, 0.01f)

            service.resetDeckAnalysis(0)
            assertEquals(0f, service.bpmState.value.deckA.detectedBpm, 0.001f)
        } finally {
            controller.unbind().destroy()
        }
    }

    @Test
    fun `header displays live analyzed tempo in Compose text field and commits edits`() {
        val dummyTrack = TrackEntity(
            id = 1L,
            uriString = "file:///demo.wav",
            title = "Live Set",
            artist = "Pulse",
            album = "Club",
            durationMs = 180_000L,
            bpm = 128.0f,
            firstBeatMs = 0L,
            musicalKey = "Am",
            camelotKey = "8A",
            waveformSerialized = ""
        )

        var committedDeck = -1
        var committedBpm by mutableFloatStateOf(131.4f)

        composeRule.setContent {
            PulseMixTheme {
                DualTopWaveformHeader(
                    deckA = DeckUiState(
                        deckIndex = 0,
                        track = dummyTrack,
                        baseBpm = 128.0f,
                        effectiveBpm = committedBpm,
                        realtimeBpm = committedBpm,
                        bpmConfidence = 0.90f,
                        bpmFromLiveStream = true
                    ),
                    deckB = DeckUiState(
                        deckIndex = 1,
                        track = dummyTrack.copy(id = 2L, title = "Track B", bpm = 124.0f),
                        baseBpm = 124.0f,
                        effectiveBpm = 124.0f,
                        realtimeBpm = 124.2f,
                        bpmConfidence = 0.80f,
                        bpmFromLiveStream = false
                    ),
                    masterDeckIndex = 0,
                    isRecording = false,
                    recordedMs = 0L,
                    onSeekDeck = { _, _ -> },
                    onToggleRecord = {},
                    onResetPitch = {},
                    onCommitBpm = { idx, bpm ->
                        committedDeck = idx
                        committedBpm = bpm
                    }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("deck_a_bpm_field").assertTextContains("131.4")
        composeRule.onNodeWithTag("deck_b_bpm_field").assertTextContains("124.2")

        composeRule.onNodeWithTag("deck_a_bpm_field").performTextReplacement("135.5")
        composeRule.onNodeWithTag("deck_a_bpm_field").performImeAction()
        composeRule.waitForIdle()

        assertEquals(0, committedDeck)
        assertEquals(135.5f, committedBpm, 0.01f)
    }
}
