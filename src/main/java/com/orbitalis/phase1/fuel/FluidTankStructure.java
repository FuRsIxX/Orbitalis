package com.orbitalis.phase1.fuel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.orbitalis.core.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Assemble et défait les réservoirs modulables.
 * <p>
 * Le principe tient en deux gestes. Défaire un réservoir, c'est rendre à chacun de ses blocs sa part du contenu :
 * chaque bloc redevient un petit réservoir à lui seul. Assembler, c'est regrouper des blocs voisins en pavés à base
 * carrée, aussi grands que possible, et y verser les parts de leurs blocs. Chaque fois qu'un bloc est posé ou
 * retiré, tous les réservoirs qui se touchent sont défaits puis assemblés à nouveau. Quand on retire un bloc, le
 * contenu reste dans les blocs qui restent : seul ce qui n'y tient plus est perdu.
 * <p>
 * Deux réservoirs qui contiennent des fluides différents ne fusionnent pas : ils restent côte à côte.
 */
public final class FluidTankStructure {
    /** Nombre maximal de blocs de réservoir qui se touchent et qu'on réassemble d'un coup. */
    private static final int MAX_BLOCKS = 4096;

    private FluidTankStructure() {
    }

    private static FluidTankBlockEntity tankAt(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(ModBlocks.FLUID_TANK.get()) && level.getBlockEntity(pos) instanceof FluidTankBlockEntity tank
                ? tank : null;
    }

    /** Défait le réservoir dont ce bloc fait partie : chacun de ses blocs reprend sa part du contenu. */
    public static void dissolve(ServerLevel level, FluidTankBlockEntity block) {
        FluidTankBlockEntity master = block.master();
        if (master == null) {
            // Le maître a disparu sans prévenir : ce bloc repart de zéro.
            block.becomeSingle(FluidStack.EMPTY);
            return;
        }
        // Un bloc en cours de retrait n'en fait déjà plus partie : le contenu se partage entre ceux qui restent.
        List<FluidTankBlockEntity> members = master.members();
        FluidStack content = master.fluid();
        if (members.isEmpty()) {
            return;
        }
        int share = content.getAmount() / members.size();
        int extra = content.getAmount() % members.size();
        for (int i = 0; i < members.size(); i++) {
            int amount = share + (i < extra ? 1 : 0);
            members.get(i).becomeSingle(amount <= 0 ? FluidStack.EMPTY : new FluidStack(content.getFluid(), amount));
        }
    }

