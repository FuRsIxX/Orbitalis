#!/usr/bin/env python3
"""
Génère les images des combinaisons spatiales d'Orbitalis :

- les textures portées (64x32, la disposition des armures du jeu) des deux combinaisons :
  - l'orange, de lancement et de sortie, inspirée des combinaisons ACES de la navette : tissu orange, anneau de cou
    argenté, raccords bleu et rouge sur la poitrine, écusson de mission, gants et bottes noirs, casque blanc à
    visière claire (le visage se voit au travers) ;
  - la blanche, avancée, inspirée de la xEMU de la NASA : torse rigide blanc, boîtier de commande sur la poitrine,
    gros sac de survie dans le dos, anneaux de rotation gris aux épaules, aux coudes, aux hanches et aux genoux,
    casque à visière claire cerclée d'or et ses deux lampes ;
- les icônes des huit pièces (casque, torse, jambières, bottes) et des cinq modules ;
- les visières, posées devant les yeux quand on porte le casque : à peine un assombrissement des coins, et pour la
  blanche, un léger reflet doré.

Usage : python tools/generate_spacesuits.py
"""
import os
import random

from generate_textures import ITEM_DIR, ROOT, TRANSPARENT, rgb, write_png

TEXTURES = os.path.join(ROOT, "src", "main", "resources", "assets", "orbitalis", "textures")
EQUIPMENT = os.path.join(TEXTURES, "entity", "equipment")


# ---------------------------------------------------------------------------
# Couleurs
# ---------------------------------------------------------------------------
def tone(color, factor):
    r, g, b, a = color
    return (min(255, max(0, round(r * factor))), min(255, max(0, round(g * factor))), min(255, max(0, round(b * factor))), a)


ORANGE = rgb("#EE6E22")
ORANGE_DARK = rgb("#B24E16")
WHITE = rgb("#E9ECEF")
SHELL = rgb("#F4F5F6")
SILVER = rgb("#C9CED4")
STEEL = rgb("#9AA2AB")
GRAPHITE = rgb("#5A6068")
DARK = rgb("#3A4048")
GLOVE = rgb("#2A2D31")
BOOT = rgb("#26282B")
SOLE = rgb("#55595F")
PATCH_BLUE = rgb("#1F4FA8")
FIT_BLUE = rgb("#2F6FD0")
FIT_RED = rgb("#D0453A")
LAMP = rgb("#FFF6C8")
GREEN = rgb("#5BC27A")
AMBER = rgb("#FFC24A")
STATUS = rgb("#4FB3FF")
GOLD = [rgb(c) for c in ("#F2CC5E", "#D9A93A", "#C08F2A", "#A8801F", "#8A6716")]
GOLD_SHINE = rgb("#FFE9A6")

# Éclairage de chaque face : le dessus est le plus clair, le dessous le plus sombre.
LIGHT = {"top": 1.07, "front": 1.0, "right": 0.9, "left": 0.9, "back": 0.93, "bottom": 0.72}


# ---------------------------------------------------------------------------
# Disposition des armures : une boîte (u, v, largeur, hauteur, profondeur) et ses six faces
# ---------------------------------------------------------------------------
HEAD = (0, 0, 8, 8, 8)
BODY = (16, 16, 8, 12, 4)
ARM = (40, 16, 4, 12, 4)
LEG = (0, 16, 4, 12, 4)


def faces(box):
    u, v, w, h, d = box
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


def canvas():
    return [[TRANSPARENT for _ in range(64)] for _ in range(32)]


class Part:
    """Une boîte de l'armure, que l'on peint face par face, en coordonnées locales à chaque face."""

    def __init__(self, img, box, seed):
        self.img = img
        self.faces = faces(box)
        self.rand = random.Random(seed)

    def fill(self, face, color, grain=0.025, gradient=0.06, rows=None):
        """Remplit une face d'un tissu : sa couleur, éclairée selon la face, plus claire en haut, et un grain léger."""
        x0, y0, w, h = self.faces[face]
        for y in range(h):
            if rows is not None and y not in rows:
                continue
            shade = LIGHT[face] * (1.0 + gradient * (0.5 - y / max(1, h - 1)))
            for x in range(w):
                self.img[y0 + y][x0 + x] = tone(color, shade * (1.0 + self.rand.uniform(-grain, grain)))

    def set(self, face, x, y, color, lit=True):
        x0, y0, w, h = self.faces[face]
        if 0 <= x < w and 0 <= y < h:
            self.img[y0 + y][x0 + x] = tone(color, LIGHT[face]) if lit and color[3] else color

    def row(self, face, y, color, x_from=0, x_to=None, lit=True):
        x0, y0, w, h = self.faces[face]
        for x in range(x_from, w if x_to is None else x_to + 1):
            self.set(face, x, y, color, lit)

    def column(self, face, x, color, y_from=0, y_to=None, lit=True):
        x0, y0, w, h = self.faces[face]
        for y in range(y_from, h if y_to is None else y_to + 1):
            self.set(face, x, y, color, lit)

    def sides(self):
        return ("front", "right", "left", "back")

    def ring(self, y, color):
        """Un anneau tout autour de la boîte, à cette hauteur."""
        for face in self.sides():
            self.row(face, y, color)

    def fold(self, face, y, amount=0.86, chance=0.6):
        """Un pli du tissu : une rangée un peu plus sombre, irrégulière."""
        x0, y0, w, h = self.faces[face]
        for x in range(w):
            if self.rand.random() < chance:
                c = self.img[y0 + y][x0 + x]
                if c[3]:
                    self.img[y0 + y][x0 + x] = tone(c, amount)


