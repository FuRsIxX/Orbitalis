package com.orbitalis.phase1.spaceport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.phase1.rocket.RocketPartBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * État d'un pas de tir, relevé autour de sa table de lancement en le comparant à son plan ({@link PadPlan}).
 * <p>
 * Le pas de tir est une structure à assembler entièrement : il n'est formé que si tous les blocs de son plan sont en
 * place, et si le ciel est dégagé au-dessus de la table. Plus le tablier est grand, plus la fusée peut être lourde,
 * et plus la tour est haute, plus elle peut être grande.
 *
 * @param radius       taille du tablier : 1 pour 3×3, 2 pour 5×5 ... 5 pour 11×11 ; 0 s'il est incomplet ou sans plan
 * @param missingPads  dalles qu'il reste à poser
 * @param skyClear     vrai si rien ne se trouve au-dessus de la table
 * @param towerHeight  nombre d'étages complets de la tour, depuis le sol
 * @param arms         nombre de bras ombilicaux accrochés à la colonne technique
 * @param hasDeflector vrai si le déflecteur de flammes se trouve sous la table
 * @param hasTrench    vrai si le carneau relie la table au bord du tablier
 * @param mastHeight   hauteur des quatre mâts s'ils sont complets, 0 sinon
 * @param towerBase    position du pied de la colonne technique, ou {@code null} sans plan
 * @param armPositions positions des bras ombilicaux
 * @param missing      nombre total de blocs du plan qu'il reste à poser
 * @param compact      vrai pour le pas de tir d'essai, qui n'a ni tour, ni mâts, ni bras
 */
