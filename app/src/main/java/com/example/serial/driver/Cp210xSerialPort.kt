package com.example.serial.driver

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.util.Log

class Cp210xSerialPort(
    override val device: UsbDevice,
    override val portNumber: Int = 0,
    override val deviceDescription: String = "ESP32 DevKit (CP210x USB-to-UART)"
) : UsbSerialPort {

    override val driverName: String = "CP210x"
    private val tag = "Cp210xSerialPort"

    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var endpointIn: UsbEndpoint? = null
    private var endpointOut: UsbEndpoint? = null

    private var currentBaudRate = 115200

    companion object {
        private const val REQTYPE_HOST_TO_INTERFACE = 0x41
        private const val CP210X_IFC_ENABLE = 0x00
        private const val CP210X_SET_BAUDDIV = 0x01
        private const val CP210X_SET_LINE_CTL = 0x03
        private const val CP210X_SET_MHS = 0x07
        private const val CP210X_SET_BAUDRATE = 0x1E

        private const val UART_ENABLE = 0x0001
        private const val CONTROL_DTR = 0x0101
        private const val CONTROL_RTS = 0x0202
    }

    override fun open(connection: UsbDeviceConnection): Boolean {
        this.connection = connection

        val ifaceIndex = portNumber.coerceIn(0, (device.interfaceCount - 1).coerceAtLeast(0))
        val iface = device.getInterface(ifaceIndex)

        if (!connection.claimInterface(iface, true)) {
            Log.e(tag, "Failed to claim CP210x interface $ifaceIndex")
            return false
        }

        var inEp: UsbEndpoint? = null
        var outEp: UsbEndpoint? = null

        for (i in 0 until iface.endpointCount) {
            val ep = iface.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.direction == UsbConstants.USB_DIR_IN && inEp == null) {
                    inEp = ep
                } else if (ep.direction == UsbConstants.USB_DIR_OUT && outEp == null) {
                    outEp = ep
                }
            }
        }

        if (inEp == null || outEp == null) {
            Log.e(tag, "Could not find Bulk IN/OUT endpoints for CP210x")
            connection.releaseInterface(iface)
            return false
        }

        this.usbInterface = iface
        this.endpointIn = inEp
        this.endpointOut = outEp

        val ifaceNum = iface.id

        // 1. Enable UART
        connection.controlTransfer(REQTYPE_HOST_TO_INTERFACE, CP210X_IFC_ENABLE, UART_ENABLE, ifaceNum, null, 0, 1000)

        // 2. Set Parameters (115200 8N1)
        setParameters(currentBaudRate)

        // 3. Set DTR = 0, RTS = 0 (Crucial for ESP32 DevKit to run without being held in reset)
        setDtrRts(false, false)

        Log.i(tag, "Cp210xSerialPort opened successfully for ${device.deviceName} at $currentBaudRate baud")
        return true
    }

    override fun setParameters(baudRate: Int, dataBits: Int, stopBits: Int, parity: Int): Boolean {
        currentBaudRate = baudRate
        val conn = connection ?: return false
        val iface = usbInterface ?: return false
        val ifaceNum = iface.id

        // Set Baudrate via 0x1E
        val baudBytes = ByteArray(4)
        baudBytes[0] = (baudRate and 0xFF).toByte()
        baudBytes[1] = ((baudRate shr 8) and 0xFF).toByte()
        baudBytes[2] = ((baudRate shr 16) and 0xFF).toByte()
        baudBytes[3] = ((baudRate shr 24) and 0xFF).toByte()

        var ret = conn.controlTransfer(REQTYPE_HOST_TO_INTERFACE, CP210X_SET_BAUDRATE, 0, ifaceNum, baudBytes, 4, 1000)
        if (ret < 0) {
            // Fallback to SET_BAUDDIV
            val divisor = (0x384000 / baudRate).coerceIn(1, 0xFFFF)
            ret = conn.controlTransfer(REQTYPE_HOST_TO_INTERFACE, CP210X_SET_BAUDDIV, divisor, ifaceNum, null, 0, 1000)
        }

        // Set Line Control: 8 Data bits, 1 stop bit, no parity (0x0800)
        val lineCtl = when (dataBits) {
            5 -> 0x0500
            6 -> 0x0600
            7 -> 0x0700
            else -> 0x0800
        } or when (stopBits) {
            2 -> 0x0002
            else -> 0x0000
        } or when (parity) {
            1 -> 0x0010 // ODD
            2 -> 0x0020 // EVEN
            else -> 0x0000 // NONE
        }
        conn.controlTransfer(REQTYPE_HOST_TO_INTERFACE, CP210X_SET_LINE_CTL, lineCtl, ifaceNum, null, 0, 1000)

        return ret >= 0
    }

    override fun setDtrRts(dtr: Boolean, rts: Boolean): Boolean {
        val conn = connection ?: return false
        val iface = usbInterface ?: return false
        val ifaceNum = iface.id

        var mhs = 0
        if (dtr) mhs = mhs or CONTROL_DTR
        if (rts) mhs = mhs or CONTROL_RTS

        val ret = conn.controlTransfer(REQTYPE_HOST_TO_INTERFACE, CP210X_SET_MHS, mhs, ifaceNum, null, 0, 1000)
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
                usbInterface?.let { conn.releaseInterface(it) }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error closing CP210x port: ${e.message}")
        }
        connection = null
        usbInterface = null
        endpointIn = null
        endpointOut = null
    }
}
