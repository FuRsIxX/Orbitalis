#!/usr/bin/env python3
"""
Génère l'établi d'ingénieur d'Orbitalis : son modèle et ses textures.

C'est une table d'architecte, deux blocs de large sur deux de profondeur, haute d'un bloc : une grande planche à
dessin gris graphite, liserée d'orange, inclinée vers le dessinateur sur un piètement d'aluminium brossé. Sur la
planche, une dalle lumineuse éclaire par-dessous le plan qu'on y a posé, un second plan attend à côté, et une règle
parallèle court sur toute la largeur. Derrière elle, une tablette à plat porte une tablette graphique, une maquette de
fusée et le bras d'une rampe de LED. Dessous : des tubes à plans.

Toutes ses textures lui sont propres (graphite, aluminium brossé, dalle lumineuse, plans, tubes, tablette).

Le modèle est dessiné face avant au nord ; le bloc du meuble est celui de devant, à gauche quand on lui fait face.
Les textures gardent partout des pixels de la taille de ceux des blocs, et aucune face n'en recouvre une autre.

Usage : python tools/generate_engineer_workbench.py
        (à lancer après generate_machine_models.py, dont il réutilise les outils de dessin)
"""
import json
import os
import random

import generate_machine_models as gm
from generate_machine_models import box
from generate_textures import SIZE, rgb
from model_kit import build_tiled, check_overlaps, display

TEXTURES = {
    "top": "workbench_top", "alu": "workbench_alu", "light": "workbench_light", "plan": "workbench_blueprint",
    "sketch": "workbench_sketch", "tube": "workbench_tube", "screen": "workbench_screen", "accent": "workbench_accent",
    "white": "workbench_white", "red": "workbench_red", "drawer": "workbench_drawer",
}
EMISSIVE = {"light", "screen"}


# ---------------------------------------------------------------------------
# Textures
# ---------------------------------------------------------------------------
def flat(color, edge=None, speck=None, seed=0, amount=0.06):
    """Aplat mat : une teinte, un liseré facultatif sur le pourtour, et un grain très léger."""
    rnd = random.Random(seed)
    base = rgb(color)
    img = [[base for _ in range(SIZE)] for _ in range(SIZE)]
    if speck:
        for y in range(SIZE):
            for x in range(SIZE):
                if rnd.random() < amount:
                    img[y][x] = rgb(speck)
    if edge:
        for i in range(SIZE):
            img[0][i] = img[SIZE - 1][i] = img[i][0] = img[i][SIZE - 1] = rgb(edge)
    return img


def brushed(seed):
    """Aluminium brossé : de fines stries horizontales, claires et sombres."""
    rnd = random.Random(seed)
    tones = [rgb("#B9C0C8"), rgb("#C9CFD6"), rgb("#A9B1BA"), rgb("#D6DBE0")]
    return [[tones[(y * 3 + (1 if rnd.random() < 0.15 else 0)) % 4] for _ in range(SIZE)] for y in range(SIZE)]


def light_texture():
    """Dalle lumineuse : un blanc bleuté, quadrillé très finement, plus clair au centre."""
    core, glow, grid = rgb("#EAF6FF"), rgb("#CFE9FF"), rgb("#B4D8F5")
    img = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            near = max(abs(x - 7.5), abs(y - 7.5)) > 6.5
            row.append(grid if (x % 4 == 0 or y % 4 == 0) else glow if near else core)
        img.append(row)
    return img


def blueprint_texture():
    """Plan : une feuille bleue quadrillée où se dessine une fusée de profil, avec sa cote."""
    paper, grid, line = rgb("#2463AE"), rgb("#2F74C4"), rgb("#EEF5FC")
    img = [[grid if (x % 4 == 0 or y % 4 == 0) else paper for x in range(SIZE)] for y in range(SIZE)]
    for y, (x0, x1) in {2: (7, 8), 3: (7, 8), 4: (6, 9), 5: (6, 9), 6: (6, 9), 7: (6, 9), 8: (6, 9), 9: (6, 9), 10: (5, 10), 11: (5, 10)}.items():
        img[y][x0] = img[y][x1] = line
    for x in range(6, 10):
        img[12][x] = line
    for y in range(3, 12):
        img[y][13] = line
    img[3][12] = img[11][12] = img[3][14] = img[11][14] = line
    return img


