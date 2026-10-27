package com.orbitalis.phase1.spaceport;

import com.orbitalis.core.registry.ModEntities;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Socle de l'ascenseur de la tour : une plaque que l'on pose au sol, au centre de la tour. Elle fait venir la
 * cabine ({@link ElevatorCabinEntity}), qui reste liée à elle : casser le socle retire la cabine.
 * <p>
 * Posé ailleurs, le socle donne quand même une cabine, mais elle n'a alors nulle part où monter.
 */
public class ElevatorBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);

    public ElevatorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this) && level instanceof ServerLevel serverLevel) {
            summonCabin(serverLevel, pos);
        }
    }

    /** Clic droit : appelle la cabine au sol, rappelle le mode d'emploi, et fait revenir la cabine si elle a disparu. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            summonCabin(serverLevel, pos);
            ElevatorCabinEntity.call(serverLevel, pos, pos.getY());
            serverPlayer.sendSystemMessage(Component.translatable("spaceport.orbitalis.elevator.hint").withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResult.SUCCESS;
    }

    /** Crée la cabine de ce socle, s'il n'en a pas déjà une. */
    private static void summonCabin(ServerLevel level, BlockPos pos) {
        AABB shaft = new AABB(pos).inflate(3.0, LaunchComplex.MAX_TOWER_HEIGHT, 3.0);
        if (!level.getEntitiesOfClass(ElevatorCabinEntity.class, shaft, cabin -> pos.equals(cabin.getHome())).isEmpty()) {
            return;
        }
        ElevatorCabinEntity cabin = ModEntities.ELEVATOR_CABIN.get().create(level, EntitySpawnReason.TRIGGERED);
        if (cabin != null) {
            cabin.setHome(pos);
            cabin.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            level.addFreshEntity(cabin);
        }
    }
}
