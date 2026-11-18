# Porting Orbitalis to Minecraft 1.21.1 (Fabric + NeoForge)

Source of truth: branch `main` (Minecraft 26.2, NeoForge). This branch, `mc/1.21.1`, is a multiloader
project built with Architectury: `common` holds the mod logic, `fabric` and `neoforge` only hold what is
specific to each loader. Architectury API is a required dependency on both loaders.

## What has to change (measured on `main`, 2026-11-13)

- 435 Java files, ~96,000 lines.
- 137 files import NeoForge directly: event subscribers (46), client packet sending (22), capabilities and
  the new transfer API (ResourceHandler, EnergyHandler, transactions), DeferredRegister, ModConfigSpec,
  datagen providers.
- 96 files use APIs that only exist in 26.x: the submit/extract rendering model (SubmitNodeCollector,
  RenderPipeline, GuiGraphicsExtractor), ValueInput/ValueOutput saving, `Identifier`, block properties
  that need an id, item model definitions (`assets/*/items`), `world_clock`.

## Platform layer (common/…/platform)

Everything a loader does differently goes behind a small interface in `common`, implemented in each
loader module (looked up once at startup):

| Need | Fabric | NeoForge 1.21.1 |
|---|---|---|
| Registries | Architectury `DeferredRegister` | same |
| Networking (payloads) | Architectury `NetworkManager` | same |
| Common events (ticks, players, commands, blocks) | Architectury events, Fabric API where missing | Architectury events, NeoForge bus where missing |
| Energy between blocks | Team Reborn Energy API | `IEnergyStorage` capability |
| Fluids between blocks | Fabric Transfer API (`Storage<FluidVariant>`) | `IFluidHandler` capability |
| Items between blocks | Fabric Transfer API (`Storage<ItemVariant>`) | `IItemHandler` capability |
| Custom fluids (methane, propellants) | `FlowingFluid` + Fabric fluid render registry | `FluidType` + `BaseFlowingFluid` |
| Config | own small JSON config | same file, read by common code |
| Sky and dimension effects | Fabric `DimensionRenderingRegistry` | `RegisterDimensionSpecialEffectsEvent` |
| Key bindings, HUD, tooltips | Architectury client events | same |

Inside the mod, machines keep their own storages (energy, fluids, items); only the edges that talk to
other blocks go through the platform layer.

## Data

Generated data on `main` is converted, not regenerated: a script turns 26.2 JSON into 1.21.1 JSON
(item model definitions back into `models/item`, ingredient objects, dimension types without
`world_clock`, recipe results). The datagen code itself stays on `main`.

## Phases

1. Skeleton builds and starts on both loaders. **Done.**
2. Platform layer, registries (blocks, items, block entities, menus, entities, sounds, particles, fluids,
   creative tabs), assets and converted data. The game starts with every block and item present.
   **Server side done (2026-11-15):** both dedicated servers start, register 198 blocks and 294 items (same as
   `main`) and generate every one of the 13 dimensions (`-Porbitalis.smokeTest`, see below).
3. Server logic: machines, energy and fluid networks, research, rockets, flights, stations, ship.
4. Networking and screens (GuiGraphics, 1.21.1 widgets).
5. Rendering: block entity and entity renderers, sky and planets, warp jump, ghosts, HUD.
   **Client running on both loaders (2026-11-15):** the 26.2 client code is kept almost as is, on a small layer
   that speaks the 26 API over 1.21.1 (`client/port`: `GuiGraphicsExtractor`, `Screen26`, render states and
   `SubmitNodeCollector` over buffers, client events). Checked in game with the capture tool on Fabric and
   NeoForge: machines, launch pad and rocket, design computer, item icons, orbit sky, Earth from orbit and from the
   Moon, Mars, Titan lakes and methane fog, Europa and Jupiter.
6. Dimensions and world generation (Moon, Mars, Titan, Europa, exoplanets).
7. Game tests and a test map, then in-game captures on Fabric and NeoForge, fixing every difference.
   **Game tests: 70/70 on both loaders (2026-11-16)** (`./gradlew :fabric:runGametest`, `:neoforge:runGametest`).
   Launch checked in game on both loaders: countdown, deluge, ignition, smoke and flames, lift-off.
   **Test map (2026-11-18):** built by the server of each loader, then 117 to 129 views captured on each (station
   nodes and port ghosts, ship sections, shipyard, warp jump frame by frame, exoplanets, shuttle, ring, screen guard,
   guide, atlas, piloting and suit HUDs).
8. Then the same for 1.20.1 (Forge + Fabric), starting from this branch.

