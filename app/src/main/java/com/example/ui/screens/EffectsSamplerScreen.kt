package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.EffectsPanel
import com.example.ui.theme.ConsoleCharcoal
import com.example.ui.theme.DeckACyan
import com.example.ui.theme.DeckBAmber
import com.example.ui.theme.DeckSurface
import com.example.ui.theme.PanelBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack
import com.example.viewmodel.DjUiState
import com.example.viewmodel.DjViewModel
import kotlin.math.roundToInt

@Composable
fun EffectsSamplerScreen(
    state: DjUiState,
    viewModel: DjViewModel,
    modifier: Modifier = Modifier
) {
    var pendingPadForImport by remember { mutableIntStateOf(-1) }
    var selectedPadIdx by remember { mutableIntStateOf(0) }

    val samplePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && pendingPadForImport in 0..7) {
            viewModel.loadCustomSamplerPad(pendingPadForImport, uri)
        }
        pendingPadForImport = -1
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Effects & Sampler Studio",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )

        // 8-Pad Performance Sampler Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ConsoleCharcoal)
                .border(1.dp, PanelBorder, RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = DeckACyan)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "8-PAD PERFORMANCE SAMPLER",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                }
                Text(
                    text = "MASTER ${(state.mixer.samplerVolume * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = DeckACyan
                )
            }

            Slider(
                value = state.mixer.samplerVolume,
                onValueChange = { viewModel.setSamplerMasterVolume(it) },
                valueRange = 0f..1.2f,
                colors = SliderDefaults.colors(thumbColor = DeckACyan, activeTrackColor = DeckACyan),
                modifier = Modifier.height(26.dp)
            )

            // 4x2 Pads Grid
            state.samplerPads.chunked(4).forEach { rowPads ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowPads.forEach { pad ->
                        val color = Color(pad.colorHex)
                        val isSelected = selectedPadIdx == pad.padIndex
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .height(86.dp)
                                .testTag("studio_sampler_pad_${pad.padIndex}")
                                .clip(RoundedCornerShape(12.dp))
                                .background(color.copy(alpha = if (isSelected) 0.28f else 0.16f))
                                .border(if (isSelected) 2.dp else 1.dp, color, RoundedCornerShape(12.dp))
                                .clickable {
                                    selectedPadIdx = pad.padIndex
                                    viewModel.triggerSamplerPad(pad.padIndex)
                                }
                                .padding(8.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "P${pad.padIndex + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = color
                                )
                                IconButton(
                                    onClick = {
                                        pendingPadForImport = pad.padIndex
                                        samplePicker.launch(arrayOf("audio/*"))
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = "Load custom sample",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Text(
                                text = pad.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Selected Pad Fine Tuning (Volume & Pitch)
            state.samplerPads.firstOrNull { it.padIndex == selectedPadIdx }?.let { pad ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DeckSurface)
                        .padding(10.dp)
                ) {
                    Text(
                        text = "PAD ${pad.padIndex + 1} (${pad.label}) • VOL ${(pad.volume * 100).roundToInt()}% • PITCH ${String.format(java.util.Locale.US, "%.2fx", pad.pitch)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(pad.colorHex)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Slider(
                            value = pad.volume,
                            onValueChange = { viewModel.updateSamplerPadParams(pad.copy(volume = it)) },
                            valueRange = 0f..1.4f,
                            modifier = Modifier.weight(1f).height(26.dp)
                        )
                        Slider(
                            value = pad.pitch,
                            onValueChange = { viewModel.updateSamplerPadParams(pad.copy(pitch = it)) },
                            valueRange = 0.5f..2.0f,
                            modifier = Modifier.weight(1f).height(26.dp)
                        )
                    }
                }
            }
        }

        // Dual-Deck Live DSP FX Matrix
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(290.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ConsoleCharcoal)
                    .border(1.dp, DeckACyan.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = "DECK A FX",
                    style = MaterialTheme.typography.labelLarge,
                    color = DeckACyan,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
                EffectsPanel(
                    deck = state.deckA,
                    accentColor = DeckACyan,
                    onSelectEffect = { fx, wet, param -> viewModel.setDeckEffect(0, fx, wet, param) }
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(290.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ConsoleCharcoal)
                    .border(1.dp, DeckBAmber.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = "DECK B FX",
                    style = MaterialTheme.typography.labelLarge,
                    color = DeckBAmber,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
                EffectsPanel(
                    deck = state.deckB,
                    accentColor = DeckBAmber,
                    onSelectEffect = { fx, wet, param -> viewModel.setDeckEffect(1, fx, wet, param) }
                )
            }
        }
    }
}
