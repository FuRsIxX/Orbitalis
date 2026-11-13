package com.orbitalis.core.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Un composant de fabrication : une pièce d'électronique, de mécanique ou d'aménagement qui n'a pas d'autre usage
 * que d'entrer dans les recettes des blocs et des objets du mod (carte électronique, moteur électrique, vitre
 * renforcée…). Son infobulle le dit, et dit à quoi il sert.
 */
public class ComponentItem extends Item {
    public ComponentItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> builder$list, TooltipFlag flag) {
        java.util.function.Consumer<Component> builder = builder$list::add;
        builder.accept(Component.translatable("item.orbitalis.component.tooltip").withStyle(ChatFormatting.DARK_GRAY));
        builder.accept(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
