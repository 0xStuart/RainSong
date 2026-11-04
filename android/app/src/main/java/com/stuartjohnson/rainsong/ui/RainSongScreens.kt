package com.stuartjohnson.rainsong.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stuartjohnson.rainsong.ble.ScannedSensor
import com.stuartjohnson.rainsong.protocol.StatusFrame

@Composable
fun ScanScreen(
    state: UiState,
    onScan: () -> Unit,
    onConnectBle: (String) -> Unit,
    onAiUrl: (String) -> Unit,
    onDropVolume: (Float) -> Unit,
    onMusicVolume: (Float) -> Unit,
    onCloudDemo: () -> Unit = {},
) {
    var permissionNote by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        val ok = blePermissions().all { granted[it] == true }
        permissionNote = if (ok) null else "Bluetooth permission needed to scan"
        if (ok) onScan()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("RainSong", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Raindrops become notes. Scan the sensor, then listen.",
            style = MaterialTheme.typography.bodyMedium,
        )
        AiUrlField(
            overrideUrl = state.aiOverrideUrl,
            note = state.aiNote,
            onAiUrl = onAiUrl,
        )
        VolumeSliders(
            dropVolume = state.dropVolume,
            musicVolume = state.musicVolume,
            onDropVolume = onDropVolume,
            onMusicVolume = onMusicVolume,
        )
        permissionNote?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = { permissionLauncher.launch(blePermissions()) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Scan for RainSong")
        }
        TextButton(
            onClick = onCloudDemo,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open clouds (no sensor)")
        }
        state.statusMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (state.scanned.isEmpty()) {
            Text("No sensors yet. Filter: service UUID RainSong.", style = MaterialTheme.typography.bodySmall)
        } else {
            state.scanned.forEach { sensor ->
                SensorRow(sensor, onConnect = { onConnectBle(sensor.address) })
            }
        }
    }
}

@Composable
private fun AiUrlField(
    overrideUrl: String,
    note: String,
    onAiUrl: (String) -> Unit,
) {
    val focus = LocalFocusManager.current
    var draft by remember(overrideUrl) { mutableStateOf(overrideUrl) }
    fun commit() {
        onAiUrl(draft.trim())
        focus.clearFocus()
    }
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        label = { Text("AI server URL (override)") },
        placeholder = { Text("blank = find on Wi-Fi") },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { fs ->
                if (!fs.isFocused && draft.trim() != overrideUrl) onAiUrl(draft.trim())
            },
        singleLine = true,
    )
    Text(note, style = MaterialTheme.typography.bodySmall)
    Text(
        "Leave blank to find Docker on this Wi-Fi. Paste a URL only for a hosted box.",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun VolumeSliders(
    dropVolume: Float,
    musicVolume: Float,
    onDropVolume: (Float) -> Unit,
    onMusicVolume: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        VolumeRow("Rain drops", dropVolume, onDropVolume)
        VolumeRow("Music", musicVolume, onMusicVolume)
    }
}

@Composable
private fun VolumeRow(label: String, value: Float, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SensorRow(sensor: ScannedSensor, onConnect: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(),
        onClick = onConnect,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(sensor.name, style = MaterialTheme.typography.titleMedium)
            Text("${sensor.address}  ·  ${sensor.rssi} dBm", style = MaterialTheme.typography.bodySmall)
            Text("Tap to connect", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun PlayScreen(
    state: UiState,
    onCloudTap: () -> Unit,
    onDropVolume: (Float) -> Unit,
    onMusicVolume: (Float) -> Unit,
    onDisconnect: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        RainSky(
            intensityDps = state.intensity.dropsPerSecond,
            lastDrop = state.lastDrop,
            dropCount = state.dropCount,
            connected = state.connected,
            onCloudTap = onCloudTap,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("RainSong", style = MaterialTheme.typography.headlineMedium)
                Text(connectionLine(state), style = MaterialTheme.typography.bodyMedium)
                HudChip(
                    listOf(
                        batteryLine(state.status, state.transport),
                        "%.1f /s".format(state.intensity.dropsPerSecond),
                        state.lastDrop?.let { "seq ${it.seq}" } ?: "waiting",
                    ).joinToString("  ·  "),
                )
            }
            Spacer(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(onCloudTap) {
                        detectTapGestures { onCloudTap() }
                    },
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val arr = state.arrangement
                if (arr != null) {
                    HudChip("${arr.title}  ·  ${arr.mood}")
                }
                Text(state.backingLoopNote, style = MaterialTheme.typography.bodySmall)
                Text("Tap the clouds to hear a drop", style = MaterialTheme.typography.bodySmall)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x99071318), RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    VolumeSliders(
                        dropVolume = state.dropVolume,
                        musicVolume = state.musicVolume,
                        onDropVolume = onDropVolume,
                        onMusicVolume = onMusicVolume,
                    )
                }
                TextButton(onClick = onDisconnect, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Disconnect")
                }
            }
        }
    }
}

@Composable
private fun HudChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .background(Color(0x99071318), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

private fun connectionLine(state: UiState): String {
    val kind = when (state.transport) {
        TransportKind.Ble -> "sensor"
        TransportKind.Tcp -> "TCP ${state.tcpHost}:${state.tcpPort}"
        TransportKind.None -> "demo"
    }
    return when {
        state.connected -> "Listening · $kind"
        state.reconnecting -> "Reconnecting · $kind"
        else -> (state.statusMessage ?: "Not connected") + " · $kind"
    }
}

private fun batteryLine(status: StatusFrame?, transport: TransportKind): String {
    if (transport == TransportKind.Tcp) return "TCP"
    val s = status ?: return "battery —"
    if (s.batteryUnknown) return "battery ?"
    return "%.2f V".format(s.batteryMv / 1000.0) + if (s.lowBatt) " LOW" else ""
}

fun blePermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
        )
    }
}

fun notificationPermission(): Array<String> {
    return if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyArray()
    }
}
