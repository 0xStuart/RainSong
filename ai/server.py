#!/usr/bin/env python3
"""RainSong AI: Muse Spark (text) then MusicGen (WAV). Key from META_API_KEY."""

from __future__ import annotations

import io
import json
import os
import socket
import threading
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import numpy as np
import torch
from transformers import AutoProcessor, MusicgenForConditionalGeneration

MODEL_ID = os.environ.get("MUSICGEN_MODEL", "facebook/musicgen-small")
PORT = int(os.environ.get("RAINSONG_PORT") or os.environ.get("MUSICGEN_PORT", "8765"))
META_KEY = os.environ.get("META_API_KEY", "").strip()
SPARK_MODEL = os.environ.get("MUSE_SPARK_MODEL", "muse-spark-1.3")
MDNS = os.environ.get("RAINSONG_MDNS", "1").strip() not in ("0", "false", "no")

torch.set_num_threads(max(4, (os.cpu_count() or 8) - 2))
print(f"loading {MODEL_ID} on cpu…", flush=True)
processor = AutoProcessor.from_pretrained(MODEL_ID)
model = MusicgenForConditionalGeneration.from_pretrained(MODEL_ID)
model.eval()
SAMPLE_RATE = model.config.audio_encoder.sampling_rate
GEN_LOCK = threading.Lock()
print(f"ready on :{PORT}  rate={SAMPLE_RATE}  spark={'yes' if META_KEY else 'no'}", flush=True)


def lan_ip() -> str:
    forced = os.environ.get("RAINSONG_ADVERTISE_IP", "").strip()
    if forced:
        return forced
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.connect(("8.8.8.8", 80))
        return sock.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        sock.close()


def start_mdns(port: int) -> object | None:
    if not MDNS:
        return None
    try:
        from zeroconf import ServiceInfo, Zeroconf
    except ImportError:
        print("zeroconf not installed — LAN discovery off", flush=True)
        return None
    ip = lan_ip()
    info = ServiceInfo(
        "_rainsong-ai._tcp.local.",
        "RainSong AI._rainsong-ai._tcp.local.",
        addresses=[socket.inet_aton(ip)],
        port=port,
        properties={"path": "/compose", "ver": "1"},
        server="rainsong-ai.local.",
    )
    zc = Zeroconf()
    zc.register_service(info)
    print(f"mDNS _rainsong-ai._tcp on {ip}:{port}", flush=True)
    return zc


def fallback_arrangement(dps: float, mean: float, lost: int) -> dict:
    if dps < 0.4:
        title, mood, root, bright = "Still air", "hushed, almost dry", 196.0, 0.22
    elif dps < 2:
        title, mood, root, bright = "Drizzle", "soft pentatonic, close mic rain", 220.0, 0.38
    elif dps < 8:
        title, mood, root, bright = "Steady rain", "warm dorian pad, patient pulse", 174.0, 0.55
    else:
        title, mood, root, bright = "Downpour", "dark, dense, low drones", 130.0, 0.72
    return {
        "title": title,
        "mood": mood,
        "rootHz": root,
        "brightness": bright,
        "density": max(0.08, min(1.0, dps / 12.0)),
        "prompt": f"{mood}. {dps:.1f} drops/s.",
        "source": "local",
    }


def extract_json_object(raw: str) -> str | None:
    trimmed = raw.strip()
    for prefix in ("```json", "```"):
        if trimmed.startswith(prefix):
            trimmed = trimmed[len(prefix) :]
    if trimmed.endswith("```"):
        trimmed = trimmed[: -3]
    trimmed = trimmed.strip()
    start = trimmed.find("{")
    end = trimmed.rfind("}")
    if start < 0 or end <= start:
        return None
    return trimmed[start : end + 1]


def spark_score(dps: float, mean: float, lost: int) -> dict:
    fallback = fallback_arrangement(dps, mean, lost)
    if not META_KEY:
        return fallback
    prompt = (
        "You are RainSong's composer. A piezo rain sensor just reported:\n"
        f"- drops per second: {dps:.2f}\n"
        f"- mean strength 0–65535: {mean:.0f}\n"
        f"- lost hits in 5s window: {lost}\n"
        "Return ONLY JSON, no markdown, with keys:\n"
        "title (short), mood (short), rootHz (80-440), brightness (0-1),\n"
        "density (0-1), prompt (one sentence describing a looping rain-score).\n"
        "Match musical energy to the rain. Drizzle=sparse/high, downpour=low/dense.\n"
    )
    text = spark_post_responses(prompt) or spark_post_chat(prompt)
    if not text:
        return fallback
    blob = extract_json_object(text)
    if not blob:
        return fallback
    try:
        parsed = json.loads(blob)
    except json.JSONDecodeError:
        return fallback
    return {
        "title": str(parsed.get("title") or fallback["title"]),
        "mood": str(parsed.get("mood") or fallback["mood"]),
        "rootHz": float(parsed.get("rootHz") or fallback["rootHz"]),
        "brightness": float(parsed.get("brightness") or fallback["brightness"]),
        "density": float(parsed.get("density") or fallback["density"]),
        "prompt": str(parsed.get("prompt") or fallback["prompt"]),
        "source": "muse-spark",
    }


