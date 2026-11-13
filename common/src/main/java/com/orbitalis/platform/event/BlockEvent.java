package com.orbitalis.platform.event;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/** Un bloc est posé par une entité. */
public final class BlockEvent {
    private BlockEvent() {
    }

    public static final class EntityPlaceEvent extends Cancelable {
        private final LevelAccessor level;
        private final BlockPos pos;
        private final BlockState placed;
        private final @Nullable Entity entity;

        public EntityPlaceEvent(LevelAccessor level, BlockPos pos, BlockState placed, @Nullable Entity entity) {
            this.level = level;
            this.pos = pos;
            this.placed = placed;
            this.entity = entity;
        }

        public LevelAccessor getLevel() {
            return level;
        }

        public BlockPos getPos() {
            return pos;
        }

        public BlockState getPlacedBlock() {
            return placed;
        }

        public @Nullable Entity getEntity() {
            return entity;
        }
    }
}
