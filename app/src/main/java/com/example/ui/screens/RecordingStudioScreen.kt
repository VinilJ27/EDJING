package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.RecordingEntity
import com.example.ui.components.StereoLedVuMeter
import com.example.ui.theme.ConsoleCharcoal
import com.example.ui.theme.DeckACyan
import com.example.ui.theme.DeckBAmber
import com.example.ui.theme.DeckSurface
import com.example.ui.theme.PanelBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack
import com.example.ui.theme.VuGreen
import com.example.ui.theme.VuRed
import com.example.viewmodel.DjUiState
import com.example.viewmodel.DjViewModel
import com.example.viewmodel.formatMs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecordingStudioScreen(
    state: DjUiState,
    viewModel: DjViewModel,
    modifier: Modifier = Modifier
) {
    val rec = state.recording
    var pendingExportRecording by remember { mutableStateOf<RecordingEntity?>(null) }

    val wavExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/wav")
    ) { uri ->
        val target = pendingExportRecording
        if (uri != null && target != null) {
            viewModel.exportRecordingToSafWav(target, uri)
        }
        pendingExportRecording = null
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBlack)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Left Pane: Studio Header + Hero Recorder Card + Status
        Column(
            modifier = Modifier
                .weight(0.92f)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Recording Studio",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, if (rec.isRecording) VuRed else PanelBorder, RoundedCornerShape(16.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_studio_hero_1794566130572),
                    contentDescription = "DJ Recording Studio",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(VoidBlack.copy(alpha = 0.74f))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (rec.isRecording) "RECORDING MASTER OUTPUT" else "MASTER RECORDER (48 kHz WAV)",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (rec.isRecording) VuRed else DeckACyan
                            )
                            Text(
                                text = formatMs(rec.recordedMs),
                                style = MaterialTheme.typography.displaySmall,
                                color = TextPrimary
                            )
                            Text(
                                text = "Captures Deck A + B + EQ + FX + Sampler\nCodec: ${if (rec.mp3Supported) "MP3 & WAV Ready" else "WAV / AAC M4A Ready"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StereoLedVuMeter(
                                leftLevel = state.mixer.masterPeakLeft,
                                rightLevel = state.mixer.masterPeakRight,
                                modifier = Modifier
                                    .width(22.dp)
                                    .height(110.dp)
                            )

                            Button(
                                onClick = { viewModel.toggleRecording() },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (rec.isRecording) DeckSurface else VuRed,
                                    contentColor = TextPrimary
                                ),
                                modifier = Modifier
                                    .size(68.dp)
                                    .testTag("studio_record_button")
                            ) {
                                Icon(
                                    imageVector = if (rec.isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                                    contentDescription = if (rec.isRecording) "Stop recording" else "Start recording",
                                    tint = if (rec.isRecording) VuRed else TextPrimary,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (rec.isExporting) {
                LinearProgressIndicator(color = DeckACyan, modifier = Modifier.fillMaxWidth())
            }
            rec.statusMessage?.let { msg ->
                Text(msg, style = MaterialTheme.typography.bodySmall, color = VuGreen)
            }
        }

        // Right Pane: Saved Mixes List
        Column(
            modifier = Modifier
                .weight(1.08f)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Saved Mixes (${rec.recordings.size})",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )

            if (rec.recordings.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ConsoleCharcoal)
                        .border(1.dp, PanelBorder, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                        Icon(Icons.Default.AudioFile, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Press Record or tap REC in the DJ Console to capture your live mix.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(rec.recordings, key = { it.id }) { item ->
                        RecordingRowCard(
                            item = item,
                            isPlayingPreview = rec.previewingId == item.id,
                            onPlayToggle = { viewModel.togglePreviewRecording(item) },
                            onExportWav = {
                                pendingExportRecording = item
                                wavExportLauncher.launch("${item.title.replace(" ", "_")}.wav")
                            },
                            onTranscodeM4a = { viewModel.transcodeAndSaveM4a(item) },
                            onShare = { viewModel.shareRecording(item) },
                            onDelete = { viewModel.deleteRecording(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingRowCard(
    item: RecordingEntity,
    isPlayingPreview: Boolean,
    onPlayToggle: () -> Unit,
    onExportWav: () -> Unit,
    onTranscodeM4a: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(item.createdAt) {
        SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date(item.createdAt))
    }
    val sizeMb = String.format(Locale.US, "%.1f MB", item.fileSizeBytes / (1024f * 1024f))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ConsoleCharcoal)
            .border(1.dp, PanelBorder, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPlayToggle,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isPlayingPreview) DeckACyan else DeckSurface)
            ) {
                Icon(
                    imageVector = if (isPlayingPreview) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Preview mix",
                    tint = if (isPlayingPreview) VoidBlack else DeckACyan
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(
                    text = "${item.format} • ${formatMs(item.durationMs)} • $sizeMb • ${item.bpmSummary} • $dateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, contentDescription = "Share mix", tint = DeckACyan)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete mix", tint = VuRed)
            }
        }

        if (item.format == "WAV") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onExportWav,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = DeckACyan)
                    Spacer(Modifier.width(4.dp))
                    Text("Save WAV", color = DeckACyan, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onTranscodeM4a,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Export AAC/M4A", color = DeckBAmber, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
