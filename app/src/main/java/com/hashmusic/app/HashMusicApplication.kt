package com.hashmusic.app

import android.app.Application
import androidx.room.Room
import com.hashmusic.app.data.local.AppDatabase
import com.hashmusic.app.data.remote.HashMusicApiService
import com.hashmusic.app.data.repository.MusicRepository

class HashMusicApplication : Application() {
    lateinit var repository: MusicRepository
        private set
    
    lateinit var database: AppDatabase
        private set
    
    override fun onCreate() {
        super.onCreate()
        val api = HashMusicApiService()
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "hashmusic_db"
        ).build()
        repository = MusicRepository(api, database)
    }
}