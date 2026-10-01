package com.example.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.PicocaseApplication
import com.example.serial.PicoProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PicocaseNotificationListenerService : NotificationListenerService() {

    private val tag = "PicocaseNotifListener"
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(tag, "Notification Listener Connected and Active!")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val app = applicationContext as? PicocaseApplication ?: return
        val settingsRepo = app.settingsRepository
        val serialManager = app.serialManager

        scope.launch {
            val notificationsEnabled = settingsRepo.getBooleanSetting("notification_enabled", true)
            if (!notificationsEnabled) return@launch

            val pkg = sbn.packageName.lowercase()
            // Ignore own notifications
            if (pkg == applicationContext.packageName.lowercase()) return@launch

            val notification = sbn.notification ?: return@launch
            val extras = notification.extras
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val category = notification.category ?: ""

            Log.d(tag, "Notification from $pkg [cat=$category]: $title - $text")

            val priorityContact = settingsRepo.getSetting("priority_contact", "").trim()
            val isPriority = priorityContact.isNotEmpty() &&
                    (title.contains(priorityContact, ignoreCase = true) || text.contains(priorityContact, ignoreCase = true))

            // 1. WhatsApp & Messaging apps
            val isWhatsApp = pkg.contains("whatsapp")
            val isMessaging = isWhatsApp || pkg.contains("messaging") || pkg.contains("telegram") ||
                    pkg.contains("messenger") || pkg.contains("signal") ||
                    category == Notification.CATEGORY_MESSAGE

            if (isMessaging) {
                if (isPriority) {
                    serialManager.logSystem("[NotifBridge] Priority message from $title ($pkg)")
                    serialManager.sendTerminalCommand("ALERT_SPECIAL")
                } else {
                    serialManager.logSystem("[NotifBridge] Message from $title ($pkg)")
                    val mappedFile = app.eventMappingRepository.getMappingForEvent(PicoProtocol.EVENT_INCOMING_SMS) ?: "anims/alert_msg.bin"
                    if (mappedFile.endsWith("alert_msg.bin", ignoreCase = true) || mappedFile.isBlank()) {
                        serialManager.sendTerminalCommand(PicoProtocol.CMD_ALERT_MSG)
                    } else {
                        val path = if (mappedFile.startsWith("anims/")) mappedFile else "anims/$mappedFile"
                        serialManager.sendTerminalCommand("ALERT_EVENT $path")
                    }
                }
                return@launch
            }

            // 2. Incoming Phone Calls
            val isCall = category == Notification.CATEGORY_CALL ||
                    pkg.contains("dialer") || pkg.contains("telecom") || pkg.contains("phone") ||
                    title.contains("calling", ignoreCase = true) || title.contains("incoming call", ignoreCase = true)

            if (isCall) {
                if (isPriority) {
                    serialManager.logSystem("[NotifBridge] Priority Call from $title")
                    serialManager.sendTerminalCommand("ALERT_SPECIAL")
                } else {
                    serialManager.logSystem("[NotifBridge] Incoming Call from $title")
                    val mappedFile = app.eventMappingRepository.getMappingForEvent(PicoProtocol.EVENT_INCOMING_CALL) ?: "anims/alert_call.bin"
                    if (mappedFile.endsWith("alert_call.bin", ignoreCase = true) || mappedFile.isBlank()) {
                        serialManager.sendTerminalCommand(PicoProtocol.CMD_ALERT_CALL)
                    } else {
                        val path = if (mappedFile.startsWith("anims/")) mappedFile else "anims/$mappedFile"
                        serialManager.sendTerminalCommand("ALERT_EVENT $path")
                    }
                }
                return@launch
            }

            // 3. Missed Calls
            val isMissedCall = category == Notification.CATEGORY_MISSED_CALL ||
                    title.contains("missed call", ignoreCase = true) || text.contains("missed call", ignoreCase = true)

            if (isMissedCall) {
                serialManager.logSystem("[NotifBridge] Missed call from $title")
                val mappedFile = app.eventMappingRepository.getMappingForEvent(PicoProtocol.EVENT_MISSED_CALL) ?: "anims/alert_missed.bin"
                val path = if (mappedFile.startsWith("anims/")) mappedFile else "anims/$mappedFile"
                serialManager.sendTerminalCommand("ALERT_EVENT $path")
                return@launch
            }

            // 4. Music Playback State
            val isMusic = category == Notification.CATEGORY_TRANSPORT ||
                    pkg.contains("spotify") || pkg.contains("music") || pkg.contains("audio") ||
                    notification.actions?.any { it.title?.toString()?.contains("pause", ignoreCase = true) == true } == true

            if (isMusic) {
                val alwaysOn = settingsRepo.getBooleanSetting("always_on_enabled", false)
                if (alwaysOn) {
                    val mappedFile = app.eventMappingRepository.getMappingForEvent(PicoProtocol.EVENT_MUSIC_PLAYING) ?: "anims/emo_happy.bin"
                    serialManager.logSystem("[NotifBridge] Music playing ($pkg)")
                    serialManager.sendTerminalCommand("PLAY $mappedFile")
                }
            }
        }
    }

    companion object {
        fun isPermissionGranted(context: Context): Boolean {
            val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
            return enabledListeners.contains(context.packageName)
        }

        fun openSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
}
