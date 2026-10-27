package com.orbitalis.client;

import java.util.HashMap;
import java.util.Map;

import com.orbitalis.core.registry.ModSounds;
import com.orbitalis.phase1.spaceport.LaunchTableBlockEntity;
import com.orbitalis.phase2.flight.CapsuleEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Grondement des moteurs d'une fusée, de l'allumage à la fin de la poussée.
 * <p>
 * C'est une boucle : le jeu la répète sans couture tant que les moteurs brûlent, aussi longtemps que dure le vol.
 * Pour qu'on n'y entende pas une répétition, sa hauteur dérive très lentement, de quelques centièmes, à un rythme qui
 * ne retombe jamais sur la durée de la boucle.
 * <p>
 * Le volume est calculé ici, d'après la distance entre le joueur et la fusée, où qu'elle soit : plein dans la
 * cabine et autour du pas de tir, il décroît à mesure qu'elle s'éloigne. Il monte à l'allumage, comme la poussée, et
 * retombe en une demi-seconde quand les moteurs s'arrêtent ou que la fusée se disloque.
 */
public final class EngineSound extends AbstractTickableSoundInstance {
    /** Distance jusqu'à laquelle le grondement est à plein volume, puis distance sur laquelle il s'éteint, en blocs. */
    private static final double NEAR = 24.0;
    private static final double REACH = 320.0;
    /** Un seul grondement par table de lancement. */
    private static final Map<BlockPos, EngineSound> PLAYING = new HashMap<>();

    private final LaunchTableBlockEntity table;
    private final BlockPos key;
    /**
     * Le monde où la fusée a décollé. Quand l'équipage passe en orbite, le jeu cesse de faire jouer ce son sans
     * l'arrêter : sans ce repère, sa poussée resterait figée à son dernier niveau, et la cabine tremblerait sans fin.
     */
    private final net.minecraft.client.multiplayer.ClientLevel world;
    /** Niveau du grondement, de 0 à 1 : la montée en puissance, puis l'extinction. */
    private float level;
    private int age;
    /** Vrai après une première séparation : les étages supérieurs s'allument plus vite que ceux du sol. */
    private boolean relit;

