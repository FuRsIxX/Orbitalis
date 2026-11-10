"""
Port de jonction des stations : ses textures, ses modèles et son icône, ainsi que l'icône du kit de nœud.

Le port tient sur deux blocs de haut, dessinés d'une pièce sur une toile de 16 × 32 puis coupés en deux :
- côté vide (face nord du modèle, tournée vers le dehors) : la coque blanche, et dessus la bague d'amarrage, une
  couronne d'acier à huit boulons autour d'un couvercle graphite, avec quatre repères d'alignement jaunes ;
- côté station (face sud) : le revêtement capitonné, et dessus une écoutille ronde à volant, son voyant vert « libre »
  au-dessus et sa plaque jaune dessous.
Les autres faces sont de coque.

Usage : python tools/generate_station_port.py
"""
import json
import math
import os

import generate_machine_models as gm
from generate_station import CREAM, bevel, grain, module_icon, outline
from generate_textures import BLOCK_DIR, ITEM_DIR, SIZE, TRANSPARENT, rgb, station_hull_texture, write_png

MODEL_DIR = os.path.join(gm.MODEL_DIR, "..", "station")
CX, CY = 7.5, 15.5


def shade(color, factor):
    return gm.shade(color, factor)


def tall(top, bottom):
    """Deux textures de 16 × 16 l'une sur l'autre : une toile de 16 × 32."""
    return [row[:] for row in top] + [row[:] for row in bottom]


def halves(img):
    return img[:SIZE], img[SIZE:]


def put(img, x, y, color):
    if 0 <= y < len(img) and 0 <= x < len(img[0]):
        img[y][x] = rgb(color) if isinstance(color, str) else color


def outer_texture():
    """Côté vide : la bague d'amarrage sur la coque."""
    img = tall(station_hull_texture(), station_hull_texture())
    for y in range(len(img)):
        for x in range(SIZE):
            d = math.hypot(x - CX, y - CY)
            light = -((x - CX) + (y - CY)) / max(d, 0.01)
            if d <= 4.9:
                # Le couvercle graphite, bombé : plus clair en haut à gauche.
                img[y][x] = shade(rgb("#4A515A"), 1.0 + 0.12 * light * d / 4.9)
                if 3.0 <= d <= 3.6:
                    img[y][x] = shade(img[y][x], 0.82)
            elif d <= 7.4:
                # La couronne d'acier, son bord extérieur sombre.
                img[y][x] = rgb("#2E3238") if d > 6.8 else shade(rgb("#9AA3AE"), 1.0 + 0.18 * light)
                if 4.9 < d <= 5.4:
                    img[y][x] = rgb("#3A4048")
    for k in range(8):
        a = k * math.pi / 4 + math.pi / 8
        put(img, round(CX + 6.1 * math.cos(a)), round(CY + 6.1 * math.sin(a)), "#D5DAE0")
    # Les quatre repères d'alignement, en haut, en bas et sur les côtés, sur la coque.
    for x, y in ((7, 7), (8, 7), (7, 24), (8, 24), (0, 15), (0, 16), (15, 15), (15, 16)):
        put(img, x, y, "#E8C02A")
    put(img, 7, 6, "#B58F12")
    put(img, 8, 6, "#B58F12")
    put(img, 7, 25, "#B58F12")
    put(img, 8, 25, "#B58F12")
    return img


def panel_tall():
    """Le revêtement capitonné, sur deux blocs : chaque bloc a son joint et ses attaches."""
    halves_ = []
    for seed in (81, 82):
        img = grain(seed, CREAM, 0.035)
        outline(img, 0, 0, 15, 15, "#B7B09D")
        bevel(img, 1, 1, 14, 14, 1.06, 0.9)
        for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
            put(img, x, y, "#9C9583")
        halves_.append(img)
    return tall(*halves_)


