#!/usr/bin/env python3
"""
Écrit les seuls modèles des ports de pas de tir (fuel_port_*, dans generate_launch_pad.py), sans réécrire le reste de
ce générateur. Chaque modèle est vérifié : aucune face n'en recouvre une autre.

Il écrit aussi ce qui distingue les deux ports qui ne passent pas de fluide : le port électrique (une conduite
graphite cerclée de jaune, et un éclair sur son étiquette) et le port de données (une conduite verte, et sur son
étiquette des points et des traits de données).

Usage : python tools/write_fuel_ports.py
"""
import json
import os

import generate_launch_pad as pad
import generate_machine_models as gm
from generate_textures import SIZE, rgb
from model_kit import check_overlaps, separate


def draw_bolt(img):
    """Un éclair noir, en biais, sur l'étiquette jaune."""
    for x, y in ((9, 2), (8, 3), (9, 3), (7, 4), (8, 4), (6, 5), (7, 5), (6, 6), (7, 6), (8, 6), (9, 6),
                 (8, 7), (9, 7), (7, 8), (8, 8), (6, 9), (7, 9), (6, 10)):
        img[y][x] = rgb("#1B1C1F")


def draw_data(img):
    """Des points et des traits de données, verts sur fond sombre, et la bande de couleur de la conduite."""
    for y, pattern in ((3, "1101011"), (5, "1011101"), (7, "0110110")):
        for k, bit in enumerate(pattern):
            if bit == "1":
                img[y][3 + k * 1 + k] = rgb("#9DF2B5") if (k + y) % 3 else rgb("#5BE37D")
    for x in range(2, 14):
        img[10][x] = img[11][x] = rgb("#5BE37D")


def write_textures():
    energy = pad.pipe(240, "#2B2F36", {3: "#F2C14E", 7: "#F2C14E", 11: "#F2C14E", 15: "#1B1C1F"})
    pad.write_texture("pad_energy_h", [pad.transposed(energy)])
    pad.write_texture("pad_sign_energy", [pad.sign("#F2C14E", "#1B1C1F", draw_bolt)])
    data = pad.pipe(241, "#1E4A34", {3: "#5BE37D", 11: "#5BE37D", 15: "#123020"})
    pad.write_texture("pad_data_h", [pad.transposed(data)])
    pad.write_texture("pad_sign_data", [pad.sign("#14301F", "#5BE37D", draw_data)])


# Les deux ports sans fluide : leurs textures, et leurs modèles, sur le même caisson que les autres.
pad.PAD_TEXTURES.update({"energyh": "pad_energy_h", "s_energy": "pad_sign_energy", "datah": "pad_data_h", "s_data": "pad_sign_data"})
pad.PARTS["fuel_port_energy"] = pad.fuel_port("energyh", "s_energy")
pad.PARTS["fuel_port_data"] = pad.fuel_port("datah", "s_data")


def main():
    write_textures()
    overlaps = 0
    names = [name for name in pad.PARTS if name.startswith("fuel_port_")]
    for name in names:
        pieces = separate(pad.PARTS[name])
        overlaps += check_overlaps(name, pieces)
        model = gm.build_extra(pieces, pad.PAD_TEXTURES, pad.EMISSIVE)
        if name in pad.DISPLAYS:
            model["display"] = pad.DISPLAYS[name]
        model["textures"]["particle"] = "orbitalis:block/pad_steel"
        with open(os.path.join(gm.MODEL_DIR, name + ".json"), "w", newline="\n") as f:
            json.dump(model, f, indent=2)
    print("Ports de pas de tir : %d modèles écrits, %d chevauchement(s)." % (len(names), overlaps))


if __name__ == "__main__":
    main()
