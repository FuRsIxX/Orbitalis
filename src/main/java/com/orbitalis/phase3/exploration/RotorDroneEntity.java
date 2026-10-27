package com.orbitalis.phase3.exploration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.orbitalis.core.registry.ModItems;
import com.orbitalis.core.research.AgencyResearch;
import com.orbitalis.core.research.ResearchManager;
import com.orbitalis.phase2.survival.SpaceRules;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Le drone volant, à la manière de Dragonfly, le giravion que la NASA envoie sur Titan. Il ne transporte personne :
 * posé au sol, un clic droit l'envoie en vol de reconnaissance. Il monte, file vers un site à quelques dizaines de
 * blocs, s'y pose et l'étudie, puis revient se poser où il est parti, et recharge sa batterie à son générateur à
 * radio-isotopes avant le vol suivant.
 * <p>
 * Il lui faut de l'air pour voler : sur Titan, épais et froid, il va loin ; sur Mars, l'air est si mince qu'il ne fait
 * que de courts sauts, comme Ingenuity ; sur la Lune et en orbite, il ne décolle pas. Chaque site nouveau rapporte des
 * points de recherche à l'agence de celui qui l'a envoyé (rien sur la planète, où il ne fait que des essais), et le
 * premier vol d'une agence hors de la planète une prime.
 */
public class RotorDroneEntity extends Entity {
    public static final String RESEARCH = "rotorcraft";

    /** Où en est la sortie. */
    public enum State {
        /** Posé : au repos, ou en charge. */
        LANDED,
        CLIMB_OUT,
        OUTBOUND,
        DESCEND_SITE,
        /** Posé sur le site, il l'étudie. */
        SURVEY,
        CLIMB_BACK,
        RETURN,
        LAND
    }

    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(RotorDroneEntity.class, EntityDataSerializers.INT);
    /** Vitesses de croisière et de montée, en blocs par tick ; durée de l'étude d'un site et de la recharge, en ticks. */
    private static final double SPEED = 0.45;
    private static final double CLIMB = 0.22;
    private static final int SURVEY_TICKS = 80;
    private static final int RECHARGE_TICKS = 600;
    /** Au-delà, une sortie qui s'éternise ramène le drone chez lui. */
    private static final int MAX_SORTIE_TICKS = 3000;
    /** Écart minimal entre deux sites pour que le second soit nouveau, en blocs ; marge au-dessus du relief en croisière. */
    private static final double SITE_SPACING = 32.0;
    private static final double CRUISE_MARGIN = 14.0;
    /** Points de recherche d'un site nouveau, sur Titan puis sur Mars ; prime du premier vol d'une agence. */
    public static final int TITAN_SITE_POINTS = 25;
    public static final int MARS_SITE_POINTS = 15;
    public static final int FIRST_FLIGHT_POINTS = 150;

    private Vec3 home = Vec3.ZERO;
    private Vec3 target = Vec3.ZERO;
    private double cruiseY;
    private int timer;
    private int sortieTicks;
    private int recharge;
    private UUID owner;
    private final List<Long> visited = new ArrayList<>();
    // Côté client : la rotation des rotors, qui s'emballent au décollage et ralentissent une fois posés.
    private float spin;
    private float spinO;
    private float spinSpeed;

