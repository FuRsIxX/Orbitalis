#!/usr/bin/env python3
"""
Écrit les blocs du vaisseau interstellaire d'Orbitalis : leurs modèles 3D et leurs textures.

- le cœur de distorsion, deux blocs de haut : une colonne de plasma bleu-violet, cerclée d'anneaux de cuivre, entre
  quatre montants ; éteint (vide), il n'est qu'une colonne sombre ;
- le caisson d'hibernation, deux blocs de haut : une capsule dressée, son hublot givré, son petit écran de veille ;
- le moteur de l'anneau, au plafond : un carter de coque et, dessous, sa couronne dentée qui tourne ;
- le poste de pilotage : un pupitre large, son clavier, ses deux manettes et son écran de navigation.

Les modèles sont dessinés face avant au nord : le jeu les fait tourner. Aucune face n'en recouvre une autre.
Il réutilise les outils de dessin et les textures de generate_station.py.

Usage : python tools/generate_ship.py
"""
import json
import math
import os

import generate_machine_models as gm
import generate_station as gs
from generate_machine_models import box
from generate_textures import SIZE, rgb
from model_kit import build_tiled, check_overlaps, display, separate

SHIP_DIR = os.path.join(gm.MODEL_DIR, "..", "ship")
TEXTURES = {**gs.TEXTURES, "copper": "machine_copper", "warp": "warp_plasma_off", "warp_on": "warp_plasma_on",
            "pod_glass": "hibernation_glass", "pod_screen": "hibernation_screen", "gear": "ring_drive_gear",
            "helm_screen": "helm_screen"}
EMISSIVE = gs.EMISSIVE | {"warp_on", "pod_screen", "helm_screen"}


def mix(a, b, t):
    """Couleur entre a et b, en proportion t (0 : a, 1 : b)."""
    t = max(0.0, min(1.0, t))
    ca, cb = rgb(a), rgb(b)
    return tuple(int(ca[i] + (cb[i] - ca[i]) * t) for i in range(3)) + (255,)


# ---------------------------------------------------------------------------
# Textures
# ---------------------------------------------------------------------------
def warp_frame(step, frames=8):
    """Le plasma du cœur : des vagues bleu-violet qui montent, plus claires au milieu de la colonne."""
    img = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            core = 1.0 - abs(x - 7.5) / 8.0
            wave = 0.5 + 0.5 * math.sin((y + step * SIZE / frames) / SIZE * 4.0 * math.pi + x * 0.25)
            t = 0.35 * core + 0.45 * wave * core + 0.2 * core * core
            row.append(mix("#2A0F6E", "#B98CFF", t) if t < 0.75 else mix("#B98CFF", "#F2E9FF", (t - 0.75) * 4.0))
        img.append(row)
    return img


def warp_off():
    """Le cœur vide : une colonne de verre sombre, à peine violette, striée de reflets."""
    img = gs.canvas("#160F28")
    for y in range(SIZE):
        for x in (3, 4):
            gs.dot(img, x, y, "#251A42")
        if y % 5 == 1:
            for x in range(6, 11):
                gs.dot(img, x, y, "#1E1536")
    return img


def pod_glass():
    """Le hublot du caisson, givré : un verre bleu pâle, des cristaux de givre dans les coins, un reflet en biais."""
    img = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            edge = min(x, y, SIZE - 1 - x, SIZE - 1 - y)
            row.append(mix("#E9F6FB", "#7FB3C9", min(1.0, edge / 4.0)))
        img.append(row)
    for x, y in ((0, 0), (1, 0), (0, 1), (2, 1), (1, 2), (15, 15), (14, 15), (15, 14), (13, 14), (14, 13), (15, 0), (14, 1), (0, 15), (1, 14)):
        gs.dot(img, x, y, "#FFFFFF")
    for k in range(4, 12):
        gs.dot(img, k, 15 - k, "#B9E2F0")
    return img


