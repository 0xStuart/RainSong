#!/usr/bin/env python3
"""TCP drop harness for emulator demos (docs/protocol.md 8-byte frames).

Default mode is random bursts of rain over a window, then an optional silent
tail with the socket still open so the app can fade out.
"""

from __future__ import annotations

import argparse
import random
import socket
import struct
import time


PORT = 17321
FRAME = struct.Struct("<IHBB")  # t_us, strength, seq, flags


def pack_drop(t_us: int, strength: int, seq: int) -> bytes:
    return FRAME.pack(t_us & 0xFFFFFFFF, strength & 0xFFFF, seq & 0xFF, 0)


def serve(
    host: str,
    port: int,
    quiet: float,
    duration: float,
    peak: float,
    hold: float,
    tail: float,
    mode: str,
    seed: int | None,
) -> None:
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind((host, port))
    srv.listen(1)
    print(
        f"listening on {host}:{port} "
        f"(mode={mode}, quiet={quiet}s, duration={duration}s, peak={peak}/s, "
        f"hold={hold}s, tail={tail}s, seed={seed})",
        flush=True,
    )
    while True:
        conn, addr = srv.accept()
        print(f"client {addr}", flush=True)
        print("READY", flush=True)
        conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        rng = random.Random(seed)
        try:
            if mode == "bursts":
                run_bursts(conn, quiet, duration, peak, tail, rng)
            else:
                run_ramp(conn, quiet, duration, peak, hold, tail)
        except (BrokenPipeError, ConnectionResetError, OSError) as e:
            print(f"client gone: {e}", flush=True)
        finally:
            conn.close()


def run_bursts(
    conn: socket.socket,
    quiet: float,
    duration: float,
    peak: float,
    tail: float,
    rng: random.Random,
) -> None:
    """Irregular showers: quiet gaps, then short denser bursts."""
    if quiet > 0:
        time.sleep(quiet)
    print("RAMP_START", flush=True)
    start = time.monotonic()
    end_rain = start + duration
    end_at = end_rain + tail
    seq = 0
    t0 = time.time_ns() // 1000
    next_log = start + 1.0
    # Plan bursts that cover the window without a smooth envelope.
    t = 0.0
    planned: list[tuple[float, float, float]] = []  # start, length, rate
    while t < duration:
        gap = rng.uniform(0.35, 2.2)
        t += gap
        if t >= duration:
            break
        length = rng.uniform(0.4, 2.8)
        length = min(length, duration - t)
        # Most bursts light; occasional denser ones. Never a steady climb.
        if rng.random() < 0.22:
            rate = rng.uniform(peak * 0.55, peak)
        else:
            rate = rng.uniform(0.6, max(1.2, peak * 0.45))
        planned.append((t, length, rate))
        t += length

    burst_i = 0
    while True:
        now = time.monotonic()
        if now >= end_at:
            print(f"done seq={seq}", flush=True)
            return
        elapsed = now - start
        rate = 0.0
        if now < end_rain:
            while burst_i < len(planned) and elapsed > planned[burst_i][0] + planned[burst_i][1]:
                burst_i += 1
            if burst_i < len(planned):
                b0, blen, brate = planned[burst_i]
                if b0 <= elapsed <= b0 + blen:
                    # Soft edges inside the burst so it isn't a brick wall.
                    u = (elapsed - b0) / max(blen, 0.001)
                    edge = min(u / 0.15, (1.0 - u) / 0.2, 1.0)
                    edge = max(0.0, edge)
                    rate = brate * edge
                    # Tiny jitter so hits aren't metronomic.
                    rate *= rng.uniform(0.75, 1.25)

        # Fire with probability rate*dt each loop (~4ms)
        dt = 0.004
        if rate > 0 and rng.random() < min(0.85, rate * dt):
            strength = int(rng.uniform(10_000, 58_000))
            t_us = (time.time_ns() // 1000 - t0) & 0xFFFFFFFF
            conn.sendall(pack_drop(t_us, strength, seq))
            seq = (seq + 1) & 0xFF

        if now >= next_log:
            print(f"t={elapsed:.1f}s rate~{rate:.1f}/s seq={seq}", flush=True)
            next_log = now + 1.0
        time.sleep(dt)


def rate_at(elapsed: float, duration: float, peak: float) -> float:
    if elapsed <= 0 or elapsed >= duration:
        return 0.0
    t = elapsed / duration
    return peak * (t ** 3)


def run_ramp(
    conn: socket.socket,
    quiet: float,
    duration: float,
    peak: float,
    hold: float,
    tail: float,
) -> None:
    if quiet > 0:
        time.sleep(quiet)
    print("RAMP_START", flush=True)
    start = time.monotonic()
    last = start
    accum = 0.0
    seq = 0
    t0 = time.time_ns() // 1000
    rain_end = start + duration
    hold_end = rain_end + hold
    end_at = hold_end + tail
    next_log = start + 1.0
    while True:
        now = time.monotonic()
        if now >= end_at:
            print(f"done seq={seq}", flush=True)
            return
        elapsed = now - start
        dt = now - last
        last = now
        if now < rain_end:
            rate = rate_at(elapsed, duration, peak)
        elif now < hold_end:
            rate = peak
        else:
            rate = 0.0
        accum += rate * dt
        if accum > 8:
            accum = 8.0
        while accum >= 1.0:
            accum -= 1.0
            strength = int(8_000 + min(1.0, elapsed / max(duration, 0.001)) * 55_000)
            t_us = (time.time_ns() // 1000 - t0) & 0xFFFFFFFF
            conn.sendall(pack_drop(t_us, strength, seq))
            seq = (seq + 1) & 0xFF
        if now >= next_log:
            print(f"t={elapsed:.1f}s rate~{rate:.1f}/s seq={seq}", flush=True)
            next_log = now + 1.0
        time.sleep(0.004)


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--host", default="0.0.0.0")
    p.add_argument("--port", type=int, default=PORT)
    p.add_argument("--quiet", type=float, default=0.0, help="seconds of silence after connect")
    p.add_argument("--duration", type=float, default=30.0, help="active rain window seconds")
    p.add_argument("--peak", type=float, default=9.0, help="max burst rate (drops/s)")
    p.add_argument("--hold", type=float, default=0.0, help="ramp mode: hold peak")
    p.add_argument("--tail", type=float, default=3.0, help="keep connection open, no drops")
    p.add_argument("--mode", choices=("bursts", "ramp"), default="bursts")
    p.add_argument("--seed", type=int, default=None)
    args = p.parse_args()
    serve(
        args.host,
        args.port,
        args.quiet,
        args.duration,
        args.peak,
        args.hold,
        args.tail,
        args.mode,
        args.seed,
    )


if __name__ == "__main__":
    main()