def hatch(img, cx, cy, radius, wheel=True):
    """Une écoutille ronde : bague sombre, battant clair bombé, volant à trois rayons."""
    for y in range(len(img)):
        for x in range(len(img[0])):
            d = math.hypot(x - cx, y - cy)
            if d > radius:
                continue
            light = -((x - cx) + (y - cy)) / max(d, 0.01)
            if d > radius - 0.7:
                img[y][x] = rgb("#3A4048")
            elif d > radius - 1.6:
                img[y][x] = shade(rgb("#8C949E"), 1.0 + 0.2 * light)
            else:
                img[y][x] = shade(rgb("#E3E6E9"), 1.0 + 0.07 * light * d / radius)
    if not wheel:
        return
    # Le volant : une couronne sombre, quatre rayons en croix et le moyeu, d'un pixel de large chacun.
    r = radius * 0.55
    for y in range(len(img)):
        for x in range(len(img[0])):
            d = math.hypot(x - cx, y - cy)
            if r - 0.55 <= d <= r + 0.45:
                img[y][x] = shade(rgb("#4A515A"), 1.15 if (y - cy) < 0 else 0.9)
            elif d < r - 0.55 and (abs(x - cx) < 1.0 or abs(y - cy) < 1.0):
                img[y][x] = rgb("#6B747F")
    for x in (math.floor(cx), math.ceil(cx)):
        for y in (math.floor(cy), math.ceil(cy)):
            put(img, x, y, "#2E3238")


def inner_texture():
    """Côté station : l'écoutille ronde dans le revêtement, son voyant et sa plaque."""
    img = panel_tall()
    hatch(img, CX, CY, 7.0)
    # Le voyant « libre », au-dessus, et la plaque jaune, dessous.
    for x in (7, 8):
        put(img, x, 2, "#5BE08A")
        put(img, x, 1, "#2E7A48")
    for x in range(5, 11):
        put(img, x, 28, "#E8C02A" if x % 2 else "#2E3238")
        put(img, x, 29, "#2E3238" if x % 2 else "#E8C02A")
    return img


def item_icon():
    """L'icône du port : l'écoutille, sa bague jaune de repérage."""
    img = [[TRANSPARENT for _ in range(SIZE)] for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            d = math.hypot(x - 7.5, y - 7.5)
            if 6.4 < d <= 7.5:
                img[y][x] = rgb("#E8C02A") if d <= 7.1 else rgb("#8A6E12")
    hatch(img, 7.5, 7.5, 6.4)
    return img


def node_icon():
    """Kit de nœud : le module, et au-dessus, un carrefour en T aux trois bouts jaunes (ses trois ports)."""
    emblem = {}
    for x in range(4, 12):
        emblem[(x, 3)] = "#C9CED4"
        emblem[(x, 4)] = "#8C949E"
    for y in range(0, 3):
        emblem[(7, y)] = "#C9CED4"
        emblem[(8, y)] = "#8C949E"
    for x, y in ((3, 3), (3, 4), (12, 3), (12, 4), (7, 0), (8, 0)):
        emblem[(x, y)] = "#E8C02A"
    return module_icon("#D9DEE4", emblem)


def model(north, south):
    tex = "orbitalis:block/"
    return {
        "parent": "minecraft:block/cube",
        "textures": {"particle": tex + "station_hull", "north": tex + north, "south": tex + south, "east": tex + "station_hull",
                     "west": tex + "station_hull", "up": tex + "station_hull", "down": tex + "station_hull"},
    }


def main():
    outer_top, outer_bottom = halves(outer_texture())
    inner_top, inner_bottom = halves(inner_texture())
    for name, img in (("station_port_outer_upper", outer_top), ("station_port_outer_lower", outer_bottom),
                      ("station_port_inner_upper", inner_top), ("station_port_inner_lower", inner_bottom)):
        write_png(os.path.join(BLOCK_DIR, name + ".png"), img)
    write_png(os.path.join(ITEM_DIR, "station_port.png"), item_icon())
    write_png(os.path.join(ITEM_DIR, "node_module.png"), node_icon())
    for half in ("upper", "lower"):
        with open(os.path.join(MODEL_DIR, "station_port_" + half + ".json"), "w", newline="\n") as f:
            json.dump(model("station_port_outer_" + half, "station_port_inner_" + half), f, indent=1)
    with open(os.path.join(MODEL_DIR, "station_port_item.json"), "w", newline="\n") as f:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "orbitalis:item/station_port"}}, f, indent=1)
    print("Port de jonction : 4 textures, 3 modèles, 2 icônes.")


if __name__ == "__main__":
    main()
