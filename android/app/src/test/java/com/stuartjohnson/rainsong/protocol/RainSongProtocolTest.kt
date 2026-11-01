package com.stuartjohnson.rainsong.protocol

import com.stuartjohnson.rainsong.intensity.IntensityTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RainSongProtocolTest {
    @Test
    fun dropEventLittleEndian() {
        val bytes = byteArrayOf(
            0x78, 0x56, 0x34, 0x12,
            0x00, 0x80.toByte(),
            0x07,
            0x03,
        )
        val drop = DropEvent.parse(bytes)
        assertEquals(0x12345678L, drop.tUs)
        assertEquals(32768, drop.strength)
        assertEquals(7, drop.seq)
        assertTrue(drop.clip)
        assertTrue(drop.woke)
    }

    @Test
    fun wrapSafeDelta() {
        assertEquals(1L, DropEvent.deltaUs(0, 1))
        assertEquals(2L, DropEvent.deltaUs(0xFFFF_FFFFL, 1L))
        assertEquals(0L, DropEvent.deltaUs(42, 42))
    }

    @Test
    fun seqGapDoesNotInventNotes() {
        assertEquals(0, DropEvent.seqGap(5, 6))
        assertEquals(1, DropEvent.seqGap(5, 7))
        assertEquals(0, DropEvent.seqGap(255, 0))
        assertEquals(2, DropEvent.seqGap(255, 2))
    }

    @Test
    fun statusLittleEndian() {
        val bytes = byteArrayOf(
            0x18, 0x0F, // 3864 mV
            0x01, 0x00, // fw 1.0
            0x03,       // SENSING | LOW_BATT
            0x00,
        )
        val status = StatusFrame.parse(bytes)
        assertEquals(3864, status.batteryMv)
        assertEquals(1, status.fwMajor)
        assertEquals(0, status.fwMinor)
        assertTrue(status.sensing)
        assertTrue(status.lowBatt)
        assertFalse(status.batteryUnknown)
    }

    @Test
    fun intensityCountsLostHitsWithoutInventingDrops() {
        val tracker = IntensityTracker()
        tracker.onDrop(DropEvent(tUs = 1_000, strength = 10, seq = 0, flags = 0))
        val afterGap = tracker.onDrop(DropEvent(tUs = 2_000, strength = 20, seq = 3, flags = 0))
        assertEquals(2, afterGap.dropsInWindow)
        assertEquals(2, afterGap.lostInWindow)
        assertEquals(1_000L, afterGap.lastDeltaUs)
    }

    @Test
    fun markQuietClearsWindowButKeepsSeq() {
        val tracker = IntensityTracker()
        tracker.onDrop(DropEvent(tUs = 1_000, strength = 10, seq = 4, flags = 0))
        val quiet = tracker.markQuiet()
        assertEquals(0, quiet.dropsInWindow)
        assertEquals(0f, quiet.dropsPerSecond, 0.01f)
        val next = tracker.onDrop(DropEvent(tUs = 2_000, strength = 11, seq = 5, flags = 0))
        assertEquals(0, next.lostInWindow)
        assertEquals(1, next.dropsInWindow)
    }
}
