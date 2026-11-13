package com.orbitalis.core;

import java.util.List;
import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;
import com.orbitalis.core.config.StartupConfig;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.registry.ModItems;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

/**
 * Le contenu que le mod garde en réserve pour une prochaine version. Il reste enregistré (un monde qui l'a déjà ne
 * perd rien), mais il n'est montré nulle part tant que son réglage de démarrage est éteint : ni dans l'onglet créatif ni
 * dans JEI, ni dans les recettes, l'arbre de recherche, le guide ou les objectifs.
 */
public final class Features {
    private Features() {
    }

    /** Vrai si le voyage interstellaire est montré (il l'est, sauf si le réglage de démarrage le masque). */
    public static boolean interstellar() {
        return !StartupConfig.HIDE_INTERSTELLAR.getAsBoolean();
    }

    /** Les objets du voyage interstellaire. */
    private static final List<Supplier<? extends ItemLike>> INTERSTELLAR_ITEMS = List.of(
            ModItems.SHIPYARD_MODULE, ModBlocks.SHIPYARD_CONSOLE, ModBlocks.WARP_CORE, ModBlocks.RING_DRIVE, ModBlocks.HIBERNATION_POD,
            ModBlocks.SHIP_HELM, ModBlocks.PROXIMA_ROCK, ModBlocks.PROXIMA_REGOLITH, ModBlocks.PROXIMA_MOSS, ModBlocks.PROXIMA_ASH,
            ModBlocks.PROXIMA_FROST, ModBlocks.RED_SEDGE, ModBlocks.UMBRA_FERN, ModBlocks.LANTERN_BULB, ModBlocks.TRAPPIST_ROCK,
            ModBlocks.TRAPPIST_SAND, ModBlocks.TRAPPIST_MOSS, ModBlocks.SPIRE_REED, ModBlocks.GLEAM_CUP, ModBlocks.TIDE_GRASS,
            ModBlocks.KEPLER_ROCK, ModBlocks.KEPLER_SOIL, ModBlocks.KEPLER_TURF, ModBlocks.BRONZE_FERN, ModBlocks.AMBER_TUFT,
            ModItems.PROXIMA_WARP_PROBE, ModItems.TRAPPIST_WARP_PROBE, ModItems.KEPLER_WARP_PROBE, ModBlocks.TARANTULA_ROCK,
            ModBlocks.TARANTULA_DUST, ModBlocks.TARANTULA_CRYSTAL, ModBlocks.GLOWTHREAD, ModBlocks.ROSE_VEIL);

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

    /**
     * L'identifiant de la condition de recette « orbitalis:interstellar » : la recette n'existe que si le voyage interstellaire
     * est montré. Chaque chargeur l'inscrit dans son propre système de conditions (voir platform.RecipeConditions).
     */
    public static final net.minecraft.resources.ResourceLocation INTERSTELLAR_CONDITION = com.orbitalis.Orbitalis.id("interstellar");
}
