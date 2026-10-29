#!/usr/bin/env python3
"""Процедурная генерация музыки, эмбиента и звуковых эффектов (numpy + ffmpeg).

Все композиции оригинальные: небольшой секвенсор с синтезом электропиано,
пэдов, колокольчиков, щипковых, баса и мягких ударных + свёрточный ревербератор.
"""
import os
import subprocess
import wave

import numpy as np

SR = 44100
ROOT = os.path.join(os.path.dirname(__file__), "..", "assets", "audio")
rng = np.random.default_rng(7)

NOTES = {"C": 0, "C#": 1, "Db": 1, "D": 2, "D#": 3, "Eb": 3, "E": 4, "F": 5, "F#": 6, "Gb": 6, "G": 7, "G#": 8, "Ab": 8, "A": 9, "A#": 10, "Bb": 10, "B": 11}


def hz(n):
    """'A4' -> частота."""
    name, octv = n[:-1], int(n[-1])
    midi = 12 * (octv + 1) + NOTES[name]
    return 440.0 * 2 ** ((midi - 69) / 12)


def midi_hz(m):
    return 440.0 * 2 ** ((m - 69) / 12)


def env(n, a=0.005, r=0.3, sustain=1.0):
    t = np.arange(n) / SR
    e = np.minimum(1.0, t / max(a, 1e-4))
    rel = np.clip((n / SR - t) / max(r, 1e-4), 0, 1)
    return e * np.minimum(rel, 1.0) * sustain


def ep(f, dur, vel=0.5):
    n = int(dur * SR)
    t = np.arange(n) / SR
    dec = np.exp(-t * 2.2)
    s = np.sin(2 * np.pi * f * t) + 0.35 * np.sin(4 * np.pi * f * t) * np.exp(-t * 6) + 0.15 * np.sin(6 * np.pi * f * t) * np.exp(-t * 9)
    s *= (1 + 0.06 * np.sin(2 * np.pi * 4.5 * t))
    return s * dec * env(n, 0.004, 0.25) * vel


def piano(f, dur, vel=0.5):
    n = int(dur * SR)
    t = np.arange(n) / SR
    s = sum((0.6 ** k) * np.sin(2 * np.pi * f * (k + 1) * t * (1 + 0.0004 * k)) * np.exp(-t * (1.6 + k * 1.3)) for k in range(5))
    return s * env(n, 0.002, 0.2) * vel


def bell(f, dur, vel=0.4):
    n = int(dur * SR)
    t = np.arange(n) / SR
    s = np.sin(2 * np.pi * f * t) * np.exp(-t * 3) + 0.5 * np.sin(2 * np.pi * f * 2.76 * t) * np.exp(-t * 6) + 0.25 * np.sin(2 * np.pi * f * 5.4 * t) * np.exp(-t * 10)
    return s * env(n, 0.002, 0.1) * vel


def pad(f, dur, vel=0.25, bright=6):
    n = int(dur * SR)
    t = np.arange(n) / SR
    s = np.zeros(n)
    for det in (-0.006, 0.0, 0.007):
        ff = f * (1 + det)
        for k in range(1, bright + 1):
            s += np.sin(2 * np.pi * ff * k * t + det * 50) / k
    s *= 0.5 + 0.12 * np.sin(2 * np.pi * 0.3 * t)
    return s / 3 * env(n, min(1.2, dur * 0.4), min(1.5, dur * 0.4)) * vel


def pluck(f, dur, vel=0.4):
    n = int(dur * SR)
    p = max(2, int(SR / f))
    buf = rng.uniform(-1, 1, p)
    out = np.zeros(n)
    for i in range(n):
        out[i] = buf[i % p]
        buf[i % p] = 0.5 * (buf[i % p] + buf[(i + 1) % p]) * 0.996
    return out * env(n, 0.001, 0.05) * vel