# ---------------------------------------------------------------------------
# Combinaison orange
# ---------------------------------------------------------------------------
def orange_helmet(img):
    p = Part(img, HEAD, 11)
    for face in ("top", "front", "right", "left", "back", "bottom"):
        p.fill(face, SHELL, grain=0.012, gradient=0.08)
    # La visière, claire : on voit le visage au travers. Un cadre gris, et un seul reflet.
    for y in range(2, 7):
        for x in range(1, 7):
            p.set("front", x, y, TRANSPARENT, lit=False)
    p.row("front", 1, SILVER, 1, 6)
    p.set("front", 1, 2, rgb("#DDF3FF"), lit=False)
    # Elle tourne un peu sur les côtés, autour de son pivot.
    for y in range(3, 6):
        p.set("right", 7, y, TRANSPARENT, lit=False)
        p.set("left", 0, y, TRANSPARENT, lit=False)
    for face in ("right", "left"):
        p.set(face, 3, 4, GRAPHITE)
        p.set(face, 4, 4, GRAPHITE)
        p.set(face, 3, 3, STEEL)
        p.set(face, 4, 3, STEEL)
    p.column("right", 6, SILVER, 2, 6)
    p.column("left", 1, SILVER, 2, 6)
    # L'anneau de cou, et le loquet de la nuque.
    p.ring(7, STEEL)
    p.set("back", 3, 5, GRAPHITE)
    p.set("back", 4, 5, GRAPHITE)
    p.fill("bottom", GRAPHITE, grain=0.0)
    # La bande réfléchissante du sommet, les deux évents de la nuque, le micro sous la visière.
    for y in range(1, 7):
        p.set("top", 3, y, rgb("#D9DEE3"))
        p.set("top", 4, y, rgb("#D9DEE3"))
    p.set("back", 2, 3, STEEL)
    p.set("back", 5, 3, STEEL)
    p.set("front", 3, 7, GRAPHITE)
    p.set("front", 4, 7, GRAPHITE)


def orange_body(img, seed=21):
    p = Part(img, BODY, seed)
    for face in ("top", "front", "right", "left", "back", "bottom"):
        p.fill(face, ORANGE)
    for y in (4, 8):
        p.fold("front", y)
        p.fold("back", y)
    # Anneau de cou argenté, et les épaules.
    p.row("front", 0, SILVER, 2, 5)
    p.row("back", 0, SILVER, 2, 5)
    p.row("top", 1, SILVER, 2, 5)
    p.row("top", 2, SILVER, 2, 5)
    # La fermeture, et ses curseurs.
    p.column("front", 3, ORANGE_DARK, 1, 10)
    for y in (2, 6, 9):
        p.set("front", 3, y, SILVER)
    # Porte-nom et écusson de mission.
    p.row("front", 2, rgb("#E4E6E8"), 0, 1)
    p.set("front", 1, 2, DARK)
    p.set("front", 5, 2, PATCH_BLUE)
    p.set("front", 6, 2, PATCH_BLUE)
    p.set("front", 5, 3, PATCH_BLUE)
    p.set("front", 6, 3, rgb("#F2F2F2"))
    # Raccords d'oxygène et de ventilation : bleu et rouge, dans leur bague.
    p.set("front", 1, 6, FIT_BLUE)
    p.set("front", 2, 6, FIT_RED)
    p.set("front", 1, 7, STEEL)
    p.set("front", 2, 7, STEEL)
    # La taille, plus sombre, et sa ceinture.
    p.row("front", 11, ORANGE_DARK)
    p.row("right", 11, ORANGE_DARK)
    p.row("left", 11, ORANGE_DARK)
    p.row("back", 11, ORANGE_DARK)
    # Dans le dos, le sac du parachute et de la survie, sous ses sangles.
    for y in range(1, 10):
        for x in range(1, 7):
            edge = x in (1, 6) or y in (1, 9)
            p.set("back", x, y, GRAPHITE if edge else STEEL)
    p.row("back", 4, GRAPHITE, 2, 5)
    p.set("back", 3, 7, DARK)
    p.set("back", 4, 7, DARK)
    p.column("back", 0, ORANGE_DARK, 1, 10)
    p.column("back", 7, ORANGE_DARK, 1, 10)
    # Coutures des côtés.
    p.column("right", 2, ORANGE_DARK, 0, 10)
    p.column("left", 1, ORANGE_DARK, 0, 10)
    # Le harnais du parachute : deux sangles graphite le long des flancs du torse, leurs boucles, et la sangle de
    # poitrine qui passe sous la fermeture.
    for x in (0, 7):
        p.column("front", x, GRAPHITE, 1, 10)
        p.set("front", x, 9, SILVER)
    p.row("front", 5, GRAPHITE, 4, 6)
    p.set("front", 4, 5, SILVER)
    # La poche de poitrine, sous l'écusson, et son rabat.
    p.set("front", 5, 4, ORANGE_DARK)
    p.set("front", 6, 4, ORANGE_DARK)
    # Les bandes réfléchissantes des épaules.
    for x in (0, 1, 6, 7):
        p.set("top", x, 1, rgb("#D9DEE3"))
        p.set("top", x, 2, rgb("#D9DEE3"))
    # Le tuyau d'oxygène, qui part des raccords vers le flanc.
    p.row("right", 7, rgb("#4A6E9C"), 0, 1)


