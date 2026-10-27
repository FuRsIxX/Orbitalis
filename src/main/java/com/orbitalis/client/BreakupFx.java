package com.orbitalis.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.orbitalis.Orbitalis;
import com.orbitalis.client.render.ModelCache;
import com.orbitalis.core.network.BreakupPayload;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.registry.ModParticles;
import com.orbitalis.core.registry.ModSounds;
import com.orbitalis.phase1.spaceport.RocketBlock;
import com.orbitalis.phase1.spaceport.RocketBreakup;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Explosion d'une fusée, telle qu'on la voit et qu'on la ressent. Le serveur décide de l'accident et de ses dégâts
 * ({@link RocketBreakup}) ; ici, chaque joueur en reçoit l'annonce et la met en scène, avec sa propre physique.
 * <ul>
 * <li><b>La fusée reste là.</b> Ses tronçons ne disparaissent pas : ils tiennent debout jusqu'à ce que la réaction
 * en chaîne les atteigne, puis chacun est arraché, projeté, tourne sur lui-même, retombe, rebondit et s'immobilise,
 * en traînant sa fumée. Chaque tronçon a sa masse : un moteur part moins loin qu'une coiffe vide.</li>
 * <li><b>La boule de feu.</b> Un cœur aveuglant qui gonfle puis s'éteint, des flammes projetées en sphère, et une
 * colonne de fumée qui monte en champignon.</li>
 * <li><b>L'onde de choc.</b> Un anneau qui court au ras du sol, bien plus vite que les débris, suivi d'un mur de
 * poussière. C'est quand il atteint le joueur que tout arrive à la fois.</li>
 * <li><b>Ce que l'on ressent.</b> L'éclair arrive tout de suite. Le son, lui, met le temps qu'il faut pour venir : de
 * loin, on voit la fusée éclater en silence, puis le coup arrive, avec la secousse de la caméra. Il n'y en a qu'un par
 * accident, choisi selon la distance : de près, de loin, de très loin.</li>
 * <li><b>Les tôles.</b> Les tronçons qui retombent, et l'épave qui s'écrase, font un fracas de métal, pas une
 * seconde explosion.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Orbitalis.MODID, value = Dist.CLIENT)
public final class BreakupFx {
    private static final Identifier SHOCKWAVE = Orbitalis.id("textures/entity/shockwave.png");
    private static final Identifier GLOW = Orbitalis.id("textures/entity/led_glow.png");
    /** Vitesse du son, en blocs par tick : le décalage se sent sans faire attendre. */
    private static final double SOUND_SPEED = 8.0;
    /** En deçà, l'explosion s'entend de près ; au-delà de la seconde distance, de très loin. En blocs. */
    private static final double NEAR_UNTIL = 48.0;
    private static final double MID_UNTIL = 140.0;
    /** Une explosion lointaine s'entend deux à trois secondes après s'être vue, en ticks. */
    private static final int FAR_DELAY_MIN = 40;
    private static final int FAR_DELAY_MAX = 60;
    /** Fracas de tôles : pas plus de tant par accident, et pas deux à moins de tant de ticks d'écart. */
    private static final int CLATTERS = 2;
    /** Distance au-delà de laquelle le fracas du métal ne s'entend plus, en blocs. */
    private static final double DEBRIS_REACH = 100.0;
    private static final int CLATTER_GAP = 16;
    private static final double GOLDEN_ANGLE = 2.399963;
    private static final double GRAVITY = 0.045;