    public RotorDroneEntity(EntityType<? extends RotorDroneEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STATE, State.LANDED.ordinal());
    }

    public State state() {
        return State.values()[Math.floorMod(entityData.get(STATE), State.values().length)];
    }

    private void setState(State state) {
        entityData.set(STATE, state.ordinal());
        timer = 0;
    }

    /** Vrai en vol (ou en train de se poser, ou de décoller). */
    public boolean isFlying() {
        State state = state();
        return state != State.LANDED && state != State.SURVEY;
    }

    /** Angle des rotors, en radians, pour l'image en cours. */
    public float rotorAngle(float partialTick) {
        return Mth.lerp(partialTick, spinO, spin);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            spinSpeed = Mth.lerp(0.06F, spinSpeed, isFlying() ? 1.15F : 0.0F);
            spinO = spin;
            spin += spinSpeed;
            if (isFlying() && random.nextInt(3) == 0) {
                dustBelow();
            }
            return;
        }
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        State state = state();
        if (state == State.LANDED) {
            recharge = Math.max(0, recharge - 1);
            // Posé : il reste soumis à la pesanteur si le sol disparaît sous lui.
            move(MoverType.SELF, new Vec3(0.0, -0.2, 0.0));
            return;
        }
        timer++;
        if (++sortieTicks > MAX_SORTIE_TICKS) {
            setPos(home);
            land(level);
            return;
        }
        switch (state) {
            case CLIMB_OUT -> {
                if (flyTo(new Vec3(home.x, cruiseY, home.z), CLIMB)) {
                    setState(State.OUTBOUND);
                }
            }
            case OUTBOUND -> {
                if (flyTo(new Vec3(target.x, cruiseY, target.z), SPEED)) {
                    setState(State.DESCEND_SITE);
                }
            }
            case DESCEND_SITE -> {
                if (flyTo(target, Math.clamp((getY() - target.y) * 0.06, 0.05, CLIMB))) {
                    setState(State.SURVEY);
                }
            }
            case SURVEY -> {
                if (timer >= SURVEY_TICKS) {
                    survey(level);
                    setState(State.CLIMB_BACK);
                }
            }
            case CLIMB_BACK -> {
                if (flyTo(new Vec3(target.x, cruiseY, target.z), CLIMB)) {
                    setState(State.RETURN);
                }
            }
            case RETURN -> {
                if (flyTo(new Vec3(home.x, cruiseY, home.z), SPEED)) {
                    setState(State.LAND);
                }
            }
            case LAND -> {
                if (flyTo(home, Math.clamp((getY() - home.y) * 0.06, 0.05, CLIMB))) {
                    land(level);
                }
            }
            default -> {
            }
        }
    }

    /** Avance vers ce point, à cette vitesse, en se tournant vers lui ; vrai une fois arrivé. */
    private boolean flyTo(Vec3 point, double speed) {
        Vec3 delta = point.subtract(position());
        double distance = delta.length();
        if (Math.hypot(delta.x, delta.z) > 0.05) {
            setYRot((float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F);
        }
        if (distance <= speed) {
            setPos(point);
            return true;
        }
        setPos(position().add(delta.scale(speed / distance)));
        return false;
    }

    /** Le souffle des rotors soulève la poussière du sol, quand il en est assez près. */
    private void dustBelow() {
        BlockPos ground = BlockPos.containing(getX(), getY() - 0.5, getZ());
        for (int down = 0; down < 6; down++) {
            var below = level().getBlockState(ground.below(down));
            if (!below.isAir()) {
                double angle = random.nextDouble() * Math.PI * 2.0;
                level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, below), getX() + Math.cos(angle) * 1.2,
                        ground.below(down).getY() + 1.1, getZ() + Math.sin(angle) * 1.2, Math.cos(angle) * 0.2, 0.05, Math.sin(angle) * 0.2);
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    // Une sortie
    // ------------------------------------------------------------------

    /** Portée d'une sortie dans ce monde, en blocs : 0 s'il n'y a pas d'air pour porter les rotors. */
    public static int range(Level level) {
        if (SpaceRules.isVacuum(level)) {
            return 0;
        }
        return SpaceRules.isTitan(level) ? 96 : SpaceRules.isMars(level) ? 40 : 48;
    }

    /**
     * Envoie le drone en sortie : il choisit un site à sa portée, de préférence encore jamais étudié, sur un sol ferme
     * (pas dans un lac), et une altitude de croisière au-dessus du relief qui l'en sépare.
     *
     * @return le message à donner au joueur
     */
    public Component launch(ServerLevel level, ServerPlayer player) {
        int range = range(level);
        if (range <= 0) {
            return Component.translatable("exploration.orbitalis.drone.no_air").withStyle(ChatFormatting.RED);
        }
        if (recharge > 0) {
            return Component.translatable("exploration.orbitalis.drone.charging", (recharge + 19) / 20).withStyle(ChatFormatting.GOLD);
        }
        Vec3 chosen = null;
        boolean fresh = false;
        for (int attempt = 0; attempt < 16 && !fresh; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = Mth.lerp(random.nextDouble(), Math.min(24.0, range * 0.6), range);
            int x = Mth.floor(getX() + Math.cos(angle) * distance);
            int z = Mth.floor(getZ() + Math.sin(angle) * distance);
            level.getChunk(x >> 4, z >> 4);
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (!level.getFluidState(new BlockPos(x, top - 1, z)).isEmpty() || top >= level.getMaxY() - 8) {
                continue;
            }
            Vec3 site = new Vec3(x + 0.5, top, z + 0.5);
            boolean isNew = isNewSite(site);
            if (chosen == null || isNew) {
                chosen = site;
                fresh = isNew;
            }
        }
        if (chosen == null) {
            return Component.translatable("exploration.orbitalis.drone.no_site").withStyle(ChatFormatting.GOLD);
        }
        home = position();
        target = chosen;
        // L'altitude de croisière : au-dessus du plus haut relief entre ici et le site.
        double highest = Math.max(home.y, target.y);
        for (int k = 1; k < 12; k++) {
            double t = k / 12.0;
            int x = Mth.floor(Mth.lerp(t, home.x, target.x));
            int z = Mth.floor(Mth.lerp(t, home.z, target.z));
            highest = Math.max(highest, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
        }
        cruiseY = Math.min(level.getMaxY() - 4.0, highest + CRUISE_MARGIN);
        owner = player.getUUID();
        sortieTicks = 0;
        setState(State.CLIMB_OUT);
        return Component.translatable("exploration.orbitalis.drone.launched", (int) Math.round(home.distanceTo(target)))
                .withStyle(ChatFormatting.AQUA);
    }

    private boolean isNewSite(Vec3 site) {
        for (long packed : visited) {
            if (Math.hypot(BlockPos.getX(packed) - site.x, BlockPos.getZ(packed) - site.z) < SITE_SPACING) {
                return false;
            }
        }
        return true;
    }

    /** Fin de l'étude du site : un site nouveau rapporte des points à l'agence de celui qui a envoyé le drone. */
    private void survey(ServerLevel level) {
        boolean fresh = isNewSite(target);
        if (fresh) {
            visited.add(BlockPos.asLong(Mth.floor(target.x), 0, Mth.floor(target.z)));
        }
        ServerPlayer player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        if (player == null) {
            return;
        }
        int points = !fresh ? 0 : SpaceRules.isTitan(level) ? TITAN_SITE_POINTS : SpaceRules.isMars(level) ? MARS_SITE_POINTS : 0;
        AgencyResearch research = ResearchManager.of(player);
        if (points > 0 && research.completeMilestone("first_drone_flight")) {
            points += FIRST_FLIGHT_POINTS;
            player.sendSystemMessage(Component.translatable("exploration.orbitalis.drone.first", FIRST_FLIGHT_POINTS).withStyle(ChatFormatting.AQUA));
        }
        if (points > 0) {
            research.addPoints(points);
            ResearchManager.changed(player);
        }
        String key = !fresh ? "exploration.orbitalis.drone.known_site" : points > 0 ? "exploration.orbitalis.drone.site" : "exploration.orbitalis.drone.test_site";
        player.sendSystemMessage(Component.translatable(key, points).withStyle(points > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY));
    }

    private void land(ServerLevel level) {
        setState(State.LANDED);
        recharge = RECHARGE_TICKS;
        ServerPlayer player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        if (player != null) {
            player.sendOverlayMessage(Component.translatable("exploration.orbitalis.drone.back").withStyle(ChatFormatting.AQUA));
        }
    }

    // ------------------------------------------------------------------
    // Le joueur
    // ------------------------------------------------------------------

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    /** Posé : un clic droit l'envoie en sortie ; accroupi, on le remet dans son inventaire. */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (!(level() instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (state() != State.LANDED) {
            serverPlayer.sendOverlayMessage(Component.translatable("exploration.orbitalis.drone.busy").withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive()) {
            pickUp(level, player);
            return InteractionResult.SUCCESS;
        }
        if (!player.getAbilities().instabuild && !ResearchManager.isUnlocked(player, RESEARCH)) {
            serverPlayer.sendOverlayMessage(Component.translatable("spaceport.orbitalis.research_required",
                    Component.translatable("research.orbitalis.node." + RESEARCH)).withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }
        serverPlayer.sendOverlayMessage(launch(level, serverPlayer));
        return InteractionResult.SUCCESS;
    }

    private void pickUp(ServerLevel level, Player player) {
        ItemStack drone = new ItemStack(ModItems.ROTOR_DRONE.get());
        if (!player.getAbilities().instabuild && !player.getInventory().add(drone)) {
            spawnAtLocation(level, drone);
        }
        discard();
    }

    /** Un coup de poing d'un joueur, au sol, le remet dans son inventaire ; rien d'autre ne l'abîme. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.getEntity() instanceof Player player && state() == State.LANDED) {
            pickUp(level, player);
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256.0 * 256.0;
    }

    // ------------------------------------------------------------------
    // Sauvegarde
    // ------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("state", state().ordinal());
        output.putDouble("home_x", home.x);
        output.putDouble("home_y", home.y);
        output.putDouble("home_z", home.z);
        output.putDouble("target_x", target.x);
        output.putDouble("target_y", target.y);
        output.putDouble("target_z", target.z);
        output.putDouble("cruise_y", cruiseY);
        output.putInt("sortie", sortieTicks);
        output.putInt("recharge", recharge);
        if (owner != null) {
            output.putString("owner", owner.toString());
        }
        int[] sites = new int[visited.size() * 2];
        for (int k = 0; k < visited.size(); k++) {
            sites[2 * k] = BlockPos.getX(visited.get(k));
            sites[2 * k + 1] = BlockPos.getZ(visited.get(k));
        }
        output.putIntArray("sites", sites);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        entityData.set(STATE, input.getIntOr("state", 0));
        home = new Vec3(input.getDoubleOr("home_x", getX()), input.getDoubleOr("home_y", getY()), input.getDoubleOr("home_z", getZ()));
        target = new Vec3(input.getDoubleOr("target_x", getX()), input.getDoubleOr("target_y", getY()), input.getDoubleOr("target_z", getZ()));
        cruiseY = input.getDoubleOr("cruise_y", getY());
        sortieTicks = input.getIntOr("sortie", 0);
        recharge = input.getIntOr("recharge", 0);
        String id = input.getStringOr("owner", "");
        owner = id.isEmpty() ? null : UUID.fromString(id);
        visited.clear();
        int[] sites = input.getIntArray("sites").orElse(new int[0]);
        for (int k = 0; k + 1 < sites.length; k += 2) {
            visited.add(BlockPos.asLong(sites[k], 0, sites[k + 1]));
        }
    }
}
