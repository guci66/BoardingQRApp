package com.example.boardingqr

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [BoardingRecord::class], version = 1, exportSchema = false)
abstract class BoardingDatabase : RoomDatabase() {
    abstract fun recordDao(): BoardingRecordDao
}