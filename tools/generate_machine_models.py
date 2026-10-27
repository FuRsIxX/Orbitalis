#!/usr/bin/env python3
"""
Génère les modèles 3D des machines d'Orbitalis et leurs textures (dont les textures animées).

Chaque machine a sa propre silhouette, pour être reconnue d'un coup d'œil, et deux modèles : à l'arrêt et en marche.
Les deux ont la même forme ; en marche, certaines pièces prennent une texture animée (flammes, bulles, cylindres
qui tournent...) et émettent de la lumière.

Les modèles sont dessinés face avant au nord, comme tous les blocs orientables : le jeu les fait tourner.

Usage : python tools/generate_machine_models.py [apercu.png]
Avec un nom de fichier, l'outil dessine aussi une planche d'aperçu des machines, à l'arrêt et en marche,
vues de trois quarts : elle permet de vérifier les modèles sans lancer le jeu.
"""
import json
import math
import os
import random
import sys

from generate_textures import BLOCK_DIR, ROOT, SIZE, blotchy, rgb, write_png

MODEL_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "orbitalis", "models", "block", "machine")
FACES = ("north", "south", "east", "west", "up", "down")


# ---------------------------------------------------------------------------
# Textures
# ---------------------------------------------------------------------------
def shade(color, factor):
    """Éclaircit (facteur > 1) ou assombrit (facteur < 1) une couleur."""
    return tuple(max(0, min(255, int(c * factor))) for c in color[:3]) + (255,)


def blend(a, b, t):
    """Mélange de deux couleurs : 0 donne la première, 1 la seconde."""
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[k] + (b[k] - a[k]) * t)) for k in range(3)) + (255,)


def ramp(colors, t):
    """Couleur à la position t (de 0 à 1) d'un dégradé de plusieurs couleurs."""
    t = max(0.0, min(1.0, t)) * (len(colors) - 1)
    k = min(len(colors) - 2, int(t))
    return blend(rgb(colors[k]), rgb(colors[k + 1]), t - k)


def plain(seed, base, light, dark, lines=()):
    """
    Métal brossé : chaque rangée a sa propre nuance, très légère, comme une tôle passée à la brosse, avec un grain fin.
    Les joints horizontaux sont creusés : une ligne d'ombre, soulignée d'un filet de lumière. Le dessin reste
    uniforme d'un bord à l'autre, pour se découper proprement sur n'importe quelle pièce.
    """
    rnd = random.Random(seed)
    b, l, d = rgb(base), rgb(light), rgb(dark)
    img = []
    for y in range(SIZE):
        streak = rnd.uniform(-0.45, 0.45)
        row = []
        for x in range(SIZE):
            t = streak + rnd.uniform(-0.22, 0.22)
            row.append(blend(b, l, t) if t > 0 else blend(b, d, -t))
        img.append(row)
    for y in lines:
        for x in range(SIZE):
            img[y][x] = shade(img[y][x], 0.74)
            img[(y + 1) % SIZE][x] = shade(img[(y + 1) % SIZE][x], 1.10)
    return img


FIRE_COLORS = ["#FFF7D0", "#FFE27A", "#FFB23E", "#E86A1F", "#B5391E", "#5A1A12"]


def fire_frame(step, frames=12):
    """
    Foyer : des flammes qui ondulent. Leur hauteur suit deux ondes superposées, qui reviennent exactement à leur
    point de départ au bout du cycle : l'animation tourne en boucle sans à-coup.
    """
    img = [[rgb("#15171A") for _ in range(SIZE)] for _ in range(SIZE)]
    phase = 2 * math.pi * step / frames
    for x in range(SIZE):
        height = 9.5 + 3.0 * math.sin(phase + x * 1.3) + 2.0 * math.sin(2 * phase + x * 2.9 + 1.0)
        height = max(4.0, min(15.0, height))
        for dy in range(int(height)):
            img[SIZE - 1 - dy][x] = ramp(FIRE_COLORS, dy / height)
        # Le dessus de la flamme se fond dans l'ombre du foyer.
        top = SIZE - 1 - int(height)
        if top >= 0:
            img[top][x] = blend(rgb("#5A1A12"), rgb("#15171A"), 0.5)
    return img


def molten_frame(seed):
    """Métal en fusion, figé : des taches incandescentes."""
    return blotchy(seed, [rgb("#E8872A"), rgb("#FFD75A"), rgb("#FFF1A8"), rgb("#C4501C")], [5, 3, 1, 2])


def molten_flow(step, frames=12):
    """Bain de métal en fusion : des veines claires qui se déplacent lentement, en boucle."""
    phase = 2 * math.pi * step / frames
    img = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            value = (math.sin(x * 0.9 + phase) + math.sin(y * 1.1 - phase) + math.sin((x + y) * 0.6 + 2 * phase)) / 3.0
            row.append(ramp(["#B5451A", "#E8872A", "#FFC04A", "#FFF1A8"], 0.5 + 0.5 * value))
        img.append(row)
    return img


def arc_frame(step):
    """Arc électrique au bout des électrodes : un éclat blanc-bleu qui crépite, différent à chaque image."""
    rnd = random.Random(700 + step)
    colors = ["#FFFFFF", "#E6FAFF", "#CFF4FF", "#9FE8FF"] if step % 2 == 0 else ["#CFF4FF", "#9FE8FF", "#5FB0F5", "#FFFFFF"]
    return [[rgb(rnd.choice(colors)) for _ in range(SIZE)] for _ in range(SIZE)]


def roller_frame(offset):
    """Cylindre de laminoir : des génératrices claires qui défilent quand il tourne, ombrées comme un vrai cylindre."""
    tones = ["#C2CBD6", "#98A3B0", "#6C7683", "#454C56"]
    img = []
    for y in range(SIZE):
        color = rgb(tones[(y + offset) % 4])
        img.append([color for _ in range(SIZE)])
    for y in range(SIZE):
        img[y][0] = img[y][SIZE - 1] = rgb("#23272E")
        img[y][1] = shade(img[y][1], 0.85)
        img[y][SIZE - 2] = shade(img[y][SIZE - 2], 0.85)
    return img


def bubbles_frame(step, still=False):
    """Tube d'électrolyse : de l'eau, plus claire au centre du tube, et des bulles qui montent d'un pixel par image."""
    img = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            edge = abs(x - 7.5) / 7.5
            row.append(blend(rgb("#4F88EE"), rgb("#2F5FC4"), edge * edge))
        img.append(row)
    if still:
        return img
    rnd = random.Random(32)
    for _ in range(11):
        x, y = rnd.randrange(1, SIZE - 1), rnd.randrange(SIZE)
        y = (y - step) % SIZE
        img[y][x] = rgb("#FFFFFF")
        img[(y + 1) % SIZE][x] = rgb("#BFE3FF")
    return img