    /** Un tronçon de fusée : debout tant que la chaîne ne l'a pas atteint, puis projeté. */
    private static final class Piece {
        BlockState state;
        RocketBreakup.Burst burst;
        Vec3 pos;
        Vec3 last;
        Vec3 velocity = Vec3.ZERO;
        float pitch;
        float roll;
        float lastPitch;
        float lastRoll;
        float spinPitch;
        float spinRoll;
        int burstAt;
        boolean flying;
        /** Vrai une fois qu'il a touché le sol : son fracas ne se fait entendre qu'au premier choc. */
        boolean landed;
        boolean resting;
        int restTicks;
        /** Masse relative : un tronçon lourd part moins vite et rebondit moins. */
        float mass;
        int index;
        /** Puissance de son éclatement : celle de l'accident. */
        float scale;
        /** Orientation de la fusée quand elle a cédé : le tronçon la garde, puis tourne sur lui-même à partir d'elle. */
        Quaternionf attitude;
        /** Élan qu'il tenait de la fusée : elle tournait, et il s'écarte de son axe. */
        Vec3 inherited = Vec3.ZERO;
        /** Axe de la fusée, vers son nez si le tronçon est au-dessus de la rupture, vers ses moteurs sinon. */
        Vec3 away = Vec3.ZERO;
    }

    /** Forme d'une lueur : le cœur d'une boule de feu, un anneau à plat sur le sol, ou un anneau face à l'observateur. */
    private enum Shape {
        GLOW,
        GROUND_RING,
        FACING_RING
    }

    /** Un anneau d'onde de choc, ou un cœur de boule de feu : quelque chose qui gonfle puis s'efface. */
    private record Bloom(Vec3 at, int born, int life, float size, int color, Shape shape) {
    }

    /** Un son à faire entendre quand il aura eu le temps d'arriver. */
    private static final class Echo {
        int delay;
        Vec3 at;
        SoundEvent sound;
        /** Distance à laquelle on le fait entendre, dans sa direction : un son lointain reste audible, et situé. */
        double heardAt;
        float shake;
    }

    /** L'éclatement d'un groupe de réservoirs : une seule boule de feu, à son heure. */
    private record Blast(Vec3 at, int tick, RocketBreakup.Burst burst, float scale, float strength) {
    }

    private static final List<Blast> BLASTS = new ArrayList<>();
    private static final List<Piece> PIECES = new ArrayList<>();
    private static final List<Bloom> BLOOMS = new ArrayList<>();
    private static final List<Echo> ECHOES = new ArrayList<>();
    private static int clock;
    /** Fracas de tôles encore permis pour l'accident en cours, et tick du dernier. */
    private static int clattersLeft;
    private static int lastClatter;
    /** Secousse de la caméra, en degrés, et éblouissement de l'écran, de 0 à 1 : tous deux retombent vite. */
    private static float shake;
    private static float flash;

    private BreakupFx() {
    }

    // ------------------------------------------------------------------
    // Arrivée d'un accident
    // ------------------------------------------------------------------