    /** Un bloc de réservoir vient d'être retiré : les réservoirs qui le touchaient se réassemblent. */
    public static void afterRemoval(ServerLevel level, BlockPos removed) {
        Set<BlockPos> done = new HashSet<>();
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = removed.relative(direction);
            if (!done.contains(neighbour) && tankAt(level, neighbour) != null) {
                done.addAll(reform(level, neighbour));
            }
        }
    }

    /**
     * Réassemble tous les blocs de réservoir qui se touchent, à partir de celui-ci. Renvoie les blocs concernés.
     */
    public static Set<BlockPos> reform(ServerLevel level, BlockPos start) {
        Set<BlockPos> blocks = new HashSet<>();
        if (tankAt(level, start) == null) {
            return blocks;
        }
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        blocks.add(start.immutable());
        queue.add(start.immutable());
        while (!queue.isEmpty() && blocks.size() < MAX_BLOCKS) {
            BlockPos current = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos neighbour = current.relative(direction);
                if (!blocks.contains(neighbour) && tankAt(level, neighbour) != null) {
                    blocks.add(neighbour);
                    queue.add(neighbour);
                }
            }
        }

        // 1. Tout défaire : chaque bloc reprend sa part.
        for (BlockPos pos : blocks) {
            FluidTankBlockEntity tank = tankAt(level, pos);
            if (tank != null && (!tank.isMaster() || tank.width() > 1 || tank.height() > 1)) {
                dissolve(level, tank);
                // Un bloc que son maître ne reconnaît plus repart de zéro.
                if (!tank.isMaster()) {
                    tank.becomeSingle(FluidStack.EMPTY);
                }
            }
        }
        Map<BlockPos, FluidStack> shares = new HashMap<>();
        for (BlockPos pos : blocks) {
            FluidTankBlockEntity tank = tankAt(level, pos);
            shares.put(pos, tank == null ? FluidStack.EMPTY : tank.fluid());
        }

        // 2. Regrouper : du bas vers le haut, du nord-ouest vers le sud-est, le plus grand pavé possible à chaque fois.
        List<BlockPos> order = new ArrayList<>(blocks);
        order.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        Set<BlockPos> taken = new HashSet<>();
        Map<BlockPos, BlockPos> masters = new HashMap<>();
        for (BlockPos corner : order) {
            if (taken.contains(corner)) {
                continue;
            }
            Fluid[] fluid = {Fluids.EMPTY};
            int width = FluidTankBlockEntity.MAX_WIDTH;
            while (width > 1 && !layerFits(corner, width, blocks, taken, shares, fluid, false)) {
                width--;
            }
            layerFits(corner, width, blocks, taken, shares, fluid, true);
            int height = 1;
            while (height < FluidTankBlockEntity.MAX_HEIGHT && layerFits(corner.above(height), width, blocks, taken, shares, fluid, true)) {
                height++;
            }
            int total = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    for (int z = 0; z < width; z++) {
                        BlockPos pos = corner.offset(x, y, z);
                        taken.add(pos);
                        masters.put(pos, corner);
                        total += shares.get(pos).getAmount();
                    }
                }
            }
            // 3. Le maître reçoit tout, les autres le suivent.
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    for (int z = 0; z < width; z++) {
                        BlockPos pos = corner.offset(x, y, z);
                        FluidTankBlockEntity tank = tankAt(level, pos);
                        if (tank == null) {
                            continue;
                        }
                        if (pos.equals(corner)) {
                            tank.becomeMaster(width, height, total <= 0 ? FluidStack.EMPTY : new FluidStack(fluid[0], total));
                        } else {
                            tank.follow(corner);
                        }
                    }
                }
            }
        }

        // Chaque bloc montre de quels côtés le réservoir continue : c'est ce qui dessine ses parois.
        for (BlockPos pos : blocks) {
            BlockState state = level.getBlockState(pos);
            BlockState updated = state;
            for (Direction direction : Direction.values()) {
                boolean joined = masters.get(pos).equals(masters.get(pos.relative(direction)));
                updated = updated.setValue(FluidTankBlock.JOINED.get(direction), joined);
            }
            if (updated != state) {
                level.setBlock(pos, updated, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
        return blocks;
    }

    /**
     * Vrai si une couche carrée de ce côté, à partir de ce coin, peut rejoindre le réservoir en cours : tous ses
     * blocs sont des blocs de réservoir encore libres, et aucun ne contient un autre fluide que le sien.
     *
     * @param fluid  le fluide du réservoir en cours ; il est fixé par le premier bloc qui en contient
     * @param commit vrai pour retenir ce fluide si la couche convient
     */
    private static boolean layerFits(BlockPos corner, int width, Set<BlockPos> blocks, Set<BlockPos> taken,
            Map<BlockPos, FluidStack> shares, Fluid[] fluid, boolean commit) {
        Fluid current = fluid[0];
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < width; z++) {
                BlockPos pos = corner.offset(x, 0, z);
                if (!blocks.contains(pos) || taken.contains(pos)) {
                    return false;
                }
                FluidStack share = shares.get(pos);
                if (!share.isEmpty()) {
                    if (current != Fluids.EMPTY && current != share.getFluid()) {
                        return false;
                    }
                    current = share.getFluid();
                }
            }
        }
        if (commit) {
            fluid[0] = current;
        }
        return true;
    }
}
