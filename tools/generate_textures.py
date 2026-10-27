"""
Générateur des textures 16x16 d'Orbitalis (étape 2 : minerais et matériaux de base).

Usage (depuis la racine du projet) :
    python tools/generate_textures.py            -> écrit les textures dans src/main/resources
    python tools/generate_textures.py apercu.png -> écrit en plus une planche d'aperçu agrandie

Aucune dépendance externe : le fichier PNG est écrit avec la bibliothèque standard.
Toutes les textures sont dessinées ici (aucune texture de Minecraft n'est copiée).
"""
import math
import os
import random
import struct
import sys
import zlib

SIZE = 16
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
BLOCK_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "orbitalis", "textures", "block")
ITEM_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "orbitalis", "textures", "item")

TRANSPARENT = (0, 0, 0, 0)


# ---------------------------------------------------------------------------
# Outils
# ---------------------------------------------------------------------------
def rgb(hex_color):
    """Convertit '#RRGGBB' en tuple RGBA opaque."""
    h = hex_color.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


def write_png(path, pixels):
    """Écrit une image RGBA (liste de lignes de tuples) au format PNG."""
    height = len(pixels)
    width = len(pixels[0])
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in pixels)

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


def from_mask(mask, palette):
    """Construit une image à partir d'un dessin en caractères et d'une palette."""
    rows = [line for line in mask.strip("\n").split("\n")]
    assert len(rows) == SIZE and all(len(r) == SIZE for r in rows), "le masque doit faire 16x16"
    return [[palette.get(c, TRANSPARENT) for c in row] for row in rows]


def flip_h(mask):
    return "\n".join(line[::-1] for line in mask.strip("\n").split("\n"))


def transpose(mask):
    rows = mask.strip("\n").split("\n")
    return "\n".join("".join(rows[x][y] for x in range(SIZE)) for y in range(SIZE))


# ---------------------------------------------------------------------------
# Roches de fond
# ---------------------------------------------------------------------------
def blotchy(seed, shades, weights, passes=2):
    """Bruit en petites taches : chaque pixel a une chance de prendre la teinte d'un voisin."""
    rnd = random.Random(seed)
    pool = [shade for shade, weight in zip(shades, weights) for _ in range(weight)]
    img = [[rnd.choice(pool) for _ in range(SIZE)] for _ in range(SIZE)]
    for _ in range(passes):
        for y in range(SIZE):
            for x in range(SIZE):
                if rnd.random() < 0.45:
                    dx, dy = rnd.choice([(1, 0), (-1, 0), (0, 1), (0, -1)])
                    img[y][x] = img[(y + dy) % SIZE][(x + dx) % SIZE]
    return img


def stone_base(seed):
    """Roche grise en taches douces, dans les tons de la pierre."""
    shades = [rgb("#7F7F7F"), rgb("#747474"), rgb("#8F8F8F"), rgb("#686868")]
    return blotchy(seed, shades, [6, 4, 3, 1])


def deepslate_base(seed):
    """Roche sombre en strates horizontales, dans les tons de l'ardoise des abîmes."""
    rnd = random.Random(seed)
    shades = [rgb("#515151"), rgb("#3D3D43"), rgb("#646464"), rgb("#2F2F37")]
    img = blotchy(seed, shades, [6, 5, 2, 1], passes=1)
    # Étire chaque teinte vers la droite pour former des strates.
    for y in range(SIZE):
        for x in range(SIZE):
            if rnd.random() < 0.55:
                img[y][(x + 1) % SIZE] = img[y][x]
    return img


# ---------------------------------------------------------------------------
# Minerais : des grains à la façon du jeu (h = clair, m = moyen, s = ombre du grain, o = creux dans la roche)
# ---------------------------------------------------------------------------
# Cinq grains de formes variées, éclairés par le haut et la gauche, tous à l'écart des bords de la texture (les blocs
# voisins ne se rejoignent jamais par un grain coupé) ; sous chacun, à droite et en bas, la roche est creusée d'une
# ombre, comme si le grain affleurait d'un trou.
ORE_MASK = """
................
................
...hm...........
..hmms.....hhm..
..msso....hmmms.
...oo.....smsso.
...........oo...
................
......hhm.......
.....hmmms......
.....mmsso......
......soo...hm..
...........hmms.
..hm........sso.
..mso........o..
................
"""


def ore(base, mask, light, mid, dark):
    """Un minerai : la roche, ses grains aux trois tons, et l'ombre du creux autour de chacun (la roche assombrie)."""
    overlay = from_mask(mask.replace("o", "."), {"h": rgb(light), "m": rgb(mid), "s": rgb(dark)})
    hollow = from_mask(mask.replace("h", ".").replace("m", ".").replace("s", "."), {"o": (0, 0, 0, 255)})
    out = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            if overlay[y][x][3]:
                row.append(overlay[y][x])
            elif hollow[y][x][3]:
                r, g, b, a = base[y][x]
                row.append((round(r * 0.72), round(g * 0.72), round(b * 0.72), a))
            else:
                row.append(base[y][x])
        out.append(row)
    return out


