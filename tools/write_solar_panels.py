#!/usr/bin/env python3
"""
Les panneaux solaires d'Orbitalis, de leurs quatre générations : leurs modèles et leurs textures. C'est le seul script
qui les écrit.

Chaque panneau est une nappe de cellules posée sur un mât : une platine boulonnée, le mât, sa tête, le tube porteur et
deux rails, le boîtier de raccordement sous la nappe. La nappe occupe tout le bloc, pour se raccorder sans joint à ses
voisines ; un cadre la borde sur chaque côté libre (modèle « solar_edge », que le jeu tourne pour chaque côté) : un
profilé d'aluminium et son joint de vitrage noir. Le bout de chaque profilé dépend du voisin : un angle (« solar_corner »)
s'il n'y a pas de panneau de ce côté-là, sinon un raccord (« solar_end_cw », « solar_end_ccw ») qui va jusqu'au bord du
bloc, où il rejoint celui du voisin : une rangée de panneaux a un seul cadre continu, sans écart.

Les cellules sont centrées sur le bloc : quatre cellules par bloc, séparées par un joint d'un pixel sur chaque bord et
de deux au milieu. Deux blocs voisins forment donc un joint de deux pixels, comme au milieu d'un bloc : la nappe est
régulière d'un bout à l'autre, et un panneau seul, comme son icône, est symétrique.

Les générations :
- le panneau solaire : cellules monocristallines bleues aux coins coupés, cadre d'aluminium ;
- le panneau avancé : cellules noires à contacts arrière, sans aucun ruban, cadre graphite et liseré cyan ;
- le panneau à pérovskite : cellules irisées, du sarcelle au violet, contacts dorés, cadre noir filet d'or, une tête
  de suivi du soleil sur deux axes ;
- le panneau quantique : cellules sombres où passent des franges d'interférence cyan et magenta (animées), cadre de
  chrome à bande lumineuse, et deux anneaux lumineux sur le mât.

L'objet montre le panneau seul, encadré sur ses quatre côtés.

Usage : python tools/write_solar_panels.py
"""
import json
import math
import os

import generate_machine_models as gm
from generate_machine_models import box
from generate_textures import SIZE, rgb
from model_kit import check_overlaps, display, separate


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    ca = rgb(a) if isinstance(a, str) else a
    cb = rgb(b) if isinstance(b, str) else b
    return tuple(int(ca[i] + (cb[i] - ca[i]) * t) for i in range(3)) + (255,)


def fill(color):
    return [[rgb(color) for _ in range(SIZE)] for _ in range(SIZE)]


# ---------------------------------------------------------------------------
# La nappe de cellules
# ---------------------------------------------------------------------------
def cell_coords(x, y):
    """
    Place d'un pixel dans sa cellule : None sur un joint, sinon sa colonne et sa rangée dans la cellule (de 0 à 5).
    Les cellules occupent les pixels 1 à 6 et 9 à 14 ; 0, 7, 8 et 15 sont des joints.
    """
    if x in (0, 7, 8, 15) or y in (0, 7, 8, 15):
        return None
    return (x - 1) % 8 if x < 8 else (x - 9), (y - 1) % 8 if y < 8 else (y - 9)


def crossing(x, y):
    """Distance d'un pixel au croisement de joints le plus proche (au centre du bloc ou à ses coins), en pixels."""
    dx = min(abs(x - 7.5), abs(x + 0.5), abs(x - 15.5))
    dy = min(abs(y - 7.5), abs(y + 0.5), abs(y - 15.5))
    return dx + dy


def cells_basic():
    """Cellules monocristallines bleu nuit, leurs coins coupés sur le fond blanc, deux rubans et un reflet de verre."""
    low, high, joint, sheet, ribbon = rgb("#0B1730"), rgb("#16305A"), rgb("#0A0F18"), rgb("#C9D3DE"), rgb("#2C4670")
    img = fill("#0B1730")
    for y in range(SIZE):
        for x in range(SIZE):
            spot = cell_coords(x, y)
            if crossing(x, y) <= 1.0:
                img[y][x] = sheet
            elif spot is None:
                img[y][x] = joint
            elif crossing(x, y) <= 2.0:
                img[y][x] = mix(sheet, joint, 0.35)
            else:
                cx, cy = spot
                color = mix(high, low, cy / 5.0)
                if cx in (1, 4):
                    color = mix(color, ribbon, 0.6)
                if (x + y) % 16 in (9, 10):
                    color = mix(color, "#FFFFFF", 0.12)
                img[y][x] = color
    return img


