#!/usr/bin/env python3
"""
Génère ce qu'il faut voir de Tarentule b, une planète du Grand Nuage de Magellan, dans Orbitalis.

Tarentule b tourne autour d'une jeune étoile bleue, au bord de la Tarentule, la plus grande pouponnière d'étoiles connue
alentour. Son air est mince ; son ciel, presque noir, est rempli par notre Galaxie, vue enfin de l'extérieur, et par le
grand nuage rose de la Tarentule. Le sol est une poussière rose sur un basalte violet, d'où sortent des cristaux qui
luisent ; sa flore est faite de fils et de voiles lumineux.

- la roche de Tarentule b, un basalte violet sombre ; sa poussière, rose et grise ;
- le cristal de la Tarentule, rose, qui luit ;
- la flore : le fil-lueur et le voile rose ;
- dans le ciel, notre Galaxie et la Tarentule ; la planète vue d'orbite (des images de particule).

Usage : python tools/generate_tarantula.py
"""
import json
import math
import os
import random

from generate_textures import BLOCK_DIR, ROOT, blotchy, rgb, write_png

PARTICLE_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "orbitalis", "textures", "particle")
CLEAR = (0, 0, 0, 0)


def rock_texture():
    """Roche de Tarentule b : un basalte violet sombre, des éclats roses."""
    img = blotchy(601, [rgb("#3A2A44"), rgb("#32243C"), rgb("#44324E"), rgb("#2A1E32")], [5, 3, 2, 1], passes=2)
    for x, y in ((2, 3), (11, 6), (6, 12), (14, 10), (8, 1)):
        img[y][x] = rgb("#A0587E")
    return img


def dust_texture():
    """Poussière de Tarentule b : rose et grise, des grains sombres et des paillettes claires."""
    img = blotchy(602, [rgb("#9A7C88"), rgb("#8C6E7C"), rgb("#A88A96"), rgb("#7E6270")], [5, 3, 2, 1], passes=2)
    for x, y in ((3, 4), (12, 2), (7, 9), (14, 13), (1, 11)):
        img[y][x] = rgb("#4E3A46")
    for x, y in ((9, 5), (4, 14), (13, 8)):
        img[y][x] = rgb("#E8D0DC")
    return img


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def crystal_texture():
    """
    Cristal de la Tarentule : une facette tous les quatre pixels (son arête claire, sa face, son flanc dans l'ombre),
    d'un magenta profond au pied à un rose pâle vers la pointe, avec quelques reflets et un voile laiteux au cœur. Chaque
    face d'un prisme du modèle prend une tranche de quatre pixels (ou trois) : elle tombe toujours sur une facette entière.
    """
    columns = [rgb("#FFC2E2"), rgb("#F27CBA"), rgb("#D9559C"), rgb("#A8347A")]
    foot, tip = rgb("#5E1846"), rgb("#FFE6F3")
    rnd = random.Random(603)
    img = []
    for y in range(16):
        row = []
        # Plus clair vers le haut de la texture (la pointe), plus sombre au pied.
        t = y / 15.0
        for x in range(16):
            c = columns[x % 4]
            c = mix(c, tip, max(0.0, 0.45 - t * 0.6)) if t < 0.75 else mix(c, foot, (t - 0.75) * 1.6)
            row.append(c)
        img.append(row)
    # Le voile laiteux, au cœur des facettes : des taches un peu plus pâles, sans toucher les arêtes.
    for _ in range(14):
        x = rnd.randrange(16)
        y = rnd.randrange(2, 13)
        if x % 4 in (1, 2):
            img[y][x] = mix(img[y][x], rgb("#FFE0F0"), 0.35)
    # Des reflets, rares : un pixel presque blanc sur une arête claire.
    for x, y in ((0, 3), (4, 7), (8, 2), (12, 9), (4, 12), (0, 10)):
        img[y][x] = rgb("#FFF4FA")
    return img


