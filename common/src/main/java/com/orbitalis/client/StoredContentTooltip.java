package com.orbitalis.client;

import com.orbitalis.Orbitalis;
import com.orbitalis.core.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import com.orbitalis.client.port.event.ClientHooks.ItemTooltipEvent;
import com.orbitalis.platform.fluid.SimpleFluidContent;

/** Un réservoir ou une machine cassés disent, au survol, les fluides et l'énergie qu'ils emportent (les objets, Minecraft les montre). */
public final class StoredContentTooltip {
    private StoredContentTooltip() {
    }

        public static void onTooltip(ItemTooltipEvent event) {
        int line = 1;
        SimpleFluidContent content = event.getItemStack().get(ModDataComponents.FLUID_CONTENT.get());
        if (content != null && !content.isEmpty()) {
            event.getToolTip().add(line++, Component.translatable("tank.orbitalis.tooltip", content.copy().getHoverName(), content.getAmount())
                    .withStyle(ChatFormatting.AQUA));
        }
        var fluids = event.getItemStack().get(ModDataComponents.MACHINE_FLUIDS.get());
        if (fluids != null) {
            for (SimpleFluidContent fluid : fluids) {
                if (!fluid.isEmpty()) {
                    event.getToolTip().add(line++, Component.translatable("tank.orbitalis.tooltip", fluid.copy().getHoverName(), fluid.getAmount())
                            .withStyle(ChatFormatting.AQUA));
                }
            }
        }
        Integer energy = event.getItemStack().get(ModDataComponents.STORED_ENERGY.get());
        if (energy != null && energy > 0) {
            event.getToolTip().add(line, Component.translatable("machine.orbitalis.stored_energy", energy).withStyle(ChatFormatting.YELLOW));
        }
    }
}
