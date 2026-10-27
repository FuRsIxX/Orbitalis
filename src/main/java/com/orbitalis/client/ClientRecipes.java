package com.orbitalis.client;

import com.orbitalis.Orbitalis;
import com.orbitalis.phase1.machines.recipe.MachineRecipeLookup;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;

/**
 * Reçoit, côté client, les recettes de machines envoyées par le serveur.
 * Elles servent à l'affichage dans JEI et au filtrage des emplacements d'entrée des machines.
 */
@EventBusSubscriber(modid = Orbitalis.MODID, value = Dist.CLIENT)
public final class ClientRecipes {
    private ClientRecipes() {
    }

    /** Priorité haute : la copie doit être prête avant que JEI ne recharge ses recettes. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRecipesReceived(RecipesReceivedEvent event) {
        MachineRecipeLookup.setClientRecipes(event.getRecipeMap());
    }
}
