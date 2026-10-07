"""Synthesizes Zero Hour's sound effects as mono OGG Vorbis files.

Requires numpy and soundfile:  pip install numpy soundfile
Run from the project root:      python tools/gen_sounds.py [name ...]   (no names = all)
"""
import os
import sys

import numpy as np
import soundfile as sf

SR = 44100
OUT = "src/main/resources/assets/zerohour/sounds/"
rng = np.random.default_rng(1)


def seconds(sec):
    return np.arange(int(SR * sec)) / SR


def normalize(x, peak=0.85):
    return x / (np.max(np.abs(x)) + 1e-9) * peak


def envelope(n, attack, release):
    e = np.ones(n)
    a, r = int(attack * SR), int(release * SR)
    if a:
        e[:a] = np.linspace(0, 1, a)
    if r:
        e[-r:] *= np.linspace(1, 0, r)
    return e


def spectral(x, gain):
    """Filter by multiplying the spectrum with gain(freqs)."""
    spec = np.fft.rfft(x)
    freqs = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(spec * gain(freqs), len(x))


def lowpass(x, cutoff, order=2):
    return spectral(x, lambda f: 1 / np.sqrt(1 + (f / cutoff) ** (2 * order)))


def highpass(x, cutoff, order=2):
    return spectral(x, lambda f: 1 / np.sqrt(1 + (cutoff / np.maximum(f, 1e-3)) ** (2 * order)))


def bandpass(x, lo, hi):
    return highpass(lowpass(x, hi), lo)


def osc(freq):
    """Phase-continuous oscillator phase for a per-sample frequency array."""
    return 2 * np.pi * np.cumsum(freq) / SR


def echo(x, delay, decay, taps):
    out = x.copy()
    d = int(delay * SR)
    for k in range(1, taps + 1):
        shifted = np.zeros_like(x)
        shifted[k * d:] = x[:-k * d] * decay ** k
        out += shifted
    return out


def noise(n):
    return rng.standard_normal(n)


def brown(n):
    b = np.cumsum(noise(n))
    b -= np.convolve(b, np.ones(2048) / 2048, mode="same")
    return b


def save(name, x, peak=0.85):
    os.makedirs(OUT, exist_ok=True)
    sf.write(OUT + name + ".ogg", normalize(x, peak).astype(np.float32), SR, format="OGG", subtype="VORBIS")
    print("  %-18s %5.1f s" % (name, len(x) / SR))


# ---------------------------------------------------------------- sounds

def siren():
    t = seconds(10.0)
    f = np.where(t < 3.5, 180 + 440 * (1 - np.exp(-t * 1.2)) / (1 - np.exp(-4.2)),
                 np.where(t < 5.5, 620 + 6 * np.sin(t * 9), 150 + 470 * np.exp(-(t - 5.5) * 0.65)))
    phase = osc(f)
    tone = sum(np.sin(k * phase + k * 0.7) / k ** 1.2 for k in range(1, 9))
    tone = np.tanh(1.6 * tone)
    x = lowpass(tone, 3500) * envelope(len(t), 0.3, 1.5)
    save("siren", echo(x, 0.19, 0.38, 4))


def eas_tone():
    parts = []
    baud = 520.83
    for _ in range(3):
        bits = rng.integers(0, 2, int(0.45 * baud))
        f = np.repeat(np.where(bits == 1, 2083.3, 1562.5), int(SR / baud))
        parts.append(np.sin(osc(f)) * 0.55)
        parts.append(np.zeros(int(0.25 * SR)))
    parts.append(np.zeros(int(0.1 * SR)))
    t = seconds(2.6)
    parts.append((np.sin(2 * np.pi * 853 * t) + np.sin(2 * np.pi * 960 * t)) * 0.5 * envelope(len(t), 0.01, 0.05))
    save("eas_tone", np.concatenate(parts), 0.7)


