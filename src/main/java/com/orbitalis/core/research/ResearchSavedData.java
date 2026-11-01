package com.orbitalis.core.research;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.orbitalis.Orbitalis;

import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Sauvegarde de la recherche de toutes les agences d'un monde.
 * Elle est rangée avec les données de l'Overworld et enregistrée avec le monde.
 */
public class ResearchSavedData extends SavedData {
    private static final Codec<ResearchSavedData> CODEC = Codec.unboundedMap(Codec.STRING, AgencyResearch.CODEC)
            .xmap(ResearchSavedData::new, data -> data.agencies);

    public static final SavedDataType<ResearchSavedData> TYPE = new SavedDataType<>(
            Orbitalis.id("research"), ResearchSavedData::new, CODEC);

    /** Recherche de chaque agence, par clé d'agence (voir {@link ResearchManager#agencyKey}). */
    private final Map<String, AgencyResearch> agencies = new HashMap<>();

    public ResearchSavedData() {
    }

    private ResearchSavedData(Map<String, AgencyResearch> agencies) {
        this.agencies.putAll(agencies);
        this.agencies.values().forEach(research -> research.onChange(this::setDirty));
    }

    /**
     * Recherche d'une agence ; elle est créée vide à la première demande. Tout changement marque la sauvegarde à
     * enregistrer, d'où qu'il vienne.
     */
    public AgencyResearch get(String agencyKey) {
        return agencies.computeIfAbsent(agencyKey, key -> {
            AgencyResearch research = new AgencyResearch();
            research.onChange(this::setDirty);
            return research;
        });
    }
}
