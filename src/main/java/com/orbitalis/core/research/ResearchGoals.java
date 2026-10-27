package com.orbitalis.core.research;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.registry.ModItems;
import com.orbitalis.phase1.rocket.RocketStats;
import com.orbitalis.phase1.satellite.SatelliteType;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * Objectifs de l'agence : tout ce qui rapporte des points de recherche, classé par palier d'avancement.
 * <p>
 * Les points se gagnent en faisant, jamais en attendant : obtenir un matériau pour la première fois, atteindre un
 * palier de vol, mettre en orbite un premier satellite de chaque sorte, faire voler un équipage, se poser ailleurs.
 * Chaque objectif ne paie qu'une fois. Ce classement sert à l'arbre de compétences, qui montre au joueur d'où
 * viendront ses prochains points ; les primes elles-mêmes sont versées là où l'exploit se produit.
 * <p>
 * Les six paliers suivent la progression du mod : l'industrie, les premiers vols, l'orbite, l'équipage, la Lune, Mars.
 */
public final class ResearchGoals {
    /** Paliers d'avancement, du premier au dernier. */
    public enum Tier {
        INDUSTRY,
        FIRST_FLIGHTS,
        ORBIT,
        CREW,
        MOON,
        MARS;

        public String nameKey() {
            return "goals.orbitalis.tier." + name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Un objectif.
     *
     * @param id     jalon enregistré avec la recherche de l'agence quand l'objectif est atteint
     * @param tier   palier
     * @param icon   objet qui le représente
     * @param points points de recherche qu'il rapporte
     * @param name   son nom
     */
    public record Goal(String id, Tier tier, Supplier<? extends ItemLike> icon, int points, Supplier<Component> name) {
    }

    /** Préfixe sous lequel les objectifs atteints sont envoyés au client, mêlés aux technologies. */
    public static final String SAVED_PREFIX = "goal/";

    /** Primes des vols habités, des sorties et des atterrissages : les mêmes que là où elles sont versées. */
    public static final int CREWED_SUBORBITAL = 150;
    public static final int CREWED_ORBITAL = 300;
    public static final int FIRST_STATION = 250;
    public static final int SPACEWALK = 100;
    public static final int MOON_LANDING = 500;
    public static final int MOONWALK = 300;
    public static final int MARS_LANDING = 900;
    public static final int MARSWALK = 500;
    public static final int TITAN_LANDING = 1200;
    public static final int TITANWALK = 700;

    private static List<Goal> goals;

    private ResearchGoals() {
    }

    /** Prime de recherche la première fois qu'une agence atteint ce palier de vol. */
    public static int flightReward(RocketStats.Reach reach) {
        return switch (reach) {
            case ATMOSPHERIC -> 10;
            case SUBORBITAL -> 30;
            case LOW_ORBIT -> 100;
            case GEO_TRANSFER -> 150;
            case LUNAR, GEOSTATIONARY -> 200;
            case MARS -> 300;
            case VENUS -> 250;
            case JUPITER -> 400;
            case SATURN, TITAN -> 450;
            case EUROPA, MERCURY -> 500;
            default -> 0;
        };
    }

    private static Tier tierOf(RocketStats.Reach reach) {
        return switch (reach) {
            case ATMOSPHERIC, SUBORBITAL -> Tier.FIRST_FLIGHTS;
            case LUNAR -> Tier.MOON;
            // Mars et les planètes au-delà : l'espace lointain.
            case MARS, VENUS, JUPITER, SATURN, TITAN, EUROPA, MERCURY -> Tier.MARS;
            default -> Tier.ORBIT;
        };
    }

    private static void own(List<Goal> list, String id, Tier tier, Supplier<? extends ItemLike> icon, int points) {
        list.add(new Goal(id, tier, icon, points, () -> Component.translatable("goals.orbitalis." + id)));
    }

    /** Tous les objectifs, palier par palier. */
    public static List<Goal> all() {
        if (goals == null) {
            List<Goal> list = new ArrayList<>();
            // Industrie et échantillons : le premier exemplaire de chaque matériau.
            for (ResearchMilestones.Milestone milestone : ResearchMilestones.ALL) {
                Tier tier = milestone.id().startsWith("moon") || milestone.id().startsWith("lunar") ? Tier.MOON
                        : milestone.id().startsWith("mars") ? Tier.MARS : Tier.INDUSTRY;
                list.add(new Goal(milestone.id(), tier, milestone.item(), milestone.points(),
                        () -> Component.translatable("goals.orbitalis.obtain", new ItemStack(milestone.item().get()).getHoverName())));
            }
            // Paliers de vol.
            for (RocketStats.Reach reach : RocketStats.Reach.values()) {
                if (flightReward(reach) > 0) {
                    list.add(new Goal("flight_" + reach.name().toLowerCase(Locale.ROOT), tierOf(reach), ModItems.ROCKET, flightReward(reach),
                            () -> Component.translatable("goals.orbitalis.flight", Component.translatable(reach.nameKey()))));
                }
            }
            // Premier satellite de chaque sorte, et la constellation de navigation.
            for (SatelliteType satellite : SatelliteType.values()) {
                list.add(new Goal("satellite_" + satellite.id(), Tier.ORBIT, satellite::item, satellite.firstReward(),
                        () -> Component.translatable("goals.orbitalis.satellite", Component.translatable(satellite.nameKey()))));
            }
            own(list, "constellation_navigation", Tier.ORBIT, () -> SatelliteType.NAVIGATION.item(), SatelliteType.CONSTELLATION_REWARD);
            // Équipage.
            own(list, "crewed_suborbital", Tier.CREW, ModItems.CREW_CAPSULE, CREWED_SUBORBITAL);
            own(list, "crewed_orbital", Tier.CREW, ModItems.CREW_CAPSULE, CREWED_ORBITAL);
            own(list, "first_spacewalk", Tier.CREW, ModItems.SPACESUIT, SPACEWALK);
            own(list, "first_station", Tier.CREW, ModItems.STATION_CORE, FIRST_STATION);
            // La Lune, puis Mars.
            own(list, "moon_landing", Tier.MOON, ModItems.LUNAR_MODULE, MOON_LANDING);
            own(list, "first_moonwalk", Tier.MOON, ModBlocks.MOON_REGOLITH, MOONWALK);
            own(list, "mars_landing", Tier.MARS, ModItems.MARS_LANDER, MARS_LANDING);
            own(list, "first_marswalk", Tier.MARS, ModBlocks.MARS_REGOLITH, MARSWALK);
            own(list, "titan_landing", Tier.MARS, ModItems.TITAN_LANDER, TITAN_LANDING);
            own(list, "first_titanwalk", Tier.MARS, ModBlocks.THOLIN_SAND, TITANWALK);
            own(list, "europa_landing", Tier.MARS, ModItems.EUROPA_LANDER, 1500);
            own(list, "first_europawalk", Tier.MARS, ModBlocks.EUROPA_ICE, 800);
            own(list, "europa_ocean", Tier.MARS, ModBlocks.CRYOBOT, com.orbitalis.phase3.exploration.CryobotBlockEntity.OCEAN_POINTS);
            if (com.orbitalis.core.Features.interstellar()) {
                own(list, "first_proximawalk", Tier.MARS, ModBlocks.PROXIMA_MOSS, 2500);
            }
            own(list, "first_drone_flight", Tier.MARS, ModItems.ROTOR_DRONE, com.orbitalis.phase3.exploration.RotorDroneEntity.FIRST_FLIGHT_POINTS);
            // Rangés par palier, sans changer l'ordre à l'intérieur de chacun.
            list.sort(java.util.Comparator.comparingInt(goal -> goal.tier().ordinal()));
            goals = List.copyOf(list);
        }
        return goals;
    }
}
