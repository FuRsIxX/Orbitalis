package com.orbitalis.client;

import java.util.HashMap;
import java.util.Map;

import com.orbitalis.core.registry.ModSounds;
import com.orbitalis.phase1.spaceport.LaunchTableBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * Bruit du déluge d'un pas de tir : l'eau qui tombe sur le tablier, tant qu'elle coule.
 * <p>
 * C'est une boucle sans couture, que le jeu répète aussi longtemps que le déluge est ouvert : de son ouverture
 * jusqu'à quelques secondes après le décollage. Elle monte en une seconde, et s'éteint en deux quand l'eau se coupe.
 * <p>
 * Comme pour le grondement des moteurs ({@link EngineSound}), le volume est calculé ici d'après la distance entre le
 * joueur et le pas de tir : le son est joué « dans la tête » du joueur, sans que le jeu ne l'atténue lui-même.
 */
public final class DelugeSound extends AbstractTickableSoundInstance {
    /** Distance jusqu'à laquelle l'eau s'entend à plein volume, puis distance sur laquelle elle s'éteint, en blocs. */
    private static final double NEAR = 12.0;
    private static final double REACH = 72.0;
    /** Volume au pied du pas de tir : le déluge accompagne le lancement, il ne doit pas le couvrir. */
    private static final float FULL = 0.7F;
    /** Un seul bruit de déluge par table de lancement. */
    private static final Map<BlockPos, DelugeSound> PLAYING = new HashMap<>();

    private final LaunchTableBlockEntity table;
    private final BlockPos key;
    /** Niveau du bruit, de 0 à 1 : la montée, puis l'extinction. */
    private float level;

    private DelugeSound(LaunchTableBlockEntity table) {
        super(ModSounds.PAD_DELUGE_LOOP.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.table = table;
        this.key = table.getBlockPos().immutable();
        looping = true;
        delay = 0;
        // Placé sur le pas de tir : on entend l'eau venir de sa direction ; le volume, lui, est calculé ci-dessous.
        relative = false;
        attenuation = SoundInstance.Attenuation.NONE;
        volume = 0.01F;
        x = table.getBlockPos().getX() + 0.5;
        y = table.getBlockPos().getY() + 0.5;
        z = table.getBlockPos().getZ() + 0.5;
    }

    /**
     * Le déluge de ce pas de tir coule : son bruit commence, s'il ne joue pas déjà. Un bruit que le jeu a coupé de
     * lui-même (en quittant le monde, en rechargeant les ressources) est remplacé.
     */
    public static void start(LaunchTableBlockEntity table) {
        DelugeSound playing = PLAYING.get(table.getBlockPos());
        if (playing != null && !playing.isStopped() && Minecraft.getInstance().getSoundManager().isActive(playing)) {
            return;
        }
        DelugeSound sound = new DelugeSound(table);
        PLAYING.put(sound.key, sound);
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean flowing = !table.isRemoved() && table.isDelugeOn() && minecraft.player != null && minecraft.level != null;
        if (flowing) {
            level = Math.min(1.0F, level + 0.05F);
        } else {
            level -= 0.025F;
            if (level <= 0.0F) {
                PLAYING.remove(key, this);
                stop();
                return;
            }
        }
        double distance = minecraft.player == null ? REACH : Math.sqrt(minecraft.player.blockPosition().distSqr(key));
        float heard = (float) Mth.clamp(1.0 - (distance - NEAR) / REACH, 0.0, 1.0);
        volume = Math.max(0.001F, FULL * level * heard * heard);
    }
}
