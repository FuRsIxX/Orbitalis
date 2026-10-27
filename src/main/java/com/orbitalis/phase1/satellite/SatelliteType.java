package com.orbitalis.phase1.satellite;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.orbitalis.core.registry.ModItems;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Types de satellites. Chacun est une charge utile de fusée : il a une masse, une orbite de travail,
 * une durée de vie et un rôle une fois en orbite.
 * <ul>
 * <li>Le satellite expérimental, le satellite d'observation, le satellite météo et le télescope spatial
 * produisent des données scientifiques, c'est-à-dire des points de recherche.</li>
 * <li>Le satellite météo donne en plus les prévisions à la station de poursuite.</li>
 * <li>Le satellite de télécommunications ne produit rien lui-même : il relaie les données des autres vers
 * le sol, en continu, sans qu'il faille aller les chercher à une station de poursuite.</li>
 * <li>Les satellites de navigation forment une constellation : il en faut quatre pour un positionnement.</li>
 * </ul>
 */
public enum SatelliteType implements StringRepresentable {
    /** Premier satellite : une sphère, une radio et une batterie. Il retombe vite. */
    EXPERIMENTAL("test_satellite", () -> ModItems.TEST_SATELLITE.get(), 85, "first_satellite", Orbit.LOW, 1, 15, 40, 0xFFD8DCE0),
    WEATHER("weather_satellite", () -> ModItems.WEATHER_SATELLITE.get(), 700, "weather_satellites", Orbit.POLAR, 2, 60, 60, 0xFF7FD4F5),
    COMMUNICATION("communication_satellite", () -> ModItems.COMMUNICATION_SATELLITE.get(), 1500, "communication_satellites",
            Orbit.GEOSTATIONARY, 0, 120, 80, 0xFFF2CC5E),
    OBSERVATION("observation_satellite", () -> ModItems.OBSERVATION_SATELLITE.get(), 1200, "earth_observation", Orbit.POLAR, 4, 60, 80,
            0xFF5BC27A),
    NAVIGATION("navigation_satellite", () -> ModItems.NAVIGATION_SATELLITE.get(), 1000, "navigation_satellites", Orbit.MEDIUM, 1, 100, 100,
            0xFFE8872A),
    /** Télescope spatial : onze tonnes en orbite basse, de loin la meilleure source de données. */
    TELESCOPE("space_telescope", () -> ModItems.SPACE_TELESCOPE.get(), 11_000, "space_telescopes", Orbit.LOW, 12, 150, 200, 0xFFC79BF2),
    /** Station en orbite lunaire : un avant-poste autour de la Lune, comme le Lunar Gateway. Il fait beaucoup de science. */
    LUNAR_GATEWAY("lunar_gateway", () -> ModItems.LUNAR_GATEWAY.get(), 9_000, "lunar_gateway", Orbit.LUNAR, 10, 300, 150, 0xFFE8E2D0);

    public static final Codec<SatelliteType> CODEC = StringRepresentable.fromEnum(SatelliteType::values);
    /** Nombre de satellites de navigation en service à partir duquel la constellation donne un positionnement. */
    public static final int NAVIGATION_CONSTELLATION = 4;
    /** Prime de recherche pour la première constellation de navigation complète. */
    public static final int CONSTELLATION_REWARD = 150;

    private final String id;
    private final Supplier<Item> item;
    private final double mass;
    private final String research;
    private final Orbit orbit;
    private final int pointsPerDay;
    private final int lifetimeDays;
    private final int firstReward;
    private final int color;

    SatelliteType(String id, Supplier<Item> item, double mass, String research, Orbit orbit, int pointsPerDay, int lifetimeDays,
            int firstReward, int color) {
        this.id = id;
        this.item = item;
        this.mass = mass;
        this.research = research;
        this.orbit = orbit;
        this.pointsPerDay = pointsPerDay;
        this.lifetimeDays = lifetimeDays;
        this.firstReward = firstReward;
        this.color = color;
    }

    /** Identifiant, qui est aussi celui de l'objet. */
    public String id() {
        return id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public Item item() {
        return item.get();
    }

    /** Masse, en kg. */
    public double mass() {
        return mass;
    }

    /** Technologie à maîtriser pour l'installer sur une fusée. */
    public String research() {
        return research;
    }

    public Orbit orbit() {
        return orbit;
    }

    /** Points de recherche produits par jour de jeu, tant que le satellite fonctionne. */
    public int pointsPerDay() {
        return pointsPerDay;
    }

    /** Durée de vie en orbite, en jours de jeu. */
    public int lifetimeDays() {
        return lifetimeDays;
    }

    /** Prime de recherche pour le premier satellite de ce type mis en orbite par une agence. */
    public int firstReward() {
        return firstReward;
    }

    /** Couleur du satellite sur les écrans. */
    public int color() {
        return color;
    }

    public String nameKey() {
        return "item.orbitalis." + id;
    }

    /** Type de satellite que représente cet objet, ou {@code null}. */
    public static SatelliteType of(ItemStack stack) {
        if (!stack.isEmpty()) {
            for (SatelliteType type : values()) {
                if (stack.is(type.item())) {
                    return type;
                }
            }
        }
        return null;
    }
}
