package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uriString: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val bpm: Float,
    val firstBeatMs: Long,
    val musicalKey: String,
    val camelotKey: String,
    val waveformSerialized: String, // packed low,mid,high,peak floats per bucket
    val albumArtPath: String? = null,
    val dateAdded: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long = 0L,
    val playCount: Int = 0,
    val isBuiltIn: Boolean = false
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("trackId")]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: Long,
    val position: Int = 0
)

@Entity(
    tableName = "cue_points",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("trackId")]
)
data class CuePointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val slotIndex: Int, // 0..3 for Hot Cues, -1 for main Cue
    val positionMs: Long,
    val label: String,
    val colorHex: Long
)

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val filePath: String,
    val format: String, // "WAV" or "M4A"
    val durationMs: Long,
    val fileSizeBytes: Long,
    val bpmSummary: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val crossfaderCurve: Int = 1, // 0=Smooth, 1=ConstantPower, 2=SharpCut
    val autoCrossfadeSeconds: Float = 4.0f,
    val pitchRangePercent: Int = 16, // 8, 16, 32
    val keyLockDefault: Boolean = true,
    val quantizeDefault: Boolean = true,
    val vinylRpm: Int = 33, // 33 or 45
    val lowLatencyAudio: Boolean = true,
    val completedTutorialIds: String = ""
)

@Entity(tableName = "sampler_pads")
data class SamplerPadEntity(
    @PrimaryKey val padIndex: Int, // 0..7
    val label: String,
    val customUri: String? = null,
    val volume: Float = 0.9f,
    val pitch: Float = 1.0f,
    val colorHex: Long
)
