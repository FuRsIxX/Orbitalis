package com.orbitalis.phase3.exploration;

import java.util.function.Consumer;

import com.orbitalis.core.registry.ModEntities;
import com.orbitalis.core.research.ResearchManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Le drone volant, replié : un clic droit sur le sol l'y pose, tourné comme le joueur. Il faut maîtriser la technologie
 * des giravions.
 */
public class RotorDroneItem extends Item {
    public RotorDroneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        if (player != null && !player.getAbilities().instabuild && !ResearchManager.isUnlocked(player, RotorDroneEntity.RESEARCH)) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendOverlayMessage(Component.translatable("spaceport.orbitalis.research_required",
                        Component.translatable("research.orbitalis.node." + RotorDroneEntity.RESEARCH)).withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        RotorDroneEntity drone = ModEntities.ROTOR_DRONE.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (drone == null) {
            return InteractionResult.FAIL;
        }
        float yaw = player == null ? 0.0F : player.getYRot();
        drone.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0F);
        if (!level.noCollision(drone)) {
            return InteractionResult.FAIL;
        }
        level.addFreshEntity(drone);
        context.getItemInHand().consume(1, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("exploration.orbitalis.drone.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
