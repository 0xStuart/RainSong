package com.stuartjohnson.rainsong.intensity

import com.stuartjohnson.rainsong.protocol.DropEvent

/**
 * Rolling intensity from wrap-safe `t_us` deltas and `strength`.
 * Seq gaps count as lost hits; they do not invent notes.
 */
class IntensityTracker(
    private val windowUs: Long = 5_000_000L,
) {
    private data class Sample(
        val tUs: Long,
        val strength: Int,
        val lostBefore: Int,
    )

    private val samples = ArrayDeque<Sample>()
    private var lastTUs: Long? = null
    private var lastSeq: Int? = null
    private var lastDeltaUs: Long? = null

    @Synchronized
    fun onDrop(event: DropEvent): IntensitySnapshot {
        val prevT = lastTUs
        val prevSeq = lastSeq
        if (prevT != null) {
            lastDeltaUs = DropEvent.deltaUs(prevT, event.tUs)
        }
        val lost = if (prevSeq != null) DropEvent.seqGap(prevSeq, event.seq) else 0
        lastTUs = event.tUs
        lastSeq = event.seq
        samples.addLast(Sample(event.tUs, event.strength, lost))
        prune(event.tUs)
        return snapshot()
    }

    @Synchronized
    fun snapshot(): IntensitySnapshot {
        val drops = samples.size
        val lost = samples.sumOf { it.lostBefore }
        val mean = if (drops == 0) 0f else samples.sumOf { it.strength.toLong() }.toFloat() / drops
        val spanUs = if (samples.size >= 2) {
            DropEvent.deltaUs(samples.first().tUs, samples.last().tUs).coerceAtLeast(1L)
        } else {
            windowUs
        }
        val dps = if (drops == 0) 0f else drops * 1_000_000f / spanUs.toFloat()
        return IntensitySnapshot(
            dropsInWindow = drops,
            lostInWindow = lost,
            dropsPerSecond = dps,
            meanStrength = mean,
            lastDeltaUs = lastDeltaUs,
        )
    }

    @Synchronized
    fun reset() {
        samples.clear()
        lastTUs = null
        lastSeq = null
        lastDeltaUs = null
    }

    /** Drop the rolling window after rain has stopped. Keep seq so the next hit is not a gap. */
    @Synchronized
    fun markQuiet(): IntensitySnapshot {
        samples.clear()
        lastDeltaUs = null
        return snapshot()
    }

    private fun prune(nowUs: Long) {
        while (samples.isNotEmpty() && DropEvent.deltaUs(samples.first().tUs, nowUs) > windowUs) {
            samples.removeFirst()
        }
    }
}

data class IntensitySnapshot(
    val dropsInWindow: Int,
    val lostInWindow: Int,
    val dropsPerSecond: Float,
    val meanStrength: Float,
    val lastDeltaUs: Long?,
)
