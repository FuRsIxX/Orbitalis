package com.orbitalis.phase1.machines;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.orbitalis.core.energy.MachineEnergyStorage;
import com.orbitalis.phase1.machines.recipe.MachineRecipe;
import com.orbitalis.phase1.machines.recipe.MachineRecipeInput;
import com.orbitalis.phase1.machines.recipe.MachineRecipeLookup;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Machine de transformation électrique : consomme de l'énergie pour transformer
 * ses entrées en une sortie, selon les recettes de son type (fonderie à arc, laminoir...).
 * <p>
 * Emplacements : {@code 0 .. inputCount-1} = entrées, {@code inputCount} = sortie.
 */
public class ProcessingMachineBlockEntity extends MachineBlockEntity {
    private final Supplier<RecipeType<MachineRecipe>> recipeType;
    private final int inputCount;

    private int progress;
    private int maxProgress;
    /** Recette en cours ; recherchée à nouveau dès que les entrées changent. */
    private RecipeHolder<MachineRecipe> currentRecipe;
    private boolean inputsChanged = true;

    /** Valeurs synchronisées vers l'interface du joueur : données communes, puis progression et consommation. */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index - MachineMenu.BASE_DATA_COUNT) {
                case 0 -> progress;
                case 1 -> maxProgress;
                case 2 -> currentRecipe == null ? 0 : currentRecipe.value().energyPerTick();
                default -> baseData(index);
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return ProcessingMachineMenu.DATA_COUNT;
        }
    };

    public ProcessingMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
            Supplier<RecipeType<MachineRecipe>> recipeType, int inputCount) {
        super(type, pos, state);
        this.recipeType = recipeType;
        this.inputCount = inputCount;
    }

    @Override
    protected MachineEnergyStorage createEnergy() {
        // La machine reçoit de l'énergie mais n'en fournit pas.
        return new MachineEnergyStorage(ENERGY_CAPACITY, ENERGY_TRANSFER, 0, this::setChanged);
    }

    @Override
    protected MachineInventory createInventory() {
        // Deux entrées au maximum et une sortie. Les entrées n'acceptent que les ingrédients des recettes
        // de la machine ; l'automatisation ne retire que de la sortie.
        return new MachineInventory(3,
                (slot, stack) -> slot < inputCount && acceptsInput(stack),
                slot -> slot == inputCount,
                () -> {
                    inputsChanged = true;
                    setChanged();
                });
    }

    public int getInputCount() {
        return inputCount;
    }

    /** Vrai si l'objet est un ingrédient d'au moins une recette de cette machine. */
    public boolean acceptsInput(ItemStack stack) {
        return MachineRecipeLookup.isIngredient(level, recipeType.get(), stack);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (inputsChanged) {
            inputsChanged = false;
            RecipeHolder<MachineRecipe> found = level.recipeAccess()
                    .getRecipeFor(recipeType.get(), createInput(), level).orElse(null);
            if (found != currentRecipe) {
                currentRecipe = found;
                progress = 0;
            }
            maxProgress = found == null ? 0 : found.value().time();
        }

        boolean working = false;
        if (currentRecipe == null) {
            progress = 0;
        } else {
            MachineRecipe recipe = currentRecipe.value();
            if (canOutput(recipe.result().create()) && energy.consume(recipe.energyPerTick())) {
                working = true;
                progress++;
                if (progress >= recipe.time()) {
                    craft(recipe);
                    progress = 0;
                }
                setChanged();
            }
        }
        setLit(working);
    }

    private MachineRecipeInput createInput() {
        List<ItemStack> stacks = new ArrayList<>(inputCount);
        for (int slot = 0; slot < inputCount; slot++) {
            stacks.add(inventory.getStack(slot));
        }
        return new MachineRecipeInput(stacks);
    }

    /** Vrai si l'emplacement de sortie peut recevoir le résultat. */
    private boolean canOutput(ItemStack result) {
        ItemStack output = inventory.getStack(inputCount);
        if (output.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(output, result)
                && output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    /** Retire les ingrédients et ajoute le résultat dans la sortie. */
    private void craft(MachineRecipe recipe) {
        int[] slots = recipe.assignSlots(createInput());
        if (slots == null) {
            return;
        }
        for (int i = 0; i < slots.length; i++) {
            ItemStack stack = inventory.getStack(slots[i]);
            stack.shrink(recipe.ingredients().get(i).count());
            inventory.setStack(slots[i], stack);
        }
        ItemStack result = recipe.result().create();
        ItemStack output = inventory.getStack(inputCount);
        if (output.isEmpty()) {
            inventory.setStack(inputCount, result);
        } else {
            output.grow(result.getCount());
            inventory.setStack(inputCount, output);
        }
    }

    @Override
    public void writeMenuData(RegistryFriendlyByteBuf buffer) {
        super.writeMenuData(buffer);
        buffer.writeVarInt(inputCount);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ProcessingMachineMenu(containerId, playerInventory, this, inventory, data, inputCount);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("progress", progress);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        progress = input.getIntOr("progress", 0);
        inputsChanged = true;
    }
}
