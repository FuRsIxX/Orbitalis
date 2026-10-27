package com.orbitalis.client.particle;

import org.joml.Quaternionf;
import org.joml.Vector3fc;

import com.orbitalis.Orbitalis;
import com.orbitalis.core.registry.ModParticles;
import com.orbitalis.phase2.survival.SpaceRules;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * La planète vue depuis l'orbite : un immense disque, à la verticale du joueur, loin sous lui.
 * <p>
 * Elle suit le joueur dans ses déplacements, comme le fait un astre très lointain : on ne s'en rapproche pas
 * en volant. Elle tourne lentement sur elle-même. Les blocs d'une station, plus proches, la cachent normalement.
 * <p>
 * C'est une particule unique, créée quand le joueur est dans la dimension d'orbite et qui dure tant qu'il y reste.
 */
@EventBusSubscriber(modid = Orbitalis.MODID, value = Dist.CLIENT)
public class PlanetParticle extends SingleQuadParticle {
    /**
     * Profondeur de la planète sous le joueur, et demi-largeur du disque, en part de la distance d'affichage.
     * Le disque doit rester en deçà de cette distance : au-delà, le brouillard, noir en orbite, l'effacerait.
     */
    private static final float DEPTH = 0.22F;
    private static final float HALF_SIZE = 0.62F;
    /** Demi-largeur de la planète vue depuis la Lune, en part de la distance d'affichage. */
    private static final float MOON_SKY_SIZE = 0.11F;
    /** Durée d'un tour de la planète sur elle-même, en ticks (dix minutes). */
    private static final float TURN_TICKS = 12_000.0F;

    /** Demi-largeur de la naine rouge vue de Proxima b, halo compris, en part de la distance d'affichage. */
    private static final float PROXIMA_STAR_SIZE = 0.3F;

    /** Demi-largeur de Jupiter vue d'Europe, en part de la distance d'affichage : elle occupe une grande part du ciel. */
    private static final float EUROPA_SKY_SIZE = 0.3F;

    /** La planète affichée en ce moment, s'il y en a une ; ses images (la planète, puis Jupiter). */
    private static PlanetParticle current;
    private static SpriteSet images;

    protected PlanetParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
        hasPhysics = false;
        lifetime = Integer.MAX_VALUE;
    }

    /** La planète ne vieillit pas : elle disparaît avec le monde quand le joueur quitte l'orbite. */
    @Override
    public void tick() {
        if (!shown(level)) {
            remove();
            return;
        }
        anchor(Minecraft.getInstance().gameRenderer.mainCamera());
    }

    /**
     * Le jeu ne dessine une particule que si son point d'attache est dans le champ de vision : on le garde donc
     * juste devant le regard, où qu'il se tourne, alors que le disque, lui, reste sous le joueur.
     */
    private void anchor(Camera camera) {
        Vec3 eye = camera.position();
        Vector3fc forward = camera.forwardVector();
        x = xo = eye.x + forward.x() * 4.0;
        y = yo = eye.y + forward.y() * 4.0;
        z = zo = eye.z + forward.z() * 4.0;
    }

    @Override
    public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        // Le disque est horizontal, sa face tournée vers le haut, et tourne lentement autour de la verticale.
        float view = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0F;
        if (SpaceRules.isProxima(level) && images != null) {
            // Vue de Proxima b, la naine rouge : un grand disque rouge dans son halo, à 20° au-dessus de l'horizon, à l'est,
            // immobile (la planète lui tourne toujours la même face). Elle couvre le soleil du jeu, placé au même endroit
            // par le type de la dimension pour que la lumière soit celle du jour.
            setSprite(images.get(3, 3));
            quadSize = PROXIMA_STAR_SIZE * view;
            float distance = 0.6F * view;
            extractRotatedQuad(state, new Quaternionf(camera.rotation()), distance * 0.9397F, distance * 0.3420F, 0.0F, partialTick);
            anchor(camera);
            return;
        }
        if (SpaceRules.isEuropa(level) && images != null) {
            // Vue d'Europe, Jupiter : un disque immense, bas sur l'horizon, toujours au même endroit du ciel.
            setSprite(images.get(1, 3));
            quadSize = EUROPA_SKY_SIZE * view;
            extractRotatedQuad(state, new Quaternionf(camera.rotation()), -0.45F * view, 0.32F * view, 0.5F * view, partialTick);
            anchor(camera);
            return;
        }
        if (SpaceRules.isMoon(level)) {
            // Vue de la Lune, la planète est un petit disque bleu, haut dans le ciel, tourné vers celui qui la regarde.
            quadSize = MOON_SKY_SIZE * view;
            extractRotatedQuad(state, new Quaternionf(camera.rotation()), 0.28F * view, 0.62F * view, 0.4F * view, partialTick);
            anchor(camera);
            return;
        }
        float depth = DEPTH * view;
        quadSize = HALF_SIZE * view;
        // Sous l'orbite de Proxima, ce n'est pas la planète bleue, mais Proxima b.
        if (images != null) {
            setSprite(SpaceRules.isProximaOrbit(level) ? images.get(2, 3) : images.get(0, 3));
        }
        float turn = (level.getGameTime() % (long) TURN_TICKS + partialTick) / TURN_TICKS * (float) (Math.PI * 2);
        // Deux faces dos à dos : le disque se voit quel que soit le sens dans lequel le jeu dessine ses particules.
        extractRotatedQuad(state, new Quaternionf().rotateY(turn).rotateX((float) (-Math.PI / 2)), 0.0F, -depth, 0.0F, partialTick);
        extractRotatedQuad(state, new Quaternionf().rotateY(turn).rotateX((float) (Math.PI / 2)), 0.0F, -depth - 0.05F, 0.0F, partialTick);

        anchor(camera);
    }

    @Override
    public int getLightCoords(float partialTick) {
        return LightCoordsUtil.FULL_BRIGHT;
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    /**
     * Vrai là où cette particule dessine un astre : sur Proxima b (sa naine rouge, dans le ciel du jeu). Dans le vide,
     * les astres font partie du ciel de l'espace (voir {@code SkyBodies}) ; la particule ne les remplace que si un
     * pack de shaders a pris la main sur le ciel.
     */
    private static boolean shown(net.minecraft.world.level.Level level) {
        return SpaceRules.isProxima(level) || SpaceRules.isVacuum(level) && com.orbitalis.client.SpaceSky.shadersActive();
    }

    public static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites) {
        images = sprites;
        return (type, level, x, y, z, xd, yd, zd, random) -> {
            current = new PlanetParticle(level, x, y, z, sprites.first());
            return current;
        };
    }

    /** Tant que le joueur est en orbite, la planète est là : on la (re)crée si elle manque. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !shown(minecraft.level)) {
            current = null;
            return;
        }
        if (current == null || !current.isAlive() || current.level != minecraft.level) {
            Vec3 eye = minecraft.player.getEyePosition();
            minecraft.level.addAlwaysVisibleParticle(ModParticles.PLANET.get(), true, eye.x, eye.y, eye.z, 0.0, 0.0, 0.0);
        }
    }
}
