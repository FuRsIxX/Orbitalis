package com.orbitalis.platform.transfer.item;

import com.orbitalis.platform.transfer.StacksResourceHandler;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

/** Un inventaire en emplacements, chacun limité à la taille de pile de son objet. */
public class ItemStacksResourceHandler extends StacksResourceHandler<ItemStack, ItemResource> {
    public ItemStacksResourceHandler(int size) {
        super(size, ItemStack.EMPTY, ItemStack.OPTIONAL_CODEC);
    }

    public ItemStacksResourceHandler(NonNullList<ItemStack> stacks) {
        super(stacks, ItemStack.EMPTY, ItemStack.OPTIONAL_CODEC);
    }

    @Override
    public ItemResource getResourceFrom(ItemStack stack) {
        return ItemResource.of(stack);
    }

    @Override
    public int getAmountFrom(ItemStack stack) {
        return stack.getCount();
    }

    @Override
    protected ItemStack getStackFrom(ItemResource resource, int amount) {
        return resource.toStack(amount);
    }

    @Override
    protected int getCapacity(int index, ItemResource resource) {
        return resource.isEmpty() ? 64 : resource.getMaxStackSize();
    }

    @Override
    protected ItemStack copyOf(ItemStack stack) {
        return stack.copy();
    }

    @Override
    public boolean matches(ItemStack stack, ItemResource resource) {
        return resource.matches(stack);
    }

    /** La pile de cet emplacement, telle quelle (à ne pas modifier). */
    public ItemStack getStack(int index) {
        return stacks.get(index);
    }
}
