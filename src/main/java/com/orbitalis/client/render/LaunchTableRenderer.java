package com.orbitalis.client.render;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.orbitalis.Orbitalis;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.phase1.rocket.RocketFuel;
import com.orbitalis.phase1.spaceport.LaunchComplex;
import com.orbitalis.phase1.spaceport.LaunchTableBlockEntity;
import com.orbitalis.phase1.spaceport.LedDriver;
import com.orbitalis.phase1.spaceport.RocketBlock;
import com.orbitalis.phase1.spaceport.RocketShape;
import com.orbitalis.phase1.spaceport.StructurePartBlock;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Dessine et anime le pas de tir assemblé : son tablier d'un seul tenant, son carneau, son déluge, ses mâts,
 * ses bras ombilicaux, ses passerelles d'embarquement, et la fusée qui décolle. Les blocs du tablier, des mâts et
 * des bras sont alors effacés.
 * <p>
 * La tour de service, elle, n'est pas dessinée ici : elle est faite de vrais blocs pleins et ne bouge pas. Seuls
 * ce qu'elle porte s'anime.
 * <p>
 * Le pas de tir est assemblé à partir d'un kit de pièces (voir {@code tools/generate_launch_pad.py}) : les cinq
 * tailles utilisent les mêmes, en plus ou moins grand nombre. Au repos rien ne bouge et rien n'est calculé :
 * chaque pièce est un modèle gardé en mémoire, posé à sa place.
 * <p>
 * La chronologie s'inspire des vrais lancements :
 * <ol>
 * <li>à partir de T-15, les passerelles d'embarquement pivotent et se rangent le long de la tour ;</li>
 * <li>les flexibles se débranchent un à un, de bas en haut, et chaque bras pivote à son tour ;</li>
 * <li>au décollage la fusée s'élève, d'abord lentement puis de plus en plus vite, jusqu'à disparaître dans le ciel ;</li>
 * <li>après le vol, tout revient en place.</li>
 * </ol>
 */
public class LaunchTableRenderer implements BlockEntityRenderer<LaunchTableBlockEntity, LaunchTableRenderer.State> {
    /** Image du halo d'une LED : un cœur blanc, une auréole qui s'efface, quatre aigrettes. */
    private static final net.minecraft.resources.Identifier LED_GLOW = Orbitalis.id("textures/entity/led_glow.png");

    /** État de bloc de chaque pièce de structure, préparé à la première demande. */
    private static final java.util.Map<StructurePartBlock.Part, BlockState> PART_STATES = new java.util.EnumMap<>(StructurePartBlock.Part.class);
    /** Demi-largeur de la capsule habitée, en blocs (28 pixels de large). */
    private static final float CAPSULE_HALF_WIDTH = 14.0F / 16.0F;
    /** Longueur minimale d'un bras, en blocs. */
    private static final float MIN_ARM_LENGTH = 0.3F;
    /** Part de leur longueur que le bras et la passerelle perdent en rentrant dans la tour. */
    private static final float ARM_RETRACT = 0.75F;
    private static final float GANGWAY_RETRACT = 0.8F;
    /** Écart entre la face de la tour et le départ d'un bras ou d'une passerelle : leurs faces ne se confondent pas. */
    private static final float FACE_GAP = 0.03F;
    /** Hauteur du platelage d'une passerelle au-dessus du seuil de son portique, et longueur de son seuil, en blocs. */
    private static final float GANGWAY_FLOOR = 1.0F / 16.0F;
    private static final float GANGWAY_SILL = 2.5F / 16.0F;
    /** Épaisseur de la plaque de raccordement au bout des flexibles, en blocs. */
    private static final float PLATE_THICKNESS = 1.5F / 16.0F;
    /** Distance au-delà de laquelle les bras sont dessinés sans leurs détails, en blocs. */
    private static final double DETAIL_RANGE = 144.0;

