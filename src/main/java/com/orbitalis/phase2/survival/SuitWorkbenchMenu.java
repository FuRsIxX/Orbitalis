package com.orbitalis.phase2.survival;

import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.registry.ModMenus;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * L'établi de combinaison : quatre emplacements, un par pièce (casque, torse, jambières, bottes), et l'inventaire du
 * joueur. Les boutons de l'écran fixent un module pris dans l'inventaire sur la pièce qui le reçoit, ou le retirent
 * et le rendent. Les pièces posées sur l'établi reviennent au joueur quand il le quitte.
 */
public class SuitWorkbenchMenu extends AbstractContainerMenu {
    /** Les emplacements des pièces, dans la fenêtre : une colonne, de la tête aux pieds. */
    public static final int PIECE_X = 16;
    public static final int PIECE_Y = 48;
    public static final int PIECE_STEP = 24;
    /** L'inventaire du joueur, dans la fenêtre : en bas à gauche, sous la combinaison. */
    public static final int INVENTORY_X = 13;
    public static final int INVENTORY_Y = 167;
    /** Boutons : fixer un module (son rang), le retirer (son rang + {@link #REMOVE}). */
    public static final int REMOVE = 16;

    private final Container suit = new SimpleContainer(SpaceSuits.PIECES.length) {
        @Override
        public void setChanged() {
            super.setChanged();
            slotsChanged(this);
        }
    };
    private final ContainerLevelAccess access;

    /** Côté client. */
    public SuitWorkbenchMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public SuitWorkbenchMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(ModMenus.SUIT_WORKBENCH.get(), containerId);
        this.access = access;
        for (int i = 0; i < SpaceSuits.PIECES.length; i++) {
            EquipmentSlot piece = SpaceSuits.PIECES[i];
            addSlot(new Slot(suit, i, PIECE_X, PIECE_Y + i * PIECE_STEP) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return SpaceSuits.isPiece(stack, piece);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
    }

    /** La pièce posée à cet emplacement (casque, torse, jambières ou bottes), ou une pile vide. */
    public ItemStack piece(EquipmentSlot slot) {
        for (int i = 0; i < SpaceSuits.PIECES.length; i++) {
            if (SpaceSuits.PIECES[i] == slot) {
                return suit.getItem(i);
            }
        }
        return ItemStack.EMPTY;
    }

    /** Le premier module de ce type dans l'inventaire du joueur, ou une pile vide. */
    public static ItemStack moduleIn(Inventory inventory, SuitModule module) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(module.item())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        boolean remove = buttonId >= REMOVE;
        int index = remove ? buttonId - REMOVE : buttonId;
        if (index < 0 || index >= SuitModule.values().length) {
            return false;
        }
        SuitModule module = SuitModule.values()[index];
        ItemStack piece = piece(module.slot());
        if (remove) {
            if (!SpaceSuits.remove(piece, module)) {
                return false;
            }
            ItemStack back = new ItemStack(module.item());
            if (!player.getInventory().add(back)) {
                player.drop(back, false);
            }
            sound(player, 0.85F);
        } else {
            ItemStack item = moduleIn(player.getInventory(), module);
            if (item.isEmpty() || !SpaceSuits.install(piece, module)) {
                return false;
            }
            item.shrink(1);
            sound(player, 1.2F);
        }
        suit.setChanged();
        return true;
    }

    private void sound(Player player, float pitch) {
        access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.BLOCKS, 0.8F, pitch));
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.SUIT_WORKBENCH.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        access.execute((level, pos) -> clearContainer(player, suit));
    }

    /** Maj + clic : une pièce va à sa place sur l'établi, ou en revient. */
    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int pieces = SpaceSuits.PIECES.length;
        if (slotIndex < pieces) {
            if (!moveItemStackTo(stack, pieces, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, pieces, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }
}
