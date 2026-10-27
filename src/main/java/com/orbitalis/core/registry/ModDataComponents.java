package com.orbitalis.core.registry;

import java.util.List;

import com.orbitalis.Orbitalis;
import com.orbitalis.phase2.survival.SuitModule;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Données propres au mod, gardées sur les objets. */
public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE,
            Orbitalis.MODID);

    /** Modules installés sur une pièce de combinaison avancée, dans l'ordre de leur pose. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<SuitModule>>> SUIT_MODULES = COMPONENTS.registerComponentType(
            "suit_modules", builder -> builder.persistent(SuitModule.LIST_CODEC).networkSynchronized(SuitModule.LIST_STREAM_CODEC));

    /**
     * Plan d'archive pas encore validé : le modèle du catalogue qu'il décrit. Il faut le faire étudier à l'établi
     * d'ingénieur (onglet Archives) avant de pouvoir le déposer sur une table de lancement.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> ARCHIVE_PLAN = COMPONENTS.registerComponentType(
            "archive_plan", builder -> builder.persistent(com.mojang.serialization.Codec.STRING)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8));

    private ModDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
