package com.example.service.monitors

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.PicocaseApplication
import com.example.serial.PicoProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationMonitor : NotificationListenerService() {

    private val tag = "NotificationMonitor"

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val app = application as? PicocaseApplication ?: return
        val packageName = sbn.packageName ?: ""

        // Ignore our own notification channel updates
        if (packageName == sbn.packageName && sbn.tag?.contains("picocase") == true) return

        CoroutineScope(Dispatchers.Default).launch {
            val isNotifEnabled = app.settingsRepository.getBooleanSetting("notification_enabled", true)
            if (!isNotifEnabled) return@launch

            val isMiniMsgEnabled = app.settingsRepository.getBooleanSetting("mini_message_enabled", false)

            Log.d(tag, "Notification posted by: $packageName")
            app.serialManager.logSystem("[Notif] Incoming banner from $packageName")

            if (isMiniMsgEnabled) {
                // Send custom mini msg ticker to Pico
                app.serialManager.sendCommand("ALERT_MSG")
            } else {
                // Send default mapping
                val file = app.eventMappingRepository.getMappingForEvent(PicoProtocol.EVENT_INCOMING_SMS) ?: "alert_msg.bin"
                app.serialManager.sendCommand(PicoProtocol.CMD_ALERT_MSG)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        Log.d(tag, "Notification dismissed")
    }
}
