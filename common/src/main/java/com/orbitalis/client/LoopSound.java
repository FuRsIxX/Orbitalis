package com.orbitalis.client;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Un son en boucle, qui joue tant qu'une condition dure : le sifflement de la rentrée, la voile des parachutes, le
 * moteur d'atterrissage, les alertes de bord, le ronronnement d'un générateur d'air. Il monte et s'éteint en douceur,
 * au rythme qu'on lui donne ; une seule boucle joue par clé, si bien qu'on peut la redemander à chaque tick.
 * <p>
 * Placé dans le monde, il suit une position (le son vient de sa direction) et s'atténue avec la distance, calculée
 * ici comme pour le déluge ({@link DelugeSound}) ; sans position, il est joué « dans la tête » du joueur.
 */
public final class LoopSound extends AbstractTickableSoundInstance {
    private static final Map<Object, LoopSound> PLAYING = new HashMap<>();

    private final Object key;
    private final BooleanSupplier active;
    private final Supplier<Vec3> where;
    private final double near;
    private final double reach;
    private final float full;
    private final float rise;
    private final float fall;
    /** Niveau du son, de 0 à 1 : la montée, puis l'extinction. */
    private float level;

    private LoopSound(Builder builder, BooleanSupplier active) {
        super(builder.event, builder.source, SoundInstance.createUnseededRandom());
        this.key = builder.key;
        this.active = active;
        this.where = builder.where;
        this.near = builder.near;
        this.reach = builder.reach;
        this.full = builder.full;
        this.rise = builder.rise;
        this.fall = builder.fall;
        looping = true;
        delay = 0;
        attenuation = SoundInstance.Attenuation.NONE;
        relative = where == null;
        volume = 0.001F;
        if (where != null) {
            Vec3 at = where.get();
            x = at.x;
            y = at.y;
            z = at.z;
        }
    }

    /** Une boucle repérée par cette clé, sur ce son. */
    public static Builder of(Object key, SoundEvent event) {
        return new Builder(key, event);
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean on = minecraft.player != null && minecraft.level != null && active.getAsBoolean();
        if (on) {
            level = Math.min(1.0F, level + rise);
        } else {
            level -= fall;
            if (level <= 0.0F) {
                PLAYING.remove(key, this);
                stop();
                return;
            }
        }
        float heard = 1.0F;
        if (where != null) {
            if (on) {
                Vec3 at = where.get();
                x = at.x;
                y = at.y;
                z = at.z;
            }
            double distance = minecraft.player == null ? reach : Math.sqrt(minecraft.player.getEyePosition().distanceToSqr(x, y, z));
            heard = (float) Mth.clamp(1.0 - (distance - near) / Math.max(1.0, reach - near), 0.0, 1.0);
            heard *= heard;
        }
        volume = Math.max(0.001F, full * level * heard);
    }

    /** Les réglages d'une boucle, avant de la lancer. */
    public static final class Builder {
        private final Object key;
        private final SoundEvent event;
        private SoundSource source = SoundSource.BLOCKS;
        private Supplier<Vec3> where;
        private double near;
        private double reach = 1.0;
        private float full = 1.0F;
        private float rise = 0.1F;
        private float fall = 0.05F;

        private Builder(Object key, SoundEvent event) {
            this.key = key;
            this.event = event;
        }

        /** La catégorie du son, celle dont le joueur règle le volume. */
        public Builder in(SoundSource source) {
            this.source = source;
            return this;
        }

        /** Le son vient de là : à plein volume jusqu'à {@code near} blocs, éteint à {@code reach}. */
        public Builder at(Supplier<Vec3> where, double near, double reach) {
            this.where = where;
            this.near = near;
            this.reach = reach;
            return this;
        }

        /** Volume à plein, de 0 à 1. */
        public Builder volume(float full) {
            this.full = full;
            return this;
        }

        /** Ce que gagne le son à chaque tick quand il monte, et ce qu'il perd quand il s'éteint (de 0 à 1). */
        public Builder fade(float rise, float fall) {
            this.rise = rise;
            this.fall = fall;
            return this;
        }

        /**
         * Lance la boucle, si elle ne joue pas déjà : elle dure tant que la condition est vraie. Le jeu peut couper tous
         * les sons sans prévenir (en quittant un monde, en rechargeant les ressources) : une boucle qu'il ne joue plus
         * est remplacée.
         */
        public void play(BooleanSupplier active) {
            LoopSound playing = PLAYING.get(key);
            if (playing != null && !playing.isStopped() && Minecraft.getInstance().getSoundManager().isActive(playing)) {
                return;
            }
            LoopSound sound = new LoopSound(this, active);
            PLAYING.put(key, sound);
            Minecraft.getInstance().getSoundManager().play(sound);
        }
    }
}
