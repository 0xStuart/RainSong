package com.stuartjohnson.rainsong.protocol

import java.util.UUID

/**
 * BLE + TCP debug contract from `docs/protocol.md`. Do not invent fields.
 * Play audio on packet arrival; [DropEvent.tUs] is for intensity/pattern only.
 */
object RainSongUuids {
    const val GAP_NAME = "RainSong"
    const val TCP_PORT = 17321
    const val EMULATOR_TCP_HOST = "10.0.2.2"
    const val LOOPBACK_TCP_HOST = "127.0.0.1"

    val SERVICE: UUID = UUID.fromString("1e968972-3c13-443c-ad79-a41645d56e75")
    val DROP_EVENT: UUID = UUID.fromString("81a2f805-d83b-4214-b5d1-895642ccaa8f")
    val STATUS: UUID = UUID.fromString("5c471e86-7a6e-45b2-9434-bc440c0a8dfe")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

data class DropEvent(
    val tUs: Long,
    val strength: Int,
    val seq: Int,
    val flags: Int,
) {
    val clip: Boolean get() = flags and FLAG_CLIP != 0
    val woke: Boolean get() = flags and FLAG_WOKE != 0

    companion object {
        const val SIZE = 8
        const val FLAG_CLIP = 0x01
        const val FLAG_WOKE = 0x02

        fun parse(bytes: ByteArray, offset: Int = 0): DropEvent {
            require(bytes.size - offset >= SIZE) { "drop frame needs $SIZE bytes" }
            val tUs = u32le(bytes, offset)
            val strength = u16le(bytes, offset + 4)
            val seq = bytes[offset + 6].toInt() and 0xFF
            val flags = bytes[offset + 7].toInt() and 0xFF
            return DropEvent(tUs, strength, seq, flags)
        }

        /** Wrap-safe unsigned 32-bit difference of consecutive `t_us` values. */
        fun deltaUs(t0: Long, t1: Long): Long = (t1 - t0) and 0xFFFF_FFFFL

        /**
         * Missed sequence numbers between [prev] (exclusive) and [next].
         * A gap means lost notifies — do not invent notes for them.
         */
        fun seqGap(prev: Int, next: Int): Int = (next - prev - 1) and 0xFF
    }
}

data class StatusFrame(
    val batteryMv: Int,
    val fwMajor: Int,
    val fwMinor: Int,
    val flags: Int,
) {
    val sensing: Boolean get() = flags and FLAG_SENSING != 0
    val lowBatt: Boolean get() = flags and FLAG_LOW_BATT != 0
    val batteryUnknown: Boolean get() = batteryMv == 0

    companion object {
        const val SIZE = 6
        const val FLAG_SENSING = 0x01
        const val FLAG_LOW_BATT = 0x02

        fun parse(bytes: ByteArray, offset: Int = 0): StatusFrame {
            require(bytes.size - offset >= SIZE) { "status frame needs $SIZE bytes" }
            return StatusFrame(
                batteryMv = u16le(bytes, offset),
                fwMajor = bytes[offset + 2].toInt() and 0xFF,
                fwMinor = bytes[offset + 3].toInt() and 0xFF,
                flags = bytes[offset + 4].toInt() and 0xFF,
            )
        }
    }
}

internal fun u16le(bytes: ByteArray, offset: Int): Int {
    return (bytes[offset].toInt() and 0xFF) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 8)
}

internal fun u32le(bytes: ByteArray, offset: Int): Long {
    return (bytes[offset].toLong() and 0xFF) or
        ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
        ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
        ((bytes[offset + 3].toLong() and 0xFF) shl 24)
}
