package com.example.serial

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.example.data.database.PicoFile
import com.example.data.database.PicocaseDao
import com.example.data.database.SerialLogEntry
import com.example.serial.driver.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

enum class ConnectionState {
    OFFLINE,
    CONNECTING,
    CONNECTED,
    BUSY,
    ERROR
}

class SerialManager(
    private val context: Context,
    private val dao: PicocaseDao
) {
    private val tag = "SerialManager"
    private val usbPermissionAction = "com.aistudio.picocase.USB_PERMISSION"

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var usbDevice: UsbDevice? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var activePort: UsbSerialPort? = null

    private val _connectionState = MutableStateFlow(ConnectionState.OFFLINE)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectedDeviceInfo = MutableStateFlow("No device connected")
    val connectedDeviceInfo: StateFlow<String> = _connectedDeviceInfo.asStateFlow()

    private val _availableDevices = MutableStateFlow<List<UsbSerialProber.DeviceInfo>>(emptyList())
    val availableDevices: StateFlow<List<UsbSerialProber.DeviceInfo>> = _availableDevices.asStateFlow()

    private val _isSimulationMode = MutableStateFlow(false)
    val isSimulationMode: StateFlow<Boolean> = _isSimulationMode.asStateFlow()

    // Shared flow with replay=1 so fast responses aren't lost by immediate collectors
    private val incomingLinesFlow = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 128)

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var readJob: Job? = null
    private val commandMutex = Mutex()

    // Real-time cached files list
    private val _filesList = MutableStateFlow<List<PicoFile>>(emptyList())
    val filesList: StateFlow<List<PicoFile>> = _filesList.asStateFlow()

    private var currentBaudRate = 115200

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                usbPermissionAction -> {
                    synchronized(this) {
                        val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            device?.let { connectToDevice(it) }
                        } else {
                            logSystem("USB Permission Denied for device: ${device?.deviceName}")
                            _connectionState.value = ConnectionState.ERROR
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    logSystem("USB Device Attached. Scanning for MicroPython / ESP32 hardware...")
                    scanAndConnect()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (device != null && device == usbDevice) {
                        logSystem("Device ${device.deviceName} unplugged.")
                        disconnect()
                    }
                    refreshAvailableDevices()
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(usbPermissionAction)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }

        // Stream storage cache from Room
        scope.launch {
            dao.getAllPicoFilesFlow().collect {
                _filesList.value = it
            }
        }

        // Load configured baud rate from database (default 115200)
        scope.launch {
            val savedBaud = dao.getSettingByKey("baud_rate")?.value?.toIntOrNull() ?: 115200
            currentBaudRate = savedBaud
            val autoConnect = dao.getSettingByKey("auto_connect")?.value != "false"
            if (autoConnect) {
                scanAndConnect()
            }
        }
    }

    fun setSimulationMode(enabled: Boolean) {
        _isSimulationMode.value = enabled
        if (enabled) {
            _connectionState.value = ConnectionState.CONNECTED
            _connectedDeviceInfo.value = "Virtual Simulator (Offline Testing)"
            logSystem("Simulation Mode Enabled.")
        } else {
            logSystem("Simulation Disabled. Scanning real USB hardware...")
            scanAndConnect()
        }
    }

    fun updateBaudRate(rate: Int) {
        currentBaudRate = rate
        logSystem("Baud rate set to $rate bps.")
        activePort?.setParameters(baudRate = rate)
        if (_connectionState.value == ConnectionState.CONNECTED) {
            _connectedDeviceInfo.value = "${activePort?.deviceDescription ?: "Microcontroller"} @ ${currentBaudRate}bps"
        }
    }

    fun refreshAvailableDevices() {
        val devices = UsbSerialProber.probeAll(usbManager)
        _availableDevices.value = devices
    }

    fun scanAndConnect() {
        refreshAvailableDevices()
        if (_isSimulationMode.value) {
            _connectionState.value = ConnectionState.CONNECTED
            _connectedDeviceInfo.value = "Virtual Simulator"
            return
        }

        _connectionState.value = ConnectionState.CONNECTING
        logSystem("Scanning USB ports (ESP32 DevKit, CP210x, CH340, Pico RP2040, MicroPython CDC)...")

        val target = UsbSerialProber.findBestTargetDevice(usbManager)
        if (target != null) {
            val device = target.device
            logSystem("Device detected: ${target.displayName} (VID:0x${Integer.toHexString(device.vendorId)}, PID:0x${Integer.toHexString(device.productId)})")
            usbDevice = device

            if (usbManager.hasPermission(device)) {
                connectToDevice(device)
            } else {
                logSystem("Requesting USB Permission for ${target.displayName}...")
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                val permissionIntent = PendingIntent.getBroadcast(
                    context, 0, Intent(usbPermissionAction), flags
                )
                usbManager.requestPermission(device, permissionIntent)
            }
        } else {
            logSystem("No USB microcontroller detected. Connect your ESP32 or Pico via USB-OTG.")
            _connectedDeviceInfo.value = "No USB Device Detected"
            _connectionState.value = ConnectionState.OFFLINE
        }
    }

    fun connectToSpecificDevice(device: UsbDevice) {
        usbDevice = device
        if (usbManager.hasPermission(device)) {
            connectToDevice(device)
        } else {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            val permissionIntent = PendingIntent.getBroadcast(
                context, 0, Intent(usbPermissionAction), flags
            )
            usbManager.requestPermission(device, permissionIntent)
        }
    }

    private fun connectToDevice(device: UsbDevice) {
        try {
            val connection = usbManager.openDevice(device)
            if (connection == null) {
                logSystem("Failed to open USB device handle. Check OTG cable or grant permission.")
                _connectionState.value = ConnectionState.ERROR
                return
            }

            // Robust multi-driver trial: try primary chip driver, then CDC-ACM, then GenericBulk
            val primaryPort = UsbSerialProber.createPort(device)
            val driverCandidates = listOf(
                primaryPort,
                CdcAcmSerialPort(device, 0, "CDC-ACM Serial"),
                GenericBulkSerialPort(device, 0, "Generic USB Bulk")
            ).distinctBy { it.driverName }

            var successfullyOpenedPort: UsbSerialPort? = null
            for (candidate in driverCandidates) {
                if (candidate.open(connection)) {
                    successfullyOpenedPort = candidate
                    break
                }
            }

            if (successfullyOpenedPort == null) {
                logSystem("Could not initialize any serial driver for ${device.deviceName}")
                connection.close()
                _connectionState.value = ConnectionState.ERROR
                return
            }

            val baudStr = runBlocking { dao.getSettingByKey("baud_rate")?.value } ?: "115200"
            val baud = baudStr.toIntOrNull() ?: 115200
            currentBaudRate = baud
            successfullyOpenedPort.setParameters(baudRate = baud)
            // Keep DTR and RTS deasserted so ESP32 runs freely without auto-reset
            successfullyOpenedPort.setDtrRts(dtr = false, rts = false)

            this.usbConnection = connection
            this.activePort = successfullyOpenedPort
            this.usbDevice = device

            _connectionState.value = ConnectionState.CONNECTED
            _connectedDeviceInfo.value = "${successfullyOpenedPort.deviceDescription} @ ${baud}bps"
            logSystem("Hardware Link Established: ${successfullyOpenedPort.deviceDescription} @ $baud baud")

            startReadingFromDevice()

            // Gracefully query files from anims/ directory in background without blocking
            scope.launch {
                delay(600)
                refreshFileList()
            }
        } catch (e: Exception) {
            logSystem("Connection error: ${e.message}")
            _connectionState.value = ConnectionState.ERROR
        }
    }

    private fun startReadingFromDevice() {
        readJob?.cancel()
        readJob = scope.launch {
            val buffer = ByteArray(1024)
            val lineBuffer = ByteArrayOutputStream()
            var lastByteTime = System.currentTimeMillis()

            while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                val port = activePort ?: break
                val bytesRead = port.read(buffer, 80)
                val now = System.currentTimeMillis()

                if (bytesRead > 0) {
                    lastByteTime = now
                    val readData = buffer.copyOf(bytesRead)
                    for (b in readData) {
                        if (b == '\n'.toByte() || b == '\r'.toByte()) {
                            if (lineBuffer.size() > 0) {
                                val line = lineBuffer.toString("UTF-8").trim()
                                if (line.isNotEmpty()) {
                                    logRx(line)
                                    incomingLinesFlow.emit(line)
                                    checkAndAutoParseFileLine(line)
                                }
                                lineBuffer.reset()
                            }
                        } else {
                            lineBuffer.write(b.toInt())
                        }
                    }
                } else {
                    // Flush lineBuffer if > 100ms passed without newline (shows prompt '>>> ' or partial output)
                    if (lineBuffer.size() > 0 && (now - lastByteTime > 100)) {
                        val partial = lineBuffer.toString("UTF-8").trim()
                        if (partial.isNotEmpty()) {
                            logRx(partial)
                            incomingLinesFlow.emit(partial)
                            checkAndAutoParseFileLine(partial)
                        }
                        lineBuffer.reset()
                    }
                }
                delay(10)
            }
        }
    }

    // Auto-parses any file listing stream coming from the ESP32
    private fun checkAndAutoParseFileLine(line: String) {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed == PicoProtocol.RESP_END || trimmed == PicoProtocol.RESP_READY || trimmed.startsWith("ERROR")) return
        val parts = trimmed.split(" ")
        if (parts.isNotEmpty() && parts[0].endsWith(".bin", ignoreCase = true)) {
            val name = parts[0]
            val size = if (parts.size >= 2) parts[1].toLongOrNull() ?: 0L else 0L
            scope.launch {
                dao.insertPicoFiles(listOf(PicoFile(name, size, "emo")))
            }
        }
    }

    fun disconnect() {
        readJob?.cancel()
        readJob = null
        try {
            activePort?.close()
            usbConnection?.close()
        } catch (e: Exception) {
            Log.e(tag, "Error disconnecting: ${e.message}")
        }
        activePort = null
        usbConnection = null
        usbDevice = null
        _connectedDeviceInfo.value = "Disconnected"
        _connectionState.value = ConnectionState.OFFLINE
        logSystem("USB Serial disconnected.")
    }

    // Request file list from microcontroller's anims/ directory
    suspend fun refreshFileList(): List<PicoFile> = withContext(Dispatchers.IO) {
        val port = activePort
        if (port == null) {
            logSystem("Cannot sync files: Microcontroller offline.")
            return@withContext emptyList()
        }

        logSystem("Syncing files from microcontroller (anims/)...")
        val receivedFiles = mutableListOf<PicoFile>()
        var isCompleted = false

        // Listen for incoming lines BEFORE sending the command so no lines are dropped
        val collectorJob = scope.launch {
            incomingLinesFlow.collect { line ->
                val trimmed = line.trim()
                if (trimmed == PicoProtocol.RESP_END || trimmed.startsWith("ERROR")) {
                    isCompleted = true
                    cancel()
                    return@collect
                }
                val parts = trimmed.split(" ")
                if (parts.isNotEmpty() && parts[0].endsWith(".bin", ignoreCase = true)) {
                    val name = parts[0]
                    val size = if (parts.size >= 2) parts[1].toLongOrNull() ?: 0L else 0L
                    receivedFiles.add(PicoFile(name, size, "emo"))
                }
            }
        }

        delay(30)
        logTx(PicoProtocol.CMD_LIST_FILES)
        val cmdBytes = (PicoProtocol.CMD_LIST_FILES + "\r\n").toByteArray(StandardCharsets.UTF_8)
        port.write(cmdBytes, 1000)

        withTimeoutOrNull(3000) {
            while (!isCompleted) {
                delay(40)
            }
        }
        collectorJob.cancel()

        logSystem("Directory sync complete: ${receivedFiles.size} animation file(s) found in anims/.")
        dao.clearPicoFiles()
        if (receivedFiles.isNotEmpty()) {
            dao.insertPicoFiles(receivedFiles)
        }
        return@withContext receivedFiles
    }

    suspend fun writeRawBytes(data: ByteArray, timeoutMs: Int = 1000): Int = withContext(Dispatchers.IO) {
        val port = activePort ?: return@withContext -1
        port.write(data, timeoutMs)
    }

    // Direct, non-blocking send for terminal commands typed by the user or triggered by events
    fun sendTerminalCommand(command: String) {
        val port = activePort
        if (port == null) {
            logSystem("Device offline. Command ignored: $command")
            return
        }
        logTx(command)
        scope.launch(Dispatchers.IO) {
            val commandBytes = (command + "\r\n").toByteArray(StandardCharsets.UTF_8)
            port.write(commandBytes, 1000)
        }
    }

    // Command sender for structured requests with pre-subscribed collector
    suspend fun sendCommand(command: String, timeoutMs: Long = 2500): String = withContext(Dispatchers.IO) {
        commandMutex.lock()
        var response = ""

        try {
            _connectionState.value = ConnectionState.BUSY
            logTx(command)

            val port = activePort
            if (port == null) {
                logSystem("Error: USB Serial port offline.")
                return@withContext "ERROR: OFFLINE"
            }

            val resultList = mutableListOf<String>()
            var isDone = false

            // Pre-subscribe collector before writing to USB
            val collectJob = scope.launch {
                incomingLinesFlow.collect { line ->
                    resultList.add(line)
                    val trimmed = line.trim()
                    if (command == PicoProtocol.CMD_LIST_FILES) {
                        if (trimmed == PicoProtocol.RESP_END || trimmed.startsWith("ERROR")) {
                            isDone = true
                            cancel()
                        }
                    } else {
                        if (trimmed == PicoProtocol.RESP_READY ||
                            trimmed == PicoProtocol.RESP_END ||
                            trimmed.startsWith("ERROR") ||
                            resultList.size >= 1
                        ) {
                            isDone = true
                            cancel()
                        }
                    }
                }
            }

            delay(20)
            val commandBytes = (command + "\r\n").toByteArray(StandardCharsets.UTF_8)
            port.write(commandBytes, 1000)

            withTimeoutOrNull(timeoutMs) {
                while (!isDone) {
                    delay(30)
                }
            }
            collectJob.cancel()

            response = resultList.joinToString("\n")
            return@withContext response
        } finally {
            _connectionState.value = ConnectionState.CONNECTED
            commandMutex.unlock()
        }
    }

    // MicroPython REPL Controls
    fun sendCtrlC() {
        logTx("CTRL+C (Break/Interrupt)")
        scope.launch(Dispatchers.IO) {
            activePort?.write("\r\u0003\u0003".toByteArray(StandardCharsets.UTF_8), 500)
        }
    }

    fun sendCtrlD() {
        logTx("CTRL+D (Soft Reset)")
        scope.launch(Dispatchers.IO) {
            activePort?.write("\u0004".toByteArray(StandardCharsets.UTF_8), 500)
        }
    }

    fun cleanLocalLogs() {
        scope.launch {
            dao.clearLogs()
            logSystem("Console log history cleared.")
        }
    }

    private fun logTx(msg: String) {
        Log.i(tag, "TX >> $msg")
        scope.launch {
            dao.insertLog(SerialLogEntry(message = msg, direction = "TX"))
        }
    }

    private fun logRx(msg: String) {
        Log.i(tag, "RX << $msg")
        scope.launch {
            dao.insertLog(SerialLogEntry(message = msg, direction = "RX"))
        }
    }

    fun logSystem(msg: String) {
        Log.w(tag, "SYS : $msg")
        scope.launch {
            dao.insertLog(SerialLogEntry(message = msg, direction = "SYS"))
        }
    }
}
