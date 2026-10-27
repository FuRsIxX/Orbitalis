package com.orbitalis.phase1.spaceport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.orbitalis.core.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Plan d'un pas de tir : la liste de tous les blocs à poser, un à un, autour de la table de lancement.
 * <p>
 * Le plan est choisi dans le kit de pas de tir : sa taille (de 3×3 à 11×11), sa tour (avec escalier, ou avec
 * ascenseur) et son orientation. La table le garde en mémoire, montre en jeu les blocs qui manquent, et ne forme le
 * pas de tir que lorsque tous sont en place ; si l'un d'eux est cassé, le plan réapparaît.
 * <p>
 * Tout est repéré depuis la table. Le tablier est un bloc au-dessus du sol : la table et les dalles sont au niveau 0,
 * le déflecteur et les appuis au niveau -1 (posés sur le sol), et la tour part du sol.
 * <pre>
 *   vue de dessus, carneau vers la droite          un étage de la tour (5×5), la fusée à droite
 *
 *   T T T T T  M . . . M                            C C C C C        C : charpente
 *   T . . . T  . . . . .                            C x x x C        R : colonne technique (ou portique)
 *   T . . . R  . . F = =  → carneau                 C x x x R        x : escalier et paliers, ou gaine de
 *   T . . . T  . . . . .                            C x x x C            l'ascenseur
 *   T T T T T  M . . . M                            C C C C C
 * </pre>
 *
 * @param radius   taille du tablier : 1 pour 3×3 ... 5 pour 11×11
 * @param elevator vrai pour une tour à ascenseur, faux pour une tour à escalier
 * @param facing   sens du carneau ; la tour est du côté opposé
 * @param gate     étage du seuil du portique d'embarquement. La table de lancement le déplace d'elle-même à la
 *                 hauteur de la cabine de la fusée qu'on lui confie
 * @param arm      étage du bras ombilical. La table le déplace de même à la hauteur des réservoirs de la fusée
 */
public record PadPlan(int radius, boolean elevator, Direction facing, int gate, int arm) {
    /** Étage du bras ombilical tant qu'aucune fusée ne dit où le mettre. */
    private static final int ARM_LEVEL = 4;

    /** Nombre d'étages de la tour pour chaque taille de tablier, de 3×3 à 11×11. Toujours pair : le sommet est un palier. */
    private static final int[] TOWER_LEVELS = {10, 26, 32, 38, 46};
    /** Demi-largeur de la tour (5×5), et sa profondeur. */
    private static final int TOWER_HALF = 2;
    private static final int TOWER_DEPTH = 5;

    /** Rôle d'un bloc dans la structure. */
    public enum Role {
        DEFLECTOR,
        DECK,
        TRENCH,
        SUPPORT,
        MAST,
        DELUGE,
        TOWER,
        ROOF,
        ARM,
        ACCESS
    }

    /**
     * Un bloc du plan.
     *
     * @param offset      position par rapport à la table
     * @param role        son rôle dans la structure
     * @param block       le bloc attendu
     * @param alternative un autre bloc accepté à sa place (portique dans la colonne technique, bloc de ravitaillement
     *                    au pied de la tour), ou {@code null}
     * @param facing      orientation que le bloc doit avoir, ou {@code null} s'il n'en a pas ; la table la corrige d'elle-même
     * @param level       étage de la tour auquel il appartient, ou -1
     */
    public record Piece(BlockPos offset, Role role, Block block, Block alternative, Direction facing, int level, BlockState shown) {
        public Piece(BlockPos offset, Role role, Block block, Block alternative, Direction facing, int level) {
            this(offset, role, block, alternative, facing, level, null);
        }

        public boolean accepts(BlockState state) {
            return state.is(block) || (alternative != null && state.is(alternative));
        }

        /** État du bloc tel que le plan le montre. */
        public BlockState display() {
            if (shown != null) {
                return shown;
            }
            BlockState state = block.defaultBlockState();
            return facing != null && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                    ? state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing) : state;
        }

