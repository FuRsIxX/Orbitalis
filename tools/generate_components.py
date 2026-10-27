#!/usr/bin/env python3
"""
Génère les icônes des composants de fabrication d'Orbitalis : des pièces qui ne servent qu'à fabriquer les blocs et
les objets du mod, pour que leurs recettes parlent d'électronique, de mécanique et d'espace plutôt que de blocs du
jeu ordinaire.

- carte électronique, processeur de vol, émetteur radio, centrale inertielle, capteur optique, écran ;
- cellule photovoltaïque, cellule de batterie, lampe à diodes, résistance chauffante, moteur électrique ;
- vitre renforcée, caillebotis d'acier, couverture isolante, tuile de bouclier thermique, filtre à CO2 ;
- et les pièces de fusée qu'ouvrent certaines technologies : booster à poudre, moteur hypergolique, moteur à vide,
  réservoir en titane, grand réservoir d'acier inoxydable.

Chaque icône est dessinée au pixel, dans le style des objets du jeu : un contour sombre, une lumière venue d'en haut
à gauche, des couleurs franches.

Usage : python tools/generate_components.py
"""
import os

from generate_textures import ITEM_DIR, SIZE, TRANSPARENT, rgb, write_png


def icon(rows, palette):
    """Une icône d'après son dessin en caractères (16 lignes de 16) et sa palette ; « . » est transparent."""
    assert len(rows) == SIZE, len(rows)
    out = []
    for row in rows:
        # Une ligne trop courte est complétée de transparent, une trop longue rognée.
        row = (row + "." * SIZE)[:SIZE]
        out.append([rgb(palette[c]) if c in palette else TRANSPARENT for c in row])
    return out


DARK = "#22262B"