def fan_frame(step, frames=8, still=False):
    """
    Ventilateur du compresseur : quatre pales qui tournent d'un huitième de quart de tour par image. Comme les
    pales sont identiques, huit images suffisent à un mouvement continu.
    """
    img = [[rgb("#15171A") for _ in range(SIZE)] for _ in range(SIZE)]
    turn = (math.pi / 2) * step / frames
    blade = rgb("#5C6672") if still else rgb("#B4BEC9")
    edge = rgb("#454C56") if still else rgb("#7F8A97")
    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = x - 7.5, y - 7.5
            radius = math.hypot(dx, dy)
            if radius > 7.6:
                continue
            around = (math.atan2(dy, dx) - turn) % (math.pi / 2)
            if around < 0.42:
                img[y][x] = blade
            elif around < 0.62:
                img[y][x] = edge
            if radius < 2.2:
                img[y][x] = rgb("#C8672A") if radius < 1.3 else rgb("#8A4521")
    return img


def frost_frame(step, frames=8, still=False):
    """Serpentin de refroidissement : gris à l'arrêt, couvert d'un givre dont le scintillement se déplace en marche."""
    img = [[rgb("#24272C") for _ in range(SIZE)] for _ in range(SIZE)]
    phase = 2 * math.pi * step / frames
    for y in range(1, SIZE - 1, 3):
        for x in range(1, SIZE - 1):
            if still:
                img[y][x] = rgb("#6B737D")
                img[y + 1][x] = rgb("#4A5058")
            else:
                glow = 0.5 + 0.5 * math.sin(phase + x * 0.8 + y * 0.5)
                img[y][x] = blend(rgb("#8FD6F2"), rgb("#F2FCFF"), glow)
                img[y + 1][x] = blend(rgb("#4E9CC4"), rgb("#9FE0F7"), glow)
    return img


def level_frame(step, frames=8, still=False):
    """Hublot de niveau du réservoir : sombre à l'arrêt, un liquide froid dont la surface ondule en marche."""
    img = [[rgb("#15171A") for _ in range(SIZE)] for _ in range(SIZE)]
    if still:
        return img
    for x in range(SIZE):
        surface = 3 + round(math.sin((x + step) * 2 * math.pi / frames))
        for y in range(surface, SIZE):
            depth = (y - surface) / (SIZE - surface)
            img[y][x] = rgb("#CFF4FF") if y == surface else blend(rgb("#6FBEF7"), rgb("#2F5FC4"), depth)
    return img


# Première image de chaque texture écrite, pour l'aperçu.
TEXTURES = {}


MAP_ROWS = [
    "................",
    "..##....####.##.",
    ".####..########.",
    "..###..#######..",
    "...##...####.#..",
    "...###..###.....",
    "....##..##...##.",
    "....#....#...##.",
    "................",
]


def screen_base(dim):
    """Fond d'un écran mural : une petite carte du monde sur fond bleu nuit."""
    ocean = rgb("#0A1622") if dim else rgb("#0E1C2B")
    land = rgb("#1B3A30") if dim else rgb("#2C5A4A")
    img = [[ocean for _ in range(SIZE)] for _ in range(SIZE)]
    for y, row in enumerate(MAP_ROWS):
        for x, c in enumerate(row):
            if c == "#":
                img[3 + y][x] = land
    return img


def screen_idle_frame():
    """Écran en veille : la carte, assombrie, et un témoin d'attente."""
    img = screen_base(True)
    for x in range(2, 6):
        img[13][x] = rgb("#5A626B")
    return img


def screen_countdown_frame(step):
    """Écran pendant le compte à rebours : la carte, et une barre orange qui se remplit puis clignote."""
    img = screen_base(False)
    for x in range(2, 14):
        img[13][x] = img[14][x] = rgb("#33373A")
    for x in range(2, 2 + 3 * (step + 1)):
        img[13][x] = img[14][x] = rgb("#FFC857")
    img[1][13 if step % 2 == 0 else 12] = rgb("#FFC857")
    return img


def screen_flight_frame(step):
    """Écran pendant le vol : la trace au sol sinueuse, et la fusée qui avance le long de cette trace."""
    img = screen_base(False)
    points = [(x, 7 + round(3 * math.sin(x * math.pi / 8))) for x in range(SIZE)]
    for x, y in points:
        img[y][x] = rgb("#8FA3B8")
    x, y = points[(2 * step) % SIZE]
    img[y][x] = rgb("#FFFFFF")
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        if 0 <= x + dx < SIZE and 0 <= y + dy < SIZE:
            img[y + dy][x + dx] = rgb("#FFC857")
    for i in range(2, 14):
        img[14][i] = rgb("#5BC27A")
    return img


def console_screen_frame(step):
    """Écran du pupitre : des lignes de télémétrie vertes qui défilent."""
    rnd = random.Random(91)
    lines = [(rnd.randint(2, 6), rnd.randint(8, 13)) for _ in range(8)]
    img = [[rgb("#0C1410") for _ in range(SIZE)] for _ in range(SIZE)]
    for row in range(7):
        start, end = lines[(row + step) % len(lines)]
        for x in range(start, end + 1):
            img[1 + 2 * row][x] = rgb("#7CE08A") if row != 6 - step % 7 else rgb("#FFC857")
    return img


def keys_texture():
    """Clavier du pupitre : des rangées de touches sombres, quelques-unes colorées."""
    img = [[rgb("#25282D") for _ in range(SIZE)] for _ in range(SIZE)]
    rnd = random.Random(92)
    for y in range(1, SIZE, 2):
        for x in range(1, SIZE, 2):
            img[y][x] = rgb(rnd.choice(["#4A5058", "#4A5058", "#4A5058", "#5BC27A", "#E0655F", "#FFC857"]))
    return img


