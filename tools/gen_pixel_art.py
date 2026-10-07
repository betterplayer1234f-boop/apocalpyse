"""Procedural 16x16 pixel art for Last Sunset's blocks and items (pure Python). Run from the project root."""
import math, os, random, struct, zlib

BLOCK = "src/main/resources/assets/lastsunset/textures/block/"
ITEM = "src/main/resources/assets/lastsunset/textures/item/"
T = (0, 0, 0, 0)


def save(path, frames, mcmeta=None):
    """frames: list of 16x16 images (rows of RGBA tuples), stacked vertically when animated."""
    os.makedirs(os.path.dirname(path), exist_ok=True)
    rows = [row for f in frames for row in f]
    w, h = len(rows[0]), len(rows)
    raw = b"".join(b"\x00" + bytes(c for px in row for c in px) for row in rows)
    chunk = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    if mcmeta:
        with open(path + ".mcmeta", "w") as f:
            f.write(mcmeta + "\n")


def img(fill=T):
    return [[fill for _ in range(16)] for _ in range(16)]


def clamp(v):
    return max(0, min(255, int(v)))


def shade(c, d):
    return (clamp(c[0] + d), clamp(c[1] + d), clamp(c[2] + d), 255)


def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def noisy(base, amount, rnd):
    return shade(base, rnd.randint(-amount, amount))


def line(im, x0, y0, x1, y1, c):
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        if 0 <= x0 < 16 and 0 <= y0 < 16:
            im[y0][x0] = c
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


ANIM_SLOW = '{"animation": {"frametime": 8, "interpolate": true}}'
ANIM_PULSE = '{"animation": {"frametime": 4, "interpolate": true}}'
ANIM_BLINK = '{"animation": {"frametime": 12}}'


# ---------------------------------------------------------------- blocks

def ash_block():
    r = random.Random(11)
    im = img()
    for y in range(16):
        for x in range(16):
            v = r.random()
            if v < 0.12:
                im[y][x] = noisy((74, 72, 70), 8, r)
            elif v < 0.18:
                im[y][x] = noisy((196, 194, 190), 6, r)
            else:
                im[y][x] = noisy((140, 136, 132), 14, r)
    save(BLOCK + "ash_layer.png", [im])


