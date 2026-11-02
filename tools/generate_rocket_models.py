#!/usr/bin/env python3
"""
Génère les modèles 3D des tronçons de fusée dressés sur le pas de tir (voir RocketBlock et RocketShape), et leurs
textures.

Un modèle par tronçon (baie de propulsion, réservoirs, interétage, compartiment de charge utile, ogive) et par
diamètre : fin (un demi-bloc), moyen (un bloc) et large (trois blocs). Les modèles larges débordent du bloc
central sur les huit cases voisines, occupées par des flancs invisibles.

Chaque étage a son habit, d'après ce qu'il emporte :
- kérosène : un blanc lisse, et la goulotte de câbles qui court tout le long du corps ;
- hydrogène : la mousse isolante orange, marbrée, et la grosse conduite d'oxygène qui longe le réservoir ;
- inox : l'acier brossé, une soudure en anneau à chaque virole ;
- aluminium-lithium : le métal clair, raidi de lisses verticales ;
- composite : le carbone tissé, noir ;
- poudre : les segments blancs, leurs joints sombres et boulonnés.
Les tronçons reprennent l'aspect des blocs de fusée qu'on pose à leur place (generate_rocket_parts.py) : leur peau, les
deux frettes et la ligne de cuivre des réservoirs, la bague de cuivre des tuyères, la bande de danger
de l'interétage, les trappes cerclées de laiton de la coiffe, la pointe en gradins et sa sonde rouge. Les textures
« rocket_hull_* » ne servent plus qu'aux lèvres des tuyères, aux rails et aux ailerons en grille.
Les boosters latéraux ont leurs propres tronçons, calqués sur les blocs de booster qu'on pose : le corps de poudre
blanc cerclé de ses bagues, sa bande de danger, sa tuyère large rougie par la chaleur, et une pointe à sonde rouge.
Les interétages sont noirs et nervurés, avec leurs quatre ailerons en grille ; la coiffe est blanche, avec ses rails
de séparation. Le corps est un cylindre à seize pans, l'ogive se resserre par petits paliers, les tuyères ont leur
lèvre. Les textures du corps ne portent aucune ligne horizontale qui découperait la fusée bloc par bloc, sauf là où
la vraie en a (les soudures de l'inox, les joints des segments de poudre).

Tout le lanceur parle la même langue, étages comme boosters : chaque panneau du corps a son joint vertical, une arête
claire et une file de rivets ; seule la matière change d'un étage à l'autre. Les tuyères, les mêmes partout, s'évasent
en paliers fins, du col sombre à la lèvre d'acier poli.

Usage : python tools/generate_rocket_models.py   (après generate_machine_models.py)
"""
import json
import math
import os
import random

import generate_machine_models as gm
from generate_textures import ROOT, SIZE, rgb

MODEL_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "orbitalis", "models", "block", "rocket")


# ---------------------------------------------------------------------------
# Textures
# ---------------------------------------------------------------------------
def grain(seed, base, amount):
    rnd = random.Random(seed)
    return [[gm.shade(rgb(base), 1.0 + rnd.uniform(-amount, amount)) for _ in range(SIZE)] for _ in range(SIZE)]


def seam_column(img, x, factor):
    for y in range(SIZE):
        img[y][x] = gm.shade(img[y][x], factor)


def panel(img, rivets=True, depth=0.86):
    """
    Le détail commun à tous les corps, étages et boosters : le joint vertical entre deux panneaux (une ombre, puis une
    arête claire), et une file de rivets qui le longe.
    """
    seam_column(img, 0, depth)
    seam_column(img, 1, 1.05)
    if rivets:
        for y in range(1, SIZE, 4):
            img[y][2] = gm.shade(img[y][2], 0.82)
            img[y + 1][2] = gm.shade(img[y + 1][2], 1.06)
    return img


def hull_white():
    """Kérosène : un blanc lisse, son joint de panneau et ses rivets."""
    return panel(grain(501, "#EEF0F2", 0.015), depth=0.9)


