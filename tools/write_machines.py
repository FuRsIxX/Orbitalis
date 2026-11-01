#!/usr/bin/env python3
"""
Les machines d'Orbitalis : leurs modèles et leurs textures. C'est le seul script qui les écrit.

Toutes partagent un même modèle, simple et presque plein : un cube ; en façade, un écran et une grille en retrait d'un
demi-pixel. Chacune a pourtant sa livrée, qui la fait reconnaître de loin :

- la couleur de sa coque (graphite pour le générateur, ocre pour la fonderie, acier bleu pour le laminoir, blanc pour
  l'électrolyseur, glace pour le liquéfacteur, rouille pour la raffinerie...) ;
- sa couleur d'accent, reprise par le pictogramme et la jauge de l'écran, la lueur de la grille et les coins du dessus ;
- le motif de ses flancs (ailettes, bandes de danger, panneaux, conduites, nervures, givre, cerclages...), de son dessus
  (ventilateur, cheminée, trappe, bouchons, vanne, trémie, cœur) et de sa grille (lamelles, résistance, treillis,
  barreaux, voyants) ;
- son pictogramme : flamme (générateur), éclair (fonderie à arc), cylindres (laminoir), goutte (électrolyseur),
  flocon (liquéfacteur), colonne (raffinerie), fiole (Sabatier), croissant de lune (hélium-3), plaques (Casimir),
  atome (fusion), réservoir (cryogénique), foret (derrick), fusée (ordinateur de vol).

En marche, la jauge de l'écran monte cran par cran, le témoin clignote et la grille s'anime de la couleur de la machine.
Le dos de toutes porte le port d'énergie, le dessous est un socle sombre : câbles et tuyaux s'y raccordent proprement.

La pompe à eau n'est pas une machine de ce modèle : c'est une vraie pompe, sur son socle, avec son corps, son moteur, son
boîtier électrique à l'arrière (où se branche le câble) et sa bride de sortie à l'avant (où se branche le tuyau).

Usage : python tools/write_machines.py
"""
import json
import math
import os
import random

import generate_machine_models as gm
from generate_machine_models import box
from generate_textures import SIZE, rgb
from model_kit import check_overlaps, separate

EDGE = "#2B3036"
TRIM = "#3B4249"
BOLT = "#C9CFD6"
DARK = "#1C2025"

SCREEN = "#0C1014"
# Écran et grille de la façade, en pixels de texture (bornes incluses) : l'écran en haut, la grille en bas.
SX0, SX1, SY0, SY1 = 2, 13, 2, 9
GY0, GY1 = 11, 14
# Les pictogrammes des machines, cinq pixels sur cinq.
ICONS = {
    "flame": ["..#..", ".##.#", ".####", "#####", ".###."],
    "bolt": ["...##", "..##.", ".####", "..##.", ".##.."],
    "rollers": [".###.", ".###.", "#####", ".###.", ".###."],
    "drop": ["..#..", ".###.", "#####", "#####", ".###."],
    "snow": ["#.#.#", ".###.", "##.##", ".###.", "#.#.#"],
    "tower": [".###.", ".#.#.", ".###.", ".#.#.", "#####"],
    "flask": ["..#..", "..#..", ".###.", "##.##", "#####"],
    "moon": [".###.", "##...", "#....", "##...", ".###."],
    "plates": ["#...#", "#.#.#", "#...#", "#.#.#", "#...#"],
    "atom": [".###.", "#.#.#", "##.##", "#.#.#", ".###."],
    "tank": [".###.", "#...#", "#####", "#####", ".###."],
    "rocket": ["..#..", ".###.", ".###.", ".###.", "#.#.#"],
    "drill": ["#####", ".###.", ".###.", "..#..", "..#.."],
}


def canvas(color):
    return [[rgb(color) for _ in range(SIZE)] for _ in range(SIZE)]


def put(img, x, y, color):
    if 0 <= x < SIZE and 0 <= y < SIZE:
        img[y][x] = rgb(color) if isinstance(color, str) else color


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    ca = rgb(a) if isinstance(a, str) else a
    cb = rgb(b) if isinstance(b, str) else b
    return tuple(int(ca[i] + (cb[i] - ca[i]) * t) for i in range(3)) + (255,)


def light(color, t):
    return mix(color, "#FFFFFF", t)


def shade(color, t):
    return mix(color, "#000000", t)


def shell(seed, body):
    """La coque : une tôle de la couleur de la machine, à peine nuancée, bordée d'un filet sombre, éclairée en haut à gauche."""
    rnd = random.Random(seed)
    tones = (rgb(body), rgb(body), rgb(body), light(body, 0.06), shade(body, 0.06))
    img = [[rnd.choice(tones) for _ in range(SIZE)] for _ in range(SIZE)]
    for k in range(SIZE):
        put(img, k, 0, EDGE)
        put(img, k, SIZE - 1, EDGE)
        put(img, 0, k, EDGE)
        put(img, SIZE - 1, k, EDGE)
    for k in range(1, SIZE - 1):
        put(img, k, 1, light(body, 0.3))
        put(img, 1, k, light(body, 0.3))
        put(img, k, SIZE - 2, shade(body, 0.16))
        put(img, SIZE - 2, k, shade(body, 0.16))
    return img


def slats(img, x0, x1, y0, y1):
    """Des lamelles d'aération, sombres et claires une rangée sur deux."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(img, x, y, DARK if (y - y0) % 2 == 0 else "#5A626C")


def seam(img, y, body):
    """Un joint de tôle horizontal : une ligne d'ombre soulignée d'un filet de lumière."""
    for x in range(2, SIZE - 2):
        put(img, x, y, shade(body, 0.18))
        put(img, x, y + 1, light(body, 0.22))


def bolts(img, points, body=None):
    for x, y in points:
        put(img, x, y, BOLT if body is None else light(body, 0.45))


def disc(img, cx, cy, radius, color, rim=None):
    """Un disque plein, cerclé d'un filet s'il est demandé."""
    for y in range(SIZE):
        for x in range(SIZE):
            d = math.hypot(x - cx, y - cy)
            if d <= radius:
                put(img, x, y, rim if rim is not None and d > radius - 1 else color)


# ---------------------------------------------------------------------------
# Les flancs : un motif par machine
# ---------------------------------------------------------------------------
def side_vents(img, body, accent, extra):
    """Un joint de tôle, un filet d'accent et une grille d'aération."""
    seam(img, 4, body)
    for x in range(3, SIZE - 3):
        put(img, x, 7, accent)
    slats(img, 4, 11, 10, 12)


def side_fins(img, body, accent, extra):
    """Des ailettes de refroidissement verticales sur toute la hauteur, et un filet d'accent au pied."""
    for y in range(3, 12):
        for x in range(3, 13):
            put(img, x, y, shade(body, 0.4) if x % 2 == 0 else light(body, 0.14))
    for x in range(3, 13):
        put(img, x, 2, shade(body, 0.2))
        put(img, x, 13, accent)


