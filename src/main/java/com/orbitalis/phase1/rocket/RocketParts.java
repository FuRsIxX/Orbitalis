package com.orbitalis.phase1.rocket;

import java.util.Map;

import com.orbitalis.core.registry.ModItems;
import com.orbitalis.phase1.satellite.SatelliteType;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Caractéristiques des pièces de fusée. Les valeurs s'inspirent d'engins réels ; elles servent au calcul
 * des statistiques dans l'atelier d'assemblage, puis serviront au vol.
 */
public final class RocketParts {
    /** Rôle d'une pièce : il détermine l'emplacement de l'atelier qui l'accepte. */
    public enum Kind {
        ENGINE,
        TANK,
        INTERSTAGE,
        PAYLOAD,
        NOSE_CONE
    }

    /**
     * Moteur.
     *
     * @param thrustSeaLevel poussée au niveau de la mer, en kN
     * @param thrustVacuum   poussée dans le vide, en kN
     * @param ispSeaLevel    impulsion spécifique au niveau de la mer, en secondes
     * @param ispVacuum      impulsion spécifique dans le vide, en secondes
     * @param mass           masse à vide, en kg
     * @param solidLoad      masse de propergol coulée dans le moteur, en kg (moteurs à poudre uniquement)
     */
    public record Engine(Propellant propellant, double thrustSeaLevel, double thrustVacuum,
            double ispSeaLevel, double ispVacuum, double mass, double solidLoad) {
    }

    /**
     * Réservoir d'ergols liquides.
     *
     * @param volume volume utile, en m³
     * @param mass   masse à vide, en kg
     */
    public record Tank(double volume, double mass) {
    }

    /** Pièce connue de l'atelier : son rôle, sa masse et la technologie à maîtriser pour l'installer. */
    public record Part(Kind kind, double mass, String research, Engine engine, Tank tank) {
    }

    private static Map<Item, Part> parts;

    private RocketParts() {
    }