## Checking a build

    ./gradlew build
    ./gradlew :fabric:runServer -Porbitalis.smokeTest
    ./gradlew :neoforge:runServer -Porbitalis.smokeTest
    ./gradlew :fabric:runGametest
    ./gradlew :fabric:runMultiplayerServer        # then, in another shell:
    ./gradlew :fabric:runClient -Porbitalis.joinServer=true

The smoke test starts a dedicated server, generates the centre chunk of each Orbitalis dimension, logs what it
found (`[essai]` lines), then stops the server and the process.

Data is converted with `python tools/convert_data_121.py` (rerun it after copying new data from `main`).

## Differences with 26.2 found so far

| 26.2 | 1.21.1 | Status |
|---|---|---|
| Environment attributes (sky, fog, clouds, stars, sun angle, sky light colour) | Kept per dimension in `assets/orbitalis/dimension_visuals/`; sky and fog colours also go into the biomes; the sun angle becomes the dimension's fixed time | Same sky, fog and light |
| Bed rule "never sleep, never explode" | `bed_works: false` + `BedBlockMixin`: no explosion, a message points to the crew quarters and hibernation pods | Same behaviour, new message |
| `light_emission` on model elements | NeoForge reads `neoforge_data`; vanilla and Fabric ignore it | Fabric: read back by client mixins (see below) |
| `preRemoveSideEffects` | `RemovalAware`, called by `BlockStateBaseMixin` | Same |
| NeoForge persistent player data | `PlayerData` (saved with the world) | Same |
| Item model definitions (`assets/*/items`) | `models/item/*.json` | Same |
| `block_blob` feature, integer `trapezoid` | `orbitalis:block_blob`, exact weighted list | Same |
| Noise router `preliminary_surface_level` | `initial_density_without_jaggedness` | Same terrain shape |
| One elevator message both ways | Two message types (`elevator_panel` to the player, `elevator` to the server) | Same |
| Test map marked as "experimental" world | Every world with the mod's dimensions is "experimental" in 1.21.1; `PrimaryLevelDataMixin` treats it as stable (NeoForge already did) | No warning on either loader |
| Block entity blocks draw their model | 1.21.1 `BaseEntityBlock` is invisible by default; `BaseEntityBlockMixin` restores the model for the mod's blocks | Same |
| Render layer guessed from textures | `render_layers.json` (tools/render_layers_121.py), registered at client start | Same |
| `END_SKY` pipeline (no alpha cut) | Own core shader `orbitalis:sky` (position_tex_color without the 10 % alpha cut) | Same soft Milky Way and halos |
| Picture-in-picture 3D scenes (guide, shipyard, construction) | Drawn straight into the GUI, clipped to their area | Same framing |
| Emissive model faces | NeoForge reads them; on Fabric, mixins keep the element's light when baking and use it when drawing | Same on both |
| Mod fluids on NeoForge | Architectury gives no textures to NeoForge: the client registers them (water texture, tinted) | Same |
| Particles registered by Architectury | Architectury 13 registers nothing on Fabric: each loader registers the providers itself (`OrbitalisClient.particles`) | Same |
| Block entity renderers registered at client setup | Raced the resource reload; each loader now registers them at its own moment (`OrbitalisClient.blockEntityRenderers`) | Same |
| Game test structures | 1.21.1 walls each test with barriers and spaces them 5 blocks apart: `StructureUtilsMixin` and `StructureGridSpawnerMixin` remove both | Same results |
| JEI 30 | JEI 19 (`compat/jei`, common code): found by its annotation on NeoForge and the `jei_mod_plugin` entrypoint on Fabric. Built against 19.43 (the last one made with Loom 1.11), runs with later 19.x | Same categories and catalysts |
| Create compatibility | Nothing to port: `main` only has the empty package | — |
| Server config per world, synced to players (NeoForge `SERVER` config) | Own file per world (`serverconfig/orbitalis-server.toml`, `defaultconfigs/` as model), sent to each player on join and after a change | Same |
| NeoForge generic config screen | Own screen on vanilla options widgets (`ConfigScreen`), opened from the mod list (NeoForge) or Mod Menu (Fabric) | Same settings, vanilla look |
| Menu screens registered at client setup | NeoForge 21 only takes them during `RegisterMenuScreensEvent`: each loader registers them at its own moment (`OrbitalisClient.menuScreens`) | Same |
| Server ignores a click sent before the player confirms a teleport | Real players never hit it; the capture tool waits a few ticks before clicking | Tool only |
| Test map server stops by itself | 1.21.1 keeps the process alive after `halt`: it exits once the world is closed | Same |