COMPONENTS = {
    # Une carte verte, ses pistes dorées, sa puce noire, les contacts du connecteur en bas.
    "circuit_board": ([
        "................",
        "................",
        "..oooooooooooo..",
        "..olggggggggGo..",
        "..ogyyyygkkkGo..",
        "..oggggygkhkGo..",
        "..ogkkgygkkkGo..",
        "..ogkkgyyyyyGo..",
        "..oggggggggyGo..",
        "..ogyyyggkkyGo..",
        "..ogggyggkkyGo..",
        "..oGGGGGGGGGGo..",
        "..oyoyoyoyoyoo..",
        "..oooooooooooo..",
        "................",
        "................",
    ], {"o": "#14361F", "g": "#2E8B57", "G": "#22683F", "l": "#4FB27A", "y": "#E8C25A", "k": DARK, "h": "#4A515A"}),
    # Une puce carrée, ses broches dorées sur les quatre côtés, son cœur qui luit.
    "flight_processor": ([
        "................",
        "................",
        "....y.y.y.y.....",
        "...oooooooooo...",
        "..yokkkkkkkkoy..",
        "...okssssssko...",
        "..yoksccccskoy..",
        "...okscbbcsko...",
        "..yokscbbcskoy..",
        "...okscccccko...",
        "..yokssssssko...",
        "...okkkkkkkkoy..",
        "...oooooooooo...",
        "....y.y.y.y.....",
        "................",
        "................",
    ], {"o": "#101214", "k": "#2A2F35", "s": "#3A4048", "c": "#8C949E", "b": "#39B5C9", "y": "#E8C25A"}),
    # Un boîtier blanc, ses voyants, et son antenne à pointe jaune.
    "radio_transmitter": ([
        "................",
        ".........oo.....",
        ".........oho....",
        "..........o.....",
        "..........o.....",
        "..........o.....",
        "..........o.....",
        "...oooooooooo...",
        "...owwwwwwwwso..",
        "...owrrwwbbwso..",
        "...owwwwwwwwso..",
        "...owkkkkkkwso..",
        "...owwwwwwwwso..",
        "...ossssssssso..",
        "...ooooooooooo..",
        "................",
    ], {"o": "#3A4048", "w": "#E6E9EC", "s": "#B9C0C7", "r": "#E0453A", "b": "#3F76E4", "k": "#8C949E", "h": "#E8C02A"}),
    # Une cellule bleue quadrillée, dans son cadre d'aluminium.
    "solar_cell": ([
        "................",
        "................",
        "..oooooooooooo..",
        "..offffffffffo..",
        "..ofbbblbbbbfo..",
        "..ofbhblbbbbfo..",
        "..ofbbblbbbbfo..",
        "..ofllllllllfo..",
        "..ofbbblbbbbfo..",
        "..ofbbblbbbbfo..",
        "..ofbbblbbbbfo..",
        "..ofllllllllfo..",
        "..offffffffffo..",
        "..oooooooooooo..",
        "................",
        "................",
    ], {"o": "#3A4048", "f": "#C9CED4", "b": "#1F4FA8", "l": "#6E9BF0", "h": "#9FC4FF"}),
    # Un moteur vu de profil : sa carcasse d'acier, ses bobinages de cuivre, son arbre et ses pattes.
    "electric_motor": ([
        "................",
        "................",
        "................",
        "...oooooooo.....",
        "..osssssssso....",
        "..ocCcCcCcCo....",
        "..ocCcCcCcCoooo.",
        "..ocCcCcCcCohhho",
        "..ocCcCcCcCoooo.",
        "..ocCcCcCcCo....",
        "..oSSSSSSSSo....",
        "...oooooooo.....",
        "....o....o......",
        "...ooo..ooo.....",
        "................",
        "................",
    ], {"o": "#2A2E33", "s": "#AEB6BF", "S": "#6B747F", "c": "#B5632A", "C": "#E8873A", "h": "#C9CED4"}),
    # Un caillebotis : des barreaux d'acier croisés.
    "steel_grating": ([
        "................",
        ".oooooooooooooo.",
        ".ohhhhhhhhhhhho.",
        ".oho.ho.ho.ho.o.".replace(".o.", "..."),
        ".oh..h..h..h..o.",
        ".oh..h..h..h..o.",
        ".ohhhhhhhhhhhho.",
        ".oh..h..h..h..o.",
        ".oh..h..h..h..o.",
        ".oh..h..h..h..o.",
        ".ohhhhhhhhhhhho.",
        ".oh..h..h..h..o.",
        ".oh..h..h..h..o.",
        ".ossssssssssssо.".replace("о", "o"),
        ".oooooooooooooo.",
        "................",
    ], {"o": "#3A4048", "h": "#AEB6BF", "s": "#6B747F"}),
    # Une couverture multicouche : une feuille dorée, matelassée en losanges, repliée sur elle-même.
    "insulation_blanket": ([
        "................",
        "................",
        "..oooooooooooo..",
        "..oGgggGgggGgo..",
        "..ogGgGgGgGgGo..",
        "..oggGgggGgggo..",
        "..ogGgGgGgGgGo..",
        "..oGgggGgggGgo..",
        "..ossssssssssо..".replace("о", "o"),
        "..oSSSSSSSSSSo..",
        "..ossssssssssо..".replace("о", "o"),
        "..oSSSSSSSSSSo..",
        "..oooooooooooo..",
        "................",
        "................",
        "................",
    ], {"o": "#5A4512", "g": "#D9A93A", "G": "#F2CC5E", "s": "#C9CED4", "S": "#9AA3AE"}),
    # Une résistance en spirale, rouge de chaleur, tendue entre deux bornes d'acier.
    "heating_element": ([
        "................",
        "................",
        "................",
        "................",
        "..oo........oo..",
        ".osso......osso.",
        ".oss.r.r.r.osso.",
        ".osyRyRyRyRysso.".replace("RysS", "Ryss"),
        ".ossr.r.r.r.sso.".replace("r.r.r.r.s", ".r.r.r.r"),
        ".osso......osso.",
        "..oo........oo..",
        "..oo........oo..",
        "................",
        "................",
        "................",
        "................",
    ], {"o": "#2A2E33", "s": "#AEB6BF", "r": "#B5442A", "R": "#FF8A3D", "y": "#FFD27A"}),
    # La centrale inertielle : un boîtier sombre, son cadran de gyroscope et l'aiguille rouge.
    "navigation_unit": ([
        "................",
        "................",
        "..oooooooooooo..",
        "..okkkkkkkkkko..",
        "..okkccccccko...".ljust(16, "."),
        "..okcbbbbbbcko..",
        "..okcbbrbbbcko..",
        "..okcbbbrbbcko..",
        "..okcbbbbrbcko..",
        "..okcbbbbbbcko..",
        "..okkccccccko...".ljust(16, "."),
        "..okkkkkkkkkko..",
        "..oyokkkkkkoyo..",
        "..oooooooooooo..",
        "................",
        "................",
    ], {"o": "#101214", "k": "#3A4048", "c": "#8C949E", "b": "#173E5C", "r": "#E0453A", "y": "#E8C25A"}),
    # Un objectif : son fût gris et sa grosse lentille bleutée, avec son reflet.
    "optical_sensor": ([
        "................",
        "................",
        ".....oooooo.....",
        "....oggggggo....",
        "...ogollllogo...",
        "..ogolhllllogo..",
        "..ogollhlllogo..",
        "..ogollllllogo..",
        "..ogolllllpogo..",
        "..ogollllppogo..",
        "...ogoppppogo...",
        "....oggggggo....",
        ".....oooooo.....",
        "................",
        "................",
        "................",
    ], {"o": "#2A2E33", "g": "#8C949E", "l": "#5B7FD0", "p": "#2F4A8C", "h": "#DDEBFF"}),
    # Une vitre épaisse, cerclée d'aluminium, boulonnée aux quatre coins, son reflet en biais.
    "reinforced_glass": ([
        "................",
        ".oooooooooooooo.",
        ".obaaaaaaaaaabo.",
        ".oaggggggggggao.",
        ".oagwggggggggao.",
        ".oaggwgggggggao.",
        ".oagggwggggggao.",
        ".oaggggggggggao.",
        ".oaggggggggwgao.",
        ".oagggggggwggao.",
        ".oaggggggggggao.",
        ".oaggggggggggao.",
        ".oaggggggggggao.",
        ".obaaaaaaaaaabo.",
        ".oooooooooooooo.",
        "................",
    ], {"o": "#3A4048", "a": "#C9CED4", "b": "#6B747F", "g": "#9FD3F2", "w": "#EAF7FF"}),
    # Un écran plat : son cadre sombre, sa dalle allumée, une courbe et deux lignes de texte.
    "display_screen": ([
        "................",
        "................",
        ".oooooooooooooo.",
        ".okkkkkkkkkkkko.",
        ".oksssssssssskо.".replace("о", "o"),
        ".oksccssssssskо.".replace("о", "o"),
        ".okssccssssssko.",
        ".oksssscssccsko.",
        ".okssssscc ssko.".replace(" ", "s"),
        ".oksssssssssskо.".replace("о", "o"),
        ".okslllslllssko.",
        ".okkkkkkkkkkkko.",
        ".oooooooooooooo.",
        "......okko......",
        ".....oooooo.....",
        "................",
    ], {"o": "#101214", "k": "#2A2F35", "s": "#0E2A38", "c": "#39B5C9", "l": "#5BE08A"}),
    # Une lampe : son boîtier blanc et son diffuseur qui luit.
    "led_lamp": ([
        "................",
        "................",
        "................",
        "....oooooooo....",
        "...owwwwwwwwo...",
        "...owyyyyyywo...",
        "...owyhhhhywo...",
        "...owyhhhhywo...",
        "...owyyyyyywo...",
        "...owwwwwwwwo...",
        "...ossssssssо...".replace("о", "o"),
        "....oooooooo....",
        "......oo........",
        "......oo........",
        "................",
        "................",
    ], {"o": "#3A4048", "w": "#E6E9EC", "s": "#B9C0C7", "y": "#FFE7A6", "h": "#FFFBEA"}),
    # Deux tuiles noires de bouclier thermique, l'une devant l'autre.
    "heat_shield_tile": ([
        "................",
        "................",
        ".......ooooooo..",
        ".......olllllo..",
        ".......olkkkko..",
        "..oooooookkkko..",
        "..olllllokkkko..",
        "..olkkkkookkko..".replace("ookk", "okkk"),
        "..olkkkkkoooo...".ljust(16, "."),
        "..olkkkkkko.....",
        "..olkkkkkko.....",
        "..oddddddo......",
        "..oooooooo......",
        "................",
        "................",
        "................",
    ], {"o": "#08090A", "l": "#4A4F55", "k": "#1E2124", "d": "#141618"}),
    # Une cellule de batterie cylindrique : sa gaine bleue, sa borne d'acier, le signe « + ».
    "battery_cell": ([
        "................",
        "......oooo......",
        ".....ohhhho.....",
        "....oooooooo....",
        "....obbbBbbo....",
        "....obwbBbbo....",
        "....owwwBbbo....",
        "....obwbBbbo....",
        "....obbbBbbo....",
        "....obbbBbbo....",
        "....obbbBbbo....",
        "....obbbBbbo....",
        "....osssssso....",
        "....oooooooo....",
        "................",
        "................",
    ], {"o": "#14243F", "b": "#3F76E4", "B": "#2F5DB8", "w": "#F2F4F7", "h": "#C9CED4", "s": "#8C949E"}),
    # Une cartouche de filtre : un cylindre blanc, sa bande verte, son couvercle percé.
    "co2_filter": ([
        "................",
        "....oooooooo....",
        "...osksksksks o.".replace(" o.", "o.."),
        "...oooooooooo...",
        "...owwwwwwwso...",
        "...owwwwwwwso...",
        "...ogggggggGo...",
        "...ogwgwggggGo..".replace("ggggGo..", "gggGo..."),
        "...ogggggggGo...",
        "...owwwwwwwso...",
        "...owwwwwwwso...",
        "...owwwwwwwso...",
        "...osssssssso...",
        "....oooooooo....",
        "................",
        "................",
    ], {"o": "#3A4048", "w": "#E6E9EC", "s": "#B9C0C7", "k": "#5A626C", "g": "#5BC27A", "G": "#3E9A5C"}),
}


