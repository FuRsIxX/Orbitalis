#!/usr/bin/env python3
"""
Génère le modèle et les textures de la capsule habitée d'Orbitalis.

C'est une capsule à trois places, de forme tronconique, bâtie en gradins : une large section basse où l'équipage
est assis, puis deux épaulements qui la resserrent jusqu'au collier d'amarrage. Elle fait deux blocs de haut et un
peu moins de deux de large.

Dehors : un bouclier thermique à tuiles sombres, une coque de panneaux blancs à joints fins, une ceinture de
tuiles noires au ras du bouclier, un liseré bleu, un pare-brise devant le pilote, une grande baie sur chaque
flanc, la porte d'accès à l'arrière, un bloc de petits propulseurs sur chaque face de l'épaulement, et le collier
d'amarrage dentelé au sommet.

Dedans : trois sièges baquets, celui du pilote devant, ceux des équipiers derrière lui de part et d'autre. Tous
sont assis droit, face au nord. Devant le pilote, sous le pare-brise, le grand écran de bord et son pupitre
tactile ; sous chaque baie, un écran d'état ; à sa main droite, le manche. Un filet de lumière court sous le
plafond.

Les parois sont de vraies cloisons d'un pixel, claires dehors, capitonnées dedans. Les textures gardent partout
des pixels de la taille de ceux des blocs : aucune n'est étirée, sauf les vitres et les écrans, qui sont des
images entières. Aucune face n'en recouvre une autre.

Le même modèle sert au tronçon « capsule » de la fusée et à la capsule en vol libre.

Usage : python tools/generate_capsule.py
        (à lancer après generate_machine_models.py, dont il réutilise les outils de dessin)
"""
import json
import os
import random

import generate_machine_models as gm
from generate_textures import SIZE, rgb, rocket_skin
from model_kit import build_tiled, check_overlaps

# Demi-enveloppes des trois gradins : de LO à HI, en pixels, autour du centre du bloc (8).
LOW = (-6, 22)
MID = (-4, 20)
TOP = (-1, 17)
# Hauteurs : le plancher, le haut de la section basse, les deux épaulements, le toit.
FLOOR = 2
DECK = 20
SHOULDER = 25
ROOF = 29
# Le siège du pilote, puis ceux des deux équipiers : centre de l'assise (x, z). Le nord (z petit) est devant.
SEATS = ((8, 9.5), (1.5, 15), (14.5, 15))


# ---------------------------------------------------------------------------
# Textures
# ---------------------------------------------------------------------------
def canvas(color):
    return [[rgb(color) for _ in range(SIZE)] for _ in range(SIZE)]


def smooth(seed, color, amount=0.012):
    """Une peinture lisse : un grain à peine visible, plus claire en haut qu'en bas."""
    img = grained(seed, color, amount)
    for y in range(SIZE):
        for x in range(SIZE):
            img[y][x] = tone(img[y][x], 1.03 - 0.05 * y / 15.0)
    return img


def hull_texture(belt=False):
    """
    Coque : un blanc lisse, un seul joint de panneau par bloc, à peine marqué. Avec `belt`, le bas du panneau est une
    ceinture graphite unie, soulignée d'un filet d'acier : elle fait le lien avec le bouclier.
    """
    img = smooth(81, "#EEF0F2")
    for y in range(SIZE):
        img[y][0] = tone(img[y][0], 0.9)
    if belt:
        dark = smooth(85, "#2B2F35", 0.02)
        for y in range(12, SIZE):
            img[y] = list(dark[y])
        for x in range(SIZE):
            img[11][x] = rgb("#9AA2AB")
            img[12][x] = tone(dark[12][x], 1.25)
    return img


def shield_texture():
    """Bouclier thermique : un ablatif graphite uni, à peine roussi."""
    img = smooth(402, "#26292E", 0.03)
    rnd = random.Random(402)
    for _ in range(18):
        x, y = rnd.randrange(SIZE), rnd.randrange(SIZE)
        img[y][x] = tone(img[y][x], 1.0 + rnd.choice((-0.12, 0.1)))
    return img


def glass_texture():
    """
    Vitre : un joint noir, doublé d'un filet d'acier, et deux reflets en biais. Tout le reste est entièrement
    transparent : aucun pixel à demi transparent, pour ne pas faire disparaître l'eau et les nuages vus au travers.
    """
    clear = (0, 0, 0, 0)
    img = [[clear for _ in range(SIZE)] for _ in range(SIZE)]
    for k in range(SIZE):
        img[0][k] = img[SIZE - 1][k] = img[k][0] = img[k][SIZE - 1] = rgb("#111316")
    for k in range(1, SIZE - 1):
        img[1][k] = img[SIZE - 2][k] = img[k][1] = img[k][SIZE - 2] = rgb("#4A525C")
    for k in range(3, 8):
        img[k][11 - k] = rgb("#E4F2FB")
    for k in range(9, 12):
        img[k][22 - k] = rgb("#C9DDEA")
    return img