def cells_advanced():
    """Cellules noires à contacts arrière : pas un ruban sur la face, seulement de fins doigts et un léger reflet."""
    img = fill("#05070A")
    for y in range(SIZE):
        for x in range(SIZE):
            spot = cell_coords(x, y)
            if spot is None:
                img[y][x] = rgb("#05070A") if crossing(x, y) > 1.0 else rgb("#2A3240")
                continue
            cx, cy = spot
            color = mix("#18202E", "#0A0D14", cy / 5.0)
            if cy % 2 == 1:
                color = mix(color, "#1E2838", 0.5)
            if (x + y) % 16 in (9, 10):
                color = mix(color, "#9FB4D0", 0.14)
            img[y][x] = color
    return img


def cells_perovskite():
    """Cellules irisées : du sarcelle au violet selon la lumière, un contact doré au milieu de chacune."""
    img = fill("#0B0B12")
    for y in range(SIZE):
        for x in range(SIZE):
            spot = cell_coords(x, y)
            if spot is None:
                img[y][x] = rgb("#0B0B12") if crossing(x, y) > 1.0 else rgb("#C9A23A")
                continue
            cx, cy = spot
            # La teinte tourne en diagonale, d'un bout à l'autre de la nappe, et se raccorde d'un bloc à l'autre.
            t = 0.5 + 0.5 * math.sin(2 * math.pi * (x + y) / 16.0)
            color = mix(mix("#1C6E7A", "#5A2D82", t), "#0B0B12", cy / 12.0)
            if x in (3, 12):
                color = rgb("#C9A23A") if cy % 2 == 0 else rgb("#8E7020")
            img[y][x] = color
    return img


def cells_quantum(step, frames):
    """Cellules quantiques : un fond presque noir où glissent des franges d'interférence cyan et magenta."""
    img = fill("#090416")
    phase = 2 * math.pi * step / frames
    for y in range(SIZE):
        for x in range(SIZE):
            spot = cell_coords(x, y)
            if spot is None:
                img[y][x] = rgb("#E9FFFF") if crossing(x, y) <= 1.0 else rgb("#241A4E")
                continue
            a = math.sin(2 * math.pi * (2 * x + y) / 16.0 + phase)
            b = math.sin(2 * math.pi * (x - 2 * y) / 16.0 - phase)
            wave = (a + b) / 2.0
            color = mix("#0E0828", "#3DE8FF", max(0.0, wave) * 0.55)
            color = mix(color, "#C04BFF", max(0.0, -wave) * 0.5)
            img[y][x] = color
    return img


# ---------------------------------------------------------------------------
# Le cadre, le dessous, le mât
# ---------------------------------------------------------------------------
def frame_rows(rows):
    """Une texture faite de bandes horizontales : le cadre est plaqué en entier sur chacune de ses faces."""
    img = []
    for y in range(SIZE):
        color = rgb(rows.get(y, rows["base"]))
        img.append([color for _ in range(SIZE)])
    return img


# Le cadre est un profilé : chaque face prend sa bande de la texture (voir PROFILE) ; 0 le dessus, 1 à 3 la face
# extérieure (arête vive, milieu, pied), 4 la face intérieure, 5 le joint de vitrage, 15 le dessous.
FRAME_BASIC = {0: "#F1F5F9", 1: "#D9E0E7", 2: "#B2BBC6", 3: "#8C96A1", 4: "#A2ABB6", 5: "#111418", 15: "#6E7782", "base": "#AEB7C2"}
FRAME_ADVANCED = {0: "#6E7884", 1: "#4C5560", 2: "#38D6F0", 3: "#22272E", 4: "#2B3038", 5: "#07090C", 15: "#15181D", "base": "#2B3038"}
FRAME_PEROVSKITE = {0: "#55565E", 1: "#2C2D34", 2: "#D4AF37", 3: "#121317", 4: "#1A1B20", 5: "#060708", 15: "#08090B", "base": "#15171C"}
FRAME_QUANTUM = {0: "#FFFFFF", 1: "#E6EDF3", 2: "#7FF3FF", 3: "#AEB9C4", 4: "#C8D0D8", 5: "#16263A", 15: "#9AA6B3", "base": "#DDE3EA"}