def sketch_texture():
    """Second plan : le même papier, mais un moteur vu en coupe, et un cartouche en bas à droite."""
    paper, grid, line = rgb("#1F5598"), rgb("#2966B0"), rgb("#EEF5FC")
    img = [[grid if (x % 4 == 0 or y % 4 == 0) else paper for x in range(SIZE)] for y in range(SIZE)]
    for x in range(5, 11):
        img[3][x] = img[6][x] = line
    for y in range(3, 7):
        img[y][5] = img[y][10] = line
    for k in range(5):
        img[7 + k][6 - (k // 2)] = img[7 + k][9 + (k // 2)] = line
    for x in range(11, 15):
        img[13][x] = img[14][x] = line
    return img


def tube_texture():
    """Tube à plans : un carton bleu nuit, une bague claire à chaque bout."""
    body, ring = rgb("#27364B"), rgb("#D5DBE2")
    return [[ring if x in (1, 14) else body for x in range(SIZE)] for _ in range(SIZE)]


def screen_frame(step):
    """Tablette : un fond sombre, le tracé d'une fusée, et un curseur qui le parcourt."""
    dark, line, cursor = rgb("#0C1622"), rgb("#7FD0FF"), rgb("#FFB23E")
    img = [[dark for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(3, 13):
        img[y][6] = img[y][9] = line
    img[2][7] = img[2][8] = line
    for x in range(5, 11):
        img[13][x] = line
    for x in range(2, 5):
        img[5][x] = img[9][x] = rgb("#3A8EE0")
    img[3 + step % 10][11] = cursor
    return img


def drawer_texture():
    """Façade du tiroir : le graphite du plateau, un filet clair en haut, un filet sombre en bas."""
    img = flat("#2A2F36", speck="#30363E", seed=823)
    for x in range(SIZE):
        img[0][x] = rgb("#454C56")
        img[SIZE - 1][x] = rgb("#1B1F24")
    return img


def write_textures():
    gm.write_texture("workbench_top", [flat("#2A2F36", speck="#31373F", seed=821)])
    gm.write_texture("workbench_alu", [brushed(822)])
    gm.write_texture("workbench_light", [light_texture()])
    gm.write_texture("workbench_blueprint", [blueprint_texture()])
    gm.write_texture("workbench_sketch", [sketch_texture()])
    gm.write_texture("workbench_tube", [tube_texture()])
    gm.write_texture("workbench_screen", [screen_frame(s) for s in range(10)], 6, interpolate=False)
    gm.write_texture("workbench_accent", [flat("#E8872A", speck="#F2A04A", seed=824, amount=0.1)])
    gm.write_texture("workbench_white", [flat("#ECEFF2", speck="#DCE1E6", seed=825)])
    gm.write_texture("workbench_red", [flat("#C8402F", speck="#D85642", seed=826)])
    gm.write_texture("workbench_drawer", [drawer_texture()])


# ---------------------------------------------------------------------------
# Modèle
# ---------------------------------------------------------------------------
def whole(part):
    """Pièce dont chaque face reçoit la texture entière."""
    return {**part, "stretch": True}


# Inclinaison de la planche, et son axe : le chant avant, autour duquel elle se relève vers l'arrière.
TILT = {"origin": [16, 8, 3], "axis": "x", "angle": -22.5}


def tilted(part):
    """Pièce posée sur la planche inclinée : elle tourne avec elle, texture entière sur chaque face."""
    return {**part, "stretch": True, "rotation": TILT}


def frame(x):
    """Un flanc du piètement : un pied avant court, un pied arrière haut, une semelle et une traverse inclinée."""
    return [
        box(x, 0, 3, x + 1.5, 8, 4.5, "alu"), box(x, 0, 27, x + 1.5, 15, 28.5, "alu"),
        box(x + 0.2, 0.3, 4.5, x + 1.3, 1.5, 27, "alu"),
    ]


WORKBENCH = frame(2) + frame(28.5) + [
    # Deux traverses relient les flancs : une à l'avant, sous la planche, une à l'arrière.
    box(3.5, 5.5, 3.3, 28.5, 6.7, 4.2, "alu"),
    box(3.5, 9, 27.3, 28.5, 10.2, 28.2, "alu"),
    # La planche à dessin, inclinée vers le dessinateur : un grand plateau graphite, son liseré orange sur le chant
    # avant, et la réglette qui retient les plans.
    tilted(box(1, 8, 3, 31, 9.5, 25, "top")),
    tilted(box(1, 8.3, 2.6, 31, 9.2, 3, "accent")),
    tilted(box(1, 9.5, 3, 31, 10.3, 4, "alu")),
    # Sur la planche : la dalle lumineuse et son plan, un second plan, et la règle parallèle sur toute la largeur.
    tilted(box(10, 9.5, 6, 26, 9.7, 20, "light")),
    tilted(box(11.5, 9.7, 7, 23.5, 9.8, 18.5, "plan")),
    tilted(box(2.5, 9.5, 8, 9, 9.6, 19, "sketch")),
    tilted(box(1.5, 9.8, 20.6, 30.5, 10.2, 21.8, "accent")),
    # Derrière la planche, une tablette à plat : la tablette graphique, la maquette de fusée, et le pied de la lampe.
    box(0, 15, 25.5, 32, 16, 32, "top"),
    box(4, 16, 28, 10, 16.4, 31, "alu"),
    whole(box(3.6, 16.4, 29.6, 10.4, 21.4, 30.2, "screen")),
    box(3.4, 16.4, 30.2, 10.6, 21.6, 30.6, "top"),
    box(24, 16, 27, 27, 16.6, 30, "accent"),
    box(24.9, 16.6, 27.9, 26.1, 21.5, 29.1, "white"),
    box(25.1, 21.5, 28.1, 25.9, 22.8, 28.9, "red"),
    # La rampe de LED, sur son bras, qui avance au-dessus de la planche.
    box(15.5, 16, 29.5, 16.5, 23, 30.5, "alu"),
    box(15.5, 23, 17, 16.5, 23.8, 30.5, "alu"),
    box(11, 22.6, 17, 21, 23, 18.6, "light"),
    # Sous la planche : une tablette basse et ses trois tubes à plans.
    box(3.5, 3, 9, 28.5, 4, 23, "alu"),
    whole(box(6, 4, 10, 22, 6, 12, "tube")), whole(box(6, 4, 13, 22, 6, 15, "tube")),
    whole(box(6, 4, 16, 22, 6, 18, "tube")),
]


if __name__ == "__main__":
    write_textures()
    os.makedirs(gm.MODEL_DIR, exist_ok=True)
    for part in WORKBENCH:
        assert all(-16 <= c <= 32 for c in part["from"] + part["to"]), part
        assert all(a < b for a, b in zip(part["from"], part["to"])), part
    overlaps = check_overlaps("engineer_workbench", WORKBENCH)
    model = build_tiled(WORKBENCH, TEXTURES, EMISSIVE, particle="orbitalis:block/workbench_alu",
                        display=display([0, 0, 0], [32, 23, 32], fill=1.3))
    with open(os.path.join(gm.MODEL_DIR, "engineer_workbench.json"), "w", newline="\n") as f:
        json.dump(model, f, indent=1)
    print("Etabli d'ingenieur ecrit, %d chevauchement(s)." % overlaps)
