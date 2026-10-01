package com.example.serial

object PicoProtocol {
    // Basic Controls
    const val CMD_START = "START"
    const val CMD_STOP = "STOP"
    const val CMD_PING = "PING"

    // Emotion Commands
    const val CMD_EMO_HAPPY = "EMO_HAPPY"
    const val CMD_EMO_SLEEPY = "EMO_SLEEPY"
    const val CMD_EMO_EXCITED = "EMO_EXCITED"

    // Alert Triggers
    const val CMD_ALERT_MSG = "ALERT_MSG"
    const val CMD_ALERT_CALL = "ALERT_CALL"
    const val CMD_ALERT_CHARGE = "ALERT_CHARGE"

    // File Management
    const val CMD_LIST_FILES = "LIST_FILES"
    const val CMD_DELETE_PREFIX = "DELETE "
    const val CMD_RENAME_PREFIX = "RENAME "
    const val CMD_UPLOAD_BEGIN_PREFIX = "FILE_UPLOAD_BEGIN "
    const val CMD_UPLOAD_END = "FILE_UPLOAD_END"

    // Responses from Pico
    const val RESP_READY = "READY"
    const val RESP_BUSY = "BUSY"
    const val RESP_END = "END"
    const val RESP_ERROR = "ERROR"

    // Event keys for Android Mapping
    const val EVENT_INCOMING_CALL = "incoming_call"
    const val EVENT_INCOMING_SMS = "incoming_sms"
    const val EVENT_CHARGING_STARTED = "charging_started"
    const val EVENT_MUSIC_PLAYING = "music_playing"
    const val EVENT_BATTERY_LOW = "battery_low"
    const val EVENT_MISSED_CALL = "missed_call"
}
