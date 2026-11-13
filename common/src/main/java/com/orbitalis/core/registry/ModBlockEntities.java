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
import com.orbitalis.platform.capability.Capabilities;
import com.orbitalis.platform.capability.RegisterCapabilitiesEvent;
import com.orbitalis.platform.registry.DeferredHolder;
import com.orbitalis.platform.registry.DeferredRegister;

/**
 * Registre des entités de bloc (blocs qui stockent des données et s'exécutent à chaque tick).
 */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Orbitalis.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CombustionGeneratorBlockEntity>> COMBUSTION_GENERATOR =
            BLOCK_ENTITIES.register("combustion_generator",
                    () -> com.orbitalis.platform.Compat.blockEntityType(CombustionGeneratorBlockEntity::new, ModBlocks.COMBUSTION_GENERATOR.get()));

    /** Fonderie à arc : deux entrées, recettes d'alliage. */
    /** Fours électriques : le four à induction, et le four de fusion qui double les minerais. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.orbitalis.phase1.machines.FurnaceMachineBlockEntity>> INDUCTION_FURNACE =
            BLOCK_ENTITIES.register("induction_furnace", () -> com.orbitalis.platform.Compat.blockEntityType(
                    (pos, state) -> new com.orbitalis.phase1.machines.FurnaceMachineBlockEntity(ModBlockEntities.INDUCTION_FURNACE.get(), pos, state, false),
                    ModBlocks.INDUCTION_FURNACE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.orbitalis.phase1.machines.FurnaceMachineBlockEntity>> MELTING_FURNACE =
            BLOCK_ENTITIES.register("melting_furnace", () -> com.orbitalis.platform.Compat.blockEntityType(
                    (pos, state) -> new com.orbitalis.phase1.machines.FurnaceMachineBlockEntity(ModBlockEntities.MELTING_FURNACE.get(), pos, state, true),
                    ModBlocks.MELTING_FURNACE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProcessingMachineBlockEntity>> ARC_FOUNDRY =
            BLOCK_ENTITIES.register("arc_foundry", () -> com.orbitalis.platform.Compat.blockEntityType(
                    (pos, state) -> new ProcessingMachineBlockEntity(ModBlockEntities.ARC_FOUNDRY.get(), pos, state,
                            ModRecipes.ALLOYING_TYPE, 2),
                    ModBlocks.ARC_FOUNDRY.get()));

    /** Laminoir : une entrée, recettes de laminage. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProcessingMachineBlockEntity>> ROLLING_MILL =
            BLOCK_ENTITIES.register("rolling_mill", () -> com.orbitalis.platform.Compat.blockEntityType(
                    (pos, state) -> new ProcessingMachineBlockEntity(ModBlockEntities.ROLLING_MILL.get(), pos, state,
                            ModRecipes.ROLLING_TYPE, 1),
                    ModBlocks.ROLLING_MILL.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidMachineBlockEntity>> ELECTROLYZER =
            BLOCK_ENTITIES.register("electrolyzer", () -> com.orbitalis.platform.Compat.blockEntityType(
                    (pos, state) -> new FluidMachineBlockEntity(ModBlockEntities.ELECTROLYZER.get(), pos, state,
                            FluidMachineLayout.ELECTROLYZER, ModRecipes.ELECTROLYSIS_TYPE),
                    ModBlocks.ELECTROLYZER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidMachineBlockEntity>> CRYOGENIC_LIQUEFIER =
            BLOCK_ENTITIES.register("cryogenic_liquefier", () -> com.orbitalis.platform.Compat.blockEntityType(
                    (pos, state) -> new FluidMachineBlockEntity(ModBlockEntities.CRYOGENIC_LIQUEFIER.get(), pos, state,
                            FluidMachineLayout.CRYOGENIC_LIQUEFIER, ModRecipes.LIQUEFACTION_TYPE),
                    ModBlocks.CRYOGENIC_LIQUEFIER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidMachineBlockEntity>> REFINERY =
            BLOCK_ENTITIES.register("refinery", () -> com.orbitalis.platform.Compat.blockEntityType(
                    (pos, state) -> new FluidMachineBlockEntity(ModBlockEntities.REFINERY.get(), pos, state,
                            FluidMachineLayout.REFINERY, ModRecipes.REFINING_TYPE),
                    ModBlocks.REFINERY.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidMachineBlockEntity>> HELIUM3_EXTRACTOR =
            BLOCK_ENTITIES.register("helium3_extractor", () -> com.orbitalis.platform.Compat.blockEntityType(
                    (pos, state) -> new FluidMachineBlockEntity(ModBlockEntities.HELIUM3_EXTRACTOR.get(), pos, state,
                            FluidMachineLayout.HELIUM3_EXTRACTOR, ModRecipes.HELIUM3_TYPE),
                    ModBlocks.HELIUM3_EXTRACTOR.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidMachineBlockEntity>> SABATIER_REACTOR =
            BLOCK_ENTITIES.register("sabatier_reactor", () -> com.orbitalis.platform.Compat.blockEntityType(
                    (pos, state) -> new FluidMachineBlockEntity(ModBlockEntities.SABATIER_REACTOR.get(), pos, state,
                            FluidMachineLayout.SABATIER_REACTOR, ModRecipes.SABATIER_TYPE),
                    ModBlocks.SABATIER_REACTOR.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CryogenicTankBlockEntity>> CRYOGENIC_TANK =
            BLOCK_ENTITIES.register("cryogenic_tank",
                    () -> com.orbitalis.platform.Compat.blockEntityType(CryogenicTankBlockEntity::new, ModBlocks.CRYOGENIC_TANK.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.orbitalis.phase1.fuel.WaterPumpBlockEntity>> WATER_PUMP =
            BLOCK_ENTITIES.register("water_pump",
                    () -> com.orbitalis.platform.Compat.blockEntityType(com.orbitalis.phase1.fuel.WaterPumpBlockEntity::new, ModBlocks.WATER_PUMP.get()));

    /** Ordinateur de conception : il garde le brouillon qu'on y règle. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.orbitalis.phase1.rocket.DesignComputerBlockEntity>> DESIGN_COMPUTER =
            BLOCK_ENTITIES.register("design_computer",
                    () -> com.orbitalis.platform.Compat.blockEntityType(com.orbitalis.phase1.rocket.DesignComputerBlockEntity::new, ModBlocks.DESIGN_COMPUTER.get()));

    /** Derrick : la tour de forage et son travail. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.orbitalis.phase1.quarry.DerrickBlockEntity>> DRILL_DERRICK =
            BLOCK_ENTITIES.register("drill_derrick",
                    () -> com.orbitalis.platform.Compat.blockEntityType(com.orbitalis.phase1.quarry.DerrickBlockEntity::new, ModBlocks.DRILL_DERRICK.get()));

    /** Table de lancement : surveille le complexe qui l'entoure. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaunchTableBlockEntity>> LAUNCH_TABLE =
            BLOCK_ENTITIES.register("launch_table",
                    () -> com.orbitalis.platform.Compat.blockEntityType(LaunchTableBlockEntity::new, ModBlocks.LAUNCH_TABLE.get()));

    /** Sources créatives d'énergie et de fluide. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CreativeSourceBlockEntity>> CREATIVE_SOURCE =
            BLOCK_ENTITIES.register("creative_source", () -> com.orbitalis.platform.Compat.blockEntityType(CreativeSourceBlockEntity::new,
                    java.util.stream.Stream.concat(
                            java.util.stream.Stream.of(ModBlocks.CREATIVE_ENERGY_SOURCE.get(), ModBlocks.CREATIVE_FLUID_SOURCE.get()),
                            ModBlocks.CREATIVE_TANKS.stream().map(tank -> tank.get())).collect(java.util.stream.Collectors.toSet())));

    /** Réservoir modulable. */
    /** Couleur des tuyaux à liquides et à gaz. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.orbitalis.phase1.conduit.PipeBlockEntity>> PIPE =
            BLOCK_ENTITIES.register("pipe", () -> com.orbitalis.platform.Compat.blockEntityType(com.orbitalis.phase1.conduit.PipeBlockEntity::new,
                    ModBlocks.FLUID_PIPE.get(), ModBlocks.GAS_PIPE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidTankBlockEntity>> FLUID_TANK =
            BLOCK_ENTITIES.register("fluid_tank", () -> com.orbitalis.platform.Compat.blockEntityType(FluidTankBlockEntity::new, ModBlocks.FLUID_TANK.get()));

    /** Écran de contrôle : sert au rendu du grand écran sur un mur d'écrans. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MissionDisplayBlockEntity>> MISSION_DISPLAY =
            BLOCK_ENTITIES.register("mission_display",
                    () -> com.orbitalis.platform.Compat.blockEntityType(MissionDisplayBlockEntity::new, ModBlocks.MISSION_DISPLAY.get()));

    /** Panneau solaire. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SolarPanelBlockEntity>> SOLAR_PANEL =
            BLOCK_ENTITIES.register("solar_panel", () -> com.orbitalis.platform.Compat.blockEntityType(SolarPanelBlockEntity::new, ModBlocks.SOLAR_PANEL.get(),
                    ModBlocks.SOLAR_PANEL_ADVANCED.get(), ModBlocks.SOLAR_PANEL_PEROVSKITE.get(), ModBlocks.SOLAR_PANEL_QUANTUM.get()));

    /** Accumulateurs, de leurs quatre générations. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.orbitalis.phase1.power.AccumulatorBlockEntity>> ACCUMULATOR =
            BLOCK_ENTITIES.register("accumulator", () -> com.orbitalis.platform.Compat.blockEntityType(com.orbitalis.phase1.power.AccumulatorBlockEntity::new,
                    ModBlocks.ACCUMULATOR.get(), ModBlocks.ACCUMULATOR_ADVANCED.get(), ModBlocks.ACCUMULATOR_ELITE.get(),
                    ModBlocks.ACCUMULATOR_QUANTUM.get()));

    /** Générateur d'air des stations. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LifeSupportBlockEntity>> LIFE_SUPPORT =
            BLOCK_ENTITIES.register("life_support", () -> com.orbitalis.platform.Compat.blockEntityType(LifeSupportBlockEntity::new, ModBlocks.LIFE_SUPPORT.get()));

    /** Équipements des stations : baie d'expériences, bac de culture, casier de fret, batterie. */
    /** Le plan étalé sur une table à dessin. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.orbitalis.phase1.rocket.DraftingTableBlockEntity>> DRAFTING_TABLE =
            BLOCK_ENTITIES.register("drafting_table", () -> com.orbitalis.platform.Compat.blockEntityType(com.orbitalis.phase1.rocket.DraftingTableBlockEntity::new,
                    ModBlocks.DRAFTING_TABLE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.orbitalis.phase2.station.LabRackBlockEntity>> LAB_RACK =
            BLOCK_ENTITIES.register("lab_rack", () -> com.orbitalis.platform.Compat.blockEntityType(com.orbitalis.phase2.station.LabRackBlockEntity::new, ModBlocks.LAB_RACK.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.orbitalis.phase2.station.HydroponicTrayBlockEntity>> HYDROPONIC_TRAY =
            BLOCK_ENTITIES.register("hydroponic_tray",
                    () -> com.orbitalis.platform.Compat.blockEntityType(com.orbitalis.phase2.station.HydroponicTrayBlockEntity::new, ModBlocks.HYDROPONIC_TRAY.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.orbitalis.phase2.station.CargoRackBlockEntity>> CARGO_RACK =
            BLOCK_ENTITIES.register("cargo_rack", () -> com.orbitalis.platform.Compat.blockEntityType(com.orbitalis.phase2.station.CargoRackBlockEntity::new, ModBlocks.CARGO_RACK.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.orbitalis.phase2.station.StationBatteryBlockEntity>> STATION_BATTERY =
            BLOCK_ENTITIES.register("station_battery",
                    () -> com.orbitalis.platform.Compat.blockEntityType(com.orbitalis.phase2.station.StationBatteryBlockEntity::new, ModBlocks.STATION_BATTERY.get()));

    /** Porte de sas : son cycle de mise à niveau, et le coulissement de ses battants. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.orbitalis.phase2.station.AirlockDoorBlockEntity>> AIRLOCK_DOOR =
            BLOCK_ENTITIES.register("airlock_door",
                    () -> com.orbitalis.platform.Compat.blockEntityType(com.orbitalis.phase2.station.AirlockDoorBlockEntity::new, ModBlocks.AIRLOCK_DOOR.get()));

    /** Porte vitrée coulissante : le coulissement de ses battants. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.orbitalis.phase2.station.GlassDoorBlockEntity>> GLASS_DOOR =
            BLOCK_ENTITIES.register("glass_door",
                    () -> com.orbitalis.platform.Compat.blockEntityType(com.orbitalis.phase2.station.GlassDoorBlockEntity::new, ModBlocks.GLASS_DOOR.get()));

    /** Machines de production sur place : la foreuse à glace et l'extracteur d'oxygène. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.orbitalis.phase3.exploration.IsruBlockEntity>> ISRU =
            BLOCK_ENTITIES.register("isru", () -> com.orbitalis.platform.Compat.blockEntityType(com.orbitalis.phase3.exploration.IsruBlockEntity::new,
                    ModBlocks.ICE_DRILL.get(), ModBlocks.OXYGEN_EXTRACTOR.get()));

    /** Cryobot : la sonde foreuse d'Europe. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.orbitalis.phase3.exploration.CryobotBlockEntity>> CRYOBOT =
            BLOCK_ENTITIES.register("cryobot", () -> com.orbitalis.platform.Compat.blockEntityType(com.orbitalis.phase3.exploration.CryobotBlockEntity::new,
                    ModBlocks.CRYOBOT.get()));

    /** Pupitre du chantier naval. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.orbitalis.phase3.interstellar.ShipyardConsoleBlockEntity>> SHIPYARD_CONSOLE =
            BLOCK_ENTITIES.register("shipyard_console", () -> com.orbitalis.platform.Compat.blockEntityType(com.orbitalis.phase3.interstellar.ShipyardConsoleBlockEntity::new,
                    ModBlocks.SHIPYARD_CONSOLE.get()));

    /** Moteur de l'anneau du vaisseau : sa marche, et l'anneau qu'il fait tourner. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.orbitalis.phase3.interstellar.RingDriveBlockEntity>> RING_DRIVE =
            BLOCK_ENTITIES.register("ring_drive", () -> com.orbitalis.platform.Compat.blockEntityType(com.orbitalis.phase3.interstellar.RingDriveBlockEntity::new,
                    ModBlocks.RING_DRIVE.get()));

    /** Chambre de Casimir : une entrée, la matière exotique. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProcessingMachineBlockEntity>> CASIMIR_CHAMBER =
            BLOCK_ENTITIES.register("casimir_chamber", () -> com.orbitalis.platform.Compat.blockEntityType(
                    (pos, state) -> new ProcessingMachineBlockEntity(ModBlockEntities.CASIMIR_CHAMBER.get(), pos, state,
                            ModRecipes.CASIMIR_TYPE, 1),
                    ModBlocks.CASIMIR_CHAMBER.get()));

    /** Réacteur à fusion. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.orbitalis.phase3.propulsion.FusionReactorBlockEntity>> FUSION_REACTOR =
            BLOCK_ENTITIES.register("fusion_reactor", () -> com.orbitalis.platform.Compat.blockEntityType(com.orbitalis.phase3.propulsion.FusionReactorBlockEntity::new,
                    ModBlocks.FUSION_REACTOR.get()));

    private ModBlockEntities() {
    }

    public static void register() {
        BLOCK_ENTITIES.register();
    }

    /**
     * Expose l'énergie et l'inventaire des machines aux autres blocs (générateurs, câbles, entonnoirs,
     * tuyaux d'autres mods), sur toutes les faces.
     */
        public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, COMBUSTION_GENERATOR.get(), (machine, side) -> machine.getEnergy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ARC_FOUNDRY.get(), (machine, side) -> machine.getEnergy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ROLLING_MILL.get(), (machine, side) -> machine.getEnergy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, CASIMIR_CHAMBER.get(), (machine, side) -> machine.getEnergy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, INDUCTION_FURNACE.get(), (machine, side) -> machine.getEnergy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, MELTING_FURNACE.get(), (machine, side) -> machine.getEnergy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, INDUCTION_FURNACE.get(), (machine, side) -> machine.getInventory().automationView());
        event.registerBlockEntity(Capabilities.Item.BLOCK, MELTING_FURNACE.get(), (machine, side) -> machine.getInventory().automationView());

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
        // Pompe à eau : le courant n'entre que par son boîtier électrique, à l'arrière, et l'eau ne sort que par sa bride,
        // à l'avant. Câbles et tuyaux ne se raccordent donc qu'au bon endroit.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, WATER_PUMP.get(),
                (pump, side) -> side == null || side == pump.powerInlet() ? pump.getEnergy() : null);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, WATER_PUMP.get(),
                (pump, side) -> side == null || side == pump.outlet() ? pump.getTank().automationView() : null);

        // Réservoir modulable : quel que soit le bloc touché, on atteint le contenu du réservoir entier.
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, FLUID_TANK.get(), (tank, side) -> tank.handler());

        // Prises de données : ce qu'un câble de données peut relier. Les écrans y lisent les ordinateurs et les réservoirs.
        // Un écran n'a de prise qu'au dos : le câble arrive par le mur, jamais par la tranche ni par la vitre.
        event.registerBlock(DataLink.BLOCK, (level, pos, state, blockEntity, side) ->
                side == null || side == state.getValue(MissionDisplayBlock.FACING).getOpposite() ? DataLink.PORT : null,
                ModBlocks.MISSION_DISPLAY.get());
        event.registerBlock(DataLink.BLOCK, (level, pos, state, blockEntity, side) -> DataLink.PORT,
                ModBlocks.LAUNCH_CONSOLE.get(), ModBlocks.TEST_CONSOLE.get(), ModBlocks.TRACKING_STATION.get(),
                ModBlocks.WORKSTATION_PART.get(), ModBlocks.FLUID_TANK.get(), ModBlocks.CRYOGENIC_TANK.get());

        // Stations : le panneau solaire fournit de l'électricité, le générateur d'air en reçoit.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SOLAR_PANEL.get(), (panel, side) -> panel.getEnergy());
        // Accumulateur : il reçoit par cinq faces et ne donne que par l'avant.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ACCUMULATOR.get(), (accumulator, side) -> accumulator.handler(side));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, LIFE_SUPPORT.get(), (unit, side) -> unit.getEnergy());
        // Équipements de station : ils se branchent aussi par câble, hors d'une station ou en renfort de son réseau.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, LAB_RACK.get(), (rack, side) -> rack.getEnergy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, HYDROPONIC_TRAY.get(), (tray, side) -> tray.getEnergy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, STATION_BATTERY.get(), (battery, side) -> battery.getEnergy());

        // Blocs de ravitaillement, au pied de la tour : l'ergol qu'on leur envoie est chargé dans la fusée de la table
        // de lancement voisine. Chacun ne laisse passer que le sien.
        event.registerBlock(Capabilities.Fluid.BLOCK,
                (level, pos, state, blockEntity, side) -> state.getValue(com.orbitalis.phase1.spaceport.FuelPortBlock.KIND).carriesFluid()
                        ? new UmbilicalFluidHandler(level, pos) : null,
                ModBlocks.FUEL_PORT.get());
        // Port de données : un câble de données s'y branche, et relie au pas de tir les pupitres et les écrans.
        event.registerBlock(DataLink.BLOCK, (level, pos, state, blockEntity, side) ->
                state.getValue(com.orbitalis.phase1.spaceport.FuelPortBlock.KIND) == com.orbitalis.phase1.spaceport.FuelPortBlock.Kind.DATA
                        ? DataLink.PORT : null, ModBlocks.FUEL_PORT.get());
        // Foreuse : elle prend l'électricité et l'eau de refroidissement, et on lui prend son butin, par son bloc de
        // commande comme par n'importe quel bloc de son socle.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, DRILL_DERRICK.get(), (derrick, side) -> derrick.getEnergy());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, DRILL_DERRICK.get(), (derrick, side) -> derrick.getCoolant());
        event.registerBlockEntity(Capabilities.Item.BLOCK, DRILL_DERRICK.get(), (derrick, side) -> derrick.getOutput().automationView());
        event.registerBlock(Capabilities.Energy.BLOCK, (level, pos, state, blockEntity, side) -> {
            var derrick = com.orbitalis.phase1.quarry.DerrickBlockEntity.ownerOfBase(level, pos);
            return derrick == null ? null : derrick.getEnergy();
        }, ModBlocks.DRILL_BASE.get());
        event.registerBlock(Capabilities.Fluid.BLOCK, (level, pos, state, blockEntity, side) -> {
            var derrick = com.orbitalis.phase1.quarry.DerrickBlockEntity.ownerOfBase(level, pos);
            return derrick == null ? null : derrick.getCoolant();
        }, ModBlocks.DRILL_BASE.get());
        // L'électricité du pas de tir : un câble se branche sur la table, sur une dalle du tablier ou sur un bloc de
        // ravitaillement, et charge la réserve de la table la plus proche.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, LAUNCH_TABLE.get(), (table, side) -> table.getPower());
        event.registerBlock(Capabilities.Energy.BLOCK, (level, pos, state, blockEntity, side) -> {
            LaunchTableBlockEntity table = LaunchTableBlockEntity.nearest(level, pos, com.orbitalis.phase1.spaceport.LaunchComplex.REACH + 2);
            return table == null ? null : table.getPower();
        }, ModBlocks.LAUNCH_PAD.get(), ModBlocks.FLAME_TRENCH.get());
        // Un port de pas de tir ne prend l'électricité que réglé sur l'électricité.
        event.registerBlock(Capabilities.Energy.BLOCK, (level, pos, state, blockEntity, side) -> {
            if (state.getValue(com.orbitalis.phase1.spaceport.FuelPortBlock.KIND) != com.orbitalis.phase1.spaceport.FuelPortBlock.Kind.ENERGY) {
                return null;
            }
            LaunchTableBlockEntity table = LaunchTableBlockEntity.nearest(level, pos, com.orbitalis.phase1.spaceport.LaunchComplex.REACH + 2);
            return table == null ? null : table.getPower();
        }, ModBlocks.FUEL_PORT.get());

        // Conduits : ils n'ont pas d'entité de bloc ; ce qu'on leur envoie est livré au réseau auquel ils appartiennent.
        event.registerBlock(Capabilities.Energy.BLOCK,
                (level, pos, state, blockEntity, side) -> new CableEnergyHandler(level, pos, state.getBlock()),
                ModBlocks.ENERGY_CABLE.get());
        event.registerBlock(Capabilities.Fluid.BLOCK,
                (level, pos, state, blockEntity, side) -> new PipeFluidHandler(level, pos, state.getBlock()),
                ModBlocks.FLUID_PIPE.get(), ModBlocks.GAS_PIPE.get());
    }
}
