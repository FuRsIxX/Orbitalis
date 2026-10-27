package com.orbitalis.phase1.rocket;

import java.util.List;

import com.orbitalis.core.registry.ModItems;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Plan de fusée : la feuille que sort l'ordinateur de conception. Elle décrit une fusée précise, pièce par pièce,
 * aux mêmes emplacements que le plan d'une fusée assemblée (voir {@link RocketDesign}).
 * <p>
 * Un clic droit sur la table d'un pas de tir assemblé, assez grand pour elle, y dépose le plan : les blocs à poser
 * apparaissent au-dessus de la table, et la fusée se forme quand le dernier est en place.
 */
public class RocketBlueprintItem extends Item {
    public RocketBlueprintItem(Properties properties) {
        super(properties);
    }

    /** Fabrique le plan d'une fusée, et lui donne son nom. */
    public static ItemStack create(List<ItemStack> slots, Component name) {
        ItemStack blueprint = new ItemStack(ModItems.ROCKET_BLUEPRINT.get());
        blueprint.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(slots));
        blueprint.set(DataComponents.CUSTOM_NAME, name);
        return blueprint;
    }

    /** Un plan d'archive dit qu'il n'est pas encore validé. */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<Component> builder, net.minecraft.world.item.TooltipFlag flag) {
        if (isArchive(stack)) {
            builder.accept(Component.translatable("item.orbitalis.rocket_blueprint.unvalidated").withStyle(net.minecraft.ChatFormatting.GOLD));
        }
    }

    /** Vrai pour un plan d'archive pas encore validé. */
    public static boolean isArchive(ItemStack stack) {
        return stack.has(com.orbitalis.core.registry.ModDataComponents.ARCHIVE_PLAN.get());
    }

    /** Pièces de la fusée décrite par le plan, emplacement par emplacement. */
    public static NonNullList<ItemStack> parts(ItemStack blueprint) {
        NonNullList<ItemStack> parts = NonNullList.withSize(RocketDesign.SLOT_COUNT, ItemStack.EMPTY);
        blueprint.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(parts);
        return parts;
    }

    /** La fusée décrite par le plan. */
    public static RocketDesign design(ItemStack blueprint) {
        NonNullList<ItemStack> parts = parts(blueprint);
        return RocketDesign.fromSlots(parts::get);
    }
}
