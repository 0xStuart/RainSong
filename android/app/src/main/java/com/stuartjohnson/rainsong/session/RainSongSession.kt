package com.stuartjohnson.rainsong.session

import android.content.Context
import android.os.SystemClock
import com.stuartjohnson.rainsong.ai.AiServiceClient
import com.stuartjohnson.rainsong.ai.ComposeResult
import com.stuartjohnson.rainsong.ai.Arrangement
import com.stuartjohnson.rainsong.ai.DevicePrefs
import com.stuartjohnson.rainsong.ai.LanAiFinder
import com.stuartjohnson.rainsong.ai.RainPatternSummary
import com.stuartjohnson.rainsong.ai.toPatternSummary
import com.stuartjohnson.rainsong.audio.HitPlayer
import com.stuartjohnson.rainsong.audio.LoopPlayer
import com.stuartjohnson.rainsong.audio.PadPlayer
import com.stuartjohnson.rainsong.audio.Wav
import com.stuartjohnson.rainsong.ble.BleRainSensor
import com.stuartjohnson.rainsong.intensity.IntensitySnapshot
import com.stuartjohnson.rainsong.intensity.IntensityTracker
import com.stuartjohnson.rainsong.protocol.DropEvent
import com.stuartjohnson.rainsong.tcp.TcpDropClient
import com.stuartjohnson.rainsong.ui.Screen
import com.stuartjohnson.rainsong.ui.TransportKind
import com.stuartjohnson.rainsong.ui.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Process-scoped sensor + audio session. Lives in [com.stuartjohnson.rainsong.RainSongApp]
 * so a foreground service can keep rain listening after the activity is gone.
 */