def accent_texture():
    """Liseré : le bleu nuit des réservoirs des fusées, entre deux filets d'aluminium."""
    img = canvas("#1F3F8F")
    for x in range(SIZE):
        img[0][x] = rgb("#C9CDD1")
        img[1][x] = rgb("#2C55B8")
        img[SIZE - 1][x] = rgb("#C9CDD1")
    return img


def dock_texture():
    """Collier d'amarrage : un anneau d'acier satiné, une gorge sombre à mi-hauteur."""
    img = smooth(91, "#A7AFB8", 0.02)
    for x in range(SIZE):
        img[0][x] = rgb("#D2D8DE")
        img[7][x] = rgb("#5B636D")
        img[8][x] = rgb("#C3CAD1")
        img[SIZE - 1][x] = rgb("#6B737D")
    return img


def hatch_texture():
    """
    Porte d'accès : le blanc de la coque, cerné d'un joint graphite, un hublot rond et une poignée encastrée soulignée
    d'orange.
    """
    img = smooth(81, "#EEF0F2")
    for k in range(SIZE):
        img[0][k] = img[SIZE - 1][k] = img[k][0] = img[k][SIZE - 1] = rgb("#3A3F46")
    for y in range(SIZE):
        for x in range(SIZE):
            d = (x - 7.5) ** 2 + (y - 5.5) ** 2
            if d < 4.5:
                img[y][x] = rgb("#101820") if (x + y) % 5 else rgb("#3A566E")
            elif d < 8:
                img[y][x] = rgb("#7F8893")
    for x in range(9, 13):
        img[11][x] = rgb("#3A3F46")
        img[12][x] = rgb("#F08A24")
    return img


def tone(color, factor):
    """La même couleur, plus claire (facteur > 1) ou plus sombre (facteur < 1)."""
    c = rgb(color) if isinstance(color, str) else color
    return tuple(max(0, min(255, int(round(v * factor)))) for v in c[:3]) + (255,)


def grained(seed, color, amount):
    """Une teinte unie à grain très fin : rien ne doit faire damier."""
    rnd = random.Random(seed)
    return [[tone(color, 1.0 + rnd.uniform(-amount, amount)) for _ in range(SIZE)] for _ in range(SIZE)]


def liner_texture():
    """
    Habillage intérieur : deux coussins gris perle par bloc, rembourrés (un rien plus clairs au centre), séparés par
    une couture creusée soulignée d'un filet de lumière ; une couture verticale à points à chaque bloc. Le contraste
    reste faible : de près comme de loin, c'est une paroi douce, jamais un damier.
    """
    img = grained(181, "#B9BDBF", 0.012)
    for y in range(SIZE):
        local = y % 8
        puff = 1.0 + 0.045 * (1.0 - abs(local - 3.5) / 3.5)
        for x in range(SIZE):
            img[y][x] = tone(img[y][x], puff)
        if local == 0:
            for x in range(SIZE):
                img[y][x] = tone(img[y][x], 1.06)
        if local == 7:
            for x in range(SIZE):
                img[y][x] = tone(img[y][x], 0.84)
    for y in range(SIZE):
        img[y][0] = tone(img[y][0], 0.88)
        img[y][1] = tone(img[y][1], 1.03)
        if y % 2 == 0:
            img[y][0] = tone(img[y][0], 0.92)
    return img


def floor_texture():
    """Plancher : un caillebotis gris graphite à mailles fines, ses barreaux éclairés d'un côté, ombrés de l'autre."""
    img = grained(195, "#15171B", 0.03)
    for y in range(SIZE):
        for x in range(SIZE):
            if x % 4 == 0:
                img[y][x] = tone("#3A3F46", 1.0)
            elif x % 4 == 1:
                img[y][x] = tone("#2A2E34", 1.0)
            if y % 4 == 0:
                img[y][x] = tone("#454B53", 1.0)
    return img


def seat_texture():
    """
    Sellerie : un cuir technique charbon, matelassé en bandes horizontales (chaque bande bombée, sa couture
    piquée), bordé d'un passepoil orange.
    """
    img = grained(207, "#24272C", 0.02)
    for y in range(SIZE):
        local = y % 4
        for x in range(1, SIZE - 1):
            img[y][x] = tone(img[y][x], (1.10, 1.04, 0.98, 0.78)[local])
            if local == 3 and x % 2 == 0:
                img[y][x] = tone("#4A5058", 1.0)
        img[y][0] = tone("#F08A24", 0.92 if y % 2 else 1.0)
        img[y][SIZE - 1] = tone("#F08A24", 0.85)
    return img


