package com.example

import com.example.audio.CrossfaderMath
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure JVM unit tests for crossfader gain curves (no Android framework / Robolectric required).
 */
class CrossfaderMathTest {

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
}
