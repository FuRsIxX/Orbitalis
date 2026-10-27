#!/usr/bin/env python3
"""
Écrit les seuls fichiers du méthalox d'Orbitalis, sans réécrire le reste des générateurs dont ils viennent :

- le moteur méthalox : son bloc de construction (generate_rocket_parts.py) et son icône (generate_textures.py) ;
- le bloc de ravitaillement en méthane, sa conduite et son étiquette (generate_launch_pad.py) ;
- le réacteur de Sabatier, éteint et allumé (generate_machine_models.py), et son interface (generate_textures.py).

Chacun est aussi déclaré dans son générateur d'origine : relancer celui-ci le réécrit à l'identique.

Usage : python tools/generate_methalox.py
"""
import json
import os

import generate_launch_pad as pad
import generate_machine_models as gm
from generate_machine_models import box
import generate_rocket_parts as parts
import generate_textures as tx
from model_kit import check_overlaps, separate


def write_engine():
    """Le bloc du moteur, comme generate_rocket_parts.py l'écrit, et son icône."""
    name = "rocket_engine_methalox"
    pieces = separate(parts.PARTS[name])
    overlaps = check_overlaps(name, pieces, quiet=True)
    model = gm.build_extra(pieces, parts.TEXTURES, parts.EMISSIVE)
    model["textures"]["particle"] = "orbitalis:block/machine_frame"
    with open(os.path.join(gm.MODEL_DIR, name + ".json"), "w", newline="\n") as f:
        json.dump(model, f, indent=2)
    tx.write_png(os.path.join(tx.ITEM_DIR, "methalox_engine.png"),
                 tx.engine_item(tx.ALUMINUM, tx.COPPER, "#7FD9A8", [2, 3, 3, 4, 4, 5, 5]))
    return overlaps


def write_port():
    """La conduite de méthane dans ses deux sens, son étiquette, et le bloc de ravitaillement."""
    pipe = pad.pipe(219, "#8FC7AE", {3: "#2E6B5E", 11: "#2E6B5E", 15: "#4E8A74"}, speckle="#D8F2E6")
    pad.write_texture("pad_ch4", [pipe])
    pad.write_texture("pad_ch4_h", [pad.transposed(pipe)])
    pad.write_texture("pad_sign_ch4", [pad.fluid_sign("#2E6B5E", "CH4", "#8FC7AE")])
    name = "fuel_port_ch4"
    pieces = separate(pad.PARTS[name])
    overlaps = check_overlaps(name, pieces)
    assert name not in pad.TILED
    model = gm.build_extra(pieces, pad.PAD_TEXTURES, pad.EMISSIVE)
    if name in pad.DISPLAYS:
        model["display"] = pad.DISPLAYS[name]
    model["textures"]["particle"] = "orbitalis:block/pad_steel"
    with open(os.path.join(gm.MODEL_DIR, name + ".json"), "w", newline="\n") as f:
        json.dump(model, f, indent=2)
    return overlaps


def write_reactor():
    """Le réacteur de Sabatier, éteint et allumé, et son interface."""
    # Son modèle est écrit par write_machines.py.
    name = "sabatier_reactor"
    tx.write_png(os.path.join(tx.GUI_DIR, name + ".png"), tx.gui_fluid_machine(True, True, 2))
    return 0


def write_helium3():
    """L'extracteur d'hélium-3, éteint et allumé, et son interface."""
    # Son modèle est écrit par write_machines.py.
    name = "helium3_extractor"
    tx.write_png(os.path.join(tx.GUI_DIR, name + ".png"), tx.gui_fluid_machine(True, False, 2))
    return 0


def write_fusion():
    """Le moteur à fusion (son bloc, son icône) et le raccord d'hélium-3."""
    overlaps = 0
    name = "rocket_engine_fusion"
    pieces = separate(parts.PARTS[name])
    overlaps += check_overlaps(name, pieces, quiet=True)
    model = gm.build_extra(pieces, parts.TEXTURES, parts.EMISSIVE)
    model["textures"]["particle"] = "orbitalis:block/machine_frame"
    with open(os.path.join(gm.MODEL_DIR, name + ".json"), "w", newline="\n") as f:
        json.dump(model, f, indent=2)
    tx.write_png(os.path.join(tx.ITEM_DIR, "fusion_engine.png"), fusion_item())
    pipe = pad.pipe(220, "#E8DC9A", {7: "#7A5BB0", 15: "#9C8A4A"})
    pad.write_texture("pad_he3", [pipe])
    pad.write_texture("pad_he3_h", [pad.transposed(pipe)])
    pad.write_texture("pad_sign_he3", [pad.fluid_sign("#4A3A78", "HE3", "#E8DC9A")])
    port = "fuel_port_he3"
    pieces = separate(pad.PARTS[port])
    overlaps += check_overlaps(port, pieces)
    model = gm.build_extra(pieces, pad.PAD_TEXTURES, pad.EMISSIVE)
    model["textures"]["particle"] = "orbitalis:block/pad_steel"
    with open(os.path.join(gm.MODEL_DIR, port + ".json"), "w", newline="\n") as f:
        json.dump(model, f, indent=2)
    return overlaps


