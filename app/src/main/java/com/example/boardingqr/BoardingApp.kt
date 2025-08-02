package com.example.boardingqr

import android.app.Application
import androidx.room.Room

class BoardingApp : Application() {
    lateinit var db: BoardingDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(
            applicationContext,
            BoardingDatabase::class.java,
            "boarding.db"
        ).fallbackToDestructiveMigration().build()
    }
}