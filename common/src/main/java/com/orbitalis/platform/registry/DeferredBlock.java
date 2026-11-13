package com.orbitalis.platform.registry;

import java.util.function.Supplier;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

/** Un bloc du mod : son entrée de registre, et l'objet qui le porte. */
public class DeferredBlock<B extends Block> extends DeferredHolder<Block, B> implements ItemLike {
    DeferredBlock(ResourceKey<Block> key, Supplier<B> value) {
        super(key, value);
    }

    @Override
    public Item asItem() {
        return get().asItem();
    }

    public ItemStack toStack() {
        return new ItemStack(this);
    }

    public ItemStack toStack(int count) {
        return new ItemStack(this, count);
    }
}
