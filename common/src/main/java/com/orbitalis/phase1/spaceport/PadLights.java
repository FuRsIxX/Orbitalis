package com.orbitalis.phase1.spaceport;

import java.util.ArrayList;
import java.util.List;

import com.orbitalis.core.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Éclairage du pas de tir : une vraie lumière, qui porte.
 * <p>
 * Un bloc du jeu n'éclaire qu'autour de lui. Un projecteur, lui, éclaire ce qu'il vise : son faisceau part dans sa
 * direction en descendant, et dépose de la lumière ({@link PadLightBlock}) le long de son trajet, jusqu'au premier
 * obstacle. Un projecteur perché sur un mât éclaire donc le pied de la fusée, à quinze blocs de là, et pas le ciel
 * autour de lui. Qu'on dresse un mur devant lui, et l'ombre tombe derrière le mur.
 * <p>
 * Les balises des mâts clignotent pour de bon : à chaque éclat, leur lumière monte, puis retombe en traîne.
 */
public final class PadLights {
    /** Portée d'un faisceau, en pas (un pas vaut un bloc tout droit, ou un bloc en diagonale). */
    public static final int RANGE = 16;

    private PadLights() {
    }

    /**
     * Cases que traverse le faisceau d'un projecteur, dans l'ordre : il avance d'un pas à chaque case et descend
     * d'un bloc tous les deux pas. En diagonale, il vise un huitième de tour à droite de son orientation.
     */
    private static List<BlockPos> path(BlockPos pos, Direction facing, boolean diagonal) {
        Direction side = facing.getClockWise();
        int dx = facing.getStepX() + (diagonal ? side.getStepX() : 0);
        int dz = facing.getStepZ() + (diagonal ? side.getStepZ() : 0);
        List<BlockPos> path = new ArrayList<>(RANGE);
        for (int step = 1; step <= RANGE; step++) {
            path.add(pos.offset(dx * step, -(step / 2), dz * step));
        }
        return path;
    }

    /** Allume le faisceau d'un projecteur : de la lumière une case sur deux, jusqu'au premier obstacle. */
    public static void cast(Level level, BlockPos pos, Direction facing, boolean diagonal) {
        List<BlockPos> path = path(pos, facing, diagonal);
        for (int i = 0; i < path.size(); i++) {
            BlockPos target = path.get(i);
            if (level.isOutsideBuildHeight(target) || !level.getBlockState(target).isAir()) {
                return;
            }
            if (i % 2 == 0) {
                put(level, target, 15);
            }
        }
    }

    /** Éteint le faisceau d'un projecteur : toute la lumière déposée sur son trajet est reprise. */
    public static void douse(Level level, BlockPos pos, Direction facing, boolean diagonal) {
        for (BlockPos target : path(pos, facing, diagonal)) {
            take(level, target);
        }
    }

    /** Règle la lumière d'une balise : sa force suit le courant de sa LED ({@link LedDriver}), de 0 (éteinte) à 15. */
    public static void beacon(Level level, BlockPos pos, int strength) {
        if (strength > 0) {
            put(level, pos, strength);
        } else {
            take(level, pos);
        }
    }

    /** Reprend toute la lumière déposée dans un volume : un pas de tir qui s'éteint ne laisse rien derrière lui. */
    public static void clear(Level level, BlockPos from, BlockPos to) {
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            take(level, pos);
        }
    }

    private static void put(Level level, BlockPos pos, int strength) {
        BlockState state = level.getBlockState(pos);
        BlockState light = ModBlocks.PAD_LIGHT.get().defaultBlockState().setValue(PadLightBlock.LEVEL, strength);
        if (state != light && state.isAir()) {
            level.setBlock(pos, light, Block.UPDATE_CLIENTS);
        }
    }

    private static void take(Level level, BlockPos pos) {
        if (level.getBlockState(pos).is(ModBlocks.PAD_LIGHT.get())) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
