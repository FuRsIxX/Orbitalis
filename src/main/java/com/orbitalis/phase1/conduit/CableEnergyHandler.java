package com.orbitalis.phase1.conduit;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Énergie vue depuis un câble : ce qu'on y envoie est partagé équitablement entre toutes les machines
 * raccordées au réseau. Le câble ne stocke rien et on ne peut rien en retirer.
 * Chaque transfert illumine le réseau.
 */
public class CableEnergyHandler implements EnergyHandler {
    /** Évite qu'un bloc desservi renvoie aussitôt l'énergie dans le même réseau. */
    private static final ThreadLocal<Boolean> DISTRIBUTING = ThreadLocal.withInitial(() -> false);

    private final Level level;
    private final BlockPos pos;
    private final Block cable;

    public CableEnergyHandler(Level level, BlockPos pos, Block cable) {
        this.level = level;
        this.pos = pos;
        this.cable = cable;
    }

    @Override
    public long getAmountAsLong() {
        return 0;
    }

    @Override
    public long getCapacityAsLong() {
        return 0;
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        if (amount <= 0 || DISTRIBUTING.get()) {
            return 0;
        }
        DISTRIBUTING.set(true);
        try {
            List<EnergyHandler> targets = new ArrayList<>();
            for (ConduitNetwork.Endpoint endpoint : ConduitNetwork.endpoints(level, pos, cable, Capabilities.Energy.BLOCK)) {
                EnergyHandler target = level.getCapability(Capabilities.Energy.BLOCK, endpoint.pos(), endpoint.side());
                if (target != null) {
                    targets.add(target);
                }
            }

            int remaining = amount;
            // Premier passage : chacun reçoit sa part de ce qui reste.
            for (int i = 0; i < targets.size() && remaining > 0; i++) {
                int share = (remaining + targets.size() - i - 1) / (targets.size() - i);
                remaining -= targets.get(i).insert(share, transaction);
            }
            // Second passage : ce que les machines pleines ont refusé va à celles qui ont encore de la place.
            for (int i = 0; i < targets.size() && remaining > 0; i++) {
                remaining -= targets.get(i).insert(remaining, transaction);
            }
            if (remaining < amount) {
                ConduitNetwork.markActive(level, pos, cable, Capabilities.Energy.BLOCK);
            }
            return amount - remaining;
        } finally {
            DISTRIBUTING.set(false);
        }
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        return 0;
    }
}
