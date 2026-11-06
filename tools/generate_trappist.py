#!/usr/bin/env python3
"""
Génère ce qu'il faut voir de TRAPPIST-1 e, la quatrième des sept planètes de la petite étoile froide TRAPPIST-1, dans
Orbitalis.

TRAPPIST-1 e est un monde-océan : une mer presque sans fin, d'où sortent des îles de basalte bordées de sable pâle. Sa
lumière est rouge sombre ; dans son ciel, trois de ses sœurs se montrent grandes comme des lunes.

- la roche de TRAPPIST-1 e, un basalte gris-bleu ; son sable, pâle et lilas ;
- le tapis d'algues des îles, vert-bleu (le dessus d'un bloc de sable) ;
- la flore : l'herbe des marées (sous l'eau, en rubans pourpres), le roseau-flèche des rivages et la coupe-lueur, qui luit ;
- l'étoile dans le ciel, la planète vue d'orbite, et ses trois sœurs, d, f et g (des images de particule).

Usage : python tools/generate_trappist.py
"""
import math
import os
import random

from generate_textures import BLOCK_DIR, ROOT, blotchy, rgb, write_png

PARTICLE_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "orbitalis", "textures", "particle")
CLEAR = (0, 0, 0, 0)


def rock_texture():
    """Roche de TRAPPIST-1 e : un basalte gris-bleu, des veines vert sombre, quelques grains clairs."""
    img = blotchy(401, [rgb("#3A4650"), rgb("#33404A"), rgb("#44525C"), rgb("#2C3840")], [5, 3, 2, 1], passes=2)
    for x, y in ((2, 3), (10, 7), (5, 12), (13, 14), (8, 1)):
        img[y][x] = rgb("#8A9AA4")
    for x, y in ((6, 6), (7, 6), (12, 11), (3, 9)):
        img[y][x] = rgb("#2A4A44")
    return img


def sand_texture():
    """Sable des rivages : un sable pâle teinté de lilas, des grains sombres et des éclats de coquillage."""
    img = blotchy(402, [rgb("#B8A6A0"), rgb("#AA9890"), rgb("#C4B4AC"), rgb("#9C8A84")], [5, 3, 2, 1], passes=2)
    for x, y in ((3, 2), (11, 5), (6, 9), (14, 12), (1, 14)):
        img[y][x] = rgb("#6E5C5A")
    for x, y in ((8, 3), (2, 11), (12, 14)):
        img[y][x] = rgb("#E6DCD6")
    return img


def moss_top_texture():
    """Tapis d'algues des îles : un vert-bleu sombre, des taches plus claires, et çà et là un liseré pourpre."""
    img = blotchy(403, [rgb("#2E6A5E"), rgb("#275A50"), rgb("#387A6C"), rgb("#1F4A42")], [5, 3, 2, 2], passes=2)
    rnd = random.Random(404)
    for _ in range(12):
        img[rnd.randrange(16)][rnd.randrange(16)] = rgb("#4E9A7A")
    for _ in range(5):
        img[rnd.randrange(16)][rnd.randrange(16)] = rgb("#7A3A5E")
    return img


def moss_side_texture():
    """Flanc d'un bloc d'îlot : le sable, et le tapis d'algues qui déborde du dessus en franges inégales."""
    img = sand_texture()
    top = moss_top_texture()
    depth = [2, 3, 4, 3, 2, 2, 3, 4, 3, 2, 3, 4, 5, 3, 2, 3]
    for x in range(16):
        for y in range(depth[x]):
            img[y][x] = top[y][x]
    return img


def plant(draw):
    img = [[CLEAR for _ in range(16)] for _ in range(16)]
    draw(img)
    return img


def tide_grass(img):
    """Herbe des marées : des rubans pourpres qui ondulent dans le courant, plus sombres au pied."""
    for x0, top, phase in ((2, 3, 0.0), (5, 1, 1.4), (8, 4, 2.6), (11, 2, 0.8), (14, 5, 2.0)):
        for y in range(top, 16):
            x = x0 + int(round(math.sin(y * 0.7 + phase)))
            t = (y - top) / max(1, 15 - top)
            color = rgb("#E878AE") if t < 0.3 else rgb("#C8508E") if t < 0.7 else rgb("#943670")
            img[y][max(0, min(15, x))] = color


def spire_reed(img):
    """Roseau-flèche : des tiges droites vert-bleu, et à leur pointe un épi pâle."""
    for x, top in ((3, 4), (6, 1), (8, 3), (10, 0), (13, 5)):
        for y in range(top, 16):
            img[y][x] = rgb("#3E8A9A") if (y - top) < 5 else rgb("#2A6A7A") if y < 13 else rgb("#1E4E5A")
        for y in range(top, min(16, top + 3)):
            img[y][x] = rgb("#D8ECE4") if y == top else rgb("#A8C8BE")
    for x, y in ((4, 9), (7, 8), (11, 10), (12, 11)):
        img[y][x] = rgb("#3E8A9A")


def gleam_cup(img):
    """Coupe-lueur : de courtes tiges sombres, et au bout des coupes turquoise au cœur clair, qui luisent."""
    for x, top in ((4, 8), (8, 5), (12, 9)):
        for y in range(top, 16):
            img[y][x] = rgb("#1E4A46")
        for dx in (-2, -1, 0, 1, 2):
            img[top - 1][x + dx] = rgb("#2EB8B0") if abs(dx) == 2 else rgb("#52D6CC")
        for dx in (-1, 0, 1):
            img[top][x + dx] = rgb("#2EB8B0")
        img[top - 1][x] = rgb("#C8FFF8")
    img[12][6] = rgb("#2E6A5E")
    img[13][10] = rgb("#2E6A5E")


