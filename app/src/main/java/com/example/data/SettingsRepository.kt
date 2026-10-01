package com.example.data

import com.example.data.database.PicocaseDao
import com.example.data.database.SettingEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SettingsRepository(private val dao: PicocaseDao) {

    val allSettings: Flow<List<SettingEntry>> = dao.getAllSettingsFlow()

    fun getSettingFlow(key: String, defaultValue: String): Flow<String> {
        return allSettings.map { list ->
            list.find { it.key == key }?.value ?: defaultValue
        }
    }

    fun getBooleanSettingFlow(key: String, defaultValue: Boolean): Flow<Boolean> {
        return getSettingFlow(key, defaultValue.toString()).map { it.toBoolean() }
    }

    suspend fun getSetting(key: String, defaultValue: String): String = withContext(Dispatchers.IO) {
        dao.getSettingByKey(key)?.value ?: defaultValue
    }

    suspend fun getBooleanSetting(key: String, defaultValue: Boolean): Boolean = withContext(Dispatchers.IO) {
        val strValue = dao.getSettingByKey(key)?.value
        strValue?.toBoolean() ?: defaultValue
    }

    suspend fun saveSetting(key: String, value: String) = withContext(Dispatchers.IO) {
        dao.insertSetting(SettingEntry(key, value))
    }

    suspend fun saveBooleanSetting(key: String, value: Boolean) = withContext(Dispatchers.IO) {
        saveSetting(key, value.toString())
    }
}
