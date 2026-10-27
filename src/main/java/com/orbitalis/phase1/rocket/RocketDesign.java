package com.orbitalis.phase1.rocket;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import net.minecraft.world.item.ItemStack;

/**
 * Plan d'une fusée, tel qu'il est composé dans l'atelier d'assemblage.
 *
 * @param stages   étages, du premier (en bas, allumé au décollage) au dernier
 * @param payload  charge utile, ou une pile vide
 * @param nose     coiffe, ou une pile vide
 * @param boosters boosters latéraux accrochés au premier étage (une paire), ou une pile vide
 */
public record RocketDesign(List<Stage> stages, ItemStack payload, ItemStack nose, ItemStack boosters) {
    /** Une fusée sans boosters latéraux. */
    public RocketDesign(List<Stage> stages, ItemStack payload, ItemStack nose) {
        this(stages, payload, nose, ItemStack.EMPTY);
    }

    /** Les boosters vont par paire, un de chaque côté du premier étage. */
    public static final int BOOSTER_PAIR = 2;

    /** Nombre maximal d'étages. */
    public static final int MAX_STAGES = 3;

    // Emplacements de l'atelier. Le premier étage n'a pas d'interétage : rien ne se trouve sous lui.
    public static final int SLOT_PAYLOAD = 0;
    public static final int SLOT_NOSE = 1;
    private static final int[] SLOT_ENGINE = {2, 4, 7};
    private static final int[] SLOT_TANK = {3, 5, 8};
    private static final int[] SLOT_INTERSTAGE = {-1, 6, 9};
    /** Les boosters latéraux : ajoutés en dernier, les plans d'avant les lisent vides. */
    public static final int SLOT_BOOSTERS = 10;
    public static final int SLOT_COUNT = 11;

    /**
     * Étage.
     *
     * @param engines    moteurs (tous identiques) ; la taille de la pile donne leur nombre
     * @param tanks      réservoirs ; la taille de la pile donne leur nombre
     * @param interstage interétage qui relie cet étage à celui du dessous
     */
    public record Stage(ItemStack engines, ItemStack tanks, ItemStack interstage) {
        /** Vrai si rien n'est installé dans cet étage. */
        public boolean isEmpty() {
            return engines.isEmpty() && tanks.isEmpty() && interstage.isEmpty();
        }
    }

    public static int engineSlot(int stage) {
        return SLOT_ENGINE[stage];
    }

    public static int tankSlot(int stage) {
        return SLOT_TANK[stage];
    }

    /** Emplacement de l'interétage d'un étage, ou -1 pour le premier étage. */
    public static int interstageSlot(int stage) {
        return SLOT_INTERSTAGE[stage];
    }

    /** Rôle de pièce accepté par un emplacement de l'atelier. */
    public static RocketParts.Kind kindOf(int slot) {
        if (slot == SLOT_PAYLOAD) {
            return RocketParts.Kind.PAYLOAD;
        }
        if (slot == SLOT_NOSE) {
            return RocketParts.Kind.NOSE_CONE;
        }
        if (slot == SLOT_BOOSTERS) {
            return RocketParts.Kind.ENGINE;
        }
        for (int stage = 0; stage < MAX_STAGES; stage++) {
            if (slot == SLOT_ENGINE[stage]) {
                return RocketParts.Kind.ENGINE;
            }
            if (slot == SLOT_TANK[stage]) {
                return RocketParts.Kind.TANK;
            }
        }
        return RocketParts.Kind.INTERSTAGE;
    }

    /** Lit le plan dans le contenu des emplacements de l'atelier. */
    public static RocketDesign fromSlots(IntFunction<ItemStack> slots) {
        List<Stage> stages = new ArrayList<>();
        for (int stage = 0; stage < MAX_STAGES; stage++) {
            ItemStack interstage = SLOT_INTERSTAGE[stage] < 0 ? ItemStack.EMPTY : slots.apply(SLOT_INTERSTAGE[stage]);
            stages.add(new Stage(slots.apply(SLOT_ENGINE[stage]), slots.apply(SLOT_TANK[stage]), interstage));
        }
        return new RocketDesign(stages, slots.apply(SLOT_PAYLOAD), slots.apply(SLOT_NOSE), slots.apply(SLOT_BOOSTERS));
    }
}