# Pièces de fusée ajoutées avec les technologies : dans le style des moteurs et des réservoirs déjà dessinés.
PARTS = {
    # Booster à poudre : un long cylindre blanc, ses deux bandes rouges, sa jupe et sa tuyère sombres.
    "solid_booster": ([
        "................",
        ".......oo.......",
        "......owso......",
        ".....owwsso.....",
        ".....owwsso.....",
        ".....orrrro.....",
        ".....owwsso.....",
        ".....owwsso.....",
        ".....owwsso.....",
        ".....owwsso.....",
        ".....orrrro.....",
        ".....owwsso.....",
        ".....okkkko.....",
        "....okkkkkko....",
        "....oooooooo....",
        "................",
    ], {"o": "#1E2226", "w": "#EEF0F2", "s": "#B9C0C7", "r": "#C8402F", "k": "#3A4048"}),
    # Moteur hypergolique : ses deux sphères d'ergols dorées, sa bande jaune, une petite tuyère.
    "hypergolic_engine": ([
        "................",
        "....oo....oo....",
        "...oggo..oggo...",
        "...oGgo..oGgo...",
        "....oo....oo....",
        ".....oooooo.....",
        ".....osssso.....",
        ".....oyyyyo.....",
        "......osso......",
        "......osso......",
        ".....osssso.....",
        "....osssSsso....",
        "...ossssSssso...",
        "...oooooooooo...",
        "................",
        "................",
    ], {"o": "#1E2226", "g": "#D9A93A", "G": "#F2CC5E", "s": "#8C949E", "S": "#B9C0C7", "y": "#E8C02A"}),
    # Moteur à vide : un corps fin, sa bande bleue, et une tuyère démesurée.
    "vacuum_engine": ([
        "................",
        "......oooo......",
        "......obbo......",
        "......osso......",
        ".....osssso.....",
        ".....osssso.....",
        "......osso......",
        ".....okkKko.....",
        ".....okkKko.....",
        "....okkkKkko....",
        "....okkkKkko....",
        "...okkkkKkkko...",
        "..okkkkkKkkkko..",
        ".okkkkkkKkkkkko.",
        ".oooooooooooooo.",
        "................",
    ], {"o": "#1E2226", "b": "#4FA0DC", "s": "#9AA3AE", "k": "#4A515A", "K": "#6B747F"}),
    # Réservoir en titane : la forme des autres réservoirs, dans le gris bleuté du titane.
    "titanium_tank": ([
        "................",
        "......oooo......",
        ".....oslmmo.....",
        "....osllmmdo....",
        "....osllmmdo....",
        "....odddddddo...",
        "....osllmmdo....",
        "....osllmmdo....",
        "....osllmmdo....",
        "....osllmmdo....",
        "....odddddddo...",
        "....osllmmdo....",
        ".....osmmdo.....",
        "......oooo......",
        "................",
        "................",
    ], {"o": "#1E2226", "s": "#6E7A8C", "l": "#B8C2D4", "m": "#8C96A8", "d": "#5A6475"}),
    # Grand réservoir d'acier inoxydable : large, brillant, ses cordons de soudure.
    "stainless_tank": ([
        "................",
        ".....oooooo.....",
        "....oslhmmmo....",
        "...osllhmmmdo...",
        "...osllhmmmdo...",
        "...oddddddddo...",
        "...osllhmmmdo...",
        "...osllhmmmdo...",
        "...oddddddddo...",
        "...osllhmmmdo...",
        "...osllhmmmdo...",
        "...oddddddddo...",
        "...osllhmmmdo...",
        "....osllmmdo....",
        ".....oooooo.....",
        "................",
    ], {"o": "#1E2226", "s": "#8C949E", "l": "#D9DEE3", "h": "#F4F6F8", "m": "#B4BCC4", "d": "#7F8790"}),
}

