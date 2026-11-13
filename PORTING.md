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
3. Server logic: machines, energy and fluid networks, research, rockets, flights, stations, ship.
4. Networking and screens (GuiGraphics, 1.21.1 widgets).
5. Rendering: block entity and entity renderers, sky and planets, warp jump, ghosts, HUD.
6. Dimensions and world generation (Moon, Mars, Titan, Europa, exoplanets).
7. Game tests and a test map, then in-game captures on Fabric and NeoForge, fixing every difference.
8. Then the same for 1.20.1 (Forge + Fabric), starting from this branch.
