#!/usr/bin/env python3
"""
Génère les blocs de construction des fusées d'Orbitalis.

Une fusée se bâtit bloc par bloc sur son pas de tir, d'après un plan sorti de l'ordinateur de conception : moteurs,
réservoirs, interétage, baie de charge utile, coiffe, cabine, panneaux de coque, chemin de câbles, ordinateur de
bord. Aucun n'est un simple cube : un réservoir est une virole ronde dans son bâti ajouré, un moteur a sa chambre,
sa tuyère en gradins et ses turbopompes, une pointe est un cône, une cabine un tronc de cône vitré. Une fois le
dernier bloc posé, ils laissent place à la fusée assemblée (dont les tronçons sont écrits par
generate_rocket_models.py).

Une fusée se regarde de tous les côtés : chaque bloc a ses quatre faces pareilles. Un détail de flanc (une
conduite, une vanne, une turbopompe, un hublot) est répété sur chacune, d'un quart de tour en quart de tour (voir
around()).

Usage : python tools/generate_rocket_parts.py
        (à lancer après generate_textures.py, generate_machine_models.py et generate_launch_pad.py, dont il
        réutilise les textures)
"""
import json
import os

import generate_machine_models as gm
from generate_machine_models import box
from model_kit import check_overlaps, separate
from generate_textures import ITEM_DIR, SIZE, TRANSPARENT, rgb, write_png

# Nom court d'une texture dans les modèles, et la texture qu'il désigne.
TEXTURES = {
    "white": "rocket_white", "orange": "rocket_orange", "solid": "rocket_solid", "steel": "rocket_steel",
    "alloy": "rocket_alloy", "composite": "rocket_composite", "rdark": "rocket_dark", "fairing": "rocket_fairing",
    "nozzle": "rocket_nozzle", "nozzlec": "rocket_nozzle_copper", "checker": "rocket_checker",
    "casing": "pad_casing", "brass": "pad_brass", "cable": "pad_cable", "hazard": "pad_hazard", "plate": "pad_tread",
    "screen": "machine_console_screen", "keys": "machine_keys", "window": "capsule_glass", "led": "pad_led",
    "display": "capsule_screen", "side": "capsule_side",
    "titanium": "rocket_titanium", "stainless": "rocket_stainless", "foil": "robotic_lander_foil", "ionglow": "rocket_ion_glow",
    "sailfoil": "rocket_sail",
}
EMISSIVE = {"screen", "led", "display", "side", "ionglow"}


def whole(part):
    """Pièce dont chaque face reçoit la texture entière."""
    return {**part, "stretch": True}


def turn(part):
    """La même pièce, tournée d'un quart de tour autour de l'axe vertical du bloc : (x, z) devient (16 - z, x)."""
    (x0, y0, z0), (x1, y1, z1) = part["from"], part["to"]
    return {**part, "from": [16 - z1, y0, x0], "to": [16 - z0, y1, x1]}


def around(parts):
    """Ces pièces, posées sur une face, et répétées sur les trois autres : un bloc pareil de tous les côtés."""
    out = []
    for _ in range(4):
        out += parts
        parts = [turn(part) for part in parts]
    return out


def round_body(y0, y1, radius, texture):
    """Corps rond : trois pavés croisés dont la section approche un cercle de ce rayon, centré dans le bloc."""
    r, s, d = radius, radius * 0.5, radius * 0.82
    return [box(8 - r, y0, 8 - s, 8 + r, y1, 8 + s, texture), box(8 - s, y0, 8 - r, 8 + s, y1, 8 + r, texture),
            box(8 - d, y0, 8 - d, 8 + d, y1, 8 + d, texture)]


def sphere(cx, cz, y0, y1, radius, texture):
    """Réservoir sphérique (approché) : trois pavés croisés centrés sur ce point du bloc."""
    r, s, d = radius, radius * 0.5, radius * 0.82
    return [box(cx - r, y0 + s * 0.6, cz - s, cx + r, y1 - s * 0.6, cz + s, texture), box(cx - s, y0 + s * 0.6, cz - r, cx + s, y1 - s * 0.6, cz + r, texture),
            box(cx - d, y0, cz - d, cx + d, y1, cz + d, texture)]


