package com.orbitalis.platform.transfer.item;

import java.util.Objects;

import com.orbitalis.platform.transfer.resource.Resource;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/** Un objet, sans quantité : ce qu'on échange entre inventaires. */
public final class ItemResource implements Resource {
    public static final ItemResource EMPTY = new ItemResource(ItemStack.EMPTY);

    /** Un exemplaire de l'objet, avec ses composants ; jamais modifié. */
    private final ItemStack prototype;

    private ItemResource(ItemStack prototype) {
        this.prototype = prototype;
    }

    public static ItemResource of(ItemStack stack) {
        return stack.isEmpty() ? EMPTY : new ItemResource(stack.copyWithCount(1));
    }

    public static ItemResource of(ItemLike item) {
        return item.asItem() == Items.AIR ? EMPTY : new ItemResource(new ItemStack(item));
    }

    public Item getItem() {
        return prototype.getItem();
    }

    public Item value() {
        return getItem();
    }

    @Override
    public boolean isEmpty() {
        return prototype.isEmpty();
    }

    public DataComponentPatch getComponentsPatch() {
        return prototype.getComponentsPatch();
    }

    public boolean matches(ItemStack stack) {
        return ItemStack.isSameItemSameComponents(prototype, stack);
    }

    public boolean is(ItemLike item) {
        return prototype.is(item.asItem());
    }

    public ItemStack toStack(int count) {
        return isEmpty() || count <= 0 ? ItemStack.EMPTY : prototype.copyWithCount(count);
    }

    public ItemStack toStack() {
        return toStack(1);
    }

    public int getMaxStackSize() {
        return prototype.getMaxStackSize();
    }

    public Component getHoverName() {
        return prototype.getHoverName();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ItemResource resource && ItemStack.isSameItemSameComponents(resource.prototype, prototype);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getItem(), getComponentsPatch());
    }

    @Override
    public String toString() {
        return "ItemResource[" + prototype + "]";
    }
}
