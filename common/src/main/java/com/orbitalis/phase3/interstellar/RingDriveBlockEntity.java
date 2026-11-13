package com.orbitalis.phase3.interstellar;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.orbitalis.core.registry.ModBlockEntities;
import com.orbitalis.core.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.orbitalis.platform.nbt.ValueInput;
import com.orbitalis.platform.nbt.ValueOutput;

/**
 * Le moteur de l'anneau, en marche ou à l'arrêt. Lancé, il met un segment invisible à la place de chaque bloc de
 * l'anneau et de ses rayons, et c'est son rendu qui dessine l'anneau en rotation. Il prend de la vitesse en quatre
 * secondes ; arrêté, il ralentit autant et s'immobilise sur un quart de tour, là où l'anneau retrouve exactement ses
 * blocs. Tant qu'il tourne, l'équipage retrouve la pesanteur dans tout le vaisseau.
 * <p>
 * L'angle ne dépend que du temps du monde, du tick de départ et de celui où le ralentissement commence : le serveur et
 * chaque joueur le calculent de leur côté, sans rien s'envoyer d'autre.
 */
public class RingDriveBlockEntity extends BlockEntity implements com.orbitalis.platform.Compat.RemovalAware {
    /** Vitesse de croisière, en degrés par tick : un tour en douze secondes. */
    public static final double SPEED = 1.5;
    /** Durée de la mise en vitesse, et du ralentissement, en ticks. */
    public static final int RAMP = 80;

    /**
     * Les moteurs en marche, par dimension, côté serveur et côté joueur : la pesanteur de bord se cherche parmi eux (le
     * client en a besoin aussi, c'est lui qui calcule la nage en apesanteur).
     */
    private static final Map<ResourceKey<Level>, Set<BlockPos>> RUNNING = new HashMap<>();
    private static final Map<ResourceKey<Level>, Set<BlockPos>> RUNNING_CLIENT = new HashMap<>();

    private static Map<ResourceKey<Level>, Set<BlockPos>> running(Level level) {
        return level.isClientSide() ? RUNNING_CLIENT : RUNNING;
    }

    /** Tick du démarrage, ou -1 à l'arrêt. */
    private long start = -1;
    /** Tick où le ralentissement commence, ou -1 tant qu'on ne l'a pas arrêté. */
    private long stop = -1;
    /** Le sens de la proue du vaisseau. */
    private Direction out = Direction.EAST;

