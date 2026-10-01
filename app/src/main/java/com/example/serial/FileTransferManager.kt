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

        // ESP32 requires frames to be exact multiples of 1024 bytes (128*64/8)
        val uploadBytes = if (extension == "bin" && bytes.size % 1024 != 0) {
            val remainder = bytes.size % 1024
            val padding = 1024 - remainder
            bytes + ByteArray(padding)
        } else {
            bytes
        }

        val fileSizeBytes = uploadBytes.size.toLong()
        val maxUploadSize = 512 * 1024 // 512 KB
        if (fileSizeBytes > maxUploadSize) {
            _transferState.value = TransferState.Failed("File limit exceeded: Maximum size is 512 KB.")
            return@withContext
        }

        _transferState.value = TransferState.Validating("Preparing upload to ESP32 / MicroPython flash...")

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

            if (!isProtocolReady) {
                _transferState.value = TransferState.Failed("Microcontroller rejected upload initiation: $initResp")
                return@withContext
            }

            val chunkSize = 512
            val totalBytes = uploadBytes.size
            var offset = 0
            val startTime = System.currentTimeMillis()

            // 2. Stream DATA:<hex> chunks
            while (offset < totalBytes) {
                val currentChunkSize = minOf(chunkSize, totalBytes - offset)
                val chunkBytes = uploadBytes.copyOfRange(offset, offset + currentChunkSize)

                val hexStringBuilder = StringBuilder()
                for (b in chunkBytes) {
                    hexStringBuilder.append(String.format("%02X", b))
                }
                val hexChunk = "DATA:${hexStringBuilder}"

                val chunkResp = serialManager.sendCommand(hexChunk, timeoutMs = 3000)
                if (chunkResp.contains("ERROR", ignoreCase = true)) {
                    _transferState.value = TransferState.Failed("Device reported error on chunk write: $chunkResp")
                    return@withContext
                }

                offset += currentChunkSize
                val progress = offset.toFloat() / totalBytes
                val elapsedSec = (System.currentTimeMillis() - startTime) / 1000f
                val speed = if (elapsedSec > 0) (offset / 1024f) / elapsedSec else 0f

                _transferState.value = TransferState.Transferring(
                    fileName = destPath,
                    progress = progress,
                    uploadedBytes = offset.toLong(),
                    totalBytes = totalBytes.toLong(),
                    remainingBytes = (totalBytes - offset).toLong(),
                    speedKbps = speed
                )

                delay(10)
            }

            // 3. Complete Upload
            val endResp = serialManager.sendCommand("FILE_UPLOAD_END", timeoutMs = 4000)
            if (endResp.contains("READY", ignoreCase = true) || endResp.contains("OK", ignoreCase = true) || endResp.isEmpty()) {
                _transferState.value = TransferState.Success(destPath)
                serialManager.logSystem("Uploaded $destPath ($fileSizeBytes B) successfully.")
                // Refresh directory index on device
                serialManager.refreshFileList()
            } else {
                _transferState.value = TransferState.Failed("Device finalized upload with status: $endResp")
            }
        } catch (e: Exception) {
            _transferState.value = TransferState.Failed("Transfer exception: ${e.message}")
        }
    }

    fun resetState() {
        _transferState.value = TransferState.Idle
    }
}
