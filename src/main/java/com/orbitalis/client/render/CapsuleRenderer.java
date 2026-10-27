package com.orbitalis.client.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.orbitalis.phase1.rocket.RocketStats;
import com.orbitalis.phase1.spaceport.FlightProfile;
import com.orbitalis.phase1.spaceport.LaunchTableBlockEntity;
import com.orbitalis.phase1.spaceport.StructurePartBlock;
import com.orbitalis.phase2.flight.CapsuleEntity;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Rendu de la capsule habitée.
 * <p>
 * Une fois séparée de sa fusée, il dessine la cabine et, pendant la descente, sa voile de parachutes ; sur la
 * fusée, c'est le tronçon « capsule » de la fusée qui montre la cabine.
 * <p>
 * Dans les deux cas, il dessine l'image de l'écran de bord, par-dessus l'écran de la planche de bord : l'horloge
 * de mission, l'altitude et la vitesse avec leurs jauges, la trajectoire, l'état des systèmes et le déroulé de
 * la mission, tenus à jour en direct, comme sur les écrans du centre de contrôle.
 * <p>
 * Cette image n'est recomposée que dix fois par seconde, pas à chaque image du jeu : entre deux, la même
 * est redessinée telle quelle.
 */
public class CapsuleRenderer extends EntityRenderer<CapsuleEntity, CapsuleRenderer.State> {
    // Grand écran de bord, dans le modèle de la capsule (en pixels) : il fait 14 sur 8, dressé sur la cloison avant,
    // sous le pare-brise, face au pilote assis (voir tools/generate_capsule.py). Son bord gauche, son bord haut, et
    // le plan de sa dalle.
    private static final float SCREEN_LEFT = 1.0F;
    private static final float SCREEN_TOP = 14.5F;
    private static final float SCREEN_PLANE = -3.95F;
    /** Taille de l'image de l'écran, en points : dix points par pixel du modèle. */
    private static final float WIDTH = 140.0F;
    private static final float HEIGHT = 80.0F;
    private static final float POINT = 1.0F / 160.0F;
    /** Hauteur à laquelle commence le bandeau de l'image : l'écran se voit en entier, il commence tout en haut. */
    private static final float HEADER_TOP = 1.0F;
    /** Intervalle entre deux recompositions de l'image de l'écran, en ticks. */
    private static final long REFRESH_TICKS = 2;

    private static final int COLOR_BACKGROUND = 0xFF05080C;
    private static final int COLOR_PANEL = 0xFF0C141D;
    private static final int COLOR_TRACK = 0xFF16222E;
    private static final int COLOR_EDGE = 0xFF1D5F78;
    private static final int COLOR_ACCENT = 0xFF35C3F0;
    private static final int COLOR_TEXT = 0xFFEAF6FF;
    private static final int COLOR_MUTED = 0xFF7F96A8;
    private static final int COLOR_OK = 0xFF4FD67A;
    private static final int COLOR_BAD = 0xFFE0655F;
    private static final int COLOR_WARNING = 0xFFFFB23E;

    private record Rect(float x0, float y0, float x1, float y1, int color, int layer) {
    }

    private record Label(float x, float y, FormattedCharSequence text, int color, float scale) {
    }

    /** Image de l'écran de bord d'une capsule, et l'instant où elle a été composée. */
    private static final class Screen {
        final List<Rect> rects = new ArrayList<>();
        final List<Label> labels = new ArrayList<>();
        /** Jamais composée au départ : une valeur lointaine, mais dont la différence avec l'heure du jeu ne déborde pas. */
        long composedAt = -1_000_000L;
        // Ce que montrent les instruments, relevé à chaque composition.
        double altitudeKm;
        double targetKm = 1.0;
        double speed;
        double gForce = 1.0;
        int mode;
        // Le manche : son inclinaison, qui rejoint en douceur celle des commandes.
        float stickPitch;
        float stickRoll;
        float stickYaw;
        long stickTime = System.nanoTime();
    }

    /** Moments du vol, pour l'ambiance de la cabine et ses voyants. */
    private static final int MODE_GROUND = 0;
    private static final int MODE_COUNTDOWN = 1;
    private static final int MODE_ASCENT = 2;
    private static final int MODE_ORBIT = 3;
    private static final int MODE_DESCENT = 4;
    private static final int MODE_ABORT = 5;

    /** Ce qu'il faut pour dessiner la capsule à un instant donné. */
    public static class State extends EntityRenderState {
        boolean exposed;
        boolean parachutes;
        /** Gonflement de la voile, de 0 à 1. */
        float chuteInflation;
        boolean lunar;
        /** Orientation de la cabine : celle de sa fusée pendant la montée. */
        final org.joml.Quaternionf attitude = new org.joml.Quaternionf();
        /** Fusée que la capsule dessine pendant la montée : ses tronçons, son diamètre, le rang de la cabine. */
        List<com.orbitalis.phase1.spaceport.RocketBlock.Part> rocketParts = List.of();
        com.orbitalis.phase1.spaceport.RocketBlock.Size rocketSize = com.orbitalis.phase1.spaceport.RocketBlock.Size.SLIM;
        int cabin;
        /** Temps écoulé depuis le largage de chaque étage, en ticks ; -1 tant qu'il est attaché. */
        final float[] stageAges = new float[16];
        /** Boosters latéraux : direction du premier depuis l'axe ({@code null} sans boosters), silhouette, âge du largage. */
        net.minecraft.core.Direction boosterSide;
        com.orbitalis.phase1.spaceport.RocketShape boosterShape;
        float boosterAge;
        boolean coasting;
        /** Ce qui brûle sur la cabine seule : 0 rien, 1 le moteur lunaire en descente, 2 le même au décollage, 3 la tour de sauvetage. */
        int burning;
        /** Force du plasma de rentrée, de 0 à 1, d'après la vitesse de chute de la cabine. */
        float plasma;
        /** Les instruments : altitude, altitude visée, vitesse, accélération, moment du vol, et l'heure du jeu. */
        double altitudeKm;
        double targetKm = 1.0;
        double speed;
        double gForce = 1.0;
        int mode;
        float time;
        boolean chutes;
        /** Inclinaison du manche, en degrés : tangage, roulis, lacet. */
        float stickPitch;
        float stickRoll;
        float stickYaw;
        /** Lumière de la cabine : celle du dehors, relevée par les plafonniers. */
        int cabinLight;
        /** Modèle de la cabine : 0 la Mk1, 1 la Mk2, 2 la Mk3. */
        int variant;
        /** Image de l'écran de bord : des copies figées, que la recomposition suivante ne touche pas. */
        List<Rect> rects = List.of();
        List<Label> labels = List.of();
    }

    /** Image de l'écran de chaque capsule visible ; une capsule disparue libère la sienne d'elle-même. */
    private final Map<CapsuleEntity, Screen> screens = new WeakHashMap<>();
    /** Copies figées de la dernière image composée, partagées par toutes les images du jeu jusqu'à la suivante. */
    private final Map<Screen, List<Rect>> frozenRects = new WeakHashMap<>();
    private final Map<Screen, List<Label>> frozenLabels = new WeakHashMap<>();
    private final Font font;

