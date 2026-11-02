package com.orbitalis.phase1.machines;

import com.orbitalis.core.energy.MachineEnergyStorage;

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
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.Tags;

/**
 * Four électrique : il cuit ce que cuit le four du jeu (les recettes de four, celles du jeu comme celles des autres
 * mods), à l'électricité, sans combustible. Deux sortes :
 * <ul>
 * <li>le four à induction : tout ce qui se cuit, deux fois plus vite qu'un four ;</li>
 * <li>le four de fusion : seulement les minerais et les minerais bruts, au rythme d'un four mais trois fois plus
 * gourmand, et chaque minerai donne le double.</li>
 * </ul>
 * Emplacements : {@code 0} l'entrée, {@code 1} la sortie. L'écran est celui des machines de transformation.
 */
public class FurnaceMachineBlockEntity extends MachineBlockEntity {
    /** Consommation du four à induction, puis du four de fusion, en FE par tick. */
    public static final int INDUCTION_ENERGY = 20;
    public static final int MELTING_ENERGY = 60;

    private final boolean melting;
    private int progress;
    private int maxProgress;
    private RecipeHolder<SmeltingRecipe> currentRecipe;
    private boolean inputsChanged = true;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index - MachineMenu.BASE_DATA_COUNT) {
                case 0 -> progress;
                case 1 -> maxProgress;
                case 2 -> currentRecipe == null ? 0 : overclockEnergy(energyPerTick());
                case 3, 4 -> overclockData(index - MachineMenu.BASE_DATA_COUNT - 3);
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

    public FurnaceMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, boolean melting) {
        super(type, pos, state);
        this.melting = melting;
    }

    @Override
    protected MachineEnergyStorage createEnergy() {
        return new MachineEnergyStorage(ENERGY_CAPACITY, ENERGY_TRANSFER, 0, this::setChanged);
    }

    @Override
    protected MachineInventory createInventory() {
        // L'entrée n'accepte que ce que ce four sait cuire ; l'automatisation ne retire que de la sortie.
        return new MachineInventory(2, (slot, stack) -> slot == 0 && acceptsInput(stack), slot -> slot == 1, () -> {
            inputsChanged = true;
            setChanged();
        });
    }

    @Override
    public boolean supportsOverclock() {
        return true;
    }

    /** Consommation en marche à vitesse normale, en FE par tick. */
    public int energyPerTick() {
        return melting ? MELTING_ENERGY : INDUCTION_ENERGY;
    }

    /** Vrai pour un minerai ou un minerai brut : le four de fusion ne prend que ceux-là. */
    public static boolean isOre(ItemStack stack) {
        return stack.is(Tags.Items.ORES) || stack.is(Tags.Items.RAW_MATERIALS);
    }

    /** Vrai si ce four sait cuire cet objet. Le client, qui ne connaît pas les recettes de four, laisse le serveur juger. */
    public boolean acceptsInput(ItemStack stack) {
        if (stack.isEmpty() || (melting && !isOre(stack))) {
            return false;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        return serverLevel.recipeAccess().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), serverLevel).isPresent();
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        ItemStack input = inventory.getStack(0);
        if (inputsChanged) {
            inputsChanged = false;
            RecipeHolder<SmeltingRecipe> found = input.isEmpty() || (melting && !isOre(input)) ? null
                    : level.recipeAccess().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(input), level).orElse(null);
            if (found != currentRecipe) {
                currentRecipe = found;
                progress = 0;
            }
            int cooking = found == null ? 0 : found.value().cookingTime();
            maxProgress = found == null ? 0 : melting ? cooking : Math.max(1, cooking / 2);
        }
        boolean working = false;
        if (currentRecipe == null) {
            progress = 0;
        } else {
            ItemStack result = result(input);
            if (!result.isEmpty() && canOutput(result) && energy.consume(overclockEnergy(energyPerTick()))) {
                working = true;
                progress += speed();
                if (progress >= maxProgress) {
                    input.shrink(1);
                    inventory.setStack(0, input);
                    ItemStack output = inventory.getStack(1);
                    if (output.isEmpty()) {
                        inventory.setStack(1, result);
                    } else {
                        output.grow(result.getCount());
                        inventory.setStack(1, output);
                    }
                    progress = 0;
                }
                setChanged();
            }
        }
        setLit(working);
    }

    /** Ce que donne une cuisson : le résultat du four, doublé dans le four de fusion. */
    private ItemStack result(ItemStack input) {
        ItemStack result = currentRecipe.value().assemble(new SingleRecipeInput(input));
        if (melting && !result.isEmpty()) {
            result.setCount(Math.min(result.getMaxStackSize(), result.getCount() * 2));
        }
        return result;
    }

    private boolean canOutput(ItemStack result) {
        ItemStack output = inventory.getStack(1);
        return output.isEmpty() || (ItemStack.isSameItemSameComponents(output, result) && output.getCount() + result.getCount() <= output.getMaxStackSize());
    }

    @Override
    public void writeMenuData(RegistryFriendlyByteBuf buffer) {
        super.writeMenuData(buffer);
        buffer.writeVarInt(1);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ProcessingMachineMenu(containerId, playerInventory, this, inventory, upgrades, data, 1);
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
