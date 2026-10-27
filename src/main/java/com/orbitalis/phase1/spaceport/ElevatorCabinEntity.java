package com.orbitalis.phase1.spaceport;

import java.util.ArrayList;
import java.util.List;

import com.orbitalis.Orbitalis;
import com.orbitalis.core.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.network.PacketDistributor;
import com.orbitalis.core.network.ElevatorPayload;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;

/**
 * Cabine de l'ascenseur de la tour de service, haute de trois blocs : un plancher de trois blocs sur trois, qui occupe tout l'intérieur
 * de la tour et monte et descend en son centre. La cabine est la même de chaque côté de son axe : on y entre par
 * l'arrière de la tour, on en sort en face, vers le portique d'embarquement.
 * <p>
 * Son emploi est volontairement simple. Debout dans la cabine, un clic droit sur son plancher ouvre le panneau de
 * commande : la liste des arrêts (le sol, et chaque portique d'embarquement de la tour). On choisit, la cabine y va ;
 * le trajet dure moins de quatre secondes, quelle que soit la hauteur. Un clic droit sur un portique ou sur le socle
 * appelle la cabine, et une cabine vide vient d'elle-même à l'étage où un joueur l'attend.
 * <p>
 * Pour que le trajet soit sans à-coups, la cabine prend ses occupants à son bord le temps du voyage, comme un
 * véhicule (ils restent debout), puis les repose sur son plancher à l'arrivée. À l'arrêt, c'est un plancher
 * solide ordinaire : on y marche avec la physique habituelle du jeu.
 * <p>
 * La gaine est fermée de toutes parts par la charpente de la tour. Chaque portique a une porte palière, que la
 * cabine ouvre en arrivant et referme en partant : on ne peut pas tomber dans la gaine.
 */
public class ElevatorCabinEntity extends Entity {
    /** Épaisseur du plancher, en blocs : la cabine est posée sur le bas de sa case, son dessus est un pixel plus haut. */
    public static final double FLOOR = 1.0 / 16.0;
    /** Vitesse de croisière, en blocs par seconde, et bornes de la durée d'un trajet, en ticks. */
    private static final double SPEED = 12.0;
    private static final int MIN_TRAVEL = 30;
    private static final int MAX_TRAVEL = 75;
    /** Délai après un arrêt avant de répondre à un appel, en ticks : les occupants ont le temps de sortir. */
    private static final int REST = 12;
    /** Distance à laquelle un joueur qui attend à un étage fait venir la cabine vide, en blocs. */
    private static final double CALL_RANGE = 1.5;
    /** Demi-largeur du plancher, en blocs, et nombre d'occupants que la cabine emporte. */
    private static final double HALF = 1.5;
    private static final int SEATS = 4;

    private static final EntityDataAccessor<Integer> FACING = SynchedEntityData.defineId(ElevatorCabinEntity.class, EntityDataSerializers.INT);
    /** Place de chaque occupant sur le plancher : son écart au centre de la cabine, en x puis en z, en blocs. */
    private static final List<EntityDataAccessor<Float>> SEAT_X = new ArrayList<>();
    private static final List<EntityDataAccessor<Float>> SEAT_Z = new ArrayList<>();

    static {
        for (int seat = 0; seat < SEATS; seat++) {
            SEAT_X.add(SynchedEntityData.defineId(ElevatorCabinEntity.class, EntityDataSerializers.FLOAT));
            SEAT_Z.add(SynchedEntityData.defineId(ElevatorCabinEntity.class, EntityDataSerializers.FLOAT));
        }
    }

    private final InterpolationHandler interpolation = new InterpolationHandler(this, 3);
    /** Socle auquel la cabine est liée. */
    private BlockPos home = BlockPos.ZERO;
    /** Niveaux des arrêts, du plus bas au plus haut ; relevés une fois par seconde. */
    private List<Integer> stops = List.of();
    /** Pied de la colonne technique de la tour : les portiques, et leurs portes palières, sont dans cette colonne. */
    private BlockPos riser;
    /** Axe de la gaine : le centre de la tour, là où se trouve le socle. */
    private double shaftX;
    private double shaftZ;
    // Trajet en cours.
    private double startY;
    private double targetY;
    private int travel;
    private int elapsed;
    private int rest;

