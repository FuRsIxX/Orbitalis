package com.orbitalis.phase1.fuel;

import java.util.Map;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

/**
 * Réservoir modulable : un bloc de réservoir, qui s'assemble avec ses voisins en un seul grand réservoir (voir
 * {@link FluidTankBlockEntity}). De chaque côté, le bloc retient si le réservoir continue : c'est ce qui dessine
 * ses parois, ses arêtes et ses fonds, et seulement là où le réservoir s'arrête.
 * <p>
 * Un seau se vide ou se remplit dessus ; à main nue, un clic droit annonce son contenu.
 */
public class FluidTankBlock extends Block implements EntityBlock {
    public static final MapCodec<FluidTankBlock> CODEC = simpleCodec(FluidTankBlock::new);
    /** De chaque côté : vrai si le bloc voisin fait partie du même réservoir. */
    public static final Map<Direction, BooleanProperty> JOINED = PipeBlock.PROPERTY_BY_DIRECTION;

    public FluidTankBlock(BlockBehaviour.Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (BooleanProperty property : JOINED.values()) {
            state = state.setValue(property, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<FluidTankBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PipeBlock.NORTH, PipeBlock.EAST, PipeBlock.SOUTH, PipeBlock.WEST, PipeBlock.UP, PipeBlock.DOWN);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluidTankBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof FluidTankBlockEntity tank && tickLevel instanceof ServerLevel serverLevel) {
                tank.serverTick(serverLevel);
            }
        };
    }

    /** Un bloc posé : les réservoirs qu'il touche se réassemblent avec lui. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this) && level instanceof ServerLevel serverLevel) {
            FluidTankStructure.reform(serverLevel, pos);
            level.invalidateCapabilities(pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        FluidTankStructure.afterRemoval(level, pos);
        level.invalidateCapabilities(pos);
    }

    /** Avec un seau en main : vide ou remplit le seau dans le réservoir. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof FluidTankBlockEntity tank) {
            ResourceHandler<FluidResource> handler = tank.handler();
            if (handler != null && FluidUtil.interactWithFluidHandler(player, hand, pos, handler)) {
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /** À main nue : annonce la taille du réservoir et ce qu'il contient. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof FluidTankBlockEntity block) {
            FluidTankBlockEntity tank = block.master();
            if (tank != null) {
                FluidStack content = tank.fluid();
                Component name = content.isEmpty() ? Component.translatable("gui.orbitalis.fluid.empty")
                        : content.getFluid().getFluidType().getDescription();
                serverPlayer.sendOverlayMessage(Component.translatable("tank.orbitalis.status", name, content.getAmount(), tank.capacity(),
                        tank.width(), tank.width(), tank.height()).withStyle(ChatFormatting.AQUA));
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Le réservoir est vitré : il ne fait pas d'ombre sur ses voisins. */
    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }
}