    /** Les items n'existent pas encore au chargement de la classe : la table est construite à la première demande. */
    private static Map<Item, Part> parts() {
        if (parts == null) {
            // Dans l'ordre où l'écran de conception les propose : du plus simple au plus avancé.
            Map<Item, Part> map = new java.util.LinkedHashMap<>();
            // Moteurs. Le moteur à poudre embarque son propergol (type fusée-sonde) ; le moteur à pression
            // est un petit moteur simple ; le moteur à turbopompes rappelle le Merlin, le moteur cryogénique le Vulcain.
            engine(map, ModItems.SOLID_MOTOR.get(), "solid_motors", new Engine(Propellant.SOLID, 60, 66, 230, 250, 150, 900));
            engine(map, ModItems.PRESSURE_FED_ENGINE.get(), "pressure_fed_engines", new Engine(Propellant.KEROLOX, 30, 34, 240, 290, 60, 0));
            engine(map, ModItems.KEROLOX_ENGINE.get(), "kerolox_engines", new Engine(Propellant.KEROLOX, 845, 914, 282, 311, 470, 0));
            engine(map, ModItems.HYDROLOX_ENGINE.get(), "hydrolox_engines", new Engine(Propellant.HYDROLOX, 960, 1359, 318, 434, 1800, 0));
            // Booster à poudre : un premier étage qui pousse fort, une minute durant, sans plein à faire.
            engine(map, ModItems.SOLID_BOOSTER.get(), "solid_boosters", new Engine(Propellant.SOLID, 600, 680, 240, 268, 2600, 16000));
            // Moteur hypergolique : ses ergols stockables sont chargés à l'usine, comme un moteur à poudre.
            engine(map, ModItems.HYPERGOLIC_ENGINE.get(), "hypergolics", new Engine(Propellant.SOLID, 45, 50, 270, 315, 110, 1600));
            // Moteur à vide : il pousse à peine au sol, mais son impulsion dans le vide est la meilleure de tous.
            engine(map, ModItems.VACUUM_ENGINE.get(), "superalloys", new Engine(Propellant.HYDROLOX, 30, 110, 210, 465, 300, 0));

            // Propulseur ionique : un demi-newton de poussée, mais une impulsion dix fois celle d'un moteur chimique ;
            // son réservoir de xénon est chargé à l'usine. Il ne sert qu'une fois en orbite.
            engine(map, ModItems.ION_THRUSTER.get(), "ion_propulsion", new Engine(Propellant.XENON, 0, 0.0005, 0, 3100, 140, 450));
            // Voile solaire : aucune poussée mesurable sur le pas de tir, aucun ergol ; la lumière fait le reste.
            engine(map, ModItems.SOLAR_SAIL.get(), "solar_sails", new Engine(Propellant.SUNLIGHT, 0, 0, 0, 0, 60, 0));
            // Moteur nucléaire thermique (type NERVA) : un réacteur chauffe de l'hydrogène seul ; une impulsion double de
            // celle d'un moteur chimique, une poussée honnête dans le vide, mais un moteur lourd, et un ergol si léger
            // qu'il lui faut beaucoup de réservoirs.
            engine(map, ModItems.NUCLEAR_THERMAL_ENGINE.get(), "nuclear_thermal", new Engine(Propellant.HYDROGEN, 180, 330, 380, 900, 3800, 0));
            // Moteur à fusion (type Direct Fusion Drive) : un plasma de deutérium et d'hélium-3, tenu par ses aimants,
            // chauffe l'hydrogène bien plus que ne le fait un réacteur à fission. Lourd, mais près de trois fois
            // l'impulsion du moteur nucléaire, avec une poussée qui suffit à un équipage.
            engine(map, ModItems.FUSION_ENGINE.get(), "fusion", new Engine(Propellant.FUSION, 200, 900, 800, 2500, 20000, 0));
            engine(map, ModItems.STAGED_COMBUSTION_ENGINE.get(), "staged_combustion", new Engine(Propellant.KEROLOX, 1800, 1960, 311, 338, 1200, 0));
            // Moteur méthane / oxygène à flux complet (type Raptor) : la poussée du moteur à combustion étagée, une
            // meilleure impulsion, et un ergol qu'on sait fabriquer sur Mars et puiser sur Titan.
            engine(map, ModItems.METHALOX_ENGINE.get(), "methalox_engines", new Engine(Propellant.METHALOX, 2230, 2530, 327, 350, 1600, 0));
            tank(map, ModItems.COMPOSITE_TANK.get(), "composites", new Tank(16.0, 520));

            // Réservoirs : même volume, de plus en plus légers.
            tank(map, ModItems.STEEL_TANK.get(), "steelmaking", new Tank(4.0, 480));
            tank(map, ModItems.ALUMINUM_TANK.get(), "aluminum_structures", new Tank(4.0, 280));
            tank(map, ModItems.ALUMINUM_LITHIUM_TANK.get(), "aluminum_lithium", new Tank(4.0, 200));
            tank(map, ModItems.TITANIUM_TANK.get(), "titanium_parts", new Tank(4.0, 160));
            // Grand réservoir d'acier inoxydable : vingt-quatre mètres cubes, lourd mais bon marché.
            tank(map, ModItems.STAINLESS_TANK.get(), "stainless_steel", new Tank(24.0, 1100));
            // Réservoir d'hydrogène : trente-deux mètres cubes, sans cloison ni comburant, sous une mousse isolante. Il est
            // léger pour sa taille, mais ne garde que de l'hydrogène seul : il ne sert qu'au moteur nucléaire.
            tank(map, ModItems.HYDROGEN_TANK.get(), "nuclear_thermal", new Tank(32.0, 300));

            map.put(ModItems.INTERSTAGE.get(), new Part(Kind.INTERSTAGE, 100, null, null, null));
            map.put(ModItems.NOSE_CONE.get(), new Part(Kind.NOSE_CONE, 30, null, null, null));
            map.put(ModItems.SOUNDING_PAYLOAD.get(), new Part(Kind.PAYLOAD, 50, "sounding_payloads", null, null));
            // Capsule habitée : cabine pressurisée, bouclier thermique, parachutes et tour de sauvetage.
            map.put(ModItems.CREW_CAPSULE.get(), new Part(Kind.PAYLOAD, 2800, "capsules", null, null));
            // Les capsules à quatre places : plus lourdes, plus avancées.
            map.put(ModItems.CREW_CAPSULE_MK2.get(), new Part(Kind.PAYLOAD, 3600, "rendezvous_docking", null, null));
            map.put(ModItems.CREW_CAPSULE_MK3.get(), new Part(Kind.PAYLOAD, 4500, "lunar_lander", null, null));
            // Vaisseau lunaire : la capsule, son étage d'alunissage et les ergols du voyage.
            map.put(ModItems.LUNAR_MODULE.get(), new Part(Kind.PAYLOAD, 6000, "lunar_lander", null, null));
            // Vaisseau martien : la capsule, un étage d'atterrissage plus lourd, son bouclier et les ergols du retour.
            map.put(ModItems.MARS_LANDER.get(), new Part(Kind.PAYLOAD, 8000, "mars_lander", null, null));
            // Vaisseau titanien : la cabine Mk3 dans sa coque d'entrée, ses parachutes et son étage de remontée.
            map.put(ModItems.TITAN_LANDER.get(), new Part(Kind.PAYLOAD, 7000, "titan_lander", null, null));
            // Vaisseau d'Europe : la cabine Mk3 sur son étage de descente, sous son blindage.
            map.put(ModItems.EUROPA_LANDER.get(), new Part(Kind.PAYLOAD, 6000, "europa_lander", null, null));
            // Missions robotiques : des sondes et des atterrisseurs sans équipage, vers la Lune et Mars.
            map.put(ModItems.LUNAR_PROBE.get(), new Part(Kind.PAYLOAD, 900, "probes", null, null));
            map.put(ModItems.MARS_PROBE.get(), new Part(Kind.PAYLOAD, 1200, "probes", null, null));
            map.put(ModItems.LUNAR_ROBOTIC_LANDER.get(), new Part(Kind.PAYLOAD, 1600, "landers", null, null));
            map.put(ModItems.MARS_ROBOTIC_LANDER.get(), new Part(Kind.PAYLOAD, 2200, "landers", null, null));
            map.put(ModItems.LUNAR_SAMPLE_RETURN.get(), new Part(Kind.PAYLOAD, 2400, "sample_return", null, null));
            map.put(ModItems.MARS_SAMPLE_RETURN.get(), new Part(Kind.PAYLOAD, 3200, "sample_return", null, null));
            // Sondes planétaires : celles des planètes lointaines tirent leur électricité d'un générateur à radio-isotopes.
            map.put(ModItems.VENUS_ORBITER.get(), new Part(Kind.PAYLOAD, 1500, "probes", null, null));
            map.put(ModItems.VENUS_LANDER.get(), new Part(Kind.PAYLOAD, 2500, "landers", null, null));
            map.put(ModItems.MERCURY_ORBITER.get(), new Part(Kind.PAYLOAD, 1100, "probes", null, null));
            map.put(ModItems.JUPITER_ORBITER.get(), new Part(Kind.PAYLOAD, 3600, "rtg", null, null));
            map.put(ModItems.JUPITER_ENTRY_PROBE.get(), new Part(Kind.PAYLOAD, 2600, "heat_shields", null, null));
            map.put(ModItems.SATURN_ORBITER.get(), new Part(Kind.PAYLOAD, 5700, "rtg", null, null));
            map.put(ModItems.TITAN_ENTRY_PROBE.get(), new Part(Kind.PAYLOAD, 2400, "heat_shields", null, null));
            map.put(ModItems.EUROPA_ORBITER.get(), new Part(Kind.PAYLOAD, 6000, "rtg", null, null));
            // Module central de station : une pièce repliée, qui se déploie une fois en orbite.
            map.put(ModItems.STATION_CORE.get(), new Part(Kind.PAYLOAD, 3000, "station_modules", null, null));
            // Kits de modules : livrés en fret à la station de l'agence.
            map.put(ModItems.HABITATION_MODULE.get(), new Part(Kind.PAYLOAD, 4000, "station_modules", null, null));
            map.put(ModItems.LABORATORY_MODULE.get(), new Part(Kind.PAYLOAD, 4500, "station_modules", null, null));
            map.put(ModItems.GREENHOUSE_MODULE.get(), new Part(Kind.PAYLOAD, 4000, "closed_loop_life_support", null, null));
            map.put(ModItems.CARGO_MODULE.get(), new Part(Kind.PAYLOAD, 3500, "station_modules", null, null));
            map.put(ModItems.CUPOLA_MODULE.get(), new Part(Kind.PAYLOAD, 2000, "station_modules", null, null));
            map.put(ModItems.AIRLOCK_MODULE.get(), new Part(Kind.PAYLOAD, 2500, "station_modules", null, null));
            map.put(ModItems.POWER_MODULE.get(), new Part(Kind.PAYLOAD, 3000, "solar_arrays", null, null));
            // Satellites : des charges utiles, chacune avec sa masse et sa technologie.
            for (SatelliteType satellite : SatelliteType.values()) {
                map.put(satellite.item(), new Part(Kind.PAYLOAD, satellite.mass(), satellite.research(), null, null));
            }
            parts = map;
        }
        return parts;
    }

