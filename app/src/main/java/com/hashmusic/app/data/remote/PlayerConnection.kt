package com.hashmusic.app.data.remote

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.hashmusic.app.service.MusicService

@OptIn(UnstableApi::class)
object PlayerConnection {
    
    private var controllerFuture: ListenableFuture<MediaController>? = null
    var controller: MediaController? = null
        private set
    
    var isConnected: Boolean = false
        private set
    
    fun connect(context: Context, onConnected: () -> Unit = {}) {
        if (controller != null) {
            onConnected()
            return
        }
        
        val token = SessionToken(
            context,
            ComponentName(context, MusicService::class.java)
        )
        
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        
        future.addListener({
            try {
                controller = future.get()
                isConnected = true
                onConnected()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }
    
    fun playSong(url: String, title: String, artist: String, albumArt: String) {
        android.util.Log.d("HashMusic", "PlayerConnection.playSong called")
        val c = controller
        if (c == null) {
            android.util.Log.e("HashMusic", "Controller is NULL - cannot play!")
            return
        }
        android.util.Log.d("HashMusic", "Controller exists, setting media item")
        
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMediaId(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(android.net.Uri.parse(albumArt))
                    .build()
            )
            .build()
        
        c.setMediaItem(mediaItem)
        c.prepare()
        c.play()
        android.util.Log.d("HashMusic", "Media item set, playing. isPlaying=${c.isPlaying}")
    }
    
    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            c.play()
        }
    }
    
    fun isPlaying(): Boolean = controller?.isPlaying ?: false
    
    fun release() {
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        controller = null
        isConnected = false
    }
}