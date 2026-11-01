package com.orbitalis.client;

import com.orbitalis.Orbitalis;
import com.orbitalis.core.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.fluids.SimpleFluidContent;

/** Un réservoir cassé dit, au survol, le fluide qu'il emporte. */
@EventBusSubscriber(modid = Orbitalis.MODID, value = Dist.CLIENT)
public final class FluidContentTooltip {
    private FluidContentTooltip() {
    }

    @SubscribeEvent
    static void onTooltip(ItemTooltipEvent event) {
        SimpleFluidContent content = event.getItemStack().get(ModDataComponents.FLUID_CONTENT.get());
        if (content != null && !content.isEmpty()) {
            event.getToolTip().add(1, Component.translatable("tank.orbitalis.tooltip", content.getFluidType().getDescription(), content.getAmount())
                    .withStyle(ChatFormatting.AQUA));
        }
    }
}
