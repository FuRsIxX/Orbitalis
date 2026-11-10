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

La face avant, celle qui regarde le joueur à la pose, porte la jauge : cinq crans qui s'allument de bas en haut. Toutes
les faces prennent ou donnent l'énergie selon le réglage (charge ou décharge) : aucune n'a de prise de couleur. Le dos
porte une trappe de service ; les flancs, une grille d'aération et deux voyants. Le dessus
porte quatre crans qui disent la génération ; le dessous, quatre patins et une grille. Chaque face est un panneau en
creux dans son cadre. Un modèle par cran de charge (de 0 à 5) ; le jeu le tourne vers l'avant.

Usage : python tools/write_accumulators.py
"""
import json
import os

import generate_machine_models as gm
from generate_textures import SIZE, rgb

MAX_CHARGE = 5
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
    # Le panneau en creux, dans le cadre : une ombre en haut à gauche, une lumière en bas à droite.
    for k in range(2, 14):
        dot(img, k, 2, mix(body, "#000000", 0.28))
        dot(img, 2, k, mix(body, "#000000", 0.2))
        dot(img, k, 13, mix(body, "#FFFFFF", 0.14))
        dot(img, 13, k, mix(body, "#FFFFFF", 0.1))
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
    # Des rivets à mi-hauteur du cadre, de chaque côté.
    for x, y in ((1, 7), (14, 7)):
        dot(img, x, y, mix(t["trim"], "#FFFFFF", 0.6))
        dot(img, x, y + 1, mix(t["trim"], "#000000", 0.4))
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
    # À gauche de la fenêtre, une graduation, un trait par cran ; à droite, le voyant de marche et une plaquette de
    # danger.
    for k in range(MAX_CHARGE):
        y = 11 - 2 * k
        dot(img, 3, y, mix(t["trim"], "#FFFFFF", 0.2) if k % 2 == 0 else mix(t["trim"], "#000000", 0.1))
        if k % 2 == 0:
            dot(img, 2, y, mix(t["trim"], "#000000", 0.2))
    on = charge > 0
    dot(img, 12, 5, mix(t["accent"], "#FFFFFF", 0.3) if on else "#3A1F1F")
    dot(img, 13, 5, t["accent"] if on else "#2A1717")
    for y in (8, 9, 10):
        for x in (12, 13):
            dot(img, x, y, "#F2C14E" if (x + y) % 2 == 0 else SEAM)
    # L'interrupteur, sous la plaquette : un boîtier sombre et sa manette claire, levée quand il y a du courant.
    for x in (12, 13):
        dot(img, x, 12, SEAM)
    dot(img, 12 if on else 13, 12, mix(t["trim"], "#FFFFFF", 0.5))
    return img


def back(t):
    """Le dos : une trappe de service vissée, sa plaque d'avertissement. Aucune prise : toutes les faces font de même."""
    img = chassis(t)
    # La trappe, en relief : un rectangle bordé d'ombre en bas à droite, de lumière en haut à gauche.
    for y in range(4, 12):
        for x in range(4, 12):
            edge_light = x == 4 or y == 4
            edge_dark = x == 11 or y == 11
            if edge_light:
                dot(img, x, y, mix(t["body"], "#FFFFFF", 0.18))
            elif edge_dark:
                dot(img, x, y, mix(t["body"], "#000000", 0.35))
            else:
                dot(img, x, y, mix(t["body"], "#FFFFFF", 0.06))
    # Ses quatre vis, et sa poignée encastrée.
    for x, y in ((5, 5), (10, 5), (5, 10), (10, 10)):
        dot(img, x, y, mix(t["trim"], "#FFFFFF", 0.45))
    for x in range(6, 10):
        dot(img, x, 8, SEAM)
        dot(img, x, 9, mix(t["body"], "#FFFFFF", 0.2))
    # La plaque d'avertissement, au-dessus de la trappe : la couleur de la génération.
    for x in range(6, 10):
        dot(img, x, 3, mix(t["accent"], t["body"], 0.35))
    return img


def side(t):
    """Un flanc : une grille d'aération sur toute la largeur du panneau, un liseré et deux voyants de la génération."""
    img = chassis(t)
    for y in range(4, 12, 2):
        for x in range(4, 12):
            dot(img, x, y, SEAM)
            dot(img, x, y + 1, mix(t["body"], "#FFFFFF", 0.15))
    for x in (2, 13):
        for y in (5, 6, 9, 10):
            dot(img, x, y, mix(t["body"], "#000000", 0.25))
    # Un liseré de la couleur de la génération sous la grille, et deux voyants de part et d'autre.
    for x in range(5, 11):
        dot(img, x, 13, mix(t["accent"], t["body"], 0.45))
    for x in (3, 12):
        dot(img, x, 7, mix(t["accent"], "#FFFFFF", 0.3))
        dot(img, x, 8, mix(t["accent"], "#000000", 0.25))
    return img


def top(t):
    """Le dessus : une grille d'aération, et quatre crans allumés jusqu'à la génération."""
    img = chassis(t)
    # La plaque signalétique : une étiquette claire et deux lignes d'écriture.
    for y in (13, 14):
        for x in range(5, 11):
            dot(img, x, y - 11, mix(t["trim"], "#FFFFFF", 0.55))
    for x in (6, 7, 9):
        dot(img, x, 2, mix(t["body"], "#000000", 0.35))
    for y in (4, 6):
        for x in range(4, 12):
            dot(img, x, y, SEAM)
            dot(img, x, y + 1, mix(t["body"], "#FFFFFF", 0.15))
    for k in range(4):
        lit = k < t["tier"]
        for x in (3 + 3 * k, 4 + 3 * k):
            dot(img, x, 10, mix(t["accent"], "#FFFFFF", 0.35) if lit else "#2A3036")
            dot(img, x, 11, t["accent"] if lit else "#222930")
    # Deux boulons de fixation, sous les crans.
    for x in (4, 11):
        dot(img, x, 13, mix(t["trim"], "#FFFFFF", 0.45))
        dot(img, x + 1, 13, mix(t["trim"], "#000000", 0.25))
    return img


def bottom(t):
    """Le dessous : quatre patins sombres aux coins du panneau, et une grille d'évacuation au milieu."""
    img = chassis(t)
    for cx, cy in ((3, 3), (11, 3), (3, 11), (11, 11)):
        for y in (cy, cy + 1):
            for x in (cx, cx + 1):
                dot(img, x, y, "#1A1D21")
        dot(img, cx, cy, "#2C3036")
    for y in (6, 8):
        for x in range(6, 10):
            dot(img, x, y, SEAM)
            dot(img, x, y + 1, mix(t["body"], "#FFFFFF", 0.15))
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
        gm.write_texture("accumulator%s_bottom" % suffix, [bottom(t)])
        for charge in range(MAX_CHARGE + 1):
            gm.write_texture("accumulator%s_front_%d" % (suffix, charge), [front(t, charge)])
            with open(os.path.join(gm.MODEL_DIR, "accumulator%s_%d.json" % (suffix, charge)), "w", newline="\n") as f:
                json.dump(model(suffix, charge), f, indent=2)
    print("Accumulateurs : %d générations écrites." % len(TIERS))


if __name__ == "__main__":
    main()
