#!/usr/bin/env python3
"""
Génère les trois grands postes de travail d'Orbitalis : l'ordinateur de conception, le pupitre de lancement et la
station de poursuite.

Ce sont trois meubles de la même famille, trois blocs de large, deux de haut et deux de profondeur : un socle, un
caisson, un plan de travail avec son clavier, et derrière lui un mur d'écrans, un grand au centre et deux petits
tournés vers l'opérateur. Ce qui les distingue se voit de loin :
- l'ordinateur de conception est bleu ; il porte une maquette de fusée sur son socle lumineux, une table traçante
  d'où sort un plan, et des rouleaux de plans sur son toit ;
- le pupitre de lancement est rouge ; il porte une rangée de leviers, le gros bouton de mise à feu sous son capot
  rayé, et un gyrophare orange sur son toit ;
- la station de poursuite est verte ; elle porte un manche, un récepteur, une parabole inclinée et une antenne fouet.
Le pupitre d'essai, d'un seul bloc, est le petit frère rouge du pupitre de lancement.

Les modèles sont dessinés face avant au nord, le bloc du meuble étant celui du milieu, en bas, à l'avant. Les
textures gardent partout des pixels de la taille de ceux des blocs, et aucune face n'en recouvre une autre.

Usage : python tools/generate_consoles.py
        (à lancer après generate_machine_models.py, dont il réutilise des textures)
"""
import json
import math
import os

import generate_machine_models as gm
from generate_machine_models import box
from generate_textures import rgb
from model_kit import build_tiled, check_overlaps, display

TEXTURES = {
    "shell": "console_shell", "shelld": "console_shell_dark", "desk": "console_desk", "paper": "console_paper",
    "holo": "console_holo", "dish": "console_dish", "amber": "console_amber",
    "trim_blue": "console_trim_blue", "trim_red": "console_trim_red", "trim_green": "console_trim_green",
    "screen_design": "console_design_screen", "screen_launch": "console_launch_screen", "screen_track": "console_track_screen",
    "vent": "machine_vent", "panel": "machine_panel", "keys": "console_keys", "light": "machine_light",
    "red": "machine_red", "white": "rocket_white", "brass": "pad_brass", "hazard": "pad_hazard", "led": "pad_led",
}
EMISSIVE = {"screen_design", "screen_launch", "screen_track", "holo", "amber", "led", "trim_blue", "trim_red", "trim_green"}


# ---------------------------------------------------------------------------
# Textures
# ---------------------------------------------------------------------------
def canvas(size, color):
    return [[rgb(color) for _ in range(size)] for _ in range(size)]


def dot(img, x, y, color):
    if 0 <= y < len(img) and 0 <= x < len(img[0]):
        img[y][x] = rgb(color)


def hline(img, x0, x1, y, color):
    for x in range(x0, x1 + 1):
        dot(img, x, y, color)


def vline(img, x, y0, y1, color):
    for y in range(y0, y1 + 1):
        dot(img, x, y, color)


def rect(img, x0, y0, x1, y1, color):
    for y in range(y0, y1 + 1):
        hline(img, x0, x1, y, color)


def outline(img, x0, y0, x1, y1, color):
    hline(img, x0, x1, y0, color)
    hline(img, x0, x1, y1, color)
    vline(img, x0, y0, y1, color)
    vline(img, x1, y0, y1, color)


# Chiffres de trois pixels sur cinq.
DIGITS = {
    "0": "111101101101111", "1": "010110010010111", "2": "111001111100111", "3": "111001111001111", "4": "101101111001001",
    "5": "111100111001111", "6": "111100111101111", "7": "111001001010010", "8": "111101111101111", "9": "111101111001111",
    "T": "111010010010010", "-": "000000111000000",
}