# Missions robotiques : deux sondes (la martienne porte son bouclier) et deux atterrisseurs (le martien, son bouclier).
PROBE_ROWS = [
    "................",
    ".......oo.......",
    ".......ow.......",
    "......oddo......",
    "......oddo......",
    ".......oo.......",
    "oooo..oooo..oooo",
    "obbbooggGgoobbbo",
    "obBbooggGgoobBbo",
    "obbbooggGgoobbbo",
    "oooo..oggGo.oooo",
    "......oooo......",
    "......hhhh......",
    ".....hhhhhh.....",
    "................",
    "................",
]
LANDER_ROWS = [
    "................",
    ".......ww.......",
    "......owwo......",
    ".......oo.......",
    "....oooooooo....",
    "....ofgGggfo....",
    "....ofgGggfo....",
    "....ofgGggfo....",
    "....oooooooo....",
    "...of......fo...",
    "..of........fo..",
    ".of..........fo.",
    "oo............oo",
    "hhhh........hhhh",
    "................",
    "................",
]
PROBE_PAL = {"o": "#1E2226", "w": "#E9EBED", "d": "#B9C0C7", "b": "#1F3F8F", "B": "#3F76E4", "g": "#D9A93A", "G": "#F2CC5E"}
LANDER_PAL = {"o": "#1E2226", "w": "#E9EBED", "f": "#9AA3AE", "g": "#D9A93A", "G": "#F2CC5E"}
PARTS["lunar_probe"] = (PROBE_ROWS, PROBE_PAL)
PARTS["mars_probe"] = (PROBE_ROWS, {**PROBE_PAL, "h": "#B5442A"})
PARTS["lunar_robotic_lander"] = (LANDER_ROWS, {**LANDER_PAL, "h": "#6B747F"})
PARTS["mars_robotic_lander"] = (LANDER_ROWS, {**LANDER_PAL, "h": "#B5442A"})

