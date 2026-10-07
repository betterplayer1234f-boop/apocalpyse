"""Generates Zero Hour's GUI texture and mod icon (pure Python, no dependencies). Run from the project root."""
import math, os, struct, zlib

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
GUI = "src/client/resources/assets/zerohour/textures/gui/"

# Vignette: transparent centre, white edges (tinted in-game: green for radiation, red for the infection, orange for fire).
W, H = 256, 144; px = []
for y in range(H):
    for x in range(W):
        r = math.hypot((x + .5) / W * 2 - 1, (y + .5) / H * 2 - 1) / math.sqrt(2)
        a = max(0, (r - 0.35) / 0.65) ** 1.6
        px.append((255, 255, 255, c8(a)))
png(GUI + "vignette.png", W, H, px); mcmeta(GUI + "vignette.png")

# Mod icon: a mushroom cloud over a black horizon, the sky lit orange by the fireball.
N = 128; px = []
def cloud(x, y):
    """0..1 density of the mushroom cloud at pixel (x, y)."""
    cap = ((x - 64) / 40) ** 2 + ((y - 40) / 19) ** 2
    ring = ((x - 64) / 33) ** 2 + ((y - 52) / 9) ** 2
    stem = abs(x - 64) < 7 + (y - 50) * 0.18 and 48 < y < 100
    base = ((x - 64) / 44) ** 2 + ((y - 98) / 8) ** 2
    lump = 0.12 * math.sin(x * 0.45) * math.sin(y * 0.5)
    return 1.0 if cap + lump < 1 or ring < 1 or stem or base < 1 else 0.0
for y in range(N):
    for x in range(N):
        t = y / N
        col = (0.55 + 0.4 * t, 0.2 + 0.25 * t, 0.08 + 0.05 * t)
        if cloud(x, y):
            glow = max(0.0, min(1.0, (y - 30) / 60))
            shadow = 0.28 + 0.06 * math.sin(x * 0.7) * math.sin(y * 0.9) + 0.12 * (y - 20) / 80
            col = (shadow + 0.55 * glow, shadow * 0.8 + 0.25 * glow, shadow * 0.6 + 0.05 * glow)
            if abs(x - 64) < 5 and 58 < y < 96:
                col = (1.0, 0.75 - (y - 58) / 120, 0.3)
        if y > 100 + 3 * math.sin(x / 9): col = (0.04, 0.03, 0.03)
        px.append((c8(col[0]), c8(col[1]), c8(col[2]), 255))
png("src/main/resources/assets/zerohour/icon.png", N, N, px)
print("textures generated")
