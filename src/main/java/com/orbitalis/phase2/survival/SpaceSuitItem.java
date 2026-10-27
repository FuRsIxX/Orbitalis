package com.orbitalis.phase2.survival;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Une pièce de combinaison spatiale : casque, torse ou jambes, orange ou blanche (voir {@link SpaceSuits}). Elle se
 * porte comme une armure. Accroupi, un clic droit avec une pièce blanche en main en retire le dernier module posé.
 */
public class SpaceSuitItem extends Item {
    private final boolean advanced;
    private final EquipmentSlot slot;

    public SpaceSuitItem(Properties properties, boolean advanced, EquipmentSlot slot) {
        super(properties);
        this.advanced = advanced;
        this.slot = slot;
    }

    public boolean advanced() {
        return advanced;
    }

    public EquipmentSlot slot() {
        return slot;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (advanced && player.isSecondaryUseActive() && !SpaceSuits.modules(stack).isEmpty()) {
            if (!level.isClientSide()) {
                SuitModule module = SpaceSuits.removeLast(stack);
                if (module != null) {
                    player.getInventory().placeItemBackInInventory(new ItemStack(module.item()));
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.8F,
                            0.8F);
                    player.sendOverlayMessage(Component.translatable("suit.orbitalis.module.removed", Component.translatable(module.nameKey())));
                }
            }
            return InteractionResult.SUCCESS;
        }
        return super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        if (slot == EquipmentSlot.CHEST) {
            int left = SpaceRules.oxygen(stack);
            builder.accept(Component.translatable("suit.orbitalis.tooltip.oxygen", left / 60, String.format("%02d", left % 60), stack.getMaxDamage() / 60)
                    .withStyle(ChatFormatting.AQUA));
        }
        builder.accept(Component.translatable("suit.orbitalis.tooltip.set").withStyle(ChatFormatting.GRAY));
        if (!advanced) {
            return;
        }
        List<SuitModule> modules = SpaceSuits.modules(stack);
        if (modules.isEmpty()) {
            builder.accept(Component.translatable("suit.orbitalis.tooltip.no_modules").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            builder.accept(Component.translatable("suit.orbitalis.tooltip.modules").withStyle(ChatFormatting.GRAY));
            for (SuitModule module : modules) {
                builder.accept(Component.literal(" • ").append(Component.translatable(module.nameKey())).withStyle(ChatFormatting.WHITE));
            }
            builder.accept(Component.translatable("suit.orbitalis.tooltip.remove").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
