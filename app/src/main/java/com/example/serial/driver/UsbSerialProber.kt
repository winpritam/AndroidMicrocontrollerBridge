package com.example.serial.driver

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

object UsbSerialProber {

    data class DeviceInfo(
        val device: UsbDevice,
        val displayName: String,
        val chipType: String,
        val isMicroPythonTarget: Boolean
    )

    fun probeAll(usbManager: UsbManager): List<DeviceInfo> {
        val list = mutableListOf<DeviceInfo>()
        for (device in usbManager.deviceList.values) {
            val (chip, desc, isTarget) = identifyDevice(device)
            list.add(DeviceInfo(device, desc, chip, isTarget))
        }
        return list
    }

    fun findBestTargetDevice(usbManager: UsbManager): DeviceInfo? {
        val all = probeAll(usbManager)
        // Prefer recognized microcontroller chips (ESP32, Pico, CDC ACM)
        return all.firstOrNull { it.isMicroPythonTarget } ?: all.firstOrNull()
    }

    fun createPort(device: UsbDevice): UsbSerialPort {
        val (chip, desc, _) = identifyDevice(device)
        return when (chip) {
            "CP210x" -> Cp210xSerialPort(device, 0, desc)
            "CH340"  -> Ch340SerialPort(device, 0, desc)
            "FTDI"   -> FtdiSerialPort(device, 0, desc)
            "CDC-ACM"-> CdcAcmSerialPort(device, 0, desc)
            else     -> {
                if (isCdcAcmDevice(device)) {
                    CdcAcmSerialPort(device, 0, desc)
                } else {
                    GenericBulkSerialPort(device, 0, desc)
                }
            }
        }
    }

    fun identifyDevice(device: UsbDevice): Triple<String, String, Boolean> {
        val vid = device.vendorId
        val pid = device.productId

        return when {
            // Silicon Labs CP210x (ESP32 DevKit standard)
            vid == 0x10C4 -> {
                val chipName = when (pid) {
                    0xEA60 -> "CP2102/CP2109"
                    0xEA70 -> "CP2105"
                    0xEA71 -> "CP2108"
                    0xEA61 -> "CP2101"
                    else   -> "CP210x"
                }
                Triple("CP210x", "ESP32 DevKit ($chipName)", true)
            }

            // WCH CH340 / CH341 (Budget ESP32 DevKit, Nano, NodeMCU)
            vid == 0x1A86 -> {
                val chipName = when (pid) {
                    0x7523 -> "CH340G"
                    0x5523 -> "CH341A"
                    0x7522 -> "CH340C"
                    else   -> "CH340"
                }
                Triple("CH340", "ESP32 DevKit ($chipName)", true)
            }

            // Espressif Native USB (ESP32-S2, ESP32-S3, ESP32-C3)
            vid == 0x303A -> {
                val model = when (pid) {
                    0x1001 -> "ESP32-S3/C3 (USB Serial/JTAG)"
                    0x0002 -> "ESP32-S2 (USB CDC)"
                    else   -> "ESP32-Series Native USB"
                }
                Triple("CDC-ACM", model, true)
            }

            // Raspberry Pi Pico (RP2040 / RP2350)
            vid == 0x2E8A -> {
                val model = when (pid) {
                    0x0005 -> "Raspberry Pi Pico (MicroPython)"
                    0x000A -> "Raspberry Pi Pico 2 (RP2350)"
                    else   -> "Raspberry Pi RP2040"
                }
                Triple("CDC-ACM", model, true)
            }

            // FTDI Chips
            vid == 0x0403 -> {
                val chip = when (pid) {
                    0x6001 -> "FT232R"
                    0x6010 -> "FT2232"
                    0x6014 -> "FT232H"
                    0x6015 -> "FT230X"
                    else   -> "FTDI USB-UART"
                }
                Triple("FTDI", "Microcontroller ($chip)", true)
            }

            // Generic CDC-ACM (MicroPython, Arduino, STM32)
            isCdcAcmDevice(device) -> {
                val name = device.productName?.takeIf { it.isNotBlank() } ?: "MicroPython USB CDC"
                Triple("CDC-ACM", name, true)
            }

            // Generic Bulk Fallback
            else -> {
                val name = device.productName?.takeIf { it.isNotBlank() } ?: "USB Serial Device (0x${Integer.toHexString(vid)}:0x${Integer.toHexString(pid)})"
                Triple("Generic", name, false)
            }
        }
    }

    private fun isCdcAcmDevice(device: UsbDevice): Boolean {
        if (device.deviceClass == UsbConstants.USB_CLASS_COMM) return true
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA ||
                iface.interfaceClass == UsbConstants.USB_CLASS_COMM) {
                return true
            }
        }
        return false
    }
}
