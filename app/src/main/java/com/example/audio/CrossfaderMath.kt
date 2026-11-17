package com.example.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Pure Kotlin mirror of C++ AudioEngine::computeCrossfaderGains so UI mix-ratio readouts
 * and state tests stay exact with the Oboe DSP engine.
 */
object CrossfaderMath {

    const val CURVE_LINEAR = 0
    const val CURVE_CONSTANT_POWER = 1
    const val CURVE_SHARP_CUT = 2

    data class MixGains(
        val deckAGain: Float,
        val deckBGain: Float
    ) {
        val deckAPercent: Int get() = (deckAGain * 100f).roundToInt().coerceIn(0, 100)
        val deckBPercent: Int get() = (deckBGain * 100f).roundToInt().coerceIn(0, 100)
    }

    fun computeGains(crossfader: Float, curve: Int): MixGains {
        val xf = crossfader.coerceIn(-1f, 1f)
        val norm = ((xf + 1f) * 0.5f).coerceIn(0f, 1f)
        return when (curve) {
            CURVE_LINEAR -> MixGains(
                deckAGain = (1f - norm).coerceIn(0f, 1f),
                deckBGain = norm.coerceIn(0f, 1f)
            )
            CURVE_SHARP_CUT -> MixGains(
                deckAGain = if (norm < 0.92f) 1f else ((1f - norm) / 0.08f).coerceIn(0f, 1f),
                deckBGain = if (norm > 0.08f) 1f else (norm / 0.08f).coerceIn(0f, 1f)
            )
            else -> {
                val theta = norm * (PI.toFloat() * 0.5f)
                MixGains(
                    deckAGain = cos(theta).coerceIn(0f, 1f),
                    deckBGain = sin(theta).coerceIn(0f, 1f)
                )
            }
        }
    }

    fun curveShortLabel(curve: Int): String = when (curve) {
        CURVE_LINEAR -> "LIN"
        CURVE_SHARP_CUT -> "CUT"
        else -> "PWR"
    }

    fun curveFullName(curve: Int): String = when (curve) {
        CURVE_LINEAR -> "Smooth Linear"
        CURVE_SHARP_CUT -> "Sharp Scratch Cut"
        else -> "Constant Power"
    }
}
