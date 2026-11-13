package com.orbitalis.phase1.spaceport;

import java.util.ArrayList;
import java.util.List;

import com.orbitalis.core.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Grand poste de travail : l'ordinateur de conception, le pupitre de lancement, la station de poursuite. Tous trois
 * sont des meubles de trois blocs de large, deux de haut et deux de profondeur.
 * <p>
 * Le bloc du poste est celui du milieu, en bas, à l'avant : c'est lui qui dessine tout le meuble. Autour de lui,
 * des cases invisibles ({@link WorkstationPartBlock}) lui donnent sa solidité : le plan de travail (les deux cases
 * voisines, à l'avant) et le mur d'écrans (les six cases de la rangée arrière). Au-dessus du plan de travail,
 * l'air reste libre.
 */
public final class Workstation {
    /** Marque les blocs qui sont le cœur d'un poste de travail. Leur état doit porter l'orientation horizontale. */
    public interface Host {
        /** Cases que le meuble occupe en plus de la sienne. Par défaut, celles d'un grand poste de travail. */
        default List<BlockPos> wingsOf(BlockPos pos, Direction facing) {
            return wings(pos, facing);
        }
    }

    /** Cases d'un meuble posé, d'après son bloc. */
    private static List<BlockPos> wingsOf(BlockPos pos, BlockState state) {
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        return state.getBlock() instanceof Host host ? host.wingsOf(pos, facing) : wings(pos, facing);
    }

    /** Orientation d'un meuble posé par un joueur (il lui fait face), ou {@code null} si la place manque. */
    public static Direction placement(BlockPlaceContext context, Host host) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        for (BlockPos wing : host.wingsOf(context.getClickedPos(), facing)) {
            if (context.getLevel().isOutsideBuildHeight(wing) || !context.getLevel().getBlockState(wing).canBeReplaced()) {
                return null;
            }
        }
        return facing;
    }

    private Workstation() {
    }

    /** Cases que le poste occupe en plus de la sienne, pour des écrans tournés vers {@code facing}. */
    public static List<BlockPos> wings(BlockPos pos, Direction facing) {
        Direction side = facing.getClockWise();
        BlockPos rear = pos.relative(facing.getOpposite());
        List<BlockPos> wings = new ArrayList<>(8);
        wings.add(pos.relative(side));
        wings.add(pos.relative(side.getOpposite()));
        for (int up = 0; up <= 1; up++) {
            for (int across = -1; across <= 1; across++) {
                wings.add(rear.above(up).relative(side, across));
            }
        }
        return wings;
    }

    /** Orientation d'un poste posé par un joueur (les écrans lui font face), ou {@code null} si la place manque. */
    public static Direction placement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        for (BlockPos wing : wings(context.getClickedPos(), facing)) {
            if (context.getLevel().isOutsideBuildHeight(wing) || !context.getLevel().getBlockState(wing).canBeReplaced()) {
                return null;
            }
        }
        return facing;
    }

    /** Pose les cases invisibles d'un poste qui vient d'être placé. */
    public static void placeWings(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide()) {
            return;
        }
        for (BlockPos wing : wingsOf(pos, state)) {
            if (level.getBlockState(wing).canBeReplaced()) {
                level.setBlock(wing, ModBlocks.WORKSTATION_PART.get().defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /** Retire les cases invisibles d'un poste qui vient d'être cassé. */
    public static void removeWings(Level level, BlockPos pos, BlockState state) {
        for (BlockPos wing : wingsOf(pos, state)) {
            if (level.getBlockState(wing).is(ModBlocks.WORKSTATION_PART.get())) {
                level.setBlock(wing, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /** Le poste auquel appartient une case invisible, ou {@code null}. */
    public static BlockPos findHost(Level level, BlockPos wing) {
        for (BlockPos pos : BlockPos.betweenClosed(wing.offset(-1, -1, -1), wing.offset(1, 0, 1))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof Host && wingsOf(pos, state).contains(wing)) {
                return pos.immutable();
            }
        }
        return null;
    }
}