# ---------------------------------------------------------------------------
# Blocs de métal
# ---------------------------------------------------------------------------
def metal_block(seed, pal, style):
    """Plaque de métal : bord sombre, reflet en haut à gauche, ombre en bas à droite."""
    rnd = random.Random(seed)
    edge, dark, mid, light, shine = (rgb(c) for c in pal)
    img = [[mid for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            if rnd.random() < 0.18:
                img[y][x] = rnd.choice([light, dark])
    for i in range(SIZE):
        img[0][i] = img[i][0] = light
        img[SIZE - 1][i] = img[i][SIZE - 1] = edge
    for i in range(1, SIZE - 1):
        img[1][i] = img[i][1] = shine
        img[SIZE - 2][i] = img[i][SIZE - 2] = dark
    img[0][SIZE - 1] = img[SIZE - 1][0] = mid

    if style == "rivets":          # acier : tôle rivetée
        for (x, y) in [(3, 3), (12, 3), (3, 12), (12, 12)]:
            img[y][x] = shine
            img[y + 1][x] = dark
        for x in range(2, SIZE - 2):
            img[8][x] = dark
            img[7][x] = light
    elif style == "brushed":       # aluminium : métal brossé
        for y in range(3, SIZE - 2, 3):
            for x in range(2, SIZE - 2):
                img[y][x] = light if (x + y) % 5 else shine
    elif style == "cross":         # titane : panneaux joints en croix
        for i in range(2, SIZE - 2):
            img[i][8] = dark
            img[i][7] = light
            img[8][i] = dark
            img[7][i] = light
        img[7][7] = shine
        img[8][8] = edge
    return img


# ---------------------------------------------------------------------------
# Items : o = contour, s = ombre, m = moyen, l = clair, h = reflet
# ---------------------------------------------------------------------------
# Lingot dans le style des lingots de Minecraft : une barre vue de trois quarts, avec une face
# supérieure claire, une arête brillante et une face avant plus sombre.
# O = contour, D = contour sombre, T = dessus, L = dessus clair, H = arête brillante,
# F = face avant, S = ombre de la face avant
INGOT_MASK = """
................
................
..........OO....
.......OOOLTO...
....OOOLTTTTTO..
.OOOLTTTTTTTTTD.
OHTTTTTTTTTTHHSD
OFHTTTTTTHHHFSSD
OFFHTTHHHFFFSSFD
OFFFHHFFFSSSFFFD
OSFFFFSSSSFFFDD.
.OSFFSSSFFDDD...
..OSFSFDDD......
...OODDD........
................
................
"""


def ingot(pal):
    """Lingot : la palette donne, dans l'ordre, O, D, T, L, H, F, S."""
    return from_mask(INGOT_MASK, dict(zip("ODTLHFS", (rgb(c) for c in pal))))


# Palettes des lingots (contour, contour sombre, dessus, dessus clair, arête brillante, face avant, ombre)
STEEL_INGOT = ("#3A4049", "#1B1F25", "#8A95A3", "#A7B2BF", "#C5CED8", "#5F6975", "#48515C")
ALUMINUM_INGOT = ("#6F777F", "#3F464D", "#D5DBE1", "#E9EDF0", "#FFFFFF", "#A6AFB8", "#88919A")
TITANIUM_INGOT = ("#55526A", "#2E2C3B", "#B9B6CC", "#D0CEE0", "#EDEBF7", "#8683A0", "#6A6782")
LITHIUM_INGOT = ("#8A7584", "#564651", "#EADDE5", "#F5EEF2", "#FFFFFF", "#C6B4C0", "#A8959F")
ALUMINUM_LITHIUM_INGOT = ("#56706F", "#2F4443", "#BFD9D8", "#D8EBEA", "#F1FAFA", "#8FB0AF", "#729392")


RAW_MASK = """
................
......oooo......
....oohhllo.....
...ohhhllmlo....
..ohhllmmmmlo...
.ohhllmmsmmmlo..
.ohllmmssmmlmo..
ohllmmmsmmllmso.
ohlmmmmmmlmmsso.
olmmsmmmlmmssso.
ommssmmmmmsssso.
.omsssmmmssssoo.
.osssssmssssoo..
..oossssssooo...
....oooooo......
................
"""


GUIDE_BOOK_MASK = """
................
..oooooooooooo..
.obbbbbbbbbbbbo.
.oblbbbbbbbbbdo.
.oblbbbyybbbbdo.
.oblbbybbybbbdo.
.oblbybwwbybbdo.
.oblbybwwbybbdo.
.oblbbybbybbbdo.
.oblbbbyybbbbdo.
.oblbbbbbbbbbdo.
.obddddddddddbo.
.opppppppppppwo.
.obbbbbbbbbbbbo.
..oooooooooooo..
................
"""
GUIDE_BOOK_PALETTE = {
    "o": "#121A2B", "b": "#2F4A7A", "l": "#4A6BA8", "d": "#20345A",
    "y": "#F2C14E", "w": "#FFFFFF", "p": "#E6E0CF",
}


def item(mask, pal):
    outline, shadow, mid, light, shine = (rgb(c) for c in pal)
    return from_mask(mask, {"o": outline, "s": shadow, "m": mid, "l": light, "h": shine})


# ---------------------------------------------------------------------------
# Palettes (contour/bord, ombre, moyen, clair, reflet)
# ---------------------------------------------------------------------------
STEEL = ("#23272E", "#3E454F", "#5C6672", "#7F8A97", "#A9B3BF")
ALUMINUM = ("#5E666E", "#8C959E", "#B4BCC4", "#D3D9DF", "#F2F5F7")
TITANIUM = ("#3A3846", "#5D5A70", "#8A879E", "#B3B0C6", "#DEDCEB")
LITHIUM = ("#6B5A66", "#9C8A97", "#C9B9C4", "#E6DAE1", "#FFFFFF")
RAW_BAUXITE = ("#4A2413", "#8A4521", "#C46A35", "#E8A56A", "#F7D2A8")
RAW_TITANIUM = ("#23212F", "#4A4760", "#77748F", "#B4B1CC", "#F2F1FA")
RAW_LITHIUM = ("#5A3854", "#96608B", "#D08FBE", "#EDBBDD", "#FDEAF5")


ALUMINUM_LITHIUM = ("#4E6468", "#7C989C", "#A9C4C7", "#CFE3E4", "#F1FAFA")

# Tôle : feuille de métal vue en perspective.
PLATE_MASK = """
................
................
................
....oooooooooo..
...ohhhhhhhhlo..
...ohllllllmso..
..ohllllllmmso..
..ohllllllmso...
.ohllllllmmso...
.ohlllllmmso....
.olmmmmmmsso....
.osssssssso.....
.oooooooooo.....
................
................
................
"""

# ---------------------------------------------------------------------------
# Machines : carcasse commune (e = bord, d = sombre, m = moyen, l = clair, s = reflet)
# et couleurs d'effets (k = noir, r = rouge, o = orange, y = jaune, c = cyan, w = blanc)
# ---------------------------------------------------------------------------
MACHINE_PALETTE = {
    "e": rgb("#2E3238"), "d": rgb("#4A5058"), "m": rgb("#6B737D"), "l": rgb("#8C949E"), "s": rgb("#AEB6BF"),
    "k": rgb("#15171A"), "r": rgb("#B5391E"), "o": rgb("#E8872A"), "y": rgb("#FFD75A"),
    "c": rgb("#9FE8FF"), "w": rgb("#FFFFFF"), "b": rgb("#3F76E4"), "g": rgb("#7CE08A"),
}

MACHINE_SIDE = """
llllllllllllllle
lsmmmmmmmmmmmmde
lmmmmmmmmmmmmmde
lmmmmmmmmmmmmmde
lmmmddddddddmmde
lmmmllllllllmmde
lmmmddddddddmmde
lmmmllllllllmmde
lmmmddddddddmmde
lmmmllllllllmmde
lmmmmmmmmmmmmmde
lmmmmmmmmmmmmmde
lmmmmmmmmmmmmmde
lmsmmmmmmmmsmmde
ldddddddddddddde
eeeeeeeeeeeeeeee
"""

MACHINE_TOP = """
llllllllllllllle
lsmmmmmmmmmmmsde
lmmmmmmmmmmmmmde
lmmddddddddddmde
lmmdllllllllemde
lmmdlmmmmmmdemde
lmmdlmmmmmmdemde
lmmdlmmmmmmdemde
lmmdlmmmmmmdemde
lmmdlmmmmmmdemde
lmmdlmmmmmmdemde
lmmdldddddddemde
lmmdeeeeeeeeemde
lsmmmmmmmmmmmsde
ldddddddddddddde
eeeeeeeeeeeeeeee
"""


def machine_port(color):
    """Côté d'une machine percé d'une sortie de fluide, cerclée de la couleur du fluide."""
    palette = dict(MACHINE_PALETTE)
    palette["p"] = rgb(color)
    return from_mask("""
llllllllllllllle
lsmmmmmmmmmmmmde
lmmmmmmmmmmmmmde
lmmmddddddddmmde
lmmdppppppppdmde
lmmdpkkkkkkpdmde
lmmdpkkkkkkpdmde
lmmdpkkkkkkpdmde
lmmdpkkkkkkpdmde
lmmdpkkkkkkpdmde
lmmdppppppppdmde
lmmmddddddddmmde
lmmmmmmmmmmmmmde
lmsmmmmmmmmsmmde
ldddddddddddddde
eeeeeeeeeeeeeeee
""", palette)


def conduit(dark, mid, light, core, glow=None, level=0):
    """
    Texture d'un conduit, lue par morceaux par les modèles :
    centre (5..10, 5..10) = face du cœur et extrémité des bras ;
    bande du haut (5..10, 0..4) et bande de gauche (0..4, 5..10) = flancs des bras, rayés dans le sens de la longueur.

    glow / level : couleur de la lueur et intensité (0 à 3) quand quelque chose circule dans le conduit.
    Le filet central et le cœur virent vers cette couleur, et le reste du conduit en prend un léger reflet.
    """
    amount = level / 3.0
    if glow is not None and level > 0:
        light = mix(light, glow, 0.35 + 0.65 * amount)
        core = mix(core, glow, 0.45 + 0.55 * amount)
        mid = mix(mid, glow, 0.10 * amount)
    stripe = [rgb(dark), rgb(mid), rgb(light), rgb(light), rgb(mid), rgb(dark)]
    img = [[rgb(mid) for _ in range(SIZE)] for _ in range(SIZE)]
    for i in range(6):
        for j in range(5):
            img[j][5 + i] = stripe[i]       # bande du haut : rayures verticales
            img[5 + i][j] = stripe[i]       # bande de gauche : rayures horizontales
    for y in range(5, 11):
        for x in range(5, 11):
            edge = x in (5, 10) or y in (5, 10)
            img[y][x] = rgb(dark) if edge else rgb(core)
    img[6][6] = rgb(mix(light, "#FFFFFF", 0.5 * amount))  # petit reflet
    return img


def mix(a, b, t):
    """Mélange deux couleurs « #RRGGBB » (t = 0 : la première, t = 1 : la seconde)."""
    ca, cb = rgb(a), rgb(b)
    return "#%02X%02X%02X" % tuple(round(ca[i] + (cb[i] - ca[i]) * t) for i in range(3))


def machine_front(interior):
    """Face avant d'une machine : cadre commun autour d'une fenêtre de 8x7 pixels propre à la machine."""
    assert len(interior) == 7 and all(len(r) == 8 for r in interior), "la fenêtre doit faire 8x7"
    rows = [
        "llllllllllllllle",
        "lsmmmmmmmmmmmmde",
        "lmmmmmmmmmmmmmde",
        "lmmddddddddddmde",
    ]
    rows += ["lmmd" + r + "dmde" for r in interior]
    rows += [
        "lmmddddddddddmde",
        "lmmmmmmmmmmmmmde",
        "lmsmmmmmmmmsmmde",
        "ldddddddddddddde",
        "eeeeeeeeeeeeeeee",
    ]
    return from_mask("\n".join(rows), MACHINE_PALETTE)


# Générateur à combustion : foyer avec grille.
GENERATOR_OFF = ["kkkkkkkk", "kkkkkkkk", "kkkkkkkk", "kkkkkkkk", "kkkkkkkk", "lklklklk", "lklklklk"]
GENERATOR_ON = ["kkkkkkkk", "kkrkkrkk", "krorkork", "royroyor", "oyyooyyo", "lolololo", "lolololo"]
# Fonderie à arc : deux électrodes au-dessus d'un creuset.
FOUNDRY_OFF = ["kkmkkmkk", "kkmkkmkk", "kkmkkmkk", "kkkkkkkk", "kkkkkkkk", "kddddddk", "dddddddd"]
FOUNDRY_ON = ["kkmkkmkk", "kkmkkmkk", "kkmkkmkk", "kkckkckk", "kcwccwck", "oyyyyyyo", "royyyyor"]
# Laminoir : deux cylindres ; une tôle incandescente passe entre eux quand il fonctionne.
ROLLING_OFF = ["ssssssss", "llllllll", "dddddddd", "kkkkkkkk", "ssssssss", "llllllll", "dddddddd"]
ROLLING_ON = ["ssssssss", "llllllll", "dddddddd", "oyyyyyyo", "ssssssss", "llllllll", "dddddddd"]
# Électrolyseur : deux tubes d'eau au-dessus des électrodes ; des bulles montent quand il fonctionne.
ELECTROLYZER_OFF = ["dbbddbbd", "dbbddbbd", "dbbddbbd", "dbbddbbd", "dbbddbbd", "dkkddkkd", "dddddddd"]
ELECTROLYZER_ON = ["dbcddcbd", "dcbddbcd", "dbcddcbd", "dcbddbcd", "dbbddbbd", "dyyddyyd", "dddddddd"]
# Liquéfacteur cryogénique : serpentin de refroidissement, givré quand il fonctionne.
LIQUEFIER_OFF = ["kkkkkkkk", "kssssssk", "kkkkkksk", "kssssssk", "kskkkkkk", "kssssssk", "kkkkkkkk"]
LIQUEFIER_ON = ["kkkkkkkk", "kcccccwk", "kkkkkkck", "kwccccck", "kckkkkkk", "kcccccwk", "kkkkkkkk"]
# Raffinerie : colonne de distillation, chauffée par le bas.
REFINERY_OFF = ["kkdmmdkk", "kkdmmdkk", "kkdmmdkk", "kdmmmmdk", "kdmmmmdk", "kdkkkkdk", "kdkkkkdk"]
REFINERY_ON = ["kkdmmdkk", "kkdmmdkk", "kkdmmdkk", "kdmmmmdk", "kdmmmmdk", "kdoyyodk", "kdyooydk"]
# Terminal de recherche : un écran qui affiche un arbre de technologies, au-dessus d'une rangée de voyants.
TERMINAL_SCREEN = ["bbbbbbbb", "bwcbwcbb", "bbbbbcbb", "bwccbwcw", "bbbbbbbb", "kkkkkkkk", "kykskckk"]
# Réservoir cryogénique : hublot de niveau, bleuté quand le refroidissement est actif.
TANK_OFF = ["dddddddd", "dkkkkkkd", "dkkkkkkd", "dkkkkkkd", "dkkkkkkd", "dkkkkkkd", "dddddddd"]
TANK_ON = ["dddddddd", "dkkkkkkd", "dwwwwwwd", "dccccccd", "dccccccd", "dccccccd", "dddddddd"]


# ---------------------------------------------------------------------------
# Interfaces des machines (images 256x256 : fenêtre 176x166 + éléments animés à droite)
# ---------------------------------------------------------------------------
GUI_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "orbitalis", "textures", "gui")
GUI_FILL = rgb("#C6C6C6")
GUI_DARK = rgb("#373737")
GUI_SHADOW = rgb("#555555")
GUI_SLOT = rgb("#8B8B8B")
GUI_WHITE = rgb("#FFFFFF")
GUI_BLACK = rgb("#000000")

FLAME_MASK = [
    "......o.......",
    ".....oo.......",
    ".....ooo......",
    "....oooo..o...",
    "..o.ooooo.oo..",
    "..ooooooooooo.",
    ".ooooyyooooooo",
    ".oooyyyyoooooo",
    "oooyyyyyyooooo",
    "oooyywwyyyoooo",
    "oooyywwwyyooo.",
    ".ooyyywwyyooo.",
    "..ooyyyyyyoo..",
    "...oooooooo...",
]


def gui_rect(img, x, y, width, height, color):
    for yy in range(y, y + height):
        for xx in range(x, x + width):
            img[yy][xx] = color


def gui_slot(img, x, y, width=18, height=18, inner=GUI_SLOT):
    """Cadre en creux : bord sombre en haut à gauche, bord clair en bas à droite."""
    gui_rect(img, x, y, width, height, inner)
    gui_rect(img, x, y, width - 1, 1, GUI_DARK)
    gui_rect(img, x, y, 1, height - 1, GUI_DARK)
    gui_rect(img, x + 1, y + height - 1, width - 1, 1, GUI_WHITE)
    gui_rect(img, x + width - 1, y + 1, 1, height - 1, GUI_WHITE)


# Couleurs d'accent des emplacements : bleu = entrée, orange = sortie, rouge = combustible.
ACCENT_INPUT = rgb("#4F86D6")
ACCENT_OUTPUT = rgb("#E8872A")
ACCENT_FUEL = rgb("#D9534F")
GUI_SCREEN = rgb("#18221C")       # fond de l'écran d'état
GUI_GAUGE = rgb("#222426")        # fond des jauges vides


def gui_accent_slot(img, x, y, accent, width=18, height=18):
    """Emplacement entouré d'un liseré de couleur indiquant son rôle."""
    gui_rect(img, x - 1, y - 1, width + 2, height + 2, accent)
    gui_slot(img, x, y, width, height)


def gui_bar_frame(img, x, y):
    """Cadre d'une barre horizontale de 32x8 pixels (progression, combustion)."""
    gui_slot(img, x - 1, y - 1, 34, 10, GUI_GAUGE)
    for dx in range(4, 32, 4):
        gui_rect(img, x + dx - 1, y, 1, 8, rgb("#33373A"))


def gui_bar_full(img, x, y, light, dark):
    """Barre pleine en segments de 4 pixels, plus claire en haut."""
    for dx in range(32):
        if dx % 4 == 3:
            continue
        gui_rect(img, x + dx, y, 1, 3, light)
        gui_rect(img, x + dx, y + 3, 1, 5, dark)


def gui_flame(img, x, y, colors):
    for dy, row in enumerate(FLAME_MASK):
        for dx, c in enumerate(row):
            if c != ".":
                img[y + dy][x + dx] = colors[c]


def gui_panel(img, width, height):
    """Panneau avec bord noir, reflet en haut à gauche et ombre en bas à droite."""
    gui_rect(img, 0, 0, width, height, GUI_BLACK)
    gui_rect(img, 1, 1, width - 2, height - 2, GUI_SHADOW)
    gui_rect(img, 1, 1, width - 4, height - 4, GUI_WHITE)
    gui_rect(img, 3, 3, width - 6, height - 6, GUI_FILL)
    for (cx, cy) in [(0, 0), (width - 1, 0), (0, height - 1), (width - 1, height - 1)]:
        img[cy][cx] = TRANSPARENT


def gui_base():
    """Fenêtre commune : panneau, écran d'état, jauge d'énergie à droite, inventaire du joueur."""
    img = [[TRANSPARENT for _ in range(256)] for _ in range(256)]
    gui_panel(img, 176, 166)

    # Jauge d'énergie vide, fine et verticale, sur le bord droit (zone utile 8x52 en 158,17).
    gui_slot(img, 157, 16, 10, 54, GUI_GAUGE)
    for dy in range(4, 52, 4):
        gui_rect(img, 158, 17 + dy - 1, 8, 1, rgb("#33373A"))

    # Écran d'état sombre sous les emplacements (zone utile 142x17 en 8,53).
    gui_slot(img, 7, 52, 144, 19, GUI_SCREEN)

    # Inventaire du joueur (3 rangées) et barre d'accès rapide.
    for row in range(3):
        for col in range(9):
            gui_slot(img, 7 + col * 18, 83 + row * 18)
    for col in range(9):
        gui_slot(img, 7 + col * 18, 141)

    # Éléments animés, rangés à droite de la fenêtre.
    gui_bar_full(img, 176, 0, rgb("#8CF29B"), rgb("#3FBF5A"))      # progression (vert)
    gui_bar_full(img, 176, 8, rgb("#FFD27A"), rgb("#EE8A1E"))      # combustion (orange)
    gui_flame(img, 176, 17, {"o": rgb("#E8872A"), "y": rgb("#FFD75A"), "w": rgb("#FFF6C9")})  # flamme allumée
    top, bottom = rgb("#B6F27A"), rgb("#2FA84A")                     # énergie, en dégradé
    for dy in range(52):
        t = dy / 51.0
        color = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,)
        if dy % 4 == 3:
            color = tuple(int(c * 0.78) for c in color[:3]) + (255,)
        gui_rect(img, 176, 31 + dy, 8, 1, color)
        gui_rect(img, 176, 31 + dy, 1, 1, tuple(min(255, int(c * 1.15)) for c in color[:3]) + (255,))
    return img