def fusion_item():
    """L'icône du moteur à fusion : son bloc de tête, deux anneaux de cuivre autour d'un plasma violet, sa tuyère."""
    img = tx.part_canvas()
    tx.part_cap(img, 1, 5, 10, tx.TITANIUM)
    for y in (2, 3):
        tx.part_row(img, y, 5, 10, tx.TITANIUM)
    for y in (4, 7):
        tx.part_row(img, y, 2, 13, tx.COPPER)
    for y in (5, 6):
        tx.part_row(img, y, 3, 12, tx.DARK_METAL)
        for x in range(5, 11):
            img[y][x] = tx.rgb("#E0B8FF" if 7 <= x <= 8 else "#B26BFF")
    tx.part_row(img, 8, 6, 9, tx.DARK_METAL)
    for i, half in enumerate([2, 3, 3, 4, 4, 5]):
        tx.part_row(img, 9 + i, 8 - half, 7 + half, tx.DARK_METAL)
    tx.part_cap(img, 15, 3, 12, tx.DARK_METAL)
    return img


def plasma_frame(step, frames=8):
    """Le plasma du réacteur : des volutes violettes et roses qui tournent, plus claires au cœur."""
    import math
    img = []
    for y in range(16):
        row = []
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            angle = math.atan2(dy, dx) + step / frames * 2.0 * math.pi
            r = math.hypot(dx, dy)
            wave = 0.5 + 0.5 * math.sin(angle * 3.0 + r * 0.9)
            core = max(0.0, 1.0 - r / 9.0)
            red = int(min(255, 120 + 100 * wave + 80 * core))
            green = int(min(255, 40 + 60 * wave * core + 120 * core * core))
            blue = int(min(255, 190 + 50 * wave + 20 * core))
            row.append((red, green, blue, 255))
        img.append(row)
    return img


def write_fusion_power():
    """Le réacteur à fusion (éteint et allumé), la chambre de Casimir, et l'icône de la matière exotique."""
    # Le réacteur à fusion et la chambre de Casimir : leurs modèles sont écrits par write_machines.py.
    overlaps = 0
    tx.write_png(os.path.join(tx.ITEM_DIR, "exotic_matter.png"), exotic_matter_item())
    return overlaps


def exotic_matter_item():
    """La matière exotique : une capsule de verre cerclée de titane, et au cœur, une goutte d'un noir violacé qui luit."""
    img = tx.part_canvas()
    tx.part_cap(img, 1, 5, 10, tx.TITANIUM)
    tx.part_row(img, 2, 4, 11, tx.TITANIUM)
    for y in range(3, 13):
        for x in range(4, 12):
            img[y][x] = tx.rgb(tx.TITANIUM[0]) if x in (4, 11) else tx.rgb("#BFD6E8") if x == 5 else tx.rgb("#2A1A40")
    glow = {(7, 6): "#E0B8FF", (8, 6): "#B26BFF", (6, 7): "#B26BFF", (7, 7): "#FFFFFF", (8, 7): "#E0B8FF", (9, 7): "#7A3FC7",
            (6, 8): "#7A3FC7", (7, 8): "#E0B8FF", (8, 8): "#B26BFF", (9, 8): "#7A3FC7", (7, 9): "#7A3FC7", (8, 9): "#5A2E94"}
    for (x, y), color in glow.items():
        img[y][x] = tx.rgb(color)
    for y in range(3, 13, 3):
        img[y][6] = tx.rgb("#E8F2FA")
    tx.part_row(img, 13, 4, 11, tx.TITANIUM)
    tx.part_cap(img, 14, 5, 10, tx.TITANIUM)
    return img


def write_creative_tanks():
    """Les réservoirs créatifs de méthane, de méthane liquide et d'hélium-3, comme generate_tank_models.py les écrit."""
    import generate_tank_models as tg
    textures = {"side": "tank_side", "cap": "tank_cap", "base": "tank_base", "frame": "creative_tank_frame",
                "belt": "creative_tank_belt", "particle": "creative_tank_frame"}
    overlaps = 0
    for name, color in tg.CREATIVE:
        if name not in ("methane", "liquid_methane", "helium_3"):
            continue
        gm.write_texture("creative_tank_" + name, [tg.fluid_texture(color)])
        filled = tg.whole_block() + [tg.box(1, 1, 1, 15, 15, 15, "fluid")]
        overlaps += check_overlaps("creative_tank_" + name, filled)
        tg.write_model("creative_tank_" + name, filled, {**textures, "fluid": "creative_tank_" + name}, parent="minecraft:block/block")
    return overlaps


if __name__ == "__main__":
    found = (write_engine() + write_port() + write_reactor() + write_helium3() + write_fusion() + write_fusion_power()
             + write_creative_tanks())
    print("Méthalox : moteur, raccord et réacteur écrits ; %d chevauchement(s)." % found)
