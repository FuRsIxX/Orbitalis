package com.orbitalis.client.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.registry.ModFluids;
import com.orbitalis.phase1.rocket.RocketStats;
import com.orbitalis.phase1.satellite.SatelliteType;
import com.orbitalis.phase1.satellite.Orbit;
import com.orbitalis.phase1.spaceport.FlightProfile;
import com.orbitalis.phase1.spaceport.LaunchComplex;
import com.orbitalis.phase1.spaceport.LaunchTableBlockEntity;
import com.orbitalis.phase1.spaceport.MissionDisplayBlock;
import com.orbitalis.phase1.spaceport.MissionDisplayBlockEntity;
import com.orbitalis.phase1.spaceport.RocketBlock;
import com.orbitalis.phase1.spaceport.RocketShape;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Dessine l'image d'un écran de contrôle, sur toutes ses dalles à la fois.
 * <p>
 * Ce que l'écran montre dépend de ses sources (pupitre de lancement, station de poursuite, réservoirs) et de
 * l'affichage choisi ; la composition s'adapte à sa taille, d'une seule dalle à un mur entier.
 * <p>
 * L'image est composée comme sur un écran de salle de contrôle : un bandeau avec l'horloge de mission,
 * la carte du terrain autour du pas de tir, la fusée en volume qui tourne sur elle-même, ses caractéristiques,
 * et en bas la trajectoire tracée en temps réel avec la télémétrie. Elle suit le lancement en cours sur la table
 * de lancement la plus proche.
 * <p>
 * Tout est exprimé dans le repère de l'écran : x vers la droite, y vers le bas, en « points » dont
 * {@link #POINTS_PER_BLOCK} font un bloc. Seul l'écran du coin en haut à gauche du mur dessine.
 */
public class MissionDisplayRenderer implements BlockEntityRenderer<MissionDisplayBlockEntity, MissionDisplayRenderer.State> {
    /** Finesse de l'image : nombre de points par bloc. Un caractère fait 8 points de haut. */
    private static final float POINTS_PER_BLOCK = 40.0F;
    /** Distance entre le dos de la dalle et le plan de l'image, en blocs : juste devant sa vitre. */
    private static final float SURFACE = 3.1F / 16.0F;

    private static final int COLOR_BACKGROUND = 0xFF0B0E12;
    private static final int COLOR_PANEL = 0xFF131820;
    private static final int COLOR_EDGE = 0xFF2B3440;
    private static final int COLOR_ACCENT = 0xFF4F86D6;
    private static final int COLOR_TEXT = 0xFFE6E9EE;
    private static final int COLOR_MUTED = 0xFF8D97A3;
    private static final int COLOR_OK = 0xFF5BC27A;
    private static final int COLOR_BAD = 0xFFE0655F;
    private static final int COLOR_WARNING = 0xFFFFC857;
    private static final int COLOR_UNKNOWN = 0xFF1A1F26;

    /** Rectangle plein. La profondeur {@code layer} range les rectangles les uns au-dessus des autres. */
    private record Rect(float x0, float y0, float x1, float y1, int color, int layer) {
    }

    private record Label(float x, float y, FormattedCharSequence text, int color, float scale) {
    }

    /** Image à dessiner, préparée à chaque trame. */
    public static class State extends BlockEntityRenderState {
        boolean visible;
        float yRot;
        final List<Rect> rects = new ArrayList<>();
        final List<Label> labels = new ArrayList<>();
        /** Fusée en volume : sa silhouette, la position du bas de son axe, son échelle (points par bloc) et son angle. */
        /** Carte de navigation : les rectangles de sa prise de vue, et où les poser (coin, et taille d'une unité de vue). */
        List<IsoMap.Prim> mapPrims = List.of();
        float mapX0;
        float mapY0;
        float mapScale = 1.0F;
        RocketShape rocket;
        float rocketX;
        float rocketY;
        float rocketScale;
        float rocketAngle;
    }

    private final Font font;
    /** Fraction de tick de l'image en cours de composition. */
    private float partial;

    public MissionDisplayRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.font();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    // ------------------------------------------------------------------
    // Composition de l'image
    // ------------------------------------------------------------------

    @Override
    public void extractRenderState(MissionDisplayBlockEntity display, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(display, state, partialTicks, cameraPosition, breakProgress);
        state.rects.clear();
        state.labels.clear();
        state.rocket = null;
        state.mapPrims = List.of();
        partial = partialTicks;
        state.visible = display.drawsWall() && display.getLevel() != null;
        if (!state.visible) {
            return;
        }
        state.yRot = display.getBlockState().getValue(MissionDisplayBlock.FACING).toYRot();
        float width = display.wallWidth() * POINTS_PER_BLOCK;
        float height = display.wallHeight() * POINTS_PER_BLOCK;
        float time = display.getLevel().getGameTime() + partialTicks;
        compose(state, display, width, height, time, partialTicks);
    }

    /**
     * Compose l'image selon la place disponible. Le bandeau et l'horloge sont toujours là ; la fusée et ses
     * caractéristiques aussi. La carte n'apparaît que sur un mur assez large, la trajectoire que sur un mur assez
     * haut ; sur un petit mur, l'altitude et la vitesse prennent place parmi les caractéristiques pendant le vol.
     * Chaque texte est coupé à la largeur de sa zone et chaque ligne n'est écrite que si elle tient : rien ne se chevauche.
     */
    private void compose(State state, MissionDisplayBlockEntity display, float width, float height, float time, float partialTicks) {
        rect(state, 0, 0, width, height, COLOR_BACKGROUND, 0);
        MissionDisplayBlockEntity.Page page = display.page();
        if (page == MissionDisplayBlockEntity.Page.SATELLITES) {
            composeSatellites(state, display.satellites(), width, height, time);
            return;
        }
        if (page == MissionDisplayBlockEntity.Page.TANKS) {
            composeTanks(state, display.tanks(), width, height);
            return;
        }
        LaunchTableBlockEntity table = display.linkedTable();
        if (table == null) {
            if (isStandby(display)) {
                composeStandby(state, display, width, height, time);
            } else {
                composeUnlinked(state, display.hasLink(), width, height);
            }
            return;
        }
        // La carte de navigation prend tout l'écran, quelle que soit sa taille.
        if (page == MissionDisplayBlockEntity.Page.MAP) {
            composeMapScreen(state, table, width, height);
            return;
        }
        // Un petit écran (moins de trois dalles de large ou de deux de haut) : l'horloge, la phase, et ce qui tient.
        if (width < 110 || height < 70) {
            composeCompact(state, table, width, height, partialTicks);
            return;
        }

        LaunchTableBlockEntity.Phase phase = table.getPhase();
        boolean hasRocket = table.hasRocket();
        RocketStats stats = hasRocket ? table.rocketStats() : null;
        float phaseTicks = table.phaseTicks() + partialTicks;
        float progress = phase == LaunchTableBlockEntity.Phase.FLIGHT
                ? Mth.clamp(phaseTicks / table.flightTicks(), 0.0F, 1.0F) : 0.0F;

        // Bandeau : l'horloge de mission au centre ; le titre à gauche et la phase à droite s'il reste de la place.
        float bar = 15;
        rect(state, 0, 0, width, bar, COLOR_PANEL, 1);
        rect(state, 0, bar, width, bar + 1, COLOR_EDGE, 1);
        int phaseColor = switch (phase) {
            case COUNTDOWN -> COLOR_WARNING;
            case FLIGHT -> COLOR_OK;
            default -> COLOR_MUTED;
        };
        String clock = switch (phase) {
            case COUNTDOWN -> "T-" + clock(Math.max(0, (LaunchTableBlockEntity.COUNTDOWN_TICKS - phaseTicks) / 20.0F));
            case FLIGHT -> "T+" + clock(stats == null ? phaseTicks / 20.0F : (float) FlightProfile.missionSeconds(stats, progress));
            default -> "T-00:00";
        };
        float clockScale = width >= 160 ? 1.5F : 1.2F;
        float clockWidth = font.width(clock) * clockScale;
        float clockX = (width - clockWidth) / 2.0F;
        label(state, clockX, (bar - 8 * clockScale) / 2.0F + 0.5F, clock, phaseColor, clockScale);
        // Le titre complet, ou sa version courte, ou rien : jamais un mot coupé. Hors de la vue d'ensemble,
        // c'est le nom de l'affichage qui tient lieu de titre.
        if (page != MissionDisplayBlockEntity.Page.OVERVIEW) {
            whole(state, 4, 4, translate(page.nameKey()), clockX - 10, COLOR_ACCENT, false);
        } else if (!whole(state, 4, 4, translate("spaceport.orbitalis.console.title"), clockX - 10, COLOR_ACCENT, false)) {
            whole(state, 4, 4, translate("launch.orbitalis.console.title_short"), clockX - 10, COLOR_ACCENT, false);
        }
        String phaseText = switch (phase) {
            case COUNTDOWN -> {
                float seconds = (LaunchTableBlockEntity.COUNTDOWN_TICKS - phaseTicks) / 20.0F;
                yield translate("launch.orbitalis.console.stage." + (seconds > 20 ? "systems" : seconds > 10 ? "poll" : seconds > 3 ? "arms" : "ignition"));
            }
            case FLIGHT -> translate("launch.orbitalis.console.in_flight");
            default -> translate(table.isGoForLaunch() ? "launch.orbitalis.console.go" : "launch.orbitalis.console.standby");
        };
        whole(state, width - 4, 4, phaseText, width - 4 - (clockX + clockWidth) - 8, phaseColor, true);

        // Découpage du reste de l'image.
        float top = bar + 4;
        float bottom = height - 3;
        if (page != MissionDisplayBlockEntity.Page.OVERVIEW) {
            composePage(state, page, table, stats, phase, progress, time, top, bottom, width);
            return;
        }
        boolean hasPlot = height >= 118;
        float plotHeight = hasPlot ? Mth.clamp(height * 0.26F, 30.0F, 48.0F) : 0.0F;
        float bodyBottom = hasPlot ? bottom - plotHeight - 3 : bottom;
        float bodyHeight = bodyBottom - top;
        float rocketWidth = Mth.clamp(width * 0.15F, 20.0F, 46.0F);
        float mapSize = Math.min((bodyHeight - 19) * 1.5F, width * 0.42F);
        boolean hasMap = width >= 190 && mapSize >= 56;

        float x = 3;
        if (hasMap) {
            composeMap(state, table, stats, progress, x, top, mapSize, bodyHeight);
            x += mapSize + 4;
        }

        // Fusée en volume, dans sa colonne.
        panel(state, x, top, rocketWidth, bodyHeight);
        if (hasRocket) {
            RocketShape shape = table.rocketShape();
            state.rocket = shape;
            float available = bodyHeight - 6;
            state.rocketScale = Math.min(available / shape.height(), (rocketWidth - 4) / (shape.isWide() ? 3.0F : 1.2F));
            state.rocketX = x + rocketWidth / 2.0F;
            state.rocketY = bodyBottom - 3 - (available - shape.height() * state.rocketScale) / 2.0F;
            state.rocketAngle = time * 2.0F;
        }
        x += rocketWidth + 4;

        composeStats(state, table, stats, phase, progress, !hasPlot, x, top, width - 3 - x, bodyHeight);
        if (hasPlot) {
            composeTrajectory(state, stats, phase, progress, 3, bodyBottom + 3, width - 6, plotHeight);
        }
    }

    /**
     * Affichage d'un petit écran relié à un pas de tir : l'horloge de mission, aussi grande que l'écran le permet,
     * la phase en dessous, puis, sur un écran de deux dalles de haut, l'état de la fusée et du pas de tir.
     */
    private void composeCompact(State state, LaunchTableBlockEntity table, float width, float height, float partialTicks) {
        LaunchTableBlockEntity.Phase phase = table.getPhase();
        RocketStats stats = table.hasRocket() ? table.rocketStats() : null;
        float phaseTicks = table.phaseTicks() + partialTicks;
        float progress = phase == LaunchTableBlockEntity.Phase.FLIGHT
                ? Mth.clamp(phaseTicks / table.flightTicks(), 0.0F, 1.0F) : 0.0F;
        int color = switch (phase) {
            case COUNTDOWN -> COLOR_WARNING;
            case FLIGHT -> COLOR_OK;
            default -> COLOR_MUTED;
        };
        String clock = switch (phase) {
            case COUNTDOWN -> "T-" + clock(Math.max(0, (LaunchTableBlockEntity.COUNTDOWN_TICKS - phaseTicks) / 20.0F));
            case FLIGHT -> "T+" + clock(stats == null ? phaseTicks / 20.0F : (float) FlightProfile.missionSeconds(stats, progress));
            default -> "T-00:00";
        };
        String status = switch (phase) {
            case COUNTDOWN -> translate("launch.orbitalis.console.stage.systems");
            case FLIGHT -> translate("launch.orbitalis.console.in_flight");
            default -> translate(table.isGoForLaunch() ? "launch.orbitalis.console.go" : "launch.orbitalis.console.standby");
        };
        boolean tall = height >= 70;
        float scale = Math.min(tall ? 2.0F : 1.6F, (width - 6) / font.width(clock));
        boolean hasStatus = font.width(status) <= width - 6;
        // L'état de la fusée ne s'ajoute que sur un écran assez large pour l'écrire sans couper un mot.
        boolean hasStats = tall && width >= 70;
        float block = 8 * scale + (hasStatus ? 11 : 6);
        float y = hasStats ? 6 : Math.max(2, (height - block) / 2.0F);
        label(state, (width - font.width(clock) * scale) / 2.0F, y, clock, color, scale);
        int statusColor = phase == LaunchTableBlockEntity.Phase.IDLE ? (table.isGoForLaunch() ? COLOR_OK : COLOR_MUTED) : color;
        if (hasStatus) {
            label(state, (width - font.width(status)) / 2.0F, y + 8 * scale + 3, status, statusColor, 1.0F);
        } else {
            // Pas la place d'écrire la phase : un trait de sa couleur la signale.
            rect(state, width / 2.0F - 7, y + 8 * scale + 3, width / 2.0F + 7, y + 8 * scale + 5, statusColor, 1);
        }
        float top = y + block + 5;
        if (hasStats && height - 3 - top >= 24) {
            composeStats(state, table, stats, phase, progress, true, 3, top, width - 6, height - 3 - top);
        }
    }

    /**
     * Niveaux des réservoirs reliés à l'écran : pour chacun, ce qu'il contient, son taux de remplissage, et une
     * jauge à la couleur du fluide. La liste s'arrête dès que la place manque.
     */
    private void composeTanks(State state, int[] tanks, float width, float height) {
        int fields = MissionDisplayBlockEntity.TANK_FIELDS;
        int count = tanks.length / fields;
        float top = 3;
        if (height >= 60) {
            float bar = 15;
            rect(state, 0, 0, width, bar, COLOR_PANEL, 1);
            rect(state, 0, bar, width, bar + 1, COLOR_EDGE, 1);
            String title = translate(MissionDisplayBlockEntity.Page.TANKS.nameKey());
            if (whole(state, 4, 4, title, width - 8, COLOR_ACCENT, false)) {
                whole(state, width - 4, 4, Component.translatable("display.orbitalis.tanks.count", count).getString(),
                        width - 14 - font.width(title), COLOR_MUTED, true);
            }
            top = bar + 4;
        }
        // Les réservoirs se rangent sur une colonne ; sur deux, si l'écran est large et qu'une seule ne suffit pas.
        float rowHeight = 20;
        int fitting = Math.max(1, (int) ((height - 2 - top - 16) / rowHeight) + 1);
        int columns = count > fitting && width >= 150 ? 2 : 1;
        float columnWidth = (width - 6 - (columns - 1) * 6) / columns;
        // Une seule dalle : la ligne unique se place au milieu.
        if (count * rowHeight < height - top && height < 60) {
            top = Math.max(top, (height - 16) / 2.0F);
        }
        for (int i = 0; i < count; i++) {
            float x = 3 + (i % columns) * (columnWidth + 6);
            float y = top + (i / columns) * rowHeight;
            if (y + 16 > height - 2) {
                break;
            }
            int id = tanks[i * fields];
            int amount = tanks[i * fields + 1];
            int capacity = Math.max(1, tanks[i * fields + 2]);
            Fluid fluid = id < 0 ? Fluids.EMPTY : BuiltInRegistries.FLUID.byId(id);
            boolean empty = fluid == Fluids.EMPTY || amount <= 0;
            String name = empty ? translate("gui.orbitalis.fluid.empty") : fluid.getFluidType().getDescription().getString();
            float fill = Mth.clamp(amount / (float) capacity, 0.0F, 1.0F);
            // La quantité en clair si elle tient à côté du nom, sinon le seul pourcentage.
            String percent = Math.round(fill * 100) + " %";
            String full = grouped(amount) + " / " + grouped(capacity) + " mB";
            String value = font.width(full) + font.width(name) + 12 <= columnWidth ? full : percent;
            float valueWidth = font.width(value);
            if (valueWidth <= columnWidth - 2) {
                // Le nom en entier, ou pas du tout : la couleur de la jauge suffit alors à reconnaître le fluide.
                boolean named = whole(state, x, y, name, columnWidth - valueWidth - 6, COLOR_MUTED, false);
                label(state, named ? x + columnWidth - valueWidth : x + (columnWidth - valueWidth) / 2.0F, y, value,
                        empty ? COLOR_MUTED : COLOR_TEXT, 1.0F);
            }
            rect(state, x, y + 10, x + columnWidth, y + 16, COLOR_UNKNOWN, 1);
            if (!empty) {
                rect(state, x, y + 10, x + Math.max(1, columnWidth * fill), y + 16, 0xFF000000 | ModFluids.color(fluid), 2);
            }
        }
    }

    /**
     * Un affichage en grand : la carte, la fusée, les caractéristiques ou la trajectoire prennent tout le mur sous
     * le bandeau. C'est ce qui permet à un petit mur de tout montrer, un affichage après l'autre.
     */
    private void composePage(State state, MissionDisplayBlockEntity.Page page, LaunchTableBlockEntity table, RocketStats stats,
            LaunchTableBlockEntity.Phase phase, float progress, float time, float top, float bottom, float width) {
        float bodyHeight = bottom - top;
        switch (page) {
            case ROCKET -> {
                float rocketWidth = Math.min(width * 0.45F, bodyHeight * 0.7F);
                panel(state, 3, top, rocketWidth, bodyHeight);
                if (table.hasRocket()) {
                    RocketShape shape = table.rocketShape();
                    state.rocket = shape;
                    float available = bodyHeight - 6;
                    state.rocketScale = Math.min(available / shape.height(), (rocketWidth - 4) / (shape.isWide() ? 3.0F : 1.2F));
                    state.rocketX = 3 + rocketWidth / 2.0F;
                    state.rocketY = bottom - 3 - (available - shape.height() * state.rocketScale) / 2.0F;
                    state.rocketAngle = time * 2.0F;
                }
                float x = 3 + rocketWidth + 4;
                composeStats(state, table, stats, phase, progress, true, x, top, width - 3 - x, bodyHeight);
            }
            case TELEMETRY -> composeTelemetry(state, table, stats, phase, progress, 3, top, width - 6, bodyHeight);
            case TRAJECTORY -> composeTrajectory(state, stats, phase, progress, 3, top, width - 6, bodyHeight);
            default -> {
            }
        }
    }

    /**
     * Satellites de l'agence suivie : à gauche la planète, les quatre orbites et les satellites qui y tournent,
     * chacun dessiné comme un petit satellite (un corps de la couleur de son type entre deux panneaux solaires) ;
     * à droite, s'il reste de la place, leur liste.
     */
    private void composeSatellites(State state, int[] satellites, float width, float height, float time) {
        int fields = MissionDisplayBlockEntity.SATELLITE_FIELDS;
        int count = satellites.length / fields;
        int active = 0;
        for (int i = 0; i < count; i++) {
            active += satellites[i * fields + 2];
        }
        // Le bandeau ne s'affiche que sur un écran assez haut : sur une seule dalle, toute la place va aux orbites.
        float bar = height >= 60 ? 15 : 0;
        if (bar > 0) {
            rect(state, 0, 0, width, bar, COLOR_PANEL, 1);
            rect(state, 0, bar, width, bar + 1, COLOR_EDGE, 1);
            String title = translate("satellite.orbitalis.station.heading");
            if (whole(state, 4, 4, title, width - 8, COLOR_ACCENT, false)) {
                whole(state, width - 4, 4, Component.translatable("satellite.orbitalis.station.active", active).getString(),
                        width - 14 - font.width(title), COLOR_MUTED, true);
            }
        }

        float top = bar + (bar > 0 ? 4 : 3);
        float bodyHeight = height - 3 - top;
        float size = Math.min(bodyHeight, width - 6);
        // Avec une liste à côté, la carte lui laisse au moins la moitié de la largeur.
        boolean hasList = width - 6 - size >= 70 || width >= 150;
        if (hasList) {
            size = Math.min(size, width * 0.5F);
        }
        float mapX = hasList ? 3 : (width - size) / 2.0F;
        float centerX = mapX + size / 2.0F;
        float centerY = top + bodyHeight / 2.0F;
        float reach = size / 2.0F - 3;
        rect(state, mapX, centerY - size / 2.0F, mapX + size, centerY + size / 2.0F, COLOR_PANEL, 1);

        // Orbites en pointillés, puis la planète par-dessus.
        float planet = reach * 0.25F;
        for (Orbit orbit : Orbit.values()) {
            float radiusX = orbitRadiusX(orbit) * reach;
            float radiusY = orbitRadiusY(orbit) * reach;
            int points = Math.max(24, (int) (radiusX + radiusY));
            for (int i = 0; i < points; i += 2) {
                float angle = Mth.TWO_PI * i / points;
                float dx = Mth.cos(angle) * radiusX;
                float dy = Mth.sin(angle) * radiusY;
                // Moitié de l'orbite polaire cachée par la planète.
                if (orbit == Orbit.POLAR && dx > 0 && dx * dx + dy * dy < planet * planet) {
                    continue;
                }
                rect(state, centerX + dx, centerY + dy, centerX + dx + 0.8F, centerY + dy + 0.8F, COLOR_EDGE, 2);
            }
        }
        int rows = Math.max(4, (int) (planet * 2));
        for (int row = 0; row < rows; row++) {
            float dy = -planet + (row + 0.5F) * 2 * planet / rows;
            float half = (float) Math.sqrt(Math.max(0, planet * planet - dy * dy));
            float y0 = centerY - planet + row * 2 * planet / rows;
            float y1 = y0 + 2 * planet / rows;
            rect(state, centerX - half, y0, centerX + half, y1, 0xFF2E6FB7, 3);
            // Un continent au centre, et le côté nuit à droite.
            if (Math.abs(dy) < planet * 0.55F) {
                rect(state, centerX - half * 0.5F, y0, centerX + half * 0.1F, y1, 0xFF4E9A52, 4);
            }
            rect(state, centerX + half * 0.4F, y0, centerX + half, y1, 0x66000000, 5);
        }

        // Satellites en service : un petit satellite par engin, sur son orbite, à la vitesse de celle-ci.
        float glyph = Mth.clamp(size / 40.0F, 1.0F, 2.5F);
        for (int i = 0; i < count; i++) {
            if (satellites[i * fields + 2] == 0) {
                continue;
            }
            SatelliteType type = satelliteType(satellites[i * fields + 1]);
            Orbit orbit = type.orbit();
            float angle = satellites[i * fields] * 2.399963F + Mth.TWO_PI * time / ((float) orbit.periodMinutes() * 4.0F);
            float dx = Mth.cos(angle) * orbitRadiusX(orbit) * reach;
            float dy = Mth.sin(angle) * orbitRadiusY(orbit) * reach;
            if (orbit == Orbit.POLAR && Mth.cos(angle) > 0 && dx * dx + dy * dy < planet * planet) {
                continue;
            }
            float x = centerX + dx;
            float y = centerY + dy;
            rect(state, x - 2.6F * glyph, y - 0.6F * glyph, x - 1.0F * glyph, y + 0.6F * glyph, 0xFF3F76E4, 6);
            rect(state, x + 1.0F * glyph, y - 0.6F * glyph, x + 2.6F * glyph, y + 0.6F * glyph, 0xFF3F76E4, 6);
            rect(state, x - 1.0F * glyph, y - 1.0F * glyph, x + 1.0F * glyph, y + 1.0F * glyph, type.color(), 7);
        }

        if (!hasList) {
            return;
        }
        // Liste : les plus récents d'abord, tant que les lignes tiennent.
        float listX = mapX + size + 4;
        float listWidth = width - 3 - listX;
        panel(state, listX, top, listWidth, bodyHeight);
        float scale = listWidth >= 110 ? 1.0F : 0.8F;
        float limit = top + bodyHeight - 2;
        float lineY = top + 3;
        if (count == 0) {
            for (FormattedCharSequence line : font.split(Component.translatable("satellite.orbitalis.station.empty"), (int) ((listWidth - 6) / scale))) {
                if (lineY + 8 * scale > limit) {
                    break;
                }
                state.labels.add(new Label(listX + 3, lineY, line, COLOR_MUTED, scale));
                lineY += 9 * scale;
            }
            return;
        }
        for (int i = count - 1; i >= 0; i--) {
            SatelliteType type = satelliteType(satellites[i * fields + 1]);
            boolean inService = satellites[i * fields + 2] != 0;
            String name = Component.translatable("satellite.orbitalis.station.name", Component.translatable(type.nameKey()),
                    satellites[i * fields]).getString();
            String orbit = inService ? translate(type.orbit().nameKey() + ".short") : translate("satellite.orbitalis.station.retired");
            float next = row(state, listX, lineY, listWidth, limit, scale, name, orbit, inService ? type.color() : COLOR_BAD);
            if (next == lineY) {
                break;
            }
            lineY = next;
        }
    }

    private static SatelliteType satelliteType(int ordinal) {
        SatelliteType[] types = SatelliteType.values();
        return types[Math.floorMod(ordinal, types.length)];
    }

    /** Demi-largeur d'une orbite sur la carte, en part de son rayon. L'orbite polaire, vue par la tranche, est une ellipse étroite. */
    private static float orbitRadiusX(Orbit orbit) {
        return switch (orbit) {
            case LOW -> 0.375F;
            case POLAR -> 0.16F;
            case MEDIUM -> 0.75F;
            case GEOSTATIONARY -> 1.0F;
            case LUNAR -> 1.0F;
        };
    }

    private static float orbitRadiusY(Orbit orbit) {
        return orbit == Orbit.POLAR ? 0.5F : orbitRadiusX(orbit);
    }

    /** Vrai si l'un des écrans du mur est celui d'un centre spatial, en veille. */
    private static boolean isStandby(MissionDisplayBlockEntity display) {
        return display.isStandby();
    }

    /**
     * Écran de veille d'un centre spatial : le bandeau et l'heure, une grille que balaie lentement une ligne, et
     * l'état de la liaison. Rien à faire, rien à lire de plus.
     */
    private void composeStandby(State state, MissionDisplayBlockEntity display, float width, float height, float time) {
        rect(state, 0, 0, width, 12, COLOR_PANEL, 1);
        rect(state, 0, 12, width, 12.6F, COLOR_EDGE, 1);
        long day = display.getLevel().getDefaultClockTime() % 24000L;
        int minutes = (int) ((day + 6000L) % 24000L * 1440L / 24000L);
        String hour = "%02d:%02d".formatted(minutes / 60, minutes % 60);
        boolean clock = whole(state, width - 4, 2.5F, hour, width / 2.0F - 6, COLOR_TEXT, true);
        whole(state, 4, 2.5F, translate("display.orbitalis.standby"), width - (clock ? font.width(hour) + 14 : 8), COLOR_ACCENT, false);
        for (float x = 8; x < width - 4; x += 16) {
            rect(state, x, 16, x + 0.5F, height - 14, COLOR_UNKNOWN, 1);
        }
        for (float y = 20; y < height - 14; y += 12) {
            rect(state, 4, y, width - 4, y + 0.5F, COLOR_UNKNOWN, 1);
        }
        float sweep = 4 + (time * 0.5F) % Math.max(1.0F, width - 9);
        rect(state, sweep, 16, sweep + 1.0F, height - 14, COLOR_EDGE, 2);
        // Le voyant de veille, qui respire, et l'état de la liaison.
        float glow = 0.5F + 0.5F * (float) Math.sin(time * 0.08F);
        int lamp = glow > 0.5F ? COLOR_WARNING : 0xFF8A6D2A;
        rect(state, 4, height - 9, 8, height - 5, lamp, 2);
        text(state, 11, height - 10, translate("display.orbitalis.standby.link"), width - 15, COLOR_MUTED, 1.0F);
    }

    /** Écran sans source : ce qu'il lui manque, et la marche à suivre s'il reste de la place. */
    private void composeUnlinked(State state, boolean lostLink, float width, float height) {
        String title = translate(lostLink ? "display.orbitalis.lost" : "display.orbitalis.unlinked");
        // Sur une seule dalle, le titre long ne tient pas : on écrit sa version courte, entière.
        if (font.width(title) > width - 8) {
            title = translate("display.orbitalis.no_signal");
        }
        float y = height < 70 ? Math.max(4, height / 2.0F - 4) : Math.max(4, height / 2.0F - 22);
        if (!whole(state, Math.max(4, (width - font.width(title)) / 2.0F), y, title, width - 8, COLOR_WARNING, false)) {
            // Une seule dalle : pas la place d'un mot, un voyant de veille suffit.
            rect(state, width / 2.0F - 5, height / 2.0F - 1, width / 2.0F + 5, height / 2.0F + 1, COLOR_WARNING, 1);
        }
        if (height < 70) {
            return;
        }
        y += 13;
        float scale = 0.8F;
        for (FormattedCharSequence line : font.split(Component.translatable("display.orbitalis.hint"), (int) ((width - 10) / scale))) {
            if (y + 8 * scale > height - 3) {
                break;
            }
            state.labels.add(new Label(5, y, line, COLOR_MUTED, scale));
            y += 9 * scale;
        }
    }

    /**
     * Pose la carte de navigation (voir {@link IsoMap}) sur ce rectangle de l'écran, qu'elle remplit en entier. Sa
     * prise de vue est refaite à chaque image du jeu, au format exact du rectangle : rien n'est déformé, et tout
     * ce qui bouge avance sans à-coup.
     */
    private void placeMap(State state, LaunchTableBlockEntity table, float x0, float y0, float x1, float y1) {
        state.mapScale = (y1 - y0) / IsoMap.VIEW_HEIGHT;
        state.mapX0 = x0;
        state.mapY0 = y0;
        state.mapPrims = IsoMap.frame(table, partial, (x1 - x0) / state.mapScale);
    }

    /** Une étiquette posée sur la carte : son texte sur un cartouche sombre, pour rester lisible sur le terrain. */
    private void chip(State state, float x, float y, String text, int color, float scale, boolean alignRight) {
        float textWidth = font.width(text) * scale;
        float left = alignRight ? x - textWidth - 4 : x;
        rect(state, left, y, left + textWidth + 4, y + 8 * scale + 3, 0xD00A0F16, 4);
        label(state, left + 2, y + 2, text, color, scale);
    }

    /**
     * La carte de navigation en plein écran : elle couvre tout l'écran, d'un bord à l'autre. Par-dessus, sans rien
     * cacher d'utile : l'horloge de mission et la phase en haut, la position du pas de tir en bas ; en vol,
     * l'altitude, la vitesse et la distance parcourue au sol.
     */
    private void composeMapScreen(State state, LaunchTableBlockEntity table, float width, float height) {
        placeMap(state, table, 0, 0, width, height);
        LaunchTableBlockEntity.Phase phase = table.getPhase();
        RocketStats stats = table.hasRocket() ? table.rocketStats() : null;
        float phaseTicks = table.phaseTicks() + partial;
        boolean flying = phase == LaunchTableBlockEntity.Phase.FLIGHT;
        float progress = flying ? Mth.clamp(phaseTicks / table.flightTicks(), 0.0F, 1.0F) : 0.0F;
        int color = phase == LaunchTableBlockEntity.Phase.COUNTDOWN ? COLOR_WARNING : flying ? COLOR_OK : COLOR_TEXT;
        String clock = switch (phase) {
            case COUNTDOWN -> "T-" + clock(Math.max(0, (LaunchTableBlockEntity.COUNTDOWN_TICKS - phaseTicks) / 20.0F));
            case FLIGHT -> "T+" + clock(stats == null ? phaseTicks / 20.0F : (float) FlightProfile.missionSeconds(stats, progress));
            default -> "T-00:00";
        };
        float scale = width >= 150 ? 1.4F : 1.0F;
        if (font.width(clock) * scale + 10 > width) {
            return;
        }
        chip(state, 3, 3, clock, color, scale, false);
        String title = translate(MissionDisplayBlockEntity.Page.MAP.nameKey());
        if (font.width(title) + font.width(clock) * scale + 24 <= width) {
            chip(state, width - 3, 3, title, COLOR_ACCENT, 1.0F, true);
        }
        if (height < 60) {
            return;
        }
        // En bas : le site à gauche ; en vol, la télémesure à droite, une valeur par ligne.
        String site = "X " + table.getBlockPos().getX() + "  Z " + table.getBlockPos().getZ();
        if (font.width(site) + 10 <= width) {
            chip(state, 3, height - 14, site, COLOR_MUTED, 1.0F, false);
        }
        if (flying && stats != null && width >= 150) {
            // La distance au sol est celle du vol calculé, celle que trace la carte : en mètres, puis en kilomètres.
            double downrange = FlightProfile.downrangeKm(stats, progress);
            String distance = downrange < 1.0 ? Component.translatable("display.orbitalis.map.downrange", grouped(downrange * 1000.0)).getString()
                    : Component.translatable("display.orbitalis.map.downrange_km", grouped(downrange)).getString();
            String[] lines = {
                    grouped(FlightProfile.speed(stats, progress)) + " m/s",
                    String.format(Locale.ROOT, "%.1f km", FlightProfile.altitudeKm(stats, progress)),
                    distance};
            float y = height - 14;
            for (String line : lines) {
                if (y < 30) {
                    break;
                }
                chip(state, width - 3, y, line, COLOR_TEXT, 1.0F, true);
                y -= 13;
            }
        }
    }

    /**
     * La carte de navigation dans un volet de la vue d'ensemble : son titre, la carte qui remplit le volet, et la
     * position du pas de tir dessous.
     */
    private void composeMap(State state, LaunchTableBlockEntity table, RocketStats stats, float progress, float x, float top,
            float width, float height) {
        text(state, x, top, translate("launch.orbitalis.console.heading_map"), width, COLOR_MUTED, 0.8F);
        float y = top + 9;
        float mapHeight = height - 9 - 10;
        rect(state, x - 1, y - 1, x + width + 1, y + mapHeight + 1, COLOR_EDGE, 1);
        placeMap(state, table, x, y, x + width, y + mapHeight);
        text(state, x, y + mapHeight + 3, "X " + table.getBlockPos().getX() + "  Z " + table.getBlockPos().getZ(), width, COLOR_TEXT, 0.8F);
    }

    /**
     * Caractéristiques de la fusée et état du pas de tir, par ordre d'importance : la liste s'arrête dès que
     * la place manque. Sur un mur sans graphique, l'altitude et la vitesse passent en tête pendant le vol.
     */
    private void composeStats(State state, LaunchTableBlockEntity table, RocketStats stats, LaunchTableBlockEntity.Phase phase,
            float progress, boolean showTelemetry, float x, float y, float width, float height) {
        panel(state, x, y, width, height);
        float scale = width >= 104 ? 1.0F : 0.8F;
        float step = 9.5F * scale + 0.5F;
        float limit = y + height - 2;
        float lineY = y + 3;
        LaunchComplex complex = table.getComplex();
        boolean flying = phase == LaunchTableBlockEntity.Phase.FLIGHT && stats != null;

        if (stats == null) {
            text(state, x + 3, lineY, translate("spaceport.orbitalis.console.no_rocket"), width - 6, COLOR_BAD, scale);
            lineY += step;
        } else {
            text(state, x + 3, lineY, table.getRocket().getHoverName().getString(), width - 6, COLOR_TEXT, scale);
            lineY += step;
            if (lineY + 8 * scale <= limit) {
                text(state, x + 3, lineY, translate(stats.reach().nameKey()), width - 6, COLOR_OK, scale);
                lineY += step + 1;
            }
            if (showTelemetry && flying) {
                lineY = row(state, x, lineY, width, limit, scale, translate("launch.orbitalis.console.altitude"),
                        grouped(FlightProfile.altitudeKm(stats, progress)) + " km", COLOR_OK);
                lineY = row(state, x, lineY, width, limit, scale, translate("launch.orbitalis.console.speed"),
                        grouped(FlightProfile.speed(stats, progress)) + " m/s", COLOR_OK);
            }
            lineY = row(state, x, lineY, width, limit, scale, translate("rocket.orbitalis.mass"), mass(stats.liftoffMass()), COLOR_TEXT);
            lineY = row(state, x, lineY, width, limit, scale, translate("rocket.orbitalis.delta_v"), grouped(stats.totalDeltaV()) + " m/s", COLOR_TEXT);
            if (table.needsArm()) {
                int fuel = table.fuelPercent();
                lineY = row(state, x, lineY, width, limit, scale, translate("launch.orbitalis.station.propellant"), fuel + " %",
                        fuel >= 100 ? COLOR_OK : COLOR_BAD);
            }
            lineY = row(state, x, lineY, width, limit, scale, translate("spaceport.orbitalis.pad.height"),
                    Component.translatable("spaceport.orbitalis.pad.blocks", table.rocketHeight()).getString(), COLOR_TEXT);
            lineY = row(state, x, lineY, width, limit, scale, translate("rocket.orbitalis.thrust"),
                    grouped(stats.liftoffThrust() / 1000.0) + " kN", COLOR_TEXT);
            if (!stats.stages().isEmpty()) {
                lineY = row(state, x, lineY, width, limit, scale, "TWR", String.format(Locale.ROOT, "%.2f", stats.stages().get(0).twr()), COLOR_TEXT);
            }
        }
        int padSize = complex.padSize();
        lineY = row(state, x, lineY, width, limit, scale, translate("launch.orbitalis.station.pad"), padSize + " × " + padSize,
                table.isOperational() ? COLOR_OK : COLOR_BAD);
        lineY = row(state, x, lineY, width, limit, scale, translate("spaceport.orbitalis.pad.max_mass"), mass(complex.maxRocketMass()), COLOR_TEXT);
        // Verdict, tout en bas, s'il reste une ligne libre.
        float verdictY = y + height - 2 - 8 * scale;
        if (verdictY >= lineY + 1) {
            boolean go = table.isGoForLaunch();
            text(state, x + 3, verdictY, translate(go ? "launch.orbitalis.console.go" : "launch.orbitalis.console.no_go_short"),
                    width - 6, go ? COLOR_OK : COLOR_BAD, scale);
        }
    }

    /** Trajectoire : la courbe d'altitude prévue, la partie déjà parcourue, la position de la fusée, et la télémétrie. */
    private void composeTrajectory(State state, RocketStats stats, LaunchTableBlockEntity.Phase phase, float progress,
            float x, float y, float width, float height) {
        boolean flying = phase == LaunchTableBlockEntity.Phase.FLIGHT && stats != null;
        String altitude = flying ? grouped(FlightProfile.altitudeKm(stats, progress)) + " km" : "---";
        String speed = flying ? grouped(FlightProfile.speed(stats, progress)) + " m/s" : "---";
        int valueColor = flying ? COLOR_OK : COLOR_MUTED;

        // Sur un mur large, la télémétrie a son propre volet ; sinon elle s'inscrit dans l'en-tête du graphique.
        boolean split = width >= 170;
        float plotWidth = split ? width * 0.64F : width;
        panel(state, x, y, plotWidth, height);
        if (split) {
            float panelX = x + plotWidth + 4;
            float panelWidth = width - plotWidth - 4;
            panel(state, panelX, y, panelWidth, height);
            text(state, panelX + 3, y + 3, translate("launch.orbitalis.console.altitude"), panelWidth - 6, COLOR_MUTED, 0.8F);
            text(state, panelX + 3, y + 10, altitude, panelWidth - 6, valueColor, 1.1F);
            if (height >= 42) {
                text(state, panelX + 3, y + 22, translate("launch.orbitalis.console.speed"), panelWidth - 6, COLOR_MUTED, 0.8F);
                text(state, panelX + 3, y + 29, speed, panelWidth - 6, valueColor, 1.1F);
            }
            text(state, x + 3, y + 3, translate("launch.orbitalis.console.altitude_curve"), plotWidth - 6, COLOR_MUTED, 0.8F);
        } else {
            float valueWidth = font.width(altitude) * 0.8F;
            label(state, x + plotWidth - 3 - valueWidth, y + 3, altitude, valueColor, 0.8F);
            text(state, x + 3, y + 3, translate("launch.orbitalis.console.altitude"), plotWidth - 10 - valueWidth, COLOR_MUTED, 0.8F);
        }

        if (stats == null) {
            return;
        }
        float plotLeft = x + 4;
        float plotRight = x + plotWidth - 4;
        float plotBottom = y + height - 4;
        float plotTop = y + 13;
        int steps = 80;
        for (int i = 0; i <= steps; i++) {
            float fraction = i / (float) steps;
            float px = Mth.lerp(fraction, plotLeft, plotRight);
            float py = plotBottom - (float) (FlightProfile.altitudeKm(stats, fraction) / FlightProfile.targetAltitudeKm(stats))
                    * (plotBottom - plotTop);
            boolean flown = flying && fraction <= progress;
            rect(state, px - 0.5F, py - 0.5F, px + 0.5F, py + 0.5F, flown ? COLOR_WARNING : 0xFF3F5670, flown ? 3 : 2);
        }
        if (flying) {
            float px = Mth.lerp(progress, plotLeft, plotRight);
            float py = plotBottom - (float) (FlightProfile.altitudeKm(stats, progress) / FlightProfile.targetAltitudeKm(stats))
                    * (plotBottom - plotTop);
            rect(state, px - 2, py - 2, px + 2, py + 2, 0xFFFFFFFF, 4);
        }
    }

    /**
     * La télémesure, comme sur un vrai écran de contrôle. À gauche, trois relevés (altitude, vitesse, accélération),
     * chacun sur sa jauge graduée ; à droite, deux enregistrements (altitude et vitesse) où la courbe prévue est en
     * pointillé et la courbe mesurée s'inscrit par-dessus.
     * <p>
     * Rien n'est lissé artificiellement : les relevés arrivent par trames, dix fois par seconde, et la courbe
     * mesurée est faite des points réellement reçus, deux par seconde, reliés en escalier. C'est le mouvement d'un
     * instrument, pas celui d'une animation.
     */
    private void composeTelemetry(State state, LaunchTableBlockEntity table, RocketStats stats, LaunchTableBlockEntity.Phase phase,
            float progress, float x, float y, float width, float height) {
        if (stats == null || width < 150 || height < 60) {
            composeStats(state, table, stats, phase, progress, true, x, y, width, height);
            return;
        }
        boolean flying = phase == LaunchTableBlockEntity.Phase.FLIGHT;
        // La trame en cours : le dernier relevé reçu, il y a deux ticks au plus.
        float frame = flying ? Mth.floor(progress * table.flightTicks() / 2.0F) * 2.0F / table.flightTicks() : 0.0F;
        double altitude = FlightProfile.altitudeKm(stats, frame);
        double speed = FlightProfile.speed(stats, frame);
        double load = FlightProfile.gForce(stats, frame);
        double topAltitude = Math.max(1.0, FlightProfile.targetAltitudeKm(stats));
        double topSpeed = 1.0;
        for (int i = 0; i <= 40; i++) {
            topSpeed = Math.max(topSpeed, FlightProfile.speed(stats, i / 40.0));
        }

        float left = Math.min(width * 0.36F, 112.0F);
        panel(state, x, y, left, height);
        String[] names = {translate("launch.orbitalis.console.altitude"), translate("launch.orbitalis.console.speed"),
                translate("display.orbitalis.telemetry.acceleration")};
        String[] values = {flying ? String.format(Locale.ROOT, altitude < 100 ? "%.2f km" : "%.1f km", altitude) : "---",
                flying ? grouped(speed) + " m/s" : "---", flying ? String.format(Locale.ROOT, "%.2f g", load) : "---"};
        double[] shares = {altitude / topAltitude, speed / topSpeed, load / 6.0};
        float slot = (height - 4) / 3.0F;
        float big = slot >= 34 && left >= 90 ? 1.5F : 1.1F;
        for (int i = 0; i < 3; i++) {
            float top = y + 3 + i * slot;
            text(state, x + 4, top, names[i], left - 8, COLOR_MUTED, 0.8F);
            if (font.width(values[i]) * big <= left - 8) {
                label(state, x + 4, top + 8, values[i], flying ? COLOR_TEXT : COLOR_MUTED, big);
            }
            // La jauge : une réglette graduée tous les quarts, et un index qui s'y déplace.
            float track = top + slot - 7;
            if (track > top + 8 + 8 * big) {
                rect(state, x + 4, track, x + left - 4, track + 2, COLOR_UNKNOWN, 2);
                for (int tick = 0; tick <= 4; tick++) {
                    float tx = x + 4 + (left - 9) * tick / 4.0F;
                    rect(state, tx, track - 2, tx + 1, track + 2, COLOR_EDGE, 2);
                }
                float share = (float) Mth.clamp(shares[i], 0.0, 1.0);
                if (flying) {
                    rect(state, x + 4, track, x + 4 + (left - 9) * share, track + 2, i == 2 && load > 4.5 ? COLOR_BAD : COLOR_ACCENT, 3);
                    rect(state, x + 3 + (left - 9) * share, track - 3, x + 5 + (left - 9) * share, track + 3, COLOR_TEXT, 4);
                }
            }
        }

        float chartX = x + left + 4;
        float chartWidth = width - left - 4;
        float chartHeight = (height - 3) / 2.0F;
        chart(state, chartX, y, chartWidth, chartHeight, names[0], grouped(topAltitude) + " km", stats, true, topAltitude, flying, progress, COLOR_WARNING);
        chart(state, chartX, y + chartHeight + 3, chartWidth, chartHeight, names[1], grouped(topSpeed) + " m/s", stats, false, topSpeed, flying, progress,
                COLOR_ACCENT);
    }

    /**
     * Un enregistrement : la grandeur prévue sur tout le vol, en pointillé, et la grandeur mesurée depuis le
     * décollage, en trait plein, point reçu après point reçu.
     *
     * @param altitude vrai pour l'altitude, faux pour la vitesse
     * @param top      valeur du haut de l'échelle
     */
    private void chart(State state, float x, float y, float width, float height, String name, String scale, RocketStats stats, boolean altitude,
            double top, boolean flying, float progress, int color) {
        panel(state, x, y, width, height);
        float scaleWidth = font.width(scale) * 0.8F;
        label(state, x + width - 3 - scaleWidth, y + 3, scale, COLOR_MUTED, 0.8F);
        text(state, x + 3, y + 3, name, width - 10 - scaleWidth, COLOR_MUTED, 0.8F);
        float plotLeft = x + 4;
        float plotRight = x + width - 4;
        float plotTop = y + 13;
        float plotBottom = y + height - 4;
        if (plotBottom - plotTop < 8) {
            return;
        }
        // Le quadrillage : trois lignes de niveau, et l'axe du temps.
        for (int line = 0; line <= 3; line++) {
            float ly = Mth.lerp(line / 3.0F, plotBottom, plotTop);
            rect(state, plotLeft, ly, plotRight, ly + 0.4F, line == 0 ? COLOR_EDGE : 0xFF1C2530, 2);
        }
        java.util.function.DoubleUnaryOperator value = at -> (altitude ? FlightProfile.altitudeKm(stats, at) : FlightProfile.speed(stats, at)) / top;
        java.util.function.DoubleUnaryOperator place = share -> plotBottom - Mth.clamp(share, 0.0, 1.0) * (plotBottom - plotTop);
        // La courbe prévue : un point sur trois.
        int columns = Math.max(8, (int) (plotRight - plotLeft));
        for (int i = 0; i <= columns; i += 3) {
            float at = i / (float) columns;
            float py = (float) place.applyAsDouble(value.applyAsDouble(at));
            rect(state, plotLeft + i, py - 0.4F, plotLeft + i + 1, py + 0.4F, 0xFF3F5670, 3);
        }
        if (!flying) {
            return;
        }
        // La courbe mesurée : un point reçu toutes les demi-secondes, tenu jusqu'au suivant.
        int samples = com.orbitalis.phase1.spaceport.FlightProfile.flightTicks(stats) / 10;
        int received = Mth.floor(progress * samples);
        float lastY = plotBottom;
        for (int k = 0; k <= received; k++) {
            float from = Mth.lerp(k / (float) samples, plotLeft, plotRight);
            float to = Mth.lerp(Math.min(progress, (k + 1) / (float) samples), plotLeft, plotRight);
            float py = (float) place.applyAsDouble(value.applyAsDouble(k / (double) samples));
            rect(state, from, Math.min(lastY, py) - 0.5F, from + 1, Math.max(lastY, py) + 0.5F, color, 4);
            rect(state, from, py - 0.5F, Math.max(from + 1, to), py + 0.5F, color, 4);
            lastY = py;
        }
        // L'instant présent : un trait vertical fin, et le dernier point reçu.
        float now = Mth.lerp(progress, plotLeft, plotRight);
        rect(state, now, plotTop, now + 0.4F, plotBottom, 0x60FFFFFF, 3);
        rect(state, now - 1.5F, lastY - 1.5F, now + 1.5F, lastY + 1.5F, COLOR_TEXT, 5);
    }

    // ------------------------------------------------------------------
    // Outils de composition
    // ------------------------------------------------------------------

    private static void rect(State state, float x0, float y0, float x1, float y1, int color, int layer) {
        state.rects.add(new Rect(x0, y0, x1, y1, color, layer));
    }

    private static void panel(State state, float x, float y, float width, float height) {
        rect(state, x, y, x + width, y + height, COLOR_PANEL, 1);
    }

    private void label(State state, float x, float y, String text, int color, float scale) {
        state.labels.add(new Label(x, y, Component.literal(text).getVisualOrderText(), color, scale));
    }

    /** Texte coupé à la largeur disponible (en points) ; rien n'est écrit si la place manque. */
    private void text(State state, float x, float y, String text, float maxWidth, int color, float scale) {
        String fitted = font.plainSubstrByWidth(text, Math.max(0, (int) (maxWidth / scale)));
        if (!fitted.isEmpty()) {
            label(state, x, y, fitted, color, scale);
        }
    }

    /**
     * Texte écrit seulement s'il tient en entier dans la largeur disponible ; aligné à gauche sur {@code x},
     * ou à droite si {@code alignRight}. Renvoie vrai s'il a été écrit.
     */
    private boolean whole(State state, float x, float y, String text, float maxWidth, int color, boolean alignRight) {
        int textWidth = font.width(text);
        if (textWidth > maxWidth) {
            return false;
        }
        label(state, alignRight ? x - textWidth : x, y, text, color, 1.0F);
        return true;
    }

    /**
     * Une ligne : le libellé à gauche, la valeur alignée à droite. Elle n'est écrite que si elle tient au-dessus
     * de {@code limit}. Renvoie la hauteur de la ligne suivante.
     */
    private float row(State state, float x, float y, float width, float limit, float scale, String name, String value, int valueColor) {
        if (y + 8 * scale > limit) {
            return y;
        }
        float valueWidth = font.width(value) * scale;
        label(state, x + width - 3 - valueWidth, y, value, valueColor, scale);
        text(state, x + 3, y, name, width - 10 - valueWidth, COLOR_MUTED, scale);
        return y + 9.5F * scale + 0.5F;
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

    private static String mass(double kilograms) {
        return kilograms >= 10_000 ? String.format(Locale.ROOT, "%.1f t", kilograms / 1000.0) : grouped(kilograms) + " kg";
    }

    // ------------------------------------------------------------------
    // Dessin
    // ------------------------------------------------------------------

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.visible) {
            return;
        }
        poseStack.pushPose();
        // Repère de l'écran : origine en haut à gauche de la face avant du mur, x vers la droite, y vers le bas,
        // z vers le spectateur ; l'image est posée juste devant la vitre de la dalle, qui est mince et plaquée au mur.
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.yRot));
        poseStack.translate(-0.5F, 0.5F, -0.5F + SURFACE);
        float unit = 1.0F / POINTS_PER_BLOCK;
        poseStack.scale(unit, -unit, unit);

        // Tous les rectangles en une fois ; chaque couche est décalée d'un rien vers le spectateur.
        List<Rect> rects = List.copyOf(state.rects);
        collector.submitCustomGeometry(poseStack, RenderTypes.textBackground(), (pose, buffer) -> {
            for (Rect rect : rects) {
                float z = rect.layer() * 0.02F;
                buffer.addVertex(pose, rect.x0(), rect.y0(), z).setColor(rect.color()).setLight(LightCoordsUtil.FULL_BRIGHT);
                buffer.addVertex(pose, rect.x0(), rect.y1(), z).setColor(rect.color()).setLight(LightCoordsUtil.FULL_BRIGHT);
                buffer.addVertex(pose, rect.x1(), rect.y1(), z).setColor(rect.color()).setLight(LightCoordsUtil.FULL_BRIGHT);
                buffer.addVertex(pose, rect.x1(), rect.y0(), z).setColor(rect.color()).setLight(LightCoordsUtil.FULL_BRIGHT);
            }
        });

        // La carte de navigation : ses plans du fond vers l'avant, chacun un rien devant le précédent. Ses rectangles
        // puisent dans plusieurs images (une par tuile de terrain) : ils sont dessinés par tranches, une par suite de
        // rectangles de la même image, et chaque tranche a son rang. Sans ce rang, le moteur regrouperait les images
        // dans un ordre à lui ; une tuile dessinée avant celle qu'elle recouvre laisserait voir le fond par son bord
        // adouci, en un fin trait sombre le long de chaque tuile.
        List<List<IsoMap.Prim>> runs = new ArrayList<>();
        for (IsoMap.Prim prim : state.mapPrims) {
            if (runs.isEmpty() || !runs.get(runs.size() - 1).get(0).texture().equals(prim.texture())) {
                runs.add(new ArrayList<>());
            }
            runs.get(runs.size() - 1).add(prim);
        }
        for (int run = 0; run < runs.size(); run++) {
            List<IsoMap.Prim> prims = runs.get(run);
            float ox = state.mapX0;
            float oy = state.mapY0;
            float step = state.mapScale;
            collector.order(1 + run).submitCustomGeometry(poseStack, RenderTypes.text(prims.get(0).texture()), (pose, buffer) -> {
                for (IsoMap.Prim prim : prims) {
                    float z = 0.05F + prim.depth() * 0.0035F;
                    float x0 = ox + prim.x0() * step;
                    float y0 = oy + prim.y0() * step;
                    float x1 = ox + prim.x1() * step;
                    float y1 = oy + prim.y1() * step;
                    buffer.addVertex(pose, x0, y0, z).setColor(prim.top()).setUv(prim.u0(), prim.v0()).setLight(LightCoordsUtil.FULL_BRIGHT);
                    buffer.addVertex(pose, x0, y1, z).setColor(prim.bottom()).setUv(prim.u0(), prim.v1()).setLight(LightCoordsUtil.FULL_BRIGHT);
                    buffer.addVertex(pose, x1, y1, z).setColor(prim.bottom()).setUv(prim.u1(), prim.v1()).setLight(LightCoordsUtil.FULL_BRIGHT);
                    buffer.addVertex(pose, x1, y0, z).setColor(prim.top()).setUv(prim.u1(), prim.v0()).setLight(LightCoordsUtil.FULL_BRIGHT);
                }
            });
        }

        for (Label label : state.labels) {
            poseStack.pushPose();
            poseStack.translate(label.x(), label.y(), 0.16F);
            poseStack.scale(label.scale(), label.scale(), 1.0F);
            collector.submitText(poseStack, 0.0F, 0.0F, label.text(), false, Font.DisplayMode.NORMAL, LightCoordsUtil.FULL_BRIGHT,
                    label.color(), 0, 0);
            poseStack.popPose();
        }

        // Fusée en volume, qui tourne sur son axe ; elle est aplatie en profondeur pour rester devant l'écran.
        if (state.rocket != null) {
            poseStack.pushPose();
            float depth = 0.35F;
            poseStack.translate(state.rocketX, state.rocketY, state.rocketScale * (state.rocket.isWide() ? 1.6F : 0.6F) * depth + 0.3F);
            poseStack.scale(state.rocketScale, -state.rocketScale, state.rocketScale * depth);
            poseStack.mulPose(Axis.YP.rotationDegrees(state.rocketAngle));
            for (int i = 0; i < state.rocket.height(); i++) {
                BlockState part = ModBlocks.ROCKET.get().defaultBlockState()
                        .setValue(RocketBlock.PART, state.rocket.parts().get(i)).setValue(RocketBlock.SIZE, state.rocket.size());
                poseStack.pushPose();
                poseStack.translate(-0.5F, i, -0.5F);
                ModelCache.get(part).submit(poseStack, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
                poseStack.popPose();
            }
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    /** L'image couvre tout le mur : elle est dessinée même si le bloc du coin est hors champ. */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public AABB getRenderBoundingBox(MissionDisplayBlockEntity display) {
        return new AABB(display.getBlockPos()).inflate(16.0);
    }
}
