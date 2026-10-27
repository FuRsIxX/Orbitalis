#!/usr/bin/env python3
"""
Génère les blocs d'aménagement des stations spatiales d'Orbitalis : leurs modèles 3D et leurs textures, ainsi que les
icônes des kits de modules.

Une station se bâtit avec la coque blanche (déjà écrite par generate_textures.py), et s'aménage avec :
- le revêtement intérieur : des panneaux capitonnés crème, en trois variantes (nu, avec une main courante, avec une
  bouche d'aération), tournés vers la pièce, la coque blanche restant dehors ;
- le plancher, une tôle antidérapante ; le plafonnier, une dalle de diodes ;
- la poutre treillis des panneaux solaires ; le sas étanche (une porte de deux blocs, à hublot) ; le hublot de coupole ;
- le terminal de la station, une console murale à écran ;
- la couchette d'équipage, une niche où un sac de couchage pend au mur, comme à bord de l'ISS ;
- la baie d'expériences du laboratoire, en marche et à l'arrêt ;
- le bac de culture hydroponique de la serre, vide ou planté (blé, carottes, pommes de terre, betteraves), à quatre
  stades de croissance ;
- le casier de fret, des sacs de toile sanglés dans leur cadre ;
- la batterie de la station, avec sa jauge à quatre barres.

Les modèles sont dessinés face avant au nord : le jeu les fait tourner. Les textures gardent des pixels de la taille
de ceux des blocs, sauf les écrans, vitres et jauges, plaqués entiers. Aucune face n'en recouvre une autre.

Usage : python tools/generate_station.py
        (à lancer après generate_machine_models.py, dont il réutilise les outils de dessin)
"""
import json
import math
import os
import random

import generate_machine_models as gm
from generate_machine_models import box, brace, plain
from generate_textures import ITEM_DIR, SIZE, TRANSPARENT, rgb, station_hull_texture, write_png
from model_kit import build_tiled, check_overlaps, display, separate

STATION_DIR = os.path.join(gm.MODEL_DIR, "..", "station")


# ---------------------------------------------------------------------------
# Outils de dessin
# ---------------------------------------------------------------------------
def canvas(color):
    return [[rgb(color) if isinstance(color, str) else color for _ in range(SIZE)] for _ in range(SIZE)]


def dot(img, x, y, color):
    if 0 <= y < len(img) and 0 <= x < len(img[0]):
        img[y][x] = rgb(color) if isinstance(color, str) else color


def rect(img, x0, y0, x1, y1, color):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            dot(img, x, y, color)


def outline(img, x0, y0, x1, y1, color):
    for x in range(x0, x1 + 1):
        dot(img, x, y0, color)
        dot(img, x, y1, color)
    for y in range(y0, y1 + 1):
        dot(img, x0, y, color)
        dot(img, x1, y, color)


def shade(color, factor):
    return gm.shade(color, factor)


def grain(seed, base, amount=0.05):
    """Une teinte unie, à peine granuleuse : du tissu, du plastique, de la peinture mate."""
    rnd = random.Random(seed)
    b = rgb(base)
    return [[shade(b, 1.0 + rnd.uniform(-amount, amount)) for _ in range(SIZE)] for _ in range(SIZE)]


def bevel(img, x0, y0, x1, y1, light=1.12, dark=0.8):
    """Relief d'un panneau : un filet clair en haut et à gauche, une ombre en bas et à droite."""
    for x in range(x0, x1 + 1):
        img[y0][x] = shade(img[y0][x], light)
        img[y1][x] = shade(img[y1][x], dark)
    for y in range(y0 + 1, y1):
        img[y][x0] = shade(img[y][x0], light)
        img[y][x1] = shade(img[y][x1], dark)


# ---------------------------------------------------------------------------
# Textures
# ---------------------------------------------------------------------------
CREAM = "#E4DFD1"


def panel_texture(kind):
    """
    Revêtement intérieur : un panneau capitonné crème, en relief dans son joint, tenu par quatre attaches.
    kind : "plain" (deux carrés d'agrippant, une étiquette bleue), "rail" (pour la main courante) ou "vent"
    (une bouche d'aération à lamelles).
    """
    img = grain(81, CREAM, 0.035)
    # Le capitonnage : deux coutures horizontales à peine marquées.
    for y in (5, 10):
        for x in range(2, 14):
            img[y][x] = shade(img[y][x], 0.95)
    outline(img, 0, 0, 15, 15, "#B7B09D")
    bevel(img, 1, 1, 14, 14, 1.06, 0.9)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        dot(img, x, y, "#9C9583")
    if kind == "plain":
        for x0, y0 in ((4, 7), (9, 11)):
            rect(img, x0, y0, x0 + 2, y0 + 1, "#CFC8B4")
            dot(img, x0, y0, "#D9D3C1")
        rect(img, 9, 3, 12, 3, "#3F76E4")
        dot(img, 9, 3, "#6E9BF0")
    elif kind == "rail":
        rect(img, 3, 3, 6, 3, "#E8872A")
        # La barre, fine et satinée, ses embouts sombres, ses deux pattes et son ombre portée sur le capitonnage.
        for x in range(2, 14):
            dot(img, x, 9, "#7FA2CC")
            dot(img, x, 10, "#4A6E9C")
            img[11][x] = shade(img[11][x], 0.84)
        for y in (9, 10):
            dot(img, 2, y, "#3D5D85")
            dot(img, 13, y, "#3D5D85")
        for x in (3, 4, 11, 12):
            dot(img, x, 11, "#3D5D85")
    elif kind == "vent":
        rect(img, 4, 8, 11, 12, "#8E8877")
        for y in (9, 11):
            for x in range(5, 11):
                dot(img, x, y, "#3A3D42")
        outline(img, 4, 8, 11, 12, "#A9A291")
        rect(img, 4, 3, 7, 3, "#5BC27A")
    return img


def rail_texture():
    """Main courante : une barre bleu acier, satinée."""
    return plain(82, "#4A6E9C", "#5D82B0", "#3D5D85")


