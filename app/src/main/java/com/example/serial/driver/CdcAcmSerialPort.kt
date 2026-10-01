package com.example.serial.driver

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.util.Log

class CdcAcmSerialPort(
    override val device: UsbDevice,
    override val portNumber: Int = 0,
    override val deviceDescription: String = "CDC-ACM MicroPython Device"
) : UsbSerialPort {

    override val driverName: String = "CDC-ACM"
    private val tag = "CdcAcmSerialPort"

    private var connection: UsbDeviceConnection? = null
    private var controlInterface: UsbInterface? = null
    private var dataInterface: UsbInterface? = null
    private var endpointIn: UsbEndpoint? = null
    private var endpointOut: UsbEndpoint? = null

    private var currentBaudRate = 115200

    override fun open(connection: UsbDeviceConnection): Boolean {
        this.connection = connection

        // Locate Control Interface and Data Interface
        var ctrlIface: UsbInterface? = null
        var dataIface: UsbInterface? = null

        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            when (iface.interfaceClass) {
                UsbConstants.USB_CLASS_COMM -> ctrlIface = iface
                UsbConstants.USB_CLASS_CDC_DATA -> dataIface = iface
            }
        }

        // If not strictly divided into COMM and CDC_DATA, fallback to first interface
        if (ctrlIface == null) {
            ctrlIface = device.getInterface(0)
        }
        if (dataIface == null) {
            dataIface = if (device.interfaceCount > 1) device.getInterface(1) else device.getInterface(0)
        }

        // Locate endpoints in data interface (or all interfaces if shared)
        var inEp: UsbEndpoint? = null
        var outEp: UsbEndpoint? = null

        val targetInterfaces = listOfNotNull(dataIface, ctrlIface).distinct()
        for (iface in targetInterfaces) {
            for (j in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(j)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN && inEp == null) {
                        inEp = ep
                    } else if (ep.direction == UsbConstants.USB_DIR_OUT && outEp == null) {
                        outEp = ep
                    }
                }
            }
            if (inEp != null && outEp != null) break
        }

        if (inEp == null || outEp == null) {
            Log.e(tag, "Could not find Bulk IN/OUT endpoints for CDC-ACM")
            return false
        }

        if (ctrlIface != null && !connection.claimInterface(ctrlIface, true)) {
            Log.w(tag, "Failed to claim CDC control interface")
        }
        if (dataIface != null && dataIface != ctrlIface) {
            if (!connection.claimInterface(dataIface, true)) {
                Log.w(tag, "Failed to claim CDC data interface")
            }
        }

        this.controlInterface = ctrlIface
        this.dataInterface = dataIface
        this.endpointIn = inEp
        this.endpointOut = outEp

        // Initialize line coding (default 115200 8N1)
        setParameters(currentBaudRate)
        // Set DTR = 1, RTS = 1
        setDtrRts(true, true)

        Log.i(tag, "CdcAcmSerialPort opened successfully for ${device.deviceName}")
        return true
    }

    override fun setParameters(baudRate: Int, dataBits: Int, stopBits: Int, parity: Int): Boolean {
        currentBaudRate = baudRate
        val conn = connection ?: return false
        val ctrlIface = controlInterface ?: return false

        val lineCoding = ByteArray(7)
        lineCoding[0] = (baudRate and 0xFF).toByte()
        lineCoding[1] = ((baudRate shr 8) and 0xFF).toByte()
        lineCoding[2] = ((baudRate shr 16) and 0xFF).toByte()
        lineCoding[3] = ((baudRate shr 24) and 0xFF).toByte()
        lineCoding[4] = (stopBits - 1).coerceAtLeast(0).toByte() // 0 = 1 stop bit, 2 = 2 stop bits
        lineCoding[5] = parity.toByte() // 0 = none, 1 = odd, 2 = even
        lineCoding[6] = dataBits.toByte() // 5, 6, 7, 8

        val ctrlIndex = ctrlIface.id
        val ret = conn.controlTransfer(0x21, 0x20, 0, ctrlIndex, lineCoding, lineCoding.size, 1000)
        return ret >= 0
    }

    override fun setDtrRts(dtr: Boolean, rts: Boolean): Boolean {
        val conn = connection ?: return false
        val ctrlIface = controlInterface ?: return false
        var value = 0
        if (dtr) value = value or 0x01
        if (rts) value = value or 0x02
        val ctrlIndex = ctrlIface.id
        val ret = conn.controlTransfer(0x21, 0x22, value, ctrlIndex, null, 0, 1000)
        return ret >= 0
    }

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
                controlInterface?.let { conn.releaseInterface(it) }
                if (dataInterface != controlInterface) {
                    dataInterface?.let { conn.releaseInterface(it) }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error closing CDC ACM port: ${e.message}")
        }
        connection = null
        controlInterface = null
        dataInterface = null
        endpointIn = null
        endpointOut = null
    }
}
