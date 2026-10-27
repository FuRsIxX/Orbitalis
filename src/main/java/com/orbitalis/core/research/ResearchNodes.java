package com.orbitalis.core.research;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.registry.ModItems;

import net.minecraft.world.level.ItemLike;

/**
 * Toutes les technologies du mod, arbre par arbre.
 * <p>
 * La progression suit l'histoire réelle : fusées-sondes, premiers satellites, vols habités, Lune, stations,
 * lanceurs réutilisables, puis technologies futures. Les coûts augmentent au fil de cette progression.
 * Dans chaque arbre, la colonne indique l'avancement (de gauche à droite) et la ligne la branche.
 */
public final class ResearchNodes {
    private static final Map<String, ResearchNode> NODES = new LinkedHashMap<>();

    static {
        // --- Propulsion : du moteur à poudre aux moteurs à combustion étagée ---
        node(ResearchTree.PROPULSION, "solid_motors", ModItems.SOLID_MOTOR, 5, 0, 0);
        node(ResearchTree.PROPULSION, "pressure_fed_engines", ModItems.PRESSURE_FED_ENGINE, 10, 1, 0, "solid_motors");
        node(ResearchTree.PROPULSION, "kerolox_engines", ModItems.KEROLOX_ENGINE, 25, 2, 0, "pressure_fed_engines");
        node(ResearchTree.PROPULSION, "hydrolox_engines", ModItems.HYDROLOX_ENGINE, 50, 3, 0, "kerolox_engines");
        node(ResearchTree.PROPULSION, "staged_combustion", ModItems.STAGED_COMBUSTION_ENGINE, 90, 4, 0, "hydrolox_engines");
        node(ResearchTree.PROPULSION, "methalox_engines", ModItems.METHALOX_ENGINE, 140, 5, 0, "staged_combustion", "stainless_steel");
        node(ResearchTree.PROPULSION, "solid_boosters", ModItems.SOLID_BOOSTER, 40, 1, 1, "solid_motors");
        node(ResearchTree.PROPULSION, "hypergolics", ModItems.HYPERGOLIC_ENGINE, 30, 2, 1, "pressure_fed_engines");

        // --- Structures et matériaux : de l'acier aux boucliers thermiques ---
        node(ResearchTree.STRUCTURES, "steelmaking", ModItems.STEEL_INGOT, 0, 0, 0);
        node(ResearchTree.STRUCTURES, "aluminum_structures", ModItems.ALUMINUM_PLATE, 10, 1, 0, "steelmaking");
        node(ResearchTree.STRUCTURES, "aluminum_lithium", ModItems.ALUMINUM_LITHIUM_PLATE, 25, 2, 0, "aluminum_structures");
        node(ResearchTree.STRUCTURES, "titanium_parts", ModItems.TITANIUM_TANK, 40, 3, 0, "aluminum_lithium");
        node(ResearchTree.STRUCTURES, "superalloys", ModItems.VACUUM_ENGINE, 70, 4, 0, "titanium_parts");
        node(ResearchTree.STRUCTURES, "stainless_steel", ModItems.STAINLESS_TANK, 100, 5, 0, "superalloys");
        node(ResearchTree.STRUCTURES, "composites", ModItems.COMPOSITE_TANK, 80, 3, 1, "aluminum_lithium");
        node(ResearchTree.STRUCTURES, "heat_shields", ModItems.HEAT_SHIELD_TILE, 110, 4, 1, "composites");

        // --- Électronique et avionique ---
        node(ResearchTree.AVIONICS, "basic_electronics", ModItems.CIRCUIT_BOARD, 5, 0, 0);
        node(ResearchTree.AVIONICS, "gyroscopes", ModItems.NAVIGATION_UNIT, 15, 1, 0, "basic_electronics");
        node(ResearchTree.AVIONICS, "flight_computers", ModBlocks.FLIGHT_COMPUTER, 35, 2, 0, "gyroscopes");
        node(ResearchTree.AVIONICS, "telemetry", ModItems.RADIO_TRANSMITTER, 30, 1, 1, "basic_electronics");
        node(ResearchTree.AVIONICS, "solar_power", ModBlocks.SOLAR_PANEL, 40, 1, 2, "basic_electronics");
        node(ResearchTree.AVIONICS, "fuel_cells", ModItems.BATTERY_CELL, 60, 2, 2, "solar_power");
        node(ResearchTree.AVIONICS, "rtg", ModItems.REACTOR_CORE, 120, 3, 2, "fuel_cells");

        // --- Infrastructures au sol : le centre spatial ---
        node(ResearchTree.GROUND, "launch_pad", ModItems.PAD_KIT, 5, 0, 0);
        node(ResearchTree.GROUND, "fuel_farm", ModBlocks.CRYOGENIC_TANK, 15, 1, 0, "launch_pad");
        node(ResearchTree.GROUND, "service_tower", ModBlocks.UMBILICAL_ARM, 30, 2, 0, "fuel_farm");
        node(ResearchTree.GROUND, "vehicle_assembly", ModItems.INTERSTAGE, 50, 3, 0, "service_tower");
        node(ResearchTree.GROUND, "mission_control", ModBlocks.MISSION_DISPLAY, 60, 4, 0, "vehicle_assembly", "telemetry");
        node(ResearchTree.GROUND, "tracking_stations", ModBlocks.TRACKING_STATION, 80, 5, 0, "mission_control");
        node(ResearchTree.GROUND, "test_stands", ModBlocks.ENGINEER_WORKBENCH, 25, 1, 1, "launch_pad");

        // --- Satellites ---
        node(ResearchTree.SATELLITES, "sounding_payloads", ModItems.SOUNDING_PAYLOAD, 10, 0, 0);
        node(ResearchTree.SATELLITES, "first_satellite", ModItems.TEST_SATELLITE, 40, 1, 0, "sounding_payloads", "kerolox_engines");
        node(ResearchTree.SATELLITES, "communication_satellites", ModItems.COMMUNICATION_SATELLITE, 70, 2, 0, "first_satellite");
        node(ResearchTree.SATELLITES, "navigation_satellites", ModItems.NAVIGATION_SATELLITE, 100, 3, 0, "communication_satellites");
        node(ResearchTree.SATELLITES, "space_telescopes", ModItems.SPACE_TELESCOPE, 160, 4, 0, "navigation_satellites");
        node(ResearchTree.SATELLITES, "weather_satellites", ModItems.WEATHER_SATELLITE, 70, 2, 1, "first_satellite");
        node(ResearchTree.SATELLITES, "earth_observation", ModItems.OBSERVATION_SATELLITE, 90, 3, 1, "weather_satellites");
        node(ResearchTree.SATELLITES, "mission_tablet", ModItems.MISSION_TABLET, 80, 3, 2, "communication_satellites", "mission_control");

        // --- Vols habités et survie ---
        node(ResearchTree.CREWED, "life_support", ModItems.CO2_FILTER, 50, 0, 0);
        node(ResearchTree.CREWED, "capsules", ModItems.CREW_CAPSULE, 90, 1, 0, "life_support", "heat_shields");
        node(ResearchTree.CREWED, "spacesuits", ModItems.SPACESUIT, 110, 2, 0, "capsules");
        node(ResearchTree.CREWED, "rendezvous_docking", ModBlocks.DOCKING_PORT, 130, 3, 0, "spacesuits", "fuel_cells");
        node(ResearchTree.CREWED, "lunar_lander", ModItems.LUNAR_MODULE, 220, 4, 0, "rendezvous_docking");
        node(ResearchTree.CREWED, "mars_lander", ModItems.MARS_LANDER, 340, 5, 0, "lunar_lander");
        node(ResearchTree.CREWED, "europa_lander", ModItems.EUROPA_LANDER, 600, 7, 0, "titan_lander");
        node(ResearchTree.CREWED, "titan_lander", ModItems.TITAN_LANDER, 520, 6, 0, "mars_lander", "nuclear_thermal");
        node(ResearchTree.CREWED, "launch_escape", ModBlocks.ESCAPE_TOWER, 60, 2, 1, "capsules");

        // --- Stations spatiales ---
        node(ResearchTree.STATIONS, "station_modules", ModItems.STATION_CORE, 150, 0, 0, "rendezvous_docking");
        node(ResearchTree.STATIONS, "solar_arrays", ModItems.POWER_MODULE, 120, 1, 0, "station_modules");
        node(ResearchTree.STATIONS, "closed_loop_life_support", ModItems.GREENHOUSE_MODULE, 180, 2, 0, "solar_arrays");
        node(ResearchTree.STATIONS, "lunar_gateway", ModItems.LUNAR_GATEWAY, 300, 3, 0, "closed_loop_life_support");
        node(ResearchTree.STATIONS, "robotic_arm", ModBlocks.CARGO_RACK, 140, 1, 1, "station_modules");
        // Le voyage interstellaire, en réserve pour une prochaine version : le chantier naval et le moteur à distorsion.
        if (com.orbitalis.core.Features.interstellar()) {
            node(ResearchTree.STATIONS, "orbital_shipyard", ModItems.SHIPYARD_MODULE, 900, 4, 0, "lunar_gateway", "fusion");
        }

        // --- Exploration planétaire ---
        node(ResearchTree.EXPLORATION, "probes", ModItems.LUNAR_PROBE, 120, 0, 0, "first_satellite");
        node(ResearchTree.EXPLORATION, "landers", ModItems.LUNAR_ROBOTIC_LANDER, 160, 1, 0, "probes");
        node(ResearchTree.EXPLORATION, "rovers", ModItems.ROVER, 200, 2, 0, "landers");
        node(ResearchTree.EXPLORATION, "isru", ModBlocks.ICE_DRILL, 260, 3, 0, "rovers");
        node(ResearchTree.EXPLORATION, "planetary_bases", ModItems.BASE_KIT, 350, 4, 0, "isru");
        node(ResearchTree.EXPLORATION, "helium3", ModBlocks.HELIUM3_EXTRACTOR, 420, 5, 0, "planetary_bases");
        node(ResearchTree.EXPLORATION, "sample_return", ModItems.LUNAR_SAMPLE_RETURN, 220, 2, 1, "landers");
        node(ResearchTree.EXPLORATION, "rotorcraft", ModItems.ROTOR_DRONE, 280, 3, 1, "rovers");
        node(ResearchTree.EXPLORATION, "cryobot", ModBlocks.CRYOBOT, 450, 4, 1, "isru", "nuclear_thermal");

        // --- Propulsion avancée : du moteur ionique aux technologies spéculatives ---
        node(ResearchTree.ADVANCED, "ion_propulsion", ModItems.ION_THRUSTER, 200, 0, 0, "solar_power");
        node(ResearchTree.ADVANCED, "nuclear_thermal", ModItems.NUCLEAR_THERMAL_ENGINE, 350, 1, 0, "ion_propulsion");
        node(ResearchTree.ADVANCED, "fusion", ModItems.FUSION_ENGINE, 600, 2, 0, "nuclear_thermal", "helium3");
        if (com.orbitalis.core.Features.interstellar()) {
            node(ResearchTree.ADVANCED, "warp_drive", ModBlocks.WARP_CORE, 1500, 3, 0, "fusion", "orbital_shipyard");
        }
        node(ResearchTree.ADVANCED, "solar_sails", ModItems.SOLAR_SAIL, 300, 1, 1, "ion_propulsion");

        // Vérification : chaque prérequis doit désigner une technologie existante.
        for (ResearchNode node : NODES.values()) {
            for (String prerequisite : node.prerequisites()) {
                if (!NODES.containsKey(prerequisite)) {
                    throw new IllegalStateException("Prérequis inconnu « " + prerequisite + " » pour la technologie « " + node.id() + " »");
                }
            }
        }
    }

    private ResearchNodes() {
    }

    private static void node(ResearchTree tree, String id, Supplier<? extends ItemLike> icon, int cost, int column, int row,
            String... prerequisites) {
        if (NODES.put(id, new ResearchNode(id, tree, icon, cost, List.of(prerequisites), column, row)) != null) {
            throw new IllegalStateException("Technologie déclarée deux fois : " + id);
        }
    }

    /** Technologie portant cet identifiant, ou {@code null}. */
    public static ResearchNode get(String id) {
        return NODES.get(id);
    }

    /** Toutes les technologies, dans leur ordre de déclaration. */
    public static Collection<ResearchNode> all() {
        return Collections.unmodifiableCollection(NODES.values());
    }

    /** Technologies d'un arbre. */
    public static List<ResearchNode> of(ResearchTree tree) {
        List<ResearchNode> nodes = new ArrayList<>();
        for (ResearchNode node : NODES.values()) {
            if (node.tree() == tree) {
                nodes.add(node);
            }
        }
        return nodes;
    }
}
