package com.orbitalis.phase3.exploration;

import com.orbitalis.core.registry.ModBlockEntities;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.registry.ModFluids;
import com.orbitalis.phase2.survival.SpaceRules;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Une machine ISRU au travail : toutes les secondes, elle mesure ce qu'elle peut produire là où elle est (la glace
 * à sa portée pour la foreuse, l'atmosphère de Mars pour l'extracteur d'oxygène), remplit sa petite réserve, et la
 * pousse dans les réservoirs et les tuyaux qui la touchent.
 */
public class IsruBlockEntity extends BlockEntity {
    /** Réserve interne, en mB. */
    public static final int CAPACITY = 4000;
    /** Débit de la foreuse par bloc de glace à sa portée, en mB par tick, et au plus. */
    private static final float ICE_RATE = 0.5F;
    private static final int ICE_MAX = 5;
    /** Débit de l'extracteur d'oxygène, en mB par tick. */
    private static final int OXYGEN_RATE = 2;
    /** Ce qu'elle cède à un voisin, au plus, par tick. */
    private static final int TRANSFER = 200;

    private int stored;
    /** Débit mesuré à la dernière seconde, en mB par tick. */
    private float rate;
    /** Blocs de glace à portée de la foreuse, à la dernière mesure. */
    private int ice;
    private float fraction;

    public IsruBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ISRU.get(), pos, state);
    }

    private IsruBlock.Kind kind() {
        return getBlockState().getBlock() instanceof IsruBlock block ? block.kind() : IsruBlock.Kind.ICE_DRILL;
    }

    /** Le fluide que produit cette machine : de l'eau (du méthane liquide, sur Titan), ou de l'oxygène. */
    public Fluid output() {
        if (kind() != IsruBlock.Kind.ICE_DRILL) {
            return ModFluids.OXYGEN.source().get();
        }
        return level != null && SpaceRules.isTitan(level) ? ModFluids.LIQUID_METHANE.source().get() : Fluids.WATER;
    }

    public void serverTick(ServerLevel level, BlockPos pos) {
        if (level.getGameTime() % 20 == 0) {
            measure(level, pos);
        }
        fraction += rate;
        int made = (int) fraction;
        fraction -= made;
        stored = Math.min(CAPACITY, stored + made);
        if (stored > 0) {
            push(level, pos);
        }
    }

    /** Ce qu'elle peut produire, là où elle est. */
    private void measure(ServerLevel level, BlockPos pos) {
        ice = 0;
        if (kind() == IsruBlock.Kind.ICE_DRILL) {
            if (SpaceRules.isTitan(level)) {
                // Sur Titan, la foreuse posée au bord d'un lac en pompe le méthane : chaque bloc de lac à sa portée compte.
                for (BlockPos probe : BlockPos.betweenClosed(pos.offset(-2, -4, -2), pos.offset(2, 0, 2))) {
                    net.minecraft.world.level.material.FluidState fluid = level.getFluidState(probe);
                    if (fluid.isSource() && fluid.getType().isSame(ModFluids.LIQUID_METHANE.get())) {
                        ice++;
                    }
                }
            } else if (SpaceRules.isMoon(level) || SpaceRules.isMars(level)) {
                for (BlockPos probe : BlockPos.betweenClosed(pos.offset(-2, -4, -2), pos.offset(2, -1, 2))) {
                    BlockState state = level.getBlockState(probe);
                    if (state.is(ModBlocks.LUNAR_ICE.get()) || state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)) {
                        ice++;
                    }
                }
            }
            rate = Math.min(ICE_MAX, ice * ICE_RATE);
        } else {
            rate = SpaceRules.isMars(level) ? OXYGEN_RATE : 0;
        }
        setChanged();
    }

    /** Pousse sa réserve dans les réservoirs et les tuyaux qui la touchent. */
    private void push(ServerLevel level, BlockPos pos) {
        FluidResource resource = FluidResource.of(output());
        for (Direction direction : Direction.values()) {
            if (stored <= 0) {
                return;
            }
            ResourceHandler<FluidResource> target = level.getCapability(Capabilities.Fluid.BLOCK, pos.relative(direction), direction.getOpposite());
            if (target == null) {
                continue;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                int moved = target.insert(resource, Math.min(stored, TRANSFER), transaction);
                transaction.commit();
                stored -= moved;
            }
        }
    }

    /** Ce qu'en dit un clic droit : ce qu'elle produit, à quel débit, et pourquoi si elle ne produit rien. */
    public Component status(ServerLevel level) {
        measure(level, worldPosition);
        boolean drill = kind() == IsruBlock.Kind.ICE_DRILL;
        String name = drill ? "exploration.orbitalis.isru.drill" : "exploration.orbitalis.isru.oxygen";
        if (rate <= 0) {
            String why = drill ? (SpaceRules.isTitan(level) ? "exploration.orbitalis.isru.no_lake"
                    : SpaceRules.isMoon(level) || SpaceRules.isMars(level) ? "exploration.orbitalis.isru.no_ice" : "exploration.orbitalis.isru.no_world")
                    : "exploration.orbitalis.isru.no_mars";
            return Component.translatable(why, Component.translatable(name)).withStyle(ChatFormatting.GOLD);
        }
        Component detail = !drill ? Component.translatable("exploration.orbitalis.isru.air")
                : SpaceRules.isTitan(level) ? Component.translatable("exploration.orbitalis.isru.lake", ice)
                : Component.translatable("exploration.orbitalis.isru.ice", ice);
        return Component.translatable("exploration.orbitalis.isru.working", Component.translatable(name), Math.round(rate * 20), detail, stored)
                .withStyle(ChatFormatting.AQUA);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("stored", stored);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        stored = input.getIntOr("stored", 0);
    }
}