def pod_screen(step, frames=8):
    """L'écran de veille du caisson : un battement de cœur lent qui défile, et un voyant bleu."""
    img = gs.canvas("#04121C")
    for x in range(SIZE):
        phase = (x + step * 2) % SIZE
        y = 9 - (4 if phase == 6 else 2 if phase in (5, 7) else 0)
        gs.dot(img, x, y, "#5FD3F5")
    gs.dot(img, 14, 2, "#7FC4FF" if step % 4 < 2 else "#1E4E7A")
    return img


def gear_frame(step, frames=8):
    """La couronne dentée du moteur de l'anneau, vue de dessous : ses dents de cuivre tournent autour d'un moyeu sombre."""
    img = gs.canvas("#2B3038")
    for y in range(SIZE):
        for x in range(SIZE):
            r = math.hypot(x - 7.5, y - 7.5)
            angle = math.atan2(y - 7.5, x - 7.5) + step / frames * 2.0 * math.pi / 6.0
            if 5.5 <= r <= 7.6 and math.sin(angle * 12.0) > 0.0:
                gs.dot(img, x, y, "#C46A35")
            elif 4.2 <= r < 5.5:
                gs.dot(img, x, y, "#8A4521")
            elif r < 2.2:
                gs.dot(img, x, y, "#9AA3AE")
            elif r < 4.2:
                gs.dot(img, x, y, "#15171A")
    return img


def helm_frame(step, frames=8):
    """L'écran de navigation : un champ d'étoiles, la route du vaisseau en pointillés, et l'étoile visée qui clignote."""
    img = gs.canvas("#030914")
    for x, y in ((1, 2), (4, 12), (7, 5), (12, 13), (14, 7), (2, 9), (9, 1), (11, 9)):
        gs.dot(img, x, y, "#3A5A7A")
    for k in range(0, 12):
        x = 2 + k
        y = 12 - round(k * 0.7)
        if (k + step) % 3 != 0:
            gs.dot(img, x, y, "#5BE08A")
    target = "#FFD84A" if step % 4 < 2 else "#8A6A12"
    for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
        gs.dot(img, 13 + dx, 4 + dy, target)
    gs.dot(img, 2, 12, "#7FC4FF")
    return img


def write_textures():
    gm.write_texture("warp_plasma_on", [warp_frame(k) for k in range(8)], 2)
    gm.write_texture("warp_plasma_off", [warp_off()])
    gm.write_texture("hibernation_glass", [pod_glass()])
    gm.write_texture("hibernation_screen", [pod_screen(k) for k in range(8)], 3, interpolate=False)
    gm.write_texture("ring_drive_gear", [gear_frame(k) for k in range(8)], 2)
    gm.write_texture("helm_screen", [helm_frame(k) for k in range(8)], 4, interpolate=False)


# ---------------------------------------------------------------------------
# Modèles
# ---------------------------------------------------------------------------
def posts(y0, y1):
    return [box(x0, y0, z0, x0 + 2, y1, z0 + 2, "frame") for x0 in (2, 12) for z0 in (2, 12)]


def warp_lower(lit):
    plasma = "warp_on" if lit else "warp"
    return [
        box(0, 0, 0, 16, 2, 16, "dark"),
        box(2, 2, 2, 14, 4, 14, "frame"),
        box(5, 4, 5, 11, 16, 11, plasma),
        box(4, 8, 4, 12, 9.5, 12, "copper"), box(4, 13, 4, 12, 14.5, 12, "copper"),
        box(6, 2.5, 1.6, 10, 3.5, 2, "led"),
    ] + posts(4, 16)


def warp_upper(lit):
    plasma = "warp_on" if lit else "warp"
    return [
        box(5, 0, 5, 11, 12, 11, plasma),
        box(4, 3, 4, 12, 4.5, 12, "copper"), box(4, 8, 4, 12, 9.5, 12, "copper"),
        box(2, 12, 2, 14, 14, 14, "dark"), box(4, 14, 4, 12, 16, 12, "frame"),
    ] + posts(0, 12)