def hull_orange():
    """Hydrogène : la mousse isolante, orange brûlé, marbrée de plaques plus sombres, aux nervures douces."""
    rnd = random.Random(502)
    img = grain(502, "#D9822B", 0.04)
    for _ in range(9):
        cx, cy, r = rnd.uniform(0, 16), rnd.uniform(0, 16), rnd.uniform(1.5, 3.2)
        for y in range(SIZE):
            for x in range(SIZE):
                d = min(abs(x - cx), 16 - abs(x - cx)) ** 2 + min(abs(y - cy), 16 - abs(y - cy)) ** 2
                if d < r * r:
                    img[y][x] = gm.shade(img[y][x], 0.9)
    for x in (3, 11):
        seam_column(img, x, 1.06)
    # La mousse cache les rivets : il ne reste que le joint entre deux pans.
    return panel(img, rivets=False, depth=0.88)


def hull_steel():
    """Inox : l'acier brossé en long, ses reflets, et la soudure d'une virole à l'autre."""
    rnd = random.Random(503)
    img = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            streak = 0.05 * math.sin(x * 1.7) + rnd.uniform(-0.03, 0.03)
            row.append(gm.shade(rgb("#C3C9D0"), 1.0 + streak))
        img.append(row)
    seam_column(img, 5, 1.08)
    for x in range(SIZE):
        img[15][x] = gm.shade(img[15][x], 0.82)
        img[0][x] = gm.shade(img[0][x], 1.08)
    return panel(img, depth=0.84)


def hull_alloy():
    """
    Aluminium-lithium : un métal clair, gris bleuté, raidi de lisses verticales (une ombre et une arête claire), sans
    le quadrillage en losanges qui faisait papier peint.
    """
    img = grain(504, "#C2CBD5", 0.02)
    for x in (6, 11):
        seam_column(img, x, 0.92)
        seam_column(img, x + 1, 1.05)
    return panel(img, depth=0.84)