public record LaunchComplex(int radius, int missingPads, boolean skyClear, int towerHeight, int arms,
        boolean hasDeflector, boolean hasTrench, int mastHeight, BlockPos towerBase, List<BlockPos> armPositions, int missing,
        boolean compact) {
    /** Plus grand tablier reconnu (11×11). */
    public static final int MAX_RADIUS = 5;
    /** Hauteur maximale d'une tour, en blocs. */
    public static final int MAX_TOWER_HEIGHT = 96;
    /** Distance maximale entre la table et le bloc le plus éloigné de son plan, à l'horizontale. */
    public static final int REACH = MAX_RADIUS + 6;
    /** Hauteur maximale d'une fusée sur le pas de tir d'essai, qui n'a pas de tour pour la borner, en blocs. */
    public static final int TEST_PAD_HEIGHT = 8;

    /** Pas de tir sans plan : rien n'est attendu, rien n'est formé. */
    public static final LaunchComplex NONE = new LaunchComplex(0, 0, false, 0, 0, false, false, 0, null, List.of(), 0, false);

    /** Pas de tir entièrement équipé, de la taille donnée (sert aux écrans, qui ne connaissent que la taille). */
    public LaunchComplex(int radius, int missingPads, boolean skyClear, int towerHeight, int arms) {
        this(radius, missingPads, skyClear, towerHeight, arms, false);
    }

    /** Pas de tir entièrement équipé, à tour ou d'essai (sert aux écrans). */
    public LaunchComplex(int radius, int missingPads, boolean skyClear, int towerHeight, int arms, boolean compact) {
        this(radius, missingPads, skyClear, towerHeight, arms, true, true, compact ? 0 : requiredMastHeight(radius), null, List.of(), 0,
                compact);
    }

    /** Hauteur exigée de chacun des quatre mâts paratonnerres, en blocs. */
    public static int requiredMastHeight(int radius) {
        return radius <= 0 ? 0 : 2 + 2 * radius;
    }

    /** Nombre d'étages exigé de la tour de service. */
    public static int requiredTowerHeight(int radius) {
        return radius <= 0 ? 0 : PadPlan.towerLevels(radius);
    }

    /**
     * Hauteur maximale d'une fusée sous une tour d'un nombre d'étages donné. La tour part du sol, la fusée du
     * dessus de la table, deux blocs plus haut : elle doit rester sous le sommet de la tour.
     */
    public static int maxRocketHeight(int towerLevels) {
        return Math.max(0, towerLevels - 2);
    }

    /** Vrai si les mâts atteignent la hauteur exigée. Le pas de tir d'essai n'en demande pas. */
    public boolean hasMasts() {
        return radius > 0 && (compact || mastHeight >= requiredMastHeight(radius));
    }

    /** Vrai si la tour atteint la hauteur exigée. Le pas de tir d'essai n'en demande pas. */
    public boolean hasTower() {
        return radius > 0 && (compact || towerHeight >= requiredTowerHeight(radius));
    }

    /** Vrai si le déflecteur, le carneau, les mâts et la tour sont tous en place. */
    public boolean hasHeavyEquipment() {
        return hasDeflector && hasTrench && hasMasts() && hasTower();
    }

    /** Vrai si le pas de tir est entièrement assemblé : tous les blocs de son plan sont posés et le ciel est dégagé. */
    public boolean isComplete() {
        return radius > 0 && missing == 0 && skyClear && hasHeavyEquipment();
    }

    /** Taille prise en compte pour la masse des fusées : celle du tablier. */
    public int capacityRadius() {
        return radius;
    }

    /** Côté du tablier, en blocs (de 3 à 11), ou 0 s'il est incomplet. */
    public int padSize() {
        return radius == 0 ? 0 : 2 * radius + 1;
    }

    /** Masse maximale de la fusée que le pas de tir supporte, en kg. */
    public double maxRocketMass() {
        return maxRocketMass(radius);
    }

    /** Hauteur maximale de la fusée : elle doit rester sous le sommet de la tour, en blocs. */
    public int maxRocketHeight() {
        return compact ? TEST_PAD_HEIGHT : maxRocketHeight(towerHeight);
    }

    /** Hauteur de tour exigée sur ce pas de tir : aucune sur le pas de tir d'essai. */
    public int requiredTowerHeight() {
        return compact ? 0 : requiredTowerHeight(radius);
    }

    /** Masse maximale supportée par un pas de tir de cette taille, en kg. */
    public static double maxRocketMass(int radius) {
        return switch (radius) {
            case 1 -> 20_000;
            case 2 -> 200_000;
            case 3 -> 1_000_000;
            case 4 -> 3_000_000;
            case 5 -> 10_000_000;
            default -> 0;
        };
    }

    /**
     * Direction qui va de la tour vers la fusée : la tour est sur le pourtour du tablier, la fusée du côté de la
     * table selon l'axe le plus éloigné.
     */
    public static Direction towerFacing(BlockPos tower, BlockPos table) {
        int dx = tower.getX() - table.getX();
        int dz = tower.getZ() - table.getZ();
        return Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.WEST : Direction.EAST) : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
    }

    /** Vrai si le bloc est un tronçon de mât paratonnerre : un segment de fût, ou celui qui porte les projecteurs. */
    public static boolean isMast(BlockState state) {
        return state.is(ModBlocks.LIGHTNING_MAST.get()) || state.is(ModBlocks.PAD_FLOODLIGHT.get());
    }

    /** Vrai si le bloc fait partie d'une fusée : un tronçon de fusée assemblée, ou un bloc de construction. */
    private static boolean isRocket(BlockState state) {
        return state.is(ModBlocks.ROCKET.get()) || state.getBlock() instanceof RocketPartBlock;
    }

    /** Blocs du plan qui ne sont pas encore en place autour de la table, du bas vers le haut. */
    public static List<PadPlan.Piece> missingPieces(Level level, BlockPos table, PadPlan plan) {
        List<PadPlan.Piece> missing = new ArrayList<>();
        for (PadPlan.Piece piece : plan.pieces()) {
            if (!piece.accepts(level.getBlockState(table.offset(piece.offset())))) {
                missing.add(piece);
            }
        }
        return missing;
    }

    /** Ce qu'il reste à poser : le nombre de blocs de chaque sorte. */
    public static Map<Block, Integer> missingBlocks(Level level, BlockPos table, PadPlan plan) {
        Map<Block, Integer> counts = new LinkedHashMap<>();
        for (PadPlan.Piece piece : missingPieces(level, table, plan)) {
            if (piece.counted()) {
                counts.merge(piece.block(), 1, Integer::sum);
            }
        }
        return counts;
    }

    /** Relève l'état du pas de tir autour de la table de lancement située en {@code table}, d'après son plan. */
    public static LaunchComplex scan(Level level, BlockPos table, PadPlan plan) {
        if (plan == null) {
            return NONE;
        }
        int missing = 0;
        int missingPads = 0;
        boolean hasDeflector = true;
        boolean hasTrench = true;
        boolean hasMasts = true;
        // Étage le plus bas de la tour où il manque un bloc : en dessous, la tour est complète.
        int towerHeight = plan.towerLevels();
        for (PadPlan.Piece piece : missingPieces(level, table, plan)) {
            missing++;
            switch (piece.role()) {
                case DECK -> missingPads++;
                case DEFLECTOR -> hasDeflector = false;
                case TRENCH -> hasTrench = false;
                case MAST -> hasMasts = false;
                case TOWER -> towerHeight = Math.min(towerHeight, piece.level());
                default -> {
                }
            }
        }

        // Ciel dégagé : on regarde au-dessus de la fusée si la table en porte une, qu'elle soit assemblée ou encore
        // en construction. Sans cela, poser le premier bloc d'une fusée déferait le pas de tir.
        BlockPos aboveRocket = table.above();
        while (isRocket(level.getBlockState(aboveRocket))) {
            aboveRocket = aboveRocket.above();
        }
        boolean skyClear = level.canSeeSky(aboveRocket);

        // Le pas de tir d'essai n'a pas de tour : ni colonne technique, ni bras.
        BlockPos towerBase = plan.compact() ? null : table.offset(plan.riserBase()).immutable();
        List<BlockPos> armPositions = towerBase == null ? List.of() : findArms(level, towerBase, towerHeight);
        return new LaunchComplex(missingPads == 0 ? plan.radius() : 0, missingPads, skyClear, towerHeight, armPositions.size(), hasDeflector,
                hasTrench, hasMasts ? plan.mastHeight() : 0, towerBase, armPositions, missing, plan.compact());
    }

    /** Bras ombilicaux accrochés à la colonne technique d'une tour. */
    private static List<BlockPos> findArms(Level level, BlockPos base, int height) {
        List<BlockPos> arms = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos pos = base.above(y).relative(direction);
                if (level.getBlockState(pos).is(ModBlocks.UMBILICAL_ARM.get())) {
                    arms.add(pos.immutable());
                }
            }
        }
        return List.copyOf(arms);
    }
}
