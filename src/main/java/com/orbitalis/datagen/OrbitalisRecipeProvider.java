package com.orbitalis.datagen;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.orbitalis.Orbitalis;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.registry.ModFluids;
import com.orbitalis.core.registry.ModItems;
import com.orbitalis.core.registry.ModRecipes;
import com.orbitalis.core.registry.ModTags;
import com.orbitalis.phase1.fuel.recipe.FluidRecipe;
import com.orbitalis.phase1.machines.recipe.MachineRecipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStackTemplate;

/**
 * Génère les recettes du mod.
 */
public class OrbitalisRecipeProvider extends RecipeProvider {
    protected OrbitalisRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
        super(registries, output);
    }

    @Override
    protected void buildRecipes() {
        // Minerais et minerais bruts -> lingots (four et haut fourneau).
        smeltOre("aluminum_ingot", ModItems.ALUMINUM_INGOT, 0.7F,
                ModItems.RAW_BAUXITE, ModBlocks.BAUXITE_ORE, ModBlocks.DEEPSLATE_BAUXITE_ORE);
        smeltOre("titanium_ingot", ModItems.TITANIUM_INGOT, 1.0F,
                ModItems.RAW_TITANIUM, ModBlocks.TITANIUM_ORE, ModBlocks.DEEPSLATE_TITANIUM_ORE);
        smeltOre("lithium_ingot", ModItems.LITHIUM_INGOT, 0.7F,
                ModItems.RAW_LITHIUM, ModBlocks.LITHIUM_ORE, ModBlocks.DEEPSLATE_LITHIUM_ORE);

        // Blocs de métal <-> 9 lingots.
        storageBlock("steel", ModItems.STEEL_INGOT, ModBlocks.STEEL_BLOCK);
        storageBlock("aluminum", ModItems.ALUMINUM_INGOT, ModBlocks.ALUMINUM_BLOCK);
        storageBlock("titanium", ModItems.TITANIUM_INGOT, ModBlocks.TITANIUM_BLOCK);

        // Manuel : un livre et un lingot de fer.
        shapeless(RecipeCategory.MISC, ModItems.GUIDE_BOOK)
                .requires(Items.BOOK)
                .requires(Tags.Items.INGOTS_IRON)
                .unlockedBy("has_book", has(Items.BOOK))
                .save(output, key("guide_book"));

        buildMachineCrafting();
        buildAlloying();
        buildRolling();
        buildFuelCrafting();
        buildFuels();
        buildConduits();
    }

    /** Complexe de lancement : dalles, table, tour de service, bras ombilical et pupitre. */
    private void buildSpaceport() {
        shaped(RecipeCategory.MISC, ModBlocks.LAUNCH_PAD, 8)
                .pattern("CCC")
                .pattern("CPC")
                .pattern("CCC")
                .define('C', Items.SMOOTH_STONE)
                .define('P', ModTags.Items.PLATES_STEEL)
                .unlockedBy("has_steel_plate", has(ModTags.Items.PLATES_STEEL))
                .save(output, key("launch_pad"));

        // Équipements des grands pas de tir.
        shaped(RecipeCategory.MISC, ModBlocks.FLAME_DEFLECTOR)
                .pattern("PPP")
                .pattern("PIP")
                .pattern("BBB")
                .define('P', ModTags.Items.PLATES_STEEL)
                .define('I', Items.IRON_BLOCK)
                .define('B', ModItems.HEAT_SHIELD_TILE)
                .unlockedBy("has_steel_plate", has(ModTags.Items.PLATES_STEEL))
                .save(output, key("flame_deflector"));

        shaped(RecipeCategory.MISC, ModBlocks.FLAME_TRENCH, 4)
                .pattern("I I")
                .pattern("PPP")
                .pattern("I I")
                .define('I', ModItems.STEEL_GRATING)
                .define('P', ModTags.Items.PLATES_STEEL)
                .unlockedBy("has_steel_plate", has(ModTags.Items.PLATES_STEEL))
                .save(output, key("flame_trench"));

        // Les débris d'un accident se refondent : c'est de l'acier.
        smeltOre("steel_ingot_from_debris", ModItems.STEEL_INGOT, 0.1F, ModBlocks.ROCKET_DEBRIS);

        shaped(RecipeCategory.MISC, ModBlocks.LIGHTNING_MAST, 4)
                .pattern("C")
                .pattern("S")
                .pattern("S")
                .define('C', Tags.Items.INGOTS_COPPER)
                .define('S', ModTags.Items.INGOTS_STEEL)
                .unlockedBy("has_steel_ingot", has(ModTags.Items.INGOTS_STEEL))
                .save(output, key("lightning_mast"));
        shaped(RecipeCategory.MISC, ModBlocks.PAD_FLOODLIGHT)
                .pattern("GLG")
                .pattern("PMP")
                .define('G', ModItems.REINFORCED_GLASS)
                .define('L', ModItems.LED_LAMP)
                .define('P', ModTags.Items.PLATES_STEEL)
                .define('M', ModBlocks.LIGHTNING_MAST)
                .unlockedBy("has_lightning_mast", has(ModBlocks.LIGHTNING_MAST))
                .save(output, key("pad_floodlight"));

        shaped(RecipeCategory.MISC, ModBlocks.LAUNCH_TABLE)
                .pattern("IPI")
                .pattern("PRP")
                .pattern("PPP")
                .define('I', ModItems.STEEL_GRATING)
                .define('P', ModTags.Items.PLATES_STEEL)
                .define('R', ModItems.FLIGHT_PROCESSOR)
                .unlockedBy("has_steel_plate", has(ModTags.Items.PLATES_STEEL))
                .save(output, key("launch_table"));

        // Tour de service. Une tour compte six blocs de charpente par étage : ils se fabriquent par seize.
        shaped(RecipeCategory.MISC, ModBlocks.TOWER_FRAME, 16)
                .pattern("S S")
                .pattern(" S ")
                .pattern("S S")
                .define('S', ModTags.Items.INGOTS_STEEL)
                .unlockedBy("has_steel_ingot", has(ModTags.Items.INGOTS_STEEL))
                .save(output, key("tower_frame"));
        // Colonne technique : de la charpente traversée par une conduite d'ergols et un câble.
        shaped(RecipeCategory.MISC, ModBlocks.SERVICE_TOWER, 4)
                .pattern("FPF")
                .pattern("FCF")
                .define('F', ModBlocks.TOWER_FRAME)
                .define('P', ModBlocks.FLUID_PIPE)
                .define('C', ModBlocks.ENERGY_CABLE)
                .unlockedBy("has_tower_frame", has(ModBlocks.TOWER_FRAME))
                .save(output, key("service_tower"));
        shaped(RecipeCategory.MISC, ModBlocks.BOARDING_GATE)
                .pattern("FPF")
                .pattern("F F")
                .pattern("FPF")
                .define('F', ModBlocks.TOWER_FRAME)
                .define('P', ModTags.Items.PLATES_STEEL)
                .unlockedBy("has_tower_frame", has(ModBlocks.TOWER_FRAME))
                .save(output, key("boarding_gate"));
        shaped(RecipeCategory.MISC, ModBlocks.TOWER_ELEVATOR)
                .pattern("PPP")
                .pattern("IRI")
                .pattern("PPP")
                .define('P', ModTags.Items.PLATES_STEEL)
                .define('I', ModItems.STEEL_GRATING)
                .define('R', ModItems.FLIGHT_PROCESSOR)
                .unlockedBy("has_tower_frame", has(ModBlocks.TOWER_FRAME))
                .save(output, key("tower_elevator"));

        // Kit de pas de tir : la table de lancement et son plan.
        shaped(RecipeCategory.MISC, ModItems.PAD_KIT)
                .pattern("P")
                .pattern("T")
                .define('P', Items.PAPER)
                .define('T', ModBlocks.LAUNCH_TABLE)
                .unlockedBy("has_launch_table", has(ModBlocks.LAUNCH_TABLE))
                .save(output, key("pad_kit"));
        shaped(RecipeCategory.MISC, ModBlocks.TOWER_STAIRS, 4)
                .pattern("P  ")
                .pattern("FP ")
                .pattern(" FP")
                .define('P', ModTags.Items.PLATES_STEEL)
                .define('F', ModBlocks.TOWER_FRAME)
                .unlockedBy("has_tower_frame", has(ModBlocks.TOWER_FRAME))
                .save(output, key("tower_stairs"));
        shaped(RecipeCategory.MISC, ModBlocks.TOWER_LANDING, 6)
                .pattern("PPP")
                .pattern("F F")
                .define('P', ModTags.Items.PLATES_STEEL)
                .define('F', ModBlocks.TOWER_FRAME)
                .unlockedBy("has_tower_frame", has(ModBlocks.TOWER_FRAME))
                .save(output, key("tower_landing"));
        shaped(RecipeCategory.MISC, ModBlocks.TOWER_DECK, 6)
                .pattern("PPP")
                .pattern("FFF")
                .define('P', ModTags.Items.PLATES_STEEL)
                .define('F', ModBlocks.TOWER_FRAME)
                .unlockedBy("has_tower_frame", has(ModBlocks.TOWER_FRAME))
                .save(output, key("tower_deck"));
        shaped(RecipeCategory.MISC, ModBlocks.TOWER_RAILING, 6)
                .pattern("I I")
                .pattern("I I")
                .pattern("PPP")
                .define('P', ModTags.Items.PLATES_STEEL)
                .define('I', ModItems.STEEL_GRATING)
                .unlockedBy("has_tower_frame", has(ModBlocks.TOWER_FRAME))
                .save(output, key("tower_railing"));
        shaped(RecipeCategory.MISC, ModBlocks.PAD_SUPPORT, 4)
                .pattern("P")
                .pattern("B")
                .pattern("B")
                .define('P', ModTags.Items.PLATES_STEEL)
                .define('B', Items.STONE_BRICKS)
                .unlockedBy("has_launch_pad", has(ModBlocks.LAUNCH_PAD))
                .save(output, key("pad_support"));
        shaped(RecipeCategory.MISC, ModBlocks.DELUGE_PIPE, 4)
                .pattern(" I ")
                .pattern("PWP")
                .define('I', ModItems.STEEL_GRATING)
                .define('P', ModBlocks.FLUID_PIPE)
                .define('W', Items.WATER_BUCKET)
                .unlockedBy("has_fluid_pipe", has(ModBlocks.FLUID_PIPE))
                .save(output, key("deluge_pipe"));
        shaped(RecipeCategory.MISC, ModBlocks.FUEL_PORT)
                .pattern("SPS")
                .pattern("PFP")
                .pattern("SPS")
                .define('S', ModTags.Items.PLATES_STEEL)
                .define('P', ModBlocks.FLUID_PIPE)
                .define('F', ModBlocks.TOWER_FRAME)
                .unlockedBy("has_tower_frame", has(ModBlocks.TOWER_FRAME))
                .save(output, key("fuel_port"));

        shaped(RecipeCategory.MISC, ModBlocks.UMBILICAL_ARM)
                .pattern("TFC")
                .define('T', ModBlocks.SERVICE_TOWER)
                .define('F', ModBlocks.FLUID_PIPE)
                .define('C', ModBlocks.ENERGY_CABLE)
                .unlockedBy("has_service_tower", has(ModBlocks.SERVICE_TOWER))
                .save(output, key("umbilical_arm"));

        // Câble de données : de l'aluminium autour d'un fil de redstone.
        shaped(RecipeCategory.MISC, ModBlocks.DATA_CABLE, 4)
                .pattern("ARA")
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .unlockedBy("has_aluminum_plate", has(ModTags.Items.PLATES_ALUMINUM))
                .save(output, key("data_cable"));
        // Réservoir modulable : une virole de tôles d'acier, deux regards vitrés.
        shaped(RecipeCategory.MISC, ModBlocks.FLUID_TANK, 2)
                .pattern("SGS")
                .pattern("S S")
                .pattern("SGS")
                .define('S', ModTags.Items.PLATES_STEEL)
                .define('G', Tags.Items.GLASS_BLOCKS)
                .unlockedBy("has_steel_plate", has(ModTags.Items.PLATES_STEEL))
                .save(output, key("fluid_tank"));

        // Écran mural du centre de contrôle, fabriqué par deux.
        shaped(RecipeCategory.MISC, ModBlocks.MISSION_DISPLAY, 2)
                .pattern("AAA")
                .pattern("GRG")
                .pattern("AAA")
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('G', ModItems.DISPLAY_SCREEN)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .unlockedBy("has_aluminum_plate", has(ModTags.Items.PLATES_ALUMINUM))
                .save(output, key("mission_display"));

        shaped(RecipeCategory.MISC, ModBlocks.LAUNCH_CONSOLE)
                .pattern("AGA")
                .pattern("RLR")
                .pattern("AAA")
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('G', ModItems.DISPLAY_SCREEN)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .define('L', ModItems.CIRCUIT_BOARD)
                .unlockedBy("has_aluminum_plate", has(ModTags.Items.PLATES_ALUMINUM))
                .save(output, key("launch_console"));
    }

    /** Atelier d'assemblage et pièces de fusée. */
    /** Stations spatiales : blocs de construction et module central. */
    private void buildStations() {
        // Glace lunaire : fondue dans un seau, elle donne de l'eau, que l'électrolyseur change en ergols.
        shapeless(RecipeCategory.MISC, Items.WATER_BUCKET)
                .requires(ModBlocks.LUNAR_ICE)
                .requires(Items.BUCKET)
                .unlockedBy("has_lunar_ice", has(ModBlocks.LUNAR_ICE))
                .save(output, key("water_from_lunar_ice"));

        // Pièces des lanceurs lourds et vaisseau lunaire.
        shaped(RecipeCategory.MISC, ModItems.STAGED_COMBUSTION_ENGINE)
                .pattern("TET")
                .pattern("TET")
                .pattern(" N ")
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('E', ModItems.KEROLOX_ENGINE)
                .define('N', Items.NETHERITE_SCRAP)
                .unlockedBy("has_kerolox_engine", has(ModItems.KEROLOX_ENGINE))
                .save(output, key("staged_combustion_engine"));
        // Moteur méthalox : un moteur à combustion étagée, refait en acier inoxydable pour le méthane.
        shaped(RecipeCategory.MISC, ModItems.METHALOX_ENGINE)
                .pattern("SES")
                .pattern("SCS")
                .pattern(" N ")
                .define('S', ModItems.STAINLESS_TANK)
                .define('E', ModItems.STAGED_COMBUSTION_ENGINE)
                .define('C', ModItems.FLIGHT_PROCESSOR)
                .define('N', Items.NETHERITE_SCRAP)
                .unlockedBy("has_staged_combustion_engine", has(ModItems.STAGED_COMBUSTION_ENGINE))
                .save(output, key("methalox_engine"));
        shaped(RecipeCategory.MISC, ModItems.COMPOSITE_TANK)
                .pattern("SLS")
                .pattern("L L")
                .pattern("SLS")
                .define('S', Items.STRING)
                .define('L', ModItems.ALUMINUM_LITHIUM_TANK)
                .unlockedBy("has_aluminum_lithium_tank", has(ModItems.ALUMINUM_LITHIUM_TANK))
                .save(output, key("composite_tank"));
        // Rover : son châssis, ses quatre moteurs de roue, sa batterie et sa centrale de navigation.
        // Drone volant : quatre moteurs électriques aux bras, une caméra, un générateur à radio-isotopes, une coque légère.
        shaped(RecipeCategory.TRANSPORTATION, ModItems.ROTOR_DRONE)
                .pattern("MPM")
                .pattern("PSP")
                .pattern("MRM")
                .define('M', ModItems.ELECTRIC_MOTOR)
                .define('P', ModTags.Items.PLATES_ALUMINUM_LITHIUM)
                .define('S', ModItems.OPTICAL_SENSOR)
                .define('R', ModItems.REACTOR_CORE)
                .unlockedBy("has_rover", has(ModItems.ROVER))
                .save(output, key("rotor_drone"));
        shaped(RecipeCategory.TRANSPORTATION, ModItems.ROVER)
                .pattern("ANA")
                .pattern("MBM")
                .pattern("T T")
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('N', ModItems.NAVIGATION_UNIT)
                .define('M', ModItems.ELECTRIC_MOTOR)
                .define('B', ModItems.BATTERY_CELL)
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .unlockedBy("has_battery_cell", has(ModItems.BATTERY_CELL))
                .save(output, key("rover"));
        // ISRU : une foreuse et un extracteur, chacun sur son petit générateur ; le kit de base planétaire.
        shaped(RecipeCategory.MISC, ModBlocks.ICE_DRILL)
                .pattern("TBT")
                .pattern("MHM")
                .pattern("TST")
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('B', ModItems.BATTERY_CELL)
                .define('M', ModItems.ELECTRIC_MOTOR)
                .define('H', ModItems.HEATING_ELEMENT)
                .define('S', ModTags.Items.INGOTS_STEEL)
                .unlockedBy("has_battery_cell", has(ModItems.BATTERY_CELL))
                .save(output, key("ice_drill"));
        // Cryobot : une sonde chauffée par un cœur de réacteur, son treuil et son câble, son antenne.
        shaped(RecipeCategory.MISC, ModBlocks.CRYOBOT)
                .pattern("TRT")
                .pattern("HCH")
                .pattern("TNT")
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('R', ModItems.RADIO_TRANSMITTER)
                .define('H', ModItems.HEATING_ELEMENT)
                .define('C', ModItems.REACTOR_CORE)
                .define('N', ModItems.NAVIGATION_UNIT)
                .unlockedBy("has_reactor_core", has(ModItems.REACTOR_CORE))
                .save(output, key("cryobot"));
        shaped(RecipeCategory.MISC, ModBlocks.OXYGEN_EXTRACTOR)
                .pattern("TBT")
                .pattern("FHF")
                .pattern("TPT")
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('B', ModItems.BATTERY_CELL)
                .define('F', ModItems.CO2_FILTER)
                .define('H', ModItems.HEATING_ELEMENT)
                .define('P', ModBlocks.FLUID_PIPE)
                .unlockedBy("has_battery_cell", has(ModItems.BATTERY_CELL))
                .save(output, key("oxygen_extractor"));
        shaped(RecipeCategory.MISC, ModItems.BASE_KIT)
                .pattern("HLH")
                .pattern("SCS")
                .pattern("HBH")
                .define('H', ModBlocks.STATION_HULL)
                .define('L', ModBlocks.LIFE_SUPPORT)
                .define('S', ModItems.SOLAR_CELL)
                .define('C', ModItems.STATION_CORE)
                .define('B', ModItems.BATTERY_CELL)
                .unlockedBy("has_station_core", has(ModItems.STATION_CORE))
                .save(output, key("base_kit"));
        // Station lunaire : un module de station, ses ailes solaires, sa radio et son processeur.
        shaped(RecipeCategory.MISC, ModItems.LUNAR_GATEWAY)
                .pattern("SRS")
                .pattern("TCT")
                .pattern("SPS")
                .define('S', ModItems.SOLAR_CELL)
                .define('R', ModItems.RADIO_TRANSMITTER)
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('C', ModItems.STATION_CORE)
                .define('P', ModItems.FLIGHT_PROCESSOR)
                .unlockedBy("has_station_core", has(ModItems.STATION_CORE))
                .save(output, key("lunar_gateway"));
        // Retour d'échantillons : un atterrisseur, sa capsule de retour, ses conteneurs.
        shaped(RecipeCategory.MISC, ModItems.LUNAR_SAMPLE_RETURN)
                .pattern(" H ")
                .pattern("CLC")
                .pattern(" E ")
                .define('H', ModItems.HEAT_SHIELD_TILE)
                .define('C', ModItems.SAMPLE_CONTAINER)
                .define('L', ModItems.LUNAR_ROBOTIC_LANDER)
                .define('E', ModItems.HYPERGOLIC_ENGINE)
                .unlockedBy("has_lunar_robotic_lander", has(ModItems.LUNAR_ROBOTIC_LANDER))
                .save(output, key("lunar_sample_return"));
        shaped(RecipeCategory.MISC, ModItems.MARS_SAMPLE_RETURN)
                .pattern(" H ")
                .pattern("CLC")
                .pattern(" E ")
                .define('H', ModItems.HEAT_SHIELD_TILE)
                .define('C', ModItems.SAMPLE_CONTAINER)
                .define('L', ModItems.MARS_ROBOTIC_LANDER)
                .define('E', ModItems.HYPERGOLIC_ENGINE)
                .unlockedBy("has_mars_robotic_lander", has(ModItems.MARS_ROBOTIC_LANDER))
                .save(output, key("mars_sample_return"));
        shaped(RecipeCategory.MISC, ModItems.SAMPLE_CONTAINER, 4)
                .pattern("TGT")
                .pattern("T T")
                .pattern("TTT")
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('G', ModItems.REINFORCED_GLASS)
                .unlockedBy("has_titanium_plate", has(ModTags.Items.PLATES_TITANIUM))
                .save(output, key("sample_container"));
        // Missions robotiques.
        shaped(RecipeCategory.MISC, ModItems.LUNAR_PROBE)
                .pattern(" R ")
                .pattern("SBS")
                .pattern("AOA")
                .define('R', ModItems.RADIO_TRANSMITTER)
                .define('S', ModItems.SOLAR_CELL)
                .define('B', ModItems.CIRCUIT_BOARD)
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('O', ModItems.OPTICAL_SENSOR)
                .unlockedBy("has_radio_transmitter", has(ModItems.RADIO_TRANSMITTER))
                .save(output, key("lunar_probe"));
        shaped(RecipeCategory.MISC, ModItems.MARS_PROBE)
                .pattern(" N ")
                .pattern("HPH")
                .define('N', ModItems.NAVIGATION_UNIT)
                .define('H', ModItems.HEAT_SHIELD_TILE)
                .define('P', ModItems.LUNAR_PROBE)
                .unlockedBy("has_lunar_probe", has(ModItems.LUNAR_PROBE))
                .save(output, key("mars_probe"));
        // Sondes vers les planètes du système solaire.
        shaped(RecipeCategory.MISC, ModItems.VENUS_ORBITER)
                .pattern(" R ")
                .pattern("HPH")
                .pattern(" S ")
                .define('R', ModItems.RADIO_TRANSMITTER)
                .define('H', ModItems.HEAT_SHIELD_TILE)
                .define('P', ModItems.MARS_PROBE)
                .define('S', ModItems.SOLAR_CELL)
                .unlockedBy("has_mars_probe", has(ModItems.MARS_PROBE))
                .save(output, key("venus_orbiter"));
        shaped(RecipeCategory.MISC, ModItems.VENUS_LANDER)
                .pattern(" O ")
                .pattern("BPB")
                .pattern("HHH")
                .define('O', ModItems.OPTICAL_SENSOR)
                .define('B', ModItems.BATTERY_CELL)
                .define('P', ModItems.VENUS_ORBITER)
                .define('H', ModItems.HEAT_SHIELD_TILE)
                .unlockedBy("has_venus_orbiter", has(ModItems.VENUS_ORBITER))
                .save(output, key("venus_lander"));
        shaped(RecipeCategory.MISC, ModItems.MERCURY_ORBITER)
                .pattern(" R ")
                .pattern("HPH")
                .pattern(" S ")
                .define('R', ModItems.RADIO_TRANSMITTER)
                .define('H', ModItems.HEAT_SHIELD_TILE)
                .define('P', ModItems.MARS_PROBE)
                .define('S', ModItems.SOLAR_CELL)
                .unlockedBy("has_mars_probe", has(ModItems.MARS_PROBE))
                .save(output, key("mercury_orbiter"));
        shaped(RecipeCategory.MISC, ModItems.JUPITER_ORBITER)
                .pattern("RNR")
                .pattern("TPT")
                .pattern(" C ")
                .define('R', ModItems.RADIO_TRANSMITTER)
                .define('N', ModItems.NAVIGATION_UNIT)
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('P', ModItems.MARS_PROBE)
                .define('C', ModItems.REACTOR_CORE)
                .unlockedBy("has_mars_probe", has(ModItems.MARS_PROBE))
                .save(output, key("jupiter_orbiter"));
        shaped(RecipeCategory.MISC, ModItems.JUPITER_ENTRY_PROBE)
                .pattern(" O ")
                .pattern("BPB")
                .pattern("HHH")
                .define('O', ModItems.OPTICAL_SENSOR)
                .define('B', ModItems.BATTERY_CELL)
                .define('P', ModItems.JUPITER_ORBITER)
                .define('H', ModItems.HEAT_SHIELD_TILE)
                .unlockedBy("has_jupiter_orbiter", has(ModItems.JUPITER_ORBITER))
                .save(output, key("jupiter_entry_probe"));
        shaped(RecipeCategory.MISC, ModItems.SATURN_ORBITER)
                .pattern("RNR")
                .pattern("TPT")
                .pattern(" C ")
                .define('R', ModItems.RADIO_TRANSMITTER)
                .define('N', ModItems.NAVIGATION_UNIT)
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('P', ModItems.MARS_PROBE)
                .define('C', ModItems.REACTOR_CORE)
                .unlockedBy("has_mars_probe", has(ModItems.MARS_PROBE))
                .save(output, key("saturn_orbiter"));
        shaped(RecipeCategory.MISC, ModItems.TITAN_ENTRY_PROBE)
                .pattern(" O ")
                .pattern("BPB")
                .pattern("HHH")
                .define('O', ModItems.OPTICAL_SENSOR)
                .define('B', ModItems.BATTERY_CELL)
                .define('P', ModItems.SATURN_ORBITER)
                .define('H', ModItems.HEAT_SHIELD_TILE)
                .unlockedBy("has_saturn_orbiter", has(ModItems.SATURN_ORBITER))
                .save(output, key("titan_entry_probe"));
        shaped(RecipeCategory.MISC, ModItems.EUROPA_ORBITER)
                .pattern("RNR")
                .pattern("TPT")
                .pattern(" C ")
                .define('R', ModItems.RADIO_TRANSMITTER)
                .define('N', ModItems.NAVIGATION_UNIT)
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('P', ModItems.MARS_PROBE)
                .define('C', ModItems.REACTOR_CORE)
                .unlockedBy("has_mars_probe", has(ModItems.MARS_PROBE))
                .save(output, key("europa_orbiter"));
        shaped(RecipeCategory.MISC, ModItems.LUNAR_ROBOTIC_LANDER)
                .pattern("CRC")
                .pattern("AEA")
                .pattern("T T")
                .define('C', ModItems.BATTERY_CELL)
                .define('R', ModItems.RADIO_TRANSMITTER)
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('E', ModItems.HYPERGOLIC_ENGINE)
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .unlockedBy("has_radio_transmitter", has(ModItems.RADIO_TRANSMITTER))
                .save(output, key("lunar_robotic_lander"));
        shaped(RecipeCategory.MISC, ModItems.MARS_ROBOTIC_LANDER)
                .pattern(" N ")
                .pattern("HLH")
                .define('N', ModItems.NAVIGATION_UNIT)
                .define('H', ModItems.HEAT_SHIELD_TILE)
                .define('L', ModItems.LUNAR_ROBOTIC_LANDER)
                .unlockedBy("has_lunar_robotic_lander", has(ModItems.LUNAR_ROBOTIC_LANDER))
                .save(output, key("mars_robotic_lander"));
        shaped(RecipeCategory.MISC, ModItems.MARS_LANDER)
                .pattern(" L ")
                .pattern("TPT")
                .pattern("EPE")
                .define('L', ModItems.LUNAR_MODULE)
                .define('T', ModItems.COMPOSITE_TANK)
                .define('P', ModTags.Items.PLATES_TITANIUM)
                .define('E', ModItems.PRESSURE_FED_ENGINE)
                .unlockedBy("has_lunar_module", has(ModItems.LUNAR_MODULE))
                .save(output, key("mars_lander"));
        // Le vaisseau titanien : la cabine Mk3, dans une coque de tuiles thermiques, avec ses parachutes.
        shaped(RecipeCategory.MISC, ModItems.TITAN_LANDER)
                .pattern("WCW")
                .pattern("HMH")
                .pattern("PEP")
                .define('W', net.minecraft.world.item.Items.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE))
                .define('C', ModItems.NAVIGATION_UNIT)
                .define('H', ModItems.HEAT_SHIELD_TILE)
                .define('M', ModItems.CREW_CAPSULE_MK3)
                .define('P', ModTags.Items.PLATES_TITANIUM)
                .define('E', ModItems.PRESSURE_FED_ENGINE)
                .unlockedBy("has_crew_capsule_mk3", has(ModItems.CREW_CAPSULE_MK3))
                .save(output, key("titan_lander"));
        // Le vaisseau d'Europe : la cabine Mk3 sur un étage de descente, sous son blindage.
        shaped(RecipeCategory.MISC, ModItems.EUROPA_LANDER)
                .pattern(" C ")
                .pattern("BMB")
                .pattern("EPE")
                .define('C', ModItems.NAVIGATION_UNIT)
                .define('B', ModItems.SUIT_MODULE_RADIATION_SHIELD)
                .define('M', ModItems.CREW_CAPSULE_MK3)
                .define('E', ModItems.PRESSURE_FED_ENGINE)
                .define('P', ModTags.Items.PLATES_TITANIUM)
                .unlockedBy("has_crew_capsule_mk3", has(ModItems.CREW_CAPSULE_MK3))
                .save(output, key("europa_lander"));
        shaped(RecipeCategory.MISC, ModItems.LUNAR_MODULE)
                .pattern(" C ")
                .pattern("TPT")
                .pattern("GEG")
                .define('C', ModItems.CREW_CAPSULE)
                .define('T', ModItems.ALUMINUM_LITHIUM_TANK)
                .define('P', ModTags.Items.PLATES_TITANIUM)
                .define('G', Tags.Items.INGOTS_GOLD)
                .define('E', ModItems.PRESSURE_FED_ENGINE)
                .unlockedBy("has_crew_capsule", has(ModItems.CREW_CAPSULE))
                .save(output, key("lunar_module"));

        shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.STATION_HULL, 8)
                .pattern("AAA")
                .pattern("ATA")
                .pattern("AAA")
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .unlockedBy("has_titanium_plate", has(ModTags.Items.PLATES_TITANIUM))
                .save(output, key("station_hull"));

        shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.STATION_WINDOW, 4)
                .pattern("AGA")
                .pattern("G G")
                .pattern("AGA")
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('G', ModItems.REINFORCED_GLASS)
                .unlockedBy("has_aluminum_plate", has(ModTags.Items.PLATES_ALUMINUM))
                .save(output, key("station_window"));

        shaped(RecipeCategory.MISC, ModBlocks.DOCKING_PORT)
                .pattern("TAT")
                .pattern("A A")
                .pattern("TAT")
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .unlockedBy("has_titanium_plate", has(ModTags.Items.PLATES_TITANIUM))
                .save(output, key("docking_port"));

        shaped(RecipeCategory.MISC, ModBlocks.SOLAR_PANEL)
                .pattern("DDD")
                .pattern("ARA")
                .pattern("AAA")
                .define('D', ModItems.SOLAR_CELL)
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .unlockedBy("has_aluminum_plate", has(ModTags.Items.PLATES_ALUMINUM))
                .save(output, key("solar_panel"));

        shaped(RecipeCategory.MISC, ModBlocks.LIFE_SUPPORT)
                .pattern("ASA")
                .pattern("CTC")
                .pattern("ASA")
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('S', ModItems.CO2_FILTER)
                .define('C', ModItems.CIRCUIT_BOARD)
                .define('T', ModBlocks.CRYOGENIC_TANK)
                .unlockedBy("has_cryogenic_tank", has(ModBlocks.CRYOGENIC_TANK))
                .save(output, key("life_support"));

        // Module central : la coque d'une pièce, un hublot, le sas, le générateur d'air et son panneau solaire.
        shaped(RecipeCategory.MISC, ModItems.STATION_CORE)
                .pattern("HPH")
                .pattern("WLD")
                .pattern("HHH")
                .define('H', ModBlocks.STATION_HULL)
                .define('P', ModBlocks.SOLAR_PANEL)
                .define('W', ModBlocks.STATION_WINDOW)
                .define('L', ModBlocks.LIFE_SUPPORT)
                .define('D', ModBlocks.DOCKING_PORT)
                .unlockedBy("has_life_support", has(ModBlocks.LIFE_SUPPORT))
                .save(output, key("station_core"));

        // Aménagement des stations.
        shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.STATION_PANEL, 8)
                .pattern("HHH")
                .pattern("HWH")
                .pattern("HHH")
                .define('H', ModBlocks.STATION_HULL)
                .define('W', ModItems.INSULATION_BLANKET)
                .unlockedBy("has_station_hull", has(ModBlocks.STATION_HULL))
                .save(output, key("station_panel"));
        shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.STATION_FLOOR, 4)
                .pattern("HH")
                .pattern("HH")
                .define('H', ModBlocks.STATION_HULL)
                .unlockedBy("has_station_hull", has(ModBlocks.STATION_HULL))
                .save(output, key("station_floor"));
        shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.STATION_LIGHT, 4)
                .pattern("HGH")
                .pattern("GRG")
                .pattern("HGH")
                .define('H', ModBlocks.STATION_HULL)
                .define('G', ModItems.LED_LAMP)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .unlockedBy("has_station_hull", has(ModBlocks.STATION_HULL))
                .save(output, key("station_light"));
        shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.STATION_TRUSS, 8)
                .pattern("A A")
                .pattern(" A ")
                .pattern("A A")
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .unlockedBy("has_aluminum_plate", has(ModTags.Items.PLATES_ALUMINUM))
                .save(output, key("station_truss"));
        shaped(RecipeCategory.REDSTONE, ModItems.STATION_HATCH)
                .pattern("HH")
                .pattern("WH")
                .pattern("HH")
                .define('H', ModBlocks.STATION_HULL)
                .define('W', ModBlocks.STATION_WINDOW)
                .unlockedBy("has_station_hull", has(ModBlocks.STATION_HULL))
                .save(output, key("station_hatch"));
        shaped(RecipeCategory.REDSTONE, ModItems.GLASS_DOOR)
                .pattern("AGA")
                .pattern("AGA")
                .pattern("ARA")
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('G', Items.GLASS_PANE)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .unlockedBy("has_aluminum_plate", has(ModTags.Items.PLATES_ALUMINUM))
                .save(output, key("glass_door"));
        shaped(RecipeCategory.REDSTONE, ModItems.AIRLOCK_DOOR)
                .pattern("HWH")
                .pattern("PRP")
                .pattern("HHH")
                .define('H', ModBlocks.STATION_HULL)
                .define('W', ModBlocks.STATION_WINDOW)
                .define('P', ModItems.ELECTRIC_MOTOR)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .unlockedBy("has_station_hull", has(ModBlocks.STATION_HULL))
                .save(output, key("airlock_door"));
        shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.CUPOLA_WINDOW, 4)
                .pattern("TGT")
                .pattern("G G")
                .pattern("TGT")
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('G', Tags.Items.GLASS_BLOCKS)
                .unlockedBy("has_titanium_plate", has(ModTags.Items.PLATES_TITANIUM))
                .save(output, key("cupola_window"));
        shaped(RecipeCategory.MISC, ModBlocks.STATION_TERMINAL)
                .pattern("AGA")
                .pattern("RCR")
                .pattern("AAA")
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('G', ModItems.DISPLAY_SCREEN)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .define('C', ModItems.CIRCUIT_BOARD)
                .unlockedBy("has_station_hull", has(ModBlocks.STATION_HULL))
                .save(output, key("station_terminal"));
        shaped(RecipeCategory.MISC, ModBlocks.CREW_BUNK)
                .pattern("HHH")
                .pattern("WBW")
                .pattern("HHH")
                .define('H', ModBlocks.STATION_HULL)
                .define('W', ModItems.INSULATION_BLANKET)
                .define('B', ModItems.INSULATION_BLANKET)
                .unlockedBy("has_station_hull", has(ModBlocks.STATION_HULL))
                .save(output, key("crew_bunk"));
        shaped(RecipeCategory.MISC, ModBlocks.LAB_RACK)
                .pattern("AGA")
                .pattern("RBR")
                .pattern("ACA")
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('G', ModItems.DISPLAY_SCREEN)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .define('B', Items.BREWING_STAND)
                .define('C', ModItems.CIRCUIT_BOARD)
                .unlockedBy("has_station_hull", has(ModBlocks.STATION_HULL))
                .save(output, key("lab_rack"));
        shaped(RecipeCategory.MISC, ModBlocks.HYDROPONIC_TRAY)
                .pattern("GLG")
                .pattern("AWA")
                .pattern("AAA")
                .define('G', ModItems.REINFORCED_GLASS)
                .define('L', ModItems.LED_LAMP)
                .define('W', Items.WATER_BUCKET)
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .unlockedBy("has_station_hull", has(ModBlocks.STATION_HULL))
                .save(output, key("hydroponic_tray"));
        shaped(RecipeCategory.MISC, ModBlocks.CARGO_RACK)
                .pattern("AWA")
                .pattern("WCW")
                .pattern("AWA")
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('W', ModItems.INSULATION_BLANKET)
                .define('C', Tags.Items.CHESTS_WOODEN)
                .unlockedBy("has_aluminum_plate", has(ModTags.Items.PLATES_ALUMINUM))
                .save(output, key("cargo_rack"));
        shaped(RecipeCategory.MISC, ModBlocks.STATION_BATTERY)
                .pattern("ARA")
                .pattern("LLL")
                .pattern("ARA")
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('R', ModItems.BATTERY_CELL)
                .define('L', ModTags.Items.INGOTS_LITHIUM)
                .unlockedBy("has_lithium_ingot", has(ModTags.Items.INGOTS_LITHIUM))
                .save(output, key("station_battery"));

        // Kits de modules : chacun réunit les équipements qu'il déploie.
        shaped(RecipeCategory.MISC, ModItems.HABITATION_MODULE)
                .pattern("BCB")
                .pattern("CTC")
                .pattern("BCB")
                .define('B', ModBlocks.CREW_BUNK)
                .define('C', ModBlocks.CARGO_RACK)
                .define('T', ModBlocks.STATION_TERMINAL)
                .unlockedBy("has_crew_bunk", has(ModBlocks.CREW_BUNK))
                .save(output, key("habitation_module"));
        shaped(RecipeCategory.MISC, ModItems.LABORATORY_MODULE)
                .pattern("LLL")
                .pattern("LTL")
                .pattern("LLL")
                .define('L', ModBlocks.LAB_RACK)
                .define('T', ModBlocks.STATION_TERMINAL)
                .unlockedBy("has_lab_rack", has(ModBlocks.LAB_RACK))
                .save(output, key("laboratory_module"));
        shaped(RecipeCategory.MISC, ModItems.GREENHOUSE_MODULE)
                .pattern("TTT")
                .pattern("TCT")
                .pattern("TTT")
                .define('T', ModBlocks.HYDROPONIC_TRAY)
                .define('C', ModBlocks.CARGO_RACK)
                .unlockedBy("has_hydroponic_tray", has(ModBlocks.HYDROPONIC_TRAY))
                .save(output, key("greenhouse_module"));
        shaped(RecipeCategory.MISC, ModItems.CARGO_MODULE)
                .pattern("CCC")
                .pattern("CHC")
                .pattern("CCC")
                .define('C', ModBlocks.CARGO_RACK)
                .define('H', ModBlocks.STATION_HULL)
                .unlockedBy("has_cargo_rack", has(ModBlocks.CARGO_RACK))
                .save(output, key("cargo_module"));
        shaped(RecipeCategory.MISC, ModItems.CUPOLA_MODULE)
                .pattern("WWW")
                .pattern("WHW")
                .pattern("WWW")
                .define('W', ModBlocks.CUPOLA_WINDOW)
                .define('H', ModBlocks.STATION_HULL)
                .unlockedBy("has_cupola_window", has(ModBlocks.CUPOLA_WINDOW))
                .save(output, key("cupola_module"));
        shaped(RecipeCategory.MISC, ModItems.AIRLOCK_MODULE)
                .pattern("HDH")
                .pattern("HCH")
                .pattern("HDH")
                .define('H', ModBlocks.STATION_HULL)
                .define('D', ModItems.AIRLOCK_DOOR)
                .define('C', ModBlocks.CARGO_RACK)
                .unlockedBy("has_airlock_door", has(ModItems.AIRLOCK_DOOR))
                .save(output, key("airlock_module"));
        shaped(RecipeCategory.MISC, ModItems.POWER_MODULE)
                .pattern("PPP")
                .pattern("PBP")
                .pattern("PPP")
                .define('P', ModBlocks.SOLAR_PANEL)
                .define('B', ModBlocks.STATION_BATTERY)
                .unlockedBy("has_station_battery", has(ModBlocks.STATION_BATTERY))
                .save(output, key("power_module"));
        // Chantier naval : de la poutre treillis pour le berceau, un hublot de coupole pour la verrière, un terminal pour
        // le pupitre, et un moteur à fusion, dont le plan sert de modèle au vaisseau.
        shaped(RecipeCategory.MISC, ModItems.SHIPYARD_MODULE)
                .pattern("TWT")
                .pattern("CFC")
                .pattern("TTT")
                .define('T', ModBlocks.STATION_TRUSS)
                .define('W', ModBlocks.CUPOLA_WINDOW)
                .define('C', ModBlocks.STATION_TERMINAL)
                .define('F', ModItems.FUSION_ENGINE)
                .unlockedBy("has_fusion_engine", has(ModItems.FUSION_ENGINE))
                .save(output.withConditions(com.orbitalis.core.Features.InterstellarCondition.INSTANCE), key("shipyard_module"));
        // Cœur de distorsion : un réacteur à fusion pour le nourrir, de la matière exotique pour ouvrir la bulle.
        shaped(RecipeCategory.MISC, ModBlocks.WARP_CORE)
                .pattern("TXT")
                .pattern("XRX")
                .pattern("TCT")
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('X', ModItems.EXOTIC_MATTER)
                .define('R', ModBlocks.FUSION_REACTOR)
                .define('C', ModItems.CIRCUIT_BOARD)
                .unlockedBy("has_exotic_matter", has(ModItems.EXOTIC_MATTER))
                .save(output.withConditions(com.orbitalis.core.Features.InterstellarCondition.INSTANCE), key("warp_core"));
        // Moteur de l'anneau : quatre moteurs électriques autour d'une couronne d'acier.
        shaped(RecipeCategory.MISC, ModBlocks.RING_DRIVE)
                .pattern("SMS")
                .pattern("MCM")
                .pattern("SMS")
                .define('S', ModTags.Items.PLATES_STEEL)
                .define('M', ModItems.ELECTRIC_MOTOR)
                .define('C', ModItems.CIRCUIT_BOARD)
                .unlockedBy("has_shipyard_module", has(ModItems.SHIPYARD_MODULE))
                .save(output.withConditions(com.orbitalis.core.Features.InterstellarCondition.INSTANCE), key("ring_drive"));
        // Caisson d'hibernation : une capsule de coque, son hublot, un recycleur d'air pour la refroidir et la faire respirer.
        shaped(RecipeCategory.MISC, ModBlocks.HIBERNATION_POD, 2)
                .pattern("HGH")
                .pattern("HLH")
                .pattern("HCH")
                .define('H', ModBlocks.STATION_HULL)
                .define('G', ModBlocks.CUPOLA_WINDOW)
                .define('L', ModBlocks.LIFE_SUPPORT)
                .define('C', ModItems.CIRCUIT_BOARD)
                .unlockedBy("has_shipyard_module", has(ModItems.SHIPYARD_MODULE))
                .save(output.withConditions(com.orbitalis.core.Features.InterstellarCondition.INSTANCE), key("hibernation_pod"));
        // Poste de pilotage : la centrale inertielle, deux terminaux et l'ordinateur de vol d'une fusée.
        shaped(RecipeCategory.MISC, ModBlocks.SHIP_HELM)
                .pattern(" N ")
                .pattern("TFT")
                .pattern("HHH")
                .define('N', ModItems.NAVIGATION_UNIT)
                .define('T', ModBlocks.STATION_TERMINAL)
                .define('F', ModBlocks.FLIGHT_COMPUTER)
                .define('H', ModBlocks.STATION_HULL)
                .unlockedBy("has_shipyard_module", has(ModItems.SHIPYARD_MODULE))
                .save(output.withConditions(com.orbitalis.core.Features.InterstellarCondition.INSTANCE), key("ship_helm"));
    }

    /** Satellites et station de poursuite. */
    private void buildSatellites() {
        shaped(RecipeCategory.MISC, ModItems.RECOVERY_REMOTE)
                .pattern(" T ")
                .pattern("ACA")
                .pattern(" A ")
                .define('T', ModItems.CIRCUIT_BOARD)
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('C', ModItems.CIRCUIT_BOARD)
                .unlockedBy("has_aluminum_plate", has(ModTags.Items.PLATES_ALUMINUM))
                .save(output, key("recovery_remote"));

        // Tablette de mission : un écran dans un boîtier d'aluminium, et l'électronique qui reçoit le satellite relais.
        shaped(RecipeCategory.MISC, ModItems.MISSION_TABLET)
                .pattern("AGA")
                .pattern("ACA")
                .pattern("ARA")
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('G', ModItems.DISPLAY_SCREEN)
                .define('C', ModItems.CIRCUIT_BOARD)
                .define('R', Items.REDSTONE)
                .unlockedBy("has_aluminum_plate", has(ModTags.Items.PLATES_ALUMINUM))
                .save(output, key("mission_tablet"));

        // Centres spatiaux : les blocs de construction.
        shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.FACILITY_CONCRETE, 8)
                .pattern("SSS")
                .pattern("SDS")
                .pattern("SSS")
                .define('S', Items.SMOOTH_STONE)
                .define('D', Tags.Items.DYES_LIGHT_GRAY)
                .unlockedBy("has_smooth_stone", has(Items.SMOOTH_STONE))
                .save(output, key("facility_concrete"));
        slab(RecipeCategory.BUILDING_BLOCKS, ModBlocks.FACILITY_CONCRETE_SLAB, ModBlocks.FACILITY_CONCRETE);
        stairBuilder(ModBlocks.FACILITY_CONCRETE_STAIRS, net.minecraft.world.item.crafting.Ingredient.of(ModBlocks.FACILITY_CONCRETE))
                .unlockedBy("has_facility_concrete", has(ModBlocks.FACILITY_CONCRETE))
                .save(output, key("facility_concrete_stairs"));
        net.minecraft.data.recipes.SimpleCookingRecipeBuilder.smelting(net.minecraft.world.item.crafting.Ingredient.of(ModBlocks.FACILITY_CONCRETE),
                        RecipeCategory.BUILDING_BLOCKS, net.minecraft.world.item.crafting.CookingBookCategory.BLOCKS, ModBlocks.CRACKED_FACILITY_CONCRETE, 0.1F, 200)
                .unlockedBy("has_facility_concrete", has(ModBlocks.FACILITY_CONCRETE))
                .save(output, key("cracked_facility_concrete"));
        shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.FACILITY_TILES, 4)
                .pattern("CC")
                .pattern("CC")
                .define('C', ModBlocks.FACILITY_CONCRETE)
                .unlockedBy("has_facility_concrete", has(ModBlocks.FACILITY_CONCRETE))
                .save(output, key("facility_tiles"));
        shapeless(RecipeCategory.BUILDING_BLOCKS, ModBlocks.HAZARD_STRIPES, 4)
                .requires(ModBlocks.FACILITY_CONCRETE, 4)
                .requires(Tags.Items.DYES_YELLOW)
                .requires(Tags.Items.DYES_BLACK)
                .unlockedBy("has_facility_concrete", has(ModBlocks.FACILITY_CONCRETE))
                .save(output, key("hazard_stripes"));
        shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.FACILITY_SIDING, 8)
                .pattern("AA")
                .pattern("AA")
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .unlockedBy("has_aluminum_plate", has(ModTags.Items.PLATES_ALUMINUM))
                .save(output, key("facility_siding"));
        shapeless(RecipeCategory.BUILDING_BLOCKS, ModBlocks.BLUE_FACILITY_SIDING, 4)
                .requires(ModBlocks.FACILITY_SIDING, 4)
                .requires(Tags.Items.DYES_BLUE)
                .unlockedBy("has_facility_siding", has(ModBlocks.FACILITY_SIDING))
                .save(output, key("blue_facility_siding"));
        shapeless(RecipeCategory.BUILDING_BLOCKS, ModBlocks.RUSTY_FACILITY_SIDING, 4)
                .requires(ModBlocks.FACILITY_SIDING, 4)
                .requires(Tags.Items.DYES_BROWN)
                .unlockedBy("has_facility_siding", has(ModBlocks.FACILITY_SIDING))
                .save(output, key("rusty_facility_siding"));
        shapeless(RecipeCategory.BUILDING_BLOCKS, ModBlocks.ORANGE_FACILITY_SIDING, 4)
                .requires(ModBlocks.FACILITY_SIDING, 4)
                .requires(Tags.Items.DYES_ORANGE)
                .unlockedBy("has_facility_siding", has(ModBlocks.FACILITY_SIDING))
                .save(output, key("orange_facility_siding"));
        shaped(RecipeCategory.DECORATIONS, ModBlocks.DRAFTING_TABLE)
                .pattern("PPP")
                .pattern("WWW")
                .pattern("S S")
                .define('P', Items.PAPER)
                .define('W', net.minecraft.tags.ItemTags.WOODEN_SLABS)
                .define('S', Items.STICK)
                .unlockedBy("has_paper", has(Items.PAPER))
                .save(output, key("drafting_table"));
        shaped(RecipeCategory.DECORATIONS, ModBlocks.SERVER_RACK)
                .pattern("ABA")
                .pattern("ARA")
                .pattern("ABA")
                .define('A', ModTags.Items.PLATES_STEEL)
                .define('B', ModItems.STEEL_GRATING)
                .define('R', Items.REDSTONE)
                .unlockedBy("has_steel_plate", has(ModTags.Items.PLATES_STEEL))
                .save(output, key("server_rack"));
        shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.LOUVERED_PANEL, 4)
                .pattern("BB")
                .pattern("BB")
                .define('B', ModItems.STEEL_GRATING)
                .unlockedBy("has_iron_bars", has(ModItems.STEEL_GRATING))
                .save(output, key("louvered_panel"));
        shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.RADOME_PANEL, 8)
                .pattern("WWW")
                .pattern("WAW")
                .pattern("WWW")
                .define('W', Items.CONCRETE.pick(net.minecraft.world.item.DyeColor.WHITE))
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .unlockedBy("has_aluminum_plate", has(ModTags.Items.PLATES_ALUMINUM))
                .save(output, key("radome_panel"));
        shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.FACILITY_GLASS, 4)
                .pattern("GA")
                .pattern("AG")
                .define('G', Tags.Items.GLASS_BLOCKS_COLORLESS)
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .unlockedBy("has_aluminum_plate", has(ModTags.Items.PLATES_ALUMINUM))
                .save(output, key("facility_glass"));
        shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.ANTENNA_MESH, 4)
                .pattern("BAB")
                .define('B', ModItems.STEEL_GRATING)
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .unlockedBy("has_aluminum_plate", has(ModTags.Items.PLATES_ALUMINUM))
                .save(output, key("antenna_mesh"));

        // Combinaison orange, en trois pièces : un casque à visière, le torse et son électronique de survie, les jambières.
        shaped(RecipeCategory.COMBAT, ModItems.SPACE_HELMET)
                .pattern("AGA")
                .pattern("ALA")
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('G', ModItems.REINFORCED_GLASS)
                .define('L', ModItems.INSULATION_BLANKET)
                .unlockedBy("has_aluminum_plate", has(ModTags.Items.PLATES_ALUMINUM))
                .save(output, key("space_helmet"));
        shaped(RecipeCategory.COMBAT, ModItems.SPACESUIT)
                .pattern("ADA")
                .pattern("ALA")
                .pattern("ACA")
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('D', Tags.Items.DYES_ORANGE)
                .define('L', ModItems.INSULATION_BLANKET)
                .define('C', ModItems.CIRCUIT_BOARD)
                .unlockedBy("has_aluminum_plate", has(ModTags.Items.PLATES_ALUMINUM))
                .save(output, key("spacesuit"));
        shaped(RecipeCategory.COMBAT, ModItems.SPACE_LEGGINGS)
                .pattern("ADA")
                .pattern("ALA")
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('D', Tags.Items.DYES_ORANGE)
                .define('L', ModItems.INSULATION_BLANKET)
                .unlockedBy("has_aluminum_plate", has(ModTags.Items.PLATES_ALUMINUM))
                .save(output, key("space_leggings"));
        // Composants de fabrication : l'électronique, la mécanique et l'aménagement dont sont faits les blocs du mod.
        shaped(RecipeCategory.MISC, ModItems.CIRCUIT_BOARD, 3)
                .pattern("RGR")
                .pattern("CAC")
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .define('G', Tags.Items.INGOTS_GOLD)
                .define('C', Tags.Items.INGOTS_COPPER)
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .unlockedBy("has_aluminum_plate", has(ModTags.Items.PLATES_ALUMINUM))
                .save(output, key("circuit_board"));
        shaped(RecipeCategory.MISC, ModItems.FLIGHT_PROCESSOR)
                .pattern("BLB")
                .pattern("LGL")
                .pattern("BLB")
                .define('B', ModItems.CIRCUIT_BOARD)
                .define('L', ModTags.Items.INGOTS_LITHIUM)
                .define('G', Tags.Items.INGOTS_GOLD)
                .unlockedBy("has_circuit_board", has(ModItems.CIRCUIT_BOARD))
                .save(output, key("flight_processor"));
        shaped(RecipeCategory.MISC, ModItems.RADIO_TRANSMITTER)
                .pattern(" C ")
                .pattern(" C ")
                .pattern("ABA")
                .define('C', Tags.Items.INGOTS_COPPER)
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('B', ModItems.CIRCUIT_BOARD)
                .unlockedBy("has_circuit_board", has(ModItems.CIRCUIT_BOARD))
                .save(output, key("radio_transmitter"));
        shaped(RecipeCategory.MISC, ModItems.SOLAR_CELL, 3)
                .pattern("GGG")
                .pattern("CLC")
                .define('G', Tags.Items.GLASS_PANES)
                .define('C', Tags.Items.INGOTS_COPPER)
                .define('L', Tags.Items.GEMS_LAPIS)
                .unlockedBy("has_copper_ingot", has(Tags.Items.INGOTS_COPPER))
                .save(output, key("solar_cell"));
        // Le moteur et la résistance ne demandent que des lingots : le laminoir, la fonderie et le générateur en ont
        // besoin avant qu'on sache faire des tôles.
        shaped(RecipeCategory.MISC, ModItems.ELECTRIC_MOTOR, 2)
                .pattern("SCS")
                .pattern("IRI")
                .define('S', ModTags.Items.INGOTS_STEEL)
                .define('C', Tags.Items.INGOTS_COPPER)
                .define('I', Tags.Items.INGOTS_IRON)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .unlockedBy("has_steel_ingot", has(ModTags.Items.INGOTS_STEEL))
                .save(output, key("electric_motor"));
        shaped(RecipeCategory.MISC, ModItems.STEEL_GRATING, 16)
                .pattern("SSS")
                .pattern("SSS")
                .define('S', ModTags.Items.INGOTS_STEEL)
                .unlockedBy("has_steel_ingot", has(ModTags.Items.INGOTS_STEEL))
                .save(output, key("steel_grating"));
        shaped(RecipeCategory.MISC, ModItems.INSULATION_BLANKET, 4)
                .pattern("WWW")
                .pattern("AAA")
                .define('W', ItemTags.WOOL)
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .unlockedBy("has_aluminum_plate", has(ModTags.Items.PLATES_ALUMINUM))
                .save(output, key("insulation_blanket"));
        shaped(RecipeCategory.MISC, ModItems.HEATING_ELEMENT, 2)
                .pattern("CCC")
                .pattern("CRC")
                .pattern("III")
                .define('C', Tags.Items.INGOTS_COPPER)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .define('I', Tags.Items.INGOTS_IRON)
                .unlockedBy("has_copper_ingot", has(Tags.Items.INGOTS_COPPER))
                .save(output, key("heating_element"));
        shaped(RecipeCategory.MISC, ModItems.NAVIGATION_UNIT)
                .pattern("ABA")
                .pattern("GRG")
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('B', ModItems.CIRCUIT_BOARD)
                .define('G', Tags.Items.INGOTS_GOLD)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .unlockedBy("has_circuit_board", has(ModItems.CIRCUIT_BOARD))
                .save(output, key("navigation_unit"));
        shaped(RecipeCategory.MISC, ModItems.OPTICAL_SENSOR)
                .pattern(" G ")
                .pattern("ABA")
                .define('G', Tags.Items.GLASS_BLOCKS_COLORLESS)
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('B', ModItems.CIRCUIT_BOARD)
                .unlockedBy("has_circuit_board", has(ModItems.CIRCUIT_BOARD))
                .save(output, key("optical_sensor"));
        shaped(RecipeCategory.MISC, ModItems.REINFORCED_GLASS, 6)
                .pattern("AGA")
                .pattern("GGG")
                .pattern("AGA")
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('G', Tags.Items.GLASS_PANES)
                .unlockedBy("has_aluminum_plate", has(ModTags.Items.PLATES_ALUMINUM))
                .save(output, key("reinforced_glass"));
        shaped(RecipeCategory.MISC, ModItems.DISPLAY_SCREEN, 2)
                .pattern("PPP")
                .pattern("DBD")
                .define('P', Tags.Items.GLASS_PANES)
                .define('D', Tags.Items.DUSTS_GLOWSTONE)
                .define('B', ModItems.CIRCUIT_BOARD)
                .unlockedBy("has_circuit_board", has(ModItems.CIRCUIT_BOARD))
                .save(output, key("display_screen"));
        shaped(RecipeCategory.MISC, ModItems.LED_LAMP, 2)
                .pattern("GDG")
                .pattern("CRC")
                .define('G', Tags.Items.GLASS_PANES)
                .define('D', Tags.Items.DUSTS_GLOWSTONE)
                .define('C', Tags.Items.INGOTS_COPPER)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .unlockedBy("has_copper_ingot", has(Tags.Items.INGOTS_COPPER))
                .save(output, key("led_lamp"));
        shaped(RecipeCategory.MISC, ModItems.HEAT_SHIELD_TILE, 4)
                .pattern("BBB")
                .pattern("AAA")
                .define('B', Items.BRICK)
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .unlockedBy("has_aluminum_plate", has(ModTags.Items.PLATES_ALUMINUM))
                .save(output, key("heat_shield_tile"));
        shaped(RecipeCategory.MISC, ModItems.BATTERY_CELL, 2)
                .pattern("ALA")
                .pattern("LRL")
                .pattern("ACA")
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('L', ModTags.Items.INGOTS_LITHIUM)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .define('C', Tags.Items.INGOTS_COPPER)
                .unlockedBy("has_lithium_ingot", has(ModTags.Items.INGOTS_LITHIUM))
                .save(output, key("battery_cell"));
        shaped(RecipeCategory.MISC, ModItems.CO2_FILTER)
                .pattern("ALA")
                .pattern("AWA")
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('L', ModTags.Items.INGOTS_LITHIUM)
                .define('W', ItemTags.WOOL)
                .unlockedBy("has_lithium_ingot", has(ModTags.Items.INGOTS_LITHIUM))
                .save(output, key("co2_filter"));
        shaped(RecipeCategory.MISC, ModBlocks.SUIT_WORKBENCH)
                .pattern("TGT")
                .pattern("ACA")
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('G', ModItems.REINFORCED_GLASS)
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('C', Items.CRAFTING_TABLE)
                .unlockedBy("has_titanium_plate", has(ModTags.Items.PLATES_TITANIUM))
                .save(output, key("suit_workbench"));
        shaped(RecipeCategory.COMBAT, ModItems.SPACE_BOOTS)
                .pattern("A A")
                .pattern("ABA")
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('B', ModItems.INSULATION_BLANKET)
                .unlockedBy("has_aluminum_plate", has(ModTags.Items.PLATES_ALUMINUM))
                .save(output, key("space_boots"));

        // Combinaison blanche, avancée : la coque en titane autour de chaque pièce orange ; une visière dorée.
        shaped(RecipeCategory.COMBAT, ModItems.ADVANCED_SPACE_HELMET)
                .pattern("TGT")
                .pattern("TPT")
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('G', Items.GOLD_INGOT)
                .define('P', ModItems.SPACE_HELMET)
                .unlockedBy("has_space_helmet", has(ModItems.SPACE_HELMET))
                .save(output, key("advanced_space_helmet"));
        shaped(RecipeCategory.COMBAT, ModItems.ADVANCED_SPACESUIT)
                .pattern("TCT")
                .pattern("TPT")
                .pattern("TRT")
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('C', ModItems.CIRCUIT_BOARD)
                .define('P', ModItems.SPACESUIT)
                .define('R', Items.REDSTONE)
                .unlockedBy("has_spacesuit", has(ModItems.SPACESUIT))
                .save(output, key("advanced_spacesuit"));
        shaped(RecipeCategory.COMBAT, ModItems.ADVANCED_SPACE_LEGGINGS)
                .pattern("TPT")
                .pattern("T T")
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('P', ModItems.SPACE_LEGGINGS)
                .unlockedBy("has_space_leggings", has(ModItems.SPACE_LEGGINGS))
                .save(output, key("advanced_space_leggings"));
        shaped(RecipeCategory.COMBAT, ModItems.ADVANCED_SPACE_BOOTS)
                .pattern("T T")
                .pattern("TPT")
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('P', ModItems.SPACE_BOOTS)
                .unlockedBy("has_space_boots", has(ModItems.SPACE_BOOTS))
                .save(output, key("advanced_space_boots"));

        // Modules de la combinaison avancée.
        shaped(RecipeCategory.MISC, ModItems.SUIT_MODULE_MAP)
                .pattern("TGT")
                .pattern("RMR")
                .pattern("TTT")
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('G', ModItems.REINFORCED_GLASS)
                .define('R', Items.REDSTONE)
                .define('M', ModItems.NAVIGATION_UNIT)
                .unlockedBy("has_advanced_space_helmet", has(ModItems.ADVANCED_SPACE_HELMET))
                .save(output, key("suit_module_map"));
        shaped(RecipeCategory.MISC, ModItems.SUIT_MODULE_SENSOR)
                .pattern("TGT")
                .pattern("RDR")
                .pattern("TTT")
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('G', ModItems.REINFORCED_GLASS)
                .define('R', Items.REDSTONE)
                .define('D', ModItems.OPTICAL_SENSOR)
                .unlockedBy("has_advanced_space_helmet", has(ModItems.ADVANCED_SPACE_HELMET))
                .save(output, key("suit_module_sensor"));
        shaped(RecipeCategory.MISC, ModItems.SUIT_MODULE_OXYGEN_TANK)
                .pattern("TRT")
                .pattern("TBT")
                .pattern("TBT")
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('R', Items.REDSTONE)
                .define('B', Items.GLASS_BOTTLE)
                .unlockedBy("has_advanced_spacesuit", has(ModItems.ADVANCED_SPACESUIT))
                .save(output, key("suit_module_oxygen_tank"));
        shaped(RecipeCategory.MISC, ModItems.SUIT_MODULE_THRUSTERS)
                .pattern("TRT")
                .pattern("TCT")
                .pattern("F F")
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('R', Items.REDSTONE)
                .define('C', ModItems.CIRCUIT_BOARD)
                .define('F', Items.FIREWORK_ROCKET)
                .unlockedBy("has_advanced_spacesuit", has(ModItems.ADVANCED_SPACESUIT))
                .save(output, key("suit_module_thrusters"));
        shaped(RecipeCategory.MISC, ModItems.SUIT_MODULE_SHOCK_ABSORBERS)
                .pattern("TPT")
                .pattern("S S")
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('P', ModItems.ELECTRIC_MOTOR)
                .define('S', Items.SLIME_BALL)
                .unlockedBy("has_advanced_space_leggings", has(ModItems.ADVANCED_SPACE_LEGGINGS))
                .save(output, key("suit_module_shock_absorbers"));
        // Blindage anti-radiations : des tôles de titane épaisses sur une couverture isolante, et un bloc de fer.
        shaped(RecipeCategory.MISC, ModItems.SUIT_MODULE_RADIATION_SHIELD)
                .pattern("TIT")
                .pattern("IBI")
                .pattern("TIT")
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('I', ModItems.INSULATION_BLANKET)
                .define('B', Tags.Items.STORAGE_BLOCKS_IRON)
                .unlockedBy("has_advanced_spacesuit", has(ModItems.ADVANCED_SPACESUIT))
                .save(output, key("suit_module_radiation_shield"));

        // Capsule habitée : coque en titane, hublot, électronique de bord et bouclier thermique.
        shaped(RecipeCategory.MISC, ModItems.CREW_CAPSULE)
                .pattern("TGT")
                .pattern("TCT")
                .pattern("HHH")
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('G', ModItems.REINFORCED_GLASS)
                .define('C', ModItems.CIRCUIT_BOARD)
                .define('H', ModItems.HEAT_SHIELD_TILE)
                .unlockedBy("has_titanium_plate", has(ModTags.Items.PLATES_TITANIUM))
                .save(output, key("crew_capsule"));

        // Capsule Mk2 « Horizon » : une Mk1 agrandie, ses écrans, son pupitre tactile et sa quatrième place.
        shaped(RecipeCategory.MISC, ModItems.CREW_CAPSULE_MK2)
                .pattern("AGA")
                .pattern("DCD")
                .pattern("AHA")
                .define('A', ModTags.Items.PLATES_ALUMINUM_LITHIUM)
                .define('G', ModItems.REINFORCED_GLASS)
                .define('D', ModItems.SOLAR_CELL)
                .define('C', ModItems.CREW_CAPSULE)
                .define('H', ModItems.HEAT_SHIELD_TILE)
                .unlockedBy("has_crew_capsule", has(ModItems.CREW_CAPSULE))
                .save(output, key("crew_capsule_mk2"));
        // Capsule Mk3 « Odyssée » : la coque en titane d'un vaisseau de long voyage, autour d'une Mk2.
        shaped(RecipeCategory.MISC, ModItems.CREW_CAPSULE_MK3)
                .pattern("TGT")
                .pattern("RCR")
                .pattern("THT")
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('G', ModItems.REINFORCED_GLASS)
                .define('R', ModItems.CIRCUIT_BOARD)
                .define('C', ModItems.CREW_CAPSULE_MK2)
                .define('H', ModItems.HEAT_SHIELD_TILE)
                .unlockedBy("has_crew_capsule_mk2", has(ModItems.CREW_CAPSULE_MK2))
                .save(output, key("crew_capsule_mk3"));

        shaped(RecipeCategory.MISC, ModItems.TEST_SATELLITE)
                .pattern(" T ")
                .pattern("ACA")
                .pattern(" T ")
                .define('T', ModItems.CIRCUIT_BOARD)
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('C', ModItems.CIRCUIT_BOARD)
                .unlockedBy("has_aluminum_plate", has(ModTags.Items.PLATES_ALUMINUM))
                .save(output, key("test_satellite"));

        shaped(RecipeCategory.MISC, ModItems.WEATHER_SATELLITE)
                .pattern("DCD")
                .pattern("ASA")
                .pattern(" G ")
                .define('D', ModItems.SOLAR_CELL)
                .define('C', ModItems.CIRCUIT_BOARD)
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('S', Items.SNOWBALL)
                .define('G', ModItems.REINFORCED_GLASS)
                .unlockedBy("has_aluminum_plate", has(ModTags.Items.PLATES_ALUMINUM))
                .save(output, key("weather_satellite"));

        shaped(RecipeCategory.MISC, ModItems.OBSERVATION_SATELLITE)
                .pattern("DCD")
                .pattern("APA")
                .pattern(" G ")
                .define('D', ModItems.SOLAR_CELL)
                .define('C', ModItems.CIRCUIT_BOARD)
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('P', ModItems.OPTICAL_SENSOR)
                .define('G', ModItems.REINFORCED_GLASS)
                .unlockedBy("has_aluminum_plate", has(ModTags.Items.PLATES_ALUMINUM))
                .save(output, key("observation_satellite"));

        shaped(RecipeCategory.MISC, ModItems.COMMUNICATION_SATELLITE)
                .pattern("DED")
                .pattern("ACA")
                .pattern(" Q ")
                .define('D', ModItems.SOLAR_CELL)
                .define('E', ModItems.RADIO_TRANSMITTER)
                .define('C', ModItems.CIRCUIT_BOARD)
                .define('A', ModTags.Items.PLATES_ALUMINUM_LITHIUM)
                .define('Q', ModItems.RADIO_TRANSMITTER)
                .unlockedBy("has_aluminum_lithium_plate", has(ModTags.Items.PLATES_ALUMINUM_LITHIUM))
                .save(output, key("communication_satellite"));

        shaped(RecipeCategory.MISC, ModItems.NAVIGATION_SATELLITE)
                .pattern("DKD")
                .pattern("ACA")
                .pattern(" O ")
                .define('D', ModItems.SOLAR_CELL)
                .define('K', ModItems.NAVIGATION_UNIT)
                .define('C', ModItems.CIRCUIT_BOARD)
                .define('A', ModTags.Items.PLATES_ALUMINUM_LITHIUM)
                .define('O', ModItems.NAVIGATION_UNIT)
                .unlockedBy("has_aluminum_lithium_plate", has(ModTags.Items.PLATES_ALUMINUM_LITHIUM))
                .save(output, key("navigation_satellite"));

        shaped(RecipeCategory.MISC, ModItems.SPACE_TELESCOPE)
                .pattern("TPT")
                .pattern("DMD")
                .pattern("TCT")
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('P', ModItems.OPTICAL_SENSOR)
                .define('D', ModItems.SOLAR_CELL)
                .define('M', ModItems.OPTICAL_SENSOR)
                .define('C', ModItems.CIRCUIT_BOARD)
                .unlockedBy("has_titanium_plate", has(ModTags.Items.PLATES_TITANIUM))
                .save(output, key("space_telescope"));

        shaped(RecipeCategory.MISC, ModBlocks.TRACKING_STATION)
                .pattern(" A ")
                .pattern("ARA")
                .pattern("SCS")
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .define('S', ModTags.Items.PLATES_STEEL)
                .define('C', ModItems.CIRCUIT_BOARD)
                .unlockedBy("has_aluminum_plate", has(ModTags.Items.PLATES_ALUMINUM))
                .save(output, key("tracking_station"));
    }

    private void buildRocketParts() {
        // Ordinateur de conception : il remplace l'atelier d'assemblage.
        shaped(RecipeCategory.MISC, ModBlocks.DESIGN_COMPUTER)
                .pattern("GGG")
                .pattern("PRP")
                .pattern("PCP")
                .define('G', ModItems.DISPLAY_SCREEN)
                .define('P', ModTags.Items.PLATES_STEEL)
                .define('R', ModItems.FLIGHT_PROCESSOR)
                .define('C', ModBlocks.ENERGY_CABLE)
                .unlockedBy("has_steel_plate", has(ModTags.Items.PLATES_STEEL))
                .save(output, key("design_computer"));

        // Établi d'ingénieur : un plan de travail en acier sur un établi.
        shaped(RecipeCategory.MISC, ModBlocks.ENGINEER_WORKBENCH)
                .pattern("PPP")
                .pattern("ITI")
                .pattern("I I")
                .define('P', ModTags.Items.PLATES_STEEL)
                .define('I', Items.IRON_INGOT)
                .define('T', Items.CRAFTING_TABLE)
                .unlockedBy("has_steel_plate", has(ModTags.Items.PLATES_STEEL))
                .save(output, key("engineer_workbench"));

        // Blocs de construction des fusées. Moteurs et réservoirs se fabriquent autour de la pièce correspondante.
        shaped(RecipeCategory.MISC, ModBlocks.ROCKET_FUSELAGE, 8)
                .pattern("AAA")
                .pattern("A A")
                .pattern("AAA")
                .define('A', ModItems.ALUMINUM_PLATE)
                .unlockedBy("has_part", has(ModItems.ALUMINUM_PLATE))
                .save(output, key("rocket_fuselage"));
        shaped(RecipeCategory.MISC, ModBlocks.ROCKET_RACEWAY, 2)
                .pattern("FCF")
                .define('F', ModBlocks.ROCKET_FUSELAGE)
                .define('C', ModBlocks.ENERGY_CABLE)
                .unlockedBy("has_part", has(ModBlocks.ROCKET_FUSELAGE))
                .save(output, key("rocket_raceway"));
        shaped(RecipeCategory.MISC, ModBlocks.FLIGHT_COMPUTER)
                .pattern("FRF")
                .pattern("GCG")
                .pattern("FRF")
                .define('F', ModBlocks.ROCKET_FUSELAGE)
                .define('R', Items.REDSTONE)
                .define('G', Items.GOLD_INGOT)
                .define('C', ModItems.CIRCUIT_BOARD)
                .unlockedBy("has_part", has(ModBlocks.ROCKET_FUSELAGE))
                .save(output, key("flight_computer"));
        shaped(RecipeCategory.MISC, ModBlocks.ROCKET_PAYLOAD_BAY, 2)
                .pattern("FFF")
                .pattern("F F")
                .pattern("FFF")
                .define('F', ModBlocks.ROCKET_FUSELAGE)
                .unlockedBy("has_part", has(ModBlocks.ROCKET_FUSELAGE))
                .save(output, key("rocket_payload_bay"));
        shaped(RecipeCategory.MISC, ModBlocks.ROCKET_FAIRING, 8)
                .pattern(" A ")
                .pattern("ANA")
                .pattern(" A ")
                .define('A', ModItems.ALUMINUM_PLATE)
                .define('N', ModItems.NOSE_CONE)
                .unlockedBy("has_part", has(ModItems.NOSE_CONE))
                .save(output, key("rocket_fairing"));
        // Cabine : la coque de la capsule, sa vitre et son bouclier ; la capsule elle-même part avec le plan imprimé.
        shaped(RecipeCategory.MISC, ModBlocks.CREW_CABIN)
                .pattern("FGF")
                .pattern("FHF")
                .define('F', ModBlocks.ROCKET_FUSELAGE)
                .define('G', ModItems.REINFORCED_GLASS)
                .define('H', ModItems.HEAT_SHIELD_TILE)
                .unlockedBy("has_part", has(ModItems.CREW_CAPSULE))
                .save(output, key("crew_cabin"));
        shaped(RecipeCategory.MISC, ModBlocks.CABIN_ROOF)
                .pattern(" I ")
                .pattern("FFF")
                .define('F', ModBlocks.ROCKET_FUSELAGE)
                .define('I', Items.IRON_TRAPDOOR)
                .unlockedBy("has_part", has(ModBlocks.ROCKET_FUSELAGE))
                .save(output, key("cabin_roof"));
        shaped(RecipeCategory.MISC, ModBlocks.ESCAPE_TOWER)
                .pattern(" S ")
                .pattern("IMI")
                .define('S', ModItems.STEEL_GRATING)
                .define('I', ModItems.STEEL_GRATING)
                .define('M', ModItems.SOLID_MOTOR)
                .unlockedBy("has_part", has(ModItems.SOLID_MOTOR))
                .save(output, key("escape_tower"));
        // Corps de moteur à poudre : une enveloppe d'acier, que remplit le propergol des moteurs posés en dessous.
        shaped(RecipeCategory.MISC, ModBlocks.ROCKET_MOTOR_CASING, 2)
                .pattern("P P")
                .pattern("P P")
                .define('P', ModTags.Items.PLATES_STEEL)
                .unlockedBy("has_part", has(ModItems.SOLID_MOTOR))
                .save(output, key("rocket_motor_casing"));

        // Réservoirs : un cylindre de tôles, fabriqué par deux.
        tank(ModItems.STEEL_TANK, ModTags.Items.PLATES_STEEL, "steel_tank");
        tank(ModItems.ALUMINUM_TANK, ModTags.Items.PLATES_ALUMINUM, "aluminum_tank");
        tank(ModItems.ALUMINUM_LITHIUM_TANK, ModTags.Items.PLATES_ALUMINUM_LITHIUM, "aluminum_lithium_tank");
        tank(ModItems.TITANIUM_TANK, ModTags.Items.PLATES_TITANIUM, "titanium_tank");
        // Grand réservoir d'acier inoxydable : une virole de blocs d'acier et de tôles.
        shaped(RecipeCategory.MISC, ModItems.STAINLESS_TANK)
                .pattern("BPB")
                .pattern("P P")
                .pattern("BPB")
                .define('B', ModBlocks.STEEL_BLOCK)
                .define('P', ModTags.Items.PLATES_STEEL)
                .unlockedBy("has_steel_plate", has(ModTags.Items.PLATES_STEEL))
                .save(output, key("stainless_tank"));
        // Réservoir d'hydrogène : une virole d'aluminium-lithium sous sa couverture isolante.
        shaped(RecipeCategory.MISC, ModItems.HYDROGEN_TANK)
                .pattern("PIP")
                .pattern("I I")
                .pattern("PIP")
                .define('P', ModTags.Items.PLATES_ALUMINUM_LITHIUM)
                .define('I', ModItems.INSULATION_BLANKET)
                .unlockedBy("has_insulation_blanket", has(ModItems.INSULATION_BLANKET))
                .save(output, key("hydrogen_tank"));
        // Booster à poudre : un moteur à poudre allongé, son corps chargé de poudre.
        shaped(RecipeCategory.MISC, ModItems.SOLID_BOOSTER)
                .pattern(" M ")
                .pattern("PGP")
                .pattern("PGP")
                .define('M', ModItems.SOLID_MOTOR)
                .define('P', ModTags.Items.PLATES_STEEL)
                .define('G', Items.GUNPOWDER)
                .unlockedBy("has_part", has(ModItems.SOLID_MOTOR))
                .save(output, key("solid_booster"));
        // Moteur hypergolique : un moteur à pression, ses sphères d'ergols en titane, sa carte de commande.
        shaped(RecipeCategory.MISC, ModItems.HYPERGOLIC_ENGINE)
                .pattern(" B ")
                .pattern("TET")
                .pattern("T T")
                .define('B', ModItems.CIRCUIT_BOARD)
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('E', ModItems.PRESSURE_FED_ENGINE)
                .unlockedBy("has_part", has(ModItems.PRESSURE_FED_ENGINE))
                .save(output, key("hypergolic_engine"));
        // Moteur à vide : un moteur cryogénique, sa grande tuyère en superalliage.
        shaped(RecipeCategory.MISC, ModItems.VACUUM_ENGINE)
                .pattern("TBT")
                .pattern("AEA")
                .pattern("A A")
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('B', ModItems.CIRCUIT_BOARD)
                .define('A', ModTags.Items.PLATES_ALUMINUM_LITHIUM)
                .define('E', ModItems.HYDROLOX_ENGINE)
                .unlockedBy("has_part", has(ModItems.HYDROLOX_ENGINE))
                .save(output, key("vacuum_engine"));
        // Cœur de réacteur : des barreaux de combustible gainés de titane dans un modérateur, et sa régulation.
        shaped(RecipeCategory.MISC, ModItems.REACTOR_CORE)
                .pattern("THT")
                .pattern("IPI")
                .pattern("THT")
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('H', ModItems.HEATING_ELEMENT)
                .define('I', ModItems.INSULATION_BLANKET)
                .define('P', ModItems.FLIGHT_PROCESSOR)
                .unlockedBy("has_heating_element", has(ModItems.HEATING_ELEMENT))
                .save(output, key("reactor_core"));
        // Moteur nucléaire thermique : le cœur de réacteur, son blindage, sa grande tuyère.
        shaped(RecipeCategory.MISC, ModItems.NUCLEAR_THERMAL_ENGINE)
                .pattern("ICI")
                .pattern("TRT")
                .pattern(" V ")
                .define('I', ModItems.INSULATION_BLANKET)
                .define('C', ModItems.REACTOR_CORE)
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('R', ModItems.HEAT_SHIELD_TILE)
                .define('V', ModItems.VACUUM_ENGINE)
                .unlockedBy("has_reactor_core", has(ModItems.REACTOR_CORE))
                .save(output, key("nuclear_thermal_engine"));
        // Moteur à fusion : deux cœurs de réacteur pour allumer le plasma, une étoile du Nether pour ses aimants, sa tuyère.
        shaped(RecipeCategory.MISC, ModItems.FUSION_ENGINE)
                .pattern("TCT")
                .pattern("CSC")
                .pattern("TVT")
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('C', ModItems.REACTOR_CORE)
                .define('S', Items.NETHER_STAR)
                .define('V', ModItems.NUCLEAR_THERMAL_ENGINE)
                .unlockedBy("has_nuclear_thermal_engine", has(ModItems.NUCLEAR_THERMAL_ENGINE))
                .save(output, key("fusion_engine"));
        // Propulseur ionique : son processeur de puissance, sa chambre chauffée, ses grilles d'accélération.
        shaped(RecipeCategory.MISC, ModItems.ION_THRUSTER)
                .pattern(" P ")
                .pattern("THT")
                .pattern("TGT")
                .define('P', ModItems.FLIGHT_PROCESSOR)
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('H', ModItems.HEATING_ELEMENT)
                .define('G', ModItems.STEEL_GRATING)
                .unlockedBy("has_flight_processor", has(ModItems.FLIGHT_PROCESSOR))
                .save(output, key("ion_thruster"));
        // Voile solaire : des couvertures réfléchissantes tendues sur des mâts d'aluminium-lithium, et sa navigation.
        shaped(RecipeCategory.MISC, ModItems.SOLAR_SAIL)
                .pattern("BLB")
                .pattern("LNL")
                .pattern("BLB")
                .define('B', ModItems.INSULATION_BLANKET)
                .define('L', ModTags.Items.PLATES_ALUMINUM_LITHIUM)
                .define('N', ModItems.NAVIGATION_UNIT)
                .unlockedBy("has_insulation_blanket", has(ModItems.INSULATION_BLANKET))
                .save(output, key("solar_sail"));

        // Moteur à poudre : un tube d'acier rempli de poudre.
        shaped(RecipeCategory.MISC, ModItems.SOLID_MOTOR)
                .pattern(" P ")
                .pattern("PGP")
                .pattern("PGP")
                .define('P', ModTags.Items.PLATES_STEEL)
                .define('G', Items.GUNPOWDER)
                .unlockedBy("has_steel_plate", has(ModTags.Items.PLATES_STEEL))
                .save(output, key("solid_motor"));

        // Moteurs à ergols liquides : chacun se construit à partir du précédent.
        shaped(RecipeCategory.MISC, ModItems.PRESSURE_FED_ENGINE)
                .pattern(" C ")
                .pattern("SIS")
                .pattern("S S")
                .define('C', Tags.Items.INGOTS_COPPER)
                .define('S', ModTags.Items.PLATES_STEEL)
                .define('I', Tags.Items.INGOTS_IRON)
                .unlockedBy("has_steel_plate", has(ModTags.Items.PLATES_STEEL))
                .save(output, key("pressure_fed_engine"));

        shaped(RecipeCategory.MISC, ModItems.KEROLOX_ENGINE)
                .pattern("SRS")
                .pattern("TET")
                .pattern("T T")
                .define('S', ModTags.Items.PLATES_STEEL)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('E', ModItems.PRESSURE_FED_ENGINE)
                .unlockedBy("has_titanium_plate", has(ModTags.Items.PLATES_TITANIUM))
                .save(output, key("kerolox_engine"));

        shaped(RecipeCategory.MISC, ModItems.HYDROLOX_ENGINE)
                .pattern("ARA")
                .pattern("TET")
                .pattern("T T")
                .define('A', ModTags.Items.PLATES_ALUMINUM_LITHIUM)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('E', ModItems.KEROLOX_ENGINE)
                .unlockedBy("has_titanium_plate", has(ModTags.Items.PLATES_TITANIUM))
                .save(output, key("hydrolox_engine"));

        shaped(RecipeCategory.MISC, ModItems.NOSE_CONE)
                .pattern(" A ")
                .pattern("A A")
                .pattern("A A")
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .unlockedBy("has_aluminum_plate", has(ModTags.Items.PLATES_ALUMINUM))
                .save(output, key("nose_cone"));

        shaped(RecipeCategory.MISC, ModItems.INTERSTAGE, 2)
                .pattern("A A")
                .pattern("SSS")
                .pattern("A A")
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('S', ModTags.Items.PLATES_STEEL)
                .unlockedBy("has_aluminum_plate", has(ModTags.Items.PLATES_ALUMINUM))
                .save(output, key("interstage"));

        shaped(RecipeCategory.MISC, ModItems.SOUNDING_PAYLOAD)
                .pattern(" G ")
                .pattern("ARA")
                .pattern("ACA")
                .define('G', ModItems.REINFORCED_GLASS)
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .define('C', ModItems.NAVIGATION_UNIT)
                .unlockedBy("has_aluminum_plate", has(ModTags.Items.PLATES_ALUMINUM))
                .save(output, key("sounding_payload"));
    }

    private void tank(ItemLike result, TagKey<Item> plates, String name) {
        shaped(RecipeCategory.MISC, result, 2)
                .pattern("PPP")
                .pattern("P P")
                .pattern("PPP")
                .define('P', plates)
                .unlockedBy("has_plate", has(plates))
                .save(output, key(name));
    }

    /** Câbles et tuyaux, fabriqués par six. */
    private void buildConduits() {
        shaped(RecipeCategory.MISC, ModBlocks.ENERGY_CABLE, 6)
                .pattern("WWW")
                .pattern("CRC")
                .pattern("WWW")
                .define('W', ItemTags.WOOL)
                .define('C', Tags.Items.INGOTS_COPPER)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .unlockedBy("has_copper_ingot", has(Tags.Items.INGOTS_COPPER))
                .save(output, key("energy_cable"));

        shaped(RecipeCategory.MISC, ModBlocks.FLUID_PIPE, 6)
                .pattern("PPP")
                .pattern("   ")
                .pattern("PPP")
                .define('P', ModTags.Items.PLATES_STEEL)
                .unlockedBy("has_steel_plate", has(ModTags.Items.PLATES_STEEL))
                .save(output, key("fluid_pipe"));

        // Tuyau à gaz : un tube de verre cerclé de cuivre.
        shaped(RecipeCategory.MISC, ModBlocks.GAS_PIPE, 6)
                .pattern("GGG")
                .pattern("C C")
                .pattern("GGG")
                .define('G', Tags.Items.GLASS_PANES)
                .define('C', Tags.Items.INGOTS_COPPER)
                .unlockedBy("has_copper_ingot", has(Tags.Items.INGOTS_COPPER))
                .save(output, key("gas_pipe"));
    }

    /** Fabrication des machines à carburants, à partir des tôles produites au laminoir. */
    private void buildFuelCrafting() {
        shaped(RecipeCategory.MISC, ModBlocks.ELECTROLYZER)
                .pattern("PGP")
                .pattern("CBC")
                .pattern("PRP")
                .define('P', ModTags.Items.PLATES_STEEL)
                .define('G', Tags.Items.GLASS_BLOCKS)
                .define('C', Tags.Items.INGOTS_COPPER)
                .define('B', Items.BUCKET)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .unlockedBy("has_steel_plate", has(ModTags.Items.PLATES_STEEL))
                .save(output, key("electrolyzer"));
        // Réacteur de Sabatier : une chambre de catalyse en cuivre, une résistance chauffante et un circuit de commande.
        shaped(RecipeCategory.MISC, ModBlocks.SABATIER_REACTOR)
                .pattern("PCP")
                .pattern("HBH")
                .pattern("PRP")
                .define('P', ModTags.Items.PLATES_STEEL)
                .define('C', ModItems.CIRCUIT_BOARD)
                .define('H', ModItems.HEATING_ELEMENT)
                .define('B', Tags.Items.STORAGE_BLOCKS_COPPER)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .unlockedBy("has_electrolyzer", has(ModBlocks.ELECTROLYZER))
                .save(output, key("sabatier_reactor"));
        // Extracteur d'hélium-3 : un four rotatif (des tôles de titane, des résistances), sa trémie et son séparateur.
        shaped(RecipeCategory.MISC, ModBlocks.HELIUM3_EXTRACTOR)
                .pattern("THT")
                .pattern("HMH")
                .pattern("TCT")
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('H', ModItems.HEATING_ELEMENT)
                .define('M', ModItems.ELECTRIC_MOTOR)
                .define('C', ModItems.CIRCUIT_BOARD)
                .unlockedBy("has_moon_regolith", has(ModBlocks.MOON_REGOLITH))
                .save(output, key("helium3_extractor"));
        // Réacteur à fusion : un tore de tôles de titane, deux cœurs de réacteur pour allumer le plasma, une étoile du
        // Nether pour ses aimants, des barres de cuivre pour ses bobines, son circuit de commande.
        shaped(RecipeCategory.MISC, ModBlocks.FUSION_REACTOR)
                .pattern("TBT")
                .pattern("CSC")
                .pattern("TRT")
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('B', Tags.Items.STORAGE_BLOCKS_COPPER)
                .define('C', ModItems.REACTOR_CORE)
                .define('S', Items.NETHER_STAR)
                .define('R', ModItems.CIRCUIT_BOARD)
                .unlockedBy("has_reactor_core", has(ModItems.REACTOR_CORE))
                .save(output, key("fusion_reactor"));
        // Chambre de Casimir : deux miroirs de diamant poli, sous une cloche de verre, et la pompe qui y fait le vide.
        shaped(RecipeCategory.MISC, ModBlocks.CASIMIR_CHAMBER)
                .pattern("TGT")
                .pattern("DCD")
                .pattern("TMT")
                .define('T', ModTags.Items.PLATES_TITANIUM)
                .define('G', Tags.Items.GLASS_BLOCKS)
                .define('D', Tags.Items.GEMS_DIAMOND)
                .define('C', ModItems.CIRCUIT_BOARD)
                .define('M', ModItems.ELECTRIC_MOTOR)
                .unlockedBy("has_fusion_reactor", has(ModBlocks.FUSION_REACTOR))
                .save(output, key("casimir_chamber"));

        shaped(RecipeCategory.MISC, ModBlocks.CRYOGENIC_LIQUEFIER)
                .pattern("APA")
                .pattern("IRI")
                .pattern("AAA")
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('P', ModItems.ELECTRIC_MOTOR)
                .define('I', Items.PACKED_ICE)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .unlockedBy("has_aluminum_plate", has(ModTags.Items.PLATES_ALUMINUM))
                .save(output, key("cryogenic_liquefier"));

        shaped(RecipeCategory.MISC, ModBlocks.REFINERY)
                .pattern("PPP")
                .pattern("PFP")
                .pattern("CRC")
                .define('P', ModTags.Items.PLATES_STEEL)
                .define('F', ModItems.HEATING_ELEMENT)
                .define('C', Tags.Items.INGOTS_COPPER)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .unlockedBy("has_steel_plate", has(ModTags.Items.PLATES_STEEL))
                .save(output, key("refinery"));

        buildRocketParts();
        buildSatellites();
        buildStations();
        buildSpaceport();

        shaped(RecipeCategory.MISC, ModBlocks.CRYOGENIC_TANK)
                .pattern("AWA")
                .pattern("AGA")
                .pattern("AWA")
                .define('A', ModTags.Items.PLATES_ALUMINUM)
                .define('W', ModItems.INSULATION_BLANKET)
                .define('G', Tags.Items.GLASS_BLOCKS)
                .unlockedBy("has_aluminum_plate", has(ModTags.Items.PLATES_ALUMINUM))
                .save(output, key("cryogenic_tank"));
    }

    /** Production des gaz et des ergols. Quantités en mB, énergie en FE, durée en ticks. */
    private void buildFuels() {
        // Électrolyse : l'eau donne deux volumes d'hydrogène pour un volume d'oxygène.
        fluid(ModRecipes.ELECTROLYSIS, "water_electrolysis", Optional.empty(), Optional.of(stack(Fluids.WATER, 100)),
                400, 20, stack(ModFluids.HYDROGEN.get(), 200), stack(ModFluids.OXYGEN.get(), 100));

        // Liquéfaction : l'hydrogène, plus froid, demande plus d'énergie que l'oxygène.
        fluid(ModRecipes.LIQUEFACTION, "liquid_oxygen", Optional.empty(), Optional.of(stack(ModFluids.OXYGEN.get(), 100)),
                800, 40, stack(ModFluids.LIQUID_OXYGEN.get(), 50));
        fluid(ModRecipes.LIQUEFACTION, "liquid_hydrogen", Optional.empty(), Optional.of(stack(ModFluids.HYDROGEN.get(), 100)),
                1200, 40, stack(ModFluids.LIQUID_HYDROGEN.get(), 50));

        // Liquéfaction du méthane : à 111 K, il demande moins de froid que l'oxygène.
        fluid(ModRecipes.LIQUEFACTION, "liquid_methane", Optional.empty(), Optional.of(stack(ModFluids.METHANE.get(), 100)),
                600, 40, stack(ModFluids.LIQUID_METHANE.get(), 50));
        // Réaction de Sabatier : le carbone d'un charbon, brûlé en gaz carbonique, et quatre volumes d'hydrogène donnent
        // du méthane et de l'eau.
        fluid(ModRecipes.SABATIER, "methane", Optional.of(tag(ItemTags.COALS, 1)), Optional.of(stack(ModFluids.HYDROGEN.get(), 800)),
                1600, 80, stack(ModFluids.METHANE.get(), 400), stack(net.minecraft.world.level.material.Fluids.WATER, 200));

        // Hélium-3 : cuit vers 700 °C, le régolithe lunaire libère l'hélium-3 et l'hydrogène que le vent solaire y a
        // déposés ; le minerai d'ilménite, qui les retient mieux, en donne quatre fois plus.
        fluid(ModRecipes.HELIUM3, "helium_3_from_regolith", Optional.of(item(ModBlocks.MOON_REGOLITH.get(), 1)), Optional.empty(),
                4000, 200, stack(ModFluids.HELIUM_3.get(), 10), stack(ModFluids.HYDROGEN.get(), 50));
        fluid(ModRecipes.HELIUM3, "helium_3_from_ilmenite", Optional.of(item(ModBlocks.MOON_ILMENITE_ORE.get(), 1)), Optional.empty(),
                4000, 200, stack(ModFluids.HELIUM_3.get(), 40), stack(ModFluids.HYDROGEN.get(), 50));

        // Raffinage : le charbon est transformé en kérosène.
        fluid(ModRecipes.REFINING, "rp1", Optional.of(tag(ItemTags.COALS, 1)), Optional.empty(),
                2000, 100, stack(ModFluids.RP1.get(), 250));
    }

    private void fluid(FluidRecipe.Kind kind, String name, Optional<SizedIngredient> itemInput,
            Optional<FluidStackTemplate> fluidInput, int energy, int time, FluidStackTemplate... outputs) {
        output.accept(key(name), new FluidRecipe(kind, itemInput, fluidInput, List.of(outputs), energy, time), null);
    }

    private static FluidStackTemplate stack(Fluid fluid, int amount) {
        return new FluidStackTemplate(fluid, amount);
    }

    /**
     * Fabrication des machines à l'établi. Les deux premières ne demandent que des matériaux du jeu et une résistance
     * chauffante, elle-même faite de lingots.
     */
    private void buildMachineCrafting() {
        shaped(RecipeCategory.MISC, ModBlocks.COMBUSTION_GENERATOR)
                .pattern("III")
                .pattern("CFC")
                .pattern("IRI")
                .define('I', Tags.Items.INGOTS_IRON)
                .define('C', Tags.Items.INGOTS_COPPER)
                .define('F', ModItems.HEATING_ELEMENT)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .unlockedBy("has_heating_element", has(ModItems.HEATING_ELEMENT))
                .save(output, key("combustion_generator"));

        shaped(RecipeCategory.MISC, ModBlocks.ARC_FOUNDRY)
                .pattern("ICI")
                .pattern("CBC")
                .pattern("IRI")
                .define('I', Tags.Items.INGOTS_IRON)
                .define('C', Tags.Items.INGOTS_COPPER)
                .define('B', ModItems.HEATING_ELEMENT)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .unlockedBy("has_heating_element", has(ModItems.HEATING_ELEMENT))
                .save(output, key("arc_foundry"));

        shaped(RecipeCategory.MISC, ModBlocks.ROLLING_MILL)
                .pattern("SPS")
                .pattern("I I")
                .pattern("SRS")
                .define('S', ModTags.Items.INGOTS_STEEL)
                .define('P', ModItems.ELECTRIC_MOTOR)
                .define('I', Tags.Items.INGOTS_IRON)
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .unlockedBy("has_steel_ingot", has(ModTags.Items.INGOTS_STEEL))
                .save(output, key("rolling_mill"));
    }

    /** Fonderie à arc : alliages. */
    private void buildAlloying() {
        // Acier : fer + carbone (charbon ou charbon de bois).
        machine(ModRecipes.ALLOYING, "steel_ingot", ModItems.STEEL_INGOT, 1, 4000, 200,
                tag(Tags.Items.INGOTS_IRON, 1), tag(ItemTags.COALS, 1));
        // Aluminium-lithium : alliage léger des réservoirs de fusée.
        machine(ModRecipes.ALLOYING, "aluminum_lithium_ingot", ModItems.ALUMINUM_LITHIUM_INGOT, 5, 9000, 300,
                tag(ModTags.Items.INGOTS_ALUMINUM, 4), tag(ModTags.Items.INGOTS_LITHIUM, 1));
    }

    /** Laminoir : un lingot donne une tôle. */
    private void buildRolling() {
        machine(ModRecipes.ROLLING, "steel_plate", ModItems.STEEL_PLATE, 1, 1000, 100, tag(ModTags.Items.INGOTS_STEEL, 1));
        machine(ModRecipes.ROLLING, "aluminum_plate", ModItems.ALUMINUM_PLATE, 1, 1000, 100, tag(ModTags.Items.INGOTS_ALUMINUM, 1));
        machine(ModRecipes.ROLLING, "titanium_plate", ModItems.TITANIUM_PLATE, 1, 2000, 100, tag(ModTags.Items.INGOTS_TITANIUM, 1));
        // Matière exotique : une perle d'ender, qui plie déjà l'espace, tenue une minute entre les miroirs, à 200 FE/t.
        machine(ModRecipes.CASIMIR, "exotic_matter", ModItems.EXOTIC_MATTER, 1, 240_000, 1200, item(Items.ENDER_PEARL, 1));
        machine(ModRecipes.ROLLING, "aluminum_lithium_plate", ModItems.ALUMINUM_LITHIUM_PLATE, 1, 1000, 100,
                tag(ModTags.Items.INGOTS_ALUMINUM_LITHIUM, 1));
    }

    /**
     * Recette de machine.
     *
     * @param energy énergie totale en FE
     * @param time   durée en ticks (20 ticks = 1 seconde)
     */
    private void machine(MachineRecipe.Kind kind, String name, ItemLike result, int count, int energy, int time,
            SizedIngredient... ingredients) {
        MachineRecipe recipe = new MachineRecipe(kind, List.of(ingredients),
                new ItemStackTemplate(result.asItem(), count), energy, time);
        output.accept(key(name), recipe, null);
    }

    /** Ingrédient désigné par un tag, avec sa quantité. */
    private SizedIngredient tag(TagKey<Item> tag, int count) {
        return new SizedIngredient(Ingredient.of(items.getOrThrow(tag)), count);
    }

    /** Ingrédient désigné par un objet, avec sa quantité. */
    private SizedIngredient item(ItemLike item, int count) {
        return new SizedIngredient(Ingredient.of(item), count);
    }

    /** Cuisson au four (200 ticks) et au haut fourneau (100 ticks) de chaque ingrédient vers le lingot. */
    private void smeltOre(String resultName, ItemLike result, float experience, ItemLike... ingredients) {
        for (ItemLike ingredient : ingredients) {
            String ingredientName = getItemName(ingredient);
            SimpleCookingRecipeBuilder.smelting(Ingredient.of(ingredient), RecipeCategory.MISC, CookingBookCategory.MISC,
                            result, experience, 200)
                    .group(resultName)
                    .unlockedBy(getHasName(ingredient), has(ingredient))
                    .save(output, key(resultName + "_from_smelting_" + ingredientName));
            SimpleCookingRecipeBuilder.blasting(Ingredient.of(ingredient), RecipeCategory.MISC, CookingBookCategory.MISC,
                            result, experience, 100)
                    .group(resultName)
                    .unlockedBy(getHasName(ingredient), has(ingredient))
                    .save(output, key(resultName + "_from_blasting_" + ingredientName));
        }
    }

    /** 9 lingots -> 1 bloc, et 1 bloc -> 9 lingots. */
    private void storageBlock(String metal, ItemLike ingot, ItemLike block) {
        shaped(RecipeCategory.BUILDING_BLOCKS, block)
                .pattern("###")
                .pattern("###")
                .pattern("###")
                .define('#', ingot)
                .unlockedBy(getHasName(ingot), has(ingot))
                .save(output, key(metal + "_block"));
        shapeless(RecipeCategory.MISC, ingot, 9)
                .requires(block)
                .unlockedBy(getHasName(block), has(block))
                .save(output, key(metal + "_ingot_from_" + metal + "_block"));
    }

    /** Identifiant de recette dans l'espace de noms du mod. */
    private static ResourceKey<Recipe<?>> key(String name) {
        return ResourceKey.create(Registries.RECIPE, Orbitalis.id(name));
    }

    /** Lanceur appelé par le datagen. */
    public static class Runner extends RecipeProvider.Runner {
        public Runner(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries);
        }

        @Override
        protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
            return new OrbitalisRecipeProvider(registries, output);
        }

        @Override
        public String getName() {
            return "Recettes d'Orbitalis";
        }
    }
}
