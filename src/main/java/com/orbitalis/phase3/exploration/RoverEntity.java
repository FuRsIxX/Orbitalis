package com.orbitalis.phase3.exploration;

import org.jspecify.annotations.Nullable;

import com.orbitalis.core.registry.ModItems;
import com.orbitalis.phase2.survival.SpaceRules;

import net.minecraft.ChatFormatting;
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
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Le rover : une jeep lunaire à quatre roues grillagées, deux sièges, son antenne parabolique et son coffre, comme le
 * rover des missions Apollo. On le pose au sol, on y monte d'un clic droit, on le conduit : W et S pour avancer et
 * reculer, A et D pour tourner. Il franchit un bloc de haut.
 * <p>
 * Il roule sur sa batterie, qui se vide en roulant et se recharge au soleil, dès qu'il voit le ciel. Il tient compte
 * de la pesanteur du monde où il roule. Un coup de poing le remet dans l'inventaire.
 */
public class RoverEntity extends LivingEntity {
    /** Charge de la batterie : cinq minutes de route, en ticks. */
    public static final int MAX_CHARGE = 6000;
    /** Ce que la batterie gagne par tick au soleil, à l'arrêt ou en roulant. */
    private static final int SOLAR_CHARGE = 1;
    /** Vitesse de rotation, en degrés par tick, la touche de direction enfoncée. */
    private static final float TURN = 4.0F;
    /** Pesanteur de base du jeu, que celle du monde module. */
    private static final double BASE_GRAVITY = 0.08;
    /** Rayon des roues, en blocs : elles tournent d'un radian chaque fois que le rover avance d'autant. */
    public static final float WHEEL_RADIUS = 0.27F;
    private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(RoverEntity.class, EntityDataSerializers.INT);

    // Animation, côté client seulement : l'angle des roues, leur braquage, l'assiette de la caisse, et le lacet
    // dessiné (le conducteur fixe celui du tick précédent, ce qui ôterait toute fluidité aux virages).
    private float wheelSpin;
    private float wheelSpinO;
    private float steer;
    private float steerO;
    private float pitch;
    private float pitchO;
    private float roll;
    private float rollO;
    private float renderYaw;
    private float renderYawO;
    private double lastSpeed;
    private boolean animated;

