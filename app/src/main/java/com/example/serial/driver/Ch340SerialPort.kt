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
    override val deviceDescription: String = "ESP32 DevKit (CH340 USB-to-UART)"
) : UsbSerialPort {

    override val driverName: String = "CH340"
    private val tag = "Ch340SerialPort"

    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var endpointIn: UsbEndpoint? = null
    private var endpointOut: UsbEndpoint? = null

    private var currentBaudRate = 115200

    companion object {
        private const val REQTYPE_HOST_TO_DEVICE = 0x40
        private const val CH341_REQ_WRITE_REG = 0x9A
        private const val CH341_REQ_MODEM_CTRL = 0xA4
        private const val CH341_REG_BREAK = 0x05
        private const val CH341_REG_LCR = 0x18
        private const val CH341_NBREAK_BITS = 0x01
    }

    override fun open(connection: UsbDeviceConnection): Boolean {
        this.connection = connection

        val ifaceIndex = portNumber.coerceIn(0, (device.interfaceCount - 1).coerceAtLeast(0))
        val iface = device.getInterface(ifaceIndex)

        if (!connection.claimInterface(iface, true)) {
            Log.e(tag, "Failed to claim CH340 interface $ifaceIndex")
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
            Log.e(tag, "Could not find Bulk IN/OUT endpoints for CH340")
            connection.releaseInterface(iface)
            return false
        }

        this.usbInterface = iface
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

        // CH340 baud divisor lookup / calculation
        val (factorVal, factorIndex) = when (baudRate) {
            921600 -> Pair(0xF307, 0x1312)
            115200 -> Pair(0xCC83, 0x1312)
            57600  -> Pair(0xE682, 0x1312)
            38400  -> Pair(0x6482, 0x1312)
            19200  -> Pair(0xB281, 0x1312)
            9600   -> Pair(0xD980, 0x1312)
            else   -> {
                val factor = calculateCh340BaudFactor(baudRate)
                Pair(factor, 0x1312)
            }
        }

        val r1 = conn.controlTransfer(REQTYPE_HOST_TO_DEVICE, CH341_REQ_WRITE_REG, factorIndex, factorVal, null, 0, 1000)
        val r2 = conn.controlTransfer(REQTYPE_HOST_TO_DEVICE, CH341_REQ_WRITE_REG, 0x0F2C, 0x0007, null, 0, 1000)

        // Line control (8N1)
        val lcr = 0xC3 // 8 bits, 1 stop bit, no parity
        conn.controlTransfer(REQTYPE_HOST_TO_DEVICE, CH341_REQ_WRITE_REG, 0x2518, lcr, null, 0, 1000)

        return r1 >= 0 && r2 >= 0
    }

    private fun calculateCh340BaudFactor(baud: Int): Int {
        val factor = 1532620800L / baud
        var div = 3
        var f = factor
        while (f > 0xFF && div > 0) {
            f = f shr 3
            div--
        }
        val a = (0x100 - f).toInt() and 0xFF
        val b = 0x80 or div
        return (a shl 8) or b
    }

    override fun setDtrRts(dtr: Boolean, rts: Boolean): Boolean {
        val conn = connection ?: return false
        var modem = 0
        if (dtr) modem = modem or 0x20
        if (rts) modem = modem or 0x40
        val ret = conn.controlTransfer(REQTYPE_HOST_TO_DEVICE, CH341_REQ_MODEM_CTRL, modem xor 0xFF, 0, null, 0, 1000)
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
            Log.e(tag, "Error closing CH340 port: ${e.message}")
        }
        connection = null
        usbInterface = null
        endpointIn = null
        endpointOut = null
    }
}
