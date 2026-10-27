#!/usr/bin/env python3
"""
Génère ce qu'il faut voir de Proxima b, la planète de la naine rouge la plus proche du Soleil, dans Orbitalis.

Proxima b tourne toujours la même face vers son étoile : une face de jour brûlée, une face de nuit gelée, et entre
elles une bande de crépuscule éternel, où la vie a pris. Sous une lumière aussi rouge, les plantes sont sombres : elles
boivent toute la lumière qu'elles reçoivent.

- la roche de Proxima b, un basalte sombre et violacé ; son régolithe, brun rouille ;
- la mousse du crépuscule, presque noire, violette au soleil rasant (le dessus d'un bloc de régolithe) ;
- la cendre de la face de jour, orange et grise ; le givre de la face de nuit, bleu nuit ;
- la flore : la laîche rouge, la fougère d'ombre et le bulbe-lanterne, qui luit ;
- la naine rouge dans le ciel, et Proxima b vue d'orbite (des images de particule, comme Jupiter vue d'Europe).

Usage : python tools/generate_proxima.py
"""
import math
import os
import random

from generate_textures import BLOCK_DIR, ROOT, blotchy, rgb, write_png

PARTICLE_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "orbitalis", "textures", "particle")
CLEAR = (0, 0, 0, 0)


def rock_texture():
    """Roche de Proxima b : un basalte gris sombre, nuancé de violet, quelques cristaux rouges."""
    img = blotchy(301, [rgb("#3B3340"), rgb("#332C38"), rgb("#453C4A"), rgb("#2B2530")], [5, 3, 2, 1], passes=2)
    for x, y in ((3, 4), (11, 9), (6, 13), (14, 2)):
        img[y][x] = rgb("#7A3A3A")
    return img


def regolith_texture():
    """Régolithe : une terre brun rouille, des grains plus clairs et des cailloux sombres."""
    img = blotchy(302, [rgb("#6B3A2A"), rgb("#5E3224"), rgb("#7A4632"), rgb("#52291D")], [5, 3, 2, 1], passes=2)
    for x, y in ((2, 2), (9, 5), (13, 12), (5, 10)):
        img[y][x] = rgb("#9A6248")
    for x, y in ((7, 7), (12, 3), (3, 14)):
        img[y][x] = rgb("#3A1E16")
    return img


def moss_top_texture():
    """Mousse du crépuscule : presque noire, aux reflets violets et rouge sombre là où la lumière rase."""
    img = blotchy(303, [rgb("#2A1830"), rgb("#22122A"), rgb("#36203E"), rgb("#1A0E20")], [5, 3, 2, 2], passes=2)
    rnd = random.Random(304)
    for _ in range(14):
        img[rnd.randrange(16)][rnd.randrange(16)] = rgb("#5A2A4A")
    for _ in range(6):
        img[rnd.randrange(16)][rnd.randrange(16)] = rgb("#7A2E36")
    return img


def moss_side_texture():
    """Flanc d'un bloc moussu : le régolithe, et la mousse qui déborde du dessus en franges inégales."""
    img = regolith_texture()
    top = moss_top_texture()
    depth = [3, 4, 3, 2, 3, 4, 5, 3, 2, 3, 4, 3, 2, 4, 3, 3]
    for x in range(16):
        for y in range(depth[x]):
            img[y][x] = top[y][x]
    return img


def ash_texture():
    """Cendre de la face de jour : une poussière orange et grise, cuite par l'étoile, piquée de grains noirs."""
    img = blotchy(305, [rgb("#9A5A36"), rgb("#8A5032"), rgb("#A8683E"), rgb("#7A4630")], [5, 3, 2, 1], passes=2)
    for x, y in ((4, 3), (10, 8), (2, 12), (13, 14), (7, 1)):
        img[y][x] = rgb("#2A1A14")
    return img


def frost_texture():
    """Givre de la face de nuit : une croûte bleu nuit, des aiguilles de givre plus claires."""
    img = blotchy(306, [rgb("#2A3A5A"), rgb("#24324E"), rgb("#33466A"), rgb("#1E2A44")], [5, 3, 2, 1], passes=2)
    for x, y in ((3, 2), (4, 3), (11, 6), (12, 7), (6, 11), (7, 12), (14, 13)):
        img[y][x] = rgb("#8FB0D8")
    return img


def plant(draw):
    img = [[CLEAR for _ in range(16)] for _ in range(16)]
    draw(img)
    return img


def sedge(img):
    """Laîche rouge : une touffe de longues feuilles fines, cramoisies, plus sombres au pied."""
    blades = [(3, 6, -1), (5, 3, 0), (7, 5, 1), (8, 2, 0), (10, 4, 1), (12, 7, 1), (6, 8, -1)]
    for x0, top, lean in blades:
        for y in range(top, 16):
            x = x0 + (lean if y < top + 3 else 0)
            t = (y - top) / max(1, 15 - top)
            img[y][max(0, min(15, x))] = rgb("#B0283A") if t < 0.35 else rgb("#7A1A2C") if t < 0.75 else rgb("#4A1020")