def star_texture(size=160):
    """TRAPPIST-1, vue de sa planète e : un disque rouge sombre, plus orangé au cœur, dans un halo bas."""
    img = [[CLEAR for _ in range(size)] for _ in range(size)]
    for y in range(size):
        for x in range(size):
            r = math.hypot(x - size / 2 + 0.5, y - size / 2 + 0.5) / (size / 2)
            if r <= 0.5:
                t = r / 0.5
                img[y][x] = (255, int(160 - 100 * t), int(90 - 70 * t), 255)
            elif r <= 1.0:
                glow = (1.0 - (r - 0.5) / 0.5) ** 2.8
                img[y][x] = (230, 60, 40, int(200 * glow))
    return img


def sphere(size, palette, seed, lit=1.0, bands=False, cell=2):
    """Une planète en disque, éclairée par la droite : une palette de couleurs posées par taches, ombrée vers la gauche."""
    rnd = random.Random(seed)
    n = size // cell
    img = [[CLEAR for _ in range(size)] for _ in range(size)]
    spots = [(rnd.uniform(-1, 1), rnd.uniform(-1, 1), rnd.uniform(0.15, 0.4), rnd.randrange(len(palette))) for _ in range(14)]
    for j in range(n):
        for i in range(n):
            x = (i + 0.5) / n * 2.0 - 1.0
            y = (j + 0.5) / n * 2.0 - 1.0
            r2 = x * x + y * y
            if r2 > 1.0:
                continue
            index = 0
            if bands:
                index = int((y + 1.0) * 3.5 + 0.4 * math.sin(x * 5.0)) % len(palette)
            for sx, sy, sr, si in spots:
                if (x - sx) ** 2 + (y - sy) ** 2 < sr * sr:
                    index = si
            color = palette[index]
            if rnd.random() < 0.12:
                color = palette[(index + 1) % len(palette)]
            light = 0.08 + 0.92 * max(0.0, min(1.0, (x + 0.3) / 1.0)) ** 1.3 * lit
            shade = (0.6 + 0.4 * math.sqrt(1.0 - r2)) * light
            c = tuple(min(255, int(channel * shade)) for channel in color[:3]) + (255,)
            for dy in range(cell):
                for dx in range(cell):
                    img[j * cell + dy][i * cell + dx] = c
    return img


def planet_texture(size=160):
    """TRAPPIST-1 e vue d'orbite : un océan bleu sombre, des îles vert-bleu bordées de sable, des calottes de glace."""
    rnd = random.Random(405)
    cell = 2
    n = size // cell
    img = [[CLEAR for _ in range(size)] for _ in range(size)]
    islands = [(rnd.uniform(-0.8, 0.8), rnd.uniform(-0.7, 0.7), rnd.uniform(0.05, 0.14)) for _ in range(16)]
    for j in range(n):
        for i in range(n):
            x = (i + 0.5) / n * 2.0 - 1.0
            y = (j + 0.5) / n * 2.0 - 1.0
            r2 = x * x + y * y
            if r2 > 1.0:
                continue
            color = rgb("#1A3E62") if rnd.random() > 0.2 else rgb("#22507A")
            for ix, iy, ir in islands:
                d = math.hypot(x - ix, y - iy)
                if d < ir:
                    color = rgb("#2E6A5E") if d < ir * 0.7 else rgb("#B8A6A0")
            if abs(y) > 0.82:
                color = rgb("#DCE6EE")
            light = 0.12 + 0.88 * max(0.0, min(1.0, (x + 0.35) / 1.1)) ** 1.4
            shade = (0.55 + 0.45 * math.sqrt(1.0 - r2)) * light
            c = tuple(min(255, int(channel * shade)) for channel in color[:3]) + (255,)
            for dy in range(cell):
                for dx in range(cell):
                    img[j * cell + dy][i * cell + dx] = c
    return img


def main():
    blocks = {
        "trappist_rock": rock_texture(), "trappist_sand": sand_texture(), "trappist_moss_top": moss_top_texture(),
        "trappist_moss_side": moss_side_texture(), "tide_grass": plant(tide_grass), "spire_reed": plant(spire_reed),
        "gleam_cup": plant(gleam_cup),
    }
    for name, img in blocks.items():
        write_png(os.path.join(BLOCK_DIR, name + ".png"), img)
    particles = {
        "trappist_star": star_texture(),
        "trappist_1e": planet_texture(),
        # Ses trois sœurs, dans son ciel : d, rocheuse et chaude ; f, glacée ; g, plus lointaine, voilée de bandes.
        "trappist_d": sphere(64, [rgb("#A8784E"), rgb("#8A5E3E"), rgb("#C49A6A")], 406),
        "trappist_f": sphere(64, [rgb("#C8DCE8"), rgb("#A4C0D4"), rgb("#E8F0F4")], 407),
        "trappist_g": sphere(64, [rgb("#B4BCC8"), rgb("#96A0B0"), rgb("#D0D6DE")], 408, bands=True),
    }
    for name, img in particles.items():
        write_png(os.path.join(PARTICLE_DIR, name + ".png"), img)
    print("TRAPPIST-1 e : %d textures de blocs et %d images du ciel écrites." % (len(blocks), len(particles)))


if __name__ == "__main__":
    main()
