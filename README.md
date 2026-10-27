# Orbitalis

**From the first steel ingot to the moons of Jupiter.** Orbitalis is a NeoForge mod for Minecraft: build a space industry, a spaceport and modular rockets, launch satellites and probes, assemble stations in orbit, and land on the Moon, Mars, Titan and Europa.

*Version 0.9.0 (beta)*

## At a glance

It all starts on the ground, with ores and a foundry. Step by step, your industry produces the steel, alloys and propellants you need to fly. Rockets are designed stage by stage on the design computer, then built block by block on a launch pad from a ghost plan. Flight follows real physics (thrust, mass, drag, delta-v), and every mission earns research points that unlock what comes next.

**Industry**
- Titanium, aluminum and lithium ores; steel and alloys.
- Combustion generator, arc foundry, rolling mill; energy cables, pipes and data cables.
- Electrolyzer, cryogenic liquefier, RP-1 refinery, Sabatier reactor, helium-3 extractor; modular and cryogenic tanks.
- Fusion reactor and Casimir chamber.

**Spaceport and rockets**
- Spaceports to discover in the world, intact or abandoned.
- Launch pads in three sizes (5×5, 9×9, 11×11): service tower, umbilical arms, deluge, fuel ports, launch console, mission displays.
- Design computer with a stage editor, side boosters, rocket construction site.
- Kerosene, hydrogen, methane, solid, nuclear thermal, ion, solar sail and fusion engines.

**Orbit and crewed flight**
- Satellites (weather, observation, telecommunications, navigation), space telescope, tracking station and observatory.
- Multi-seat capsules, atmospheric reentry, parachutes.
- Spacesuits and their modules; orbital stations built module by module.

**Exploration**
- The Moon, Mars, Titan (methane lakes, rotor drone) and Europa (ice under radiation, cryobot).
- Rovers, in-situ resource drills, planetary bases.

**Progression**
- A research tree across nine fields, goals and milestones.
- An illustrated in-game manual with animated scenes.

## Compatibility

| | |
|---|---|
| Minecraft | 26.2 |
| NeoForge | 26.2.0.88 or newer |
| Java | 25 |
| Required dependencies | none besides NeoForge |
| JEI | optional: shows machine recipes |
| Multiplayer | yes, on dedicated servers; install the mod on the server and on every client |

Tested with Sodium and Iris (shaders).

**Languages:** English, French, German, Spanish, Italian, Portuguese, Russian, Polish, Ukrainian, Simplified Chinese, Japanese and Korean, regional variants included.

## Installation

1. Install NeoForge 26.2.0.88 or newer for Minecraft 26.2.
2. Drop `orbitalis-0.9.0.jar` into your `mods` folder.
3. Optional: add JEI to browse recipes.

In game, open the Orbitalis manual: it walks you from the first ore all the way to orbit. The research tree opens with the **K** key.

## Settings

- **Gameplay** (realism, failures, propellant boil-off, accident damage, satellite lifetime…): in the world's server configuration.
- **Display** (units, particle density): in the client configuration.
- **Interstellar travel**: still in development and disabled by default. To try it early, set `interstellar = true` in `config/orbitalis-startup.toml`, then restart the game. On a server, use the same setting on the server and on every client.

## Building from source

You need JDK 25.

```
./gradlew build                # builds the mod into build/libs
./gradlew runClient            # starts a development client
./gradlew runGameTestServer    # runs the automated in-game tests
./gradlew runData              # regenerates the resources in src/generated
```

Models, textures and sounds are produced by the Python scripts in `tools/`.

## License

All rights reserved © the-simimoune.
