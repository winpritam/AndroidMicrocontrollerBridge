package com.example.serial

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

sealed class TransferState {
    object Idle : TransferState()
    data class Validating(val info: String) : TransferState()
    data class Transferring(
        val fileName: String,
        val progress: Float, // 0.0 to 1.0
        val uploadedBytes: Long,
        val totalBytes: Long,
        val remainingBytes: Long,
        val speedKbps: Float // KB/S
    ) : TransferState()
    data class Success(val fileName: String) : TransferState()
    data class Failed(val error: String) : TransferState()
}

class FileTransferManager(private val serialManager: SerialManager) {

    private val _transferState = MutableStateFlow<TransferState>(TransferState.Idle)
    val transferState: StateFlow<TransferState> = _transferState.asStateFlow()

    suspend fun uploadAnimationFile(fileName: String, bytes: ByteArray) = withContext(Dispatchers.Default) {
        val extension = fileName.substringAfterLast(".", "").lowercase()
        if (extension != "bin" && extension != "py" && extension != "mpy") {
            _transferState.value = TransferState.Failed("Unsupported format: Only .bin, .py, and .mpy files are supported.")
            return@withContext
        }

        val fileSizeBytes = bytes.size.toLong()
        val maxUploadSize = 512 * 1024 // 512 KB
        if (fileSizeBytes > maxUploadSize) {
            _transferState.value = TransferState.Failed("File limit exceeded: Maximum size is 512 KB.")
            return@withContext
        }

        _transferState.value = TransferState.Validating("Handshaking with MicroPython / ESP32 device...")

        try {
            // Target path: .bin files MUST be placed in 'anims/' directory
            val destPath = if (extension == "bin" && !fileName.startsWith("anims/")) {
                "anims/$fileName"
            } else {
                fileName
            }

            // 1. Send FILE_UPLOAD_BEGIN
            val initResp = serialManager.sendCommand("FILE_UPLOAD_BEGIN $destPath $fileSizeBytes", timeoutMs = 4000)
            val isProtocolReady = initResp.contains("READY", ignoreCase = true) || initResp.contains("OK", ignoreCase = true)

            val chunkSize = 512
            val totalBytes = bytes.size
            var offset = 0
            val startTime = System.currentTimeMillis()

            if (isProtocolReady) {
                // Use Picocase Stream Protocol
                while (offset < totalBytes) {
                    val currentChunkSize = minOf(chunkSize, totalBytes - offset)
                    val chunkBytes = bytes.copyOfRange(offset, offset + currentChunkSize)

                    val hexStringBuilder = StringBuilder()
                    for (b in chunkBytes) {
                        hexStringBuilder.append(String.format("%02X", b))
                    }
                    val hexChunk = "DATA:${hexStringBuilder}"

                    val chunkResp = serialManager.sendCommand(hexChunk, timeoutMs = 3000, maxRetries = 2)
                    if (chunkResp.contains("ERROR", ignoreCase = true)) {
                        _transferState.value = TransferState.Failed("Device reported error on chunk write: $chunkResp")
                        return@withContext
                    }

                    offset += currentChunkSize
                    val progressRatio = offset.toFloat() / totalBytes
                    val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000f + 0.1f
                    val speed = (offset / 1024f) / elapsedSeconds

                    _transferState.value = TransferState.Transferring(
                        fileName = destPath,
                        progress = progressRatio,
                        uploadedBytes = offset.toLong(),
                        totalBytes = totalBytes.toLong(),
                        remainingBytes = (totalBytes - offset).toLong(),
                        speedKbps = speed
                    )
                }

                // Finalize session
                val finalizeResp = serialManager.sendCommand("FILE_UPLOAD_END", timeoutMs = 3000)
                if (finalizeResp.contains("READY", ignoreCase = true) || finalizeResp.contains("OK", ignoreCase = true) || finalizeResp.contains("END", ignoreCase = true)) {
                    _transferState.value = TransferState.Success(destPath)
                    serialManager.sendCommand(PicoProtocol.CMD_LIST_FILES)
                } else {
                    _transferState.value = TransferState.Failed("Failed to finalize upload: $finalizeResp")
                }
            } else {
                // Direct MicroPython REPL fallback transfer
                serialManager.logSystem("Picocase daemon not active. Attempting MicroPython REPL file write...")
                _transferState.value = TransferState.Validating("Writing via MicroPython filesystem...")

                // Ensure anims folder exists on device
                if (destPath.startsWith("anims/")) {
                    serialManager.sendCommand("import os; try:\n    os.mkdir('anims')\nexcept:\n    pass", timeoutMs = 1500)
                }

                // Open file in append/write mode
                serialManager.sendCommand("_f = open('$destPath', 'wb')", timeoutMs = 2000)

                while (offset < totalBytes) {
                    val currentChunkSize = minOf(256, totalBytes - offset) // 256 bytes per REPL chunk
                    val chunkBytes = bytes.copyOfRange(offset, offset + currentChunkSize)

                    val hexStringBuilder = StringBuilder()
                    for (b in chunkBytes) {
                        hexStringBuilder.append(String.format("%02X", b))
                    }
                    val pyWrite = "_f.write(bytes.fromhex('$hexStringBuilder'))"
                    serialManager.sendCommand(pyWrite, timeoutMs = 2500)

                    offset += currentChunkSize
                    val progressRatio = offset.toFloat() / totalBytes
                    val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000f + 0.1f
                    val speed = (offset / 1024f) / elapsedSeconds

                    _transferState.value = TransferState.Transferring(
                        fileName = destPath,
                        progress = progressRatio,
                        uploadedBytes = offset.toLong(),
                        totalBytes = totalBytes.toLong(),
                        remainingBytes = (totalBytes - offset).toLong(),
                        speedKbps = speed
                    )
                }

                serialManager.sendCommand("_f.close()", timeoutMs = 2000)
                _transferState.value = TransferState.Success(destPath)
                serialManager.sendCommand(PicoProtocol.CMD_LIST_FILES)
            }
        } catch (e: Exception) {
            _transferState.value = TransferState.Failed("Transfer exception: ${e.message}")
        }
    }

    fun resetState() {
        _transferState.value = TransferState.Idle
    }
}
