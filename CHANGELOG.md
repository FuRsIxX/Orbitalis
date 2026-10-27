# Orbitalis — 0.9.1 (bêta)

*Minecraft 26.2 · NeoForge 26.2.0.88 · JEI recommandé*

## Nouveautés
- **Pas de tir d'essai** : le kit de pas de tir propose maintenant un 3×3 sans tour, sans mâts ni bras. Treize blocs suffisent pour lancer ses premières fusées-sondes, à poudre et sans équipage (20 t et 8 blocs de haut au plus).
- **Pupitre d'essai** : un pupitre d'un seul bloc qui n'affiche que la séquence de lancement (tour de table, allumage, lâcher des attaches), sans carte ni fiche de la fusée. Il marche aussi avec les grands pas de tir.
- Un nouveau chapitre du manuel, « Le pas de tir d'essai ».

## Équilibrage de l'énergie
L'énergie venait trop facilement. Les chiffres s'inspirent maintenant de Mekanism : un générateur fait tourner à peu près une machine, et les machines à gaz sont les plus gourmandes.
- Générateur à combustion : 20 FE/t au lieu de 40 (un charbon donne 32 000 FE).
- Panneau solaire : 8 FE/t au sol au lieu de 15, 40 FE/t en orbite au lieu de 60.
- Réacteur à fusion : 1 200 FE/t au lieu de 2 000.
- Fonderie à arc : 30 FE/t pour l'acier, 50 pour l'aluminium-lithium. Laminoir : 20 FE/t, 40 pour le titane.
- Électrolyseur : 80 FE/t. Liquéfacteur : 50 (oxygène), 80 (hydrogène), 40 (méthane). Raffinerie : 40. Réacteur de Sabatier : 50. Extracteur d'hélium-3 : 80. Chambre de Casimir : 400.
- Réservoir cryogénique : 10 FE/t pour garder le froid, au lieu de 5.
- Les machines acceptent jusqu'à 400 FE/t au lieu de 200.

# Orbitalis — 0.9.0 (bêta)

*Minecraft 26.2 · NeoForge 26.2.0.88 · JEI recommandé*

Première version publique d'Orbitalis : du premier lingot d'acier aux lunes de Jupiter. C'est une bêta : tout ce qui suit est jouable de bout en bout, et vos retours aideront à l'équilibrer avant la 1.0.

## Ce qu'il y a dedans

**Industrie**
- Minerais de titane, d'aluminium et de lithium ; acier et alliages.
- Générateur à combustion, fonderie à arc, laminoir ; câbles d'énergie, tuyaux et câbles de données.
- Électrolyseur, liquéfacteur cryogénique, raffinerie de RP-1, réacteur de Sabatier (méthane), extracteur d'hélium-3 ; réservoirs modulables et cryogéniques.
- Réacteur à fusion (2 000 FE/t) et chambre de Casimir, qui tire la matière exotique du vide.

**Centre spatial et fusées**
- Centres spatiaux à découvrir dans le monde, normaux ou abandonnés.
- Pas de tir en trois tailles (5×5, 9×9, 11×11), bâtis bloc par bloc d'après un plan fantôme : tour de service, bras ombilicaux, déluge, raccords de ravitaillement, console de lancement, écrans de mission.
- Ordinateur de conception avec éditeur d'étages libre, boosters latéraux, chantier de fusée et bras d'assemblage.
- Moteurs kérosène, hydrogène, méthalox, à poudre, nucléaire thermique, ionique, voile solaire et fusion.

**Orbite et vols habités**
- Satellites et sondes, station de poursuite, observatoire.
- Capsules habitées à plusieurs places, rentrée atmosphérique avec secousses, parachutes.
- Combinaisons spatiales et leurs modules ; stations orbitales à modules (habitation, laboratoire, serre, fret, coupole, sas, énergie).

**Exploration**
- La Lune, Mars, Titan (lacs de méthane, drone volant) et Europe (banquise sous radiations, cryobot jusqu'à l'océan).
- Rovers, foreuses ISRU, base planétaire.

**Progression**
- Arbre de recherche, objectifs et jalons ; un guide illustré, scènes animées comprises ; recettes des machines dans JEI.

**Langues**
- Français, anglais, allemand, espagnol, italien, portugais (Brésil), russe, polonais, ukrainien, chinois simplifié, japonais et coréen ; variantes régionales comprises.