# Rover : de profil, l'avant à gauche. Ses roues rondes en treillis doré et leur moyeu, ses garde-boue gris bleuté,
# son plancher blanc et son caisson avant, la caméra, la parabole sur son mât, le pupitre, le siège, la palette dorée
# et son panneau solaire.
PARTS["rover"] = ([
    "................",
    ".dDDDDDd........",
    "....o...........",
    "....o...........",
    "....o......s....",
    "....o.kkk..s....",
    ".xl.o..k...sbbbb",
    ".wwwww.k.sssyyyy",
    ".wwwwwwwwwwwyyyy",
    "ffffffkkkkffffff",
    ".eeee......eeee.",
    "egGGge....egGGge",
    "eGhhGe....eGhhGe",
    "eGhhGe....eGhhGe",
    "egGGge....egGGge",
    ".eeee......eeee.",
], {"d": "#B9C0C7", "D": "#E9EBED", "o": "#9AA3AE", "k": "#2A2F35", "x": "#2A2F35", "l": "#6E9BF0", "s": "#5A6E86",
    "b": "#1F3F8F", "w": "#E6E9EC", "y": "#D9A93A", "f": "#8E9EB2", "e": "#8C949E", "g": "#C9A64A", "G": "#8E7432",
    "h": "#4A515A"})
