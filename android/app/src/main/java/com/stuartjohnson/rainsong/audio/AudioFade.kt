package com.stuartjohnson.rainsong.audio

/** Shared linear slew for rain bed gain. Out is slower than in. */
internal object AudioFade {
    const val OUT_SEC = 3.5
    const val IN_SEC = 1.0

    fun slew(current: Double, target: Double, frames: Int, sampleRate: Int): Double {
        val sec = if (target < current - 1e-6) OUT_SEC else IN_SEC
        val step = frames / (sampleRate * sec)
        return current + (target - current).coerceIn(-step, step)
    }
}
