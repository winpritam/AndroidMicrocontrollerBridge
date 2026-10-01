package com.example.serial.driver

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.util.Log

class FtdiSerialPort(
    override val device: UsbDevice,
    override val portNumber: Int = 0,
    override val deviceDescription: String = "FTDI USB-to-UART Microcontroller"
) : UsbSerialPort {

    override val driverName: String = "FTDI"
    private val tag = "FtdiSerialPort"

    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var endpointIn: UsbEndpoint? = null
    private var endpointOut: UsbEndpoint? = null

    private var currentBaudRate = 115200

    companion object {
        private const val REQTYPE_HOST_TO_DEVICE = 0x40
        private const val SIO_RESET = 0
        private const val SIO_MODEM_CTRL = 1
        private const val SIO_SET_FLOW_CTRL = 2
        private const val SIO_SET_BAUD_RATE = 3
        private const val SIO_SET_DATA = 4
    }

    override fun open(connection: UsbDeviceConnection): Boolean {
        this.connection = connection

        val ifaceIndex = portNumber.coerceIn(0, (device.interfaceCount - 1).coerceAtLeast(0))
        val iface = device.getInterface(ifaceIndex)

        if (!connection.claimInterface(iface, true)) {
            Log.e(tag, "Failed to claim FTDI interface $ifaceIndex")
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
            Log.e(tag, "Could not find Bulk IN/OUT endpoints for FTDI")
            connection.releaseInterface(iface)
            return false
        }

        this.usbInterface = iface
        this.endpointIn = inEp
        this.endpointOut = outEp

        // Reset FTDI
        connection.controlTransfer(REQTYPE_HOST_TO_DEVICE, SIO_RESET, 0, iface.id, null, 0, 1000)

        setParameters(currentBaudRate)
        setDtrRts(true, true)

        Log.i(tag, "FtdiSerialPort opened successfully for ${device.deviceName}")
        return true
    }

    override fun setParameters(baudRate: Int, dataBits: Int, stopBits: Int, parity: Int): Boolean {
        currentBaudRate = baudRate
        val conn = connection ?: return false
        val iface = usbInterface ?: return false
        val ifaceNum = iface.id

        // FTDI Baud Divisor
        val divisor = (24000000 / (baudRate * 8)).coerceIn(1, 0xFFFF)
        conn.controlTransfer(REQTYPE_HOST_TO_DEVICE, SIO_SET_BAUD_RATE, divisor, ifaceNum, null, 0, 1000)

        // 8N1
        conn.controlTransfer(REQTYPE_HOST_TO_DEVICE, SIO_SET_DATA, 8, ifaceNum, null, 0, 1000)
        return true
    }

    override fun setDtrRts(dtr: Boolean, rts: Boolean): Boolean {
        val conn = connection ?: return false
        val iface = usbInterface ?: return false
        val ifaceNum = iface.id
        var value = 0
        if (dtr) value = value or 0x0101
        if (rts) value = value or 0x0202
        val ret = conn.controlTransfer(REQTYPE_HOST_TO_DEVICE, SIO_MODEM_CTRL, value, ifaceNum, null, 0, 1000)
        return ret >= 0
    }

    override fun read(dest: ByteArray, timeoutMs: Int): Int {
        val conn = connection ?: return -1
        val ep = endpointIn ?: return -1
        val rawBuffer = ByteArray(dest.size + 2)
        val read = conn.bulkTransfer(ep, rawBuffer, rawBuffer.size, timeoutMs)
        if (read <= 2) return 0
        // FTDI prepends 2 modem status bytes to every read packet
        val payloadLen = read - 2
        System.arraycopy(rawBuffer, 2, dest, 0, payloadLen)
        return payloadLen
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
            Log.e(tag, "Error closing FTDI port: ${e.message}")
        }
        connection = null
        usbInterface = null
        endpointIn = null
        endpointOut = null
    }
}
