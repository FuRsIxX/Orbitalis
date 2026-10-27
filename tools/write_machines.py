#!/usr/bin/env python3
"""
Les machines d'Orbitalis : leurs modèles et leurs textures. C'est le seul script qui les écrit.

Toutes partagent un même modèle, simple et presque plein : un cube de coque claire ; en façade, un écran et une
grille d'aération en retrait d'un demi-pixel. Ce qui les distingue :

- leur couleur d'accent, reprise par le pictogramme et la jauge de l'écran, la lueur de la grille, le filet des flancs
  et les coins du dessus ;
- leur pictogramme : flamme (générateur), éclair (fonderie à arc), cylindres (laminoir), goutte (électrolyseur),
  flocon (liquéfacteur), colonne (raffinerie), fiole (Sabatier), croissant de lune (hélium-3), plaques (Casimir),
  atome (fusion), réservoir (cryogénique), fusée (ordinateur de vol).

En marche, la jauge de l'écran monte cran par cran, le témoin clignote et la grille ondule de la couleur de la machine.

Le dos de toutes porte le port d'énergie, le dessous est un socle sombre : câbles et tuyaux s'y raccordent proprement.
Allumés, l'écran et la grille luisent.

Usage : python tools/write_machines.py
"""
import json
import math
import os
import random

import generate_machine_models as gm
from generate_machine_models import box
from generate_textures import SIZE, rgb
from model_kit import check_overlaps, separate

# La coque, commune à toutes les machines.
SHELL = ("#D2D7DD", "#DCE0E5", "#C6CCD3")
EDGE = "#2B3036"
TRIM = "#3B4249"
GLASS_DARK = "#141A20"
BEZEL = "#7B848E"
BOLT = "#C9CFD6"

SCREEN = "#0C1014"
# Écran et grille de la façade, en pixels de texture (bornes incluses) : l'écran en haut, la grille en bas.
SX0, SX1, SY0, SY1 = 2, 13, 2, 9
GY0, GY1 = 11, 14
# Les pictogrammes des machines, cinq pixels sur cinq.
ICONS = {
    "flame": ["..#..", ".##.#", ".####", "#####", ".###."],
    "bolt": ["...##", "..##.", ".####", "..##.", ".##.."],
    "rollers": [".###.", ".###.", "#####", ".###.", ".###."],
    "drop": ["..#..", ".###.", "#####", "#####", ".###."],
    "snow": ["#.#.#", ".###.", "##.##", ".###.", "#.#.#"],
    "tower": [".###.", ".#.#.", ".###.", ".#.#.", "#####"],
    "flask": ["..#..", "..#..", ".###.", "##.##", "#####"],
    "moon": [".###.", "##...", "#....", "##...", ".###."],
    "plates": ["#...#", "#.#.#", "#...#", "#.#.#", "#...#"],
    "atom": [".###.", "#.#.#", "##.##", "#.#.#", ".###."],
    "tank": [".###.", "#...#", "#####", "#####", ".###."],
    "rocket": ["..#..", ".###.", ".###.", ".###.", "#.#.#"],
}


def canvas(color):
    return [[rgb(color) for _ in range(SIZE)] for _ in range(SIZE)]


def put(img, x, y, color):
    if 0 <= x < SIZE and 0 <= y < SIZE:
        img[y][x] = rgb(color) if isinstance(color, str) else color


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    ca, cb = rgb(a), rgb(b)
    return tuple(int(ca[i] + (cb[i] - ca[i]) * t) for i in range(3)) + (255,)


def shell(seed):
    """La coque : une tôle claire, très légèrement nuancée, bordée d'un filet sombre, éclairée en haut à gauche."""
    rnd = random.Random(seed)
    img = [[rgb(rnd.choice((SHELL[0], SHELL[0], SHELL[0], SHELL[1], SHELL[2]))) for _ in range(SIZE)] for _ in range(SIZE)]
    for k in range(SIZE):
        put(img, k, 0, EDGE)
        put(img, k, SIZE - 1, EDGE)
        put(img, 0, k, EDGE)
        put(img, SIZE - 1, k, EDGE)
    for k in range(1, SIZE - 1):
        put(img, k, 1, "#EEF1F4")
        put(img, 1, k, "#EEF1F4")
        put(img, k, SIZE - 2, "#AEB5BD")
        put(img, SIZE - 2, k, "#AEB5BD")
    return img


