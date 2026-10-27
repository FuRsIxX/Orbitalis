package com.orbitalis.phase1.rocket;

import java.util.ArrayList;
import java.util.List;

import com.orbitalis.core.registry.ModItems;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Brouillon de fusée, tel qu'on le règle sur l'ordinateur de conception : la charge utile, et pour chaque étage son
 * moteur, le nombre de moteurs, sa sorte de réservoir et le nombre de réservoirs. L'écran le compose, le serveur le
 * revérifie entièrement avant d'imprimer le plan : il ne fait pas confiance aux chiffres de l'écran.
 *
 * @param payload  la charge utile, ou {@link Items#AIR} pour aucune
 * @param stages   les étages, du premier au dernier (trois au plus)
 * @param boosters boosters latéraux du premier étage : 0, ou une paire
 */
public record RocketDraft(Item payload, List<Stage> stages, int boosters) {
    /** Un brouillon sans boosters latéraux. */
    public RocketDraft(Item payload, List<Stage> stages) {
        this(payload, stages, 0);
    }

    /** Plus grand nombre de moteurs d'un étage : un au centre, huit autour sur une fusée large. */
    public static final int MAX_ENGINES = 9;
    /** Plus grand nombre de réservoirs d'un étage. */
    public static final int MAX_TANKS = 48;

    /**
     * Un étage.
     *
     * @param engine  son moteur
     * @param engines nombre de moteurs (pour la poudre et les moteurs qui embarquent leurs ergols, c'est ce nombre
     *                qui dit combien l'étage en emporte)
     * @param tank    sa sorte de réservoir, ou {@link Items#AIR} pour un moteur qui embarque ses ergols
     * @param tanks   nombre de réservoirs
     */
    public record Stage(Item engine, int engines, Item tank, int tanks) {
        /** Vrai si les moteurs de l'étage embarquent leurs ergols : il n'a pas de réservoir. */
        public boolean preloaded() {
            RocketParts.Part part = RocketParts.get(new ItemStack(engine));
            return part != null && part.engine() != null && part.engine().propellant().isPreloaded();
        }
    }

    /** Le brouillon d'un plan déjà composé (un modèle du catalogue, par exemple). */
    public static RocketDraft of(NonNullList<ItemStack> slots) {
        RocketDesign design = RocketDesign.fromSlots(slots::get);
        List<Stage> stages = new ArrayList<>();
        for (RocketDesign.Stage stage : design.stages()) {
            if (stage.engines().isEmpty()) {
                break;
            }
            stages.add(new Stage(stage.engines().getItem(), stage.engines().getCount(),
                    stage.tanks().isEmpty() ? Items.AIR : stage.tanks().getItem(), stage.tanks().getCount()));
        }
        int boosters = design.boosters().is(ModItems.SOLID_BOOSTER.get()) ? design.boosters().getCount() : 0;
        return new RocketDraft(design.payload().isEmpty() ? Items.AIR : design.payload().getItem(), List.copyOf(stages), boosters);
    }

    /** Vrai si chaque pièce est à sa place et chaque nombre dans sa plage. Ne dit rien de ce que vaut la fusée. */
    public boolean isValid() {
        if (stages.isEmpty() || stages.size() > RocketDesign.MAX_STAGES || (boosters != 0 && boosters != RocketDesign.BOOSTER_PAIR)) {
            return false;
        }
        if (payload != Items.AIR && !RocketParts.is(new ItemStack(payload), RocketParts.Kind.PAYLOAD)) {
            return false;
        }
        for (Stage stage : stages) {
            if (!RocketParts.is(new ItemStack(stage.engine()), RocketParts.Kind.ENGINE) || stage.engines() < 1 || stage.engines() > MAX_ENGINES) {
                return false;
            }
            if (stage.preloaded() ? stage.tank() != Items.AIR
                    : !RocketParts.is(new ItemStack(stage.tank()), RocketParts.Kind.TANK) || stage.tanks() < 1 || stage.tanks() > MAX_TANKS) {
                return false;
            }
        }
        return true;
    }

    /** Les pièces de la fusée, emplacement par emplacement (voir {@link RocketDesign}) : la pointe, la charge, les étages. */
    public NonNullList<ItemStack> slots() {
        NonNullList<ItemStack> parts = NonNullList.withSize(RocketDesign.SLOT_COUNT, ItemStack.EMPTY);
        parts.set(RocketDesign.SLOT_NOSE, new ItemStack(ModItems.NOSE_CONE.get()));
        if (payload != Items.AIR) {
            parts.set(RocketDesign.SLOT_PAYLOAD, new ItemStack(payload));
        }
        if (boosters > 0) {
            parts.set(RocketDesign.SLOT_BOOSTERS, new ItemStack(ModItems.SOLID_BOOSTER.get(), boosters));
        }
        for (int i = 0; i < stages.size() && i < RocketDesign.MAX_STAGES; i++) {
            Stage stage = stages.get(i);
            parts.set(RocketDesign.engineSlot(i), new ItemStack(stage.engine(), stage.engines()));
            if (!stage.preloaded() && stage.tank() != Items.AIR) {
                parts.set(RocketDesign.tankSlot(i), new ItemStack(stage.tank(), stage.tanks()));
            }
            if (i > 0) {
                parts.set(RocketDesign.interstageSlot(i), new ItemStack(ModItems.INTERSTAGE.get()));
            }
        }
        return parts;
    }

    public RocketDesign design() {
        NonNullList<ItemStack> parts = slots();
        return RocketDesign.fromSlots(parts::get);
    }
}
