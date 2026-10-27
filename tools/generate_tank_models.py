#!/usr/bin/env python3
"""
Génère les modèles et les textures du réservoir modulable d'Orbitalis, et ceux des réservoirs créatifs.

C'est une cuve vitrée, d'allure contemporaine : de grandes parois de verre teinté tenues par une ossature de
graphite mat, ceinturée en haut et en bas d'un bandeau d'aluminium brossé.

Un réservoir est fait de blocs qui s'assemblent : son modèle est donc découpé en morceaux, et chaque bloc ne
montre que ceux qui se trouvent au bord du réservoir.

- une paroi par côté libre : un verre bleuté, presque limpide, avec un reflet en biais, un fin montant à chaque
  joint de bloc, un cerclage discret à chaque étage et une échelle graduée sur le bord. Le verre va d'un bord à
  l'autre du bloc : sur un grand réservoir, les parois forment une seule façade vitrée ;
- un fond et un couvercle de graphite, là où le réservoir s'arrête en bas et en haut ; le couvercle porte un
  trou d'homme en aluminium ;
- des montants de graphite là où deux parois se rencontrent, et une ceinture d'aluminium, haute de deux pixels,
  là où une paroi rencontre le fond ou le couvercle.

Les morceaux sont dessinés pour le côté nord (ou le coin nord-ouest) : le jeu les tourne vers les autres côtés.
Le liquide lui-même n'est pas dans le modèle : c'est le jeu qui le dessine, à son niveau, dans tout le réservoir.

Un réservoir créatif a la même silhouette, d'un seul bloc, toujours plein, avec une ossature violette.

Usage : python tools/generate_tank_models.py
        (à lancer après generate_machine_models.py, dont il réutilise les outils de dessin)
"""
import json
import os

from generate_machine_models import plain, write_texture
from generate_textures import ROOT, SIZE, rgb
from model_kit import check_overlaps

MODEL_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "orbitalis", "models", "block", "tank")
# Les fluides des réservoirs créatifs, et leur couleur (celle des jauges).
CREATIVE = [("rp1", "#C98A3A"), ("liquid_oxygen", "#5FB0F5"), ("liquid_hydrogen", "#D96A6A"), ("water", "#3F76E4"),
            ("hydrogen", "#F0A6A6"), ("oxygen", "#BFE4FF"), ("methane", "#B6E3C6"), ("liquid_methane", "#6B4A2A"),
            ("helium_3", "#F1E7A8")]
CLEAR = (0, 0, 0, 0)


