package com.orbitalis.core.registry;

import com.orbitalis.Orbitalis;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Onglet du mode créatif regroupant tout le contenu d'Orbitalis.
 */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Orbitalis.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ORBITALIS = CREATIVE_MODE_TABS.register("orbitalis",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.orbitalis"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> ModItems.TITANIUM_INGOT.get().getDefaultInstance())
                    // Tous les items du mod sont ajoutés automatiquement, dans leur ordre d'enregistrement.
                    // La fusée assemblée n'y figure pas : une fusée se bâtit sur son pas de tir, d'après un plan. La
                    // citerne de fluides non plus : les réservoirs créatifs, un par fluide, la remplacent.
                    .displayItems((parameters, output) -> {
                        ModItems.ITEMS.getEntries().stream()
                                .filter(item -> item.get() != ModItems.ROCKET.get()
                                        && item.get() != ModBlocks.CREATIVE_FLUID_SOURCE.get().asItem()
                                        // Le contenu en réserve pour une prochaine version n'y est pas.
                                        && !com.orbitalis.core.Features.hidden(item.get()))
                                .forEach(item -> output.accept(item.get()));
                    })
                    .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
