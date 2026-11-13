package com.orbitalis.core.item;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.architectury.core.item.ArchitecturyBucketItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Le seau de méthane liquide. Versé ailleurs que sur Titan, le méthane bout : il s'en va en fumée, comme l'eau qu'on verse
 * dans le Nether. Il ne reste liquide que dans le froid de cette lune.
 */
public class MethaneBucketItem extends ArchitecturyBucketItem {
    public MethaneBucketItem(Supplier<? extends Fluid> fluid, Properties properties) {
        super(fluid, properties);
    }

    @Override
    public boolean emptyContents(@Nullable Player player, Level level, BlockPos pos, @Nullable BlockHitResult hit) {
        if (!com.orbitalis.phase2.survival.SpaceRules.isTitan(level)) {
            level.playSound(player, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F,
                    2.6F + (level.random.nextFloat() - level.random.nextFloat()) * 0.8F);
            for (int k = 0; k < 8; k++) {
                level.addParticle(ParticleTypes.LARGE_SMOKE, pos.getX() + level.random.nextDouble(), pos.getY() + level.random.nextDouble(),
                        pos.getZ() + level.random.nextDouble(), 0.0, 0.0, 0.0);
            }
            return true;
        }
        return super.emptyContents(player, level, pos, hit);
    }
}
