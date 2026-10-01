package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.serial.MicroPythonHelper
import com.example.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Config streams bindings
    val autoConnect by viewModel.autoConnect.collectAsStateWithLifecycle()
    val runInBg by viewModel.runInBackground.collectAsStateWithLifecycle()
    val startBoot by viewModel.startServiceOnBoot.collectAsStateWithLifecycle()
    val logsEnabled by viewModel.enableLogging.collectAsStateWithLifecycle()
    val selectAlwaysReconnect by viewModel.reconnectAutomatically.collectAsStateWithLifecycle()
    val baudRate by viewModel.baudRate.collectAsStateWithLifecycle()

    val sdaPin by viewModel.sdaPin.collectAsStateWithLifecycle()
    val sclPin by viewModel.sclPin.collectAsStateWithLifecycle()
    val boardModel by viewModel.boardModel.collectAsStateWithLifecycle()
    val frameDelay by viewModel.frameDelay.collectAsStateWithLifecycle()

    // GitHub Script states
    val githubStatus by viewModel.githubSyncStatus.collectAsStateWithLifecycle()
    val isGithubSyncing by viewModel.isGithubSyncing.collectAsStateWithLifecycle()
    val githubCachedScript by viewModel.githubCachedScript.collectAsStateWithLifecycle()

    var expandedBaudMenu by remember { mutableStateOf(false) }
    var expandedBoardMenu by remember { mutableStateOf(false) }
    val baudRatesList = listOf("9600", "19200", "38400", "57600", "115200", "921600")
    val boardProfiles = listOf(
        "ESP32 DevKit (SDA 21, SCL 22)",
        "Raspberry Pi Pico (SDA 4, SCL 5)",
        "ESP32-S2 / S3 (SDA 8, SCL 9)",
        "Custom MicroPython"
    )

    // Dialog state controllers
    var showDeployDialog by remember { mutableStateOf(false) }
    var showHardwareDialog by remember { mutableStateOf(false) }
    var showCodeDialog by remember { mutableStateOf(false) }
    var showWipeAnimDialog by remember { mutableStateOf(false) }

    // Launcher for choosing a .py file from the phone
    val pyFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val contentResolver = context.contentResolver
                var displayName = "main.py"
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
                    viewModel.deployPickedScript(displayName, bytes)
                    Toast.makeText(context, "Deploying $displayName to device /main.py...", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to read file", Toast.LENGTH_SHORT).show()
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
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "Hardware & Script Settings",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Manage MicroPython main.py scripts, GitHub updates, and OLED pins",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(bottom = 14.dp)
        )

        // 1. GitHub Script Auto-Update Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = "GitHub Sync",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "GitHub Script Auto-Update",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "winpritam/pico-case main.py",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.syncFromGitHub() },
                        enabled = !isGithubSyncing,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        if (isGithubSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sync", fontSize = 11.sp)
                        }
                    }
                }

                Text(
                    text = githubStatus,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showDeployDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = "Deploy", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Deploy main.py", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { showCodeDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Code, contentDescription = "View Code", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("View Code", fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 2. Target Microcontroller & Pins Section
        Text(
            text = "Microcontroller & OLED Configuration",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Board Profile Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hardware Profile", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(boardModel, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Box {
                        Button(
                            onClick = { expandedBoardMenu = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(boardModel.take(14), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown", modifier = Modifier.size(16.dp))
                        }

                        DropdownMenu(
                            expanded = expandedBoardMenu,
                            onDismissRequest = { expandedBoardMenu = false }
                        ) {
                            boardProfiles.forEach { profile ->
                                DropdownMenuItem(
                                    text = { Text(profile, fontSize = 12.sp) },
                                    onClick = {
                                        expandedBoardMenu = false
                                        when {
                                            profile.startsWith("ESP32 DevKit") -> viewModel.saveHardwareConfig("21", "22", "ESP32 DevKit", frameDelay)
                                            profile.startsWith("Raspberry Pi Pico") -> viewModel.saveHardwareConfig("4", "5", "Raspberry Pi Pico", frameDelay)
                                            profile.startsWith("ESP32-S2") -> viewModel.saveHardwareConfig("8", "9", "ESP32-S2/S3", frameDelay)
                                            else -> viewModel.saveHardwareConfig(sdaPin, sclPin, "Custom", frameDelay)
                                        }
                                        Toast.makeText(context, "Set profile: $profile", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // OLED I2C Pinout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("OLED SSD1306 Pins", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("SDA: GPIO $sdaPin | SCL: GPIO $sclPin | Timing: ${frameDelay}ms", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                    }

                    OutlinedButton(
                        onClick = { showHardwareDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = "Edit Pins", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit Pins", fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 3. Serial USB Link Card
        Text(
            text = "Serial Connection Settings",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Baud Rate Configuration
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Baud Rate",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Baud Rate",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "MicroPython default is 115200 bps.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Box {
                        Button(
                            onClick = { expandedBaudMenu = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(text = baudRate, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown", modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(
                            expanded = expandedBaudMenu,
                            onDismissRequest = { expandedBaudMenu = false }
                        ) {
                            baudRatesList.forEach { rate ->
                                DropdownMenuItem(
                                    text = { Text(rate) },
                                    onClick = {
                                        viewModel.changeBaudRate(rate)
                                        expandedBaudMenu = false
                                        Toast.makeText(context, "Baud rate changed to $rate", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Auto connect
                SettingsRow(
                    title = "Auto Connect USB-OTG",
                    desc = "Connects on USB attachment (no auto-upload or code overwrite on connect).",
                    icon = Icons.Default.Usb,
                    checked = autoConnect,
                    iconColor = MaterialTheme.colorScheme.secondary
                ) {
                    viewModel.toggleAutoConnect(it)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Runs background
                SettingsRow(
                    title = "Run in Background",
                    desc = "Keep serial connection and notification bridge active in background.",
                    icon = Icons.Default.PlayArrow,
                    checked = runInBg,
                    iconColor = MaterialTheme.colorScheme.tertiary
                ) {
                    viewModel.toggleRunInBackground(it)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Start on Boot
                SettingsRow(
                    title = "Start on Boot",
                    desc = "Automatically start background companion when phone reboots.",
                    icon = Icons.Default.SettingsPower,
                    checked = startBoot,
                    iconColor = MaterialTheme.colorScheme.primary
                ) {
                    viewModel.toggleStartServiceOnBoot(it)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Logging
                SettingsRow(
                    title = "Maintain Local Logs",
                    desc = "Store incoming and outgoing serial packets in Room database.",
                    icon = Icons.Default.LogoDev,
                    checked = logsEnabled,
                    iconColor = MaterialTheme.colorScheme.secondary
                ) {
                    viewModel.toggleEnableLogging(it)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Danger Zone Section
        Text(
            text = "Storage Maintenance",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f)
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Wipe anims/ Directory", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text("Delete all animation files from device storage.", fontSize = 10.sp, color = Color.Gray)
                    }

                    Button(
                        onClick = { showWipeAnimDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Wipe anims/", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Modal Prompt when "Deploy main.py" is tapped (Prompt to choose .py file or use GitHub synced)
    if (showDeployDialog) {
        AlertDialog(
            onDismissRequest = { showDeployDialog = false },
            title = { Text("Deploy main.py to Device", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select source script to flash onto the device as /main.py:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Card(
                        onClick = {
                            showDeployDialog = false
                            pyFilePickerLauncher.launch("*/*")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Pick file", tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Choose .py File from Phone", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                Text("Select any custom Python script from your phone storage", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Card(
                        onClick = {
                            showDeployDialog = false
                            viewModel.deployGitHubMainPy()
                            Toast.makeText(context, "Deploying GitHub main.py to device...", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = "GitHub", tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Use GitHub main.py (winpritam)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Auto-synced from github.com/winpritam/pico-case", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDeployDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Hardware Pinout Configuration Dialog
    if (showHardwareDialog) {
        var sdaInput by remember { mutableStateOf(sdaPin) }
        var sclInput by remember { mutableStateOf(sclPin) }
        var delayInput by remember { mutableStateOf(frameDelay) }

        AlertDialog(
            onDismissRequest = { showHardwareDialog = false },
            title = { Text("Configure OLED I2C Pins", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Specify GPIO pins connected to SSD1306 OLED display:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    OutlinedTextField(
                        value = sdaInput,
                        onValueChange = { sdaInput = it },
                        label = { Text("OLED SDA (GPIO)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = sclInput,
                        onValueChange = { sclInput = it },
                        label = { Text("OLED SCL (GPIO)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = delayInput,
                        onValueChange = { delayInput = it },
                        label = { Text("Frame Delay (ms)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveHardwareConfig(sdaInput.trim(), sclInput.trim(), boardModel, delayInput.trim())
                        showHardwareDialog = false
                        Toast.makeText(context, "Saved: SDA=$sdaInput, SCL=$sclInput", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHardwareDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // View MicroPython Code Dialog
    if (showCodeDialog) {
        val sdaNum = sdaPin.toIntOrNull() ?: 21
        val sclNum = sclPin.toIntOrNull() ?: 22
        val delayNum = frameDelay.toIntOrNull() ?: 120
        val codeToDisplay = if (githubCachedScript.isNotBlank()) {
            githubCachedScript
        } else {
            MicroPythonHelper.generateDeviceCode(sdaPin = sdaNum, sclPin = sclNum, frameDelayMs = delayNum)
        }

        AlertDialog(
            onDismissRequest = { showCodeDialog = false },
            title = { Text("MicroPython main.py Code", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        text = "Source: ${if (githubCachedScript.isNotBlank()) "winpritam/pico-case (GitHub)" else "Local Template"}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "esptool: esptool.py --chip esp32 --port COM3 write_flash -z 0x1000 firmware.bin",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).padding(6.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = codeToDisplay,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .background(Color(0xFF1D1B20), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("MicroPython main.py", codeToDisplay))
                        Toast.makeText(context, "Copied Python code to clipboard!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy Code")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCodeDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Wipe Anim Confirm Dialog
    if (showWipeAnimDialog) {
        AlertDialog(
            onDismissRequest = { showWipeAnimDialog = false },
            title = { Text("Wipe anims/ Directory?", fontWeight = FontWeight.Bold) },
            text = { Text("This will delete all animation (.bin) files inside the 'anims/' directory on your connected microcontroller. Do you wish to continue?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllAnimationsOnDevice()
                        showWipeAnimDialog = false
                        Toast.makeText(context, "Wipe command sent.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Wipe Files")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeAnimDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsRow(
    title: String,
    desc: String,
    icon: ImageVector,
    checked: Boolean,
    iconColor: Color,
    onCheckedChange: (Boolean) -> Unit
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
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = iconColor, modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = desc,
                    fontSize = 9.sp,
                    color = Color.Gray,
                    lineHeight = 11.sp,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}
