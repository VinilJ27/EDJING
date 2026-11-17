package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.example.ui.components.CenterMixerColumn
import com.example.ui.components.CrossfaderBar
import com.example.ui.components.DualTopWaveformHeader
import com.example.ui.components.EffectsPanel
import com.example.ui.components.EqFilterPanel
import com.example.ui.components.HotCuesPanel
import com.example.ui.components.LoopPanel
import com.example.ui.components.MiniSamplerPanel
import com.example.ui.components.TurntableView
import com.example.ui.components.VerticalFader
import com.example.ui.theme.ConsoleCharcoal
import com.example.ui.theme.DeckACyan
import com.example.ui.theme.DeckBAmber
import com.example.ui.theme.DeckSurface
import com.example.ui.theme.PanelBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TrackSlotBg
import com.example.ui.theme.VoidBlack
import com.example.viewmodel.DeckPanelMode
import com.example.viewmodel.DeckUiState
import com.example.viewmodel.DjUiState
import com.example.viewmodel.DjViewModel
import com.example.viewmodel.formatPitch
import kotlin.math.abs

@Composable
fun ConsoleScreen(
    state: DjUiState,
    viewModel: DjViewModel,
    onOpenLibraryForDeck: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBlack)
    ) {
        val narrowMultiWindow = maxWidth < 540.dp
        val contentModifier = if (narrowMultiWindow) {
            Modifier
                .width(640.dp)
                .fillMaxHeight()
        } else {
            Modifier.fillMaxSize()
        }

        Box(
            modifier = if (narrowMultiWindow) {
                Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
            } else {
                Modifier.fillMaxSize()
            }
        ) {
            Column(modifier = contentModifier) {
                // Top Dual Waveform & BPM Header
                DualTopWaveformHeader(
                    deckA = state.deckA,
                    deckB = state.deckB,
                    masterDeckIndex = state.mixer.masterDeckIndex,
                    isRecording = state.recording.isRecording,
                    recordedMs = state.recording.recordedMs,
                    onSeekDeck = { deckIdx, frac -> viewModel.seekDeckFraction(deckIdx, frac) },
                    onToggleRecord = { viewModel.toggleRecording() },
                    onResetPitch = { deckIdx -> viewModel.resetPitch(deckIdx) },
                    onCommitBpm = { deckIdx, bpm -> viewModel.setManualBpm(deckIdx, bpm) }
                )

                LandscapeConsoleBody(
                    state = state,
                    viewModel = viewModel,
                    onOpenLibraryForDeck = onOpenLibraryForDeck,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun LandscapeConsoleBody(
    state: DjUiState,
    viewModel: DjViewModel,
    onOpenLibraryForDeck: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 5.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Main Workspace: [Tabs A] [Deck A] [Pitch A] [Center Mixer] [Pitch B] [Deck B] [Tabs B]
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            VerticalPanelTabs(
                selected = state.deckA.panelMode,
                accentColor = DeckACyan,
                onSelect = { viewModel.setDeckPanelMode(0, it) },
                modifier = Modifier
                    .width(54.dp)
                    .fillMaxHeight()
            )

            DeckWorkspaceCard(
                deck = state.deckA,
                accentColor = DeckACyan,
                state = state,
                viewModel = viewModel,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )

            PitchFaderColumn(
                deck = state.deckA,
                pitchRangePercent = state.settings.pitchRangePercent,
                accentColor = DeckACyan,
                onPitchChange = { viewModel.setPitchOffset(0, it) },
                onReset = { viewModel.resetPitch(0) },
                onCycleRange = { viewModel.cyclePitchRange() },
                modifier = Modifier
                    .width(44.dp)
                    .fillMaxHeight()
            )

            CenterMixerColumn(
                deckA = state.deckA,
                deckB = state.deckB,
                mixer = state.mixer,
                onVolumeChange = { idx, v -> viewModel.setDeckVolume(idx, v) },
                onSyncToggle = { idx -> viewModel.toggleSync(idx) },
                onSetMaster = { idx -> viewModel.setMasterDeck(idx) },
                onAutoMix = { viewModel.triggerAutoCrossfade() },
                modifier = Modifier
                    .width(116.dp)
                    .fillMaxHeight()
            )

            PitchFaderColumn(
                deck = state.deckB,
                pitchRangePercent = state.settings.pitchRangePercent,
                accentColor = DeckBAmber,
                onPitchChange = { viewModel.setPitchOffset(1, it) },
                onReset = { viewModel.resetPitch(1) },
                onCycleRange = { viewModel.cyclePitchRange() },
                modifier = Modifier
                    .width(44.dp)
                    .fillMaxHeight()
            )

            DeckWorkspaceCard(
                deck = state.deckB,
                accentColor = DeckBAmber,
                state = state,
                viewModel = viewModel,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )

            VerticalPanelTabs(
                selected = state.deckB.panelMode,
                accentColor = DeckBAmber,
                onSelect = { viewModel.setDeckPanelMode(1, it) },
                modifier = Modifier
                    .width(54.dp)
                    .fillMaxHeight()
            )
        }

        // Bottom Transport & Crossfader Bar (edjing Mix mirrored layout)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DeckTransportRow(
                deck = state.deckA,
                accentColor = DeckACyan,
                isRightSide = false,
                onLoadClick = { onOpenLibraryForDeck(0) },
                onPlayPause = { viewModel.togglePlay(0) },
                onCuePress = { pressed -> viewModel.setCuePreview(0, pressed) },
                onSetCue = { viewModel.setMainCueHere(0) },
                onResetPitch = { viewModel.resetPitch(0) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            CrossfaderBar(
                value = state.mixer.crossfader,
                curveMode = state.mixer.crossfaderCurve,
                onValueChange = { viewModel.setCrossfader(it) },
                onDragStateChange = { viewModel.setCrossfaderDragging(it) },
                onReset = { viewModel.resetCrossfader() },
                onCycleCurve = { viewModel.cycleCrossfaderCurve() },
                modifier = Modifier
                    .weight(0.92f)
                    .fillMaxHeight()
            )
            DeckTransportRow(
                deck = state.deckB,
                accentColor = DeckBAmber,
                isRightSide = true,
                onLoadClick = { onOpenLibraryForDeck(1) },
                onPlayPause = { viewModel.togglePlay(1) },
                onCuePress = { pressed -> viewModel.setCuePreview(1, pressed) },
                onSetCue = { viewModel.setMainCueHere(1) },
                onResetPitch = { viewModel.resetPitch(1) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun VerticalPanelTabs(
    selected: DeckPanelMode,
    accentColor: Color,
    onSelect: (DeckPanelMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        DeckPanelMode.entries.forEach { mode ->
            val isSel = selected == mode
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (isSel) accentColor.copy(alpha = 0.22f) else ConsoleCharcoal)
                    .border(
                        width = if (isSel) 1.5.dp else 1.dp,
                        color = if (isSel) accentColor else PanelBorder,
                        shape = RoundedCornerShape(7.dp)
                    )
                    .clickable { onSelect(mode) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    color = if (isSel) accentColor else TextSecondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DeckWorkspaceCard(
    deck: DeckUiState,
    accentColor: Color,
    state: DjUiState,
    viewModel: DjViewModel,
    modifier: Modifier = Modifier
) {
    val idx = deck.deckIndex
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ConsoleCharcoal)
            .border(1.dp, PanelBorder, RoundedCornerShape(12.dp))
    ) {
        when (deck.panelMode) {
            DeckPanelMode.VINYL -> TurntableView(
                deck = deck,
                accentColor = accentColor,
                vinylRpm = state.settings.vinylRpm,
                onScratchStart = { viewModel.onJogScratchStart(idx) },
                onScratchDelta = { d, dtMs -> viewModel.onJogScratchDelta(idx, d, dtMs) },
                onScratchEnd = { viewModel.onJogScratchEnd(idx) },
                onPitchBend = { b -> viewModel.onPitchBend(idx, b) }
            )
            DeckPanelMode.EQ -> EqFilterPanel(
                deck = deck,
                accentColor = accentColor,
                onEqChange = { l, m, h -> viewModel.setDeckEq(idx, l, m, h) },
                onFilterChange = { v -> viewModel.setDeckFilter(idx, v) },
                onTrimChange = { v -> viewModel.setDeckTrim(idx, v) },
                onToggleKeyLock = { viewModel.toggleKeyLock(idx) }
            )
            DeckPanelMode.FX -> EffectsPanel(
                deck = deck,
                accentColor = accentColor,
                onSelectEffect = { fx, wet, param -> viewModel.setDeckEffect(idx, fx, wet, param) }
            )
            DeckPanelMode.LOOP -> LoopPanel(
                deck = deck,
                accentColor = accentColor,
                onBeatLoop = { beats -> viewModel.toggleBeatLoop(idx, beats) },
                onManualIn = { viewModel.setManualLoopIn(idx) },
                onManualOut = { viewModel.setManualLoopOut(idx) },
                onExitLoop = { viewModel.exitLoop(idx) },
                onBeatJump = { jump -> viewModel.beatJump(idx, jump) },
                onToggleQuantize = { viewModel.toggleQuantize(idx) },
                onToggleSlip = { viewModel.toggleSlipMode(idx) }
            )
            DeckPanelMode.CUES -> HotCuesPanel(
                deck = deck,
                accentColor = accentColor,
                onTriggerHotCue = { slot -> viewModel.triggerHotCue(idx, slot) },
                onClearHotCue = { slot -> viewModel.clearHotCue(idx, slot) },
                onSetMainCue = { viewModel.setMainCueHere(idx) }
            )
            DeckPanelMode.SAMPLER -> MiniSamplerPanel(
                pads = state.samplerPads,
                onTriggerPad = { padIdx -> viewModel.triggerSamplerPad(padIdx) }
            )
        }

        if (deck.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(VoidBlack.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = accentColor, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
private fun PitchFaderColumn(
    deck: DeckUiState,
    pitchRangePercent: Int,
    accentColor: Color,
    onPitchChange: (Float) -> Unit,
    onReset: () -> Unit,
    onCycleRange: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pitchRange = (pitchRangePercent / 100f).coerceAtLeast(0.01f)
    val norm = ((deck.pitchOffset / pitchRange) + 1f) * 0.5f
    val prefix = if (deck.deckIndex == 0) "deck_a" else "deck_b"

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ConsoleCharcoal)
            .border(1.dp, PanelBorder, RoundedCornerShape(10.dp))
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Range selector & display pill (±8%, ±16%, ±32%)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("${prefix}_pitch_range")
                .clip(RoundedCornerShape(6.dp))
                .background(DeckSurface)
                .border(1.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                .clickable(onClick = onCycleRange)
                .padding(vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "±$pitchRangePercent%",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.sp,
                color = accentColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(2.dp))
        VerticalFader(
            value = norm.coerceIn(0f, 1f),
            onValueChange = { v -> onPitchChange((v * 2f - 1f) * pitchRange) },
            onDoubleTap = onReset,
            accentColor = accentColor,
            label = "PITCH",
            readout = formatPitch(deck.pitchOffset),
            bipolar = true,
            modifier = Modifier
                .weight(1f)
                .testTag("${prefix}_pitch_slider")
        )
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("${prefix}_pitch_fader_reset")
                .clip(RoundedCornerShape(6.dp))
                .background(TrackSlotBg)
                .clickable(onClick = onReset)
                .padding(vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.RestartAlt,
                contentDescription = "Reset pitch",
                tint = if (abs(deck.pitchOffset) > 0.001f) accentColor else TextSecondary,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun DeckTransportRow(
    deck: DeckUiState,
    accentColor: Color,
    isRightSide: Boolean,
    onLoadClick: () -> Unit,
    onPlayPause: () -> Unit,
    onCuePress: (Boolean) -> Unit,
    onSetCue: () -> Unit,
    onResetPitch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prefix = if (deck.deckIndex == 0) "deck_a" else "deck_b"
    val pitchShifted = abs(deck.pitchOffset) > 0.001f

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ConsoleCharcoal)
            .border(1.dp, PanelBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 3.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val loadButton: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .fillMaxHeight()
                    .testTag("${prefix}_load_button")
                    .clip(RoundedCornerShape(7.dp))
                    .background(DeckSurface)
                    .border(1.dp, PanelBorder, RoundedCornerShape(7.dp))
                    .clickable(onClick = onLoadClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LibraryMusic,
                    contentDescription = "Load track",
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        val playButton: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .fillMaxHeight()
                    .testTag("${prefix}_play_button")
                    .clip(CircleShape)
                    .background(if (deck.isPlaying) accentColor else DeckSurface)
                    .border(1.5.dp, accentColor, CircleShape)
                    .clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (deck.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (deck.isPlaying) "Pause" else "Play",
                    tint = if (deck.isPlaying) VoidBlack else accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        val setCueButton: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .weight(0.85f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(7.dp))
                    .background(DeckSurface)
                    .border(1.dp, PanelBorder, RoundedCornerShape(7.dp))
                    .clickable(onClick = onSetCue),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SET",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        val cueButton: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("${prefix}_cue_button")
                    .clip(RoundedCornerShape(7.dp))
                    .background(DeckSurface)
                    .border(1.dp, accentColor.copy(alpha = 0.65f), RoundedCornerShape(7.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onCuePress(true)
                                tryAwaitRelease()
                                onCuePress(false)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CUE",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        val resetButton: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .weight(0.95f)
                    .fillMaxHeight()
                    .testTag("${prefix}_reset_pitch_button")
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (pitchShifted) accentColor.copy(alpha = 0.14f) else DeckSurface)
                    .border(
                        width = 1.dp,
                        color = if (pitchShifted) accentColor else PanelBorder,
                        shape = RoundedCornerShape(7.dp)
                    )
                    .clickable(onClick = onResetPitch),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "RESET",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    color = if (pitchShifted) accentColor else TextSecondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Mirrored edjing layout:
        // Left:  [Load] [Play] [SET] [CUE] [RESET]
        // Right: [RESET] [CUE] [SET] [Play] [Load]
        if (!isRightSide) {
            loadButton()
            playButton()
            setCueButton()
            cueButton()
            resetButton()
        } else {
            resetButton()
            cueButton()
            setCueButton()
            playButton()
            loadButton()
        }
    }
}