def gui_processing(input_count):
    """Machine de transformation : une ou deux entrées, une barre de progression, une sortie."""
    img = gui_base()
    if input_count == 1:
        gui_accent_slot(img, 43, 26, ACCENT_INPUT)
    else:
        gui_accent_slot(img, 34, 26, ACCENT_INPUT)
        gui_accent_slot(img, 52, 26, ACCENT_INPUT)
    gui_bar_frame(img, 77, 31)
    gui_accent_slot(img, 115, 22, ACCENT_OUTPUT, 26, 26)   # cadre large autour de l'emplacement (120, 27)
    return img


def gui_generator():
    """Générateur : un emplacement de combustible, une flamme et une barre de combustion."""
    img = gui_base()
    gui_accent_slot(img, 43, 26, ACCENT_FUEL)
    gui_flame(img, 119, 28, {"o": rgb("#9A9A9A"), "y": rgb("#B4B4B4"), "w": rgb("#B4B4B4")})
    gui_bar_frame(img, 77, 31)
    return img


def gui_gauge(img, x, y, width, height, accent):
    """Jauge de fluide vide : zone utile de la taille donnée, entourée d'un cadre et d'un liseré de couleur."""
    gui_rect(img, x - 2, y - 2, width + 4, height + 4, accent)
    gui_slot(img, x - 1, y - 1, width + 2, height + 2, GUI_GAUGE)
    for dy in range(8, height, 8):
        gui_rect(img, x, y + height - dy, 3, 1, rgb("#4A4E52"))


def gui_fluid_machine(has_item, has_fluid_input, output_count):
    """Machine à fluides : entrée (objet ou jauge) à gauche, barre de progression, jauges de sortie à droite."""
    img = gui_base()
    if has_item:
        # Avec une jauge d'entrée à sa place habituelle, l'emplacement d'objet se range à sa gauche.
        gui_accent_slot(img, 21 if has_fluid_input else 43, 26, ACCENT_INPUT)
    if has_fluid_input:
        gui_gauge(img, 48, 18, 12, 32, ACCENT_INPUT)
    gui_bar_frame(img, 77, 31)
    for i in range(output_count):
        gui_gauge(img, 118 + i * 16, 18, 12, 32, ACCENT_OUTPUT)
    return img


