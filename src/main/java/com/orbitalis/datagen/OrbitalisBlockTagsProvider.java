package com.orbitalis.datagen;

import java.util.concurrent.CompletableFuture;

import com.orbitalis.Orbitalis;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.registry.ModTags;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;

/**
 * Génère les tags de blocs : outil requis, niveau d'outil et tags communs.
 */
public class OrbitalisBlockTagsProvider extends BlockTagsProvider {
    public OrbitalisBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, Orbitalis.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        // Tous les blocs du mod se minent à la pioche.
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(
                ModBlocks.BAUXITE_ORE.getKey(), ModBlocks.DEEPSLATE_BAUXITE_ORE.getKey(),
                ModBlocks.TITANIUM_ORE.getKey(), ModBlocks.DEEPSLATE_TITANIUM_ORE.getKey(),
                ModBlocks.LITHIUM_ORE.getKey(), ModBlocks.DEEPSLATE_LITHIUM_ORE.getKey(),
                ModBlocks.STEEL_BLOCK.getKey(), ModBlocks.ALUMINUM_BLOCK.getKey(), ModBlocks.TITANIUM_BLOCK.getKey(),
                ModBlocks.COMBUSTION_GENERATOR.getKey(), ModBlocks.ARC_FOUNDRY.getKey(), ModBlocks.ROLLING_MILL.getKey(),
                ModBlocks.ELECTROLYZER.getKey(), ModBlocks.CRYOGENIC_LIQUEFIER.getKey(), ModBlocks.REFINERY.getKey(), ModBlocks.SABATIER_REACTOR.getKey(), ModBlocks.HELIUM3_EXTRACTOR.getKey(), ModBlocks.CASIMIR_CHAMBER.getKey(), ModBlocks.FUSION_REACTOR.getKey(), ModBlocks.CRYOGENIC_TANK.getKey(),
                ModBlocks.ENERGY_CABLE.getKey(), ModBlocks.FLUID_PIPE.getKey(), ModBlocks.GAS_PIPE.getKey(), ModBlocks.DATA_CABLE.getKey(), ModBlocks.FLUID_TANK.getKey(),
                ModBlocks.LAUNCH_PAD.getKey(), ModBlocks.LAUNCH_TABLE.getKey(), ModBlocks.FLAME_DEFLECTOR.getKey(), ModBlocks.FLAME_TRENCH.getKey(),
                ModBlocks.LIGHTNING_MAST.getKey(), ModBlocks.PAD_FLOODLIGHT.getKey(), ModBlocks.SERVICE_TOWER.getKey(), ModBlocks.TOWER_FRAME.getKey(), ModBlocks.ROCKET_ENGINE_SOLID.getKey(), ModBlocks.ROCKET_ENGINE_PRESSURE.getKey(), ModBlocks.ROCKET_ENGINE_KEROLOX.getKey(), ModBlocks.ROCKET_ENGINE_STAGED.getKey(), ModBlocks.ROCKET_ENGINE_METHALOX.getKey(), ModBlocks.ROCKET_ENGINE_HYDROLOX.getKey(), ModBlocks.ROCKET_TANK_STEEL.getKey(), ModBlocks.ROCKET_TANK_ALUMINUM.getKey(), ModBlocks.ROCKET_TANK_ALLOY.getKey(), ModBlocks.ROCKET_TANK_COMPOSITE.getKey(), ModBlocks.ROCKET_TANK_CRYO.getKey(), ModBlocks.ROCKET_MOTOR_CASING.getKey(), ModBlocks.ROCKET_INTERSTAGE.getKey(), ModBlocks.ROCKET_PAYLOAD_BAY.getKey(), ModBlocks.ROCKET_FAIRING.getKey(), ModBlocks.ROCKET_NOSE.getKey(), ModBlocks.CREW_CABIN.getKey(), ModBlocks.CABIN_ROOF.getKey(), ModBlocks.ESCAPE_TOWER.getKey(), ModBlocks.ROCKET_FUSELAGE.getKey(), ModBlocks.ROCKET_RACEWAY.getKey(), ModBlocks.FLIGHT_COMPUTER.getKey(), ModBlocks.DESIGN_COMPUTER.getKey(), ModBlocks.ENGINEER_WORKBENCH.getKey(), ModBlocks.TOWER_STAIRS.getKey(), ModBlocks.TOWER_LANDING.getKey(), ModBlocks.TOWER_DECK.getKey(), ModBlocks.TOWER_RAILING.getKey(), ModBlocks.PAD_SUPPORT.getKey(), ModBlocks.DELUGE_PIPE.getKey(), ModBlocks.FUEL_PORT.getKey(), ModBlocks.BOARDING_GATE.getKey(), ModBlocks.TOWER_ELEVATOR.getKey(),
                ModBlocks.UMBILICAL_ARM.getKey(), ModBlocks.LAUNCH_CONSOLE.getKey(), ModBlocks.TEST_CONSOLE.getKey(), ModBlocks.MISSION_DISPLAY.getKey(), ModBlocks.TRACKING_STATION.getKey(),
                ModBlocks.MOON_ROCK.getKey(), ModBlocks.MOON_ILMENITE_ORE.getKey(), ModBlocks.MARS_ROCK.getKey(), ModBlocks.MARS_HEMATITE_ORE.getKey(), ModBlocks.LUNAR_ICE.getKey(), ModBlocks.TITAN_ICE.getKey(), ModBlocks.EUROPA_ICE.getKey(), ModBlocks.EUROPA_LINEA_ICE.getKey(), ModBlocks.PROXIMA_ROCK.getKey(), ModBlocks.PROXIMA_FROST.getKey(), ModBlocks.CRYOBOT.getKey(), ModBlocks.STATION_HULL.getKey(), ModBlocks.STATION_WINDOW.getKey(), ModBlocks.DOCKING_PORT.getKey(),
                ModBlocks.SOLAR_PANEL.getKey(), ModBlocks.LIFE_SUPPORT.getKey(),
                ModBlocks.STATION_PANEL.getKey(), ModBlocks.STATION_FLOOR.getKey(), ModBlocks.STATION_LIGHT.getKey(), ModBlocks.STATION_TRUSS.getKey(), ModBlocks.STATION_HATCH.getKey(), ModBlocks.AIRLOCK_DOOR.getKey(), ModBlocks.GLASS_DOOR.getKey(), ModBlocks.CUPOLA_WINDOW.getKey(), ModBlocks.STATION_TERMINAL.getKey(), ModBlocks.SHIPYARD_CONSOLE.getKey(), ModBlocks.WARP_CORE.getKey(), ModBlocks.RING_DRIVE.getKey(), ModBlocks.HIBERNATION_POD.getKey(), ModBlocks.SHIP_HELM.getKey(), ModBlocks.CREW_BUNK.getKey(), ModBlocks.ICE_DRILL.getKey(), ModBlocks.OXYGEN_EXTRACTOR.getKey(), ModBlocks.SUIT_WORKBENCH.getKey(), ModBlocks.LAB_RACK.getKey(), ModBlocks.HYDROPONIC_TRAY.getKey(), ModBlocks.CARGO_RACK.getKey(), ModBlocks.STATION_BATTERY.getKey(),
                ModBlocks.CREATIVE_ENERGY_SOURCE.getKey(), ModBlocks.CREATIVE_FLUID_SOURCE.getKey(), ModBlocks.ROCKET_DEBRIS.getKey());

