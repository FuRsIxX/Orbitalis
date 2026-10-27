#!/usr/bin/env python3
"""
Génère tout ce qui fait l'apparence du pas de tir d'Orbitalis :

  1. ses matériaux (textures 16 x 16) : bétons, aciers, caillebotis, isolants, conduites, signalétique, feux ;
  2. son kit de pièces 3D : table, déflecteur, carneau, déluge, blocs de la tour, portique d'embarquement,
     ascenseur, passerelle, mâts, bras, vérins ;
  3. les grandes images du tablier assemblé, une par taille, de 3x3 à 11x11.

Les cinq tailles de pas de tir sont bâties avec les mêmes pièces : seul leur nombre change. Le rendu
(LaunchTableRenderer) assemble le tablier, les mâts, les bras et les passerelles. La tour, elle, est faite de
vrais blocs pleins, que le joueur pose un à un : leurs modèles sont écrits ici aussi.

Repères des pièces, en pixels (16 par bloc) :
  - tour     : x vers la fusée, y vers le haut ;
  - bras     : il part de sa charnière (x = 0) vers la fusée ; son treillis occupe z = 3 ... 13 ;
  - cabine   : x de l'arrière de la tour vers la fusée (deux blocs), z sur un bloc, y sur deux blocs ;
  - carneau  : x dans le sens d'évacuation des gaz ;
  - mât      : le projecteur regarde vers z < 0, le rendu le tourne vers le centre du pas de tir.

Usage : python tools/generate_launch_pad.py [apercu_textures.png [apercu_modeles.png [apercu_tablier.png]]]
"""
import json
import math
import os
import random
import sys

import generate_machine_models as gm
from model_kit import build_tiled, check_overlaps, display, separate
from generate_machine_models import blend, box, brace, plain, shade, write_texture
from generate_textures import ITEM_DIR, ROOT, SIZE, TRANSPARENT, blotchy, preview, rgb, write_png

ENTITY_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "orbitalis", "textures", "entity")


# ---------------------------------------------------------------------------
# Outils de dessin
# ---------------------------------------------------------------------------
def mix(a, b, t):
    """Mélange de deux couleurs (0 : la première, 1 : la seconde), opaque."""
    return blend(a, b, t)


def fill(color):
    return [[color for _ in range(SIZE)] for _ in range(SIZE)]


def transposed(img):
    """La même texture couchée : les anneaux d'une conduite verticale deviennent ceux d'une conduite horizontale."""
    return [[img[x][y] for x in range(SIZE)] for y in range(SIZE)]


# Petite police de 3 x 5 pixels, pour la signalétique.
FONT = {
    "0": ["###", "# #", "# #", "# #", "###"], "1": [" # ", "## ", " # ", " # ", "###"],
    "2": ["###", "  #", "###", "#  ", "###"], "3": ["###", "  #", " ##", "  #", "###"],
    "4": ["# #", "# #", "###", "  #", "  #"], "5": ["###", "#  ", "###", "  #", "###"],
    "L": ["#  ", "#  ", "#  ", "#  ", "###"], "O": ["###", "# #", "# #", "# #", "###"],
    "X": ["# #", "# #", " # ", "# #", "# #"], "R": ["## ", "# #", "## ", "# #", "# #"],
    "P": ["###", "# #", "###", "#  ", "#  "], "H": ["# #", "# #", "###", "# #", "# #"],
    "C": ["###", "#  ", "#  ", "#  ", "###"], "E": ["###", "#  ", "## ", "#  ", "###"],
}


def text(img, x, y, word, color, scale=1):
    """Écrit un mot avec la petite police, coin haut gauche en (x, y)."""
    for index, letter in enumerate(word):
        for row, line in enumerate(FONT[letter]):
            for col, mark in enumerate(line):
                if mark == "#":
                    for sy in range(scale):
                        for sx in range(scale):
                            px, py = x + (index * 4 + col) * scale + sx, y + row * scale + sy
                            if 0 <= py < len(img) and 0 <= px < len(img[0]):
                                img[py][px] = color


# ---------------------------------------------------------------------------
# Matériaux
# ---------------------------------------------------------------------------
CONCRETE = ["#A3A29C", "#9C9B95", "#A9A8A2", "#97968F"]
SOOT = rgb("#2B2723")


def concrete(seed):
    """Béton de structure : coulé en dalles, avec son joint. Volontairement sobre."""
    img = blotchy(seed, [rgb(c) for c in CONCRETE], [6, 4, 3, 1], passes=3)
    for i in range(SIZE):
        img[0][i] = img[i][0] = rgb("#B1B0AA")
        img[SIZE - 1][i] = img[i][SIZE - 1] = rgb("#7C7B75")
    return img


def concrete_tech():
    """Béton technique : lisse, plus sombre et bleuté, en panneaux préfabriqués à joint creux."""
    img = plain(202, "#8A8D8F", "#979A9C", "#7B7E80", [15])
    for y in range(SIZE):
        img[y][0] = shade(img[y][0], 0.78)
        img[y][1] = shade(img[y][1], 1.08)
    return img


def concrete_burnt():
    """Béton brûlé : le même, noirci par les flammes, craquelé, avec des coulures de suie."""
    img = concrete(203)
    rnd = random.Random(203)
    for y in range(SIZE):
        for x in range(SIZE):
            img[y][x] = mix(img[y][x], SOOT, 0.45 + rnd.uniform(-0.15, 0.2))
    for _ in range(3):
        x, y = rnd.randint(2, 13), rnd.randint(1, 6)
        for _ in range(rnd.randint(4, 8)):
            if 0 <= x < SIZE and y < SIZE:
                img[y][x] = rgb("#1C1916")
            y += 1
            x += rnd.choice((-1, 0, 1))
    return img


def refractory():
    """Béton réfractaire : briques ocre posées à joints décalés, encrassées de suie vers le bas."""
    rnd = random.Random(204)
    bricks = [rgb("#B08A62"), rgb("#A47E58"), rgb("#BC966C"), rgb("#96724E")]
    img = fill(rgb("#6A5644"))
    for row in range(4):
        offset = 0 if row % 2 == 0 else 4
        for col in range(-1, 3):
            color = rnd.choice(bricks)
            for y in range(row * 4 + 1, row * 4 + 4):
                for x in range(col * 8 + offset + 1, col * 8 + offset + 8):
                    if 0 <= x < SIZE:
                        soot = 0.12 + 0.3 * (y / 15.0) + rnd.uniform(-0.08, 0.08)
                        img[y][x] = mix(shade(color, 1.06 if y % 4 == 1 else 1.0), SOOT, soot)
    return img


def steel(seed, base, light, dark, lines):
    """Acier de charpente : tôle brossée, à joints creusés."""
    return plain(seed, base, light, dark, lines)


def steel_red():
    """Acier peint, rouge de sécurité."""
    return steel(205, "#B13A22", "#BA432B", "#A5341E", [15])


def stainless():
    """Acier inoxydable : très clair, avec deux cordons de soudure."""
    return plain(207, "#C9D0D6", "#D6DCE1", "#BBC3CA", [5, 11])


