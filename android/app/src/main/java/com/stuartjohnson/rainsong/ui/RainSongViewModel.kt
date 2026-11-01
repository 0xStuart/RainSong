package com.stuartjohnson.rainsong.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import com.stuartjohnson.rainsong.RainSongApp
import com.stuartjohnson.rainsong.ai.Arrangement
import com.stuartjohnson.rainsong.ble.ScannedSensor
import com.stuartjohnson.rainsong.intensity.IntensitySnapshot
import com.stuartjohnson.rainsong.protocol.DropEvent
import com.stuartjohnson.rainsong.protocol.RainSongUuids
import com.stuartjohnson.rainsong.protocol.StatusFrame
import kotlinx.coroutines.flow.StateFlow

enum class Screen { Scan, Play }

enum class TransportKind { None, Ble, Tcp }

data class UiState(
    val screen: Screen = Screen.Scan,
    val transport: TransportKind = TransportKind.None,
    val connected: Boolean = false,
    val statusMessage: String? = null,
    val scanned: List<ScannedSensor> = emptyList(),
    val tcpHost: String = defaultTcpHost(),
    val tcpPort: Int = RainSongUuids.TCP_PORT,
    val lastDrop: DropEvent? = null,
    val status: StatusFrame? = null,
    val intensity: IntensitySnapshot = IntensitySnapshot(0, 0, 0f, 0f, null),
    val dropCount: Int = 0,
    val reconnecting: Boolean = false,
    val backingLoopNote: String = "Muse Spark scores the rain. MusicGen plays the loop.",
    val arrangement: Arrangement? = null,
    val dropVolume: Float = 0.90f,
    val musicVolume: Float = 0.70f,
    val aiOverrideUrl: String = "",
    val aiFoundUrl: String? = null,
    val aiNote: String = "Searching LAN for RainSong AI…",
)

class RainSongViewModel(app: Application) : AndroidViewModel(app) {
    private val session = (app as RainSongApp).session
    val state: StateFlow<UiState> = session.state

    fun startScan() = session.startScan()
    fun connectBle(address: String) = session.connectBle(address)
    fun setAiUrl(value: String) = session.setAiUrl(value)
    fun setDropVolume(value: Float) = session.setDropVolume(value)
    fun setMusicVolume(value: Float) = session.setMusicVolume(value)
    fun disconnectToScan() = session.disconnectToScan()
    fun simulateCloudDrop() = session.simulateCloudDrop()
}

fun defaultTcpHost(): String {
    val fingerprint = Build.FINGERPRINT
    val product = Build.PRODUCT
    val model = Build.MODEL
    val hardware = Build.HARDWARE
    val emulator = fingerprint.startsWith("generic") ||
        fingerprint.contains("emulator") ||
        product.contains("sdk") ||
        model.contains("Emulator") ||
        model.contains("Android SDK") ||
        hardware.contains("goldfish") ||
        hardware.contains("ranchu")
    return if (emulator) RainSongUuids.EMULATOR_TCP_HOST else RainSongUuids.LOOPBACK_TCP_HOST
}