    private EngineSound(LaunchTableBlockEntity table) {
        super(ModSounds.PAD_ENGINE.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.table = table;
        this.key = table.getBlockPos().immutable();
        this.world = Minecraft.getInstance().level;
        looping = true;
        delay = 0;
        // Le volume suit la distance par le calcul ci-dessous, sans que le jeu ne l'atténue (il fixerait sinon sa portée
        // une fois pour toutes au premier instant) ; mais le son est placé sur la fusée, à chaque tick : on l'entend
        // venir de sa direction, à gauche, à droite, au-dessus.
        relative = false;
        attenuation = SoundInstance.Attenuation.NONE;
        volume = 0.02F;
        Vec3 at = where();
        x = at.x;
        y = at.y;
        z = at.z;
    }

    /** Les moteurs de cette fusée brûlent : le grondement commence, s'il ne joue pas déjà. */
    public static void start(LaunchTableBlockEntity table) {
        forgetOtherWorlds();
        EngineSound playing = PLAYING.get(table.getBlockPos());
        if (playing != null && !playing.isStopped()) {
            return;
        }
        EngineSound sound = new EngineSound(table);
        PLAYING.put(sound.key, sound);
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    /** Poussée des moteurs de la fusée de cette table, de 0 à 1, telle que le grondement l'entend ; 0 s'ils sont éteints. */
    public static float thrust(BlockPos table) {
        forgetOtherWorlds();
        EngineSound sound = table == null ? null : PLAYING.get(table);
        return sound == null || sound.isStopped() ? 0.0F : sound.level;
    }

    /**
     * Le grondement le plus fort ressenti en ce point : la poussée de chaque fusée qui brûle, d'autant plus forte qu'on
     * est près d'elle et qu'elle est encore basse. Sert au tremblement de la caméra des spectateurs.
     */
    public static float rumble(Vec3 listener) {
        forgetOtherWorlds();
        float strongest = 0.0F;
        for (EngineSound sound : PLAYING.values()) {
            if (sound.isStopped()) {
                continue;
            }
            Vec3 rocket = sound.where();
            double distance = listener.distanceTo(rocket);
            double height = Math.max(0.0, rocket.y - sound.key.getY());
            float near = (float) Mth.clamp(1.0 - distance / 64.0, 0.0, 1.0);
            float low = (float) Mth.clamp(1.0 - height / 160.0, 0.0, 1.0);
            strongest = Math.max(strongest, sound.level * near * near * low);
        }
        return strongest;
    }

    /**
     * Oublie les grondements qui ne jouent plus : nés dans un autre monde que celui où se trouve le joueur, ou que le
     * jeu a lâchés. On le demande au jeu lui-même, et non à l'horloge : au décollage d'un gros lanceur, le jeu peut se
     * figer un instant, et l'horloge, recalée ensuite sur le serveur, fait un saut. Le grondement passait alors pour
     * lâché : coupé, puis relancé depuis le silence à chaque à-coup, on ne l'entendait pas décoller.
     */
    private static void forgetOtherWorlds() {
        net.minecraft.client.multiplayer.ClientLevel current = Minecraft.getInstance().level;
        net.minecraft.client.sounds.SoundManager sounds = Minecraft.getInstance().getSoundManager();
        PLAYING.values().removeIf(sound -> {
            if (sound.world == current && current != null && sounds.isActive(sound)) {
                return false;
            }
            sound.stop();
            return true;
        });
    }

    /** Où se trouve la fusée : sa capsule si elle en a une, sinon la hauteur que la table lui donne. */
    private Vec3 where() {
        CapsuleEntity capsule = CapsuleEntity.flyingFrom(key);
        if (capsule != null) {
            return capsule.position();
        }
        BlockPos pos = table.getBlockPos();
        Vec3 drift = table.renderDrift(0.0F);
        return new Vec3(pos.getX() + 0.5 + drift.x, pos.getY() + 1.0 + table.renderAscent(0.0F), pos.getZ() + 0.5 + drift.z);
    }

    @Override
    public void tick() {
        age++;
        Minecraft minecraft = Minecraft.getInstance();
        LaunchTableBlockEntity.Phase phase = table.getPhase();
        // Une table sortie de la mémoire du client ne dit plus rien : on se fie alors à la capsule, si elle vole encore.
        boolean burning = table.isRemoved() ? CapsuleEntity.flyingFrom(key) != null
                : phase == LaunchTableBlockEntity.Phase.FLIGHT || (phase == LaunchTableBlockEntity.Phase.COUNTDOWN && table.hasRocket());
        if (minecraft.player == null || minecraft.level == null) {
            burning = false;
        }
        CapsuleEntity riding = CapsuleEntity.flyingFrom(key);
        boolean coasting = burning && (riding != null ? riding.isCoasting() : !table.isRemoved() && table.enginesOut());
        if (coasting) {
            // Entre deux étages, moteurs coupés : le grondement tombe, sans s'arrêter ; il reprendra à l'allumage.
            level = Math.max(0.0F, level - 0.1F);
            relit = true;
        } else if (burning) {
            // La montée en puissance : deux secondes et demie au sol, comme la poussée ; un étage supérieur, lui,
            // s'allume en une demi-seconde.
            level = Math.min(1.0F, level + (relit ? 0.1F : 0.02F));
        } else {
            level -= 0.1F;
            if (level <= 0.0F) {
                PLAYING.remove(key, this);
                stop();
                return;
            }
        }
        Vec3 at = where();
        x = at.x;
        y = at.y;
        z = at.z;
        double distance = minecraft.player == null ? REACH : minecraft.player.position().distanceTo(at);
        float heard = (float) Mth.clamp(1.0 - (distance - NEAR) / REACH, 0.0, 1.0);
        volume = Math.max(0.001F, 0.95F * level * level * heard * heard);
        // Une dérive lente de la hauteur, sur deux rythmes qui ne s'accordent pas : la boucle ne s'entend pas revenir.
        pitch = 1.0F + 0.018F * Mth.sin(age * 0.0131F) + 0.012F * Mth.sin(age * 0.0377F + 1.3F);
    }
}
