package com.orbitalis.phase2.survival;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
 * Un module de combinaison avancée. Un clic droit le fixe sur la pièce qui le reçoit : celle que l'on porte, sinon
 * celle que l'on tient dans l'autre main.
 */
public class SuitModuleItem extends Item {
    private final SuitModule module;

    public SuitModuleItem(Properties properties, SuitModule module) {
        super(properties);
        this.module = module;
    }

    public SuitModule module() {
        return module;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        ItemStack piece = player.getItemBySlot(module.slot());
        if (SpaceSuits.refusal(piece, module) != null) {
            ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            if (SpaceSuits.isPiece(other, module.slot()) && SpaceSuits.isAdvanced(other)) {
                piece = other;
            }
        }
        String refusal = SpaceSuits.refusal(piece, module);
        if (refusal != null) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable(refusal, Component.translatable("suit.orbitalis.piece." + module.slot().getName()))
                        .withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            SpaceSuits.install(piece, module);
            held.consume(1, player);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 0.8F, 1.2F);
            player.sendOverlayMessage(Component.translatable("suit.orbitalis.module.installed", Component.translatable(module.nameKey()))
                    .withStyle(ChatFormatting.AQUA));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable(module.nameKey() + ".desc").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("suit.orbitalis.tooltip.fits", Component.translatable("suit.orbitalis.piece." + module.slot().getName()))
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
