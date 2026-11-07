# Orbitalis — prochaine version

## Changements
- **Saut du vaisseau interstellaire** (avec `interstellar = true`) : un clic droit sur le poste de pilotage ouvre la passerelle. Elle montre où est le vaisseau, le voyant de chacun de ses systèmes, la destination et, en une phrase, ce qui empêche de partir. Le bouton « Sauter » lance un compte à rebours de cinq secondes ; le vaisseau entier quitte alors son berceau, avec ses blocs, leur contenu et tout l'équipage à bord, et réapparaît en orbite de Proxima b. Le saut consomme 4 matières exotiques du cœur de distorsion. Il faut avoir découvert Proxima b à l'observatoire, et l'espace d'arrivée doit être libre. Depuis l'orbite de Proxima b, le même poste ramène le vaisseau à son chantier naval.
- **Anneau du vaisseau qui tourne** : un clic droit sur le moteur de l'anneau le lance, si l'anneau est entier. L'anneau et ses quatre rayons tournent autour du vaisseau, prennent de la vitesse en quatre secondes, puis font un tour en douze secondes. Tant qu'il tourne, l'équipage retrouve la pesanteur dans tout le vaisseau au lieu de flotter. Un second clic droit l'arrête : il ralentit et s'immobilise pile sur un quart de tour, où il retrouve ses blocs. L'anneau en marche ne se casse pas ; il suit le vaisseau quand il saute, et la passerelle indique « en rotation ».
- **Navette de Proxima b** : en orbite de Proxima b, la passerelle a un bouton « Navette ». Une capsule se détache du flanc du vaisseau, le joueur aux commandes ; d'autres équipiers peuvent y monter. La touche de retour prépare le freinage, comme pour rentrer sur Terre, mais vers Proxima b : rentrée avec secousses, parachutes, atterrissage. Au sol, on sort en combinaison ; la navette ne se ramasse pas. Pour repartir, on maintient à bord la touche de lancement : la navette remonte, s'amarre au vaisseau, et l'équipage se retrouve sur la passerelle. Laissée vide en orbite, elle rentre en soute.
- Écrans de bord : hors de la Lune, la montée s'appelle « Décollage » et la descente propulsée « Atterrissage », au lieu de « Départ Lune » et « Alunissage » (Mars, Titan, Europe, Proxima b).
- **TRAPPIST-1 e** (avec `interstellar = true`) : un monde-océan à 39 années-lumière, et son orbite. Une mer presque sans fin, d'où sortent des îles de basalte gris-bleu couvertes d'un tapis d'algues vert-bleu et bordées de sable pâle. Sous une lumière rouge sombre, le ciel est mauve, avec des nuages roses ; la petite étoile TRAPPIST-1 se tient à l'est, et trois de ses autres planètes se montrent grandes comme des lunes. Sa flore : le roseau-flèche des rivages, la coupe-lueur qui luit, et sous l'eau des prairies d'herbe des marées. Pesanteur 0,93 ; l'air ne se respire pas, il faut la combinaison. Premiers pas : +3 000 points de recherche.
- **Destination du vaisseau** : au chantier naval, la passerelle a un sélecteur ‹ › pour choisir où sauter, Proxima b ou TRAPPIST-1 e. Chaque monde doit avoir été découvert à l'observatoire. Le vaisseau garde sa destination ; la passerelle indique en orbite de quel monde il se trouve, et la navette descend sur ce monde-là. Sur TRAPPIST-1 e, elle se pose sur une île ou amerrit, et flotte : on remonte au vaisseau de la même façon. Les consignes de la navette au sol sont plus courtes et tiennent à l'écran.
- **Kepler-452b** (avec `interstellar = true`) : la « cousine de la Terre », à 1 400 années-lumière. Elle se découvre à l'observatoire après TRAPPIST-1 e, puis devient la troisième destination du vaisseau. Une super-Terre de mers et de plaines sous une étoile semblable au Soleil : un ciel bleu voilé d'or, un gazon bronze sur une terre sombre, la fougère bronze et la touffe d'ambre, basses et étalées. La pesanteur y est 1,5 fois celle de la planète : on y saute moins haut et l'on y tombe plus vite. L'air ne se respire pas. Premiers pas : +3 500 points de recherche. La navette s'y pose ou y amerrit.
- **Sondes à distorsion** (avec `interstellar = true`) : vers Proxima b, TRAPPIST-1 e et Kepler-452b. Un atterrisseur robotique martien sur un petit moteur à distorsion chargé de matière exotique : une fusée qui atteint l'orbite de transfert suffit, la sonde fait le reste du voyage seule et se pose sur son exoplanète, à l'est de l'aplomb du pas de tir, ses coordonnées annoncées. Il faut avoir découvert l'exoplanète à l'observatoire, sinon la sonde n'a pas de cible et se perd. Recette : matière exotique, titane, atterrisseur robotique martien et une teinture à la couleur de l'étoile (rouge, cyan ou jaune) ; étude d'ingénierie et technologie « moteur à distorsion ». Première sonde : +800, +1 000 et +1 200 points.
- **Trois galaxies à l'observatoire**, découvertes après Andromède : le Grand Nuage de Magellan (sa barre d'étoiles et le nuage rose de la Tarentule), la galaxie du Triangle (une spirale lâche, floconneuse) et la galaxie du Tourbillon (une grande spirale de face et sa petite compagne). Chacune a sa photo, sa fenêtre d'observation et sa prime.
- **Saut galactique vers Tarentule b** (avec `interstellar = true`) : une fois le Grand Nuage de Magellan découvert, il devient la quatrième destination du vaisseau. Ce saut vide un cœur plein (8 matières exotiques) ; sur place, il faut recharger le cœur avec la matière exotique emportée pour revenir. Tarentule b est une planète de poussière rose et de basalte violet, semée de bouquets de cristaux qui luisent, de fils-lueurs et de voiles roses, sous un air mince et la lumière bleue d'une jeune étoile. Dans son ciel, notre Galaxie, vue enfin de l'extérieur, et le grand nuage rose de la Tarentule. Pesanteur 0,8 ; premiers pas : +5 000 points. Le coût de chaque saut dépend maintenant de sa destination, et le message qui demande une découverte nomme l'astre à observer.