# Kit de base planétaire : le dôme pressurisé qu'il déploie, son hublot, sa bande orange, son sas et son antenne,
# posé sur le régolithe.
PARTS["base_kit"] = ([
    "................",
    "................",
    "........w.......",
    "........o.......",
    ".....oooooo.....",
    "....owwwwwwo....",
    "...owwwwwwwso...",
    "..owlLwwwwwwso..",
    "..owllwwwwwwso..",
    ".owwwwwwwwwwso..",
    ".oyyyyyyyyyysooo",
    ".owwwwwwwwwwsodo",
    ".owwwwwwwwwwsodo",
    "gggggggggggggggg",
    ".GgGggGgggGggGg.",
    "................",
], {"o": "#1E2226", "w": "#E9EBED", "s": "#B9C0C7", "l": "#3F76E4", "L": "#9FC4FF", "y": "#E8872A",
    "d": "#5A6068", "g": "#8E8E8A", "G": "#6E6E6A"})

# Retour d'échantillons : l'atterrisseur et, au-dessus, sa capsule de retour au bouclier brun.
SAMPLE_RETURN_ROWS = [
    "......hhhh......",
    ".....owwwwo.....",
    "......oooo......",
    ".......oo.......",
    "....oooooooo....",
    "....ofgGggfo....",
    "....ofgGggfo....",
    "....ofgGggfo....",
    "....oooooooo....",
    "...of..ee..fo...",
    "..of...ee...fo..",
    ".of..........fo.",
    "oo............oo",
    "ssss........ssss",
    "................",
    "................",
]
PARTS["lunar_sample_return"] = (SAMPLE_RETURN_ROWS, {**LANDER_PAL, "h": "#6B4A2E", "e": "#5A6068", "s": "#6B747F"})
PARTS["mars_sample_return"] = (SAMPLE_RETURN_ROWS, {**LANDER_PAL, "h": "#6B4A2E", "e": "#5A6068", "s": "#B5442A"})
# Conteneur à échantillons : une boîte de titane à hublot ; pleine, on y voit la roche de l'astre.
CONTAINER_ROWS = [
    "................",
    "................",
    "................",
    "....oooooooo....",
    "...ommmmmmmmo...",
    "...oooooooooo...",
    "...ossssssslo...",
    "...osoggggslo...",
    "...osgrRrgslo...",
    "...osgrrrgslo...",
    "...osoggggslo...",
    "...ossssssslo...",
    "...ooooooooooo..",
    "................",
    "................",
    "................",
]
CONTAINER_PAL = {"o": "#1E2226", "m": "#D9DEE3", "s": "#A8B0B9", "l": "#7F8790", "g": "#5A6068"}
PARTS["sample_container"] = (CONTAINER_ROWS, {**CONTAINER_PAL, "r": "#9FC4FF", "R": "#E9F2FF"})
PARTS["lunar_samples"] = (CONTAINER_ROWS, {**CONTAINER_PAL, "r": "#8E8E8A", "R": "#C9C9C2"})
PARTS["mars_samples"] = (CONTAINER_ROWS, {**CONTAINER_PAL, "r": "#A2462B", "R": "#D9774E"})
# Station lunaire : un module central, ses deux ailes solaires et son antenne tournée vers la Terre.
PARTS["lunar_gateway"] = ([
    "................",
    "........ww......",
    ".......oo.......",
    ".......of.......",
    "bbbb..oooo..bbbb",
    "bBbb..owwo..bBbb",
    "bbbbooowwooobbbb",
    "bBbb.owlLwo.bBbb",
    "bbbbooowwooobbbb",
    "bBbb..owwo..bBbb",
    "bbbb..owwo..bbbb",
    "......osso......",
    "......oggo......",
    ".......oo.......",
    "................",
    "................",
], {"o": "#1E2226", "w": "#E9EBED", "f": "#9AA3AE", "s": "#B9C0C7", "l": "#3F76E4", "L": "#9FC4FF",
    "b": "#1F3F8F", "B": "#3F76E4", "g": "#D9A93A"})

