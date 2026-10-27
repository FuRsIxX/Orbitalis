package com.orbitalis.datagen;

import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import java.util.Set;

import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.registry.ModItems;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;

/**
 * Génère les loot tables des blocs (ce qu'ils lâchent une fois minés).
 */
public class OrbitalisBlockLoot extends BlockLootSubProvider {
    public OrbitalisBlockLoot(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        // Minerais : lâchent le minerai brut (sensible à Fortune), ou le bloc lui-même avec Toucher de soie.
        add(ModBlocks.BAUXITE_ORE.get(), block -> createOreDrop(block, ModItems.RAW_BAUXITE.get()));
        add(ModBlocks.DEEPSLATE_BAUXITE_ORE.get(), block -> createOreDrop(block, ModItems.RAW_BAUXITE.get()));
        add(ModBlocks.TITANIUM_ORE.get(), block -> createOreDrop(block, ModItems.RAW_TITANIUM.get()));
        add(ModBlocks.DEEPSLATE_TITANIUM_ORE.get(), block -> createOreDrop(block, ModItems.RAW_TITANIUM.get()));
        add(ModBlocks.LITHIUM_ORE.get(), block -> createOreDrop(block, ModItems.RAW_LITHIUM.get()));
        add(ModBlocks.DEEPSLATE_LITHIUM_ORE.get(), block -> createOreDrop(block, ModItems.RAW_LITHIUM.get()));

        // Blocs de métal : se lâchent eux-mêmes.
        dropSelf(ModBlocks.STEEL_BLOCK.get());
        dropSelf(ModBlocks.ALUMINUM_BLOCK.get());
        dropSelf(ModBlocks.TITANIUM_BLOCK.get());

        // Machines : se lâchent elles-mêmes (leur contenu tombe séparément).
        dropSelf(ModBlocks.COMBUSTION_GENERATOR.get());
        dropSelf(ModBlocks.ARC_FOUNDRY.get());
        dropSelf(ModBlocks.ROLLING_MILL.get());
        dropSelf(ModBlocks.ELECTROLYZER.get());
        dropSelf(ModBlocks.CRYOGENIC_LIQUEFIER.get());
        dropSelf(ModBlocks.SABATIER_REACTOR.get());
        dropSelf(ModBlocks.HELIUM3_EXTRACTOR.get());
        dropSelf(ModBlocks.CASIMIR_CHAMBER.get());
        dropSelf(ModBlocks.FUSION_REACTOR.get());
        dropSelf(ModBlocks.REFINERY.get());
        dropSelf(ModBlocks.CRYOGENIC_TANK.get());

        // Terminal de recherche et atelier d'assemblage (ses pièces tombent séparément).

        // Blocs de test.
        dropSelf(ModBlocks.CREATIVE_ENERGY_SOURCE.get());
        dropSelf(ModBlocks.CREATIVE_FLUID_SOURCE.get());
        dropSelf(ModBlocks.ROCKET_DEBRIS.get());

        // Complexe de lancement.
        dropSelf(ModBlocks.LAUNCH_PAD.get());
        dropSelf(ModBlocks.FLAME_DEFLECTOR.get());
        dropSelf(ModBlocks.FLAME_TRENCH.get());
        dropSelf(ModBlocks.LIGHTNING_MAST.get());
        dropSelf(ModBlocks.PAD_FLOODLIGHT.get());
        dropSelf(ModBlocks.LAUNCH_TABLE.get());
        dropSelf(ModBlocks.SERVICE_TOWER.get());
        dropSelf(ModBlocks.TOWER_FRAME.get());
        dropSelf(ModBlocks.DESIGN_COMPUTER.get());
        dropSelf(ModBlocks.ENGINEER_WORKBENCH.get());
        // Ancien réservoir cryogénique, qui n'entre plus dans les plans : il rend un réservoir en composite.
        dropOther(ModBlocks.ROCKET_TANK_CRYO.get(), com.orbitalis.core.registry.ModItems.COMPOSITE_TANK.get());
        dropSelf(ModBlocks.ROCKET_MOTOR_CASING.get());
        dropSelf(ModBlocks.ROCKET_PAYLOAD_BAY.get());
        dropSelf(ModBlocks.ROCKET_FAIRING.get());
        dropSelf(ModBlocks.CREW_CABIN.get());
        dropSelf(ModBlocks.CABIN_ROOF.get());
        dropSelf(ModBlocks.ESCAPE_TOWER.get());
        dropSelf(ModBlocks.ROCKET_FUSELAGE.get());
        dropSelf(ModBlocks.ROCKET_RACEWAY.get());
        dropSelf(ModBlocks.FLIGHT_COMPUTER.get());
        dropSelf(ModBlocks.TOWER_STAIRS.get());
        dropSelf(ModBlocks.TOWER_LANDING.get());
        dropSelf(ModBlocks.TOWER_DECK.get());
        dropSelf(ModBlocks.TOWER_RAILING.get());
        dropSelf(ModBlocks.PAD_SUPPORT.get());
        dropSelf(ModBlocks.DELUGE_PIPE.get());
        dropSelf(ModBlocks.FUEL_PORT.get());
        dropSelf(ModBlocks.TOWER_ELEVATOR.get());
        // Portique d'embarquement : seule sa moitié basse rend l'objet, comme une porte.
        // Le portique rend son objet lui-même, quelle que soit celle de ses neuf cases que l'on casse.
        add(ModBlocks.BOARDING_GATE.get(), noDrop());
        dropSelf(ModBlocks.UMBILICAL_ARM.get());
        dropSelf(ModBlocks.LAUNCH_CONSOLE.get());
        dropSelf(ModBlocks.TEST_CONSOLE.get());
        dropSelf(ModBlocks.MISSION_DISPLAY.get());
        dropSelf(ModBlocks.TRACKING_STATION.get());
        dropSelf(ModBlocks.STATION_HULL.get());
        dropSelf(ModBlocks.MOON_REGOLITH.get());
        dropSelf(ModBlocks.MOON_ROCK.get());
        dropSelf(ModBlocks.LUNAR_ICE.get());
        add(ModBlocks.MOON_ILMENITE_ORE.get(), block -> createOreDrop(block, ModItems.RAW_TITANIUM.get()));
        dropSelf(ModBlocks.MARS_REGOLITH.get());
        dropSelf(ModBlocks.MARS_ROCK.get());
        dropSelf(ModBlocks.TITAN_ICE.get());
        dropSelf(ModBlocks.THOLIN_SAND.get());
        for (net.minecraft.world.level.block.Block block : java.util.List.of(ModBlocks.PROXIMA_ROCK.get(), ModBlocks.PROXIMA_REGOLITH.get(),
                ModBlocks.PROXIMA_MOSS.get(), ModBlocks.PROXIMA_ASH.get(), ModBlocks.PROXIMA_FROST.get(), ModBlocks.RED_SEDGE.get(),
                ModBlocks.UMBRA_FERN.get(), ModBlocks.LANTERN_BULB.get())) {
            dropSelf(block);
        }
        dropSelf(ModBlocks.EUROPA_ICE.get());
        dropSelf(ModBlocks.EUROPA_LINEA_ICE.get());
        dropSelf(ModBlocks.CRYOBOT.get());
        add(ModBlocks.MARS_HEMATITE_ORE.get(), block -> createOreDrop(block, net.minecraft.world.item.Items.RAW_IRON));
        dropSelf(ModBlocks.STATION_WINDOW.get());
        dropSelf(ModBlocks.FACILITY_CONCRETE.get());
        dropSelf(ModBlocks.FACILITY_CONCRETE_STAIRS.get());
        dropSelf(ModBlocks.CRACKED_FACILITY_CONCRETE.get());
        dropSelf(ModBlocks.FACILITY_TILES.get());
        dropSelf(ModBlocks.HAZARD_STRIPES.get());
        dropSelf(ModBlocks.LOUVERED_PANEL.get());
        dropSelf(ModBlocks.RADOME_PANEL.get());
        dropSelf(ModBlocks.FACILITY_GLASS.get());
        dropSelf(ModBlocks.ANTENNA_MESH.get());
        dropSelf(ModBlocks.FACILITY_SIDING.get());
        dropSelf(ModBlocks.BLUE_FACILITY_SIDING.get());
        dropSelf(ModBlocks.RUSTY_FACILITY_SIDING.get());
        add(ModBlocks.FACILITY_CONCRETE_SLAB.get(), createSlabItemTable(ModBlocks.FACILITY_CONCRETE_SLAB.get()));
        dropSelf(ModBlocks.ORANGE_FACILITY_SIDING.get());
        dropSelf(ModBlocks.SERVER_RACK.get());
        dropSelf(ModBlocks.DRAFTING_TABLE.get());
        dropSelf(ModBlocks.DOCKING_PORT.get());
        dropSelf(ModBlocks.SOLAR_PANEL.get());
        dropSelf(ModBlocks.LIFE_SUPPORT.get());
        // Aménagement des stations.
        dropSelf(ModBlocks.STATION_PANEL.get());
        dropSelf(ModBlocks.STATION_FLOOR.get());
        dropSelf(ModBlocks.STATION_LIGHT.get());
        dropSelf(ModBlocks.STATION_TRUSS.get());
        dropSelf(ModBlocks.CUPOLA_WINDOW.get());
        dropSelf(ModBlocks.STATION_TERMINAL.get());
        dropSelf(ModBlocks.SHIPYARD_CONSOLE.get());
        dropSelf(ModBlocks.RING_DRIVE.get());
        dropSelf(ModBlocks.SHIP_HELM.get());
        // Les meubles de deux blocs ne donnent leur objet que par leur moitié basse.
        add(ModBlocks.WARP_CORE.get(), this::createDoorTable);
        add(ModBlocks.HIBERNATION_POD.get(), this::createDoorTable);
        add(ModBlocks.CREW_BUNK.get(), this::createDoorTable);
        dropSelf(ModBlocks.ICE_DRILL.get());
        dropSelf(ModBlocks.OXYGEN_EXTRACTOR.get());
        // L'établi de combinaison tient sur deux cases : seule sa moitié gauche le rend.
        add(ModBlocks.SUIT_WORKBENCH.get(), block -> net.minecraft.world.level.storage.loot.LootTable.lootTable().withPool(applyExplosionCondition(block,
                net.minecraft.world.level.storage.loot.LootPool.lootPool()
                        .setRolls(net.minecraft.world.level.storage.loot.providers.number.ConstantValue.exactly(1.0F))
                        .add(net.minecraft.world.level.storage.loot.entries.LootItem.lootTableItem(block).when(
                                net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
                                        .setProperties(net.minecraft.advancements.predicates.StatePropertiesPredicate.Builder.properties()
                                                .hasProperty(com.orbitalis.phase2.survival.SuitWorkbenchBlock.RIGHT, false)))))));
        dropSelf(ModBlocks.LAB_RACK.get());
        dropSelf(ModBlocks.HYDROPONIC_TRAY.get());
        dropSelf(ModBlocks.CARGO_RACK.get());
        dropSelf(ModBlocks.STATION_BATTERY.get());
        add(ModBlocks.STATION_HATCH.get(), this::createDoorTable);
        add(ModBlocks.AIRLOCK_DOOR.get(), this::createDoorTable);
        // La porte vitrée tient sur quatre cases : seule celle du bas, à gauche, rend la porte.
        add(ModBlocks.GLASS_DOOR.get(), block -> net.minecraft.world.level.storage.loot.LootTable.lootTable().withPool(applyExplosionCondition(block, net.minecraft.world.level.storage.loot.LootPool.lootPool()
                .setRolls(net.minecraft.world.level.storage.loot.providers.number.ConstantValue.exactly(1.0F))
                .add(net.minecraft.world.level.storage.loot.entries.LootItem.lootTableItem(block).when(net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition
                        .hasBlockStateProperties(block).setProperties(net.minecraft.advancements.predicates.StatePropertiesPredicate.Builder.properties()
                                .hasProperty(com.orbitalis.phase2.station.GlassDoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER)
                                .hasProperty(com.orbitalis.phase2.station.GlassDoorBlock.RIGHT, false)))))));

        // Conduits.
        dropSelf(ModBlocks.ENERGY_CABLE.get());
        dropSelf(ModBlocks.FLUID_PIPE.get());
        dropSelf(ModBlocks.GAS_PIPE.get());
        dropSelf(ModBlocks.DATA_CABLE.get());
        dropSelf(ModBlocks.FLUID_TANK.get());
        ModBlocks.CREATIVE_TANKS.forEach(tank -> dropSelf(tank.get()));
    }

    /** Limite la vérification aux blocs d'Orbitalis : chaque bloc du mod doit avoir une loot table. */
    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ModBlocks.BLOCKS.getEntries().stream().map(Holder::value).map(Block.class::cast)::iterator;
    }
}
