#!/usr/bin/env python3
"""
Outils communs aux générateurs de modèles d'Orbitalis.

- build_tiled : écrit un modèle dont les grandes pièces gardent des pixels de la taille de ceux des blocs. Une pièce
  plus grande qu'un bloc est découpée le long de la grille des blocs, et chaque morceau reçoit la portion de texture
  qui correspond à sa place : la texture se répète de bloc en bloc au lieu d'être étirée.
- check_overlaps : signale les faces de deux pièces qui se superposent dans le même plan et dans le même sens. Ce
  sont elles qui scintillent en jeu (les deux textures se disputent les mêmes pixels).
- display : présentation d'un modèle dans l'inventaire, en main, au sol et dans un cadre, pour un modèle plus grand
  qu'un bloc ou dont le centre n'est pas celui du bloc.
"""
import math

FACES = ("north", "south", "west", "east", "up", "down")
# Axe et sens de chaque face.
NORMALS = {"west": (0, -1), "east": (0, 1), "down": (1, -1), "up": (1, 1), "north": (2, -1), "south": (2, 1)}


def _cuts(lo, hi):
    """Découpe d'un intervalle le long de la grille des blocs (tous les 16)."""
    points = [lo]
    edge = math.floor(lo / 16.0) * 16 + 16
    while edge < hi - 1e-6:
        points.append(edge)
        edge += 16
    points.append(hi)
    return list(zip(points[:-1], points[1:]))


def _uv(face, lo, hi):
    """Portion de texture d'une face, pour un morceau qui tient dans une case de la grille : celle que le jeu choisirait."""
    x0, y0, z0 = (c - math.floor(c / 16.0 + 1e-9) * 16 for c in lo)
    x1, y1, z1 = (x0 + hi[0] - lo[0], y0 + hi[1] - lo[1], z0 + hi[2] - lo[2])
    return {
        "down": [x0, 16 - z1, x1, 16 - z0], "up": [x0, z0, x1, z1],
        "north": [16 - x1, 16 - y1, 16 - x0, 16 - y0], "south": [x0, 16 - y1, x1, 16 - y0],
        "west": [z0, 16 - y1, z1, 16 - y0], "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0],
    }[face]


def build_tiled(parts, textures, emissive=(), particle="orbitalis:block/machine_frame", display=None):
    """
    Modèle fait de pavés.

    Chaque pièce est un dictionnaire : "from", "to", "texture", et au besoin
    - "faces"    : texture propre à certaines faces ;
    - "uv"       : portion de texture imposée à certaines faces ({face: [u0, v0, u1, v1]}) ;
    - "only"     : les seules faces à dessiner ;
    - "stretch"  : la texture entière est plaquée sur chaque face, sans découpe ;
    - "rotation" : rotation de la pièce (elle n'est alors pas découpée).

    textures : nom court -> nom de la texture de bloc
    emissive : noms courts des textures lumineuses
    """
    used = set()
    elements = []
    for part in parts:
        own = part.get("faces", {})
        forced = part.get("uv", {})
        only = part.get("only", FACES)
        whole = part.get("stretch") or "rotation" in part
        lo, hi = part["from"], part["to"]
        pieces = [(lo, hi)] if whole else [
            ([x[0], y[0], z[0]], [x[1], y[1], z[1]])
            for x in _cuts(lo[0], hi[0]) for y in _cuts(lo[1], hi[1]) for z in _cuts(lo[2], hi[2])]
        for a, b in pieces:
            faces = {}
            for face in only:
                axis, sign = NORMALS[face]
                # Une face créée par la découpe est à l'intérieur de la pièce : on ne la dessine pas.
                if abs((b if sign > 0 else a)[axis] - (hi if sign > 0 else lo)[axis]) > 1e-6:
                    continue
                name = own.get(face, part["texture"])
                used.add(name)
                uv = forced.get(face) or ([0, 0, 16, 16] if whole else [round(v, 4) for v in _uv(face, a, b)])
                faces[face] = {"texture": "#" + name, "uv": uv}
            if not faces:
                continue
            element = {"from": [round(c, 4) for c in a], "to": [round(c, 4) for c in b], "faces": faces}
            if "rotation" in part:
                element["rotation"] = part["rotation"]
            if part["texture"] in emissive:
                element["light_emission"] = 15
            elements.append(element)
    model = {"parent": "minecraft:block/block",
             "textures": {name: "orbitalis:block/" + textures[name] for name in sorted(used)}, "elements": elements}
    model["textures"]["particle"] = particle
    if display:
        model["display"] = display
    return model


