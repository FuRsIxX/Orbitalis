package com.orbitalis.phase1.rocket;

import java.util.List;

import com.orbitalis.core.registry.ModItems;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

/**
 * Fusée assemblée : sort de l'atelier d'assemblage et se pose sur une table de lancement.
 * <p>
 * Ses pièces sont rangées dans l'objet, aux mêmes emplacements que dans l'atelier (voir {@link RocketDesign}).
 * Accroupi, un clic droit dans le vide la démonte et rend les pièces.
 */
public class RocketItem extends Item {
    public RocketItem(Properties properties) {
        super(properties);
    }

    /** Fabrique une fusée à partir du contenu des emplacements de l'atelier. */
    public static ItemStack create(List<ItemStack> slots) {
        ItemStack rocket = new ItemStack(ModItems.ROCKET.get());
        rocket.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(slots));
        return rocket;
    }

    /** Pièces de la fusée, emplacement par emplacement. */
    public static NonNullList<ItemStack> parts(ItemStack rocket) {
        NonNullList<ItemStack> parts = NonNullList.withSize(RocketDesign.SLOT_COUNT, ItemStack.EMPTY);
        rocket.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(parts);
        return parts;
    }

    /** Plan de la fusée. */
    public static RocketDesign design(ItemStack rocket) {
        NonNullList<ItemStack> parts = parts(rocket);
        return RocketDesign.fromSlots(parts::get);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            ItemStack rocket = player.getItemInHand(hand);
            for (ItemStack part : parts(rocket)) {
                if (!part.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(part);
                }
            }
            rocket.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }
}
