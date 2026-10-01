package com.example.service

import android.app.*
import android.content.*
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.telephony.SmsMessage
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.PicocaseApplication
import com.example.data.EventMappingRepository
import com.example.data.SettingsRepository
import com.example.serial.PicoProtocol
import com.example.serial.SerialManager
import kotlinx.coroutines.*

class BackgroundEventService : Service() {

    private val tag = "BackgroundEventService"
    private val channelId = "picocase_background_channel"
    private val notificationId = 101

    private lateinit var serialManager: SerialManager
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var eventMappingRepository: EventMappingRepository

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // --- Dynamic Receivers ---
    private var batteryReceiver: BroadcastReceiver? = null
    private var smsReceiver: BroadcastReceiver? = null
    private var callReceiver: BroadcastReceiver? = null
    private var musicStateReceiver: BroadcastReceiver? = null

    companion object {
        const val ACTION_STOP_SERVICE = "com.aistudio.picocase.STOP_SERVICE"
        const val ACTION_SIMULATE_EVENT = "com.aistudio.picocase.SIMULATE_EVENT"
        const val EXTRA_EVENT_NAME = "extra_event_name"
        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val EXTRA_SMS_SENDER = "extra_sms_sender"

        var isServiceRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "Foreground Service onCreate initialized")
        val app = application as PicocaseApplication
        serialManager = app.serialManager
        settingsRepository = app.settingsRepository
        eventMappingRepository = app.eventMappingRepository
        isServiceRunning = true

        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                notificationId,
                buildNotification("Monitoring active"),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(notificationId, buildNotification("Monitoring active"))
        }

        registerMonitors()
        logToTerminal("Picocase Bridge Service started. Monitoring active.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.action) {
                ACTION_STOP_SERVICE -> {
                    logToTerminal("Service stop requested by user.")
                    stopSelf()
                }
                ACTION_SIMULATE_EVENT -> {
                    val eventName = intent.getStringExtra(EXTRA_EVENT_NAME) ?: ""
                    val caller = intent.getStringExtra(EXTRA_CALLER_NAME)
                    val sender = intent.getStringExtra(EXTRA_SMS_SENDER)
                    handleSimulatedEvent(eventName, caller, sender)
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isServiceRunning = false
        unregisterMonitors()
        serviceScope.cancel()
        logToTerminal("Picocase Bridge Service stopped.")
        super.onDestroy()
    }

    private fun registerMonitors() {
        // 1. Battery & Power Power receiver
        batteryReceiver = object : BroadcastReceiver() {
            private var lastIsCharging = false

            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_POWER_CONNECTED -> {
                        triggerEvent(PicoProtocol.EVENT_CHARGING_STARTED, "Charging Plugged")
                    }
                    Intent.ACTION_BATTERY_LOW -> {
                        triggerEvent(PicoProtocol.EVENT_BATTERY_LOW, "Battery Low Alert")
                    }
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                status == BatteryManager.BATTERY_STATUS_FULL
                        if (isCharging && !lastIsCharging) {
                            triggerEvent(PicoProtocol.EVENT_CHARGING_STARTED, "Power connected")
                        }
                        lastIsCharging = isCharging
                    }
                }
            }
        }
        val batteryFilter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(batteryReceiver, batteryFilter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(batteryReceiver, batteryFilter)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error registering battery monitor: ${e.message}")
        }

        // 2. Telephony Call Receiver
        callReceiver = object : BroadcastReceiver() {
            private var lastState = TelephonyManager.CALL_STATE_IDLE

            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                    val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                    val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: "Unknown"

                    val state = when (stateStr) {
                        TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
                        TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
                        else -> TelephonyManager.CALL_STATE_IDLE
                    }

                    if (state == TelephonyManager.CALL_STATE_RINGING) {
                        triggerCallEvent(incomingNumber)
                    } else if (state == TelephonyManager.CALL_STATE_IDLE && lastState == TelephonyManager.CALL_STATE_RINGING) {
                        triggerEvent(PicoProtocol.EVENT_MISSED_CALL, "Missed Ringing Call")
                    }
                    lastState = state
                }
            }
        }
        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                val callFilter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(callReceiver, callFilter, Context.RECEIVER_EXPORTED)
                } else {
                    registerReceiver(callReceiver, callFilter)
                }
            } else {
                Log.w(tag, "READ_PHONE_STATE permission not granted. Dynamic call monitor registration skipped.")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error registering telephony monitor: ${e.message}")
        }

        // 3. SMS Message Receiver
        smsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
                    val bundle = intent.extras
                    if (bundle != null) {
                        try {
                            val pdus = bundle.get("pdus") as Array<*>
                            for (pdu in pdus) {
                                val sms = SmsMessage.createFromPdu(pdu as ByteArray)
                                val sender = sms.originatingAddress ?: "Unknown"
                                triggerSmsEvent(sender)
                            }
                        } catch (e: Exception) {
                            Log.e(tag, "SMS Parse error: ${e.message}")
                        }
                    }
                }
            }
        }
        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                val smsFilter = IntentFilter("android.provider.Telephony.SMS_RECEIVED")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(smsReceiver, smsFilter, Context.RECEIVER_EXPORTED)
                } else {
                    registerReceiver(smsReceiver, smsFilter)
                }
            } else {
                Log.w(tag, "RECEIVE_SMS permission not granted. Dynamic SMS monitor registration skipped.")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error registering SMS monitor: ${e.message}")
        }

        // 4. Music Playback State broadcast receiver
        musicStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action ?: return
                // Check if playback is ongoing or paused
                val isPlaying = intent.getBooleanExtra("playing", false) ||
                        action.contains("playstatechanged", ignoreCase = true) && intent.hasExtra("playstate") && intent.getIntExtra("playstate", 0) == 1

                if (isPlaying) {
                    triggerEvent(PicoProtocol.EVENT_MUSIC_PLAYING, "Music Playing Started")
                } else {
                    // Send sleep mapping as a pause effect
                    serviceScope.launch {
                        val sleepModeEnabled = settingsRepository.getBooleanSetting("sleep_mode_enabled", false)
                        if (sleepModeEnabled) {
                            serialManager.sendCommand(PicoProtocol.CMD_EMO_SLEEPY)
                        }
                    }
                }
            }
        }
        val musicFilter = IntentFilter().apply {
            addAction("com.android.music.playstatechanged")
            addAction("com.android.music.playbackstatechanged")
            addAction("com.spotify.music.playbackstatechanged")
            addAction("com.spotify.music.metadatachanged")
            addAction("com.android.music.metachanged")
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(musicStateReceiver, musicFilter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(musicStateReceiver, musicFilter)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error registering music playback monitor: ${e.message}")
        }
    }

    private fun unregisterMonitors() {
        try {
            batteryReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            Log.e(tag, "Error unregistering battery monitor: ${e.message}")
        }
        try {
            smsReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            Log.e(tag, "Error unregistering SMS monitor: ${e.message}")
        }
        try {
            callReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            Log.e(tag, "Error unregistering call monitor: ${e.message}")
        }
        try {
            musicStateReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            Log.e(tag, "Error unregistering music monitor: ${e.message}")
        }
    }

    private fun triggerEvent(eventKey: String, logLabel: String) {
        serviceScope.launch {
            // Check global configurations
            val masterEnabled = settingsRepository.getBooleanSetting("run_in_background", true)
            if (!masterEnabled) return@launch

            // Specific cards checks
            when (eventKey) {
                PicoProtocol.EVENT_CHARGING_STARTED, PicoProtocol.EVENT_BATTERY_LOW -> {
                    // Battery triggers are always processed
                }
                PicoProtocol.EVENT_MUSIC_PLAYING -> {
                    val alwaysOn = settingsRepository.getBooleanSetting("always_on_enabled", false)
                    if (!alwaysOn) return@launch
                }
            }

            val animationFile = eventMappingRepository.getMappingForEvent(eventKey) ?: ""
            logToTerminal("System Trigger >> $logLabel ($eventKey). Mapping file: $animationFile")

            if (animationFile.isNotEmpty()) {
                // Command protocol sends either raw ALERT or filename map triggers
                val fileCmd = when (eventKey) {
                    PicoProtocol.EVENT_CHARGING_STARTED -> PicoProtocol.CMD_ALERT_CHARGE
                    PicoProtocol.EVENT_BATTERY_LOW -> "ALERT_BATTERY"
                    PicoProtocol.EVENT_INCOMING_SMS -> PicoProtocol.CMD_ALERT_MSG
                    PicoProtocol.EVENT_INCOMING_CALL -> PicoProtocol.CMD_ALERT_CALL
                    else -> "ALERT_EVENT $animationFile"
                }

                serialManager.sendCommand(fileCmd)
            }
        }
    }

    private fun triggerCallEvent(phoneNumber: String) {
        serviceScope.launch {
            Log.d(tag, "Ringing event for phone: $phoneNumber")
            val isNotificationsEnabled = settingsRepository.getBooleanSetting("notification_enabled", true)
            if (!isNotificationsEnabled) return@launch

            // Check priority contact rule:
            val priorityContact = settingsRepository.getSetting("priority_contact", "").trim()
            if (priorityContact.isNotEmpty() && phoneNumber.contains(priorityContact, ignoreCase = true)) {
                logToTerminal("Priority Contact Call ($priorityContact)! Triggering: ALERT_SPECIAL")
                serialManager.sendCommand("ALERT_SPECIAL")
                return@launch
            }

            // Normal Call Flow
            val file = eventMappingRepository.getMappingForEvent(PicoProtocol.EVENT_INCOMING_CALL) ?: "alert_call.bin"
            logToTerminal("Call Trigger from $phoneNumber. File mapping: $file")
            serialManager.sendCommand(PicoProtocol.CMD_ALERT_CALL)
        }
    }

    private fun triggerSmsEvent(sender: String) {
        serviceScope.launch {
            val isNotificationsEnabled = settingsRepository.getBooleanSetting("notification_enabled", true)
            if (!isNotificationsEnabled) return@launch

            val priorityContact = settingsRepository.getSetting("priority_contact", "").trim()
            if (priorityContact.isNotEmpty() && sender.contains(priorityContact, ignoreCase = true)) {
                logToTerminal("Priority Contact SMS ($priorityContact)! Triggering: ALERT_SPECIAL")
                serialManager.sendCommand("ALERT_SPECIAL")
                return@launch
            }

            val file = eventMappingRepository.getMappingForEvent(PicoProtocol.EVENT_INCOMING_SMS) ?: "alert_msg.bin"
            logToTerminal("SMS received from $sender. File mapping: $file")
            serialManager.sendCommand(PicoProtocol.CMD_ALERT_MSG)
        }
    }

    private fun handleSimulatedEvent(eventName: String, caller: String?, sender: String?) {
        Log.d(tag, "Simulating event in background: $eventName")
        when (eventName) {
            PicoProtocol.EVENT_INCOMING_CALL -> triggerCallEvent(caller ?: "Incoming Call")
            PicoProtocol.EVENT_INCOMING_SMS -> triggerSmsEvent(sender ?: "Incoming Message")
            PicoProtocol.EVENT_CHARGING_STARTED -> triggerEvent(PicoProtocol.EVENT_CHARGING_STARTED, "Charging Started [Sim]")
            PicoProtocol.EVENT_BATTERY_LOW -> triggerEvent(PicoProtocol.EVENT_BATTERY_LOW, "Battery System Low [Sim]")
            PicoProtocol.EVENT_MUSIC_PLAYING -> triggerEvent(PicoProtocol.EVENT_MUSIC_PLAYING, "Music Track Play [Sim]")
            PicoProtocol.EVENT_MISSED_CALL -> triggerEvent(PicoProtocol.EVENT_MISSED_CALL, "Missed Ringing Call [Sim]")
        }
    }

    private fun logToTerminal(msg: String) {
        serialManager.logSystem("[BridgeService] $msg")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Picocase Event Bridge Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors device status updates and streams indicators to the phone case over USB."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = Intent(this, BackgroundEventService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, flags)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Picocase Active Companion")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Service", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
