package com.orbitalis.phase3.propulsion;

import java.util.ArrayList;
import java.util.List;

import com.orbitalis.core.energy.MachineEnergyStorage;
import com.orbitalis.core.registry.ModBlockEntities;
import com.orbitalis.core.registry.ModFluids;
import com.orbitalis.phase1.fuel.MachineTanks;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import com.orbitalis.platform.nbt.ValueInput;
import com.orbitalis.platform.nbt.ValueOutput;
import com.orbitalis.platform.capability.Capabilities;
import com.orbitalis.platform.fluid.FluidStack;
import com.orbitalis.platform.transfer.energy.EnergyHandler;
import com.orbitalis.platform.transfer.energy.EnergyHandlerUtil;

/**
 * Le réacteur à fusion au travail : un tore d'aimants tient un plasma de deutérium (tiré de l'hydrogène) et d'hélium-3,
 * qui fusionnent en ne rendant presque pas de neutrons. Il produit beaucoup d'électricité, qu'il envoie aux câbles et
 * aux machines qui le touchent, tant qu'il a ses deux gaz et que sa réserve n'est pas pleine.
 * <p>
 * L'hélium-3 (réservoir 0) et l'hydrogène (réservoir 1) arrivent par les tuyaux, par n'importe quelle face.
 */
public class FusionReactorBlockEntity extends BlockEntity {
    /** Production, en FE par tick, tant que le plasma brûle. */
    public static final int OUTPUT = 1200;
    /** Ce qu'il brûle par seconde, en mB : un peu d'hélium-3, dix fois plus d'hydrogène. */
    public static final int HELIUM3_PER_SECOND = 2;
    public static final int HYDROGEN_PER_SECOND = 20;
    private static final int TANK_CAPACITY = 8000;
    private static final int ENERGY_CAPACITY = 1_000_000;
    private static final int ENERGY_TRANSFER = 4000;
    static final int HELIUM3 = 0;
    static final int HYDROGEN = 1;

    private final MachineEnergyStorage energy = new MachineEnergyStorage(ENERGY_CAPACITY, 0, ENERGY_TRANSFER, this::setChanged);
    private final MachineTanks tanks = new MachineTanks(2, TANK_CAPACITY,
            (tank, fluid) -> tank == HELIUM3 ? fluid == ModFluids.HELIUM_3.get() : fluid == ModFluids.HYDROGEN.get(),
            tank -> false, this::setChanged);
    private int ticks;
    private boolean burning;

    public FusionReactorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FUSION_REACTOR.get(), pos, state);
    }

    public MachineEnergyStorage getEnergy() {
        return energy;
    }

    public MachineTanks getTanks() {
        return tanks;
    }

    public boolean isBurning() {
        return burning;
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        // Chaque seconde, le réacteur brûle sa part de gaz, s'il en a et si son électricité trouve preneur.
        if (ticks++ % 20 == 0) {
            FluidStack helium = tanks.getFluid(HELIUM3);
            FluidStack hydrogen = tanks.getFluid(HYDROGEN);
            burning = !energy.isFull() && helium.getAmount() >= HELIUM3_PER_SECOND && hydrogen.getAmount() >= HYDROGEN_PER_SECOND;
            if (burning) {
                tanks.setFluid(HELIUM3, shrink(helium, HELIUM3_PER_SECOND));
                tanks.setFluid(HYDROGEN, shrink(hydrogen, HYDROGEN_PER_SECOND));
            }
            if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT) != burning) {
                level.setBlock(pos, state.setValue(BlockStateProperties.LIT, burning), 3);
            }
        }
        if (burning) {
            energy.generate(OUTPUT);
        }
        distributeEnergy(level, pos);
    }

    private static FluidStack shrink(FluidStack stack, int amount) {
        int left = stack.getAmount() - amount;
        return left <= 0 ? FluidStack.EMPTY : new FluidStack(stack.getFluid(), left);
    }

    /** Partage l'électricité entre les voisins qui en prennent, comme le générateur à combustion. */
    private void distributeEnergy(ServerLevel level, BlockPos pos) {
        if (energy.getEnergy() <= 0) {
            return;
        }
        List<EnergyHandler> neighbors = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            EnergyHandler neighbor = com.orbitalis.platform.capability.Caps.get(level, Capabilities.Energy.BLOCK, pos.relative(direction), direction.getOpposite());
            if (neighbor != null) {
                neighbors.add(neighbor);
            }
        }
        for (int i = 0; i < neighbors.size(); i++) {
            int share = Mth.positiveCeilDiv(energy.getEnergy(), neighbors.size() - i);
            EnergyHandlerUtil.move(energy, neighbors.get(i), Math.min(share, ENERGY_TRANSFER), null);
        }
    }

    /** Ce qu'en dit un clic droit : s'il brûle, sa production, et ses deux gaz. */
    public Component status() {
        FluidStack helium = tanks.getFluid(HELIUM3);
        FluidStack hydrogen = tanks.getFluid(HYDROGEN);
        String key = burning ? "propulsion.orbitalis.fusion_reactor.burning"
                : helium.getAmount() < HELIUM3_PER_SECOND || hydrogen.getAmount() < HYDROGEN_PER_SECOND ? "propulsion.orbitalis.fusion_reactor.no_fuel"
                : "propulsion.orbitalis.fusion_reactor.full";
        return Component.translatable(key, OUTPUT, helium.getAmount(), hydrogen.getAmount(), energy.getEnergy() / 1000)
                .withStyle(burning ? ChatFormatting.AQUA : ChatFormatting.GOLD);
    }

    /** Cassée, la machine garde son énergie et ses fluides sur son objet ; reposée, elle les retrouve. */
    @Override
    protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        energy.storeIn(components);
        tanks.storeIn(components);
    }

    @Override
    protected void applyImplicitComponents(net.minecraft.world.level.block.entity.BlockEntity.DataComponentInput components) {
        super.applyImplicitComponents(components);
        energy.restoreFrom(components::get);
        tanks.restoreFrom(components::get);
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider registries$) {
        ValueOutput output = ValueOutput.of(tag$, registries$);
        super.saveAdditional(output.tag(), output.lookup());
        energy.serialize(output.child("energy"));
        tanks.serialize(output.child("tanks"));
        output.putBoolean("burning", burning);
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider registries$) {
        ValueInput input = ValueInput.of(tag$, registries$);
        super.loadAdditional(input.tag(), input.lookup());
        energy.deserialize(input.childOrEmpty("energy"));
        tanks.deserialize(input.childOrEmpty("tanks"));
        burning = input.getBooleanOr("burning", false);
    }
}
