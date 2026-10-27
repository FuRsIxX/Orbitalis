package com.orbitalis.phase1.conduit;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Fluide vu depuis un tuyau : ce qu'on y envoie est livré aux machines et réservoirs raccordés au réseau,
 * en remplissant le premier qui accepte ce fluide avant de passer au suivant.
 * Le tuyau ne stocke rien et on ne peut rien en retirer. Chaque transfert illumine le réseau.
 */
public class PipeFluidHandler implements ResourceHandler<FluidResource> {
    /** Évite qu'un bloc desservi renvoie aussitôt le fluide dans le même réseau. */
    private static final ThreadLocal<Boolean> DISTRIBUTING = ThreadLocal.withInitial(() -> false);

    private final Level level;
    private final BlockPos pos;
    private final Block pipe;

    public PipeFluidHandler(Level level, BlockPos pos, Block pipe) {
        this.level = level;
        this.pos = pos;
        this.pipe = pipe;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public FluidResource getResource(int index) {
        return FluidResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        return 0;
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return accepts(resource);
    }

    /** Vrai si ce tuyau transporte ce fluide : les gaz dans les tuyaux à gaz, les liquides dans les tuyaux à liquides. */
    private boolean accepts(FluidResource resource) {
        return !(pipe instanceof ConduitBlock conduit) || conduit.accepts(resource.getFluid());
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        if (amount <= 0 || resource.isEmpty() || !accepts(resource) || DISTRIBUTING.get()) {
            return 0;
        }
        DISTRIBUTING.set(true);
        try {
            int remaining = amount;
            for (ConduitNetwork.Endpoint endpoint : ConduitNetwork.endpoints(level, pos, pipe, Capabilities.Fluid.BLOCK)) {
                if (remaining <= 0) {
                    break;
                }
                // Un tuyau en extraction ne livre rien aux blocs qu'il touche : il ne fait que les vider.
                if (ConduitBlock.isPulling(level, endpoint.conduit())) {
                    continue;
                }
                ResourceHandler<FluidResource> target = level.getCapability(Capabilities.Fluid.BLOCK, endpoint.pos(), endpoint.side());
                if (target != null) {
                    remaining -= target.insert(resource, remaining, transaction);
                }
            }
            if (remaining < amount) {
                ConduitNetwork.markActive(level, pos, pipe, Capabilities.Fluid.BLOCK,
                        pipe instanceof ConduitBlock conduit ? conduit.contentOf(resource.getFluid()) : 0);
            }
            return amount - remaining;
        } finally {
            DISTRIBUTING.set(false);
        }
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return 0;
    }
}
