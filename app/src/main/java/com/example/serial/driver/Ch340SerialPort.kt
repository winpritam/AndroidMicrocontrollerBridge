package com.example.serial.driver

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.util.Log

class Ch340SerialPort(
    override val device: UsbDevice,
    override val portNumber: Int = 0,
    override val deviceDescription: String = "ESP32 DevKit (CH340)"
) : UsbSerialPort {

    private val tag = "Ch340SerialPort"
    override val driverName: String = "WCH CH340 / CH341"

    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var endpointIn: UsbEndpoint? = null
    private var endpointOut: UsbEndpoint? = null
    private var currentBaudRate = 115200

    companion object {
        private const val REQTYPE_HOST_TO_DEVICE = 0x40
        private const val REQTYPE_DEVICE_TO_HOST = 0xC0

        private const val CH341_REQ_WRITE_REG = 0x9A
        private const val CH341_REQ_MODEM_CTRL = 0xA4
    }

    override fun open(connection: UsbDeviceConnection): Boolean {
        this.connection = connection

        var chosenIface: UsbInterface? = null
        var inEp: UsbEndpoint? = null
        var outEp: UsbEndpoint? = null

        // Scan all interfaces to locate bulk IN and OUT endpoints
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
            Log.e(tag, "Could not find Bulk IN/OUT endpoints for CH340 on ${device.deviceName}")
            return false
        }

        this.usbInterface = chosenIface
        this.endpointIn = inEp
        this.endpointOut = outEp

        // CH340 handshake init sequence
        initDevice()

        setParameters(currentBaudRate)
        setDtrRts(false, false)

        Log.i(tag, "Ch340SerialPort opened successfully for ${device.deviceName}")
        return true
    }

    private fun initDevice() {
        val conn = connection ?: return
        conn.controlTransfer(REQTYPE_HOST_TO_DEVICE, 0xA1, 0xC29C, 0xB2B9, null, 0, 1000)
        conn.controlTransfer(REQTYPE_HOST_TO_DEVICE, CH341_REQ_MODEM_CTRL, 0xFF, 0, null, 0, 1000)
    }

    override fun setParameters(baudRate: Int, dataBits: Int, stopBits: Int, parity: Int): Boolean {
        currentBaudRate = baudRate
        val conn = connection ?: return false

        // Compute CH340 divisor and prescaler
        val (factor, divisor) = when (baudRate) {
            921600 -> Pair(0xF3, 7)
            115200 -> Pair(0xCC, 7)
            57600  -> Pair(0xD8, 7)
            38400  -> Pair(0xE5, 7)
            19200  -> Pair(0x64, 7)
            9600   -> Pair(0xB2, 6)
            else   -> Pair(0xCC, 7)
        }

        val regValue = (factor shl 8) or divisor
        conn.controlTransfer(REQTYPE_HOST_TO_DEVICE, CH341_REQ_WRITE_REG, 0x1312, regValue, null, 0, 1000)
        conn.controlTransfer(REQTYPE_HOST_TO_DEVICE, CH341_REQ_WRITE_REG, 0x0F2C, 0x0000, null, 0, 1000)

        return true
    }

    override fun setDtrRts(dtr: Boolean, rts: Boolean): Boolean {
        val conn = connection ?: return false
        var mhs = 0
        if (!dtr) mhs = mhs or 0x20
        if (!rts) mhs = mhs or 0x40
        val ret = conn.controlTransfer(REQTYPE_HOST_TO_DEVICE, CH341_REQ_MODEM_CTRL, mhs, 0, null, 0, 1000)
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
                conn.releaseInterface(iface)
            } catch (e: Exception) {
                Log.e(tag, "Error releasing CH340 interface: ${e.message}")
            }
        }
        connection = null
        usbInterface = null
        endpointIn = null
        endpointOut = null
    }
}
