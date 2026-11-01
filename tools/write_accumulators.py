#!/usr/bin/env python3
"""
Les accumulateurs d'Orbitalis, de leurs quatre générations : leurs textures et leurs modèles. C'est le seul script qui
les écrit.

Un accumulateur est un bloc plein : un caisson biseauté et boulonné aux couleurs de sa génération. La face avant porte
la prise de sortie, un connecteur rond orange ; les autres faces sont des entrées, marquées d'un repère bleu, et
montrent la charge dans une fenêtre de cinq crans qui s'allument de bas en haut. Le dessus porte quatre crans qui
disent la génération. Un modèle par cran de charge (de 0 à 5) ; le jeu le tourne vers la face avant.

Les générations :
- l'accumulateur : acier canon de fusil, crans verts ;
- l'avancé : graphite, crans cyan ;
- l'élite : noir et or, crans dorés ;
- le quantique : chrome clair, crans violets.

Usage : python tools/write_accumulators.py
"""
import json
import math
import os

import generate_machine_models as gm
from generate_textures import SIZE, rgb

MAX_CHARGE = 5
OUTPUT = "#E8872A"
INPUT = "#3F76E4"
HOLE = "#15171A"

TIERS = {
    # suffixe : caisson, filet, crans allumés
    "": {"body": "#4A525B", "trim": "#6B747E", "accent": "#5BE37D", "tier": 1},
    "_advanced": {"body": "#262B31", "trim": "#3F4852", "accent": "#3FD8F2", "tier": 2},
    "_elite": {"body": "#1F1E23", "trim": "#C9A23A", "accent": "#E8C03A", "tier": 3},
    "_quantum": {"body": "#CDD5DD", "trim": "#8E99A6", "accent": "#C04BFF", "tier": 4},
}


def mix(a, b, t):
    ca = rgb(a) if isinstance(a, str) else a
    cb = rgb(b) if isinstance(b, str) else b
    return tuple(int(ca[i] + (cb[i] - ca[i]) * t) for i in range(3)) + (255,)


def dot(img, x, y, color):
    if 0 <= x < SIZE and 0 <= y < SIZE:
        img[y][x] = rgb(color) if isinstance(color, str) else color


def casing(t):
    """Le caisson : la tôle, un filet sombre au bord, un biseau (clair en haut à gauche, ombré en bas à droite), et
    quatre boulons."""
    body = t["body"]
    img = [[rgb(body) for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            if (x * 3 + y * 5) % 11 == 0:
                img[y][x] = mix(body, "#FFFFFF", 0.04)
    for k in range(SIZE):
        for x, y in ((k, 0), (k, 15), (0, k), (15, k)):
            dot(img, x, y, mix(body, "#000000", 0.45))
    for k in range(1, 15):
        dot(img, k, 1, mix(t["trim"], "#FFFFFF", 0.25))
        dot(img, 1, k, mix(t["trim"], "#FFFFFF", 0.15))
        dot(img, k, 14, mix(t["trim"], "#000000", 0.3))
        dot(img, 14, k, mix(t["trim"], "#000000", 0.2))
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        dot(img, x, y, mix(t["trim"], "#FFFFFF", 0.5))
        dot(img, x + 1, y + 1, mix(body, "#000000", 0.35))
    return img


def front(t):
    """La face avant, la sortie : un connecteur rond orange au milieu, et le filet de la génération au pied."""
    img = casing(t)
    for y in range(SIZE):
        for x in range(SIZE):
            r = math.hypot(x - 7.5, y - 7.5)
            if r <= 2.1:
                dot(img, x, y, HOLE)
            elif r <= 3.6:
                dot(img, x, y, mix(OUTPUT, "#FFFFFF", 0.3) if y < 7 else OUTPUT)
            elif r <= 4.4:
                dot(img, x, y, mix(t["body"], "#000000", 0.5))
    dot(img, 7, 7, mix(OUTPUT, "#000000", 0.4))
    dot(img, 8, 8, mix(OUTPUT, "#000000", 0.4))
    for x in range(5, 11):
        dot(img, x, 13, t["accent"])
    return img


def side(t, charge):
    """Une face d'entrée : le repère bleu, et la fenêtre de charge, cinq crans de deux pixels allumés de bas en haut."""
    img = casing(t)
    for y in range(2, 14):
        for x in range(5, 11):
            dot(img, x, y, "#15181C" if x in (5, 10) or y in (2, 13) else "#1E2328")
    for k in range(MAX_CHARGE):
        y0 = 11 - 2 * k
        lit = k < charge
        for x in range(6, 10):
            dot(img, x, y0, mix(t["accent"], "#FFFFFF", 0.35) if lit else "#2A3036")
            dot(img, x, y0 + 1, t["accent"] if lit else "#232930")
    for y in (7, 8):
        dot(img, 2, y, INPUT)
        dot(img, 3, y, mix(INPUT, "#FFFFFF", 0.3))
        dot(img, 12, y, mix(INPUT, "#FFFFFF", 0.3))
        dot(img, 13, y, INPUT)
    return img


def top(t):
    """Le dessus : deux grilles d'aération, et entre elles quatre crans, allumés jusqu'à la génération."""
    img = casing(t)
    for y in (4, 5, 10, 11):
        for x in range(4, 12):
            dot(img, x, y, "#15181C" if y % 2 == 0 else mix(t["body"], "#FFFFFF", 0.2))
    for k in range(4):
        lit = k < t["tier"]
        for x in (3 + 3 * k, 4 + 3 * k):
            dot(img, x, 7, mix(t["accent"], "#FFFFFF", 0.3) if lit else "#2A3036")
            dot(img, x, 8, t["accent"] if lit else "#232930")
    return img


def model(suffix, charge):
    tex = lambda name: "orbitalis:block/" + name
    side_tex = tex("accumulator%s_side_%d" % (suffix, charge))
    return {"parent": "minecraft:block/cube",
            "textures": {"particle": tex("accumulator%s_front" % suffix), "north": tex("accumulator%s_front" % suffix),
                         "south": side_tex, "east": side_tex, "west": side_tex,
                         "up": tex("accumulator%s_top" % suffix), "down": tex("accumulator%s_bottom" % suffix)}}


def main():
    for suffix, t in TIERS.items():
        gm.write_texture("accumulator%s_front" % suffix, [front(t)])
        gm.write_texture("accumulator%s_top" % suffix, [top(t)])
        gm.write_texture("accumulator%s_bottom" % suffix, [casing(t)])
        for charge in range(MAX_CHARGE + 1):
            gm.write_texture("accumulator%s_side_%d" % (suffix, charge), [side(t, charge)])
            with open(os.path.join(gm.MODEL_DIR, "accumulator%s_%d.json" % (suffix, charge)), "w", newline="\n") as f:
                json.dump(model(suffix, charge), f, indent=2)
    print("Accumulateurs : %d générations écrites." % len(TIERS))


if __name__ == "__main__":
    main()
