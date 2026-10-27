package com.orbitalis.phase1.spaceport;

import java.util.ArrayList;
import java.util.List;

import com.orbitalis.core.config.ServerConfig;
import com.orbitalis.core.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Dislocation d'une fusée : ce qui se passe quand un lancement tourne mal. La fusée ne disparaît pas dans une
 * explosion quelconque : elle se défait tronçon après tronçon, chacun selon ce qu'il contient, et ce qu'il en
 * reste retombe autour.
 * <ul>
 * <li><b>Une seule explosion.</b> La fusée saute d'un coup, là où elle a cédé. Un réservoir de kérosène donne une
 * boule de feu et de la fumée noire, des ergols cryogéniques un nuage blanc glacé et un éclair, un moteur à poudre
 * une gerbe d'escarbilles. L'épave qui retombe ensuite s'écrase, sans exploser de nouveau.</li>
 * <li><b>Onde de choc.</b> Un anneau de poussière part au ras du sol ; les créatures proches sont blessées et
 * repoussées, d'autant plus qu'elles sont près.</li>
 * <li><b>Dégâts.</b> Les blocs du pas de tir les plus proches sont détruits (le plan les redemandera) ; plus loin,
 * le sol est calciné et quelques feux prennent. Leur étendue dépend de la masse de la fusée et des ergols qu'elle
 * portait encore : une fusée-sonde abîme quelques dalles, un lanceur lourd rase le tablier et le pied de la tour.</li>
 * <li><b>Débris.</b> Des morceaux de coque noircis sont projetés, retombent et restent au sol : on les ramasse.</li>
 * </ul>
 * L'accident lui-même n'est jamais dû au hasard ; seule la ruine qu'il laisse varie un peu d'une fois à l'autre.
 * La configuration du serveur ({@link ServerConfig#ACCIDENT_DAMAGE}) limite les dégâts au pas de tir, ou les supprime.
 */
public final class RocketBreakup {
    /** Ce qu'un tronçon contient, et donc la façon dont il éclate. */
    public enum Burst {
        /** Kérosène ou moteur : boule de feu, fumée noire. */
        FIRE,
        /** Ergols cryogéniques : nuage blanc, éclair. */
        CRYO,
        /** Poudre : gerbe d'escarbilles, fumée épaisse. */
        SOLID,
        /** Structure sans ergols : fumée et tôles. */
        SHELL
    }

    /** Étendue des dégâts d'un accident. */
    public enum Damage {
        /** Le pas de tir et le terrain alentour : blocs détruits, cratère, sol calciné, feux. */
        FULL,
        /** Les blocs du pas de tir seulement. */
        PAD_ONLY,
        /** Aucun bloc n'est touché : l'accident ne fait que du bruit et de la fumée. */
        NONE
    }

    /** Nombre maximal de débris projetés par un accident. */
    private static final int MAX_DEBRIS = 28;
    /** Angle d'or : il répartit les débris tout autour, sans tirage au sort. */
    private static final double GOLDEN_ANGLE = 2.399963;

    /**
     * Étendue des dégâts imposée, à la place de celle de la configuration : pour les essais automatiques, qui ne
     * doivent rien abîmer autour d'eux. {@code null} en temps normal.
     */
    public static volatile Damage forced;

    private static Damage mode() {
        Damage imposed = forced;
        return imposed != null ? imposed : ServerConfig.ACCIDENT_DAMAGE.get();
    }

    /** Un éclatement à venir : dans combien de ticks, où, et de quelle sorte. */
    private record Pending(int delay, Vec3 at, Burst burst, float scale, Effect effect) {
    }

    /** Ce qu'un éclatement fait au monde, en plus de ses débris. */
    private enum Effect {
        /** Rien de plus. */
        NONE,
        /** La fusée cède : l'onde de choc part de là, et ce qui l'entoure sur toute sa hauteur est touché. */
        ROCKET,
        /** L'épave touche le sol : onde de choc et cratère au point d'impact. */
        IMPACT
    }

    private final LaunchTableBlockEntity table;
    private final List<Pending> pending = new ArrayList<>();
    private final double radius;
    /** Pied de la fusée au moment où elle cède, sa hauteur en blocs, et vrai si elle fait trois blocs de large. */
    private final Vec3 foot;
    private final int height;
    private final boolean wide;
    /** Vrai si la fusée cède sur sa table ; faux si elle cède en vol. */
    private final boolean onPad;
    private int age;
    private int debris;

    /** Orientation de la fusée quand elle cède, hauteur du point autour duquel elle tournait, et sa vitesse de rotation. */
    private org.joml.Quaternionf attitude = new org.joml.Quaternionf();
    private float pivot;
    private org.joml.Vector3f spin = new org.joml.Vector3f();

    /** Centre d'un tronçon de la fusée, là où il se trouve vraiment : sur l'axe de la fusée, penchée ou non. */
    private Vec3 pieceAt(int index) {
        org.joml.Vector3f along = attitude.transform(new org.joml.Vector3f(0.0F, index + 0.5F - pivot, 0.0F));
        return foot.add(along.x, pivot + along.y, along.z);
    }

    private RocketBreakup(LaunchTableBlockEntity table, Vec3 foot, RocketShape shape, double radius, boolean onPad) {
        this.table = table;
        this.foot = foot;
        this.height = shape.height();
        this.wide = shape.isWide();
        this.radius = radius;
        this.onPad = onPad;
    }

    /**
     * Rayon de l'onde de choc, en blocs, pour une fusée d'une masse donnée dont il reste une part des ergols.
     *
     * @param massKg       masse au décollage, pleins faits
     * @param fuelFraction part des ergols encore à bord, de 0 à 1
     * @param solid        vrai pour un moteur à poudre, qui brûle plus qu'il n'explose
     */
    public static double blastRadius(double massKg, double fuelFraction, boolean solid) {
        double tons = massKg / 1000.0;
        double energy = tons * 0.85 * Math.clamp(fuelFraction, 0.0, 1.0) * (solid ? 0.6 : 1.0) + tons * 0.05;
        return Math.clamp(2.0 + Math.cbrt(energy) * 0.75, 2.5, 10.0);
    }

    /** Sorte d'éclatement d'un tronçon de fusée. */
    public static Burst burstOf(RocketBlock.Part part, boolean cryogenic) {
        String name = part.name();
        if (name.equals("TANK_SOLID") || name.equals("ENGINE_SOLID")) {
            return Burst.SOLID;
        }
        if (name.startsWith("TANK")) {
            return cryogenic ? Burst.CRYO : Burst.FIRE;
        }
        return name.startsWith("ENGINE") ? Burst.FIRE : Burst.SHELL;
    }

    /**
     * Accident sur le pas de tir : la fusée se disloque là où elle se dresse, à partir du tronçon qui a cédé.
     *
     * @param shape     silhouette de la fusée
     * @param origin    rang du tronçon qui cède le premier
     * @param radius    rayon de l'onde de choc (voir {@link #blastRadius})
     * @param cryogenic vrai si la fusée porte de l'hydrogène liquide
     */
    public static RocketBreakup onPad(LaunchTableBlockEntity table, RocketShape shape, int origin, double radius, boolean cryogenic) {
        BlockPos pos = table.getBlockPos();
        Vec3 base = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        int first = Math.clamp(origin, 0, Math.max(0, shape.height() - 1));
        RocketBreakup breakup = new RocketBreakup(table, base, shape, radius, true);
        breakup.chain(base, shape, first, cryogenic);
        if (table.getLevel() instanceof ServerLevel level) {
            breakup.announceRocket(level, base, shape, first, cryogenic);
        }
        return breakup;
    }

    /** Nature de l'éclatement d'un groupe de tronçons : celle de son premier réservoir ou moteur chargé. */
    public static Burst groupBurst(List<RocketBlock.Part> parts, int from, int to, boolean cryogenic) {
        for (int i = from; i < to; i++) {
            Burst burst = burstOf(parts.get(i), cryogenic);
            if (burst != Burst.SHELL) {
                return burst;
            }
        }
        return Burst.SHELL;
    }

    /** La fusée éclate : une seule explosion, d'un seul coup, là où elle a cédé. */
    private void chain(Vec3 base, RocketShape shape, int first, boolean cryogenic) {
        float scale = (float) (radius / 5.0) * (shape.isWide() ? 1.4F : 1.0F) * 1.3F;
        List<RocketBlock.Part> parts = shape.parts();
        pending.add(new Pending(0, pieceAt(first), groupBurst(parts, 0, parts.size(), cryogenic), scale, Effect.ROCKET));
    }

    /**
     * Accident en vol : la fusée éclate là où elle se trouve vraiment, à la hauteur qu'elle a atteinte, puis ce
     * qu'il en reste retombe plus loin, dans le sens de son tir, et s'écrase (sans exploser de nouveau). Si elle vient à peine de quitter sa
     * table, le pas de tir et la tour sont touchés comme si elle y était encore.
     *
     * @param shape  silhouette de la fusée
     * @param base   pied de la fusée à l'instant où elle cède
     * @param impact point où l'épave touche le sol
     */
    public static RocketBreakup inFlight(LaunchTableBlockEntity table, RocketShape shape, Vec3 base, Vec3 impact, double radius,
            boolean cryogenic) {
        return inFlight(table, shape, base, impact, radius, cryogenic, new org.joml.Quaternionf(), 0.0F, new org.joml.Vector3f());
    }

    /**
     * Le même accident, pour une fusée penchée : elle éclate dans l'orientation où elle se trouvait.
     *
     * @param attitude orientation de la fusée
     * @param pivot    hauteur, au-dessus de son pied, du point autour duquel elle tournait
     * @param spin     sa vitesse de rotation (axe, et angle par tick en radians)
     */
    public static RocketBreakup inFlight(LaunchTableBlockEntity table, RocketShape shape, Vec3 base, Vec3 impact, double radius,
            boolean cryogenic, org.joml.Quaternionf attitude, float pivot, org.joml.Vector3f spin) {
        RocketBreakup breakup = new RocketBreakup(table, base, shape, radius, false);
        breakup.attitude = attitude;
        breakup.pivot = pivot;
        breakup.spin = spin;
        // En l'air, la fusée cède par le milieu.
        int first = shape.height() / 2;
        breakup.chain(base, shape, first, cryogenic);
        // L'épave retombe : d'autant plus longtemps qu'elle tombe de haut (chute libre, bornée à dix secondes).
        double fall = Math.max(4.0, base.y - impact.y);
        int delay = (int) Math.clamp(Math.sqrt(2.0 * fall / 0.08), 40.0, 200.0);
        breakup.pending.add(new Pending(delay, impact, Burst.FIRE, (float) (radius / 5.0) * 1.3F, Effect.IMPACT));
        if (table.getLevel() instanceof ServerLevel level) {
            breakup.announceRocket(level, base, shape, first, cryogenic);
        }
        return breakup;
    }

    /**
     * Fait avancer l'accident d'un tick.
     *
     * @return vrai quand tout est retombé : l'accident est terminé
     */
    public boolean tick(ServerLevel level) {
        for (int i = pending.size() - 1; i >= 0; i--) {
            Pending burst = pending.get(i);
            if (burst.delay() <= age) {
                pending.remove(i);
                explode(level, burst);
            }
        }
        age++;
        return pending.isEmpty();
    }

    // ------------------------------------------------------------------
    // Un éclatement
    // ------------------------------------------------------------------

    private void explode(ServerLevel level, Pending burst) {
        Vec3 at = burst.at();
        // Le spectacle (tronçons projetés, boule de feu, onde de choc, son) est l'affaire de chaque client ; ici ne
        // se décide que ce qui change le monde : les débris qui retombent, les blessures, les blocs détruits.
        if (level.isLoaded(BlockPos.containing(at)) && !level.isOutsideBuildHeight(BlockPos.containing(at))) {
            throwDebris(level, at, burst.burst() == Burst.SHELL ? 4 : 10);
        }
        switch (burst.effect()) {
            case ROCKET -> {
                shockwave(level, at);
                // Tout ce qui borde la fusée, sur toute sa hauteur, là où elle se trouve.
                damagePad(level, foot, foot.add(0.0, height, 0.0));
                if (onPad) {
                    damageTerrain(level, foot);
                }
            }
            case IMPACT -> {
                shockwave(level, at);
                damagePad(level, at, at);
                damageTerrain(level, at);
                // L'épave touche le sol : les clients en voient l'impact.
                announce(level, new com.orbitalis.core.network.BreakupPayload(at, List.of(), 0, 0, (float) radius, false, true,
                        new org.joml.Quaternionf(), 0.0F, new org.joml.Vector3f()));
            }
            default -> {
            }
        }
    }

    /**
     * Annonce l'accident aux joueurs qui peuvent le voir : ceux qui sont autour du pas de tir, d'aussi loin qu'on
     * voit une fusée, même si elle a cédé très haut au-dessus d'eux, et ceux qui sont près de la fusée.
     */
    private void announce(ServerLevel level, com.orbitalis.core.network.BreakupPayload payload) {
        BlockPos pad = table.getBlockPos();
        // Ceux du pas de tir, et ceux qui sont près de la fusée elle-même : en vol, elle peut être loin de sa table, et
        // son équipage avec elle.
        com.orbitalis.core.network.ModNetwork.sendNear(level, 320.0, payload, new Vec3(pad.getX() + 0.5, pad.getY(), pad.getZ() + 0.5), payload.base());
    }

    /** Les joueurs voient la fusée se disloquer : on leur dit de quoi elle était faite, et où elle a cédé. */
    private void announceRocket(ServerLevel level, Vec3 base, RocketShape shape, int origin, boolean cryogenic) {
        List<Integer> parts = new ArrayList<>();
        for (RocketBlock.Part part : shape.parts()) {
            parts.add(part.ordinal());
        }
        announce(level, new com.orbitalis.core.network.BreakupPayload(base, parts, shape.size().ordinal(), origin, (float) radius, cryogenic, onPad,
                attitude, pivot, spin));
    }

    /** Projette des morceaux de coque : chacun part dans sa direction, retombe, et reste au sol s'il y trouve la place. */
    private void throwDebris(ServerLevel level, Vec3 at, int count) {
        Damage mode = mode();
        for (int i = 0; i < count && debris < MAX_DEBRIS; i++, debris++) {
            BlockPos pos = BlockPos.containing(at);
            if (!level.getBlockState(pos).isAir() || level.isOutsideBuildHeight(pos)) {
                continue;
            }
            double angle = debris * GOLDEN_ANGLE;
            double speed = 0.4 + 0.12 * (debris % 4);
            FallingBlockEntity piece = FallingBlockEntity.fall(level, pos, ModBlocks.ROCKET_DEBRIS.get().defaultBlockState());
            piece.setDeltaMovement(Math.cos(angle) * speed, 0.35 + 0.1 * (debris % 3), Math.sin(angle) * speed);
            piece.setHurtsEntities(2.0F, 20);
            piece.hurtMarked = true;
            if (mode != Damage.FULL) {
                // Les débris ne restent au sol qu'avec les dégâts complets : sinon, ils ne laissent rien derrière eux.
                piece.disableDrop();
            }
        }
    }

    /** Onde de choc : les créatures proches sont blessées et repoussées, d'autant plus qu'elles sont près. */
    private void shockwave(ServerLevel level, Vec3 at) {
        double reach = radius * 1.5;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(BlockPos.containing(at)).inflate(reach),
                candidate -> candidate.isAlive() && !candidate.isSpectator())) {
            Vec3 away = entity.position().add(0.0, entity.getBbHeight() / 2.0, 0.0).subtract(at);
            double distance = away.length();
            if (distance >= reach) {
                continue;
            }
            double force = 1.0 - distance / reach;
            entity.hurtServer(level, level.damageSources().explosion(null, null), (float) (4.0 + 26.0 * force * force));
            Vec3 push = (distance < 0.01 ? new Vec3(0.0, 1.0, 0.0) : away.scale(1.0 / distance)).scale(1.6 * force);
            entity.push(push.x, push.y * 0.5 + 0.35 * force, push.z);
            entity.hurtMarked = true;
        }
    }

    /** Distance d'un point à un segment vertical. */
    private static double distanceToColumn(Vec3 point, Vec3 bottom, Vec3 top) {
        double y = Math.clamp(point.y, bottom.y, top.y);
        return point.distanceTo(new Vec3(bottom.x, y, bottom.z));
    }

    /**
     * Dégâts au pas de tir : tout ce qui borde la fusée, sur toute sa hauteur, est touché. Une fusée haute déchire
     * donc la tour jusqu'en haut, une fusée large souffle plus loin. Au plus près, tout est détruit à coup sûr ; plus
     * loin, de moins en moins de blocs cèdent, au hasard : deux accidents ne laissent pas la même ruine. Un mât
     * coupé tombe tout entier, depuis la coupure. La table de lancement (elle garde le plan, qui montrera quoi
     * rebâtir) et le déflecteur de flammes (il est fait pour cela) tiennent toujours.
     *
     * @param bottom pied de la fusée, ou point d'impact
     * @param top    sommet de la fusée, ou point d'impact
     */
    private void damagePad(ServerLevel level, Vec3 bottom, Vec3 top) {
        PadPlan plan = table.getPlan();
        if (mode() == Damage.NONE || plan == null) {
            return;
        }
        BlockPos origin = table.getBlockPos();
        // Zone où tout cède, et zone où les blocs cèdent de moins en moins.
        double sure = radius * 0.5 * (wide ? 1.35 : 1.0);
        double reach = sure * 2.1;
        net.minecraft.util.RandomSource random = level.getRandom();
        java.util.Map<Long, Integer> cutMasts = new java.util.HashMap<>();
        for (PadPlan.Piece piece : plan.pieces()) {
            BlockPos pos = origin.offset(piece.offset());
            if (piece.role() == PadPlan.Role.DEFLECTOR || !level.isLoaded(pos)) {
                continue;
            }
            double distance = distanceToColumn(Vec3.atCenterOf(pos), bottom, top);
            if (distance > reach) {
                continue;
            }
            // De 100 % au bord de la zone sûre à 0 % au bout de la portée, plus vite que la distance.
            double chance = distance <= sure ? 1.0 : Math.pow(1.0 - (distance - sure) / (reach - sure), 1.6);
            if (random.nextDouble() >= chance) {
                continue;
            }
            if (piece.accepts(level.getBlockState(pos))) {
                level.destroyBlock(pos, false);
            }
            if (piece.role() == PadPlan.Role.MAST) {
                cutMasts.merge(BlockPos.asLong(pos.getX(), 0, pos.getZ()), pos.getY(), Math::min);
            }
        }
        // Un mât coupé ne reste pas en l'air : tout ce qui est au-dessus de la coupure tombe avec.
        for (PadPlan.Piece piece : plan.pieces()) {
            BlockPos pos = origin.offset(piece.offset());
            Integer cut = piece.role() == PadPlan.Role.MAST ? cutMasts.get(BlockPos.asLong(pos.getX(), 0, pos.getZ())) : null;
            if (cut != null && pos.getY() > cut && piece.accepts(level.getBlockState(pos))) {
                level.destroyBlock(pos, false);
            }
        }
    }

    /**
     * Dégâts au terrain, avec les dégâts complets seulement : un cratère au plus près, puis le sol calciné et
     * quelques feux. Les blocs du pas de tir n'en font pas partie : ils ont leurs propres dégâts.
     */
    private void damageTerrain(ServerLevel level, Vec3 at) {
        if (mode() != Damage.FULL) {
            return;
        }
        BlockPos origin = table.getBlockPos();
        java.util.Set<BlockPos> padBlocks = new java.util.HashSet<>();
        PadPlan plan = table.getPlan();
        if (plan != null) {
            for (PadPlan.Piece piece : plan.pieces()) {
                padBlocks.add(origin.offset(piece.offset()));
            }
        }
        double blast = radius * (wide ? 1.25 : 1.0);
        int reach = (int) Math.ceil(blast);
        BlockPos centrePos = BlockPos.containing(at);
        int fires = 0;
        for (BlockPos pos : BlockPos.betweenClosed(centrePos.offset(-reach, -reach, -reach), centrePos.offset(reach, reach, reach))) {
            double distance = Vec3.atCenterOf(pos).distanceTo(at);
            if (distance > blast || pos.equals(origin) || padBlocks.contains(pos) || !level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0.0F) {
                continue;
            }
            if (distance <= blast * 0.45 && state.getDestroySpeed(level, pos) <= 3.0F) {
                // Le cratère : au plus près, le terrain tendre est soufflé.
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            } else if (state.is(BlockTags.LEAVES) || state.canBeReplaced()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            } else if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.DIRT) || state.is(Blocks.FARMLAND)) {
                level.setBlock(pos, Blocks.COARSE_DIRT.defaultBlockState(), 3);
                BlockPos above = pos.above();
                if (fires < 6 && Math.floorMod(pos.getX() * 3 + pos.getZ() * 5, 7) == 0 && level.getBlockState(above).isAir()) {
                    level.setBlock(above, Blocks.FIRE.defaultBlockState(), 3);
                    fires++;
                }
            }
        }
    }

    /** Point où retombe l'épave d'une fusée dont les moteurs se sont éteints : plus loin dans le sens de son tir, au sol. */
    public static Vec3 impactPoint(ServerLevel level, LaunchTableBlockEntity table) {
        return impactPoint(level, table, 0.0, 0.0);
    }

    /** Le même point, pour une fusée qui s'est écartée de la verticale de sa table : l'épave tombe plus loin, sous elle. */
    public static Vec3 impactPoint(ServerLevel level, LaunchTableBlockEntity table, double movedX, double movedZ) {
        BlockPos pos = table.getBlockPos().offset((int) Math.round(movedX), 0, (int) Math.round(movedZ));
        int distance = 20 + 4 * Math.max(1, table.getComplex().radius());
        BlockPos spot = pos.relative(table.getTrenchDirection(), distance);
        int ground = level.isLoaded(spot) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spot.getX(), spot.getZ()) : pos.getY();
        return new Vec3(spot.getX() + 0.5, ground + 0.5, spot.getZ() + 0.5);
    }
}
