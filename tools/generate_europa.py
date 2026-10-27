#!/usr/bin/env python3
"""
Génère ce qu'il faut voir d'Europe, la lune glacée de Jupiter, dans Orbitalis :

- la glace d'Europe : une glace d'eau bleutée, très claire, à peine fissurée ;
- la glace des lineae : la glace brun-rouge, chargée de sels, des longues traînées et des terrains chaotiques ;
- Jupiter, telle qu'on la voit dans le ciel d'Europe : un disque immense, ses bandes crème et brunes, sa Grande Tache
  rouge, un bord plus sombre (une image de particule, comme la planète vue de la Lune) ;
- le vaisseau d'Europe (une cabine sur son étage de descente, sous un blindage gris) et le module de blindage
  anti-radiations de la combinaison, en icônes ;
- le cryobot, la sonde foreuse : sa sonde blanche à demi enfoncée dans la glace sous son portique, son treuil et son
  câble, son générateur à radio-isotopes et son antenne (un modèle de bloc).

Aucune face du modèle n'en recouvre une autre.

Usage : python tools/generate_europa.py
"""
import json
import math
import os
import random

import generate_exploration as ex
from generate_machine_models import box
from generate_textures import (BLOCK_DIR, DARK_METAL, ITEM_DIR, ROOT, WHITE_PAINT, blotchy, part_canvas, part_cap, part_row,
                               rgb, write_png)
from model_kit import build_tiled, check_overlaps, separate

PARTICLE_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "orbitalis", "textures", "particle")
SHIELD = ("#2E3238", "#5C6672", "#8C949E", "#B4BCC4", "#DADDE1")


def europa_ice_texture():
    """Glace d'Europe : un blanc bleuté, de fines fissures plus sombres, quelques reflets."""
    img = blotchy(150, [rgb("#DCE8F0"), rgb("#CFDDE8"), rgb("#E8F0F6"), rgb("#C2D3E0")], [5, 3, 2, 1], passes=2)
    for x, y in ((1, 4), (2, 4), (3, 5), (4, 5), (5, 6), (9, 11), (10, 11), (11, 12), (12, 12), (13, 13)):
        img[y][x] = rgb("#A9BFCF")
    for x, y in ((11, 2), (4, 13), (14, 7)):
        img[y][x] = rgb("#F6FAFD")
    return img


def linea_ice_texture():
    """Glace des lineae : brun-rouge, des grains de sel plus clairs, et la glace qui affleure par endroits."""
    img = blotchy(151, [rgb("#9C5A3C"), rgb("#8A4C32"), rgb("#B06A46"), rgb("#7A4029")], [5, 3, 2, 1], passes=2)
    for x, y in ((2, 3), (12, 5), (7, 10), (4, 14), (14, 12)):
        img[y][x] = rgb("#D9C2B0")
    for x, y in ((9, 2), (1, 8), (13, 15)):
        img[y][x] = rgb("#C9D6E0")
    return img


def jupiter_texture(size=160):
    """Jupiter : ses bandes, sa Grande Tache rouge, ses volutes, et un bord qui s'assombrit ; en pixels larges."""
    rnd = random.Random(152)
    cell = 4
    n = size // cell
    bands = [
        (0.00, "#B7A58C"), (0.10, "#D8C9AE"), (0.18, "#A97F5E"), (0.25, "#E6DCC8"), (0.33, "#B98A64"),
        (0.40, "#EFE6D4"), (0.47, "#C7A07A"), (0.53, "#F2EBDD"), (0.60, "#9C6B4C"), (0.68, "#DDCBB0"),
        (0.76, "#B08463"), (0.84, "#D9CBB4"), (0.92, "#A99580"), (1.01, "#A99580"),
    ]

    def band(v):
        for k in range(len(bands) - 1):
            if bands[k][0] <= v < bands[k + 1][0]:
                return bands[k][1]
        return bands[-1][1]

    img = [[(0, 0, 0, 0) for _ in range(size)] for _ in range(size)]
    for j in range(n):
        for i in range(n):
            x = (i + 0.5) / n * 2.0 - 1.0
            y = (j + 0.5) / n * 2.0 - 1.0
            r2 = x * x + y * y
            if r2 > 1.0:
                continue
            # Les bandes, qu'une volute fait onduler.
            v = (y + 1.0) / 2.0 + 0.012 * math.sin(x * 9.0 + y * 4.0) + rnd.uniform(-0.006, 0.006)
            color = rgb(band(v))
            # La Grande Tache rouge, dans la bande sombre de l'hémisphère sud.
            gx, gy = (x - 0.28) / 0.2, (y - 0.24) / 0.09
            if gx * gx + gy * gy < 1.0:
                color = rgb("#C5563A") if gx * gx + gy * gy < 0.55 else rgb("#D98A6A")
            shade = 0.55 + 0.45 * math.sqrt(1.0 - r2)
            c = tuple(min(255, int(channel * shade)) for channel in color[:3]) + (255,)
            for dy in range(cell):
                for dx in range(cell):
                    img[j * cell + dy][i * cell + dx] = c
    return img


