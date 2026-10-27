#!/usr/bin/env python3
"""
Génère les textures et les modèles des blocs des centres spatiaux d'Orbitalis (les bâtiments que l'on trouve dans
le monde) :

- béton de coffrage, et sa version fissurée : un panneau gris chaud, ses joints, ses trous de banche, quelques
  coulures ;
- carrelage technique, rayures de sécurité ;
- bardage métallique nervuré (blanc, bleu, orange, rouillé), ses rangées de vis ;
- persiennes, panneau de radôme à facettes, vitrage de façade, grillage d'antenne ;
- le mobilier : la baie de serveurs (un bloc plein : une armoire graphite, sa porte vitrée, ses rails vissés, un
  commutateur, un panneau de brassage, des serveurs, une baie de stockage, un onduleur, leurs voyants qui clignotent) et la table à dessin (sa planche inclinée, le plan qu'on y étale).

Elles gardent l'allure des blocs du jeu (16x16, un grain léger, un relief simple), sans en copier aucun.

Usage : python tools/generate_facilities.py
"""
import json
import os
import random

from generate_textures import BLOCK_DIR, SIZE, rgb, write_png
from model_kit import build_tiled, check_overlaps, separate

MODEL_DIR = os.path.join(os.path.dirname(BLOCK_DIR), "..", "models", "block", "facility")
CLEAR = (0, 0, 0, 0)


def tone(color, factor):
    r, g, b, a = color
    return (min(255, max(0, round(r * factor))), min(255, max(0, round(g * factor))), min(255, max(0, round(b * factor))), a)


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (a[3],)


def grain(seed, base, amount=0.03):
    rand = random.Random(seed)
    return [[tone(base, 1.0 + rand.uniform(-amount, amount)) for _ in range(SIZE)] for _ in range(SIZE)]


# ---------------------------------------------------------------------------
# Béton, carrelage, rayures
# ---------------------------------------------------------------------------
def concrete(seed=301):
    """
    Béton coulé, banché à la planche : le gris chaud d'avant, mais lisse, en quatre planches horizontales de quatre
    pixels, chacune de sa nuance, avec le fil du bois à peine marqué et le joint fin entre deux planches. Les arêtes
    sont chanfreinées (un filet clair en haut et à gauche, une ombre en bas et à droite), et deux trous de banche,
    chacun de sa forme, sont posés sur la ligne médiane.
    """
    rand = random.Random(seed + 1)
    base = rgb("#BEBBB3")
    img = [[base for _ in range(SIZE)] for _ in range(SIZE)]
    for plank in range(4):
        f = rand.uniform(0.975, 1.025)
        streaks = [rand.uniform(-0.012, 0.012) for _ in range(4)]
        for dy in range(4):
            y = plank * 4 + dy
            for x in range(SIZE):
                grain_f = 1.0 + streaks[dy] + rand.uniform(-0.01, 0.01)
                # Le fil du bois : de longues traînées à peine plus sombres.
                if (x + plank * 5 + dy * 7) % 11 == 0:
                    grain_f -= 0.018
                img[y][x] = tone(base, f * grain_f)
        # Le joint entre deux planches : une ligne fine, un rien plus sombre.
        if plank > 0:
            for x in range(SIZE):
                img[plank * 4][x] = tone(img[plank * 4][x], 0.955)
    # Les granulats qui affleurent, rares.
    for _ in range(10):
        x, y = rand.randrange(SIZE), rand.randrange(SIZE)
        img[y][x] = tone(img[y][x], rand.choice((0.92, 1.06)))
    # Les deux trous de banche, sur la ligne médiane : chacun sa place, sa forme, sa profondeur.
    for x0 in (4, 11):
        x = x0 + rand.choice((-1, 0, 0))
        y = 7 + rand.choice((0, 0, 1))
        if rand.random() < 0.6:
            img[y][x] = tone(rgb("#7E7B74"), rand.uniform(0.95, 1.05))
            img[y][x + 1] = rgb("#95928A")
            img[y + 1][x] = rgb("#A09D95")
            img[y + 1][x + 1] = rgb("#CFCCC4")
        else:
            img[y][x] = tone(rgb("#86837B"), rand.uniform(0.95, 1.05))
            img[y + 1][x] = rgb("#C9C6BE")
    # Le chanfrein des arêtes.
    for k in range(SIZE):
        img[0][k] = tone(img[0][k], 1.07)
        img[k][0] = tone(img[k][0], 1.05)
        img[SIZE - 1][k] = tone(img[SIZE - 1][k], 0.86)
        img[k][SIZE - 1] = tone(img[k][SIZE - 1], 0.88)
    return img


