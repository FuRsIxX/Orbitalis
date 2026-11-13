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
import com.orbitalis.platform.nbt.ValueInput;
import com.orbitalis.platform.nbt.ValueOutput;
import com.orbitalis.platform.capability.Capabilities;
import com.orbitalis.platform.fluid.FluidStack;
import com.orbitalis.platform.fluid.FluidStackTemplate;
import com.orbitalis.platform.transfer.ResourceHandler;
import com.orbitalis.platform.transfer.ResourceHandlerUtil;
import com.orbitalis.platform.transfer.fluid.FluidResource;

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
    /** Débit de la purge, en mB par tick : plus que ne produit aucune recette, pour que le niveau tienne. */
    public static final int PURGE_PER_TICK = 250;
    /** Intervalle entre deux bouffées de la purge, en ticks. */
    private static final int PURGE_PUFF_INTERVAL = 8;

    /**
     * Purge d'un réservoir de sortie : quand l'une des sorties est pleine, la machine s'arrête, même si l'autre a de la
     * place. La purge rejette à l'air ce qui gêne.
     */
    public enum Purge {
        /** Rien n'est rejeté. */
        OFF,
        /** Le réservoir ne dépasse jamais la moitié : le surplus est rejeté. */
        HALF,
        /** Tout ce qui arrive est rejeté : le réservoir reste vide. */
        FULL;

        /** Le mode suivant, au clic gauche : arrêt, moitié, totale, puis arrêt. */
        public Purge next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private final FluidMachineLayout layout;
    private final Supplier<RecipeType<FluidRecipe>> recipeType;
    private final MachineTanks tanks;

    private int progress;
    private int maxProgress;
    /** Purge de chaque réservoir (celle de l'entrée reste arrêtée). */
    private final Purge[] purge = {Purge.OFF, Purge.OFF, Purge.OFF};
    private int purgeTicks;
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
                case 2 -> currentRecipe == null ? 0 : overclockEnergy(currentRecipe.value().energyPerTick());
                // Après les réservoirs : la purge des deux sorties, puis le surcadençage.
                case 3 + TANK_COUNT * 2 -> purge[1].ordinal();
                case 4 + TANK_COUNT * 2 -> purge[2].ordinal();
                case 5 + TANK_COUNT * 2, 6 + TANK_COUNT * 2 -> overclockData(local - 5 - TANK_COUNT * 2);
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

    @Override
    public boolean supportsOverclock() {
        return true;
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
            RecipeHolder<FluidRecipe> found = level.getRecipeManager()
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
            if (canOutput(recipe) && energy.consume(overclockEnergy(recipe.energyPerTick()))) {
                working = true;
                progress += speed();
                if (progress >= recipe.time()) {
                    craft(recipe);
                    progress = 0;
                }
                setChanged();
            }
        }
        setLit(working);

        pushOutputs(level, pos);
        purge(level, pos);
    }

    public Purge getPurge(int tank) {
        return purge[tank];
    }

    /** Change la purge d'un réservoir de sortie : clic gauche, le mode suivant ; clic droit, l'arrêt. */
    public void setPurge(int tank, Purge mode) {
        if (tank > INPUT_TANK && tank <= layout.outputCount()) {
            purge[tank] = mode;
            setChanged();
        }
    }

    /** Rejette à l'air ce que les purges des sorties demandent, avec une bouffée de vapeur de temps en temps. */
    private void purge(ServerLevel level, BlockPos pos) {
        boolean vented = false;
        for (int tank = 1; tank <= layout.outputCount(); tank++) {
            FluidStack content = tanks.getFluid(tank);
            int keep = switch (purge[tank]) {
                case OFF -> content.getAmount();
                case HALF -> TANK_CAPACITY / 2;
                case FULL -> 0;
            };
            int vent = Math.min(PURGE_PER_TICK, content.getAmount() - keep);
            if (vent > 0) {
                int left = content.getAmount() - vent;
                tanks.setFluid(tank, left <= 0 ? FluidStack.EMPTY : new FluidStack(content.getFluid(), left));
                vented = true;
            }
        }
        if (vented && purgeTicks++ % PURGE_PUFF_INTERVAL == 0) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5,
                    3, 0.2, 0.05, 0.2, 0.02);
        }
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
        // Sur Mars, le carbone vient de l'air : ce que présente la machine à ses recettes tient lieu du charbon.
        ItemStack item = carbonFromAir() ? new ItemStack(net.minecraft.world.item.Items.CHARCOAL) : inventory.getStack(0);
        return new FluidRecipeInput(item, tanks.getFluid(INPUT_TANK));
    }

    /**
     * Vrai pour un réacteur de Sabatier posé sur Mars : il prend son carbone dans le gaz carbonique de l'air martien,
     * comme le feraient les usines de méthane d'une base martienne, au lieu de brûler du charbon. Le charbon qu'on y met
     * n'est pas consommé.
     */
    public boolean carbonFromAir() {
        return layout == FluidMachineLayout.SABATIER_REACTOR && level != null && com.orbitalis.phase2.survival.SpaceRules.isMars(level);
    }

    /** Vrai si chaque réservoir de sortie peut recevoir le fluide que la recette lui destine. */
    private boolean canOutput(FluidRecipe recipe) {
        List<FluidStackTemplate> outputs = recipe.fluidOutputs();
        for (int i = 0; i < outputs.size(); i++) {
            FluidStack content = tanks.getFluid(1 + i);
            FluidStackTemplate output = outputs.get(i);
            boolean sameFluid = content.isEmpty() || content.getFluid() == output.fluid();
            if (!sameFluid || content.getAmount() + output.amount() > TANK_CAPACITY) {
                return false;
            }
        }
        return true;
    }

    /** Consomme les entrées et ajoute les fluides produits dans les réservoirs de sortie. */
    private void craft(FluidRecipe recipe) {
        recipe.itemInput().filter(ingredient -> !carbonFromAir()).ifPresent(ingredient -> {
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
                com.orbitalis.platform.capability.Caps.get(level, Capabilities.Fluid.BLOCK, pos.relative(direction), direction.getOpposite());
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
        return new FluidMachineMenu(containerId, playerInventory, this, inventory, upgrades, data, layout);
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider registries$) {
        ValueOutput output = ValueOutput.of(tag$, registries$);
        super.saveAdditional(output.tag(), output.lookup());
        tanks.serialize(output.child("tanks"));
        output.putInt("progress", progress);
        output.putInt("purge_1", purge[1].ordinal());
        output.putInt("purge_2", purge[2].ordinal());
    }

    /** Cassée, la machine garde aussi ses fluides sur son objet. */
    @Override
    protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        tanks.storeIn(components);
    }

    @Override
    protected void applyImplicitComponents(net.minecraft.world.level.block.entity.BlockEntity.DataComponentInput components) {
        super.applyImplicitComponents(components);
        tanks.restoreFrom(components::get);
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider registries$) {
        ValueInput input = ValueInput.of(tag$, registries$);
        super.loadAdditional(input.tag(), input.lookup());
        tanks.deserialize(input.childOrEmpty("tanks"));
        progress = input.getIntOr("progress", 0);
        purge[1] = Purge.values()[Math.clamp(input.getIntOr("purge_1", 0), 0, Purge.values().length - 1)];
        purge[2] = Purge.values()[Math.clamp(input.getIntOr("purge_2", 0), 0, Purge.values().length - 1)];
        inputsChanged = true;
    }
}
