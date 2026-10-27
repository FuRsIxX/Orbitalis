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
 * Le rover, replié : un clic droit sur le sol l'y pose, tourné comme le joueur. Il faut maîtriser la technologie des
 * rovers.
 */
public class RoverItem extends Item {
    public static final String RESEARCH = "rovers";

    public RoverItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        if (player != null && !player.getAbilities().instabuild && !ResearchManager.isUnlocked(player, RESEARCH)) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendOverlayMessage(Component.translatable("spaceport.orbitalis.research_required",
                        Component.translatable("research.orbitalis.node." + RESEARCH)).withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        RoverEntity rover = ModEntities.ROVER.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (rover == null) {
            return InteractionResult.FAIL;
        }
        float yaw = player == null ? 0.0F : player.getYRot();
        rover.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0F);
        rover.yBodyRot = rover.yHeadRot = yaw;
        if (!level.noCollision(rover)) {
            return InteractionResult.FAIL;
        }
        level.addFreshEntity(rover);
        context.getItemInHand().consume(1, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("exploration.orbitalis.rover.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
