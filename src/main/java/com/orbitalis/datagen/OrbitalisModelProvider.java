package com.orbitalis.datagen;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.orbitalis.phase2.station.CrewBunkBlock;
import com.orbitalis.Orbitalis;
import com.orbitalis.phase2.station.LifeSupportBlock;
import com.orbitalis.phase2.station.HydroponicTrayBlock;
import com.orbitalis.phase2.station.LabRackBlock;
import com.orbitalis.phase2.station.StationBatteryBlock;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.registry.ModItems;
import com.orbitalis.phase1.conduit.ConduitBlock;
import com.orbitalis.phase1.fuel.FluidTankBlock;
import com.orbitalis.phase1.spaceport.BoardingGateBlock;
import com.orbitalis.phase1.spaceport.FloodlightBlock;
import com.orbitalis.phase1.spaceport.FuelPortBlock;
import com.orbitalis.phase1.spaceport.LaunchPadBlock;
import com.orbitalis.phase1.spaceport.LaunchTableBlock;
import com.orbitalis.phase1.spaceport.MissionDisplayBlock;
import com.orbitalis.phase1.spaceport.RocketBlock;
import com.orbitalis.phase1.spaceport.StructurePartBlock;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.ConditionBuilder;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.renderer.block.dispatch.VariantMutator;
import net.minecraft.client.renderer.block.dispatch.multipart.CombinedCondition;
import net.minecraft.client.renderer.block.dispatch.multipart.Condition;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.data.PackOutput;

/**
 * Génère les états de blocs, les modèles de blocs et les modèles d'items.
 */
