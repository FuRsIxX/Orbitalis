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
import net.minecraft.world.level.Level;

/**
 * Des échantillons d'un astre (la Lune, Mars, ou une exoplanète), dans leur boîte scellée. Rapportés sur Terre, un clic
 * droit les confie au laboratoire de l'agence : leur analyse rapporte des points de recherche, beaucoup pour les premiers
 * de chaque astre. Confiés à une baie du laboratoire d'une station, en apesanteur, ils en rapportent la moitié de plus
 * (voir {@code LabRackBlockEntity}).
 */
public class SampleItem extends Item {
    /** L'astre d'où viennent les échantillons, et ce que vaut leur analyse : les premiers, puis les suivants. */
    public enum Body {
        MOON("moon", 150, 30),
        MARS("mars", 250, 50),
        PROXIMA("proxima", 400, 80),
        TRAPPIST("trappist", 450, 90),
        KEPLER("kepler", 450, 90),
        TARANTULA("tarantula", 800, 160);

        private final String id;
        private final int first;
        private final int next;

        Body(String id, int first, int next) {
            this.id = id;
            this.first = first;
            this.next = next;
        }

        public String id() {
            return id;
        }

        /** Le jalon de la première analyse : les premiers échantillons de l'astre ne valent cher qu'une fois. */
        public String milestone() {
            return "analysed_" + id;
        }

        public int points(boolean first) {
            return first ? this.first : next;
        }
    }

    private final Body body;

    public SampleItem(Properties properties, Body body) {
        super(properties);
        this.body = body;
    }

    public Body body() {
        return body;
    }

    /** Points de l'analyse sur Terre : les premiers échantillons de chaque astre, puis les suivants. */
    public int points(boolean first) {
        return body.points(first);
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> use(Level level, Player player, InteractionHand hand) {
        return com.orbitalis.platform.Compat.holder(use$(level, player, hand), player.getItemInHand(hand), level);
    }

    private InteractionResult use$(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        // On les analyse sur Terre, dans le monde principal, ou dans une baie du laboratoire d'une station : pas sur place.
        if (level.dimension() != Level.OVERWORLD) {
            serverPlayer.displayClientMessage(Component.translatable("exploration.orbitalis.samples.bring_home").withStyle(ChatFormatting.GOLD), true);
            return InteractionResult.FAIL;
        }
        AgencyResearch research = ResearchManager.of(serverPlayer);
        boolean first = research.completeMilestone(body.milestone());
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
    public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> builder$list, TooltipFlag flag) {
        java.util.function.Consumer<Component> builder = builder$list::add;
        builder.accept(Component.translatable("exploration.orbitalis.samples.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
