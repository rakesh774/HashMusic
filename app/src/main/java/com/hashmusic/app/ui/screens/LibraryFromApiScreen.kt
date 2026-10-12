package com.hashmusic.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hashmusic.app.model.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryFromApiScreen(songs: List<Song>) {
    val context = LocalContext.current
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your Liked Songs") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (songs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No liked songs found. Sign in or refresh.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(songs) { song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://music.youtube.com/watch?v=${song.videoId}")
                                ).apply {
                                    setPackage("com.google.android.apps.youtube.music")
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val fallback = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://music.youtube.com/watch?v=${song.videoId}")
                                    ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                                    context.startActivity(fallback)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = song.albumArt,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                song.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                song.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
