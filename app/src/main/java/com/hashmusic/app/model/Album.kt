package com.hashmusic.app.model

data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val year: String = ""
)