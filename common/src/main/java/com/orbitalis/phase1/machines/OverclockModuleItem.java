package com.orbitalis.phase1.machines;

import java.util.function.Consumer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.orbitalis.core.registry.ModItems;

import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Module de surcadençage : posé dans l'onglet d'une machine de transformation, il lui permet d'aller jusqu'à
 * {@link #speed()} fois plus vite. La vitesse se choisit ensuite sur l'écran de la machine. Aller N fois plus vite
 * coûte N² fois plus d'électricité : ×2 coûte ×4, ×4 coûte ×16, ×8 coûte ×64.
 */
public class OverclockModuleItem extends Item {
    private final int speed;

    public OverclockModuleItem(Properties properties, int speed) {
        super(properties);
        this.speed = speed;
    }

    /** Vitesse la plus haute que ce module permet. */
    public int speed() {
        return speed;
    }

    /** Le module qui permet cette vitesse ; vide pour ×1 ou une vitesse inconnue. */
    public static ItemStack forSpeed(int speed) {
        return switch (speed) {
            case 2 -> new ItemStack(ModItems.OVERCLOCK_MODULE_X2.get());
            case 4 -> new ItemStack(ModItems.OVERCLOCK_MODULE_X4.get());
            case 8 -> new ItemStack(ModItems.OVERCLOCK_MODULE_X8.get());
            default -> ItemStack.EMPTY;
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> builder$list, TooltipFlag flag) {
        java.util.function.Consumer<Component> builder = builder$list::add;
        builder.accept(Component.translatable("item.orbitalis.overclock_module.tooltip", speed, speed * speed).withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.orbitalis.overclock_module.hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * Réglage gardé sur l'objet d'une machine cassée : la vitesse du module posé (0 sans module) et la vitesse choisie.
     */
    public record Setting(int module, int speed) {
        public static final Codec<Setting> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("module").forGetter(Setting::module),
                Codec.INT.fieldOf("speed").forGetter(Setting::speed)).apply(instance, Setting::new));
        public static final StreamCodec<ByteBuf, Setting> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Setting::module,
                ByteBufCodecs.VAR_INT, Setting::speed,
                Setting::new);
    }
}
