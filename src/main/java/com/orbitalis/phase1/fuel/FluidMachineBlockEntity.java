package com.orbitalis.phase1.fuel;

import java.util.List;
import java.util.function.Supplier;

import com.orbitalis.core.energy.MachineEnergyStorage;
import com.orbitalis.phase1.fuel.recipe.FluidRecipe;
import com.orbitalis.phase1.fuel.recipe.FluidRecipeInput;
import com.orbitalis.phase1.machines.MachineBlock;
import com.orbitalis.phase1.machines.MachineBlockEntity;
import com.orbitalis.phase1.machines.MachineInventory;
import com.orbitalis.phase1.machines.MachineMenu;
import com.orbitalis.phase1.machines.recipe.MachineRecipeLookup;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * Machine à fluides (électrolyseur, liquéfacteur cryogénique, raffinerie) : consomme de l'énergie pour
 * transformer une entrée (objet et/ou fluide) en un ou deux fluides, puis les envoie aux blocs voisins.
 * <p>
 * Réservoirs : {@code 0} = entrée, {@code 1} et {@code 2} = sorties.
 */
public class FluidMachineBlockEntity extends MachineBlockEntity {
    /** Capacité de chaque réservoir, en mB. */
    public static final int TANK_CAPACITY = 8_000;
    public static final int TANK_COUNT = 3;
    /** Débit maximal vers chaque bloc voisin, en mB par tick. */
    private static final int TRANSFER_PER_TICK = 100;
    /** Eau pompée par tick au contact d'une source d'eau, en mB. */
    private static final int WATER_INTAKE_PER_TICK = 100;
    private static final int INPUT_TANK = 0;

    private final FluidMachineLayout layout;
    private final Supplier<RecipeType<FluidRecipe>> recipeType;
    private final MachineTanks tanks;

    private int progress;
    private int maxProgress;
    /** Recette en cours ; recherchée à nouveau dès que les entrées changent. */
    private RecipeHolder<FluidRecipe> currentRecipe;
    private boolean inputsChanged = true;