def orange_arms(img):
    p = Part(img, ARM, 31)
    for face in ("top", "front", "right", "left", "back", "bottom"):
        p.fill(face, ORANGE)
    p.fold("front", 6)
    p.fold("right", 6)
    p.fold("left", 6)
    # Écusson à l'épaule, du côté extérieur.
    p.set("right", 1, 1, PATCH_BLUE)
    p.set("right", 2, 1, rgb("#F2F2F2"))
    p.set("right", 1, 2, FIT_RED)
    p.set("right", 2, 2, PATCH_BLUE)
    # Poignet argenté, gants noirs.
    p.ring(9, SILVER)
    for y in (10, 11):
        p.ring(y, GLOVE)
    p.set("front", 1, 10, rgb("#3E4247"))
    p.set("front", 2, 10, rgb("#3E4247"))
    p.fill("bottom", GLOVE, grain=0.0)
    # La poche du bras, à l'extérieur, son rabat ; le stylo glissé dedans.
    for y in range(4, 7):
        for x in range(0, 4):
            if y == 4 or x in (0, 3):
                p.set("right", x, y, ORANGE_DARK)
    p.set("right", 2, 3, SILVER)
    # L'aide-mémoire du poignet, et la bande réfléchissante de l'avant-bras.
    p.set("front", 1, 7, rgb("#F2F2F2"))
    p.set("front", 2, 7, rgb("#F2F2F2"))
    p.set("front", 1, 8, DARK)
    p.row("back", 7, rgb("#D9DEE3"))
    # Les jointures du gant.
    p.row("front", 11, rgb("#3E4247"))


def orange_legs(img):
    p = Part(img, LEG, 41)
    for face in ("top", "front", "right", "left", "back", "bottom"):
        p.fill(face, ORANGE)
    # Poche de cuisse, à l'extérieur.
    for y in range(2, 5):
        for x in range(0, 4):
            p.set("right", x, y, ORANGE_DARK if y == 2 or x in (0, 3) else tone(ORANGE, 0.95))
    # Genoux, et plis.
    p.fold("front", 5, 0.82, 0.9)
    p.fold("front", 6, 0.9, 0.5)
    p.fold("back", 6)
    p.fold("front", 10, 0.88, 0.6)
    p.fill("bottom", ORANGE_DARK, grain=0.0)
    # Les genouillères, plus sombres, et la bande réfléchissante sous le genou.
    for x in range(4):
        p.set("front", x, 5, tone(ORANGE_DARK, 1.05))
    p.row("front", 7, rgb("#D9DEE3"))
    p.row("right", 7, rgb("#D9DEE3"))
    # La poche de l'intérieur de la cuisse, et la fermeture de la cheville.
    for y in range(2, 4):
        for x in range(4):
            if y == 2 or x in (0, 3):
                p.set("left", x, y, ORANGE_DARK)
    p.column("back", 2, ORANGE_DARK, 8, 10)