def thermal():
    """Métal thermique : acier sombre, à peine bleui par la chaleur, en plaques boulonnées."""
    img = plain(208, "#3B3E44", "#454A52", "#32353A", [7])
    for y in range(SIZE):
        img[y][8] = shade(img[y][8], 0.7)
        for x in range(SIZE):
            if (x // 8 + y // 8) % 2 == 0:
                img[y][x] = mix(img[y][x], rgb("#3B4C66"), 0.18)
    for x, y in ((2, 2), (13, 2), (2, 12), (13, 12)):
        img[y][x] = rgb("#7A838E")
    return img


def cabin_glass():
    """Vitrage de la cabine d'ascenseur : presque tout transparent, un cadre fin et deux reflets en biais."""
    frame, glint = rgb("#C9D6E2"), rgb("#EAF3FA")
    img = [[TRANSPARENT for _ in range(SIZE)] for _ in range(SIZE)]
    for i in range(SIZE):
        img[0][i] = img[SIZE - 1][i] = img[i][0] = img[i][SIZE - 1] = frame
    for k in range(4):
        img[4 + k][6 - k] = glint
        img[8 + k][12 - k] = glint
    return img


def grating():
    """Caillebotis : des barreaux croisés ; entre eux, on voit au travers."""
    img = [[TRANSPARENT for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            if x % 4 == 0 or y % 4 == 0:
                img[y][x] = rgb("#5A626B")
            if x % 4 == 0 and y % 4 == 0:
                img[y][x] = rgb("#8C949E")
            if x % 4 == 1 and y % 4 == 0:
                img[y][x] = rgb("#6B737D")
    return img


def tread():
    """Tôle larmée : de petites larmes en relief, tournées tantôt dans un sens, tantôt dans l'autre."""
    img = plain(210, "#6B737D", "#757D87", "#5F676F")
    for y in range(SIZE):
        for x in range(SIZE):
            cell = (x // 4 + y // 4) % 2
            lx, ly = x % 4, y % 4
            if (cell == 0 and lx == ly and lx in (1, 2)) or (cell == 1 and lx + ly == 3 and lx in (1, 2)):
                img[y][x] = rgb("#858D97")
    return img


def rubber():
    """Caoutchouc : noir mat, à cannelures (les plis d'un soufflet, les spires d'un flexible)."""
    return [[rgb(("#17181B", "#232529", "#2E3035", "#232529")[x % 4]) for x in range(SIZE)] for _ in range(SIZE)]


# Relief d'une conduite, colonne après colonne : un reflet, la teinte, une ombre, un creux. Le motif se répète tous
# les quatre pixels : une conduite large de deux à quatre pixels en montre une période, quelle que soit sa place.
PIPE_RELIEF = (0.9, 1.16, 1.0, 0.7)


def pipe(seed, base, rings, speckle=None, quilt=None):
    """
    Conduite verticale, en aplats francs : pas de dégradé, quatre teintes nettes d'un pixel de large. Elle est
    appliquée pixel pour pixel sur sa pièce (voir `piped`), et non étirée.

    rings   : {rangée: couleur} des colliers et des joints
    speckle : couleur de mouchetures (givre) ou None
    quilt   : couleur d'un matelassage en losanges (isolant multicouche) ou None
    """
    rnd = random.Random(seed)
    b = rgb(base)
    img = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            color = shade(b, PIPE_RELIEF[x % 4])
            if quilt and (x + y) % 4 == 0:
                color = mix(color, rgb(quilt), 0.55)
            if speckle and rnd.random() < 0.08:
                color = mix(color, rgb(speckle), 0.85)
            row.append(color)
        img.append(row)
    for y, color in rings.items():
        for x in range(SIZE):
            img[y][x] = shade(rgb(color), PIPE_RELIEF[x % 4])
    return img


def cables():
    """
    Chemin de câbles, vu de dessus : une goulotte en tôle grise, et dedans quatre câbles gainés (noir, bleu, noir,
    orange), chacun large d'un pixel entre deux pixels de tôle, tenus par des colliers clairs. Le motif se répète
    tous les quatre pixels en largeur : une pièce de deux ou quatre pixels en montre un nombre entier de câbles.
    """
    tray, shadow = rgb("#6E7782"), rgb("#4A5058")
    sheaths = (rgb("#16181C"), rgb("#2F6FB5"), rgb("#16181C"), rgb("#D9822B"))
    img = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            if x % 2 == 0:
                row.append(sheaths[(x // 2) % 4])
            else:
                row.append(tray if y % 8 not in (3, 4) else shadow)
        if y % 8 == 3:
            row = [rgb("#C9D0D8") for _ in range(SIZE)]
        img.append(row)
    return img


def hazard():
    """Bande de sécurité : rayures jaunes et noires en biais, un peu passées."""
    rnd = random.Random(213)
    img = []
    for y in range(SIZE):
        img.append([shade(rgb("#F2C14E") if (x + y) // 4 % 2 == 0 else rgb("#22252A"), rnd.uniform(0.92, 1.04))
                    for x in range(SIZE)])
    return img


def white_panel():
    """Panneau peint en blanc, percé d'un bandeau de hublots : la passerelle d'accès de l'équipage."""
    img = plain(214, "#E6E9EC", "#F5F6F7", "#CFD4D9", [15])
    for x in range(SIZE):
        for y in (6, 7, 8):
            img[y][x] = rgb("#35506E") if x % 8 not in (0, 7) else rgb("#9AA2AB")
        img[5][x] = img[9][x] = rgb("#9AA2AB")
    for x in range(1, SIZE, 8):
        img[6][x] = rgb("#8FB7DD")
    return img


def lamp(lit):
    """Optique d'un projecteur : une dalle de quatre lampes derrière sa grille."""
    glass, core = (rgb("#FFE9A6"), rgb("#FFFBEA")) if lit else (rgb("#565A5E"), rgb("#70757A"))
    img = fill(rgb("#2A2D31"))
    for y in range(1, SIZE - 1):
        for x in range(1, SIZE - 1):
            if x % 7 != 0 and y % 7 != 0:
                img[y][x] = core if (x % 7 in (3, 4) and y % 7 in (3, 4)) else glass
    return img


def amber_frame(step, frames=6):
    """Feu orange à éclats : le reflet de son miroir tournant passe d'un bord à l'autre."""
    img = []
    bright = step * SIZE / frames
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            distance = min(abs(x - bright), abs(x - bright + SIZE), abs(x - bright - SIZE))
            row.append(mix(rgb("#FF9D1E"), rgb("#FFF3C4"), max(0.0, 1.0 - distance / 4.0)))
        img.append(row)
    return img


def wheel():
    """Volant de vanne, vu de face : une jante rouge, quatre rayons et le moyeu ; le reste est à jour."""
    img = [[TRANSPARENT for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            distance = math.hypot(x - 7.5, y - 7.5)
            if 5.6 <= distance <= 7.8:
                img[y][x] = rgb("#D9342B") if x + y < 17 else rgb("#A82219")
            elif distance < 2.2:
                img[y][x] = rgb("#3A3D42")
            elif abs(x - 7.5) < 1.0 or abs(y - 7.5) < 1.0:
                img[y][x] = rgb("#C22B22")
    return img


def sign(background, border, draw):
    """Panneau : un fond, un liseré, et un dessin."""
    img = fill(rgb(background))
    for i in range(SIZE):
        img[0][i] = img[SIZE - 1][i] = img[i][0] = img[i][SIZE - 1] = rgb(border)
    draw(img)
    return img


def draw_warning(img):
    """Triangle noir à point d'exclamation : danger."""
    black, yellow = rgb("#1B1C1F"), rgb("#F2C14E")
    for y in range(3, 14):
        half = (y - 3) * 0.6
        for x in range(SIZE):
            if abs(x - 7.5) <= half + 0.5:
                edge = abs(x - 7.5) > half - 0.9 or y == 13
                img[y][x] = black if edge else yellow
    for y in (7, 8, 9, 11):
        img[y][7] = img[y][8] = black


def draw_no_entry(img):
    """Disque rouge barré de blanc : accès réservé au personnel autorisé."""
    for y in range(SIZE):
        for x in range(SIZE):
            if math.hypot(x - 7.5, y - 7.5) <= 6.2:
                img[y][x] = rgb("#F4F5F6") if 6 <= y <= 9 and 4 <= x <= 11 else rgb("#D9342B")


def draw_arrow(img):
    """Flèche blanche : chemin d'évacuation."""
    white = rgb("#F4F5F6")
    for x in range(3, 11):
        img[7][x] = img[8][x] = white
    for k in range(5):
        img[7 - k][12 - k] = img[8 + k][12 - k] = white
        img[7 - k][11 - k] = img[8 + k][11 - k] = white


def draw_wrench(img):
    """Clé plate blanche : accès de maintenance."""
    white = rgb("#F4F5F6")
    for k in range(3, 12):
        img[15 - k][k] = img[14 - k][k] = white
    for x, y in ((11, 2), (13, 4), (12, 2), (13, 3), (2, 11), (4, 13), (2, 12), (3, 13)):
        img[y][x] = white


def draw_orbit(img):
    """Marque d'Orbitalis : une planète, l'anneau incliné de son orbite, et un satellite."""
    white, gold = rgb("#F4F5F6"), rgb("#F2C14E")
    for y in range(SIZE):
        for x in range(SIZE):
            if math.hypot(x - 7.5, y - 7.5) <= 3.1:
                img[y][x] = white
    tilt = math.radians(-24)
    for step in range(96):
        angle = step * math.pi / 48
        ex, ey = math.cos(angle) * 6.4, math.sin(angle) * 2.0
        x = 7.5 + ex * math.cos(tilt) - ey * math.sin(tilt)
        y = 7.5 + ex * math.sin(tilt) + ey * math.cos(tilt)
        px, py = int(round(x)), int(round(y))
        behind = math.sin(angle) < 0 and math.hypot(px - 7.5, py - 7.5) <= 3.6
        if not behind and 1 <= px <= 14 and 1 <= py <= 14:
            img[py][px] = gold
    img[3][12] = img[3][13] = img[2][13] = white


def fluid_sign(background, word, accent):
    """Étiquette d'ergol : son nom, et une bande de couleur qui répète celle de la conduite."""
    def draw(img):
        text(img, 3, 3, word, rgb("#F4F5F6"))
        for x in range(2, 14):
            img[10][x] = img[11][x] = rgb(accent)
        img[12][3] = img[12][12] = rgb(accent)
    return sign(background, "#F4F5F6", draw)


def casing():
    """
    Carter : une tôle épaisse gris pierre, cernée d'un bourrelet sombre et d'un filet clair, rivetée aux quatre
    angles. Elle habille les blocs techniques de la tour (appuis, ravitaillement, socles).
    """
    img = blotchy(230, [rgb("#8E9296"), rgb("#868A8E"), rgb("#969A9E")], [5, 3, 2], passes=3)
    for i in range(SIZE):
        for edge, color in ((0, "#55595E"), (SIZE - 1, "#4A4E53")):
            img[edge][i] = img[i][edge] = rgb(color)
    for i in range(1, SIZE - 1):
        img[1][i] = img[i][1] = rgb("#B4B8BC")
        img[SIZE - 2][i] = img[i][SIZE - 2] = rgb("#6C7075")
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        img[y][x] = rgb("#C9CDD1")
        img[y + 1][x] = rgb("#5E6267")
    return img


def brass():
    """Laiton : doré, chaud, à peine strié, avec un joint creusé."""
    return plain(231, "#C9A24A", "#DDB95F", "#A9853A", [15])


def blueprint_item():
    """Kit de pas de tir, en objet : un plan bleu roulé à moitié, où se dessinent le tablier, la table et la tour."""
    img = [[TRANSPARENT for _ in range(SIZE)] for _ in range(SIZE)]
    paper, line, dark = rgb("#2F6FB5"), rgb("#CFE3F7"), rgb("#1B4577")
    for y in range(2, 14):
        for x in range(1, 13):
            img[y][x] = paper if (x + y) % 5 else rgb("#3A7CC4")
    for i in range(1, 13):
        img[2][i] = img[13][i] = dark
    for y in range(2, 14):
        img[y][1] = dark
    # Le tablier, la table au centre, le carneau et la tour.
    for i in range(4, 10):
        img[5][i] = img[10][i] = img[i + 1][4] = img[i + 1][9] = line
    img[7][6] = img[7][7] = img[8][6] = img[8][7] = rgb("#F2C14E")
    img[7][8] = img[8][8] = line
    img[7][2] = img[8][2] = img[7][3] = img[8][3] = rgb("#E0655F")
    # Le bord roulé, à droite.
    for y in range(1, 15):
        img[y][13] = rgb("#E6EEF6")
        img[y][14] = rgb("#B9C6D2")
    img[1][13] = img[14][13] = rgb("#8FA2B4")
    return img


def launch_pad_slab(look):
    """Dalle de pas de tir, telle qu'on la pose : béton technique, cornière d'acier et quatre ancrages."""
    img = concrete(220)
    for i in range(SIZE):
        img[0][i] = img[i][0] = rgb("#9DA6B1")
        img[SIZE - 1][i] = img[i][SIZE - 1] = rgb("#5A626B")
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        img[y][x] = rgb("#3A3D42")
    if look == "border":
        pattern = hazard()
        for y in range(SIZE):
            for x in range(SIZE):
                if min(x, y, SIZE - 1 - x, SIZE - 1 - y) < 3:
                    img[y][x] = pattern[y][x]
    elif look == "border_side":
        pattern = hazard()
        for y in range(4):
            img[y] = pattern[y][:]
    elif look == "inner":
        burnt = concrete_burnt()
        img = [[mix(img[y][x], burnt[y][x], 0.5) for x in range(SIZE)] for y in range(SIZE)]
    return img


def write_materials():
    """Écrit toutes les textures du pas de tir et renvoie celles à montrer sur la planche d'aperçu."""
    sheet = []

    def add(name, frames, frametime=None, interpolate=True):
        write_texture(name, frames, frametime, interpolate)
        sheet.append(frames[0])

    add("pad_concrete", [concrete(201)])
    add("pad_concrete_tech", [concrete_tech()])
    add("pad_concrete_burnt", [concrete_burnt()])
    add("pad_refractory", [refractory()])
    add("pad_steel", [steel(206, "#8C949E", "#959DA7", "#838B95", [15])])
    add("pad_steel_red", [steel_red()])
    add("pad_steel_grey", [steel(209, "#565D66", "#5E656E", "#4E555D", [15])])
    add("pad_stainless", [stainless()])
    add("pad_thermal", [thermal()])
    add("pad_grating", [grating()])
    add("pad_cabin_glass", [cabin_glass()])
    add("pad_tread", [tread()])
    add("pad_rubber", [rubber()])
    add("pad_white", [white_panel()])
    add("pad_hazard", [hazard()])
    add("pad_casing", [casing()])
    add("pad_brass", [brass()])
    add("pad_cable", [cables()])
    add("pad_cable_h", [transposed(cables())])

    # Conduites, chacune dans ses deux sens. L'oxygène liquide est calorifugé de blanc et givré ; l'hydrogène
    # liquide porte un isolant doré matelassé ; le kérosène est une simple conduite peinte en ocre, baguée de
    # rouge ; l'eau du déluge est bleue.
    pipes = {
        "pad_lox": pipe(215, "#EEF1F4", {3: "#B9C6D2", 11: "#B9C6D2", 15: "#8FA2B4"}, speckle="#FFFFFF"),
        "pad_lh2": pipe(216, "#D9B24A", {15: "#7A5E18", 7: "#F4E2A0"}, quilt="#A8842A"),
        "pad_rp1": pipe(217, "#B5762E", {6: "#C22B22", 7: "#C22B22", 15: "#5E3D14"}),
        "pad_water": pipe(218, "#2F6FB5", {7: "#E6EEF6", 15: "#1B4577"}),
        "pad_ch4": pipe(219, "#8FC7AE", {3: "#2E6B5E", 11: "#2E6B5E", 15: "#4E8A74"}, speckle="#D8F2E6"),
        "pad_he3": pipe(220, "#E8DC9A", {7: "#7A5BB0", 15: "#9C8A4A"}),
    }
    for name, img in pipes.items():
        add(name, [img])
        add(name + "_h", [transposed(img)])

    # Feux : projecteurs, balise rouge, feu orange à éclats, voyants de la table.
    add("pad_lamp", [lamp(True)])
    add("pad_lamp_off", [lamp(False)])
    # Balise à LED : un aplat net, sans grain, allumée comme éteinte.
    add("beacon_red", [[[rgb("#FF4A3D")] * SIZE for _ in range(SIZE)]])
    add("pad_beacon_off", [[[rgb("#6A1A15")] * SIZE for _ in range(SIZE)]])
    add("pad_amber", [amber_frame(s) for s in range(6)], 2)
    add("pad_amber_off", [plain(222, "#6E4A10", "#835A18", "#55380A")])
    add("pad_led", [plain(223, "#7CE08A", "#B4F5BD", "#57C768")])
    add("pad_led_off", [plain(224, "#24402A", "#2E5036", "#1A3020")])
    add("pad_wheel", [wheel()])

    # Peintures des mâts.
    add("mast_white", [plain(81, "#E9ECEF", "#F7F8F9", "#D3D8DD", [15])])
    add("mast_red", [plain(82, "#C8321E", "#DA4630", "#A82614", [15])])
    add("mast_insulator", [plain(83, "#F1F5F8", "#FFFFFF", "#DCE4EA")])

    # Signalétique d'Orbitalis.
    add("pad_sign_warning", [sign("#F2C14E", "#1B1C1F", draw_warning)])
    add("pad_sign_no_entry", [sign("#F4F5F6", "#D9342B", draw_no_entry)])
    add("pad_sign_exit", [sign("#2E8B57", "#F4F5F6", draw_arrow)])
    add("pad_sign_maintenance", [sign("#2F6FB5", "#F4F5F6", draw_wrench)])
    add("pad_sign_orbitalis", [sign("#1B2A4A", "#F2C14E", draw_orbit)])
    add("pad_sign_lox", [fluid_sign("#2F6FB5", "LOX", "#EEF1F4")])
    add("pad_sign_lh2", [fluid_sign("#B13A22", "LH2", "#D9B24A")])
    add("pad_sign_rp1", [fluid_sign("#5E3D14", "RP1", "#C22B22")])
    add("pad_sign_water", [fluid_sign("#1B4577", "H2O", "#6FB4EE")])
    add("pad_sign_ch4", [fluid_sign("#2E6B5E", "CH4", "#8FC7AE")])
    add("pad_sign_he3", [fluid_sign("#4A3A78", "HE3", "#E8DC9A")])

    # Dalle posée seule, et ses aspects une fois prise dans la structure (ils ne sont alors plus dessinés).
    add("launch_pad", [launch_pad_slab("plain")])
    for look in ("inner", "border", "border_side"):
        write_texture("launch_pad_" + look, [launch_pad_slab(look)])
    write_texture("launch_pad_trench", [launch_pad_slab("inner")])
    write_png(os.path.join(ITEM_DIR, "pad_kit.png"), blueprint_item())
    return sheet


# ---------------------------------------------------------------------------
# Kit de pièces
# ---------------------------------------------------------------------------
# Nom court d'une texture dans les modèles, et la texture qu'il désigne.
PAD_TEXTURES = {
    "concrete": "pad_concrete", "tech": "pad_concrete_tech", "burnt": "pad_concrete_burnt", "brick": "pad_refractory",
    "steel": "pad_steel", "red": "pad_steel_red", "grey": "pad_steel_grey", "inox": "pad_stainless",
    "thermal": "pad_thermal", "grate": "pad_grating", "cglass": "pad_cabin_glass", "plate": "pad_tread", "rubber": "pad_rubber",
    "white": "pad_white", "hazard": "pad_hazard", "casing": "pad_casing", "brass": "pad_brass", "cable": "pad_cable", "cableh": "pad_cable_h",
    "lox": "pad_lox", "loxh": "pad_lox_h", "lh2": "pad_lh2", "lh2h": "pad_lh2_h", "rp1": "pad_rp1", "rp1h": "pad_rp1_h",
    "water": "pad_water", "waterh": "pad_water_h", "wheel": "pad_wheel",
    "lamp": "pad_lamp", "lampoff": "pad_lamp_off", "beacon": "beacon_red", "beaconoff": "pad_beacon_off",
    "amber": "pad_amber", "amberoff": "pad_amber_off", "led": "pad_led", "ledoff": "pad_led_off",
    "paint": "mast_white", "paintred": "mast_red", "insulator": "mast_insulator",
    "s_warning": "pad_sign_warning", "s_noentry": "pad_sign_no_entry", "s_exit": "pad_sign_exit",
    "s_maint": "pad_sign_maintenance", "s_orbitalis": "pad_sign_orbitalis",
    "s_lox": "pad_sign_lox", "s_lh2": "pad_sign_lh2", "s_rp1": "pad_sign_rp1", "s_water": "pad_sign_water",
    "ch4": "pad_ch4", "ch4h": "pad_ch4_h", "s_ch4": "pad_sign_ch4",
    "he3": "pad_he3", "he3h": "pad_he3_h", "s_he3": "pad_sign_he3",
}
# Pièces lumineuses : elles se voient dans le noir.
EMISSIVE = {"lamp", "beacon", "amber", "led"}


def whole(part):
    """Pièce dont chaque face reçoit la texture entière (conduite, panneau, feu)."""
    return {**part, "stretch": True}


def faced(part, **faces):
    """Pièce dont certaines faces portent une autre texture que les autres."""
    return {**part, "faces": faces}


def diagonal(cx, cy, z0, z1, length, thickness, angle, texture):
    """Barre inclinée dans le plan vertical x-y, centrée en (cx, cy) : limon d'escalier, main courante, diagonale."""
    part = box(cx - length / 2, cy - thickness / 2, z0, cx + length / 2, cy + thickness / 2, z1, texture)
    part["rotation"] = {"origin": [cx, cy, (z0 + z1) / 2], "axis": "z", "angle": angle}
    part["stretch"] = True
    return part


# --- Tour de service -------------------------------------------------------
# La tour fait trois blocs de côté et se bâtit en vrais blocs pleins, ajourés : on voit au travers, mais on ne
# les traverse pas. À chaque étage, la colonne technique occupe le milieu de la face avant (côté fusée), six
# blocs de charpente l'entourent, et les deux cases du milieu, derrière elle, restent vides : c'est la gaine de
# l'ascenseur, ouverte sur l'arrière de la tour.
#
# Charpente d'un bloc : quatre montants, une ceinture basse et une diagonale par face.
def frame_brace(axis, plane, angle):
    """Croisillon d'une face de charpente, plus court que la diagonale du bloc : ses bouts restent dans les montants."""
    half = 9.6
    near = plane + (0.3 if angle > 0 else -0.3)
    if axis == "z":
        part = box(8 - half, 7.25, near - 0.25, 8 + half, 8.75, near + 0.25, "red")
        part["rotation"] = {"origin": [8, 8, plane], "axis": "z", "angle": angle}
    elif axis == "x":
        part = box(near - 0.25, 7.25, 8 - half, near + 0.25, 8.75, 8 + half, "red")
        part["rotation"] = {"origin": [plane, 8, 8], "axis": "x", "angle": angle}
    else:
        part = box(8 - half, near - 0.25, 7.25, 8 + half, near + 0.25, 8.75, "red")
        part["rotation"] = {"origin": [8, plane, 8], "axis": "y", "angle": angle}
    part["stretch"] = True
    return part


# Quatre montants, une ceinture en bas et une en haut.
FRAME = [
    box(0, 0, 0, 2, 16, 2, "red"), box(14, 0, 0, 16, 16, 2, "red"),
    box(0, 0, 14, 2, 16, 16, "red"), box(14, 0, 14, 16, 16, 16, "red"),
    box(2, 0.2, 0.4, 14, 1.4, 1.6, "red"), box(2, 0.2, 14.4, 14, 1.4, 15.6, "red"),
    box(0.4, 0.2, 2, 1.6, 1.4, 14, "red"), box(14.4, 0.2, 2, 15.6, 1.4, 14, "red"),
    box(2, 14.6, 0.4, 14, 15.8, 1.6, "red"), box(2, 14.6, 14.4, 14, 15.8, 15.6, "red"),
    box(0.4, 14.6, 2, 1.6, 15.8, 14, "red"), box(14.4, 14.6, 2, 15.6, 15.8, 14, "red"),
]
# Dessus du bloc de charpente : deux diagonales croisées, l'une sous l'autre.
FRAME_TOP = [frame_brace("y", 15.2, 45), frame_brace("y", 15.2, -45)]
FRAME_BRACES = [frame_brace("z", 0.75, 45), frame_brace("z", 15.25, -45),
                frame_brace("x", 0.75, -45), frame_brace("x", 15.25, 45)]


def piped(part):
    """
    Conduite ou câble : sa texture est appliquée pixel pour pixel, depuis son coin, au lieu d'être étirée sur la
    pièce. Une conduite de trois pixels de large montre trois pixels de texture.
    """
    (x0, y0, z0), (x1, y1, z1) = part["from"], part["to"]
    dx, dy, dz = min(16, x1 - x0), min(16, y1 - y0), min(16, z1 - z0)
    return {**part, "uv": {"north": [0, 0, dx, dy], "south": [0, 0, dx, dy], "west": [0, 0, dz, dy], "east": [0, 0, dz, dy],
                           "up": [0, 0, dx, dz], "down": [0, 0, dx, dz]}}


# Colonne technique : la même charpente, traversée par les colonnes montantes (oxygène liquide, hydrogène liquide,
# kérosène) et le chemin de câbles. Sa face avant porte la platine où se branche un bras ombilical.
TOWER_RISERS = [
    piped(box(3, 0, 3, 6, 16, 6, "lox")),
    piped(box(3, 0, 7, 7, 16, 10, "lh2")),
    piped(box(3, 0, 11, 5, 16, 13, "rp1")),
    piped(box(8, 0, 11, 12, 16, 13, "cable")),
    box(2.4, 8, 2.6, 2.9, 9.2, 13.6, "steel"),
    box(13.6, 5, 4.5, 14.6, 11, 11.5, "grey"),
    piped(box(14.6, 6, 5, 15.4, 9, 7, "loxh")), piped(box(14.6, 6, 9, 15.4, 9, 11, "rp1h")),
]


def gate_lower(closed=False):
    """
    Portique d'embarquement, ses deux premiers niveaux : trois blocs de large, le passage au milieu. Un seuil en tôle
    larmée bordé de bandes rayées, deux montants habillés d'un panneau gris et d'une main courante en laiton. Le
    passage va le long de l'axe x ; rien ne dépasse du volume du portique.
    """
    parts = [
        box(1.5, 0, -13, 14.5, 1, 29, "plate"),
        box(0, 0, -13, 1.5, 1, 29, "hazard"), box(14.5, 0, -13, 16, 1, 29, "hazard"),
        box(0, 0, -16, 16, 32, -13, "red"), box(0, 0, 29, 16, 32, 32, "red"),
        box(2, 1, -13, 14, 32, -12.4, "grey"), box(2, 1, 28.4, 14, 32, 29, "grey"),
        box(1.5, 15, -12.4, 14.5, 16, -11.8, "brass"), box(1.5, 15, 27.8, 14.5, 16, 28.4, "brass"),
    ]
    if closed:
        # Porte palière : un panneau grillagé en travers du passage, et sa barre rayée.
        parts += [box(7.6, 1, -11.8, 8.4, 32, 27.8, "grate"), box(7.3, 13.5, -11.8, 8.7, 14.8, 27.8, "hazard")]
    return parts


def gate_top(closed=False):
    """Portique d'embarquement, son troisième niveau : le haut des montants, le linteau, son plafonnier et ses panneaux."""
    parts = [
        box(0, 0, -16, 16, 11, -13, "red"), box(0, 0, 29, 16, 11, 32, "red"),
        box(2, 0, -13, 14, 11, -12.4, "grey"), box(2, 0, 28.4, 14, 11, 29, "grey"),
        box(0.4, 11, -13, 15.6, 16, 29, "red"), box(0, 11, -16, 16, 16, -13, "red"), box(0, 11, 29, 16, 16, 32, "red"),
        whole(box(4, 10.6, -4, 12, 11, 20, "lamp")),
        whole(box(15.6, 11.8, 3, 15.9, 15.2, 13, "s_orbitalis")), whole(box(0.1, 11.8, 3, 0.4, 15.2, 13, "s_exit")),
    ]
    if closed:
        parts += [box(7.6, 0, -11.8, 8.4, 6, 27.8, "grate")]
    return parts


# Socle de l'ascenseur : la plaque que l'on pose au centre de la tour. C'est elle qui fait venir la cabine.
# Elle est plus basse que le plancher de la cabine, qui la recouvre : rien n'en dépasse quand la cabine est au sol.
ELEVATOR_BASE = [
    box(0, 0, 1.5, 16, 0.5, 14.5, "plate"),
    box(0, 0, 0, 16, 0.5, 1.5, "hazard"), box(0, 0, 14.5, 16, 0.5, 16, "hazard"),
]


def mirrored(parts):
    """Les pièces, et leur reflet de l'autre côté de l'axe de la cabine (z devient 16 - z)."""
    return parts + [{**part, "from": [part["from"][0], part["from"][1], 16 - part["to"][2]],
                     "to": [part["to"][0], part["to"][1], 16 - part["from"][2]]} for part in parts]


# Cabine de l'ascenseur : elle occupe tout l'intérieur de la tour, trois blocs sur trois, et elle est la même des
# deux côtés de son axe. Une cabine panoramique : un plancher en tôle entre deux bandes sombres, souligné d'un
# liseré lumineux ; quatre montants fins ; deux flancs faits d'une allège blanche, d'une main courante et d'un grand
# vitrage ; un toit blanc à plafonnier carré ; et, à chaque bout, un bandeau au-dessus du passage, avec son voyant.
# Ouverte aux deux bouts : on y entre par l'arrière de la tour, on en sort vers le portique. Son axe est celui des x.
# Elle fait trois blocs de haut, comme le portique d'embarquement.
CABIN_HEIGHT = 46
CABIN_TALL = [
    box(-14.5, -1, -13, 30.5, 0, 29, "plate"),
    box(-14.5, -1, -16, 30.5, 0, -13, "casing"), box(-14.5, -1, 29, 30.5, 0, 32, "casing"),
    box(-16, -1, -16, -14.5, 0, 32, "hazard"), box(30.5, -1, -16, 32, 0, 32, "hazard"),
    box(-16, CABIN_HEIGHT, -16, 32, CABIN_HEIGHT + 1.5, 32, "white"),
    whole(box(0, CABIN_HEIGHT - 0.4, 0, 16, CABIN_HEIGHT, 16, "lamp")),
    # Bandeaux au-dessus des deux passages, et leur voyant.
    box(-16, CABIN_HEIGHT - 3, -14.5, -14.5, CABIN_HEIGHT, 30.5, "grey"), box(30.5, CABIN_HEIGHT - 3, -14.5, 32, CABIN_HEIGHT, 30.5, "grey"),
    whole(box(-15.6, CABIN_HEIGHT - 3.4, 2, -14.9, CABIN_HEIGHT - 3, 14, "led")),
    whole(box(30.9, CABIN_HEIGHT - 3.4, 2, 31.6, CABIN_HEIGHT - 3, 14, "led")),
] + mirrored([
    box(-16, 0, -16, -14.5, CABIN_HEIGHT, -14.5, "grey"), box(30.5, 0, -16, 32, CABIN_HEIGHT, -14.5, "grey"),
    box(-14.5, 0, -15.8, 30.5, 10, -15, "white"),
    box(-14.5, 10, -15.8, 30.5, 11, -14.4, "grey"),
    box(-14.5, 11, -15.5, 30.5, CABIN_HEIGHT, -15.2, "cglass"),
    whole(box(-14.5, 0, -15, 30.5, 0.4, -14.5, "led")),
    # Pupitre de commande : son écran et sa rangée de touches. On commande la cabine d'un clic droit sur son plancher.
    box(5, 14, -15.2, 11, 24, -14.6, "casing"),
    whole(box(5.6, 18.5, -14.6, 10.4, 23.4, -14.45, "lamp")),
    whole(box(5.6, 14.6, -14.6, 10.4, 17.9, -14.45, "s_exit")),
])


def sliced(parts, y0, y1, shift):
    """
    La tranche d'un modèle comprise entre deux hauteurs, abaissée de `shift`. Un modèle ne peut pas dépasser deux blocs
    de haut : la cabine, qui en fait trois, est dessinée en deux pièces superposées.
    """
    out = []
    for part in parts:
        lo, hi = max(part["from"][1], y0), min(part["to"][1], y1)
        if hi - lo <= 1e-6:
            continue
        piece = {**part, "from": [part["from"][0], lo - shift, part["from"][2]], "to": [part["to"][0], hi - shift, part["to"][2]]}
        # Une face créée par la coupe est à l'intérieur de la cabine : on ne la dessine pas.
        hidden = ([] if lo == part["from"][1] else ["down"]) + ([] if hi == part["to"][1] else ["up"])
        if hidden:
            piece["only"] = tuple(face for face in part.get("only", ("north", "south", "west", "east", "up", "down")) if face not in hidden)
        out.append(piece)
    return out


# Les deux premiers blocs de la cabine, et son troisième.
ELEVATOR_CABIN = sliced(CABIN_TALL, -16, 32, 0)
ELEVATOR_CABIN_TOP = sliced(CABIN_TALL, 32, 64, 32)

# Escalier de tour : quatre marches en tôle larmée à nez de laiton, à contremarches ouvertes, portées par deux
# limons en crémaillère. Il monte vers les x croissants, d'un quart de bloc par marche. Les volées se superposent
# tous les deux étages : les marches sont minces pour laisser passer la tête.
TOWER_STAIRS = [part for k in range(4) for part in (
    box(4 * k + 0.75, 4 * k + 3, 1, 4 * k + 4, 4 * k + 4, 15, "plate"),
    box(4 * k, 4 * k + 3, 1, 4 * k + 0.75, 4 * k + 4, 15, "brass"),
    # Limons : sous chaque marche, une joue de chaque côté, qui descend jusqu'à la marche précédente.
    box(4 * k, max(0, 4 * k - 1), 0, 4 * k + 4, 4 * k + 3.6, 1, "red"),
    box(4 * k, max(0, 4 * k - 1), 15, 4 * k + 4, 4 * k + 3.6, 16, "red"),
)]
# Palier : un platelage en tôle larmée au pied de sa case. Dessous, la même ossature que la dalle et la rambarde de
# tour, à plat : quatre sabots d'angle identiques, des longerons sur les quatre côtés et une croix de poutres. Elle est
# pareille de tous les côtés : rien n'y indique une direction.
TOWER_LANDING = [box(0, 0, 0, 16, 1, 16, "plate")] + [
    box(x0, -1.6, z0, x0 + 2.5, 0, z0 + 2.5, "red") for x0, z0 in ((0, 0), (13.5, 0), (0, 13.5), (13.5, 13.5))
] + [
    box(2.5, -1.2, 0, 13.5, 0, 1.5, "grey"), box(2.5, -1.2, 14.5, 13.5, 0, 16, "grey"),
    box(0, -1.2, 2.5, 1.5, 0, 13.5, "grey"), box(14.5, -1.2, 2.5, 16, 0, 13.5, "grey"),
    box(2.5, -1.6, 7, 13.5, 0, 9, "red"),
    box(7, -1.6, 2.5, 9, 0, 7, "red"), box(7, -1.6, 9, 9, 0, 13.5, "red"),
]
# Socle de la dalle et de la rambarde de tour : la tôle larmée sur quatre piliers d'angle identiques, reliés par des
# longerons sur les quatre côtés et une croix de poutres dessous. Il est pareil de tous les côtés : rien n'y indique
# une direction.
RAILING_BASE = [box(0, 6.5, 0, 16, 8, 16, "plate")] + [
    box(x0, 0, z0, x0 + 2.5, 6.5, z0 + 2.5, "red") for x0, z0 in ((0, 0), (13.5, 0), (0, 13.5), (13.5, 13.5))
] + [
    box(2.5, 4.5, 0, 13.5, 6.5, 1.5, "grey"), box(2.5, 4.5, 14.5, 13.5, 6.5, 16, "grey"),
    box(0, 4.5, 2.5, 1.5, 6.5, 13.5, "grey"), box(14.5, 4.5, 2.5, 16, 6.5, 13.5, "grey"),
    box(2.5, 4.5, 7, 13.5, 6.5, 9, "red"),
    box(7, 4.5, 2.5, 9, 6.5, 7, "red"), box(7, 4.5, 9, 9, 6.5, 13.5, "red"),
]
# Dalle de tour : une demi-dalle, pour le sommet, sur le même socle que la rambarde.
TOWER_DECK = list(RAILING_BASE)


def railing_post(low):
    """
    Le potelet central de la rambarde. « low » : une autre rambarde est posée dessus, son socle prend la place de la
    main courante ; le potelet s'arrête au bord du bloc, sans chapeau.
    """
    if low:
        return RAILING_BASE + [box(6.5, 8, 6.5, 9.5, 16, 9.5, "grey"), box(6, 8, 6, 10, 9, 10, "grey")]
    return RAILING_BASE + [box(6.5, 9, 6.5, 9.5, 20.8, 9.5, "grey"), box(6, 20.8, 6, 10, 22, 10, "brass"),
                           box(6, 8, 6, 10, 9, 10, "grey")]


def railing_side(low):
    """Les lisses vers une voisine, vers le nord (le jeu les tourne). Basse : sans la main courante de laiton."""
    parts = [box(7.6, 14, 0, 8.4, 14.8, 6.5, "grey"), box(7.6, 10.5, 0, 8.4, 11.3, 6.5, "grey")]
    if not low:
        parts.append(box(7.3, 20.8, 0, 8.7, 22, 6, "brass"))
    return parts


# Rambarde de tour : elle se raccorde à ses voisines, comme une barrière. Le poteau porte la demi-dalle et un potelet
# au centre ; chaque côté raccordé ajoute une lisse haute en laiton et une lisse basse.
TOWER_RAILING_POST = railing_post(False)
TOWER_RAILING_SIDE = railing_side(False)
# L'objet en main : un poteau et ses deux lisses, d'un bord à l'autre.
TOWER_RAILING = TOWER_RAILING_POST + [
    box(0, 20.8, 7.3, 6, 22, 8.7, "brass"), box(10, 20.8, 7.3, 16, 22, 8.7, "brass"),
    box(0, 14, 7.6, 6.5, 14.8, 8.4, "grey"), box(9.5, 14, 7.6, 16, 14.8, 8.4, "grey"),
    box(0, 10.5, 7.6, 6.5, 11.3, 8.4, "grey"), box(9.5, 10.5, 7.6, 16, 11.3, 8.4, "grey"),
]
# Appui du tablier : une semelle, un fût de béton et un chapiteau en carter. Il porte le tablier, un bloc au-dessus du sol.
PAD_SUPPORT = [
    box(2, 0, 2, 14, 2.5, 14, "tech"),
    box(4.5, 2.5, 4.5, 11.5, 13, 11.5, "concrete"),
    box(3, 13, 3, 13, 16, 13, "casing"),
]
# Rampe de déluge, épurée : une conduite d'eau sur deux selles, et une buse courte au milieu.
DELUGE_PIPE = [
    box(3, 0, 6.5, 5, 1, 9.5, "grey"), box(11, 0, 6.5, 13, 1, 9.5, "grey"),
    piped(box(0, 1, 6, 16, 5, 10, "waterh")),
    box(6.5, 5, 6.5, 9.5, 8, 9.5, "inox"),
    box(7.25, 8, 7.25, 8.75, 9, 8.75, "dark"),
]


def fuel_port(pipe_key, sign_key):
    """
    Bloc de ravitaillement, presque plein : un caisson en carter à un demi-pixel du bord, ceinturé en bas d'une bande de
    danger et en haut d'une ceinture d'acier pleines. Sur chaque flanc, la bride où se branchent réservoirs et tuyaux :
    un cadre d'acier en léger relief, et au fond la couleur de son ergol ; au-dessus, l'étiquette de l'ergol.
    """
    parts = [
        box(0.5, 2, 0.5, 15.5, 14, 15.5, "casing"),
        box(0, 0, 0, 16, 2, 16, "hazard"), box(0, 14, 0, 16, 16, 16, "grey"),
    ]
    for side in ("north", "south", "west", "east"):
        def plate(u0, y0, u1, y1, d0, d1, texture, side=side):
            # Un pavé posé contre ce flanc : u le long du flanc, d vers l'intérieur du bloc.
            if side == "north":
                return box(u0, y0, d0, u1, y1, d1, texture)
            if side == "south":
                return box(16 - u1, y0, 16 - d1, 16 - u0, y1, 16 - d0, texture)
            if side == "west":
                return box(d0, y0, 16 - u1, d1, y1, 16 - u0, texture)
            return box(16 - d1, y0, u0, 16 - d0, y1, u1, texture)
        parts += [
            plate(4, 3, 12, 4, 0, 0.5, "grey"), plate(4, 10, 12, 11, 0, 0.5, "grey"),
            plate(4, 4, 5, 10, 0, 0.5, "grey"), plate(11, 4, 12, 10, 0, 0.5, "grey"),
            whole(plate(5, 4, 11, 10, 0.25, 0.5, pipe_key)),
            whole(plate(4, 11.25, 12, 13.75, 0.25, 0.5, sign_key)),
        ]
    return parts


# Passerelle d'embarquement : un tronçon d'un bloc de long (le rendu le répète jusqu'à la capsule) fait d'un
# platelage, de deux longerons et de deux garde-corps ; et son seuil, au bout, contre l'écoutille. Le dessus du
# platelage est en y = 0. Comme un bras, la passerelle part de sa charnière et pivote pour se ranger.
def gangway_span(n):
    """Travée de passerelle : le platelage, ses deux longerons, et de chaque côté une lisse haute et une lisse basse."""
    return [
        box(0, -1, 1.5, n, 0, 14.5, "plate"),
        box(0, -3, 0.5, n, 0.4, 1.5, "red"), box(0, -3, 14.5, n, 0.4, 15.5, "red"),
        box(0, 13.2, 0.6, n, 14, 1.4, "steel"), box(0, 13.2, 14.6, n, 14, 15.4, "steel"),
        box(0, 6.8, 0.75, n, 7.4, 1.25, "steel"), box(0, 6.8, 14.75, n, 7.4, 15.25, "steel"),
    ]


# Potelets du garde-corps, un de chaque côté : ils s'arrêtent sous la lisse haute.
# Ils sont décalés d'un dixième de pixel : posés à la jonction de deux travées, leur face ne tombe ainsi jamais dans
# le plan du bout d'une lisse.
GANGWAY_POST = [box(0.1, 0.4, 0.6, 0.9, 13.2, 1.4, "steel"), box(0.1, 0.4, 14.6, 0.9, 13.2, 15.4, "steel")]
GANGWAY_END = [
    box(0, -1.1, 1.5, 2.5, 0.15, 14.5, "hazard"),
    box(1.7, 0.15, 0.6, 2.5, 14, 1.4, "steel"), box(1.7, 0.15, 14.6, 2.5, 14, 15.4, "steel"),
]


# --- Bras ombilicaux -------------------------------------------------------
# Poutre d'un bras, épurée : quatre membrures, le chemin de câbles posé dessus, et deux conduites à l'intérieur
# (l'oxygène et le carburant). Elle existe en travées de 1, 2, 4, 8 et 16 pixels : le rendu en met bout à bout ce
# qu'il faut pour toute longueur, sans jamais en étirer une. Aucune texture n'est donc déformée, que le bras soit
# long ou court, déployé ou à demi rentré.
SPANS = (1, 2, 4, 8, 16)


def arm_span(n):
    return [
        box(0, 3, 3, n, 4.5, 4.5, "red"), box(0, 11.5, 3, n, 13, 4.5, "red"),
        box(0, 3, 11.5, n, 4.5, 13, "red"), box(0, 11.5, 11.5, n, 13, 13, "red"),
        piped(box(0, 13, 6, n, 14, 10, "cableh")),
        piped(box(0, 5, 5, n, 7, 7, "loxh")), piped(box(0, 5, 9, n, 7, 11, "rp1h")),
    ]


# Cadre : il ferme la poutre à son départ et la rythme tous les blocs.
ARM_RIB = [
    box(0, 4.5, 3, 1.5, 11.5, 4.5, "red"), box(0, 4.5, 11.5, 1.5, 11.5, 13, "red"),
    box(0, 11.5, 4.5, 1.5, 13, 11.5, "red"), box(0, 3, 4.5, 1.5, 4.5, 11.5, "red"),
]


def arm_hose(n):
    """Flexibles entre le bout du bras et la plaque de raccordement : ils prolongent les deux conduites et les câbles."""
    return [piped(box(0, 5, 5, n, 7, 7, "rubber")), piped(box(0, 5, 9, n, 7, 11, "rubber")),
            piped(box(0, 9, 7, n, 10, 9, "cableh"))]


ARM_TRUSS = arm_span(16) + ARM_RIB
# Plaque de raccordement : une platine de la section du bras, avec ses trois coupleurs, qui se plaque contre la fusée.
ARM_PLATE = [
    box(0, 3, 3, 1.25, 13, 13, "inox"),
    # Les coupleurs s'arrêtent un rien avant la platine : leur bout ne tombe pas dans le plan de celui des conduites.
    box(-0.8, 4.6, 4.6, -0.05, 7.4, 7.4, "grey"), box(-0.8, 4.6, 8.6, -0.05, 7.4, 11.4, "grey"),
    box(-0.6, 8.6, 6.6, -0.05, 10.4, 9.4, "grey"),
]


# --- Mâts ------------------------------------------------------------------
def mast_tube(y0, y1, paint="paint"):
    """Fût d'un mât : un tube carré."""
    return [box(6, y0, 6, 10, y1, 10, paint)]


def mast_fittings(y0):
    """Ce qui court le long du fût : plus rien (ni échelle ni câble de terre), le fût reste nu."""
    return []


# Bride d'assemblage, au pied de chaque tronçon : le fût et ses accessoires partent de son dessus.
MAST_FLANGE = [box(5, 0, 5, 11, 1, 11, "dark")]


def mast_segment(paint="paint", flange=True):
    """
    Tronçon de mât. « banded » : blanc, bagué de rouge en haut, comme le tronçon posé seul et le projecteur de mât.
    Sans bride (« flange » faux), le fût descend jusqu'au sol : c'est le bloc posé seul, hors d'un pas de tir.
    """
    y0 = 1 if flange else 0
    tube = mast_tube(y0, 11) + mast_tube(11, 16, "paintred") if paint == "banded" else mast_tube(y0, 16, paint)
    return tube + (MAST_FLANGE if flange else []) + mast_fittings(y0)


def lamp_head(lens):
    """Projecteurs d'un mât : une plateforme à garde-corps, un bras, une traverse et deux projecteurs tournés vers le nord."""
    parts = [
        box(3, 4, 3, 13, 4.6, 13, "plate"),
        box(3, 4.6, 3, 3.6, 9, 3.6, "steel"), box(12.4, 4.6, 3, 13, 9, 3.6, "steel"),
        box(3, 4.6, 12.4, 3.6, 9, 13, "steel"), box(12.4, 4.6, 12.4, 13, 9, 13, "steel"),
        box(3, 9, 3, 13, 9.6, 3.6, "steel"), box(3, 9, 12.4, 13, 9.6, 13, "steel"),
        box(3, 9, 3.6, 3.6, 9.6, 12.4, "steel"), box(12.4, 9, 3.6, 13, 9.6, 12.4, "steel"),
        box(7.25, 10.5, 2, 8.75, 11.5, 6, "grey"), box(3, 10, 1, 13, 12, 2, "grey"),
    ]
    for x in (3.5, 8.5):
        parts += [box(x, 8.5, -1, x + 4, 13.5, 1, "grey"), whole(box(x + 0.4, 9, -1.3, x + 3.6, 13, -1, lens))]
    return parts


def mast_lamp(lens, diagonal=False, banded=False, flange=True):
    """Tronçon de mât à projecteurs. En diagonale, la tête est tournée d'un huitième de tour vers l'est."""
    head = lamp_head(lens)
    if diagonal:
        head = [{**part, "stretch": True, "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": -45}} for part in head]
    return mast_segment("banded" if banded else "paint", flange) + head


def shockwave(size=128):
    """
    Onde de choc, vue de dessus, blanche (l'affichage la teinte) : un anneau net sur son bord extérieur, qui traîne
    vers l'intérieur un voile de plus en plus léger. Par paliers francs, dans le grain du jeu.
    """
    half = size / 2.0
    img = []
    for y in range(size):
        row = []
        for x in range(size):
            r = math.hypot(x + 0.5 - half, y + 0.5 - half) / half
            if r > 1.0:
                alpha = 0.0
            elif r > 0.82:
                alpha = 1.0 - (r - 0.91) ** 2 * 110
            else:
                alpha = 0.6 * max(0.0, (r - 0.3) / 0.52) ** 2
            # Des rayons : l'anneau n'est pas un disque parfait, il a du grain.
            angle = math.atan2(y + 0.5 - half, x + 0.5 - half)
            alpha *= 0.82 + 0.18 * math.sin(angle * 24)
            alpha = round(max(0.0, min(1.0, alpha)) * 10) / 10.0
            row.append((255, 255, 255, int(255 * alpha)))
        img.append(row)
    return img


def exhaust_flame(width=16, height=32):
    """
    Dard d'un moteur-fusée, vu de profil, la tuyère en haut : un cœur blanc et étroit, une gaine jaune puis orangée
    qui s'évase, et une queue rouge qui s'effiloche. Par paliers francs, dans le grain du jeu. Le rendu le plaque sur
    trois plans croisés, attachés à la tuyère : la flamme suit l'engin au degré près.
    """
    img = []
    for y in range(height):
        v = (y + 0.5) / height
        row = []
        for x in range(width):
            u = abs((x + 0.5) / width * 2.0 - 1.0)
            # La flamme s'évase en quittant la tuyère, puis se referme en pointe.
            spread = 0.45 + 0.55 * math.sin(math.pi * min(1.0, v * 1.35)) ** 0.7
            edge = u / max(0.05, spread * (1.0 - v) ** 0.35)
            if edge >= 1.0:
                row.append((0, 0, 0, 0))
                continue
            heat = (1.0 - edge) * (1.0 - v) ** 0.8
            heat = round(heat * 6) / 6.0
            if heat > 0.75:
                color = (255, 250, 225)
            elif heat > 0.5:
                color = (255, 214, 110)
            elif heat > 0.25:
                color = (255, 150, 50)
            else:
                color = (230, 80, 30)
            alpha = min(1.0, 0.35 + heat * 1.2) * min(1.0, (1.0 - v) * 3.0)
            row.append((color[0], color[1], color[2], int(255 * round(alpha * 8) / 8.0)))
        img.append(row)
    return img


def exhaust_puff(size=16):
    """
    Bouffée de flamme, blanche (l'affichage la teinte) : un disque plein au centre, qui s'efface vers le bord par
    paliers francs. Le rendu en fait défiler une vingtaine le long du dard de chaque moteur.
    """
    half = size / 2.0
    img = []
    for y in range(size):
        row = []
        for x in range(size):
            r = math.hypot((x + 0.5 - half) / half, (y + 0.5 - half) / half)
            alpha = max(0.0, min(1.0, (1.0 - r) * 1.6))
            row.append((255, 255, 255, int(255 * round(alpha * 4) / 4.0)))
        img.append(row)
    return img


def led_glow(size=64):
    """
    Halo d'une LED, blanc (l'affichage le teinte) : un cœur plein, une auréole qui s'efface vite puis lentement, et
    quatre aigrettes fines, comme en donne une source ponctuelle vue par un œil ou un objectif.
    """
    half = size / 2.0
    img = []
    for y in range(size):
        row = []
        for x in range(size):
            dx, dy = (x + 0.5 - half) / half, (y + 0.5 - half) / half
            r = math.hypot(dx, dy)
            glow = math.exp(-(r / 0.2) ** 2) + 0.7 * math.exp(-r / 0.3) * max(0.0, 1.0 - r)
            spike = 0.5 * max(0.0, 1.0 - r) ** 2 * (math.exp(-(dy / 0.03) ** 2) + math.exp(-(dx / 0.03) ** 2))
            # Paliers francs plutôt que dégradé continu : le halo reste dans le grain du jeu.
            alpha = min(1.0, glow + spike)
            alpha = round(alpha * 12) / 12.0
            row.append((255, 255, 255, int(255 * alpha)))
        img.append(row)
    return img


MAST_BASE = [
    # Embase : une platine, un socle, et le coffret de mise à la terre au bout du câble.
    box(3, 0, 3, 13, 1, 13, "dark"),
    box(4.5, 1, 4.5, 11.5, 4, 11.5, "paint"),
    box(10.6, 5, 6.5, 12.2, 9, 9.5, "grey"), whole(box(12.2, 5.5, 7, 12.4, 8.5, 9, "s_warning")),
] + mast_tube(4, 16) + mast_fittings(4)

MAST_TOP = mast_tube(0, 8) + [
    # Tête : un plateau, une hampe, deux isolateurs, le paratonnerre et sa balise.
    box(4.5, 8, 4.5, 11.5, 9, 11.5, "dark"),
    box(6.75, 9, 6.75, 9.25, 14, 9.25, "paint"),
    box(5.5, 14, 5.5, 10.5, 16, 10.5, "insulator"), box(6.5, 16, 6.5, 9.5, 18, 9.5, "insulator"),
    box(5.5, 18, 5.5, 10.5, 20, 10.5, "insulator"), box(6.5, 20, 6.5, 9.5, 22, 9.5, "insulator"),
    box(7.5, 22, 7.5, 8.5, 30, 8.5, "inox"),
    box(6.75, 30, 6.75, 9.25, 31.9, 9.25, "beaconoff"),
]


# --- Plateforme ------------------------------------------------------------
# Table de lancement : quatre piliers, une couronne d'acier qui laisse passer les gaz en son centre, quatre
# sabots de retenue, des entretoises, la crête du déflecteur, un coffret de commande et son chemin de câbles.
# Entre les piliers, quatre ouvertures : c'est par là que les gaz filent vers le carneau.
def launch_table(led):
    pillars = [box(x, 0, z, x + 3.5, 11, z + 3.5, "thermal") for x in (0, 12.5) for z in (0, 12.5)]
    crown = [
        faced(box(0, 11, 0, 16, 15.5, 4, "grey"), up="plate"), faced(box(0, 11, 12, 16, 15.5, 16, "grey"), up="plate"),
        faced(box(0, 11, 4, 4, 15.5, 12, "grey"), up="plate"), faced(box(12, 11, 4, 16, 15.5, 12, "grey"), up="plate"),
    ]
    caps = [part for x in (0, 12.5) for z in (0, 12.5) for part in (
        box(x, 15.5, z, x + 3.5, 16, z + 3.5, "steel"),
        whole(box(x + 1, 16, z + 1, x + 2.5, 16.3, z + 2.5, led)),
    )]
    clamps = [box(x, 12.5, z, x + 1.75, 16, z + 1.75, "hazard") for x in (4, 10.25) for z in (4, 10.25)]
    ties = [
        box(3.5, 1, 0.6, 12.5, 2.8, 2, "grey"), box(3.5, 1, 14, 12.5, 2.8, 15.4, "grey"),
        box(0.6, 1, 3.5, 2, 2.8, 12.5, "grey"), box(14, 1, 3.5, 15.4, 2.8, 12.5, "grey"),
    ]
    crest = [box(3.5, 0, 3.5, 12.5, 2.4, 12.5, "thermal"), box(5.5, 2.4, 5.5, 10.5, 4.8, 10.5, "thermal"),
             box(7, 4.8, 7, 9, 6.8, 9, "thermal")]
    service = [whole(box(3.6, 9.6, 0.3, 12.4, 10.6, 1.2, "cableh")), box(6, 5, 0.2, 10, 9, 1.4, "dark"),
               whole(box(6.6, 6.2, 0.05, 9.4, 7.8, 0.2, "s_orbitalis"))]
    return pillars + crown + caps + clamps + ties + crest + service


# Déflecteur de flammes : un massif de béton, un corps de briques réfractaires entre quatre contreforts, et une
# épaisse plaque de métal thermique.
FLAME_DEFLECTOR = [
    box(0, 0, 0, 16, 5, 16, "tech"),
    box(1, 5, 1, 15, 12, 15, "brick"),
    box(0, 5, 0, 3, 12, 3, "tech"), box(13, 5, 0, 16, 12, 3, "tech"),
    box(0, 5, 13, 3, 12, 16, "tech"), box(13, 5, 13, 16, 12, 16, "tech"),
    box(0, 12, 0, 16, 16, 16, "thermal"),
]

# Carneau : une tranchée aux parois de briques réfractaires et au radier de métal thermique, bordée de deux
# longrines d'acier et couverte d'un caillebotis de barreaux. Les gaz y filent dans le sens des x.
FLAME_TRENCH = [
    box(0, 0, 1.5, 16, 1, 14.5, "thermal"),
    box(0, 0, 0, 16, 14, 1.5, "brick"), box(0, 0, 14.5, 16, 14, 16, "brick"),
    box(0, 14, 0, 16, 16, 1.5, "steel"), box(0, 14, 14.5, 16, 16, 16, "steel"),
    box(0, 12.8, 1.5, 16, 13.8, 2.4, "grey"), box(0, 12.8, 13.6, 16, 13.8, 14.5, "grey"),
    box(0, 14.2, 5, 16, 15.1, 5.8, "steel"), box(0, 14.2, 10.2, 16, 15.1, 11, "steel"),
] + [box(0.6 + 3.2 * k, 13.9, 1.5, 1.8 + 3.2 * k, 15.5, 14.5, "grey") for k in range(5)]

# Balise rouge allumée : de la taille de la balise éteinte d'un mât, qu'elle recouvre d'un dixième de pixel (assez
# pour que les deux ne se disputent pas l'affichage, trop peu pour qu'on la voie grossir).
BEACON = [box(6.65, 29.9, 6.65, 9.35, 32, 9.35, "beacon")]

# Toutes les pièces du kit. Celles qui portent le nom d'un bloc lui servent aussi de modèle, posé seul.
PARTS = {
    # Blocs de la tour : charpente, colonne technique, portique d'embarquement (deux moitiés), socle de l'ascenseur.
    "tower_frame": FRAME + FRAME_TOP + FRAME_BRACES,
    "service_tower": FRAME + TOWER_RISERS + FRAME_BRACES[:3],
    "boarding_gate": gate_lower(),
    "boarding_gate_closed": gate_lower(True),
    "boarding_gate_top": gate_top(),
    "boarding_gate_top_closed": gate_top(True),
    "tower_elevator": ELEVATOR_BASE,
    # Escalier, palier et rambarde de la tour ; appui du tablier ; rampe de déluge ; blocs de ravitaillement.
    "tower_stairs": TOWER_STAIRS,
    "tower_landing": TOWER_LANDING,
    "tower_deck": TOWER_DECK,
    "tower_railing": TOWER_RAILING,
    "tower_railing_post": TOWER_RAILING_POST,
    "tower_railing_side": TOWER_RAILING_SIDE,
    "tower_railing_post_low": railing_post(True),
    "tower_railing_side_low": railing_side(True),
    "pad_support": PAD_SUPPORT,
    "deluge_pipe": DELUGE_PIPE,
    "fuel_port_rp1": fuel_port("rp1h", "s_rp1"),
    "fuel_port_water": fuel_port("waterh", "s_water"),
    "fuel_port_lox": fuel_port("loxh", "s_lox"),
    "fuel_port_lh2": fuel_port("lh2h", "s_lh2"),
    "fuel_port_ch4": fuel_port("ch4h", "s_ch4"),
    "fuel_port_he3": fuel_port("he3h", "s_he3"),
    # Pièces animées : la cabine de l'ascenseur, la passerelle et son seuil, la balise des mâts.
    "elevator_cabin": ELEVATOR_CABIN,
    "elevator_cabin_top": ELEVATOR_CABIN_TOP,
    "gangway_post": GANGWAY_POST,
    "gangway_end": GANGWAY_END,
    "beacon": BEACON,
    # Bras.
    "arm_rib": ARM_RIB,
    "arm_plate": ARM_PLATE,
    # Mâts. Le bloc posé seul est un tronçon de fût, bagué de rouge.
    "mast_block": mast_segment("banded", flange=False),
    "mast_base": MAST_BASE,
    "mast_mid": mast_segment(),
    "mast_mid_red": mast_segment("paintred"),
    "mast_lamp": mast_lamp("lampoff"),
    "mast_lamp_on": mast_lamp("lamp"),
    # Projecteur de mât, posé seul : le même tronçon, éteint ou allumé, tourné droit ou en diagonale.
    "pad_floodlight": mast_lamp("lampoff", banded=True, flange=False),
    "pad_floodlight_on": mast_lamp("lamp", banded=True, flange=False),
    "pad_floodlight_diagonal": mast_lamp("lampoff", True, True, flange=False),
    "pad_floodlight_diagonal_on": mast_lamp("lamp", True, True, flange=False),
    "mast_top": MAST_TOP,
    # Plateforme.
    "launch_table": launch_table("ledoff"),
    "launch_table_on": launch_table("led"),
    "flame_deflector": FLAME_DEFLECTOR,
    "flame_trench": FLAME_TRENCH,
}
# Le bras posé seul montre un bloc de sa poutre.
PARTS["umbilical_arm"] = ARM_TRUSS
for _n in SPANS:
    PARTS["arm_span_%d" % _n] = arm_span(_n)
    PARTS["gangway_span_%d" % _n] = gangway_span(_n)
for _n in (1, 2, 4):
    PARTS["arm_hose_%d" % _n] = arm_hose(_n)


# Modèles plus grands qu'un bloc, découpés le long de la grille pour que leurs textures ne soient pas étirées.
TILED = {"elevator_cabin", "elevator_cabin_top", "boarding_gate", "boarding_gate_closed", "boarding_gate_top", "boarding_gate_top_closed"}
# Présentation en objet : l'escalier se montre de profil, comme ceux du jeu.
DISPLAYS = {"tower_stairs": display([0, 0, 0], [16, 16, 16], gui_rotation=(30, 135, 0)),
            "boarding_gate": display([0, 0, -16], [16, 32, 32], gui_rotation=(30, 135, 0), fill=1.2)}


def write_models():
    os.makedirs(gm.MODEL_DIR, exist_ok=True)
    faces = 0
    for name, parts in PARTS.items():
        for part in parts:
            assert all(-16 <= c <= 32 for c in part["from"] + part["to"]), (name, part)
            assert all(a < b for a, b in zip(part["from"], part["to"])), (name, part)
        parts = separate(parts)
        if name in TILED:
            model = build_tiled(parts, {**gm.COMMON, **PAD_TEXTURES}, EMISSIVE)
        else:
            model = gm.build_extra(parts, PAD_TEXTURES, EMISSIVE)
        if name in DISPLAYS:
            model["display"] = DISPLAYS[name]
        overlaps = check_overlaps(name, parts)
        assert not overlaps, name
        model["textures"]["particle"] = "orbitalis:block/pad_steel"
        with open(os.path.join(gm.MODEL_DIR, name + ".json"), "w", newline="\n") as f:
            json.dump(model, f, indent=2)
        faces += 6 * len(parts)
    return faces


# ---------------------------------------------------------------------------
# Grandes images du tablier assemblé
# ---------------------------------------------------------------------------
def paste(img, tile, x0, y0):
    for y in range(len(tile)):
        for x in range(len(tile[0])):
            if 0 <= y0 + y < len(img) and 0 <= x0 + x < len(img[0]) and tile[y][x][3]:
                img[y0 + y][x0 + x] = tile[y][x]


def deck_tile(seed):
    """Dalle du tablier : béton coulé, avec un joint de dilatation sur deux bords."""
    img = blotchy(seed, [rgb(c) for c in CONCRETE], [6, 4, 3, 1], passes=3)
    for i in range(SIZE):
        img[SIZE - 1][i] = shade(img[SIZE - 1][i], 0.84)
        img[i][SIZE - 1] = shade(img[i][SIZE - 1], 0.84)
    return img


def deck_top(radius):
    """
    Dessus du tablier, 16 pixels par bloc. Le carneau part du centre vers la droite ; le rendu tourne l'image.
    L'emplacement de la table et celui du carneau sont à jour : on y voit les pièces 3D, en relief.
    """
    blocks = 2 * radius + 1
    size = blocks * 16
    center = size // 2
    rnd = random.Random(300 + radius)
    tiles = [deck_tile(310 + k) for k in range(5)]
    img = [[None] * size for _ in range(size)]
    for by in range(blocks):
        for bx in range(blocks):
            paste(img, rnd.choice(tiles), bx * 16, by * 16)

    # Chemin de câbles enterré, sous des tôles larmées : du bord opposé au carneau jusqu'à la table.
    plate = tread()
    for x in range(4, center - 8):
        for y in range(center - 3, center + 3):
            img[y][x] = plate[y % 16][x % 16]
        img[center - 4][x] = img[center + 3][x] = rgb("#4A5058")

    # Traces de flammes : le béton noircit vers le centre, et plus encore le long du carneau.
    for y in range(size):
        for x in range(size):
            distance = math.hypot(x + 0.5 - center, y + 0.5 - center)
            burn = max(0.0, 1.0 - distance / (22.0 + 9.0 * radius)) ** 1.3 * 0.95
            if x > center:
                burn = max(burn, max(0.0, 1.0 - abs(y + 0.5 - center) / 22.0) * 0.75)
            img[y][x] = mix(img[y][x], SOOT, min(0.82, burn * rnd.uniform(0.8, 1.15)))
    # Coulures : de l'eau du déluge chargée de suie, qui a séché en rayonnant depuis la table.
    for _ in range(2 * radius):
        angle = rnd.uniform(0, 2 * math.pi)
        start, length = rnd.uniform(12, 20), rnd.uniform(6, 10 + 6 * radius)
        for step in range(int(length)):
            x = int(center + math.cos(angle) * (start + step))
            y = int(center + math.sin(angle) * (start + step))
            if 0 <= x < size and 0 <= y < size:
                img[y][x] = mix(img[y][x], SOOT, 0.35 * (1.0 - step / length))

    # Marquages au sol : cercle de sécurité en pointillés, allée piétonne jaune de part et d'autre du chemin
    # de câbles, numéro de la classe du pas de tir et marque d'Orbitalis.
    white, yellow = rgb("#E4E3DC"), rgb("#E0B33E")
    ring = min(size * 0.36, center - 9)
    if radius >= 2:
        for step in range(720):
            if step // 12 % 2 == 0:
                angle = step * math.pi / 360
                x, y = int(center + math.cos(angle) * ring), int(center + math.sin(angle) * ring)
                img[y][x] = mix(img[y][x], white, 0.85)
        for x in range(4, center - 10):
            if x // 3 % 2 == 0:
                img[center - 7][x] = mix(img[center - 7][x], yellow, 0.85)
                img[center + 6][x] = mix(img[center + 6][x], yellow, 0.85)
        # Numéro de classe : un grand chiffre au pochoir, dans son cadre jaune.
        stencil = [[None] * 9 for _ in range(15)]
        text(stencil, 0, 0, str(radius), white, 3)
        nx, ny = 23, size - 39
        for y in range(-3, 18):
            for x in range(-3, 12):
                px, py = nx + x, ny + y
                if x in (-3, 11) or y in (-3, 17):
                    img[py][px] = mix(img[py][px], yellow, 0.85)
                elif 0 <= y < 15 and 0 <= x < 9 and stencil[y][x]:
                    img[py][px] = mix(img[py][px], white, 0.9)
        mark = sign("#1B2A4A", "#F2C14E", draw_orbit)
        for y in range(16):
            for x in range(16):
                px, py = 22 + x, 20 + y
                if mark[y][x][:3] != rgb("#1B2A4A")[:3]:
                    img[py][px] = mix(img[py][px], mark[y][x], 0.75)

    # Points d'arrimage : une cuvette et son anneau, tous les deux blocs.
    for by in range(1, blocks - 1, 2):
        for bx in range(1, blocks - 1, 2):
            x, y = bx * 16 + 8, by * 16 + 8
            if math.hypot(x - center, y - center) > 20 and not (x > center and abs(y - center) < 14):
                img[y][x] = img[y][x - 1] = rgb("#2E3238")
                img[y - 1][x] = img[y - 1][x - 1] = rgb("#8C949E")

    # Platines des mâts, aux quatre angles : une tôle boulonnée, cernée de jaune.
    steel_plate = steel(320, "#6B737D", "#727A84", "#646C75", [])
    for cx in (8, size - 8):
        for cy in (8, size - 8):
            for y in range(cy - 6, cy + 6):
                for x in range(cx - 6, cx + 6):
                    border = max(abs(x + 0.5 - cx), abs(y + 0.5 - cy)) > 5
                    img[y][x] = yellow if border else steel_plate[y % 16][x % 16]

    # Bordure : bande de sécurité sur le pourtour, sous une cornière d'acier.
    stripes = hazard()
    for y in range(size):
        for x in range(size):
            margin = min(x, y, size - 1 - x, size - 1 - y)
            if margin == 0:
                img[y][x] = rgb("#9DA6B1")
            elif margin < 3:
                near_mast = min(abs(x - 8), abs(x - (size - 8))) < 7 and min(abs(y - 8), abs(y - (size - 8))) < 7
                if not near_mast:
                    img[y][x] = stripes[y % 16][x % 16]
            elif margin == 3:
                img[y][x] = shade(img[y][x], 0.7)

    # Encadrement de la table : une bande de sécurité autour de son puits. Puis les deux jours.
    for y in range(center - 11, center + 11):
        for x in range(center - 11, center + 11):
            edge = max(abs(x + 0.5 - center), abs(y + 0.5 - center))
            if edge > 10:
                img[y][x] = rgb("#3A3D42")
            elif edge > 8:
                img[y][x] = stripes[y % 16][x % 16]
    for y in range(center - 8, center + 8):
        for x in range(center - 8, size):
            img[y][x] = TRANSPARENT
    for x in range(center + 8, size):
        img[center - 9][x] = img[center + 8][x] = rgb("#3A3D42")
    return img


def deck_bottom(radius):
    """Dessous du tablier : des dalles de béton brut, plus sombres, portées par un quadrillage de poutres d'acier."""
    blocks = 2 * radius + 1
    size = blocks * 16
    rnd = random.Random(400 + radius)
    tiles = [deck_tile(410 + k) for k in range(4)]
    img = [[None] * size for _ in range(size)]
    for by in range(blocks):
        for bx in range(blocks):
            paste(img, rnd.choice(tiles), bx * 16, by * 16)
    beam, edge = rgb("#3F444B"), rgb("#2A2E33")
    for y in range(size):
        for x in range(size):
            img[y][x] = shade(img[y][x], 0.62)
            # Une poutre sous chaque joint de dalles, large de deux pixels, et sa rive plus sombre.
            near = min(x % 16, y % 16)
            far = max(x % 16, y % 16)
            if near == 0 or far == 15:
                img[y][x] = beam
            elif near == 1 or far == 14:
                img[y][x] = edge
    return img


def deck_side(radius, mouth):
    """
    Flanc du tablier : une cornière d'acier rivetée, la bande de sécurité, des panneaux de béton, un fourreau de
    câbles sur ses colliers, des barbacanes qui ont coulé, des panneaux d'avertissement et une semelle sombre.
    Du côté où débouche le carneau, le flanc est percé de sa bouche.
    """
    width = (2 * radius + 1) * 16
    rnd = random.Random(400 + radius)
    stripes, panel = hazard(), concrete_tech()
    img = [[None] * width for _ in range(16)]
    for x in range(width):
        img[0][x] = rgb("#B3BBC4") if x % 4 else rgb("#DDE2E7")
        img[1][x] = rgb("#7A838E")
        for y in (2, 3, 4):
            img[y][x] = stripes[y][x % 16]
        img[5][x] = rgb("#4E5052")
        for y in range(6, 15):
            img[y][x] = panel[y][x % 16]
        img[15][x] = rgb("#3A3A36")
        # Fourreau de câbles et ses colliers.
        img[9][x] = rgb("#2C2F33")
        img[10][x] = rgb("#17181B")
        if x % 8 == 3:
            img[8][x] = img[11][x] = rgb("#9DA6B1")
            img[9][x] = img[10][x] = rgb("#6B737D")
    # Barbacanes : un trou, et la coulure de rouille et de suie qui en descend.
    for bx in range(2 * radius + 1):
        x = bx * 16 + rnd.randint(5, 11)
        img[7][x] = rgb("#1C1D1F")
        for y in range(8, 15):
            if y not in (9, 10):
                img[y][x] = mix(img[y][x], rgb("#5A3A22"), 0.5 - 0.04 * (y - 8))
    # Panneaux d'avertissement, tous les deux blocs.
    for bx in range(1, 2 * radius + 1, 2):
        x0 = bx * 16 + 5
        for y in range(11, 15):
            for x in range(x0, x0 + 5):
                img[y][x] = rgb("#F2C14E")
        img[12][x0 + 2] = img[13][x0 + 2] = rgb("#1B1C1F")
        img[11][x0 + 2] = rgb("#1B1C1F")
    if mouth:
        middle = width // 2
        for y in range(16):
            for x in range(middle - 8, middle + 8):
                img[y][x] = TRANSPARENT
        for y in range(16):
            for x in list(range(middle - 12, middle - 8)) + list(range(middle + 8, middle + 12)):
                img[y][x] = mix(img[y][x], SOOT, 0.55)
            img[y][middle - 9] = img[y][middle + 8] = rgb("#3A3D42")
    return img


def write_decks():
    os.makedirs(ENTITY_DIR, exist_ok=True)
    for radius in range(1, 6):
        write_png(os.path.join(ENTITY_DIR, "led_glow.png"), led_glow())
        write_png(os.path.join(ENTITY_DIR, "shockwave.png"), shockwave())
        write_png(os.path.join(ENTITY_DIR, "exhaust_flame.png"), exhaust_flame())
        write_png(os.path.join(ENTITY_DIR, "exhaust_puff.png"), exhaust_puff())
        write_png(os.path.join(ENTITY_DIR, "launch_pad_top_%d.png" % radius), deck_top(radius))
        write_png(os.path.join(ENTITY_DIR, "launch_pad_bottom_%d.png" % radius), deck_bottom(radius))
        write_png(os.path.join(ENTITY_DIR, "launch_pad_side_%d.png" % radius), deck_side(radius, False))
        write_png(os.path.join(ENTITY_DIR, "launch_pad_mouth_%d.png" % radius), deck_side(radius, True))


# ---------------------------------------------------------------------------
# Aperçus
# ---------------------------------------------------------------------------
def write_model_preview(path, names):
    """Planche des pièces, vues de trois quarts (les pièces inclinées y sont montrées droites)."""
    tiles = [gm.render(PARTS[name], False, scale=7, own_textures=PAD_TEXTURES) for name in names]
    per_row = 5
    tile = len(tiles[0])
    rows = (len(tiles) + per_row - 1) // per_row
    sheet = [[(60, 60, 64, 255) for _ in range(per_row * tile)] for _ in range(rows * tile)]
    for index, image in enumerate(tiles):
        ox, oy = (index % per_row) * tile, (index // per_row) * tile
        for y in range(tile):
            sheet[oy + y][ox:ox + tile] = image[y]
    write_png(path, sheet)


def write_deck_preview(path, radius):
    top, side = deck_top(radius), deck_side(radius, True)
    scale = 4
    width = len(top)
    sheet = [[(40, 60, 40, 255) for _ in range(width * scale)] for _ in range((width + 20) * scale)]
    for y in range(width * scale):
        for x in range(width * scale):
            p = top[y // scale][x // scale]
            if p[3]:
                sheet[y][x] = p
    for y in range(16 * scale):
        for x in range(width * scale):
            p = side[y // scale][x // scale]
            if p[3]:
                sheet[(width + 4) * scale + y][x] = p
    write_png(path, sheet)


if __name__ == "__main__":
    materials = write_materials()
    faces = write_models()
    write_decks()
    print("Pas de tir : %d textures, %d pièces (%d faces), 5 tailles de tablier." % (len(materials), len(PARTS), faces))
    if len(sys.argv) > 1:
        preview(sys.argv[1], materials, scale=8, per_row=10)
    if len(sys.argv) > 2:
        gm.write_textures()
        write_model_preview(sys.argv[2], ["tower_frame", "service_tower", "boarding_gate", "boarding_gate_top", "elevator_cabin",
                                           "launch_table", "flame_deflector", "flame_trench", "mast_lamp_on", "pad_valve"])
    if len(sys.argv) > 3:
        write_deck_preview(sys.argv[3], 3)
