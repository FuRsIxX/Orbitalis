package com.orbitalis.core.registry;

import java.util.List;

import com.orbitalis.Orbitalis;
import com.orbitalis.phase1.conduit.CableEnergyHandler;
import com.orbitalis.phase1.conduit.DataLink;
import com.orbitalis.phase1.fuel.FluidTankBlockEntity;
import com.orbitalis.phase1.conduit.PipeFluidHandler;
import com.orbitalis.phase1.fuel.CryogenicTankBlockEntity;
import com.orbitalis.phase1.fuel.FluidMachineBlockEntity;
import com.orbitalis.phase1.fuel.FluidMachineLayout;
import com.orbitalis.core.creative.CreativeSourceBlockEntity;
import com.orbitalis.phase1.machines.CombustionGeneratorBlockEntity;
import com.orbitalis.phase1.spaceport.LaunchTableBlockEntity;
import com.orbitalis.phase1.spaceport.MissionDisplayBlock;
import com.orbitalis.phase1.spaceport.MissionDisplayBlockEntity;
import com.orbitalis.phase1.spaceport.UmbilicalFluidHandler;
import com.orbitalis.phase2.station.SolarPanelBlockEntity;
import com.orbitalis.phase2.station.LifeSupportBlockEntity;
import com.orbitalis.phase1.machines.ProcessingMachineBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registre des entités de bloc (blocs qui stockent des données et s'exécutent à chaque tick).
 */
@EventBusSubscriber(modid = Orbitalis.MODID)
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Orbitalis.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CombustionGeneratorBlockEntity>> COMBUSTION_GENERATOR =
            BLOCK_ENTITIES.register("combustion_generator",
                    () -> new BlockEntityType<>(CombustionGeneratorBlockEntity::new, ModBlocks.COMBUSTION_GENERATOR.get()));

    /** Fonderie à arc : deux entrées, recettes d'alliage. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProcessingMachineBlockEntity>> ARC_FOUNDRY =
            BLOCK_ENTITIES.register("arc_foundry", () -> new BlockEntityType<>(
                    (pos, state) -> new ProcessingMachineBlockEntity(ModBlockEntities.ARC_FOUNDRY.get(), pos, state,
                            ModRecipes.ALLOYING_TYPE, 2),
                    ModBlocks.ARC_FOUNDRY.get()));

    /** Laminoir : une entrée, recettes de laminage. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProcessingMachineBlockEntity>> ROLLING_MILL =
            BLOCK_ENTITIES.register("rolling_mill", () -> new BlockEntityType<>(
                    (pos, state) -> new ProcessingMachineBlockEntity(ModBlockEntities.ROLLING_MILL.get(), pos, state,
                            ModRecipes.ROLLING_TYPE, 1),
                    ModBlocks.ROLLING_MILL.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidMachineBlockEntity>> ELECTROLYZER =
            BLOCK_ENTITIES.register("electrolyzer", () -> new BlockEntityType<>(
                    (pos, state) -> new FluidMachineBlockEntity(ModBlockEntities.ELECTROLYZER.get(), pos, state,
                            FluidMachineLayout.ELECTROLYZER, ModRecipes.ELECTROLYSIS_TYPE),
                    ModBlocks.ELECTROLYZER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidMachineBlockEntity>> CRYOGENIC_LIQUEFIER =
            BLOCK_ENTITIES.register("cryogenic_liquefier", () -> new BlockEntityType<>(
                    (pos, state) -> new FluidMachineBlockEntity(ModBlockEntities.CRYOGENIC_LIQUEFIER.get(), pos, state,
                            FluidMachineLayout.CRYOGENIC_LIQUEFIER, ModRecipes.LIQUEFACTION_TYPE),
                    ModBlocks.CRYOGENIC_LIQUEFIER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidMachineBlockEntity>> REFINERY =
            BLOCK_ENTITIES.register("refinery", () -> new BlockEntityType<>(
                    (pos, state) -> new FluidMachineBlockEntity(ModBlockEntities.REFINERY.get(), pos, state,
                            FluidMachineLayout.REFINERY, ModRecipes.REFINING_TYPE),
                    ModBlocks.REFINERY.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidMachineBlockEntity>> HELIUM3_EXTRACTOR =
            BLOCK_ENTITIES.register("helium3_extractor", () -> new BlockEntityType<>(
                    (pos, state) -> new FluidMachineBlockEntity(ModBlockEntities.HELIUM3_EXTRACTOR.get(), pos, state,
                            FluidMachineLayout.HELIUM3_EXTRACTOR, ModRecipes.HELIUM3_TYPE),
                    ModBlocks.HELIUM3_EXTRACTOR.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidMachineBlockEntity>> SABATIER_REACTOR =
            BLOCK_ENTITIES.register("sabatier_reactor", () -> new BlockEntityType<>(
                    (pos, state) -> new FluidMachineBlockEntity(ModBlockEntities.SABATIER_REACTOR.get(), pos, state,
                            FluidMachineLayout.SABATIER_REACTOR, ModRecipes.SABATIER_TYPE),
                    ModBlocks.SABATIER_REACTOR.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CryogenicTankBlockEntity>> CRYOGENIC_TANK =
            BLOCK_ENTITIES.register("cryogenic_tank",
                    () -> new BlockEntityType<>(CryogenicTankBlockEntity::new, ModBlocks.CRYOGENIC_TANK.get()));

    /** Table de lancement : surveille le complexe qui l'entoure. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaunchTableBlockEntity>> LAUNCH_TABLE =
            BLOCK_ENTITIES.register("launch_table",
                    () -> new BlockEntityType<>(LaunchTableBlockEntity::new, ModBlocks.LAUNCH_TABLE.get()));

    /** Sources créatives d'énergie et de fluide. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CreativeSourceBlockEntity>> CREATIVE_SOURCE =
            BLOCK_ENTITIES.register("creative_source", () -> new BlockEntityType<>(CreativeSourceBlockEntity::new,
                    java.util.stream.Stream.concat(
                            java.util.stream.Stream.of(ModBlocks.CREATIVE_ENERGY_SOURCE.get(), ModBlocks.CREATIVE_FLUID_SOURCE.get()),
                            ModBlocks.CREATIVE_TANKS.stream().map(tank -> tank.get())).collect(java.util.stream.Collectors.toSet())));

    /** Réservoir modulable. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidTankBlockEntity>> FLUID_TANK =
            BLOCK_ENTITIES.register("fluid_tank", () -> new BlockEntityType<>(FluidTankBlockEntity::new, ModBlocks.FLUID_TANK.get()));

    /** Écran de contrôle : sert au rendu du grand écran sur un mur d'écrans. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MissionDisplayBlockEntity>> MISSION_DISPLAY =
            BLOCK_ENTITIES.register("mission_display",
                    () -> new BlockEntityType<>(MissionDisplayBlockEntity::new, ModBlocks.MISSION_DISPLAY.get()));

    /** Panneau solaire. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SolarPanelBlockEntity>> SOLAR_PANEL =
            BLOCK_ENTITIES.register("solar_panel", () -> new BlockEntityType<>(SolarPanelBlockEntity::new, ModBlocks.SOLAR_PANEL.get()));

    /** Générateur d'air des stations. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LifeSupportBlockEntity>> LIFE_SUPPORT =
            BLOCK_ENTITIES.register("life_support", () -> new BlockEntityType<>(LifeSupportBlockEntity::new, ModBlocks.LIFE_SUPPORT.get()));

    /** Équipements des stations : baie d'expériences, bac de culture, casier de fret, batterie. */
    /** Le plan étalé sur une table à dessin. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.orbitalis.phase1.rocket.DraftingTableBlockEntity>> DRAFTING_TABLE =
            BLOCK_ENTITIES.register("drafting_table", () -> new BlockEntityType<>(com.orbitalis.phase1.rocket.DraftingTableBlockEntity::new,
                    ModBlocks.DRAFTING_TABLE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.orbitalis.phase2.station.LabRackBlockEntity>> LAB_RACK =
            BLOCK_ENTITIES.register("lab_rack", () -> new BlockEntityType<>(com.orbitalis.phase2.station.LabRackBlockEntity::new, ModBlocks.LAB_RACK.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.orbitalis.phase2.station.HydroponicTrayBlockEntity>> HYDROPONIC_TRAY =
            BLOCK_ENTITIES.register("hydroponic_tray",
                    () -> new BlockEntityType<>(com.orbitalis.phase2.station.HydroponicTrayBlockEntity::new, ModBlocks.HYDROPONIC_TRAY.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.orbitalis.phase2.station.CargoRackBlockEntity>> CARGO_RACK =
            BLOCK_ENTITIES.register("cargo_rack", () -> new BlockEntityType<>(com.orbitalis.phase2.station.CargoRackBlockEntity::new, ModBlocks.CARGO_RACK.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.orbitalis.phase2.station.StationBatteryBlockEntity>> STATION_BATTERY =
            BLOCK_ENTITIES.register("station_battery",
                    () -> new BlockEntityType<>(com.orbitalis.phase2.station.StationBatteryBlockEntity::new, ModBlocks.STATION_BATTERY.get()));

    /** Porte de sas : son cycle de mise à niveau, et le coulissement de ses battants. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.orbitalis.phase2.station.AirlockDoorBlockEntity>> AIRLOCK_DOOR =
            BLOCK_ENTITIES.register("airlock_door",
                    () -> new BlockEntityType<>(com.orbitalis.phase2.station.AirlockDoorBlockEntity::new, ModBlocks.AIRLOCK_DOOR.get()));

    /** Porte vitrée coulissante : le coulissement de ses battants. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.orbitalis.phase2.station.GlassDoorBlockEntity>> GLASS_DOOR =
            BLOCK_ENTITIES.register("glass_door",
                    () -> new BlockEntityType<>(com.orbitalis.phase2.station.GlassDoorBlockEntity::new, ModBlocks.GLASS_DOOR.get()));

    /** Machines de production sur place : la foreuse à glace et l'extracteur d'oxygène. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.orbitalis.phase3.exploration.IsruBlockEntity>> ISRU =
            BLOCK_ENTITIES.register("isru", () -> new BlockEntityType<>(com.orbitalis.phase3.exploration.IsruBlockEntity::new,
                    ModBlocks.ICE_DRILL.get(), ModBlocks.OXYGEN_EXTRACTOR.get()));

    /** Cryobot : la sonde foreuse d'Europe. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.orbitalis.phase3.exploration.CryobotBlockEntity>> CRYOBOT =
            BLOCK_ENTITIES.register("cryobot", () -> new BlockEntityType<>(com.orbitalis.phase3.exploration.CryobotBlockEntity::new,
                    ModBlocks.CRYOBOT.get()));

    /** Pupitre du chantier naval. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.orbitalis.phase3.interstellar.ShipyardConsoleBlockEntity>> SHIPYARD_CONSOLE =
            BLOCK_ENTITIES.register("shipyard_console", () -> new BlockEntityType<>(com.orbitalis.phase3.interstellar.ShipyardConsoleBlockEntity::new,
                    ModBlocks.SHIPYARD_CONSOLE.get()));

    /** Chambre de Casimir : une entrée, la matière exotique. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProcessingMachineBlockEntity>> CASIMIR_CHAMBER =
            BLOCK_ENTITIES.register("casimir_chamber", () -> new BlockEntityType<>(
                    (pos, state) -> new ProcessingMachineBlockEntity(ModBlockEntities.CASIMIR_CHAMBER.get(), pos, state,
                            ModRecipes.CASIMIR_TYPE, 1),
                    ModBlocks.CASIMIR_CHAMBER.get()));

    /** Réacteur à fusion. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.orbitalis.phase3.propulsion.FusionReactorBlockEntity>> FUSION_REACTOR =
            BLOCK_ENTITIES.register("fusion_reactor", () -> new BlockEntityType<>(com.orbitalis.phase3.propulsion.FusionReactorBlockEntity::new,
                    ModBlocks.FUSION_REACTOR.get()));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }

    /**
     * Expose l'énergie et l'inventaire des machines aux autres blocs (générateurs, câbles, entonnoirs,
     * tuyaux d'autres mods), sur toutes les faces.
     */
    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, COMBUSTION_GENERATOR.get(), (machine, side) -> machine.getEnergy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ARC_FOUNDRY.get(), (machine, side) -> machine.getEnergy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ROLLING_MILL.get(), (machine, side) -> machine.getEnergy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, CASIMIR_CHAMBER.get(), (machine, side) -> machine.getEnergy());

        event.registerBlockEntity(Capabilities.Item.BLOCK, COMBUSTION_GENERATOR.get(), (machine, side) -> machine.getInventory().automationView());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ARC_FOUNDRY.get(), (machine, side) -> machine.getInventory().automationView());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ROLLING_MILL.get(), (machine, side) -> machine.getInventory().automationView());
        event.registerBlockEntity(Capabilities.Item.BLOCK, CASIMIR_CHAMBER.get(), (machine, side) -> machine.getInventory().automationView());

        // Machines à fluides et réservoir cryogénique.
        for (var type : List.of(ELECTROLYZER.get(), CRYOGENIC_LIQUEFIER.get(), REFINERY.get(), SABATIER_REACTOR.get(), HELIUM3_EXTRACTOR.get())) {
            event.registerBlockEntity(Capabilities.Energy.BLOCK, type, (machine, side) -> machine.getEnergy());
            event.registerBlockEntity(Capabilities.Item.BLOCK, type, (machine, side) -> machine.getInventory().automationView());
            event.registerBlockEntity(Capabilities.Fluid.BLOCK, type, (machine, side) -> machine.getTanks().automationView());
        }
        event.registerBlockEntity(Capabilities.Energy.BLOCK, CRYOGENIC_TANK.get(), (tank, side) -> tank.getEnergy());
        // Réacteur à fusion : il ne fait que donner son électricité, et ne prend que ses deux gaz.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, FUSION_REACTOR.get(), (reactor, side) -> reactor.getEnergy());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, FUSION_REACTOR.get(), (reactor, side) -> reactor.getTanks().automationView());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, CRYOGENIC_TANK.get(), (tank, side) -> tank.getTank().automationView());

        // Réservoir modulable : quel que soit le bloc touché, on atteint le contenu du réservoir entier.
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, FLUID_TANK.get(), (tank, side) -> tank.handler());

        // Prises de données : ce qu'un câble de données peut relier. Les écrans y lisent les ordinateurs et les réservoirs.
        // Un écran n'a de prise qu'au dos : le câble arrive par le mur, jamais par la tranche ni par la vitre.
        event.registerBlock(DataLink.BLOCK, (level, pos, state, blockEntity, side) ->
                side == null || side == state.getValue(MissionDisplayBlock.FACING).getOpposite() ? DataLink.PORT : null,
                ModBlocks.MISSION_DISPLAY.get());
        event.registerBlock(DataLink.BLOCK, (level, pos, state, blockEntity, side) -> DataLink.PORT,
                ModBlocks.LAUNCH_CONSOLE.get(), ModBlocks.TRACKING_STATION.get(),
                ModBlocks.WORKSTATION_PART.get(), ModBlocks.FLUID_TANK.get(), ModBlocks.CRYOGENIC_TANK.get());

        // Stations : le panneau solaire fournit de l'électricité, le générateur d'air en reçoit.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SOLAR_PANEL.get(), (panel, side) -> panel.getEnergy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, LIFE_SUPPORT.get(), (unit, side) -> unit.getEnergy());
        // Équipements de station : ils se branchent aussi par câble, hors d'une station ou en renfort de son réseau.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, LAB_RACK.get(), (rack, side) -> rack.getEnergy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, HYDROPONIC_TRAY.get(), (tray, side) -> tray.getEnergy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, STATION_BATTERY.get(), (battery, side) -> battery.getEnergy());

        // Blocs de ravitaillement, au pied de la tour : l'ergol qu'on leur envoie est chargé dans la fusée de la table
        // de lancement voisine. Chacun ne laisse passer que le sien.
        event.registerBlock(Capabilities.Fluid.BLOCK,
                (level, pos, state, blockEntity, side) -> new UmbilicalFluidHandler(level, pos),
                ModBlocks.FUEL_PORT.get());

        // Conduits : ils n'ont pas d'entité de bloc ; ce qu'on leur envoie est livré au réseau auquel ils appartiennent.
        event.registerBlock(Capabilities.Energy.BLOCK,
                (level, pos, state, blockEntity, side) -> new CableEnergyHandler(level, pos, state.getBlock()),
                ModBlocks.ENERGY_CABLE.get());
        event.registerBlock(Capabilities.Fluid.BLOCK,
                (level, pos, state, blockEntity, side) -> new PipeFluidHandler(level, pos, state.getBlock()),
                ModBlocks.FLUID_PIPE.get(), ModBlocks.GAS_PIPE.get());
    }
}
