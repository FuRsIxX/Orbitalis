#!/usr/bin/env python3
"""
Génère les modèles et les textures des conduits d'Orbitalis : le câble électrique, le tuyau à liquides, le tuyau
à gaz et le câble de données.

Tous ont la même silhouette, celle d'un transmetteur industriel : un tube carré, tenu par quatre arêtes
métalliques, avec un flanc entre chaque paire d'arêtes.
Ils se reconnaissent à deux choses :

- la couleur de leurs nœuds et de leurs raccords : cuivre pour le câble, bleu pour les liquides, laiton pour les
  gaz, turquoise pour les données ;
- leurs flancs : pour le câble, des panneaux sombres parcourus d'un filet lumineux qui pulse quand le courant
  passe ; pour le tuyau à liquides, un verre teinté ; pour le tuyau à gaz, un verre plus clair, grillagé ; pour le
  câble de données, une gaine noire à fibre optique, où filent des paquets de lumière.

Un conduit se dessine par morceaux :

- un nœud en son milieu, sauf quand il ne fait que passer tout droit : il porte alors un simple tronçon de tube,
  et une longue ligne de conduits se lit comme un seul tube, sans nœud à chaque bloc ;
- un bras par côté raccordé. Vers un autre conduit, le bras va jusqu'au bord du bloc. Vers une machine, il se
  termine par un raccord : une platine contre la machine et un collier à la couleur du conduit. Sur un tuyau en
  extraction, le collier est orange et rayé.

Les tuyaux laissent voir ce qui y circule, à sa couleur : un liquide remplit le tube, un gaz y passe en veine
plus fine. Le verre est teinté, pas limpide : on voit le contenu sans que le tuyau disparaisse.

Les bras sont dessinés vers le nord, le tronçon droit du nord au sud : le jeu les tourne vers les autres côtés.

Usage : python tools/generate_conduit_models.py
"""
import json
import math
import os

from generate_machine_models import blend, plain, shade, write_texture
from generate_textures import ROOT, SIZE, rgb
from model_kit import check_overlaps

MODEL_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "orbitalis", "models", "block", "conduit")
FACES = ("north", "south", "east", "west", "up", "down")

# Liquides et gaz reconnus, dans l'ordre de leur numéro de contenu (voir ConduitBlock.contentOf) : 1, 2, 3...
LIQUIDS = [("#3F76E4", "#8FB6FF"), ("#C98A3A", "#E8B76A"), ("#5FB0F5", "#C7E6FF"), ("#D96A6A", "#F2A6A6")]
GASES = [("#F0A6A6", "#FFE7E7"), ("#BFE4FF", "#F4FBFF")]
MAX_GLOW = 3

# Tube : de TUBE_MIN à 16 - TUBE_MIN, avec des arêtes d'un pixel. Les deux câbles (électrique, données) sont un
# peu plus fins que les tuyaux.
TUBE_MIN = 5
CABLE_MIN = 6
# Raccord à une machine : la platine fait un pixel d'épaisseur, le collier un pixel et demi.
PLATE = 1
COLLAR = 2.5


# ---------------------------------------------------------------------------
# Textures
# ---------------------------------------------------------------------------
def frame_texture():
    """Arêtes des conduits : un métal sombre brossé, le même pour les trois."""
    return plain(210, "#4A515A", "#5C646E", "#3A4048")


CABLE_FRAMES = 16


def on_line(x, y, branch):
    """
    Place d'un pixel par rapport au filet d'un flanc : 2 sur le filet, 1 sur son halo, 0 ailleurs.

    branch : "cross" pour un nœud (le filet se croise au milieu de la face), "v" ou "h" pour un tronçon, où il ne
    court que dans un sens : celui du câble, selon la face où le flanc est plaqué.
    """
    across = [abs(c - 7.5) for c, used in ((x, branch in ("cross", "v")), (y, branch in ("cross", "h"))) if used]
    nearest = min(across)
    return 2 if nearest < 1 else 1 if nearest < 2 else 0


def along(x, y, branch):
    """Distance parcourue le long du filet, en pixels : c'est elle qui règle l'avance de la lumière, sans biais."""
    if branch == "v" or (branch == "cross" and abs(x - 7.5) < 2):
        return y
    return x


