#!/usr/bin/env python3
"""
Génère ce qu'il faut voir de Kepler-452b, la « cousine de la Terre », dans Orbitalis.

Kepler-452b tourne autour d'une étoile semblable au Soleil, un peu plus vieille et plus brillante. C'est une
super-Terre : une fois et demie plus pesante, aux reliefs bas, couverte de mers et de plaines. Sous une lumière plus
dorée que celle du Soleil, sa végétation est bronze et ambre, et elle pousse ras, écrasée par la pesanteur.

- la roche de Kepler-452b, un basalte brun-gris ; son sol, une terre sombre ;
- le gazon bronze des plaines (le dessus d'un bloc de sol) ;
- la flore : la fougère bronze, étalée au ras du sol, et la touffe d'ambre ;
- la planète vue d'orbite (une image de particule).

Usage : python tools/generate_kepler.py
"""
import math
import os
import random

from generate_textures import BLOCK_DIR, ROOT, blotchy, rgb, write_png

PARTICLE_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "orbitalis", "textures", "particle")
CLEAR = (0, 0, 0, 0)


def rock_texture():
    """Roche de Kepler-452b : un basalte brun-gris, tassé, aux grains plus clairs."""
    img = blotchy(501, [rgb("#5A4E44"), rgb("#4E433A"), rgb("#665A4E"), rgb("#43392F")], [5, 3, 2, 1], passes=2)
    for x, y in ((3, 2), (12, 6), (7, 11), (14, 13), (1, 8)):
        img[y][x] = rgb("#8A7C6C")
    return img


def soil_texture():
    """Sol de Kepler-452b : une terre brun sombre, des cailloux et des racines plus claires."""
    img = blotchy(502, [rgb("#4A3626"), rgb("#3E2D20"), rgb("#56402E"), rgb("#33251A")], [5, 3, 2, 1], passes=2)
    for x, y in ((2, 4), (10, 2), (6, 9), (13, 12)):
        img[y][x] = rgb("#7A6450")
    for x, y in ((8, 6), (9, 6), (4, 13)):
        img[y][x] = rgb("#2A1E14")
    return img


def turf_top_texture():
    """Gazon bronze : des brins ras, olive et bronze, piqués de points d'ambre."""
    img = blotchy(503, [rgb("#6E6A2E"), rgb("#5E5A26"), rgb("#7E7636"), rgb("#4E4A20")], [5, 3, 2, 2], passes=2)
    rnd = random.Random(504)
    for _ in range(12):
        img[rnd.randrange(16)][rnd.randrange(16)] = rgb("#9A8A3E")
    for _ in range(5):
        img[rnd.randrange(16)][rnd.randrange(16)] = rgb("#C8862E")
    return img


def turf_side_texture():
    """Flanc d'un bloc de gazon : le sol, et le gazon qui déborde du dessus en franges courtes."""
    img = soil_texture()
    top = turf_top_texture()
    depth = [2, 3, 2, 2, 3, 4, 3, 2, 2, 3, 2, 3, 4, 3, 2, 2]
    for x in range(16):
        for y in range(depth[x]):
            img[y][x] = top[y][x]
    return img


def plant(draw):
    img = [[CLEAR for _ in range(16)] for _ in range(16)]
    draw(img)
    return img


def bronze_fern(img):
    """Fougère bronze : des frondes larges et basses, étalées par la pesanteur, bronze au cœur, dorées au bout."""
    for x0, lean in ((7, 0), (3, -1), (11, 1), (5, -1), (9, 1)):
        top = 6 + abs(lean) * 3
        for y in range(top, 16):
            x = x0 + lean * (15 - y) // 3
            img[y][max(0, min(15, x))] = rgb("#7A5A26")
            if (y - top) % 2 == 0 and y < 15:
                for dx in (-1, 1):
                    xx = x + dx
                    if 0 <= xx < 16:
                        img[y][xx] = rgb("#A07A32") if y - top < 3 else rgb("#8A6A2C")
        img[top][max(0, min(15, x0))] = rgb("#D8A84A")


def amber_tuft(img):
    """Touffe d'ambre : une touffe basse de brins sombres, et au-dessus des grappes de graines ambrées."""
    for x, top in ((4, 9), (6, 7), (8, 8), (10, 7), (12, 10)):
        for y in range(top, 16):
            img[y][x] = rgb("#5E4A22") if y > 12 else rgb("#6E5A28")
        for dy in (0, 1):
            img[top - 1 - dy][x] = rgb("#E0962E") if dy == 0 else rgb("#C87A22")
        if x + 1 < 16:
            img[top - 1][x + 1] = rgb("#F0B04A")


def planet_texture(size=160):
    """Kepler-452b vue d'orbite : des mers bleu sombre, des continents bronze et olive, des nuages blancs, des glaces."""
    rnd = random.Random(505)
    cell = 2
    n = size // cell
    img = [[CLEAR for _ in range(size)] for _ in range(size)]
    lands = [(rnd.uniform(-0.8, 0.8), rnd.uniform(-0.7, 0.7), rnd.uniform(0.15, 0.32)) for _ in range(9)]
    clouds = [(rnd.uniform(-0.9, 0.9), rnd.uniform(-0.8, 0.8), rnd.uniform(0.05, 0.12)) for _ in range(18)]
    for j in range(n):
        for i in range(n):
            x = (i + 0.5) / n * 2.0 - 1.0
            y = (j + 0.5) / n * 2.0 - 1.0
            r2 = x * x + y * y
            if r2 > 1.0:
                continue
            color = rgb("#1E3E66") if rnd.random() > 0.15 else rgb("#24507E")
            for lx, ly, lr in lands:
                if math.hypot(x - lx, y - ly) < lr:
                    color = rgb("#6E6A2E") if rnd.random() > 0.3 else rgb("#7A5A2E")
            for cx, cy, cr in clouds:
                if math.hypot(x - cx, (y - cy) * 2.0) < cr:
                    color = rgb("#EEF0F2")
            if abs(y) > 0.86:
                color = rgb("#E4EAF0")
            light = 0.1 + 0.9 * max(0.0, min(1.0, (x + 0.4) / 1.1)) ** 1.3
            shade = (0.55 + 0.45 * math.sqrt(1.0 - r2)) * light
            c = tuple(min(255, int(channel * shade)) for channel in color[:3]) + (255,)
            for dy in range(cell):
                for dx in range(cell):
                    img[j * cell + dy][i * cell + dx] = c
    return img


def main():
    blocks = {
        "kepler_rock": rock_texture(), "kepler_soil": soil_texture(), "kepler_turf_top": turf_top_texture(),
        "kepler_turf_side": turf_side_texture(), "bronze_fern": plant(bronze_fern), "amber_tuft": plant(amber_tuft),
    }
    for name, img in blocks.items():
        write_png(os.path.join(BLOCK_DIR, name + ".png"), img)
    write_png(os.path.join(PARTICLE_DIR, "kepler_452b.png"), planet_texture())
    print("Kepler-452b : %d textures de blocs et la planète écrites." % len(blocks))


if __name__ == "__main__":
    main()
