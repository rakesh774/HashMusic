package com.hashmusic.app.data.local
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey 
    val id: String,
    val title: String,
    val artist: String,
    val albumArt: String,
    val duration: Long,
    val videoId: String,
    val album: String = "",
    val isLiked: Boolean = false,
    val isDownloaded: Boolean = false,
    val localPath: String? = null,
    val dateAdded: Long = System.currentTimeMillis()
)