def gui_cryogenic_tank():
    """Réservoir cryogénique : une grande jauge."""
    img = gui_base()
    gui_gauge(img, 44, 18, 88, 32, rgb("#5FB0F5"))
    return img


# ---------------------------------------------------------------------------
# Fusées (les textures du pas de tir sont écrites par generate_launch_pad.py)
# ---------------------------------------------------------------------------
def rocket_debris_texture():
    img = blotchy(186, [rgb(c) for c in ("#2B2C2E", "#1F2022", "#3A3B3D", "#4A4038")], [5, 4, 2, 1], passes=2)
    for x, y in ((2, 3), (3, 3), (3, 4), (4, 4), (10, 10), (11, 10), (11, 11), (12, 12), (6, 12), (7, 12)):
        img[y][x] = rgb("#C9CDD1")
    for x, y in ((8, 2), (9, 3), (9, 4), (10, 5), (4, 9), (5, 10), (5, 11), (13, 6), (13, 7)):
        img[y][x] = rgb("#0E0F10")
    for x, y in ((12, 3), (2, 13)):
        img[y][x] = rgb("#E8742A")
    return img


def rocket_skin(seed, shades, weights, seams, bands=()):
    """Peau d'un tronçon de fusée : teinte de base légèrement nuancée, joints horizontaux, bandes de couleur."""
    img = blotchy(seed, [rgb(c) for c in shades], weights, passes=1)
    for y in seams:
        for x in range(SIZE):
            img[y][x] = tuple(int(c * 0.86) for c in img[y][x][:3]) + (255,)
    for y, color in bands:
        for x in range(SIZE):
            img[y][x] = rgb(color)
    return img


def rocket_fairing_texture():
    """Coiffe : blanche, avec le joint vertical de ses deux demi-coquilles et un liseré bleu."""
    img = rocket_skin(84, ["#F4F5F6", "#E8EAEC", "#FBFBFC"], [5, 3, 2], [])
    for y in range(SIZE):
        img[y][7] = img[y][8] = rgb("#C9CDD1")
    for x in range(SIZE):
        img[14][x] = rgb("#3F76E4")
    return img


