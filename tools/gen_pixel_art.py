"""Procedural 16x16 pixel art for Zero Hour's blocks and items (pure Python). Run from the project root."""
import math, os, random, struct, zlib

BLOCK = "src/main/resources/assets/zerohour/textures/block/"
ITEM = "src/main/resources/assets/zerohour/textures/item/"
EQUIPMENT = "src/main/resources/assets/zerohour/textures/entity/equipment/"
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

def fallout_block():
    # Grey ash shot through with sickly yellow-green specks.
    r = random.Random(11)
    im = img()
    for y in range(16):
        for x in range(16):
            v = r.random()
            if v < 0.10:
                im[y][x] = noisy((70, 72, 64), 8, r)
            elif v < 0.16:
                im[y][x] = noisy((172, 196, 96), 10, r)
            elif v < 0.20:
                im[y][x] = noisy((196, 194, 186), 6, r)
            else:
                im[y][x] = noisy((128, 130, 120), 12, r)
    save(BLOCK + "fallout.png", [im])


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


def blast_shielding():
    # Reinforced concrete in a riveted steel frame, with a hazard band across the middle.
    r = random.Random(3)
    im = img()
    for y in range(16):
        for x in range(16):
            im[y][x] = noisy((118, 118, 114), 7, r) if r.random() > 0.08 else noisy((88, 88, 86), 5, r)
    for i in range(16):
        im[0][i] = im[15][i] = im[i][0] = im[i][15] = (54, 58, 66, 255)
        im[1][i] = shade(im[1][i], 18) if 0 < i < 15 else im[1][i]
        im[i][1] = shade(im[i][1], 18) if 0 < i < 15 else im[i][1]
    for x in range(1, 15):
        stripe = ((x + 6) // 2) % 2 == 0
        im[7][x] = (232, 186, 36, 255) if stripe else (30, 30, 32, 255)
        im[8][x] = (232, 186, 36, 255) if not stripe else (30, 30, 32, 255)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        im[y][x] = (176, 180, 188, 255)
        im[y + 1 if y < 15 else y][x] = (40, 42, 48, 255)
    save(BLOCK + "blast_shielding.png", [im])


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

def geiger_counter():
    # A yellow civil-defence survey meter: dial window, needle, carrying handle and a probe on a cable.
    cx, cy = 7.5, 9.0
    outline = (58, 44, 10, 255)
    for k in range(8):
        im = img()
        for y in range(5, 15):
            for x in range(1, 13):
                light = 0.55 + 0.45 * ((13 - x) + (15 - y)) / 22
                im[y][x] = lerp((176, 128, 18), (246, 206, 64), light)
        for x in range(1, 13):
            im[5][x] = im[14][x] = outline
        for y in range(5, 15):
            im[y][1] = im[y][12] = outline
        # carrying handle
        for x in range(3, 11):
            im[3][x] = (60, 60, 62, 255)
        im[4][3] = im[4][10] = (60, 60, 62, 255)
        # dial window
        for y in range(6, 11):
            for x in range(3, 11):
                im[y][x] = (236, 232, 218, 255)
        for i in range(9):
            a = math.pi - i / 8 * math.pi
            t = i / 8
            c = lerp((60, 180, 70), (232, 200, 40), t * 2) if t < 0.5 else lerp((232, 200, 40), (222, 46, 36), (t - 0.5) * 2)
            x, y = round(cx + math.cos(a) * 3.3), round(cy - math.sin(a) * 3.0)
            if 6 <= y <= 10 and 3 <= x <= 10:
                im[y][x] = c
        a = math.pi - k / 7 * math.pi
        line(im, 7, 9, round(cx + math.cos(a) * 2.6), round(cy - math.sin(a) * 2.6), (32, 30, 28, 255))
        im[10][7] = (96, 92, 88, 255)
        # radiation trefoil and knob
        im[12][4] = im[12][6] = im[13][5] = (30, 28, 24, 255)
        im[12][5] = (30, 28, 24, 255)
        im[12][9] = im[12][10] = (40, 40, 40, 255)
        im[13][9] = im[13][10] = (90, 90, 90, 255)
        # probe on its cable
        for y in range(7, 12):
            im[y][14] = (150, 150, 156, 255)
        im[6][14] = (80, 80, 84, 255)
        im[12][13] = im[13][14] = (40, 40, 42, 255)
        save(ITEM + "geiger_counter_%d.png" % k, [im])


def fallout_dust():
    r = random.Random(4)
    im = img()
    for y in range(9, 14):
        half = (y - 8) * 1.4
        for x in range(16):
            if abs(x - 7.5) <= half:
                v = r.random()
                im[y][x] = noisy((168, 190, 96), 10, r) if v < 0.15 else noisy((136, 136, 128) if v > 0.3 else (88, 90, 82), 12, r)
    for x, y in ((5, 6), (10, 4), (8, 7), (3, 8)):
        im[y][x] = (190, 214, 110, 255)
    save(ITEM + "fallout_dust.png", [im])


def outlined(im, color):
    for y in range(16):
        for x in range(16):
            if im[y][x][3] and any(not (0 <= x + dx < 16 and 0 <= y + dy < 16) or not im[y + dy][x + dx][3]
                                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                im[y][x] = color


YELLOW = (236, 194, 44)
YELLOW_DARK = (168, 124, 16)
RUBBER = (34, 34, 36)


def gas_mask():
    im = img()
    for y in range(3, 14):
        for x in range(3, 13):
            d = ((x - 7.5) / 5.0) ** 2 + ((y - 7.5) / 5.6) ** 2
            if d <= 1.0:
                im[y][x] = lerp((70, 70, 74), (28, 28, 30), (y - 3) / 10)
    for lx in (5, 10):
        for y in range(5, 9):
            for x in range(lx - 1, lx + 1):
                im[y][x] = (156, 196, 210, 255) if (x + y) % 3 else (220, 240, 246, 255)
        im[4][lx - 1] = im[4][lx] = (110, 110, 116, 255)
    # filter canister
    for y in range(10, 15):
        for x in range(6, 10):
            im[y][x] = lerp((120, 128, 70), (70, 76, 38), (x - 6) / 3)
    for x in range(6, 10):
        im[14][x] = (40, 42, 24, 255)
        im[12][x] = (150, 156, 96, 255)
    # straps
    im[6][2] = im[7][2] = im[6][13] = im[7][13] = (60, 50, 40, 255)
    outlined(im, (16, 16, 18, 255))
    save(ITEM + "gas_mask.png", [im])


def hazmat_suit():
    im = img()
    for y in range(2, 15):
        for x in range(2, 14):
            torso = 4 <= x <= 11
            sleeve = y <= 9 and (x <= 3 or x >= 12)
            if torso or sleeve:
                im[y][x] = lerp(YELLOW, YELLOW_DARK, (x - 2) / 14 + (y - 2) / 40)
    for x in range(6, 10):
        im[2][x] = T
    im[3][6] = im[3][9] = YELLOW_DARK + (255,)
    for y in range(3, 15):
        im[y][7] = RUBBER + (255,)  # zipper
    for x in (2, 3, 12, 13):
        im[9][x] = RUBBER + (255,)  # gloves at the cuffs
    for x in range(4, 12):
        im[11][x] = (120, 88, 10, 255)  # belt seam
    outlined(im, (90, 64, 6, 255))
    save(ITEM + "hazmat_suit.png", [im])


def hazmat_trousers():
    im = img()
    for y in range(2, 15):
        for x in range(3, 13):
            leg = y <= 4 or x <= 6 or x >= 9
            if leg:
                im[y][x] = lerp(YELLOW, YELLOW_DARK, (x - 3) / 12 + (y - 2) / 40)
    for x in range(3, 13):
        im[3][x] = (120, 88, 10, 255)
    for y in range(12, 15):
        for x in list(range(3, 7)) + list(range(9, 13)):
            im[y][x] = RUBBER + (255,)
    outlined(im, (90, 64, 6, 255))
    save(ITEM + "hazmat_trousers.png", [im])


def hazmat_boots():
    im = img()
    for y in range(5, 14):
        for x in list(range(2, 6)) + list(range(9, 13)):
            im[y][x] = lerp((58, 58, 62), RUBBER, (y - 5) / 8)
        if y >= 11:
            im[y][6] = im[y][13] = lerp((58, 58, 62), RUBBER, 0.6)
    for x in list(range(2, 7)) + list(range(9, 14)):
        im[13][x] = (18, 18, 20, 255)
        im[5][x] = YELLOW + (255,) if im[5][x][3] else im[5][x]
    outlined(im, (12, 12, 14, 255))
    save(ITEM + "hazmat_boots.png", [im])


def iodine_tablets():
    # A foil blister strip of white tablets.
    im = img()
    for y in range(3, 13):
        for x in range(2, 14):
            im[y][x] = lerp((214, 218, 226), (150, 156, 168), (x + y) / 26)
    for py in (5, 9):
        for px in (4, 7, 10):
            for dy in range(2):
                for dx in range(2):
                    im[py + dy][px + dx] = (250, 250, 246, 255) if dx + dy < 2 else (210, 210, 204, 255)
    for x in range(2, 14):
        im[12][x] = (40, 92, 170, 255)
    im[3][12] = im[3][13] = (40, 92, 170, 255)
    outlined(im, (96, 100, 112, 255))
    save(ITEM + "iodine_tablets.png", [im])


def syringe(name, liquid, liquid_dark):
    # An auto-injector lying diagonally: plunger bottom-left, needle top-right, liquid in a glass barrel.
    im = img()
    ax, ay = 1.5, 14.5
    root = math.sqrt(0.5)
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5 - ax, y + 0.5 - ay
            t = (px - py) * root   # distance along the pen
            s = (px + py) * root   # distance across it
            if t < 0 or t > 18.5:
                continue
            if t < 3.0:
                if abs(s) <= 1.2:
                    im[y][x] = (58, 60, 68, 255)  # plunger
            elif t < 4.6:
                if abs(s) <= 2.3:
                    im[y][x] = (110, 114, 124, 255)  # finger grip
            elif t < 14.0:
                if abs(s) <= 1.8:
                    edge = abs(s) > 1.0
                    label = 8.5 < t < 10.0
                    im[y][x] = (236, 240, 242, 255) if label else (200, 226, 236, 255) if edge and s < 0 else lerp(liquid, liquid_dark, (s + 1.8) / 3.6)
            elif t < 15.6:
                if abs(s) <= 1.0:
                    im[y][x] = (90, 94, 102, 255)  # tip
            elif abs(s) <= 0.5:
                im[y][x] = (210, 214, 220, 255)  # needle
    outlined(im, (30, 32, 36, 255))
    save(ITEM + name + ".png", [im])


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


# ---------------------------------------------------------------- worn hazmat gear (64x32 armour layers)

def box(im, u, v, w, h, d, faces):
    """Paints a model cuboid's UV regions. faces maps top/bottom/right/front/left/back to fn(x, y, fw, fh) -> colour."""
    regions = {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }
    for face, (fx, fy, fw, fh) in regions.items():
        paint = faces.get(face)
        if paint is None:
            continue
        for y in range(fh):
            for x in range(fw):
                c = paint(x, y, fw, fh)
                if c is not None:
                    im[fy + y][fx + x] = c


def hazmat_layers():
    r = random.Random(17)
    canvas = lambda: [[T for _ in range(64)] for _ in range(32)]

    def rubber_yellow(x, y, w, h):
        c = noisy(YELLOW, 6, r)
        return shade(c, -28) if x == 0 or x == w - 1 else c

    def hood(x, y, w, h):
        return shade(noisy(YELLOW, 5, r), -10 if y > 5 else 0)

    def mask_front(x, y, w, h):
        if y == 0:
            return noisy(YELLOW, 5, r)
        if y in (2, 3) and x in (1, 2, 5, 6):
            return (160, 200, 214, 255) if (x + y) % 2 else (214, 236, 242, 255)
        if y >= 5 and x in (3, 4):
            return (110, 118, 64, 255) if y < 7 else (64, 70, 34, 255)
        return noisy(RUBBER, 4, r)

    def body_front(x, y, w, h):
        if x in (3, 4) and y >= 1:
            return RUBBER + (255,)
        if y == 8:
            return (150, 110, 14, 255)
        return rubber_yellow(x, y, w, h)

    def arm(x, y, w, h):
        return noisy(RUBBER, 3, r) if y >= h - 3 else rubber_yellow(x, y, w, h)

    def boot(x, y, w, h):
        return noisy(RUBBER, 3, r) if y >= h - 4 else None

    def boot_end(x, y, w, h):
        return noisy(RUBBER, 3, r)

    top = canvas()
    box(top, 0, 0, 8, 8, 8, {"top": hood, "bottom": lambda *a: noisy(RUBBER, 3, r), "right": hood, "left": hood, "back": hood, "front": mask_front})
    box(top, 16, 16, 8, 12, 4, {"top": rubber_yellow, "bottom": rubber_yellow, "right": rubber_yellow, "left": rubber_yellow,
                                 "back": rubber_yellow, "front": body_front})
    box(top, 40, 16, 4, 12, 4, {"top": rubber_yellow, "bottom": lambda *a: noisy(RUBBER, 3, r), "right": arm, "left": arm, "back": arm, "front": arm})
    box(top, 0, 16, 4, 12, 4, {"bottom": boot_end, "right": boot, "left": boot, "back": boot, "front": boot})
    save(EQUIPMENT + "humanoid/hazmat.png", [top])

    def leg(x, y, w, h):
        c = rubber_yellow(x, y, w, h)
        return shade(c, -24) if y == 6 else c

    def waist(x, y, w, h):
        return (150, 110, 14, 255) if y == h - 4 else rubber_yellow(x, y, w, h) if y >= h - 4 else None

    legs = canvas()
    box(legs, 0, 16, 4, 12, 4, {"top": rubber_yellow, "bottom": rubber_yellow, "right": leg, "left": leg, "back": leg, "front": leg})
    box(legs, 16, 16, 8, 12, 4, {"right": waist, "left": waist, "back": waist, "front": waist})
    save(EQUIPMENT + "humanoid_leggings/hazmat.png", [legs])


for fn in (fallout_block, charred_log, scorched_soil, blast_shielding, radio,
           geiger_counter, fallout_dust, gas_mask, hazmat_suit, hazmat_trousers, hazmat_boots, iodine_tablets, ration, flare_gun,
           hazmat_layers):
    fn()
syringe("anti_rad_serum", (70, 120, 230), (30, 60, 150))
syringe("antiviral", (120, 220, 90), (50, 140, 40))
print("pixel art generated")