def orange_boots(img):
    """Les bottes de la combinaison orange : noires, leur revers gris, la pointe, la semelle (le reste est transparent)."""
    p = Part(img, LEG, 61)
    for face in ("front", "right", "left", "back"):
        p.fill(face, BOOT, rows=range(8, 12))
    p.ring(8, rgb("#3A3D42"))
    p.ring(11, SOLE)
    p.set("front", 1, 10, rgb("#34373B"))
    p.set("front", 2, 10, rgb("#34373B"))
    p.set("right", 1, 9, rgb("#C9CED4"))
    p.fill("bottom", rgb("#1B1C1E"), grain=0.0)
    # Les lacets, la sangle de la cheville et sa boucle.
    p.set("front", 1, 9, rgb("#55595F"))
    p.set("front", 2, 9, rgb("#55595F"))
    p.set("left", 2, 9, rgb("#C9CED4"))


def orange_waist(img):
    """La taille du pantalon (sous le torse, qui la recouvre) : quelques rangées, et la ceinture."""
    p = Part(img, BODY, 51)
    for face in ("front", "right", "left", "back"):
        p.fill(face, ORANGE, rows=range(7, 12))
    p.ring(7, STEEL)
    p.fill("bottom", ORANGE_DARK)


# ---------------------------------------------------------------------------
# Combinaison blanche, avancée
# ---------------------------------------------------------------------------
def white_helmet(img):
    p = Part(img, HEAD, 12)
    for face in ("top", "front", "right", "left", "back", "bottom"):
        p.fill(face, SHELL, grain=0.01, gradient=0.08)
    # La visière, claire : on voit le visage au travers. Un cerclage doré, un reflet.
    for y in range(2, 7):
        for x in range(1, 7):
            p.set("front", x, y, TRANSPARENT, lit=False)
    p.row("front", 1, GOLD[1], 1, 6)
    p.set("front", 1, 2, rgb("#FFF3C9"), lit=False)
    # Les deux lampes du casque, au-dessus de la visière.
    p.set("front", 0, 1, LAMP, lit=False)
    p.set("front", 7, 1, LAMP, lit=False)
    # La visière tourne sur les côtés, cerclée d'or ; une caméra et sa lampe de chaque côté.
    for y in range(3, 6):
        p.set("right", 7, y, TRANSPARENT, lit=False)
        p.set("left", 0, y, TRANSPARENT, lit=False)
    p.column("right", 6, GOLD[2], 2, 6)
    p.column("left", 1, GOLD[2], 2, 6)
    for face, xs in (("right", (2, 3, 4)), ("left", (3, 4, 5))):
        for x in xs:
            p.set(face, x, 1, DARK)
        p.set(face, xs[0] if face == "right" else xs[-1], 1, LAMP, lit=False)
        p.set(face, 3 if face == "right" else 4, 4, STEEL)
    # Une couture au sommet, l'anneau de cou.
    p.column("top", 3, rgb("#DADDE0"))
    p.ring(7, STEEL)
    p.fill("bottom", GRAPHITE, grain=0.0)
    # La caméra du sommet, les évents de la nuque, la plaque bleue du numéro de combinaison.
    p.set("top", 4, 1, DARK)
    p.set("top", 4, 2, STEEL)
    for x in (2, 5):
        p.set("back", x, 4, STEEL)
        p.set("back", x, 5, GRAPHITE)
    p.set("back", 3, 2, rgb("#3D7BE0"))
    p.set("back", 4, 2, rgb("#3D7BE0"))


