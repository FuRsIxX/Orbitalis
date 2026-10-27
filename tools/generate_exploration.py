#!/usr/bin/env python3
"""
Génère les modèles et les textures de l'exploration robotique d'Orbitalis :

- l'atterrisseur robotique posé sur la Lune ou sur Mars : un corps habillé de couverture dorée, quatre jambes et
  leurs patins, deux ailes de panneaux solaires, une antenne parabolique et son voyant vert ;
- le rover, une jeep lunaire comme celle des missions Apollo, en deux modèles : son châssis (plancher, bras de
  suspension, garde-boue, caisson avant, sièges, pupitre et manche, caméra, antennes, palette arrière et son panneau
  solaire) et sa roue grillagée, ronde, que le rendu fait tourner et braquer ;
- les machines de production sur place : la foreuse à glace (son caisson, son derrick et son trépan, son générateur
  à radio-isotopes) et l'extracteur d'oxygène de Mars (son caisson, sa prise d'air, ses deux bouteilles bleues).

Aucune face n'en recouvre une autre (vérifié par check_overlaps).

Usage : python tools/generate_exploration.py
        (à lancer après generate_machine_models.py, dont il réutilise les outils de dessin)
"""
import json
import os
import random

import generate_machine_models as gm
from generate_machine_models import box
from generate_textures import SIZE, rgb
from model_kit import build_tiled, check_overlaps, separate

MODEL_DIR = os.path.join(gm.MODEL_DIR, "..", "exploration")


