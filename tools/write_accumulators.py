#!/usr/bin/env python3
"""
Les accumulateurs d'Orbitalis, de leurs quatre générations : leurs textures et leurs modèles. C'est le seul script qui
les écrit.

Un accumulateur est un bloc plein, le même châssis pour les quatre générations, qui gagne à chacune un signe de son
amélioration :
- l'accumulateur : châssis d'acier, filet clair, jauge verte ;
- l'avancé : châssis graphite, cadre d'aluminium, et des coiffes boulonnées aux quatre coins, jauge cyan ;
- l'élite : châssis noir, cadre d'or, coiffes, et un filet lumineux sous le haut du cadre, jauge ambre ;
- le quantique : châssis nacré, cadre de chrome, coiffes, filet lumineux, et une fenêtre de jauge cerclée de lumière,
  jauge violette.

La face avant, celle qui regarde le joueur à la pose, porte la jauge : cinq crans qui s'allument de bas en haut. Le dos
porte la sortie, un connecteur rond orange ; les flancs, des entrées, un connecteur bleu entre deux grilles. Le dessus
porte quatre crans qui disent la génération. Un modèle par cran de charge (de 0 à 5) ; le jeu le tourne vers l'avant.

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
HOLE = "#12151A"
SEAM = "#14181D"
GLASS = "#0F151A"

TIERS = {
    # suffixe : châssis, cadre, jauge, génération
    "": {"body": "#59616B", "trim": "#8A939D", "accent": "#5BE37D", "tier": 1},
    "_advanced": {"body": "#2A3037", "trim": "#A7B0BA", "accent": "#3FD8F2", "tier": 2},
    "_elite": {"body": "#1C1B20", "trim": "#C9A23A", "accent": "#F2B33D", "tier": 3},
    "_quantum": {"body": "#E2E7EC", "trim": "#8E99A6", "accent": "#B04BFF", "tier": 4},
}
GLOW = "#7FF3FF"


def mix(a, b, t):
    ca = rgb(a) if isinstance(a, str) else a
    cb = rgb(b) if isinstance(b, str) else b
    return tuple(int(ca[i] + (cb[i] - ca[i]) * t) for i in range(3)) + (255,)


def dot(img, x, y, color):
    if 0 <= x < SIZE and 0 <= y < SIZE:
        img[y][x] = rgb(color) if isinstance(color, str) else color


def chassis(t):
    """Le châssis commun : la tôle à peine nuancée, un bord sombre, le cadre (clair en haut à gauche, ombré en bas à
    droite), et, selon la génération, les coiffes d'angle et le filet lumineux."""
    body = t["body"]
    img = [[rgb(body) for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            if (x * 3 + y * 5) % 13 == 0:
                img[y][x] = mix(body, "#FFFFFF" if t["tier"] < 4 else "#9AA6B3", 0.06)
    for k in range(SIZE):
        for x, y in ((k, 0), (k, 15), (0, k), (15, k)):
            dot(img, x, y, mix(body, "#000000", 0.5))
    for k in range(1, 15):
        dot(img, k, 1, mix(t["trim"], "#FFFFFF", 0.25))
        dot(img, 1, k, mix(t["trim"], "#FFFFFF", 0.12))
        dot(img, k, 14, mix(t["trim"], "#000000", 0.3))
        dot(img, 14, k, mix(t["trim"], "#000000", 0.2))
    if t["tier"] >= 2:
        for cx, cy in ((1, 1), (12, 1), (1, 12), (12, 12)):
            for y in range(cy, cy + 3):
                for x in range(cx, cx + 3):
                    dot(img, x, y, t["trim"])
            dot(img, cx + 1, cy + 1, mix(t["trim"], "#FFFFFF", 0.55))
            dot(img, cx + 2, cy + 2, mix(t["trim"], "#000000", 0.35))
    if t["tier"] >= 3:
        for x in range(4, 12):
            dot(img, x, 1, mix(t["accent"], "#FFFFFF", 0.35))
    return img


def front(t, charge):
    """La face avant : la fenêtre de la jauge, cinq crans de deux pixels allumés de bas en haut ; cerclée de lumière
    pour le quantique."""
    img = chassis(t)
    frame = GLOW if t["tier"] >= 4 else SEAM
    for y in range(2, 14):
        for x in range(4, 12):
            dot(img, x, y, frame if x in (4, 11) or y in (2, 13) else GLASS)
    for k in range(MAX_CHARGE):
        y0 = 11 - 2 * k
        lit = k < charge
        for x in range(5, 11):
            dot(img, x, y0, mix(t["accent"], "#FFFFFF", 0.4) if lit else "#1C252C")
            dot(img, x, y0 + 1, t["accent"] if lit else "#172027")
    # Le reflet de la vitre, sur le bord gauche.
    for y in range(3, 13):
        if img[y][5] == rgb("#1C252C") or img[y][5] == rgb("#172027"):
            dot(img, 5, y, "#222D35")
    return img


def connector(img, color, radius, t):
    """Un connecteur rond : la bague de couleur, le trou au milieu, et un liseré du cadre autour."""
    for y in range(SIZE):
        for x in range(SIZE):
            r = math.hypot(x - 7.5, y - 7.5)
            if r <= radius - 1.5:
                dot(img, x, y, HOLE)
            elif r <= radius:
                dot(img, x, y, mix(color, "#FFFFFF", 0.3) if y < 7 else color)
            elif r <= radius + 0.9:
                dot(img, x, y, mix(t["trim"], "#000000", 0.25))


def back(t):
    """Le dos, la sortie : un grand connecteur orange."""
    img = chassis(t)
    connector(img, OUTPUT, 3.8, t)
    return img


def side(t):
    """Un flanc, une entrée : un connecteur bleu entre deux grilles d'aération."""
    img = chassis(t)
    for y in (3, 4, 11, 12):
        for x in range(5, 11):
            dot(img, x, y, SEAM if y % 2 == 1 else mix(t["body"], "#FFFFFF", 0.15))
    connector(img, INPUT, 2.6, t)
    return img


def top(t):
    """Le dessus : une grille d'aération, et quatre crans allumés jusqu'à la génération."""
    img = chassis(t)
    for y in (4, 6):
        for x in range(4, 12):
            dot(img, x, y, SEAM)
            dot(img, x, y + 1, mix(t["body"], "#FFFFFF", 0.15))
    for k in range(4):
        lit = k < t["tier"]
        for x in (3 + 3 * k, 4 + 3 * k):
            dot(img, x, 10, mix(t["accent"], "#FFFFFF", 0.35) if lit else "#2A3036")
            dot(img, x, 11, t["accent"] if lit else "#222930")
    return img


def model(suffix, charge):
    tex = lambda name: "orbitalis:block/" + name
    side_tex = tex("accumulator%s_side" % suffix)
    return {"parent": "minecraft:block/cube",
            "textures": {"particle": side_tex, "north": tex("accumulator%s_front_%d" % (suffix, charge)),
                         "south": tex("accumulator%s_back" % suffix), "east": side_tex, "west": side_tex,
                         "up": tex("accumulator%s_top" % suffix), "down": tex("accumulator%s_bottom" % suffix)}}


def main():
    for suffix, t in TIERS.items():
        gm.write_texture("accumulator%s_back" % suffix, [back(t)])
        gm.write_texture("accumulator%s_side" % suffix, [side(t)])
        gm.write_texture("accumulator%s_top" % suffix, [top(t)])
        gm.write_texture("accumulator%s_bottom" % suffix, [chassis(t)])
        for charge in range(MAX_CHARGE + 1):
            gm.write_texture("accumulator%s_front_%d" % (suffix, charge), [front(t, charge)])
            with open(os.path.join(gm.MODEL_DIR, "accumulator%s_%d.json" % (suffix, charge)), "w", newline="\n") as f:
                json.dump(model(suffix, charge), f, indent=2)
    print("Accumulateurs : %d générations écrites." % len(TIERS))


if __name__ == "__main__":
    main()