def cracked_concrete():
    img = concrete(305)
    dark, light = rgb("#77746D"), rgb("#CFCCC4")
    for start, steps in (((2, 0), [(1, 1), (0, 1), (1, 1), (1, 0), (0, 1), (1, 1), (0, 1), (-1, 1), (0, 1)]),
                         ((15, 9), [(-1, 0), (-1, 1), (-1, 0), (0, 1), (-1, 1), (-1, 0), (-1, 1)])):
        x, y = start
        for dx, dy in steps:
            img[y % SIZE][x % SIZE] = dark
            img[(y + 1) % SIZE][x % SIZE] = mix(img[(y + 1) % SIZE][x % SIZE], light, 0.4)
            x, y = x + dx, y + dy
    # Un éclat, et la mousse qui prend dans une fissure.
    for x, y in ((9, 6), (10, 6), (9, 7)):
        img[y][x] = rgb("#D2CFC7")
    for x, y in ((3, 2), (4, 3), (12, 12)):
        img[y][x] = mix(img[y][x], rgb("#5E7A3A"), 0.55)
    return img


def tiles():
    """Carrelage technique : quatre dalles par bloc, chanfreinées, des joints gris, un grain antidérapant."""
    img = grain(311, rgb("#CDD1D4"), 0.015)
    rand = random.Random(312)
    for y in range(SIZE):
        for x in range(SIZE):
            lx, ly = x % 8, y % 8
            if lx == 0 or ly == 0:
                img[y][x] = rgb("#9AA0A6")
            elif lx == 7 or ly == 7:
                img[y][x] = tone(img[y][x], 0.93)
            elif lx == 1 or ly == 1:
                img[y][x] = tone(img[y][x], 1.04)
            elif rand.random() < 0.05:
                img[y][x] = tone(img[y][x], 0.92)
    return img