def frame_texture(rows, brushed=False):
    """Les bandes du profilé ; l'aluminium brut a un léger brossage sur sa face extérieure."""
    img = frame_rows(rows)
    if brushed:
        for x in range(SIZE):
            if x % 5 == 2:
                img[2][x] = mix(rows[2], "#000000", 0.07)
            if x % 7 == 4:
                img[1][x] = mix(rows[1], "#FFFFFF", 0.25)
    return img


def back(color, accent=None):
    """Dessous de la nappe et rails : la face arrière, un fin quadrillage, et le liseré du cadre s'il en a un."""
    img = fill(color)
    for y in range(SIZE):
        for x in range(SIZE):
            if (x + 2 * y) % 11 == 0:
                img[y][x] = mix(color, "#FFFFFF", 0.05)
            if accent and (x in (0, 15) or y in (0, 15)):
                img[y][x] = rgb(accent)
    return img


def mast(color, accent=None):
    """Le mât : un tube de métal, éclairé d'un côté, et un collier de couleur à mi-hauteur s'il en a un."""
    img = fill(color)
    for y in range(SIZE):
        for x in range(SIZE):
            if x % 8 in (0, 1):
                img[y][x] = mix(color, "#FFFFFF", 0.18)
            elif x % 8 == 7:
                img[y][x] = mix(color, "#000000", 0.25)
            if accent and y in (7, 8):
                img[y][x] = rgb(accent)
    return img


def glow():
    """Anneau lumineux du panneau quantique."""
    img = fill("#7FF3FF")
    for y in range(SIZE):
        for x in range(SIZE):
            if y in (7, 8):
                img[y][x] = rgb("#E8FFFF")
    return img


# ---------------------------------------------------------------------------
# Les modèles
# ---------------------------------------------------------------------------
def body(tier):
    """La nappe sur son mât, vue sans cadre : platine, boulons, mât, tête, tube porteur, rails, boîtier, nappe."""
    parts = [
        box(4, 0, 4, 12, 0.5, 12, "dark"),
        box(4.5, 0.5, 4.5, 5.5, 1, 5.5, "mast"), box(10.5, 0.5, 4.5, 11.5, 1, 5.5, "mast"),
        box(4.5, 0.5, 10.5, 5.5, 1, 11.5, "mast"), box(10.5, 0.5, 10.5, 11.5, 1, 11.5, "mast"),
        box(7, 0.5, 7, 9, 9, 9, "mast"),
        box(6, 9, 6.5, 10, 10.5, 9.5, "dark"),
        box(0, 10.5, 7, 16, 11.5, 9, "alu"),
        box(2, 11.5, 0, 3, 12, 16, "back"), box(13, 11.5, 0, 14, 12, 16, "back"),
        box(9, 11, 10, 12, 12, 13, "dark"),
        # La nappe : ses chants ne se dessinent pas, le cadre les couvre (et deux nappes voisines se touchent).
        {**box(0, 12, 0, 16, 13, 16, "back"), "faces": {"up": "cells"}, "omit": ["north", "south", "east", "west"],
         "only": ["up", "down"]},
    ]
    if tier == "perovskite":
        # Une tête de suivi du soleil sur deux axes, et un collier doré en haut du mât.
        parts[6] = box(5.5, 9, 6, 10.5, 10.5, 10, "dark")
        parts.append(box(6.5, 8, 6.5, 9.5, 9, 9.5, "accent"))
    if tier == "quantum":
        # Deux anneaux lumineux autour du mât.
        parts.append(box(6.5, 3, 6.5, 9.5, 4, 9.5, "glow"))
        parts.append(box(6.5, 7, 6.5, 9.5, 8, 9.5, "glow"))
    return parts


