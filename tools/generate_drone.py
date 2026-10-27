#!/usr/bin/env python3
"""
Génère le drone volant d'Orbitalis, inspiré de Dragonfly, le giravion que la NASA envoie sur Titan :

- son corps : un caisson blanc sur deux patins, un générateur à radio-isotopes doré à l'arrière, une caméra et ses
  phares à l'avant, une antenne sur le dessus, et quatre bras en croix qui portent chacun deux rotors superposés ;
- son rotor : une pale double et son moyeu, que le rendu dessine huit fois et fait tourner, les deux d'un même bras
  en sens contraires ;
- son icône.

Il réutilise les textures de l'exploration robotique (generate_exploration.py). Aucune face n'en recouvre une autre.

Usage : python tools/generate_drone.py
"""
import json
import os

import generate_exploration as ex
from generate_machine_models import box
from generate_textures import ITEM_DIR, rgb, write_png
from model_kit import build_tiled, check_overlaps, separate

TEXTURES = dict(ex.TEXTURES)
EMISSIVE = {"light"}
# Centres des quatre moyeux, en pixels (le corps occupe le milieu du bloc), et hauteur des deux rotors de chaque bras.
HUBS = ((-3.5, 8.0), (19.5, 8.0), (8.0, -3.5), (8.0, 19.5))
ROTOR_LOW = 8.4
ROTOR_HIGH = 11.6


def body():
    parts = [
        # Les patins et leurs jambes.
        box(2, 0, 1, 3, 1, 15, "dark"), box(13, 0, 1, 14, 1, 15, "dark"),
    ]
    for x in (2.25, 12.75):
        for z in (4, 11):
            parts.append(box(x, 1, z, x + 1, 4, z + 1, "frame"))
    parts += [
        # Le caisson, son pont, sa caméra et ses deux phares.
        box(3, 4, 3, 13, 9, 13, "white"),
        box(3.5, 9, 3.5, 12.5, 9.5, 12.5, "frame"),
        box(6.5, 5.5, 2.4, 9.5, 8, 3, "dark"), box(7.25, 6.25, 2.1, 8.75, 7.25, 2.4, "lens"),
        box(4, 7.5, 2.6, 5.5, 8.5, 3, "light"), box(10.5, 7.5, 2.6, 12, 8.5, 3, "light"),
        # Le générateur à radio-isotopes, à l'arrière, et ses ailettes.
        box(5.5, 5, 13, 10.5, 8.5, 15.5, "rtg"),
        box(5, 5.5, 15.5, 6, 8, 16, "frame"), box(10, 5.5, 15.5, 11, 8, 16, "frame"),
        # Le mât et l'antenne.
        box(7.5, 9.5, 7.5, 8.5, 11, 8.5, "frame"), box(6, 11, 6, 10, 11.5, 10, "dish"),
        # Les deux poutres en croix, qui portent les moyeux.
        box(-3, 9.5, 7.5, 19, 10.5, 8.5, "frame"), box(7.5, 9.6, -3, 8.5, 10.4, 19, "frame"),
    ]
    for hx, hz in HUBS:
        parts.append(box(hx - 1, ROTOR_LOW + 0.4, hz - 1, hx + 1, ROTOR_HIGH - 0.1, hz + 1, "dark"))
    return parts


def rotor():
    """Une pale double sur son moyeu, centrée au milieu du bloc, au ras de son bas."""
    return [
        box(1, 0, 7.4, 15, 0.35, 8.6, "dark"),
        box(7.2, 0, 7.2, 8.8, 0.7, 8.8, "frame"),
    ]


def drone_item():
    """L'icône : le drone de face, son caisson blanc, ses patins, et ses rotors étendus de part et d'autre."""
    size = 16
    img = [[(0, 0, 0, 0) for _ in range(size)] for _ in range(size)]

    def put(x, y, color):
        if 0 <= x < size and 0 <= y < size:
            img[y][x] = rgb(color)

    for x in range(0, 16):
        put(x, 3, "#A9B3BF")
    for x in (0, 1, 14, 15, 5, 6, 9, 10):
        put(x, 2, "#C5CED8")
    for x in range(1, 15):
        put(x, 4, "#4A5058")
    for y in (5, 6):
        put(2, y, "#4A5058")
        put(13, y, "#4A5058")
    for y in range(6, 12):
        for x in range(4, 12):
            put(x, y, "#EEF0F2" if x < 9 else "#D4D8DC")
        put(4, y, "#9AA0A6")
        put(11, y, "#9AA0A6")
    for x in range(4, 12):
        put(x, 6, "#FFFFFF")
        put(x, 11, "#9AA0A6")
    put(7, 8, "#1B1F25")
    put(8, 8, "#3F76E4")
    put(5, 7, "#FFE9A6")
    put(10, 7, "#FFE9A6")
    for y in (12, 13):
        put(5, y, "#4A5058")
        put(10, y, "#4A5058")
    for x in range(2, 14):
        put(x, 14, "#2E3238")
    return img


def main():
    os.makedirs(ex.MODEL_DIR, exist_ok=True)
    overlaps = 0
    for name, build in (("rotor_drone", body), ("rotor_drone_rotor", rotor)):
        parts = separate(build())
        overlaps += check_overlaps(name, parts)
        model = build_tiled(parts, TEXTURES, EMISSIVE, particle="orbitalis:block/rover_white")
        with open(os.path.join(ex.MODEL_DIR, name + ".json"), "w", newline="\n") as f:
            json.dump(model, f, indent=1)
    write_png(os.path.join(ITEM_DIR, "rotor_drone.png"), drone_item())
    print("Drone : modèles et icône écrits, %d chevauchement(s)." % overlaps)


if __name__ == "__main__":
    main()
