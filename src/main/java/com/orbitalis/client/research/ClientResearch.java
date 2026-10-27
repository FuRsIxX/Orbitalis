package com.orbitalis.client.research;

import java.util.List;

import com.orbitalis.core.network.ResearchSyncPayload;
import com.orbitalis.core.research.AgencyResearch;

/**
 * Copie, côté client, de la recherche de l'agence du joueur. Elle sert uniquement à l'affichage :
 * le serveur la renvoie à chaque changement, et c'est lui qui décide de tout.
 */
public final class ClientResearch {
    private static AgencyResearch research = new AgencyResearch();

    private ClientResearch() {
    }

    public static AgencyResearch get() {
        return research;
    }

    /** Remplace l'état connu par celui que le serveur vient d'envoyer. */
    public static void receive(ResearchSyncPayload payload) {
        research = new AgencyResearch(payload.points(), payload.unlocked(), List.of());
    }
}
