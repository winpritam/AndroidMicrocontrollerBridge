package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingEntry(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "event_mappings")
data class EventMapping(
    @PrimaryKey val eventKey: String, // e.g. "incoming_call", "incoming_sms", "charging_started", "music_playing", etc.
    val animationFile: String          // e.g. "alert_call.bin", "alert_msg.bin", etc.
)

@Entity(tableName = "pico_files")
data class PicoFile(
    @PrimaryKey val fileName: String,
    val sizeBytes: Long,
    val fileType: String // "alert" or "emo"
)

@Entity(tableName = "serial_logs")
data class SerialLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val message: String,
    val direction: String // "TX" (Transmitted) or "RX" (Received) or "SYS" (System Info/Log)
)
