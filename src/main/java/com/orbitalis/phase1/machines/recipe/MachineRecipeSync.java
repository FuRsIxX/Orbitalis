package com.orbitalis.phase1.machines.recipe;

import com.orbitalis.Orbitalis;
import com.orbitalis.core.registry.ModRecipes;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/**
 * Envoie les recettes des machines aux clients.
 * <p>
 * Minecraft ne transmet plus les recettes aux joueurs : sans cet envoi, JEI ne pourrait pas
 * afficher les recettes des machines.
 */
@EventBusSubscriber(modid = Orbitalis.MODID)
public final class MachineRecipeSync {
    private MachineRecipeSync() {
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        event.sendRecipes(ModRecipes.ALLOYING_TYPE.get(), ModRecipes.ROLLING_TYPE.get(),
                ModRecipes.ELECTROLYSIS_TYPE.get(), ModRecipes.LIQUEFACTION_TYPE.get(), ModRecipes.REFINING_TYPE.get());
    }
}
