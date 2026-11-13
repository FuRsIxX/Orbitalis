package com.orbitalis.core.research;

import java.util.function.Supplier;

import com.orbitalis.core.registry.ModItems;

import net.minecraft.world.level.ItemLike;

/**
 * Arbres de recherche : chacun regroupe les technologies d'un domaine.
 * L'ordre de déclaration est celui de l'affichage dans le terminal de recherche.
 */
public enum ResearchTree {
    PROPULSION("propulsion", ModItems.KEROLOX_ENGINE),
    STRUCTURES("structures", ModItems.STEEL_PLATE),
    AVIONICS("avionics", ModItems.FLIGHT_PROCESSOR),
    GROUND("ground", ModItems.PAD_KIT),
    SATELLITES("satellites", ModItems.COMMUNICATION_SATELLITE),
    CREWED("crewed", ModItems.CREW_CAPSULE),
    STATIONS("stations", ModItems.STATION_CORE),
    EXPLORATION("exploration", ModItems.ROVER),
    ADVANCED("advanced", ModItems.FUSION_ENGINE);

    private final String id;
    private final Supplier<? extends ItemLike> icon;

    ResearchTree(String id, Supplier<? extends ItemLike> icon) {
        this.id = id;
        this.icon = icon;
    }

    public String id() {
        return id;
    }

    public ItemLike icon() {
        return icon.get();
    }

    /** Clé de traduction du nom de l'arbre. */
    public String nameKey() {
        return "research.orbitalis.tree." + id;
    }
}
