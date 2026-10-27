package com.orbitalis.client;

import java.util.Locale;

import com.mojang.blaze3d.platform.NativeImage;
import com.orbitalis.Orbitalis;
import com.orbitalis.client.screen.Ui;
import com.orbitalis.core.network.SuitStatusPayload;
import com.orbitalis.phase2.flight.CapsuleEntity;
import com.orbitalis.phase2.station.StationPower;
import com.orbitalis.phase2.survival.SpaceRules;
import com.orbitalis.phase2.survival.SpaceSuits;
import com.orbitalis.phase2.survival.SuitModule;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Affichage tête haute de la combinaison spatiale : ce que le casque projette sur les côtés de sa visière, en vue à
 * la première personne.
 * <ul>
 * <li>à gauche, la survie : l'oxygène qu'il reste et sa jauge, la pression de la combinaison, son étanchéité, et si
 * l'on respire l'air du dehors, sa réserve, ou si elle se recharge ;</li>
 * <li>à droite, avec les modules de la combinaison avancée : le capteur d'environnement (pression, air, pesanteur,
 * température), puis la carte de proximité, les murs et les sols de quelques blocs alentour, vus d'en haut.</li>
 * </ul>
 * Les panneaux ne sont pas collés à l'écran : ils traînent un peu derrière le regard quand la tête tourne, puis
 * reviennent en place, et suivent le pas quand on marche, comme une visière qui bouge avec son porteur.
 */
@EventBusSubscriber(modid = Orbitalis.MODID, value = Dist.CLIENT)
public final class SuitHud {
    /** Largeur des panneaux, marge au bord de l'écran, écart entre deux panneaux. */
    private static final int WIDTH = 124;
    private static final int MARGIN = 8;
    private static final int GAP = 4;
    /** Fond des panneaux : un verre fumé, on voit le monde au travers. */
    private static final int GLASS = 0x9A07090C;

    /** Rayon de la carte, en blocs ; chaque bloc y fait trois pixels de l'interface, pour qu'on lise bien les murs. */
    private static final int MAP_RADIUS = 12;
    private static final int MAP_SIZE = 2 * MAP_RADIUS + 1;
    private static final int MAP_SCALE = 3;
    /** Profondeur jusqu'où la carte cherche le sol sous les pieds, en blocs. */
    private static final int MAP_DEPTH = 10;
    private static final Identifier MAP_ID = Orbitalis.id("suit_map");
    /** Hauteur du panneau de la carte : son titre, la carte, une marge. */
    private static final int MAP_PANEL = 15 + MAP_SIZE * MAP_SCALE + 5;
    /** Hauteurs des panneaux de la survie et du capteur. */
    private static final int LIFE_PANEL = 52;
    private static final int SENSOR_PANEL = 56;

    private static boolean breathable = true;
    /** Ticks écoulés depuis la dernière nouvelle des capteurs : au-delà de trois secondes, elle est périmée. */
    private static int statusAge = 1000;
    private static int clock;

    /** Décalage des panneaux, en pixels de l'interface : leur retard sur le regard. */
    private static float swayX;
    private static float swayY;
    private static float lastYaw;
    private static float lastPitch;
    private static boolean primed;

    private static DynamicTexture map;
    private static int mapClock = -1000;
    private static BlockPos mapCenter = BlockPos.ZERO;
    private static Level mapLevel;

    private SuitHud() {
    }

    public static void onStatus(SuitStatusPayload payload) {
        breathable = payload.breathable();
        statusAge = 0;
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        statusAge++;
        clock++;
    }

    /** Vrai si le joueur respire l'air du dehors là où il se tient (les capteurs, ou à défaut le monde où il est). */
    static boolean breathable(Player player) {
        return statusAge < 60 ? breathable : !SpaceRules.isUnbreathable(player.level());
    }