# ---------------------------------------------------------------------------
# Textures
# ---------------------------------------------------------------------------
def side_texture():
    """
    Paroi : un verre bleuté presque limpide. Un seul reflet fin le traverse en biais, adouci sur ses bords, et un
    second, plus discret, le suit ; un fin montant marque chaque joint de bloc, un filet translucide chaque étage ;
    une jauge graduée, givrée, court le long du bord droit.
    """
    glass = (150, 208, 240, 40)
    img = [[glass for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(1, 15):
            # Le reflet : une ligne d'un pixel, dans le coin haut gauche, entourée d'un halo plus léger.
            d = x + y
            if d == 6:
                img[y][x] = (240, 250, 255, 120)
            elif d in (5, 7):
                img[y][x] = (225, 244, 255, 70)
            elif d == 10 and y < 9:
                img[y][x] = (225, 244, 255, 58)
        # Les montants de joint, sombres et nets.
        img[y][0] = img[y][15] = (28, 32, 37, 240)
        # La jauge : une bande givrée sur le bord droit.
        img[y][13] = (205, 228, 242, 82)
        img[y][14] = (190, 218, 236, 66)
    for x in range(1, 15):
        img[15][x] = (28, 32, 37, 110)
    # Les graduations : un trait tous les quatre pixels, plus long au milieu de l'étage.
    for y in (3, 7, 11):
        img[y][13] = img[y][14] = (246, 249, 252, 255)
        if y == 7:
            img[y][12] = (246, 249, 252, 255)
    return img


def graphite(seed):
    """Graphite mat : un gris très sombre, presque uni."""
    img = plain(seed, "#262A30", "#2B3036", "#21252A")
    return img


def cap_texture():
    """
    Couvercle : une plaque de graphite bordée d'un filet clair, un trou d'homme en aluminium au centre (une couronne
    claire à six boulons, un tampon sombre et sa poignée), et quatre boulons dans les coins.
    """
    img = graphite(302)
    for i in range(SIZE):
        img[0][i] = img[i][0] = rgb("#3A4048")
        img[SIZE - 1][i] = img[i][SIZE - 1] = rgb("#1A1D21")
    for y in range(SIZE):
        for x in range(SIZE):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 2.6:
                img[y][x] = rgb("#3A4048")
            elif d < 3.4:
                img[y][x] = rgb("#16191C")
            elif d < 5.2:
                img[y][x] = rgb("#C3CAD2") if (x + 2 * y) % 5 else rgb("#D4DAE0")
            elif d < 5.9:
                img[y][x] = rgb("#7D8691")
    # La poignée du tampon.
    for x in (6, 7, 8, 9):
        img[7][x] = img[8][x] = rgb("#9AA3AE") if x in (6, 9) else img[7][x]
    img[7][7] = img[7][8] = img[8][7] = img[8][8] = rgb("#9AA3AE")
    # Six boulons sur la couronne.
    for x, y in ((7, 3), (8, 12), (4, 5), (11, 5), (4, 10), (11, 10)):
        img[y][x] = rgb("#5E6670")
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        img[y][x] = rgb("#9AA3AE")
        img[y + 1][x] = rgb("#1A1D21")
    return img


def base_texture():
    """Fond : le même graphite, à nervures fines et régulières."""
    return plain(303, "#202428", "#272B30", "#1A1D21", [3, 7, 11, 15])


def frame_texture(seed, base, light, dark):
    """Montants : un profilé mat à rainure centrale, un filet clair sur un bord, une ombre sur l'autre."""
    img = plain(seed, base, light, dark)
    for i in range(SIZE):
        img[i][0] = rgb(light)
        img[i][SIZE - 1] = rgb(dark)
        img[i][7] = rgb(dark)
        img[i][8] = rgb(light)
    return img


def belt_texture(accent):
    """
    Ceinture : de l'aluminium brossé, avec une seule ligne de voyants à la couleur du réservoir au milieu, et ses
    arêtes : un filet clair en haut, une ombre en bas.
    """
    img = plain(306, "#B0B8C1", "#C0C7CF", "#A2AAB4")
    for x in range(SIZE):
        img[0][x] = rgb("#D9DEE4")
        img[SIZE - 1][x] = rgb("#7F8893")
        img[7][x] = rgb("#5A626C")
        img[8][x] = rgb(accent) if x % 4 != 3 else rgb("#5A626C")
    return img


def fluid_texture(color):
    """Contenu d'un réservoir créatif : un aplat à la couleur du fluide, avec un reflet en biais."""
    base = rgb(color)
    light = tuple(min(255, c + 34) for c in base[:3]) + (255,)
    return [[light if (x + y) % 8 == 3 else base for x in range(SIZE)] for y in range(SIZE)]


def write_textures():
    write_texture("tank_side", [side_texture()])
    write_texture("tank_cap", [cap_texture()])
    write_texture("tank_base", [base_texture()])
    write_texture("tank_frame", [frame_texture(304, "#24282D", "#3A4048", "#1A1D21")])
    write_texture("tank_belt", [belt_texture("#4FB6E8")])
    write_texture("creative_tank_frame", [frame_texture(305, "#7B3FB8", "#9A5FD6", "#5E2C92")])
    write_texture("creative_tank_belt", [belt_texture("#B77BEA")])
    for name, color in CREATIVE:
        write_texture("creative_tank_" + name, [fluid_texture(color)])


# ---------------------------------------------------------------------------
# Modèles
# ---------------------------------------------------------------------------
def box(x0, y0, z0, x1, y1, z1, texture, only=None):
    part = {"from": [x0, y0, z0], "to": [x1, y1, z1], "texture": texture}
    if only:
        part["only"] = only
    return part


# Hauteur des ceintures, en pixels.
BELT = 2
# Les morceaux, côté nord. La paroi est en retrait d'un quart de pixel : elle passe sous l'ossature sans jamais
# partager son plan. La texture d'une ceinture est rayée : sa face avant, haute de deux pixels, en montre un rang
# d'aluminium et un rang de couleur.
PIECES = {
    "wall": [box(0, 0, 0.25, 16, 16, 0.75, "side", ("north", "south"))],
    "top": [box(0, 15.25, 0, 16, 15.75, 16, "cap", ("up", "down"))],
    "bottom": [box(0, 0.25, 0, 16, 0.75, 16, "base", ("up", "down"))],
    "post": [box(0, BELT, 0, 1, 16 - BELT, 1, "frame")],
    "rail_top": [box(1, 16 - BELT, 0, 15, 16, 1, "belt")],
    "rail_bottom": [box(1, 0, 0, 15, BELT, 1, "belt")],
    "corner_top": [box(0, 16 - BELT, 0, 1, 16, 1, "frame")],
    "corner_bottom": [box(0, 0, 0, 1, BELT, 1, "frame")],
}
TURN = {"north": "east", "east": "south", "south": "west", "west": "north", "up": "up", "down": "down"}


def turned(part, quarters):
    """La même pièce, tournée d'autant de quarts de tour autour de la verticale (le nord passe à l'est)."""
    for _ in range(quarters):
        x0, y0, z0 = part["from"]
        x1, y1, z1 = part["to"]
        part = {**part, "from": [16 - z1, y0, x0], "to": [16 - z0, y1, x1]}
        if "only" in part:
            part["only"] = tuple(TURN[face] for face in part["only"])
    return part


def element(part):
    faces = part.get("only", ("north", "south", "east", "west", "up", "down"))
    return {"from": part["from"], "to": part["to"], "faces": {face: {"texture": "#" + part["texture"]} for face in faces}}


def write_model(name, parts, textures, parent=None):
    used = {part["texture"] for part in parts}
    model = {"textures": {key: "orbitalis:block/" + value for key, value in textures.items() if key in used or key == "particle"},
             "elements": [element(part) for part in parts]}
    if parent:
        model = {"parent": parent, **model}
    with open(os.path.join(MODEL_DIR, name + ".json"), "w", newline="\n") as f:
        json.dump(model, f, indent=1)


def whole_block():
    """Un réservoir d'un seul bloc : tous les morceaux, sur tous les côtés."""
    parts = PIECES["top"] + PIECES["bottom"]
    for quarters in range(4):
        for name in ("wall", "post", "rail_top", "rail_bottom", "corner_top", "corner_bottom"):
            parts += [turned(part, quarters) for part in PIECES[name]]
    return parts


if __name__ == "__main__":
    write_textures()
    os.makedirs(MODEL_DIR, exist_ok=True)
    textures = {"side": "tank_side", "cap": "tank_cap", "base": "tank_base", "frame": "tank_frame", "belt": "tank_belt",
                "particle": "tank_frame"}
    for name, parts in PIECES.items():
        write_model("fluid_tank_" + name, parts, textures)
    block = whole_block()
    overlaps = check_overlaps("fluid_tank", block)
    write_model("fluid_tank_inventory", block, textures, parent="minecraft:block/block")
    for name, _ in CREATIVE:
        filled = block + [box(1, 1, 1, 15, 15, 15, "fluid")]
        overlaps += check_overlaps("creative_tank_" + name, filled)
        write_model("creative_tank_" + name, filled, {**textures, "frame": "creative_tank_frame", "belt": "creative_tank_belt", "fluid": "creative_tank_" + name,
                                                      "particle": "creative_tank_frame"}, parent="minecraft:block/block")
    print("Reservoirs ecrits, %d chevauchement(s)." % overlaps)