def radio_static():
    n = int(2.6 * SR)
    hiss = bandpass(noise(n), 400, 4200)
    flutter = lowpass(noise(n), 6)
    flutter = 0.55 + 0.45 * (flutter - flutter.min()) / (np.ptp(flutter) + 1e-9)
    crackle = np.zeros(n)
    for i in rng.integers(0, n - 200, 90):
        crackle[i:i + 60] += rng.standard_normal(60) * np.exp(-np.arange(60) / 8) * rng.uniform(2, 6)
    save("radio_static", (hiss * flutter + highpass(crackle, 1500)) * envelope(n, 0.05, 0.3), 0.6)


def rumble():
    n = int(6.0 * SR)
    t = seconds(6.0)
    body = lowpass(brown(n), 90, 3)
    swell = 0.55 + 0.45 * np.sin(t * 1.7) * np.sin(t * 0.6 + 1)
    thumps = np.zeros(n)
    for start in (0.4, 2.1, 3.7):
        s = int(start * SR)
        k = np.arange(n - s) / SR
        thumps[s:] += np.sin(2 * np.pi * 52 * k) * np.exp(-k * 5) * 1.6
    save("rumble", (body / np.max(np.abs(body)) * swell + thumps) * envelope(n, 0.8, 2.0), 0.95)


def nuke_blast():
    n = int(10.0 * SR)
    t = seconds(10.0)
    crack = highpass(noise(n), 200) * np.exp(-t * 18)
    thump = np.sin(osc(55 * np.exp(-t * 0.2) + 18)) * np.exp(-t * 0.35)
    roar = lowpass(brown(n) + 0.25 * noise(n), 420, 2)
    roar = roar / np.max(np.abs(roar)) * np.exp(-t * 0.3) * np.minimum(1, t * 6)
    rolls = np.zeros(n)
    for start, amp in ((1.2, 0.8), (2.6, 0.6), (4.1, 0.45), (6.0, 0.3)):
        s = int(start * SR)
        k = np.arange(n - s) / SR
        seg = lowpass(noise(n - s), 160, 2)
        rolls[s:] += seg / np.max(np.abs(seg)) * np.exp(-k * 1.2) * amp
    debris = highpass(noise(n), 2500) * 0.08 * np.exp(-t * 0.5)
    save("nuke_blast", 1.6 * crack + 1.2 * thump + roar + 0.7 * rolls + debris, 0.95)


def geiger_click():
    t = seconds(0.06)
    x = noise(len(t)) * np.exp(-t / 0.0015) + 0.6 * np.sin(2 * np.pi * 3200 * t) * np.exp(-t / 0.004)
    save("geiger_click", x, 0.8)


def flare_gun():
    n = int(2.0 * SR)
    t = seconds(2.0)
    pop = highpass(noise(n), 900) * np.exp(-t / 0.012) * 2.5 + np.sin(2 * np.pi * 120 * t) * np.exp(-t / 0.03)
    whoosh = np.zeros(n)
    hop, size = 1024, 4096
    src = noise(n + size)
    for start in range(0, n, hop):
        centre = 800 + 2200 * min(1, start / (1.2 * SR))
        seg = src[start:start + size] * np.hanning(size)
        seg = spectral(seg, lambda f: np.exp(-((f - centre) / (centre * 0.35)) ** 2))
        end = min(n, start + size)
        whoosh[start:end] += seg[:end - start]
    whoosh *= np.exp(-t * 1.6) * 0.6
    fizz = highpass(noise(n), 4000) * 0.15 * np.exp(-t * 1.2)
    save("flare_gun", pop + whoosh + fizz, 0.85)


def shell_whistle():
    n = int(4.5 * SR)
    t = seconds(4.5)
    f = 1700 * np.exp(-t * 0.42) + 200 + 25 * np.sin(t * 30)
    tone = np.sin(osc(f)) + 0.3 * np.sin(2 * osc(f))
    air = bandpass(noise(n), 300, 3000) * 0.35
    loud = np.minimum(1, (t / 4.5) ** 1.5 * 1.3) * envelope(n, 0.2, 0.15)
    save("shell_whistle", (tone * 0.5 + air) * loud, 0.8)


