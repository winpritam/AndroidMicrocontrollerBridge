package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.PicocaseApplication
import com.example.data.database.EventMapping
import com.example.data.database.PicoFile
import com.example.data.database.SerialLogEntry
import com.example.serial.*
import com.example.serial.driver.UsbSerialProber
import com.example.service.BackgroundEventService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PicocaseApplication
    private val serialManager = app.serialManager
    private val settingsRepository = app.settingsRepository
    private val eventMappingRepository = app.eventMappingRepository
    private val dao = app.database.picocaseDao()
    private val gitHubScriptManager = app.gitHubScriptManager

    private val transferManager = FileTransferManager(serialManager)

    // --- State Stream Binds ---
    val connectionState: StateFlow<ConnectionState> = serialManager.connectionState
    val connectedDeviceInfo: StateFlow<String> = serialManager.connectedDeviceInfo
    val availableDevices: StateFlow<List<UsbSerialProber.DeviceInfo>> = serialManager.availableDevices
    val isSimulationMode: StateFlow<Boolean> = serialManager.isSimulationMode
    val baudRate: StateFlow<String> = settingsRepository.getSettingFlow("baud_rate", "115200")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "115200")
    val filesList: StateFlow<List<PicoFile>> = serialManager.filesList
    val transferState: StateFlow<TransferState> = transferManager.transferState
    val logsList: StateFlow<List<SerialLogEntry>> = dao.getAllLogsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val eventMappings: StateFlow<List<EventMapping>> = eventMappingRepository.allEventMappings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- GitHub Script Stream Binds ---
    val githubSyncStatus: StateFlow<String> = gitHubScriptManager.syncStatus
    val isGithubSyncing: StateFlow<Boolean> = gitHubScriptManager.isSyncing
    val githubCachedScript: StateFlow<String> = gitHubScriptManager.cachedScript

    // --- Hardware & OLED Pin Configurations (ESP32 DevKit Defaults: SDA 21, SCL 22) ---
    val sdaPin: StateFlow<String> = settingsRepository.getSettingFlow("oled_sda_pin", "21")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "21")

    val sclPin: StateFlow<String> = settingsRepository.getSettingFlow("oled_scl_pin", "22")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "22")

    val boardModel: StateFlow<String> = settingsRepository.getSettingFlow("board_model", "ESP32 DevKit")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ESP32 DevKit")

    val frameDelay: StateFlow<String> = settingsRepository.getSettingFlow("frame_delay", "120")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "120")

    // --- Dashboard Specific Switch Flows ---
    val notificationEnabled = settingsRepository.getBooleanSettingFlow("notification_enabled", true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val miniMessageEnabled = settingsRepository.getBooleanSettingFlow("mini_message_enabled", false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val wakeOnShakeEnabled = settingsRepository.getBooleanSettingFlow("wake_on_shake_enabled", false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val sleepModeEnabled = settingsRepository.getBooleanSettingFlow("sleep_mode_enabled", false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val randomAnimationEnabled = settingsRepository.getBooleanSettingFlow("random_animation_enabled", false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val alwaysOnEnabled = settingsRepository.getBooleanSettingFlow("always_on_enabled", false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Settings panel switch flows
    val autoConnect = settingsRepository.getBooleanSettingFlow("auto_connect", true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val runInBackground = settingsRepository.getBooleanSettingFlow("run_in_background", true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val startServiceOnBoot = settingsRepository.getBooleanSettingFlow("start_service_on_boot", true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val enableLogging = settingsRepository.getBooleanSettingFlow("enable_logging", true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val reconnectAutomatically = settingsRepository.getBooleanSettingFlow("reconnect_automatically", true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    init {
        viewModelScope.launch {
            val bgRun = settingsRepository.getBooleanSetting("run_in_background", true)
            if (bgRun && !BackgroundEventService.isServiceRunning) {
                startForegroundService()
            }
        }
    }

    // --- Dashboard Card Toggle Handlers ---
    fun toggleNotification(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.saveBooleanSetting("notification_enabled", enabled)
        if (enabled) {
            serialManager.sendTerminalCommand(PicoProtocol.CMD_ALERT_MSG)
        } else {
            serialManager.sendTerminalCommand(PicoProtocol.CMD_STOP)
        }
    }

    fun toggleMiniMessage(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.saveBooleanSetting("mini_message_enabled", enabled)
        serialManager.sendTerminalCommand(if (enabled) "CONFIG_MINI_MSG_ON" else "CONFIG_MINI_MSG_OFF")
    }

    fun toggleWakeOnShake(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.saveBooleanSetting("wake_on_shake_enabled", enabled)
        serialManager.sendTerminalCommand(if (enabled) "CONFIG_SHAKE_ON" else "CONFIG_SHAKE_OFF")
    }

    fun toggleSleepMode(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.saveBooleanSetting("sleep_mode_enabled", enabled)
        serialManager.sendTerminalCommand(if (enabled) PicoProtocol.CMD_EMO_SLEEPY else PicoProtocol.CMD_START)
    }

    fun toggleRandomAnimation(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.saveBooleanSetting("random_animation_enabled", enabled)
        serialManager.sendTerminalCommand(if (enabled) "CONFIG_RANDOM_ON" else "CONFIG_RANDOM_OFF")
    }

    fun toggleAlwaysOn(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.saveBooleanSetting("always_on_enabled", enabled)
        serialManager.sendTerminalCommand(if (enabled) "CONFIG_ALWAYS_ON" else "CONFIG_ALWAYS_OFF")
    }

    // --- General Settings Configurations Changes ---
    fun toggleAutoConnect(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.saveBooleanSetting("auto_connect", enabled)
    }

    fun toggleRunInBackground(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.saveBooleanSetting("run_in_background", enabled)
        if (enabled) {
            startForegroundService()
            serialManager.logSystem("Background service running.")
        } else {
            stopForegroundService()
            serialManager.logSystem("Background service paused.")
        }
    }

    fun toggleStartServiceOnBoot(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.saveBooleanSetting("start_service_on_boot", enabled)
    }

    fun toggleEnableLogging(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.saveBooleanSetting("enable_logging", enabled)
    }

    fun toggleReconnectAutomatically(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.saveBooleanSetting("reconnect_automatically", enabled)
    }

    fun toggleSimulationMode(enabled: Boolean) {
        serialManager.setSimulationMode(enabled)
    }

    fun changeBaudRate(rate: String) = viewModelScope.launch {
        settingsRepository.saveSetting("baud_rate", rate)
        serialManager.updateBaudRate(rate.toIntOrNull() ?: 115200)
    }

    fun saveHardwareConfig(sda: String, scl: String, board: String, delay: String) = viewModelScope.launch {
        settingsRepository.saveSetting("oled_sda_pin", sda)
        settingsRepository.saveSetting("oled_scl_pin", scl)
        settingsRepository.saveSetting("board_model", board)
        settingsRepository.saveSetting("frame_delay", delay)
        serialManager.logSystem("Updated hardware configuration: $board (SDA=$sda, SCL=$scl, delay=${delay}ms)")
    }

    // --- Terminal & Manual Command Controls (Direct, Real-time) ---
    fun sendManualCommand(cmd: String) {
        if (cmd.isBlank()) return
        serialManager.sendTerminalCommand(cmd.trim())
    }

    fun sendCtrlC() {
        serialManager.sendCtrlC()
    }

    fun sendCtrlD() {
        serialManager.sendCtrlD()
    }

    fun clearLogHistory() {
        serialManager.cleanLocalLogs()
    }

    // --- GitHub Script Synchronization ---
    fun syncFromGitHub() = viewModelScope.launch {
        gitHubScriptManager.fetchFromGitHub()
    }

    fun deployPickedScript(fileName: String, bytes: ByteArray) = viewModelScope.launch {
        val targetName = if (fileName.endsWith(".py", ignoreCase = true)) "main.py" else fileName
        serialManager.logSystem("Uploading picked file '$fileName' as '$targetName' to device...")
        transferManager.uploadAnimationFile(targetName, bytes)
    }

    fun deployGitHubMainPy() = viewModelScope.launch {
        var script = githubCachedScript.value
        if (script.isBlank()) {
            gitHubScriptManager.fetchFromGitHub()
            script = githubCachedScript.value
        }
        if (script.isNotBlank()) {
            serialManager.logSystem("Deploying GitHub main.py (winpritam/pico-case) to device...")
            transferManager.uploadAnimationFile("main.py", script.toByteArray(StandardCharsets.UTF_8))
        } else {
            deployGeneratedMainPy()
        }
    }

    fun deployGeneratedMainPy() = viewModelScope.launch {
        val sda = sdaPin.value.toIntOrNull() ?: 21
        val scl = sclPin.value.toIntOrNull() ?: 22
        val delayMs = frameDelay.value.toIntOrNull() ?: 120
        val code = MicroPythonHelper.generateDeviceCode(
            sdaPin = sda,
            sclPin = scl,
            frameDelayMs = delayMs
        )
        serialManager.logSystem("Deploying generated main.py (SDA=$sda, SCL=$scl)...")
        transferManager.uploadAnimationFile("main.py", code.toByteArray(StandardCharsets.UTF_8))
    }

    // --- Animation Library Management ---
    fun uploadAnimation(fileName: String, bytes: ByteArray) = viewModelScope.launch {
        transferManager.uploadAnimationFile(fileName, bytes)
    }

    fun playAnimation(fileName: String) = viewModelScope.launch {
        val cleanName = if (fileName.endsWith(".bin", ignoreCase = true) && !fileName.startsWith("anims/")) {
            "anims/$fileName"
        } else {
            fileName
        }
        serialManager.sendTerminalCommand("PLAY $cleanName")
    }

    fun deleteAnimation(fileName: String) = viewModelScope.launch {
        val targetPath = if (fileName.endsWith(".bin", ignoreCase = true) && !fileName.startsWith("anims/")) {
            "anims/$fileName"
        } else {
            fileName
        }
        val result = serialManager.sendCommand("${PicoProtocol.CMD_DELETE_PREFIX}$targetPath")
        if (result.contains(PicoProtocol.RESP_READY, ignoreCase = true) || result.contains("OK", ignoreCase = true) || result.contains("END", ignoreCase = true)) {
            serialManager.logSystem("Deleted file $targetPath from device storage.")
            serialManager.sendCommand(PicoProtocol.CMD_LIST_FILES)
        }
    }

    fun renameAnimation(oldName: String, newName: String) = viewModelScope.launch {
        val extension = oldName.substringAfterLast(".", "bin")
        val cleanNew = if (newName.contains(".")) newName else "$newName.$extension"
        val oldPath = if (extension == "bin" && !oldName.startsWith("anims/")) "anims/$oldName" else oldName
        val newPath = if (extension == "bin" && !cleanNew.startsWith("anims/")) "anims/$cleanNew" else cleanNew

        val result = serialManager.sendCommand("${PicoProtocol.CMD_RENAME_PREFIX}$oldPath $newPath")
        if (result.contains(PicoProtocol.RESP_READY, ignoreCase = true) || result.contains("OK", ignoreCase = true) || result.contains("END", ignoreCase = true)) {
            serialManager.logSystem("Renamed $oldPath to $newPath successfully.")
            serialManager.sendCommand(PicoProtocol.CMD_LIST_FILES)
        }
    }

    fun refreshFileList() = viewModelScope.launch {
        serialManager.sendCommand(PicoProtocol.CMD_LIST_FILES)
    }

    // --- Event Mappings Configurations Updates ---
    fun updateEventMapping(eventKey: String, animationFile: String) = viewModelScope.launch {
        eventMappingRepository.saveMapping(eventKey, animationFile)
        serialManager.logSystem("Updated map: $eventKey -> $animationFile")
    }

    // --- Foregrounds Service Start / Stop ---
    private fun startForegroundService() {
        try {
            val intent = Intent(app, BackgroundEventService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                app.startForegroundService(intent)
            } else {
                app.startService(intent)
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "FGS start error: ${e.message}")
        }
    }

    private fun stopForegroundService() {
        val intent = Intent(app, BackgroundEventService::class.java)
        app.stopService(intent)
    }

    // --- Real-time Simulator Bridge Triggers ---
    fun triggerSimulatedEvent(eventKey: String, details: String = "") = viewModelScope.launch {
        val callerName = if (eventKey == PicoProtocol.EVENT_INCOMING_CALL) details.ifBlank { "Incoming Call" } else null
        val smsSender = if (eventKey == PicoProtocol.EVENT_INCOMING_SMS) details.ifBlank { "Incoming Message" } else null

        val intent = Intent(app, BackgroundEventService::class.java).apply {
            action = BackgroundEventService.ACTION_SIMULATE_EVENT
            putExtra(BackgroundEventService.EXTRA_EVENT_NAME, eventKey)
            callerName?.let { putExtra(BackgroundEventService.EXTRA_CALLER_NAME, it) }
            smsSender?.let { putExtra(BackgroundEventService.EXTRA_SMS_SENDER, it) }
        }
        app.startService(intent)
    }

    fun resetDeviceConfig() = viewModelScope.launch {
        serialManager.sendCommand("RESET_DEVICE")
        serialManager.logSystem("Sent master Reset Config order to microcontroller.")
    }

    fun deleteAllAnimationsOnDevice() = viewModelScope.launch {
        serialManager.sendCommand("WIPE_STORAGE")
        serialManager.logSystem("Sent master storage format command to microcontroller.")
    }

    fun scanUsbHardware() {
        serialManager.scanAndConnect()
    }

    fun selectUsbDevice(device: UsbDevice) {
        serialManager.connectToSpecificDevice(device)
    }

    fun clearUploadState() {
        transferManager.resetState()
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
