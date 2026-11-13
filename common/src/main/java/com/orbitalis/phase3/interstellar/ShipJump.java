package com.orbitalis.phase3.interstellar;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.orbitalis.core.Features;
import com.orbitalis.core.network.ModNetwork;
import com.orbitalis.core.network.ShipHelmPayload;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.research.ResearchManager;
import com.orbitalis.phase1.satellite.CelestialBody;
import com.orbitalis.phase1.satellite.SatelliteManager;
import com.orbitalis.phase2.survival.SpaceRules;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.orbitalis.platform.event.ServerTickEvent;

/**
 * Le saut du vaisseau interstellaire. Depuis le poste de pilotage, quand tous ses systèmes sont prêts, le vaisseau
 * quitte son berceau du chantier naval et réapparaît en orbite du monde choisi (voir {@link ShipDestination}), s'il a
 * été découvert à l'observatoire, avec tout ce qu'il contient et son équipage ; le saut retour le ramène à son berceau.
 * <p>
 * Un saut vers une étoile proche coûte {@link #JUMP_COST} matières exotiques au cœur de distorsion : un cœur plein fait
 * l'aller et le retour. Un saut galactique en coûte davantage (voir {@link ShipDestination#cost()}) : il faut recharger le
 * cœur sur place, avec la matière exotique emportée, pour revenir.
 * Après l'ordre de saut, un compte à rebours de {@link #COUNTDOWN} ticks laisse à l'équipage le temps d'embarquer. Au bout,
 * le vaisseau passe en distorsion : l'équipage voit les étoiles s'étirer pendant {@link #WARP_LEAD} ticks, puis, dans
 * l'éclair, le vaisseau est déplacé ; le tunnel et la sortie se jouent chez lui (voir {@code WarpFx}).
 */
public final class ShipJump {
    /** Matières exotiques consommées par un saut vers une étoile proche, le plus petit des sauts. */
    public static final int JUMP_COST = 4;
    /** Durée du compte à rebours, en ticks. */
    public static final int COUNTDOWN = 100;
    /** Le temps de l'accélération, entre la fin du compte à rebours et le déplacement du vaisseau, en ticks (WarpFx.STRETCH). */
    public static final int WARP_LEAD = 60;

    /** Ce qui empêche de sauter, dans l'ordre où on le vérifie. */
    public enum Hold {
        NONE, PLACE, SYSTEMS, CHARGE, UNDISCOVERED, BLOCKED, JUMPING;

