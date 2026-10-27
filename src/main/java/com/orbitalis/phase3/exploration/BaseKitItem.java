package com.orbitalis.phase3.exploration;

import java.util.function.Consumer;

import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.research.AgencyResearch;
import com.orbitalis.core.research.ResearchManager;
import com.orbitalis.phase2.station.AirlockDoorBlock;
import com.orbitalis.phase2.station.StationManager;
import com.orbitalis.phase2.survival.SpaceRules;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Le kit de base planétaire : sur la Lune ou sur Mars, un clic droit sur le sol y bâtit un module pressurisé, le même
 * que le module central d'une station (sa coque, son générateur d'air, sa batterie, son terminal, son aile solaire),
 * avec une porte de sas pour y entrer depuis la surface. Les kits de modules de station s'y raccordent comme en orbite.
 */
public class BaseKitItem extends Item {
    public static final String RESEARCH = "planetary_bases";
    /** Prime de la première base de l'agence sur chaque astre. */
    private static final int FIRST_BASE_POINTS = 80;

    public BaseKitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        if (!player.getAbilities().instabuild && !ResearchManager.isUnlocked(player, RESEARCH)) {
            player.sendOverlayMessage(Component.translatable("spaceport.orbitalis.research_required",
                    Component.translatable("research.orbitalis.node." + RESEARCH)).withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        boolean moon = SpaceRules.isMoon(level);
        if (!moon && !SpaceRules.isMars(level) && !SpaceRules.isTitan(level) && !SpaceRules.isEuropa(level)) {
            player.sendOverlayMessage(Component.translatable("exploration.orbitalis.base.wrong_world").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        int r = StationManager.CORE_RADIUS;
        // Le plancher du module se pose sur le bloc visé.
        BlockPos center = context.getClickedPos().above(1 + r);
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r + 1, r))) {
            if (!level.getBlockState(pos).canBeReplaced()) {
                player.sendOverlayMessage(Component.translatable("exploration.orbitalis.base.no_room").withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
        }
        StationManager.build(level, center);
        // Au sol, pas de sas d'amarrage : une porte de sas, au sud, pour entrer depuis la surface.
        BlockPos door = center.offset(0, -1, r);
        BlockState lower = ModBlocks.AIRLOCK_DOOR.get().defaultBlockState().setValue(AirlockDoorBlock.FACING, Direction.SOUTH);
        level.setBlock(door, lower, Block.UPDATE_ALL);
        level.setBlock(door.above(), lower.setValue(AirlockDoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        context.getItemInHand().consume(1, player);
        player.sendSystemMessage(Component.translatable("exploration.orbitalis.base.built").withStyle(ChatFormatting.GREEN));
        AgencyResearch research = ResearchManager.of(player);
        if (research.completeMilestone("base_" + (moon ? "moon" : SpaceRules.isTitan(level) ? "titan" : SpaceRules.isEuropa(level) ? "europa" : "mars"))) {
            research.addPoints(FIRST_BASE_POINTS);
            ResearchManager.changed(player);
            player.sendSystemMessage(Component.translatable("exploration.orbitalis.base.first", FIRST_BASE_POINTS).withStyle(ChatFormatting.AQUA));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("exploration.orbitalis.base.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