def hull_composite():
    """Composite : le carbone tissé, noir, à reflet satiné."""
    img = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            weave = ((x // 2) + (y // 2)) % 2
            base = "#23262B" if weave else "#1A1C20"
            row.append(gm.shade(rgb(base), 1.0 + 0.06 * math.sin((x + y) * 0.4)))
        img.append(row)
    return panel(img, rivets=False, depth=0.8)


def hull_solid():
    """
    Poudre : un segment blanc et, en bas, son joint de raccord : une lèvre claire, la bague sombre et ses boulons, puis
    l'ombre qu'elle porte. Le même joint de panneau et les mêmes rivets que les étages.
    """
    img = panel(grain(505, "#E8EAEC", 0.015), depth=0.9)
    for x in range(SIZE):
        img[12][x] = gm.shade(img[12][x], 1.04)
        img[13][x] = rgb("#5A616A")
        img[14][x] = rgb("#4A5058")
        img[15][x] = rgb("#3A3F46")
    for x in range(1, SIZE, 4):
        img[14][x] = rgb("#A9B1BA")
        img[15][x] = rgb("#2C3036")
    return img


def hull_dark():
    """Interétage et jupe des moteurs : un noir mat, raidi de lisses verticales."""
    img = grain(506, "#25282D", 0.03)
    for x in range(0, SIZE, 4):
        seam_column(img, x, 1.35)
        seam_column(img, (x + 1) % SIZE, 0.85)
    return img


def hull_fairing():
    """Coiffe : un blanc satiné, et le joint de ses deux demi-coquilles, riveté comme le reste du lanceur."""
    return panel(grain(507, "#F1F2F4", 0.012), depth=0.9)


def hull_checker():
    """Jupe d'un moteur alimenté par pression : le grand damier noir et blanc des premières fusées."""
    img = []
    for y in range(SIZE):
        img.append([rgb("#ECEEF0") if (x // 8 + y // 8) % 2 == 0 else rgb("#1E2125") for x in range(SIZE)])
    return img


def hull_rail():
    """Rail de séparation, goulotte, conduite : un gris graphite, son arête claire."""
    img = grain(508, "#3A3F46", 0.03)
    seam_column(img, 0, 1.3)
    return img


def hull_feed():
    """Conduite d'oxygène : la même mousse orange, plus claire, cerclée de ses colliers."""
    img = grain(509, "#E39A4A", 0.03)
    for x in range(SIZE):
        img[7][x] = rgb("#9AA2AB")
    return img


def hull_bell():
    """Tuyère : un métal sombre, bleui par la chaleur vers le col, parcouru des tubes de refroidissement."""
    img = []
    for y in range(SIZE):
        t = y / 15.0
        img.append([gm.shade(gm.blend(rgb("#2E3440"), rgb("#55606C"), t), 1.08 if x % 2 == 0 else 0.92) for x in range(SIZE)])
    return img


def hull_bell_copper():
    """Grande tuyère d'un moteur cryogénique : ses tubes de cuivre, côte à côte."""
    img = []
    for y in range(SIZE):
        img.append([gm.shade(rgb("#B8693A"), 1.12 if x % 2 == 0 else 0.86) for x in range(SIZE)])
    return img


def hull_lip():
    """La lèvre d'une tuyère : un acier clair, poli."""
    return grain(510, "#AEB5BD", 0.03)


def gridfin():
    """Aileron en grille : un cadre d'acier sombre, un treillis serré."""
    img = [[rgb("#2B3036") for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            if x in (0, 15) or y in (0, 15):
                img[y][x] = rgb("#5A626C")
            elif (x + y) % 3 == 0 or (x - y) % 3 == 0:
                img[y][x] = rgb("#7F8893")
    return img


TEXTURES = {
    "white": hull_white, "orange": hull_orange, "steel": hull_steel, "alloy": hull_alloy, "composite": hull_composite,
    "solid": hull_solid, "dark": hull_dark, "fairing": hull_fairing, "checker": hull_checker, "rail": hull_rail,
    "feed": hull_feed, "bell": hull_bell, "copper": hull_bell_copper, "lip": hull_lip, "gridfin": gridfin,
}


def write_textures():
    for key, draw in TEXTURES.items():
        gm.write_texture("rocket_hull_" + key, [draw()])


# ---------------------------------------------------------------------------
# Géométrie
# ---------------------------------------------------------------------------
def tiled_box(x0, y0, z0, x1, y1, z1, texture):
    """
    Pavé découpé aux limites des blocs voisins. Chaque morceau reçoit des coordonnées de texture explicites,
    prises dans une seule tuile de 16 pixels : la texture se répète d'un bloc à l'autre au lieu de s'étirer,
    et le modèle garde partout la densité de pixels de Minecraft, même quand il fait trois blocs de large.
    """
    def cuts(a, b):
        points = [a]
        edge = math.floor(a / 16) * 16 + 16
        while edge < b - 1e-6:
            points.append(edge)
            edge += 16
        points.append(b)
        return list(zip(points[:-1], points[1:]))

    def local(a, b):
        start = a - math.floor(a / 16) * 16
        return round(start, 2), round(start + (b - a), 2)

    elements = []
    v0, v1 = round(16 - y1, 2), round(16 - y0, 2)
    for xa, xb in cuts(x0, x1):
        for za, zb in cuts(z0, z1):
            ux0, ux1 = local(xa, xb)
            uz0, uz1 = local(za, zb)
            faces = {
                "north": {"uv": [ux0, v0, ux1, v1], "texture": "#" + texture},
                "south": {"uv": [ux0, v0, ux1, v1], "texture": "#" + texture},
                "east": {"uv": [uz0, v0, uz1, v1], "texture": "#" + texture},
                "west": {"uv": [uz0, v0, uz1, v1], "texture": "#" + texture},
                "up": {"uv": [ux0, uz0, ux1, uz1], "texture": "#" + texture},
                "down": {"uv": [ux0, uz0, ux1, uz1], "texture": "#" + texture},
            }
            elements.append({"from": [xa, y0, za], "to": [xb, y1, zb], "faces": faces})
    return elements


def whole_box(x0, y0, z0, x1, y1, z1, texture):
    """Pavé dont chaque face reçoit la texture entière (un aileron en grille)."""
    faces = {face: {"uv": [0, 0, 16, 16], "texture": "#" + texture} for face in ("north", "south", "east", "west", "up", "down")}
    return [{"from": [x0, y0, z0], "to": [x1, y1, z1], "faces": faces}]


# Les demi-largeurs des pavés croisés d'un cylindre à seize pans, en fraction du rayon.
ROUND16 = ((1.0, 0.4), (0.92, 0.62), (0.78, 0.78), (0.62, 0.92), (0.4, 1.0))
ROUND8 = ((1.0, 0.55), (0.85, 0.85), (0.55, 1.0))


def cylinder(y0, y1, half, texture):
    """Tronçon de cylindre : seize pans dès que le corps fait un bloc de large, huit pour une fusée-sonde."""
    elements = []
    for a, b in (ROUND16 if half >= 6 else ROUND8):
        elements += tiled_box(round(8 - half * a, 2), y0, round(8 - half * b, 2), round(8 + half * a, 2), y1, round(8 + half * b, 2), texture)
    return elements


# Profil d'une tuyère, du col à la sortie : (bas, haut, demi-largeur). Sept paliers fins, pour qu'elle s'évase en douceur
# au lieu de descendre en marches d'escalier.
BELL = ((9.2, 10, 0.9), (8, 9.2, 1.15), (6.6, 8, 1.45), (5.2, 6.6, 1.75), (3.8, 5.2, 2.05), (2.3, 3.8, 2.32), (0.6, 2.3, 2.56))


def bell(cx, cz, scale, texture="bell"):
    """
    Tuyère : un col étroit qui s'évase vers le bas en paliers fins, cerclé d'une bague de cuivre comme sur les blocs de
    moteur, et sa lèvre d'acier poli.
    """
    elements = []
    for y0, y1, half in BELL:
        h = round(half * scale, 2)
        elements += tiled_box(cx - h, y0, cz - h, cx + h, y1, cz + h, texture)
    collar = round(1.95 * scale, 2)
    elements += tiled_box(cx - collar, 5.6, cz - collar, cx + collar, 6.2, cz + collar, "copper")
    lip = round(2.8 * scale, 2)
    return elements + tiled_box(cx - lip, 0, cz - lip, cx + lip, 0.6, cz + lip, "lip")


def around(boxes):
    """Ces pavés (x0, y0, z0, x1, y1, z1, texture), et leurs trois copies tournées d'un quart de tour autour du centre."""
    out = []
    for x0, y0, z0, x1, y1, z1, texture in boxes:
        for _ in range(4):
            out.append((x0, y0, z0, x1, y1, z1, texture))
            x0, z0, x1, z1 = 16 - z1, x0, 16 - z0, x1
    return out


def fins(half, height, sweep):
    """Quatre ailerons en trapèze au pied de la fusée : plus larges en bas, ils remontent en trois paliers."""
    out = 4 if half < 8 else 6
    steps = ((0, height * 0.45, out), (height * 0.45, height * 0.8, out * 0.65), (height * 0.8, height, out * 0.3))
    boxes = []
    for y0, y1, reach in steps:
        reach = round(reach, 2)
        boxes.append((7.4, round(y0, 2), round(8 - half - reach, 2), 8.6, round(y1, 2), round(8 - half + 0.5, 2), sweep))
    elements = []
    for x0, y0, z0, x1, y1, z1, texture in around(boxes):
        elements += tiled_box(x0, y0, z0, x1, y1, z1, texture)
    return elements


def strip(half, side_width, depth, texture, y0=0, y1=16, angle=0):
    """
    Une bande en saillie le long du corps (goulotte, rail, conduite), sur la face donnée par son quart de tour. Un
    lanceur lourd touche déjà les bords permis d'un modèle : sa bande longe alors un angle du cylindre.
    """
    if half > 16:
        corner = round(8 - half * 0.78, 2)
        box = (corner - side_width, y0, corner - side_width, corner + 0.6, y1, corner + 0.6, texture)
    else:
        box = (8 - side_width / 2, y0, 8 - half - depth, 8 + side_width / 2, y1, 8 - half + 0.6, texture)
    turned = around([box])
    x0, y0, z0, x1, y1, z1, texture = turned[angle]
    return tiled_box(round(x0, 2), y0, round(z0, 2), round(x1, 2), y1, round(z1, 2), texture)


def grid_fins(half):
    """Les quatre ailerons en grille, repliés contre le haut de l'interétage."""
    size = 3.0 if half < 16 else 7.0
    elements = []
    for x0, y0, z0, x1, y1, z1, texture in around([(8 - size / 2, 16 - size - 1.5, 8 - half - 0.8, 8 + size / 2, 14.5, 8 - half - 0.2, "gridfin")]):
        elements += whole_box(round(x0, 2), y0, round(z0, 2), round(x1, 2), y1, round(z1, 2), texture)
    return elements


# Peau de chaque tronçon : la même texture que le bloc qu'on pose à sa place.
SKINS = {
    "tank_white": "white", "tank_orange": "orange", "tank_solid": "solid", "tank_steel": "steel", "tank_alloy": "alloy",
    "tank_composite": "composite",
    "interstage": "dark", "payload": "fairing", "nose_lower": "fairing", "nose": "fairing",
    "engine": "dark", "engine_solid": "solid", "engine_pressure": "checker", "engine_hydrolox": "orange",
    "escape_tower": "dark",
}
PARTS = tuple(SKINS) + ("capsule", "capsule_upper", "capsule_mk2", "capsule_mk3", "booster_engine", "booster_body", "booster_nose")

# Nom court d'une texture dans les tronçons, et la texture qu'il désigne : celles des blocs de fusée (generate_rocket_parts.py),
# plus la lèvre des tuyères, les rails et les ailerons en grille propres à la fusée assemblée.
FILES = {
    "white": "rocket_white", "orange": "rocket_orange", "solid": "rocket_solid", "steel": "rocket_steel", "alloy": "rocket_alloy",
    "composite": "rocket_composite", "dark": "rocket_dark", "fairing": "rocket_fairing", "checker": "rocket_checker",
    "bell": "rocket_nozzle", "nozzlec": "rocket_nozzle_copper", "hazard": "pad_hazard", "frame": "machine_frame",
    "copper": "machine_copper", "brass": "pad_brass", "metal": "machine_metal", "red": "machine_red", "port": "machine_port",
    "lip": "rocket_hull_lip", "rail": "rocket_hull_rail", "gridfin": "rocket_hull_gridfin", "nozzle": "rocket_nozzle",
}


def body(y0, y1, half, bands=()):
    """
    Le corps du tronçon, et ses bagues (y0, y1, texture) : en saillie d'un quart de pixel par pixel de rayon, comme les
    frettes des blocs ; un lanceur lourd touche déjà les bords permis d'un modèle, ses bagues affleurent alors le corps.
    """
    if half < 16:
        out = cylinder(y0, y1, half, "body")
        for b0, b1, texture in bands:
            out += cylinder(b0, b1, round(half * 1.05, 2), texture)
        return out
    out, at = [], y0
    for b0, b1, texture in sorted(bands):
        if b0 > at:
            out += cylinder(at, b0, half, "body")
        out += cylinder(b0, b1, half, texture)
        at = b1
    if at < y1:
        out += cylinder(at, y1, half, "body")
    return out


def feed_lines(half):
    """
    La ligne d'alimentation de cuivre, de part et d'autre du réservoir, comme sur les blocs. Sans la vanne de laiton du
    bloc : répétée à chaque bloc d'un long réservoir, elle dessinait une échelle.
    """
    out = []
    for angle in (0, 2):
        out += strip(half, 1.2, 0.6, "copper", angle=angle)
    return out


def probe(top, size):
    """La sonde au sommet d'une pointe : une tige de métal et son bout rouge."""
    half = round(size / 2, 2)
    tip = round(size / 4, 2)
    return (tiled_box(8 - half, top, 8 - half, 8 + half, top + 1.2, 8 + half, "metal")
            + tiled_box(8 - tip, top + 1.2, 8 - tip, 8 + tip, top + 2, 8 + tip, "red"))


# Les boosters latéraux reprennent les textures des blocs de booster (voir generate_rocket_parts.py).
BOOSTER_TEXTURES = {"solid": "rocket_solid", "hazard": "pad_hazard", "nozzle": "rocket_nozzle", "frame": "machine_frame",
                    "copper": "machine_copper", "metal": "machine_metal", "red": "machine_red"}


def booster_model(part, half):
    """
    Un tronçon de booster latéral, à l'image des blocs de booster : le bloc a un rayon de 7 pixels, le tronçon reprend ses
    proportions à son propre rayon.
    """
    k = half / 7.0
    # Les anneaux débordent un peu du corps, sans sortir des limites d'un modèle (pour la taille large, jamais montée).
    ring = min(round(half * 1.05, 2), 24.0)
    if part == "booster_engine":
        # Le corps de poudre, sa bande de danger, et la tuyère large en deux gradins, sans ailerons.
        elements = (cylinder(4, 16, half, "solid") + cylinder(9, 10.5, ring, "hazard")
                    + cylinder(1.5, 4, round(4.4 * k, 2), "nozzle") + cylinder(0, 1.5, round(5.6 * k, 2), "nozzle"))
    elif part == "booster_body":
        # Le corps de poudre, cerclé de deux frettes, et une ligne d'alimentation de cuivre de part et d'autre.
        elements = (cylinder(0, 16, half, "solid") + cylinder(4, 5, ring, "frame")
                    + cylinder(11, 12, ring, "frame"))
        if half >= 6:
            elements += strip(half, 1.2, 0.6, "copper") + strip(half, 1.2, 0.6, "copper", angle=2)
    else:
        # La pointe : un cône en quatre gradins sur son anneau, et la sonde au bout rouge.
        elements = cylinder(0, 1, min(round(half * 1.02, 2), 24.0), "frame")
        for y0, y1, ratio in ((1, 4.5, 1.0), (4.5, 8.5, 0.8), (8.5, 11.5, 0.57), (11.5, 14, 0.36)):
            elements += cylinder(y0, y1, round(half * ratio, 2), "solid")
        size = max(0.5, round(0.8 * k, 2))
        elements += tiled_box(8 - size, 14, 8 - size, 8 + size, 15.2, 8 + size, "metal")
        elements += tiled_box(round(8 - size * 0.5, 2), 15.2, round(8 - size * 0.5, 2), round(8 + size * 0.5, 2), 16,
                              round(8 + size * 0.5, 2), "red")
    used = {face["texture"][1:] for element in elements for face in element["faces"].values()}
    textures = {key: "orbitalis:block/" + BOOSTER_TEXTURES[key] for key in sorted(used)}
    textures["particle"] = "orbitalis:block/rocket_solid"
    return {"ambientocclusion": False, "textures": textures, "elements": elements}


def rocket_model(part, half):
    # La capsule habitée est la même sur la fusée et en vol libre : le tronçon reprend son modèle.
    if part in ("capsule", "capsule_mk2", "capsule_mk3"):
        return {"parent": "orbitalis:block/machine/" + part}
    if part == "capsule_upper":
        # Le haut de la capsule est dessiné par le tronçon du dessous, qui fait deux blocs de haut.
        return {"textures": {"particle": "orbitalis:block/capsule_hull"}}
    if part.startswith("booster_"):
        return booster_model(part, half)
    wide = half > 8
    slim = half < 8
    if part == "escape_tower":
        # Tour de sauvetage : un mât en treillis, et tout en haut le petit moteur-fusée avec sa couronne de tuyères.
        elements = (tiled_box(7.25, 0, 7.25, 8.75, 9, 8.75, "body") + tiled_box(6, 2, 7.6, 10, 2.8, 8.4, "body")
                    + tiled_box(7.6, 5, 6, 8.4, 5.8, 10, "body")
                    + tiled_box(5.75, 9, 5.75, 10.25, 10, 10.25, "bell")
                    + tiled_box(6.25, 10, 6.25, 9.75, 14.5, 9.75, "fairing")
                    + tiled_box(7, 14.5, 7, 9, 16, 9, "fairing"))
    elif part == "nose":
        if wide:
            # Pointe d'une grosse fusée : elle prolonge le bas de l'ogive par petits paliers, et porte la sonde.
            elements = []
            for k, ratio in enumerate((0.8, 0.74, 0.66, 0.57, 0.47, 0.36, 0.25, 0.14)):
                elements += cylinder(2 * k, 2 * k + 2, max(0.6, round(half * ratio, 2)), "body")
            elements += probe(16, 2.4)
        else:
            # Pointe : comme le bloc de pointe, un cône en quatre gradins sur son anneau, et la sonde au bout rouge.
            elements = cylinder(0, 1, round(half * 1.02, 2), "frame")
            for y0, y1, ratio in ((1, 4.5, 1.0), (4.5, 8.5, 0.8), (8.5, 11.5, 0.57), (11.5, 14, 0.36)):
                elements += cylinder(y0, y1, round(half * ratio, 2), "body")
            elements += probe(14, max(1.0, round(half * 0.23, 2)))
    elif part == "nose_lower":
        # Bas de l'ogive : encore presque au diamètre de la fusée, il s'arrondit doucement ; ses coutures comme sur le
        # bloc de coiffe.
        elements = cylinder(0, 6, half, "body") + cylinder(6, 11, round(half * 0.95, 2), "body") + cylinder(11, 16, round(half * 0.88, 2), "body")
        if not slim:
            elements += strip(half, 0.8, 0.3, "rail", 0, 6) + strip(half, 0.8, 0.3, "rail", 0, 6, 2)
            elements += strip(half, 0.8, 0.3, "frame", 0, 6, 1) + strip(half, 0.8, 0.3, "frame", 0, 6, 3)
    elif part == "payload":
        # Coiffe : ses deux rails de séparation, et sur les deux autres faces une trappe de visite cerclée de laiton,
        # comme sur le bloc de baie de charge utile.
        elements = body(0, 16, half)
        if not slim:
            elements += strip(half, 0.8, 0.3, "rail") + strip(half, 0.8, 0.3, "rail", angle=2)
            for angle in (1, 3):
                elements += (strip(half, 2.2, 0.4, "port", 5.5, 10.5, angle) + strip(half, 2.8, 0.5, "brass", 5.1, 5.5, angle)
                             + strip(half, 2.8, 0.5, "brass", 10.5, 10.9, angle))
    elif part == "interstage":
        # Interétage : noir, cerclé de sa bande de danger (les boulons explosifs) et de son anneau de séparation, avec
        # ses ailerons en grille repliés.
        elements = body(1, 16, half, [(7, 9, "hazard")]) + cylinder(0, 1, half if wide else half + 0.3, "lip")
        if not slim and not wide:
            elements += grid_fins(half)
    elif part == "engine":
        # Moteurs à turbopompes : la jupe, le bouclier thermique, et la grappe de tuyères.
        elements = cylinder(10, 16, half, "body") + cylinder(9.4, 10, round(half * 0.96, 2), "rail")
        if wide:
            # Lanceur lourd : une grosse tuyère centrale entourée de quatre autres.
            elements += bell(8, 8, 3.0)
            for dx, dz in ((-13.5, -13.5), (13.5, -13.5), (-13.5, 13.5), (13.5, 13.5)):
                elements += bell(8 + dx, 8 + dz, 3.0)
        elif half >= 8:
            # Lanceur moyen : neuf moteurs en carré, comme sous un lanceur réutilisable.
            for dx in (-4.4, 0, 4.4):
                for dz in (-4.4, 0, 4.4):
                    elements += bell(8 + dx, 8 + dz, 0.78)
        else:
            elements += bell(8, 8, 1.3)
    elif part == "engine_hydrolox":
        # Moteur cryogénique : une jupe isolée, et une seule grande tuyère de cuivre.
        elements = cylinder(10, 16, half, "body") + cylinder(9.4, 10, round(half * 0.96, 2), "rail") \
            + bell(8, 8, 2.4 if wide else 2.5 if half >= 8 else 1.25, "nozzlec")
        if wide:
            for dx, dz in ((-13.5, -13.5), (13.5, -13.5), (-13.5, 13.5), (13.5, 13.5)):
                elements += bell(8 + dx, 8 + dz, 2.4, "nozzlec")
    elif part == "engine_pressure":
        # Moteur alimenté par pression : une jupe à damier, une petite tuyère et quatre ailerons en trapèze.
        elements = cylinder(9, 16, half, "body") + bell(8, 8, 3.6 if wide else 1.9 if half >= 8 else 1.05)
        if not wide:
            elements += fins(half, 11, "dark")
    elif part == "engine_solid":
        # Moteur à poudre : le corps descend presque en bas, cerclé de sa frette comme le bloc ; la tuyère dépasse à
        # peine, quatre ailerons le stabilisent.
        elements = body(4, 16, half, [(12.5, 13.5, "frame")]) + bell(8, 8, 4.6 if wide else 2.2 if half >= 8 else 1.15)
        if not wide:
            elements += fins(half, 12, "dark")
    else:
        # Réservoirs : la virole, cerclée de deux frettes, et la ligne d'alimentation de cuivre et sa vanne, comme sur
        # les blocs de réservoir.
        elements = body(0, 16, half, [(4, 5, "frame"), (11, 12, "frame")])
        if not slim:
            elements += feed_lines(half)
    skin = FILES[SKINS[part]]
    textures = {"body": "orbitalis:block/" + skin, "particle": "orbitalis:block/" + skin}
    # Seules les textures utilisées par le tronçon sont déclarées.
    used = {face["texture"][1:] for element in elements for face in element["faces"].values()}
    for key in sorted(used - {"body"}):
        textures[key] = "orbitalis:block/" + FILES[key]
    # Pas d'ombrage de contact : la fusée déborde de son bloc et s'entoure de flancs invisibles,
    # ce qui fausserait le calcul et l'assombrirait entièrement.
    return {"ambientocclusion": False, "textures": textures, "elements": elements}

if __name__ == "__main__":
    write_textures()
    os.makedirs(MODEL_DIR, exist_ok=True)
    count = 0
    for part in PARTS:
        for size, half in (("slim", 4), ("medium", 8), ("large", 24)):
            model = rocket_model(part, half)
            for element in model.get("elements", []):
                assert all(-16 <= c <= 32 for c in element["from"] + element["to"]), (part, size, element)
                assert all(a < b for a, b in zip(element["from"], element["to"])), (part, size, element)
            with open(os.path.join(MODEL_DIR, "%s_%s.json" % (part, size)), "w", newline="\n") as f:
                json.dump(model, f, indent=2)
            count += 1
    print(f"{count} modèles de tronçons de fusée écrits, et leurs textures.")
