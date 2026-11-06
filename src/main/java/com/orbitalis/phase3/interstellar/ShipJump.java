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
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Le saut du vaisseau interstellaire. Depuis le poste de pilotage, quand tous ses systèmes sont prêts et que Proxima b a
 * été découverte à l'observatoire, le vaisseau quitte son berceau du chantier naval et réapparaît en orbite de Proxima b,
 * avec tout ce qu'il contient et son équipage ; le saut retour le ramène à son berceau.
 * <p>
 * Un saut coûte {@link #JUMP_COST} matières exotiques au cœur de distorsion : un cœur plein fait l'aller et le retour.
 * Après l'ordre de saut, un compte à rebours de {@link #COUNTDOWN} ticks laisse à l'équipage le temps d'embarquer.
 */
public final class ShipJump {
    /** Matières exotiques consommées par un saut. */
    public static final int JUMP_COST = 4;
    /** Durée du compte à rebours, en ticks. */
    public static final int COUNTDOWN = 100;

    /** Ce qui empêche de sauter, dans l'ordre où on le vérifie. */
    public enum Hold {
        NONE, PLACE, SYSTEMS, CHARGE, UNDISCOVERED, BLOCKED, JUMPING;

        public String key() {
            return "ship.orbitalis.helm.block." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Où se trouve le vaisseau : à son berceau (dans l'orbite de la planète), en orbite de Proxima b, ou ailleurs. */
    public enum Place {
        HOME, AWAY, ELSEWHERE
    }

    /** Un saut ordonné, en compte à rebours. */
    private static final class Pending {
        final ResourceKey<Level> dimension;
        final BlockPos origin;
        final Direction out;
        final String agency;
        final boolean toProxima;
        int ticks = COUNTDOWN;

        Pending(ResourceKey<Level> dimension, BlockPos origin, Direction out, String agency, boolean toProxima) {
            this.dimension = dimension;
            this.origin = origin;
            this.out = out;
            this.agency = agency;
            this.toProxima = toProxima;
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
        if (level.dimension() == SpaceRules.PROXIMA_ORBIT) {
            ShipVoyages.Voyage voyage = ShipVoyages.of(level.getServer()).of(agency);
            if (voyage != null && voyage.away() && voyage.out() == out && voyage.awayOrigin().equals(origin)) {
                return Place.AWAY;
            }
        }
        return Place.ELSEWHERE;
    }

    /** Ce qui empêche ce vaisseau de sauter, ou {@link Hold#NONE}. */
    public static Hold blocker(ServerLevel level, BlockPos origin, Direction out, String agency, ShipSystems systems) {
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
        if (systems.charge() < JUMP_COST) {
            return Hold.CHARGE;
        }
        if (place == Place.HOME && !SatelliteManager.of(level.getServer(), agency).isDiscovered(CelestialBody.PROXIMA_B)) {
            return Hold.UNDISCOVERED;
        }
        ServerLevel to = destination(level.getServer(), place);
        BlockPos target = destinationOrigin(level.getServer(), place, agency);
        if (to == null || target == null || !clear(to, target, out)) {
            return Hold.BLOCKED;
        }
        return Hold.NONE;
    }

    private static ServerLevel destination(MinecraftServer server, Place place) {
        return server.getLevel(place == Place.HOME ? SpaceRules.PROXIMA_ORBIT : SpaceRules.ORBIT);
    }

    /** Centre de la poupe à l'arrivée : la place de l'agence en orbite de Proxima b, ou le berceau d'où le vaisseau était parti. */
    private static BlockPos destinationOrigin(MinecraftServer server, Place place, String agency) {
        ShipVoyages voyages = ShipVoyages.of(server);
        ShipVoyages.Voyage voyage = voyages.of(agency);
        if (place == Place.AWAY) {
            return voyage == null ? null : voyage.home();
        }
        // Une agence garde sa place en orbite de Proxima b d'un voyage à l'autre ; la première fois, la suivante libre.
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

    /** Toutes les cases de la boîte du vaisseau : sa longueur, et en travers comme en hauteur le rayon de l'anneau. */
    static List<BlockPos> cells(BlockPos origin, Direction out) {
        List<BlockPos> cells = new ArrayList<>();
        int r = ShipPlan.RING_RADIUS;
        for (int a = 0; a < ShipPlan.LENGTH; a++) {
            for (int s = -r; s <= r; s++) {
                for (int y = -r; y <= r; y++) {
                    cells.add(origin.offset(ShipPlan.local(out, a, s, y)));
                }
            }
        }
        return cells;
    }

    /** La boîte du vaisseau, bord extérieur des cases compris. */
    static AABB box(BlockPos origin, Direction out) {
        int r = ShipPlan.RING_RADIUS;
        BlockPos a = origin.offset(ShipPlan.local(out, 0, -r, -r));
        BlockPos b = origin.offset(ShipPlan.local(out, ShipPlan.LENGTH - 1, r, r));
        return new AABB(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1);
    }

    // ------------------------------------------------------------------
    // Poste de pilotage
    // ------------------------------------------------------------------

    /** L'état du vaisseau pour l'écran du poste de pilotage. */
    public static ShipHelmPayload status(ServerPlayer player, BlockPos helm, BlockPos origin, Direction out) {
        ServerLevel level = player.level();
        String agency = ResearchManager.agencyKey(player);
        ShipSystems systems = ShipSystems.check(level, origin, out);
        Place place = place(level, origin, out, agency);
        Hold blocker = blocker(level, origin, out, agency, systems);
        Pending pending = pending(level.dimension(), origin);
        boolean spinning = level.getBlockEntity(origin.offset(ShipPlan.local(out, ShipPlan.RING_A, 0, ShipPlan.RING_DRIVE_Y)))
                instanceof RingDriveBlockEntity drive && drive.spinning();
        return ShipHelmPayload.of(helm, systems, place.ordinal(), blocker.ordinal(), pending == null ? -1 : pending.ticks, spinning);
    }

    /** Le joueur ordonne le saut depuis le poste de pilotage : le compte à rebours commence, si rien ne l'empêche. */
    public static void request(ServerPlayer player, BlockPos helm) {
        ServerLevel level = player.level();
        BlockState state = level.getBlockState(helm);
        if (!Features.interstellar() || !state.is(ModBlocks.SHIP_HELM.get()) || player.distanceToSqr(Vec3.atCenterOf(helm)) > 64) {
            return;
        }
        Direction out = state.getValue(ShipHelmBlock.FACING).getOpposite();
        BlockPos origin = ShipHelmBlock.shipOrigin(helm, state.getValue(ShipHelmBlock.FACING));
        String agency = ResearchManager.agencyKey(player);
        ShipSystems systems = ShipSystems.check(level, origin, out);
        Hold blocker = blocker(level, origin, out, agency, systems);
        if (blocker != Hold.NONE) {
            player.sendSystemMessage(Component.translatable(blocker.key(), systems.charge(), JUMP_COST).withStyle(ChatFormatting.GOLD), true);
            return;
        }
        Place place = place(level, origin, out, agency);
        PENDING.add(new Pending(level.dimension(), origin.immutable(), out, agency, place == Place.HOME));
        level.playSound(null, helm, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 0.6F);
        for (ServerPlayer crew : crew(level, origin, out)) {
            title(crew, Component.translatable("ship.orbitalis.jump.countdown", COUNTDOWN / 20),
                    Component.translatable(place == Place.HOME ? "ship.orbitalis.jump.to_proxima" : "ship.orbitalis.jump.to_home"));
        }
        ModNetwork.send(player, status(player, helm, origin, out));
    }

    // ------------------------------------------------------------------
    // Navette
    // ------------------------------------------------------------------

    /**
     * Le joueur prend la navette depuis le poste de pilotage, le vaisseau en orbite de Proxima b : une capsule se
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
        // Au flanc gauche du vaisseau, à hauteur de la serre, à l'écart de la coque.
        BlockPos dock = origin.offset(ShipPlan.local(out, ShipPlan.HELM_A - 3, -7, 0));
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

    private static Pending pending(ResourceKey<Level> dimension, BlockPos origin) {
        for (Pending pending : PENDING) {
            if (pending.dimension == dimension && pending.origin.equals(origin)) {
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
            it.remove();
            jump(level, pending);
        }
    }

    /** Le saut lui-même, au bout du compte à rebours : tout est vérifié une dernière fois. */
    private static void jump(ServerLevel level, Pending pending) {
        MinecraftServer server = level.getServer();
        ShipSystems systems = ShipSystems.check(level, pending.origin, pending.out);
        Hold blocker = blocker(level, pending.origin, pending.out, pending.agency, systems);
        List<ServerPlayer> crew = crew(level, pending.origin, pending.out);
        if (blocker != Hold.NONE) {
            for (ServerPlayer player : crew) {
                title(player, Component.translatable("ship.orbitalis.jump.cancelled"), Component.translatable(blocker.key(), systems.charge(), JUMP_COST));
            }
            return;
        }
        Place place = pending.toProxima ? Place.HOME : Place.AWAY;
        ServerLevel to = destination(server, place);
        BlockPos target = destinationOrigin(server, place, pending.agency);
        transfer(level, pending.origin, to, target, pending.out);
        spendCharge(to, target, pending.out);
        ShipVoyages voyages = ShipVoyages.of(server);
        if (pending.toProxima) {
            voyages.depart(pending.agency, pending.origin, pending.out);
        } else {
            voyages.back(pending.agency);
        }
        to.playSound(null, target, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.0F, 1.2F);
        for (ServerPlayer player : crew) {
            title(player, Component.translatable(pending.toProxima ? "ship.orbitalis.jump.arrived_proxima" : "ship.orbitalis.jump.arrived_home"),
                    Component.translatable("ship.orbitalis.jump.arrived_sub"));
        }
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
            entity.teleport(new TeleportTransition(to, at, Vec3.ZERO, entity.getYRot(), entity.getXRot(), TeleportTransition.DO_NOTHING));
        }
        int remove = place | net.minecraft.world.level.block.Block.UPDATE_SUPPRESS_DROPS
                | net.minecraft.world.level.block.Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
        for (Move move : moves) {
            from.removeBlockEntity(move.from());
            from.setBlock(move.from(), Blocks.AIR.defaultBlockState(), remove);
        }
    }

    /** Le cœur de distorsion arrivé à destination a dépensé de quoi faire ce saut. */
    private static void spendCharge(ServerLevel level, BlockPos origin, Direction out) {
        for (ShipPlan.Piece piece : ShipPlan.pieces(out)) {
            if (piece.state().is(ModBlocks.WARP_CORE.get())) {
                BlockPos pos = origin.offset(piece.offset());
                BlockState state = level.getBlockState(pos);
                if (state.hasProperty(WarpCoreBlock.CHARGE)) {
                    int charge = Math.max(0, state.getValue(WarpCoreBlock.CHARGE) - JUMP_COST);
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
