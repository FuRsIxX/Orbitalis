package com.orbitalis.phase1.rocket;

import java.util.function.Supplier;

import com.orbitalis.phase1.spaceport.LaunchTableBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pièce de fusée qui se pose elle-même : un moteur, un réservoir, un interétage, une pointe. Posée sur le fantôme
 * d'une fusée en chantier, elle y prend la place prévue par le plan, et coûte ce que le plan en demande (la baie d'un
 * étage à trois moteurs en prend trois). Posée ailleurs, elle pose son bloc, un à la fois : c'est ainsi qu'on bâtit
 * une fusée librement, sans plan.
 */
public class RocketPartItem extends Item {
    private final Supplier<? extends Block> block;

    public RocketPartItem(Properties properties, Supplier<? extends Block> block) {
        super(properties);
        this.block = block;
    }

    /** Le bloc que pose cette pièce, hors d'un plan. */
    public Block block() {
        return block.get();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockPlaceContext place = new BlockPlaceContext(context);
        if (!place.canPlace()) {
            return InteractionResult.FAIL;
        }
        Level level = context.getLevel();
        BlockPos pos = place.getClickedPos();
        Player player = context.getPlayer();
        ItemStack held = context.getItemInHand();
        // Sur le fantôme d'une fusée en chantier : la place et le coût que le plan prévoient.
        LaunchTableBlockEntity.PlannedPiece planned = LaunchTableBlockEntity.plannedPieceAt(level, pos);
        Block target = block.get();
        int cost = 1;
        if (planned != null) {
            if (planned.piece().source() != this) {
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.sendOverlayMessage(Component.translatable("spaceport.orbitalis.rocket.wrong_piece",
                            new ItemStack(planned.piece().source()).getHoverName()).withStyle(ChatFormatting.GOLD));
                }
                return InteractionResult.FAIL;
            }
            target = planned.piece().block();
            cost = planned.piece().count();
        }
        boolean free = player != null && player.getAbilities().instabuild;
        if (!free && held.getCount() < cost) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendOverlayMessage(Component.translatable("spaceport.orbitalis.rocket.need_pieces", cost, held.getHoverName())
                        .withStyle(ChatFormatting.GOLD));
            }
            return InteractionResult.FAIL;
        }
        BlockState state = target.getStateForPlacement(place);
        if (state == null || !state.canSurvive(level, pos)) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!level.setBlock(pos, state, Block.UPDATE_ALL)) {
            return InteractionResult.FAIL;
        }
        SoundType sound = state.getSoundType();
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        if (!free) {
            held.shrink(cost);
        }
        return InteractionResult.SUCCESS;
    }
}
