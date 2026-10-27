package com.orbitalis.core.research;

import java.util.List;
import java.util.function.Supplier;

import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.registry.ModItems;

import net.minecraft.world.level.ItemLike;

/**
 * Jalons industriels : la première fois qu'une agence obtient un de ces objets, elle gagne des points de recherche.
 * <p>
 * C'est la première source de points du mod. Les missions, les données scientifiques et les essais
 * (bancs d'essai, souffleries) s'y ajouteront avec les étapes suivantes.
 */
public final class ResearchMilestones {
    /**
     * @param id     identifiant enregistré dans la sauvegarde
     * @param item   objet à obtenir
     * @param points points de recherche gagnés
     */
    public record Milestone(String id, Supplier<? extends ItemLike> item, int points) {
    }

    public static final List<Milestone> ALL = List.of(
            new Milestone("steel_ingot", ModItems.STEEL_INGOT, 5),
            new Milestone("steel_plate", ModItems.STEEL_PLATE, 5),
            new Milestone("aluminum_ingot", ModItems.ALUMINUM_INGOT, 5),
            new Milestone("aluminum_plate", ModItems.ALUMINUM_PLATE, 5),
            new Milestone("aluminum_lithium_ingot", ModItems.ALUMINUM_LITHIUM_INGOT, 10),
            new Milestone("titanium_ingot", ModItems.TITANIUM_INGOT, 10),
            new Milestone("titanium_plate", ModItems.TITANIUM_PLATE, 10),
            new Milestone("electrolyzer", ModBlocks.ELECTROLYZER, 5),
            new Milestone("cryogenic_liquefier", ModBlocks.CRYOGENIC_LIQUEFIER, 10),
            new Milestone("refinery", ModBlocks.REFINERY, 5),
            new Milestone("cryogenic_tank", ModBlocks.CRYOGENIC_TANK, 10),
            // Échantillons lunaires : les rapporter vaut de la recherche.
            new Milestone("moon_regolith", ModBlocks.MOON_REGOLITH, 40),
            new Milestone("moon_rock", ModBlocks.MOON_ROCK, 60),
            new Milestone("lunar_ice", ModBlocks.LUNAR_ICE, 120),
            // Échantillons martiens.
            new Milestone("mars_regolith", ModBlocks.MARS_REGOLITH, 80),
            new Milestone("mars_rock", ModBlocks.MARS_ROCK, 100));

    private ResearchMilestones() {
    }
}
