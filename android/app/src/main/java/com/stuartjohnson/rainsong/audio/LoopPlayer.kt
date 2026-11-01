package com.stuartjohnson.rainsong.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Loops a MusicGen WAV under the live hits, with a short crossfade on replace. */
class LoopPlayer(context: Context) {
    val sampleRate: Int
    private val writeFrames: Int
    private val track: AudioTrack
    private val running = AtomicBoolean(false)
    private val current = AtomicReference<ShortArray?>(null)
    private val incoming = AtomicReference<ShortArray?>(null)
    private val rainGainQ15 = AtomicInteger(0)
    private val userGainQ15 = AtomicInteger((0.70f * 32767).toInt())
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
            var pos = 0
            var fade = 0
            var from: ShortArray? = null
            var to: ShortArray? = null
            var rainGain = 0.0
            val fadeLen = (sampleRate * 1.2).toInt().coerceAtLeast(1)
            while (running.get()) {
                incoming.getAndSet(null)?.let { next ->
                    from = current.get()
                    to = next
                    fade = 0
                    current.set(next)
                    pos = 0
                }
                rainGain = AudioFade.slew(
                    rainGain,
                    rainGainQ15.get() / 32767.0,
                    writeFrames,
                    sampleRate,
                )
                val cur = current.get()
                if (cur == null || cur.isEmpty()) {
                    java.util.Arrays.fill(mix, 0)
                    track.write(mix, 0, mix.size, AudioTrack.WRITE_BLOCKING)
                    continue
                }
                val old = from
                for (i in mix.indices) {
                    val n = cur[pos % cur.size].toInt()
                    val sample = if (old != null && old.isNotEmpty() && fade < fadeLen) {
                        val o = old[pos % old.size].toInt()
                        val t = fade / fadeLen.toFloat()
                        fade++
                        (o * (1f - t) + n * t).toInt()
                    } else {
                        n
                    }
                    val user = userGainQ15.get() / 32767.0
                    mix[i] = (sample * 0.55 * rainGain * user).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort()
                    pos++
                }
                track.write(mix, 0, mix.size, AudioTrack.WRITE_BLOCKING)
            }
        }, "rainsong-loop").also { it.start() }
    }

    fun play(loop: PcmLoop) {
        val pcm = Wav.resample(loop.samples, loop.sampleRate, sampleRate)
        incoming.set(pcm)
    }

    val hasLoop: Boolean get() = current.get() != null || incoming.get() != null

    /** Target 0..1. Fades out over ~3.5 s, in over ~1 s. */
    fun setRainGain(amount: Float) {
        rainGainQ15.set((amount.coerceIn(0f, 1f) * 32767).toInt())
    }

    fun setUserGain(amount: Float) {
        userGainQ15.set((amount.coerceIn(0f, 1f) * 32767).toInt())
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
