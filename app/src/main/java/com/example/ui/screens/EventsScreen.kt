package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.serial.PicoProtocol
import com.example.service.PicocaseNotificationListenerService
import com.example.ui.theme.M3PurplePrimary
import com.example.ui.theme.M3PurpleTertiary
import com.example.viewmodel.MainViewModel

@Composable
fun EventsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val mappings by viewModel.eventMappings.collectAsStateWithLifecycle()
    val deviceFiles by viewModel.filesList.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showMapSelectorForEvent by remember { mutableStateOf<String?>(null) }
    var isNotifAccessGranted by remember { mutableStateOf(PicocaseNotificationListenerService.isPermissionGranted(context)) }

    // Standard preset animation options in anims/
    val standardPresets = listOf(
        "anims/emo_happy.bin",
        "anims/alert_call.bin",
        "anims/alert_msg.bin",
        "anims/alert_charge.bin",
        "anims/alert_battery.bin",
        "anims/alert_missed.bin",
        "anims/emo_sleepy.bin",
        "anims/emo_excited.bin"
    )

    // Merge device files with standard presets
    val allSelectableAnimations = remember(deviceFiles) {
        val deviceList = deviceFiles.map {
            if (it.fileName.startsWith("anims/")) it.fileName else "anims/${it.fileName}"
        }
        (deviceList + standardPresets).distinct()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Notification Listener Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isNotifAccessGranted) Color(0xFF1B5E20).copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isNotifAccessGranted) Color(0xFF4CAF50).copy(alpha = 0.4f) else MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isNotifAccessGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = "Status",
                        tint = if (isNotifAccessGranted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isNotifAccessGranted) "Notification Listener Active" else "Notification Access Required",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isNotifAccessGranted) "WhatsApp, SMS, calls & music are forwarded to ESP32." else "Grant permission to mirror WhatsApp, SMS, and Calls.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!isNotifAccessGranted) {
                    Button(
                        onClick = {
                            PicocaseNotificationListenerService.openSettings(context)
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Grant", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Explanatory Panel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Event-Animation Mapping",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Select which anims/*.bin file is played on each phone trigger",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }

            IconButton(onClick = { viewModel.refreshFileList() }) {
                Icon(Icons.Default.Sync, contentDescription = "Sync", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Events List index table
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val eventKeys = listOf(
                EventRowData(PicoProtocol.EVENT_INCOMING_CALL, "Incoming Voice Call", "Alerts when phone rings (or WhatsApp call)", Icons.Default.Call, M3PurplePrimary),
                EventRowData(PicoProtocol.EVENT_INCOMING_SMS, "SMS / WhatsApp Message", "Alerts on incoming texts & chats", Icons.Default.Sms, M3PurpleTertiary),
                EventRowData(PicoProtocol.EVENT_CHARGING_STARTED, "Charging Plugged", "USB power cable connected", Icons.Default.ElectricBolt, Color(0xFFF57C00)),
                EventRowData(PicoProtocol.EVENT_MUSIC_PLAYING, "Music Playing State", "Active media streams (Spotify, YT Music)", Icons.Default.MusicNote, M3PurplePrimary),
                EventRowData(PicoProtocol.EVENT_BATTERY_LOW, "Battery Low Alert", "Phone battery drops below 15%", Icons.Default.BatteryAlert, Color(0xFFD32F2F)),
                EventRowData(PicoProtocol.EVENT_MISSED_CALL, "Missed Call Alert", "Plays missed call reminder animation", Icons.Default.PhoneMissed, Color(0xFF388E3C))
            )

            items(eventKeys) { event ->
                val activeMapping = mappings.find { it.eventKey == event.key }?.animationFile ?: "anims/emo_happy.bin"
                EventMappingRow(
                    data = event,
                    fileNameMapped = activeMapping,
                    onEditClick = {
                        showMapSelectorForEvent = event.key
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Simulation Triggers Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Test Simulation Triggers",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SimulateButton(
                        label = "Simulate SMS",
                        icon = Icons.Default.Sms,
                        color = M3PurpleTertiary,
                        modifier = Modifier.weight(1f)
                    ) {
                        viewModel.triggerSimulatedEvent(PicoProtocol.EVENT_INCOMING_SMS)
                        Toast.makeText(context, "Triggered SMS Alert", Toast.LENGTH_SHORT).show()
                    }

                    SimulateButton(
                        label = "Simulate Call",
                        icon = Icons.Default.Call,
                        color = M3PurplePrimary,
                        modifier = Modifier.weight(1f)
                    ) {
                        viewModel.triggerSimulatedEvent(PicoProtocol.EVENT_INCOMING_CALL)
                        Toast.makeText(context, "Triggered Call Alert", Toast.LENGTH_SHORT).show()
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SimulateButton(
                        label = "Charging",
                        icon = Icons.Default.ElectricBolt,
                        color = Color(0xFFF57C00),
                        modifier = Modifier.weight(1f)
                    ) {
                        viewModel.triggerSimulatedEvent(PicoProtocol.EVENT_CHARGING_STARTED)
                        Toast.makeText(context, "Triggered Charging Alert", Toast.LENGTH_SHORT).show()
                    }

                    SimulateButton(
                        label = "Battery Low",
                        icon = Icons.Default.BatteryAlert,
                        color = Color(0xFFD32F2F),
                        modifier = Modifier.weight(1f)
                    ) {
                        viewModel.triggerSimulatedEvent(PicoProtocol.EVENT_BATTERY_LOW)
                        Toast.makeText(context, "Triggered Low Battery Alert", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Modal Selection Dialog for Choosing Animation File
    if (showMapSelectorForEvent != null) {
        val eventKey = showMapSelectorForEvent!!
        val eventLabel = when (eventKey) {
            PicoProtocol.EVENT_INCOMING_CALL -> "Incoming Call"
            PicoProtocol.EVENT_INCOMING_SMS -> "SMS / WhatsApp Message"
            PicoProtocol.EVENT_CHARGING_STARTED -> "Charging Plugged"
            PicoProtocol.EVENT_MUSIC_PLAYING -> "Music Playing"
            PicoProtocol.EVENT_BATTERY_LOW -> "Battery Low"
            PicoProtocol.EVENT_MISSED_CALL -> "Missed Call"
            else -> eventKey
        }

        AlertDialog(
            onDismissRequest = { showMapSelectorForEvent = null },
            title = { Text("Map Animation for $eventLabel", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column {
                    Text(
                        text = "Choose an animation file from anims/ directory:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                    ) {
                        items(allSelectableAnimations) { animPath ->
                            val cleanName = if (animPath.startsWith("anims/")) animPath.substringAfter("anims/") else animPath
                            Card(
                                onClick = {
                                    viewModel.updateEventMapping(eventKey, animPath)
                                    showMapSelectorForEvent = null
                                    Toast.makeText(context, "Mapped $eventLabel -> $cleanName", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(cleanName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text(animPath, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Select", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMapSelectorForEvent = null }) {
                    Text("Close")
                }
            }
        )
    }
}

data class EventRowData(
    val key: String,
    val name: String,
    val desc: String,
    val icon: ImageVector,
    val brandingColor: Color
)

@Composable
fun EventMappingRow(
    data: EventRowData,
    fileNameMapped: String,
    onEditClick: () -> Unit
) {
    val cleanName = if (fileNameMapped.startsWith("anims/")) fileNameMapped.substringAfter("anims/") else fileNameMapped

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(14.dp)
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
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(data.brandingColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(data.icon, contentDescription = data.name, tint = data.brandingColor, modifier = Modifier.size(20.dp))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(text = data.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = data.desc, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Mapped animation pill button
            Button(
                onClick = onEditClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(cleanName.take(16), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

@Composable
fun SimulateButton(
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.15f)),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
    }
}
