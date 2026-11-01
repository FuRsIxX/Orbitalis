package com.orbitalis.phase1.quarry;

import java.util.ArrayList;
import java.util.List;

import com.orbitalis.core.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Plan de la tour du derrick, autour de son bloc de commande : quatre jambes de charpente aux angles, hautes de cinq
 * blocs, et une couronne de platelage par-dessus. Les quatre côtés du bloc de commande restent libres : câbles et
 * tuyaux s'y branchent. Comme le pas de tir, le derrick montre en fantôme ce qui manque, et ne fore qu'une fois complet.
 */
public final class DerrickPlan {
    /** Une case du plan : sa place par rapport au bloc de commande, et le bloc attendu. */
    public record Piece(BlockPos offset, Block block) {
    }

    public static final int LEG_HEIGHT = 5;

    private DerrickPlan() {
    }

    public static List<Piece> pieces() {
        List<Piece> pieces = new ArrayList<>();
        for (int y = 0; y < LEG_HEIGHT; y++) {
            for (int x = -1; x <= 1; x += 2) {
                for (int z = -1; z <= 1; z += 2) {
                    pieces.add(new Piece(new BlockPos(x, y, z), ModBlocks.TOWER_FRAME.get()));
                }
            }
        }
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                pieces.add(new Piece(new BlockPos(x, LEG_HEIGHT, z), ModBlocks.TOWER_DECK.get()));
            }
        }
        return pieces;
    }

    /** Les cases du plan qui manquent encore autour de ce bloc de commande. */
    public static List<Piece> missing(Level level, BlockPos origin) {
        List<Piece> missing = new ArrayList<>();
        for (Piece piece : pieces()) {
            BlockState state = level.getBlockState(origin.offset(piece.offset()));
            if (!state.is(piece.block())) {
                missing.add(piece);
            }
        }
        return missing;
    }
}
