package com.orbitalis.client.screen;

import com.orbitalis.core.network.StationRequestPayload;
import com.orbitalis.core.network.StationStatusPayload;
import com.orbitalis.phase2.station.LabRackBlockEntity;
import com.orbitalis.phase2.station.ModuleType;
import com.orbitalis.phase2.station.StationPower;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Tableau de bord d'une station, sur son terminal : le plan de ses modules, et six cartes (énergie, support de vie,
 * laboratoire, serre, équipage, logistique). L'en-tête montre l'orbite : la part passée au soleil, l'éclipse, et
 * où en est la station. L'écran s'actualise chaque seconde.
 */
public class StationScreen extends Screen {
    private static final int HEADER = 30;
    private static final int GAP = 6;
    private static final int REFRESH_TICKS = 20;

    private StationStatusPayload status;
    private int panelWidth;
    private int panelHeight;
    private int left;
    private int top;
    private int age;
    private int mouseX;
    private int mouseY;
    /** Ticks écoulés depuis le dernier relevé : l'orbite avance entre deux relevés. */
    private int sinceUpdate;

    public StationScreen(StationStatusPayload status) {
        super(Component.translatable("station.orbitalis.screen.title"));
        this.status = status;
    }

    /** Le serveur envoie l'état de la station : ouverture de l'écran, ou actualisation de celui qui est ouvert. */
    public static void accept(StationStatusPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (payload.open()) {
            minecraft.gui.setScreen(new StationScreen(payload));
        } else if (minecraft.gui.screen() instanceof StationScreen screen && screen.status.terminal().equals(payload.terminal())) {
            screen.status = payload;
            screen.sinceUpdate = 0;
        }
    }

    @Override
    protected void init() {
        panelWidth = Math.min(width - 16, 560);
        panelHeight = Math.min(height - 16, 320);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
    }