# Bande de la texture du cadre que prend chaque face : [u0, v0, u1, v1] selon la largeur de la face.
def profile(width):
    u = min(16, max(1, round(width)))
    return {"up": [0, 0, u, 1], "north": [0, 1, u, 4], "south": [0, 4, u, 5], "down": [0, 15, u, 16],
            "east": [0, 1, 1, 4], "west": [0, 1, 1, 4]}


def frame_part(x0, x1, z0, z1, y0, y1, omit, uv):
    return {**box(x0, y0, z0, x1, y1, z1, "alu"), "stretch": True, "uv": uv, "omit": list(omit),
            "only": [face for face in ("north", "south", "east", "west", "up", "down") if face not in omit]}


def rail(x0, x1, omit):
    """Le profilé du côté nord, de x0 à x1, tout entier dans le bloc : deux panneaux voisins qui ne se raccordent pas
    (deux générations différentes) ont chacun le leur, dos à dos, sans se chevaucher. Il part du dessus des rails
    porteurs, sans partager de plan avec eux."""
    # Son dessous ne se dessine pas : il serait dans le plan du dessous de la nappe, qui le couvre déjà, et les deux
    # faces se disputeraient l'affichage vues d'en bas.
    return frame_part(x0, x1, 0.0, 0.75, 12.0, 13.5, set(omit) | {"down"}, profile(x1 - x0))


def seal(x0, x1, omit):
    """Le joint de vitrage, noir, posé sur la nappe le long du profilé."""
    band = {face: [0, 5, 16, 6] for face in ("up", "north", "south", "east", "west", "down")}
    return frame_part(x0, x1, 0.75, 1.25, 13.0, 13.25, set(omit) | {"down", "north"}, band)


# Le côté nord : le profilé entre les deux bouts, et son joint, qui s'arrête où commence celui du côté est (en moulinet).
EDGE = [rail(0.75, 15.25, ("east", "west")), seal(0.75, 14.75, ("east", "west"))]
# L'angle nord-est, sans voisin au nord ni à l'est : il ferme le cadre.
CORNER = [rail(15.25, 16.0, ("west", "south"))]
# Les raccords : le profilé et son joint vont jusqu'au bord du bloc, vers le panneau voisin.
END_CW = [rail(15.25, 16.0, ("west",)), seal(14.75, 16.0, ("west",))]
END_CCW = [rail(0.0, 0.75, ("east",)), seal(0.0, 0.75, ("east",))]


TURN = {"north": "east", "east": "south", "south": "west", "west": "north", "up": "up", "down": "down"}


def rotate(part, quarter):
    """Tourne une pièce d'un quart de tour (ou plus) autour du centre du bloc, dans le sens du jeu (nord vers est),
    avec ses faces : celles qu'on omet et les bandes de texture qu'elles prennent."""
    (x0, y0, z0), (x1, y1, z1) = part["from"], part["to"]
    omit = list(part.get("omit", []))
    uv = dict(part.get("uv", {}))
    for _ in range(quarter % 4):
        x0, z0, x1, z1 = 16 - z1, x0, 16 - z0, x1
        omit = [TURN[face] for face in omit]
        uv = {TURN[face]: value for face, value in uv.items()}
    turned = {**part, "from": [x0, y0, z0], "to": [x1, y1, z1], "omit": omit, "uv": uv}
    if "only" in part:
        turned["only"] = [face for face in ("north", "south", "east", "west", "up", "down") if face not in omit]
    return turned


