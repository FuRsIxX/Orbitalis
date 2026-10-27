package com.orbitalis.phase3.exploration;

import java.util.function.Consumer;

import com.orbitalis.core.research.AgencyResearch;
import com.orbitalis.core.research.ResearchManager;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Des échantillons de la Lune ou de Mars, dans leur boîte scellée. Rapportés sur Terre, un clic droit les confie au
 * laboratoire de l'agence : leur analyse rapporte des points de recherche, beaucoup pour les premiers de chaque astre.
 */
public class SampleItem extends Item {
    private final boolean lunar;

    public SampleItem(Properties properties, boolean lunar) {
        super(properties);
        this.lunar = lunar;
    }

    /** Points de l'analyse : les premiers échantillons de chaque astre, puis les suivants. */
    public int points(boolean first) {
        return lunar ? (first ? 150 : 30) : (first ? 250 : 50);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        // On les analyse sur Terre, dans le monde principal : pas sur place.
        if (level.dimension() != Level.OVERWORLD) {
            serverPlayer.sendOverlayMessage(Component.translatable("exploration.orbitalis.samples.bring_home").withStyle(ChatFormatting.GOLD));
            return InteractionResult.FAIL;
        }
        AgencyResearch research = ResearchManager.of(serverPlayer);
        boolean first = research.completeMilestone("analysed_" + (lunar ? "moon" : "mars"));
        int points = points(first);
        research.addPoints(points);
        ResearchManager.changed(serverPlayer);
        player.getItemInHand(hand).consume(1, player);
        level.playSound(null, player.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.8F, 1.2F);
        serverPlayer.sendSystemMessage(Component.translatable(first ? "exploration.orbitalis.samples.analysed_first" : "exploration.orbitalis.samples.analysed",
                getName(player.getItemInHand(hand).isEmpty() ? new ItemStack(this) : player.getItemInHand(hand)), points).withStyle(ChatFormatting.GREEN));
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("exploration.orbitalis.samples.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