    public RingDriveBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RING_DRIVE.get(), pos, state);
    }

    public boolean spinning() {
        return start >= 0;
    }

    public boolean stopping() {
        return stop >= 0;
    }

    public Direction out() {
        return out;
    }

    /** La poupe du vaisseau de ce moteur. */
    public BlockPos shipOrigin() {
        return worldPosition.subtract(ShipPlan.local(out, ShipPlan.RING_A, 0, ShipPlan.RING_DRIVE_Y));
    }

    /** Le centre de l'anneau : sur l'axe du vaisseau, sous le moteur. */
    public BlockPos ringCenter() {
        return worldPosition.below(ShipPlan.RING_DRIVE_Y);
    }

    /** Angle de l'anneau, en degrés, à ce moment (un tick et une fraction). */
    public double angle(double time) {
        return angle(start, stop, time);
    }

    static double angle(long start, long stop, double time) {
        if (start < 0) {
            return 0.0;
        }
        if (stop >= 0 && time > stop) {
            double slowing = Math.min(time - stop, RAMP);
            return spun(stop - start) + SPEED * slowing - SPEED * slowing * slowing / (2.0 * RAMP);
        }
        return spun(time - start);
    }

    /** Angle parcouru depuis le départ, la mise en vitesse comprise. */
    private static double spun(double ticks) {
        if (ticks <= 0) {
            return 0.0;
        }
        if (ticks < RAMP) {
            return SPEED * ticks * ticks / (2.0 * RAMP);
        }
        return SPEED * RAMP / 2.0 + SPEED * (ticks - RAMP);
    }

    // ------------------------------------------------------------------
    // Marche et arrêt
    // ------------------------------------------------------------------

    /** Un clic droit sur le moteur : il démarre, ou il commence à ralentir. La réponse est pour le joueur. */
    public Component toggle() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        if (stopping()) {
            return Component.translatable("ship.orbitalis.ring.stopping");
        }
        if (spinning()) {
            slowDown(serverLevel.getGameTime());
            serverLevel.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.8F, 0.6F);
            return Component.translatable("ship.orbitalis.ring.stop");
        }
        Component problem = start(serverLevel);
        if (problem != null) {
            return problem;
        }
        serverLevel.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8F, 0.5F);
        return Component.translatable("ship.orbitalis.ring.start");
    }

    /** Lance l'anneau, s'il est à bord d'un vaisseau et entier ; sinon, ce qui l'en empêche. */
    public Component start(ServerLevel level) {
        Direction ship = findShip(level, worldPosition);
        if (ship == null) {
            return Component.translatable("ship.orbitalis.ring.no_ship");
        }
        out = ship;
        List<ShipPlan.Piece> ring = ShipPlan.ring(out);
        BlockPos origin = shipOrigin();
        for (ShipPlan.Piece piece : ring) {
            BlockPos pos = origin.offset(piece.offset());
            if (!level.isLoaded(pos) || !ShipPlan.matches(level.getBlockState(pos), piece.state())) {
                return Component.translatable("ship.orbitalis.ring.incomplete");
            }
        }
        for (ShipPlan.Piece piece : ring) {
            BlockPos pos = origin.offset(piece.offset());
            if (!(level.getBlockState(pos).getBlock() instanceof RingSegmentBlock)) {
                level.setBlock(pos, ModBlocks.RING_SEGMENT.get().defaultBlockState()
                        .setValue(RingSegmentBlock.KIND, RingSegmentBlock.Kind.of(piece.state())), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
        start = level.getGameTime();
        stop = -1;
        changed();
        return null;
    }

    /** Commence à ralentir, au plus tôt une fois la vitesse atteinte, et de façon à finir sur un quart de tour. */
    void slowDown(long now) {
        long from = Math.max(now, start + RAMP);
        long tick = from;
        // À vitesse de croisière, un quart de tour prend soixante ticks : la bonne place se trouve dans ce délai.
        for (long t = from; t < from + 400; t++) {
            double end = angle(start, -1, t) + SPEED * RAMP / 2.0;
            if (Math.abs(end / 90.0 - Math.rint(end / 90.0)) < 1.0E-6) {
                tick = t;
                break;
            }
        }
        stop = tick;
        changed();
    }

    /** L'anneau s'immobilise : chaque segment redevient le bloc qu'il remplaçait. */
    public void halt() {
        if (level != null && !level.isClientSide()) {
            BlockPos origin = shipOrigin();
            for (ShipPlan.Piece piece : ShipPlan.ring(out)) {
                BlockPos pos = origin.offset(piece.offset());
                BlockState there = level.isLoaded(pos) ? level.getBlockState(pos) : null;
                if (there != null && there.getBlock() instanceof RingSegmentBlock) {
                    level.setBlock(pos, RingSegmentBlock.original(there), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
        if (level != null) {
            Set<BlockPos> running = running(level).get(level.dimension());
            if (running != null) {
                running.remove(worldPosition);
            }
        }
        start = -1;
        stop = -1;
        changed();
    }

    /**
     * À chaque tick, des deux côtés : un moteur en marche se fait connaître pour la pesanteur de bord. Côté serveur, en
     * plus, la fin du ralentissement ; et un anneau qu'on aurait abîmé s'arrête net.
     */
    void tick() {
        if (!spinning() || level == null) {
            return;
        }
        running(level).computeIfAbsent(level.dimension(), key -> new HashSet<>()).add(worldPosition.immutable());
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        long now = serverLevel.getGameTime();
        if (stopping() && now >= stop + RAMP) {
            halt();
            return;
        }
        if (now % 20 == 0 && !intact(serverLevel)) {
            halt();
        }
    }

    /** Tous les segments de l'anneau sont-ils encore là ? */
    private boolean intact(ServerLevel level) {
        BlockPos origin = shipOrigin();
        for (ShipPlan.Piece piece : ShipPlan.ring(out)) {
            BlockPos pos = origin.offset(piece.offset());
            if (level.isLoaded(pos) && !(level.getBlockState(pos).getBlock() instanceof RingSegmentBlock)) {
                return false;
            }
        }
        return true;
    }

    /** Le sens de la proue du vaisseau dont ce moteur est celui de l'anneau, ou rien s'il n'y a pas de poste de pilotage à sa place. */
    static Direction findShip(Level level, BlockPos drive) {
        for (Direction out : Direction.Plane.HORIZONTAL) {
            BlockPos origin = drive.subtract(ShipPlan.local(out, ShipPlan.RING_A, 0, ShipPlan.RING_DRIVE_Y));
            BlockPos helm = origin.offset(ShipPlan.local(out, ShipPlan.HELM_A, 0, ShipPlan.HELM_Y));
            if (level.isLoaded(helm) && level.getBlockState(helm).is(ModBlocks.SHIP_HELM.get())) {
                return out;
            }
        }
        return null;
    }

    /** Ce joueur est-il à bord d'un vaisseau dont l'anneau tourne ? Il y retrouve alors la pesanteur. */
    public static boolean gravityAt(Player player) {
        Set<BlockPos> running = running(player.level()).get(player.level().dimension());
        if (running == null || running.isEmpty()) {
            return false;
        }
        for (Iterator<BlockPos> it = running.iterator(); it.hasNext();) {
            BlockPos pos = it.next();
            if (!player.level().isLoaded(pos) || !(player.level().getBlockEntity(pos) instanceof RingDriveBlockEntity drive) || !drive.spinning()) {
                it.remove();
                continue;
            }
            if (ShipJump.box(drive.shipOrigin(), drive.out).contains(player.position())) {
                return true;
            }
        }
        return false;
    }

    /** Le moteur cassé : l'anneau retrouve ses blocs. Un saut, qui l'emporte ailleurs, ne passe pas par ici. */
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (spinning()) {
            halt();
        }
    }

    private void changed() {
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider registries$) {
        ValueOutput output = ValueOutput.of(tag$, registries$);
        super.saveAdditional(output.tag(), output.lookup());
        output.putLong("start", start);
        output.putLong("stop", stop);
        output.putString("out", out.getSerializedName());
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider registries$) {
        ValueInput input = ValueInput.of(tag$, registries$);
        super.loadAdditional(input.tag(), input.lookup());
        start = input.getLongOr("start", -1L);
        stop = input.getLongOr("stop", -1L);
        Direction loaded = Direction.byName(input.getStringOr("out", "east"));
        out = loaded != null && loaded.getAxis().isHorizontal() ? loaded : Direction.EAST;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }
}
