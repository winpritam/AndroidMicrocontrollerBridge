package com.example.serial.driver

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.util.Log

class GenericBulkSerialPort(
    override val device: UsbDevice,
    override val portNumber: Int = 0,
    override val deviceDescription: String = "Generic USB Serial Microcontroller"
) : UsbSerialPort {

    override val driverName: String = "Generic-Bulk"
    private val tag = "GenericBulkSerialPort"

    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var endpointIn: UsbEndpoint? = null
    private var endpointOut: UsbEndpoint? = null

    override fun open(connection: UsbDeviceConnection): Boolean {
        this.connection = connection

        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            var inEp: UsbEndpoint? = null
            var outEp: UsbEndpoint? = null

            for (j in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(j)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN && inEp == null) inEp = ep
                    if (ep.direction == UsbConstants.USB_DIR_OUT && outEp == null) outEp = ep
                }
            }

            if (inEp != null && outEp != null) {
                if (connection.claimInterface(iface, true)) {
                    this.usbInterface = iface
                    this.endpointIn = inEp
                    this.endpointOut = outEp
                    Log.i(tag, "Claimed generic interface $i for ${device.deviceName}")
                    return true
                }
            }
        }

        Log.e(tag, "Could not find pair of bulk endpoints in any interface")
        return false
    }

    override fun setParameters(baudRate: Int, dataBits: Int, stopBits: Int, parity: Int): Boolean = true
    override fun setDtrRts(dtr: Boolean, rts: Boolean): Boolean = true

    override fun read(dest: ByteArray, timeoutMs: Int): Int {
        val conn = connection ?: return -1
        val ep = endpointIn ?: return -1
        return conn.bulkTransfer(ep, dest, dest.size, timeoutMs)
    }

    override fun write(src: ByteArray, timeoutMs: Int): Int {
        val conn = connection ?: return -1
        val ep = endpointOut ?: return -1
        return conn.bulkTransfer(ep, src, src.size, timeoutMs)
    }

    override fun close() {
        try {
            connection?.let { conn ->
                usbInterface?.let { conn.releaseInterface(it) }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error closing generic port: ${e.message}")
        }
        connection = null
        usbInterface = null
        endpointIn = null
        endpointOut = null
    }
}
