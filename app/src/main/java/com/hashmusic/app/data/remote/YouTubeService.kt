package com.hashmusic.app.data.remote

import android.content.Context
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.PlaylistItemListResponse
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.hashmusic.app.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class YouTubeService(private val context: Context) {

    private var youtube: YouTube? = null

    fun setAuthToken(account: GoogleSignInAccount) {
        val credential = com.google.api.client.googleapis.auth.oauth2.GoogleCredential.Builder()
            .setTransport(NetHttpTransport())
            .setJsonFactory(GsonFactory.getDefaultInstance())
            .setClientSecrets("", "") // Not needed for Android client ID
            .build()
            .setAccessToken(account.idToken)
            .setRefreshToken(account.serverAuthCode)

        youtube = YouTube.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            HttpRequestInitializer { request -> credential.initialize(request) }
        ).setApplicationName("HashMusic").build()
    }

    suspend fun getLikedVideos(maxResults: Long = 25): List<Song> {
        return withContext(Dispatchers.IO) {
            try {
                val service = youtube ?: return@withContext emptyList()
                
                // The "LL" playlist is the special ID for the user's Liked Videos
                val response: PlaylistItemListResponse = service.playlistItems()
                    .list(listOf("snippet", "contentDetails"))
                    .setPlaylistId("LL")
                    .setMaxResults(maxResults)
                    .execute()

                val songs = mutableListOf<Song>()
                for (item in response.items) {
                    val snippet = item.snippet
                    songs.add(
                        Song(
                            id = item.contentDetails.videoId,
                            title = snippet.title,
                            artist = snippet.videoOwnerChannelTitle ?: "Unknown",
                            albumArt = snippet.thumbnails.high.url,
                            duration = 0L, // Duration is not provided in this call
                            videoId = item.contentDetails.videoId,
                            album = ""
                        )
                    )
                }
                songs
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
}
