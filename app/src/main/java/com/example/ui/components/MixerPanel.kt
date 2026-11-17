package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ConsoleCharcoal
import com.example.ui.theme.DeckACyan
import com.example.ui.theme.DeckBAmber
import com.example.ui.theme.DeckSurface
import com.example.ui.theme.PanelBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TrackSlotBg
import com.example.ui.theme.VuGreen
import com.example.ui.theme.VuRed
import com.example.ui.theme.VuYellow
import com.example.viewmodel.DeckUiState
import com.example.viewmodel.MixerUiState

@Composable
fun VerticalFader(
    value: Float, // 0f..1f
    onValueChange: (Float) -> Unit,
    accentColor: Color,
    label: String,
    readout: String? = null,
    bipolar: Boolean = false,
    onDoubleTap: (() -> Unit)? = null,
    centerSnap: Boolean = bipolar,
    modifier: Modifier = Modifier
) {
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnDoubleTap by rememberUpdatedState(onDoubleTap)

    fun mapYToValue(y: Float, heightPx: Int): Float {
        var v = (1f - (y / heightPx.coerceAtLeast(1))).coerceIn(0f, 1f)
        if (centerSnap && kotlin.math.abs(v - 0.5f) < 0.014f) {
            v = 0.5f
        }
        return v
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (readout != null) {
            Text(
                text = readout,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = accentColor,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .widthIn(max = 44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(TrackSlotBg)
                .border(1.dp, PanelBorder, RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            val resetHandler = latestOnDoubleTap
                            if (resetHandler != null) {
                                resetHandler()
                            } else if (bipolar) {
                                latestOnValueChange(0.5f)
                            }
                        },
                        onTap = { offset ->
                            latestOnValueChange(mapYToValue(offset.y, size.height))
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        latestOnValueChange(mapYToValue(change.position.y, size.height))
                    }
                }
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width * 0.5f
                val trackTop = 10f
                val trackBot = size.height - 10f
                val trackH = (trackBot - trackTop).coerceAtLeast(1f)

                // Center groove
                drawRoundRect(
                    color = Color(0xFF050608),
                    topLeft = Offset(cx - 4f, trackTop),
                    size = Size(8f, trackH),
                    cornerRadius = CornerRadius(4f, 4f)
                )

                // Tick marks
                for (t in 0..8) {
                    val ty = trackTop + (t / 8f) * trackH
                    val tickW = if (t == 4) 14f else 8f
                    drawLine(
                        color = if (t == 4 && bipolar) accentColor.copy(alpha = 0.6f) else PanelBorder,
                        start = Offset(cx - tickW, ty),
                        end = Offset(cx + tickW, ty),
                        strokeWidth = 1.5f
                    )
                }

                // Active fill bar
                val knobY = trackBot - value.coerceIn(0f, 1f) * trackH
                if (bipolar) {
                    val midY = trackTop + trackH * 0.5f
                    val top = minOf(knobY, midY)
                    val h = kotlin.math.abs(knobY - midY)
                    drawRoundRect(
                        color = accentColor,
                        topLeft = Offset(cx - 3.5f, top),
                        size = Size(7f, h),
                        cornerRadius = CornerRadius(3f, 3f)
                    )
                } else {
                    drawRoundRect(
                        color = accentColor,
                        topLeft = Offset(cx - 3.5f, knobY),
                        size = Size(7f, trackBot - knobY),
                        cornerRadius = CornerRadius(3f, 3f)
                    )
                }

                // Brushed aluminum fader cap
                val capW = size.width * 0.82f
                val capH = 22f
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF4B5568), Color(0xFF282E3B), Color(0xFF1A1E28))
                    ),
                    topLeft = Offset(cx - capW * 0.5f, knobY - capH * 0.5f),
                    size = Size(capW, capH),
                    cornerRadius = CornerRadius(5f, 5f)
                )
                drawLine(
                    color = accentColor,
                    start = Offset(cx - capW * 0.42f, knobY),
                    end = Offset(cx + capW * 0.42f, knobY),
                    strokeWidth = 3f
                )
            }
        }

        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = TextSecondary
        )
    }
}