def crystal_model():
    """
    Le cristal de la Tarentule en bouquet : un grand prisme droit au milieu, trois plus petits qui penchent autour,
    chacun terminé par une pointe en deux étages. Chaque face prend une tranche entière de la texture : les flancs, la
    partie basse (de plus en plus sombre vers le pied), les pointes, la partie haute (plus pâle). Les pièces luisent
    un peu d'elles-mêmes, comme le bloc.
    """
    # (x, z, largeur, hauteur du fût, rotation : axe, angle, origine)
    prisms = [
        (6, 6, 4, 9, None),
        (2, 7, 3, 6, ("z", 22.5, (5, 0, 8.5))),
        (9, 11, 3, 5, ("x", 22.5, (10.5, 0, 11))),
        (10, 2, 3, 4, ("x", -22.5, (11.5, 0, 5))),
    ]
    elements = []

    def box(lo, hi, rotation, uv_rows, top_face):
        w = hi[0] - lo[0]
        h = hi[1] - lo[1]
        v0, v1 = uv_rows
        side = {"uv": [0, v0, w, v1], "texture": "#crystal"}
        faces = {name: dict(side) for name in ("north", "south", "west", "east")}
        if top_face:
            faces["up"] = {"uv": [4, 0, 4 + w, w], "texture": "#crystal"}
        element = {"from": list(lo), "to": list(hi), "faces": faces, "light_emission": 9}
        if rotation is not None:
            axis, angle, origin = rotation
            element["rotation"] = {"angle": angle, "axis": axis, "origin": list(origin)}
        return element

    for x, z, w, h, rotation in prisms:
        # Le fût : la texture du pied jusqu'à mi-hauteur, selon sa taille ; dessous, rien (il sort de la poussière).
        shaft = box((x, 0, z), (x + w, h, z + w), rotation, (16 - min(h, 12), 16), True)
        shaft["faces"]["down"] = {"uv": [0, 12, w, 12 + w], "texture": "#crystal", "cullface": "down"}
        elements.append(shaft)
        # La pointe : un étage plus étroit, puis l'aiguille, dans la partie claire de la texture.
        inner = w - 2
        elements.append(box((x + 1, h, z + 1), (x + 1 + inner, h + inner + 1, z + 1 + inner), rotation, (1, 2 + inner), True))
        if inner >= 2:
            c = x + w / 2.0
            elements.append(box((c - 0.5, h + inner + 1, z + w / 2.0 - 0.5), (c + 0.5, h + inner + 3, z + w / 2.0 + 0.5),
                                rotation, (0, 2), True))
    return {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "textures": {"crystal": "orbitalis:block/tarantula_crystal", "particle": "orbitalis:block/tarantula_crystal"},
        "elements": elements,
    }


def plant(draw):
    img = [[CLEAR for _ in range(16)] for _ in range(16)]
    draw(img)
    return img


def glowthread(img):
    """Fil-lueur : de longs fils fins et souples, bleu pâle, qui luisent à leur pointe."""
    for x0, top, phase in ((3, 3, 0.0), (6, 1, 1.2), (9, 2, 2.4), (12, 4, 0.6), (7, 6, 1.8)):
        for y in range(top, 16):
            x = x0 + int(round(0.8 * math.sin(y * 0.5 + phase)))
            img[y][max(0, min(15, x))] = rgb("#5A7AB8") if y > top + 2 else rgb("#A8C8FF")
        img[top][max(0, min(15, x0))] = rgb("#E8F4FF")


