package com.hashmusic.app.data.local
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey 
    val id: String,
    val name: String,
    val thumbnailUrl: String = "",
    val dateCreated: Long = System.currentTimeMillis()
)