def europa_lander_item():
    """Vaisseau d'Europe : la cabine blanche sur son étage de descente, sous un blindage gris, quatre jambes."""
    img = part_canvas()
    part_cap(img, 1, 6, 9, WHITE_PAINT)
    for i, half in enumerate([2, 2, 3, 3, 4]):
        part_row(img, 2 + i, 8 - half, 7 + half, WHITE_PAINT)
    for y in (4, 5):
        for x in (7, 8):
            img[y][x] = rgb("#3F76E4")
    for y in range(7, 11):
        part_row(img, y, 3, 12, SHIELD)
    for x in range(4, 12, 2):
        img[8][x] = rgb("#F2C14E")
    part_cap(img, 11, 3, 12, SHIELD)
    for k in range(4):
        img[11 + k][2 - min(k, 2)] = img[11 + k][13 + min(k, 2)] = rgb(DARK_METAL[2])
    for x in (0, 1, 14, 15):
        img[15][x] = rgb(DARK_METAL[3])
    return img


def radiation_shield_item():
    """Module de blindage : une plaque grise épaisse, cerclée, frappée du trèfle jaune des radiations."""
    img = part_canvas()
    for y in range(2, 14):
        part_row(img, y, 3, 12, SHIELD)
    part_cap(img, 1, 4, 11, SHIELD)
    part_cap(img, 14, 4, 11, SHIELD)
    for y in range(4, 12):
        for x in range(5, 11):
            img[y][x] = rgb("#F2C14E")
    black = rgb("#1B1C1F")
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8), (6, 5), (7, 5), (8, 5), (9, 5), (5, 9), (6, 10), (10, 9), (9, 10)):
        img[y][x] = black
    return img


def cryobot():
    """Le cryobot, dans son bloc : une plaque sur la glace, la sonde, son portique, son treuil, son générateur, son antenne."""
    parts = [
        # La couronne de la plaque d'ancrage, autour du trou de forage.
        box(1, 0, 1, 15, 1, 4, "frame"), box(1, 0, 12, 15, 1, 15, "frame"),
        box(1, 0, 4, 4, 1, 12, "frame"), box(12, 0, 4, 15, 1, 12, "frame"),
        # La sonde, blanche, cerclée, à demi enfoncée dans son trou.
        box(5.5, 0, 5.5, 10.5, 9, 10.5, "white"),
        box(5.25, 6, 5.25, 10.75, 7, 10.75, "dark"),
        box(6.5, 9, 6.5, 9.5, 10, 9.5, "dark"),
        # Le portique : quatre montants, une traverse, le treuil et son câble.
        box(1.5, 1, 1.5, 3, 14, 3, "frame"), box(13, 1, 1.5, 14.5, 14, 3, "frame"),
        box(1.5, 1, 13, 3, 14, 14.5, "frame"), box(13, 1, 13, 14.5, 14, 14.5, "frame"),
        box(1.5, 14, 7, 14.5, 15, 9, "frame"),
        box(6, 13, 6.5, 10, 16, 9.5, "dark"),
        box(7.6, 10, 7.6, 8.4, 13, 8.4, "dark"),
        # Le générateur à radio-isotopes, contre le portique, et l'antenne de liaison.
        box(12.5, 1, 5.5, 15, 5, 10.5, "rtg"),
        box(0.5, 1, 7, 1.5, 9, 9, "frame"), box(-1, 9, 5.5, 2, 9.5, 10.5, "dish"),
        box(3.5, 1, 3.5, 5, 2, 5, "light"),
    ]
    return parts


def main():
    write_png(os.path.join(BLOCK_DIR, "europa_ice.png"), europa_ice_texture())
    write_png(os.path.join(BLOCK_DIR, "europa_linea_ice.png"), linea_ice_texture())
    write_png(os.path.join(PARTICLE_DIR, "jupiter.png"), jupiter_texture())
    write_png(os.path.join(ITEM_DIR, "europa_lander.png"), europa_lander_item())
    write_png(os.path.join(ITEM_DIR, "suit_module_radiation_shield.png"), radiation_shield_item())
    parts = separate(cryobot())
    overlaps = check_overlaps("cryobot", parts)
    model = build_tiled(parts, ex.TEXTURES, ex.EMISSIVE, particle="orbitalis:block/rover_white")
    with open(os.path.join(ex.MODEL_DIR, "cryobot.json"), "w", newline="\n") as f:
        json.dump(model, f, indent=1)
    print("Europe : textures, icônes et cryobot écrits, %d chevauchement(s)." % overlaps)


if __name__ == "__main__":
    main()
