package com.orbitalis.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.orbitalis.client.render.StationGlobe;
import com.orbitalis.core.network.ProspectPayload;
import com.orbitalis.core.network.SatelliteCommandPayload;
import com.orbitalis.core.network.SatelliteStatusPayload;
import com.orbitalis.phase1.satellite.AgencySatellites;
import com.orbitalis.phase1.satellite.Orbit;
import com.orbitalis.phase1.satellite.ProspectOre;
import com.orbitalis.phase1.satellite.SatelliteType;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Écran d'une station de poursuite : le radar de l'agence.
 * <p>
 * À gauche, la carte des orbites, en 3D : la planète en cubes, bâtie avec le relevé de la planète entière, les quatre
 * orbites de travail, inclinées comme les vraies, et chaque satellite en service, de la couleur de son type, qui tourne
 * à son rythme (un satellite en orbite basse fait quinze tours pendant qu'un géostationnaire en fait un). On fait
 * tourner le tout à la souris, dans tous les sens ; la planète tourne d'elle-même au rythme du géostationnaire, qui
 * reste ainsi au-dessus du même point. À droite, la flotte : la liste des satellites, puis la fiche de celui qu'on a choisi,
 * en cliquant sur sa ligne ou sur son point de la carte : à quoi il sert, son orbite, son âge, ce qu'il lui reste à
 * vivre, ce qu'il produit et ce qu'il garde en mémoire. En bas, la liaison, la météo et la réception des données.
 * <p>
 * Un second onglet, « Prospection », montre ce que voient les satellites d'observation : le sous-sol autour de la
 * station, tronçon par tronçon, avec les minerais qu'il contient. On choisit un minerai dans la liste pour ne voir
 * que lui ; la case sous la souris donne ses coordonnées et son contenu.
 */
public class TrackingStationScreen extends Screen implements BlockScreen {
    /** Hauteur de la fiche d'un satellite quand elle a toute sa place. */
    private static final int DETAIL_HEIGHT = 112;
    private static final int HEADER = 30;
    private static final int FOOTER = 34;
    private static final int MAP_X = 10;
    private static final int MAP_WIDTH = 206;
    private static final int SIDE_X = 224;
    private static final int ROW_HEIGHT = 15;
    private static final int BUTTON_WIDTH = 132;
    private static final int BUTTON_HEIGHT = 18;

    /** Intervalle entre deux actualisations demandées au serveur, en ticks. */
    private static final int REFRESH_TICKS = 40;
    /** Durée, à l'écran, d'une minute d'orbite réelle, en ticks : l'orbite basse se boucle en dix-huit secondes. */
    private static final float TICKS_PER_ORBIT_MINUTE = 4.0F;

    private SatelliteStatusPayload status;
    /** Taille du panneau, selon la fenêtre, et nombre de lignes que la liste peut montrer. */
    private int panelWidth = 440;
    private int panelHeight = 260;
    private int sideWidth = 206;
    private int listRows = 3;
    private int left;
    private int top;
    private int age;
    /** Numéro du satellite choisi ; -1 tant qu'on n'en a choisi aucun (la fiche montre alors le plus récent). */
    private int selected = -1;
    private int scroll;
    /** Vrai quand l'onglet de prospection est ouvert ; le dernier relevé reçu ; le minerai affiché (-1 : tous). */
    private boolean prospecting;
    private ProspectPayload survey;
    private int oreFilter = -1;

    public TrackingStationScreen(SatelliteStatusPayload status) {
        super(Component.translatable("satellite.orbitalis.station.title"));
        this.status = status;
        // Le relevé de la planète est demandé tout de suite : il arrive pendant que l'écran s'ouvre.
        StationGlobe.prepare(status.station());
    }

    /** Nouvel état reçu du serveur pendant que l'écran est ouvert. */
    public void update(SatelliteStatusPayload status) {
        this.status = status;
    }

    /** Ouvre l'onglet de prospection sur un relevé donné (sert aux captures de vérification). */
    public void showSurvey(ProspectPayload payload) {
        prospecting = true;
        survey = payload;
    }

    /** Relevé de prospection reçu du serveur. */
    public void prospect(ProspectPayload payload) {
        if (payload.station().equals(status.station())) {
            survey = payload;
        }
    }

    @Override
    protected void init() {
        panelWidth = Math.clamp(width - 8, 400, 440);
        panelHeight = Math.clamp(height - 8, 228, 290);
        sideWidth = panelWidth - SIDE_X - 10;
        // La liste prend ce que la fiche lui laisse : deux lignes au moins.
        listRows = Math.clamp((panelHeight - HEADER - FOOTER - 20 - 12 - DETAIL_HEIGHT) / ROW_HEIGHT, 2, 6);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
    }

    @Override
    public void tick() {
        super.tick();
        if (++age % REFRESH_TICKS == 0) {
            send(SatelliteCommandPayload.REFRESH);
        }
    }

    private void send(int action) {
        ClientPacketDistributor.sendToServer(new SatelliteCommandPayload(status.station(), action));
    }

    // ------------------------------------------------------------------
    // État
    // ------------------------------------------------------------------

    private static SatelliteType typeOf(SatelliteStatusPayload.Entry entry) {
        SatelliteType[] types = SatelliteType.values();
        return types[Math.floorMod(entry.type(), types.length)];
    }

    /** Les satellites, du plus récent au plus ancien : ce sont ceux qu'on vient de lancer qu'on cherche. */
    private List<SatelliteStatusPayload.Entry> fleet() {
        List<SatelliteStatusPayload.Entry> fleet = new ArrayList<>(status.satellites());
        java.util.Collections.reverse(fleet);
        return fleet;
    }

    private SatelliteStatusPayload.Entry selectedEntry() {
        List<SatelliteStatusPayload.Entry> fleet = fleet();
        for (SatelliteStatusPayload.Entry entry : fleet) {
            if (entry.number() == selected) {
                return entry;
            }
        }
        return fleet.isEmpty() ? null : fleet.get(0);
    }

    private int activeCount() {
        int count = 0;
        for (SatelliteStatusPayload.Entry entry : status.satellites()) {
            count += entry.active() ? 1 : 0;
        }
        return count;
    }

    private float time(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        return (minecraft.level != null ? minecraft.level.getGameTime() % 1_000_000L : age) + partialTick;
    }

    private int mapTop() {
        return top + HEADER + 6;
    }

    private int mapHeight() {
        return panelHeight - HEADER - FOOTER - 12;
    }

    /**
     * L'angle de vue de la carte, gardé d'une ouverture à l'autre : rotation autour de l'axe des pôles, et
     * inclinaison. À la première ouverture, la station est face à soi.
     */
    private static float viewYaw = com.orbitalis.client.ViewPrefs.getFloat("station.yaw", Float.NaN);
    private static float viewPitch = com.orbitalis.client.ViewPrefs.getFloat("station.pitch", (float) com.orbitalis.core.atlas.StationSurvey.LATITUDE);
    /** Tourne la carte des orbites (sert aux captures de vérification). */
    public static void setView(float yaw, float pitch) {
        viewYaw = yaw;
        viewPitch = pitch;
    }

    /** Vrai tant qu'on fait tourner la planète à la souris. */
    private boolean rotating;

    /** Vitesse d'un satellite sur son orbite, en radians par tick, au rythme de l'écran. */
    private static float angularSpeed(Orbit orbit) {
        return Mth.TWO_PI / ((float) orbit.periodMinutes() * TICKS_PER_ORBIT_MINUTE);
    }

    /** L'angle de vue à cet instant : celui qu'on a donné, et la planète qui a tourné sur elle-même. */
    private StationGlobe.View view(float time) {
        float spin = -angularSpeed(Orbit.GEOSTATIONARY) * time;
        if (Float.isNaN(viewYaw)) {
            viewYaw = -spin;
        }
        return new StationGlobe.View(viewYaw, viewPitch, spin);
    }

    /** Rayon d'une orbite sur la carte, en pixels : à l'échelle de l'écran, pas de la réalité, et toujours dans le cadre. */
    private int radius(Orbit orbit) {
        int base = switch (orbit) {
            case LOW -> 33;
            case POLAR -> 41;
            case MEDIUM -> 64;
            case GEOSTATIONARY -> 86;
            case LUNAR -> 120;
        };
        return Math.min(base, mapHeight() / 2 - 4);
    }

    /** Inclinaison d'une orbite sur l'équateur, en radians : celles des vraies orbites de travail. */
    private static double inclination(Orbit orbit) {
        return switch (orbit) {
            case LOW -> Math.toRadians(51.6);
            case POLAR -> Math.toRadians(98.0);
            case MEDIUM -> Math.toRadians(55.0);
            case GEOSTATIONARY -> 0.0;
            case LUNAR -> Math.toRadians(28.0);
        };
    }

    /** Longitude où l'orbite croise l'équateur en montant, en radians : chacune la sienne, pour qu'elles ne se confondent pas. */
    private static double node(Orbit orbit) {
        return switch (orbit) {
            case LOW -> 0.4;
            case POLAR -> -1.0;
            case MEDIUM -> 2.1;
            case GEOSTATIONARY -> 0.0;
            case LUNAR -> 1.3;
        };
    }

    /**
     * Un point d'une orbite, à cet angle sur elle, dans le cadre : décalage horizontal et vertical depuis le centre de
     * la carte, en pixels, puis profondeur (positive devant la planète).
     */
    private float[] ringPoint(StationGlobe.View view, Orbit orbit, float angle) {
        double i = inclination(orbit);
        double x = Math.cos(angle);
        double y = -Math.sin(angle) * Math.sin(i);
        double z = Math.sin(angle) * Math.cos(i);
        double node = node(orbit);
        double px = x * Math.cos(node) + z * Math.sin(node);
        double pz = -x * Math.sin(node) + z * Math.cos(node);
        float[] v = view.rotate(px, y, pz);
        int r = radius(orbit);
        return new float[] {v[0] * r, -v[1] * r, v[2]};
    }

    /** Où est un satellite sur son orbite, à cet instant : chacun part d'un point différent, selon son numéro. */
    private float[] orbitPoint(SatelliteStatusPayload.Entry entry, StationGlobe.View view, float time) {
        Orbit orbit = typeOf(entry).orbit();
        return ringPoint(view, orbit, entry.number() * 2.399963F + angularSpeed(orbit) * time);
    }

    /** Position d'un satellite sur la carte, par rapport à son centre ; {@code null} s'il passe derrière la planète. */
    private float[] position(SatelliteStatusPayload.Entry entry, float time) {
        float[] at = orbitPoint(entry, view(time), time);
        if (at[2] < 0.0F && at[0] * at[0] + at[1] * at[1] < StationGlobe.RADIUS * StationGlobe.RADIUS) {
            return null;
        }
        return new float[] {at[0], at[1]};
    }

    private int listTop() {
        return top + HEADER + 20;
    }

    private int maxScroll() {
        return Math.max(0, status.satellites().size() - listRows);
    }

    private int buttonX() {
        return left + panelWidth - 10 - BUTTON_WIDTH;
    }

    /** Le bouton de l'observatoire, à gauche de celui de la réception : sa largeur, et sa place. */
    private int observatoryWidth() {
        return font.width(Component.translatable("observatory.orbitalis.open").getString().toUpperCase(Locale.ROOT)) + 16;
    }

    private int observatoryX() {
        return buttonX() - 6 - observatoryWidth();
    }

    private int buttonY() {
        return top + panelHeight - FOOTER + 8;
    }

    /** Le bouton ne sert que s'il y a des données à recevoir et que l'antenne voit le ciel. */
    private boolean buttonActive() {
        return status.skyClear() && status.pending() > 0;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (Ui.inside(mouseX, mouseY, observatoryX(), buttonY(), observatoryWidth(), BUTTON_HEIGHT)) {
            Ui.click();
            Minecraft.getInstance().gui.setScreen(new ObservatoryScreen(this, status.station()));
            return true;
        }
        if (buttonActive() && Ui.inside(mouseX, mouseY, buttonX(), buttonY(), BUTTON_WIDTH, BUTTON_HEIGHT)) {
            send(SatelliteCommandPayload.DOWNLOAD);
            Ui.click();
            return true;
        }
        // Les deux onglets, en haut de la carte.
        for (int tab = 0; tab < 2; tab++) {
            if (Ui.inside(mouseX, mouseY, tabX(tab), mapTop() + 4, tabWidth(tab), 13)) {
                boolean wanted = tab == 1;
                if (wanted != prospecting || wanted) {
                    prospecting = wanted;
                    if (prospecting) {
                        // Un nouveau relevé à chaque ouverture de l'onglet (ou clic dessus) : le terrain a pu changer.
                        survey = null;
                        send(SatelliteCommandPayload.PROSPECT);
                    }
                    Ui.click();
                }
                return true;
            }
        }
        if (prospecting) {
            // La liste des minerais : « tous », puis un par ligne.
            for (int row = 0; row <= ProspectOre.values().length; row++) {
                if (Ui.inside(mouseX, mouseY, left + SIDE_X, oreTop() + row * ORE_ROW, sideWidth, ORE_ROW)) {
                    oreFilter = row - 1;
                    Ui.click();
                    return true;
                }
            }
            return super.mouseClicked(event, doubleClick);
        }
        // Une ligne de la liste.
        List<SatelliteStatusPayload.Entry> fleet = fleet();
        for (int row = 0; row < listRows && row + scroll < fleet.size(); row++) {
            if (Ui.inside(mouseX, mouseY, left + SIDE_X, listTop() + row * ROW_HEIGHT, sideWidth, ROW_HEIGHT)) {
                selected = fleet.get(row + scroll).number();
                Ui.click();
                return true;
            }
        }
        // Un point de la carte : le satellite le plus proche du clic ; et, en glissant, on fait tourner la planète.
        if (Ui.inside(mouseX, mouseY, left + MAP_X, mapTop(), MAP_WIDTH, mapHeight())) {
            rotating = true;
            float centerX = left + MAP_X + MAP_WIDTH / 2.0F;
            float centerY = mapTop() + mapHeight() / 2.0F;
            float best = 49.0F;
            for (SatelliteStatusPayload.Entry entry : fleet) {
                float[] at = entry.active() ? position(entry, time(0.0F)) : null;
                if (at != null) {
                    float distance = Mth.square((float) mouseX - centerX - at[0]) + Mth.square((float) mouseY - centerY - at[1]);
                    if (distance < best) {
                        best = distance;
                        selected = entry.number();
                    }
                }
            }
            if (best < 49.0F) {
                Ui.click();
                // La liste suit : la ligne du satellite choisi vient à l'écran.
                for (int i = 0; i < fleet.size(); i++) {
                    if (fleet.get(i).number() == selected) {
                        scroll = Mth.clamp(i - 1, 0, maxScroll());
                    }
                }
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (rotating && !prospecting) {
            viewYaw += (float) dx * 0.012F;
            viewPitch = Mth.clamp(viewPitch + (float) dy * 0.012F, -1.45F, 1.45F);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (rotating && !Float.isNaN(viewYaw)) {
            // L'angle où on l'a laissée, gardé d'une partie à l'autre.
            com.orbitalis.client.ViewPrefs.set("station.yaw", viewYaw);
            com.orbitalis.client.ViewPrefs.set("station.pitch", viewPitch);
        }
        rotating = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
        return true;
    }

    // ------------------------------------------------------------------
    // Dessin
    // ------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        Ui.panel(graphics, left, top, panelWidth, panelHeight);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        // En-tête : le titre, puis l'état de la station en trois étiquettes.
        graphics.text(font, title.copy().withStyle(ChatFormatting.BOLD), left + 12, top + 11, Ui.TEXT, false);
        int right = left + panelWidth - 12;
        String link = text(!status.skyClear() ? "satellite.orbitalis.radar.link_lost" : status.relay() ? "satellite.orbitalis.radar.link_relay"
                : "satellite.orbitalis.radar.link_ok");
        Ui.pillRight(graphics, font, link, right, top + 10, !status.skyClear() ? Ui.BAD : status.relay() ? Ui.OK : Ui.ACCENT);
        right -= Ui.pillWidth(font, link) + 5;
        String active = Component.translatable("satellite.orbitalis.radar.active", activeCount()).getString();
        Ui.pillRight(graphics, font, active, right, top + 10, activeCount() > 0 ? Ui.OK : Ui.DIM);
        right -= Ui.pillWidth(font, active) + 5;
        if (status.navigation() > 0) {
            String navigation = Component.translatable("satellite.orbitalis.radar.navigation", status.navigation(),
                    SatelliteType.NAVIGATION_CONSTELLATION).getString();
            Ui.pillRight(graphics, font, navigation, right, top + 10, status.navigation() >= SatelliteType.NAVIGATION_CONSTELLATION ? Ui.OK : Ui.WARN);
        }
        Ui.hline(graphics, left + 1, top + HEADER, panelWidth - 2);

        if (prospecting) {
            extractSurvey(graphics, mouseX, mouseY);
            extractOres(graphics, mouseX, mouseY);
        } else {
            extractMap(graphics, mouseX, mouseY, partialTick);
            extractFleet(graphics, mouseX, mouseY);
            extractDetail(graphics);
        }
        extractTabs(graphics, mouseX, mouseY);
        extractFooter(graphics, mouseX, mouseY);
        if (prospecting) {
            extractCellTip(graphics, mouseX, mouseY);
        }
    }

    // ------------------------------------------------------------------
    // Prospection
    // ------------------------------------------------------------------

    private static final int ORE_ROW = 12;

    private static String tabName(int tab) {
        return text(tab == 0 ? "satellite.orbitalis.radar.tab.orbits" : "satellite.orbitalis.radar.tab.prospect").toUpperCase(Locale.ROOT);
    }

    private int tabWidth(int tab) {
        return font.width(tabName(tab)) + 10;
    }

    private int tabX(int tab) {
        return left + MAP_X + 4 + (tab == 0 ? 0 : tabWidth(0) + 3);
    }

    private int oreTop() {
        return top + HEADER + 20;
    }

    /** Les deux onglets de la carte : les orbites, la prospection. */
    private void extractTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (int tab = 0; tab < 2; tab++) {
            boolean current = prospecting == (tab == 1);
            int x = tabX(tab);
            int y = mapTop() + 4;
            boolean hovered = Ui.inside(mouseX, mouseY, x, y, tabWidth(tab), 13);
            Ui.round(graphics, x, y, tabWidth(tab), 13, current ? Ui.SURFACE_HI : hovered ? Ui.SURFACE : 0xC004060A);
            Ui.frame(graphics, x, y, tabWidth(tab), 13, current ? Ui.ACCENT : Ui.LINE);
            graphics.text(font, tabName(tab), x + 5, y + 3, current ? Ui.TEXT : hovered ? Ui.TEXT : Ui.MUTED, false);
        }
    }

    /** Taille d'une case de la carte de prospection, en pixels. */
    private int cellSize() {
        return survey == null || survey.size() <= 0 ? 0 : Math.max(4, Math.min((MAP_WIDTH - 20) / survey.size(), (mapHeight() - 52) / survey.size()));
    }

    private int gridLeft() {
        return left + MAP_X + (MAP_WIDTH - cellSize() * survey.size()) / 2;
    }

    private int gridTop() {
        return mapTop() + 22 + (mapHeight() - 42 - cellSize() * survey.size()) / 2;
    }

    /** Ce que la carte montre d'une case : les blocs du minerai choisi, ou de tous. -1 si la case n'a pas pu être relevée. */
    private int shown(int column, int row) {
        if (survey.count(column, row, 0) < 0) {
            return -1;
        }
        if (oreFilter >= 0) {
            return survey.count(column, row, oreFilter);
        }
        int total = 0;
        for (int kind = 0; kind < survey.kinds(); kind++) {
            total += survey.count(column, row, kind);
        }
        return total;
    }

    /** Minerai le plus abondant d'une case, ou -1. */
    private int dominant(int column, int row) {
        int best = -1;
        int most = 0;
        for (int kind = 0; kind < survey.kinds(); kind++) {
            if (survey.count(column, row, kind) > most) {
                most = survey.count(column, row, kind);
                best = kind;
            }
        }
        return best;
    }

    /** La carte de prospection : une case par tronçon, d'autant plus vive qu'elle contient de minerai. */
    private void extractSurvey(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = left + MAP_X;
        int y = mapTop();
        int h = mapHeight();
        Ui.round(graphics, x, y, MAP_WIDTH, h, 0xFF04060A);
        Ui.frame(graphics, x, y, MAP_WIDTH, h, Ui.LINE);
        if (survey == null || survey.size() <= 0) {
            int lineY = y + 34;
            for (var line : font.split(Component.translatable(survey == null ? "satellite.orbitalis.prospect.waiting" : "satellite.orbitalis.prospect.none"),
                    MAP_WIDTH - 24)) {
                graphics.text(font, line, x + 12, lineY, Ui.MUTED, false);
                lineY += 10;
            }
            return;
        }
        int cell = cellSize();
        int gx = gridLeft();
        int gy = gridTop();
        int size = survey.size();
        int most = 1;
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                most = Math.max(most, shown(column, row));
            }
        }
        ProspectOre[] ores = ProspectOre.values();
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                int cx = gx + column * cell;
                int cy = gy + row * cell;
                int amount = shown(column, row);
                graphics.fill(cx, cy, cx + cell - 1, cy + cell - 1, amount < 0 ? 0xFF0A0D11 : Ui.SURFACE);
                if (amount < 0) {
                    // Terrain hors de vue : une petite croix grise.
                    graphics.fill(cx + cell / 2 - 1, cy + cell / 2 - 1, cx + cell / 2 + 1, cy + cell / 2 + 1, Ui.LINE_HI);
                } else if (amount > 0) {
                    int kind = oreFilter >= 0 ? oreFilter : dominant(column, row);
                    int color = kind >= 0 && kind < ores.length ? ores[kind].color() : Ui.ACCENT;
                    graphics.fill(cx, cy, cx + cell - 1, cy + cell - 1, Ui.alpha(color, 0.18F + 0.82F * amount / most));
                }
                if (Ui.inside(mouseX, mouseY, cx, cy, cell, cell)) {
                    Ui.frame(graphics, cx - 1, cy - 1, cell + 1, cell + 1, Ui.TEXT);
                }
            }
        }
        // La station, au centre du relevé ; et le nord, en haut.
        int middle = size / 2;
        Ui.frame(graphics, gx + middle * cell + cell / 2 - 3, gy + middle * cell + cell / 2 - 3, 6, 6, Ui.TEXT);
        graphics.text(font, "N", gx + size * cell / 2 - 3, gy - 10, Ui.DIM, false);
        Ui.right(graphics, font, Component.translatable("satellite.orbitalis.prospect.area", size, size), x + MAP_WIDTH - 6, y + h - 12, Ui.DIM);
    }

    /** À droite de la carte de prospection : les minerais relevés, avec leur total ; un clic n'en montre qu'un. */
    private void extractOres(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = left + SIDE_X;
        Ui.heading(graphics, font, Component.translatable("satellite.orbitalis.prospect.title"), x, top + HEADER + 8);
        ProspectOre[] ores = ProspectOre.values();
        for (int row = 0; row <= ores.length; row++) {
            int y = oreTop() + row * ORE_ROW;
            int kind = row - 1;
            boolean current = kind == oreFilter;
            if (current) {
                Ui.round(graphics, x, y, sideWidth, ORE_ROW - 1, Ui.SURFACE_HI);
                graphics.fill(x, y + 2, x + 1, y + ORE_ROW - 3, Ui.ACCENT);
            } else if (Ui.inside(mouseX, mouseY, x, y, sideWidth, ORE_ROW)) {
                Ui.round(graphics, x, y, sideWidth, ORE_ROW - 1, Ui.SURFACE);
            }
            int total = 0;
            if (survey != null) {
                for (int cellRow = 0; cellRow < survey.size(); cellRow++) {
                    for (int column = 0; column < survey.size(); column++) {
                        for (int k = 0; k < survey.kinds(); k++) {
                            if (kind < 0 || k == kind) {
                                total += Math.max(0, survey.count(column, cellRow, k));
                            }
                        }
                    }
                }
            }
            Ui.dot(graphics, x + 6, y + 3, kind < 0 ? Ui.TEXT : total > 0 ? ores[kind].color() : Ui.DIM);
            graphics.text(font, Component.translatable(kind < 0 ? "satellite.orbitalis.prospect.all" : ores[kind].nameKey()), x + 16, y + 2,
                    total > 0 ? Ui.TEXT : Ui.DIM, false);
            Ui.right(graphics, font, String.format(Locale.ROOT, "%,d", total).replace(',', ' '), x + sideWidth - 6, y + 2, total > 0 ? Ui.MUTED : Ui.DIM);
        }
        // Le mode d'emploi, s'il tient en entier sous la liste : une phrase coupée ne dirait rien.
        int hintY = oreTop() + (ores.length + 1) * ORE_ROW + 6;
        var hint = font.split(Component.translatable("satellite.orbitalis.prospect.hint"), sideWidth - 4);
        if (hintY + hint.size() * 10 <= top + panelHeight - FOOTER - 4) {
            for (var line : hint) {
                graphics.text(font, line, x + 2, hintY, Ui.DIM, false);
                hintY += 10;
            }
        }
    }

    /** La case sous la souris : où elle se trouve dans le monde, et ce qu'elle contient. */
    private void extractCellTip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int cell = cellSize();
        if (cell <= 0 || !Ui.inside(mouseX, mouseY, gridLeft(), gridTop(), cell * survey.size(), cell * survey.size())) {
            return;
        }
        int column = (mouseX - gridLeft()) / cell;
        int row = (mouseY - gridTop()) / cell;
        int blockX = (survey.originX() + column) << 4;
        int blockZ = (survey.originZ() + row) << 4;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("satellite.orbitalis.prospect.cell", blockX, blockX + 15, blockZ, blockZ + 15));
        ProspectOre[] ores = ProspectOre.values();
        List<Integer> colors = new ArrayList<>();
        colors.add(Ui.TEXT);
        if (survey.count(column, row, 0) < 0) {
            lines.add(Component.translatable("satellite.orbitalis.prospect.unseen"));
            colors.add(Ui.DIM);
        } else {
            for (int kind = 0; kind < survey.kinds() && kind < ores.length; kind++) {
                int count = survey.count(column, row, kind);
                if (count > 0) {
                    lines.add(Component.translatable(ores[kind].nameKey()).append(" · " + count));
                    colors.add(ores[kind].color());
                }
            }
            if (lines.size() == 1) {
                lines.add(Component.translatable("satellite.orbitalis.prospect.empty"));
                colors.add(Ui.DIM);
            }
        }
        int w = 0;
        for (Component line : lines) {
            w = Math.max(w, font.width(line));
        }
        w += 12;
        int h = lines.size() * 10 + 8;
        int x = Math.min(mouseX + 10, width - w - 4);
        int y = Math.min(mouseY + 10, height - h - 4);
        graphics.nextStratum();
        Ui.card(graphics, x, y, w, h, true);
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(font, lines.get(i), x + 6, y + 5 + i * 10, colors.get(i), false);
        }
    }

    /** Nom d'un satellite, dans la largeur qu'on lui laisse : si le nom du modèle est coupé, son numéro reste entier. */
    private String satelliteName(SatelliteType type, int number, int width) {
        String model = Component.translatable(type.nameKey()).getString();
        String full = Component.translatable("satellite.orbitalis.station.name", model, number).getString();
        if (font.width(full) <= width || !full.startsWith(model)) {
            return Ui.fit(font, full, width);
        }
        String suffix = full.substring(model.length());
        return Ui.fit(font, model, width - font.width(suffix)) + suffix;
    }

    private static String text(String key) {
        return Component.translatable(key).getString();
    }

    /** Carte des orbites : la planète au centre, en 3D, les orbites autour, et les satellites en mouvement. On la fait tourner à la souris. */
    private void extractMap(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = left + MAP_X;
        int y = mapTop();
        int h = mapHeight();
        Ui.round(graphics, x, y, MAP_WIDTH, h, 0xFF04060A);
        Ui.frame(graphics, x, y, MAP_WIDTH, h, Ui.LINE);
        int centerX = x + MAP_WIDTH / 2;
        int centerY = y + h / 2;
        graphics.enableScissor(x + 1, y + 1, x + MAP_WIDTH - 1, y + h - 1);

        // Quelques étoiles, toujours aux mêmes endroits.
        for (int i = 0; i < 46; i++) {
            int starX = x + 2 + Math.floorMod(i * 73 + 11, MAP_WIDTH - 4);
            int starY = y + 2 + Math.floorMod(i * 151 + 29, h - 4);
            graphics.fill(starX, starY, starX + 1, starY + 1, i % 5 == 0 ? Ui.MUTED : Ui.LINE_HI);
        }

        float time = time(partialTick);
        StationGlobe.View view = view(time);
        // Les étiquettes posées sur la carte : aucune ne doit en recouvrir une autre.
        List<int[]> taken = new ArrayList<>();
        String hint = text("satellite.orbitalis.radar.rotate");
        int hintWidth = Math.min(font.width(hint), MAP_WIDTH - 12);
        taken.add(new int[] {x + MAP_WIDTH - 6 - hintWidth, y + h - 12, x + MAP_WIDTH - 4, y + h});
        SatelliteStatusPayload.Entry chosen = selectedEntry();
        Orbit chosenOrbit = chosen == null ? null : typeOf(chosen).orbit();
        // L'arrière d'abord (ce qui passe derrière la planète), puis la planète, puis l'avant : la planète cache ce qui
        // est derrière elle, d'elle-même.
        for (int pass = 0; pass < 2; pass++) {
            boolean front = pass == 1;
            if (front) {
                StationGlobe.draw(graphics, status.station(), centerX, centerY, view);
                // La station elle-même, sur le globe, quand elle est de ce côté.
                float[] here = view.project(0.0, com.orbitalis.core.atlas.StationSurvey.LATITUDE);
                if (here[2] > 0.0F) {
                    int sx = centerX + Math.round(here[0] * StationGlobe.RADIUS);
                    int sy = centerY - Math.round(here[1] * StationGlobe.RADIUS);
                    graphics.fill(sx - 1, sy - 1, sx + 2, sy + 2, 0xFF04060A);
                    graphics.fill(sx, sy, sx + 1, sy + 1, (age / 10) % 2 == 0 ? Ui.ACCENT : 0xFFFFFFFF);
                }
            }
            for (Orbit orbit : Orbit.values()) {
                ring(graphics, view, orbit, centerX, centerY, orbit == chosenOrbit ? Ui.ACCENT : Ui.LINE_HI, front);
            }
            // Les satellites en service : un point de la couleur de leur type ; celui qu'on a choisi est entouré.
            for (SatelliteStatusPayload.Entry entry : status.satellites()) {
                if (!entry.active()) {
                    continue;
                }
                float[] at = orbitPoint(entry, view, time);
                if (at[2] >= 0.0F != front) {
                    continue;
                }
                int dotX = centerX + Math.round(at[0]);
                int dotY = centerY + Math.round(at[1]);
                boolean hidden = !front && at[0] * at[0] + at[1] * at[1] < StationGlobe.RADIUS * StationGlobe.RADIUS;
                if (hidden) {
                    continue;
                }
                boolean isChosen = chosen != null && entry.number() == chosen.number();
                boolean hovered = Mth.square(mouseX - dotX) + Mth.square(mouseY - dotY) < 30;
                int color = front ? typeOf(entry).color() : Ui.alpha(typeOf(entry).color(), 0.55F);
                if (isChosen) {
                    Ui.frame(graphics, dotX - 5, dotY - 5, 11, 11, Ui.TEXT);
                    String name = "n° " + entry.number();
                    graphics.fill(dotX + 7, dotY - 5, dotX + 10 + font.width(name), dotY + 5, 0xD004060A);
                    graphics.text(font, name, dotX + 9, dotY - 4, Ui.TEXT, false);
                    taken.add(new int[] {dotX - 5, dotY - 5, dotX + 10 + font.width(name), dotY + 6});
                } else if (hovered) {
                    Ui.frame(graphics, dotX - 4, dotY - 4, 9, 9, Ui.MUTED);
                }
                Ui.dot(graphics, dotX - 2, dotY - 2, color);
            }
        }
        // Le nom de chaque orbite, au point le plus bas de sa partie visible : le haut de la carte est aux onglets.
        for (Orbit orbit : Orbit.values()) {
            float[] low = null;
            for (int k = 0; k < 64; k++) {
                float[] at = ringPoint(view, orbit, Mth.TWO_PI * k / 64.0F);
                if (at[2] >= 0.0F && (low == null || at[1] > low[1])) {
                    low = at;
                }
            }
            if (low != null) {
                String label = text(orbit.nameKey() + ".short");
                int labelX = Mth.clamp(centerX + Math.round(low[0]) - font.width(label) / 2, x + 3, x + MAP_WIDTH - 3 - font.width(label));
                int baseY = centerY + Math.round(low[1]) + 3;
                // Juste sous l'orbite si la place est libre, sinon un peu plus bas ou plus haut ; nulle part, on n'écrit pas.
                for (int shift : new int[] {0, 9, -12, 18, -21}) {
                    int labelY = Mth.clamp(baseY + shift, y + 20, y + h - 11);
                    int[] box = {labelX - 1, labelY - 1, labelX + font.width(label) + 1, labelY + 9};
                    boolean free = true;
                    for (int[] other : taken) {
                        free &= box[2] <= other[0] || box[0] >= other[2] || box[3] <= other[1] || box[1] >= other[3];
                    }
                    if (free) {
                        taken.add(box);
                        graphics.text(font, label, labelX, labelY, orbit == chosenOrbit ? Ui.ACCENT : Ui.DIM, false);
                        break;
                    }
                }
            }
        }
        // Comment faire tourner la planète.
        graphics.text(font, Ui.fit(font, hint, MAP_WIDTH - 12), x + MAP_WIDTH - 6 - hintWidth, y + h - 11, Ui.alpha(Ui.DIM, 0.8F), false);
        graphics.disableScissor();
    }

    /** Un tour d'orbite en pointillés : sa moitié de devant, ou celle de derrière, que la planète cache en partie. */
    private void ring(GuiGraphicsExtractor graphics, StationGlobe.View view, Orbit orbit, int centerX, int centerY, int color, boolean front) {
        int points = Math.max(48, radius(orbit) * 4);
        for (int i = 0; i < points; i += 2) {
            float[] at = ringPoint(view, orbit, Mth.TWO_PI * i / points);
            if (at[2] >= 0.0F != front) {
                continue;
            }
            if (!front && at[0] * at[0] + at[1] * at[1] < StationGlobe.RADIUS * StationGlobe.RADIUS) {
                continue;
            }
            int px = centerX + Math.round(at[0]);
            int py = centerY + Math.round(at[1]);
            graphics.fill(px, py, px + 1, py + 1, front ? color : Ui.alpha(color, 0.5F));
        }
    }

    /** La flotte : une ligne par satellite, avec la couleur de son type, son orbite et ses données en mémoire. */
    private void extractFleet(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = left + SIDE_X;
        Ui.heading(graphics, font, Component.translatable("satellite.orbitalis.radar.fleet"), x, top + HEADER + 8);
        List<SatelliteStatusPayload.Entry> fleet = fleet();
        Ui.right(graphics, font, String.valueOf(fleet.size()), x + sideWidth, top + HEADER + 8, Ui.DIM);
        if (fleet.isEmpty()) {
            int lineY = listTop() + 2;
            for (var line : font.split(Component.translatable("satellite.orbitalis.station.empty"), sideWidth)) {
                graphics.text(font, line, x, lineY, Ui.MUTED, false);
                lineY += 10;
            }
            return;
        }
        SatelliteStatusPayload.Entry chosen = selectedEntry();
        for (int row = 0; row < listRows && row + scroll < fleet.size(); row++) {
            SatelliteStatusPayload.Entry entry = fleet.get(row + scroll);
            SatelliteType type = typeOf(entry);
            int y = listTop() + row * ROW_HEIGHT;
            boolean isChosen = chosen != null && chosen.number() == entry.number();
            if (isChosen) {
                Ui.round(graphics, x, y, sideWidth, ROW_HEIGHT - 1, Ui.SURFACE_HI);
                graphics.fill(x, y + 2, x + 1, y + ROW_HEIGHT - 3, Ui.ACCENT);
            } else if (Ui.inside(mouseX, mouseY, x, y, sideWidth, ROW_HEIGHT)) {
                Ui.round(graphics, x, y, sideWidth, ROW_HEIGHT - 1, Ui.SURFACE);
            }
            Ui.dot(graphics, x + 6, y + 4, entry.active() ? type.color() : Ui.DIM);
            String orbit = text(type.orbit().nameKey() + ".short");
            String pending = entry.pending() > 0 ? "+" + entry.pending() : "";
            int rightEdge = x + sideWidth - 6;
            if (!pending.isEmpty()) {
                Ui.right(graphics, font, pending, rightEdge, y + 3, Ui.OK);
                rightEdge -= font.width(pending) + 8;
            }
            Ui.right(graphics, font, orbit, rightEdge, y + 3, Ui.DIM);
            rightEdge -= font.width(orbit) + 8;
            graphics.text(font, satelliteName(type, entry.number(), rightEdge - (x + 16)), x + 16, y + 3, entry.active() ? Ui.TEXT : Ui.DIM, false);
        }
        // Barre de défilement, seulement si la liste dépasse.
        if (maxScroll() > 0) {
            int track = listRows * ROW_HEIGHT;
            int thumb = Math.max(8, track * listRows / fleet.size());
            graphics.fill(x + sideWidth + 2, listTop() + (track - thumb) * scroll / maxScroll(), x + sideWidth + 3,
                    listTop() + (track - thumb) * scroll / maxScroll() + thumb, Ui.LINE_HI);
        }
    }

    /** La fiche du satellite choisi : à quoi il sert, et ses chiffres. */
    private void extractDetail(GuiGraphicsExtractor graphics) {
        int x = left + SIDE_X;
        int y = listTop() + listRows * ROW_HEIGHT + 6;
        int h = top + panelHeight - FOOTER - 6 - y;
        Ui.card(graphics, x, y, sideWidth, h, false);
        SatelliteStatusPayload.Entry entry = selectedEntry();
        if (entry == null) {
            return;
        }
        SatelliteType type = typeOf(entry);
        Orbit orbit = type.orbit();
        graphics.item(new ItemStack(type.item()), x + 6, y + 5);
        String state = text(entry.active() ? "satellite.orbitalis.radar.in_service" : "satellite.orbitalis.station.retired");
        graphics.text(font, satelliteName(type, entry.number(), sideWidth - 36 - Ui.pillWidth(font, state)), x + 27, y + 9, Ui.TEXT, false);
        Ui.pillRight(graphics, font, state, x + sideWidth - 6, y + 7, entry.active() ? Ui.OK : Ui.BAD);

        // À quoi il sert : deux lignes au plus.
        int lineY = y + 25;
        int described = h >= DETAIL_HEIGHT ? 2 : 1;
        for (String line : Ui.lines(font, Component.translatable("satellite.orbitalis.function." + type.id()), sideWidth - 12, described)) {
            graphics.text(font, line, x + 6, lineY, Ui.MUTED, false);
            lineY += 10;
        }
        lineY = y + 28 + described * 10;
        Ui.hline(graphics, x + 6, lineY - 3, sideWidth - 12);
        int w = sideWidth - 12;
        Ui.row(graphics, font, Component.translatable(orbit.nameKey()), String.format(Locale.ROOT, "%,d km", orbit.altitudeKm()).replace(',', ' '),
                x + 6, lineY, w, Ui.TEXT);
        Ui.row(graphics, font, Component.translatable("satellite.orbitalis.radar.period"), period(orbit.periodMinutes()), x + 6, lineY + 10, w, Ui.TEXT);
        Ui.row(graphics, font, Component.translatable("satellite.orbitalis.radar.age"),
                Component.translatable("satellite.orbitalis.radar.days", entry.age() / AgencySatellites.DAY).getString(), x + 6, lineY + 20, w, Ui.TEXT);
        String output = type.pointsPerDay() > 0 ? Component.translatable("satellite.orbitalis.radar.per_day", type.pointsPerDay()).getString()
                : text("satellite.orbitalis.radar.no_output");
        Ui.row(graphics, font, Component.translatable("satellite.orbitalis.radar.output"), output, x + 6, lineY + 30, w, type.pointsPerDay() > 0 ? Ui.TEXT : Ui.DIM);
        Ui.row(graphics, font, Component.translatable("satellite.orbitalis.radar.memory"), entry.pending() > 0 ? "+" + entry.pending() : "0",
                x + 6, lineY + 40, w, entry.pending() > 0 ? Ui.OK : Ui.DIM);

        // Ce qu'il lui reste à vivre : en jours, et en jauge.
        long total = (long) type.lifetimeDays() * AgencySatellites.DAY;
        String life = !entry.active() ? "0" : entry.remaining() < 0 ? text("satellite.orbitalis.radar.unlimited")
                : Component.translatable("satellite.orbitalis.radar.days", (entry.remaining() + AgencySatellites.DAY - 1) / AgencySatellites.DAY).getString();
        float share = !entry.active() ? 0.0F : entry.remaining() < 0 || total <= 0 ? 1.0F : Mth.clamp(entry.remaining() / (float) total, 0.0F, 1.0F);
        int lifeColor = share > 0.3F ? Ui.OK : share > 0.1F ? Ui.WARN : Ui.BAD;
        Ui.row(graphics, font, Component.translatable("satellite.orbitalis.radar.life"), life, x + 6, lineY + 50, w, lifeColor);
        if (lineY + 63 < y + h - 2) {
            Ui.bar(graphics, x + 6, lineY + 61, w, 2, share, lifeColor);
        }
    }

    /** Période d'une orbite, lisible : en minutes sous deux heures, en heures au-delà. */
    private static String period(double minutes) {
        return minutes < 120.0 ? String.format(Locale.ROOT, "%.0f min", minutes) : String.format(Locale.ROOT, "%.1f h", minutes / 60.0);
    }

    /** Bas de l'écran : la météo vue d'en haut, les données en attente, et le bouton de réception. */
    private void extractFooter(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int y = top + panelHeight - FOOTER;
        Ui.hline(graphics, left + 1, y, panelWidth - 2);
        int x = left + 12;

        Component forecast = switch (status.forecast()) {
            case SatelliteStatusPayload.FORECAST_CLEAR -> Component.translatable("satellite.orbitalis.forecast.clear", minutes(status.forecastTicks()));
            case SatelliteStatusPayload.FORECAST_RAIN -> Component.translatable("satellite.orbitalis.forecast.rain", minutes(status.forecastTicks()));
            case SatelliteStatusPayload.FORECAST_THUNDER -> Component.translatable("satellite.orbitalis.forecast.thunder", minutes(status.forecastTicks()));
            default -> Component.translatable("satellite.orbitalis.forecast.none");
        };
        int forecastColor = switch (status.forecast()) {
            case SatelliteStatusPayload.FORECAST_CLEAR -> Ui.OK;
            case SatelliteStatusPayload.FORECAST_RAIN -> Ui.WARN;
            case SatelliteStatusPayload.FORECAST_THUNDER -> Ui.BAD;
            default -> Ui.DIM;
        };
        Ui.dot(graphics, x, y + 8, forecastColor);
        graphics.text(font, Ui.fit(font, forecast.getString(), observatoryX() - x - 16), x + 9, y + 7,
                status.forecast() == SatelliteStatusPayload.FORECAST_NONE ? Ui.MUTED : Ui.TEXT, false);

        Component link = !status.skyClear() ? Component.translatable("satellite.orbitalis.station.sky_blocked")
                : status.relay() ? Component.translatable("satellite.orbitalis.station.relay")
                        : Component.translatable("satellite.orbitalis.station.pending", status.pending());
        Ui.dot(graphics, x, y + 20, !status.skyClear() ? Ui.BAD : status.relay() || status.pending() > 0 ? Ui.OK : Ui.DIM);
        graphics.text(font, Ui.fit(font, link.getString(), observatoryX() - x - 16), x + 9, y + 19, Ui.MUTED, false);

        // L'observatoire : ce que les satellites voient du ciel.
        Ui.button(graphics, font, Component.translatable("observatory.orbitalis.open"), observatoryX(), buttonY(), observatoryWidth(), BUTTON_HEIGHT,
                Ui.inside(mouseX, mouseY, observatoryX(), buttonY(), observatoryWidth(), BUTTON_HEIGHT), true, false, Ui.ACCENT);
        boolean active = buttonActive();
        Ui.button(graphics, font, Component.translatable("satellite.orbitalis.station.download"), buttonX(), buttonY(), BUTTON_WIDTH, BUTTON_HEIGHT,
                active && Ui.inside(mouseX, mouseY, buttonX(), buttonY(), BUTTON_WIDTH, BUTTON_HEIGHT), active, true, Ui.ACCENT);
    }

    /** Durée en minutes réelles, arrondie au-dessus (1 200 ticks par minute). */
    private static int minutes(int ticks) {
        return (ticks + 1199) / 1200;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
