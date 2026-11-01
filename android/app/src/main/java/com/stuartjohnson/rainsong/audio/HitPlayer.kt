package com.stuartjohnson.rainsong.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Low-latency per-drop wind chime via AAudio ([AudioTrack.PERFORMANCE_MODE_LOW_LATENCY]).
 *
 * The output stream runs continuously. [trigger] only starts a voice —
 * no allocation, no decode, no wait on `t_us`. Call this on the BLE/TCP thread
 * the instant a drop frame arrives. Voices overlap so a burst of drops rings
 * like a set of tubes, not a single retriggered sample.
 */
class HitPlayer(context: Context) {
    private val sampleRate: Int
    private val writeFrames: Int
    private val tubes: Array<ShortArray>
    private val track: AudioTrack
    private val running = AtomicBoolean(false)
    private val pendingLock = Any()
    private val pendingTube = IntArray(PENDING)
    private val pendingGain = IntArray(PENDING)
    private var pendingCount = 0
    private val userGainQ15 = AtomicInteger(Q15)
    private var thread: Thread? = null

    init {
        val am = context.getSystemService(AudioManager::class.java)
        sampleRate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            ?.toIntOrNull()
            ?: 48_000
        writeFrames = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
            ?.toIntOrNull()
            ?: 240
        tubes = TUBE_HZ.map { hz -> synthesizeChime(sampleRate, hz) }.toTypedArray()
        track = buildTrack(sampleRate, writeFrames)
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        if (track.state != AudioTrack.STATE_INITIALIZED) return
        track.play()
        thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val frames = writeFrames.coerceAtLeast(64)
            val mix = ShortArray(frames)
            val voicePos = IntArray(VOICES) { Int.MAX_VALUE }
            val voiceTube = IntArray(VOICES)
            val voiceGain = IntArray(VOICES)
            val startTube = IntArray(PENDING)
            val startGain = IntArray(PENDING)
            while (running.get()) {
                var nStart = 0
                synchronized(pendingLock) {
                    if (pendingCount > 0) {
                        System.arraycopy(pendingTube, 0, startTube, 0, pendingCount)
                        System.arraycopy(pendingGain, 0, startGain, 0, pendingCount)
                        nStart = pendingCount
                        pendingCount = 0
                    }
                }
                for (s in 0 until nStart) {
                    startVoice(voicePos, voiceTube, voiceGain, startTube[s], startGain[s])
                }
                val userGain = userGainQ15.get()
                for (i in 0 until frames) {
                    var acc = 0
                    for (v in 0 until VOICES) {
                        val pos = voicePos[v]
                        val tube = tubes[voiceTube[v]]
                        if (pos < tube.size) {
                            acc += (tube[pos].toInt() * voiceGain[v]) shr 15
                            voicePos[v] = pos + 1
                        }
                    }
                    acc = (acc * userGain) shr 15
                    mix[i] = acc.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                track.write(mix, 0, frames, AudioTrack.WRITE_BLOCKING)
            }
        }, "rainsong-hit").also { it.start() }
    }

    /**
     * Fire a chime immediately. [strength] is the protocol u16 (0..65535).
     */
    fun trigger(strength: Int) {
        val clamped = strength.coerceIn(0, 65535)
        val g = 0.22f + 0.78f * (clamped / 65535f)
        val hashed = (clamped * 1_103_515_245 + 12_345) ushr 1
        val tube = hashed % tubes.size
        val gainQ15 = (g * Q15).toInt().coerceIn(1, Q15)
        synchronized(pendingLock) {
            if (pendingCount < PENDING) {
                pendingTube[pendingCount] = tube
                pendingGain[pendingCount] = gainQ15
                pendingCount++
            } else {
                pendingTube[PENDING - 1] = tube
                pendingGain[PENDING - 1] = gainQ15
            }
        }
    }

    fun setUserGain(amount: Float) {
        userGainQ15.set((amount.coerceIn(0f, 1f) * Q15).toInt())
    }

    fun release() {
        running.set(false)
        thread?.interrupt()
        thread = null
        try {
            track.stop()
        } catch (_: IllegalStateException) {
        }
        track.release()
    }

    private fun startVoice(
        voicePos: IntArray,
        voiceTube: IntArray,
        voiceGain: IntArray,
        tube: Int,
        gainQ15: Int,
    ) {
        var steal = 0
        var oldest = -1
        for (v in voicePos.indices) {
            val tubeIdx = voiceTube[v]
            val len = tubes[tubeIdx].size
            if (voicePos[v] >= len) {
                steal = v
                oldest = Int.MAX_VALUE
                break
            }
            if (voicePos[v] > oldest) {
                oldest = voicePos[v]
                steal = v
            }
        }
        voiceTube[steal] = tube.coerceIn(0, tubes.lastIndex)
        voiceGain[steal] = gainQ15
        voicePos[steal] = 0
    }

    companion object {
        private const val Q15 = 32767
        private const val VOICES = 8
        private const val PENDING = 16

        /** Pentatonic tubes, high to low — light drops tend to hash across the set. */
        internal val TUBE_HZ = doubleArrayOf(1046.5, 880.0, 783.99, 659.25, 587.33, 523.25)

        private fun buildTrack(sampleRate: Int, nativeFrames: Int): AudioTrack {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val minBytes = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val nativeBytes = (nativeFrames * 2 /* bytes/sample */ * 2 /* double buffer */)
                .coerceAtLeast(minBytes)
            return tryBuild(attrs, format, nativeBytes, AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                ?: tryBuild(attrs, format, minBytes, AudioTrack.PERFORMANCE_MODE_NONE)
                ?: error("AudioTrack failed to initialize")
        }

        private fun tryBuild(
            attrs: AudioAttributes,
            format: AudioFormat,
            bufferBytes: Int,
            performance: Int,
        ): AudioTrack? {
            val track = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(performance)
                .build()
            return if (track.state == AudioTrack.STATE_INITIALIZED) {
                track
            } else {
                track.release()
                null
            }
        }

        /**
         * Metallic tube: inharmonic bar partials + a short mallet tick, ~1.8 s decay.
         * Free-free bar ratios 1 : 2.756 : 5.404 : 8.933.
         */
        internal fun synthesizeChime(sampleRate: Int, fundamentalHz: Double): ShortArray {
            val n = (sampleRate * 1.80).toInt().coerceAtLeast(64)
            val out = ShortArray(n)
            val twoPi = PI * 2.0
            val decay = 1.55 + 400.0 / fundamentalHz
            var p1 = 0.0
            var p2 = 0.0
            var p3 = 0.0
            var p4 = 0.0
            for (i in 0 until n) {
                val t = i / sampleRate.toDouble()
                val attack = (1.0 - exp(-t * 280.0)).coerceAtMost(1.0)
                val env = attack * exp(-t * decay)
                p1 += twoPi * fundamentalHz / sampleRate
                p2 += twoPi * fundamentalHz * 2.756 / sampleRate
                p3 += twoPi * fundamentalHz * 5.404 / sampleRate
                p4 += twoPi * fundamentalHz * 8.933 / sampleRate
                val bar = sin(p1) * exp(-t * (decay * 0.85)) +
                    sin(p2) * 0.42 * exp(-t * (decay * 1.35)) +
                    sin(p3) * 0.16 * exp(-t * (decay * 2.10)) +
                    sin(p4) * 0.07 * exp(-t * (decay * 3.20))
                val tick = pseudoNoise(i) * exp(-t * 70.0) * 0.18
                val sample = (bar * 0.62 + tick) * env
                out[i] = (sample * 32767.0)
                    .toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
            return out
        }

        private fun pseudoNoise(i: Int): Double {
            var x = (i + 1) * 1103515245 + 12345
            x = (x ushr 16) xor x
            return ((x and 0xFFFF) / 32768.0) - 1.0
        }
    }
}