@Composable
fun StereoLedVuMeter(
    leftLevel: Float,
    rightLevel: Float,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(TrackSlotBg)
            .border(1.dp, PanelBorder, RoundedCornerShape(6.dp))
            .padding(4.dp)
    ) {
        val segments = 12
        val gap = 3f
        val segH = ((size.height - gap * (segments - 1)) / segments).coerceAtLeast(2f)
        val colW = (size.width - 4f) * 0.5f

        val lLit = (leftLevel.coerceIn(0f, 1f) * segments).toInt()
        val rLit = (rightLevel.coerceIn(0f, 1f) * segments).toInt()

        for (i in 0 until segments) {
            val y = size.height - (i + 1) * (segH + gap) + gap
            val baseColor = when {
                i >= 10 -> VuRed
                i >= 7 -> VuYellow
                else -> VuGreen
            }
            drawRoundRect(
                color = if (i < lLit) baseColor else baseColor.copy(alpha = 0.14f),
                topLeft = Offset(0f, y),
                size = Size(colW, segH),
                cornerRadius = CornerRadius(2f, 2f)
            )
            drawRoundRect(
                color = if (i < rLit) baseColor else baseColor.copy(alpha = 0.14f),
                topLeft = Offset(colW + 4f, y),
                size = Size(colW, segH),
                cornerRadius = CornerRadius(2f, 2f)
            )
        }
    }
}

@Composable
fun CenterMixerColumn(
    deckA: DeckUiState,
    deckB: DeckUiState,
    mixer: MixerUiState,
    onVolumeChange: (Int, Float) -> Unit,
    onSyncToggle: (Int) -> Unit,
    onSetMaster: (Int) -> Unit,
    onAutoMix: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPhaseStrip by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ConsoleCharcoal)
            .border(1.dp, PanelBorder, RoundedCornerShape(12.dp))
            .padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // SYNC A | SYNC B
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SyncBadgeButton(
                label = "SYNC",
                active = deckA.syncEnabled,
                isMaster = mixer.masterDeckIndex == 0,
                accentColor = DeckACyan,
                onClick = { onSyncToggle(0) },
                onLongSelectMaster = { onSetMaster(0) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("sync_deck_a")
            )
            SyncBadgeButton(
                label = "SYNC",
                active = deckB.syncEnabled,
                isMaster = mixer.masterDeckIndex == 1,
                accentColor = DeckBAmber,
                onClick = { onSyncToggle(1) },
                onLongSelectMaster = { onSetMaster(1) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("sync_deck_b")
            )
        }

        Spacer(Modifier.height(4.dp))

        // Channel A Volume | (Stereo LED VU or Parallel Phase Strip) | Channel B Volume
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VerticalFader(
                value = deckA.volume,
                onValueChange = { onVolumeChange(0, it) },
                accentColor = DeckACyan,
                label = "VOL",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("deck_a_volume")
            )
            Column(
                modifier = Modifier
                    .width(if (showPhaseStrip) 36.dp else 24.dp)
                    .fillMaxHeight()
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("center_meter_mode_toggle")
                        .clip(RoundedCornerShape(4.dp))
                        .background(DeckSurface)
                        .border(1.dp, if (showPhaseStrip) DeckACyan else PanelBorder, RoundedCornerShape(4.dp))
                        .clickable { showPhaseStrip = !showPhaseStrip }
                        .padding(vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (showPhaseStrip) "PHS" else "VU",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 7.sp,
                        color = if (showPhaseStrip) DeckACyan else TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(3.dp))
                if (showPhaseStrip) {
                    ParallelPhaseStrip(
                        deckA = deckA,
                        deckB = deckB,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                } else {
                    StereoLedVuMeter(
                        leftLevel = mixer.masterPeakLeft,
                        rightLevel = mixer.masterPeakRight,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    )
                }
            }
            VerticalFader(
                value = deckB.volume,
                onValueChange = { onVolumeChange(1, it) },
                accentColor = DeckBAmber,
                label = "VOL",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("deck_b_volume")
            )
        }

        Spacer(Modifier.height(4.dp))

        // AUTO-MIX + MASTER toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(DeckSurface)
                    .border(1.dp, PanelBorder, RoundedCornerShape(7.dp))
                    .clickable { onSetMaster(if (mixer.masterDeckIndex == 0) 1 else 0) }
                    .padding(vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "MST ${if (mixer.masterDeckIndex == 0) "A" else "B"}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    color = if (mixer.masterDeckIndex == 0) DeckACyan else DeckBAmber,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .testTag("auto_crossfade_button")
                    .clip(RoundedCornerShape(7.dp))
                    .background(DeckSurface)
                    .border(1.dp, VuGreen.copy(alpha = 0.6f), RoundedCornerShape(7.dp))
                    .clickable(onClick = onAutoMix)
                    .padding(vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "AUTO XF",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    color = VuGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SyncBadgeButton(
    label: String,
    active: Boolean,
    isMaster: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    onLongSelectMaster: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) accentColor.copy(alpha = 0.22f) else DeckSurface)
            .border(
                width = 1.dp,
                color = when {
                    active -> accentColor
                    isMaster -> accentColor.copy(alpha = 0.45f)
                    else -> PanelBorder
                },
                shape = RoundedCornerShape(8.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongSelectMaster() }
                )
            }
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isMaster) "MSTR" else label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active || isMaster) accentColor else TextSecondary,
            fontWeight = FontWeight.Bold
        )
    }
}