def hazard():
    """Rayures de sécurité : des bandes en biais, jaunes et noires, usées sur les arêtes."""
    rand = random.Random(321)
    yellow, black = rgb("#E2B42A"), rgb("#2A2A2B")
    img = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            c = yellow if ((x + y) // 4) % 2 == 0 else black
            c = tone(c, 1.0 + rand.uniform(-0.04, 0.04))
            edge = x in (0, SIZE - 1) or y in (0, SIZE - 1)
            if rand.random() < (0.18 if edge else 0.04):
                c = mix(c, rgb("#8C8A84"), 0.55)
            row.append(c)
        img.append(row)
    return img


# ---------------------------------------------------------------------------
# Bardages, persiennes, radôme, vitrage, grillage
# ---------------------------------------------------------------------------
def siding(base, seed, rust=False):
    """Bardage nervuré : une nervure toutes les quatre colonnes, éclairée d'un côté ; deux rangées de vis."""
    img = grain(seed, base, 0.012)
    for y in range(SIZE):
        for x in range(SIZE):
            f = (1.1, 1.0, 0.96, 0.84)[x % 4]
            img[y][x] = tone(img[y][x], f * (1.025 - 0.035 * y / SIZE))
    for y in (1, 14):
        for x in range(2, SIZE, 4):
            img[y][x] = tone(base, 0.62)
            img[y + 1][x] = tone(base, 1.08)
    for x in range(SIZE):
        img[SIZE - 1][x] = tone(img[SIZE - 1][x], 0.86)
    if rust:
        rand = random.Random(seed + 1)
        streaks = rgb("#9A5A2E"), rgb("#B8763F"), rgb("#7E4A28")
        # La rouille part des vis et coule.
        for x in range(2, SIZE, 4):
            if rand.random() < 0.8:
                length = rand.randint(4, 11)
                for y in range(2, min(SIZE, 2 + length)):
                    t = 0.25 + 0.55 * (1 - (y - 2) / length)
                    img[y][x] = mix(img[y][x], rand.choice(streaks), t)
                    if rand.random() < 0.4:
                        img[y][x + 1 if x + 1 < SIZE else x] = mix(img[y][x + 1 if x + 1 < SIZE else x], streaks[0], t * 0.5)
        for x in range(SIZE):
            for y in (13, 14, 15):
                img[y][x] = mix(img[y][x], rgb("#6E5A48"), 0.13 * (y - 12))
    return img


def siding_top(base):
    """Le dessus du bardage : une tôle plate, bordée, ses vis aux angles."""
    img = grain(331, tone(base, 0.95), 0.012)
    for k in range(SIZE):
        img[0][k] = img[SIZE - 1][k] = img[k][0] = img[k][SIZE - 1] = tone(base, 0.8)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        img[y][x] = tone(base, 0.65)
    return img


def louvers():
    """Persiennes : un cadre blanc, des lames inclinées toutes les trois rangées, l'ombre derrière."""
    base = rgb("#E3E5E7")
    img = grain(341, base, 0.012)
    for y in range(SIZE):
        for x in range(SIZE):
            if x in (0, SIZE - 1) or y in (0, SIZE - 1):
                img[y][x] = tone(base, 0.8)
            elif x in (1, SIZE - 2) or y in (1, SIZE - 2):
                img[y][x] = tone(base, 1.04)
            else:
                img[y][x] = tone(base, (1.07, 0.9, 0.5)[(y - 2) % 3])
    return img


def radome():
    """Panneau de radôme : une toile blanche tendue sur des triangles, aux joints gris clair."""
    img = grain(351, rgb("#F1F1EE"), 0.01)
    seam = rgb("#CDD0D2")
    for y in range(SIZE):
        local = y % 8
        shift = 4 if (y // 8) % 2 else 0
        for x in range(SIZE):
            u = (x + shift) % 8
            if local == 0 or u == local or u == (8 - local) % 8:
                img[y][x] = seam
            elif local == 1:
                img[y][x] = tone(img[y][x], 1.02)
            elif local == 7:
                img[y][x] = tone(img[y][x], 0.97)
    return img


def glass():
    """Vitrage de façade : un montant en aluminium tout autour, une vitre bleutée, deux reflets en biais."""
    frame, inner = rgb("#B3BAC2"), rgb("#7C848D")
    img = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            if x in (0, SIZE - 1) or y in (0, SIZE - 1):
                row.append(tone(frame, 1.08 if y == 0 or x == 0 else 0.9))
            elif x in (1, SIZE - 2) or y in (1, SIZE - 2):
                row.append(inner)
            else:
                d = x + y
                if 4 <= d <= 6:
                    row.append((222, 240, 252, 135))
                elif d == 9:
                    row.append((214, 236, 250, 105))
                else:
                    row.append((150, 194, 224, 62 + (y - 2) * 2))
        img.append(row)
    return img


def mesh():
    """Grillage d'antenne : une tôle blanche perforée de trous carrés réguliers (on voit au travers)."""
    base = rgb("#E4E6E8")
    img = grain(361, base, 0.02)
    for y in range(SIZE):
        for x in range(SIZE):
            if x % 4 in (1, 2) and y % 4 in (1, 2):
                img[y][x] = CLEAR
            elif x % 4 == 3 or y % 4 == 3:
                img[y][x] = tone(img[y][x], 0.88)
    return img


# ---------------------------------------------------------------------------
# Mobilier : bureau à écrans, baie de serveurs
# ---------------------------------------------------------------------------
def plain(seed, base, border=None, amount=0.015):
    img = grain(seed, base, amount)
    if border:
        for k in range(SIZE):
            img[0][k] = img[SIZE - 1][k] = img[k][0] = img[k][SIZE - 1] = border
    return img


# ---------------------------------------------------------------------------
# Baie de serveurs : une armoire graphite, sa porte vitrée, des serveurs d'une unité, leurs voyants
# ---------------------------------------------------------------------------
GRAPHITE = rgb("#1E2226")
SLATE = rgb("#2C3238")
STEEL = rgb("#4A525B")
SILVER = rgb("#AEB6BF")


# Les unités de la baie, de haut en bas : chacune fait deux rangées de pixels (une « unité » de baie).
UNITS = ("switch", "patch", "server", "server", "storage", "storage", "ups")


def rack_side():
    """
    Le flanc de l'armoire : un panneau graphite mat dans son cadre, des perforations d'aération en haut et en bas,
    la poignée encastrée et la serrure du panneau amovible.
    """
    img = grain(403, GRAPHITE, 0.02)
    for k in range(SIZE):
        img[0][k] = tone(SLATE, 1.15)
        img[SIZE - 1][k] = tone(GRAPHITE, 0.8)
        img[k][0] = tone(SLATE, 1.05)
        img[k][SIZE - 1] = tone(GRAPHITE, 0.82)
    for y in (2, 3, 12, 13):
        for x in range(3, 13):
            if (x + y) % 2 == 0:
                img[y][x] = tone(GRAPHITE, 0.5)
    for y in range(6, 10):
        img[y][7] = tone(GRAPHITE, 0.6)
        img[y][8] = tone(SLATE, 1.2)
    img[7][11] = SILVER
    return img


def rack_top():
    """Le dessus : une tôle graphite, deux ventilateurs dans leurs grilles, et le passe-câbles à brosse au fond."""
    img = grain(404, GRAPHITE, 0.02)
    for k in range(SIZE):
        img[0][k] = img[SIZE - 1][k] = img[k][0] = img[k][SIZE - 1] = SLATE
    for cx in (4.5, 11.5):
        for y in range(SIZE):
            for x in range(SIZE):
                d = ((x - cx) ** 2 + (y - 6.5) ** 2) ** 0.5
                if d <= 3.2:
                    img[y][x] = tone(GRAPHITE, 0.5) if int(d * 1.6) % 2 == 0 else tone(STEEL, 0.9)
                if d <= 0.8:
                    img[y][x] = STEEL
    for x in range(3, 13):
        img[12][x] = tone(GRAPHITE, 0.45)
        img[13][x] = tone(GRAPHITE, 0.6) if x % 2 else tone(GRAPHITE, 0.4)
    return img


def rack_front():
    """
    L'intérieur de la baie, derrière sa porte vitrée : les deux rails à oreilles vissées, et de haut en bas un
    commutateur (sa rangée de ports), un panneau de brassage et ses câbles de couleur, deux serveurs (aérations et
    baies de disques), une baie de stockage de deux unités (ses disques debout) et l'onduleur (son petit écran).
    Le léger reflet de la vitre passe en biais.
    """
    img = [[tone(GRAPHITE, 0.6) for _ in range(SIZE)] for _ in range(SIZE)]
    rand = random.Random(405)
    unit = 0
    while unit < len(UNITS):
        kind = UNITS[unit]
        y = 1 + unit * 2
        for x in range(3, 13):
            if kind == "switch":
                img[y][x] = tone(SLATE, 1.1)
                img[y + 1][x] = tone(GRAPHITE, 0.4) if x % 2 else tone(STEEL, 1.1)
            elif kind == "patch":
                img[y][x] = tone(STEEL, 0.9) if x % 2 else tone(GRAPHITE, 0.45)
                img[y + 1][x] = rand.choice((rgb("#3D7BE0"), rgb("#E0B030"), rgb("#5BC27A"), rgb("#D9DDE1")))
            elif kind == "server":
                img[y][x] = tone(SLATE, 1.2)
                img[y + 1][x] = tone(SLATE, 0.9) if x < 8 else (tone(STEEL, 1.2) if x % 2 else tone(GRAPHITE, 0.5))
                if 4 <= x <= 7 and x % 2 == 0:
                    img[y + 1][x] = tone(GRAPHITE, 0.4)
            elif kind == "storage":
                # Deux unités d'un coup : des disques debout, leurs poignées.
                for dy in range(4):
                    img[y + dy][x] = tone(STEEL, 1.15) if x % 2 else tone(GRAPHITE, 0.45)
                    if dy == 3 and x % 2:
                        img[y + dy][x] = tone(SLATE, 0.9)
            elif kind == "ups":
                img[y][x] = tone(SLATE, 1.05)
                img[y + 1][x] = tone(SLATE, 0.95)
                if 9 <= x <= 11:
                    img[y][x] = rgb("#173E5C")
        # Les oreilles des rails, vissées à chaque unité.
        rows = 4 if kind == "storage" else 2
        for dy in range(rows):
            img[y + dy][2] = SILVER
            img[y + dy][13] = SILVER
        img[y][2] = img[y][13] = tone(GRAPHITE, 0.5)
        unit += 2 if kind == "storage" else 1
    # Les rails eux-mêmes, en haut et en bas.
    for x in range(2, 14):
        img[0][x] = tone(SLATE, 0.9)
        img[15][x] = tone(SLATE, 0.9)
    # Le reflet de la vitre, en biais.
    for y in range(SIZE):
        for x in range(SIZE):
            if 10 <= x + y <= 11:
                img[y][x] = mix(img[y][x], rgb("#DCE8F2"), 0.16)
    return img


def rack_frame():
    """
    Le cadre de la porte : un profilé d'aluminium anodisé noir, une arête claire, et la poignée de la porte
    (colonnes 0 et 1, que seul le montant droit montre).
    """
    img = grain(406, rgb("#2A2F35"), 0.02)
    for k in range(SIZE):
        img[k][15] = tone(SILVER, 0.75)
    for y in range(6, 10):
        img[y][0] = SILVER
        img[y][1] = tone(SILVER, 0.7)
    img[8][0] = tone(SILVER, 1.1)
    return img


def rack_leds(frame):
    """
    Les voyants, sur un fond transparent, calés sur les unités de la façade : les ports du commutateur qui
    clignotent, l'alimentation (bleue) et l'activité (verte) des serveurs, leurs disques (ambre), ceux de la baie de
    stockage (verts), et l'écran de l'onduleur.
    """
    img = [[CLEAR for _ in range(SIZE)] for _ in range(SIZE)]
    rand = random.Random(500 + frame)
    unit = 0
    while unit < len(UNITS):
        kind = UNITS[unit]
        y = 1 + unit * 2
        if kind == "switch":
            for x in range(3, 13, 2):
                if rand.random() < 0.6:
                    img[y][x] = rgb("#4ADE80")
        elif kind == "server":
            img[y][3] = rgb("#4FB3FF")
            if rand.random() < 0.7:
                img[y][4] = rgb("#4ADE80")
            for x in (9, 11):
                if rand.random() < 0.5:
                    img[y][x] = rgb("#FFC24A")
        elif kind == "storage":
            for x in range(3, 13, 2):
                if rand.random() < 0.55:
                    img[y + 3][x] = rgb("#4ADE80")
        elif kind == "ups":
            img[y][4] = rgb("#4ADE80")
            for x in range(9, 12):
                if (x + frame) % 3:
                    img[y][x] = rgb("#7FD8FF")
        unit += 2 if kind == "storage" else 1
    return img


def write_animated(name, frames, frametime):
    sheet = [row for frame in frames for row in frame]
    write_png(os.path.join(BLOCK_DIR, name + ".png"), sheet)
    with open(os.path.join(BLOCK_DIR, name + ".png.mcmeta"), "w", newline="\n") as f:
        json.dump({"animation": {"frametime": frametime}}, f, indent=2)


# ---------------------------------------------------------------------------
# Table à dessin : un piètement d'acier, la planche inclinée, sa règle, le plan étalé
# ---------------------------------------------------------------------------
def drafting_board():
    """La planche : une surface vert pâle, quadrillée finement, sa bordure."""
    img = grain(411, rgb("#D9E2DA"), 0.012)
    for y in range(SIZE):
        for x in range(SIZE):
            if x % 4 == 0 or y % 4 == 0:
                img[y][x] = tone(img[y][x], 0.94)
    for k in range(SIZE):
        img[0][k] = img[SIZE - 1][k] = img[k][0] = img[k][SIZE - 1] = rgb("#7E8A84")
    return img


def drafting_plan():
    """Le plan étalé : un bleu de tirage, une fusée tracée au trait blanc, son cartouche."""
    img = grain(412, rgb("#2B5C9E"), 0.02)
    line = rgb("#E4EEF8")
    for y in range(SIZE):
        for x in range(SIZE):
            if x % 4 == 0 or y % 4 == 0:
                img[y][x] = tone(img[y][x], 1.08)
    # La fusée, couchée : la coiffe à droite, les étages, les tuyères à gauche.
    for x in range(3, 12):
        img[6][x] = line
        img[9][x] = line
    for y in range(6, 10):
        img[y][3] = line
        img[y][7] = line
    img[7][12] = img[8][12] = line
    img[6][12] = img[9][12] = tone(line, 0.8)
    img[7][13] = img[8][13] = line
    img[6][2] = img[9][2] = line
    img[5][1] = img[10][1] = line
    # Le cartouche, en bas à droite.
    for x in range(10, 15):
        img[13][x] = line
    for y in range(12, 15):
        img[y][10] = line
    return img


TEXTURES = {
    "rack": "server_rack_side", "rack_top": "server_rack_top", "rack_front": "server_rack_front", "rack_frame": "server_rack_frame",
    "leds": "server_rack_leds", "metal": "server_rack_frame",
    "steel": "drafting_table_frame", "board": "drafting_table_board", "plan": "drafting_table_plan",
}
EMISSIVE = {"leds"}


def box(x0, y0, z0, x1, y1, z1, texture, **extra):
    return {"from": [x0, y0, z0], "to": [x1, y1, z1], "texture": texture, **extra}


def drafting(with_plan):
    """Façade au nord : le côté où l'on se tient. La planche monte vers le fond."""
    tilt = {"angle": -22.5, "axis": "x", "origin": [8, 12, 8]}
    parts = [
        box(1, 0, 2, 2, 12, 3, "steel"), box(14, 0, 2, 15, 12, 3, "steel"),
        box(1, 0, 13, 2, 12, 14, "steel"), box(14, 0, 13, 15, 12, 14, "steel"),
        box(2, 3, 13, 14, 4, 14, "steel"), box(2, 3, 2, 14, 4, 3, "steel"),
        box(0, 11.5, 1, 16, 12.5, 15, "board", faces={"down": "steel", "north": "steel", "south": "steel", "east": "steel", "west": "steel"},
            rotation=tilt),
        box(1, 12.5, 1.2, 15, 13.2, 2, "steel", rotation=tilt),
    ]
    if with_plan:
        parts.append(box(2.5, 12.5, 3, 13.5, 12.6, 13.5, "plan", stretch=True, only=("up",), rotation=tilt))
    return parts


def models():
    """
    Façade au nord (z = 0) : la porte de la baie, le côté de la table où l'on se tient. La baie est un bloc plein :
    l'armoire occupe toute la case, sa porte vitrée à fleur, les serveurs un pixel en retrait derrière la vitre.
    """
    rack = [
        box(0, 0, 1, 16, 16, 16, "rack", faces={"north": "rack_front", "up": "rack_top", "down": "rack_top"}),
        box(0, 0, 0, 16, 1, 1, "rack_frame"), box(0, 15, 0, 16, 16, 1, "rack_frame"),
        box(0, 1, 0, 1.5, 15, 1, "rack_frame"), box(14.5, 1, 0, 16, 15, 1, "rack_frame"),
        # Les voyants, calés pixel pour pixel sur la façade des serveurs.
        box(1.5, 1, 0.95, 14.5, 15, 1, "leds", only=("north",)),
    ]
    return {"server_rack": rack, "drafting_table": drafting(False), "drafting_table_plan": drafting(True)}


def main():
    out = {
        "facility_concrete": concrete(),
        "facility_concrete_1": concrete(321),
        "facility_concrete_2": concrete(341),
        "cracked_facility_concrete": cracked_concrete(),
        "facility_tiles": tiles(),
        "hazard_stripes": hazard(),
        "facility_siding_side": siding(rgb("#E6E8EA"), 371),
        "facility_siding_top": siding_top(rgb("#E6E8EA")),
        "blue_facility_siding_side": siding(rgb("#2F5DA8"), 372),
        "blue_facility_siding_top": siding_top(rgb("#2F5DA8")),
        "orange_facility_siding_side": siding(rgb("#DD6E28"), 374),
        "orange_facility_siding_top": siding_top(rgb("#DD6E28")),
        "rusty_facility_siding_side": siding(rgb("#DADBD8"), 373, rust=True),
        "rusty_facility_siding_top": siding_top(rgb("#C9C3B8")),
        "louvered_panel": louvers(),
        "radome_panel": radome(),
        "facility_glass": glass(),
        "antenna_mesh": mesh(),
        "server_rack_side": rack_side(),
        "server_rack_top": rack_top(),
        "server_rack_front": rack_front(),
        "server_rack_frame": rack_frame(),
        "drafting_table_frame": plain(413, rgb("#4A525B"), amount=0.03),
        "drafting_table_board": drafting_board(),
        "drafting_table_plan": drafting_plan(),
    }
    for name, img in out.items():
        write_png(os.path.join(BLOCK_DIR, name + ".png"), img)
    write_animated("server_rack_leds", [rack_leds(k) for k in range(4)], 10)
    os.makedirs(MODEL_DIR, exist_ok=True)
    overlaps = 0
    for name, parts in models().items():
        parts = separate(parts)
        overlaps += check_overlaps(name, parts)
        particle = "orbitalis:block/" + (TEXTURES["rack"] if name == "server_rack" else TEXTURES["steel"])
        model = build_tiled(parts, TEXTURES, EMISSIVE, particle=particle)
        with open(os.path.join(MODEL_DIR, name + ".json"), "w", newline="\n") as f:
            json.dump(model, f, indent=1)
    print("centres spatiaux : %d textures, %d modèles, %d chevauchement(s)" % (len(out) + 1, len(models()), overlaps))


if __name__ == "__main__":
    main()