    public static void receive(BreakupPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Vec3 base = payload.base();
        float scale = payload.radius() / 5.0F;
        if (payload.parts().isEmpty()) {
            // Un impact seul : l'épave qui touche le sol. Elle s'écrase, elle n'explose pas une seconde fois.
            crash(minecraft.level, base, payload.radius());
            return;
        }
        RocketBlock.Part[] parts = RocketBlock.Part.values();
        RocketBlock.Size[] sizes = RocketBlock.Size.values();
        RocketBlock.Size size = sizes[Math.floorMod(payload.size(), sizes.length)];
        int origin = Math.clamp(payload.origin(), 0, payload.parts().size() - 1);
        for (int i = 0; i < payload.parts().size(); i++) {
            RocketBlock.Part part = parts[Math.floorMod(payload.parts().get(i), parts.length)];
            Piece piece = new Piece();
            piece.state = ModBlocks.ROCKET.get().defaultBlockState().setValue(RocketBlock.PART, part).setValue(RocketBlock.SIZE, size);
            piece.burst = RocketBreakup.burstOf(part, payload.cryogenic());
            // Chaque tronçon est là où on le voyait : sur l'axe de la fusée, penchée comme son pilote la tenait.
            Vector3f arm = payload.attitude().transform(new Vector3f(0.0F, i + 0.5F - payload.pivot(), 0.0F));
            piece.pos = base.add(arm.x, payload.pivot() + arm.y, arm.z);
            piece.last = piece.pos;
            piece.attitude = new Quaternionf(payload.attitude());
            // La fusée tournait : chaque tronçon part avec la vitesse qu'il avait au bout de son bras de levier.
            Vector3f swing = new Vector3f(payload.spin()).cross(arm);
            piece.inherited = new Vec3(swing.x, swing.y, swing.z);
            Vector3f nose = payload.attitude().transform(new Vector3f(0.0F, 1.0F, 0.0F));
            piece.away = new Vec3(nose.x, nose.y, nose.z).scale(Math.signum(i - origin));
            piece.index = i;
            piece.scale = scale * (size == RocketBlock.Size.LARGE ? 1.4F : 1.0F);
            // Toute la fusée saute d'un coup.
            piece.burstAt = clock;
            String name = part.name();
            piece.mass = name.startsWith("ENGINE") ? 1.6F : name.startsWith("TANK") ? 1.2F : 0.7F;
            PIECES.add(piece);
        }
        if (payload.shock()) {
            shock(base.add(0.0, origin + 0.5, 0.0), payload.radius(), true);
        }
        // Une seule boule de feu, là où la fusée a cédé.
        List<RocketBlock.Part> list = new ArrayList<>();
        for (int value : payload.parts()) {
            list.add(parts[Math.floorMod(value, parts.length)]);
        }
        float big = scale * (size == RocketBlock.Size.LARGE ? 1.4F : 1.0F) * 1.3F;
        Vector3f arm = payload.attitude().transform(new Vector3f(0.0F, origin + 0.5F - payload.pivot(), 0.0F));
        BLASTS.add(new Blast(base.add(arm.x, payload.pivot() + arm.y, arm.z), clock,
                RocketBreakup.groupBurst(list, 0, list.size(), payload.cryogenic()), big, 1.0F));
        // Les tôles ne se font entendre qu'une fois le coup passé.
        clattersLeft = CLATTERS;
        lastClatter = clock;
    }

