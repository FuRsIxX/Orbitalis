package com.orbitalis.phase2.station;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Générateur d'air : le bloc qui rend une station habitable. Posé dans une pièce close et alimenté en
 * électricité, il la pressurise. Un clic droit affiche son état : sous tension ou non, volume pressurisé ou fuite.
 */
public class LifeSupportBlock extends Block implements EntityBlock {
    /** Vrai quand la pièce est pressurisée : le voyant du bloc passe au vert. */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    /** Côté client : lance le ronronnement de ce générateur (voir StationSounds). Rien sur un serveur. */
    public static java.util.function.Consumer<net.minecraft.core.BlockPos> hum = pos -> {
    };

    public LifeSupportBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LifeSupportBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof LifeSupportBlockEntity unit && tickLevel instanceof ServerLevel serverLevel) {
                unit.serverTick(serverLevel, pos, tickState);
            }
        };
    }

    /** En marche, de petites bouffées d'air sortent des grilles des ventilateurs, et l'on entend un souffle. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (!state.getValue(ACTIVE)) {
            return;
        }
        if (random.nextInt(3) == 0) {
            net.minecraft.core.Direction side = net.minecraft.core.Direction.Plane.HORIZONTAL.getRandomDirection(random);
            double x = pos.getX() + 0.5 + side.getStepX() * 0.58 + (random.nextDouble() - 0.5) * 0.4;
            double y = pos.getY() + 0.3 + random.nextDouble() * 0.45;
            double z = pos.getZ() + 0.5 + side.getStepZ() * 0.58 + (random.nextDouble() - 0.5) * 0.4;
            level.addParticle(com.orbitalis.core.registry.ModParticles.VAPOR.get(), x, y, z, side.getStepX() * 0.02, 0.005, side.getStepZ() * 0.02);
        }
        // Le ronronnement du générateur : une boucle, que le client tient tant qu'il tourne.
        hum.accept(pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof LifeSupportBlockEntity unit) {
            Component message = switch (unit.getStatus()) {
                case NO_POWER -> Component.translatable("station.orbitalis.air.no_power", LifeSupportBlockEntity.ENERGY_PER_TICK)
                        .withStyle(ChatFormatting.RED);
                case LEAK -> Component.translatable("station.orbitalis.air.leak", StationAir.MAX_VOLUME).withStyle(ChatFormatting.GOLD);
                case PRESSURIZED -> Component.translatable("station.orbitalis.air.pressurized", unit.getVolume()).withStyle(ChatFormatting.GREEN);
            };
            serverPlayer.sendSystemMessage(message);
        }
        return InteractionResult.SUCCESS;
    }
}
