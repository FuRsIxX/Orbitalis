# Sons d'Orbitalis : comment les changer

Les sons sont vos fichiers, rangés dans `tools/sounds/orbitalis sound/`, un dossier par famille :

- `pad/` : le pas de tir et le centre spatial ;
- `capsule/` : le vol, la cabine et ses alertes ;
- `station/` : la station.

L'outil `tools/generate_sounds.py` les convertit au format du jeu (Ogg Vorbis, une seule voie), **ramène chacun à son
niveau sonore** et les déclare dans `sounds.json`.

## Remplacer un son

1. Déposez votre fichier sous le même nom, dans le même dossier (`.wav`, `.ogg`, `.mp3` ou `.flac`).
2. Depuis la racine du projet, lancez :

   ```
   python tools/generate_sounds.py
   ```

   Pour ne refaire que certains sons, donnez leur nom (`python tools/generate_sounds.py docking undocking`), ou
   `dedies` pour tous ceux des dossiers `capsule/` et `station/`, avec `part_placed` et `warning_overpressure`.
3. Lancez le jeu (`.\gradlew runClient`) ou déployez (`.\gradlew deployCurseForge`).

L'outil affiche pour chaque son sa durée, son niveau obtenu (LUFS) et sa crête : vérifiez que le niveau est proche de
celui visé.

## Le volume

Vous n'avez pas à régler le niveau de vos fichiers : chacun est ramené au niveau perçu prévu pour lui, en LUFS (plus
près de zéro : plus fort), et ses crêtes sont plafonnées pour ne jamais saturer. Les niveaux visés sont dans
`tools/generate_sounds.py` : `PAD_LEVELS` pour le pas de tir, `DEDICATED` pour les autres. Ils s'étagent ainsi :

| Niveau | Sons |
|---|---|
| -11 à -16 | explosions, moteurs de la fusée, alarme de surpression, séparation d'étage, atterrissage |
| -15 à -17 | rentrée, moteur de descente, parachutes, coiffe, amarrage |
| -17 à -19 | alertes de bord, sas, pièces posées |
| -21 à -24 | voile des parachutes, générateur d'air, ambiance de la station |

Un son trop fort ou trop discret en jeu se corrige en changeant son niveau visé, puis en relançant l'outil.

## Les boucles

