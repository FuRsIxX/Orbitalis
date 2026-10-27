#!/usr/bin/env python3
"""
Génère les deux autres capsules habitées d'Orbitalis, sur la base de la Mk1 « Éclaireur » (generate_capsule.py) :

- la Mk2 « Horizon », inspirée de Crew Dragon : coque blanche, chapeau et bande noirs, cabine gris anthracite à
  liserés bleus, un poste de pilotage tout en écrans (trois grands écrans d'un seul tenant, un pupitre tactile, pas
  de manche) ;
- la Mk3 « Odyssée », inspirée d'Orion : coque blanche et gris acier, bande orange, cabine blanche à accents orange,
  trois écrans, un grand panneau de plafond, des interrupteurs sous garde, le manche et le levier de translation.

Les deux ont quatre places : le pilote devant, au centre (la cabine tourne autour de sa tête), et trois équipiers
derrière lui : deux sur les côtés, le troisième au milieu, sur une marche, pour voir par-dessus le pilote. La coque
reprend la forme en gradins de la Mk1 ; ses hublots sont aux mêmes places.

Chaque capsule est écrite en trois modèles, comme la Mk1 : entière (le tronçon de la fusée), et en deux, la coque
et la cabine (la capsule en vol libre, dont la cabine a son propre éclairage).

Usage : python tools/generate_capsule_variants.py   (après generate_capsule.py)
"""
import json
import math
import os
import random

import generate_capsule as gc
import generate_machine_models as gm
from generate_capsule import DECK, FLOOR, LOW, MID, box, cabin, whole
from generate_textures import ITEM_DIR, SIZE, TRANSPARENT, rgb, write_png
from model_kit import build_tiled, check_overlaps

LO, HI = LOW


# ---------------------------------------------------------------------------
# Textures
# ---------------------------------------------------------------------------
def tone(color, factor):
    return gc.tone(color, factor)


def padded(seed, base, seam):
    """Un capitonnage à deux coussins par bloc, au relief doux (comme celui de la Mk1), dans une autre teinte."""
    img = gc.grained(seed, base, 0.012)
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
        img[y][0] = tone(seam, 1.0) if seam else tone(img[y][0], 0.88)
    return img


def band(base, edge):
    """Bande de la coque : une teinte pleine entre deux filets d'aluminium."""
    img = gc.grained(31, base, 0.02)
    for x in range(SIZE):
        img[0][x] = rgb(edge)
        img[SIZE - 1][x] = rgb(edge)
    return img


def seat(seed, base, piping):
    """Sellerie matelassée en bandes, bordée d'un passepoil."""
    img = gc.grained(seed, base, 0.02)
    for y in range(SIZE):
        local = y % 4
        for x in range(1, SIZE - 1):
            img[y][x] = tone(img[y][x], (1.10, 1.04, 0.98, 0.78)[local])
        img[y][0] = tone(piping, 0.92 if y % 2 else 1.0)
        img[y][SIZE - 1] = tone(piping, 0.85)
    return img


def shell(seed, base):
    img = gc.grained(seed, base, 0.01)
    for y in range(SIZE):
        for x in range(SIZE):
            img[y][x] = tone(img[y][x], 1.04 - 0.08 * y / 15.0)
    for k in range(SIZE):
        img[k][0] = tone(img[k][0], 0.8)
        img[k][SIZE - 1] = tone(img[k][SIZE - 1], 0.86)
    return img


def panel(seed, base):
    """Planche : un composite mat, chanfreiné, à vis noyées."""
    img = gc.grained(seed, base, 0.02)
    for k in range(SIZE):
        img[0][k] = tone(img[0][k], 1.18)
        img[SIZE - 1][k] = tone(img[SIZE - 1][k], 0.75)
        img[k][0] = tone(img[k][0], 1.1)
        img[k][SIZE - 1] = tone(img[k][SIZE - 1], 0.82)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        gc.px(img, x, y, tone(base, 0.55))
    return img