        tag(BlockTags.MINEABLE_WITH_SHOVEL).add(ModBlocks.MOON_REGOLITH.getKey(), ModBlocks.MARS_REGOLITH.getKey(), ModBlocks.THOLIN_SAND.getKey(),
                ModBlocks.PROXIMA_REGOLITH.getKey(), ModBlocks.PROXIMA_MOSS.getKey(), ModBlocks.PROXIMA_ASH.getKey());
        // Centres spatiaux : à la pioche, sans exigence ; la dalle et l'escalier rejoignent les leurs.
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ModBlocks.FACILITY_CONCRETE.getKey(), ModBlocks.FACILITY_CONCRETE_STAIRS.getKey(), ModBlocks.CRACKED_FACILITY_CONCRETE.getKey(), ModBlocks.FACILITY_TILES.getKey(), ModBlocks.HAZARD_STRIPES.getKey(), ModBlocks.LOUVERED_PANEL.getKey(), ModBlocks.FACILITY_GLASS.getKey(), ModBlocks.ANTENNA_MESH.getKey(), ModBlocks.FACILITY_SIDING.getKey(), ModBlocks.BLUE_FACILITY_SIDING.getKey(), ModBlocks.RUSTY_FACILITY_SIDING.getKey(), ModBlocks.FACILITY_CONCRETE_SLAB.getKey());
        tag(BlockTags.SLABS).add(ModBlocks.FACILITY_CONCRETE_SLAB.getKey());
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ModBlocks.ORANGE_FACILITY_SIDING.getKey(), ModBlocks.SERVER_RACK.getKey());
        tag(BlockTags.MINEABLE_WITH_AXE).add(ModBlocks.DRAFTING_TABLE.getKey());
        // Les équipements d'origine des centres spatiaux : démontés, ils ne rendent que de la ferraille.
        tag(com.orbitalis.core.world.FacilitySalvage.SALVAGE).add(ModBlocks.LAUNCH_PAD.getKey(), ModBlocks.FLAME_DEFLECTOR.getKey(),
                ModBlocks.FLAME_TRENCH.getKey(), ModBlocks.TOWER_FRAME.getKey(), ModBlocks.DELUGE_PIPE.getKey(), ModBlocks.PAD_FLOODLIGHT.getKey(),
                ModBlocks.SOLAR_PANEL.getKey(), ModBlocks.LAUNCH_CONSOLE.getKey(), ModBlocks.TRACKING_STATION.getKey(), ModBlocks.MISSION_DISPLAY.getKey(),
                ModBlocks.WORKSTATION_PART.getKey(), ModBlocks.STATION_LIGHT.getKey(), ModBlocks.SERVER_RACK.getKey(),
                ModBlocks.ROCKET_ENGINE_SOLID.getKey(), ModBlocks.ROCKET_ENGINE_PRESSURE.getKey(), ModBlocks.ROCKET_ENGINE_KEROLOX.getKey(),
                ModBlocks.ROCKET_ENGINE_STAGED.getKey(), ModBlocks.ROCKET_ENGINE_METHALOX.getKey(), ModBlocks.ROCKET_ENGINE_HYDROLOX.getKey(), ModBlocks.ROCKET_TANK_STEEL.getKey(),
                ModBlocks.ROCKET_TANK_ALUMINUM.getKey(), ModBlocks.ROCKET_TANK_ALLOY.getKey(), ModBlocks.ROCKET_TANK_COMPOSITE.getKey(),
                ModBlocks.ROCKET_TANK_CRYO.getKey(), ModBlocks.ROCKET_MOTOR_CASING.getKey(), ModBlocks.ROCKET_INTERSTAGE.getKey(),
                ModBlocks.ROCKET_PAYLOAD_BAY.getKey(), ModBlocks.ROCKET_FAIRING.getKey(), ModBlocks.ROCKET_NOSE.getKey());
        tag(BlockTags.STAIRS).add(ModBlocks.FACILITY_CONCRETE_STAIRS.getKey());

        // Pioche en pierre : bauxite, lithium, acier, aluminium et machines.
        tag(BlockTags.NEEDS_STONE_TOOL).add(
                ModBlocks.BAUXITE_ORE.getKey(), ModBlocks.DEEPSLATE_BAUXITE_ORE.getKey(),
                ModBlocks.LITHIUM_ORE.getKey(), ModBlocks.DEEPSLATE_LITHIUM_ORE.getKey(),
                ModBlocks.STEEL_BLOCK.getKey(), ModBlocks.ALUMINUM_BLOCK.getKey(),
                ModBlocks.COMBUSTION_GENERATOR.getKey(), ModBlocks.ARC_FOUNDRY.getKey(), ModBlocks.ROLLING_MILL.getKey(),
                ModBlocks.ELECTROLYZER.getKey(), ModBlocks.CRYOGENIC_LIQUEFIER.getKey(), ModBlocks.REFINERY.getKey(), ModBlocks.SABATIER_REACTOR.getKey(), ModBlocks.HELIUM3_EXTRACTOR.getKey(), ModBlocks.CASIMIR_CHAMBER.getKey(), ModBlocks.FUSION_REACTOR.getKey(), ModBlocks.CRYOGENIC_TANK.getKey(),
                ModBlocks.LAUNCH_PAD.getKey(), ModBlocks.LAUNCH_TABLE.getKey(), ModBlocks.FLAME_DEFLECTOR.getKey(), ModBlocks.FLAME_TRENCH.getKey(),
                ModBlocks.LIGHTNING_MAST.getKey(), ModBlocks.PAD_FLOODLIGHT.getKey(), ModBlocks.SERVICE_TOWER.getKey(), ModBlocks.TOWER_FRAME.getKey(), ModBlocks.ROCKET_ENGINE_SOLID.getKey(), ModBlocks.ROCKET_ENGINE_PRESSURE.getKey(), ModBlocks.ROCKET_ENGINE_KEROLOX.getKey(), ModBlocks.ROCKET_ENGINE_STAGED.getKey(), ModBlocks.ROCKET_ENGINE_METHALOX.getKey(), ModBlocks.ROCKET_ENGINE_HYDROLOX.getKey(), ModBlocks.ROCKET_TANK_STEEL.getKey(), ModBlocks.ROCKET_TANK_ALUMINUM.getKey(), ModBlocks.ROCKET_TANK_ALLOY.getKey(), ModBlocks.ROCKET_TANK_COMPOSITE.getKey(), ModBlocks.ROCKET_TANK_CRYO.getKey(), ModBlocks.ROCKET_MOTOR_CASING.getKey(), ModBlocks.ROCKET_INTERSTAGE.getKey(), ModBlocks.ROCKET_PAYLOAD_BAY.getKey(), ModBlocks.ROCKET_FAIRING.getKey(), ModBlocks.ROCKET_NOSE.getKey(), ModBlocks.CREW_CABIN.getKey(), ModBlocks.CABIN_ROOF.getKey(), ModBlocks.ESCAPE_TOWER.getKey(), ModBlocks.ROCKET_FUSELAGE.getKey(), ModBlocks.ROCKET_RACEWAY.getKey(), ModBlocks.FLIGHT_COMPUTER.getKey(), ModBlocks.DESIGN_COMPUTER.getKey(), ModBlocks.ENGINEER_WORKBENCH.getKey(), ModBlocks.TOWER_STAIRS.getKey(), ModBlocks.TOWER_LANDING.getKey(), ModBlocks.TOWER_DECK.getKey(), ModBlocks.TOWER_RAILING.getKey(), ModBlocks.PAD_SUPPORT.getKey(), ModBlocks.DELUGE_PIPE.getKey(), ModBlocks.FUEL_PORT.getKey(), ModBlocks.BOARDING_GATE.getKey(), ModBlocks.TOWER_ELEVATOR.getKey(),
                ModBlocks.UMBILICAL_ARM.getKey(), ModBlocks.LAUNCH_CONSOLE.getKey(), ModBlocks.TEST_CONSOLE.getKey(), ModBlocks.MISSION_DISPLAY.getKey(), ModBlocks.TRACKING_STATION.getKey(),
                ModBlocks.STATION_HULL.getKey(), ModBlocks.STATION_WINDOW.getKey(), ModBlocks.DOCKING_PORT.getKey(),
                ModBlocks.SOLAR_PANEL.getKey(), ModBlocks.LIFE_SUPPORT.getKey(),
                ModBlocks.STATION_PANEL.getKey(), ModBlocks.STATION_FLOOR.getKey(), ModBlocks.STATION_LIGHT.getKey(), ModBlocks.STATION_TRUSS.getKey(), ModBlocks.STATION_HATCH.getKey(), ModBlocks.AIRLOCK_DOOR.getKey(), ModBlocks.CUPOLA_WINDOW.getKey(), ModBlocks.STATION_TERMINAL.getKey(), ModBlocks.SHIPYARD_CONSOLE.getKey(), ModBlocks.WARP_CORE.getKey(), ModBlocks.RING_DRIVE.getKey(), ModBlocks.HIBERNATION_POD.getKey(), ModBlocks.SHIP_HELM.getKey(), ModBlocks.CREW_BUNK.getKey(), ModBlocks.ICE_DRILL.getKey(), ModBlocks.OXYGEN_EXTRACTOR.getKey(), ModBlocks.SUIT_WORKBENCH.getKey(), ModBlocks.LAB_RACK.getKey(), ModBlocks.HYDROPONIC_TRAY.getKey(), ModBlocks.CARGO_RACK.getKey(), ModBlocks.STATION_BATTERY.getKey());

        // Pioche en fer : titane.
        tag(BlockTags.NEEDS_IRON_TOOL).add(
                ModBlocks.TITANIUM_ORE.getKey(), ModBlocks.DEEPSLATE_TITANIUM_ORE.getKey(),
                ModBlocks.TITANIUM_BLOCK.getKey());

        // Tags communs : minerais.
        tag(ModTags.Blocks.ORES_BAUXITE).add(ModBlocks.BAUXITE_ORE.getKey(), ModBlocks.DEEPSLATE_BAUXITE_ORE.getKey());
        tag(ModTags.Blocks.ORES_TITANIUM).add(ModBlocks.TITANIUM_ORE.getKey(), ModBlocks.DEEPSLATE_TITANIUM_ORE.getKey());
        tag(ModTags.Blocks.ORES_LITHIUM).add(ModBlocks.LITHIUM_ORE.getKey(), ModBlocks.DEEPSLATE_LITHIUM_ORE.getKey());
        tag(Tags.Blocks.ORES).addTags(ModTags.Blocks.ORES_BAUXITE, ModTags.Blocks.ORES_TITANIUM, ModTags.Blocks.ORES_LITHIUM);
        tag(Tags.Blocks.ORES_IN_GROUND_STONE).add(
                ModBlocks.BAUXITE_ORE.getKey(), ModBlocks.TITANIUM_ORE.getKey(), ModBlocks.LITHIUM_ORE.getKey());
        tag(Tags.Blocks.ORES_IN_GROUND_DEEPSLATE).add(
                ModBlocks.DEEPSLATE_BAUXITE_ORE.getKey(), ModBlocks.DEEPSLATE_TITANIUM_ORE.getKey(),
                ModBlocks.DEEPSLATE_LITHIUM_ORE.getKey());

        // Tags communs : blocs de stockage.
        tag(ModTags.Blocks.STORAGE_BLOCKS_STEEL).add(ModBlocks.STEEL_BLOCK.getKey());
        tag(ModTags.Blocks.STORAGE_BLOCKS_ALUMINUM).add(ModBlocks.ALUMINUM_BLOCK.getKey());
        tag(ModTags.Blocks.STORAGE_BLOCKS_TITANIUM).add(ModBlocks.TITANIUM_BLOCK.getKey());
        tag(Tags.Blocks.STORAGE_BLOCKS).addTags(ModTags.Blocks.STORAGE_BLOCKS_STEEL,
                ModTags.Blocks.STORAGE_BLOCKS_ALUMINUM, ModTags.Blocks.STORAGE_BLOCKS_TITANIUM);

        // Les blocs de métal peuvent servir de base à une balise.
        tag(BlockTags.BEACON_BASE_BLOCKS).add(
                ModBlocks.STEEL_BLOCK.getKey(), ModBlocks.ALUMINUM_BLOCK.getKey(), ModBlocks.TITANIUM_BLOCK.getKey());
    }
}