def white_body(img):
    p = Part(img, BODY, 22)
    for face in ("top", "front", "right", "left", "back", "bottom"):
        p.fill(face, WHITE, grain=0.015)
    # Anneau de cou du torse rigide, liserés bleus aux épaules.
    p.row("front", 0, STEEL, 1, 6)
    p.row("back", 0, STEEL, 1, 6)
    for x in (0, 7):
        p.set("front", x, 1, rgb("#3D7BE0"))
    # Le boîtier de commande sur la poitrine : écran, voyants, molettes.
    for y in range(4, 8):
        for x in range(2, 6):
            p.set("front", x, y, GRAPHITE if y == 4 else DARK)
    p.set("front", 3, 5, GREEN, lit=False)
    p.set("front", 4, 5, AMBER, lit=False)
    p.set("front", 2, 6, STEEL)
    p.set("front", 5, 6, STEEL)
    # Écusson, et l'anneau de rotation de la taille.
    p.set("front", 6, 2, PATCH_BLUE)
    p.set("front", 6, 3, FIT_RED)
    p.ring(10, STEEL)
    p.ring(11, rgb("#D4D8DC"))
    # Le sac de survie, qui couvre tout le dos : ses panneaux, ses évents, son voyant.
    for y in range(12):
        for x in range(8):
            edge = x in (0, 7) or y in (0, 11)
            p.set("back", x, y, rgb("#B9C0C7") if edge else SHELL)
    p.row("back", 4, rgb("#C9CED4"), 1, 6)
    p.row("back", 8, rgb("#C9CED4"), 1, 6)
    for x in range(2, 6):
        p.set("back", x, 9, GRAPHITE if x % 2 == 0 else DARK)
        p.set("back", x, 10, DARK if x % 2 == 0 else GRAPHITE)
    p.set("back", 6, 1, STATUS, lit=False)
    p.set("back", 1, 1, STEEL)
    # Coutures des côtés.
    p.column("right", 2, rgb("#C9CED4"), 1, 9)
    p.column("left", 1, rgb("#C9CED4"), 1, 9)
    # Les joints du torse rigide, le cadre du boîtier de commande et ses deux raccords sur le côté.
    p.row("front", 3, rgb("#DADDE0"), 1, 6)
    p.row("front", 8, rgb("#DADDE0"), 1, 6)
    p.set("front", 1, 5, rgb("#D4D8DC"))
    p.set("front", 6, 5, rgb("#D4D8DC"))
    p.set("front", 0, 6, FIT_BLUE)
    p.set("front", 0, 7, FIT_RED)
    # Les roulements des épaules, sur le dessus.
    for x in (0, 1, 6, 7):
        p.set("top", x, 1, STEEL)
        p.set("top", x, 2, STEEL)
    # Le sac de survie : sa poignée en haut, l'antenne, l'évent de sublimation et la plaque des consignes.
    p.row("back", 0, GRAPHITE, 2, 5)
    p.set("back", 7, 1, DARK)
    p.set("back", 7, 2, DARK)
    p.set("back", 2, 6, rgb("#F2F2F2"))
    p.set("back", 3, 6, rgb("#F2F2F2"))
    p.set("back", 2, 7, FIT_RED)
    p.set("back", 5, 6, STEEL)
    p.set("back", 5, 7, GRAPHITE)


def white_arms(img):
    p = Part(img, ARM, 32)
    for face in ("top", "front", "right", "left", "back", "bottom"):
        p.fill(face, WHITE, grain=0.015)
    # Anneaux de rotation de l'épaule et du coude, connecteur du poignet.
    p.ring(2, STEEL)
    p.ring(6, STEEL)
    p.ring(9, GRAPHITE)
    # Écusson de l'épaule.
    p.set("right", 1, 0, PATCH_BLUE)
    p.set("right", 2, 0, rgb("#3D7BE0"))
    # Gants clairs, articulations grises.
    for y in (10, 11):
        p.ring(y, rgb("#D4D8DC"))
    p.set("front", 1, 10, STEEL)
    p.set("front", 2, 10, STEEL)
    p.fill("bottom", rgb("#8C949E"), grain=0.0)
    # La bande rouge d'identification au-dessus du coude, le miroir et l'aide-mémoire du poignet.
    p.row("right", 4, FIT_RED)
    p.row("front", 4, FIT_RED)
    p.row("back", 4, FIT_RED)
    p.row("left", 4, FIT_RED)
    p.set("front", 1, 7, SILVER)
    p.set("front", 2, 7, rgb("#DDF3FF"), lit=False)
    p.set("front", 1, 8, rgb("#F2F2F2"))
    p.set("front", 2, 8, rgb("#F2F2F2"))
    # Les jointures du gant.
    p.row("front", 11, STEEL)


def white_legs(img):
    p = Part(img, LEG, 42)
    for face in ("top", "front", "right", "left", "back", "bottom"):
        p.fill(face, WHITE, grain=0.015)
    # Anneaux de rotation de la hanche et du genou ; un liseré bleu à l'extérieur.
    p.ring(0, STEEL)
    p.ring(5, STEEL)
    p.column("right", 2, rgb("#3D7BE0"), 1, 4)
    p.ring(9, STEEL)
    p.fill("bottom", rgb("#C9CED4"), grain=0.0)
    # Les bandes rouges d'identification sur la cuisse, les plis du genou, la poche à outils à l'extérieur.
    for face in ("front", "right", "left", "back"):
        p.row(face, 2, FIT_RED)
    p.fold("front", 6, 0.92, 0.8)
    p.fold("front", 7, 0.95, 0.6)
    for y in range(3, 5):
        for x in range(4):
            if y == 3 or x in (0, 3):
                p.set("right", x, y, rgb("#C9CED4"))


