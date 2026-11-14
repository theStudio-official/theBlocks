"""Generates the placeholder sound effects used by the game.

Everything is synthesised from sine/triangle partials, so there are no licensing
concerns and the whole set weighs only a few kilobytes. Re-run from the repo root:

    python tools/generate_sfx.py

Output: app/src/main/res/raw/sfx_*.wav (16-bit mono, 44.1 kHz).
"""

from __future__ import annotations

import math
import os
import struct
import wave

SAMPLE_RATE = 44_100
OUT_DIR = os.path.join("app", "src", "main", "res", "raw")


def osc(kind: str, freq: float, t: float) -> float:
    phase = 2.0 * math.pi * freq * t
    if kind == "sine":
        return math.sin(phase)
    if kind == "triangle":
        return 2.0 / math.pi * math.asin(math.sin(phase))
    if kind == "soft_square":
        return math.tanh(2.5 * math.sin(phase))
    raise ValueError(kind)


def note(
    freq: float,
    dur: float,
    amp: float = 0.3,
    kind: str = "sine",
    attack: float = 0.005,
    tau: float | None = None,
    end_freq: float | None = None,
) -> list[float]:
    """One note with a pitch glide and an exponential decay envelope."""
    tau = tau if tau is not None else dur / 3.0
    count = int(SAMPLE_RATE * dur)
    out = []
    for i in range(count):
        t = i / SAMPLE_RATE
        f = freq if end_freq is None else freq + (end_freq - freq) * (i / max(1, count - 1))
        env = min(1.0, t / attack) if attack > 0 else 1.0
        env *= math.exp(-t / tau)
        out.append(amp * env * osc(kind, f, t))
    return out


def silence(dur: float) -> list[float]:
    return [0.0] * int(SAMPLE_RATE * dur)


def mix_at(buffer: list[float], sound: list[float], offset: float) -> None:
    start = int(offset * SAMPLE_RATE)
    if len(buffer) < start + len(sound):
        buffer.extend([0.0] * (start + len(sound) - len(buffer)))
    for i, value in enumerate(sound):
        buffer[start + i] += value


def write(name: str, samples: list[float]) -> None:
    peak = max(1e-9, max(abs(s) for s in samples))
    scale = 0.85 / peak
    os.makedirs(OUT_DIR, exist_ok=True)
    path = os.path.join(OUT_DIR, name)
    with wave.open(path, "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(SAMPLE_RATE)
        frames = bytearray()
        for s in samples:
            frames += struct.pack("<h", int(max(-1.0, min(1.0, s * scale)) * 32767))
        wav.writeframes(bytes(frames))
    print(f"{path}  {len(samples) / SAMPLE_RATE * 1000:.0f} ms")


def pickup() -> list[float]:
    return note(740, 0.075, amp=0.30, end_freq=1050, tau=0.028)


def drop() -> list[float]:
    buf = note(240, 0.13, amp=0.55, kind="triangle", end_freq=120, tau=0.045)
    mix_at(buf, note(480, 0.06, amp=0.12, end_freq=300, tau=0.018), 0.0)
    return buf


def clear() -> list[float]:
    buf: list[float] = []
    for i, freq in enumerate([523.25, 659.25, 783.99, 1046.5]):
        mix_at(buf, note(freq, 0.26, amp=0.26, tau=0.085), i * 0.065)
        mix_at(buf, note(freq * 2, 0.18, amp=0.06, tau=0.05), i * 0.065)
    return buf


def combo() -> list[float]:
    buf: list[float] = []
    for i, freq in enumerate([659.25, 783.99, 987.77, 1318.51]):
        mix_at(buf, note(freq, 0.24, amp=0.28, tau=0.075), i * 0.055)
        mix_at(buf, note(freq * 2, 0.16, amp=0.08, tau=0.045), i * 0.055)
    return buf


def invalid() -> list[float]:
    buf = note(155, 0.18, amp=0.34, kind="soft_square", end_freq=120, tau=0.06)
    mix_at(buf, note(233, 0.12, amp=0.10, kind="triangle", tau=0.04), 0.0)
    return buf


def game_over() -> list[float]:
    buf: list[float] = []
    for i, freq in enumerate([392.0, 329.63, 261.63]):
        mix_at(buf, note(freq, 0.42, amp=0.30, kind="triangle", tau=0.16), i * 0.17)
        mix_at(buf, note(freq / 2, 0.42, amp=0.10, tau=0.16), i * 0.17)
    return buf


def tap() -> list[float]:
    return note(1250, 0.04, amp=0.22, tau=0.012)


SOUNDS = {
    "sfx_pickup.wav": pickup,
    "sfx_drop.wav": drop,
    "sfx_clear.wav": clear,
    "sfx_combo.wav": combo,
    "sfx_invalid.wav": invalid,
    "sfx_gameover.wav": game_over,
    "sfx_tap.wav": tap,
}


if __name__ == "__main__":
    for filename, factory in SOUNDS.items():
        write(filename, factory())