    @Override
    public void tick() {
        super.tick();
        sinceUpdate++;
        if (++age % REFRESH_TICKS == 0) {
            ClientPacketDistributor.sendToServer(new StationRequestPayload(status.terminal()));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        Ui.panel(graphics, left, top, panelWidth, panelHeight);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        extractHeader(graphics);
        Ui.hline(graphics, left + 1, top + HEADER, panelWidth - 2);

        int bodyTop = top + HEADER + GAP;
        int bodyHeight = top + panelHeight - GAP - bodyTop;
        int planWidth = Math.max(120, (panelWidth - 3 * GAP) * 36 / 100);
        extractPlan(graphics, left + GAP, bodyTop, planWidth, bodyHeight);

        int gridLeft = left + 2 * GAP + planWidth;
        int gridWidth = left + panelWidth - GAP - gridLeft;
        int cardWidth = (gridWidth - GAP) / 2;
        int cardHeight = (bodyHeight - 2 * GAP) / 3;
        for (int k = 0; k < 6; k++) {
            int x = gridLeft + (k % 2) * (cardWidth + GAP);
            int y = bodyTop + (k / 2) * (cardHeight + GAP);
            Ui.card(graphics, x, y, cardWidth, cardHeight, false);
            extractCard(graphics, k, x + 6, y + 6, cardWidth - 12, cardHeight - 12);
        }
    }

    // ------------------------------------------------------------------
    // En-tête : le titre, l'orbite et l'air
    // ------------------------------------------------------------------

    private void extractHeader(GuiGraphicsExtractor graphics) {
        int x = left + 12;
        graphics.text(font, title.copy().withStyle(ChatFormatting.BOLD), x, top + 7, Ui.TEXT, false);
        String where = Component.translatable("station.orbitalis.screen.where", status.station().getX(), status.station().getY(),
                status.station().getZ()).getString();
        int titleWidth = font.width(title.copy().withStyle(ChatFormatting.BOLD));

        // À droite : l'état de l'air, puis la frise de l'orbite et son décompte.
        int right = left + panelWidth - 12;
        StationStatusPayload.Air air = status.air();
        String airLabel = Component.translatable(air.working() > 0 ? "station.orbitalis.screen.pressurized"
                : air.leaks() > 0 ? "station.orbitalis.screen.leak" : "station.orbitalis.screen.no_air").getString();
        int airColor = air.working() > 0 ? Ui.OK : air.leaks() > 0 ? Ui.BAD : Ui.DIM;
        Ui.pillRight(graphics, font, airLabel, right, top + 6, airColor);
        right -= Ui.pillWidth(font, airLabel) + 6;

        StationStatusPayload.Power power = status.power();
        int switchIn = Math.max(0, power.switchIn() - sinceUpdate);
        String clock = clock(switchIn);
        String orbit = Component.translatable(power.sunlit() ? "station.orbitalis.screen.next_eclipse" : "station.orbitalis.screen.next_sun",
                clock).getString();
        String sun = Component.translatable(power.sunlit() ? "station.orbitalis.screen.sunlit" : "station.orbitalis.screen.eclipse").getString();
        Ui.pillRight(graphics, font, sun, right, top + 6, power.sunlit() ? Ui.WARN : Ui.ACCENT);
        int sunRight = right;
        right -= Ui.pillWidth(font, sun) + 6;

        // La frise de l'orbite, sous les étiquettes, calée à droite : le soleil en ambre, l'ombre en bleu nuit, la
        // station en blanc. Son décompte la précède ; les coordonnées, à gauche, gardent leur place.
        int barRight = left + panelWidth - 12;
        int barWidth = Math.min(120, barRight - x - font.width(where) - font.width(orbit) - 24);
        if (barWidth >= 40) {
            int barX = barRight - barWidth;
            int barY = top + 21;
            int sunlitWidth = (int) Math.round(barWidth * (1.0 - StationPower.ECLIPSE_SHARE));
            graphics.fill(barX, barY, barX + sunlitWidth, barY + 3, Ui.alpha(Ui.WARN, 0.75F));
            graphics.fill(barX + sunlitWidth, barY, barX + barWidth, barY + 3, 0xFF1F3F8F);
            double phase = phase(power, switchIn);
            int marker = barX + (int) Math.round(phase * (barWidth - 1));
            graphics.fill(marker - 1, barY - 2, marker + 2, barY + 5, Ui.TEXT);
            String label = Ui.fit(font, orbit, barWidth + 40);
            graphics.text(font, label, barX - font.width(label) - 6, barY - 2, Ui.MUTED, false);
            graphics.text(font, Ui.fit(font, where, barX - font.width(label) - 18 - x), x, top + 19, Ui.DIM, false);
        } else {
            graphics.text(font, Ui.fit(font, orbit, sunRight - x - titleWidth - 12), x + titleWidth + 10, top + 7, Ui.MUTED, false);
            graphics.text(font, Ui.fit(font, where, panelWidth - 24), x, top + 19, Ui.DIM, false);
        }
    }

    /** Où en est la station sur son orbite, de 0 (sortie de l'ombre) à 1. */
    private static double phase(StationStatusPayload.Power power, int switchIn) {
        double period = StationPower.ORBIT_PERIOD;
        double sunlit = Math.round(period * (1.0 - StationPower.ECLIPSE_SHARE));
        double ticks = power.sunlit() ? sunlit - switchIn : period - switchIn;
        return Math.max(0.0, Math.min(1.0, ticks / period));
    }

    private static String clock(int ticks) {
        int seconds = (ticks + 19) / 20;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    // ------------------------------------------------------------------
    // Le plan
    // ------------------------------------------------------------------

    /** Un module à l'écran : son type, son corps (en points) et son axe (vrai : est-ouest). */
    private record Body(ModuleType type, StationStatusPayload.Module module, float x0, float y0, float x1, float y1, boolean alongX) {
    }

    /**
     * Le plan de la station, comme un schéma technique : sur un fond quadrillé, chaque module habitable est un cylindre
     * aux bouts arrondis, couché selon son axe, de la couleur de son rôle, avec son nom quand il tient dedans ; les
     * passages relient les modules ; la poutre d'énergie est un trait qui porte ses deux ailes de panneaux. Le nord est
     * en haut. Le terminal palpite là où l'on est ; le module survolé donne son nom en tête du plan. En bas, le compte
     * des modules par rôle.
     */
    private void extractPlan(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        Ui.card(graphics, x, y, w, h, false);
        List<StationStatusPayload.Module> modules = status.modules();
        Ui.heading(graphics, font, Component.translatable("station.orbitalis.screen.plan", modules.size()), x + 6, y + 6);

        // Le compte des modules par rôle, en pastilles, sur une ou deux lignes en bas.
        Map<ModuleType, Integer> counts = new EnumMap<>(ModuleType.class);
        for (StationStatusPayload.Module module : modules) {
            counts.merge(type(module), 1, Integer::sum);
        }
        List<String> chips = new ArrayList<>();
        List<Integer> chipColors = new ArrayList<>();
        for (Map.Entry<ModuleType, Integer> entry : counts.entrySet()) {
            chips.add(Component.translatable(entry.getKey().nameKey() + ".short").getString() + " " + entry.getValue());
            chipColors.add(0xFF000000 | entry.getKey().color());
        }
        int chipRows = 1;
        int lineWidth = 0;
        for (String chip : chips) {
            int cw = font.width(chip) + 14;
            if (lineWidth > 0 && lineWidth + cw > w - 12) {
                chipRows++;
                lineWidth = 0;
            }
            lineWidth += cw + 4;
        }
        int chipTop = y + h - 6 - chipRows * 13 + 2;
        int planTop = y + 20;
        int planBottom = chipTop - 6;
        int planLeft = x + 8;
        int planRight = x + w - 8;

        // Le fond du schéma : un quadrillage de points, comme une feuille de plan.
        for (int gy = planTop; gy < planBottom; gy += 6) {
            for (int gx = planLeft; gx < planRight; gx += 6) {
                graphics.fill(gx, gy, gx + 1, gy + 1, Ui.alpha(Ui.LINE_HI, 0.5F));
            }
        }
        // La boussole : le nord en haut.
        Ui.right(graphics, font, "N", x + w - 7, y + 6, Ui.MUTED);
        graphics.fill(x + w - 7 - font.width("N") / 2 - 1, y + 15, x + w - 7 - font.width("N") / 2, y + 18, Ui.MUTED);

        if (!modules.isEmpty() && planBottom - planTop > 16) {
            float minX = Float.MAX_VALUE;
            float minZ = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float maxZ = -Float.MAX_VALUE;
            for (StationStatusPayload.Module module : modules) {
                minX = Math.min(minX, module.x0());
                minZ = Math.min(minZ, module.z0());
                maxX = Math.max(maxX, module.x1() + 1);
                maxZ = Math.max(maxZ, module.z1() + 1);
            }
            float scale = Math.min((planRight - planLeft) / (maxX - minX), (planBottom - planTop) / (maxZ - minZ));
            scale = Math.min(scale, 9.0F);
            float ox = planLeft + ((planRight - planLeft) - (maxX - minX) * scale) / 2.0F - minX * scale;
            float oy = planTop + ((planBottom - planTop) - (maxZ - minZ) * scale) / 2.0F - minZ * scale;

            // Les corps des modules habitables, en retrait de leurs parois (deux voisins ne se touchent pas) ;
            // les poutres d'énergie, à part.
            List<Body> bodies = new ArrayList<>();
            for (StationStatusPayload.Module module : modules) {
                ModuleType type = type(module);
                // L'axe d'un module : sa longueur. Celui de la poutre d'énergie : le sens de la poutre, plus court que ses ailes.
                boolean longX = module.x1() - module.x0() >= module.z1() - module.z0();
                boolean alongX = type == ModuleType.POWER ? !longX : longX;
                float inset = 0.75F;
                bodies.add(new Body(type, module, ox + (module.x0() + inset) * scale, oy + (module.z0() + inset) * scale,
                        ox + (module.x1() + 1 - inset) * scale, oy + (module.z1() + 1 - inset) * scale, alongX));
            }
            // Les passages, puis les poutres, puis les modules, puis leurs noms.
            for (Body body : bodies) {
                if (body.type() != ModuleType.CORE) {
                    connector(graphics, body, bodies, scale);
                }
            }
            for (Body body : bodies) {
                if (body.type() == ModuleType.POWER) {
                    truss(graphics, body, bodies, scale);
                }
            }
            Body hovered = null;
            for (Body body : bodies) {
                if (body.type().pressurized()) {
                    cylinder(graphics, body);
                    if (mouseX >= body.x0() && mouseX < body.x1() && mouseY >= body.y0() && mouseY < body.y1()) {
                        hovered = body;
                    }
                } else if (mouseX >= body.x0() && mouseX < body.x1() && mouseY >= body.y0() && mouseY < body.y1()) {
                    hovered = body;
                }
            }
            for (Body body : bodies) {
                if (body.type().pressurized()) {
                    String label = Component.translatable(body.type().nameKey() + ".short").getString();
                    float bw = body.x1() - body.x0();
                    float bh = body.y1() - body.y0();
                    if (font.width(label) + 6 <= bw && bh >= 11) {
                        graphics.text(font, label, Math.round((body.x0() + body.x1() - font.width(label)) / 2.0F),
                                Math.round((body.y0() + body.y1()) / 2.0F - 4), Ui.TEXT, false);
                    }
                }
            }
            // Le terminal : « vous êtes ici », un anneau qui palpite.
            float tx = ox + (status.terminal().getX() - status.station().getX() + 0.5F) * scale;
            float tz = oy + (status.terminal().getZ() - status.station().getZ() + 0.5F) * scale;
            int ring = 2 + (age / 6) % 3;
            Ui.frame(graphics, Math.round(tx) - ring, Math.round(tz) - ring, ring * 2 + 1, ring * 2 + 1, Ui.alpha(Ui.ACCENT, 0.9F - 0.25F * (ring - 2)));
            Ui.dot(graphics, Math.round(tx) - 2, Math.round(tz) - 2, Ui.TEXT);
            // Le module survolé : son nom en tête du plan.
            if (hovered != null) {
                String name = Ui.fit(font, Component.translatable(hovered.type().nameKey()).getString(), w / 2);
                Ui.right(graphics, font, name, x + w - 16, y + 6, 0xFF000000 | hovered.type().color());
            }
        }

        // Les pastilles du compte des modules.
        int cx = x + 6;
        int cy = chipTop;
        for (int k = 0; k < chips.size(); k++) {
            int cw = font.width(chips.get(k)) + 14;
            if (cx > x + 6 && cx + cw > x + w - 6) {
                cx = x + 6;
                cy += 13;
            }
            Ui.round(graphics, cx, cy, cw, 11, Ui.alpha(chipColors.get(k), 0.14F));
            Ui.dot(graphics, cx + 4, cy + 3, chipColors.get(k));
            graphics.text(font, chips.get(k), cx + 11, cy + 2, Ui.TEXT, false);
            cx += cw + 4;
        }
    }

    private static ModuleType type(StationStatusPayload.Module module) {
        return ModuleType.values()[Math.floorMod(module.type(), ModuleType.values().length)];
    }

    /**
     * Un module habitable : un cylindre vu de dessus, aux bouts arrondis. Un fond sombre teinté de sa couleur, un contour
     * de sa couleur, et un filet de lumière le long de son axe, comme le reflet sur une coque ronde.
     */
    private static void cylinder(GuiGraphicsExtractor graphics, Body body) {
        int x0 = Math.round(body.x0());
        int y0 = Math.round(body.y0());
        int x1 = Math.round(body.x1());
        int y1 = Math.round(body.y1());
        int color = 0xFF000000 | body.type().color();
        boolean core = body.type() == ModuleType.CORE;
        // Le module central est un bloc carré aux angles adoucis ; les autres, des cylindres.
        float radius = core ? Math.min(x1 - x0, y1 - y0) * 0.22F : Math.min(x1 - x0, y1 - y0) / 2.0F;
        rounded(graphics, x0, y0, x1, y1, radius, color);
        rounded(graphics, x0 + 1, y0 + 1, x1 - 1, y1 - 1, Math.max(0.0F, radius - 1.0F), mix(0xFF0F1318, color, core ? 0.32F : 0.2F));
        if (body.alongX()) {
            int mid = (y0 + y1) / 2 - Math.max(1, (y1 - y0) / 5);
            graphics.fill(x0 + Math.round(radius), mid, x1 - Math.round(radius), mid + 1, Ui.alpha(color, 0.45F));
        } else {
            int mid = (x0 + x1) / 2 - Math.max(1, (x1 - x0) / 5);
            graphics.fill(mid, y0 + Math.round(radius), mid + 1, y1 - Math.round(radius), Ui.alpha(color, 0.45F));
        }
    }

    /** Rectangle aux angles arrondis de ce rayon (des demi-cercles aux bouts, s'il vaut la moitié de l'épaisseur). */
    private static void rounded(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, float radius, int color) {
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        for (int yy = y0; yy < y1; yy++) {
            float fromEdge = Math.min(yy - y0 + 0.5F, y1 - yy - 0.5F);
            int inset = 0;
            if (fromEdge < radius) {
                float d = radius - fromEdge;
                inset = Math.round(radius - (float) Math.sqrt(Math.max(0.0F, radius * radius - d * d)));
            }
            if (x0 + inset < x1 - inset) {
                graphics.fill(x0 + inset, yy, x1 - inset, yy + 1, color);
            }
        }
    }

    /** Le côté par lequel un module tient à un autre : la tranche de son emprise qui chevauche celle d'un voisin. */
    private static Body parentOf(Body body, List<Body> bodies, boolean atMin) {
        StationStatusPayload.Module m = body.module();
        int x0 = body.alongX() ? (atMin ? m.x0() : m.x1()) : m.x0();
        int x1 = body.alongX() ? (atMin ? m.x0() : m.x1()) : m.x1();
        int z0 = body.alongX() ? m.z0() : (atMin ? m.z0() : m.z1());
        int z1 = body.alongX() ? m.z1() : (atMin ? m.z0() : m.z1());
        for (Body other : bodies) {
            if (other == body || !other.type().pressurized()) {
                continue;
            }
            StationStatusPayload.Module o = other.module();
            if (o.x0() <= x1 && x0 <= o.x1() && o.z0() <= z1 && z0 <= o.z1()) {
                return other;
            }
        }
        return null;
    }

    /** Le passage entre un module et celui auquel il tient : un court manchon clair qui franchit l'interstice. */
    private static void connector(GuiGraphicsExtractor graphics, Body body, List<Body> bodies, float scale) {
        for (boolean atMin : new boolean[] {true, false}) {
            Body parent = parentOf(body, bodies, atMin);
            if (parent == null) {
                continue;
            }
            int color = Ui.alpha(Ui.MUTED, 0.85F);
            float half = Math.max(1.5F, scale * 0.6F);
            if (body.alongX()) {
                float cy = (body.y0() + body.y1()) / 2.0F;
                float from = atMin ? parent.x1() - 1 : body.x1() - 1;
                float to = atMin ? body.x0() + 1 : parent.x0() + 1;
                graphics.fill(Math.round(Math.min(from, to)), Math.round(cy - half), Math.round(Math.max(from, to)), Math.round(cy + half), color);
            } else {
                float cx = (body.x0() + body.x1()) / 2.0F;
                float from = atMin ? parent.y1() - 1 : body.y1() - 1;
                float to = atMin ? body.y0() + 1 : parent.y0() + 1;
                graphics.fill(Math.round(cx - half), Math.round(Math.min(from, to)), Math.round(cx + half), Math.round(Math.max(from, to)), color);
            }
            return;
        }
    }

    /**
     * La poutre d'énergie : un trait d'acier depuis la paroi où elle s'attache, et au bout deux ailes de panneaux solaires
     * en travers, bleu nuit striées de leurs cellules.
     */
    private static void truss(GuiGraphicsExtractor graphics, Body body, List<Body> bodies, float scale) {
        StationStatusPayload.Module m = body.module();
        // Le sens de la poutre : elle part du côté où elle touche un module, vers l'autre bout.
        boolean fromMin = parentOf(body, bodies, true) != null || parentOf(body, bodies, false) == null;
        float thick = Math.max(1.0F, scale * 0.35F);
        int steel = 0xFFAEB6BF;
        int panel = 0xFF1F3F8F;
        int cell = 0xFF3F76E4;
        if (body.alongX()) {
            float cy = (body.y0() + body.y1()) / 2.0F;
            graphics.fill(Math.round(body.x0()), Math.round(cy - thick / 2), Math.round(body.x1()), Math.round(cy + thick / 2), steel);
            float wingX = fromMin ? body.x1() - scale * 1.1F : body.x0() + scale * 0.1F;
            wing(graphics, Math.round(wingX), Math.round(body.y0()), Math.round(wingX + scale), Math.round(body.y1()), panel, cell, false);
        } else {
            float cx = (body.x0() + body.x1()) / 2.0F;
            graphics.fill(Math.round(cx - thick / 2), Math.round(body.y0()), Math.round(cx + thick / 2), Math.round(body.y1()), steel);
            float wingY = fromMin ? body.y1() - scale * 1.1F : body.y0() + scale * 0.1F;
            wing(graphics, Math.round(body.x0()), Math.round(wingY), Math.round(body.x1()), Math.round(wingY + scale), panel, cell, true);
        }
    }

    /** Une aile de panneaux solaires : un bleu nuit, strié de ses cellules en travers de sa longueur. */
    private static void wing(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int panel, int cell, boolean alongX) {
        graphics.fill(x0, y0, x1, y1, panel);
        if (alongX) {
            for (int xx = x0 + 1; xx < x1 - 1; xx += 3) {
                graphics.fill(xx, y0 + 1, xx + 1, y1 - 1, cell);
            }
        } else {
            for (int yy = y0 + 1; yy < y1 - 1; yy += 3) {
                graphics.fill(x0 + 1, yy, x1 - 1, yy + 1, cell);
            }
        }
        Ui.frame(graphics, x0, y0, x1 - x0, y1 - y0, 0xFF6B7682);
    }

    /** Mélange de deux couleurs opaques : 0 donne la première, 1 la seconde. */
    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    // ------------------------------------------------------------------
    // Les cartes
    // ------------------------------------------------------------------

    private void extractCard(GuiGraphicsExtractor graphics, int card, int x, int y, int w, int h) {
        String[] titles = {"power", "air", "lab", "greenhouse", "crew", "logistics"};
        Ui.heading(graphics, font, Component.translatable("station.orbitalis.screen." + titles[card]), x, y);
        int rows = Math.max(1, (h - 12) / 10);
        List<Row> list = rows(card);
        for (int k = 0; k < list.size() && k < rows; k++) {
            Row row = list.get(k);
            int ry = y + 12 + k * 10;
            if (row.bar() >= 0) {
                Ui.bar(graphics, x, ry + 3, w, 3, row.bar(), row.color());
            } else if (row.name() == null) {
                graphics.text(font, Ui.fit(font, row.value(), w), x, ry, row.color(), false);
            } else {
                Ui.row(graphics, font, row.name(), row.value(), x, ry, w, row.color());
            }
        }
    }

    /** Une ligne de carte : un nom et une valeur, une valeur seule (nom nul), ou une jauge (part de 0 à 1). */
    private record Row(Component name, String value, int color, float bar) {
        static Row of(String key, String value, int color) {
            return new Row(Component.translatable(key), value, color, -1.0F);
        }

        static Row text(String value, int color) {
            return new Row(null, value, color, -1.0F);
        }

        static Row gauge(float share, int color) {
            return new Row(null, "", color, share);
        }
    }

    private List<Row> rows(int card) {
        String k = "station.orbitalis.screen.";
        List<Row> rows = new ArrayList<>();
        switch (card) {
            case 0 -> {
                StationStatusPayload.Power p = status.power();
                int balance = p.produced() - p.consumed();
                rows.add(Row.of(k + "produced", p.produced() + " FE/t", p.produced() > 0 ? Ui.OK : Ui.DIM));
                rows.add(Row.of(k + "consumed", p.consumed() + " FE/t", balance >= 0 ? Ui.TEXT : Ui.WARN));
                float share = p.capacity() <= 0 ? 0.0F : (float) p.stored() / p.capacity();
                rows.add(Row.of(k + "stored", Math.round(share * 100) + " %", share > 0.25F ? Ui.TEXT : Ui.WARN));
                rows.add(Row.gauge(share, share > 0.25F ? Ui.OK : Ui.WARN));
                rows.add(Row.of(k + "batteries", String.valueOf(p.batteries()), p.batteries() > 0 ? Ui.TEXT : Ui.DIM));
            }
            case 1 -> {
                StationStatusPayload.Air a = status.air();
                rows.add(Row.of(k + "volume", a.volume() + " m³", a.working() > 0 ? Ui.OK : Ui.DIM));
                rows.add(Row.of(k + "generators", a.working() + "/" + a.generators(), a.leaks() > 0 ? Ui.BAD : a.working() > 0 ? Ui.TEXT : Ui.DIM));
                rows.add(Row.of(k + "recycling", a.plants() > 0 ? "−" + Math.min(14, a.plants() * 2) + " FE/t" : "—", a.plants() > 0 ? Ui.OK : Ui.DIM));
                if (a.leaks() > 0) {
                    rows.add(Row.text(Component.translatable(k + "leak_hint").getString(), Ui.BAD));
                }
            }
            case 2 -> {
                StationStatusPayload.Lab l = status.lab();
                rows.add(Row.of(k + "racks", l.running() + "/" + l.racks(), l.running() > 0 ? Ui.OK : Ui.DIM));
                rows.add(Row.of(k + "experiments", String.valueOf(l.experiments()), Ui.TEXT));
                int perHour = l.running() * LabRackBlockEntity.POINTS_PER_EXPERIMENT * 72_000 / LabRackBlockEntity.EXPERIMENT_TICKS;
                rows.add(Row.of(k + "research", Component.translatable(k + "per_hour", "+" + perHour).getString(), perHour > 0 ? Ui.ACCENT : Ui.DIM));
            }
            case 3 -> {
                StationStatusPayload.Lab l = status.lab();
                rows.add(Row.of(k + "trays", l.planted() + "/" + l.trays(), l.planted() > 0 ? Ui.OK : Ui.DIM));
                rows.add(Row.of(k + "ripe", String.valueOf(l.ripe()), l.ripe() > 0 ? Ui.WARN : Ui.DIM));
                rows.add(Row.gauge(l.trays() == 0 ? 0.0F : (float) l.planted() / l.trays(), Ui.OK));
            }
            case 4 -> {
                rows.add(Row.of(k + "aboard", String.valueOf(status.crew().size()), status.crew().isEmpty() ? Ui.DIM : Ui.OK));
                rows.add(Row.of(k + "bunks", String.valueOf(status.bunks()), status.bunks() > 0 ? Ui.TEXT : Ui.DIM));
                if (!status.crew().isEmpty()) {
                    rows.add(Row.text(String.join(", ", status.crew()), Ui.MUTED));
                }
            }
            default -> {
                int[] cargo = status.cargo();
                rows.add(Row.of(k + "cargo", String.valueOf(cargo[0]), cargo[0] > 0 ? Ui.TEXT : Ui.DIM));
                rows.add(Row.of(k + "slots", cargo[1] + "/" + cargo[0] * 27, Ui.TEXT));
                rows.add(Row.of(k + "capsules", String.valueOf(status.capsules()), status.capsules() > 0 ? Ui.OK : Ui.DIM));
            }
        }
        return rows;
    }
}