        public String key() {
            return "ship.orbitalis.helm.block." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Où se trouve le vaisseau : à son berceau (dans l'orbite de la planète), en orbite de sa destination, ou ailleurs. */
    public enum Place {
        HOME, AWAY, ELSEWHERE
    }

    /** Un saut ordonné, en compte à rebours. */
    private static final class Pending {
        final ResourceKey<Level> dimension;
        final BlockPos origin;
        final Direction out;
        final String agency;
        /** Le monde où va le vaisseau, ou rien s'il rentre à son berceau. */
        final ShipDestination outbound;
        int ticks = COUNTDOWN;
        /** Après le compte à rebours : les ticks qui restent avant le déplacement, ou -1 tant que la distorsion n'a pas commencé. */
        int lead = -1;

        Pending(ResourceKey<Level> dimension, BlockPos origin, Direction out, String agency, ShipDestination outbound) {
            this.dimension = dimension;
            this.origin = origin;
            this.out = out;
            this.agency = agency;
            this.outbound = outbound;
        }
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    private ShipJump() {
    }

    // ------------------------------------------------------------------
    // Où, et peut-on sauter
    // ------------------------------------------------------------------

    /** Où se trouve le vaisseau dont la poupe est en {@code origin}. */
    public static Place place(ServerLevel level, BlockPos origin, Direction out, String agency) {
        if (level.dimension() == SpaceRules.ORBIT) {
            return Place.HOME;
        }
        ShipVoyages.Voyage voyage = ShipVoyages.of(level.getServer()).of(agency);
        if (voyage != null && voyage.away() && level.dimension() == voyage.destination().orbit() && voyage.out() == out
                && voyage.awayOrigin().equals(origin)) {
            return Place.AWAY;
        }
        return Place.ELSEWHERE;
    }

    /**
     * Ce qui empêche ce vaisseau de sauter, ou {@link Hold#NONE}. À son berceau, il part pour {@code wanted} ; ailleurs,
     * il rentre.
     */
    public static Hold blocker(ServerLevel level, BlockPos origin, Direction out, String agency, ShipSystems systems, ShipDestination wanted) {
        if (pending(level.dimension(), origin) != null) {
            return Hold.JUMPING;
        }
        Place place = place(level, origin, out, agency);
        if (place == Place.ELSEWHERE) {
            return Hold.PLACE;
        }
        if (!systems.complete()) {
            return Hold.SYSTEMS;
        }
        if (systems.charge() < cost(level.getServer(), place, wanted, agency)) {
            return Hold.CHARGE;
        }
        if (place == Place.HOME && !SatelliteManager.of(level.getServer(), agency).isDiscovered(wanted.body())) {
            return Hold.UNDISCOVERED;
        }
        ServerLevel to = destination(level.getServer(), place, wanted);
        BlockPos target = destinationOrigin(level.getServer(), place, agency);
        if (to == null || target == null || !clear(to, target, out)) {
            return Hold.BLOCKED;
        }
        return Hold.NONE;
    }

    /** Ce que coûte le saut : vers la destination voulue, depuis le berceau ; ou le retour depuis celle du voyage en cours. */
    public static int cost(MinecraftServer server, Place place, ShipDestination wanted, String agency) {
        ShipVoyages.Voyage voyage = ShipVoyages.of(server).of(agency);
        return place == Place.AWAY && voyage != null ? voyage.destination().cost() : wanted.cost();
    }

    private static ServerLevel destination(MinecraftServer server, Place place, ShipDestination wanted) {
        return server.getLevel(place == Place.HOME ? wanted.orbit() : SpaceRules.ORBIT);
    }

    /** Centre de la poupe à l'arrivée : la place de l'agence en orbite de sa destination, ou le berceau d'où le vaisseau était parti. */
    private static BlockPos destinationOrigin(MinecraftServer server, Place place, String agency) {
        ShipVoyages voyages = ShipVoyages.of(server);
        ShipVoyages.Voyage voyage = voyages.of(agency);
        if (place == Place.AWAY) {
            return voyage == null ? null : voyage.home();
        }
        // Une agence garde sa place en orbite d'un voyage à l'autre ; la première fois, la suivante libre.
        return voyage != null ? voyage.awayOrigin() : voyages.peekAwayOrigin();
    }

    /** Le vaisseau tient-il à cette place : toute sa boîte (coque et anneau) y est vide ? */
    public static boolean clear(ServerLevel level, BlockPos origin, Direction out) {
        for (BlockPos cell : cells(origin, out)) {
            if (!level.getBlockState(cell).isAir()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Toutes les cases de la boîte du vaisseau : sa longueur, collier d'amarrage compris, et en travers comme en hauteur le
     * rayon de l'anneau.
     */
    static List<BlockPos> cells(BlockPos origin, Direction out) {
        List<BlockPos> cells = new ArrayList<>();
        int r = ShipPlan.RING_RADIUS;
        for (int a = ShipPlan.COLLAR_A; a < ShipPlan.LENGTH; a++) {
            for (int s = -r; s <= r; s++) {
                for (int y = -r; y <= r; y++) {
                    cells.add(origin.offset(ShipPlan.local(out, a, s, y)));
                }
            }
        }
        return cells;
    }

    /** La boîte du vaisseau, bord extérieur des cases compris. */
    public static AABB box(BlockPos origin, Direction out) {
        int r = ShipPlan.RING_RADIUS;
        BlockPos a = origin.offset(ShipPlan.local(out, ShipPlan.COLLAR_A, -r, -r));
        BlockPos b = origin.offset(ShipPlan.local(out, ShipPlan.LENGTH - 1, r, r));
        return new AABB(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1);
    }

    // ------------------------------------------------------------------
    // Poste de pilotage
    // ------------------------------------------------------------------

    /**
     * L'état du vaisseau pour l'écran du poste de pilotage : ce qui l'empêche de partir vers chaque destination, et celle
     * qu'il faut montrer (celle du voyage en cours, sinon la première).
     */
    public static ShipHelmPayload status(ServerPlayer player, BlockPos helm, BlockPos origin, Direction out) {
        ServerLevel level = player.level();
        String agency = ResearchManager.agencyKey(player);
        ShipSystems systems = ShipSystems.check(level, origin, out);
        Place place = place(level, origin, out, agency);
        Pending pending = pending(level.dimension(), origin);
        ShipVoyages.Voyage voyage = ShipVoyages.of(level.getServer()).of(agency);
        ShipDestination shown = pending != null && pending.outbound != null ? pending.outbound
                : voyage != null && place == Place.AWAY ? voyage.destination() : ShipDestination.PROXIMA;
        java.util.List<Integer> holds = new java.util.ArrayList<>();
        for (ShipDestination destination : ShipDestination.values()) {
            holds.add(blocker(level, origin, out, agency, systems, destination).ordinal());
        }
        boolean spinning = level.getBlockEntity(origin.offset(ShipPlan.local(out, ShipPlan.RING_A, 0, ShipPlan.RING_DRIVE_Y)))
                instanceof RingDriveBlockEntity drive && drive.spinning();
        return ShipHelmPayload.of(helm, systems, place.ordinal(), blocker(level, origin, out, agency, systems, shown).ordinal(),
                pending == null ? -1 : pending.ticks, spinning, shown.ordinal(), holds);
    }

    /**
     * Le joueur ordonne le saut depuis le poste de pilotage, vers la destination de ce rang s'il est à son berceau : le
     * compte à rebours commence, si rien ne l'empêche.
     */
    public static void request(ServerPlayer player, BlockPos helm, int destinationIndex) {
        ServerLevel level = player.level();
        BlockState state = level.getBlockState(helm);
        if (!Features.interstellar() || !state.is(ModBlocks.SHIP_HELM.get()) || player.distanceToSqr(Vec3.atCenterOf(helm)) > 64) {
            return;
        }
        Direction out = state.getValue(ShipHelmBlock.FACING).getOpposite();
        BlockPos origin = ShipHelmBlock.shipOrigin(helm, state.getValue(ShipHelmBlock.FACING));
        String agency = ResearchManager.agencyKey(player);
        ShipSystems systems = ShipSystems.check(level, origin, out);
        ShipDestination wanted = ShipDestination.byIndex(destinationIndex);
        Hold blocker = blocker(level, origin, out, agency, systems, wanted);
        if (blocker != Hold.NONE) {
            player.sendSystemMessage(holdMessage(blocker, systems, wanted, cost(level.getServer(), place(level, origin, out, agency), wanted, agency))
                    .withStyle(ChatFormatting.GOLD), true);
            return;
        }
        Place place = place(level, origin, out, agency);
        ShipDestination outbound = place == Place.HOME ? wanted : null;
        PENDING.add(new Pending(level.dimension(), origin.immutable(), out, agency, outbound));
        level.playSound(null, helm, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 0.6F);
        for (ServerPlayer crew : crew(level, origin, out)) {
            title(crew, Component.translatable("ship.orbitalis.jump.countdown", COUNTDOWN / 20),
                    Component.translatable(outbound != null ? outbound.toKey() : "ship.orbitalis.jump.to_home"));
        }
        ModNetwork.send(player, status(player, helm, origin, out));
    }

    /** Ce qui empêche le saut, en une phrase : la charge du cœur, ce qu'il faut, et le monde visé. */
    public static net.minecraft.network.chat.MutableComponent holdMessage(Hold hold, ShipSystems systems, ShipDestination wanted, int cost) {
        return Component.translatable(hold.key(), systems.charge(), cost, Component.translatable(wanted.nameKey()));
    }

    // ------------------------------------------------------------------
    // Navette
    // ------------------------------------------------------------------

    /**
     * Le joueur prend la navette depuis le poste de pilotage, le vaisseau en orbite d'un autre monde : une capsule se
     * détache du flanc du vaisseau, le joueur aux commandes. D'autres équipiers peuvent y monter avant le freinage.
     */
    public static void launchShuttle(ServerPlayer player, BlockPos helm) {
        ServerLevel level = player.level();
        BlockState state = level.getBlockState(helm);
        if (!Features.interstellar() || !state.is(ModBlocks.SHIP_HELM.get()) || player.distanceToSqr(Vec3.atCenterOf(helm)) > 64
                || player.isPassenger()) {
            return;
        }
        Direction out = state.getValue(ShipHelmBlock.FACING).getOpposite();
        BlockPos origin = ShipHelmBlock.shipOrigin(helm, state.getValue(ShipHelmBlock.FACING));
        if (place(level, origin, out, ResearchManager.agencyKey(player)) != Place.AWAY || pending(level.dimension(), origin) != null) {
            return;
        }
        // Devant le sas d'amarrage du hangar, sur le flanc gauche du vaisseau, à l'écart de la coque.
        BlockPos dock = origin.offset(ShipPlan.local(out, ShipPlan.HANGAR_A, -7, 0));
        com.orbitalis.phase2.flight.CapsuleEntity shuttle = new com.orbitalis.phase2.flight.CapsuleEntity(
                com.orbitalis.core.registry.ModEntities.CAPSULE.get(), level);
        shuttle.setPos(dock.getX() + 0.5, dock.getY(), dock.getZ() + 0.5);
        shuttle.startShuttle();
        level.addFreshEntity(shuttle);
        player.startRiding(shuttle);
        level.playSound(null, dock, com.orbitalis.core.registry.ModSounds.UNDOCKING.get(), SoundSource.BLOCKS, 2.0F, 1.0F);
        player.sendSystemMessage(Component.translatable("capsule.orbitalis.shuttle_away").withStyle(ChatFormatting.AQUA));
    }

    /**
     * Où se tient l'équipage qui revient à bord : sur la passerelle, derrière le poste de pilotage. Rien si le vaisseau
     * n'est plus là.
     */
    public static Vec3 bridge(ServerLevel level, BlockPos origin, Direction out) {
        BlockPos helm = origin.offset(ShipPlan.local(out, ShipPlan.HELM_A, 0, ShipPlan.HELM_Y));
        if (!level.getBlockState(helm).is(ModBlocks.SHIP_HELM.get())) {
            return null;
        }
        return Vec3.atBottomCenterOf(origin.offset(ShipPlan.local(out, ShipPlan.HELM_A - 2, 0, ShipPlan.HELM_Y)));
    }

    /** Le saut qui se vérifie lui-même, au bout de son compte à rebours : il ne compte pas comme un autre saut en cours. */
    private static Pending checking;

    private static Pending pending(ResourceKey<Level> dimension, BlockPos origin) {
        for (Pending pending : PENDING) {
            if (pending != checking && pending.dimension == dimension && pending.origin.equals(origin)) {
                return pending;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Compte à rebours et saut
    // ------------------------------------------------------------------

    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext();) {
            Pending pending = it.next();
            ServerLevel level = server.getLevel(pending.dimension);
            if (level == null) {
                it.remove();
                continue;
            }
            if (pending.lead > 0) {
                // La distorsion a commencé : les étoiles s'étirent chez l'équipage ; puis le vaisseau part.
                if (--pending.lead == 0) {
                    it.remove();
                    jump(level, pending);
                }
                continue;
            }
            pending.ticks--;
            if (pending.ticks > 0) {
                if (pending.ticks % 20 == 0) {
                    for (ServerPlayer crew : crew(level, pending.origin, pending.out)) {
                        title(crew, Component.translatable("ship.orbitalis.jump.countdown", pending.ticks / 20), null);
                    }
                    level.playSound(null, pending.origin,SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 0.8F,
                            0.8F + 0.1F * (5 - pending.ticks / 20));
                }
                continue;
            }
            if (!startWarp(level, pending)) {
                it.remove();
            }
        }
    }

    /**
     * La fin du compte à rebours : si rien n'empêche plus le saut, l'équipage voit la distorsion commencer, et le vaisseau
     * partira dans {@link #WARP_LEAD} ticks. Sinon, le saut est annulé, et on dit pourquoi.
     */
    private static boolean startWarp(ServerLevel level, Pending pending) {
        MinecraftServer server = level.getServer();
        ShipSystems systems = ShipSystems.check(level, pending.origin, pending.out);
        ShipDestination wanted = pending.outbound != null ? pending.outbound : ShipDestination.PROXIMA;
        Hold blocker;
        checking = pending;
        try {
            blocker = blocker(level, pending.origin, pending.out, pending.agency, systems, wanted);
        } finally {
            checking = null;
        }
        if (blocker != Hold.NONE) {
            for (ServerPlayer player : crew(level, pending.origin, pending.out)) {
                title(player, Component.translatable("ship.orbitalis.jump.cancelled"),
                        holdMessage(blocker, systems, wanted, cost(server, place(level, pending.origin, pending.out, pending.agency), wanted, pending.agency)));
            }
            return false;
        }
        // Vers où : la destination à l'aller ; au retour, le monde d'où l'on revient dit si le saut était galactique.
        ShipVoyages.Voyage voyage = ShipVoyages.of(server).of(pending.agency);
        ShipDestination far = pending.outbound != null ? pending.outbound : voyage != null ? voyage.destination() : ShipDestination.PROXIMA;
        boolean galactic = far.cost() > JUMP_COST;
        String titleKey = pending.outbound != null ? pending.outbound.arrivedKey() : "ship.orbitalis.jump.arrived_home";
        for (ServerPlayer player : crew(level, pending.origin, pending.out)) {
            com.orbitalis.core.network.ModNetwork.send(player, new com.orbitalis.core.network.WarpPayload(true,
                    pending.out.get3DDataValue(), galactic, titleKey, "ship.orbitalis.jump.arrived_sub"));
        }
        pending.lead = WARP_LEAD;
        return true;
    }

    /** Le saut lui-même, au bout du compte à rebours : tout est vérifié une dernière fois. */
    private static void jump(ServerLevel level, Pending pending) {
        MinecraftServer server = level.getServer();
        ShipSystems systems = ShipSystems.check(level, pending.origin, pending.out);
        ShipDestination wanted = pending.outbound != null ? pending.outbound : ShipDestination.PROXIMA;
        Hold blocker = blocker(level, pending.origin, pending.out, pending.agency, systems, wanted);
        List<ServerPlayer> crew = crew(level, pending.origin, pending.out);
        if (blocker != Hold.NONE) {
            for (ServerPlayer player : crew) {
                com.orbitalis.core.network.ModNetwork.send(player, new com.orbitalis.core.network.WarpPayload(false, 0, false, "", ""));
                title(player, Component.translatable("ship.orbitalis.jump.cancelled"),
                        holdMessage(blocker, systems, wanted, cost(server, place(level, pending.origin, pending.out, pending.agency), wanted, pending.agency)));
            }
            return;
        }
        Place place = pending.outbound != null ? Place.HOME : Place.AWAY;
        int cost = cost(server, place, wanted, pending.agency);
        ServerLevel to = destination(server, place, wanted);
        BlockPos target = destinationOrigin(server, place, pending.agency);
        transfer(level, pending.origin, to, target, pending.out);
        spendCharge(to, target, pending.out, cost);
        ShipVoyages voyages = ShipVoyages.of(server);
        if (pending.outbound != null) {
            voyages.depart(pending.agency, pending.origin, pending.out, pending.outbound);
        } else {
            voyages.back(pending.agency);
        }
        to.playSound(null, target, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.0F, 1.2F);
        // Le nom du monde d'arrivée, l'équipage le lit dans le bandeau, à la sortie du tunnel (voir WarpFx).
    }

    /**
     * Déplace le vaisseau, bloc par bloc avec leur contenu, puis ce qui se trouve à bord : de {@code origin} dans
     * {@code from} à {@code target} dans {@code to}, sans le tourner. Les blocs sont posés à l'arrivée avant d'être retirés
     * au départ, pour que l'équipage ne tombe jamais dans le vide ; rien ne tombe au sol en route.
     */
    public static void transfer(ServerLevel from, BlockPos origin, ServerLevel to, BlockPos target, Direction out) {
        record Move(BlockPos from, BlockPos to, BlockState state, CompoundTag data) {
        }
        List<Move> moves = new ArrayList<>();
        for (BlockPos cell : cells(origin, out)) {
            BlockState state = from.getBlockState(cell);
            if (state.isAir()) {
                continue;
            }
            BlockEntity entity = from.getBlockEntity(cell);
            CompoundTag data = entity == null ? null : entity.saveWithFullMetadata(from.registryAccess());
            moves.add(new Move(cell.immutable(), target.offset(cell.subtract(origin)), state, data));
        }
        int place = net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE;
        for (Move move : moves) {
            to.setBlock(move.to(), move.state(), place);
            if (move.data() != null) {
                BlockEntity loaded = BlockEntity.loadStatic(move.to(), move.state(), move.data(), to.registryAccess());
                if (loaded != null) {
                    to.setBlockEntity(loaded);
                }
            }
        }
        // L'équipage et tout ce qui flotte à bord suivent le vaisseau, à la même place.
        for (Entity entity : from.getEntities((Entity) null, box(origin, out), entity -> entity.getVehicle() == null)) {
            Vec3 at = entity.position().subtract(Vec3.atLowerCornerOf(origin)).add(Vec3.atLowerCornerOf(target));
            entity.changeDimension(new DimensionTransition(to, at, Vec3.ZERO, entity.getYRot(), entity.getXRot(), DimensionTransition.DO_NOTHING));
        }
        int remove = place | net.minecraft.world.level.block.Block.UPDATE_SUPPRESS_DROPS
                | net.minecraft.world.level.block.Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
        for (Move move : moves) {
            from.removeBlockEntity(move.from());
            from.setBlock(move.from(), Blocks.AIR.defaultBlockState(), remove);
        }
    }

    /** Le cœur de distorsion arrivé à destination a dépensé de quoi faire ce saut. */
    private static void spendCharge(ServerLevel level, BlockPos origin, Direction out, int cost) {
        for (ShipPlan.Piece piece : ShipPlan.pieces(out)) {
            if (piece.state().is(ModBlocks.WARP_CORE.get())) {
                BlockPos pos = origin.offset(piece.offset());
                BlockState state = level.getBlockState(pos);
                if (state.hasProperty(WarpCoreBlock.CHARGE)) {
                    int charge = Math.max(0, state.getValue(WarpCoreBlock.CHARGE) - cost);
                    level.setBlock(pos, state.setValue(WarpCoreBlock.CHARGE, charge), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** Les joueurs à bord : dans la boîte du vaisseau. */
    private static List<ServerPlayer> crew(ServerLevel level, BlockPos origin, Direction out) {
        AABB box = box(origin, out);
        return level.players().stream().filter(player -> box.contains(player.position())).toList();
    }

    private static void title(ServerPlayer player, Component title, Component subtitle) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(4, 30, 8));
        if (subtitle != null) {
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle.copy().withStyle(ChatFormatting.LIGHT_PURPLE)));
        }
        player.connection.send(new ClientboundSetTitleTextPacket(title.copy().withStyle(ChatFormatting.AQUA)));
    }
}