def floor_texture():
    """Plancher : une tôle antidérapante gris moyen, à bossettes en quinconce, bordée d'un joint sombre."""
    img = plain(83, "#8D949C", "#99A0A8", "#80878F")
    for y in range(1, 15):
        for x in range(1, 15):
            if (x + (y // 4) * 2) % 4 == 1 and y % 4 == 1:
                dot(img, x, y, "#B3BAC1")
                dot(img, x + 1, y + 1, "#6B727A")
    outline(img, 0, 0, 15, 15, "#5E656D")
    return img


def light_texture():
    """Plafonnier : une dalle de diodes derrière un diffuseur laiteux, plus claire au centre."""
    img = canvas("#DCEBF3")
    for y in range(SIZE):
        for x in range(SIZE):
            d = max(abs(x - 7.5), abs(y - 7.5)) / 7.5
            img[y][x] = gm.blend(rgb("#FFFFFF"), rgb("#CFE3EE"), d * d)
            if x % 3 == 1 and y % 3 == 1:
                img[y][x] = shade(img[y][x], 1.04)
    return img


def light_face_texture():
    """Face du plafonnier : le diffuseur laiteux, serti dans son cadre gris clair à arêtes biseautées."""
    img = light_texture()
    frame = light_frame_texture()
    for y in range(SIZE):
        for x in range(SIZE):
            if x in (0, 15) or y in (0, 15):
                img[y][x] = frame[y][x]
    bevel(img, 0, 0, 15, 15, 1.1, 0.78)
    # Le joint entre le cadre et le diffuseur : une ombre fine en haut et à gauche, le diffuseur étant en retrait.
    for i in range(1, 15):
        img[1][i] = shade(img[1][i], 0.9)
        img[i][1] = shade(img[i][1], 0.9)
    return img


def light_frame_texture():
    """Cadre du plafonnier : un profilé gris clair."""
    return plain(84, "#AEB6BF", "#BDC4CC", "#9CA4AD")


def truss_texture():
    """Poutre treillis : un profilé d'aluminium gris argent, à arêtes sombres."""
    img = plain(85, "#C3C9D0", "#D2D7DD", "#B0B7BF")
    for k in range(SIZE):
        img[0][k] = shade(img[0][k], 0.82)
        img[15][k] = shade(img[15][k], 0.82)
    return img


def hatch_texture(half):
    """
    Sas étanche, un battant de deux blocs, dessiné à plat : un cadre graphite et ses deux charnières, un battant
    blanc à panneau en creux. En haut, le hublot rond transparent dans sa bague à six boulons et la plaque bleue du
    module ; en bas, deux nervures de renfort, la plaque de consignes, la poignée et son levier orange, les deux pênes,
    le bandeau de sécurité et la plinthe d'acier brossé.
    """
    img = gm.plain(87, "#E3E6E9", "#EEF0F2", "#D2D6DA")
    for k in range(SIZE):
        img[k][0] = img[k][15] = rgb("#3A4048")
        img[k][1] = img[k][14] = rgb("#6B747F")
    # Le panneau en creux : un filet d'ombre à gauche, un filet de lumière à droite.
    for k in range(SIZE):
        img[k][3] = shade(img[k][3], 0.9)
        img[k][12] = shade(img[k][12], 1.05)
    # Les charnières, sur le montant gauche.
    for y in ((3, 4, 5) if half == "top" else (9, 10, 11)):
        img[y][1] = rgb("#9AA3AE")
        img[y][2] = rgb("#5A626C")
    if half == "top":
        for x in range(SIZE):
            img[0][x] = rgb("#3A4048")
            img[1][x] = rgb("#6B747F")
        for x in range(3, 13):
            img[3][x] = shade(img[3][x], 0.9)
        cx, cy = 7.5, 8.5
        for y in range(SIZE):
            for x in range(SIZE):
                d = math.hypot(x - cx, y - cy)
                if d <= 3.2:
                    img[y][x] = TRANSPARENT
                elif d <= 4.6:
                    light = -((x - cx) + (y - cy)) / d
                    img[y][x] = rgb("#3A4048") if d > 4.0 else shade(rgb("#8C949E"), 1.0 + 0.2 * light)
        for k in range(6):
            a = k * math.pi / 3 + math.pi / 6
            x, y = round(cx + 4.3 * math.cos(a)), round(cy + 4.3 * math.sin(a))
            dot(img, x, y, "#B9C0C7")
        # La plaque bleue du module, au-dessus du hublot.
        rect(img, 6, 2, 9, 2, "#3F76E4")
        dot(img, 6, 2, "#9FC4FF")
    else:
        for x in range(SIZE):
            img[15][x] = rgb("#3A4048")
            img[14][x] = rgb("#5A626C")
            img[13][x] = rgb("#9AA3AE") if x % 3 else rgb("#8C949E")
        # Deux nervures de renfort.
        for y in (1, 8):
            for x in range(3, 13):
                img[y][x] = shade(img[y][x], 1.07)
                img[y + 1][x] = shade(img[y + 1][x], 0.88)
        # La plaque de consignes.
        rect(img, 4, 3, 7, 6, "#F7F8F9")
        outline(img, 4, 3, 7, 6, "#9AA3AE")
        dot(img, 5, 4, "#E8C02A")
        dot(img, 6, 4, "#2E3238")
        dot(img, 5, 5, "#3F76E4")
        # La poignée : une barre sombre sur deux pattes, son levier orange.
        rect(img, 10, 3, 11, 7, "#3A4048")
        rect(img, 9, 3, 12, 3, "#5A626C")
        rect(img, 9, 7, 12, 7, "#5A626C")
        rect(img, 10, 5, 12, 5, "#E8872A")
        dot(img, 12, 6, "#B5631A")
        # Les deux pênes, côté serrure.
        rect(img, 13, 4, 13, 5, "#2E3238")
        rect(img, 13, 9, 13, 10, "#2E3238")
        # Le bandeau de sécurité, au-dessus de la plinthe.
        for x in range(3, 13):
            dot(img, x, 11, "#E8C02A" if (x // 2) % 2 == 0 else "#2E3238")
            dot(img, x, 12, "#2E3238" if (x // 2) % 2 == 0 else "#E8C02A")
    return img


def cupola_texture():
    """Vitre de coupole : un cadre épais gris acier boulonné, et un verre presque transparent, reflet en biais."""
    img = [[(160, 205, 235, 34) for _ in range(SIZE)] for _ in range(SIZE)]
    for k in range(SIZE):
        for e in (0, 15):
            img[e][k] = img[k][e] = rgb("#8C949E")
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        img[y][x] = rgb("#6B747F")
    for k in range(3, 10):
        img[k][12 - k] = (240, 250, 255, 120)
        if k < 8:
            img[k + 3][12 - k] = (230, 245, 255, 70)
    return img


def console_texture():
    """Habillage des consoles : un plastique gris bleuté mat, avec un joint."""
    return plain(88, "#B9C1CA", "#C6CDD5", "#AAB2BC", [11])


DIGITS = {
    "0": "111101101101111", "1": "010110010010111", "2": "111001111100111", "3": "111001111001111", "4": "101101111001001",
    "5": "111100111001111", "6": "111100111101111", "7": "111001001010010", "8": "111101111101111", "9": "111101111001111",
}


def terminal_screen(step, frames=8):
    """
    Écran du terminal : le plan de la station (le module central et ses modules en croix), un voyant qui palpite, une
    courbe d'énergie qui défile et deux barres d'état.
    """
    img = canvas("#07131F")
    for x in range(0, SIZE, 4):
        for y in range(0, 9):
            if (x + y) % 4 == 0:
                dot(img, x, y, "#0D2234")
    # Plan : module central et quatre modules.
    rect(img, 6, 3, 8, 5, "#7FD0FF")
    rect(img, 2, 4, 5, 4, "#4FA0DC")
    rect(img, 9, 4, 12, 4, "#4FA0DC")
    rect(img, 7, 1, 7, 2, "#4FA0DC")
    rect(img, 7, 6, 7, 7, "#4FA0DC")
    rect(img, 13, 3, 14, 5, "#5BE08A")
    dot(img, 1, 4, "#FFB23E" if step % 4 < 2 else "#7A4A12")
    # Courbe d'énergie : une sinusoïde qui défile.
    for x in range(1, 15):
        y = 11 + round(1.4 * math.sin((x + step * 2 * 16 / frames) * 2 * math.pi / 8))
        dot(img, x, y, "#5BE08A")
    for x in range(1, 15):
        dot(img, x, 14, "#16324D")
    for x in range(1, 4 + (step % frames) // 2 + 6):
        dot(img, x, 14, "#7FD0FF")
    dot(img, 14, 1, "#5BE08A" if step % 2 == 0 else "#2E6B45")
    return img


def keys_texture():
    """Clavier : des touches gris sombre en rangées, quelques-unes de couleur."""
    img = canvas("#2E3238")
    for y in range(1, 15, 3):
        for x in range(1, 15, 2):
            dot(img, x, y, "#5A626C")
            dot(img, x, y + 1, "#454C55")
    for x, y, c in ((3, 1, "#E8872A"), (11, 7, "#5BE08A"), (13, 13, "#D9534F")):
        dot(img, x, y, c)
    return img


def keycap_texture(top, side, shine):
    """Dessus de touche : une teinte franche, plus claire sur le bord du haut et à gauche, comme une touche bombée."""
    img = canvas(top)
    for k in range(SIZE):
        dot(img, k, 0, shine)
        dot(img, 0, k, shine)
        dot(img, k, SIZE - 1, side)
        dot(img, SIZE - 1, k, side)
    return img


def terminal_keys():
    """
    Clavier du terminal, en relief sur son plateau, simple et lisible : au fond, quatre grandes touches, dont une verte ;
    devant, une barre d'espace entre deux touches.
    """
    parts = []
    y0, y1 = 5.0, 5.5
    x = 1.5
    for k in range(4):
        parts.append(box(x, y0, 1.1, x + 2.9, y1, 1.8, "keycap_green" if k == 3 else "keycap"))
        x += 3.4
    for x0, x1 in ((1.5, 4.4), (4.9, 11.1), (11.6, 14.5)):
        parts.append(box(x0, y0, 0.2, x1, y1, 0.9, "keycap"))
    return parts


def air_fan_texture(step, frames=8, on=True, framed=False):
    """
    Ventilateur du générateur d'air, vu de face : un boîtier sombre, une couronne (bleu clair et lumineuse en marche),
    quatre pales claires autour d'un moyeu, derrière une grille de fines barres. En marche, les pales tournent.
    """
    img = canvas("#1C1F23")
    offset = step / frames * math.pi / 2.0
    for y in range(SIZE):
        for x in range(SIZE):
            r = math.hypot(x - 7.5, y - 7.5)
            angle = math.atan2(y - 7.5, x - 7.5) - offset
            if r > 7.4:
                if framed:
                    edge = x in (0, SIZE - 1) or y in (0, SIZE - 1)
                    dot(img, x, y, "#8C949E" if edge else "#E4E8EC")
                continue
            if r > 6.2:
                dot(img, x, y, ("#7FD0FF" if (x + y + step) % 3 else "#BFEAFF") if on else "#5A626C")
            elif r > 1.7:
                blade = math.sin(4.0 * angle + r * 0.35) > 0.3
                if blade:
                    dot(img, x, y, ("#D9DEE3" if r < 4.5 else "#B4BCC4") if on else "#8C949E")
                else:
                    dot(img, x, y, "#121417")
            else:
                dot(img, x, y, "#6B747F" if r > 0.8 else ("#7FD0FF" if on else "#3A4048"))
    # La grille : une barre sombre toutes les trois rangées, par-dessus les pales.
    for y in range(2, SIZE - 2, 3):
        for x in range(1, SIZE - 1):
            if math.hypot(x - 7.5, y - 7.5) < 6.2:
                dot(img, x, y, "#2E3238")
    return img


def life_support_top_texture(on):
    """Dessus du générateur d'air : la coque blanche, deux cartouches filtrantes cerclées de vert, et le voyant d'état."""
    img = canvas("#E4E8EC")
    for k in range(SIZE):
        for x, y in ((k, 0), (k, SIZE - 1), (0, k), (SIZE - 1, k)):
            dot(img, x, y, "#8C949E")
    for cx, cy in ((4.5, 4.5), (10.5, 10.5)):
        for y in range(SIZE):
            for x in range(SIZE):
                r = math.hypot(x - cx, y - cy)
                if r <= 3.0:
                    dot(img, x, y, "#5BC27A" if r > 2.2 else "#F4F6F8" if r > 1.0 else "#BFC6CD")
    for x, y in ((11, 3), (12, 3), (11, 4), (12, 4)):
        dot(img, x, y, "#5BE08A" if on else "#B03A3A")
    return img


def life_support(on):
    """
    Générateur d'air : sur un socle sombre, entre quatre montants d'angle, un caisson de coque blanche percé sur chaque
    face d'un ventilateur rond sous sa grille ; dessus, deux cartouches filtrantes et un voyant d'état. Il se voit de
    partout de la même façon : il n'a pas de face avant.
    """
    return [{**box(0, 0, 0, 16, 16, 16, "air_side_on" if on else "air_side_off"),
             "faces": {"up": "air_top_on" if on else "air_top_off"}}]


def led_texture(color):
    img = canvas(color)
    for k in range(SIZE):
        img[k][k] = shade(img[k][k], 1.15)
    return img


def bag_texture(seed, base, label, tag):
    """
    Sac de toile : un tissu mat, son rabat cousu en haut, deux plis, une étiquette d'inventaire et son code-barres,
    et une pastille de couleur qui dit ce qu'il contient.
    """
    img = grain(seed, base, 0.035)
    for x in range(SIZE):
        img[0][x] = shade(img[0][x], 0.86)
        img[15][x] = shade(img[15][x], 0.8)
    for y in range(SIZE):
        img[y][0] = shade(img[y][0], 0.9)
        img[y][15] = shade(img[y][15], 0.84)
    # Le rabat : un peu plus clair, fermé par une couture en pointillés.
    for y in range(1, 4):
        for x in range(1, 15):
            img[y][x] = shade(img[y][x], 1.04)
    for x in range(1, 15):
        img[4][x] = shade(img[4][x], 0.86)
        if x % 2 == 0:
            dot(img, x, 3, shade(img[3][x], 0.9))
    # Deux plis du tissu, à peine marqués.
    for y in range(6, 14):
        img[y][3] = shade(img[y][3], 0.95)
        img[y][12] = shade(img[y][12], 0.95)
    # L'étiquette, son code-barres, la pastille.
    rect(img, 6, 6, 11, 11, label)
    outline(img, 6, 6, 11, 11, "#C9CED4")
    for x in range(7, 11):
        if x % 2 == 1:
            dot(img, x, 9, "#2E3238")
            dot(img, x, 10, "#2E3238")
    dot(img, 7, 7, "#2E3238")
    dot(img, 8, 7, "#8C949E")
    rect(img, 10, 7, 10, 7, tag)
    return img


def strap_texture():
    """Sangle : une bande gris sombre, tissée, et la boucle d'acier de chaque sac (en haut de chacun d'eux)."""
    img = canvas("#4A515A")
    for y in range(SIZE):
        for x in range(SIZE):
            if (x + y) % 3 == 0:
                img[y][x] = rgb("#3A4048")
    for y in (3, 10):
        for x in range(SIZE):
            img[y][x] = rgb("#B9C0C7")
            img[y + 1][x] = rgb("#7F8790")
    return img


def bunk_bag_texture():
    """
    Sac de couchage des quartiers d'équipage : gris ardoise, matelassé en bandes, deux ouvertures pour les bras, la
    fermeture à glissière blanche au milieu, l'écusson de mission et l'étiquette du nom.
    """
    img = grain(89, "#56708E", 0.04)
    for y in range(SIZE):
        for x in range(SIZE):
            if y % 3 == 0:
                img[y][x] = shade(img[y][x], 0.88)
    for y in range(SIZE):
        img[y][0] = shade(img[y][0], 0.8)
        img[y][15] = shade(img[y][15], 0.75)
    for y in range(3, 16):
        dot(img, 8, y, "#E9EBED")
    dot(img, 8, 3, "#8C949E")
    for y in range(5, 8):
        dot(img, 2, y, "#2C3A4C")
        dot(img, 13, y, "#2C3A4C")
    rect(img, 3, 9, 5, 11, "#E8872A")
    dot(img, 4, 10, "#FFFFFF")
    rect(img, 10, 9, 13, 10, "#E9EBED")
    dot(img, 11, 10, "#3F76E4")
    return img


def bunk_liner_texture():
    """Paroi du fond des quartiers : un capitonnage gris perle, en losanges cousus, deux carrés d'agrippant."""
    img = grain(90, "#C9D0D7", 0.03)
    for y in range(SIZE):
        for x in range(SIZE):
            if (x + y) % 8 == 0 or (x - y) % 8 == 0:
                img[y][x] = shade(img[y][x], 0.93)
    for x0, y0 in ((2, 2), (12, 12)):
        rect(img, x0, y0, x0 + 1, y0 + 1, "#AEB6BF")
    return img


def bunk_side_texture():
    """
    Parois latérales des quartiers : un panneau gris clair, une bouche d'aération à lamelles, deux photos et une
    note collées à l'agrippant.
    """
    img = gm.plain(105, "#D6DBE0", "#E2E6EA", "#C9CED4")
    rect(img, 3, 3, 12, 5, "#8C949E")
    for x in range(4, 12):
        dot(img, x, 4, "#3A4048")
    rect(img, 3, 8, 6, 11, "#F4F5F6")
    rect(img, 4, 9, 5, 10, "#4F8FD0")
    dot(img, 4, 10, "#5BC27A")
    rect(img, 8, 9, 10, 12, "#F4F5F6")
    rect(img, 8, 9, 10, 10, "#E8872A")
    rect(img, 12, 10, 13, 12, "#FFE27A")
    return img


def bunk_ceiling_texture():
    """Plafond des quartiers : le ventilateur qui renouvelle l'air de la cabine, dans sa grille ronde."""
    img = gm.plain(106, "#C9CED4", "#D6DBE0", "#BCC2C9")
    for y in range(SIZE):
        for x in range(SIZE):
            d = math.hypot(x - 7.5, y - 7.5)
            if d <= 5.0:
                img[y][x] = rgb("#3A4048") if int(d) % 2 == 0 else rgb("#8C949E")
    return img


def bunk_floor_texture():
    """Plancher des quartiers : un tapis gris sombre, à petites côtes."""
    img = grain(107, "#5A626C", 0.04)
    for y in range(SIZE):
        if y % 2 == 0:
            for x in range(SIZE):
                img[y][x] = shade(img[y][x], 0.9)
    return img


def bunk_bezel_texture(upper):
    """
    Encadrement de la cabine, vu de face, sur l'une de ses deux moitiés : la coque blanche, sans joint entre les deux
    blocs ; un joint gris tout autour de l'ouverture, aux angles arrondis (en bas pour la moitié basse, en haut pour
    la haute), et un filet bleu tout en haut.
    """
    rnd = random.Random(112 if upper else 113)
    img = [[shade(rgb("#ECEEF0"), 1.0 + rnd.uniform(-0.015, 0.015)) for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(SIZE):
        img[y][0] = img[y][15] = rgb("#B4B9BF")
        img[y][1] = img[y][14] = rgb("#9AA3AE")
    edge, inner = (0, 1) if upper else (15, 14)
    for x in range(SIZE):
        img[edge][x] = rgb("#B4B9BF")
    for x in range(1, 15):
        img[inner][x] = rgb("#9AA3AE")
    img[inner][1] = img[inner][14] = rgb("#ECEEF0")
    if upper:
        for x in range(5, 11):
            img[0][x] = rgb("#3F76E4")
    return img


def bunk_side_lower_texture():
    """Flanc de la cabine, en bas : un panneau gris clair, un filet de rangement tendu, deux attaches."""
    img = gm.plain(114, "#D6DBE0", "#E2E6EA", "#C9CED4")
    for y in range(6, 12):
        for x in range(4, 12):
            if (x + y) % 2 == 0:
                img[y][x] = rgb("#8C949E")
    for x in range(4, 12):
        img[5][x] = rgb("#5A626C")
    dot(img, 3, 5, "#3A4048")
    dot(img, 12, 5, "#3A4048")
    return img


def bunk_side_upper_texture():
    """Flanc de la cabine, en haut : la bouche d'aération à lamelles, deux photos et une note collées à l'agrippant."""
    return bunk_side_texture()


def bunk_bag_art():
    """
    Le sac de couchage, sur les deux blocs (trente-deux lignes, la ligne 0 en haut) : gris ardoise, matelassé, sa
    capuche ouverte en haut, la fermeture à glissière blanche au milieu, deux fentes pour les bras, l'écusson de
    mission et l'étiquette du nom, le pied plus sombre.
    """
    rnd = random.Random(89)
    art = [[TRANSPARENT for _ in range(SIZE)] for _ in range(32)]
    for r in range(4, 28):
        for x in range(4, 12):
            art[r][x] = shade(rgb("#56708E"), 1.0 + rnd.uniform(-0.03, 0.03))
            if r % 3 == 0:
                art[r][x] = shade(art[r][x], 0.88)
        art[r][4] = shade(art[r][4], 0.82)
        art[r][11] = shade(art[r][11], 0.78)
    # La capuche, ouverte sur la doublure claire.
    for r in range(4, 8):
        for x in range(5, 11):
            art[r][x] = rgb("#C9D0D7") if 5 <= r <= 6 and 6 <= x <= 9 else rgb("#3F566F")
    for r in range(8, 27):
        art[r][8] = rgb("#E9EBED")
    art[8][8] = rgb("#8C949E")
    for r in range(12, 15):
        art[r][5] = rgb("#2C3A4C")
        art[r][10] = rgb("#2C3A4C")
    for r in (16, 17):
        art[r][5] = rgb("#E8872A")
        art[r][6] = rgb("#E8872A")
    art[16][6] = rgb("#FFFFFF")
    art[16][9] = art[16][10] = rgb("#E9EBED")
    art[17][9] = rgb("#3F76E4")
    art[17][10] = rgb("#E9EBED")
    for r in (26, 27):
        for x in range(4, 12):
            art[r][x] = rgb("#3F566F")
    return art


def bunk_icon():
    """Objet : la cabine debout, son encadrement blanc, le sac ardoise, le bandeau lumineux."""
    img = [[TRANSPARENT for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(3, 13):
            img[y][x] = rgb("#ECEEF0") if x in (3, 4, 11, 12) or y in (0, 15) else rgb("#C9D0D7")
    for x in range(5, 11):
        img[1][x] = rgb("#FFF1D6")
    for y in range(3, 14):
        for x in range(6, 10):
            img[y][x] = rgb("#56708E") if y > 4 else rgb("#EEF0F2")
        img[y][3] = rgb("#9AA3AE")
        img[y][12] = rgb("#8C949E")
    for y in range(5, 14):
        img[y][8] = rgb("#E9EBED")
    img[0][7] = img[0][8] = rgb("#3F76E4")
    return img


def bunk_door_texture():
    """Porte accordéon repliée : des panneaux gris clair, plis alternés, la poignée encastrée."""
    img = canvas("#C9CED4")
    for y in range(SIZE):
        for x in range(SIZE):
            img[y][x] = rgb("#DDE1E5") if x % 2 == 0 else rgb("#A9B1BA")
    for y in range(7, 10):
        for x in range(SIZE):
            if x % 2 == 0:
                img[y][x] = rgb("#5A626C")
    return img


def bunk_pillow_texture():
    """Appui-tête : un coussin blanc, sa sangle grise."""
    img = grain(108, "#EEF0F2", 0.03)
    for x in range(SIZE):
        dot(img, x, 7, "#8C949E")
        dot(img, x, 8, "#8C949E")
    return img


def lamp_texture():
    return led_texture("#FFF1D6")


def lab_screen(step, frames=8, on=True):
    """Écran d'une baie d'expériences : un histogramme qui monte, un point de mesure, un compteur."""
    img = canvas("#061016")
    if not on:
        for k in range(3, 9):
            dot(img, k + 4, k, "#14222C")
        return img
    for k in range(6):
        height = 3 + (k * 5 + step * (k + 1)) % 7
        rect(img, 2 + k * 2, 13 - height, 2 + k * 2, 13, "#39B5C9" if k != 3 else "#5BE08A")
    for x in range(1, 15):
        dot(img, x, 14, "#2A6F7A")
    dot(img, 13, 2, "#FFB23E" if step % 4 < 2 else "#5A3A10")
    return img


def lab_panel_texture():
    """
    Panneau de commande de la baie : un plastique blanc, un filet bleu en haut, des repères gravés autour de l'écran
    et des voyants.
    """
    img = gm.plain(109, "#E6E9EC", "#F1F3F5", "#D8DCE0")
    for x in range(1, 15):
        dot(img, x, 1, "#3F76E4")
    for x in range(2, 6):
        dot(img, x, 3, "#9AA3AE")
    dot(img, 2, 5, "#E8872A")
    return img


def lab_locker_texture():
    """
    Les deux casiers du bas de la baie, côte à côte : des façades blanches, leur joint, leurs deux loquets et
    l'étiquette de l'expérience.
    """
    img = gm.plain(110, "#E6E9EC", "#F1F3F5", "#D8DCE0")
    for y in range(SIZE):
        dot(img, 0, y, "#8C949E")
        dot(img, 7, y, "#8C949E")
        dot(img, 8, y, "#B9C0C7")
        dot(img, 15, y, "#8C949E")
    for x0 in (1, 9):
        rect(img, x0 + 1, 13, x0 + 1, 13, "#3A4048")
        rect(img, x0 + 4, 13, x0 + 4, 13, "#3A4048")
        rect(img, x0 + 2, 12, x0 + 3, 12, "#3F76E4" if x0 == 1 else "#5BC27A")
    return img


def lab_glovebox_texture():
    """
    Boîte à gants, vue au travers de sa vitre (l'image est plaquée sur un panneau quatre fois plus large que haut) :
    l'intérieur bleuté, un plateau d'échantillons et ses fioles, les deux manchons ronds où l'on passe les bras.
    """
    img = canvas("#1A2A30")
    for y in range(SIZE):
        for x in range(SIZE):
            img[y][x] = gm.blend(rgb("#2B4550"), rgb("#14222A"), y / 15.0)
    rect(img, 1, 12, 14, 14, "#7F8A97")
    for x, color in ((6, "#5BE08A"), (7, "#39B5C9"), (9, "#FFB23E"), (10, "#5BE08A")):
        rect(img, x, 8, x, 11, color)
        dot(img, x, 7, "#C9CED4")
    for cx in (3.0, 12.5):
        for y in range(SIZE):
            for x in range(SIZE):
                d = math.hypot((x - cx) / 2.0, (y - 7.5) / 6.0)
                if d <= 1.0:
                    img[y][x] = rgb("#B9C0C7") if d > 0.62 else rgb("#0B0D10")
    for k in range(5):
        dot(img, 5 + k, 4 - k // 2, shade(img[4 - k // 2][5 + k], 1.7))
    outline(img, 0, 0, 15, 15, "#3A4048")
    return img


def rack_frame_texture():
    """Cadre des baies : un profilé gris sombre, à trous de fixation."""
    img = plain(92, "#5A626C", "#6B737D", "#4A515A")
    for y in range(1, SIZE, 4):
        dot(img, 7, y, "#1E2226")
        dot(img, 8, y + 2, "#1E2226")
    return img


def tray_texture():
    """Bac de culture : un plastique blanc, uni."""
    return plain(93, "#E9EBED", "#F4F5F6", "#DDE0E3")


def mat_texture():
    """Lit de culture : une natte de fibre brun sombre, percée de trous."""
    img = grain(94, "#3F3326", 0.12)
    rnd = random.Random(95)
    for _ in range(14):
        x, y = rnd.randrange(16), rnd.randrange(16)
        dot(img, x, y, "#2A221A")
    return img


def battery_texture():
    """
    Flancs de la batterie : des panneaux blancs lisses, une grille de refroidissement à fentes verticales au milieu,
    et la plaquette de la tension en haut.
    """
    img = gm.plain(96, "#E6E9EC", "#F1F3F5", "#D8DCE0")
    bevel(img, 1, 1, 14, 14, 1.04, 0.9)
    rect(img, 4, 5, 11, 12, "#2A2F35")
    for x in range(4, 12):
        if x % 2 == 1:
            for y in range(5, 13):
                dot(img, x, y, "#5A626C")
    outline(img, 3, 4, 12, 13, "#AEB6BF")
    rect(img, 4, 2, 7, 2, "#3F76E4")
    dot(img, 10, 2, "#E8C02A")
    dot(img, 11, 2, "#2E3238")
    return img


def battery_top_texture():
    """Dessus de la batterie : le chapeau gris graphite, et la grille ronde de son ventilateur."""
    img = gm.plain(103, "#4A515A", "#565E68", "#3F454D")
    for y in range(SIZE):
        for x in range(SIZE):
            d = math.hypot(x - 7.5, y - 7.5)
            if d <= 6.0:
                ring = int(d) % 2 == 0
                img[y][x] = rgb("#1C2024") if ring else rgb("#6B747F")
            if d <= 1.5:
                img[y][x] = rgb("#8C949E")
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        dot(img, x, y, "#2A2E33")
    return img


def gauge_texture(level):
    """
    Fenêtre de la batterie, une vitre fumée : derrière, quatre modules de cellules empilés. Chacun porte son voyant
    de charge, allumé en vert (en ambre quand il n'en reste qu'un), et un filet bleu quand il est plein.
    """
    img = canvas("#0C1216")
    for y in range(SIZE):
        for x in range(SIZE):
            img[y][x] = gm.blend(rgb("#16222A"), rgb("#0A0F12"), y / 15.0)
    for k in range(4):
        y0 = 12 - k * 4
        lit = k < level
        rect(img, 1, y0, 14, y0 + 2, "#262D35")
        for x in range(2, 11):
            if x % 3 == 1:
                dot(img, x, y0 + 1, "#1A1F25")
        dot(img, 1, y0, "#3A434D")
        color = ("#5BE08A" if level > 1 else "#FFB23E") if lit else "#1F2A24"
        rect(img, 12, y0 + 1, 13, y0 + 1, color)
        if lit:
            for x in range(2, 11):
                dot(img, x, y0, "#39B5C9" if level > 1 else "#B5822E")
    # Le reflet de la vitre, en biais.
    for k in range(4):
        dot(img, 2 + k, 5 - k, shade(img[5 - k][2 + k], 1.6))
    return img


def battery_label_texture():
    """
    Façade de la batterie : le blanc de la coque, le cadre sombre de sa fenêtre, une bande bleue au pied et, au
    sommet, le petit filet jaune et noir des hautes tensions.
    """
    img = gm.plain(104, "#E6E9EC", "#F1F3F5", "#D8DCE0")
    outline(img, 2, 2, 13, 13, "#2E3238")
    for x in range(3, 12):
        dot(img, x, 1, "#E8C02A" if x % 2 else "#2E3238")
    for x in range(2, 14):
        dot(img, x, 14, "#3F76E4")
    dot(img, 12, 14, "#9FC4FF")
    return img


def port_texture():
    """Prises de puissance : une platine sombre et deux connecteurs ronds, l'un orange, l'autre bleu."""
    img = canvas("#2E3238")
    outline(img, 0, 0, 15, 15, "#4A515A")
    for cx, color in ((4.5, "#E8872A"), (11.0, "#3F76E4")):
        for y in range(SIZE):
            for x in range(SIZE):
                d = math.hypot(x - cx, y - 7.5)
                if d <= 3.4:
                    img[y][x] = rgb(color) if d > 1.8 else rgb("#15171A")
    return img


def hull_quilted_texture():
    """
    Coque, côté vide : la couverture de protection contre les micrométéorites, une toile blanche matelassée en
    carreaux cousus, bordée du même joint que la coque.
    """
    img = station_hull_texture()
    for y in range(1, 15):
        for x in range(1, 15):
            if x % 5 == 0 or y % 5 == 0:
                img[y][x] = shade(img[y][x], 0.93)
            elif x % 5 == 1 or y % 5 == 1:
                img[y][x] = shade(img[y][x], 1.03)
    return img


def hull_rail_texture():
    """Coque, côté vide : une main courante jaune pour les sorties dans l'espace, sur ses deux pattes boulonnées."""
    img = station_hull_texture()
    for x in range(2, 14):
        dot(img, x, 7, "#E8C02A")
        dot(img, x, 8, "#B8901A")
    for x in (3, 12):
        dot(img, x, 6, "#8C949E")
        dot(img, x, 9, "#6B747F")
    return img


# ---------------------------------------------------------------------------
# Porte de sas
# ---------------------------------------------------------------------------
def airlock_leaf_art():
    """
    Le battant de la porte de sas, vu de face, sur seize pixels de large et trente-deux de haut (la ligne 0 est en
    haut du bloc du dessus). Il occupe les colonnes 1 à 14 et les lignes 2 à 31 : une tôle blanche à peine nuancée,
    ses chants d'aluminium, deux joints de panneaux, un hublot aux angles arrondis (transparent) dans sa bague, un
    filet bleu, la poignée encastrée, la plaque du sas, le bandeau jaune et noir et la plinthe d'acier brossé.
    """
    rnd = random.Random(99)
    art = [[TRANSPARENT for _ in range(SIZE)] for _ in range(32)]
    for r in range(2, 32):
        for x in range(1, 15):
            art[r][x] = shade(rgb("#E6E9EC"), 1.0 + rnd.uniform(-0.015, 0.015))
    # Les chants, et le haut du battant.
    for r in range(2, 32):
        art[r][1] = art[r][14] = rgb("#B9C0C7")
        art[r][2] = shade(art[r][2], 0.95)
        art[r][13] = shade(art[r][13], 0.93)
    for x in range(1, 15):
        art[2][x] = rgb("#B9C0C7")
        art[3][x] = shade(art[3][x], 0.95)
    # Les joints des panneaux.
    for r in (15, 22):
        for x in range(3, 13):
            art[r][x] = rgb("#AEB6BF")
            art[r + 1][x] = shade(art[r + 1][x], 1.04)
    # Le hublot : un rectangle aux angles arrondis, transparent, dans une bague sombre puis claire.
    x0, x1, r0, r1 = 5, 10, 6, 12
    for r in range(r0 - 1, r1 + 2):
        for x in range(x0 - 1, x1 + 2):
            inside = x0 <= x <= x1 and r0 <= r <= r1
            corner = (x in (x0, x1)) and (r in (r0, r1))
            edge = (x in (x0 - 1, x1 + 1)) or (r in (r0 - 1, r1 + 1))
            outer_corner = (x in (x0 - 1, x1 + 1)) and (r in (r0 - 1, r1 + 1))
            if inside and not corner:
                art[r][x] = TRANSPARENT
            elif corner or (edge and not outer_corner):
                art[r][x] = rgb("#3A4048")
    for x in range(x0 - 1, x1 + 2):
        art[r0 - 2][x] = shade(art[r0 - 2][x], 0.92)
        art[r1 + 2][x] = shade(art[r1 + 2][x], 1.05)
    # Le filet bleu, la poignée encastrée, la plaque du sas.
    for x in range(3, 13):
        art[17][x] = rgb("#3F76E4")
        art[18][x] = rgb("#2F5D8A")
    for r in range(19, 22):
        art[r][11] = rgb("#3A4048")
        art[r][12] = rgb("#8C949E")
    for x in range(4, 8):
        art[20][x] = rgb("#F7F8F9")
    art[20][5] = rgb("#3F76E4")
    art[20][6] = rgb("#2E3238")
    # Le bandeau de sécurité et la plinthe.
    for x in range(2, 14):
        art[25][x] = rgb("#E8C02A") if (x // 2) % 2 == 0 else rgb("#2E3238")
        art[26][x] = rgb("#2E3238") if (x // 2) % 2 == 0 else rgb("#E8C02A")
    for r in range(27, 32):
        for x in range(2, 14):
            art[r][x] = shade(rgb("#9AA3AE"), 1.0 + (0.05 if x % 3 == 0 else 0.0) - (0.06 if r == 31 else 0.0))
    for x in range(2, 14):
        art[27][x] = rgb("#C9CED4")
    return art


def airlock_leaf_textures():
    """Les deux moitiés de l'image des battants : le haut (bloc du dessus) et le bas."""
    art = airlock_leaf_art()
    return art[0:16], art[16:32]


def airlock_frame_texture():
    """Cadre et linteau de la porte de sas : un aluminium gris clair, satiné, à boulons espacés."""
    img = plain(100, "#B9C0C7", "#C9CED4", "#AEB6BF")
    for x, y in ((3, 2), (12, 2), (3, 13), (12, 13)):
        dot(img, x, y, "#7F8790")
    return img


def airlock_track_texture():
    """Glissière des battants, à l'intérieur des montants : une gorge sombre au milieu."""
    img = plain(101, "#3A4048", "#454C55", "#30353C")
    for y in range(SIZE):
        for x in (6, 7, 8, 9):
            img[y][x] = rgb("#15171A")
        img[y][5] = rgb("#6B747F")
    return img


def airlock_threshold_texture():
    """Seuil : des bandes jaunes et noires en biais."""
    img = canvas("#25282D")
    for y in range(SIZE):
        for x in range(SIZE):
            if ((x + y) // 3) % 2 == 0:
                img[y][x] = rgb("#E8C02A")
    return img


def airlock_edge_texture():
    """Tranche d'un battant : le caoutchouc sombre du joint."""
    return plain(102, "#2E3238", "#383D44", "#25282D")


def airlock_icon():
    """Objet : la porte de sas fermée dans son cadre mince, son voyant au linteau."""
    hi, lo = airlock_leaf_textures()
    art = hi + lo
    img = [[TRANSPARENT for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(2, 14):
            img[y][x] = art[min(31, 2 + y * 2 - (y * 2) // 16)][x]
    for y in range(SIZE):
        img[y][1] = img[y][14] = rgb("#B9C0C7")
    for x in range(1, 15):
        img[0][x] = rgb("#8C949E")
    img[0][7] = img[0][8] = rgb("#5BE08A")
    return img


# ---------------------------------------------------------------------------
# Porte vitrée coulissante
# ---------------------------------------------------------------------------
GLASS_ALU = "#B9C0C7"


def glass_leaf_texture(upper):
    """
    Un battant de la porte vitrée, sur une moitié de sa hauteur : seize pixels de large, un cadre d'aluminium sur
    ses deux chants (les deux battants se rejoignent chant contre chant), le verre transparent avec quelques traits
    de reflet ; en bas, la traverse haute au-dessus du rail ; en haut, la traverse sous le linteau et, à hauteur
    d'yeux, une rangée de pastilles blanches pour qu'on voie la vitre.
    """
    img = [[TRANSPARENT for _ in range(SIZE)] for _ in range(SIZE)]
    alu, alu_dark = rgb(GLASS_ALU), rgb("#8C949E")
    for y in range(SIZE):
        img[y][0] = img[y][15] = alu
        img[y][1] = img[y][14] = alu_dark
    if upper:
        for x in range(SIZE):
            img[2][x] = alu
            img[3][x] = alu_dark
        for x in (4, 6, 9, 11):
            img[10][x] = (240, 245, 248, 255)
        for k in range(3):
            img[5 + k][7 - k] = (225, 238, 246, 255)
    else:
        for y in (13, 14):
            for x in range(SIZE):
                img[y][x] = alu
        for x in range(SIZE):
            img[12][x] = alu_dark
            img[15][x] = alu_dark
        for k in range(2):
            img[4 + k][6 - k] = (225, 238, 246, 255)
    return img


def glass_frame_texture():
    """Rail, linteau et tranches de la porte vitrée : un aluminium brossé, satiné."""
    img = gm.plain(111, GLASS_ALU, "#C9CED4", "#AEB6BF")
    return img


def glass_door_icon():
    """Objet : la porte vitrée de deux battants, leur cadre d'aluminium et le linteau à détecteur."""
    img = [[TRANSPARENT for _ in range(SIZE)] for _ in range(SIZE)]
    alu, dark = rgb(GLASS_ALU), rgb("#4A515A")
    for y in range(2, SIZE):
        for x in range(1, 15):
            img[y][x] = (170, 210, 235, 255)
    for y in range(2, SIZE):
        for x in (1, 7, 8, 14):
            img[y][x] = alu
    for x in range(1, 15):
        img[0][x] = alu
        img[1][x] = alu
        img[15][x] = alu
        img[14][x] = dark
    img[1][7] = img[1][8] = rgb("#15171A")
    for k in range(3):
        img[5 + k][5 - k] = (235, 246, 252, 255)
        img[5 + k][12 - k] = (235, 246, 252, 255)
    return img


# ---------------------------------------------------------------------------
# Établi de combinaison
# ---------------------------------------------------------------------------
def bench_front_texture(right):
    """
    Façade du meuble de l'établi, sur l'une de ses deux moitiés (seules les lignes 4 à 15 se voient, sous le plan de
    travail). À gauche, trois tiroirs à poignée en barre et étiquette ; à droite, une porte d'armoire, sa grille
    d'aération, sa poignée verticale, un filet orange et la plaque bleue de l'atelier.
    """
    img = gm.plain(115 + right, "#E6E9EC", "#F1F3F5", "#D8DCE0")
    edge = 15 if right else 0
    for y in range(SIZE):
        img[y][edge] = rgb("#B4B9BF")
    if not right:
        for top in (4, 8, 12):
            for x in range(SIZE):
                img[top][x] = rgb("#AEB6BF")
            for x in range(1, 15):
                img[top + 1][x] = shade(img[top + 1][x], 1.04)
            for x in range(5, 11):
                img[top + 2][x] = rgb("#3A4048")
            dot(img, 12, top + 2, "#3F76E4" if top == 4 else "#C9CED4")
        for y in range(4, SIZE):
            img[y][15] = rgb("#AEB6BF")
    else:
        for x in range(SIZE):
            img[4][x] = rgb("#AEB6BF")
        outline(img, 1, 5, 14, 15, "#AEB6BF")
        for y in range(7, 11):
            for x in range(4, 12):
                img[y][x] = rgb("#3A4048") if y % 2 == 1 else rgb("#8C949E")
        for y in range(8, 12):
            dot(img, 2, y, "#3A4048")
        for x in range(4, 12):
            dot(img, x, 13, "#E8872A")
        rect(img, 9, 5, 12, 5, "#3F76E4")
    return img


def bench_side_texture():
    """Flanc du meuble : la coque blanche, un panneau en creux et une petite grille d'aération."""
    img = gm.plain(117, "#E6E9EC", "#F1F3F5", "#D8DCE0")
    outline(img, 2, 5, 13, 14, "#C9CED4")
    for x in range(5, 11):
        if x % 2 == 0:
            for y in range(8, 12):
                dot(img, x, y, "#8C949E")
    return img


def bench_mat_texture():
    """Plan de travail : un tapis graphite antidérapant, quadrillé tous les quatre pixels, sa règle graduée au bord."""
    img = grain(118, "#2A2F35", 0.035)
    for k in range(SIZE):
        if k % 4 == 0:
            for j in range(SIZE):
                img[k][j] = shade(img[k][j], 1.18)
                img[j][k] = shade(img[j][k], 1.18)
    for x in range(SIZE):
        img[15][x] = rgb("#C9CED4") if x % 2 == 0 else rgb("#8C949E")
    return img


def bench_edge_texture():
    """Chant du plan de travail : le graphite, et un fin filet bleu."""
    img = canvas("#3A4048")
    for x in range(SIZE):
        img[3][x] = rgb("#3F76E4")
        img[2][x] = rgb("#454C55")
    return img


def bench_back_texture():
    """Le dosseret : un rail à outils percé, trois outils accrochés (les seules lignes 0 à 2 se voient)."""
    img = canvas("#C9CED4")
    for x in range(SIZE):
        img[0][x] = rgb("#AEB6BF")
        if x % 3 == 1:
            img[1][x] = rgb("#5A626C")
    for x, color in ((3, "#E8872A"), (8, "#3F76E4"), (12, "#E8C02A")):
        dot(img, x, 1, color)
        dot(img, x, 2, "#3A4048")
    return img


def bench_screen_texture():
    """
    La console de diagnostic, vue de dessus : la silhouette de la combinaison en filets bleus, ses modules en
    pastilles (deux en place, verts ; un en attente, ambre), et une ligne d'état.
    """
    img = canvas("#061016")
    blue, dim = rgb("#39B5C9"), rgb("#1B4A55")
    outline(img, 6, 2, 9, 4, "#39B5C9")
    outline(img, 5, 5, 10, 9, "#39B5C9")
    for y in range(6, 9):
        dot(img, 4, y, "#1B4A55")
        dot(img, 11, y, "#1B4A55")
    for y in range(10, 14):
        dot(img, 6, y, "#39B5C9")
        dot(img, 9, y, "#39B5C9")
    dot(img, 7, 3, "#5BE08A")
    dot(img, 8, 7, "#5BE08A")
    dot(img, 7, 12, "#FFB23E")
    for x in range(1, 15):
        img[15][x] = dim if x > 9 else blue
    for x in (12, 13):
        for y in (2, 4, 6):
            dot(img, x, y, "#1B4A55")
    return img


def bench_module_texture(color):
    """Un module posé sur le plateau : une coque blanche, sa bande de couleur."""
    img = canvas("#E9EBED")
    for y in range(6, 10):
        for x in range(SIZE):
            img[y][x] = rgb(color)
    outline(img, 0, 0, 15, 15, "#AEB6BF")
    return img


def bench_visor_texture():
    """Le casque posé sur l'établi, vu de face : la coque blanche et sa visière fumée cerclée d'or."""
    img = canvas("#F4F5F6")
    rect(img, 2, 4, 13, 12, "#C08F2A")
    rect(img, 3, 5, 12, 11, "#2B3A48")
    dot(img, 4, 6, "#9FC4E8")
    dot(img, 5, 6, "#9FC4E8")
    return img


def write_textures():
    write = gm.write_texture
    write("suit_workbench_front_left", [bench_front_texture(False)])
    write("suit_workbench_front_right", [bench_front_texture(True)])
    write("suit_workbench_side", [bench_side_texture()])
    write("suit_workbench_mat", [bench_mat_texture()])
    write("suit_workbench_edge", [bench_edge_texture()])
    write("suit_workbench_back", [bench_back_texture()])
    write("suit_workbench_screen", [bench_screen_texture()])
    write("suit_workbench_module", [bench_module_texture("#3F76E4")])
    write("suit_workbench_module_orange", [bench_module_texture("#E8872A")])
    write("suit_workbench_visor", [bench_visor_texture()])
    write("glass_door_leaf_lo", [glass_leaf_texture(False)])
    write("glass_door_leaf_hi", [glass_leaf_texture(True)])
    write("glass_door_frame", [glass_frame_texture()])
    hi, lo = airlock_leaf_textures()
    write("airlock_leaf_hi", [hi])
    write("airlock_leaf_lo", [lo])
    write("airlock_frame", [airlock_frame_texture()])
    write("airlock_track", [airlock_track_texture()])
    write("airlock_threshold", [airlock_threshold_texture()])
    write("airlock_edge", [airlock_edge_texture()])
    write("airlock_led_closed", [led_texture("#FF4A3D")])
    write("airlock_led_cycling", [led_texture("#FFB23E"), led_texture("#7A4A12")], frametime=6)
    write("airlock_led_open", [led_texture("#5BE08A")])
    write("station_hull_quilted", [hull_quilted_texture()])
    write("station_hull_rail", [hull_rail_texture()])
    write("station_panel", [panel_texture("plain")])
    write("station_panel_rail", [panel_texture("rail")])
    write("station_panel_vent", [panel_texture("vent")])
    write("station_rail", [rail_texture()])
    write("station_floor", [floor_texture()])
    write("station_light", [light_texture()])
    write("station_light_face", [light_face_texture()])
    write("station_truss", [truss_texture()])
    write("station_hatch_top", [hatch_texture("top")])
    write("station_hatch_bottom", [hatch_texture("bottom")])
    write("cupola_window", [cupola_texture()])
    write("station_console", [console_texture()])
    write("station_terminal_screen", [terminal_screen(k) for k in range(8)], frametime=5, interpolate=False)
    write("station_keys", [keys_texture()])
    write("life_support_side_on", [air_fan_texture(k, framed=True) for k in range(8)], 1, interpolate=False)
    write("life_support_side_off", [air_fan_texture(0, on=False, framed=True)])
    write("life_support_top_on", [life_support_top_texture(True)])
    write("life_support_top_off", [life_support_top_texture(False)])
    write("station_keycap", [keycap_texture("#A9B1BB", "#7A838E", "#D4DAE0")])
    write("station_keycap_green", [keycap_texture("#5BE08A", "#2E9A58", "#A8F2C2")])
    write("station_led", [led_texture("#7FD0FF")])
    write("cargo_bag", [bag_texture(97, "#E7E9EC", "#FFFFFF", "#3F76E4")])
    write("cargo_bag_b", [bag_texture(98, "#D9DEE3", "#F7F8F9", "#E8872A")])
    write("cargo_strap", [strap_texture()])
    write("crew_bunk_bag", [bunk_bag_texture()])
    write("crew_bunk_liner", [bunk_liner_texture()])
    write("crew_bunk_side", [bunk_side_texture()])
    write("crew_bunk_ceiling", [bunk_ceiling_texture()])
    write("crew_bunk_floor", [bunk_floor_texture()])
    write("crew_bunk_bezel_lower", [bunk_bezel_texture(False)])
    write("crew_bunk_bezel_upper", [bunk_bezel_texture(True)])
    write("crew_bunk_side_lower", [bunk_side_lower_texture()])
    write("crew_bunk_side_upper", [bunk_side_upper_texture()])
    bag = bunk_bag_art()
    write("crew_bunk_bag_upper", [bag[0:16]])
    write("crew_bunk_bag_lower", [bag[16:32]])
    write("crew_bunk_door", [bunk_door_texture()])
    write("crew_bunk_pillow", [bunk_pillow_texture()])
    write("crew_bunk_lamp", [lamp_texture()])
    write("lab_screen_off", [lab_screen(0, on=False)])
    write("lab_screen_on", [lab_screen(k) for k in range(8)], frametime=6, interpolate=False)
    write("lab_glovebox", [lab_glovebox_texture()])
    write("lab_panel", [lab_panel_texture()])
    write("lab_locker", [lab_locker_texture()])
    write("lab_led_off", [led_texture("#2A3A30")])
    write("lab_led_on", [led_texture("#5BE08A"), led_texture("#2E9A58")], frametime=10)
    write("rack_frame", [rack_frame_texture()])
    write("hydroponic_tray", [tray_texture()])
    write("hydroponic_mat", [mat_texture()])
    write("station_battery", [battery_texture()])
    write("station_battery_top", [battery_top_texture()])
    write("station_battery_label", [battery_label_texture()])
    write("station_port", [port_texture()])
    for level in range(5):
        write("battery_gauge_%d" % level, [gauge_texture(level)])


# ---------------------------------------------------------------------------
# Icônes des kits de modules
# ---------------------------------------------------------------------------
def module_icon(band, emblem):
    """
    Kit de module : un module cylindrique blanc vu de profil, ses deux bouts sombres, deux hublots, une bande de la
    couleur de son rôle et un emblème au-dessus.
    """
    img = [[TRANSPARENT for _ in range(SIZE)] for _ in range(SIZE)]
    tones = ["#FFFFFF", "#F4F5F6", "#E9EBED", "#DDE0E3", "#C9CED4", "#AEB4BB"]
    for y in range(7, 14):
        for x in range(2, 14):
            img[y][x] = rgb(tones[y - 7] if y - 7 < len(tones) else tones[-1])
    for y in range(7, 14):
        img[y][1] = img[y][14] = rgb("#4A515A")
    for x in range(2, 14):
        img[6][x] = rgb("#8C949E")
        img[14][x] = rgb("#5A626C")
    for x in (10, 11):
        for y in range(7, 14):
            img[y][x] = shade(rgb(band), 1.1 if y < 9 else 0.95 if y < 12 else 0.8)
    for x in (4, 7):
        img[9][x] = rgb("#3F76E4")
        img[10][x] = rgb("#2F5D8A")
    img[9][4] = rgb("#9FE8FF")
    for (x, y), color in emblem.items():
        dot(img, x, y, color)
    return img


def leaf(x, y):
    return {(x, y): "#5BC27A", (x + 1, y - 1): "#7FE09A", (x - 1, y - 1): "#3E9A5C", (x, y + 1): "#2E7A48"}


def kit_icons():
    icons = {}
    # Habitation : une couchette (un sac bleu) sur fond de lune.
    icons["habitation_module"] = module_icon("#3F76E4", {**{(x, 3): "#2F4E86" for x in range(5, 11)},
                                                         **{(x, 4): "#2F4E86" for x in range(5, 11)}, (5, 3): "#E4DFD1", (4, 2): "#FFE7B0"})
    # Laboratoire : une fiole qui luit.
    icons["laboratory_module"] = module_icon("#39B5C9", {(7, 1): "#C9CED4", (8, 1): "#C9CED4", (7, 2): "#C9CED4", (8, 2): "#C9CED4",
                                                         (6, 3): "#9CF5C0", (7, 3): "#9CF5C0", (8, 3): "#5BE08A", (9, 3): "#5BE08A",
                                                         (6, 4): "#5BE08A", (7, 4): "#5BE08A", (8, 4): "#2E9A58", (9, 4): "#2E9A58"})
    # Serre : des pousses vertes.
    icons["greenhouse_module"] = module_icon("#5BC27A", {**leaf(5, 3), **leaf(8, 4), **leaf(11, 3)})
    # Fret : une caisse orange.
    icons["cargo_module"] = module_icon("#E8872A", {(x, y): ("#E8872A" if (x + y) % 5 else "#B5631A") for x in range(6, 10) for y in range(1, 5)})
    # Coupole : un dôme de vitres au bout du module.
    cupola = module_icon("#8C949E", {})
    for y in range(8, 13):
        for x in (12, 13):
            cupola[y][x] = rgb("#7FC4F0") if (x + y) % 2 else rgb("#3F76E4")
        cupola[y][14] = rgb("#4A515A")
    for y, x in ((7, 12), (13, 12)):
        cupola[y][x] = rgb("#4A515A")
    icons["cupola_module"] = cupola
    # Sas : une écoutille ronde orange.
    airlock = module_icon("#E8C02A", {})
    for y in range(SIZE):
        for x in range(SIZE):
            d = math.hypot(x - 7.5, y - 3.5)
            if d <= 2.8:
                airlock[y][x] = rgb("#E8872A") if d > 1.6 else rgb("#3A4048")
    icons["airlock_module"] = airlock
    # Énergie : une poutre et deux ailes de panneaux solaires.
    power = [[TRANSPARENT for _ in range(SIZE)] for _ in range(SIZE)]
    for x in range(SIZE):
        power[8][x] = rgb("#C3C9D0")
        power[9][x] = rgb("#8C949E")
    for x0 in (1, 9):
        for x in range(x0, x0 + 6):
            for y in list(range(2, 7)) + list(range(11, 15)):
                power[y][x] = rgb("#3F76E4") if (x % 2 == 0 or y in (4, 13)) else rgb("#1F3F8F")
            power[7][x] = rgb("#9AA3AE")
            power[10][x] = rgb("#9AA3AE")
    for y in range(6, 12):
        power[y][7] = rgb("#E9EBED")
        power[y][8] = rgb("#C9CED4")
    icons["power_module"] = power
    # Sas étanche (objet) : un battant blanc à hublot.
    hatch = [[TRANSPARENT for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(4, 12):
            hatch[y][x] = rgb("#E3E6E9") if 5 <= x <= 10 else rgb("#4A515A")
    for y in (0, 15):
        for x in range(4, 12):
            hatch[y][x] = rgb("#4A515A")
    for y in range(SIZE):
        for x in range(SIZE):
            d = math.hypot(x - 7.5, y - 4.5)
            if d <= 2.3 and 5 <= x <= 10:
                hatch[y][x] = rgb("#3A4048") if d > 1.5 else TRANSPARENT
    for y in (10, 11):
        hatch[y][9] = rgb("#E8872A")
        hatch[y][10] = rgb("#E8872A")
    icons["station_hatch"] = hatch
    return icons


# ---------------------------------------------------------------------------
# Modèles
# ---------------------------------------------------------------------------
TEXTURES = {
    "hull": "station_hull", "panel": "station_panel", "panel_rail": "station_panel_rail", "panel_vent": "station_panel_vent",
    "rail": "station_rail", "floor": "station_floor", "light": "station_light", "light_face": "station_light_face",
    "truss": "station_truss", "console": "station_console", "screen": "station_terminal_screen",
    "keys": "station_keys", "led": "station_led",
    "air_side_on": "life_support_side_on", "air_side_off": "life_support_side_off",
    "air_top_on": "life_support_top_on", "air_top_off": "life_support_top_off", "keycap": "station_keycap",
    "keycap_green": "station_keycap_green", "dark": "machine_dark", "frame": "rack_frame",
    "bag": "cargo_bag", "bag_b": "cargo_bag_b", "strap": "cargo_strap",
    "sleep": "crew_bunk_bag", "liner": "crew_bunk_liner", "lamp": "crew_bunk_lamp", "cq_side": "crew_bunk_side",
    "cq_ceiling": "crew_bunk_ceiling", "cq_floor": "crew_bunk_floor", "cq_door": "crew_bunk_door",
    "pillow": "crew_bunk_pillow", "cq_side_lo": "crew_bunk_side_lower", "cq_side_hi": "crew_bunk_side_upper",
    "bezel_lo": "crew_bunk_bezel_lower", "bezel_hi": "crew_bunk_bezel_upper", "bag_lo": "crew_bunk_bag_lower",
    "bag_hi": "crew_bunk_bag_upper",
    "lab_screen": "lab_screen_off", "lab_screen_on": "lab_screen_on", "glovebox": "lab_glovebox",
    "lab_panel": "lab_panel", "locker": "lab_locker",
    "lab_led": "lab_led_off", "lab_led_on": "lab_led_on",
    "tray": "hydroponic_tray", "mat": "hydroponic_mat",
    "battery": "station_battery", "label": "station_battery_label", "port": "station_port", "battop": "station_battery_top",
    "gauge0": "battery_gauge_0", "gauge1": "battery_gauge_1", "gauge2": "battery_gauge_2", "gauge3": "battery_gauge_3",
    "gauge4": "battery_gauge_4", "plant": "station_light",
    "leaf_hi": "airlock_leaf_hi", "leaf_lo": "airlock_leaf_lo", "dframe": "airlock_frame", "track": "airlock_track",
    "sill": "airlock_threshold", "edge": "airlock_edge", "led_closed": "airlock_led_closed", "led_cycling": "airlock_led_cycling",
    "led_open": "airlock_led_open",
    "gleaf_lo": "glass_door_leaf_lo", "gleaf_hi": "glass_door_leaf_hi", "gframe": "glass_door_frame",
    "sw_front_l": "suit_workbench_front_left", "sw_front_r": "suit_workbench_front_right", "sw_side": "suit_workbench_side",
    "sw_mat": "suit_workbench_mat", "sw_edge": "suit_workbench_edge", "sw_back": "suit_workbench_back",
    "sw_screen": "suit_workbench_screen", "sw_mod": "suit_workbench_module", "sw_mod_o": "suit_workbench_module_orange",
    "sw_visor": "suit_workbench_visor",
}
EMISSIVE = {"light", "screen", "sw_screen", "led", "lamp", "lab_screen_on", "lab_led_on", "grow", "gauge1", "gauge2", "gauge3", "gauge4",
            "led_closed", "led_cycling", "led_open"}


def whole(part):
    """Pièce dont chaque face reçoit la texture entière : un écran, une vitre, une jauge."""
    return {**part, "stretch": True}


def front_only(part):
    return {**part, "only": ("north",)}


def wall_panel(kind):
    """
    Revêtement : un cube plein de coque, sa face avant capitonnée, affleurante. Le relief (joint, attaches, main
    courante de la variante « rail ») est peint dans la texture : rien ne dépasse du bloc.
    """
    return [{**box(0, 0, 0, 16, 16, 16, "hull"), "faces": {"north": {"plain": "panel", "rail": "panel_rail", "vent": "panel_vent"}[kind]}}]


# Plafonnier : un cube plein de coque, sa face avant faite du diffuseur dans son cadre.
LIGHT = [{**box(0, 0, 0, 16, 16, 16, "hull"), "faces": {"north": "light_face"}}]

FLOOR = [{**box(0, 0, 0, 16, 16, 16, "hull"), "faces": {"up": "floor"}}]


def truss():
    """
    Poutre treillis : quatre montants d'un seul tenant sur les arêtes verticales, huit longerons entre eux, une croix
    sur chaque face latérale. Rien ne dépasse du bloc : les croisillons s'arrêtent dans les angles.
    """
    parts = []
    t = 2.0
    for x0, z0 in ((0, 0), (16 - t, 0), (0, 16 - t), (16 - t, 16 - t)):
        parts.append(box(x0, 0, z0, x0 + t, 16, z0 + t, "truss"))
    for y0 in (0, 16 - t):
        parts.append(box(t, y0, 0, 16 - t, y0 + t, t, "truss"))
        parts.append(box(t, y0, 16 - t, 16 - t, y0 + t, 16, "truss"))
        parts.append(box(0, y0, t, t, y0 + t, 16 - t, "truss"))
        parts.append(box(16 - t, y0, t, 16, y0 + t, 16 - t, "truss"))
    for plane, axis in ((1.0, "z"), (15.0, "z"), (1.0, "x"), (15.0, "x")):
        parts.append(brace(axis, plane, 45 if plane < 8 else -45, "truss", half=10.2))
    return parts


TERMINAL = [
    # Le caisson, encastré dans la paroi ; son dos, dehors, est de la coque.
    {**box(0, 0, 3, 16, 16, 16, "console"), "faces": {"south": "hull"}},
    # Le pupitre, en saillie, et son clavier posé dessus, sur un plateau sombre.
    {**box(1, 2, 0, 15, 5, 3, "console"), "faces": {"up": "tray"}},
    box(0, 0, 0, 1, 16, 3, "dark"), box(15, 0, 0, 16, 16, 3, "dark"),
    box(1, 0, 0, 15, 2, 3, "dark"), box(1, 15, 0, 15, 16, 3, "dark"),
    # L'écran, dans son cadre, au-dessus du pupitre ; deux voyants sous le bandeau.
    box(1, 5, 2, 15, 15, 3, "dark"),
    front_only(whole(box(2, 6, 1.7, 14, 14.5, 2, "screen"))),
    box(12, 14.6, 1.8, 13.5, 14.9, 2, "led"),
] + terminal_keys()

def lab_rack(on):
    """
    Baie d'expériences, à la façon des baies du laboratoire de l'ISS : une armoire blanche dans ses montants graphite.
    En haut, le panneau de commande, son écran et ses voyants ; au milieu, la boîte à gants vitrée et ses deux
    manchons ; en bas, deux casiers à loquets.
    """
    screen = "lab_screen_on" if on else "lab_screen"
    led = "lab_led_on" if on else "lab_led"
    return [
        {**box(0, 0, 1, 16, 16, 16, "hull"), "only": ("south", "east", "west", "up", "down")},
        box(0, 0, 0, 1, 16, 1, "frame"), box(15, 0, 0, 16, 16, 1, "frame"),
        box(1, 15, 0, 15, 16, 1, "frame"), box(1, 0, 0, 15, 1, 1, "frame"),
        box(1, 10, 0, 15, 10.5, 1, "frame"), box(1, 4.5, 0, 15, 5, 1, "frame"),
        # Le panneau de commande : l'écran, deux voyants, un clavier.
        box(1, 10.5, 0.4, 15, 15, 1, "lab_panel"),
        front_only(whole(box(6.5, 11.1, 0.2, 14, 14.4, 0.4, screen))),
        box(3, 13.2, 0.2, 4, 14, 0.4, led), box(4.6, 13.2, 0.2, 5.6, 14, 0.4, led),
        {**box(2.5, 11.1, 0.1, 5.6, 12.4, 0.4, "dark"), "faces": {"north": "keys"}},
        # La boîte à gants, vitrée, en retrait dans son cadre.
        box(1, 5, 0.7, 15, 10, 1, "frame"),
        front_only(whole(box(1.6, 5.5, 0.5, 14.4, 9.5, 0.7, "glovebox"))),
        # Les deux casiers.
        box(1, 1, 0.4, 15, 4.5, 1, "locker"),
    ]


def tray(crop=None, stage=None):
    """Bac de culture : la cuve, son lit de culture en retrait et, s'il est planté, quatre plants."""
    parts = [
        box(0, 0, 0, 16, 5.5, 16, "tray"),
        box(1, 5.5, 1, 15, 6.2, 15, "mat"),
        box(0, 5.5, 0, 16, 7, 1, "tray"), box(0, 5.5, 15, 16, 7, 16, "tray"),
        box(0, 5.5, 1, 1, 7, 15, "tray"), box(15, 5.5, 1, 16, 7, 15, "tray"),
    ]
    if crop:
        for cx, cz in ((4.5, 4.5), (11.5, 4.5), (4.5, 11.5), (11.5, 11.5)):
            for angle in (45, -45):
                parts.append({"from": [cx - 3.5, 6.2, cz], "to": [cx + 3.5, 15.2, cz], "texture": "plant",
                              "only": ("north", "south"), "stretch": True,
                              "rotation": {"origin": [cx, 8, cz], "axis": "y", "angle": angle}})
    return parts


CARGO = [
    {**box(0, 0, 2, 16, 16, 16, "console"), "faces": {"south": "hull"}},
    box(0, 0, 0, 1, 16, 2, "frame"), box(15, 0, 0, 16, 16, 2, "frame"),
    box(1, 0, 0, 15, 1, 2, "frame"), box(1, 15, 0, 15, 16, 2, "frame"),
    box(1, 7.75, 0, 15, 8.25, 2, "frame"),
    # Deux sacs de toile sanglés, l'un sur l'autre.
    whole(box(1.5, 8.25, 0.6, 14.5, 14.6, 2, "bag")),
    whole(box(1.5, 1.4, 0.6, 14.5, 7.75, 2, "bag_b")),
    box(4, 8.25, 0.3, 5, 14.6, 0.6, "strap"), box(11, 8.25, 0.3, 12, 14.6, 0.6, "strap"),
    box(4, 1.4, 0.3, 5, 7.75, 0.6, "strap"), box(11, 1.4, 0.3, 12, 7.75, 0.6, "strap"),
]


def battery(level):
    """
    Batterie : une armoire blanche sur son socle graphite, coiffée d'un chapeau à ventilateur. Sur la façade, derrière
    une vitre fumée, les quatre modules de cellules et leurs voyants de charge, un filet de diodes bleues sur le côté ;
    une platine de prises sur chaque flanc.
    """
    return [
        box(0, 0, 0, 16, 1, 16, "frame"),
        {**box(0, 15, 0, 16, 16, 16, "frame"), "faces": {"up": "battop"}},
        {**box(1, 1, 1, 15, 15, 15, "battery"), "faces": {"north": "label", "up": "battop", "down": "frame"}},
        box(0, 1, 0, 1, 15, 1, "frame"), box(15, 1, 0, 16, 15, 1, "frame"),
        box(0, 1, 15, 1, 15, 16, "frame"), box(15, 1, 15, 16, 15, 16, "frame"),
        front_only(whole(box(3, 3, 0.6, 13, 13, 1, "gauge%d" % level))),
        front_only(box(1.6, 3, 0.7, 2.3, 13, 1, "led")),
        whole(box(15, 6, 4.5, 15.6, 9.5, 11.5, "port")), whole(box(0.4, 6, 4.5, 1, 9.5, 11.5, "port")),
    ]


# Plants des bacs, par culture : la texture du jeu à chacun des quatre stades.
CROPS = {
    "wheat": ["minecraft:block/wheat_stage1", "minecraft:block/wheat_stage3", "minecraft:block/wheat_stage5", "minecraft:block/wheat_stage7"],
    "carrot": ["minecraft:block/carrots_stage0", "minecraft:block/carrots_stage1", "minecraft:block/carrots_stage2", "minecraft:block/carrots_stage3"],
    "potato": ["minecraft:block/potatoes_stage0", "minecraft:block/potatoes_stage1", "minecraft:block/potatoes_stage2", "minecraft:block/potatoes_stage3"],
    "beetroot": ["minecraft:block/beetroots_stage0", "minecraft:block/beetroots_stage1", "minecraft:block/beetroots_stage2", "minecraft:block/beetroots_stage3"],
}


def jamb(x0, x1, inner, y0, y1, led, led_lo, led_hi):
    """
    Un montant du dormant, sur toute l'épaisseur de la paroi. Le long de l'ouverture, une colonne étroite porte un
    bandeau lumineux devant et derrière (vers la pièce et vers le vide), entre led_lo et led_hi.
    """
    strip = (x1 - 0.6, x1) if inner == "east" else (x0, x0 + 0.6)
    core = (x0, x1 - 0.6) if inner == "east" else (x0 + 0.6, x1)
    parts = [{**box(core[0], y0, 0, core[1], y1, 16, "dframe")},
             {**box(strip[0], y0, 0.4, strip[1], y1, 15.6, "dframe"), "faces": {inner: "track"}}]
    for z0, z1 in ((0, 0.4), (15.6, 16)):
        parts.append(box(strip[0], led_lo, z0, strip[1], led_hi, z1, led))
        if led_lo > y0:
            parts.append(box(strip[0], y0, z0, strip[1], led_lo, z1, "dframe"))
        if led_hi < y1:
            parts.append(box(strip[0], led_hi, z0, strip[1], y1, z1, "dframe"))
    return parts


def airlock_frame(upper, phase):
    """
    Cadre de la porte de sas : deux montants d'un pixel, six d'épaisseur, au milieu de la paroi ; en bas, le seuil
    rayé ; en haut, le linteau et son voyant de chaque côté (rouge fermée, ambre en cycle, vert ouverte). Fermée ou en
    cycle, la porte porte son battant ; ouverte, c'est le rendu qui le dessine, glissé dans la paroi.
    """
    led = "led_" + ("cycling" if phase == "closing" else phase)
    top = 14 if upper else 16
    parts = [box(0, 0, 5, 1, top, 11, "dframe"), box(15, 0, 5, 16, top, 11, "dframe")]
    if upper:
        parts += [box(0, 14, 5, 16, 16, 11, "dframe"),
                  box(5, 14.5, 4.7, 11, 15.5, 5, led), box(5, 14.5, 11, 11, 15.5, 11.3, led)]
    else:
        parts.append({**box(1, 0, 5, 15, 0.5, 11, "dframe"), "faces": {"up": "sill"}})
    if phase in ("closed", "cycling"):
        for leaf in airlock_leaf(False):
            if upper and leaf["to"][1] > 16:
                parts.append({**leaf, "from": [leaf["from"][0], leaf["from"][1] - 16, leaf["from"][2]],
                              "to": [leaf["to"][0], leaf["to"][1] - 16, leaf["to"][2]]})
            elif not upper and leaf["to"][1] <= 16:
                parts.append(leaf)
    return parts


# Hauteur du bas de l'écoutille à chaque position (comme AirlockDoorBlock.STEP_HEIGHT), et haut du passage.
HATCH_STEPS = (0, 10, 20, 30)
HATCH_TOP = 29.5


def airlock_hatch(upper, step, led):
    """
    Porte de sas à écoutille. Le cadre : deux montants de deux pixels, huit d'épaisseur, la glissière sombre côté
    passage ; devant et derrière, un rebord d'un pixel de plus, en léger relief, qui dessine le contour de la porte sur la
    paroi ; en bas, le seuil rayé, d'un pixel de haut ; en haut, le caisson du linteau où l'écoutille se range, et son
    voyant de chaque côté (rouge fermée, ambre en cycle, vert ouverte). L'écoutille, d'un seul tenant, à hublot, remonte
    dans le caisson en trois temps ; sa texture la suit.
    """
    base = 16 if upper else 0
    top = 13.5 if upper else 16
    parts = [
        {**box(0, 0, 4, 2, top, 12, "dframe"), "faces": {"east": "track"}},
        {**box(14, 0, 4, 16, top, 12, "dframe"), "faces": {"west": "track"}},
    ]
    for z0, z1 in ((3.5, 4), (12, 12.5)):
        parts += [box(0, 0, z0, 3, top, z1, "dframe"), box(13, 0, z0, 16, top, z1, "dframe")]
    if upper:
        parts += [box(0, 13.5, 3.5, 16, 16, 12.5, "dframe"),
                  box(5, 14, 3.2, 11, 15.5, 3.5, "led_" + led), box(5, 14, 12.5, 11, 15.5, 12.8, "led_" + led)]
    else:
        parts.append({**box(2, 0, 4, 14, 1, 12, "dframe"), "faces": {"up": "sill"}})
    bottom = HATCH_STEPS[step] + 1
    lo = max(bottom, base)
    hi = min(bottom + 29.0, HATCH_TOP, base + 16)
    # L'écoutille est peinte en deux textures (bas et haut, coupées à seize pixels de son pied) : chaque morceau visible
    # reçoit la portion qui lui revient, pour que le hublot monte avec elle.
    if hi - lo < 0.01:
        return parts
    cuts = sorted({lo, hi, min(max(bottom + 16.0, lo), hi)})
    side = {"east": "edge", "west": "edge", "up": "edge", "down": "edge"}
    for y0, y1 in zip(cuts, cuts[1:]):
        if y1 - y0 < 0.01:
            continue
        l0, l1 = y0 - bottom, y1 - bottom
        texture, offset = ("leaf_lo", 0.0) if l1 <= 16.0 else ("leaf_hi", 16.0)
        v0, v1 = 16.0 - (l1 - offset), 16.0 - (l0 - offset)
        parts.append({**box(2, y0 - base, 6.5, 14, y1 - base, 9.5, texture), "faces": side,
                      "uv": {"north": [2, v0, 14, v1], "south": [14, v0, 2, v1]}})
    return parts


def airlock_leaf(right):
    """
    Le battant de la porte de sas : quatorze pixels de large, du seuil au linteau, trois d'épaisseur, au milieu de la
    paroi. Le rendu de la porte le fait coulisser vers l'est du modèle, dans le montant et la paroi voisine. (Il n'y
    a qu'un battant : les deux modèles sont le même.)
    """
    side = {"east": "edge", "west": "edge", "up": "edge", "down": "edge"}
    return [
        {**box(1, 0.5, 6.5, 15, 16, 9.5, "leaf_lo"), "faces": side, "only": ("north", "south", "east", "west", "down")},
        {**box(1, 16, 6.5, 15, 30, 9.5, "leaf_hi"), "faces": side, "only": ("north", "south", "east", "west", "up")},
    ]


# ---------------------------------------------------------------------------
# Quartiers d'équipage : une cabine d'un bloc de large et deux de haut.
# ---------------------------------------------------------------------------
def crew_lower():
    """Le bas de la cabine : le fond capitonné, les flancs, le plancher, la porte repliée, le bas du sac de couchage."""
    return [
        {**box(0, 0, 13, 16, 16, 16, "hull"), "faces": {"north": "liner"}},
        {**box(0, 0, 0, 2, 16, 13, "hull"), "faces": {"east": "cq_side_lo", "north": "bezel_lo"}},
        {**box(14, 0, 0, 16, 16, 13, "hull"), "faces": {"west": "cq_side_lo", "north": "bezel_lo"}},
        {**box(2, 0, 0, 14, 2, 13, "hull"), "faces": {"up": "cq_floor", "north": "bezel_lo"}},
        box(2, 2, 0.5, 4, 16, 2.5, "cq_door"),
        box(4, 4, 11.5, 12, 16, 13, "bag_lo"),
        box(3.5, 9, 11, 12.5, 9.8, 11.5, "strap"),
        whole(box(13.2, 5, 4, 14, 9, 9, "bag")),
    ]


def crew_upper():
    """Le haut de la cabine : le plafond et son ventilateur, le bandeau de diodes, le haut du sac et son appui-tête, la tablette."""
    return [
        {**box(0, 0, 13, 16, 16, 16, "hull"), "faces": {"north": "liner"}},
        {**box(0, 0, 0, 2, 16, 13, "hull"), "faces": {"east": "cq_side_hi", "north": "bezel_hi"}},
        {**box(14, 0, 0, 16, 16, 13, "hull"), "faces": {"west": "cq_side_hi", "north": "bezel_hi"}},
        {**box(2, 14, 0, 14, 16, 13, "hull"), "faces": {"down": "cq_ceiling", "north": "bezel_hi"}},
        box(2, 0, 0.5, 4, 14, 2.5, "cq_door"),
        box(4.5, 13.6, 1, 13.5, 14, 2, "lamp"),
        box(4, 0, 11.5, 12, 12, 13, "bag_hi"),
        box(5, 8.5, 11, 11, 11, 11.5, "pillow"),
        box(3.5, 3, 11, 12.5, 3.8, 11.5, "strap"),
        box(12.5, 4, 4, 14, 4.6, 5, "dark"),
        {**box(11.6, 2.5, 3, 12.5, 6.5, 7, "dark"), "faces": {"west": "screen"}},
    ]


# ---------------------------------------------------------------------------
# Porte vitrée : deux blocs de large, deux de haut ; un battant par colonne.
# ---------------------------------------------------------------------------
def glass_door(upper, right=False):
    """
    Porte vitrée, sans ses battants (le rendu les dessine) : en bas, le rail encastré au sol ; en haut, le linteau
    d'aluminium, et la moitié du détecteur sous son bord, côté milieu de la porte (à l'ouest du modèle pour la colonne
    de gauche, à l'est pour celle de droite).
    """
    if not upper:
        return [box(0, 0, 6.5, 16, 0.5, 9.5, "gframe")]
    x0, x1 = (14, 16) if right else (0, 2)
    return [box(0, 14, 5.5, 16, 16, 10.5, "gframe"),
            box(x0, 13.3, 5.5, x1, 14, 6.3, "dark"), box(x0, 13.3, 9.7, x1, 14, 10.5, "dark")]


def glass_leaf():
    """
    Un battant de la porte vitrée : toute la largeur de sa colonne, du rail au linteau (deux blocs moins le linteau),
    un pixel et demi d'épaisseur, au milieu du bloc. Les tranches sont en aluminium.
    """
    side = {"east": "gframe", "west": "gframe", "up": "gframe", "down": "gframe"}
    return [
        {**box(0, 0.5, 7.25, 16, 16, 8.75, "gleaf_lo"), "faces": side, "only": ("north", "south", "east", "west", "down")},
        {**box(0, 16, 7.25, 16, 30, 8.75, "gleaf_hi"), "faces": side, "only": ("north", "south", "east", "west", "up")},
    ]


def bench_half(right):
    """
    Une moitié de l'établi de combinaison, face avant au nord. L'établi se dessine d'un bout à l'autre sur 32 pixels,
    de gauche à droite pour qui lui fait face (u) ; la moitié gauche en prend les 16 premiers, la droite les suivants.
    Vu de face, la droite du joueur est à l'ouest du modèle : u se change en x = 16 - u (ou 32 - u pour la droite).
    Le meuble, son plan de travail et son dosseret courent d'un bout à l'autre ; sur la moitié gauche, le plateau
    des modules et un casque ; sur la droite, la console de diagnostic, sa rampe lumineuse et une clé.
    """
    span = (16, 32) if right else (0, 16)
    front = "sw_front_r" if right else "sw_front_l"
    pieces = [
        # Le socle en retrait, le meuble, le plan de travail et le dosseret.
        (1, 31, 0, 1, 3, 15, "dark", {}),
        (0.5, 31.5, 1, 12, 2, 15.5, "hull", {"north": front, "east": "sw_side", "west": "sw_side"}),
        (0, 32, 12, 13.5, 0.5, 16, "sw_edge", {"up": "sw_mat", "down": "dark"}),
        (0, 32, 13.5, 16, 15, 16, "sw_back", {"up": "dark", "south": "hull"}),
        # Le plateau des modules, trois modules et un casque.
        (2, 11, 13.5, 14, 4, 10, "dark", {}),
        (3, 5, 14, 15.5, 5, 7, "sw_mod", {}),
        (6, 8, 14, 15, 5, 7, "sw_mod_o", {}),
        (9, 10.5, 14, 15.5, 7, 9, "sw_mod", {}),
        (12, 15, 13.5, 16, 7, 10.5, "hull", {"north": "sw_visor"}),
        # La console : son pied, son écran tourné vers le haut, la rampe lumineuse au dosseret, une clé.
        (19, 30, 13.5, 14.5, 6, 13, "dark", {}),
        (19.5, 29.5, 14.5, 14.8, 6.5, 12.5, "sw_screen", {}),
        (18, 30, 15, 15.5, 14.6, 15, "led", {}),
        (25, 30.5, 13.5, 13.9, 2, 3, "rail", {}),
    ]
    stretched = {"sw_mod", "sw_mod_o", "sw_screen", "sw_visor"}
    parts = []
    for u0, u1, y0, y1, z0, z1, texture, faces in pieces:
        a, b = max(u0, span[0]), min(u1, span[1])
        if a >= b:
            continue
        x0, x1 = span[1] - b, span[1] - a
        part = {**box(x0, y0, z0, x1, y1, z1, texture), "faces": dict(faces)}
        # Les bouts du meuble : seule la moitié qui porte le bout en garde le flanc.
        if texture in stretched and a == u0 and b == u1:
            part["stretch"] = True
        if texture == "sw_screen":
            part["only"] = ("up",)
        if texture == "led":
            part["only"] = ("north",)
        parts.append(part)
    return parts


def models():
    out = {
        "station_panel": wall_panel("plain"), "station_panel_rail": wall_panel("rail"), "station_panel_vent": wall_panel("vent"),
        "station_light": LIGHT, "station_floor": FLOOR, "station_truss": truss(), "station_terminal": TERMINAL,
        "crew_bunk_lower": crew_lower(), "crew_bunk_upper": crew_upper(), "lab_rack": lab_rack(False), "lab_rack_on": lab_rack(True), "hydroponic_tray": tray(),
        "cargo_rack": CARGO, "life_support": life_support(False), "life_support_on": life_support(True),
    }
    for level in range(5):
        out["station_battery_%d" % level] = battery(level)
    for step in range(len(HATCH_STEPS)):
        for led in ("closed", "cycling", "open"):
            out["airlock_door_lower_%d_%s" % (step, led)] = airlock_hatch(False, step, led)
            out["airlock_door_upper_%d_%s" % (step, led)] = airlock_hatch(True, step, led)
    out["airlock_leaf_left"] = airlock_leaf(False)
    out["airlock_leaf_right"] = airlock_leaf(True)
    out["glass_door_lower"] = glass_door(False)
    out["glass_door_upper_left"] = glass_door(True, False)
    out["glass_door_upper_right"] = glass_door(True, True)
    out["glass_door_leaf"] = glass_leaf()
    out["suit_workbench_left"] = bench_half(False)
    out["suit_workbench_right"] = bench_half(True)
    # L'objet : l'établi entier, ses deux moitiés côte à côte (la droite à l'ouest de la gauche), réduit dans la case.
    out["suit_workbench_item"] = bench_half(False) + [
        {**part, "from": [part["from"][0] - 16, part["from"][1], part["from"][2]], "to": [part["to"][0] - 16, part["to"][1], part["to"][2]]}
        for part in bench_half(True)]
    for crop in CROPS:
        for stage in range(4):
            out["hydroponic_tray_%s_%d" % (crop, stage)] = tray(crop, stage)
    return out


def write_models():
    os.makedirs(STATION_DIR, exist_ok=True)
    total = 0
    for name, parts in models().items():
        for part in parts:
            assert all(a <= b for a, b in zip(part["from"], part["to"])), (name, part)
        parts = separate(parts)
        found = check_overlaps(name, parts)
        total += found
        model = build_tiled(parts, TEXTURES, EMISSIVE, particle="orbitalis:block/station_hull")
        if name.startswith("airlock_"):
            # Les battants sont au fond d'un dormant épais : l'ombrage de contact du jeu les noircirait.
            model["ambientocclusion"] = False
        if name == "suit_workbench_item":
            model["display"] = display([-16, 0, 0], [16, 16, 16], fill=1.15)
        if name.startswith("hydroponic_tray_"):
            _, _, crop, stage = name.split("_")
            model["textures"]["plant"] = CROPS[crop][int(stage)]
        with open(os.path.join(STATION_DIR, name + ".json"), "w", newline="\n") as f:
            json.dump(model, f, indent=1)
    return total


if __name__ == "__main__":
    write_textures()
    for name, img in kit_icons().items():
        write_png(os.path.join(ITEM_DIR, name + ".png"), img)
    write_png(os.path.join(ITEM_DIR, "airlock_door.png"), airlock_icon())
    write_png(os.path.join(ITEM_DIR, "glass_door.png"), glass_door_icon())
    write_png(os.path.join(ITEM_DIR, "crew_bunk.png"), bunk_icon())
    overlaps = write_models()
    print("Station : textures, icônes et modèles écrits, %d chevauchement(s)." % overlaps)
