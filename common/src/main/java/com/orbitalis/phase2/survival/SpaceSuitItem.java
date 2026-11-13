package com.orbitalis.phase2.survival;

import java.util.EnumMap;
import java.util.List;

import com.orbitalis.Orbitalis;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

/**
 * Une pièce de combinaison spatiale : casque, torse ou jambes, orange ou blanche (voir {@link SpaceSuits}). Elle se
 * porte comme une armure. Accroupi, un clic droit avec une pièce blanche en main en retire le dernier module posé.
 * <p>
 * Sa durabilité est sa réserve d'oxygène : elle est incassable (les coups ne l'usent pas), et sa barre montre l'oxygène.
 */
public class SpaceSuitItem extends ArmorItem {
    /** L'apparence portée : spacesuit_layer_1/2 et advanced_spacesuit_layer_1/2 (textures/models/armor). */
    private static final Holder<ArmorMaterial> SUIT = material("spacesuit", SoundEvents.ARMOR_EQUIP_LEATHER);
    private static final Holder<ArmorMaterial> ADVANCED_SUIT = material("advanced_spacesuit", SoundEvents.ARMOR_EQUIP_IRON);

    private final boolean advanced;
    private final EquipmentSlot slot;

    public SpaceSuitItem(Properties properties, boolean advanced, EquipmentSlot slot) {
        super(advanced ? ADVANCED_SUIT : SUIT, type(slot), properties);
        this.advanced = advanced;
        this.slot = slot;
    }

    private static Holder<ArmorMaterial> material(String name, Holder<net.minecraft.sounds.SoundEvent> sound) {
        EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        for (ArmorItem.Type type : ArmorItem.Type.values()) {
            defense.put(type, 0);
        }
        return Holder.direct(new ArmorMaterial(defense, 9, sound, () -> Ingredient.EMPTY,
                List.of(new ArmorMaterial.Layer(Orbitalis.id(name))), 0.0F, 0.0F));
    }

    private static ArmorItem.Type type(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> ArmorItem.Type.HELMET;
            case CHEST -> ArmorItem.Type.CHESTPLATE;
            case LEGS -> ArmorItem.Type.LEGGINGS;
            default -> ArmorItem.Type.BOOTS;
        };
    }

    public boolean advanced() {
        return advanced;
    }

    public EquipmentSlot slot() {
        return slot;
    }

    /** La protection et les bonus de la pièce, selon sa génération et sa place (voir {@link SpaceSuits#attributes}). */
    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        return SpaceSuits.attributes(advanced, slot, List.of());
    }

    /** La barre de la pièce montre sa réserve d'oxygène, bien qu'elle soit incassable. */
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stack.getMaxDamage() > 0 && stack.getDamageValue() > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return stack.getMaxDamage() <= 0 ? 13 : Math.round(13.0F - stack.getDamageValue() * 13.0F / stack.getMaxDamage());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float left = stack.getMaxDamage() <= 0 ? 1.0F : 1.0F - (float) stack.getDamageValue() / stack.getMaxDamage();
        return Mth.hsvToRgb(Math.max(0.0F, left) / 3.0F, 1.0F, 1.0F);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (advanced && player.isSecondaryUseActive() && !SpaceSuits.modules(stack).isEmpty()) {
            if (!level.isClientSide()) {
                SuitModule module = SpaceSuits.removeLast(stack);
                if (module != null) {
                    player.getInventory().placeItemBackInInventory(new ItemStack(module.item()));
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.8F,
                            0.8F);
                    player.displayClientMessage(Component.translatable("suit.orbitalis.module.removed", Component.translatable(module.nameKey())), true);
                }
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        return super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> builder, TooltipFlag flag) {
        if (slot == EquipmentSlot.CHEST) {
            int left = SpaceRules.oxygen(stack);
            builder.add(Component.translatable("suit.orbitalis.tooltip.oxygen", left / 60, String.format("%02d", left % 60), stack.getMaxDamage() / 60)
                    .withStyle(ChatFormatting.AQUA));
        }
        builder.add(Component.translatable("suit.orbitalis.tooltip.set").withStyle(ChatFormatting.GRAY));
        if (!advanced) {
            return;
        }
        List<SuitModule> modules = SpaceSuits.modules(stack);
        if (modules.isEmpty()) {
            builder.add(Component.translatable("suit.orbitalis.tooltip.no_modules").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            builder.add(Component.translatable("suit.orbitalis.tooltip.modules").withStyle(ChatFormatting.GRAY));
            for (SuitModule module : modules) {
                builder.add(Component.literal(" • ").append(Component.translatable(module.nameKey())).withStyle(ChatFormatting.WHITE));
            }
            builder.add(Component.translatable("suit.orbitalis.tooltip.remove").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
