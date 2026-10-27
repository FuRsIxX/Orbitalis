package com.orbitalis.gametest;

import java.util.function.Consumer;

import net.minecraft.network.chat.Component;
import com.orbitalis.phase1.rocket.RocketBlueprintItem;
import com.orbitalis.phase1.rocket.RocketPlan;
import com.orbitalis.phase1.rocket.RocketCatalog;
import com.orbitalis.Orbitalis;
import com.orbitalis.core.creative.CreativeSourceBlock;
import java.util.List;
import com.orbitalis.phase1.rocket.RocketDesign;
import com.orbitalis.phase1.spaceport.LaunchComplex;
import net.minecraft.world.level.block.Block;
import com.orbitalis.phase1.spaceport.LaunchTableBlock;
import com.orbitalis.phase1.spaceport.LaunchPadBlock;
import com.orbitalis.phase1.spaceport.ManagedStructureBlock;
import com.orbitalis.phase1.spaceport.BoardingGateBlock;
import com.orbitalis.phase1.spaceport.PadPlan;
import com.orbitalis.phase1.spaceport.PadPartBlock;
import com.orbitalis.phase1.spaceport.FuelPortBlock;
import com.orbitalis.phase1.spaceport.ElevatorCabinEntity;
import com.orbitalis.phase1.spaceport.MissionDisplayBlock;
import com.orbitalis.phase1.spaceport.RocketBlock;
import com.orbitalis.phase1.spaceport.RocketBreakup;
import com.orbitalis.phase1.spaceport.RocketShape;
import com.orbitalis.phase1.spaceport.LaunchStep;
import com.orbitalis.phase1.spaceport.LaunchTableBlockEntity;
import com.orbitalis.phase1.rocket.RocketFuel;
import com.orbitalis.phase1.rocket.RocketItem;
import net.minecraft.world.phys.Vec3;
import com.orbitalis.core.registry.ModEntities;
import com.orbitalis.phase1.spaceport.FlightSimulation;
import com.orbitalis.phase1.spaceport.MissionDisplayBlockEntity;
import com.orbitalis.phase2.station.SolarPanelBlock;
import com.orbitalis.phase2.station.StationManager;
import com.orbitalis.phase2.station.StationModules;
import com.orbitalis.phase2.station.StationAir;
import com.orbitalis.phase2.station.LifeSupportBlockEntity;
import com.orbitalis.phase2.station.LifeSupportBlock;
import com.orbitalis.phase2.survival.SpaceRules;
import com.orbitalis.phase2.flight.CapsuleEntity;
import com.orbitalis.phase1.satellite.SatelliteType;
import com.orbitalis.phase1.satellite.SatelliteManager;
import com.orbitalis.phase1.satellite.Satellite;
import com.orbitalis.phase1.satellite.AgencySatellites;
import com.orbitalis.phase1.rocket.RocketParts;
import com.orbitalis.core.creative.CreativeRockets;
import com.orbitalis.core.network.PilotInputPayload;
import com.orbitalis.core.research.ResearchManager;
import net.minecraft.core.NonNullList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import com.orbitalis.phase1.rocket.RocketStats;
import com.mojang.serialization.JsonOps;
import com.orbitalis.core.research.AgencyResearch;
import com.orbitalis.core.research.ResearchNodes;
import net.minecraft.world.level.block.Blocks;
import com.orbitalis.phase1.conduit.ConduitBlock;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.registry.ModFluids;
import com.orbitalis.core.registry.ModItems;
import com.orbitalis.phase1.fuel.CryogenicTankBlockEntity;
import com.orbitalis.phase1.fuel.FluidTankBlock;
import com.orbitalis.phase1.fuel.FluidTankBlockEntity;
import com.orbitalis.phase1.fuel.FluidMachineBlockEntity;
import com.orbitalis.phase1.machines.CombustionGeneratorBlockEntity;
import com.orbitalis.phase1.machines.ProcessingMachineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Tests automatisés en jeu (gradlew runGameTestServer).
 * Ils posent de vrais blocs dans un monde de test et vérifient le résultat après quelques secondes.
 * En jeu, la commande /test permet aussi de les lancer.
 */
