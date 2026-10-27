package com.orbitalis.core;

import java.util.List;
import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;
import com.orbitalis.core.config.StartupConfig;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.registry.ModItems;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * Le contenu que le mod garde en réserve pour une prochaine version. Il reste enregistré (un monde qui l'a déjà ne
 * perd rien), mais il n'est montré nulle part tant que son réglage de démarrage est éteint : ni dans l'onglet créatif ni
 * dans JEI, ni dans les recettes, l'arbre de recherche, le guide ou les objectifs.
 */
public final class Features {
    private Features() {
    }

    /** Vrai si le voyage interstellaire est montré : chantier naval, vaisseau, moteur à distorsion, Proxima b. */
    public static boolean interstellar() {
        return StartupConfig.INTERSTELLAR.getAsBoolean();
    }

    /** Les objets du voyage interstellaire. */
    private static final List<Supplier<? extends ItemLike>> INTERSTELLAR_ITEMS = List.of(
            ModItems.SHIPYARD_MODULE, ModBlocks.SHIPYARD_CONSOLE, ModBlocks.WARP_CORE, ModBlocks.RING_DRIVE, ModBlocks.HIBERNATION_POD,
            ModBlocks.SHIP_HELM, ModBlocks.PROXIMA_ROCK, ModBlocks.PROXIMA_REGOLITH, ModBlocks.PROXIMA_MOSS, ModBlocks.PROXIMA_ASH,
            ModBlocks.PROXIMA_FROST, ModBlocks.RED_SEDGE, ModBlocks.UMBRA_FERN, ModBlocks.LANTERN_BULB);

    /** Vrai si cet objet est en réserve : il fait partie d'un contenu qui n'est pas montré. */
    public static boolean hidden(ItemLike item) {
        if (interstellar()) {
            return false;
        }
        Item target = item.asItem();
        for (Supplier<? extends ItemLike> entry : INTERSTELLAR_ITEMS) {
            if (entry.get().asItem() == target) {
                return true;
            }
        }
        return false;
    }

    /** Les objets en réserve, pour JEI. */
    public static List<Item> hiddenItems() {
        return interstellar() ? List.of() : INTERSTELLAR_ITEMS.stream().map(entry -> entry.get().asItem()).toList();
    }

    /** Condition de recette : la recette n'existe que si le voyage interstellaire est montré. */
    public record InterstellarCondition() implements ICondition {
        public static final InterstellarCondition INSTANCE = new InterstellarCondition();
        public static final MapCodec<InterstellarCondition> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public boolean test(IContext context) {
            return interstellar();
        }

        @Override
        public MapCodec<? extends ICondition> codec() {
            return CODEC;
        }
    }
}
