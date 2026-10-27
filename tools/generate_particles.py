#!/usr/bin/env python3
"""
Génère les particules d'Orbitalis : leurs images (8 x 8 pixels, dans le style du jeu) et les fichiers qui les
déclarent. Chaque particule a plusieurs images, parcourues au fil de sa vie : une volute de fumée qui se défait,
une flamme qui passe du blanc au rouge, une étincelle qui s'éteint.

Usage : python tools/generate_particles.py
"""
import json
import math
import os
import random

from generate_textures import ROOT, rgb, write_png

ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "orbitalis")
TEXTURE_DIR = os.path.join(ASSETS, "textures", "particle")
PARTICLE_DIR = os.path.join(ASSETS, "particles")
SIZE = 8
CLEAR = (0, 0, 0, 0)


def puff(seed, radius, colors, alpha, ragged=0.0):
    """
    Bouffée : un disque aux bords irréguliers, plus clair en haut à gauche (éclairé) et plus sombre en bas à droite.

    radius : rayon du disque, en pixels
    colors : teintes, de la plus claire à la plus sombre
    alpha  : opacité, de 0 à 255
    ragged : part des pixels du bord qui manquent (la bouffée se défait)
    """
    rnd = random.Random(seed)
    img = [[CLEAR for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = x - 3.5, y - 3.5
            distance = math.hypot(dx, dy) + rnd.uniform(-0.5, 0.5)
            if distance > radius:
                continue
            if distance > radius - 1.4 and rnd.random() < ragged:
                continue
            light = (dx + dy) / (2 * 3.5)
            index = min(len(colors) - 1, max(0, int((light + 1) / 2 * len(colors) + rnd.uniform(-0.4, 0.4))))
            r, g, b = rgb(colors[index])[:3]
            img[y][x] = (r, g, b, alpha)
    return img


def flame(radius, core, mid, edge):
    """Flamme : un cœur très clair, une couronne plus chaude, un liseré sombre."""
    img = [[CLEAR for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            distance = math.hypot(x - 3.5, y - 3.5)
            if distance <= radius * 0.45:
                img[y][x] = rgb(core)
            elif distance <= radius * 0.8:
                img[y][x] = rgb(mid)
            elif distance <= radius:
                img[y][x] = rgb(edge)[:3] + (200,)
    return img


def spark(color, long_arms):
    """Étincelle : un point incandescent, avec ou sans ses quatre branches."""
    img = [[CLEAR for _ in range(SIZE)] for _ in range(SIZE)]
    img[3][3] = img[3][4] = img[4][3] = img[4][4] = rgb("#FFFFFF")
    if long_arms:
        for x, y in ((2, 3), (5, 4), (3, 2), (4, 5), (1, 3), (6, 4), (3, 1), (4, 6)):
            img[y][x] = rgb(color)
    else:
        for x, y in ((2, 3), (5, 4), (3, 2), (4, 5)):
            img[y][x] = rgb(color)[:3] + (190,)
    return img


# Trame de dissolution : l'ordre dans lequel les pixels d'une volute s'effacent, régulier plutôt qu'au hasard.
BAYER = ((0, 8, 2, 10), (12, 4, 14, 6), (3, 11, 1, 9), (15, 7, 13, 5))


def billow(seed, size, fill, colors, alpha, ragged=0.0, holes=0.0):
    """
    Volute : plusieurs bouffées accolées, pour un nuage bosselé plutôt qu'un disque. Éclairée par le haut à gauche.

    size   : côté de l'image, en pixels
    fill   : part de l'image qu'occupe la volute, de 0 à 1
    colors : teintes, de la plus claire à la plus sombre
    alpha  : opacité, de 0 à 255
    ragged : part des pixels du bord qui manquent (la volute se défait)
    holes  : part de tous les pixels qui manquent, en trame régulière (la volute se dissout sans devenir transparente)
    """
    rnd = random.Random(seed)
    half = size / 2.0
    lobes = [(half, half, half * fill * 0.72)]
    for _ in range(5):
        angle = rnd.uniform(0, 2 * math.pi)
        distance = half * fill * rnd.uniform(0.35, 0.55)
        lobes.append((half + math.cos(angle) * distance, half + math.sin(angle) * distance, half * fill * rnd.uniform(0.35, 0.5)))
    img = [[CLEAR for _ in range(size)] for _ in range(size)]
    for y in range(size):
        for x in range(size):
            depth = max(radius - math.hypot(x + 0.5 - cx, y + 0.5 - cy) for cx, cy, radius in lobes)
            if depth < 0 or (depth < 1.3 and rnd.random() < ragged) or BAYER[y % 4][x % 4] / 16.0 < holes:
                continue
            # Ombrage par touffes de deux pixels : la volute a du modelé sans devenir un semis de points.
            grain = random.Random(seed * 7919 + (x // 2) * 131 + (y // 2)).uniform(-0.16, 0.16)
            light = (x + y) / (2.0 * size) + grain - min(0.25, depth / size)
            index = min(len(colors) - 1, max(0, int(light * len(colors) + 0.5)))
            r, g, b = rgb(colors[index])[:3]
            img[y][x] = (r, g, b, alpha)
    return img


def droplets(seed, color, light):
    """Paquet d'eau : quelques gouttes étirées par leur chute."""
    rnd = random.Random(seed)
    img = [[CLEAR for _ in range(SIZE)] for _ in range(SIZE)]
    for _ in range(6):
        x, y = rnd.randint(0, SIZE - 1), rnd.randint(0, SIZE - 4)
        for k in range(3):
            img[y + k][x] = rgb(light if k < 2 else color)[:3] + (245 - 40 * k,)
    return img


def haze(seed, size=16):
    """Voile de chaleur : quelques filets clairs, ondulés et presque transparents, qui déforment à peine ce qu'ils couvrent."""
    rnd = random.Random(seed)
    img = [[CLEAR for _ in range(size)] for _ in range(size)]
    for line in range(4):
        y0 = 2 + line * 4 + rnd.uniform(-1, 1)
        phase = rnd.uniform(0, 6.28)
        for x in range(1, size - 1):
            y = int(round(y0 + math.sin(x * 0.8 + phase) * 1.3))
            edge = min(x, size - 1 - x) / (size / 2.0)
            if 0 <= y < size:
                img[y][x] = (255, 244, 224, int(70 * edge))
                if y + 1 < size:
                    img[y + 1][x] = (255, 250, 240, int(30 * edge))
    return img


def flash(size=32):
    """Éclair d'allumage : des anneaux francs, du blanc au cœur à l'orange au bord, de plus en plus transparents."""
    half = size / 2.0
    rings = [(0.28, "#FFFFFF", 235), (0.5, "#FFF3B0", 175), (0.72, "#FFD27A", 110), (0.9, "#FFA94D", 60), (1.0, "#FF8A3D", 28)]
    img = [[CLEAR for _ in range(size)] for _ in range(size)]
    for y in range(size):
        for x in range(size):
            t = math.hypot(x + 0.5 - half, y + 0.5 - half) / half
            for limit, color, alpha in rings:
                if t < limit:
                    img[y][x] = rgb(color)[:3] + (alpha,)
                    break
    return img


SMOKE = ["#F2F2F0", "#DAD9D6", "#BDBCB8", "#9C9B97"]
# Fumée de lancement : éclairée par les flammes à sa naissance, puis brune, puis grise.
LIT_SMOKE = ["#FFEBCB", "#F3D2A6", "#D8B48C", "#B39373"]
BROWN_SMOKE = ["#D8CFC4", "#B9AC9E", "#968878", "#74685C"]
GREY_SMOKE = ["#C4C2BE", "#A3A19C", "#807E79", "#5F5D59"]
# Vapeur du déluge : d'un blanc pur, à peine bleutée dans l'ombre.
WHITE_STEAM = ["#FFFFFF", "#F1F6FA", "#DCE7F0", "#C2D2E0"]
FIRE = ["#FFFBE0", "#FFE27A", "#FFB23E", "#E86A1F"]
DYING_FIRE = ["#FFC865", "#F0872B", "#B5391E", "#5A1A12"]
DUST = ["#E4D6B8", "#CDBD9C", "#B3A283", "#96866A"]
DARK_SMOKE = ["#B5B4B0", "#96958F", "#77766F", "#5A5953"]
SOOT = ["#6A6964", "#55544F", "#403F3B", "#2E2D2A"]
MIST = ["#F4FCFF", "#DDF4FF", "#BFE6FA", "#9FD4F0"]
STEAM = ["#FFFFFF", "#F2F5F7", "#DDE3E8", "#C4CCD3"]

PARTICLES = {
    # Fumée des moteurs : huit images, d'une boule dense et claire à une volute grise, large et trouée.
    # Comme les volutes du pas de tir, elle est pleine : chaque pixel est opaque, et elle se dissout en trame.
    "exhaust_smoke": [billow(100 + k, 32, 0.6 + 0.05 * k, SMOKE if k < 4 else DARK_SMOKE, 255, 0.05 * k, 0.07 * max(0, k - 4))
                      for k in range(8)],
    # Flamme des moteurs : du blanc au sortir de la tuyère au rouge sombre en bout de jet.
    "engine_flame": [
        flame(3.6, "#FFFFFF", "#FFF3B0", "#FFD75A"),
        flame(3.4, "#FFF7D0", "#FFD75A", "#FFA63D"),
        flame(3.0, "#FFE27A", "#FFA63D", "#E86A1F"),
        flame(2.4, "#FFB23E", "#E86A1F", "#B5391E"),
    ],
    "spark": [spark("#FFE27A", True), spark("#FFB23E", True), spark("#E86A1F", False)],
    "cryo_mist": [puff(300 + k, 2.2 + 0.35 * k, MIST, 170 - 28 * k, 0.12 * k) for k in range(4)],
    "vapor": [puff(400 + k, 2.0 + 0.4 * k, STEAM, 190 - 30 * k, 0.12 * k) for k in range(4)],
    "soot": [puff(500 + k, 2.0 + 0.35 * k, SOOT, 215 - 26 * k, 0.1 * k) for k in range(5)],

    # --- Pas de tir ---------------------------------------------------------
    # Les volutes du pas de tir font deux à trois blocs de large : leurs images font 32 pixels, pour que leurs
    # pixels aient la taille de ceux des blocs. Elles sont pleines : chaque pixel est opaque, si bien qu'aucune ne
    # masque à tort celles qui sont derrière elle, même vues de l'intérieur du nuage. En fin de vie, elles se
    # dissolvent en trame au lieu de devenir transparentes.
    # Fumée de lancement : dorée par les flammes à sa naissance, puis brune, puis grise.
    "launch_smoke": [billow(600 + k, 32, 0.62 + 0.045 * k,
                            LIT_SMOKE if k < 2 else BROWN_SMOKE if k < 5 else GREY_SMOKE, 255, 0.05 * k, 0.07 * max(0, k - 4))
                     for k in range(8)],
    # Vapeur du déluge : blanche, qui gonfle vite et se défait en lambeaux.
    "launch_steam": [billow(700 + k, 32, 0.55 + 0.07 * k, WHITE_STEAM, 255, 0.08 * k, 0.08 * max(0, k - 3)) for k in range(6)],
    # Eau du déluge : des paquets de gouttes.
    "deluge_water": [droplets(800 + k, "#6FB4EE", "#E6F4FF") for k in range(3)],
    # Feu du carneau : des boules de feu qui roulent, du jaune vif au rouge sombre.
    "trench_fire": [billow(1000 + k, 32, 0.75 - 0.05 * k, FIRE if k < 3 else DYING_FIRE, 255, 0.08 * k, 0.2 * max(0, k - 2)) for k in range(5)],
    # Escarbilles : des éclats incandescents arrachés au carneau.
    "launch_ember": [spark("#FFE27A", False), spark("#FFB23E", False), spark("#E86A1F", False), spark("#B5391E", False)],
    # Poussière soulevée par le souffle.
    "launch_dust": [billow(1100 + k, 32, 0.55 + 0.07 * k, DUST, 255, 0.1 * k, 0.07 * k) for k in range(5)],
    # Voile de chaleur au-dessus du carneau.
    "heat_haze": [haze(1200 + k) for k in range(4)],
    # Éclair d'allumage.
    "launch_flash": [flash()],
}


def planet_texture(size=160):
    """
    La planète vue d'en haut : un disque d'océans, de continents, de calottes polaires et de nuages, plus sombre
    vers le bord, entouré du mince halo bleu de son atmosphère. Le dessin vient de quelques ondes superposées :
    il est le même à chaque génération.
    """
    rnd = random.Random(7)
    waves = [(rnd.uniform(1.5, 6.0), rnd.uniform(1.5, 6.0), rnd.uniform(0, 6.28), rnd.uniform(0.4, 1.0)) for _ in range(9)]
    clouds = [(rnd.uniform(3.0, 9.0), rnd.uniform(3.0, 9.0), rnd.uniform(0, 6.28), rnd.uniform(0.4, 1.0)) for _ in range(7)]

    def field(set_, u, v):
        total = sum(weight * math.sin(fx * u + fy * v + phase) * math.cos(fy * u - fx * v + phase * 0.7) for fx, fy, phase, weight in set_)
        return total / sum(w[3] for w in set_)

    def mix(a, b, t):
        t = max(0.0, min(1.0, t))
        return tuple(int(a[k] + (b[k] - a[k]) * t) for k in range(3))

    half = size / 2.0
    planet = half * 0.93
    img = [[CLEAR for _ in range(size)] for _ in range(size)]
    for y in range(size):
        for x in range(size):
            dx, dy = (x + 0.5 - half), (y + 0.5 - half)
            distance = math.hypot(dx, dy)
            if distance > half:
                continue
            if distance > planet:
                # Halo de l'atmosphère : il s'efface vers l'extérieur.
                fade = 1.0 - (distance - planet) / (half - planet)
                img[y][x] = (110, 180, 255, int(190 * fade * fade))
                continue
            u, v = dx / planet * 3.0, dy / planet * 3.0
            land = field(waves, u, v)
            if land > 0.22:
                color = mix((72, 132, 68), (150, 130, 84), (land - 0.22) * 4.0)
            elif land > 0.16:
                color = (196, 184, 132)
            else:
                color = mix((20, 62, 140), (46, 110, 190), (land + 0.6) * 1.1)
            # Calottes polaires, en haut et en bas du disque.
            if abs(dy) / planet > 0.86 + 0.05 * math.sin(u * 4.0):
                color = (236, 244, 250)
            cloud = field(clouds, u + 11.0, v - 5.0)
            if cloud > 0.3:
                color = mix(color, (250, 252, 255), min(1.0, (cloud - 0.3) * 4.0) * 0.85)
            # Assombrissement vers le bord, et fine frange bleue de l'atmosphère.
            rim = distance / planet
            color = mix(color, (60, 120, 210), max(0.0, rim - 0.86) * 4.5)
            shade = 1.0 - 0.45 * rim ** 6
            img[y][x] = tuple(int(c * shade) for c in color) + (255,)
    return img


if __name__ == "__main__":
    os.makedirs(TEXTURE_DIR, exist_ok=True)
    os.makedirs(PARTICLE_DIR, exist_ok=True)
    count = 0
    for name, frames in PARTICLES.items():
        names = []
        for index, frame in enumerate(frames):
            write_png(os.path.join(TEXTURE_DIR, "%s_%d.png" % (name, index)), frame)
            names.append("orbitalis:%s_%d" % (name, index))
            count += 1
        with open(os.path.join(PARTICLE_DIR, name + ".json"), "w", newline="\n") as f:
            json.dump({"textures": names}, f, indent=2)
    # Particules d'explosion : elles reprennent les images d'une autre, en bien plus gros (voir OrbitalisParticle).
    for alias, source in {"blast_fire": "trench_fire", "blast_smoke": "launch_smoke", "blast_cryo": "launch_steam"}.items():
        with open(os.path.join(PARTICLE_DIR, alias + ".json"), "w", newline=chr(10)) as f:
            json.dump({"textures": ["orbitalis:%s_%d" % (source, k) for k in range(len(PARTICLES[source]))]}, f, indent=2)
    write_png(os.path.join(TEXTURE_DIR, "planet.png"), planet_texture())
    # Les images de Jupiter et de Proxima sont dessinées par generate_europa.py et generate_proxima.py.
    with open(os.path.join(PARTICLE_DIR, "planet.json"), "w", newline=chr(10)) as f:
        json.dump({"textures": ["orbitalis:planet", "orbitalis:jupiter", "orbitalis:proxima_b",
                                "orbitalis:proxima_star"]}, f, indent=2)

    print(f"{len(PARTICLES) + 1} particules, {count + 1} images écrites.")
