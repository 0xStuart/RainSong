package com.stuartjohnson.rainsong.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import com.stuartjohnson.rainsong.ai.Arrangement
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.sin

/**
 * Quiet looping pad under the live drop hits. Parameters come from Muse Spark
 * (or the intensity fallback). Never sits on the hit path.
 */
class PadPlayer(context: Context) {
    private val sampleRate: Int
    private val writeFrames: Int
    private val track: AudioTrack
    private val running = AtomicBoolean(false)
    private val rootMilliHz = AtomicInteger(220_000)
    private val brightnessQ15 = AtomicInteger((0.4f * Q15).toInt())
    private val densityQ15 = AtomicInteger((0.3f * Q15).toInt())
    private val intensityQ15 = AtomicInteger(0)
    private val mixQ15 = AtomicInteger(Q15)
    private val rainGainQ15 = AtomicInteger(0)
    private val userGainQ15 = AtomicInteger((0.70f * Q15).toInt())
    private var thread: Thread? = null

    init {
        val am = context.getSystemService(AudioManager::class.java)
        sampleRate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            ?.toIntOrNull()
            ?: 48_000
        writeFrames = (am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
            ?.toIntOrNull() ?: 240).coerceAtLeast(128) * 4
        track = buildTrack(sampleRate, writeFrames)
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            running.set(false)
            return
        }
        track.play()
        thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val mix = ShortArray(writeFrames)
            var p1 = 0.0
            var p2 = 0.0
            var p3 = 0.0
            var t = 0.0
            var rainGain = 0.0
            val twoPi = PI * 2.0
            while (running.get()) {
                val root = rootMilliHz.get() / 1000.0
                val bright = brightnessQ15.get() / Q15.toDouble()
                val dens = densityQ15.get() / Q15.toDouble()
                val rain = intensityQ15.get() / Q15.toDouble()
                val mixAmt = mixQ15.get() / Q15.toDouble()
                rainGain = AudioFade.slew(rainGain, rainGainQ15.get() / Q15.toDouble(), writeFrames, sampleRate)
                val user = userGainQ15.get() / Q15.toDouble()
                val gain = (0.04 + 0.10 * dens * (0.35 + 0.65 * rain)) * mixAmt * rainGain * user
                for (i in mix.indices) {
                    val f1 = root
                    val f2 = root * 1.5
                    val f3 = root * (2.0 + 0.5 * bright)
                    p1 += twoPi * f1 / sampleRate
                    p2 += twoPi * f2 / sampleRate
                    p3 += twoPi * f3 / sampleRate
                    t += 1.0 / sampleRate
                    val trem = 0.85 + 0.15 * sin(twoPi * (0.15 + dens) * t)
                    val s = (sin(p1) * 0.55 + sin(p2) * 0.30 + sin(p3) * 0.15 * bright) * gain * trem
                    mix[i] = (s * 32767.0)
                        .toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort()
                }
                track.write(mix, 0, mix.size, AudioTrack.WRITE_BLOCKING)
            }
        }, "rainsong-pad").also { it.start() }
    }

    fun apply(arrangement: Arrangement) {
        rootMilliHz.set((arrangement.rootHz * 1000).toInt().coerceIn(80_000, 880_000))
        brightnessQ15.set((arrangement.brightness * Q15).toInt().coerceIn(0, Q15))
        densityQ15.set((arrangement.density * Q15).toInt().coerceIn(0, Q15))
    }

    fun setIntensity(dropsPerSecond: Float) {
        val n = (dropsPerSecond / 12f).coerceIn(0f, 1f)
        intensityQ15.set((n * Q15).toInt())
    }

    fun setMix(amount: Float) {
        mixQ15.set((amount.coerceIn(0f, 1f) * Q15).toInt())
    }

    /** Target 0..1. Fades out over ~3.5 s, in over ~1 s. */
    fun setRainGain(amount: Float) {
        rainGainQ15.set((amount.coerceIn(0f, 1f) * Q15).toInt())
    }

    fun setUserGain(amount: Float) {
        userGainQ15.set((amount.coerceIn(0f, 1f) * Q15).toInt())
    }

    fun release() {
        running.set(false)
        thread?.interrupt()
        thread = null
        try {
            track.pause()
            track.flush()
            track.stop()
        } catch (_: IllegalStateException) {
        }
        track.release()
    }

    companion object {
        private const val Q15 = 32767

        private fun buildTrack(sampleRate: Int, frames: Int): AudioTrack {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val bytes = (frames * 2).coerceAtLeast(
                AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                ),
            )
            return AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_NONE)
                .build()
        }
    }
}