def px(img, x, y, color):
    if 0 <= x < SIZE and 0 <= y < SIZE:
        img[y][x] = rgb(color) if isinstance(color, str) else color


def panel_texture():
    """
    Planche de bord : un aluminium anodisé gris anthracite, brossé très finement, aux arêtes chanfreinées (claire en
    haut et à gauche, sombre en bas et à droite), et ses quatre vis noyées.
    """
    img = grained(411, "#3B4047", 0.02)
    for y in range(SIZE):
        streak = 1.0 + 0.015 * ((y * 7) % 5 - 2)
        for x in range(SIZE):
            img[y][x] = tone(img[y][x], streak)
    for k in range(SIZE):
        img[0][k] = tone(img[0][k], 1.22)
        img[k][0] = tone(img[k][0], 1.12)
        img[SIZE - 1][k] = tone(img[SIZE - 1][k], 0.72)
        img[k][SIZE - 1] = tone(img[k][SIZE - 1], 0.8)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px(img, x, y, "#1C1F23")
        px(img, x - 1, y - 1, tone("#3B4047", 1.25))
    return img


def bezel_texture():
    """Lunette d'instrument : un noir laqué, son chanfrein d'acier en haut, et un reflet discret."""
    img = grained(412, "#111316", 0.015)
    for k in range(SIZE):
        img[0][k] = tone("#4A515A", 1.0)
        img[1][k] = tone("#22262B", 1.0)
        img[SIZE - 1][k] = tone("#08090B", 1.0)
    for k in range(3, 9):
        px(img, k, 3, tone("#111316", 1.6))
    return img


def dial_texture():
    """
    Afficheur rond, à cristaux liquides : un fond noir bleuté, sa couronne gravée, et la piste éteinte de la jauge
    en arc (le rendu y allume les segments et pose l'aiguille).
    """
    import math
    img = canvas("#070B10")
    for y in range(SIZE):
        for x in range(SIZE):
            d = math.hypot(x - 7.5, y - 7.5)
            if d > 7.6:
                px(img, x, y, "#111316")
            elif d > 6.9:
                px(img, x, y, "#3A4048")
            elif 4.6 < d < 6.2:
                a = math.degrees(math.atan2(x - 7.5, 7.5 - y))
                if -135 <= a <= 135:
                    px(img, x, y, "#14212B")
    return img


def globe_face_texture():
    """Globe de navigation éteint : un verre bleu nuit (le globe vivant est dessiné par-dessus)."""
    import math
    img = canvas("#15171A")
    for y in range(SIZE):
        for x in range(SIZE):
            if math.hypot(x - 7.5, y - 7.5) <= 7.4:
                px(img, x, y, "#0A1626")
    return img


def switches_texture():
    """
    Pupitre moderne : deux rangées de touches rétroéclairées (un capuchon sombre bombé, sa légende d'un trait blanc
    doux, son témoin vert ou ambre), sur l'aluminium anthracite de la planche.
    """
    img = panel_texture()
    for row, y0 in enumerate((2, 9)):
        for k, x0 in enumerate(range(1, 15, 4)):
            for y in range(y0, y0 + 4):
                for x in range(x0, x0 + 3):
                    px(img, x, y, "#25292E")
            for x in range(x0, x0 + 3):
                px(img, x, y0, "#353A40")
                px(img, x, y0 + 3, "#16191C")
            px(img, x0, y0 + 2, "#AEB8C2")
            px(img, x0 + 1, y0 + 2, "#AEB8C2")
            lit = (row + k) % 3
            px(img, x0 + 2, y0 + 1, "#5BE08A" if lit == 0 else "#FFB23E" if lit == 1 else "#1C2420")
    return img


def breakers_texture():
    """Disjoncteurs : des rangées de boutons noirs ronds dans leur collerette d'acier, sous un bandeau d'étiquettes."""
    img = panel_texture()
    for y in (3, 9):
        for x in range(2, 14, 3):
            px(img, x, y, "#8C949E")
            px(img, x + 1, y, "#6B737D")
            px(img, x, y + 1, "#121417")
            px(img, x + 1, y + 1, "#1C1F23")
        for x in range(1, 15):
            px(img, x, y - 2, tone("#3B4047", 1.18) if x % 3 else "#3B4047")
    return img


