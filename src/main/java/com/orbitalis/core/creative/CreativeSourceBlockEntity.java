package com.orbitalis.core.creative;

import com.orbitalis.core.registry.ModBlockEntities;
import com.orbitalis.phase1.spaceport.LaunchTableBlockEntity;
import com.orbitalis.core.registry.ModFluids;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Source créative : à chaque tick, elle remplit tout ce qui la touche, sans jamais se vider.
 * Elle n'expose elle-même ni énergie ni fluide : rien ne peut y être envoyé.
 */
public class CreativeSourceBlockEntity extends BlockEntity {
    /** Énergie proposée à chaque voisin et à chaque tick, en FE. */
    private static final int ENERGY_PER_TICK = 100_000;
    /** Fluide proposé à chaque voisin et à chaque tick, en mB. */
    private static final int FLUID_PER_TICK = 1_000;
    /** Distance à laquelle une source de fluide fait le plein d'une fusée, en blocs. */
    public static final double REFUEL_RANGE = 12.0;
    /** Débit de ce plein, par ergol et par tick, en mB. */
    private static final int REFUEL_PER_TICK = 100;

    public CreativeSourceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CREATIVE_SOURCE.get(), pos, state);
    }

    /**
     * Camion-citerne : posée à quelques blocs d'un pas de tir, la source de fluide charge dans la fusée tous les
     * ergols dont elle a besoin, quel que soit le fluide choisi et sans raccordement. Le remplissage suit les
     * mêmes règles qu'avec un parc à ergols : à débit limité, le kérosène d'abord.
     */
    private static void refuelNearbyRocket(ServerLevel level, BlockPos pos) {
        LaunchTableBlockEntity table = LaunchTableBlockEntity.nearest(level, pos, REFUEL_RANGE);
        if (table == null || !table.hasRocket()) {
            return;
        }
        table.fill(ModFluids.RP1.get(), REFUEL_PER_TICK, false);
        table.fill(ModFluids.LIQUID_OXYGEN.get(), REFUEL_PER_TICK, false);
        table.fill(ModFluids.LIQUID_HYDROGEN.get(), REFUEL_PER_TICK, false);
        table.fill(ModFluids.LIQUID_METHANE.get(), REFUEL_PER_TICK, false);
        table.fill(ModFluids.HELIUM_3.get(), REFUEL_PER_TICK, false);
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof CreativeSourceBlock source)) {
            return;
        }
        if (source.kind() == CreativeSourceBlock.Kind.FLUID) {
            refuelNearbyRocket(level, pos);
        }
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = pos.relative(direction);
            Direction side = direction.getOpposite();
            // Chaque voisin prend ce qu'il peut ; le reste n'est pas perdu puisque la source est sans fond.
            try (Transaction transaction = Transaction.openRoot()) {
                if (source.kind() == CreativeSourceBlock.Kind.ENERGY) {
                    EnergyHandler target = level.getCapability(Capabilities.Energy.BLOCK, neighbour, side);
                    if (target != null) {
                        target.insert(ENERGY_PER_TICK, transaction);
                    }
                } else {
                    ResourceHandler<FluidResource> target = level.getCapability(Capabilities.Fluid.BLOCK, neighbour, side);
                    if (target != null) {
                        target.insert(FluidResource.of(CreativeSourceBlock.fluidOf(state)), FLUID_PER_TICK, transaction);
                    }
                }
                transaction.commit();
            }
        }
    }
}