    /** Ce qu'il faut pour dessiner le pas de tir à un instant donné. */
    public static class State extends BlockEntityRenderState {
        boolean towerVisible;
        /** Pied de la tour, relativement à la table. */
        int towerX;
        int towerY;
        int towerZ;
        int towerHeight;
        /** Hauteur de chaque bras au-dessus du pied de la tour. */
        final List<Float> armHeights = new ArrayList<>();
        /** Direction qui va de la tour vers la fusée. */
        Direction toRocket = Direction.EAST;
        /** Distance entre la face de la tour et le flanc de la fusée, en blocs. */
        float gap;
        /** Distance entre la tour et la fusée au niveau de chaque bras : la capsule est plus large que le corps. */
        final List<Float> armGaps = new ArrayList<>();
        /** Hauteur du seuil de chaque passerelle d'embarquement au-dessus du pied de la tour. */
        final List<Float> gangways = new ArrayList<>();
        /** Longueur des passerelles : de la face de la tour au flanc de la fusée (ou de la capsule), en blocs. */
        float gangwayReach;
        /** Ergols que la fusée embarque : chacun a sa conduite sous les bras. */
        boolean lox;
        boolean lh2;
        boolean rp1;
        /** Vrai pendant un compte à rebours ou un vol : feux à éclats, balises plus pressantes. */
        boolean active;
        /** Vrai si les projecteurs sont allumés : de nuit, et pendant tout un lancement. */
        boolean lamps;
        /** Lueur des moteurs sur la structure, de 0 à 1. */
        float glow;
        /** Vrai si le pas de tir est vu de loin : les bras sont dessinés sans leurs détails. */
        boolean far;
        /** Dégagement de la structure, de 0 (tout en place) à 1 (bras et passerelles rangés). */
        float clearance;
        /** Vrai si les bras sont au repos, sans fusée à laquelle se brancher : pas de flexibles. */
        boolean idleArms;
        int structureLight;
        /** Blocs du plan qui manquent encore : leur position par rapport à la table, et leur aspect. */
        final List<BlockPos> ghostOffsets = new ArrayList<>();
        final List<BlockState> ghostStates = new ArrayList<>();

        /** Tablier assemblé : sa taille, le sens de son carneau, la hauteur de ses quatre mâts. */
        boolean deckVisible;
        int radius;
        Direction trench = Direction.EAST;
        final int[] mastHeights = new int[4];
        int deckLight;

        /** Fusée en vol : sa silhouette et la hauteur dont elle s'est élevée, en blocs. */
        RocketShape flyingRocket;
        /** Heure du jeu, en ticks : elle rythme le clignotement des balises. */
        long gameTime;
        /** Heure du monde avec sa fraction de tick, puissance des projecteurs (0 à 1), et vrai s'il fait nuit. */
        double time;
        float lampGlow;
        boolean dark;
        float ascent;
        /** Orientation de la fusée en vol, et hauteur, au-dessus de son pied, du point autour duquel elle tourne. */
        final org.joml.Quaternionf attitude = new org.joml.Quaternionf();
        float pivot;
        float pivotBack;
        /** Écart de la fusée en vol à la verticale de sa table, vers l'est et vers le sud, en blocs. */
        /** Temps écoulé depuis le largage de chaque étage, en ticks ; -1 tant qu'il est attaché. */
        final float[] stageAges = new float[16];
        /** Temps écoulé depuis le largage des boosters latéraux, en ticks ; négatif tant qu'ils poussent. */
        float boosterAge;
        boolean enginesOut;
        float driftX;
        float driftZ;
    }

