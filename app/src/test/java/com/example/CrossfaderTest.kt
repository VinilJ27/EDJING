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
@Config(sdk = [36])
class CrossfaderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `linear crossfader curve blends continuously without step jump`() {
        val left = CrossfaderMath.computeGains(-1f, CrossfaderMath.CURVE_LINEAR)
        assertEquals(1f, left.deckAGain, 0.001f)
        assertEquals(0f, left.deckBGain, 0.001f)

        val center = CrossfaderMath.computeGains(0f, CrossfaderMath.CURVE_LINEAR)
        assertEquals(0.5f, center.deckAGain, 0.001f)
        assertEquals(0.5f, center.deckBGain, 0.001f)

        val right = CrossfaderMath.computeGains(1f, CrossfaderMath.CURVE_LINEAR)
        assertEquals(0f, right.deckAGain, 0.001f)
        assertEquals(1f, right.deckBGain, 0.001f)
    }

    @Test
    fun `constant power curve preserves total power at center`() {
        val center = CrossfaderMath.computeGains(0f, CrossfaderMath.CURVE_CONSTANT_POWER)
        val totalPower = center.deckAGain * center.deckAGain + center.deckBGain * center.deckBGain
        assertEquals(1.0f, totalPower, 0.001f)
        assertEquals(71, center.deckAPercent)
        assertEquals(71, center.deckBPercent)
    }

    @Test
    fun `sharp cut curve keeps both decks full across middle zone`() {
        val mid = CrossfaderMath.computeGains(0f, CrossfaderMath.CURVE_SHARP_CUT)
        assertEquals(1f, mid.deckAGain, 0.001f)
        assertEquals(1f, mid.deckBGain, 0.001f)

        val fullA = CrossfaderMath.computeGains(-1f, CrossfaderMath.CURVE_SHARP_CUT)
        assertEquals(1f, fullA.deckAGain, 0.001f)
        assertEquals(0f, fullA.deckBGain, 0.001f)
    }

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

        composeRule.onNodeWithTag("crossfader_ratio_a").assertTextEquals("71%")
        composeRule.onNodeWithTag("crossfader_ratio_b").assertTextEquals("71%")
        composeRule.onNodeWithTag("crossfader_curve_badge").assertTextEquals("PWR")

        // Quick cut to Deck A
        composeRule.onNodeWithTag("crossfader_cut_a").performClick()
        assertEquals(-1f, xf, 0.001f)
        composeRule.onNodeWithTag("crossfader_ratio_a").assertTextEquals("100%")
        composeRule.onNodeWithTag("crossfader_ratio_b").assertTextEquals("0%")

        // Quick cut to Deck B
        composeRule.onNodeWithTag("crossfader_cut_b").performClick()
        assertEquals(1f, xf, 0.001f)
        composeRule.onNodeWithTag("crossfader_ratio_a").assertTextEquals("0%")
        composeRule.onNodeWithTag("crossfader_ratio_b").assertTextEquals("100%")

        // Cycle curve from PWR -> CUT -> LIN
        composeRule.onNodeWithTag("crossfader_curve_badge").performClick()
        assertEquals(CrossfaderMath.CURVE_SHARP_CUT, curve)
        composeRule.onNodeWithTag("crossfader_curve_badge").performClick()
        assertEquals(CrossfaderMath.CURVE_LINEAR, curve)

        // Swipe slider left then right
        composeRule.onNodeWithTag("crossfader_slider").performTouchInput { swipeLeft() }
        assertTrue("Expected drag state callback to fire", draggingSeen)
        assertTrue("Expected negative crossfader after swipeLeft, got $xf", xf < -0.5f)

        composeRule.onNodeWithTag("crossfader_slider").performTouchInput { swipeRight() }
        assertTrue("Expected positive crossfader after swipeRight, got $xf", xf > 0.5f)
    }
}
