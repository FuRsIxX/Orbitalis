package com.orbitalis.phase1.satellite;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Satellite en orbite.
 *
 * @param number     numéro d'ordre dans l'agence, qui lui sert de nom (« Satellite météo n° 3 »)
 * @param type       type de satellite
 * @param launchTime date de mise en orbite, en ticks de jeu
 * @param dataSince  date à partir de laquelle ses données n'ont pas encore été reçues au sol, en ticks de jeu
 * @param rtg        vrai s'il a un générateur à radio-isotopes : sa durée de vie est doublée
 */
public record Satellite(int number, SatelliteType type, long launchTime, long dataSince, boolean rtg) {
    public static final Codec<Satellite> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("number").forGetter(Satellite::number),
            SatelliteType.CODEC.fieldOf("type").forGetter(Satellite::type),
            Codec.LONG.fieldOf("launch_time").forGetter(Satellite::launchTime),
            Codec.LONG.fieldOf("data_since").forGetter(Satellite::dataSince),
            Codec.BOOL.optionalFieldOf("rtg", false).forGetter(Satellite::rtg))
            .apply(instance, Satellite::new));

    /** Le même satellite, dont les données ont été reçues jusqu'à cette date. */
    public Satellite withDataSince(long time) {
        return new Satellite(number, type, launchTime, time, rtg);
    }
}
