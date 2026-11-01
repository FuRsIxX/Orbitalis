# Orbitalis — prochaine version

## Nouveautés
- **Pompe à eau** : posée au-dessus d'une étendue d'au moins 8 blocs d'eau (une nappe de 4×2), elle pompe un seau par seconde pour 10 FE par tick, sans jamais la vider. C'est une vraie pompe, sur son socle, avec son moteur : le câble se branche sur son boîtier électrique, à l'arrière, et le tuyau sur sa bride de sortie, à l'avant.
- Chaque machine a sa propre livrée : la couleur de sa coque et les motifs de ses flancs, de son dessus et de sa grille changent d'une machine à l'autre (ailettes du générateur, bandes de danger de la fonderie, conduites de l'électrolyseur, givre du liquéfacteur...). La forme reste la même pour toutes.
- Cassés, les réservoirs gardent leur contenu : l'objet l'emporte et le rend une fois reposé. Un bloc de réservoir modulable emporte sa part.
- Le réservoir modulable ravitaille aussi la fusée, collé à un bloc de ravitaillement ou relié par un tuyau.
- En multijoueur, tous les joueurs à moins de 64 blocs du pas de tir au décollage reçoivent les points de recherche du vol, pour leur agence.
- Cassées, toutes les machines gardent leur énergie et leur contenu, objets comme fluides (générateurs, fonderie, laminoir, machines à fluides, pompe, panneaux solaires, batteries, réacteur à fusion...). L'info-bulle de l'objet les affiche. Rien ne tombe plus au sol à part : tout part avec l'objet, qui tombe toujours, même soufflé par une explosion. En mode créatif, une machine qui contient quelque chose tombe quand même, pour ne rien perdre.
- **Pas de tir posés sur le sol** : le tablier ne repose plus sur des appuis. Le kit pose la table directement sur le sol, le déflecteur de flammes s'enterre d'un bloc sous la table, et une seule marche suffit pour monter sur le tablier. La tour part du même sol. Les pas de tir déjà bâtis gardent leur forme, tablier surélevé sur ses appuis, même si on change leur plan avec un kit.
- **Purge des machines à fluides** : quand une sortie est pleine, la machine s'arrête, même si l'autre a de la place. Sous chaque jauge de sortie, un bouton règle sa purge. Un clic gauche passe à la purge à 50 % (le surplus au-delà de la moitié est rejeté à l'air), puis à la purge totale (tout est rejeté). Un clic droit l'arrête.
- L'ordinateur de conception garde le brouillon en cours : on le retrouve en revenant, et en multijoueur tous les joueurs voient le même, réglage après réglage.
- Panneaux solaires : 80 FE par tick en orbite (au lieu de 40) et 20 au sol de jour (au lieu de 8). Quatre panneaux raccordés en carré produisent chacun un quart de plus.

## Équilibrage
- Recherche du début de partie : chaque vol réussi rapporte ses données (3 points en vol atmosphérique, 6 dans l'espace, 10 en orbite et au-delà). Les primes des premiers vols passent à 20 et 50 points, la charge utile de fusée-sonde à 10. Une fusée-sonde rapporte donc 16 points par vol au lieu de 5.
- Nouveaux jalons pour les premières pièces fabriquées : carte électronique, moteur à poudre, charge utile de fusée-sonde, kit de pas de tir, moteurs à ergols, émetteur radio, centrale de navigation.

## Corrections
- L'eau et les autres fluides des jauges des machines, du réservoir cryogénique et des réservoirs modulables ont la texture de l'eau de Minecraft, teintée, comme dans le monde : ils n'ont plus une couleur à part.
- Le bandeau d'état des machines ne fait plus chevaucher l'état et la consommation. S'ils ne tiennent pas ensemble, l'état reste entier et le reste s'affiche au survol.
- Les points de recherche s'actualisent aussitôt chez tous les joueurs, sans se reconnecter : les gains des satellites, sondes, échantillons ou coéquipiers n'arrivaient parfois qu'à la connexion suivante.
- Les grands meubles (établi d'ingénieur, ordinateur de conception, pupitre de lancement, station de poursuite) se cassent par n'importe laquelle de leurs cases, aussi vite que par leur bloc principal, avec les fissures visibles.
- Neuf pièces de fusée (propulseurs d'appoint, moteurs hypergolique, à vide, ionique, à fusion et nucléaire, voile solaire, réservoirs en titane et inox) mettaient une douzaine de secondes à casser, même à la pioche : elles se cassent maintenant aussi vite que les autres.
- Recettes en double corrigées : le fuselage (il prend un lingot d'acier au centre), le terminal de station (sur une coque de station), et les sondes de Vénus, Saturne et Europe, qui avaient la même recette que d'autres.
- Les blocs de ravitaillement des pas de tir 9×9 et 11×11 ne trouvaient pas leur table : ceux du fond de la tour étaient hors de portée. L'hélium-3 passe aussi par les tuyaux à gaz.
- Le déluge se voit de loin, depuis le pupitre, et pendant ses essais même sans fusée sur la table.
- Le bras ombilical et la passerelle se retirent au rythme du son des vérins, sur tous les pas de tir.
- La cabine de l'ascenseur, posée avant que la tour soit finie, tourne son entrée vers le joueur qui pose le socle.

# Orbitalis — 0.9.2 (bêta)

*Minecraft 26.2 · NeoForge 26.2.0.88 · JEI recommandé*

## Recettes
- Les conduits se fabriquent en une ligne de trois :
  - câble électrique : cuivre, redstone, cuivre ;
  - tuyau à gaz : verre, cuivre, verre ;
  - tuyau à liquides : tôle d'acier, verre, tôle d'acier.
- Le verre teinté convient aussi. Chaque recette donne toujours 6 conduits.

## Interfaces
- En multijoueur, si un joueur casse un pupitre, un ordinateur ou un terminal pendant qu'un autre s'en sert, l'écran de ce dernier se ferme.
- La touche d'inventaire (E par défaut) ferme les écrans du mod, comme Échap, sauf quand on tape un nom.

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

# Orbitalis — next version

## New
- **Water pump**: placed above a body of at least 8 water blocks (a 4×2 pool), it pumps a bucket per second for 10 FE per tick and never drains it. It's a real pump, on its skid, with its motor: the cable goes into its electrical box at the back, and the pipe onto its outlet flange at the front.
- Every machine has its own livery: the colour of its casing and the patterns on its sides, top and grille differ from one machine to the next (the generator's fins, the foundry's hazard stripes, the electrolyzer's pipes, the liquefier's frost...). The shape stays the same for all.
- Broken tanks keep their contents: the item carries them and gives them back once placed again. A modular tank block takes its share.
- The modular tank fuels rockets too, placed against a fuel port or connected to one by a pipe.
- In multiplayer, every player within 64 blocks of the pad at liftoff earns the flight's research points for their agency.
- Every machine keeps its power and its contents when broken, items and fluids alike (generators, arc foundry, rolling mill, fluid machines, pump, solar panels, batteries, fusion reactor...). The item's tooltip shows them. Nothing spills on the ground anymore: everything goes with the item, which always drops, even when blown up. In creative mode, a machine that holds something still drops, so nothing is lost.
- **Launch pads rest on the ground**: the deck no longer sits on supports. The kit places the table right on the ground, the flame deflector goes one block into the ground under the table, and a single step leads up onto the deck. The tower stands on the same ground. Pads already built keep their shape, with the deck raised on its supports, even if you change their plan with a kit.
- **Fluid machine purge**: when one output is full, the machine stops, even if the other has room. Under each output gauge, a button sets its purge. Left-click switches to the 50% purge (anything above half is vented into the air), then to the full purge (everything is vented). Right-click stops it.
- The design computer remembers the draft you were working on: it's still there when you come back, and in multiplayer everyone sees the same one, change by change.
- Solar panels: 80 FE per tick in orbit (up from 40) and 20 on the ground in daylight (up from 8). Four panels joined in a square each produce a quarter more.

## Balance
- Early research: every successful flight now brings back its data (3 points for an atmospheric flight, 6 for space, 10 for orbit and beyond). First-flight bonuses go up to 20 and 50 points, and the sounding rocket payload to 10. A sounding rocket now earns 16 points per flight instead of 5.
- New milestones for the first parts you craft: circuit board, solid motor, sounding rocket payload, pad kit, liquid-fuel engines, radio transmitter, navigation unit.

## Fixes
- Water and other fluids in machine gauges, the cryogenic tank and modular tanks now use Minecraft's water texture, tinted, like in the world, instead of a flat colour of their own.
- The machine status line no longer overlaps the status and the power use. If they don't fit together, the status stays whole and the rest shows on hover.
- Research points update right away for every player, with no need to reconnect: points from satellites, probes, samples or teammates sometimes only showed up on the next login.
- Large furniture (engineer's workbench, design computer, launch console, tracking station) can be broken from any of its blocks, as fast as from its main block, with the cracks showing.
- Nine rocket parts (side boosters, hypergolic, vacuum, ion, fusion and nuclear engines, solar sail, titanium and stainless tanks) took about twelve seconds to break, even with a pickaxe: they now break as fast as the others.
- Fixed duplicate recipes: the fuselage (it now takes a steel ingot in the middle), the station terminal (on a station hull), and the Venus, Saturn and Europa probes, which shared their recipe with others.
- Fuel ports on 9×9 and 11×11 pads couldn't find their table: the ones at the back of the tower were out of range. Helium-3 now goes through gas pipes too.
- The deluge can be seen from afar, from the console, and during its tests even with no rocket on the table.
- The umbilical arm and the gangway retract in time with the hydraulic sound, on every pad.
- An elevator cabin placed before the tower is finished turns its entrance toward the player placing the base.

# Orbitalis — 0.9.2 (beta)

*Minecraft 26.2 · NeoForge 26.2.0.88 · JEI recommended*

## Recipes
- Conduits are now crafted in a single row of three:
  - energy cable: copper, redstone, copper;
  - gas pipe: glass, copper, glass;
  - liquid pipe: steel plate, glass, steel plate.
- Stained glass works too. Each recipe still makes 6 conduits.

## Screens
- In multiplayer, if a player breaks a console, computer or terminal while someone else is using it, that player's screen closes.
- The inventory key (E by default) closes the mod's screens, like Escape, except while you're typing a name.

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
