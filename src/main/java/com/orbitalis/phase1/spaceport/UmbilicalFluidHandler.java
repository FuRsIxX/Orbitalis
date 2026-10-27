package com.orbitalis.phase1.spaceport;

import com.orbitalis.core.registry.ModBlocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Fluides vus depuis un bras ombilical : les ergols qu'on y envoie (par des tuyaux) sont chargés dans la fusée
 * posée sur la table de lancement voisine. Le bras n'accepte que les ergols dont la fusée a besoin,
 * et rien n'en ressort.
 */
public class UmbilicalFluidHandler extends SnapshotJournal<int[]> implements ResourceHandler<FluidResource> {
    /** Distance maximale entre un bras et la table qu'il dessert (le plus grand pas de tir fait 7×7). */
    private static final double RANGE = 8.0;

    private final Level level;
    private final BlockPos pos;
    /** Table concernée par la transaction en cours, pour pouvoir revenir en arrière si elle est annulée. */
    private LaunchTableBlockEntity journalTable;

    public UmbilicalFluidHandler(Level level, BlockPos pos) {
        this.level = level;
        this.pos = pos;
    }

    private LaunchTableBlockEntity table() {
        return LaunchTableBlockEntity.nearest(level, pos, RANGE);
    }

    /** Vrai si ce bloc de ravitaillement laisse passer ce fluide : chacun n'accepte que son ergol. */
    private boolean accepts(FluidResource resource) {
        BlockState state = level.getBlockState(pos);
        return state.is(ModBlocks.FUEL_PORT.get()) && state.getValue(FuelPortBlock.KIND).accepts(resource.getFluid());
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
        LaunchTableBlockEntity table = table();
        return table != null && accepts(resource) && table.fill(resource.getFluid(), 1, true) > 0;
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        LaunchTableBlockEntity table = table();
        if (table == null || resource.isEmpty() || amount <= 0 || !accepts(resource) || table.fillFromArm(resource.getFluid(), amount, true) <= 0) {
            return 0;
        }
        journalTable = table;
        updateSnapshots(transaction);
        return table.fillFromArm(resource.getFluid(), amount, false);
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return 0;
    }

    @Override
    protected int[] createSnapshot() {
        return journalTable == null ? new int[3] : journalTable.fuelSnapshot();
    }

    @Override
    protected void revertToSnapshot(int[] snapshot) {
        if (journalTable != null) {
            journalTable.restoreFuel(snapshot);
        }
    }
}