    private static void engine(Map<Item, Part> map, Item item, String research, Engine engine) {
        map.put(item, new Part(Kind.ENGINE, engine.mass(), research, engine, null));
    }

    private static void tank(Map<Item, Part> map, Item item, String research, Tank tank) {
        map.put(item, new Part(Kind.TANK, tank.mass(), research, null, tank));
    }

    /** Caractéristiques de la pièce, ou {@code null} si l'objet n'est pas une pièce de fusée. */
    public static Part get(ItemStack stack) {
        return stack.isEmpty() ? null : parts().get(stack.getItem());
    }

    /** Vrai si cette charge utile est une cabine habitée : la capsule, ou le vaisseau lunaire. */
    public static boolean isCrewCabin(ItemStack stack) {
        return stack.is(ModItems.CREW_CAPSULE.get()) || stack.is(ModItems.CREW_CAPSULE_MK2.get()) || stack.is(ModItems.CREW_CAPSULE_MK3.get())
                || stack.is(ModItems.LUNAR_MODULE.get()) || stack.is(ModItems.MARS_LANDER.get()) || stack.is(ModItems.TITAN_LANDER.get())
                || stack.is(ModItems.EUROPA_LANDER.get());
    }

    /**
     * Modèle de la cabine de cette charge utile : 0 pour la Mk1 (et les vaisseaux lunaire et martien), 1 la Mk2, 2 la Mk3
     * (et le vaisseau titanien, qui en est une).
     */
    public static int cabinVariant(ItemStack stack) {
        return stack.is(ModItems.CREW_CAPSULE_MK3.get()) || stack.is(ModItems.TITAN_LANDER.get()) || stack.is(ModItems.EUROPA_LANDER.get()) ? 2 : stack.is(ModItems.CREW_CAPSULE_MK2.get()) ? 1 : 0;
    }

    /** Les pièces de ce rôle, dans l'ordre du plus simple au plus avancé. */
    public static java.util.List<Item> ofKind(Kind kind) {
        java.util.List<Item> items = new java.util.ArrayList<>();
        parts().forEach((item, part) -> {
            if (part.kind() == kind) {
                items.add(item);
            }
        });
        return items;
    }

    public static boolean is(ItemStack stack, Kind kind) {
        Part part = get(stack);
        return part != null && part.kind() == kind;
    }
}
