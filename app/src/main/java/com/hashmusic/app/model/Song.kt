package com.hashmusic.app.model

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val albumArt: String,
    val duration: Long,
    val videoId: String,
    val album: String = "",
    val isLiked: Boolean = false,
    val isDownloaded: Boolean = false,
    val localPath: String? = null
)