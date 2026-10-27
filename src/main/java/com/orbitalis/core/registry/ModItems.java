package com.orbitalis.core.registry;

import com.orbitalis.phase1.rocket.RocketBlueprintItem;
import com.orbitalis.Orbitalis;
import com.orbitalis.core.guide.GuideBookItem;

import com.orbitalis.phase1.rocket.RocketItem;
import com.orbitalis.phase1.spaceport.PadKitItem;
import com.orbitalis.phase1.spaceport.RecoveryRemoteItem;
import com.orbitalis.phase2.station.StationModuleItem;
import com.orbitalis.core.creative.CreativeResearchItem;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.resources.ResourceKey;
import com.orbitalis.phase2.survival.SpaceSuitItem;
import com.orbitalis.phase2.survival.SuitModule;
import com.orbitalis.phase2.survival.SuitModuleItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.component.DataComponents;
import com.orbitalis.phase2.survival.SpaceRules;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registre de tous les items du mod.
 * Les items des blocs (minerais, blocs de métal...) sont enregistrés par {@link ModBlocks}.
 */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Orbitalis.MODID);

    /** Apparence portée des deux combinaisons. */
    public static final ResourceKey<EquipmentAsset> SUIT_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, Orbitalis.id("spacesuit"));
    public static final ResourceKey<EquipmentAsset> ADVANCED_SUIT_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, Orbitalis.id("advanced_spacesuit"));

    /** Manuel d'Orbitalis : livre de tutoriels, offert à la première connexion. */
    public static final DeferredItem<GuideBookItem> GUIDE_BOOK =
            ITEMS.registerItem("guide_book", GuideBookItem::new, properties -> properties.stacksTo(1));

    // ------------------------------------------------------------------
    // Phase 1 - Matériaux : minerais bruts
    // ------------------------------------------------------------------
    /** Bauxite brute : source de l'aluminium. */
    public static final DeferredItem<Item> RAW_BAUXITE = ITEMS.registerSimpleItem("raw_bauxite");
    /** Matière exotique : d'énergie négative, tirée du vide par la chambre de Casimir ; elle tiendra ouverte la bulle de distorsion. */
    public static final DeferredItem<Item> EXOTIC_MATTER = ITEMS.registerSimpleItem("exotic_matter");
    /** Titane brut. */
    public static final DeferredItem<Item> RAW_TITANIUM = ITEMS.registerSimpleItem("raw_titanium");
    /** Lithium brut : sert aux alliages aluminium-lithium et aux batteries. */
    public static final DeferredItem<Item> RAW_LITHIUM = ITEMS.registerSimpleItem("raw_lithium");

    // ------------------------------------------------------------------
    // Phase 1 - Matériaux : lingots
    // ------------------------------------------------------------------
    /** Acier : premier matériau de structure de la progression. */
    public static final DeferredItem<Item> STEEL_INGOT = ITEMS.registerSimpleItem("steel_ingot");
    public static final DeferredItem<Item> ALUMINUM_INGOT = ITEMS.registerSimpleItem("aluminum_ingot");
    public static final DeferredItem<Item> TITANIUM_INGOT = ITEMS.registerSimpleItem("titanium_ingot");
    public static final DeferredItem<Item> LITHIUM_INGOT = ITEMS.registerSimpleItem("lithium_ingot");

    /** Alliage aluminium-lithium : plus léger que l'aluminium, utilisé pour les réservoirs. */
    public static final DeferredItem<Item> ALUMINUM_LITHIUM_INGOT = ITEMS.registerSimpleItem("aluminum_lithium_ingot");

    // ------------------------------------------------------------------
    // Phase 1 - Matériaux : tôles (produites au laminoir)
    // ------------------------------------------------------------------
    public static final DeferredItem<Item> STEEL_PLATE = ITEMS.registerSimpleItem("steel_plate");
    public static final DeferredItem<Item> ALUMINUM_PLATE = ITEMS.registerSimpleItem("aluminum_plate");
    public static final DeferredItem<Item> TITANIUM_PLATE = ITEMS.registerSimpleItem("titanium_plate");
    public static final DeferredItem<Item> ALUMINUM_LITHIUM_PLATE = ITEMS.registerSimpleItem("aluminum_lithium_plate");
    // ------------------------------------------------------------------
    // Composants de fabrication : ils n'entrent que dans les recettes
    // ------------------------------------------------------------------
    public static final DeferredItem<com.orbitalis.core.item.ComponentItem> CIRCUIT_BOARD = ITEMS.registerItem("circuit_board",
            com.orbitalis.core.item.ComponentItem::new);
    public static final DeferredItem<com.orbitalis.core.item.ComponentItem> FLIGHT_PROCESSOR = ITEMS.registerItem("flight_processor",
            com.orbitalis.core.item.ComponentItem::new);
    public static final DeferredItem<com.orbitalis.core.item.ComponentItem> RADIO_TRANSMITTER = ITEMS.registerItem("radio_transmitter",
            com.orbitalis.core.item.ComponentItem::new);
    public static final DeferredItem<com.orbitalis.core.item.ComponentItem> SOLAR_CELL = ITEMS.registerItem("solar_cell",
            com.orbitalis.core.item.ComponentItem::new);
    public static final DeferredItem<com.orbitalis.core.item.ComponentItem> ELECTRIC_MOTOR = ITEMS.registerItem("electric_motor",
            com.orbitalis.core.item.ComponentItem::new);
    public static final DeferredItem<com.orbitalis.core.item.ComponentItem> STEEL_GRATING = ITEMS.registerItem("steel_grating",
            com.orbitalis.core.item.ComponentItem::new);
    public static final DeferredItem<com.orbitalis.core.item.ComponentItem> INSULATION_BLANKET = ITEMS.registerItem("insulation_blanket",
            com.orbitalis.core.item.ComponentItem::new);
    public static final DeferredItem<com.orbitalis.core.item.ComponentItem> HEATING_ELEMENT = ITEMS.registerItem("heating_element",
            com.orbitalis.core.item.ComponentItem::new);
    public static final DeferredItem<com.orbitalis.core.item.ComponentItem> NAVIGATION_UNIT = ITEMS.registerItem("navigation_unit",
            com.orbitalis.core.item.ComponentItem::new);
    public static final DeferredItem<com.orbitalis.core.item.ComponentItem> OPTICAL_SENSOR = ITEMS.registerItem("optical_sensor",
            com.orbitalis.core.item.ComponentItem::new);
    public static final DeferredItem<com.orbitalis.core.item.ComponentItem> REINFORCED_GLASS = ITEMS.registerItem("reinforced_glass",
            com.orbitalis.core.item.ComponentItem::new);
    public static final DeferredItem<com.orbitalis.core.item.ComponentItem> DISPLAY_SCREEN = ITEMS.registerItem("display_screen",
            com.orbitalis.core.item.ComponentItem::new);
    public static final DeferredItem<com.orbitalis.core.item.ComponentItem> LED_LAMP = ITEMS.registerItem("led_lamp",
            com.orbitalis.core.item.ComponentItem::new);
    public static final DeferredItem<com.orbitalis.core.item.ComponentItem> HEAT_SHIELD_TILE = ITEMS.registerItem("heat_shield_tile",
            com.orbitalis.core.item.ComponentItem::new);
    public static final DeferredItem<com.orbitalis.core.item.ComponentItem> BATTERY_CELL = ITEMS.registerItem("battery_cell",
            com.orbitalis.core.item.ComponentItem::new);
    public static final DeferredItem<com.orbitalis.core.item.ComponentItem> CO2_FILTER = ITEMS.registerItem("co2_filter",
            com.orbitalis.core.item.ComponentItem::new);
    public static final DeferredItem<com.orbitalis.core.item.ComponentItem> REACTOR_CORE = ITEMS.registerItem("reactor_core",
            com.orbitalis.core.item.ComponentItem::new);

    // ------------------------------------------------------------------
    // Phase 1 - Pièces de fusée (leurs caractéristiques sont dans RocketParts)
    // ------------------------------------------------------------------
    /** Moteur à poudre : embarque son propergol, pour les fusées-sondes. */
    public static final DeferredItem<com.orbitalis.phase1.rocket.RocketPartItem> SOLID_MOTOR = ITEMS.registerItem("solid_motor",
            properties -> new com.orbitalis.phase1.rocket.RocketPartItem(properties.stacksTo(16), ModBlocks.ROCKET_ENGINE_SOLID));
    /** Petit moteur à ergols liquides, alimenté par la pression des réservoirs. */
    public static final DeferredItem<com.orbitalis.phase1.rocket.RocketPartItem> PRESSURE_FED_ENGINE = ITEMS.registerItem("pressure_fed_engine",
            properties -> new com.orbitalis.phase1.rocket.RocketPartItem(properties.stacksTo(16), ModBlocks.ROCKET_ENGINE_PRESSURE));
    /** Moteur à turbopompes RP-1 / oxygène liquide. */
    public static final DeferredItem<com.orbitalis.phase1.rocket.RocketPartItem> KEROLOX_ENGINE = ITEMS.registerItem("kerolox_engine",
            properties -> new com.orbitalis.phase1.rocket.RocketPartItem(properties.stacksTo(16), ModBlocks.ROCKET_ENGINE_KEROLOX));
    /** Moteur cryogénique hydrogène / oxygène liquides. */
    public static final DeferredItem<com.orbitalis.phase1.rocket.RocketPartItem> HYDROLOX_ENGINE = ITEMS.registerItem("hydrolox_engine",
            properties -> new com.orbitalis.phase1.rocket.RocketPartItem(properties.stacksTo(16), ModBlocks.ROCKET_ENGINE_HYDROLOX));
    /** Booster à poudre : un gros propulseur d'appoint, son propergol coulé dedans, pour le premier étage. */
    public static final DeferredItem<com.orbitalis.phase1.rocket.RocketPartItem> SOLID_BOOSTER = ITEMS.registerItem("solid_booster",
            properties -> new com.orbitalis.phase1.rocket.RocketPartItem(properties.stacksTo(16), ModBlocks.ROCKET_ENGINE_BOOSTER));
    /** Moteur hypergolique : ses ergols stockables sont chargés à l'usine, il n'a pas besoin de plein au pas de tir. */
    public static final DeferredItem<com.orbitalis.phase1.rocket.RocketPartItem> HYPERGOLIC_ENGINE = ITEMS.registerItem("hypergolic_engine",
            properties -> new com.orbitalis.phase1.rocket.RocketPartItem(properties.stacksTo(16), ModBlocks.ROCKET_ENGINE_HYPERGOLIC));
    /** Moteur à vide : un moteur cryogénique d'étage supérieur, à la tuyère démesurée, fait pour le vide. */
    public static final DeferredItem<com.orbitalis.phase1.rocket.RocketPartItem> VACUUM_ENGINE = ITEMS.registerItem("vacuum_engine",
            properties -> new com.orbitalis.phase1.rocket.RocketPartItem(properties.stacksTo(16), ModBlocks.ROCKET_ENGINE_VACUUM));
    /** Moteur nucléaire thermique : un réacteur chauffe de l'hydrogène seul, pour les étages supérieurs. */
    public static final DeferredItem<com.orbitalis.phase1.rocket.RocketPartItem> NUCLEAR_THERMAL_ENGINE = ITEMS.registerItem("nuclear_thermal_engine",
            properties -> new com.orbitalis.phase1.rocket.RocketPartItem(properties.stacksTo(16), ModBlocks.ROCKET_ENGINE_NUCLEAR));
    /** Moteur à fusion : un plasma de deutérium et d'hélium-3 chauffe l'hydrogène, pour les étages supérieurs. */
    public static final DeferredItem<com.orbitalis.phase1.rocket.RocketPartItem> FUSION_ENGINE = ITEMS.registerItem("fusion_engine",
            properties -> new com.orbitalis.phase1.rocket.RocketPartItem(properties.stacksTo(16), ModBlocks.ROCKET_ENGINE_FUSION));
    /** Propulseur ionique : un étage supérieur à xénon, à très faible poussée, pour les sondes et les satellites. */
    public static final DeferredItem<com.orbitalis.phase1.rocket.RocketPartItem> ION_THRUSTER = ITEMS.registerItem("ion_thruster",
            properties -> new com.orbitalis.phase1.rocket.RocketPartItem(properties.stacksTo(16), ModBlocks.ROCKET_ION_THRUSTER));
    /** Voile solaire : un étage supérieur sans ergol, que pousse la lumière du Soleil. */
    public static final DeferredItem<com.orbitalis.phase1.rocket.RocketPartItem> SOLAR_SAIL = ITEMS.registerItem("solar_sail",
            properties -> new com.orbitalis.phase1.rocket.RocketPartItem(properties.stacksTo(16), ModBlocks.ROCKET_SOLAR_SAIL));

    /** Moteur à combustion étagée RP-1 / oxygène liquide : deux fois la poussée du moteur à turbopompes, meilleur rendement. */
    public static final DeferredItem<com.orbitalis.phase1.rocket.RocketPartItem> STAGED_COMBUSTION_ENGINE = ITEMS.registerItem("staged_combustion_engine",
            properties -> new com.orbitalis.phase1.rocket.RocketPartItem(properties.stacksTo(16), ModBlocks.ROCKET_ENGINE_STAGED));
    /** Moteur méthane / oxygène liquides, à flux complet (type Raptor). */
    public static final DeferredItem<com.orbitalis.phase1.rocket.RocketPartItem> METHALOX_ENGINE = ITEMS.registerItem("methalox_engine",
            properties -> new com.orbitalis.phase1.rocket.RocketPartItem(properties.stacksTo(16), ModBlocks.ROCKET_ENGINE_METHALOX));
    /** Grand réservoir en composite : quatre fois le volume des autres, pour les lanceurs lourds. */
    public static final DeferredItem<com.orbitalis.phase1.rocket.RocketPartItem> COMPOSITE_TANK = ITEMS.registerItem("composite_tank",
            properties -> new com.orbitalis.phase1.rocket.RocketPartItem(properties, ModBlocks.ROCKET_TANK_COMPOSITE));

    /** Réservoirs d'ergols : même volume, de plus en plus légers. */
    public static final DeferredItem<com.orbitalis.phase1.rocket.RocketPartItem> STEEL_TANK = ITEMS.registerItem("steel_tank",
            properties -> new com.orbitalis.phase1.rocket.RocketPartItem(properties, ModBlocks.ROCKET_TANK_STEEL));
    public static final DeferredItem<com.orbitalis.phase1.rocket.RocketPartItem> ALUMINUM_TANK = ITEMS.registerItem("aluminum_tank",
            properties -> new com.orbitalis.phase1.rocket.RocketPartItem(properties, ModBlocks.ROCKET_TANK_ALUMINUM));
    public static final DeferredItem<com.orbitalis.phase1.rocket.RocketPartItem> ALUMINUM_LITHIUM_TANK = ITEMS.registerItem("aluminum_lithium_tank",
            properties -> new com.orbitalis.phase1.rocket.RocketPartItem(properties, ModBlocks.ROCKET_TANK_ALLOY));
    /** Réservoir en titane : le plus léger des réservoirs de quatre mètres cubes. */
    public static final DeferredItem<com.orbitalis.phase1.rocket.RocketPartItem> TITANIUM_TANK = ITEMS.registerItem("titanium_tank",
            properties -> new com.orbitalis.phase1.rocket.RocketPartItem(properties, ModBlocks.ROCKET_TANK_TITANIUM));
    /** Grand réservoir en acier inoxydable : lourd, mais six fois plus grand et bon marché. */
    public static final DeferredItem<com.orbitalis.phase1.rocket.RocketPartItem> STAINLESS_TANK = ITEMS.registerItem("stainless_tank",
            properties -> new com.orbitalis.phase1.rocket.RocketPartItem(properties, ModBlocks.ROCKET_TANK_STAINLESS));
    /** Réservoir d'hydrogène : grand, léger, sous sa mousse isolante ; il ne contient que l'hydrogène du moteur nucléaire. */
    public static final DeferredItem<com.orbitalis.phase1.rocket.RocketPartItem> HYDROGEN_TANK = ITEMS.registerItem("hydrogen_tank",
            properties -> new com.orbitalis.phase1.rocket.RocketPartItem(properties, ModBlocks.ROCKET_TANK_CRYO));

    /** Interétage : relie un étage à celui du dessous et permet leur séparation. */
    public static final DeferredItem<com.orbitalis.phase1.rocket.RocketPartItem> INTERSTAGE = ITEMS.registerItem("interstage",
            properties -> new com.orbitalis.phase1.rocket.RocketPartItem(properties, ModBlocks.ROCKET_INTERSTAGE));
    /** Coiffe : protège la charge utile pendant la traversée de l'atmosphère. */
    public static final DeferredItem<com.orbitalis.phase1.rocket.RocketPartItem> NOSE_CONE = ITEMS.registerItem("nose_cone",
            properties -> new com.orbitalis.phase1.rocket.RocketPartItem(properties, ModBlocks.ROCKET_NOSE));
    /** Charge utile de fusée-sonde : instruments de mesure de la haute atmosphère. */
    public static final DeferredItem<Item> SOUNDING_PAYLOAD = ITEMS.registerSimpleItem("sounding_payload", properties -> properties.stacksTo(16));

    // ------------------------------------------------------------------
    // Phase 1 - Satellites (leurs caractéristiques sont dans SatelliteType) : des charges utiles de fusée
    // ------------------------------------------------------------------
    /** Premier satellite : une sphère, une radio et une batterie. */
    public static final DeferredItem<Item> TEST_SATELLITE = ITEMS.registerSimpleItem("test_satellite", properties -> properties.stacksTo(16));
    /** Satellite météo : des données, et les prévisions à la station de poursuite. */
    public static final DeferredItem<Item> WEATHER_SATELLITE = ITEMS.registerSimpleItem("weather_satellite", properties -> properties.stacksTo(16));
    /** Satellite de télécommunications : relaie en continu les données des autres satellites. */
    public static final DeferredItem<Item> COMMUNICATION_SATELLITE = ITEMS.registerSimpleItem("communication_satellite", properties -> properties.stacksTo(16));
    /** Satellite d'observation de la planète. */
    public static final DeferredItem<Item> OBSERVATION_SATELLITE = ITEMS.registerSimpleItem("observation_satellite", properties -> properties.stacksTo(16));
    /** Satellite de navigation : il en faut quatre pour une constellation. */
    public static final DeferredItem<Item> NAVIGATION_SATELLITE = ITEMS.registerSimpleItem("navigation_satellite", properties -> properties.stacksTo(16));
    /** Télescope spatial : onze tonnes, la meilleure source de données. */
    public static final DeferredItem<Item> SPACE_TELESCOPE = ITEMS.registerSimpleItem("space_telescope", properties -> properties.stacksTo(16));

    // ------------------------------------------------------------------
    // Phase 2 - Vols habités
    // ------------------------------------------------------------------
    /** Capsule habitée : une charge utile dans laquelle un joueur monte à bord de la fusée. */
    public static final DeferredItem<Item> CREW_CAPSULE = ITEMS.registerSimpleItem("crew_capsule", properties -> properties.stacksTo(1));

    // Combinaisons spatiales, en trois pièces (voir SpaceSuits). Le torse porte le sac de survie : sa réserve
    // d'oxygène est affichée comme une barre d'usure.
    /** La combinaison orange, de lancement et de sortie. */
    public static final DeferredItem<SpaceSuitItem> SPACE_HELMET = suit("space_helmet", false, EquipmentSlot.HEAD, 0);
    public static final DeferredItem<SpaceSuitItem> SPACESUIT = suit("spacesuit", false, EquipmentSlot.CHEST, SpaceRules.SUIT_OXYGEN);
    public static final DeferredItem<SpaceSuitItem> SPACE_LEGGINGS = suit("space_leggings", false, EquipmentSlot.LEGS, 0);
    public static final DeferredItem<SpaceSuitItem> SPACE_BOOTS = suit("space_boots", false, EquipmentSlot.FEET, 0);
    /** La combinaison blanche, avancée : plus d'oxygène, plus de protection, et des modules. */
    public static final DeferredItem<SpaceSuitItem> ADVANCED_SPACE_HELMET = suit("advanced_space_helmet", true, EquipmentSlot.HEAD, 0);
    public static final DeferredItem<SpaceSuitItem> ADVANCED_SPACESUIT = suit("advanced_spacesuit", true, EquipmentSlot.CHEST,
            com.orbitalis.phase2.survival.SpaceSuits.ADVANCED_OXYGEN);
    public static final DeferredItem<SpaceSuitItem> ADVANCED_SPACE_LEGGINGS = suit("advanced_space_leggings", true, EquipmentSlot.LEGS, 0);
    public static final DeferredItem<SpaceSuitItem> ADVANCED_SPACE_BOOTS = suit("advanced_space_boots", true, EquipmentSlot.FEET, 0);
    /** Modules de la combinaison avancée. */
    public static final DeferredItem<SuitModuleItem> SUIT_MODULE_MAP = suitModule("suit_module_map", SuitModule.MAP);
    public static final DeferredItem<SuitModuleItem> SUIT_MODULE_SENSOR = suitModule("suit_module_sensor", SuitModule.SENSOR);
    public static final DeferredItem<SuitModuleItem> SUIT_MODULE_OXYGEN_TANK = suitModule("suit_module_oxygen_tank", SuitModule.OXYGEN_TANK);
    public static final DeferredItem<SuitModuleItem> SUIT_MODULE_THRUSTERS = suitModule("suit_module_thrusters", SuitModule.THRUSTERS);
    public static final DeferredItem<SuitModuleItem> SUIT_MODULE_SHOCK_ABSORBERS = suitModule("suit_module_shock_absorbers", SuitModule.SHOCK_ABSORBERS);
    public static final DeferredItem<SuitModuleItem> SUIT_MODULE_RADIATION_SHIELD = suitModule("suit_module_radiation_shield", SuitModule.RADIATION_SHIELD);

    /** Vaisseau lunaire : une capsule habitée sur son étage d'alunissage. Il se pose sur la Lune et en repart. */
    public static final DeferredItem<Item> LUNAR_MODULE = ITEMS.registerSimpleItem("lunar_module", properties -> properties.stacksTo(1));
    /** Capsule Mk2 « Horizon » : quatre places, un poste de pilotage tout en écrans. */
    public static final DeferredItem<Item> CREW_CAPSULE_MK2 = ITEMS.registerSimpleItem("crew_capsule_mk2", properties -> properties.stacksTo(1));
    /** Capsule Mk3 « Odyssée » : quatre places, faite pour les longs voyages. */
    public static final DeferredItem<Item> CREW_CAPSULE_MK3 = ITEMS.registerSimpleItem("crew_capsule_mk3", properties -> properties.stacksTo(1));
    /** Vaisseau martien : une cabine sur un étage d'atterrissage plus lourd, avec bouclier de rentrée et ergols du retour. */
    public static final DeferredItem<Item> MARS_LANDER = ITEMS.registerSimpleItem("mars_lander", properties -> properties.stacksTo(1));
    /**
     * Vaisseau titanien : une cabine Mk3 dans une coque d'entrée, avec ses parachutes et un petit étage de remontée.
     * L'air de Titan est quatre fois plus dense que celui de la planète : il s'y pose sous parachutes, comme Huygens.
     */
    public static final DeferredItem<Item> TITAN_LANDER = ITEMS.registerSimpleItem("titan_lander", properties -> properties.stacksTo(1));
    /**
     * Vaisseau d'Europe : une cabine Mk3 sur un étage de descente, sous un blindage contre les radiations de Jupiter.
     * Europe n'a pas d'air : il se pose sur son moteur, comme sur la Lune.
     */
    public static final DeferredItem<Item> EUROPA_LANDER = ITEMS.registerSimpleItem("europa_lander", properties -> properties.stacksTo(1));
    /** Seau de méthane liquide, puisé dans un lac de Titan. */
    public static final DeferredItem<net.minecraft.world.item.BucketItem> LIQUID_METHANE_BUCKET = ITEMS.registerItem("liquid_methane_bucket",
            properties -> new net.minecraft.world.item.BucketItem(ModFluids.LIQUID_METHANE.source().get(), properties),
            () -> new Item.Properties().craftRemainder(net.minecraft.world.item.Items.BUCKET).stacksTo(1));

    // Missions robotiques : des sondes qui cartographient la Lune et Mars depuis leur orbite, des atterrisseurs qui
    // se posent à leur surface (voir ProbeMissions).
    public static final DeferredItem<Item> LUNAR_PROBE = ITEMS.registerSimpleItem("lunar_probe", properties -> properties.stacksTo(1));
    public static final DeferredItem<Item> MARS_PROBE = ITEMS.registerSimpleItem("mars_probe", properties -> properties.stacksTo(1));
    // Missions robotiques vers les planètes du système solaire.
    public static final DeferredItem<Item> VENUS_ORBITER = ITEMS.registerSimpleItem("venus_orbiter", properties -> properties.stacksTo(1));
    public static final DeferredItem<Item> VENUS_LANDER = ITEMS.registerSimpleItem("venus_lander", properties -> properties.stacksTo(1));
    public static final DeferredItem<Item> MERCURY_ORBITER = ITEMS.registerSimpleItem("mercury_orbiter", properties -> properties.stacksTo(1));
    public static final DeferredItem<Item> JUPITER_ORBITER = ITEMS.registerSimpleItem("jupiter_orbiter", properties -> properties.stacksTo(1));
    public static final DeferredItem<Item> JUPITER_ENTRY_PROBE = ITEMS.registerSimpleItem("jupiter_entry_probe", properties -> properties.stacksTo(1));
    public static final DeferredItem<Item> SATURN_ORBITER = ITEMS.registerSimpleItem("saturn_orbiter", properties -> properties.stacksTo(1));
    public static final DeferredItem<Item> TITAN_ENTRY_PROBE = ITEMS.registerSimpleItem("titan_entry_probe", properties -> properties.stacksTo(1));
    public static final DeferredItem<Item> EUROPA_ORBITER = ITEMS.registerSimpleItem("europa_orbiter", properties -> properties.stacksTo(1));
    public static final DeferredItem<Item> LUNAR_ROBOTIC_LANDER = ITEMS.registerSimpleItem("lunar_robotic_lander", properties -> properties.stacksTo(1));
    public static final DeferredItem<Item> MARS_ROBOTIC_LANDER = ITEMS.registerSimpleItem("mars_robotic_lander", properties -> properties.stacksTo(1));
    /** Station en orbite lunaire : une charge utile, mise en orbite autour de la Lune comme un satellite. */
    public static final DeferredItem<Item> LUNAR_GATEWAY = ITEMS.registerSimpleItem("lunar_gateway", properties -> properties.stacksTo(1));
    /** Missions robotiques de retour d'échantillons, vers la Lune et vers Mars. */
    public static final DeferredItem<Item> LUNAR_SAMPLE_RETURN = ITEMS.registerSimpleItem("lunar_sample_return", properties -> properties.stacksTo(1));
    public static final DeferredItem<Item> MARS_SAMPLE_RETURN = ITEMS.registerSimpleItem("mars_sample_return", properties -> properties.stacksTo(1));
    /** Conteneur à échantillons : un équipage le remplit sur la Lune ou sur Mars (voir SampleContainerItem). */
    public static final DeferredItem<com.orbitalis.phase3.exploration.SampleContainerItem> SAMPLE_CONTAINER = ITEMS.registerItem("sample_container",
            com.orbitalis.phase3.exploration.SampleContainerItem::new, () -> new Item.Properties().stacksTo(16));
    /** Échantillons rapportés de la Lune et de Mars : on les analyse sur Terre (voir SampleItem). */
    public static final DeferredItem<com.orbitalis.phase3.exploration.SampleItem> LUNAR_SAMPLES = ITEMS.registerItem("lunar_samples",
            properties -> new com.orbitalis.phase3.exploration.SampleItem(properties, true), () -> new Item.Properties().stacksTo(16));
    public static final DeferredItem<com.orbitalis.phase3.exploration.SampleItem> MARS_SAMPLES = ITEMS.registerItem("mars_samples",
            properties -> new com.orbitalis.phase3.exploration.SampleItem(properties, false), () -> new Item.Properties().stacksTo(16));
    /** Kit de base planétaire : sur la Lune ou sur Mars, un clic droit sur le sol y bâtit un module pressurisé. */
    public static final DeferredItem<com.orbitalis.phase3.exploration.BaseKitItem> BASE_KIT = ITEMS.registerItem("base_kit",
            com.orbitalis.phase3.exploration.BaseKitItem::new, () -> new Item.Properties().stacksTo(1));
    /** Rover, replié : un clic droit sur le sol le pose (voir RoverEntity). */
    /** Drone volant, replié : sur Titan (ou sur Mars, en courts sauts), il part étudier les sites alentour. */
    public static final DeferredItem<com.orbitalis.phase3.exploration.RotorDroneItem> ROTOR_DRONE = ITEMS.registerItem("rotor_drone",
            com.orbitalis.phase3.exploration.RotorDroneItem::new, () -> new Item.Properties().stacksTo(1));
    public static final DeferredItem<com.orbitalis.phase3.exploration.RoverItem> ROVER = ITEMS.registerItem("rover",
            com.orbitalis.phase3.exploration.RoverItem::new, () -> new Item.Properties().stacksTo(1));

    /** Module central de station : une charge utile qui se déploie en orbite en une première pièce habitable. */
    public static final DeferredItem<StationModuleItem> STATION_CORE = ITEMS.registerItem("station_core",
            properties -> new StationModuleItem(properties, com.orbitalis.phase2.station.ModuleType.ROOM),
            properties -> properties.stacksTo(1));
    /** Kits de modules de station : chacun se déploie contre une paroi en un module aménagé, prêt à servir. */
    public static final DeferredItem<StationModuleItem> HABITATION_MODULE = module("habitation_module", com.orbitalis.phase2.station.ModuleType.HABITATION);
    public static final DeferredItem<StationModuleItem> LABORATORY_MODULE = module("laboratory_module", com.orbitalis.phase2.station.ModuleType.LABORATORY);
    public static final DeferredItem<StationModuleItem> GREENHOUSE_MODULE = module("greenhouse_module", com.orbitalis.phase2.station.ModuleType.GREENHOUSE);
    public static final DeferredItem<StationModuleItem> CARGO_MODULE = module("cargo_module", com.orbitalis.phase2.station.ModuleType.CARGO);
    public static final DeferredItem<StationModuleItem> CUPOLA_MODULE = module("cupola_module", com.orbitalis.phase2.station.ModuleType.CUPOLA);
    public static final DeferredItem<StationModuleItem> AIRLOCK_MODULE = module("airlock_module", com.orbitalis.phase2.station.ModuleType.AIRLOCK);
    public static final DeferredItem<StationModuleItem> POWER_MODULE = module("power_module", com.orbitalis.phase2.station.ModuleType.POWER);
    public static final DeferredItem<StationModuleItem> SHIPYARD_MODULE = module("shipyard_module", com.orbitalis.phase2.station.ModuleType.SHIPYARD);
    /** Porte de sas : deux blocs de haut, deux battants coulissants. */
    public static final DeferredItem<net.minecraft.world.item.DoubleHighBlockItem> AIRLOCK_DOOR = ITEMS.registerItem("airlock_door",
            properties -> new net.minecraft.world.item.DoubleHighBlockItem(ModBlocks.AIRLOCK_DOOR.get(), properties),
            () -> new Item.Properties().useBlockDescriptionPrefix());
    /** Porte vitrée coulissante : deux blocs de haut, deux battants de verre. */
    public static final DeferredItem<net.minecraft.world.item.DoubleHighBlockItem> GLASS_DOOR = ITEMS.registerItem("glass_door",
            properties -> new net.minecraft.world.item.DoubleHighBlockItem(ModBlocks.GLASS_DOOR.get(), properties),
            () -> new Item.Properties().useBlockDescriptionPrefix());
    /** Sas étanche : une porte de deux blocs. */
    public static final DeferredItem<net.minecraft.world.item.DoubleHighBlockItem> STATION_HATCH = ITEMS.registerItem("station_hatch",
            properties -> new net.minecraft.world.item.DoubleHighBlockItem(ModBlocks.STATION_HATCH.get(), properties),
            () -> new Item.Properties().useBlockDescriptionPrefix());

    /** Télécommande de récupération : un clic droit reprend la fusée du pas de tir le plus proche. */
    /** Plan de fusée : sort de l'ordinateur de conception, se dépose sur la table d'un pas de tir assemblé. */
    public static final DeferredItem<RocketBlueprintItem> ROCKET_BLUEPRINT = ITEMS.registerItem("rocket_blueprint", RocketBlueprintItem::new,
            properties -> properties.stacksTo(1));

    /** Kit de pas de tir : le plan d'un pas de tir et sa table de lancement. */
    public static final DeferredItem<PadKitItem> PAD_KIT = ITEMS.registerItem("pad_kit", PadKitItem::new, properties -> properties.stacksTo(1));

    public static final DeferredItem<RecoveryRemoteItem> RECOVERY_REMOTE = ITEMS.registerItem("recovery_remote",
            RecoveryRemoteItem::new, properties -> properties.stacksTo(1));
    /** Tablette de mission : l'atlas partout à la surface, tant qu'un satellite de télécommunications relaie. */
    public static final DeferredItem<com.orbitalis.phase1.satellite.MissionTabletItem> MISSION_TABLET = ITEMS.registerItem("mission_tablet",
            com.orbitalis.phase1.satellite.MissionTabletItem::new, properties -> properties.stacksTo(1));

    /** Fusée assemblée : sort de l'atelier d'assemblage, se pose sur une table de lancement. */
    public static final DeferredItem<RocketItem> ROCKET = ITEMS.registerItem("rocket", RocketItem::new, properties -> properties.stacksTo(1));

    /** Objet de test : débloque toutes les technologies de l'agence du joueur. Onglet créatif uniquement. */
    public static final DeferredItem<CreativeResearchItem> CREATIVE_RESEARCH_DOSSIER = ITEMS.registerItem("creative_research_dossier",
            CreativeResearchItem::new, properties -> properties.stacksTo(1).rarity(Rarity.EPIC));

    private ModItems() {
    }

    /**
     * Une pièce de combinaison : elle se porte comme une armure, sans s'user sous les coups. Le casque pose sa
     * visière devant les yeux ; le torse a sa réserve d'oxygène.
     */
    private static DeferredItem<SpaceSuitItem> suit(String name, boolean advanced, EquipmentSlot slot, int oxygen) {
        return ITEMS.registerItem(name, properties -> new SpaceSuitItem(properties, advanced, slot), properties -> {
            Equippable.Builder equippable = Equippable.builder(slot)
                    .setEquipSound(advanced ? SoundEvents.ARMOR_EQUIP_IRON : SoundEvents.ARMOR_EQUIP_LEATHER)
                    .setAsset(advanced ? ADVANCED_SUIT_ASSET : SUIT_ASSET)
                    .setDamageOnHurt(false);
            if (slot == EquipmentSlot.HEAD) {
                equippable.setCameraOverlay(Orbitalis.id(advanced ? "misc/advanced_suit_visor" : "misc/suit_visor"));
            }
            Item.Properties result = properties.stacksTo(1)
                    .component(DataComponents.EQUIPPABLE, equippable.build())
                    .attributes(com.orbitalis.phase2.survival.SpaceSuits.attributes(advanced, slot, java.util.List.of()));
            if (oxygen > 0) {
                result = result.durability(oxygen);
            }
            return advanced ? result.rarity(Rarity.UNCOMMON) : result;
        });
    }

    private static DeferredItem<SuitModuleItem> suitModule(String name, SuitModule module) {
        return ITEMS.registerItem(name, properties -> new SuitModuleItem(properties, module), properties -> properties.stacksTo(16));
    }

    private static DeferredItem<StationModuleItem> module(String name, com.orbitalis.phase2.station.ModuleType type) {
        return ITEMS.registerItem(name, properties -> new StationModuleItem(properties, type), properties -> properties.stacksTo(1));
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
