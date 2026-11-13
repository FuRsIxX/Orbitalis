package com.orbitalis.phase3.celestial;

import java.util.HashSet;
import java.util.Set;

import com.orbitalis.core.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Le relief de la banquise d'Europe, sur une plaine de glace presque lisse :
 * <ul>
 * <li><b>les lineae</b> : de longues traînées brun-rouge, chargées de sels remontés de l'océan, tracées au creux d'une
 * double crête de glace, et bordées de deux liserés sombres ;</li>
 * <li><b>les terrains chaotiques</b> : des plaques de glace brisées, soulevées ou affaissées, où la glace brun-rouge
 * affleure entre les blocs.</li>
 * </ul>
 * Chaque élément est centré sur le milieu de son tronçon et ne déborde pas des tronçons voisins.
 */
public class EuropaTerrainFeature extends Feature<NoneFeatureConfiguration> {
    private final boolean chaos;

    public EuropaTerrainFeature(boolean chaos) {
        super(NoneFeatureConfiguration.CODEC);
        this.chaos = chaos;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        int cx = (origin.getX() & ~15) + 8;
        int cz = (origin.getZ() & ~15) + 8;
        if (chaos) {
            placeChaos(level, random, cx, cz);
        } else {
            placeLinea(level, random, cx, cz);
        }
        return true;
    }

    /** Une linea : une traînée sinueuse, sa double crête et ses deux liserés, sur une trentaine de blocs. */
    private static void placeLinea(WorldGenLevel level, RandomSource random, int cx, int cz) {
        BlockState ice = ModBlocks.EUROPA_ICE.get().defaultBlockState();
        BlockState salt = ModBlocks.EUROPA_LINEA_ICE.get().defaultBlockState();
        double angle = random.nextDouble() * Math.PI;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        int half = 10 + random.nextInt(9);
        int width = random.nextInt(3) == 0 ? 1 : 0;
        double phase = random.nextDouble() * Math.PI * 2.0;
        Set<Long> done = new HashSet<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (double t = -half; t <= half; t += 0.5) {
            double bend = Math.sin(t * 0.12 + phase) * 1.6;
            double lx = cx + cos * t - sin * bend;
            double lz = cz + sin * t + cos * bend;
            for (int d = -width - 2; d <= width + 2; d++) {
                int x = (int) Math.floor(lx - sin * d);
                int z = (int) Math.floor(lz + cos * d);
                if (!done.add(BlockPos.asLong(x, 0, z))) {
                    continue;
                }
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                int side = Math.abs(d);
                if (side <= width) {
                    // Le creux de la linea, brun-rouge.
                    level.setBlock(pos.set(x, surface - 1, z), salt, Block.UPDATE_CLIENTS);
                } else if (side == width + 1) {
                    // La double crête : la glace soulevée d'un bloc de part et d'autre.
                    level.setBlock(pos.set(x, surface, z), ice, Block.UPDATE_CLIENTS);
                } else if (random.nextInt(3) > 0) {
                    // Les liserés sombres, de chaque côté, un peu effilochés.
                    level.setBlock(pos.set(x, surface - 1, z), salt, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** Un terrain chaotique : des plaques de glace de trois blocs, soulevées ou affaissées, et la glace salée entre elles. */
    private static void placeChaos(WorldGenLevel level, RandomSource random, int cx, int cz) {
        BlockState ice = ModBlocks.EUROPA_ICE.get().defaultBlockState();
        BlockState salt = ModBlocks.EUROPA_LINEA_ICE.get().defaultBlockState();
        int radius = 5 + random.nextInt(5);
        long seed = random.nextLong();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz) + random.nextDouble() * 1.5;
                if (distance > radius) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                // Chaque plaque (un carré de trois blocs) a sa propre hauteur, de un bloc plus bas à deux plus haut.
                long cell = seed ^ (Math.floorDiv(x, 3) * 341873128712L) ^ (Math.floorDiv(z, 3) * 132897987541L);
                int lift = (int) Math.floorMod(cell >>> 7, 4L) - 1;
                boolean crack = Math.floorMod(x, 3) == 0 || Math.floorMod(z, 3) == 0;
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                if (crack) {
                    // Entre les plaques, la glace salée affleure, un peu en creux.
                    level.setBlock(pos.set(x, surface - 1, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    level.setBlock(pos.set(x, surface - 2, z), salt, Block.UPDATE_CLIENTS);
                    continue;
                }
                if (lift < 0) {
                    level.setBlock(pos.set(x, surface - 1, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                } else {
                    for (int k = 0; k < lift; k++) {
                        level.setBlock(pos.set(x, surface + k, z), ice, Block.UPDATE_CLIENTS);
                    }
                }
                if (random.nextInt(4) == 0) {
                    level.setBlock(pos.set(x, surface - 1 + Math.max(lift, 0) - (lift < 0 ? 1 : 0), z), salt, Block.UPDATE_CLIENTS);
                }
            }
        }
    }
}
