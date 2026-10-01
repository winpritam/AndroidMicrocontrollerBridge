package com.example

import android.app.Application
import com.example.data.database.PicocaseDatabase
import com.example.data.SettingsRepository
import com.example.data.EventMappingRepository
import com.example.serial.SerialManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PicocaseApplication : Application() {

    lateinit var database: PicocaseDatabase
    lateinit var settingsRepository: SettingsRepository
    lateinit var eventMappingRepository: EventMappingRepository
    lateinit var serialManager: SerialManager
    lateinit var gitHubScriptManager: com.example.data.GitHubScriptManager

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = PicocaseDatabase.getDatabase(this)
        settingsRepository = SettingsRepository(database.picocaseDao())
        eventMappingRepository = EventMappingRepository(database.picocaseDao())
        serialManager = SerialManager(this, database.picocaseDao())
        gitHubScriptManager = com.example.data.GitHubScriptManager(this, settingsRepository)

        // Feed initial mapping configurations and sync github script in background
        CoroutineScope(Dispatchers.IO).launch {
            eventMappingRepository.initializeDefaultMappings()
            gitHubScriptManager.init()
        }
    }

    companion object {
        lateinit var instance: PicocaseApplication
            private set
    }
}