def meta_http(url: str, body: dict) -> dict | None:
    req = urllib.request.Request(
        url,
        data=json.dumps(body).encode("utf-8"),
        method="POST",
        headers={
            "Authorization": f"Bearer {META_KEY}",
            "Content-Type": "application/json",
            "Accept": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=25) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, ValueError):
        return None


def spark_post_responses(prompt: str) -> str | None:
    data = meta_http(
        "https://api.meta.ai/v1/responses",
        {"model": SPARK_MODEL, "input": prompt, "reasoning": {"effort": "low"}},
    )
    if not data:
        return None
    if data.get("output_text"):
        return str(data["output_text"])
    for item in data.get("output") or []:
        if not isinstance(item, dict) or item.get("type") != "message":
            continue
        for part in item.get("content") or []:
            if isinstance(part, dict) and part.get("text"):
                return str(part["text"])
    return None


def spark_post_chat(prompt: str) -> str | None:
    data = meta_http(
        "https://api.meta.ai/v1/chat/completions",
        {"model": SPARK_MODEL, "messages": [{"role": "user", "content": prompt}]},
    )
    if not data:
        return None
    try:
        return str(data["choices"][0]["message"]["content"])
    except (KeyError, IndexError, TypeError):
        return None


def render_wav(prompt: str, duration: int) -> bytes:
    duration = max(4, min(12, duration))
    tokens = duration * 50
    print(f"generate {duration}s: {prompt[:80]}", flush=True)
    inputs = processor(text=[prompt], padding=True, return_tensors="pt")
    with GEN_LOCK:
        with torch.no_grad():
            audio = model.generate(
                **inputs,
                do_sample=True,
                guidance_scale=3.0,
                max_new_tokens=tokens,
            )
    wav = audio[0, 0].cpu().numpy()
    wav = np.clip(wav, -1.0, 1.0)
    pcm = (wav * 32767.0).astype(np.int16)
    buf = io.BytesIO()
    import scipy.io.wavfile as wavfile

    wavfile.write(buf, SAMPLE_RATE, pcm)
    return buf.getvalue()


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt: str, *args) -> None:
        print(self.address_string(), "-", fmt % args, flush=True)

    def do_GET(self) -> None:
        path = self.path.split("?", 1)[0]
        if path in ("/health", "/"):
            body = json.dumps(
                {
                    "ok": True,
                    "service": "rainsong-ai",
                    "spark": bool(META_KEY),
                    "rate": SAMPLE_RATE,
                }
            ).encode("utf-8")
            self._send(200, body, "application/json")
            return
        self._send(404, b"no", "text/plain")

    def do_POST(self) -> None:
        path = self.path.split("?", 1)[0]
        n = int(self.headers.get("Content-Length", "0"))
        try:
            payload = json.loads(self.rfile.read(n) or b"{}")
        except json.JSONDecodeError:
            self._send(400, b"bad json", "text/plain")
            return
        if path.startswith("/compose"):
            dps = float(payload.get("dropsPerSecond") or 0)
            mean = float(payload.get("meanStrength") or 0)
            lost = int(payload.get("lostHits") or 0)
            duration = int(payload.get("duration") or 8)
            arr = spark_score(dps, mean, lost)
            wav = render_wav(arr["prompt"], duration)
            extra = {"X-RainSong-Arrangement": json.dumps(arr, ensure_ascii=True)}
            self._send(200, wav, "audio/wav", extra)
            return
        if path.startswith("/generate"):
            prompt = (payload.get("prompt") or "gentle rain ambient music, looping pad").strip()
            duration = int(payload.get("duration") or 8)
            wav = render_wav(prompt, duration)
            self._send(200, wav, "audio/wav")
            return
        self._send(404, b"no", "text/plain")

    def _send(self, code: int, body: bytes, ctype: str, extra: dict | None = None) -> None:
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        for key, value in (extra or {}).items():
            self.send_header(key, value)
        self.end_headers()
        self.wfile.write(body)


if __name__ == "__main__":
    mdns = start_mdns(PORT)
    try:
        ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
    finally:
        if mdns is not None:
            mdns.close()