        /**
         * Vrai si cette case compte pour un bloc à réunir. Le portique d'embarquement occupe neuf cases mais n'est
         * qu'un seul objet : seul son cœur compte.
         */
        public boolean counted() {
            return shown == null || !(shown.getBlock() instanceof BoardingGateBlock) || BoardingGateBlock.isCore(shown);
        }
    }

    private static final Map<PadPlan, List<Piece>> CACHE = new ConcurrentHashMap<>();

    public PadPlan {
        radius = Math.clamp(radius, 1, LaunchComplex.MAX_RADIUS);
        facing = facing.getAxis().isHorizontal() ? facing : Direction.EAST;
        gate = gateLevel(radius, elevator, gate);
        // Le bras reste sous le sommet, au-dessus du tablier, et ne sort jamais de la tour à travers le portique ni
        // contre sa passerelle : il passe alors au-dessus du portique, ou en dessous s'il n'y a pas la place.
        int levels = towerLevels(radius);
        arm = Math.clamp(arm, 3, levels - 2);
        if (arm >= gate - 1 && arm <= gate + 2) {
            arm = gate + 3 < levels ? gate + 3 : gate - 2;
        }
    }

    public PadPlan(int radius, boolean elevator, Direction facing, int gate) {
        this(radius, elevator, facing, gate, ARM_LEVEL);
    }

    /** Plan dont le portique est à sa hauteur habituelle : à dix blocs du sol, ou au plus haut si la tour est basse. */
    public PadPlan(int radius, boolean elevator, Direction facing) {
        this(radius, elevator, facing, 10);
    }

    /**
     * Étage où le portique peut réellement se trouver, au plus près de celui demandé. Il lui faut trois étages sous
     * le sommet, et il ne descend pas sous le deuxième étage (la porte de la tour est en bas).
     */
    public static int gateLevel(int radius, boolean elevator, int wanted) {
        return Math.clamp(wanted, 2, towerLevels(radius) - 3);
    }

    /**
     * Côté du noyau où se trouve la volée d'escalier d'un étage (0 : flanc gauche, 1 : arrière, 2 : flanc droit,
     * 3 : avant). L'escalier est calé sur le portique d'embarquement : à son étage, une volée part du coin avant
     * gauche, dont le palier donne sur le portique. Quelle que soit la hauteur de la cabine, on y arrive de plain-pied.
     */
    private int turn(int level) {
        return Math.floorMod(level - gate, 4);
    }

    /** Le même plan, portique à un autre étage. */
    public PadPlan withGate(int level) {
        return new PadPlan(radius, elevator, facing, level, arm);
    }

    /** Le même plan, bras ombilical à un autre étage. */
    public PadPlan withArm(int level) {
        return new PadPlan(radius, elevator, facing, gate, level);
    }

    /** Étage du bras ombilical. */
    public int armLevel() {
        return arm;
    }

    /** Place du bras ombilical par rapport à la table : contre la colonne technique, du côté de la fusée. */
    public BlockPos armOffset() {
        return at(-radius, armLevel() - 1, 0);
    }

    /**
     * Place du bloc de ravitaillement en eau du déluge, par rapport à la table : au pied de la tour, à son coin
     * avant droit. {@code null} sur le plus petit pas de tir, qui n'a pas de rampes de déluge.
     */
    public BlockPos waterPort() {
        return radius >= 2 ? at(-radius - 1, -1, TOWER_HALF) : null;
    }

    /** Les neuf cases du portique d'embarquement, par rapport à la table, du seuil au linteau. */
    public List<BlockPos> gateCells() {
        List<BlockPos> cells = new ArrayList<>(9);
        for (int row = 0; row <= 2; row++) {
            for (int s = -1; s <= 1; s++) {
                cells.add(at(-radius - 1, gate - 1 + row, s));
            }
        }
        return cells;
    }

    /** Nombre d'étages de la tour. */
    public int towerLevels() {
        return TOWER_LEVELS[radius - 1];
    }

    /**
     * Tailles de tablier que le kit propose, par leur rayon : 5×5, 9×9 et 11×11. Le 3×3 et le 7×7 n'existent plus :
     * on n'en pose plus, et un pas de tir de ces tailles bâti avant leur retrait ne se met plus en service. Un clic
     * droit sur sa table avec un kit le remplace par une taille proposée.
     */
    public static final int[] SIZES = {2, 4, 5};
    /**
     * Pour les tests automatiques : ils bâtissent de petits pas de tir 3×3, qui tiennent dans leur zone d'essai. Faux
     * en temps normal.
     */
    public static boolean retiredSizesAllowed;

    /** Vrai si cette taille de tablier peut servir. */
    public static boolean isOffered(int radius) {
        if (retiredSizesAllowed) {
            return true;
        }
        for (int size : SIZES) {
            if (size == radius) {
                return true;
            }
        }
        return false;
    }

    /** La taille proposée qui convient à celle qu'on demande : la plus petite qui la contient, sinon la plus grande. */
    public static int nearestSize(int radius) {
        for (int size : SIZES) {
            if (size >= radius) {
                return size;
            }
        }
        return SIZES[SIZES.length - 1];
    }

    public static int towerLevels(int radius) {
        return TOWER_LEVELS[Math.clamp(radius, 1, LaunchComplex.MAX_RADIUS) - 1];
    }

    /** Hauteur de chacun des quatre mâts, en blocs. */
    public int mastHeight() {
        return LaunchComplex.requiredMastHeight(radius);
    }

    /** Côté du tablier, en blocs. */
    public int deckSize() {
        return 2 * radius + 1;
    }

    /** Position, par rapport à la table, d'une case repérée par son avancée vers le carneau et son écart vers la droite. */
    public BlockPos at(int forward, int y, int right) {
        return new BlockPos(facing.getStepX() * forward - facing.getStepZ() * right, y,
                facing.getStepZ() * forward + facing.getStepX() * right);
    }

    /** Rangée (comptée le long du carneau) où les rampes de déluge s'interrompent : celle des boosters latéraux. */
    public static final int BOOSTER_GAP = 0;

    /** Les deux cases où les rampes de déluge s'interrompent, par rapport à la table (vides s'il n'y a pas de rampes). */
    public List<BlockPos> delugeGaps() {
        return radius < 2 ? List.of() : List.of(at(BOOSTER_GAP, 1, -2), at(BOOSTER_GAP, 1, 2));
    }

    /** Pied de la colonne technique, par rapport à la table : au milieu de la face de la tour tournée vers la fusée. */
    public BlockPos riserBase() {
        return at(-radius - 1, -1, 0);
    }

    /** Tous les blocs du plan, du bas vers le haut. */
    public List<Piece> pieces() {
        return CACHE.computeIfAbsent(this, PadPlan::build);
    }

    /** Ce qu'il faut réunir : le nombre de blocs de chaque sorte, dans l'ordre où on les pose. */
    public Map<Block, Integer> materials() {
        Map<Block, Integer> counts = new LinkedHashMap<>();
        for (Piece piece : pieces()) {
            if (piece.counted()) {
                counts.merge(piece.block(), 1, Integer::sum);
            }
        }
        return counts;
    }

    private void add(List<Piece> pieces, int forward, int y, int right, Role role, Block block) {
        pieces.add(new Piece(at(forward, y, right), role, block, null, null, -1));
    }

    private static List<Piece> build(PadPlan plan) {
        List<Piece> pieces = new ArrayList<>();
        int r = plan.radius;
        Direction front = plan.facing;
        Direction back = front.getOpposite();

        // Sous le tablier : le déflecteur sous la table, et un appui sur deux le long du pourtour.
        plan.add(pieces, 0, -1, 0, Role.DEFLECTOR, ModBlocks.FLAME_DEFLECTOR.get());
        for (int f = -r; f <= r; f++) {
            for (int s = -r; s <= r; s++) {
                if (Math.max(Math.abs(f), Math.abs(s)) == r && Math.floorMod(f + s, 2) == 0) {
                    plan.add(pieces, f, -1, s, Role.SUPPORT, ModBlocks.PAD_SUPPORT.get());
                }
            }
        }
        // Tablier : les dalles autour de la table, et le carneau de la table jusqu'au bord.
        for (int f = -r; f <= r; f++) {
            for (int s = -r; s <= r; s++) {
                if (f == 0 && s == 0) {
                    continue;
                }
                boolean trench = s == 0 && f > 0;
                pieces.add(new Piece(plan.at(f, 0, s), trench ? Role.TRENCH : Role.DECK,
                        trench ? ModBlocks.FLAME_TRENCH.get() : ModBlocks.LAUNCH_PAD.get(), null, trench ? front : null, -1));
            }
        }
        // Sur le tablier : un mât sur chaque angle, et les deux rampes de déluge de part et d'autre de la fusée
        // (à deux blocs de l'axe : une fusée large de trois blocs garde sa place). Le 3×3 n'a pas la place d'en porter.
        // Chaque rampe s'interrompt au droit de la fusée : c'est là que se dressent ses boosters latéraux.
        for (int y = 1; y <= plan.mastHeight(); y++) {
            for (int f = -r; f <= r; f += 2 * r) {
                for (int s = -r; s <= r; s += 2 * r) {
                    // Sous la tête de chaque mât, un tronçon porte les projecteurs.
                    if (y == plan.mastHeight() - 1) {
                        // Le plan le montre déjà tourné vers la fusée.
                        BlockPos offset = plan.at(f, y, s);
                        pieces.add(new Piece(offset, Role.MAST, ModBlocks.PAD_FLOODLIGHT.get(), null, null, -1, FloodlightBlock.aimed(
                                ModBlocks.PAD_FLOODLIGHT.get().defaultBlockState(), -Integer.signum(offset.getX()), -Integer.signum(offset.getZ()))));
                    } else {
                        plan.add(pieces, f, y, s, Role.MAST, ModBlocks.LIGHTNING_MAST.get());
                    }
                }
            }
        }
        if (r >= 2) {
            for (int s = -2; s <= 2; s += 4) {
                for (int f = 1 - r; f <= r - 1; f++) {
                    if (f == BOOSTER_GAP) {
                        continue;
                    }
                    pieces.add(new Piece(plan.at(f, 1, s), Role.DELUGE, ModBlocks.DELUGE_PIPE.get(), null, front, -1));
                }
            }
        }

        // Tour : cinq blocs de côté, contre le bord du tablier opposé au carneau. `depth` compte les rangées depuis
        // sa face avant (0) jusqu'à sa face arrière (4).
        int levels = plan.towerLevels();
        for (int level = 0; level <= levels; level++) {
            int y = level - 1;
            boolean roof = level == levels;
            for (int depth = 0; depth < TOWER_DEPTH; depth++) {
                for (int s = -TOWER_HALF; s <= TOWER_HALF; s++) {
                    int f = -r - 1 - depth;
                    boolean rim = depth == 0 || depth == TOWER_DEPTH - 1 || Math.abs(s) == TOWER_HALF;
                    if (roof) {
                        // Sommet : une rambarde sur tout le pourtour, des demi-dalles au milieu.
                        Block block = rim ? ModBlocks.TOWER_RAILING.get() : ModBlocks.TOWER_DECK.get();
                        // L'escalier débouche par une trémie dans le platelage : elle s'ouvre au-dessus de ses deux
                        // dernières volées et des paliers d'où elles partent. Dès la première marche de l'avant-dernière
                        // volée, la tête du joueur arrive à la hauteur du platelage.
                        boolean stairwell = false;
                        if (!plan.elevator) {
                            for (int[] cell : new int[][] {STAIR_CELLS[plan.turn(levels - 1)], STAIR_CORNERS[plan.turn(levels - 1)],
                                    STAIR_CELLS[plan.turn(levels - 2)], STAIR_CORNERS[plan.turn(levels - 2)]}) {
                                stairwell |= depth == cell[0] && s == cell[1];
                            }
                        }
                        if (!stairwell) {
                            pieces.add(new Piece(plan.at(f, y, s), Role.ROOF, block, null, null, level));
                        }
                    } else if (rim) {
                        plan.rim(pieces, f, y, s, depth, level);
                    } else if (plan.elevator) {
                        plan.shaft(pieces, f, y, s, depth, level);
                    } else {
                        plan.stairwell(pieces, f, y, s, depth, level, front, back);
                    }
                }
            }
        }
        // Escalier d'accès : deux volées contre le flanc gauche du tablier, du sol jusqu'à son dessus.
        Direction up = front.getClockWise();
        pieces.add(new Piece(plan.at(0, -1, -r - 2), Role.ACCESS, ModBlocks.TOWER_STAIRS.get(), null, up, -1));
        pieces.add(new Piece(plan.at(0, 0, -r - 1), Role.ACCESS, ModBlocks.TOWER_STAIRS.get(), null, up, -1));
        // Le bras ombilical, accroché à la colonne technique et tourné vers la fusée.
        pieces.add(new Piece(plan.armOffset(), Role.ARM, ModBlocks.UMBILICAL_ARM.get(), null, front, plan.armLevel()));
        return List.copyOf(pieces);
    }

    /**
     * Pourtour d'un étage : la colonne technique au milieu de la face avant, de la charpente ailleurs, et une porte
     * au niveau du sol (haute de deux blocs sur le flanc pour l'escalier, de trois à l'arrière pour l'ascenseur). Au pied de
     * la tour, un bloc de charpente peut être remplacé par un bloc de ravitaillement. À l'étage du portique
     * d'embarquement et aux deux suivants, celui-ci prend la place de la colonne et de ses deux voisins de charpente.
     */
    private void rim(List<Piece> pieces, int f, int y, int s, int depth, int level) {
        if (depth == 0 && Math.abs(s) <= 1 && level >= gate && level <= gate + 2) {
            // Chaque case du portique sait sa place : sa colonne se compte vers la droite du passage.
            Direction side = facing.getClockWise();
            BlockPos offset = at(f, y, s);
            BlockPos middle = at(f, y, 0);
            int column = 1 + (offset.getX() - middle.getX()) * side.getStepX() + (offset.getZ() - middle.getZ()) * side.getStepZ();
            BlockState cell = ModBlocks.BOARDING_GATE.get().defaultBlockState().setValue(BoardingGateBlock.ROW, level - gate)
                    .setValue(BoardingGateBlock.COLUMN, column).setValue(BoardingGateBlock.FACING, facing);
            pieces.add(new Piece(offset, Role.TOWER, ModBlocks.BOARDING_GATE.get(), null, facing, level, cell));
            return;
        }
        if (depth == 0 && s == 0) {
            pieces.add(new Piece(at(f, y, s), Role.TOWER, ModBlocks.SERVICE_TOWER.get(), null, facing, level));
            return;
        }
        if (at(f, y, s).equals(waterPort())) {
            // Le bloc de ravitaillement en eau du déluge : il est du plan, sur les pas de tir qui ont des rampes.
            pieces.add(new Piece(at(f, y, s), Role.TOWER, ModBlocks.FUEL_PORT.get(), null, null, level,
                    ModBlocks.FUEL_PORT.get().defaultBlockState().setValue(FuelPortBlock.KIND, FuelPortBlock.Kind.WATER)));
            return;
        }
        // La porte de l'ascenseur a la hauteur de sa cabine : trois blocs.
        boolean door;
        if (elevator) {
            door = level < 3 && depth == TOWER_DEPTH - 1 && s == 0;
        } else {
            // La porte de l'escalier donne sur le coin d'où part sa première volée : sur le flanc gauche, à l'avant ou
            // à l'arrière, ou sur la face arrière (d'où l'on rejoint au besoin le coin avant droit, par le sol).
            int[] cell = switch (turn(0)) {
                case 0 -> new int[] {1, -TOWER_HALF};
                case 1 -> new int[] {3, -TOWER_HALF};
                default -> new int[] {TOWER_DEPTH - 1, 1};
            };
            door = level < 2 && depth == cell[0] && s == cell[1];
        }
        if (!door) {
            pieces.add(new Piece(at(f, y, s), Role.TOWER, ModBlocks.TOWER_FRAME.get(),
                    level == 0 ? ModBlocks.FUEL_PORT.get() : null, null, level));
        }
    }

    /**
     * Intérieur d'un étage, tour à ascenseur : la gaine occupe tout l'intérieur de la tour, trois blocs sur trois,
     * et reste vide ; le socle de l'ascenseur est en son centre, au sol. Le pourtour de la tour la ferme de toutes
     * parts.
     */
    private void shaft(List<Piece> pieces, int f, int y, int s, int depth, int level) {
        if (level == 0 && s == 0 && depth == TOWER_DEPTH / 2) {
            pieces.add(new Piece(at(f, y, s), Role.TOWER, ModBlocks.TOWER_ELEVATOR.get(), null, null, level));
        }
    }

    /**
     * Cases de l'escalier, autour du noyau de la tour (profondeur, écart) : à chaque étage, la volée occupe la
     * suivante. Dans l'ordre : le flanc gauche, l'arrière, le flanc droit, l'avant.
     */
    private static final int[][] STAIR_CELLS = {{2, -1}, {3, 0}, {2, 1}, {1, 0}};
    /** Coins d'où part chaque volée : avant gauche, arrière gauche, arrière droit, avant droit. */
    private static final int[][] STAIR_CORNERS = {{1, -1}, {3, -1}, {3, 1}, {1, 1}};

    /**
     * Intérieur d'un étage, tour à escalier : un noyau de charpente au centre, et un escalier en colimaçon qui
     * tourne autour de lui. Chaque étage a une volée, sur l'un des quatre côtés du noyau, et un palier dans le coin
     * d'où elle part ; la volée suivante part du coin où elle arrive. L'escalier fait donc un tour tous les quatre
     * étages : au-dessus de chaque marche et de chaque palier, il y a près de quatre blocs de vide. (Deux volées
     * superposées à deux étages d'écart ne laissaient pas passer le joueur, plus large qu'une marche.)
     */
    private void stairwell(List<Piece> pieces, int f, int y, int s, int depth, int level, Direction front, Direction back) {
        if (depth == 2 && s == 0) {
            pieces.add(new Piece(at(f, y, s), Role.TOWER, ModBlocks.TOWER_FRAME.get(), null, null, level));
            return;
        }
        int turn = turn(level);
        if (depth == STAIR_CELLS[turn][0] && s == STAIR_CELLS[turn][1]) {
            Direction climb = switch (turn) {
                case 0 -> back;
                case 1 -> front.getClockWise();
                case 2 -> front;
                default -> front.getCounterClockWise();
            };
            pieces.add(new Piece(at(f, y, s), Role.TOWER, ModBlocks.TOWER_STAIRS.get(), null, climb, level));
        } else if (level > 0 && depth == STAIR_CORNERS[turn][0] && s == STAIR_CORNERS[turn][1]) {
            pieces.add(new Piece(at(f, y, s), Role.TOWER, ModBlocks.TOWER_LANDING.get(), null, null, level));
        }
    }
}
