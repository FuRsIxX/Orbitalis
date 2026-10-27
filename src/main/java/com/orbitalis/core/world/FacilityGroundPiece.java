package com.orbitalis.core.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.registry.ModStructures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * Le terrain commun d'un centre spatial : l'esplanade et son mât de drapeau, les allées de béton jusqu'à chaque
 * bâtiment (la piste de gravier jusqu'au pas de tir), les lampadaires, l'enceinte grillagée et son entrée. Il couvre
 * tout le site, qui s'aplanit sous lui. Il se dessine en coordonnées du monde.
 */
public class FacilityGroundPiece extends StructurePiece {
    private final FacilityKind kind;
    /** Les allées : x0, z0, x1, z1, largeur, à la suite. */
    private final int[] paths;
    /** Les emprises des bâtiments : x et z minimaux, puis maximaux, à la suite. */
    private final int[] rects;
    private final int gateX;
    private final int gateZ;
    private final Direction gate;
    private final boolean abandoned;
    private final long seed;

    FacilityGroundPiece(FacilityKind kind, BoundingBox site, List<int[]> paths, List<int[]> rects, int gateX, int gateZ, Direction gate,
            boolean abandoned, long seed) {
        super(ModStructures.FACILITY_GROUND.get(), 0, site);
        setOrientation(null);
        this.kind = kind;
        this.paths = flatten(paths);
        this.rects = flatten(rects);
        this.gateX = gateX;
        this.gateZ = gateZ;
        this.gate = gate;
        this.abandoned = abandoned;
        this.seed = seed;
    }

    public FacilityGroundPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(ModStructures.FACILITY_GROUND.get(), tag);
        this.kind = FacilityKind.CODEC.byName(tag.getStringOr("Kind", ""), FacilityKind.WEATHER_STATION);
        this.paths = tag.getIntArray("Paths").orElse(new int[0]);
        this.rects = tag.getIntArray("Rects").orElse(new int[0]);
        this.gateX = tag.getIntOr("GateX", 0);
        this.gateZ = tag.getIntOr("GateZ", 0);
        this.gate = Direction.from2DDataValue(tag.getIntOr("Gate", 0));
        this.abandoned = tag.getBooleanOr("Abandoned", false);
        this.seed = tag.getLongOr("Seed", 0L);
    }

    private static int[] flatten(List<int[]> list) {
        List<Integer> all = new ArrayList<>();
        for (int[] entry : list) {
            for (int value : entry) {
                all.add(value);
            }
        }
        return all.stream().mapToInt(Integer::intValue).toArray();
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("Kind", kind.getSerializedName());
        tag.putIntArray("Paths", paths);
        tag.putIntArray("Rects", rects);
        tag.putInt("GateX", gateX);
        tag.putInt("GateZ", gateZ);
        tag.putInt("Gate", gate.get2DDataValue());
        tag.putBoolean("Abandoned", abandoned);
        tag.putLong("Seed", seed);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB,
            ChunkPos chunkPos, BlockPos referencePos) {
        int y = boundingBox.minY();
        Set<Long> paved = new HashSet<>();
        // L'esplanade : un carré de carrelage, son mât et son drapeau.
        int cx = paths.length > 0 ? paths[0] : boundingBox.getCenter().getX();
        int cz = paths.length > 1 ? paths[1] : boundingBox.getCenter().getZ();
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                pave(level, chunkBB, cx + dx, y, cz + dz, ModBlocks.FACILITY_TILES.get().defaultBlockState(), paved);
            }
        }
        for (int k = 1; k <= 5; k++) {
            put(level, chunkBB, cx + 3, y + k, cz + 3, Blocks.IRON_BARS.defaultBlockState());
        }
        put(level, chunkBB, cx + 3, y + 6, cz + 3, Blocks.CONCRETE.pick(DyeColor.LIGHT_GRAY).defaultBlockState());
        if (!abandoned || hash(cx, y, cz, 5) < 0.5) {
            put(level, chunkBB, cx + 4, y + 6, cz + 3,
                    Blocks.WALL_BANNER.pick(DyeColor.BLUE).defaultBlockState().setValue(WallBannerBlock.FACING, Direction.EAST));
        }
        // Les allées, puis leurs lampadaires.
        for (int k = 0; k + 4 < paths.length; k += 5) {
            int x0 = paths[k];
            int z0 = paths[k + 1];
            int x1 = paths[k + 2];
            int z1 = paths[k + 3];
            int width = paths[k + 4];
            boolean road = width > 3;
            int half = width / 2;
            int length = Math.abs(x1 - x0) + Math.abs(z1 - z0);
            int sx = Integer.signum(x1 - x0);
            int sz = Integer.signum(z1 - z0);
            for (int t = 0; t <= length; t++) {
                int x = x0 + sx * t;
                int z = z0 + sz * t;
                for (int o = -half; o <= half; o++) {
                    int px = sz != 0 ? x + o : x;
                    int pz = sx != 0 ? z + o : z;
                    BlockState surface = road ? (Math.abs(o) == half ? ModBlocks.FACILITY_CONCRETE.get().defaultBlockState()
                            : Blocks.GRAVEL.defaultBlockState()) : ModBlocks.FACILITY_CONCRETE.get().defaultBlockState();
                    pave(level, chunkBB, px, y, pz, surface, paved);
                }
                if (t % 9 == 4 && t < length - 2) {
                    int lx = sz != 0 ? x + half + 1 : x;
                    int lz = sx != 0 ? z + half + 1 : z;
                    lamp(level, chunkBB, lx, y, lz);
                }
            }
        }
        // L'enceinte : un grillage de deux blocs, des poteaux de béton aux angles, l'entrée et ses piliers.
        for (int x = boundingBox.minX(); x <= boundingBox.maxX(); x++) {
            fence(level, chunkBB, x, y, boundingBox.minZ(), paved);
            fence(level, chunkBB, x, y, boundingBox.maxZ(), paved);
        }
        for (int z = boundingBox.minZ() + 1; z < boundingBox.maxZ(); z++) {
            fence(level, chunkBB, boundingBox.minX(), y, z, paved);
            fence(level, chunkBB, boundingBox.maxX(), y, z, paved);
        }
        for (int[] corner : new int[][] {{boundingBox.minX(), boundingBox.minZ()}, {boundingBox.maxX(), boundingBox.minZ()},
                {boundingBox.minX(), boundingBox.maxZ()}, {boundingBox.maxX(), boundingBox.maxZ()}}) {
            for (int k = 1; k <= 3; k++) {
                put(level, chunkBB, corner[0], y + k, corner[1], ModBlocks.FACILITY_CONCRETE.get().defaultBlockState());
            }
        }
        Direction side = gate.getClockWise();
        for (int s : new int[] {-3, 3}) {
            int px = gateX + side.getStepX() * s;
            int pz = gateZ + side.getStepZ() * s;
            for (int k = 1; k <= 3; k++) {
                put(level, chunkBB, px, y + k, pz, ModBlocks.FACILITY_CONCRETE.get().defaultBlockState());
            }
            put(level, chunkBB, px, y + 4, pz, abandoned && hash(px, y, pz, 9) < 0.6 ? Blocks.AIR.defaultBlockState()
                    : Blocks.LANTERN.defaultBlockState());
        }
    }

    /** Une case d'allée : le revêtement, et le passage dégagé au-dessus. Dans un centre abandonné, l'herbe reprend ses droits. */
    private void pave(WorldGenLevel level, BoundingBox chunkBB, int x, int y, int z, BlockState surface, Set<Long> paved) {
        if (!boundingBox.isInside(x, y, z) || insideBuilding(x, z, 0)) {
            return;
        }
        paved.add(BlockPos.asLong(x, 0, z));
        if (abandoned) {
            double h = hash(x, y, z, 1);
            surface = h < 0.14 ? Blocks.GRASS_BLOCK.defaultBlockState() : h < 0.24 ? Blocks.COARSE_DIRT.defaultBlockState()
                    : h < 0.34 && surface.is(ModBlocks.FACILITY_CONCRETE.get()) ? ModBlocks.CRACKED_FACILITY_CONCRETE.get().defaultBlockState() : surface;
        }
        put(level, chunkBB, x, y, z, surface);
        for (int k = 1; k <= 3; k++) {
            if (abandoned && k == 1 && surface.is(Blocks.GRASS_BLOCK) && hash(x, y, z, 2) < 0.5) {
                put(level, chunkBB, x, y + k, z, Blocks.SHORT_GRASS.defaultBlockState());
            } else {
                put(level, chunkBB, x, y + k, z, Blocks.AIR.defaultBlockState());
            }
        }
    }

    /** Un lampadaire : un fût de pierre, une lanterne au sommet ; éteint, ou cassé, dans un centre abandonné. */
    private void lamp(WorldGenLevel level, BoundingBox chunkBB, int x, int y, int z) {
        if (!boundingBox.isInside(x, y, z) || insideBuilding(x, z, 1) || x == boundingBox.minX() || x == boundingBox.maxX()
                || z == boundingBox.minZ() || z == boundingBox.maxZ()) {
            return;
        }
        boolean broken = abandoned && hash(x, y, z, 4) < 0.4;
        int height = broken ? 1 + (int) (hash(x, y, z, 6) * 2) : 3;
        put(level, chunkBB, x, y, z, ModBlocks.FACILITY_CONCRETE.get().defaultBlockState());
        for (int k = 1; k <= height; k++) {
            put(level, chunkBB, x, y + k, z, Blocks.ANDESITE_WALL.defaultBlockState());
        }
        if (!broken) {
            put(level, chunkBB, x, y + 4, z, Blocks.LANTERN.defaultBlockState());
        }
    }

    /** Une travée de grillage, sauf à l'entrée et là où passe une allée. */
    private void fence(WorldGenLevel level, BoundingBox chunkBB, int x, int y, int z, Set<Long> paved) {
        int along = gate.getAxis() == Direction.Axis.Z ? Math.abs(x - gateX) : Math.abs(z - gateZ);
        boolean onGateSide = gate.getAxis() == Direction.Axis.Z ? z == gateZ : x == gateX;
        if (onGateSide && along <= 2 || paved.contains(BlockPos.asLong(x, 0, z))) {
            return;
        }
        if (abandoned && hash(x, y, z, 8) < 0.3) {
            return;
        }
        put(level, chunkBB, x, y, z, ModBlocks.FACILITY_CONCRETE.get().defaultBlockState());
        put(level, chunkBB, x, y + 1, z, Blocks.IRON_BARS.defaultBlockState());
        put(level, chunkBB, x, y + 2, z, Blocks.IRON_BARS.defaultBlockState());
    }

    private boolean insideBuilding(int x, int z, int margin) {
        for (int k = 0; k + 3 < rects.length; k += 4) {
            if (x >= rects[k] - margin && x <= rects[k + 2] + margin && z >= rects[k + 1] - margin && z <= rects[k + 3] + margin) {
                return true;
            }
        }
        return false;
    }

    private void put(WorldGenLevel level, BoundingBox chunkBB, int x, int y, int z, BlockState state) {
        if (chunkBB.isInside(x, y, z)) {
            BlockPos pos = new BlockPos(x, y, z);
            level.setBlock(pos, state, 2);
            if (state.is(Blocks.IRON_BARS) || state.is(Blocks.ANDESITE_WALL)) {
                level.getChunk(pos).markPosForPostProcessing(pos);
            }
        }
    }

    private double hash(int x, int y, int z, int salt) {
        long h = seed ^ BlockPos.asLong(x, y, z) * 0x9E3779B97F4A7C15L ^ salt * 0xC2B2AE3D27D4EB4FL;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }
}