def white_boots(img):
    """Les bottes de la combinaison blanche : coque blanche, anneau de cheville gris, pointe grise, semelle sombre."""
    p = Part(img, LEG, 62)
    for face in ("front", "right", "left", "back"):
        p.fill(face, SHELL, rows=range(8, 12), grain=0.012)
    p.ring(8, rgb("#8C949E"))
    p.ring(11, rgb("#3A3D42"))
    p.set("front", 1, 10, rgb("#B9C0C7"))
    p.set("front", 2, 10, rgb("#B9C0C7"))
    p.set("right", 2, 9, rgb("#3D7BE0"))
    p.fill("bottom", rgb("#2A2D31"), grain=0.0)
    # Les sangles de serrage et leurs boucles, le talon renforcé.
    p.row("front", 9, rgb("#D4D8DC"), 0, 3)
    p.set("front", 3, 9, STEEL)
    p.row("back", 10, rgb("#B9C0C7"))


def white_waist(img):
    p = Part(img, BODY, 52)
    for face in ("front", "right", "left", "back"):
        p.fill(face, WHITE, rows=range(7, 12))
    p.ring(8, STEEL)
    p.fill("bottom", rgb("#C9CED4"))


def write_suit(name, helmet, body, arms, legs, waist, boots):
    upper = canvas()
    helmet(upper)
    body(upper)
    arms(upper)
    boots(upper)
    write_png(os.path.join(EQUIPMENT, "humanoid", name + ".png"), upper)
    lower = canvas()
    waist(lower)
    legs(lower)
    write_png(os.path.join(EQUIPMENT, "humanoid_leggings", name + ".png"), lower)


# ---------------------------------------------------------------------------
# Icônes
# ---------------------------------------------------------------------------
def icon(rows, palette, overrides=()):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    img = [[palette.get(c, TRANSPARENT) if c != "." else TRANSPARENT for c in r] for r in rows]
    for x, y, color in overrides:
        img[y][x] = color
    return img


HELMET = [
    "................",
    ".....oooooo.....",
    "...ooHHWWWWoo...",
    "..oHHWWWWWWWSo..",
    "..oHWVVVVVVWSo..",
    ".oHWVvGGGGGVWSo.",
    ".oHWVGGGGGGVWSo.",
    ".oWWVGGGGGgVWSo.",
    ".oWWVGGGGggVWSo.",
    ".oWWWVVVVVVWWSo.",
    "..oWWWWWWWWWSo..",
    "..ooSWWWWWWSoo..",
    "...oRRRRRRRRo...",
    "...orrrrrrrro...",
    "....oooooooo....",
    "................",
]

TORSO = [
    "................",
    ".....oNNNNo.....",
    "...ooNnnnnNoo...",
    "..oHHOOOOOOOSo..",
    ".oHHOOOOOOOOOSo.",
    ".oHOoOOzOBBoOSo.",
    ".oHOobrzOBWoOSo.",
    ".oHOoOOzOOOoOSo.",
    ".oHOoOOzOOOoOSo.",
    ".oHOoOOzOOOoOSo.",
    ".oKKoOOzOOOoKKo.",
    ".oKKoDDDDDDoKKo.",
    "..oooooooooooo..",
    "................",
    "................",
    "................",
]

ADVANCED_TORSO = [
    "................",
    ".....oNNNNo.....",
    "...ooNnnnnNoo...",
    "..oHbWWWWWWbSo..",
    ".oHHWWWWWWWWWSo.",
    ".oHWoWWWWWBWoWSo",
    ".oHWoWMgaMRoWSo.",
    ".oHWoWMMMMWoWSo.",
    ".oHWoWWWWWWoWSo.",
    ".oRRoWWWWWWoRRo.",
    ".oGGoWWWWWWoGGo.",
    ".oGGoNNNNNNoGGo.",
    "..oooooooooooo..",
    "................",
    "................",
    "................",
]

LEGS = [
    "................",
    "...oooooooooo...",
    "...oNNNNNNNNo...",
    "...oHOOOOOOSo...",
    "...oHOOooOOSo...",
    "...oHOOooOOSo...",
    "...oHPPooPPSo...",
    "...oHOOooOOSo...",
    "...oHkkookkSo...",
    "...oHOOooOOSo...",
    "...oKKKooKKKo...",
    "..oKKKKooKKKKo..",
    "..oLLLLooLLLLo..",
    "..oooooooooooo..",
    "................",
    "................",
]

ADVANCED_LEGS = [
    "................",
    "...oooooooooo...",
    "...oNNNNNNNNo...",
    "...oHWWWWWWSo...",
    "...oHWWooWWSo...",
    "...obWWooWWbo...",
    "...oHWWooWWSo...",
    "...oNNNooNNNo...",
    "...oHWWooWWSo...",
    "...oHWWooWWSo...",
    "...oRRRooRRRo...",
    "..oHWWWooWWWSo..",
    "..oLLLLooLLLLo..",
    "..oooooooooooo..",
    "................",
    "................",
]