# Orbitalis — 0.10.1 (bêta)

*Minecraft 26.2 · NeoForge 26.2.0.88 · JEI recommandé*

## Changements
- Améliorer un accumulateur garde son énergie : la génération suivante, fabriquée autour de l'ancienne, reprend toute sa charge ; seule la capacité augmente.
- Pas de tir : le plan prévoit maintenant un escalier d'accès de chaque côté du tablier, et ils sont facultatifs. On peut retirer l'un, l'autre ou les deux d'un pas de tir formé sans le défaire : il reste en service, ne les remontre pas en fantôme et ne les repose pas.
- Kit de pas de tir : il contient maintenant le déflecteur de flammes. La recette demande un déflecteur collé au reste (dessous, dessus, ou à côté du papier ou de la table). À la pose, le kit place la table et, dessous, le déflecteur à la place du bloc visé ; une fenêtre de confirmation prévient avant de le remplacer. Un bloc incassable ou un conteneur n'est jamais supprimé : le déflecteur est alors rendu.
- **Ports de pas de tir** (anciens blocs de ravitaillement) : un clic droit à main vide choisit à tout moment ce qu'ils font passer, même posés sur un pas de tir. Deux nouveaux réglages : l'électricité (le seul qui alimente la table) et les données. Leur recette prend une prise de chaque sorte : un tuyau de liquide, un câble électrique, un tuyau de gaz et un câble de données.
- **Liaisons par câble de données** : un pupitre de lancement, un pupitre d'essai ou un mur d'écrans relié par un câble de données au port de données d'un pas de tir suit ce pas de tir, à toute distance. Un pupitre ne se relie plus tout seul à la table la plus proche.
- **Module sans fil** : la seule façon de relier sans câble. Un clic droit sur un pupitre, un écran ou une station de poursuite, puis sur un pas de tir, un pupitre ou un écran : la liaison est faite et le module est consommé. Le clic droit accroupi ne relie plus rien.
- **Four à induction** : il cuit à l'électricité (20 FE par tick) tout ce que cuit un four, minerais compris, deux fois plus vite, sans combustible.
- **Four de fusion** : il ne prend que les minerais et les minerais bruts, et chacun donne le double de ce qu'il donne au four, pour 60 FE par tick et au rythme d'un four. Il se débloque avec la nouvelle technologie « Affinage des minerais » et se fabrique à partir d'un four à induction et d'un haut fourneau.
- **Surcadençage des machines** : les machines de transformation (fonderie à arc, laminoir, fours électriques) et les machines à fluides ont un onglet à gauche de leur écran, avec un emplacement de module. Un module de surcadençage ×2, ×4 ou ×8 fixe la vitesse la plus haute ; le bouton sous l'emplacement la règle (clic gauche plus vite, clic droit moins vite). Aller N fois plus vite coûte N² fois plus d'électricité : ×2 coûte ×4, ×4 coûte ×16, ×8 coûte ×64, et la machine accepte un débit d'énergie à la mesure. Chaque module se fabrique à partir du précédent. Cassée, la machine garde son module et sa vitesse.
- **Manuel raccourci et simplifié** dans les douze langues : quatre fois plus court, il ne garde que l'essentiel de chaque chapitre. Il est à jour : escaliers facultatifs, kit qui pose le déflecteur, ports de pas de tir, liaisons par câble de données ou module sans fil. Deux nouveaux chapitres, « Fours électriques » et « Surcadençage », et deux nouvelles démonstrations animées qui montent pièce par pièce le pas de tir d'essai 3×3 et la foreuse. Les démonstrations des pas de tir montrent aussi les escaliers.
- Textures plus « Minecraft » sur les pièces fines : poutres, rebords, tubes et pièces inclinées ne reçoivent plus la texture entière tassée dans quelques pixels, qui donnait des traînées lisses. Ils montrent maintenant des pixels à la taille de ceux du bloc. Cela touche 89 modèles : charpente de tour, colonne technique, garde-corps, paliers, mâts, projecteurs, table de lancement, ports de pas de tir, passerelles, capsules, pupitres, établi, station de poursuite, poutre de station, bacs de culture, laboratoire, caissons d'hibernation, rover, atterrisseur...
- **Tuyaux colorés** : un clic droit à main vide sur un tuyau à liquides ou à gaz change sa couleur (orange, bleu clair, blanc, vert, violet, bleu, jaune, rouge, puis sans couleur) ; ses nœuds, ses colliers et ses arêtes en prennent la teinte. Deux tuyaux de couleurs différentes ne se raccordent jamais, même collés, et restent deux réseaux ; un tuyau sans couleur se raccorde à tous. Posé contre un port de pas de tir, un tuyau prend la couleur de son ergol (orange pour le RP-1, bleu clair pour l'oxygène, blanc pour l'hydrogène, vert pour le méthane, violet pour l'hélium-3, bleu pour l'eau), et un tuyau posé sur un tuyau coloré en prolonge la couleur : les conduites des ports côte à côte ne se mélangent plus. Le clic droit accroupi garde le mode aspiration.
- Accumulateurs : plus de détails sur toutes les faces, en restant simple (panneau en creux dans le cadre, interrupteur, boulons, voyants et liseré de la génération sur les flancs, patins et grille dessous). L'écran ne montre plus les crans de la génération.
- Les panneaux des ports de pas de tir (RP1, LOX, H2O...), les panneaux de sortie et de danger, les écrans, les cadrans, les plans et les plantes des bacs retrouvent leur dessin entier : seules les textures de matière (aciers, laiton, caillebotis, châssis) gardent les pixels nets des pièces fines.
- Boosters latéraux des fusées assemblées : ils reprennent l'aspect des blocs de booster qu'on pose. Corps de poudre blanc cerclé de bagues noires et d'un filet rouge, ligne d'alimentation en cuivre, bande de danger au pied, tuyère large rougie par la chaleur, et une pointe en gradins à sonde rouge ; plus d'ailerons de fusée-sonde. Un booster déjà dressé prend ce nouvel aspect la prochaine fois que sa fusée est assemblée.
- Étages des fusées assemblées : comme les boosters, ils reprennent l'aspect des blocs de fusée qu'on pose. Chaque tronçon a la peau de son bloc ; les réservoirs sont cerclés de deux frettes et longés d'une ligne de cuivre, les tuyères prennent la texture des blocs de moteur et leur bague de cuivre, l'interétage sa bande de danger, la coiffe ses coutures et ses trappes cerclées de laiton, la pointe ses gradins et sa sonde rouge. Sur un lanceur lourd, les bagues affleurent le corps.
- Moteurs des fusées assemblées : sous la jupe, ce sont maintenant de vrais moteurs, tels les blocs de moteur (le cardan, la chambre, deux turbopompes de cuivre, la tuyère en trois gradins cerclée de cuivre), au lieu de plaques empilées. Un seul moteur sous un lanceur moyen, cinq sous un lanceur lourd ; tuyères orangées au kérosène, cuivrées à l'hydrogène. Les moteurs à pression et à poudre prennent la tuyère de leur bloc.
- Fusées assemblées : plus aucune face superposée qui scintille (bagues, frettes, bandes de danger, lignes de cuivre, rails), et les cinq moteurs d'un lanceur lourd, un peu plus petits et resserrés, ne dépassent plus du corps de la fusée.
- Foreuse : elle ne perce plus de puits au milieu de la zone avant de forer. En mode « Filons » comme en mode « Tout miner », elle fouille directement la zone couche par couche.