def pluck_fast(f, dur, vel=0.4):
    """Быстрая имитация щипка (без цикла Карплуса-Стронга)."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    s = sum((0.55 ** k) * np.sin(2 * np.pi * f * (k + 1) * t) * np.exp(-t * (5 + 4 * k)) for k in range(6))
    return s * env(n, 0.001, 0.05) * vel


def bass(f, dur, vel=0.5):
    n = int(dur * SR)
    t = np.arange(n) / SR
    s = np.sin(2 * np.pi * f * t) + 0.3 * np.sin(4 * np.pi * f * t)
    return s * np.exp(-t * 1.5) * env(n, 0.01, 0.1) * vel


def kick(vel=0.6):
    n = int(0.3 * SR)
    t = np.arange(n) / SR
    f = 45 + 80 * np.exp(-t * 30)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph) * np.exp(-t * 12) * vel


def lowpass(x, cutoff):
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = np.zeros_like(x)
    acc = 0.0
    # векторизованная однополюсная фильтрация через lfilter-подобный метод
    from itertools import accumulate  # noqa
    for i in range(len(x)):
        acc = (1 - a) * x[i] + a * acc
        y[i] = acc
    return y


def onepole(x, cutoff):
    """Быстрый ФНЧ через FFT (идеальный фильтр со сглаженным краем)."""
    X = np.fft.rfft(x)
    fr = np.fft.rfftfreq(len(x), 1 / SR)
    X *= 1 / np.sqrt(1 + (fr / cutoff) ** 4)
    return np.fft.irfft(X, len(x))


def highpass(x, cutoff):
    X = np.fft.rfft(x)
    fr = np.fft.rfftfreq(len(x), 1 / SR)
    X *= 1 / np.sqrt(1 + (cutoff / np.maximum(fr, 1)) ** 4)
    return np.fft.irfft(X, len(x))


def noise(dur):
    return rng.uniform(-1, 1, int(dur * SR))


def hat(vel=0.15):
    n = noise(0.05)
    return highpass(n, 7000) * np.exp(-np.arange(len(n)) / SR * 80) * vel


def snare(vel=0.25):
    n = noise(0.2)
    t = np.arange(len(n)) / SR
    return (onepole(highpass(n, 1200), 5000) * np.exp(-t * 18) + 0.3 * np.sin(2 * np.pi * 190 * t) * np.exp(-t * 25)) * vel


def reverb(x, seconds=2.4, wet=0.3):
    n = int(seconds * SR)
    t = np.arange(n) / SR
    out = []
    for ch in range(2):
        ir = rng.normal(0, 1, n) * np.exp(-t * 3.0 / seconds * 2.3)
        ir = onepole(ir, 5000)
        ir[0] = 0
        ir /= np.sqrt(np.sum(ir ** 2))
        L = len(x) + n
        size = 1 << (L - 1).bit_length()
        y = np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(ir, size), size)[:L]
        # хвост заворачиваем в начало — бесшовная петля
        y[:n] += y[len(x):len(x) + n]
        y = y[:len(x)]
        out.append(x * (1 - wet) + y * wet * 1.2)
    return np.stack(out, axis=1)


class Track:
    def __init__(self, bpm, bars, beats=4):
        self.bpm = bpm
        self.beat = 60.0 / bpm
        self.len = int(bars * beats * self.beat * SR)
        self.buf = np.zeros(self.len + SR * 4)

    def add(self, sig, beat_pos, gain=1.0):
        i = int(beat_pos * self.beat * SR)
        end = min(i + len(sig), len(self.buf))
        self.buf[i:end] += sig[:end - i] * gain

    def render(self, wet=0.3, seconds=2.4):
        x = self.buf[:self.len].copy()
        # что вылезло за конец — в начало (петля)
        tail = self.buf[self.len:]
        x[:len(tail)] += tail
        st = reverb(x, seconds, wet)
        st /= max(1e-6, np.max(np.abs(st))) / 0.85
        return st


def chord_notes(root, kind):
    iv = {"maj7": [0, 4, 7, 11], "m7": [0, 3, 7, 10], "7": [0, 4, 7, 10], "maj": [0, 4, 7], "m": [0, 3, 7],
          "m9": [0, 3, 7, 10, 14], "maj9": [0, 4, 7, 11, 14], "add9": [0, 4, 7, 14], "13": [0, 4, 10, 14, 21], "sus": [0, 5, 7]}[kind]
    base = 12 * 4 + NOTES[root]
    return [base + i for i in iv]


def save_ogg(name, st, folder="music", q=4):
    os.makedirs(os.path.join(ROOT, folder), exist_ok=True)
    tmp = os.path.join(ROOT, folder, name + ".tmp.wav")
    write_wav(tmp, st, stereo=True)
    out = os.path.join(ROOT, folder, name + ".ogg")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp, "-c:a", "libvorbis", "-q:a", str(q), out], check=True)
    os.remove(tmp)


def write_wav(path, x, stereo=False, sr=SR):
    x = np.clip(x, -1, 1)
    data = (x * 32767).astype(np.int16)
    with wave.open(path, "wb") as w:
        w.setnchannels(2 if stereo else 1)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(data.tobytes())


# ================================================================ МУЗЫКА
def room():
    prog = [("F", "maj7"), ("E", "m7"), ("D", "m7"), ("C", "maj7")]
    tr = Track(76, 32)
    for bar in range(32):
        r, k = prog[bar % 4]
        notes = chord_notes(r, k)
        for i, m in enumerate(notes):
            tr.add(ep(midi_hz(m), 3.0, 0.16), bar * 4 + i * 0.03)
        if bar % 2 == 1:
            tr.add(ep(midi_hz(notes[1] + 12), 1.0, 0.08), bar * 4 + 2.5)
        tr.add(bass(midi_hz(12 * 3 + NOTES[r]), 1.8, 0.35), bar * 4)
        tr.add(bass(midi_hz(12 * 3 + NOTES[r] + 7), 0.9, 0.25), bar * 4 + 2.5)
        if bar >= 4:
            for b in range(4):
                if b in (0, 2):
                    tr.add(kick(0.35), bar * 4 + b)
                if b in (1, 3):
                    tr.add(snare(0.12), bar * 4 + b)
                tr.add(hat(0.05), bar * 4 + b + 0.5)
                tr.add(hat(0.03), bar * 4 + b)
    melody = [(72, 0, 1), (74, 1, 0.5), (76, 1.5, 1.5), (79, 3, 1), (77, 4, 2), (76, 6, 1), (74, 7, 1),
              (72, 8, 1.5), (71, 9.5, 0.5), (72, 10, 2), (69, 12, 1), (71, 13, 1), (72, 14, 2)]
    for rep in (8, 24):
        for m, b, d in melody:
            tr.add(bell(midi_hz(m), d + 1.2, 0.13), rep * 4 + b)
    st = tr.render(0.28)
    crackle = np.zeros(len(st))
    idx = rng.integers(0, len(st), 900)
    crackle[idx] = rng.uniform(-0.15, 0.15, 900)
    crackle += onepole(noise(len(st) / SR), 2500)[:len(st)] * 0.01
    st += crackle[:, None]
    save_ogg("room", st)


def night():
    prog = [("A", "m9"), ("F", "maj7"), ("C", "add9"), ("G", "sus")]
    tr = Track(60, 24)
    for bar in range(24):
        r, k = prog[bar % 4]
        notes = chord_notes(r, k)
        for m in notes[:4]:
            tr.add(pad(midi_hz(m - 12), 4.6, 0.09), bar * 4)
        arp = notes + [notes[1] + 12]
        for i in range(8):
            m = arp[(i * 3 + bar) % len(arp)] + 12
            tr.add(bell(midi_hz(m), 1.5, 0.07 if i % 2 else 0.1), bar * 4 + i * 0.5)
        tr.add(bass(midi_hz(12 * 2 + NOTES[r] + 12), 3.8, 0.22), bar * 4)
    save_ogg("night", tr.render(0.45, 3.2))


def city():
    prog = [("D", "m9"), ("G", "13"), ("C", "maj9"), ("A", "7")]
    tr = Track(78, 32)
    walk = {"D": [38, 41, 45, 43], "G": [43, 47, 50, 45], "C": [36, 40, 43, 44], "A": [45, 44, 43, 40]}
    for bar in range(32):
        r, k = prog[bar % 4]
        notes = chord_notes(r, k)
        for hit in (0, 1.5, 2.75):
            for m in notes:
                tr.add(ep(midi_hz(m), 1.2 if hit else 2.0, 0.09), bar * 4 + hit)
        for i, m in enumerate(walk[r]):
            tr.add(pluck_fast(midi_hz(m), 0.9, 0.35), bar * 4 + i)
        for b in range(4):
            tr.add(hat(0.04), bar * 4 + b)
            tr.add(hat(0.03), bar * 4 + b + 0.66)
    mel = [(74, 0, 1.5), (77, 1.5, 0.5), (76, 2, 1), (74, 3, 1), (72, 4, 2), (69, 6, 2), (71, 8, 1), (72, 9, 1), (74, 10, 2), (76, 12, 3)]
    for rep in (4, 12, 20):
        for m, b, d in mel:
            tr.add(ep(midi_hz(m), d + 0.5, 0.14), rep * 4 + b)
    save_ogg("city", tr.render(0.32))


def library():
    prog = [("E", "m"), ("C", "maj7"), ("A", "m"), ("B", "7")]
    tr = Track(54, 16)
    pent = [64, 67, 69, 71, 74, 76, 79]
    for bar in range(16):
        r, k = prog[bar % 4]
        for m in chord_notes(r, k):
            tr.add(pad(midi_hz(m - 12), 4.8, 0.1, 4), bar * 4)
        for _ in range(3):
            tr.add(bell(midi_hz(int(rng.choice(pent)) + 12), 2.5, 0.06), bar * 4 + float(rng.integers(0, 8)) * 0.5)
    save_ogg("library", tr.render(0.55, 3.5))


def train():
    prog = [("D", "maj"), ("B", "m"), ("G", "maj7"), ("A", "sus")]
    tr = Track(96, 32)
    for bar in range(32):
        r, k = prog[bar % 4]
        notes = chord_notes(r, k)
        for m in notes:
            tr.add(pad(midi_hz(m - 12), 2.6, 0.06, 5), bar * 4)
        seq = [notes[0], notes[1], notes[2], notes[1] + 12, notes[2], notes[1], notes[0] + 12, notes[2]]
        for i, m in enumerate(seq):
            tr.add(pluck_fast(midi_hz(m + 12), 0.5, 0.12), bar * 4 + i * 0.5)
        tr.add(bass(midi_hz(36 + NOTES[r]), 1.9, 0.3), bar * 4)
        tr.add(bass(midi_hz(36 + NOTES[r]), 1.9, 0.25), bar * 4 + 2)
        # стук колёс
        for b in (0, 0.25, 2, 2.25):
            tr.add(snare(0.05), bar * 4 + b)
    mel = [(78, 0, 1), (76, 1, 1), (74, 2, 2), (73, 4, 1), (74, 5, 1), (76, 6, 2), (78, 8, 2), (81, 10, 2), (79, 12, 4)]
    for rep in (8, 16, 24):
        for m, b, d in mel:
            tr.add(bell(midi_hz(m), d + 1, 0.1), rep * 4 + b)
    save_ogg("train", tr.render(0.3))


def edge():
    tr = Track(50, 12)
    for bar in range(12):
        for m in [60, 64, 67, 74] if bar % 2 == 0 else [57, 64, 67, 71]:
            tr.add(pad(midi_hz(m + 12), 5.2, 0.06, 2), bar * 4)
        if bar % 3 == 0:
            for i, m in enumerate([79, 76, 74, 72]):
                tr.add(piano(midi_hz(m), 3, 0.12), bar * 4 + i * 0.75)
    save_ogg("edge", tr.render(0.6, 4.0))


THEME = [(72, 0, 1), (76, 1, 1), (79, 2, 1.5), (77, 3.5, 0.5), (76, 4, 2), (74, 6, 2),
         (72, 8, 1), (74, 9, 1), (76, 10, 1), (79, 11, 1), (81, 12, 3), (79, 15, 1),
         (77, 16, 1.5), (76, 17.5, 0.5), (74, 18, 2), (72, 20, 1), (71, 21, 1), (72, 22, 2),
         (74, 24, 1), (76, 25, 1), (77, 26, 2), (76, 28, 1.5), (74, 29.5, 0.5), (72, 30, 2)]
THEME_PROG = [("F", "maj7"), ("G", "maj"), ("E", "m7"), ("A", "m7"), ("D", "m7"), ("G", "sus"), ("C", "maj"), ("C", "add9")]


def ending():
    tr = Track(68, 32)
    for bar in range(32):
        r, k = THEME_PROG[bar % 8]
        notes = chord_notes(r, k)
        for i, m in enumerate(notes):
            tr.add(piano(midi_hz(m - 12), 3.5, 0.13), bar * 4 + i * 0.5)
            tr.add(piano(midi_hz(m), 1.5, 0.07), bar * 4 + 2 + i * 0.25)
        tr.add(piano(midi_hz(24 + NOTES[r]), 4, 0.2), bar * 4)
        if bar >= 8:
            for m in notes[:3]:
                tr.add(pad(midi_hz(m), 4.4, 0.05, 3), bar * 4)
    for rep in (0, 8, 16, 24):
        for m, b, d in THEME:
            tr.add(piano(midi_hz(m), d + 1.5, 0.22 if rep else 0.18), rep * 4 + b)
    save_ogg("ending", tr.render(0.4, 3.0))


def menu():
    tr = Track(62, 16)
    for bar in range(16):
        r, k = THEME_PROG[bar % 8]
        for m in chord_notes(r, k):
            tr.add(pad(midi_hz(m - 12), 4.6, 0.07, 3), bar * 4)
        tr.add(bass(midi_hz(36 + NOTES[r]), 3.8, 0.18), bar * 4)
    for rep in (0, 8):
        for m, b, d in THEME:
            tr.add(bell(midi_hz(m + 12), d + 1.8, 0.12), rep * 4 + b)
    save_ogg("menu", tr.render(0.45, 3.2))


def minigame():
    prog = [("C", "maj"), ("A", "m"), ("F", "maj"), ("G", "maj")]
    tr = Track(112, 16)
    for bar in range(16):
        r, k = prog[bar % 4]
        notes = chord_notes(r, k)
        for i in range(8):
            m = notes[[0, 1, 2, 1, 2, 0, 1, 2][i]] + 12
            tr.add(pluck_fast(midi_hz(m), 0.35, 0.13), bar * 4 + i * 0.5)
        tr.add(bass(midi_hz(36 + NOTES[r]), 0.9, 0.3), bar * 4)
        tr.add(bass(midi_hz(43 + NOTES[r]), 0.9, 0.25), bar * 4 + 2)
        tr.add(kick(0.25), bar * 4)
        tr.add(kick(0.2), bar * 4 + 2)
        for b in range(4):
            tr.add(hat(0.04), bar * 4 + b + 0.5)
    mel = [(76, 0, 0.5), (79, 0.5, 0.5), (81, 1, 1), (79, 2, 0.5), (76, 2.5, 0.5), (74, 3, 1), (72, 4, 0.5), (74, 4.5, 0.5), (76, 5, 1), (79, 6, 2)]
    for rep in (4, 8, 12):
        for m, b, d in mel:
            tr.add(bell(midi_hz(m), d + 0.4, 0.1), rep * 4 + b)
    save_ogg("minigame", tr.render(0.22))


def dawn():
    prog = [("C", "add9"), ("G", "maj"), ("A", "m7"), ("F", "maj7")]
    tr = Track(80, 16)
    for bar in range(16):
        r, k = prog[bar % 4]
        notes = chord_notes(r, k)
        for m in notes:
            tr.add(pad(midi_hz(m), 3.4, 0.07, 4), bar * 4)
        for i in range(4):
            tr.add(ep(midi_hz(notes[i % len(notes)] + 12), 1.2, 0.1), bar * 4 + i)
        tr.add(bass(midi_hz(36 + NOTES[r]), 3.5, 0.25), bar * 4)
    for m, b, d in THEME:
        tr.add(bell(midi_hz(m + 12), d + 1, 0.1), 32 + b)
    save_ogg("dawn", tr.render(0.4))


# =============================================================== ЭМБИЕНТ
def ambience():
    d = 30.0
    n = int(d * SR)
    # дождь
    r = onepole(highpass(noise(d), 400), 6000) * 0.25
    drops = np.zeros(n)
    for i in rng.integers(0, n - 2000, 1600):
        k = np.arange(400)
        drops[i:i + 400] += np.sin(2 * np.pi * rng.uniform(1800, 4200) * k / SR) * np.exp(-k / 60) * rng.uniform(0.02, 0.08)
    rain = np.stack([r + drops, np.roll(r, 3000) + np.roll(drops, 1500)], axis=1)
    save_ogg("rain", rain / np.max(np.abs(rain)) * 0.6, "amb", 3)
    # ветер
    w = onepole(noise(d), 600)
    lfo = 0.6 + 0.4 * np.sin(2 * np.pi * np.arange(n) / SR / 7.5)
    wind = w * lfo
    st = np.stack([wind, np.roll(wind, 8000)], axis=1)
    save_ogg("wind", st / np.max(np.abs(st)) * 0.5, "amb", 3)
    # поезд
    rumble = onepole(noise(d), 180) * 1.5
    clack = np.zeros(n)
    beat = 0.86
    t = 0.0
    while t < d - 0.5:
        for off in (0.0, 0.12):
            i = int((t + off) * SR)
            s = snare(0.5)
            clack[i:i + len(s)] += onepole(s, 1500)[:min(len(s), n - i)]
        t += beat
    tr = rumble + clack
    st = np.stack([tr, np.roll(tr, 400)], axis=1)
    save_ogg("train", st / np.max(np.abs(st)) * 0.55, "amb", 3)
    # библиотека: тихий гул и редкие перезвоны
    hum = onepole(noise(d), 300) * 0.4
    for i in range(10):
        pos = int(rng.uniform(0, d - 4) * SR)
        b = bell(rng.choice([1200.0, 1500.0, 1800.0, 2100.0]), 3.0, 0.08)
        hum[pos:pos + len(b)] += b
    st = reverb(hum, 3.0, 0.5)
    save_ogg("library", st / np.max(np.abs(st)) * 0.4, "amb", 3)


# ======================================================== ЗВУКОВЫЕ ЭФФЕКТЫ
def sfx():
    out = os.path.join(ROOT, "sfx")
    os.makedirs(out, exist_ok=True)

    def w(name, x, gain=0.8):
        x = np.asarray(x, dtype=float)
        if np.max(np.abs(x)) > 0:
            x = x / np.max(np.abs(x)) * gain
        fade = min(len(x), 200)
        x[-fade:] *= np.linspace(1, 0, fade)
        write_wav(os.path.join(out, name + ".wav"), x)

    def tone(f, d, decay=20, kind="sine"):
        t = np.arange(int(d * SR)) / SR
        if kind == "square":
            s = np.sign(np.sin(2 * np.pi * f * t)) * 0.5
        elif kind == "tri":
            s = 2 * np.abs(2 * (t * f % 1) - 1) - 1
        else:
            s = np.sin(2 * np.pi * f * t)
        return s * np.exp(-t * decay) * env(len(t), 0.003, 0.02)

    def seq(*parts):
        return np.concatenate(parts)

    def mix(*parts):
        n = max(len(p) for p in parts)
        y = np.zeros(n)
        for p in parts:
            y[:len(p)] += p
        return y

    w("click", mix(tone(880, 0.06, 60), 0.4 * tone(1760, 0.04, 90)), 0.5)
    w("hover", tone(1320, 0.05, 70), 0.25)
    w("tick", tone(1600, 0.03, 120), 0.3)
    w("open", seq(tone(660, 0.07, 30), tone(990, 0.12, 25)), 0.4)
    w("close", seq(tone(880, 0.06, 35), tone(587, 0.1, 30)), 0.35)
    w("choice", mix(bell(1046, 0.6, 1), bell(1318, 0.6, 0.6)), 0.4)
    w("toast", seq(bell(1318, 0.12, 1), bell(1760, 0.5, 1)), 0.4)
    w("unlock", seq(*[bell(f, 0.12 if i < 3 else 0.8, 1) for i, f in enumerate([1046, 1318, 1568, 2093])]), 0.5)
    w("save", seq(bell(784, 0.1, 1), bell(1175, 0.5, 1)), 0.4)
    w("chapter", reverb(mix(*[np.pad(bell(f, 2.0, 1), (int(i * 0.18 * SR), 0)) for i, f in enumerate([523, 659, 784, 1046])]), 2.0, 0.4)[:, 0], 0.6)
    g = noise(0.45)
    g *= (np.sin(2 * np.pi * 37 * np.arange(len(g)) / SR) > 0)
    g = np.round(g * 4) / 4
    w("glitch", mix(g * 0.6, tone(110, 0.45, 4, "square")), 0.5)
    w("rumble", onepole(noise(0.9), 120) * np.exp(-np.arange(int(0.9 * SR)) / SR * 3), 0.7)
    w("switch", seq(tone(2000, 0.015, 200), np.zeros(800), tone(1500, 0.02, 200)), 0.4)
    w("twinkle", mix(bell(2093, 0.5, 1), bell(3136, 0.4, 0.5)), 0.35)
    w("star_connect", seq(bell(1568, 0.08, 1), bell(2093, 0.5, 1)), 0.4)
    w("star_complete", reverb(mix(*[np.pad(bell(f, 1.5, 1), (int(i * 0.09 * SR), 0)) for i, f in enumerate([1046, 1318, 1568, 2093, 2637])]), 1.5, 0.35)[:, 0], 0.55)
    w("fail", seq(tone(330, 0.12, 12, "tri"), tone(262, 0.22, 10, "tri")), 0.35)
    w("success", reverb(mix(*[np.pad(ep(f, 1.2, 1), (int(i * 0.12 * SR), 0)) for i, f in enumerate([523, 659, 784, 1046])]), 1.5, 0.3)[:, 0], 0.55)
    w("plop", tone(500, 0.12, 25) * np.linspace(1, 0.2, int(0.12 * SR)) + tone(250, 0.12, 30), 0.5)
    s = highpass(noise(0.9), 3000) * (0.6 + 0.4 * np.sin(2 * np.pi * 13 * np.arange(int(0.9 * SR)) / SR))
    w("sizzle", s * env(len(s), 0.05, 0.3), 0.35)
    w("pour_done", seq(onepole(noise(0.3), 1500) * 0.5, bell(1175, 0.3, 1)), 0.4)
    w("flip", mix(tone(300, 0.15, 15), highpass(noise(0.15), 2000) * np.exp(-np.arange(int(0.15 * SR)) / SR * 30) * 0.4), 0.5)
    w("stir", onepole(noise(0.25), 900) * env(int(0.25 * SR), 0.05, 0.1), 0.3)
    w("swap", seq(tone(700, 0.05, 40), tone(1050, 0.08, 40)), 0.35)
    w("place", mix(tone(180, 0.18, 20), tone(900, 0.06, 60)), 0.5)
    # «голоса»: мягкие короткие тона
    w("blip_kira", mix(tone(740, 0.045, 50), 0.3 * tone(1480, 0.03, 80)), 0.22)
    w("blip_low", tone(330, 0.05, 45, "tri"), 0.22)
    w("blip_high", tone(988, 0.04, 55), 0.2)
    w("blip_system", tone(1200, 0.035, 60, "square"), 0.12)
    door = onepole(noise(0.9), 400) * np.exp(-np.arange(int(0.9 * SR)) / SR * 4)
    w("door", mix(door, np.pad(bell(784, 1.2, 0.6), (int(0.3 * SR), 0))), 0.6)
    ring = np.concatenate([tone(1400, 0.05, 0) * (1 if (i // 2) % 2 == 0 else 0) for i in range(40)])
    ring = ring * (np.sin(2 * np.pi * 20 * np.arange(len(ring)) / SR) > 0)
    w("phone_ring", np.concatenate([ring, np.zeros(int(0.4 * SR)), ring]), 0.35)
    t = np.arange(int(0.5 * SR)) / SR
    f = 600 + 300 * np.sin(np.pi * t / 0.5)
    meow = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.sin(np.pi * t / 0.5) * (1 + 0.5 * np.sin(2 * np.pi * 2 * f * t))
    w("meow", onepole(meow, 2500), 0.4)
    w("page", highpass(noise(0.35), 1500) * np.sin(np.pi * np.arange(int(0.35 * SR)) / (0.35 * SR)) ** 2, 0.35)
    horn = mix(*[tone(fr, 1.4, 1.2, "tri") for fr in (392, 494, 587)])
    w("train_horn", onepole(horn, 1800), 0.5)


if __name__ == "__main__":
    import sys
    which = sys.argv[1:] or ["sfx", "amb", "music"]
    if "sfx" in which:
        sfx()
        print("sfx ok")
    if "amb" in which:
        ambience()
        print("amb ok")
    if "music" in which:
        for fn in (room, night, city, library, train, edge, ending, menu, minigame, dawn):
            fn()
            print(fn.__name__, "ok")
