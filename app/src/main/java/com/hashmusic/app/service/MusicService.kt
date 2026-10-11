package com.hashmusic.app.service

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.hashmusic.app.MainActivity

@OptIn(UnstableApi::class)
class MusicService : MediaSessionService() {
    
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    
    private val sessionCallback = object : MediaSession.Callback {
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val p = player
            val currentItem = p?.currentMediaItem
            return if (p != null && currentItem != null) {
                Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(
                        listOf(currentItem),
                        p.currentMediaItemIndex,
                        p.currentPosition
                    )
                )
            } else {
                Futures.immediateFailedFuture(
                    UnsupportedOperationException("No playback items available")
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        player = ExoPlayer.Builder(this).build()
        
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        mediaSession = MediaSession.Builder(this, player!!)
            .setCallback(sessionCallback)
            .setSessionActivity(sessionActivity)
            .build()
    }
    
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }
    
    override fun onDestroy() {
        mediaSession?.run {
            player?.release()
            release()
        }
        player = null
        mediaSession = null
        super.onDestroy()
    }
}