    public ElevatorCabinEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(FACING, Direction.EAST.get2DDataValue());
        for (int seat = 0; seat < SEATS; seat++) {
            builder.define(SEAT_X.get(seat), 0.0F);
            builder.define(SEAT_Z.get(seat), 0.0F);
        }
    }

    public BlockPos getHome() {
        return home;
    }

    public void setHome(BlockPos pos) {
        home = pos.immutable();
        shaftX = pos.getX() + 0.5;
        shaftZ = pos.getZ() + 0.5;
    }

    /** Direction dans laquelle la cabine est tournée : vers la fusée, comme la tour. */
    public Direction getFacing() {
        return Direction.from2DDataValue(entityData.get(FACING));
    }

    public boolean isMoving() {
        return elapsed < travel;
    }

    @Override
    public InterpolationHandler getInterpolation() {
        return interpolation;
    }

    /** Le plancher : trois blocs sur trois, un pixel d'épaisseur. */
    @Override
    protected AABB makeBoundingBox(Vec3 position) {
        return new AABB(position.x - HALF, position.y, position.z - HALF, position.x + HALF, position.y + FLOOR, position.z + HALF);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (FACING.equals(key)) {
            setBoundingBox(makeBoundingBox(position()));
        }
    }

    // ------------------------------------------------------------------
    // Déroulement
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            interpolation.interpolate();
            return;
        }
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (stops.isEmpty() || tickCount % 20 == 0) {
            if (!level.getBlockState(home).is(ModBlocks.TOWER_ELEVATOR.get())) {
                // Le socle a été retiré : la cabine s'en va avec lui, et les portes palières restent ouvertes.
                setDoors(level, Integer.MIN_VALUE);
                for (int floor : stops) {
                    openDoor(level, floor);
                }
                ejectPassengers();
                discard();
                return;
            }
            survey(level);
        }
        if (isMoving()) {
            elapsed++;
            float progress = elapsed / (float) travel;
            // Départ et arrivée en douceur.
            float eased = progress * progress * (3.0F - 2.0F * progress);
            setPos(shaftX, Mth.lerp(eased, startY, targetY), shaftZ);
            if (!isMoving()) {
                arrive(level);
            }
            return;
        }
        if (rest > 0) {
            rest--;
            return;
        }
        // Une cabine vide rejoint l'étage où quelqu'un l'attend.
        if (tickCount % 10 == 0 && occupants(level).isEmpty()) {
            answerCall(level);
        }
    }

    /** Joueurs qui se tiennent dans la cabine à l'arrêt. */
    private List<ServerPlayer> occupants(ServerLevel level) {
        return level.getEntitiesOfClass(ServerPlayer.class, interior(getY()), player -> !player.isSpectator() && !player.isPassenger());
    }

    /** Clic droit sur le plancher, depuis la cabine : ouvre le panneau de commande. */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (level() instanceof ServerLevel level && player instanceof ServerPlayer serverPlayer && !isMoving()
                && occupants(level).contains(serverPlayer)) {
            PacketDistributor.sendToPlayer(serverPlayer, new ElevatorPayload(getId(), stops, nearestStop(), -1));
        }
        return InteractionResult.SUCCESS;
    }

    /** Le joueur a choisi un arrêt sur le panneau : la cabine y part, avec tous ses occupants. */
    public static void handle(ServerPlayer player, ElevatorPayload payload) {
        if (!(player.level().getEntity(payload.cabin()) instanceof ElevatorCabinEntity cabin) || cabin.isMoving()
                || !(cabin.level() instanceof ServerLevel level)) {
            return;
        }
        List<ServerPlayer> riders = cabin.occupants(level);
        int target = payload.target();
        if (riders.contains(player) && target >= 0 && target < cabin.stops.size() && target != cabin.nearestStop()) {
            cabin.board(riders);
            cabin.depart(level, cabin.stops.get(target));
        }
    }

    /** Appelle à un étage la cabine de la gaine la plus proche d'un bloc (portique ou socle), si elle est libre. */
    public static void call(ServerLevel level, BlockPos from, int floor) {
        for (ElevatorCabinEntity cabin : level.getEntitiesOfClass(ElevatorCabinEntity.class,
                new AABB(from).inflate(3.0, LaunchComplex.MAX_TOWER_HEIGHT, 3.0))) {
            if (cabin.occupants(level).isEmpty()) {
                cabin.sendTo(floor);
            }
        }
    }

    /**
     * Ouvre la porte palière de l'étage où se trouve la cabine, et ferme toutes les autres. Sans cabine (socle
     * retiré), toutes les portes restent ouvertes.
     */
    private void setDoors(ServerLevel level, int openFloor) {
        if (riser == null) {
            return;
        }
        for (int floor : stops) {
            ModBlocks.BOARDING_GATE.get().setClosed(level, new BlockPos(riser.getX(), floor, riser.getZ()), floor != openFloor);
        }
    }

    private void openDoor(ServerLevel level, int floor) {
        if (riser != null) {
            ModBlocks.BOARDING_GATE.get().setClosed(level, new BlockPos(riser.getX(), floor, riser.getZ()), false);
        }
    }

    /** Relève la gaine et les arrêts : le sol, puis chaque portique d'embarquement de la tour. */
    private void survey(ServerLevel level) {
        List<Integer> found = new ArrayList<>();
        found.add(home.getY());
        riser = null;
        double x = home.getX() + 0.5;
        double z = home.getZ() + 0.5;
        LaunchTableBlockEntity table = LaunchTableBlockEntity.nearest(level, home, 24.0);
        BlockPos base = table == null ? null : table.getComplex().towerBase();
        if (base != null && table.isStructureManaged()) {
            Direction forward = LaunchComplex.towerFacing(base, table.getBlockPos());
            // Le socle doit être au centre de la tour : deux cases derrière la colonne technique.
            if (home.equals(base.relative(forward, -2))) {
                riser = base;
                entityData.set(FACING, forward.get2DDataValue());
                for (int gate : table.getGateLevels()) {
                    found.add(gate);
                }
            }
        }
        found.sort(null);
        stops = List.copyOf(found);
        shaftX = x;
        shaftZ = z;
        if (!isMoving()) {
            // À l'arrêt, la cabine est toujours exactement à un étage.
            double level0 = stops.get(nearestStop());
            if (getX() != x || getY() != level0 || getZ() != z) {
                setPos(x, level0, z);
            }
            setDoors(level, (int) level0);
        }
    }

    private int nearestStop() {
        int best = 0;
        for (int i = 1; i < stops.size(); i++) {
            if (Math.abs(stops.get(i) - getY()) < Math.abs(stops.get(best) - getY())) {
                best = i;
            }
        }
        return best;
    }

    /** Volume intérieur de la cabine posée à une hauteur donnée : là où se tient un occupant. */
    private AABB interior(double y) {
        return new AABB(shaftX - HALF, y, shaftZ - HALF, shaftX + HALF, y + 1.9, shaftZ + HALF);
    }

    /** Une cabine vide rejoint l'étage où un joueur l'attend, tout près de la gaine. */
    private void answerCall(ServerLevel level) {
        int here = nearestStop();
        for (int i = 0; i < stops.size(); i++) {
            if (i != here && !level.getEntitiesOfClass(ServerPlayer.class, interior(stops.get(i)).inflate(CALL_RANGE, 0.5, CALL_RANGE),
                    player -> !player.isSpectator()).isEmpty()) {
                depart(level, stops.get(i));
                return;
            }
        }
    }

    /**
     * Envoie la cabine à l'arrêt d'un niveau donné. Renvoie faux si elle est déjà en route ou si ce niveau n'est
     * pas un arrêt.
     */
    public boolean sendTo(int stopLevel) {
        if (isMoving() || !stops.contains(stopLevel) || !(level() instanceof ServerLevel level)) {
            return false;
        }
        depart(level, stopLevel);
        return true;
    }

    /** Prend les occupants à bord pour le trajet : ils gardent leur place dans la cabine. */
    private void board(List<ServerPlayer> riders) {
        for (int i = 0; i < Math.min(SEATS, riders.size()); i++) {
            ServerPlayer player = riders.get(i);
            entityData.set(SEAT_X.get(i), (float) Mth.clamp(player.getX() - shaftX, -HALF + 0.35, HALF - 0.35));
            entityData.set(SEAT_Z.get(i), (float) Mth.clamp(player.getZ() - shaftZ, -HALF + 0.35, HALF - 0.35));
            player.startRiding(this, true, false);
        }
    }

    private void depart(ServerLevel level, int target) {
        startY = getY();
        targetY = target;
        travel = Mth.clamp((int) Math.round(Math.abs(targetY - startY) / SPEED * 20.0), MIN_TRAVEL, MAX_TRAVEL);
        elapsed = 0;
        // Les portes se ferment avant le départ.
        setDoors(level, Integer.MIN_VALUE);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.6F, 0.6F);
    }

    /** Arrivée : les occupants sont reposés sur le plancher, libres de leurs mouvements. */
    private void arrive(ServerLevel level) {
        setPos(shaftX, targetY, shaftZ);
        ejectPassengers();
        setDoors(level, (int) targetY);
        rest = REST;
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.7F, 1.4F);
    }

    // ------------------------------------------------------------------
    // Occupants
    // ------------------------------------------------------------------

    /** Place d'un occupant sur le plancher : celle qu'il avait en montant. */
    private Vec3 seat(Entity passenger, double height) {
        int index = getPassengers().indexOf(passenger);
        if (index < 0 || index >= SEATS) {
            return new Vec3(getX(), getY() + height, getZ());
        }
        return new Vec3(getX() + entityData.get(SEAT_X.get(index)), getY() + height, getZ() + entityData.get(SEAT_Z.get(index)));
    }

    /** Les occupants se tiennent debout sur le plancher. */
    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        Vec3 seat = seat(passenger, FLOOR);
        move.accept(passenger, seat.x, seat.y, seat.z);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return seat(passenger, FLOOR + 0.01);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < SEATS;
    }

    /** Les occupants restent debout pendant le trajet. */
    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    /** À l'arrêt comme en marche, le plancher porte ce qui se trouve dessus. */
    @Override
    public boolean canBeCollidedWith(Entity other) {
        return true;
    }

    /** On peut viser le plancher : un clic droit y ouvre le panneau de commande. */
    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** Rien ne détruit la cabine : on la retire en cassant son socle. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    // ------------------------------------------------------------------
    // Sauvegarde
    // ------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store("home", BlockPos.CODEC, home);
        output.putInt("facing", entityData.get(FACING));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        setHome(input.read("home", BlockPos.CODEC).orElse(blockPosition()));
        entityData.set(FACING, input.getIntOr("facing", Direction.EAST.get2DDataValue()));
    }

    /** Pendant le trajet, on ne quitte pas la cabine : Accroupi sert à descendre d'un étage, pas à en sortir. */
    @EventBusSubscriber(modid = Orbitalis.MODID)
    public static final class Rules {
        private Rules() {
        }

        @SubscribeEvent
        public static void onMount(EntityMountEvent event) {
            if (event.isDismounting() && event.getEntityBeingMounted() instanceof ElevatorCabinEntity cabin
                    && !cabin.isRemoved() && cabin.isMoving() && event.getEntityMounting().isAlive()) {
                event.setCanceled(true);
            }
        }
    }
}
