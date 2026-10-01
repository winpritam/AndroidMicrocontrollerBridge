package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PicocaseDao {

    // --- Settings ---
    @Query("SELECT * FROM settings WHERE `key` = :key LIMIT 1")
    suspend fun getSettingByKey(key: String): SettingEntry?

    @Query("SELECT * FROM settings")
    fun getAllSettingsFlow(): Flow<List<SettingEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: SettingEntry)

    // --- Event Mappings ---
    @Query("SELECT * FROM event_mappings")
    fun getAllEventMappingsFlow(): Flow<List<EventMapping>>

    @Query("SELECT * FROM event_mappings WHERE eventKey = :eventKey LIMIT 1")
    suspend fun getEventMapping(eventKey: String): EventMapping?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEventMapping(mapping: EventMapping)

    @Query("DELETE FROM event_mappings WHERE eventKey = :eventKey")
    suspend fun deleteEventMapping(eventKey: String)

    // --- Pico files ---
    @Query("SELECT * FROM pico_files ORDER BY fileName ASC")
    fun getAllPicoFilesFlow(): Flow<List<PicoFile>>

    @Query("SELECT * FROM pico_files ORDER BY fileName ASC")
    suspend fun getAllPicoFiles(): List<PicoFile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPicoFiles(files: List<PicoFile>)

    @Query("DELETE FROM pico_files WHERE fileName = :fileName")
    suspend fun deletePicoFile(fileName: String)

    @Query("DELETE FROM pico_files")
    suspend fun clearPicoFiles()

    // --- Serial logs ---
    @Query("SELECT * FROM serial_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getAllLogsFlow(limit: Int = 200): Flow<List<SerialLogEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SerialLogEntry)

    @Query("DELETE FROM serial_logs")
    suspend fun clearLogs()

    @Query("DELETE FROM serial_logs WHERE id IN (SELECT id FROM serial_logs ORDER BY timestamp ASC LIMIT :count)")
    suspend fun deleteOldestLogs(count: Int)
}
