package com.orbitalis.core.registry;

import java.util.List;
import java.util.function.Supplier;

import com.orbitalis.phase1.rocket.DesignComputerBlock;
import com.orbitalis.phase1.rocket.RocketPartBlock;
import com.orbitalis.Orbitalis;
import net.minecraft.world.level.block.TransparentBlock;
import com.orbitalis.phase2.station.SolarPanelBlock;
import com.orbitalis.phase2.station.LifeSupportBlock;
import com.orbitalis.phase2.station.DockingPortBlock;
import com.orbitalis.phase2.station.CargoRackBlock;
import com.orbitalis.phase2.station.CrewBunkBlock;
import com.orbitalis.phase2.station.HydroponicTrayBlock;
import com.orbitalis.phase2.station.LabRackBlock;
import com.orbitalis.phase2.station.StationBatteryBlock;
import com.orbitalis.phase2.station.StationFacingBlock;
import com.orbitalis.phase2.station.StationHatch;
import com.orbitalis.phase2.station.StationTerminalBlock;
import com.orbitalis.phase2.station.StationTrussBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.material.PushReaction;
import com.orbitalis.phase1.satellite.TrackingStationBlock;
import com.orbitalis.core.creative.CreativeSourceBlock;
import com.orbitalis.phase1.conduit.ConduitBlock;
import com.orbitalis.phase1.conduit.DataLink;
import com.orbitalis.phase1.fuel.FluidTankBlock;
import com.orbitalis.phase1.machines.MachineBlock;
import com.orbitalis.phase1.spaceport.LaunchConsoleBlock;
import com.orbitalis.phase1.spaceport.LaunchPadBlock;
import com.orbitalis.phase1.spaceport.LaunchTableBlock;
import com.orbitalis.phase1.spaceport.ManagedStructureBlock;
import com.orbitalis.phase1.spaceport.MissionDisplayBlock;
import com.orbitalis.phase1.spaceport.BoardingGateBlock;
import com.orbitalis.phase1.spaceport.ElevatorBlock;
import com.orbitalis.phase1.spaceport.FuelPortBlock;
import com.orbitalis.phase1.spaceport.FloodlightBlock;
import com.orbitalis.phase1.spaceport.PadLightBlock;
import com.orbitalis.phase1.spaceport.PadPartBlock;
import com.orbitalis.phase1.spaceport.TowerRailingBlock;
import com.orbitalis.phase1.spaceport.WorkstationPartBlock;
import com.orbitalis.phase1.spaceport.StructurePartBlock;
import com.orbitalis.phase1.spaceport.TowerStepBlock;
import com.orbitalis.phase1.spaceport.TestConsoleBlock;
import com.orbitalis.phase1.spaceport.RocketBlock;
import com.orbitalis.phase1.spaceport.RocketHullBlock;
import com.orbitalis.phase1.machines.MachineBlockEntity;