    /** Valeurs synchronisées vers l'interface : données communes, progression, puis contenu des réservoirs. */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            int local = index - MachineMenu.BASE_DATA_COUNT;
            if (local < 0) {
                return baseData(index);
            }
            return switch (local) {
                case 0 -> progress;
                case 1 -> maxProgress;
                case 2 -> currentRecipe == null ? 0 : currentRecipe.value().energyPerTick();
                default -> {
                    // Pour chaque réservoir : identifiant du fluide, puis quantité.
                    int tank = (local - 3) / 2;
                    FluidStack fluid = tanks.getFluid(tank);
                    yield (local - 3) % 2 == 0 ? BuiltInRegistries.FLUID.getId(fluid.getFluid()) : fluid.getAmount();
                }
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return FluidMachineMenu.DATA_COUNT;
        }
    };

    public FluidMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, FluidMachineLayout layout,
            Supplier<RecipeType<FluidRecipe>> recipeType) {
        super(type, pos, state);
        this.layout = layout;
        this.recipeType = recipeType;
        // Le réservoir d'entrée n'accepte que les fluides utilisés par les recettes de la machine ;
        // l'automatisation ne vide que les réservoirs de sortie.
        this.tanks = new MachineTanks(TANK_COUNT, TANK_CAPACITY,
                (tank, fluid) -> tank == INPUT_TANK && layout.hasFluidInput() && acceptsFluid(fluid),
                tank -> tank != INPUT_TANK,
                this::onInputsChanged);
    }

    @Override
    protected MachineEnergyStorage createEnergy() {
        // La machine reçoit de l'énergie mais n'en fournit pas.
        return new MachineEnergyStorage(ENERGY_CAPACITY, ENERGY_TRANSFER, 0, this::setChanged);
    }

    @Override
    protected MachineInventory createInventory() {
        // Un emplacement d'entrée, utilisé seulement par les machines qui consomment un objet (raffinerie).
        return new MachineInventory(1,
                (slot, stack) -> layout.hasItemInput() && acceptsItem(stack),
                slot -> false,
                this::onInputsChanged);
    }

    private void onInputsChanged() {
        inputsChanged = true;
        setChanged();
    }

    public FluidMachineLayout getLayout() {
        return layout;
    }

    public MachineTanks getTanks() {
        return tanks;
    }

    /** Vrai si l'objet est consommé par au moins une recette de cette machine. */
    public boolean acceptsItem(ItemStack stack) {
        if (level == null || stack.isEmpty()) {
            return false;
        }
        var recipes = MachineRecipeLookup.recipes(level, recipeType.get());
        // Si le client n'a pas encore reçu les recettes, il laisse le serveur décider.
        if (recipes.isEmpty() && level.isClientSide()) {
            return true;
        }
        return recipes.stream().anyMatch(holder -> holder.value().usesItem(stack));
    }

    /** Vrai si le fluide est consommé par au moins une recette de cette machine. */
    public boolean acceptsFluid(Fluid fluid) {
        if (level == null) {
            return false;
        }
        return MachineRecipeLookup.recipes(level, recipeType.get()).stream().anyMatch(holder -> holder.value().usesFluid(fluid));
    }

    /** Un seau d'eau peut être vidé à la main dans le réservoir d'entrée. */
    @Override
    public ResourceHandler<FluidResource> getBucketFluidHandler() {
        return layout.hasFluidInput() ? tanks : null;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (layout.hasWaterIntake()) {
            pumpWater(level, pos);
        }

        if (inputsChanged) {
            inputsChanged = false;
            RecipeHolder<FluidRecipe> found = level.recipeAccess()
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
            FluidRecipe recipe = currentRecipe.value();
            if (canOutput(recipe) && energy.consume(recipe.energyPerTick())) {
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

        pushOutputs(level, pos);
    }

    /** Remplit le réservoir d'entrée au contact d'une source d'eau (qui n'est pas consommée). */
    private void pumpWater(ServerLevel level, BlockPos pos) {
        FluidStack content = tanks.getFluid(INPUT_TANK);
        boolean hasRoom = content.getAmount() + WATER_INTAKE_PER_TICK <= TANK_CAPACITY;
        if (!hasRoom || !(content.isEmpty() || content.getFluid() == Fluids.WATER)) {
            return;
        }
        for (Direction direction : Direction.values()) {
            FluidState neighbor = level.getFluidState(pos.relative(direction));
            if (neighbor.getType() == Fluids.WATER && neighbor.isSource()) {
                tanks.setFluid(INPUT_TANK, new FluidStack(Fluids.WATER, content.getAmount() + WATER_INTAKE_PER_TICK));
                return;
            }
        }
    }

    private FluidRecipeInput createInput() {
        return new FluidRecipeInput(inventory.getStack(0), tanks.getFluid(INPUT_TANK));
    }

    /** Vrai si chaque réservoir de sortie peut recevoir le fluide que la recette lui destine. */
    private boolean canOutput(FluidRecipe recipe) {
        List<FluidStackTemplate> outputs = recipe.fluidOutputs();
        for (int i = 0; i < outputs.size(); i++) {
            FluidStack content = tanks.getFluid(1 + i);
            FluidStackTemplate output = outputs.get(i);
            boolean sameFluid = content.isEmpty() || content.getFluid() == output.fluid().value();
            if (!sameFluid || content.getAmount() + output.amount() > TANK_CAPACITY) {
                return false;
            }
        }
        return true;
    }

    /** Consomme les entrées et ajoute les fluides produits dans les réservoirs de sortie. */
    private void craft(FluidRecipe recipe) {
        recipe.itemInput().ifPresent(ingredient -> {
            ItemStack stack = inventory.getStack(0);
            stack.shrink(ingredient.count());
            inventory.setStack(0, stack);
        });
        recipe.fluidInput().ifPresent(fluid -> {
            FluidStack content = tanks.getFluid(INPUT_TANK);
            int remaining = content.getAmount() - fluid.amount();
            tanks.setFluid(INPUT_TANK, remaining <= 0 ? FluidStack.EMPTY : new FluidStack(content.getFluid(), remaining));
        });
        List<FluidStackTemplate> outputs = recipe.fluidOutputs();
        for (int i = 0; i < outputs.size(); i++) {
            FluidStackTemplate output = outputs.get(i);
            int amount = tanks.getFluid(1 + i).getAmount() + output.amount();
            tanks.setFluid(1 + i, new FluidStack(output.fluid(), amount));
        }
    }

    /**
     * Envoie les fluides produits aux blocs voisins qui les acceptent (réservoir, autre machine, tuyau).
     * <p>
     * Une machine à une seule sortie envoie par toutes ses faces. Une machine à deux sorties (électrolyseur)
     * sépare ses fluides : la première sortie part par le côté gauche, la seconde par le côté droit,
     * vus en regardant la face avant. Sans cela, le premier gaz produit remplirait tous les voisins.
     */
    private void pushOutputs(ServerLevel level, BlockPos pos) {
        if (layout.outputCount() == 1) {
            if (!tanks.getFluid(1).isEmpty()) {
                for (Direction direction : Direction.values()) {
                    pushOutput(level, pos, direction, 1);
                }
            }
            return;
        }
        pushOutput(level, pos, getOutputSide(1), 1);
        pushOutput(level, pos, getOutputSide(2), 2);
    }

    /** Face par laquelle sort le fluide d'un réservoir de sortie, pour une machine à deux sorties. */
    public Direction getOutputSide(int tank) {
        Direction facing = getBlockState().getValue(MachineBlock.FACING);
        return tank == 1 ? facing.getClockWise() : facing.getCounterClockWise();
    }

    private void pushOutput(ServerLevel level, BlockPos pos, Direction direction, int tank) {
        Fluid fluid = tanks.getFluid(tank).getFluid();
        if (fluid == Fluids.EMPTY) {
            return;
        }
        ResourceHandler<FluidResource> neighbor =
                level.getCapability(Capabilities.Fluid.BLOCK, pos.relative(direction), direction.getOpposite());
        if (neighbor != null) {
            ResourceHandlerUtil.move(tanks.automationView(), neighbor, resource -> resource.getFluid() == fluid, TRANSFER_PER_TICK, null);
        }
    }

    @Override
    public void writeMenuData(RegistryFriendlyByteBuf buffer) {
        super.writeMenuData(buffer);
        buffer.writeEnum(layout);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new FluidMachineMenu(containerId, playerInventory, this, inventory, data, layout);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tanks.serialize(output.child("tanks"));
        output.putInt("progress", progress);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tanks.deserialize(input.childOrEmpty("tanks"));
        progress = input.getIntOr("progress", 0);
        inputsChanged = true;
    }
}