def helmet_icon(advanced):
    palette = {
        "o": rgb("#2A2D31"), "H": rgb("#FFFFFF"), "W": SHELL, "S": rgb("#B9C0C7"),
        "V": rgb("#C9CED4"), "r": tone(ORANGE_DARK, 0.85), "R": ORANGE,
        "G": rgb("#A9D6EE"), "g": rgb("#7FB4D3"), "v": rgb("#E9F7FF"),
    }
    overrides = []
    if advanced:
        palette.update({"R": STEEL, "r": GRAPHITE, "V": GOLD[1]})
        overrides = [(4, 3, LAMP), (11, 3, LAMP)]
    return icon(HELMET, palette, overrides)


def torso_icon(advanced):
    if advanced:
        palette = {
            "o": rgb("#2A2D31"), "H": rgb("#FFFFFF"), "W": SHELL, "S": rgb("#B9C0C7"), "N": STEEL, "n": GRAPHITE,
            "b": rgb("#3D7BE0"), "B": PATCH_BLUE, "R": STEEL, "G": rgb("#D4D8DC"), "M": DARK, "g": GREEN, "a": AMBER,
        }
        rows = list(ADVANCED_TORSO)
        rows[5] = ".oHWoWWWWBWoWSo."
        return icon(rows, palette)
    palette = {
        "o": rgb("#2A2D31"), "H": rgb("#FF8F45"), "O": ORANGE, "S": ORANGE_DARK, "N": SILVER, "n": STEEL,
        "z": tone(ORANGE_DARK, 0.9), "B": PATCH_BLUE, "W": rgb("#F2F2F2"), "b": FIT_BLUE, "r": FIT_RED,
        "K": GLOVE, "D": ORANGE_DARK,
    }
    return icon(TORSO, palette)


def legs_icon(advanced):
    """Les jambières seules : le tissu descend jusqu'aux chevilles (les bottes sont une pièce à part)."""
    if advanced:
        palette = {
            "o": rgb("#2A2D31"), "H": rgb("#FFFFFF"), "W": SHELL, "S": rgb("#B9C0C7"), "N": STEEL,
            "b": rgb("#3D7BE0"), "R": rgb("#8C949E"), "L": rgb("#B9C0C7"),
        }
        return icon(ADVANCED_LEGS, palette)
    palette = {
        "o": rgb("#2A2D31"), "H": rgb("#FF8F45"), "O": ORANGE, "S": ORANGE_DARK, "N": STEEL, "P": tone(ORANGE, 0.82),
        "k": tone(ORANGE, 0.86), "K": ORANGE, "L": ORANGE_DARK,
    }
    return icon(LEGS, palette)


BOOTS = [
    "................",
    "................",
    "................",
    "................",
    "...oooo..oooo...",
    "...oCCo..oCCo...",
    "...oHKo..oHKo...",
    "...oHKo..oHKo...",
    "...oHKo..oHKo...",
    "...oHKKooHKKo...",
    "..oHKKKooHKKKo..",
    "..oHKKKooHKKKo..",
    "..oLLLLooLLLLo..",
    "..oooooooooooo..",
    "................",
    "................",
]


def boots_icon(advanced):
    """Les bottes : la coque, son revers, la semelle."""
    if advanced:
        palette = {"o": rgb("#2A2D31"), "H": rgb("#FFFFFF"), "K": SHELL, "C": rgb("#8C949E"), "L": rgb("#3A3D42")}
    else:
        palette = {"o": rgb("#15171A"), "H": rgb("#4A4F55"), "K": BOOT, "C": rgb("#C9CED4"), "L": SOLE}
    return icon(BOOTS, palette)


CARTRIDGE = [
    "................",
    "................",
    "...oooooooooo...",
    "..oAAAAAAAAAAo..",
    "..oMMMMMMMMMMo..",
    "..oMMMMMMMMMMo..",
    "..oMMMMMMMMMMo..",
    "..oMMMMMMMMMMo..",
    "..oMMMMMMMMMMo..",
    "..oMMMMMMMMMMo..",
    "..oMMMMMMMMMMo..",
    "..oMMMMMMMMMMo..",
    "..oMMMMMMMMMMo..",
    "..oddddddddddo..",
    "...oPoPoPoPoo...",
    "................",
]

