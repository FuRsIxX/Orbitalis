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

/** Un réservoir ou une machine cassés disent, au survol, le fluide et l'énergie qu'ils emportent. */
@EventBusSubscriber(modid = Orbitalis.MODID, value = Dist.CLIENT)
public final class StoredContentTooltip {
    private StoredContentTooltip() {
    }

    @SubscribeEvent
    static void onTooltip(ItemTooltipEvent event) {
        int line = 1;
        SimpleFluidContent content = event.getItemStack().get(ModDataComponents.FLUID_CONTENT.get());
        if (content != null && !content.isEmpty()) {
            event.getToolTip().add(line++, Component.translatable("tank.orbitalis.tooltip", content.getFluidType().getDescription(), content.getAmount())
                    .withStyle(ChatFormatting.AQUA));
        }
        Integer energy = event.getItemStack().get(ModDataComponents.STORED_ENERGY.get());
        if (energy != null && energy > 0) {
            event.getToolTip().add(line, Component.translatable("machine.orbitalis.stored_energy", energy).withStyle(ChatFormatting.YELLOW));
        }
    }
}
