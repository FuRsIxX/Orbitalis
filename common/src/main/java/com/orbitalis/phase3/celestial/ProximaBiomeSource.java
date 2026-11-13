package com.orbitalis.phase3.celestial;

import java.util.stream.Stream;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * Les biomes de Proxima b, posés d'après l'étoile. La planète lui tourne toujours la même face : à l'est, la face de
 * jour, brûlée ; à l'ouest, la face de nuit, gelée ; entre elles, une bande de crépuscule éternel, large de
 * {@link #BAND} blocs de part et d'autre du méridien zéro, où la vie a pris. Sa lisière ondule, comme le relief.
 */
public class ProximaBiomeSource extends BiomeSource {
    /** Demi-largeur de la bande du crépuscule, en blocs. */
    public static final int BAND = 1600;
    /** Amplitude et longueur d'onde des ondulations de sa lisière, en blocs. */
    private static final double WOBBLE = 220.0;
    private static final double WAVELENGTH = 900.0;

    public static final MapCodec<ProximaBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Biome.CODEC.fieldOf("day").forGetter(source -> source.day),
            Biome.CODEC.fieldOf("twilight").forGetter(source -> source.twilight),
            Biome.CODEC.fieldOf("night").forGetter(source -> source.night)).apply(instance, ProximaBiomeSource::new));

    /** Les trois zones de la planète. */
    public enum Zone {
        DAY, TWILIGHT, NIGHT
    }

    private final Holder<Biome> day;
    private final Holder<Biome> twilight;
    private final Holder<Biome> night;

    public ProximaBiomeSource(Holder<Biome> day, Holder<Biome> twilight, Holder<Biome> night) {
        this.day = day;
        this.twilight = twilight;
        this.night = night;
    }

    /** La zone où tombe ce point de la planète, en blocs. */
    public static Zone zone(int x, int z) {
        double edge = WOBBLE * (Math.sin(z / WAVELENGTH * 2.0 * Math.PI) + 0.5 * Math.sin(z / (WAVELENGTH * 0.37) * 2.0 * Math.PI + 1.3)) / 1.5;
        if (x > BAND + edge) {
            return Zone.DAY;
        }
        if (x < -BAND - edge) {
            return Zone.NIGHT;
        }
        return Zone.TWILIGHT;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.of(day, twilight, night);
    }

    /** Les coordonnées données sont celles des quarts de bloc : quatre blocs par unité. */
    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        return switch (zone(x << 2, z << 2)) {
            case DAY -> day;
            case NIGHT -> night;
            case TWILIGHT -> twilight;
        };
    }
}
