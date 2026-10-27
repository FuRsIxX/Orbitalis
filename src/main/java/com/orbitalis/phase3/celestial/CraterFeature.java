package com.orbitalis.phase3.celestial;

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
 * Cratère d'impact lunaire : une cuvette creusée dans le sol, plus profonde en son centre, entourée d'un bourrelet
 * de régolithe rejeté par l'impact. Le fond des plus grands cratères, toujours à l'ombre, garde parfois de la
 * glace : c'est la seule eau de la Lune.
 */
public class CraterFeature extends Feature<NoneFeatureConfiguration> {
    /** Rayons du plus petit et du plus grand cratère, en blocs. */
    private static final int MIN_RADIUS = 3;
    private static final int MAX_RADIUS = 7;
    /** Rayon à partir duquel un cratère peut garder de la glace en son fond. */
    private static final int ICE_RADIUS = 5;

    public CraterFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        int radius = MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1);
        // Le cratère est fait de la poussière du monde où il se creuse. Seuls ceux de la Lune gardent de la glace.
        boolean martian = level.getLevel().dimension() == com.orbitalis.phase2.survival.SpaceRules.MARS;
        boolean icy = !martian && radius >= ICE_RADIUS && random.nextInt(3) == 0;
        BlockState regolith = (martian ? ModBlocks.MARS_REGOLITH : ModBlocks.MOON_REGOLITH).get().defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int dx = -radius - 2; dx <= radius + 2; dx++) {
            for (int dz = -radius - 2; dz <= radius + 2; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > radius + 1.6) {
                    continue;
                }
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                // Hauteur du sol à cet endroit : premier bloc libre au-dessus de la surface.
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                if (distance > radius) {
                    // Bourrelet : une épaisseur de régolithe posée sur le sol.
                    pos.set(x, surface, z);
                    if (level.getBlockState(pos.below()).isSolid()) {
                        level.setBlock(pos, regolith, Block.UPDATE_CLIENTS);
                    }
                    continue;
                }
                // Cuvette : en forme de bol, sa profondeur au centre vaut un peu moins de la moitié de son rayon.
                double ratio = distance / radius;
                int depth = (int) Math.round((1.0 - ratio * ratio) * radius * 0.45);
                if (depth <= 0) {
                    continue;
                }
                for (int y = surface - 1; y >= surface - depth; y--) {
                    level.setBlock(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                // Le fond est tapissé de régolithe, ou de glace au cœur d'un cratère qui en garde.
                pos.set(x, surface - depth - 1, z);
                if (level.getBlockState(pos).isSolid()) {
                    boolean ice = icy && distance <= 1.5;
                    level.setBlock(pos, ice ? ModBlocks.LUNAR_ICE.get().defaultBlockState() : regolith, Block.UPDATE_CLIENTS);
                }
            }
        }
        return true;
    }
}
