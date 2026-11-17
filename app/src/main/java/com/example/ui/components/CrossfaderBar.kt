package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.CrossfaderMath
import com.example.ui.theme.DeckACyan
import com.example.ui.theme.DeckBAmber
import com.example.ui.theme.DeckSurface
import com.example.ui.theme.PanelBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TrackSlotBg
import com.example.ui.theme.VuGreen
import kotlin.math.abs

/**
 * Horizontal DJ Crossfader positioned between Deck A and Deck B.
 * Maps horizontal touch-drag / tap / quick-cut input directly to the C++ Oboe DSP engine
 * (`-1f` = 100% Deck A, `0f` = centered mix, `+1f` = 100% Deck B).
 */
@Composable
fun CrossfaderBar(
    value: Float, // -1f..+1f
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    curveMode: Int = CrossfaderMath.CURVE_CONSTANT_POWER,
    onDragStateChange: (Boolean) -> Unit = {},
    onReset: () -> Unit = { onValueChange(0f) },
    onCycleCurve: (() -> Unit)? = null
) {
    val clampedValue = value.coerceIn(-1f, 1f)
    val gains = remember(clampedValue, curveMode) {
        CrossfaderMath.computeGains(clampedValue, curveMode)
    }

    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnDragStateChange by rememberUpdatedState(onDragStateChange)
    val latestOnReset by rememberUpdatedState(onReset)
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    // Cap half-width + rail padding so touch position maps 1:1 to fader cap center
    val capWidthDp = 24.dp
    val railInsetPx = with(density) { (capWidthDp * 0.5f + 4.dp).toPx() }
    var lastDetentSign by remember { mutableFloatStateOf(clampedValue) }

    fun mapXToCrossfader(xPx: Float, widthPx: Int, snapCenter: Boolean): Float {
        val usable = (widthPx.toFloat() - railInsetPx * 2f).coerceAtLeast(1f)
        val norm = ((xPx - railInsetPx) / usable).coerceIn(0f, 1f)
        val raw = norm * 2f - 1f
        return if (snapCenter && abs(raw) < 0.03f) 0f else raw
    }

    val isCentered = abs(clampedValue) < 0.015f
    val capAccentColor = when {
        isCentered -> VuGreen
        clampedValue < 0f -> DeckACyan
        else -> DeckBAmber
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(TrackSlotBg)
            .border(1.dp, PanelBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Quick-cut to Deck A
        Box(
            modifier = Modifier
                .width(28.dp)
                .fillMaxHeight()
                .testTag("crossfader_cut_a")
                .clip(RoundedCornerShape(7.dp))
                .background(DeckACyan.copy(alpha = 0.08f + gains.deckAGain * 0.16f))
                .border(
                    width = 1.dp,
                    color = DeckACyan.copy(alpha = 0.25f + gains.deckAGain * 0.65f),
                    shape = RoundedCornerShape(7.dp)
                )
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    latestOnValueChange(-1f)
                }
                .semantics {
                    contentDescription = "Cut crossfader to Deck A"
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "A",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = DeckACyan
            )
        }

        Spacer(Modifier.width(5.dp))

        // Center mix telemetry + horizontal fader track
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${gains.deckAPercent}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (gains.deckAPercent > 0) DeckACyan else TextSecondary,
                    modifier = Modifier.testTag("crossfader_ratio_a")
                )

                val curveModifier = if (onCycleCurve != null) {
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(DeckSurface)
                        .border(0.75.dp, PanelBorder, RoundedCornerShape(4.dp))
                        .clickable(onClick = onCycleCurve)
                        .padding(horizontal = 5.dp, vertical = 0.5.dp)
                } else {
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(DeckSurface)
                        .padding(horizontal = 5.dp, vertical = 0.5.dp)
                }
                Box(
                    modifier = curveModifier.testTag("crossfader_curve_badge"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = CrossfaderMath.curveShortLabel(curveMode),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCentered) VuGreen else TextPrimary
                    )
                }

                Text(
                    text = "${gains.deckBPercent}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (gains.deckBPercent > 0) DeckBAmber else TextSecondary,
                    modifier = Modifier.testTag("crossfader_ratio_b")
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("crossfader_slider")
                    .semantics {
                        contentDescription = "Master Crossfader between Deck A and Deck B"
                        stateDescription =
                            "Deck A ${gains.deckAPercent} percent, Deck B ${gains.deckBPercent} percent, ${
                                CrossfaderMath.curveFullName(curveMode)
                            }"
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                latestOnReset()
                            },
                            onTap = { offset ->
                                val next = mapXToCrossfader(offset.x, size.width, snapCenter = true)
                                latestOnValueChange(next)
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { startOffset ->
                                latestOnDragStateChange(true)
                                val next = mapXToCrossfader(startOffset.x, size.width, snapCenter = true)
                                lastDetentSign = next
                                latestOnValueChange(next)
                            },
                            onDragEnd = {
                                latestOnDragStateChange(false)
                            },
                            onDragCancel = {
                                latestOnDragStateChange(false)
                            },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                val next = mapXToCrossfader(change.position.x, size.width, snapCenter = true)
                                if (next == 0f && lastDetentSign != 0f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                lastDetentSign = next
                                latestOnValueChange(next)
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cy = size.height * 0.5f
                    val capW = capWidthDp.toPx().coerceAtMost(size.width * 0.22f)
                    val trackLeft = railInsetPx
                    val trackRight = (size.width - railInsetPx).coerceAtLeast(trackLeft + 1f)
                    val trackW = (trackRight - trackLeft).coerceAtLeast(1f)
                    val midX = trackLeft + trackW * 0.5f

                    // Recessed dark fader groove
                    drawRoundRect(
                        color = Color(0xFF040507),
                        topLeft = Offset(trackLeft, cy - 3.5.dp.toPx()),
                        size = Size(trackW, 7.dp.toPx()),
                        cornerRadius = CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx())
                    )

                    // Calibrated console ticks (-100, -50, 0, +50, +100)
                    for (step in 0..8) {
                        val frac = step / 8f
                        val tx = trackLeft + frac * trackW
                        val isCenter = step == 4
                        val isQuarter = step % 2 == 0
                        val tickHalfH = when {
                            isCenter -> size.height * 0.40f
                            isQuarter -> size.height * 0.28f
                            else -> size.height * 0.17f
                        }
                        drawLine(
                            color = if (isCenter) {
                                if (isCentered) VuGreen else TextPrimary.copy(alpha = 0.7f)
                            } else {
                                PanelBorder.copy(alpha = if (isQuarter) 0.9f else 0.5f)
                            },
                            start = Offset(tx, cy - tickHalfH),
                            end = Offset(tx, cy + tickHalfH),
                            strokeWidth = if (isCenter) 1.8.dp.toPx() else 1.dp.toPx()
                        )
                    }

                    // Active DSP gain rails on left (Deck A) and right (Deck B)
                    val leftSpan = (trackW * 0.5f - 4.dp.toPx()).coerceAtLeast(1f)
                    drawLine(
                        color = DeckACyan.copy(alpha = 0.28f + gains.deckAGain * 0.72f),
                        start = Offset(trackLeft, cy),
                        end = Offset(midX - 4.dp.toPx(), cy),
                        strokeWidth = (2.5.dp.toPx() * (0.45f + gains.deckAGain * 0.55f))
                    )
                    drawLine(
                        color = DeckBAmber.copy(alpha = 0.28f + gains.deckBGain * 0.72f),
                        start = Offset(midX + 4.dp.toPx(), cy),
                        end = Offset(midX + 4.dp.toPx() + leftSpan, cy),
                        strokeWidth = (2.5.dp.toPx() * (0.45f + gains.deckBGain * 0.55f))
                    )

                    // Brushed machined aluminum fader cap
                    val norm = (clampedValue + 1f) * 0.5f
                    val capX = trackLeft + norm * trackW
                    val capH = (size.height * 0.84f).coerceAtLeast(16.dp.toPx())
                    val capTop = cy - capH * 0.5f
                    val capLeft = capX - capW * 0.5f

                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color(0xFF3E4759),
                                Color(0xFF5D677D),
                                Color(0xFF282E3B),
                                Color(0xFF5D677D),
                                Color(0xFF3E4759)
                            )
                        ),
                        topLeft = Offset(capLeft, capTop),
                        size = Size(capW, capH),
                        cornerRadius = CornerRadius(4.5.dp.toPx(), 4.5.dp.toPx())
                    )

                    // Subtle metallic rim on cap
                    drawRoundRect(
                        color = capAccentColor.copy(alpha = 0.75f),
                        topLeft = Offset(capLeft, capTop),
                        size = Size(capW, capH),
                        cornerRadius = CornerRadius(4.5.dp.toPx(), 4.5.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                    )

                    // Center hairline indicator
                    drawLine(
                        color = Color.White,
                        start = Offset(capX, capTop + 3.dp.toPx()),
                        end = Offset(capX, capTop + capH - 3.dp.toPx()),
                        strokeWidth = 2.2.dp.toPx()
                    )
                }
            }
        }

        Spacer(Modifier.width(5.dp))

        // Quick-cut to Deck B
        Box(
            modifier = Modifier
                .width(28.dp)
                .fillMaxHeight()
                .testTag("crossfader_cut_b")
                .clip(RoundedCornerShape(7.dp))
                .background(DeckBAmber.copy(alpha = 0.08f + gains.deckBGain * 0.16f))
                .border(
                    width = 1.dp,
                    color = DeckBAmber.copy(alpha = 0.25f + gains.deckBGain * 0.65f),
                    shape = RoundedCornerShape(7.dp)
                )
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    latestOnValueChange(1f)
                }
                .semantics {
                    contentDescription = "Cut crossfader to Deck B"
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "B",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = DeckBAmber
            )
        }
    }
}