def foil_texture():
    """Couverture multicouche dorée, froissée : des plis clairs et sombres, une couture."""
    rnd = random.Random(601)
    base = rgb("#D9A93A")
    img = [[gm.shade(base, 1.0 + rnd.uniform(-0.08, 0.08)) for _ in range(SIZE)] for _ in range(SIZE)]
    for _ in range(14):
        x, y = rnd.randrange(SIZE), rnd.randrange(SIZE)
        for k in range(3):
            if 0 <= x + k < SIZE and 0 <= y + k // 2 < SIZE:
                img[y + k // 2][x + k] = gm.shade(img[y + k // 2][x + k], 1.18 if k == 0 else 0.86)
    for x in range(SIZE):
        img[8][x] = gm.shade(img[8][x], 0.82)
    return img


def frame_texture():
    """Structure de l'atterrisseur : un aluminium gris clair, satiné."""
    return gm.plain(602, "#AEB6BF", "#BDC4CC", "#9CA4AD")


def panel_texture():
    """Panneau solaire : des cellules bleu nuit, quadrillées d'argent."""
    img = [[rgb("#1F3F8F") for _ in range(SIZE)] for _ in range(SIZE)]
    for k in range(SIZE):
        for j in range(SIZE):
            if k % 4 == 0 or j % 4 == 0:
                img[k][j] = rgb("#9AA3AE")
            elif (k + j) % 7 == 0:
                img[k][j] = rgb("#3F76E4")
    return img


def dish_texture():
    """Antenne parabolique : un disque blanc, son foyer au centre."""
    img = [[rgb("#E9EBED") for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d > 6.5:
                img[y][x] = rgb("#B9C0C7")
            elif d < 1.5:
                img[y][x] = rgb("#5A626C")
    return img


def light_texture():
    """Voyant d'activité : un vert vif."""
    return [[rgb("#5BE08A") if (x + y) % 5 else rgb("#B6FFC9") for x in range(SIZE)] for y in range(SIZE)]


def wheel_texture():
    """Roue grillagée : un treillis de fils d'acier doré, ses chevrons de bande de roulement."""
    img = [[rgb("#3A4048") for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            if (x + y) % 3 == 0 or (x - y) % 3 == 0:
                img[y][x] = rgb("#C9A64A")
            if y % 4 == 0 and x % 2 == 0:
                img[y][x] = rgb("#8C949E")
    return img


def wheel_side_texture():
    """
    Flanc de la roue, centré : le treillis doré, quatre rayons sombres qui montrent qu'elle tourne, le moyeu, et la
    bande de titane du pourtour.
    """
    img = [[rgb("#3A4048") for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            r = (dx * dx + dy * dy) ** 0.5
            if r > 4.3:
                img[y][x] = rgb("#B9C0C7") if (x + y) % 2 else rgb("#9AA3AE")
            elif r < 1.6:
                img[y][x] = rgb("#8C949E")
            elif abs(dx) < 0.6 or abs(dy) < 0.6:
                img[y][x] = rgb("#4A515A")
            else:
                img[y][x] = rgb("#C9A64A") if (x + y) % 2 else rgb("#8E7432")
    return img


def hub_texture():
    """Moyeu : un chapeau gris sombre, ses boulons."""
    img = [[rgb("#4A515A") for _ in range(SIZE)] for _ in range(SIZE)]
    for x, y in ((6, 6), (9, 6), (6, 9), (9, 9)):
        img[y][x] = rgb("#9AA3AE")
    for x in (7, 8):
        for y in (7, 8):
            img[y][x] = rgb("#2A2F35")
    return img


def white_texture():
    """Caisson avant : la couverture thermique blanche, ses coutures, ses miroirs de radiateur."""
    img = gm.plain(606, "#E6E9EC", "#F1F3F5", "#D8DCE0")
    for k in range(SIZE):
        img[k][0] = img[0][k] = rgb("#C9CED4")
    for x0, y0 in ((3, 3), (9, 3), (3, 9), (9, 9)):
        for y in range(y0, y0 + 4):
            for x in range(x0, x0 + 4):
                img[y][x] = rgb("#B9C8D8") if (x + y) % 3 else rgb("#E9F2FF")
    return img


def dark_texture():
    """Boîtier de caméra, manche : un gris très sombre, mat."""
    return gm.plain(607, "#2A2F35", "#353B42", "#23272C")


def lens_texture():
    """Objectif : un verre bleu nuit, son reflet."""
    img = [[rgb("#1A2440") for _ in range(SIZE)] for _ in range(SIZE)]
    for k in range(4, 8):
        img[k][k] = rgb("#6E9BF0")
    return img


def fender_texture():
    """Garde-boue : une fibre de verre gris bleuté, mate, qui se détache sur le régolithe."""
    return gm.plain(603, "#8E9EB2", "#9FAEC0", "#7F8FA3")


def seat_texture():
    """Siège : une sangle bleu-gris tendue sur un cadre, ses coutures."""
    img = [[rgb("#5A6E86") for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            if x % 4 == 0:
                img[y][x] = rgb("#4A5C72")
            if y in (0, 15) or x in (0, 15):
                img[y][x] = rgb("#9AA3AE")
    return img


def console_texture():
    """Pupitre du rover : un boîtier sombre, ses cadrans et ses voyants."""
    img = [[rgb("#2A2F35") for _ in range(SIZE)] for _ in range(SIZE)]
    for cx, cy in ((4, 5), (11, 5)):
        for y in range(SIZE):
            for x in range(SIZE):
                if (x - cx) ** 2 + (y - cy) ** 2 <= 5:
                    img[y][x] = rgb("#E9EBED") if (x - cx) ** 2 + (y - cy) ** 2 > 2 else rgb("#3A4048")
    for x, color in ((3, "#5BE08A"), (6, "#FFB23E"), (9, "#5BE08A"), (12, "#E0453A")):
        img[11][x] = rgb(color)
    return img


def isru_body_texture(stripe):
    """Caisson d'une machine ISRU : la coque blanche, un bandeau de couleur, une grille d'aération."""
    img = gm.plain(604, "#E6E9EC", "#F1F3F5", "#D8DCE0")
    for x in range(SIZE):
        img[3][x] = rgb(stripe)
        img[4][x] = gm.shade(rgb(stripe), 0.8)
    for y in range(8, 13):
        for x in range(4, 12):
            if x % 2 == 0:
                img[y][x] = rgb("#5A626C")
    for k in range(SIZE):
        img[0][k] = img[15][k] = img[k][0] = img[k][15] = rgb("#AEB6BF")
    return img


def shaft_texture():
    """Trépan : un acier sombre, son filet en spirale."""
    img = [[rgb("#5A626C") for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(SIZE):
        img[y][(y * 2) % SIZE] = rgb("#9AA3AE")
        img[y][(y * 2 + 1) % SIZE] = rgb("#3A4048")
    return img


def rtg_texture():
    """Générateur à radio-isotopes : un cylindre noir à ailettes, sa plaque jaune."""
    img = [[rgb("#22262B") for _ in range(SIZE)] for _ in range(SIZE)]
    for x in range(SIZE):
        if x % 3 == 0:
            for y in range(SIZE):
                img[y][x] = rgb("#3A4048")
    for x in range(5, 11):
        img[7][x] = rgb("#E8C02A")
        img[8][x] = rgb("#E8C02A")
    return img


def intake_texture():
    """Prise d'air de l'extracteur : une grille sombre, son ventilateur."""
    img = [[rgb("#2A2F35") for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 6.5 and int(d) % 2 == 0:
                img[y][x] = rgb("#5A626C")
    return img


def bottle_texture():
    """Bouteille d'oxygène : un bleu satiné, son reflet, sa bague."""
    img = gm.plain(605, "#3F76E4", "#5B8FF0", "#2F5DB8")
    for y in range(SIZE):
        img[y][3] = gm.shade(img[y][3], 1.3)
    for x in range(SIZE):
        img[2][x] = rgb("#C9CED4")
    return img


TEXTURES = {
    "body_drill": "ice_drill_body", "body_oxygen": "oxygen_extractor_body", "shaft": "ice_drill_shaft", "rtg": "isru_rtg",
    "intake": "oxygen_extractor_intake", "bottle": "oxygen_extractor_bottle",
    "wheel": "rover_wheel", "fender": "rover_fender", "seat": "rover_seat", "console": "rover_console",
    "wheel_side": "rover_wheel_side", "hub": "rover_hub", "white": "rover_white", "dark": "rover_dark", "lens": "rover_lens",
    "foil": "robotic_lander_foil", "frame": "robotic_lander_frame", "panel": "robotic_lander_panel",
    "dish": "robotic_lander_dish", "light": "robotic_lander_light",
}
EMISSIVE = {"light"}


def lander():
    """L'atterrisseur, dans un bloc : ses patins et ses jambes, son corps doré et son pont, ses ailes, son antenne."""
    parts = []
    for x0, z0 in ((2, 2), (12.5, 2), (2, 12.5), (12.5, 12.5)):
        parts.append(box(x0 - 0.5, 0, z0 - 0.5, x0 + 2, 0.5, z0 + 2, "frame"))
        parts.append(box(x0, 0.5, z0, x0 + 1.5, 5, z0 + 1.5, "frame"))
    parts += [
        box(4, 5, 4, 12, 10, 12, "foil"),
        box(3, 10, 3, 13, 11, 13, "frame"),
        # Les deux ailes solaires, sur leur bras.
        box(12, 7, 7.5, 13, 7.5, 8.5, "frame"), box(13, 7, 5, 16, 7.5, 11, "panel"),
        box(3, 7, 7.5, 4, 7.5, 8.5, "frame"), box(0, 7, 5, 3, 7.5, 11, "panel"),
        # Le mât, l'antenne et son voyant.
        box(7.5, 11, 7.5, 8.5, 14, 8.5, "frame"),
        {**box(5.5, 14, 5.5, 10.5, 14.5, 10.5, "dish"), "stretch": True},
        box(7.6, 14.5, 7.6, 8.4, 15.3, 8.4, "light"),
    ]
    return parts


# Le rover est dessiné à 32 pixels pour deux blocs (le rendu le réduit à la taille du véhicule) : le modèle déborde
# du bloc, de -8 à 24 en largeur et en longueur, centré sur 8. Face avant au nord, côté gauche à l'ouest.
ROVER_WHEELS = ((-6, -2), (22, -2), (-6, 18), (22, 18))
WHEEL_Y = 5


def mirror(part):
    """La même pièce, de l'autre côté du rover (symétrie par rapport au plan x = 8)."""
    out = {**part, "from": [16 - part["to"][0], part["from"][1], part["from"][2]],
           "to": [16 - part["from"][0], part["to"][1], part["to"][2]]}
    if "faces" in part:
        swap = {"west": "east", "east": "west"}
        out["faces"] = {swap.get(k, k): v for k, v in part["faces"].items()}
    return out


def rover():
    """
    Le châssis du rover, sans ses roues : le plancher, les bras de suspension et les garde-boue de chaque roue, le
    caisson avant et sa caméra, les antennes (la grande en ombrelle à gauche, l'hélice à droite), le pupitre, les deux
    sièges et le manche entre eux, la palette arrière dorée, son panneau solaire et une pelle à échantillons.
    """
    # Le plancher, et ses deux longerons sombres dessous.
    parts = [box(-3, 6, -7, 19, 7, 23, "frame"), box(-3, 5, -7, -1, 6, 23, "dark"), box(17, 5, -7, 19, 6, 23, "dark")]
    left = []
    for _, wz in ((-6, -2), (-6, 18)):
        left += [
            box(-4, 4.5, wz - 1, -3, 6, wz + 1, "frame"),
            box(-8.5, 10.6, wz - 4, -3.5, 11.8, wz + 4, "fender"),
            box(-8.5, 9.2, wz - 5.5, -3.5, 11.8, wz - 4, "fender"),
            box(-8.5, 9.2, wz + 4, -3.5, 11.8, wz + 5.5, "fender"),
            box(-3.5, 7, wz - 0.5, -3, 10.6, wz + 0.5, "frame"),
        ]
    parts += left + [mirror(p) for p in left]
    parts += [
        # Le caisson avant : batteries et électronique sous leur couverture blanche.
        box(0, 7, -6, 16, 10.5, -1, "white"),
        # La caméra de télévision, sur son mât, au milieu.
        box(7.5, 10.5, -5, 8.5, 14, -4, "frame"),
        box(6.5, 14, -6.5, 9.5, 16.5, -3.5, "dark"),
        box(7.25, 14.5, -7, 8.75, 16, -6.5, "lens"),
        # La grande antenne : son mât, sa parabole en ombrelle inclinée vers la Terre, sa source.
        box(1, 10.5, -4.5, 2, 24, -3.5, "frame"),
        {**box(-3, 24, -8, 6, 24.5, 1, "dish"), "stretch": True,
         "rotation": {"origin": [1.5, 24, -3.5], "axis": "x", "angle": 22.5}},
        {**box(1.1, 24.5, -4.4, 1.9, 26.5, -3.6, "frame"), "rotation": {"origin": [1.5, 24, -3.5], "axis": "x", "angle": 22.5}},
        # L'antenne hélice, à droite.
        box(14, 10.5, -4.5, 15, 20, -3.5, "frame"),
        box(13.5, 20, -5, 15.5, 24, -3, "fender"),
        # Le pupitre, tourné vers les sièges, sur son pied.
        box(7.25, 7, 1, 8.75, 13, 2.5, "frame"),
        {**box(4, 13, 0, 12, 17, 2.5, "frame"), "faces": {"south": "console"}},
        # Les deux sièges, leurs dossiers.
        box(0, 7, 6, 6, 8, 12, "seat"), box(0, 7, 12, 6, 14, 13, "seat"),
        box(10, 7, 6, 16, 8, 12, "seat"), box(10, 7, 12, 16, 14, 13, "seat"),
        # L'accoudoir entre eux, et le manche en T.
        box(7, 7, 6, 9, 9.5, 12, "frame"),
        box(7.6, 9.5, 7, 8.4, 11.5, 7.8, "dark"),
        box(7.2, 11.5, 6.8, 8.8, 12.3, 8, "dark"),
        # La palette arrière dorée, son panneau solaire, la pelle à échantillons.
        box(1, 7, 15, 15, 12, 22, "foil"),
        box(0, 12, 14.5, 16, 12.5, 22.5, "panel"),
        box(15.5, 7, 21, 16.2, 19, 21.7, "frame"),
        box(15.2, 19, 20.5, 16.5, 20.5, 22.2, "frame"),
    ]
    return parts


def rover_wheel():
    """
    Une roue, centrée dans son bloc, son axe le long de x : un octogone grillagé de dix pixels, ses flancs à rayons,
    son moyeu à l'extérieur (côté ouest) et son roulement à l'intérieur. Le rendu la fait tourner et braquer.
    """
    side = {"west": "wheel_side", "east": "wheel_side"}
    parts = [{**box(6, 3, 5, 10, 13, 11, "wheel"), "faces": side}]
    for z0, z1, y0, y1 in ((4, 5, 4, 12), (11, 12, 4, 12), (3, 4, 5, 11), (12, 13, 5, 11)):
        parts.append({**box(6, y0, z0, 10, y1, z1, "wheel"), "faces": side})
    parts += [box(5, 6.5, 6.5, 6, 9.5, 9.5, "hub"), box(10, 7, 7, 10.6, 9, 9, "hub")]
    return parts


def ice_drill():
    """La foreuse, face avant au nord : son socle, son caisson, son générateur, son derrick, son trépan, sa sortie d'eau."""
    parts = [
        box(1, 0, 1, 15, 1, 15, "frame"),
        box(3, 1, 3, 13, 9, 13, "body_drill"),
        box(13, 1, 6, 15, 6, 10, "rtg"),
        box(6, 3, 2, 10, 7, 3, "frame"),
        box(7.5, 9, 7.5, 8.5, 15, 8.5, "shaft"),
        box(4, 15, 4, 12, 16, 12, "frame"),
    ]
    for x0, z0 in ((4, 4), (11, 4), (4, 11), (11, 11)):
        parts.append(box(x0, 9, z0, x0 + 1, 15, z0 + 1, "frame"))
    return parts


def oxygen_extractor():
    """L'extracteur, face avant au nord : son socle, son caisson, sa prise d'air, sa sortie, ses deux bouteilles."""
    return [
        box(1, 0, 1, 15, 1, 15, "frame"),
        box(2, 1, 3, 14, 10, 13, "body_oxygen"),
        {**box(4, 10, 5, 12, 11, 11, "frame"), "faces": {"up": "intake"}},
        box(6, 4, 2, 10, 8, 3, "frame"),
        box(3, 1, 13, 6, 9, 15, "bottle"),
        box(10, 1, 13, 13, 9, 15, "bottle"),
    ]


def main():
    write = gm.write_texture
    write("robotic_lander_foil", [foil_texture()])
    write("robotic_lander_frame", [frame_texture()])
    write("robotic_lander_panel", [panel_texture()])
    write("robotic_lander_dish", [dish_texture()])
    write("robotic_lander_light", [light_texture()])
    write("rover_wheel", [wheel_texture()])
    write("rover_fender", [fender_texture()])
    write("rover_seat", [seat_texture()])
    write("rover_console", [console_texture()])
    write("rover_wheel_side", [wheel_side_texture()])
    write("rover_hub", [hub_texture()])
    write("rover_white", [white_texture()])
    write("rover_dark", [dark_texture()])
    write("rover_lens", [lens_texture()])
    write("ice_drill_body", [isru_body_texture("#E8872A")])
    write("oxygen_extractor_body", [isru_body_texture("#3F76E4")])
    write("ice_drill_shaft", [shaft_texture()])
    write("isru_rtg", [rtg_texture()])
    write("oxygen_extractor_intake", [intake_texture()])
    write("oxygen_extractor_bottle", [bottle_texture()])
    os.makedirs(MODEL_DIR, exist_ok=True)
    overlaps = 0
    for name, build, particle in (("robotic_lander", lander, "robotic_lander_foil"), ("rover", rover, "rover_fender"), ("rover_wheel", rover_wheel, "rover_wheel"),
                                  ("ice_drill", ice_drill, "ice_drill_body"), ("oxygen_extractor", oxygen_extractor, "oxygen_extractor_body")):
        parts = separate(build())
        overlaps += check_overlaps(name, parts)
        model = build_tiled(parts, TEXTURES, EMISSIVE, particle="orbitalis:block/" + particle)
        with open(os.path.join(MODEL_DIR, name + ".json"), "w", newline="\n") as f:
            json.dump(model, f, indent=1)
    print("exploration : modèles écrits, %d chevauchement(s)" % overlaps)


if __name__ == "__main__":
    main()