def cable_panel_frame(glow, step, branch="cross", frames=CABLE_FRAMES):
    """
    Flanc du câble : un panneau sombre parcouru, en son milieu, d'un filet conducteur. Éteint au repos, le filet
    s'allume avec la charge.

    Quand le courant passe, une impulsion de lumière glisse le long du filet : une tête claire et une courte
    traîne, rien de plus. Elle avance d'un pixel par image et parcourt un bloc par cycle : d'un bloc au suivant,
    le mouvement se raccorde sans saut.
    """
    base = plain(211, "#1B1E22", "#24282D", "#141619")
    level = glow / MAX_GLOW
    rest = blend(rgb("#4A3212"), rgb("#B06C2C"), level)
    head = blend(rgb("#9A6428"), rgb("#F0C070"), level)
    for y in range(SIZE):
        for x in range(SIZE):
            if on_line(x, y, branch) != 2:
                continue
            if glow == 0:
                base[y][x] = rgb("#3A2A12")
                continue
            # Un filet allumé, parcouru d'une respiration lente et à peine plus claire : ni halo, ni éclat.
            wave = 0.5 + 0.5 * math.cos(2 * math.pi * (along(x, y, branch) / 16.0 + step / frames))
            base[y][x] = blend(rest, head, wave ** 3)
    return base