def wind():
    loop, tail = 8.0, 1.0
    n, nt = int((loop + tail) * SR), int(tail * SR)
    t = seconds(loop + tail)
    src = noise(n + 4096)
    out = np.zeros(n)
    hop, size = 1024, 4096
    for start in range(0, n, hop):
        tt = start / SR
        centre = 380 + 220 * np.sin(2 * np.pi * tt / loop) + 120 * np.sin(2 * np.pi * tt * 3 / loop + 1)
        seg = src[start:start + size] * np.hanning(size)
        seg = spectral(seg, lambda f: np.exp(-((f - centre) / (centre * 0.45)) ** 2) + 0.15 / (1 + (f / 200) ** 2))
        end = min(n, start + size)
        out[start:end] += seg[:end - start]
    gust = 0.65 + 0.35 * np.sin(2 * np.pi * t * 2 / loop) ** 2
    out *= gust
    body = out[:-nt].copy()
    fade = np.linspace(0, 1, nt)
    body[:nt] = body[:nt] * fade + out[-nt:] * (1 - fade)
    save("wind", body, 0.7)


def jet_flyby():
    n = int(6.0 * SR)
    t = seconds(6.0)
    d = t - 2.6
    # Loudest at the closest approach; everything drops in pitch as it passes (Doppler).
    loud = 0.8 / (1 + (d / 0.55) ** 2) + 0.2 * np.exp(-np.abs(d) / 2.0)
    shift = 1.0 - 0.35 * np.tanh(d / 0.6)
    whine = np.sin(osc(1900 * shift)) * 0.25 + np.sin(osc(3800 * shift)) * 0.08
    roar = np.zeros(n)
    src = noise(n + 4096)
    hop, size = 1024, 4096
    for start in range(0, n, hop):
        centre = 700 * shift[min(start, n - 1)]
        seg = src[start:start + size] * np.hanning(size)
        seg = spectral(seg, lambda f: np.exp(-((f - centre) / (centre * 0.9)) ** 2) + 0.5 / (1 + (f / 150) ** 2))
        end = min(n, start + size)
        roar[start:end] += seg[:end - start]
    roar /= np.max(np.abs(roar)) + 1e-9
    rumble = lowpass(brown(n), 120, 2)
    rumble /= np.max(np.abs(rumble)) + 1e-9
    tail = np.exp(-np.maximum(0, d) / 1.6)
    x = (roar + whine) * loud + rumble * 0.5 * loud * tail
    save("jet_flyby", x * envelope(n, 0.05, 0.6), 0.9)


def helicopter():
    n = int(4.0 * SR)
    t = seconds(4.0)
    phase = (t * 5.2) % 1.0
    slap_env = np.exp(-phase / 0.06)
    slap = lowpass(noise(n), 900, 2)
    slap /= np.max(np.abs(slap))
    thump = np.sin(2 * np.pi * 70 * t) * np.exp(-phase / 0.05)
    body = lowpass(brown(n), 300, 2)
    body /= np.max(np.abs(body))
    whine = np.sin(osc(np.full(n, 1150.0))) * 0.05 + np.sin(osc(np.full(n, 2300.0))) * 0.02
    x = slap * slap_env * 0.9 + thump * 0.8 + body * (0.35 + 0.25 * slap_env) + whine
    save("helicopter", x * envelope(n, 0.3, 0.6), 0.85)


ALL = (siren, eas_tone, radio_static, rumble, nuke_blast, geiger_click, flare_gun, shell_whistle, wind, jet_flyby, helicopter)

if __name__ == "__main__":
    wanted = set(sys.argv[1:])
    print("synthesizing sounds:")
    for fn in ALL:
        if not wanted or fn.__name__ in wanted:
            fn()
