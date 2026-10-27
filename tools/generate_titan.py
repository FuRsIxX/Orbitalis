#!/usr/bin/env python3
"""
Génère les textures de Titan d'Orbitalis :

- la glace de Titan : de la glace d'eau, dure comme la roche à 94 K, gris beige, fissurée, tachée de tholines ;
- le sable de tholines : des grains bruns de matière organique, tombés de la brume ;
- le vaisseau titanien : la cabine blanche dans sa coque d'entrée, suspendue sous son parachute orange et blanc,
  comme la sonde Huygens ;
- le seau de méthane liquide : un seau de métal, plein d'un liquide brun ambré ;
- le réservoir d'hydrogène du moteur nucléaire : un réservoir sous sa mousse isolante orange.

Le méthane liquide lui-même se dessine avec la texture de l'eau, teintée (voir OrbitalisClient).

Usage : python tools/generate_titan.py
        (réutilise les outils de dessin de generate_textures.py)
"""
import os

from generate_textures import (BLOCK_DIR, DARK_METAL, ITEM_DIR, WHITE_PAINT, blotchy, part_canvas, part_cap, part_row,
                               rgb, tank_item, write_png)

AEROSHELL = ("#3B200E", "#7A4320", "#B5652E", "#D9894A", "#F0B07A")
METAL = ("#3A3D42", "#5E6268", "#8E949B", "#B9BEC4", "#DADDE1")
# Mousse isolante orange, comme celle du réservoir externe de la navette.
FOAM = ("#5A2A0A", "#A8521C", "#D9752E", "#EE9550", "#F8C090")


def titan_ice_texture():
    """Glace de Titan : une glace gris beige, mate, fissurée, avec quelques grains de tholines."""
    img = blotchy(140, [rgb("#C9C3B4"), rgb("#B9B3A3"), rgb("#D8D3C6"), rgb("#A9A292")], [5, 3, 2, 2], passes=2)
    for x, y in ((2, 2), (3, 3), (4, 3), (5, 4), (11, 8), (12, 9), (12, 10), (13, 11), (6, 12), (7, 13), (8, 13)):
        img[y][x] = rgb("#8E8778")
    for x, y in ((9, 2), (1, 9), (14, 14), (10, 12)):
        img[y][x] = rgb("#6B4A2E")
    return img


def tholin_sand_texture():
    """Sable de tholines : des grains bruns orangés, plus clairs par endroits."""
    img = blotchy(141, [rgb("#5E3E24"), rgb("#523520"), rgb("#6B482A"), rgb("#462D1A")], [5, 3, 2, 1], passes=2)
    for x, y in ((3, 2), (12, 4), (7, 9), (1, 13), (14, 11), (9, 14)):
        img[y][x] = rgb("#A0703F")
        img[y][(x + 1) % 16] = rgb("#4A2E18")
    return img


def titan_lander_item():
    """Vaisseau titanien : la cabine dans sa coque d'entrée, sous son parachute rayé orange et blanc."""
    img = part_canvas()
    orange, white, edge = rgb("#E0702A"), rgb("#F2F2F2"), rgb("#7A3A12")
    for x in range(5, 11):
        img[0][x] = edge
    for y, (x0, x1) in ((1, (3, 12)), (2, (2, 13)), (3, (1, 14))):
        for x in range(x0, x1 + 1):
            stripe = orange if (x // 2) % 2 == 0 else white
            img[y][x] = stripe if y < 3 else (rgb("#A84E1A") if stripe == orange else rgb("#BDBDBD"))
        img[y][x0] = img[y][x1] = edge
    # Les suspentes, qui se rejoignent au sommet de la cabine.
    line = rgb("#9AA0A6")
    for y, xs in ((4, (2, 13)), (5, (4, 11)), (6, (6, 9))):
        for x in xs:
            img[y][x] = line
    part_cap(img, 7, 7, 8, WHITE_PAINT)
    for y, half in ((8, 2), (9, 3), (10, 3), (11, 4)):
        part_row(img, y, 8 - half, 7 + half, WHITE_PAINT)
    img[9][7] = rgb("#3F76E4")
    img[9][8] = rgb("#9FE8FF")
    part_row(img, 12, 1, 14, AEROSHELL)
    part_cap(img, 13, 2, 13, AEROSHELL)
    for x in range(5, 11):
        img[14][x] = rgb(DARK_METAL[2])
    return img


def liquid_methane_bucket_item():
    """Seau de méthane liquide : un seau de métal, son anse, et le liquide brun ambré qui affleure."""
    img = part_canvas()
    handle = rgb(METAL[3])
    for x, y in ((3, 4), (3, 3), (4, 2), (5, 1), (6, 1), (7, 0), (8, 0), (9, 1), (10, 1), (11, 2), (12, 3), (12, 4)):
        img[y][x] = handle
    for x in range(2, 14):
        img[5][x] = rgb(METAL[0])
    for y in (6, 7):
        for x in range(3, 13):
            img[y][x] = rgb("#B47A3C") if (y == 6 and 4 <= x <= 6) else rgb("#8A5A2A") if y == 6 else rgb("#6A4220")
        img[y][2] = img[y][13] = rgb(METAL[0])
    for y, (x0, x1) in ((8, (2, 13)), (9, (3, 12)), (10, (3, 12)), (11, (3, 12)), (12, (4, 11)), (13, (4, 11))):
        part_row(img, y, x0, x1, METAL)
    part_cap(img, 14, 4, 11, METAL)
    return img


if __name__ == "__main__":
    blocks = {"titan_ice": titan_ice_texture(), "tholin_sand": tholin_sand_texture()}
    items = {"titan_lander": titan_lander_item(), "liquid_methane_bucket": liquid_methane_bucket_item(), "hydrogen_tank": tank_item(FOAM)}
    for name, img in blocks.items():
        write_png(os.path.join(BLOCK_DIR, name + ".png"), img)
    for name, img in items.items():
        write_png(os.path.join(ITEM_DIR, name + ".png"), img)
    print(f"{len(blocks)} textures de blocs et {len(items)} textures d'objets écrites.")