public class OrbitalisModelProvider extends ModelProvider {
    public OrbitalisModelProvider(PackOutput output) {
        super(output, Orbitalis.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        // Blocs cubiques simples (même texture sur les six faces). L'item du bloc est généré automatiquement.
        blockModels.createTrivialCube(ModBlocks.BAUXITE_ORE.get());
        blockModels.createTrivialCube(ModBlocks.DEEPSLATE_BAUXITE_ORE.get());
        blockModels.createTrivialCube(ModBlocks.TITANIUM_ORE.get());
        blockModels.createTrivialCube(ModBlocks.DEEPSLATE_TITANIUM_ORE.get());
        blockModels.createTrivialCube(ModBlocks.LITHIUM_ORE.get());
        blockModels.createTrivialCube(ModBlocks.DEEPSLATE_LITHIUM_ORE.get());
        blockModels.createTrivialCube(ModBlocks.STEEL_BLOCK.get());
        blockModels.createTrivialCube(ModBlocks.ALUMINUM_BLOCK.get());
        blockModels.createTrivialCube(ModBlocks.TITANIUM_BLOCK.get());

        // Machines : chacune a son modèle 3D, à l'arrêt et en marche.
        machine(blockModels, ModBlocks.COMBUSTION_GENERATOR.get());
        machine(blockModels, ModBlocks.ARC_FOUNDRY.get());
        machine(blockModels, ModBlocks.ROLLING_MILL.get());
        machine(blockModels, ModBlocks.ELECTROLYZER.get());
        machine(blockModels, ModBlocks.SABATIER_REACTOR.get());
        machine(blockModels, ModBlocks.HELIUM3_EXTRACTOR.get());
        machine(blockModels, ModBlocks.CASIMIR_CHAMBER.get());
        // Réacteur à fusion : ses modèles, écrits par tools/generate_methalox.py, s'appellent comme ceux des machines.
        machine(blockModels, ModBlocks.FUSION_REACTOR.get());
        machine(blockModels, ModBlocks.CRYOGENIC_LIQUEFIER.get());
        machine(blockModels, ModBlocks.REFINERY.get());
        machine(blockModels, ModBlocks.CRYOGENIC_TANK.get());

        // Terminal de recherche : même carcasse que les machines, avec un écran toujours allumé.


        // Blocs de test.
        blockModels.createTrivialCube(ModBlocks.CREATIVE_ENERGY_SOURCE.get());
        blockModels.createTrivialCube(ModBlocks.CREATIVE_FLUID_SOURCE.get());
        // Réservoirs créatifs : la silhouette du réservoir, pleine du fluide qu'ils fournissent.
        ModBlocks.CREATIVE_TANKS.forEach(tank -> {
            Identifier model = Orbitalis.id("block/tank/" + tank.getId().getPath());
            blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(tank.get(), BlockModelGenerators.plainVariant(model)));
            blockModels.registerSimpleItemModel(tank.get(), model);
        });
        fluidTank(blockModels);
        // Débris de fusée : un cube de tôle noircie.
        blockModels.createTrivialCube(ModBlocks.ROCKET_DEBRIS.get());

        // Complexe de lancement.
        launchPad(blockModels, ModBlocks.LAUNCH_PAD.get());
        // Blocs du pas de tir : modèles 3D du kit (tools/generate_launch_pad.py), dessinés tournés vers l'est et
        // orientés à la pose. Le tablier, les mâts, le carneau et les bras s'effacent dans un pas de tir assemblé :
        // c'est alors la table de lancement qui les dessine. La tour reste faite de ses blocs.
        Map<Block, String> padBlocks = Map.ofEntries(Map.entry(ModBlocks.FLAME_DEFLECTOR.get(), "flame_deflector"),
                Map.entry(ModBlocks.FLAME_TRENCH.get(), "flame_trench"), Map.entry(ModBlocks.LIGHTNING_MAST.get(), "mast_block"),
                Map.entry(ModBlocks.SERVICE_TOWER.get(), "service_tower"), Map.entry(ModBlocks.UMBILICAL_ARM.get(), "umbilical_arm"),
                Map.entry(ModBlocks.TOWER_FRAME.get(), "tower_frame"), Map.entry(ModBlocks.TOWER_ELEVATOR.get(), "tower_elevator"),
                Map.entry(ModBlocks.TOWER_STAIRS.get(), "tower_stairs"), Map.entry(ModBlocks.TOWER_LANDING.get(), "tower_landing"),
                Map.entry(ModBlocks.TOWER_DECK.get(), "tower_deck"),
                Map.entry(ModBlocks.PAD_SUPPORT.get(), "pad_support"),
                Map.entry(ModBlocks.DELUGE_PIPE.get(), "deluge_pipe"));
        padBlocks.forEach((block, name) -> {
            Identifier model = Orbitalis.id("block/machine/" + name);
            // Le socle d'ascenseur est le seul à ne pas s'orienter.
            MultiVariantGenerator states = MultiVariantGenerator.dispatch(block, BlockModelGenerators.plainVariant(model));
            blockModels.blockStateOutput.accept(block == ModBlocks.TOWER_ELEVATOR.get() ? states : states.with(BlockModelGenerators.ROTATION_TORCH));
            blockModels.registerSimpleItemModel(block, model);
        });
        // Projecteur de mât : éteint ou allumé, tourné droit ou en diagonale.
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.PAD_FLOODLIGHT.get())
                .with(PropertyDispatch.initial(FloodlightBlock.LIT, FloodlightBlock.DIAGONAL).generate((lit, diagonal) ->
                        BlockModelGenerators.plainVariant(Orbitalis.id("block/machine/pad_floodlight"
                                + (diagonal ? "_diagonal" : "") + (lit ? "_on" : "")))))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        blockModels.registerSimpleItemModel(ModBlocks.PAD_FLOODLIGHT.get(), Orbitalis.id("block/machine/pad_floodlight"));
        blockModels.createAirLikeBlock(ModBlocks.PAD_LIGHT.get(), texture("pad_lamp"));
        // Rambarde de tour : un poteau, et une lisse vers chaque voisine, comme une barrière. Quand une autre rambarde
        // est posée dessus, le poteau et les lisses s'arrêtent au bord du bloc, sans main courante.
        {
            Block railing = ModBlocks.TOWER_RAILING.get();
            MultiPartGenerator railingParts = MultiPartGenerator.multiPart(railing);
            for (boolean up : new boolean[] {false, true}) {
                String low = up ? "_low" : "";
                MultiVariant side = BlockModelGenerators.plainVariant(Orbitalis.id("block/machine/tower_railing_side" + low));
                railingParts = railingParts
                        .with(BlockModelGenerators.condition().term(com.orbitalis.phase1.spaceport.TowerRailingBlock.UP, up),
                                BlockModelGenerators.plainVariant(Orbitalis.id("block/machine/tower_railing_post" + low)))
                        .with(BlockModelGenerators.condition().term(BlockStateProperties.NORTH, true)
                                .term(com.orbitalis.phase1.spaceport.TowerRailingBlock.UP, up), side)
                        .with(BlockModelGenerators.condition().term(BlockStateProperties.EAST, true)
                                .term(com.orbitalis.phase1.spaceport.TowerRailingBlock.UP, up), side.with(BlockModelGenerators.Y_ROT_90))
                        .with(BlockModelGenerators.condition().term(BlockStateProperties.SOUTH, true)
                                .term(com.orbitalis.phase1.spaceport.TowerRailingBlock.UP, up), side.with(BlockModelGenerators.Y_ROT_180))
                        .with(BlockModelGenerators.condition().term(BlockStateProperties.WEST, true)
                                .term(com.orbitalis.phase1.spaceport.TowerRailingBlock.UP, up), side.with(BlockModelGenerators.Y_ROT_270));
            }
            blockModels.blockStateOutput.accept(railingParts);
        }
        blockModels.registerSimpleItemModel(ModBlocks.TOWER_RAILING.get(), Orbitalis.id("block/machine/tower_railing"));
        // Portique d'embarquement, trois blocs sur trois : sa case du milieu en bas dessine les deux premiers niveaux,
        // celle du milieu en haut dessine le linteau, les autres ne dessinent rien. Porte palière ouverte ou fermée.
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.BOARDING_GATE.get())
                .with(PropertyDispatch.initial(BoardingGateBlock.COLUMN, BoardingGateBlock.ROW, BoardingGateBlock.CLOSED)
                        .generate((column, row, closed) -> BlockModelGenerators.plainVariant(Orbitalis.id("block/machine/boarding_gate"
                                + (column != 1 || row == 1 ? "_part" : (row == 2 ? "_top" : "") + (closed ? "_closed" : ""))))))
                .with(BlockModelGenerators.ROTATION_TORCH));
        blockModels.registerSimpleItemModel(ModBlocks.BOARDING_GATE.get(), Orbitalis.id("block/machine/boarding_gate"));
        // Bloc de ravitaillement : un modèle par ergol.
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.FUEL_PORT.get())
                .with(PropertyDispatch.initial(FuelPortBlock.KIND).generate(kind -> BlockModelGenerators.plainVariant(
                        Orbitalis.id("block/machine/fuel_port_" + kind.getSerializedName())))));
        blockModels.registerSimpleItemModel(ModBlocks.FUEL_PORT.get(), Orbitalis.id("block/machine/fuel_port_rp1"));
        // Blocs de construction des fusées et ordinateur de conception : modèles 3D écrits par
        // tools/generate_rocket_parts.py, dessinés face avant au nord.
        List<Block> rocketParts = List.of(ModBlocks.ROCKET_ENGINE_SOLID.get(), ModBlocks.ROCKET_ENGINE_PRESSURE.get(), ModBlocks.ROCKET_ENGINE_KEROLOX.get(), ModBlocks.ROCKET_ENGINE_STAGED.get(), ModBlocks.ROCKET_ENGINE_METHALOX.get(), ModBlocks.ROCKET_ENGINE_HYDROLOX.get(), ModBlocks.ROCKET_TANK_STEEL.get(), ModBlocks.ROCKET_TANK_ALUMINUM.get(), ModBlocks.ROCKET_TANK_ALLOY.get(), ModBlocks.ROCKET_TANK_COMPOSITE.get(), ModBlocks.ROCKET_TANK_CRYO.get(), ModBlocks.ROCKET_ENGINE_BOOSTER.get(), ModBlocks.ROCKET_ENGINE_NUCLEAR.get(), ModBlocks.ROCKET_ENGINE_FUSION.get(), ModBlocks.ROCKET_ENGINE_HYPERGOLIC.get(), ModBlocks.ROCKET_ENGINE_VACUUM.get(), ModBlocks.ROCKET_ION_THRUSTER.get(), ModBlocks.ROCKET_SOLAR_SAIL.get(), ModBlocks.ROCKET_TANK_TITANIUM.get(), ModBlocks.ROCKET_TANK_STAINLESS.get(), ModBlocks.ROCKET_MOTOR_CASING.get(), ModBlocks.ROCKET_INTERSTAGE.get(), ModBlocks.ROCKET_PAYLOAD_BAY.get(), ModBlocks.ROCKET_FAIRING.get(), ModBlocks.ROCKET_NOSE.get(), ModBlocks.CREW_CABIN.get(), ModBlocks.CABIN_ROOF.get(), ModBlocks.ESCAPE_TOWER.get(), ModBlocks.ROCKET_FUSELAGE.get(), ModBlocks.ROCKET_RACEWAY.get(), ModBlocks.FLIGHT_COMPUTER.get(),
                ModBlocks.DESIGN_COMPUTER.get(), ModBlocks.ENGINEER_WORKBENCH.get());
        for (Block part : rocketParts) {
            Identifier model = Orbitalis.id("block/machine/" + BuiltInRegistries.BLOCK.getKey(part).getPath());
            blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(part, BlockModelGenerators.plainVariant(model))
                    .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            // Les blocs des pièces (moteurs, réservoirs...) n'ont pas d'objet : c'est la pièce qui les pose.
            if (part.asItem() != net.minecraft.world.item.Items.AIR) {
                blockModels.registerSimpleItemModel(part, model);
            }
        }
        blockModels.createAirLikeBlock(ModBlocks.WORKSTATION_PART.get(), texture("machine_frame"));
        // Flanc de fusée et marche de tour : invisibles, ils ne donnent que des particules quand on les casse.
        blockModels.createAirLikeBlock(ModBlocks.ROCKET_HULL.get(), texture("rocket_white"));
        blockModels.createAirLikeBlock(ModBlocks.TOWER_STEP.get(), texture("pad_tread"));
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.STRUCTURE_PART.get())
                .with(PropertyDispatch.initial(StructurePartBlock.PART).generate(part -> BlockModelGenerators.plainVariant(
                        Orbitalis.id("block/machine/" + part.getSerializedName())))));
        // Grands postes de travail (pupitre de lancement, station de poursuite, ordinateur de conception) : modèles
        // 3D écrits par tools/generate_consoles.py. Les écrans muraux viennent de l'outil des machines.
        Identifier console = Orbitalis.id("block/machine/launch_console");
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.LAUNCH_CONSOLE.get(),
                BlockModelGenerators.plainVariant(console)).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        blockModels.registerSimpleItemModel(ModBlocks.LAUNCH_CONSOLE.get(), console);
        Identifier testConsole = Orbitalis.id("block/machine/test_console");
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.TEST_CONSOLE.get(),
                BlockModelGenerators.plainVariant(testConsole)).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        blockModels.registerSimpleItemModel(ModBlocks.TEST_CONSOLE.get(), testConsole);
        Identifier station = Orbitalis.id("block/machine/tracking_station");
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.TRACKING_STATION.get(),
                BlockModelGenerators.plainVariant(station)).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        blockModels.registerSimpleItemModel(ModBlocks.TRACKING_STATION.get(), station);
        missionDisplay(blockModels);
        launchTable(blockModels, ModBlocks.LAUNCH_TABLE.get());

        // Stations : coque, hublot et sas sont des cubes ; le panneau solaire a ses cellules sur le dessus ;
        // le générateur d'air change de face quand la pièce est pressurisée.
        // La coque : un panneau blanc, et çà et là, côté vide, sa couverture matelassée ou une main courante jaune.
        Block hull = ModBlocks.STATION_HULL.get();
        Identifier hullPlain = ModelTemplates.CUBE_ALL.create(hull, TextureMapping.cube(hull), blockModels.modelOutput);
        Identifier hullQuilted = ModelTemplates.CUBE_ALL.createWithSuffix(hull, "_quilted",
                TextureMapping.cube(TextureMapping.getBlockTexture(hull, "_quilted")), blockModels.modelOutput);
        Identifier hullRail = ModelTemplates.CUBE_ALL.createWithSuffix(hull, "_rail",
                TextureMapping.cube(TextureMapping.getBlockTexture(hull, "_rail")), blockModels.modelOutput);
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(hull, new MultiVariant(WeightedList.of(List.of(
                new Weighted<>(BlockModelGenerators.plainModel(hullPlain), 6),
                new Weighted<>(BlockModelGenerators.plainModel(hullQuilted), 3),
                new Weighted<>(BlockModelGenerators.plainModel(hullRail), 1))))));
        blockModels.registerSimpleItemModel(hull, hullPlain);
        blockModels.createTrivialCube(ModBlocks.MOON_REGOLITH.get());
        blockModels.createTrivialCube(ModBlocks.MOON_ROCK.get());
        blockModels.createTrivialCube(ModBlocks.MOON_ILMENITE_ORE.get());
        blockModels.createTrivialCube(ModBlocks.LUNAR_ICE.get());
        blockModels.createTrivialCube(ModBlocks.MARS_REGOLITH.get());
        blockModels.createTrivialCube(ModBlocks.MARS_ROCK.get());
        blockModels.createTrivialCube(ModBlocks.MARS_HEMATITE_ORE.get());
        blockModels.createTrivialCube(ModBlocks.TITAN_ICE.get());
        blockModels.createTrivialCube(ModBlocks.THOLIN_SAND.get());
        // Proxima b : ses roches et ses sols ; la mousse a son dessus, ses flancs frangés et, dessous, le régolithe.
        for (Block block : List.of(ModBlocks.PROXIMA_ROCK.get(), ModBlocks.PROXIMA_REGOLITH.get(), ModBlocks.PROXIMA_ASH.get(),
                ModBlocks.PROXIMA_FROST.get())) {
            blockModels.createTrivialCube(block);
        }
        blockModels.createTrivialBlock(ModBlocks.PROXIMA_MOSS.get(), net.minecraft.client.data.models.model.TexturedModel.CUBE_TOP_BOTTOM
                .updateTexture(mapping -> mapping.put(net.minecraft.client.data.models.model.TextureSlot.BOTTOM,
                        net.minecraft.client.data.models.model.TextureMapping.getBlockTexture(ModBlocks.PROXIMA_REGOLITH.get()))));
        for (Block plant : List.of(ModBlocks.RED_SEDGE.get(), ModBlocks.UMBRA_FERN.get(), ModBlocks.LANTERN_BULB.get())) {
            blockModels.createCrossBlockWithDefaultItem(plant, BlockModelGenerators.PlantType.NOT_TINTED);
        }
        blockModels.createTrivialCube(ModBlocks.EUROPA_ICE.get());
        blockModels.createTrivialCube(ModBlocks.EUROPA_LINEA_ICE.get());
        // Le méthane liquide se dessine comme un fluide : son bloc ne sert qu'à ses particules.
        blockModels.createAirLikeBlock(ModBlocks.LIQUID_METHANE.get(), texture("tholin_sand"));
        blockModels.createTrivialCube(ModBlocks.STATION_WINDOW.get());
        // Centres spatiaux.
        // Le béton coulé : trois panneaux, tirés au hasard d'un bloc à l'autre, pour que ses trous de banche ne se
        // répètent pas à l'identique ; la dalle et l'escalier prennent le premier.
        Block concrete = ModBlocks.FACILITY_CONCRETE.get();
        Identifier concrete0 = ModelTemplates.CUBE_ALL.create(concrete, TextureMapping.cube(concrete), blockModels.modelOutput);
        Identifier concrete1 = ModelTemplates.CUBE_ALL.createWithSuffix(concrete, "_1",
                TextureMapping.cube(TextureMapping.getBlockTexture(concrete, "_1")), blockModels.modelOutput);
        Identifier concrete2 = ModelTemplates.CUBE_ALL.createWithSuffix(concrete, "_2",
                TextureMapping.cube(TextureMapping.getBlockTexture(concrete, "_2")), blockModels.modelOutput);
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(concrete, new MultiVariant(WeightedList.of(List.of(
                new Weighted<>(BlockModelGenerators.plainModel(concrete0), 1),
                new Weighted<>(BlockModelGenerators.plainModel(concrete1), 1),
                new Weighted<>(BlockModelGenerators.plainModel(concrete2), 1))))));
        blockModels.registerSimpleItemModel(concrete, concrete0);
        blockModels.familyWithExistingFullBlock(concrete).slab(ModBlocks.FACILITY_CONCRETE_SLAB.get())
                .stairs(ModBlocks.FACILITY_CONCRETE_STAIRS.get());
        blockModels.createTrivialCube(ModBlocks.CRACKED_FACILITY_CONCRETE.get());
        blockModels.createTrivialCube(ModBlocks.FACILITY_TILES.get());
        blockModels.createTrivialCube(ModBlocks.HAZARD_STRIPES.get());
        blockModels.createTrivialCube(ModBlocks.LOUVERED_PANEL.get());
        blockModels.createTrivialCube(ModBlocks.RADOME_PANEL.get());
        blockModels.createTrivialCube(ModBlocks.FACILITY_GLASS.get());
        blockModels.createTrivialCube(ModBlocks.ANTENNA_MESH.get());
        blockModels.createTrivialBlock(ModBlocks.FACILITY_SIDING.get(), net.minecraft.client.data.models.model.TexturedModel.COLUMN);
        blockModels.createTrivialBlock(ModBlocks.BLUE_FACILITY_SIDING.get(), net.minecraft.client.data.models.model.TexturedModel.COLUMN);
        blockModels.createTrivialBlock(ModBlocks.RUSTY_FACILITY_SIDING.get(), net.minecraft.client.data.models.model.TexturedModel.COLUMN);
        blockModels.createTrivialBlock(ModBlocks.ORANGE_FACILITY_SIDING.get(), net.minecraft.client.data.models.model.TexturedModel.COLUMN);
        Identifier draftingEmpty = Orbitalis.id("block/facility/drafting_table");
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.DRAFTING_TABLE.get())
                .with(BlockModelGenerators.createBooleanModelDispatch(com.orbitalis.phase1.rocket.DraftingTableBlock.HAS_PLAN,
                        BlockModelGenerators.plainVariant(Orbitalis.id("block/facility/drafting_table_plan")), BlockModelGenerators.plainVariant(draftingEmpty)))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        blockModels.registerSimpleItemModel(ModBlocks.DRAFTING_TABLE.get(), draftingEmpty);
        for (Block block : List.of(ModBlocks.SERVER_RACK.get())) {
            Identifier model = Orbitalis.id("block/facility/" + BuiltInRegistries.BLOCK.getKey(block).getPath());
            blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block, BlockModelGenerators.plainVariant(model))
                    .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            blockModels.registerSimpleItemModel(block, model);
        }
        // Sas d'amarrage : un modèle 3D orientable, écrit par l'outil des machines.
        Identifier dock = Orbitalis.id("block/machine/docking_port");
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.DOCKING_PORT.get(),
                BlockModelGenerators.plainVariant(dock)).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        blockModels.registerSimpleItemModel(ModBlocks.DOCKING_PORT.get(), dock);
        // Panneau solaire : la nappe sur son mât, plus un cadre sur chaque côté qui n'est pas raccordé à un voisin.
        Block panel = ModBlocks.SOLAR_PANEL.get();
        MultiVariant panelCore = BlockModelGenerators.plainVariant(Orbitalis.id("block/machine/solar_panel"));
        MultiVariant panelEdge = BlockModelGenerators.plainVariant(Orbitalis.id("block/machine/solar_edge"));
        blockModels.blockStateOutput.accept(MultiPartGenerator.multiPart(panel)
                .with(panelCore)
                .with(BlockModelGenerators.condition().term(BlockStateProperties.NORTH, false), panelEdge)
                .with(BlockModelGenerators.condition().term(BlockStateProperties.EAST, false), panelEdge.with(BlockModelGenerators.Y_ROT_90))
                .with(BlockModelGenerators.condition().term(BlockStateProperties.SOUTH, false), panelEdge.with(BlockModelGenerators.Y_ROT_180))
                .with(BlockModelGenerators.condition().term(BlockStateProperties.WEST, false), panelEdge.with(BlockModelGenerators.Y_ROT_270)));
        blockModels.registerSimpleItemModel(panel, Orbitalis.id("block/machine/solar_panel"));
        // Générateur d'air : son modèle 3D, écrit par tools/generate_station.py ; en marche, ses ventilateurs tournent.
        Block air = ModBlocks.LIFE_SUPPORT.get();
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(air)
                .with(BlockModelGenerators.createBooleanModelDispatch(LifeSupportBlock.ACTIVE,
                        BlockModelGenerators.plainVariant(Orbitalis.id("block/station/life_support_on")),
                        BlockModelGenerators.plainVariant(Orbitalis.id("block/station/life_support")))));
        blockModels.registerSimpleItemModel(air, Orbitalis.id("block/station/life_support"));
        // Aménagement des stations : modèles 3D écrits par tools/generate_station.py. Le revêtement et le plafonnier
        // regardent la pièce ; le revêtement se décline en trois panneaux, tirés au hasard d'un bloc à l'autre.
        Identifier panelPlain = Orbitalis.id("block/station/station_panel");
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.STATION_PANEL.get(), new MultiVariant(WeightedList.of(List.of(
                        new Weighted<>(BlockModelGenerators.plainModel(panelPlain), 6),
                        new Weighted<>(BlockModelGenerators.plainModel(Orbitalis.id("block/station/station_panel_rail")), 3),
                        new Weighted<>(BlockModelGenerators.plainModel(Orbitalis.id("block/station/station_panel_vent")), 1)))))
                .with(BlockModelGenerators.ROTATION_FACING));
        blockModels.registerSimpleItemModel(ModBlocks.STATION_PANEL.get(), panelPlain);
        Identifier lightModel = Orbitalis.id("block/station/station_light");
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.STATION_LIGHT.get(), BlockModelGenerators.plainVariant(lightModel))
                .with(BlockModelGenerators.ROTATION_FACING));
        blockModels.registerSimpleItemModel(ModBlocks.STATION_LIGHT.get(), lightModel);
        for (Block block : List.of(ModBlocks.STATION_FLOOR.get(), ModBlocks.STATION_TRUSS.get())) {
            Identifier model = Orbitalis.id("block/station/" + BuiltInRegistries.BLOCK.getKey(block).getPath());
            blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block, BlockModelGenerators.plainVariant(model)));
            blockModels.registerSimpleItemModel(block, model);
        }
        // Établi de combinaison : deux moitiés ; l'objet montre l'établi entier, en relief.
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.SUIT_WORKBENCH.get())
                .with(PropertyDispatch.initial(com.orbitalis.phase2.survival.SuitWorkbenchBlock.RIGHT).generate(right ->
                        BlockModelGenerators.plainVariant(Orbitalis.id("block/station/suit_workbench_" + (right ? "right" : "left")))))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        blockModels.registerSimpleItemModel(ModBlocks.SUIT_WORKBENCH.get(), Orbitalis.id("block/station/suit_workbench_item"));
        // Quartiers d'équipage : une cabine de deux blocs de haut, son objet dessiné à plat.
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.CREW_BUNK.get())
                .with(PropertyDispatch.initial(CrewBunkBlock.HALF).generate(half -> BlockModelGenerators.plainVariant(
                        Orbitalis.id("block/station/crew_bunk_" + (half == DoubleBlockHalf.LOWER ? "lower" : "upper")))))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        // Le vaisseau interstellaire : ses meubles de deux blocs, son moteur d'anneau et son poste de pilotage, écrits par
        // tools/generate_ship.py. Le cœur de distorsion luit dès qu'il tient de la matière exotique.
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.WARP_CORE.get())
                .with(PropertyDispatch.initial(com.orbitalis.phase3.interstellar.TwoHighBlock.HALF, com.orbitalis.phase3.interstellar.WarpCoreBlock.CHARGE)
                        .generate((half, charge) -> BlockModelGenerators.plainVariant(Orbitalis.id("block/ship/warp_core_"
                                + (half == DoubleBlockHalf.LOWER ? "lower" : "upper") + (charge > 0 ? "_on" : "")))))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        blockModels.registerSimpleItemModel(ModBlocks.WARP_CORE.get(), Orbitalis.id("block/ship/warp_core_item"));
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.HIBERNATION_POD.get())
                .with(PropertyDispatch.initial(com.orbitalis.phase3.interstellar.TwoHighBlock.HALF).generate(half -> BlockModelGenerators.plainVariant(
                        Orbitalis.id("block/ship/hibernation_pod_" + (half == DoubleBlockHalf.LOWER ? "lower" : "upper")))))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        blockModels.registerSimpleItemModel(ModBlocks.HIBERNATION_POD.get(), Orbitalis.id("block/ship/hibernation_pod_item"));
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.RING_DRIVE.get(),
                BlockModelGenerators.plainVariant(Orbitalis.id("block/ship/ring_drive"))));
        blockModels.registerSimpleItemModel(ModBlocks.RING_DRIVE.get(), Orbitalis.id("block/ship/ring_drive_item"));
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.SHIP_HELM.get(),
                BlockModelGenerators.plainVariant(Orbitalis.id("block/ship/ship_helm"))).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        blockModels.registerSimpleItemModel(ModBlocks.SHIP_HELM.get(), Orbitalis.id("block/ship/ship_helm"));
        for (Block block : List.of(ModBlocks.STATION_TERMINAL.get(), ModBlocks.CARGO_RACK.get(), ModBlocks.SHIPYARD_CONSOLE.get())) {
            Identifier model = Orbitalis.id("block/station/" + BuiltInRegistries.BLOCK.getKey(block).getPath());
            blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block, BlockModelGenerators.plainVariant(model))
                    .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            blockModels.registerSimpleItemModel(block, model);
        }
        Identifier rackOff = Orbitalis.id("block/station/lab_rack");
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.LAB_RACK.get())
                .with(BlockModelGenerators.createBooleanModelDispatch(LabRackBlock.LIT,
                        BlockModelGenerators.plainVariant(Orbitalis.id("block/station/lab_rack_on")), BlockModelGenerators.plainVariant(rackOff)))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        blockModels.registerSimpleItemModel(ModBlocks.LAB_RACK.get(), rackOff);
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.HYDROPONIC_TRAY.get())
                .with(PropertyDispatch.initial(HydroponicTrayBlock.CROP, HydroponicTrayBlock.AGE).generate((crop, age) ->
                        BlockModelGenerators.plainVariant(Orbitalis.id(crop == HydroponicTrayBlock.Crop.NONE ? "block/station/hydroponic_tray"
                                : "block/station/hydroponic_tray_" + crop.getSerializedName() + "_" + age)))));
        blockModels.registerSimpleItemModel(ModBlocks.HYDROPONIC_TRAY.get(), Orbitalis.id("block/station/hydroponic_tray_wheat_3"));
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.STATION_BATTERY.get())
                .with(PropertyDispatch.initial(StationBatteryBlock.CHARGE).generate(charge ->
                        BlockModelGenerators.plainVariant(Orbitalis.id("block/station/station_battery_" + charge))))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        blockModels.registerSimpleItemModel(ModBlocks.STATION_BATTERY.get(), Orbitalis.id("block/station/station_battery_3"));
        blockModels.createDoor(ModBlocks.STATION_HATCH.get());
        // Porte de sas : un modèle par moitié, par position de l'écoutille et par couleur du voyant (rouge fermée, ambre en
        // cycle ou en fermeture, vert ouverte). Rien n'est dessiné en mouvement : chaque position est un modèle de bloc.
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.AIRLOCK_DOOR.get())
                .with(PropertyDispatch.initial(com.orbitalis.phase2.station.AirlockDoorBlock.HALF, com.orbitalis.phase2.station.AirlockDoorBlock.PHASE,
                                com.orbitalis.phase2.station.AirlockDoorBlock.STEP)
                        .generate((half, phase, step) -> BlockModelGenerators.plainVariant(Orbitalis.id("block/station/airlock_door_"
                                + (half == DoubleBlockHalf.LOWER ? "lower" : "upper") + "_" + step + "_"
                                + switch (phase) {
                                    case CLOSED -> "closed";
                                    case OPEN -> "open";
                                    default -> "cycling";
                                }))))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.AIRLOCK_LEAF.get())
                .with(BlockModelGenerators.createBooleanModelDispatch(com.orbitalis.phase2.station.AirlockLeafBlock.RIGHT,
                        BlockModelGenerators.plainVariant(Orbitalis.id("block/station/airlock_leaf_right")),
                        BlockModelGenerators.plainVariant(Orbitalis.id("block/station/airlock_leaf_left")))));
        // Porte vitrée, deux colonnes : le rail (en bas) et le linteau, avec sa moitié de détecteur (en haut) ; le
        // battant à part, que le rendu fait coulisser.
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.GLASS_DOOR.get())
                .with(PropertyDispatch.initial(com.orbitalis.phase2.station.GlassDoorBlock.HALF, com.orbitalis.phase2.station.GlassDoorBlock.RIGHT)
                        .generate((half, right) -> BlockModelGenerators.plainVariant(Orbitalis.id("block/station/glass_door_"
                                + (half == DoubleBlockHalf.LOWER ? "lower" : "upper_" + (right ? "right" : "left"))))))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.GLASS_DOOR_LEAF.get(),
                BlockModelGenerators.plainVariant(Orbitalis.id("block/station/glass_door_leaf"))));
        blockModels.createTrivialCube(ModBlocks.CUPOLA_WINDOW.get());
        // Machines ISRU : leurs modèles, tournés comme le joueur qui les pose ; le kit de base, dessiné à plat.
        for (Block isru : List.of(ModBlocks.ICE_DRILL.get(), ModBlocks.OXYGEN_EXTRACTOR.get(), ModBlocks.CRYOBOT.get())) {
            Identifier model = Orbitalis.id("block/exploration/" + BuiltInRegistries.BLOCK.getKey(isru).getPath());
            blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(isru, BlockModelGenerators.plainVariant(model))
                    .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            blockModels.registerSimpleItemModel(isru, model);
        }
        itemModels.generateFlatItem(ModItems.BASE_KIT.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.LUNAR_GATEWAY.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.LUNAR_SAMPLE_RETURN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.MARS_SAMPLE_RETURN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SAMPLE_CONTAINER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.LUNAR_SAMPLES.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.MARS_SAMPLES.get(), ModelTemplates.FLAT_ITEM);
        // Rover : son châssis et sa roue, portés par un bloc jamais posé ; l'objet est dessiné à plat.
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.ROVER_MODEL.get())
                .with(BlockModelGenerators.createBooleanModelDispatch(com.orbitalis.phase3.exploration.RoverModelBlock.WHEEL,
                        BlockModelGenerators.plainVariant(Orbitalis.id("block/exploration/rover_wheel")),
                        BlockModelGenerators.plainVariant(Orbitalis.id("block/exploration/rover")))));
        itemModels.generateFlatItem(ModItems.ROVER.get(), ModelTemplates.FLAT_ITEM);
        // Drone volant : son corps et son rotor, écrits par tools/generate_drone.py, portés par un bloc jamais posé.
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.ROTOR_DRONE_MODEL.get())
                .with(BlockModelGenerators.createBooleanModelDispatch(com.orbitalis.phase3.exploration.RotorDroneModelBlock.ROTOR,
                        BlockModelGenerators.plainVariant(Orbitalis.id("block/exploration/rotor_drone_rotor")),
                        BlockModelGenerators.plainVariant(Orbitalis.id("block/exploration/rotor_drone")))));
        itemModels.generateFlatItem(ModItems.ROTOR_DRONE.get(), ModelTemplates.FLAT_ITEM);
        // Atterrisseur robotique : un modèle écrit par tools/generate_exploration.py.
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.ROBOTIC_LANDER.get(),
                BlockModelGenerators.plainVariant(Orbitalis.id("block/exploration/robotic_lander"))));
        // Fusée dressée sur la table : un modèle par tronçon et par diamètre, écrits par l'outil de textures.
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.ROCKET.get())
                .with(PropertyDispatch.initial(RocketBlock.PART, RocketBlock.SIZE).generate((part, size) ->
                        BlockModelGenerators.plainVariant(Orbitalis.id("block/rocket/" + part.getSerializedName() + "_" + size.getSerializedName())))));

        // Conduits : un cœur, plus un bras par raccordement.
        conduit(blockModels, ModBlocks.ENERGY_CABLE.get(), "energy_cable", ConduitBlock.MAX_GLOW + 1);
        conduit(blockModels, ModBlocks.FLUID_PIPE.get(), "fluid_pipe", 5);
        conduit(blockModels, ModBlocks.GAS_PIPE.get(), "gas_pipe", 3);
        conduit(blockModels, ModBlocks.DATA_CABLE.get(), "data_cable", 1);

        // Items plats (une seule texture).
        itemModels.generateFlatItem(ModItems.GUIDE_BOOK.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.PAD_KIT.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ROCKET_BLUEPRINT.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.RAW_BAUXITE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.RAW_TITANIUM.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.RAW_LITHIUM.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.STEEL_INGOT.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ALUMINUM_INGOT.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.TITANIUM_INGOT.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.LITHIUM_INGOT.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ALUMINUM_LITHIUM_INGOT.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.STEEL_PLATE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ALUMINUM_PLATE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.CIRCUIT_BOARD.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.FLIGHT_PROCESSOR.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.RADIO_TRANSMITTER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SOLAR_CELL.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ELECTRIC_MOTOR.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.STEEL_GRATING.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.INSULATION_BLANKET.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.HEATING_ELEMENT.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.NAVIGATION_UNIT.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.OPTICAL_SENSOR.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.REINFORCED_GLASS.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.DISPLAY_SCREEN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.LED_LAMP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.HEAT_SHIELD_TILE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.BATTERY_CELL.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.CO2_FILTER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.TITANIUM_PLATE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ALUMINUM_LITHIUM_PLATE.get(), ModelTemplates.FLAT_ITEM);

        itemModels.generateFlatItem(ModItems.CREATIVE_RESEARCH_DOSSIER.get(), ModelTemplates.FLAT_ITEM);

        // Pièces de fusée et fusée assemblée.
        itemModels.generateFlatItem(ModItems.ROCKET.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SOLID_MOTOR.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.PRESSURE_FED_ENGINE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.KEROLOX_ENGINE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.HYDROLOX_ENGINE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SOLID_BOOSTER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.HYPERGOLIC_ENGINE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.VACUUM_ENGINE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ION_THRUSTER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.NUCLEAR_THERMAL_ENGINE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.FUSION_ENGINE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.EXOTIC_MATTER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.REACTOR_CORE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SOLAR_SAIL.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.TITANIUM_TANK.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.STAINLESS_TANK.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.HYDROGEN_TANK.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.STEEL_TANK.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ALUMINUM_TANK.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ALUMINUM_LITHIUM_TANK.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.INTERSTAGE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.NOSE_CONE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SOUNDING_PAYLOAD.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.CREW_CAPSULE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.CREW_CAPSULE_MK2.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.CREW_CAPSULE_MK3.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SPACESUIT.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SPACE_HELMET.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SPACE_LEGGINGS.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SPACE_BOOTS.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ADVANCED_SPACE_BOOTS.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ADVANCED_SPACE_HELMET.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ADVANCED_SPACESUIT.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.ADVANCED_SPACE_LEGGINGS.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SUIT_MODULE_MAP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SUIT_MODULE_SENSOR.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SUIT_MODULE_OXYGEN_TANK.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SUIT_MODULE_THRUSTERS.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SUIT_MODULE_SHOCK_ABSORBERS.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SUIT_MODULE_RADIATION_SHIELD.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.RECOVERY_REMOTE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.MISSION_TABLET.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.STATION_CORE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.HABITATION_MODULE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.LABORATORY_MODULE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.GREENHOUSE_MODULE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.CARGO_MODULE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.CUPOLA_MODULE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.AIRLOCK_MODULE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.POWER_MODULE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SHIPYARD_MODULE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.AIRLOCK_DOOR.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.GLASS_DOOR.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModBlocks.CREW_BUNK.get().asItem(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.LUNAR_MODULE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.MARS_LANDER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.TITAN_LANDER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.EUROPA_LANDER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.LIQUID_METHANE_BUCKET.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.LUNAR_PROBE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.MARS_PROBE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.VENUS_ORBITER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.VENUS_LANDER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.MERCURY_ORBITER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.JUPITER_ORBITER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.JUPITER_ENTRY_PROBE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SATURN_ORBITER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.TITAN_ENTRY_PROBE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.EUROPA_ORBITER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.LUNAR_ROBOTIC_LANDER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.MARS_ROBOTIC_LANDER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.STAGED_COMBUSTION_ENGINE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.METHALOX_ENGINE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.COMPOSITE_TANK.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.TEST_SATELLITE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.WEATHER_SATELLITE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.COMMUNICATION_SATELLITE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.OBSERVATION_SATELLITE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.NAVIGATION_SATELLITE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.SPACE_TELESCOPE.get(), ModelTemplates.FLAT_ITEM);
    }

    /**
     * Machine : deux modèles 3D propres à chaque machine, à l'arrêt et en marche, écrits par
     * {@code tools/generate_machine_models.py}. En marche, certaines pièces sont animées et lumineuses.
     */
    private static void machine(BlockModelGenerators blockModels, Block block) {
        String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
        Identifier off = Orbitalis.id("block/machine/" + name);
        Identifier on = Orbitalis.id("block/machine/" + name + "_on");

        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block)
                .with(BlockModelGenerators.createBooleanModelDispatch(BlockStateProperties.LIT,
                        BlockModelGenerators.plainVariant(on), BlockModelGenerators.plainVariant(off)))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        blockModels.registerSimpleItemModel(block, off);
    }

    /**
     * Dalle de pas de tir : un aspect par rôle dans la structure. Seul le dessus change, sauf pour la bordure,
     * dont les côtés portent aussi une bande de sécurité.
     */
    private static void launchPad(BlockModelGenerators blockModels, Block block) {
        Material plain = TextureMapping.getBlockTexture(block);
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block)
                .with(PropertyDispatch.initial(LaunchPadBlock.LOOK).generate(look -> {
                    if (look == LaunchPadBlock.Look.PLAIN) {
                        return BlockModelGenerators.plainVariant(
                                ModelTemplates.CUBE_ALL.create(block, TextureMapping.cube(plain), blockModels.modelOutput));
                    }
                    String suffix = "_" + look.getSerializedName();
                    TextureMapping textures = new TextureMapping()
                            .put(TextureSlot.TOP, TextureMapping.getBlockTexture(block, suffix))
                            .put(TextureSlot.SIDE, look == LaunchPadBlock.Look.BORDER ? TextureMapping.getBlockTexture(block, "_border_side") : plain)
                            .put(TextureSlot.BOTTOM, plain);
                    return BlockModelGenerators.plainVariant(
                            ModelTemplates.CUBE_BOTTOM_TOP.createWithSuffix(block, suffix, textures, blockModels.modelOutput));
                })));
    }

    /** Table de lancement : modèle 3D du kit du pas de tir, dont les voyants s'allument quand le complexe est opérationnel. */
    private static void launchTable(BlockModelGenerators blockModels, Block block) {
        MultiVariant off = BlockModelGenerators.plainVariant(Orbitalis.id("block/machine/launch_table"));
        MultiVariant on = BlockModelGenerators.plainVariant(Orbitalis.id("block/machine/launch_table_on"));
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block)
                .with(BlockModelGenerators.createBooleanModelDispatch(LaunchTableBlock.FORMED, on, off)));
        blockModels.registerSimpleItemModel(block, Orbitalis.id("block/machine/launch_table"));
    }

    /** Bloc orientable à face avant unique (pas d'état allumé ou éteint). */
    /**
     * Conduit. Les modèles sont écrits par l'outil des conduits, un jeu par variante : niveau de charge pour le
     * câble, contenu pour les tuyaux (0 quand ils sont vides).
     * <p>
     * Au milieu, un nœud ; ou, si le conduit ne fait que passer tout droit, un simple tronçon de tube. De chaque
     * côté raccordé, un bras : nu vers un autre conduit, terminé par un raccord vers une machine. Le raccord d'un
     * tuyau en extraction a sa propre couleur.
     */
    private static void conduit(BlockModelGenerators blockModels, ConduitBlock block, String name, int variants) {
        Property<Integer> property = switch (block.kind()) {
            case ENERGY -> ConduitBlock.GLOW;
            case DATA -> null;
            default -> ConduitBlock.CONTENT;
        };
        boolean pulls = block.kind().isFluid();
        // Le nœud : dès que le conduit tourne ou se divise (deux axes raccordés), ou qu'il n'est traversé de part
        // en part sur aucun axe (bout de ligne, conduit isolé).
        Condition node = any(
                all(linked(ConduitBlock.NORTH, ConduitBlock.SOUTH), linked(ConduitBlock.EAST, ConduitBlock.WEST)),
                all(linked(ConduitBlock.NORTH, ConduitBlock.SOUTH), linked(ConduitBlock.UP, ConduitBlock.DOWN)),
                all(linked(ConduitBlock.EAST, ConduitBlock.WEST), linked(ConduitBlock.UP, ConduitBlock.DOWN)),
                all(open(ConduitBlock.NORTH, ConduitBlock.SOUTH), open(ConduitBlock.EAST, ConduitBlock.WEST),
                        open(ConduitBlock.UP, ConduitBlock.DOWN)));
        Map<Direction, VariantMutator> turns = Map.of(Direction.EAST, BlockModelGenerators.Y_ROT_90,
                Direction.SOUTH, BlockModelGenerators.Y_ROT_180, Direction.WEST, BlockModelGenerators.Y_ROT_270,
                Direction.UP, BlockModelGenerators.X_ROT_270, Direction.DOWN, BlockModelGenerators.X_ROT_90);
        MultiPartGenerator generator = MultiPartGenerator.multiPart(block);
        for (int variant = 0; variant < variants; variant++) {
            int value = variant;
            Supplier<ConditionBuilder> when = () -> property == null ? BlockModelGenerators.condition()
                    : BlockModelGenerators.condition().term(property, value);
            String prefix = "block/conduit/" + name + "_";
            MultiVariant core = BlockModelGenerators.plainVariant(Orbitalis.id(prefix + "core_" + variant));
            MultiVariant straight = BlockModelGenerators.plainVariant(Orbitalis.id(prefix + "straight_" + variant));
            MultiVariant arm = BlockModelGenerators.plainVariant(Orbitalis.id(prefix + "arm_" + variant));
            MultiVariant link = BlockModelGenerators.plainVariant(Orbitalis.id(prefix + "link_" + variant));
            generator = generator
                    .with(property == null ? node : all(when.get().build(), node), core)
                    .with(through(when.get(), Direction.Axis.Z), straight)
                    .with(through(when.get(), Direction.Axis.X), straight.with(BlockModelGenerators.Y_ROT_90))
                    .with(through(when.get(), Direction.Axis.Y), straight.with(BlockModelGenerators.X_ROT_90));
            for (Direction direction : Direction.values()) {
                EnumProperty<ConduitBlock.Link> side = ConduitBlock.LINKS.get(direction);
                VariantMutator turn = turns.get(direction);
                generator = generator.with(when.get().term(side, ConduitBlock.Link.CONDUIT), turn == null ? arm : arm.with(turn));
                if (pulls) {
                    MultiVariant pull = BlockModelGenerators.plainVariant(Orbitalis.id(prefix + "pull_" + variant));
                    generator = generator
                            .with(when.get().term(side, ConduitBlock.Link.MACHINE).term(ConduitBlock.PULL, false),
                                    turn == null ? link : link.with(turn))
                            .with(when.get().term(side, ConduitBlock.Link.MACHINE).term(ConduitBlock.PULL, true),
                                    turn == null ? pull : pull.with(turn));
                } else {
                    generator = generator.with(when.get().term(side, ConduitBlock.Link.MACHINE), turn == null ? link : link.with(turn));
                }
            }
        }
        blockModels.blockStateOutput.accept(generator);

        // Dans l'inventaire : un tronçon droit.
        blockModels.registerSimpleItemModel(block, Orbitalis.id("block/conduit/" + name + "_inventory"));
    }

    /**
     * Écran de contrôle : une dalle mince, toujours la même, et un liseré seulement sur les côtés où l'écran
     * s'arrête. Les morceaux sont dessinés pour une dalle tournée vers le nord par tools/generate_display_models.py ;
     * vu de face, la droite de l'écran est alors à l'ouest.
     */
    private static void missionDisplay(BlockModelGenerators blockModels) {
        Block block = ModBlocks.MISSION_DISPLAY.get();
        java.util.function.Function<String, MultiVariant> piece =
                name -> BlockModelGenerators.plainVariant(Orbitalis.id("block/display/mission_display_" + name));
        Map<Direction, VariantMutator> turns = Map.of(Direction.EAST, BlockModelGenerators.Y_ROT_90,
                Direction.SOUTH, BlockModelGenerators.Y_ROT_180, Direction.WEST, BlockModelGenerators.Y_ROT_270);
        MultiPartGenerator generator = MultiPartGenerator.multiPart(block);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VariantMutator turn = turns.get(facing);
            java.util.function.Function<String, MultiVariant> turned = name -> turn == null ? piece.apply(name) : piece.apply(name).with(turn);
            Supplier<ConditionBuilder> when = () -> BlockModelGenerators.condition().term(MissionDisplayBlock.FACING, facing);
            generator = generator
                    .with(when.get(), turned.apply("panel"))
                    .with(when.get().term(MissionDisplayBlock.UP, false), turned.apply("edge_up"))
                    .with(when.get().term(MissionDisplayBlock.DOWN, false), turned.apply("edge_down"))
                    .with(when.get().term(MissionDisplayBlock.LEFT, false), turned.apply("edge_left"))
                    .with(when.get().term(MissionDisplayBlock.RIGHT, false), turned.apply("edge_right"));
            // Un angle du liseré dès que l'écran s'arrête sur l'un de ses deux côtés.
            Object[][] corners = {{"corner_up_left", MissionDisplayBlock.UP, MissionDisplayBlock.LEFT},
                    {"corner_up_right", MissionDisplayBlock.UP, MissionDisplayBlock.RIGHT},
                    {"corner_down_left", MissionDisplayBlock.DOWN, MissionDisplayBlock.LEFT},
                    {"corner_down_right", MissionDisplayBlock.DOWN, MissionDisplayBlock.RIGHT}};
            for (Object[] corner : corners) {
                generator = generator.with(all(when.get().build(), any(
                        BlockModelGenerators.condition().term((BooleanProperty) corner[1], false).build(),
                        BlockModelGenerators.condition().term((BooleanProperty) corner[2], false).build())), turned.apply((String) corner[0]));
            }
        }
        blockModels.blockStateOutput.accept(generator);
        blockModels.registerSimpleItemModel(block, Orbitalis.id("block/display/mission_display_inventory"));
    }

    /**
     * Réservoir modulable : chaque bloc ne montre que ce qui est au bord du réservoir. Une paroi par côté libre, un
     * fond et un couvercle, une arête là où deux faces libres se rencontrent, un coin là où des arêtes se rejoignent.
     * Les morceaux sont dessinés côté nord (ou au coin nord-ouest) par tools/generate_tank_models.py.
     */
    private static void fluidTank(BlockModelGenerators blockModels) {
        Block block = ModBlocks.FLUID_TANK.get();
        java.util.function.Function<String, MultiVariant> piece =
                name -> BlockModelGenerators.plainVariant(Orbitalis.id("block/tank/fluid_tank_" + name));
        BooleanProperty up = FluidTankBlock.JOINED.get(Direction.UP);
        BooleanProperty down = FluidTankBlock.JOINED.get(Direction.DOWN);
        Direction[] sides = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        VariantMutator[] turns = {null, BlockModelGenerators.Y_ROT_90, BlockModelGenerators.Y_ROT_180, BlockModelGenerators.Y_ROT_270};
        MultiPartGenerator generator = MultiPartGenerator.multiPart(block)
                .with(BlockModelGenerators.condition().term(up, false), piece.apply("top"))
                .with(BlockModelGenerators.condition().term(down, false), piece.apply("bottom"));
        for (int i = 0; i < sides.length; i++) {
            VariantMutator turn = turns[i];
            java.util.function.Function<String, MultiVariant> turned = name -> turn == null ? piece.apply(name) : piece.apply(name).with(turn);
            // Le coin dessiné au nord-ouest se retrouve, une fois tourné, entre ce côté et celui qui le précède.
            BooleanProperty side = FluidTankBlock.JOINED.get(sides[i]);
            BooleanProperty before = FluidTankBlock.JOINED.get(sides[(i + 3) % 4]);
            generator = generator
                    .with(BlockModelGenerators.condition().term(side, false), turned.apply("wall"))
                    .with(free(side, before), turned.apply("post"))
                    .with(free(side, up), turned.apply("rail_top"))
                    .with(free(side, down), turned.apply("rail_bottom"))
                    .with(any(free(side, before).build(), free(side, up).build(), free(before, up).build()), turned.apply("corner_top"))
                    .with(any(free(side, before).build(), free(side, down).build(), free(before, down).build()), turned.apply("corner_bottom"));
        }
        blockModels.blockStateOutput.accept(generator);
        blockModels.registerSimpleItemModel(block, Orbitalis.id("block/tank/fluid_tank_inventory"));
    }

    /** Vrai si le réservoir s'arrête de ces deux côtés à la fois. */
    private static ConditionBuilder free(BooleanProperty a, BooleanProperty b) {
        return BlockModelGenerators.condition().term(a, false).term(b, false);
    }

    /** Vrai si l'un des deux côtés au moins est raccordé. */
    private static Condition linked(EnumProperty<ConduitBlock.Link> a, EnumProperty<ConduitBlock.Link> b) {
        return any(BlockModelGenerators.condition().negatedTerm(a, ConduitBlock.Link.NONE).build(),
                BlockModelGenerators.condition().negatedTerm(b, ConduitBlock.Link.NONE).build());
    }

    /** Vrai si l'un des deux côtés au moins est libre. */
    private static Condition open(EnumProperty<ConduitBlock.Link> a, EnumProperty<ConduitBlock.Link> b) {
        return any(BlockModelGenerators.condition().term(a, ConduitBlock.Link.NONE).build(),
                BlockModelGenerators.condition().term(b, ConduitBlock.Link.NONE).build());
    }

    /** Conduit traversé de part en part le long de cet axe, et raccordé nulle part ailleurs. */
    private static ConditionBuilder through(ConditionBuilder when, Direction.Axis axis) {
        for (Direction direction : Direction.values()) {
            EnumProperty<ConduitBlock.Link> side = ConduitBlock.LINKS.get(direction);
            when = direction.getAxis() == axis ? when.negatedTerm(side, ConduitBlock.Link.NONE) : when.term(side, ConduitBlock.Link.NONE);
        }
        return when;
    }

    private static Condition any(Condition... terms) {
        return new CombinedCondition(CombinedCondition.Operation.OR, List.of(terms));
    }

    private static Condition all(Condition... terms) {
        return new CombinedCondition(CombinedCondition.Operation.AND, List.of(terms));
    }

    private static Material texture(String name) {
        return new Material(Orbitalis.id("block/" + name), false);
    }
}
