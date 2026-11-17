package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.audio.AudioAnalyzer
import com.example.data.TrackEntity
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
import com.example.viewmodel.DjUiState
import com.example.viewmodel.DjViewModel
import com.example.viewmodel.LibraryFilterTab
import com.example.viewmodel.LibrarySortMode
import com.example.viewmodel.formatMs
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun LibraryScreen(
    state: DjUiState,
    viewModel: DjViewModel,
    preferredDeckIndex: Int?,
    onTrackLoadedToDeck: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lib = state.library
    val masterDeck = if (state.mixer.masterDeckIndex == 0) state.deckA else state.deckB
    val masterCamelot = masterDeck.track?.camelotKey

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistTargetTrack by remember { mutableStateOf<TrackEntity?>(null) }

    // Drag & Drop state
    var deckABounds by remember { mutableStateOf<Rect?>(null) }
    var deckBBounds by remember { mutableStateOf<Rect?>(null) }
    var dragPointerRoot by remember { mutableStateOf(Offset.Zero) }
    var draggingTrack by remember { mutableStateOf<TrackEntity?>(null) }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importAudioUris(uris)
        }
    }

    val baseList = when (lib.filterTab) {
        LibraryFilterTab.ALL -> lib.tracks
        LibraryFilterTab.RECENT -> lib.recentTracks
        LibraryFilterTab.HARMONIC -> {
            if (masterCamelot == null) lib.tracks
            else lib.tracks.filter { AudioAnalyzer.areKeysHarmonic(masterCamelot, it.camelotKey) }
        }
        LibraryFilterTab.PLAYLISTS -> {
            if (lib.selectedPlaylistId == null) emptyList() else lib.selectedPlaylistTracks
        }
    }

    val filteredTracks = remember(baseList, lib.searchQuery, lib.sortMode) {
        val q = lib.searchQuery.trim().lowercase(Locale.US)
        val searched = if (q.isEmpty()) baseList else baseList.filter {
            it.title.lowercase(Locale.US).contains(q) ||
                it.artist.lowercase(Locale.US).contains(q) ||
                it.musicalKey.lowercase(Locale.US).contains(q)
        }
        when (lib.sortMode) {
            LibrarySortMode.RECENT -> searched.sortedByDescending { it.dateAdded }
            LibrarySortMode.TITLE -> searched.sortedBy { it.title.lowercase(Locale.US) }
            LibrarySortMode.BPM -> searched.sortedBy { it.bpm }
            LibrarySortMode.KEY -> searched.sortedBy { it.camelotKey }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBlack)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Left Pane: Import + Drop Targets + Search + Filters + Playlists
            Column(
                modifier = Modifier
                    .weight(0.38f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Music Library",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = "${lib.tracks.size} tracks",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    Button(
                        onClick = { audioPicker.launch(arrayOf("audio/*")) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DeckACyan,
                            contentColor = VoidBlack
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("import_local_music_button")
                    ) {
                        Icon(Icons.Default.LibraryAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Import", fontWeight = FontWeight.Bold)
                    }
                }

                if (lib.isImporting) {
                    LinearProgressIndicator(
                        color = DeckACyan,
                        trackColor = DeckSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                    lib.importStatus?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = DeckACyan)
                    }
                }

                // Deck A & Deck B Drag-and-Drop Targets
                val hoverA = draggingTrack != null && deckABounds?.contains(dragPointerRoot) == true
                val hoverB = draggingTrack != null && deckBBounds?.contains(dragPointerRoot) == true

                DeckDropZone(
                    deckLabel = "DROP TO DECK A",
                    loadedTitle = state.deckA.track?.title ?: "Empty Deck A",
                    accentColor = DeckACyan,
                    isHighlighted = hoverA || preferredDeckIndex == 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { deckABounds = it.boundsInRoot() }
                )
                DeckDropZone(
                    deckLabel = "DROP TO DECK B",
                    loadedTitle = state.deckB.track?.title ?: "Empty Deck B",
                    accentColor = DeckBAmber,
                    isHighlighted = hoverB || preferredDeckIndex == 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { deckBBounds = it.boundsInRoot() }
                )

                OutlinedTextField(
                    value = lib.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search tracks...", color = TextSecondary) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DeckACyan,
                        unfocusedBorderColor = PanelBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = ConsoleCharcoal,
                        unfocusedContainerColor = ConsoleCharcoal
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("library_search_input")
                )

                // Filter Tabs
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(LibraryFilterTab.entries) { tab ->
                        val sel = lib.filterTab == tab
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (sel) DeckACyan.copy(alpha = 0.22f) else ConsoleCharcoal)
                                .border(1.dp, if (sel) DeckACyan else PanelBorder, RoundedCornerShape(20.dp))
                                .clickable { viewModel.setFilterTab(tab) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (sel) DeckACyan else TextSecondary
                            )
                        }
                    }
                }

                if (lib.filterTab == LibraryFilterTab.PLAYLISTS) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DeckSurface)
                                    .border(1.dp, DeckBAmber, RoundedCornerShape(10.dp))
                                    .clickable { showCreatePlaylistDialog = true }
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = DeckBAmber, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("New", style = MaterialTheme.typography.labelMedium, color = DeckBAmber)
                                }
                            }
                        }
                        items(lib.playlists) { pl ->
                            val sel = lib.selectedPlaylistId == pl.id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (sel) DeckBAmber.copy(alpha = 0.2f) else ConsoleCharcoal)
                                    .border(1.dp, if (sel) DeckBAmber else PanelBorder, RoundedCornerShape(10.dp))
                                    .clickable { viewModel.selectPlaylist(pl.id) }
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = pl.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (sel) DeckBAmber else TextPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Right Pane: Sort Bar + Track List
            Column(
                modifier = Modifier
                    .weight(0.62f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${filteredTracks.size} Tracks • Hold & drag to drop on Deck A/B",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(DeckSurface)
                            .border(1.dp, PanelBorder, RoundedCornerShape(10.dp))
                            .clickable {
                                val entries = LibrarySortMode.entries
                                val next = entries[(entries.indexOf(lib.sortMode) + 1) % entries.size]
                                viewModel.setSortMode(next)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Sort: ${lib.sortMode.label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = DeckBAmber
                        )
                    }
                }

                if (filteredTracks.isEmpty()) {
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
                                text = if (lib.filterTab == LibraryFilterTab.PLAYLISTS && lib.selectedPlaylistId == null)
                                    "Create or select a playlist on the left"
                                else
                                    "No matching tracks found. Tap Import to add MP3, WAV, FLAC, or AAC files.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(filteredTracks, key = { it.id }) { track ->
                            val isHarmonic = masterCamelot != null && AudioAnalyzer.areKeysHarmonic(masterCamelot, track.camelotKey)
                            var itemOrigin by remember { mutableStateOf(Offset.Zero) }

                            TrackRowCard(
                                track = track,
                                isHarmonicMatch = isHarmonic,
                                onLoadDeckA = {
                                    viewModel.loadTrackToDeck(0, track)
                                    onTrackLoadedToDeck()
                                },
                                onLoadDeckB = {
                                    viewModel.loadTrackToDeck(1, track)
                                    onTrackLoadedToDeck()
                                },
                                onAddToPlaylist = { playlistTargetTrack = track },
                                modifier = Modifier
                                    .onGloballyPositioned { coords ->
                                        itemOrigin = coords.boundsInRoot().topLeft
                                    }
                                    .pointerInput(track.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { offset ->
                                                draggingTrack = track
                                                dragPointerRoot = itemOrigin + offset
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragPointerRoot += dragAmount
                                            },
                                            onDragEnd = {
                                                val dropped = draggingTrack
                                                if (dropped != null) {
                                                    when {
                                                        deckABounds?.contains(dragPointerRoot) == true -> {
                                                            viewModel.loadTrackToDeck(0, dropped)
                                                            onTrackLoadedToDeck()
                                                        }
                                                        deckBBounds?.contains(dragPointerRoot) == true -> {
                                                            viewModel.loadTrackToDeck(1, dropped)
                                                            onTrackLoadedToDeck()
                                                        }
                                                    }
                                                }
                                                draggingTrack = null
                                            },
                                            onDragCancel = {
                                                draggingTrack = null
                                            }
                                        )
                                    }
                            )
                        }
                    }
                }
            }
        }

        // Floating Drag Preview Ghost
        draggingTrack?.let { tr ->
            Box(
                modifier = Modifier
                    .offset { IntOffset(dragPointerRoot.x.roundToInt() - 100, dragPointerRoot.y.roundToInt() - 80) }
                    .clip(RoundedCornerShape(10.dp))
                    .background(DeckSurface.copy(alpha = 0.92f))
                    .border(2.dp, DeckACyan, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Drop on Deck A or B: ${tr.title}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary
                )
            }
        }
    }

    if (showCreatePlaylistDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            containerColor = ConsoleCharcoal,
            title = { Text("Create Playlist", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Peak Hour Techno") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createPlaylist(name)
                        showCreatePlaylistDialog = false
                    }
                ) { Text("Create", color = DeckACyan) }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    playlistTargetTrack?.let { tr ->
        AlertDialog(
            onDismissRequest = { playlistTargetTrack = null },
            containerColor = ConsoleCharcoal,
            title = { Text("Add '${tr.title}' to Playlist", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (lib.playlists.isEmpty()) {
                        Text("No playlists yet. Create one below.", color = TextSecondary)
                    } else {
                        lib.playlists.forEach { pl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DeckSurface)
                                    .clickable {
                                        viewModel.addTrackToPlaylist(pl.id, tr.id)
                                        playlistTargetTrack = null
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = DeckBAmber)
                                Spacer(Modifier.width(8.dp))
                                Text(pl.name, color = TextPrimary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    playlistTargetTrack = null
                    showCreatePlaylistDialog = true
                }) { Text("New Playlist", color = DeckACyan) }
            },
            dismissButton = {
                TextButton(onClick = { playlistTargetTrack = null }) {
                    Text("Close", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun DeckDropZone(
    deckLabel: String,
    loadedTitle: String,
    accentColor: Color,
    isHighlighted: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isHighlighted) accentColor.copy(alpha = 0.22f) else TrackSlotBg)
            .border(1.5.dp, if (isHighlighted) accentColor else PanelBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = deckLabel,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = loadedTitle,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TrackRowCard(
    track: TrackEntity,
    isHarmonicMatch: Boolean,
    onLoadDeckA: () -> Unit,
    onLoadDeckB: () -> Unit,
    onAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ConsoleCharcoal)
            .border(1.dp, if (isHarmonicMatch) HarmonicMatchGreen.copy(alpha = 0.5f) else PanelBorder, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.DragIndicator,
            contentDescription = "Drag track",
            tint = TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))

        // Album Art
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DeckSurface)
        ) {
            if (track.albumArtPath != null && File(track.albumArtPath).exists()) {
                AsyncImage(
                    model = File(track.albumArtPath),
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.img_vinyl_cover_1794566130200),
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        // Metadata
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${track.artist} • ${formatMs(track.durationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = String.format(Locale.US, "%.1f BPM", track.bpm),
                    style = MaterialTheme.typography.labelSmall,
                    color = DeckACyan
                )
                Text(
                    text = "${track.musicalKey} (${track.camelotKey})",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isHarmonicMatch) HarmonicMatchGreen else DeckBAmber
                )
            }
        }

        // Load Deck A / B + Playlist buttons
        IconButton(onClick = onAddToPlaylist) {
            Icon(Icons.Default.Add, contentDescription = "Add to playlist", tint = TextSecondary)
        }

        Box(
            modifier = Modifier
                .testTag("load_deck_a_${track.id}")
                .clip(RoundedCornerShape(8.dp))
                .background(DeckACyan.copy(alpha = 0.2f))
                .border(1.dp, DeckACyan, RoundedCornerShape(8.dp))
                .clickable(onClick = onLoadDeckA)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text("A", style = MaterialTheme.typography.labelLarge, color = DeckACyan)
        }

        Spacer(Modifier.width(6.dp))

        Box(
            modifier = Modifier
                .testTag("load_deck_b_${track.id}")
                .clip(RoundedCornerShape(8.dp))
                .background(DeckBAmber.copy(alpha = 0.2f))
                .border(1.dp, DeckBAmber, RoundedCornerShape(8.dp))
                .clickable(onClick = onLoadDeckB)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text("B", style = MaterialTheme.typography.labelLarge, color = DeckBAmber)
        }
    }
}
