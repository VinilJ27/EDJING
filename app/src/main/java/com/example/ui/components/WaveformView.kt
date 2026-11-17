package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioAnalyzer
import com.example.audio.WaveformBucket
import com.example.ui.theme.ConsoleCharcoal
import com.example.ui.theme.DeckACyan
import com.example.ui.theme.DeckBAmber
import com.example.ui.theme.DeckSurface
import com.example.ui.theme.HarmonicMatchGreen
import com.example.ui.theme.PanelBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TrackSlotBg
import com.example.ui.theme.VoidBlack
import com.example.ui.theme.VuGreen
import com.example.ui.theme.VuRed
import com.example.viewmodel.DeckUiState
import com.example.viewmodel.formatMs
import com.example.viewmodel.formatPitch
import java.util.Locale
import kotlin.math.max

@Composable
fun DualTopWaveformHeader(
    deckA: DeckUiState,
    deckB: DeckUiState,
    masterDeckIndex: Int,
    isRecording: Boolean,
    recordedMs: Long,
    onSeekDeck: (Int, Float) -> Unit,
    onToggleRecord: () -> Unit,
    onResetPitch: (Int) -> Unit,
    onCommitBpm: (Int, Float) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val harmonic = deckA.track != null && deckB.track != null &&
        AudioAnalyzer.areKeysHarmonic(deckA.track.camelotKey, deckB.track.camelotKey)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ConsoleCharcoal)
            .border(1.dp, PanelBorder, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        // Top Info Row: [BPM/PITCH A] Title+Time | REC & Harmonic Badge | Time+Title [BPM/PITCH B]
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Deck A Readout
            DeckHeaderMeta(
                deck = deckA,
                accentColor = DeckACyan,
                isMaster = masterDeckIndex == 0,
                alignEnd = false,
                onResetPitch = { onResetPitch(0) },
                onCommitBpm = { bpm -> onCommitBpm(0, bpm) },
                modifier = Modifier.weight(1f)
            )

            // Center REC + Harmonic Match
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 6.dp)
            ) {
                if (harmonic) {
                    Text(
                        text = "KEY",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = HarmonicMatchGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
                val recBg by animateColorAsState(
                    if (isRecording) VuRed.copy(alpha = 0.28f) else Color(0xFF1A2B45),
                    label = "rec_bg"
                )
                Row(
                    modifier = Modifier
                        .testTag("header_record_button")
                        .clip(RoundedCornerShape(20.dp))
                        .background(recBg)
                        .border(1.dp, if (isRecording) VuRed else Color(0xFF2D4A75), RoundedCornerShape(20.dp))
                        .clickable(onClick = onToggleRecord)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (isRecording) VuRed else Color(0xFFFF3B30))
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (isRecording) formatMs(recordedMs) else "REC",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Deck B Readout
            DeckHeaderMeta(
                deck = deckB,
                accentColor = DeckBAmber,
                isMaster = masterDeckIndex == 1,
                alignEnd = true,
                onResetPitch = { onResetPitch(1) },
                onCommitBpm = { bpm -> onCommitBpm(1, bpm) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(3.dp))

        // Continuous dual interactive overview waveforms meeting at center divider
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(VoidBlack)
                .border(1.dp, PanelBorder, RoundedCornerShape(6.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OverviewWaveformBar(
                deck = deckA,
                accentColor = DeckACyan,
                onSeekFraction = { onSeekDeck(0, it) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("deck_a_waveform")
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(VuRed.copy(alpha = 0.85f))
            )
            OverviewWaveformBar(
                deck = deckB,
                accentColor = DeckBAmber,
                onSeekFraction = { onSeekDeck(1, it) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("deck_b_waveform")
            )
        }
    }
}

@Composable
private fun DeckHeaderMeta(
    deck: DeckUiState,
    accentColor: Color,
    isMaster: Boolean,
    alignEnd: Boolean,
    onResetPitch: () -> Unit,
    onCommitBpm: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start
    ) {
        if (!alignEnd) {
            BpmTempoFieldBadge(
                deck = deck,
                accentColor = accentColor,
                isMaster = isMaster,
                onResetPitch = onResetPitch,
                onCommitBpm = onCommitBpm
            )
            Spacer(Modifier.width(6.dp))
        }

        Column(
            horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Text(
                text = deck.track?.title ?: "Empty Deck",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${formatMs(deck.cursorMs)} / ${formatMs(deck.durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                deck.track?.let { tr ->
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "${tr.musicalKey}•${tr.camelotKey}",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (alignEnd) {
            Spacer(Modifier.width(6.dp))
            BpmTempoFieldBadge(
                deck = deck,
                accentColor = accentColor,
                isMaster = isMaster,
                onResetPitch = onResetPitch,
                onCommitBpm = onCommitBpm
            )
        }
    }
}

@Composable
fun BpmTempoFieldBadge(
    deck: DeckUiState,
    accentColor: Color,
    isMaster: Boolean,
    onResetPitch: () -> Unit,
    onCommitBpm: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayBpm = if (deck.track != null && deck.realtimeBpm > 1f && deck.bpmConfidence >= 0.2f) {
        deck.realtimeBpm
    } else {
        deck.effectiveBpm
    }
    val formattedBpm = remember(displayBpm) {
        String.format(Locale.US, "%.1f", displayBpm)
    }

    var isFocused by remember { mutableStateOf(false) }
    var textValue by remember { mutableStateOf(formattedBpm) }
    val focusManager = LocalFocusManager.current
    val deckPrefix = if (deck.deckIndex == 0) "deck_a" else "deck_b"
    val deckLabel = if (deck.deckIndex == 0) "Deck A" else "Deck B"

    LaunchedEffect(formattedBpm, isFocused) {
        if (!isFocused) {
            textValue = formattedBpm
        }
    }

    fun commitText() {
        val parsed = textValue.trim().toFloatOrNull()
        if (parsed != null && parsed in 40f..240f) {
            onCommitBpm(parsed)
        } else {
            textValue = formattedBpm
        }
    }

    Column(
        modifier = modifier
            .widthIn(min = 66.dp, max = 76.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(TrackSlotBg)
            .border(
                width = if (isFocused || isMaster) 1.2.dp else 1.dp,
                color = if (isFocused || isMaster) accentColor else PanelBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 5.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            deck.bpmFromLiveStream -> VuGreen
                            deck.realtimeBpm > 1f -> accentColor
                            else -> TextSecondary.copy(alpha = 0.45f)
                        }
                    )
            )
            BasicTextField(
                value = textValue,
                onValueChange = { input ->
                    val filtered = input.filter { it.isDigit() || it == '.' }
                    if (filtered.length <= 6 && filtered.count { it == '.' } <= 1) {
                        textValue = filtered
                    }
                },
                singleLine = true,
                textStyle = TextStyle(
                    color = accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(accentColor),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        commitText()
                        focusManager.clearFocus()
                    }
                ),
                modifier = Modifier
                    .width(46.dp)
                    .testTag("${deckPrefix}_bpm_field")
                    .onFocusChanged { state ->
                        if (isFocused && !state.isFocused) {
                            commitText()
                        }
                        isFocused = state.isFocused
                    }
                    .semantics {
                        contentDescription = "$deckLabel tempo BPM"
                        stateDescription = "$formattedBpm beats per minute"
                    }
            )
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onResetPitch)
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = if (isMaster) "MST" else "BPM",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 7.sp,
                color = if (isMaster) accentColor else TextSecondary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = formatPitch(deck.pitchOffset),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun OverviewWaveformBar(
    deck: DeckUiState,
    accentColor: Color,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (deck.durationMs > 0L) (deck.cursorMs.toFloat() / deck.durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val buckets = deck.waveform

    Box(
        modifier = modifier
            .background(VoidBlack)
            .pointerInput(deck.durationMs) {
                detectTapGestures { offset ->
                    if (size.width > 0) {
                        onSeekFraction((offset.x / size.width).coerceIn(0f, 1f))
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val midY = h * 0.5f

            // Loop region highlight
            if (deck.loopActive && deck.durationMs > 0L) {
                val ls = (deck.loopStartMs.toFloat() / deck.durationMs) * w
                val le = (deck.loopEndMs.toFloat() / deck.durationMs) * w
                drawRect(
                    color = accentColor.copy(alpha = 0.22f),
                    topLeft = Offset(ls, 0f),
                    size = Size(max(4f, le - ls), h)
                )
            }

            if (buckets.isNotEmpty()) {
                val count = buckets.size
                val barW = w / count.toFloat()
                for (i in 0 until count) {
                    val b = buckets[i]
                    val x = i * barW
                    val barH = (b.peak * h * 0.88f).coerceAtLeast(2f)
                    val played = (i.toFloat() / count) <= progress

                    // Multi-band color tint: heavy bass -> accent, high energy -> bright white/yellow
                    val color = when {
                        !played -> accentColor.copy(alpha = 0.34f)
                        b.high > 0.55f -> Color.White.copy(alpha = 0.92f)
                        else -> accentColor
                    }
                    drawRect(
                        color = color,
                        topLeft = Offset(x, midY - barH * 0.5f),
                        size = Size(max(1.2f, barW * 0.78f), barH)
                    )
                }
            } else {
                // Subtle baseline when empty
                drawLine(
                    color = PanelBorder,
                    start = Offset(0f, midY),
                    end = Offset(w, midY),
                    strokeWidth = 2f
                )
            }

            // Beat grid ticks
            if (deck.track != null && deck.durationMs > 0L && deck.baseBpm > 1f) {
                val beatMs = 60_000f / deck.baseBpm
                var t = deck.track.firstBeatMs.toFloat()
                var beatNum = 0
                while (t < deck.durationMs) {
                    if (beatNum % 4 == 0) {
                        val x = (t / deck.durationMs) * w
                        drawLine(
                            color = Color.White.copy(alpha = 0.24f),
                            start = Offset(x, 0f),
                            end = Offset(x, h),
                            strokeWidth = 1f
                        )
                    }
                    t += beatMs
                    beatNum++
                }
            }

            // Hot cue markers
            if (deck.durationMs > 0L) {
                deck.hotCues.values.forEach { cue ->
                    val cx = (cue.positionMs.toFloat() / deck.durationMs) * w
                    drawLine(
                        color = Color(cue.colorHex),
                        start = Offset(cx, 0f),
                        end = Offset(cx, h),
                        strokeWidth = 3f
                    )
                }
            }

            // Playhead needle
            val px = progress * w
            drawLine(
                color = Color.White,
                start = Offset(px, 0f),
                end = Offset(px, h),
                strokeWidth = 3f
            )
        }
    }
}

/**
 * Parallel scrolling phase-alignment strip (shown in center or between decks)
 * so DJs can visually verify beat grids locking in real time.
 */
@Composable
fun ParallelPhaseStrip(
    deckA: DeckUiState,
    deckB: DeckUiState,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(VoidBlack)
            .border(1.dp, PanelBorder, RoundedCornerShape(8.dp))
    ) {
        val w = size.width
        val h = size.height
        val centerY = h * 0.5f
        val halfW = w * 0.5f

        drawScrollingHalf(deckA, DeckACyan, 0f, halfW - 2f, h, centerY)
        drawScrollingHalf(deckB, DeckBAmber, halfW + 2f, halfW - 2f, h, centerY)

        // Center fixed playhead across both strips
        drawLine(
            color = VuRed,
            start = Offset(0f, centerY),
            end = Offset(w, centerY),
            strokeWidth = 2.5f
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawScrollingHalf(
    deck: DeckUiState,
    color: Color,
    startX: Float,
    halfW: Float,
    h: Float,
    centerY: Float
) {
    val buckets = deck.waveform
    if (buckets.isEmpty() || deck.durationMs <= 0L) return

    val windowMs = 6000f // +/- 3 seconds around playhead
    val minMs = deck.cursorMs - windowMs * 0.5f
    val maxMs = deck.cursorMs + windowMs * 0.5f

    // Draw scrolling waveform bars
    val visibleBars = 48
    val barH = h / visibleBars.toFloat()
    for (i in 0 until visibleBars) {
        val fracY = i.toFloat() / visibleBars.toFloat()
        val sampleMs = minMs + fracY * windowMs
        if (sampleMs < 0f || sampleMs > deck.durationMs) continue
        val bucketIdx = ((sampleMs / deck.durationMs) * buckets.size).toInt().coerceIn(0, buckets.lastIndex)
        val b = buckets[bucketIdx]
        val amp = b.peak * (halfW * 0.85f)
        val y = fracY * h
        drawRect(
            color = color.copy(alpha = 0.8f),
            topLeft = Offset(startX + (halfW - amp) * 0.5f, y),
            size = Size(max(2f, amp), max(1.5f, barH * 0.72f))
        )
    }

    // Draw beat-grid lines scrolling across playhead
    if (deck.track != null && deck.baseBpm > 1f) {
        val beatMs = 60_000f / deck.baseBpm
        val firstVisBeat = ((minMs - deck.track.firstBeatMs) / beatMs).toInt()
        val lastVisBeat = ((maxMs - deck.track.firstBeatMs) / beatMs).toInt() + 1
        for (b in firstVisBeat..lastVisBeat) {
            val tMs = deck.track.firstBeatMs + b * beatMs
            val y = ((tMs - minMs) / windowMs) * h
            if (y in 0f..h) {
                val isBar = b % 4 == 0
                drawLine(
                    color = Color.White.copy(alpha = if (isBar) 0.55f else 0.22f),
                    start = Offset(startX, y),
                    end = Offset(startX + halfW, y),
                    strokeWidth = if (isBar) 2f else 1f
                )
            }
        }
    }
}