def pod_lower():
    return [
        box(1, 0, 1, 15, 2, 15, "dark"),
        box(1, 2, 12, 15, 16, 15, "hull"),
        box(1, 2, 1, 3, 16, 12, "hull"), box(13, 2, 1, 15, 16, 12, "hull"),
        box(3, 2, 1, 13, 3, 12, "frame"),
        gs.front_only(gs.whole(box(3, 3, 1.5, 13, 16, 2, "pod_glass"))),
    ]


def pod_upper():
    return [
        box(1, 0, 12, 15, 12, 15, "hull"),
        box(1, 0, 1, 3, 12, 12, "hull"), box(13, 0, 1, 15, 12, 12, "hull"),
        gs.front_only(gs.whole(box(3, 0, 1.5, 13, 11, 2, "pod_glass"))),
        box(1, 12, 1, 15, 16, 15, "hull"),
        gs.front_only(gs.whole(box(5, 12.5, 0.6, 11, 15, 1, "pod_screen"))),
    ]


RING_DRIVE = [
    box(0, 4, 0, 16, 16, 16, "hull"),
    {**box(2, 0, 2, 14, 4, 14, "dark"), "faces": {"down": "gear"}},
    box(1.5, 3, 1.5, 14.5, 4, 14.5, "copper"),
]

# L'objet du moteur de l'anneau : retourné, sa couronne dentée sur le dessus, pour qu'on la voie dans l'inventaire.
RING_DRIVE_ITEM = [
    box(0, 0, 0, 16, 12, 16, "hull"),
    {**box(2, 12, 2, 14, 16, 14, "dark"), "faces": {"up": "gear"}},
    box(1.5, 12, 1.5, 14.5, 13, 14.5, "copper"),
]

HELM = [
    box(1, 0, 3, 15, 1, 15, "dark"),
    box(3, 1, 6, 13, 7, 13, "console"),
    {**box(0, 7, 1, 16, 9, 10, "console"), "faces": {"up": "keys"}},
    box(0, 7, 10, 16, 16, 12, "dark"),
    gs.front_only(gs.whole(box(1, 9.5, 9.7, 15, 15.5, 10, "helm_screen"))),
    box(12, 9, 4, 13, 11.5, 5, "copper"), box(10.5, 9, 4, 11.5, 10.5, 5, "copper"),
    box(1.5, 7.6, 0.6, 3, 8.4, 1, "led"),
]


def stacked(lower, upper):
    """Les deux moitiés d'un meuble, l'une sur l'autre : pour l'objet en main."""
    return lower + [{**part, "from": [part["from"][0], part["from"][1] + 16, part["from"][2]],
                     "to": [part["to"][0], part["to"][1] + 16, part["to"][2]]} for part in upper]


def models():
    out = {
        "warp_core_lower": warp_lower(False), "warp_core_lower_on": warp_lower(True),
        "warp_core_upper": warp_upper(False), "warp_core_upper_on": warp_upper(True),
        "warp_core_item": stacked(warp_lower(True), warp_upper(True)),
        "hibernation_pod_lower": pod_lower(), "hibernation_pod_upper": pod_upper(),
        "hibernation_pod_item": stacked(pod_lower(), pod_upper()),
        "ring_drive": RING_DRIVE, "ring_drive_item": RING_DRIVE_ITEM, "ship_helm": HELM,
    }
    return out


def main():
    write_textures()
    os.makedirs(SHIP_DIR, exist_ok=True)
    total = 0
    for name, parts in models().items():
        parts = separate(parts)
        total += check_overlaps(name, parts)
        model = build_tiled(parts, TEXTURES, EMISSIVE, particle="orbitalis:block/station_hull")
        if name.endswith("_item"):
            model["display"] = display([0, 0, 0], [16, 32, 16], fill=1.1)
        with open(os.path.join(SHIP_DIR, name + ".json"), "w", newline="\n") as f:
            json.dump(model, f, indent=1)
    print("Vaisseau : textures et modèles écrits, %d chevauchement(s)." % total)


if __name__ == "__main__":
    main()