def glass_texture(tint, alpha, mesh):
    """
    Flanc d'un tuyau : un verre teinté, assez opaque pour qu'on voie le tuyau, assez clair pour qu'on voie ce
    qu'il contient. Celui des gaz porte en plus un fin grillage.
    """
    r, g, b = rgb(tint)[:3]
    img = [[(r, g, b, alpha) for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            if mesh and (x % 2 == 0 and y % 2 == 0):
                img[y][x] = (r * 2 // 3, g * 2 // 3, b * 2 // 3, min(255, alpha + 70))
            # Un reflet clair, en diagonale, qui se répète.
            if (x + y) % 8 == 2:
                img[y][x] = (255, 255, 255, min(255, alpha + 25))
    return img


def flow_frame(colors, step, frames, wavelength):
    """Fluide en mouvement : des veines claires qui avancent en biais et reviennent à leur départ en fin de cycle."""
    base, light = rgb(colors[0]), rgb(colors[1])
    img = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            wave = 0.5 + 0.5 * math.sin(2 * math.pi * ((x + y) / wavelength - step / frames))
            row.append(blend(base, light, wave * wave))
        img.append(row)
    return img


DATA_FRAMES = 16


def data_panel_frame(step, branch="cross", frames=DATA_FRAMES):
    """
    Gaine du câble de données : noire, avec une fibre optique en son milieu (tracée comme le filet du câble
    électrique). La fibre luit faiblement, et un seul trait de lumière, court, la parcourt : rien d'autre.
    """
    base = plain(215, "#15181C", "#1C2026", "#0F1114")
    idle = rgb("#113A3E")
    lit = rgb("#8FE6EE")
    for y in range(SIZE):
        for x in range(SIZE):
            if on_line(x, y, branch) != 2:
                continue
            # Un point de lumière, un seul, qui file sur une fibre presque éteinte.
            behind = (along(x, y, branch) / 16.0 + step / frames) % 1.0
            base[y][x] = blend(idle, lit, max(0.0, 1.0 - behind / 0.14) ** 2)
    return base


def pull_texture():
    """Collier d'un tuyau en extraction : orange, rayé de noir en biais."""
    return [[rgb("#1B1E22") if (x + y) % 4 == 0 else rgb("#F08A24") if (x + y) % 4 != 2 else rgb("#FFB45A")
             for x in range(SIZE)] for y in range(SIZE)]


def write_textures():
    write_texture("conduit_frame", [frame_texture()])
    write_texture("conduit_corner_data", [plain(216, "#2BB5A4", "#55D6C4", "#1C8A7C")])
    # Une image par tick, fondue dans la suivante : la lumière file sans saccade.
    for suffix, branch in (("", "cross"), ("_v", "v"), ("_h", "h")):
        write_texture("data_panel" + suffix, [data_panel_frame(s, branch) for s in range(DATA_FRAMES)], 2)
    write_texture("conduit_pull", [pull_texture()])
    write_texture("conduit_corner_cable", [plain(212, "#C46A35", "#E08A52", "#9A4E24")])
    write_texture("conduit_corner_liquid", [plain(213, "#3F76E4", "#6A98F0", "#2F5FC4")])
    write_texture("conduit_corner_gas", [plain(214, "#C9A227", "#E6C65A", "#9C7A12")])
    for glow in range(MAX_GLOW + 1):
        for suffix, branch in (("", "cross"), ("_v", "v"), ("_h", "h")):
            frames = [cable_panel_frame(glow, s, branch) for s in range(1 if glow == 0 else CABLE_FRAMES)]
            # Deux ticks par image, chaque image fondue dans la suivante : l'impulsion glisse en continu.
            write_texture("cable_panel_%d%s" % (glow, suffix), frames, 3)
    write_texture("pipe_glass", [glass_texture("#7FA6C8", 112, False)])
    write_texture("gas_glass", [glass_texture("#C9CCB4", 96, True)])
    # Les liquides coulent vite, en veines serrées ; les gaz dérivent lentement, en veines larges et pâles.
    for index, colors in enumerate(LIQUIDS, start=1):
        write_texture("pipe_liquid_%d" % index, [flow_frame(colors, s, 8, 8.0) for s in range(8)], 2)
    for index, colors in enumerate(GASES, start=1):
        write_texture("gas_flow_%d" % index, [flow_frame(colors, s, 12, 16.0) for s in range(12)], 3)


# ---------------------------------------------------------------------------
# Modèles
# ---------------------------------------------------------------------------
def box(x0, y0, z0, x1, y1, z1, texture, light=0):
    """Pavé dont toutes les faces portent la même texture, plaquée selon sa position dans le bloc."""
    element = {"from": [x0, y0, z0], "to": [x1, y1, z1], "faces": {face: {"texture": "#" + texture} for face in FACES}}
    if light:
        element["light_emission"] = light
    return element


def tube(light, inner, lo=TUBE_MIN):  # noqa: C901
    """
    Les morceaux d'un conduit. Tous les conduits ont exactement ces pièces ; seules leurs textures changent.

    light : lumière émise par les flancs (câble en charge)
    inner : demi-largeur de ce qui circule dans le tube, en pixels, ou 0 si le tube est vide ou plein (câble)
    lo    : bord du tube ; il va de lo à 16 - lo

    Renvoie le nœud, le tronçon droit (du nord au sud), le bras vers un conduit, le bras vers une machine et le
    bras vers une machine d'un tuyau en extraction.
    """
    hi = 16 - lo
    near, far = lo + 0.25, lo + 0.75

    def run(z0, z1):
        """Un bout de tube le long de l'axe nord-sud : quatre arêtes, un flanc entre chaque paire d'arêtes."""
        parts = [box(x, y, z0, x + 1, y + 1, z1, "frame") for x in (lo, hi - 1) for y in (lo, hi - 1)]
        # Les flancs du dessus et du dessous, puis ceux des côtés : chacun a sa texture, dont le filet court dans
        # le sens du câble.
        parts += [
            box(lo + 1, 16 - far, z0, hi - 1, 16 - near, z1, "panel_v", light),
            box(lo + 1, near, z0, hi - 1, far, z1, "panel_v", light),
            box(16 - far, lo + 1, z0, 16 - near, hi - 1, z1, "panel_h", light),
            box(near, lo + 1, z0, far, hi - 1, z1, "panel_h", light),
        ]
        return parts

    # Nœud : huit angles, douze arêtes entre eux, et un flanc en retrait sur chacune des six faces.
    core = []
    for x in (lo, hi - 1):
        for y in (lo, hi - 1):
            for z in (lo, hi - 1):
                core.append(box(x, y, z, x + 1, y + 1, z + 1, "corner"))
    for a in (lo, hi - 1):
        for b in (lo, hi - 1):
            core.append(box(lo + 1, a, b, hi - 1, a + 1, b + 1, "frame"))
            core.append(box(a, lo + 1, b, a + 1, hi - 1, b + 1, "frame"))
            core.append(box(a, b, lo + 1, a + 1, b + 1, hi - 1, "frame"))
    core += [
        box(lo + 1, lo + 1, near, hi - 1, hi - 1, far, "panel", light),
        box(lo + 1, lo + 1, 16 - far, hi - 1, hi - 1, 16 - near, "panel", light),
        box(near, lo + 1, lo + 1, far, hi - 1, hi - 1, "panel", light),
        box(16 - far, lo + 1, lo + 1, 16 - near, hi - 1, hi - 1, "panel", light),
        box(lo + 1, near, lo + 1, hi - 1, far, hi - 1, "panel", light),
        box(lo + 1, 16 - far, lo + 1, hi - 1, 16 - near, hi - 1, "panel", light),
    ]
    straight = run(lo, hi)
    arm = run(0, lo)

    def linked(collar):
        """Bras vers une machine : la platine contre elle, le collier, puis le tube jusqu'au milieu du conduit."""
        return [
            box(lo - 1, lo - 1, 0, hi + 1, hi + 1, PLATE, "frame"),
            box(lo - 0.5, lo - 0.5, PLATE, hi + 0.5, hi + 0.5, COLLAR, collar),
        ] + run(COLLAR, lo)

    link, pull = linked("corner"), linked("pull")
    if inner:
        middle = box(8 - inner, 8 - inner, 8 - inner, 8 + inner, 8 + inner, 8 + inner, "content")
        core.append(middle)
        straight.append(middle)
        arm.append(box(8 - inner, 8 - inner, 0, 8 + inner, 8 + inner, 8 - inner, "content"))
        for pieces in (link, pull):
            pieces.append(box(8 - inner, 8 - inner, COLLAR, 8 + inner, 8 + inner, 8 - inner, "content"))
    return core, straight, arm, link, pull


def cable(glow):
    """Câble : flancs sombres à filet lumineux, nœuds et raccords de cuivre."""
    return tube(2 * glow, 0, CABLE_MIN), {"frame": "conduit_frame", "corner": "conduit_corner_cable", "panel": "cable_panel_%d" % glow,
                               "panel_v": "cable_panel_%d_v" % glow, "panel_h": "cable_panel_%d_h" % glow,
                               "particle": "conduit_frame"}


def liquid_pipe(content):
    """Tuyau à liquides : flancs de verre teinté, nœuds et raccords bleus ; le liquide remplit le tube."""
    textures = {"frame": "conduit_frame", "corner": "conduit_corner_liquid", "panel": "pipe_glass", "panel_v": "pipe_glass", "panel_h": "pipe_glass",
                "pull": "conduit_pull",
                "particle": "conduit_frame"}
    if content:
        textures["content"] = "pipe_liquid_%d" % content
    return tube(0, 1.75 if content else 0), textures


def gas_pipe(content):
    """Tuyau à gaz : flancs de verre grillagé, nœuds et raccords de laiton ; le gaz passe en veine fine au milieu du tube."""
    textures = {"frame": "conduit_frame", "corner": "conduit_corner_gas", "panel": "gas_glass", "panel_v": "gas_glass", "panel_h": "gas_glass",
                "pull": "conduit_pull",
                "particle": "conduit_frame"}
    if content:
        textures["content"] = "gas_flow_%d" % content
    return tube(0, 1.0 if content else 0), textures


def data_cable(_):
    """Câble de données : la carrure du câble électrique, gaine noire à fibre lumineuse, nœuds et raccords turquoise."""
    return tube(3, 0, CABLE_MIN), {"frame": "conduit_frame", "corner": "conduit_corner_data", "panel": "data_panel", "panel_v": "data_panel_v",
                         "panel_h": "data_panel_h", "particle": "conduit_frame"}


def mirrored(arm):
    """Le même bras, tourné vers le sud : il sert au tronçon droit montré dans l'inventaire."""
    result = []
    for element in arm:
        copy = json.loads(json.dumps(element))
        x0, y0, z0 = element["from"]
        x1, y1, z1 = element["to"]
        copy["from"] = [x0, y0, round(16 - z1, 2)]
        copy["to"] = [x1, y1, round(16 - z0, 2)]
        result.append(copy)
    return result


def write_model(name, elements, textures, parent=None):
    used = {face["texture"][1:] for element in elements for face in element["faces"].values()} | {"particle"}
    model = {"textures": {key: "orbitalis:block/" + value for key, value in textures.items() if key in used}, "elements": elements}
    if parent:
        model = {"parent": parent, **model}
    with open(os.path.join(MODEL_DIR, name + ".json"), "w", newline="\n") as f:
        json.dump(model, f, indent=2)


def write_models():
    os.makedirs(MODEL_DIR, exist_ok=True)
    for stale in os.listdir(MODEL_DIR):
        os.remove(os.path.join(MODEL_DIR, stale))
    count = overlaps = 0
    kinds = (("energy_cable", cable, MAX_GLOW + 1, False), ("fluid_pipe", liquid_pipe, len(LIQUIDS) + 1, True),
             ("gas_pipe", gas_pipe, len(GASES) + 1, True), ("data_cable", data_cable, 1, False))
    for name, build, variants, pulls in kinds:
        for variant in range(variants):
            (core, straight, arm, link, pull), textures = build(variant)
            pieces = {"core": core, "straight": straight, "arm": arm, "link": link}
            if pulls:
                pieces["pull"] = pull
            for piece, elements in pieces.items():
                write_model("%s_%s_%d" % (name, piece, variant), elements, textures)
                count += 1
            # Les assemblages qu'on voit en jeu : aucune face ne doit en recouvrir une autre.
            for label, parts in (("noeud et bras", core + arm + mirrored(link)), ("droit", straight + arm + mirrored(link)),
                                 ("extraction", core + pull + mirrored(arm))):
                overlaps += check_overlaps("%s %d, %s" % (name, variant, label), parts)
        # Dans l'inventaire : un tronçon droit, au repos pour le câble, vide pour les tuyaux.
        (core, straight, arm, link, pull), textures = build(0)
        write_model(name + "_inventory", straight + arm + mirrored(arm), textures, parent="minecraft:block/block")
        count += 1
    return count, overlaps


if __name__ == "__main__":
    write_textures()
    written, overlapping = write_models()
    print(f"{written} modeles de conduits ecrits, {overlapping} chevauchement(s).")
