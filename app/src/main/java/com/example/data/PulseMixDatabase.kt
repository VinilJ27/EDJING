package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        CuePointEntity::class,
        RecordingEntity::class,
        UserSettingsEntity::class,
        SamplerPadEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PulseMixDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun cuePointDao(): CuePointDao
    abstract fun recordingDao(): RecordingDao
    abstract fun settingsDao(): SettingsDao
    abstract fun samplerPadDao(): SamplerPadDao

    companion object {
        @Volatile
        private var INSTANCE: PulseMixDatabase? = null

        fun getInstance(context: Context): PulseMixDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PulseMixDatabase::class.java,
                    "pulsemix.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