def glass_screen():
    """Grand écran éteint : un verre noir bleuté, son reflet en biais (le rendu y dessine l'image vivante)."""
    img = gc.canvas("#060A0F")
    for k in range(3, 9):
        gc.px(img, k + 2, k, "#0E1822")
    return img


def touch_desk():
    """Pupitre tactile : une dalle de verre noir, ses pavés lumineux bleus et blancs."""
    img = gc.canvas("#0A0E13")
    for y0 in (2, 9):
        for x0 in range(1, 15, 5):
            for y in range(y0, y0 + 4):
                for x in range(x0, x0 + 4):
                    gc.px(img, x, y, "#122230")
            for x in range(x0, x0 + 4):
                gc.px(img, x, y0 + 2, "#5FB8F0" if (x0 + y0) % 2 else "#DCEBF5")
    return img


def nose_texture():
    """Chapeau de la Mk2 : un composite noir satiné, uni, à peine éclairé sur son arête."""
    img = gc.grained(77, "#1A1C20", 0.015)
    for x in range(SIZE):
        img[0][x] = rgb("#2C3036")
    return img


def write_textures():
    write = gm.write_texture
    write("mk2_liner", [padded(201, "#3A3F46", None)])
    write("mk2_band", [band("#16181C", "#9AA3AE")])
    write("mk2_seat", [seat(203, "#1B1D21", "#5FB8F0")])
    write("mk2_shell", [shell(204, "#E9ECEF")])
    write("mk2_panel", [panel(205, "#24282E")])
    write("mk2_nose", [nose_texture()])
    write("mk2_touch", [touch_desk()])
    write("mk3_liner", [padded(301, "#E4E5E2", None)])
    write("mk3_band", [band("#E8872A", "#C9CED4")])
    write("mk3_seat", [seat(303, "#3B4250", "#E8872A")])
    write("mk3_shell", [shell(304, "#B9C0C8")])
    write("mk3_panel", [panel(305, "#C9CED4")])
    write("glass_screen", [glass_screen()])


def icon(body, top, accent, window):
    """Objet : la capsule de profil, en gradins, sa bande et son hublot."""
    img = [[TRANSPARENT for _ in range(SIZE)] for _ in range(SIZE)]
    rows = [(2, 6, 9, top), (3, 5, 10, top), (4, 4, 11, top), (5, 4, 11, body), (6, 3, 12, body), (7, 3, 12, body),
            (8, 2, 13, body), (9, 2, 13, body), (10, 2, 13, accent), (11, 1, 14, body), (12, 1, 14, body), (13, 1, 14, "#2A2D31")]
    for y, x0, x1, color in rows:
        for x in range(x0, x1 + 1):
            img[y][x] = tone(color, 1.12 if x < (x0 + x1) / 2 - 1 else 0.92 if x > (x0 + x1) / 2 + 2 else 1.0)
        img[y][x0] = tone(color, 0.7)
        img[y][x1] = tone(color, 0.7)
    for x in (6, 7):
        img[7][x] = rgb(window)
        img[8][x] = tone(window, 0.7)
    for x in range(6, 10):
        img[1][x] = rgb("#5A626C")
    return img


# ---------------------------------------------------------------------------
# Modèles
# ---------------------------------------------------------------------------
# La coque de la Mk1, sans son aménagement : ses cloisons, ses hublots, ses gradins, son toit.
EXTERIOR = [part for part in gc.CAPSULE if not part.get("cabin")]
# Places : le pilote, deux équipiers sur les côtés, et le troisième au milieu, sur une marche.
SEATS = ((8, 9.5, 0), (1.5, 15, 0), (14.5, 15, 0), (8, 17, 3))


def seat_at(cx, cz, lift):
    """Un siège (celui de la Mk1), posé sur une marche de {@code lift} pixels ; sur la marche, pas de repose-pieds."""
    parts = []
    for part in gc.seat(cx, cz):
        if lift and part["to"][1] <= 3.21 and part["from"][2] < cz - 4:
            continue
        moved = {**part, "from": [part["from"][0], part["from"][1] + lift, part["from"][2]],
                 "to": [part["to"][0], part["to"][1] + lift, part["to"][2]]}
        parts.append(moved)
    return parts