def overhead_texture():
    """Panneau de plafond : des interrupteurs sous capot de garde rayé, une rangée de voyants ambre et verts."""
    img = switches_texture()
    for x in range(1, 15, 5):
        for k in range(3):
            px(img, x + k, 5, "#E8C02A" if k % 2 == 0 else "#25282D")
    for x in range(2, 14, 2):
        px(img, x, 15, "#FFB23E" if x % 4 == 0 else "#5BE08A")
    return img


def annunciator_texture():
    """Bandeau d'alarmes, éteint : des fenêtres de voyant sombres, cerclées de noir (le rendu les allume)."""
    img = canvas("#2A2E33")
    for x in range(SIZE):
        px(img, x, 0, "#3A3F46")
        px(img, x, 15, "#15171A")
    for x0 in range(0, 16, 4):
        for y in range(4, 12):
            for x in range(x0 + 1, x0 + 3):
                px(img, x, y, "#1A1416")
    return img


def shell_texture():
    """Coque de siège : un composite blanc satiné, plus clair en haut, son arête vive et un fin trait de joint."""
    img = grained(415, "#DCE0E4", 0.01)
    for y in range(SIZE):
        for x in range(SIZE):
            img[y][x] = tone(img[y][x], 1.04 - 0.08 * y / 15.0)
    for k in range(SIZE):
        img[k][0] = tone(img[k][0], 0.8)
        img[0][k] = tone(img[0][k], 1.05)
        img[k][SIZE - 1] = tone(img[k][SIZE - 1], 0.86)
    for x in range(3, 13):
        img[11][x] = tone(img[11][x], 0.9)
    return img


def helmet_texture():
    """Casque : une coque blanche, un liseré bleu, un écusson orange."""
    img = gm.plain(416, "#EEF0F2", "#F8F9FA", "#E2E5E8")
    for x in range(SIZE):
        px(img, x, 4, "#3F76E4")
    px(img, 11, 9, "#E8872A")
    px(img, 12, 9, "#E8872A")
    return img


def visor_texture():
    """Visière : un or fumé, avec son reflet."""
    img = canvas("#5A4310")
    for y in range(SIZE):
        for x in range(SIZE):
            img[y][x] = gm.blend(rgb("#D9A93A"), rgb("#3A2A08"), y / 15.0)
    for k in range(4, 10):
        px(img, k, 12 - k, "#FFE9A6")
    return img


def abort_texture():
    """Poignée d'avortement : un rouge vif, ses arêtes plus claires."""
    img = canvas("#C8322B")
    for k in range(SIZE):
        px(img, k, 0, "#F06A52")
    return img


