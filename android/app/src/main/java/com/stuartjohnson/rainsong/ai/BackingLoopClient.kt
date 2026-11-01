package com.stuartjohnson.rainsong.ai

import com.stuartjohnson.rainsong.intensity.IntensitySnapshot

/**
 * App ↔ AI backing-loop seam.
 *
 * The live path is [AiServiceClient] against `docs/ai-interface.md`.
 * This cached stub remains so drop-to-sound never waits on the AI container.
 */
interface BackingLoopClient {
    suspend fun requestLoop(summary: RainPatternSummary): BackingLoop
}

data class RainPatternSummary(
    val dropsPerSecond: Float,
    val meanStrength: Float,
    val lostHits: Int,
    val windowMs: Int = 5_000,
)

data class BackingLoop(
    val pcm: ShortArray?,
    val sampleRateHz: Int,
    val source: Source,
) {
    enum class Source { CachedFallback, Generated }
}

class CachedBackingLoop : BackingLoopClient {
    override suspend fun requestLoop(summary: RainPatternSummary): BackingLoop {
        return FALLBACK
    }

    companion object {
        val FALLBACK = BackingLoop(
            pcm = null,
            sampleRateHz = 48_000,
            source = BackingLoop.Source.CachedFallback,
        )
    }
}

fun IntensitySnapshot.toPatternSummary(): RainPatternSummary = RainPatternSummary(
    dropsPerSecond = dropsPerSecond,
    meanStrength = meanStrength,
    lostHits = lostInWindow,
)
