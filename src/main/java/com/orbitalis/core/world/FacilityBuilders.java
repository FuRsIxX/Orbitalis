package com.orbitalis.core.world;

import static com.orbitalis.core.world.FacilityPiece.IN;
import static com.orbitalis.core.world.FacilityPiece.LEFT;
import static com.orbitalis.core.world.FacilityPiece.OUT;
import static com.orbitalis.core.world.FacilityPiece.RIGHT;

import com.orbitalis.core.registry.ModBlocks;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Les plans des bâtiments des centres spatiaux, inspirés de vrais centres :
 * <ul>
 * <li>la station météorologique : un bâtiment d'observation à toit plat, son mât anémométrique, son parc à
 * instruments (abri de Stevenson, pluviomètre, héliographe) ; en grand, une tour radar sous radôme, comme les radars
 * météo NEXRAD, et l'abri où l'on gonfle les ballons-sondes ;</li>
 * <li>le centre de recherche : un laboratoire à deux niveaux et ses fenêtres en bandeau ; en grand, un banc d'essai
 * de moteurs (une tour d'acier sur un massif de béton et son déflecteur, comme à Stennis) et un hangar ;</li>
 * <li>le centre d'observation : la coupole d'un télescope et sa fente, son poste de contrôle, une petite antenne ;
 * en grand, un radiotélescope, comme ceux du réseau de l'espace lointain ;</li>
 * <li>le centre de contrôle : une salle de tir aux grandes baies tournées vers le pas de tir, comme au Launch
 * Control Center du centre Kennedy ; un haut bâtiment d'assemblage, une fusée dedans ; un château d'eau ; et le pas
 * de tir, sa butte, sa tranchée de flammes, sa tour de service et ses sphères d'ergols ;</li>
 * <li>et des annexes, tirées au hasard : antenne-relais, dépôt de carburant, champ solaire, parc de stockage,
 * groupe de paraboles, hélistation, poste de garde, bureaux.</li>
 * </ul>
 * Chaque centre a son style de matériaux ({@link FacilityStyle}), et chaque bâtiment ses variantes. Les salles sont
 * meublées de plans de travail et de pupitres bas, d'archives et de baies de serveurs ; un centre n'a qu'un ou deux vrais postes de
 * travail, posés avec toute leur place et leur mur d'écrans.
 * <p>
 * Repère : x vers la droite, y vers le haut (0 est la dalle), z de la façade vers le fond ; la façade regarde le sud.
 */
final class FacilityBuilders {
    private FacilityBuilders() {
    }

    /** Les blocs communs à tous les styles. */
    private static final class P {
        static final BlockState CONCRETE = ModBlocks.FACILITY_CONCRETE.get().defaultBlockState();
        static final BlockState SLAB = ModBlocks.FACILITY_CONCRETE_SLAB.get().defaultBlockState();
        static final BlockState SLAB_TOP = SLAB.setValue(SlabBlock.TYPE, SlabType.TOP);
        static final BlockState SIDING = ModBlocks.FACILITY_SIDING.get().defaultBlockState();
        static final BlockState LOUVER = ModBlocks.LOUVERED_PANEL.get().defaultBlockState();
        static final BlockState RADOME = ModBlocks.RADOME_PANEL.get().defaultBlockState();
        static final BlockState GLASS = ModBlocks.FACILITY_GLASS.get().defaultBlockState();
        static final BlockState MESH = ModBlocks.ANTENNA_MESH.get().defaultBlockState();
        static final BlockState HAZARD = ModBlocks.HAZARD_STRIPES.get().defaultBlockState();
        static final BlockState LIGHT = ModBlocks.STATION_LIGHT.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.DOWN);
        static final BlockState GROUND_LIGHT = ModBlocks.STATION_LIGHT.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.UP);
        static final BlockState BARS = Blocks.IRON_BARS.defaultBlockState();
        static final BlockState ROD = Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState();
        /** Les pièces mécaniques (étriers, montures, cornets) : un béton gris clair, jamais du fer. */
        static final BlockState IRON = Blocks.CONCRETE.pick(DyeColor.LIGHT_GRAY).defaultBlockState();
        static final BlockState FRAME = ModBlocks.TOWER_FRAME.get().defaultBlockState();
        static final BlockState WHITE = Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState();
        static final BlockState GRAY = Blocks.CONCRETE.pick(DyeColor.GRAY).defaultBlockState();

        static BlockState of(Block block) {
            return block.defaultBlockState();
        }

        static BlockState facing(Block block, Direction direction) {
            return block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, direction);
        }

        static BlockState stairs(Direction direction) {
            return ModBlocks.FACILITY_CONCRETE_STAIRS.get().defaultBlockState().setValue(StairBlock.FACING, direction);
        }

        static BlockState ladder(Direction direction) {
            return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, direction);
        }

        /**
         * Un plan de travail : un escalier de quartz lisse retourné. Son plateau est à hauteur de table, et son pan
         * arrière descend jusqu'au sol, comme le caisson d'un bureau ; on s'y tient du côté {@code facing}.
         */
        static BlockState bench(Direction facing) {
            return Blocks.SMOOTH_QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing.getOpposite())
                    .setValue(StairBlock.HALF, net.minecraft.world.level.block.state.properties.Half.TOP);
        }

        /** Un écran posé sur un plan de travail : une vitre noire, de la largeur du bureau (qui court de gauche à droite). */
        static BlockState monitor() {
            return Blocks.STAINED_GLASS_PANE.pick(DyeColor.BLACK).defaultBlockState()
                    .setValue(net.minecraft.world.level.block.CrossCollisionBlock.EAST, true)
                    .setValue(net.minecraft.world.level.block.CrossCollisionBlock.WEST, true);
        }

        /** Un pupitre : un escalier de pierre noire polie, son plan incliné tourné vers l'opérateur, qui se tient du côté {@code facing}. */
        static BlockState console(Direction facing) {
            return Blocks.POLISHED_BLACKSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing.getOpposite());
        }

        /** Une baie de serveurs, sa porte côté {@code facing}. */
        static BlockState rack(Direction facing) {
            return facing(ModBlocks.SERVER_RACK.get(), facing);
        }
    }

    // ------------------------------------------------------------------
    // Outils communs
    // ------------------------------------------------------------------

    /** Un toit plat à {@code y}, et son acrotère d'une demi-dalle tout autour. */
    private static void roof(FacilityPiece p, int x0, int y, int z0, int x1, int z1) {
        p.fill(x0, y, z0, x1, y, z1, p.style().roof());
        parapet(p, x0, y + 1, z0, x1, z1, P.SLAB);
    }

    private static void parapet(FacilityPiece p, int x0, int y, int z0, int x1, int z1, BlockState state) {
        for (int x = x0; x <= x1; x++) {
            p.set(x, y, z0, state);
            p.set(x, y, z1, state);
        }
        for (int z = z0 + 1; z < z1; z++) {
            p.set(x0, y, z, state);
            p.set(x1, y, z, state);
        }
    }

    /** Des poteaux aux quatre angles d'un volume. */
    private static void corners(FacilityPiece p, int x0, int y0, int z0, int x1, int y1, int z1) {
        BlockState frame = p.style().frame();
        for (int y = y0; y <= y1; y++) {
            p.solid(x0, y, z0, frame);
            p.solid(x1, y, z0, frame);
            p.solid(x0, y, z1, frame);
            p.solid(x1, y, z1, frame);
        }
    }

    /** Une volée d'escalier qui monte vers le fond, de {@code y} à {@code y + steps - 1}, et sa trémie dans le plancher. */
    private static void flight(FacilityPiece p, int x, int y, int z, int steps) {
        for (int k = 0; k < steps; k++) {
            p.solid(x, y + k, z + k, P.stairs(IN));
            p.air(x, y + k + 1, z + k, x, y + k + 2, z + k);
        }
    }

    /** Un plafonnier pris dans un plancher d'étage : un tapis le couvre côté étage, pour qu'il n'éclaire que la pièce du dessous. */
    private static void ceilingLight(FacilityPiece p, int x, int y, int z) {
        p.solid(x, y, z, P.LIGHT);
        p.cover(x, y + 1, z, P.of(Blocks.CARPET.pick(DyeColor.GRAY)));
    }

    /** Un mât d'antenne : des barreaux, et un paratonnerre au sommet. */
    private static void mast(FacilityPiece p, int x, int y, int z, int height) {
        for (int k = 0; k < height - 1; k++) {
            p.set(x, y + k, z, P.BARS);
        }
        p.set(x, y + height - 1, z, P.ROD);
    }

    /** Une petite parabole de toit : un pied, une coupelle de grillage tournée vers le ciel, son cornet. */
    private static void miniDish(FacilityPiece p, int x, int y, int z) {
        p.set(x, y, z, P.BARS);
        p.set(x, y + 1, z, P.IRON);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx != 0 || dz != 0) {
                    p.set(x + dx, y + 2, z + dz, P.MESH);
                }
            }
        }
        p.set(x, y + 2, z, P.BARS);
        p.set(x, y + 3, z, P.ROD);
    }

    /** L'équipement d'un toit plat, au hasard : une petite parabole, la climatisation, des panneaux solaires ou une antenne. */
    private static void roofGear(FacilityPiece p, int x0, int y, int z0, int x1, int z1) {
        int cx = (x0 + x1) / 2;
        int cz = (z0 + z1) / 2;
        switch (p.random.nextInt(4)) {
            case 0 -> miniDish(p, x0 + 1, y, z1 - 1);
            case 1 -> p.fill(cx - 1, y, cz, cx + 1, y + 1, cz + 1, P.LOUVER);
            case 2 -> {
                for (int x = x0; x <= x1; x += 2) {
                    p.set(x, y, cz, P.of(ModBlocks.SOLAR_PANEL.get()));
                }
            }
            default -> mast(p, x1, y, z1, 3 + p.random.nextInt(3));
        }
        if (p.random.nextBoolean()) {
            p.fill(x1 - 1, y, z0, x1, y, z0, P.LOUVER);
        }
    }

    /**
     * Une parabole : la surface d'un paraboloïde de révolution, de sommet {@code (vx, vy, vz)}, de rayon et de focale
     * donnés, son axe penché de {@code tilt} degrés vers la façade. Le cornet de réception est au foyer, tenu par
     * trois bras.
     */
    private static void dish(FacilityPiece p, double vx, double vy, double vz, double radius, double focal, double tilt, BlockState shell) {
        double t = Math.toRadians(tilt);
        double ny = Math.cos(t);
        double nz = -Math.sin(t);
        int x0 = Math.max(0, (int) Math.floor(vx - radius - 1));
        int x1 = Math.min(p.width() - 1, (int) Math.ceil(vx + radius + 1));
        for (int y = 0; y < p.height(); y++) {
            for (int z = 0; z < p.depth(); z++) {
                for (int x = x0; x <= x1; x++) {
                    double px = x + 0.5 - vx;
                    double py = y + 0.5 - vy;
                    double pz = z + 0.5 - vz;
                    double a = py * ny + pz * nz;
                    double r2 = px * px + py * py + pz * pz - a * a;
                    if (r2 <= radius * radius && Math.abs(a - r2 / (4.0 * focal)) <= 0.7) {
                        p.set(x, y, z, shell);
                    }
                }
            }
        }
        double fx = vx;
        double fy = vy + ny * focal;
        double fz = vz + nz * focal;
        double rim = radius * radius / (4.0 * focal);
        for (double angle : new double[] {90.0, 210.0, 330.0}) {
            double c = Math.cos(Math.toRadians(angle)) * radius;
            double s = Math.sin(Math.toRadians(angle)) * radius;
            double rx = vx + c * 0.92;
            double ry = vy + s * 0.92 * nz + ny * rim;
            double rz = vz - s * 0.92 * ny + nz * rim;
            for (int k = 1; k < 12; k++) {
                double f = k / 12.0;
                p.set((int) Math.floor(rx + (fx - rx) * f), (int) Math.floor(ry + (fy - ry) * f), (int) Math.floor(rz + (fz - rz) * f), P.BARS);
            }
        }
        p.set((int) Math.floor(fx), (int) Math.floor(fy), (int) Math.floor(fz), P.IRON);
        p.set((int) Math.floor(fx), (int) Math.floor(fy) + 1, (int) Math.floor(fz), P.ROD);
    }

    /** Distance d'un bloc à un centre, dans le plan horizontal, en prenant le milieu des blocs. */
    private static double flat(int x, int z, double cx, double cz) {
        return Math.hypot(x + 0.5 - cx, z + 0.5 - cz);
    }

    // ------------------------------------------------------------------
    // Station météorologique
    // ------------------------------------------------------------------

    /** Le bâtiment d'observation : un volume à toit plat, son bureau des relevés, ses archives ; l'équipement du toit varie. */
    static void weatherHut(FacilityPiece p) {
        FacilityStyle s = p.style();
        p.base(0, 0, 8, 7, s.frame());
        p.air(0, 1, 0, 8, 9, 0);
        p.fill(1, 0, 2, 7, 0, 6, s.floor());
        p.room(1, 1, 2, 7, 3, 6);
        p.walls(0, 1, 1, 8, 3, 7, s.wall());
        corners(p, 0, 1, 1, 8, 3, 7);
        boolean wide = p.random.nextBoolean();
        for (int x : wide ? new int[] {1, 2, 3, 5, 6, 7} : new int[] {1, 2, 6, 7}) {
            p.set(x, 2, 1, P.GLASS);
        }
        for (int z = 3; z <= 5; z++) {
            p.set(0, 2, z, P.GLASS);
            p.set(8, 2, z, P.GLASS);
            p.set(z, 2, 7, P.GLASS);
        }
        p.door(4, 1, 1, OUT);
        roof(p, 0, 4, 1, 8, 7);
        p.air(1, 5, 2, 7, 9, 6);
        p.solid(2, 4, 4, P.LIGHT);
        p.solid(6, 4, 4, P.LIGHT);
        // Le poste : le bureau des relevés, la baie des données, les archives, un lutrin.
        p.set(4, 1, 6, P.console(OUT));
        p.set(5, 1, 6, P.rack(OUT));
        p.chest(7, 1, 6, LEFT);
        p.set(1, 1, 6, P.of(Blocks.BOOKSHELF));
        p.set(1, 2, 6, P.of(Blocks.BOOKSHELF));
        p.set(1, 1, 4, P.facing(Blocks.LECTERN, RIGHT));
        p.draftingTable(3, 1, 6, OUT, p.archiveReach());
        p.set(7, 1, 3, P.of(Blocks.BARREL));
        // Sur le toit : le mât de l'anémomètre, et le reste au hasard.
        mast(p, 7, 5, 6, 5);
        p.set(6, 8, 6, P.ROD);
        roofGear(p, 1, 5, 2, 5, 6);
    }

    /** Le mât météorologique : huit à dix mètres de treillis, deux bras, les coupelles de l'anémomètre et la girouette. */
    static void metMast(FacilityPiece p) {
        p.base(0, 0, 2, 2, P.CONCRETE);
        p.air(0, 1, 0, 2, 12, 2);
        p.set(1, 1, 1, P.CONCRETE);
        int top = 8 + p.random.nextInt(3);
        for (int y = 2; y <= top; y++) {
            p.set(1, y, 1, P.BARS);
        }
        p.set(0, top, 1, P.BARS);
        p.set(2, top, 1, P.BARS);
        p.set(0, top + 1, 1, P.ROD);
        p.set(2, top + 1, 1, P.ROD);
        p.set(1, top + 1, 1, P.BARS);
        p.set(1, top + 2, 1, P.ROD);
        p.set(1, 1, 0, P.LOUVER);
    }

    /** Le parc à instruments : un enclos sur l'herbe, l'abri de Stevenson, le pluviomètre, l'héliographe, le géothermomètre. */
    static void weatherGarden(FacilityPiece p) {
        p.air(0, 1, 0, 6, 3, 6);
        for (int z = 0; z <= 6; z++) {
            for (int x = 0; x <= 6; x++) {
                p.solid(x, 0, z, x == 3 && z <= 3 ? P.of(Blocks.GRAVEL) : P.of(Blocks.GRASS_BLOCK));
            }
        }
        for (int k = 0; k <= 6; k++) {
            p.set(k, 1, 6, P.of(Blocks.BIRCH_FENCE));
            p.set(0, 1, k, P.of(Blocks.BIRCH_FENCE));
            p.set(6, 1, k, P.of(Blocks.BIRCH_FENCE));
            if (k != 3) {
                p.set(k, 1, 0, P.of(Blocks.BIRCH_FENCE));
            }
        }
        p.set(3, 1, 0, Blocks.BIRCH_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, OUT));
        p.set(2, 1, 4, P.of(Blocks.BIRCH_FENCE));
        p.set(2, 2, 4, P.LOUVER);
        p.set(2, 3, 4, P.of(Blocks.SMOOTH_QUARTZ_SLAB));
        p.set(4, 1, 4, P.of(Blocks.FLOWER_POT));
        p.set(5, 1, 2, P.of(Blocks.BIRCH_FENCE));
        p.set(5, 2, 2, P.of(Blocks.DAYLIGHT_DETECTOR));
        p.set(1, 1, 2, P.ROD);
        if (p.random.nextBoolean()) {
            // Un second abri, et le bac d'évaporation.
            p.set(4, 1, 2, P.of(Blocks.BIRCH_FENCE));
            p.set(4, 2, 2, P.LOUVER);
            p.set(2, 1, 2, P.of(Blocks.FLOWER_POT));
        }
    }

    /** La tour radar : un fût, sa plate-forme et son garde-corps, le radôme blanc posé dessus, l'antenne dedans. */
    static void radarTower(FacilityPiece p) {
        FacilityStyle s = p.style();
        p.base(0, 0, 8, 8, P.CONCRETE);
        p.air(0, 1, 0, 8, 23, 8);
        p.walls(2, 1, 2, 6, 12, 6, s.frame());
        p.room(3, 1, 3, 5, 12, 5);
        p.door(4, 1, 2, OUT);
        p.set(2, 7, 4, P.GLASS);
        p.set(6, 7, 4, P.GLASS);
        p.chest(5, 1, 5, LEFT);
        for (int y = 1; y <= 13; y++) {
            p.solid(3, y, 5, P.ladder(OUT));
        }
        p.fill(0, 13, 0, 8, 13, 8, P.CONCRETE);
        p.solid(3, 13, 5, P.ladder(OUT));
        p.solid(4, 13, 4, P.LIGHT);
        parapet(p, 0, 14, 0, 8, 8, P.BARS);
        for (int y = 14; y <= 18; y++) {
            p.solid(4, y, 4, y == 14 ? P.CONCRETE : P.IRON);
        }
        // L'antenne du radar, sous son radôme : une grande plaque de grillage sur son axe.
        for (int k = -2; k <= 2; k++) {
            p.set(4 + k, 19, 4, P.MESH);
            if (Math.abs(k) < 2) {
                p.set(4 + k, 20, 4, P.MESH);
            }
        }
        for (int y = 16; y <= 23; y++) {
            for (int z = 0; z <= 8; z++) {
                for (int x = 0; x <= 8; x++) {
                    double d = Math.sqrt(Math.pow(x + 0.5 - 4.5, 2) + Math.pow(y + 0.5 - 19.5, 2) + Math.pow(z + 0.5 - 4.5, 2));
                    if (d > 2.9 && d <= 3.9) {
                        p.set(x, y, z, P.RADOME);
                    }
                }
            }
        }
    }

    /** L'abri des ballons-sondes : un hangar ouvert, un ballon à demi gonflé, les bouteilles d'hélium. */
    static void balloonShed(FacilityPiece p) {
        FacilityStyle s = p.style();
        p.base(0, 0, 6, 7, P.CONCRETE);
        p.air(0, 1, 0, 6, 6, 7);
        p.walls(0, 1, 1, 6, 4, 7, s.wall());
        corners(p, 0, 1, 1, 6, 4, 7);
        p.air(2, 1, 1, 4, 3, 1);
        p.fill(2, 4, 1, 4, 4, 1, s.accent());
        p.room(1, 1, 2, 5, 4, 6);
        roof(p, 0, 5, 1, 6, 7);
        p.solid(3, 5, 4, P.LIGHT);
        p.fill(2, 2, 4, 4, 4, 6, P.of(Blocks.WOOL.pick(DyeColor.WHITE)));
        for (int x : new int[] {2, 4}) {
            for (int y : new int[] {2, 4}) {
                for (int z : new int[] {4, 6}) {
                    p.air(x, y, z, x, y, z);
                }
            }
        }
        p.set(3, 1, 5, P.of(Blocks.IRON_CHAIN));
        p.chest(1, 1, 6, RIGHT);
        p.set(5, 1, 6, P.of(Blocks.BARREL));
        p.set(5, 1, 5, P.of(Blocks.BARREL));
        p.set(5, 1, 2, P.ROD);
        p.set(5, 1, 3, P.ROD);
    }

    // ------------------------------------------------------------------
    // Centre de recherche
    // ------------------------------------------------------------------

    /** Le laboratoire : deux niveaux, des poteaux, des fenêtres en bandeau, un bandeau de couleur ; un auvent sur l'entrée, ou non. */
    static void researchLab(FacilityPiece p) {
        FacilityStyle s = p.style();
        p.base(0, 0, 16, 11, s.frame());
        p.air(0, 1, 0, 16, 11, 1);
        p.fill(1, 0, 3, 15, 0, 10, s.floor());
        p.walls(0, 1, 2, 16, 3, 11, s.wall());
        p.fill(0, 4, 2, 16, 4, 11, s.frame());
        p.walls(0, 5, 2, 16, 7, 11, s.wall());
        p.room(1, 1, 3, 15, 3, 10);
        p.room(1, 5, 3, 15, 7, 10);
        int spacing = p.random.nextBoolean() ? 4 : 8;
        for (int floor : new int[] {1, 5}) {
            for (int x = 1; x <= 15; x++) {
                if (x % spacing != 0) {
                    p.set(x, floor + 1, 2, P.GLASS);
                    p.set(x, floor + 2, 2, P.GLASS);
                    p.set(x, floor + 1, 11, P.GLASS);
                    p.set(x, floor + 2, 11, P.GLASS);
                    if (floor == 5) {
                        p.set(x, 5, 2, s.accent());
                        p.set(x, 5, 11, s.accent());
                    }
                }
            }
            for (int z : new int[] {4, 5, 8, 9}) {
                p.set(0, floor + 1, z, P.GLASS);
                p.set(0, floor + 2, z, P.GLASS);
                p.set(16, floor + 1, z, P.GLASS);
                p.set(16, floor + 2, z, P.GLASS);
            }
        }
        for (int x = 0; x <= 16; x += spacing) {
            for (int y = 1; y <= 8; y++) {
                p.solid(x, y, 2, s.frame());
                p.solid(x, y, 11, s.frame());
            }
        }
        for (int y = 1; y <= 3; y++) {
            p.solid(7, y, 2, s.frame());
            p.solid(9, y, 2, s.frame());
            p.set(6, y, 2, P.GLASS);
            p.set(10, y, 2, P.GLASS);
        }
        p.set(8, 3, 2, P.GLASS);
        p.door(8, 1, 2, OUT);
        if (p.random.nextBoolean()) {
            p.fill(5, 4, 0, 11, 4, 1, P.SLAB);
            for (int y = 1; y <= 3; y++) {
                p.solid(5, y, 0, s.frame());
                p.solid(11, y, 0, s.frame());
            }
        }
        flight(p, 15, 1, 4, 4);
        p.air(15, 4, 4, 15, 4, 6);
        for (int z = 4; z <= 7; z++) {
            p.set(14, 5, z, P.BARS);
        }
        // Rez-de-chaussée : les paillasses, les bureaux des calculs, l'atelier.
        for (int x = 1; x <= 6; x++) {
            p.set(x, 1, 10, P.of(Blocks.SMOOTH_STONE));
        }
        p.set(2, 2, 10, P.of(Blocks.BREWING_STAND));
        p.set(5, 2, 10, P.of(Blocks.FLOWER_POT));
        p.set(8, 1, 10, P.bench(OUT));
        p.set(8, 2, 10, P.monitor());
        p.set(9, 1, 10, P.console(OUT));
        p.set(10, 1, 10, P.rack(OUT));
        p.set(12, 1, 10, P.of(Blocks.SMITHING_TABLE));
        p.set(13, 1, 10, P.of(Blocks.CRAFTING_TABLE));
        p.chest(14, 1, 10, LEFT);
        p.chest(1, 1, 4, RIGHT);
        if (p.random.nextBoolean()) {
            // Une paillasse centrale.
            for (int x = 5; x <= 10; x++) {
                p.set(x, 1, 6, P.bench(OUT));
                p.set(x, 1, 7, P.bench(IN));
            }
        }
        for (int x : new int[] {3, 7, 11}) {
            ceilingLight(p, x, 4, 5);
            ceilingLight(p, x, 4, 8);
        }
        // Étage : les bureaux, les archives.
        for (int x = 1; x <= 5; x++) {
            p.set(x, 5, 10, P.of(Blocks.BOOKSHELF));
            p.set(x, 6, 10, P.of(Blocks.BOOKSHELF));
        }
        p.set(3, 5, 7, P.facing(Blocks.LECTERN, OUT));
        p.draftingTable(7, 5, 10, OUT, p.archiveReach());
        for (int x = 8; x <= 11; x++) {
            p.set(x, 5, 9, P.bench(OUT));
            if (x % 2 == 0) {
                p.set(x, 6, 9, P.monitor());
            }
        }
        p.set(12, 5, 10, P.rack(OUT));
        p.chest(13, 5, 10, OUT);
        p.fill(6, 5, 4, 12, 5, 7, P.of(Blocks.CARPET.pick(DyeColor.LIGHT_GRAY)));
        roof(p, 0, 8, 2, 16, 11);
        p.air(1, 9, 3, 15, 11, 10);
        p.solid(3, 8, 5, P.LIGHT);
        p.solid(7, 8, 8, P.LIGHT);
        p.solid(11, 8, 5, P.LIGHT);
        roofGear(p, 2, 9, 4, 8, 9);
        roofGear(p, 9, 9, 4, 14, 9);
    }

    /** La remise : un bardage de couleur, une large baie, des pièces de fusée en attente. */
    static void storageShed(FacilityPiece p) {
        FacilityStyle s = p.style();
        p.base(0, 0, 6, 6, P.CONCRETE);
        p.air(0, 1, 0, 6, 5, 6);
        p.walls(0, 1, 1, 6, 3, 6, s.accent());
        corners(p, 0, 1, 1, 6, 3, 6);
        p.air(2, 1, 1, 4, 2, 1);
        p.room(1, 1, 2, 5, 3, 5);
        roof(p, 0, 4, 1, 6, 6);
        p.solid(3, 4, 4, P.LIGHT);
        p.chest(1, 1, 5, RIGHT);
        p.set(5, 1, 5, P.of(Blocks.BARREL));
        p.set(5, 1, 4, P.of(Blocks.BARREL));
        p.set(5, 2, 5, P.of(Blocks.BARREL));
        p.set(3, 1, 5, P.facing(ModBlocks.ROCKET_ENGINE_SOLID.get(), OUT));
        p.set(1, 1, 3, P.facing(ModBlocks.ROCKET_NOSE.get(), OUT));
    }

    /** Le banc d'essai : un massif de béton et sa tranchée, le déflecteur sous le moteur, la tour d'acier et ses planchers. */
    static void testStand(FacilityPiece p) {
        p.base(0, 0, 10, 10, P.CONCRETE);
        p.air(0, 1, 0, 10, 23, 10);
        p.fill(0, 1, 1, 10, 6, 10, P.CONCRETE);
        parapet(p, 0, 6, 1, 10, 10, P.HAZARD);
        p.air(4, 1, 0, 6, 4, 8);
        p.air(4, 5, 4, 6, 6, 6);
        p.fill(4, 0, 0, 6, 0, 8, P.facing(ModBlocks.FLAME_TRENCH.get(), OUT));
        p.fill(4, 1, 5, 6, 1, 6, P.facing(ModBlocks.FLAME_DEFLECTOR.get(), OUT));
        // L'étage en essai : un moteur kérosène ou un moteur à hydrogène, et ses réservoirs.
        p.set(5, 7, 5, P.facing(p.random.nextBoolean() ? ModBlocks.ROCKET_ENGINE_KEROLOX.get() : ModBlocks.ROCKET_ENGINE_HYDROLOX.get(), OUT));
        int tanks = 3 + p.random.nextInt(4);
        for (int y = 8; y < 8 + tanks; y++) {
            p.set(5, y, 5, P.facing(ModBlocks.ROCKET_TANK_ALUMINUM.get(), OUT));
        }
        for (int y = 7; y <= 22; y++) {
            p.solid(2, y, 2, P.FRAME);
            p.solid(8, y, 2, P.FRAME);
            p.solid(2, y, 8, P.FRAME);
            p.solid(8, y, 8, P.CONCRETE);
            p.solid(7, y, 8, P.ladder(LEFT));
        }
        for (int y : new int[] {11, 16, 21}) {
            for (int z = 2; z <= 8; z++) {
                for (int x = 2; x <= 8; x++) {
                    boolean hole = x >= 4 && x <= 6 && z >= 4 && z <= 6;
                    boolean post = (x == 2 || x == 8) && (z == 2 || z == 8);
                    if (!hole && !post && !(x == 7 && z == 8)) {
                        p.set(x, y, z, P.SLAB_TOP);
                    }
                }
            }
            for (int k = 3; k <= 7; k++) {
                p.set(k, y + 1, 2, P.BARS);
                p.set(2, y + 1, k, P.BARS);
                p.set(8, y + 1, k, P.BARS);
            }
        }
        p.set(2, 23, 2, P.of(ModBlocks.PAD_FLOODLIGHT.get()));
        p.set(8, 23, 2, P.of(ModBlocks.PAD_FLOODLIGHT.get()));
        p.chest(1, 7, 1, OUT);
    }

    /** Le hangar : une grande porte plus ou moins ouverte, des lanterneaux, un pont roulant, une fusée-sonde sur son bâti. */
    static void hangar(FacilityPiece p) {
        FacilityStyle s = p.style();
        p.base(0, 0, 14, 12, P.CONCRETE);
        p.air(0, 1, 0, 14, 10, 12);
        p.fill(1, 0, 2, 13, 0, 11, s.floor());
        p.walls(0, 1, 1, 14, 7, 12, s.wall());
        p.walls(0, 1, 1, 14, 1, 12, s.frame());
        corners(p, 0, 1, 1, 14, 7, 12);
        p.room(1, 1, 2, 13, 7, 11);
        int open = 1 + p.random.nextInt(3);
        p.air(3, 1, 1, 11, 6, 1);
        p.fill(3, 6, 1, 11, 6, 1, s.accent());
        p.fill(3, 1, 1, 3 + open, 5, 1, s.accent());
        p.fill(11 - open, 1, 1, 11, 5, 1, s.accent());
        for (int z = 3; z <= 10; z += 2) {
            p.set(0, 6, z, P.GLASS);
            p.set(14, 6, z, P.GLASS);
        }
        roof(p, 0, 8, 1, 14, 12);
        p.air(1, 9, 2, 13, 10, 11);
        for (int z = 4; z <= 10; z += 2) {
            p.fill(5, 8, z, 9, 8, z, P.GLASS);
        }
        for (int x : new int[] {3, 11}) {
            p.solid(x, 8, 4, P.LIGHT);
            p.solid(x, 8, 9, P.LIGHT);
        }
        p.set(7, 1, 8, P.facing(ModBlocks.ROCKET_ENGINE_SOLID.get(), OUT));
        int body = 2 + p.random.nextInt(3);
        for (int y = 2; y < 2 + body; y++) {
            p.set(7, y, 8, P.facing(ModBlocks.ROCKET_MOTOR_CASING.get(), OUT));
        }
        p.set(7, 2 + body, 8, P.facing(ModBlocks.ROCKET_NOSE.get(), OUT));
        for (int y = 1; y <= 5; y++) {
            p.set(5, y, 8, P.FRAME);
        }
        for (int x = 1; x <= 13; x++) {
            p.set(x, 7, 6, P.BARS);
        }
        p.set(7, 6, 6, P.of(Blocks.IRON_CHAIN));
        p.set(1, 1, 11, P.of(Blocks.CRAFTING_TABLE));
        p.set(2, 1, 11, P.of(Blocks.SMITHING_TABLE));
        p.set(3, 1, 11, P.facing(Blocks.STONECUTTER, OUT));
        p.set(4, 1, 11, P.bench(OUT));
        p.chest(13, 1, 11, LEFT);
        p.set(13, 1, 10, P.of(Blocks.BARREL));
        p.set(13, 1, 9, P.of(Blocks.BARREL));
        p.set(12, 1, 11, P.of(Blocks.BARREL));
    }

    // ------------------------------------------------------------------
    // Centre d'observation
    // ------------------------------------------------------------------

    /** La coupole : un tambour, la coupole nervurée ou blanche et sa fente, le télescope sur son pilier. */
    static void observatory(FacilityPiece p) {
        FacilityStyle s = p.style();
        double cx = 5.5;
        double cz = 6.5;
        BlockState dome = p.random.nextBoolean() ? P.SIDING : P.RADOME;
        p.base(0, 0, 10, 11, P.CONCRETE);
        p.air(0, 1, 0, 10, 11, 11);
        for (int z = 1; z <= 11; z++) {
            for (int x = 0; x <= 10; x++) {
                double d = flat(x, z, cx, cz);
                if (d <= 5.3) {
                    p.solid(x, 0, z, s.floor());
                    if (d > 4.3) {
                        for (int y = 1; y <= 4; y++) {
                            p.set(x, y, z, y == 4 ? s.accent() : s.wall());
                        }
                    }
                }
                for (int y = 5; y <= 11; y++) {
                    double r = Math.sqrt(d * d + Math.pow(y + 0.5 - 5.0, 2));
                    boolean slit = x == 5 && z <= 6 && y >= 7;
                    if (r > 4.3 && r <= 5.3 && !slit) {
                        p.set(x, y, z, dome);
                    }
                }
            }
        }
        p.door(5, 1, 1, OUT);
        p.solid(4, 10, 7, P.LIGHT);
        p.solid(2, 8, 6, P.LIGHT);
        p.solid(8, 8, 6, P.LIGHT);
        for (int y = 1; y <= 3; y++) {
            p.solid(5, y, 6, P.of(Blocks.POLISHED_ANDESITE));
        }
        p.solid(5, 4, 6, P.IRON);
        p.set(5, 5, 6, P.WHITE);
        p.set(5, 6, 5, P.WHITE);
        p.set(5, 7, 4, P.WHITE);
        p.set(5, 8, 3, P.of(Blocks.CONCRETE.pick(DyeColor.BLACK)));
        p.draftingTable(2, 1, 4, RIGHT, p.archiveReach());
        p.chest(8, 1, 4, LEFT);
        p.set(2, 1, 8, P.of(Blocks.BOOKSHELF));
        p.set(8, 1, 8, P.of(Blocks.CARTOGRAPHY_TABLE));
    }

    /** Le poste de contrôle : des fenêtres tout autour, la station de poursuite et son mur d'écrans, une antenne sur le toit. */
    static void controlHut(FacilityPiece p) {
        FacilityStyle s = p.style();
        p.base(0, 0, 8, 9, s.frame());
        p.air(0, 1, 0, 8, 9, 0);
        p.fill(1, 0, 2, 7, 0, 8, s.floor());
        p.room(1, 1, 2, 7, 4, 8);
        p.walls(0, 1, 1, 8, 4, 9, s.wall());
        corners(p, 0, 1, 1, 8, 4, 9);
        for (int k = 1; k <= 7; k++) {
            if (k != 4) {
                p.set(k, 2, 1, P.GLASS);
                p.set(k, 3, 1, P.GLASS);
            }
            if (k >= 3) {
                p.set(0, 2, k, P.GLASS);
                p.set(8, 2, k, P.GLASS);
            }
        }
        p.door(4, 1, 1, OUT);
        roof(p, 0, 5, 1, 8, 9);
        p.air(1, 6, 2, 7, 9, 8);
        p.solid(2, 5, 5, P.LIGHT);
        p.solid(6, 5, 5, P.LIGHT);
        // Le seul vrai poste du centre : la station de poursuite, contre le mur du fond, son mur d'écrans au-dessus.
        p.workstation(4, 1, 7, OUT, ModBlocks.TRACKING_STATION.get(), true);
        p.set(1, 1, 8, P.of(Blocks.BOOKSHELF));
        p.set(7, 1, 8, P.rack(OUT));
        p.chest(7, 1, 5, LEFT);
        miniDish(p, 6, 6, 6);
        mast(p, 2, 6, 7, 3);
    }

    /** La petite antenne : un pied, un étrier, une parabole de grillage penchée vers le ciel. */
    static void smallDish(FacilityPiece p) {
        p.base(0, 0, 6, 6, P.CONCRETE);
        p.air(0, 1, 0, 6, 8, 6);
        for (int y = 1; y <= 3; y++) {
            p.solid(3, y, 3, P.CONCRETE);
        }
        p.solid(3, 4, 3, P.IRON);
        dish(p, 3.5, 5.5, 3.5, 3.0, 2.0, 25.0 + p.random.nextInt(30), P.MESH);
        p.set(5, 1, 5, P.LOUVER);
    }

    /** Le radiotélescope : un bâtiment au pied, l'étrier, la grande parabole et son cornet au foyer. */
    static void radioDish(FacilityPiece p) {
        FacilityStyle s = p.style();
        p.base(0, 0, 14, 14, P.CONCRETE);
        p.air(0, 1, 0, 14, 17, 14);
        p.walls(5, 1, 5, 9, 4, 9, s.wall());
        corners(p, 5, 1, 5, 9, 4, 9);
        p.room(6, 1, 6, 8, 4, 8);
        p.fill(5, 5, 5, 9, 5, 9, P.CONCRETE);
        p.door(7, 1, 5, OUT);
        p.solid(7, 5, 7, P.LIGHT);
        p.chest(8, 1, 8, LEFT);
        p.set(6, 1, 8, P.rack(OUT));
        p.set(7, 1, 8, P.console(OUT));
        for (int y = 6; y <= 8; y++) {
            p.solid(7, y, 7, P.IRON);
        }
        p.solid(6, 7, 7, P.IRON);
        p.solid(8, 7, 7, P.IRON);
        dish(p, 7.5, 9.5, 7.5, 7.0, 4.2, 30.0 + p.random.nextInt(15), P.MESH);
    }

    // ------------------------------------------------------------------
    // Centre de contrôle
    // ------------------------------------------------------------------

    /**
     * La salle de tir : trois niveaux ; au premier, l'accueil et les bureaux ; au deuxième, la salle de tir, double
     * hauteur, sa rangée de bureaux tournée vers les grandes baies et le pas de tir, et ses deux pupitres de
     * lancement sous leurs murs d'écrans ; au troisième, les bureaux de la direction ; sur le toit, les antennes.
     */
    static void launchControl(FacilityPiece p) {
        FacilityStyle s = p.style();
        p.base(0, 0, 26, 13, s.frame());
        p.air(0, 1, 0, 26, 17, 1);
        p.fill(1, 0, 3, 25, 0, 12, s.floor());
        p.walls(0, 1, 2, 26, 3, 13, s.wall());
        p.fill(0, 4, 2, 26, 4, 13, s.frame());
        p.walls(0, 5, 2, 26, 9, 13, s.wall());
        p.fill(0, 10, 2, 26, 10, 13, s.frame());
        p.walls(0, 11, 2, 26, 13, 13, s.wall());
        p.fill(0, 14, 2, 26, 14, 13, s.roof());
        p.room(1, 1, 3, 25, 3, 12);
        p.room(1, 5, 3, 25, 9, 12);
        p.room(1, 11, 3, 25, 13, 12);
        for (int x = 1; x <= 25; x++) {
            p.set(x, 2, 2, P.GLASS);
            p.set(x, 3, 2, P.GLASS);
            p.set(x, 2, 13, P.GLASS);
            p.set(x, 3, 13, P.GLASS);
            p.set(x, 5, 2, s.accent());
            for (int y = 6; y <= 9; y++) {
                p.set(x, y, 2, P.GLASS);
            }
            p.set(x, 7, 13, P.GLASS);
            p.set(x, 8, 13, P.GLASS);
            p.set(x, 12, 2, P.GLASS);
            p.set(x, 13, 2, P.GLASS);
            p.set(x, 12, 13, P.GLASS);
            p.set(x, 13, 13, P.GLASS);
        }
        for (int z : new int[] {4, 5, 9, 10, 11}) {
            for (int y : new int[] {2, 3, 7, 8, 12, 13}) {
                p.set(0, y, z, P.GLASS);
                p.set(26, y, z, P.GLASS);
            }
        }
        for (int x : new int[] {0, 6, 20, 26}) {
            for (int y = 1; y <= 14; y++) {
                p.solid(x, y, 2, s.frame());
                p.solid(x, y, 13, s.frame());
            }
        }
        for (int y = 5; y <= 14; y++) {
            p.solid(13, y, 2, s.frame());
            p.solid(13, y, 13, s.frame());
        }
        for (int y = 1; y <= 14; y++) {
            p.solid(0, y, 7, s.frame());
            p.solid(26, y, 7, s.frame());
        }
        for (int y = 1; y <= 3; y++) {
            p.solid(12, y, 2, s.frame());
            p.solid(14, y, 2, s.frame());
        }
        p.set(13, 3, 2, P.GLASS);
        p.door(13, 1, 2, OUT);
        // L'auvent de l'entrée : une toiture pleine de béton coulé, portée à gauche par un poteau et sa vitre de côté,
        // à droite par un mur plein qui rejoint la façade.
        p.fill(9, 4, 0, 17, 4, 1, P.CONCRETE);
        for (int y = 1; y <= 3; y++) {
            p.solid(9, y, 0, s.frame());
            p.set(9, y, 1, P.GLASS);
            p.solid(17, y, 0, s.frame());
            p.solid(17, y, 1, s.frame());
        }
        flight(p, 25, 1, 4, 4);
        p.air(25, 4, 4, 25, 4, 6);
        flight(p, 24, 5, 4, 6);
        p.air(24, 10, 4, 24, 10, 8);
        // Premier niveau : l'accueil, deux bureaux.
        for (int x = 11; x <= 15; x++) {
            p.set(x, 1, 6, P.bench(IN));
        }
        p.set(12, 2, 6, P.monitor());
        p.set(14, 2, 6, P.monitor());
        for (int x : new int[] {7, 19}) {
            for (int z = 3; z <= 12; z++) {
                if (z != 5) {
                    p.fill(x, 1, z, x, 3, z, s.wall());
                }
            }
        }
        p.chest(1, 1, 12, RIGHT);
        for (int x = 2; x <= 4; x++) {
            p.set(x, 1, 12, P.of(Blocks.BOOKSHELF));
        }
        p.set(5, 1, 12, P.bench(OUT));
        p.set(5, 2, 12, P.monitor());
        p.set(3, 1, 8, P.facing(Blocks.LECTERN, OUT));
        p.chest(21, 1, 12, OUT);
        p.set(22, 1, 12, P.bench(OUT));
        p.set(22, 2, 12, P.monitor());
        p.set(23, 1, 12, P.rack(OUT));
        for (int x : new int[] {3, 10, 16, 22}) {
            ceilingLight(p, x, 4, 10);
        }
        for (int x : new int[] {3, 10, 16}) {
            ceilingLight(p, x, 4, 4);
        }
        // Deuxième niveau : la salle de tir. En bas, une rangée de bureaux face aux baies ; sur le gradin, les deux
        // pupitres de lancement et leurs murs d'écrans ; au fond, les baies de serveurs.
        p.fill(1, 5, 7, 22, 5, 12, P.CONCRETE);
        p.fill(1, 5, 3, 22, 5, 6, P.of(Blocks.CARPET.pick(DyeColor.GRAY)));
        for (int x = 2; x <= 21; x++) {
            if (x != 11 && x != 12) {
                p.set(x, 5, 4, P.console(IN));
            }
        }
        for (int x : new int[] {1, 11, 12, 22}) {
            p.set(x, 5, 6, P.stairs(IN));
        }
        for (int x = 1; x <= 22; x++) {
            p.set(x, 6, 11, P.of(Blocks.CARPET.pick(DyeColor.GRAY)));
        }
        p.workstation(7, 6, 9, IN, ModBlocks.LAUNCH_CONSOLE.get(), true);
        p.workstation(17, 6, 9, IN, ModBlocks.LAUNCH_CONSOLE.get(), true);
        for (int x = 11; x <= 13; x++) {
            p.set(x, 6, 10, P.console(IN));
        }
        for (int x : new int[] {2, 3, 4, 20, 21, 22}) {
            p.set(x, 6, 12, P.rack(OUT));
        }
        for (int x : new int[] {3, 9, 15, 21}) {
            ceilingLight(p, x, 10, 5);
            ceilingLight(p, x, 10, 11);
        }
        // Troisième niveau : la direction.
        for (int x = 1; x <= 4; x++) {
            p.set(x, 11, 12, P.of(Blocks.BOOKSHELF));
            p.set(x, 12, 12, P.of(Blocks.BOOKSHELF));
        }
        p.set(6, 11, 9, P.facing(Blocks.LECTERN, OUT));
        p.draftingTable(8, 11, 12, OUT, p.archiveReach());
        for (int x = 10; x <= 15; x++) {
            p.set(x, 11, 10, P.bench(OUT));
            if (x % 2 == 0) {
                p.set(x, 12, 10, P.monitor());
            }
        }
        p.chest(12, 11, 12, OUT);
        p.set(18, 11, 12, P.rack(OUT));
        p.fill(8, 11, 4, 18, 11, 8, P.of(Blocks.CARPET.pick(DyeColor.LIGHT_BLUE)));
        for (int x : new int[] {4, 11, 18}) {
            p.solid(x, 14, 6, P.LIGHT);
        }
        // Le toit : un bandeau sur la façade, l'acrotère, les antennes.
        parapet(p, 0, 15, 2, 26, 13, P.SLAB);
        for (int x = 0; x <= 26; x++) {
            p.set(x, 15, 2, s.accent());
        }
        p.air(1, 15, 3, 25, 17, 12);
        mast(p, 3, 15, 10, 3);
        mast(p, 23, 15, 10, 3);
        miniDish(p, 8, 15, 9);
        miniDish(p, 18, 15, 9);
        p.fill(11, 15, 8, 15, 16, 11, P.LOUVER);
    }

    /** Le bâtiment d'assemblage : un haut volume, sa porte géante plus ou moins levée, les plates-formes de travail, une fusée dedans. */
    static void assemblyBuilding(FacilityPiece p) {
        FacilityStyle s = p.style();
        p.base(0, 0, 14, 13, P.CONCRETE);
        p.air(0, 1, 0, 14, 25, 0);
        p.walls(0, 1, 1, 14, 23, 13, P.SIDING);
        p.walls(0, 1, 1, 14, 3, 13, s.frame());
        corners(p, 0, 1, 1, 14, 23, 13);
        p.room(1, 1, 2, 13, 23, 12);
        for (int y = 21; y <= 23; y++) {
            for (int x = 1; x <= 13; x++) {
                p.set(x, y, 1, s.accent());
            }
            for (int z = 2; z <= 12; z++) {
                p.set(0, y, z, s.accent());
                p.set(14, y, z, s.accent());
            }
        }
        for (int y = 6; y <= 19; y++) {
            p.set(2, y, 1, P.LOUVER);
            p.set(12, y, 1, P.LOUVER);
        }
        int raised = 11 + p.random.nextInt(6);
        for (int y = 1; y <= 20; y++) {
            p.solid(4, y, 1, s.frame());
            p.solid(10, y, 1, s.frame());
        }
        p.fill(4, 20, 1, 10, 20, 1, s.frame());
        p.air(5, 1, 1, 9, raised, 1);
        p.fill(5, raised + 1, 1, 9, 19, 1, P.SIDING);
        roof(p, 0, 24, 1, 14, 13);
        p.air(1, 25, 2, 13, 25, 12);
        for (int x : new int[] {3, 7, 11}) {
            p.solid(x, 24, 4, P.LIGHT);
            p.solid(x, 24, 10, P.LIGHT);
        }
        // La fusée en cours d'assemblage : son premier étage, plus ou moins haut, l'interétage, le second, la coiffe.
        p.set(7, 1, 7, P.facing(ModBlocks.ROCKET_ENGINE_KEROLOX.get(), OUT));
        int first = 6 + p.random.nextInt(3);
        for (int y = 2; y < 2 + first; y++) {
            p.set(7, y, 7, P.facing(ModBlocks.ROCKET_TANK_ALUMINUM.get(), OUT));
        }
        int y = 2 + first;
        p.set(7, y++, 7, P.facing(ModBlocks.ROCKET_INTERSTAGE.get(), OUT));
        for (int k = 0; k < 3; k++) {
            p.set(7, y++, 7, P.facing(ModBlocks.ROCKET_TANK_ALUMINUM.get(), OUT));
        }
        p.set(7, y++, 7, P.facing(ModBlocks.ROCKET_PAYLOAD_BAY.get(), OUT));
        p.set(7, y++, 7, P.facing(ModBlocks.ROCKET_FAIRING.get(), OUT));
        p.set(7, y, 7, P.facing(ModBlocks.ROCKET_NOSE.get(), OUT));
        for (int k = 1; k <= 18; k++) {
            p.solid(4, k, 5, P.FRAME);
            p.solid(4, k, 9, P.FRAME);
            p.solid(10, k, 5, P.FRAME);
            p.solid(10, k, 9, P.FRAME);
            p.solid(10, k, 7, P.CONCRETE);
            p.solid(11, k, 7, P.ladder(RIGHT));
        }
        for (int level : new int[] {6, 12, 18}) {
            for (int z = 5; z <= 9; z++) {
                for (int x = 4; x <= 10; x++) {
                    boolean hole = x >= 6 && x <= 8 && z >= 6 && z <= 8;
                    boolean post = (x == 4 || x == 10) && (z == 5 || z == 9 || z == 7 && x == 10);
                    if (!hole && !post) {
                        p.set(x, level, z, P.SLAB_TOP);
                    }
                }
            }
        }
        p.chest(1, 1, 12, RIGHT);
        p.chest(13, 1, 12, LEFT);
        p.set(1, 1, 11, P.of(Blocks.BARREL));
        p.set(13, 1, 11, P.of(Blocks.BARREL));
        p.set(2, 1, 12, P.bench(OUT));
        p.draftingTable(3, 1, 12, OUT, p.archiveReach());
    }

    /** Le château d'eau : quatre pieds en treillis, le réservoir blanc et sa bande de couleur. */
    static void waterTower(FacilityPiece p) {
        FacilityStyle s = p.style();
        p.base(0, 0, 6, 6, P.CONCRETE);
        p.air(0, 1, 0, 6, 15, 6);
        int[][] legs = {{1, 1}, {5, 1}, {1, 5}, {5, 5}};
        for (int[] leg : legs) {
            for (int y = 1; y <= 9; y++) {
                p.set(leg[0], y, leg[1], P.BARS);
            }
        }
        for (int k = 2; k <= 4; k++) {
            p.set(k, 5, 1, P.BARS);
            p.set(k, 5, 5, P.BARS);
            p.set(1, 5, k, P.BARS);
            p.set(5, 5, k, P.BARS);
        }
        for (int y = 1; y <= 9; y++) {
            p.set(3, y, 3, P.BARS);
        }
        for (int y = 10; y <= 14; y++) {
            for (int z = 0; z <= 6; z++) {
                for (int x = 0; x <= 6; x++) {
                    double d = flat(x, z, 3.5, 3.5);
                    if (y == 10 && d <= 3.2 || y == 14 && d <= 2.6 || y > 10 && y < 14 && d <= 3.2 && d > 2.2) {
                        p.set(x, y, z, y == 12 ? s.accent() : P.WHITE);
                    }
                }
            }
        }
        p.set(3, 15, 3, P.ROD);
    }

    /**
     * Le pas de tir : une aire de béton, la butte et sa rampe, les rayures de sécurité, la dalle de lancement et
     * l'ouverture sous la fusée, la tranchée des flammes et son déflecteur, la tour de service et son bras d'accès,
     * le paratonnerre, les projecteurs, et les deux sphères d'ergols.
     */
    static void launchPad(FacilityPiece p) {
        p.base(0, 0, 24, 24, P.CONCRETE);
        p.air(0, 1, 0, 24, 30, 24);
        p.fill(5, 1, 5, 19, 3, 19, P.CONCRETE);
        for (int x = 9; x <= 15; x++) {
            p.solid(x, 1, 2, P.stairs(IN));
            p.solid(x, 1, 3, P.CONCRETE);
            p.solid(x, 2, 3, P.stairs(IN));
            p.solid(x, 1, 4, P.CONCRETE);
            p.solid(x, 2, 4, P.CONCRETE);
            p.solid(x, 3, 4, P.stairs(IN));
        }
        parapet(p, 5, 3, 5, 19, 19, P.HAZARD);
        p.fill(9, 3, 9, 15, 3, 15, P.of(ModBlocks.LAUNCH_PAD.get()));
        p.air(11, 1, 11, 13, 3, 13);
        p.air(11, 1, 14, 13, 2, 24);
        p.fill(11, 0, 11, 13, 0, 24, P.facing(ModBlocks.FLAME_TRENCH.get(), IN));
        p.fill(11, 1, 11, 13, 1, 11, P.facing(ModBlocks.FLAME_DEFLECTOR.get(), IN));
        for (int z = 20; z <= 24; z++) {
            p.set(10, 1, z, P.CONCRETE);
            p.set(14, 1, z, P.CONCRETE);
        }
        p.set(10, 4, 12, P.facing(ModBlocks.DELUGE_PIPE.get(), RIGHT));
        int top = 21 + p.random.nextInt(5);
        for (int y = 4; y <= top; y++) {
            p.solid(15, y, 11, P.FRAME);
            p.solid(17, y, 11, P.FRAME);
            p.solid(15, y, 13, P.FRAME);
            p.solid(17, y, 13, P.FRAME);
            p.solid(16, y, 12, P.CONCRETE);
            p.solid(15, y, 12, P.ladder(LEFT));
        }
        for (int y = 8; y <= top - 1; y += 4) {
            p.set(16, y, 11, P.SLAB_TOP);
            p.set(16, y, 13, P.SLAB_TOP);
            p.set(17, y, 12, P.SLAB_TOP);
            p.set(16, y + 1, 11, P.BARS);
            p.set(16, y + 1, 13, P.BARS);
            p.set(17, y + 1, 12, P.BARS);
        }
        p.set(14, 20, 12, P.SLAB_TOP);
        p.set(13, 20, 12, P.SLAB_TOP);
        p.set(14, 21, 11, P.BARS);
        p.set(14, 21, 13, P.BARS);
        for (int y = top + 1; y <= top + 3; y++) {
            p.set(16, y, 12, P.BARS);
        }
        p.set(16, top + 4, 12, P.ROD);
        for (int[] corner : new int[][] {{5, 5}, {19, 5}, {5, 19}, {19, 19}}) {
            p.set(corner[0], 4, corner[1], P.of(ModBlocks.PAD_FLOODLIGHT.get()));
        }
        p.chest(18, 4, 15, LEFT);
        for (double sx : new double[] {2.5, 22.5}) {
            p.solid((int) sx, 1, 21, P.CONCRETE);
            p.solid((int) sx, 2, 21, P.CONCRETE);
            for (int y = 2; y <= 7; y++) {
                for (int z = 18; z <= 24; z++) {
                    for (int x = (int) sx - 3; x <= (int) sx + 3; x++) {
                        if (x < 0 || x > 24) {
                            continue;
                        }
                        double d = Math.sqrt(Math.pow(x + 0.5 - sx, 2) + Math.pow(y + 0.5 - 5.0, 2) + Math.pow(z + 0.5 - 21.5, 2));
                        if (d <= 2.4) {
                            p.set(x, y, z, P.WHITE);
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Annexes, tirées au hasard
    // ------------------------------------------------------------------

    /** L'antenne-relais : un pylône de treillis, ses antennes à plusieurs hauteurs, le local technique au pied. */
    static void commsMast(FacilityPiece p) {
        p.base(0, 0, 4, 4, P.CONCRETE);
        p.air(0, 1, 0, 4, 23, 4);
        int top = 16 + p.random.nextInt(5);
        for (int y = 1; y <= top; y++) {
            p.solid(2, y, 2, P.FRAME);
        }
        for (int[] anchor : new int[][] {{0, 0}, {4, 0}, {0, 4}, {4, 4}}) {
            p.set(anchor[0], 1, anchor[1], P.CONCRETE);
        }
        for (int y = 8; y <= top - 3; y += 5) {
            p.set(1, y, 2, P.MESH);
            p.set(3, y + 2, 2, P.MESH);
            p.set(2, y + 1, 1, P.MESH);
        }
        for (int y = top + 1; y <= top + 2; y++) {
            p.set(2, y, 2, P.BARS);
        }
        p.set(2, top + 3, 2, P.ROD);
        p.fill(3, 1, 3, 4, 2, 4, P.LOUVER);
    }

    /** Le dépôt de carburant : deux ou trois réservoirs verticaux dans leur cuvette de rétention, leurs conduites. */
    static void fuelFarm(FacilityPiece p) {
        FacilityStyle s = p.style();
        p.base(0, 0, 10, 8, P.CONCRETE);
        p.air(0, 1, 0, 10, 6, 8);
        parapet(p, 0, 1, 0, 10, 8, P.CONCRETE);
        p.air(5, 1, 0, 5, 1, 0);
        int count = 2 + p.random.nextInt(2);
        double[] centres = count == 2 ? new double[] {3.0, 8.0} : new double[] {2.5, 5.5, 8.5};
        double radius = count == 2 ? 1.9 : 1.4;
        for (double cx : centres) {
            for (int y = 1; y <= 5; y++) {
                for (int z = 1; z <= 7; z++) {
                    for (int x = 1; x <= 9; x++) {
                        if (flat(x, z, cx, 5.0) <= radius + (y == 5 ? -0.6 : 0)) {
                            p.set(x, y, z, y == 3 ? s.accent() : P.WHITE);
                        }
                    }
                }
            }
            for (int z = 1; z <= 3; z++) {
                p.set((int) cx, 2, z, P.BARS);
            }
        }
        for (int x = 1; x <= 9; x++) {
            p.set(x, 2, 1, P.BARS);
        }
        p.set(9, 1, 1, P.HAZARD);
    }

    /** Le champ solaire : des rangées de panneaux sur le gravier, les onduleurs. */
    static void solarFarm(FacilityPiece p) {
        p.air(0, 1, 0, 8, 2, 8);
        for (int z = 0; z <= 8; z++) {
            for (int x = 0; x <= 8; x++) {
                p.solid(x, 0, z, z % 2 == 0 ? P.of(Blocks.GRAVEL) : P.of(Blocks.GRASS_BLOCK));
            }
        }
        for (int z = 1; z <= 7; z += 2) {
            for (int x = 1; x <= 7; x++) {
                p.set(x, 1, z, P.of(ModBlocks.SOLAR_PANEL.get()));
            }
        }
        p.set(8, 1, 0, P.LOUVER);
        p.set(8, 1, 8, P.LOUVER);
    }

    /** Le parc de stockage : une aire de béton, ses marquages, des fûts, des pièces de fusée en attente, une caisse. */
    static void storageYard(FacilityPiece p) {
        p.base(0, 0, 8, 8, P.CONCRETE);
        p.air(0, 1, 0, 8, 3, 8);
        parapet(p, 0, 0, 0, 8, 8, P.HAZARD);
        for (int x = 1; x <= 7; x += 2) {
            int high = 1 + p.random.nextInt(2);
            for (int y = 1; y <= high; y++) {
                p.set(x, y, 7, P.of(Blocks.BARREL));
            }
        }
        Block[] parts = {ModBlocks.ROCKET_TANK_STEEL.get(), ModBlocks.ROCKET_NOSE.get(), ModBlocks.ROCKET_INTERSTAGE.get(),
                ModBlocks.ROCKET_ENGINE_PRESSURE.get(), ModBlocks.ROCKET_MOTOR_CASING.get()};
        for (int x = 2; x <= 6; x += 2) {
            p.set(x, 1, 4, P.facing(parts[p.random.nextInt(parts.length)], OUT));
        }
        p.chest(7, 1, 2, LEFT);
        p.set(1, 1, 2, P.of(Blocks.BARREL));
    }

    /** Le groupe de paraboles : trois petites antennes sur leurs pieds, chacune pointée un peu ailleurs. */
    static void dishArray(FacilityPiece p) {
        p.base(0, 0, 12, 8, P.CONCRETE);
        p.air(0, 1, 0, 12, 6, 8);
        for (double cx : new double[] {2.5, 6.5, 10.5}) {
            int x = (int) cx;
            p.solid(x, 1, 4, P.CONCRETE);
            p.solid(x, 2, 4, P.IRON);
            dish(p, cx, 3.5, 4.5, 2.0, 1.5, 20.0 + p.random.nextInt(35), P.MESH);
        }
        p.set(12, 1, 8, P.LOUVER);
    }

    /** L'hélistation : une dalle, son cercle jaune, la lettre H, ses balises. */
    static void helipad(FacilityPiece p) {
        p.base(0, 0, 8, 8, P.GRAY);
        p.air(0, 1, 0, 8, 1, 8);
        for (int z = 0; z <= 8; z++) {
            for (int x = 0; x <= 8; x++) {
                double d = flat(x, z, 4.5, 4.5);
                if (d > 3.3 && d <= 4.3) {
                    p.set(x, 0, z, P.HAZARD);
                }
            }
        }
        for (int z = 2; z <= 6; z++) {
            p.set(3, 0, z, P.WHITE);
            p.set(5, 0, z, P.WHITE);
        }
        p.set(4, 0, 4, P.WHITE);
        for (int[] corner : new int[][] {{0, 0}, {8, 0}, {0, 8}, {8, 8}}) {
            p.set(corner[0], 0, corner[1], P.GROUND_LIGHT);
        }
    }

    /** Le poste de garde : une guérite vitrée, son bureau, et la barrière levée. */
    static void guardHouse(FacilityPiece p) {
        FacilityStyle s = p.style();
        p.base(0, 0, 4, 5, s.frame());
        p.air(0, 1, 0, 4, 4, 0);
        p.room(1, 1, 2, 3, 2, 4);
        p.walls(0, 1, 1, 4, 2, 5, s.wall());
        corners(p, 0, 1, 1, 4, 2, 5);
        for (int k = 1; k <= 3; k++) {
            p.set(k, 2, 5, P.GLASS);
            p.set(0, 2, k + 1, P.GLASS);
            p.set(4, 2, k + 1, P.GLASS);
        }
        p.set(1, 2, 1, P.GLASS);
        p.door(2, 1, 1, OUT);
        roof(p, 0, 3, 1, 4, 5);
        p.air(1, 4, 2, 3, 4, 4);
        p.solid(2, 3, 3, P.LIGHT);
        p.set(2, 1, 4, P.console(OUT));
        p.set(3, 1, 4, P.rack(LEFT));
        p.set(0, 1, 0, s.accent());
        p.set(0, 2, 0, P.BARS);
        p.set(0, 3, 0, P.BARS);
    }

    /** Des bureaux : un plain-pied à fenêtres en bandeau, une ou deux rangées de bureaux, les archives. */
    static void officeAnnex(FacilityPiece p) {
        FacilityStyle s = p.style();
        p.base(0, 0, 10, 8, s.frame());
        p.air(0, 1, 0, 10, 5, 0);
        p.fill(1, 0, 2, 9, 0, 7, s.floor());
        p.room(1, 1, 2, 9, 3, 7);
        p.walls(0, 1, 1, 10, 3, 8, s.wall());
        corners(p, 0, 1, 1, 10, 3, 8);
        for (int x = 1; x <= 9; x++) {
            if (x != 5) {
                p.set(x, 2, 1, P.GLASS);
            }
            p.set(x, 2, 8, P.GLASS);
            p.set(x, 3, 1, s.accent());
        }
        p.door(5, 1, 1, OUT);
        roof(p, 0, 4, 1, 10, 8);
        p.air(1, 5, 2, 9, 5, 7);
        for (int x : new int[] {2, 5, 8}) {
            p.solid(x, 4, 4, P.LIGHT);
        }
        int rows = 1 + p.random.nextInt(2);
        for (int r = 0; r < rows; r++) {
            for (int x = 2; x <= 8; x++) {
                if (x != 5) {
                    p.set(x, 1, 4 + r * 2, P.bench(OUT));
                    if (x % 3 != 0) {
                        p.set(x, 2, 4 + r * 2, P.monitor());
                    }
                }
            }
        }
        p.set(1, 1, 7, P.of(Blocks.BOOKSHELF));
        p.draftingTable(3, 1, 7, OUT, p.archiveReach());
        p.set(9, 1, 7, P.rack(OUT));
        p.chest(8, 1, 7, OUT);
        roofGear(p, 1, 5, 2, 9, 7);
    }
}
