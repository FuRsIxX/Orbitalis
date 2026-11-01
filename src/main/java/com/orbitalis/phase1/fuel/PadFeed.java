package com.orbitalis.phase1.fuel;

import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.phase1.conduit.ConduitBlock;
import com.orbitalis.phase1.conduit.ConduitNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * Ravitaillement du pas de tir par un réservoir : il envoie son contenu aux blocs de ravitaillement qu'il touche, ou
 * qu'un réseau de tuyaux relie à lui (tuyau à liquides pour les ergols liquides, tuyau à gaz pour l'hélium-3). Un seul
 * tuyau jusqu'au pied de la tour suffit pour tout un parc de réservoirs, quels que soient leurs ergols. Il ne se vide
 * jamais dans une autre machine ni dans un autre réservoir : le bloc de ravitaillement ne prend que ce dont la fusée a
 * besoin.
 */
public final class PadFeed {
    private PadFeed() {
    }

    /**
     * Envoie ce fluide, depuis le bloc de réservoir situé en {@code pos}, aux prises du pas de tir qu'il touche ou que
     * ses tuyaux atteignent.
     *
     * @return la quantité envoyée, en mB
     */
    public static int feed(ServerLevel level, BlockPos pos, ResourceHandler<FluidResource> source, Fluid fluid, int rate) {
        int sent = 0;
        for (Direction direction : Direction.values()) {
            if (sent >= rate) {
                break;
            }
            BlockPos neighbour = pos.relative(direction);
            BlockState neighbourState = level.getBlockState(neighbour);
            if (isInlet(neighbourState)) {
                sent += move(level, neighbour, direction.getOpposite(), source, fluid, rate - sent);
            } else if (neighbourState.getBlock() instanceof ConduitBlock.Pipe pipe && pipe.accepts(fluid)) {
                int through = 0;
                for (ConduitNetwork.Endpoint endpoint : ConduitNetwork.endpoints(level, neighbour, pipe, Capabilities.Fluid.BLOCK)) {
                    if (sent + through < rate && isInlet(level.getBlockState(endpoint.pos()))) {
                        through += move(level, endpoint.pos(), endpoint.side(), source, fluid, rate - sent - through);
                    }
                }
                if (through > 0) {
                    // Les tuyaux traversés s'illuminent, comme pour tout transfert.
                    ConduitNetwork.markActive(level, neighbour, pipe, Capabilities.Fluid.BLOCK, pipe.contentOf(fluid));
                }
                sent += through;
            }
        }
        return sent;
    }

    /** Vrai si le bloc est une prise d'ergols du pas de tir : un bloc de ravitaillement, au pied de la tour. */
    public static boolean isInlet(BlockState state) {
        return state.is(ModBlocks.FUEL_PORT.get());
    }

    private static int move(ServerLevel level, BlockPos inlet, Direction side, ResourceHandler<FluidResource> source, Fluid fluid, int amount) {
        ResourceHandler<FluidResource> target = level.getCapability(Capabilities.Fluid.BLOCK, inlet, side);
        return target == null || amount <= 0 ? 0 : ResourceHandlerUtil.move(source, target, resource -> resource.getFluid() == fluid, amount, null);
    }
}
