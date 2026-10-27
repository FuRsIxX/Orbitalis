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
- aluminium-lithium : le métal clair et son isogrille gravée ;
- composite : le carbone tissé, noir ;
- poudre : les segments blancs, leurs joints sombres et boulonnés.
Les interétages sont noirs et nervurés, avec leurs quatre ailerons en grille ; la coiffe est blanche, avec ses rails
de séparation. Le corps est un cylindre à seize pans, l'ogive se resserre par petits paliers, les tuyères ont leur
lèvre. Les textures du corps ne portent aucune ligne horizontale qui découperait la fusée bloc par bloc, sauf là où
la vraie en a (les soudures de l'inox, les joints des segments de poudre).

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


def hull_white():
    """Kérosène : un blanc lisse, un joint de panneau vertical à peine marqué."""
    img = grain(501, "#EEF0F2", 0.012)
    seam_column(img, 0, 0.9)
    seam_column(img, 1, 1.02)
    return img


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
    return img


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
    return img


def hull_alloy():
    """Aluminium-lithium : un métal clair, gris bleuté, et son isogrille gravée en triangles."""
    img = grain(504, "#B9C3CE", 0.02)
    for y in range(SIZE):
        for x in range(SIZE):
            if y % 8 == 0 or (x + y) % 8 == 0 or (x - y) % 8 == 0:
                img[y][x] = gm.shade(img[y][x], 0.9)
    return img


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
    return img


def hull_solid():
    """Poudre : un segment blanc, son joint sombre en bas, boulonné, et le filet de lumière au-dessus."""
    img = grain(505, "#E8EAEC", 0.015)
    for x in range(SIZE):
        img[14][x] = rgb("#4A5058")
        img[15][x] = rgb("#3A3F46")
        img[13][x] = gm.shade(img[13][x], 0.92)
    for x in range(1, SIZE, 4):
        img[14][x] = rgb("#9AA2AB")
    return img


def hull_dark():
    """Interétage et jupe des moteurs : un noir mat, raidi de lisses verticales."""
    img = grain(506, "#25282D", 0.03)
    for x in range(0, SIZE, 4):
        seam_column(img, x, 1.35)
        seam_column(img, (x + 1) % SIZE, 0.85)
    return img


def hull_fairing():
    """Coiffe : un blanc satiné, d'un seul tenant."""
    img = grain(507, "#F1F2F4", 0.01)
    seam_column(img, 8, 0.94)
    return img


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
    """Tuyère : un métal sombre, bleui par la chaleur vers le col."""
    img = []
    for y in range(SIZE):
        t = y / 15.0
        img.append([gm.shade(gm.blend(rgb("#2E3440"), rgb("#4A505A"), t), 1.0 + 0.04 * ((x * 5) % 3 - 1)) for x in range(SIZE)])
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


def bell(cx, cz, scale, texture="bell"):
    """Tuyère : un col étroit qui s'évase vers le bas en quatre paliers, et sa lèvre d'acier poli."""
    elements = []
    for y0, y1, half in ((8, 10, 1.0), (5, 8, 1.6), (2, 5, 2.2), (0.6, 2, 2.6)):
        h = round(half * scale, 2)
        elements += tiled_box(cx - h, y0, cz - h, cx + h, y1, cz + h, texture)
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


# Texture du corps de chaque tronçon.
BODIES = {
    "tank_white": "white", "tank_orange": "orange", "tank_solid": "solid", "tank_steel": "steel", "tank_alloy": "alloy",
    "tank_composite": "composite",
    "interstage": "dark", "payload": "fairing", "nose_lower": "fairing", "nose": "fairing",
    "engine": "dark", "engine_solid": "solid", "engine_pressure": "checker", "engine_hydrolox": "orange",
    "escape_tower": "dark",
}
PARTS = tuple(BODIES) + ("capsule", "capsule_upper", "capsule_mk2", "capsule_mk3")


def rocket_model(part, half):
    # La capsule habitée est la même sur la fusée et en vol libre : le tronçon reprend son modèle.
    if part in ("capsule", "capsule_mk2", "capsule_mk3"):
        return {"parent": "orbitalis:block/machine/" + part}
    if part == "capsule_upper":
        # Le haut de la capsule est dessiné par le tronçon du dessous, qui fait deux blocs de haut.
        return {"textures": {"particle": "orbitalis:block/capsule_hull"}}
    body = BODIES[part]
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
        # Pointe : l'ogive se resserre par paliers de deux pixels jusqu'au sommet ; sur une grosse fusée, elle prolonge
        # le bas de l'ogive.
        ratios = (0.8, 0.74, 0.66, 0.57, 0.47, 0.36, 0.25, 0.14) if wide else (1.0, 0.93, 0.83, 0.7, 0.56, 0.42, 0.28, 0.14)
        elements = []
        for k, ratio in enumerate(ratios):
            elements += cylinder(2 * k, 2 * k + 2, max(0.6, round(half * ratio, 2)), "body")
    elif part == "nose_lower":
        # Bas de l'ogive : encore presque au diamètre de la fusée, il s'arrondit doucement.
        elements = cylinder(0, 6, half, "body") + cylinder(6, 11, round(half * 0.95, 2), "body") + cylinder(11, 16, round(half * 0.88, 2), "body")
        if not slim:
            elements += strip(half, 0.8, 0.3, "rail", 0, 6) + strip(half, 0.8, 0.3, "rail", 0, 6, 2)
    elif part == "payload":
        # Coiffe : d'un seul tenant, et ses deux rails de séparation, face à face.
        elements = cylinder(0, 16, half, "body")
        if not slim:
            elements += strip(half, 0.8, 0.3, "rail") + strip(half, 0.8, 0.3, "rail", angle=2)
    elif part == "interstage":
        # Interétage : noir, nervuré, cerclé d'un anneau de séparation, et ses ailerons en grille repliés.
        elements = cylinder(1, 16, half, "body") + cylinder(0, 1, half if wide else half + 0.3, "lip")
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
            + bell(8, 8, 2.4 if wide else 2.5 if half >= 8 else 1.25, "copper")
        if wide:
            for dx, dz in ((-13.5, -13.5), (13.5, -13.5), (-13.5, 13.5), (13.5, 13.5)):
                elements += bell(8 + dx, 8 + dz, 2.4, "copper")
    elif part == "engine_pressure":
        # Moteur alimenté par pression : une jupe à damier, une petite tuyère et quatre ailerons en trapèze.
        elements = cylinder(9, 16, half, "body") + bell(8, 8, 3.6 if wide else 1.9 if half >= 8 else 1.05)
        if not wide:
            elements += fins(half, 11, "dark")
    elif part == "engine_solid":
        # Moteur à poudre : le corps descend presque en bas, la tuyère dépasse à peine, quatre ailerons le stabilisent.
        elements = cylinder(4, 16, half, "body") + bell(8, 8, 4.6 if wide else 2.2 if half >= 8 else 1.15)
        if not wide:
            elements += fins(half, 12, "dark")
    else:
        elements = cylinder(0, 16, half, "body")
        if not slim:
            if part == "tank_white":
                # La goulotte de câbles, tout le long du corps.
                elements += strip(half, 1.6, 0.8, "rail")
            elif part == "tank_orange":
                # La conduite d'oxygène, qui longe le réservoir d'hydrogène.
                elements += strip(half, 2.4 if half < 16 else 4.0, 1.6 if half < 16 else 2.6, "feed")
            elif part == "tank_steel":
                # Deux goulottes face à face.
                elements += strip(half, 1.2, 0.6, "rail", angle=1) + strip(half, 1.2, 0.6, "rail", angle=3)
    textures = {"body": "orbitalis:block/rocket_hull_" + body, "particle": "orbitalis:block/rocket_hull_" + body}
    # Seules les textures utilisées par le tronçon sont déclarées.
    used = {face["texture"][1:] for element in elements for face in element["faces"].values()}
    for key in sorted(used - {"body"}):
        textures[key] = "orbitalis:block/rocket_hull_" + key
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
