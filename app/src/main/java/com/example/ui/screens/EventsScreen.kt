package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.serial.PicoProtocol
import com.example.ui.theme.M3PurplePrimary
import com.example.ui.theme.M3PurpleTertiary
import com.example.viewmodel.MainViewModel

@Composable
fun EventsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val mappings by viewModel.eventMappings.collectAsStateWithLifecycle()
    val files by viewModel.filesList.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Modal Sheet helper custom dropdown controls
    var showMapSelectorForEvent by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Explanatory Panel
        Text(
            text = "Event-Animation Mapping",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Assign which animation file (.bin) to stream when an Android phone event is triggered in background.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Events List index table
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val eventKeys = listOf(
                EventRowData(PicoProtocol.EVENT_INCOMING_CALL, "Incoming Voice Call", "Alerts when phone lines ring", Icons.Default.Call, M3PurplePrimary),
                EventRowData(PicoProtocol.EVENT_INCOMING_SMS, "Incoming SMS Text", "SMS message banner overlay", Icons.Default.Sms, M3PurpleTertiary),
                EventRowData(PicoProtocol.EVENT_CHARGING_STARTED, "Charging Plugged", "USB power cable connected", Icons.Default.ElectricBolt, Color(0xFFF57C00)),
                EventRowData(PicoProtocol.EVENT_MUSIC_PLAYING, "Music Playing State", "Active media streams (happy face)", Icons.Default.MusicNote, M3PurplePrimary),
                EventRowData(PicoProtocol.EVENT_BATTERY_LOW, "Battery Low Alert", "Device drops below 15% charge", Icons.Default.BatteryAlert, Color(0xFFD32F2F)),
                EventRowData(PicoProtocol.EVENT_MISSED_CALL, "Missed Call Log", "Triggers character sadness", Icons.Default.PhoneMissed, Color(0xFF388E3C))
            )

            items(eventKeys) { event ->
                val activeMapping = mappings.find { it.eventKey == event.key }?.animationFile ?: "None"
                EventMappingRow(
                    data = event,
                    fileNameMapped = activeMapping,
                    onEditClick = {
                        showMapSelectorForEvent = event.key
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick simulation helper deck
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                .padding(14.dp)
        ) {
            Column {
                Text(
                    text = "Live Event Injector",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Test background triggers instantly. Emulates OS telemetry broadcasts:",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp)
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
                        Toast.makeText(context, "Simulated SMS Event", Toast.LENGTH_SHORT).show()
                    }

                    SimulateButton(
                        label = "Simulate Call",
                        icon = Icons.Default.Call,
                        color = M3PurplePrimary,
                        modifier = Modifier.weight(1f)
                    ) {
                        viewModel.triggerSimulatedEvent(PicoProtocol.EVENT_INCOMING_CALL)
                        Toast.makeText(context, "Simulated Call Event", Toast.LENGTH_SHORT).show()
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SimulateButton(
                        label = "Power Connected",
                        icon = Icons.Default.ElectricBolt,
                        color = Color(0xFFF57C00),
                        modifier = Modifier.weight(1f)
                    ) {
                        viewModel.triggerSimulatedEvent(PicoProtocol.EVENT_CHARGING_STARTED)
                        Toast.makeText(context, "Simulated Power Connected", Toast.LENGTH_SHORT).show()
                    }

                    SimulateButton(
                        label = "Battery Low",
                        icon = Icons.Default.BatteryAlert,
                        color = Color(0xFFD32F2F),
                        modifier = Modifier.weight(1f)
                    ) {
                        viewModel.triggerSimulatedEvent(PicoProtocol.EVENT_BATTERY_LOW)
                        Toast.makeText(context, "Simulated Low Battery Alert", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Modal Selection Dialog for Choosing File Mappings
    if (showMapSelectorForEvent != null) {
        val eventKey = showMapSelectorForEvent!!
        val eventLabel = when (eventKey) {
            PicoProtocol.EVENT_INCOMING_CALL -> "Incoming Voice Call"
            PicoProtocol.EVENT_INCOMING_SMS -> "Incoming SMS Text"
            PicoProtocol.EVENT_CHARGING_STARTED -> "Charging Plugged"
            PicoProtocol.EVENT_MUSIC_PLAYING -> "Music Playing State"
            PicoProtocol.EVENT_BATTERY_LOW -> "Battery Low Alert"
            PicoProtocol.EVENT_MISSED_CALL -> "Missed Call Log"
            else -> "System Event"
        }

        AlertDialog(
            onDismissRequest = { showMapSelectorForEvent = null },
            title = { Text("Map File for $eventLabel", fontWeight = FontWeight.Black, fontSize = 16.sp) },
            text = {
                Column {
                    Text("Select an animation from anims/ to load:", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 10.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 250.dp)
                    ) {
                        // Option for None/Cleared
                        item {
                            Card(
                                onClick = {
                                    viewModel.updateEventMapping(eventKey, "")
                                    showMapSelectorForEvent = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                            ) {
                                Text("Unmapped (No Action)", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        items(files) { file ->
                            Card(
                                onClick = {
                                    viewModel.updateEventMapping(eventKey, file.fileName)
                                    showMapSelectorForEvent = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(file.fileName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("${file.sizeBytes / 1024} KB", fontSize = 10.sp, color = Color.Gray)
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(data.brandingColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = data.icon,
                        contentDescription = data.name,
                        tint = data.brandingColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = data.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = data.desc,
                        fontSize = 9.sp,
                        color = Color.Gray,
                        lineHeight = 11.sp
                    )
                }
            }

            // Current mapping badge button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .clickable { onEditClick() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (fileNameMapped.isBlank() || fileNameMapped == "None") "Unmapped" else fileNameMapped,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Map option",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
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
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.height(38.dp),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
