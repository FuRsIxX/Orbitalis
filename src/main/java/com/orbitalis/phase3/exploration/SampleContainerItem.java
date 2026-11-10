package com.orbitalis.phase3.exploration;

import java.util.function.Consumer;

import com.orbitalis.core.registry.ModItems;
import com.orbitalis.core.research.ResearchManager;
import com.orbitalis.phase2.survival.SpaceRules;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Le conteneur à échantillons, vide : sur la Lune, sur Mars ou sur une exoplanète, un clic droit sur le sol y prélève un
 * peu de roche, et le conteneur devient une boîte d'échantillons de cet astre, à rapporter sur Terre ou au laboratoire
 * d'une station. Il faut maîtriser la technologie du retour d'échantillons.
 */
public class SampleContainerItem extends Item {
    public static final String RESEARCH = "sample_return";

    public SampleContainerItem(Properties properties) {
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
        ItemStack samples = samplesOf(level);
        if (samples.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("exploration.orbitalis.samples.wrong_world").withStyle(ChatFormatting.GOLD));
            return InteractionResult.FAIL;
        }
        // On ne prélève que le sol lui-même : pas une machine, ni un bloc qu'on a posé là.
        BlockState ground = level.getBlockState(context.getClickedPos());
        if (ground.hasBlockEntity() || ground.isAir() || !ground.isSolidRender()) {
            player.sendOverlayMessage(Component.translatable("exploration.orbitalis.samples.not_ground").withStyle(ChatFormatting.GOLD));
            return InteractionResult.FAIL;
        }
        context.getItemInHand().consume(1, player);
        if (!player.getInventory().add(samples)) {
            player.drop(samples, false);
        }
        level.playSound(null, context.getClickedPos(), SoundEvents.BRUSH_GENERIC, SoundSource.PLAYERS, 1.0F, 0.9F);
        player.sendOverlayMessage(Component.translatable("exploration.orbitalis.samples.collected", samples.getHoverName()).withStyle(ChatFormatting.AQUA));
        return InteractionResult.SUCCESS;
    }

    /** La boîte d'échantillons de ce monde, ou rien si l'on n'y prélève pas : la Lune, Mars et les exoplanètes. */
    private static ItemStack samplesOf(ServerLevel level) {
        if (SpaceRules.isMoon(level)) {
            return new ItemStack(ModItems.LUNAR_SAMPLES.get());
        } else if (SpaceRules.isMars(level)) {
            return new ItemStack(ModItems.MARS_SAMPLES.get());
        } else if (SpaceRules.isProxima(level)) {
            return new ItemStack(ModItems.PROXIMA_SAMPLES.get());
        } else if (SpaceRules.isTrappist(level)) {
            return new ItemStack(ModItems.TRAPPIST_SAMPLES.get());
        } else if (SpaceRules.isKepler(level)) {
            return new ItemStack(ModItems.KEPLER_SAMPLES.get());
        } else if (SpaceRules.isTarantula(level)) {
            return new ItemStack(ModItems.TARANTULA_SAMPLES.get());
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("exploration.orbitalis.samples.container_tooltip").withStyle(ChatFormatting.GRAY));
    }
}