def hazard_texture():
    """Garde rayée : des bandes jaunes et noires en biais."""
    img = canvas("#25282D")
    for y in range(SIZE):
        for x in range(SIZE):
            if ((x + y) // 3) % 2 == 0:
                px(img, x, y, "#E8C02A")
    return img


def keycap_texture():
    """Touche de fonction : un capuchon sombre, sa légende d'un trait bleu clair rétroéclairé."""
    img = canvas("#25292E")
    for x in range(SIZE):
        px(img, x, 0, "#3A4048")
        px(img, x, 15, "#15171A")
    for x in range(4, 12):
        px(img, x, 8, "#7FD0FF")
    return img


def write_textures():
    gm.write_texture("pod_panel", [panel_texture()])
    gm.write_texture("pod_bezel", [bezel_texture()])
    gm.write_texture("pod_dial", [dial_texture()])
    gm.write_texture("pod_globe", [globe_face_texture()])
    gm.write_texture("pod_switches", [switches_texture()])
    gm.write_texture("pod_breakers", [breakers_texture()])
    gm.write_texture("pod_overhead", [overhead_texture()])
    gm.write_texture("pod_annunciator", [annunciator_texture()])
    gm.write_texture("pod_shell", [shell_texture()])
    gm.write_texture("pod_helmet", [helmet_texture()])
    gm.write_texture("pod_visor", [visor_texture()])
    gm.write_texture("pod_abort", [abort_texture()])
    gm.write_texture("pod_hazard", [hazard_texture()])
    gm.write_texture("pod_keycap", [keycap_texture()])
    gm.write_texture("pod_hull", [hull_texture()])
    gm.write_texture("pod_hull_low", [hull_texture(belt=True)])
    gm.write_texture("pod_shield", [shield_texture()])
    gm.write_texture("pod_glass", [glass_texture()])
    gm.write_texture("pod_accent", [accent_texture()])
    gm.write_texture("pod_dock", [dock_texture()])
    gm.write_texture("pod_hatch", [hatch_texture()])
    gm.write_texture("pod_liner", [liner_texture()])
    gm.write_texture("pod_floor", [floor_texture()])
    gm.write_texture("pod_seat", [seat_texture()])
    gm.write_texture("pod_trim", [rocket_skin(85, ["#2A2D31", "#24272B", "#33363B"], [5, 3, 2], [0, 15])])


# ---------------------------------------------------------------------------
# Modèle
# ---------------------------------------------------------------------------
def box(x0, y0, z0, x1, y1, z1, texture, **faces):
    return {"from": [x0, y0, z0], "to": [x1, y1, z1], "texture": texture, "faces": dict(faces)}


def lined(part, *inner):
    """Cloison : sa texture dehors, le capitonnage sur ses faces tournées vers l'intérieur."""
    return {**part, "faces": {face: "liner" for face in inner}}


def whole(part):
    """Pièce dont chaque face reçoit la texture entière (une vitre, un écran, la porte)."""
    return {**part, "stretch": True}


def ring(span, y0, y1, texture):
    """Quatre cloisons d'un pixel, fermant un gradin."""
    lo, hi = span
    return [
        lined(box(lo, y0, lo, hi, y1, lo + 1, texture), "south"), lined(box(lo, y0, hi - 1, hi, y1, hi, texture), "north"),
        lined(box(lo, y0, lo + 1, lo + 1, y1, hi - 1, texture), "east"), lined(box(hi - 1, y0, lo + 1, hi, y1, hi - 1, texture), "west"),
    ]


def ledge(outer, inner, y0, y1):
    """Épaulement : une couronne à plat entre deux gradins, capitonnée dessous."""
    lo, hi = outer
    a, b = inner
    return [
        lined(box(lo, y0, lo, hi, y1, a, "hull"), "down"), lined(box(lo, y0, b, hi, y1, hi, "hull"), "down"),
        lined(box(lo, y0, a, a, y1, b, "hull"), "down"), lined(box(b, y0, a, hi, y1, b, "hull"), "down"),
    ]


# Le cône, au-dessus de la section basse : une marche d'un pixel tous les deux pixels, jusqu'au toit.
CONE = (((-5, 21), DECK, 22), (MID, 22, 24), ((-3, 19), 24, 26), ((-2, 18), 26, 28), (TOP, 28, ROOF))


def cone():
    parts = []
    outer = LOW
    for span, y0, y1 in CONE:
        parts += ledge(outer, span, y0, y0 + 0.5) + ring(span, y0 + 0.5, y1, "hull")
        outer = span
    return parts


def seat(cx, cz):
    """
    Un siège baquet, tourné vers le nord : son socle, l'assise et le dossier capitonnés dans une coque de composite à
    joues enveloppantes, l'appui-tête et ses oreilles, le harnais (deux bretelles, la ceinture) et le repose-pieds.
    """
    return [
        cabin(box(cx - 2, FLOOR, cz - 2, cx + 2, 3, cz + 2, "liner")),
        cabin(box(cx - 2.75, 3, cz - 3, cx + 2.75, 4.2, cz + 2.5, "seat")),
        cabin(box(cx - 3.25, 3, cz - 2.5, cx - 2.75, 5.0, cz + 2.5, "shell")), cabin(box(cx + 2.75, 3, cz - 2.5, cx + 3.25, 5.0, cz + 2.5, "shell")),
        cabin(box(cx - 2.75, 4.2, cz + 2.5, cx + 2.75, 13.5, cz + 3.5, "seat", south="shell")),
        cabin(box(cx - 3.25, 5.0, cz + 1.5, cx - 2.75, 13.0, cz + 3.5, "shell")), cabin(box(cx + 2.75, 5.0, cz + 1.5, cx + 3.25, 13.0, cz + 3.5, "shell")),
        cabin(box(cx - 1.75, 13.5, cz + 2.6, cx + 1.75, 16.5, cz + 3.5, "seat", south="shell")),
        cabin(box(cx - 2.25, 13.5, cz + 1.8, cx - 1.75, 16.0, cz + 3.5, "shell")), cabin(box(cx + 1.75, 13.5, cz + 1.8, cx + 2.25, 16.0, cz + 3.5, "shell")),
        cabin(box(cx - 1.9, 6, cz + 2.3, cx - 1.2, 13, cz + 2.5, "strap")), cabin(box(cx + 1.2, 6, cz + 2.3, cx + 1.9, 13, cz + 2.5, "strap")),
        cabin(box(cx - 2.5, 4.2, cz - 0.2, cx + 2.5, 4.5, cz + 0.4, "strap")),
        cabin(box(cx - 2, FLOOR, cz - 6, cx + 2, 3.2, cz - 4.5, "shell")),
    ]


def cabin(part):
    """Pièce de la cabine : éclairée par les plafonniers de la capsule, et non par le jour du dehors."""
    return {**part, "cabin": True}


LO, HI = LOW
CAPSULE = [
    # Bouclier thermique, en deux gradins, et plancher.
    box(-4, 0, -4, 20, 0.6, 20, "shield"),
    box(LO, 0.6, LO, HI, 1.5, HI, "shield"),
    box(LO + 1, 1.5, LO + 1, HI - 1, FLOOR, HI - 1, "floor"),
    # Section basse. Devant (nord) : une allège, le pare-brise entre deux montants, un linteau.
    lined(box(LO, 1.5, LO, HI, 15.5, LO + 1, "hull_low"), "south"),
    lined(box(LO, 15.5, LO, 2, 19.5, LO + 1, "hull"), "south", "east"), lined(box(14, 15.5, LO, HI, 19.5, LO + 1, "hull"), "south", "west"),
    lined(box(LO, 19.5, LO, HI, DECK, LO + 1, "hull"), "south", "down"),
    whole(box(2, 15.5, LO + 0.25, 14, 19.5, LO + 0.75, "glass")),
    # Derrière (sud) : une cloison pleine, qui porte la porte d'accès.
    lined(box(LO, 1.5, HI - 1, HI, 16, HI, "hull_low"), "north"), lined(box(LO, 16, HI - 1, HI, DECK, HI, "hull"), "north"),
    whole(box(3, 3.5, HI, 13, 15.5, HI + 0.3, "hatch")),
    # Flancs : une allège, une grande baie entre deux montants, un linteau.
    lined(box(LO, 1.5, LO + 1, LO + 1, 11, HI - 1, "hull_low"), "east"), lined(box(HI - 1, 1.5, LO + 1, HI, 11, HI - 1, "hull_low"), "west"),
    lined(box(LO, 11, LO + 1, LO + 1, 16, 2, "hull"), "east", "south"), lined(box(LO, 11, 14, LO + 1, 16, HI - 1, "hull"), "east", "north"),
    lined(box(HI - 1, 11, LO + 1, HI, 16, 2, "hull"), "west", "south"), lined(box(HI - 1, 11, 14, HI, 16, HI - 1, "hull"), "west", "north"),
    lined(box(LO, 16, LO + 1, LO + 1, 18, 2, "hull"), "east", "south"), lined(box(LO, 16, 14, LO + 1, 18, HI - 1, "hull"), "east", "north"),
    lined(box(HI - 1, 16, LO + 1, HI, 18, 2, "hull"), "west", "south"), lined(box(HI - 1, 16, 14, HI, 18, HI - 1, "hull"), "west", "north"),
    lined(box(LO, 18, LO + 1, LO + 1, DECK, HI - 1, "hull"), "east", "down"), lined(box(HI - 1, 18, LO + 1, HI, DECK, HI - 1, "hull"), "west", "down"),
    whole(box(LO + 0.25, 11, 2, LO + 0.75, 18, 14, "glass")), whole(box(HI - 0.75, 11, 2, HI - 0.25, 18, 14, "glass")),
    # Liseré bleu, tout autour de la section basse, un rien en saillie.
    box(LO - 0.2, 9, LO - 0.2, HI + 0.2, 10, LO, "accent"), box(LO - 0.2, 9, HI, 3, 10, HI + 0.2, "accent"),
    box(13, 9, HI, HI + 0.2, 10, HI + 0.2, "accent"),
    box(LO - 0.2, 9, LO, LO, 10, HI, "accent"), box(HI, 9, LO, HI + 0.2, 10, HI, "accent"),
] + cone() + [
    # Toit, collier d'amarrage et écoutille.
    lined(box(TOP[0], ROOF, TOP[0], TOP[1], ROOF + 1, TOP[1], "hull"), "down"),
    box(3, ROOF + 1, 3, 13, ROOF + 2.2, 13, "dock"),
    box(5, ROOF + 2.2, 5, 11, ROOF + 3, 11, "trim"),
    # Les propulseurs d'attitude : une fente sombre de deux pixels, à fleur de coque, sur chaque face du cône.
    box(7, 23, MID[0] - 0.05, 9, 24, MID[0], "trim"), box(7, 23, MID[1], 9, 24, MID[1] + 0.05, "trim"),
    box(MID[0] - 0.05, 23, 7, MID[0], 24, 9, "trim"), box(MID[1], 23, 7, MID[1] + 0.05, 24, 9, "trim"),
    # Éclairage d'ambiance : un filet lumineux sous le plafond de la section basse (sa couleur suit le vol).
    cabin(box(LO + 1.2, 19, HI - 1.5, HI - 1.2, 19.3, HI - 1.2, "led")), cabin(box(LO + 1.2, 19, LO + 1.5, LO + 1.5, 19.3, HI - 1.5, "led")),
    cabin(box(HI - 1.5, 19, LO + 1.5, HI - 1.2, 19.3, HI - 1.5, "led")),
    # La planche de bord, sur toute la largeur de la cloison avant, sous le pare-brise.
    cabin(box(-4.5, 5, LO + 1, 20.5, 15.3, -4.4, "panel")),
    # Au centre, l'écran de bord dans sa lunette (le rendu y dessine l'image vivante).
    cabin(box(0.1, 5.4, -4.4, 15.9, 15, -4.1, "bezel")),
    # Les touches de fonction de l'écran, sous lui : cinq capuchons rétroéclairés.
] + [cabin(whole(box(2.2 + 2.5 * k, 5.6, -4.1, 3.8 + 2.5 * k, 6.2, -3.95, "keycap"))) for k in range(5)] + [
    cabin({**whole(box(1, 6.5, -4.1, 15, 14.5, -3.95, "screen")), "only": ("south",)}),
    # À gauche, le globe de navigation, et sous lui le cadran d'altitude.
    cabin(box(-3.9, 10.2, -4.4, -0.1, 14.2, -3.8, "bezel")),
    cabin({**whole(box(-3.5, 10.6, -3.8, -0.5, 13.8, -3.7, "globe")), "only": ("south",)}),
    cabin(box(-3.7, 6.2, -4.4, -0.3, 9.6, -3.9, "bezel")),
    cabin({**whole(box(-3.4, 6.5, -3.9, -0.6, 9.3, -3.8, "dial")), "only": ("south",)}),
    # À droite, le cadran de vitesse et celui de l'accélération.
    cabin(box(16.3, 10.4, -4.4, 19.7, 13.8, -3.9, "bezel")),
    cabin({**whole(box(16.6, 10.7, -3.9, 19.4, 13.5, -3.8, "dial")), "only": ("south",)}),
    cabin(box(16.3, 6.2, -4.4, 19.7, 9.6, -3.9, "bezel")),
    cabin({**whole(box(16.6, 6.5, -3.9, 19.4, 9.3, -3.8, "dial")), "only": ("south",)}),
    # Le pare-soleil au-dessus de la planche : il porte le bandeau d'alarmes et les deux voyants d'alarme générale.
    cabin(box(-4.5, 15.3, LO + 1, 20.5, 15.9, -3.6, "panel", south="annunciator", up="bezel")),
    # Le pupitre : des rangées d'interrupteurs, des disjoncteurs sur sa face, la poignée d'avortement sous sa garde.
    cabin(box(-1.5, FLOOR, LO + 1, 17.5, 5, -1.2, "panel", up="switches", south="breakers")),
    cabin(box(6.3, 5, -3.9, 9.7, 5.3, -2.0, "hazard")),
    cabin(box(6.3, 5.3, -3.9, 6.6, 6.4, -2.0, "hazard")), cabin(box(9.4, 5.3, -3.9, 9.7, 6.4, -2.0, "hazard")),
    cabin(box(7.6, 5.3, -3.4, 8.4, 6.1, -3.0, "abort")), cabin(box(6.8, 6.1, -3.5, 9.2, 6.6, -2.9, "abort")),
    # Sous les baies : des disjoncteurs à gauche, l'écran des systèmes à droite.
    cabin(box(LO + 1, 4, 2, LO + 1.6, 10.5, 12, "panel", east="breakers")),
    cabin(box(HI - 1.6, 4, 2, HI - 1, 10.5, 12, "panel")),
    cabin({**whole(box(HI - 1.8, 5, 3, HI - 1.6, 9.8, 11, "side")), "only": ("west",)}),
    # Les commandes : le levier de translation à gauche du pilote, le pied du manche à sa droite (le manche bouge).
    cabin(box(3.4, FLOOR, 7.2, 4.6, 5.0, 8.4, "liner")), cabin(box(3.7, 5.0, 7.4, 4.3, 6.2, 8.2, "trim")),
    cabin(box(3.2, 6.2, 7.2, 4.8, 6.9, 8.4, "grip")),
    cabin(box(11.4, FLOOR, 7.2, 12.6, 5.5, 8.4, "liner")),
    # Le panneau de plafond, tout en haut du cône : hors de portée des têtes.
    cabin(box(1, ROOF - 0.8, 0, 15, ROOF, 6, "panel", down="overhead")),
    # Deux casques accrochés aux flancs, derrière les baies, près des équipiers.
    cabin(box(LO + 1.1, 10.5, 15, LO + 3.1, 12.6, 17, "helmet")), cabin({**whole(box(LO + 3.1, 10.9, 15.3, LO + 3.2, 12.2, 16.7, "visor")), "only": ("east",)}),
    cabin(box(HI - 3.1, 10.5, 15, HI - 1.1, 12.6, 17, "helmet")), cabin({**whole(box(HI - 3.2, 10.9, 15.3, HI - 3.1, 12.2, 16.7, "visor")), "only": ("west",)}),
    # L'échelle du tunnel d'amarrage, contre la cloison arrière de la section du milieu.
    cabin(box(6, 21.5, MID[1] - 1.6, 10, 22.0, MID[1] - 1, "trim")), cabin(box(6, 23.5, MID[1] - 1.6, 10, 24.0, MID[1] - 1, "trim")),
] + [part for cx, cz in SEATS for part in seat(cx, cz)]

TEXTURES = {
    "hull": "pod_hull", "hull_low": "pod_hull_low", "shield": "pod_shield", "glass": "pod_glass", "accent": "pod_accent",
    "dock": "pod_dock", "hatch": "pod_hatch", "liner": "pod_liner", "floor": "pod_floor", "seat": "pod_seat",
    "trim": "pod_trim", "grip": "capsule_grip", "strap": "capsule_strap", "led": "capsule_led", "screen": "capsule_screen",
    "touch": "capsule_touch", "side": "capsule_side",
    "panel": "pod_panel", "bezel": "pod_bezel", "dial": "pod_dial", "globe": "pod_globe", "switches": "pod_switches",
    "breakers": "pod_breakers", "overhead": "pod_overhead", "annunciator": "pod_annunciator", "shell": "pod_shell",
    "helmet": "pod_helmet", "visor": "pod_visor", "abort": "pod_abort", "hazard": "pod_hazard", "keycap": "pod_keycap",
}
EMISSIVE = {"screen", "touch", "led", "side"}

# Le manche, à part : le rendu le fait pencher autour de son pied, au rythme des commandes du pilote.
STICK = [
    box(11.7, 5.5, 7.5, 12.3, 7.6, 8.1, "trim"), box(11.5, 7.6, 7.3, 12.5, 8.5, 8.3, "grip"),
    box(11.8, 8.5, 7.5, 12.2, 8.75, 7.9, "abort"),
]


def faces_of(part, inside):
    """
    Les faces d'une pièce qui reviennent à la cabine (inside vrai) ou à la coque : une cloison donne à la cabine ses
    faces capitonnées, tournées vers l'intérieur, et garde les autres ; une pièce de la cabine lui revient en entier.
    """
    all_faces = part.get("only", ("north", "south", "west", "east", "up", "down"))
    if part.get("cabin"):
        return all_faces if inside else ()
    lined_faces = tuple(f for f, t in part.get("faces", {}).items() if t == "liner")
    return tuple(f for f in all_faces if (f in lined_faces) == inside)


def split(parts, inside):
    out = []
    for part in parts:
        faces = faces_of(part, inside)
        if faces:
            clean = {k: v for k, v in part.items() if k != "cabin"}
            out.append({**clean, "only": faces})
    return out


def write_model(name, parts):
    model = build_tiled(parts, TEXTURES, EMISSIVE, particle="orbitalis:block/pod_hull")
    # Le modèle déborde largement de son bloc : l'ombrage de contact l'assombrirait à tort.
    model["ambientocclusion"] = False
    with open(os.path.join(gm.MODEL_DIR, name + ".json"), "w", newline="\n") as f:
        json.dump(model, f, indent=1)


if __name__ == "__main__":
    write_textures()
    for part in CAPSULE:
        assert all(-16 <= c <= 32 for c in part["from"] + part["to"]), part
        assert all(a < b for a, b in zip(part["from"], part["to"])), part
    overlaps = check_overlaps("capsule", [{k: v for k, v in part.items() if k != "cabin"} for part in CAPSULE])
    clean = [{k: v for k, v in part.items() if k != "cabin"} for part in CAPSULE]
    # Le modèle entier (le tronçon de la fusée), puis la coque et la cabine à part (la capsule en vol libre, dont
    # la cabine a son propre éclairage), et le manche.
    write_model("capsule", clean)
    write_model("capsule_shell", split(CAPSULE, False))
    write_model("capsule_cabin", split(CAPSULE, True))
    write_model("capsule_stick", STICK)
    print("Capsule ecrite : %d pieces, %d chevauchement(s)." % (len(CAPSULE), overlaps))