public final class OrbitalisGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, Orbitalis.MODID);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MACHINES_PROCESS_ITEMS =
            FUNCTIONS.register("machines_process_items", () -> OrbitalisGameTests::machinesProcessItems);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MACHINE_SLOT_RULES =
            FUNCTIONS.register("machine_slot_rules", () -> OrbitalisGameTests::machineSlotRules);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> FUEL_PRODUCTION_CHAIN =
            FUNCTIONS.register("fuel_production_chain", () -> OrbitalisGameTests::fuelProductionChain);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> HELIUM3_AND_METHANE =
            FUNCTIONS.register("helium3_and_methane", () -> OrbitalisGameTests::helium3AndMethane);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PROXIMA_WORLD =
            FUNCTIONS.register("proxima_world", () -> OrbitalisGameTests::proximaWorld);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ORBITAL_SHIPYARD =
            FUNCTIONS.register("orbital_shipyard", () -> OrbitalisGameTests::orbitalShipyard);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> FUSION_POWER =
            FUNCTIONS.register("fusion_power", () -> OrbitalisGameTests::fusionPower);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> REFINERY_MAKES_RP1 =
            FUNCTIONS.register("refinery_makes_rp1", () -> OrbitalisGameTests::refineryMakesRp1);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> CRYOGENIC_STORAGE =
            FUNCTIONS.register("cryogenic_storage", () -> OrbitalisGameTests::cryogenicStorage);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> CABLES_CARRY_ENERGY =
            FUNCTIONS.register("cables_carry_energy", () -> OrbitalisGameTests::cablesCarryEnergy);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PIPES_CARRY_FLUIDS =
            FUNCTIONS.register("pipes_carry_fluids", () -> OrbitalisGameTests::pipesCarryFluids);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> CAPSULE_SEATS =
            FUNCTIONS.register("capsule_seats", () -> OrbitalisGameTests::capsuleSeats);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PAD_TESTS =
            FUNCTIONS.register("pad_tests", () -> OrbitalisGameTests::padTests);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MODULAR_TANK =
            FUNCTIONS.register("modular_tank", () -> OrbitalisGameTests::modularTank);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PIPE_EXTRACTION =
            FUNCTIONS.register("pipe_extraction", () -> OrbitalisGameTests::pipeExtraction);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> DISPLAY_SOURCES =
            FUNCTIONS.register("display_sources", () -> OrbitalisGameTests::displaySources);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> CONDUITS_GLOW =
            FUNCTIONS.register("conduits_glow", () -> OrbitalisGameTests::conduitsGlow);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> RESEARCH_RULES =
            FUNCTIONS.register("research_rules", () -> OrbitalisGameTests::researchRules);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ROCKET_STATS =
            FUNCTIONS.register("rocket_stats", () -> OrbitalisGameTests::rocketStats);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> LAUNCH_COMPLEX =
            FUNCTIONS.register("launch_complex", () -> OrbitalisGameTests::launchComplex);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> LAUNCH_SEQUENCE =
            FUNCTIONS.register("launch_sequence", () -> OrbitalisGameTests::launchSequence);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> TANK_FEEDS_ROCKET =
            FUNCTIONS.register("tank_feeds_rocket", () -> OrbitalisGameTests::tankFeedsRocket);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> CREATIVE_SOURCES =
            FUNCTIONS.register("creative_sources", () -> OrbitalisGameTests::creativeSources);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ROCKET_SHAPES =
            FUNCTIONS.register("rocket_shapes", () -> OrbitalisGameTests::rocketShapes);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> HEAVY_LAUNCH_PAD =
            FUNCTIONS.register("heavy_launch_pad", () -> OrbitalisGameTests::heavyLaunchPad);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ROCKET_BUILD =
            FUNCTIONS.register("rocket_build", () -> OrbitalisGameTests::rocketBuild);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> TOWER_ELEVATOR =
            FUNCTIONS.register("tower_elevator", () -> OrbitalisGameTests::towerElevator);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> LAYER_BUILD =
            FUNCTIONS.register("layer_build", () -> OrbitalisGameTests::layerBuild);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> TEST_PAD =
            FUNCTIONS.register("test_pad", () -> OrbitalisGameTests::testPad);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> LAUNCH_STEPS =
            FUNCTIONS.register("launch_steps", () -> OrbitalisGameTests::launchSteps);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ORBIT_ELEMENTS =
            FUNCTIONS.register("orbit_elements", () -> OrbitalisGameTests::orbitElements);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MANOEUVRE_NODES =
            FUNCTIONS.register("manoeuvre_nodes", () -> OrbitalisGameTests::manoeuvreNodes);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> STRUCTURE_REACTS_INSTANTLY =
            FUNCTIONS.register("structure_reacts_instantly", () -> OrbitalisGameTests::structureReactsInstantly);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> SATELLITES =
            FUNCTIONS.register("satellites", () -> OrbitalisGameTests::satellites);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MARS_LANDING =
            FUNCTIONS.register("mars_landing", () -> OrbitalisGameTests::marsLanding);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> EUROPA =
            FUNCTIONS.register("europa", () -> OrbitalisGameTests::europa);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> DRONE_SORTIE =
            FUNCTIONS.register("drone_sortie", () -> OrbitalisGameTests::droneSortie);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> TITAN_LANDING =
            FUNCTIONS.register("titan_landing", () -> OrbitalisGameTests::titanLanding);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PILOTED_FLIGHT =
            FUNCTIONS.register("piloted_flight", () -> OrbitalisGameTests::pilotedFlight);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> CREWED_FLIGHT =
            FUNCTIONS.register("crewed_flight", () -> OrbitalisGameTests::crewedFlight);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> SPACE_RULES =
            FUNCTIONS.register("space_rules", () -> OrbitalisGameTests::spaceRules);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> STATION_AIR =
            FUNCTIONS.register("station_air", () -> OrbitalisGameTests::stationAir);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> STATION_MODULES =
            FUNCTIONS.register("station_modules", () -> OrbitalisGameTests::stationModules);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> FUELLING =
            FUNCTIONS.register("fuelling", () -> OrbitalisGameTests::fuelling);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> SOLAR_ARRAY =
            FUNCTIONS.register("solar_array", () -> OrbitalisGameTests::solarArray);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> DISPLAY_PAGES =
            FUNCTIONS.register("display_pages", () -> OrbitalisGameTests::displayPages);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PIPE_KINDS =
            FUNCTIONS.register("pipe_kinds", () -> OrbitalisGameTests::pipeKinds);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> FLIGHT_PHYSICS =
            FUNCTIONS.register("flight_physics", () -> OrbitalisGameTests::flightPhysics);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> DOCKING =
            FUNCTIONS.register("docking", () -> OrbitalisGameTests::docking);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MOON =
            FUNCTIONS.register("moon", () -> OrbitalisGameTests::moon);

    private OrbitalisGameTests() {
    }

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener(OrbitalisGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(Orbitalis.id("default"));
        Identifier emptyStructure = Identifier.withDefaultNamespace("empty");

        // Un seul générateur (20 FE/t) pour la fonderie et le laminoir : 8 000 FE à produire, 800 ticks (40 secondes) au maximum.
        event.registerTest(Orbitalis.id("machines_process_items"), new FunctionGameTestInstance(
                MACHINES_PROCESS_ITEMS.getKey(), spaced(environment, emptyStructure, 800)));
        event.registerTest(Orbitalis.id("machine_slot_rules"), new FunctionGameTestInstance(
                MACHINE_SLOT_RULES.getKey(), spaced(environment, emptyStructure, 20)));
        event.registerTest(Orbitalis.id("fuel_production_chain"), new FunctionGameTestInstance(
                FUEL_PRODUCTION_CHAIN.getKey(), spaced(environment, emptyStructure, 400)));
        event.registerTest(Orbitalis.id("refinery_makes_rp1"), new FunctionGameTestInstance(
                REFINERY_MAKES_RP1.getKey(), spaced(environment, emptyStructure, 300)));
        // L'extracteur d'hélium-3 cuit l'ilménite ; le réacteur de Sabatier fait du méthane.
        event.registerTest(Orbitalis.id("helium3_and_methane"), new FunctionGameTestInstance(
                HELIUM3_AND_METHANE.getKey(), spaced(environment, emptyStructure, 400)));
        // Proxima b : ses trois zones d'est en ouest, et sa flore qui ne pousse que sur la mousse et le régolithe.
        event.registerTest(Orbitalis.id("proxima_world"), new FunctionGameTestInstance(
                PROXIMA_WORLD.getKey(), spaced(environment, emptyStructure, 40)));
        // Le chantier naval s'accole à une station ; son pupitre pose tout le vaisseau.
        event.registerTest(Orbitalis.id("orbital_shipyard"), new FunctionGameTestInstance(
                ORBITAL_SHIPYARD.getKey(), spaced(environment, emptyStructure, 100)));
        // Le réacteur à fusion alimente, seul, une chambre de Casimir qui fait de la matière exotique.
        event.registerTest(Orbitalis.id("fusion_power"), new FunctionGameTestInstance(
                FUSION_POWER.getKey(), spaced(environment, emptyStructure, 1600)));
        event.registerTest(Orbitalis.id("cryogenic_storage"), new FunctionGameTestInstance(
                CRYOGENIC_STORAGE.getKey(), spaced(environment, emptyStructure, 100)));
        event.registerTest(Orbitalis.id("cables_carry_energy"), new FunctionGameTestInstance(
                CABLES_CARRY_ENERGY.getKey(), spaced(environment, emptyStructure, 400)));
        event.registerTest(Orbitalis.id("pipes_carry_fluids"), new FunctionGameTestInstance(
                PIPES_CARRY_FLUIDS.getKey(), spaced(environment, emptyStructure, 300)));
        event.registerTest(Orbitalis.id("capsule_seats"), new FunctionGameTestInstance(
                CAPSULE_SEATS.getKey(), spaced(environment, emptyStructure, 40)));
        event.registerTest(Orbitalis.id("pad_tests"), new FunctionGameTestInstance(
                PAD_TESTS.getKey(), spaced(environment, emptyStructure, 40)));
        event.registerTest(Orbitalis.id("modular_tank"), new FunctionGameTestInstance(
                MODULAR_TANK.getKey(), spaced(environment, emptyStructure, 40)));
        event.registerTest(Orbitalis.id("pipe_extraction"), new FunctionGameTestInstance(
                PIPE_EXTRACTION.getKey(), spaced(environment, emptyStructure, 200)));
        event.registerTest(Orbitalis.id("display_sources"), new FunctionGameTestInstance(
                DISPLAY_SOURCES.getKey(), spaced(environment, emptyStructure, 40)));
        event.registerTest(Orbitalis.id("rocket_build"), new FunctionGameTestInstance(
                ROCKET_BUILD.getKey(), spaced(environment, emptyStructure, 20)));
        event.registerTest(Orbitalis.id("tower_elevator"), new FunctionGameTestInstance(
                TOWER_ELEVATOR.getKey(), spaced(environment, emptyStructure, 20)));
        event.registerTest(Orbitalis.id("layer_build"), new FunctionGameTestInstance(
                LAYER_BUILD.getKey(), spaced(environment, emptyStructure, 20)));
        // Le pas de tir d'essai : sans tour, il lance une fusée-sonde à poudre et refuse le reste.
        event.registerTest(Orbitalis.id("test_pad"), new FunctionGameTestInstance(
                TEST_PAD.getKey(), spaced(environment, emptyStructure, 20)));
        event.registerTest(Orbitalis.id("launch_steps"), new FunctionGameTestInstance(
                LAUNCH_STEPS.getKey(), spaced(environment, emptyStructure, 20)));
        event.registerTest(Orbitalis.id("orbit_elements"), new FunctionGameTestInstance(
                ORBIT_ELEMENTS.getKey(), spaced(environment, emptyStructure, 20)));
        event.registerTest(Orbitalis.id("manoeuvre_nodes"), new FunctionGameTestInstance(
                MANOEUVRE_NODES.getKey(), spaced(environment, emptyStructure, 900)));
        event.registerTest(Orbitalis.id("structure_reacts_instantly"), new FunctionGameTestInstance(
                STRUCTURE_REACTS_INSTANTLY.getKey(), spaced(environment, emptyStructure, 20)));
        event.registerTest(Orbitalis.id("satellites"), new FunctionGameTestInstance(
                SATELLITES.getKey(), spaced(environment, emptyStructure, 20)));
        // Compte à rebours, montée, rentrée et descente sous parachutes : une minute et demie.
        event.registerTest(Orbitalis.id("crewed_flight"), new FunctionGameTestInstance(
                CREWED_FLIGHT.getKey(), spaced(environment, emptyStructure, 4000)));
        // Trois descentes propulsées : l'ordinateur de bord, un pilote qui ne pousse pas, un pilote qui dose sa poussée.
        event.registerTest(Orbitalis.id("mars_landing"), new FunctionGameTestInstance(
                MARS_LANDING.getKey(), spaced(environment, emptyStructure, 4500)));
        // Europe : le lanceur l'atteint, la lune est sans air, le cryobot y atteint l'océan.
        event.registerTest(Orbitalis.id("europa"), new FunctionGameTestInstance(
                EUROPA.getKey(), spaced(environment, emptyStructure, 200)));
        // Une sortie du drone volant : il part, se pose sur un site, et revient se poser chez lui.
        event.registerTest(Orbitalis.id("drone_sortie"), new FunctionGameTestInstance(
                DRONE_SORTIE.getKey(), spaced(environment, emptyStructure, 2400)));
        // Une rentrée vers Titan : le vaisseau arme ses parachutes, les ouvre et se pose.
        event.registerTest(Orbitalis.id("titan_landing"), new FunctionGameTestInstance(
                TITAN_LANDING.getKey(), spaced(environment, emptyStructure, 900)));
        // Trois vols avec un pilote à bord : l'orbite et le retour, l'éjection, l'écrasement.
        event.registerTest(Orbitalis.id("piloted_flight"), new FunctionGameTestInstance(
                PILOTED_FLIGHT.getKey(), spaced(environment, emptyStructure, 6000)));
        event.registerTest(Orbitalis.id("space_rules"), new FunctionGameTestInstance(
                SPACE_RULES.getKey(), spaced(environment, emptyStructure, 20)));
        event.registerTest(Orbitalis.id("station_air"), new FunctionGameTestInstance(
                STATION_AIR.getKey(), spaced(environment, emptyStructure, 300)));
        event.registerTest(Orbitalis.id("station_modules"), new FunctionGameTestInstance(
                STATION_MODULES.getKey(), spaced(environment, emptyStructure, 500)));
        event.registerTest(Orbitalis.id("fuelling"), new FunctionGameTestInstance(
                FUELLING.getKey(), spaced(environment, emptyStructure, 400)));
        event.registerTest(Orbitalis.id("solar_array"), new FunctionGameTestInstance(
                SOLAR_ARRAY.getKey(), spaced(environment, emptyStructure, 100)));
        event.registerTest(Orbitalis.id("display_pages"), new FunctionGameTestInstance(
                DISPLAY_PAGES.getKey(), spaced(environment, emptyStructure, 20)));
        event.registerTest(Orbitalis.id("pipe_kinds"), new FunctionGameTestInstance(
                PIPE_KINDS.getKey(), spaced(environment, emptyStructure, 100)));
        event.registerTest(Orbitalis.id("flight_physics"), new FunctionGameTestInstance(
                FLIGHT_PHYSICS.getKey(), spaced(environment, emptyStructure, 20)));
        event.registerTest(Orbitalis.id("docking"), new FunctionGameTestInstance(
                DOCKING.getKey(), spaced(environment, emptyStructure, 600)));
        event.registerTest(Orbitalis.id("moon"), new FunctionGameTestInstance(
                MOON.getKey(), spaced(environment, emptyStructure, 400)));
        event.registerTest(Orbitalis.id("heavy_launch_pad"), new FunctionGameTestInstance(
                HEAVY_LAUNCH_PAD.getKey(), spaced(environment, emptyStructure, 20)));
        event.registerTest(Orbitalis.id("rocket_shapes"), new FunctionGameTestInstance(
                ROCKET_SHAPES.getKey(), spaced(environment, emptyStructure, 20)));
        event.registerTest(Orbitalis.id("creative_sources"), new FunctionGameTestInstance(
                CREATIVE_SOURCES.getKey(), spaced(environment, emptyStructure, 200)));
        event.registerTest(Orbitalis.id("tank_feeds_rocket"), new FunctionGameTestInstance(
                TANK_FEEDS_ROCKET.getKey(), spaced(environment, emptyStructure, 200)));
        // Compte à rebours (30 s) puis vol (25 s) : le test dure un peu plus d'une minute.
        event.registerTest(Orbitalis.id("launch_sequence"), new FunctionGameTestInstance(
                LAUNCH_SEQUENCE.getKey(), spaced(environment, emptyStructure, 2200)));
        event.registerTest(Orbitalis.id("launch_complex"), new FunctionGameTestInstance(
                LAUNCH_COMPLEX.getKey(), spaced(environment, emptyStructure, 20)));
        event.registerTest(Orbitalis.id("rocket_stats"), new FunctionGameTestInstance(
                ROCKET_STATS.getKey(), spaced(environment, emptyStructure, 20)));
        event.registerTest(Orbitalis.id("research_rules"), new FunctionGameTestInstance(
                RESEARCH_RULES.getKey(), spaced(environment, emptyStructure, 20)));
        event.registerTest(Orbitalis.id("conduits_glow"), new FunctionGameTestInstance(
                CONDUITS_GLOW.getKey(), spaced(environment, emptyStructure, 300)));
    }

    /**
     * Chaîne complète : un électrolyseur rempli d'eau envoie l'hydrogène à gauche et l'oxygène à droite.
     * Chaque liquéfacteur ne reçoit donc qu'un gaz, le liquéfie, et remplit le réservoir posé au-dessus de lui.
     */
    private static void fuelProductionChain(GameTestHelper helper) {
        // Posé sans orientation, l'électrolyseur regarde le nord : sa gauche est à l'est (x + 1), sa droite à l'ouest.
        BlockPos electrolyzerPos = new BlockPos(1, 1, 0);
        BlockPos hydrogenLiquefierPos = new BlockPos(2, 1, 0);
        BlockPos oxygenLiquefierPos = new BlockPos(0, 1, 0);
        helper.setBlock(electrolyzerPos, ModBlocks.ELECTROLYZER.get());
        helper.setBlock(hydrogenLiquefierPos, ModBlocks.CRYOGENIC_LIQUEFIER.get());
        helper.setBlock(oxygenLiquefierPos, ModBlocks.CRYOGENIC_LIQUEFIER.get());
        helper.setBlock(hydrogenLiquefierPos.above(), ModBlocks.CRYOGENIC_TANK.get());
        helper.setBlock(oxygenLiquefierPos.above(), ModBlocks.CRYOGENIC_TANK.get());

        FluidMachineBlockEntity electrolyzer = helper.getBlockEntity(electrolyzerPos, FluidMachineBlockEntity.class);
        FluidMachineBlockEntity hydrogenLiquefier = helper.getBlockEntity(hydrogenLiquefierPos, FluidMachineBlockEntity.class);
        FluidMachineBlockEntity oxygenLiquefier = helper.getBlockEntity(oxygenLiquefierPos, FluidMachineBlockEntity.class);
        CryogenicTankBlockEntity hydrogenTank = helper.getBlockEntity(hydrogenLiquefierPos.above(), CryogenicTankBlockEntity.class);
        CryogenicTankBlockEntity oxygenTank = helper.getBlockEntity(oxygenLiquefierPos.above(), CryogenicTankBlockEntity.class);

        electrolyzer.getTanks().setFluid(0, new FluidStack(Fluids.WATER, 1000));
        electrolyzer.getEnergy().generate(20_000);
        hydrogenLiquefier.getEnergy().generate(20_000);
        oxygenLiquefier.getEnergy().generate(20_000);
        hydrogenTank.getEnergy().generate(20_000);
        oxygenTank.getEnergy().generate(20_000);

        helper.succeedWhen(() -> {
            helper.assertTrue(hydrogenTank.getTank().getFluid(0).getFluid() == ModFluids.LIQUID_HYDROGEN.get(),
                    "le réservoir de gauche n'a pas reçu d'hydrogène liquide");
            helper.assertTrue(oxygenTank.getTank().getFluid(0).getFluid() == ModFluids.LIQUID_OXYGEN.get(),
                    "le réservoir de droite n'a pas reçu d'oxygène liquide");
        });
    }

    /**
     * Blocs de test : la source d'énergie créative charge une machine à travers un câble, et la source de fluide
     * créative remplit un réservoir avec le fluide choisi.
     */
    private static void creativeSources(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 0), ModBlocks.CREATIVE_ENERGY_SOURCE.get());
        helper.setBlock(new BlockPos(1, 1, 0), ModBlocks.ENERGY_CABLE.get());
        BlockPos foundryPos = new BlockPos(2, 1, 0);
        helper.setBlock(foundryPos, ModBlocks.ARC_FOUNDRY.get());

        // Source réglée sur l'oxygène liquide (deuxième fluide de la liste).
        helper.setBlock(new BlockPos(0, 1, 2), ModBlocks.CREATIVE_FLUID_SOURCE.get().defaultBlockState()
                .setValue(CreativeSourceBlock.FLUID, 1));
        BlockPos tankPos = new BlockPos(1, 1, 2);
        helper.setBlock(tankPos, ModBlocks.CRYOGENIC_TANK.get());

        ProcessingMachineBlockEntity foundry = helper.getBlockEntity(foundryPos, ProcessingMachineBlockEntity.class);
        CryogenicTankBlockEntity tank = helper.getBlockEntity(tankPos, CryogenicTankBlockEntity.class);
        helper.succeedWhen(() -> {
            helper.assertTrue(foundry.getEnergy().getEnergy() == foundry.getEnergy().getCapacity(), "la fonderie n'est pas chargée");
            helper.assertTrue(tank.getTank().getFluid(0).getFluid() == ModFluids.LIQUID_OXYGEN.get()
                    && tank.getTank().getFluid(0).getAmount() == CryogenicTankBlockEntity.CAPACITY, "le réservoir n'est pas plein d'oxygène liquide");
        });
    }

    /**
     * Réglages d'un test : sa durée maximale, et une marge de quinze blocs tout autour de lui. Un pas de tir, avec sa
     * tour de cinq blocs de côté, déborde largement de la structure vide des tests : sans marge, il empiéterait sur
     * les tests voisins.
     */
    private static <T> TestData<T> spaced(T environment, Identifier structure, int maxTicks) {
        return new TestData<>(environment, structure, maxTicks, 0, true, Rotation.NONE, false, 1, 1, false, 15);
    }

    /**
     * Bâtit un pas de tir complet autour d'une table, d'après son plan : carneau vers l'est, tour à escalier contre le
     * bord ouest, et un bras ombilical tourné vers la fusée.
     *
     * @return la position du bras ombilical
     */
    private static BlockPos buildLaunchPad(GameTestHelper helper, BlockPos tablePos, int radius) {
        return buildLaunchPad(helper, tablePos, radius, false);
    }

    private static BlockPos buildLaunchPad(GameTestHelper helper, BlockPos tablePos, int radius, boolean elevator) {
        // Les tests bâtissent de petits pas de tir 3×3, qui tiennent dans leur zone d'essai : cette taille, retirée
        // du jeu, leur reste permise.
        PadPlan.retiredSizesAllowed = true;
        helper.setBlock(tablePos, ModBlocks.LAUNCH_TABLE.get());
        PadPlan plan = new PadPlan(radius, elevator, Direction.EAST);
        helper.getBlockEntity(tablePos, LaunchTableBlockEntity.class).setPlan(plan);
        BlockPos origin = helper.absolutePos(tablePos);
        for (PadPlan.Piece piece : plan.pieces()) {
            helper.getLevel().setBlockAndUpdate(origin.offset(piece.offset()), piece.display());
        }
        BlockPos arm = tablePos.offset(-radius, 3, 0);
        helper.setBlock(arm, ModBlocks.UMBILICAL_ARM.get());
        return arm;
    }

    /**
     * Remplace un bloc de charpente du pied de la tour par un bloc de ravitaillement, réglé sur un ergol.
     *
     * @return la case libre contre ce bloc, hors de la tour, où brancher un réservoir ou un tuyau
     */
    private static BlockPos placeFuelPort(GameTestHelper helper, BlockPos tablePos, int radius, FuelPortBlock.Kind kind) {
        helper.setBlock(tablePos.offset(-radius - 3, -1, 2), ModBlocks.FUEL_PORT.get().defaultBlockState().setValue(FuelPortBlock.KIND, kind));
        return tablePos.offset(-radius - 3, -1, 3);
    }

    /**
     * Promène le corps d'un joueur le long de l'escalier d'une tour, du sol au sommet, d'après les formes des blocs
     * du plan. Renvoie ce qu'il heurte, ou {@code null} s'il passe partout. On ne regarde que le corps au-dessus des
     * genoux : en dessous, ce sont les marches, que le joueur enjambe.
     */
    private static String stairBlocker(PadPlan plan) {
        java.util.Map<BlockPos, BlockState> blocks = new java.util.HashMap<>();
        List<PadPlan.Piece> flights = new java.util.ArrayList<>();
        for (PadPlan.Piece piece : plan.pieces()) {
            blocks.put(piece.offset(), piece.display());
            if (piece.role() == PadPlan.Role.TOWER && piece.block() == ModBlocks.TOWER_STAIRS.get()) {
                flights.add(piece);
            }
        }
        flights.sort(java.util.Comparator.comparingInt(PadPlan.Piece::level));
        for (PadPlan.Piece flight : flights) {
            BlockPos cell = flight.offset();
            Direction climb = flight.facing();
            boolean last = flight.level() == plan.towerLevels() - 1;
            // Le coin de départ, quatre points sur la volée, le coin d'arrivée : où sont les pieds à chaque fois.
            double[][] stops = {{-1.0, flight.level() > 0 ? 1.0 / 16.0 : 0.0}, {-0.375, 0.25}, {-0.125, 0.5}, {0.125, 0.75}, {0.375, 1.0},
                    {1.0, last ? 1.5 : 1.0 + 1.0 / 16.0}};
            for (double[] stop : stops) {
                double x = cell.getX() + 0.5 + climb.getStepX() * stop[0];
                double z = cell.getZ() + 0.5 + climb.getStepZ() * stop[0];
                double feet = cell.getY() + stop[1];
                AABB body = new AABB(x - 0.3, feet + 0.6, z - 0.3, x + 0.3, feet + 1.8, z + 0.3);
                for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(body.minX, body.minY, body.minZ),
                        BlockPos.containing(body.maxX - 1.0E-6, body.maxY - 1.0E-6, body.maxZ - 1.0E-6))) {
                    BlockState state = blocks.get(pos);
                    if (state == null) {
                        continue;
                    }
                    for (AABB box : state.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, pos).toAabbs()) {
                        if (box.move(pos).intersects(body)) {
                            return "à l'étage " + flight.level() + ", le joueur heurte " + state.getBlock() + " en " + pos.immutable();
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Le pas de tir assemblé se défait à l'instant où l'un de ses blocs est cassé, et se reforme à l'instant où
     * il est reposé : aucune attente.
     */
    private static void structureReactsInstantly(GameTestHelper helper) {
        BlockPos tablePos = new BlockPos(3, 2, 3);
        buildLaunchPad(helper, tablePos, 1);
        // Le portique d'embarquement du plan est dans la colonne technique, à un étage où l'escalier a un palier à l'avant.
        BlockPos riser = tablePos.offset(-2, -1, 0);
        helper.assertTrue(new PadPlan(1, false, Direction.EAST).gate() == 7, "le portique du plan 3×3 devrait être au septième étage");
        // L'escalier doit laisser passer un joueur : deux cases libres au-dessus de chaque volée (on y monte d'un bloc),
        // une au-dessus de chaque palier, jusque sous le platelage du sommet.
        for (int size = 1; size <= 5; size++) {
            PadPlan stairs = new PadPlan(size, false, Direction.EAST);
            java.util.Set<BlockPos> taken = new java.util.HashSet<>();
            for (PadPlan.Piece piece : stairs.pieces()) {
                taken.add(piece.offset());
            }
            for (PadPlan.Piece piece : stairs.pieces()) {
                if (piece.block() == ModBlocks.TOWER_STAIRS.get() || piece.block() == ModBlocks.TOWER_LANDING.get()) {
                    for (int up = 1; up <= (piece.block() == ModBlocks.TOWER_STAIRS.get() ? 2 : 1); up++) {
                        helper.assertTrue(!taken.contains(piece.offset().above(up)), "escalier du pas de tir " + size
                                + " : la case " + piece.offset().above(up) + " surplombe de trop près " + piece.offset());
                    }
                }
            }
        }
        // Et l'on doit pouvoir le gravir du sol au sommet, pour chaque taille et chaque hauteur de portique : le corps
        // d'un joueur, promené le long de l'escalier, ne doit rien heurter.
        for (int size = 1; size <= 5; size++) {
            for (int gate = 4; gate <= 7; gate++) {
                String blocker = stairBlocker(new PadPlan(size, false, Direction.EAST, gate));
                helper.assertTrue(blocker == null, "escalier du pas de tir " + size + ", portique à l'étage " + gate + " : " + blocker);
            }
        }
        LaunchTableBlockEntity table = helper.getBlockEntity(tablePos, LaunchTableBlockEntity.class);
        table.commission(true);

        BlockPos witness = tablePos.offset(1, 0, 1);
        // Le caillebotis du carneau s'efface dans un pas de tir assemblé ; la tour, elle, reste faite de ses blocs.
        BlockPos trenchPos = tablePos.offset(1, 0, 0);
        helper.assertTrue(table.getComplex().isComplete(), "le pas de tir devrait être complet : il manque "
                + table.getComplex().missing() + " bloc(s)");
        helper.assertTrue(helper.getBlockState(witness).getValue(LaunchPadBlock.LOOK) != LaunchPadBlock.Look.PLAIN
                && helper.getBlockState(trenchPos).getValue(ManagedStructureBlock.MANAGED), "le pas de tir devrait être assemblé");
        helper.assertTrue(!helper.getBlockState(riser).getValue(ManagedStructureBlock.MANAGED), "la tour doit rester faite de ses blocs");
        helper.assertTrue(table.getComplex().towerHeight() == 10, "le portique devrait compter dans la colonne technique");
        // La passerelle d'embarquement part du seuil du portique vers la fusée : son plancher est solide.
        BlockPos gangway = riser.offset(1, 7, 0);
        helper.assertTrue(table.getGateLevels().length == 1 && helper.getBlockState(gangway).is(ModBlocks.TOWER_STEP.get()),
                "le plancher de la passerelle d'embarquement manque");

        // Une dalle cassée : dans le même tick, les autres blocs redeviennent ordinaires.
        helper.setBlock(tablePos.offset(-1, 0, -1), Blocks.AIR);
        helper.assertTrue(helper.getBlockState(witness).getValue(LaunchPadBlock.LOOK) == LaunchPadBlock.Look.PLAIN
                && !helper.getBlockState(trenchPos).getValue(ManagedStructureBlock.MANAGED), "la structure ne s'est pas défaite aussitôt");
        helper.assertTrue(helper.getBlockState(gangway).isAir(), "la passerelle aurait dû être retirée");
        helper.assertTrue(LaunchTableBlock.missingParts(helper.getLevel(), table).size() == 2, "la table devrait dire quel bloc manque");

        // La dalle reposée : le pas de tir se reforme aussitôt.
        helper.setBlock(tablePos.offset(-1, 0, -1), ModBlocks.LAUNCH_PAD.get());
        helper.assertTrue(helper.getBlockState(witness).getValue(LaunchPadBlock.LOOK) != LaunchPadBlock.Look.PLAIN
                && helper.getBlockState(trenchPos).getValue(ManagedStructureBlock.MANAGED), "la structure ne s'est pas reformée aussitôt");
        helper.assertTrue(helper.getBlockState(gangway).is(ModBlocks.TOWER_STEP.get()), "la passerelle n'a pas été reposée");

        // Un escalier posé à l'envers est remis dans le bon sens par la table : seul le bloc compte.
        BlockPos stairs = riser.offset(-3, 0, 0);
        Direction right = helper.getBlockState(stairs).getValue(PadPartBlock.FACING);
        helper.setBlock(stairs, ModBlocks.TOWER_STAIRS.get().defaultBlockState().setValue(PadPartBlock.FACING, right.getOpposite()));
        table.refresh(helper.getLevel());
        helper.assertTrue(helper.getBlockState(stairs).getValue(PadPartBlock.FACING) == right, "l'escalier aurait dû être retourné");

        // Un bloc de charpente retiré au troisième étage : la tour ne compte plus que les étages du dessous.
        helper.setBlock(riser.offset(-4, 3, 1), Blocks.AIR);
        helper.assertTrue(table.getComplex().towerHeight() == 3 && !table.getComplex().isComplete(), "un étage incomplet ne doit pas compter");
        helper.succeed();
    }

    /**
     * Fusée bâtie bloc par bloc : tous les modèles du catalogue volent et tiennent sur un pas de tir ; un plan déposé
     * sur la table demande ses blocs un à un, la fusée se forme quand le dernier est posé, se défait si l'on en
     * casse un bloc (qui n'est pas remis), et se reforme quand on le repose.
     */
    private static void rocketBuild(GameTestHelper helper) {
        for (RocketCatalog.Template template : RocketCatalog.all()) {
            for (int payload = 0; payload < template.payloads().size(); payload++) {
                NonNullList<ItemStack> parts = RocketCatalog.build(template, payload, template.presets());
                RocketDesign design = RocketDesign.fromSlots(parts::get);
                RocketStats stats = RocketStats.of(design);
                helper.assertTrue(stats.isFlightReady(), "le modèle « " + template.id() + " » ne vole pas avec sa charge utile " + payload);
                boolean fits = false;
                // Parmi les tailles que le kit propose encore.
                for (int radius : PadPlan.SIZES) {
                    fits |= stats.liftoffMass() <= LaunchComplex.maxRocketMass(radius)
                            && RocketShape.of(design).height() <= LaunchComplex.maxRocketHeight(PadPlan.towerLevels(radius));
                }
                helper.assertTrue(fits, "aucun pas de tir n'accepte le modèle « " + template.id() + " »");
            }
        }
        // Chaque destination a son modèle au catalogue : le lanceur lourd va sur la Lune, le lanceur martien sur Mars.
        for (RocketCatalog.Template template : RocketCatalog.all()) {
            RocketStats.Reach wanted = template.id().equals("mars") ? RocketStats.Reach.MARS : template.id().equals("heavy") ? RocketStats.Reach.LUNAR : null;
            if (wanted != null) {
                RocketStats stats = RocketStats.of(RocketDesign.fromSlots(RocketCatalog.build(template, 0, template.presets())::get));
                helper.assertTrue(stats.reach().ordinal() >= wanted.ordinal(), "le modèle « " + template.id() + " » devrait atteindre " + wanted
                        + ", et non " + stats.reach() + " (" + (int) stats.totalDeltaV() + " m/s)");
            }
        }
        helper.assertTrue(RocketCatalog.all().stream().anyMatch(template -> template.id().equals("mars")), "le catalogue n'a pas de lanceur martien");
        // Un lanceur large de trois blocs a une couronne autour de chaque tronçon, avec un seul ordinateur de bord.
        RocketDesign heavy = RocketDesign.fromSlots(RocketCatalog.build(RocketCatalog.all().get(3), 0, RocketCatalog.all().get(3).presets())::get);
        java.util.Map<net.minecraft.world.item.Item, Integer> materials = RocketPlan.materials(heavy);
        helper.assertTrue(RocketShape.of(heavy).isWide() && materials.getOrDefault(ModBlocks.FLIGHT_COMPUTER.get().asItem(), 0) == 1
                && materials.getOrDefault(ModBlocks.ROCKET_RACEWAY.get().asItem(), 0) > 10 && materials.getOrDefault(ModItems.KEROLOX_ENGINE.get(), 0) == 3
                && materials.getOrDefault(ModItems.ALUMINUM_TANK.get(), 0) == 28,
                "plan du lanceur orbital incorrect : " + materials);

        BlockPos tablePos = new BlockPos(3, 2, 3);
        buildLaunchPad(helper, tablePos, 1);
        LaunchTableBlockEntity table = helper.getBlockEntity(tablePos, LaunchTableBlockEntity.class);
        table.commission(true);
        RocketCatalog.Template sounding = RocketCatalog.all().get(0);
        ItemStack blueprint = RocketBlueprintItem.create(RocketCatalog.build(sounding, 0, sounding.presets()), Component.literal("test"));
        helper.assertTrue(table.applyBlueprint(blueprint) == null && table.hasRocketPlan() && !table.hasRocket(), "le plan aurait dû être accepté");
        helper.assertTrue(table.applyBlueprint(blueprint) != null, "un pas de tir ne porte qu'un plan de fusée à la fois");

        List<RocketPlan.Piece> pieces = table.rocketPieces();
        helper.assertTrue(pieces.size() == RocketShape.of(RocketBlueprintItem.design(blueprint)).height(), "une fusée fine a un bloc par tronçon");
        // L'écran de chantier part de rien de posé, bloc par bloc dans l'ordre du plan.
        var site = table.construction(true);
        helper.assertTrue(!site.plan().isEmpty() && site.placed().size() == pieces.size() && site.placed().stream().noneMatch(Boolean::booleanValue),
                "le chantier devrait lister chaque bloc du plan, aucun encore posé");
        for (int i = 0; i < pieces.size(); i++) {
            helper.assertTrue(!table.hasRocket(), "la fusée ne doit pas se former avant son dernier bloc");
            helper.setBlock(tablePos.offset(pieces.get(i).offset()), pieces.get(i).block());
            // Les blocs de la fusée se dressent au-dessus de la table : ils ne doivent pas défaire le pas de tir.
            helper.assertTrue(table.isOperational(), "bâtir la fusée ne doit pas défaire le pas de tir");
        }
        helper.assertTrue(table.hasRocket() && helper.getBlockState(tablePos.above()).is(ModBlocks.ROCKET.get()),
                "la fusée aurait dû se former une fois son dernier bloc posé");
        helper.assertTrue(table.construction(false).plan().isEmpty(), "une fois la fusée formée, l'écran de chantier doit se fermer");
        // Le plan d'une fusée dressée se copie, comme une impression : une feuille de papier et la charge utile.
        net.minecraft.server.level.ServerPlayer copier = helper.makeMockServerPlayerInLevel();
        copier.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        copier.getInventory().add(new ItemStack(net.minecraft.world.item.Items.PAPER));
        copier.getInventory().add(new ItemStack(ModItems.SOUNDING_PAYLOAD.get()));
        net.minecraft.network.chat.Component copyProblem = table.copyPlan(copier);
        helper.assertTrue(copyProblem == null && copier.getInventory().countItem(ModItems.ROCKET_BLUEPRINT.get()) == 1
                && copier.getInventory().countItem(net.minecraft.world.item.Items.PAPER) == 0
                && copier.getInventory().countItem(ModItems.SOUNDING_PAYLOAD.get()) == 0, "la copie du plan devrait coûter le papier et la charge utile : "
                + (copyProblem == null ? "" : copyProblem.getString()) + " / plans " + copier.getInventory().countItem(ModItems.ROCKET_BLUEPRINT.get())
                + " / papier " + copier.getInventory().countItem(net.minecraft.world.item.Items.PAPER) + " / créatif " + copier.getAbilities().instabuild);
        helper.assertTrue(table.copyPlan(copier) != null, "sans papier ni charge utile, pas de copie");

        // Un tronçon cassé : la fusée se défait, ses blocs reviennent sauf celui-là.
        BlockPos broken = helper.absolutePos(tablePos.offset(pieces.get(1).offset()));
        helper.assertTrue(table.unformRocket(broken) && !table.hasRocket() && table.hasRocketPlan(), "la fusée aurait dû se défaire");
        helper.assertTrue(helper.getBlockState(tablePos.offset(pieces.get(0).offset())).is(pieces.get(0).block())
                && helper.getBlockState(tablePos.offset(pieces.get(1).offset())).isAir(), "les blocs de construction auraient dû revenir, sauf le bloc cassé");
        helper.setBlock(tablePos.offset(pieces.get(1).offset()), pieces.get(1).block());
        helper.assertTrue(table.hasRocket(), "la fusée aurait dû se reformer");

        // Le plan ne se reprend pas tant que la fusée est bâtie.
        helper.assertTrue(table.takeBlueprint().isEmpty() && table.unformRocket(null) && !table.takeBlueprint().isEmpty() && !table.hasRocketPlan(),
                "le plan ne devrait se reprendre qu'une fois la fusée défaite");
        for (RocketPlan.Piece piece : pieces) {
            helper.setBlock(tablePos.offset(piece.offset()), Blocks.AIR);
        }

        // Le bras d'assemblage : il bâtit la fusée-sonde avec les pièces d'un coffre, bloc par bloc, et le vide.
        helper.assertTrue(table.applyBlueprint(blueprint) == null, "le plan aurait dû être de nouveau accepté");
        BlockPos chestPos = tablePos.offset(4, 0, 4);
        helper.setBlock(chestPos, Blocks.CHEST);
        var chest = helper.getBlockEntity(chestPos, net.minecraft.world.level.block.entity.ChestBlockEntity.class);
        int slot = 0;
        for (var entry : RocketPlan.materials(RocketBlueprintItem.design(blueprint)).entrySet()) {
            chest.setItem(slot++, new ItemStack(entry.getKey(), entry.getValue()));
        }
        var handler = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, helper.absolutePos(chestPos), null);
        int gestures = 0;
        while (gestures < 40 && !table.hasRocket()
                && table.placeNext(List.of(handler)).result() == LaunchTableBlockEntity.ArmResult.PLACED) {
            gestures++;
        }
        helper.assertTrue(table.hasRocket() && chest.isEmpty(), "le bras aurait dû bâtir la fusée avec tout le coffre (" + gestures + " gestes)");
        helper.assertTrue(table.unformRocket(null) && !table.takeBlueprint().isEmpty(), "la fusée du bras aurait dû se défaire");
        for (RocketPlan.Piece piece : pieces) {
            helper.setBlock(tablePos.offset(piece.offset()), Blocks.AIR);
        }
        helper.setBlock(chestPos, Blocks.AIR);

        // Construction libre : sans plan, la table lit la colonne posée dans son axe. Un moteur à poudre, un seul
        // corps de moteur, une baie et une pointe : sous 5 t la fusée est fine, ses corps vont par deux ; rien ne se forme.
        Block[] column = {ModBlocks.ROCKET_ENGINE_SOLID.get(), ModBlocks.ROCKET_MOTOR_CASING.get(), ModBlocks.ROCKET_PAYLOAD_BAY.get(),
                ModBlocks.ROCKET_NOSE.get()};
        for (int k = 0; k < column.length; k++) {
            helper.setBlock(tablePos.above(k + 1), column[k]);
        }
        helper.assertTrue(table.offerCargo(new ItemStack(ModItems.SOUNDING_PAYLOAD.get())) == null && !table.getFreeCargo().isEmpty(),
                "la table aurait dû prendre la charge utile");
        helper.assertTrue(!table.hasRocketPlan() && !table.hasRocket(), "une colonne qui ne tombe pas juste ne doit rien former");
        for (int k = 0; k < column.length; k++) {
            helper.setBlock(tablePos.above(k + 1), Blocks.AIR);
        }
        // Avec deux corps de moteur, c'est la fusée-sonde : la table en tire le plan, et la fusée se forme.
        Block[] freeColumn = {ModBlocks.ROCKET_ENGINE_SOLID.get(), ModBlocks.ROCKET_MOTOR_CASING.get(), ModBlocks.ROCKET_MOTOR_CASING.get(),
                ModBlocks.ROCKET_PAYLOAD_BAY.get(), ModBlocks.ROCKET_NOSE.get()};
        for (int k = 0; k < freeColumn.length; k++) {
            helper.assertTrue(!table.hasRocket(), "la fusée libre s'est formée avant son dernier bloc");
            helper.setBlock(tablePos.above(k + 1), freeColumn[k]);
        }
        helper.assertTrue(table.hasRocket() && table.hasRocketPlan() && table.getFreeCargo().isEmpty(),
                "la fusée bâtie librement aurait dû être reconnue et se former");
        RocketDesign free = RocketItem.design(table.getRocket());
        helper.assertTrue(free.payload().is(ModItems.SOUNDING_PAYLOAD.get()) && free.stages().get(0).engines().is(ModItems.SOLID_MOTOR.get())
                && free.stages().get(0).engines().getCount() == 1 && RocketStats.of(free).isFlightReady(),
                "la fusée libre devrait être la fusée-sonde : un moteur à poudre et ses instruments");
        // Son plan se reprend comme un autre : c'est ainsi qu'on garde une invention.
        helper.assertTrue(table.unformRocket(null) && table.takeBlueprint().is(ModItems.ROCKET_BLUEPRINT.get()) && !table.hasRocketPlan(),
                "le plan d'une fusée libre devrait pouvoir se reprendre");
        for (int k = 0; k < freeColumn.length; k++) {
            helper.setBlock(tablePos.above(k + 1), Blocks.AIR);
        }
        // Le plan d'un lanceur à combustion étagée demande le bloc de ce moteur, et la lecture le reconnaît.
        RocketCatalog.Template marsTemplate = RocketCatalog.all().stream().filter(template -> template.id().equals("mars")).findFirst().orElseThrow();
        helper.assertTrue(RocketPlan.materials(RocketDesign.fromSlots(RocketCatalog.build(marsTemplate, 0, marsTemplate.presets())::get))
                .getOrDefault(ModItems.STAGED_COMBUSTION_ENGINE.get(), 0) == 3, "le lanceur martien devrait demander trois moteurs à combustion étagée");
        // Chaque pièce a son bloc, et le plan coûte exactement ce que la fusée emporte : un étage kérosène à six
        // réservoirs en titane, puis un étage à deux moteurs hypergoliques.
        RocketDesign.Stage noStage = new RocketDesign.Stage(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);
        RocketDesign mixed = new RocketDesign(List.of(
                new RocketDesign.Stage(new ItemStack(ModItems.KEROLOX_ENGINE.get()), new ItemStack(ModItems.TITANIUM_TANK.get(), 6), ItemStack.EMPTY),
                new RocketDesign.Stage(new ItemStack(ModItems.HYPERGOLIC_ENGINE.get(), 2), ItemStack.EMPTY, new ItemStack(ModItems.INTERSTAGE.get())),
                noStage), new ItemStack(ModItems.SOUNDING_PAYLOAD.get()), new ItemStack(ModItems.NOSE_CONE.get()));
        java.util.Map<net.minecraft.world.item.Item, Integer> mixedCost = RocketPlan.materials(mixed);
        List<RocketPlan.Piece> mixedPieces = RocketPlan.pieces(mixed, net.minecraft.core.Direction.EAST);
        helper.assertTrue(mixedCost.getOrDefault(ModItems.TITANIUM_TANK.get(), 0) == 6 && mixedCost.getOrDefault(ModItems.HYPERGOLIC_ENGINE.get(), 0) == 2
                && mixedCost.getOrDefault(ModItems.KEROLOX_ENGINE.get(), 0) == 1 && mixedCost.getOrDefault(ModItems.INTERSTAGE.get(), 0) == 1
                && mixedCost.getOrDefault(ModItems.NOSE_CONE.get(), 0) == 1
                && mixedPieces.stream().anyMatch(piece -> piece.block() == ModBlocks.ROCKET_ENGINE_HYPERGOLIC.get())
                && mixedPieces.stream().anyMatch(piece -> piece.block() == ModBlocks.ROCKET_TANK_TITANIUM.get()),
                "le plan devrait poser les blocs des pièces récentes et coûter ce que la fusée emporte : " + mixedCost);

        // Une fusée à ergols liquides exige un bloc de ravitaillement par ergol, au pied de la tour : elle ne se
        // forme pas sans eux, et la table les règle d'elle-même sur le bon ergol.
        RocketCatalog.Template liquid = RocketCatalog.all().get(1);
        helper.assertTrue(table.applyBlueprint(RocketBlueprintItem.create(RocketCatalog.build(liquid, 0, liquid.presets()), Component.literal("test"))) == null,
                "le plan de la fusée à ergols liquides aurait dû être accepté");
        List<LaunchTableBlockEntity.PortNeed> ports = table.requiredPorts();
        helper.assertTrue(ports.size() == 2 && ports.get(0).kind() == FuelPortBlock.Kind.RP1 && ports.get(1).kind() == FuelPortBlock.Kind.LOX,
                "une fusée kérosène-oxygène demande deux blocs de ravitaillement");
        for (RocketPlan.Piece piece : table.rocketPieces()) {
            helper.setBlock(tablePos.offset(piece.offset()), piece.block());
        }
        helper.assertTrue(!table.hasRocket() && table.missingPorts(helper.getLevel()).size() == 2, "la fusée ne doit pas se former sans ses blocs de ravitaillement");
        for (LaunchTableBlockEntity.PortNeed port : ports) {
            helper.setBlock(tablePos.offset(port.offset()), ModBlocks.FUEL_PORT.get());
        }
        helper.assertTrue(helper.getBlockState(tablePos.offset(ports.get(1).offset())).getValue(FuelPortBlock.KIND) == FuelPortBlock.Kind.LOX,
                "le bloc de ravitaillement aurait dû être réglé sur l'oxygène liquide");
        helper.assertTrue(table.hasRocket() && table.isOperational(), "la fusée aurait dû se former une fois ses blocs de ravitaillement posés");
        helper.succeed();
    }

    /**
     * Pose par couche : un clic sur la table pose la couche la plus basse du plan avec les blocs du joueur. En mode
     * survie sans rien sur soi, rien n'est posé ; en mode créatif, couche après couche, tout le pas de tir se bâtit.
     */
    private static void layerBuild(GameTestHelper helper) {
        BlockPos tablePos = new BlockPos(3, 2, 3);
        helper.setBlock(tablePos, ModBlocks.LAUNCH_TABLE.get());
        LaunchTableBlockEntity table = helper.getBlockEntity(tablePos, LaunchTableBlockEntity.class);
        PadPlan plan = new PadPlan(1, true, Direction.EAST);
        table.setPlan(plan);
        // L'emprise du pas de tir déborde sur la clôture de la zone de test : on la dégage.
        for (PadPlan.Piece piece : plan.pieces()) {
            helper.getLevel().setBlockAndUpdate(helper.absolutePos(tablePos).offset(piece.offset()), Blocks.AIR.defaultBlockState());
        }
        helper.assertTrue(table.buildLayer(helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL)) == 0,
                "sans bloc sur soi, rien ne devrait être posé");
        net.minecraft.world.entity.player.Player builder = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        builder.getInventory().add(new ItemStack(ModBlocks.FLAME_DEFLECTOR.get()));
        helper.assertTrue(table.buildLayer(builder) == 1 && builder.getInventory().isEmpty()
                && helper.getBlockState(tablePos.below()).is(ModBlocks.FLAME_DEFLECTOR.get()),
                "le déflecteur du joueur aurait dû être posé sous la table, et retiré de son inventaire");
        net.minecraft.world.entity.player.Player creative = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        int clicks = 0;
        while (clicks++ < 40 && table.buildLayer(creative) > 0) {
            // Une couche par clic.
        }
        table.commission(true);
        helper.assertTrue(table.getComplex().isComplete(), "le pas de tir devrait être complet après la pose par couche : il manque "
                + table.getComplex().missing() + " bloc(s)");
        helper.assertTrue(clicks <= 16 && table.getGateLevels().length == 1, "la pose par couche a pris " + clicks + " clics");
        helper.succeed();
    }

    /** Rebâtit ce qu'un accident a détruit d'un pas de tir, d'après son plan, et vérifie qu'il est de nouveau en service. */
    private static void repairPad(GameTestHelper helper, BlockPos tablePos, LaunchTableBlockEntity table) {
        BlockPos origin = helper.absolutePos(tablePos);
        for (PadPlan.Piece piece : table.getPlan().pieces()) {
            if (!piece.accepts(helper.getLevel().getBlockState(origin.offset(piece.offset())))) {
                helper.getLevel().setBlockAndUpdate(origin.offset(piece.offset()), piece.display());
            }
        }
        table.refresh(helper.getLevel());
        helper.assertTrue(table.isOperational(), "rebâti, le pas de tir devrait être de nouveau en service : il manque "
                + table.getComplex().missing() + " bloc(s)");
    }

    /** Mène un vol jusqu'au bout en commandant chaque étape dès que sa fenêtre s'ouvre, comme un équipage attentif. */
    private static void flyAttentively(GameTestHelper helper, LaunchTableBlockEntity table, String agency) {
        LaunchStep[] steps = {LaunchStep.THROTTLE, LaunchStep.STAGE, LaunchStep.FAIRING, LaunchStep.CUTOFF};
        for (int tick = 0; tick <= com.orbitalis.phase1.spaceport.FlightProfile.MAX_FLIGHT_TICKS + 1 && table.getPhase() == LaunchTableBlockEntity.Phase.FLIGHT; tick++) {
            int open = table.flightOpen();
            for (int k = 0; k < steps.length; k++) {
                if ((open & 1 << k) != 0) {
                    helper.assertTrue(table.command(steps[k], agency) == null, "l'étape ouverte " + steps[k] + " aurait dû être acceptée");
                }
            }
            table.serverTick(helper.getLevel());
        }
    }

    /** Fait avancer une table de lancement d'un nombre de ticks, à la main : le test ne dépend pas de l'horloge du monde. */
    private static void tickTable(GameTestHelper helper, LaunchTableBlockEntity table, int ticks) {
        for (int i = 0; i < ticks; i++) {
            table.serverTick(helper.getLevel());
        }
    }

    /** Capsule à trois places : trois joueurs y montent, pas un quatrième ; le premier monté est le pilote, chacun a son siège. */
    @SuppressWarnings("removal")
    private static void capsuleSeats(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        CapsuleEntity capsule = new CapsuleEntity(ModEntities.CAPSULE.get(), helper.getLevel());
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        capsule.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        helper.getLevel().addFreshEntity(capsule);
        java.util.List<net.minecraft.server.level.ServerPlayer> crew = new java.util.ArrayList<>();
        for (int i = 0; i < CapsuleEntity.SEATS; i++) {
            net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
            crew.add(player);
            helper.assertTrue(player.startRiding(capsule), "le joueur " + (i + 1) + " aurait dû pouvoir monter à bord");
        }
        net.minecraft.server.level.ServerPlayer extra = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(!extra.startRiding(capsule), "une capsule à trois places ne prend pas un quatrième passager");
        helper.assertTrue(capsule.isPilot(crew.get(0)) && !capsule.isPilot(crew.get(1)) && !capsule.isPilot(crew.get(2)),
                "seul le premier monté est le pilote");
        capsule.tick();
        // Les deux équipiers sont derrière le pilote (plus au sud), de part et d'autre.
        double pilotX = crew.get(0).getX();
        helper.assertTrue(crew.get(1).getZ() > crew.get(0).getZ() + 0.2 && crew.get(2).getZ() > crew.get(0).getZ() + 0.2
                && (crew.get(1).getX() - pilotX) * (crew.get(2).getX() - pilotX) < 0, "les équipiers devraient être assis derrière le pilote, de part et d'autre");
        crew.forEach(net.minecraft.world.entity.Entity::stopRiding);
        extra.discard();
        crew.forEach(net.minecraft.world.entity.Entity::discard);
        capsule.discard();
        helper.succeed();
    }

    /**
     * Essais du pas de tir et surpression. Sans fusée, le bras s'écarte puis revient de lui-même. Avec une fusée
     * pleine, des vannes laissées ouvertes font monter la pression : refermées à temps, elle retombe ; laissées
     * ouvertes trente secondes, les réservoirs cèdent et la fusée explose.
     */
    private static void padTests(GameTestHelper helper) {
        BlockPos tablePos = new BlockPos(3, 2, 3);
        buildLaunchPad(helper, tablePos, 1);
        LaunchTableBlockEntity table = helper.getBlockEntity(tablePos, LaunchTableBlockEntity.class);
        table.commission(true);
        table.refresh(helper.getLevel());
        String agency = "test:pad_tests";
        RocketBreakup.forced = RocketBreakup.Damage.PAD_ONLY;

        // Essai des bras, sans fusée : ils s'écartent, rien d'autre ne se commande, puis ils reviennent.
        helper.assertTrue(table.request(LaunchStep.TEST_ARMS, agency) == null && table.isRetracted(), "l'essai des bras aurait dû les écarter");
        helper.assertTrue(table.request(LaunchStep.TEST_ALARM, agency) != null, "pendant un essai, rien d'autre ne se commande");
        helper.assertTrue(table.request(LaunchStep.TEST_DELUGE, agency) != null, "un pas de tir sans déluge n'a pas d'essai de déluge");
        tickTable(helper, table, 2);
        helper.assertTrue(table.isRetracted(), "les bras ne reviennent qu'à la fin de l'essai");

        // Surpression : fusée pleine, vannes ouvertes.
        NonNullList<ItemStack> liquid = NonNullList.withSize(RocketDesign.SLOT_COUNT, ItemStack.EMPTY);
        liquid.set(RocketDesign.SLOT_NOSE, new ItemStack(ModItems.NOSE_CONE.get()));
        liquid.set(RocketDesign.engineSlot(0), new ItemStack(ModItems.PRESSURE_FED_ENGINE.get(), 2));
        liquid.set(RocketDesign.tankSlot(0), new ItemStack(ModItems.STEEL_TANK.get()));
        helper.assertTrue(table.setRocket(RocketItem.create(liquid)), "la fusée à ergols liquides n'a pas été posée");
        // Poser une fusée remet la séquence à zéro ou non : dans les deux cas, le bras doit être en place pour le plein.
        if (table.isRetracted()) {
            table.command(LaunchStep.RETRACT, agency);
        }
        RocketFuel needed = table.requiredFuel();
helper.assertTrue(table.command(LaunchStep.VALVES, agency) == null && table.areValvesOpen(), "les vannes auraient dû s'ouvrir");
        // Le parc à ergols pousse sans arrêt, comme un réservoir relié au pied de la tour.
        java.util.function.IntConsumer supply = ticks -> {
            for (int i = 0; i < ticks; i++) {
                table.fill(ModFluids.RP1.get(), 100, false);
                table.fill(ModFluids.LIQUID_OXYGEN.get(), 100, false);
                table.serverTick(helper.getLevel());
            }
        };
        table.fill(ModFluids.RP1.get(), needed.rp1(), false);
        table.fill(ModFluids.LIQUID_OXYGEN.get(), needed.oxygen() / 2, false);
        helper.assertTrue(table.overpressurePercent() == 0, "tant que le plein n'est pas fait, la pression ne monte pas");
        table.fill(ModFluids.LIQUID_OXYGEN.get(), needed.oxygen(), false);
        // Plein fait, mais plus rien n'arrive : il ne se passe rien.
        tickTable(helper, table, 60);
        helper.assertTrue(table.overpressurePercent() == 0, "sans rien qui arrive, la pression ne monte pas");
        supply.accept(LaunchTableBlockEntity.OVERPRESSURE_TICKS / 2);
        int half = table.overpressurePercent();
        helper.assertTrue(half >= 45 && half <= 55 && table.hasRocket(), "à mi-chemin, la surpression devrait être de 50 %, pas de " + half);
        // Vannes refermées : la pression retombe, et la fusée est sauve.
        table.command(LaunchStep.VALVES, agency);
        supply.accept(LaunchTableBlockEntity.OVERPRESSURE_TICKS / 3);
        helper.assertTrue(table.overpressurePercent() == 0 && table.hasRocket(), "vannes refermées, la pression aurait dû retomber");
        // Vannes rouvertes et oubliées : au bout de trente secondes, la fusée explose.
        table.command(LaunchStep.VALVES, agency);
        supply.accept(LaunchTableBlockEntity.OVERPRESSURE_TICKS - 2);
        helper.assertTrue(table.hasRocket(), "la fusée ne doit pas céder avant la fin du délai");
        supply.accept(4);
        helper.assertTrue(!table.hasRocket() && table.getLastResult() == LaunchTableBlockEntity.Result.FAILURE,
                "des vannes oubliées ouvertes auraient dû faire exploser la fusée");
        helper.succeed();
    }

    /**
     * Séquence de lancement menée à la main. Rien n'y est dû au hasard : chaque oubli a sa conséquence, toujours la
     * même. Une séquence bien menée fait décoller la fusée ; lâcher les attaches avant la pleine poussée la fait
     * retomber ; oublier le bras le fait arracher ; sans pression, rien ne s'allume ; un plein incomplet éteint les
     * moteurs en vol ; des attaches jamais lâchées font couper les moteurs.
     */
    /**
     * L'orbite instantanée de la montée : au départ, rien ; une fusée-sonde garde un périgée sous la surface ; un lanceur
     * orbital finit avec un périgée au-dessus de l'atmosphère, et une apogée proche de l'orbite visée.
     */
    private static void orbitElements(GameTestHelper helper) {
        var templates = RocketCatalog.all();
        RocketDesign sounding = RocketDesign.fromSlots(RocketCatalog.build(templates.get(0), 0, templates.get(0).presets())::get);
        RocketDesign orbital = RocketDesign.fromSlots(RocketCatalog.build(templates.get(3), 0, templates.get(3).presets())::get);
        com.orbitalis.phase1.rocket.RocketStats soundingStats = com.orbitalis.phase1.rocket.RocketStats.of(sounding);
        com.orbitalis.phase1.rocket.RocketStats orbitalStats = com.orbitalis.phase1.rocket.RocketStats.of(orbital);
        double[] start = com.orbitalis.phase1.spaceport.FlightProfile.orbitKm(orbitalStats, 0.0);
        helper.assertTrue(start[0] < 1.0 && start[1] < 0.0, "au départ, ni apogée ni périgée : " + start[0] + " / " + start[1]);
        double[] hop = com.orbitalis.phase1.spaceport.FlightProfile.orbitKm(soundingStats, 1.0);
        helper.assertTrue(hop[1] < 0.0, "une fusée-sonde ne se met pas en orbite : périgée " + hop[1]);
        double[] end = com.orbitalis.phase1.spaceport.FlightProfile.orbitKm(orbitalStats, 1.0);
        helper.assertTrue(end[1] > 100.0 && end[0] > 150.0 && end[0] < 400.0,
                "le lanceur orbital aurait dû finir en orbite : apogée " + end[0] + ", périgée " + end[1]);
        helper.succeed();
    }

    /**
     * Les manœuvres en orbite : un appui prépare le freinage de retour ; une fenêtre manquée est reprogrammée ; l'autre
     * touche annule une manœuvre pas encore allumée ; tenue au top, la touche allume, et la poussée, coupée par
     * l'ordinateur de bord au-delà de 110 %, lance la rentrée.
     */
    private static void manoeuvreNodes(GameTestHelper helper) {
        CapsuleEntity capsule = new CapsuleEntity(com.orbitalis.core.registry.ModEntities.CAPSULE.get(), helper.getLevel());
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        capsule.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        capsule.parkInOrbit();
        helper.getLevel().addFreshEntity(capsule);
        net.minecraft.server.level.ServerPlayer pilot = helper.makeMockServerPlayerInLevel();
        pilot.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        helper.assertTrue(pilot.startRiding(capsule, true, false), "le pilote n'a pas pu monter à bord");
        int[] step = {0};
        int[] wait = {0};
        helper.onEachTick(() -> {
            CapsuleEntity.Phase phase = capsule.getPhase();
            switch (step[0]) {
                case 0 -> {
                    // Un appui : le freinage est préparé, son compte à rebours commence.
                    capsule.setControls(PilotInputPayload.RETURN);
                    step[0] = 1;
                }
                case 1 -> {
                    if (capsule.getNode() == CapsuleEntity.NODE_NONE) {
                        helper.assertTrue(++wait[0] < 5, "un appui sur la touche de retour aurait dû préparer le freinage");
                        return;
                    }
                    capsule.setControls(0);
                    helper.assertTrue(capsule.getNode() == CapsuleEntity.NODE_DEORBIT && capsule.nodeCountdown() > 0,
                            "le freinage préparé devrait attendre sa fenêtre");
                    wait[0] = 0;
                    step[0] = 2;
                }
                case 2 -> {
                    // Personne n'allume : la fenêtre s'ouvre, passe, et la manœuvre est reprogrammée.
                    if (capsule.nodeCountdown() <= 0) {
                        wait[0] = 1;
                        return;
                    }
                    if (wait[0] == 0) {
                        return;
                    }
                    wait[0] = 0;
                    helper.assertTrue(capsule.getNode() == CapsuleEntity.NODE_DEORBIT && capsule.nodeCountdown() > 0 && phase == CapsuleEntity.Phase.ORBIT,
                            "une fenêtre manquée aurait dû être reprogrammée");
                    // L'autre touche annule la manœuvre.
                    capsule.setControls(PilotInputPayload.MOON);
                    step[0] = 3;
                }
                case 3 -> {
                    capsule.setControls(0);
                    helper.assertTrue(capsule.getNode() == CapsuleEntity.NODE_NONE, "l'autre touche aurait dû annuler la manœuvre");
                    step[0] = 4;
                }
                case 4 -> {
                    // De nouveau préparé, et la touche tenue : au top, elle allume ; la poussée finie, c'est la rentrée.
                    capsule.setControls(PilotInputPayload.RETURN);
                    if (phase == CapsuleEntity.Phase.ORBIT) {
                        helper.assertTrue(++wait[0] < 400, "la poussée n'a jamais lancé la rentrée");
                        return;
                    }
                    capsule.setControls(0);
                    helper.assertTrue(phase == CapsuleEntity.Phase.REENTRY && capsule.getNode() == CapsuleEntity.NODE_NONE,
                            "le freinage réussi aurait dû lancer la rentrée : " + phase);
                    capsule.discard();
                    helper.succeed();
                }
                default -> {
                }
            }
        });
    }

    /**
     * Pas de tir d'essai : un 3×3 sans tour, sans mâts ni bras. Il est en service une fois son tablier posé, refuse les
     * fusées à ergols liquides et les capsules habitées, et lance une fusée-sonde à poudre sans qu'on ait de bras à retirer.
     */
    private static void testPad(GameTestHelper helper) {
        BlockPos tablePos = new BlockPos(3, 2, 3);
        helper.setBlock(tablePos, ModBlocks.LAUNCH_TABLE.get());
        LaunchTableBlockEntity table = helper.getBlockEntity(tablePos, LaunchTableBlockEntity.class);
        PadPlan plan = PadPlan.test(Direction.EAST);
        table.setPlan(plan);
        for (PadPlan.Piece piece : plan.pieces()) {
            helper.assertTrue(piece.role() != PadPlan.Role.TOWER && piece.role() != PadPlan.Role.MAST && piece.role() != PadPlan.Role.ARM
                    && piece.role() != PadPlan.Role.ROOF, "le pas de tir d'essai ne devrait avoir ni tour, ni mâts, ni bras");
            helper.getLevel().setBlockAndUpdate(helper.absolutePos(tablePos).offset(piece.offset()), piece.display());
        }
        table.commission(true);
        LaunchComplex complex = table.getComplex();
        helper.assertTrue(complex.compact() && complex.isComplete() && table.isOperational() && !table.isRetiredSize(),
                "le pas de tir d'essai complet devrait être en service : il manque " + complex.missing() + " bloc(s)");
        helper.assertTrue(complex.towerHeight() == 0 && complex.arms() == 0 && complex.towerBase() == null
                && complex.maxRocketMass() == 20_000 && complex.maxRocketHeight() == LaunchComplex.TEST_PAD_HEIGHT
                && table.requiredTowerHeight() == 0, "capacités du pas de tir d'essai incorrectes");
        helper.assertTrue(PadPlan.forKit(1, true, Direction.NORTH).compact() && !PadPlan.forKit(2, false, Direction.NORTH).compact(),
                "le kit devrait proposer le 3×3 comme pas de tir d'essai, et les autres tailles avec leur tour");

        // Ni ergols liquides (pas de bras pour le plein), ni équipage (pas de passerelle).
        helper.assertTrue(table.testPadProblem(CreativeRockets.all().get(1)) != null, "une fusée à ergols liquides devrait être refusée");
        NonNullList<ItemStack> crewed = NonNullList.withSize(RocketDesign.SLOT_COUNT, ItemStack.EMPTY);
        crewed.set(RocketDesign.SLOT_PAYLOAD, new ItemStack(ModItems.CREW_CAPSULE.get()));
        crewed.set(RocketDesign.engineSlot(0), new ItemStack(ModItems.SOLID_MOTOR.get()));
        helper.assertTrue(table.testPadProblem(RocketItem.create(crewed)) != null, "une capsule habitée devrait être refusée");

        // Une fusée-sonde à poudre : allumage, puis lâcher des attaches, sans bras à retirer.
        RocketBreakup.forced = RocketBreakup.Damage.PAD_ONLY;
        NonNullList<ItemStack> solid = NonNullList.withSize(RocketDesign.SLOT_COUNT, ItemStack.EMPTY);
        solid.set(RocketDesign.SLOT_PAYLOAD, new ItemStack(ModItems.SOUNDING_PAYLOAD.get()));
        solid.set(RocketDesign.SLOT_NOSE, new ItemStack(ModItems.NOSE_CONE.get()));
        solid.set(RocketDesign.engineSlot(0), new ItemStack(ModItems.SOLID_MOTOR.get()));
        ItemStack sounding = RocketItem.create(solid);
        helper.assertTrue(table.testPadProblem(sounding) == null && table.setRocket(sounding), "la fusée-sonde à poudre aurait dû être posée");
        table.setReliable(true);
        helper.assertTrue(table.stationGo(3), "sur le pas de tir d'essai, le poste « pas de tir » n'a pas de bras à attendre");
        BlockPos console = helper.absolutePos(tablePos.offset(0, 0, 10));
        helper.assertTrue(com.orbitalis.phase1.spaceport.LaunchConsoleBlock.status(helper.getLevel(), console, false).testPad(),
                "l'écran du pupitre devrait savoir qu'il commande un pas de tir d'essai");
        String agency = "test:test_pad";
        helper.assertTrue(table.command(LaunchStep.IGNITE, agency) == null && table.getPhase() == LaunchTableBlockEntity.Phase.COUNTDOWN,
                "les moteurs auraient dû s'allumer");
        tickTable(helper, table, LaunchTableBlockEntity.IGNITION_TICKS + 1);
        table.command(LaunchStep.RELEASE, agency);
        helper.assertTrue(table.getPhase() == LaunchTableBlockEntity.Phase.FLIGHT && table.getLastResult() != LaunchTableBlockEntity.Result.FAILURE,
                "la fusée-sonde aurait dû décoller du pas de tir d'essai");
        RocketBreakup.forced = null;
        helper.succeed();
    }

    private static void launchSteps(GameTestHelper helper) {
        BlockPos tablePos = new BlockPos(3, 2, 3);
        buildLaunchPad(helper, tablePos, 1);
        LaunchTableBlockEntity table = helper.getBlockEntity(tablePos, LaunchTableBlockEntity.class);
        table.commission(true);
        String agency = "test:launch_steps";
        // Les accidents de cet essai n'abîment que son propre pas de tir : ni débris, ni cratère chez les voisins.
        RocketBreakup.forced = RocketBreakup.Damage.PAD_ONLY;
        NonNullList<ItemStack> solid = NonNullList.withSize(RocketDesign.SLOT_COUNT, ItemStack.EMPTY);
        solid.set(RocketDesign.SLOT_PAYLOAD, new ItemStack(ModItems.SOUNDING_PAYLOAD.get()));
        solid.set(RocketDesign.SLOT_NOSE, new ItemStack(ModItems.NOSE_CONE.get()));
        solid.set(RocketDesign.engineSlot(0), new ItemStack(ModItems.SOLID_MOTOR.get()));
        int ramp = LaunchTableBlockEntity.IGNITION_TICKS;
        // L'atlas reconnaît les dalles du pas de tir : son intérieur, et son pourtour bordé de rouge.
        com.orbitalis.core.atlas.AtlasStore atlas = com.orbitalis.core.atlas.AtlasStore.of(helper.getLevel());
        BlockPos padCentre = table.getBlockPos();
        int padInner = 0;
        int padEdge = 0;
        for (int dz = -6; dz <= 6; dz++) {
            for (int dx = -6; dx <= 6; dx++) {
                BlockPos column = padCentre.offset(dx, 0, dz);
                atlas.record(helper.getLevel(), helper.getLevel().getChunkAt(column));
                int rgb = atlas.chunk(column.getX() >> 4, column.getZ() >> 4)[(column.getZ() & 15) * 16 + (column.getX() & 15)] & 0xFFFFFF;
                padInner += rgb == com.orbitalis.core.atlas.AtlasTiles.PAD_INNER ? 1 : 0;
                padEdge += rgb == com.orbitalis.core.atlas.AtlasTiles.PAD_BORDER ? 1 : 0;
            }
        }
        helper.assertTrue(padInner + padEdge > 0, "l'atlas devrait reconnaître les dalles du pas de tir : " + padInner + " dalle(s), " + padEdge + " de pourtour");

        // 1. Attaches lâchées avant la pleine poussée : la fusée retombe, elle est perdue.
        helper.assertTrue(table.setRocket(RocketItem.create(solid)), "la fusée-sonde n'a pas été posée");
        helper.assertTrue(table.command(LaunchStep.RELEASE, agency) != null, "on ne lâche pas les attaches moteurs éteints");
        // Chaque technique a sa séquence : la poudre ne demande rien, les turbopompes une mise en froid, les lanceurs
        // avancés en plus le passage sur alimentation interne.
        List<ItemStack> crafts = CreativeRockets.all();
        helper.assertTrue(LaunchTableBlockEntity.techOf(crafts.get(0)) == LaunchTableBlockEntity.Tech.SOLID
                && LaunchTableBlockEntity.techOf(crafts.get(1)) == LaunchTableBlockEntity.Tech.PRESSURE_FED
                && LaunchTableBlockEntity.techOf(crafts.get(2)) == LaunchTableBlockEntity.Tech.TURBOPUMP
                && LaunchTableBlockEntity.techOf(crafts.get(crafts.size() - 1)) == LaunchTableBlockEntity.Tech.ADVANCED,
                "familles techniques des fusées de test incorrectes");
        helper.assertTrue(!table.needsChill() && !table.needsInternal() && table.command(LaunchStep.CHILL, agency) != null,
                "une fusée à poudre ne demande ni mise en froid ni alimentation interne");
        // Au rythme d'une équipe (ce que fait un joueur) : une seule opération à la fois.
        helper.assertTrue(table.request(LaunchStep.POLL, agency) == null && table.busyStep() == LaunchStep.POLL.ordinal()
                && table.pollState() >= 0 && table.pollState() < 100, "le tour de table aurait dû commencer");
        helper.assertTrue(table.request(LaunchStep.RETRACT, agency) != null && !table.isRetracted(),
                "rien d'autre ne devrait se commander pendant une opération");
        helper.assertTrue(table.command(LaunchStep.RETRACT, agency) == null && table.isRetracted(), "le bras aurait dû se retirer");
        helper.assertTrue(table.command(LaunchStep.IGNITE, agency) == null && table.getPhase() == LaunchTableBlockEntity.Phase.COUNTDOWN,
                "les moteurs auraient dû s'allumer");
        tickTable(helper, table, ramp / 2);
        helper.assertTrue(table.thrustPercent() > 0 && table.thrustPercent() < 100, "la poussée devrait être en train de monter");
        table.command(LaunchStep.RELEASE, agency);
        helper.assertTrue(table.getPhase() == LaunchTableBlockEntity.Phase.IDLE && table.getLastResult() == LaunchTableBlockEntity.Result.FAILURE
                && !table.hasRocket(), "lâchée avant la pleine poussée, la fusée aurait dû retomber");
        helper.assertTrue(!table.isRetracted(), "après un accident, la séquence repart de zéro");
        // La fusée s'est disloquée sur sa table : les dalles voisines sont détruites, la table tient, le plan demande réparation.
        tickTable(helper, table, 2);
        helper.assertTrue(helper.getBlockState(tablePos.offset(0, 0, 1)).isAir() && helper.getBlockState(tablePos).is(ModBlocks.LAUNCH_TABLE.get())
                && helper.getBlockState(tablePos.below()).is(ModBlocks.FLAME_DEFLECTOR.get()),
                "l'accident aurait dû détruire les dalles voisines de la table, et épargner la table et le déflecteur");
        helper.assertTrue(!table.isOperational() && table.getComplex().missing() > 0, "le pas de tir abîmé ne doit plus être en service");
        // Une petite fusée ne rase pas la tour ; un lanceur lourd, plein, souffle bien plus loin.
        helper.assertTrue(helper.getBlockState(tablePos.offset(-4, 5, 0)).is(ModBlocks.TOWER_FRAME.get()), "la tour aurait dû tenir");
        helper.assertTrue(RocketBreakup.blastRadius(2_000, 1.0, true) < RocketBreakup.blastRadius(500_000, 1.0, false)
                && RocketBreakup.blastRadius(500_000, 0.0, false) < RocketBreakup.blastRadius(500_000, 1.0, false),
                "les dégâts doivent croître avec la masse de la fusée et avec les ergols qu'elle porte");
        repairPad(helper, tablePos, table);

        // 2. Bras oublié : la fusée l'arrache en partant.
        BlockPos arm = tablePos.offset(table.getPlan().armOffset());
        helper.assertTrue(table.setRocket(RocketItem.create(solid)), "la fusée-sonde n'a pas été reposée");
        table.command(LaunchStep.IGNITE, agency);
        tickTable(helper, table, ramp);
        helper.assertTrue(table.thrustPercent() == 100 && table.holdSeconds() > 0, "à pleine poussée, les attaches devraient retenir la fusée");
        table.command(LaunchStep.RELEASE, agency);
        helper.assertTrue(table.getLastResult() == LaunchTableBlockEntity.Result.FAILURE && helper.getBlockState(arm).isAir(),
                "le bras oublié aurait dû être arraché, et la fusée perdue");
        tickTable(helper, table, 2);
        repairPad(helper, tablePos, table);

        // 3. Attaches jamais lâchées : les moteurs sont coupés, la fusée reste là.
        helper.assertTrue(table.setRocket(RocketItem.create(solid)), "la fusée-sonde n'a pas été reposée");
        table.command(LaunchStep.RETRACT, agency);
        table.command(LaunchStep.IGNITE, agency);
        tickTable(helper, table, ramp + LaunchTableBlockEntity.HOLD_TICKS + 1);
        helper.assertTrue(table.getPhase() == LaunchTableBlockEntity.Phase.IDLE && table.getLastResult() == LaunchTableBlockEntity.Result.SCRUBBED
                && table.hasRocket(), "attaches non lâchées, les moteurs auraient dû être coupés et la fusée rester sur sa table");

        // 4. Séquence bien menée : la fusée décolle.
        table.command(LaunchStep.RETRACT, agency);
        table.command(LaunchStep.IGNITE, agency);
        tickTable(helper, table, ramp);
        helper.assertTrue(table.command(LaunchStep.RELEASE, agency) == null && table.getPhase() == LaunchTableBlockEntity.Phase.FLIGHT,
                "la séquence bien menée aurait dû faire décoller la fusée");
        // En vol, chaque étape demandée est commandée dès que sa fenêtre s'ouvre.
        flyAttentively(helper, table, agency);
        helper.assertTrue(table.getLastResult() == LaunchTableBlockEntity.Result.SUCCESS, "le vol aurait dû réussir : " + table.getLastResult());

        // 5. Fusée à ergols liquides : sans pression, rien ne s'allume ; avec un demi-plein, les moteurs s'éteignent en vol.
        NonNullList<ItemStack> liquid = NonNullList.withSize(RocketDesign.SLOT_COUNT, ItemStack.EMPTY);
        liquid.set(RocketDesign.SLOT_NOSE, new ItemStack(ModItems.NOSE_CONE.get()));
        liquid.set(RocketDesign.engineSlot(0), new ItemStack(ModItems.PRESSURE_FED_ENGINE.get(), 2));
        liquid.set(RocketDesign.tankSlot(0), new ItemStack(ModItems.STEEL_TANK.get()));
        helper.assertTrue(table.setRocket(RocketItem.create(liquid)), "la fusée à ergols liquides n'a pas été posée");
        RocketFuel needed = table.requiredFuel();
        table.command(LaunchStep.VALVES, agency);
        table.fill(ModFluids.RP1.get(), needed.rp1(), false);
        table.fill(ModFluids.LIQUID_OXYGEN.get(), needed.oxygen() / 4, false);
        helper.assertTrue(table.command(LaunchStep.RETRACT, agency) == null && !table.areValvesOpen()
                && table.fill(ModFluids.LIQUID_OXYGEN.get(), 10, false) == 0, "bras retiré, plus rien ne doit entrer dans la fusée");
        helper.assertTrue(table.command(LaunchStep.IGNITE, agency) != null && table.getPhase() == LaunchTableBlockEntity.Phase.IDLE,
                "sans pression, les moteurs ne doivent pas s'allumer");
        helper.assertTrue(table.command(LaunchStep.PRESSURIZE, agency) == null && table.isPressurized(), "les réservoirs auraient dû être mis sous pression");
        table.command(LaunchStep.IGNITE, agency);
        tickTable(helper, table, ramp);
        table.command(LaunchStep.RELEASE, agency);
        helper.assertTrue(table.getPhase() == LaunchTableBlockEntity.Phase.FLIGHT, "la fusée à demi pleine décolle quand même");
        helper.assertTrue((table.flightNeeded() & LaunchTableBlockEntity.FLIGHT_THROTTLE) != 0
                && table.command(LaunchStep.THROTTLE, agency) != null, "avant sa fenêtre, la réduction des gaz doit être refusée, sans dommage");
        flyAttentively(helper, table, agency);
        helper.assertTrue(table.getPhase() == LaunchTableBlockEntity.Phase.IDLE && table.getLastResult() == LaunchTableBlockEntity.Result.FAILURE,
                "le plein incomplet aurait dû éteindre les moteurs en vol : " + table.getLastResult());
        // La fusée a cédé très haut, et son épave retombe plus loin, dans le sens du tir : le pas de tir, lui, n'a rien.
        tickTable(helper, table, 220);
        helper.assertTrue(table.getComplex().missing() == 0, "un accident à haute altitude ne doit pas abîmer le pas de tir : il manque "
                + table.getComplex().missing() + " bloc(s)");

        // 6. Gaz jamais réduits : la structure cède à la pression maximale, bien avant la fin du vol.
        helper.assertTrue(table.setRocket(RocketItem.create(liquid)), "la fusée à ergols liquides n'a pas été reposée");
        table.command(LaunchStep.VALVES, agency);
        table.fill(ModFluids.RP1.get(), needed.rp1(), false);
        table.fill(ModFluids.LIQUID_OXYGEN.get(), needed.oxygen(), false);
        table.command(LaunchStep.RETRACT, agency);
        table.command(LaunchStep.PRESSURIZE, agency);
        table.command(LaunchStep.IGNITE, agency);
        tickTable(helper, table, ramp);
        table.command(LaunchStep.RELEASE, agency);
        tickTable(helper, table, 340);
        helper.assertTrue(table.getPhase() == LaunchTableBlockEntity.Phase.IDLE && table.getLastResult() == LaunchTableBlockEntity.Result.FAILURE,
                "gaz non réduits, la fusée aurait dû se disloquer à la pression maximale");
        tickTable(helper, table, 220);

        // 6 ter. La manette des gaz : descendue à la main pendant la pression maximale, elle fait l'étape, et la fusée
        // passe ; descendue plus bas que prévu, elle coûte de la vitesse.
        helper.assertTrue(table.setRocket(RocketItem.create(liquid)), "la fusée à ergols liquides n'a pas été reposée");
        table.command(LaunchStep.VALVES, agency);
        table.fill(ModFluids.RP1.get(), needed.rp1(), false);
        table.fill(ModFluids.LIQUID_OXYGEN.get(), needed.oxygen(), false);
        table.command(LaunchStep.RETRACT, agency);
        table.command(LaunchStep.PRESSURIZE, agency);
        table.command(LaunchStep.IGNITE, agency);
        tickTable(helper, table, ramp);
        table.command(LaunchStep.RELEASE, agency);
        helper.assertTrue(table.throttleable() && table.throttle() == 1.0F, "une fusée à ergols liquides part à pleins gaz, manette réglable");
        for (int tick = 0; tick < 300 && table.getPhase() == LaunchTableBlockEntity.Phase.FLIGHT; tick++) {
            table.throttle(table.inMaxQ() ? -1 : 0);
            tickTable(helper, table, 1);
        }
        helper.assertTrue(table.getPhase() == LaunchTableBlockEntity.Phase.FLIGHT && (table.flightBits() >> 4 & LaunchTableBlockEntity.FLIGHT_THROTTLE) != 0,
                "gaz réduits à la manette pendant la pression maximale, la fusée aurait dû passer : " + table.getLastResult());
        helper.assertTrue(table.throttle() <= LaunchTableBlockEntity.MAXQ_THROTTLE && table.steerLoss() > 0.0,
                "la manette tenue au plus bas aurait dû coûter de la vitesse : " + table.steerLoss());
        tickTable(helper, table, table.flightTicks());
        tickTable(helper, table, 220);

        // 6 bis. Étage vide jamais séparé : rien ne le détache à la place de l'équipe, la fusée est perdue.
        helper.assertTrue(table.launchForTest(helper.getLevel(), CreativeRockets.satelliteLauncher(), agency) && table.stageCount() > 0,
                "le lanceur à étages aurait dû décoller");
        for (int tick = 0; tick <= com.orbitalis.phase1.spaceport.FlightProfile.MAX_FLIGHT_TICKS && table.getPhase() == LaunchTableBlockEntity.Phase.FLIGHT; tick++) {
            // Les gaz sont réduits à temps ; la séparation, elle, n'est jamais commandée.
            table.command(LaunchStep.THROTTLE, agency);
            tickTable(helper, table, 1);
        }
        helper.assertTrue(table.getPhase() == LaunchTableBlockEntity.Phase.IDLE && table.getLastResult() == LaunchTableBlockEntity.Result.FAILURE
                && table.stagesPassed() == 0, "étage jamais séparé, la fusée aurait dû être perdue, sans séparation d'elle-même : "
                + table.getLastResult() + ", " + table.stagesPassed() + " séparation(s)");
        tickTable(helper, table, 220);
        // Moteurs jamais coupés en fin de vol orbital : la mission échoue, et ne rapporte rien.
        helper.assertTrue(table.launchForTest(helper.getLevel(), CreativeRockets.satelliteLauncher(), agency), "le lanceur aurait dû redécoller");
        for (int tick = 0; tick <= com.orbitalis.phase1.spaceport.FlightProfile.MAX_FLIGHT_TICKS + 2 && table.getPhase() == LaunchTableBlockEntity.Phase.FLIGHT; tick++) {
            // Tout est fait à temps, sauf la coupure des moteurs.
            table.command(LaunchStep.THROTTLE, agency);
            table.command(LaunchStep.STAGE, agency);
            table.command(LaunchStep.FAIRING, agency);
            tickTable(helper, table, 1);
        }
        helper.assertTrue(table.getPhase() == LaunchTableBlockEntity.Phase.IDLE && table.getLastResult() == LaunchTableBlockEntity.Result.FAILURE
                && table.getLastPoints() == 0, "moteurs jamais coupés, la mission aurait dû échouer sans rien rapporter : " + table.getLastResult());
        // L'épave du lanceur perdu a pu abîmer le pas de tir en retombant.
        repairPad(helper, tablePos, table);

        // 7. Niveaux d'aide : verrouillés sans la recherche ; en automatique, l'ordinateur de bord mène le vol seul.
        AgencyResearch assistResearch = ResearchManager.forAgency(helper.getLevel().getServer(), agency);
        assistResearch.reset();
        helper.assertTrue(table.command(LaunchStep.ASSIST, agency) != null && table.getAssist() == 0, "sans la recherche, l'aide doit rester verrouillée");
        assistResearch.addPoints(1000);
        for (String node : new String[] {"basic_electronics", "gyroscopes", "flight_computers"}) {
            assistResearch.unlock(node);
            helper.assertTrue(assistResearch.isUnlocked(node), "la technologie " + node + " aurait dû se débloquer");
        }
        table.command(LaunchStep.ASSIST, agency);
        table.command(LaunchStep.ASSIST, agency);
        helper.assertTrue(table.getAssist() == 2, "avec les ordinateurs de vol, l'aide automatique devrait être disponible");
        helper.assertTrue(table.setRocket(RocketItem.create(liquid)), "la fusée à ergols liquides n'a pas été reposée");
        table.command(LaunchStep.VALVES, agency);
        table.fill(ModFluids.RP1.get(), needed.rp1(), false);
        table.fill(ModFluids.LIQUID_OXYGEN.get(), needed.oxygen(), false);
        tickTable(helper, table, 2);
        helper.assertTrue(!table.areValvesOpen(), "en automatique, les vannes se referment une fois le plein fait");
        table.command(LaunchStep.RETRACT, agency);
        table.command(LaunchStep.PRESSURIZE, agency);
        table.command(LaunchStep.IGNITE, agency);
        tickTable(helper, table, ramp);
        table.command(LaunchStep.RELEASE, agency);
        tickTable(helper, table, table.flightTicks() + 1);
        helper.assertTrue(table.getLastResult() == LaunchTableBlockEntity.Result.SUCCESS,
                "en automatique, le vol aurait dû réussir sans rien toucher : " + table.getLastResult());
        table.command(LaunchStep.ASSIST, agency);
        helper.assertTrue(table.getAssist() == 0, "le réglage d'aide revient au manuel après l'automatique");
        assistResearch.reset();

        // 8. Pilotage : un pilote qui tient la consigne arrive à destination sans rien perdre ; un pilote qui couche
        // la fusée d'emblée la met de travers dans l'air dense, et sa structure cède.
        LaunchStep[] flightSteps = {LaunchStep.THROTTLE, LaunchStep.STAGE, LaunchStep.FAIRING, LaunchStep.CUTOFF};
        for (boolean careful : new boolean[] {true, false}) {
            helper.assertTrue(table.setRocket(RocketItem.create(solid)), "la fusée-sonde n'a pas été reposée");
            table.command(LaunchStep.RETRACT, agency);
            table.command(LaunchStep.IGNITE, agency);
            tickTable(helper, table, ramp);
            table.command(LaunchStep.RELEASE, agency);
            for (int tick = 0; tick <= com.orbitalis.phase1.spaceport.FlightProfile.MAX_FLIGHT_TICKS + 1 && table.getPhase() == LaunchTableBlockEntity.Phase.FLIGHT; tick++) {
                for (int k = 0; k < flightSteps.length; k++) {
                    if ((table.flightOpen() & 1 << k) != 0) {
                        table.command(flightSteps[k], agency);
                    }
                }
                // Le pilote soigneux pique dès que la consigne passe sous son nez, et lâche les commandes sinon : la
                // fusée a de l'inertie, il n'a pas à corriger sans cesse.
                double gap = table.getPitch() - table.targetPitch();
                table.steer(careful ? (gap > 1.5 ? 1 : gap < -1.5 ? -1 : 0) : 1, 0, 0);
                table.serverTick(helper.getLevel());
            }
            if (careful) {
                helper.assertTrue(table.getLastResult() == LaunchTableBlockEntity.Result.SUCCESS && table.steerLoss() < 50.0,
                        "un pilote qui tient la consigne devrait réussir sans perte : " + table.getLastResult() + ", " + (int) table.steerLoss() + " m/s perdus");
            } else {
                helper.assertTrue(table.getLastResult() == LaunchTableBlockEntity.Result.FAILURE,
                        "une fusée couchée dans l'air dense aurait dû se disloquer : " + table.getLastResult());
                tickTable(helper, table, 220);
            }
        }

        // 9. Le déluge : le plus petit pas de tir n'en a pas ; un 5×5 en demande, et de l'eau pour le faire.
        helper.assertTrue(!table.hasDeluge() && new PadPlan(1, false, Direction.EAST).waterPort() == null
                && new PadPlan(2, false, Direction.EAST).waterPort() != null, "seuls les pas de tir à rampes ont un bloc d'eau");
        // 10. Toutes les fusées du catalogue, aux commandes. Un pilote ordinaire (il pique quand la consigne passe
        // sous son nez, avec cinq degrés de retard, donne de petits coups de lacet, et fait chaque étape à son
        // heure) arrive à destination, tous ses étages largués, sans que la structure souffre. Un pilote qui tient
        // la commande enfoncée met la fusée en vrille, et la perd. Les fusées partent d'une table nue, à l'écart :
        // la plus grosse ne tiendrait pas sur ce petit pas de tir.
        BlockPos barePos = tablePos.offset(9, 0, 9);
        helper.setBlock(barePos, ModBlocks.LAUNCH_TABLE.get());
        LaunchTableBlockEntity bare = helper.getBlockEntity(barePos, LaunchTableBlockEntity.class);
        RocketBreakup.forced = RocketBreakup.Damage.NONE;
        for (ItemStack craft : CreativeRockets.all()) {
            String name = craft.getHoverName().getString();
            for (boolean careful : new boolean[] {true, false}) {
                helper.assertTrue(bare.launchForTest(helper.getLevel(), craft, agency), name + " : la fusée n'a pas décollé");
                int stages = bare.stageCount();
                int dropped = 0;
                int firstDrop = -1;
                int coasted = 0;
                float worst = 0.0F;
                double lost = 0.0;
                double margin = 0.0;
                for (int tick = 0; tick <= com.orbitalis.phase1.spaceport.FlightProfile.MAX_FLIGHT_TICKS + 1 && bare.getPhase() == LaunchTableBlockEntity.Phase.FLIGHT; tick++) {
                    for (int k = 0; k < flightSteps.length; k++) {
                        if ((bare.flightOpen() & 1 << k) != 0) {
                            bare.command(flightSteps[k], agency);
                        }
                    }
                    double gap = bare.getPitch() - bare.targetPitch();
                    boolean leaning = bare.targetPitch() < 85.0;
                    bare.steer(careful ? (gap > 5.0 ? 1 : 0) : (tick > 100 ? 1 : 0), careful && leaning && tick % 40 < 5 ? 1 : 0, 0);
                    if (bare.stagesPassed() > 0 && firstDrop < 0) {
                        firstDrop = tick;
                    }
                    // Moteurs coupés, l'étage vide est encore là un instant, puis il part ; rien ne pousse la fusée.
                    boolean out = bare.enginesOut();
                    coasted += out ? 1 : 0;
                    dropped = Math.max(dropped, bare.stagesPassed());
                    worst = Math.max(worst, bare.stress());
                    lost = bare.steerLoss();
                    margin = bare.deltaVLeft();
                    bare.serverTick(helper.getLevel());
                }
                if (careful) {
                    helper.assertTrue(bare.getLastResult() == LaunchTableBlockEntity.Result.SUCCESS, name + " : un pilote ordinaire aurait dû réussir, et non "
                            + bare.getLastResult() + " (structure " + worst + ", " + (int) lost + " m/s perdus)");
                    helper.assertTrue(dropped == stages, name + " : " + dropped + " étage(s) largué(s) sur " + stages);
                    // Chaque séparation coupe les moteurs deux à trois secondes ; la première vient bien après le décollage.
                    helper.assertTrue(coasted >= stages * 30 && coasted <= stages * 70, name + " : " + coasted + " ticks de vol libre pour " + stages + " séparation(s)");
                    helper.assertTrue(stages == 0 || firstDrop >= 320, name + " : première séparation trop tôt, au tick " + firstDrop);
                    helper.assertTrue(worst < 0.5F, name + " : la structure ne devrait presque pas souffrir d'un pilotage ordinaire (" + worst + ")");
                    helper.assertTrue(margin >= 0.0, name + " : un pilotage ordinaire ne doit pas faire manquer la destination (" + (int) lost + " m/s perdus, marge "
                            + (int) margin + ")");
                    com.orbitalis.Orbitalis.LOGGER.info("Vol piloté de {} : {} étage(s) largué(s), structure au pire {} %, {} m/s perdus, marge {} m/s", name, dropped,
                            Math.round(worst * 100), (int) lost, (int) margin);
                } else {
                    helper.assertTrue(bare.getLastResult() == LaunchTableBlockEntity.Result.FAILURE, name + " : une fusée en vrille aurait dû se disloquer, et non "
                            + bare.getLastResult());
                    tickTable(helper, bare, 260);
                }
                // La capsule d'une fusée habitée reste en l'air : on l'enlève avant le vol suivant.
                for (CapsuleEntity left : helper.getLevel().getEntitiesOfClass(CapsuleEntity.class,
                        new net.minecraft.world.phys.AABB(helper.absolutePos(barePos)).inflate(64.0, 4096.0, 64.0))) {
                    left.discard();
                }
            }
        }
        helper.setBlock(barePos, net.minecraft.world.level.block.Blocks.AIR);
        RocketBreakup.forced = null;
        helper.succeed();
    }

    /**
     * Ascenseur de la tour : son socle, posé au centre de la tour, fait venir une cabine centrée sur lui et
     * tournée comme la tour ; ses arrêts sont le sol et le portique d'embarquement, et elle rejoint
     * celui qu'on lui demande en moins de quatre secondes. Casser le socle retire la cabine.
     */
    private static void towerElevator(GameTestHelper helper) {
        BlockPos tablePos = new BlockPos(3, 2, 3);
        buildLaunchPad(helper, tablePos, 1, true);
        BlockPos riser = tablePos.offset(-2, -1, 0);
        // Le portique fait partie du plan : c'est le seul arrêt de l'ascenseur en hauteur.
        int floor = new PadPlan(1, true, Direction.EAST).gate();
        LaunchTableBlockEntity table = helper.getBlockEntity(tablePos, LaunchTableBlockEntity.class);
        table.commission(true);
        helper.assertTrue(table.getComplex().isComplete(), "la tour à ascenseur devrait être complète : il manque "
                + table.getComplex().missing() + " bloc(s)");

        // Le socle fait partie du plan : il a fait venir une cabine, au pied de la gaine.
        BlockPos home = helper.absolutePos(riser.offset(-2, 0, 0));
        List<ElevatorCabinEntity> cabins = helper.getLevel().getEntitiesOfClass(ElevatorCabinEntity.class,
                new AABB(home).inflate(3.0), cabin -> home.equals(cabin.getHome()));
        helper.assertTrue(cabins.size() == 1, "le socle devrait faire venir une cabine, et une seule");
        ElevatorCabinEntity cabin = cabins.get(0);
        // La cabine est avancée à la main, tick par tick : le test ne dépend pas du chargement des tronçons du monde.
        cabin.tick();
        helper.assertTrue(cabin.getFacing() == Direction.EAST && cabin.getY() == home.getY(),
                "la cabine devrait être au pied de la gaine et tournée vers la fusée");
        helper.assertTrue(helper.getBlockState(riser.above(floor)).getValue(BoardingGateBlock.CLOSED),
                "la porte palière devrait être fermée tant que la cabine est au sol");

        int gate = home.getY() + floor;
        helper.assertTrue(!cabin.sendTo(gate + 1) && cabin.sendTo(gate), "le portique devrait être le seul arrêt en hauteur");
        int ticks = 0;
        while (cabin.isMoving() && ticks < 200) {
            cabin.tick();
            ticks++;
        }
        helper.assertTrue(cabin.getY() == gate && ticks <= 80, "la cabine devrait atteindre le portique en moins de quatre secondes : " + ticks);
        helper.assertTrue(!helper.getBlockState(riser.above(floor)).getValue(BoardingGateBlock.CLOSED)
                && !helper.getBlockState(riser.above(floor + 1)).getValue(BoardingGateBlock.CLOSED), "la porte palière devrait s'ouvrir à l'arrivée de la cabine");
        helper.succeed();
    }

    /**
     * Grand pas de tir 5×5 : assemblé selon le plan complet, il accueille un lanceur lourd, dressé sur trois blocs
     * de large ; une fusée plus haute que sa tour est refusée.
     */
    private static void heavyLaunchPad(GameTestHelper helper) {
        BlockPos tablePos = new BlockPos(4, 2, 4);
        buildLaunchPad(helper, tablePos, 2);
        LaunchComplex complex = helper.getBlockEntity(tablePos, LaunchTableBlockEntity.class).getComplex();
        helper.assertTrue(complex.radius() == 2 && complex.isComplete(), "le pas de tir 5×5 complet n'est pas reconnu");
        helper.assertTrue(complex.maxRocketMass() == 200_000 && complex.maxRocketHeight() == 24, "capacités du pas de tir 5×5 incorrectes");

        // Lanceur de 125 t : il se dresse sur 22 blocs, entouré de ses flancs sur trois blocs de large.
        LaunchTableBlockEntity table = helper.getBlockEntity(tablePos, LaunchTableBlockEntity.class);
        table.commission(true);
        NonNullList<ItemStack> parts = NonNullList.withSize(RocketDesign.SLOT_COUNT, ItemStack.EMPTY);
        parts.set(RocketDesign.SLOT_PAYLOAD, new ItemStack(ModItems.SOUNDING_PAYLOAD.get()));
        parts.set(RocketDesign.SLOT_NOSE, new ItemStack(ModItems.NOSE_CONE.get()));
        parts.set(RocketDesign.engineSlot(0), new ItemStack(ModItems.KEROLOX_ENGINE.get(), 2));
        parts.set(RocketDesign.tankSlot(0), new ItemStack(ModItems.ALUMINUM_TANK.get(), 24));
        parts.set(RocketDesign.engineSlot(1), new ItemStack(ModItems.KEROLOX_ENGINE.get()));
        parts.set(RocketDesign.tankSlot(1), new ItemStack(ModItems.ALUMINUM_TANK.get(), 4));
        parts.set(RocketDesign.interstageSlot(1), new ItemStack(ModItems.INTERSTAGE.get()));
        helper.assertTrue(table.isMassAllowedFor(RocketItem.create(parts)) && table.isHeightAllowedFor(RocketItem.create(parts)),
                "le lanceur devrait être accepté par ce pas de tir");
        helper.assertTrue(table.setRocket(RocketItem.create(parts)), "le lanceur n'a pas été posé");
        helper.assertTrue(helper.getBlockState(tablePos.above(22)).getValue(RocketBlock.PART) == RocketBlock.Part.NOSE,
                "la pointe du lanceur n'est pas à 22 blocs");
        helper.assertTrue(helper.getBlockState(tablePos.offset(1, 10, 1)).is(ModBlocks.ROCKET_HULL.get()),
                "le flanc du lanceur n'occupe pas les cases voisines");
        helper.assertTrue(!table.removeRocket().isEmpty(), "le lanceur devrait pouvoir être retiré");
        helper.assertTrue(helper.getBlockState(tablePos.offset(1, 10, 1)).isAir() && helper.getBlockState(tablePos.above(22)).isAir(),
                "il reste des blocs de fusée après le retrait");

        // Huit réservoirs de plus : la fusée dépasse le sommet de la tour, elle est refusée.
        parts.set(RocketDesign.tankSlot(1), new ItemStack(ModItems.ALUMINUM_TANK.get(), 12));
        helper.assertTrue(!table.isHeightAllowedFor(RocketItem.create(parts)), "une fusée plus haute que la tour doit être refusée");
        helper.succeed();
    }

    /**
     * Silhouette des fusées : la taille et le diamètre suivent le plan, et le pas de tir prend l'aspect
     * de ses rôles (carneau, intérieur, bordure) selon sa taille.
     */
    private static void rocketShapes(GameTestHelper helper) {
        ItemStack nose = new ItemStack(ModItems.NOSE_CONE.get());
        ItemStack payload = new ItemStack(ModItems.SOUNDING_PAYLOAD.get());
        RocketDesign.Stage empty = new RocketDesign.Stage(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);

        RocketShape sounding = RocketShape.of(new RocketDesign(List.of(
                new RocketDesign.Stage(new ItemStack(ModItems.SOLID_MOTOR.get()), ItemStack.EMPTY, ItemStack.EMPTY), empty, empty),
                payload, nose));
        helper.assertTrue(sounding.size() == RocketBlock.Size.SLIM && sounding.height() == 5, "silhouette de la fusée-sonde incorrecte");

        // Lanceur de 125 t : trois blocs de large ; 24 réservoirs font 12 tronçons, 4 en font deux.
        RocketShape launcher = RocketShape.of(new RocketDesign(List.of(
                new RocketDesign.Stage(new ItemStack(ModItems.KEROLOX_ENGINE.get(), 2), new ItemStack(ModItems.ALUMINUM_TANK.get(), 24),
                        ItemStack.EMPTY),
                new RocketDesign.Stage(new ItemStack(ModItems.KEROLOX_ENGINE.get()), new ItemStack(ModItems.ALUMINUM_TANK.get(), 4),
                        new ItemStack(ModItems.INTERSTAGE.get())),
                empty), payload, nose));
        helper.assertTrue(launcher.isWide() && launcher.height() == 22, "silhouette du lanceur incorrecte : " + launcher.height());
        helper.assertTrue(launcher.parts().get(0) == RocketBlock.Part.ENGINE && launcher.parts().get(13) == RocketBlock.Part.INTERSTAGE
                && launcher.parts().get(20) == RocketBlock.Part.NOSE_LOWER && launcher.parts().get(21) == RocketBlock.Part.NOSE,
                "ordre des tronçons du lanceur incorrect");

        helper.assertTrue(LaunchPadBlock.lookFor(1, 1) == LaunchPadBlock.Look.BORDER, "un pas de tir 3×3 n'a que sa bordure");
        helper.assertTrue(LaunchPadBlock.lookFor(1, 3) == LaunchPadBlock.Look.TRENCH && LaunchPadBlock.lookFor(2, 3) == LaunchPadBlock.Look.INNER
                && LaunchPadBlock.lookFor(3, 3) == LaunchPadBlock.Look.BORDER && LaunchPadBlock.lookFor(4, 3) == LaunchPadBlock.Look.PLAIN,
                "aspect des dalles d'un pas de tir 7×7 incorrect");
        helper.assertTrue(new LaunchComplex(5, 0, true, 0, 0).maxRocketMass() == 10_000_000, "masse maximale du pas de tir 11×11 incorrecte");
        helper.succeed();
    }

    /**
     * Ravitaillement depuis la ferme de réservoirs : un réservoir de RP-1 relié au bras ombilical par un tuyau
     * charge dans la fusée exactement ce dont elle a besoin, et garde le reste.
     */
    private static void tankFeedsRocket(GameTestHelper helper) {
        BlockPos tablePos = new BlockPos(3, 2, 3);
        buildLaunchPad(helper, tablePos, 1);
        // Un bloc de ravitaillement réglé sur le kérosène, au pied de la tour ; le réservoir lui est relié par un tuyau.
        BlockPos pipePos = placeFuelPort(helper, tablePos, 1, FuelPortBlock.Kind.RP1);
        helper.setBlock(pipePos, ModBlocks.FLUID_PIPE.get());
        BlockPos tankPos = pipePos.offset(0, 0, 1);
        helper.setBlock(tankPos, ModBlocks.CRYOGENIC_TANK.get());

        LaunchTableBlockEntity table = helper.getBlockEntity(tablePos, LaunchTableBlockEntity.class);
        table.commission(true);
        NonNullList<ItemStack> parts = NonNullList.withSize(RocketDesign.SLOT_COUNT, ItemStack.EMPTY);
        parts.set(RocketDesign.SLOT_NOSE, new ItemStack(ModItems.NOSE_CONE.get()));
        parts.set(RocketDesign.engineSlot(0), new ItemStack(ModItems.PRESSURE_FED_ENGINE.get(), 2));
        parts.set(RocketDesign.tankSlot(0), new ItemStack(ModItems.STEEL_TANK.get()));
        helper.assertTrue(table.setRocket(RocketItem.create(parts)), "la fusée n'a pas été posée");
        table.command(LaunchStep.VALVES, "test:tank");

        int needed = table.requiredFuel().rp1();
        CryogenicTankBlockEntity tank = helper.getBlockEntity(tankPos, CryogenicTankBlockEntity.class);
        tank.getTank().setFluid(0, new FluidStack(ModFluids.RP1.get(), 5000));

        helper.succeedWhen(() -> {
            helper.assertTrue(table.fuelSnapshot()[0] == needed, "le RP-1 n'a pas été chargé dans la fusée");
            int left = tank.getTank().getFluid(0).getAmount();
            helper.assertTrue(left >= 5000 - needed && left < 5000, "le réservoir doit alimenter la fusée et garder le reste : " + left);
        });
    }

    /**
     * Lancement complet : une fusée à ergols liquides se ravitaille par le bras ombilical, puis une fusée-sonde
     * est posée sur la table, le compte à rebours se déroule, la fusée vole et l'agence reçoit ses points.
     */
    private static void launchSequence(GameTestHelper helper) {
        BlockPos tablePos = new BlockPos(3, 2, 3);
        BlockPos armPos = buildLaunchPad(helper, tablePos, 1);

        LaunchTableBlockEntity table = helper.getBlockEntity(tablePos, LaunchTableBlockEntity.class);
        table.commission(true);
        table.setReliable(true);
        helper.assertTrue(table.isOperational(), "le pas de tir devrait être opérationnel");
        helper.assertTrue(helper.getBlockState(tablePos.offset(1, 0, 1)).getValue(LaunchPadBlock.LOOK) == LaunchPadBlock.Look.BORDER,
                "les dalles d'un pas de tir assemblé devraient connaître leur rôle");

        // Fusée à ergols liquides : elle n'est prête qu'une fois ravitaillée par le bras ombilical.
        NonNullList<ItemStack> liquidParts = NonNullList.withSize(RocketDesign.SLOT_COUNT, ItemStack.EMPTY);
        liquidParts.set(RocketDesign.SLOT_NOSE, new ItemStack(ModItems.NOSE_CONE.get()));
        // Deux moteurs : un seul ne soulèverait pas un réservoir plein.
        liquidParts.set(RocketDesign.engineSlot(0), new ItemStack(ModItems.PRESSURE_FED_ENGINE.get(), 2));
        liquidParts.set(RocketDesign.tankSlot(0), new ItemStack(ModItems.STEEL_TANK.get()));
        helper.assertTrue(table.setRocket(RocketItem.create(liquidParts)), "la fusée à ergols liquides n'a pas été posée");
        table.command(LaunchStep.VALVES, "test:launch_sequence");
        helper.assertTrue(helper.getBlockState(tablePos.above()).is(ModBlocks.ROCKET.get()), "la fusée n'est pas dressée sur la table");
        helper.assertTrue(!table.isGoForLaunch(), "une fusée aux réservoirs vides ne doit pas pouvoir partir");

        RocketFuel needed = table.requiredFuel();
        // Un bloc de ravitaillement par ergol : chacun ne laisse passer que le sien.
        BlockPos rp1Port = tablePos.offset(-4, -1, 2);
        BlockPos oxygenPort = tablePos.offset(-5, -1, 2);
        helper.setBlock(rp1Port, ModBlocks.FUEL_PORT.get().defaultBlockState().setValue(FuelPortBlock.KIND, FuelPortBlock.Kind.RP1));
        helper.setBlock(oxygenPort, ModBlocks.FUEL_PORT.get().defaultBlockState().setValue(FuelPortBlock.KIND, FuelPortBlock.Kind.LOX));
        helper.assertTrue(table.isOperational(), "un bloc de ravitaillement peut remplacer un bloc de charpente au pied de la tour");
        var kerosene = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(rp1Port), null);
        var oxidiser = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(oxygenPort), null);
        helper.assertTrue(kerosene != null && oxidiser != null, "les blocs de ravitaillement n'acceptent pas les fluides");
        // Le parc à ergols pousse tant qu'il peut ; les bras, eux, ont leur débit : le plein prend plusieurs secondes.
        int wrong = 0;
        int rp1 = 0;
        int oxygen = 0;
        int hydrogen = 0;
        int passes = 0;
        for (; passes < 4000 && !table.isFuelled(); passes++) {
            try (Transaction transaction = Transaction.openRoot()) {
                wrong += kerosene.insert(FluidResource.of(ModFluids.LIQUID_OXYGEN.get()), 100_000, transaction);
                rp1 += kerosene.insert(FluidResource.of(ModFluids.RP1.get()), 100_000, transaction);
                oxygen += oxidiser.insert(FluidResource.of(ModFluids.LIQUID_OXYGEN.get()), 100_000, transaction);
                hydrogen += oxidiser.insert(FluidResource.of(ModFluids.LIQUID_HYDROGEN.get()), 100_000, transaction);
                transaction.commit();
            }
            table.serverTick(helper.getLevel());
        }
        // L'oxygène s'évapore un peu pendant le plein : il en passe à peine plus que le réservoir n'en contient.
        helper.assertTrue(wrong == 0 && rp1 == needed.rp1() && oxygen >= needed.oxygen() && oxygen <= needed.oxygen() + 8 && hydrogen == 0,
                "chaque bloc doit accepter exactement son ergol : " + wrong + " / " + rp1 + " / " + oxygen + " / " + hydrogen);
        helper.assertTrue(passes >= 200, "le plein devrait prendre plusieurs secondes, il s'est fait en " + passes + " ticks");
        table.command(LaunchStep.VALVES, "test:launch_sequence");
        helper.assertTrue(table.isFuelled() && table.isGoForLaunch(), "la fusée ravitaillée devrait être prête");
        // La tour et le bras, eux, ne reçoivent plus d'ergols.
        helper.assertTrue(helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(armPos), null) == null,
                "le bras ombilical ne devrait plus accepter les ergols");
        helper.assertTrue(!table.removeRocket().isEmpty(), "la fusée devrait pouvoir être retirée");
        helper.assertTrue(helper.getBlockState(tablePos.above()).isAir(), "la fusée retirée ne doit plus être dressée");

        // Fusée-sonde : compte à rebours, vol, puis points de recherche pour l'agence qui a lancé.
        NonNullList<ItemStack> soundingParts = NonNullList.withSize(RocketDesign.SLOT_COUNT, ItemStack.EMPTY);
        soundingParts.set(RocketDesign.SLOT_PAYLOAD, new ItemStack(ModItems.SOUNDING_PAYLOAD.get()));
        soundingParts.set(RocketDesign.SLOT_NOSE, new ItemStack(ModItems.NOSE_CONE.get()));
        soundingParts.set(RocketDesign.engineSlot(0), new ItemStack(ModItems.SOLID_MOTOR.get()));
        helper.assertTrue(table.setRocket(RocketItem.create(soundingParts)), "la fusée-sonde n'a pas été posée");
        // Sa silhouette : baie de propulsion, corps du moteur (deux blocs), compartiment de la charge utile, pointe.
        helper.assertTrue(table.rocketHeight() == 5, "hauteur de la fusée-sonde incorrecte : " + table.rocketHeight());
        helper.assertTrue(helper.getBlockState(tablePos.above(5)).getValue(RocketBlock.PART) == RocketBlock.Part.NOSE,
                "la pointe de la fusée-sonde n'est pas en haut");

        String agency = "test:launch_sequence";
        AgencyResearch research = ResearchManager.forAgency(helper.getLevel().getServer(), agency);
        research.reset();
        // Un écran de contrôle posé à portée doit suivre le lancement : veille, compte à rebours, vol, puis veille.
        BlockPos displayPos = tablePos.offset(0, 0, 2);
        helper.setBlock(displayPos, ModBlocks.MISSION_DISPLAY.get());
        helper.getBlockEntity(displayPos, MissionDisplayBlockEntity.class).setLink(table.getBlockPos());
        helper.assertTrue(table.startCountdown(agency), "le compte à rebours aurait dû démarrer");
        helper.assertTrue(!table.startCountdown(agency), "un second compte à rebours ne doit pas démarrer");

        boolean[] seen = new boolean[2];
        helper.onEachTick(() -> {
            MissionDisplayBlock.Mode mode = helper.getBlockState(displayPos).getValue(MissionDisplayBlock.MODE);
            seen[0] |= mode == MissionDisplayBlock.Mode.COUNTDOWN;
            seen[1] |= mode == MissionDisplayBlock.Mode.FLIGHT;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(table.getLastResult() == LaunchTableBlockEntity.Result.SUCCESS, "le vol n'est pas terminé");
            helper.assertTrue(seen[0] && seen[1], "l'écran de contrôle n'a pas suivi le compte à rebours puis le vol");
            helper.assertTrue(helper.getBlockState(displayPos).getValue(MissionDisplayBlock.MODE) == MissionDisplayBlock.Mode.IDLE,
                    "l'écran de contrôle devrait revenir en veille après le vol");
            helper.assertTrue(!table.hasRocket(), "la fusée devrait avoir quitté la table");
            // 10 (premier vol atmosphérique) + 30 (premier vol dans l'espace) + 5 (charge utile scientifique).
            helper.assertTrue(research.points() == 45 && table.getLastPoints() == 45, "points de recherche incorrects : " + research.points());
            helper.assertTrue(research.flights() == 1, "le vol réussi n'a pas été compté");
        });
    }

    /**
     * Plan du pas de tir : il n'est validé que si tous ses blocs sont posés. Chaque élément retiré, tour à tour,
     * suffit à l'invalider, et la table sait dire ce qui manque.
     */
    private static void launchComplex(GameTestHelper helper) {
        BlockPos tablePos = new BlockPos(3, 2, 3);
        buildLaunchPad(helper, tablePos, 1);
        LaunchTableBlockEntity table = helper.getBlockEntity(tablePos, LaunchTableBlockEntity.class);
        PadPlan plan = table.getPlan();

        LaunchComplex complex = table.getComplex();
        helper.assertTrue(complex.radius() == 1 && complex.isComplete(), "le pas de tir 3×3 complet n'est pas reconnu : il manque "
                + complex.missing() + " bloc(s)");
        helper.assertTrue(complex.towerHeight() == 10 && complex.mastHeight() == 4 && complex.arms() == 1, "éléments du pas de tir mal relevés");
        helper.assertTrue(complex.maxRocketMass() == 20_000 && complex.maxRocketHeight() == 8, "capacités du pas de tir 3×3 incorrectes");
        helper.assertTrue(LaunchTableBlock.missingParts(helper.getLevel(), table).isEmpty(), "rien ne devrait manquer à un pas de tir complet");
        // En service, la table dessine elle-même le bras ombilical : son bloc, accroché en hauteur au-dessus du vide,
        // doit s'effacer, sans quoi il se verrait sous le bras animé.
        table.commission(true);
        table.refresh(helper.getLevel());
        // Pour un joueur, un 3×3 ne sert plus : complet et mis en service, il reste hors service.
        helper.assertTrue(table.isOperational(), "le pas de tir d'essai devrait être en service");
        PadPlan.retiredSizesAllowed = false;
        boolean refused = !table.isOperational() && table.isRetiredSize() && PadPlan.nearestSize(1) == 2 && PadPlan.nearestSize(3) == 4
                && !PadPlan.isOffered(3) && PadPlan.isOffered(2) && PadPlan.isOffered(4) && PadPlan.isOffered(5);
        PadPlan.retiredSizesAllowed = true;
        helper.assertTrue(refused, "un pas de tir 3×3 ou 7×7 ne devrait plus pouvoir servir, et le kit devrait proposer 5×5 ou 9×9 à la place");
        for (BlockPos arm : table.getComplex().armPositions()) {
            helper.assertTrue(helper.getLevel().getBlockState(arm).getValue(com.orbitalis.phase1.spaceport.ManagedStructureBlock.MANAGED),
                    "le bloc du bras ombilical aurait dû s'effacer sur un pas de tir en service");
        }

        // Un bloc de chaque rôle est indispensable : on le retire, on vérifie que le pas de tir n'est plus validé et
        // que la table dit ce qui manque, puis on le remet.
        java.util.Set<PadPlan.Role> seen = java.util.EnumSet.noneOf(PadPlan.Role.class);
        for (PadPlan.Piece piece : plan.pieces()) {
            if (!seen.add(piece.role())) {
                continue;
            }
            BlockPos pos = helper.absolutePos(tablePos).offset(piece.offset());
            helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            helper.assertTrue(!table.getComplex().isComplete(), "le pas de tir ne devrait plus être validé sans : " + piece.block().getName().getString());
            helper.assertTrue(LaunchTableBlock.missingParts(helper.getLevel(), table).size() == 2, "la table devrait dire ce qui manque");
            helper.getLevel().setBlockAndUpdate(pos, piece.display());
        }
        helper.assertTrue(seen.size() == PadPlan.Role.values().length - 1, "le 3×3 devrait avoir tous les rôles sauf le déluge");
        helper.assertTrue(table.getComplex().isComplete(), "le pas de tir devrait être de nouveau complet");

        // Une table sans plan ne forme rien, et le dit.
        BlockPos bare = tablePos.offset(0, 14, 0);
        helper.setBlock(bare, ModBlocks.LAUNCH_TABLE.get());
        LaunchTableBlockEntity planless = helper.getBlockEntity(bare, LaunchTableBlockEntity.class);
        helper.assertTrue(!planless.getComplex().isComplete() && LaunchTableBlock.missingParts(helper.getLevel(), planless).size() == 1,
                "une table sans plan devrait demander un kit");
        helper.setBlock(bare, Blocks.AIR);
        helper.succeed();
    }

    /**
     * Satellites : production de données, mémoire limitée, fin de vie, relais, puis mise en orbite à la fin d'un vol
     * (orbite atteinte ou non), primes et constellation de navigation.
     */
    private static void satellites(GameTestHelper helper) {
        long day = AgencySatellites.DAY;

        // Un satellite d'observation produit 4 points par jour.
        AgencySatellites fleet = new AgencySatellites();
        Satellite observation = fleet.deploy(SatelliteType.OBSERVATION, 0);
        helper.assertTrue(observation.number() == 1, "le premier satellite devrait porter le numéro 1");
        // Avec un générateur à radio-isotopes, un satellite dure deux fois plus longtemps.
        Satellite powered = new AgencySatellites().deploy(SatelliteType.OBSERVATION, 0, true);
        helper.assertTrue(AgencySatellites.endOfLife(powered, true) == 2 * AgencySatellites.endOfLife(observation, true),
                "un satellite à RTG devrait durer deux fois plus longtemps");
        // Les pièces qu'ouvrent les technologies : le booster à poudre porte son propergol, le moteur à vide brûle
        // de l'hydrogène, le réservoir en titane est le plus léger des petits réservoirs.
        var booster = com.orbitalis.phase1.rocket.RocketParts.get(new ItemStack(ModItems.SOLID_BOOSTER.get()));
        var vacuum = com.orbitalis.phase1.rocket.RocketParts.get(new ItemStack(ModItems.VACUUM_ENGINE.get()));
        var titaniumTank = com.orbitalis.phase1.rocket.RocketParts.get(new ItemStack(ModItems.TITANIUM_TANK.get()));
        var lithium = com.orbitalis.phase1.rocket.RocketParts.get(new ItemStack(ModItems.ALUMINUM_LITHIUM_TANK.get()));
        helper.assertTrue(booster != null && booster.engine().propellant().isSolid() && booster.engine().solidLoad() > 0
                && "solid_boosters".equals(booster.research()), "le booster à poudre devrait porter son propergol");
        helper.assertTrue(vacuum != null && vacuum.engine().propellant() == com.orbitalis.phase1.rocket.Propellant.HYDROLOX
                && vacuum.engine().ispVacuum() > 434, "le moteur à vide devrait avoir la meilleure impulsion dans le vide");
        helper.assertTrue(titaniumTank != null && titaniumTank.mass() < lithium.mass(), "le réservoir en titane devrait être le plus léger");
        helper.assertTrue(fleet.pending(day / 2, true) == 2, "une demi-journée d'observation devrait valoir 2 points");
        helper.assertTrue(fleet.collect(day / 2, true) == 2 && fleet.pending(day / 2, true) == 0, "les données reçues ne doivent plus être en attente");
        // Sa mémoire ne garde que trois jours de données.
        helper.assertTrue(fleet.pending(10 * day + day / 2, true) == 12, "la mémoire de bord devrait plafonner à trois jours de données");
        // En fin de vie (60 jours), il ne produit plus ; une fois ses dernières données reçues, il est oublié.
        helper.assertTrue(!AgencySatellites.isActive(observation, 60 * day, true), "le satellite devrait être hors service après 60 jours");
        helper.assertTrue(AgencySatellites.isActive(observation, 1000 * day, false), "sans durée de vie, le satellite fonctionne toujours");
        helper.assertTrue(fleet.collect(100 * day, true) == 12 && fleet.isEmpty(), "le satellite hors service devrait être oublié une fois vidé");
        // Un satellite de télécommunications relaie les données tant qu'il fonctionne (120 jours).
        fleet.deploy(SatelliteType.COMMUNICATION, 0);
        helper.assertTrue(fleet.hasRelay(day, true) && !fleet.hasRelay(121 * day, true), "relais des télécommunications incorrect");

        // Les satellites sont des charges utiles de fusée, avec leur masse.
        RocketParts.Part part = RocketParts.get(new ItemStack(ModItems.OBSERVATION_SATELLITE.get()));
        helper.assertTrue(part != null && part.kind() == RocketParts.Kind.PAYLOAD && part.mass() == 1200, "le satellite n'est pas une charge utile");
        // Le lanceur de test emporte un satellite d'observation jusqu'en orbite basse, pas plus loin.
        RocketStats.Reach reach = RocketStats.of(RocketItem.design(CreativeRockets.satelliteLauncher())).reach();
        helper.assertTrue(reach == RocketStats.Reach.LOW_ORBIT, "le lanceur de satellite devrait atteindre l'orbite basse : " + reach);

        // Fin de vol : un satellite géostationnaire sur un lanceur d'orbite basse est perdu.
        var server = helper.getLevel().getServer();
        String agency = "test:satellites";
        SatelliteManager.of(server, agency).clear();
        AgencyResearch research = ResearchManager.forAgency(server, agency);
        research.reset();
        SatelliteManager.Deployment lost = SatelliteManager.onFlight(server, agency, SatelliteType.COMMUNICATION, RocketStats.Reach.LOW_ORBIT);
        helper.assertTrue(lost.satellite() == null && SatelliteManager.of(server, agency).isEmpty(), "le satellite aurait dû être perdu");

        // Premier satellite expérimental : en orbite, avec sa prime ; le second n'en rapporte plus.
        SatelliteManager.Deployment first = SatelliteManager.onFlight(server, agency, SatelliteType.EXPERIMENTAL, reach);
        helper.assertTrue(first.satellite() != null && first.points() == 40 && research.points() == 40, "prime du premier satellite incorrecte");
        helper.assertTrue(SatelliteManager.onFlight(server, agency, SatelliteType.EXPERIMENTAL, reach).points() == 0,
                "le second satellite du même type ne doit pas rapporter de prime");

        // Navigation : le premier rapporte sa prime, le quatrième complète la constellation.
        int[] expected = {100, 0, 0, 150};
        for (int points : expected) {
            SatelliteManager.Deployment navigation = SatelliteManager.onFlight(server, agency, SatelliteType.NAVIGATION,
                    RocketStats.Reach.GEO_TRANSFER);
            helper.assertTrue(navigation.points() == points, "prime de navigation incorrecte : " + navigation.points());
        }
        helper.assertTrue(SatelliteManager.of(server, agency).all().size() == 6 && research.points() == 290, "bilan des satellites incorrect");
        SatelliteManager.of(server, agency).clear();

        // Les missions robotiques : la première sonde lunaire rapporte sa prime, la suivante un peu de science ; un
        // lanceur trop faible la perd ; un atterrisseur se pose pour de vrai, sur le sol de la Lune.
        String explorer = "test_probes";
        var probe = new ItemStack(ModItems.LUNAR_PROBE.get());
        var probeLost = com.orbitalis.phase3.exploration.ProbeMissions.onFlight(server, explorer, probe, RocketStats.Reach.LOW_ORBIT, BlockPos.ZERO);
        var mapped = com.orbitalis.phase3.exploration.ProbeMissions.onFlight(server, explorer, probe, RocketStats.Reach.LUNAR, BlockPos.ZERO);
        var again = com.orbitalis.phase3.exploration.ProbeMissions.onFlight(server, explorer, probe, RocketStats.Reach.LUNAR, BlockPos.ZERO);
        helper.assertTrue(probeLost != null && probeLost.points() == 0 && mapped.points() == 60 && again.points() == 10,
                "primes de la sonde lunaire incorrectes");
        var landed = com.orbitalis.phase3.exploration.ProbeMissions.onFlight(server, explorer, new ItemStack(ModItems.LUNAR_ROBOTIC_LANDER.get()),
                RocketStats.Reach.LUNAR, BlockPos.ZERO);
        helper.assertTrue(landed != null && landed.points() == 100, "le premier atterrisseur lunaire devrait rapporter 100 points");
        // Les planètes : l'échelle des destinations suit le delta-v (Vénus entre la Lune et Mars, Jupiter au-delà de
        // l'orbite géostationnaire) ; une sonde jovienne est perdue sur un lanceur qui ne va que vers Mars.
        helper.assertTrue(RocketStats.Reach.orbital(12_950) == RocketStats.Reach.VENUS && RocketStats.Reach.orbital(16_500) == RocketStats.Reach.JUPITER
                && RocketStats.Reach.orbital(30_000) == RocketStats.Reach.MERCURY && RocketStats.Reach.orbital(9_000) == null,
                "échelle des destinations incorrecte");
        var jovian = new ItemStack(ModItems.JUPITER_ORBITER.get());
        var jovianLost = com.orbitalis.phase3.exploration.ProbeMissions.onFlight(server, explorer, jovian, RocketStats.Reach.MARS, BlockPos.ZERO);
        var jovianMapped = com.orbitalis.phase3.exploration.ProbeMissions.onFlight(server, explorer, jovian, RocketStats.Reach.JUPITER, BlockPos.ZERO);
        var titanEntry = com.orbitalis.phase3.exploration.ProbeMissions.onFlight(server, explorer, new ItemStack(ModItems.TITAN_ENTRY_PROBE.get()),
                RocketStats.Reach.MERCURY, BlockPos.ZERO);
        helper.assertTrue(jovianLost != null && jovianLost.points() == 0 && jovianMapped.points() == 250 && titanEntry.points() == 380
                && titanEntry.landedAt() == null, "primes des sondes planétaires incorrectes");
        // Le retour d'échantillons martiens : sa prime la première fois, puis un peu de science ; il ne pose rien.
        var marsSamples = new ItemStack(ModItems.MARS_SAMPLE_RETURN.get());
        var returned = com.orbitalis.phase3.exploration.ProbeMissions.onFlight(server, explorer, marsSamples, RocketStats.Reach.MARS, BlockPos.ZERO);
        var returnedAgain = com.orbitalis.phase3.exploration.ProbeMissions.onFlight(server, explorer, marsSamples, RocketStats.Reach.MARS, BlockPos.ZERO);
        helper.assertTrue(returned != null && returned.points() == 200 && returned.landedAt() == null && returnedAgain.points() == 40,
                "primes du retour d'échantillons martiens incorrectes");
        // La station lunaire est un satellite en orbite lunaire : un lanceur qui n'atteint pas la Lune la perd.
        helper.assertTrue(SatelliteManager.onFlight(server, explorer, SatelliteType.LUNAR_GATEWAY, RocketStats.Reach.GEO_TRANSFER).satellite() == null,
                "la station lunaire aurait dû être perdue");
        SatelliteManager.Deployment gateway = SatelliteManager.onFlight(server, explorer, SatelliteType.LUNAR_GATEWAY, RocketStats.Reach.LUNAR);
        helper.assertTrue(gateway.satellite() != null && gateway.points() == 150, "prime de la station lunaire incorrecte : " + gateway.points());
        SatelliteManager.of(server, explorer).clear();
        // Le serveur d'essai n'a pas de Lune : on vérifie la pose sur le sol de son propre monde.
        BlockPos touchdown = com.orbitalis.phase3.exploration.ProbeMissions.land(server, helper.getLevel().dimension(),
                helper.absolutePos(new BlockPos(0, 1, 0)));
        helper.assertTrue(touchdown != null && helper.getLevel().getBlockState(touchdown).is(ModBlocks.ROBOTIC_LANDER.get())
                && !helper.getLevel().getBlockState(touchdown.below()).isAir(), "l'atterrisseur devrait se poser sur le sol : " + touchdown);
        helper.getLevel().setBlock(touchdown, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        // Une foreuse à glace hors de la Lune et de Mars ne produit rien, et le dit.
        BlockPos drillPos = helper.absolutePos(new BlockPos(0, 2, 0));
        helper.getLevel().setBlock(drillPos, ModBlocks.ICE_DRILL.get().defaultBlockState(), 3);
        var drill = (com.orbitalis.phase3.exploration.IsruBlockEntity) helper.getLevel().getBlockEntity(drillPos);
        var drillStatus = drill == null ? null : drill.status(helper.getLevel()).getContents();
        helper.assertTrue(drillStatus instanceof net.minecraft.network.chat.contents.TranslatableContents translatable
                && translatable.getKey().equals("exploration.orbitalis.isru.no_world"), "la foreuse devrait dire qu'elle ne marche que sur la Lune ou Mars");
        helper.getLevel().setBlock(drillPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);

        // Ce que les satellites changent au sol. Sans eux : ni météo, ni relais, ni prospection.
        net.minecraft.server.level.ServerPlayer operator = helper.makeMockServerPlayerInLevel();
        String own = ResearchManager.agencyKey(operator);
        SatelliteManager.of(server, own).clear();
        BlockPos station = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.assertTrue(SatelliteManager.forecast(server, own)[0] == com.orbitalis.core.network.SatelliteStatusPayload.FORECAST_NONE,
                "sans satellite météo, pas de prévision");
        helper.assertTrue(!SatelliteManager.hasRelay(server, own), "sans satellite de télécommunications, pas de relais");
        helper.assertTrue(SatelliteManager.prospect(operator, station).size() == 0, "sans satellite d'observation, pas de relevé");
        // Avec eux : la prévision, le relais, et un relevé de cinq tronçons de côté qui compte le minerai posé là.
        SatelliteManager.onFlight(server, own, SatelliteType.WEATHER, RocketStats.Reach.LOW_ORBIT);
        SatelliteManager.onFlight(server, own, SatelliteType.COMMUNICATION, RocketStats.Reach.GEO_TRANSFER);
        SatelliteManager.onFlight(server, own, SatelliteType.OBSERVATION, RocketStats.Reach.LOW_ORBIT);
        helper.assertTrue(SatelliteManager.forecast(server, own)[0] != com.orbitalis.core.network.SatelliteStatusPayload.FORECAST_NONE,
                "le satellite météo devrait donner une prévision");
        helper.assertTrue(SatelliteManager.hasRelay(server, own), "le satellite de télécommunications devrait relayer");
        com.orbitalis.core.network.ProspectPayload before = SatelliteManager.prospect(operator, station);
        helper.setBlock(new BlockPos(1, 2, 1), ModBlocks.TITANIUM_ORE.get());
        com.orbitalis.core.network.ProspectPayload after = SatelliteManager.prospect(operator, station);
        int titanium = com.orbitalis.phase1.satellite.ProspectOre.TITANIUM.ordinal();
        helper.assertTrue(after.size() == 5 && after.count(2, 2, titanium) == before.count(2, 2, titanium) + 1,
                "le relevé devrait compter le minerai posé sous la station : " + before.count(2, 2, titanium) + " puis " + after.count(2, 2, titanium));
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.AIR);
        SatelliteManager.of(server, own).clear();
        ResearchManager.forAgency(server, own).reset();

        // L'établi d'ingénieur : une étude demande sa technologie et les matériaux de son prototype, et ne se fait qu'une fois.
        operator.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        helper.assertTrue(com.orbitalis.core.research.EngineeringStudies.all().size() >= 25, "il manque des études");
        helper.assertTrue(com.orbitalis.core.research.EngineeringStudies.complete(operator, "kerolox_engine")
                == com.orbitalis.core.research.EngineeringStudies.Problem.MISSING_RESEARCH, "une étude sans sa technologie devrait être refusée");
        helper.assertTrue(com.orbitalis.core.research.EngineeringStudies.complete(operator, "rocket_sounding")
                == com.orbitalis.core.research.EngineeringStudies.Problem.MISSING_STUDY, "un lanceur demande que ses pièces soient étudiées");
        ResearchManager.forAgency(server, own).forceUnlock("solid_motors");
        helper.assertTrue(com.orbitalis.core.research.EngineeringStudies.complete(operator, "solid_motor")
                == com.orbitalis.core.research.EngineeringStudies.Problem.MISSING_MATERIALS, "une étude sans matériaux devrait être refusée");
        operator.getInventory().add(new ItemStack(ModItems.STEEL_PLATE.get(), 5));
        operator.getInventory().add(new ItemStack(net.minecraft.world.item.Items.GUNPOWDER, 8));
        operator.getInventory().add(new ItemStack(net.minecraft.world.item.Items.PAPER, 2));
        helper.assertTrue(com.orbitalis.core.research.EngineeringStudies.complete(operator, "solid_motor")
                == com.orbitalis.core.research.EngineeringStudies.Problem.NONE
                && com.orbitalis.core.research.EngineeringStudies.isDone(operator, "solid_motor")
                && com.orbitalis.core.research.EngineeringStudies.count(operator.getInventory(), ModItems.STEEL_PLATE.get()) == 1,
                "l'étude du moteur à poudre aurait dû se faire, et prendre quatre plaques d'acier");
        helper.assertTrue(com.orbitalis.core.research.EngineeringStudies.complete(operator, "solid_motor")
                == com.orbitalis.core.research.EngineeringStudies.Problem.ALREADY_DONE, "une étude ne se fait qu'une fois");
        helper.assertTrue(com.orbitalis.core.research.EngineeringStudies.complete(operator, "rocket_sounding")
                == com.orbitalis.core.research.EngineeringStudies.Problem.NONE, "le modèle de fusée-sonde aurait dû s'étudier");
        helper.assertTrue(com.orbitalis.core.research.EngineeringStudies.firstUnstudied(operator, List.of(new ItemStack(ModItems.SOLID_MOTOR.get()),
                new ItemStack(ModItems.KEROLOX_ENGINE.get()))).id().equals("kerolox_engine"), "le moteur à turbopompes n'est pas étudié");
        ResearchManager.forAgency(server, own).reset();
        operator.discard();
        helper.succeed();
    }

    /**
     * Vol habité : une fusée qui emporte une capsule la crée au sommet, l'emmène au décollage, puis la capsule
     * revient seule, sous parachutes, et se pose. Les fusées habitées de test atteignent ce qu'elles annoncent.
     */
    private static void crewedFlight(GameTestHelper helper) {
        // Fusées de test : la petite va dans l'espace depuis un 3×3, la grande en orbite depuis un 5×5.
        RocketDesign small = RocketItem.design(CreativeRockets.crewedSuborbital());
        helper.assertTrue(RocketStats.of(small).reach() == RocketStats.Reach.SUBORBITAL && RocketStats.of(small).liftoffMass() <= 20_000
                && RocketShape.of(small).height() <= 7, "la fusée habitée suborbitale ne convient pas à un pas de tir 3×3");
        RocketDesign large = RocketItem.design(CreativeRockets.crewedOrbital());
        helper.assertTrue(RocketStats.of(large).reach() == RocketStats.Reach.LOW_ORBIT && RocketStats.of(large).liftoffMass() <= 200_000
                && RocketShape.of(large).height() <= 23, "le lanceur habité orbital ne convient pas à un pas de tir 5×5 : "
                + RocketStats.of(large).reach() + ", " + RocketShape.of(large).height() + " blocs");

        BlockPos tablePos = new BlockPos(3, 2, 3);
        buildLaunchPad(helper, tablePos, 1);
        LaunchTableBlockEntity table = helper.getBlockEntity(tablePos, LaunchTableBlockEntity.class);
        table.commission(true);
        table.setReliable(true);

        // Capsule sur un moteur à poudre : un vol atmosphérique, sans ravitaillement.
        NonNullList<ItemStack> parts = NonNullList.withSize(RocketDesign.SLOT_COUNT, ItemStack.EMPTY);
        parts.set(RocketDesign.SLOT_PAYLOAD, new ItemStack(ModItems.CREW_CAPSULE.get()));
        parts.set(RocketDesign.SLOT_NOSE, new ItemStack(ModItems.NOSE_CONE.get()));
        parts.set(RocketDesign.engineSlot(0), new ItemStack(ModItems.SOLID_MOTOR.get()));
        helper.assertTrue(table.setRocket(RocketItem.create(parts)), "la fusée habitée n'a pas été posée");
        helper.assertTrue(table.isCrewed(), "la fusée devrait être reconnue comme habitée");
        // Le portique d'embarquement du plan vient de lui-même à la hauteur de la cabine, et le pas de tir reste formé.
        // Ici la cabine est basse : le bras ombilical, qui gênerait le passage, monte au-dessus du portique. L'ancien
        // portique occupait les étages 7 à 9 : le neuvième redevient colonne technique.
        table.refresh(helper.getLevel());
        int cabin = com.orbitalis.phase1.spaceport.RocketShape.cabinIndex(table.rocketShape().parts()) + 2;
        int wanted = PadPlan.gateLevel(1, false, cabin);
        helper.assertTrue(table.getPlan().gate() == wanted && wanted != new PadPlan(1, false, Direction.EAST).gate(),
                "le portique du plan devrait suivre la cabine : étage " + table.getPlan().gate() + " au lieu de " + wanted);
        helper.assertTrue(BoardingGateBlock.isCore(helper.getBlockState(tablePos.offset(-2, wanted - 1, 0)))
                && helper.getBlockState(tablePos.offset(-2, 8, 0)).is(ModBlocks.SERVICE_TOWER.get()),
                "le portique devrait avoir été déplacé, et la colonne technique remise à son ancienne place");
        helper.assertTrue(helper.getBlockState(tablePos.offset(table.getPlan().armOffset())).is(ModBlocks.UMBILICAL_ARM.get())
                && table.getComplex().isComplete() && table.getGateLevels().length == 1,
                "le pas de tir devrait rester complet après le déplacement du portique : il manque " + table.getComplex().missing() + " bloc(s)");

        CapsuleEntity capsule = table.capsule(true);
        helper.assertTrue(capsule != null && capsule.getPhase() == CapsuleEntity.Phase.PAD && !capsule.locksCrew(),
                "la capsule devrait attendre sur le pas de tir, et laisser redescendre son équipage");
        helper.assertTrue(table.capsule(true) == capsule, "une fusée ne doit avoir qu'une capsule");
        // Sans dérive : elle retombe à la verticale, dans la zone du test.
        capsule.setDrift(0.0, 0.0);
        // Le test ne tient active que sa propre case : on garde chargés les chunks autour de la table, sans quoi la
        // capsule peut s'y figer en plein vol. Ils restent chargés jusqu'à la fin de la série de tests.
        // La poussée emmène la fusée, et sa capsule avec elle, jusqu'à une centaine de blocs du pas de tir.
        BlockPos center = helper.absolutePos(tablePos);
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                helper.getLevel().setChunkForced((center.getX() >> 4) + dx, (center.getZ() >> 4) + dz, true);
            }
        }

        helper.assertTrue(table.startCountdown("test:crewed_flight"), "le compte à rebours aurait dû démarrer");
        boolean[] seen = new boolean[2];
        helper.onEachTick(() -> {
            seen[0] |= capsule.getPhase() == CapsuleEntity.Phase.ASCENT && capsule.locksCrew();
            seen[1] |= capsule.getPhase() == CapsuleEntity.Phase.CHUTE;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(table.getLastResult() == LaunchTableBlockEntity.Result.SUCCESS, "le vol n'est pas terminé");
            helper.assertTrue(capsule.getPhase() == CapsuleEntity.Phase.LANDED && !capsule.isRemoved(), "la capsule ne s'est pas posée");
            helper.assertTrue(seen[0] && seen[1], "la capsule n'a pas suivi la fusée puis ouvert ses parachutes");
        });
    }

    /**
     * Espace : la réserve d'oxygène d'une combinaison se vide et se remplit
     * sans jamais sortir de ses bornes.
     */
    private static void spaceRules(GameTestHelper helper) {
        // Le serveur de test n'a que l'Overworld : la dimension d'orbite se vérifie en jeu.
        helper.assertTrue(!SpaceRules.isOrbit(helper.getLevel()), "l'Overworld n'est pas l'orbite");

        ItemStack suit = new ItemStack(ModItems.SPACESUIT.get());
        helper.assertTrue(SpaceRules.oxygen(suit) == SpaceRules.SUIT_OXYGEN, "une combinaison neuve devrait être pleine");
        SpaceRules.addOxygen(suit, -90);
        helper.assertTrue(SpaceRules.oxygen(suit) == SpaceRules.SUIT_OXYGEN - 90, "l'oxygène consommé n'a pas été retiré");
        SpaceRules.addOxygen(suit, -10_000);
        helper.assertTrue(SpaceRules.oxygen(suit) == 0 && !suit.isEmpty(), "une combinaison vide reste une combinaison, à zéro");
        SpaceRules.addOxygen(suit, 10_000);
        helper.assertTrue(SpaceRules.oxygen(suit) == SpaceRules.SUIT_OXYGEN, "la recharge ne doit pas dépasser la réserve");
        helper.assertTrue(SpaceRules.oxygen(new ItemStack(ModItems.CREW_CAPSULE.get())) == 0, "seule une combinaison contient de l'oxygène");
        // Les combinaisons : un quart d'heure pour la blanche, dix minutes de plus avec son réservoir, qui repart avec son oxygène.
        ItemStack advanced = new ItemStack(ModItems.ADVANCED_SPACESUIT.get());
        helper.assertTrue(SpaceRules.oxygen(advanced) == com.orbitalis.phase2.survival.SpaceSuits.ADVANCED_OXYGEN, "la combinaison blanche devrait tenir 15 min");
        helper.assertTrue(com.orbitalis.phase2.survival.SpaceSuits.install(advanced, com.orbitalis.phase2.survival.SuitModule.OXYGEN_TANK)
                && SpaceRules.oxygen(advanced) == com.orbitalis.phase2.survival.SpaceSuits.ADVANCED_OXYGEN + com.orbitalis.phase2.survival.SpaceSuits.TANK_OXYGEN,
                "le réservoir supplémentaire n'a pas ajouté son oxygène : " + SpaceRules.oxygen(advanced));
        helper.assertTrue(!com.orbitalis.phase2.survival.SpaceSuits.install(advanced, com.orbitalis.phase2.survival.SuitModule.OXYGEN_TANK),
                "un module ne se pose qu'une fois par pièce");
        helper.assertTrue(!com.orbitalis.phase2.survival.SpaceSuits.install(advanced, com.orbitalis.phase2.survival.SuitModule.MAP),
                "la carte ne va pas sur le torse");
        helper.assertTrue(!com.orbitalis.phase2.survival.SpaceSuits.install(new ItemStack(ModItems.SPACE_HELMET.get()), com.orbitalis.phase2.survival.SuitModule.MAP),
                "la combinaison orange ne reçoit pas de module");
        SpaceRules.addOxygen(advanced, -1400);
        helper.assertTrue(com.orbitalis.phase2.survival.SpaceSuits.removeLast(advanced) == com.orbitalis.phase2.survival.SuitModule.OXYGEN_TANK
                && SpaceRules.oxygen(advanced) == 0 && SpaceRules.capacity(advanced) == com.orbitalis.phase2.survival.SpaceSuits.ADVANCED_OXYGEN,
                "le réservoir retiré devrait emporter sa part : " + SpaceRules.oxygen(advanced) + " / " + SpaceRules.capacity(advanced));
        ItemStack legs = new ItemStack(ModItems.ADVANCED_SPACE_LEGGINGS.get());
        com.orbitalis.phase2.survival.SpaceSuits.install(legs, com.orbitalis.phase2.survival.SuitModule.SHOCK_ABSORBERS);
        boolean[] absorbers = {false};
        legs.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY)
                .forEach(net.minecraft.world.entity.EquipmentSlot.LEGS, (attribute, modifier) -> absorbers[0] |= attribute.is(net.minecraft.world.entity.ai.attributes.Attributes.SAFE_FALL_DISTANCE));
        helper.assertTrue(absorbers[0], "les amortisseurs devraient allonger les chutes sans mal");
        // L'établi de combinaison retire un module précis, et laisse les autres en place.
        ItemStack helmet = new ItemStack(ModItems.ADVANCED_SPACE_HELMET.get());
        com.orbitalis.phase2.survival.SpaceSuits.install(helmet, com.orbitalis.phase2.survival.SuitModule.MAP);
        com.orbitalis.phase2.survival.SpaceSuits.install(helmet, com.orbitalis.phase2.survival.SuitModule.SENSOR);
        helper.assertTrue(com.orbitalis.phase2.survival.SpaceSuits.remove(helmet, com.orbitalis.phase2.survival.SuitModule.MAP)
                && com.orbitalis.phase2.survival.SpaceSuits.modules(helmet).equals(java.util.List.of(com.orbitalis.phase2.survival.SuitModule.SENSOR))
                && !com.orbitalis.phase2.survival.SpaceSuits.remove(helmet, com.orbitalis.phase2.survival.SuitModule.MAP),
                "l'établi devrait retirer la carte, et seulement elle : " + com.orbitalis.phase2.survival.SpaceSuits.modules(helmet));
        // Les centres spatiaux : leurs quatre structures sont déclarées, leurs coffres existent, et un plan d'archive décrit une vraie fusée.
        var structures = helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        for (com.orbitalis.core.world.FacilityKind kind : com.orbitalis.core.world.FacilityKind.values()) {
            helper.assertTrue(structures.get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE,
                    com.orbitalis.Orbitalis.id(kind.getSerializedName()))).isPresent(), "structure absente : " + kind);
            helper.assertTrue(helper.getLevel().getServer().reloadableRegistries().getLootTable(kind.loot())
                    != net.minecraft.world.level.storage.loot.LootTable.EMPTY, "coffres absents : " + kind);
        }
        helper.assertTrue(helper.getLevel().getServer().reloadableRegistries().getLootTable(com.orbitalis.core.world.FacilityKind.abandonedLoot())
                != net.minecraft.world.level.storage.loot.LootTable.EMPTY, "coffres des centres abandonnés absents");
        for (int k = 0; k < 8; k++) {
            ItemStack archive = com.orbitalis.core.world.ArchiveBlueprintFunction.archive(net.minecraft.util.RandomSource.create(k), 6);
            helper.assertTrue(archive.is(ModItems.ROCKET_BLUEPRINT.get())
                    && !com.orbitalis.phase1.rocket.RocketBlueprintItem.parts(archive).stream().allMatch(ItemStack::isEmpty),
                    "un plan d'archive devrait décrire une fusée");
            helper.assertTrue(com.orbitalis.phase1.rocket.RocketBlueprintItem.isArchive(archive), "un plan d'archive arrive non validé");
        }
        // Chaque modèle du catalogue a sa validation d'archive, à moitié prix du prototype du modèle.
        long archives = com.orbitalis.core.research.EngineeringStudies.all().stream().filter(com.orbitalis.core.research.EngineeringStudies::isArchive).count();
        helper.assertTrue(archives == com.orbitalis.phase1.rocket.RocketCatalog.all().size(), "études d'archive : " + archives);
        // Étanche seulement avec les trois pièces, de n'importe quelle combinaison.
        net.minecraft.world.entity.decoration.ArmorStand stand = helper.spawn(net.minecraft.world.entity.EntityTypes.ARMOR_STAND, 1, 2, 1);
        stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(ModItems.SPACESUIT.get()));
        stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(ModItems.ADVANCED_SPACE_HELMET.get()));
        helper.assertTrue(com.orbitalis.phase2.survival.SpaceSuits.missing(stand) == net.minecraft.world.entity.EquipmentSlot.LEGS, "il manque les jambières");
        stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new ItemStack(ModItems.SPACE_LEGGINGS.get()));
        helper.assertTrue(com.orbitalis.phase2.survival.SpaceSuits.missing(stand) == net.minecraft.world.entity.EquipmentSlot.FEET, "il manque les bottes");
        stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(ModItems.ADVANCED_SPACE_BOOTS.get()));
        helper.assertTrue(com.orbitalis.phase2.survival.SpaceSuits.missing(stand) == null, "quatre pièces font une combinaison étanche");
        stand.discard();
        // Les trois capsules : trois places pour la Mk1, quatre pour les Mk2 et Mk3 ; chacune se récupère telle quelle,
        // et chacune a son tronçon sur la fusée.
        for (int variant = 0; variant < 3; variant++) {
            CapsuleEntity capsule = new CapsuleEntity(com.orbitalis.core.registry.ModEntities.CAPSULE.get(), helper.getLevel());
            capsule.setVariant(variant);
            net.minecraft.world.item.Item expected = variant == 2 ? ModItems.CREW_CAPSULE_MK3.get() : variant == 1 ? ModItems.CREW_CAPSULE_MK2.get()
                    : ModItems.CREW_CAPSULE.get();
            helper.assertTrue(capsule.seats() == (variant == 0 ? 3 : 4) && capsule.asItem().is(expected),
                    "capsule " + variant + " : " + capsule.seats() + " places, récupérée en " + capsule.asItem());
            helper.assertTrue(com.orbitalis.phase1.rocket.RocketParts.cabinVariant(new ItemStack(expected)) == variant
                    && com.orbitalis.phase1.rocket.RocketParts.isCrewCabin(new ItemStack(expected)), "capsule " + variant + " mal reconnue");
            capsule.discard();
        }
        helper.succeed();
    }

    /**
     * Station : le module central, une fois alimenté, pressurise sa pièce ; une paroi cassée, et l'air s'échappe ;
     * la paroi réparée, il revient. Le lanceur de test emporte bien le module jusqu'en orbite basse.
     */
    private static void stationAir(GameTestHelper helper) {
        RocketDesign launcher = RocketItem.design(CreativeRockets.stationLauncher());
        helper.assertTrue(RocketStats.of(launcher).reach() == RocketStats.Reach.LOW_ORBIT && RocketStats.of(launcher).liftoffMass() <= 200_000
                && RocketShape.of(launcher).height() <= 23, "le lanceur de station ne convient pas à un pas de tir 5×5 : "
                + RocketStats.of(launcher).reach() + ", " + RocketShape.of(launcher).height() + " blocs");

        BlockPos center = new BlockPos(3, 4, 3);
        BlockPos absolute = helper.absolutePos(center);
        StationManager.build(helper.getLevel(), absolute);
        helper.assertTrue(helper.getBlockState(center.offset(0, 0, 2)).is(ModBlocks.DOCKING_PORT.get())
                && helper.getBlockState(center.offset(2, 0, 0)).is(ModBlocks.STATION_WINDOW.get())
                && helper.getBlockState(center.above(2)).is(ModBlocks.LIFE_SUPPORT.get()) && helper.getBlockState(center).isAir(),
                "le module central n'est pas bâti comme prévu");
        helper.assertTrue(!StationAir.isBreathable(helper.getLevel(), absolute), "sans électricité, pas d'air");
        // Une source d'énergie à la place du panneau solaire : le test ne dépend pas de l'heure qu'il est.
        helper.setBlock(center.above(3), ModBlocks.CREATIVE_ENERGY_SOURCE.get());

        BlockPos wall = center.offset(-2, -1, 1);
        int[] stage = {0};
        helper.succeedWhen(() -> {
            boolean air = StationAir.isBreathable(helper.getLevel(), absolute);
            if (stage[0] == 0) {
                helper.assertTrue(air && helper.getBlockState(center.above(2)).getValue(LifeSupportBlock.ACTIVE), "la pièce devrait se pressuriser");
                // 27 cases : la pièce de trois blocs de côté, ni plus ni moins.
                LifeSupportBlockEntity unit = helper.getBlockEntity(center.above(2), LifeSupportBlockEntity.class);
                helper.assertTrue(unit.getVolume() == 27, "volume pressurisé incorrect : " + unit.getVolume());
                helper.setBlock(wall, Blocks.AIR);
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                helper.assertTrue(!air, "une paroi cassée devrait vider la pièce de son air");
                helper.setBlock(wall, ModBlocks.STATION_HULL.get());
                stage[0] = 2;
            }
            helper.assertTrue(stage[0] >= 2 && air, "la paroi réparée, l'air devrait revenir");
            if (stage[0] == 2) {
                // Un module d'extension contre le mur ouest : refusé au plafond, accepté sur le mur, passage ouvert.
                helper.assertTrue(StationManager.expand(helper.getLevel(), absolute.above(2).west(), Direction.UP) != null,
                        "un module ne s'accole pas au plafond");
                helper.assertTrue(StationManager.expand(helper.getLevel(), absolute.offset(-2, 0, 1), Direction.WEST) == null,
                        "le module d'extension aurait dû s'accoler");
                helper.assertTrue(helper.getBlockState(center.offset(-2, 0, 1)).isAir() && helper.getBlockState(center.offset(-2, -1, 1)).isAir()
                        && helper.getBlockState(center.offset(-6, 0, 1)).is(ModBlocks.STATION_WINDOW.get()), "module d'extension mal bâti");
                stage[0] = 3;
            }
            // Les deux pièces et le passage : 27 + 27 + 2 cases, pressurisées par le même générateur.
            LifeSupportBlockEntity unit = helper.getBlockEntity(center.above(2), LifeSupportBlockEntity.class);
            helper.assertTrue(stage[0] == 3 && unit.getVolume() == 56, "volume après extension incorrect : " + unit.getVolume());
        });
    }

    /**
     * Modules de station : le module central aménagé (terminal, batterie), un laboratoire accolé au mur ouest (huit
     * baies, son terminal au fond), un sas de sortie au mur est. Le sas fermé tient l'air à l'écart ; ouvert, il
     * rejoint le volume. Ses portes sont verrouillées entre elles : l'extérieure refuse de s'ouvrir tant que
     * l'intérieure est ouverte ; l'intérieure fermée, elle fait son cycle puis s'ouvre, et la station garde son air. Un bac de culture planté dans le module central pousse
     * sous tension et allège le générateur d'air. Le réseau électrique charge et décharge sa batterie.
     */
    private static void stationModules(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        BlockPos center = new BlockPos(8, 4, 8);
        BlockPos absolute = helper.absolutePos(center);
        StationManager.build(level, absolute);
        helper.assertTrue(helper.getBlockState(center.offset(0, -1, -2)).is(ModBlocks.STATION_TERMINAL.get())
                && helper.getBlockState(center.offset(-2, -1, 0)).is(ModBlocks.STATION_BATTERY.get())
                && helper.getBlockState(center.offset(0, -2, 0)).is(ModBlocks.STATION_FLOOR.get())
                && helper.getBlockState(center.offset(1, 0, 2)).is(ModBlocks.STATION_PANEL.get()), "le module central n'est pas aménagé");

        // Laboratoire contre le mur ouest, sas de sortie contre le mur est.
        helper.assertTrue(com.orbitalis.phase2.station.StationModules.attach(level, absolute.offset(-2, 0, 1), Direction.WEST,
                com.orbitalis.phase2.station.ModuleType.LABORATORY) == null, "le laboratoire aurait dû s'accoler");
        int racks = 0;
        for (BlockPos pos : BlockPos.betweenClosed(absolute.offset(-8, -2, -1), absolute.offset(-3, 2, 3))) {
            racks += level.getBlockState(pos).is(ModBlocks.LAB_RACK.get()) ? 1 : 0;
        }
        helper.assertTrue(racks == 8, "le laboratoire devrait avoir huit baies : " + racks);
        helper.assertTrue(helper.getBlockState(center.offset(-8, -1, 1)).is(ModBlocks.STATION_TERMINAL.get())
                && helper.getBlockState(center.offset(-8, 0, 1)).is(ModBlocks.STATION_WINDOW.get())
                && helper.getBlockState(center.offset(-2, -1, 1)).isAir(), "laboratoire mal bâti");
        helper.assertTrue(com.orbitalis.phase2.station.StationModules.attach(level, absolute.offset(2, 0, 0), Direction.EAST,
                com.orbitalis.phase2.station.ModuleType.AIRLOCK) == null, "le sas aurait dû s'accoler");
        helper.assertTrue(helper.getBlockState(center.offset(2, -1, 0)).is(ModBlocks.AIRLOCK_DOOR.get())
                && helper.getBlockState(center.offset(5, 0, 0)).is(ModBlocks.AIRLOCK_DOOR.get()), "le sas n'a pas ses deux portes");
        helper.assertTrue(StationModules.attach(level, absolute.offset(2, 0, 0), Direction.EAST, com.orbitalis.phase2.station.ModuleType.CARGO) != null,
                "un module ne se bâtit pas sur un autre");

        // Un bac de culture planté, sous tension : la culture gagne un stade en STAGE_TICKS ticks.
        BlockPos trayPos = center.offset(1, -1, 1);
        helper.setBlock(trayPos, ModBlocks.HYDROPONIC_TRAY.get().defaultBlockState()
                .setValue(com.orbitalis.phase2.station.HydroponicTrayBlock.CROP, com.orbitalis.phase2.station.HydroponicTrayBlock.Crop.WHEAT));
        com.orbitalis.phase2.station.HydroponicTrayBlockEntity tray = helper.getBlockEntity(trayPos, com.orbitalis.phase2.station.HydroponicTrayBlockEntity.class);
        tray.getEnergy().generate(1_000_000);
        for (int k = 0; k <= com.orbitalis.phase2.station.HydroponicTrayBlockEntity.STAGE_TICKS; k++) {
            tray.serverTick(level, helper.absolutePos(trayPos), helper.getBlockState(trayPos));
        }
        helper.assertTrue(helper.getBlockState(trayPos).getValue(com.orbitalis.phase2.station.HydroponicTrayBlock.AGE) == 1,
                "le blé aurait dû gagner un stade");

        // Le réseau électrique : il charge la batterie, puis y puise ; il refuse ce qu'il n'a pas.
        // (La batterie du module central part chargée à moitié ; on en pose une neuve, à l'écart.)
        helper.assertTrue(helper.getBlockEntity(center.offset(-2, -1, 0), com.orbitalis.phase2.station.StationBatteryBlockEntity.class)
                .getEnergy().getEnergy() == com.orbitalis.phase2.station.StationBatteryBlockEntity.CAPACITY / 2, "la batterie du module central part chargée à moitié");
        helper.setBlock(center.offset(0, 7, 0), ModBlocks.STATION_BATTERY.get());
        com.orbitalis.phase2.station.StationBatteryBlockEntity battery = helper.getBlockEntity(center.offset(0, 7, 0),
                com.orbitalis.phase2.station.StationBatteryBlockEntity.class);
        com.orbitalis.phase2.station.StationPower.Bus bus = com.orbitalis.phase2.station.StationPower.bus(absolute.below(500));
        bus.attach(battery);
        helper.assertTrue(bus.offer(level, 1000) == 1000 && battery.getEnergy().getEnergy() == 1000, "la batterie aurait dû se charger");
        helper.assertTrue(bus.draw(level, 600) && battery.getEnergy().getEnergy() == 400, "le réseau aurait dû puiser dans la batterie");
        helper.assertTrue(!bus.draw(level, 5000) && battery.getEnergy().getEnergy() == 400, "le réseau ne peut pas donner ce qu'il n'a pas");
        bus.detach(battery);
        helper.assertTrue(com.orbitalis.phase2.station.StationPower.sunlit(level), "pas d'éclipse sur la planète");

        // Les coupoles d'une agence comptent pour son observatoire, deux au plus.
        String agency = "test:cupolas";
        BlockPos fake = new BlockPos(0, -2000, 0);
        StationManager.register(level.getServer(), agency, fake);
        com.orbitalis.phase2.station.StationLayout.clear(level.getServer(), fake);
        for (int k = 0; k < 3; k++) {
            com.orbitalis.phase2.station.StationLayout.add(level.getServer(), fake, com.orbitalis.phase2.station.ModuleType.CUPOLA, fake, fake.above());
        }
        helper.assertTrue(com.orbitalis.phase1.satellite.Observatory.cupolas(level.getServer(), agency) == 2, "deux coupoles au plus comptent");
        // Les fenêtres de visibilité : la Lune se voit à la pleine lune (phase 0), pas à la nouvelle lune (phase 4).
        var moon = com.orbitalis.phase1.satellite.CelestialBody.MOON;
        int day = com.orbitalis.phase1.satellite.Observatory.DAY;
        helper.assertTrue(com.orbitalis.phase1.satellite.Observatory.visible(moon, 8L * day)
                && !com.orbitalis.phase1.satellite.Observatory.visible(moon, 4L * day)
                && com.orbitalis.phase1.satellite.Observatory.untilChange(moon, 4L * day) == 2L * day,
                "la Lune devrait se voir à la pleine lune, et revenir deux jours après la nouvelle lune");

        helper.setBlock(center.above(3), ModBlocks.CREATIVE_ENERGY_SOURCE.get());
        BlockPos inner = absolute.offset(2, -1, 0);
        BlockPos outer = absolute.offset(5, -1, 0);
        int[] stage = {0};
        helper.succeedWhen(() -> {
            LifeSupportBlockEntity unit = helper.getBlockEntity(center.above(2), LifeSupportBlockEntity.class);
            boolean air = StationAir.isBreathable(level, absolute);
            if (stage[0] == 0) {
                // Le module central (27), le couloir du laboratoire (45 cases moins ses 8 baies) et le passage (2) ; le sas fermé reste
                // à part. Le bac laisse passer l'air.
                helper.assertTrue(air && unit.getVolume() == 66, "volume avec le laboratoire incorrect : " + unit.getVolume());
                helper.assertTrue(unit.getPlants() == 1 && unit.consumption() == LifeSupportBlockEntity.ENERGY_PER_TICK - LifeSupportBlockEntity.PLANT_SAVING,
                        "le bac planté aurait dû alléger le générateur : " + unit.getPlants());
                com.orbitalis.phase2.station.LabRackBlockEntity rack = helper.getBlockEntity(center.offset(-4, -1, 2), com.orbitalis.phase2.station.LabRackBlockEntity.class);
                helper.assertTrue(rack.getStatus() == com.orbitalis.phase2.station.LabRackBlockEntity.Status.NOT_IN_ORBIT, "pas d'expérience sans apesanteur");
                com.orbitalis.phase2.station.AirlockDoorBlock.setPhase(level, inner, com.orbitalis.phase2.station.AirlockDoorBlock.Phase.OPEN);
                com.orbitalis.phase2.station.AirlockDoorBlock.hold(level, inner, true);
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                // Le sas ouvert rejoint le volume : ses 18 cases moins le casier à combinaisons, et le passage.
                helper.assertTrue(air && unit.getVolume() == 85, "volume avec le sas ouvert incorrect : " + unit.getVolume());
                // Verrouillage : la porte extérieure refuse de s'ouvrir tant que la porte intérieure est ouverte.
                helper.assertTrue(com.orbitalis.phase2.station.AirlockDoorBlock.toggle(level, outer) != null, "le sas aurait dû rester verrouillé");
                // La porte intérieure se referme : ses battants coulissent, puis elle est close.
                com.orbitalis.phase2.station.AirlockDoorBlock.toggle(level, inner);
                stage[0] = 11;
            }
            if (stage[0] == 11) {
                helper.assertTrue(level.getBlockState(inner).getValue(com.orbitalis.phase2.station.AirlockDoorBlock.PHASE)
                        == com.orbitalis.phase2.station.AirlockDoorBlock.Phase.CLOSED, "la porte intérieure devrait finir de se refermer");
                helper.assertTrue(com.orbitalis.phase2.station.AirlockDoorBlock.toggle(level, outer) == null
                        && level.getBlockState(outer).getValue(com.orbitalis.phase2.station.AirlockDoorBlock.PHASE)
                        == com.orbitalis.phase2.station.AirlockDoorBlock.Phase.CYCLING, "la porte extérieure aurait dû lancer son cycle");
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                // Le cycle fini, la porte extérieure s'ouvre sur le vide : la porte intérieure fermée, la station garde son air.
                helper.assertTrue(com.orbitalis.phase2.station.AirlockDoorBlock.isOpen(level.getBlockState(outer.above())),
                        "la porte extérieure aurait dû s'ouvrir après son cycle");
                helper.assertTrue(level.getBlockState(outer).getCollisionShape(level, outer).isEmpty()
                        && level.getBlockState(outer.above()).getCollisionShape(level, outer.above()).isEmpty(),
                        "une porte de sas ouverte ne doit retenir personne");
                stage[0] = 3;
            }
            helper.assertTrue(stage[0] == 3 && air && unit.getVolume() == 66, "la porte intérieure fermée, la station devrait garder son air : "
                    + unit.getVolume());
        });
    }

    /**
     * Ravitaillement : les ergols cryogéniques ne sont acceptés qu'une fois le kérosène chargé ; une citerne créative
     * posée à quelques blocs du pas de tir, sans rien toucher, fait le plein complet ; un réservoir posé contre le
     * pied de la tour alimente la fusée.
     */
    private static void fuelling(GameTestHelper helper) {
        BlockPos tablePos = new BlockPos(3, 2, 3);
        buildLaunchPad(helper, tablePos, 1);
        LaunchTableBlockEntity table = helper.getBlockEntity(tablePos, LaunchTableBlockEntity.class);
        table.commission(true);
        NonNullList<ItemStack> parts = NonNullList.withSize(RocketDesign.SLOT_COUNT, ItemStack.EMPTY);
        parts.set(RocketDesign.SLOT_NOSE, new ItemStack(ModItems.NOSE_CONE.get()));
        parts.set(RocketDesign.engineSlot(0), new ItemStack(ModItems.PRESSURE_FED_ENGINE.get(), 2));
        parts.set(RocketDesign.tankSlot(0), new ItemStack(ModItems.STEEL_TANK.get()));
        helper.assertTrue(table.setRocket(RocketItem.create(parts)), "la fusée n'a pas été posée");
        RocketFuel needed = table.requiredFuel();

        // Vannes fermées, rien n'entre dans la fusée ; on les ouvre.
        helper.assertTrue(table.fill(ModFluids.RP1.get(), 10, false) == 0, "vannes fermées, le kérosène ne doit pas être chargé");
        helper.assertTrue(table.command(LaunchStep.VALVES, "test:fuelling") == null && table.areValvesOpen(), "les vannes auraient dû s'ouvrir");
        // L'oxygène liquide est refusé tant que le kérosène manque, puis accepté.
        helper.assertTrue(table.fill(ModFluids.LIQUID_OXYGEN.get(), 10, false) == 0, "l'oxygène ne doit pas être chargé avant le kérosène");
        helper.assertTrue(table.fill(ModFluids.RP1.get(), 100_000, false) == needed.rp1(), "le kérosène devrait être accepté en entier");
        helper.assertTrue(table.fill(ModFluids.LIQUID_OXYGEN.get(), 10, false) == 10, "une fois le kérosène chargé, l'oxygène est accepté");
        table.restoreFuel(new int[3]);

        // Un réservoir de RP-1 contre un bloc de ravitaillement, au pied de la tour, et une citerne créative à deux
        // blocs de la table.
        BlockPos tankPos = placeFuelPort(helper, tablePos, 1, FuelPortBlock.Kind.RP1);
        helper.setBlock(tankPos, ModBlocks.CRYOGENIC_TANK.get());
        helper.getBlockEntity(tankPos, CryogenicTankBlockEntity.class).getTank().setFluid(0, new FluidStack(ModFluids.RP1.get(), 50));
        // Tout près de sa table : la citerne sert le pas de tir le plus proche, et d'autres tests en ont un à côté.
        helper.setBlock(tablePos.offset(0, 0, 2), ModBlocks.CREATIVE_FLUID_SOURCE.get());
        helper.succeedWhen(() -> {
            helper.assertTrue(table.isFuelled(), "la citerne créative aurait dû faire le plein");
            helper.assertTrue(helper.getBlockEntity(tankPos, CryogenicTankBlockEntity.class).getTank().getFluid(0).getAmount() < 50,
                    "le réservoir posé contre la tour aurait dû alimenter la fusée");
        });
    }

    /**
     * Panneaux solaires : posés côte à côte, ils se raccordent en un grand panneau, qui s'arrête à six éléments
     * de long ; le septième reste indépendant. Tout le grand panneau alimente une machine branchée à un seul bout.
     */
    private static void solarArray(GameTestHelper helper) {
        SolarPanelBlock panel = ModBlocks.SOLAR_PANEL.get();
        // Sept panneaux en ligne, posés un par un comme le ferait un joueur.
        for (int x = 0; x < 7; x++) {
            BlockPos pos = new BlockPos(x, 2, 1);
            BlockPos absolute = helper.absolutePos(pos);
            BlockState state = panel.defaultBlockState();
            java.util.Set<BlockPos> array = new java.util.HashSet<>(java.util.Set.of(absolute));
            boolean touches = false;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (helper.getLevel().getBlockState(absolute.relative(direction)).is(panel)) {
                    touches = true;
                    array.addAll(SolarPanelBlock.array(helper.getLevel(), absolute.relative(direction)));
                }
            }
            if (touches && array.size() <= SolarPanelBlock.MAX_LENGTH) {
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    state = state.setValue(SolarPanelBlock.CONNECTIONS.get(direction),
                            helper.getLevel().getBlockState(absolute.relative(direction)).is(panel));
                }
            }
            helper.getLevel().setBlockAndUpdate(absolute, state);
        }
        BlockPos first = helper.absolutePos(new BlockPos(0, 2, 1));
        helper.assertTrue(SolarPanelBlock.array(helper.getLevel(), first).size() == 6, "le grand panneau devrait compter six éléments : "
                + SolarPanelBlock.array(helper.getLevel(), first).size());
        helper.assertTrue(SolarPanelBlock.array(helper.getLevel(), helper.absolutePos(new BlockPos(6, 2, 1))).size() == 1,
                "le septième élément devrait rester indépendant");
        helper.assertTrue(helper.getBlockState(new BlockPos(2, 2, 1)).getValue(BlockStateProperties.EAST)
                && helper.getBlockState(new BlockPos(2, 2, 1)).getValue(BlockStateProperties.WEST)
                && !helper.getBlockState(new BlockPos(5, 2, 1)).getValue(BlockStateProperties.EAST), "raccordements du grand panneau incorrects");
        helper.succeed();
    }

    /**
     * Écrans de contrôle : un mur non relié n'a qu'un affichage ; relié à un pas de tir, il fait défiler les cinq
     * affichages du pas de tir ; relié en plus aux satellites d'une agence, il y ajoute le leur et en tient la liste.
     */
    private static void displayPages(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, ModBlocks.MISSION_DISPLAY.get());
        MissionDisplayBlockEntity display = helper.getBlockEntity(pos, MissionDisplayBlockEntity.class);
        helper.assertTrue(display.page() == MissionDisplayBlockEntity.Page.OVERVIEW && display.nextPage() == MissionDisplayBlockEntity.Page.OVERVIEW,
                "un mur non relié n'a que la vue d'ensemble");

        // Relié à un pas de tir : cinq affichages, puis retour au premier, sans celui des satellites.
        display.setLink(helper.absolutePos(pos));
        java.util.List<MissionDisplayBlockEntity.Page> seen = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            display.setPage(display.nextPage());
            seen.add(display.page());
        }
        helper.assertTrue(seen.equals(java.util.List.of(MissionDisplayBlockEntity.Page.MAP, MissionDisplayBlockEntity.Page.ROCKET,
                MissionDisplayBlockEntity.Page.TELEMETRY, MissionDisplayBlockEntity.Page.TRAJECTORY, MissionDisplayBlockEntity.Page.OVERVIEW)),
                "défilement des affichages incorrect : " + seen);

        // Relié aux satellites : l'affichage passe sur eux, et la liste suit ce que l'agence a en orbite.
        var server = helper.getLevel().getServer();
        String agency = "test:display_pages";
        SatelliteManager.of(server, agency).clear();
        SatelliteManager.onFlight(server, agency, SatelliteType.WEATHER, RocketStats.Reach.LOW_ORBIT);
        display.setSatelliteLink(agency);
        display.refreshSatellites(server);
        helper.assertTrue(display.page() == MissionDisplayBlockEntity.Page.SATELLITES, "le mur devrait montrer les satellites");
        int[] satellites = display.satellites();
        helper.assertTrue(satellites.length == MissionDisplayBlockEntity.SATELLITE_FIELDS && satellites[0] == 1
                && satellites[1] == SatelliteType.WEATHER.ordinal() && satellites[2] == 1, "liste des satellites du mur incorrecte");
        display.setPage(display.nextPage());
        helper.assertTrue(display.page() == MissionDisplayBlockEntity.Page.OVERVIEW, "après les satellites, retour à la vue d'ensemble");
        SatelliteManager.of(server, agency).clear();
        helper.succeed();
    }

    /**
     * Vol calculé : chaque lanceur orbital de référence largue son premier étage, franchit la ligne de Kármán,
     * ne dépasse pas 4 g et finit en orbite, à la vitesse orbitale, vers 200 km, avec une marge d'ergols ;
     * la fusée-sonde monte à la verticale, s'éteint, et culmine dans l'espace.
     */
    private static void flightPhysics(GameTestHelper helper) {
        // La mécanique du vol : la poussée dans l'axe, la pesanteur vers le bas, et rien d'autre.
        // (Un autre test, qui tourne en même temps, borne l'éloignement des fusées : ici, on mesure sans borne.)
        double bounded = com.orbitalis.phase1.spaceport.RocketMotion.testLimit;
        com.orbitalis.phase1.spaceport.RocketMotion.testLimit = 0.0;
        // Debout, la fusée monte droit, avec l'accélération de la montée normale : 70 blocs en dix secondes.
        com.orbitalis.phase1.spaceport.RocketMotion upright = new com.orbitalis.phase1.spaceport.RocketMotion();
        for (int tick = 0; tick < 200; tick++) {
            upright.step(new org.joml.Vector3d(0.0, 1.0, 0.0));
        }
        double expected = LaunchTableBlockEntity.ASCENT_RATE * 100.0;
        helper.assertTrue(Math.abs(upright.offset().y - expected) < 1.5 && Math.abs(upright.offset().x) < 1.0E-9 && Math.abs(upright.offset().z) < 1.0E-9,
                "debout, la fusée devrait monter droit de " + expected + " blocs en dix secondes, et non de " + upright.offset());
        // À 60° de la verticale, la poussée porte tout juste son poids : elle ne monte plus, et file de côté.
        com.orbitalis.phase1.spaceport.RocketMotion leaning = new com.orbitalis.phase1.spaceport.RocketMotion();
        for (int tick = 0; tick < 200; tick++) {
            leaning.step(new org.joml.Vector3d(Math.sin(Math.toRadians(60.0)), Math.cos(Math.toRadians(60.0)), 0.0));
        }
        helper.assertTrue(Math.abs(leaning.offset().y) < 0.5 && leaning.offset().x > expected,
                "à 60° de la verticale, la fusée ne devrait plus monter, et partir de côté : " + leaning.offset());
        // Couchée à plat, la pesanteur l'emporte : elle retombe. Et la vitesse prise de côté lui reste si elle se redresse.
        com.orbitalis.phase1.spaceport.RocketMotion flat = new com.orbitalis.phase1.spaceport.RocketMotion();
        for (int tick = 0; tick < 100; tick++) {
            flat.step(new org.joml.Vector3d(1.0, 0.0, 0.0));
        }
        helper.assertTrue(flat.offset().y < -5.0, "à plat, la fusée devrait retomber : " + flat.offset());
        double sideways = flat.velocity().x;
        flat.step(new org.joml.Vector3d(0.0, 1.0, 0.0));
        helper.assertTrue(Math.abs(flat.velocity().x - sideways) < 1.0E-9, "se redresser ne doit pas effacer la vitesse déjà prise de côté");
        com.orbitalis.phase1.spaceport.RocketMotion.testLimit = bounded;
        for (ItemStack rocket : List.of(CreativeRockets.all().get(2), CreativeRockets.satelliteLauncher(), CreativeRockets.crewedOrbital(),
                CreativeRockets.stationLauncher())) {
            RocketStats stats = RocketStats.of(RocketItem.design(rocket));
            FlightSimulation flight = FlightSimulation.of(stats);
            String name = rocket.getHoverName().getString();
            List<FlightSimulation.EventType> types = flight.events().stream().map(FlightSimulation.Event::type).toList();
            Orbitalis.LOGGER.info("Vol calculé de {} : {} s, {} km, {} m/s, marge {} kg, événements {}", name, (int) flight.duration(),
                    (int) (flight.maxAltitude() / 1000), (int) flight.speed(1.0), (int) flight.propellantLeft(), types);
            helper.assertTrue(types.equals(List.of(FlightSimulation.EventType.SPACE, FlightSimulation.EventType.STAGING, FlightSimulation.EventType.ORBIT))
                    || types.equals(List.of(FlightSimulation.EventType.STAGING, FlightSimulation.EventType.SPACE, FlightSimulation.EventType.ORBIT)),
                    name + " : déroulement du vol inattendu : " + types);
            double altitude = flight.altitude(1.0) / 1000.0;
            helper.assertTrue(altitude >= 170 && altitude <= 230, name + " : altitude d'orbite hors limites : " + altitude);
            helper.assertTrue(flight.speed(1.0) >= 7_600 && flight.speed(1.0) <= 8_100, name + " : vitesse d'orbite incorrecte : " + flight.speed(1.0));
            helper.assertTrue(flight.propellantLeft() > 0, name + " : aucune marge d'ergols");
            for (int i = 0; i <= 100; i++) {
                helper.assertTrue(flight.gForce(i / 100.0) <= FlightSimulation.G_LIMIT + 0.01, name + " : accélération excessive");
                helper.assertTrue(i == 0 || flight.altitude(i / 100.0) >= flight.altitude((i - 1) / 100.0) - 1.0, name + " : la fusée redescend");
            }
            helper.assertTrue(flight.stage(0.0) == 1 && flight.stage(1.0) == 2, name + " : étages allumés incorrects");
        }

        // Fusée-sonde : un seul étage à poudre, à la verticale.
        RocketStats sounding = RocketStats.of(RocketItem.design(CreativeRockets.all().get(0)));
        FlightSimulation hop = FlightSimulation.of(sounding);
        List<FlightSimulation.EventType> types = hop.events().stream().map(FlightSimulation.Event::type).toList();
        Orbitalis.LOGGER.info("Vol calculé de la fusée-sonde : {} s, apogée {} km, événements {}", (int) hop.duration(),
                (int) (hop.maxAltitude() / 1000), types);
        helper.assertTrue(types.equals(List.of(FlightSimulation.EventType.BURNOUT, FlightSimulation.EventType.SPACE, FlightSimulation.EventType.APOGEE)),
                "fusée-sonde : déroulement du vol inattendu : " + types);
        // L'atlas : une tuile de terrain se calcule sans charger le monde, case par case ; le monde chargé y remplace
        // ce que le générateur prévoyait ; et un centre de contrôle ne connaît que ses environs.
        BlockPos here = helper.absolutePos(BlockPos.ZERO);
        int atlasSpan = com.orbitalis.core.atlas.AtlasTiles.TILE * com.orbitalis.core.atlas.AtlasTiles.CELL[0];
        int atlasX = Math.floorDiv(here.getX(), atlasSpan);
        int atlasZ = Math.floorDiv(here.getZ(), atlasSpan);
        // La mémoire de l'atlas relève la vraie surface de ce qui est chargé, et la tuile la reprend telle quelle.
        com.orbitalis.core.atlas.AtlasStore memory = com.orbitalis.core.atlas.AtlasStore.of(helper.getLevel());
        memory.record(helper.getLevel(), helper.getLevel().getChunkAt(here));
        int[] surface = memory.chunk(here.getX() >> 4, here.getZ() >> 4);
        helper.assertTrue(surface != null && surface.length == 256 && memory.summary(here.getX() >> 4, here.getZ() >> 4) != 0,
                "la surface du morceau de monde chargé aurait dû être relevée");
        int[] generated = com.orbitalis.core.atlas.AtlasTiles.compute(helper.getLevel(), 0, atlasX, atlasZ).join();
        helper.assertTrue(generated.length == com.orbitalis.core.atlas.AtlasTiles.SIDE * com.orbitalis.core.atlas.AtlasTiles.SIDE
                && java.util.Arrays.stream(generated).allMatch(cell -> cell != 0), "une tuile de l'atlas devrait être relevée en entier");
        int column = here.getX() - (atlasX * com.orbitalis.core.atlas.AtlasTiles.TILE - com.orbitalis.core.atlas.AtlasTiles.MARGIN);
        int row = here.getZ() - (atlasZ * com.orbitalis.core.atlas.AtlasTiles.TILE - com.orbitalis.core.atlas.AtlasTiles.MARGIN);
        helper.assertTrue(generated[row * com.orbitalis.core.atlas.AtlasTiles.SIDE + column] == surface[(here.getZ() & 15) * 16 + (here.getX() & 15)],
                "au bloc près, la tuile devrait montrer la surface relevée, pas celle du générateur");
        helper.assertTrue(com.orbitalis.core.atlas.AtlasTiles.isKnown(here, 0, atlasX, atlasZ)
                && !com.orbitalis.core.atlas.AtlasTiles.isKnown(here, 0, atlasX + 1000, atlasZ)
                && com.orbitalis.core.atlas.AtlasTiles.isKnown(here, 6, Math.floorDiv(here.getX(), com.orbitalis.core.atlas.AtlasTiles.TILE * 64),
                        Math.floorDiv(here.getZ(), com.orbitalis.core.atlas.AtlasTiles.TILE * 64)),
                "un centre de contrôle ne connaît que ses environs");
        // Les installations de l'atlas : une station posée y entre, cassée elle en sort.
        BlockPos stationAt = helper.absolutePos(new BlockPos(0, 1, 0));
        helper.getLevel().setBlockAndUpdate(stationAt, com.orbitalis.core.registry.ModBlocks.TRACKING_STATION.get().defaultBlockState());
        boolean noted = com.orbitalis.core.atlas.AtlasSites.of(helper.getLevel()).kindAt(stationAt) == com.orbitalis.core.network.AtlasSitesPayload.STATION;
        helper.getLevel().removeBlock(stationAt, false);
        helper.assertTrue(noted && com.orbitalis.core.atlas.AtlasSites.of(helper.getLevel()).kindAt(stationAt) < 0,
                "une station de poursuite devrait entrer dans les installations de l'atlas quand on la pose, et en sortir quand on la casse");
        // Ce que connaît une agence grandit avec ses satellites d'observation, jusqu'à la limite.
        helper.assertTrue(com.orbitalis.core.atlas.AtlasTiles.knownRadius(0) == com.orbitalis.core.atlas.AtlasTiles.BASE_RADIUS
                && com.orbitalis.core.atlas.AtlasTiles.knownRadius(1) > com.orbitalis.core.atlas.AtlasTiles.BASE_RADIUS
                && com.orbitalis.core.atlas.AtlasTiles.knownRadius(10) == com.orbitalis.core.atlas.AtlasTiles.KNOWN_RADIUS,
                "le rayon connu devrait grandir avec les satellites d'observation, sans dépasser la limite");
        // Les repères d'une agence : posés, renommés, retirés ; une autre agence ne les voit pas.
        var server = helper.getLevel().getServer();
        String world = helper.getLevel().dimension().identifier().toString();
        com.orbitalis.core.atlas.AtlasMarkers.add(server, "test_agency_pins", world, 10, 70, -20, "Testeur");
        var pins = com.orbitalis.core.atlas.AtlasMarkers.of(server, "test_agency_pins", world);
        boolean placed = pins.size() == 1 && pins.get(0).x() == 10 && pins.get(0).name().isEmpty();
        com.orbitalis.core.atlas.AtlasMarkers.rename(server, "test_agency_pins", pins.get(0).id(), "  Zone de récupération  ");
        boolean renamed = com.orbitalis.core.atlas.AtlasMarkers.of(server, "test_agency_pins", world).get(0).name().equals("Zone de récupération");
        boolean private_ = com.orbitalis.core.atlas.AtlasMarkers.of(server, "other_agency_pins", world).isEmpty();
        com.orbitalis.core.atlas.AtlasMarkers.remove(server, "test_agency_pins", pins.get(0).id());
        helper.assertTrue(placed && renamed && private_ && com.orbitalis.core.atlas.AtlasMarkers.of(server, "test_agency_pins", world).isEmpty(),
                "un repère devrait se poser, se renommer et se retirer, et rester propre à son agence");
        // La tablette de mission : sans satellite relais, elle refuse ; avec un satellite de télécommunications en
        // service, tablette en main, elle donne accès à l'atlas là où on l'ouvre, et pas au-delà de sa portée.
        net.minecraft.server.level.ServerPlayer reader = helper.makeMockServerPlayerInLevel();
        reader.getAbilities().instabuild = true;
        String readerAgency = com.orbitalis.core.research.ResearchManager.agencyKey(reader);
        com.orbitalis.phase1.satellite.SatelliteManager.of(server, readerAgency).clear();
        boolean refusedWithoutRelay = com.orbitalis.core.atlas.AtlasAccess.tabletRefusal(reader) != null;
        com.orbitalis.phase1.satellite.SatelliteManager.onFlight(server, readerAgency, com.orbitalis.phase1.satellite.SatelliteType.COMMUNICATION,
                RocketStats.Reach.GEOSTATIONARY);
        reader.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(com.orbitalis.core.registry.ModItems.MISSION_TABLET.get()));
        BlockPos openedAt = reader.blockPosition();
        boolean tabletAllowed = com.orbitalis.core.atlas.AtlasAccess.tabletRefusal(reader) == null
                && com.orbitalis.core.atlas.AtlasAccess.allowed(reader, openedAt)
                && !com.orbitalis.core.atlas.AtlasAccess.allowed(reader, openedAt.offset(2000, 0, 0));
        reader.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
        boolean needsTablet = !com.orbitalis.core.atlas.AtlasAccess.allowed(reader, openedAt);
        com.orbitalis.phase1.satellite.SatelliteManager.of(server, readerAgency).clear();
        helper.assertTrue(refusedWithoutRelay && tabletAllowed && needsTablet,
                "la tablette ne devrait ouvrir l'atlas qu'avec un satellite relais, en main, et à sa portée");
        // L'observatoire : un satellite d'observation découvre la Lune, ce qui révèle Mars ; le ciel profond demande un
        // télescope ; et la sauvegarde garde tout.
        {
            var sky = new com.orbitalis.phase1.satellite.AgencySatellites();
            var moon = com.orbitalis.phase1.satellite.CelestialBody.MOON;
            var mars = com.orbitalis.phase1.satellite.CelestialBody.MARS;
            var orion = com.orbitalis.phase1.satellite.CelestialBody.ORION_NEBULA;
            sky.deploy(com.orbitalis.phase1.satellite.SatelliteType.OBSERVATION, 0L);
            boolean marsHidden = !com.orbitalis.phase1.satellite.Observatory.listed(sky, mars);
            boolean deepNeedsTelescope = com.orbitalis.phase1.satellite.Observatory.power(sky, com.orbitalis.phase1.satellite.CelestialBody.PLEIADES, 1L, true) == 0;
            sky.observe(moon);
            com.orbitalis.phase1.satellite.CelestialBody found = null;
            for (int step = 0; step < 200 && found == null; step++) {
                found = com.orbitalis.phase1.satellite.Observatory.advance(sky, 1L, true);
            }
            boolean discovered = found == moon && sky.isDiscovered(moon) && sky.observing() == null
                    && com.orbitalis.phase1.satellite.Observatory.listed(sky, mars) && !com.orbitalis.phase1.satellite.Observatory.listed(sky, orion);
            sky.image(moon);
            sky.observe(mars);
            sky.addProgress(mars, 300);
            var saved = com.orbitalis.phase1.satellite.AgencySatellites.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, sky).getOrThrow();
            var loaded = com.orbitalis.phase1.satellite.AgencySatellites.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, saved).getOrThrow();
            boolean kept = loaded.isImaged(moon) && loaded.isDiscovered(moon) && loaded.observing() == mars && loaded.progress(mars) == 300;
            helper.assertTrue(marsHidden && deepNeedsTelescope && discovered && kept,
                    "l'observatoire devrait découvrir la Lune, révéler Mars, réserver le ciel profond au télescope, et tout garder");
        }

        // Ce que disent les écrans colle à ce que l'on voit : tant que la fusée est en vue, l'altitude affichée est
        // celle de sa hauteur dans le monde, pour une fusée nerveuse comme pour un lanceur lourd ; et l'horloge de
        // mission compte les secondes du jeu.
        for (RocketStats craft : List.of(sounding, RocketStats.of(RocketItem.design(CreativeRockets.crewedOrbital())))) {
            double previous = 0.0;
            int total = com.orbitalis.phase1.spaceport.FlightProfile.flightTicks(craft);
            for (int tick = 0; tick <= total; tick += 20) {
                double progress = tick / (double) total;
                double seconds = tick / 20.0;
                double seen = LaunchTableBlockEntity.ASCENT_RATE * seconds * seconds;
                double shown = com.orbitalis.phase1.spaceport.FlightProfile.altitudeKm(craft, progress) * com.orbitalis.phase1.spaceport.FlightProfile.BLOCKS_PER_KM;
                if (seen >= 8.0 && seen <= LaunchTableBlockEntity.ASCENT_LIMIT) {
                    helper.assertTrue(Math.abs(shown - seen) <= 0.08 * seen + 2.0, "à T+" + (int) seconds + " s, l'écran dit " + (int) shown
                            + " blocs d'altitude quand la fusée est à " + (int) seen + " blocs");
                }
                double sim = com.orbitalis.phase1.spaceport.FlightProfile.simProgress(craft, progress);
                helper.assertTrue(sim >= previous - 1.0E-9, "le vol calculé ne doit jamais reculer");
                previous = sim;
                helper.assertTrue(Math.abs(com.orbitalis.phase1.spaceport.FlightProfile.missionSeconds(craft, progress) - seconds) < 1.0E-6,
                        "l'horloge de mission doit compter les secondes du jeu");
            }
            helper.assertTrue(Math.abs(previous - 1.0) < 1.0E-9, "le vol calculé doit être arrivé au bout à la fin du vol en jeu");
            Orbitalis.LOGGER.info("Calage du vol : T+5 s {} km, T+10 s {} km, T+20 s {} km, T+30 s {} km, T+40 s {} km",
                    String.format(java.util.Locale.ROOT, "%.2f", com.orbitalis.phase1.spaceport.FlightProfile.altitudeKm(craft, 0.125)),
                    String.format(java.util.Locale.ROOT, "%.2f", com.orbitalis.phase1.spaceport.FlightProfile.altitudeKm(craft, 0.25)),
                    String.format(java.util.Locale.ROOT, "%.1f", com.orbitalis.phase1.spaceport.FlightProfile.altitudeKm(craft, 0.5)),
                    String.format(java.util.Locale.ROOT, "%.0f", com.orbitalis.phase1.spaceport.FlightProfile.altitudeKm(craft, 0.75)),
                    String.format(java.util.Locale.ROOT, "%.0f", com.orbitalis.phase1.spaceport.FlightProfile.altitudeKm(craft, 1.0)));
        }
        helper.assertTrue(hop.maxAltitude() >= 100_000 && hop.downrange(1.0) == 0 && hop.speed(1.0) < 5.0, "fusée-sonde : apogée incorrect");
        helper.succeed();
    }

    /**
     * Amarrage : une capsule arrivée à distance d'une station garde son équipage à bord, s'approche en
     * ralentissant, et finit exactement contre le sas, amarrée ; sa vitesse au contact est très faible.
     */
    private static void docking(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(3, 6, 3));
        // Le test ne tient active que sa propre case : on garde chargés les chunks que la capsule traverse,
        // sans quoi elle peut s'y figer.
        // Seuls ceux que ce test retient lui-même seront libérés : les autres servent aux tests voisins.
        List<long[]> held = new java.util.ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int chunkX = (center.getX() >> 4) + dx;
                int chunkZ = (center.getZ() >> 4) + dz;
                if (helper.getLevel().setChunkForced(chunkX, chunkZ, true)) {
                    held.add(new long[] {chunkX, chunkZ});
                }
            }
        }
        StationManager.build(helper.getLevel(), center);
        CapsuleEntity capsule = new CapsuleEntity(ModEntities.CAPSULE.get(), helper.getLevel());
        capsule.setPos(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
        helper.getLevel().addFreshEntity(capsule);
        capsule.startApproach(center, 8.0);
        Vec3 dock = StationManager.capsulePositionAt(center);
        helper.assertTrue(capsule.position().distanceTo(dock) > 7 && capsule.locksCrew() && !capsule.isDocked(),
                "la capsule devrait commencer son approche à distance, équipage à bord");

        double[] lastStep = {1.0};
        Vec3[] previous = {capsule.position()};
        helper.onEachTick(() -> {
            if (!capsule.isDocked()) {
                lastStep[0] = capsule.position().distanceTo(previous[0]);
                previous[0] = capsule.position();
            }
        });
        boolean[] undocking = {false};
        helper.succeedWhen(() -> {
            if (!undocking[0]) {
                helper.assertTrue(capsule.isDocked() && !capsule.locksCrew(), "la capsule n'est pas amarrée");
                helper.assertTrue(capsule.position().distanceTo(dock) < 0.01, "la capsule n'est pas au contact du sas");
                helper.assertTrue(lastStep[0] < 0.05, "contact trop rapide : " + lastStep[0] + " bloc par tick");
                // Désamarrage : la capsule garde son équipage, marque un temps d'arrêt, puis s'écarte du sas.
                capsule.undock();
                undocking[0] = true;
                helper.assertTrue(capsule.isDeparting() && capsule.locksCrew(), "le désamarrage aurait dû commencer, équipage à bord");
            }
            helper.assertTrue(capsule.position().distanceTo(dock) > 1.5, "la capsule ne s'écarte pas de la station");
            capsule.discard();
            for (long[] chunk : held) {
                helper.getLevel().setChunkForced((int) chunk[0], (int) chunk[1], false);
            }
        });
    }

    /**
     * La Lune : le lanceur lunaire de test peut l'atteindre avec son vaisseau, tient sur un pas de tir 7×7 et se
     * met en orbite ; un vaisseau lunaire en descente propulsée ralentit à l'approche du sol, se pose sans
     * parachutes et garde son équipage jusqu'au contact.
     */
    private static void moon(GameTestHelper helper) {
        RocketDesign design = RocketItem.design(CreativeRockets.lunarLauncher());
        RocketStats stats = RocketStats.of(design);
        FlightSimulation flight = FlightSimulation.of(stats);
        Orbitalis.LOGGER.info("Lanceur lunaire : {} t, {} m/s, {} blocs, vol {} s, orbite {} km, marge {} kg", (int) (stats.liftoffMass() / 1000),
                (int) stats.totalDeltaV(), RocketShape.of(design).height(), (int) flight.duration(), (int) (flight.altitude(1.0) / 1000),
                (int) flight.propellantLeft());
        helper.assertTrue(stats.isFlightReady() && stats.reach().ordinal() >= RocketStats.Reach.LUNAR.ordinal(),
                "le lanceur lunaire devrait atteindre la Lune : " + stats.reach() + ", " + stats.issues());
        helper.assertTrue(stats.liftoffMass() <= 1_000_000 && RocketShape.of(design).height() <= 35, "le lanceur lunaire ne tient pas sur un pas de tir 7×7");
        helper.assertTrue(flight.propellantLeft() > 0 && flight.stage(1.0) >= 2, "le lanceur lunaire devrait se mettre en orbite avec de la marge");
        helper.assertTrue(RocketParts.isCrewCabin(new ItemStack(ModItems.LUNAR_MODULE.get())) && !SpaceRules.isVacuum(helper.getLevel()),
                "le vaisseau lunaire est une cabine habitée, et le monde normal n'est pas le vide");

        // Descente propulsée, de vingt blocs de haut, au-dessus d'une dalle.
        helper.setBlock(new BlockPos(1, 1, 1), ModBlocks.MOON_ROCK.get());
        CapsuleEntity lander = new CapsuleEntity(ModEntities.CAPSULE.get(), helper.getLevel());
        BlockPos start = helper.absolutePos(new BlockPos(1, 22, 1));
        // Le test ne tient active que sa propre case : sans cela, le vaisseau peut se figer en pleine descente.
        helper.getLevel().setChunkForced(start.getX() >> 4, start.getZ() >> 4, true);
        lander.setPos(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        helper.getLevel().addFreshEntity(lander);
        lander.startLunarDescent();
        helper.assertTrue(lander.isLunar() && lander.locksCrew() && !lander.hasOpenParachutes(), "descente lunaire mal engagée");
        double[] lastStep = {1.0};
        double[] previous = {lander.getY()};
        helper.onEachTick(() -> {
            if (lander.getPhase() == CapsuleEntity.Phase.LUNAR_DESCENT) {
                lastStep[0] = previous[0] - lander.getY();
                previous[0] = lander.getY();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(lander.getPhase() == CapsuleEntity.Phase.LANDED && !lander.locksCrew(), "le vaisseau ne s'est pas posé");
            helper.assertTrue(Math.abs(lander.getY() - (helper.absolutePos(new BlockPos(1, 1, 1)).getY() + 1)) < 0.05, "le vaisseau n'est pas posé sur le sol");
            helper.assertTrue(lastStep[0] <= 0.07, "contact trop dur : " + lastStep[0] + " bloc par tick");
            helper.assertTrue(lander.asItem().is(ModItems.LUNAR_MODULE.get()), "le vaisseau récupéré devrait rester un vaisseau lunaire");
            lander.discard();
        });
    }

    /**
     * Mars : le lanceur d'essai atteint le transfert martien, et l'atterrissage se joue sur le moteur.
     * <ol>
     * <li>Sans pilote, l'ordinateur de bord pose le vaisseau en douceur.</li>
     * <li>Un pilote qui ne pousse jamais s'écrase : le vaisseau est détruit, et lui avec.</li>
     * <li>Un pilote qui pousse quand il descend trop vite pour sa hauteur se pose, vivant, avec des ergols en réserve.</li>
     * <li>Un pilote qui tient simplement la touche de poussée tout du long se pose aussi : le moteur se dose lui-même, et ne
     * fait jamais remonter le vaisseau.</li>
     * </ol>
     */
    private static void marsLanding(GameTestHelper helper) {
        RocketStats launcher = RocketStats.of(RocketItem.design(CreativeRockets.marsLauncher()));
        helper.assertTrue(launcher.reach() == RocketStats.Reach.MARS, "le lanceur martien devrait atteindre le transfert martien, et non "
                + launcher.reach() + " (" + (int) launcher.totalDeltaV() + " m/s)");
        helper.assertTrue(RocketParts.isCrewCabin(new ItemStack(ModItems.MARS_LANDER.get())), "le vaisseau martien devrait être une cabine habitée");
        for (int dx = 0; dx <= 4; dx++) {
            for (int dz = 0; dz <= 4; dz++) {
                helper.setBlock(new BlockPos(dx, 1, dz), ModBlocks.MARS_ROCK.get());
            }
        }
        BlockPos start = helper.absolutePos(new BlockPos(2, 82, 2));
        double floor = helper.absolutePos(new BlockPos(2, 1, 2)).getY() + 1;
        helper.getLevel().setChunkForced(start.getX() >> 4, start.getZ() >> 4, true);
        int[] run = {0};
        int[] step = {0};
        int[] wait = {0};
        double[] contact = {0.0};
        CapsuleEntity[] craft = new CapsuleEntity[1];
        net.minecraft.server.level.ServerPlayer[] pilot = new net.minecraft.server.level.ServerPlayer[1];
        helper.onEachTick(() -> {
            if (run[0] > 3) {
                return;
            }
            RocketBreakup.forced = RocketBreakup.Damage.NONE;
            if (step[0] == 0) {
                craft[0] = new CapsuleEntity(ModEntities.CAPSULE.get(), helper.getLevel());
                craft[0].setPos(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
                helper.getLevel().addFreshEntity(craft[0]);
                pilot[0] = null;
                if (run[0] > 0) {
                    pilot[0] = helper.makeMockServerPlayerInLevel();
                    pilot[0].setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                    helper.assertTrue(pilot[0].startRiding(craft[0], true, false), "descente " + run[0] + " : le pilote n'a pas pu monter à bord");
                    craft[0].setManualChute(true);
                }
                craft[0].startMarsDescent();
                helper.assertTrue(craft[0].getPhase() == CapsuleEntity.Phase.POWERED_DESCENT && craft[0].locksCrew(), "la descente propulsée n'a pas commencé");
                wait[0] = 0;
                step[0] = 1;
                return;
            }
            CapsuleEntity lander = craft[0];
            if (step[0] == 1) {
                if (lander.getPhase() == CapsuleEntity.Phase.POWERED_DESCENT && !lander.isRemoved()) {
                    double above = lander.getY() - floor;
                    double sink = -lander.descentVelocity().y;
                    contact[0] = sink;
                    if (run[0] == 3) {
                        // Le pilote tient la touche de poussée, sans jamais la lâcher.
                        lander.setControls(PilotInputPayload.STEP);
                        helper.assertTrue(lander.descentVelocity().y <= 0.02, "tenir la poussée ne doit jamais faire remonter le vaisseau");
                    }
                    if (run[0] == 2) {
                        // Le pilote pousse dès qu'il descend plus vite que ce que sa hauteur permet.
                        lander.setControls(sink > CapsuleEntity.safeDescentSpeed(above) ? PilotInputPayload.STEP : 0);
                    }
                    helper.assertTrue(++wait[0] < 900, "descente " + run[0] + " : le vaisseau n'a jamais touché le sol (hauteur " + (int) above + ")");
                    return;
                }
                if (run[0] == 1) {
                    helper.assertTrue(lander.isRemoved(), "sans poussée, le vaisseau aurait dû se briser au sol");
                    helper.assertTrue(!pilot[0].isAlive(), "sans poussée, le pilote n'aurait pas dû survivre à l'écrasement");
                    // L'écrasement a creusé son cratère (le test se joue sur la planète, non sur Mars) : on refait le sol.
                    for (int dx = -3; dx <= 7; dx++) {
                        for (int dz = -3; dz <= 7; dz++) {
                            for (int dy = -3; dy <= 4; dy++) {
                                helper.setBlock(new BlockPos(dx, 1 + dy, dz), dy == 0 && dx >= 0 && dx <= 4 && dz >= 0 && dz <= 4
                                        ? ModBlocks.MARS_ROCK.get().defaultBlockState()
                                        : dy < 0 ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()
                                        : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                            }
                        }
                    }
                } else {
                    helper.assertTrue(lander.getPhase() == CapsuleEntity.Phase.LANDED && !lander.isRemoved() && !lander.locksCrew(),
                            "descente " + run[0] + " : le vaisseau aurait dû se poser (vitesse au contact " + contact[0] + ")");
                    helper.assertTrue(contact[0] <= CapsuleEntity.SAFE_SINK + 0.02, "descente " + run[0] + " : contact trop dur, " + contact[0] + " bloc par tick");
                    helper.assertTrue(Math.abs(lander.getY() - floor) < 0.05, "le vaisseau n'est pas posé sur le sol");
                    helper.assertTrue(lander.asItem().is(ModItems.MARS_LANDER.get()), "le vaisseau récupéré devrait rester un vaisseau martien");
                    if (run[0] >= 2) {
                        helper.assertTrue(pilot[0].isAlive() && pilot[0].getVehicle() == lander, "le pilote devrait être posé, sauf, dans son vaisseau");
                        helper.assertTrue(lander.fuelShare() > 0.15F, "un atterrissage propre devrait laisser des ergols : " + lander.fuelShare());
                    }
                    lander.discard();
                }
                if (pilot[0] != null) {
                    pilot[0].discard();
                }
                wait[0] = 0;
                step[0] = 2;
                return;
            }
            if (++wait[0] > 60) {
                run[0]++;
                step[0] = 0;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(run[0] > 3, "descentes en cours : " + run[0]);
            RocketBreakup.forced = null;
        });
    }

    /**
     * Titan : le lanceur d'essai y arrive, avec un étage nucléaire ; le vaisseau titanien est une cabine Mk3. Arrivé
     * au-dessus du sol, il fait sa rentrée : l'ordinateur de bord arme les parachutes, ils s'ouvrent, et il se pose.
     */
    private static void titanLanding(GameTestHelper helper) {
        RocketStats launcher = RocketStats.of(RocketItem.design(CreativeRockets.titanLauncher()));
        helper.assertTrue(launcher.isFlightReady() && launcher.reach().ordinal() >= RocketStats.Reach.TITAN.ordinal(),
                "le lanceur titanien devrait atteindre Titan, et non " + launcher.reach() + " (" + (int) launcher.totalDeltaV() + " m/s, "
                        + launcher.issues() + ")");
        RocketDesign titan = RocketItem.design(CreativeRockets.titanLauncher());
        RocketDesign.Stage wrong = new RocketDesign.Stage(new ItemStack(ModItems.HYDROLOX_ENGINE.get()),
                new ItemStack(ModItems.HYDROGEN_TANK.get(), 24), new ItemStack(ModItems.INTERSTAGE.get()));
        helper.assertTrue(RocketStats.of(new RocketDesign(List.of(titan.stages().get(0), titan.stages().get(1), wrong), titan.payload(), titan.nose()))
                .issues().stream().anyMatch(issue -> issue.type() == RocketStats.IssueType.HYDROGEN_TANK),
                "des réservoirs d'hydrogène sous un moteur cryogénique doivent être refusés");
        ItemStack payload = new ItemStack(ModItems.TITAN_LANDER.get());
        helper.assertTrue(RocketParts.isCrewCabin(payload) && RocketParts.cabinVariant(payload) == 2,
                "le vaisseau titanien devrait être une cabine habitée Mk3");
        // Sous ses parachutes, le vaisseau dérive avec le vent : un sol large, et ses tronçons gardés en vie.
        for (int dx = -14; dx <= 18; dx++) {
            for (int dz = -14; dz <= 18; dz++) {
                helper.setBlock(new BlockPos(dx, 1, dz), ModBlocks.THOLIN_SAND.get());
            }
        }
        BlockPos start = helper.absolutePos(new BlockPos(2, 70, 2));
        keepChunksLoaded(helper, start, 2);
        double floor = helper.absolutePos(new BlockPos(2, 1, 2)).getY() + 1;
        CapsuleEntity lander = new CapsuleEntity(ModEntities.CAPSULE.get(), helper.getLevel());
        lander.setPos(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        helper.getLevel().addFreshEntity(lander);
        lander.startTitanEntry();
        helper.assertTrue(lander.getPhase() == CapsuleEntity.Phase.REENTRY && lander.isTitanian(), "la rentrée vers Titan n'a pas commencé");
        boolean[] chutes = {false};
        helper.onEachTick(() -> chutes[0] |= lander.hasOpenParachutes());
        helper.succeedWhen(() -> {
            helper.assertTrue(lander.getPhase() == CapsuleEntity.Phase.LANDED, "le vaisseau descend encore (" + lander.getPhase() + ")");
            helper.assertTrue(!lander.isRemoved() && chutes[0], "le vaisseau aurait dû se poser sous parachutes, sans se briser");
            // Les parachutes le font dériver : il se pose où le vent l'a mené, mais sur un sol solide.
            helper.assertTrue(lander.onGround() && lander.getY() <= floor + 0.05, "le vaisseau n'est pas posé sur le sol (y " + lander.getY() + ")");
            helper.assertTrue(lander.asItem().is(ModItems.TITAN_LANDER.get()), "le vaisseau récupéré devrait rester un vaisseau titanien");
            lander.discard();
        });
    }

    /**
     * Europe : le lanceur d'essai l'atteint avec son vaisseau, une cabine Mk3 ; la lune est sans air, à 13 % de la
     * pesanteur ; un cryobot posé sur sa glace descend jusqu'à l'océan et y découvre la vie, pour l'agence qui l'a posé.
     */
    private static void europa(GameTestHelper helper) {
        RocketStats launcher = RocketStats.of(RocketItem.design(CreativeRockets.europaLauncher()));
        helper.assertTrue(launcher.isFlightReady() && launcher.reach().ordinal() >= RocketStats.Reach.EUROPA.ordinal(),
                "le lanceur d'Europe devrait atteindre Europe, et non " + launcher.reach() + " (" + (int) launcher.totalDeltaV() + " m/s)");
        ItemStack lander = new ItemStack(ModItems.EUROPA_LANDER.get());
        helper.assertTrue(RocketParts.isCrewCabin(lander) && RocketParts.cabinVariant(lander) == 2, "le vaisseau d'Europe devrait être une cabine Mk3");
        net.minecraft.server.level.ServerLevel europa = helper.getLevel().getServer().getLevel(com.orbitalis.phase2.survival.SpaceRules.EUROPA);
        // Le serveur des essais ne charge que les mondes du jeu : la suite ne se vérifie qu'en jeu, s'il manque Europe.
        if (europa == null) {
            helper.succeed();
            return;
        }
        helper.assertTrue(com.orbitalis.phase2.survival.SpaceRules.isVacuum(europa)
                && Math.abs(com.orbitalis.phase2.survival.SpaceRules.gravityOf(europa) - 0.134) < 1.0E-6, "Europe devrait être sans air, à 13 % de la pesanteur");
        // Un cryobot, sur la glace d'Europe, loin de tout, presque au bout de sa descente.
        BlockPos spot = new BlockPos(9000, 90, 9000);
        europa.getChunk(spot.getX() >> 4, spot.getZ() >> 4);
        europa.setBlock(spot, ModBlocks.CRYOBOT.get().defaultBlockState(), 3);
        net.minecraft.server.level.ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        com.orbitalis.core.research.AgencyResearch research = com.orbitalis.core.research.ResearchManager.of(owner);
        int before = research.points();
        if (europa.getBlockEntity(spot) instanceof com.orbitalis.phase3.exploration.CryobotBlockEntity cryobot) {
            ModBlocks.CRYOBOT.get().setPlacedBy(europa, spot, europa.getBlockState(spot), owner, ItemStack.EMPTY);
            cryobot.advanceForTest(com.orbitalis.phase3.exploration.CryobotBlockEntity.DRILL_TICKS);
            cryobot.serverTick(europa, spot);
            helper.assertTrue(cryobot.isDone(), "le cryobot aurait dû atteindre l'océan");
        } else {
            helper.fail("le cryobot n'a pas d'entité de bloc");
        }
        helper.assertTrue(research.points() - before >= com.orbitalis.phase3.exploration.CryobotBlockEntity.REPEAT_POINTS,
                "l'agence du cryobot aurait dû gagner des points");
        europa.removeBlock(spot, false);
        owner.discard();
        helper.succeed();
    }

    /**
     * Le drone volant : posé, une sortie l'emmène sur un site à sa portée, où il se pose et étudie ; puis il revient se
     * poser là où il est parti, et sa batterie se recharge. Pas d'air (en orbite), pas de décollage.
     */
    private static void droneSortie(GameTestHelper helper) {
        for (int dx = -2; dx <= 6; dx++) {
            for (int dz = -2; dz <= 6; dz++) {
                helper.setBlock(new BlockPos(dx, 1, dz), net.minecraft.world.level.block.Blocks.STONE);
            }
        }
        BlockPos home = helper.absolutePos(new BlockPos(2, 2, 2));
        // Le site peut être à une cinquantaine de blocs : ses tronçons doivent vivre, sinon le drone s'y fige.
        keepChunksLoaded(helper, home, 4);
        com.orbitalis.phase3.exploration.RotorDroneEntity drone = ModEntities.ROTOR_DRONE.get().create(helper.getLevel(),
                net.minecraft.world.entity.EntitySpawnReason.SPAWN_ITEM_USE);
        drone.snapTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(drone);
        net.minecraft.server.level.ServerPlayer pilot = helper.makeMockServerPlayerInLevel();
        drone.launch(helper.getLevel(), pilot);
        helper.assertTrue(drone.isFlying(), "le drone aurait dû décoller");
        helper.assertTrue(com.orbitalis.phase3.exploration.RotorDroneEntity.range(helper.getLevel()) > 0,
                "la planète a de l'air : le drone doit pouvoir y voler");
        boolean[] surveyed = {false};
        double[] farthest = {0.0};
        helper.onEachTick(() -> {
            surveyed[0] |= drone.state() == com.orbitalis.phase3.exploration.RotorDroneEntity.State.SURVEY;
            farthest[0] = Math.max(farthest[0], Math.hypot(drone.getX() - home.getX() - 0.5, drone.getZ() - home.getZ() - 0.5));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(drone.state() == com.orbitalis.phase3.exploration.RotorDroneEntity.State.LANDED && surveyed[0],
                    "le drone est encore en sortie (" + drone.state() + ")");
            helper.assertTrue(farthest[0] >= 10.0, "le drone ne s'est pas éloigné : " + farthest[0] + " blocs");
            helper.assertTrue(Math.hypot(drone.getX() - home.getX() - 0.5, drone.getZ() - home.getZ() - 0.5) < 0.1
                    && Math.abs(drone.getY() - home.getY()) < 0.6, "le drone n'est pas revenu se poser chez lui");
            pilot.discard();
            drone.discard();
        });
    }

    /** Garde chargés (et vivants) les tronçons autour de ce point, sur ce rayon en tronçons. */
    private static void keepChunksLoaded(GameTestHelper helper, BlockPos center, int radius) {
        for (int cx = (center.getX() >> 4) - radius; cx <= (center.getX() >> 4) + radius; cx++) {
            for (int cz = (center.getZ() >> 4) - radius; cz <= (center.getZ() >> 4) + radius; cz++) {
                helper.getLevel().setChunkForced(cx, cz, true);
            }
        }
    }

    /** Capsule qui monte avec la fusée partie de cette table. */
    private static CapsuleEntity flyingCapsule(GameTestHelper helper, BlockPos table) {
        for (CapsuleEntity capsule : helper.getLevel().getEntitiesOfClass(CapsuleEntity.class,
                new net.minecraft.world.phys.AABB(helper.absolutePos(table)).inflate(8.0, 64.0, 8.0))) {
            if (capsule.getPhase() == CapsuleEntity.Phase.ASCENT) {
                return capsule;
            }
        }
        return null;
    }

    /**
     * Vols avec un pilote à bord, du décollage au retour, tels qu'un joueur les vit : ses commandes passent par la
     * cabine, comme celles d'un vrai client.
     * <ol>
     * <li>Un vol orbital piloté : l'étage est largué, la capsule arrive en orbite à la verticale de son pas de tir
     * (c'est là qu'est la station de l'agence), le pilote commande le retour, arme ses parachutes et se pose.</li>
     * <li>Une fusée mise en vrille : elle se disloque, la tour de sauvetage emporte la cabine, le pilote arme ses
     * parachutes et se pose.</li>
     * <li>La même, sans armer les parachutes : la cabine s'écrase.</li>
     * </ol>
     */
    private static void pilotedFlight(GameTestHelper helper) {
        BlockPos tablePos = new BlockPos(2, 2, 2);
        helper.setBlock(tablePos, ModBlocks.LAUNCH_TABLE.get());
        LaunchTableBlockEntity table = helper.getBlockEntity(tablePos, LaunchTableBlockEntity.class);
        BlockPos center = helper.absolutePos(tablePos);
        net.minecraft.server.level.ServerLevel orbit = helper.getLevel().getServer().getLevel(SpaceRules.ORBIT);
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                helper.getLevel().setChunkForced((center.getX() >> 4) + dx, (center.getZ() >> 4) + dz, true);
                if (orbit != null) {
                    orbit.setChunkForced((center.getX() >> 4) + dx, (center.getZ() >> 4) + dz, true);
                }
            }
        }
        String agency = "test:piloted";
        // Avancement : numéro du vol (0 à 2) et étape dans ce vol.
        int[] flight = {0};
        int[] step = {0};
        int[] wait = {0};
        int[] dropped = {0};
        CapsuleEntity[] capsule = new CapsuleEntity[1];
        net.minecraft.server.level.ServerPlayer[] pilot = new net.minecraft.server.level.ServerPlayer[1];
        helper.onEachTick(() -> {
            if (flight[0] > 2) {
                return;
            }
            RocketBreakup.forced = RocketBreakup.Damage.NONE;
            // Un joueur simulé ne charge pas le monde autour de lui : la fusée reste dans la zone que le test retient.
            com.orbitalis.phase1.spaceport.RocketMotion.testLimit = 24.0;
            if (step[0] == 0) {
                // Décollage, et pilote à bord.
                helper.assertTrue(table.launchForTest(helper.getLevel(), CreativeRockets.crewedOrbital(), agency), "vol " + flight[0] + " : pas de décollage");
                capsule[0] = flyingCapsule(helper, tablePos);
                helper.assertTrue(capsule[0] != null, "vol " + flight[0] + " : la capsule ne suit pas la fusée");
                pilot[0] = helper.makeMockServerPlayerInLevel();
                pilot[0].setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                helper.assertTrue(pilot[0].startRiding(capsule[0], true, false), "vol " + flight[0] + " : le pilote n'a pas pu monter à bord");
                // Sa cabine lui laisse les parachutes : le niveau d'aide est manuel.
                capsule[0].setManualChute(true);
                dropped[0] = 0;
                step[0] = 1;
                return;
            }
            CapsuleEntity craft = capsule[0];
            CapsuleEntity.Phase phase = craft.getPhase();
            if (step[0] == 1) {
                // La montée.
                if (table.getPhase() == LaunchTableBlockEntity.Phase.FLIGHT) {
                    int bits = 0;
                    if (flight[0] == 0) {
                        double gap = table.getPitch() - table.targetPitch();
                        bits = gap > 5.0 ? PilotInputPayload.PITCH_DOWN : 0;
                        if (table.flightOpen() != 0 && helper.getTick() % 2 == 0) {
                            bits |= PilotInputPayload.STEP;
                        }
                        // Les QTE de stabilisation : tenir la touche demandée, ou la presser à petits coups.
                        if (table.qteState() == LaunchTableBlockEntity.QTE_ACTIVE) {
                            boolean hold = (table.qteHud() >> 4 & 1) != 0;
                            bits |= hold || helper.getTick() % 4 < 2 ? table.qteKeyBit() : 0;
                        }
                    } else {
                        // Aucune décision : les gaz ne sont pas réduits au passage de la pression maximale. La commande de
                        // tangage tenue, elle, ne change plus rien : la fusée tient sa trajectoire d'elle-même.
                        bits = PilotInputPayload.PITCH_DOWN;
                    }
                    craft.setControls(bits);
                    dropped[0] = Math.max(dropped[0], craft.droppedStages());
                    return;
                }
                craft.setControls(0);
                if (flight[0] == 0) {
                    helper.assertTrue(table.getLastResult() == LaunchTableBlockEntity.Result.SUCCESS, "le vol orbital piloté aurait dû réussir : " + table.getLastResult());
                    helper.assertTrue(dropped[0] == 1, "la cabine aurait dû montrer un étage largué, et non " + dropped[0]);
                } else {
                    helper.assertTrue(table.getLastResult() == LaunchTableBlockEntity.Result.FAILURE,
                            "sans les décisions du vol, la fusée aurait dû se perdre au passage de la pression maximale : " + table.getLastResult());
                }
                wait[0] = 0;
                step[0] = 2;
                return;
            }
            if (step[0] == 2) {
                if (flight[0] == 0) {
                    // En orbite, à la verticale du pas de tir ; puis le pilote commande le retour. Le serveur de test
                    // n'a pas la dimension d'orbite : la capsule y fait alors ses tours sur place, et rentre d'elle-même.
                    if (orbit == null) {
                        helper.assertTrue(phase == CapsuleEntity.Phase.COAST || phase == CapsuleEntity.Phase.REENTRY,
                                "faute de dimension d'orbite, la capsule aurait dû rester en vol libre : " + phase);
                        helper.assertTrue(craft.getFirstPassenger() == pilot[0], "le pilote devrait être resté à bord à la fin de la poussée");
                        step[0] = 3;
                        wait[0] = 0;
                        return;
                    }
                    if (phase != CapsuleEntity.Phase.ORBIT) {
                        helper.assertTrue(++wait[0] < 40, "la capsule n'est pas arrivée en orbite : " + phase);
                        return;
                    }
                    helper.assertTrue(craft.level().dimension() == SpaceRules.ORBIT, "la capsule en orbite devrait être dans l'espace");
                    helper.assertTrue(Math.abs(craft.getX() - (center.getX() + 0.5)) < 1.0 && Math.abs(craft.getZ() - (center.getZ() + 0.5)) < 1.0,
                            "la capsule devrait arriver en orbite à la verticale de son pas de tir, et non en " + craft.position());
                    helper.assertTrue(craft.getFirstPassenger() == pilot[0], "le pilote devrait être resté à bord en passant en orbite");
                    craft.setControls(PilotInputPayload.RETURN);
                    step[0] = 3;
                } else {
                    // Éjection : la tour de sauvetage emporte la cabine.
                    helper.assertTrue(phase == CapsuleEntity.Phase.ABORT || phase == CapsuleEntity.Phase.REENTRY, "la cabine aurait dû être éjectée : " + phase);
                    helper.assertTrue(!craft.isChuteArmed(), "après l'éjection, les parachutes ne doivent pas s'armer tout seuls");
                    craft.setControls(flight[0] == 1 ? PilotInputPayload.CHUTE : 0);
                    step[0] = 3;
                }
                wait[0] = 0;
                return;
            }
            if (step[0] == 3) {
                // Le retour.
                if (flight[0] == 0) {
                    craft.setControls(phase == CapsuleEntity.Phase.ORBIT ? PilotInputPayload.RETURN : PilotInputPayload.CHUTE);
                } else {
                    craft.setControls(flight[0] == 1 ? PilotInputPayload.CHUTE : 0);
                }
                boolean down = flight[0] == 2 ? craft.isRemoved() : phase == CapsuleEntity.Phase.LANDED;
                if (!down) {
                    helper.assertTrue(++wait[0] < 2400, "vol " + flight[0] + " : la cabine n'est jamais revenue au sol (" + phase + ", y=" + (int) craft.getY() + ")");
                    return;
                }
                if (flight[0] == 2) {
                    helper.assertTrue(!pilot[0].isAlive() || pilot[0].getVehicle() == null, "la cabine écrasée n'aurait pas dû garder son pilote");
                    helper.assertTrue(!pilot[0].isAlive(), "sans parachutes, le pilote n'aurait pas dû survivre à l'écrasement");
                } else {
                    helper.assertTrue(craft.level().dimension() == helper.getLevel().dimension(), "la cabine devrait être revenue sur la planète");
                    helper.assertTrue(pilot[0].isAlive() && pilot[0].getVehicle() == craft, "vol " + flight[0] + " : le pilote devrait être posé, sauf, dans sa cabine");
                    craft.discard();
                }
                pilot[0].discard();
                wait[0] = 0;
                step[0] = 4;
                return;
            }
            // Le temps que l'accident se termine au sol, puis vol suivant.
            if (++wait[0] > 300) {
                flight[0]++;
                step[0] = 0;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(flight[0] > 2, "vols pilotés en cours : vol " + flight[0] + ", étape " + step[0]);
            RocketBreakup.forced = null;
            com.orbitalis.phase1.spaceport.RocketMotion.testLimit = 0.0;
            helper.setBlock(tablePos, net.minecraft.world.level.block.Blocks.AIR);
        });
    }

    /**
     * Statistiques des fusées : le delta-v suit l'équation de Tsiolkovski, une fusée-sonde atteint l'espace,
     * un lanceur à deux étages atteint l'orbite basse, et un plan incomplet est refusé.
     */
    private static void rocketStats(GameTestHelper helper) {
        ItemStack nose = new ItemStack(ModItems.NOSE_CONE.get());
        ItemStack payload = new ItemStack(ModItems.SOUNDING_PAYLOAD.get());
        RocketDesign.Stage empty = new RocketDesign.Stage(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);

        // Fusée-sonde : un moteur à poudre (150 kg + 900 kg de propergol), une charge utile (50 kg) et une coiffe (30 kg).
        RocketStats sounding = RocketStats.of(new RocketDesign(List.of(
                new RocketDesign.Stage(new ItemStack(ModItems.SOLID_MOTOR.get()), ItemStack.EMPTY, ItemStack.EMPTY), empty, empty),
                payload, nose));
        double expected = 240.0 * RocketStats.G0 * Math.log(1130.0 / 230.0);
        helper.assertTrue(sounding.isFlightReady(), "la fusée-sonde devrait pouvoir voler");
        helper.assertTrue(Math.abs(sounding.totalDeltaV() - expected) < 0.5, "delta-v de la fusée-sonde incorrect : " + sounding.totalDeltaV());
        helper.assertTrue(Math.abs(sounding.liftoffMass() - 1130.0) < 0.01, "masse de la fusée-sonde incorrecte");
        helper.assertTrue(sounding.reach() == RocketStats.Reach.SUBORBITAL, "la fusée-sonde devrait atteindre l'espace");

        // Lanceur à deux étages : 2 moteurs et 24 réservoirs en bas, 1 moteur et 4 réservoirs en haut.
        RocketDesign.Stage first = new RocketDesign.Stage(new ItemStack(ModItems.KEROLOX_ENGINE.get(), 2),
                new ItemStack(ModItems.ALUMINUM_TANK.get(), 24), ItemStack.EMPTY);
        RocketDesign.Stage second = new RocketDesign.Stage(new ItemStack(ModItems.KEROLOX_ENGINE.get()),
                new ItemStack(ModItems.ALUMINUM_TANK.get(), 4), new ItemStack(ModItems.INTERSTAGE.get()));
        RocketStats launcher = RocketStats.of(new RocketDesign(List.of(first, second, empty), payload, nose));
        helper.assertTrue(launcher.isFlightReady(), "le lanceur à deux étages devrait pouvoir voler");
        helper.assertTrue(launcher.stages().get(0).twr() > 1.0, "le lanceur devrait décoller");
        helper.assertTrue(launcher.reach() == RocketStats.Reach.LOW_ORBIT, "le lanceur devrait atteindre l'orbite basse : "
                + launcher.totalDeltaV());

        // Le même sans interétage : le plan est refusé.
        RocketDesign.Stage loose = new RocketDesign.Stage(second.engines(), second.tanks(), ItemStack.EMPTY);
        RocketStats broken = RocketStats.of(new RocketDesign(List.of(first, loose, empty), payload, nose));
        helper.assertTrue(!broken.isFlightReady() && broken.reach() == RocketStats.Reach.NONE
                && broken.issues().get(0).type() == RocketStats.IssueType.MISSING_INTERSTAGE,
                "un étage supérieur sans interétage doit être refusé");

        // À volume égal, un réservoir contient bien moins d'hydrogène que de RP-1.
        RocketStats hydrogen = RocketStats.of(new RocketDesign(List.of(
                new RocketDesign.Stage(new ItemStack(ModItems.HYDROLOX_ENGINE.get()), new ItemStack(ModItems.ALUMINUM_TANK.get()),
                        ItemStack.EMPTY), empty, empty), payload, nose));
        helper.assertTrue(Math.abs(hydrogen.stages().get(0).propellantMass() - 1440.0) < 0.01, "masse d'ergols hydrogène incorrecte");

        // Un propulseur ionique au sommet du lanceur : ses 450 kg de xénon, à 3 100 s, s'ajoutent une fois en orbite.
        RocketDesign.Stage ion = new RocketDesign.Stage(new ItemStack(ModItems.ION_THRUSTER.get()), ItemStack.EMPTY,
                new ItemStack(ModItems.INTERSTAGE.get()));
        RocketStats ionLauncher = RocketStats.of(new RocketDesign(List.of(first, second, ion), payload, nose));
        double ionDeltaV = 3100.0 * RocketStats.G0 * Math.log(770.0 / 320.0);
        helper.assertTrue(ionLauncher.isFlightReady() && Math.abs(ionLauncher.stages().get(2).deltaV() - ionDeltaV) < 0.5
                && ionLauncher.reach() == RocketStats.Reach.MERCURY && RocketFuel.required(ionLauncher).total() == RocketFuel.required(launcher).total(),
                "l'étage ionique devrait ajouter son delta-v sans demander de plein : " + ionLauncher.totalDeltaV());
        // Sur une fusée-sonde, qui n'atteint pas l'orbite, il ne sert à rien.
        RocketStats ionSounding = RocketStats.of(new RocketDesign(List.of(
                new RocketDesign.Stage(new ItemStack(ModItems.SOLID_MOTOR.get()), ItemStack.EMPTY, ItemStack.EMPTY), ion, empty), payload, nose));
        helper.assertTrue(ionSounding.isFlightReady() && Math.abs(ionSounding.totalDeltaV() - ionSounding.stages().get(0).deltaV()) < 0.01,
                "sans orbite, l'étage ionique ne doit rien ajouter");
        // Il ne décolle pas, et n'emporte pas d'équipage.
        RocketStats ionFirst = RocketStats.of(new RocketDesign(List.of(
                new RocketDesign.Stage(new ItemStack(ModItems.ION_THRUSTER.get()), ItemStack.EMPTY, ItemStack.EMPTY), empty, empty), payload, nose));
        helper.assertTrue(ionFirst.issues().stream().anyMatch(issue -> issue.type() == RocketStats.IssueType.LOW_THRUST),
                "un premier étage ionique ne doit pas décoller");
        RocketStats ionCrew = RocketStats.of(new RocketDesign(List.of(first, second, ion), new ItemStack(ModItems.CREW_CAPSULE.get()), ItemStack.EMPTY));
        helper.assertTrue(ionCrew.issues().stream().anyMatch(issue -> issue.type() == RocketStats.IssueType.CREWED_LOW_THRUST),
                "un étage ionique ne doit pas emporter d'équipage");
        // La voile : une impulsion fixe, divisée par la masse qu'elle pousse (interétage, voile, charge et coiffe).
        RocketStats sail = RocketStats.of(new RocketDesign(List.of(first, second, new RocketDesign.Stage(new ItemStack(ModItems.SOLAR_SAIL.get()),
                ItemStack.EMPTY, new ItemStack(ModItems.INTERSTAGE.get()))), payload, nose));
        helper.assertTrue(sail.isFlightReady() && Math.abs(sail.stages().get(2).deltaV() - RocketStats.SAIL_IMPULSE / 240.0) < 0.5,
                "delta-v de la voile incorrect : " + sail.stages().get(2).deltaV());
        // Une paire de boosters à poudre : plus de poussée au décollage et plus de delta-v, le même plein pour le
        // premier étage, deux phases pour lui mais une seule séparation dans la séquence ; le plan pose leurs blocs.
        RocketDesign boosted = new RocketDesign(List.of(first, second, empty), payload, nose, new ItemStack(ModItems.SOLID_BOOSTER.get(), 2));
        RocketStats withBoosters = RocketStats.of(boosted);
        long separations = com.orbitalis.phase1.spaceport.FlightSimulation.of(withBoosters).events().stream()
                .filter(event -> event.type() == com.orbitalis.phase1.spaceport.FlightSimulation.EventType.STAGING).count();
        java.util.Map<net.minecraft.world.item.Item, Integer> boosterCost = RocketPlan.materials(boosted);
        helper.assertTrue(withBoosters.isFlightReady() && withBoosters.stages().size() == 3 && withBoosters.liftoffThrust() > launcher.liftoffThrust()
                && withBoosters.totalDeltaV() > launcher.totalDeltaV() && RocketFuel.required(withBoosters).total() == RocketFuel.required(launcher).total()
                && separations == 1 && boosterCost.getOrDefault(ModItems.SOLID_BOOSTER.get(), 0) == 2 && boosterCost.getOrDefault(ModItems.NOSE_CONE.get(), 0) == 3,
                "boosters latéraux incorrects : " + (int) withBoosters.totalDeltaV() + " m/s, " + separations + " séparation(s), " + boosterCost);
        // Les boosters, étroits comme larges, se dressent sur le tablier sans heurter aucune pièce du pas de tir (les
        // rampes de déluge s'interrompent au droit de la fusée), quels que soient sa taille et son orientation.
        RocketDesign titanDesign = RocketItem.design(com.orbitalis.core.creative.CreativeRockets.titanLauncher());
        RocketDesign.Stage small = new RocketDesign.Stage(new ItemStack(ModItems.KEROLOX_ENGINE.get()), new ItemStack(ModItems.ALUMINUM_TANK.get(), 2), ItemStack.EMPTY);
        for (RocketDesign onPad : List.of(new RocketDesign(List.of(small, empty, empty), payload, nose, new ItemStack(ModItems.SOLID_BOOSTER.get(), 2)),
                new RocketDesign(titanDesign.stages(), titanDesign.payload(), titanDesign.nose(), new ItemStack(ModItems.SOLID_BOOSTER.get(), 2)))) {
            com.orbitalis.phase1.spaceport.RocketShape boostedShape = com.orbitalis.phase1.spaceport.RocketShape.of(onPad);
            helper.assertTrue(boostedShape.isWide() == (onPad.stages() == titanDesign.stages()), "le lanceur titanien devrait être large, l'autre étroit");
            for (int padRadius = 2; padRadius <= com.orbitalis.phase1.spaceport.LaunchComplex.MAX_RADIUS; padRadius++) {
                for (net.minecraft.core.Direction facing : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    com.orbitalis.phase1.spaceport.PadPlan pad = new com.orbitalis.phase1.spaceport.PadPlan(padRadius, false, facing);
                    java.util.Set<BlockPos> taken = new java.util.HashSet<>();
                    pad.pieces().forEach(piece -> taken.add(piece.offset()));
                    for (BlockPos cell : boostedShape.boosterOffsets(facing)) {
                        helper.assertTrue(!taken.contains(cell), "un booster (fusée " + (boostedShape.isWide() ? "large" : "étroite") + ") heurte le pas de tir "
                                + (2 * padRadius + 1) + "×" + (2 * padRadius + 1) + " en " + cell);
                    }
                }
            }
        }
        // Le méthalox : du méthane et de l'oxygène liquides, 3,6 kg d'oxygène par kilo de méthane, et pas de kérosène.
        RocketStats methalox = RocketStats.of(new RocketDesign(List.of(new RocketDesign.Stage(new ItemStack(ModItems.METHALOX_ENGINE.get(), 2),
                new ItemStack(ModItems.STAINLESS_TANK.get(), 8), ItemStack.EMPTY), empty, empty), payload, nose));
        RocketFuel methaloxFuel = RocketFuel.required(methalox);
        double ratio = methaloxFuel.oxygen() / (double) Math.max(1, methaloxFuel.methane());
        helper.assertTrue(methalox.isFlightReady() && methaloxFuel.methane() > 0 && methaloxFuel.rp1() == 0 && methaloxFuel.hydrogen() == 0
                && Math.abs(ratio - 3.6) < 0.05, "plein méthalox incorrect : " + methaloxFuel + " (rapport " + ratio + ")");
        // La fusion : le lanceur d'essai va au-delà de Mercure ; il charge de l'hydrogène et un vingtième d'hélium-3, pas
        // de méthane ni de kérosène pour son dernier étage ; et un moteur à fusion n'allume jamais le premier étage.
        RocketStats fusion = RocketStats.of(RocketItem.design(com.orbitalis.core.creative.CreativeRockets.fusionLauncher()));
        RocketFuel fusionFuel = RocketFuel.required(fusion);
        helper.assertTrue(fusion.isFlightReady() && fusion.reach() == RocketStats.Reach.MERCURY && fusionFuel.helium3() > 0
                && Math.abs(fusionFuel.helium3() - fusion.stages().get(2).propellantMass() / RocketFuel.KG_PER_MB * RocketFuel.HELIUM3_PER_HYDROGEN) < 2.0,
                "lanceur à fusion incorrect : " + fusion.reach() + " (" + (int) fusion.totalDeltaV() + " m/s), " + fusionFuel);
        RocketDesign fusionDesign = RocketItem.design(com.orbitalis.core.creative.CreativeRockets.fusionLauncher());
        helper.assertTrue(RocketStats.of(new RocketDesign(List.of(fusionDesign.stages().get(2), empty, empty), payload, nose)).issues().stream()
                .anyMatch(issue -> issue.type() == RocketStats.IssueType.NUCLEAR_LIFTOFF), "un moteur à fusion au premier étage doit être refusé");
        // Le moteur nucléaire thermique : jamais au premier étage ; au second, de l'hydrogène seul, à 900 s.
        RocketDesign.Stage nuclear = new RocketDesign.Stage(new ItemStack(ModItems.NUCLEAR_THERMAL_ENGINE.get()),
                new ItemStack(ModItems.COMPOSITE_TANK.get(), 4), new ItemStack(ModItems.INTERSTAGE.get()));
        RocketStats nuclearFirst = RocketStats.of(new RocketDesign(List.of(new RocketDesign.Stage(nuclear.engines(), nuclear.tanks(), ItemStack.EMPTY),
                empty, empty), payload, nose));
        helper.assertTrue(nuclearFirst.issues().stream().anyMatch(issue -> issue.type() == RocketStats.IssueType.NUCLEAR_LIFTOFF),
                "un moteur nucléaire au premier étage doit être refusé");
        RocketStats nuclearUpper = RocketStats.of(new RocketDesign(List.of(first, nuclear, empty), payload, nose));
        RocketStats.StageStats upper = nuclearUpper.stages().get(1);
        double hydrogenMass = 4 * 16.0 * 71.0;
        double nuclearDeltaV = 900.0 * RocketStats.G0 * Math.log((upper.dryMass() + 80.0 + hydrogenMass) / (upper.dryMass() + 80.0));
        RocketFuel nuclearFuel = RocketFuel.required(nuclearUpper);
        RocketFuel firstOnly = RocketFuel.required(RocketStats.of(new RocketDesign(List.of(first, empty, empty), payload, nose)));
        helper.assertTrue(nuclearUpper.issues().isEmpty() && Math.abs(upper.propellantMass() - hydrogenMass) < 0.01
                && Math.abs(upper.deltaV() - nuclearDeltaV) < 0.5 && nuclearFuel.hydrogen() > 0 && nuclearFuel.oxygen() == firstOnly.oxygen(),
                "étage nucléaire incorrect : " + (int) upper.deltaV() + " m/s, " + nuclearFuel);
        // Un seul booster, ce n'est pas une paire.
        helper.assertTrue(RocketStats.of(new RocketDesign(List.of(first, second, empty), payload, nose, new ItemStack(ModItems.SOLID_BOOSTER.get(), 1)))
                .issues().stream().anyMatch(issue -> issue.type() == RocketStats.IssueType.BAD_BOOSTERS), "un booster seul doit être refusé");
        helper.succeed();
    }

    /**
     * Règles de la recherche : une technologie demande ses prérequis et assez de points, les points sont dépensés,
     * et l'état survit à un enregistrement suivi d'une relecture.
     */
    private static void researchRules(GameTestHelper helper) {
        AgencyResearch research = new AgencyResearch();
        helper.assertTrue(research.isUnlocked("steelmaking"), "la sidérurgie doit être acquise dès le départ");
        helper.assertTrue(research.canUnlock("aluminum_structures") == AgencyResearch.UnlockResult.NOT_ENOUGH_POINTS,
                "sans points, la recherche doit être refusée");

        research.addPoints(1000);
        helper.assertTrue(research.unlock("aluminum_lithium") == AgencyResearch.UnlockResult.MISSING_PREREQUISITE,
                "une technologie ne doit pas se débloquer sans son prérequis");
        helper.assertTrue(research.unlock("methalox_engines") == AgencyResearch.UnlockResult.MISSING_PREREQUISITE,
                "un prérequis d'un autre arbre doit compter aussi");
        helper.assertTrue(research.unlock("nonexistent") == AgencyResearch.UnlockResult.UNKNOWN_NODE,
                "une technologie inconnue doit être refusée");

        int cost = ResearchNodes.get("aluminum_structures").cost();
        helper.assertTrue(research.unlock("aluminum_structures") == AgencyResearch.UnlockResult.SUCCESS,
                "la recherche aurait dû réussir");
        helper.assertTrue(research.points() == 1000 - cost, "les points n'ont pas été dépensés");
        helper.assertTrue(research.unlock("aluminum_structures") == AgencyResearch.UnlockResult.ALREADY_UNLOCKED,
                "une technologie acquise ne se recherche pas deux fois");
        helper.assertTrue(research.unlock("aluminum_lithium") == AgencyResearch.UnlockResult.SUCCESS,
                "le prérequis acquis, la technologie suivante doit se débloquer");

        // Enregistrement puis relecture, comme lors d'une sauvegarde du monde.
        AgencyResearch reloaded = AgencyResearch.CODEC.parse(JsonOps.INSTANCE,
                AgencyResearch.CODEC.encodeStart(JsonOps.INSTANCE, research).getOrThrow()).getOrThrow();
        helper.assertTrue(reloaded.points() == research.points() && reloaded.isUnlocked("aluminum_lithium"),
                "la recherche n'a pas survécu à l'enregistrement");
        helper.succeed();
    }

    /**
     * Un câble s'illumine complètement quand de l'énergie y circule, puis s'éteint quand le transfert s'arrête
     * (ici, quand on retire le générateur).
     */
    private static void conduitsGlow(GameTestHelper helper) {
        BlockPos generatorPos = new BlockPos(0, 1, 0);
        BlockPos cablePos = new BlockPos(1, 1, 0);
        helper.setBlock(generatorPos, ModBlocks.COMBUSTION_GENERATOR.get());
        helper.setBlock(cablePos, ModBlocks.ENERGY_CABLE.get());
        helper.setBlock(new BlockPos(2, 1, 0), ModBlocks.ENERGY_CABLE.get());
        helper.setBlock(new BlockPos(3, 1, 0), ModBlocks.ARC_FOUNDRY.get());

        helper.assertTrue(helper.getBlockState(cablePos).getValue(ConduitBlock.GLOW) == 0, "un câble au repos ne doit pas luire");
        helper.getBlockEntity(generatorPos, CombustionGeneratorBlockEntity.class).getInventory().setStack(0, new ItemStack(Items.COAL));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(cablePos).getValue(ConduitBlock.GLOW) == ConduitBlock.MAX_GLOW,
                        "le câble parcouru par l'énergie ne s'est pas illuminé"))
                .thenExecute(() -> helper.setBlock(generatorPos, Blocks.AIR))
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(cablePos).getValue(ConduitBlock.GLOW) == 0,
                        "le câble ne s'est pas éteint après l'arrêt du transfert"))
                .thenSucceed();
    }

    /** Un générateur alimente une fonderie à travers trois câbles, dont un coude vertical. */
    private static void cablesCarryEnergy(GameTestHelper helper) {
        BlockPos generatorPos = new BlockPos(0, 1, 0);
        BlockPos foundryPos = new BlockPos(2, 3, 0);
        helper.setBlock(generatorPos, ModBlocks.COMBUSTION_GENERATOR.get());
        helper.setBlock(new BlockPos(1, 1, 0), ModBlocks.ENERGY_CABLE.get());
        helper.setBlock(new BlockPos(2, 1, 0), ModBlocks.ENERGY_CABLE.get());
        helper.setBlock(new BlockPos(2, 2, 0), ModBlocks.ENERGY_CABLE.get());
        helper.setBlock(foundryPos, ModBlocks.ARC_FOUNDRY.get());

        CombustionGeneratorBlockEntity generator = helper.getBlockEntity(generatorPos, CombustionGeneratorBlockEntity.class);
        ProcessingMachineBlockEntity foundry = helper.getBlockEntity(foundryPos, ProcessingMachineBlockEntity.class);
        generator.getInventory().setStack(0, new ItemStack(Items.COAL));
        foundry.getInventory().setStack(0, new ItemStack(Items.IRON_INGOT));
        foundry.getInventory().setStack(1, new ItemStack(Items.COAL));

        helper.succeedWhen(() -> helper.assertTrue(foundry.getInventory().getStack(2).is(ModItems.STEEL_INGOT.get()),
                "la fonderie reliée par câbles n'a pas produit d'acier"));
    }

    /**
     * Tuyaux : un tuyau à gaz transporte l'hydrogène gazeux mais refuse le RP-1, un tuyau à liquides fait
     * l'inverse, les deux ne se raccordent pas entre eux, et chacun prend la couleur de ce qui y circule.
     */
    private static void pipeKinds(GameTestHelper helper) {
        BlockPos gasPipe = new BlockPos(1, 2, 0);
        BlockPos liquidPipe = new BlockPos(1, 2, 2);
        helper.setBlock(new BlockPos(2, 2, 0), ModBlocks.CRYOGENIC_TANK.get());
        helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.CRYOGENIC_TANK.get());
        helper.setBlock(gasPipe, ModBlocks.GAS_PIPE.get());
        helper.setBlock(liquidPipe, ModBlocks.FLUID_PIPE.get());
        // Un troisième tuyau entre les deux : ni l'un ni l'autre ne doit s'y raccorder.
        helper.setBlock(new BlockPos(1, 2, 1), ModBlocks.GAS_PIPE.get());
        // Le test peut être tourné dans le monde : on compte les raccordements, sans se fier à leur direction.
        // Le tuyau à gaz du milieu, posé en dernier, s'est raccordé à l'autre tuyau à gaz, pas au tuyau à liquides.
        java.util.function.ToIntFunction<BlockPos> connections = pos -> (int) ConduitBlock.LINKS
                .values().stream().filter(property -> helper.getBlockState(pos).getValue(property) == ConduitBlock.Link.CONDUIT).count();
        helper.assertTrue(connections.applyAsInt(liquidPipe) == 0 && connections.applyAsInt(gasPipe) == 1,
                "raccordements des tuyaux incorrects : " + connections.applyAsInt(liquidPipe) + " et " + connections.applyAsInt(gasPipe));

        var gas = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(gasPipe), null);
        var liquid = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(liquidPipe), null);
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(gas.insert(FluidResource.of(ModFluids.RP1.get()), 100, transaction) == 0, "un tuyau à gaz doit refuser un liquide");
            helper.assertTrue(liquid.insert(FluidResource.of(ModFluids.HYDROGEN.get()), 100, transaction) == 0, "un tuyau à liquides doit refuser un gaz");
            helper.assertTrue(gas.insert(FluidResource.of(ModFluids.HYDROGEN.get()), 100, transaction) == 100, "le tuyau à gaz devrait transporter l'hydrogène");
            helper.assertTrue(liquid.insert(FluidResource.of(ModFluids.RP1.get()), 100, transaction) == 100, "le tuyau à liquides devrait transporter le RP-1");
            transaction.commit();
        }
        // En service, chaque tuyau montre ce qui y circule : hydrogène (1) pour l'un, RP-1 (2) pour l'autre.
        helper.succeedWhen(() -> helper.assertTrue(helper.getBlockState(gasPipe).getValue(ConduitBlock.CONTENT) == 1
                && helper.getBlockState(liquidPipe).getValue(ConduitBlock.CONTENT) == 2, "les tuyaux ne montrent pas leur contenu : gaz "
                + helper.getBlockState(gasPipe).getValue(ConduitBlock.CONTENT) + ", liquide " + helper.getBlockState(liquidPipe).getValue(ConduitBlock.CONTENT)));
    }

    /** Une raffinerie remplit un réservoir éloigné à travers deux tuyaux. */
    private static void pipesCarryFluids(GameTestHelper helper) {
        BlockPos refineryPos = new BlockPos(0, 1, 0);
        BlockPos tankPos = new BlockPos(3, 1, 0);
        helper.setBlock(refineryPos, ModBlocks.REFINERY.get());
        helper.setBlock(new BlockPos(1, 1, 0), ModBlocks.FLUID_PIPE.get());
        helper.setBlock(new BlockPos(2, 1, 0), ModBlocks.FLUID_PIPE.get());
        helper.setBlock(tankPos, ModBlocks.CRYOGENIC_TANK.get());

        FluidMachineBlockEntity refinery = helper.getBlockEntity(refineryPos, FluidMachineBlockEntity.class);
        CryogenicTankBlockEntity tank = helper.getBlockEntity(tankPos, CryogenicTankBlockEntity.class);
        refinery.getInventory().setStack(0, new ItemStack(Items.COAL));
        refinery.getEnergy().generate(20_000);

        helper.succeedWhen(() -> {
            FluidStack stored = tank.getTank().getFluid(0);
            helper.assertTrue(stored.getFluid() == ModFluids.RP1.get() && stored.getAmount() == 250,
                    "le réservoir relié par tuyaux n'a pas reçu 250 mB de RP-1");
        });
    }

    /** Verse un fluide dans un réservoir modulable, par n'importe lequel de ses blocs. Renvoie la quantité acceptée. */
    private static int fillTank(GameTestHelper helper, BlockPos pos, net.minecraft.world.level.material.Fluid fluid, int amount) {
        FluidTankBlockEntity tank = helper.getBlockEntity(pos, FluidTankBlockEntity.class);
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = tank.handler().insert(FluidResource.of(fluid), amount, transaction);
            transaction.commit();
            return inserted;
        }
    }

    /**
     * Réservoir modulable : huit blocs en cube forment un seul réservoir de 2×2×2 ; un bloc retiré, le contenu reste
     * dans les blocs qui restent ; et deux réservoirs de fluides différents ne fusionnent pas.
     */
    private static void modularTank(GameTestHelper helper) {
        java.util.List<BlockPos> cube = new java.util.ArrayList<>();
        for (int y = 2; y <= 3; y++) {
            for (int x = 1; x <= 2; x++) {
                for (int z = 1; z <= 2; z++) {
                    cube.add(new BlockPos(x, y, z));
                }
            }
        }
        cube.forEach(pos -> helper.setBlock(pos, ModBlocks.FLUID_TANK.get()));
        FluidTankBlockEntity any = helper.getBlockEntity(new BlockPos(2, 3, 2), FluidTankBlockEntity.class);
        FluidTankBlockEntity master = any.master();
        helper.assertTrue(master != null && master.width() == 2 && master.height() == 2
                && master.capacity() == 8 * FluidTankBlockEntity.CAPACITY_PER_BLOCK, "huit blocs en cube devraient former un réservoir de 2×2×2");
        long masters = cube.stream().filter(pos -> helper.getBlockEntity(pos, FluidTankBlockEntity.class).isMaster()).count();
        helper.assertTrue(masters == 1, "un réservoir n'a qu'un bloc maître, pas " + masters);
        // Au cœur du cube, chaque bloc touche trois voisins du même réservoir.
        long joined = FluidTankBlock.JOINED.values().stream().filter(side -> helper.getBlockState(new BlockPos(1, 2, 1)).getValue(side)).count();
        helper.assertTrue(joined == 3, "un bloc d'angle du cube devrait être joint de trois côtés, pas de " + joined);

        helper.assertTrue(fillTank(helper, new BlockPos(2, 3, 2), ModFluids.RP1.get(), 20_000) == 20_000, "le réservoir aurait dû accepter 20 000 mB");
        helper.assertTrue(fillTank(helper, new BlockPos(1, 2, 1), Fluids.WATER, 1_000) == 0, "un réservoir ne contient qu'un fluide à la fois");
        helper.assertTrue(any.fluid().getAmount() == 20_000 && any.tankCapacity() == 128_000, "contenu du réservoir incorrect");

        // Un bloc du haut retiré : il reste un réservoir de 2×2×1 et trois blocs seuls, et rien n'est perdu.
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.AIR);
        int total = 0;
        int wide = 0;
        for (BlockPos pos : cube) {
            if (helper.getBlockState(pos).is(ModBlocks.FLUID_TANK.get())) {
                FluidTankBlockEntity tank = helper.getBlockEntity(pos, FluidTankBlockEntity.class);
                if (tank.isMaster()) {
                    total += tank.fluid().getAmount();
                    wide += tank.width() == 2 && tank.height() == 1 ? 1 : 0;
                }
            }
        }
        helper.assertTrue(total == 20_000 && wide == 1, "après le retrait d'un bloc : " + total + " mB, " + wide + " réservoir(s) de 2×2×1");

        // Une colonne : de l'eau en bas, du RP-1 en haut. Le bloc posé entre les deux rejoint celui du bas, pas celui du haut.
        helper.setBlock(new BlockPos(5, 2, 1), ModBlocks.FLUID_TANK.get());
        helper.setBlock(new BlockPos(5, 4, 1), ModBlocks.FLUID_TANK.get());
        fillTank(helper, new BlockPos(5, 2, 1), Fluids.WATER, 4_000);
        fillTank(helper, new BlockPos(5, 4, 1), ModFluids.RP1.get(), 3_000);
        helper.setBlock(new BlockPos(5, 3, 1), ModBlocks.FLUID_TANK.get());
        FluidTankBlockEntity bottom = helper.getBlockEntity(new BlockPos(5, 2, 1), FluidTankBlockEntity.class);
        FluidTankBlockEntity top = helper.getBlockEntity(new BlockPos(5, 4, 1), FluidTankBlockEntity.class);
        helper.assertTrue(bottom.height() == 2 && bottom.fluid().getFluid() == Fluids.WATER && bottom.fluid().getAmount() == 4_000
                && top.isMaster() && top.height() == 1 && top.fluid().getFluid() == ModFluids.RP1.get() && top.fluid().getAmount() == 3_000,
                "deux réservoirs de fluides différents ne devraient pas fusionner");
        helper.succeed();
    }

    /**
     * Un réservoir ne pousse rien : il faut un tuyau en extraction pour le vider. Le contenu arrive alors au
     * réservoir cryogénique, au bout du tuyau.
     */
    private static void pipeExtraction(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 2, 1);
        BlockPos pipe = new BlockPos(2, 2, 1);
        BlockPos target = new BlockPos(4, 2, 1);
        helper.setBlock(pipe, ModBlocks.FLUID_PIPE.get());
        helper.setBlock(new BlockPos(3, 2, 1), ModBlocks.FLUID_PIPE.get());
        helper.setBlock(source, ModBlocks.FLUID_TANK.get());
        helper.setBlock(target, ModBlocks.CRYOGENIC_TANK.get());
        fillTank(helper, source, ModFluids.RP1.get(), 5_000);
        long fittings = ConduitBlock.LINKS.values().stream()
                .filter(side -> helper.getBlockState(pipe).getValue(side) == ConduitBlock.Link.MACHINE).count();
        helper.assertTrue(fittings == 1, "le tuyau devrait porter un raccord, du côté du réservoir");

        FluidTankBlockEntity tank = helper.getBlockEntity(source, FluidTankBlockEntity.class);
        CryogenicTankBlockEntity cryogenic = helper.getBlockEntity(target, CryogenicTankBlockEntity.class);
        helper.startSequence()
                .thenIdle(30)
                .thenExecute(() -> {
                    helper.assertTrue(tank.fluid().getAmount() == 5_000 && cryogenic.getTank().getFluid(0).isEmpty(),
                            "un réservoir ne devrait rien pousser de lui-même");
                    helper.setBlock(pipe, helper.getBlockState(pipe).setValue(ConduitBlock.PULL, true));
                })
                .thenWaitUntil(() -> helper.assertTrue(tank.fluid().isEmpty() && cryogenic.getTank().getFluid(0).getAmount() == 5_000
                        && cryogenic.getTank().getFluid(0).getFluid() == ModFluids.RP1.get(),
                        "le tuyau en extraction n'a pas vidé le réservoir : il y reste " + tank.fluid().getAmount() + " mB"))
                .thenSucceed();
    }

    /**
     * Sources d'un écran de contrôle : un réservoir relié par un câble de données et un autre posé contre l'écran
     * lui donnent l'affichage des réservoirs, avec leurs niveaux.
     */
    private static void displaySources(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        // Deux câbles de données derrière l'écran (il regarde le nord, son dos est au sud), posés d'abord : l'écran
        // puis le réservoir viendront s'y raccorder. Un troisième câble au-dessus de l'écran, lui, ne s'y branche pas.
        helper.setBlock(pos.south(1), ModBlocks.DATA_CABLE.get());
        helper.setBlock(pos.south(2), ModBlocks.DATA_CABLE.get());
        helper.setBlock(pos.above(), ModBlocks.DATA_CABLE.get());
        helper.setBlock(pos, ModBlocks.MISSION_DISPLAY.get());
        MissionDisplayBlockEntity display = helper.getBlockEntity(pos, MissionDisplayBlockEntity.class);
        MissionDisplayBlockEntity.Wall wall = MissionDisplayBlockEntity.wallOf(helper.getLevel(), helper.absolutePos(pos));
        display.scan(helper.getLevel(), wall);
        helper.assertTrue(!display.hasSource() && display.tanks().length == 0, "un écran sans réservoir ni ordinateur n'a pas de source");

        // Au bout des câbles : un réservoir modulable. Sous l'écran, au contact : un réservoir cryogénique. Au bout du
        // câble du dessus : un autre réservoir, que l'écran ne doit pas lire.
        helper.setBlock(pos.south(3), ModBlocks.FLUID_TANK.get());
        helper.setBlock(pos.above(2), ModBlocks.FLUID_TANK.get());
        helper.setBlock(pos.below(), ModBlocks.CRYOGENIC_TANK.get());
        fillTank(helper, pos.south(3), Fluids.WATER, 3_000);
        java.util.function.BiFunction<BlockPos, ConduitBlock.Link, Long> links = (cable, kind) -> ConduitBlock.LINKS.values().stream()
                .filter(side -> helper.getBlockState(cable).getValue(side) == kind).count();
        helper.assertTrue(links.apply(pos.south(1), ConduitBlock.Link.MACHINE) == 1 && links.apply(pos.south(1), ConduitBlock.Link.CONDUIT) == 1
                && links.apply(pos.south(2), ConduitBlock.Link.MACHINE) == 1, "le câble de données devrait se raccorder au dos de l'écran et au réservoir");
        helper.assertTrue(links.apply(pos.above(), ConduitBlock.Link.MACHINE) == 1,
                "un câble de données ne devrait pas se brancher sur la tranche d'un écran, seulement sur le réservoir");
        display.scan(helper.getLevel(), wall);
        int[] tanks = display.tanks();
        helper.assertTrue(tanks.length == 2 * MissionDisplayBlockEntity.TANK_FIELDS && display.sourceCount() == 2,
                "l'écran devrait lire deux réservoirs, pas " + tanks.length / MissionDisplayBlockEntity.TANK_FIELDS);
        boolean water = false;
        for (int i = 0; i < tanks.length; i += MissionDisplayBlockEntity.TANK_FIELDS) {
            water |= tanks[i + 1] == 3_000 && tanks[i + 2] == FluidTankBlockEntity.CAPACITY_PER_BLOCK;
        }
        helper.assertTrue(water && display.hasSource() && display.page() == MissionDisplayBlockEntity.Page.TANKS,
                "l'écran devrait afficher les réservoirs, dont celui de 3 000 mB d'eau");
        helper.succeed();
    }

    /** La raffinerie transforme un charbon en 250 mB de kérosène RP-1. */
    private static void refineryMakesRp1(GameTestHelper helper) {
        BlockPos refineryPos = new BlockPos(0, 1, 0);
        helper.setBlock(refineryPos, ModBlocks.REFINERY.get());
        FluidMachineBlockEntity refinery = helper.getBlockEntity(refineryPos, FluidMachineBlockEntity.class);

        helper.assertTrue(refinery.getInventory().isValid(0, ItemResource.of(Items.COAL)), "la raffinerie refuse le charbon");
        helper.assertTrue(!refinery.getInventory().isValid(0, ItemResource.of(Items.DIRT)), "la raffinerie accepte de la terre");

        refinery.getInventory().setStack(0, new ItemStack(Items.COAL));
        refinery.getEnergy().generate(20_000);

        helper.succeedWhen(() -> {
            FluidStack produced = refinery.getTanks().getFluid(1);
            helper.assertTrue(produced.getFluid() == ModFluids.RP1.get() && produced.getAmount() == 250,
                    "la raffinerie n'a pas produit 250 mB de RP-1");
        });
    }

    /**
     * L'extracteur d'hélium-3 tire d'un minerai d'ilménite 40 mB d'hélium-3 et 50 mB d'hydrogène ; le réacteur de
     * Sabatier tire d'un charbon et de 800 mB d'hydrogène 400 mB de méthane et 200 mB d'eau.
     */
    private static void helium3AndMethane(GameTestHelper helper) {
        BlockPos extractorPos = new BlockPos(0, 1, 0);
        helper.setBlock(extractorPos, ModBlocks.HELIUM3_EXTRACTOR.get());
        FluidMachineBlockEntity extractor = helper.getBlockEntity(extractorPos, FluidMachineBlockEntity.class);
        helper.assertTrue(extractor.getInventory().isValid(0, ItemResource.of(ModBlocks.MOON_ILMENITE_ORE.get().asItem())),
                "l'extracteur refuse le minerai d'ilménite");
        helper.assertTrue(!extractor.getInventory().isValid(0, ItemResource.of(Items.DIRT)), "l'extracteur accepte de la terre");
        extractor.getInventory().setStack(0, new ItemStack(ModBlocks.MOON_ILMENITE_ORE.get()));
        extractor.getEnergy().generate(20_000);

        BlockPos reactorPos = new BlockPos(3, 1, 0);
        helper.setBlock(reactorPos, ModBlocks.SABATIER_REACTOR.get());
        FluidMachineBlockEntity reactor = helper.getBlockEntity(reactorPos, FluidMachineBlockEntity.class);
        reactor.getInventory().setStack(0, new ItemStack(Items.COAL));
        reactor.getTanks().setFluid(0, new FluidStack(ModFluids.HYDROGEN.get(), 800));
        reactor.getEnergy().generate(20_000);

        helper.succeedWhen(() -> {
            FluidStack helium = extractor.getTanks().getFluid(1);
            FluidStack hydrogen = extractor.getTanks().getFluid(2);
            helper.assertTrue(helium.getFluid() == ModFluids.HELIUM_3.get() && helium.getAmount() == 40
                    && hydrogen.getFluid() == ModFluids.HYDROGEN.get() && hydrogen.getAmount() == 50,
                    "l'extracteur n'a pas produit 40 mB d'hélium-3 et 50 mB d'hydrogène");
            FluidStack methane = reactor.getTanks().getFluid(1);
            FluidStack water = reactor.getTanks().getFluid(2);
            helper.assertTrue(methane.getFluid() == ModFluids.METHANE.get() && methane.getAmount() == 400
                    && water.getFluid() == net.minecraft.world.level.material.Fluids.WATER && water.getAmount() == 200,
                    "le réacteur de Sabatier n'a pas produit 400 mB de méthane et 200 mB d'eau");
        });
    }

    /**
     * Proxima b : la face de jour à l'est, la face de nuit à l'ouest, la bande du crépuscule entre elles, autour du
     * méridien zéro ; la flore pousse sur la mousse et le régolithe, pas sur la roche. Le serveur de test ne charge pas
     * les dimensions du mod : on vérifie la répartition et les plantes, pas le monde lui-même.
     */
    private static void proximaWorld(GameTestHelper helper) {
        var zone = com.orbitalis.phase3.celestial.ProximaBiomeSource.zone(0, 0);
        helper.assertTrue(zone == com.orbitalis.phase3.celestial.ProximaBiomeSource.Zone.TWILIGHT, "le méridien zéro devrait être au crépuscule");
        helper.assertTrue(com.orbitalis.phase3.celestial.ProximaBiomeSource.zone(5000, 300) == com.orbitalis.phase3.celestial.ProximaBiomeSource.Zone.DAY,
                "l'est devrait être la face de jour");
        helper.assertTrue(com.orbitalis.phase3.celestial.ProximaBiomeSource.zone(-5000, -800) == com.orbitalis.phase3.celestial.ProximaBiomeSource.Zone.NIGHT,
                "l'ouest devrait être la face de nuit");
        BlockPos ground = new BlockPos(1, 1, 1);
        helper.setBlock(ground, ModBlocks.PROXIMA_MOSS.get());
        helper.setBlock(ground.above(), ModBlocks.UMBRA_FERN.get());
        helper.assertTrue(ModBlocks.UMBRA_FERN.get().defaultBlockState().canSurvive(helper.getLevel(), helper.absolutePos(ground.above())),
                "la fougère d'ombre devrait pousser sur la mousse");
        BlockPos rock = new BlockPos(3, 1, 1);
        helper.setBlock(rock, ModBlocks.PROXIMA_ROCK.get());
        helper.assertTrue(!ModBlocks.RED_SEDGE.get().defaultBlockState().canSurvive(helper.getLevel(), helper.absolutePos(rock.above())),
                "la laîche ne pousse pas sur la roche nue");
        helper.succeed();
    }

    /**
     * Le chantier naval s'accole au module central d'une station, bâtie haut dans le ciel pour que son berceau ne gêne
     * aucun autre essai. Son pupitre est relié au berceau ; un joueur en créatif lui fait poser tout le vaisseau, sans
     * qu'aucune pièce du plan ne tombe sur une autre.
     */
    private static void orbitalShipyard(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(3, 90, 3));
        keepChunksLoaded(helper, center.east(20), 3);
        StationManager.build(level, center);
        Component problem = StationModules.attach(level, center.east(2), Direction.EAST, com.orbitalis.phase2.station.ModuleType.SHIPYARD);
        helper.assertTrue(problem == null, "le chantier aurait dû s'accoler : " + (problem == null ? "" : problem.getString()));
        BlockPos consolePos = center.east(5).below();
        helper.assertTrue(level.getBlockEntity(consolePos) instanceof com.orbitalis.phase3.interstellar.ShipyardConsoleBlockEntity,
                "pas de pupitre face à la verrière");
        com.orbitalis.phase3.interstellar.ShipyardConsoleBlockEntity console =
                (com.orbitalis.phase3.interstellar.ShipyardConsoleBlockEntity) level.getBlockEntity(consolePos);
        helper.assertTrue(console.linked(), "le pupitre n'est pas relié au berceau");
        java.util.Set<BlockPos> cells = new java.util.HashSet<>();
        for (com.orbitalis.phase3.interstellar.ShipPlan.Piece piece : console.pieces()) {
            helper.assertTrue(cells.add(piece.offset()), "deux pièces du plan au même endroit : " + piece.offset());
        }
        helper.assertTrue(console.currentSection() == com.orbitalis.phase3.interstellar.ShipPlan.Section.ENGINEERING,
                "on commence par la salle des machines");
        int placed = console.build(helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE), true);
        helper.assertTrue(placed == cells.size(), "tout le vaisseau aurait dû être posé : " + placed + " / " + cells.size());
        helper.assertTrue(console.currentSection() == null, "le vaisseau devrait être achevé");
        // Le bilan, au poste de pilotage : tout est là, mais le cœur de distorsion est vide.
        BlockPos origin = console.shipOrigin();
        BlockPos helmPos = origin.offset(com.orbitalis.phase3.interstellar.ShipPlan.local(Direction.EAST,
                com.orbitalis.phase3.interstellar.ShipPlan.HELM_A, 0, com.orbitalis.phase3.interstellar.ShipPlan.HELM_Y));
        helper.assertTrue(level.getBlockState(helmPos).is(ModBlocks.SHIP_HELM.get()), "pas de poste de pilotage sur la passerelle");
        helper.assertTrue(com.orbitalis.phase3.interstellar.ShipHelmBlock.shipOrigin(helmPos,
                level.getBlockState(helmPos).getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)).equals(origin),
                "le poste de pilotage ne retrouve pas la poupe");
        com.orbitalis.phase3.interstellar.ShipSystems systems = com.orbitalis.phase3.interstellar.ShipSystems.check(level, origin, Direction.EAST);
        helper.assertTrue(systems.aboard() && systems.core() && systems.ringDrive() && systems.helm() && systems.pods()[0] == 4
                && systems.trays()[0] == 8 && !systems.ready(), "bilan faux avant la charge du cœur");
        BlockPos corePos = origin.offset(com.orbitalis.phase3.interstellar.ShipPlan.local(Direction.EAST, 2, 0, -1));
        com.orbitalis.phase3.interstellar.WarpCoreBlock.setCharge(level, corePos.above(), level.getBlockState(corePos.above()),
                com.orbitalis.phase3.interstellar.WarpCoreBlock.MAX_CHARGE);
        helper.assertTrue(level.getBlockState(corePos).getValue(com.orbitalis.phase3.interstellar.WarpCoreBlock.CHARGE)
                == com.orbitalis.phase3.interstellar.WarpCoreBlock.MAX_CHARGE, "la charge n'a pas gagné la moitié basse du cœur");
        helper.assertTrue(com.orbitalis.phase3.interstellar.ShipSystems.check(level, origin, Direction.EAST).ready(),
                "le vaisseau devrait être prêt, cœur chargé");
        helper.succeed();
    }

    /**
     * Le réacteur à fusion, nourri d'hélium-3 et d'hydrogène, s'allume et alimente seul la chambre de Casimir posée contre
     * lui : une perle d'ender y devient de la matière exotique, et le réacteur a brûlé ses deux gaz.
     */
    private static void fusionPower(GameTestHelper helper) {
        BlockPos reactorPos = new BlockPos(0, 1, 0);
        BlockPos chamberPos = new BlockPos(1, 1, 0);
        helper.setBlock(reactorPos, ModBlocks.FUSION_REACTOR.get());
        helper.setBlock(chamberPos, ModBlocks.CASIMIR_CHAMBER.get());
        com.orbitalis.phase3.propulsion.FusionReactorBlockEntity reactor =
                helper.getBlockEntity(reactorPos, com.orbitalis.phase3.propulsion.FusionReactorBlockEntity.class);
        ProcessingMachineBlockEntity chamber = helper.getBlockEntity(chamberPos, ProcessingMachineBlockEntity.class);
        helper.assertTrue(!reactor.getTanks().isValid(0, FluidResource.of(ModFluids.METHANE.get())),
                "le réacteur accepte du méthane");
        reactor.getTanks().setFluid(0, new FluidStack(ModFluids.HELIUM_3.get(), 1000));
        reactor.getTanks().setFluid(1, new FluidStack(ModFluids.HYDROGEN.get(), 8000));
        chamber.getInventory().setStack(0, new ItemStack(Items.ENDER_PEARL));

        helper.succeedWhen(() -> {
            helper.assertTrue(chamber.getInventory().getStack(1).is(ModItems.EXOTIC_MATTER.get()),
                    "la chambre de Casimir n'a pas produit de matière exotique");
            helper.assertTrue(reactor.getTanks().getFluid(0).getAmount() < 1000 && reactor.getTanks().getFluid(1).getAmount() < 8000,
                    "le réacteur n'a pas brûlé ses gaz");
        });
    }

    /**
     * Stockage : un ergol cryogénique s'évapore dans un réservoir sans énergie, mais pas dans un réservoir
     * alimenté ; le RP-1 se conserve sans énergie.
     */
    private static void cryogenicStorage(GameTestHelper helper) {
        CryogenicTankBlockEntity unpowered = placeTank(helper, new BlockPos(0, 1, 0), ModFluids.LIQUID_OXYGEN.get());
        CryogenicTankBlockEntity powered = placeTank(helper, new BlockPos(2, 1, 0), ModFluids.LIQUID_OXYGEN.get());
        CryogenicTankBlockEntity kerosene = placeTank(helper, new BlockPos(4, 1, 0), ModFluids.RP1.get());
        powered.getEnergy().generate(20_000);

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(unpowered.getTank().getFluid(0).getAmount() < 1000, "l'oxygène liquide ne s'évapore pas sans énergie");
            helper.assertTrue(powered.getTank().getFluid(0).getAmount() == 1000, "l'oxygène liquide s'évapore malgré le refroidissement");
            helper.assertTrue(kerosene.getTank().getFluid(0).getAmount() == 1000, "le RP-1 s'évapore");
            helper.succeed();
        });
    }

    private static CryogenicTankBlockEntity placeTank(GameTestHelper helper, BlockPos pos, net.minecraft.world.level.material.Fluid fluid) {
        helper.setBlock(pos, ModBlocks.CRYOGENIC_TANK.get());
        CryogenicTankBlockEntity tank = helper.getBlockEntity(pos, CryogenicTankBlockEntity.class);
        tank.getTank().setFluid(0, new FluidStack(fluid, 1000));
        return tank;
    }

    /**
     * Règles des emplacements, vérifiées à travers la vraie interface d'un joueur :
     * une entrée n'accepte que les ingrédients des recettes de la machine, la sortie n'accepte rien,
     * le joueur peut reprendre ce qu'il a déposé, et l'automatisation ne retire que de la sortie.
     */
    private static void machineSlotRules(GameTestHelper helper) {
        BlockPos foundryPos = new BlockPos(0, 1, 0);
        helper.setBlock(foundryPos, ModBlocks.ARC_FOUNDRY.get());
        ProcessingMachineBlockEntity foundry = helper.getBlockEntity(foundryPos, ProcessingMachineBlockEntity.class);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        AbstractContainerMenu menu = foundry.createMenu(1, player.getInventory(), player);
        Slot input = menu.getSlot(0);
        Slot output = menu.getSlot(2);

        helper.assertTrue(input.mayPlace(new ItemStack(Items.IRON_INGOT)), "l'entrée refuse un lingot de fer");
        helper.assertTrue(input.mayPlace(new ItemStack(Items.COAL)), "l'entrée refuse du charbon");
        helper.assertTrue(!input.mayPlace(new ItemStack(Items.DIRT)), "l'entrée accepte de la terre");
        helper.assertTrue(!output.mayPlace(new ItemStack(Items.IRON_INGOT)), "la sortie accepte un dépôt");

        foundry.getInventory().setStack(0, new ItemStack(Items.IRON_INGOT, 3));
        foundry.getInventory().setStack(2, new ItemStack(ModItems.STEEL_INGOT.get()));
        helper.assertTrue(input.mayPickup(player), "le joueur ne peut pas reprendre le contenu d'une entrée");
        helper.assertTrue(output.mayPickup(player), "le joueur ne peut pas prendre le contenu de la sortie");

        var automation = foundry.getInventory().automationView();
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(automation.extract(0, ItemResource.of(Items.IRON_INGOT), 1, transaction) == 0,
                    "l'automatisation retire d'une entrée");
            helper.assertTrue(automation.extract(2, ItemResource.of(ModItems.STEEL_INGOT.get()), 1, transaction) == 1,
                    "l'automatisation ne retire pas de la sortie");
            helper.assertTrue(automation.insert(0, ItemResource.of(Items.DIRT), 1, transaction) == 0,
                    "l'automatisation dépose de la terre dans une entrée");
        }
        helper.succeed();
    }

    /**
     * Un générateur à combustion alimente une fonderie à arc et un laminoir posés contre lui :
     * la fonderie doit produire un lingot d'acier, le laminoir une tôle d'aluminium.
     */
    private static void machinesProcessItems(GameTestHelper helper) {
        BlockPos foundryPos = new BlockPos(0, 1, 0);
        BlockPos generatorPos = new BlockPos(1, 1, 0);
        BlockPos millPos = new BlockPos(2, 1, 0);
        helper.setBlock(foundryPos, ModBlocks.ARC_FOUNDRY.get());
        helper.setBlock(generatorPos, ModBlocks.COMBUSTION_GENERATOR.get());
        helper.setBlock(millPos, ModBlocks.ROLLING_MILL.get());

        CombustionGeneratorBlockEntity generator = helper.getBlockEntity(generatorPos, CombustionGeneratorBlockEntity.class);
        ProcessingMachineBlockEntity foundry = helper.getBlockEntity(foundryPos, ProcessingMachineBlockEntity.class);
        ProcessingMachineBlockEntity mill = helper.getBlockEntity(millPos, ProcessingMachineBlockEntity.class);

        generator.getInventory().setStack(0, new ItemStack(Items.COAL));
        // Ingrédients volontairement placés dans l'ordre inverse de la recette : l'ordre ne doit pas compter.
        foundry.getInventory().setStack(0, new ItemStack(Items.COAL));
        foundry.getInventory().setStack(1, new ItemStack(Items.IRON_INGOT));
        mill.getInventory().setStack(0, new ItemStack(ModItems.ALUMINUM_INGOT.get()));

        helper.succeedWhen(() -> {
            helper.assertTrue(foundry.getInventory().getStack(2).is(ModItems.STEEL_INGOT.get()),
                    "la fonderie à arc n'a pas produit de lingot d'acier");
            helper.assertTrue(mill.getInventory().getStack(1).is(ModItems.ALUMINUM_PLATE.get()),
                    "le laminoir n'a pas produit de tôle d'aluminium");
            helper.assertTrue(foundry.getInventory().getStack(0).isEmpty() && foundry.getInventory().getStack(1).isEmpty(),
                    "la fonderie à arc n'a pas consommé ses ingrédients");
        });
    }
}
