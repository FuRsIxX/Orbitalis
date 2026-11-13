package com.orbitalis.core.world;

import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.registry.ModStructures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * Un bâtiment d'un centre spatial. Il se dessine dans son propre repère, que la génération du monde tourne comme il
 * faut : x vers la droite quand on regarde la façade, y vers le haut (0 est la dalle de fondation, au niveau du sol),
 * z de la façade (z = 0, son parvis) vers le fond. Dans ce repère, la façade regarde le sud.
 * <p>
 * Un bâtiment abandonné s'use en se dessinant : béton fissuré, bardage rouillé, vitres et lampes brisées, toiles
 * d'araignée. L'usure de chaque bloc ne dépend que de sa place : le bâtiment est le même d'un tronçon de terrain à
 * l'autre.
 */
public class FacilityPiece extends StructurePiece {
    /** Les sens, dans le repère du bâtiment : vers le dehors par la façade, vers le fond, à droite, à gauche. */
    static final Direction OUT = Direction.SOUTH;
    static final Direction IN = Direction.NORTH;
    static final Direction RIGHT = Direction.EAST;
    static final Direction LEFT = Direction.WEST;

    private final FacilityBuilding building;
    private final FacilityKind kind;
    private final boolean abandoned;
    private final long seed;
    /** Le style du centre (voir {@link FacilityStyle}) : le même pour tous ses bâtiments. */
    private final int style;

    private WorldGenLevel level;
    private BoundingBox chunk;
    private RandomSource lootRandom;
    /** Le hasard du bâtiment : le même à chaque tronçon. */
    RandomSource random;

    public FacilityPiece(FacilityBuilding building, FacilityKind kind, int x, int y, int z, Direction orientation, boolean abandoned, long seed,
            int style) {
        super(ModStructures.FACILITY_BUILDING.get(), 0, makeBoundingBox(x, y, z, orientation, building.width(), building.height(), building.depth()));
        setOrientation(orientation);
        this.building = building;
        this.kind = kind;
        this.abandoned = abandoned;
        this.seed = seed;
        this.style = style;
    }