def ring(y0, y1, texture="frame"):
    """Ceinture ajourée : quatre barres sur le pourtour du bloc, vide au milieu."""
    return [box(0, y0, 0, 16, y1, 1.5, texture), box(0, y0, 14.5, 16, y1, 16, texture),
            box(0, y0, 1.5, 1.5, y1, 14.5, texture), box(14.5, y0, 1.5, 16, y1, 14.5, texture)]


def posts(y0, y1, texture="frame"):
    """Quatre longerons d'angle."""
    return [box(x, y0, z, x + 1.5, y1, z + 1.5, texture) for x in (0, 14.5) for z in (0, 14.5)]


def bay():
    """Bâti ajouré d'un bloc : deux ceintures et quatre longerons. On voit au travers ce qu'il porte."""
    return ring(0, 1.5) + ring(14.5, 16) + posts(1.5, 14.5)


def tank(skin):
    """
    Réservoir : une virole ronde à la peau de son matériau, cerclée de deux frettes, dans son bâti ajouré ; sur
    chaque face, une ligne d'alimentation en cuivre et sa vanne de laiton.
    """
    return bay() + round_body(0.5, 15.5, 7, skin) + round_body(4, 5, 7.35, "frame") + round_body(11, 12, 7.35, "frame") + around([
        box(0.2, 1.5, 7.2, 1.3, 14.5, 8.8, "copper"), box(0, 6.5, 6.6, 1.7, 9.5, 9.4, "brass"),
    ])


def engine(skin, bell, turbopump=True):
    """
    Moteur : la structure de poussée en haut, le cardan, la chambre et la tuyère en trois gradins, entre quatre
    bielles. Un moteur à turbopompes porte une pompe et sa conduite de chaque côté de la chambre.
    """
    parts = ring(14.5, 16) + posts(0, 14.5) + [box(1.5, 13, 1.5, 14.5, 14.5, 14.5, skin)]
    parts += round_body(11, 13, 3.2, "metal") + round_body(8, 11, 2.6, "dark")
    parts += round_body(5.5, 8, 3.4, bell) + round_body(3, 5.5, 4.6, bell) + round_body(0, 3, 6, bell)
    parts += round_body(5.2, 5.8, 5, "copper")
    if turbopump:
        parts += around([box(10.6, 8.5, 6.6, 13.2, 12.5, 9.4, "copper"), box(11.4, 6, 7.5, 12.4, 8.5, 8.5, "copper")])
    return parts


# Moteur à poudre : son corps rond, sa tuyère courte et quatre ailerons en deux gradins.
ENGINE_SOLID = round_body(4, 16, 6.5, "solid") + round_body(12.5, 13.5, 6.85, "frame") + round_body(1.5, 4, 3.6, "nozzle") \
    + round_body(0, 1.5, 4.8, "nozzle") + [
        box(7.5, 0, 0, 8.5, 6, 2.5, "rdark"), box(7.5, 6, 0.8, 8.5, 10, 2.5, "rdark"),
        box(7.5, 0, 13.5, 8.5, 6, 16, "rdark"), box(7.5, 6, 13.5, 8.5, 10, 15.2, "rdark"),
        box(0, 0, 7.5, 2.5, 6, 8.5, "rdark"), box(0.8, 6, 7.5, 2.5, 10, 8.5, "rdark"),
        box(13.5, 0, 7.5, 16, 6, 8.5, "rdark"), box(13.5, 6, 7.5, 15.2, 10, 8.5, "rdark"),
    ]
# Moteur alimenté par pression : pas de pompes, une jupe à damier sur la chambre.
ENGINE_PRESSURE = ring(14.5, 16) + posts(0, 14.5) + round_body(8, 14.5, 6.6, "checker") + round_body(5.5, 8, 3.2, "nozzle") \
    + round_body(3, 5.5, 4.4, "nozzle") + round_body(0, 3, 5.6, "nozzle")