    /**
     * L'onde de choc : deux anneaux qui courent sur le terrain, un troisième, plus pâle, qui gonfle face à
     * l'observateur (le dôme de condensation, visible d'où qu'on regarde), et le mur de poussière qui les suit.
     */
    private static void shock(Vec3 at, float radius, boolean ground) {
        // Le terrain autour du pas de tir, et non son tablier : l'onde court sur le sol.
        double terrain = Double.MAX_VALUE;
        for (int k = 0; k < 4; k++) {
            double angle = k * Math.PI / 2.0 + 0.6;
            terrain = Math.min(terrain, groundBelow(at.add(Math.cos(angle) * (radius + 8.0), 4.0, Math.sin(angle) * (radius + 8.0))));
        }
        Vec3 floor = new Vec3(at.x, terrain + 0.12, at.z);
        BLOOMS.add(new Bloom(floor, clock, 22, radius * 5.0F, 0xFFFFFFFF, Shape.GROUND_RING));
        BLOOMS.add(new Bloom(floor.add(0.0, 0.06, 0.0), clock + 3, 30, radius * 3.4F, 0xFFFFD9A0, Shape.GROUND_RING));
        BLOOMS.add(new Bloom(at, clock, 12, radius * 3.0F, 0xB0FFFFFF, Shape.FACING_RING));
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !ground) {
            return;
        }
        int count = 64 + Math.round(radius * 14);
        for (int k = 0; k < count; k++) {
            double angle = k * Math.PI * 2 / count;
            double speed = 0.7 + 0.16 * radius + 0.15 * (k % 3);
            level.addAlwaysVisibleParticle(ModParticles.LAUNCH_DUST.get(), true, floor.x + Math.cos(angle) * 2.0, floor.y + 0.4,
                    floor.z + Math.sin(angle) * 2.0, Math.cos(angle) * speed, 0.03 + 0.04 * (k % 2), Math.sin(angle) * speed);
        }
    }

    /**
     * Le coup : un seul son, celui qui convient à la distance d'où on l'entend. Il arrive d'autant plus tard qu'on
     * est loin (deux à trois secondes pour une explosion lointaine), et secoue la caméra à ce moment-là.
     */
    private static void thunder(Vec3 at, float radius, float strength) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        double distance = minecraft.player.getEyePosition().distanceTo(at);
        float reach = radius * 14.0F;
        float felt = (float) Math.max(0.0, 1.0 - distance / reach) * strength;
        // L'éclair, lui, n'attend pas.
        flash = Math.max(flash, 0.85F * (float) Math.max(0.0, 1.0 - distance / (radius * 9.0F)) * strength);
        Echo boom = new Echo();
        boom.at = at;
        if (distance < NEAR_UNTIL) {
            boom.sound = ModSounds.EXPLOSION_NEAR.get();
            boom.delay = (int) (distance / SOUND_SPEED);
            boom.heardAt = Math.min(distance * 0.25, 8.0);
        } else if (distance < MID_UNTIL) {
            boom.sound = ModSounds.EXPLOSION_MID.get();
            boom.delay = (int) (distance / SOUND_SPEED);
            boom.heardAt = 14.0;
        } else {
            boom.sound = ModSounds.EXPLOSION_FAR.get();
            boom.delay = (int) Math.clamp(distance / SOUND_SPEED, FAR_DELAY_MIN, FAR_DELAY_MAX);
            boom.heardAt = 20.0;
        }
        boom.shake = (1.2F + radius * 0.7F) * felt;
        ECHOES.add(boom);
    }

    /**
     * Fait entendre un son dans la direction d'où il vient, à cette distance de l'oreille : son fichier est déjà
     * celui d'un son proche ou lointain, il n'a pas à être affaibli une seconde fois par le jeu. Le jeu l'atténue
     * en ligne droite jusqu'à 64 blocs : à 8 blocs il sort presque à plein niveau, à 16 aux trois quarts.
     */
    private static void hear(ClientLevel level, SoundEvent sound, Vec3 from, double heardAt) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        Vec3 ear = minecraft.player.getEyePosition();
        Vec3 toward = from.subtract(ear);
        double distance = toward.length();
        Vec3 at = distance <= heardAt || distance < 0.01 ? from : ear.add(toward.scale(heardAt / distance));
        SimpleSoundInstance instance = new SimpleSoundInstance(sound, SoundSource.BLOCKS, 4.0F, 1.0F, SoundInstance.createUnseededRandom(), at.x, at.y, at.z);
        SoundEngine.PlayResult result = minecraft.getSoundManager().play(instance);
        Orbitalis.LOGGER.debug("Son d'accident : {} ({}), venu de {} blocs, entendu à {} blocs, tick {} : {}", sound.location(),
                instance.getSound() == null ? "aucun fichier" : instance.getSound().getLocation(), Math.round(distance),
                Math.round(Math.min(distance, heardAt)), clock, result);
    }

    /**
     * Le fracas du métal, là où il se produit : plein son tout près, de plus en plus sourd en s'éloignant, inaudible
     * au-delà d'une centaine de blocs. Une épave qui s'écrase à l'horizon ne s'entend pas.
     */
    private static void clang(ClientLevel level, Vec3 at) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        double distance = minecraft.player.getEyePosition().distanceTo(at);
        if (distance <= DEBRIS_REACH) {
            hear(level, ModSounds.DEBRIS_FALL.get(), at, distance <= 20.0 ? distance : 20.0 + (distance - 20.0) * 0.5);
        }
    }

    /** Un fracas de tôles, s'il en reste à faire entendre pour cet accident et que le précédent est assez loin. */
    private static void clatter(ClientLevel level, Vec3 at) {
        if (clattersLeft <= 0 || clock - lastClatter < CLATTER_GAP) {
            return;
        }
        clattersLeft--;
        lastClatter = clock;
        clang(level, at);
    }

    /** L'épave touche le sol : de la poussière, de la fumée, quelques escarbilles, et le fracas du métal. */
    private static void crash(ClientLevel level, Vec3 at, float radius) {
        int count = 28 + Math.round(radius * 6);
        for (int k = 0; k < count; k++) {
            double angle = k * Math.PI * 2 / count;
            double speed = 0.3 + 0.06 * radius + 0.1 * (k % 3);
            level.addAlwaysVisibleParticle(ModParticles.LAUNCH_DUST.get(), true, at.x + Math.cos(angle), at.y + 0.3, at.z + Math.sin(angle),
                    Math.cos(angle) * speed, 0.04 + 0.05 * (k % 2), Math.sin(angle) * speed);
        }
        for (int k = 0; k < 14; k++) {
            double angle = k * GOLDEN_ANGLE;
            double out = 0.04 + 0.1 * ((k * 3) % 7) / 6.0;
            level.addAlwaysVisibleParticle(ModParticles.BLAST_SMOKE.get(), true, at.x + Math.cos(angle) * 0.6, at.y + 0.4, at.z + Math.sin(angle) * 0.6,
                    Math.cos(angle) * out, 0.1 + 0.25 * (k % 5) / 4.0, Math.sin(angle) * out);
            level.addParticle(ModParticles.LAUNCH_EMBER.get(), at.x, at.y + 0.3, at.z, Math.cos(angle) * 0.3, 0.2 + 0.3 * (k % 4) / 3.0, Math.sin(angle) * 0.3);
        }
        // Si des tôles viennent de tomber, leur fracas couvre déjà celui-ci.
        if (clock - lastClatter >= 10) {
            clang(level, at);
        }
        lastClatter = clock;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            double distance = minecraft.player.position().distanceTo(at);
            shake = Math.min(7.0F, shake + (0.8F + radius * 0.3F) * (float) Math.max(0.0, 1.0 - distance / (radius * 10.0F)));
        }
    }

    /** Hauteur du sol sous un point : là où court l'onde de choc. */
    private static double groundBelow(Vec3 at) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return at.y;
        }
        BlockPos.MutableBlockPos pos = BlockPos.containing(at).mutable();
        for (int k = 0; k < 48 && pos.getY() > level.getMinY(); k++) {
            if (level.getBlockState(pos).blocksMotion()) {
                return pos.getY() + 1.0;
            }
            pos.move(0, -1, 0);
        }
        return at.y;
    }

    // ------------------------------------------------------------------
    // Un tronçon qui éclate
    // ------------------------------------------------------------------

    /** Boule de feu d'un tronçon : son cœur lumineux, ses flammes en sphère, sa fumée qui monte. */
    private static void fireball(ClientLevel level, Vec3 at, RocketBreakup.Burst burst, float scale) {
        int core = switch (burst) {
            case CRYO -> 0xFFD8F1FF;
            case SOLID -> 0xFFFFC865;
            case SHELL -> 0xFFFFE8C0;
            default -> 0xFFFFB23E;
        };
        BLOOMS.add(new Bloom(at, clock, 12, 5.0F + 5.0F * scale, 0xFFFFFFFF, Shape.GLOW));
        BLOOMS.add(new Bloom(at, clock + 1, 26, 6.5F + 6.5F * scale, core, Shape.GLOW));
        ParticleOptions flame = burst == RocketBreakup.Burst.CRYO ? ModParticles.BLAST_CRYO.get() : ModParticles.BLAST_FIRE.get();
        ParticleOptions smoke = burst == RocketBreakup.Burst.CRYO ? ModParticles.BLAST_CRYO.get() : ModParticles.BLAST_SMOKE.get();
        // Flammes projetées en sphère : des directions réparties sans tirage au sort (spirale de Fibonacci).
        int flames = burst == RocketBreakup.Burst.SHELL ? 12 : Math.round(34 + 30 * scale);
        for (int k = 0; k < flames; k++) {
            double y = 1.0 - 2.0 * (k + 0.5) / flames;
            double ring = Math.sqrt(1.0 - y * y);
            double angle = k * GOLDEN_ANGLE;
            double speed = (0.3 + 0.4 * scale) * (0.5 + 0.5 * ((k * 7) % 5) / 4.0);
            level.addAlwaysVisibleParticle(flame, true, at.x, at.y, at.z, Math.cos(angle) * ring * speed, y * speed + 0.05,
                    Math.sin(angle) * ring * speed);
        }
        // Le champignon : la fumée monte vite au centre, et s'étale en haut.
        int puffs = burst == RocketBreakup.Burst.SHELL ? 8 : Math.round(16 + 16 * scale);
        for (int k = 0; k < puffs; k++) {
            double angle = k * GOLDEN_ANGLE;
            double out = 0.05 + 0.16 * ((k * 3) % 7) / 6.0;
            level.addAlwaysVisibleParticle(smoke, true, at.x + Math.cos(angle) * 0.6, at.y + 0.4, at.z + Math.sin(angle) * 0.6,
                    Math.cos(angle) * out, 0.15 + 0.4 * (k % 5) / 4.0, Math.sin(angle) * out);
        }
        if (burst == RocketBreakup.Burst.FIRE) {
            for (int k = 0; k < 14; k++) {
                double angle = k * GOLDEN_ANGLE;
                level.addAlwaysVisibleParticle(ModParticles.SOOT.get(), true, at.x, at.y + 1.0, at.z, Math.cos(angle) * 0.08, 0.25, Math.sin(angle) * 0.08);
            }
        }
        int embers = burst == RocketBreakup.Burst.SOLID ? 60 : 24;
        for (int k = 0; k < embers; k++) {
            double angle = k * GOLDEN_ANGLE;
            double speed = 0.35 + 0.45 * ((k * 5) % 9) / 8.0;
            level.addParticle(ModParticles.LAUNCH_EMBER.get(), at.x, at.y, at.z, Math.cos(angle) * speed, 0.2 + 0.5 * ((k * 3) % 7) / 6.0,
                    Math.sin(angle) * speed);
        }
    }

    private static void detonate(ClientLevel level, Piece piece, float scale) {
        piece.flying = true;
        // Chaque tronçon part dans sa direction, d'autant plus vite qu'il est léger ; un sur trois est aussi jeté en l'air.
        double angle = piece.index * GOLDEN_ANGLE + 0.7;
        double speed = (0.28 + 0.22 * scale) / piece.mass * (0.8 + 0.2 * (piece.index % 3));
        piece.velocity = new Vec3(Math.cos(angle) * speed, (0.32 + 0.14 * (piece.index % 4)) / Math.sqrt(piece.mass), Math.sin(angle) * speed)
                // Le souffle chasse aussi les tronçons le long de l'axe de la fusée, de part et d'autre de la rupture,
                // et chacun garde l'élan qu'il tenait d'elle.
                .add(piece.away.scale(0.22 * scale / piece.mass)).add(piece.inherited);
        piece.spinPitch = (float) (14.0 + 9.0 * (piece.index % 3)) / piece.mass * (piece.index % 2 == 0 ? 1 : -1);
        piece.spinRoll = (float) (9.0 + 7.0 * (piece.index % 4)) / piece.mass;
        // La boule de feu est celle de son groupe (voir BLASTS) : le tronçon, lui, ne fait que partir en fumant.
        level.addAlwaysVisibleParticle(ModParticles.BLAST_SMOKE.get(), true, piece.pos.x, piece.pos.y, piece.pos.z, piece.velocity.x * 0.3, 0.05,
                piece.velocity.z * 0.3);
    }

    // ------------------------------------------------------------------
    // Déroulement
    // ------------------------------------------------------------------

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            PIECES.clear();
            BLASTS.clear();
            BLOOMS.clear();
            ECHOES.clear();
            shake = flash = 0.0F;
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        clock++;
        shake *= 0.86F;
        flash *= 0.78F;

        for (Iterator<Echo> iterator = ECHOES.iterator(); iterator.hasNext();) {
            Echo echo = iterator.next();
            if (echo.delay-- <= 0) {
                hear(level, echo.sound, echo.at, echo.heardAt);
                shake = Math.min(7.0F, shake + echo.shake);
                iterator.remove();
            }
        }
        BLOOMS.removeIf(bloom -> clock - bloom.born() > bloom.life());
        for (Iterator<Blast> iterator = BLASTS.iterator(); iterator.hasNext();) {
            Blast blast = iterator.next();
            if (clock >= blast.tick()) {
                fireball(level, blast.at(), blast.burst(), blast.scale());
                thunder(blast.at(), blast.scale() * 5.0F, blast.strength());
                iterator.remove();
            }
        }

        for (Iterator<Piece> iterator = PIECES.iterator(); iterator.hasNext();) {
            Piece piece = iterator.next();
            piece.last = piece.pos;
            piece.lastPitch = piece.pitch;
            piece.lastRoll = piece.roll;
            if (!piece.flying) {
                if (clock >= piece.burstAt) {
                    detonate(level, piece, piece.scale);
                }
                continue;
            }
            if (piece.resting) {
                // Posé, le tronçon fume encore, puis s'efface.
                if (++piece.restTicks > 90) {
                    iterator.remove();
                } else if (piece.restTicks % 6 == 0 && piece.burst != RocketBreakup.Burst.SHELL) {
                    level.addParticle(ModParticles.SOOT.get(), piece.pos.x, piece.pos.y + 0.4, piece.pos.z, 0.0, 0.06, 0.0);
                }
                continue;
            }
            piece.velocity = piece.velocity.add(0.0, -GRAVITY, 0.0).scale(0.985);
            Vec3 next = piece.pos.add(piece.velocity);
            piece.pitch += piece.spinPitch;
            piece.roll += piece.spinRoll;
            // Le sol : le tronçon rebondit, perd de l'élan, et finit par s'immobiliser.
            BlockPos under = BlockPos.containing(next.x, next.y - 0.5, next.z);
            if (piece.velocity.y < 0.0 && level.getBlockState(under).blocksMotion()) {
                next = new Vec3(next.x, under.getY() + 1.5, next.z);
                double bounce = -piece.velocity.y * 0.32 / piece.mass;
                piece.velocity = new Vec3(piece.velocity.x * 0.55, bounce, piece.velocity.z * 0.55);
                piece.spinPitch *= 0.45F;
                piece.spinRoll *= 0.45F;
                for (int k = 0; k < 6; k++) {
                    double angle = k * Math.PI / 3.0;
                    level.addParticle(ModParticles.LAUNCH_DUST.get(), next.x, next.y - 0.4, next.z, Math.cos(angle) * 0.15, 0.03, Math.sin(angle) * 0.15);
                }
                if (!piece.landed) {
                    piece.landed = true;
                    clatter(level, next);
                }
                if (bounce < 0.09) {
                    piece.resting = true;
                    piece.velocity = Vec3.ZERO;
                }
            } else if (level.getBlockState(BlockPos.containing(next)).blocksMotion()) {
                // Un mur : le tronçon repart d'où il vient.
                piece.velocity = new Vec3(-piece.velocity.x * 0.4, piece.velocity.y, -piece.velocity.z * 0.4);
                next = piece.pos;
            }
            piece.pos = next;
            // La traînée : feu tant que le tronçon brûle, fumée ensuite.
            if (clock % 2 == 0) {
                level.addAlwaysVisibleParticle(piece.burst == RocketBreakup.Burst.CRYO ? ModParticles.VAPOR.get() : ModParticles.EXHAUST_SMOKE.get(),
                        true, piece.pos.x, piece.pos.y, piece.pos.z, 0.0, 0.02, 0.0);
                if (piece.burst == RocketBreakup.Burst.FIRE || piece.burst == RocketBreakup.Burst.SOLID) {
                    level.addParticle(ModParticles.ENGINE_FLAME.get(), piece.pos.x, piece.pos.y, piece.pos.z, 0.0, 0.03, 0.0);
                }
            }
            if (piece.pos.y < level.getMinY() - 8) {
                iterator.remove();
            }
        }
    }

    // ------------------------------------------------------------------
    // Affichage
    // ------------------------------------------------------------------

    @SubscribeEvent
    static void onSubmit(SubmitCustomGeometryEvent event) {
        if (PIECES.isEmpty() && BLOOMS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        SubmitNodeCollector collector = event.getSubmitNodeCollector();

        for (Piece piece : List.copyOf(PIECES)) {
            Vec3 at = piece.last.lerp(piece.pos, partial);
            // Un tronçon en feu est éclairé par sa propre flamme ; les autres prennent la lumière du lieu.
            int light = piece.flying && !piece.resting && piece.burst != RocketBreakup.Burst.SHELL ? LightCoordsUtil.FULL_BRIGHT
                    : LightCoordsUtil.getLightCoords(minecraft.level, BlockPos.containing(at).above());
            float fade = piece.resting && piece.restTicks > 70 ? Math.max(0.05F, 1.0F - (piece.restTicks - 70 + partial) / 20.0F) : 1.0F;
            poseStack.pushPose();
            poseStack.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partial, piece.lastPitch, piece.pitch)));
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partial, piece.lastRoll, piece.roll)));
            poseStack.mulPose(piece.attitude);
            poseStack.scale(fade, fade, fade);
            poseStack.translate(-0.5F, -0.5F, -0.5F);
            ModelCache.get(piece.state).submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }

        for (Bloom bloom : List.copyOf(BLOOMS)) {
            float t = (clock - bloom.born() + partial) / bloom.life();
            if (t < 0.0F || t > 1.0F) {
                continue;
            }
            // Ça gonfle vite puis ralentit, et s'efface en fin de course.
            float grown = 1.0F - (1.0F - t) * (1.0F - t);
            boolean ring = bloom.shape() != Shape.GLOW;
            float radius = bloom.size() * (ring ? grown : 0.35F + 0.65F * grown);
            int alpha = (int) ((bloom.color() >>> 24) * Math.pow(1.0F - t, ring ? 1.3 : 1.6));
            int color = (alpha << 24) | (bloom.color() & 0xFFFFFF);
            // Une lueur est avancée vers l'observateur : les tronçons de la fusée ne la cachent pas.
            Vec3 at = bloom.at();
            if (bloom.shape() != Shape.GROUND_RING) {
                Vec3 toCamera = camera.subtract(at);
                double distance = toCamera.length();
                at = distance < 0.1 ? at : at.add(toCamera.scale(Math.min(distance * 0.5, 3.0) / distance));
            }
            poseStack.pushPose();
            poseStack.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
            if (bloom.shape() == Shape.GROUND_RING) {
                // À plat sur le sol.
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            } else {
                poseStack.mulPose(event.getLevelRenderState().cameraRenderState.orientation);
            }
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(ring ? SHOCKWAVE : GLOW), (pose, buffer) -> {
                float[][] corners = {{-1, -1, 0, 1}, {-1, 1, 0, 0}, {1, 1, 1, 0}, {1, -1, 1, 1}};
                for (int pass = 0; pass < 2; pass++) {
                    for (int k = 0; k < 4; k++) {
                        float[] corner = corners[pass == 0 ? k : 3 - k];
                        buffer.addVertex(pose, corner[0] * radius, corner[1] * radius, 0.0F).setColor(color).setUv(corner[2], corner[3])
                                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0.0F, 0.0F, 1.0F);
                    }
                }
            });
            poseStack.popPose();
        }
    }

    /** La secousse : la caméra tremble sur ses trois axes, à des rythmes différents, puis se calme. */
    @SubscribeEvent
    static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (shake < 0.02F) {
            return;
        }
        float time = clock + (float) event.getPartialTick();
        event.setYaw(event.getYaw() + Mth.sin(time * 1.9F) * shake);
        event.setPitch(event.getPitch() + Mth.cos(time * 2.3F) * shake * 0.8F);
        event.setRoll(event.getRoll() + Mth.sin(time * 2.7F) * shake * 0.6F);
    }

    /** L'éblouissement : l'écran blanchit un instant. */
    @SubscribeEvent
    static void onGui(RenderGuiEvent.Post event) {
        if (flash < 0.02F) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int alpha = (int) (255 * Math.min(0.9F, flash));
        event.getGuiGraphics().fill(0, 0, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight(),
                (alpha << 24) | 0xFFF4DC);
    }
}
