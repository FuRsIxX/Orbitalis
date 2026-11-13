package com.orbitalis.phase2.survival;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.orbitalis.Orbitalis;
import com.orbitalis.core.registry.ModDataComponents;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * Les combinaisons spatiales, en quatre pièces : le casque, le torse (avec le sac de survie et sa réserve
 * d'oxygène), les jambières et les bottes. Dans le vide, il faut les quatre : sans l'une d'elles, la combinaison
 * n'est pas étanche.
 * <ul>
 * <li>La combinaison orange, de lancement et de sortie : la survie ordinaire, dix minutes d'oxygène.</li>
 * <li>La combinaison blanche, avancée : un quart d'heure d'oxygène, une meilleure protection, et des modules à
 * fixer sur chacune de ses pièces (voir {@link SuitModule}).</li>
 * </ul>
 */
public final class SpaceSuits {
    /** Réserve d'oxygène de la combinaison avancée, et ce que lui ajoute son réservoir supplémentaire, en secondes. */
    public static final int ADVANCED_OXYGEN = 900;
    public static final int TANK_OXYGEN = 600;
    /** Les quatre pièces, de la tête aux pieds. */
    public static final EquipmentSlot[] PIECES = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    /** Pression de la combinaison fermée, en kPa : de l'oxygène pur, à un peu moins du tiers de la pression de l'air. */
    public static final float SUIT_PRESSURE = 29.6F;

    private SpaceSuits() {
    }

    /** Vrai pour toute pièce de combinaison. */
    public static boolean isPiece(ItemStack stack) {
        return stack.getItem() instanceof SpaceSuitItem;
    }

    /** Vrai pour une pièce de combinaison faite pour cet emplacement. */
    public static boolean isPiece(ItemStack stack, EquipmentSlot slot) {
        return stack.getItem() instanceof SpaceSuitItem piece && piece.slot() == slot;
    }

    public static boolean isAdvanced(ItemStack stack) {
        return stack.getItem() instanceof SpaceSuitItem piece && piece.advanced();
    }

    /** La première pièce qui manque à ce joueur pour être étanche, ou {@code null} s'il porte les quatre. */
    public static @Nullable EquipmentSlot missing(LivingEntity entity) {
        for (EquipmentSlot slot : PIECES) {
            if (!isPiece(entity.getItemBySlot(slot), slot)) {
                return slot;
            }
        }
        return null;
    }

    public static List<SuitModule> modules(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.SUIT_MODULES.get(), List.of());
    }

    public static boolean has(ItemStack stack, SuitModule module) {
        return isAdvanced(stack) && modules(stack).contains(module);
    }

    /** Vrai si ce joueur porte, à sa place, une pièce munie de ce module. */
    public static boolean has(LivingEntity entity, SuitModule module) {
        return has(entity.getItemBySlot(module.slot()), module);
    }

    /** Pourquoi ce module ne peut pas aller sur cette pièce, ou {@code null} s'il le peut. */
    public static @Nullable String refusal(ItemStack piece, SuitModule module) {
        if (!isAdvanced(piece) || !isPiece(piece, module.slot())) {
            return "suit.orbitalis.module.wrong_piece";
        }
        return modules(piece).contains(module) ? "suit.orbitalis.module.already" : null;
    }

    /** Fixe ce module sur cette pièce, si elle l'accepte. */
    public static boolean install(ItemStack piece, SuitModule module) {
        if (refusal(piece, module) != null) {
            return false;
        }
        List<SuitModule> list = new ArrayList<>(modules(piece));
        list.add(module);
        piece.set(ModDataComponents.SUIT_MODULES.get(), List.copyOf(list));
        refresh(piece);
        return true;
    }

    /** Ôte ce module de cette pièce ; faux s'il n'y était pas. */
    public static boolean remove(ItemStack piece, SuitModule module) {
        List<SuitModule> list = new ArrayList<>(modules(piece));
        if (!list.remove(module)) {
            return false;
        }
        if (list.isEmpty()) {
            piece.remove(ModDataComponents.SUIT_MODULES.get());
        } else {
            piece.set(ModDataComponents.SUIT_MODULES.get(), List.copyOf(list));
        }
        refresh(piece);
        return true;
    }

    /** Ôte le dernier module posé sur cette pièce, et le rend ; {@code null} s'il n'y en a pas. */
    public static @Nullable SuitModule removeLast(ItemStack piece) {
        List<SuitModule> list = new ArrayList<>(modules(piece));
        if (list.isEmpty()) {
            return null;
        }
        SuitModule module = list.removeLast();
        if (list.isEmpty()) {
            piece.remove(ModDataComponents.SUIT_MODULES.get());
        } else {
            piece.set(ModDataComponents.SUIT_MODULES.get(), List.copyOf(list));
        }
        refresh(piece);
        return module;
    }

    /** Remet la réserve d'oxygène et les protections d'une pièce d'accord avec ses modules. */
    private static void refresh(ItemStack piece) {
        if (!(piece.getItem() instanceof SpaceSuitItem item)) {
            return;
        }
        List<SuitModule> list = modules(piece);
        Integer base = piece.getItem().components().get(DataComponents.MAX_DAMAGE);
        if (base != null) {
            // L'oxygène est ce qui reste de la réserve : le réservoir en plus arrive plein, et s'en va avec ce qu'il contenait.
            piece.set(DataComponents.MAX_DAMAGE, base + (list.contains(SuitModule.OXYGEN_TANK) ? TANK_OXYGEN : 0));
            piece.setDamageValue(Math.min(piece.getDamageValue(), piece.getMaxDamage()));
        }
        piece.set(DataComponents.ATTRIBUTE_MODIFIERS, attributes(item.advanced(), item.slot(), list));
    }

    /**
     * Protections d'une pièce : un peu d'armure (la combinaison orange protège comme du cuir, la blanche comme du
     * fer, ou presque), et les amortisseurs des jambes, qui allongent les chutes sans mal et adoucissent les autres.
     */
    public static ItemAttributeModifiers attributes(boolean advanced, EquipmentSlot slot, List<SuitModule> modules) {
        int armor = switch (slot) {
            case HEAD -> advanced ? 2 : 1;
            case CHEST -> advanced ? 5 : 3;
            case LEGS -> advanced ? 4 : 2;
            case FEET -> advanced ? 2 : 1;
            default -> 0;
        };
        EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(slot);
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder()
                .add(Attributes.ARMOR, new AttributeModifier(Orbitalis.id("suit_armor." + slot.getName()), armor, AttributeModifier.Operation.ADD_VALUE),
                        group);
        if (modules.contains(SuitModule.SHOCK_ABSORBERS)) {
            builder.add(Attributes.SAFE_FALL_DISTANCE, new AttributeModifier(Orbitalis.id("suit_shock_absorbers"), 6.0,
                    AttributeModifier.Operation.ADD_VALUE), group);
            builder.add(Attributes.FALL_DAMAGE_MULTIPLIER, new AttributeModifier(Orbitalis.id("suit_shock_absorbers"), -0.5,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), group);
        }
        return builder.build();
    }
}
