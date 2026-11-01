package com.stuartjohnson.rainsong.tcp

import com.stuartjohnson.rainsong.protocol.DropEvent
import com.stuartjohnson.rainsong.protocol.RainSongUuids
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Debug transport from `docs/protocol.md`: concatenated 8-byte drop frames,
 * no header, no Status. Connect = sensor connected; close = disconnected.
 *
 * Emulator: [RainSongUuids.EMULATOR_TCP_HOST]:17321 (host loopback).
 * Device with `adb reverse`: [RainSongUuids.LOOPBACK_TCP_HOST]:17321.
 */
class TcpDropClient(
    private val onDrop: (DropEvent) -> Unit,
    private val onConnection: (connected: Boolean, message: String?) -> Unit,
) {
    @Volatile private var socket: Socket? = null
    @Volatile private var thread: Thread? = null

    fun connect(host: String, port: Int = RainSongUuids.TCP_PORT) {
        disconnect()
        val t = Thread({
            val s = Socket()
            try {
                s.tcpNoDelay = true
                s.connect(InetSocketAddress(host, port), 3_000)
                socket = s
                onConnection(true, null)
                readLoop(s.getInputStream())
                onConnection(false, "TCP closed")
            } catch (e: Exception) {
                if (thread != null) {
                    onConnection(false, e.message ?: "TCP error")
                }
            } finally {
                runCatching { s.close() }
                if (socket === s) socket = null
            }
        }, "rainsong-tcp")
        thread = t
        t.start()
    }

    fun disconnect() {
        val t = thread
        thread = null
        runCatching { socket?.close() }
        socket = null
        t?.interrupt()
    }

    private fun readLoop(input: InputStream) {
        val buf = ByteArray(DropEvent.SIZE)
        var filled = 0
        while (thread != null && !Thread.currentThread().isInterrupted) {
            val n = input.read(buf, filled, DropEvent.SIZE - filled)
            if (n < 0) break
            filled += n
            if (filled == DropEvent.SIZE) {
                onDrop(DropEvent.parse(buf))
                filled = 0
            }
        }
    }
}