Les sons en boucle (rentrée, voile, moteur de descente, alertes de bord, générateur d'air, ambiance) sont ramenés à
leur niveau **sans perdre leur raccord**. Ceux dont le fichier ne se raccorde pas de lui-même (`SEAMLESS` : moteur de
descente, générateur d'air, ambiance) ont leur fin fondue dans leur début, sur une demi-seconde au plus : ils
raccourcissent d'autant.

`parachute_deploy_loop` prend la suite de `parachute_deploy` à la fin de celui-ci : si ce dernier change de longueur,
reportez sa durée en ticks dans `DEPLOY_TICKS` (`src/main/java/com/orbitalis/client/CapsuleSounds.java`).
L'alarme de surpression se répète toutes les 2,2 s : si son fichier change de longueur, reportez-la dans
`ALARM_REPEAT_TICKS` (`LaunchTableBlockEntity.java`).

## Les sons du pas de tir

| Fichier (`pad/`) | Quand il est joué |
|---|---|
| `purge` | Mise sous pression, mise en froid des moteurs, début du compte à rebours, souffle des soupapes en surpression |
| `valve` | Ouverture et fermeture des vannes, ouverture du déluge, allumage raté, lâcher des attaches |
| `hydraulic` | Retrait du bras ombilical et de la passerelle, portique d'embarquement, poussée des manœuvres en orbite |
| `creak` | Compte à rebours, à T-13 : les bras et les passerelles se rangent |
| `deluge` | Matière de la boucle du déluge, qui dure tant que l'eau coule |
| `ignition` | Allumage ; sans fichier `engine`, matière du grondement des moteurs en vol |
| `warning_overpressure` | Alarme du pas de tir : surpression au-delà de 70 %, jusqu'à la fermeture des vannes ou la rupture ; essai d'alarme depuis le pupitre |
| `part_placed_1` à `_5` | Pièce de fusée posée, à la main ou par le chantier ; table de lancement posée (le jeu tire une variante au hasard) |

Un `.ogg` à une voie déposé dans `pad/` est repris tel quel, sans changer son niveau.

## Les sons du vol et de la station

| Fichier | Quand il est joué |
|---|---|
| `capsule/stage_separation` | Séparation d'un étage |
| `capsule/fairing_jettison` | Largage de la coiffe |
| `capsule/reentry_loop` | En boucle, pendant la traversée de l'atmosphère (planète, Titan, Mars) |
| `capsule/parachute_deploy` | Ouverture des parachutes ; `parachute_deploy_loop` prend la suite jusqu'au sol |
| `capsule/capsule_landing` | Le vaisseau se pose, sur tous les mondes |
| `capsule/lander_engine` | En boucle, tant que le moteur de descente pousse, sur tous les mondes |
| `capsule/capsule_alert_maneuver` | Toutes les 2,5 s, tant qu'une étape, une poussée ou des attaches attendent et que le temps presse |
| `capsule/capsule_alert_imminent_crash` | En boucle : structure qui souffre, descente trop rapide ou sans carburant, sol proche sans parachutes |
| `capsule/capsule_alert_stall` | En boucle : vaisseau trop penché, à redresser vite |
| `capsule/capsule_alert_deploy` | En boucle : fenêtre des parachutes ouverte, parachutes pas armés |
| `station/airlock_cycle` | Mise à niveau de l'air du sas |
| `station/airlock_hatch open` et `close` | L'écoutille du sas monte, puis redescend |
| `station/docking` et `undocking` | Amarrage et désamarrage de la capsule |
| `station/life_support_hum` | En boucle, près d'un générateur d'air en service |
| `station/station_ambient_noise` | En boucle, discrète, dans les pièces pressurisées d'une station |

Les alertes de bord peuvent sonner ensemble. Un son dédié sans fichier reste muet, sans erreur.

## Les sons d'explosion

Ils ne sont pas synthétisés : ce sont vos fichiers, rangés dans `tools/sounds/explosion/`. C'est le début du nom qui
dit à quoi le fichier sert ; le numéro qui suit en fait une variante, et le jeu en tire une au hasard à chaque fois.

| Début du nom | Quand il est joué |
|---|---|
| `explosion proche N` | La fusée explose à moins de 48 blocs du joueur : sur le pas de tir ou juste au-dessus |
| `explosion moyenne distance N` | Elle explose entre 48 et 140 blocs : déjà haute, encore bien visible |
| `explosion longue distance N` | Elle explose à plus de 140 blocs ; le son arrive 2 à 3 secondes après l'éclair |
| `chute debris N` | Les tronçons projetés touchent le sol (deux fois au plus par accident), l'épave d'une fusée ou une capsule s'écrase |

| `overfiled pressure` | Vannes oubliées ouvertes après le plein : la montée en pression des réservoirs, dans les dernières secondes avant qu'ils ne cèdent |

Une fusée n'explose qu'une fois, et un seul de ces sons d'explosion est joué par accident.

Le son de surpression est lancé pour **finir à l'instant où l'explosion se fait entendre** : sa durée compte. Si vous le
remplacez par un fichier d'une autre longueur, l'outil affiche la valeur à reporter dans `OVERPRESSURE_SOUND_TICKS`
(`src/main/java/com/orbitalis/phase1/spaceport/LaunchTableBlockEntity.java`). Vannes refermées à temps, il se tait.

Pour ajouter une variante, déposez par exemple `explosion proche 3.wav` dans ce dossier. Pour convertir ces sons
sans refaire ceux du pas de tir :

```
python tools/generate_sounds.py explosions
```

Ne déposez pas vos `.wav` dans `src/main/resources/.../sounds/` : le jeu ne lit que des `.ogg` aux noms sans espace,
que l'outil fabrique dans `sounds/explosion/` (`near_1.ogg`, `mid_1.ogg`, `far_1.ogg`, `debris_1.ogg`...).

Les distances et les délais se règlent en tête de `src/main/java/com/orbitalis/client/BreakupFx.java`
(`NEAR_UNTIL`, `MID_UNTIL`, `FAR_DELAY_MIN`, `FAR_DELAY_MAX`, `CLATTERS`).

## Conseils pour vos fichiers

- **Une seule voie (mono) si possible.** L'outil convertit de toute façon en mono : le jeu ne fait décroître avec
  la distance que les sons mono.
- **Pas de silence au début**, sinon le son arrive en retard sur l'image.
- **Pour une boucle**, faites que sa fin rejoigne son début ; sinon, ajoutez son nom à `SEAMLESS`.
- `ignition` commence trois secondes avant le décollage : son moment le plus fort doit arriver vers 3 s.

## Ajouter un son

1. Déposez son fichier dans le bon dossier d'`orbitalis sound/`, et ajoutez une ligne dans `DEDICATED`
   (`tools/generate_sounds.py`) : nom de l'événement, dossier, fichier(s), niveau visé.
2. Déclarez-le dans `src/main/java/com/orbitalis/core/registry/ModSounds.java`.
3. Ajoutez son sous-titre (`subtitles.orbitalis.<nom>`) dans les deux fichiers de `datagen/lang/`, puis
   `.\gradlew runData`.
4. Jouez-le là où il doit l'être, puis lancez l'outil.

## Où vont les fichiers produits

- Sons : `src/main/resources/assets/orbitalis/sounds/pad/`, `capsule/`, `station/` et `explosion/`
- Déclaration : `src/main/resources/assets/orbitalis/sounds.json`

Ces fichiers sont réécrits à chaque passage de l'outil : ne les modifiez pas à la main.

## Si ffmpeg est introuvable

L'outil a besoin de ffmpeg (`winget install Gyan.FFmpeg`). S'il n'est pas trouvé, donnez son chemin dans la variable
d'environnement `FFMPEG`.