def fern(img):
    """Fougère d'ombre : des frondes noir-violet, leurs folioles en arêtes de poisson, des pointes rouge sombre."""
    for stem_x, top, side in ((7, 1, 0), (4, 5, -1), (11, 4, 1)):
        for y in range(top, 16):
            x = stem_x + (side * (15 - y) // 6)
            img[y][max(0, min(15, x))] = rgb("#2A1236")
            if (y - top) % 2 == 0 and y < 14:
                for dx in (-2, -1, 1, 2):
                    xx = x + dx
                    if 0 <= xx < 16:
                        img[y][xx] = rgb("#3E1C4E") if abs(dx) == 1 else rgb("#552A66")
        img[top][max(0, min(15, stem_x))] = rgb("#8A2A3A")


def bulb(img):
    """Bulbe-lanterne : une tige courbe et sombre, et au bout trois bulbes orange qui luisent."""
    for y in range(6, 16):
        img[y][7 + (1 if y < 10 else 0)] = rgb("#2E1A26")
    for y in range(9, 16):
        img[y][4 + (y - 9) // 3] = rgb("#2E1A26")
        img[y][12 - (y - 9) // 3] = rgb("#2E1A26")
    for cx, cy in ((8, 4), (4, 8), (12, 8)):
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                if abs(dx) + abs(dy) < 2:
                    img[cy + dy][cx + dx] = rgb("#FFB24A") if dx == 0 and dy == 0 else rgb("#E8742A")
        img[cy - 1][cx - 1] = rgb("#FFE2A0")


def red_star_texture(size=160):
    """La naine rouge, vue de Proxima b : un disque rouge-orange, plus clair au cœur, dans un grand halo qui s'éteint."""
    img = [[CLEAR for _ in range(size)] for _ in range(size)]
    for y in range(size):
        for x in range(size):
            r = math.hypot(x - size / 2 + 0.5, y - size / 2 + 0.5) / (size / 2)
            if r <= 0.42:
                t = r / 0.42
                red = 255
                green = int(200 - 120 * t)
                blue = int(140 - 110 * t)
                img[y][x] = (red, green, blue, 255)
            elif r <= 1.0:
                glow = (1.0 - (r - 0.42) / 0.58) ** 2.4
                img[y][x] = (255, 90, 50, int(220 * glow))
    return img


def proxima_b_texture(size=160):
    """Proxima b vue d'orbite : la face de jour cuite (à droite), la face de nuit gelée, et le crépuscule sombre entre elles."""
    rnd = random.Random(307)
    cell = 2
    n = size // cell
    img = [[CLEAR for _ in range(size)] for _ in range(size)]
    for j in range(n):
        for i in range(n):
            x = (i + 0.5) / n * 2.0 - 1.0
            y = (j + 0.5) / n * 2.0 - 1.0
            r2 = x * x + y * y
            if r2 > 1.0:
                continue
            wobble = 0.08 * math.sin(y * 7.0) + rnd.uniform(-0.04, 0.04)
            side = x + wobble
            if side > 0.25:
                color = rgb("#B0683E") if rnd.random() > 0.2 else rgb("#8A4A30")
            elif side < -0.25:
                color = rgb("#2A3550") if rnd.random() > 0.25 else rgb("#5A7090")
            else:
                color = rgb("#3A2236") if rnd.random() > 0.3 else rgb("#5A2E3A")
            # La lumière de l'étoile vient de la droite : la face de nuit reste dans l'ombre.
            light = 0.18 + 0.82 * max(0.0, min(1.0, (x + 0.35) / 1.1)) ** 1.5
            shade = (0.55 + 0.45 * math.sqrt(1.0 - r2)) * light
            c = tuple(min(255, int(channel * shade)) for channel in color[:3]) + (255,)
            for dy in range(cell):
                for dx in range(cell):
                    img[j * cell + dy][i * cell + dx] = c
    return img


def main():
    blocks = {
        "proxima_rock": rock_texture(), "proxima_regolith": regolith_texture(), "proxima_moss_top": moss_top_texture(),
        "proxima_moss_side": moss_side_texture(), "proxima_ash": ash_texture(), "proxima_frost": frost_texture(),
        "red_sedge": plant(sedge), "umbra_fern": plant(fern), "lantern_bulb": plant(bulb),
    }
    for name, img in blocks.items():
        write_png(os.path.join(BLOCK_DIR, name + ".png"), img)
    write_png(os.path.join(PARTICLE_DIR, "proxima_star.png"), red_star_texture())
    write_png(os.path.join(PARTICLE_DIR, "proxima_b.png"), proxima_b_texture())
    print("Proxima b : %d textures de blocs, la naine rouge et la planète écrites." % len(blocks))


if __name__ == "__main__":
    main()
