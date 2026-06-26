package com.hashmusic.app

import android.app.Application
import androidx.room.Room
import com.hashmusic.app.data.local.AppDatabase
import com.hashmusic.app.data.remote.YouTubeApiService
import com.hashmusic.app.data.repository.MusicRepository

class HashMusicApplication : Application() {
    lateinit var repository: MusicRepository
        private set
    
    lateinit var database: AppDatabase
        private set
    
    override fun onCreate() {
        super.onCreate()
        val api = YouTubeApiService()
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "hashmusic_db"
        ).build()
        repository = MusicRepository(api, database)
    }
}