package com.orbitalis.core.world;

import com.orbitalis.Orbitalis;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Les sortes de centres spatiaux que l'on trouve dans le monde, inspirés des centres de la NASA et des agences
 * spatiales. Chacun a ses bâtiments, petits ou grands, et ses coffres.
 */
public enum FacilityKind implements StringRepresentable {
    /** Station météorologique : un bâtiment d'observation, un mât, un parc à instruments ; en grand, un radar sous radôme et l'abri des ballons-sondes. */
    WEATHER_STATION("weather_station"),
    /** Centre de recherche : un laboratoire ; en grand, un banc d'essai de moteurs et un hangar. */
    RESEARCH_CENTER("research_center"),
    /** Centre d'observation : une coupole de télescope, son poste de contrôle, une petite antenne ; en grand, un radiotélescope. */
    OBSERVATION_CENTER("observation_center"),
    /** Centre de contrôle et son pas de tir : le plus rare. La salle de tir, le bâtiment d'assemblage, le château d'eau et le pas de tir. */
    CONTROL_CENTER("control_center");

    public static final StringRepresentable.EnumCodec<FacilityKind> CODEC = StringRepresentable.fromEnum(FacilityKind::values);

    private final String id;

    FacilityKind(String id) {
        this.id = id;
    }

    /** Les coffres de ce centre. */
    public ResourceKey<LootTable> loot() {
        return ResourceKey.create(Registries.LOOT_TABLE, Orbitalis.id("chests/" + id));
    }

    /** Les coffres d'un centre abandonné : moins remplis, et de ce qui a été laissé derrière. */
    public static ResourceKey<LootTable> abandonedLoot() {
        return ResourceKey.create(Registries.LOOT_TABLE, Orbitalis.id("chests/abandoned_facility"));
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
