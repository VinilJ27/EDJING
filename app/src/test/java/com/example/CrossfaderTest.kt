package com.example

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.example.audio.CrossfaderMath
import com.example.ui.components.CrossfaderBar
import com.example.ui.theme.PulseMixTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrossfaderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `crossfader UI updates ratio badges, quick cuts, curve cycle, and drag gestures`() {
        var xf by mutableFloatStateOf(0f)
        var curve by mutableIntStateOf(CrossfaderMath.CURVE_CONSTANT_POWER)
        var draggingSeen = false

        composeRule.setContent {
            PulseMixTheme {
                CrossfaderBar(
                    value = xf,
                    curveMode = curve,
                    onValueChange = { xf = it },
                    onDragStateChange = { if (it) draggingSeen = true },
                    onReset = { xf = 0f },
                    onCycleCurve = { curve = (curve + 1) % 3 },
                    modifier = Modifier
                        .width(260.dp)
                        .height(52.dp)
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("crossfader_ratio_a").assertTextEquals("71%")
        composeRule.onNodeWithTag("crossfader_ratio_b").assertTextEquals("71%")
        composeRule.onNodeWithTag("crossfader_curve_badge").assertTextEquals("PWR")

        // Quick cut to Deck A
        composeRule.onNodeWithTag("crossfader_cut_a").performClick()
        composeRule.waitForIdle()
        assertEquals(-1f, xf, 0.001f)
        composeRule.onNodeWithTag("crossfader_ratio_a").assertTextEquals("100%")
        composeRule.onNodeWithTag("crossfader_ratio_b").assertTextEquals("0%")

        // Quick cut to Deck B
        composeRule.onNodeWithTag("crossfader_cut_b").performClick()
        composeRule.waitForIdle()
        assertEquals(1f, xf, 0.001f)
        composeRule.onNodeWithTag("crossfader_ratio_a").assertTextEquals("0%")
        composeRule.onNodeWithTag("crossfader_ratio_b").assertTextEquals("100%")

        // Cycle curve from PWR -> CUT -> LIN
        composeRule.onNodeWithTag("crossfader_curve_badge").performClick()
        composeRule.waitForIdle()
        assertEquals(CrossfaderMath.CURVE_SHARP_CUT, curve)
        composeRule.onNodeWithTag("crossfader_curve_badge").performClick()
        composeRule.waitForIdle()
        assertEquals(CrossfaderMath.CURVE_LINEAR, curve)

        // Swipe slider left then right
        composeRule.onNodeWithTag("crossfader_slider").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertTrue("Expected drag state callback to fire", draggingSeen)
        assertTrue("Expected negative crossfader after swipeLeft, got $xf", xf < -0.5f)

        composeRule.onNodeWithTag("crossfader_slider").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        assertTrue("Expected positive crossfader after swipeRight, got $xf", xf > 0.5f)
    }
}