    public LaunchTableRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(LaunchTableBlockEntity table, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(table, state, partialTicks, cameraPosition, breakProgress);
        LaunchComplex complex = table.getComplex();
        BlockPos tower = complex.towerBase();
        BlockPos origin = table.getBlockPos();
        RocketShape shape = table.hasRocket() ? table.rocketShape() : null;

        state.gameTime = table.getLevel() == null ? 0L : table.getLevel().getGameTime();
        state.time = state.gameTime + partialTicks;
        state.lampGlow = table.lampGlow(partialTicks);
        state.dark = table.getLevel() != null && table.getLevel().isDarkOutside();
        LaunchTableBlockEntity.Phase phase = table.getPhase();
        state.active = phase != LaunchTableBlockEntity.Phase.IDLE;
        state.lamps = state.active || (table.getLevel() != null && table.getLevel().isDarkOutside());
        state.far = cameraPosition.distanceToSqr(Vec3.atCenterOf(origin)) > DETAIL_RANGE * DETAIL_RANGE;
        state.ghostOffsets.clear();
        state.ghostStates.clear();
        for (LaunchTableBlockEntity.Ghost ghost : table.getGhosts()) {
            state.ghostOffsets.add(ghost.offset());
            state.ghostStates.add(ghost.state());
        }
        // Lueur des moteurs : elle monte à l'allumage, trois secondes avant le décollage, puis s'éloigne avec la fusée.
        float remaining = (LaunchTableBlockEntity.COUNTDOWN_TICKS - table.phaseTicks() - partialTicks) / 20.0F;
        state.glow = phase == LaunchTableBlockEntity.Phase.COUNTDOWN
                ? Mth.clamp((LaunchTableBlockEntity.IGNITION_TICKS / 20.0F - remaining) / 1.2F, 0.0F, 1.0F)
                : phase == LaunchTableBlockEntity.Phase.FLIGHT ? Mth.clamp(1.0F - table.ascent(partialTicks) / 110.0F, 0.0F, 1.0F) : 0.0F;
        // Sans fusée, les bras au repos montrent les conduites d'un lanceur à kérosène.
        RocketFuel fuel = table.requiredFuel();
        state.lox = fuel.isEmpty() || fuel.oxygen() > 0;
        state.lh2 = fuel.hydrogen() > 0;
        state.rp1 = fuel.isEmpty() || fuel.rp1() > 0;
        // Fusée en vol : les blocs ont quitté la table, c'est ce rendu qui la montre s'élever.
        state.flyingRocket = null;
        // Une fusée habitée est dessinée par sa capsule, qui l'accompagne où qu'elle aille ; la table ne dessine que
        // les fusées sans équipage.
        if (shape != null && table.getPhase() == LaunchTableBlockEntity.Phase.FLIGHT && !table.carriesCabin()) {
            float ascent = table.renderAscent(partialTicks);
            if (ascent < LaunchTableBlockEntity.ASCENT_LIMIT) {
                state.flyingRocket = shape;
                state.ascent = ascent;
                state.attitude.set(table.renderAttitude(partialTicks));
                state.pivot = table.pivotHeight();
                state.pivotBack = table.pivotBack();
                for (int k = 0; k < state.stageAges.length; k++) {
                    state.stageAges[k] = table.stageAge(k, partialTicks);
                }
                state.enginesOut = table.enginesOut();
                state.boosterAge = table.boosterAge(partialTicks);
                Vec3 drift = table.renderDrift(partialTicks);
                state.driftX = (float) drift.x;
                state.driftZ = (float) drift.z;
            }
        }

        // Tablier assemblé : les dalles se sont effacées, la table dessine le pas de tir d'un seul tenant.
        state.deckVisible = table.isOperational();
        state.radius = complex.radius();
        state.trench = table.getTrenchDirection();
        System.arraycopy(table.getMastHeights(), 0, state.mastHeights, 0, 4);
        state.deckLight = table.getLevel() == null ? LightCoordsUtil.FULL_BRIGHT
                : LightCoordsUtil.getLightCoords(table.getLevel(), origin.above());

        state.towerVisible = table.isStructureManaged() && tower != null && complex.towerHeight() > 0;
        state.structureLight = table.getLevel() == null ? LightCoordsUtil.FULL_BRIGHT
                : LightCoordsUtil.getLightCoords(table.getLevel(), origin.above(2));
        if (!state.towerVisible) {
            return;
        }
        state.towerX = tower.getX() - origin.getX();
        state.towerY = tower.getY() - origin.getY();
        state.towerZ = tower.getZ() - origin.getZ();
        state.towerHeight = complex.towerHeight();
        // Les bras et les passerelles prennent la lumière de l'air libre où ils se trouvent, devant la tour : relevée
        // dans un bloc de la structure, elle était nulle, et ils paraissaient sombres et bleutés.
        BlockPos open = complex.armPositions().isEmpty() ? tower.above(state.towerHeight + 1) : complex.armPositions().get(0).above();
        state.structureLight = LightCoordsUtil.getLightCoords(table.getLevel(), open);

        // La tour est sur le pourtour du tablier : la fusée est du côté de la table, selon l'axe le plus éloigné.
        boolean alongX = Math.abs(state.towerX) >= Math.abs(state.towerZ);
        state.toRocket = alongX ? (state.towerX > 0 ? Direction.WEST : Direction.EAST)
                : (state.towerZ > 0 ? Direction.NORTH : Direction.SOUTH);
        float distance = Math.max(Math.abs(state.towerX), Math.abs(state.towerZ));

        // Bras : un par bloc de bras ombilical posé contre la tour, à la hauteur de ce bloc. La table place celui de
        // son plan à la hauteur des réservoirs de la fusée. Chacun s'arrête au flanc de ce qui lui fait face ; sans
        // fusée, il est au repos, sans flexibles.
        RocketShape expected = table.expectedShape();
        state.armHeights.clear();
        state.armGaps.clear();
        state.gap = Math.max(0.7F, distance - 1.0F);
        state.idleArms = shape == null;
        for (BlockPos arm : complex.armPositions()) {
            state.armHeights.add((float) (arm.getY() - tower.getY()));
            float hull = expected == null ? 0.5F : Math.max(0.5F, hullHalfWidth(expected, arm.getY() - origin.getY() - 1.0F));
            state.armGaps.add(Math.max(MIN_ARM_LENGTH, distance - 0.5F - hull));
        }
        // Passerelles d'embarquement : une par portique, jusqu'au flanc de la capsule si la fusée en porte une.
        state.gangways.clear();
        for (int gate : table.getGateLevels()) {
            state.gangways.add((float) (gate - tower.getY()));
        }
        // La passerelle s'arrête au flanc de ce qui lui fait face : la capsule, plus étroite qu'un lanceur large, ou
        // le corps de la fusée si le portique n'est pas à la hauteur de la cabine.
        float flank = 0.5F;
        if (expected != null && table.getPhase() != LaunchTableBlockEntity.Phase.FLIGHT && table.getGateLevels().length > 0) {
            flank = Math.max(0.5F, hullHalfWidth(expected, table.getGateLevels()[0] - origin.getY() - 1.0F));
        }
        state.gangwayReach = Math.max(MIN_ARM_LENGTH, distance - 0.5F - flank);
        state.clearance = table.getClearance(partialTicks);
    }