def check_overlaps(name, parts, quiet=False):
    """Faces de deux pièces superposées dans le même plan et le même sens : renvoie leur nombre, et les signale."""
    found = 0
    plain = [p for p in parts if "rotation" not in p]
    for i, a in enumerate(plain):
        for b in plain[i + 1:]:
            for face, (axis, sign) in NORMALS.items():
                if face not in a.get("only", FACES) or face not in b.get("only", FACES):
                    continue
                ca = (a["to"] if sign > 0 else a["from"])[axis]
                cb = (b["to"] if sign > 0 else b["from"])[axis]
                if abs(ca - cb) > 1e-6:
                    continue
                others = [k for k in range(3) if k != axis]
                if all(min(a["to"][k], b["to"][k]) - max(a["from"][k], b["from"][k]) > 1e-6 for k in others):
                    found += 1
                    if not quiet:
                        print("  chevauchement dans %s, face %s : %s / %s" % (name, face, a["from"] + a["to"], b["from"] + b["to"]))
    return found


def separate(parts, gap=0.2):
    """
    Écarte les faces superposées : de deux faces qui se recouvrent dans le même plan, la plus petite recule d'un
    cinquième de pixel dans sa pièce (moins, et les deux faces se disputeraient encore l'affichage vues de loin). Elle passe ainsi sous l'autre, et plus rien ne scintille. Renvoie de nouvelles pièces.
    """
    parts = [{**p, "from": list(p["from"]), "to": list(p["to"])} for p in parts]
    plain = [p for p in parts if "rotation" not in p]
    for _ in range(12):
        moved = False
        for i, a in enumerate(plain):
            for b in plain[i + 1:]:
                for face, (axis, sign) in NORMALS.items():
                    if face not in a.get("only", FACES) or face not in b.get("only", FACES):
                        continue
                    key = "to" if sign > 0 else "from"
                    if abs(a[key][axis] - b[key][axis]) > 1e-6:
                        continue
                    others = [k for k in range(3) if k != axis]
                    if not all(min(a["to"][k], b["to"][k]) - max(a["from"][k], b["from"][k]) > 1e-6 for k in others):
                        continue
                    area = lambda p: (p["to"][others[0]] - p["from"][others[0]]) * (p["to"][others[1]] - p["from"][others[1]])
                    small = a if area(a) <= area(b) else b
                    # Une pièce mince recule moins : elle ne doit pas s'aplatir.
                    step = min(gap, (small["to"][axis] - small["from"][axis]) / 4.0)
                    if step > 0.01:
                        small[key][axis] = round(small[key][axis] - sign * step, 4)
                        moved = True
        if not moved:
            break
    return parts


def _rotation(rx, ry, rz):
    """Matrice de la rotation d'un affichage d'objet : autour de z, puis de y, puis de x."""
    cx, sx, cy, sy, cz, sz = (f(math.radians(a)) for a in (rx, ry, rz) for f in (math.cos, math.sin))
    mx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    my = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    mz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]

    def mul(a, b):
        return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]

    return mul(mx, mul(my, mz))


def _view(rotation, scale, centre, shift=(0, 0, 0)):
    """Un affichage : le centre du modèle est ramené au centre de la case, puis décalé de `shift` (en seizièmes)."""
    matrix = _rotation(*rotation)
    offset = [(c - 8.0) * scale for c in centre]
    moved = [sum(matrix[i][k] * offset[k] for k in range(3)) for i in range(3)]
    return {"rotation": list(rotation), "translation": [round(shift[i] - moved[i], 3) for i in range(3)], "scale": [round(scale, 4)] * 3}


def display(lo, hi, gui_rotation=(30, 225, 0), fill=1.0):
    """
    Présentation d'un modèle qui occupe le pavé lo..hi (en seizièmes de bloc) : réduit pour tenir dans une case
    d'inventaire, et recentré.

    fill : 1 pour remplir la case comme un bloc ordinaire ; moins pour laisser de la marge
    """
    centre = [(a + b) / 2.0 for a, b in zip(lo, hi)]
    extent = max(b - a for a, b in zip(lo, hi)) / 16.0
    k = fill / max(1.0, extent)
    return {
        "gui": _view(gui_rotation, 0.625 * k, centre),
        "ground": _view((0, 0, 0), 0.25 * k, centre, (0, 3, 0)),
        "fixed": _view((0, 0, 0), 0.5 * k, centre),
        "head": _view((0, 0, 0), 1.0 * k, centre),
        "thirdperson_righthand": _view((75, 45, 0), 0.375 * k, centre, (0, 2.5, 0)),
        "thirdperson_lefthand": _view((75, 45, 0), 0.375 * k, centre, (0, 2.5, 0)),
        "firstperson_righthand": _view((0, 45, 0), 0.4 * k, centre),
        "firstperson_lefthand": _view((0, 225, 0), 0.4 * k, centre),
    }
