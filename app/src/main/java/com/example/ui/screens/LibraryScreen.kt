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
import com.example.serial.TransferState
import com.example.viewmodel.MainViewModel

@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val files by viewModel.filesList.collectAsStateWithLifecycle()
    val transferState by viewModel.transferState.collectAsStateWithLifecycle()
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
                    text = "Microcontroller anims/ Storage",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Upload animations (.bin) to 'anims/' directory (1024 B/frame) or load official presets directly from GitHub.",
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
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = "Upload", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pick File", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { showPresetDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = "Presets", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("GitHub Presets", fontSize = 11.sp)
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
                    text = "anims/ Directory Partition",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${files.size} active animation(s) in flash memory",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }

            IconButton(
                onClick = {
                    viewModel.refreshFileList()
                    Toast.makeText(context, "Syncing anims/ directory...", Toast.LENGTH_SHORT).show()
                },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Icon(Icons.Default.Sync, contentDescription = "Sync indexes", tint = MaterialTheme.colorScheme.primary)
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
                        text = "No Animations Synced Yet",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "Tap 'Sync' to read device flash or 'GitHub Presets' to upload.",
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
                        placeholder = { Text("e.g. alert_call") },
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

    // Presets Modal Dialog (Loads directly from winpritam/pico-case GitHub repo)
    if (showPresetDialog) {
        AlertDialog(
            onDismissRequest = { showPresetDialog = false },
            title = { Text("Load GitHub Animation Presets", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Downloads directly from winpritam/pico-case/anims and uploads to device:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    PresetChoiceItem(
                        title = "emo_happy.bin",
                        info = "Happy looping emotion face -> anims/emo_happy.bin",
                        dotColor = Color(0xFF62EFE4)
                    ) {
                        viewModel.loadPresetFromGitHub("emo_happy.bin", fallbackFrames = 4)
                        showPresetDialog = false
                    }

                    PresetChoiceItem(
                        title = "alert_call.bin",
                        info = "Incoming call alert animation -> anims/alert_call.bin",
                        dotColor = Color(0xFFFFD166)
                    ) {
                        viewModel.loadPresetFromGitHub("alert_call.bin", fallbackFrames = 3)
                        showPresetDialog = false
                    }

                    PresetChoiceItem(
                        title = "alert_msg.bin",
                        info = "Incoming message envelope -> anims/alert_msg.bin",
                        dotColor = Color(0xFFB08CFF)
                    ) {
                        viewModel.loadPresetFromGitHub("alert_msg.bin", fallbackFrames = 2)
                        showPresetDialog = false
                    }

                    PresetChoiceItem(
                        title = "alert_charge.bin",
                        info = "Battery charging lightning alert -> anims/alert_charge.bin",
                        dotColor = Color(0xFFFF6D80)
                    ) {
                        viewModel.loadPresetFromGitHub("alert_charge.bin", fallbackFrames = 2)
                        showPresetDialog = false
                    }

                    PresetChoiceItem(
                        title = "alert_battery.bin",
                        info = "Low battery warning alert -> anims/alert_battery.bin",
                        dotColor = Color(0xFFE91E63)
                    ) {
                        viewModel.loadPresetFromGitHub("alert_battery.bin", fallbackFrames = 2)
                        showPresetDialog = false
                    }

                    PresetChoiceItem(
                        title = "alert_missed.bin",
                        info = "Missed call notification alert -> anims/alert_missed.bin",
                        dotColor = Color(0xFF4CAF50)
                    ) {
                        viewModel.loadPresetFromGitHub("alert_missed.bin", fallbackFrames = 2)
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
