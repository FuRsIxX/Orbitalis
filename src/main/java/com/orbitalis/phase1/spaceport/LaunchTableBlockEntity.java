package com.orbitalis.phase1.spaceport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.orbitalis.core.config.ServerConfig;
import com.orbitalis.core.registry.ModBlockEntities;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.registry.ModSounds;
import com.orbitalis.core.registry.ModFluids;
import com.orbitalis.core.research.ResearchManager;
import com.orbitalis.phase1.rocket.RocketBlueprintItem;
import com.orbitalis.phase1.rocket.RocketPlan;
import com.orbitalis.phase1.rocket.RocketFuel;
import com.orbitalis.phase1.rocket.RocketItem;
import com.orbitalis.phase1.rocket.RocketParts;
import com.orbitalis.core.registry.ModItems;
import com.orbitalis.core.registry.ModEntities;
import com.orbitalis.phase1.satellite.SatelliteManager;
import com.orbitalis.phase1.satellite.SatelliteType;
import com.orbitalis.phase2.flight.CapsuleEntity;
import com.orbitalis.phase2.station.StationManager;
import com.orbitalis.phase1.rocket.RocketStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Table de lancement : le cœur d'un complexe de lancement.
 * <ul>
 * <li>Elle surveille la structure qui l'entoure. Quand le complexe est opérationnel, elle s'allume et les dalles
 * du pas de tir prennent l'aspect de leur rôle (carneau, bordure).</li>
 * <li>Elle porte la fusée, reçoit ses ergols par les bras ombilicaux, et déroule le compte à rebours puis le vol.</li>
 * </ul>
 * Le vol lui-même est simulé (altitude et vitesse affichées au pupitre) : la fusée quitte le pas de tir
 * et la mission s'achève avec son résultat. La fusée volera physiquement dans une étape ultérieure.
 */
public class LaunchTableBlockEntity extends BlockEntity {
    /** Intervalle entre deux relevés de la structure, en ticks. */
    private static final int SCAN_INTERVAL = 40;
    /** Durée du compte à rebours et du vol simulé, en ticks. */
    public static final int COUNTDOWN_TICKS = 30 * 20;
    /** Durée du vol en jeu quand la table n'a pas de fusée ; avec une fusée, voir {@link #flightTicks()}. */
    public static final int DEFAULT_FLIGHT_TICKS = 40 * 20;
    /** La montée normale : la hauteur gagnée vaut ce nombre fois le carré du temps de vol, en secondes. */
    /**
     * Hauteur de l'alarme du pas de tir : un peu plus grave que le son enregistré, et donc un peu plus longue (un peu
     * moins de trois secondes) ; l'essai d'alarme la répète d'autant plus lentement.
     */
    /**
     * Intervalle entre deux alarmes du pas de tir, en ticks : son fichier dure 2,18 s, elles s'enchaînent donc sans se
     * chevaucher. À reprendre si son fichier change de longueur.
     */
    private static final int ALARM_REPEAT_TICKS = 44;
    public static final float ASCENT_RATE = 0.9F;
    /** Fin du compte à rebours : l'allumage, après lequel on ne peut plus annuler. */
    public static final int IGNITION_TICKS = 3 * 20;
    /** Hauteur à laquelle la fusée en vol cesse d'être dessinée, en blocs au-dessus de la table. */
    public static final float ASCENT_LIMIT = 420.0F;
    /** Intervalle entre deux évaporations des ergols cryogéniques, en ticks (dix secondes). */
    private static final int BOIL_OFF_INTERVAL = 200;
    /** Temps restant au compte à rebours quand les passerelles d'embarquement se retirent, avec les bras, en ticks. */
    private static final int GANGWAY_RETRACT_TICKS = 15 * 20;
    /** Dans une case de carte, la hauteur du terrain est décalée d'autant pour rester positive. */
    public static final int MAP_HEIGHT_BIAS = 64;
    /** Portée des annonces du compte à rebours, en blocs. */
    private static final double ANNOUNCE_RANGE = 96.0;

    /** Déroulement d'un lancement. */
    public enum Phase {
        IDLE,
        COUNTDOWN,
        FLIGHT
    }

    /** Issue du dernier lancement. */
    public enum Result {
        NONE,
        SUCCESS,
        FAILURE,
        ABORTED,
        SCRUBBED
    }

    /** Tables de lancement chargées, par monde : les pupitres et les bras ombilicaux s'en servent pour trouver la plus proche. */
    private static final Map<Level, Set<BlockPos>> TABLES = new WeakHashMap<>();

    /** Vrai une fois que le complexe a été mis en service par un joueur dont l'agence maîtrise la technologie. */
    private boolean commissioned;
    /** Vrai si la tour de service est reconnue (l'agence du joueur maîtrisait la technologie à la mise en service). */
    private boolean towerAuthorized;
    /** Plan du pas de tir, choisi dans le kit ; {@code null} tant que la table n'en a pas reçu. */
    private PadPlan plan;
    private LaunchComplex complex = LaunchComplex.NONE;
    private int ticks;

    // Fusée posée sur la table et ergols déjà chargés, en mB.
    private ItemStack rocket = ItemStack.EMPTY;
    /** Caractéristiques et silhouette de la fusée posée, gardées en mémoire tant que la fusée ne change pas. */
    private RocketStats stats;
    private ItemStack statsRocket;
    private RocketShape shape;
    private ItemStack shapeRocket;
    private int loadedRp1;
    private int loadedOxygen;
    private int loadedHydrogen;
    private int loadedMethane;
    private int loadedHelium3;

    // Lancement en cours.
    private Phase phase = Phase.IDLE;
    /** Ticks restants pendant le compte à rebours, ticks écoulés pendant le vol. */
    private int timer;
    /** Agence qui a lancé le compte à rebours : c'est elle qui reçoit les points de recherche. */
    private String agencyKey = "";
    private Result lastResult = Result.NONE;
    private int lastPoints;
    /** Pour les tests automatiques : supprime le risque d'échec. */
    private boolean reliable;

    // Séquence de lancement (voir LaunchStep) : ce que l'équipe a fait, et jusqu'à quand cela tient.
    /** Vrai si les vannes de remplissage sont ouvertes : sans cela, aucun ergol n'entre dans la fusée. */
    private boolean valvesOpen;
    /** Heure du monde jusqu'à laquelle les réservoirs restent sous pression ; 0 s'ils ne le sont pas. */
    private long pressureUntil;
    /** Vrai si le bras ombilical et la passerelle sont retirés. */
    private boolean retracted;
    /** Heure du monde jusqu'à laquelle le déluge coule ; 0 s'il est fermé. */
    private long delugeUntil;
    /** Eau en réserve pour le déluge, en mB. */
    private int water;
    /** Vrai pendant une séquence automatique (essais) : l'horloge fait elle-même chaque étape, à son heure. */
    private boolean autoSequence;
    /** Ticks passés moteurs à pleine poussée, fusée retenue par ses attaches. */
    private int holdTicks;
    /** Tick du vol où les moteurs s'éteignent faute d'ergols ; 0 si le plein était fait. */
    private int flameoutAt;

    // Étapes en vol. Chaque étape a un bit : 1 pour les gaz, 2 pour la séparation, 4 pour la coiffe, 8 pour la coupure.
    public static final int FLIGHT_THROTTLE = 1;
    public static final int FLIGHT_STAGE = 2;
    public static final int FLIGHT_FAIRING = 4;
    public static final int FLIGHT_CUTOFF = 8;
    /** Fenêtre du passage de la pression maximale, et durée de celles de la séparation et de la coupure, en ticks de vol. */
    /** Tick de vol où s'ouvre la fenêtre du Max-Q : l'écran de vol annonce la décision qui vient. */
    public static int maxQOpenTick() {
        return MAXQ_FROM;
    }

    /** Tick de vol du Max-Q, au milieu de sa fenêtre : pour la frise du centre de contrôle. */
    public static int maxQTick() {
        return (MAXQ_FROM + MAXQ_TO) / 2;
    }

    private static final int MAXQ_FROM = 100;
    private static final int MAXQ_TO = 260;
    /**
     * Manette des gaz : la plus basse poussée des moteurs à ergols liquides, celle qui passe la pression maximale sans
     * fatiguer la structure, et ce qu'elle bouge à chaque tick tant que le pilote tient sa touche (la moitié en une
     * seconde).
     */
    public static final float MIN_THROTTLE = 0.4F;
    public static final float MAXQ_THROTTLE = 0.7F;
    private static final float THROTTLE_RATE = 0.025F;
    private static final int STAGE_WINDOW = 140;
    private static final int CUTOFF_WINDOW = 140;
    /** Étapes faites, et étapes manquées. */
    private int flightDone;
    private int flightMissed;
    /** Nombre de séparations d'étage faites ou manquées jusqu'ici. */
    private int stagesPassed;
    /**
     * Ticks de vol auxquels les moteurs ont été coupés pour chaque séparation, dans l'ordre. Une séparation se
     * déroule en trois temps : les moteurs de l'étage vide se coupent, ses verrous lâchent un instant plus tard, et
     * la fusée continue sur son élan le temps que l'étage s'éloigne ; alors seulement l'étage suivant s'allume.
     */
    private int[] stageCuts = new int[0];
    /** Temps entre la coupure des moteurs et le largage de l'étage, en ticks. */
    private static final int SEPARATION_DELAY = 14;
    /** Écart le plus court entre deux séparations, et entre la fin de la pression maximale et la première, en ticks. */
    private static final int STAGE_GAP = 160;

    /**
     * Vol libre entre la coupure d'un étage et l'allumage du suivant, en ticks. Il dépend du moteur qui s'allume :
     * un moteur à poudre part presque aussitôt, un moteur à kérosène demande deux secondes et demie, et un moteur à
     * hydrogène, qu'il faut d'abord mettre en froid, un peu plus de trois.
     */
    private int coastTicks(int cut) {
        // L'étage qui s'allume après la séparation de rang « cut » : le numéro cut + 2 (la phase des boosters porte le
        // numéro du premier étage, et n'est pas une séparation).
        RocketStats.StageStats next = rocketStats().stages().stream().filter(stage -> stage.number() == cut + 2).findFirst().orElse(null);
        if (next == null) {
            return 50;
        }
        return switch (next.propellant()) {
            case SOLID, XENON, SUNLIGHT -> 30;
            case KEROLOX -> 50;
            case HYDROLOX, HYDROGEN -> 64;
            // Un moteur à méthane se met en froid aussi, moins longtemps qu'un moteur à hydrogène.
            case METHALOX -> 58;
            // Un moteur à fusion : son plasma se rallume en quelques secondes.
            case FUSION -> 70;
        };
    }

    /** Vrai si, à ce tick de vol, la fusée est entre deux étages : moteurs coupés, elle continue sur son élan. */
    public boolean enginesOut(long flightTick) {
        for (int k = 0; k < stageCuts.length; k++) {
            if (flightTick >= stageCuts[k] && flightTick < stageCuts[k] + coastTicks(k)) {
                return true;
            }
        }
        return false;
    }

    /** Vrai si la fusée, en vol, a ses moteurs coupés en ce moment. */
    public boolean enginesOut() {
        return phase == Phase.FLIGHT && hasRocket() && enginesOut(level != null && level.isClientSide() ? phaseTicks() : timer);
    }

    /** Côté serveur : une séparation est en cours, de la coupure à l'allumage de l'étage suivant. */
    private boolean staging() {
        return stageCuts.length > 0 && timer < stageCuts[stageCuts.length - 1] + coastTicks(stageCuts.length - 1);
    }

    /** Commande une séparation : les moteurs de l'étage allumé se coupent, et la suite s'enchaîne d'elle-même. */
    private void beginStaging() {
        stageCuts = java.util.Arrays.copyOf(stageCuts, stageCuts.length + 1);
        stageCuts[stageCuts.length - 1] = timer;
        announce(Component.translatable("sequence.orbitalis.flight.cut", stageCuts.length).withStyle(ChatFormatting.YELLOW));
    }
    /** Paliers perdus par les étapes manquées : la fusée ira moins loin que prévu. */
    private int reachPenalty;
    /** Vrai si la charge utile est perdue (coiffe jamais larguée). */
    private boolean payloadLost;

    // Pilotage. L'attitude de la fusée (son orientation sur ses trois axes) est tenue par RocketAttitude ; l'assiette
    // est l'angle de son nez au-dessus de l'horizon : 90° à la verticale, 0° à plat.
    /** Durée de la montée verticale, le temps de dégager la tour, puis durée du basculement vers la trajectoire, en ticks. */
    private static final double TOWER_CLEAR_TICKS = 70.0;
    private static final double PITCH_OVER_TICKS = 150.0;
    /** Écart au-dessous duquel le pilotage ne coûte rien, en degrés, et vitesse que la fusée peut perdre sans manquer sa destination, en m/s. */
    private static final double FREE_ANGLE = 7.0;
    private static final double LOSS_GRACE = 400.0;
    /** Part de l'inclinaison de la vraie trajectoire que suit la consigne : en fin de vol, le nez reste à 35° au moins. */
    private static final double LEAN = 0.61;
    /** Fatigue que coûte à la structure chaque tick passé à heurter un bloc. */
    private static final int COLLISION_STRESS = 20;
    /** Hors de l'air dense : écart au-delà duquel la poussée, de travers, plie la fusée. */
    public static final double TUMBLE_ANGLE = 50.0;
    /** Durée du vol dans l'air dense, en ticks : au-delà, seul un écart de {@link #TUMBLE_ANGLE} met la fusée en danger. */
    public static final int DENSE_TICKS = MAXQ_TO + 60;
    /** Vrai si la fusée est partie en vrille pendant ce vol. */
    private boolean tumbled;
    /** Vrai si la fusée a heurté quelque chose pendant ce vol. */
    private boolean collided;
    /** Écart d'assiette au-delà duquel la structure souffre dans l'air dense, et durée qu'elle le supporte, en ticks. */
    public static final double STRESS_ANGLE = 30.0;
    public static final int STRESS_LIMIT = 100;
    /** Lance le grondement des moteurs d'une fusée : branché par le client, sans effet sur un serveur. */
    /** Côté client : le bruit du déluge, tant qu'il coule. Branché par le client au démarrage. */
    public static java.util.function.Consumer<LaunchTableBlockEntity> delugeSound = table -> {
    };

    public static java.util.function.Consumer<LaunchTableBlockEntity> engineSound = table -> {
    };

    /** Orientation de la fusée en vol, et sa vitesse de rotation. */
    private final RocketAttitude attitude = new RocketAttitude();

    // Stabilisation (QTE). En vol piloté, deux ou trois fois, une rafale ou une tuyère qui dérive écarte la fusée de sa
    // route : il faut tenir une touche quelques secondes, ou la presser à temps. Réussi, le nez revient ; manqué, la
    // fusée part de travers (l'alerte de décrochage sonne), perd de la vitesse et, dans l'air dense, fatigue sa
    // structure, avant que l'ordinateur ne la redresse. Ils tombent au hasard, jamais pendant une étape du vol.
    /**
     * Fenêtre pour réagir, durée à tenir la touche, et écart le plus court entre deux QTE, en ticks. La fenêtre ne croise
     * jamais celle d'une étape du vol ; seule la suite d'un QTE manqué (la fusée qui se redresse) peut la déborder.
     */
    public static final int QTE_WINDOW = 90;
    private static final int QTE_HOLD = 45;
    private static final int QTE_GAP = 120;
    /** Écart de la fusée pendant le QTE, et après un QTE manqué, en degrés. */
    private static final double QTE_LEAN = 10.0;
    private static final double QTE_FAIL_LEAN = 36.0;
    public static final int QTE_NONE = 0;
    public static final int QTE_ACTIVE = 1;
    public static final int QTE_DONE = 2;
    public static final int QTE_FAILED = 3;
    /** Instants des QTE du vol (ticks de vol), leur touche (0 W, 1 S, 2 A, 3 D) et leur sorte (vrai : tenir la touche). */
    private int[] qteAt;
    private int[] qteKey;
    private boolean[] qteHold;
    private int qteIndex = -1;
    private int qteState;
    private int qteSince;
    private int qteHeld;
    private int qteInputs;
    private boolean qteWasDown;
    private boolean qtePilot;
    private double qteDrift;
    /** Déplacement de la fusée en vol : où sa poussée l'a emmenée, par rapport à la montée droite au-dessus de la table. */
    private final RocketMotion motion = new RocketMotion();
    /**
     * Côté client, pour une fusée sans équipage : le même déplacement, recalculé d'après la trajectoire qu'elle suit
     * (ce tick et le précédent), et le nombre de ticks de vol déjà comptés.
     */
    private final RocketMotion clientMotion = new RocketMotion();
    private final RocketMotion clientMotionLast = new RocketMotion();
    private long clientMotionTicks;
    /** Côté client : étages dont on a vu le largage, et l'heure du monde de chacun. */
    private int clientStagesSeen;
    /** Côté client : la coiffe de ce vol a déjà été larguée (son bruit ne joue qu'une fois). */
    private boolean clientFairingSeen;
    private final long[] clientStageDroppedAt = new long[16];

    /** Côté client : temps écoulé depuis le largage d'un étage, en ticks, ou -1 s'il est encore attaché. */
    /**
     * Temps écoulé depuis le largage des boosters latéraux, en ticks : négatif tant qu'ils poussent, et
     * {@link Float#MAX_VALUE} pour une fusée qui n'en a pas (ou qui n'est pas en vol : ils sont alors des blocs).
     */
    public float boosterAge(float partialTick) {
        if (!hasRocket() || phase != Phase.FLIGHT || rocketShape().boosters() == 0) {
            return Float.MAX_VALUE;
        }
        RocketStats stats = rocketStats();
        int drop = (int) (FlightProfile.gameProgress(stats, stats.stages().get(0).burnTime()) * flightTicks());
        return phaseTicks() + partialTick - drop;
    }

    public float stageAge(int stage, float partialTick) {
        return level != null && stage < clientStagesSeen ? level.getGameTime() - clientStageDroppedAt[stage] + partialTick : -1.0F;
    }
    /** Vitesse perdue à pousser de travers, en m/s. */
    private double steerLoss;
    /** Ticks passés de travers dans l'air dense. */
    private int aeroStress;
    /** Commandes du pilote ce tick, de -1 à 1 : piquer, lacet à droite, roulis à droite. */
    private int pitchInput;
    private int yawInput;
    private int rollInput;
    /** Manette des gaz, de {@link #MIN_THROTTLE} à 1, et ce que le pilote en fait à ce tick (-1, 0 ou 1). */
    private float throttle = 1.0F;
    private int throttleInput;
    /**
     * Vrai quand l'équipe a commandé l'étape « réduire les gaz » (la touche d'étape) : l'ordinateur de bord passe la
     * pression maximale à {@link #MAXQ_THROTTLE}, puis remet les gaz. Toucher la manette lui reprend la main.
     */
    private boolean throttleProgram;
    /** Vrai si un pilote tient les commandes ce tick. */
    private boolean piloted;
    /**
     * Niveau d'aide de l'ordinateur de bord : 0, tout à la main ; 1, assisté (il réduit les gaz et coupe les moteurs
     * lui-même) ; 2, automatique (il fait toutes les étapes du vol, et referme les vannes quand le plein est fait).
     * C'est un réglage du pas de tir : il reste d'un lancement à l'autre.
     */
    private int assist;
    /** Étapes du vol faites à la main : chacune vaut une prime de pilotage. */
    private int manualSteps;
    /** Technologies qui ouvrent les niveaux d'aide, et prime par étape faite à la main, en points de recherche. */
    public static final String RESEARCH_ASSISTED = "gyroscopes";
    public static final String RESEARCH_AUTOMATIC = "flight_computers";
    private static final int PILOT_BONUS = 2;

    public int getAssist() {
        return assist;
    }

    /** Étapes du vol que l'ordinateur de bord fait lui-même, au niveau d'aide réglé. */
    private int assistedSteps() {
        return assist >= 2 ? FLIGHT_THROTTLE | FLIGHT_STAGE | FLIGHT_FAIRING | FLIGHT_CUTOFF : assist == 1 ? FLIGHT_THROTTLE | FLIGHT_CUTOFF : 0;
    }
    /** Accident en cours : la fusée qui se disloque. Il n'est pas sauvegardé : il ne dure que quelques secondes. */
    private RocketBreakup breakup;
    /** Rayon de l'onde de choc si la fusée en vol venait à être perdue, relevé au décollage. */
    private double flightBlast;
    private boolean flightCryogenic;
    /** Heure du monde (en ticks) à laquelle la phase en cours a commencé : les clients en déduisent le temps écoulé. */
    private long phaseStart;
    /** Vrai si l'état a changé depuis le dernier envoi aux clients. */
    private boolean syncPending;
    /** Vrai pendant un relevé de la structure. */
    private boolean refreshing;

    // Côté client : dégagement de la tour et des bras, de 0 (en place) à 1 (tour reculée), animé en douceur.
    private float clearance;
    private float previousClearance;
    private Direction trenchDirection = Direction.EAST;
    private final int[] mastHeights = new int[4];
    /** Vrai tant que les balises des mâts déposent leur lumière. */
    private boolean beaconsOn;
    /** Puissance des projecteurs des mâts côté client, de 0 à 1, et sa valeur au tick précédent. */
    private float lampGlow;
    private float previousLampGlow;

    /** Puissance des projecteurs des mâts, de 0 (éteints) à 1, lissée entre deux ticks. */
    public float lampGlow(float partialTick) {
        return Mth.lerp(partialTick, previousLampGlow, lampGlow);
    }
    /** Planchers des passerelles d'embarquement posés par cette table (côté serveur). */
    private final Set<BlockPos> towerSteps = new HashSet<>();
    /** Vrai une fois que les planchers laissés par une session précédente ont été retrouvés. */
    private boolean towerStepsFound;
    /** Niveaux des portiques d'embarquement de la tour, relevés avec la structure (des deux côtés). */
    private int[] gateLevels = new int[0];
    /** Côté client : effets visuels et sonores du lancement en cours. */
    private final LaunchEffects effects = new LaunchEffects();
    /**
     * Plan de la fusée à bâtir sur ce pas de tir, déposé depuis une feuille sortie de l'ordinateur de conception ;
     * vide s'il n'y en a pas. Il reste en place d'un lancement à l'autre : on rebâtit la même fusée.
     */
    private ItemStack rocketPlan = ItemStack.EMPTY;
    /**
     * Vrai quand la fusée a été défaite à la demande du joueur : tous ses blocs sont en place, mais elle ne se
     * reforme pas d'elle-même. Elle se reformera dès qu'un bloc aura été retiré puis reposé.
     */
    private boolean formPaused;
    /** Vrai pendant qu'une fusée se défait : ses blocs reviennent un à un, il ne faut pas la reformer en chemin. */
    private boolean unforming;

    /**
     * Bloc à montrer en jeu parce qu'il manque encore : sa position par rapport à la table, et son aspect.
     */
    public record Ghost(BlockPos offset, BlockState state) {
    }

    /** Côté client : blocs des plans (pas de tir, puis fusée) à montrer en jeu. */
    private List<Ghost> ghosts = List.of();
    /** Nombre de blocs fantômes montrés à la fois, au plus : au-delà, l'affichage ralentirait. */
    private static final int MAX_GHOSTS = 8000;