# Interétage : un fût sombre dans une couronne de lisses, sa bande de séparation et ses boulons explosifs.
INTERSTAGE = bay() + round_body(1.5, 14.5, 5.4, "rdark") + round_body(7, 9, 5.75, "hazard") + [
    box(7.2, 1.5, 0.2, 8.8, 14.5, 1.3, "frame"), box(7.2, 1.5, 14.7, 8.8, 14.5, 15.8, "frame"),
    box(0.2, 1.5, 7.2, 1.3, 14.5, 8.8, "frame"), box(14.7, 1.5, 7.2, 15.8, 14.5, 8.8, "frame"),
    box(3, 13.6, 3, 4.4, 14.5, 4.4, "brass"), box(11.6, 13.6, 3, 13, 14.5, 4.4, "brass"),
    box(3, 13.6, 11.6, 4.4, 14.5, 13, "brass"), box(11.6, 13.6, 11.6, 13, 14.5, 13, "brass"),
]
# Baie de charge utile : la coiffe ronde, ses quatre coutures, et sur chaque face une trappe de visite cerclée de laiton.
PAYLOAD_BAY = ring(0, 1.5) + ring(14.5, 16) + round_body(1.5, 14.5, 7.2, "fairing") + around([
    box(7.6, 1.5, 0.5, 8.4, 14.5, 0.9, "frame"),
    box(4.4, 5.5, 0.6, 6.6, 10.5, 1.0, "port"), box(4.2, 5.1, 0.7, 6.8, 5.5, 1.1, "brass"), box(4.2, 10.5, 0.7, 6.8, 10.9, 1.1, "brass"),
])
# Coiffe : le bas de l'ogive, ronde, avec ses deux coutures et son anneau de base.
FAIRING = ring(0, 1.5) + round_body(1.5, 16, 7.2, "fairing") + [
    box(7.6, 1.5, 0.5, 8.4, 16, 0.9, "frame"), box(7.6, 1.5, 15.1, 8.4, 16, 15.5, "frame"),
    box(0.5, 1.5, 7.6, 0.9, 16, 8.4, "frame"), box(15.1, 1.5, 7.6, 15.5, 16, 8.4, "frame"),
]
# Pointe : un cône en quatre gradins, coiffé de sa sonde rouge.
NOSE = ring(0, 1) + round_body(0, 4.5, 7, "fairing") + round_body(4.5, 8.5, 5.6, "fairing") + round_body(8.5, 11.5, 4, "fairing") \
    + round_body(11.5, 14, 2.5, "fairing") + [box(7.2, 14, 7.2, 8.8, 15.2, 8.8, "metal"), box(7.6, 15.2, 7.6, 8.4, 16, 8.4, "red")]
# Cabine : un tronc de cône blanc sur son bouclier ; sur chaque face, un hublot cerclé et une tuyère d'orientation.
CREW_CABIN = round_body(0, 1.2, 7.8, "rdark") + round_body(1.2, 7, 7.4, "white") + round_body(7, 12.5, 6.6, "white") \
    + round_body(12.5, 16, 5.6, "white") + around([
        whole(box(5.5, 8, 1.0, 10.5, 11.5, 1.5, "window")),
        box(5, 7.5, 1.1, 11, 8, 1.6, "frame"), box(5, 11.5, 1.1, 11, 12, 1.6, "frame"),
        box(7, 3, 0.2, 9, 4.6, 0.9, "dark"),
    ])
# Haut de la cabine : l'épaulement qui se resserre, le collier d'amarrage, l'écoutille, et deux trappes de parachutes.
CABIN_ROOF = round_body(0, 4, 5.6, "white") + round_body(4, 8, 4.4, "white") + round_body(8, 12.5, 3.2, "metal") \
    + round_body(12.5, 16, 2.4, "dark") + [
        box(6.5, 12.6, 6.5, 9.5, 13.4, 9.5, "brass"),
    ] + around([box(3.4, 4, 6.5, 4.4, 6, 9.5, "hazard")])
