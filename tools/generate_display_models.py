#!/usr/bin/env python3
"""
Génère le modèle et les textures de l'écran de contrôle d'Orbitalis.

C'est une dalle d'affichage comme celles d'un mur d'images de salle de contrôle : mince (trois pixels), plaquée
contre le mur derrière elle, avec une vitre noire qui en couvre toute la face. Les dalles s'assemblent : la vitre
va d'un bord à l'autre du bloc, et l'image passe d'une dalle à la suivante sans couture. Seul le tour de l'écran
porte un liseré d'aluminium anodisé, fin, qui dépasse à peine de la vitre.

Le modèle est donc découpé : la dalle elle-même, un liseré par côté, et un angle par coin. Le jeu ne montre un
liseré que là où l'écran s'arrête. Le dos porte une plaque de fixation et des ouïes d'aération.

Tout est dessiné pour une dalle tournée vers le nord : la vitre regarde le nord, le dos est au sud. Pour qui
regarde l'écran de face, sa droite est alors à l'ouest (x = 0) et sa gauche à l'est (x = 16).

Usage : python tools/generate_display_models.py
        (à lancer après generate_machine_models.py, dont il réutilise les outils de dessin)
"""
import json
import os

from generate_machine_models import plain, write_texture
from generate_textures import ROOT, SIZE, rgb
from model_kit import check_overlaps

MODEL_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "orbitalis", "models", "block", "display")
# La vitre est à 13 pixels du nord ; le liseré dépasse d'un cinquième de pixel devant elle, et fait un demi-pixel de large.
GLASS = 13
RIM = GLASS - 0.2
EDGE = 0.5


def glass_texture():
    """Vitre éteinte : un noir bleuté, uni, pour que les dalles se fondent les unes dans les autres."""
    return [[rgb("#07090D") for _ in range(SIZE)] for _ in range(SIZE)]


def back_texture():
    """Dos de la dalle : un capot gris sombre, quatre ouïes, et la plaque de fixation au centre."""
    img = plain(311, "#2B3036", "#353B42", "#22262B")
    for y in (2, 4, 11, 13):
        for x in range(3, 13):
            img[y][x] = rgb("#111316")
    for y in range(6, 10):
        for x in range(5, 11):
            img[y][x] = rgb("#5B636D") if (x in (5, 10) or y in (6, 9)) else rgb("#474E57")
    return img


def write_textures():
    write_texture("display_glass", [glass_texture()])
    write_texture("display_back", [back_texture()])
    write_texture("display_shell", [plain(312, "#23272C", "#2C3137", "#1B1E22")])
    write_texture("display_rim", [plain(313, "#59616B", "#6C747F", "#474E57")])


def box(x0, y0, z0, x1, y1, z1, texture, **faces):
    return {"from": [x0, y0, z0], "to": [x1, y1, z1], "texture": texture, "faces": faces}


# La dalle : la vitre devant, la coque derrière, son dos tourné vers le mur.
PIECES = {
    "panel": [
        box(0, 0, GLASS, 16, 16, GLASS + 0.5, "glass"),
        box(0, 0, GLASS + 0.5, 16, 16, 16, "shell", south="back"),
    ],
    "edge_up": [box(EDGE, 16 - EDGE, RIM, 16 - EDGE, 16, GLASS, "rim")],
    "edge_down": [box(EDGE, 0, RIM, 16 - EDGE, EDGE, GLASS, "rim")],
    "edge_right": [box(0, EDGE, RIM, EDGE, 16 - EDGE, GLASS, "rim")],
    "edge_left": [box(16 - EDGE, EDGE, RIM, 16, 16 - EDGE, GLASS, "rim")],
    "corner_up_right": [box(0, 16 - EDGE, RIM, EDGE, 16, GLASS, "rim")],
    "corner_up_left": [box(16 - EDGE, 16 - EDGE, RIM, 16, 16, GLASS, "rim")],
    "corner_down_right": [box(0, 0, RIM, EDGE, EDGE, GLASS, "rim")],
    "corner_down_left": [box(16 - EDGE, 0, RIM, 16, EDGE, GLASS, "rim")],
}
TEXTURES = {"glass": "display_glass", "shell": "display_shell", "back": "display_back", "rim": "display_rim",
            "particle": "display_shell"}


def write_model(name, parts, extra=None):
    used = {part["texture"] for part in parts} | {t for part in parts for t in part["faces"].values()} | {"particle"}
    elements = []
    for part in parts:
        faces = {face: {"texture": "#" + part["faces"].get(face, part["texture"])}
                 for face in ("north", "south", "east", "west", "up", "down")}
        elements.append({"from": part["from"], "to": part["to"], "faces": faces})
    model = {"textures": {key: "orbitalis:block/" + value for key, value in TEXTURES.items() if key in used}, "elements": elements}
    if extra:
        model = {**extra, **model}
    with open(os.path.join(MODEL_DIR, "mission_display_" + name + ".json"), "w", newline="\n") as f:
        json.dump(model, f, indent=1)


if __name__ == "__main__":
    write_textures()
    os.makedirs(MODEL_DIR, exist_ok=True)
    for name, parts in PIECES.items():
        write_model(name, parts)
    whole = [part for parts in PIECES.values() for part in parts]
    overlaps = check_overlaps("mission_display", whole)
    # Dans l'inventaire : une dalle seule, avec tout son liseré, présentée de face et ramenée au centre de la case.
    display = {
        "gui": {"rotation": [20, 200, 0], "translation": [0, 0, 0], "scale": [0.85, 0.85, 0.85]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.3, 0.3, 0.3]},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
        "firstperson_righthand": {"rotation": [0, 135, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
        "firstperson_lefthand": {"rotation": [0, 315, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    }
    moved = [{**part, "from": [part["from"][0], part["from"][1], part["from"][2] - 6.5],
              "to": [part["to"][0], part["to"][1], part["to"][2] - 6.5]} for part in whole]
    write_model("inventory", moved, {"parent": "minecraft:block/block", "display": display})
    print("Ecran de controle ecrit, %d chevauchement(s)." % overlaps)