def solar_cells_texture():
    """
    Dessus d'un panneau solaire moderne : quatre cellules monocristallines bleu nuit par bloc, séparées par un fin
    joint sombre. Leurs coins coupés laissent voir le fond blanc du panneau, en petits losanges aux croisements ; deux
    rubans conducteurs, à peine plus clairs, traversent chaque cellule ; un reflet de verre glisse en diagonale. Le
    motif se répète d'un bloc à l'autre sans couture : une rangée de panneaux forme une seule nappe.
    """
    cell_low = rgb("#0B1730")
    cell_high = rgb("#16305A")
    joint = rgb("#0A0F18")
    backsheet = rgb("#C9D3DE")
    ribbon = rgb("#2C4670")
    img = [[cell_low for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            cx, cy = x % 8, y % 8
            if cx == 0 or cy == 0:
                # Le joint entre deux cellules ; à leur croisement, le fond blanc du panneau.
                img[y][x] = backsheet if cx == 0 and cy == 0 else joint
                continue
            # Les coins coupés des cellules : le fond blanc, en losange autour du croisement.
            corner = min(cx, 8 - cx) + min(cy, 8 - cy)
            if corner <= 2:
                img[y][x] = blend(backsheet, joint, 0.35)
                continue
            # La cellule : un léger dégradé, plus clair vers le haut de la nappe.
            color = blend(cell_high, cell_low, cy / 7.0)
            if cx in (3, 5):
                color = blend(color, ribbon, 0.6)
            # Le reflet du verre : une bande en diagonale, à peine plus claire.
            if (x + y) % 16 in (9, 10):
                color = shade(color, 1.35)
            img[y][x] = color
    return img


def solar_frame_texture():
    """Cadre d'aluminium anodisé d'un panneau solaire : gris clair brossé, une arête vive en haut, l'ombre en bas."""
    img = [[rgb("#AEB7C2") for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            color = rgb("#AEB7C2")
            # Le brossage : des stries horizontales très légères.
            if (y * 7 + x // 5) % 3 == 0:
                color = shade(color, 0.94)
            img[y][x] = color
        img[0][y] = rgb("#E2E8EE")
        img[SIZE - 1][y] = rgb("#7A838E")
    return img


def solar_back_texture():
    """Dessous d'un panneau solaire : la face arrière gris anthracite, et le liseré du cadre sur ses bords."""
    img = [[rgb("#2A3038") for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            if (x + 2 * y) % 11 == 0:
                img[y][x] = rgb("#30363F")
    return img


def write_texture(name, frames, frametime=None, interpolate=True):
    """
    Écrit une texture ; avec plusieurs images, elles sont empilées et accompagnées de leur fichier d'animation.
    Par défaut le jeu fond chaque image dans la suivante : le mouvement est continu, sans saccade. Les animations
    qui doivent rester franches (un arc qui crépite, un texte qui défile) le désactivent.
    """
    TEXTURES[name] = frames[0]
    rows = [row for frame in frames for row in frame]
    path = os.path.join(BLOCK_DIR, name + ".png")
    write_png(path, rows)
    meta = path + ".mcmeta"
    if len(frames) > 1:
        animation = {"frametime": frametime}
        if interpolate:
            animation["interpolate"] = True
        with open(meta, "w", newline="\n") as f:
            json.dump({"animation": animation}, f, indent=2)
    elif os.path.exists(meta):
        os.remove(meta)


def vent_texture():
    """Évent : des lamelles claires sur un fond noir, une rangée sur deux."""
    return [[rgb("#6B747F") if y % 2 == 0 else rgb("#111316") for _ in range(SIZE)] for y in range(SIZE)]


def port_texture():
    """Port d'énergie : un cadre doré, un isolant sombre et une âme de cuivre, en carrés emboîtés."""
    colors = ["#8A6A12", "#D9A93A", "#F2CC5E", "#15171A", "#15171A", "#8A4521", "#C46A35", "#E8A56A"]
    img = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            ring = min(x, y, SIZE - 1 - x, SIZE - 1 - y)
            row.append(rgb(colors[min(ring, len(colors) - 1)]))
        img.append(row)
    return img


def side_texture():
    """Flanc d'une machine : la tôle claire, une bande d'évents à lamelles dans le bas, un filet sombre au pied."""
    img = plain(64, "#8C95A0", "#9DA6B1", "#7A838E", [3])
    for y in range(9, 15):
        for x in range(3, 13):
            img[y][x] = rgb("#6B747F") if y % 2 == 1 else rgb("#111316")
    for x in range(SIZE):
        img[15][x] = rgb("#343A42")
    return img


def back_texture():
    """Dos d'une machine : la tôle claire, et dans le bas le port d'énergie, ses carrés emboîtés, où se branchent les câbles."""
    img = plain(66, "#8C95A0", "#9DA6B1", "#7A838E", [3])
    colors = ["#8A6A12", "#D9A93A", "#15171A", "#C46A35"]
    for y in range(9, 15):
        for x in range(5, 11):
            ring = min(x - 5, y - 9, 10 - x, 14 - y)
            img[y][x] = rgb(colors[min(ring, len(colors) - 1)])
    for x in range(SIZE):
        img[15][x] = rgb("#343A42")
    return img


def tube_glass_texture():
    """Verre d'un tube : presque transparent, légèrement bleuté, avec un reflet vertical."""
    img = [[(175, 215, 240, 55) for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(SIZE):
        img[y][3] = (240, 250, 255, 140)
        img[y][4] = (240, 250, 255, 85)
        img[y][12] = (120, 170, 205, 90)
    return img


def led_frame(step, frames=8):
    """Voyant d'état en marche : un vert qui respire lentement."""
    glow = 0.5 + 0.5 * math.sin(2 * math.pi * step / frames)
    color = blend(rgb("#2FA657"), rgb("#B6FFC9"), glow)
    return [[color for _ in range(SIZE)] for _ in range(SIZE)]


def status_frame(step):
    """Petit écran d'état d'une machine en marche : des barres vertes dont la longueur change."""
    rnd = random.Random(96)
    lengths = [rnd.randint(4, 12) for _ in range(12)]
    img = [[rgb("#0C1410") for _ in range(SIZE)] for _ in range(SIZE)]
    for row in range(5):
        length = lengths[(row * 2 + step) % len(lengths)]
        for x in range(2, 2 + length):
            img[2 + 3 * row][x] = rgb("#7CE08A") if row != step % 5 else rgb("#FFC857")
    return img


def glass_texture():
    """
    Vitre d'un hublot : un joint noir fin et un reflet en biais ; tout le reste est entièrement transparent.
    Aucun pixel n'est à demi transparent, et c'est voulu : un verre teinté serait dessiné avec les surfaces
    translucides, et ferait disparaître l'eau, la glace et les nuages vus au travers.
    """
    clear = (0, 0, 0, 0)
    img = [[clear for _ in range(SIZE)] for _ in range(SIZE)]
    for k in range(SIZE):
        img[0][k] = img[SIZE - 1][k] = img[k][0] = img[k][SIZE - 1] = rgb("#15171A")
    for k in range(2, 7):
        img[k][9 - k] = rgb("#DCEEFA")
    return img


SCREEN_SIZE = 32


def cockpit_screen_frame(step, frames=8):
    """
    Écran tactile de la capsule, deux fois plus fin que les autres textures pour rester lisible : à gauche la
    trajectoire autour de la planète, au centre les jauges de vol, à droite l'état des systèmes, en bas la
    chronologie de la mission. Fond presque noir, tracés cyan et blancs, comme sur une capsule moderne.
    """
    size = SCREEN_SIZE
    back, line, cyan, white = rgb("#080B10"), rgb("#16222E"), rgb("#35C3F0"), rgb("#EAF6FF")
    dim, green, amber = rgb("#1D5F78"), rgb("#4FD67A"), rgb("#FFB23E")
    img = [[back for _ in range(size)] for _ in range(size)]

    def put(x, y, color):
        if 0 <= x < size and 0 <= y < size:
            img[y][x] = color

    # Barre d'état et séparations des trois volets.
    for x in range(size):
        put(x, 3, line)
        put(x, 21, line)
    for y in range(4, 21):
        put(11, y, line)
        put(21, y, line)
    for x in (1, 2, 3):
        put(x, 1, cyan)
    for x in range(26, 31):
        put(x, 1, green if x < 29 + step % 2 else dim)

    # Volet gauche : la planète, l'orbite en pointillés, et la capsule qui la parcourt.
    for k in range(24):
        angle = 2 * math.pi * k / 24
        put(5 + round(2.6 * math.cos(angle)), 12 + round(2.6 * math.sin(angle)), dim)
        if k % 2 == 0:
            put(5 + round(4.4 * math.cos(angle)), 12 + round(6.6 * math.sin(angle)), line)
    angle = 2 * math.pi * step / frames
    put(5 + round(4.4 * math.cos(angle)), 12 + round(6.6 * math.sin(angle)), white)

    # Volet central : trois jauges verticales, dont la hauteur respire.
    for k, phase in enumerate((0.0, 2.1, 4.2)):
        level = 5 + round(3.5 * (1 + math.sin(2 * math.pi * step / frames + phase)))
        x = 13 + 3 * k
        for y in range(6, 20):
            put(x, y, line)
            put(x + 1, y, line)
        for y in range(19 - level, 20):
            put(x, y, cyan)
            put(x + 1, y, cyan)
        put(x, 19 - level, white)
        put(x + 1, 19 - level, white)

    # Volet droit : la liste des systèmes, tous au vert, sauf un qui clignote en orange.
    for row in range(6):
        y = 6 + 2 * row + (row // 3)
        blink = row == 4 and step % 4 < 2
        put(23, y, amber if blink else green)
        for x in range(25, 30 - row % 2):
            put(x, y, dim)

    # Bas de l'écran : la chronologie, ses jalons, et le repère de l'instant présent qui avance.
    for x in range(2, 30):
        put(x, 26, dim)
    for x in (4, 11, 18, 25):
        put(x, 25, cyan)
        put(x, 27, cyan)
    marker = 3 + (step * 25) // frames
    for y in (24, 25, 26, 27, 28):
        put(marker, y, white)
    for x in range(2, 9):
        put(x, 30, line)
    for x in range(23, 30):
        put(x, 30, line)
    return img


def touch_pad_texture():
    """Tablette d'accoudoir : quelques touches plates et deux curseurs, sur fond noir."""
    img = [[rgb("#080B10") for _ in range(SIZE)] for _ in range(SIZE)]
    for y in (2, 6, 10):
        for x in (2, 6, 10):
            for dy in range(2):
                for dx in range(3):
                    img[y + dy][x + dx] = rgb("#16222E")
    img[2][2] = img[2][3] = img[2][4] = rgb("#35C3F0")
    img[10][10] = img[10][11] = img[10][12] = rgb("#4FD67A")
    for x in range(2, 14):
        img[14][x] = rgb("#1D5F78")
    img[14][9] = img[13][9] = rgb("#EAF6FF")
    return img


def carbon_texture():
    """Siège baquet : un tissage de carbone sombre, un rembourrage central matelassé, et deux surpiqûres orange."""
    img = [[rgb("#1B1E23") if (x // 2 + y // 2) % 2 == 0 else rgb("#24282E") for x in range(SIZE)] for y in range(SIZE)]
    for y in range(SIZE):
        for x in range(5, 11):
            img[y][x] = rgb("#2D323A") if (x + y) % 4 else rgb("#3A404A")
        img[y][4] = img[y][11] = rgb("#E8872A")
    return img


def cabin_floor_texture():
    """Plancher de la cabine : une tôle sombre à larmes, antidérapante."""
    img = plain(85, "#22262C", "#2A2F36", "#1B1E23")
    for y in range(1, SIZE, 4):
        for x in range(1, SIZE, 4):
            shift = 2 if (y // 4) % 2 else 0
            px = (x + shift) % SIZE
            img[y][px] = img[(y + 1) % SIZE][(px + 1) % SIZE] = rgb("#454C56")
    return img


def cabin_liner_texture():
    """
    Habillage intérieur de la cabine : un panneau de composite sombre dans son cadre, coupé d'un joint, avec une
    grille d'aération, quatre fixations et un repère lumineux. Il est appliqué en entier sur chaque cloison.
    """
    img = plain(78, "#2A2E35", "#323741", "#23272D")
    frame, light = rgb("#16191D"), rgb("#434A55")
    for k in range(SIZE):
        img[0][k] = img[SIZE - 1][k] = img[k][0] = img[k][SIZE - 1] = frame
    for k in range(1, SIZE - 1):
        img[1][k] = light
        img[k][1] = shade(img[k][1], 1.12)
    for x in range(1, SIZE - 1):
        img[8][x] = frame
        img[9][x] = light
    for y in (11, 13):
        for x in range(3, 8):
            img[y][x] = rgb("#0E1013")
            img[y + 1][x] = rgb("#3A404A")
    for x, y in ((2, 3), (13, 3), (2, 6), (13, 6)):
        img[y][x] = rgb("#5C6672")
    img[4][6] = img[4][7] = img[4][8] = rgb("#1D5F78")
    img[4][9] = rgb("#35C3F0")
    for x in range(10, 14):
        img[12][x] = rgb("#1D5F78")
    img[12][13] = rgb("#4FD67A")
    return img


def cabin_side_screen_frame(step):
    """Écran latéral de la cabine : des barres d'état cyan dont une change, et une rangée de voyants."""
    img = [[rgb("#05080C") for _ in range(SIZE)] for _ in range(SIZE)]
    rnd = random.Random(97)
    lengths = [rnd.randint(4, 12) for _ in range(10)]
    for k in range(SIZE):
        img[0][k] = img[SIZE - 1][k] = img[k][0] = img[k][SIZE - 1] = rgb("#16222E")
    for row in range(5):
        length = lengths[(row + (step if row == step % 5 else 0)) % len(lengths)]
        for x in range(2, 2 + length):
            img[2 + 2 * row][x] = rgb("#35C3F0") if row != step % 5 else rgb("#EAF6FF")
        for x in range(2 + length, 14):
            img[2 + 2 * row][x] = rgb("#16222E")
    for x in range(2, 14, 3):
        img[13][x] = img[13][x + 1] = rgb("#4FD67A") if (x // 3 + step) % 4 else rgb("#FFB23E")
    return img


def flat(color, rows=None, columns=None, frame=None):
    """
    Aplat lisse : une seule couleur, sans grain. On peut y tracer des filets (rangées ou colonnes d'une autre
    couleur) et l'entourer d'un liseré. C'est la matière de la capsule : des surfaces nettes, des joints fins.
    """
    img = [[rgb(color) for _ in range(SIZE)] for _ in range(SIZE)]
    for y, other in (rows or {}).items():
        for x in range(SIZE):
            img[y][x] = rgb(other)
    for x, other in (columns or {}).items():
        for y in range(SIZE):
            img[y][x] = rgb(other)
    if frame:
        for k in range(SIZE):
            img[0][k] = img[SIZE - 1][k] = img[k][0] = img[k][SIZE - 1] = rgb(frame)
    return img


def chute_texture():
    """Voile de parachute : des fuseaux orange et blancs, nets, et un ourlet sombre en bas."""
    img = [[rgb("#F08A24") if (x // 4) % 2 == 0 else rgb("#F4F5F6") for x in range(SIZE)] for _ in range(SIZE)]
    for x in range(SIZE):
        img[SIZE - 1][x] = shade(img[SIZE - 1][x], 0.8)
    return img


def write_textures():
    write_texture("machine_metal", [plain(51, "#6B737D", "#7A828C", "#5E666F", [7, 15])])
    write_texture("machine_dark", [plain(52, "#2E3238", "#383D44", "#25282D")])
    write_texture("machine_light", [plain(53, "#AEB6BF", "#BDC4CC", "#9CA4AD", [15])])
    write_texture("machine_base", [plain(54, "#4A5058", "#555B64", "#3F444B", [0])])
    write_texture("machine_copper", [plain(55, "#C46A35", "#D98450", "#A85525", [3, 7, 11, 15])])
    write_texture("machine_insulation", [plain(56, "#E2E5E8", "#EEF0F2", "#D2D6DA", [5, 10])])
    write_texture("machine_red", [plain(57, "#D96A6A", "#E58484", "#BF5252")])
    write_texture("machine_blue", [plain(58, "#5FB0F5", "#82C2F8", "#4494DC")])
    write_texture("machine_plate_cold", [plain(59, "#7F8A97", "#93A0AD", "#6B7682")])
    write_texture("machine_plate_hot", [molten_frame(60)])

    # Châssis des machines : cadre sombre, panneaux clairs, évents, port d'énergie, voyant d'état, verre.
    write_texture("machine_frame", [plain(63, "#343A42", "#434A53", "#262B31", [7, 15])])
    write_texture("machine_panel", [plain(64, "#8C95A0", "#9DA6B1", "#7A838E", [3, 11])])
    write_texture("machine_vent", [vent_texture()])
    write_texture("machine_port", [port_texture()])
    write_texture("machine_side", [side_texture()])
    write_texture("machine_back", [back_texture()])
    write_texture("machine_glass", [tube_glass_texture()])
    write_texture("machine_led_off", [plain(65, "#4A1B18", "#5C2320", "#35120F")])
    write_texture("machine_led_on", [led_frame(s) for s in range(8)], 3)
    write_texture("machine_status_on", [status_frame(s) for s in range(6)], 6, interpolate=False)
    write_texture("machine_fire_off", [plain(61, "#15171A", "#1E2125", "#0F1113", [4, 9, 14])])
    # Capsule habitée : coque, vitres, planche de bord.
    write_texture("capsule_hull", [flat("#EEF0F2", rows={15: "#D3D8DD"})])
    # Bas de la coque : la même, ceinte d'un bandeau sombre au-dessus du bouclier.
    write_texture("capsule_hull_low", [flat("#EEF0F2", rows={0: "#D3D8DD", 12: "#1C2026", 13: "#1C2026", 14: "#1C2026"})])
    write_texture("capsule_trim", [flat("#1C2026", rows={0: "#2A2F37"})])
    write_texture("capsule_shield", [flat("#2A2622", rows={0: "#3A342E"})])
    write_texture("capsule_glass", [glass_texture()])
    write_texture("capsule_screen", [cockpit_screen_frame(s) for s in range(8)], 6, interpolate=False)
    write_texture("capsule_touch", [touch_pad_texture()])
    write_texture("capsule_seat", [flat("#17191D", rows={3: "#22262C", 7: "#22262C", 11: "#22262C", 15: "#22262C"})])
    write_texture("capsule_liner", [flat("#252930", frame="#1A1D22")])
    write_texture("capsule_floor", [flat("#1D2025", rows={7: "#272B31", 15: "#272B31"}, columns={7: "#272B31", 15: "#272B31"})])
    write_texture("capsule_side", [cabin_side_screen_frame(s) for s in range(6)], 8, interpolate=False)
    write_texture("capsule_strap", [flat("#F08A24")])
    write_texture("capsule_led", [flat("#D8F4FF")])
    write_texture("capsule_grip", [flat("#D8442F", rows={0: "#F06A52"})])
    write_texture("machine_fire_on", [fire_frame(s) for s in range(12)], 2)
    write_texture("machine_molten_off", [plain(62, "#3A3D42", "#44484E", "#2E3136")])
    write_texture("machine_molten_on", [molten_flow(s) for s in range(12)], 3)
    write_texture("machine_arc_on", [arc_frame(s) for s in range(6)], 1, interpolate=False)
    write_texture("machine_roller_off", [roller_frame(0)])
    write_texture("machine_roller_on", [roller_frame(s) for s in (0, 1, 2, 3)], 2)
    write_texture("machine_water_off", [bubbles_frame(0, still=True)])
    write_texture("machine_water_on", [bubbles_frame(s) for s in range(16)], 2)
    write_texture("machine_fan_off", [fan_frame(0, still=True)])
    write_texture("machine_fan_on", [fan_frame(s) for s in range(8)], 1)
    write_texture("machine_coil_off", [frost_frame(0, still=True)])
    write_texture("machine_coil_on", [frost_frame(s) for s in range(8)], 3)
    write_texture("machine_level_off", [level_frame(0, still=True)])
    write_texture("machine_level_on", [level_frame(s) for s in range(8)], 3)

    # Voile des parachutes de la capsule habitée.
    write_texture("capsule_chute", [chute_texture()])

    # Panneau solaire : ses cellules, son cadre d'aluminium et sa face arrière.
    write_texture("solar_panel_top", [solar_cells_texture()])
    write_texture("solar_frame", [solar_frame_texture()])
    write_texture("solar_back", [solar_back_texture()])

    # Centre de contrôle : pupitre et écrans muraux.
    write_texture("machine_keys", [keys_texture()])
    write_texture("machine_console_screen", [console_screen_frame(s) for s in range(7)], 6, interpolate=False)
    write_texture("machine_screen_idle", [screen_idle_frame()])
    # Dalle d'écran éteinte : c'est le mur entier qui affiche l'image, pas chaque dalle.
    write_texture("machine_screen_blank", [plain(93, "#0B0E12", "#0E1217", "#090B0E")])
    write_texture("machine_screen_countdown", [screen_countdown_frame(s) for s in range(4)], 5, interpolate=False)
    write_texture("machine_screen_flight", [screen_flight_frame(s) for s in range(8)], 3, interpolate=False)


# ---------------------------------------------------------------------------
# Modèles
# ---------------------------------------------------------------------------
def box(x0, y0, z0, x1, y1, z1, texture):
    """Pavé dont toutes les faces portent la même texture. Les textures suivent la position de la pièce."""
    return {"from": [x0, y0, z0], "to": [x1, y1, z1], "texture": texture}


def brace(axis, plane, angle, texture, half=11.0):
    """
    Croisillon : une barre qui traverse une face du bloc en diagonale.

    axis  : axe perpendiculaire à la face ("z" pour les faces nord et sud, "x" pour les faces est et ouest)
    plane : position de la face le long de cet axe, en pixels
    angle : 45 ou -45
    """
    # half : demi-longueur de la barre (11 pour toute la diagonale d'une face de 16 pixels, ses bouts dépassant alors
    # un peu des angles ; 10.2 pour qu'ils restent dans le bloc).
    # Les deux diagonales d'une face se croisent : chacune a sa propre profondeur, l'une devant l'autre.
    # Deux faces dans le même plan se disputeraient l'affichage et scintilleraient quand on se déplace.
    near = plane + (0.3 if angle > 0 else -0.3)
    if axis == "z":
        part = box(8 - half, 7.25, near - 0.25, 8 + half, 8.75, near + 0.25, texture)
        part["rotation"] = {"origin": [8, 8, plane], "axis": "z", "angle": angle}
    else:
        part = box(near - 0.25, 7.25, 8 - half, near + 0.25, 8.75, 8 + half, texture)
        part["rotation"] = {"origin": [plane, 8, 8], "axis": "x", "angle": angle}
    # Les textures sont appliquées telles quelles : la barre dépasse du carré de 16 pixels.
    part["stretch"] = True
    return part


# Textures de base, communes à toutes les machines.
COMMON = {
    "base": "machine_base", "metal": "machine_metal", "dark": "machine_dark", "light": "machine_light",
    "copper": "machine_copper", "insulation": "machine_insulation", "red": "machine_red", "blue": "machine_blue",
    "frame": "machine_frame", "panel": "machine_panel", "vent": "machine_vent", "port": "machine_port",
    "side": "machine_side", "back": "machine_back",
    "glass": "machine_glass",
}
# Pièces animées : texture à l'arrêt, texture en marche. En marche, elles émettent de la lumière.
ANIMATED = {
    "fire": ("machine_fire_off", "machine_fire_on"),
    "molten": ("machine_molten_off", "machine_molten_on"),
    "arc": ("machine_copper", "machine_arc_on"),
    "roller": ("machine_roller_off", "machine_roller_on"),
    "plate": ("machine_plate_cold", "machine_plate_hot"),
    "water": ("machine_water_off", "machine_water_on"),
    "fan": ("machine_fan_off", "machine_fan_on"),
    "coil": ("machine_coil_off", "machine_coil_on"),
    "level": ("machine_level_off", "machine_level_on"),
    "led": ("machine_led_off", "machine_led_on"),
    "screen": ("machine_screen_blank", "machine_status_on"),
}
# Pièces qui tournent sans chauffer : animées, mais sans lumière.
UNLIT = {"roller", "fan"}


def chassis(height, vents=True, port=True):
    """
    Châssis commun à toutes les machines, dans l'esprit des machines industrielles modulaires : une semelle et
    un socle, quatre montants d'angle sombres, un caisson de panneaux clairs en retrait, une ceinture haute,
    un voyant d'état en façade, des évents à lamelles sur les côtés et un port d'énergie à l'arrière.
    Ce qui distingue chaque machine se pose sur ce châssis, ou s'y encastre.

    height : hauteur du caisson, en pixels ; la ceinture occupe le pixel du dessus
    """
    parts = [
        box(0, 0, 0, 16, 1, 16, "frame"),
        box(0.5, 1, 0.5, 15.5, 3, 15.5, "base"),
        box(0, 3, 0, 2, height, 2, "frame"), box(14, 3, 0, 16, height, 2, "frame"),
        box(0, 3, 14, 2, height, 16, "frame"), box(14, 3, 14, 16, height, 16, "frame"),
        box(1, 3, 1, 15, height, 15, "panel"),
        box(0, height, 0, 16, height + 1, 16, "frame"),
        # Voyant d'état : rouge sombre à l'arrêt, vert lumineux en marche.
        box(4, 1.5, 0.1, 12, 2.5, 0.5, "led"),
    ]
    if vents:
        y = 4.0
        while y + 0.75 <= height - 0.5:
            parts += [box(0.4, y, 4, 1, y + 0.75, 12, "vent"), box(15, y, 4, 15.6, y + 0.75, 12, "vent")]
            y += 1.5
    if port and height >= 8:
        parts += [box(5, 4, 15, 11, height - 1, 15.6, "port")]
    return parts


def body(height, texture="panel", sides="side"):
    """
    Corps d'une machine, presque un bloc plein : toute la largeur et toute la profondeur, de {@code 0} à
    {@code height}, sous une ceinture pleine d'un pixel. Le dessous et le dos sont pleins (le dos porte le port
    d'énergie) : les câbles et les tuyaux s'y raccordent proprement. Les flancs portent une bande d'évents. En façade,
    une plinthe sombre au pied et un petit voyant d'état en haut à gauche ; le reste de la façade attend ses trappes.
    """
    return [
        box(0, 0, 0, 16, 2, 0.5, "frame"),
        {**box(0, 0, 0.5, 16, height, 16, texture), "faces": {"south": "back", "east": sides, "west": sides, "down": "base"}},
        box(0, height, 0, 16, height + 1, 16, "frame"),
        box(1, height - 2, 0, 2.5, height - 1, 0.5, "led"),
    ]


def hatch(x0, y0, x1, y1, texture):
    """Trappe en façade, en léger relief : un cadre sombre d'un demi-pixel, et la pièce animée en retrait au fond."""
    return [
        box(x0 - 1, y0 - 1, 0, x0, y1 + 1, 0.5, "frame"), box(x1, y0 - 1, 0, x1 + 1, y1 + 1, 0.5, "frame"),
        box(x0, y1, 0, x1, y1 + 1, 0.5, "frame"), box(x0, y0 - 1, 0, x1, y0, 0.5, "frame"),
        box(x0, y0, 0.25, x1, y1, 0.5, texture),
    ]


def door(x0, y0, x1, y1, texture):
    """Ouverture encadrée en façade (foyer, serpentin) : un cadre sombre en saillie, et la pièce animée au fond."""
    return [
        box(x0 - 1, y0 - 1, 0.4, x0, y1 + 1, 1, "frame"), box(x1, y0 - 1, 0.4, x1 + 1, y1 + 1, 1, "frame"),
        box(x0, y1, 0.4, x1, y1 + 1, 1, "frame"), box(x0, y0 - 1, 0.4, x1, y0, 1, "frame"),
        box(x0, y0, 0.7, x1, y1, 1, texture),
    ]


# Les machines sont écrites par write_machines.py, leur seul auteur : un même cube plein, leur accent et leur fenêtre.
MACHINES = {}


# Dalle d'écran du centre de contrôle : un caisson, un cadre fin et la dalle, sombre tant que le mur n'affiche rien.
DISPLAY = [
    box(0, 0, 2, 16, 16, 16, "dark"),
    box(0, 0, 1, 16, 16, 2, "metal"),
    box(1, 1, 0.5, 15, 15, 1, "screen"),
]

def tilted(part):
    """Pièce de la parabole d'une station de poursuite : inclinée vers l'avant, autour du haut de son mât."""
    return {**part, "rotation": {"origin": [8, 9, 8], "axis": "x", "angle": -22.5}}


def stretched(part):
    """Pièce dont chaque face reçoit la texture entière, quelle que soit sa position (cadre d'une vitre, écran)."""
    return {**part, "stretch": True}


def lined(part, *inner):
    """Cloison de la capsule : blanche dehors, habillée de panneaux sombres sur ses faces tournées vers l'intérieur."""
    return {**part, "faces": {face: "liner" for face in inner}}



def slanted(part, axis, angle):
    """Suspente du parachute : elle part du nez de la capsule et s'écarte à 45° vers le bord de la voile."""
    return {**part, "rotation": {"origin": [8, 0, 8], "axis": axis, "angle": angle}}


# Blocs à modèle unique : pièces, textures propres au bloc, pièces lumineuses.
EXTRAS = {
    # (Le pupitre de lancement et la station de poursuite sont écrits par generate_consoles.py.)
    # (La capsule habitée est écrite par generate_capsule.py.)
    # Parachute : quatre suspentes qui partent du nez de la capsule et s'écartent vers le bord de la voile, et une
    # voile en coupole, à fuseaux orange et blancs, percée de sa cheminée au sommet. Le rendu le dessine deux blocs
    # au-dessus de la capsule : sa base est le nez de celle-ci.
    "parachute": ([
        slanted(box(7.75, 0, 7.75, 8.25, 22.6, 8.25, "trim"), "z", 45), slanted(box(7.75, 0, 7.75, 8.25, 22.6, 8.25, "trim"), "z", -45),
        slanted(box(7.75, 0, 7.75, 8.25, 22.6, 8.25, "trim"), "x", 45), slanted(box(7.75, 0, 7.75, 8.25, 22.6, 8.25, "trim"), "x", -45),
        stretched(box(-10, 16, -10, 26, 19, 26, "chute")),
        stretched(box(-8, 19, -8, 24, 22, 24, "chute")),
        stretched(box(-5, 22, -5, 21, 24.5, 21, "chute")),
        stretched(box(-1, 24.5, -1, 17, 26, 17, "chute")),
        box(5.5, 26, 5.5, 10.5, 26.4, 10.5, "trim"),
    ], {"chute": "capsule_chute", "trim": "capsule_trim"}, set()),
    # Panneau solaire moderne : une platine boulonnée, un mât fin, sa tête, le tube porteur (d'est en ouest) et deux
    # rails (du nord au sud), qui filent d'un bloc à l'autre, le boîtier de raccordement sous la nappe, et la nappe,
    # fine, qui occupe tout le bloc pour se raccorder sans joint à ses voisines.
    "solar_panel": ([
        box(4, 0, 4, 12, 0.5, 12, "dark"),
        box(4.5, 0.5, 4.5, 5.5, 1, 5.5, "frame"), box(10.5, 0.5, 4.5, 11.5, 1, 5.5, "frame"),
        box(4.5, 0.5, 10.5, 5.5, 1, 11.5, "frame"), box(10.5, 0.5, 10.5, 11.5, 1, 11.5, "frame"),
        box(7, 0.5, 7, 9, 9, 9, "frame"),
        box(6, 9, 6.5, 10, 10.5, 9.5, "dark"),
        box(0, 10.5, 7, 16, 11.5, 9, "alu"),
        box(2, 11.5, 0, 3, 12, 16, "back"), box(13, 11.5, 0, 14, 12, 16, "back"),
        box(9, 11, 10, 12, 12, 13, "dark"),
        {**box(0, 12, 0, 16, 13, 16, "back"), "faces": {"up": "cells"}},
    ], {"cells": "solar_panel_top", "alu": "solar_frame", "back": "solar_back"}, set()),
    # Cadre d'aluminium d'un panneau, sur un côté sans voisin (ici le nord ; le jeu le tourne pour les autres). Il
    # coiffe la tranche de la nappe et la dépasse d'un demi-pixel. Les quatre cadres d'un panneau isolé tournent en
    # moulinet (chacun s'arrête où commence le suivant) : aucun ne recouvre un autre, aucune face ne se dédouble.
    "solar_edge": ([box(-0.25, 11.6, -0.25, 15.5, 13.5, 0.5, "alu")], {"alu": "solar_frame"}, set()),
    # Sas d'amarrage, collier tourné vers le nord (l'extérieur) : la paroi de coque en retrait, le collier
    # d'amarrage, ses quatre verrous, l'écoutille et son hublot, et un bandeau lumineux au-dessus.
    # Côté intérieur, la paroi porte l'écoutille vue de dedans.
    "docking_port": ([
        # Paroi de coque, en retrait des deux côtés : le collier dehors, la porte dedans.
        box(0, 0, 2, 16, 16, 13, "hull"),
        # Extérieur (au nord) : collier d'amarrage, quatre verrous, écoutille, hublot et bandeau lumineux.
        box(2, 12, 0, 14, 14, 2, "frame"), box(2, 2, 0, 14, 4, 2, "frame"),
        box(2, 4, 0, 4, 12, 2, "frame"), box(12, 4, 0, 14, 12, 2, "frame"),
        box(7, 12.5, -1, 9, 13.5, 0, "light"), box(7, 2.5, -1, 9, 3.5, 0, "light"),
        box(2.5, 7, -1, 3.5, 9, 0, "light"), box(12.5, 7, -1, 13.5, 9, 0, "light"),
        box(4, 4, 1, 12, 12, 2, "panel"),
        stretched(box(6, 6, 0.7, 10, 10, 1, "glass")),
        box(5, 14.6, 1.5, 11, 15.3, 2, "led"),
        # Intérieur (au sud) : le dormant, un cadre sombre en saillie.
        box(1, 13, 13, 15, 15, 16, "frame"), box(1, 1, 13, 15, 3, 16, "frame"),
        box(1, 3, 13, 3, 13, 16, "frame"), box(13, 3, 13, 15, 13, 16, "frame"),
        # La porte, dans son cadre, avec ses deux charnières à gauche.
        box(3, 3, 13, 13, 13, 14.5, "panel"),
        box(3, 4, 14.5, 3.8, 6, 15, "dark"), box(3, 10, 14.5, 3.8, 12, 15, "dark"),
        # Hublot de la porte.
        box(5.5, 8, 14.5, 10.5, 12, 14.9, "dark"),
        stretched(box(6, 8.5, 14.9, 10, 11.5, 15.1, "glass")),
        # Volant de verrouillage : une croix à quatre branches sur son moyeu.
        box(6, 5.1, 14.5, 7.4, 5.9, 15.3, "light"), box(8.6, 5.1, 14.5, 10, 5.9, 15.3, "light"),
        box(7.6, 3.5, 14.5, 8.4, 4.9, 15.3, "light"), box(7.6, 6.1, 14.5, 8.4, 7.5, 15.3, "light"),
        box(7.2, 4.7, 14.5, 8.8, 6.3, 15.7, "dark"),
        # Bandeau lumineux au-dessus de la porte, et tablette de commande sur le montant de droite.
        box(4, 13.7, 16, 12, 14.3, 16.25, "led"),
        stretched(box(13.3, 6.5, 16, 14.7, 9.5, 16.25, "touch")),
    ], {"hull": "station_hull", "glass": "capsule_glass", "led": "capsule_led", "touch": "capsule_touch"}, {"led", "touch"}),
    # Train d'alunissage du vaisseau lunaire : une jupe dorée autour du bouclier, la tuyère du moteur de descente,
    # et quatre jambes en diagonale terminées par leurs patins. Il se dessine par-dessus la capsule.
    "lander": ([
        box(-7, 1.6, -7, 23, 4, -6.1, "gold"), box(-7, 1.6, 22.1, 23, 4, 23, "gold"),
        box(-7, 1.6, -6.1, -6.1, 4, 22.1, "gold"), box(22.1, 1.6, -6.1, 23, 4, 22.1, "gold"),
        box(5, -0.6, 5, 11, 0, 11, "dark"),
    ] + [part for sx, sz in ((-1, -1), (1, -1), (-1, 1), (1, 1)) for part in (
        box(8 + sx * 15.5 - 0.75, 0.6, 8 + sz * 15.5 - 0.75, 8 + sx * 15.5 + 0.75, 6, 8 + sz * 15.5 + 0.75, "metal"),
        box(8 + sx * 15.5 - 2, 0, 8 + sz * 15.5 - 2, 8 + sx * 15.5 + 2, 0.6, 8 + sz * 15.5 + 2, "light"),
    )], {"gold": "machine_port"}, set()),
}


def build_extra(parts, own_textures, emissive):
    names = dict(COMMON)
    names.update(own_textures)
    used = {part["texture"] for part in parts} | {key for part in parts for key in part.get("faces", {}).values()}
    textures = {key: "orbitalis:block/" + name for key, name in names.items() if key in used}
    textures["particle"] = "orbitalis:block/machine_metal"
    elements = []
    for part in parts:
        faces = {face: {"texture": "#" + part.get("faces", {}).get(face, part["texture"])} for face in FACES}
        if part.get("stretch") or any(c < 0 or c > 16 for c in part["from"] + part["to"]):
            # Pièce qui dépasse du bloc : la texture entière est plaquée sur chaque face.
            for face in faces.values():
                face["uv"] = [0, 0, 16, 16]
        # Portion de texture imposée à certaines faces (une conduite garde ainsi des pixels de la taille de ceux des blocs).
        for face, uv in part.get("uv", {}).items():
            faces[face]["uv"] = uv
        element = {"from": part["from"], "to": part["to"], "faces": faces}
        if "rotation" in part:
            element["rotation"] = part["rotation"]
        if part["texture"] in emissive:
            element["light_emission"] = 15
        elements.append(element)
    return {"parent": "minecraft:block/block", "textures": textures, "elements": elements}


def build_model(parts, lit):
    textures = {key: "orbitalis:block/" + name for key, name in COMMON.items()}
    for key, (off, on) in ANIMATED.items():
        textures[key] = "orbitalis:block/" + (on if lit else off)
    textures["particle"] = "orbitalis:block/machine_metal"

    used = {part["texture"] for part in parts} | {key for part in parts for key in part.get("faces", {}).values()}
    elements = []
    for part in parts:
        own = part.get("faces", {})
        element = {"from": part["from"], "to": part["to"],
                   "faces": {face: {"texture": "#" + own.get(face, part["texture"])} for face in FACES}}
        if lit and part["texture"] in ANIMATED and part["texture"] not in UNLIT:
            element["light_emission"] = 15
        elements.append(element)
    # Seules les textures réellement utilisées par la machine sont déclarées.
    textures = {key: value for key, value in textures.items() if key in used or key == "particle"}
    # Le parent donne au modèle sa présentation habituelle dans l'inventaire et en main.
    return {"parent": "minecraft:block/block", "textures": textures, "elements": elements}


def write_models():
    os.makedirs(MODEL_DIR, exist_ok=True)
    for name, parts in MACHINES.items():
        for part in parts:
            assert all(0 <= c <= 16 for c in part["from"] + part["to"]), (name, part)
            assert all(a < b for a, b in zip(part["from"], part["to"])), (name, part)
        for lit in (False, True):
            path = os.path.join(MODEL_DIR, name + ("_on" if lit else "") + ".json")
            with open(path, "w", newline="\n") as f:
                json.dump(build_model(parts, lit), f, indent=2)
    for name, (parts, own_textures, emissive) in EXTRAS.items():
        model = build_extra(parts, own_textures, emissive)
        if name in ("capsule", "parachute"):
            # Ces modèles débordent largement de leur bloc : l'ombrage de contact les assombrirait à tort.
            model["ambientocclusion"] = False
        with open(os.path.join(MODEL_DIR, name + ".json"), "w", newline="\n") as f:
            json.dump(model, f, indent=2)


# ---------------------------------------------------------------------------
# Aperçu : rendu simplifié des modèles, vus de trois quarts face avant
# ---------------------------------------------------------------------------
def render(parts, lit, scale=10, own_textures=None):
    """Dessine un modèle en perspective isométrique : on voit la face avant (nord), le côté ouest et le dessus."""
    size = int(16 * scale * 2.6)
    image = [[(60, 60, 64, 255) for _ in range(size)] for _ in range(size)]
    depth = [[-1e9 for _ in range(size)] for _ in range(size)]

    def texture_of(part):
        key = part["texture"]
        if own_textures and key in own_textures:
            # Les textures écrites par un autre outil ne sont pas connues ici : l'aperçu prend alors le métal nu.
            return TEXTURES.get(own_textures[key], TEXTURES["machine_metal"])
        if key in ANIMATED:
            return TEXTURES[ANIMATED[key][1 if lit else 0]]
        return TEXTURES[COMMON[key]]

    def plot(x, y, z, color, shade):
        # Caméra placée devant (au nord), à gauche (à l'ouest) et au-dessus.
        # Plus un point est proche de la caméra (petit x, petit z), plus il est bas à l'écran.
        sx = int(size / 2 + ((16 - x) - (16 - z)) * scale * 0.85)
        sy = int(size * 0.12 + ((16 - x) + (16 - z)) * scale * 0.45 + (16 - y) * scale)
        d = -(z) - (x) + y
        if 0 <= sx < size and 0 <= sy < size and d > depth[sy][sx]:
            depth[sy][sx] = d
            image[sy][sx] = tuple(int(c * shade) for c in color[:3]) + (255,)

    step = 1.0 / (scale * 1.3)
    for part in parts:
        (x0, y0, z0), (x1, y1, z1) = part["from"], part["to"]
        tex = texture_of(part)

        def frange(a, b):
            n = max(1, int((b - a) / step))
            return [a + (b - a) * (i + 0.5) / n for i in range(n)]

        for x in frange(x0, x1):
            for y in frange(y0, y1):          # face nord
                plot(x, y, z0, tex[min(15, int(16 - y))][min(15, int(16 - x) % 16)], 0.85)
            for z in frange(z0, z1):          # dessus
                plot(x, y1, z, tex[min(15, int(z))][min(15, int(x))], 1.0)
        for z in frange(z0, z1):              # face ouest
            for y in frange(y0, y1):
                plot(x0, y, z, tex[min(15, int(16 - y))][min(15, int(z))], 0.65)
    return image


def write_preview(path):
    tiles = []
    for name, parts in MACHINES.items():
        tiles.append(render(parts, False))
        tiles.append(render(parts, True))
    for name, (parts, own_textures, emissive) in EXTRAS.items():
        tiles.append(render(parts, False, own_textures=own_textures))
    per_row = 4
    tile = len(tiles[0])
    rows = (len(tiles) + per_row - 1) // per_row
    sheet = [[(60, 60, 64, 255) for _ in range(per_row * tile)] for _ in range(rows * tile)]
    for index, image in enumerate(tiles):
        ox, oy = (index % per_row) * tile, (index // per_row) * tile
        for y in range(tile):
            sheet[oy + y][ox:ox + tile] = image[y]
    write_png(path, sheet)


if __name__ == "__main__":
    write_textures()
    write_models()
    if len(sys.argv) > 1:
        write_preview(sys.argv[1])
        print("Aperçu :", sys.argv[1])
    print(f"{len(MACHINES)} machines : modèles et textures écrits.")