    public FacilityPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(ModStructures.FACILITY_BUILDING.get(), tag);
        this.building = FacilityBuilding.byName(tag.getStringOr("Building", ""));
        this.kind = FacilityKind.CODEC.byName(tag.getStringOr("Kind", ""), FacilityKind.WEATHER_STATION);
        this.abandoned = tag.getBooleanOr("Abandoned", false);
        this.seed = tag.getLongOr("Seed", 0L);
        this.style = tag.getIntOr("Style", 0);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("Building", building.name());
        tag.putString("Kind", kind.getSerializedName());
        tag.putBoolean("Abandoned", abandoned);
        tag.putLong("Seed", seed);
        tag.putInt("Style", style);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB,
            ChunkPos chunkPos, BlockPos referencePos) {
        this.level = level;
        this.chunk = chunkBB;
        this.lootRandom = random;
        this.random = RandomSource.create(seed);
        building.build(this);
        this.level = null;
    }

    boolean abandoned() {
        return abandoned;
    }

    public FacilityBuilding building() {
        return building;
    }

    /** La place, dans le monde, d'un bloc du repère du bâtiment. */
    public BlockPos worldPos(int x, int y, int z) {
        return getWorldPos(x, y, z).immutable();
    }

    /** Le côté où regarde la façade, dans le monde. */
    public Direction front() {
        Direction orientation = getOrientation();
        return orientation == null ? OUT : orientation.getOpposite();
    }

    FacilityKind kind() {
        return kind;
    }

    FacilityStyle style() {
        return FacilityStyle.of(style);
    }

    int width() {
        return building.width();
    }

    int height() {
        return building.height();
    }

    int depth() {
        return building.depth();
    }

    // ------------------------------------------------------------------
    // Dessin
    // ------------------------------------------------------------------

    /** Pose un bloc, usé si le bâtiment est abandonné. */
    void set(int x, int y, int z, BlockState state) {
        placeBlock(level, abandoned ? decay(state, x, y, z) : state, x, y, z, chunk);
    }

    /** Pose un bloc tel quel, même dans un bâtiment abandonné (la structure qui tient le reste). */
    void solid(int x, int y, int z, BlockState state) {
        placeBlock(level, state, x, y, z, chunk);
    }

    void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                    set(x, y, z, state);
                }
            }
        }
    }

    /** Les quatre murs d'un volume, sans sol ni plafond. */
    void walls(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                set(x, y, z0, state);
                set(x, y, z1, state);
            }
            for (int z = z0 + 1; z < z1; z++) {
                set(x0, y, z, state);
                set(x1, y, z, state);
            }
        }
    }

    /** Vide un volume : le terrain, les arbres, l'herbe. */
    void air(int x0, int y0, int z0, int x1, int y1, int z1) {
        clear(x0, y0, z0, x1, y1, z1, false);
    }

    /** Vide l'intérieur d'une pièce. Dans un bâtiment abandonné, des toiles d'araignée s'y accrochent, sous le plafond. */
    void room(int x0, int y0, int z0, int x1, int y1, int z1) {
        clear(x0, y0, z0, x1, y1, z1, abandoned);
    }

    private void clear(int x0, int y0, int z0, int x1, int y1, int z1, boolean webs) {
        int top = Math.max(y0, y1);
        for (int y = Math.min(y0, y1); y <= top; y++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                    boolean web = webs && y == top && hash(x, y, z, 7) < 0.06;
                    placeBlock(level, web ? Blocks.COBWEB.defaultBlockState() : Blocks.AIR.defaultBlockState(), x, y, z, chunk);
                }
            }
        }
    }

    /** La dalle de fondation (y = 0), et sous elle, des piliers jusqu'au sol là où il manque. */
    void base(int x0, int z0, int x1, int z1, BlockState state) {
        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                solid(x, 0, z, state);
                fillColumnDown(level, ModBlocks.FACILITY_CONCRETE.get().defaultBlockState(), x, -1, z, chunk);
            }
        }
    }

    /** Une porte en acier à un battant, sa façade vers {@code facing}, et une plaque de pression de chaque côté pour l'ouvrir. */
    void door(int x, int y, int z, Direction facing) {
        if (abandoned && hash(x, y, z, 11) < 0.45) {
            // Arrachée : il ne reste que l'embrasure.
            air(x, y, z, x, y + 1, z);
            return;
        }
        BlockState door = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
        solid(x, y, z, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        solid(x, y + 1, z, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        // Une plaque de pression devant la porte, dehors et dedans : elle s'ouvre quand on avance, et le mur reste tel quel.
        BlockState plate = Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE.defaultBlockState();
        solid(x + dx(facing), y, z + dz(facing), plate);
        solid(x - dx(facing), y, z - dz(facing), plate);
    }

    /** Le pas d'un sens le long de x et de z, dans le repère du bâtiment (où z monte vers le nord). */
    static int dx(Direction direction) {
        return direction.getStepX();
    }

    static int dz(Direction direction) {
        return -direction.getStepZ();
    }

    /**
     * Un grand poste de travail (pupitre de lancement, station de poursuite) : son bloc au milieu de l'avant, ses
     * écrans tournés vers {@code facing}, et les huit cases invisibles qui lui donnent sa solidité, comme quand un
     * joueur le pose. Il faut lui laisser la place : trois blocs de large, deux de haut, deux de profondeur. Dans un
     * centre abandonné, il a parfois été emporté.
     *
     * @param screens vrai pour poser au-dessus de lui un mur d'écrans de trois blocs sur deux, qui affiche ce qu'il mesure
     */
    void workstation(int x, int y, int z, Direction facing, Block block, boolean screens) {
        if (abandoned && hash(x, y, z, 13) < 0.4) {
            return;
        }
        solid(x, y, z, block.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,
                facing));
        Direction side = facing.getClockWise();
        Direction back = facing.getOpposite();
        BlockState part = ModBlocks.WORKSTATION_PART.get().defaultBlockState();
        solid(x + dx(side), y, z + dz(side), part);
        solid(x - dx(side), y, z - dz(side), part);
        for (int across = -1; across <= 1; across++) {
            int wx = x + dx(back) + across * dx(side);
            int wz = z + dz(back) + across * dz(side);
            solid(wx, y, wz, part);
            solid(wx, y + 1, wz, part);
            if (screens) {
                for (int up = 2; up <= 3; up++) {
                    shaped(wx, y + up, wz, ModBlocks.MISSION_DISPLAY.get().defaultBlockState()
                            .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, facing));
                }
            }
        }
        if (screens) {
            // Toutes les dalles posées, chacune s'accorde à ses voisines : l'écran ne fait plus qu'un.
            for (int across = -1; across <= 1; across++) {
                for (int up = 2; up <= 3; up++) {
                    BlockPos pos = getWorldPos(x + dx(back) + across * dx(side), y + up, z + dz(back) + across * dz(side));
                    if (chunk.isInside(pos)) {
                        level.setBlock(pos, Block.updateFromNeighbourShapes(level.getBlockState(pos), level, pos), 2);
                    }
                }
            }
        }
    }

    /**
     * Pose un bloc qui s'accorde à ses voisins (une dalle d'écran : ses bords disparaissent là où l'écran continue) :
     * la génération du monde n'accorde pas les blocs qu'elle pose, celui-ci le sera une fois le tronçon achevé.
     */
    private void shaped(int x, int y, int z, BlockState state) {
        solid(x, y, z, state);
        BlockPos pos = getWorldPos(x, y, z);
        if (chunk.isInside(pos)) {
            // Un écran qui chevauche deux tronçons s'accorde aussi au voisin posé plus tard, quand le tronçon s'achève.
            if (!(level.getChunk(pos) instanceof net.minecraft.world.level.chunk.LevelChunk)) {
                level.getChunk(pos).markPosForPostProcessing(pos);
            }
            // Un écran de centre spatial : en veille, sans la marche à suivre des écrans qu'un joueur vient de poser.
            if (level.getBlockEntity(pos) instanceof com.orbitalis.phase1.spaceport.MissionDisplayBlockEntity display) {
                display.setStandby(true);
            }
        }
    }

    /**
     * Une table à dessin. Dans un centre en service, un plan d'archive y est souvent resté étalé ; dans un centre
     * abandonné, rarement.
     *
     * @param maxTemplate le plus avancé des modèles du catalogue que le plan peut décrire
     */
    void draftingTable(int x, int y, int z, Direction facing, int maxTemplate) {
        boolean plan = hash(x, y, z, 17) < (abandoned ? 0.25 : 0.6);
        solid(x, y, z, ModBlocks.DRAFTING_TABLE.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, facing)
                .setValue(com.orbitalis.phase1.rocket.DraftingTableBlock.HAS_PLAN, plan));
        BlockPos pos = getWorldPos(x, y, z);
        if (plan && chunk.isInside(pos) && level.getBlockEntity(pos) instanceof com.orbitalis.phase1.rocket.DraftingTableBlockEntity table) {
            table.setPlan(ArchiveBlueprintFunction.archive(RandomSource.create(seed ^ pos.asLong()), maxTemplate));
        }
    }

    /** Le plus avancé des modèles qu'un plan d'archive de ce centre peut décrire. */
    int archiveReach() {
        return switch (kind) {
            case WEATHER_STATION -> 1;
            case OBSERVATION_CENTER -> 2;
            case RESEARCH_CENTER -> 3;
            case CONTROL_CENTER -> 5;
        };
    }

    /** Pose ce bloc seulement si la place est vide (un tapis sur un plancher, sans écraser ce qui y est déjà). */
    void cover(int x, int y, int z, BlockState state) {
        if (getBlock(level, x, y, z, chunk).isAir()) {
            set(x, y, z, state);
        }
    }

    /** Un coffre et ce qu'il contient : celui du centre, ou ce qu'il en reste s'il est abandonné. */
    void chest(int x, int y, int z, Direction facing) {
        BlockPos pos = getWorldPos(x, y, z);
        BlockState state = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing).mirror(getMirror()).rotate(getRotation());
        createChest(level, chunk, lootRandom, pos, abandoned ? FacilityKind.abandonedLoot() : kind.loot(), state);
    }

    /** Un hasard fixe, entre 0 et 1, propre à cette place du bâtiment (et à un usage, pour ne pas tout lier). */
    double hash(int x, int y, int z, int salt) {
        BlockPos pos = getWorldPos(x, y, z);
        long h = seed ^ pos.asLong() * 0x9E3779B97F4A7C15L ^ salt * 0xC2B2AE3D27D4EB4FL;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** L'usure d'un bloc d'un bâtiment abandonné. */
    private BlockState decay(BlockState state, int x, int y, int z) {
        if (state.isAir()) {
            return state;
        }
        double h = hash(x, y, z, 3);
        Block block = state.getBlock();
        if (block == ModBlocks.FACILITY_GLASS.get() || state.is(Blocks.GLASS_PANE) || state.is(Blocks.GLASS)) {
            return h < 0.5 ? Blocks.AIR.defaultBlockState() : state;
        }
        if (block == ModBlocks.FACILITY_CONCRETE.get() || block == ModBlocks.FACILITY_TILES.get()) {
            if (h < 0.025 && y >= 2) {
                return Blocks.AIR.defaultBlockState();
            }
            return h < 0.22 ? ModBlocks.CRACKED_FACILITY_CONCRETE.get().defaultBlockState() : h < 0.25 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : state;
        }
        if (block == ModBlocks.FACILITY_SIDING.get() || block == ModBlocks.BLUE_FACILITY_SIDING.get() || block == ModBlocks.LOUVERED_PANEL.get()) {
            return h < 0.04 && y >= 2 ? Blocks.AIR.defaultBlockState() : h < 0.4 ? ModBlocks.RUSTY_FACILITY_SIDING.get().defaultBlockState() : state;
        }
        if (block == ModBlocks.RADOME_PANEL.get() || block == ModBlocks.ANTENNA_MESH.get()) {
            return h < 0.3 ? Blocks.AIR.defaultBlockState() : state;
        }
        if (state.getLightEmission() > 0 || state.is(BlockTags.WOOL_CARPETS) || state.is(Blocks.IRON_BARS)) {
            return h < 0.45 ? Blocks.AIR.defaultBlockState() : state;
        }
        if (block == ModBlocks.SERVER_RACK.get() || state.is(Blocks.POLISHED_BLACKSTONE_STAIRS) || state.is(Blocks.LECTERN) || state.is(Blocks.BOOKSHELF)) {
            return h < 0.4 ? Blocks.AIR.defaultBlockState() : state;
        }
        if (block == ModBlocks.ORANGE_FACILITY_SIDING.get()) {
            return h < 0.3 ? ModBlocks.RUSTY_FACILITY_SIDING.get().defaultBlockState() : state;
        }
        if ((state.is(BlockTags.TERRACOTTA) || state.is(Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.LIGHT_GRAY))) && y >= 2 && h < 0.03) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }
}