# Propulseur ionique : son processeur, son corps enveloppé d'or, sa grille d'accélération et la lueur bleue du xénon.
PARTS["ion_thruster"] = ([
    "................",
    "......oooo......",
    "......okko......",
    ".....oooooo.....",
    "....oggGgggo....",
    "....oggGgggo....",
    "....oggGgggo....",
    "...oooooooooo...",
    "...ossssSssso...",
    "...osxsxsxsxo...",
    "...ossssSssso...",
    "...oooooooooo...",
    "....obbBBbbo....",
    ".....obBBbo.....",
    "......obbo......",
    "................",
], {"o": "#1E2226", "k": "#3A4048", "g": "#D9A93A", "G": "#F2CC5E", "s": "#8C949E", "S": "#B9C0C7",
    "x": "#4A515A", "b": "#3F76E4", "B": "#9FC4FF"})
# Voile solaire : un losange de film argenté, tendu sur deux mâts en croix ; ses quadrants accrochent la lumière
# chacun à sa façon.
PARTS["solar_sail"] = ([
    ".......oo.......",
    "......moom......",
    ".....mLoolm.....",
    "....mLLoollm....",
    "...mLLLoolllm...",
    "..mLLLLoollllm..",
    ".mLLLLLoolllllm.",
    "oooooookkooooooo",
    "oooooookkooooooo",
    ".mlllllooLLLLLm.",
    "..mllllooLLLLm..",
    "...mlllooLLLm...",
    "....mllooLLm....",
    ".....mlooLm.....",
    "......moom......",
    ".......oo.......",
], {"o": "#4A515A", "k": "#1E2226", "L": "#E9EEF3", "l": "#B9C8D8", "m": "#8C949E"})