    @SubscribeEvent
    static void onGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gui.hud.isHidden() || minecraft.gui.screen() != null || !minecraft.options.getCameraType().isFirstPerson()
                || player.getVehicle() instanceof CapsuleEntity || !SpaceSuits.isPiece(player.getItemBySlot(EquipmentSlot.HEAD), EquipmentSlot.HEAD)) {
            primed = false;
            return;
        }
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        Font font = minecraft.font;
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        sway(minecraft, player, partial, minecraft.getDeltaTracker().getRealtimeDeltaTicks());

        // Le pas : les panneaux suivent la tête qui balance, si l'on a gardé le balancement de la vue.
        float bobX = 0.0F;
        float bobY = 0.0F;
        if (minecraft.options.bobView().get()) {
            float walk = player.avatarState().getBackwardsInterpolatedWalkDistance(partial);
            float bob = player.avatarState().getInterpolatedBob(partial);
            bobX = Mth.sin(walk * Mth.PI) * bob * 18.0F;
            bobY = -Math.abs(Mth.cos(walk * Mth.PI) * bob) * 14.0F;
        }
        float offsetX = swayX + bobX;
        float offsetY = swayY + bobY;

        boolean advanced = SpaceSuits.isAdvanced(player.getItemBySlot(EquipmentSlot.HEAD));
        boolean sensor = SpaceSuits.has(player, SuitModule.SENSOR);
        boolean showMap = SpaceSuits.has(player, SuitModule.MAP);

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(offsetX, offsetY);
        drawLife(graphics, font, player, MARGIN, height / 2 - 40, advanced);
        int right = width - MARGIN - WIDTH;
        int column = (sensor ? SENSOR_PANEL : 0) + (showMap ? MAP_PANEL : 0) + (sensor && showMap ? GAP : 0);
        int y = height / 2 - 10 - column / 2;
        if (sensor) {
            drawEnvironment(graphics, font, player, right, y);
            y += SENSOR_PANEL + GAP;
        }
        if (showMap) {
            drawMap(graphics, font, player, right, y, partial);
        }
        pose.popMatrix();
    }

    /**
     * Retard des panneaux sur le regard : chaque degré de rotation les pousse à l'opposé, puis ils reviennent en
     * place en un cinquième de seconde environ. Le calcul se fait à chaque image, d'après le temps réel écoulé.
     */
    private static void sway(Minecraft minecraft, LocalPlayer player, float partial, float frameTicks) {
        float yaw = player.getViewYRot(partial);
        float pitch = player.getViewXRot(partial);
        if (!primed) {
            lastYaw = yaw;
            lastPitch = pitch;
            swayX = 0.0F;
            swayY = 0.0F;
            primed = true;
        }
        float dYaw = Mth.wrapDegrees(yaw - lastYaw);
        float dPitch = pitch - lastPitch;
        lastYaw = yaw;
        lastPitch = pitch;
        float scale = minecraft.options.screenEffectScale().get().floatValue();
        swayX = Mth.clamp(swayX - dYaw * 0.22F * scale, -7.0F, 7.0F);
        swayY = Mth.clamp(swayY - dPitch * 0.16F * scale, -5.0F, 5.0F);
        float settle = (float) Math.exp(-Math.min(frameTicks, 5.0F) * 0.28F);
        swayX *= settle;
        swayY *= settle;
    }

    /** Le verre d'un panneau : un fond fumé, un filet en tête, et deux coins soulignés de sa couleur. */
    private static void glass(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int accent) {
        Ui.round(graphics, x, y, w, h, GLASS);
        graphics.fill(x + 1, y, x + w - 1, y + 1, Ui.alpha(accent, 0.35F));
        int edge = Ui.alpha(accent, 0.9F);
        graphics.fill(x, y, x + 7, y + 1, edge);
        graphics.fill(x, y, x + 1, y + 7, edge);
        graphics.fill(x + w - 7, y + h - 1, x + w, y + h, edge);
        graphics.fill(x + w - 1, y + h - 7, x + w, y + h, edge);
    }

    private static Component key(String name) {
        return Component.translatable("suit.orbitalis.hud." + name);
    }

    private static String upper(Component text) {
        return text.getString().toUpperCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------
    // À gauche : la survie
    // ------------------------------------------------------------------

    private static void drawLife(GuiGraphicsExtractor graphics, Font font, LocalPlayer player, int x, int y, boolean advanced) {
        ItemStack suit = player.getItemBySlot(EquipmentSlot.CHEST);
        boolean hasSuit = SpaceRules.isSuit(suit);
        int left = SpaceRules.oxygen(suit);
        int capacity = Math.max(1, SpaceRules.capacity(suit));
        boolean sealed = SpaceSuits.missing(player) == null;
        boolean outside = breathable(player);
        boolean onTank = !outside;
        boolean low = onTank && left <= 60;
        boolean danger = onTank && (!hasSuit || !sealed || left <= 0);

        // L'état de la réserve : on respire dehors, sur sa réserve, ou elle se recharge.
        String state;
        int stateColor;
        if (danger || low) {
            state = upper(key("state.alert"));
            stateColor = Ui.BAD;
        } else if (onTank) {
            state = upper(key("state.tank"));
            stateColor = Ui.WARN;
        } else if (hasSuit && left < capacity) {
            state = upper(key("state.refill"));
            stateColor = Ui.ACCENT;
        } else {
            state = upper(key("state.ambient"));
            stateColor = Ui.OK;
        }
        // Les alertes clignotent, lentement.
        boolean blink = (danger || low) && clock / 10 % 2 == 0;
        int accent = danger || low ? Ui.BAD : advanced ? Ui.ACCENT : 0xFFFF9A3C;
        glass(graphics, x, y, WIDTH, LIFE_PANEL, blink ? Ui.alpha(accent, 0.5F) : accent);

        int inner = WIDTH - 12;
        String heading = upper(key("life"));
        graphics.text(font, Ui.fit(font, heading, inner - font.width(state) - 6), x + 6, y + 4, Ui.MUTED, false);
        Ui.right(graphics, font, state, x + WIDTH - 6, y + 4, stateColor);

        String time = hasSuit ? "%d:%02d".formatted(left / 60, left % 60) : "--:--";
        float share = hasSuit ? (float) left / capacity : 0.0F;
        int gauge = !hasSuit || share <= 0.1F ? Ui.BAD : share <= 0.3F ? Ui.WARN : Ui.ACCENT;
        Ui.row(graphics, font, key("oxygen"), time, x + 6, y + 15, inner, gauge);
        // La jauge, en segments.
        int segments = 24;
        int lit = Math.round(segments * Mth.clamp(share, 0.0F, 1.0F));
        float step = (float) inner / segments;
        for (int k = 0; k < segments; k++) {
            int x0 = x + 6 + Math.round(k * step);
            int x1 = x + 6 + Math.round((k + 1) * step) - 1;
            graphics.fill(x0, y + 25, x1, y + 28, k < lit ? gauge : Ui.alpha(Ui.LINE_HI, 0.8F));
        }

        // Pression : celle de la combinaison fermée ; ouverte, celle du dehors.
        float pressure = sealed ? SpaceSuits.SUIT_PRESSURE : outsidePressure(player);
        Ui.row(graphics, font, key("pressure"), String.format(Locale.ROOT, "%.1f kPa", pressure), x + 6, y + 31, inner,
                sealed ? Ui.TEXT : onTank ? Ui.BAD : Ui.MUTED);
        Ui.row(graphics, font, key("seal"), upper(key(sealed ? "seal_ok" : "seal_open")), x + 6, y + 41, inner,
                sealed ? Ui.OK : onTank ? Ui.BAD : Ui.WARN);
    }

    // ------------------------------------------------------------------
    // À droite : le capteur d'environnement
    // ------------------------------------------------------------------

    /** Pression de l'air autour du joueur, en kPa : elle baisse avec l'altitude sur la planète. */
    private static float outsidePressure(Player player) {
        Level level = player.level();
        if (breathable(player) && SpaceRules.isUnbreathable(level)) {
            return 101.3F;
        }
        if (SpaceRules.isVacuum(level)) {
            return 0.0F;
        }
        if (SpaceRules.isMars(level)) {
            return 0.6F;
        }
        if (SpaceRules.isTitan(level)) {
            return 146.7F;
        }
        return 101.3F * (float) Math.exp(-(player.getY() - level.getSeaLevel()) / 1200.0);
    }

    private static void drawEnvironment(GuiGraphicsExtractor graphics, Font font, LocalPlayer player, int x, int y) {
        Level level = player.level();
        boolean inside = breathable(player);
        boolean shelter = inside && SpaceRules.isUnbreathable(level);
        glass(graphics, x, y, WIDTH, SENSOR_PANEL, Ui.ACCENT);
        int inner = WIDTH - 12;
        graphics.text(font, Ui.fit(font, upper(key("environment")), inner), x + 6, y + 4, Ui.MUTED, false);

        float pressure = outsidePressure(player);
        Ui.row(graphics, font, key("pressure"), String.format(Locale.ROOT, "%.1f kPa", pressure), x + 6, y + 15, inner,
                pressure < 20.0F ? Ui.BAD : Ui.TEXT);

        String air;
        int airColor;
        if (inside) {
            air = key("air.breathable").getString();
            airColor = Ui.OK;
        } else if (SpaceRules.isMars(level)) {
            air = key("air.co2").getString();
            airColor = Ui.WARN;
        } else if (SpaceRules.isTitan(level)) {
            air = key("air.nitrogen").getString();
            airColor = Ui.WARN;
        } else {
            air = key("air.vacuum").getString();
            airColor = Ui.BAD;
        }
        Ui.row(graphics, font, key("air"), air, x + 6, y + 25, inner, airColor);

        double gravity = SpaceRules.isOrbit(level) ? 0.0 : SpaceRules.gravityOf(level);
        Ui.row(graphics, font, key("gravity"), String.format(Locale.ROOT, "%.2f g", gravity), x + 6, y + 35, inner, Ui.TEXT);

        int celsius;
        if (shelter) {
            celsius = 21;
        } else if (SpaceRules.isOrbit(level)) {
            celsius = StationPower.sunlit(level) ? 121 : -157;
        } else if (SpaceRules.isMoon(level)) {
            celsius = 96;
        } else if (SpaceRules.isMars(level)) {
            celsius = -63;
        } else if (SpaceRules.isTitan(level)) {
            celsius = -179;
        } else if (SpaceRules.isEuropa(level)) {
            celsius = -160;
        } else {
            celsius = Math.round(level.getBiome(player.blockPosition()).value().getBaseTemperature() * 20.0F + 4.0F);
        }
        Ui.row(graphics, font, key("temperature"), celsius + " °C", x + 6, y + 45, inner, celsius > 60 || celsius < -30 ? Ui.WARN : Ui.TEXT);
    }

    // ------------------------------------------------------------------
    // À droite : la carte de proximité
    // ------------------------------------------------------------------

    private static void drawMap(GuiGraphicsExtractor graphics, Font font, LocalPlayer player, int x, int y, float partial) {
        glass(graphics, x, y, WIDTH, MAP_PANEL, Ui.ACCENT);
        int inner = WIDTH - 12;
        String range = MAP_RADIUS + " m";
        graphics.text(font, Ui.fit(font, upper(key("map")), inner - font.width(range) - 6), x + 6, y + 4, Ui.MUTED, false);
        Ui.right(graphics, font, range, x + WIDTH - 6, y + 4, Ui.MUTED);

        paintMap(player);
        int size = MAP_SIZE * MAP_SCALE;
        int mapX = x + (WIDTH - size) / 2;
        int mapY = y + 15;
        graphics.blit(RenderPipelines.GUI_TEXTURED, MAP_ID, mapX, mapY, 0.0F, 0.0F, size, size, MAP_SIZE, MAP_SIZE, MAP_SIZE, MAP_SIZE, 0xFFFFFFFF);
        int cx = mapX + size / 2;
        int cy = mapY + size / 2;
        // Le nord, en haut.
        graphics.text(font, "N", cx - font.width("N") / 2, mapY - 3, Ui.MUTED, false);

        // Les autres : les joueurs en blanc, les créatures hostiles en rouge, les autres en ambre.
        double px = Mth.lerp(partial, player.xo, player.getX());
        double pz = Mth.lerp(partial, player.zo, player.getZ());
        for (Entity entity : player.level().getEntities(player, player.getBoundingBox().inflate(MAP_RADIUS, 8.0, MAP_RADIUS))) {
            if (!(entity instanceof net.minecraft.world.entity.LivingEntity) || entity.isInvisible()) {
                continue;
            }
            double dx = Mth.lerp(partial, entity.xo, entity.getX()) - px;
            double dz = Mth.lerp(partial, entity.zo, entity.getZ()) - pz;
            if (dx * dx + dz * dz > (MAP_RADIUS - 1) * (MAP_RADIUS - 1)) {
                continue;
            }
            int color = entity instanceof Player ? Ui.TEXT : entity instanceof Enemy ? Ui.BAD : Ui.WARN;
            int ex = cx + (int) Math.round(dx * MAP_SCALE);
            int ez = cy + (int) Math.round(dz * MAP_SCALE);
            graphics.fill(ex - 2, ez - 2, ex + 2, ez + 2, 0xC0000000);
            graphics.fill(ex - 1, ez - 1, ex + 1, ez + 1, color);
        }

        // Soi-même : une flèche au centre, tournée comme le regard.
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.rotate((player.getViewYRot(partial) + 180.0F) * Mth.DEG_TO_RAD);
        graphics.fill(-2, -6, 2, 3, 0xC0000000);
        graphics.fill(-4, -2, 4, 4, 0xC0000000);
        graphics.fill(-1, -5, 1, -2, Ui.WARN);
        graphics.fill(-2, -2, 2, 1, Ui.WARN);
        graphics.fill(-3, 1, -1, 3, Ui.WARN);
        graphics.fill(1, 1, 3, 3, Ui.WARN);
        pose.popMatrix();
    }

    /**
     * Peint la carte, deux fois par seconde ou dès que l'on a changé de bloc : vue d'en haut, en un seul ton de bleu.
     * Les murs (un bloc plein à hauteur du corps) sont clairs ; les sols, plus sombres à mesure qu'ils sont bas ; le
     * vide reste presque transparent. Un disque, cerclé d'un filet.
     */
    private static void paintMap(LocalPlayer player) {
        if (map == null) {
            map = new DynamicTexture(() -> "Orbitalis suit map", MAP_SIZE, MAP_SIZE, false);
            Minecraft.getInstance().getTextureManager().register(MAP_ID, map);
        }
        BlockPos center = player.blockPosition();
        Level level = player.level();
        if (clock - mapClock < 10 && center.equals(mapCenter) && level == mapLevel) {
            return;
        }
        mapClock = clock;
        mapCenter = center;
        mapLevel = level;
        NativeImage image = map.getPixels();
        if (image == null) {
            return;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int feet = center.getY();
        int[] kinds = new int[MAP_SIZE * MAP_SIZE];
        for (int dz = -MAP_RADIUS; dz <= MAP_RADIUS; dz++) {
            for (int dx = -MAP_RADIUS; dx <= MAP_RADIUS; dx++) {
                kinds[(dz + MAP_RADIUS) * MAP_SIZE + dx + MAP_RADIUS] = column(level, cursor, center.getX() + dx, feet, center.getZ() + dz);
            }
        }
        for (int dz = -MAP_RADIUS; dz <= MAP_RADIUS; dz++) {
            for (int dx = -MAP_RADIUS; dx <= MAP_RADIUS; dx++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                int color;
                if (distance > MAP_RADIUS + 0.5) {
                    color = 0;
                } else if (distance > MAP_RADIUS - 0.5) {
                    color = 0xFF6B7682;
                } else {
                    int kind = kinds[(dz + MAP_RADIUS) * MAP_SIZE + dx + MAP_RADIUS];
                    // Un mur au nord ou à l'ouest jette son ombre sur la case : les volumes se lisent d'un coup d'œil.
                    boolean shaded = kind != WALL && (isWall(kinds, dx, dz - 1) || isWall(kinds, dx - 1, dz));
                    color = colour(kind, shaded);
                }
                image.setPixel(dx + MAP_RADIUS, dz + MAP_RADIUS, color);
            }
        }
        map.upload();
    }

    /** Les natures des cases de la carte : un mur, rien, une surface d'eau ; sinon la profondeur du sol sous les pieds. */
    private static final int WALL = -1;
    private static final int VOID = -2;
    private static final int WATER = -3;

    private static boolean isWall(int[] kinds, int dx, int dz) {
        if (Math.abs(dx) > MAP_RADIUS || Math.abs(dz) > MAP_RADIUS) {
            return false;
        }
        return kinds[(dz + MAP_RADIUS) * MAP_SIZE + dx + MAP_RADIUS] == WALL;
    }

    /** Couleur d'une case : les murs presque blancs, les sols bleu acier de plus en plus sombres en descendant, l'eau bleue. */
    private static int colour(int kind, boolean shaded) {
        if (kind == WALL) {
            return 0xFFEAF4F9;
        }
        if (kind == VOID) {
            return 0x70040608;
        }
        int r;
        int g;
        int b;
        if (kind == WATER) {
            r = 0x2A;
            g = 0x68;
            b = 0xC8;
        } else {
            float t = Math.min(1.0F, kind / (float) (MAP_DEPTH - 1));
            r = Math.round(Mth.lerp(t, 0x46, 0x14));
            g = Math.round(Mth.lerp(t, 0x86, 0x2E));
            b = Math.round(Mth.lerp(t, 0xA8, 0x40));
        }
        if (shaded) {
            r = r * 6 / 10;
            g = g * 6 / 10;
            b = b * 6 / 10;
        }
        return 0xF0000000 | r << 16 | g << 8 | b;
    }

    /** La nature d'une colonne de la carte : mur, vide, eau, ou la profondeur du sol sous les pieds. */
    private static int column(Level level, BlockPos.MutableBlockPos cursor, int x, int feet, int z) {
        if (!level.isLoaded(cursor.set(x, feet, z))) {
            return VOID;
        }
        if (solid(level.getBlockState(cursor.set(x, feet, z))) || solid(level.getBlockState(cursor.set(x, feet + 1, z)))) {
            return WALL;
        }
        for (int depth = 0; depth < MAP_DEPTH; depth++) {
            BlockState state = level.getBlockState(cursor.set(x, feet - 1 - depth, z));
            if (solid(state)) {
                return state.getFluidState().isEmpty() ? depth : WATER;
            }
        }
        return VOID;
    }

    private static boolean solid(BlockState state) {
        return !state.isAir() && (state.blocksMotion() || !state.getFluidState().isEmpty());
    }
}
