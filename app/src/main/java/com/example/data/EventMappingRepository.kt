package com.example.data

import com.example.data.database.EventMapping
import com.example.data.database.PicocaseDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class EventMappingRepository(private val dao: PicocaseDao) {

    val allEventMappings: Flow<List<EventMapping>> = dao.getAllEventMappingsFlow()

    suspend fun getMappingForEvent(eventKey: String): String? = withContext(Dispatchers.IO) {
        dao.getEventMapping(eventKey)?.animationFile
    }

    suspend fun saveMapping(eventKey: String, animationFile: String) = withContext(Dispatchers.IO) {
        dao.insertEventMapping(EventMapping(eventKey, animationFile))
    }

    suspend fun deleteMapping(eventKey: String) = withContext(Dispatchers.IO) {
        dao.deleteEventMapping(eventKey)
    }

    suspend fun initializeDefaultMappings() = withContext(Dispatchers.IO) {
        val defaults = listOf(
            EventMapping("incoming_call", "alert_call.bin"),
            EventMapping("incoming_sms", "alert_msg.bin"),
            EventMapping("charging_started", "alert_charge.bin"),
            EventMapping("music_playing", "emo_happy.bin"),
            EventMapping("battery_low", "alert_battery.bin"),
            EventMapping("missed_call", "alert_missed.bin")
        )
        for (item in defaults) {
            // Only insert if not already present
            if (dao.getEventMapping(item.eventKey) == null) {
                dao.insertEventMapping(item)
            }
        }
    }
}