def text(img, x, y, string, color):
    for char in string:
        bits = DIGITS[char]
        for k, bit in enumerate(bits):
            if bit == "1":
                dot(img, x + k % 3, y + k // 3, color)
        x += 4


def design_screen(step):
    """Écran de l'ordinateur de conception : un plan de fusée qu'un trait de balayage parcourt, des cotes, des barres."""
    img = canvas(32, "#0A1B2E")
    # Grand écran (32×16) : quadrillage, fusée en plan, cotes et barres de performances.
    for x in range(0, 32, 4):
        vline(img, x, 0, 15, "#10283F")
    for y in range(0, 16, 4):
        hline(img, 0, 31, y, "#10283F")
    for y, (x0, x1) in {1: (8, 8), 2: (7, 9), 3: (7, 9), 4: (6, 10), 5: (6, 10), 6: (6, 10), 7: (6, 10), 8: (6, 10), 9: (6, 10),
                        10: (6, 10), 11: (6, 10), 12: (5, 11), 13: (4, 12)}.items():
        dot(img, x0, y, "#7FD0FF")
        dot(img, x1, y, "#7FD0FF")
    hline(img, 6, 10, 4, "#4FA0DC")
    hline(img, 6, 10, 9, "#4FA0DC")
    hline(img, 7, 9, 14, "#FFB23E")
    vline(img, 2, 1, 13, "#3A7CC4")
    hline(img, 1, 3, 1, "#3A7CC4")
    hline(img, 1, 3, 13, "#3A7CC4")
    for row, width in enumerate((11, 8, 13, 6)):
        hline(img, 16, 29, 2 + row * 3, "#16324D")
        hline(img, 16, 16 + width, 2 + row * 3, "#7FD0FF" if row != 2 else "#5BE08A")
    hline(img, 16, 29, 14, "#3A7CC4")
    scan = 1 + (step * 13) // 8
    hline(img, 4, 12, scan, "#D8F1FF")
    # Petit écran de gauche (16×16) : un histogramme.
    rect(img, 0, 16, 15, 31, "#081625")
    for k, height in enumerate((5, 9, 7, 11, 4, 8)):
        rect(img, 2 + k * 2, 29 - height - (1 if (step + k) % 4 == 0 else 0), 2 + k * 2, 29, "#4FA0DC")
    hline(img, 1, 14, 30, "#7FD0FF")
    # Petit écran de droite : une trajectoire qui monte en courbe.
    rect(img, 16, 16, 31, 31, "#081625")
    for k in range(13):
        dot(img, 17 + k, 30 - int(round(12 * (1 - math.cos(k / 12.0 * math.pi / 2)))), "#5BE08A" if k <= (step * 13) // 7 else "#1E4A63")
    outline(img, 16, 16, 31, 31, "#16324D")
    return img


def launch_screen(step):
    """Écran du pupitre de lancement : le compte à rebours, la ligne des voyants, les jauges d'ergols."""
    img = canvas(32, "#120A08")
    amber, red, green = "#FFB23E", "#FF5A4A", "#5BE08A"
    text(img, 4, 2, "T-", amber)
    # Les grands chiffres : chaque pixel du chiffre est doublé.
    digits = "%02d" % (8 - step)
    for index, char in enumerate(digits):
        for k, bit in enumerate(DIGITS[char]):
            if bit == "1":
                rect(img, 14 + index * 8 + (k % 3) * 2, 2 + (k // 3) * 2, 15 + index * 8 + (k % 3) * 2, 3 + (k // 3) * 2, amber)
    for k in range(7):
        rect(img, 2 + k * 4, 13, 4 + k * 4, 14, green if k <= step else "#3A2A12")
    hline(img, 0, 31, 0, "#5A2A18")
    hline(img, 0, 31, 15, "#5A2A18")
    # Petit écran de gauche : trois jauges d'ergols qui se remplissent.
    rect(img, 0, 16, 15, 31, "#0E0806")
    for k, color in enumerate(("#6FB4EE", amber, "#E6F4FF")):
        outline(img, 2 + k * 5, 18, 4 + k * 5, 29, "#5A2A18")
        level = min(10, 4 + step + k)
        rect(img, 3 + k * 5, 29 - level, 3 + k * 5, 28, color)
    # Petit écran de droite : la grille des feux verts, dont un clignote en rouge.
    rect(img, 16, 16, 31, 31, "#0E0806")
    for row in range(4):
        for col in range(4):
            bad = row == 2 and col == 1 and step % 2 == 0
            rect(img, 18 + col * 3, 18 + row * 3, 19 + col * 3, 19 + row * 3, red if bad else green if (row * 4 + col) <= step * 2 else "#1F3A28")
    return img


def track_screen(step):
    """Écran de la station de poursuite : la carte et la trace d'une orbite, un radar, un signal."""
    img = canvas(32, "#06140F")
    land = "#1F5A3A"
    for x0, y0, x1, y1 in ((3, 3, 8, 6), (5, 7, 7, 11), (13, 2, 18, 5), (14, 6, 17, 10), (20, 3, 28, 6), (24, 7, 27, 9), (26, 11, 29, 12)):
        rect(img, x0, y0, x1, y1, land)
    for x in range(0, 32, 8):
        vline(img, x, 1, 14, "#0E2A1E")
    hline(img, 0, 31, 8, "#0E2A1E")
    for x in range(32):
        dot(img, x, 8 - int(round(4.5 * math.sin(x / 31.0 * 2 * math.pi))), "#2FB8A0")
    sat = (step * 4 + 2) % 32
    sy = 8 - int(round(4.5 * math.sin(sat / 31.0 * 2 * math.pi)))
    rect(img, sat - 1, sy - 1, sat + 1, sy + 1, "#06140F")
    dot(img, sat, sy, "#FFFFFF")
    dot(img, sat - 1, sy, "#8CF5C0")
    dot(img, sat + 1, sy, "#8CF5C0")
    dot(img, sat, sy - 1, "#8CF5C0")
    dot(img, sat, sy + 1, "#8CF5C0")
    # Petit écran de gauche : un radar dont le faisceau tourne.
    rect(img, 0, 16, 15, 31, "#041009")
    for k in range(24):
        a = k / 24.0 * 2 * math.pi
        dot(img, 8 + int(round(6 * math.cos(a))), 24 + int(round(6 * math.sin(a))), "#1F5A3A")
        dot(img, 8 + int(round(3 * math.cos(a))), 24 + int(round(3 * math.sin(a))), "#14402A")
    beam = step / 8.0 * 2 * math.pi
    for k in range(1, 7):
        dot(img, 8 + int(round(k * math.cos(beam))), 24 + int(round(k * math.sin(beam))), "#8CF5C0")
    dot(img, 11, 21, "#FFFFFF" if step in (6, 7, 0) else "#2FB8A0")
    # Petit écran de droite : le signal reçu.
    rect(img, 16, 16, 31, 31, "#041009")
    hline(img, 17, 30, 24, "#14402A")
    for x in range(14):
        dot(img, 17 + x, 24 - int(round(4 * math.sin((x + step * 2) * 0.9) * math.exp(-((x - 7) ** 2) / 30.0))), "#8CF5C0")
    hline(img, 17, 17 + (step * 13) // 7, 30, "#2FB8A0")
    return img


def holo_frame(step):
    """Socle lumineux de la maquette : un anneau bleu qui bat."""
    img = canvas(16, "#0A1B2E")
    for k in range(40):
        a = k / 40.0 * 2 * math.pi
        dot(img, int(round(7.5 + 5.5 * math.cos(a))), int(round(7.5 + 5.5 * math.sin(a))), ("#7FD0FF", "#4FA0DC", "#3A7CC4", "#4FA0DC")[step])
        dot(img, int(round(7.5 + 2.5 * math.cos(a))), int(round(7.5 + 2.5 * math.sin(a))), "#16324D")
    return img


def shell_texture(seed, base, light, dark):
    """Tôle d'un caisson : un panneau à bord relevé, un joint au milieu, quatre vis. Du grain, pas de dégradé."""
    img = gm.plain(seed, base, light, dark)
    for i in range(16):
        img[0][i] = img[i][0] = rgb(light)
        img[15][i] = img[i][15] = rgb(dark)
    for i in range(1, 15):
        img[8][i] = rgb(dark)
        img[9][i] = rgb(light)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        dot(img, x, y, dark)
        dot(img, x + 1, y + 1, light)
    return img


def desk_texture():
    """Plan de travail : un stratifié clair, une rive sombre, un tapis de bureau gris et de fines rayures d'usage."""
    img = gm.plain(303, "#9AA3AE", "#A6AFBA", "#8F98A3")
    for i in range(16):
        img[0][i] = rgb("#C3CAD2")
        img[15][i] = rgb("#6E7782")
    for x, y in ((3, 4), (4, 4), (9, 7), (10, 7), (11, 7), (5, 11), (12, 12)):
        dot(img, x, y, "#B4BCC6")
    return img


def keys_texture():
    """
    Clavier : cinq rangées de touches en relief, chacune avec son arête claire et son ombre, une barre d'espace,
    une touche d'entrée verte, une touche d'arrêt rouge et un pavé de quatre touches à droite.
    """
    img = canvas(16, "#15181C")
    cap, top, low = "#4B535D", "#6A737E", "#2C3138"
    for row in range(5):
        y = 1 + row * 3
        x = 1 + (row % 2)
        while x <= 10:
            dot(img, x, y, top)
            dot(img, x + 1, y, cap)
            dot(img, x, y + 1, cap)
            dot(img, x + 1, y + 1, low)
            x += 3
    # Barre d'espace, à la place des touches du milieu de la dernière rangée.
    rect(img, 4, 13, 9, 14, "#15181C")
    hline(img, 4, 9, 13, top)
    hline(img, 4, 9, 14, low)
    # Pavé de droite : quatre touches, dont l'entrée et l'arrêt.
    for k, color in enumerate(("#6A737E", "#6A737E", "#3FBF7A", "#D8442F")):
        y = 1 + k * 3 + (1 if k >= 2 else 0)
        rect(img, 13, y, 14, y + 1, color)
        dot(img, 14, y + 1, "#2C3138" if k < 2 else ("#2A8A56" if k == 2 else "#9A2E1F"))
    return img


def trim(color, light, dark):
    """Liseré de couleur : un ruban lumineux segmenté, chaque segment avec son reflet et son ombre."""
    img = canvas(16, color)
    for y in range(16):
        for x in range(16):
            if x % 4 == 3:
                img[y][x] = rgb(dark)
            elif y % 4 == 0:
                img[y][x] = rgb(light)
    return img


def write_textures():
    gm.write_texture("console_shell", [shell_texture(301, "#3B424C", "#4A525D", "#2B3038")])
    gm.write_texture("console_shell_dark", [shell_texture(302, "#22262C", "#2E333A", "#16181C")])
    gm.write_texture("console_desk", [desk_texture()])
    gm.write_texture("console_keys", [keys_texture()])
    gm.write_texture("console_paper", [gm.plain(304, "#DCE9F5", "#EEF5FB", "#B9CFE4", [5, 11])])
    gm.write_texture("console_dish", [gm.plain(305, "#D9DEE3", "#EBEEF1", "#C3CAD1", [7])])
    gm.write_texture("console_amber", [gm.plain(306, "#FFB23E", "#FFD27A", "#F08A2B"), gm.plain(307, "#F08A2B", "#FFB23E", "#C96A1C")], 8)
    gm.write_texture("console_trim_blue", [trim("#3A8EE0", "#7FD0FF", "#1F5FA8")])
    gm.write_texture("console_trim_red", [trim("#D8442F", "#FF8A6A", "#9A2E1F")])
    gm.write_texture("console_trim_green", [trim("#2FA86A", "#8CF5C0", "#1E7447")])
    gm.write_texture("console_holo", [holo_frame(s) for s in range(4)], 6)
    gm.write_texture("console_design_screen", [design_screen(s) for s in range(8)], 5, interpolate=False)
    gm.write_texture("console_launch_screen", [launch_screen(s) for s in range(8)], 20, interpolate=False)
    gm.write_texture("console_track_screen", [track_screen(s) for s in range(8)], 6, interpolate=False)


# ---------------------------------------------------------------------------
# Modèles
# ---------------------------------------------------------------------------
def faced(part, **faces):
    return {**part, "faces": faces}


def screen(x0, y0, x1, y1, z, texture, uv, angle=None, origin=None):
    """Dalle d'écran : seule sa face avant porte l'image ; son chant est sombre."""
    part = {"from": [x0, y0, z], "to": [x1, y1, z + 0.3], "texture": texture, "stretch": True,
            "only": ("north", "west", "east", "up", "down"),
            "faces": {"west": "shelld", "east": "shelld", "up": "shelld", "down": "shelld"},
            "uv": {"north": uv, "west": [0, 0, 1, 1], "east": [0, 0, 1, 1], "up": [0, 0, 1, 1], "down": [0, 0, 1, 1]}}
    if angle:
        part["rotation"] = {"origin": origin, "axis": "y", "angle": angle}
    return part


def turned(part, angle, origin):
    return {**part, "stretch": True, "rotation": {"origin": origin, "axis": "y", "angle": angle}}


def shell(trim_name, screen_name):
    """Le meuble commun aux trois postes : socle, caisson, plan de travail, clavier, mur d'écrans."""
    left, right = [-3, 21, 18], [19, 21, 18]
    return [
        # Socle, et son liseré de couleur en façade.
        box(-16, 0, 1, 32, 2, 32, "shelld"),
        {**box(-16, 0.5, 0.5, 32, 1.5, 1, trim_name), "only": ("north", "west", "east", "up", "down")},
        # Caisson : deux grilles d'aération et une porte de service.
        box(-15, 2, 5, 31, 12, 31, "shell"),
        {**box(-13, 4, 4.6, -3, 10, 5, "vent"), "only": ("north", "west", "east", "up", "down")},
        {**box(19, 4, 4.6, 29, 10, 5, "vent"), "only": ("north", "west", "east", "up", "down")},
        {**box(1, 3.5, 4.6, 15, 10.5, 5, "panel"), "only": ("north", "west", "east", "up", "down")},
        box(12.5, 6.5, 4.2, 13.5, 7.5, 4.6, "led"),
        # Plan de travail, son chant de couleur, le clavier.
        box(-16, 12, 1, 32, 14, 32, "desk"),
        {**box(-16, 12.5, 0.5, 32, 13.5, 1, trim_name), "only": ("north", "west", "east", "up", "down")},
        box(1, 14, 2, 15, 14.4, 9, "shelld"),
        {**faced(box(1.5, 14.4, 4, 14.5, 15, 8.5, "shelld"), up="keys"), "stretch": True,
         "uv": {"up": [0, 0, 16, 16], "north": [0, 0, 13, 1], "south": [0, 0, 13, 1], "west": [0, 0, 5, 1], "east": [0, 0, 5, 1], "down": [0, 0, 13, 5]}},
        # Mur d'écrans, sa casquette, ses joues.
        box(-16, 14, 18, 32, 28, 31, "shell"),
        box(-16, 28, 17, 32, 29, 32, "shelld"),
        {**box(-16, 28.2, 16.6, 32, 28.8, 17, trim_name), "only": ("north", "west", "east", "up", "down")},
        box(-16, 14, 14, -14, 24, 18, "shelld"), box(30, 14, 14, 32, 24, 18, "shelld"),
        # Grand écran central, dans son cadre.
        {**box(-3, 15, 17.4, 19, 27, 18, "shelld"), "only": ("north", "west", "east", "up", "down")},
        screen(-2, 16, 18, 26, 17.1, screen_name, [0, 0, 16, 8]),
        # Deux petits écrans, tournés vers l'opérateur.
        turned(box(-12.5, 16, 17.4, -3, 26, 18, "shelld"), -22.5, left),
        screen(-11.75, 17, -3.75, 25, 17.1, screen_name, [0, 8, 8, 16], -22.5, left),
        turned(box(19, 16, 17.4, 28.5, 26, 18, "shelld"), 22.5, right),
        screen(19.75, 17, 27.75, 25, 17.1, screen_name, [8, 8, 16, 16], 22.5, right),
        # Grille de refroidissement à l'arrière.
        {**box(-10, 16, 31, 26, 26, 31.4, "vent"), "only": ("south", "west", "east", "up", "down")},
    ]


DESIGN = shell("trim_blue", "screen_design") + [
    # Maquette de fusée sur son socle lumineux.
    faced(box(-13, 14, 3, -5, 14.8, 11, "shelld"), up="holo"),
    box(-10, 14.8, 6, -8, 20, 8, "white"), box(-9.5, 20, 6.5, -8.5, 21.5, 7.5, "red"),
    box(-11, 14.8, 6.7, -10, 16.2, 7.3, "red"), box(-8, 14.8, 6.7, -7, 16.2, 7.3, "red"),
    # Table traçante, et le plan qui en sort.
    box(19, 14, 3, 30, 16.5, 10, "panel"),
    box(19, 16.5, 8, 30, 17.5, 10, "shelld"),
    box(20.5, 16.5, 2, 28.5, 16.8, 8, "paper"),
    # Rouleaux de plans sur le toit.
    box(-10, 29, 21, 8, 31, 23, "paper"), box(-6, 29, 24, 12, 31, 26, "paper"),
    box(-10.4, 29.2, 21.2, -10, 30.8, 22.8, "trim_blue"), box(12, 29.2, 24.2, 12.4, 30.8, 25.8, "trim_blue"),
]

LAUNCH = shell("trim_red", "screen_launch") + [
    # Rangée de leviers.
    box(-13, 14, 3, -4, 14.5, 9, "shelld"),
] + [part for k in range(4) for part in (
    box(-12 + 2.2 * k, 14.5, 5.6, -11.2 + 2.2 * k, 16.5, 6.4, "light"),
    box(-12.2 + 2.2 * k, 16.5, 5.4, -11 + 2.2 * k, 17.5, 6.6, "red"),
)] + [
    # Bouton de mise à feu, sur son embase rayée, et la clé d'armement.
    box(20, 14, 3, 29, 15.5, 10, "hazard"),
    box(22.5, 15.5, 4.5, 26.5, 17, 8.5, "red"),
    box(27.2, 15.5, 8, 28.2, 16.5, 9, "brass"),
    # Gyrophare.
    box(5.5, 29, 21.5, 10.5, 30, 26.5, "shelld"),
    box(6.25, 30, 22.25, 9.75, 32, 25.75, "amber"),
]

TRACKING = shell("trim_green", "screen_track") + [
    # Manche de pointage.
    box(-12, 14, 4, -6, 15, 9, "shelld"),
    box(-9.5, 15, 6, -8.5, 18, 7, "light"), box(-10, 18, 5.5, -8, 19.5, 7.5, "trim_green"),
    # Récepteur : un coffret à deux boutons et un voyant.
    box(19, 14, 3, 30, 17, 10, "panel"),
    box(20.5, 17, 4.5, 22.5, 18, 6.5, "shelld"), box(24, 17, 4.5, 26, 18, 6.5, "shelld"),
    box(27.5, 17, 5, 28.5, 17.6, 6, "led"),
    # Parabole inclinée, sur son pied, et antenne fouet.
    box(7, 29, 23, 9, 30, 25, "shelld"),
    {**box(1, 30.2, 19.5, 15, 31, 28.5, "dish"), "stretch": True, "rotation": {"origin": [8, 30.5, 24], "axis": "x", "angle": 22.5}},
    {**box(7.4, 31, 23.4, 8.6, 32, 24.6, "shelld"), "stretch": True, "rotation": {"origin": [8, 30.5, 24], "axis": "x", "angle": 22.5}},
    box(26, 29, 27, 26.6, 32, 27.6, "light"),
]

# Pupitre d'essai : le petit frère du pupitre de lancement, d'un seul bloc. Le même rouge, un seul écran, le clavier,
# le bouton de mise à feu sous son capot rayé et la clé d'armement, et une petite lampe orange sur le caisson.
TEST = [
    # Socle et caisson, avec leur liseré rouge et une grille d'aération en façade comme au dos.
    box(0, 0, 1, 16, 1, 16, "shelld"),
    {**box(0, 0.25, 0.6, 16, 0.75, 1, "trim_red"), "only": ("north", "west", "east", "up", "down")},
    box(1, 1, 3, 15, 10, 15, "shell"),
    {**box(3, 3, 2.6, 13, 8, 3, "vent"), "only": ("north", "west", "east", "up", "down")},
    {**box(3, 3, 15, 13, 8, 15.4, "vent"), "only": ("south", "west", "east", "up", "down")},
    # Plan de travail, son chant rouge, le clavier.
    box(0, 10, 1, 16, 11, 16, "desk"),
    {**box(0, 10.25, 0.6, 16, 10.75, 1, "trim_red"), "only": ("north", "west", "east", "up", "down")},
    {**faced(box(1.5, 11, 2.5, 8, 11.5, 7.5, "shelld"), up="keys"), "stretch": True,
     "uv": {"up": [0, 0, 16, 16], "north": [0, 0, 13, 1], "south": [0, 0, 13, 1], "west": [0, 0, 5, 1], "east": [0, 0, 5, 1], "down": [0, 0, 13, 5]}},
    # Bouton de mise à feu sur son embase rayée, et la clé d'armement.
    box(10, 11, 2.5, 14.5, 12, 7, "hazard"),
    box(11.25, 12, 3.75, 13.25, 13, 5.75, "red"),
    box(8.75, 11, 5, 9.5, 11.75, 5.75, "brass"),
    # L'écran, dans son cadre, sur le caisson arrière.
    box(1, 11, 9, 15, 16, 15, "shell"),
    {**box(2, 11.5, 8.6, 14, 15.75, 9, "shelld"), "only": ("north", "west", "east", "up", "down")},
    screen(2.5, 12, 13.5, 15.25, 8.3, "screen_launch", [0, 8, 8, 16]),
    # Lampe orange.
    box(11.5, 16, 11, 14.5, 16.5, 14, "shelld"),
    box(12, 16.5, 11.5, 14, 18, 13.5, "amber"),
]

CONSOLES = {"design_computer": DESIGN, "launch_console": LAUNCH, "tracking_station": TRACKING}
# Les postes d'un seul bloc, et le pavé qu'ils occupent (pour leur présentation dans l'inventaire).
SMALL = {"test_console": (TEST, [0, 0, 0], [16, 18, 16])}


def write_models():
    os.makedirs(gm.MODEL_DIR, exist_ok=True)
    overlaps = 0
    models = {name: (parts, [-16, 0, 0], [32, 32, 32], 1.25) for name, parts in CONSOLES.items()}
    models.update({name: (parts, lo, hi, 1.0) for name, (parts, lo, hi) in SMALL.items()})
    for name, (parts, lo, hi, fill) in models.items():
        for part in parts:
            assert all(-16 <= c <= 32 for c in part["from"] + part["to"]), (name, part)
            assert all(a < b for a, b in zip(part["from"], part["to"])), (name, part)
        overlaps += check_overlaps(name, parts)
        model = build_tiled(parts, TEXTURES, EMISSIVE, display=display(lo, hi, fill=fill))
        with open(os.path.join(gm.MODEL_DIR, name + ".json"), "w", newline="\n") as f:
            json.dump(model, f, indent=1)
    return overlaps


if __name__ == "__main__":
    write_textures()
    found = write_models()
    print("%d postes de travail écrits, %d chevauchement(s)." % (len(CONSOLES) + len(SMALL), found))