# Tour de sauvetage : un treillis, son moteur à quatre tuyères et sa pointe rouge.
ESCAPE_TOWER = [
    box(4, 0, 4, 5, 6, 5, "frame"), box(11, 0, 4, 12, 6, 5, "frame"), box(4, 0, 11, 5, 6, 12, "frame"), box(11, 0, 11, 12, 6, 12, "frame"),
    box(4, 5.5, 4, 12, 6.5, 12, "frame"),
] + round_body(6.5, 13, 2.2, "white") + [
    box(5, 6.5, 7.25, 6, 8.5, 8.75, "nozzle"), box(10, 6.5, 7.25, 11, 8.5, 8.75, "nozzle"),
    box(7.25, 6.5, 5, 8.75, 8.5, 6, "nozzle"), box(7.25, 6.5, 10, 8.75, 8.5, 11, "nozzle"),
    box(6.75, 13, 6.75, 9.25, 15, 9.25, "red"), box(7.5, 15, 7.5, 8.5, 16, 8.5, "metal"),
]


def fuselage():
    """
    Panneau de coque : la peau du lanceur en retrait, raidie par trois cadres et des lisses sur chaque face, avec
    une trappe de visite.
    """
    parts = [box(0.6, 0, 0.6, 15.4, 16, 15.4, "white"), box(0, 0, 0, 16, 1, 16, "frame"), box(0, 15, 0, 16, 16, 16, "frame"),
             box(0.2, 7.4, 0.2, 15.8, 8.6, 15.8, "frame")]
    for a in (3.5, 7.5, 11.5):
        parts += [box(a, 1, 0.1, a + 1, 15, 0.6, "frame"), box(a, 1, 15.4, a + 1, 15, 15.9, "frame"),
                  box(0.1, 1, a, 0.6, 15, a + 1, "frame"), box(15.4, 1, a, 15.9, 15, a + 1, "frame")]
    return parts + around([box(8.7, 9.6, 0.3, 11.3, 13.4, 0.6, "panel")])


# Chemin de câbles : un panneau de coque porteur, sur chaque face, d'une goulotte en saillie tenue par deux colliers
# en laiton (la trappe de visite laisse la place à la goulotte).
RACEWAY = [part for part in fuselage() if part["texture"] != "panel"] + around([
    whole(box(5.5, 0, -1, 10.5, 16, 0.3, "cable")),
    box(5, 3, -1.25, 11, 4, -1, "brass"), box(5, 12, -1.25, 11, 13, -1, "brass"),
])
# Ordinateur de bord : un caisson en carter dans son bâti, son écran cerclé de laiton, ses voyants, ses connecteurs,
# deux radiateurs à ailettes sur les flancs et son antenne.
FLIGHT_COMPUTER = bay() + [
    box(1, 1.5, 1, 15, 14.5, 15, "casing"),
    whole(box(3.5, 6.5, 0.5, 12.5, 12, 1, "screen")),
    box(3, 6, 0.6, 13, 6.5, 1.1, "brass"), box(3, 12, 0.6, 13, 12.5, 1.1, "brass"),
    whole(box(3.5, 3.2, 0.6, 5.3, 4.6, 1, "led")), whole(box(6.2, 3.2, 0.6, 8, 4.6, 1, "led")),
    box(10, 2.6, 0.5, 12.8, 5, 1, "port"),
    box(0.2, 3, 3, 1, 13, 13, "vent"), box(15, 3, 3, 15.8, 13, 13, "vent"),
    box(11.5, 14.5, 11.5, 12.5, 16, 12.5, "dark"),
]

# Booster à poudre : un gros corps de poudre cerclé d'une bande de danger, sa tuyère large, sans ailerons.
ENGINE_BOOSTER = round_body(4, 16, 7, "solid") + round_body(9, 10.5, 7.35, "hazard") + round_body(1.5, 4, 4.4, "nozzle")     + round_body(0, 1.5, 5.6, "nozzle")
# Moteur hypergolique : ses deux sphères d'ergols dorées, sa petite chambre et sa tuyère.
ENGINE_HYPERGOLIC = ring(14.5, 16) + posts(0, 14.5) + [part for cx, cz in ((4.6, 4.6), (11.4, 4.6), (4.6, 11.4), (11.4, 11.4))
                                                     for part in sphere(cx, cz, 9, 14.5, 2.3, "foil")]     + round_body(5.5, 9, 2.2, "dark") + round_body(3, 5.5, 3.2, "nozzle") + round_body(0, 3, 4.2, "nozzle")
