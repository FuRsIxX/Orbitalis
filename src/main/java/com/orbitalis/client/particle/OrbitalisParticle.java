package com.orbitalis.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;

/**
 * Particule du mod. Toutes partagent le même comportement, réglé par un {@link Style} : une image qui évolue
 * avec l'âge de la particule (une volute qui se défait, une flamme qui rougit), une taille qui grandit ou
 * rétrécit, une pesanteur (négative pour ce qui monte), un frottement, et un fondu en fin de vie.
 */
public class OrbitalisParticle extends SingleQuadParticle {
    /**
     * Réglages d'un type de particule.
     *
     * @param minLifetime durée de vie minimale, en ticks
     * @param maxLifetime durée de vie maximale, en ticks
     * @param size        taille de départ, en blocs
     * @param growth      taille en fin de vie, en multiple de la taille de départ (1 = constante)
     * @param gravity     accélération vers le bas, en blocs par tick² (négative : la particule monte)
     * @param friction    part de la vitesse conservée à chaque tick
     * @param fade        part de la vie, à la fin, pendant laquelle la particule s'efface (0 = jamais)
     * @param alpha       opacité de départ
     * @param glowing     vrai si la particule est lumineuse : elle se voit dans le noir
     * @param collides    vrai si elle se heurte aux blocs (la fumée roule alors sur le sol)
     * @param tumbled     vrai si chaque particule est tournée au hasard ; faux si son image doit rester droite
     * @param solid       vrai si la particule est pleine : ses pixels sont opaques, elle ne devient jamais
     *                    transparente et rétrécit en fin de vie au lieu de s'effacer. Un nuage de particules pleines
     *                    reste juste vu de l'intérieur : aucune ne masque à tort celles qui sont derrière elle
     */
    public record Style(int minLifetime, int maxLifetime, float size, float growth, float gravity, float friction, float fade,
            float alpha, boolean glowing, boolean collides, boolean tumbled, boolean solid) {
        /** Style ordinaire : chaque particule est tournée au hasard. */
        public Style(int minLifetime, int maxLifetime, float size, float growth, float gravity, float friction, float fade,
                float alpha, boolean glowing, boolean collides) {
            this(minLifetime, maxLifetime, size, growth, gravity, friction, fade, alpha, glowing, collides, true, false);
        }

        public Style(int minLifetime, int maxLifetime, float size, float growth, float gravity, float friction, float fade,
                float alpha, boolean glowing, boolean collides, boolean tumbled) {
            this(minLifetime, maxLifetime, size, growth, gravity, friction, fade, alpha, glowing, collides, tumbled, false);
        }

        /** Le même style, en particules pleines. */
        public Style asSolid() {
            return new Style(minLifetime, maxLifetime, size, growth, gravity, friction, fade, 1.0F, glowing, collides, tumbled, true);
        }

        /** Grosses volutes de fumée des moteurs-fusées. */
        public static final Style EXHAUST_SMOKE = new Style(110, 200, 0.4F, 2.6F, -0.0006F, 0.94F, 0.3F, 1.0F, false, true).asSolid();
        /** Jet de flamme des moteurs-fusées. */
        public static final Style ENGINE_FLAME = new Style(10, 18, 0.5F, 0.35F, 0.0F, 0.96F, 0.3F, 1.0F, true, false);
        /** Étincelles. */
        public static final Style SPARK = new Style(12, 26, 0.07F, 0.5F, 0.035F, 0.97F, 0.3F, 1.0F, true, true);
        /** Brume cryogénique. */
        public static final Style CRYO_MIST = new Style(30, 55, 0.16F, 2.2F, 0.0012F, 0.93F, 0.6F, 0.6F, false, true);
        /** Vapeur. */
        public static final Style VAPOR = new Style(18, 34, 0.1F, 2.4F, -0.0015F, 0.93F, 0.6F, 0.7F, false, false);
        /** Suie de cheminée. */
        public static final Style SOOT = new Style(40, 70, 0.14F, 2.6F, -0.0012F, 0.95F, 0.5F, 0.85F, false, false);

        // Pas de tir.
        /**
         * Fumée de lancement : des volutes de deux à trois blocs, nombreuses et serrées, lentes à se dissiper, qui
         * roulent sur le sol. Leurs pixels ont la taille de ceux des blocs.
         */
        public static final Style LAUNCH_SMOKE = new Style(130, 220, 0.45F, 2.4F, -0.0007F, 0.95F, 0.3F, 1.0F, false, true).asSolid();
        /** Vapeur du déluge : elle gonfle et monte vite, et ne dure que quelques secondes. */
        public static final Style LAUNCH_STEAM = new Style(45, 85, 0.4F, 2.8F, -0.002F, 0.93F, 0.35F, 1.0F, false, false).asSolid();
        /** Eau du déluge : elle retombe en cloche. */
        public static final Style DELUGE_WATER = new Style(14, 26, 0.34F, 1.0F, 0.045F, 0.98F, 0.2F, 0.95F, false, true, false);
        /** Feu du carneau : bref et lumineux. */
        public static final Style TRENCH_FIRE = new Style(12, 24, 0.55F, 2.0F, -0.002F, 0.92F, 0.4F, 1.0F, true, false).asSolid();
        /** Escarbille : elle retombe, rebondit et s'éteint. */
        public static final Style LAUNCH_EMBER = new Style(30, 60, 0.09F, 0.4F, 0.02F, 0.98F, 0.3F, 1.0F, true, true);
        /** Poussière : basse, elle s'étale au ras du sol. */
        public static final Style LAUNCH_DUST = new Style(60, 110, 0.4F, 2.6F, 0.0003F, 0.9F, 0.4F, 1.0F, false, true).asSolid();
        /** Voile de chaleur : presque invisible, il monte en ondulant. */
        public static final Style HEAT_HAZE = new Style(16, 30, 1.2F, 1.7F, -0.004F, 0.97F, 0.5F, 0.35F, true, false, false);
        /** Boule de feu d'une explosion : grosse, lumineuse, brève. */
        public static final Style BLAST_FIRE = new Style(14, 30, 1.7F, 2.3F, -0.004F, 0.9F, 0.4F, 1.0F, true, false).asSolid();
        /** Fumée d'une explosion : de grosses volutes sombres qui montent longtemps, en champignon. */
        public static final Style BLAST_SMOKE = new Style(130, 230, 1.3F, 3.2F, -0.0035F, 0.95F, 0.3F, 1.0F, false, false).asSolid();
        /** Nuage glacé d'ergols cryogéniques : blanc, il gonfle vite et retombe. */
        public static final Style BLAST_CRYO = new Style(60, 110, 1.4F, 3.0F, 0.0008F, 0.93F, 0.35F, 1.0F, false, false).asSolid();
        /** Éclair d'allumage : immense et très bref. */
        public static final Style LAUNCH_FLASH = new Style(5, 8, 2.4F, 1.8F, 0.0F, 1.0F, 0.9F, 0.8F, true, false, false);
    }