# Moteur nucléaire thermique : le tambour du réacteur et son trèfle jaune, sa grande tuyère de cuivre.
PARTS["nuclear_thermal_engine"] = ([
    "................",
    "....oooooooo....",
    "....osssssso....",
    "...okkkkkkkko...",
    "...okkyyyykko...",
    "...okkykkykko...",
    "...okkkyykkko...",
    "...okkkkkkkko...",
    "....oooooooo....",
    ".....occcco.....",
    ".....occcco.....",
    "....occCccco....",
    "...occcCcccco...",
    "..occccCccccco..",
    "..oooooooooooo..",
    "................",
], {"o": "#1E2226", "s": "#B9C0C7", "k": "#3A4048", "y": "#E8C02A", "c": "#B8733A", "C": "#E09A5A"})
# Cœur de réacteur : une cuve d'acier, ses barreaux de combustible qui luisent du bleu de Tcherenkov.
PARTS["reactor_core"] = ([
    "................",
    "....oooooooo....",
    "...ossssssslo...",
    "...osbsbsbslo...",
    "...osBsBsBslo...",
    "...osbsbsbslo...",
    "...osBsBsBslo...",
    "...osbsbsbslo...",
    "...osBsBsBslo...",
    "...osbsbsbslo...",
    "...ossssssslo...",
    "...oyyyyyyyyo...",
    "...ossssssslo...",
    "....oooooooo....",
    "................",
    "................",
], {"o": "#1E2226", "s": "#8C949E", "l": "#5A6068", "b": "#3F76E4", "B": "#9FC4FF", "y": "#E8C02A"})

# Sondes planétaires : les orbiteurs reprennent la sonde, à la couleur de leur astre ; les sondes de descente sont une
# capsule dorée sous son bouclier, son parachute replié au sommet.
ENTRY_ROWS = [
    "................",
    "......oooo......",
    ".....owwwwo.....",
    "......oddo......",
    ".....oggggo.....",
    "....oggGggo.....",
    "...ogggGgggo....",
    "..oooooooooooo..",
    "..ohhhhhhhhhho..",
    "...ohhhhhhhho...",
    ".....ohhhho.....",
    "......oooo......",
    "................",
    "................",
    "................",
    "................",
]
ENTRY_PAL = {"o": "#1E2226", "w": "#E9EBED", "d": "#9AA3AE", "g": "#D9A93A", "G": "#F2CC5E"}
# Les orbiteurs des planètes lointaines : une grande antenne parabolique tournée vers la Terre, comme Juno ou Cassini.
DISH_ROWS = [
    "................",
    "...oooooooooo...",
    "..owwwwwwwwwwo..",
    "...oooooooooo...",
    "......oddo......",
    ".......oo.......",
] + PROBE_ROWS[6:]
PARTS["venus_orbiter"] = (PROBE_ROWS, {**PROBE_PAL, "h": "#E8C27A"})
PARTS["mercury_orbiter"] = (PROBE_ROWS, {**PROBE_PAL, "h": "#9A8F86"})
PARTS["jupiter_orbiter"] = (DISH_ROWS, {**PROBE_PAL, "h": "#C98B5A"})
PARTS["saturn_orbiter"] = (DISH_ROWS, {**PROBE_PAL, "h": "#E3CF8E"})
PARTS["europa_orbiter"] = (DISH_ROWS, {**PROBE_PAL, "h": "#B9D3E8"})
PARTS["venus_lander"] = (ENTRY_ROWS, {**ENTRY_PAL, "h": "#7A4A2A"})
PARTS["jupiter_entry_probe"] = (ENTRY_ROWS, {**ENTRY_PAL, "h": "#5A3A22"})
PARTS["titan_entry_probe"] = (ENTRY_ROWS, {**ENTRY_PAL, "h": "#8A6A3A"})

def main():
    for name, (rows, palette) in list(COMPONENTS.items()) + list(PARTS.items()):
        write_png(os.path.join(ITEM_DIR, name + ".png"), icon(rows, palette))
    print("composants et pièces : %d icônes" % (len(COMPONENTS) + len(PARTS)))


if __name__ == "__main__":
    main()
