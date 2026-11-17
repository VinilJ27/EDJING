package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.EffectType
import com.example.data.SamplerPadEntity
import com.example.ui.theme.DeckSurface
import com.example.ui.theme.PanelBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TrackSlotBg
import com.example.ui.theme.VoidBlack
import com.example.ui.theme.VuGreen
import com.example.ui.theme.VuRed
import com.example.viewmodel.DeckUiState
import com.example.viewmodel.formatMs
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun EqFilterPanel(
    deck: DeckUiState,
    accentColor: Color,
    onEqChange: (Float, Float, Float) -> Unit,
    onFilterChange: (Float) -> Unit,
    onTrimChange: (Float) -> Unit,
    onToggleKeyLock: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "3-BAND EQ & FILTER",
                style = MaterialTheme.typography.labelMedium,
                color = accentColor
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallTogglePill(
                    label = "KEY LOCK",
                    active = deck.keyLock,
                    accentColor = accentColor,
                    onClick = onToggleKeyLock
                )
                SmallTogglePill(
                    label = "RESET",
                    active = false,
                    accentColor = TextSecondary,
                    onClick = {
                        onEqChange(0f, 0f, 0f)
                        onFilterChange(0f)
                        onTrimChange(1f)
                    }
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Map -24..+12 dB -> 0..1 (with 0 dB at 0.667f or symmetric -18..+12)
            val lowNorm = ((deck.eqLowDb + 24f) / 36f).coerceIn(0f, 1f)
            val midNorm = ((deck.eqMidDb + 24f) / 36f).coerceIn(0f, 1f)
            val highNorm = ((deck.eqHighDb + 24f) / 36f).coerceIn(0f, 1f)
            val filtNorm = ((deck.filterVal + 1f) * 0.5f).coerceIn(0f, 1f)
            val trimNorm = (deck.trimGain / 2f).coerceIn(0f, 1f)

            VerticalFader(
                value = lowNorm,
                onValueChange = { v -> onEqChange(v * 36f - 24f, deck.eqMidDb, deck.eqHighDb) },
                accentColor = accentColor,
                label = "LOW",
                readout = "${deck.eqLowDb.roundToInt()}dB",
                bipolar = true,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            VerticalFader(
                value = midNorm,
                onValueChange = { v -> onEqChange(deck.eqLowDb, v * 36f - 24f, deck.eqHighDb) },
                accentColor = accentColor,
                label = "MID",
                readout = "${deck.eqMidDb.roundToInt()}dB",
                bipolar = true,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            VerticalFader(
                value = highNorm,
                onValueChange = { v -> onEqChange(deck.eqLowDb, deck.eqMidDb, v * 36f - 24f) },
                accentColor = accentColor,
                label = "HIGH",
                readout = "${deck.eqHighDb.roundToInt()}dB",
                bipolar = true,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            VerticalFader(
                value = filtNorm,
                onValueChange = { v -> onFilterChange(v * 2f - 1f) },
                accentColor = VuGreen,
                label = "FILTER",
                readout = when {
                    deck.filterVal < -0.05f -> "LPF"
                    deck.filterVal > 0.05f -> "HPF"
                    else -> "OFF"
                },
                bipolar = true,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            VerticalFader(
                value = trimNorm,
                onValueChange = { v -> onTrimChange(v * 2f) },
                accentColor = TextPrimary,
                label = "TRIM",
                readout = "${(deck.trimGain * 100).roundToInt()}%",
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EffectsPanel(
    deck: DeckUiState,
    accentColor: Color,
    onSelectEffect: (EffectType, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DSP EFFECTS RACK",
                style = MaterialTheme.typography.labelMedium,
                color = accentColor
            )
            if (deck.activeEffect != EffectType.NONE) {
                SmallTogglePill(
                    label = "BYPASS",
                    active = true,
                    accentColor = VuRed,
                    onClick = { onSelectEffect(EffectType.NONE, deck.fxWetDry, deck.fxParam) }
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            maxItemsInEachRow = 3
        ) {
            EffectType.entries.filter { it != EffectType.NONE }.forEach { fx ->
                val active = deck.activeEffect == fx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("fx_${fx.name.lowercase(Locale.US)}")
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) accentColor.copy(alpha = 0.24f) else DeckSurface)
                        .border(1.dp, if (active) accentColor else PanelBorder, RoundedCornerShape(8.dp))
                        .clickable {
                            val next = if (active) EffectType.NONE else fx
                            onSelectEffect(next, deck.fxWetDry, deck.fxParam)
                        }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = fx.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) accentColor else TextPrimary,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "WET / DRY  ${(deck.fxWetDry * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Slider(
            value = deck.fxWetDry,
            onValueChange = { onSelectEffect(deck.activeEffect, it, deck.fxParam) },
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor
            ),
            modifier = Modifier.height(28.dp)
        )

        Text(
            text = "DEPTH / RATE  ${(deck.fxParam * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Slider(
            value = deck.fxParam,
            onValueChange = { onSelectEffect(deck.activeEffect, deck.fxWetDry, it) },
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor
            ),
            modifier = Modifier.height(28.dp)
        )
    }
}

@Composable
fun LoopPanel(
    deck: DeckUiState,
    accentColor: Color,
    onBeatLoop: (Int) -> Unit,
    onManualIn: () -> Unit,
    onManualOut: () -> Unit,
    onExitLoop: () -> Unit,
    onBeatJump: (Int) -> Unit,
    onToggleQuantize: () -> Unit,
    onToggleSlip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val beatSizes = listOf(1, 2, 4, 8, 16, 32)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BEAT LOOPS",
                style = MaterialTheme.typography.labelMedium,
                color = accentColor
            )
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                SmallTogglePill("Q", deck.quantize, accentColor, onToggleQuantize)
                SmallTogglePill("SLIP", deck.slipMode, VuGreen, onToggleSlip)
            }
        }

        // 1, 2, 4, 8, 16, 32 beat grid
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            beatSizes.chunked(3).forEach { rowBeats ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    rowBeats.forEach { beats ->
                        val active = deck.loopActive && deck.loopBeats == beats
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .testTag("loop_${beats}_beats")
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) accentColor.copy(alpha = 0.28f) else DeckSurface)
                                .border(1.dp, if (active) accentColor else PanelBorder, RoundedCornerShape(8.dp))
                                .clickable { onBeatLoop(beats) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$beats",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (active) accentColor else TextPrimary
                            )
                        }
                    }
                }
            }
        }

        // Manual IN / OUT / EXIT
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            SmallActionBox(
                label = if (deck.manualLoopInMs != null) "IN ✓" else "IN",
                active = deck.manualLoopInMs != null,
                accentColor = accentColor,
                onClick = onManualIn,
                modifier = Modifier.weight(1f)
            )
            SmallActionBox(
                label = "OUT",
                active = deck.loopActive && deck.loopBeats == 0,
                accentColor = accentColor,
                onClick = onManualOut,
                modifier = Modifier.weight(1f)
            )
            SmallActionBox(
                label = "EXIT",
                active = deck.loopActive,
                accentColor = VuRed,
                onClick = onExitLoop,
                modifier = Modifier.weight(1f)
            )
        }

        // Beat Jump
        Text(
            text = "BEAT JUMP",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            listOf(-4, -1, 1, 4).forEach { jump ->
                SmallActionBox(
                    label = if (jump > 0) "+$jump" else "$jump",
                    active = false,
                    accentColor = accentColor,
                    onClick = { onBeatJump(jump) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun HotCuesPanel(
    deck: DeckUiState,
    accentColor: Color,
    onTriggerHotCue: (Int) -> Unit,
    onClearHotCue: (Int) -> Unit,
    onSetMainCue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "HOT CUES (HOLD TO CLEAR)",
                style = MaterialTheme.typography.labelSmall,
                color = accentColor
            )
            SmallTogglePill("SET CUE", false, TextPrimary, onSetMainCue)
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(0 to 1, 2 to 3).forEach { (a, b) ->
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(a, b).forEach { slot ->
                        val cue = deck.hotCues[slot]
                        val padColors = listOf(Color(0xFF00E5FF), Color(0xFFFFB300), Color(0xFF22E584), Color(0xFFFF5252))
                        val padColor = padColors[slot]
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("hot_cue_$slot")
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (cue != null) padColor.copy(alpha = 0.24f) else DeckSurface)
                                .border(1.5.dp, if (cue != null) padColor else PanelBorder, RoundedCornerShape(10.dp))
                                .pointerInput(cue) {
                                    detectTapGestures(
                                        onTap = { onTriggerHotCue(slot) },
                                        onLongPress = { onClearHotCue(slot) }
                                    )
                                }
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "CUE ${slot + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (cue != null) padColor else TextSecondary
                                )
                                Text(
                                    text = if (cue != null) formatMs(cue.positionMs) else "EMPTY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MiniSamplerPanel(
    pads: List<SamplerPadEntity>,
    onTriggerPad: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = "PERFORMANCE SAMPLER",
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary
        )
        val rows = pads.chunked(2)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            rows.forEach { rowPads ->
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    rowPads.forEach { pad ->
                        val color = Color(pad.colorHex)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("mini_sampler_pad_${pad.padIndex}")
                                .clip(RoundedCornerShape(8.dp))
                                .background(color.copy(alpha = 0.22f))
                                .border(1.5.dp, color, RoundedCornerShape(8.dp))
                                .clickable { onTriggerPad(pad.padIndex) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = pad.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallTogglePill(
    label: String,
    active: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) accentColor.copy(alpha = 0.22f) else TrackSlotBg)
            .border(1.dp, if (active) accentColor else PanelBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) accentColor else TextSecondary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SmallActionBox(
    label: String,
    active: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) accentColor.copy(alpha = 0.22f) else VoidBlack)
            .border(1.dp, if (active) accentColor else PanelBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) accentColor else TextPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}
