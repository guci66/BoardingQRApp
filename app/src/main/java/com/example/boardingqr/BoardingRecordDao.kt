package com.example.boardingqr

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BoardingRecordDao {
    @Insert
    suspend fun insert(record: BoardingRecord): Long

    @Delete
    suspend fun delete(record: BoardingRecord)

    @Query("SELECT * FROM BoardingRecord ORDER BY id DESC")
    fun all(): Flow<List<BoardingRecord>>

    @Query("DELETE FROM BoardingRecord")
    suspend fun clear()
}