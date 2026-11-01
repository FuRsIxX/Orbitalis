package com.orbitalis.phase1.quarry;

import java.util.ArrayList;
import java.util.List;

import com.orbitalis.core.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Plan de la tour de forage, autour de son bloc de commande, qui est au milieu de la face avant du socle et regarde la
 * zone qu'il fore :
 * <ul>
 * <li>un socle de 3×3 : le bloc de commande et huit blocs de socle, par où arrivent l'électricité et l'eau ;</li>
 * <li>quatre colonnes de mâts aux angles du socle, de deux à huit étages : une tour de trois à neuf blocs de haut.</li>
 * </ul>
 * Plus la tour est haute, plus la foreuse va loin : la plus petite (3×3×3) fore jusqu'à 9×9 sur 32 blocs de profondeur,
 * la plus grande (3×3×9) jusqu'à 33×33, jusqu'au fond du monde. Tant qu'elle n'a pas ses deux premiers étages, les blocs
 * qui manquent apparaissent en fantôme.
 */
public final class DerrickPlan {
    /** Une case du plan : sa place par rapport au bloc de commande, et le bloc attendu. */
    public record Piece(BlockPos offset, Block block) {
    }

    /** Étages de mâts : au moins deux pour forer, huit au plus. */
    public static final int MIN_LEVELS = 2;
    public static final int MAX_LEVELS = 8;
    /** Profondeur forée selon le nombre d'étages (de 2 à 8) ; 0 : jusqu'au fond du monde. */
    private static final int[] DEPTH = {32, 48, 64, 96, 128, 192, 0};
    /** Écart entre le socle et le bord de la zone forée : une case reste libre devant le bloc de commande. */
    public static final int GAP = 2;

    private DerrickPlan() {
    }

    /** Côté le plus grand du carré foré, pour une tour de ce nombre d'étages. */
    public static int maxSide(int levels) {
        return levels < MIN_LEVELS ? 0 : 4 * levels + 1;
    }

    /** Profondeur forée, en blocs, pour une tour de ce nombre d'étages ; 0 : jusqu'au fond du monde. */
    public static int depth(int levels) {
        return levels < MIN_LEVELS ? 0 : DEPTH[Math.min(levels, MAX_LEVELS) - MIN_LEVELS];
    }

    /** Les huit cases du socle, autour du bloc de commande (lui-même au milieu de la face avant). */
    public static List<BlockPos> base(Direction facing) {
        Direction back = facing.getOpposite();
        Direction right = facing.getClockWise();
        List<BlockPos> cells = new ArrayList<>(8);
        for (int row = 0; row <= 2; row++) {
            for (int side = -1; side <= 1; side++) {
                if (row != 0 || side != 0) {
                    cells.add(BlockPos.ZERO.relative(back, row).relative(right, side));
                }
            }
        }
        return cells;
    }

    /** Les quatre angles du socle, au pied desquels montent les colonnes de mâts. */
    public static List<BlockPos> corners(Direction facing) {
        Direction back = facing.getOpposite();
        Direction right = facing.getClockWise();
        List<BlockPos> corners = new ArrayList<>(4);
        for (int row = 0; row <= 2; row += 2) {
            for (int side = -1; side <= 1; side += 2) {
                corners.add(BlockPos.ZERO.relative(back, row).relative(right, side));
            }
        }
        return corners;
    }

    /** Vrai si la case appartient à l'emprise de la tour (son socle de 3×3), qu'on ne fore jamais. */
    public static boolean inFootprint(BlockPos origin, Direction facing, int x, int z) {
        BlockPos local = new BlockPos(x - origin.getX(), 0, z - origin.getZ());
        if (local.equals(BlockPos.ZERO)) {
            return true;
        }
        return base(facing).contains(local);
    }

    /** Étages complets, du premier vers le haut : un étage compte si ses quatre mâts sont posés. */
    public static int levels(Level level, BlockPos origin, Direction facing) {
        int levels = 0;
        for (int y = 1; y <= MAX_LEVELS; y++) {
            for (BlockPos corner : corners(facing)) {
                if (!level.getBlockState(origin.offset(corner).above(y)).is(ModBlocks.DRILL_MAST.get())) {
                    return levels;
                }
            }
            levels = y;
        }
        return levels;
    }

    /** Ce qui manque pour que la tour fore : le socle, puis les deux premiers étages de mâts. */
    public static List<Piece> missing(Level level, BlockPos origin, Direction facing) {
        List<Piece> missing = new ArrayList<>();
        for (BlockPos cell : base(facing)) {
            if (!level.getBlockState(origin.offset(cell)).is(ModBlocks.DRILL_BASE.get())) {
                missing.add(new Piece(cell, ModBlocks.DRILL_BASE.get()));
            }
        }
        for (int y = 1; y <= MIN_LEVELS; y++) {
            for (BlockPos corner : corners(facing)) {
                BlockPos cell = corner.above(y);
                if (!level.getBlockState(origin.offset(cell)).is(ModBlocks.DRILL_MAST.get())) {
                    missing.add(new Piece(cell, ModBlocks.DRILL_MAST.get()));
                }
            }
        }
        return missing;
    }

    /** Tout le plan d'une tour de ce nombre d'étages (pour les bâtisseurs et les tests). */
    public static List<Piece> pieces(Direction facing, int levels) {
        List<Piece> pieces = new ArrayList<>();
        for (BlockPos cell : base(facing)) {
            pieces.add(new Piece(cell, ModBlocks.DRILL_BASE.get()));
        }
        for (int y = 1; y <= levels; y++) {
            for (BlockPos corner : corners(facing)) {
                pieces.add(new Piece(corner.above(y), ModBlocks.DRILL_MAST.get()));
            }
        }
        return pieces;
    }
}