    public LaunchTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LAUNCH_TABLE.get(), pos, state);
    }

    // ------------------------------------------------------------------
    // Registre des tables chargées
    // ------------------------------------------------------------------

    /** Positions des tables de lancement chargées d'un monde. */
    public static synchronized Set<BlockPos> loadedTables(Level level) {
        Set<BlockPos> tables = TABLES.get(level);
        return tables == null ? Set.of() : Set.copyOf(tables);
    }

    private static synchronized void track(Level level, BlockPos pos, boolean loaded) {
        Set<BlockPos> tables = TABLES.computeIfAbsent(level, key -> Collections.synchronizedSet(new HashSet<>()));
        if (loaded) {
            tables.add(pos.immutable());
        } else {
            tables.remove(pos);
        }
    }

    /** Table de lancement chargée la plus proche d'une position, dans un rayon donné, ou {@code null}. */
    public static LaunchTableBlockEntity nearest(Level level, BlockPos from, double range) {
        LaunchTableBlockEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (BlockPos tablePos : loadedTables(level)) {
            double distance = Math.sqrt(tablePos.distSqr(from));
            if (distance <= range && distance < nearestDistance && level.getBlockEntity(tablePos) instanceof LaunchTableBlockEntity table) {
                nearest = table;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    // ------------------------------------------------------------------
    // Synchronisation avec les clients
    // ------------------------------------------------------------------

    /** Envoie l'état de la table aux joueurs qui la voient : leur rendu (tour, bras, écrans) en dépend. */
    private void sync() {
        syncPending = false;
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    /** Plan du pas de tir, ou {@code null} si la table n'en a pas encore reçu. */
    public PadPlan getPlan() {
        return plan;
    }

    /** Confie un plan à la table : elle montre aussitôt en jeu les blocs à poser. */
    public void setPlan(PadPlan newPlan) {
        plan = newPlan;
        if (level != null && !level.isClientSide()) {
            refreshNow(level);
            sync();
        }
    }

    /**
     * Côté client : tous les blocs du plan qui manquent encore, à montrer en jeu, du bas vers le haut. Qu'un seul
     * bloc soit cassé dans un pas de tir achevé, et il réapparaît ici.
     */
    public List<Ghost> getGhosts() {
        return ghosts;
    }

    // ------------------------------------------------------------------
    // Fusée à bâtir
    // ------------------------------------------------------------------

    /** Vrai si un plan de fusée est déposé sur ce pas de tir. */
    public boolean hasRocketPlan() {
        return !rocketPlan.isEmpty();
    }

    /** Blocs à poser pour bâtir la fusée du plan déposé, par rapport à la table. */
    public List<RocketPlan.Piece> rocketPieces() {
        return rocketPlan.isEmpty() ? List.of()
                : RocketPlan.pieces(RocketBlueprintItem.design(rocketPlan), plan == null ? Direction.EAST : plan.facing());
    }

    /**
     * Bloc de ravitaillement qu'exige la fusée du plan déposé.
     *
     * @param offset sa place, par rapport à la table : au pied de la tour, sur son flanc
     * @param kind   l'ergol qu'il doit laisser passer
     */
    public record PortNeed(BlockPos offset, FuelPortBlock.Kind kind) {
    }

    /**
     * Blocs de ravitaillement qu'exige la fusée du plan déposé : un par ergol qu'elle embarque. Chacun a sa place
     * au pied de la tour, à la place d'un bloc de charpente : le kérosène près de la fusée, puis l'oxygène liquide,
     * puis l'hydrogène liquide, et le méthane liquide au coin arrière. Une fusée à poudre n'en demande aucun.
     */
    public List<PortNeed> requiredPorts() {
        if (rocketPlan.isEmpty() || plan == null) {
            return List.of();
        }
        RocketFuel fuel = RocketFuel.required(RocketStats.of(RocketBlueprintItem.design(rocketPlan)));
        List<PortNeed> needs = new ArrayList<>();
        int radius = plan.radius();
        if (fuel.rp1() > 0) {
            needs.add(new PortNeed(plan.at(-radius - 2, -1, 2), FuelPortBlock.Kind.RP1));
        }
        if (fuel.oxygen() > 0) {
            needs.add(new PortNeed(plan.at(-radius - 3, -1, 2), FuelPortBlock.Kind.LOX));
        }
        if (fuel.hydrogen() > 0) {
            needs.add(new PortNeed(plan.at(-radius - 4, -1, 2), FuelPortBlock.Kind.LH2));
        }
        if (fuel.methane() > 0) {
            needs.add(new PortNeed(plan.at(-radius - 5, -1, 2), FuelPortBlock.Kind.CH4));
        }
        if (fuel.helium3() > 0) {
            needs.add(new PortNeed(plan.at(-radius - 5, -1, -2), FuelPortBlock.Kind.HE3));
        }
        return needs;
    }

    /** Blocs de ravitaillement exigés par la fusée qui ne sont pas encore en place. */
    public List<PortNeed> missingPorts(Level level) {
        List<PortNeed> missing = new ArrayList<>();
        for (PortNeed need : requiredPorts()) {
            BlockState state = level.getBlockState(worldPosition.offset(need.offset()));
            if (!state.is(ModBlocks.FUEL_PORT.get()) || state.getValue(FuelPortBlock.KIND) != need.kind()) {
                missing.add(need);
            }
        }
        return missing;
    }

    /** Règle sur le bon ergol les blocs de ravitaillement déjà posés à leur place : seul le bloc compte pour le joueur. */
    private void tunePorts(Level level) {
        // Le bloc d'eau du déluge, lui, est toujours réglé sur l'eau.
        if (plan != null && plan.waterPort() != null) {
            BlockPos pos = worldPosition.offset(plan.waterPort());
            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.FUEL_PORT.get()) && state.getValue(FuelPortBlock.KIND) != FuelPortBlock.Kind.WATER) {
                level.setBlock(pos, state.setValue(FuelPortBlock.KIND, FuelPortBlock.Kind.WATER), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
        for (PortNeed need : requiredPorts()) {
            BlockPos pos = worldPosition.offset(need.offset());
            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.FUEL_PORT.get()) && state.getValue(FuelPortBlock.KIND) != need.kind()) {
                level.setBlock(pos, state.setValue(FuelPortBlock.KIND, need.kind()), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }

    // ------------------------------------------------------------------
    // Pose par couche
    // ------------------------------------------------------------------

    /** Retire un bloc de l'inventaire d'un joueur. Renvoie faux s'il n'en a pas ; en mode créatif, rien n'est retiré. */
    /** Prend dans l'inventaire du joueur autant de cette pièce ; pour un coût nul, il suffit d'en avoir une. */
    private static boolean takeItems(Player player, net.minecraft.world.item.Item item, int count) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        int have = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                have += stack.getCount();
            }
        }
        if (have < Math.max(1, count)) {
            return false;
        }
        int left = count;
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
        }
        return true;
    }

    /**
     * L'état du chantier de la fusée, pour l'écran de chantier : le plan, ce qui est déjà posé, les raccords qui
     * manquent. Sans plan en attente (fusée formée, plan repris), le plan envoyé est vide : l'écran se ferme.
     */
    public com.orbitalis.core.network.ConstructionPayload construction(boolean open) {
        if (level == null || rocketPlan.isEmpty() || hasRocket() || plan == null) {
            return new com.orbitalis.core.network.ConstructionPayload(open, worldPosition, ItemStack.EMPTY, 0, List.of(), List.of());
        }
        List<Boolean> placed = new ArrayList<>();
        for (RocketPlan.Piece piece : rocketPieces()) {
            placed.add(level.getBlockState(worldPosition.offset(piece.offset())).is(piece.block()));
        }
        List<Integer> ports = new ArrayList<>();
        for (PortNeed need : missingPorts(level)) {
            ports.add(need.kind().ordinal());
        }
        return new com.orbitalis.core.network.ConstructionPayload(open, worldPosition, rocketPlan.copy(), plan.facing().get2DDataValue(),
                List.copyOf(placed), List.copyOf(ports));
    }

    /**
     * Copie le plan de la fusée dressée sur cette table, pour le joueur. Comme une impression, elle coûte une feuille
     * de papier et la charge utile de la fusée, qui partira avec la copie. Renvoie ce qui s'y oppose, ou {@code null}.
     */
    public Component copyPlan(net.minecraft.server.level.ServerPlayer player) {
        if (rocketPlan.isEmpty() || !hasRocket()) {
            return Component.translatable("spaceport.orbitalis.pad.copy_none");
        }
        net.minecraft.core.NonNullList<ItemStack> parts = RocketBlueprintItem.parts(rocketPlan);
        ItemStack cargo = parts.get(com.orbitalis.phase1.rocket.RocketDesign.SLOT_PAYLOAD);
        if (!player.getAbilities().instabuild) {
            if (!hasItems(player, net.minecraft.world.item.Items.PAPER) || (!cargo.isEmpty() && !hasItems(player, cargo.getItem()))) {
                return cargo.isEmpty() ? Component.translatable("design.orbitalis.print.need_paper")
                        : Component.translatable("design.orbitalis.print.need_payload", cargo.getHoverName());
            }
            takeItems(player, net.minecraft.world.item.Items.PAPER, 1);
            if (!cargo.isEmpty()) {
                takeItems(player, cargo.getItem(), 1);
            }
        }
        Component name = com.orbitalis.phase1.rocket.DesignComputerBlock.planName(com.orbitalis.phase1.rocket.RocketDraft.of(parts));
        ItemStack copy = RocketBlueprintItem.create(parts, Component.translatable("item.orbitalis.rocket_blueprint.named", name));
        if (!player.getInventory().add(copy)) {
            player.drop(copy, false);
        }
        return null;
    }

    private static boolean hasItems(Player player, net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(item)) {
                return true;
            }
        }
        return false;
    }

    /** Ce qu'a donné un geste du bras d'assemblage. */
    public enum ArmResult {
        /** Un bloc a été posé. */
        PLACED,
        /** Il manque la pièce du prochain bloc. */
        MISSING,
        /** La place du prochain bloc est prise par autre chose. */
        BLOCKED,
        /** Rien à bâtir. */
        IDLE
    }

    /** Un geste du bras d'assemblage : ce qu'il a donné, et la pièce qui manque s'il en manque une. */
    public record ArmStep(ArmResult result, net.minecraft.world.item.Item missing) {
    }

    /**
     * Le bras d'assemblage pose le prochain bloc du plan : un raccord de ravitaillement qui manque, sinon le plus bas
     * des blocs de la fusée qui manquent. Il prend dans ces inventaires ce que coûte ce bloc, tout ou rien.
     */
    public ArmStep placeNext(List<net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource>> sources) {
        if (level == null || rocketPlan.isEmpty() || hasRocket() || phase != Phase.IDLE || !isOperational()) {
            return new ArmStep(ArmResult.IDLE, null);
        }
        for (PortNeed need : missingPorts(level)) {
            BlockPos pos = worldPosition.offset(need.offset());
            BlockState there = level.getBlockState(pos);
            if (!there.canBeReplaced() && !there.is(ModBlocks.TOWER_FRAME.get())) {
                return new ArmStep(ArmResult.BLOCKED, null);
            }
            if (!takeFrom(sources, ModBlocks.FUEL_PORT.get().asItem(), 1)) {
                return new ArmStep(ArmResult.MISSING, ModBlocks.FUEL_PORT.get().asItem());
            }
            if (there.is(ModBlocks.TOWER_FRAME.get())) {
                Block.popResource(level, pos, new ItemStack(ModBlocks.TOWER_FRAME.get()));
            }
            level.setBlock(pos, ModBlocks.FUEL_PORT.get().defaultBlockState().setValue(FuelPortBlock.KIND, need.kind()), Block.UPDATE_ALL);
            level.playSound(null, pos, ModSounds.PART_PLACED.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            return new ArmStep(ArmResult.PLACED, null);
        }
        List<RocketPlan.Piece> pieces = rocketPieces();
        int lowest = Integer.MAX_VALUE;
        for (RocketPlan.Piece piece : pieces) {
            if (!level.getBlockState(worldPosition.offset(piece.offset())).is(piece.block())) {
                lowest = Math.min(lowest, piece.offset().getY());
            }
        }
        boolean blocked = false;
        for (RocketPlan.Piece piece : pieces) {
            BlockPos pos = worldPosition.offset(piece.offset());
            if (piece.offset().getY() != lowest || level.getBlockState(pos).is(piece.block())) {
                continue;
            }
            if (!isFreeForRocket(level.getBlockState(pos))) {
                blocked = true;
                continue;
            }
            if (!takeFrom(sources, piece.source(), piece.count())) {
                return new ArmStep(ArmResult.MISSING, piece.source());
            }
            level.setBlock(pos, piece.block().defaultBlockState(), Block.UPDATE_ALL);
            level.playSound(null, pos, ModSounds.PART_PLACED.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            return new ArmStep(ArmResult.PLACED, null);
        }
        return new ArmStep(blocked ? ArmResult.BLOCKED : ArmResult.IDLE, null);
    }

    /** Prend autant de cette pièce dans ces inventaires, tout ou rien ; un coût nul ne prend rien. */
    private static boolean takeFrom(List<net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource>> sources,
            net.minecraft.world.item.Item item, int count) {
        if (count <= 0) {
            return true;
        }
        try (net.neoforged.neoforge.transfer.transaction.Transaction transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            int left = count;
            for (var source : sources) {
                left -= source.extract(net.neoforged.neoforge.transfer.item.ItemResource.of(item), left, transaction);
                if (left <= 0) {
                    transaction.commit();
                    return true;
                }
            }
        }
        return false;
    }

    /** Un bloc du plan d'une fusée en chantier, et la table qui porte ce plan. */
    public record PlannedPiece(LaunchTableBlockEntity table, RocketPlan.Piece piece) {
    }

    /**
     * Le bloc que prévoit, à cette place, le plan d'une fusée en chantier sur une table voisine ; {@code null} s'il n'y
     * en a pas (pas de plan, fusée déjà formée, ou place hors du plan).
     */
    public static PlannedPiece plannedPieceAt(Level level, BlockPos pos) {
        int reach = LaunchComplex.REACH;
        for (BlockPos tablePos : loadedTables(level)) {
            if (Math.abs(pos.getX() - tablePos.getX()) > reach || Math.abs(pos.getZ() - tablePos.getZ()) > reach
                    || !(level.getBlockEntity(tablePos) instanceof LaunchTableBlockEntity table) || table.rocketPlan.isEmpty() || table.hasRocket()) {
                continue;
            }
            for (RocketPlan.Piece piece : table.rocketPieces()) {
                if (tablePos.offset(piece.offset()).equals(pos)) {
                    return new PlannedPiece(table, piece);
                }
            }
        }
        return null;
    }

    /**
     * Ce que rend un bloc de ce plan quand on le retire : ce qu'il a coûté pour un plan imprimé ; une pièce pour une
     * fusée bâtie librement, dont chaque bloc a été posé un à un.
     */
    public int refundOf(RocketPlan.Piece piece) {
        return freePlan ? 1 : piece.count();
    }

    private static boolean takeBlock(Player player, Block block) {
        if (player.isCreative()) {
            return true;
        }
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(block.asItem())) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    /**
     * Pose d'un coup, avec les blocs que le joueur a sur lui, la couche la plus basse où il manque quelque chose
     * (celle que le plan cerne de jaune), ou à défaut la première au-dessus où il peut poser un bloc. Tant que le pas de tir est incomplet, c'est une couche du pas de tir ;
     * ensuite, si un plan de fusée est déposé, une couche de la fusée (et ses blocs de ravitaillement).
     * <p>
     * Chaque bloc posé est pris dans l'inventaire : rien n'est créé. Une case occupée par autre chose est laissée
     * telle quelle. Le portique d'embarquement, fait de neuf cases, est dressé en entier pour un seul objet.
     *
     * @return le nombre de blocs posés
     */
    public int buildLayer(Player player) {
        int placed = buildLayer(player, false);
        return placed;
    }

    /**
     * Comme {@link #buildLayer(Player)} ; en silence, la pose ne fait pas de bruit : celui qui pose plusieurs couches
     * d'un coup joue le sien une seule fois, à la fin (voir {@link #placedSound()}).
     */
    public int buildLayer(Player player, boolean quiet) {
        if (level == null || plan == null || phase != Phase.IDLE) {
            return 0;
        }
        int placed = 0;
        List<PadPlan.Piece> missing = LaunchComplex.missingPieces(level, worldPosition, plan);
        if (!missing.isEmpty()) {
            // De bas en haut : la première couche où l'on parvient à poser quelque chose. Une couche où rien ne peut
            // l'être (case occupée par autre chose, bloc que le joueur n'a pas) ne bloque pas les suivantes.
            java.util.SortedSet<Integer> layers = new java.util.TreeSet<>();
            for (PadPlan.Piece piece : missing) {
                layers.add(piece.offset().getY());
            }
            boolean gateBuilt = false;
            for (int layer : layers) {
                for (PadPlan.Piece piece : missing) {
                    BlockPos pos = worldPosition.offset(piece.offset());
                    if (piece.offset().getY() == layer && piece.block() == ModBlocks.FUEL_PORT.get()
                            && level.getBlockState(pos).is(ModBlocks.TOWER_FRAME.get()) && takeBlock(player, piece.block())) {
                        // Sur un pas de tir bâti avant le déluge à eau : le bloc de charpente est repris, et rendu.
                        if (!player.isCreative()) {
                            player.getInventory().placeItemBackInInventory(new ItemStack(ModBlocks.TOWER_FRAME.get()));
                        }
                        level.setBlock(pos, piece.display(), Block.UPDATE_ALL);
                        placed++;
                        continue;
                    }
                    if (piece.offset().getY() != layer || !level.getBlockState(pos).canBeReplaced()) {
                        continue;
                    }
                    if (piece.block() == ModBlocks.BOARDING_GATE.get()) {
                        // Les neuf cases du portique se dressent ensemble, pour un seul objet.
                        boolean free = true;
                        for (BlockPos cell : plan.gateCells()) {
                            BlockState there = level.getBlockState(worldPosition.offset(cell));
                            free &= there.canBeReplaced() || there.is(ModBlocks.BOARDING_GATE.get());
                        }
                        if (!gateBuilt && free && takeBlock(player, piece.block())) {
                            gateBuilt = true;
                            ModBlocks.BOARDING_GATE.get().place(level, worldPosition.offset(plan.riserBase()).above(plan.gate()), plan.facing());
                            placed++;
                        }
                    } else if (takeBlock(player, piece.block())) {
                        level.setBlock(pos, piece.display(), Block.UPDATE_ALL);
                        placed++;
                    }
                }
                if (placed > 0) {
                    break;
                }
            }
        } else if (!rocketPlan.isEmpty() && !hasRocket() && isOperational()) {
            for (PortNeed need : missingPorts(level)) {
                BlockPos pos = worldPosition.offset(need.offset());
                BlockState there = level.getBlockState(pos);
                // Le bloc de ravitaillement remplace un bloc de charpente du pied de la tour, qui est rendu.
                if ((there.canBeReplaced() || there.is(ModBlocks.TOWER_FRAME.get())) && takeBlock(player, ModBlocks.FUEL_PORT.get())) {
                    if (there.is(ModBlocks.TOWER_FRAME.get()) && !player.isCreative()) {
                        player.getInventory().placeItemBackInInventory(new ItemStack(ModBlocks.TOWER_FRAME.get()));
                    }
                    level.setBlock(pos, ModBlocks.FUEL_PORT.get().defaultBlockState().setValue(FuelPortBlock.KIND, need.kind()), Block.UPDATE_ALL);
                    placed++;
                }
            }
            int lowest = Integer.MAX_VALUE;
            List<RocketPlan.Piece> pieces = rocketPieces();
            for (RocketPlan.Piece piece : pieces) {
                if (!level.getBlockState(worldPosition.offset(piece.offset())).is(piece.block())) {
                    lowest = Math.min(lowest, piece.offset().getY());
                }
            }
            for (RocketPlan.Piece piece : pieces) {
                BlockPos pos = worldPosition.offset(piece.offset());
                if (piece.offset().getY() == lowest && isFreeForRocket(level.getBlockState(pos)) && takeItems(player, piece.source(), piece.count())) {
                    level.setBlock(pos, piece.block().defaultBlockState(), Block.UPDATE_ALL);
                    placed++;
                }
            }
        }
        if (placed > 0) {
            if (!quiet) {
                placedSound();
            }
            refresh(level);
            sync();
        }
        return placed;
    }

    /** Le « clang » d'une pose de blocs sur ce pas de tir. */
    public void placedSound() {
        if (level != null) {
            level.playSound(null, worldPosition, ModSounds.PART_PLACED.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    /**
     * Silhouette de la fusée que porte la table, ou de celle que son plan annonce ; {@code null} s'il n'y en a pas.
     * Les bras et la passerelle s'y ajustent dès que le plan est déposé, avant même que la fusée soit bâtie.
     */
    public RocketShape expectedShape() {
        if (hasRocket()) {
            return rocketShape();
        }
        if (rocketPlan.isEmpty()) {
            return null;
        }
        if (plannedShapeOf != rocketPlan || plannedShape == null) {
            plannedShape = RocketShape.of(RocketBlueprintItem.design(rocketPlan));
            plannedShapeOf = rocketPlan;
        }
        return plannedShape;
    }

    private RocketShape plannedShape;
    private ItemStack plannedShapeOf = ItemStack.EMPTY;

    /**
     * La table qui commande une case de tablier : celle dont le plan compte cette case parmi ses dalles ou son
     * carneau. {@code null} si la case n'appartient à aucun pas de tir.
     */
    public static LaunchTableBlockEntity ownerOfDeck(Level level, BlockPos deck) {
        LaunchTableBlockEntity table = nearest(level, deck, LaunchComplex.MAX_RADIUS * 1.5);
        if (table == null || table.plan == null || deck.getY() != table.worldPosition.getY()) {
            return null;
        }
        int radius = table.plan.radius();
        return Math.abs(deck.getX() - table.worldPosition.getX()) <= radius && Math.abs(deck.getZ() - table.worldPosition.getZ()) <= radius
                ? table : null;
    }

    /**
     * Étage de la cabine de la fusée posée sur la table, ou de celle du plan déposé ; -1 s'il n'y en a pas, ou si
     * elle n'emporte pas d'équipage.
     */
    private int capsuleLevel() {
        RocketShape wanted = expectedShape();
        int capsule = wanted == null ? -1 : RocketShape.cabinIndex(wanted.parts());
        // Le tronçon d'indice i est i + 1 blocs au-dessus de la table, donc à l'étage i + 2 de la tour, qui part du sol.
        return capsule < 0 ? -1 : capsule + 2;
    }

    /** Étage où brancher le bras ombilical sur une fusée : le milieu de son groupe de réservoirs le plus haut. */
    private static int armLevelFor(RocketShape shape) {
        int end = -1;
        for (int i = shape.height() - 1; i >= 0 && end < 0; i--) {
            if (shape.parts().get(i).name().startsWith("TANK")) {
                end = i;
            }
        }
        if (end < 0) {
            return shape.height() / 2 + 2;
        }
        int start = end;
        while (start > 0 && shape.parts().get(start - 1).name().startsWith("TANK")) {
            start--;
        }
        // Le tronçon d'indice i est à l'étage i + 2 de la tour.
        return (start + end) / 2 + 2;
    }

    /**
     * Le portique d'embarquement suit la fusée : dès qu'une fusée habitée est posée (ou son plan déposé), il se
     * place à la hauteur de sa cabine. S'il est déjà bâti, la table l'y déplace elle-même, en échangeant ses neuf
     * cases avec celles de la colonne et de la charpente ; sinon, c'est le plan qui change, et montre le portique
     * à sa nouvelle place.
     */
    private void moveGate(Level level) {
        RocketShape expected = expectedShape();
        if (expected == null || phase != Phase.IDLE) {
            return;
        }
        // Le portique va à la hauteur de la cabine, s'il y en a une ; le bras, au milieu des réservoirs du dernier
        // étage de la fusée, là où se branchent ses ergols.
        int capsule = capsuleLevel();
        PadPlan moved = plan.withGate(capsule < 0 ? plan.gate() : capsule).withArm(armLevelFor(expected));
        if (moved.equals(plan)) {
            return;
        }
        // Si le pas de tir est bâti, la table réarrange elle-même ses blocs : le portique, la colonne et la charpente
        // échangent leurs places, le bras suit, et l'escalier, qui est calé sur le portique, tourne avec lui. Sinon,
        // seul le plan change, et montre ce qu'il reste à poser.
        boolean built = LaunchComplex.missingPieces(level, worldPosition, plan).isEmpty();
        PadPlan old = plan;
        plan = moved;
        if (built) {
            Map<BlockPos, PadPlan.Piece> wanted = new java.util.HashMap<>();
            for (PadPlan.Piece piece : moved.pieces()) {
                wanted.put(piece.offset(), piece);
            }
            // D'abord ce qui n'a plus sa place (l'ancien portique part en entier, d'un seul tenant), puis ce qui manque.
            for (PadPlan.Piece piece : old.pieces()) {
                BlockPos pos = worldPosition.offset(piece.offset());
                BlockState state = level.getBlockState(pos);
                PadPlan.Piece now = wanted.get(piece.offset());
                boolean stays = now != null && now.accepts(state) && !state.is(ModBlocks.BOARDING_GATE.get());
                if (!stays && piece.accepts(state)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            for (PadPlan.Piece piece : moved.pieces()) {
                BlockPos pos = worldPosition.offset(piece.offset());
                if (!piece.accepts(level.getBlockState(pos))) {
                    level.setBlock(pos, piece.display(), Block.UPDATE_ALL);
                }
            }
            level.playSound(null, worldPosition.offset(moved.riserBase()).above(moved.gate()), ModSounds.PAD_HYDRAULIC.get(), SoundSource.BLOCKS, 1.5F, 1.15F);
        }
        setChanged();
        sync();
    }

    /**
     * Projecteurs des mâts. Sur un pas de tir en service, ils sont tournés vers la fusée, allumés à la tombée de la
     * nuit et pendant les lancements, éteints au matin. Leur faisceau est relancé à chaque relevé : la lumière
     * revient là où une fusée vient de partir. Un pas de tir qui n'est plus en service éteint les siens.
     */
    private void updateFloodlights(Level level) {
        if (plan == null) {
            return;
        }
        boolean operational = isOperational();
        boolean lit = operational && (phase != Phase.IDLE || level.isDarkOutside());
        int radius = plan.radius();
        for (int dx = -radius; dx <= radius; dx += 2 * radius) {
            for (int dz = -radius; dz <= radius; dz += 2 * radius) {
                BlockPos pos = worldPosition.offset(dx, plan.mastHeight() - 1, dz);
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof FloodlightBlock)) {
                    continue;
                }
                // À sa place sur un mât, un projecteur vise toujours la fusée, dans quelque sens qu'on l'ait posé.
                BlockState wanted = FloodlightBlock.aimed(state, -Integer.signum(dx), -Integer.signum(dz));
                if (operational) {
                    wanted = wanted.setValue(FloodlightBlock.LIT, lit);
                } else if (state.getValue(FloodlightBlock.LIT) && !level.hasNeighborSignal(pos)) {
                    wanted = wanted.setValue(FloodlightBlock.LIT, false);
                }
                if (wanted != state) {
                    level.setBlock(pos, wanted, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                } else if (lit) {
                    FloodlightBlock.shine(level, pos, state);
                }
            }
        }
    }

    /**
     * Balises des mâts : leur lumière clignote pour de bon, au rythme de l'éclat que l'on voit à leur pointe. Un pas
     * de tir qui cesse d'être en service reprend toute la lumière qu'il avait déposée.
     */
    private void tickBeacons(ServerLevel level) {
        if (plan == null || !isOperational() || plan.compact()) {
            if (beaconsOn) {
                beaconsOn = false;
                clearLights(level);
            }
            return;
        }
        beaconsOn = true;
        int radius = plan.radius();
        long time = level.getGameTime();
        int corner = 0;
        // Même ordre que l'affichage : chaque lumière bat avec la balise qu'on voit.
        for (int dz = -radius; dz <= radius; dz += 2 * radius) {
            for (int dx = -radius; dx <= radius; dx += 2 * radius) {
                corner++;
                PadLights.beacon(level, worldPosition.offset(dx, plan.mastHeight() + 1, dz),
                        LedDriver.beaconLight(LedDriver.beaconCurrent(time, corner, phase != Phase.IDLE)));
            }
        }
    }

    /** Reprend la lumière des balises et des projecteurs, dans tout le volume du tablier et de ses mâts. */
    private void clearLights(Level level) {
        int reach = LaunchComplex.MAX_RADIUS;
        PadLights.clear(level, worldPosition.offset(-reach, 0, -reach),
                worldPosition.offset(reach, LaunchComplex.requiredMastHeight(LaunchComplex.MAX_RADIUS) + 2, reach));
    }

    /**
     * Dépose un plan de fusée sur le pas de tir. Renvoie ce qui s'y oppose, ou {@code null} si le plan est accepté :
     * les blocs à poser apparaissent alors au-dessus de la table.
     */
    public Component applyBlueprint(ItemStack blueprint) {
        ItemStack candidate = RocketItem.create(RocketBlueprintItem.parts(blueprint));
        RocketStats candidateStats = RocketStats.of(RocketItem.design(candidate));
        if (!isOperational()) {
            return Component.translatable("spaceport.orbitalis.table.not_operational");
        }
        if (hasRocket() || hasRocketPlan() || phase != Phase.IDLE) {
            return Component.translatable("spaceport.orbitalis.table.occupied");
        }
        if (!candidateStats.isFlightReady()) {
            return Component.translatable("spaceport.orbitalis.table.rocket_invalid");
        }
        Component testPad = testPadProblem(candidate);
        if (testPad != null) {
            return testPad;
        }
        if (candidateStats.liftoffMass() > complex.maxRocketMass()) {
            return Component.translatable("spaceport.orbitalis.table.rocket_too_heavy", (int) (complex.maxRocketMass() / 1000));
        }
        if (!isHeightAllowedFor(candidate)) {
            return Component.translatable("spaceport.orbitalis.table.rocket_too_tall",
                    RocketShape.of(RocketItem.design(candidate)).height(), complex.maxRocketHeight());
        }
        rocketPlan = blueprint.copyWithCount(1);
        formPaused = false;
        sync();
        return null;
    }

    /** Retire le plan de fusée du pas de tir et le renvoie ; impossible tant que la fusée est bâtie ou en vol. */
    public ItemStack takeBlueprint() {
        if (hasRocket() || phase != Phase.IDLE || rocketPlan.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = rocketPlan;
        rocketPlan = ItemStack.EMPTY;
        // Repris en main, c'est un plan comme un autre : la table ne le remet plus en cause.
        freePlan = false;
        sync();
        return taken;
    }

    // ------------------------------------------------------------------
    // Construction libre
    // ------------------------------------------------------------------

    /** Charge utile confiée à la table pour une fusée bâtie librement, sans plan. */
    private ItemStack freeCargo = ItemStack.EMPTY;
    /** Vrai si le plan déposé a été déduit par la table d'une fusée bâtie librement. */
    private boolean freePlan;
    /** Dernier relevé dit aux joueurs : on ne le répète pas tant qu'il ne change pas. */
    private String lastReadout = "";

    public ItemStack getFreeCargo() {
        return freeCargo;
    }

    /**
     * Confie une charge utile à la table, pour la fusée qu'on y bâtit librement. Renvoie ce qui s'y oppose, ou
     * {@code null} si elle est prise.
     */
    public Component offerCargo(ItemStack cargo) {
        if (!isOperational()) {
            return Component.translatable("spaceport.orbitalis.table.not_operational");
        }
        if (hasRocket() || hasRocketPlan() || phase != Phase.IDLE || !freeCargo.isEmpty()) {
            return Component.translatable("spaceport.orbitalis.table.occupied");
        }
        // Le pas de tir d'essai n'a pas de passerelle : pas de capsule habitée.
        if (isTestPad() && RocketParts.isCrewCabin(cargo)) {
            return Component.translatable("spaceport.orbitalis.table.test_pad_crew");
        }
        freeCargo = cargo.copyWithCount(1);
        lastReadout = "";
        if (level != null) {
            refresh(level);
        }
        sync();
        return null;
    }

    /** Reprend la charge utile confiée à la table, tant qu'aucune fusée ne l'a prise. */
    public ItemStack takeFreeCargo() {
        ItemStack taken = freeCargo;
        freeCargo = ItemStack.EMPTY;
        if (!taken.isEmpty()) {
            sync();
        }
        return taken;
    }

    /**
     * Construction libre : sans plan déposé, la table lit ce qui est empilé dans son axe ({@link RocketScan}). Elle
     * dit aux joueurs proches ce que vaut la fusée en l'état et ce qui lui manque ; dès que la colonne fait une fusée
     * complète, elle en tire elle-même le plan et le dépose : la suite (couronne d'une fusée large, blocs de
     * ravitaillement, formation de la fusée) se passe comme avec un plan imprimé.
     */
    private void tryFreeBuild(Level level) {
        if (hasRocket() || phase != Phase.IDLE || !isOperational() || unforming || plan == null) {
            return;
        }
        if (!rocketPlan.isEmpty()) {
            if (!freePlan || !contradictsPlan(level)) {
                return;
            }
            // Le joueur a changé la colonne : le plan que la table en avait tiré ne vaut plus. Elle relit tout,
            // et garde la charge utile qu'on lui avait confiée.
            if (freeCargo.isEmpty()) {
                freeCargo = RocketBlueprintItem.design(rocketPlan).payload().copy();
            }
            rocketPlan = ItemStack.EMPTY;
            freePlan = false;
            lastReadout = "";
            sync();
        }
        com.orbitalis.phase1.rocket.RocketScan.Reading reading =
                com.orbitalis.phase1.rocket.RocketScan.read(level, worldPosition, plan.facing(), freeCargo);
        if (reading == null) {
            lastReadout = "";
            return;
        }
        Component message = reading.message();
        boolean good = false;
        if (reading.slots() != null) {
            net.minecraft.world.entity.player.Player builder = level.getNearestPlayer(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5, 32.0, false);
            String locked = builder == null ? null : com.orbitalis.phase1.rocket.DesignComputerBlock.lockedResearch(builder, reading.slots());
            // Une fusée libre n'emploie que des pièces étudiées à l'établi d'ingénieur.
            com.orbitalis.core.research.EngineeringStudies.Study unstudied = builder == null || locked != null ? null
                    : com.orbitalis.core.research.EngineeringStudies.firstUnstudied(builder, reading.slots());
            Component problem = locked != null
                    ? Component.translatable("free.orbitalis.locked", Component.translatable("research.orbitalis.node." + locked))
                    : unstudied != null ? Component.translatable("engineering.orbitalis.required", unstudied.name())
                    : applyBlueprint(RocketBlueprintItem.create(reading.slots(), Component.translatable("free.orbitalis.name")));
            if (problem == null) {
                freePlan = true;
                freeCargo = ItemStack.EMPTY;
                good = true;
                sync();
            } else {
                message = problem;
            }
        }
        String said = message.getString();
        if (!said.equals(lastReadout) && level instanceof ServerLevel serverLevel) {
            lastReadout = said;
            for (net.minecraft.server.level.ServerPlayer player : serverLevel.players()) {
                if (player.blockPosition().closerThan(worldPosition, 24.0)) {
                    player.sendSystemMessage(message.copy().withStyle(good ? ChatFormatting.GREEN : ChatFormatting.AQUA), true);
                }
            }
        }
    }

    /**
     * Vrai si ce qui est posé dans l'axe de la table contredit le plan déposé : un bloc de fusée là où le plan en
     * veut un autre, ou au-dessus de sa pointe. Un bloc qui manque ne contredit rien.
     */
    private boolean contradictsPlan(Level level) {
        int top = 0;
        for (RocketPlan.Piece piece : rocketPieces()) {
            if (piece.offset().getX() == 0 && piece.offset().getZ() == 0) {
                top = Math.max(top, piece.offset().getY());
                net.minecraft.world.level.block.Block there = level.getBlockState(worldPosition.offset(piece.offset())).getBlock();
                if (com.orbitalis.phase1.rocket.RocketScan.isPart(there) && there != piece.block()) {
                    return true;
                }
            }
        }
        return com.orbitalis.phase1.rocket.RocketScan.isPart(level.getBlockState(worldPosition.above(top + 1)).getBlock());
    }

    /**
     * Forme la fusée si tous les blocs de son plan sont en place : ils laissent place à la fusée assemblée, prête à
     * recevoir ses ergols.
     */
    private void tryFormRocket(Level level) {
        if (rocketPlan.isEmpty() || hasRocket() || phase != Phase.IDLE || !isOperational() || unforming) {
            return;
        }
        List<RocketPlan.Piece> pieces = rocketPieces();
        for (RocketPlan.Piece piece : pieces) {
            if (!level.getBlockState(worldPosition.offset(piece.offset())).is(piece.block())) {
                // Un bloc manque : la fusée se formera quand il sera reposé, même si elle avait été défaite à la main.
                formPaused = false;
                return;
            }
        }
        // Il lui faut aussi ses blocs de ravitaillement : sans eux, elle ne pourrait jamais faire le plein.
        if (formPaused || !missingPorts(level).isEmpty()) {
            return;
        }
        for (RocketPlan.Piece piece : pieces) {
            level.setBlock(worldPosition.offset(piece.offset()), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        if (setRocket(RocketItem.create(RocketBlueprintItem.parts(rocketPlan)))) {
            level.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 1.1F);
            announce(Component.translatable("spaceport.orbitalis.rocket.formed").withStyle(ChatFormatting.GREEN));
        } else {
            // La fusée n'a pas pu se former (place prise au dernier instant) : ses blocs sont remis.
            for (RocketPlan.Piece piece : pieces) {
                level.setBlock(worldPosition.offset(piece.offset()), piece.block().defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }

    /**
     * Défait la fusée bâtie : elle redevient ses blocs de construction, et son plan réapparaît. Si elle se défait
     * parce qu'un joueur en a cassé un bloc, ce bloc-là n'est pas remis : il tombe au sol, et il faudra le reposer.
     * Les ergols déjà chargés sont perdus.
     *
     * @param broken bloc que le joueur vient de casser, ou {@code null}
     * @return vrai si la fusée a été défaite
     */
    public boolean unformRocket(BlockPos broken) {
        if (rocketPlan.isEmpty() || !hasRocket() || phase != Phase.IDLE || level == null) {
            return false;
        }
        clearRocket();
        unforming = true;
        for (RocketPlan.Piece piece : rocketPieces()) {
            BlockPos pos = worldPosition.offset(piece.offset());
            if (pos.equals(broken)) {
                int refund = refundOf(piece);
                if (refund > 0) {
                    Block.popResource(level, pos, new ItemStack(piece.source(), refund));
                }
            } else {
                level.setBlock(pos, piece.block().defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
        unforming = false;
        // Défaite à la demande du joueur, sans bloc cassé : elle reste en blocs, pour être démontée ou modifiée.
        formPaused = broken == null;
        return true;
    }

    /** Vrai si la table dessine elle-même la tour et les bras (complexe en service, tour reconnue). */
    public boolean isStructureManaged() {
        return commissioned && complex.isComplete() && !isRetiredSize();
    }

    /** Demi-largeur de la fusée posée, en blocs ; 0 s'il n'y en a pas. */
    public float rocketHalfWidth() {
        return hasRocket() ? rocketShape().size().halfWidth() / 16.0F : 0.0F;
    }

    /**
     * Durée du vol en jeu de la fusée posée, en ticks : selon sa destination et son nombre d'étages (voir
     * {@link FlightProfile#flightTicks}).
     */
    public int flightTicks() {
        return hasRocket() ? FlightProfile.flightTicks(rocketStats()) : DEFAULT_FLIGHT_TICKS;
    }

    /** Ticks écoulés depuis le début de la phase en cours. */
    public long phaseTicks() {
        return level == null ? 0 : Math.max(0, level.getGameTime() - phaseStart);
    }

    /** Dégagement de la structure à l'instant du rendu, entre deux ticks. */
    public float getClearance(float partialTick) {
        return Mth.lerp(partialTick, previousClearance, clearance);
    }

    /**
     * Animation côté client : pendant les quinze dernières secondes du compte à rebours, la structure se dégage
     * (flexibles, bras, puis tour) ; elle reste dégagée pendant le vol, puis revient doucement en place.
     */
    public void clientTick() {
        // Consigne : la structure se dégage quand le retrait du bras est commandé, et le reste pendant le vol.
        float target = retracted || phase == Phase.FLIGHT ? 1.0F : 0.0F;
        // Chaque mouvement rejoint sa consigne en douceur, sans jamais sauter : il part vite et ralentit en arrivant.
        previousClearance = clearance;
        clearance += (target - clearance) * (target > clearance ? 0.06F : 0.035F);
        // Projecteurs : leurs LED montent en puissance en une seconde, et s'éteignent un peu plus vite.
        previousLampGlow = lampGlow;
        boolean lamps = isOperational() && (phase != Phase.IDLE || (level != null && level.isDarkOutside()));
        lampGlow += ((lamps ? 1.0F : 0.0F) - lampGlow) * (lamps ? 0.12F : 0.25F);
        // Particules et sons du lancement : ils suivent le compte à rebours sans rien y changer.
        if (level != null) {
            effects.tick(this, level);
        }
        // Le déluge coule : son bruit joue, en boucle, jusqu'à ce qu'il se referme.
        if (isDelugeOn()) {
            delugeSound.accept(this);
        }
        // Les moteurs brûlent : le grondement joue. On le redemande à chaque tick ; il ne se relance pas s'il joue déjà.
        if (hasRocket() && (phase == Phase.FLIGHT || (phase == Phase.COUNTDOWN && phaseTicks() >= COUNTDOWN_TICKS - IGNITION_TICKS))) {
            engineSound.accept(this);
        }
        // Fusée sans équipage en vol : elle suit sa trajectoire, et sa poussée la déplace. On refait ici le calcul
        // du serveur, tick après tick (en rattrapant d'un coup si l'on arrive en cours de vol).
        if (phase == Phase.FLIGHT && hasRocket() && level != null) {
            while (clientStagesSeen < stagesPassed && clientStagesSeen < clientStageDroppedAt.length) {
                clientStageDroppedAt[clientStagesSeen++] = level.getGameTime();
                if (!carriesCabin()) {
                    // Les boulons de l'étage sautent : un claquement sec, là où se trouve la fusée.
                    net.minecraft.world.phys.Vec3 drift = renderDrift(0.0F);
                    level.playLocalSound(worldPosition.getX() + 0.5 + drift.x, worldPosition.getY() + 1.0 + renderAscent(0.0F),
                            worldPosition.getZ() + 0.5 + drift.z, ModSounds.STAGE_SEPARATION.get(), SoundSource.BLOCKS, 12.0F, 1.0F, false);
                }
            }
            if ((flightDone & FLIGHT_FAIRING) != 0 && !clientFairingSeen) {
                clientFairingSeen = true;
                // La coiffe s'ouvre en deux et s'écarte de la charge utile.
                net.minecraft.world.phys.Vec3 drift = renderDrift(0.0F);
                level.playLocalSound(worldPosition.getX() + 0.5 + drift.x, worldPosition.getY() + 1.0 + renderAscent(0.0F) + rocketShape().height(),
                        worldPosition.getZ() + 0.5 + drift.z, ModSounds.FAIRING_JETTISON.get(), SoundSource.BLOCKS, 12.0F, 1.0F, false);
            }
        } else {
            clientStagesSeen = 0;
            clientFairingSeen = false;
        }
        if (phase == Phase.FLIGHT && hasRocket()) {
            long flown = Math.min(phaseTicks(), flightTicks());
            boolean redo = flown < clientMotionTicks;
            if (redo) {
                clientMotion.reset();
                clientMotionTicks = 0;
            }
            clientMotionLast.copy(clientMotion);
            RocketAttitude guide = new RocketAttitude();
            guide.reset(trenchDirection);
            while (clientMotionTicks < flown) {
                if (redo && clientMotionTicks == flown - 1) {
                    // Calcul refait depuis le décollage : l'image d'avant est celle du tick d'avant, pas le sol.
                    clientMotionLast.copy(clientMotion);
                }
                org.joml.Vector3d course = guide.course(targetPitch(++clientMotionTicks / (double) flightTicks()));
                clientMotion.step(course, enginesOut(clientMotionTicks) ? 0.0 : 1.0);
            }
        } else if (clientMotionTicks != 0) {
            clientMotion.reset();
            clientMotionLast.reset();
            clientMotionTicks = 0;
        }
        // La table se déclare aussi côté client : les écrans du centre de contrôle la retrouvent ainsi.
        if (level != null && ticks++ % 20 == 0) {
            track(level, worldPosition, true);
            clientScan(level);
        }
    }

    /**
     * Hauteur dont la fusée s'est élevée depuis le décollage, en blocs : elle quitte la table lentement,
     * puis accélère sans cesse, comme un vrai lanceur.
     */
    public float ascent(float partialTick) {
        if (phase != Phase.FLIGHT) {
            return 0.0F;
        }
        // La hauteur vient du mouvement calculé de la fusée (voir RocketMotion) : celui du serveur, ou, côté client,
        // celui que la table refait pour une fusée qui suit sa consigne.
        if (level != null && level.isClientSide()) {
            return (float) Math.max(0.0, Mth.lerp(partialTick, clientMotionLast.offset().y, clientMotion.offset().y));
        }
        return (float) Math.max(0.0, motion.offset().y);
    }

    /** Capsule de la fusée posée sur la table, une fois trouvée ou créée. */
    private CapsuleEntity padCapsule;

    /**
     * Côté client : orientation, à l'instant du rendu, d'une fusée sans équipage en vol. Son ordinateur de bord suit
     * la trajectoire calculée : on la recalcule ici. (Une fusée habitée est dessinée par sa capsule, qui reçoit du
     * serveur l'orientation que tient son pilote.)
     */
    public org.joml.Quaternionf renderAttitude(float partialTick) {
        if (phase == Phase.FLIGHT && hasRocket()) {
            double progress = Math.min(1.0, (phaseTicks() + partialTick) / (double) flightTicks());
            return RocketAttitude.following(trenchDirection, targetPitch(progress));
        }
        return new org.joml.Quaternionf();
    }

    /**
     * Hauteur, au-dessus du pied de la fusée, du point autour duquel elle tourne. Habitée, c'est la tête de son
     * pilote : la cabine tourne autour de lui, et il ne quitte pas son siège. Sans équipage, c'est sa mi-hauteur.
     */
    public float pivotHeight() {
        RocketShape shape = rocketShape();
        int cabin = RocketShape.cabinIndex(shape.parts());
        return cabin >= 0 ? cabin + (float) CapsuleEntity.EYE_HEIGHT : shape.height() * 0.5F;
    }

    /** Recul du même point par rapport à l'axe de la fusée, vers le sud : celui du siège du pilote, s'il y en a un. */
    public float pivotBack() {
        return RocketShape.cabinIndex(rocketShape().parts()) >= 0 ? (float) CapsuleEntity.SEAT_BACK : 0.0F;
    }

    /** Nombre de séparations d'étages que compte le vol de la fusée posée. */
    public int stageCount() {
        return hasRocket() ? stagingTicks().size() : 0;
    }

    /**
     * Pour les tests automatiques : fait décoller cette fusée sur-le-champ, pleins faits, séquence au sol passée,
     * quelle que soit la taille du pas de tir. Le vol qui suit est le vrai, avec ses étapes et son pilotage.
     */
    public boolean launchForTest(ServerLevel level, ItemStack newRocket, String agency) {
        if (phase != Phase.IDLE || newRocket.isEmpty()) {
            return false;
        }
        rocket = newRocket.copyWithCount(1);
        RocketFuel full = requiredFuel();
        loadedRp1 = full.rp1();
        loadedOxygen = full.oxygen();
        loadedHydrogen = full.hydrogen();
        loadedMethane = full.methane();
        loadedHelium3 = full.helium3();
        agencyKey = agency;
        resetSequence();
        breakup = null;
        timer = 0;
        flameoutAt = 0;
        flightBlast = RocketBreakup.blastRadius(rocketStats().liftoffMass(), 0.1, full.isEmpty());
        flightCryogenic = full.hydrogen() > 0;
        liftoff(level);
        return phase == Phase.FLIGHT;
    }

    /**
     * Pour les essais : le plein est fait, les vannes sont ouvertes, le parc à ergols pousse sans fin, et les
     * réservoirs céderont dans ce nombre de ticks si personne ne referme les vannes.
     */
    public void overpressureForTest(int ticksLeft) {
        RocketFuel full = requiredFuel();
        loadedRp1 = full.rp1();
        loadedOxygen = full.oxygen();
        loadedHydrogen = full.hydrogen();
        loadedMethane = full.methane();
        loadedHelium3 = full.helium3();
        valvesOpen = true;
        retracted = false;
        overpressure = Math.max(0, OVERPRESSURE_TICKS - ticksLeft);
        forcedOverflow = true;
        sync();
    }

    /** Pour les essais : le parc à ergols pousse sans fin dans des réservoirs pleins. */
    private boolean forcedOverflow;

    /** Pour les essais : les moteurs de la fusée en vol s'éteindront à ce tick de vol, et elle sera perdue là où elle est. */
    public void flameoutForTest(int flightTick) {
        flameoutAt = flightTick;
    }

    /** Étages déjà largués pendant ce vol. */
    public int stagesPassed() {
        return stagesPassed;
    }

    /** Vrai si la fusée emporte une cabine : en vol, c'est alors sa capsule qui la dessine, et non la table. */
    public boolean carriesCabin() {
        return hasRocket() && RocketShape.cabinIndex(rocketShape().parts()) >= 0;
    }

    /** Orientation de la fusée, côté serveur. */
    public org.joml.Quaternionf attitude() {
        return attitude.orientation();
    }

    /** Côté client : hauteur à laquelle dessiner une fusée sans équipage en vol, à l'instant du rendu. */
    public float renderAscent(float partialTick) {
        return ascent(partialTick);
    }

    /**
     * Côté client : de combien la fusée en vol s'est écartée de la verticale de sa table, vers l'est (x) et vers le
     * sud (z), à l'instant du rendu. Vaut pour une fusée sans équipage, qui suit sa consigne.
     */
    public Vec3 renderDrift(float partialTick) {
        org.joml.Vector3d last = clientMotionLast.offset();
        org.joml.Vector3d now = clientMotion.offset();
        return new Vec3(Mth.lerp(partialTick, last.x, now.x), 0.0, Mth.lerp(partialTick, last.z, now.z));
    }

    /** Côté serveur : déplacement de la fusée en vol par rapport à la montée droite au-dessus de la table. */
    public Vec3 flightOffset() {
        org.joml.Vector3d offset = motion.offset();
        return new Vec3(offset.x, offset.y, offset.z);
    }

    /** Vrai si c'est cette table qui retient son morceau de monde : elle ne relâche que ce qu'elle a retenu elle-même. */
    private boolean chunkHeld;

    /**
     * Retient, ou relâche, le morceau de monde de la table : la fusée s'en éloigne, et la table doit continuer de la
     * faire voler. S'il était déjà retenu par ailleurs (une commande du joueur, par exemple), on n'y touche pas.
     */
    private void holdChunk(boolean hold) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        int chunkX = worldPosition.getX() >> 4;
        int chunkZ = worldPosition.getZ() >> 4;
        if (hold) {
            chunkHeld = serverLevel.setChunkForced(chunkX, chunkZ, true) || chunkHeld;
        } else if (chunkHeld) {
            serverLevel.setChunkForced(chunkX, chunkZ, false);
            chunkHeld = false;
        }
    }

    /**
     * Prend en charge, ou relâche, les blocs de la structure. Dans un pas de tir assemblé, la tour, les mâts et
     * les caillebotis du carneau s'effacent et c'est la table qui dessine l'ensemble ; dès que la structure n'est
     * plus complète, tous redeviennent des blocs ordinaires.
     * <p>
     * Tout le voisinage de la table est passé en revue : un bloc resté effacé alors qu'il ne fait plus partie
     * de la structure (tablier rétréci, par exemple) est ainsi relâché lui aussi.
     */
    private void updateManaged(Level level, boolean managed) {
        Set<BlockPos> wanted = new HashSet<>();
        if (managed) {
            int radius = complex.radius();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    wanted.add(worldPosition.offset(dx, 0, dz));
                    // Mâts paratonnerres : la colonne dressée sur chaque angle du tablier.
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius) {
                        for (int i = 1; i < LaunchComplex.MAX_TOWER_HEIGHT; i++) {
                            BlockPos pos = worldPosition.offset(dx, i, dz);
                            if (!LaunchComplex.isMast(level.getBlockState(pos))) {
                                break;
                            }
                            wanted.add(pos);
                        }
                    }
                }
            }
            // Bras ombilicaux : posés contre la tour, ils sont dessinés déployés vers la fusée.
            wanted.addAll(complex.armPositions());
        }
        int reach = LaunchComplex.MAX_RADIUS + 1;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                // On monte dans chaque colonne tant qu'on y trouve des blocs de structure.
                for (int dy = 0; dy < LaunchComplex.MAX_TOWER_HEIGHT; dy++) {
                    BlockPos pos = worldPosition.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof ManagedStructureBlock) {
                        boolean wantManaged = wanted.contains(pos);
                        if (state.getValue(ManagedStructureBlock.MANAGED) != wantManaged) {
                            level.setBlock(pos, state.setValue(ManagedStructureBlock.MANAGED, wantManaged),
                                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        }
                    } else if (dy > 1) {
                        break;
                    }
                }
            }
        }
        // Les bras ombilicaux sont accrochés en hauteur, au-dessus du vide : la montée colonne par colonne ne les
        // atteint pas. On les traite à part, tout autour de la colonne technique, sur toute sa hauteur. Sans cela,
        // le bloc du bras resterait visible sous le bras animé que dessine la table.
        Set<BlockPos> arms = new HashSet<>(shownArms);
        arms.addAll(complex.armPositions());
        BlockPos towerBase = complex.towerBase();
        if (towerBase != null) {
            for (int dy = 0; dy < LaunchComplex.MAX_TOWER_HEIGHT; dy++) {
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    arms.add(towerBase.above(dy).relative(side));
                }
            }
        }
        for (BlockPos pos : arms) {
            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.UMBILICAL_ARM.get())) {
                boolean wantManaged = wanted.contains(pos);
                if (state.getValue(ManagedStructureBlock.MANAGED) != wantManaged) {
                    level.setBlock(pos, state.setValue(ManagedStructureBlock.MANAGED, wantManaged), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
        shownArms = managed ? List.copyOf(complex.armPositions()) : List.of();
    }

    /** Bras ombilicaux que la table a effacés pour les dessiner elle-même : à rendre visibles si elle cesse de le faire. */
    private List<BlockPos> shownArms = List.of();

    /**
     * Niveaux des portiques d'embarquement de la tour (hauteur de leur seuil), du plus bas au plus haut.
     * L'ascenseur s'y arrête, et une passerelle en part vers la capsule.
     */
    public int[] getGateLevels() {
        return gateLevels;
    }

    /** Relève les portiques d'embarquement posés dans la colonne technique. */
    private int[] scanGates(Level level) {
        BlockPos base = complex.towerBase();
        if (base == null) {
            return new int[0];
        }
        List<Integer> levels = new ArrayList<>();
        for (int i = 0; i < complex.towerHeight(); i++) {
            BlockState state = level.getBlockState(base.above(i));
            if (state.is(ModBlocks.BOARDING_GATE.get()) && BoardingGateBlock.isCore(state)) {
                levels.add(base.getY() + i);
            }
        }
        return levels.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Vrai tant que les passerelles sont déployées : au repos, et jusqu'au retrait des bras pendant le compte à rebours. */
    private boolean gangwaysDeployed() {
        return phase != Phase.FLIGHT && !retracted;
    }

    /**
     * Pose, ou retire, le plancher des passerelles d'embarquement. Dans un pas de tir assemblé, une passerelle part
     * du seuil de chaque portique et s'étend jusqu'à la fusée ; elle est dessinée par le rendu, et c'est une rangée
     * de blocs invisibles et solides qui la rend praticable. Ils ne prennent que la place libre. Au retrait des
     * bras, quinze secondes avant le décollage, ou dès que la structure se défait, tout est retiré.
     */
    private void updateGangways(Level level, boolean managed) {
        Map<BlockPos, BlockState> wanted = new java.util.HashMap<>();
        BlockPos base = complex.towerBase();
        if (managed && base != null && gangwaysDeployed()) {
            Direction forward = LaunchComplex.towerFacing(base, worldPosition);
            int distance = Math.max(Math.abs(base.getX() - worldPosition.getX()), Math.abs(base.getZ() - worldPosition.getZ()));
            BlockState floor = ModBlocks.TOWER_STEP.get().defaultBlockState();
            for (int gate : gateLevels) {
                for (int step = 1; step < distance; step++) {
                    wanted.put(base.atY(gate).relative(forward, step), floor);
                }
            }
        }
        // Au premier passage, on retrouve les planchers qu'une session précédente a laissés autour du pas de tir.
        if (!towerStepsFound) {
            towerStepsFound = true;
            int reach = LaunchComplex.REACH;
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    for (int dy = 0; dy <= LaunchComplex.MAX_TOWER_HEIGHT + 1; dy++) {
                        BlockPos pos = worldPosition.offset(dx, dy, dz);
                        if (level.getBlockState(pos).is(ModBlocks.TOWER_STEP.get())) {
                            towerSteps.add(pos);
                        }
                    }
                }
            }
        }
        towerSteps.removeIf(pos -> {
            if (wanted.containsKey(pos)) {
                return false;
            }
            if (level.getBlockState(pos).is(ModBlocks.TOWER_STEP.get())) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            return true;
        });
        wanted.forEach((pos, state) -> {
            BlockState current = level.getBlockState(pos);
            // Un plancher ne prend que la place libre : il ne remplace jamais un bloc posé par un joueur, ni la fusée.
            if (current.isAir() || (current.is(ModBlocks.TOWER_STEP.get()) && current != state)) {
                level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            }
            if (level.getBlockState(pos).is(ModBlocks.TOWER_STEP.get())) {
                towerSteps.add(pos.immutable());
            }
        });
    }

    /** Sens du carneau, relevé côté client pour orienter l'image du tablier. */
    public Direction getTrenchDirection() {
        return trenchDirection;
    }

    /** Hauteur du mât dressé sur chaque angle du tablier (nord-ouest, nord-est, sud-ouest, sud-est), relevée côté client. */
    public int[] getMastHeights() {
        return mastHeights;
    }

    /**
     * Relevé côté client de ce que la table dessine elle-même : le sens du carneau, la hauteur de chaque mât, les
     * portiques de la tour, et les blocs du plan qui manquent encore.
     */
    private void clientScan(Level level) {
        int radius = complex.radius();
        if (plan != null) {
            trenchDirection = plan.facing();
        }
        int corner = 0;
        for (int dz = -radius; dz <= radius; dz += Math.max(1, 2 * radius)) {
            for (int dx = -radius; dx <= radius; dx += Math.max(1, 2 * radius)) {
                int height = 0;
                while (height < LaunchComplex.MAX_TOWER_HEIGHT
                        && LaunchComplex.isMast(level.getBlockState(worldPosition.offset(dx, 1 + height, dz)))) {
                    height++;
                }
                if (corner < mastHeights.length) {
                    mastHeights[corner++] = height;
                }
            }
        }
        gateLevels = scanGates(level);

        List<Ghost> missing = new ArrayList<>();
        if (plan != null) {
            for (PadPlan.Piece piece : LaunchComplex.missingPieces(level, worldPosition, plan)) {
                missing.add(new Ghost(piece.offset(), piece.display()));
            }
        }
        // Le plan de la fusée ne se montre que sur un pas de tir formé, tant que la fusée n'est pas bâtie.
        if (missing.isEmpty() && !rocketPlan.isEmpty() && !hasRocket() && phase == Phase.IDLE) {
            // D'abord les blocs de ravitaillement, au pied de la tour, puis les blocs de la fusée.
            for (PortNeed need : missingPorts(level)) {
                missing.add(new Ghost(need.offset(), ModBlocks.FUEL_PORT.get().defaultBlockState().setValue(FuelPortBlock.KIND, need.kind())));
            }
            for (RocketPlan.Piece piece : rocketPieces()) {
                if (!level.getBlockState(worldPosition.offset(piece.offset())).is(piece.block())) {
                    missing.add(new Ghost(piece.offset(), piece.block().defaultBlockState()));
                }
            }
        }
        if (missing.isEmpty()) {
            ghosts = List.of();
            return;
        }
        // Le plan est montré en entier, du bas vers le haut : l'affichage détaille les couches où l'on bâtit.
        missing.sort(java.util.Comparator.comparingInt(ghost -> ghost.offset().getY()));
        ghosts = List.copyOf(missing.size() > MAX_GHOSTS ? missing.subList(0, MAX_GHOSTS) : missing);
    }

    /**
     * Tourne dans le bon sens les blocs du plan déjà posés : seul le bloc compte pour le joueur, la table corrige
     * d'elle-même l'orientation d'un escalier, d'un caillebotis ou de la colonne technique.
     */
    /**
     * Les rampes de déluge s'interrompent au droit de la fusée, pour laisser la place à ses boosters. Sur un pas de tir
     * bâti avant, le tronçon qui occupe encore l'ouverture est démonté, et rendu à côté.
     */
    private void openDelugeGaps(Level level) {
        for (BlockPos offset : plan.delugeGaps()) {
            BlockPos pos = worldPosition.offset(offset);
            if (level.getBlockState(pos).is(ModBlocks.DELUGE_PIPE.get())) {
                level.removeBlock(pos, false);
                Block.popResource(level, pos, new ItemStack(ModBlocks.DELUGE_PIPE.get()));
            }
        }
    }

    private void orientPieces(Level level) {
        for (PadPlan.Piece piece : plan.pieces()) {
            if (piece.facing() == null) {
                continue;
            }
            BlockPos pos = worldPosition.offset(piece.offset());
            BlockState state = level.getBlockState(pos);
            if (piece.accepts(state) && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                    && state.getValue(BlockStateProperties.HORIZONTAL_FACING) != piece.facing()) {
                level.setBlock(pos, state.setValue(BlockStateProperties.HORIZONTAL_FACING, piece.facing()),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }

    // ------------------------------------------------------------------
    // Structure du complexe
    // ------------------------------------------------------------------

    public void serverTick(ServerLevel level) {
        if (ticks++ % SCAN_INTERVAL == 0) {
            track(level, worldPosition, true);
            refresh(level);
            if (phase != Phase.FLIGHT) {
                placeRocketBlocks(level);
            }
        }
        if (syncPending && ticks % 20 == 0) {
            sync();
        }
        if (ticks % BOIL_OFF_INTERVAL == 1) {
            boilOff();
        }
        tickBeacons(level);
        tickFarSurvey(level);
        tickSequence(level);
        if (breakup != null && breakup.tick(level)) {
            breakup = null;
        }
        LaunchHeat.tick(level, this);
        switch (phase) {
            case COUNTDOWN -> tickCountdown(level);
            case FLIGHT -> tickFlight(level);
            default -> {
            }
        }
    }

    /**
     * À appeler quand un bloc de structure vient d'être posé ou cassé : les tables de lancement voisines relèvent
     * aussitôt leur structure, sans attendre leur relevé périodique. Le pas de tir se défait ou se reforme
     * ainsi à l'instant même.
     */
    public static void notifyStructureChanged(Level level, BlockPos changed) {
        if (level.isClientSide()) {
            return;
        }
        int reach = LaunchComplex.REACH;
        for (BlockPos tablePos : loadedTables(level)) {
            int dy = changed.getY() - tablePos.getY();
            if (Math.abs(changed.getX() - tablePos.getX()) <= reach && Math.abs(changed.getZ() - tablePos.getZ()) <= reach
                    && dy >= -1 && dy <= LaunchComplex.MAX_TOWER_HEIGHT && level.getBlockEntity(tablePos) instanceof LaunchTableBlockEntity table) {
                table.refresh(level);
            }
        }
    }

    /** Relève la structure et met à jour le voyant de la table. */
    public void refresh(Level level) {
        // La table pose et retire elle-même des blocs pendant un relevé : ces changements ne doivent pas en relancer un.
        if (refreshing) {
            return;
        }
        refreshing = true;
        try {
            refreshNow(level);
        } finally {
            refreshing = false;
        }
    }

    private void refreshNow(Level level) {
        // La table se déclare ici aussi : elle est ainsi connue des pupitres et des bras dès sa mise en service,
        // sans attendre son premier relevé périodique.
        if (!level.isClientSide()) {
            track(level, worldPosition, true);
        }
        LaunchComplex before = complex;
        if (plan != null && !level.isClientSide()) {
            moveGate(level);
            orientPieces(level);
            openDelugeGaps(level);
        }
        complex = LaunchComplex.scan(level, worldPosition, plan);
        boolean operational = isOperational();
        BlockState state = getBlockState();
        if (state.getValue(LaunchTableBlock.FORMED) != operational) {
            level.setBlock(worldPosition, state.setValue(LaunchTableBlock.FORMED, operational), 3);
            if (operational) {
                // La structure vient de se former : un son le signale.
                level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.2F);
            }
        }
        if (!level.isClientSide()) {
            updatePadLooks(level, operational ? complex.radius() : 0);
            updateManaged(level, isOperational());
            gateLevels = scanGates(level);
            tunePorts(level);
            updateFloodlights(level);
            tryFreeBuild(level);
            tryFormRocket(level);
            updateGangways(level, isStructureManaged());
            if (!complex.equals(before)) {
                sync();
            }
        }
    }

    /**
     * Met le complexe en service.
     *
     * @param towerAuthorized vrai si l'agence du joueur maîtrise la tour de service
     */
    public void commission(boolean towerAuthorized) {
        this.commissioned = true;
        this.towerAuthorized = towerAuthorized;
        setChanged();
        if (level != null) {
            refresh(level);
        }
        sync();
    }

    public boolean isCommissioned() {
        return commissioned;
    }

    public boolean isTowerAuthorized() {
        return towerAuthorized;
    }

    /** Dernier relevé de la structure. */
    public LaunchComplex getComplex() {
        return complex;
    }

    /** Vrai si ce pas de tir est d'une taille qui n'existe plus (3×3 à tour, 7×7) : il ne peut plus être mis en service. */
    public boolean isRetiredSize() {
        return plan != null && !plan.compact() && !PadPlan.isOffered(plan.radius());
    }

    /** Vrai si c'est le pas de tir d'essai : un 3×3 sans tour, pour les fusées-sondes à poudre, sans équipage. */
    public boolean isTestPad() {
        return plan != null && plan.compact();
    }

    /**
     * Ce qui interdit cette fusée sur le pas de tir d'essai, ou {@code null} si elle y a sa place (ou si ce n'est pas un
     * pas de tir d'essai). Il n'a ni bras pour faire le plein, ni passerelle pour embarquer : il ne prend que des
     * fusées à poudre, sans équipage.
     */
    public Component testPadProblem(ItemStack candidate) {
        if (!isTestPad() || candidate.isEmpty()) {
            return null;
        }
        com.orbitalis.phase1.rocket.RocketDesign design = RocketItem.design(candidate);
        if (RocketParts.isCrewCabin(design.payload())) {
            return Component.translatable("spaceport.orbitalis.table.test_pad_crew");
        }
        if (!RocketFuel.required(RocketStats.of(design)).isEmpty()) {
            return Component.translatable("spaceport.orbitalis.table.test_pad_liquid");
        }
        return null;
    }

    /** Vrai si le complexe est mis en service et que sa structure est complète. */
    public boolean isOperational() {
        return commissioned && complex.isComplete() && !isRetiredSize();
    }

    /** Hauteur de la tour prise en compte : 0 tant que la technologie n'est pas maîtrisée. */
    public int usableTowerHeight() {
        return complex.towerHeight();
    }

    /**
     * Nombre de bras ombilicaux disponibles : la tour de service porte les siens, qui se déploient selon la fusée.
     * Sans tour reconnue, il n'y en a aucun.
     */
    public int usableArms() {
        return complex.arms();
    }

    // ------------------------------------------------------------------
    // Fusée et ergols
    // ------------------------------------------------------------------

    public boolean hasRocket() {
        return !rocket.isEmpty();
    }

    /** Copie de la fusée posée sur la table, ou une pile vide. */
    public ItemStack getRocket() {
        return rocket.copy();
    }

    /** Statistiques de la fusée posée sur la table. */
    public RocketStats rocketStats() {
        // Calculées une fois par fusée : elles sont demandées plusieurs fois par tick et par image.
        if (statsRocket != rocket || stats == null) {
            stats = RocketStats.of(RocketItem.design(rocket));
            statsRocket = rocket;
        }
        return stats;
    }

    /** Ergols à charger pour la fusée posée sur la table. */
    public RocketFuel requiredFuel() {
        return hasRocket() ? RocketFuel.required(rocketStats()) : RocketFuel.NONE;
    }

    /** Pose une fusée sur la table. Renvoie faux si la table en porte déjà une ou qu'un lancement est en cours. */
    public boolean setRocket(ItemStack newRocket) {
        if (hasRocket() || phase != Phase.IDLE || newRocket.isEmpty() || !hasRoomFor(newRocket) || !isHeightAllowedFor(newRocket)) {
            return false;
        }
        rocket = newRocket.copyWithCount(1);
        loadedRp1 = loadedOxygen = loadedHydrogen = loadedMethane = loadedHelium3 = 0;
        lastResult = Result.NONE;
        if (level != null) {
            placeRocketBlocks(level);
        }
        sync();
        return true;
    }

    /** Retire la fusée de la table et la renvoie. Les ergols déjà chargés sont perdus. */
    public ItemStack removeRocket() {
        if (phase != Phase.IDLE || !hasRocket()) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = rocket;
        clearRocket();
        return removed;
    }

    private void clearRocket() {
        CapsuleEntity capsule = capsule(false);
        if (capsule != null) {
            capsule.discard();
        }
        rocket = ItemStack.EMPTY;
        loadedRp1 = loadedOxygen = loadedHydrogen = loadedMethane = loadedHelium3 = 0;
        if (level != null) {
            removeRocketBlocks(level);
        }
        sync();
    }

    // ------------------------------------------------------------------
    // Vol habité
    // ------------------------------------------------------------------

    /** Capsule partie avec la fusée en vol. */
    private CapsuleEntity flightCapsule;

    /**
     * Capsule qui monte avec la fusée en vol, ou {@code null}. Elle n'est plus « sur le pas de tir » : on la garde
     * en mémoire depuis le décollage, et on la recherche le long de la montée si le monde a été rechargé entre-temps.
     */
    private CapsuleEntity crewCapsule() {
        if (flightCapsule != null && !flightCapsule.isRemoved() && flightCapsule.getPhase() == CapsuleEntity.Phase.ASCENT) {
            return flightCapsule;
        }
        flightCapsule = null;
        if (level instanceof ServerLevel serverLevel) {
            AABB column = new AABB(worldPosition).inflate(RocketMotion.LIMIT + 8.0, 0.0, RocketMotion.LIMIT + 8.0).expandTowards(0.0, 4000.0, 0.0);
            for (CapsuleEntity capsule : serverLevel.getEntitiesOfClass(CapsuleEntity.class, column)) {
                if (worldPosition.equals(capsule.getTable()) && capsule.getPhase() == CapsuleEntity.Phase.ASCENT) {
                    flightCapsule = capsule;
                    break;
                }
            }
        }
        return flightCapsule;
    }

    /** Vrai si la fusée posée emporte une capsule habitée. */
    public boolean isCrewed() {
        return hasRocket() && RocketParts.isCrewCabin(RocketItem.design(rocket).payload());
    }

    /** Emplacement de la capsule : au pied du compartiment de la charge utile, sous la coiffe. */
    private Vec3 capsulePosition() {
        int index = Math.max(0, RocketShape.cabinIndex(rocketShape().parts()));
        return new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + 1 + index, worldPosition.getZ() + 0.5);
    }

    /**
     * Capsule de la fusée posée sur la table.
     *
     * @param create vrai pour la créer si elle n'existe pas encore
     * @return la capsule, ou {@code null} si la fusée n'en emporte pas
     */
    public CapsuleEntity capsule(boolean create) {
        if (!(level instanceof ServerLevel serverLevel) || !isCrewed()) {
            return null;
        }
        // La table garde sa capsule en mémoire : la chercher dans le monde à chaque fois coûterait plus cher, et
        // pourrait la manquer tant que les entités des alentours ne sont pas toutes chargées.
        if (padCapsule != null && !padCapsule.isRemoved() && padCapsule.getPhase() == CapsuleEntity.Phase.PAD) {
            return padCapsule;
        }
        padCapsule = null;
        Vec3 seat = capsulePosition();
        for (CapsuleEntity capsule : serverLevel.getEntitiesOfClass(CapsuleEntity.class, new AABB(seat, seat).inflate(2.0, 4.0, 2.0))) {
            if (worldPosition.equals(capsule.getTable()) && capsule.getPhase() == CapsuleEntity.Phase.PAD) {
                padCapsule = capsule;
                return capsule;
            }
        }
        if (!create) {
            return null;
        }
        CapsuleEntity capsule = new CapsuleEntity(ModEntities.CAPSULE.get(), serverLevel);
        capsule.attachTo(worldPosition, seat);
        capsule.setLunar(RocketItem.design(rocket).payload().is(ModItems.LUNAR_MODULE.get()));
        capsule.setMartian(RocketItem.design(rocket).payload().is(ModItems.MARS_LANDER.get()));
        capsule.setTitanian(RocketItem.design(rocket).payload().is(ModItems.TITAN_LANDER.get()));
        capsule.setEuropan(RocketItem.design(rocket).payload().is(ModItems.EUROPA_LANDER.get()));
        capsule.setVariant(com.orbitalis.phase1.rocket.RocketParts.cabinVariant(RocketItem.design(rocket).payload()));
        serverLevel.addFreshEntity(capsule);
        padCapsule = capsule;
        return capsule;
    }

    /**
     * Fait monter un joueur à bord de la fusée.
     *
     * @return ce qui l'en empêche, ou {@code null} s'il est à bord
     */
    public Component board(ServerPlayer player) {
        if (!isCrewed()) {
            return Component.translatable("capsule.orbitalis.no_capsule");
        }
        // On n'embarque plus une fois les moteurs allumés.
        if (phase == Phase.FLIGHT || (phase == Phase.COUNTDOWN && timer <= IGNITION_TICKS)) {
            return Component.translatable("capsule.orbitalis.too_late");
        }
        CapsuleEntity capsule = capsule(true);
        if (capsule == null || capsule.isVehicle()) {
            return Component.translatable("capsule.orbitalis.occupied");
        }
        return player.startRiding(capsule) ? null : Component.translatable("capsule.orbitalis.occupied");
    }

    /** Silhouette de la fusée posée sur la table : ses tronçons, de bas en haut. */
    public RocketShape rocketShape() {
        if (shapeRocket != rocket || shape == null) {
            shape = RocketShape.of(RocketItem.design(rocket));
            shapeRocket = rocket;
        }
        return shape;
    }

    /** Hauteur de la fusée posée sur la table, en blocs ; 0 s'il n'y en a pas. */
    public int rocketHeight() {
        return hasRocket() ? rocketShape().height() : 0;
    }

    /** Cases occupées par un tronçon : la sienne, plus les huit qui l'entourent si la fusée fait trois blocs de large. */
    private static List<BlockPos> layer(BlockPos center, boolean wide) {
        List<BlockPos> cells = new ArrayList<>();
        cells.add(center);
        if (wide) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dz != 0) {
                        cells.add(center.offset(dx, 0, dz));
                    }
                }
            }
        }
        return cells;
    }

    /** Vrai si rien ne gêne, au-dessus de la table, pour dresser cette fusée sur toute sa hauteur et sa largeur. */
    public boolean hasRoomFor(ItemStack candidate) {
        if (level == null) {
            return false;
        }
        RocketShape shape = RocketShape.of(RocketItem.design(candidate));
        for (int i = 1; i <= shape.height(); i++) {
            for (BlockPos pos : layer(worldPosition.above(i), shape.isWide())) {
                if (level.isOutsideBuildHeight(pos) || !isFreeForRocket(level.getBlockState(pos))) {
                    return false;
                }
            }
        }
        for (BlockPos offset : shape.boosterOffsets(trenchFacing())) {
            if (!isFreeForRocket(level.getBlockState(worldPosition.offset(offset)))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Vrai si la fusée peut occuper cette case : elle est vide, ou ne porte que le plancher d'une passerelle
     * d'embarquement, qui s'efface devant elle.
     */
    private static boolean isFreeForRocket(BlockState state) {
        return state.isAir() || state.is(ModBlocks.TOWER_STEP.get());
    }

    /**
     * Dresse la partie visible de la fusée au-dessus de la table : un bloc par tronçon de sa silhouette,
     * entouré de flancs invisibles si la fusée fait trois blocs de large.
     */
    private void placeRocketBlocks(Level level) {
        if (!hasRocket()) {
            return;
        }
        RocketShape shape = rocketShape();
        for (int i = 0; i < shape.height(); i++) {
            BlockPos center = worldPosition.above(i + 1);
            BlockState wanted = ModBlocks.ROCKET.get().defaultBlockState()
                    .setValue(RocketBlock.PART, shape.parts().get(i)).setValue(RocketBlock.SIZE, shape.size());
            BlockState current = level.getBlockState(center);
            if (current != wanted && (isFreeForRocket(current) || current.is(ModBlocks.ROCKET.get()))) {
                level.setBlock(center, wanted, Block.UPDATE_ALL);
            }
            for (BlockPos pos : layer(center, shape.isWide())) {
                if (!pos.equals(center) && isFreeForRocket(level.getBlockState(pos))) {
                    level.setBlock(pos, ModBlocks.ROCKET_HULL.get().defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        // Les boosters latéraux, de part et d'autre : de fins tronçons de fusée à poudre.
        List<BlockPos> boosters = shape.boosterOffsets(trenchFacing());
        int boosterHeight = shape.boosterHeight();
        for (int k = 0; k < boosters.size(); k++) {
            BlockPos pos = worldPosition.offset(boosters.get(k));
            BlockState wanted = ModBlocks.ROCKET.get().defaultBlockState()
                    .setValue(RocketBlock.PART, RocketShape.boosterPart(k % boosterHeight, boosterHeight)).setValue(RocketBlock.SIZE, shape.boosterSize());
            if (isFreeForRocket(level.getBlockState(pos))) {
                level.setBlock(pos, wanted, Block.UPDATE_ALL);
            }
        }
    }

    /** Sens du carneau du pas de tir (est, faute de plan). */
    private Direction trenchFacing() {
        return plan == null ? Direction.EAST : plan.facing();
    }

    /** Retire toute la colonne de tronçons de fusée qui surmonte la table, avec leurs flancs. */
    private void removeRocketBlocks(Level level) {
        // Les boosters latéraux : à un ou deux blocs de l'axe, depuis le bas.
        for (Direction side : Direction.Plane.HORIZONTAL) {
            for (int distance = 1; distance <= 2; distance++) {
                for (int y = 1; y <= LaunchComplex.MAX_TOWER_HEIGHT; y++) {
                    BlockPos pos = worldPosition.offset(side.getStepX() * distance, y, side.getStepZ() * distance);
                    if (!level.getBlockState(pos).is(ModBlocks.ROCKET.get())) {
                        break;
                    }
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        BlockPos center = worldPosition.above();
        while (level.getBlockState(center).is(ModBlocks.ROCKET.get())) {
            for (BlockPos pos : layer(center, true)) {
                if (pos.equals(center) || level.getBlockState(pos).is(ModBlocks.ROCKET_HULL.get())) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            center = center.above();
        }
    }

    /**
     * Donne aux dalles du pas de tir l'aspect de leur rôle : carneau autour de la table, bordure sur le pourtour.
     *
     * @param radius rayon du pas de tir en service, ou 0 pour rendre aux dalles leur aspect ordinaire
     */
    private void updatePadLooks(Level level, int radius) {
        for (int dx = -LaunchComplex.MAX_RADIUS; dx <= LaunchComplex.MAX_RADIUS; dx++) {
            for (int dz = -LaunchComplex.MAX_RADIUS; dz <= LaunchComplex.MAX_RADIUS; dz++) {
                BlockPos pos = worldPosition.offset(dx, 0, dz);
                BlockState state = level.getBlockState(pos);
                if (!state.is(ModBlocks.LAUNCH_PAD.get())) {
                    continue;
                }
                LaunchPadBlock.Look look = LaunchPadBlock.lookFor(Math.max(Math.abs(dx), Math.abs(dz)), radius);
                if (state.getValue(LaunchPadBlock.LOOK) != look) {
                    // Simple changement d'aspect : envoyé aux joueurs, sans prévenir les blocs voisins.
                    level.setBlock(pos, state.setValue(LaunchPadBlock.LOOK, look), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
    }

    /**
     * Hauteur de tour de service exigée par la fusée posée : une fusée à ergols liquides se ravitaille par les bras
     * de la tour, il lui en faut donc une. Une fusée à poudre n'exige rien.
     */
    public int requiredTowerHeight() {
        return complex.requiredTowerHeight();
    }

    /** Durée minimale d'un plein complet, en ticks : le débit des bras est borné pour qu'on le voie monter. */
    private static final int FILL_TICKS = 16 * 20;
    /** Ergols passés par les bras pendant ce tick. */
    private int fedThisTick;
    private long fedAt = Long.MIN_VALUE;

    /**
     * Charge un ergol dans la fusée par un bras ombilical, à son débit : le plein d'une fusée prend au moins
     * {@link #FILL_TICKS}, quelle que soit sa taille et quoi que pousse le parc à ergols. L'eau du déluge n'est pas
     * concernée. Vannes restées ouvertes une fois le plein fait, ce qui arrive encore fait monter la pression.
     *
     * @return la quantité acceptée, en mB
     */
    public int fillFromArm(Fluid fluid, int amount, boolean simulate) {
        if (fluid == net.minecraft.world.level.material.Fluids.WATER || !hasRocket()) {
            return fill(fluid, amount, simulate);
        }
        if (fedAt != ticks) {
            fedAt = ticks;
            fedThisTick = 0;
        }
        int rate = Math.max(1, (int) Math.ceil(requiredFuel().total() / (double) FILL_TICKS));
        int allowed = Math.min(amount, rate - fedThisTick);
        if (allowed <= 0) {
            return 0;
        }
        int accepted = fill(fluid, allowed, simulate);
        // Plein fait : ce que le parc pousse encore n'entre plus, et c'est ce qui fait monter la pression.
        if (accepted <= 0 && valvesOpen && !retracted && phase == Phase.IDLE && fuelPercent() >= 99) {
            overflowAt = ticks;
        }
        if (!simulate) {
            fedThisTick += accepted;
        }
        return accepted;
    }

    /**
     * Charge un ergol dans la fusée (appelé par les bras ombilicaux).
     *
     * @return la quantité acceptée, en mB
     */
    public int fill(Fluid fluid, int amount, boolean simulate) {
        // L'eau du déluge va dans la réserve du pas de tir, qu'il porte une fusée ou non.
        if (fluid == net.minecraft.world.level.material.Fluids.WATER) {
            int room = Math.max(0, Math.min(amount, waterCapacity() - water));
            if (room > 0 && !simulate) {
                water += room;
                setChanged();
                syncPending = true;
            }
            return room;
        }
        // Les ergols ne passent que vannes ouvertes, bras branché, et jamais moteurs allumés.
        if (!hasRocket() || phase != Phase.IDLE || usableArms() == 0 || amount <= 0 || !valvesOpen || retracted) {
            return 0;
        }
        RocketFuel required = requiredFuel();
        // Comme sur un vrai pas de tir, le kérosène est chargé en premier : les ergols cryogéniques, qui s'évaporent,
        // ne sont chargés qu'ensuite, au dernier moment.
        boolean cryogenic = fluid == ModFluids.LIQUID_OXYGEN.get() || fluid == ModFluids.LIQUID_HYDROGEN.get()
                || fluid == ModFluids.LIQUID_METHANE.get();
        if (cryogenic && loadedRp1 < required.rp1()) {
            return 0;
        }
        int accepted;
        if (fluid == ModFluids.RP1.get()) {
            accepted = Math.min(amount, required.rp1() - loadedRp1);
            loadedRp1 += simulate ? 0 : Math.max(0, accepted);
        } else if (fluid == ModFluids.LIQUID_OXYGEN.get()) {
            accepted = Math.min(amount, required.oxygen() - loadedOxygen);
            loadedOxygen += simulate ? 0 : Math.max(0, accepted);
        } else if (fluid == ModFluids.LIQUID_HYDROGEN.get()) {
            accepted = Math.min(amount, required.hydrogen() - loadedHydrogen);
            loadedHydrogen += simulate ? 0 : Math.max(0, accepted);
        } else if (fluid == ModFluids.LIQUID_METHANE.get()) {
            accepted = Math.min(amount, required.methane() - loadedMethane);
            loadedMethane += simulate ? 0 : Math.max(0, accepted);
        } else if (fluid == ModFluids.HELIUM_3.get()) {
            accepted = Math.min(amount, required.helium3() - loadedHelium3);
            loadedHelium3 += simulate ? 0 : Math.max(0, accepted);
        } else {
            return 0;
        }
        // Un ergol qui arrive encore alors que son réservoir est plein : c'est ce qui fait monter la pression.
        if (accepted < amount) {
            overflowAt = ticks;
        }
        if (accepted > 0 && !simulate) {
            setChanged();
            // Le niveau des réservoirs est renvoyé aux clients une fois par seconde au plus.
            syncPending = true;
        }
        return Math.max(0, accepted);
    }

    /**
     * Évaporation : tant que la fusée attend sur son pas de tir, ses ergols cryogéniques se réchauffent et une petite
     * partie s'échappe, l'hydrogène deux fois plus vite que l'oxygène. Un parc à ergols encore raccordé complète
     * aussitôt le niveau ; sans lui, le plein finit par ne plus être fait. Pendant le compte à rebours, les
     * réservoirs sont mis sous pression et ne perdent plus rien.
     */
    private void boilOff() {
        // Sous pression, les réservoirs ne perdent rien.
        if (phase != Phase.IDLE || !hasRocket() || !ServerConfig.BOIL_OFF.get() || isPressurized()) {
            return;
        }
        int oxygen = loadedOxygen > 0 ? Math.max(1, loadedOxygen / 1000) : 0;
        int hydrogen = loadedHydrogen > 0 ? Math.max(1, loadedHydrogen / 500) : 0;
        // Le méthane, moins froid que l'oxygène, s'évapore moins vite ; sur Titan, il ne s'évapore pas.
        int methane = loadedMethane > 0 && level != null && ModFluids.boilsOff(ModFluids.LIQUID_METHANE.get(), level)
                ? Math.max(1, loadedMethane / 1500) : 0;
        if (oxygen + hydrogen + methane > 0) {
            loadedOxygen -= oxygen;
            loadedHydrogen -= hydrogen;
            loadedMethane -= methane;
            setChanged();
            syncPending = true;
        }
    }

    /** Ergols chargés, pour pouvoir annuler un transfert (voir UmbilicalFluidHandler). */
    public int[] fuelSnapshot() {
        return new int[] {loadedRp1, loadedOxygen, loadedHydrogen, water, loadedMethane, loadedHelium3};
    }

    public void restoreFuel(int[] snapshot) {
        loadedRp1 = snapshot[0];
        loadedOxygen = snapshot[1];
        loadedHydrogen = snapshot[2];
        if (snapshot.length > 3) {
            water = snapshot[3];
        }
        if (snapshot.length > 4) {
            loadedMethane = snapshot[4];
        }
        if (snapshot.length > 5) {
            loadedHelium3 = snapshot[5];
        }
    }

    /** Remplissage des réservoirs, de 0 à 100. Une fusée à poudre est toujours « pleine ». */
    public int fuelPercent() {
        RocketFuel required = requiredFuel();
        if (required.isEmpty()) {
            return 100;
        }
        long loaded = (long) Math.min(loadedRp1, required.rp1()) + Math.min(loadedOxygen, required.oxygen())
                + Math.min(loadedHydrogen, required.hydrogen()) + Math.min(loadedMethane, required.methane())
                + Math.min(loadedHelium3, required.helium3());
        return (int) (loaded * 100 / required.total());
    }

    public boolean isFuelled() {
        RocketFuel required = requiredFuel();
        return loadedRp1 >= required.rp1() && loadedOxygen >= required.oxygen() && loadedHydrogen >= required.hydrogen()
                && loadedMethane >= required.methane() && loadedHelium3 >= required.helium3();
    }

    // ------------------------------------------------------------------
    // Vérifications avant lancement (« Go / No-Go »)
    // ------------------------------------------------------------------

    /** Vrai si la fusée ne dépasse pas la masse que le pas de tir supporte. */
    public boolean isMassAllowed() {
        return hasRocket() && rocketStats().liftoffMass() <= complex.maxRocketMass();
    }

    /** Vrai si le pas de tir supporte la masse de cette fusée. */
    public boolean isMassAllowedFor(ItemStack candidate) {
        return RocketStats.of(RocketItem.design(candidate)).liftoffMass() <= complex.maxRocketMass();
    }

    /** Vrai si cette fusée tient sous le sommet de la tour de service. */
    public boolean isHeightAllowedFor(ItemStack candidate) {
        return RocketShape.of(RocketItem.design(candidate)).height() <= complex.maxRocketHeight();
    }

    /** Vrai si la fusée a besoin d'ergols liquides, donc d'un bras ombilical. */
    public boolean needsArm() {
        return !requiredFuel().isEmpty();
    }

    /**
     * Pas de lancement par mauvais temps : ni sous la pluie, ni sous la neige, ni sous l'orage. Un pas de tir bâti là
     * où il ne tombe jamais rien (un désert, une savane) n'est pas concerné : le ciel y reste sec quand il pleut ailleurs.
     */
    public boolean isWeatherGo() {
        return level == null || !level.isRaining() || !level.getBiome(worldPosition).value().hasPrecipitation();
    }

    /** Vrai si tout est en ordre pour lancer. */
    public boolean isGoForLaunch() {
        return isOperational() && hasRocket() && rocketStats().isFlightReady() && isMassAllowed() && testPadProblem(rocket) == null
                && (!needsArm() || usableArms() > 0) && usableTowerHeight() >= requiredTowerHeight()
                && isFuelled() && isWeatherGo();
    }

    // ------------------------------------------------------------------
    // Compte à rebours et vol
    // ------------------------------------------------------------------

    public Phase getPhase() {
        return phase;
    }

    public Result getLastResult() {
        return lastResult;
    }

    /** Points de recherche gagnés au dernier vol réussi. */
    public int getLastPoints() {
        return lastPoints;
    }

    /** Secondes restantes avant le décollage (compte à rebours), ou écoulées depuis (vol). */
    public int getSeconds() {
        return phase == Phase.COUNTDOWN ? (timer + 19) / 20 : phase == Phase.FLIGHT ? timer / 20 : 0;
    }

    /** Côté serveur : ticks écoulés depuis le décollage, 0 hors du vol. */
    public int flightTimer() {
        return phase == Phase.FLIGHT ? timer : 0;
    }

    /** Pour les tests automatiques : le lancement ne peut plus échouer par malchance. */
    public void setReliable(boolean reliable) {
        this.reliable = reliable;
    }

    // ------------------------------------------------------------------
    // Séquence de lancement
    // ------------------------------------------------------------------

    /** Durée pendant laquelle les réservoirs restent sous pression, et durée du déluge, en ticks. */
    private static final int PRESSURE_TICKS = 120 * 20;
    private static final int DELUGE_TICKS = 180 * 20;
    /** Temps pendant lequel le déluge coule encore après le décollage, en ticks. */
    private static final int DELUGE_AFTER_LIFTOFF = 70;
    /** Temps que les attaches peuvent retenir la fusée à pleine poussée avant que les moteurs ne soient coupés, en ticks. */
    public static final int HOLD_TICKS = 12 * 20;

    /** Vrai si la fusée embarque des ergols liquides : il faut alors faire le plein et mettre sous pression. */
    public boolean isLiquid() {
        return hasRocket() && !requiredFuel().isEmpty();
    }

    public boolean areValvesOpen() {
        return valvesOpen;
    }

    public boolean isPressurized() {
        return level != null && pressureUntil > level.getGameTime();
    }

    /** Secondes de pression restantes. */
    public int pressureSeconds() {
        return level == null ? 0 : (int) Math.max(0, (pressureUntil - level.getGameTime() + 19) / 20);
    }

    public boolean isRetracted() {
        return retracted;
    }

    public boolean isDelugeOn() {
        return level != null && delugeUntil > level.getGameTime();
    }

    public int delugeSeconds() {
        return level == null ? 0 : (int) Math.max(0, (delugeUntil - level.getGameTime() + 19) / 20);
    }

    /** Vrai si le pas de tir a des rampes de déluge : dès le 5×5. Le 3×3 s'en passe. */
    public boolean hasDeluge() {
        return plan != null && plan.radius() >= 2;
    }

    /** Eau qu'un déluge consomme, et contenance de la réserve, en mB. */
    public int delugeNeed() {
        return plan == null ? 0 : 1000 * plan.radius();
    }

    public int waterCapacity() {
        return 3 * delugeNeed();
    }

    public int getWater() {
        return water;
    }

    /** Poussée des moteurs, de 0 à 100 : ils montent en puissance pendant les trois secondes qui suivent l'allumage. */
    public int thrustPercent() {
        return phase != Phase.COUNTDOWN || timer > IGNITION_TICKS ? 0 : 100 * (IGNITION_TICKS - timer) / IGNITION_TICKS;
    }

    /** Secondes pendant lesquelles les attaches retiennent encore la fusée à pleine poussée. */
    public int holdSeconds() {
        return phase == Phase.COUNTDOWN && timer <= 0 ? (HOLD_TICKS - holdTicks + 19) / 20 : 0;
    }

    /** Vrai si tout est en ordre pour allumer les moteurs. Le plein n'en fait pas partie : c'est l'affaire de l'équipe. */
    public boolean isReadyToIgnite() {
        return isOperational() && hasRocket() && rocketStats().isFlightReady() && isMassAllowed() && testPadProblem(rocket) == null
                && usableTowerHeight() >= requiredTowerHeight() && isWeatherGo();
    }

    // ------------------------------------------------------------------
    // Rythme de la séquence au sol
    // ------------------------------------------------------------------

    /**
     * Famille technique d'un lanceur, de la plus simple à la plus avancée : elle décide de ce que sa séquence de
     * lancement demande. Poudre : rien à remplir. Ergols sous pression : le plein et la pression. Turbopompes : en
     * plus, la mise en froid des moteurs. Combustion étagée ou hydrogène : en plus, le passage sur alimentation interne.
     */
    public enum Tech {
        SOLID,
        PRESSURE_FED,
        TURBOPUMP,
        ADVANCED
    }

    /** Famille technique de cette fusée, d'après les moteurs de ses étages. */
    public static Tech techOf(ItemStack rocket) {
        Tech tech = Tech.SOLID;
        for (com.orbitalis.phase1.rocket.RocketDesign.Stage stage : RocketItem.design(rocket).stages()) {
            ItemStack engines = stage.engines();
            Tech own = engines.is(ModItems.STAGED_COMBUSTION_ENGINE.get()) || engines.is(ModItems.HYDROLOX_ENGINE.get())
                    || engines.is(ModItems.VACUUM_ENGINE.get()) || engines.is(ModItems.NUCLEAR_THERMAL_ENGINE.get()) ? Tech.ADVANCED
                    : engines.is(ModItems.KEROLOX_ENGINE.get()) ? Tech.TURBOPUMP
                            : engines.is(ModItems.PRESSURE_FED_ENGINE.get()) || engines.is(ModItems.HYPERGOLIC_ENGINE.get()) ? Tech.PRESSURE_FED
                                    : Tech.SOLID;
            tech = own.ordinal() > tech.ordinal() ? own : tech;
        }
        return tech;
    }

    public Tech tech() {
        return hasRocket() ? techOf(rocket) : Tech.SOLID;
    }

    /** Durée pendant laquelle des moteurs mis en froid le restent, en ticks. */
    private static final int CHILL_TICKS = 120 * 20;
    /** Instant jusqu'auquel les moteurs sont en froid. */
    private long chillUntil;
    /** Vrai si le lanceur est passé sur son alimentation interne. */
    private boolean internalPower;

    /** Vrai si les moteurs de cette fusée demandent une mise en froid avant l'allumage. */
    public boolean needsChill() {
        return tech().ordinal() >= Tech.TURBOPUMP.ordinal();
    }

    public boolean isChilled() {
        return level != null && chillUntil > level.getGameTime();
    }

    /** Secondes pendant lesquelles les moteurs restent en froid ; -1 si cette fusée n'en a pas besoin. */
    public int chillSeconds() {
        return !needsChill() ? -1 : level == null ? 0 : (int) Math.max(0, (chillUntil - level.getGameTime() + 19) / 20);
    }

    /** Vrai si ce lanceur doit passer sur son alimentation interne avant l'allumage. */
    public boolean needsInternal() {
        return tech() == Tech.ADVANCED;
    }

    /** Alimentation du lanceur : -1 s'il n'a pas à en changer, 0 sur le sol, 1 sur ses batteries. */
    public int internalState() {
        return !needsInternal() ? -1 : internalPower ? 1 : 0;
    }

    /** Durée du tour de table, en ticks, et temps pendant lequel son verdict reste valable. */
    /**
     * Surpression : une fois le plein fait, des vannes laissées ouvertes laissent le parc à ergols pousser dans des
     * réservoirs pleins. Sans rien qui arrive (parc vide, tuyau coupé), il ne se passe rien. La pression monte, les soupapes crachent, l'alarme sonne ; au bout de trente secondes,
     * les réservoirs cèdent et la fusée explose sur son pas de tir. Vannes refermées, la pression retombe.
     */
    public static final int OVERPRESSURE_TICKS = 30 * 20;
    private int overpressure;
    /**
     * Durée du son de surpression, en ticks (4,36 s), et retard avec lequel l'explosion s'entend après la rupture
     * (le temps que son bruit arrive). Le son est lancé pour finir à l'instant où l'explosion se fait entendre. À
     * reprendre si son fichier change de longueur : l'outil des sons donne la valeur.
     */
    private static final int OVERPRESSURE_SOUND_TICKS = 87;
    private static final int EXPLOSION_HEARD_AFTER = 4;
    /** Vrai tant que ce son joue : refermer les vannes le fait taire. */
    private boolean overpressureSound;
    /** Dernier instant (compté en ticks de la table) où un ergol est arrivé dans un réservoir déjà plein. */
    private long overflowAt = Long.MIN_VALUE / 2;
    /** Essais du pas de tir en cours : instant où les bras reviennent, instant où l'alarme se tait, et déluge d'essai. */
    private long armTestUntil;
    private long alarmTestUntil;
    private boolean delugeTest;
    private static final int TEST_DELUGE_TICKS = 160;
    private static final int TEST_ARMS_TICKS = 110;
    /** L'essai d'alarme : trois alarmes, l'une après l'autre, sans se chevaucher. */
    private static final int TEST_ALARM_TICKS = 3 * ALARM_REPEAT_TICKS;

    /** Surpression à partir de laquelle l'alarme du pas de tir sonne : 70 %. */
    private static final int OVERPRESSURE_WARNING = OVERPRESSURE_TICKS * 7 / 10;
    /** Les soupapes crachent trois fois, à ces surpressions (en %) : assez pour qu'on les remarque, sans ressasser. */
    private static final int[] VENT_AT = {30, 45, 60};

    /** Fait taire ce son pour les joueurs proches du pas de tir. */
    private void hush(ServerLevel level, net.minecraft.sounds.SoundEvent sound) {
        var quiet = new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(sound.location(), SoundSource.BLOCKS);
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().closerThan(worldPosition, 96.0)) {
                player.connection.send(quiet);
            }
        }
    }

    /** Surpression des réservoirs, de 0 à 100 : à 100, ils cèdent. */
    public int overpressurePercent() {
        return Math.clamp(100 * overpressure / OVERPRESSURE_TICKS, 0, 100);
    }

    /**
     * Essai du pas de tir, demandé depuis le pupitre : le déluge, les bras ou l'alarme, pour les voir fonctionner
     * avant le jour du lancement. Il se fait au repos, avec ou sans fusée.
     *
     * @return ce qui empêche l'essai, ou {@code null} s'il est lancé
     */
    private Component test(LaunchStep step) {
        if (level == null || phase != Phase.IDLE) {
            return Component.translatable("sequence.orbitalis.too_late");
        }
        if (!isOperational()) {
            return Component.translatable("sequence.orbitalis.test.no_pad");
        }
        long now = level.getGameTime();
        switch (step) {
            case TEST_DELUGE -> {
                if (!hasDeluge()) {
                    return Component.translatable("sequence.orbitalis.no_deluge");
                }
                if (isDelugeOn()) {
                    return Component.translatable("sequence.orbitalis.test.running");
                }
                int cost = delugeNeed() / 4;
                if (water < cost) {
                    return Component.translatable("sequence.orbitalis.no_water", water, cost);
                }
                water -= cost;
                delugeUntil = now + TEST_DELUGE_TICKS;
                delugeTest = true;
                level.playSound(null, worldPosition, ModSounds.PAD_VALVE.get(), SoundSource.BLOCKS, 2.0F, 0.7F);
            }
            case TEST_ARMS -> {
                if (retracted) {
                    return Component.translatable("sequence.orbitalis.test.arm_away");
                }
                if (valvesOpen) {
                    return Component.translatable("sequence.orbitalis.test.valves");
                }
                retracted = true;
                armTestUntil = now + TEST_ARMS_TICKS;
                level.playSound(null, worldPosition, ModSounds.PAD_HYDRAULIC.get(), SoundSource.BLOCKS, 2.5F, 1.0F);
                updateGangways(level, isStructureManaged());
            }
            default -> {
                alarmTestUntil = now + TEST_ALARM_TICKS;
                level.playSound(null, worldPosition, ModSounds.WARNING_OVERPRESSURE.get(), SoundSource.BLOCKS, 3.0F, 1.0F);
            }
        }
        sync();
        return null;
    }

    public static final int POLL_TICKS = 70;
    public static final int POLL_VALID_TICKS = 90 * 20;
    /** Postes du tour de table, dans l'ordre où ils répondent. */
    public static final int POLL_STATIONS = 6;

    /** Opération en cours au sol : l'étape, et les instants où elle a commencé et où elle se termine. */
    private int busyStep = -1;
    private long busyFrom;
    private long busyUntil;
    /** Instant où le dernier tour de table a commencé ; -1 s'il n'y en a pas eu. */
    private long pollStart = -1;
    /** Verdict du tour de table, arrêté quand le dernier poste a répondu. */
    private boolean pollDone;
    private boolean pollGo;

    /** Temps que prend une opération au sol, en ticks : rien d'autre ne se commande tant qu'elle dure. */
    private static int operationTicks(LaunchStep step) {
        return switch (step) {
            // Les vannes s'ouvrent et se referment d'un geste : c'est le plein lui-même qu'on regarde monter.
            case VALVES -> 0;
            case PRESSURIZE -> 40;
            // Le bras et la passerelle prennent le temps de s'écarter : on les voit bouger.
            case RETRACT -> 80;
            case DELUGE -> 25;
            case POLL -> POLL_TICKS;
            case CHILL -> 50;
            case INTERNAL -> 25;
            case TEST_DELUGE -> TEST_DELUGE_TICKS;
            case TEST_ARMS -> TEST_ARMS_TICKS + 80;
            case TEST_ALARM -> TEST_ALARM_TICKS;
            default -> 0;
        };
    }

    /** Étape de l'opération en cours au sol (rang dans {@link LaunchStep}), ou -1. */
    public int busyStep() {
        return level != null && level.getGameTime() < busyUntil ? busyStep : -1;
    }

    /** Avancement de l'opération en cours, de 0 à 100. */
    public int busyPercent() {
        if (busyStep() < 0 || busyUntil <= busyFrom) {
            return 0;
        }
        return (int) Math.clamp(100 * (level.getGameTime() - busyFrom) / (busyUntil - busyFrom), 0, 100);
    }

    /**
     * Réponse d'un poste du tour de table, maintenant : les ergols, la pression, les moteurs (en froid), le pas de
     * tir (bras retiré), la météo, le lanceur (prêt, et sur son alimentation interne s'il le faut).
     */
    public boolean stationGo(int station) {
        return switch (station) {
            case 0 -> !isLiquid() || fuelPercent() >= 100;
            case 1 -> !isLiquid() || isPressurized();
            case 2 -> !needsChill() || isChilled();
            case 3 -> retracted || isTestPad();
            case 4 -> isWeatherGo();
            default -> hasRocket() && isOperational() && rocketStats().isFlightReady() && isMassAllowed()
                    && usableTowerHeight() >= requiredTowerHeight() && (!needsInternal() || internalPower);
        };
    }

    /**
     * Où en est le tour de table : -1 s'il n'y en a pas (ou s'il est périmé), de 0 à 99 pendant qu'il se déroule,
     * 100 si tous les postes ont dit « prêt », 101 si l'un d'eux a dit non.
     */
    public int pollState() {
        if (level == null || pollStart < 0 || phase != Phase.IDLE) {
            return -1;
        }
        long since = level.getGameTime() - pollStart;
        if (since < POLL_TICKS) {
            return (int) Math.clamp(100 * since / POLL_TICKS, 0, 99);
        }
        if (since > POLL_TICKS + POLL_VALID_TICKS) {
            return -1;
        }
        if (!pollDone) {
            // Le dernier poste vient de répondre : le verdict est arrêté.
            pollDone = true;
            pollGo = true;
            for (int station = 0; station < POLL_STATIONS; station++) {
                pollGo &= stationGo(station);
            }
            announce(Component.translatable(pollGo ? "sequence.orbitalis.poll.go" : "sequence.orbitalis.poll.no_go")
                    .withStyle(pollGo ? ChatFormatting.GREEN : ChatFormatting.GOLD));
        }
        return pollGo ? 100 : 101;
    }

    /**
     * Étape demandée par un joueur, depuis le pupitre ou la cabine. C'est {@link #command}, au rythme d'une vraie
     * équipe de lancement : une opération au sol prend quelques secondes (les vannes s'ouvrent, la pression monte,
     * le bras s'écarte), et rien d'autre ne se commande tant qu'elle dure. Le tour de table est facultatif : s'il a
     * eu lieu et qu'un poste a dit « pas prêt », l'allumage est refusé ; s'il n'a pas eu lieu, personne ne retient
     * l'équipe, et ce qu'elle a oublié se paie au décollage (un plein incomplet : la fusée explose en vol). Les
     * étapes en vol et l'arrêt passent tels quels.
     *
     * @return ce qui empêche l'étape, ou {@code null} si elle est lancée
     */
    public Component request(LaunchStep step, String agency) {
        boolean ground = step.ordinal() <= LaunchStep.IGNITE.ordinal() || step == LaunchStep.POLL || step == LaunchStep.CHILL
                || step == LaunchStep.INTERNAL || step.isTest();
        if (level == null || !ground || phase != Phase.IDLE) {
            return step == LaunchStep.POLL ? Component.translatable("sequence.orbitalis.too_late") : command(step, agency);
        }
        long now = level.getGameTime();
        if (busyStep() >= 0) {
            return Component.translatable("sequence.orbitalis.busy", Component.translatable("sequence.orbitalis.step."
                    + LaunchStep.values()[busyStep].name().toLowerCase(java.util.Locale.ROOT)));
        }
        if (step == LaunchStep.POLL) {
            if (!hasRocket()) {
                return Component.translatable("sequence.orbitalis.no_rocket");
            }
            refresh(level);
            pollStart = now;
            pollDone = false;
            level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 2.0F, 0.8F);
        } else {
            if (step == LaunchStep.IGNITE) {
                // Le tour de table protège qui l'a fait : un poste qui a dit non retient l'allumage. Sans tour de
                // table, rien ne le retient.
                if (pollState() == 101) {
                    return Component.translatable("sequence.orbitalis.poll.refused");
                }
                if (needsChill() && !isChilled()) {
                    // Le froid n'a pas tenu jusqu'à l'allumage : rien ne démarre. Sans dommage.
                    level.playSound(null, worldPosition, ModSounds.PAD_VALVE.get(), SoundSource.BLOCKS, 2.0F, 0.6F);
                    return Component.translatable("sequence.orbitalis.misfire_warm");
                }
            }
            boolean unchecked = step == LaunchStep.IGNITE && pollState() != 100;
            Component refused = command(step, agency);
            if (refused != null) {
                return refused;
            }
            if (unchecked) {
                announce(Component.translatable("sequence.orbitalis.poll.missing").withStyle(ChatFormatting.GOLD));
            }
        }
        int ticks = operationTicks(step);
        if (ticks > 0) {
            busyStep = step.ordinal();
            busyFrom = now;
            busyUntil = now + ticks;
        }
        sync();
        return null;
    }

    /**
     * Exécute une étape de la séquence de lancement, demandée depuis le pupitre ou depuis la cabine. Rien n'est
     * laissé au hasard : chaque étape a un effet précis, et l'oublier ou la faire au mauvais moment a une
     * conséquence précise, toujours la même.
     *
     * @return ce qui empêche l'étape, ou {@code null} si elle est faite
     */
    public Component command(LaunchStep step, String agency) {
        if (step == LaunchStep.ASSIST) {
            // Le niveau d'aide suivant, parmi ceux que l'agence maîtrise ; il ne se change pas moteurs allumés.
            if (level == null || level.getServer() == null || phase != Phase.IDLE) {
                return Component.translatable("sequence.orbitalis.too_late");
            }
            var research = ResearchManager.forAgency(level.getServer(), agency);
            int allowed = research.isUnlocked(RESEARCH_AUTOMATIC) ? 2 : research.isUnlocked(RESEARCH_ASSISTED) ? 1 : 0;
            if (allowed == 0) {
                assist = 0;
                sync();
                return Component.translatable("sequence.orbitalis.assist.locked", Component.translatable("research.orbitalis.node." + RESEARCH_ASSISTED));
            }
            assist = assist >= allowed ? 0 : assist + 1;
            sync();
            return null;
        }
        if (step.isTest()) {
            return test(step);
        }
        if (level == null || !hasRocket()) {
            return Component.translatable("sequence.orbitalis.no_rocket");
        }
        long now = level.getGameTime();
        switch (step) {
            case VALVES -> {
                if (phase != Phase.IDLE) {
                    return Component.translatable("sequence.orbitalis.too_late");
                }
                if (!valvesOpen && retracted) {
                    return Component.translatable("sequence.orbitalis.arm_away");
                }
                valvesOpen = !valvesOpen;
                // Les vannes de remplissage claquent : on l'entend jusqu'à 64 blocs, depuis la console.
                level.playSound(null, worldPosition, ModSounds.PAD_VALVE.get(), SoundSource.BLOCKS, 4.0F, valvesOpen ? 1.0F : 0.8F);
            }
            case CHILL -> {
                if (phase != Phase.IDLE) {
                    return Component.translatable("sequence.orbitalis.too_late");
                }
                if (!needsChill()) {
                    return Component.translatable("sequence.orbitalis.flight.not_needed");
                }
                if (retracted) {
                    // L'oxygène froid qui refroidit les moteurs vient du sol, par le bras.
                    return Component.translatable("sequence.orbitalis.arm_away");
                }
                chillUntil = now + CHILL_TICKS;
                level.playSound(null, worldPosition, ModSounds.PAD_PURGE.get(), SoundSource.BLOCKS, 2.0F, 1.3F);
            }
            case INTERNAL -> {
                if (phase != Phase.IDLE) {
                    return Component.translatable("sequence.orbitalis.too_late");
                }
                if (!needsInternal()) {
                    return Component.translatable("sequence.orbitalis.flight.not_needed");
                }
                internalPower = !internalPower;
                level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 2.0F, internalPower ? 1.4F : 0.9F);
            }
            case PRESSURIZE -> {
                if (phase != Phase.IDLE) {
                    return Component.translatable("sequence.orbitalis.too_late");
                }
                pressureUntil = now + PRESSURE_TICKS;
                level.playSound(null, worldPosition, ModSounds.PAD_PURGE.get(), SoundSource.BLOCKS, 2.0F, 1.0F);
            }
            case RETRACT -> {
                if (phase == Phase.FLIGHT || (phase == Phase.COUNTDOWN && retracted)) {
                    return Component.translatable("sequence.orbitalis.too_late");
                }
                retracted = !retracted;
                // Bras débranché : plus rien n'entre dans la fusée.
                valvesOpen &= !retracted;
                level.playSound(null, worldPosition, ModSounds.PAD_HYDRAULIC.get(), SoundSource.BLOCKS, 2.5F, 1.0F);
                updateGangways(level, isStructureManaged());
            }
            case DELUGE -> {
                if (phase == Phase.FLIGHT) {
                    return Component.translatable("sequence.orbitalis.too_late");
                }
                if (!hasDeluge()) {
                    return Component.translatable("sequence.orbitalis.no_deluge");
                }
                // Un déluge d'essai qui coule encore ne compte pas : le vrai prend sa suite.
                if (isDelugeOn() && !delugeTest) {
                    return null;
                }
                delugeTest = false;
                if (water < delugeNeed()) {
                    return Component.translatable("sequence.orbitalis.no_water", water, delugeNeed());
                }
                water -= delugeNeed();
                // Le déluge coule jusqu'au décollage (son bruit est une boucle que tient le client, voir DelugeSound).
                delugeUntil = now + DELUGE_TICKS;
                level.playSound(null, worldPosition, ModSounds.PAD_VALVE.get(), SoundSource.BLOCKS, 2.0F, 0.7F);
            }
            case IGNITE -> {
                if (phase != Phase.IDLE) {
                    return Component.translatable("sequence.orbitalis.too_late");
                }
                refresh(level);
                if (!isReadyToIgnite()) {
                    return Component.translatable(isWeatherGo() ? "sequence.orbitalis.not_ready" : "launch.orbitalis.scrubbed_weather");
                }
                if (isLiquid() && !isPressurized()) {
                    // Sans pression, les ergols n'arrivent pas aux moteurs : rien ne s'allume. Sans dommage.
                    level.playSound(null, worldPosition, ModSounds.PAD_VALVE.get(), SoundSource.BLOCKS, 2.0F, 0.6F);
                    announce(Component.translatable("sequence.orbitalis.misfire").withStyle(ChatFormatting.GOLD));
                    return Component.translatable("sequence.orbitalis.misfire");
                }
                agencyKey = agency;
                autoSequence = false;
                valvesOpen = false;
                holdTicks = 0;
                phase = Phase.COUNTDOWN;
                timer = IGNITION_TICKS;
                // L'horloge est calée sur T-3 s : tout ce qui suit le compte à rebours (lueur, chaleur, sons) s'y retrouve.
                phaseStart = now - (COUNTDOWN_TICKS - IGNITION_TICKS);
                lastResult = Result.NONE;
                lastPoints = 0;
                announce(Component.translatable("launch.orbitalis.ignition").withStyle(ChatFormatting.YELLOW));
            }
            case RELEASE -> {
                if (phase != Phase.COUNTDOWN || timer > IGNITION_TICKS || !(level instanceof ServerLevel serverLevel)) {
                    return Component.translatable("sequence.orbitalis.not_ignited");
                }
                release(serverLevel);
                return null;
            }
            case ABORT -> {
                if (!abort()) {
                    return Component.translatable("sequence.orbitalis.too_late");
                }
                return null;
            }
            case THROTTLE, STAGE, FAIRING, CUTOFF -> {
                int bit = switch (step) {
                    case THROTTLE -> FLIGHT_THROTTLE;
                    case STAGE -> FLIGHT_STAGE;
                    case FAIRING -> FLIGHT_FAIRING;
                    default -> FLIGHT_CUTOFF;
                };
                if (phase != Phase.FLIGHT || (flightNeeded() & bit) == 0) {
                    return Component.translatable("sequence.orbitalis.flight.not_needed");
                }
                // Hors de sa fenêtre, une étape est refusée, sans dommage : il suffit de ne pas l'oublier quand elle s'ouvre.
                if ((flightOpen() & bit) == 0) {
                    return Component.translatable((flightDone & bit) != 0 && bit != FLIGHT_STAGE
                            ? "sequence.orbitalis.flight.already" : "sequence.orbitalis.flight.not_yet");
                }
                if (bit == FLIGHT_STAGE) {
                    beginStaging();
                } else {
                    if (bit == FLIGHT_THROTTLE) {
                        throttle = Math.min(throttle, MAXQ_THROTTLE);
                        throttleProgram = true;
                    }
                    flightDone |= bit;
                    announce(Component.translatable("sequence.orbitalis.flight.done." + step.name().toLowerCase(java.util.Locale.ROOT))
                            .withStyle(ChatFormatting.GREEN));
                }
                manualSteps++;
                level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 2.0F, 1.2F);
            }
        }
        sync();
        return null;
    }

    // ------------------------------------------------------------------
    // Étapes en vol
    // ------------------------------------------------------------------

    /**
     * Ticks de vol auxquels s'ouvre la fenêtre de chaque séparation, dans l'ordre. Ils viennent du vol calculé, mais
     * jamais serrés les uns contre les autres : la première séparation attend que la fusée soit sortie de l'air
     * dense, bien après le décollage, et chacune laisse à la précédente le temps de se dérouler en entier.
     */
    public List<Integer> stagingTicks() {
        List<Integer> ticks = new ArrayList<>();
        FlightSimulation flight = FlightProfile.flight(rocketStats());
        int floor = MAXQ_TO + 60;
        for (FlightSimulation.Event event : flight.events()) {
            if (event.type() == FlightSimulation.EventType.STAGING) {
                int tick = Math.max(floor, (int) (FlightProfile.gameProgress(rocketStats(), event.time()) * flightTicks()));
                ticks.add(tick);
                floor = tick + STAGE_GAP;
            }
        }
        // La dernière séparation se fait avant la coupure finale ; celles d'avant reculent s'il le faut.
        int cap = flightTicks() - CUTOFF_WINDOW - 20;
        for (int k = ticks.size() - 1; k >= 0; k--) {
            ticks.set(k, Math.min(ticks.get(k), cap));
            cap = ticks.get(k) - STAGE_GAP;
        }
        return ticks;
    }

    /** Tick de vol où la fusée entre dans l'espace, ou -1 si elle n'y va pas. */
    private int spaceTick() {
        FlightSimulation flight = FlightProfile.flight(rocketStats());
        for (FlightSimulation.Event event : flight.events()) {
            if (event.type() == FlightSimulation.EventType.SPACE) {
                return (int) Math.min(flightTicks() - 60, FlightProfile.gameProgress(rocketStats(), event.time()) * flightTicks());
            }
        }
        return -1;
    }

    /**
     * Étapes que ce vol demande : réduire les gaz pour une fusée à ergols liquides, séparer s'il y a plusieurs
     * étages, larguer la coiffe si une fusée sans équipage va dans l'espace, couper les moteurs pour un vol orbital.
     */
    public int flightNeeded() {
        if (!hasRocket()) {
            return 0;
        }
        int needed = requiredFuel().isEmpty() ? 0 : FLIGHT_THROTTLE;
        needed |= stagingTicks().isEmpty() ? 0 : FLIGHT_STAGE;
        needed |= !isCrewed() && spaceTick() >= 0 ? FLIGHT_FAIRING : 0;
        needed |= rocketStats().reach().ordinal() >= RocketStats.Reach.LOW_ORBIT.ordinal() ? FLIGHT_CUTOFF : 0;
        return needed;
    }

    /** Étapes dont la fenêtre est ouverte en ce moment : c'est maintenant qu'il faut les commander. */
    public int flightOpen() {
        if (phase != Phase.FLIGHT) {
            return 0;
        }
        int needed = flightNeeded();
        int open = 0;
        if ((needed & FLIGHT_THROTTLE) != 0 && (flightDone & FLIGHT_THROTTLE) == 0 && timer >= MAXQ_FROM && timer < MAXQ_TO) {
            open |= FLIGHT_THROTTLE;
        }
        List<Integer> stagings = stagingTicks();
        // Une séparation à la fois : la suivante attend que l'étage d'après soit allumé.
        if (!staging() && stageCuts.length < stagings.size() && timer >= stagings.get(stageCuts.length)) {
            open |= FLIGHT_STAGE;
        }
        if ((needed & FLIGHT_FAIRING) != 0 && (flightDone & FLIGHT_FAIRING) == 0 && timer >= spaceTick()) {
            open |= FLIGHT_FAIRING;
        }
        if ((needed & FLIGHT_CUTOFF) != 0 && (flightDone & FLIGHT_CUTOFF) == 0 && timer >= flightTicks() - CUTOFF_WINDOW) {
            open |= FLIGHT_CUTOFF;
        }
        return open;
    }

    /** Secondes avant que ne se referme la fenêtre ouverte la plus pressée ; 0 si aucune n'est ouverte. */
    public int flightWindowSeconds() {
        int open = flightOpen();
        int left = Integer.MAX_VALUE;
        if ((open & FLIGHT_THROTTLE) != 0) {
            left = Math.min(left, MAXQ_TO - timer);
        }
        if ((open & FLIGHT_STAGE) != 0) {
            left = Math.min(left, stagingTicks().get(stageCuts.length) + STAGE_WINDOW - timer);
        }
        if ((open & (FLIGHT_FAIRING | FLIGHT_CUTOFF)) != 0) {
            left = Math.min(left, flightTicks() - timer);
        }
        return left == Integer.MAX_VALUE ? 0 : Math.max(0, (left + 19) / 20);
    }

    /**
     * État des étapes en vol, pour les écrans : les étapes demandées (bits 0 à 3), faites (4 à 7), ouvertes (8 à 11)
     * et manquées (12 à 15), puis le nombre de séparations passées (16 à 19) et à faire en tout (20 à 23), et enfin
     * le bit 24 : moteurs coupés, entre deux étages.
     */
    public int flightBits() {
        if (phase != Phase.FLIGHT) {
            return 0;
        }
        int stagings = stagingTicks().size();
        int done = flightDone | (stagings > 0 && stagesPassed >= stagings && (flightMissed & FLIGHT_STAGE) == 0 ? FLIGHT_STAGE : 0);
        return flightNeeded() | done << 4 | flightOpen() << 8 | flightMissed << 12 | Math.min(15, stagesPassed) << 16 | Math.min(15, stagings) << 20
                | (enginesOut(timer) ? 1 : 0) << 24;
    }

    /**
     * Suivi des étapes en vol : une fenêtre qui se referme sans que l'étape ait été faite a sa conséquence, toujours
     * la même, et rien ne fait l'étape à la place de l'équipe : gaz non réduits ou étage non séparé, la fusée est
     * perdue ; coiffe non larguée ou moteurs non coupés, la mission échoue. Seuls l'ordinateur de bord, au niveau
     * d'aide que l'équipe a choisi, et la séquence automatique (essais) font des étapes d'eux-mêmes.
     *
     * @return vrai si la fusée est perdue : le vol s'arrête là
     */
    private boolean tickFlightSteps(ServerLevel level) {
        // La séparation en cours suit son cours : les verrous lâchent peu après la coupure, puis l'étage suivant
        // s'allume. Les clients doivent le savoir : ils montrent l'étage se détacher, et le panache s'éteindre.
        if (stagesPassed < stageCuts.length && timer >= stageCuts[stagesPassed] + SEPARATION_DELAY) {
            stagesPassed++;
            announce(Component.translatable("sequence.orbitalis.flight.staged", stagesPassed).withStyle(ChatFormatting.GREEN));
            sync();
        }
        if (stageCuts.length > 0 && timer == stageCuts[stageCuts.length - 1] + coastTicks(stageCuts.length - 1)) {
            announce(Component.translatable("sequence.orbitalis.flight.relight", stageCuts.length + 1).withStyle(ChatFormatting.GREEN));
            sync();
        }
        if (autoSequence) {
            int open = flightOpen();
            flightDone |= open & ~FLIGHT_STAGE;
            if ((open & FLIGHT_STAGE) != 0) {
                beginStaging();
                sync();
            }
            return false;
        }
        // L'ordinateur de bord fait les étapes qui lui reviennent dès que leur fenêtre s'ouvre.
        int helped = flightOpen() & assistedSteps();
        if (helped != 0) {
            flightDone |= helped & ~FLIGHT_STAGE;
            announce(Component.translatable("sequence.orbitalis.assist.did").withStyle(ChatFormatting.AQUA));
            if ((helped & FLIGHT_STAGE) != 0) {
                beginStaging();
            }
            sync();
        }
        int needed = flightNeeded();
        // Gaz jamais réduits : la structure cède au passage de la pression maximale.
        if ((needed & FLIGHT_THROTTLE) != 0 && (flightDone & FLIGHT_THROTTLE) == 0 && timer >= MAXQ_TO) {
            flightBlast = RocketBreakup.blastRadius(rocketStats().liftoffMass(), 0.7, false);
            loseInFlight(level, "sequence.orbitalis.fail.maxq");
            return true;
        }
        // Étage vide jamais séparé : rien ne le détache à la place de l'équipe. Ses moteurs éteints, la fusée le
        // traîne, ne peut pas allumer l'étage suivant, et retombe : elle est perdue.
        List<Integer> stagings = stagingTicks();
        if (!staging() && stageCuts.length < stagings.size() && timer >= stagings.get(stageCuts.length) + STAGE_WINDOW) {
            flightMissed |= FLIGHT_STAGE;
            flightBlast = RocketBreakup.blastRadius(rocketStats().liftoffMass(), 0.4, requiredFuel().isEmpty());
            loseInFlight(level, "sequence.orbitalis.fail.stage");
            return true;
        }
        if (timer >= flightTicks()) {
            // Coiffe jamais larguée : la charge utile y reste enfermée. Moteurs jamais coupés : l'orbite est manquée.
            if ((needed & FLIGHT_FAIRING) != 0 && (flightDone & FLIGHT_FAIRING) == 0) {
                flightMissed |= FLIGHT_FAIRING;
                payloadLost = true;
                announce(Component.translatable("sequence.orbitalis.flight.missed.fairing").withStyle(ChatFormatting.RED));
            }
            if ((needed & FLIGHT_CUTOFF) != 0 && (flightDone & FLIGHT_CUTOFF) == 0) {
                flightMissed |= FLIGHT_CUTOFF;
                reachPenalty++;
                announce(Component.translatable("sequence.orbitalis.flight.missed.cutoff").withStyle(ChatFormatting.RED));
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Pilotage
    // ------------------------------------------------------------------

    /** Le pilote tient les commandes : appelé à chaque tick par sa cabine. */
    public void steer(int pitch, int yaw, int roll) {
        pitchInput = Integer.signum(pitch);
        yawInput = Integer.signum(yaw);
        rollInput = Integer.signum(roll);
        piloted = true;
    }

    /** Direction du mouvement de la fusée, de longueur 1 ; le nez tant qu'elle bouge à peine. */
    public org.joml.Vector3d prograde() {
        org.joml.Vector3d velocity = motion.velocity();
        return velocity.lengthSquared() < 1.0E-6 ? attitude.nose() : velocity.normalize();
    }

    /** Le pilote pousse la manette des gaz : vers le haut (1), vers le bas (-1), ou la laisse (0). */
    public void throttle(int direction) {
        throttleInput = Integer.signum(direction);
    }

    /** Manette des gaz, de {@link #MIN_THROTTLE} à 1. */
    public float throttle() {
        return throttle;
    }

    /** Vrai si les moteurs de cette fusée se règlent : ceux à ergols liquides ; la poudre brûle d'un bout à l'autre. */
    public boolean throttleable() {
        return hasRocket() && !requiredFuel().isEmpty();
    }

    /** Vrai pendant la traversée de la pression maximale, où la poussée doit rester sous {@link #MAXQ_THROTTLE}. */
    public boolean inMaxQ() {
        return phase == Phase.FLIGHT && timer >= MAXQ_FROM && timer < MAXQ_TO;
    }

    /** Assiette de la fusée : angle de son nez au-dessus de l'horizon, en degrés. */
    public double getPitch() {
        return attitude.pitch();
    }

    /** Maniabilité de la fusée : une fusée haute a plus d'inertie, ses tuyères la font tourner moins vite. */
    private double agility() {
        return Math.clamp(1.2 - rocketShape().height() * 0.02, 0.6, 1.1);
    }

    /**
     * Assiette à tenir à cet instant du vol : celle de la trajectoire calculée. La fusée part à la verticale, puis
     * se couche peu à peu vers l'horizon ; un vol orbital finit presque à plat, un vol suborbital reste cabré.
     */
    public double targetPitch() {
        return targetPitch(flightProgress());
    }

    /** Assiette à tenir à un avancement donné du vol (de 0 à 1) : les clients s'en servent pour leur tableau de bord. */
    public double targetPitch(double progress) {
        FlightSimulation flight = FlightProfile.flight(rocketStats());
        // La pente de la trajectoire calculée, là où en est le vol calculé à cet instant du jeu.
        double at = FlightProfile.simProgress(rocketStats(), progress);
        double before = Math.max(0.0, at - 0.01);
        double after = Math.min(1.0, at + 0.01);
        double climb = flight.altitude(after) - flight.altitude(before);
        double along = flight.downrange(after) - flight.downrange(before);
        double path = Math.abs(climb) + Math.abs(along) < 1.0 ? 90.0 : Math.clamp(Math.toDegrees(Math.atan2(climb, along)), 0.0, 90.0);
        // La fusée monte d'abord droit, le temps de dégager sa tour ; elle ne bascule vers sa trajectoire qu'ensuite,
        // progressivement.
        double clear = Math.clamp((progress * flightTicks() - clearTicks()) / PITCH_OVER_TICKS, 0.0, 1.0);
        // La consigne ne se couche pas autant que la vraie trajectoire : à l'échelle du jeu, une fusée presque à
        // plat ne monterait plus. Elle garde une bonne part de son inclinaison jusqu'au bout.
        return 90.0 + (path - 90.0) * LEAN * clear * clear * (3.0 - 2.0 * clear);
    }

    /**
     * Durée de la montée verticale, en ticks : le temps qu'il faut au pied de la fusée pour passer au-dessus de sa
     * tour, avec de la marge. Une fusée habitée tourne autour de sa cabine : en se couchant, son pied part en
     * arrière, et balaierait le pas de tir s'il n'était pas déjà plus haut que tout.
     */
    private double clearTicks() {
        // Les mâts paratonnerres dépassent la tour : on compte large, et jamais moins que la hauteur de la fusée.
        double top = Math.max(complex.towerHeight() * 1.25, hasRocket() ? rocketShape().height() : 0.0) + 14.0;
        return Math.max(TOWER_CLEAR_TICKS, Math.sqrt(top / ASCENT_RATE) * 20.0);
    }

    /** Hauteur dont la fusée s'est élevée, côté serveur : celle où on la voit. Habitée, elle suit sa capsule. */
    private double flightClimb() {
        CapsuleEntity capsule = crewCapsule();
        return capsule != null && capsule.isFollowingRocket() ? capsule.getY() - capsule.baseHeight() : motion.offset().y;
    }

    /**
     * Nombre de points de la fusée qui se trouvent dans un bloc solide : son axe, tronçon par tronçon, et quatre
     * points sur son flanc. Une fusée penchée trop tôt, ou trop bas, heurte ainsi sa tour, un mât ou le relief.
     */
    private int collisions(ServerLevel level) {
        RocketShape shape = rocketShape();
        org.joml.Quaternionf turned = attitude.orientation();
        float pivot = pivotHeight();
        float back = pivotBack();
        float flank = shape.isWide() ? 1.2F : 0.3F;
        double footY = worldPosition.getY() + 1.0 + Math.max(0.0, flightClimb());
        float[][] around = {{0.0F, 0.0F}, {flank, 0.0F}, {-flank, 0.0F}, {0.0F, flank}, {0.0F, -flank}};
        org.joml.Vector3d moved = motion.offset();
        int hits = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int i = 0; i < shape.height(); i++) {
            for (float[] side : around) {
                org.joml.Vector3f point = turned.transform(new org.joml.Vector3f(side[0], i + 0.5F - pivot, side[1] - back));
                at.set(Mth.floor(worldPosition.getX() + 0.5 + moved.x + point.x), Mth.floor(footY + pivot + point.y),
                        Mth.floor(worldPosition.getZ() + 0.5 + moved.z + back + point.z));
                if (at.equals(worldPosition) || level.isOutsideBuildHeight(at) || !level.isLoaded(at)) {
                    continue;
                }
                BlockState state = level.getBlockState(at);
                if (!state.isAir() && state.blocksMotion()) {
                    hits++;
                }
            }
        }
        return hits;
    }

    /** Vitesse dont la fusée dispose en plus de ce que demande sa destination, en m/s : ce que le pilote peut se permettre de perdre. */
    private double deltaVMargin() {
        RocketStats stats = rocketStats();
        // Sous l'orbite, on vise une altitude : un cinquième de la vitesse peut se perdre sans la manquer.
        double needed = stats.reach().deltaV() > 0 ? stats.reach().deltaV() : stats.totalDeltaV() * 0.8;
        return Math.max(0.0, stats.totalDeltaV() - needed) + LOSS_GRACE;
    }

    /** Marge de vitesse qu'il reste au pilote, en m/s ; négative, la destination est manquée. */
    public double deltaVLeft() {
        return deltaVMargin() - steerLoss;
    }

    public double steerLoss() {
        return steerLoss;
    }

    /**
     * La manette des gaz, à chaque tick du vol. Sans aide, elle est à l'équipe : le pilote la tient, et personne ne la
     * touche à sa place. Avec l'aide (assistée ou automatique), pendant la séquence automatique, ou une fois l'étape
     * « réduire les gaz » commandée, l'ordinateur de bord la ramène à {@link #MAXQ_THROTTLE} pour la pression maximale,
     * puis à fond.
     * Descendre sous {@link #MAXQ_THROTTLE} pendant la pression maximale fait l'étape « réduire les gaz » ; la
     * remonter au-dessus, une fois l'étape faite et tant que l'air presse encore, fatigue la structure.
     */
    private void tickThrottle(ServerLevel level, boolean maxQ) {
        int input = throttleInput;
        throttleInput = 0;
        if (!throttleable()) {
            throttle = 1.0F;
            return;
        }
        if (input != 0) {
            throttleProgram = false;
        }
        if (assist == 0 && !autoSequence && !throttleProgram) {
            throttle = Math.clamp(throttle + input * THROTTLE_RATE, MIN_THROTTLE, 1.0F);
        } else {
            float goal = maxQ ? MAXQ_THROTTLE : 1.0F;
            throttle += Math.clamp(goal - throttle, -THROTTLE_RATE, THROTTLE_RATE);
        }
        if (!maxQ) {
            return;
        }
        if ((flightDone & FLIGHT_THROTTLE) == 0) {
            if (throttle <= MAXQ_THROTTLE + 0.001F) {
                flightDone |= FLIGHT_THROTTLE;
                announce(Component.translatable("sequence.orbitalis.flight.done.throttle").withStyle(ChatFormatting.GREEN));
                sync();
            }
        } else if (throttle > MAXQ_THROTTLE + 0.05F) {
            aeroStress += 1;
        }
    }

    /**
     * Le pilote, à bord : ses touches de direction, ce tick. Les QTE n'ont lieu qu'en vol piloté (manuel ou assisté).
     */
    public void qteInput(int bits) {
        qteInputs = bits;
        qtePilot = !autoSequence && assist < 2;
    }

    /**
     * Les QTE, à chaque tick du vol : leur tirage au premier tick piloté, leur départ, la touche tenue ou pressée, et
     * l'écart qui en découle.
     */
    private void tickQte(ServerLevel level) {
        boolean pilot = qtePilot;
        qtePilot = false;
        if (qteAt == null && pilot) {
            scheduleQte(level);
        }
        // Un QTE prévu part à son heure, même si la fusée se remet encore du précédent : sans cela, il serait perdu.
        if (qteAt != null && qteState != QTE_ACTIVE) {
            for (int k = 0; k < qteAt.length; k++) {
                if (timer == qteAt[k]) {
                    qteIndex = k;
                    qteState = QTE_ACTIVE;
                    qteSince = timer;
                    qteHeld = 0;
                    qteWasDown = true;
                }
            }
        }
        int age = timer - qteSince;
        switch (qteState) {
            case QTE_ACTIVE -> {
                boolean down = pilot && (qteInputs & 1 << qteKey[qteIndex]) != 0;
                boolean success;
                if (qteHold[qteIndex]) {
                    // Tenir : relâcher remet le compte à zéro.
                    qteHeld = down ? qteHeld + 1 : 0;
                    success = qteHeld >= QTE_HOLD;
                } else {
                    // Presser : un nouvel appui (une touche déjà tenue au départ ne compte pas).
                    success = down && !qteWasDown;
                    qteWasDown = down;
                }
                qteDrift = Math.min(QTE_LEAN, qteDrift + QTE_LEAN / 60.0);
                if (success) {
                    qteState = QTE_DONE;
                    qteSince = timer;
                } else if (age >= QTE_WINDOW) {
                    qteState = QTE_FAILED;
                    qteSince = timer;
                }
            }
            case QTE_DONE -> {
                qteDrift *= 0.85;
                if (age >= 40) {
                    qteState = QTE_NONE;
                    qteDrift = 0.0;
                }
            }
            case QTE_FAILED -> {
                // La fusée part de travers, reste un instant ainsi, puis l'ordinateur la redresse.
                if (age < 25) {
                    qteDrift = Math.min(QTE_FAIL_LEAN, qteDrift + (QTE_FAIL_LEAN - QTE_LEAN) / 25.0);
                } else if (age >= 50) {
                    qteDrift *= 0.93;
                }
                if (age >= 120) {
                    qteState = QTE_NONE;
                    qteDrift = 0.0;
                }
            }
            default -> qteDrift = 0.0;
        }
    }

    /**
     * Tire les QTE du vol : deux ou trois, chacun à un instant pris au hasard entre la sortie de la tour et la coupure
     * finale, loin des fenêtres des étapes du vol (pression maximale, séparations, coiffe, coupure) et des autres QTE.
     */
    private void scheduleQte(ServerLevel level) {
        net.minecraft.util.RandomSource random = level.getRandom();
        java.util.List<int[]> busy = new java.util.ArrayList<>();
        int needed = flightNeeded();
        if ((needed & FLIGHT_THROTTLE) != 0) {
            busy.add(new int[] {MAXQ_FROM, MAXQ_TO});
        }
        java.util.List<Integer> stagings = stagingTicks();
        for (int k = 0; k < stagings.size(); k++) {
            busy.add(new int[] {stagings.get(k), stagings.get(k) + STAGE_WINDOW + coastTicks(k) + SEPARATION_DELAY});
        }
        if ((needed & FLIGHT_FAIRING) != 0 && spaceTick() >= 0) {
            busy.add(new int[] {spaceTick(), spaceTick() + STAGE_WINDOW});
        }
        int from = Math.max(timer + 40, (int) clearTicks() + 40);
        int to = flightTicks() - CUTOFF_WINDOW - QTE_WINDOW - 20;
        int count = 2 + random.nextInt(2);
        java.util.List<Integer> chosen = new java.util.ArrayList<>();
        for (int attempt = 0; attempt < 60 && chosen.size() < count && to > from; attempt++) {
            int at = from + random.nextInt(to - from);
            boolean free = true;
            for (int[] window : busy) {
                free &= at + QTE_WINDOW + 20 < window[0] || at > window[1] + 20;
            }
            for (int other : chosen) {
                free &= Math.abs(at - other) > QTE_GAP;
            }
            if (free) {
                chosen.add(at);
            }
        }
        java.util.Collections.sort(chosen);
        qteAt = new int[chosen.size()];
        qteKey = new int[chosen.size()];
        qteHold = new boolean[chosen.size()];
        for (int k = 0; k < chosen.size(); k++) {
            qteAt[k] = chosen.get(k);
            qteKey[k] = random.nextInt(4);
            qteHold[k] = random.nextBoolean();
        }
    }

    /** La route que tient l'ordinateur, détournée par le QTE en cours : du côté opposé à la touche qui la corrige. */
    private org.joml.Vector3d drifted(org.joml.Vector3d course) {
        int key = qteIndex >= 0 && qteKey != null ? qteKey[qteIndex] : 0;
        org.joml.Vector3d axis = key < 2 ? new org.joml.Vector3d(0.0, 1.0, 0.0).cross(attitude.downrange()) : new org.joml.Vector3d(0.0, 1.0, 0.0);
        double sign = key == 0 || key == 2 ? -1.0 : 1.0;
        return new org.joml.Quaterniond().rotationAxis(Math.toRadians(qteDrift * sign), axis.x, axis.y, axis.z).transform(new org.joml.Vector3d(course));
    }

    /**
     * Le QTE pour l'écran du pilote, en un entier : son état (2 bits), sa touche (2 bits), sa sorte (1 bit : tenir), le
     * temps qui reste pour réagir (8 bits, en ticks) et l'avancement de la touche tenue (7 bits, en %).
     */
    public int qteHud() {
        if (qteState == QTE_NONE || qteIndex < 0 || qteKey == null) {
            return 0;
        }
        int left = qteState == QTE_ACTIVE ? Math.clamp(QTE_WINDOW - (timer - qteSince), 0, 255) : 0;
        int held = qteHold[qteIndex] ? Math.clamp(100 * qteHeld / QTE_HOLD, 0, 100) : 0;
        return qteState | qteKey[qteIndex] << 2 | (qteHold[qteIndex] ? 1 : 0) << 4 | left << 5 | held << 13;
    }

    /** Pour les essais : l'état du QTE en cours, et la touche qu'il demande (bit de commande). */
    public int qteState() {
        return qteState;
    }

    public int qteKeyBit() {
        return qteIndex >= 0 && qteKey != null ? 1 << qteKey[qteIndex] : 0;
    }

    /** Fatigue de la structure, de 0 à 1 : à 1, elle cède. */
    public float stress() {
        return Math.min(1.0F, aeroStress / (float) STRESS_LIMIT);
    }

    /**
     * Le guidage, à chaque tick du vol. La poussée part dans l'axe de la fusée : tout écart entre son nez et la
     * trajectoire à tenir fait perdre de la vitesse (la part de la poussée qui ne pousse pas dans le bon sens), et,
     * dans l'air dense, met la fusée de travers dans le vent : sa structure fatigue, puis cède.
     * <p>
     * Un pilote à bord tient lui-même la fusée sur ses trois axes (voir {@link RocketAttitude}). En aide assistée,
     * le stabilisateur ramène le nez vers la consigne dès qu'il lâche les commandes ; en automatique, ou sans
     * pilote, l'ordinateur de bord suit exactement la trajectoire.
     *
     * @return vrai si la fusée est perdue
     */
    private boolean tickGuidance(ServerLevel level) {
        boolean manual = piloted && !autoSequence && assist < 2;
        // L'ordinateur de bord suit la trajectoire dans le sens du tir. Un pilote, lui, choisit son cap : on ne
        // lui demande que l'inclinaison.
        org.joml.Vector3d course = manual ? attitude.courseAlongNose(targetPitch()) : attitude.course(targetPitch());
        if (!manual) {
            attitude.follow(qteDrift == 0.0 ? course : drifted(course));
        } else {
            // L'aide assistée ramène le nez sur la trajectoire dès que le pilote lâche les commandes.
            attitude.steer(pitchInput, yawInput, rollInput, agility(), assist == 1, course);
            course = attitude.courseAlongNose(targetPitch());
        }
        piloted = false;
        pitchInput = yawInput = rollInput = 0;
        // La poussée part dans l'axe : elle emmène la fusée là où pointe son nez.
        // Une cabine sans pilote reste à la verticale de son pas de tir : personne n'est à bord pour garder en
        // mémoire le monde qu'elle survolerait, et elle s'y figerait.
        org.joml.Vector3d nose = attitude.nose();
        CapsuleEntity riding = carriesCabin() ? crewCapsule() : null;
        if (carriesCabin() && !(riding != null && riding.getFirstPassenger() instanceof net.minecraft.world.entity.player.Player)) {
            nose = new org.joml.Vector3d(0.0, nose.y, 0.0);
        }
        // Entre deux étages, moteurs coupés, rien ne pousse : la fusée continue sur son élan.
        boolean coasting = enginesOut(timer);
        boolean maxQ = inMaxQ();
        tickThrottle(level, maxQ);
        motion.step(nose, coasting ? 0.0 : throttle);
        double error = attitude.errorTo(course);
        FlightSimulation flight = FlightProfile.flight(rocketStats());
        // Le vol calculé dure plusieurs minutes : chaque tick en représente une tranche, brève au décollage, de plus
        // en plus longue ensuite.
        double seconds = FlightProfile.simSeconds(rocketStats(), flightProgress())
                - FlightProfile.simSeconds(rocketStats(), Math.max(0, timer - 1) / (double) flightTicks());
        double thrust = FlightProfile.gForce(rocketStats(), flightProgress()) * RocketStats.G0;
        // Un petit écart ne coûte rien : personne ne tient une fusée au degré près. Au-delà, la part de la poussée
        // qui ne pousse pas dans le bon sens est perdue.
        double wasted = Math.max(0.0, Math.min(error, 180.0) - FREE_ANGLE);
        if (!coasting) {
            steerLoss += 0.6 * thrust * (1.0 - Math.cos(Math.toRadians(wasted))) * seconds;
            // Moins de poussée, c'est plus de temps passé à lutter contre la pesanteur : la vitesse qu'elle reprend est
            // perdue. Pendant la pression maximale, descendre jusqu'à MAXQ_THROTTLE est prévu, et ne coûte rien de plus.
            float planned = maxQ ? MAXQ_THROTTLE : 1.0F;
            if (throttleable() && throttle < planned) {
                steerLoss += RocketStats.G0 * (planned - throttle) * seconds;
            }
        }
        // L'air n'est dense qu'au début du vol : c'est là qu'un écart d'assiette fatigue la structure.
        boolean dense = timer < DENSE_TICKS;
        if (dense && error > STRESS_ANGLE) {
            aeroStress += error > 2 * STRESS_ANGLE ? 2 : 1;
        } else if (error > TUMBLE_ANGLE) {
            // Plus haut, l'air ne compte plus, mais la fusée reste une colonne mince poussée par ses moteurs : en
            // travers de sa route, la poussée la plie ; retournée, elle pousse contre son propre élan. Aucune fusée
            // ne survit à une vrille. Seul l'écart compte, pas la vitesse à laquelle le pilote manœuvre : tenir une
            // commande pour suivre la consigne ne doit rien coûter.
            aeroStress += error > 2 * TUMBLE_ANGLE ? 3 : 1;
            tumbled = true;
        } else if (aeroStress > 0) {
            // Revenue dans l'axe, la structure se remet plus vite qu'elle n'a souffert.
            aeroStress = Math.max(0, aeroStress - 2);
        }
        // Un choc contre un bloc abîme la structure, bien plus vite que le vent : quelques dixièmes de seconde
        // de frottement, et elle cède.
        if (collisions(level) > 0) {
            aeroStress += COLLISION_STRESS + 1;
            if (!collided) {
                collided = true;
                announce(Component.translatable("sequence.orbitalis.flight.collision").withStyle(ChatFormatting.RED));
            }
        }
        if (aeroStress >= STRESS_LIMIT) {
            flightBlast = RocketBreakup.blastRadius(rocketStats().liftoffMass(), 0.6, requiredFuel().isEmpty());
            loseInFlight(level, collided ? "sequence.orbitalis.fail.collision" : tumbled && timer >= MAXQ_TO + 60 ? "sequence.orbitalis.fail.tumble" : "sequence.orbitalis.fail.attitude");
            return true;
        }
        return false;
    }

    /**
     * Ce que la fusée atteint vraiment. La vitesse perdue au pilotage est retirée de celle dont elle disposait : s'il
     * n'en reste pas assez pour sa destination, elle s'arrête au palier d'en dessous. Chaque étape manquée lui en
     * coûte un de plus.
     */
    private RocketStats.Reach achievedReach() {
        RocketStats stats = rocketStats();
        RocketStats.Reach planned = stats.reach();
        RocketStats.Reach[] reaches = RocketStats.Reach.values();
        double left = stats.totalDeltaV() + LOSS_GRACE - steerLoss;
        RocketStats.Reach flown = planned;
        if (planned.ordinal() >= RocketStats.Reach.LOW_ORBIT.ordinal()) {
            RocketStats.Reach orbital = RocketStats.Reach.orbital(left);
            flown = orbital != null ? orbital : RocketStats.Reach.SUBORBITAL;
            flown = flown.ordinal() > planned.ordinal() ? planned : flown;
        } else if (planned == RocketStats.Reach.SUBORBITAL && deltaVLeft() < 0.0) {
            flown = RocketStats.Reach.ATMOSPHERIC;
        }
        return reachPenalty <= 0 ? flown
                : reaches[Math.max(Math.min(flown.ordinal(), RocketStats.Reach.ATMOSPHERIC.ordinal()), flown.ordinal() - reachPenalty)];
    }

    /** La fusée est perdue en vol : elle éclate là où elle se trouve, et son épave retombe plus loin dans le sens du tir. */
    private void loseInFlight(ServerLevel level, String messageKey) {
        // La capsule en vol : la tour de sauvetage doit l'arracher à la fusée, sans quoi elle poursuivrait son vol
        // comme si de rien n'était, jusqu'en orbite.
        CapsuleEntity capsule = crewCapsule();
        // Hauteur dont la fusée s'est élevée : celle où on la voit. Habitée, elle suit sa capsule.
        double climbed = flightClimb();
        if (capsule != null) {
            capsule.abort();
        }
        org.joml.Vector3d moved = motion.offset();
        net.minecraft.world.phys.Vec3 impact = RocketBreakup.impactPoint(level, this, moved.x, moved.z);
        holdChunk(false);
        // Elle éclate telle qu'elle est : penchée comme son pilote la tenait, et tournant encore.
        org.joml.Vector3d turning = attitude.angularVelocity();
        breakup = RocketBreakup.inFlight(this, rocketShape(), new net.minecraft.world.phys.Vec3(worldPosition.getX() + 0.5 + moved.x,
                worldPosition.getY() + 1.0 + Math.max(0.0, climbed), worldPosition.getZ() + 0.5 + moved.z), impact, flightBlast, flightCryogenic,
                attitude.orientation(), pivotHeight(), new org.joml.Vector3f((float) turning.x, (float) turning.y, (float) turning.z));
        announce(Component.translatable("sequence.orbitalis.fail.impact",
                (int) Math.sqrt(worldPosition.distToCenterSqr(impact.x, worldPosition.getY(), impact.z))).withStyle(ChatFormatting.GOLD));
        lastResult = Result.FAILURE;
        phase = Phase.IDLE;
        timer = 0;
        rocket = ItemStack.EMPTY;
        loadedRp1 = loadedOxygen = loadedHydrogen = loadedMethane = loadedHelium3 = 0;
        flameoutAt = 0;
        resetSequence();
        sync();
        announce(Component.translatable(messageKey).withStyle(ChatFormatting.RED));
    }

    /** Remet la séquence à zéro : après un vol, un arrêt ou un accident, tout est à refaire. */
    private void resetSequence() {
        busyStep = -1;
        busyUntil = 0;
        pollStart = -1;
        pollDone = false;
        chillUntil = 0;
        internalPower = false;
        overpressure = 0;
        armTestUntil = 0;
        alarmTestUntil = 0;
        delugeTest = false;
        valvesOpen = false;
        pressureUntil = 0;
        retracted = false;
        delugeUntil = 0;
        autoSequence = false;
        holdTicks = 0;
        stageCuts = new int[0];
        flightDone = flightMissed = stagesPassed = reachPenalty = 0;
        payloadLost = false;
    }

    /** Suivi de la séquence au repos : la pression ne tient qu'un temps. */
    private void tickSequence(ServerLevel level) {
        long now = level.getGameTime();
        // Essais du pas de tir : les bras reviennent d'eux-mêmes, l'alarme sonne toutes les deux secondes.
        if (armTestUntil > 0 && now >= armTestUntil) {
            armTestUntil = 0;
            if (phase == Phase.IDLE && retracted) {
                retracted = false;
                level.playSound(null, worldPosition, ModSounds.PAD_HYDRAULIC.get(), SoundSource.BLOCKS, 2.5F, 0.9F);
                updateGangways(level, isStructureManaged());
                sync();
            }
        }
        if (alarmTestUntil > 0) {
            if (now >= alarmTestUntil) {
                alarmTestUntil = 0;
            } else if ((alarmTestUntil - now) % ALARM_REPEAT_TICKS == 0) {
                level.playSound(null, worldPosition, ModSounds.WARNING_OVERPRESSURE.get(), SoundSource.BLOCKS, 3.0F, 1.0F);
            }
        }
        if (delugeTest && !isDelugeOn()) {
            delugeTest = false;
            sync();
        }
        if (forcedOverflow) {
            forcedOverflow = valvesOpen && hasRocket();
            overflowAt = ticks;
        }
        // Surpression : le plein est fait et les vannes sont restées ouvertes.
        if (phase == Phase.IDLE && valvesOpen && hasRocket() && isLiquid() && fuelPercent() >= 99 && ticks - overflowAt <= 20 && assist < 2) {
            overpressure++;
            if (overpressure == 1) {
                announce(Component.translatable("sequence.orbitalis.overpressure.start").withStyle(ChatFormatting.GOLD));
            }
            double top = worldPosition.getY() + 1 + rocketShape().height() * 0.7;
            // Les soupapes crachent : un panache blanc, de plus en plus fourni.
            if (overpressure % 4 == 0) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, worldPosition.getX() + 0.5, top, worldPosition.getZ() + 0.5,
                        2 + overpressurePercent() / 12, 0.5, 0.3, 0.5, 0.04);
            }
            // Les dernières secondes : la montée en pression s'entend, et s'achève à l'instant où tout cède.
            if (overpressure == OVERPRESSURE_TICKS - OVERPRESSURE_SOUND_TICKS + EXPLOSION_HEARD_AFTER) {
                level.playSound(null, worldPosition, ModSounds.PAD_OVERPRESSURE.get(), SoundSource.BLOCKS, 4.0F, 1.0F);
                overpressureSound = true;
            }
            // Le souffle des soupapes, trois fois, avant que l'alarme et la montée en pression prennent le relais.
            for (int percent : VENT_AT) {
                if (overpressure == OVERPRESSURE_TICKS * percent / 100 && !overpressureSound) {
                    level.playSound(null, worldPosition, ModSounds.PAD_PURGE.get(), SoundSource.BLOCKS, 2.0F, 1.5F);
                }
            }
            // Passé 70 %, l'alarme sonne, sans s'arrêter, jusqu'à ce que les vannes se ferment ou que tout cède.
            if (overpressure >= OVERPRESSURE_WARNING && (overpressure - OVERPRESSURE_WARNING) % ALARM_REPEAT_TICKS == 0) {
                level.playSound(null, worldPosition, ModSounds.WARNING_OVERPRESSURE.get(), SoundSource.BLOCKS, 3.0F, 1.0F);
            }
            if (overpressure % 10 == 0) {
                syncPending = true;
            }
            if (overpressure >= OVERPRESSURE_TICKS) {
                overpressure = 0;
                overpressureSound = false;
                hush(level, ModSounds.WARNING_OVERPRESSURE.get());
                valvesOpen = false;
                padFailure(level, "sequence.orbitalis.overpressure.burst", rocketShape().height() / 2);
                return;
            }
        } else if (overpressure > 0) {
            // Vannes refermées : la pression retombe, trois fois plus vite qu'elle n'est montée. Sa montée se tait.
            if (overpressureSound) {
                overpressureSound = false;
                hush(level, ModSounds.PAD_OVERPRESSURE.get());
            }
            if (overpressure >= OVERPRESSURE_WARNING) {
                hush(level, ModSounds.WARNING_OVERPRESSURE.get());
            }
            overpressure = Math.max(0, overpressure - 3);
            if (overpressure % 10 == 0 || overpressure == 0) {
                syncPending = true;
            }
        }
        // En automatique, l'ordinateur de bord referme les vannes dès que le plein est fait.
        if (phase == Phase.IDLE && assist >= 2 && valvesOpen && hasRocket() && isFuelled()) {
            valvesOpen = false;
            sync();
        }
        if (phase == Phase.IDLE && pressureUntil > 0 && level.getGameTime() >= pressureUntil) {
            pressureUntil = 0;
            // La pression retombe : une part des ergols froids est partie avec elle.
            loadedOxygen -= loadedOxygen / 20;
            loadedHydrogen -= loadedHydrogen / 20;
            loadedMethane -= loadedMethane / 20;
            announce(Component.translatable("sequence.orbitalis.pressure_lost").withStyle(ChatFormatting.GOLD));
            sync();
        }
    }

    /**
     * Séquence automatique : l'horloge part de T-30 s et fait elle-même chaque étape à son heure, puis lâche la
     * fusée à T-0. Elle sert aux essais ; les joueurs, eux, mènent la séquence étape par étape ({@link #command}).
     *
     * @param agencyKey agence qui lance, et qui recevra les points de recherche
     * @return vrai si le compte à rebours a démarré
     */
    public boolean startCountdown(String agencyKey) {
        if (phase != Phase.IDLE || level == null) {
            return false;
        }
        refresh(level);
        if (!isGoForLaunch()) {
            return false;
        }
        this.agencyKey = agencyKey;
        resetSequence();
        autoSequence = true;
        pressureUntil = level.getGameTime() + COUNTDOWN_TICKS + HOLD_TICKS;
        phase = Phase.COUNTDOWN;
        timer = COUNTDOWN_TICKS;
        phaseStart = level.getGameTime();
        lastResult = Result.NONE;
        lastPoints = 0;
        sync();
        announce(Component.translatable("launch.orbitalis.countdown_started", COUNTDOWN_TICKS / 20).withStyle(ChatFormatting.AQUA));
        return true;
    }

    /**
     * Vrai si le lancement peut encore être arrêté. En séquence manuelle, on peut couper les moteurs tant que la
     * fusée est retenue par ses attaches ; la séquence automatique ne s'arrête plus une fois les moteurs allumés.
     */
    public boolean canAbort() {
        return phase == Phase.COUNTDOWN && (!autoSequence || timer > IGNITION_TICKS);
    }

    /** Arrête le lancement. Moteurs déjà allumés, ils sont coupés : ce qu'ils ont brûlé est perdu. */
    public boolean abort() {
        if (!canAbort()) {
            return false;
        }
        if (timer <= IGNITION_TICKS) {
            burnOnPad(0.15);
        }
        endCountdown(Result.ABORTED, Component.translatable("launch.orbitalis.aborted").withStyle(ChatFormatting.GOLD));
        return true;
    }

    /** Ergols brûlés par des moteurs qui tournent sur le pas de tir : une part du plein. */
    private void burnOnPad(double share) {
        loadedRp1 -= (int) (loadedRp1 * share);
        loadedOxygen -= (int) (loadedOxygen * share);
        loadedHydrogen -= (int) (loadedHydrogen * share);
        loadedMethane -= (int) (loadedMethane * share);
        loadedHelium3 -= (int) (loadedHelium3 * share);
    }

    private void endCountdown(Result result, Component message) {
        phase = Phase.IDLE;
        timer = 0;
        lastResult = result;
        resetSequence();
        if (level != null) {
            updateGangways(level, isStructureManaged());
        }
        sync();
        announce(message);
    }

    private void tickCountdown(ServerLevel level) {
        if (timer > 0) {
            timer--;
        }
        if (autoSequence) {
            int seconds = timer / 20;
            if (timer % 20 == 0) {
                // Sondages « Go / No-Go » : à T-20 et T-10, tout est vérifié à nouveau ; un défaut reporte le lancement.
                if ((seconds == 20 || seconds == 10)) {
                    refresh(level);
                    if (!isGoForLaunch()) {
                        endCountdown(Result.SCRUBBED, Component.translatable(
                                isWeatherGo() ? "launch.orbitalis.scrubbed" : "launch.orbitalis.scrubbed_weather").withStyle(ChatFormatting.GOLD));
                        return;
                    }
                    announce(Component.translatable(seconds == 20 ? "launch.orbitalis.poll_go" : "launch.orbitalis.arms_retract", seconds)
                            .withStyle(ChatFormatting.AQUA));
                } else if (seconds == 3) {
                    announce(Component.translatable("launch.orbitalis.ignition").withStyle(ChatFormatting.YELLOW));
                } else if (seconds > 0 && seconds <= 5) {
                    announce(Component.literal(Integer.toString(seconds)).withStyle(ChatFormatting.YELLOW));
                }
            }
            // Chaque étape à son heure : le bras à T-15 s, le déluge à T-6 s.
            if (timer == GANGWAY_RETRACT_TICKS) {
                retracted = true;
                updateGangways(level, isStructureManaged());
                sync();
            } else if (timer == 6 * 20) {
                delugeUntil = level.getGameTime() + DELUGE_TICKS;
                sync();
            }
            if (timer <= 0) {
                release(level);
            }
            return;
        }
        // Séquence manuelle : à pleine poussée, les attaches retiennent la fusée, mais pas indéfiniment.
        if (timer <= 0) {
            if (holdTicks == 0) {
                announce(Component.translatable("sequence.orbitalis.full_thrust").withStyle(ChatFormatting.YELLOW));
                sync();
            }
            if (++holdTicks >= HOLD_TICKS) {
                burnOnPad(0.3);
                endCountdown(Result.SCRUBBED, Component.translatable("sequence.orbitalis.cutoff").withStyle(ChatFormatting.GOLD));
            }
        }
    }

    /**
     * Lâche les attaches. C'est ici que se paie tout ce que la séquence a oublié :
     * <ul>
     * <li>lâchée avant la pleine poussée, la fusée n'a pas de quoi s'arracher au sol : elle retombe sur sa table ;</li>
     * <li>le bras et la passerelle encore en place, elle les arrache en partant, et s'y déchire ;</li>
     * <li>sans déluge, sur un pas de tir qui en a un, les flammes brisent des dalles du tablier ;</li>
     * <li>le plein incomplet, les moteurs s'éteindront en vol.</li>
     * </ul>
     */
    private void release(ServerLevel level) {
        if (timer > 0) {
            // Elle retombe sur ses moteurs : c'est par le bas qu'elle cède.
            padFailure(level, "sequence.orbitalis.fail.early", 0);
            return;
        }
        if (!retracted && !isTestPad()) {
            // Le bras est emporté avec la fusée, qui se déchire à sa hauteur.
            int torn = 0;
            for (BlockPos arm : complex.armPositions()) {
                torn = arm.getY() - worldPosition.getY() - 1;
                level.destroyBlock(arm, false);
            }
            padFailure(level, "sequence.orbitalis.fail.arm", torn);
            return;
        }
        boolean scorched = hasDeluge() && (!isDelugeOn() || delugeTest);
        RocketFuel required = requiredFuel();
        long needed = (long) required.rp1() + required.oxygen() + required.hydrogen() + required.methane() + required.helium3();
        long loaded = (long) Math.min(loadedRp1, required.rp1()) + Math.min(loadedOxygen, required.oxygen())
                + Math.min(loadedHydrogen, required.hydrogen()) + Math.min(loadedMethane, required.methane())
                + Math.min(loadedHelium3, required.helium3());
        // Les moteurs brûlent leurs ergols dans l'ordre du vol : la part chargée est la part du vol accomplie.
        flameoutAt = needed > 0 && loaded < needed ? Math.max(40, (int) (flightTicks() * 0.8 * loaded / needed)) : 0;
        // Si la fusée est perdue en vol, il ne lui restera presque plus d'ergols : c'est surtout sa masse qui s'écrase.
        flightBlast = RocketBreakup.blastRadius(rocketStats().liftoffMass(), 0.1, required.isEmpty());
        flightCryogenic = required.hydrogen() > 0;
        liftoff(level);
        if (scorched && phase == Phase.FLIGHT) {
            scorchDeck(level);
        }
    }

    /** Sans déluge, les flammes brisent les dalles les plus proches de la table : deux par taille de tablier. */
    private void scorchDeck(ServerLevel level) {
        if (plan == null) {
            return;
        }
        List<PadPlan.Piece> slabs = new ArrayList<>();
        for (PadPlan.Piece piece : plan.pieces()) {
            if (piece.role() == PadPlan.Role.DECK) {
                slabs.add(piece);
            }
        }
        slabs.sort(java.util.Comparator.comparingInt(piece -> piece.offset().distManhattan(BlockPos.ZERO)));
        int broken = 0;
        for (PadPlan.Piece piece : slabs) {
            BlockPos pos = worldPosition.offset(piece.offset());
            if (broken < 2 * plan.radius() && level.getBlockState(pos).is(ModBlocks.LAUNCH_PAD.get())) {
                level.destroyBlock(pos, false);
                broken++;
            }
        }
        announce(Component.translatable("sequence.orbitalis.scorched", broken).withStyle(ChatFormatting.GOLD));
    }

    /** Part des ergols à bord, de 0 à 1 ; une fusée à poudre est toujours pleine. */
    private double fuelFraction() {
        RocketFuel required = requiredFuel();
        long needed = (long) required.rp1() + required.oxygen() + required.hydrogen() + required.methane() + required.helium3();
        long loaded = (long) Math.min(loadedRp1, required.rp1()) + Math.min(loadedOxygen, required.oxygen())
                + Math.min(loadedHydrogen, required.hydrogen()) + Math.min(loadedMethane, required.methane())
                + Math.min(loadedHelium3, required.helium3());
        return needed <= 0 ? 1.0 : (double) loaded / needed;
    }

    /**
     * Accident sur le pas de tir : la fusée se disloque là où elle se dresse ({@link RocketBreakup}), à partir du
     * tronçon qui a cédé. L'étendue des dégâts dépend de sa masse et des ergols qu'elle porte.
     *
     * @param origin rang du tronçon qui cède le premier
     */
    private void padFailure(ServerLevel level, String messageKey, int origin) {
        RocketShape wreck = rocketShape();
        boolean solid = requiredFuel().isEmpty();
        double blast = RocketBreakup.blastRadius(rocketStats().liftoffMass(), fuelFraction(), solid);
        boolean cryogenic = requiredFuel().hydrogen() > 0;
        CapsuleEntity capsule = capsule(true);
        removeRocketBlocks(level);
        rocketPlan = ItemStack.EMPTY;
        if (capsule != null) {
            capsule.abort();
        }
        breakup = RocketBreakup.onPad(this, wreck, origin, blast, cryogenic);
        rocket = ItemStack.EMPTY;
        loadedRp1 = loadedOxygen = loadedHydrogen = loadedMethane = loadedHelium3 = 0;
        endCountdown(Result.FAILURE, Component.translatable(messageKey).withStyle(ChatFormatting.RED));
    }

    private void liftoff(ServerLevel level) {
        double x = worldPosition.getX() + 0.5;
        double y = worldPosition.getY() + 1.5;
        double z = worldPosition.getZ() + 0.5;
        // La capsule existe dès le décollage, même sans équipage : un vol d'essai la ramène au sol comme les autres.
        CapsuleEntity capsule = capsule(true);
        removeRocketBlocks(level);
        // La fusée s'en va : son plan a servi, il quitte le pas de tir. Sans cela, il se redessinerait au-dessus de
        // la table sitôt la fusée partie.
        rocketPlan = ItemStack.EMPTY;
        // Le déluge accompagne les premières secondes de la montée, puis se referme.
        if (isDelugeOn()) {
            delugeUntil = level.getGameTime() + DELUGE_AFTER_LIFTOFF;
        }
        // Le lâcher des attaches : les bras de retenue claquent, et le grondement des moteurs fait le reste.
        level.playSound(null, x, y, z, ModSounds.PAD_VALVE.get(), SoundSource.BLOCKS, 4.0F, 0.55F);
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.2F, 0.5F);
        phase = Phase.FLIGHT;
        timer = 0;
        phaseStart = level.getGameTime();
        stageCuts = new int[0];
        flightDone = flightMissed = stagesPassed = reachPenalty = manualSteps = 0;
        payloadLost = false;
        attitude.reset(trenchDirection);
        motion.reset();
        holdChunk(true);
        collided = false;
        tumbled = false;
        steerLoss = 0.0;
        aeroStress = 0;
        throttle = 1.0F;
        throttleInput = 0;
        throttleProgram = false;
        qteAt = null;
        qteIndex = -1;
        qteState = QTE_NONE;
        qteDrift = 0.0;
        if (capsule != null) {
            capsule.launch(rocketStats().reach());
            // La capsule emporte le plan de sa fusée : c'est elle qui la dessine pendant tout le vol, où qu'elle aille.
            capsule.setRocket(rocket.copy());
            // En manuel et en assisté, c'est au pilote d'ouvrir les parachutes au retour.
            capsule.setManualChute(assist < 2 && !autoSequence);
        }
        flightCapsule = capsule;
        sync();
        announce(Component.translatable("launch.orbitalis.liftoff").withStyle(ChatFormatting.GREEN));
    }

    private void tickFlight(ServerLevel level) {
        timer++;
        // Plein incomplet : les moteurs s'éteignent avant l'heure, et la fusée retombe.
        if (flameoutAt > 0 && timer >= flameoutAt) {
            loseInFlight(level, "sequence.orbitalis.fail.flameout");
            return;
        }
        tickQte(level);
        if (tickFlightSteps(level) || tickGuidance(level)) {
            return;
        }
        // Les annonces suivent le vol calculé : chaque événement est annoncé au moment où il se produit.
        FlightSimulation flight = FlightProfile.flight(rocketStats());
        double before = FlightProfile.simSeconds(rocketStats(), (timer - 1) / (double) flightTicks());
        double now = FlightProfile.simSeconds(rocketStats(), timer / (double) flightTicks());
        for (FlightSimulation.Event event : flight.events()) {
            if (event.time() > before && event.time() <= now) {
                Component message = switch (event.type()) {
                    case SPACE -> Component.translatable("launch.orbitalis.event.space");
                    // La séparation s'annonce d'elle-même, au moment où elle se fait vraiment.
                    case STAGING -> null;
                    case BURNOUT -> Component.translatable("launch.orbitalis.burnout");
                    case ORBIT -> Component.translatable("launch.orbitalis.event.orbit", (int) (flight.maxAltitude() / 1000.0),
                            (int) flight.propellantLeft());
                    case APOGEE -> Component.translatable("launch.orbitalis.event.apogee", (int) (flight.maxAltitude() / 1000.0));
                };
                if (message != null) {
                    announce(message.copy().withStyle(ChatFormatting.AQUA));
                }
            }
        }
        if (timer >= flightTicks()) {
            // Ce que la fusée a vraiment atteint : les étapes manquées en vol lui ont coûté des paliers.
            RocketStats.Reach reached = achievedReach();
            if (reached != rocketStats().reach() && steerLoss > deltaVMargin()) {
                announce(Component.translatable("sequence.orbitalis.flight.steer_loss", (int) steerLoss).withStyle(ChatFormatting.GOLD));
            }
            // Charge utile restée sous sa coiffe : elle est perdue, quoi qu'elle fût.
            ItemStack payload = payloadLost ? ItemStack.EMPTY : RocketItem.design(rocket).payload();
            CapsuleEntity crew = crewCapsule();
            if (crew != null && reached != rocketStats().reach()) {
                crew.setReach(reached);
            }
            // Une étape manquée jusqu'au bout (coiffe jamais larguée, moteurs jamais coupés) : la mission échoue.
            // Rien n'est gagné, rien n'est mis en orbite ; une capsule, elle, revient de là où elle est arrivée.
            if ((flightMissed & (FLIGHT_FAIRING | FLIGHT_CUTOFF)) != 0) {
                lastPoints = 0;
                lastResult = Result.FAILURE;
                holdChunk(false);
                phase = Phase.IDLE;
                timer = 0;
                rocket = ItemStack.EMPTY;
                loadedRp1 = loadedOxygen = loadedHydrogen = loadedMethane = loadedHelium3 = 0;
                resetSequence();
                flameoutAt = 0;
                sync();
                announce(Component.translatable("launch.orbitalis.mission_failed").withStyle(ChatFormatting.RED));
                return;
            }
            // Les instruments d'une fusée-sonde rapportent des données à chaque vol dans l'espace.
            boolean science = payload.is(ModItems.SOUNDING_PAYLOAD.get());
            lastPoints = ResearchManager.awardFlight(level.getServer(), agencyKey, reached, science);
            // Un satellite est mis en orbite si le lanceur a atteint son orbite de travail ; sinon il est perdu.
            SatelliteType satellite = SatelliteType.of(payload);
            SatelliteManager.Deployment deployment = satellite == null ? null
                    : SatelliteManager.onFlight(level.getServer(), agencyKey, satellite, reached);
            if (deployment != null) {
                lastPoints += deployment.points();
            }
            // Une sonde se met en orbite autour de la Lune ou de Mars ; un atterrisseur robotique se pose à sa surface.
            com.orbitalis.phase3.exploration.ProbeMissions.Outcome mission = com.orbitalis.phase3.exploration.ProbeMissions.onFlight(
                    level.getServer(), agencyKey, payload, reached, worldPosition);
            if (mission != null) {
                lastPoints += mission.points();
            }
            // Prime de pilotage : chaque étape du vol faite à la main, plutôt que par l'ordinateur de bord, rapporte.
            int bonus = PILOT_BONUS * manualSteps;
            if (bonus > 0 && level.getServer() != null) {
                ResearchManager.forAgency(level.getServer(), agencyKey).addPoints(bonus);
                lastPoints += bonus;
                announce(Component.translatable("sequence.orbitalis.assist.bonus", manualSteps, bonus).withStyle(ChatFormatting.AQUA));
            }
            lastResult = Result.SUCCESS;
            holdChunk(false);
            phase = Phase.IDLE;
            timer = 0;
            rocket = ItemStack.EMPTY;
            loadedRp1 = loadedOxygen = loadedHydrogen = loadedMethane = loadedHelium3 = 0;
            resetSequence();
            flameoutAt = 0;
            sync();
            announce(Component.translatable("launch.orbitalis.success", Component.translatable(reached.nameKey()), lastPoints)
                    .withStyle(ChatFormatting.GREEN));
            if (deployment != null) {
                deployment.messages().forEach(this::announce);
            }
            if (mission != null) {
                mission.messages().forEach(this::announce);
            }
            // Un module central de station se déploie en orbite, à la verticale du pas de tir ; un kit de module est
            // livré à la station de l'agence.
            if (payload.getItem() instanceof com.orbitalis.phase2.station.StationModuleItem) {
                StationManager.onFlight(level.getServer(), agencyKey, reached, worldPosition, payload).forEach(this::announce);
            }
        }
    }

    /** Avancement du vol simulé, de 0 à 1. */
    private double flightProgress() {
        return Math.min(1.0, timer / (double) flightTicks());
    }

    /** Altitude de la fusée pendant le vol simulé, en km. */
    public double flightAltitudeKm() {
        return phase == Phase.FLIGHT ? FlightProfile.altitudeKm(rocketStats(), flightProgress()) : 0;
    }

    /** Accélération ressentie à bord pendant le vol, en g. */
    public double flightGForce() {
        return phase == Phase.FLIGHT ? FlightProfile.gForce(rocketStats(), flightProgress()) : 0;
    }

    /** Vitesse de la fusée pendant le vol simulé, en m/s. */
    public double flightSpeed() {
        return phase == Phase.FLIGHT ? FlightProfile.speed(rocketStats(), flightProgress()) : 0;
    }

    // ------------------------------------------------------------------
    // Relevés lointains, pour la carte de navigation
    // ------------------------------------------------------------------

    /** Côté d'un relevé lointain, en cubes, et taille d'un cube en blocs : la région, puis le pays. */
    public static final int FAR_SIZE = 256;
    public static final int[] FAR_STEP = {6, 128};
    /** Couleurs des relevés lointains : celles que la carte donne aux mêmes terrains, vus de près. */
    public static final int FAR_GRASS = 0x6D9930;
    public static final int FAR_FOREST = 0x2F7A22;
    public static final int FAR_SAVANNA = 0x93A04C;
    public static final int FAR_SAND = 0xD5C98C;
    public static final int FAR_STONE = 0x606060;
    public static final int FAR_SNOW = 0xDCDCDC;
    public static final int FAR_BADLANDS = 0xBA6D2C;
    public static final int FAR_SHALLOW = 0x569FE2;
    public static final int FAR_SEA = 0x4389D4;
    public static final int FAR_DEEP = 0x3477C4;
    private final int[][] farMaps = new int[2][];
    /** Relevé en cours de calcul, mené à part pour ne pas ralentir le serveur, et son rang (0 : la région). */
    private java.util.concurrent.CompletableFuture<int[]> farJob;
    private int farLod;

    /** Vrai si cette couleur d'un relevé lointain est de l'eau. */
    public static boolean isFarWater(int rgb) {
        return rgb == FAR_SHALLOW || rgb == FAR_SEA || rgb == FAR_DEEP;
    }

    /**
     * Fait calculer les relevés lointains (la région, puis le pays), et envoie chacun aux joueurs des environs dès
     * qu'il est prêt. Le terrain n'est pas chargé : sa hauteur et son biome sont demandés au générateur du monde,
     * colonne par colonne, comme il les donnerait en le créant. Soixante mille colonnes par relevé : le calcul se
     * fait hors du fil du serveur, comme la création du monde elle-même.
     */
    private void tickFarSurvey(ServerLevel level) {
        if (!commissioned || farLod >= FAR_STEP.length) {
            return;
        }
        if (farJob == null) {
            var generator = level.getChunkSource().getGenerator();
            var random = level.getChunkSource().randomState();
            int sea = level.getSeaLevel();
            int step = FAR_STEP[farLod];
            BlockPos centre = worldPosition.immutable();
            farJob = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                long started = System.nanoTime();
                int[] cells = new int[FAR_SIZE * FAR_SIZE];
                for (int index = 0; index < cells.length; index++) {
                    int x = centre.getX() + (index % FAR_SIZE - FAR_SIZE / 2) * step;
                    int z = centre.getZ() + (index / FAR_SIZE - FAR_SIZE / 2) * step;
                    cells[index] = farCell(generator, random, level, sea, x, z);
                }
                com.orbitalis.Orbitalis.LOGGER.debug("Relevé lointain (pas de {} blocs) : {} ms", step, (System.nanoTime() - started) / 1_000_000);
                return cells;
            });
            return;
        }
        if (!farJob.isDone()) {
            return;
        }
        int[] cells = farJob.isCompletedExceptionally() ? null : farJob.getNow(null);
        farJob = null;
        if (cells == null) {
            // Le générateur de ce monde ne s'y prête pas : pas de relevé lointain, la carte s'en passera.
            farLod = FAR_STEP.length;
            return;
        }
        farMaps[farLod] = cells;
        com.orbitalis.core.network.ModNetwork.sendNear(level, 320.0, new com.orbitalis.core.network.SurveyPayload(worldPosition, farLod, cells),
                net.minecraft.world.phys.Vec3.atCenterOf(worldPosition));
        farLod++;
    }

    /** Un cube d'un relevé lointain : ce que le générateur du monde met à cet endroit. */
    public static int farCell(net.minecraft.world.level.chunk.ChunkGenerator generator, net.minecraft.world.level.levelgen.RandomState random,
            net.minecraft.world.level.LevelHeightAccessor heights, int sea, int x, int z) {
        // Un monde fait de bruit donne son niveau de surface d'un seul calcul, à quelques blocs près : c'est cent fois
        // plus rapide que de lui faire bâtir la colonne entière, et bien assez juste pour une vue de loin.
        int floor = generator instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator
                ? net.minecraft.util.Mth.floor(random.router().preliminarySurfaceLevel()
                        .compute(new net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext(x, 0, z))) - 1
                : generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, heights, random) - 1;
        int rgb;
        int top = floor;
        if (floor < sea - 1) {
            // Sous le niveau de la mer : de l'eau, d'autant plus sombre qu'elle est profonde.
            int depth = sea - 1 - floor;
            rgb = depth > 14 ? FAR_DEEP : depth > 4 ? FAR_SEA : FAR_SHALLOW;
            top = sea - 1;
        } else {
            var biome = generator.getBiomeSource().getNoiseBiome(x >> 2, floor >> 2, z >> 2, random.sampler());
            int above = floor - sea;
            if (biome.is(net.minecraft.tags.BiomeTags.IS_BADLANDS)) {
                rgb = FAR_BADLANDS;
            } else if (biome.is(net.minecraft.tags.BiomeTags.IS_BEACH) || biome.is(net.minecraft.tags.BiomeTags.HAS_DESERT_PYRAMID)) {
                rgb = FAR_SAND;
            } else if (biome.value().getBaseTemperature() < 0.15F || above > 100) {
                rgb = FAR_SNOW;
            } else if (above > 66 || biome.is(net.minecraft.tags.BiomeTags.IS_MOUNTAIN) && above > 46) {
                rgb = FAR_STONE;
            } else if (biome.is(net.minecraft.tags.BiomeTags.IS_FOREST) || biome.is(net.minecraft.tags.BiomeTags.IS_JUNGLE)
                    || biome.is(net.minecraft.tags.BiomeTags.IS_TAIGA)) {
                // Une forêt : la couleur du feuillage ; la carte y plantera ses arbres.
                rgb = FAR_FOREST;
            } else if (biome.is(net.minecraft.tags.BiomeTags.IS_SAVANNA)) {
                rgb = FAR_SAVANNA;
            } else {
                rgb = FAR_GRASS;
            }
        }
        // La hauteur est comptée depuis le niveau de la mer : elle ne dépend pas de l'endroit où la table est posée.
        return Math.clamp(top - sea + MAP_HEIGHT_BIAS, 1, 255) << 24 | rgb;
    }

    /** Envoie à ce joueur les relevés lointains déjà prêts : sa carte de navigation les demande. */
    public void sendSurveys(ServerPlayer player) {
        for (int lod = 0; lod < farMaps.length; lod++) {
            if (farMaps[lod] != null && farMaps[lod].length > 0) {
                com.orbitalis.core.network.ModNetwork.send(player, new com.orbitalis.core.network.SurveyPayload(worldPosition, lod, farMaps[lod]));
            }
        }
    }

    /** Message envoyé aux joueurs proches du pas de tir. */
    private void announce(Component message) {
        if (level instanceof ServerLevel serverLevel) {
            for (ServerPlayer player : serverLevel.players()) {
                if (player.blockPosition().closerThan(worldPosition, ANNOUNCE_RANGE)) {
                    player.sendSystemMessage(message);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Sauvegarde
    // ------------------------------------------------------------------

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) {
            track(level, worldPosition, false);
        }
    }

    /** Quand la table est cassée, la fusée tombe au sol. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !level.isClientSide()) {
            clearLights(level);
        }
        if (level != null && hasRocket()) {
            removeRocketBlocks(level);
            // Une fusée bâtie d'après un plan n'existe pas en objet : seul son plan tombe.
            if (rocketPlan.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), rocket);
            }
            rocket = ItemStack.EMPTY;
        }
        if (level != null && !rocketPlan.isEmpty()) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), rocketPlan);
            rocketPlan = ItemStack.EMPTY;
        }
        // Les dalles retrouvent leur aspect ordinaire, la tour et les bras redeviennent visibles.
        if (level != null) {
            updatePadLooks(level, 0);
            updateManaged(level, false);
            updateGangways(level, false);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("commissioned", commissioned);
        output.putBoolean("tower_authorized", towerAuthorized);
        output.store("rocket", ItemStack.OPTIONAL_CODEC, rocket);
        output.store("rocket_plan", ItemStack.OPTIONAL_CODEC, rocketPlan);
        output.store("free_cargo", ItemStack.OPTIONAL_CODEC, freeCargo);
        output.putBoolean("free_plan", freePlan);
        output.putInt("rp1", loadedRp1);
        output.putBoolean("seq_valves", valvesOpen);
        output.putLong("seq_pressure", pressureUntil);
        output.putLong("seq_chill", chillUntil);
        output.putBoolean("seq_internal", internalPower);
        output.putBoolean("seq_retracted", retracted);
        output.putLong("seq_deluge", delugeUntil);
        output.putInt("seq_water", water);
        output.putBoolean("seq_auto", autoSequence);
        output.putInt("seq_hold", holdTicks);
        output.putInt("seq_flameout", flameoutAt);
        output.putInt("assist", assist);
        double[] turned = attitude.save();
        for (int k = 0; k < turned.length; k++) {
            output.putDouble("flight_attitude_" + k, turned[k]);
        }
        double[] moved = motion.save();
        for (int k = 0; k < moved.length; k++) {
            output.putDouble("flight_motion_" + k, moved[k]);
        }
        output.putBoolean("flight_chunk_held", chunkHeld);
        output.putDouble("flight_steer_loss", steerLoss);
        output.putFloat("flight_throttle", throttle);
        output.putBoolean("flight_throttle_program", throttleProgram);
        output.putInt("flight_stress", aeroStress);
        output.putInt("flight_manual", manualSteps);
        output.putInt("flight_done", flightDone);
        output.putInt("flight_missed", flightMissed);
        output.putInt("flight_stages", stagesPassed);
        output.putIntArray("flight_cuts", stageCuts);
        output.putInt("flight_penalty", reachPenalty);
        output.putBoolean("flight_payload_lost", payloadLost);
        output.putInt("oxygen", loadedOxygen);
        output.putInt("hydrogen", loadedHydrogen);
        output.putInt("methane", loadedMethane);
        output.putInt("helium3", loadedHelium3);
        output.putInt("phase", phase.ordinal());
        output.putInt("timer", timer);
        output.putString("agency", agencyKey);
        output.putInt("last_result", lastResult.ordinal());
        output.putInt("last_points", lastPoints);
        output.putLong("phase_start", phaseStart);
        // Relevé de la structure : les clients s'en servent pour dessiner la tour, les bras et les écrans.
        if (plan != null) {
            output.putInt("plan_radius", plan.radius());
            output.putBoolean("plan_elevator", plan.elevator());
            output.putInt("plan_facing", plan.facing().get2DDataValue());
            output.putInt("plan_gate", plan.gate());
            output.putInt("plan_arm", plan.arm());
            output.putBoolean("plan_compact", plan.compact());
        }
        output.putInt("pad_missing", complex.missing());
        output.putInt("pad_radius", complex.radius());
        output.putBoolean("pad_sky", complex.skyClear());
        output.putBoolean("pad_deflector", complex.hasDeflector());
        output.putBoolean("pad_trench", complex.hasTrench());
        output.putInt("pad_masts", complex.mastHeight());
        output.putInt("tower_height", complex.towerHeight());
        if (complex.towerBase() != null) {
            output.putLong("tower_base", complex.towerBase().asLong());
        }
        long[] arms = complex.armPositions().stream().mapToLong(BlockPos::asLong).toArray();
        int[] packed = new int[arms.length * 2];
        for (int i = 0; i < arms.length; i++) {
            packed[2 * i] = (int) (arms[i] >> 32);
            packed[2 * i + 1] = (int) arms[i];
        }
        output.putIntArray("tower_arms", packed);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        commissioned = input.getBooleanOr("commissioned", false);
        towerAuthorized = input.getBooleanOr("tower_authorized", false);
        rocket = input.read("rocket", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        rocketPlan = input.read("rocket_plan", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        freeCargo = input.read("free_cargo", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        freePlan = input.getBooleanOr("free_plan", false);
        loadedRp1 = input.getIntOr("rp1", 0);
        valvesOpen = input.getBooleanOr("seq_valves", false);
        pressureUntil = input.getLongOr("seq_pressure", 0L);
        chillUntil = input.getLongOr("seq_chill", 0L);
        internalPower = input.getBooleanOr("seq_internal", false);
        retracted = input.getBooleanOr("seq_retracted", false);
        delugeUntil = input.getLongOr("seq_deluge", 0L);
        water = input.getIntOr("seq_water", 0);
        autoSequence = input.getBooleanOr("seq_auto", false);
        holdTicks = input.getIntOr("seq_hold", 0);
        flameoutAt = input.getIntOr("seq_flameout", 0);
        assist = Math.clamp(input.getIntOr("assist", 0), 0, 2);
        double[] turned = new double[7];
        for (int k = 0; k < turned.length; k++) {
            turned[k] = input.getDoubleOr("flight_attitude_" + k, k == 3 ? 1.0 : 0.0);
        }
        attitude.reset(trenchDirection);
        attitude.load(turned);
        double[] moved = new double[6];
        for (int k = 0; k < moved.length; k++) {
            moved[k] = input.getDoubleOr("flight_motion_" + k, 0.0);
        }
        motion.load(moved);
        chunkHeld = input.getBooleanOr("flight_chunk_held", false);
        steerLoss = input.getDoubleOr("flight_steer_loss", 0.0);
        throttle = input.getFloatOr("flight_throttle", 1.0F);
        throttleProgram = input.getBooleanOr("flight_throttle_program", false);
        aeroStress = input.getIntOr("flight_stress", 0);
        manualSteps = input.getIntOr("flight_manual", 0);
        flightDone = input.getIntOr("flight_done", 0);
        flightMissed = input.getIntOr("flight_missed", 0);
        stagesPassed = input.getIntOr("flight_stages", 0);
        int[] cuts = input.getIntArray("flight_cuts").orElse(new int[0]);
        if (!java.util.Arrays.equals(cuts, stageCuts)) {
            stageCuts = cuts;
            // Côté client : la trajectoire d'une fusée sans équipage se refait depuis le décollage, coupures comprises.
            clientMotionTicks = Integer.MAX_VALUE;
        }
        reachPenalty = input.getIntOr("flight_penalty", 0);
        payloadLost = input.getBooleanOr("flight_payload_lost", false);
        loadedOxygen = input.getIntOr("oxygen", 0);
        loadedHydrogen = input.getIntOr("hydrogen", 0);
        loadedMethane = input.getIntOr("methane", 0);
        loadedHelium3 = input.getIntOr("helium3", 0);
        phase = Phase.values()[Math.floorMod(input.getIntOr("phase", 0), Phase.values().length)];
        timer = input.getIntOr("timer", 0);
        agencyKey = input.getStringOr("agency", "");
        lastResult = Result.values()[Math.floorMod(input.getIntOr("last_result", 0), Result.values().length)];
        lastPoints = input.getIntOr("last_points", 0);
        phaseStart = input.getLongOr("phase_start", 0L);
        int[] packed = input.getIntArray("tower_arms").orElse(new int[0]);
        List<BlockPos> arms = new ArrayList<>();
        for (int i = 0; i + 1 < packed.length; i += 2) {
            arms.add(BlockPos.of(((long) packed[i] << 32) | (packed[i + 1] & 0xFFFFFFFFL)));
        }
        long towerBase = input.getLongOr("tower_base", Long.MIN_VALUE);
        complex = new LaunchComplex(input.getIntOr("pad_radius", 0), 0, input.getBooleanOr("pad_sky", false),
                input.getIntOr("tower_height", 0), arms.size(), input.getBooleanOr("pad_deflector", false),
                input.getBooleanOr("pad_trench", false), input.getIntOr("pad_masts", 0),
                towerBase == Long.MIN_VALUE ? null : BlockPos.of(towerBase), List.copyOf(arms), input.getIntOr("pad_missing", 0),
                input.getBooleanOr("plan_compact", false));
        int planRadius = input.getIntOr("plan_radius", 0);
        plan = planRadius <= 0 ? null : new PadPlan(planRadius, input.getBooleanOr("plan_elevator", false),
                Direction.from2DDataValue(input.getIntOr("plan_facing", Direction.EAST.get2DDataValue())), input.getIntOr("plan_gate", 10),
                input.getIntOr("plan_arm", 4), input.getBooleanOr("plan_compact", false));
    }

    /** Vrai si les échecs de lancement sont activés dans la configuration du serveur. */
    public static boolean failuresEnabled() {
        return ServerConfig.FAILURES_ENABLED.get();
    }
}
