package com.orbitalis.phase3.interstellar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.orbitalis.core.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Le plan du vaisseau interstellaire, tel qu'il se bâtit au chantier naval orbital : une longue coque habitable de cinq
 * blocs sur cinq, comme les modules d'une station, que ceinture un anneau tenu par quatre rayons.
 * <p>
 * Il se compte en trois coordonnées locales : {@code a} le long du vaisseau (0 à la poupe, côté station, jusqu'à
 * {@link #LENGTH} - 1 à la proue, tournée vers le large ; le collier d'amarrage est en -1), {@code s} en travers (vers la
 * droite quand on regarde la proue) et {@code y} en hauteur, toutes deux de -2 à 2 pour la coque et jusqu'à
 * ±{@link #RING_RADIUS} pour l'anneau.
 * <p>
 * Il se bâtit section par section, de la poupe à la proue :
 * <ul>
 * <li>le sas : le collier qui s'ajuste à la porte du chantier, la porte extérieure, une chambre avec les casiers des
 * combinaisons, la porte intérieure ;</li>
 * <li>la salle des machines : le cœur de distorsion, le générateur d'air au plafond, deux batteries, et dehors, sur le
 * dos, douze panneaux solaires ;</li>
 * <li>l'anneau et son moteur ;</li>
 * <li>les quartiers : quatre caissons d'hibernation, où l'on se réveille à bord ;</li>
 * <li>le laboratoire et la soute : quatre baies d'expériences et quatre casiers de fret ;</li>
 * <li>le hangar : le sas d'amarrage de la navette, sur le flanc gauche ;</li>
 * <li>la serre : huit bacs de culture sous leurs rampes ;</li>
 * <li>la passerelle : le poste de pilotage entre deux consoles, face à la verrière de la proue.</li>
 * </ul>
 */
public final class ShipPlan {
    /** Longueur du vaisseau, en blocs, collier d'amarrage non compris. */
    public static final int LENGTH = 42;
    /** Rayon de l'anneau, en blocs, compté depuis l'axe. */
    public static final int RING_RADIUS = 5;
    /** Demi-largeur du berceau qui entoure le vaisseau, en blocs : l'anneau y tient, avec un bloc de marge. */
    public static final int CRADLE = 6;
    /** Positions des cadres du berceau, le long du vaisseau : devant la poupe, puis tous les quatorze blocs. */
    public static final int[] RIBS = {-1, 14, 28, LENGTH};
    /** Place du poste de pilotage : le long du vaisseau, et en hauteur (au ras du plancher). */
    public static final int HELM_A = LENGTH - 3;
    public static final int HELM_Y = -1;
    /** Le collier d'amarrage, derrière la poupe : il s'ajuste à la porte du chantier. */
    public static final int COLLAR_A = -1;
    /** Le passage du sas, de la poupe à la salle des machines : sur le côté droit du couloir, comme la porte du chantier. */
    public static final int DOOR_S = 1;
    /** La porte intérieure du sas. */
    public static final int INNER_DOOR_A = 3;
    /** Le cœur de distorsion, dressé au milieu de la salle des machines. */
    public static final int WARP_CORE_A = 6;
    /** Le sas d'amarrage de la navette, sur le flanc gauche du hangar. */
    public static final int HANGAR_A = 30;

    /** Les sections, de la poupe à la proue, avec leurs bornes le long du vaisseau. */
    public enum Section {
        AIRLOCK("airlock", COLLAR_A, INNER_DOOR_A),
        ENGINEERING("engineering", 4, 8),
        RING("ring", 9, 15),
        HIBERNATION("hibernation", 16, 21),
        LABORATORY("laboratory", 22, 27),
        HANGAR("hangar", 28, 31),
        GREENHOUSE("greenhouse", 32, 37),
        BRIDGE("bridge", 38, LENGTH - 1);

        private final String id;
        private final int from;
        private final int to;

        Section(String id, int from, int to) {
            this.id = id;
            this.from = from;
            this.to = to;
        }

        public String id() {
            return id;
        }

        public String nameKey() {
            return "shipyard.orbitalis.section." + id;
        }

        static Section at(int a) {
            for (Section section : values()) {
                if (a >= section.from && a <= section.to) {
                    return section;
                }
            }
            return BRIDGE;
        }
    }

    /**
     * Une pièce du plan : sa position par rapport à l'origine du vaisseau (le centre de la poupe), le bloc qui y va,
     * tourné comme il faut, et la section à laquelle il appartient.
     */
    public record Piece(BlockPos offset, BlockState state, Section section) {
    }

    private static final Map<Direction, List<Piece>> CACHE = new EnumMap<>(Direction.class);

    private ShipPlan() {
    }

    /** Position, par rapport à l'origine du vaisseau, du point (a, s, y) d'un vaisseau dont la proue regarde {@code out}. */
    public static BlockPos local(Direction out, int a, int s, int y) {
        Direction right = out.getClockWise();
        return new BlockPos(out.getStepX() * a + right.getStepX() * s, y, out.getStepZ() * a + right.getStepZ() * s);
    }

    /** Toutes les pièces du vaisseau dont la proue regarde {@code out}, de la poupe à la proue. */
    public static synchronized List<Piece> pieces(Direction out) {
        return CACHE.computeIfAbsent(out, ShipPlan::build);
    }

    private static List<Piece> build(Direction out) {
        Direction right = out.getClockWise();
        // L'aménagement d'abord relevé : il prend la place de la coque là où il se pose (une rampe au plafond, le moteur
        // de l'anneau), et il vient après elle dans chaque section.
        Map<BlockPos, Piece> fittings = new java.util.LinkedHashMap<>();
        for (Piece piece : fittings(out, right)) {
            fittings.put(piece.offset(), piece);
        }
        Map<Section, List<Piece>> bySection = new EnumMap<>(Section.class);
        for (Section section : Section.values()) {
            bySection.put(section, new ArrayList<>());
        }
        for (int a = 0; a < LENGTH; a++) {
            Section section = Section.at(a);
            List<Piece> pieces = bySection.get(section);
            for (int s = -2; s <= 2; s++) {
                for (int y = -2; y <= 2; y++) {
                    BlockState state = hull(out, right, a, s, y);
                    if (state != null && !fittings.containsKey(local(out, a, s, y))) {
                        pieces.add(new Piece(local(out, a, s, y), state, section));
                    }
                }
            }
            addRing(pieces, out, a);
        }
        for (Piece fitting : fittings.values()) {
            bySection.get(fitting.section()).add(fitting);
        }
        List<Piece> all = new ArrayList<>();
        bySection.values().forEach(all::addAll);
        return Collections.unmodifiableList(all);
    }

    /** Place de l'axe de l'anneau le long du vaisseau ; son moteur est au plafond, au droit de cet axe. */
    public static final int RING_A = 12;
    /** Le moteur de l'anneau, depuis la poupe. */
    public static final int RING_DRIVE_Y = 2;

    /**
     * L'anneau à la hauteur {@code a} : trois rangs de coque autour de l'axe, le rang du milieu vitré ; au milieu, ses
     * quatre rayons. C'est ce qui tourne quand le moteur de l'anneau est en marche.
     */
    private static void addRing(List<Piece> pieces, Direction out, int a) {
        if (a >= RING_A - 1 && a <= RING_A + 1) {
            for (int s = -RING_RADIUS; s <= RING_RADIUS; s++) {
                for (int y = -RING_RADIUS; y <= RING_RADIUS; y++) {
                    double d = Math.sqrt(s * s + y * y);
                    if (d >= RING_RADIUS - 0.5 && d < RING_RADIUS + 0.5) {
                        BlockState ring = a == RING_A ? ModBlocks.STATION_WINDOW.get().defaultBlockState() : ModBlocks.STATION_HULL.get().defaultBlockState();
                        pieces.add(new Piece(local(out, a, s, y), ring, Section.RING));
                    }
                }
            }
        }
        if (a == RING_A) {
            for (int k = 3; k < RING_RADIUS; k++) {
                for (int[] spoke : new int[][] {{k, 0}, {-k, 0}, {0, k}, {0, -k}}) {
                    pieces.add(new Piece(local(out, a, spoke[0], spoke[1]), ModBlocks.STATION_TRUSS.get().defaultBlockState(), Section.RING));
                }
            }
        }
    }

    /** Les pièces de l'anneau et de ses rayons, pour un vaisseau dont la proue regarde {@code out}. */
    public static List<Piece> ring(Direction out) {
        List<Piece> ring = new ArrayList<>();
        for (int a = RING_A - 1; a <= RING_A + 1; a++) {
            addRing(ring, out, a);
        }
        return ring;
    }

    /** Le bloc posé là tient-il lieu de cette pièce ? Un segment d'anneau en rotation compte pour le bloc qu'il remplace. */
    public static boolean matches(BlockState there, BlockState planned) {
        return there.is(planned.getBlock())
                || there.getBlock() instanceof RingSegmentBlock && RingSegmentBlock.original(there).is(planned.getBlock());
    }

    /** L'aménagement du vaisseau, section par section. */
    private static List<Piece> fittings(Direction out, Direction right) {
        List<Piece> list = new ArrayList<>();
        Direction back = out.getOpposite();
        Direction left = right.getOpposite();
        // Sas : le collier, un cadre de coque autour du passage, qui s'ajuste à la porte du chantier ; les deux portes ; les
        // casiers des combinaisons, contre le flanc gauche de la chambre.
        for (int[] cell : new int[][] {{DOOR_S - 1, -1}, {DOOR_S - 1, 0}, {DOOR_S + 1, -1}, {DOOR_S + 1, 0}, {DOOR_S, -2}, {DOOR_S, 1}}) {
            add(list, out, COLLAR_A, cell[0], cell[1], ModBlocks.STATION_HULL.get().defaultBlockState());
        }
        BlockState door = ModBlocks.AIRLOCK_DOOR.get().defaultBlockState().setValue(com.orbitalis.phase2.station.AirlockDoorBlock.FACING, back)
                .setValue(com.orbitalis.phase2.station.AirlockDoorBlock.PHASE, com.orbitalis.phase2.station.AirlockDoorBlock.Phase.CLOSED);
        tall(list, out, 0, DOOR_S, door);
        tall(list, out, INNER_DOOR_A, DOOR_S, door);
        for (int a = 1; a <= 2; a++) {
            add(list, out, a, -1, -1, ModBlocks.CARGO_RACK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, right));
        }
        // Salle des machines : le cœur de distorsion, dressé au milieu du couloir, sa face vers la proue ; le générateur d'air
        // au plafond ; deux batteries ; et sur le dos, un champ de panneaux solaires raccordés entre eux.
        tall(list, out, WARP_CORE_A, 0, ModBlocks.WARP_CORE.get().defaultBlockState().setValue(TwoHighBlock.FACING, back));
        add(list, out, 5, 0, 2, ModBlocks.LIFE_SUPPORT.get().defaultBlockState());
        for (int s = -1; s <= 1; s += 2) {
            add(list, out, 8, s, -1, ModBlocks.STATION_BATTERY.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, s < 0 ? right : left));
        }
        for (int a = 4; a <= 7; a++) {
            for (int s = -1; s <= 1; s++) {
                BlockState panel = ModBlocks.SOLAR_PANEL.get().defaultBlockState();
                panel = connect(panel, out, a < 7);
                panel = connect(panel, back, a > 4);
                panel = connect(panel, right, s < 1);
                panel = connect(panel, left, s > -1);
                add(list, out, a, s, 3, panel);
            }
        }
        // Anneau : son moteur, au plafond, au droit des rayons.
        add(list, out, RING_A, 0, RING_DRIVE_Y, ModBlocks.RING_DRIVE.get().defaultBlockState());
        // Quartiers : quatre caissons dressés le long des flancs, tournés vers le couloir.
        for (int a : new int[] {17, 19}) {
            for (int s : new int[] {-1, 1}) {
                tall(list, out, a, s, ModBlocks.HIBERNATION_POD.get().defaultBlockState().setValue(TwoHighBlock.FACING, s < 0 ? right : left));
            }
        }
        // Laboratoire et soute : quatre baies d'expériences, deux de chaque côté, puis quatre casiers de fret.
        for (int s : new int[] {-1, 1}) {
            Direction inward = s < 0 ? right : left;
            for (int y = -1; y <= 0; y++) {
                add(list, out, 23, s, y, ModBlocks.LAB_RACK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, inward));
            }
            for (int a = 25; a <= 26; a++) {
                add(list, out, a, s, -1, ModBlocks.CARGO_RACK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, inward));
            }
        }
        // Hangar : le sas d'amarrage de la navette, dans le flanc gauche, son collier tourné vers le dehors.
        add(list, out, HANGAR_A, -2, 0, ModBlocks.DOCKING_PORT.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, left));
        // Serre : huit bacs de culture le long des flancs, sous leurs rampes de diodes.
        for (int a = 33; a <= 36; a++) {
            for (int s : new int[] {-1, 1}) {
                add(list, out, a, s, -1, ModBlocks.HYDROPONIC_TRAY.get().defaultBlockState());
                add(list, out, a, s, 2, ModBlocks.STATION_LIGHT.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.DOWN));
            }
        }
        // Passerelle : le poste de pilotage, et une console de chaque côté, face à la verrière de la proue.
        add(list, out, HELM_A, 0, HELM_Y, ModBlocks.SHIP_HELM.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, back));
        for (int s : new int[] {-1, 1}) {
            add(list, out, HELM_A, s, HELM_Y, ModBlocks.STATION_TERMINAL.get().defaultBlockState()
                    .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, back));
        }
        return list;
    }

    /** Un panneau solaire raccordé, ou non, à son voisin de ce côté. */
    private static BlockState connect(BlockState panel, Direction side, boolean joined) {
        return panel.setValue(com.orbitalis.phase2.station.SolarPanelBlock.CONNECTIONS.get(side), joined);
    }

    private static void add(List<Piece> list, Direction out, int a, int s, int y, BlockState state) {
        list.add(new Piece(local(out, a, s, y), state, Section.at(a)));
    }

    /** Un meuble de deux blocs de haut, posé sur le plancher : ses deux moitiés. */
    private static void tall(List<Piece> list, Direction out, int a, int s, BlockState state) {
        add(list, out, a, s, -1, state.setValue(TwoHighBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
        add(list, out, a, s, 0, state.setValue(TwoHighBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
    }

    /** Vrai si cette pièce se pose avec une autre (le haut d'un meuble de deux blocs) : elle ne coûte rien. */
    public static boolean free(Piece piece) {
        return piece.state().hasProperty(TwoHighBlock.HALF)
                && piece.state().getValue(TwoHighBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER;
    }

    /** La coque au point (a, s, y) : {@code null} pour l'intérieur, qui reste vide. */
    private static BlockState hull(Direction out, Direction right, int a, int s, int y) {
        int as = Math.abs(s);
        int ay = Math.abs(y);
        boolean stern = a == 0;
        boolean bow = a == LENGTH - 1;
        // La cloison du sas, entre la chambre et la salle des machines.
        boolean bulkhead = a == INNER_DOOR_A;
        BlockState hull = ModBlocks.STATION_HULL.get().defaultBlockState();
        BlockState cupola = ModBlocks.CUPOLA_WINDOW.get().defaultBlockState();
        if (as == 2 && ay == 2) {
            return hull;
        }
        if (stern || bow || bulkhead) {
            if (as == 2 || ay == 2) {
                return hull;
            }
            // La poupe et la cloison sont fermées de revêtement ; la proue est une verrière, celle de la passerelle.
            return bow ? cupola : panel(out);
        }
        // Les flancs et le plafond du bout de la passerelle sont vitrés, comme une coupole.
        if (a == LENGTH - 2 && ((y == 2 && as <= 1) || (as == 2 && ay <= 1))) {
            return cupola;
        }
        if (as == 2) {
            // Un hublot au milieu des flancs de chaque section habitable ; le hangar en a un de chaque côté du sas de la navette.
            if (y == 0 && (a == 2 || a == 6 || a == 18 || a == 24 || a == 29 || a == 31 || a == 34)) {
                return ModBlocks.STATION_WINDOW.get().defaultBlockState();
            }
            return panel(s < 0 ? right : right.getOpposite());
        }
        if (y == -2) {
            return ModBlocks.STATION_FLOOR.get().defaultBlockState();
        }
        if (y == 2) {
            if (s == 0 && a % 3 == 1) {
                return ModBlocks.STATION_LIGHT.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.DOWN);
            }
            return panel(Direction.DOWN);
        }
        return null;
    }

    private static BlockState panel(Direction facing) {
        return ModBlocks.STATION_PANEL.get().defaultBlockState().setValue(DirectionalBlock.FACING, facing);
    }

    /**
     * Le berceau du chantier : quatre longerons de poutre treillis aux angles, et des cadres carrés en travers, au droit
     * de {@link #RIBS}. Positions par rapport à l'origine du vaisseau.
     */
    public static List<BlockPos> cradle(Direction out) {
        List<BlockPos> list = new ArrayList<>();
        for (int a = RIBS[0]; a <= RIBS[RIBS.length - 1]; a++) {
            boolean rib = false;
            for (int r : RIBS) {
                rib |= r == a;
            }
            for (int s = -CRADLE; s <= CRADLE; s++) {
                for (int y = -CRADLE; y <= CRADLE; y++) {
                    boolean edge = Math.abs(s) == CRADLE && Math.abs(y) == CRADLE;
                    boolean frame = rib && (Math.abs(s) == CRADLE || Math.abs(y) == CRADLE);
                    if (edge || frame) {
                        list.add(local(out, a, s, y));
                    }
                }
            }
        }
        return list;
    }

    /** Toutes les cases du berceau, intérieur compris : elles doivent être libres pour l'ouvrir. */
    public static List<BlockPos> bay(Direction out) {
        List<BlockPos> list = new ArrayList<>();
        for (int a = RIBS[0]; a <= RIBS[RIBS.length - 1]; a++) {
            for (int s = -CRADLE; s <= CRADLE; s++) {
                for (int y = -CRADLE; y <= CRADLE; y++) {
                    list.add(local(out, a, s, y));
                }
            }
        }
        return list;
    }
}