def side_hazard(img, body, accent, extra):
    """Un joint de tôle, et une large bande de danger jaune et noire : il y a un arc électrique là-dedans."""
    seam(img, 4, body)
    for y in range(8, 13):
        for x in range(2, 14):
            put(img, x, y, "#F2C14E" if ((x + y) // 2) % 2 == 0 else DARK)
    for x in range(2, 14):
        put(img, x, 7, shade(body, 0.3))
        put(img, x, 13, shade(body, 0.3))


def side_grid(img, body, accent, extra):
    """Quatre panneaux vissés, séparés par leurs joints, et une étiquette d'accent."""
    for k in range(2, 14):
        put(img, 7, k, shade(body, 0.3))
        put(img, 8, k, light(body, 0.2))
        put(img, k, 7, shade(body, 0.3))
        put(img, k, 8, light(body, 0.2))
    for x in range(3, 6):
        put(img, x, 3, accent)
        put(img, x, 4, shade(accent, 0.25))
    bolts(img, ((5, 6), (10, 6), (5, 10), (10, 10)), body)


def side_pipes(img, body, accent, extra):
    """Deux conduites de couleur le long du flanc, tenues par leurs colliers."""
    seam(img, 3, body)
    for row, color in ((6, accent), (10, extra or "#9AA3AD")):
        for x in range(2, 14):
            put(img, x, row, light(color, 0.3))
            put(img, x, row + 1, shade(color, 0.25))
        for x in (4, 11):
            put(img, x, row - 1, TRIM)
            put(img, x, row, TRIM)
            put(img, x, row + 1, TRIM)
            put(img, x, row + 2, TRIM)


def side_ribs(img, body, accent, extra):
    """Une tôle nervurée, et une bande d'accent qui la traverse."""
    for y in range(2, 14):
        for x in range(2, 14):
            put(img, x, y, shade(body, 0.28) if x % 3 == 0 else light(body, 0.12) if x % 3 == 1 else rgb(body))
    for x in range(2, 14):
        put(img, x, 9, accent)
        put(img, x, 10, shade(accent, 0.3))


def side_frost(img, body, accent, extra):
    """Un filet d'accent, des ailettes d'échangeur et du givre accroché aux bords."""
    seam(img, 3, body)
    for x in range(3, 13):
        put(img, x, 6, accent)
    for y in range(8, 14):
        for x in range(3, 13):
            put(img, x, y, light(body, 0.25) if y % 2 == 0 else shade(body, 0.22))
    rnd = random.Random(7)
    for _ in range(14):
        x, y = rnd.randrange(2, 14), rnd.choice((2, 13, rnd.randrange(2, 14)))
        put(img, x, y, "#F4FBFF")


def side_hoops(img, body, accent, extra):
    """Les cerclages d'une cuve, et au milieu sa plaque d'identification à la couleur de la machine."""
    for row in (3, 12):
        for x in range(2, 14):
            put(img, x, row, shade(body, 0.32))
            put(img, x, row + 1, light(body, 0.2))
    for y in range(6, 10):
        for x in range(4, 12):
            put(img, x, y, accent)
    for x in range(5, 11):
        put(img, x, 8, shade(accent, 0.45))
    bolts(img, ((4, 6), (11, 6), (4, 9), (11, 9)), body)


def side_panels(img, body, accent, extra):
    """Trois hauts panneaux séparés par des fentes lumineuses : la chambre semble vibrer derrière."""
    for y in range(2, 14):
        for x in (5, 10):
            put(img, x, y, accent if 4 <= y <= 11 else shade(body, 0.4))
        for x in (4, 9):
            put(img, x, y, shade(body, 0.35))
    bolts(img, ((2, 3), (7, 3), (12, 3), (2, 12), (7, 12), (12, 12)), body)


SIDES = {"vents": side_vents, "fins": side_fins, "hazard": side_hazard, "grid": side_grid, "pipes": side_pipes,
         "ribs": side_ribs, "frost": side_frost, "hoops": side_hoops, "panels": side_panels}


def side_texture(spec):
    img = shell(11, spec["body"])
    SIDES[spec["side"]](img, spec["body"], spec["accent"], spec.get("extra"))
    if spec["side"] in ("vents", "pipes", "frost"):
        bolts(img, ((3, 2), (12, 2), (3, 13), (12, 13)) if spec["side"] != "vents" else ((3, 3), (12, 3), (3, 13), (12, 13)))
    return img


# ---------------------------------------------------------------------------
# Le dessus : un motif par machine
# ---------------------------------------------------------------------------
def top_vent(img, body, accent):
    for k in range(4, 12):
        put(img, k, 4, TRIM)
        put(img, k, 11, TRIM)
        put(img, 4, k, TRIM)
        put(img, 11, k, TRIM)
    slats(img, 5, 10, 5, 10)


def top_fan(img, body, accent):
    """Un ventilateur dans sa buse : quatre pales autour d'un moyeu."""
    disc(img, 7.5, 7.5, 5.2, DARK, TRIM)
    for k in range(-4, 5):
        for x, y in ((7.5 + k, 7.5 + k * 0.35), (7.5 - k * 0.35, 7.5 + k)):
            put(img, int(round(x)), int(round(y)), "#6B747F")
    disc(img, 7.5, 7.5, 1.3, accent)


def top_chimney(img, body, accent):
    """Une cheminée : un conduit sombre, cerclé de sa couleur, et la suie autour."""
    disc(img, 7.5, 7.5, 4.6, shade(body, 0.3), TRIM)
    disc(img, 7.5, 7.5, 3.2, "#121417", accent)
    for x, y in ((4, 4), (11, 4), (4, 11), (11, 11), (7, 3), (3, 8)):
        put(img, x, y, shade(body, 0.2))


def top_hatch(img, body, accent):
    """Une trappe de visite vissée, et sa poignée."""
    for y in range(3, 13):
        for x in range(3, 13):
            edge = x in (3, 12) or y in (3, 12)
            put(img, x, y, TRIM if edge else light(body, 0.08))
    for x in range(6, 10):
        put(img, x, 7, accent)
        put(img, x, 8, shade(accent, 0.35))
    bolts(img, ((4, 4), (11, 4), (4, 11), (11, 11)))


def top_cap(img, body, accent):
    """Une tête ronde vissée, comme le chapeau d'une cuve sous pression."""
    disc(img, 7.5, 7.5, 5.2, light(body, 0.15), TRIM)
    for angle in range(0, 360, 60):
        put(img, int(round(7.5 + 4.1 * math.cos(math.radians(angle)))), int(round(7.5 + 4.1 * math.sin(math.radians(angle)))), BOLT)
    disc(img, 7.5, 7.5, 1.6, accent)


def top_twin(img, body, accent):
    """Deux bouchons de sortie, un par gaz."""
    for cx in (4.5, 10.5):
        disc(img, cx, 7.5, 2.8, light(body, 0.12), TRIM)
        disc(img, cx, 7.5, 1.0, accent)


def top_valve(img, body, accent):
    """Une vanne à volant sur sa tête ronde."""
    disc(img, 7.5, 7.5, 5.0, shade(body, 0.12), TRIM)
    for k in range(3, 13):
        put(img, k, 7, accent)
        put(img, 7, k, accent)
    disc(img, 7.5, 7.5, 1.2, BOLT)


def top_hopper(img, body, accent):
    """Une trémie : des carrés emboîtés de plus en plus sombres, où l'on verse le régolithe."""
    for ring, color in enumerate((TRIM, shade(body, 0.2), shade(body, 0.4), shade(body, 0.6), "#121417")):
        for k in range(3 + ring, 13 - ring):
            for x, y in ((k, 3 + ring), (k, 12 - ring), (3 + ring, k), (12 - ring, k)):
                put(img, x, y, color)
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        put(img, x, y, accent)


def top_core(img, body, accent):
    """Un anneau lumineux autour d'un cœur sombre."""
    disc(img, 7.5, 7.5, 5.0, TRIM)
    disc(img, 7.5, 7.5, 4.0, accent)
    disc(img, 7.5, 7.5, 2.6, "#121417")


TOPS = {"vent": top_vent, "fan": top_fan, "chimney": top_chimney, "hatch": top_hatch, "cap": top_cap, "twin": top_twin,
        "valve": top_valve, "hopper": top_hopper, "core": top_core}


def top_texture(spec):
    body, accent = spec["body"], spec["accent"]
    img = shell(12, body)
    for cx, cy, dx, dy in ((2, 2, 1, 1), (13, 2, -1, 1), (2, 13, 1, -1), (13, 13, -1, -1)):
        for k in range(3):
            put(img, cx + dx * k, cy, accent)
            put(img, cx, cy + dy * k, accent)
    TOPS[spec["top"]](img, body, accent)
    return img


def back_texture():
    """Dos : une coque neutre, et au centre le port d'énergie où se branchent les câbles, ses carrés dorés et cuivrés."""
    img = shell(13, "#C6CCD3")
    colors = ["#2B3036", "#8A6A12", "#D9A93A", "#15171A", "#C46A35", "#E8A56A"]
    for y in range(4, 12):
        for x in range(4, 12):
            ring = min(x - 4, y - 4, 11 - x, 11 - y)
            put(img, x, y, colors[min(ring, len(colors) - 1)])
    return img


def base_texture():
    """Dessous : un socle graphite, ses quatre patins."""
    img = canvas("#353A41")
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        for dx in (0, 1):
            for dy in (0, 1):
                put(img, x - dx if x > 8 else x + dx, y - dy if y > 8 else y + dy, "#23272C")
    return img


# ---------------------------------------------------------------------------
# La grille de façade : un motif par machine, qui s'anime en marche
# ---------------------------------------------------------------------------
def grille(img, kind, accent, step, on, frames):
    for y in range(GY0, GY1 + 1):
        for x in range(SX0, SX1 + 1):
            wave = 0.55 + 0.35 * math.sin(step * 2 * math.pi / frames + x * 0.55)
            glow = mix("#2B3036", accent, wave) if on else rgb("#2B3036")
            if kind == "slats":
                put(img, x, y, DARK if (y - GY0) % 2 == 0 else glow)
            elif kind == "coil":
                # Une résistance en zigzag, qui rougeoie en marche.
                path = GY0 + (0, 1, 2, 3, 2, 1)[(x - SX0) % 6]
                put(img, x, y, glow if y == path else DARK)
            elif kind == "mesh":
                put(img, x, y, glow if (x + y) % 2 == 0 else DARK)
            elif kind == "bars":
                put(img, x, y, glow if (x - SX0) % 3 != 2 else DARK)
            elif kind == "dots":
                # Une rangée de voyants qui s'allument l'un après l'autre.
                lamp = y == GY0 + 1 and (x - SX0) % 2 == 0
                lit = on and ((x - SX0) // 2 - step) % 3 == 0
                put(img, x, y, (accent if lit else "#3A4048") if lamp else DARK)


def front_texture(spec, step, on, frames=8):
    """
    Façade : la coque ; en haut, l'écran de la machine dans sa lunette graphite, son pictogramme et la jauge qui monte
    pendant qu'elle travaille ; en bas, la grille, qui s'anime de la couleur de la machine en marche.
    """
    accent, icon = spec["accent"], spec["icon"]
    img = shell(14, spec["body"])
    for x in range(SX0 - 1, SX1 + 2):
        put(img, x, SY0 - 1, TRIM)
        put(img, x, SY1 + 1, TRIM)
    for y in range(SY0 - 1, SY1 + 2):
        put(img, SX0 - 1, y, TRIM)
        put(img, SX1 + 1, y, TRIM)
    for y in range(SY0, SY1 + 1):
        for x in range(SX0, SX1 + 1):
            put(img, x, y, SCREEN)
    # Le pictogramme, net et lumineux en marche, éteint sinon ; son ombre portée d'un pixel en bas.
    for r, row in enumerate(ICONS[icon]):
        for c, ch in enumerate(row):
            if ch == "#":
                put(img, 4 + c, 4 + r, accent if on else "#3A4048")
                if on and (r + 1 >= len(ICONS[icon]) or ICONS[icon][r + 1][c] != "#"):
                    put(img, 4 + c, 4 + r, mix(accent, "#000000", 0.25))
    # La jauge : cinq crans, qui se remplissent au fil de l'animation.
    level = 1 + step * 5 // frames if on else 0
    for k in range(5):
        for x in (10, 11):
            put(img, x, 8 - k, accent if k < level else "#1A2026")
    # Un témoin, en haut à droite de l'écran : vert en marche, rouge à l'arrêt.
    put(img, 12, 3, ("#5BE08A" if step % 2 == 0 else "#2F7A4A") if on else "#8A2A2A")
    grille(img, spec["grille"], accent, step, on, frames)
    return img


def spec(body, accent, icon, frames, side, top, grille_kind, extra=None):
    return {"body": body, "accent": accent, "icon": icon, "frames": frames, "side": side, "top": top,
            "grille": grille_kind, "extra": extra}


# Chaque machine : sa coque, son accent, son pictogramme, le nombre d'images de son animation, puis le motif de ses
# flancs, de son dessus et de sa grille.
MACHINES = {
    "combustion_generator": spec("#5A5F66", "#F08A24", "flame", 8, "fins", "fan", "coil"),
    "arc_foundry": spec("#C4A25C", "#8FD8FF", "bolt", 8, "hazard", "vent", "bars"),
    "rolling_mill": spec("#6F8295", "#A9CDEF", "rollers", 4, "grid", "hatch", "bars"),
    "electrolyzer": spec("#E3E7EB", "#3F8FE0", "drop", 8, "pipes", "twin", "slats", extra="#E05A4F"),
    "cryogenic_liquefier": spec("#BCD9E3", "#4FC6E0", "snow", 8, "frost", "cap", "slats"),
    "refinery": spec("#7A5C46", "#F0A030", "tower", 8, "ribs", "chimney", "coil"),
    "sabatier_reactor": spec("#5F8A6C", "#A6E36F", "flask", 8, "pipes", "valve", "mesh", extra="#D9DEE3"),
    "helium3_extractor": spec("#B3AC9A", "#E8C24A", "moon", 5, "vents", "hopper", "dots"),
    "casimir_chamber": spec("#4B4462", "#B07CFF", "plates", 8, "panels", "core", "dots"),
    "fusion_reactor": spec("#2E3036", "#F05AB4", "atom", 8, "hoops", "core", "mesh"),
    "cryogenic_tank": spec("#DCE8EF", "#9AD4F5", "tank", 8, "hoops", "cap", "slats"),
    "drill_derrick": spec("#B4652B", "#FFB347", "drill", 8, "hazard", "hatch", "bars"),
}
# Blocs du même style qui n'ont pas d'état allumé : leur fenêtre est toujours animée et lumineuse.
ALWAYS_ON = {
    # L'ordinateur de vol : l'avionique d'une fusée, posée dans la fusée comme ses autres pièces.
    "flight_computer": spec("#34415C", "#6FA0FF", "rocket", 8, "grid", "vent", "dots"),
}


# ---------------------------------------------------------------------------
# Le modèle, commun à toutes
# ---------------------------------------------------------------------------
def parts():
    """
    Un cube plein ; en façade, l'écran et la grille en retrait d'un demi-pixel (l'écran et la grille luisent en marche),
    et entre eux et autour d'eux la coque.
    """
    return [
        {**box(0, 0, 1, 16, 16, 16, "side"), "faces": {"north": "front", "south": "back", "up": "top", "down": "base"}},
        {**box(0, 14, 0, 16, 16, 1, "front"), "faces": {"up": "top", "east": "side", "west": "side"}},
        {**box(0, 5, 0, 16, 6, 1, "front"), "faces": {"east": "side", "west": "side"}},
        {**box(0, 0, 0, 16, 1, 1, "front"), "faces": {"down": "base", "east": "side", "west": "side"}},
        {**box(0, 6, 0, 2, 14, 1, "front"), "faces": {"west": "side"}},
        {**box(14, 6, 0, 16, 14, 1, "front"), "faces": {"east": "side"}},
        {**box(0, 1, 0, 2, 5, 1, "front"), "faces": {"west": "side"}},
        {**box(14, 1, 0, 16, 5, 1, "front"), "faces": {"east": "side"}},
        {**box(2, 6, 0.5, 14, 14, 1, "front"), "window": True},
        {**box(2, 1, 0.5, 14, 5, 1, "front"), "window": True},
    ]


def elements(part_list, lit):
    out = []
    for part in separate(part_list):
        own = part.get("faces", {})
        element = {"from": part["from"], "to": part["to"],
                   "faces": {face: {"texture": "#" + own.get(face, part["texture"])} for face in gm.FACES}}
        if lit and part.get("window"):
            element["light_emission"] = 12
        out.append(element)
    return out


def model(name, lit):
    textures = {
        "front": "orbitalis:block/%s_front%s" % (name, "_on" if lit else ""),
        "side": "orbitalis:block/%s_side" % name, "top": "orbitalis:block/%s_top" % name,
        "back": "orbitalis:block/machine_back", "base": "orbitalis:block/machine_base",
        "particle": "orbitalis:block/%s_side" % name,
    }
    return {"parent": "minecraft:block/block", "textures": textures, "elements": elements(parts(), lit)}


# ---------------------------------------------------------------------------
# La pompe à eau : une vraie pompe
# ---------------------------------------------------------------------------
PUMP_PAINT = "#2F6FA8"
PUMP_STEEL = "#8E979F"


def pump_paint_texture():
    """Le corps de la pompe : une fonte peinte en bleu, ses joints et ses vis."""
    img = shell(21, PUMP_PAINT)
    seam(img, 7, PUMP_PAINT)
    bolts(img, ((3, 3), (12, 3), (3, 12), (12, 12)))
    return img


def pump_motor_texture():
    """Le moteur : un carter gris à ailettes de refroidissement, et la plaque de ses caractéristiques."""
    img = canvas(PUMP_STEEL)
    for y in range(SIZE):
        for x in range(SIZE):
            put(img, x, y, shade(PUMP_STEEL, 0.38) if y % 2 == 0 else light(PUMP_STEEL, 0.12))
    for y in range(6, 10):
        for x in range(6, 10):
            put(img, x, y, "#D8DCE0" if (x, y) != (7, 8) else "#5A626C")
    return img


def pump_base_texture():
    """Le socle : une tôle striée bordée d'une bande de danger, et les quatre boulons d'ancrage."""
    img = canvas("#4A5058")
    for y in range(SIZE):
        for x in range(SIZE):
            if (x + y) % 4 == 0:
                put(img, x, y, "#5A616A")
            if x in (0, 15) or y in (0, 15):
                put(img, x, y, "#F2C14E" if ((x + y) // 2) % 2 == 0 else DARK)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        put(img, x, y, BOLT)
    return img


def pump_intake_texture():
    """Dessous : la crépine d'aspiration, qui plonge dans l'eau, au milieu du socle."""
    img = pump_base_texture()
    disc(img, 7.5, 7.5, 4.6, TRIM)
    for y in range(4, 12):
        for x in range(4, 12):
            if math.hypot(x - 7.5, y - 7.5) < 3.6:
                put(img, x, y, "#121417" if (x + y) % 2 == 0 else "#3A4048")
    return img


def pump_flange_texture():
    """La bride de sortie : un collier d'acier, ses boulons, et l'orifice où se branche le tuyau."""
    img = canvas(PUMP_STEEL)
    for y in range(SIZE):
        for x in range(SIZE):
            d = max(abs(x - 7.5), abs(y - 7.5))
            if d < 2.1:
                put(img, x, y, "#121417")
            elif d < 3.1:
                put(img, x, y, shade(PUMP_STEEL, 0.4))
            elif d < 4.1:
                put(img, x, y, light(PUMP_STEEL, 0.2))
    for x, y in ((4, 4), (11, 4), (4, 11), (11, 11)):
        put(img, x, y, BOLT)
    return img


def pump_terminal_texture(on):
    """Le boîtier électrique : le port d'énergie doré et cuivré, et le voyant, vert quand la pompe tourne."""
    img = shell(23, "#C6CCD3")
    colors = ["#2B3036", "#8A6A12", "#D9A93A", "#15171A", "#C46A35", "#E8A56A"]
    for y in range(5, 13):
        for x in range(4, 12):
            ring = min(x - 4, y - 5, 11 - x, 12 - y)
            put(img, x, y, colors[min(ring, len(colors) - 1)])
    for x in (12, 13):
        for y in (3, 4):
            put(img, x, y, ("#5BE08A" if on else "#2F7A4A") if (x, y) != (13, 4) else ("#2F7A4A" if on else "#1E4A30"))
    return img


def pump_parts():
    """
    Vue de la pompe tournée vers le nord (sa sortie) : le socle sur toute la surface ; au-dessus, vers l'avant, le corps
    de pompe, sa bride de sortie au ras de la face avant ; vers l'arrière, le moteur à ailettes ; contre la face arrière,
    le boîtier électrique et son port d'énergie ; sur le corps, un manomètre.
    """
    return [
        {**box(0, 0, 0, 16, 2, 16, "base"), "faces": {"down": "intake"}},
        {**box(3, 2, 2, 13, 12, 9, "paint")},
        {**box(4, 12, 3, 12, 13, 8, "paint")},
        {**box(5, 4, 1, 11, 10, 2, "pipe")},
        {**box(4, 3, 0, 12, 11, 1, "pipe"), "faces": {"north": "flange"}},
        {**box(4, 3, 9, 12, 11, 13, "motor")},
        {**box(5, 4, 13, 11, 10, 14, "pipe")},
        {**box(3, 2, 14, 13, 12, 16, "paint"), "faces": {"south": "terminal"}},
        {**box(7, 13, 5, 9, 14, 7, "pipe")},
    ]


def pump_model(lit):
    textures = {
        "base": "orbitalis:block/water_pump_base", "intake": "orbitalis:block/water_pump_intake",
        "paint": "orbitalis:block/water_pump_paint", "motor": "orbitalis:block/water_pump_motor",
        "pipe": "orbitalis:block/water_pump_pipe", "flange": "orbitalis:block/water_pump_flange",
        "terminal": "orbitalis:block/water_pump_terminal%s" % ("_on" if lit else ""),
        "particle": "orbitalis:block/water_pump_paint",
    }
    return {"parent": "minecraft:block/block", "textures": textures, "elements": elements(pump_parts(), lit)}


# ---------------------------------------------------------------------------
# La tour de la foreuse : son socle et ses mâts
# ---------------------------------------------------------------------------
DRILL_YELLOW = "#E2B132"


def hazard_row(img, y0, y1):
    for y in range(y0, y1 + 1):
        for x in range(SIZE):
            put(img, x, y, "#F2C14E" if ((x + y) // 2) % 2 == 0 else DARK)


# ---------------------------------------------------------------------------
# La tour de la foreuse
# ---------------------------------------------------------------------------
# Posés seuls, le socle et le mât ont leur aspect « à bâtir ». La tour complète prend son aspect assemblé, dessiné
# d'un seul tenant sur toute sa largeur (trois blocs) puis découpé bloc par bloc : jambes en treillis jaune aux angles,
# contreventement en triangle d'une jambe à l'autre, couronne à bande de danger et feux d'obstacle en haut, plateau
# tournant de la flèche au sommet, plinthe au pied.
TOWER_STEEL = "#343A41"
TOWER_DEEP = "#262B30"
TOWER_PALE = "#4B535C"
LAMP = "#E8473F"


def sheet(w, h, color, seed=0, tones=0.05):
    """Une tôle de w×h pixels, à peine nuancée."""
    rnd = random.Random(seed)
    choices = (rgb(color), rgb(color), light(color, tones), shade(color, tones))
    return [[rnd.choice(choices) for _ in range(w)] for _ in range(h)]


def dot(img, x, y, color):
    if 0 <= y < len(img) and 0 <= x < len(img[0]):
        img[y][x] = rgb(color) if isinstance(color, str) else color


def piece(img, x0, y0):
    """Un carré de 16 pixels découpé dans un dessin d'ensemble."""
    return [row[x0:x0 + SIZE] for row in img[y0:y0 + SIZE]]


GRAPHITE = "#2A3037"
PIPE = "#AEB8C2"
PANEL = "#3D4752"
ALLOY = "#9AA4AE"
SEAM = "#161A1F"
CYAN = "#3FD8F2"


def both(img, x, y, color):
    """Un pixel et son reflet de l'autre côté de la tour."""
    dot(img, x, y, color)
    dot(img, len(img[0]) - 1 - x, y, color)


def plate(img, x0, y0, x1, y1, base=PANEL):
    """Une plaque d'habillage : éclairée en haut à gauche, ombrée en bas à droite, coins chanfreinés, rivetée."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            color = base
            if y == y0 or x == x0:
                color = light(base, 0.18)
            if y == y1 or x == x1:
                color = shade(base, 0.35)
            dot(img, x, y, color)
    for x, y in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
        dot(img, x, y, GRAPHITE)
    for x, y in ((x0 + 1, y0 + 1), (x1 - 1, y0 + 1), (x0 + 1, y1 - 1), (x1 - 1, y1 - 1)):
        dot(img, x, y, light(base, 0.4))


def pillars(img, y0, y1, lamp=False):
    """Les piliers d'angle en alliage clair, chacun parcouru d'une bande lumineuse cyan, boulonnés ; un feu rouge en
    tête pour la couronne."""
    tones = (SEAM, light(ALLOY, 0.35), ALLOY, ALLOY, shade(ALLOY, 0.3), None, SEAM)
    for y in range(y0, y1 + 1):
        for k, color in enumerate(tones):
            if color is None:
                color = light(CYAN, 0.6) if y % 4 == 1 else CYAN
            both(img, k, y, color)
        both(img, 3, y, shade(ALLOY, 0.12) if y % 8 == 7 else ALLOY)
    for y in (y0 + 2, y1 - 2):
        both(img, 3, y, light(ALLOY, 0.55))
        both(img, 3, y + 1, shade(ALLOY, 0.45))
    if lamp:
        for x in (2, 3, 4):
            both(img, x, y0 + 1, LAMP)
            both(img, x, y0 + 2, shade(LAMP, 0.3))
        both(img, 3, y0 + 1, light(LAMP, 0.55))


def cladding(img, y0, y1):
    """L'habillage entre les piliers : deux plaques à évents et à onglet de couleur, et au milieu le module de
    ventilation et ses voyants."""
    for x0, x1 in ((8, 19), (28, 39)):
        plate(img, x0, y0, x1, y1)
        mid = (y0 + y1) // 2
        for x in range(x0 + 3, x1 - 2):
            if x % 2 == 0:
                dot(img, x, mid - 2, SEAM)
                dot(img, x, mid - 1, shade(PANEL, 0.2))
        for k in range(4):
            tab = x0 + 3 + k if x0 < 24 else x1 - 3 - k
            dot(img, tab, y1 - 2, DRILL_YELLOW if k % 2 == 0 else DARK)
    pipe_rack(img, 0, 15)


def pipe_rack(img, y0, y1):
    """Le râtelier : deux tiges de forage d'acier poli, rangées debout derrière une fente, avec leurs raccords ; d'un
    étage à l'autre, elles forment des colonnes continues sur toute la hauteur de la tour."""
    for y in range(y0, y1 + 1):
        for x in range(20, 28):
            dot(img, x, y, SEAM if x in (20, 27) else "#12161A")
        for x0 in (21, 25):
            joint = y % 16 in (5, 6)
            dot(img, x0, y, light(PIPE, 0.45) if not joint else PIPE)
            dot(img, x0 + 1, y, shade(PIPE, 0.25) if not joint else shade(PIPE, 0.5))
            if joint:
                dot(img, x0 - 1 if x0 == 21 else x0 + 2, y, shade(PIPE, 0.35))


def tower_side(crown):
    """Un étage vu de côté, sur toute la largeur de la tour (48 pixels) ; en couronne pour l'étage du haut."""
    img = sheet(48, SIZE, GRAPHITE, 71, 0.06)
    for x in range(48):
        dot(img, x, 15, SEAM)
        dot(img, x, 0, light(GRAPHITE, 0.12))
    if crown:
        for x in range(48):
            dot(img, x, 0, light(ALLOY, 0.35))
            dot(img, x, 1, ALLOY)
            dot(img, x, 2, SEAM)
            dot(img, x, 3, (light(CYAN, 0.6) if x % 6 < 3 else CYAN) if 8 <= x <= 39 else SEAM)
            dot(img, x, 4, SEAM)
            dot(img, x, 5, shade(ALLOY, 0.3))
        cladding(img, 7, 13)
        pipe_rack(img, 6, 15)
        for x in range(20, 28):
            dot(img, x, 6, shade(ALLOY, 0.3))
        pillars(img, 6, 15)
        pillars(img, 0, 5, lamp=True)
        for y in (0, 1):
            both(img, 6, y, ALLOY)
    else:
        cladding(img, 2, 13)
        pillars(img, 0, 15)
    return img


def tower_plinth():
    """Le pied de la tour, vu de côté sur toute sa largeur : lèvre d'alliage, bande de danger, caisson sombre à grandes
    grilles, voyant d'état, et les pieds des piliers."""
    img = sheet(48, SIZE, "#232830", 72, 0.05)
    for x in range(48):
        dot(img, x, 0, light(ALLOY, 0.35))
        dot(img, x, 1, shade(ALLOY, 0.3))
        for y in (2, 3, 4):
            dot(img, x, y, "#F2C14E" if ((x + y) // 2) % 2 == 0 else DARK)
        dot(img, x, 5, SEAM)
        dot(img, x, 15, SEAM)
    for x0, x1 in ((9, 20), (27, 38)):
        plate(img, x0, 7, x1, 13, "#323A44")
        for y in range(9, 12):
            for x in range(x0 + 2, x1 - 1):
                dot(img, x, y, SEAM if y % 2 == 1 else "#4A545F")
    for x in range(21, 27):
        for y in range(7, 14):
            dot(img, x, y, SEAM if x in (21, 26) or y in (7, 13) else "#1B2026")
    for y in (9, 10):
        dot(img, 23, y, "#5BE37D")
        dot(img, 24, y, light("#5BE37D", 0.5) if y == 9 else "#5BE37D")
    pillars(img, 6, 14)
    for x in range(8):
        both(img, x, 13, shade(ALLOY, 0.35))
        both(img, x, 14, shade(ALLOY, 0.55))
    return img


def tower_cap():
    """Le sommet, vu d'en haut sur toute la tour (48×48), symétrique dans les quatre sens : platelage graphite, cadre
    d'alliage souligné d'un filet lumineux, chapeaux des piliers, et au milieu le plateau tournant de la flèche."""
    n = 48
    img = sheet(n, n, GRAPHITE, 73, 0.05)
    for y in range(n):
        for x in range(n):
            if x % 3 == 0 and y % 3 == 0:
                dot(img, x, y, light(GRAPHITE, 0.2))
    c = (n - 1) / 2.0
    for k in range(n):
        for at, tone in ((22, shade(ALLOY, 0.35)), (23, ALLOY), (24, ALLOY), (25, shade(ALLOY, 0.35))):
            dot(img, k, at, tone)
            dot(img, at, k, tone)
    for k in range(n):
        for d, tone in ((0, SEAM), (1, light(ALLOY, 0.35)), (2, ALLOY), (3, shade(ALLOY, 0.3)), (4, SEAM),
                        (5, light(CYAN, 0.5) if k % 6 < 3 else CYAN), (6, SEAM)):
            if 5 <= d and not (7 <= k <= n - 8):
                continue
            dot(img, k, d, tone)
            dot(img, k, n - 1 - d, tone)
            dot(img, d, k, tone)
            dot(img, n - 1 - d, k, tone)
    for cx, cy in ((0, 0), (n - 8, 0), (0, n - 8), (n - 8, n - 8)):
        for y in range(8):
            for x in range(8):
                rim = x in (0, 7) or y in (0, 7)
                dot(img, cx + x, cy + y, SEAM if rim else (light(ALLOY, 0.2) if x + y < 7 else ALLOY))
        for x, y in ((3, 3), (4, 3), (3, 4), (4, 4)):
            dot(img, cx + x, cy + y, light(CYAN, 0.5) if (x, y) == (3, 3) else CYAN)
        for x, y in ((2, 3), (5, 4), (3, 5), (4, 2)):
            dot(img, cx + x, cy + y, SEAM)
    for y in range(n):
        for x in range(n):
            r = math.hypot(x - c, y - c)
            if r <= 2.6:
                dot(img, x, y, light(ALLOY, 0.4) if r < 1.2 else SEAM)
            elif r <= 6.2:
                dot(img, x, y, ALLOY if (x + y) % 2 else shade(ALLOY, 0.15))
            elif r <= 7.4:
                dot(img, x, y, light(CYAN, 0.55) if r <= 6.8 else CYAN)
            elif r <= 9.0:
                dot(img, x, y, "#3A434D")
            elif r <= 10.0:
                dot(img, x, y, SEAM)
    for a in range(8):
        ang = a * math.pi / 4 + math.pi / 8
        dot(img, int(round(c + 4.4 * math.cos(ang))), int(round(c + 4.4 * math.sin(ang))), SEAM)
    return img


def top_drive_texture():
    """La tête d'entraînement, par régions : flanc du moteur (0-7, 0-7), son dessus et son ventilateur (0-7, 8-15), la
    tige (8-11, 0-5), le bouchon pivotant (12-15, 0-4), la platine boulonnée (8-15, 8-15)."""
    img = sheet(SIZE, SIZE, GRAPHITE, 79, 0.0)
    for x in range(8):
        dot(img, x, 0, light(ALLOY, 0.35))
        dot(img, x, 1, ALLOY)
        dot(img, x, 2, light(CYAN, 0.5) if x % 3 == 1 else CYAN)
        for y in (3, 4, 5):
            dot(img, x, y, "#3A434D" if x % 2 == 0 else SEAM)
        dot(img, x, 6, "#F2C14E" if x % 2 == 0 else DARK)
        dot(img, x, 7, SEAM)
    dot(img, 6, 4, "#5BE37D")
    for y in range(8, 16):
        for x in range(8):
            r = math.hypot(x - 3.5, y - 11.5)
            if r > 3.6:
                dot(img, x, y, ALLOY if (x + y) % 2 else light(ALLOY, 0.2))
            elif r > 1.5:
                dot(img, x, y, SEAM if (x + y) % 2 else "#3A434D")
            else:
                dot(img, x, y, shade(PIPE, 0.4))
    for y in range(6):
        joint = y in (0, 5)
        for x, tone in ((8, light(PIPE, 0.45)), (9, PIPE), (10, shade(PIPE, 0.15)), (11, shade(PIPE, 0.35))):
            dot(img, x, y, shade(tone, 0.4) if joint else tone)
    for y in range(4):
        for x in range(12, 16):
            dot(img, x, y, shade(PIPE, 0.55) if x in (12, 15) or y in (0, 3) else shade(PIPE, 0.3))
    for x in range(12, 16):
        dot(img, x, 4, shade(PIPE, 0.45))
    for y in range(8, 16):
        for x in range(8, 16):
            rim = x in (8, 15) or y in (8, 15)
            dot(img, x, y, ("#F2C14E" if (x + y) % 2 == 0 else DARK) if rim else ALLOY)
    for x, y in ((9, 9), (14, 9), (9, 14), (14, 14)):
        dot(img, x, y, light(ALLOY, 0.5))
    return img


def top_drive():
    """Les pièces de la tête d'entraînement, posées sur le plateau tournant du cœur de la couronne : la platine, le
    moteur, la tige qui en sort et son bouchon pivotant. Aucune face ne se dessine contre une autre pièce."""
    def element(x0, y0, z0, x1, y1, z1, faces):
        return {"from": [x0, y0, z0], "to": [x1, y1, z1],
                "faces": {face: {"texture": "#drive", "uv": uv} for face, uv in faces.items()}}

    def sides(uv):
        return {face: uv for face in ("north", "south", "east", "west")}

    return [
        element(3, 16, 3, 13, 17, 13, {**sides([8, 8, 16, 9]), "up": [8, 8, 16, 16]}),
        element(4, 17, 4, 12, 25, 12, {**sides([0, 0, 8, 8]), "up": [0, 8, 8, 16]}),
        element(6, 25, 6, 10, 31, 10, sides([8, 0, 12, 6])),
        element(5, 31, 5, 11, 32, 11, {**sides([12, 4, 16, 5]), "up": [12, 0, 16, 4]}),
    ]


def preview_texture():
    """La couleur de l'aperçu de la zone : un blanc plein, que la foreuse teinte de ses deux cyans."""
    return [[(255, 255, 255, 255) for _ in range(SIZE)] for _ in range(SIZE)]


def drill_base_top():
    """Dessus du socle à bâtir : une tôle striée d'acier sombre, bordée d'une bande de danger, et ses boulons."""
    img = canvas("#4A5058")
    for y in range(SIZE):
        for x in range(SIZE):
            if (x + y) % 4 == 0 or (x - y) % 4 == 0:
                put(img, x, y, "#59616A")
            if x in (0, 15) or y in (0, 15):
                put(img, x, y, "#F2C14E" if ((x + y) // 2) % 2 == 0 else DARK)
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        put(img, x, y, BOLT)
        put(img, x + 1, y + 1, shade("#4A5058", 0.35))
    return img


def drill_base_side():
    """Flanc du socle à bâtir : le pied de la tour en réduction, une jambe à chaque bord."""
    return [row[0:6] + row[21:25] + row[42:48] for row in tower_plinth()]


def drill_mast_side():
    """Flanc d'un mât à bâtir : un treillis d'acier, deux montants jaunes, une traverse à chaque bout et une croix de
    contreventement ; empilés, les mâts forment une tour en treillis."""
    img = sheet(SIZE, SIZE, "#30353B", 74)
    for y in range(SIZE):
        for x in range(3, 13):
            if abs(x - y) == 0 or abs(x - (15 - y)) == 0:
                put(img, x, y, DRILL_YELLOW)
            elif abs(x - y) == 1 or abs(x - (15 - y)) == 1:
                put(img, x, y, shade(DRILL_YELLOW, 0.35) if x > 7 else light(DRILL_YELLOW, 0.1))
    for y in range(SIZE):
        for x, tone in ((0, shade(DRILL_YELLOW, 0.5)), (1, light(DRILL_YELLOW, 0.3)), (2, DRILL_YELLOW), (13, DRILL_YELLOW),
                        (14, shade(DRILL_YELLOW, 0.25)), (15, shade(DRILL_YELLOW, 0.5))):
            put(img, x, y, tone)
    for x in range(SIZE):
        put(img, x, 0, light(DRILL_YELLOW, 0.2))
        put(img, x, 1, shade(DRILL_YELLOW, 0.3) if 2 < x < 13 else DRILL_YELLOW)
        put(img, x, 14, DRILL_YELLOW)
        put(img, x, 15, shade(DRILL_YELLOW, 0.45))
    for x, y in ((2, 3), (13, 3), (2, 12), (13, 12)):
        put(img, x, y, light(DRILL_YELLOW, 0.55))
    return img


def drill_mast_top():
    """Bout d'un mât à bâtir : le cadre jaune du treillis, ses goussets d'angle et sa croix, sur un fond sombre."""
    img = sheet(SIZE, SIZE, "#2A2E33", 75)
    for y in range(SIZE):
        for x in range(SIZE):
            if x in (0, 15) or y in (0, 15):
                put(img, x, y, shade(DRILL_YELLOW, 0.45))
            elif x in (1, 14) or y in (1, 14):
                put(img, x, y, DRILL_YELLOW)
            elif x == y or x == 15 - y:
                put(img, x, y, shade(DRILL_YELLOW, 0.2))
    for cx, cy in ((2, 2), (11, 2), (2, 11), (11, 11)):
        for k in range(3):
            put(img, cx + k, cy + (0 if cy == 2 else 2), light(DRILL_YELLOW, 0.2))
    return img


def tower_cube(textures, mirror_east=False):
    """Un bloc plein de la tour : une texture par face, la face est en miroir pour un angle (sa jambe à droite)."""
    faces = {}
    for face in ("north", "south", "east", "west", "up", "down"):
        entry = {"texture": "#" + face, "cullface": face, "uv": [0, 0, 16, 16]}
        if face == "east" and mirror_east:
            entry["uv"] = [16, 0, 0, 16]
        faces[face] = entry
    model = {"parent": "minecraft:block/block",
             "textures": dict({"particle": textures["north"]}, **textures),
             "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}]}
    return model


def write_tower_model(name, model):
    with open(os.path.join(gm.MODEL_DIR, name + ".json"), "w", newline="\n") as f:
        json.dump(model, f, indent=2)


def write_drill_tower():
    gm.write_texture("drill_base_top", [drill_base_top()])
    gm.write_texture("drill_base_side", [drill_base_side()])
    gm.write_texture("drill_mast_side", [drill_mast_side()])
    gm.write_texture("drill_mast_top", [drill_mast_top()])
    side, crown, plinth, cap = tower_side(False), tower_side(True), tower_plinth(), tower_cap()
    gm.write_texture("drill_tower_corner", [piece(side, 0, 0)])
    gm.write_texture("drill_tower_edge", [piece(side, 16, 0)])
    gm.write_texture("drill_tower_crown_corner", [piece(crown, 0, 0)])
    gm.write_texture("drill_tower_crown_edge", [piece(crown, 16, 0)])
    gm.write_texture("drill_tower_plinth_corner", [piece(plinth, 0, 0)])
    gm.write_texture("drill_tower_plinth_edge", [piece(plinth, 16, 0)])
    # Vu d'en haut, le nord en haut : un angle tourné vers le nord a sa seconde face à l'est, en haut à droite.
    gm.write_texture("drill_tower_cap_corner", [piece(cap, 32, 0)])
    gm.write_texture("drill_tower_cap_edge", [piece(cap, 16, 0)])
    gm.write_texture("drill_tower_cap_center", [piece(cap, 16, 16)])
    gm.write_texture("drill_tower_inner", [sheet(SIZE, SIZE, TOWER_DEEP, 76)])
    gm.write_png(os.path.join(gm.BLOCK_DIR, "..", "entity", "drill_preview.png"), preview_texture())
    gm.write_texture("drill_top_drive", [top_drive_texture()])

    def tex(name):
        return "orbitalis:block/" + name

    inner = tex("drill_tower_inner")
    write_tower_model("drill_base", tower_cube({"north": tex("drill_base_side"), "south": tex("drill_base_side"),
                                                "east": tex("drill_base_side"), "west": tex("drill_base_side"),
                                                "up": tex("drill_base_top"), "down": tex("machine_base")}))
    write_tower_model("drill_mast", tower_cube({"north": tex("drill_mast_side"), "south": tex("drill_mast_side"),
                                                "east": tex("drill_mast_side"), "west": tex("drill_mast_side"),
                                                "up": tex("drill_mast_top"), "down": tex("drill_mast_top")}))
    for block, corner, edge in (("drill_base", "drill_tower_plinth_corner", "drill_tower_plinth_edge"),
                                ("drill_mast", "drill_tower_corner", "drill_tower_edge")):
        tops = [False, True] if block == "drill_mast" else [False]
        for top in tops:
            sfx = "_top" if top else ""
            c = tex("drill_tower_crown_corner") if top else tex(corner)
            e = tex("drill_tower_crown_edge") if top else tex(edge)
            up = (lambda name: tex(name)) if top else (lambda name: inner)
            write_tower_model(block + "_corner" + sfx, tower_cube(
                {"north": c, "east": c, "south": inner, "west": inner, "up": up("drill_tower_cap_corner"), "down": inner}, True))
            write_tower_model(block + "_edge" + sfx, tower_cube(
                {"north": e, "east": inner, "south": inner, "west": inner, "up": up("drill_tower_cap_edge"), "down": inner}))
            center = tower_cube(
                {"north": inner, "east": inner, "south": inner, "west": inner, "up": up("drill_tower_cap_center"), "down": inner})
            if top:
                # Au sommet, au cœur de la couronne : la tête d'entraînement de la foreuse.
                center["textures"]["drive"] = tex("drill_top_drive")
                center["elements"] += top_drive()
            write_tower_model(block + "_center" + sfx, center)


def write_pump():
    gm.write_texture("water_pump_paint", [pump_paint_texture()])
    gm.write_texture("water_pump_motor", [pump_motor_texture()])
    gm.write_texture("water_pump_base", [pump_base_texture()])
    gm.write_texture("water_pump_intake", [pump_intake_texture()])
    gm.write_texture("water_pump_pipe", [[[light(PUMP_STEEL, 0.1) if y % 4 else shade(PUMP_STEEL, 0.2) for x in range(SIZE)]
                                           for y in range(SIZE)]])
    gm.write_texture("water_pump_flange", [pump_flange_texture()])
    gm.write_texture("water_pump_terminal", [pump_terminal_texture(False)])
    gm.write_texture("water_pump_terminal_on", [pump_terminal_texture(True)])
    for lit in (False, True):
        with open(os.path.join(gm.MODEL_DIR, "water_pump" + ("_on" if lit else "") + ".json"), "w", newline="\n") as f:
            json.dump(pump_model(lit), f, indent=2)
    return check_overlaps("water_pump", separate(pump_parts()))


def main():
    gm.write_texture("machine_back", [back_texture()])
    gm.write_texture("machine_base", [base_texture()])
    for name, s in MACHINES.items():
        gm.write_texture(name + "_side", [side_texture(s)])
        gm.write_texture(name + "_top", [top_texture(s)])
        gm.write_texture(name + "_front", [front_texture(s, 0, False, s["frames"])])
        gm.write_texture(name + "_front_on", [front_texture(s, k, True, s["frames"]) for k in range(s["frames"])], 4, interpolate=False)
    for name, s in ALWAYS_ON.items():
        gm.write_texture(name + "_side", [side_texture(s)])
        gm.write_texture(name + "_top", [top_texture(s)])
        gm.write_texture(name + "_front_on", [front_texture(s, k, True, s["frames"]) for k in range(s["frames"])], 4, interpolate=False)
        with open(os.path.join(gm.MODEL_DIR, name + ".json"), "w", newline="\n") as f:
            json.dump(model(name, True), f, indent=2)
    overlaps = check_overlaps("machine", separate(parts()))
    for name in MACHINES:
        for lit in (False, True):
            with open(os.path.join(gm.MODEL_DIR, name + ("_on" if lit else "") + ".json"), "w", newline="\n") as f:
                json.dump(model(name, lit), f, indent=2)
    pump = write_pump()
    write_drill_tower()
    print("Machines : %d modèles et leurs textures écrits, %d chevauchement(s) ; pompe : %d chevauchement(s)."
          % (len(MACHINES), overlaps, pump))


if __name__ == "__main__":
    main()