class RainSongSession(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val intensity = IntensityTracker()
    private val prefs = DevicePrefs(app)
    private val ai = AiServiceClient()
    private var finder: LanAiFinder? = null

    private var hits: HitPlayer? = null
    private var pad: PadPlayer? = null
    private var loop: LoopPlayer? = null
    private var wantPlay = false
    private var reconnectJob: Job? = null
    private var loopJob: Job? = null
    private var rainWatchJob: Job? = null
    private var lastBleAddress: String? = null
    private var lastDropElapsed = 0L
    private var bedFadingOut = false

    private val _state = MutableStateFlow(
        UiState(
            dropVolume = prefs.dropVolume(),
            musicVolume = prefs.musicVolume(),
            aiOverrideUrl = prefs.aiUrl(),
            aiNote = if (prefs.aiUrl().isNotBlank()) {
                "Using ${prefs.aiUrl()}"
            } else {
                "Searching LAN for RainSong AI…"
            },
        ),
    )
    val state: StateFlow<UiState> = _state

    init {
        startAiFinder()
        startAiWatch()
    }

    val isPlaying: Boolean get() = wantPlay

    private val ble = BleRainSensor(
        context = app,
        onDrop = ::onDropArrived,
        onStatus = { frame -> _state.update { it.copy(status = frame) } },
        onConnection = { connected, message ->
            _state.update {
                it.copy(
                    connected = connected,
                    statusMessage = message,
                    reconnecting = !connected && wantPlay && it.transport == TransportKind.Ble,
                )
            }
            if (!connected && wantPlay && _state.value.transport == TransportKind.Ble) {
                scheduleReconnect()
            }
            if (connected) reconnectJob?.cancel()
        },
        onScanResult = { sensor ->
            _state.update { st ->
                val rest = st.scanned.filterNot { it.address == sensor.address }
                st.copy(scanned = (listOf(sensor) + rest).take(12))
            }
        },
    )

    private val tcp = TcpDropClient(
        onDrop = ::onDropArrived,
        onConnection = { connected, message ->
            _state.update {
                it.copy(
                    connected = connected,
                    statusMessage = message,
                    reconnecting = !connected && wantPlay && it.transport == TransportKind.Tcp,
                )
            }
            if (!connected && wantPlay && _state.value.transport == TransportKind.Tcp) {
                scheduleReconnect()
            }
            if (connected) reconnectJob?.cancel()
        },
    )

    private fun onDropArrived(event: DropEvent) {
        hits?.trigger(event.strength)
        lastDropElapsed = SystemClock.elapsedRealtime()
        if (bedFadingOut) {
            bedFadingOut = false
        }
        setBedRainGain(1f)
        val snap = intensity.onDrop(event)
        pad?.setIntensity(snap.dropsPerSecond)
        _state.update {
            it.copy(
                lastDrop = event,
                intensity = snap,
                dropCount = it.dropCount + 1,
                backingLoopNote = it.backingLoopNote.substringBefore(FADE_NOTE),
            )
        }
    }

    fun startScan() {
        _state.update { it.copy(scanned = emptyList(), statusMessage = "Scanning…") }
        ble.startScan()
    }

    fun connectBle(address: String) {
        enterPlay(TransportKind.Ble)
        lastBleAddress = address
        ble.connect(address)
        RainSongService.start(app)
    }

    fun connectTcp(host: String = _state.value.tcpHost, port: Int = _state.value.tcpPort) {
        enterPlay(TransportKind.Tcp, host, port)
        tcp.connect(host, port)
        RainSongService.start(app)
    }

    /** Play screen with clouds only — no BLE/TCP sensor. Tap clouds for drops. */
    fun startCloudDemo() {
        enterPlay(TransportKind.None)
        _state.update {
            it.copy(
                connected = true,
                statusMessage = "Demo · tap the clouds",
                reconnecting = false,
            )
        }
        RainSongService.start(app)
    }

    fun setTcpHost(host: String) {
        _state.update { it.copy(tcpHost = host) }
    }

    fun setAiUrl(value: String) {
        prefs.setAiUrl(value)
        val trimmed = value.trim()
        _state.update { it.copy(aiOverrideUrl = trimmed) }
        scope.launch(Dispatchers.IO) {
            val base = AiServiceClient.normalizeBase(trimmed)
            val note = when {
                base == null -> {
                    finder?.rediscover()
                    "Searching LAN for RainSong AI…"
                }
                ai.health(base) -> "Override reachable · $base"
                else -> "Override set · $base (not answering yet)"
            }
            _state.update { it.copy(aiNote = note, aiOverrideUrl = trimmed) }
        }
    }

    fun setDropVolume(value: Float) {
        val v = value.coerceIn(0f, 1f)
        prefs.setDropVolume(v)
        hits?.setUserGain(v)
        _state.update { it.copy(dropVolume = v) }
    }

    fun setMusicVolume(value: Float) {
        val v = value.coerceIn(0f, 1f)
        prefs.setMusicVolume(v)
        pad?.setUserGain(v)
        loop?.setUserGain(v)
        _state.update { it.copy(musicVolume = v) }
    }

    fun simulateCloudDrop() {
        ensureAudio()
        applyUserGains()
        val strength = 8_000 + Random.nextInt(50_000)
        val liveBle = _state.value.connected && _state.value.transport == TransportKind.Ble
        if (liveBle) {
            hits?.trigger(strength)
            lastDropElapsed = SystemClock.elapsedRealtime()
            if (bedFadingOut) bedFadingOut = false
            setBedRainGain(1f)
            _state.update {
                val visual = (it.lastDrop ?: DropEvent(0, strength, 0, 0)).copy(strength = strength)
                it.copy(
                    lastDrop = visual,
                    dropCount = it.dropCount + 1,
                    backingLoopNote = it.backingLoopNote.substringBefore(FADE_NOTE),
                )
            }
        } else {
            val last = _state.value.lastDrop
            val tUs = if (last != null) {
                (last.tUs + 20_000L) and 0xFFFF_FFFFL
            } else {
                (SystemClock.elapsedRealtimeNanos() / 1_000L) and 0xFFFF_FFFFL
            }
            val seq = ((last?.seq ?: 255) + 1) and 0xFF
            onDropArrived(DropEvent(tUs, strength, seq, 0))
        }
    }

    fun disconnectToScan() {
        wantPlay = false
        reconnectJob?.cancel()
        loopJob?.cancel()
        rainWatchJob?.cancel()
        ble.disconnect()
        tcp.disconnect()
        releaseAudio()
        RainSongService.stop(app)
        _state.update {
            it.copy(
                screen = Screen.Scan,
                transport = TransportKind.None,
                connected = false,
                reconnecting = false,
                statusMessage = null,
            )
        }
    }

    private fun enterPlay(kind: TransportKind, host: String? = null, port: Int? = null) {
        wantPlay = true
        intensity.reset()
        lastDropElapsed = 0L
        bedFadingOut = false
        ensureAudio()
        setBedRainGain(0f)
        _state.update {
            it.copy(
                screen = Screen.Play,
                transport = kind,
                tcpHost = host ?: it.tcpHost,
                tcpPort = port ?: it.tcpPort,
                connected = false,
                lastDrop = null,
                status = null,
                dropCount = 0,
                intensity = IntensitySnapshot(0, 0, 0f, 0f, null),
                statusMessage = if (kind == TransportKind.Tcp) {
                    "TCP ${host ?: it.tcpHost}:${port ?: it.tcpPort}"
                } else {
                    "Connecting…"
                },
                arrangement = Arrangement.fromIntensity(RainPatternSummary(0f, 0f, 0)),
            )
        }
        startLoopScheduler()
        startRainWatch()
    }

    private fun ensureAudio() {
        if (hits == null) {
            hits = try {
                HitPlayer(app).also { it.start() }
            } catch (_: Exception) {
                null
            }
        }
        if (pad == null) {
            pad = try {
                PadPlayer(app).also { it.start() }
            } catch (_: Exception) {
                null
            }
        }
        if (loop == null) {
            loop = try {
                LoopPlayer(app).also { it.start() }
            } catch (_: Exception) {
                null
            }
        }
        applyUserGains()
    }

    private fun applyUserGains() {
        hits?.setUserGain(_state.value.dropVolume)
        pad?.setUserGain(_state.value.musicVolume)
        loop?.setUserGain(_state.value.musicVolume)
    }

    private fun releaseAudio() {
        hits?.release()
        hits = null
        pad?.release()
        pad = null
        loop?.release()
        loop = null
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            var wait = 500L
            while (isActive && wantPlay && !_state.value.connected) {
                delay(wait)
                if (!wantPlay || _state.value.connected) return@launch
                when (_state.value.transport) {
                    TransportKind.Ble -> lastBleAddress?.let { ble.connect(it) }
                    TransportKind.Tcp -> tcp.connect(_state.value.tcpHost, _state.value.tcpPort)
                    TransportKind.None -> return@launch
                }
                wait = (wait * 2).coerceAtMost(5_000L)
            }
        }
    }

    private fun setBedRainGain(amount: Float) {
        pad?.setRainGain(amount)
        loop?.setRainGain(amount)
    }

    private fun hudNote(base: String): String {
        val clean = base.substringBefore(FADE_NOTE)
        return if (bedFadingOut) clean + FADE_NOTE else clean
    }

    private fun startRainWatch() {
        rainWatchJob?.cancel()
        rainWatchJob = scope.launch {
            while (isActive && wantPlay) {
                delay(QUIET_POLL_MS)
                if (!wantPlay) return@launch
                val now = SystemClock.elapsedRealtime()
                val quiet = lastDropElapsed != 0L && now - lastDropElapsed >= QUIET_MS
                if (quiet && !bedFadingOut) {
                    bedFadingOut = true
                    setBedRainGain(0f)
                    pad?.setIntensity(0f)
                    val snap = intensity.markQuiet()
                    _state.update {
                        it.copy(
                            intensity = snap,
                            backingLoopNote = it.backingLoopNote.substringBefore(FADE_NOTE) + FADE_NOTE,
                        )
                    }
                }
            }
        }
    }

    private fun startLoopScheduler() {
        loopJob?.cancel()
        loopJob = scope.launch {
            var first = true
            while (isActive && wantPlay) {
                if (first) delay(2_500) else delay(45_000)
                first = false
                if (!wantPlay) return@launch
                if (bedFadingOut) continue
                val summary = intensity.snapshot().toPatternSummary()
                val base = aiBaseUrl()
                val local = Arrangement.fromIntensity(summary)
                pad?.apply(local)
                pad?.setIntensity(summary.dropsPerSecond)
                if (base == null) {
                    pad?.setMix(1f)
                    _state.update {
                        it.copy(
                            arrangement = local,
                            backingLoopNote = hudNote(
                                "No AI server · searching Wi-Fi…",
                            ),
                        )
                    }
                    if (_state.value.aiOverrideUrl.isBlank()) finder?.rediscover()
                    continue
                }
                _state.update {
                    it.copy(backingLoopNote = hudNote("Spark + MusicGen at $base…"))
                }
                var composed = withContext(Dispatchers.IO) { ai.compose(base, summary, 8) }
                if (composed == null && _state.value.aiOverrideUrl.isBlank()) {
                    _state.update {
                        it.copy(backingLoopNote = hudNote("AI unreachable · searching Wi-Fi…"))
                    }
                    finder?.rediscover()
                    delay(2_500)
                    val retry = aiBaseUrl()
                    if (retry != null) {
                        composed = withContext(Dispatchers.IO) { ai.compose(retry, summary, 8) }
                    }
                }
                if (composed != null) {
                    applyComposed(composed)
                } else {
                    pad?.setMix(1f)
                    _state.update {
                        it.copy(
                            arrangement = local,
                            backingLoopNote = hudNote("AI server unreachable · searching LAN"),
                        )
                    }
                    if (_state.value.aiOverrideUrl.isBlank()) finder?.rediscover()
                }
            }
        }
    }

    private fun applyComposed(composed: ComposeResult) {
        pad?.apply(composed.arrangement)
        try {
            loop?.play(Wav.parse(composed.wav))
            pad?.setMix(0.18f)
            val src = if (composed.arrangement.source == Arrangement.Source.MuseSpark) {
                "Muse Spark"
            } else {
                "local score"
            }
            _state.update {
                it.copy(
                    arrangement = composed.arrangement,
                    backingLoopNote = hudNote("$src · ${composed.arrangement.title} · playing"),
                )
            }
        } catch (_: Exception) {
            pad?.setMix(1f)
            _state.update {
                it.copy(
                    arrangement = composed.arrangement,
                    backingLoopNote = hudNote("MusicGen wav failed"),
                )
            }
        }
    }

    private fun startAiWatch() {
        scope.launch {
            while (isActive) {
                delay(5_000)
                if (_state.value.aiOverrideUrl.isNotBlank()) continue
                val url = _state.value.aiFoundUrl
                val ok = url != null && withContext(Dispatchers.IO) { ai.health(url) }
                when {
                    !ok -> {
                        _state.update { it.copy(aiFoundUrl = null, aiNote = "Searching LAN for RainSong AI…") }
                        finder?.rediscover()
                    }
                    else -> {
                        val live = url ?: continue
                        if (LanAiFinder.isLoopback(live)) finder?.seekLanUpgrade()
                    }
                }
            }
        }
    }

    private fun aiBaseUrl(): String? {
        val override = AiServiceClient.normalizeBase(_state.value.aiOverrideUrl)
        if (override != null) return override
        return _state.value.aiFoundUrl
    }

    private fun startAiFinder() {
        finder?.stop()
        finder = LanAiFinder(app) { url, note ->
            scope.launch {
                _state.update { st ->
                    val display = if (st.aiOverrideUrl.isNotBlank()) st.aiNote else note
                    val keepLan = st.aiFoundUrl?.takeIf { !LanAiFinder.isLoopback(it) }
                    st.copy(aiFoundUrl = url ?: keepLan, aiNote = display)
                }
            }
        }.also { it.start() }
    }

    fun close() {
        finder?.stop()
        disconnectToScan()
        scope.cancel()
    }

    companion object {
        private const val QUIET_MS = 2_000L
        private const val QUIET_POLL_MS = 250L
        private const val FADE_NOTE = " · rain stopped, fading"
    }
}