GLYPHS = {
    "map": [
        "..cccc..",
        ".c....c.",
        "c..aa..c",
        "c.aaaa.c",
        "c..aa..c",
        "c..aa..c",
        ".c....c.",
        "..cccc..",
    ],
    "sensor": [
        "..cccc..",
        ".c....c.",
        "c.....ac",
        "c....a.c",
        "c...a..c",
        "c..w...c",
        ".c....c.",
        "..cccc..",
    ],
    "oxygen_tank": [
        "...ss...",
        "..cccc..",
        ".cwaaac.",
        ".cwaaac.",
        ".cwaaac.",
        ".cwaaac.",
        ".caaaac.",
        "..cccc..",
    ],
    "thrusters": [
        "cccccccc",
        "cwccccwc",
        ".ss..ss.",
        "ssss.sss",
        ".aa..aa.",
        ".aa..aa.",
        "..w..w..",
        "........",
    ],
    "shock_absorbers": [
        "cccccccc",
        "..a..a..",
        ".a.aa.a.",
        "a..aa..a",
        ".a.aa.a.",
        "..a..a..",
        "cccccccc",
        "........",
    ],
}

ACCENTS = {
    "map": "#35C3F0", "sensor": "#FFB23E", "oxygen_tank": "#4FD67A", "thrusters": "#5FB0F5", "shock_absorbers": "#E8C547",
}


def module_icon(name):
    accent = rgb(ACCENTS[name])
    img = icon(CARTRIDGE, {"o": rgb("#15171A"), "A": accent, "M": rgb("#2E3238"), "d": rgb("#1E2125"), "P": rgb("#C08F2A")})
    # Un léger dégradé du boîtier, plus clair en haut à gauche.
    for y in range(4, 13):
        for x in range(3, 13):
            img[y][x] = tone(img[y][x], 1.12 - 0.02 * (y - 4) - 0.01 * (x - 3))
    glyph = GLYPHS[name]
    colors = {"c": tone(accent, 0.75), "a": accent, "w": rgb("#FFFFFF"), "s": rgb("#8C949E")}
    for gy, line in enumerate(glyph):
        for gx, ch in enumerate(line):
            if ch != ".":
                img[4 + gy][4 + gx] = colors[ch]
    return img


# ---------------------------------------------------------------------------
# Visières
# ---------------------------------------------------------------------------
def smooth(a, b, t):
    t = max(0.0, min(1.0, (t - a) / (b - a)))
    return t * t * (3 - 2 * t)


def visor(tint, tint_alpha):
    """Ce que l'on voit du casque, de l'intérieur : les coins assombris par le bord de la visière, et sa teinte."""
    width, height = 320, 180
    img = []
    for y in range(height):
        row = []
        ny = (y + 0.5) / height * 2 - 1
        for x in range(width):
            nx = (x + 0.5) / width * 2 - 1
            d = (abs(nx) ** 4 + abs(ny) ** 4) ** 0.25
            edge = smooth(0.9, 1.18, d)
            a = edge * 0.7 + tint_alpha * (1 - edge)
            # La couleur glisse de la teinte au noir avec le bord : jamais de pixel clair sous un pixel transparent,
            # que le filtrage de l'image étirée changerait en halo.
            r, g, b = (round(c * (1 - edge) + 6 * edge) for c in tint)
            row.append((r, g, b, round(255 * a)))
        img.append(row)
    return img


def main():
    write_suit("spacesuit", orange_helmet, orange_body, orange_arms, orange_legs, orange_waist, orange_boots)
    write_suit("advanced_spacesuit", white_helmet, white_body, white_arms, white_legs, white_waist, white_boots)
    write_png(os.path.join(ITEM_DIR, "space_boots.png"), boots_icon(False))
    write_png(os.path.join(ITEM_DIR, "advanced_space_boots.png"), boots_icon(True))
    write_png(os.path.join(ITEM_DIR, "space_helmet.png"), helmet_icon(False))
    write_png(os.path.join(ITEM_DIR, "spacesuit.png"), torso_icon(False))
    write_png(os.path.join(ITEM_DIR, "space_leggings.png"), legs_icon(False))
    write_png(os.path.join(ITEM_DIR, "advanced_space_helmet.png"), helmet_icon(True))
    write_png(os.path.join(ITEM_DIR, "advanced_spacesuit.png"), torso_icon(True))
    write_png(os.path.join(ITEM_DIR, "advanced_space_leggings.png"), legs_icon(True))
    for name in GLYPHS:
        write_png(os.path.join(ITEM_DIR, "suit_module_" + name + ".png"), module_icon(name))
    write_png(os.path.join(TEXTURES, "misc", "suit_visor.png"), visor((6, 8, 10), 0.0))
    write_png(os.path.join(TEXTURES, "misc", "advanced_suit_visor.png"), visor((6, 8, 10), 0.0))
    print("combinaisons : textures, icônes et visières écrites")


if __name__ == "__main__":
    main()
