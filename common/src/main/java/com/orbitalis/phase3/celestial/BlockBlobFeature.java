package com.orbitalis.phase3.celestial;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * Un amas de blocs posé sur le sol (les blocs de glace de Titan) : le « block_blob » de 26.2, que 1.21.1 n'a pas. Comme le
 * rocher des forêts du jeu, mais posé sur les blocs que dit {@code can_place_on}.
 */
public final class BlockBlobFeature extends Feature<BlockBlobFeature.Config> {
    public record Config(BlockState state, BlockPredicate canPlaceOn) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockState.CODEC.fieldOf("state").forGetter(Config::state),
                BlockPredicate.CODEC.fieldOf("can_place_on").forGetter(Config::canPlaceOn))
                .apply(instance, Config::new));
    }

    public BlockBlobFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        BlockPos pos = context.origin();
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        Config config = context.config();
        // On descend jusqu'au premier bloc qui peut porter l'amas.
        for (; pos.getY() > level.getMinBuildHeight() + 3; pos = pos.below()) {
            if (!level.isEmptyBlock(pos.below()) && config.canPlaceOn().test(level, pos.below())) {
                break;
            }
        }
        if (pos.getY() <= level.getMinBuildHeight() + 3) {
            return false;
        }
        for (int blob = 0; blob < 3; blob++) {
            int dx = random.nextInt(2);
            int dy = random.nextInt(2);
            int dz = random.nextInt(2);
            float radius = (dx + dy + dz) * 0.333F + 0.5F;
            for (BlockPos at : BlockPos.betweenClosed(pos.offset(-dx, -dy, -dz), pos.offset(dx, dy, dz))) {
                if (at.distSqr(pos) <= radius * radius) {
                    level.setBlock(at, config.state(), 3);
                }
            }
            pos = pos.offset(-1 + random.nextInt(2), -random.nextInt(2), -1 + random.nextInt(2));
        }
        return true;
    }
}