def charred_log():
    r = random.Random(5)
    base = [[None] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            groove = x % 4 == 0 or (x % 4 == 1 and r.random() < 0.3)
            base[y][x] = noisy((15, 12, 10) if groove else (34, 29, 24), 6, r)
    cracks = []
    for _ in range(5):
        x, y = r.randrange(0, 16, 4), r.randrange(16)
        phase = r.random() * math.tau
        for k in range(r.randint(2, 4)):
            cracks.append(((x + (1 if r.random() < 0.3 else 0)) % 16, (y + k) % 16, phase))
    frames = []
    for f in range(4):
        im = [row[:] for row in base]
        for x, y, phase in cracks:
            i = 0.5 + 0.5 * math.sin(phase + f * math.pi / 2)
            im[y][x] = lerp((110, 26, 4), (255, 150, 30), i)
        frames.append(im)
    save(BLOCK + "charred_log.png", frames, ANIM_SLOW)

    top = img()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d > 7.0:
                top[y][x] = noisy((18, 15, 12), 4, r)
            elif d < 1.6:
                top[y][x] = (210, 90, 18, 255)
            else:
                top[y][x] = noisy((44, 37, 30) if int(d * 1.3) % 2 else (26, 22, 18), 4, r)
    save(BLOCK + "charred_log_top.png", [top])


def scorched_soil():
    r = random.Random(7)
    im = img()
    for y in range(16):
        for x in range(16):
            im[y][x] = noisy((60, 43, 33), 10, r)
    for _ in range(3):
        x, y = r.randrange(16), r.randrange(16)
        for _ in range(7):
            im[y % 16][x % 16] = noisy((24, 17, 12), 3, r)
            x += r.choice((-1, 0, 1))
            y += r.choice((0, 1))
        im[y % 16][x % 16] = (196, 74, 20, 255)
    save(BLOCK + "scorched_soil.png", [im])


def solarite_ore():
    r = random.Random(9)
    bg = [[noisy((58, 57, 62), 9, r) if r.random() > 0.15 else noisy((38, 38, 42), 5, r) for _ in range(16)] for _ in range(16)]
    crystals = []
    for cx, cy in ((4, 4), (11, 6), (6, 11), (12, 12)):
        for dx, dy, layer in ((0, 0, 0), (1, 0, 1), (-1, 0, 1), (0, 1, 1), (0, -1, 1), (1, 1, 2), (-1, -1, 2)):
            crystals.append((cx + dx, cy + dy, layer))
    frames = []
    for f in range(8):
        pulse = 0.72 + 0.28 * math.sin(f / 8 * math.tau)
        im = [row[:] for row in bg]
        for x, y, _ in crystals:
            for gx in (-1, 0, 1):
                for gy in (-1, 0, 1):
                    px, py = x + gx, y + gy
                    if 0 <= px < 16 and 0 <= py < 16:
                        im[py][px] = lerp(im[py][px], (180, 90, 20), 0.18 * pulse)
        for x, y, layer in crystals:
            c = [(255, 236, 150), (255, 172, 40), (205, 92, 12)][layer]
            im[y][x] = shade(c, -60 * (1 - pulse))
        frames.append(im)
    save(BLOCK + "solarite_ore.png", frames, ANIM_PULSE)


def flare_shielding():
    r = random.Random(3)
    im = img()
    for y in range(16):
        for x in range(16):
            im[y][x] = noisy((64, 68, 76), 5, r)
    for i in range(1, 15):
        im[1][i] = shade(im[1][i], 22)
        im[i][1] = shade(im[i][1], 22)
    for i in range(16):
        im[0][i] = im[15][i] = im[i][0] = im[i][15] = (34, 36, 42, 255)
        im[7][i] = (150, 98, 28, 255)
        im[8][i] = (110, 66, 16, 255) if i % 3 else (255, 180, 60, 255)
    for x, y in ((3, 3), (12, 3), (3, 11), (12, 11)):
        im[y][x] = (168, 172, 182, 255)
        im[y + 1][x + 1] = (30, 32, 38, 255)
    save(BLOCK + "flare_shielding.png", [im])


def radio():
    r = random.Random(21)
    casing = (78, 86, 60)

    def body():
        im = img()
        for y in range(16):
            for x in range(16):
                im[y][x] = noisy(casing, 4, r)
        return im

    frames = []
    for lit in (True, False):
        im = body()
        for x in range(2, 14):
            im[7][x] = shade(casing, 22)
            im[15][x] = shade(casing, -26)
        for y in range(7, 16):
            im[y][2] = shade(casing, 12)
            im[y][13] = shade(casing, -22)
        for y in range(9, 15):
            for x in range(3, 8):
                im[y][x] = (28, 30, 24, 255) if (x + y) % 2 == 0 else shade(casing, -12)
        for y in range(9, 12):
            for x in range(9, 13):
                im[y][x] = (222, 212, 172, 255)
        for y in range(9, 12):
            im[y][10] = (190, 40, 30, 255)
        im[13][9] = im[13][12] = (34, 34, 34, 255)
        im[12][9] = im[12][12] = (120, 120, 120, 255)
        im[8][12] = (255, 52, 36, 255) if lit else (92, 24, 18, 255)
        frames.append(im)
    save(BLOCK + "emergency_radio_front.png", frames, ANIM_BLINK)

    side = body()
    for y in range(16):
        side[y][0] = noisy((150, 152, 158), 6, r)
    for y in range(7, 16):
        side[y][8] = shade(casing, -16)
    side[9][6] = side[13][6] = (140, 140, 130, 255)
    save(BLOCK + "emergency_radio_side.png", [side])

    top = body()
    for x in range(4, 12):
        top[7][x] = (40, 42, 36, 255)
        top[8][x] = shade(casing, 18)
    save(BLOCK + "emergency_radio_top.png", [top])


# ---------------------------------------------------------------- items

def heliometer():
    cx, cy = 7.5, 7.0
    for k in range(8):
        im = img()
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - cx, y - cy)
                if d <= 6.6:
                    light = 0.5 + 0.5 * ((cx - x) + (cy - y)) / 9
                    im[y][x] = lerp((122, 88, 30), (226, 184, 82), light)
                if d <= 5.2:
                    im[y][x] = (236, 232, 218, 255)
        for i in range(13):
            a = math.pi - i / 12 * math.pi
            t = i / 12
            c = lerp((60, 180, 70), (232, 200, 40), t * 2) if t < 0.5 else lerp((232, 200, 40), (222, 46, 36), (t - 0.5) * 2)
            x, y = round(cx + math.cos(a) * 4.2), round(cy - math.sin(a) * 4.2)
            im[y][x] = c
        a = math.pi - k / 7 * math.pi
        line(im, 7, 7, round(cx + math.cos(a) * 3.6), round(cy - math.sin(a) * 3.6), (32, 30, 28, 255))
        im[7][7] = (96, 92, 88, 255)
        for y in (13, 14):
            im[y][7] = (150, 112, 44, 255)
            im[y][8] = (100, 72, 26, 255)
        im[15][7] = im[15][8] = (60, 44, 18, 255)
        save(ITEM + "heliometer_%d.png" % k, [im])


