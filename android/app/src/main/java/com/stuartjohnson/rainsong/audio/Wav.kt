package com.stuartjohnson.rainsong.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class PcmLoop(
    val sampleRate: Int,
    val samples: ShortArray,
)

object Wav {
    fun parse(bytes: ByteArray): PcmLoop {
        require(bytes.size >= 44) { "wav too small" }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(buf.int == 0x46464952) { "not RIFF" } // 'RIFF'
        buf.int // size
        require(buf.int == 0x45564157) { "not WAVE" } // 'WAVE'
        var channels = 1
        var rate = 32_000
        var bits = 16
        var data: ByteArray? = null
        while (buf.remaining() >= 8) {
            val id = buf.int
            val size = buf.int
            if (buf.remaining() < size) break
            when (id) {
                0x20746d66 -> { // 'fmt '
                    buf.short // format
                    channels = buf.short.toInt()
                    rate = buf.int
                    buf.int // byte rate
                    buf.short // block align
                    bits = buf.short.toInt()
                    if (size > 16) buf.position(buf.position() + (size - 16))
                }
                0x61746164 -> { // 'data'
                    val chunk = ByteArray(size)
                    buf.get(chunk)
                    data = chunk
                }
                else -> buf.position(buf.position() + size)
            }
        }
        val raw = data ?: error("no wav data")
        require(bits == 16) { "need 16-bit pcm" }
        val shorts = ShortArray(raw.size / 2)
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        val mono = if (channels <= 1) {
            shorts
        } else {
            ShortArray(shorts.size / channels) { i -> shorts[i * channels] }
        }
        return PcmLoop(rate, mono)
    }

    fun resample(src: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        if (fromRate == toRate || src.isEmpty()) return src
        val n = (src.size.toLong() * toRate / fromRate).toInt().coerceAtLeast(1)
        val out = ShortArray(n)
        val step = fromRate.toDouble() / toRate
        var pos = 0.0
        for (i in 0 until n) {
            val i0 = pos.toInt().coerceIn(0, src.lastIndex)
            val i1 = (i0 + 1).coerceAtMost(src.lastIndex)
            val frac = (pos - i0).toFloat()
            val a = src[i0].toInt()
            val b = src[i1].toInt()
            out[i] = (a + ((b - a) * frac).toInt()).toShort()
            pos += step
        }
        return out
    }
}
