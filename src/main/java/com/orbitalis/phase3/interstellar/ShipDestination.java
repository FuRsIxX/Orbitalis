package com.orbitalis.phase3.interstellar;

import java.util.Locale;

import com.orbitalis.phase1.satellite.CelestialBody;
import com.orbitalis.phase2.survival.SpaceRules;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Les mondes que le vaisseau interstellaire peut rejoindre : chacun doit d'abord être découvert à l'observatoire. Le
 * vaisseau saute de son berceau jusqu'à l'orbite de l'un d'eux, et la navette en descend sur la planète.
 */
public enum ShipDestination {
    PROXIMA(CelestialBody.PROXIMA_B, SpaceRules.PROXIMA_ORBIT, SpaceRules.PROXIMA_B),
    TRAPPIST(CelestialBody.TRAPPIST_1E, SpaceRules.TRAPPIST_ORBIT, SpaceRules.TRAPPIST_1E);

    private final CelestialBody body;
    private final ResourceKey<Level> orbit;
    private final ResourceKey<Level> planet;

    ShipDestination(CelestialBody body, ResourceKey<Level> orbit, ResourceKey<Level> planet) {
        this.body = body;
        this.orbit = orbit;
        this.planet = planet;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** L'astre à découvrir à l'observatoire avant d'y aller. */
    public CelestialBody body() {
        return body;
    }

    /** L'orbite où arrive le vaisseau. */
    public ResourceKey<Level> orbit() {
        return orbit;
    }

    /** La planète où descend la navette. */
    public ResourceKey<Level> planet() {
        return planet;
    }

    /** Son nom, à l'écran du poste de pilotage. */
    public String nameKey() {
        return "ship.orbitalis.helm.dest." + id();
    }

    /** L'endroit où se trouve le vaisseau, une fois arrivé. */
    public String placeKey() {
        return "ship.orbitalis.helm.place.away_" + id();
    }

    public String toKey() {
        return "ship.orbitalis.jump.to_" + id();
    }

    public String arrivedKey() {
        return "ship.orbitalis.jump.arrived_" + id();
    }

    /** La destination de ce nom, ou Proxima b (le premier voyage n'en gardait pas). */
    public static ShipDestination byId(String id) {
        for (ShipDestination destination : values()) {
            if (destination.id().equals(id)) {
                return destination;
            }
        }
        return PROXIMA;
    }

    public static ShipDestination byIndex(int index) {
        return values()[Math.floorMod(index, values().length)];
    }

    /** La destination dont c'est l'orbite, ou rien. */
    public static ShipDestination byOrbit(ResourceKey<Level> dimension) {
        for (ShipDestination destination : values()) {
            if (destination.orbit == dimension) {
                return destination;
            }
        }
        return null;
    }
}