def leds():
    return [
        cabin(box(LO + 1.2, 19, HI - 1.5, HI - 1.2, 19.3, HI - 1.2, "led")), cabin(box(LO + 1.2, 19, LO + 1.5, LO + 1.5, 19.3, HI - 1.5, "led")),
        cabin(box(HI - 1.5, 19, LO + 1.5, HI - 1.2, 19.3, HI - 1.5, "led")),
    ]


def glass_cockpit():
    """La planche à trois écrans d'un seul tenant (celui du milieu à la place de l'écran de la Mk1), et le pare-soleil."""
    return [
        cabin(box(-4.5, 5, LO + 1, 20.5, 15.3, -4.4, "panel")),
        cabin(box(-4.4, 5.6, -4.4, 20.4, 15, -4.1, "bezel")),
        cabin({**whole(box(1, 6.5, -4.1, 15, 14.5, -3.95, "screen")), "only": ("south",)}),
        cabin({**whole(box(-3.9, 6.5, -4.1, 0.5, 14.5, -3.95, "glass_screen")), "only": ("south",)}),
        cabin({**whole(box(15.5, 6.5, -4.1, 19.9, 14.5, -3.95, "glass_screen")), "only": ("south",)}),
        cabin(box(-4.5, 15.3, LO + 1, 20.5, 15.9, -3.6, "panel", south="annunciator", up="bezel")),
    ]


def helmets():
    return [
        cabin(box(LO + 1.1, 10.5, 15, LO + 3.1, 12.6, 17, "helmet")), cabin({**whole(box(LO + 3.1, 10.9, 15.3, LO + 3.2, 12.2, 16.7, "visor")), "only": ("east",)}),
        cabin(box(HI - 3.1, 10.5, 15, HI - 1.1, 12.6, 17, "helmet")), cabin({**whole(box(HI - 3.2, 10.9, 15.3, HI - 3.1, 12.2, 16.7, "visor")), "only": ("west",)}),
    ]


def mk2():
    """Horizon : un pupitre tactile sur toute la largeur, des consoles latérales lisses, pas de manche."""
    parts = [dict(p) for p in EXTERIOR]
    for part in parts:
        # Le haut de la coque (épaulements, toit) prend le noir du chapeau ; la bande devient noire.
        if part["texture"] == "hull" and part["from"][1] >= DECK:
            part["texture"] = "nose"
    parts += [
        # Le chapeau de nez, articulé, rabattu sur le collier d'amarrage.
        box(2.5, 30.05, 2.5, 13.5, 31.4, 13.5, "nose"),
    ]
    parts += leds() + glass_cockpit() + helmets()
    parts += [
        cabin(box(-1.5, FLOOR, LO + 1, 17.5, 5, -1.2, "panel", up="touch_desk")),
        cabin(box(LO + 1, 4, 2, LO + 1.6, 10.5, 12, "panel")),
        cabin(box(HI - 1.6, 4, 2, HI - 1, 10.5, 12, "panel")),
        cabin({**whole(box(HI - 1.8, 5, 3, HI - 1.6, 9.8, 11, "side")), "only": ("west",)}),
        cabin({**whole(box(LO + 1.6, 5, 3, LO + 1.8, 9.8, 11, "side")), "only": ("east",)}),
    ]
    for cx, cz, lift in SEATS:
        parts += seat_at(cx, cz, lift)
        if lift:
            parts.append(cabin(box(cx - 2.5, FLOOR, cz - 2.5, cx + 2.5, FLOOR + lift, cz + 3.5, "panel")))
    return parts