# Moteur à vide : un corps fin, et une tuyère de cuivre démesurée qui emplit le bâti.
ENGINE_VACUUM = ring(14.5, 16) + posts(0, 14.5) + [box(1.5, 13, 1.5, 14.5, 14.5, 14.5, "white")] + round_body(11, 13, 2.4, "metal")     + round_body(8.5, 11, 3.4, "nozzlec") + round_body(5.5, 8.5, 5, "nozzlec") + round_body(2.5, 5.5, 6.1, "nozzlec")     + round_body(0, 2.5, 6.8, "nozzlec")
# Moteur nucléaire thermique : le bouclier de radioprotection, le tambour du réacteur et sa bande de danger, une
# grande tuyère de cuivre.
ENGINE_NUCLEAR = ring(14.5, 16) + posts(0, 14.5) + round_body(13, 14.5, 6.6, "metal") + round_body(8, 13, 5.6, "rdark") \
    + round_body(10, 11, 5.95, "hazard") + round_body(6, 8, 3.2, "nozzlec") + round_body(3, 6, 4.6, "nozzlec") + round_body(0, 3, 6, "nozzlec")
# Propulseur ionique : son corps enveloppé d'or, sa grille d'accélération, la lueur bleue du xénon.
ION_THRUSTER = bay() + round_body(5, 14.5, 5.5, "foil") + round_body(2.5, 5, 6, "metal") + round_body(1.5, 2.5, 5, "ionglow")
# Voile solaire : son étui blanc, cerclé du film argenté de la voile repliée, et son moyeu.
SOLAR_SAIL = bay() + round_body(1.5, 14.5, 5.8, "white") + round_body(4, 5, 6.15, "sailfoil") + round_body(11, 12, 6.15, "sailfoil")     + round_body(7.5, 8.5, 6.15, "sailfoil")

PARTS = {
    "rocket_engine_booster": ENGINE_BOOSTER,
    "rocket_engine_hypergolic": ENGINE_HYPERGOLIC,
    "rocket_engine_nuclear": ENGINE_NUCLEAR,
    # Moteur à fusion : sa structure de poussée, deux anneaux d'aimants en cuivre autour du plasma qui luit, sa tuyère.
    "rocket_engine_fusion": ring(14.5, 16) + posts(0, 14.5) + round_body(12.5, 14.5, 6.6, "titanium") + round_body(11.5, 12.5, 6.2, "copper")
    + round_body(9.5, 11.5, 4.0, "ionglow") + round_body(8.5, 9.5, 6.2, "copper") + round_body(5.5, 8.5, 3.0, "rdark")
    + round_body(3, 5.5, 4.4, "nozzle") + round_body(0, 3, 5.8, "nozzle"),
    "rocket_engine_vacuum": ENGINE_VACUUM,
    "rocket_ion_thruster": ION_THRUSTER,
    "rocket_solar_sail": SOLAR_SAIL,
    "rocket_tank_titanium": tank("titanium"),
    "rocket_tank_stainless": tank("stainless"),
    "rocket_engine_solid": ENGINE_SOLID,
    "rocket_engine_pressure": ENGINE_PRESSURE,
    "rocket_engine_kerolox": engine("white", "nozzle"),
    "rocket_engine_staged": engine("composite", "nozzlec") + round_body(2.4, 3.0, 6.3, "brass"),
    "rocket_engine_methalox": engine("stainless", "nozzlec") + round_body(4.0, 4.6, 5.2, "stainless"),
    "rocket_engine_hydrolox": engine("orange", "nozzlec"),
    "rocket_tank_steel": tank("steel"),
    "rocket_tank_aluminum": tank("white"),
    "rocket_tank_alloy": tank("alloy"),
    "rocket_tank_composite": tank("composite"),
    "rocket_tank_cryo": tank("orange"),
    "rocket_motor_casing": tank("solid"),
    "rocket_interstage": INTERSTAGE,
    "rocket_payload_bay": PAYLOAD_BAY,
    "rocket_fairing": FAIRING,
    "rocket_nose": NOSE,
    "crew_cabin": CREW_CABIN,
    "cabin_roof": CABIN_ROOF,
    "escape_tower": ESCAPE_TOWER,
    "rocket_fuselage": fuselage(),
    "rocket_raceway": RACEWAY,
    # L'ordinateur de vol est un bloc plein, écrit par write_machines.py.
}