    private final Style style;
    private final SpriteSet sprites;
    private final float startAlpha;

    protected OrbitalisParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, Style style,
            SpriteSet sprites) {
        super(level, x, y, z, sprites.first());
        this.style = style;
        this.sprites = sprites;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.lifetime = style.minLifetime() + random.nextInt(Math.max(1, style.maxLifetime() - style.minLifetime() + 1));
        // Chaque particule a sa propre taille, à un quart près : un nuage de volutes identiques ferait faux.
        this.quadSize = style.size() * (0.75F + random.nextFloat() * 0.5F);
        this.gravity = style.gravity();
        this.friction = style.friction();
        this.hasPhysics = style.collides();
        this.startAlpha = style.alpha();
        this.alpha = startAlpha;
        this.roll = style.tumbled() ? random.nextFloat() * 6.2832F : 0.0F;
        this.oRoll = roll;
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        yd -= gravity;
        move(xd, yd, zd);
        xd *= friction;
        yd *= friction;
        zd *= friction;
        if (onGround) {
            // Au sol, la fumée s'étale et les étincelles s'arrêtent.
            xd *= 0.8;
            zd *= 0.8;
        }
        setSpriteFromAge(sprites);
        float life = age / (float) lifetime;
        if (!style.solid() && style.fade() > 0.0F && life > 1.0F - style.fade()) {
            alpha = startAlpha * Math.max(0.0F, (1.0F - life) / style.fade());
        }
    }

    /** La taille passe en douceur de la taille de départ à la taille de fin de vie. */
    @Override
    public float getQuadSize(float partialTick) {
        float life = Math.min(1.0F, (age + partialTick) / lifetime);
        float size = quadSize * (1.0F + (style.growth() - 1.0F) * life);
        // Une particule pleine ne s'efface pas : elle rétrécit jusqu'à disparaître.
        if (style.solid() && style.fade() > 0.0F && life > 1.0F - style.fade()) {
            size *= Math.max(0.0F, (1.0F - life) / style.fade());
        }
        return size;
    }

    @Override
    public int getLightCoords(float partialTick) {
        return style.glowing() ? LightCoordsUtil.FULL_BRIGHT : super.getLightCoords(partialTick);
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return style.solid() ? SingleQuadParticle.Layer.OPAQUE : SingleQuadParticle.Layer.TRANSLUCENT;
    }

    /** Fabrique des particules d'un style donné, à enregistrer pour un type de particule. */
    public static ParticleProvider<SimpleParticleType> provider(Style style, SpriteSet sprites) {
        return (type, level, x, y, z, xd, yd, zd, random) -> (Particle) new OrbitalisParticle(level, x, y, z, xd, yd, zd, style, sprites);
    }
}