    public CapsuleRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 1.1F;
        font = getFont();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CapsuleEntity capsule, State state, float partialTicks) {
        super.extractRenderState(capsule, state, partialTicks);
        state.exposed = capsule.isExposed();
        state.parachutes = capsule.hasOpenParachutes();
        state.chuteInflation = capsule.chuteInflation(partialTicks);
        state.lunar = capsule.isLunar() || capsule.isMartian() || capsule.isEuropan();
        state.attitude.set(capsule.attitude(partialTicks));
        CapsuleEntity.Phase burningPhase = capsule.getPhase();
        state.plasma = burningPhase == CapsuleEntity.Phase.REENTRY || burningPhase == CapsuleEntity.Phase.MARS_ENTRY ? Mth.clamp((float) (capsule.yo - capsule.getY() - 1.2) / 2.0F, 0.0F, 1.0F) : 0.0F;
        state.burning = burningPhase == CapsuleEntity.Phase.LUNAR_DESCENT ? 1 : burningPhase == CapsuleEntity.Phase.LUNAR_ASCENT ? 2
                : burningPhase == CapsuleEntity.Phase.POWERED_DESCENT ? (capsule.isBurning() ? 2 : 0)
                : burningPhase == CapsuleEntity.Phase.ABORT ? 3 : 0;
        com.orbitalis.phase1.spaceport.RocketShape shape = capsule.isFollowingRocket() ? capsule.flightShape() : null;
        state.rocketParts = shape == null ? List.of() : shape.parts();
        if (shape != null) {
            state.rocketSize = shape.size();
            state.cabin = Math.max(0, com.orbitalis.phase1.spaceport.RocketShape.cabinIndex(shape.parts()));
            for (int k = 0; k < state.stageAges.length; k++) {
                state.stageAges[k] = capsule.stageAge(k, partialTicks);
            }
            state.coasting = capsule.isCoasting();
        }
        // Les boosters : la table de lancement sait quand ils se larguent ; loin d'elle, ils sont déjà tombés.
        state.boosterSide = null;
        if (shape != null && shape.boosters() > 0 && capsule.getTable() != null
                && capsule.level().getBlockEntity(capsule.getTable()) instanceof com.orbitalis.phase1.spaceport.LaunchTableBlockEntity table) {
            state.boosterSide = table.getTrenchDirection().getClockWise();
            state.boosterShape = shape;
            state.boosterAge = table.boosterAge(partialTicks);
        }
        Screen screen = screens.computeIfAbsent(capsule, key -> new Screen());
        long time = capsule.level().getGameTime();
        if (time - screen.composedAt >= REFRESH_TICKS || time < screen.composedAt) {
            screen.composedAt = time;
            screen.rects.clear();
            screen.labels.clear();
            compose(capsule, screen);
            frozenRects.put(screen, List.copyOf(screen.rects));
            frozenLabels.put(screen, List.copyOf(screen.labels));
        }
        state.rects = frozenRects.getOrDefault(screen, List.of());
        state.labels = frozenLabels.getOrDefault(screen, List.of());
        state.altitudeKm = screen.altitudeKm;
        state.targetKm = screen.targetKm;
        state.speed = screen.speed;
        state.gForce = screen.gForce;
        state.mode = screen.mode;
        state.variant = capsule.getVariant();
        state.time = time + partialTicks;
        state.chutes = burningPhase == CapsuleEntity.Phase.CHUTE || burningPhase == CapsuleEntity.Phase.LANDED;
        state.cabinLight = LightCoordsUtil.pack(Math.max(CABIN_LIGHT, LightCoordsUtil.block(state.lightCoords)), LightCoordsUtil.sky(state.lightCoords));
        // Le manche suit les commandes du pilote de ce client ; pour les autres, il reste droit.
        net.minecraft.client.player.LocalPlayer local = net.minecraft.client.Minecraft.getInstance().player;
        int bits = local != null && capsule.getFirstPassenger() == local ? com.orbitalis.client.PilotControls.heldBits() : 0;
        long now = System.nanoTime();
        float step = 1.0F - (float) Math.exp(-Mth.clamp((now - screen.stickTime) / 1.0E9F, 0.0F, 0.2F) * 14.0F);
        screen.stickTime = now;
        screen.stickPitch += (axis(bits, com.orbitalis.core.network.PilotInputPayload.PITCH_DOWN, com.orbitalis.core.network.PilotInputPayload.PITCH_UP) * 14.0F
                - screen.stickPitch) * step;
        screen.stickRoll += (axis(bits, com.orbitalis.core.network.PilotInputPayload.ROLL_LEFT, com.orbitalis.core.network.PilotInputPayload.ROLL_RIGHT) * 14.0F
                - screen.stickRoll) * step;
        screen.stickYaw += (axis(bits, com.orbitalis.core.network.PilotInputPayload.YAW_LEFT, com.orbitalis.core.network.PilotInputPayload.YAW_RIGHT) * 12.0F
                - screen.stickYaw) * step;
        state.stickPitch = screen.stickPitch;
        state.stickRoll = screen.stickRoll;
        state.stickYaw = screen.stickYaw;
    }

    /** Lumière minimale de la cabine, plafonniers allumés. */
    private static final int CABIN_LIGHT = 13;

    private static float axis(int bits, int positive, int negative) {
        return ((bits & positive) != 0 ? 1.0F : 0.0F) - ((bits & negative) != 0 ? 1.0F : 0.0F);
    }

    // ------------------------------------------------------------------
    // Image de l'écran de bord
    // ------------------------------------------------------------------

    /** Compose l'image de l'écran : un bandeau, trois volets, et le déroulé de la mission en bas. */
    private void compose(CapsuleEntity capsule, Screen screen) {
        LaunchTableBlockEntity table = capsule.getTable() != null
                && capsule.level().getBlockEntity(capsule.getTable()) instanceof LaunchTableBlockEntity found ? found : null;
        CapsuleEntity.Phase phase = capsule.getPhase();
        boolean onRocket = table != null && table.hasRocket() && (phase == CapsuleEntity.Phase.PAD || phase == CapsuleEntity.Phase.ASCENT);
        LaunchTableBlockEntity.Phase launch = onRocket ? table.getPhase() : LaunchTableBlockEntity.Phase.IDLE;
        RocketStats stats = onRocket ? table.rocketStats() : null;

        // Grandeurs affichées, et étape de la mission (de 0 à 4) pour le déroulé du bas.
        String clock;
        int clockColor;
        String status;
        double altitudeKm = 0;
        double speed = 0;
        double targetKm = FlightProfile.ORBIT_ALTITUDE_KM;
        float progress = 0.0F;
        int step;
        if (onRocket && launch == LaunchTableBlockEntity.Phase.COUNTDOWN) {
            clock = "T-" + clock(Math.max(0.0F, (LaunchTableBlockEntity.COUNTDOWN_TICKS - table.phaseTicks()) / 20.0F));
            clockColor = COLOR_WARNING;
            status = translate("cockpit.orbitalis.countdown");
            targetKm = FlightProfile.targetAltitudeKm(stats);
            step = 1;
        } else if (onRocket && launch == LaunchTableBlockEntity.Phase.FLIGHT) {
            float ticks = table.phaseTicks();
            progress = Mth.clamp(ticks / table.flightTicks(), 0.0F, 1.0F);
            clock = "T+" + clock((float) FlightProfile.missionSeconds(stats, progress));
            clockColor = COLOR_OK;
            status = Component.translatable("cockpit.orbitalis.stage", FlightProfile.stage(stats, progress)).getString()
                    + " · " + String.format(Locale.ROOT, "%.1f g", FlightProfile.gForce(stats, progress));
            targetKm = FlightProfile.targetAltitudeKm(stats);
            altitudeKm = FlightProfile.altitudeKm(stats, progress);
            speed = FlightProfile.speed(stats, progress);
            step = 2;
        } else if (onRocket) {
            clock = "T-" + clock(LaunchTableBlockEntity.COUNTDOWN_TICKS / 20.0F);
            clockColor = COLOR_MUTED;
            status = translate("cockpit.orbitalis.hold");
            targetKm = FlightProfile.targetAltitudeKm(stats);
            step = 0;
        } else {
            // Capsule seule : en orbite, ou sur le chemin du retour.
            clock = translate(phase.messageKey());
            clockColor = phase == CapsuleEntity.Phase.ABORT ? COLOR_BAD : COLOR_ACCENT;
            status = "";
            progress = 1.0F;
            if (phase == CapsuleEntity.Phase.ORBIT || phase == CapsuleEntity.Phase.COAST) {
                altitudeKm = FlightProfile.ORBIT_ALTITUDE_KM;
                speed = FlightProfile.ORBIT_SPEED;
                step = 3;
            } else {
                // Descente : hauteur au-dessus du sol et vitesse de chute, un bloc valant un mètre.
                int ground = capsule.level().getHeight(Heightmap.Types.MOTION_BLOCKING, capsule.getBlockX(), capsule.getBlockZ());
                altitudeKm = Math.max(0.0, capsule.getY() - ground) / 1000.0;
                speed = Math.abs(capsule.getY() - capsule.yo) * 20.0;
                targetKm = 1.0;
                step = 4;
            }
        }

        // Ce que montrent les cadrans et le globe de la planche de bord.
        screen.altitudeKm = altitudeKm;
        screen.targetKm = targetKm;
        screen.speed = speed;
        screen.gForce = onRocket && launch == LaunchTableBlockEntity.Phase.FLIGHT ? FlightProfile.gForce(stats, progress)
                : phase == CapsuleEntity.Phase.ORBIT || phase == CapsuleEntity.Phase.COAST ? 0.0
                : phase == CapsuleEntity.Phase.REENTRY || phase == CapsuleEntity.Phase.MARS_ENTRY ? 3.2 : 1.0;
        screen.mode = phase == CapsuleEntity.Phase.ABORT ? MODE_ABORT
                : onRocket && launch == LaunchTableBlockEntity.Phase.COUNTDOWN ? MODE_COUNTDOWN
                : onRocket && launch == LaunchTableBlockEntity.Phase.FLIGHT ? MODE_ASCENT
                : onRocket ? MODE_GROUND
                : phase == CapsuleEntity.Phase.ORBIT || phase == CapsuleEntity.Phase.COAST ? MODE_ORBIT
                : phase == CapsuleEntity.Phase.LANDED ? MODE_GROUND : MODE_DESCENT;

        // Fond, bandeau, et sous le bandeau un filet de la couleur de la phase.
        rect(screen, 0, 0, WIDTH, HEIGHT, COLOR_BACKGROUND, 0);
        float head = HEADER_TOP;
        rect(screen, 0, head, WIDTH, head + 13, COLOR_PANEL, 1);
        rect(screen, 0, head + 13, WIDTH, head + 13.8F, clockColor, 1);
        // Bandeau : la phase en pastille à droite, l'horloge au centre, le nom à gauche. Chacun a sa place réservée :
        // l'horloge se décale vers la gauche si la pastille est large, et le nom s'efface s'il n'a plus la place.
        float clockWidth = font.width(clock) * 1.2F;
        float pillLeft = WIDTH;
        if (!status.isEmpty()) {
            // La phase, en pastille : un fond de couleur, le texte en sombre.
            float statusWidth = font.width(status) * 0.7F;
            pillLeft = WIDTH - statusWidth - 8;
            rect(screen, pillLeft, head + 2.5F, WIDTH - 3, head + 10.5F, clockColor, 2);
            label(screen, pillLeft + 2.5F, head + 3.7F, status, COLOR_BACKGROUND, 0.7F);
        }
        float clockX = Math.max(3, Math.min((WIDTH - clockWidth) / 2.0F, pillLeft - 4 - clockWidth));
        label(screen, clockX, head + 2, clock, clockColor, 1.2F);
        if (4 + font.width("ORBITALIS") * 0.8F + 3 <= clockX) {
            label(screen, 4, head + 3, "ORBITALIS", COLOR_ACCENT, 0.8F);
        }

        // Volet gauche : altitude et vitesse, chacune avec sa jauge.
        float top = head + 16;
        float bottom = 65;
        card(screen, 2, top, 44, bottom);
        label(screen, 5, top + 2, translate("cockpit.orbitalis.altitude"), COLOR_MUTED, 0.65F);
        label(screen, 5, top + 8, altitudeKm >= 1.0 ? grouped(altitudeKm) + " km" : grouped(altitudeKm * 1000.0) + " m", COLOR_TEXT, 1.0F);
        gauge(screen, 5, top + 17, 36, (float) (altitudeKm / Math.max(1.0, targetKm)), COLOR_ACCENT);
        label(screen, 5, top + 21, translate("cockpit.orbitalis.speed"), COLOR_MUTED, 0.65F);
        label(screen, 5, top + 27, grouped(speed) + " m/s", COLOR_TEXT, 1.0F);
        gauge(screen, 5, top + 36, 36, (float) (speed / FlightProfile.ORBIT_SPEED), COLOR_OK);

        // Volet central : la trajectoire sur son quadrillage, de la table à l'altitude visée, et la capsule dessus.
        float plotLeft = 47;
        float plotRight = 83;
        card(screen, plotLeft, top, plotRight, bottom);
        float innerLeft = plotLeft + 3;
        float innerRight = plotRight - 3;
        float innerTop = top + 4;
        float innerBottom = bottom - 4;
        for (int line = 1; line <= 3; line++) {
            float y = innerBottom - (innerBottom - innerTop) * line / 3.0F;
            rect(screen, innerLeft, y, innerRight, y + 0.4F, COLOR_TRACK, 2);
            float x = innerLeft + (innerRight - innerLeft) * line / 3.0F;
            rect(screen, x, innerTop, x + 0.4F, innerBottom, COLOR_TRACK, 2);
        }
        int samples = 24;
        for (int i = 0; i <= samples; i++) {
            float t = i / (float) samples;
            // La trajectoire calculée de cette fusée ; sans fusée, une courbe type.
            float x = innerLeft + (innerRight - innerLeft) * t;
            float y = innerBottom - (innerBottom - innerTop) * curve(stats, t);
            rect(screen, x, y, x + 1.3F, y + 1.3F, t <= progress ? COLOR_ACCENT : COLOR_EDGE, 3);
        }
        float markerX = innerLeft + (innerRight - innerLeft) * progress;
        float markerY = innerBottom - (innerBottom - innerTop) * curve(stats, progress);
        rect(screen, markerX - 2.2F, markerY - 2.2F, markerX + 3.4F, markerY + 3.4F, COLOR_ACCENT, 4);
        rect(screen, markerX - 1.0F, markerY - 1.0F, markerX + 2.2F, markerY + 2.2F, COLOR_TEXT, 5);

        // Volet droit : état des systèmes.
        float listX = 86;
        float listWidth = WIDTH - 2 - listX;
        card(screen, listX - 2, top, WIDTH - 2, bottom);
        if (onRocket && launch == LaunchTableBlockEntity.Phase.FLIGHT) {
            // En vol : l'assiette que tient le pilote, la consigne, et leur écart.
            float pitch = capsule.getPitch();
            float target = (float) table.targetPitch(progress);
            float error = Math.abs(pitch - target);
            int color = error <= 8.0F ? COLOR_OK : error <= LaunchTableBlockEntity.STRESS_ANGLE ? COLOR_WARNING : COLOR_BAD;
            system(screen, listX, top + 2, listWidth, translate("cockpit.orbitalis.pitch"), Math.round(pitch) + "°", color);
            gauge(screen, listX, top + 9.5F, listWidth - 2, pitch / 90.0F, color);
            system(screen, listX, top + 14, listWidth, translate("cockpit.orbitalis.target"), Math.round(target) + "°", COLOR_WARNING);
            system(screen, listX, top + 22, listWidth, translate("cockpit.orbitalis.error"), Math.round(error) + "°", color);
            system(screen, listX, top + 30, listWidth, translate("cockpit.orbitalis.cabin"), translate("cockpit.orbitalis.go"), COLOR_OK);
        } else if (onRocket) {
            // Au sol : la séquence de lancement, ligne par ligne.
            int fuel = table.fuelPercent();
            system(screen, listX, top + 2, listWidth, translate("cockpit.orbitalis.fuel"), fuel + " %", fuel >= 100 ? COLOR_OK : COLOR_BAD);
            gauge(screen, listX, top + 9.5F, listWidth - 2, fuel / 100.0F, fuel >= 100 ? COLOR_OK : COLOR_WARNING);
            boolean pressure = !table.isLiquid() || table.isPressurized() || launch == LaunchTableBlockEntity.Phase.COUNTDOWN;
            system(screen, listX, top + 14, listWidth, translate("cockpit.orbitalis.pressure"),
                    translate(pressure ? "cockpit.orbitalis.yes" : "cockpit.orbitalis.no"), pressure ? COLOR_OK : COLOR_BAD);
            boolean clear = table.isRetracted();
            system(screen, listX, top + 22, listWidth, translate("cockpit.orbitalis.arm"),
                    translate(clear ? "cockpit.orbitalis.arm_out" : "cockpit.orbitalis.arm_in"), clear ? COLOR_OK : COLOR_WARNING);
            // La poussée monte pendant les trois secondes qui suivent l'allumage.
            int thrust = launch != LaunchTableBlockEntity.Phase.COUNTDOWN ? 0 : (int) Mth.clamp(100.0F * (table.phaseTicks()
                    - (LaunchTableBlockEntity.COUNTDOWN_TICKS - LaunchTableBlockEntity.IGNITION_TICKS)) / LaunchTableBlockEntity.IGNITION_TICKS, 0.0F, 100.0F);
            system(screen, listX, top + 30, listWidth, translate("cockpit.orbitalis.thrust"), thrust + " %", thrust >= 100 ? COLOR_OK : COLOR_MUTED);
        } else {
            system(screen, listX, top + 3, listWidth, translate("cockpit.orbitalis.cabin"), translate("cockpit.orbitalis.go"), COLOR_OK);
            boolean chutes = phase == CapsuleEntity.Phase.CHUTE || phase == CapsuleEntity.Phase.LANDED;
            system(screen, listX, top + 12, listWidth, translate("cockpit.orbitalis.chutes"),
                    translate(chutes ? "cockpit.orbitalis.open" : "cockpit.orbitalis.armed"), chutes ? COLOR_OK : COLOR_MUTED);
            system(screen, listX, top + 21, listWidth, translate("cockpit.orbitalis.shield"), translate("cockpit.orbitalis.go"), COLOR_OK);
        }

        // Déroulé de la mission : cinq étapes ; celles qui sont passées sont pleines, celle en cours est claire.
        float stepLeft = 2;
        float stepWidth = (WIDTH - 4 - 4 * 2) / 5.0F;
        for (int i = 0; i < 5; i++) {
            float x = stepLeft + i * (stepWidth + 2);
            int color = i < step ? COLOR_EDGE : i == step ? clockColor : COLOR_TRACK;
            rect(screen, x, 68, x + stepWidth, 70.5F, color, 2);
            String name = translate("cockpit.orbitalis.step." + i);
            float nameWidth = font.width(name) * 0.6F;
            if (nameWidth <= stepWidth) {
                label(screen, x + (stepWidth - nameWidth) / 2.0F, 72.5F, name, i == step ? COLOR_TEXT : COLOR_MUTED, 0.6F);
            }
        }
    }

    /** Hauteur de la trajectoire à cet avancement du vol, de 0 (au sol) à 1 (au plus haut). */
    private static float curve(RocketStats stats, float progress) {
        if (stats == null) {
            return 1.0F - (1.0F - progress) * (1.0F - progress);
        }
        return (float) Mth.clamp(FlightProfile.altitudeKm(stats, progress) / FlightProfile.targetAltitudeKm(stats), 0.0, 1.0);
    }

    /** Fond d'un volet : un panneau, souligné d'un filet en haut. */
    private static void card(Screen screen, float x0, float y0, float x1, float y1) {
        rect(screen, x0, y0, x1, y1, COLOR_PANEL, 1);
        rect(screen, x0, y0, x1, y0 + 0.6F, COLOR_EDGE, 2);
    }

    /** Jauge horizontale : une piste sombre, remplie selon {@code value} (de 0 à 1). */
    private static void gauge(Screen screen, float x, float y, float width, float value, int color) {
        rect(screen, x, y, x + width, y + 2, COLOR_TRACK, 2);
        float filled = width * Mth.clamp(value, 0.0F, 1.0F);
        if (filled > 0.2F) {
            rect(screen, x, y, x + filled, y + 2, color, 3);
        }
    }

    /** Une ligne de l'état des systèmes : un voyant, le nom, et la valeur alignée à droite. */
    private void system(Screen screen, float x, float y, float width, String name, String value, int color) {
        rect(screen, x, y + 1.2F, x + 2.5F, y + 3.7F, color, 2);
        float valueWidth = font.width(value) * 0.65F;
        // Le nom n'empiète jamais sur la valeur : s'il est trop long, il est coupé et suivi de points de suspension.
        float room = (width - 2 - valueWidth - 2 - 4.5F) / 0.65F;
        String shown = name;
        if (font.width(shown) > room) {
            shown = font.plainSubstrByWidth(name, Math.max(0, (int) room - font.width("…"))).stripTrailing() + "…";
        }
        label(screen, x + 4.5F, y, shown, COLOR_MUTED, 0.65F);
        label(screen, x + width - 2 - valueWidth, y, value, color, 0.65F);
    }

    private static void rect(Screen screen, float x0, float y0, float x1, float y1, int color, int layer) {
        screen.rects.add(new Rect(x0, y0, x1, y1, color, layer));
    }

    private static void label(Screen screen, float x, float y, String text, int color, float scale) {
        screen.labels.add(new Label(x, y, Component.literal(text).getVisualOrderText(), color, scale));
    }

    private static String translate(String key) {
        return Component.translatable(key).getString();
    }

    private static String clock(float seconds) {
        int whole = (int) seconds;
        return String.format(Locale.ROOT, "%02d:%02d", whole / 60, whole % 60);
    }

    private static String grouped(double value) {
        return String.format(Locale.ROOT, "%,.0f", value).replace(',', ' ');
    }

    // ------------------------------------------------------------------
    // Dessin
    // ------------------------------------------------------------------

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        // La cabine tourne avec sa fusée, autour de la tête de son pilote.
        poseStack.translate(0.0F, (float) CapsuleEntity.EYE_HEIGHT, (float) CapsuleEntity.SEAT_BACK);
        poseStack.mulPose(state.attitude);
        poseStack.translate(0.0F, (float) -CapsuleEntity.EYE_HEIGHT, (float) -CapsuleEntity.SEAT_BACK);
        // Le modèle est dessiné autour d'un bloc, de 0 à 1 : on le centre sur la position de la capsule.
        // Il est le même que le tronçon « capsule » de la fusée : rien ne bouge à la séparation.
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        if (!state.rocketParts.isEmpty()) {
            submitRocket(state, poseStack, collector, camera.orientation);
        } else if (state.exposed) {
            if (state.burning != 0) {
                // Le moteur du vaisseau lunaire souffle sous lui ; les moteurs de la tour de sauvetage, au-dessus de la cabine.
                poseStack.pushPose();
                poseStack.translate(0.5F, state.burning == 3 ? 2.9F : -0.05F, 0.5F);
                submitPlume(poseStack, collector, state.burning == 3 ? 1.5F : 0.9F, state.burning == 2 ? 3.4F : state.burning == 1 ? 2.2F : 2.6F,
                        state.ageInTicks, camera.orientation);
                poseStack.popPose();
            }
            if (state.plasma > 0.0F) {
                // Rentrée : l'air s'embrase sous le bouclier, et le plasma remonte en gaine le long de la coque, dans
                // le sillage de la cabine. Attaché à elle, il la suit à toutes les vitesses.
                // Rien n'entre dans la cabine : la gaine naît à l'écart de la coque, et le brasier bien sous le bouclier.
                poseStack.pushPose();
                poseStack.translate(0.5F, 0.1F, 0.5F);
                submitPuffs(poseStack, collector, camera.orientation, state.ageInTicks, Math.round(10 + 14 * state.plasma), 0.5F, 4.5F * state.plasma + 1.0F, 1.0F,
                        0.7F, 0.4F, 1.7F);
                poseStack.translate(0.0F, -1.0F, 0.0F);
                submitPuffs(poseStack, collector, camera.orientation, state.ageInTicks + 37.0F, 6, 0.9F, 0.5F, -1.0F, 1.5F, 1.0F, 0.0F);
                poseStack.popPose();
            }
            // La coque prend le jour du dehors ; la cabine, ses plafonniers.
            LaunchTableRenderer.submitPart(state.variant == 2 ? StructurePartBlock.Part.CAPSULE_MK3_SHELL
                    : state.variant == 1 ? StructurePartBlock.Part.CAPSULE_MK2_SHELL : StructurePartBlock.Part.CAPSULE_SHELL,
                    poseStack, collector, state.lightCoords);
            LaunchTableRenderer.submitPart(state.variant == 2 ? StructurePartBlock.Part.CAPSULE_MK3_CABIN
                    : state.variant == 1 ? StructurePartBlock.Part.CAPSULE_MK2_CABIN : StructurePartBlock.Part.CAPSULE_CABIN,
                    poseStack, collector, state.cabinLight);
            if (state.lunar) {
                // Vaisseau lunaire : son train d'alunissage, autour du bouclier.
                LaunchTableRenderer.submitPart(StructurePartBlock.Part.LANDER, poseStack, collector, state.lightCoords);
            }
            if (state.parachutes) {
                // Les suspentes partent de l'écoutille, au sommet de la capsule.
                poseStack.pushPose();
                // La voile est dessinée plus grande que son modèle, autour du nez de la capsule d'où partent les suspentes.
                // Elle sort de son sac en fuseau étroit et long, puis s'ouvre en largeur à mesure qu'elle se gonfle.
                float open = 1.0F - (1.0F - state.chuteInflation) * (1.0F - state.chuteInflation);
                poseStack.translate(0.5F, 2.0F, 0.5F);
                poseStack.scale(1.5F * (0.15F + 0.85F * open), 1.5F * (1.35F - 0.35F * open), 1.5F * (0.15F + 0.85F * open));
                poseStack.translate(-0.5F, 0.0F, -0.5F);
                LaunchTableRenderer.submitPart(StructurePartBlock.Part.PARACHUTE, poseStack, collector, state.lightCoords);
                poseStack.popPose();
            }
        }
        // L'écran de bord, les instruments et le manche ne se voient que de près : au-delà de quelques blocs, ils ne
        // sont pas dessinés.
        if (state.distanceToCameraSq < 12.0 * 12.0 && (state.exposed || !state.rocketParts.isEmpty())) {
            if (!state.rects.isEmpty()) {
                submitScreen(state, poseStack, collector);
            }
            // La Mk2 se pilote au pupitre tactile : elle n'a pas de manche.
            if (state.variant != 1) {
                submitStick(state, poseStack, collector);
            }
            submitInstruments(state, poseStack, collector);
        }
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    /** Durée pendant laquelle on voit s'éloigner un étage largué, en ticks. */
    private static final float DROP_TICKS = 160.0F;

    /**
     * La fusée, pendant la montée : la colonne de ses tronçons, autour de la cabine. Un étage largué se détache :
     * ses verrous lâchent, il recule dans l'axe, de plus en plus vite puisque la fusée, elle, continue d'accélérer,
     * et il bascule lentement sur lui-même avant de disparaître au loin.
     */
    private static void submitRocket(State state, PoseStack poseStack, SubmitNodeCollector collector, org.joml.Quaternionf view) {
        submitStages(poseStack, collector, state.rocketParts, state.rocketSize, state.cabin, state.stageAges, state.ageInTicks, view, !state.coasting);
        if (state.boosterSide != null) {
            submitBoosters(poseStack, collector, state.cabin, state.boosterSide, state.boosterShape, state.boosterAge, state.ageInTicks, view,
                    !state.coasting);
        }
    }

    /**
     * La colonne des tronçons d'une fusée en vol, étage par étage, avec le largage de ceux qui se sont détachés. La
     * table de lancement s'en sert aussi, pour les fusées sans équipage.
     *
     * @param base   rang du tronçon posé à l'origine du repère
     * @param ages   temps écoulé depuis le largage de chaque étage, en ticks ; -1 tant qu'il est attaché
     * @param burning faux entre deux étages, moteurs coupés : la fusée n'a alors pas de panache
     */
    static void submitStages(PoseStack poseStack, SubmitNodeCollector collector, List<com.orbitalis.phase1.spaceport.RocketBlock.Part> parts,
            com.orbitalis.phase1.spaceport.RocketBlock.Size size, int base, float[] ages, float time, org.joml.Quaternionf view, boolean burning) {
        // Le dard des moteurs de l'étage allumé : le premier qui n'a pas été largué.
        int lit = 0;
        for (int stage = 0; stage < ages.length && ages[stage] >= 0.0F; stage++) {
            lit = com.orbitalis.phase2.flight.CapsuleEntity.stageStart(parts, stage + 1);
        }
        boolean wide = size == com.orbitalis.phase1.spaceport.RocketBlock.Size.LARGE;
        if (burning) {
            poseStack.pushPose();
            poseStack.translate(0.5F, lit - base + 0.15F, 0.5F);
            submitPlume(poseStack, collector, wide ? 2.6F : 1.0F, wide ? 11.0F : 5.5F, time, view);
            poseStack.popPose();
        }
        int from = 0;
        for (int stage = 0; from < parts.size() && stage < ages.length; stage++) {
            int to = from;
            while (to < parts.size() && parts.get(to) != com.orbitalis.phase1.spaceport.RocketBlock.Part.INTERSTAGE) {
                to++;
            }
            // L'interétage part avec l'étage qu'il coiffe.
            to = Math.min(parts.size(), to + 1);
            float age = ages[stage];
            if (age < DROP_TICKS) {
                poseStack.pushPose();
                if (age >= 0.0F) {
                    float middle = (from + to) * 0.5F - base;
                    // Un court instant d'immobilité, verrous ouverts, puis l'étage recule et bascule.
                    float moving = Math.max(0.0F, age - 4.0F);
                    poseStack.translate(0.0F, -(0.03F * moving + 0.0028F * moving * moving), 0.0F);
                    poseStack.translate(0.5F, middle, 0.5F);
                    poseStack.mulPose(Axis.XP.rotationDegrees(moving * moving * 0.006F));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(moving * 0.12F));
                    poseStack.translate(-0.5F, -middle, -0.5F);
                }
                for (int i = from; i < to; i++) {
                    net.minecraft.world.level.block.state.BlockState part = com.orbitalis.core.registry.ModBlocks.ROCKET.get().defaultBlockState()
                            .setValue(com.orbitalis.phase1.spaceport.RocketBlock.PART, parts.get(i))
                            .setValue(com.orbitalis.phase1.spaceport.RocketBlock.SIZE, size);
                    poseStack.pushPose();
                    poseStack.translate(0.0F, i - base, 0.0F);
                    // En plein ciel, la fusée est éclairée par le jour.
                    ModelCache.get(part).submit(poseStack, collector, LightCoordsUtil.FULL_BRIGHT, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0);
                    poseStack.popPose();
                }
                poseStack.popPose();
            }
            from = to;
        }
    }

    /**
     * Les deux boosters latéraux d'une fusée en vol, de part et d'autre du premier étage : ils poussent avec lui,
     * flamme comprise, puis à leur extinction se détachent, s'écartent de la fusée en basculant et tombent.
     *
     * @param base     rang du tronçon posé à l'origine du repère
     * @param side     direction, depuis l'axe, du premier booster ; le second est à l'opposé
     * @param distance écart entre l'axe et un booster, en blocs
     * @param age      temps écoulé depuis le largage, en ticks ; négatif tant qu'ils poussent
     * @param burning  vrai si les moteurs sont allumés
     */
    static void submitBoosters(PoseStack poseStack, SubmitNodeCollector collector, int base, net.minecraft.core.Direction side,
            com.orbitalis.phase1.spaceport.RocketShape shape, float age, float time, org.joml.Quaternionf view, boolean burning) {
        int distance = shape.isWide() ? 2 : 1;
        int height = shape.boosterHeight();
        float middle = height * 0.5F;
        float plume = shape.isWide() ? 1.2F : 0.7F;
        if (age >= DROP_TICKS) {
            return;
        }
        for (int sign = 1; sign >= -1; sign -= 2) {
            float dx = side.getStepX() * distance * sign;
            float dz = side.getStepZ() * distance * sign;
            poseStack.pushPose();
            poseStack.translate(dx, -base, dz);
            if (age >= 0.0F) {
                // Les attaches lâchent : le booster s'écarte de la fusée, prend du retard, et bascule vers l'extérieur.
                float moving = Math.max(0.0F, age - 2.0F);
                poseStack.translate(side.getStepX() * sign * 0.02F * moving, -(0.035F * moving + 0.003F * moving * moving),
                        side.getStepZ() * sign * 0.02F * moving);
                poseStack.translate(0.5F, middle, 0.5F);
                float tilt = Math.min(70.0F, moving * moving * 0.02F);
                if (side.getAxis() == net.minecraft.core.Direction.Axis.X) {
                    poseStack.mulPose(Axis.ZP.rotationDegrees(-tilt * sign * side.getStepX()));
                } else {
                    poseStack.mulPose(Axis.XP.rotationDegrees(tilt * sign * side.getStepZ()));
                }
                poseStack.translate(-0.5F, -middle, -0.5F);
            } else if (burning) {
                poseStack.pushPose();
                poseStack.translate(0.5F, 0.15F, 0.5F);
                submitPlume(poseStack, collector, plume, plume * 6.0F, time + sign * 7.0F, view);
                poseStack.popPose();
            }
            for (int k = 0; k < height; k++) {
                net.minecraft.world.level.block.state.BlockState part = com.orbitalis.core.registry.ModBlocks.ROCKET.get().defaultBlockState()
                        .setValue(com.orbitalis.phase1.spaceport.RocketBlock.PART, com.orbitalis.phase1.spaceport.RocketShape.boosterPart(k, height))
                        .setValue(com.orbitalis.phase1.spaceport.RocketBlock.SIZE, shape.boosterSize());
                poseStack.pushPose();
                poseStack.translate(0.0F, k, 0.0F);
                ModelCache.get(part).submit(poseStack, collector, LightCoordsUtil.FULL_BRIGHT, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0);
                poseStack.popPose();
            }
            poseStack.popPose();
        }
    }

    private static final net.minecraft.resources.Identifier PLUME = com.orbitalis.Orbitalis.id("textures/entity/exhaust_flame.png");

    /**
     * Le dard d'un moteur : trois plans croisés portant l'image de la flamme, attachés à la tuyère et dirigés vers
     * le bas du repère. Parce qu'il est dessiné avec l'engin, il en suit l'inclinaison et le mouvement exactement ;
     * il vacille en longueur et en largeur, à des rythmes différents, comme une combustion qui bat.
     *
     * @param width  largeur du dard à son plus large, en blocs
     * @param length longueur du dard, en blocs
     * @param time   temps, en ticks : il rythme le vacillement
     */
    static void submitPlume(PoseStack poseStack, SubmitNodeCollector collector, float width, float length, float time, org.joml.Quaternionf view) {
        submitPuffs(poseStack, collector, view, time, 18, width * 0.5F, length * 1.25F, -1.0F, width * 0.3F, width * 0.75F, 0.0F);
        float reach = length * (1.0F + 0.07F * Mth.sin(time * 1.9F) + 0.05F * Mth.sin(time * 3.7F + 1.0F));
        float half = width * 0.5F * (1.0F + 0.06F * Mth.sin(time * 2.9F + 0.5F));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(PLUME), (pose, buffer) -> {
            for (int plane = 0; plane < 3; plane++) {
                float angle = plane * Mth.PI / 3.0F + time * 0.02F;
                float dx = Mth.cos(angle) * half;
                float dz = Mth.sin(angle) * half;
                float[][] corners = {{-dx, 0.0F, -dz, 0.0F, 0.0F}, {-dx, -reach, -dz, 0.0F, 1.0F}, {dx, -reach, dz, 1.0F, 1.0F}, {dx, 0.0F, dz, 1.0F, 0.0F}};
                for (int pass = 0; pass < 2; pass++) {
                    for (int k = 0; k < 4; k++) {
                        float[] corner = corners[pass == 0 ? k : 3 - k];
                        buffer.addVertex(pose, corner[0], corner[1], corner[2]).setColor(0xFFFFFFFF).setUv(corner[3], corner[4])
                                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT)
                                .setNormal(pose, 0.0F, 0.0F, 1.0F);
                    }
                }
            }
        });
    }

    private static final net.minecraft.resources.Identifier PUFF = com.orbitalis.Orbitalis.id("textures/entity/exhaust_puff.png");

    /**
     * Des bouffées de flamme qui défilent le long d'un axe de l'engin, attachées à lui. Rien n'est gardé en mémoire
     * d'une image à l'autre : la place de chaque bouffée se calcule d'après l'heure. Elles naissent à l'origine,
     * s'en éloignent en s'écartant et en grossissant, passent du blanc au jaune, à l'orangé puis au rouge sombre, et
     * s'effacent. Parce qu'elles sont dessinées dans le repère de l'engin, elles en suivent l'inclinaison et la
     * trajectoire exactement, à toutes les vitesses, sans jamais rester en arrière. Chacune fait face à l'observateur.
     *
     * @param view      orientation de la caméra
     * @param count     nombre de bouffées
     * @param spread    écartement maximal autour de l'axe, en blocs
     * @param length    distance parcourue le long de l'axe, en blocs
     * @param direction sens du défilement le long de l'axe vertical de l'engin : -1 vers le bas, 1 vers le haut
     * @param small     taille d'une bouffée à sa naissance, puis à sa fin
     * @param ring      rayon du cercle où naissent les bouffées (0 : au centre)
     */
    static void submitPuffs(PoseStack poseStack, SubmitNodeCollector collector, org.joml.Quaternionf view, float time, int count, float spread,
            float length, float direction, float small, float large, float ring) {
        // Les axes de l'écran, exprimés dans le repère de l'engin : une bouffée dessinée sur eux fait face à la caméra.
        org.joml.Quaternionf local = poseStack.last().pose().getUnnormalizedRotation(new org.joml.Quaternionf()).normalize().conjugate();
        org.joml.Vector3f right = local.transform(view.transform(new org.joml.Vector3f(1.0F, 0.0F, 0.0F)));
        org.joml.Vector3f up = local.transform(view.transform(new org.joml.Vector3f(0.0F, 1.0F, 0.0F)));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(PUFF), (pose, buffer) -> {
            for (int i = 0; i < count; i++) {
                // Chaque bouffée vit une demi-seconde, décalée des autres ; à chaque vie, elle prend une autre place.
                float clock = time * 0.1F + i * 0.618034F;
                float life = clock - Mth.floor(clock);
                float turn = (Mth.floor(clock) * 2.39996F + i * 1.7F) % Mth.TWO_PI;
                float out = ring + spread * life * (0.35F + 0.65F * ((i * 7 % 5) / 4.0F));
                float cx = Mth.cos(turn) * out;
                float cz = Mth.sin(turn) * out;
                float cy = direction * length * life * (0.75F + 0.25F * ((i * 3 % 4) / 3.0F));
                float size = Mth.lerp(life, small, large) * 0.5F;
                int alpha = (int) (235.0F * (1.0F - life) * Math.min(1.0F, life * 6.0F + 0.35F));
                int color = life < 0.22F ? 0xFFF6DC : life < 0.5F ? 0xFFC850 : life < 0.78F ? 0xFF7A28 : 0xB8381C;
                int tinted = alpha << 24 | color;
                float[][] corners = {{-1.0F, -1.0F, 0.0F, 1.0F}, {-1.0F, 1.0F, 0.0F, 0.0F}, {1.0F, 1.0F, 1.0F, 0.0F}, {1.0F, -1.0F, 1.0F, 1.0F}};
                for (int pass = 0; pass < 2; pass++) {
                    for (int k = 0; k < 4; k++) {
                        float[] corner = corners[pass == 0 ? k : 3 - k];
                        buffer.addVertex(pose, cx + (right.x * corner[0] + up.x * corner[1]) * size, cy + (right.y * corner[0] + up.y * corner[1]) * size,
                                        cz + (right.z * corner[0] + up.z * corner[1]) * size)
                                .setColor(tinted).setUv(corner[2], corner[3]).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                                .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0.0F, 0.0F, 1.0F);
                    }
                }
            }
        });
    }

    /** La fusée dépasse de beaucoup la capsule : tant qu'elle vole, on la dessine même si la capsule est hors du champ. */
    @Override
    public boolean shouldRender(CapsuleEntity capsule, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return capsule.isFollowingRocket() || super.shouldRender(capsule, frustum, x, y, z);
    }

    // ------------------------------------------------------------------
    // La cabine : le manche, les instruments, l'ambiance
    // ------------------------------------------------------------------

    /** Le manche, penché autour de son pied selon les commandes du pilote. */
    private static void submitStick(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        poseStack.pushPose();
        poseStack.translate(12.0F / 16.0F, 5.5F / 16.0F, 7.8F / 16.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.stickYaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-state.stickPitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-state.stickRoll));
        poseStack.translate(-12.0F / 16.0F, -5.5F / 16.0F, -7.8F / 16.0F);
        LaunchTableRenderer.submitPart(StructurePartBlock.Part.CAPSULE_STICK, poseStack, collector, state.cabinLight);
        poseStack.popPose();
    }

    private static final int LAMP_OFF = 0xFF241A1C;
    private static final int LAMP_GREEN = 0xFF4FD67A;
    private static final int LAMP_AMBER = 0xFFFFB23E;
    private static final int LAMP_RED = 0xFFFF4A3D;

    /**
     * Les instruments vivants de la planche de bord et l'ambiance de la cabine :
     * <ul>
     * <li>le globe de navigation, à gauche : la planète qui tourne sous la capsule, son réticule au centre ;</li>
     * <li>trois cadrans à aiguille : l'altitude (vers l'altitude visée), la vitesse (vers celle de l'orbite) et
     * l'accélération (jusqu'à 5 g) ;</li>
     * <li>sur le pare-soleil, le bandeau de huit voyants des systèmes, entre l'alarme générale (rouge) et l'alerte
     * (ambre) ;</li>
     * <li>le filet lumineux du plafond, blanc au sol, bleuté en orbite, ambre au compte à rebours, rouge en rentrée et
     * en éjection ;</li>
     * <li>pendant la rentrée, la lueur orangée du plasma derrière les hublots.</li>
     * </ul>
     */
    private static void submitInstruments(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        float t = state.time;
        boolean blink = ((int) (t / 6.0F)) % 2 == 0;
        double altitude = state.targetKm <= 0 ? 0.0 : state.altitudeKm / state.targetKm;
        double velocity = state.speed / FlightProfile.ORBIT_SPEED;
        double load = state.gForce / 5.0;
        int[] lamps = new int[8];
        lamps[0] = LAMP_GREEN;
        lamps[1] = LAMP_GREEN;
        lamps[2] = state.mode == MODE_ASCENT ? LAMP_GREEN : state.mode == MODE_COUNTDOWN ? LAMP_AMBER : LAMP_OFF;
        lamps[3] = state.mode == MODE_ASCENT && blink ? LAMP_AMBER : LAMP_OFF;
        lamps[4] = state.chutes ? LAMP_GREEN : state.mode == MODE_DESCENT ? LAMP_AMBER : LAMP_OFF;
        lamps[5] = state.plasma > 0.8F ? LAMP_RED : state.plasma > 0.2F ? LAMP_AMBER : LAMP_OFF;
        lamps[6] = state.mode == MODE_ORBIT ? LAMP_GREEN : LAMP_OFF;
        lamps[7] = state.mode == MODE_ABORT ? LAMP_RED : LAMP_GREEN;
        int alarm = (state.mode == MODE_ABORT || state.plasma > 0.8F) && blink ? LAMP_RED : LAMP_OFF;
        int caution = state.mode == MODE_COUNTDOWN || state.plasma > 0.2F ? LAMP_AMBER : LAMP_OFF;
        int ambient = state.mode == MODE_ABORT || state.plasma > 0.2F ? 0xFFFF5A44
                : state.mode == MODE_COUNTDOWN ? 0xFFFFC46A : state.mode == MODE_ORBIT ? 0xFFC4DCFF : 0xFFEAF6FF;
        float plasma = state.plasma * (0.8F + 0.12F * Mth.sin(t * 1.7F) + 0.08F * Mth.sin(t * 4.3F + 1.0F));

        collector.submitCustomGeometry(poseStack, RenderTypes.textBackground(), (pose, buffer) -> {
            float turn = state.mode == MODE_ASCENT || state.mode == MODE_ORBIT ? t * 0.004F : 0.8F;
            if (state.variant == 0) {
                // Mk1 : le globe dans sa lunette, et les cadrans.
                globe(buffer, pose, -2.0F, 12.2F, -3.67F, 1.45F, turn);
                needle(buffer, pose, -2.0F, 7.9F, -3.77F, 1.15F, (float) altitude);
                needle(buffer, pose, 18.0F, 12.1F, -3.77F, 1.15F, (float) velocity);
                needle(buffer, pose, 18.0F, 7.9F, -3.77F, 1.15F, (float) load);
            } else {
                // Mk2 et Mk3 : à gauche, l'écran de navigation, le globe en grand dans son cadran ; à droite, l'écran des
                // systèmes, trois jauges verticales (altitude, vitesse, accélération) et leurs voyants.
                glassBackground(buffer, pose, -3.9F, 6.5F, 0.5F, 14.5F, -3.93F);
                glassBackground(buffer, pose, 15.5F, 6.5F, 19.9F, 14.5F, -3.93F);
                globe(buffer, pose, -1.7F, 10.6F, -3.91F, 1.85F, turn);
                ringMarks(buffer, pose, -1.7F, 10.6F, -3.905F, 2.05F);
                bar(buffer, pose, 16.1F, 7.4F, 16.9F, 13.0F, -3.91F, (float) altitude, 0xFF35C3F0);
                bar(buffer, pose, 17.3F, 7.4F, 18.1F, 13.0F, -3.91F, (float) velocity, 0xFF4FD67A);
                bar(buffer, pose, 18.5F, 7.4F, 19.3F, 13.0F, -3.91F, (float) load, 0xFFFFB23E);
                for (int k = 0; k < 4; k++) {
                    facingSouth(buffer, pose, 16.1F + k * 0.85F, 13.5F, 16.7F + k * 0.85F, 13.95F, -3.91F, lamps[k * 2]);
                }
            }
            // Les touches de fonction sous l'écran : celle du moment du vol est allumée.
            int page = Mth.clamp(state.mode == MODE_ABORT ? 4 : state.mode == MODE_DESCENT ? 3 : state.mode, 0, 4);
            for (int k = 0; k < 5; k++) {
                if (k == page) {
                    facingSouth(buffer, pose, 2.3F + 2.5F * k, 5.65F, 3.7F + 2.5F * k, 6.15F, -3.94F, 0x6F7FD0FF);
                }
            }
            // Le bandeau des voyants, et l'alarme générale de part et d'autre.
            for (int k = 0; k < lamps.length; k++) {
                float x = 0.6F + 1.9F * k;
                facingSouth(buffer, pose, x, 15.42F, x + 1.3F, 15.8F, -3.58F, lamps[k]);
            }
            facingSouth(buffer, pose, -4.0F, 15.4F, -1.6F, 15.82F, -3.58F, alarm);
            facingSouth(buffer, pose, 17.6F, 15.4F, 20.0F, 15.82F, -3.58F, caution);
            // Le filet lumineux du plafond, vu d'en dessous.
            facingDown(buffer, pose, -4.8F, 20.5F, 20.8F, 20.8F, 18.97F, ambient);
            facingDown(buffer, pose, -4.8F, -4.5F, -4.5F, 20.5F, 18.97F, ambient);
            facingDown(buffer, pose, 20.5F, -4.5F, 20.8F, 20.5F, 18.97F, ambient);
            // Le plasma de la rentrée, derrière le pare-brise et les baies.
            if (plasma > 0.01F) {
                int glow = Mth.clamp((int) (165.0F * plasma), 0, 200) << 24 | 0xFF7A28;
                facingSouth(buffer, pose, 2.0F, 15.9F, 14.0F, 19.5F, -4.97F, glow);
                facingEast(buffer, pose, -4.97F, 11.0F, 2.0F, 18.0F, 14.0F, glow);
                facingEast(buffer, pose, 20.97F, 11.0F, 2.0F, 18.0F, 14.0F, glow);
            }
        });
    }

    /**
     * Jauge en arc d'un afficheur rond : une couronne de segments qui s'allument de -135° (zéro) à +135° (pleine
     * échelle), verts, puis ambre, puis rouges en fin d'échelle ; et l'aiguille fine par-dessus.
     */
    private static void needle(com.mojang.blaze3d.vertex.VertexConsumer buffer, PoseStack.Pose pose, float cx, float cy, float z, float length,
            float fraction) {
        float value = Mth.clamp(fraction, 0.0F, 1.0F);
        int segments = 18;
        for (int k = 0; k < segments; k++) {
            float from = (-135.0F + 270.0F * k / segments + 2.0F) * Mth.DEG_TO_RAD;
            float to = (-135.0F + 270.0F * (k + 1) / segments - 2.0F) * Mth.DEG_TO_RAD;
            float share = (k + 0.5F) / segments;
            int lit = share > 0.85F ? 0xFFE0655F : share > 0.65F ? 0xFFFFB23E : 0xFF4FD67A;
            int color = share <= value ? lit : 0xFF16232C;
            float inner = length * 0.76F;
            float outer = length * 1.02F;
            quad(buffer, pose, new float[] {cx + Mth.sin(from) * inner, cy + Mth.cos(from) * inner, z},
                    new float[] {cx + Mth.sin(to) * inner, cy + Mth.cos(to) * inner, z},
                    new float[] {cx + Mth.sin(to) * outer, cy + Mth.cos(to) * outer, z},
                    new float[] {cx + Mth.sin(from) * outer, cy + Mth.cos(from) * outer, z}, color);
        }
        length *= 0.72F;
        z += 0.01F;
        float angle = (-135.0F + 270.0F * value) * Mth.DEG_TO_RAD;
        float dx = Mth.sin(angle);
        float dy = Mth.cos(angle);
        float half = 0.07F;
        float px = dy * half;
        float py = -dx * half;
        quad(buffer, pose, new float[] {cx + px, cy + py, z}, new float[] {cx + dx * length + px * 0.4F, cy + dy * length + py * 0.4F, z},
                new float[] {cx + dx * length - px * 0.4F, cy + dy * length - py * 0.4F, z}, new float[] {cx - px, cy - py, z}, 0xFFF2F4F7);
        facingSouth(buffer, pose, cx - 0.16F, cy - 0.16F, cx + 0.16F, cy + 0.16F, z + 0.01F, 0xFF7FD0FF);
    }

    /**
     * Le globe de navigation : la planète vue d'en haut, qui défile sous la capsule. Chaque petit carré prend la couleur
     * du point de la sphère qu'il montre (mer, terre, glace), assombrie vers le bord ; un réticule marque la verticale.
     */
    private static void globe(com.mojang.blaze3d.vertex.VertexConsumer buffer, PoseStack.Pose pose, float cx, float cy, float z, float radius,
            float turn) {
        int cells = 13;
        float size = 2.0F * radius / cells;
        for (int i = 0; i < cells; i++) {
            for (int j = 0; j < cells; j++) {
                float u = (i + 0.5F) / cells * 2.0F - 1.0F;
                float v = (j + 0.5F) / cells * 2.0F - 1.0F;
                float r2 = u * u + v * v;
                if (r2 > 1.0F) {
                    continue;
                }
                float depth = Mth.sqrt(1.0F - r2);
                float lat = (float) Math.asin(v);
                float lon = (float) Math.atan2(u, depth) + turn;
                float land = Mth.sin(lon * 2.0F + 1.3F) * Mth.cos(lat * 3.0F) + 0.6F * Mth.sin(lon * 5.0F + lat * 4.0F)
                        + 0.4F * Mth.cos(lon * 3.0F - lat * 7.0F);
                int base = Math.abs(lat) > 1.15F ? 0xE8EEF2 : land > 0.45F ? 0x4C9A3A : land > 0.3F ? 0xC9B26A : 0x1F5FA8;
                float shade = 0.45F + 0.55F * depth;
                int color = 0xFF000000 | ((int) (((base >> 16) & 255) * shade) << 16) | ((int) (((base >> 8) & 255) * shade) << 8)
                        | (int) ((base & 255) * shade);
                float x0 = cx - radius + i * size;
                float y0 = cy + radius - (j + 1) * size;
                facingSouth(buffer, pose, x0, y0, x0 + size, y0 + size, z, color);
            }
        }
        facingSouth(buffer, pose, cx - radius * 0.6F, cy - 0.04F, cx + radius * 0.6F, cy + 0.04F, z + 0.01F, 0xFFF2F4F7);
        facingSouth(buffer, pose, cx - 0.04F, cy - radius * 0.6F, cx + 0.04F, cy + radius * 0.6F, z + 0.01F, 0xFFF2F4F7);
    }

    /** Fond d'un grand écran allumé : un bleu nuit, plus clair en haut, et un filet en tête. */
    private static void glassBackground(com.mojang.blaze3d.vertex.VertexConsumer buffer, PoseStack.Pose pose, float x0, float y0, float x1, float y1,
            float z) {
        facingSouth(buffer, pose, x0, y0, x1, y1, z, 0xFF05080C);
        facingSouth(buffer, pose, x0, y1 - 0.55F, x1, y1, z + 0.005F, 0xFF0C141D);
        facingSouth(buffer, pose, x0, y1 - 0.6F, x1, y1 - 0.55F, z + 0.006F, 0xFF1D5F78);
    }

    /** Jauge verticale d'un écran : sa piste sombre, remplie de bas en haut ; une graduation tous les quarts. */
    private static void bar(com.mojang.blaze3d.vertex.VertexConsumer buffer, PoseStack.Pose pose, float x0, float y0, float x1, float y1, float z,
            float value, int color) {
        facingSouth(buffer, pose, x0, y0, x1, y1, z, 0xFF16222E);
        float top = y0 + (y1 - y0) * Mth.clamp(value, 0.0F, 1.0F);
        if (top > y0 + 0.05F) {
            facingSouth(buffer, pose, x0, y0, x1, top, z + 0.005F, color);
        }
        for (int k = 1; k < 4; k++) {
            float y = y0 + (y1 - y0) * k / 4.0F;
            facingSouth(buffer, pose, x1, y - 0.03F, x1 + 0.2F, y + 0.03F, z + 0.006F, 0xFF7F96A8);
        }
    }

    /** Les graduations d'un cadran de navigation : douze traits autour du globe, le nord plus long. */
    private static void ringMarks(com.mojang.blaze3d.vertex.VertexConsumer buffer, PoseStack.Pose pose, float cx, float cy, float z, float radius) {
        for (int k = 0; k < 12; k++) {
            float a = k * Mth.TWO_PI / 12.0F;
            float inner = radius - (k == 0 ? 0.35F : 0.2F);
            float dx = Mth.sin(a);
            float dy = Mth.cos(a);
            float px = dy * 0.04F;
            float py = -dx * 0.04F;
            quad(buffer, pose, new float[] {cx + dx * inner + px, cy + dy * inner + py, z}, new float[] {cx + dx * radius + px, cy + dy * radius + py, z},
                    new float[] {cx + dx * radius - px, cy + dy * radius - py, z}, new float[] {cx + dx * inner - px, cy + dy * inner - py, z},
                    k == 0 ? 0xFF35C3F0 : 0xFF4A6878);
        }
    }

    /** Un rectangle dressé, tourné vers le sud (vers l'équipage), en pixels du modèle. */
    private static void facingSouth(com.mojang.blaze3d.vertex.VertexConsumer buffer, PoseStack.Pose pose, float x0, float y0, float x1, float y1,
            float z, int color) {
        quad(buffer, pose, new float[] {x0, y0, z}, new float[] {x1, y0, z}, new float[] {x1, y1, z}, new float[] {x0, y1, z}, color);
    }

    /** Un rectangle à plat, vu d'en dessous, en pixels du modèle. */
    private static void facingDown(com.mojang.blaze3d.vertex.VertexConsumer buffer, PoseStack.Pose pose, float x0, float z0, float x1, float z1,
            float y, int color) {
        quad(buffer, pose, new float[] {x0, y, z0}, new float[] {x0, y, z1}, new float[] {x1, y, z1}, new float[] {x1, y, z0}, color);
    }

    /** Un rectangle dressé le long de l'axe nord-sud, en pixels du modèle. */
    private static void facingEast(com.mojang.blaze3d.vertex.VertexConsumer buffer, PoseStack.Pose pose, float x, float y0, float z0, float y1,
            float z1, int color) {
        quad(buffer, pose, new float[] {x, y0, z0}, new float[] {x, y0, z1}, new float[] {x, y1, z1}, new float[] {x, y1, z0}, color);
    }

    /** Un quadrilatère, en pixels du modèle, dessiné des deux côtés. */
    private static void quad(com.mojang.blaze3d.vertex.VertexConsumer buffer, PoseStack.Pose pose, float[] a, float[] b, float[] c, float[] d,
            int color) {
        if ((color >>> 24) == 0) {
            return;
        }
        float[][] corners = {a, b, c, d};
        for (int pass = 0; pass < 2; pass++) {
            for (int k = 0; k < 4; k++) {
                float[] p = corners[pass == 0 ? k : 3 - k];
                buffer.addVertex(pose, p[0] / 16.0F, p[1] / 16.0F, p[2] / 16.0F).setColor(color).setLight(LightCoordsUtil.FULL_BRIGHT);
            }
        }
    }

    /** Dessine l'image de l'écran de bord, un rien devant sa dalle. */
    private void submitScreen(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        poseStack.pushPose();
        // Coin haut gauche de l'écran, vu du siège ; x vers la droite du pilote (l'est), y vers le bas, z vers lui
        // (le sud).
        poseStack.translate(SCREEN_LEFT / 16.0F, SCREEN_TOP / 16.0F, (SCREEN_PLANE + 0.04F) / 16.0F);
        poseStack.scale(POINT, -POINT, POINT);

        List<Rect> rects = state.rects;
        collector.submitCustomGeometry(poseStack, RenderTypes.textBackground(), (pose, buffer) -> {
            for (Rect rect : rects) {
                // Chaque couche est décalée d'un rien vers le pilote : aucun rectangle ne se confond avec un autre.
                float z = rect.layer() * 0.03F;
                buffer.addVertex(pose, rect.x0(), rect.y0(), z).setColor(rect.color()).setLight(LightCoordsUtil.FULL_BRIGHT);
                buffer.addVertex(pose, rect.x0(), rect.y1(), z).setColor(rect.color()).setLight(LightCoordsUtil.FULL_BRIGHT);
                buffer.addVertex(pose, rect.x1(), rect.y1(), z).setColor(rect.color()).setLight(LightCoordsUtil.FULL_BRIGHT);
                buffer.addVertex(pose, rect.x1(), rect.y0(), z).setColor(rect.color()).setLight(LightCoordsUtil.FULL_BRIGHT);
            }
        });
        for (Label label : state.labels) {
            poseStack.pushPose();
            poseStack.translate(label.x(), label.y(), 0.25F);
            poseStack.scale(label.scale(), label.scale(), 1.0F);
            collector.submitText(poseStack, 0.0F, 0.0F, label.text(), false, Font.DisplayMode.NORMAL, LightCoordsUtil.FULL_BRIGHT,
                    label.color(), 0, 0);
            poseStack.popPose();
        }
        poseStack.popPose();
    }
}
