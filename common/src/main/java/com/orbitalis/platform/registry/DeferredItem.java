package com.orbitalis.platform.registry;

import java.util.function.Supplier;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/** Un objet du mod : son entrée de registre. */
public class DeferredItem<I extends Item> extends DeferredHolder<Item, I> implements ItemLike {
    DeferredItem(ResourceKey<Item> key, Supplier<I> value) {
        super(key, value);
    }

    @Override
    public Item asItem() {
        return get();
    }

    public ItemStack toStack() {
        return new ItemStack(get());
    }

    public ItemStack toStack(int count) {
        return new ItemStack(get(), count);
    }
}