def inside(poly, x, y):
    hit = False
    for i in range(len(poly)):
        (x1, y1), (x2, y2) = poly[i], poly[(i + 1) % len(poly)]
        if (y1 > y) != (y2 > y) and x < x1 + (y - y1) * (x2 - x1) / (y2 - y1):
            hit = not hit
    return hit


def solarite_shard():
    im = img()
    # A faceted crystal pointing up and to the right.
    poly = [(2.5, 13.5), (4.0, 9.5), (11.0, 2.0), (14.0, 2.5), (13.0, 6.0), (6.5, 13.0)]
    for y in range(16):
        for x in range(16):
            if inside(poly, x + 0.5, y + 0.5):
                # Facet split along the long axis: lit upper-left face, darker lower-right face.
                lit = (x + 0.5) - (y + 0.5) * 0.15 < 8.2 - (y + 0.5) * 0.85 + 4.5
                im[y][x] = (255, 176, 52, 255) if lit else (226, 112, 18, 255)
    for y in range(16):
        for x in range(16):
            if im[y][x][3] and any(not (0 <= x + dx < 16 and 0 <= y + dy < 16) or not im[y + dy][x + dx][3]
                                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                im[y][x] = (140, 52, 6, 255)
    for x, y in ((5, 9), (6, 8), (7, 7), (8, 6), (9, 5), (10, 4)):
        im[y][x] = (255, 232, 160, 255)
    im[3][12] = (255, 250, 220, 255)
    im[1][4] = im[14][12] = (255, 236, 170, 255)
    save(ITEM + "solarite_shard.png", [im])


def ash_item():
    r = random.Random(4)
    im = img()
    for y in range(9, 14):
        half = (y - 8) * 1.4
        for x in range(16):
            if abs(x - 7.5) <= half:
                im[y][x] = noisy((150, 146, 142) if r.random() > 0.2 else (90, 88, 86), 12, r)
    for x, y in ((5, 6), (10, 4), (8, 7), (3, 8)):
        im[y][x] = (176, 172, 168, 255)
    save(ITEM + "ash.png", [im])


def ration():
    im = img()
    for y in range(4, 14):
        for x in range(4, 12):
            im[y][x] = lerp((70, 80, 46), (124, 136, 84), 1 - abs(x - 6.5) / 5)
    for x in range(4, 12):
        im[3][x] = (186, 186, 180, 255)
        im[4][x] = (140, 140, 134, 255)
        im[13][x] = (100, 100, 96, 255)
        for y in (7, 8, 9):
            im[y][x] = (228, 216, 180, 255)
    for x in (6, 7, 9):
        im[8][x] = (170, 30, 26, 255)
    save(ITEM + "emergency_ration.png", [im])


def flare_gun():
    im = img()
    for y in range(5, 9):
        for x in range(2, 13):
            im[y][x] = lerp((250, 128, 30), (200, 84, 14), (y - 5) / 3)
    for y in range(5, 9):
        im[y][2] = (150, 54, 10, 255)
        im[y][3] = (60, 40, 30, 255)
    for y in range(9, 14):
        for x in range(9, 12):
            im[y][x] = lerp((176, 70, 16), (120, 44, 10), (y - 9) / 4)
    im[9][7] = im[10][7] = im[10][8] = (54, 38, 30, 255)
    im[4][11] = im[4][12] = (60, 40, 30, 255)
    for x in range(4, 12):
        im[5][x] = (255, 176, 84, 255)
    save(ITEM + "flare_gun.png", [im])


for fn in (ash_block, charred_log, scorched_soil, solarite_ore, flare_shielding, radio,
           heliometer, solarite_shard, ash_item, ration, flare_gun):
    fn()
print("pixel art generated")