def slats(img, x0, x1, y0, y1):
    """Des lamelles d'aération, sombres et claires une rangée sur deux."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(img, x, y, "#1C2025" if (y - y0) % 2 == 0 else "#5A626C")


# ---------------------------------------------------------------------------
# Les faces communes
# ---------------------------------------------------------------------------
def seam(img, y):
    """Un joint de tôle horizontal : une ligne d'ombre soulignée d'un filet de lumière."""
    for x in range(2, SIZE - 2):
        put(img, x, y, "#AEB5BD")
        put(img, x, y + 1, "#E6EAEE")


def bolts(img, points):
    for x, y in points:
        put(img, x, y, BOLT)


def side_texture(accent):
    """Flanc : la coque, un joint de tôle, un filet d'accent, une grille d'aération et quatre rivets."""
    img = shell(11)
    seam(img, 4)
    for x in range(3, SIZE - 3):
        put(img, x, 7, accent)
    slats(img, 4, 11, 10, 12)
    bolts(img, ((3, 3), (12, 3), (3, 13), (12, 13)))
    return img


def top_texture(accent):
    """Dessus : la coque, une grille d'aération au centre, et l'accent dans les quatre coins."""
    img = shell(12)
    for cx, cy, dx, dy in ((2, 2, 1, 1), (13, 2, -1, 1), (2, 13, 1, -1), (13, 13, -1, -1)):
        for k in range(3):
            put(img, cx + dx * k, cy, accent)
            put(img, cx, cy + dy * k, accent)
    for k in range(4, 12):
        put(img, k, 4, TRIM)
        put(img, k, 11, TRIM)
        put(img, 4, k, TRIM)
        put(img, 11, k, TRIM)
    slats(img, 5, 10, 5, 10)
    bolts(img, ((6, 2), (9, 2), (6, 13), (9, 13)))
    return img


def back_texture():
    """Dos : la coque, et au centre le port d'énergie où se branchent les câbles, ses carrés emboîtés dorés et cuivrés."""
    img = shell(13)
    colors = ["#2B3036", "#8A6A12", "#D9A93A", "#15171A", "#C46A35", "#E8A56A"]
    for y in range(4, 12):
        for x in range(4, 12):
            ring = min(x - 4, y - 4, 11 - x, 11 - y)
            put(img, x, y, colors[min(ring, len(colors) - 1)])
    return img


def base_texture():
    """Dessous : un socle graphite, ses quatre patins."""
    img = canvas("#353A41")
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        for dx in (0, 1):
            for dy in (0, 1):
                put(img, x - dx if x > 8 else x + dx, y - dy if y > 8 else y + dy, "#23272C")
    return img


def front_texture(accent, icon, step, on, frames=8):
    """
    Façade : la coque ; en haut, l'écran de la machine dans sa lunette graphite, son pictogramme et la jauge qui monte
    pendant qu'elle travaille ; en bas, la grande grille d'aération, qui luit de la couleur de la machine en marche.
    """
    img = shell(14)
    for x in range(SX0 - 1, SX1 + 2):
        put(img, x, SY0 - 1, TRIM)
        put(img, x, SY1 + 1, TRIM)
    for y in range(SY0 - 1, SY1 + 2):
        put(img, SX0 - 1, y, TRIM)
        put(img, SX1 + 1, y, TRIM)
    for y in range(SY0, SY1 + 1):
        for x in range(SX0, SX1 + 1):
            put(img, x, y, SCREEN)
    # Le pictogramme, net et lumineux en marche, éteint sinon ; son ombre portée d'un pixel en bas à droite.
    for r, row in enumerate(ICONS[icon]):
        for c, ch in enumerate(row):
            if ch == "#":
                put(img, 4 + c, 4 + r, accent if on else "#3A4048")
                if on and (r + 1 >= len(ICONS[icon]) or ICONS[icon][r + 1][c] != "#"):
                    put(img, 4 + c, 4 + r, mix(accent, "#000000", 0.25))
    # La jauge : cinq crans, qui se remplissent au fil de l'animation.
    level = 1 + step * 5 // frames if on else 0
    for k in range(5):
        for x in (10, 11):
            put(img, x, 8 - k, accent if k < level else "#1A2026")
    # Un témoin, en haut à droite de l'écran : vert en marche, rouge à l'arrêt.
    put(img, 12, 3, ("#5BE08A" if step % 2 == 0 else "#2F7A4A") if on else "#8A2A2A")
    # La grille : des lamelles, et entre elles la lueur de la machine, qui ondule en marche.
    for y in range(GY0, GY1 + 1):
        for x in range(SX0, SX1 + 1):
            if (y - GY0) % 2 == 0:
                put(img, x, y, "#1C2025")
            else:
                pulse = 0.55 + 0.35 * math.sin(step * 2 * math.pi / frames + x * 0.55)
                put(img, x, y, mix("#2B3036", accent, pulse) if on else "#2B3036")
    return img


