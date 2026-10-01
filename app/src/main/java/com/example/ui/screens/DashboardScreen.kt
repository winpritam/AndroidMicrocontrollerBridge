package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.SerialLogEntry
import com.example.serial.ConnectionState
import com.example.serial.PicoProtocol
import com.example.viewmodel.MainViewModel

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val connectedInfo by viewModel.connectedDeviceInfo.collectAsStateWithLifecycle()
    val availableDevices by viewModel.availableDevices.collectAsStateWithLifecycle()
    val logs by viewModel.logsList.collectAsStateWithLifecycle()

    // Switch states
    val notifications by viewModel.notificationEnabled.collectAsStateWithLifecycle()
    val miniMsg by viewModel.miniMessageEnabled.collectAsStateWithLifecycle()
    val wakeShake by viewModel.wakeOnShakeEnabled.collectAsStateWithLifecycle()
    val sleepMode by viewModel.sleepModeEnabled.collectAsStateWithLifecycle()
    val randomAnim by viewModel.randomAnimationEnabled.collectAsStateWithLifecycle()

    var showDevicePicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        // 1. Hero Connection Status Card
        ConnectionHeader(
            state = connectionState,
            deviceInfo = connectedInfo,
            deviceCount = availableDevices.size,
            onScanClick = { viewModel.scanUsbHardware() },
            onDevicePickerClick = { showDevicePicker = true }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Quick OLED Emotion Controls Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "OLED Quick Controls",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "128x64 SSD1306",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = { viewModel.sendManualCommand(PicoProtocol.CMD_EMO_HAPPY) },
                modifier = Modifier.weight(1f).height(38.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Icon(Icons.Default.SentimentVerySatisfied, contentDescription = "Happy", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Happy", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }

            Button(
                onClick = { viewModel.sendManualCommand(PicoProtocol.CMD_EMO_SLEEPY) },
                modifier = Modifier.weight(1f).height(38.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Icon(Icons.Default.Bedtime, contentDescription = "Sleepy", tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Sleepy", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }

            Button(
                onClick = { viewModel.sendManualCommand(PicoProtocol.CMD_EMO_EXCITED) },
                modifier = Modifier.weight(1f).height(38.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Icon(Icons.Default.Celebration, contentDescription = "Excited", tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Excited", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }

            Button(
                onClick = { viewModel.sendManualCommand(PicoProtocol.CMD_STOP) },
                modifier = Modifier.weight(0.8f).height(38.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Icon(Icons.Default.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text("Stop", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Companion Behaviors",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
        )

        // 2. Behavior Grid Config Options
        val cards = listOf(
            BehaviorCardData(
                title = "Notifications",
                desc = if (notifications) "Forwarding SMS, WhatsApp & Calls" else "Notification bridge paused",
                icon = Icons.Default.Notifications,
                checked = notifications,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onCheckedChange = { viewModel.toggleNotification(it) }
            ),
            BehaviorCardData(
                title = "Sleep Mode",
                desc = if (sleepMode) "Character sleeping (EMO_SLEEPY)" else "Character awake & active",
                icon = Icons.Default.Bedtime,
                checked = sleepMode,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onCheckedChange = { viewModel.toggleSleepMode(it) }
            ),
            BehaviorCardData(
                title = "Wake on Shake",
                desc = if (wakeShake) "Armed: Shake phone to wake!" else "Sensor motion wake paused",
                icon = Icons.Default.Vibration,
                checked = wakeShake,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onCheckedChange = { viewModel.toggleWakeOnShake(it) }
            ),
            BehaviorCardData(
                title = "Randomize",
                desc = if (randomAnim) "Cycling anims/ every 10s" else "Manual animation loop",
                icon = Icons.Default.Shuffle,
                checked = randomAnim,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onCheckedChange = { viewModel.toggleRandomAnimation(it) }
            )
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(cards) { card ->
                BehaviorCard(card)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3. Quick Terminal Preview Panel
        QuickTerminalPreview(logs)
    }

    if (showDevicePicker) {
        AlertDialog(
            onDismissRequest = { showDevicePicker = false },
            title = { Text("Connected USB Devices (${availableDevices.size})", fontWeight = FontWeight.Bold) },
            text = {
                if (availableDevices.isEmpty()) {
                    Text("No USB devices detected. Connect your ESP32 or Pico board using a USB-OTG adapter.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableDevices.forEach { dev ->
                            Card(
                                onClick = {
                                    viewModel.selectUsbDevice(dev.device)
                                    showDevicePicker = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(dev.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Chip: ${dev.chipType} | VID: 0x${Integer.toHexString(dev.device.vendorId)} PID: 0x${Integer.toHexString(dev.device.productId)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDevicePicker = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun ConnectionHeader(
    state: ConnectionState,
    deviceInfo: String,
    deviceCount: Int,
    onScanClick: () -> Unit,
    onDevicePickerClick: () -> Unit
) {
    val (statusLabel, statusText, statusColor) = when (state) {
        ConnectionState.CONNECTED -> Triple("Hardware Link", "Connected", Color(0xFF62EFE4))
        ConnectionState.CONNECTING -> Triple("Hardware Link", "Connecting...", Color(0xFFFFD166))
        ConnectionState.BUSY -> Triple("Hardware Link", "Transferring...", Color(0xFFB08CFF))
        ConnectionState.ERROR -> Triple("Hardware Link", "Link Error", Color(0xFFFF6D80))
        ConnectionState.OFFLINE -> Triple("Hardware Link", "Disconnected", Color(0xFF79747E))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = statusLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = statusText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (state == ConnectionState.CONNECTED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = deviceInfo,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 1
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (deviceCount > 0) {
                        IconButton(
                            onClick = onDevicePickerClick,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Usb,
                                contentDescription = "USB Devices",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onScanClick,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Scan link",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progression indicator
            val progressWidth = when (state) {
                ConnectionState.CONNECTED -> 1f
                ConnectionState.OFFLINE -> 0f
                else -> 0.66f
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressWidth)
                        .fillMaxHeight()
                        .background(statusColor)
                )
            }
        }
    }
}

data class BehaviorCardData(
    val title: String,
    val desc: String,
    val icon: ImageVector,
    val checked: Boolean,
    val containerColor: Color,
    val iconColor: Color,
    val onCheckedChange: (Boolean) -> Unit
)

@Composable
fun BehaviorCard(card: BehaviorCardData) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(135.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(card.containerColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = card.icon,
                        contentDescription = card.title,
                        tint = card.iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Switch(
                    checked = card.checked,
                    onCheckedChange = card.onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.scale(0.82f)
                )
            }

            Column {
                Text(
                    text = card.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = card.desc,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    lineHeight = 12.sp
                )
            }
        }
    }
}

@Composable
fun QuickTerminalPreview(logs: List<SerialLogEntry>) {
    val newestLogs = remember(logs) {
        logs.take(2)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1D1B20)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            if (newestLogs.isEmpty()) {
                Text(
                    text = "> USB_READY: MicroPython serial online",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Color(0xFFD0BCFF)
                )
                Text(
                    text = "> WAITING_FOR_HARDWARE_PACKETS",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Color(0xFFE6E1E5)
                )
            } else {
                newestLogs.reversed().forEach { log ->
                    val color = if (log.direction == "TX") Color(0xFFD0BCFF) else Color(0xFFE6E1E5)
                    Text(
                        text = "> ${log.direction}: ${log.message}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = color,
                        maxLines = 1
                    )
                }
            }
            Text(
                text = "_",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