## À savoir
- Le voyage interstellaire (chantier naval orbital, vaisseau, moteur à distorsion, Proxima b) est en préparation. Il est désactivé par défaut et arrivera dans une prochaine mise à jour. Pour le découvrir en avance, malgré ses manques : `interstellar = true` dans `config/orbitalis-startup.toml`, puis relancer le jeu (sur un serveur, même réglage pour le serveur et les joueurs).
- Les réglages de jeu (réalisme, pannes, évaporation des ergols, dégâts des accidents…) sont dans la configuration serveur du monde.

---

# Orbitalis — 0.9.1 (beta)

*Minecraft 26.2 · NeoForge 26.2.0.88 · JEI recommended*

## New
- **Test pad**: the launch pad kit now offers a 3×3 pad with no tower, masts or arm. Thirteen blocks are enough to fly your first sounding rockets, solid-fuel and uncrewed (up to 20 t and 8 blocks tall).
- **Test console**: a one-block console that only shows the launch sequence (Go/No-Go poll, ignition, clamp release), with no map and no rocket sheet. It also works with the big pads.
- A new manual chapter, "The Test Pad".

## Energy balance
Power came too easily. The numbers now take after Mekanism: one generator runs about one machine, and the gas machines are the hungriest.
- Combustion generator: 20 FE/t instead of 40 (one coal gives 32,000 FE).
- Solar panel: 8 FE/t on the ground instead of 15, 40 FE/t in orbit instead of 60.
- Fusion reactor: 1,200 FE/t instead of 2,000.
- Arc foundry: 30 FE/t for steel, 50 for aluminum-lithium. Rolling mill: 20 FE/t, 40 for titanium.
- Electrolyzer: 80 FE/t. Liquefier: 50 (oxygen), 80 (hydrogen), 40 (methane). Refinery: 40. Sabatier reactor: 50. Helium-3 extractor: 80. Casimir chamber: 400.
- Cryogenic tank: 10 FE/t to stay cold, instead of 5.
- Machines accept up to 400 FE/t instead of 200.

# Orbitalis — 0.9.0 (beta)

*Minecraft 26.2 · NeoForge 26.2.0.88 · JEI recommended*

The first public release of Orbitalis: from the first steel ingot to the moons of Jupiter. This is a beta: everything below is playable end to end, and your feedback will help balance it before 1.0.

## What's inside

**Industry**
- Titanium, aluminum and lithium ores; steel and alloys.
- Combustion generator, arc foundry, rolling mill; energy cables, pipes and data cables.
- Electrolyzer, cryogenic liquefier, RP-1 refinery, Sabatier reactor (methane), helium-3 extractor; modular and cryogenic tanks.
- Fusion reactor (2,000 FE/t) and Casimir chamber, which draws exotic matter from the vacuum.

**Spaceport and rockets**
- Spaceports to discover in the world, intact or abandoned.
- Launch pads in three sizes (5×5, 9×9, 11×11), built block by block from a ghost plan: service tower, umbilical arms, deluge, fuel ports, launch console, mission displays.
- Design computer with a free stage editor, side boosters, rocket construction site and assembly arm.
- Kerosene, hydrogen, methalox, solid, nuclear thermal, ion, solar sail and fusion engines.

**Orbit and crewed flight**
- Satellites and probes, tracking station, observatory.
- Multi-seat crew capsules, atmospheric reentry with shaking, parachutes.
- Spacesuits and their modules; modular orbital stations (habitation, laboratory, greenhouse, cargo, cupola, airlock, power).

**Exploration**
- The Moon, Mars, Titan (methane lakes, rotor drone) and Europa (ice under radiation, cryobot down to the ocean).
- Rovers, ISRU drills, planetary base.

**Progression**
- Research tree, goals and milestones; an illustrated guide with animated scenes; machine recipes in JEI.

**Languages**
- English, French, German, Spanish, Italian, Portuguese (Brazil), Russian, Polish, Ukrainian, Simplified Chinese, Japanese and Korean, regional variants included.

## Good to know
- Interstellar travel (orbital shipyard, ship, warp drive, Proxima b) is in development. It is disabled by default and will arrive in a future update. To try it early, gaps included: set `interstellar = true` in `config/orbitalis-startup.toml`, then restart the game (on a server, use the same setting for the server and the players).
- Gameplay settings (realism, failures, propellant boil-off, accident damage…) are in the world's server configuration.
