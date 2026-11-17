package com.example.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ConsoleCharcoal
import com.example.ui.theme.DeckACyan
import com.example.ui.theme.DeckBAmber
import com.example.ui.theme.DeckSurface
import com.example.ui.theme.PanelBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack
import com.example.ui.theme.VuGreen
import com.example.viewmodel.DjUiState
import com.example.viewmodel.DjViewModel
import java.util.Locale

@Composable
fun SettingsScreen(
    state: DjUiState,
    viewModel: DjViewModel,
    modifier: Modifier = Modifier
) {
    val diag = state.diagnostics
    val settings = state.settings
    val latencyMs = (diag.bufferSizeFrames.toFloat() / diag.sampleRate.coerceAtLeast(1)) * 1000f

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBlack)
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Audio & Mixer Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )

        // Live Oboe Audio Engine Telemetry Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ConsoleCharcoal)
                .border(1.dp, DeckACyan.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "OBOE / AAUDIO ENGINE DIAGNOSTICS",
                style = MaterialTheme.typography.labelLarge,
                color = DeckACyan
            )
            DiagRow("Audio API", diag.audioApi, VuGreen)
            DiagRow("Sample Rate", "${diag.sampleRate} Hz", TextPrimary)
            DiagRow("Buffer / Burst", "${diag.bufferSizeFrames} / ${diag.framesPerBurst} frames", TextPrimary)
            DiagRow("Est. Output Latency", String.format(Locale.US, "%.1f ms", latencyMs), DeckACyan)
            DiagRow("Low-Latency Mode", if (diag.isLowLatency) "Active" else "Standard", VuGreen)
            DiagRow("Output Device", diag.routeState.outputDeviceLabel, DeckBAmber)
        }

        // Crossfader Curve
        SettingSectionCard(title = "CROSSFADER CURVE") {
            val curves = listOf(0 to "Smooth Blend", 1 to "Constant Power", 2 to "Sharp Scratch")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                curves.forEach { (id, label) ->
                    val sel = settings.crossfaderCurve == id
                    OptionChip(
                        label = label,
                        selected = sel,
                        accentColor = DeckACyan,
                        onClick = { viewModel.updateSettings(crossfaderCurve = id) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Auto-Crossfade Duration
        SettingSectionCard(title = "AUTO-CROSSFADE DURATION") {
            val durations = listOf(2f to "2 sec", 4f to "4 sec", 8f to "8 sec")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                durations.forEach { (sec, label) ->
                    OptionChip(
                        label = label,
                        selected = settings.autoCrossfadeSeconds == sec,
                        accentColor = VuGreen,
                        onClick = { viewModel.updateSettings(autoCrossfadeSeconds = sec) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Pitch Range & Vinyl RPM
        SettingSectionCard(title = "PITCH SLIDER RANGE & TURNTABLE SPEED") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(8, 16, 32).forEach { pct ->
                    OptionChip(
                        label = "±$pct%",
                        selected = settings.pitchRangePercent == pct,
                        accentColor = DeckBAmber,
                        onClick = { viewModel.updateSettings(pitchRangePercent = pct) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(33 to "33⅓ RPM", 45 to "45 RPM").forEach { (rpm, label) ->
                    OptionChip(
                        label = label,
                        selected = settings.vinylRpm == rpm,
                        accentColor = DeckACyan,
                        onClick = { viewModel.updateSettings(vinylRpm = rpm) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Toggles
        SettingSectionCard(title = "PLAYBACK DEFAULTS") {
            SettingToggleRow(
                title = "Key Lock (Time-Stretch)",
                subtitle = "Preserve musical key when changing BPM",
                checked = settings.keyLockDefault,
                onCheckedChange = { viewModel.updateSettings(keyLockDefault = it) }
            )
            SettingToggleRow(
                title = "Beat-Grid Quantize",
                subtitle = "Snap cues and loops to nearest beat",
                checked = settings.quantizeDefault,
                onCheckedChange = { viewModel.updateSettings(quantizeDefault = it) }
            )
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.labelMedium, color = valueColor)
    }
}

@Composable
private fun SettingSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ConsoleCharcoal)
            .border(1.dp, PanelBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun OptionChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) accentColor.copy(alpha = 0.22f) else DeckSurface)
            .border(1.dp, if (selected) accentColor else PanelBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) accentColor else TextPrimary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = VoidBlack, checkedTrackColor = DeckACyan)
        )
    }
}
