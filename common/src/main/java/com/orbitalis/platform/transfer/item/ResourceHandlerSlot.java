package com.orbitalis.platform.transfer.item;

import com.orbitalis.platform.transfer.ResourceHandler;
import com.orbitalis.platform.transfer.transaction.Transaction;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Un emplacement d'interface relié à un emplacement d'inventaire : ce qu'on y dépose passe par la réserve (et ses règles),
 * ce qu'on y prend en sort.
 */
public class ResourceHandlerSlot extends Slot {
    /** Change directement le contenu d'un emplacement de la réserve (hors transaction). */
    @FunctionalInterface
    public interface IndexModifier {
        void set(int index, ItemResource resource, int amount);
    }

    private static final Container EMPTY = new SimpleContainer(0);

    private final ResourceHandler<ItemResource> handler;
    private final IndexModifier modifier;
    private final int index;

    public ResourceHandlerSlot(ResourceHandler<ItemResource> handler, IndexModifier modifier, int index, int x, int y) {
        super(EMPTY, index, x, y);
        this.handler = handler;
        this.modifier = modifier;
        this.index = index;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            return handler.insert(index, ItemResource.of(stack), stack.getCount(), transaction) > 0;
        }
    }

    @Override
    public ItemStack getItem() {
        return handler.getResource(index).toStack(handler.getAmountAsInt(index));
    }

    @Override
    public boolean hasItem() {
        return !handler.getResource(index).isEmpty();
    }

    @Override
    public void set(ItemStack stack) {
        modifier.set(index, ItemResource.of(stack), stack.getCount());
        setChanged();
    }

    @Override
    public void setByPlayer(ItemStack stack) {
        set(stack);
    }

    @Override
    public ItemStack remove(int amount) {
        ItemStack current = getItem();
        int taken = Math.min(amount, current.getCount());
        if (taken <= 0) {
            return ItemStack.EMPTY;
        }
        modifier.set(index, ItemResource.of(current), current.getCount() - taken);
        setChanged();
        return current.copyWithCount(taken);
    }

    @Override
    public int getMaxStackSize() {
        return handler.getCapacityAsInt(index, ItemResource.EMPTY);
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return Math.min(stack.getMaxStackSize(), handler.getCapacityAsInt(index, ItemResource.of(stack)));
    }

    @Override
    public boolean mayPickup(Player player) {
        return !getItem().isEmpty();
    }

    @Override
    public void setChanged() {
    }

    public ResourceHandler<ItemResource> getResourceHandler() {
        return handler;
    }

    @Override
    public boolean isSameInventory(Slot other) {
        return other instanceof ResourceHandlerSlot slot && slot.handler == handler;
    }
}