def mk3():
    """Odyssée : des interrupteurs sous garde, un grand panneau de plafond, le manche et le levier de translation."""
    parts = [dict(p) for p in EXTERIOR]
    parts += [
        # Un collier d'amarrage plus large, cerclé d'acier.
        box(2, 30.05, 2, 14, 30.6, 14, "trim"),
    ]
    parts += leds() + glass_cockpit() + helmets()
    parts += [
        cabin(box(-1.5, FLOOR, LO + 1, 17.5, 5, -1.2, "panel", up="switches", south="breakers")),
        cabin(box(6.3, 5, -3.9, 9.7, 5.3, -2.0, "hazard")),
        cabin(box(6.3, 5.3, -3.9, 6.6, 6.4, -2.0, "hazard")), cabin(box(9.4, 5.3, -3.9, 9.7, 6.4, -2.0, "hazard")),
        cabin(box(7.6, 5.3, -3.4, 8.4, 6.1, -3.0, "abort")), cabin(box(6.8, 6.1, -3.5, 9.2, 6.6, -2.9, "abort")),
        cabin(box(LO + 1, 4, 2, LO + 1.6, 10.5, 12, "panel", east="breakers")),
        cabin(box(HI - 1.6, 4, 2, HI - 1, 10.5, 12, "panel")),
        cabin({**whole(box(HI - 1.8, 5, 3, HI - 1.6, 9.8, 11, "side")), "only": ("west",)}),
        cabin(box(3.4, FLOOR, 7.2, 4.6, 5.0, 8.4, "liner")), cabin(box(3.7, 5.0, 7.4, 4.3, 6.2, 8.2, "trim")),
        cabin(box(3.2, 6.2, 7.2, 4.8, 6.9, 8.4, "grip")),
        cabin(box(11.4, FLOOR, 7.2, 12.6, 5.5, 8.4, "liner")),
        # Le grand panneau de plafond, tout en haut du cône, au-dessus du pilote : hors de portée des têtes.
        cabin(box(0, gc.ROOF - 0.8, 0, 16, gc.ROOF, 9, "panel", down="overhead")),
    ]
    for cx, cz, lift in SEATS:
        parts += seat_at(cx, cz, lift)
        if lift:
            parts.append(cabin(box(cx - 2.5, FLOOR, cz - 2.5, cx + 2.5, FLOOR + lift, cz + 3.5, "panel")))
    return parts


MK2_TEXTURES = {**gc.TEXTURES, "liner": "mk2_liner", "accent": "mk2_band", "seat": "mk2_seat", "shell": "mk2_shell",
                "panel": "mk2_panel", "nose": "mk2_nose", "touch_desk": "mk2_touch", "glass_screen": "glass_screen"}
MK3_TEXTURES = {**gc.TEXTURES, "liner": "mk3_liner", "accent": "mk3_band", "seat": "mk3_seat", "shell": "mk3_shell",
                "panel": "mk3_panel", "glass_screen": "glass_screen"}
EMISSIVE = set(gc.EMISSIVE) | {"touch_desk"}


def write(name, parts, textures):
    model = build_tiled(parts, textures, EMISSIVE, particle="orbitalis:block/pod_hull")
    model["ambientocclusion"] = False
    with open(os.path.join(gm.MODEL_DIR, name + ".json"), "w", newline="\n") as f:
        json.dump(model, f, indent=1)


def clean(parts):
    return [{k: v for k, v in part.items() if k != "cabin"} for part in parts]


if __name__ == "__main__":
    write_textures()
    write_png(os.path.join(ITEM_DIR, "crew_capsule_mk2.png"), icon("#EEF0F2", "#17191D", "#16181C", "#5FB8F0"))
    write_png(os.path.join(ITEM_DIR, "crew_capsule_mk3.png"), icon("#E2E5E8", "#B9C0C8", "#E8872A", "#9FD3F2"))
    total = 0
    for name, parts, textures in (("capsule_mk2", mk2(), MK2_TEXTURES), ("capsule_mk3", mk3(), MK3_TEXTURES)):
        for part in parts:
            assert all(a < b for a, b in zip(part["from"], part["to"])), (name, part)
        total += check_overlaps(name, clean(parts))
        write(name, clean(parts), textures)
        write(name + "_shell", gc.split(parts, False), textures)
        write(name + "_cabin", gc.split(parts, True), textures)
    print("Capsules Mk2 et Mk3 écrites, %d chevauchement(s)." % total)
