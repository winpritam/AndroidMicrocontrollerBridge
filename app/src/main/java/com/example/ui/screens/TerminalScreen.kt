package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.SerialLogEntry
import com.example.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TerminalScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val logs by viewModel.logsList.collectAsStateWithLifecycle()
    val connectedInfo by viewModel.connectedDeviceInfo.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var inputCommand by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Headers and exports row
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MicroPython Serial Terminal",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = connectedInfo,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    onClick = {
                        if (logs.isEmpty()) {
                            Toast.makeText(context, "Terminal is already empty", Toast.LENGTH_SHORT).show()
                            return@IconButton
                        }
                        val shareText = logs.reversed().joinToString("\n") { log ->
                            val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(log.timestamp))
                            "[${log.direction}] $timeStr - ${log.message}"
                        }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("MicroPython Logs", shareText)
                        clipboard.setPrimaryClip(clip)

                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "Export Serial Logs")
                        context.startActivity(shareIntent)
                    },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Export raw log")
                }

                IconButton(
                    onClick = { viewModel.clearLogHistory() },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear logs", tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        // Terminal Console Body Container
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 6.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1D1B20)
            )
        ) {
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "TERMINAL READY\nType commands or use MicroPython REPL shortcuts below...",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFF62EFE4).copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    reverseLayout = true
                ) {
                    items(logs) { log ->
                        TerminalLine(log)
                    }
                }
            }
        }

        // MicroPython Hardware Action Toolbar
        Text(
            text = "MicroPython REPL & Hardware Actions:",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp, bottom = 4.dp, top = 2.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ActionChip(label = "Ctrl+C (Break)", color = MaterialTheme.colorScheme.error) {
                viewModel.sendCtrlC()
            }
            ActionChip(label = "Ctrl+D (Reset)", color = MaterialTheme.colorScheme.tertiary) {
                viewModel.sendCtrlD()
            }
            ActionChip(label = "List anims/") {
                viewModel.sendManualCommand("import os; print(os.listdir('anims'))")
            }
            ActionChip(label = "Scan I2C (21,22)") {
                viewModel.sendManualCommand("from machine import Pin, I2C; print('I2C:', I2C(0, scl=Pin(22), sda=Pin(21)).scan())")
            }
            ActionChip(label = "Free RAM") {
                viewModel.sendManualCommand("import gc; print('Free RAM:', gc.mem_free())")
            }
        }

        // Command Quick Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val chips = listOf("PING", "START", "STOP", "EMO_HAPPY", "EMO_SLEEPY", "EMO_EXCITED", "LIST_FILES")
            chips.forEach { chipName ->
                SuggestionChip(
                    onClick = { inputCommand = chipName },
                    label = { Text(chipName, fontSize = 9.sp, fontWeight = FontWeight.SemiBold) },
                    shape = RoundedCornerShape(8.dp),
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        // Manual Command Send Form
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputCommand,
                onValueChange = { inputCommand = it },
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                placeholder = { Text("Command (e.g. EMO_HAPPY or python)", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            Button(
                onClick = {
                    if (inputCommand.isNotBlank()) {
                        viewModel.sendManualCommand(inputCommand)
                        inputCommand = ""
                    }
                },
                modifier = Modifier.height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send ASCII CMD")
            }
        }
    }
}

@Composable
fun ActionChip(
    label: String,
    color: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        modifier = Modifier.height(28.dp)
    ) {
        Box(modifier = Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun TerminalLine(log: SerialLogEntry) {
    val formatter = SimpleDateFormat("HH:mm:ss.S", Locale.getDefault())
    val timestampStr = formatter.format(Date(log.timestamp))

    val (color, prefix) = when (log.direction) {
        "TX" -> Pair(Color(0xFFD0BCFF), ">> TX: ")
        "RX" -> Pair(Color(0xFF62EFE4), "<< RX: ")
        else -> Pair(Color(0xFFFFD166), "SYS:  ")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "[$timestampStr] ",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = Color.Gray
        )
        Text(
            text = prefix,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = log.message,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = if (log.direction == "TX") Color(0xFFD0BCFF) else if (log.direction == "RX") Color(0xFFE6E1E5) else Color.White.copy(alpha = 0.85f),
            modifier = Modifier.weight(1f)
        )
    }
}