def rose_veil(img):
    """Voile rose : de larges feuilles translucides, roses, tendues comme des voiles, nervurées de clair."""
    for cx, top, w in ((5, 6, 3), (10, 4, 3), (8, 9, 2)):
        for y in range(top, 16):
            spread = max(0, w - abs(y - (top + 4)) // 2)
            for dx in range(-spread, spread + 1):
                x = cx + dx
                if 0 <= x < 16:
                    img[y][x] = rgb("#E888B8") if (y + dx) % 3 else rgb("#C05890")
            img[y][cx] = rgb("#FFD0E8")
        for y in range(top + 6, 16):
            img[y][cx] = rgb("#7A3A5E")


def milky_way_texture(size=256):
    """Notre Galaxie, vue du Grand Nuage de Magellan : une spirale barrée, un bulbe doré, des bras bleutés semés de rose."""
    img = [[CLEAR for _ in range(size)] for _ in range(size)]
    rnd = random.Random(604)
    for y in range(size):
        for x in range(size):
            u = (x - size / 2 + 0.5) / (size / 2)
            v = (y - size / 2 + 0.5) / (size / 2) / 0.62
            r = math.hypot(u, v)
            if r > 1.0:
                continue
            theta = math.atan2(v, u)
            phase = theta - math.log(r + 0.05) * 2.6
            arm = (0.5 + 0.5 * math.cos(2 * phase)) ** 2.5
            bar = math.exp(-((u * 0.9 + v * 0.4) ** 2) / 0.05 - ((-u * 0.4 + v * 0.9) ** 2) / 0.006)
            bulge = math.exp(-r * r / 0.02)
            disk = math.exp(-r / 0.38) * (0.25 + 0.95 * arm)
            light = min(1.0, disk * 0.9 + bulge * 1.2 + bar * 0.6)
            if light < 0.04:
                continue
            core = min(1.0, bulge * 1.6 + bar)
            red = int(150 + 105 * core)
            green = int(170 + 50 * core)
            blue = int(255 - 90 * core)
            if arm > 0.75 and r > 0.25 and rnd.random() < 0.04:
                red, green, blue = 255, 120, 180
            alpha = int(255 * min(1.0, light * 1.1))
            img[y][x] = (min(255, red), min(255, green), min(255, blue), alpha)
    return img


def nebula_texture(size=192):
    """La Tarentule : un grand nuage rose aux filaments sombres, des amas d'étoiles bleues au cœur."""
    img = [[CLEAR for _ in range(size)] for _ in range(size)]
    rnd = random.Random(605)
    blobs = [(rnd.uniform(-0.5, 0.5), rnd.uniform(-0.5, 0.5), rnd.uniform(0.15, 0.35)) for _ in range(14)]
    for y in range(size):
        for x in range(size):
            u = (x - size / 2 + 0.5) / (size / 2)
            v = (y - size / 2 + 0.5) / (size / 2)
            density = 0.0
            for bx, by, br in blobs:
                density += math.exp(-((u - bx) ** 2 + (v - by) ** 2) / (br * br))
            density *= max(0.0, 1.0 - math.hypot(u, v)) ** 0.8
            filament = 0.5 + 0.5 * math.sin(u * 13 + math.sin(v * 9) * 2.0)
            density *= 0.55 + 0.45 * filament
            if density < 0.08:
                continue
            alpha = int(255 * min(0.85, density * 0.5))
            img[y][x] = (255, int(90 + 60 * filament), int(160 + 40 * filament), alpha)
    for _ in range(30):
        cx, cy = int(size / 2 + rnd.gauss(0, 12)), int(size / 2 + rnd.gauss(0, 12))
        if 0 <= cx < size and 0 <= cy < size:
            img[cy][cx] = (220, 236, 255, 255)
    return img


def planet_texture(size=160):
    """Tarentule b vue d'orbite : une poussière rose, des taches de roche violette, des champs de cristaux qui scintillent."""
    rnd = random.Random(606)
    cell = 2
    n = size // cell
    img = [[CLEAR for _ in range(size)] for _ in range(size)]
    patches = [(rnd.uniform(-0.8, 0.8), rnd.uniform(-0.8, 0.8), rnd.uniform(0.1, 0.25)) for _ in range(12)]
    for j in range(n):
        for i in range(n):
            x = (i + 0.5) / n * 2.0 - 1.0
            y = (j + 0.5) / n * 2.0 - 1.0
            r2 = x * x + y * y
            if r2 > 1.0:
                continue
            color = rgb("#9A7C88") if rnd.random() > 0.2 else rgb("#8C6E7C")
            for px, py, pr in patches:
                if math.hypot(x - px, y - py) < pr:
                    color = rgb("#44324E")
            if rnd.random() < 0.02:
                color = rgb("#FF88C0")
            light = 0.1 + 0.9 * max(0.0, min(1.0, (x + 0.4) / 1.1)) ** 1.3
            shade = (0.55 + 0.45 * math.sqrt(1.0 - r2)) * light
            c = tuple(min(255, int(channel * shade)) for channel in color[:3]) + (255,)
            for dy in range(cell):
                for dx in range(cell):
                    img[j * cell + dy][i * cell + dx] = c
    return img


def main():
    blocks = {
        "tarantula_rock": rock_texture(), "tarantula_dust": dust_texture(), "tarantula_crystal": crystal_texture(),
        "glowthread": plant(glowthread), "rose_veil": plant(rose_veil),
    }
    for name, img in blocks.items():
        write_png(os.path.join(BLOCK_DIR, name + ".png"), img)
    model_dir = os.path.join(ROOT, "src", "main", "resources", "assets", "orbitalis", "models", "block", "exploration")
    with open(os.path.join(model_dir, "tarantula_crystal.json"), "w", newline="\n") as f:
        json.dump(crystal_model(), f, indent=1)
        f.write("\n")
    particles = {"milky_way": milky_way_texture(), "tarantula_nebula": nebula_texture(), "tarantula_b": planet_texture()}
    for name, img in particles.items():
        write_png(os.path.join(PARTICLE_DIR, name + ".png"), img)
    print("Tarentule b : %d textures de blocs et %d images du ciel écrites." % (len(blocks), len(particles)))


if __name__ == "__main__":
    main()
