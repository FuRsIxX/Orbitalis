#!/usr/bin/env python3
"""
Écrit les seuls modèles des blocs de ravitaillement (fuel_port_*, dans generate_launch_pad.py), sans réécrire le reste
de ce générateur. Chaque modèle est vérifié : aucune face n'en recouvre une autre.

Usage : python tools/write_fuel_ports.py
"""
import json
import os

import generate_launch_pad as pad
import generate_machine_models as gm
from model_kit import check_overlaps, separate


def main():
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
    print("Ravitaillement : %d modèles écrits, %d chevauchement(s)." % (len(names), overlaps))


if __name__ == "__main__":
    main()