def mix_rgb(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


# Nom de la machine : son accent, son pictogramme, et le nombre d'images de son animation.
MACHINES = {
    "combustion_generator": ("#F08A24", "flame", 8),
    "arc_foundry": ("#F2C14E", "bolt", 8),
    "rolling_mill": ("#6F9CC8", "rollers", 4),
    "electrolyzer": ("#3F8FE0", "drop", 8),
    "cryogenic_liquefier": ("#5FD3E8", "snow", 8),
    "refinery": ("#D9902A", "tower", 8),
    "sabatier_reactor": ("#4CB872", "flask", 8),
    "helium3_extractor": ("#E8C24A", "moon", 5),
    "casimir_chamber": ("#A05CF0", "plates", 8),
    "fusion_reactor": ("#F05AB4", "atom", 8),
    "cryogenic_tank": ("#9AD4F5", "tank", 8),
}
# Blocs du même style qui n'ont pas d'état allumé : leur fenêtre est toujours animée et lumineuse.
ALWAYS_ON = {
    # L'ordinateur de vol : l'avionique d'une fusée, posée dans la fusée comme ses autres pièces.
    "flight_computer": ("#3F76E4", "rocket", 8),
}


# ---------------------------------------------------------------------------
# Le modèle, commun à toutes
# ---------------------------------------------------------------------------
def parts():
    """
    Un cube plein ; en façade, l'écran et la grille en retrait d'un demi-pixel (l'écran et la grille luisent en marche),
    et entre eux et autour d'eux la coque.
    """
    return [
        {**box(0, 0, 1, 16, 16, 16, "side"), "faces": {"north": "front", "south": "back", "up": "top", "down": "base"}},
        {**box(0, 14, 0, 16, 16, 1, "front"), "faces": {"up": "top", "east": "side", "west": "side"}},
        {**box(0, 5, 0, 16, 6, 1, "front"), "faces": {"east": "side", "west": "side"}},
        {**box(0, 0, 0, 16, 1, 1, "front"), "faces": {"down": "base", "east": "side", "west": "side"}},
        {**box(0, 6, 0, 2, 14, 1, "front"), "faces": {"west": "side"}},
        {**box(14, 6, 0, 16, 14, 1, "front"), "faces": {"east": "side"}},
        {**box(0, 1, 0, 2, 5, 1, "front"), "faces": {"west": "side"}},
        {**box(14, 1, 0, 16, 5, 1, "front"), "faces": {"east": "side"}},
        {**box(2, 6, 0.5, 14, 14, 1, "front"), "window": True},
        {**box(2, 1, 0.5, 14, 5, 1, "front"), "window": True},
    ]


def model(name, lit):
    textures = {
        "front": "orbitalis:block/%s_front%s" % (name, "_on" if lit else ""),
        "side": "orbitalis:block/%s_side" % name, "top": "orbitalis:block/%s_top" % name,
        "back": "orbitalis:block/machine_back", "base": "orbitalis:block/machine_base",
        "particle": "orbitalis:block/%s_side" % name,
    }
    elements = []
    for part in separate(parts()):
        own = part.get("faces", {})
        element = {"from": part["from"], "to": part["to"],
                   "faces": {face: {"texture": "#" + own.get(face, part["texture"])} for face in gm.FACES}}
        if lit and part.get("window"):
            element["light_emission"] = 12
        elements.append(element)
    return {"parent": "minecraft:block/block", "textures": textures, "elements": elements}


def main():
    gm.write_texture("machine_back", [back_texture()])
    gm.write_texture("machine_base", [base_texture()])
    for name, (accent, icon, frames) in MACHINES.items():
        gm.write_texture(name + "_side", [side_texture(accent)])
        gm.write_texture(name + "_top", [top_texture(accent)])
        gm.write_texture(name + "_front", [front_texture(accent, icon, 0, False, frames)])
        gm.write_texture(name + "_front_on", [front_texture(accent, icon, k, True, frames) for k in range(frames)], 4, interpolate=False)
    for name, (accent, icon, frames) in ALWAYS_ON.items():
        gm.write_texture(name + "_side", [side_texture(accent)])
        gm.write_texture(name + "_top", [top_texture(accent)])
        gm.write_texture(name + "_front_on", [front_texture(accent, icon, k, True, frames) for k in range(frames)], 4, interpolate=False)
        with open(os.path.join(gm.MODEL_DIR, name + ".json"), "w", newline="\n") as f:
            json.dump(model(name, True), f, indent=2)
    overlaps = check_overlaps("machine", separate(parts()))
    for name in MACHINES:
        for lit in (False, True):
            with open(os.path.join(gm.MODEL_DIR, name + ("_on" if lit else "") + ".json"), "w", newline="\n") as f:
                json.dump(model(name, lit), f, indent=2)
    print("Machines : %d modèles et leurs textures écrits, %d chevauchement(s)." % (len(MACHINES), overlaps))


if __name__ == "__main__":
    main()
