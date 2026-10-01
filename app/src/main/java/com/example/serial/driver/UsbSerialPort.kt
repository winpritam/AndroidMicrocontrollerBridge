package com.example.serial.driver

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection

interface UsbSerialPort {
    val device: UsbDevice
    val portNumber: Int
    val driverName: String
    val deviceDescription: String

    fun open(connection: UsbDeviceConnection): Boolean
    fun close()
    fun setParameters(baudRate: Int, dataBits: Int = 8, stopBits: Int = 1, parity: Int = 0): Boolean
    fun setDtrRts(dtr: Boolean, rts: Boolean): Boolean
    fun read(dest: ByteArray, timeoutMs: Int): Int
    fun write(src: ByteArray, timeoutMs: Int): Int
}
