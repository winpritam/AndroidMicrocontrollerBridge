package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.PicoFile
import com.example.serial.MicroPythonHelper
import com.example.serial.TransferState
import com.example.viewmodel.MainViewModel
import java.nio.charset.StandardCharsets

@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val files by viewModel.filesList.collectAsStateWithLifecycle()
    val transferState by viewModel.transferState.collectAsStateWithLifecycle()
    val sda by viewModel.sdaPin.collectAsStateWithLifecycle()
    val scl by viewModel.sclPin.collectAsStateWithLifecycle()
    val delay by viewModel.frameDelay.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Dialog control states
    var showRenameDialog by remember { mutableStateOf<PicoFile?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var showPresetDialog by remember { mutableStateOf(false) }

    // Native file picker contract
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val contentResolver = context.contentResolver
                var displayName = "animation.bin"
                val cursor = contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            displayName = it.getString(nameIndex)
                        }
                    }
                }
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) {
                    viewModel.uploadAnimation(displayName, bytes)
                } else {
                    Toast.makeText(context, "Could not load file contents", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Read error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Master Upload CTA Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "MicroPython Animation & Script Storage",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Upload animations (.bin) to 'anims/' directory (1024 B/frame for 128x64 OLED) and Python scripts (.py, .mpy) to device flash.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = "Upload")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pick File", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { showPresetDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Presets")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Presets", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Live Upload Progress Sheet
        AnimatedVisibility(
            visible = transferState !is TransferState.Idle,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            UploadProgressCard(
                state = transferState,
                onDismiss = { viewModel.clearUploadState() }
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Library Index Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Microcontroller Partition",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${files.size} active files in 'anims/' and root",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }

            IconButton(
                onClick = { viewModel.refreshFileList() },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Icon(Icons.Default.Sync, contentDescription = "Sync indexes")
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Files listing
        if (files.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Empty storage",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No Files Synced Yet",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "Tap 'Sync' to read device flash or 'Add Presets' to upload.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(files) { file ->
                    FileCard(
                        file = file,
                        onPlayClick = {
                            viewModel.playAnimation(file.fileName)
                            Toast.makeText(context, "Playing ${file.fileName} on OLED", Toast.LENGTH_SHORT).show()
                        },
                        onDeleteClick = { viewModel.deleteAnimation(file.fileName) },
                        onRenameClick = {
                            showRenameDialog = file
                            renameInput = file.fileName.substringBeforeLast(".")
                        }
                    )
                }
            }
        }
    }

    // Rename dialog
    if (showRenameDialog != null) {
        val target = showRenameDialog!!
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Rename File", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Rename target '${target.fileName}':", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        singleLine = true,
                        placeholder = { Text("e.g. alert_ringing") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            viewModel.renameAnimation(target.fileName, renameInput)
                        }
                        showRenameDialog = null
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Presets Modal Dialog
    if (showPresetDialog) {
        AlertDialog(
            onDismissRequest = { showPresetDialog = false },
            title = { Text("MicroPython File Presets", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Presets formatted for 128x64 SSD1306 (1024 bytes/frame):", fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))

                    PresetChoiceItem(
                        title = "emo_happy.bin",
                        info = "4,096 bytes (4 frames @ 1024 B/frame) -> anims/",
                        dotColor = Color(0xFF62EFE4)
                    ) {
                        viewModel.uploadAnimation("emo_happy.bin", generateSampleFrames(4))
                        showPresetDialog = false
                    }

                    PresetChoiceItem(
                        title = "alert_call.bin",
                        info = "3,072 bytes (3 frames @ 1024 B/frame) -> anims/",
                        dotColor = Color(0xFFFFD166)
                    ) {
                        viewModel.uploadAnimation("alert_call.bin", generateSampleFrames(3))
                        showPresetDialog = false
                    }

                    PresetChoiceItem(
                        title = "alert_msg.bin",
                        info = "2,048 bytes (2 frames @ 1024 B/frame) -> anims/",
                        dotColor = Color(0xFFB08CFF)
                    ) {
                        viewModel.uploadAnimation("alert_msg.bin", generateSampleFrames(2))
                        showPresetDialog = false
                    }

                    PresetChoiceItem(
                        title = "alert_charge.bin",
                        info = "2,048 bytes (2 frames @ 1024 B/frame) -> anims/",
                        dotColor = Color(0xFFFF6D80)
                    ) {
                        viewModel.uploadAnimation("alert_charge.bin", generateSampleFrames(2))
                        showPresetDialog = false
                    }

                    PresetChoiceItem(
                        title = "main.py (ESP32 Driver)",
                        info = "MicroPython OLED listener script -> /main.py",
                        dotColor = Color(0xFF4CAF50)
                    ) {
                        val sdaPinNum = sda.toIntOrNull() ?: 21
                        val sclPinNum = scl.toIntOrNull() ?: 22
                        val delayMs = delay.toIntOrNull() ?: 120
                        val py = MicroPythonHelper.generateDeviceCode(sdaPin = sdaPinNum, sclPin = sclPinNum, frameDelayMs = delayMs)
                        viewModel.uploadAnimation("main.py", py.toByteArray(StandardCharsets.UTF_8))
                        showPresetDialog = false
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPresetDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

// Generate valid 1024-byte frames for 128x64 monochrome OLED
private fun generateSampleFrames(frameCount: Int): ByteArray {
    val frameSize = 1024
    val total = frameSize * frameCount
    val result = ByteArray(total)
    for (f in 0 until frameCount) {
        val offset = f * frameSize
        for (i in 0 until frameSize) {
            // Pattern creating smiling eyes / facial contours
            val row = i / 16
            val col = i % 16
            val isBorder = row == 0 || row == 63 || col == 0 || col == 15
            val isEye = (row in 20..26) && (col in 3..5 || col in 10..12)
            val isSmile = (row in (42 + f)..46) && (col in 4..11)
            result[offset + i] = if (isBorder || isEye || isSmile) 0xFF.toByte() else 0x00.toByte()
        }
    }
    return result
}

@Composable
fun PresetChoiceItem(
    title: String,
    info: String,
    dotColor: Color,
    onSelect: () -> Unit
) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(info, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun UploadProgressCard(
    state: TransferState,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column {
            when (state) {
                is TransferState.Validating -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(state.info, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                is TransferState.Transferring -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Writing: ${state.fileName}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${state.uploadedBytes} / ${state.totalBytes} B | Speed: ${"%.1f".format(state.speedKbps)} KB/s",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${(state.progress * 100).toInt()}%",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                }
                is TransferState.Success -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Saved to device: ${state.fileName}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                }
                is TransferState.Failed -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = "Failed",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = state.error,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 3,
                                lineHeight = 13.sp
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                }
                TransferState.Idle -> {}
            }
        }
    }
}

@Composable
fun FileCard(
    file: PicoFile,
    onPlayClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRenameClick: () -> Unit
) {
    val isAnim = file.fileName.endsWith(".bin", ignoreCase = true)
    val indicatorColor = if (isAnim) MaterialTheme.colorScheme.primary else Color(0xFF4CAF50)
    val formattedSize = "${file.sizeBytes / 1024} KB (${file.sizeBytes} B)"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(indicatorColor)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = file.fileName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$formattedSize | ${if (isAnim) "OLED ANIMATION" else "PYTHON SCRIPT"}",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isAnim) {
                    IconButton(
                        onClick = onPlayClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play on OLED",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onRenameClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Rename",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
