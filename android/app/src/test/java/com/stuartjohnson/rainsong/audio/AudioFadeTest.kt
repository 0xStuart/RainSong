package com.stuartjohnson.rainsong.audio

import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFadeTest {
    @Test
    fun fadeOutTakesAboutThreeAndAHalfSeconds() {
        var g = 1.0
        val sr = 48_000
        val frames = 960
        repeat(50) { g = AudioFade.slew(g, 0.0, frames, sr) }
        // 50 buffers * 20 ms = 1 s, so ~1 - 1/3.5 remaining
        assertTrue(g in 0.68..0.76)
        repeat(130) { g = AudioFade.slew(g, 0.0, frames, sr) }
        assertTrue(g < 0.02)
    }

    @Test
    fun fadeInIsFasterThanFadeOut() {
        var up = 0.0
        var down = 1.0
        val sr = 48_000
        val frames = 960
        repeat(50) {
            up = AudioFade.slew(up, 1.0, frames, sr)
            down = AudioFade.slew(down, 0.0, frames, sr)
        }
        assertTrue(up > 0.95)
        assertTrue(down > 0.65)
    }
}
