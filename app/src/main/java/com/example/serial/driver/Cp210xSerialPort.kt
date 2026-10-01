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
    override val deviceDescription: String = "ESP32 DevKit (CP210x)"
) : UsbSerialPort {

    private val tag = "Cp210xSerialPort"
    override val driverName: String = "Silicon Labs CP210x"

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
        private const val UART_DISABLE = 0x0000

        private const val CONTROL_DTR = 0x0101
        private const val CONTROL_RTS = 0x0202
    }

    override fun open(connection: UsbDeviceConnection): Boolean {
        this.connection = connection

        var chosenIface: UsbInterface? = null
        var inEp: UsbEndpoint? = null
        var outEp: UsbEndpoint? = null

        // Scan all interfaces to locate the one with bulk IN and OUT endpoints
        for (idx in 0 until device.interfaceCount) {
            val candidate = device.getInterface(idx)
            var candidateIn: UsbEndpoint? = null
            var candidateOut: UsbEndpoint? = null
            for (i in 0 until candidate.endpointCount) {
                val ep = candidate.getEndpoint(i)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN && candidateIn == null) {
                        candidateIn = ep
                    } else if (ep.direction == UsbConstants.USB_DIR_OUT && candidateOut == null) {
                        candidateOut = ep
                    }
                }
            }
            if (candidateIn != null && candidateOut != null) {
                if (connection.claimInterface(candidate, true)) {
                    chosenIface = candidate
                    inEp = candidateIn
                    outEp = candidateOut
                    break
                }
            }
        }

        if (chosenIface == null || inEp == null || outEp == null) {
            Log.e(tag, "Could not claim interface with Bulk IN/OUT endpoints for CP210x on ${device.deviceName}")
            return false
        }

        this.usbInterface = chosenIface
        this.endpointIn = inEp
        this.endpointOut = outEp

        val ifaceNum = chosenIface.id

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

        // Configure DataBits, StopBits, Parity (default 8N1 -> 0x0800)
        var lineControl = (dataBits and 0x0F) shl 8
        lineControl = lineControl or (parity and 0x0F) shl 4
        lineControl = lineControl or (stopBits and 0x0F)
        conn.controlTransfer(REQTYPE_HOST_TO_INTERFACE, CP210X_SET_LINE_CTL, lineControl, ifaceNum, null, 0, 1000)

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
        val conn = connection
        val iface = usbInterface
        if (conn != null && iface != null) {
            try {
                conn.controlTransfer(REQTYPE_HOST_TO_INTERFACE, CP210X_IFC_ENABLE, UART_DISABLE, iface.id, null, 0, 500)
                conn.releaseInterface(iface)
            } catch (e: Exception) {
                Log.e(tag, "Error closing CP210x interface: ${e.message}")
            }
        }
        connection = null
        usbInterface = null
        endpointIn = null
        endpointOut = null
    }
}