import net.minecraft.core.Direction;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registre de tous les blocs du mod. Chaque bloc est enregistré avec son item.
 */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Orbitalis.MODID);

    // ------------------------------------------------------------------
    // Phase 1 - Matériaux : minerais
    // ------------------------------------------------------------------
    public static final DeferredBlock<Block> BAUXITE_ORE = stoneOre("bauxite_ore");
    public static final DeferredBlock<Block> DEEPSLATE_BAUXITE_ORE = deepslateOre("deepslate_bauxite_ore");
    public static final DeferredBlock<Block> TITANIUM_ORE = stoneOre("titanium_ore");
    public static final DeferredBlock<Block> DEEPSLATE_TITANIUM_ORE = deepslateOre("deepslate_titanium_ore");
    public static final DeferredBlock<Block> LITHIUM_ORE = stoneOre("lithium_ore");
    public static final DeferredBlock<Block> DEEPSLATE_LITHIUM_ORE = deepslateOre("deepslate_lithium_ore");

    // ------------------------------------------------------------------
    // Phase 1 - Matériaux : blocs de métal
    // ------------------------------------------------------------------
    public static final DeferredBlock<Block> STEEL_BLOCK = metalBlock("steel_block", MapColor.COLOR_GRAY);
    public static final DeferredBlock<Block> ALUMINUM_BLOCK = metalBlock("aluminum_block", MapColor.METAL);
    public static final DeferredBlock<Block> TITANIUM_BLOCK = metalBlock("titanium_block", MapColor.COLOR_LIGHT_GRAY);

    // ------------------------------------------------------------------
    // Phase 3 - La Lune
    // ------------------------------------------------------------------
    /** Régolithe lunaire : la poussière grise qui couvre la Lune. */
    public static final DeferredBlock<Block> MOON_REGOLITH = withItem(BLOCKS.registerSimpleBlock("moon_regolith",
            properties -> properties.mapColor(MapColor.COLOR_LIGHT_GRAY).strength(0.6F).sound(SoundType.SAND)));
    /** Roche lunaire : le sous-sol de la Lune. */
    public static final DeferredBlock<Block> MOON_ROCK = withItem(BLOCKS.registerSimpleBlock("moon_rock",
            properties -> properties.mapColor(MapColor.COLOR_GRAY).instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops().strength(1.8F, 6.0F)));

    /** Minerai d'ilménite lunaire : un oxyde de fer et de titane, abondant dans la roche lunaire. Donne du titane brut. */
    public static final DeferredBlock<Block> MOON_ILMENITE_ORE = withItem(BLOCKS.registerSimpleBlock("moon_ilmenite_ore",
            properties -> properties.mapColor(MapColor.COLOR_GRAY).instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops().strength(3.0F, 6.0F)));
    /** Glace lunaire : au fond des grands cratères, toujours à l'ombre. La seule eau de la Lune. */
    public static final DeferredBlock<Block> LUNAR_ICE = withItem(BLOCKS.registerSimpleBlock("lunar_ice",
            properties -> properties.mapColor(MapColor.ICE).friction(0.98F).strength(0.8F).sound(SoundType.GLASS)));

    // ------------------------------------------------------------------
    // Phase 3 - Mars
    // ------------------------------------------------------------------
    /** Régolithe martien : la poussière rouille, riche en oxydes de fer, qui couvre Mars. */
    public static final DeferredBlock<Block> MARS_REGOLITH = withItem(BLOCKS.registerSimpleBlock("mars_regolith",
            properties -> properties.mapColor(MapColor.TERRACOTTA_ORANGE).strength(0.6F).sound(SoundType.SAND)));
    /** Roche martienne : un basalte brun-rouge, le sous-sol de Mars. */
    public static final DeferredBlock<Block> MARS_ROCK = withItem(BLOCKS.registerSimpleBlock("mars_rock",
            properties -> properties.mapColor(MapColor.TERRACOTTA_RED).instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops().strength(1.8F, 6.0F)));
    /** Minerai d'hématite martienne : l'oxyde de fer qui donne sa couleur à la planète. Donne du fer brut. */
    public static final DeferredBlock<Block> MARS_HEMATITE_ORE = withItem(BLOCKS.registerSimpleBlock("mars_hematite_ore",
            properties -> properties.mapColor(MapColor.TERRACOTTA_RED).instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops().strength(3.0F, 6.0F)));

    // ------------------------------------------------------------------
    // Phase 3 - Titan
    // ------------------------------------------------------------------
    /** Glace de Titan : à 94 K, la glace d'eau est dure comme la roche. C'est le socle de Titan. */
    public static final DeferredBlock<Block> TITAN_ICE = withItem(BLOCKS.registerSimpleBlock("titan_ice",
            properties -> properties.mapColor(MapColor.TERRACOTTA_WHITE).instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops().strength(1.6F, 6.0F).sound(SoundType.STONE)));
    /** Sable de tholines : des grains de matière organique tombés de la brume, qui forment les dunes de Titan. */
    public static final DeferredBlock<Block> THOLIN_SAND = withItem(BLOCKS.registerSimpleBlock("tholin_sand",
            properties -> properties.mapColor(MapColor.TERRACOTTA_BROWN).strength(0.5F).sound(SoundType.SAND)));
    /** Méthane liquide, versé dans le monde : les lacs et les mers de Titan. */
    public static final DeferredBlock<net.minecraft.world.level.block.LiquidBlock> LIQUID_METHANE = BLOCKS.registerBlock("liquid_methane",
            properties -> new net.minecraft.world.level.block.LiquidBlock(ModFluids.LIQUID_METHANE.source().get(), properties),
            properties -> properties.mapColor(MapColor.TERRACOTTA_BROWN).replaceable().noCollision().strength(100.0F)
                    .pushReaction(PushReaction.DESTROY).noLootTable().liquid().sound(SoundType.EMPTY));

    // ------------------------------------------------------------------
    // Phase 3 - Europe
    // ------------------------------------------------------------------
    /** Glace d'Europe : la banquise de glace d'eau, bleutée, qui couvre l'océan de cette lune de Jupiter. */
    public static final DeferredBlock<Block> EUROPA_ICE = withItem(BLOCKS.registerSimpleBlock("europa_ice",
            properties -> properties.mapColor(MapColor.ICE).instrument(NoteBlockInstrument.CHIME)
                    .requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(SoundType.GLASS)));
    /** Glace des lineae : la glace brun-rouge, chargée des sels remontés de l'océan, des traînées et des chaos. */
    public static final DeferredBlock<Block> EUROPA_LINEA_ICE = withItem(BLOCKS.registerSimpleBlock("europa_linea_ice",
            properties -> properties.mapColor(MapColor.TERRACOTTA_RED).instrument(NoteBlockInstrument.CHIME)
                    .requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(SoundType.GLASS)));
    // ------------------------------------------------------------------
    // Proxima b
    // ------------------------------------------------------------------
    /** Roche de Proxima b : un basalte sombre et violacé. */
    public static final DeferredBlock<Block> PROXIMA_ROCK = withItem(BLOCKS.registerSimpleBlock("proxima_rock",
            properties -> properties.mapColor(MapColor.COLOR_GRAY).instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops().strength(1.6F, 6.0F).sound(SoundType.BASALT)));
    /** Régolithe de Proxima b : une terre brun rouille. */
    public static final DeferredBlock<Block> PROXIMA_REGOLITH = withItem(BLOCKS.registerSimpleBlock("proxima_regolith",
            properties -> properties.mapColor(MapColor.TERRACOTTA_BROWN).strength(0.6F).sound(SoundType.GRAVEL)));
    /** Mousse du crépuscule : le régolithe couvert d'une mousse presque noire, là où vit la flore. */
    public static final DeferredBlock<Block> PROXIMA_MOSS = withItem(BLOCKS.registerSimpleBlock("proxima_moss",
            properties -> properties.mapColor(MapColor.TERRACOTTA_PURPLE).strength(0.6F).sound(SoundType.MOSS)));
    /** Cendre de la face de jour, cuite par l'étoile. */
    public static final DeferredBlock<Block> PROXIMA_ASH = withItem(BLOCKS.registerSimpleBlock("proxima_ash",
            properties -> properties.mapColor(MapColor.TERRACOTTA_ORANGE).strength(0.5F).sound(SoundType.SAND)));
    /** Givre de la face de nuit. */
    public static final DeferredBlock<Block> PROXIMA_FROST = withItem(BLOCKS.registerSimpleBlock("proxima_frost",
            properties -> properties.mapColor(MapColor.COLOR_BLUE).instrument(NoteBlockInstrument.CHIME)
                    .requiresCorrectToolForDrops().strength(1.2F, 4.0F).sound(SoundType.GLASS)));
    /** La flore du crépuscule : la laîche rouge, la fougère d'ombre et le bulbe-lanterne, qui luit. */
    public static final DeferredBlock<com.orbitalis.phase3.celestial.ProximaPlantBlock> RED_SEDGE = plant("red_sedge", 0, true);
    public static final DeferredBlock<com.orbitalis.phase3.celestial.ProximaPlantBlock> UMBRA_FERN = plant("umbra_fern", 0, false);
    public static final DeferredBlock<com.orbitalis.phase3.celestial.ProximaPlantBlock> LANTERN_BULB = plant("lantern_bulb", 10, false);

    /** Cryobot : la sonde foreuse qui fond la banquise d'Europe jusqu'à l'océan. */
    public static final DeferredBlock<com.orbitalis.phase3.exploration.CryobotBlock> CRYOBOT = withItem(BLOCKS.registerBlock("cryobot",
            com.orbitalis.phase3.exploration.CryobotBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops()
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    // ------------------------------------------------------------------
    // Phase 1 - Machines
    // ------------------------------------------------------------------
    /** Générateur à combustion : brûle des combustibles pour produire de l'énergie. */
    public static final DeferredBlock<MachineBlock> COMBUSTION_GENERATOR =
            machine("combustion_generator", () -> ModBlockEntities.COMBUSTION_GENERATOR.get());
    /** Fonderie à arc : fond deux ingrédients en un alliage (acier, aluminium-lithium). */
    public static final DeferredBlock<MachineBlock> ARC_FOUNDRY =
            machine("arc_foundry", () -> ModBlockEntities.ARC_FOUNDRY.get());
    /** Laminoir : transforme les lingots en tôles. */
    public static final DeferredBlock<MachineBlock> ROLLING_MILL =
            machine("rolling_mill", () -> ModBlockEntities.ROLLING_MILL.get());

    // ------------------------------------------------------------------
    // Phase 1 - Carburants
    // ------------------------------------------------------------------
    /** Électrolyseur : sépare l'eau en hydrogène et en oxygène. */
    public static final DeferredBlock<MachineBlock> ELECTROLYZER =
            machine("electrolyzer", () -> ModBlockEntities.ELECTROLYZER.get());
    /** Liquéfacteur cryogénique : refroidit un gaz jusqu'à le rendre liquide. */
    public static final DeferredBlock<MachineBlock> CRYOGENIC_LIQUEFIER =
            machine("cryogenic_liquefier", () -> ModBlockEntities.CRYOGENIC_LIQUEFIER.get());
    /** Raffinerie : transforme le charbon en kérosène RP-1. */
    public static final DeferredBlock<MachineBlock> REFINERY =
            machine("refinery", () -> ModBlockEntities.REFINERY.get());
    /** Extracteur d'hélium-3 : il cuit le régolithe de la Lune pour en tirer l'hélium-3 du vent solaire. */
    public static final DeferredBlock<MachineBlock> HELIUM3_EXTRACTOR =
            machine("helium3_extractor", () -> ModBlockEntities.HELIUM3_EXTRACTOR.get());
    /** Réacteur de Sabatier : du méthane, à partir de carbone et d'hydrogène. */
    public static final DeferredBlock<MachineBlock> SABATIER_REACTOR =
            machine("sabatier_reactor", () -> ModBlockEntities.SABATIER_REACTOR.get());
    /** Chambre de Casimir : entre deux miroirs presque collés, le vide lui-même donne la matière exotique. */
    public static final DeferredBlock<MachineBlock> CASIMIR_CHAMBER =
            machine("casimir_chamber", () -> ModBlockEntities.CASIMIR_CHAMBER.get());
    /** Réacteur à fusion : l'hélium-3 et l'hydrogène, brûlés en plasma, donnent beaucoup d'électricité. */
    public static final DeferredBlock<com.orbitalis.phase3.propulsion.FusionReactorBlock> FUSION_REACTOR = withItem(BLOCKS.registerBlock(
            "fusion_reactor", com.orbitalis.phase3.propulsion.FusionReactorBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops()
                    .strength(5.0F, 9.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT) ? 13 : 0)));
    /** Réservoir cryogénique : stocke les ergols et les maintient au froid. */
    public static final DeferredBlock<MachineBlock> CRYOGENIC_TANK =
            machine("cryogenic_tank", () -> ModBlockEntities.CRYOGENIC_TANK.get());

    /**
     * Réservoir modulable : ses blocs s'assemblent en un seul réservoir, d'un bloc jusqu'à 3×3×12. Il stocke n'importe
     * quel liquide ou gaz, sans le refroidir.
     */
    public static final DeferredBlock<FluidTankBlock> FLUID_TANK = withItem(BLOCKS.registerBlock("fluid_tank",
            FluidTankBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)));

    // ------------------------------------------------------------------
    // Phase 1 - Conduits
    // ------------------------------------------------------------------
    /** Câble d'énergie : relie générateurs et machines à distance. */
    public static final DeferredBlock<ConduitBlock> ENERGY_CABLE = conduit("energy_cable", Capabilities.Energy.BLOCK, ConduitBlock.Kind.ENERGY);
    /** Tuyau : transporte les fluides entre machines et réservoirs. */
    public static final DeferredBlock<ConduitBlock> FLUID_PIPE = conduit("fluid_pipe", Capabilities.Fluid.BLOCK, ConduitBlock.Kind.LIQUID);
    /** Tuyau à gaz : transporte l'hydrogène et l'oxygène gazeux, de l'électrolyseur aux liquéfacteurs. */
    public static final DeferredBlock<ConduitBlock> GAS_PIPE = conduit("gas_pipe", Capabilities.Fluid.BLOCK, ConduitBlock.Kind.GAS);
    /** Câble de données : relie les écrans de contrôle aux ordinateurs et aux réservoirs qu'ils affichent. */
    public static final DeferredBlock<ConduitBlock> DATA_CABLE = conduit("data_cable", DataLink.BLOCK, ConduitBlock.Kind.DATA);

    // ------------------------------------------------------------------
    // Phase 1 - Fusées
    // ------------------------------------------------------------------
    // ------------------------------------------------------------------
    // Phase 1 - Centre spatial : complexe de lancement
    // ------------------------------------------------------------------
    /** Dalle de pas de tir : béton armé qui résiste aux flammes. Posée en carré autour de la table de lancement. */
    public static final DeferredBlock<LaunchPadBlock> LAUNCH_PAD = withItem(BLOCKS.registerBlock("launch_pad",
            LaunchPadBlock::new,
            properties -> properties
                    .mapColor(MapColor.STONE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops()
                    .strength(4.0F, 1200.0F)));
    /** Table de lancement : cœur du complexe, au centre du pas de tir. */
    public static final DeferredBlock<LaunchTableBlock> LAUNCH_TABLE = withItem(BLOCKS.registerBlock("launch_table",
            LaunchTableBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(4.0F, 1200.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(LaunchTableBlock.FORMED) ? 8 : 0)));
    /** Colonne technique de la tour : elle porte les conduites d'ergols et les bras, face à la fusée. */
    public static final DeferredBlock<ManagedStructureBlock> SERVICE_TOWER = withItem(BLOCKS.registerBlock("service_tower",
            ManagedStructureBlock::new,
            properties -> properties
                    .mapColor(MapColor.COLOR_RED)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));
    /** Charpente de tour : les six blocs de structure qui entourent la colonne technique et la gaine, à chaque étage. */
    public static final DeferredBlock<ManagedStructureBlock> TOWER_FRAME = withItem(BLOCKS.registerBlock("tower_frame",
            ManagedStructureBlock::new,
            properties -> properties
                    .mapColor(MapColor.COLOR_RED)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));
    /** Portique d'embarquement : deux blocs de haut, dans la colonne technique, à la hauteur de la capsule. */
    public static final DeferredBlock<BoardingGateBlock> BOARDING_GATE = withItem(BLOCKS.registerBlock("boarding_gate",
            BoardingGateBlock::new,
            properties -> properties
                    .mapColor(MapColor.COLOR_RED)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));
    /** Socle de l'ascenseur de la tour : posé au pied de la gaine, il fait venir la cabine. */
    public static final DeferredBlock<ElevatorBlock> TOWER_ELEVATOR = withItem(BLOCKS.registerBlock("tower_elevator",
            ElevatorBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));
    /** Escalier de tour : deux marches minces, à contremarches ouvertes. Les volées se superposent tous les deux étages. */
    public static final DeferredBlock<PadPartBlock> TOWER_STAIRS = withItem(BLOCKS.registerBlock("tower_stairs",
            properties -> new PadPartBlock(properties, PadPartBlock.STAIRS),
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));
    /** Palier de tour : un platelage au pied de sa case. */
    public static final DeferredBlock<PadPartBlock> TOWER_LANDING = withItem(BLOCKS.registerBlock("tower_landing",
            properties -> new PadPartBlock(properties, PadPartBlock.LANDING),
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));
    /** Dalle de tour : une demi-dalle de tôle sur ses poutres, pour le sommet. */
    public static final DeferredBlock<PadPartBlock> TOWER_DECK = withItem(BLOCKS.registerBlock("tower_deck",
            properties -> new PadPartBlock(properties, PadPartBlock.DECK),
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));
    /** Rambarde de tour : elle ceinture le sommet, et se raccorde à ses voisines comme une barrière. */
    public static final DeferredBlock<TowerRailingBlock> TOWER_RAILING = withItem(BLOCKS.registerBlock("tower_railing",
            TowerRailingBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));
    /** Appui du tablier : il le porte, un bloc au-dessus du sol. */
    public static final DeferredBlock<PadPartBlock> PAD_SUPPORT = withItem(BLOCKS.registerBlock("pad_support",
            properties -> new PadPartBlock(properties, PadPartBlock.SUPPORT),
            properties -> properties
                    .mapColor(MapColor.STONE)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));
    /** Rampe de déluge : la conduite d'eau et son arroseur, sur le tablier. */
    public static final DeferredBlock<PadPartBlock> DELUGE_PIPE = withItem(BLOCKS.registerBlock("deluge_pipe",
            properties -> new PadPartBlock(properties, PadPartBlock.DELUGE),
            properties -> properties
                    .mapColor(MapColor.COLOR_BLUE)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));
    /** Bloc de ravitaillement : la prise d'un ergol, au pied de la tour. Il en faut un par ergol de la fusée. */
    public static final DeferredBlock<FuelPortBlock> FUEL_PORT = withItem(BLOCKS.registerBlock("fuel_port",
            FuelPortBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.5F, 12.0F)
                    .sound(SoundType.METAL)));
    /** Bras ombilical : s'accroche à la tour ; il ravitaillera la fusée en ergols et en électricité. */
    public static final DeferredBlock<ManagedStructureBlock> UMBILICAL_ARM = withItem(BLOCKS.registerBlock("umbilical_arm",
            ManagedStructureBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));
    /** Pupitre de lancement : poste du centre de contrôle, relié à la table de lancement la plus proche. */
    public static final DeferredBlock<LaunchConsoleBlock> LAUNCH_CONSOLE = withItem(BLOCKS.registerBlock("launch_console",
            LaunchConsoleBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> 7)));
    /** Pupitre d'essai : d'un seul bloc, il ne commande que la séquence de lancement de la table la plus proche. */
    public static final DeferredBlock<TestConsoleBlock> TEST_CONSOLE = withItem(BLOCKS.registerBlock("test_console",
            TestConsoleBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> 5)));

    /** Écran mural du centre de contrôle : il suit le lancement en cours sur la table la plus proche. */
    public static final DeferredBlock<MissionDisplayBlock> MISSION_DISPLAY = withItem(BLOCKS.registerBlock("mission_display",
            MissionDisplayBlock::new,
            properties -> properties
                    .mapColor(MapColor.COLOR_BLACK)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(MissionDisplayBlock.MODE) == MissionDisplayBlock.Mode.IDLE ? 4 : 10)));

    /** Station de poursuite : antenne parabolique qui suit les satellites de l'agence et reçoit leurs données. */
    public static final DeferredBlock<TrackingStationBlock> TRACKING_STATION = withItem(BLOCKS.registerBlock("tracking_station",
            TrackingStationBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> 5)));

    // ------------------------------------------------------------------
    // Phase 2 - Stations spatiales
    // ------------------------------------------------------------------
    /** Coque de station : le mur étanche dont on bâtit les modules. */
    public static final DeferredBlock<Block> STATION_HULL = withItem(BLOCKS.registerBlock("station_hull", Block::new,
            properties -> properties
                    .mapColor(MapColor.SNOW)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 12.0F)
                    .sound(SoundType.METAL)));
    /** Hublot de station : étanche comme la coque, mais transparent. */
    public static final DeferredBlock<TransparentBlock> STATION_WINDOW = withItem(BLOCKS.registerBlock("station_window", TransparentBlock::new,
            properties -> properties
                    .mapColor(MapColor.NONE)
                    .instrument(NoteBlockInstrument.HAT)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 12.0F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)));
    /** Sas d'amarrage : les capsules s'y amarrent, et on y remonte à bord depuis l'intérieur. */
    public static final DeferredBlock<DockingPortBlock> DOCKING_PORT = withItem(BLOCKS.registerBlock("docking_port", DockingPortBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.5F, 12.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));
    /** Panneau solaire : de l'électricité tant qu'il voit le ciel, à pleine puissance en orbite. */
    public static final DeferredBlock<SolarPanelBlock> SOLAR_PANEL = withItem(BLOCKS.registerBlock("solar_panel", SolarPanelBlock::new,
            properties -> properties
                    .mapColor(MapColor.COLOR_BLUE)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));
    /** Bras d'assemblage : alimenté en électricité, il bâtit la fusée du pas de tir voisin avec les pièces d'un coffre. */
    /** Générateur d'air : alimenté en électricité, il pressurise la pièce close qui l'entoure. Il l'éclaire aussi. */
    public static final DeferredBlock<LifeSupportBlock> LIFE_SUPPORT = withItem(BLOCKS.registerBlock("life_support", LifeSupportBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.5F, 12.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 13)));

    /** Revêtement intérieur : un panneau capitonné tourné vers la pièce, la coque blanche dehors. Étanche. */
    public static final DeferredBlock<StationFacingBlock> STATION_PANEL = withItem(BLOCKS.registerBlock("station_panel", StationFacingBlock::new,
            properties -> properties
                    .mapColor(MapColor.SAND)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 12.0F)
                    .sound(SoundType.WOOL)));
    /** Plancher de station : une tôle antidérapante. Étanche. */
    public static final DeferredBlock<Block> STATION_FLOOR = withItem(BLOCKS.registerBlock("station_floor", Block::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 12.0F)
                    .sound(SoundType.METAL)));
    /** Plafonnier : une dalle de diodes tournée vers la pièce. Étanche, et très lumineux. */
    public static final DeferredBlock<StationFacingBlock> STATION_LIGHT = withItem(BLOCKS.registerBlock("station_light", StationFacingBlock::new,
            properties -> properties
                    .mapColor(MapColor.SNOW)
                    .instrument(NoteBlockInstrument.HAT)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 12.0F)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 15)));
    /** Poutre treillis : la charpente extérieure d'une station. L'air la traverse. */
    public static final DeferredBlock<StationTrussBlock> STATION_TRUSS = withItem(BLOCKS.registerBlock("station_truss", StationTrussBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 8.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)));
    /** Sas étanche : une porte de deux blocs, à hublot, qu'on ouvre à la main. Fermée, elle ne laisse pas passer l'air. */
    public static final DeferredBlock<DoorBlock> STATION_HATCH = BLOCKS.registerBlock("station_hatch",
            properties -> new DoorBlock(StationHatch.TYPE, properties),
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops()
                    .strength(4.0F, 12.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY));
    /** Porte de sas : deux battants qui s'écartent après un cycle de mise à niveau ; les portes d'un sas se verrouillent entre elles. */
    public static final DeferredBlock<com.orbitalis.phase2.station.AirlockDoorBlock> AIRLOCK_DOOR = BLOCKS.registerBlock("airlock_door",
            com.orbitalis.phase2.station.AirlockDoorBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(4.0F, 14.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)
                    .lightLevel(state -> 6));
    /** Battant d'une porte de sas : jamais posé, il porte les modèles que le rendu de la porte fait coulisser. */
    public static final DeferredBlock<com.orbitalis.phase2.station.AirlockLeafBlock> AIRLOCK_LEAF = BLOCKS.registerBlock("airlock_leaf",
            com.orbitalis.phase2.station.AirlockLeafBlock::new,
            properties -> properties.mapColor(MapColor.METAL).strength(1.0F).noOcclusion().noLootTable());
    /** Porte vitrée coulissante : deux battants de verre qui s'écartent, au clic ou par la redstone. */
    public static final DeferredBlock<com.orbitalis.phase2.station.GlassDoorBlock> GLASS_DOOR = BLOCKS.registerBlock("glass_door",
            com.orbitalis.phase2.station.GlassDoorBlock::new,
            properties -> properties
                    .mapColor(MapColor.NONE)
                    .instrument(NoteBlockInstrument.HAT)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .pushReaction(PushReaction.DESTROY));
    /** Battant d'une porte vitrée : jamais posé, il porte les modèles que le rendu de la porte fait coulisser. */
    public static final DeferredBlock<com.orbitalis.phase2.station.GlassDoorLeafBlock> GLASS_DOOR_LEAF = BLOCKS.registerBlock("glass_door_leaf",
            com.orbitalis.phase2.station.GlassDoorLeafBlock::new,
            properties -> properties.mapColor(MapColor.NONE).strength(1.0F).noOcclusion().noLootTable());
    /** Vitre de coupole : un cadre épais boulonné. Étanche, transparente. */
    /** Foreuse à glace : sur la Lune ou sur Mars, elle tire de l'eau de la glace enfouie sous elle. */
    public static final DeferredBlock<com.orbitalis.phase3.exploration.IsruBlock> ICE_DRILL = withItem(BLOCKS.registerBlock("ice_drill",
            properties -> new com.orbitalis.phase3.exploration.IsruBlock(properties, com.orbitalis.phase3.exploration.IsruBlock.Kind.ICE_DRILL),
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops()
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));
    /** Extracteur d'oxygène : sur Mars, il tire de l'oxygène du gaz carbonique de l'atmosphère. */
    public static final DeferredBlock<com.orbitalis.phase3.exploration.IsruBlock> OXYGEN_EXTRACTOR = withItem(BLOCKS.registerBlock("oxygen_extractor",
            properties -> new com.orbitalis.phase3.exploration.IsruBlock(properties, com.orbitalis.phase3.exploration.IsruBlock.Kind.OXYGEN_EXTRACTOR),
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops()
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));
    /** Modèles du rover : jamais posé, il porte le châssis et la roue que le rendu du rover dessine. */
    public static final DeferredBlock<com.orbitalis.phase3.exploration.RoverModelBlock> ROVER_MODEL = BLOCKS.registerBlock("rover_model",
            com.orbitalis.phase3.exploration.RoverModelBlock::new,
            properties -> properties.mapColor(MapColor.METAL).strength(1.0F).noOcclusion().noLootTable());
    /** Modèles du drone volant : jamais posé, il porte le corps et le rotor que le rendu du drone dessine. */
    public static final DeferredBlock<com.orbitalis.phase3.exploration.RotorDroneModelBlock> ROTOR_DRONE_MODEL = BLOCKS.registerBlock("rotor_drone_model",
            com.orbitalis.phase3.exploration.RotorDroneModelBlock::new,
            properties -> properties.mapColor(MapColor.METAL).strength(1.0F).noOcclusion().noLootTable());
    /** Atterrisseur robotique posé sur la Lune ou sur Mars : il n'est pas fabriqué, il arrive par fusée. */
    public static final DeferredBlock<com.orbitalis.phase3.exploration.RoboticLanderBlock> ROBOTIC_LANDER = BLOCKS.registerBlock("robotic_lander",
            com.orbitalis.phase3.exploration.RoboticLanderBlock::new,
            properties -> properties
                    .mapColor(MapColor.GOLD)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .noLootTable()
                    .lightLevel(state -> 4));
    public static final DeferredBlock<TransparentBlock> CUPOLA_WINDOW = withItem(BLOCKS.registerBlock("cupola_window", TransparentBlock::new,
            properties -> properties
                    .mapColor(MapColor.NONE)
                    .instrument(NoteBlockInstrument.HAT)
                    .requiresCorrectToolForDrops()
                    .strength(3.5F, 14.0F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)));
    /** Terminal de station : le tableau de bord de toute la station. */
    // Les blocs du vaisseau interstellaire : son aménagement, section par section.
    /** Cœur de distorsion : deux blocs de haut ; il luit d'autant plus qu'il tient de matière exotique. */
    public static final DeferredBlock<com.orbitalis.phase3.interstellar.WarpCoreBlock> WARP_CORE = withItem(BLOCKS.registerBlock(
            "warp_core", com.orbitalis.phase3.interstellar.WarpCoreBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.5F, 12.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> 4 + state.getValue(com.orbitalis.phase3.interstellar.WarpCoreBlock.CHARGE))));
    /** Moteur de l'anneau : au plafond, au droit des rayons. */
    public static final DeferredBlock<com.orbitalis.phase3.interstellar.RingDriveBlock> RING_DRIVE = withItem(BLOCKS.registerBlock(
            "ring_drive", com.orbitalis.phase3.interstellar.RingDriveBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.5F, 12.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));
    /** Caisson d'hibernation : une capsule dressée de deux blocs de haut. */
    public static final DeferredBlock<com.orbitalis.phase3.interstellar.HibernationPodBlock> HIBERNATION_POD = withItem(BLOCKS.registerBlock(
            "hibernation_pod", com.orbitalis.phase3.interstellar.HibernationPodBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.5F, 12.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> 5)));
    /** Poste de pilotage du vaisseau, sur la passerelle. */
    public static final DeferredBlock<com.orbitalis.phase3.interstellar.ShipHelmBlock> SHIP_HELM = withItem(BLOCKS.registerBlock(
            "ship_helm", com.orbitalis.phase3.interstellar.ShipHelmBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.5F, 12.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> 7)));
    /** Pupitre du chantier naval : il tient le plan du vaisseau interstellaire. */
    public static final DeferredBlock<com.orbitalis.phase3.interstellar.ShipyardConsoleBlock> SHIPYARD_CONSOLE = withItem(BLOCKS.registerBlock(
            "shipyard_console", com.orbitalis.phase3.interstellar.ShipyardConsoleBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 12.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> 6)));
    public static final DeferredBlock<StationTerminalBlock> STATION_TERMINAL = withItem(BLOCKS.registerBlock("station_terminal",
            StationTerminalBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 12.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> 6)));
    /** Couchette d'équipage : on y fait son point de réapparition, même en orbite. */
    public static final DeferredBlock<CrewBunkBlock> CREW_BUNK = withItem(BLOCKS.registerBlock("crew_bunk", CrewBunkBlock::new,
            properties -> properties
                    .mapColor(MapColor.COLOR_BLUE)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 8.0F)
                    .sound(SoundType.WOOL)
                    .noOcclusion()
                    .lightLevel(state -> 7)));
    /** Baie d'expériences : en orbite, elle fait avancer la recherche de l'agence. */
    public static final DeferredBlock<LabRackBlock> LAB_RACK = withItem(BLOCKS.registerBlock("lab_rack", LabRackBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 8.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(LabRackBlock.LIT) ? 8 : 0)));
    /** Bac de culture hydroponique : des récoltes sans terre ni soleil. */
    public static final DeferredBlock<HydroponicTrayBlock> HYDROPONIC_TRAY = withItem(BLOCKS.registerBlock("hydroponic_tray",
            HydroponicTrayBlock::new,
            properties -> properties
                    .mapColor(MapColor.SNOW)
                    .requiresCorrectToolForDrops()
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> 9)));
    /** Casier de fret : vingt-sept emplacements de rangement. */
    public static final DeferredBlock<CargoRackBlock> CARGO_RACK = withItem(BLOCKS.registerBlock("cargo_rack", CargoRackBlock::new,
            properties -> properties
                    .mapColor(MapColor.WOOL)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 8.0F)
                    .sound(SoundType.WOOL)
                    .noOcclusion()));
    /** Établi de combinaison : deux blocs de long, on y fixe et retire les modules des pièces de la combinaison avancée. */
    public static final DeferredBlock<com.orbitalis.phase2.survival.SuitWorkbenchBlock> SUIT_WORKBENCH = withItem(BLOCKS.registerBlock(
            "suit_workbench", com.orbitalis.phase2.survival.SuitWorkbenchBlock::new,
            properties -> properties
                    .mapColor(MapColor.QUARTZ)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)));
    /** Batterie de station : elle stocke l'électricité du réseau de la station. */
    public static final DeferredBlock<StationBatteryBlock> STATION_BATTERY = withItem(BLOCKS.registerBlock("station_battery",
            StationBatteryBlock::new,
            properties -> properties
                    .mapColor(MapColor.COLOR_GRAY)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.5F, 10.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    /** Déflecteur de flammes : sous la table de lancement, il dévie les gaz des moteurs vers le carneau. */
    public static final DeferredBlock<ManagedStructureBlock> FLAME_DEFLECTOR = withItem(BLOCKS.registerBlock("flame_deflector",
            ManagedStructureBlock::new,
            properties -> properties
                    .mapColor(MapColor.COLOR_GRAY)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(4.0F, 1200.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));
    /** Caillebotis de carneau : posés en ligne de la table jusqu'au bord du tablier, ils laissent s'échapper les gaz. */
    public static final DeferredBlock<ManagedStructureBlock> FLAME_TRENCH = withItem(BLOCKS.registerBlock("flame_trench",
            ManagedStructureBlock::new,
            properties -> properties
                    .mapColor(MapColor.COLOR_GRAY)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(4.0F, 1200.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));
    /** Mât paratonnerre : un bloc plein en treillis, qui s'empile sur les quatre angles d'un grand pas de tir. */
    public static final DeferredBlock<ManagedStructureBlock> LIGHTNING_MAST = withItem(BLOCKS.registerBlock("lightning_mast",
            ManagedStructureBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));
    /** Projecteur de mât : un tronçon de mât qui porte deux projecteurs. Son faisceau éclaire ce qu'il vise. */
    public static final DeferredBlock<FloodlightBlock> PAD_FLOODLIGHT = withItem(BLOCKS.registerBlock("pad_floodlight",
            FloodlightBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(FloodlightBlock.LIT) ? 10 : 0)));
    /** Lumière déposée par les projecteurs et les balises : de l'air qui éclaire. Sans objet. */
    public static final DeferredBlock<PadLightBlock> PAD_LIGHT = BLOCKS.registerBlock("pad_light",
            PadLightBlock::new,
            properties -> properties
                    .replaceable()
                    .noCollision()
                    .noLootTable()
                    .air()
                    .lightLevel(state -> state.getValue(PadLightBlock.LEVEL)));
    /** Pièces de structure dessinées par la table de lancement (tour, bras, flexibles). Jamais posé, sans objet. */
    public static final DeferredBlock<StructurePartBlock> STRUCTURE_PART = BLOCKS.registerBlock("structure_part",
            StructurePartBlock::new,
            properties -> properties.mapColor(MapColor.METAL).strength(1.0F).noOcclusion().noLootTable());

    /** Plancher invisible d'une passerelle d'embarquement. Sans objet : c'est la table qui le pose et le retire. */
    public static final DeferredBlock<TowerStepBlock> TOWER_STEP = BLOCKS.registerBlock("tower_step",
            TowerStepBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .strength(-1.0F, 3_600_000.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .replaceable()
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)
                    .noLootTable());

    // ------------------------------------------------------------------
    // Fusées : ordinateur de conception et blocs de construction
    // ------------------------------------------------------------------

    /** Ordinateur de conception : le grand pupitre où l'on choisit et règle ses lanceurs, et qui imprime leur plan. */
    public static final DeferredBlock<DesignComputerBlock> DESIGN_COMPUTER = withItem(BLOCKS.registerBlock("design_computer",
            DesignComputerBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> 9)));
    /** Établi d'ingénieur : la table à dessin, large de deux blocs, où l'on étudie les pièces et les modèles de fusée. */
    public static final DeferredBlock<com.orbitalis.phase1.rocket.EngineerWorkbenchBlock> ENGINEER_WORKBENCH = withItem(BLOCKS.registerBlock(
            "engineer_workbench",
            com.orbitalis.phase1.rocket.EngineerWorkbenchBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> 7)));
    /** Débris de fusée : un morceau de coque noirci, retombé d'un accident. On le ramasse et on le refond. */
    public static final DeferredBlock<Block> ROCKET_DEBRIS = withItem(BLOCKS.registerSimpleBlock("rocket_debris",
            properties -> properties
                    .mapColor(MapColor.COLOR_BLACK)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.METAL)));
    /** Case invisible occupée par un grand poste de travail, autour de son bloc. Sans objet. */
    public static final DeferredBlock<WorkstationPartBlock> WORKSTATION_PART = BLOCKS.registerBlock("workstation_part",
            WorkstationPartBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)
                    .noLootTable());

    /**
     * Le son des pièces de fusée : celui du métal, sauf quand on les pose, où sonne le « clang » des pièces (cinq
     * variantes). Le jeu joue la pose à 80 % de la hauteur du son : 1,25 la ramène à son timbre d'origine.
     */
    private static final net.neoforged.neoforge.common.util.DeferredSoundType ROCKET_PART_SOUND = new net.neoforged.neoforge.common.util.DeferredSoundType(
            1.0F, 1.25F, () -> net.minecraft.sounds.SoundEvents.METAL_BREAK, () -> net.minecraft.sounds.SoundEvents.METAL_STEP, ModSounds.PART_PLACED,
            () -> net.minecraft.sounds.SoundEvents.METAL_HIT, () -> net.minecraft.sounds.SoundEvents.METAL_FALL);

    /** Moteur à poudre : bloc de construction d'une fusée. */
    public static final DeferredBlock<RocketPartBlock> ROCKET_ENGINE_SOLID = BLOCKS.registerBlock("rocket_engine_solid",
            RocketPartBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 6.0F)
                    .sound(ROCKET_PART_SOUND)
                    .noOcclusion()
                    .noLootTable());
    /** Moteur alimenté par pression : bloc de construction d'une fusée. */
    public static final DeferredBlock<RocketPartBlock> ROCKET_ENGINE_PRESSURE = BLOCKS.registerBlock("rocket_engine_pressure",
            RocketPartBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 6.0F)
                    .sound(ROCKET_PART_SOUND)
                    .noOcclusion()
                    .noLootTable());
    /** Moteur à turbopompes (RP-1 / oxygène) : bloc de construction d'une fusée. */
    public static final DeferredBlock<RocketPartBlock> ROCKET_ENGINE_KEROLOX = BLOCKS.registerBlock("rocket_engine_kerolox",
            RocketPartBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 6.0F)
                    .sound(ROCKET_PART_SOUND)
                    .noOcclusion()
                    .noLootTable());
    /** Moteur à combustion étagée (RP-1 / oxygène) : bloc de construction d'une fusée lourde. */
    public static final DeferredBlock<RocketPartBlock> ROCKET_ENGINE_STAGED = BLOCKS.registerBlock("rocket_engine_staged",
            RocketPartBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 6.0F)
                    .sound(ROCKET_PART_SOUND)
                    .noOcclusion()
                    .noLootTable());
    /** Moteur méthane / oxygène : bloc de construction d'une fusée. */
    public static final DeferredBlock<RocketPartBlock> ROCKET_ENGINE_METHALOX = BLOCKS.registerBlock("rocket_engine_methalox",
            RocketPartBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 6.0F)
                    .sound(ROCKET_PART_SOUND)
                    .noOcclusion()
                    .noLootTable());
    /** Moteur cryogénique (hydrogène / oxygène) : bloc de construction d'une fusée. */
    public static final DeferredBlock<RocketPartBlock> ROCKET_ENGINE_HYDROLOX = BLOCKS.registerBlock("rocket_engine_hydrolox",
            RocketPartBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 6.0F)
                    .sound(ROCKET_PART_SOUND)
                    .noOcclusion()
                    .noLootTable());
    /** Réservoir en acier : bloc de construction d'une fusée. */
    public static final DeferredBlock<RocketPartBlock> ROCKET_TANK_STEEL = BLOCKS.registerBlock("rocket_tank_steel",
            RocketPartBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 6.0F)
                    .sound(ROCKET_PART_SOUND)
                    .noOcclusion()
                    .noLootTable());
    /** Réservoir en aluminium : bloc de construction d'une fusée. */
    public static final DeferredBlock<RocketPartBlock> ROCKET_TANK_ALUMINUM = BLOCKS.registerBlock("rocket_tank_aluminum",
            RocketPartBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 6.0F)
                    .sound(ROCKET_PART_SOUND)
                    .noOcclusion()
                    .noLootTable());
    /** Réservoir en aluminium-lithium : bloc de construction d'une fusée. */
    public static final DeferredBlock<RocketPartBlock> ROCKET_TANK_ALLOY = BLOCKS.registerBlock("rocket_tank_alloy",
            RocketPartBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 6.0F)
                    .sound(ROCKET_PART_SOUND)
                    .noOcclusion()
                    .noLootTable());
    /** Réservoir en composite : bloc de construction d'une fusée. */
    public static final DeferredBlock<RocketPartBlock> ROCKET_TANK_COMPOSITE = BLOCKS.registerBlock("rocket_tank_composite",
            RocketPartBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 6.0F)
                    .sound(ROCKET_PART_SOUND)
                    .noOcclusion()
                    .noLootTable());
    /** Réservoir cryogénique, sous sa mousse isolante : bloc de construction d'une fusée. */
    public static final DeferredBlock<RocketPartBlock> ROCKET_TANK_CRYO = BLOCKS.registerBlock("rocket_tank_cryo",
            RocketPartBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 6.0F)
                    .sound(ROCKET_PART_SOUND)
                    .noOcclusion());
    /** Booster à poudre : bloc de construction d'une fusée. La pièce elle-même le pose : il n'a pas d'objet à lui. */
    public static final DeferredBlock<RocketPartBlock> ROCKET_ENGINE_BOOSTER = BLOCKS.registerBlock("rocket_engine_booster",
            RocketPartBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 6.0F)
                    .sound(ROCKET_PART_SOUND)
                    .noOcclusion()
                    .noLootTable());
    /** Moteur hypergolique : bloc de construction d'une fusée. La pièce elle-même le pose : il n'a pas d'objet à lui. */
    public static final DeferredBlock<RocketPartBlock> ROCKET_ENGINE_HYPERGOLIC = BLOCKS.registerBlock("rocket_engine_hypergolic",
            RocketPartBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 6.0F)
                    .sound(ROCKET_PART_SOUND)
                    .noOcclusion()
                    .noLootTable());
    /** Moteur à vide : bloc de construction d'un étage supérieur. La pièce elle-même le pose : il n'a pas d'objet à lui. */
    public static final DeferredBlock<RocketPartBlock> ROCKET_ENGINE_VACUUM = BLOCKS.registerBlock("rocket_engine_vacuum",
            RocketPartBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 6.0F)
                    .sound(ROCKET_PART_SOUND)
                    .noOcclusion()
                    .noLootTable());
    /** Propulseur ionique : bloc de construction d'un étage supérieur. La pièce elle-même le pose : il n'a pas d'objet à lui. */
    public static final DeferredBlock<RocketPartBlock> ROCKET_ION_THRUSTER = BLOCKS.registerBlock("rocket_ion_thruster",
            RocketPartBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 6.0F)
                    .sound(ROCKET_PART_SOUND)
                    .noOcclusion()
                    .noLootTable());
    /** Voile solaire repliée dans son étui : bloc de construction d'un étage supérieur. La pièce elle-même le pose : il n'a pas d'objet à lui. */
    public static final DeferredBlock<RocketPartBlock> ROCKET_SOLAR_SAIL = BLOCKS.registerBlock("rocket_solar_sail",
            RocketPartBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 6.0F)
                    .sound(ROCKET_PART_SOUND)
                    .noOcclusion()
                    .noLootTable());
    /** Réservoir en titane : bloc de construction d'une fusée. La pièce elle-même le pose : il n'a pas d'objet à lui. */
    public static final DeferredBlock<RocketPartBlock> ROCKET_TANK_TITANIUM = BLOCKS.registerBlock("rocket_tank_titanium",
            RocketPartBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 6.0F)
                    .sound(ROCKET_PART_SOUND)
                    .noOcclusion()
                    .noLootTable());
    /** Grand réservoir en acier inoxydable : bloc de construction d'une fusée. La pièce elle-même le pose : il n'a pas d'objet à lui. */
    public static final DeferredBlock<RocketPartBlock> ROCKET_TANK_STAINLESS = BLOCKS.registerBlock("rocket_tank_stainless",
            RocketPartBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 6.0F)
                    .sound(ROCKET_PART_SOUND)
                    .noOcclusion()
                    .noLootTable());
    /** Moteur à fusion : bloc de construction d'un étage supérieur. La pièce elle-même le pose. */
    public static final DeferredBlock<RocketPartBlock> ROCKET_ENGINE_FUSION = BLOCKS.registerBlock("rocket_engine_fusion",
            RocketPartBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 6.0F)
                    .sound(ROCKET_PART_SOUND)
                    .lightLevel(state -> 7)
                    .noOcclusion()
                    .noLootTable());
    /** Moteur nucléaire thermique : bloc de construction d'un étage supérieur. La pièce elle-même le pose. */
    public static final DeferredBlock<RocketPartBlock> ROCKET_ENGINE_NUCLEAR = BLOCKS.registerBlock("rocket_engine_nuclear",
            RocketPartBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 6.0F)
                    .sound(ROCKET_PART_SOUND)
                    .noOcclusion()
                    .noLootTable());
    /** Corps de moteur à poudre : bloc de construction d'une fusée. */
    public static final DeferredBlock<RocketPartBlock> ROCKET_MOTOR_CASING = withItem(BLOCKS.registerBlock("rocket_motor_casing",
            RocketPartBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 6.0F)
                    .sound(ROCKET_PART_SOUND)
                    .noOcclusion()));
    /** Interétage : bloc de construction d'une fusée. */
    public static final DeferredBlock<RocketPartBlock> ROCKET_INTERSTAGE = BLOCKS.registerBlock("rocket_interstage",
            RocketPartBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 6.0F)
                    .sound(ROCKET_PART_SOUND)
                    .noOcclusion()
                    .noLootTable());
    /** Baie de charge utile : bloc de construction d'une fusée. */
    public static final DeferredBlock<RocketPartBlock> ROCKET_PAYLOAD_BAY = withItem(BLOCKS.registerBlock("rocket_payload_bay",
            RocketPartBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 6.0F)
                    .sound(ROCKET_PART_SOUND)
                    .noOcclusion()));
    /** Panneau de coiffe : bloc de construction d'une fusée. */
    public static final DeferredBlock<RocketPartBlock> ROCKET_FAIRING = withItem(BLOCKS.registerBlock("rocket_fairing",
            RocketPartBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 6.0F)
                    .sound(ROCKET_PART_SOUND)
                    .noOcclusion()));
    /** Pointe de coiffe : bloc de construction d'une fusée. */
    public static final DeferredBlock<RocketPartBlock> ROCKET_NOSE = BLOCKS.registerBlock("rocket_nose",
            RocketPartBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 6.0F)
                    .sound(ROCKET_PART_SOUND)
                    .noOcclusion()
                    .noLootTable());
    /** Cabine de l'équipage : bloc de construction d'une fusée. */
    public static final DeferredBlock<RocketPartBlock> CREW_CABIN = withItem(BLOCKS.registerBlock("crew_cabin",
            RocketPartBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 6.0F)
                    .sound(ROCKET_PART_SOUND)
                    .noOcclusion()));
    /** Haut de la cabine : bloc de construction d'une fusée. */
    public static final DeferredBlock<RocketPartBlock> CABIN_ROOF = withItem(BLOCKS.registerBlock("cabin_roof",
            RocketPartBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 6.0F)
                    .sound(ROCKET_PART_SOUND)
                    .noOcclusion()));
    /** Tour de sauvetage : bloc de construction d'une fusée. */
    public static final DeferredBlock<RocketPartBlock> ESCAPE_TOWER = withItem(BLOCKS.registerBlock("escape_tower",
            RocketPartBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 6.0F)
                    .sound(ROCKET_PART_SOUND)
                    .noOcclusion()));
    /** Panneau de coque : bloc de construction d'une fusée. */
    public static final DeferredBlock<RocketPartBlock> ROCKET_FUSELAGE = withItem(BLOCKS.registerBlock("rocket_fuselage",
            RocketPartBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 6.0F)
                    .sound(ROCKET_PART_SOUND)
                    .noOcclusion()));
    /** Chemin de câbles : bloc de construction d'une fusée. */
    public static final DeferredBlock<RocketPartBlock> ROCKET_RACEWAY = withItem(BLOCKS.registerBlock("rocket_raceway",
            RocketPartBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 6.0F)
                    .sound(ROCKET_PART_SOUND)
                    .noOcclusion()));
    /** Ordinateur de bord : bloc de construction d'une fusée. */
    public static final DeferredBlock<RocketPartBlock> FLIGHT_COMPUTER = withItem(BLOCKS.registerBlock("flight_computer",
            RocketPartBlock::new,
            properties -> properties
                    .mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 6.0F)
                    .sound(ROCKET_PART_SOUND)
                    .noOcclusion()));

    /** Flanc invisible d'une fusée de trois blocs de large. Sans objet : c'est la table qui le pose et le retire. */
    public static final DeferredBlock<RocketHullBlock> ROCKET_HULL = BLOCKS.registerBlock("rocket_hull",
            RocketHullBlock::new,
            properties -> properties
                    .mapColor(MapColor.SNOW)
                    .strength(1.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)
                    .noLootTable());

    /** Partie visible de la fusée dressée sur la table de lancement. Sans objet : c'est la table qui la pose et la retire. */
    public static final DeferredBlock<RocketBlock> ROCKET = BLOCKS.registerBlock("rocket",
            RocketBlock::new,
            properties -> properties
                    .mapColor(MapColor.SNOW)
                    .strength(1.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)
                    .noLootTable());

    // ------------------------------------------------------------------
    // Blocs de test (onglet créatif uniquement, sans recette)
    // ------------------------------------------------------------------
    /** Source d'énergie créative : alimente sans limite tout ce qui la touche. */
    public static final DeferredBlock<CreativeSourceBlock> CREATIVE_ENERGY_SOURCE = creativeSource("creative_energy_source",
            CreativeSourceBlock.Kind.ENERGY);
    /** Source de fluide créative : fournit sans limite le fluide choisi (clic droit pour en changer). */
    public static final DeferredBlock<CreativeSourceBlock> CREATIVE_FLUID_SOURCE = creativeSource("creative_fluid_source",
            CreativeSourceBlock.Kind.FLUID);

    /** Réservoirs créatifs : un bloc par fluide, qui le fournit sans limite à tout ce qui le touche. */
    public static final List<DeferredBlock<CreativeSourceBlock>> CREATIVE_TANKS = List.of(
            creativeTank("rp1", () -> ModFluids.RP1.get()),
            creativeTank("liquid_oxygen", () -> ModFluids.LIQUID_OXYGEN.get()),
            creativeTank("liquid_hydrogen", () -> ModFluids.LIQUID_HYDROGEN.get()),
            creativeTank("water", () -> Fluids.WATER),
            creativeTank("hydrogen", () -> ModFluids.HYDROGEN.get()),
            creativeTank("oxygen", () -> ModFluids.OXYGEN.get()),
            creativeTank("methane", () -> ModFluids.METHANE.get()),
            creativeTank("liquid_methane", () -> ModFluids.LIQUID_METHANE.get()),
            creativeTank("helium_3", () -> ModFluids.HELIUM_3.get()));

    private static DeferredBlock<CreativeSourceBlock> creativeTank(String fluid, Supplier<Fluid> content) {
        return withItem(BLOCKS.registerBlock("creative_tank_" + fluid,
                properties -> new CreativeSourceBlock(properties, CreativeSourceBlock.Kind.FLUID, content),
                properties -> properties
                        .mapColor(MapColor.COLOR_PURPLE)
                        .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                        .strength(3.5F, 6.0F)
                        .sound(SoundType.METAL)
                        .noOcclusion()
                        .lightLevel(state -> 6)));
    }

    private static DeferredBlock<CreativeSourceBlock> creativeSource(String name, CreativeSourceBlock.Kind kind) {
        return withItem(BLOCKS.registerBlock(name,
                properties -> new CreativeSourceBlock(properties, kind),
                properties -> properties
                        .mapColor(MapColor.COLOR_PURPLE)
                        .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                        .strength(3.5F, 6.0F)
                        .sound(SoundType.METAL)
                        .lightLevel(state -> 10)));
    }

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }

    /** Minerai dans la pierre : mêmes caractéristiques que le minerai de fer. */
    private static DeferredBlock<Block> stoneOre(String name) {
        return withItem(BLOCKS.registerBlock(name,
                properties -> new DropExperienceBlock(ConstantInt.of(0), properties),
                properties -> properties
                        .mapColor(MapColor.STONE)
                        .instrument(NoteBlockInstrument.BASEDRUM)
                        .requiresCorrectToolForDrops()
                        .strength(3.0F, 3.0F)));
    }

    /** Minerai dans l'ardoise des abîmes : plus long à miner, comme en vanilla. */
    private static DeferredBlock<Block> deepslateOre(String name) {
        return withItem(BLOCKS.registerBlock(name,
                properties -> new DropExperienceBlock(ConstantInt.of(0), properties),
                properties -> properties
                        .mapColor(MapColor.DEEPSLATE)
                        .instrument(NoteBlockInstrument.BASEDRUM)
                        .requiresCorrectToolForDrops()
                        .strength(4.5F, 3.0F)
                        .sound(SoundType.DEEPSLATE)));
    }

    /** Bloc de stockage d'un métal (9 lingots). */
    private static DeferredBlock<Block> metalBlock(String name, MapColor color) {
        return withItem(BLOCKS.registerSimpleBlock(name,
                properties -> properties
                        .mapColor(color)
                        .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                        .requiresCorrectToolForDrops()
                        .strength(5.0F, 6.0F)
                        .sound(SoundType.METAL)));
    }

    /** Machine : bloc métallique orientable, à modèle 3D, qui éclaire et s'anime quand il fonctionne. */
    private static DeferredBlock<MachineBlock> machine(String name,
            Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> blockEntityType) {
        return withItem(BLOCKS.registerBlock(name,
                properties -> new MachineBlock(properties, blockEntityType),
                properties -> properties
                        .mapColor(MapColor.METAL)
                        .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                        .requiresCorrectToolForDrops()
                        .strength(3.5F, 6.0F)
                        .sound(SoundType.METAL)
                        // Les machines ont des formes ajourées : elles ne masquent pas les blocs voisins.
                        .noOcclusion()
                        .lightLevel(state -> state.getValue(MachineBlock.LIT) ? 10 : 0)));
    }

    /** Plante de Proxima b : sans collision, cassée d'un coup ; {@code light}, sa lueur ; {@code replaceable}, si un bloc posé l'écrase. */
    private static DeferredBlock<com.orbitalis.phase3.celestial.ProximaPlantBlock> plant(String name, int light, boolean replaceable) {
        return withItem(BLOCKS.registerBlock(name, com.orbitalis.phase3.celestial.ProximaPlantBlock::new, properties -> {
            properties.mapColor(MapColor.COLOR_PURPLE).noCollision().instabreak().sound(SoundType.GRASS)
                    .offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY).lightLevel(state -> light);
            return replaceable ? properties.replaceable() : properties;
        }));
    }

    /** Conduit : bloc fin, qui ne masque pas les blocs voisins. */
    private static DeferredBlock<ConduitBlock> conduit(String name, BlockCapability<?, Direction> capability, ConduitBlock.Kind kind) {
        return withItem(BLOCKS.registerBlock(name,
                properties -> kind == ConduitBlock.Kind.ENERGY ? new ConduitBlock.Cable(properties, capability, kind)
                        : kind.isFluid() ? new ConduitBlock.Pipe(properties, capability, kind)
                        : new ConduitBlock(properties, capability, kind),
                properties -> properties
                        .mapColor(MapColor.METAL)
                        .strength(1.0F, 6.0F)
                        .sound(SoundType.METAL)
                        .noOcclusion()));
    }

    // ------------------------------------------------------------------
    // Centres spatiaux : les blocs de construction des bâtiments que l'on trouve dans le monde
    // ------------------------------------------------------------------
    /** Béton de coffrage : un panneau gris chaud, ses deux trous de banche. Murs, sols, socles. */
    public static final DeferredBlock<Block> FACILITY_CONCRETE = withItem(BLOCKS.registerBlock("facility_concrete", Block::new,
            properties -> concrete(properties, MapColor.COLOR_LIGHT_GRAY)));
    public static final DeferredBlock<net.minecraft.world.level.block.SlabBlock> FACILITY_CONCRETE_SLAB = withItem(BLOCKS.registerBlock(
            "facility_concrete_slab", net.minecraft.world.level.block.SlabBlock::new, properties -> concrete(properties, MapColor.COLOR_LIGHT_GRAY)));
    public static final DeferredBlock<net.minecraft.world.level.block.StairBlock> FACILITY_CONCRETE_STAIRS = withItem(BLOCKS.registerBlock(
            "facility_concrete_stairs", properties -> new net.minecraft.world.level.block.StairBlock(FACILITY_CONCRETE.get().defaultBlockState(), properties),
            properties -> concrete(properties, MapColor.COLOR_LIGHT_GRAY)));
    /** Le même béton, fissuré par les années : celui des centres abandonnés. */
    public static final DeferredBlock<Block> CRACKED_FACILITY_CONCRETE = withItem(BLOCKS.registerBlock("cracked_facility_concrete", Block::new,
            properties -> concrete(properties, MapColor.COLOR_LIGHT_GRAY)));
    /** Carrelage technique : des dalles claires antidérapantes, joints gris. */
    public static final DeferredBlock<Block> FACILITY_TILES = withItem(BLOCKS.registerBlock("facility_tiles", Block::new,
            properties -> concrete(properties, MapColor.QUARTZ)));
    /** Rayures de sécurité : jaune et noir, au bord des zones dangereuses. */
    public static final DeferredBlock<Block> HAZARD_STRIPES = withItem(BLOCKS.registerBlock("hazard_stripes", Block::new,
            properties -> concrete(properties, MapColor.COLOR_YELLOW)));
    /** Bardage métallique nervuré : blanc, bleu, ou rouillé dans les centres abandonnés. */
    public static final DeferredBlock<Block> FACILITY_SIDING = withItem(BLOCKS.registerBlock("facility_siding", Block::new,
            properties -> siding(properties, MapColor.SNOW)));
    public static final DeferredBlock<Block> BLUE_FACILITY_SIDING = withItem(BLOCKS.registerBlock("blue_facility_siding", Block::new,
            properties -> siding(properties, MapColor.COLOR_BLUE)));
    public static final DeferredBlock<Block> RUSTY_FACILITY_SIDING = withItem(BLOCKS.registerBlock("rusty_facility_siding", Block::new,
            properties -> siding(properties, MapColor.TERRACOTTA_WHITE)));
    /** Bardage orange : celui des bases polaires et des centres d'essais, qu'on voit de loin. */
    public static final DeferredBlock<Block> ORANGE_FACILITY_SIDING = withItem(BLOCKS.registerBlock("orange_facility_siding", Block::new,
            properties -> siding(properties, MapColor.COLOR_ORANGE)));
    /** Table à dessin : la planche d'un bureau d'études, où l'on étale un plan de fusée. */
    public static final DeferredBlock<com.orbitalis.phase1.rocket.DraftingTableBlock> DRAFTING_TABLE = withItem(BLOCKS.registerBlock("drafting_table",
            com.orbitalis.phase1.rocket.DraftingTableBlock::new,
            properties -> properties.mapColor(MapColor.COLOR_GRAY).instrument(NoteBlockInstrument.BASS).strength(1.5F, 3.0F)
                    .sound(SoundType.WOOD).noOcclusion()));
    /** Baie de serveurs : une armoire sombre, sa porte grillagée, ses voyants qui clignotent. Décorative. */
    public static final DeferredBlock<com.orbitalis.core.world.FacilityFurnitureBlock> SERVER_RACK = withItem(BLOCKS.registerBlock("server_rack",
            com.orbitalis.core.world.FacilityFurnitureBlock::new,
            properties -> properties.mapColor(MapColor.COLOR_BLACK).instrument(NoteBlockInstrument.IRON_XYLOPHONE).requiresCorrectToolForDrops()
                    .strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion().lightLevel(state -> 4)));
    /** Persiennes : des lames d'aération, pour les groupes de climatisation et les abris météo. */
    public static final DeferredBlock<Block> LOUVERED_PANEL = withItem(BLOCKS.registerBlock("louvered_panel", Block::new,
            properties -> siding(properties, MapColor.SNOW)));
    /** Panneau de radôme : la coque blanche, à facettes, qui protège une antenne radar. */
    public static final DeferredBlock<Block> RADOME_PANEL = withItem(BLOCKS.registerBlock("radome_panel", Block::new,
            properties -> properties.mapColor(MapColor.SNOW).instrument(NoteBlockInstrument.BASEDRUM).strength(1.5F, 4.0F).sound(SoundType.WOOL)));
    /** Vitrage de façade : un grand carreau teinté, cerclé de montants en aluminium. */
    public static final DeferredBlock<TransparentBlock> FACILITY_GLASS = withItem(BLOCKS.registerBlock("facility_glass", TransparentBlock::new,
            properties -> properties.mapColor(MapColor.NONE).instrument(NoteBlockInstrument.HAT).strength(0.6F, 3.0F).sound(SoundType.GLASS)
                    .noOcclusion().isViewBlocking((state, level, pos) -> false).isSuffocating((state, level, pos) -> false)
                    .isRedstoneConductor((state, level, pos) -> false).isValidSpawn((state, level, pos, type) -> false)));
    /** Grillage d'antenne : une tôle perforée, celle des paraboles de radiotélescope. On voit le ciel au travers. */
    public static final DeferredBlock<TransparentBlock> ANTENNA_MESH = withItem(BLOCKS.registerBlock("antenna_mesh", TransparentBlock::new,
            properties -> properties.mapColor(MapColor.METAL).instrument(NoteBlockInstrument.IRON_XYLOPHONE).requiresCorrectToolForDrops()
                    .strength(2.0F, 4.0F).sound(SoundType.METAL).noOcclusion().isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false).isValidSpawn((state, level, pos, type) -> false)));

    private static BlockBehaviour.Properties concrete(BlockBehaviour.Properties properties, MapColor color) {
        return properties.mapColor(color).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.8F, 6.0F);
    }

    private static BlockBehaviour.Properties siding(BlockBehaviour.Properties properties, MapColor color) {
        return properties.mapColor(color).instrument(NoteBlockInstrument.IRON_XYLOPHONE).requiresCorrectToolForDrops().strength(3.0F, 6.0F)
                .sound(SoundType.METAL);
    }

    /** Enregistre l'item correspondant au bloc. */
    private static <B extends Block> DeferredBlock<B> withItem(DeferredBlock<B> block) {
        ModItems.ITEMS.registerSimpleBlockItem(block);
        return block;
    }
}