TIERS = {
    # suffixe : textures (clé du modèle -> nom de la texture), pièces lumineuses
    "": {"cells": "solar_panel_top", "alu": "solar_frame", "back": "solar_back", "mast": "machine_frame", "dark": "machine_dark"},
    "_advanced": {"cells": "solar_panel_advanced_top", "alu": "solar_panel_advanced_frame", "back": "solar_panel_advanced_back",
                  "mast": "solar_panel_advanced_mast", "dark": "solar_panel_advanced_back"},
    "_perovskite": {"cells": "solar_panel_perovskite_top", "alu": "solar_panel_perovskite_frame", "back": "solar_panel_perovskite_back",
                    "mast": "solar_panel_perovskite_mast", "dark": "solar_panel_perovskite_back", "accent": "solar_panel_perovskite_frame"},
    "_quantum": {"cells": "solar_panel_quantum_top", "alu": "solar_panel_quantum_frame", "back": "solar_panel_quantum_back",
                 "mast": "solar_panel_quantum_mast", "dark": "solar_panel_quantum_back", "glow": "solar_panel_quantum_glow"},
}


def write_model(name, parts, textures, emissive=(), item=False):
    model = gm.build_extra(parts, textures, set(emissive))
    model["textures"]["particle"] = "orbitalis:block/" + textures["alu"]
    # Les faces collées à une autre pièce du cadre ou posées sur la nappe ne se dessinent pas.
    for part, element in zip(parts, model["elements"]):
        for face in part.get("omit", []):
            element["faces"].pop(face, None)
    if item:
        model["display"] = display((0, 0, 0), (16, 13.5, 16), gui_rotation=(35, 225, 0))
    with open(os.path.join(gm.MODEL_DIR, name + ".json"), "w", newline="\n") as f:
        json.dump(model, f, indent=2)


def main():
    gm.write_texture("solar_panel_top", [cells_basic()])
    gm.write_texture("solar_frame", [frame_texture(FRAME_BASIC, brushed=True)])
    gm.write_texture("solar_back", [back("#2A3038")])
    gm.write_texture("solar_panel_advanced_top", [cells_advanced()])
    gm.write_texture("solar_panel_advanced_frame", [frame_texture(FRAME_ADVANCED)])
    gm.write_texture("solar_panel_advanced_back", [back("#1E232A", "#38D6F0")])
    gm.write_texture("solar_panel_advanced_mast", [mast("#3A4048", "#38D6F0")])
    gm.write_texture("solar_panel_perovskite_top", [cells_perovskite()])
    gm.write_texture("solar_panel_perovskite_frame", [frame_texture(FRAME_PEROVSKITE)])
    gm.write_texture("solar_panel_perovskite_back", [back("#1A1820", "#D4AF37")])
    gm.write_texture("solar_panel_perovskite_mast", [mast("#2A2B30", "#D4AF37")])
    frames = 8
    gm.write_texture("solar_panel_quantum_top", [cells_quantum(k, frames) for k in range(frames)], 3)
    gm.write_texture("solar_panel_quantum_frame", [frame_texture(FRAME_QUANTUM)])
    gm.write_texture("solar_panel_quantum_back", [back("#2A2F3A", "#7FF3FF")])
    gm.write_texture("solar_panel_quantum_mast", [mast("#C9D2DC", "#7FF3FF")])
    gm.write_texture("solar_panel_quantum_glow", [glow()])

    overlaps = 0
    for suffix, textures in TIERS.items():
        tier = suffix.lstrip("_")
        emissive = ("glow",) if tier == "quantum" else ()
        parts = separate(body(tier))
        overlaps += check_overlaps("solar_panel" + suffix, parts)
        write_model("solar_panel" + suffix, parts, textures, emissive)
        glowing = ("alu",) if tier == "quantum" else ()
        write_model("solar_edge" + suffix, EDGE, textures, glowing)
        write_model("solar_corner" + suffix, CORNER, textures, glowing)
        write_model("solar_end_cw" + suffix, END_CW, textures, glowing)
        write_model("solar_end_ccw" + suffix, END_CCW, textures, glowing)
        frame = [rotate(part, k) for k in range(4) for part in EDGE + CORNER]
        overlaps += check_overlaps("solar_frame" + suffix, frame)
        item = separate(body(tier) + frame)
        overlaps += check_overlaps("solar_panel" + suffix + "_item", item)
        write_model("solar_panel" + suffix + "_item", item, textures, emissive, item=True)
    print("Panneaux solaires : %d générations écrites, %d chevauchement(s)." % (len(TIERS), overlaps))


if __name__ == "__main__":
    main()
