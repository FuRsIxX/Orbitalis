package com.orbitalis.phase2.flight;

import java.util.Locale;
import java.util.Optional;

import com.orbitalis.core.network.PilotInputPayload;
import com.orbitalis.core.registry.ModItems;
import com.orbitalis.core.registry.ModParticles;
import com.orbitalis.core.research.AgencyResearch;
import com.orbitalis.core.research.ResearchManager;
import com.orbitalis.phase1.rocket.RocketStats;
import com.orbitalis.phase1.spaceport.LaunchComplex;
import com.orbitalis.phase1.spaceport.LaunchTableBlockEntity;
import com.orbitalis.phase2.station.StationManager;
import com.orbitalis.phase2.survival.SpaceRules;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Capsule habitée : la cabine dans laquelle un joueur monte à bord d'une fusée, vit le vol, puis revient au sol.
 * <p>
 * Tant que la fusée est sur son pas de tir, la capsule est invisible, logée sous la coiffe : c'est la fusée
 * qu'on voit. Au décollage elle suit la fusée, avec son passager. Une fois la poussée terminée, elle continue
 * seule : un vol balistique ou quelques tours en orbite selon ce que le lanceur permet, puis la rentrée
 * atmosphérique, l'ouverture des parachutes et le retour au sol. Elle se récupère une fois posée.
 * <p>
 * Si le lanceur échoue à l'allumage, la tour de sauvetage arrache la capsule à la fusée : elle retombe
 * sous ses parachutes, équipage sauf.
 */
public class CapsuleEntity extends Entity {
    /** Étapes du vol d'une capsule. */
    public enum Phase {
        /** Sur le pas de tir, sous la coiffe de la fusée. */
        PAD,
        /** Montée avec la fusée. */
        ASCENT,
        /** Vol libre au sommet de la trajectoire : apogée d'un vol suborbital, ou tours d'orbite. */
        COAST,
        /** Rentrée atmosphérique : chute rapide, bouclier thermique en avant. */
        REENTRY,
        /** Descente sous parachutes. */
        CHUTE,
        /** Posée au sol ou amerrie. */
        LANDED,
        /** Éjection par la tour de sauvetage après un échec du lanceur. */
        ABORT,
        /** En orbite, dans l'espace : l'équipage peut sortir, puis commander le retour. */
        ORBIT,
        /** Descente propulsée vers la surface de la Lune. */
        LUNAR_DESCENT,
        /** Décollage de la Lune, vers l'orbite de la planète. */
        LUNAR_ASCENT,
        /** Entrée dans l'atmosphère de Mars : son air, mince, freine le vaisseau sans le poser. */
        MARS_ENTRY,
        /** Descente propulsée, pilotée à la main : le moteur contre la pesanteur, jusqu'au sol. */
        POWERED_DESCENT;

