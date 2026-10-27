#!/usr/bin/env python3
"""
Écrit les fichiers du chantier naval orbital d'Orbitalis, sans réécrire le reste des générateurs de la station :

- le pupitre du chantier : un pupitre sur pied, son clavier, et derrière, son écran où défile le plan du vaisseau ;
- l'écran du pupitre, animé : la silhouette du vaisseau en bleu de plan, qu'un trait de balayage parcourt ;
- l'icône du module chantier naval : un berceau de poutres, et le vaisseau couché dedans.

Il réutilise les outils de dessin et les textures de generate_station.py (habillage des consoles, clavier, voyant).

Usage : python tools/generate_shipyard.py
"""
import json
import os

import generate_machine_models as gm
import generate_station as gs
from generate_machine_models import box
from generate_textures import ITEM_DIR, SIZE, TRANSPARENT, rgb, write_png
from model_kit import build_tiled, check_overlaps, separate


def yard_screen(step, frames=8):
    """
    Écran du pupitre : sur un fond de plan quadrillé, le vaisseau vu de profil (sa coque, l'anneau, la verrière de la
    proue), qu'un trait de balayage parcourt de la poupe à la proue ; en bas, la jauge d'avancement.
    """
    img = gs.canvas("#06111C")
    for x in range(0, SIZE, 3):
        for y in range(0, SIZE, 3):
            gs.dot(img, x, y, "#0C2133")
    # La coque, de la poupe (à gauche) à la proue (à droite).
    gs.rect(img, 2, 6, 13, 9, "#3F8FD0")
    gs.rect(img, 2, 6, 13, 6, "#7FC4F0")
    gs.rect(img, 14, 7, 14, 8, "#9FE8FF")
    # L'anneau, vu par la tranche, et ses rayons.
    gs.rect(img, 5, 2, 6, 3, "#7FC4F0")
    gs.rect(img, 5, 12, 6, 13, "#7FC4F0")
    gs.rect(img, 5, 4, 6, 5, "#2E6B9E")
    gs.rect(img, 5, 10, 6, 11, "#2E6B9E")
    # Le balayage : une colonne claire qui avance.
    sweep = 2 + (step * 12) // frames
    for y in range(1, 14):
        if img[y][sweep] != rgb("#06111C") and img[y][sweep] != rgb("#0C2133"):
            gs.dot(img, sweep, y, "#E6F7FF")
        else:
            gs.dot(img, sweep, y, "#123A5A")
    # La jauge d'avancement, et un voyant qui palpite.
    for x in range(1, 15):
        gs.dot(img, x, 15, "#16324D")
    for x in range(1, 10):
        gs.dot(img, x, 15, "#F0B95A")
    gs.dot(img, 14, 1, "#5BE08A" if step % 2 == 0 else "#2E6B45")
    return img


# Le pupitre, face avant au nord : un socle, un pied, la tablette du clavier en saillie, et l'écran dressé derrière.
CONSOLE = [
    box(1, 0, 2, 15, 1, 15, "dark"),
    box(3, 1, 5, 13, 8, 13, "console"),
    {**box(1, 8, 1, 15, 10, 12, "console"), "faces": {"up": "keys"}},
    box(1, 8, 12, 15, 16, 14, "dark"),
    gs.front_only(gs.whole(box(2, 10.5, 11.7, 14, 15.5, 12, "yard_screen"))),
    box(12.5, 15.6, 11.8, 13.5, 15.9, 12, "led"),
]


def shipyard_icon():
    """Le module chantier naval : un berceau carré de poutres grises, le vaisseau blanc couché dedans, son anneau bleuté."""
    img = [[TRANSPARENT for _ in range(SIZE)] for _ in range(SIZE)]
    for k in range(1, 15):
        for x, y in ((k, 1), (k, 14), (1, k), (14, k)):
            img[y][x] = rgb("#9AA3AE") if (x + y) % 2 else rgb("#6B747F")
    for k in range(2, 14):
        if k % 3 == 0:
            img[2][k] = img[13][k] = rgb("#4A515A")
    # La coque.
    for x in range(3, 13):
        for y in (7, 8):
            img[y][x] = rgb("#FFFFFF" if y == 7 else "#C9CED4")
    img[7][12] = rgb("#9FE8FF")
    img[8][12] = rgb("#3F76E4")
    for y in (7, 8):
        img[y][3] = rgb("#5A626C")
    # L'anneau, par la tranche, et ses rayons.
    for y in range(3, 13):
        img[y][6] = rgb("#7FC4F0") if y in (3, 4, 11, 12) else img[y][6]
        img[y][7] = rgb("#3F76E4") if y in (3, 4, 11, 12) else img[y][7]
    for y in (5, 6, 9, 10):
        img[y][6] = rgb("#8C949E")
    # Le liseré de chantier, ambre.
    for x in range(4, 12, 2):
        img[13][x] = rgb("#F0B95A")
    return img


def main():
    gm.write_texture("shipyard_screen", [yard_screen(k) for k in range(8)], 4, interpolate=False)
    textures = {**gs.TEXTURES, "yard_screen": "shipyard_screen"}
    emissive = gs.EMISSIVE | {"yard_screen"}
    parts = separate(CONSOLE)
    overlaps = check_overlaps("shipyard_console", parts)
    model = build_tiled(parts, textures, emissive, particle="orbitalis:block/station_hull")
    with open(os.path.join(gs.STATION_DIR, "shipyard_console.json"), "w", newline="\n") as f:
        json.dump(model, f, indent=1)
    write_png(os.path.join(ITEM_DIR, "shipyard_module.png"), shipyard_icon())
    print("Chantier naval : pupitre, écran et icône écrits ; %d chevauchement(s)." % overlaps)


if __name__ == "__main__":
    main()
