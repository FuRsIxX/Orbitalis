package com.orbitalis.phase1.satellite;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.orbitalis.Orbitalis;

import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Sauvegarde des satellites de toutes les agences d'un monde.
 * Elle est rangée avec les données de l'Overworld et enregistrée avec le monde.
 */
public class SatelliteSavedData extends SavedData {
    private static final Codec<SatelliteSavedData> CODEC = Codec.unboundedMap(Codec.STRING, AgencySatellites.CODEC)
            .xmap(SatelliteSavedData::new, data -> data.agencies);

    public static final SavedDataType<SatelliteSavedData> TYPE = new SavedDataType<>(
            Orbitalis.id("satellites"), SatelliteSavedData::new, CODEC);

    /** Satellites de chaque agence, par clé d'agence (voir {@code ResearchManager.agencyKey}). */
    private final Map<String, AgencySatellites> agencies = new HashMap<>();

    public SatelliteSavedData() {
    }

    private SatelliteSavedData(Map<String, AgencySatellites> agencies) {
        this.agencies.putAll(agencies);
    }

    /** Satellites d'une agence ; la liste est créée vide à la première demande. */
    public AgencySatellites get(String agencyKey) {
        return agencies.computeIfAbsent(agencyKey, key -> new AgencySatellites());
    }

    /** Toutes les agences qui ont, ou ont eu, des satellites. */
    public Map<String, AgencySatellites> agencies() {
        return agencies;
    }
}