    public RoverEntity(EntityType<? extends RoverEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.STEP_HEIGHT, 1.0)
                .add(Attributes.SAFE_FALL_DISTANCE, 64.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CHARGE, MAX_CHARGE);
    }

    public int charge() {
        return entityData.get(CHARGE);
    }

    private void setCharge(int charge) {
        entityData.set(CHARGE, Mth.clamp(charge, 0, MAX_CHARGE));
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    // ------------------------------------------------------------------
    // Monter, conduire
    // ------------------------------------------------------------------

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (player.isSecondaryUseActive() || isVehicle()) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide()) {
            player.startRiding(this);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player driver ? driver : super.getControllingPassenger();
    }

    @Override
    protected void tickRidden(Player driver, Vec3 riddenInput) {
        super.tickRidden(driver, riddenInput);
        // Les touches de direction tournent le rover ; le conducteur regarde où il veut.
        float turning = driver.xxa;
        if (turning != 0.0F) {
            setYRot(getYRot() - turning * TURN * (driver.zza < 0.0F ? -1.0F : 1.0F));
        }
        yRotO = yBodyRot = yHeadRot = getYRot();
        if (!level().isClientSide() && driver.zza != 0.0F) {
            setCharge(charge() - 1);
        }
    }

    @Override
    protected Vec3 getRiddenInput(Player driver, Vec3 selfInput) {
        if (charge() <= 0) {
            return Vec3.ZERO;
        }
        float forward = driver.zza;
        return new Vec3(0.0, 0.0, forward < 0.0F ? forward * 0.5F : forward);
    }

    @Override
    protected float getRiddenSpeed(Player driver) {
        return (float) getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    /** Le conducteur s'assoit sur le siège de gauche, au-dessus du plancher. */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return new Vec3(0.28, 0.37, 0.05).yRot(-getYRot() * Mth.DEG_TO_RAD);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            animate();
        }
        if (level() instanceof ServerLevel serverLevel) {
            // La pesanteur du monde où il roule : un sixième de la nôtre sur la Lune, un peu plus d'un tiers sur Mars.
            AttributeInstance gravity = getAttribute(Attributes.GRAVITY);
            double wanted = BASE_GRAVITY * SpaceRules.gravityOf(serverLevel);
            if (gravity != null && Math.abs(gravity.getBaseValue() - wanted) > 1.0E-6) {
                gravity.setBaseValue(wanted);
            }
            // Les panneaux solaires rechargent la batterie, tant qu'il voit le ciel.
            if (charge() < MAX_CHARGE && serverLevel.canSeeSky(blockPosition().above())) {
                setCharge(charge() + SOLAR_CHARGE);
            }
            // Le conducteur voit sa batterie.
            if (tickCount % 10 == 0 && getControllingPassenger() instanceof ServerPlayer driver) {
                int percent = Math.round(100.0F * charge() / MAX_CHARGE);
                driver.sendOverlayMessage(Component.translatable(charge() > 0 ? "exploration.orbitalis.rover.battery" : "exploration.orbitalis.rover.empty",
                        percent).withStyle(charge() > MAX_CHARGE / 5 ? ChatFormatting.AQUA : ChatFormatting.GOLD));
            }
        }
    }

    // ------------------------------------------------------------------
    // Animation
    // ------------------------------------------------------------------

    /**
     * Fait vivre le rover d'après son mouvement du tick : ses roues tournent de la distance parcourue, braquent dans
     * les virages (l'avant d'un côté, l'arrière un peu de l'autre, comme le LRV), la caisse se cabre à l'accélération,
     * plonge au freinage, penche dans les virages, et les roues arrière soulèvent le sol.
     */
    private void animate() {
        wheelSpinO = wheelSpin;
        steerO = steer;
        pitchO = pitch;
        rollO = roll;
        renderYawO = renderYaw;
        float yaw = getYRot();
        float turn = animated ? Mth.wrapDegrees(yaw - renderYaw) : 0.0F;
        if (!animated) {
            renderYawO = yaw;
            animated = true;
        }
        renderYaw = yaw;

        float yawRad = yaw * Mth.DEG_TO_RAD;
        double forwardX = -Mth.sin(yawRad);
        double forwardZ = Mth.cos(yawRad);
        double speed = (getX() - xo) * forwardX + (getZ() - zo) * forwardZ;
        wheelSpin += (float) (speed / WHEEL_RADIUS);

        // En marche arrière, le volant braque à l'inverse du sens où tourne la caisse.
        float direction = speed < -0.005 ? -1.0F : 1.0F;
        steer += (Mth.clamp(turn * 6.0F * direction, -26.0F, 26.0F) - steer) * 0.35F;
        double accel = speed - lastSpeed;
        lastSpeed = speed;
        pitch += (Mth.clamp((float) (accel * 90.0 + (getY() - yo) * 25.0), -5.0F, 5.0F) - pitch) * 0.2F;
        roll += (Mth.clamp(turn * (float) speed * 7.0F, -4.0F, 4.0F) - roll) * 0.2F;

        // La poussière des roues arrière, d'autant plus qu'il va vite.
        if (onGround() && Math.abs(speed) > 0.03 && random.nextFloat() < Math.min(1.0, Math.abs(speed) * 6.0)) {
            BlockState ground = level().getBlockState(blockPosition().below());
            if (!ground.isAir()) {
                for (int side = -1; side <= 1; side += 2) {
                    double x = getX() - forwardX * 0.55 - forwardZ * 0.77 * side;
                    double z = getZ() - forwardZ * 0.55 + forwardX * 0.77 * side;
                    level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, getY() + 0.05, z,
                            -forwardX * speed * 0.6, 0.04 + random.nextDouble() * 0.06, -forwardZ * speed * 0.6);
                }
            }
        }
    }

    /** Outil des captures d'écran : fige la pose du rover (roues, braquage, caisse), côté client. */
    public void previewPose(float spin, float steering, float bodyPitch, float bodyRoll) {
        wheelSpinO = wheelSpin = spin;
        steerO = steer = steering;
        pitchO = pitch = bodyPitch;
        rollO = roll = bodyRoll;
    }

    /** Angle des roues, en radians. */
    public float wheelSpin(float partialTicks) {
        return Mth.lerp(partialTicks, wheelSpinO, wheelSpin);
    }

    /** Braquage des roues avant, en degrés (positif : vers la droite). */
    public float steer(float partialTicks) {
        return Mth.lerp(partialTicks, steerO, steer);
    }

    /** Assiette de la caisse, en degrés : cabrée (positif) ou plongeante. */
    public float bodyPitch(float partialTicks) {
        return Mth.lerp(partialTicks, pitchO, pitch);
    }

    /** Roulis de la caisse, en degrés. */
    public float bodyRoll(float partialTicks) {
        return Mth.lerp(partialTicks, rollO, roll);
    }

    /** Lacet dessiné, fluide même sous la main du conducteur. */
    public float renderYaw(float partialTicks) {
        return animated ? Mth.rotLerp(partialTicks, renderYawO, renderYaw) : getYRot();
    }

    // ------------------------------------------------------------------
    // Le reprendre
    // ------------------------------------------------------------------

    /** Un coup de poing d'un joueur remet le rover dans son inventaire ; rien d'autre ne l'abîme. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.getEntity() instanceof Player player && !isVehicle()) {
            ItemStack rover = new ItemStack(ModItems.ROVER.get());
            if (!player.getAbilities().instabuild && !player.getInventory().add(rover)) {
                spawnAtLocation(level, rover);
            }
            discard();
            return true;
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("charge", charge());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setCharge(input.getIntOr("charge", MAX_CHARGE));
    }
}