# Orbitalis — 0.10.0 (bêta)

*Minecraft 26.2 · NeoForge 26.2.0.88 · JEI recommandé*

## Nouveautés
- **Accumulateurs** : un stockage d'électricité sans réglage, qu'on pose et qu'on branche. Sa jauge de cinq crans regarde le joueur qui le pose ; l'énergie entre par cette face et les quatre autres, et ressort par le dos. Il garde son énergie quand on le casse. Les quatre générations partagent le même châssis, qui gagne à chacune un signe de son amélioration : coiffes d'angle, filet lumineux, puis fenêtre de jauge cerclée de lumière. Des détails simples habillent le châssis : graduation et voyant de marche à côté de la jauge, repères de polarité et bande de danger à la sortie, flèche d'entrée sur les flancs, plaque signalétique sur le dessus. Un clic droit ouvre un écran épuré, dans le style salle de contrôle : la charge en grand, une barre graduée sur toute la largeur, une ligne d'état avec le temps avant qu'il soit plein ou vide, puis l'entrée et la sortie côte à côte. Quatre générations, chacune fabriquée autour de la précédente : 1, 4, 16 et 64 millions de FE, pour 1 000, 4 000, 16 000 et 64 000 FE par tick. Un chapitre du guide les présente.
- **Pompe à eau** : posée au-dessus d'une étendue d'au moins 8 blocs d'eau (une nappe de 4×2), elle pompe un seau par seconde pour 10 FE par tick, sans jamais la vider. C'est une vraie pompe, sur son socle, avec son moteur : le câble se branche sur son boîtier électrique, à l'arrière, et le tuyau sur sa bride de sortie, à l'avant.
- Chaque machine a sa propre livrée : la couleur de sa coque et les motifs de ses flancs, de son dessus et de sa grille changent d'une machine à l'autre (ailettes du générateur, bandes de danger de la fonderie, conduites de l'électrolyseur, givre du liquéfacteur...). La forme reste la même pour toutes.
- Cassés, les réservoirs gardent leur contenu : l'objet l'emporte et le rend une fois reposé. Un bloc de réservoir modulable emporte sa part.
- Le réservoir modulable ravitaille aussi la fusée, collé à un bloc de ravitaillement ou relié par un tuyau.
- En multijoueur, tous les joueurs à moins de 64 blocs du pas de tir au décollage reçoivent les points de recherche du vol, pour leur agence.
- Cassées, toutes les machines gardent leur énergie et leur contenu, objets comme fluides (générateurs, fonderie, laminoir, machines à fluides, pompe, panneaux solaires, batteries, réacteur à fusion...). L'info-bulle de l'objet les affiche. Rien ne tombe plus au sol à part : tout part avec l'objet, qui tombe toujours, même soufflé par une explosion. En mode créatif, une machine qui contient quelque chose tombe quand même, pour ne rien perdre.
- **Trois nouvelles générations de panneaux solaires** : le panneau avancé (cellules noires, cadre graphite et liseré cyan), le panneau à pérovskite (cellules irisées, contacts dorés, tête de suivi du soleil) et le panneau quantique (cellules aux franges d'interférence animées, cadre de chrome lumineux, anneaux lumineux sur le mât). Chacune produit environ deux fois et demie plus que la précédente, jusqu'à 300 FE par tick au sol et 1 200 en orbite pour le quantique. Chacune se fabrique avec deux panneaux de la génération d'avant : verre renforcé, carte électronique et tôles d'aluminium-lithium ; puis cellules solaires, processeur de vol, batterie et tôles de titane ; puis capteurs optiques, cœur de réacteur, voile solaire et blocs de titane. Seuls les panneaux d'une même génération se raccordent.
- **Foreuse** : une tour de forage à part, avec ses propres blocs, bâtie d'après un plan fantôme : la foreuse au milieu du rang avant d'un socle de 3×3 (huit socles de foreuse), et par-dessus des étages pleins de neuf mâts de foreuse : la tour est pleine, de 3×3×3 à 3×3×9. Complète, elle prend son aspect assemblé, comme un pas de tir, moderne et technologique : piliers d'alliage clair parcourus d'une bande lumineuse cyan, habillage de plaques graphite rivetées, module de ventilation et voyants à chaque étage, couronne à bande lumineuse et feux d'obstacle rouges, plateau tournant cerclé de lumière au sommet, caisson à bande de danger au pied. Pour qu'on la reconnaisse de loin, un râtelier de tiges de forage court sur chaque face, et une tête d'entraînement (platine, moteur à ailettes, tige et bouchon pivotant) coiffe la tour. Posés seuls, le socle (tôle striée, bande de danger, caisson à grille et voyant, pieds d'alliage) et le mât (treillis jaune, croix, goussets et platine boulonnée) ont leur propre aspect soigné. Un mât se fabrique par 8. Elle fore la zone devant elle, jamais sous elle : un bouton d'aperçu dessine la zone dans le monde : un voile cyan transparent à fleur de sol, bordé d'un ruban transparent. Le butin part dans le coffre ou la machine qui touche le socle ; le câble et le tuyau d'eau se branchent sur n'importe quel bloc du socle.
  - **Plus haute, plus loin** : la plus petite tour (3×3×3) fore jusqu'à 9×9 sur 32 blocs de profondeur ; chaque étage de mâts élargit et approfondit la zone, jusqu'à 33×33 et jusqu'au fond du monde pour la plus haute (3×3×9). On règle toujours la taille de la zone, dans la limite de la tour.
  - **Deux modes** : « Filons » n'extrait que les sortes cochées ; « Tout miner » extrait tout, sauf les fluides et les blocs qui ont un contenu (coffres, machines).
  - **Tronçon entier** : dès 5 blocs de haut, la foreuse peut forer tout le tronçon du monde qui est devant elle, aligné sur la grille du monde.
  - **Remplacement** : chaque bloc extrait est remplacé par le bloc posé dans la case de remplacement (que la réserve regarnit), ou, la case vide, par la roche alentour. Cela coûte 300 FE de plus par bloc. Sur la Lune, Mars et Titan, elle extrait aussi le régolithe pour l'extracteur d'hélium-3.
  - **Écran de machine** : jauges d'eau, de chaleur et d'énergie, dix-huit cases de réserve, case de remplacement (un bloc fantôme quand elle est vide) et inventaire, à côté des réglages (voyants sur les bascules), de la carte des filons et des sortes à extraire. Une carte d'instrument montre la hauteur de la tour, étage par étage, et l'avancement du forage.
  - **Refroidissement à l'eau** : la tête chauffe à chaque bloc. Refroidie par un tuyau d'eau, elle fore vite ; trop chaude, elle ralentit puis s'arrête le temps de refroidir.
  - **Carottage** : il révèle, couche par couche, les sortes présentes sous la zone. Le premier filon de chaque sorte trouvé dans un monde rapporte 15 points de recherche, 30 pour un minerai rare.
  - **Prospection par satellite** : avec un satellite d'observation en service, l'écran montre la carte des filons de la zone, colonne par colonne, et combien il y en a de chaque sorte.
- **Les pas de tir fonctionnent à l'électricité** : pompes, vérins, vannes et déluge en dépendent. Un câble se branche sur n'importe quelle dalle du tablier, sur un bloc de ravitaillement ou sur la table, qui garde une réserve de 200 000 FE. Le pas de tir consomme 20 FE par tick par bloc de côté quand il travaille (60 pour le pas de tir d'essai, 220 pour le 11×11), rien au repos. Sans électricité, aucune étape ne démarre et le remplissage s'arrête ; une panne pendant le compte à rebours le reporte. Le pupitre affiche l'état de l'alimentation.
- **Pas de tir posés sur le sol** : le tablier ne repose plus sur des appuis. Le kit pose la table directement sur le sol, le déflecteur de flammes s'enterre d'un bloc sous la table, et une seule marche suffit pour monter sur le tablier. La tour part du même sol. Les pas de tir déjà bâtis gardent leur forme, tablier surélevé sur ses appuis, même si on change leur plan avec un kit.
- **Purge des machines à fluides** : quand une sortie est pleine, la machine s'arrête, même si l'autre a de la place. Sous chaque jauge de sortie, un bouton règle sa purge. Un clic gauche passe à la purge à 50 % (le surplus au-delà de la moitié est rejeté à l'air), puis à la purge totale (tout est rejeté). Un clic droit l'arrête.
- L'ordinateur de conception garde le brouillon en cours : on le retrouve en revenant, et en multijoueur tous les joueurs voient le même, réglage après réglage.
- Panneaux solaires : 80 FE par tick en orbite (au lieu de 40) et 20 au sol de jour (au lieu de 8). Quatre panneaux raccordés en carré produisent chacun un quart de plus.

## Équilibrage
- Batterie de station : 2 000 000 FE (au lieu de 500 000) et un débit de 8 000 FE par tick (au lieu de 2 000). Elle devient un monolithe blanc, un bloc plein sans relief : un blanc mat, un fin liseré bleu sur les flancs, et à l'avant, tourné vers le joueur à la pose, une seule fente verticale qui se remplit de cyan avec la charge.
- Recherche du début de partie : chaque vol réussi rapporte ses données (3 points en vol atmosphérique, 6 dans l'espace, 10 en orbite et au-delà). Les primes des premiers vols passent à 20 et 50 points, la charge utile de fusée-sonde à 10. Une fusée-sonde rapporte donc 16 points par vol au lieu de 5.
- Nouveaux jalons pour les premières pièces fabriquées : carte électronique, moteur à poudre, charge utile de fusée-sonde, kit de pas de tir, moteurs à ergols, émetteur radio, centrale de navigation.

## Corrections
- Les cadres des panneaux solaires restent dans leur bloc : deux panneaux de générations différentes posés côte à côte ne se chevauchent plus et ne scintillent plus, et le dessous d'un panneau ne scintille plus non plus.
- Casser un réservoir modulable plein fait voler des éclats de son contenu, à sa texture et à sa couleur, au lieu des éclats de verre.
- Le câble électrique et le câble de données ont la même carrure que les tuyaux : même tube, même platine et même collier contre les machines.
- Le motif des panneaux solaires est centré sur le bloc : un panneau seul est symétrique, et son icône, désormais encadrée, aussi. Une rangée de panneaux reste une seule nappe régulière, et son cadre est continu : plus d'écart au raccord entre deux panneaux. Le cadre lui-même est un vrai profilé, arête vive, liseré de sa génération et joint de vitrage noir.
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

## Changes
- **Interstellar ship jump** (with `interstellar = true`): right-clicking the helm opens the bridge. It shows where the ship is, a light for each of its systems, the destination and, in one sentence, what stops it from leaving. The "Jump" button starts a five-second countdown; then the whole ship leaves its cradle, with its blocks, their contents and the whole crew aboard, and reappears in orbit of Proxima b. The jump uses 4 exotic matter from the warp core. Proxima b must have been discovered at the observatory, and the arrival space must be clear. From Proxima b orbit, the same helm brings the ship back to its shipyard.
- **Spinning ship ring**: right-clicking the ring drive starts it, if the ring is whole. The ring and its four spokes spin around the ship, speed up over four seconds, then make one turn every twelve seconds. While it spins, the crew has gravity in the whole ship instead of floating. A second right-click stops it: it slows down and comes to rest exactly on a quarter turn, where it gets its blocks back. A spinning ring can't be broken; it stays with the ship when it jumps, and the bridge shows "spinning".
- **Proxima b shuttle**: in Proxima b orbit, the bridge has a "Shuttle" button. A capsule leaves the side of the ship, with you at the controls; other crew members can climb in. The return key plans the deorbit burn, like going home to Earth, but toward Proxima b: reentry with shaking, parachutes, landing. On the ground you can get out in a suit; the shuttle can't be picked up. To leave, hold the launch key on board: the shuttle climbs back, docks with the ship, and the crew is back on the bridge. Left empty in orbit, it goes back into its bay.
- Flight screens: away from the Moon, the climb is called "Liftoff" and the powered descent "Landing", instead of "Moon liftoff" and "Moon landing" (Mars, Titan, Europa, Proxima b).
- **TRAPPIST-1 e** (with `interstellar = true`): an ocean world 39 light-years away, and its orbit. A sea with almost no end, with islands of blue-grey basalt under a blue-green algal mat, lined with pale sand. Under a dim red light the sky is mauve with pink clouds; the small star TRAPPIST-1 stands in the east, and three of its other planets show as large as moons. Its flora: spire reeds on the shores, glowing gleam cups, and underwater meadows of tide grass. Gravity 0.93; the air can't be breathed, a suit is needed. First steps: +3,000 research points.
- **Ship destination**: at the shipyard, the bridge has a ‹ › selector to choose where to jump, Proxima b or TRAPPIST-1 e. Each world must have been discovered at the observatory. The ship remembers its destination; the bridge shows which world it is orbiting, and the shuttle goes down to that world. On TRAPPIST-1 e it lands on an island or splashes down and floats: you go back to the ship the same way. The shuttle's ground hints are shorter and fit on screen.
- **Kepler-452b** (with `interstellar = true`): "Earth's cousin", 1,400 light-years away. It is discovered at the observatory after TRAPPIST-1 e, then becomes the ship's third destination. A super-Earth of seas and plains under a Sun-like star: a blue sky with a golden haze, bronze turf on dark soil, low and spreading bronze ferns and amber tufts. Gravity is 1.5 times the planet's: you jump lower and fall faster. The air can't be breathed. First steps: +3,500 research points. The shuttle lands or splashes down there.
- **Warp probes** (with `interstellar = true`): to Proxima b, TRAPPIST-1 e and Kepler-452b. A Mars robotic lander on a small warp drive charged with exotic matter: a rocket that reaches transfer orbit is enough, the probe makes the rest of the trip alone and lands on its exoplanet, east of the launch pad's position, with its coordinates announced. The exoplanet must have been discovered at the observatory, or the probe has no target and is lost. Recipe: exotic matter, titanium, a Mars robotic lander and a dye in the star's colour (red, cyan or yellow); engineering study and the warp drive technology. First probe: +800, +1,000 and +1,200 points.
- **Three galaxies at the observatory**, discovered after Andromeda: the Large Magellanic Cloud (its bar of stars and the pink Tarantula cloud), the Triangulum Galaxy (a loose, fluffy spiral) and the Whirlpool Galaxy (a large face-on spiral and its small companion). Each has its photo, observation window and reward.
- **Galactic jump to Tarantula b** (with `interstellar = true`): once the Large Magellanic Cloud is discovered, it becomes the ship's fourth destination. The jump empties a full core (8 exotic matter); you must recharge the core there with the exotic matter you brought to come back. Tarantula b is a world of pink dust and violet basalt, dotted with glowing crystal clusters, glowthreads and rose veils, under thin air and the blue light of a young star. In its sky, our Galaxy, seen from outside at last, and the great pink Tarantula cloud. Gravity 0.8; first steps: +5,000 points. Each jump's cost now depends on its destination, and the discovery message names the body to observe.

# Orbitalis — 0.10.1 (beta)

*Minecraft 26.2 · NeoForge 26.2.0.88 · JEI recommended*

## Changes
- Upgrading an accumulator keeps its energy: the next generation, crafted around the old one, takes over its whole charge; only the capacity goes up.
- Launch pads: the plan now has an access stair on each side of the deck, and both are optional. You can remove either or both from a formed pad without breaking it: it stays in service, doesn't show them as ghosts again and doesn't put them back.
- Launch pad kit: it now includes the flame deflector. The recipe needs a deflector touching the rest (below, above, or beside the paper or the table). When placed, the kit sets down the table and, under it, the deflector in place of the block you aimed at; a confirmation window warns you before it's replaced. An unbreakable block or a container is never removed: you get the deflector back instead.
- **Launch pad ports** (formerly fuel ports): right-clicking with an empty hand picks what they carry at any time, even on a built pad. Two new settings: electricity (the only one that powers the table) and data. Their recipe takes one connector of each kind: a liquid pipe, an energy cable, a gas pipe and a data cable.
- **Data cable links**: a launch console, a test console or a screen wall connected by a data cable to a launch pad's data port follows that pad, at any distance. A console no longer links itself to the nearest table.
- **Wireless module**: the only way to link without a cable. Right-click a console, a screen or a tracking station, then a launch pad, a console or a screen: the link is made and the module is used up. Sneak-right-clicking no longer links anything.
- **Induction furnace**: it cooks with electricity (20 FE per tick) everything a furnace cooks, ores included, twice as fast and without fuel.
- **Melting furnace**: it only takes ores and raw ores, and each gives twice what it gives in a furnace, for 60 FE per tick at a furnace's pace. It unlocks with the new "Ore Refining" technology and is crafted from an induction furnace and a blast furnace.
- **Machine overclocking**: processing machines (arc foundry, rolling mill, electric furnaces) and fluid machines get a tab on the left of their screen, with a module slot. An overclock module ×2, ×4 or ×8 sets the top speed; the button under the slot picks the speed (left click faster, right click slower). Going N times faster costs N² times the power: ×2 costs ×4, ×4 costs ×16, ×8 costs ×64, and the machine takes in energy fast enough to match. Each module is crafted from the previous one. When broken, the machine keeps its module and speed.
- **Shorter, simpler manual** in all twelve languages: four times shorter, it keeps only what matters in each chapter. It is up to date: optional stairs, the kit placing the deflector, launch pad ports, links by data cable or wireless module. Two new chapters, "Electric Furnaces" and "Overclocking", and two new animated demonstrations that build the 3×3 test pad and the drill rig piece by piece. The launch pad demonstrations now show the stairs too.
- More "Minecraft" textures on thin parts: beams, rims, tubes and tilted parts no longer get the whole texture squeezed into a few pixels, which gave smooth streaks. They now show pixels the size of the block's own. This affects 89 models: tower frame, service tower, railings, landings, masts, floodlights, launch table, launch pad ports, walkways, capsules, consoles, workbench, tracking station, station truss, growing trays, lab rack, hibernation pods, rover, lander...
- **Colored pipes**: right-click a fluid or gas pipe with an empty hand to change its color (orange, light blue, white, green, purple, blue, yellow, red, then no color); its nodes, collars and rails take the tint. Two pipes of different colors never connect, even side by side, and stay two networks; a pipe with no color connects to all. Placed against a launch pad port, a pipe takes its propellant's color (orange for RP-1, light blue for oxygen, white for hydrogen, green for methane, purple for helium-3, blue for water), and a pipe placed on a colored pipe carries its color on: lines from side-by-side ports no longer merge. Sneak right-click still toggles pull mode.
- Accumulators: more detail on every face while staying simple (a recessed panel in the frame, a switch, bolts, side lights and a tier-colored strip, feet and a grille underneath). The screen no longer shows the tier pips.
- Launch pad port signs (RP1, LOX, H2O...), exit and warning signs, screens, dials, blueprints and tray plants show their whole picture again: only material textures (steels, brass, grating, frames) keep crisp pixels on thin parts.
- Side boosters on assembled rockets now look like the booster blocks you place: a white solid body ringed in black with a red stripe, a copper feed line, a hazard band at the foot, a wide heat-reddened nozzle and a stepped nose with a red-tipped probe; no more sounding-rocket fins. A booster already standing takes the new look the next time its rocket is assembled.
- Assembled rocket stages, like the boosters, now look like the rocket blocks you place. Each section wears its block's skin; tanks get two frames and a copper feed line, nozzles take the engine blocks' texture and copper collar, the interstage its hazard band, the fairing its seams and brass-rimmed hatches, the nose its steps and red-tipped probe. On a heavy launcher the rings sit flush with the body.
- Assembled rocket engines: under the skirt they are now real engines like the engine blocks (gimbal, chamber, two copper turbopumps, a three-step nozzle with its copper collar) instead of stacked plates. One engine under a medium launcher, five under a heavy one; orange nozzles for kerosene, copper for hydrogen. Pressure-fed and solid engines take their block's nozzle.
- Assembled rockets: no more overlapping faces flickering (rings, frames, hazard bands, copper lines, rails), and the five engines of a heavy launcher, slightly smaller and closer together, no longer stick out of the rocket body.
- Drill rig: it no longer bores a shaft in the middle of the area before drilling. In both "Ores" and "Mine all" modes, it goes straight through the area layer by layer.

# Orbitalis — 0.10.0 (beta)

*Minecraft 26.2 · NeoForge 26.2.0.88 · JEI recommended*

## New
- **Accumulators**: plug-and-play electricity storage with nothing to set up. Its five-step gauge faces the player who places it; power comes in through that face and the four others, and leaves through the back. It keeps its energy when broken. All four generations share one chassis that gains a visible upgrade each time: corner caps, a light trim, then a gauge window ringed with light. Simple details dress the chassis: a scale and a power light by the gauge, polarity marks and a hazard stripe at the output, an input arrow on the sides, a nameplate on top. Right-click opens a clean control-room style screen: the charge in large figures, a graduated full-width bar, a status line with the time until full or empty, then input and output side by side. Four generations, each crafted around the one before: 1, 4, 16 and 64 million FE, moving 1,000, 4,000, 16,000 and 64,000 FE per tick. A guide chapter covers them.
- **Water pump**: placed above a body of at least 8 water blocks (a 4×2 pool), it pumps a bucket per second for 10 FE per tick and never drains it. It's a real pump, on its skid, with its motor: the cable goes into its electrical box at the back, and the pipe onto its outlet flange at the front.
- Every machine has its own livery: the colour of its casing and the patterns on its sides, top and grille differ from one machine to the next (the generator's fins, the foundry's hazard stripes, the electrolyzer's pipes, the liquefier's frost...). The shape stays the same for all.
- Broken tanks keep their contents: the item carries them and gives them back once placed again. A modular tank block takes its share.
- The modular tank fuels rockets too, placed against a fuel port or connected to one by a pipe.
- In multiplayer, every player within 64 blocks of the pad at liftoff earns the flight's research points for their agency.
- Every machine keeps its power and its contents when broken, items and fluids alike (generators, arc foundry, rolling mill, fluid machines, pump, solar panels, batteries, fusion reactor...). The item's tooltip shows them. Nothing spills on the ground anymore: everything goes with the item, which always drops, even when blown up. In creative mode, a machine that holds something still drops, so nothing is lost.
- **Three new generations of solar panels**: the advanced panel (black cells, graphite frame with a cyan trim), the perovskite panel (iridescent cells, gold contacts, sun-tracking head) and the quantum panel (cells with animated interference fringes, glowing chrome frame, glowing rings on the mast). Each makes about two and a half times more than the previous one, up to 300 FE per tick on the ground and 1,200 in orbit for the quantum panel. Each is crafted from two panels of the generation before: reinforced glass, a circuit board and aluminium-lithium plates; then solar cells, a flight processor, a battery and titanium plates; then optical sensors, a reactor core, a solar sail and titanium blocks. Only panels of the same generation join together.
- **Drill rig**: a drilling tower of its own, with its own blocks, built from a ghost blueprint: the drill rig in the middle of the front row of a 3×3 base (eight drill bases), with full levels of nine drill masts on top: the tower is solid, from 3×3×3 to 3×3×9. Once complete, it takes on its assembled look, like a launch pad, modern and high-tech: light alloy pillars running cyan light strips, riveted graphite cladding, a vent module with status lights on every level, a crown with a light band and red obstruction lights, a turntable ringed with light on top, and a hazard-striped plinth at the foot. So it reads as a drill from afar, a rack of drill pipes runs up each face, and a top drive (plate, finned motor, pipe and swivel cap) crowns the tower. Placed on their own, the base (checker plate, hazard band, vented casing with a status light, alloy feet) and the mast (yellow lattice, cross bracing, gussets and a bolted plate) have their own tidy look. Masts are crafted 8 at a time. It drills the area in front of it, never under itself: a preview button draws the area in the world: a see-through cyan veil at ground level, edged with a see-through ribbon. The loot goes into the chest or machine touching the base; the cable and the water pipe connect to any base block.
  - **Taller reaches further**: the smallest tower (3×3×3) drills up to 9×9 and 32 blocks down; each mast level widens and deepens the area, up to 33×33 and all the way to the bottom of the world for the tallest one (3×3×9). You can still set the size of the area, within what the tower allows.
  - **Two modes**: "Ores" only extracts the ticked kinds; "Mine all" takes everything except fluids and blocks that hold something (chests, machines).
  - **Whole chunk**: from 5 blocks high, the drill rig can drill the whole chunk in front of it, lined up with the world grid.
  - **Replacement**: each block extracted is replaced by the block in the replacement slot (refilled from storage), or, with the slot empty, by the surrounding rock. It costs 300 FE more per block. On the Moon, Mars and Titan it also extracts regolith for the helium-3 extractor.
  - **Machine screen**: water, heat and power gauges, eighteen storage slots, the replacement slot (showing a ghost block when empty) and your inventory, next to the settings (with indicator lights on the toggles), the ore map and the kinds to extract. An instrument card shows the tower's height, level by level, and how far drilling has gone.
  - **Water cooling**: the head heats up with every block. Cooled through a water pipe, it drills fast; too hot, it slows down and then stops until it cools.
  - **Core samples**: they show, layer by layer, which kinds are under the area. The first vein of each kind found in a world earns 15 research points, 30 for a rare ore.
  - **Satellite prospecting**: with an observation satellite in service, the screen shows the ore map of the area, column by column, and how much there is of each kind.
- **Launch pads run on electricity**: pumps, actuators, valves and deluge depend on it. A cable connects to any deck slab, to a fuel port or to the table, which keeps a 200,000 FE buffer. The pad draws 20 FE per tick per block of deck width while it works (60 for the test pad, 220 for the 11×11), and nothing at rest. Without power no step starts and fuelling stops; a failure during the countdown scrubs it. The console shows the power state.
- **Launch pads rest on the ground**: the deck no longer sits on supports. The kit places the table right on the ground, the flame deflector goes one block into the ground under the table, and a single step leads up onto the deck. The tower stands on the same ground. Pads already built keep their shape, with the deck raised on its supports, even if you change their plan with a kit.
- **Fluid machine purge**: when one output is full, the machine stops, even if the other has room. Under each output gauge, a button sets its purge. Left-click switches to the 50% purge (anything above half is vented into the air), then to the full purge (everything is vented). Right-click stops it.
- The design computer remembers the draft you were working on: it's still there when you come back, and in multiplayer everyone sees the same one, change by change.
- Solar panels: 80 FE per tick in orbit (up from 40) and 20 on the ground in daylight (up from 8). Four panels joined in a square each produce a quarter more.

## Balance
- Station battery: 2,000,000 FE (up from 500,000) and 8,000 FE per tick in and out (up from 2,000). It becomes a white monolith, a plain full block: matte white, a thin blue line along the sides, and on the front, facing the player who placed it, a single vertical slot that fills with cyan as it charges.
- Early research: every successful flight now brings back its data (3 points for an atmospheric flight, 6 for space, 10 for orbit and beyond). First-flight bonuses go up to 20 and 50 points, and the sounding rocket payload to 10. A sounding rocket now earns 16 points per flight instead of 5.
- New milestones for the first parts you craft: circuit board, solid motor, sounding rocket payload, pad kit, liquid-fuel engines, radio transmitter, navigation unit.

## Fixes
- Solar panel frames stay inside their block: two panels of different generations side by side no longer overlap or flicker, and neither does the underside of a panel.
- Breaking a filled modular tank now throws out debris of its contents, in their texture and colour, instead of glass shards.
- The energy cable and the data cable now have the same size as the pipes: same tube, same plate and same collar against machines.
- The solar panel pattern is now centred on the block: a lone panel is symmetrical, and so is its icon, which now shows the frame. A row of panels is still one even sheet, and its frame is continuous: no more gap where two panels meet. The frame itself is a proper profile, with a crisp top edge, its generation's trim and a black glazing seal.
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
