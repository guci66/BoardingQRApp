package com.example.boardingqr

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class BoardingRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val permitNo: String,
    val name: String,
    val zones: String, // comma separated for display
    val status: String,
    val validToIso: String,
    val scannedAtIso: String,
    val result: String, // ACCEPT / REJECT
    val reason: String? = null
)