def rocket_nozzle_texture():
    """Tuyère : métal sombre, plus clair vers le bord de sortie."""
    img = [[rgb("#2E3238") for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            if (x + y) % 5 == 0:
                img[y][x] = rgb("#3E454F")
    for x in range(SIZE):
        img[13][x] = rgb("#8A4521")
        img[14][x] = rgb("#C46A35")
        img[15][x] = rgb("#5C6672")
    return img


def rocket_steel_texture():
    """Acier inoxydable poli : des viroles soudées, un reflet vertical clair et son ombre."""
    img = rocket_skin(86, ["#B4BCC5", "#A7AFB9", "#C2C9D1"], [5, 3, 2], [0, 5, 10])
    for y in range(SIZE):
        for x, factor in ((4, 1.12), (5, 1.2), (6, 1.1), (11, 0.9), (12, 0.86)):
            img[y][x] = tuple(min(255, int(c * factor)) for c in img[y][x][:3]) + (255,)
    return img


def rocket_checker_texture():
    """Damier noir et blanc de repérage : il permet de suivre le roulis de la fusée à l'œil."""
    img = rocket_skin(88, ["#EEF0F2", "#E2E5E8", "#F8F9FA"], [5, 3, 2], [0])
    for y in range(4, 12):
        for x in range(SIZE):
            if ((x // 4) + (y // 4)) % 2 == 0:
                img[y][x] = rgb("#24272B")
    return img


def rocket_copper_nozzle_texture():
    """Tuyère refroidie par ses ergols : des tubes de cuivre côte à côte, noircis vers le bord de sortie."""
    img = [[rgb("#C46A35") for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            img[y][x] = rgb("#E8A56A") if x % 2 == 0 else rgb("#A85525")
            if y >= 12:
                img[y][x] = tuple(int(c * (0.75 - 0.08 * (y - 12))) for c in img[y][x][:3]) + (255,)
    return img


def recovery_remote_item():
    """Télécommande de récupération : un boîtier jaune de chantier, une antenne, un écran et un gros bouton rouge."""
    img = part_canvas()
    body = ("#3A2E0A", "#8A6A12", "#D9A93A", "#F2CC5E", "#FFE9A6")
    part_cap(img, 4, 4, 11, body)
    for y in range(5, 14):
        part_row(img, y, 4, 11, body)
    part_cap(img, 14, 4, 11, body)
    for y in (6, 7):
        for x in range(6, 10):
            img[y][x] = rgb("#0C1410")
    img[6][6] = img[7][8] = rgb("#7CE08A")
    for y in (10, 11):
        for x in (7, 8):
            img[y][x] = rgb("#D9534F")
    img[10][7] = rgb("#FF8A80")
    for y in range(1, 4):
        img[y][10] = rgb(DARK_METAL[2])
    img[0][10] = rgb("#D9534F")
    return img


def mission_tablet_item():
    """Tablette de mission : un boîtier graphite aux coins arrondis, et à l'écran une carte (la mer, des terres, une
    plage, le pas de tir en rouge) qu'un satellite survole ; un bouton en bas, un voyant vert de liaison."""
    img = part_canvas()
    outline, body, light = rgb(DARK_METAL[0]), rgb(DARK_METAL[1]), rgb(DARK_METAL[2])
    for y in range(1, 15):
        for x in range(2, 14):
            corner = (x in (2, 13)) and (y in (1, 14))
            if corner:
                continue
            edge = x in (2, 13) or y in (1, 14) or ((x in (3, 12)) and (y in (1, 14))) or ((y in (2, 13)) and (x in (2, 13)))
            img[y][x] = outline if edge else body
    # Le reflet du boîtier, en haut et à gauche.
    for x in range(4, 12):
        img[2][x] = light
    for y in range(3, 13):
        img[y][3] = light
    # L'écran : la carte.
    sea, deep, land, forest, sand = rgb("#4389D4"), rgb("#3477C4"), rgb("#7FBF4E"), rgb("#2F9440"), rgb("#EDDDA6")
    screen = [
        "ddssssssd",
        "dsslllssd",
        "sslllffss",
        "sllffflss",
        "sslfrflls",
        "ssllllyss",
        "dsssyyssd",
        "ddsssssdd",
        "dddssdddd",
    ]
    colors = {"s": sea, "d": deep, "l": land, "f": forest, "y": sand, "r": rgb("#E0453A")}
    for row, line in enumerate(screen):
        for col, key in enumerate(line):
            img[3 + row][4 + col] = colors[key]
    # Un satellite qui passe, en haut à droite de l'écran.
    img[3][11] = rgb("#FFFFFF")
    img[4][12] = rgb("#7FD8FF")
    # Le bouton, et le voyant de liaison.
    img[13][7] = img[13][8] = rgb(DARK_METAL[4])
    img[13][11] = rgb("#4ADE80")
    return img


# Pupitre de lancement : un écran vert de télémétrie au-dessus d'une rangée de voyants.
CONSOLE_SCREEN = ["kkkkkkkk", "kgggkggk", "kkkkkkkk", "kggkgggk", "kkkkkkkk", "kykgkrkk", "kkkkkkkk"]


# ---------------------------------------------------------------------------
# Pièces de fusée : corps cylindriques vus de côté, éclairés par la gauche
# ---------------------------------------------------------------------------
WHITE_PAINT = ("#3A3D42", "#9AA0A6", "#D4D8DC", "#EEF0F2", "#FFFFFF")
GOLD_FOIL = ("#5A4310", "#A8801F", "#D9A93A", "#F2CC5E", "#FFE9A6")
DARK_METAL = ("#15171A", "#2E3238", "#4A5058", "#6B737D", "#8C949E")
COPPER = ("#4A2413", "#8A4521", "#C46A35", "#E8A56A", "#F7D2A8")


def part_canvas():
    return [[TRANSPARENT for _ in range(SIZE)] for _ in range(SIZE)]


def part_row(img, y, x0, x1, pal):
    """Une rangée d'un cylindre : contour aux deux bouts, reflet à gauche, ombre à droite."""
    outline, shadow, mid, light, shine = (rgb(c) for c in pal)
    for x in range(x0, x1 + 1):
        t = (x - x0) / max(1, x1 - x0)
        if x in (x0, x1):
            img[y][x] = outline
        elif t < 0.3:
            img[y][x] = light
        elif t < 0.45:
            img[y][x] = shine
        elif t < 0.75:
            img[y][x] = mid
        else:
            img[y][x] = shadow


def part_cap(img, y, x0, x1, pal):
    """Rangée de contour qui ferme une forme en haut ou en bas."""
    for x in range(x0, x1 + 1):
        img[y][x] = rgb(pal[0])


def part_band(img, y, x0, x1, color):
    """Bande de couleur en travers d'un cylindre, sans toucher au contour."""
    for x in range(x0 + 1, x1):
        img[y][x] = rgb(color)


def tank_item(pal):
    """Réservoir : cylindre à fonds bombés, avec deux cerclages."""
    img = part_canvas()
    part_cap(img, 1, 6, 9, pal)
    part_row(img, 2, 5, 10, pal)
    for y in range(3, 13):
        part_row(img, y, 4, 11, pal)
    part_row(img, 13, 5, 10, pal)
    part_cap(img, 14, 6, 9, pal)
    part_band(img, 5, 4, 11, pal[1])
    part_band(img, 10, 4, 11, pal[1])
    return img


def engine_item(body, bell, accent, widths):
    """Moteur : bloc de pompes en haut, col étroit, puis tuyère qui s'évase. widths = demi-largeurs de la tuyère."""
    img = part_canvas()
    part_cap(img, 1, 5, 10, body)
    for y in range(2, 5):
        part_row(img, y, 5, 10, body)
    part_band(img, 3, 5, 10, accent)
    part_row(img, 5, 6, 9, body)
    part_row(img, 6, 6, 9, bell)
    for i, half in enumerate(widths):
        part_row(img, 7 + i, 8 - half, 7 + half, bell)
    part_cap(img, 7 + len(widths), 8 - widths[-1], 7 + widths[-1], bell)
    return img


def solid_motor_item():
    """Moteur à poudre : tube blanc à bandes, petite tuyère et ailerons."""
    img = part_canvas()
    part_cap(img, 1, 6, 9, WHITE_PAINT)
    for y in range(2, 12):
        part_row(img, y, 6, 9, WHITE_PAINT)
    part_band(img, 4, 6, 9, "#22252A")
    part_band(img, 9, 6, 9, "#B5391E")
    part_row(img, 12, 5, 10, DARK_METAL)
    part_row(img, 13, 5, 10, DARK_METAL)
    part_cap(img, 14, 5, 10, DARK_METAL)
    for y in (10, 11):
        img[y][5] = img[y][10] = rgb(WHITE_PAINT[0])
    return img


def nose_cone_item():
    """Coiffe : ogive blanche, avec une bande rouge à la base."""
    img = part_canvas()
    for i, half in enumerate([0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5]):
        part_row(img, 2 + i, 7 - half, 8 + half, WHITE_PAINT)
    part_cap(img, 14, 2, 13, WHITE_PAINT)
    part_band(img, 12, 2, 13, "#B5391E")
    return img


def interstage_item():
    """Interétage : anneau en treillis."""
    img = part_canvas()
    part_cap(img, 4, 3, 12, ALUMINUM)
    for y in range(5, 11):
        part_row(img, y, 3, 12, ALUMINUM)
    part_cap(img, 11, 3, 12, ALUMINUM)
    for y in range(6, 10):
        for x in range(4, 12):
            if (x + y) % 3 == 0:
                img[y][x] = rgb(ALUMINUM[0])
    return img


def sounding_payload_item():
    """Charge utile de fusée-sonde : boîtier doré, hublot d'instrument et antenne."""
    img = part_canvas()
    part_cap(img, 4, 4, 11, GOLD_FOIL)
    for y in range(5, 13):
        part_row(img, y, 4, 11, GOLD_FOIL)
    part_cap(img, 13, 4, 11, GOLD_FOIL)
    for y in range(7, 11):
        for x in range(6, 10):
            img[y][x] = rgb("#3F76E4")
    img[7][6] = rgb("#9FE8FF")
    for y in range(2, 4):
        img[y][8] = rgb(STEEL[0])
    img[1][8] = rgb("#D9534F")
    return img


SOLAR_DARK = "#1F3F8F"
SOLAR_LIGHT = "#3F76E4"


def solar_panel(img, x0, x1, y0, y1):
    """Panneau solaire : un cadre sombre, des cellules bleues, et une traverse au milieu."""
    middle = (y0 + y1) // 2
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            edge = x in (x0, x1) or y in (y0, y1) or y == middle
            img[y][x] = rgb(SOLAR_DARK if edge else SOLAR_LIGHT)


def satellite_bus(pal, x0=6, x1=9, y0=4, y1=12, panel_rows=(6, 10)):
    """Corps d'un satellite classique : un caisson, et deux panneaux solaires reliés par un bras."""
    img = part_canvas()
    part_cap(img, y0, x0, x1, pal)
    for y in range(y0 + 1, y1):
        part_row(img, y, x0, x1, pal)
    part_cap(img, y1, x0, x1, pal)
    solar_panel(img, 0, x0 - 2, panel_rows[0], panel_rows[1])
    solar_panel(img, x1 + 2, SIZE - 1, panel_rows[0], panel_rows[1])
    middle = (panel_rows[0] + panel_rows[1]) // 2
    img[middle][x0 - 1] = img[middle][x1 + 1] = rgb(DARK_METAL[1])
    return img


def test_satellite_item():
    """Premier satellite : une sphère polie et quatre antennes fouets rabattues vers l'arrière."""
    img = part_canvas()
    part_cap(img, 3, 6, 9, ALUMINUM)
    for i, half in enumerate([3, 4, 4, 4, 4, 3]):
        part_row(img, 4 + i, 8 - half, 7 + half, ALUMINUM)
    part_cap(img, 10, 6, 9, ALUMINUM)
    antenna = rgb(ALUMINUM[2])
    for i in range(1, 5):
        img[10 + i][5 - i] = antenna
        img[10 + i][10 + i] = antenna
    for y in (11, 12, 13):
        img[y][7 - (y - 11) // 2] = antenna
        img[y][8 + (y - 11) // 2] = antenna
    return img


def weather_satellite_item():
    """Satellite météo : caisson blanc, radiomètre bleu clair et antenne."""
    img = satellite_bus(WHITE_PAINT)
    for y in (8, 9):
        for x in (7, 8):
            img[y][x] = rgb("#7FD4F5")
    img[8][7] = rgb("#DFF6FF")
    img[3][7] = img[2][7] = rgb(STEEL[1])
    img[1][7] = rgb("#D9534F")
    return img


def communication_satellite_item():
    """Satellite de télécommunications : caisson doré, coiffé d'une grande parabole."""
    img = satellite_bus(GOLD_FOIL, y0=6, y1=13, panel_rows=(8, 11))
    part_cap(img, 1, 4, 11, WHITE_PAINT)
    part_row(img, 2, 5, 10, WHITE_PAINT)
    part_row(img, 3, 6, 9, WHITE_PAINT)
    img[4][7] = img[4][8] = img[5][7] = img[5][8] = rgb(DARK_METAL[1])
    return img


def observation_satellite_item():
    """Satellite d'observation : caisson doré et objectif tourné vers le sol."""
    img = satellite_bus(GOLD_FOIL, y0=2, y1=10, panel_rows=(4, 8))
    part_row(img, 11, 6, 9, DARK_METAL)
    part_row(img, 12, 6, 9, DARK_METAL)
    part_cap(img, 13, 6, 9, DARK_METAL)
    img[13][7] = rgb("#3F76E4")
    img[13][8] = rgb("#9FE8FF")
    return img


def navigation_satellite_item():
    """Satellite de navigation : caisson clair à bande orange, et sa rangée d'antennes tournées vers le sol."""
    img = satellite_bus(ALUMINUM, y0=3, y1=11, panel_rows=(5, 9))
    part_band(img, 5, 6, 9, "#E8872A")
    for x in (6, 8, 9):
        img[12][x] = rgb(STEEL[1])
        img[13][x] = rgb(STEEL[0])
    return img


def space_telescope_item():
    """Télescope spatial : un long tube argenté, ouvert en haut, et deux panneaux solaires."""
    img = part_canvas()
    part_cap(img, 1, 5, 10, ALUMINUM)
    for y in range(2, 14):
        part_row(img, y, 5, 10, ALUMINUM)
    part_cap(img, 14, 6, 9, ALUMINUM)
    # Ouverture du tube et sa porte relevée.
    for x in range(6, 10):
        img[2][x] = rgb("#15171A")
    img[0][4] = img[1][4] = rgb(ALUMINUM[1])
    part_band(img, 9, 5, 10, "#C79BF2")
    solar_panel(img, 0, 3, 5, 11)
    solar_panel(img, 12, 15, 5, 11)
    img[8][4] = img[8][11] = rgb(DARK_METAL[1])
    return img


def crew_capsule_item():
    """Capsule habitée : cabine conique blanche, hublot, et bouclier thermique sombre à la base."""
    img = part_canvas()
    part_cap(img, 2, 6, 9, WHITE_PAINT)
    for i, half in enumerate([2, 2, 3, 3, 4, 4, 5, 5, 6]):
        part_row(img, 3 + i, 8 - half, 7 + half, WHITE_PAINT)
    part_row(img, 12, 1, 14, DARK_METAL)
    part_cap(img, 13, 2, 13, DARK_METAL)
    for y in (7, 8):
        for x in (7, 8):
            img[y][x] = rgb("#3F76E4")
    img[7][7] = rgb("#9FE8FF")
    part_band(img, 10, 3, 12, "#B5391E")
    return img


def spacesuit_item():
    """Combinaison spatiale : casque à visière dorée, torse blanc, boîtier de commande et sac de survie."""
    img = part_canvas()
    # Casque.
    part_cap(img, 1, 6, 9, WHITE_PAINT)
    for y in range(2, 6):
        part_row(img, y, 5, 10, WHITE_PAINT)
    for y in (3, 4):
        for x in range(6, 10):
            img[y][x] = rgb(GOLD_FOIL[2])
    img[3][6] = rgb(GOLD_FOIL[4])
    # Torse et bras.
    for y in range(6, 13):
        part_row(img, y, 4, 11, WHITE_PAINT)
    for y in range(7, 12):
        part_row(img, y, 2, 3, WHITE_PAINT)
        part_row(img, y, 12, 13, WHITE_PAINT)
    part_cap(img, 13, 4, 11, WHITE_PAINT)
    # Boîtier de commande sur la poitrine, et bandes rouges du commandant.
    for x in range(6, 10):
        img[8][x] = rgb(DARK_METAL[1])
        img[9][x] = rgb(DARK_METAL[2])
    img[8][7] = rgb("#5BC27A")
    img[8][8] = rgb("#D9534F")
    part_band(img, 11, 4, 11, "#B5391E")
    return img


def station_hull_texture():
    """Coque de station : un panneau blanc cassé, bordé d'un joint, avec un rivet à chaque angle."""
    img = rocket_skin(110, ["#E9EBED", "#DDE0E3", "#F4F5F6"], [5, 3, 2], [])
    for k in range(SIZE):
        img[0][k] = img[SIZE - 1][k] = img[k][0] = img[k][SIZE - 1] = rgb("#B4B9BF")
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        img[y][x] = rgb("#8C949E")
    return img


def station_window_texture():
    """Hublot de station : un cadre de coque, et un verre presque transparent avec son reflet."""
    img = [[(150, 195, 230, 34) for _ in range(SIZE)] for _ in range(SIZE)]
    for k in range(SIZE):
        img[0][k] = img[SIZE - 1][k] = img[k][0] = img[k][SIZE - 1] = rgb("#B4B9BF")
        if 1 <= k <= SIZE - 2:
            img[1][k] = img[SIZE - 2][k] = img[k][1] = img[k][SIZE - 2] = rgb("#3A4048")
    for k in range(3, 9):
        img[k][11 - k] = (240, 250, 255, 115)
    return img


def docking_port_texture():
    """Sas d'amarrage : un collier sombre autour d'une écoutille ronde, avec son verrou central."""
    img = station_hull_texture()
    for y in range(SIZE):
        for x in range(SIZE):
            distance = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if distance <= 6.2:
                img[y][x] = rgb("#3A4048") if distance > 4.8 else rgb("#6B747F") if distance > 1.6 else rgb("#E8872A")
    for k in range(4, 12):
        img[k][7] = img[7][k] = rgb("#4A515A")
    return img


def solar_panel_top_texture():
    """Dessus du panneau solaire : des cellules bleu nuit, séparées par des conducteurs argentés."""
    img = [[rgb("#1F3F8F") for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            if x % 4 == 0 or y % 4 == 0:
                img[y][x] = rgb("#9AA3AE")
            elif (x % 4 == 1 and y % 4 == 1):
                img[y][x] = rgb("#3F76E4")
    return img


def solar_panel_side_texture():
    """Flanc du panneau solaire : la coque de station, coiffée de la tranche bleue des cellules."""
    img = station_hull_texture()
    for x in range(SIZE):
        img[0][x] = rgb("#9AA3AE")
        img[1][x] = rgb("#1F3F8F")
        img[2][x] = rgb("#3A4048")
    return img


def shade(color, factor):
    """Éclaircit (au-dessus de 1) ou assombrit (en dessous) une couleur, sans toucher à son opacité."""
    r, g, b, a = color
    return (min(255, max(0, round(r * factor))), min(255, max(0, round(g * factor))), min(255, max(0, round(b * factor))), a)


def life_support_texture(active):
    """
    Générateur d'air, sur ses six faces : un panneau blanc en relief (éclairé en haut et à gauche, ombré en bas et à
    droite), la grille de son ventilateur en creux (une lèvre éclairée d'un côté, dans l'ombre de l'autre, des pales
    au fond, le moyeu), et sous la grille, bien à l'écart des bords, la petite fenêtre de l'afficheur et son voyant :
    rouge à l'arrêt, vert quand la pièce est pressurisée.
    """
    img = [[rgb("#E6E9EC") for _ in range(SIZE)] for _ in range(SIZE)]
    rnd = random.Random(1109)
    for y in range(SIZE):
        for x in range(SIZE):
            img[y][x] = shade(img[y][x], 1.0 + rnd.uniform(-0.018, 0.018))
    # Le joint du panneau, puis son relief.
    for k in range(SIZE):
        img[0][k] = img[15][k] = img[k][0] = img[k][15] = rgb("#A9B0B8")
    for k in range(1, 15):
        img[1][k] = shade(img[1][k], 1.06)
        img[k][1] = shade(img[k][1], 1.05)
        img[14][k] = shade(img[14][k], 0.84)
        img[k][14] = shade(img[k][14], 0.86)
    # La grille du ventilateur, en creux.
    cx, cy = 7.5, 6.5
    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = x - cx, y - cy
            d = math.hypot(dx, dy)
            if d <= 4.9:
                if d > 4.1:
                    # La lèvre : éclairée en haut à gauche, dans l'ombre en bas à droite.
                    light = -(dx + dy) / (d or 1.0)
                    img[y][x] = shade(rgb("#B9C0C7"), 1.0 + 0.18 * light)
                else:
                    depth = 0.55 + 0.1 * (d / 4.1)
                    angle = math.atan2(dy, dx)
                    blade = (math.sin(angle * 4 + d * 0.6) > 0.35)
                    base = rgb("#5A626C") if blade else rgb("#22272C")
                    img[y][x] = shade(base, depth / 0.6)
                    if (x + y) % 2 == 0 and not blade:
                        img[y][x] = rgb("#2E343A")
            if d <= 1.1:
                img[y][x] = rgb("#C9CED4") if dx + dy < 0 else rgb("#8C949E")
    # L'ombre portée de la lèvre, sous la grille.
    for x in range(5, 11):
        img[11][x] = shade(img[11][x], 0.9)
    # L'afficheur et le voyant, dans leur cadre en creux, à trois pixels des bords.
    for x in range(4, 12):
        img[12][x] = rgb("#5A626C")
    for x in range(4, 12):
        img[13][x] = rgb("#C9CED4") if x in (4, 11) else rgb("#0E1A20")
    if active:
        img[13][5] = rgb("#39B5C9")
        img[13][6] = rgb("#39B5C9")
    img[13][9] = rgb("#5BC27A") if active else rgb("#7A2A26")
    img[13][10] = rgb("#B6FFC9") if active else rgb("#B5443C")
    return img


def station_core_item():
    """Module central de station : un caisson blanc à hublot, son sas sur le côté et ses deux ailes solaires."""
    img = part_canvas()
    part_cap(img, 3, 4, 11, WHITE_PAINT)
    for y in range(4, 13):
        part_row(img, y, 4, 11, WHITE_PAINT)
    part_cap(img, 13, 4, 11, WHITE_PAINT)
    for y in (6, 7, 8):
        for x in (6, 7, 8, 9):
            img[y][x] = rgb("#3F76E4")
    img[6][6] = rgb("#9FE8FF")
    for x in range(6, 10):
        img[11][x] = rgb(DARK_METAL[1])
    solar_panel(img, 0, 2, 5, 10)
    solar_panel(img, 13, 15, 5, 10)
    img[7][3] = img[7][12] = rgb(DARK_METAL[1])
    return img


def moon_regolith_texture():
    """Régolithe lunaire : une poussière gris clair, semée de petits cailloux et de micro-cratères."""
    img = blotchy(120, [rgb("#A9ABAE"), rgb("#9A9CA0"), rgb("#B6B8BB"), rgb("#8C8E92")], [5, 3, 2, 1], passes=2)
    for x, y in ((3, 4), (11, 2), (7, 10), (13, 12)):
        img[y][x] = rgb("#77797D")
        img[y][(x + 1) % SIZE] = rgb("#C6C8CB")
    return img


def moon_rock_texture():
    """Roche lunaire : un basalte gris sombre, fissuré."""
    img = blotchy(121, [rgb("#6E7074"), rgb("#62646A"), rgb("#7A7C80"), rgb("#56585D")], [5, 3, 2, 2], passes=2)
    for x, y in ((2, 3), (3, 4), (4, 4), (5, 5), (10, 9), (11, 10), (12, 10), (12, 11), (6, 13), (7, 12)):
        img[y][x] = rgb("#3F4146")
    return img


def mars_regolith_texture():
    """Régolithe martien : une poussière rouille, plus claire par endroits, semée de petits cailloux sombres."""
    img = blotchy(130, [rgb("#B5613A"), rgb("#A65432"), rgb("#C26E44"), rgb("#96492C")], [5, 3, 2, 1], passes=2)
    for x, y in ((2, 5), (10, 3), (6, 11), (13, 13)):
        img[y][x] = rgb("#6E3520")
        img[y][(x + 1) % SIZE] = rgb("#D0825A")
    return img


def mars_rock_texture():
    """Roche martienne : un basalte brun-rouge, fissuré."""
    img = blotchy(131, [rgb("#7A4634"), rgb("#6C3D2E"), rgb("#86503C"), rgb("#5E3528")], [5, 3, 2, 2], passes=2)
    for x, y in ((3, 2), (4, 3), (5, 3), (6, 4), (9, 10), (10, 11), (11, 11), (11, 12), (5, 13), (6, 12)):
        img[y][x] = rgb("#40241B")
    return img


def mars_lander_item():
    """Vaisseau martien : la capsule blanche derrière son bouclier ocre, sur un étage d'atterrissage sombre à quatre jambes."""
    img = lunar_module_item()
    for y in range(7, 12):
        for x in range(3, 13):
            if img[y][x][3]:
                img[y][x] = rgb("#B5613A") if (x + y) % 3 else rgb("#96492C")
    for x in range(3, 13):
        img[7][x] = rgb("#D0825A")
    return img


def lunar_ice_texture():
    """Glace lunaire : une glace bleu pâle, sale, mêlée de poussière grise."""
    img = blotchy(122, [rgb("#B9D6EA"), rgb("#A4C6DE"), rgb("#CDE3F2"), rgb("#9A9CA0")], [5, 3, 2, 1], passes=2)
    for k in range(3, 12):
        img[k][14 - k] = rgb("#E6F3FB")
    return img


def lunar_module_item():
    """Vaisseau lunaire : la capsule blanche sur son étage d'alunissage doré, avec ses quatre jambes."""
    img = part_canvas()
    part_cap(img, 1, 6, 9, WHITE_PAINT)
    for i, half in enumerate([2, 2, 3, 3, 4]):
        part_row(img, 2 + i, 8 - half, 7 + half, WHITE_PAINT)
    for y in (4, 5):
        for x in (7, 8):
            img[y][x] = rgb("#3F76E4")
    for y in range(7, 11):
        part_row(img, y, 3, 12, GOLD_FOIL)
    part_cap(img, 11, 3, 12, GOLD_FOIL)
    for k in range(4):
        img[11 + k][2 - min(k, 2)] = img[11 + k][13 + min(k, 2)] = rgb(DARK_METAL[2])
    for x in (0, 1, 14, 15):
        img[15][x] = rgb(DARK_METAL[3])
    for y in (12, 13):
        for x in (7, 8):
            img[y][x] = rgb(DARK_METAL[1])
    return img


def bench_top():
    """Dessus de l'atelier d'assemblage : un plan de fusée sur papier bleu, dans un cadre de machine."""
    img = [[rgb("#2B4C7E") for _ in range(SIZE)] for _ in range(SIZE)]
    for i in range(1, SIZE - 1):
        for line in (4, 8, 12):
            img[line][i] = img[i][line] = rgb("#3A62A0")
    white = rgb("#DCE9FF")
    for y in range(5, 12):
        img[y][6] = img[y][9] = white
    for x, y in [(7, 3), (8, 3), (6, 4), (9, 4), (5, 11), (10, 11), (5, 12), (10, 12), (6, 12), (7, 12), (8, 12), (9, 12)]:
        img[y][x] = white
    for i in range(SIZE):
        img[0][i] = img[i][0] = MACHINE_PALETTE["l"]
        img[SIZE - 1][i] = img[i][SIZE - 1] = MACHINE_PALETTE["e"]
    return img


def creative_source_texture(symbol, color, light):
    """Bloc de test : carcasse de machine violette, avec au centre un symbole (éclair ou goutte)."""
    purple = {"e": rgb("#2A1238"), "d": rgb("#4A2466"), "m": rgb("#7A3FA6"), "l": rgb("#A66AD0"), "s": rgb("#D2A8F0"),
              "k": rgb("#15171A")}
    img = from_mask(MACHINE_SIDE, purple)
    for y in range(4, 12):
        for x in range(4, 12):
            img[y][x] = rgb("#1A0B24")
    for y, row in enumerate(symbol):
        for x, c in enumerate(row):
            if c == "x":
                img[5 + y][5 + x] = rgb(color)
            elif c == "o":
                img[5 + y][5 + x] = rgb(light)
    return img


ENERGY_SYMBOL = ["...xo.", "..xo..", ".xxxo.", "..xo..", ".xo...", "xo...."]
FLUID_SYMBOL = ["..x...", "..xx..", ".xoxx.", ".xoxx.", ".xxxx.", "..xx.."]


def dossier_item():
    """Dossier de recherche créatif : une chemise violette marquée d'une étoile."""
    img = part_canvas()
    folder = ("#2A1238", "#4A2466", "#7A3FA6", "#A66AD0", "#D2A8F0")
    part_cap(img, 2, 3, 12, folder)
    for y in range(3, 13):
        part_row(img, y, 3, 12, folder)
    part_cap(img, 13, 3, 12, folder)
    for x, y in [(7, 5), (8, 5), (6, 7), (7, 7), (8, 7), (9, 7), (7, 6), (8, 6), (7, 8), (8, 8), (6, 9), (9, 9)]:
        img[y][x] = rgb("#FFD75A")
    return img


def rocket_item():
    """Fusée assemblée : une petite fusée blanche à pointe rouge, avec ses ailerons."""
    img = part_canvas()
    for i, half in enumerate([0, 0, 1, 1]):
        part_row(img, 1 + i, 7 - half, 8 + half, ("#5A1C10", "#8A2B18", "#B5391E", "#D9654A", "#F09A84"))
    for y in range(5, 13):
        part_row(img, y, 6, 9, WHITE_PAINT)
    part_band(img, 8, 6, 9, "#22252A")
    for y, half in ((11, 3), (12, 4), (13, 4)):
        part_row(img, y, 7 - half, 8 + half, DARK_METAL)
    for y in range(11, 14):
        part_row(img, y, 6, 9, WHITE_PAINT)
    part_cap(img, 14, 6, 9, DARK_METAL)
    return img


def gui_rocket_assembly():
    """Atelier d'assemblage : les pièces à gauche (charge utile et coiffe en haut, premier étage en bas),
    un grand écran d'état à droite, l'inventaire du joueur en dessous. Fenêtre de 248x198."""
    img = [[TRANSPARENT for _ in range(256)] for _ in range(256)]
    gui_panel(img, 248, 198)
    gui_accent_slot(img, 25, 17, ACCENT_OUTPUT)             # charge utile
    gui_accent_slot(img, 45, 17, rgb("#F2F2F2"))            # coiffe
    for stage in range(3):
        y = 79 - stage * 20
        gui_accent_slot(img, 25, y, ACCENT_FUEL)            # moteurs
        gui_accent_slot(img, 45, y, ACCENT_INPUT)           # réservoirs
        if stage > 0:
            gui_accent_slot(img, 65, y, rgb("#6B737D"))     # interétage
    gui_slot(img, 91, 16, 150, 84, GUI_SCREEN)              # écran d'état
    for row in range(3):
        for col in range(9):
            gui_slot(img, 42 + col * 18, 115 + row * 18)
    for col in range(9):
        gui_slot(img, 42 + col * 18, 173)
    return img


def build_all():
    blocks = {
        # Bauxite : nodules brun-rouge. Titane : cristaux gris-violet. Lithium : cristaux rose-lilas.
        "bauxite_ore": ore(stone_base(11), ORE_MASK, "#E8A56A", "#C46A35", "#7A3F22"),
        "deepslate_bauxite_ore": ore(deepslate_base(12), ORE_MASK, "#E8A56A", "#C46A35", "#7A3F22"),
        "titanium_ore": ore(stone_base(21), flip_h(ORE_MASK), "#F2F1FA", "#B4B1CC", "#5D5A75"),
        "deepslate_titanium_ore": ore(deepslate_base(22), flip_h(ORE_MASK), "#F2F1FA", "#B4B1CC", "#5D5A75"),
        "lithium_ore": ore(stone_base(31), transpose(ORE_MASK), "#FBE6F3", "#E3A6D0", "#96608B"),
        "deepslate_lithium_ore": ore(deepslate_base(32), transpose(ORE_MASK), "#FBE6F3", "#E3A6D0", "#96608B"),
        "steel_block": metal_block(41, STEEL, "rivets"),
        "aluminum_block": metal_block(42, ALUMINUM, "brushed"),
        "titanium_block": metal_block(43, TITANIUM, "cross"),
        # Machines : carcasse commune et faces avant (éteinte / allumée).
        "machine_side": from_mask(MACHINE_SIDE, MACHINE_PALETTE),
        "machine_top": from_mask(MACHINE_TOP, MACHINE_PALETTE),
        "machine_bottom": metal_block(51, ("#1E2126", "#2E3238", "#4A5058", "#5A626B", "#6B737D"), "plain"),
        "combustion_generator_front": machine_front(GENERATOR_OFF),
        "combustion_generator_front_on": machine_front(GENERATOR_ON),
        "arc_foundry_front": machine_front(FOUNDRY_OFF),
        "arc_foundry_front_on": machine_front(FOUNDRY_ON),
        "rolling_mill_front": machine_front(ROLLING_OFF),
        "rolling_mill_front_on": machine_front(ROLLING_ON),
        "electrolyzer_front": machine_front(ELECTROLYZER_OFF),
        "electrolyzer_front_on": machine_front(ELECTROLYZER_ON),
        "cryogenic_liquefier_front": machine_front(LIQUEFIER_OFF),
        "cryogenic_liquefier_front_on": machine_front(LIQUEFIER_ON),
        "refinery_front": machine_front(REFINERY_OFF),
        "refinery_front_on": machine_front(REFINERY_ON),
        "research_terminal_front": machine_front(TERMINAL_SCREEN),
        # Tronçons de fusée : peinture blanche, mousse orange, moteur à poudre, interétage en carbone, coiffe, tuyère.
        "rocket_white": rocket_skin(81, ["#EEF0F2", "#E2E5E8", "#F8F9FA"], [5, 3, 2], [0, 8]),
        "rocket_orange": rocket_skin(82, ["#C8742A", "#B5651F", "#D98A3F", "#A85A1C"], [5, 3, 2, 1], [0]),
        "rocket_solid": rocket_skin(83, ["#EEF0F2", "#E2E5E8", "#F8F9FA"], [5, 3, 2], [0],
                                    [(3, "#22252A"), (4, "#22252A"), (11, "#B5391E")]),
        # Réservoirs en acier inoxydable : tôle nue, soudée en viroles.
        "rocket_steel": rocket_steel_texture(),
        # Réservoirs en aluminium-lithium : peinture blanche et bande bleu nuit.
        "rocket_alloy": rocket_skin(87, ["#F2F4F6", "#E6E9EC", "#FBFCFD"], [5, 3, 2], [0],
                                    bands=[(5, "#1F3F8F"), (6, "#1F3F8F"), (7, "#C9CDD1")]),
        # Jupe d'un moteur à ergols stockés sous pression : blanche à damier noir, comme les premières fusées.
        "rocket_checker": rocket_checker_texture(),
        "rocket_nozzle_copper": rocket_copper_nozzle_texture(),
        "rocket_dark": rocket_skin(85, ["#2A2D31", "#24272B", "#33363B"], [5, 3, 2], [0, 15]),
        # Débris de fusée : une tôle blanche calcinée, déchirée, dont il reste un lambeau de peinture.
        "rocket_debris": rocket_debris_texture(),
        "rocket_fairing": rocket_fairing_texture(),
        "rocket_nozzle": rocket_nozzle_texture(),
        "moon_regolith": moon_regolith_texture(),
        "moon_rock": moon_rock_texture(),
        "moon_ilmenite_ore": ore(moon_rock_texture(), flip_h(ORE_MASK), "#D9D6E8", "#8F8AA8", "#3B3850"),
        "lunar_ice": lunar_ice_texture(),
        "mars_regolith": mars_regolith_texture(),
        "mars_rock": mars_rock_texture(),
        "mars_hematite_ore": ore(mars_rock_texture(), ORE_MASK, "#E0B090", "#9C3B2A", "#4A1D16"),
        # Grands réservoirs composites : carbone noir, cerclé d'une bande claire.
        "rocket_composite": rocket_skin(89, ["#26282C", "#1F2124", "#2E3035"], [5, 3, 2], [0, 8], bands=[(4, "#C9CDD1")]),
        # Stations spatiales.
        "station_hull": station_hull_texture(),
        "station_window": station_window_texture(),
        "docking_port": docking_port_texture(),
        "life_support": life_support_texture(False),
        "life_support_on": life_support_texture(True),
        "creative_energy_source": creative_source_texture(ENERGY_SYMBOL, "#FFD75A", "#FFF6C9"),
        "creative_fluid_source": creative_source_texture(FLUID_SYMBOL, "#5FB0F5", "#CFEAFF"),
        "launch_console_front": machine_front(CONSOLE_SCREEN),
        "cryogenic_tank_front": machine_front(TANK_OFF),
        "cryogenic_tank_front_on": machine_front(TANK_ON),
        # Sorties de l'électrolyseur : hydrogène (rouge) d'un côté, oxygène (bleu) de l'autre.
        "electrolyzer_side_hydrogen": machine_port("#D96A6A"),
        "electrolyzer_side_oxygen": machine_port("#5FB0F5"),
        # Conduits : câble (gaine sombre, âme cuivrée) et tuyau (acier clair).
        "energy_cable": conduit("#1F2023", "#33363B", "#C8672A", "#E08A3C"),
        "fluid_pipe": conduit("#5A626B", "#8C949E", "#C3CAD2", "#3A4046"),
    }
    # Conduits en service : trois niveaux de lueur, de plus en plus vifs.
    for level in (1, 2, 3):
        blocks["energy_cable_glow%d" % level] = conduit("#1F2023", "#33363B", "#C8672A", "#E08A3C", "#FFE08A", level)
        blocks["fluid_pipe_glow%d" % level] = conduit("#5A626B", "#8C949E", "#C3CAD2", "#3A4046", "#8FE4FF", level)
    items = {
        "raw_bauxite": item(RAW_MASK, RAW_BAUXITE),
        "raw_titanium": item(flip_h(RAW_MASK), RAW_TITANIUM),
        "raw_lithium": item(RAW_MASK, RAW_LITHIUM),
        "steel_ingot": ingot(STEEL_INGOT),
        "aluminum_ingot": ingot(ALUMINUM_INGOT),
        "titanium_ingot": ingot(TITANIUM_INGOT),
        "lithium_ingot": ingot(LITHIUM_INGOT),
        "aluminum_lithium_ingot": ingot(ALUMINUM_LITHIUM_INGOT),
        "steel_plate": item(PLATE_MASK, STEEL),
        "aluminum_plate": item(PLATE_MASK, ALUMINUM),
        "titanium_plate": item(PLATE_MASK, TITANIUM),
        "aluminum_lithium_plate": item(PLATE_MASK, ALUMINUM_LITHIUM),
        # Pièces de fusée.
        "rocket": rocket_item(),
        "creative_research_dossier": dossier_item(),
        "solid_motor": solid_motor_item(),
        "pressure_fed_engine": engine_item(STEEL, DARK_METAL, "#C46A35", [2, 2, 2, 3, 3, 3]),
        "kerolox_engine": engine_item(STEEL, DARK_METAL, "#E8872A", [2, 2, 3, 3, 4, 4, 5]),
        "hydrolox_engine": engine_item(TITANIUM, COPPER, "#5FB0F5", [2, 3, 3, 4, 4, 5, 5]),
        "steel_tank": tank_item(STEEL),
        "aluminum_tank": tank_item(ALUMINUM),
        "aluminum_lithium_tank": tank_item(ALUMINUM_LITHIUM),
        "interstage": interstage_item(),
        "nose_cone": nose_cone_item(),
        "sounding_payload": sounding_payload_item(),
        "crew_capsule": crew_capsule_item(),
        # La combinaison spatiale a ses icônes dans generate_spacesuits.py.
        "recovery_remote": recovery_remote_item(),
        "mission_tablet": mission_tablet_item(),
        "station_core": station_core_item(),
        "lunar_module": lunar_module_item(),
        "mars_lander": mars_lander_item(),
        "staged_combustion_engine": engine_item(TITANIUM, DARK_METAL, "#D9534F", [2, 3, 3, 4, 4, 5, 5]),
        "methalox_engine": engine_item(ALUMINUM, COPPER, "#7FD9A8", [2, 3, 3, 4, 4, 5, 5]),
        "composite_tank": tank_item(DARK_METAL),
        # Satellites.
        "test_satellite": test_satellite_item(),
        "weather_satellite": weather_satellite_item(),
        "communication_satellite": communication_satellite_item(),
        "observation_satellite": observation_satellite_item(),
        "navigation_satellite": navigation_satellite_item(),
        "space_telescope": space_telescope_item(),
        "guide_book": from_mask(GUIDE_BOOK_MASK, {k: rgb(v) for k, v in GUIDE_BOOK_PALETTE.items()}),
    }
    return blocks, items


def build_guis():
    return {
        "processing_machine_1": gui_processing(1),
        "processing_machine_2": gui_processing(2),
        "combustion_generator": gui_generator(),
        "electrolyzer": gui_fluid_machine(False, True, 2),
        "cryogenic_liquefier": gui_fluid_machine(False, True, 1),
        "refinery": gui_fluid_machine(True, False, 1),
        "sabatier_reactor": gui_fluid_machine(True, True, 2),
        "helium3_extractor": gui_fluid_machine(True, False, 2),
        "cryogenic_tank": gui_cryogenic_tank(),
    }


def preview(path, images, scale=8, per_row=8):
    """Planche d'aperçu agrandie sur fond gris, pour vérifier les textures d'un coup d'œil."""
    pad = 4
    rows = (len(images) + per_row - 1) // per_row
    cell = SIZE * scale + pad
    width, height = per_row * cell + pad, rows * cell + pad
    sheet = [[(60, 60, 64, 255) for _ in range(width)] for _ in range(height)]
    for index, img in enumerate(images):
        ox = pad + (index % per_row) * cell
        oy = pad + (index // per_row) * cell
        for y in range(SIZE * scale):
            for x in range(SIZE * scale):
                p = img[y // scale][x // scale]
                if p[3]:
                    sheet[oy + y][ox + x] = p
    write_png(path, sheet)


if __name__ == "__main__":
    block_images, item_images = build_all()
    for name, img in block_images.items():
        write_png(os.path.join(BLOCK_DIR, name + ".png"), img)
    for name, img in item_images.items():
        write_png(os.path.join(ITEM_DIR, name + ".png"), img)
    gui_images = build_guis()
    for name, img in gui_images.items():
        write_png(os.path.join(GUI_DIR, name + ".png"), img)
    print(f"{len(block_images)} textures de blocs, {len(item_images)} textures d'items "
          f"et {len(gui_images)} interfaces écrites.")
    if len(sys.argv) > 1:
        preview(sys.argv[1], list(block_images.values()) + list(item_images.values()))
        print("Aperçu :", sys.argv[1])
    if len(sys.argv) > 2:
        # Aperçu des interfaces, agrandies deux fois et placées côte à côte.
        guis = list(gui_images.values())
        sheet = [[(60, 60, 64, 255) for _ in range(len(guis) * 2 * 256)] for _ in range(2 * 256)]
        for index, img in enumerate(guis):
            for y in range(512):
                for x in range(512):
                    p = img[y // 2][x // 2]
                    if p[3]:
                        sheet[y][index * 512 + x] = p
        write_png(sys.argv[2], sheet)
        print("Aperçu des interfaces :", sys.argv[2])