        public String messageKey() {
            return "capsule.orbitalis.phase." + name().toLowerCase(Locale.ROOT);
        }
    }

    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<BlockPos>> TABLE =
            SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    /** Hauteur de la capsule sur sa fusée au repos : la montée s'y ajoute. */
    private static final EntityDataAccessor<Float> BASE_Y = SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.FLOAT);
    /** Orientation de la fusée pendant la montée : tous les clients la dessinent penchée d'autant, cabine comprise. */
    private static final EntityDataAccessor<org.joml.Quaternionfc> ATTITUDE =
            SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.QUATERNION);
    /** Plan de la fusée que suit la capsule : elle la dessine elle-même pendant la montée, étage par étage. */
    private static final EntityDataAccessor<ItemStack> ROCKET = SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.ITEM_STACK);
    /** Vrai tant que la capsule monte avec sa fusée, et nombre d'étages déjà largués. */
    private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> STAGES = SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.INT);
    /**
     * Hauteur du point d'assise du pilote dans la cabine, et celle de ses yeux : la fusée tourne autour de sa tête.
     * Il est assis droit, face au nord, les yeux à la hauteur du pare-brise.
     */
    public static final double SEAT_HEIGHT = 0.105;
    public static final double EYE_HEIGHT = SEAT_HEIGHT - 0.6 + 1.62;
    /** Recul du siège du pilote par rapport à l'axe de la cabine : il est assis un peu en arrière, face à ses écrans. */
    public static final double SEAT_BACK = 1.5 / 16.0;
    /** Nombre de places : le pilote devant, deux équipiers derrière lui. */
    public static final int SEATS = 3;
    /** Place de chaque équipier par rapport au pilote : de côté (vers l'est) et en arrière (vers le sud), en blocs. */
    private static final double CREW_SIDE = 6.5 / 16.0;
    private static final double CREW_BACK = 5.5 / 16.0;

    /** Durée d'un tour d'orbite et nombre de tours d'un vol orbital, en ticks ; durée de l'apogée d'un vol suborbital. */
    private static final int ORBIT_TICKS = 200;
    private static final int ORBIT_COUNT = 2;
    private static final int APOGEE_TICKS = 100;
    /** Vitesse de chute pendant la rentrée, puis sous parachutes (une fois freinée), en blocs par tick. */
    private static final double REENTRY_SPEED = 4.0;
    private static final double CHUTE_SPEED = 0.42;
    /**
     * Les parachutes ne freinent pas d'un coup : la voile met un peu plus d'une seconde à se gonfler, et freine de plus
     * en plus fort à mesure ; la capsule perd sa vitesse en deux secondes environ. Part de l'écart à la vitesse sous
     * voile que l'air reprend à chaque tick, voile pliée puis gonflée.
     */
    public static final int CHUTE_INFLATE_TICKS = 25;
    private static final double CHUTE_DRAG_FOLDED = 0.02;
    private static final double CHUTE_DRAG_OPEN = 0.12;
    /** Hauteur au-dessus du sol à laquelle les parachutes s'ouvrent, en blocs. */
    private static final int CHUTE_HEIGHT = 140;
    /** Hauteur sous laquelle on peut armer les parachutes : plus haut, la capsule va trop vite, ils se déchireraient. */
    public static final int CHUTE_WINDOW = 450;
    /** Vitesse de chute au ras du sol, sans parachute, en blocs par tick : l'air a freiné la capsule, pas assez. */
    private static final double CRASH_SPEED = 1.4;
    /** Vrai une fois les parachutes armés ; vrai si c'est au pilote de le faire. */
    private static final EntityDataAccessor<Boolean> CHUTE_ARMED = SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> MANUAL_CHUTE = SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.BOOLEAN);
    /** Durée de la poussée de la tour de sauvetage, en ticks, et sa vitesse, en blocs par tick. */
    private static final int ABORT_TICKS = 40;
    private static final double ABORT_SPEED = 1.2;
    /** Primes de recherche du premier vol habité suborbital, puis orbital, d'une agence. */
    private static final int REWARD_SUBORBITAL = 150;
    private static final int REWARD_ORBITAL = 300;

    /** Durée pendant laquelle il faut maintenir la touche de saut pour quitter l'orbite, en ticks. */
    private static final int DEORBIT_HOLD_TICKS = 40;
    /** Hauteur à laquelle la capsule réapparaît au-dessus de la planète pour sa rentrée. */
    private static final double REENTRY_HEIGHT = 900.0;

    private int timer;
    /**
     * Vitesse de chute pendant la rentrée, en blocs par tick. Au retour de l'espace, la capsule arrive lancée ; après
     * une éjection par la tour de sauvetage, elle part de l'arrêt et prend de la vitesse en tombant.
     */
    private double fallSpeed = REENTRY_SPEED;
    /** Vitesse de chute réelle au tick précédent de la rentrée, puis sous parachutes, en blocs par tick. */
    private double sinkSpeed;
    /** Ticks depuis l'ouverture des parachutes (des deux côtés : le client s'en sert pour gonfler la voile). */
    private int chuteTicks;
    /** Ce que la pesanteur ajoute à la vitesse de chute à chaque tick. */
    private static final double FALL_GAIN = 0.07;
    /** Point de départ du vol : la capsule revient au-dessus de son pas de tir. */
    private double originX;
    private double originZ;
    /** Station à laquelle la capsule est amarrée (centre de son module central), ou {@code null}. */
    private BlockPos station;
    /** Orientation de l'équipage à l'embarquement : assis face au nord, le regard droit devant, vers le pare-brise et l'écran de bord. */
    private static final float PILOT_YAW = 180.0F;
    private static final float PILOT_PITCH = 0.0F;
    /** Durée pendant laquelle il faut maintenir la touche de saut pour lancer le compte à rebours, en ticks. */
    private static final int LAUNCH_HOLD_TICKS = 60;
    /** Temps depuis lequel le passager maintient la commande de lancement, en ticks. */
    private int launchHold;
    /** Vrai quand la touche de saut a été relâchée depuis la dernière commande : la suivante peut être donnée. */
    private boolean commandArmed = true;
    /** Commandes que tient le pilote (voir {@code PilotInputPayload}), et ticks écoulés depuis qu'il les a confirmées. */
    private int controls;
    private int controlsAge;
    /** Côté client : orientation dessinée ce tick et au précédent. Elle rejoint en douceur celle qu'envoie le serveur. */
    private final org.joml.Quaternionf shownAttitude = new org.joml.Quaternionf();
    private final org.joml.Quaternionf lastAttitude = new org.joml.Quaternionf();
    /** Durée du désamarrage, en ticks, dont le temps d'arrêt, verrous ouverts, avant que la capsule ne recule. */
    private static final int UNDOCK_TICKS = 100;
    private static final int UNDOCK_PAUSE_TICKS = 20;
    /** Avancement du désamarrage, en ticks ; 0 quand la capsule ne se désamarre pas. */
    private int departing;
    /** Vrai une fois la capsule au contact du sas de sa station. */
    private boolean docked;
    /** Distance et hauteur, par rapport au sas, auxquelles la capsule arrive en orbite pour son approche, en blocs. */
    private static final double APPROACH_DISTANCE = 28.0;
    private static final double APPROACH_HEIGHT = 5.0;
    /** Temps depuis lequel le passager maintient la commande de retour, en ticks. */
    private int deorbitHold;
    /** Ce que le lanceur permettait d'atteindre (rang dans {@link RocketStats.Reach}). */
    private int reach;
    /** Dérive horizontale pendant la descente, en blocs par tick : la capsule ne retombe pas sur son pas de tir. */
    private double driftX;
    private double driftZ;
    /** Vrai tant que la capsule suit sa fusée dans la montée. */
    private boolean followingRocket;
    /** Côté client : lisse les déplacements envoyés par le serveur (descente, parachutes). */
    private final InterpolationHandler interpolation = new InterpolationHandler(this, 2);
    /** Vrai pour un vaisseau lunaire : la capsule est portée par un étage d'alunissage. */
    private static final EntityDataAccessor<Boolean> LUNAR = SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.BOOLEAN);
    /** Modèle de la cabine : 0 la Mk1 « Éclaireur », 1 la Mk2 « Horizon », 2 la Mk3 « Odyssée ». */
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.INT);
    /** Hauteur à laquelle le vaisseau arrive au-dessus de la Lune, et hauteur de son décollage avant le retour. */
    private static final double MOON_ARRIVAL_HEIGHT = 300.0;
    private static final int MOON_ASCENT_TICKS = 120;
    /** Écart, en blocs, entre un vaisseau lunaire de passage en orbite et la verticale du pas de tir, où peut se trouver une station. */
    private static final double FREE_ORBIT_OFFSET = 48.0;
    /** Prime de recherche du premier alunissage d'une agence. */
    private static final int REWARD_MOON_LANDING = 500;
    /** Vrai pour un vaisseau martien ; vrai tant que son moteur de descente brûle ; part de ses ergols qu'il lui reste. */
    private static final EntityDataAccessor<Boolean> MARTIAN = SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.BOOLEAN);
    /** Pendant la montée : moteurs coupés, entre deux étages. */
    private static final EntityDataAccessor<Boolean> COASTING = SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> BURNING = SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.BOOLEAN);
    /**
     * La manœuvre préparée en orbite (voir {@link #NODE_NONE} et suivants), le temps écoulé depuis qu'elle l'a été, en
     * ticks, et le delta-v déjà accompli par sa poussée, en m/s : l'affichage tête haute les montre au pilote.
     */
    private static final EntityDataAccessor<Integer> NODE = SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> NODE_TIMER = SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> NODE_DONE = SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.FLOAT);
    /** Les manœuvres : aucune, le freinage de retour, puis les départs vers la Lune, Mars, Titan et Europe. */
    public static final int NODE_NONE = 0;
    public static final int NODE_DEORBIT = 1;
    public static final int NODE_MOON = 2;
    public static final int NODE_MARS = 3;
    public static final int NODE_TITAN = 4;
    public static final int NODE_EUROPA = 5;
    /** Delta-v de chaque manœuvre, en m/s. */
    private static final float[] NODE_DELTA_V = {0.0F, 120.0F, 3150.0F, 3600.0F, 7600.0F, 6300.0F};
    /** Durée de la poussée de chaque manœuvre, en ticks. */
    private static final int[] NODE_BURN = {0, 60, 200, 240, 300, 300};
    /** Attente de la fenêtre d'allumage, en ticks : le freinage est presque immédiat, un départ attend son alignement. */
    private static final int[] NODE_LEAD = {0, 100, 400, 400, 400, 400};
    /** Temps laissé pour allumer une fois la fenêtre ouverte, et pour finir la poussée au-delà de sa durée, en ticks. */
    public static final int NODE_WINDOW = 120;
    private static final int NODE_SLACK = 160;
    /** Part du delta-v à atteindre au moins, et au-delà de laquelle l'ordinateur de bord coupe lui-même. */
    public static final float NODE_MIN = 0.95F;
    public static final float NODE_MAX = 1.10F;
    private static final EntityDataAccessor<Float> FUEL = SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.FLOAT);
    /** Hauteur à laquelle le vaisseau arrive au-dessus de Mars, puis hauteur à laquelle le pilote prend la main, en blocs. */
    private static final double MARS_ARRIVAL_HEIGHT = 420.0;
    private static final double HANDOVER_HEIGHT = 170.0;
    /** Vitesse de chute au début de l'entrée, puis au moment où le pilote prend la main, en blocs par tick. */
    private static final double ENTRY_SPEED = 3.0;
    private static final double HANDOVER_SPEED = 0.9;
    /**
     * Descente propulsée : pesanteur de Mars et poussée du moteur, en blocs par tick et par tick. La poussée vaut un
     * peu plus de deux fois le poids du vaisseau : moteur allumé, il freine franchement ; moteur coupé, il tombe.
     */
    private static final double DESCENT_GRAVITY = 0.0045;
    private static final double DESCENT_THRUST = 0.0105;
    /** Ergols de la descente, en ticks de poussée : à peu près le double de ce que demande un atterrissage propre. */
    public static final int DESCENT_FUEL = 600;
    /** Contact sûr : vitesse de descente et vitesse de côté maximales, en blocs par tick, et inclinaison maximale, en degrés. */
    public static final double SAFE_SINK = 0.3;
    public static final double SAFE_SLIDE = 0.25;
    public static final double SAFE_TILT = 25.0;
    /** Prime de recherche du premier atterrissage sur Mars d'une agence. */
    private static final int REWARD_MARS_LANDING = 900;
    /** Vrai pour un vaisseau titanien. */
    private static final EntityDataAccessor<Boolean> TITANIAN = SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.BOOLEAN);
    /** Vrai pour un vaisseau d'Europe. */
    private static final EntityDataAccessor<Boolean> EUROPAN = SynchedEntityData.defineId(CapsuleEntity.class, EntityDataSerializers.BOOLEAN);
    /** Prime de recherche du premier atterrissage sur Europe d'une agence. */
    private static final int REWARD_EUROPA_LANDING = 1500;
    /** Hauteur à laquelle le vaisseau arrive au-dessus de Titan, pour sa rentrée. */
    private static final double TITAN_ARRIVAL_HEIGHT = 420.0;
    /** Prime de recherche du premier atterrissage sur Titan d'une agence. */
    private static final int REWARD_TITAN_LANDING = 1200;
    /** Descente propulsée : orientation du vaisseau, sa vitesse, et ses ergols restants. */
    private final com.orbitalis.phase1.spaceport.RocketAttitude lander = new com.orbitalis.phase1.spaceport.RocketAttitude();
    private Vec3 descentVelocity = Vec3.ZERO;
    private double descentFuel = DESCENT_FUEL;
    /** Temps depuis lequel le passager maintient la commande de départ vers la Lune, en ticks. */
    private int lunarHold;
    /** Vrai quand les touches de départ et de retour ont été relâchées : le prochain appui prépare ou annule une manœuvre. */
    private boolean nodeArmed = true;

    public CapsuleEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        double angle = random.nextDouble() * Math.PI * 2.0;
        driftX = Math.cos(angle);
        driftZ = Math.sin(angle);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(PHASE, Phase.PAD.ordinal());
        builder.define(TABLE, Optional.empty());
        builder.define(BASE_Y, 0.0F);
        builder.define(ATTITUDE, new org.joml.Quaternionf());
        builder.define(LUNAR, false);
        builder.define(VARIANT, 0);
        builder.define(MARTIAN, false);
        builder.define(TITANIAN, false);
        builder.define(EUROPAN, false);
        builder.define(BURNING, false);
        builder.define(NODE, NODE_NONE);
        builder.define(NODE_TIMER, 0);
        builder.define(NODE_DONE, 0.0F);
        builder.define(COASTING, false);
        builder.define(FUEL, 1.0F);
        builder.define(CHUTE_ARMED, false);
        builder.define(ROCKET, ItemStack.EMPTY);
        builder.define(FLYING, false);
        builder.define(STAGES, 0);
        builder.define(MANUAL_CHUTE, false);
    }

    // ------------------------------------------------------------------
    // État
    // ------------------------------------------------------------------

    public Phase getPhase() {
        Phase[] phases = Phase.values();
        return phases[Math.floorMod(entityData.get(PHASE), phases.length)];
    }

    private void setPhase(Phase phase) {
        entityData.set(PHASE, phase.ordinal());
        timer = 0;
        // La capsule ne se heurte au monde que sous parachutes et une fois posée.
        noPhysics = phase != Phase.CHUTE && phase != Phase.LANDED;
    }

    /** Table de lancement de la fusée qui porte la capsule. */
    public BlockPos getTable() {
        return entityData.get(TABLE).orElse(null);
    }

    /** Installe la capsule au sommet de la fusée posée sur cette table. */
    public void attachTo(BlockPos table, Vec3 position) {
        entityData.set(TABLE, Optional.of(table.immutable()));
        entityData.set(BASE_Y, (float) position.y);
        setPos(position);
    }

    /** Place directement la capsule en orbite, sans vol (outil de capture). */
    public void parkInOrbit() {
        originX = getX();
        originZ = getZ();
        setPhase(Phase.ORBIT);
    }

    /** Supprime la dérive de la descente (tests automatiques). */
    public void setDrift(double x, double z) {
        driftX = x;
        driftZ = z;
    }

    /** Vrai tant que la capsule monte avec sa fusée : elle la dessine alors autour d'elle. */
    public boolean isFollowingRocket() {
        return level().isClientSide() ? entityData.get(FLYING) : followingRocket;
    }

    public void setRocket(ItemStack rocket) {
        entityData.set(ROCKET, rocket);
    }

    /** Vrai si la fusée que suit la capsule a ses moteurs coupés, entre deux étages. */
    public boolean isCoasting() {
        return entityData.get(COASTING);
    }

    /** Étages de la fusée déjà largués, tels que les clients les voient. */
    public int droppedStages() {
        return entityData.get(STAGES);
    }

    /** Côté client : les capsules qui montent avec leur fusée, par table de lancement. Le grondement des moteurs les suit. */
    private static final java.util.Map<BlockPos, java.lang.ref.WeakReference<CapsuleEntity>> CLIENT_FLYING = new java.util.HashMap<>();

    /** Côté client : la capsule qui monte avec la fusée partie de cette table, ou {@code null}. */
    public static CapsuleEntity flyingFrom(BlockPos table) {
        java.lang.ref.WeakReference<CapsuleEntity> reference = CLIENT_FLYING.get(table);
        CapsuleEntity capsule = reference == null ? null : reference.get();
        return capsule != null && !capsule.isRemoved() && capsule.isFollowingRocket() ? capsule : null;
    }

    /** Côté client : silhouette de la fusée que suit la capsule, ou {@code null}. */
    public com.orbitalis.phase1.spaceport.RocketShape flightShape() {
        ItemStack stack = entityData.get(ROCKET);
        if (stack.isEmpty()) {
            return null;
        }
        if (stack != shapeSource) {
            shapeSource = stack;
            shape = com.orbitalis.phase1.spaceport.RocketShape.of(com.orbitalis.phase1.rocket.RocketItem.design(stack));
        }
        return shape;
    }

    /**
     * Côté client : les caractéristiques de la fusée que suit la capsule, ou {@code null}. Elles servent à l'écran du
     * pilote, qui ne peut compter sur le pas de tir : la fusée s'en éloigne parfois au-delà de ce que le joueur charge.
     */
    public RocketStats flightStats() {
        ItemStack stack = entityData.get(ROCKET);
        if (stack.isEmpty()) {
            return null;
        }
        if (stack != statsSource) {
            statsSource = stack;
            stats = RocketStats.of(com.orbitalis.phase1.rocket.RocketItem.design(stack));
        }
        return stats;
    }

    private ItemStack shapeSource = ItemStack.EMPTY;
    private com.orbitalis.phase1.spaceport.RocketShape shape;
    private ItemStack statsSource = ItemStack.EMPTY;
    private RocketStats stats;
    /** Côté client : étages dont on a vu le largage, et l'instant de chacun. */
    private int seenStages;
    private final int[] stageDroppedAt = new int[16];

    /** Rang du premier tronçon d'un étage (0 pour le premier) : celui qui suit son interétage. */
    public static int stageStart(java.util.List<com.orbitalis.phase1.spaceport.RocketBlock.Part> parts, int stage) {
        int start = 0;
        for (int i = 0; i < parts.size() && stage > 0; i++) {
            if (parts.get(i) == com.orbitalis.phase1.spaceport.RocketBlock.Part.INTERSTAGE) {
                start = i + 1;
                stage--;
            }
        }
        return start;
    }

    /** Côté client : temps écoulé depuis le largage d'un étage, en ticks, ou -1 s'il est encore attaché. */
    public float stageAge(int stage, float partialTick) {
        return stage < seenStages ? tickCount - stageDroppedAt[stage] + partialTick : -1.0F;
    }

    /**
     * Côté client, pendant la montée : on note les étages qui viennent d'être largués, et les moteurs de l'étage
     * allumé crachent leurs flammes et leur fumée dans l'axe de la fusée, là où elle se trouve.
     */
    private void clientFlight() {
        com.orbitalis.phase1.spaceport.RocketShape flying = flightShape();
        if (getTable() != null && flyingFrom(getTable()) != this) {
            CLIENT_FLYING.put(getTable(), new java.lang.ref.WeakReference<>(this));
        }
        int dropped = entityData.get(STAGES);
        while (seenStages < dropped && seenStages < stageDroppedAt.length) {
            stageDroppedAt[seenStages++] = tickCount;
            // Les boulons de l'étage sautent : un claquement sec dans toute la structure.
            level().playLocalSound(getX(), getY(), getZ(), com.orbitalis.core.registry.ModSounds.STAGE_SEPARATION.get(),
                    net.minecraft.sounds.SoundSource.BLOCKS, 12.0F, 1.0F, false);
        }
        // Entre deux étages, moteurs coupés : plus de sillage.
        if (flying == null || isCoasting()) {
            return;
        }
        java.util.List<com.orbitalis.phase1.spaceport.RocketBlock.Part> parts = flying.parts();
        int cabin = Math.max(0, com.orbitalis.phase1.spaceport.RocketShape.cabinIndex(parts));
        int foot = stageStart(parts, dropped);
        org.joml.Vector3f pivot = new org.joml.Vector3f(0.0F, (float) EYE_HEIGHT, (float) SEAT_BACK);
        org.joml.Vector3f nozzle = shownAttitude.transform(new org.joml.Vector3f(0.0F, foot - cabin, 0.0F).sub(pivot)).add(pivot);
        org.joml.Vector3f exhaust = shownAttitude.transform(new org.joml.Vector3f(0.0F, -1.0F, 0.0F));
        double nx = getX() + nozzle.x;
        double ny = getY() + nozzle.y;
        double nz = getZ() + nozzle.z;
        float half = flying.isWide() ? 1.5F : 0.5F;
        double density = com.orbitalis.core.config.ClientConfig.PARTICLE_DENSITY.get();
        // La flamme elle-même est dessinée avec la fusée, attachée à ses tuyères (voir CapsuleRenderer) : elle ne
        // peut ni traîner ni se décaler. Ici ne naît que la fumée du sillage, qui, elle, reste dans le monde : posée
        // tout le long du chemin parcouru pendant ce tick, au bout du dard, elle dessine la trajectoire suivie.
        double mx = getX() - xo;
        double my = getY() - yo;
        double mz = getZ() - zo;
        double reach = flying.isWide() ? 10.0 : 5.0;
        int trail = (int) Math.ceil((3 + half * 6) * density);
        for (int i = 0; i < trail; i++) {
            double back = random.nextDouble();
            double behind = reach + random.nextDouble() * 2.0;
            double push = 0.12 + random.nextDouble() * 0.2;
            level().addAlwaysVisibleParticle(ModParticles.EXHAUST_SMOKE.get(), true,
                    nx - mx * back + (random.nextDouble() - 0.5) * 2.0 * half + exhaust.x * behind,
                    ny - my * back + exhaust.y * behind,
                    nz - mz * back + (random.nextDouble() - 0.5) * 2.0 * half + exhaust.z * behind,
                    mx * 0.15 + exhaust.x * push, my * 0.15 + exhaust.y * push, mz * 0.15 + exhaust.z * push);
        }
    }

    /**
     * Côté client, cabine seule : ce qui brûle autour d'elle. Les particules naissent là où la cabine est dessinée, et
     * partent avec sa vitesse : elles la suivent, puis restent en arrière.
     * <ul>
     * <li>rentrée : le plasma, en couronne autour du bouclier, qui file vers le haut le long des vitres ;</li>
     * <li>éjection : les moteurs de la tour de sauvetage, au-dessus de la cabine ;</li>
     * <li>alunissage et décollage de la Lune : le moteur du vaisseau, sous lui, qui souffle vers le bas.</li>
     * </ul>
     */
    private void clientExhaust() {
        Phase phase = getPhase();
        double mx = getX() - xo;
        double my = getY() - yo;
        double mz = getZ() - zo;
        // Un saut (changement de monde, retour de l'orbite) n'est pas un déplacement : rien à poser le long.
        if (mx * mx + my * my + mz * mz > 64.0 * 64.0) {
            return;
        }
        double density = com.orbitalis.core.config.ClientConfig.PARTICLE_DENSITY.get();
        if (phase == Phase.REENTRY && my < -1.5) {
            // Le plasma lui-même est dessiné avec la cabine (voir CapsuleRenderer). Ici, la traînée qu'elle laisse :
            // des braises posées le long du chemin de ce tick, qui restent où elles sont et s'éteignent.
            int count = (int) Math.ceil(5 * density);
            for (int i = 0; i < count; i++) {
                double back = random.nextDouble();
                double angle = random.nextDouble() * Math.PI * 2.0;
                // Hors de la coque (elle fait un peu moins d'un bloc de rayon), et au-dessus du toit : aucune braise
                // ne naît dans la cabine.
                double radius = 1.5 + random.nextDouble() * 0.7;
                level().addAlwaysVisibleParticle(ModParticles.ENGINE_FLAME.get(), true, getX() - mx * back + Math.cos(angle) * radius,
                        getY() - my * back + 3.0, getZ() - mz * back + Math.sin(angle) * radius, 0.0, 0.0, 0.0);
            }
        } else if (phase == Phase.ABORT) {
            // Les moteurs de la tour de sauvetage laissent derrière eux une fumée, le long du chemin parcouru.
            int count = (int) Math.ceil(2 * density);
            for (int i = 0; i < count; i++) {
                double back = random.nextDouble();
                level().addAlwaysVisibleParticle(ModParticles.EXHAUST_SMOKE.get(), true, getX() - mx * back, getY() - my * back - 0.5, getZ() - mz * back,
                        0.0, -0.05, 0.0);
            }
        }
    }

    /** Hauteur de la capsule sur sa fusée au repos. */
    public double baseHeight() {
        return entityData.get(BASE_Y);
    }

    /** Vrai si l'équipage ne peut pas quitter la capsule : en vol, on ne descend pas. */
    public boolean locksCrew() {
        Phase phase = getPhase();
        // En orbite, on peut sortir, sauf pendant l'approche d'une station : on attend le contact.
        if (phase == Phase.ORBIT) {
            return station != null && (!docked || departing > 0);
        }
        return phase != Phase.PAD && phase != Phase.LANDED;
    }

    /** Vrai si la capsule doit être dessinée : sur sa fusée, c'est la coiffe qu'on voit. */
    public boolean isExposed() {
        Phase phase = getPhase();
        if (phase == Phase.ASCENT) {
            // En montée, la capsule fait corps avec sa fusée, qu'elle dessine tout entière.
            return !isFollowingRocket();
        }
        return phase != Phase.PAD;
    }

    @Override
    public InterpolationHandler getInterpolation() {
        return interpolation;
    }

    /** Vrai pour un vaisseau lunaire. */
    /** Modèle de la cabine : 0 la Mk1, 1 la Mk2, 2 la Mk3. */
    public int getVariant() {
        return entityData.get(VARIANT);
    }

    public void setVariant(int variant) {
        entityData.set(VARIANT, Math.clamp(variant, 0, 2));
    }

    /** Nombre de places : trois dans la Mk1, quatre dans les Mk2 et Mk3. */
    public int seats() {
        return getVariant() == 0 ? SEATS : 4;
    }

    public boolean isLunar() {
        return entityData.get(LUNAR);
    }

    public void setLunar(boolean lunar) {
        entityData.set(LUNAR, lunar);
    }

    /** Vrai pour un vaisseau martien. */
    public boolean isMartian() {
        return entityData.get(MARTIAN);
    }

    public void setMartian(boolean martian) {
        entityData.set(MARTIAN, martian);
    }

    /** Vrai pour un vaisseau titanien. */
    public boolean isTitanian() {
        return entityData.get(TITANIAN);
    }

    public void setTitanian(boolean titanian) {
        entityData.set(TITANIAN, titanian);
    }

    /** Vrai pour un vaisseau d'Europe. */
    public boolean isEuropan() {
        return entityData.get(EUROPAN);
    }

    public void setEuropan(boolean europan) {
        entityData.set(EUROPAN, europan);
    }

    /** Vrai tant que le moteur de descente brûle. */
    public boolean isBurning() {
        return entityData.get(BURNING);
    }

    /** Part des ergols de descente qu'il reste, de 0 à 1. */
    public float fuelShare() {
        return entityData.get(FUEL);
    }

    /**
     * Vitesse de descente qu'on peut se permettre à une hauteur donnée, en blocs par tick : celle d'où le moteur,
     * allumé sans attendre, arrête encore le vaisseau avant le sol, avec un quart de marge. Plus on est bas, plus
     * elle est faible. L'ordinateur de bord s'y tient, et l'affichage du pilote s'en sert pour dire quand pousser.
     */
    public static double safeDescentSpeed(double height) {
        return Math.clamp(0.75 * Math.sqrt(2.0 * (DESCENT_THRUST - DESCENT_GRAVITY) * Math.max(0.0, height)), 0.07, 1.4);
    }

    /** Vitesse du vaisseau pendant sa descente propulsée, en blocs par tick. */
    public Vec3 descentVelocity() {
        return descentVelocity;
    }

    /** L'objet que redevient ce vaisseau quand on le récupère. */
    public ItemStack asItem() {
        return new ItemStack(isEuropan() ? ModItems.EUROPA_LANDER.get() : isTitanian() ? ModItems.TITAN_LANDER.get() : isMartian() ? ModItems.MARS_LANDER.get() : isLunar() ? ModItems.LUNAR_MODULE.get()
                : getVariant() == 2 ? ModItems.CREW_CAPSULE_MK3.get() : getVariant() == 1 ? ModItems.CREW_CAPSULE_MK2.get() : ModItems.CREW_CAPSULE.get());
    }

    public boolean hasOpenParachutes() {
        return getPhase() == Phase.CHUTE;
    }

    /**
     * Vrai pendant la traversée de l'atmosphère d'un monde, bouclier en avant : la rentrée sur la planète et sur Titan,
     * l'entrée dans l'air de Mars. Chaque nouvelle planète à atmosphère y ajoute sa phase d'entrée : la cabine tremble
     * alors (voir RocketShake), jusqu'aux parachutes ou au moteur de descente.
     */
    public boolean isAtmosphericEntry() {
        Phase phase = getPhase();
        return phase == Phase.REENTRY || phase == Phase.MARS_ENTRY;
    }

    /** Où en est le gonflement des parachutes, de 0 (ils sortent de leur sac) à 1 (voile pleine). */
    public float chuteInflation(float partialTick) {
        return hasOpenParachutes() ? Math.min(1.0F, (chuteTicks + partialTick) / CHUTE_INFLATE_TICKS) : 0.0F;
    }

    // ------------------------------------------------------------------
    // Ordres de la table de lancement
    // ------------------------------------------------------------------

    /** Décollage réussi : la capsule suit la fusée. */
    public void launch(RocketStats.Reach reach) {
        this.reach = reach.ordinal();
        setPhase(Phase.ASCENT);
        // Dès cet instant, c'est la capsule qui dessine la fusée : la table vient de cesser de le faire.
        entityData.set(STAGES, 0);
        entityData.set(FLYING, true);
    }

    /** En manuel et en assisté, le pilote ouvre lui-même les parachutes ; en automatique, l'ordinateur de bord s'en charge. */
    public void setManualChute(boolean manual) {
        entityData.set(MANUAL_CHUTE, manual);
    }

    public boolean isManualChute() {
        return entityData.get(MANUAL_CHUTE);
    }

    public boolean isChuteArmed() {
        return entityData.get(CHUTE_ARMED);
    }

    /** Assiette de la fusée que suit la capsule : angle de son nez au-dessus de l'horizon, en degrés. */
    public float getPitch() {
        float up = entityData.get(ATTITUDE).transform(new org.joml.Vector3f(0.0F, 1.0F, 0.0F)).y;
        return (float) Math.toDegrees(Math.asin(Math.clamp(up, -1.0F, 1.0F)));
    }

    /** Côté client : orientation de la cabine à l'instant du rendu, entre deux ticks. */
    public org.joml.Quaternionf attitude(float partialTick) {
        return new org.joml.Quaternionf(lastAttitude).slerp(shownAttitude, partialTick);
    }

    /** Le pilote tient ces commandes : son client les envoie quand elles changent, et les confirme régulièrement. */
    public void setControls(int bits) {
        controls = bits;
        controlsAge = 0;
    }

    private boolean control(int bit) {
        return (controls & bit) != 0;
    }

    /** Commande d'un axe, de -1 à 1, d'après ses deux touches. */
    private int axis(int positive, int negative) {
        return (control(positive) ? 1 : 0) - (control(negative) ? 1 : 0);
    }

    /** Le lanceur n'a pas atteint ce qu'il visait (étape manquée en vol) : la suite du vol de la capsule en tient compte. */
    public void setReach(RocketStats.Reach reach) {
        this.reach = reach.ordinal();
    }

    /** Échec du lanceur : la tour de sauvetage emporte la capsule. */
    public void abort() {
        setPhase(Phase.ABORT);
        // Sans système de sauvetage, rien n'arrache la capsule à temps : l'équipage encaisse l'explosion.
        if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            for (net.minecraft.world.entity.Entity passenger : getPassengers()) {
                if (passenger instanceof ServerPlayer crew && !crew.getAbilities().instabuild
                        && !ResearchManager.isUnlocked(crew, LAUNCH_ESCAPE)) {
                    crew.hurtServer(serverLevel, serverLevel.damageSources().explosion(this, null), ABORT_INJURY);
                    crew.sendSystemMessage(Component.translatable("capsule.orbitalis.abort_hurt").withStyle(ChatFormatting.RED));
                }
            }
        }
        // La fusée éclate : la capsule cesse aussitôt de la dessiner, ses débris prennent le relais.
        entityData.set(FLYING, false);
        tell(Component.translatable("capsule.orbitalis.abort").withStyle(ChatFormatting.RED));
    }

    /** Le système de sauvetage, et ce que l'équipage encaisse sans lui quand sa fusée éclate (en demi-cœurs). */
    public static final String LAUNCH_ESCAPE = "launch_escape";
    private static final float ABORT_INJURY = 12.0F;

    // ------------------------------------------------------------------
    // Vol
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        Phase phase = getPhase();
        chuteTicks = phase == Phase.CHUTE ? chuteTicks + 1 : 0;
        LaunchTableBlockEntity table = getTable() != null && level().getBlockEntity(getTable()) instanceof LaunchTableBlockEntity found
                ? found : null;
        if (level().isClientSide()) {
            // L'orientation affichée rejoint presque d'un coup celle du serveur : assez lissée pour rester fluide,
            // assez vive pour que la vue de l'équipage ne traîne pas derrière la fusée.
            lastAttitude.set(shownAttitude);
            shownAttitude.slerp(entityData.get(ATTITUDE), 0.8F);
        } else if (++controlsAge > 20 || !(getFirstPassenger() instanceof ServerPlayer)) {
            // Un pilote dont on n'a plus de nouvelles ne tient plus rien.
            controls = 0;
        }

        // Montée : la capsule suit exactement la fusée, que la table dessine avec la même formule des deux côtés.
        // Côté client, on la suit dès que la fusée part, même si l'ordre de décollage de la capsule n'est pas encore arrivé.
        // Le serveur décide : la capsule suit sa fusée tant que la table la fait voler. Les clients le lisent, sans
        // avoir besoin de la table, qui peut être loin derrière.
        boolean following = level().isClientSide() ? entityData.get(FLYING)
                : table != null && table.getPhase() == LaunchTableBlockEntity.Phase.FLIGHT && phase == Phase.ASCENT;
        followingRocket = following;
        if (!level().isClientSide()) {
            entityData.set(FLYING, following);
        }
        if (following) {
            double base = entityData.get(BASE_Y);
            if (level().isClientSide()) {
                // La capsule dessine elle-même sa fusée, autour d'elle : elles ne peuvent pas se décaler l'une de
                // l'autre. Elle suit donc simplement, en douceur, les positions qu'envoie le serveur.
                // D'abord la position de ce tick, puis le sillage : il part de là où la fusée se trouve vraiment.
                interpolation.interpolate();
                clientFlight();
            } else {
                Vec3 moved = table.flightOffset();
                setPos(getTable().getX() + 0.5 + moved.x, base + Math.max(0.0, moved.y), getTable().getZ() + 0.5 + moved.z);
                entityData.set(ATTITUDE, table.attitude());
                entityData.set(STAGES, table.stagesPassed());
                entityData.set(COASTING, table.enginesOut());
                // Aux commandes : tangage, lacet et roulis font tourner la fusée sur ses trois axes, et la touche
                // d'étape fait l'étape du vol qui vient de s'allumer. L'affichage tête haute dit quoi tenir et quoi faire.
                if (getFirstPassenger() instanceof ServerPlayer pilot) {
                    // L'équipage ne pilote pas : la fusée suit sa trajectoire d'elle-même (l'ordinateur de bord tient le
                    // nez, faute de commandes). Il prend les décisions du vol, chacune dans sa fenêtre, d'une seule touche.
                    // Les touches de direction ne servent qu'aux QTE de stabilisation.
                    int steering = 0;
                    for (int bit : new int[] {PilotInputPayload.PITCH_DOWN, PilotInputPayload.PITCH_UP, PilotInputPayload.YAW_LEFT, PilotInputPayload.YAW_RIGHT}) {
                        steering |= control(bit) ? bit : 0;
                    }
                    table.qteInput(steering);
                    boolean step = control(PilotInputPayload.STEP);
                    if (step && commandArmed) {
                        int open = table.flightOpen();
                        com.orbitalis.phase1.spaceport.LaunchStep[] steps = {com.orbitalis.phase1.spaceport.LaunchStep.THROTTLE,
                                com.orbitalis.phase1.spaceport.LaunchStep.STAGE, com.orbitalis.phase1.spaceport.LaunchStep.FAIRING,
                                com.orbitalis.phase1.spaceport.LaunchStep.CUTOFF};
                        for (int k = 0; k < steps.length; k++) {
                            if ((open & 1 << k) != 0) {
                                table.command(steps[k], ResearchManager.agencyKey(pilot));
                            }
                        }
                    }
                    commandArmed = !step;
                    if (tickCount % 2 == 0) {
                        com.orbitalis.core.network.ModNetwork.send(pilot, new com.orbitalis.core.network.FlightHudPayload(
                                (float) table.flightAltitudeKm(), (float) table.flightSpeed(), table.stress(), table.flightBits(),
                                table.flightWindowSeconds(), table.getAssist(), table.flightTimer(), table.qteHud()));
                    }
                }
            }
            return;
        }
        if (level().isClientSide()) {
            seenStages = 0;
            // Hors de la montée, la capsule rejoint en douceur les positions envoyées par le serveur.
            interpolation.interpolate();
            clientExhaust();
            return;
        }
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        // Séparée de sa fusée, la cabine garde l'orientation qu'elle avait, puis se redresse peu à peu : jamais d'un
        // coup. Tant que la tour de sauvetage pousse, elle ne se redresse pas du tout.
        org.joml.Quaternionf held = new org.joml.Quaternionf(entityData.get(ATTITUDE));
        if (phase != Phase.ABORT && phase != Phase.POWERED_DESCENT && !held.equals(new org.joml.Quaternionf(), 1.0E-4F)) {
            entityData.set(ATTITUDE, held.slerp(new org.joml.Quaternionf(), 0.035F).angle() < 0.01F ? new org.joml.Quaternionf() : held);
        }
        timer++;
        switch (phase) {
            case PAD -> {
                // La capsule n'existe que tant que sa fusée est sur la table.
                if (table == null || !table.isCrewed()) {
                    discard();
                } else {
                    tickLaunchCommand(table);
                }
            }
            case ASCENT -> endOfBurn();
            case COAST -> tickCoast();
            case REENTRY -> tickReentry(level);
            case CHUTE -> tickChute();
            case LUNAR_DESCENT -> tickLunarDescent(level);
            case LUNAR_ASCENT -> tickLunarAscent(level);
            case MARS_ENTRY -> tickMarsEntry(level);
            case POWERED_DESCENT -> tickPoweredDescent(level);
            case LANDED -> {
                if ((isLunar() && SpaceRules.isMoon(level)) || (isMartian() && SpaceRules.isMars(level)) || (isTitanian() && SpaceRules.isTitan(level))
                        || (isEuropan() && SpaceRules.isEuropa(level))) {
                    tickMoonDeparture();
                }
                // Posée : elle reste soumise à la pesanteur si le sol disparaît sous elle.
                if (!isInWater()) {
                    move(MoverType.SELF, new Vec3(0.0, -0.3, 0.0));
                }
            }
            case ORBIT -> tickOrbit(level);
            case ABORT -> {
                // La tour de sauvetage tire la capsule dans son axe : penchée, elle part de biais, loin de la fusée.
                org.joml.Vector3f nose = entityData.get(ATTITUDE).transform(new org.joml.Vector3f(0.0F, 1.0F, 0.0F));
                move(new Vec3(driftX * 0.4 + nose.x * ABORT_SPEED, Math.max(0.5, nose.y) * ABORT_SPEED, driftZ * 0.4 + nose.z * ABORT_SPEED));
                // Les parachutes ne s'ouvrent pas d'eux-mêmes : au pilote de les armer, dès maintenant s'il le veut.
                if (isManualChute() && getFirstPassenger() instanceof ServerPlayer && !isChuteArmed() && control(PilotInputPayload.CHUTE)) {
                    entityData.set(CHUTE_ARMED, true);
                    tell(Component.translatable("capsule.orbitalis.hud.chute_armed").withStyle(ChatFormatting.GREEN));
                }
                if (timer >= ABORT_TICKS) {
                    // La tour a fini de pousser : la capsule est au sommet de sa course, et retombe.
                    fallSpeed = 0.0;
                    setPhase(Phase.REENTRY);
                }
            }
        }
    }

    /**
     * Commande de lancement depuis la cabine : la touche de saut ouvre le séquenceur de lancement, le même qu'au
     * pupitre. Le pilote y mène toute la séquence, du plein au lâcher des attaches, sans personne au sol.
     */
    private void tickLaunchCommand(LaunchTableBlockEntity table) {
        if (!(getFirstPassenger() instanceof ServerPlayer player)) {
            return;
        }
        if (tickCount % 10 == 0) {
            com.orbitalis.core.network.ModNetwork.send(player, com.orbitalis.phase1.spaceport.LaunchConsoleBlock.status(level(), table.getBlockPos(), false, ResearchManager.agencyKey(player)));
        }
        boolean jump = control(PilotInputPayload.LAUNCH);
        // Un appui n'en vaut qu'un : il faut relâcher la touche avant de rouvrir l'écran.
        if (!jump) {
            commandArmed = true;
            return;
        }
        if (commandArmed && table.getPhase() != LaunchTableBlockEntity.Phase.FLIGHT) {
            commandArmed = false;
            com.orbitalis.core.network.ModNetwork.send(player, com.orbitalis.phase1.spaceport.LaunchConsoleBlock.status(level(), table.getBlockPos(), true, ResearchManager.agencyKey(player)));
        }
    }

    /** Fin de la poussée : la suite dépend de ce que le lanceur a permis d'atteindre. */
    private void endOfBurn() {
        originX = getX();
        originZ = getZ();
        // En orbite, c'est le pas de tir qui sert de repère, et non l'endroit où la poussée a emmené la fusée : la
        // station d'une agence est déployée à la verticale de son pas de tir, et c'est là que la capsule doit
        // arriver pour la rejoindre. Elle en reviendra de même au-dessus de lui.
        if (isOrbital() && getTable() != null) {
            originX = getTable().getX() + 0.5;
            originZ = getTable().getZ() + 0.5;
        }
        // Vol orbital habité : la capsule et son équipage passent dans l'espace, pour de bon.
        if (isOrbital() && getFirstPassenger() instanceof ServerPlayer player && level() instanceof ServerLevel level
                && enterOrbit(level, player, !canReachMoon() && !canReachMars() && !canReachTitan() && !canReachEuropa())) {
            return;
        }
        if (reach >= RocketStats.Reach.SUBORBITAL.ordinal()) {
            setPhase(Phase.COAST);
            tell(Component.translatable(isOrbital() ? "capsule.orbitalis.orbit_insertion" : "capsule.orbitalis.apogee")
                    .withStyle(ChatFormatting.AQUA));
        } else {
            setPhase(Phase.REENTRY);
        }
    }

    /**
     * Fait passer la capsule et son pilote en orbite. S'il y a une station de l'agence au-dessus du site de
     * lancement, la capsule arrive à distance, dans l'axe du sas, et fait son approche.
     *
     * @param rendezvous faux pour rester en orbite libre, sans rejoindre de station : c'est le cas d'un vaisseau
     *                   lunaire à l'aller, qui n'est que de passage avant de partir pour la Lune
     * @return faux si la dimension d'orbite n'existe pas
     */
    private boolean enterOrbit(ServerLevel level, ServerPlayer player, boolean rendezvous) {
        ServerLevel orbit = level.getServer().getLevel(SpaceRules.ORBIT);
        if (orbit == null) {
            return false;
        }
        setPhase(Phase.ORBIT);
        station = rendezvous ? StationManager.nearest(level.getServer(), ResearchManager.agencyKey(player), originX, originZ) : null;
        docked = false;
        // En orbite libre, la capsule se tient à l'écart de la station, s'il y en a une au-dessus de ce site.
        Vec3 parking = station != null ? approachStart(station)
                : new Vec3(originX + (rendezvous ? 0.0 : FREE_ORBIT_OFFSET), SpaceRules.PARKING_HEIGHT, originZ);
        player.sendSystemMessage(Component.translatable(station != null ? "capsule.orbitalis.rendezvous"
                : canReachEuropa() ? "capsule.orbitalis.in_orbit_europa"
                : canReachTitan() ? "capsule.orbitalis.in_orbit_titan" : canReachMars() ? "capsule.orbitalis.in_orbit_mars"
                : canReachMoon() ? "capsule.orbitalis.in_orbit_lunar" : "capsule.orbitalis.in_orbit")
                .withStyle(ChatFormatting.AQUA));
        teleport(new TeleportTransition(orbit, parking, Vec3.ZERO, getYRot(), getXRot(), TeleportTransition.DO_NOTHING));
        return true;
    }

    // ------------------------------------------------------------------
    // Voyage lunaire
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Voyage martien
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Voyage vers Europe
    // ------------------------------------------------------------------

    /** Vrai si ce vaisseau peut partir pour Europe : c'est un vaisseau d'Europe, et son lanceur en avait la capacité. */
    private boolean canReachEuropa() {
        return isEuropan() && reach >= RocketStats.Reach.EUROPA.ordinal();
    }

    /** Départ de l'orbite vers Europe : sans air pour le freiner, le vaisseau descend sur son moteur, comme sur la Lune. */
    private void transeuropa(ServerLevel level) {
        ServerLevel europa = level.getServer().getLevel(SpaceRules.EUROPA);
        if (europa == null) {
            return;
        }
        station = null;
        docked = false;
        departing = 0;
        setPhase(Phase.LUNAR_DESCENT);
        if (getFirstPassenger() instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.translatable("capsule.orbitalis.transeuropa").withStyle(ChatFormatting.AQUA));
        }
        teleport(new TeleportTransition(europa, new Vec3(originX, MOON_ARRIVAL_HEIGHT, originZ), Vec3.ZERO,
                getYRot(), getXRot(), TeleportTransition.DO_NOTHING));
    }

    private void europaLanded(ServerLevel level) {
        level.playSound(null, blockPosition(), com.orbitalis.core.registry.ModSounds.CAPSULE_LANDING.get(), net.minecraft.sounds.SoundSource.BLOCKS, 2.0F, 1.0F);
        if (!(getFirstPassenger() instanceof ServerPlayer player)) {
            return;
        }
        player.sendSystemMessage(Component.translatable("capsule.orbitalis.europa_landed").withStyle(ChatFormatting.GREEN));
        String agency = ResearchManager.agencyKey(player);
        AgencyResearch research = ResearchManager.forAgency(level.getServer(), agency);
        if (research.completeMilestone("europa_landing")) {
            research.addPoints(REWARD_EUROPA_LANDING);
            ResearchManager.changed(level.getServer(), agency);
            player.sendSystemMessage(Component.translatable("capsule.orbitalis.first_europa_landing", REWARD_EUROPA_LANDING).withStyle(ChatFormatting.AQUA));
        }
    }

    // ------------------------------------------------------------------
    // Voyage vers Titan
    // ------------------------------------------------------------------

    /** Vrai si ce vaisseau peut partir pour Titan : c'est un vaisseau titanien, et son lanceur en avait la capacité. */
    private boolean canReachTitan() {
        return isTitanian() && reach >= RocketStats.Reach.TITAN.ordinal();
    }

    /**
     * Départ de l'orbite vers Titan : le vaisseau arrive très haut au-dessus de sa surface et fait sa rentrée, bouclier
     * en avant. L'air de Titan est épais : il le freine assez pour que ses parachutes le posent, comme la sonde Huygens.
     */
    private void transtitan(ServerLevel level) {
        ServerLevel titan = level.getServer().getLevel(SpaceRules.TITAN);
        if (titan == null) {
            return;
        }
        station = null;
        docked = false;
        departing = 0;
        fallSpeed = 0.0;
        entityData.set(CHUTE_ARMED, false);
        setPhase(Phase.REENTRY);
        if (getFirstPassenger() instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.translatable("capsule.orbitalis.transtitan").withStyle(ChatFormatting.AQUA));
        }
        teleport(new TeleportTransition(titan, new Vec3(originX, TITAN_ARRIVAL_HEIGHT, originZ), Vec3.ZERO,
                getYRot(), getXRot(), TeleportTransition.DO_NOTHING));
    }

    /** Posé sur Titan, sous ses parachutes : le premier atterrissage d'une agence lui rapporte une prime. */
    private void titanLanded() {
        if (!(getFirstPassenger() instanceof ServerPlayer player)) {
            return;
        }
        player.sendSystemMessage(Component.translatable("capsule.orbitalis.titan_landed").withStyle(ChatFormatting.GREEN));
        String agency = ResearchManager.agencyKey(player);
        AgencyResearch research = ResearchManager.forAgency(player.level().getServer(), agency);
        if (research.completeMilestone("titan_landing")) {
            research.addPoints(REWARD_TITAN_LANDING);
            ResearchManager.changed(player.level().getServer(), agency);
            player.sendSystemMessage(Component.translatable("capsule.orbitalis.first_titan_landing", REWARD_TITAN_LANDING).withStyle(ChatFormatting.AQUA));
        }
    }

    /** Place le vaisseau en descente, comme à son arrivée au-dessus d'Europe (tests et outil de capture). */
    public void startEuropaDescent() {
        setEuropan(true);
        setPhase(Phase.LUNAR_DESCENT);
    }

    /** Place le vaisseau en rentrée, comme à son arrivée au-dessus de Titan (tests et outil de capture). */
    public void startTitanEntry() {
        setTitanian(true);
        fallSpeed = 0.0;
        entityData.set(CHUTE_ARMED, false);
        setPhase(Phase.REENTRY);
    }

    /** Vrai si ce vaisseau peut partir pour Mars : c'est un vaisseau martien, et son lanceur en avait la capacité. */
    private boolean canReachMars() {
        return isMartian() && reach >= RocketStats.Reach.MARS.ordinal();
    }

    /** Départ de l'orbite vers Mars : le vaisseau arrive très haut au-dessus de sa surface, et entre dans son air. */
    private void transmars(ServerLevel level) {
        ServerLevel mars = level.getServer().getLevel(SpaceRules.MARS);
        if (mars == null) {
            return;
        }
        station = null;
        docked = false;
        departing = 0;
        setPhase(Phase.MARS_ENTRY);
        if (getFirstPassenger() instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.translatable("capsule.orbitalis.transmars").withStyle(ChatFormatting.AQUA));
        }
        teleport(new TeleportTransition(mars, new Vec3(originX, MARS_ARRIVAL_HEIGHT, originZ), Vec3.ZERO,
                getYRot(), getXRot(), TeleportTransition.DO_NOTHING));
    }

    /**
     * Entrée dans l'air de Mars : il est cent fois plus mince que celui de la planète. Il freine le vaisseau, bouclier
     * en avant, mais ne suffit pas à le poser : à quelques centaines de mètres du sol, c'est le moteur qui prend le relais.
     */
    private void tickMarsEntry(ServerLevel level) {
        resetFallDistance();
        double height = getY() - groundHeight();
        if (height <= HANDOVER_HEIGHT) {
            beginPoweredDescent();
            return;
        }
        double share = Math.clamp((height - HANDOVER_HEIGHT) / (MARS_ARRIVAL_HEIGHT - HANDOVER_HEIGHT), 0.0, 1.0);
        move(new Vec3(0.0, -Mth.lerp(share, HANDOVER_SPEED, ENTRY_SPEED), 0.0));
        if (timer % 10 == 0) {
            tell(Component.translatable("capsule.orbitalis.hud.mars_entry", (int) height));
        }
    }

    /** Le moteur de descente prend le relais : le vaisseau est debout, il tombe encore, et ses ergols sont comptés. */
    private void beginPoweredDescent() {
        setPhase(Phase.POWERED_DESCENT);
        lander.reset(Direction.EAST);
        descentVelocity = new Vec3(0.0, -HANDOVER_SPEED, 0.0);
        descentFuel = DESCENT_FUEL;
        entityData.set(FUEL, 1.0F);
        boolean manual = isManualChute() && getFirstPassenger() instanceof ServerPlayer;
        tell(Component.translatable(manual ? "capsule.orbitalis.descent_manual" : "capsule.orbitalis.descent_auto").withStyle(ChatFormatting.GOLD));
    }

    /**
     * Descente propulsée. Deux forces, comme au décollage : la poussée du moteur, dans l'axe du vaisseau, tant qu'il
     * brûle ; et la pesanteur de Mars. Le vaisseau garde la vitesse qu'il a.
     * <p>
     * Le moteur se dose de lui-même : allumé, il freine à fond tant que le vaisseau descend trop vite pour sa hauteur,
     * puis réduit sa poussée pour tenir une descente régulière, de plus en plus lente à l'approche du sol. Il ne fait
     * jamais remonter le vaisseau : ses ergols ne partent pas en fumée à gagner de la hauteur.
     * <p>
     * En manuel et en assisté, le pilote décide quand le moteur brûle (il tient la touche d'étape) et tient
     * l'attitude du vaisseau : penché, la poussée le chasse de côté, et il faut la contrer. Moteur coupé, le vaisseau
     * tombe. Les ergols sont comptés. En automatique, ou sans pilote, l'ordinateur de bord tient le vaisseau debout et
     * garde le moteur allumé.
     * <p>
     * Au contact, tout se juge d'un coup : trop vite, trop de travers ou trop penché, et le vaisseau se brise.
     */
    private void tickPoweredDescent(ServerLevel level) {
        resetFallDistance();
        double height = getY() - groundHeight();
        boolean manual = isManualChute() && getFirstPassenger() instanceof ServerPlayer;
        org.joml.Vector3d up = new org.joml.Vector3d(0.0, 1.0, 0.0);
        boolean lit;
        if (manual) {
            lander.steer(axis(PilotInputPayload.PITCH_DOWN, PilotInputPayload.PITCH_UP), axis(PilotInputPayload.YAW_RIGHT, PilotInputPayload.YAW_LEFT),
                    axis(PilotInputPayload.ROLL_RIGHT, PilotInputPayload.ROLL_LEFT), 1.3, false, up);
            lit = control(PilotInputPayload.STEP) && descentFuel > 0.0;
        } else {
            // L'ordinateur de bord : debout, moteur allumé.
            lander.follow(up);
            lit = true;
        }
        org.joml.Vector3d nose = lander.nose();
        double thrust = 0.0;
        if (lit) {
            // Ce qu'il faut de poussée vers le haut : de quoi porter le vaisseau, plus de quoi ramener sa descente à
            // la vitesse que sa hauteur permet. Penché, le moteur doit pousser plus fort pour le même effet.
            double sink = -descentVelocity.y;
            double wanted = safeDescentSpeed(height) * 0.85;
            double lift = DESCENT_GRAVITY + Math.clamp((sink - wanted) * 0.4, -DESCENT_GRAVITY * 0.7, DESCENT_THRUST - DESCENT_GRAVITY);
            thrust = Math.clamp(lift / Math.max(0.35, nose.y), 0.0, DESCENT_THRUST);
            if (manual) {
                descentFuel = Math.max(0.0, descentFuel - thrust / DESCENT_THRUST);
            }
        }
        boolean burn = thrust > DESCENT_THRUST * 0.05;
        descentVelocity = descentVelocity.add(nose.x * thrust, nose.y * thrust - DESCENT_GRAVITY, nose.z * thrust);
        entityData.set(ATTITUDE, lander.orientation());
        entityData.set(BURNING, burn);
        entityData.set(FUEL, (float) (descentFuel / DESCENT_FUEL));
        move(descentVelocity);
        int ground = groundHeight();
        if (getY() <= ground + 0.02) {
            // Contact : on juge la vitesse de descente, la vitesse de côté et l'inclinaison.
            double sink = -descentVelocity.y;
            double slide = Math.hypot(descentVelocity.x, descentVelocity.z);
            double tilt = Math.toDegrees(Math.acos(Math.clamp(nose.y, -1.0, 1.0)));
            setPos(getX(), ground, getZ());
            entityData.set(BURNING, false);
            if (sink <= SAFE_SINK && slide <= SAFE_SLIDE && tilt <= SAFE_TILT) {
                descentVelocity = Vec3.ZERO;
                setPhase(Phase.LANDED);
                marsLanded(level);
            } else {
                crash(level, "capsule.orbitalis.crash_landing");
            }
            return;
        }
        if (timer % 10 == 0 && !manual) {
            tell(Component.translatable("capsule.orbitalis.hud.lunar_descent", (int) height, String.format(Locale.ROOT, "%.1f", -descentVelocity.y * 20.0)));
        }
    }

    private void marsLanded(ServerLevel level) {
        level.playSound(null, blockPosition(), com.orbitalis.core.registry.ModSounds.CAPSULE_LANDING.get(), net.minecraft.sounds.SoundSource.BLOCKS, 2.0F, 1.0F);
        if (!(getFirstPassenger() instanceof ServerPlayer player) || !SpaceRules.isMars(level)) {
            return;
        }
        player.sendSystemMessage(Component.translatable("capsule.orbitalis.mars_landed").withStyle(ChatFormatting.GREEN));
        String agency = ResearchManager.agencyKey(player);
        AgencyResearch research = ResearchManager.forAgency(level.getServer(), agency);
        if (research.completeMilestone("mars_landing")) {
            research.addPoints(REWARD_MARS_LANDING);
            ResearchManager.changed(level.getServer(), agency);
            player.sendSystemMessage(Component.translatable("capsule.orbitalis.first_mars_landing", REWARD_MARS_LANDING).withStyle(ChatFormatting.AQUA));
        }
    }

    /** Place le vaisseau en descente propulsée, comme à la fin de son entrée dans l'air de Mars (tests et outil de capture). */
    public void startMarsDescent() {
        setMartian(true);
        beginPoweredDescent();
    }

    /** Vrai si ce vaisseau peut partir pour la Lune : c'est un vaisseau lunaire, et son lanceur en avait la capacité. */
    private boolean canReachMoon() {
        return isLunar() && reach >= RocketStats.Reach.LUNAR.ordinal();
    }

    /** Départ de l'orbite vers la Lune : le vaisseau arrive haut au-dessus de sa surface et entame sa descente. */
    private void translunar(ServerLevel level) {
        ServerLevel moon = level.getServer().getLevel(SpaceRules.MOON);
        if (moon == null) {
            return;
        }
        station = null;
        docked = false;
        departing = 0;
        setPhase(Phase.LUNAR_DESCENT);
        if (getFirstPassenger() instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.translatable("capsule.orbitalis.translunar").withStyle(ChatFormatting.AQUA));
        }
        teleport(new TeleportTransition(moon, new Vec3(originX, MOON_ARRIVAL_HEIGHT, originZ), Vec3.ZERO,
                getYRot(), getXRot(), TeleportTransition.DO_NOTHING));
    }

    /**
     * Alunissage : pas d'air, donc pas de parachutes. Le vaisseau descend sur son moteur, vite de haut, de plus
     * en plus lentement à l'approche du sol, et se pose en douceur.
     */
    private void tickLunarDescent(ServerLevel level) {
        resetFallDistance();
        int ground = groundHeight();
        double height = getY() - ground;
        if (height <= 0.02) {
            setPos(getX(), ground, getZ());
            setPhase(Phase.LANDED);
            if (SpaceRules.isEuropa(level)) {
                europaLanded(level);
            } else {
                moonLanded(level);
            }
            return;
        }
        double speed = Math.clamp(height * 0.03, 0.06, 1.6);
        move(new Vec3(0.0, -Math.min(speed, height), 0.0));
        if (timer % 10 == 0) {
            tell(Component.translatable("capsule.orbitalis.hud.lunar_descent", (int) height, String.format(Locale.ROOT, "%.1f", speed * 20.0)));
        }
    }

    private void moonLanded(ServerLevel level) {
        level.playSound(null, blockPosition(), com.orbitalis.core.registry.ModSounds.CAPSULE_LANDING.get(), net.minecraft.sounds.SoundSource.BLOCKS, 2.0F, 1.0F);
        if (!(getFirstPassenger() instanceof ServerPlayer player) || !SpaceRules.isMoon(level)) {
            return;
        }
        player.sendSystemMessage(Component.translatable("capsule.orbitalis.moon_landed").withStyle(ChatFormatting.GREEN));
        String agency = ResearchManager.agencyKey(player);
        AgencyResearch research = ResearchManager.forAgency(level.getServer(), agency);
        if (research.completeMilestone("moon_landing")) {
            research.addPoints(REWARD_MOON_LANDING);
            ResearchManager.changed(level.getServer(), agency);
            player.sendSystemMessage(Component.translatable("capsule.orbitalis.first_moon_landing", REWARD_MOON_LANDING).withStyle(ChatFormatting.AQUA));
        }
    }

    /** Posé sur la Lune : le pilote, à bord, maintient la touche de saut trois secondes pour redécoller. */
    private void tickMoonDeparture() {
        if (!(getFirstPassenger() instanceof ServerPlayer player)) {
            launchHold = 0;
            return;
        }
        if (!control(PilotInputPayload.LAUNCH)) {
            launchHold = 0;
            if (timer % 40 == 0) {
                tell(Component.translatable(isEuropan() ? "capsule.orbitalis.hud.europa_hint" : isTitanian() ? "capsule.orbitalis.hud.titan_hint"
                        : isMartian() ? "capsule.orbitalis.hud.mars_hint" : "capsule.orbitalis.hud.moon_hint"));
            }
            return;
        }
        launchHold++;
        tell(Component.translatable("capsule.orbitalis.hud.moon_liftoff", (LAUNCH_HOLD_TICKS - launchHold + 19) / 20));
        if (launchHold >= LAUNCH_HOLD_TICKS) {
            launchHold = 0;
            setPhase(Phase.LUNAR_ASCENT);
            player.sendSystemMessage(Component.translatable(isEuropan() ? "capsule.orbitalis.europa_liftoff" : isTitanian() ? "capsule.orbitalis.titan_liftoff"
                    : isMartian() ? "capsule.orbitalis.mars_liftoff" : "capsule.orbitalis.moon_liftoff")
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    /** Décollage de la Lune : le vaisseau monte en accélérant, puis rejoint l'orbite de la planète. */
    private void tickLunarAscent(ServerLevel level) {
        move(new Vec3(0.0, 0.05 + timer * 0.02, 0.0));
        if (timer % 10 == 0) {
            tell(Component.translatable("capsule.orbitalis.hud.lunar_ascent", (int) (getY() - groundHeight())));
        }
        if (timer >= MOON_ASCENT_TICKS) {
            if (getFirstPassenger() instanceof ServerPlayer player && enterOrbit(level, player, true)) {
                return;
            }
            // Sans pilote ou sans orbite où aller : le vaisseau redescend se poser.
            setPhase(Phase.LUNAR_DESCENT);
        }
    }

    /** Place le vaisseau en descente propulsée, comme à son arrivée au-dessus de la Lune (tests et outil de capture). */
    public void startLunarDescent() {
        setLunar(true);
        setPhase(Phase.LUNAR_DESCENT);
    }

    private boolean isOrbital() {
        return reach >= RocketStats.Reach.LOW_ORBIT.ordinal();
    }

    /**
     * En orbite : la capsule reste immobile. L'équipage peut sortir (accroupi) et revenir (clic droit). Assis à bord,
     * le pilote prépare ses manœuvres comme un vrai équipage : un appui sur la touche de départ prépare le départ vers
     * la Lune (ou Mars, Titan, Europe), un appui sur la touche de retour le freinage de retour ; amarré à une station, la
     * touche de retour maintenue désamarre d'abord la capsule. Voir {@link #tickNode}.
     */
    private void tickOrbit(ServerLevel level) {
        boolean aboard = getFirstPassenger() instanceof ServerPlayer;
        boolean transferKey = aboard && control(PilotInputPayload.MOON);
        boolean returnKey = aboard && control(PilotInputPayload.RETURN);
        boolean armed = nodeArmed;
        nodeArmed = !transferKey && !returnKey;
        int node = entityData.get(NODE);
        if (node != NODE_NONE) {
            tickNode(level, node, node == NODE_DEORBIT ? returnKey : transferKey, armed && (node == NODE_DEORBIT ? transferKey : returnKey));
            return;
        }
        // Un vaisseau titanien part pour Titan, un martien pour Mars, un lunaire pour la Lune : la même touche.
        int transfer = canReachEuropa() ? NODE_EUROPA : canReachTitan() ? NODE_TITAN : canReachMars() ? NODE_MARS : canReachMoon() ? NODE_MOON : NODE_NONE;
        if (transfer != NODE_NONE && transferKey) {
            if (armed) {
                planNode(transfer);
            }
            return;
        }
        lunarHold = 0;
        if (departing > 0 && station != null) {
            tickDeparture(level);
            return;
        }
        if (station != null && !docked) {
            tickApproach();
            return;
        }
        if (!aboard) {
            deorbitHold = 0;
            return;
        }
        if (!returnKey) {
            deorbitHold = 0;
            if (timer % 20 == 0) {
                tell(canReachMoon()
                        ? Component.translatable("capsule.orbitalis.hud.parked_lunar")
                        : canReachMars() ? Component.translatable("capsule.orbitalis.hud.parked_mars")
                        : canReachTitan() ? Component.translatable("capsule.orbitalis.hud.parked_titan")
                        : canReachEuropa() ? Component.translatable("capsule.orbitalis.hud.parked_europa")
                        : Component.translatable("capsule.orbitalis.hud.parked"));
            }
            return;
        }
        if (station != null && docked) {
            // Amarrée : la touche maintenue détache la capsule, qui s'écarte de la station avant de freiner.
            deorbitHold++;
            tell(Component.translatable("capsule.orbitalis.hud.deorbit", (DEORBIT_HOLD_TICKS - deorbitHold + 19) / 20));
            if (deorbitHold >= DEORBIT_HOLD_TICKS) {
                deorbitHold = 0;
                undock();
            }
        } else if (armed) {
            planNode(NODE_DEORBIT);
        }
    }

    /** Prépare une manœuvre : l'ordinateur de bord en calcule le delta-v et la durée, et le compte à rebours commence. */
    private void planNode(int node) {
        entityData.set(NODE, node);
        entityData.set(NODE_TIMER, 0);
        entityData.set(NODE_DONE, 0.0F);
        if (getFirstPassenger() instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.translatable("capsule.orbitalis.node.planned", Component.translatable("pilot.orbitalis.node." + node),
                    (int) NODE_DELTA_V[node], NODE_LEAD[node] / 20).withStyle(ChatFormatting.AQUA));
        }
    }

    private void clearNode() {
        entityData.set(NODE, NODE_NONE);
        entityData.set(NODE_TIMER, 0);
        entityData.set(NODE_DONE, 0.0F);
    }

    /**
     * Une manœuvre en cours. Le compte à rebours mène à la fenêtre d'allumage ; au top, le pilote a
     * {@link #NODE_WINDOW} ticks pour allumer, en tenant la touche de la manœuvre. Le delta-v s'accumule tant qu'il la
     * tient : il doit couper entre {@link #NODE_MIN} et {@link #NODE_MAX} de ce qui est demandé (au-delà, l'ordinateur
     * de bord coupe lui-même). Une fenêtre manquée est reprogrammée à la suivante ; une poussée trop courte annule la
     * manœuvre. L'autre touche l'annule, tant que rien n'est allumé.
     */
    private void tickNode(ServerLevel level, int node, boolean burnKey, boolean cancel) {
        float done = entityData.get(NODE_DONE);
        if (cancel && done <= 0.0F) {
            clearNode();
            tell(Component.translatable("capsule.orbitalis.node.cancelled"));
            return;
        }
        int timer = entityData.get(NODE_TIMER) + 1;
        entityData.set(NODE_TIMER, timer);
        int since = timer - NODE_LEAD[node];
        float target = NODE_DELTA_V[node];
        if (since < 0) {
            return;
        }
        boolean burning = burnKey && (done > 0.0F || since <= NODE_WINDOW);
        if (burning) {
            done += target / NODE_BURN[node];
            entityData.set(NODE_DONE, done);
            if (timer % 8 == 0) {
                level.playSound(null, blockPosition(), com.orbitalis.core.registry.ModSounds.PAD_HYDRAULIC.get(), net.minecraft.sounds.SoundSource.NEUTRAL,
                        0.4F, 0.6F);
            }
        }
        if (done >= target * NODE_MAX || !burnKey && done >= target * NODE_MIN) {
            int percent = Math.round(done / target * 100.0F);
            clearNode();
            if (getFirstPassenger() instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.translatable("capsule.orbitalis.node.done", percent).withStyle(ChatFormatting.GREEN));
            }
            switch (node) {
                case NODE_DEORBIT -> deorbit(level);
                case NODE_EUROPA -> transeuropa(level);
                case NODE_TITAN -> transtitan(level);
                case NODE_MARS -> transmars(level);
                default -> translunar(level);
            }
            return;
        }
        if (done <= 0.0F && since > NODE_WINDOW) {
            // Fenêtre manquée : la suivante se présentera après le même alignement.
            entityData.set(NODE_TIMER, 0);
            tell(Component.translatable("capsule.orbitalis.node.missed", NODE_LEAD[node] / 20));
            return;
        }
        if (done > 0.0F && since > NODE_WINDOW + NODE_BURN[node] + NODE_SLACK) {
            clearNode();
            tell(Component.translatable("capsule.orbitalis.node.incomplete"));
        }
    }

    /** Pour l'outil de capture : une manœuvre préparée depuis {@code timer} ticks, dont {@code done} m/s sont déjà faits. */
    public void previewNode(int node, int timer, float done) {
        entityData.set(NODE, node);
        entityData.set(NODE_TIMER, timer);
        entityData.set(NODE_DONE, done);
    }

    /** La manœuvre préparée ({@link #NODE_NONE} s'il n'y en a pas). */
    public int getNode() {
        return entityData.get(NODE);
    }

    /** Ticks avant la fenêtre d'allumage de la manœuvre (négatif une fois la fenêtre ouverte). */
    public int nodeCountdown() {
        int node = getNode();
        return node == NODE_NONE ? 0 : NODE_LEAD[node] - entityData.get(NODE_TIMER);
    }

    /** Delta-v demandé par la manœuvre, et déjà accompli, en m/s. */
    public float nodeDeltaV() {
        return NODE_DELTA_V[Math.clamp(getNode(), 0, NODE_DELTA_V.length - 1)];
    }

    public float nodeDone() {
        return entityData.get(NODE_DONE);
    }

    /** Lance le désamarrage : les verrous s'ouvrent, puis la capsule recule dans l'axe du sas. */
    public void undock() {
        if (station == null || !docked || departing > 0) {
            return;
        }
        departing = 1;
        level().playSound(null, blockPosition(), com.orbitalis.core.registry.ModSounds.UNDOCKING.get(), net.minecraft.sounds.SoundSource.BLOCKS, 2.0F, 1.0F);
        if (getFirstPassenger() instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.translatable("capsule.orbitalis.undocking").withStyle(ChatFormatting.AQUA));
        }
    }

    /**
     * Désamarrage : un court temps d'arrêt, verrous ouverts, puis la capsule recule dans l'axe du sas, d'abord
     * très lentement, de plus en plus vite ensuite. Une fois à bonne distance, elle freine pour rentrer.
     */
    private void tickDeparture(ServerLevel level) {
        departing++;
        double speed = 0.0;
        if (departing > UNDOCK_PAUSE_TICKS) {
            double progress = (departing - UNDOCK_PAUSE_TICKS) / (double) (UNDOCK_TICKS - UNDOCK_PAUSE_TICKS);
            speed = 0.02 + 0.3 * progress * progress;
            move(new Vec3(0.0, speed * 0.15, speed));
        }
        if (departing % 5 == 0) {
            tell(Component.translatable("capsule.orbitalis.hud.undock",
                    String.format(Locale.ROOT, "%.1f", position().distanceTo(StationManager.capsulePositionAt(station))),
                    String.format(Locale.ROOT, "%.2f", speed * 20.0)));
        }
        if (departing >= UNDOCK_TICKS) {
            departing = 0;
            docked = false;
            station = null;
            deorbit(level);
        }
    }

    /** Vrai pendant que la capsule s'écarte de sa station. */
    public boolean isDeparting() {
        return departing > 0;
    }

    /** Freinage : la capsule quitte l'orbite et réapparaît au-dessus de son pas de tir, pour sa rentrée. */
    private void deorbit(ServerLevel level) {
        ServerLevel planet = level.getServer().overworld();
        setPhase(Phase.REENTRY);
        tell(Component.translatable("capsule.orbitalis.deorbit").withStyle(ChatFormatting.GOLD));
        teleport(new TeleportTransition(planet, new Vec3(originX, REENTRY_HEIGHT, originZ), Vec3.ZERO,
                getYRot(), getXRot(), TeleportTransition.DO_NOTHING));
    }

    /** Point d'arrivée en orbite près d'une station : à distance du sas, dans son axe, un peu au-dessus. */
    private static Vec3 approachStart(BlockPos station) {
        return StationManager.capsulePositionAt(station).add(0.0, APPROACH_HEIGHT, APPROACH_DISTANCE);
    }

    /**
     * Approche finale et amarrage, automatiques comme sur une vraie capsule : la capsule s'aligne d'abord sur
     * l'axe du sas, puis avance vers lui en ralentissant sans cesse, pour le toucher à quelques centimètres par
     * seconde. L'équipage reste à bord jusqu'au contact.
     */
    private void tickApproach() {
        Vec3 dock = StationManager.capsulePositionAt(station);
        Vec3 position = position();
        double lateral = Math.hypot(position.x - dock.x, position.y - dock.y);
        // Tant qu'elle n'est pas dans l'axe, la capsule se recentre sans avancer ; ensuite, elle avance.
        Vec3 target = lateral > 0.15 ? new Vec3(dock.x, dock.y, position.z) : dock;
        Vec3 toTarget = target.subtract(position);
        double distance = toTarget.length();
        double total = dock.subtract(position).length();
        if (total < 0.02) {
            setPos(dock);
            docked = true;
            if (getFirstPassenger() instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.translatable("capsule.orbitalis.docked").withStyle(ChatFormatting.GREEN));
            }
            level().playSound(null, blockPosition(), com.orbitalis.core.registry.ModSounds.DOCKING.get(), net.minecraft.sounds.SoundSource.BLOCKS, 2.0F, 1.0F);
            return;
        }
        // Vitesse proportionnelle à la distance restante : rapide de loin, très lente au contact.
        double speed = Math.clamp(total * 0.035, 0.012, 0.45);
        double step = Math.min(distance, speed);
        if (distance > 1.0E-6) {
            move(toTarget.scale(step / distance));
        }
        if (timer % 5 == 0) {
            tell(Component.translatable("capsule.orbitalis.hud.approach", String.format(Locale.ROOT, "%.1f", total),
                    String.format(Locale.ROOT, "%.2f", step * 20.0)));
        }
    }

    /** Place la capsule en approche d'une station, comme à son arrivée en orbite (tests et outil de capture). */
    public void startApproach(BlockPos stationCenter) {
        startApproach(stationCenter, APPROACH_DISTANCE);
    }

    /** La même approche, depuis une distance choisie. */
    public void startApproach(BlockPos stationCenter, double distance) {
        station = stationCenter.immutable();
        docked = false;
        originX = getX();
        originZ = getZ();
        setPhase(Phase.ORBIT);
        setPos(StationManager.capsulePositionAt(station).add(0.0, APPROACH_HEIGHT * distance / APPROACH_DISTANCE, distance));
    }

    /** Vrai si la capsule est amarrée à une station. */
    public boolean isDocked() {
        return docked;
    }

    private void tickCoast() {
        int duration = isOrbital() ? ORBIT_TICKS * ORBIT_COUNT : APOGEE_TICKS;
        if (timer % 20 == 0) {
            tell(isOrbital()
                    ? Component.translatable("capsule.orbitalis.hud.orbit", Math.min(ORBIT_COUNT, timer / ORBIT_TICKS + 1), ORBIT_COUNT)
                    : Component.translatable("capsule.orbitalis.hud.apogee"));
        }
        if (timer >= duration) {
            setPhase(Phase.REENTRY);
            tell(Component.translatable(isOrbital() ? "capsule.orbitalis.deorbit" : "capsule.orbitalis.reentry")
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    private int groundHeight() {
        return level().getHeight(Heightmap.Types.MOTION_BLOCKING, getBlockX(), getBlockZ());
    }

    /**
     * Rentrée : la capsule tombe, bouclier en avant. Passé une certaine hauteur, l'air l'a assez freinée pour que
     * les parachutes tiennent : il faut alors les armer. En automatique, ou sans pilote, l'ordinateur de bord le
     * fait ; sinon c'est au pilote, avec sa touche. Armés, ils s'ouvrent d'eux-mêmes à la bonne hauteur. S'ils ne le
     * sont pas quand le sol arrive, la capsule s'écrase.
     */
    private void tickReentry(ServerLevel level) {
        double height = getY() - groundHeight();
        boolean window = height <= CHUTE_WINDOW;
        fallSpeed = Math.min(REENTRY_SPEED, fallSpeed + FALL_GAIN);
        double speed = Math.min(fallSpeed, window ? Mth.lerp(Math.max(0.0, height) / CHUTE_WINDOW, CRASH_SPEED, REENTRY_SPEED) : REENTRY_SPEED);
        sinkSpeed = Math.min(speed, Math.max(0.05, height));
        move(new Vec3(driftX * 0.1, -sinkSpeed, driftZ * 0.1));
        boolean manual = isManualChute() && getFirstPassenger() instanceof ServerPlayer;
        if (window && !isChuteArmed() && (!manual || control(PilotInputPayload.CHUTE))) {
            entityData.set(CHUTE_ARMED, true);
            tell(Component.translatable("capsule.orbitalis.hud.chute_armed").withStyle(ChatFormatting.GREEN));
        }
        if (timer % 10 == 0 && (!window || isChuteArmed())) {
            tell(Component.translatable("capsule.orbitalis.hud.reentry", Math.max(0, (int) height)));
        }
        if (isChuteArmed() && height <= CHUTE_HEIGHT) {
            openParachutes();
        } else if (height <= 0.6) {
            crash(level);
        }
    }

    /** La capsule touche le sol sans parachute : elle est détruite, et son équipage avec elle. */
    private void crash(ServerLevel level) {
        crash(level, "capsule.orbitalis.crash");
    }

    /** Le vaisseau se brise au sol, pour la raison que dit ce message. */
    private void crash(ServerLevel level, String messageKey) {
        Vec3 at = position();
        com.orbitalis.core.network.ModNetwork.sendNear(level, 256.0, new com.orbitalis.core.network.BreakupPayload(at, java.util.List.of(),
                0, 0, 4.0F, false, true, new org.joml.Quaternionf(), 0.0F, new org.joml.Vector3f()), at);
        Entity crew = getFirstPassenger();
        // Posée, pour que l'équipage puisse en sortir : en vol, il y est retenu.
        setPhase(Phase.LANDED);
        boolean otherWorld = SpaceRules.isMoon(level) || SpaceRules.isMars(level) || SpaceRules.isTitan(level) || SpaceRules.isEuropa(level);
        if (otherWorld && crew instanceof ServerPlayer spared && spared.getAbilities().invulnerable) {
            // En mode créatif, rien ne tue le pilote : son vaisseau est épargné lui aussi, sans quoi il resterait
            // seul sur un autre monde, sans moyen d'en repartir.
            entityData.set(ATTITUDE, new org.joml.Quaternionf());
            descentVelocity = Vec3.ZERO;
            spared.sendSystemMessage(Component.translatable(messageKey).withStyle(ChatFormatting.RED));
            spared.sendSystemMessage(Component.translatable("capsule.orbitalis.crash_spared").withStyle(ChatFormatting.GRAY));
            return;
        }
        // Sur la planète, rien ne retient la capsule : l'équipage est éjecté du choc, et elle explose. Un équipier en
        // mode créatif en sort indemne ; les autres n'y survivent pas.
        for (Entity passenger : java.util.List.copyOf(getPassengers())) {
            passenger.stopRiding();
            if (passenger instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.translatable(messageKey).withStyle(ChatFormatting.RED));
                if (!player.getAbilities().invulnerable) {
                    player.hurtServer(level, level.damageSources().flyIntoWall(), 200.0F);
                }
            }
        }
        discard();
        // Sur la planète, le choc creuse son cratère ; sur la Lune et sur Mars, sans air pour l'attiser, il ne fait que
        // souffler la poussière (le terrain reste tel quel).
        // L'explosion du jeu, sans son bruit : on entend celle d'Orbitalis, que joue la dislocation (voir BreakupFx).
        level.explode(null, null, null, at.x, at.y + 0.6, at.z, CRASH_EXPLOSION, !otherWorld,
                otherWorld ? net.minecraft.world.level.Level.ExplosionInteraction.NONE : net.minecraft.world.level.Level.ExplosionInteraction.TNT,
                net.minecraft.core.particles.ParticleTypes.EXPLOSION, net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                net.minecraft.util.random.WeightedList.of(),
                net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(net.minecraft.sounds.SoundEvents.EMPTY));
    }

    /** Force de l'explosion d'une capsule qui s'écrase : celle d'un bloc de TNT, un peu plus. */
    private static final float CRASH_EXPLOSION = 4.5F;

    private void openParachutes() {
        setPhase(Phase.CHUTE);
        entityData.set(CHUTE_ARMED, true);
        tell(Component.translatable("capsule.orbitalis.chutes").withStyle(ChatFormatting.GREEN));
    }

    private void tickChute() {
        resetFallDistance();
        // La capsule arrive lancée ; la voile se gonfle, et l'air la freine de plus en plus fort jusqu'à sa vitesse
        // de descente sous voile.
        float inflation = chuteInflation(0.0F);
        double drag = CHUTE_DRAG_FOLDED + (CHUTE_DRAG_OPEN - CHUTE_DRAG_FOLDED) * inflation * inflation;
        sinkSpeed = Math.max(CHUTE_SPEED, sinkSpeed) + (CHUTE_SPEED - Math.max(CHUTE_SPEED, sinkSpeed)) * drag;
        move(MoverType.SELF, new Vec3(driftX * 0.03, -sinkSpeed, driftZ * 0.03));
        if (timer % 10 == 0) {
            tell(Component.translatable("capsule.orbitalis.hud.chute", Math.max(0, (int) getY() - groundHeight())));
        }
        if (onGround() || isInWater()) {
            land();
        } else if (getY() < level().getMinY() - 16) {
            discard();
        }
    }

    /** Retour au sol : l'équipage est sauf, et un premier vol habité rapporte une prime à son agence. */
    private void land() {
        setPhase(Phase.LANDED);
        level().playSound(null, blockPosition(), com.orbitalis.core.registry.ModSounds.CAPSULE_LANDING.get(), net.minecraft.sounds.SoundSource.BLOCKS, 2.0F, 1.0F);
        // Sous parachutes aussi, mais sur Titan : c'est un atterrissage, non un retour.
        if (SpaceRules.isTitan(level())) {
            titanLanded();
            return;
        }
        if (!(getFirstPassenger() instanceof ServerPlayer player)) {
            return;
        }
        player.sendSystemMessage(Component.translatable("capsule.orbitalis.landed").withStyle(ChatFormatting.GREEN));
        String agency = ResearchManager.agencyKey(player);
        AgencyResearch research = ResearchManager.forAgency(player.level().getServer(), agency);
        int points = 0;
        if (reach >= RocketStats.Reach.SUBORBITAL.ordinal() && research.completeMilestone("crewed_suborbital")) {
            points += REWARD_SUBORBITAL;
            player.sendSystemMessage(Component.translatable("capsule.orbitalis.first_suborbital", REWARD_SUBORBITAL).withStyle(ChatFormatting.AQUA));
        }
        if (isOrbital() && research.completeMilestone("crewed_orbital")) {
            points += REWARD_ORBITAL;
            player.sendSystemMessage(Component.translatable("capsule.orbitalis.first_orbital", REWARD_ORBITAL).withStyle(ChatFormatting.AQUA));
        }
        if (points > 0) {
            research.addPoints(points);
            ResearchManager.changed(player.level().getServer(), agency);
        }
    }

    /** Déplacement sans collision : la capsule traverse l'air, et sa fusée au départ. */
    private void move(Vec3 delta) {
        setPos(getX() + delta.x, getY() + delta.y, getZ() + delta.z);
    }

    /** Message au passager, affiché au-dessus de sa barre d'objets. */
    private void tell(Component message) {
        if (getFirstPassenger() instanceof ServerPlayer player) {
            player.sendOverlayMessage(message);
        }
    }

    // ------------------------------------------------------------------
    // Équipage et récupération
    // ------------------------------------------------------------------

    /**
     * Chaque passager est assis à sa place, dans la capsule. Le premier monté est le pilote : son siège est devant,
     * et la cabine tourne autour de sa tête. Les deux autres sont derrière lui, de part et d'autre : leur siège
     * tourne avec la cabine.
     */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, net.minecraft.world.entity.EntityDimensions dimensions, float scale) {
        int seat = getPassengers().indexOf(passenger);
        if (seat <= 0) {
            return new Vec3(0.0, SEAT_HEIGHT, SEAT_BACK);
        }
        // Le quatrième équipier (Mk2, Mk3) est au milieu, derrière le pilote, sur une marche de trois pixels.
        org.joml.Vector3f offset = seat == 3 ? new org.joml.Vector3f(0.0F, 3.0F / 16.0F, 7.5F / 16.0F)
                : new org.joml.Vector3f((float) (seat == 1 ? -CREW_SIDE : CREW_SIDE), 0.0F, (float) CREW_BACK);
        org.joml.Vector3f place = new org.joml.Quaternionf(entityData.get(ATTITUDE)).transform(offset);
        return new Vec3(place.x, SEAT_HEIGHT + place.y, SEAT_BACK + place.z);
    }

    /** Vrai si ce joueur est aux commandes : c'est le premier monté à bord. */
    public boolean isPilot(Entity player) {
        return getFirstPassenger() == player;
    }

    /**
     * Sortie de la capsule. Sur le pas de tir, le joueur se retrouve sur le seuil du portique d'embarquement, dos
     * à la fusée (ou, sans portique, sur le tablier). Ailleurs (capsule posée, ou en orbite), il sort à côté de la
     * capsule.
     */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        // Amarrée à une station : l'équipage passe le sas et se retrouve à l'intérieur.
        if (getPhase() == Phase.ORBIT && station != null && docked) {
            return StationManager.insideOf(station);
        }
        Vec3 pad = padExit();
        return pad != null ? pad : super.getDismountLocationForPassenger(passenger);
    }

    /**
     * Seuil du portique d'embarquement par lequel on sort de la capsule : le plus proche de sa hauteur. {@code null}
     * si le pas de tir n'en a pas.
     */
    private BlockPos exitGate(LaunchTableBlockEntity launchTable) {
        BlockPos tower = launchTable.getComplex().towerBase();
        if (tower == null) {
            return null;
        }
        BlockPos best = null;
        for (int gate : launchTable.getGateLevels()) {
            if (best == null || Math.abs(gate - getY()) < Math.abs(best.getY() - getY())) {
                best = tower.atY(gate);
            }
        }
        return best;
    }

    /**
     * Sens dans lequel regarde le joueur en sortant sur le pas de tir, dos à la fusée : vers la tour s'il sort par
     * le portique, sinon dans le sens du carneau. {@code null} hors d'un pas de tir.
     */
    private Direction padExitDirection() {
        BlockPos table = getTable();
        if (getPhase() != Phase.PAD || table == null || !(level().getBlockEntity(table) instanceof LaunchTableBlockEntity launchTable)) {
            return null;
        }
        BlockPos gate = exitGate(launchTable);
        return gate != null ? LaunchComplex.towerFacing(gate, table).getOpposite() : launchTable.getTrenchDirection();
    }

    /**
     * Où l'on pose le pied en sortant sur le pas de tir : sur le seuil du portique d'embarquement, d'où l'on
     * redescend par la tour. Sans portique, sur le tablier, juste devant le flanc de la fusée, du côté du carneau.
     */
    private Vec3 padExit() {
        Direction out = padExitDirection();
        BlockPos table = getTable();
        if (out == null || !(level().getBlockEntity(table) instanceof LaunchTableBlockEntity launchTable)) {
            return null;
        }
        BlockPos gate = exitGate(launchTable);
        if (gate != null) {
            // Le seuil est une tôle d'un pixel d'épaisseur, au pied de la case.
            return new Vec3(gate.getX() + 0.5, gate.getY() + 1.0 / 16.0, gate.getZ() + 0.5);
        }
        // Un lanceur de trois blocs de large occupe aussi la case voisine de la table.
        int distance = launchTable.hasRocket() && launchTable.rocketShape().isWide() ? 2 : 1;
        BlockPos spot = table.relative(out, distance);
        return new Vec3(spot.getX() + 0.5, table.getY() + 1.0, spot.getZ() + 0.5);
    }

    /** En sortant sur le pas de tir, le joueur tourne le dos à la fusée. */
    @Override
    protected void removePassenger(Entity passenger) {
        Direction out = padExitDirection();
        super.removePassenger(passenger);
        if (out != null && passenger instanceof ServerPlayer player) {
            player.forceSetRotation(out.toYRot(), false, 0.0F, false);
            player.setYHeadRot(out.toYRot());
        }
    }

    /**
     * À l'embarquement, le pilote est tourné vers la planche de bord, le regard un peu baissé sur son écran :
     * il s'assoit face à ses commandes, d'où qu'il soit monté.
     */
    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (passenger instanceof ServerPlayer player) {
            player.forceSetRotation(PILOT_YAW, false, PILOT_PITCH, false);
            player.setYHeadRot(PILOT_YAW);
        }
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < seats() && passenger instanceof Player;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved() && isExposed();
    }

    /** Posée : un clic droit pour y monter, accroupi pour la récupérer. */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        Phase phase = getPhase();
        if ((phase != Phase.LANDED && phase != Phase.ORBIT) || getPassengers().size() >= seats()) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide()) {
            // Une capsule ne se récupère qu'au sol : en orbite, c'est le seul moyen de rentrer.
            if (player.isSecondaryUseActive() && phase == Phase.LANDED && !isVehicle()) {
                ItemStack capsule = asItem();
                if (!player.getInventory().add(capsule)) {
                    player.drop(capsule, false);
                }
                discard();
            } else {
                player.startRiding(this);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Rien ne détruit une capsule : ni les coups, ni l'explosion de son lanceur. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    /** Visible de loin : on suit sa descente depuis le pas de tir. */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    // ------------------------------------------------------------------
    // Sauvegarde
    // ------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("phase", getPhase().ordinal());
        output.putInt("timer", timer);
        output.putInt("reach", reach);
        output.putBoolean("lunar", isLunar());
        output.putInt("variant", getVariant());
        output.putInt("node", entityData.get(NODE));
        output.putInt("node_timer", entityData.get(NODE_TIMER));
        output.putFloat("node_done", entityData.get(NODE_DONE));
        output.putBoolean("martian", isMartian());
        output.putBoolean("titanian", isTitanian());
        output.putBoolean("europan", isEuropan());
        output.putDouble("descent_fuel", descentFuel);
        output.putDouble("descent_vx", descentVelocity.x);
        output.putDouble("descent_vy", descentVelocity.y);
        output.putDouble("descent_vz", descentVelocity.z);
        output.putDouble("drift_x", driftX);
        output.putDouble("drift_z", driftZ);
        output.putDouble("origin_x", originX);
        output.putDouble("origin_z", originZ);
        output.storeNullable("station", BlockPos.CODEC, station);
        output.putBoolean("docked", docked);
        output.putBoolean("chute_armed", isChuteArmed());
        output.putDouble("fall_speed", fallSpeed);
        output.putDouble("sink_speed", sinkSpeed);
        output.putBoolean("manual_chute", isManualChute());
        output.putFloat("base_y", entityData.get(BASE_Y));
        output.storeNullable("table", BlockPos.CODEC, getTable());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        setPhase(Phase.values()[Math.floorMod(input.getIntOr("phase", 0), Phase.values().length)]);
        timer = input.getIntOr("timer", 0);
        reach = input.getIntOr("reach", 0);
        setLunar(input.getBooleanOr("lunar", false));
        setVariant(input.getIntOr("variant", 0));
        entityData.set(NODE, Math.clamp(input.getIntOr("node", NODE_NONE), NODE_NONE, NODE_EUROPA));
        entityData.set(NODE_TIMER, input.getIntOr("node_timer", 0));
        entityData.set(NODE_DONE, input.getFloatOr("node_done", 0.0F));
        setMartian(input.getBooleanOr("martian", false));
        setTitanian(input.getBooleanOr("titanian", false));
        setEuropan(input.getBooleanOr("europan", false));
        descentFuel = input.getDoubleOr("descent_fuel", DESCENT_FUEL);
        descentVelocity = new Vec3(input.getDoubleOr("descent_vx", 0.0), input.getDoubleOr("descent_vy", 0.0), input.getDoubleOr("descent_vz", 0.0));
        lander.reset(Direction.EAST);
        driftX = input.getDoubleOr("drift_x", driftX);
        driftZ = input.getDoubleOr("drift_z", driftZ);
        originX = input.getDoubleOr("origin_x", getX());
        originZ = input.getDoubleOr("origin_z", getZ());
        station = input.read("station", BlockPos.CODEC).orElse(null);
        docked = input.getBooleanOr("docked", false);
        entityData.set(CHUTE_ARMED, input.getBooleanOr("chute_armed", false));
        fallSpeed = input.getDoubleOr("fall_speed", REENTRY_SPEED);
        sinkSpeed = input.getDoubleOr("sink_speed", 0.0);
        entityData.set(MANUAL_CHUTE, input.getBooleanOr("manual_chute", false));
        entityData.set(BASE_Y, input.getFloatOr("base_y", 0.0F));
        entityData.set(TABLE, input.read("table", BlockPos.CODEC));
    }
}