    /**
     * Demi-largeur de la fusée à une hauteur donnée, en blocs : celle du plus large des tronçons que croise un bras
     * ou une passerelle posés là. Un lanceur de trois blocs de large a un flanc à un bloc et demi de son axe, sauf
     * à hauteur de sa cabine, plus étroite ; au-dessus de la fusée, il n'y a rien.
     *
     * @param height hauteur au-dessus de la base de la fusée, en blocs
     */
    private static float hullHalfWidth(RocketShape shape, float height) {
        float widest = 0.0F;
        for (int index = Mth.floor(height); index <= Mth.floor(height + 0.95F); index++) {
            if (index < 0 || index >= shape.height()) {
                continue;
            }
            RocketBlock.Part part = shape.parts().get(index);
            widest = Math.max(widest, part.isCrewModule() ? CAPSULE_HALF_WIDTH : shape.size().halfWidth() / 16.0F);
        }
        return widest;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.flyingRocket != null) {
            submitRocket(state, poseStack, collector, camera.orientation);
        }
        submitGhosts(state, poseStack, collector);
        if (state.deckVisible) {
            submitDeck(state, poseStack, collector);
            submitPlatform(state, poseStack, collector);
            submitMasts(state, poseStack, collector, camera);
        }
        if (state.towerVisible) {
            submitArms(state, poseStack, collector);
        }
    }

    /** Ce que la tour porte et qui s'anime : ses bras ombilicaux et ses passerelles d'embarquement. */
    private static void submitArms(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        int light = glowing(state.structureLight, state.glow);
        poseStack.pushPose();
        // On se place au pied de la colonne technique, en son centre, l'axe x tourné vers la fusée.
        poseStack.translate(state.towerX + 0.5F, state.towerY, state.towerZ + 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(angleTo(state.toRocket)));
        int count = state.armHeights.size();
        for (int index = 0; index < count; index++) {
            submitArm(state, index, count, poseStack, collector, light);
        }
        for (float height : state.gangways) {
            submitGangway(state, height, poseStack, collector, light);
        }
        poseStack.popPose();
    }

    /** Travées de poutre et de passerelle, de la plus longue à la plus courte, et longueurs de flexibles. */
    private static final StructurePartBlock.Part[] ARM_SPANS = {StructurePartBlock.Part.ARM_SPAN_16, StructurePartBlock.Part.ARM_SPAN_8,
            StructurePartBlock.Part.ARM_SPAN_4, StructurePartBlock.Part.ARM_SPAN_2, StructurePartBlock.Part.ARM_SPAN_1};
    private static final StructurePartBlock.Part[] GANGWAY_SPANS = {StructurePartBlock.Part.GANGWAY_SPAN_16,
            StructurePartBlock.Part.GANGWAY_SPAN_8, StructurePartBlock.Part.GANGWAY_SPAN_4, StructurePartBlock.Part.GANGWAY_SPAN_2,
            StructurePartBlock.Part.GANGWAY_SPAN_1};
    private static final StructurePartBlock.Part[] HOSES = {null, null, StructurePartBlock.Part.ARM_HOSE_4,
            StructurePartBlock.Part.ARM_HOSE_2, StructurePartBlock.Part.ARM_HOSE_1};

    /**
     * Dessine une longueur de poutre, de passerelle ou de flexibles, en pixels, en mettant bout à bout des travées
     * toutes faites de 16, 8, 4, 2 et 1 pixels. Rien n'est étiré : les textures gardent leurs pixels carrés, que la
     * pièce soit longue, courte ou à demi rentrée dans la tour. Avec une pièce de rythme (cadre d'un bras, potelet
     * d'une passerelle), elle est posée au départ et tous les blocs, comptés depuis le bout.
     *
     * @param spans  travées de 16, 8, 4, 2 et 1 pixels ; {@code null} pour une longueur qui n'existe pas
     * @param rhythm pièce de rythme, ou {@code null}
     */
    private static void submitRun(PoseStack poseStack, SubmitNodeCollector collector, int light, int pixels,
            StructurePartBlock.Part[] spans, StructurePartBlock.Part rhythm) {
        // Les travées les plus courtes d'abord : celles de 16 pixels finissent ainsi au bout, alignées sur le rythme.
        int x = 0;
        for (int k = spans.length - 1; k >= 1; k--) {
            int size = 16 >> k;
            if (spans[k] != null && ((pixels % 16) & size) != 0) {
                x = submitSpan(poseStack, collector, light, spans[k], x, size);
            }
        }
        while (spans[0] != null && pixels - x >= 16) {
            x = submitSpan(poseStack, collector, light, spans[0], x, 16);
        }
        if (rhythm != null) {
            submitSpan(poseStack, collector, light, rhythm, 0, 0);
            // Le premier rythme après le départ n'est posé que s'il en est assez loin.
            for (int at = pixels % 16 >= 4 ? pixels % 16 : pixels % 16 + 16; at < pixels; at += 16) {
                submitSpan(poseStack, collector, light, rhythm, at, 0);
            }
        }
    }

    /** Dessine une pièce à une distance donnée du départ, en pixels, et renvoie où commence la suivante. */
    private static int submitSpan(PoseStack poseStack, SubmitNodeCollector collector, int light, StructurePartBlock.Part part, int at, int size) {
        poseStack.pushPose();
        poseStack.translate(at / 16.0F, 0.0F, 0.0F);
        submitPart(part, poseStack, collector, light);
        poseStack.popPose();
        return at + size;
    }

    /**
     * Un bras ombilical : une poutre droite qui sort de la face de la colonne technique, dans son axe, vers la
     * fusée. Elle porte les conduites des ergols et se termine par des flexibles et une plaque de raccordement. Pour
     * dégager la fusée, les flexibles se débranchent, puis la poutre rentre dans la tour, pixel après pixel.
     */
    private static void submitArm(State state, int index, int count, PoseStack poseStack, SubmitNodeCollector collector, int light) {
        // Les bras se libèrent l'un après l'autre, de bas en haut : chacun a sa fenêtre dans le dégagement.
        float start = 0.35F * index / Math.max(1, count);
        float hose = ease((state.clearance - start) / 0.12F);
        float retract = ease((state.clearance - start - 0.1F) / 0.42F);
        float gap = index < state.armGaps.size() ? state.armGaps.get(index) : state.gap;

        poseStack.pushPose();
        poseStack.translate(0.5F + FACE_GAP, state.armHeights.get(index), -0.5F);
        // Du bout vers la tour : la plaque contre le flanc de la fusée, les flexibles, puis la poutre. Tout est
        // compté en pixels entiers.
        int reach = Math.max(4, Math.round((gap - FACE_GAP - PLATE_THICKNESS) * 16.0F));
        int hoseFull = state.idleArms ? 0 : Math.min(6, reach / 4);
        int truss = reach - hoseFull;
        int visible = Math.max(2, Math.round(truss * (1.0F - ARM_RETRACT * retract)));
        int flexible = Math.round(hoseFull * (1.0F - hose));
        submitRun(poseStack, collector, light, visible, ARM_SPANS, StructurePartBlock.Part.ARM_RIB);
        poseStack.translate(visible / 16.0F, 0.0F, 0.0F);
        if (!state.far) {
            submitRun(poseStack, collector, light, flexible, HOSES, null);
        }
        poseStack.translate(flexible / 16.0F, 0.0F, 0.0F);
        submitPart(StructurePartBlock.Part.ARM_PLATE, poseStack, collector, light);
        poseStack.popPose();
    }

    /**
     * Une passerelle d'embarquement : elle sort du portique, dans son axe, et s'étend jusqu'à la capsule. Comme un
     * bras, elle rentre dans la tour dès le début du dégagement. Son plancher est rendu solide par la table de
     * lancement tant qu'elle est déployée.
     *
     * @param height hauteur du seuil au-dessus du pied de la tour, en blocs
     */
    private static void submitGangway(State state, float height, PoseStack poseStack, SubmitNodeCollector collector, int light) {
        float retract = ease(state.clearance / 0.45F);
        poseStack.pushPose();
        poseStack.translate(0.5F + FACE_GAP, height + GANGWAY_FLOOR, -0.5F);
        int reach = Math.max(4, Math.round((state.gangwayReach - FACE_GAP) * 16.0F));
        int visible = Math.max(3, Math.round(reach * (1.0F - GANGWAY_RETRACT * retract)));
        submitRun(poseStack, collector, light, visible, GANGWAY_SPANS, StructurePartBlock.Part.GANGWAY_POST);
        poseStack.translate(visible / 16.0F - GANGWAY_SILL, 0.0F, 0.0F);
        submitPart(StructurePartBlock.Part.GANGWAY_END, poseStack, collector, light);
        poseStack.popPose();
    }

    /**
     * Plans du pas de tir et de la fusée : à l'emplacement de chaque bloc qui manque, ce bloc en vraie grandeur, à
     * demi transparent (voir {@link GhostBlocks}). Il disparaît quand le bloc est posé.
     */
    private static void submitGhosts(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        GhostBlocks.submit(state.ghostOffsets, state.ghostStates, poseStack, collector);
    }

    /** Le carneau du tablier assemblé, en relief, posé dans le sens d'évacuation des gaz. */
    private static void submitPlatform(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        int radius = Math.clamp(state.radius, 1, 5);
        int light = glowing(state.deckLight, state.glow);
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(angleTo(state.trench)));
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        for (int k = 1; k <= radius; k++) {
            submitAt(StructurePartBlock.Part.FLAME_TRENCH, k, 0, poseStack, collector, light);
        }
        poseStack.popPose();
    }

    private static void submitAt(StructurePartBlock.Part part, int x, int z, PoseStack poseStack, SubmitNodeCollector collector, int light) {
        poseStack.pushPose();
        poseStack.translate(x, 0.0F, z);
        submitPart(part, poseStack, collector, light);
        poseStack.popPose();
    }

    /**
     * Éclairage d'une pièce quand les moteurs sont allumés : leur lueur s'ajoute à la lumière du lieu. Le jeu
     * n'a pas de lumière mobile ; c'est donc la structure elle-même qu'on éclaire, le temps de l'allumage.
     */
    private static int glowing(int light, float glow) {
        if (glow <= 0.0F) {
            return light;
        }
        int block = Math.max(LightCoordsUtil.block(light), Math.round(15.0F * glow));
        return LightCoordsUtil.pack(block, LightCoordsUtil.sky(light));
    }

    /** Dessine une balise allumée sur la pointe en cours : de la taille de la balise éteinte, qu'elle recouvre tout juste. */
    private static void submitBeacon(PoseStack poseStack, SubmitNodeCollector collector) {
        submitPart(StructurePartBlock.Part.BEACON, poseStack, collector, LightCoordsUtil.FULL_BRIGHT);
    }

    /** La fusée en vol : la colonne de ses tronçons, élevée au-dessus de la table. */
    private static void submitRocket(State state, PoseStack poseStack, SubmitNodeCollector collector, org.joml.Quaternionf view) {
        RocketShape shape = state.flyingRocket;
        poseStack.pushPose();
        // La fusée tourne sur ses trois axes autour de la tête de son pilote, qui ne quitte pas son siège ; sans
        // équipage, autour de sa mi-hauteur.
        poseStack.translate(state.driftX, 0.0F, state.driftZ);
        float py = 1.0F + state.ascent + state.pivot;
        poseStack.translate(0.5F, py, 0.5F + state.pivotBack);
        poseStack.mulPose(state.attitude);
        poseStack.translate(-0.5F, -py, -0.5F - state.pivotBack);
        // Les tronçons, étage par étage : un étage largué se détache et s'éloigne, comme sur une fusée habitée.
        poseStack.translate(0.0F, 1.0F + state.ascent, 0.0F);
        CapsuleRenderer.submitStages(poseStack, collector, shape.parts(), shape.size(), 0, state.stageAges, (float) (state.time % 100_000.0), view,
                !state.enginesOut);
        if (shape.boosters() > 0) {
            CapsuleRenderer.submitBoosters(poseStack, collector, 0, state.trench.getClockWise(), shape, state.boosterAge,
                    (float) (state.time % 100_000.0), view, !state.enginesOut);
        }
        poseStack.popPose();
    }

    static void submitPart(StructurePartBlock.Part part, PoseStack poseStack, SubmitNodeCollector collector, int light) {
        BlockState blockState = PART_STATES.computeIfAbsent(part,
                key -> ModBlocks.STRUCTURE_PART.get().defaultBlockState().setValue(StructurePartBlock.PART, key));
        // Le modèle n'est préparé qu'une fois, puis réutilisé d'une image à l'autre (voir ModelCache).
        ModelCache.get(blockState).submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
    }

    /** Rotation autour de l'axe vertical qui amène l'axe x (celui des modèles de bras) sur une direction. */
    static float angleTo(Direction direction) {
        return switch (direction) {
            case SOUTH -> -90.0F;
            case WEST -> 180.0F;
            case NORTH -> 90.0F;
            default -> 0.0F;
        };
    }

    /**
     * Départ et arrivée très progressifs d'un mouvement : 0 avant, 1 après, et entre les deux une courbe en S
     * dont la vitesse comme l'accélération s'annulent aux deux bouts. Aucun mouvement ne démarre ni ne s'arrête sec.
     */
    private static float ease(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * t * (t * (t * 6.0F - 15.0F) + 10.0F);
    }

    /**
     * Tablier du pas de tir assemblé : un seul pavé, coiffé d'une seule grande image tournée selon le sens du carneau,
     * à jour au-dessus de la table et du carneau (on y voit les pièces en relief), avec ses quatre flancs.
     */
    private static void submitDeck(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        int radius = Math.clamp(state.radius, 1, 5);
        float min = -radius - 0.002F;
        float max = radius + 1.002F;
        float top = 1.004F;
        int light = glowing(state.deckLight, state.glow);
        // Sur l'image, le carneau part vers la droite : on la tourne pour qu'il parte dans le sens construit.
        float ex = state.trench.getStepX();
        float ez = state.trench.getStepZ();

        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(Orbitalis.id("textures/entity/launch_pad_top_" + radius + ".png")),
                (pose, buffer) -> {
                    float[][] corners = {{min, min}, {min, max}, {max, max}, {max, min}};
                    for (float[] corner : corners) {
                        // Position du coin par rapport au centre du tablier, ramenée entre -0,5 et 0,5.
                        float dx = (corner[0] - 0.5F) / (max - min);
                        float dz = (corner[1] - 0.5F) / (max - min);
                        float u = dx * ex + dz * ez + 0.5F;
                        float v = -dx * ez + dz * ex + 0.5F;
                        buffer.addVertex(pose, corner[0], top, corner[1]).setColor(0xFFFFFFFF).setUv(u, v)
                                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, 1.0F, 0.0F);
                    }
                });

        // Dessous du tablier : vu d'en bas, entre ses appuis, il est plein.
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(Orbitalis.id("textures/entity/launch_pad_bottom_" + radius + ".png")),
                (pose, buffer) -> {
                    float[][] corners = {{min, min, 0.0F, 0.0F}, {max, min, 1.0F, 0.0F}, {max, max, 1.0F, 1.0F}, {min, max, 0.0F, 1.0F}};
                    for (float[] corner : corners) {
                        buffer.addVertex(pose, corner[0], -0.002F, corner[1]).setColor(0xFFFFFFFF).setUv(corner[2], corner[3])
                                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, -1.0F, 0.0F);
                    }
                });

        // Quatre flancs : nord, sud, ouest, est. Chacun va du sol (y = 0) au dessus du tablier. Celui où débouche
        // le carneau est percé de sa bouche : il a sa propre image.
        float[][] sides = {
                {max, min, min, min, 0.0F, -1.0F}, {min, max, max, max, 0.0F, 1.0F},
                {min, min, min, max, -1.0F, 0.0F}, {max, max, max, min, 1.0F, 0.0F}};
        for (boolean mouth : new boolean[] {false, true}) {
            String image = mouth ? "launch_pad_mouth_" : "launch_pad_side_";
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(Orbitalis.id("textures/entity/" + image + radius + ".png")),
                    (pose, buffer) -> {
                        for (float[] side : sides) {
                            if ((side[4] == ex && side[5] == ez) != mouth) {
                                continue;
                            }
                            float[][] points = {{side[0], top, side[1], 0.0F, 0.0F}, {side[0], 0.0F, side[1], 0.0F, 1.0F},
                                    {side[2], 0.0F, side[3], 1.0F, 1.0F}, {side[2], top, side[3], 1.0F, 0.0F}};
                            for (float[] point : points) {
                                buffer.addVertex(pose, point[0], point[1], point[2]).setColor(0xFFFFFFFF).setUv(point[3], point[4])
                                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, side[4], 0.0F, side[5]);
                            }
                        }
                    });
        }
    }

    /**
     * Mâts du pas de tir assemblé : un fût élancé sur chaque angle, tourné vers le centre, avec sa rampe de
     * projecteurs sous la tête et sa balise à la pointe.
     */
    private static void submitMasts(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        int radius = state.radius;
        int light = glowing(state.deckLight, state.glow);
        int corner = 0;
        for (int dz = -radius; dz <= radius; dz += Math.max(1, 2 * radius)) {
            for (int dx = -radius; dx <= radius; dx += Math.max(1, 2 * radius)) {
                int height = corner < 4 ? state.mastHeights[corner++] : 0;
                // Les projecteurs regardent vers le nord du modèle : on le tourne vers la table.
                float yaw = (float) Math.atan2(dx, dz);
                // Courant de la LED de la balise, et ce que l'œil en perçoit.
                double beacon = LedDriver.brightness(LedDriver.beaconCurrent(state.time, corner, state.active));
                for (int i = 0; i < height; i++) {
                    poseStack.pushPose();
                    poseStack.translate(dx + 0.5F, 1.0F + i, dz + 0.5F);
                    poseStack.mulPose(Axis.YP.rotation(yaw));
                    poseStack.translate(-0.5F, 0.0F, -0.5F);
                    // Du pied au sommet : l'embase, des tronçons tour à tour rouges et blancs, les projecteurs, la tête.
                    StructurePartBlock.Part part = i == height - 1 ? StructurePartBlock.Part.MAST_TOP
                            : i == 0 ? StructurePartBlock.Part.MAST_BASE
                            : i == height - 2 && height >= 4 ? (state.lampGlow > 0.05F ? StructurePartBlock.Part.MAST_LAMP_ON : StructurePartBlock.Part.MAST_LAMP)
                            : i % 2 == 1 ? StructurePartBlock.Part.MAST_MID_RED : StructurePartBlock.Part.MAST_MID;
                    submitPart(part, poseStack, collector, light);
                    if (i == height - 1 && beacon > 0.2) {
                        submitBeacon(poseStack, collector);
                    }
                    poseStack.popPose();
                }
                if (height < 2) {
                    continue;
                }
                // Halo de la balise : elle rayonne tout autour d'elle, un peu moins vers le haut et vers le bas.
                submitHalo(state, poseStack, collector, camera, dx + 0.5, height + 1.95, dz + 0.5, null, false, 0.12, beacon, 1.0F, 0.16F, 0.1F);
                // Halo des projecteurs : leurs LED éclairent devant elles, vers la table et un peu vers le bas. Elles
                // scintillent à peine, comme toute LED alimentée en courant haché.
                if (height >= 4 && state.lampGlow > 0.01F) {
                    double length = Math.sqrt(dx * dx + dz * dz);
                    Vec3 axis = new Vec3(-dx / length, -0.45, -dz / length).normalize();
                    double shimmer = 1.0 - 0.03 * (1.0 + Math.sin(state.time * 2.7 + corner * 1.9));
                    submitHalo(state, poseStack, collector, camera, dx + 0.5 + axis.x * 0.62, height - 0.31, dz + 0.5 + axis.z * 0.62,
                            axis, true, 0.3, state.lampGlow * shimmer, 0.86F, 0.93F, 1.0F);
                }
            }
        }
    }

    /**
     * Halo d'une LED : un disque de lumière toujours tourné vers l'observateur. Sa taille et sa force viennent du
     * modèle de LED ({@link LedDriver}) : l'éclat de la LED, la direction d'où on la regarde, la distance, la nuit.
     *
     * @param axis   direction dans laquelle la LED éclaire, ou {@code null} si elle rayonne tout autour d'elle
     * @param narrow vrai pour un projecteur, au faisceau serré
     * @param size   rayon de la LED, en blocs
     * @param seen   éclat de la LED, de 0 à 1
     */
    private static void submitHalo(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera,
            double x, double y, double z, Vec3 axis, boolean narrow, double size, double seen, float red, float green, float blue) {
        Vec3 toCamera = camera.pos.subtract(state.blockPos.getX() + x, state.blockPos.getY() + y, state.blockPos.getZ() + z);
        double distance = toCamera.length();
        if (seen <= 0.02 || distance < 0.05) {
            return;
        }
        Vec3 view = toCamera.scale(1.0 / distance);
        // Une balise sans axe rayonne à l'horizontale : elle faiblit quand on la regarde d'en haut ou d'en bas.
        double cosine = axis == null ? Math.sqrt(Math.max(0.0, 1.0 - view.y * view.y)) : axis.dot(view);
        double emitted = seen * LedDriver.emission(cosine, narrow);
        float radius = (float) LedDriver.haloRadius(size, emitted, distance, state.dark);
        int alpha = (int) (255 * Mth.clamp(LedDriver.haloStrength(emitted, state.dark), 0.0, 1.0));
        if (alpha < 4) {
            return;
        }
        int color = (alpha << 24) | ((int) (red * 255) << 16) | ((int) (green * 255) << 8) | (int) (blue * 255);
        // Le halo est avancé d'un rien vers l'observateur : la LED elle-même ne le coupe pas en deux.
        double forward = Math.min(0.35, distance * 0.5);
        poseStack.pushPose();
        poseStack.translate(x + view.x * forward, y + view.y * forward, z + view.z * forward);
        poseStack.mulPose(camera.orientation);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(LED_GLOW), (pose, buffer) -> {
            // Les deux faces sont dessinées : le halo se voit quel que soit le sens dans lequel le jeu trie ses faces.
            float[][] corners = {{-1, -1, 0, 1}, {-1, 1, 0, 0}, {1, 1, 1, 0}, {1, -1, 1, 1}};
            for (int pass = 0; pass < 2; pass++) {
                for (int k = 0; k < 4; k++) {
                    float[] corner = corners[pass == 0 ? k : 3 - k];
                    buffer.addVertex(pose, corner[0] * radius, corner[1] * radius, 0.0F).setColor(color).setUv(corner[2], corner[3])
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0.0F, 0.0F, 1.0F);
                }
            }
        });
        poseStack.popPose();
    }

    /** La structure s'étend bien au-delà du bloc de la table : elle est dessinée même si la table est hors champ. */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 384;
    }

    @Override
    public AABB getRenderBoundingBox(LaunchTableBlockEntity table) {
        return AABB.INFINITE;
    }
}
