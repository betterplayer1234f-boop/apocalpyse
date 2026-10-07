"""Generates Last Sunset textures (pure Python, no dependencies). Run from the project root."""
import math, os, random, struct, zlib

def png(path, w, h, px):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    raw = b"".join(b"\x00" + bytes(c for p in px[y*w:(y+1)*w] for c in p) for y in range(h))
    chunk = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))

def mcmeta(path):
    with open(path + ".mcmeta", "w") as f:
        f.write('{"texture": {"blur": true, "clamp": true}}\n')

c8 = lambda v: max(0, min(255, int(v * 255)))
random.seed(1337)
ENV = "src/client/resources/assets/lastsunset/textures/environment/"
GUI = "src/client/resources/assets/lastsunset/textures/gui/"

# Sun disk: white disk with limb darkening + granulation (tinted in-game via ColorModulator).
N = 128; noise = [[random.random() for _ in range(N)] for _ in range(N)]
px = []
for y in range(N):
    for x in range(N):
        dx, dy = (x + .5) / N * 2 - 1, (y + .5) / N * 2 - 1; r = math.hypot(dx, dy)
        if r > 1: px.append((0, 0, 0, 0)); continue
        limb = 0.55 + 0.45 * math.sqrt(max(0, 1 - r * r))
        g = 0.9 + 0.1 * noise[y][x]
        a = min(1, (1 - r) * 18)
        v = limb * g
        px.append((c8(v), c8(v * 0.97), c8(v * 0.92), c8(a)))
png(ENV + "sun_disk.png", N, N, px); mcmeta(ENV + "sun_disk.png")

# Corona: soft radial glow.
N = 128; px = []
for y in range(N):
    for x in range(N):
        r = math.hypot((x + .5) / N * 2 - 1, (y + .5) / N * 2 - 1)
        a = max(0, 1 - r) ** 2.2
        px.append((255, 255, 255, c8(a)))
png(ENV + "corona.png", N, N, px); mcmeta(ENV + "corona.png")

# Flare ring: angular flame tendrils around the disk.
N = 256; spikes = [random.uniform(0.3, 1.0) for _ in range(48)]
px = []
for y in range(N):
    for x in range(N):
        dx, dy = (x + .5) / N * 2 - 1, (y + .5) / N * 2 - 1; r = math.hypot(dx, dy)
        ang = (math.atan2(dy, dx) / (2 * math.pi) + 1) % 1 * len(spikes)
        i = int(ang); t = ang - i
        reach = 0.42 + 0.5 * (spikes[i] * (1 - t) + spikes[(i + 1) % len(spikes)] * t)
        a = 0 if r < 0.38 or r > reach else (1 - (r - 0.38) / (reach - 0.38)) ** 1.5
        px.append((255, 255, 255, c8(a * 0.9)))
png(ENV + "flares.png", N, N, px); mcmeta(ENV + "flares.png")

# Heat vignette: transparent centre, hot orange edges.
W, H = 256, 144; px = []
for y in range(H):
    for x in range(W):
        r = math.hypot((x + .5) / W * 2 - 1, (y + .5) / H * 2 - 1) / math.sqrt(2)
        a = max(0, (r - 0.35) / 0.65) ** 1.6
        px.append((255, c8(0.35 + 0.3 * (1 - a)), 30, c8(a)))
png(GUI + "heat_vignette.png", W, H, px); mcmeta(GUI + "heat_vignette.png")

# Mod icon: giant red sun setting over a black horizon.
N = 128; px = []
for y in range(N):
    for x in range(N):
        t = y / N
        sky = (0.35 + 0.5 * t, 0.08 + 0.15 * t, 0.05)
        r = math.hypot(x - 64, y - 78) / 46
        if r < 1: col = (1.0, 0.35 + 0.35 * (1 - r), 0.12)
        elif r < 1.5: k = (1.5 - r) / 0.5; col = tuple(s + (o - s) * k * 0.6 for s, o in zip(sky, (1, 0.4, 0.1)))
        else: col = sky
        if y > 96 + 4 * math.sin(x / 9): col = (0.04, 0.02, 0.02)
        px.append((c8(col[0]), c8(col[1]), c8(col[2]), 255))
png("src/main/resources/assets/lastsunset/icon.png", N, N, px)
print("textures generated")