def blueprint_item():
    """Plan de fusée, en objet : une feuille bleue où se dessine une fusée, coin corné."""
    img = [[TRANSPARENT for _ in range(SIZE)] for _ in range(SIZE)]
    paper, grid, line, dark = rgb("#2F6FB5"), rgb("#3A7CC4"), rgb("#E6EEF6"), rgb("#1B4577")
    for y in range(1, 15):
        for x in range(2, 14):
            img[y][x] = grid if (x % 4 == 2 or y % 4 == 1) else paper
    for i in range(2, 14):
        img[1][i] = img[14][i] = dark
    for y in range(1, 15):
        img[y][2] = img[y][13] = dark
    # La fusée : pointe, corps, ailerons, tuyère.
    for y, (x0, x1) in {3: (7, 8), 4: (7, 8), 5: (6, 9), 6: (6, 9), 7: (6, 9), 8: (6, 9), 9: (6, 9), 10: (6, 9), 11: (5, 10), 12: (7, 8)}.items():
        for x in range(x0, x1 + 1):
            img[y][x] = line
    img[7][7] = img[7][8] = rgb("#F2C14E")
    img[12][7] = img[12][8] = rgb("#E0655F")
    # Coin corné.
    img[1][13] = img[1][12] = img[2][13] = TRANSPARENT
    img[2][12] = rgb("#B9C6D2")
    return img


def write_part_textures():
    """Textures des pièces récentes : titane, inox brossé, lueur du xénon, film de la voile."""
    gm.write_texture("rocket_titanium", [gm.plain(911, "#8A93A0", "#9AA3AE", "#7C8591")])
    stainless = gm.plain(912, "#C9CED4", "#D9DEE3", "#B9C0C7")
    for y in range(SIZE):
        for x in range(SIZE):
            if (x * 7 + y) % 11 == 0:
                stainless[y][x] = gm.shade(stainless[y][x], 1.08)
    gm.write_texture("rocket_stainless", [stainless])
    glow = [[gm.blend(rgb("#E9F2FF"), rgb("#3F76E4"), min(1.0, (((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5) / 8.0)) for x in range(SIZE)]
            for y in range(SIZE)]
    gm.write_texture("rocket_ion_glow", [glow])
    sail = [[rgb("#D9DEE3") if (x + y) % 4 else rgb("#AEB6BF") for x in range(SIZE)] for y in range(SIZE)]
    gm.write_texture("rocket_sail", [sail])


if __name__ == "__main__":
    write_part_textures()
    os.makedirs(gm.MODEL_DIR, exist_ok=True)
    overlaps = 0
    for name, parts in PARTS.items():
        for part in parts:
            assert all(-16 <= c <= 32 for c in part["from"] + part["to"]), (name, part)
            assert all(a < b for a, b in zip(part["from"], part["to"])), (name, part)
        parts = separate(parts)
        overlaps += check_overlaps(name, parts, quiet=True)
        model = gm.build_extra(parts, TEXTURES, EMISSIVE)
        model["textures"]["particle"] = "orbitalis:block/machine_frame"
        with open(os.path.join(gm.MODEL_DIR, name + ".json"), "w", newline="\n") as f:
            json.dump(model, f, indent=2)
    write_png(os.path.join(ITEM_DIR, "rocket_blueprint.png"), blueprint_item())
    print("%d blocs de fusée écrits, et le plan de fusée ; %d chevauchement(s) restant(s)." % (len(PARTS), overlaps))
