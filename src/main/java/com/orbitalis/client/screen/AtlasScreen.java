package com.orbitalis.client.screen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.orbitalis.client.render.AtlasMap;
import com.orbitalis.client.render.AtlasPainter;
import com.orbitalis.core.atlas.AtlasTiles;
import com.orbitalis.core.network.AtlasMarkerPayload;
import com.orbitalis.core.network.AtlasSitesPayload;
import com.orbitalis.core.network.AtlasSitesRequestPayload;
import com.orbitalis.core.network.SatelliteStatusPayload;
import com.orbitalis.phase1.satellite.SatelliteType;
import com.orbitalis.phase1.rocket.RocketStats;
import com.orbitalis.phase1.spaceport.LaunchTableBlockEntity;
import com.orbitalis.phase1.spaceport.RocketMotion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.joml.Matrix3x2f;

/**
 * L'atlas : la carte du monde, en plein écran. On la parcourt librement, en la faisant glisser, on zoome à la
 * molette, du bloc près jusqu'à une région entière, et l'on choisit de quel côté on la regarde.
 * <ul>
 * <li><b>Le terrain</b> vient du serveur, tuile par tuile (voir {@link AtlasMap}) : seules celles que l'on regarde
 * sont demandées, à l'échelle du zoom.</li>
 * <li><b>Rien ne disparaît.</b> Tant qu'une tuile n'est pas prête, ce que l'on a déjà de cet endroit reste montré à
 * sa place : la tuile plus grossière qui la couvre, ou les tuiles plus fines qu'elle couvre. Une tuile neuve
 * apparaît en fondu. Le zoom glisse d'une échelle à l'autre, et l'on ne change d'orientation qu'une fois la
 * nouvelle vue prête.</li>
 * <li><b>Ce que l'on connaît</b> : le centre de contrôle ne connaît que ses environs. Un pointillé en marque la
 * limite ; au-delà, la carte reste vide.</li>
 * <li><b>Les calques</b>, que l'on montre ou cache au choix : les installations (pas de tir avec leur état,
 * centres de contrôle, stations de poursuite), les vols en cours (la route suivie et sa suite), et les zones de danger
 * (la zone d'exclusion d'une fusée posée, le couloir de retombée d'une fusée en vol). Le serveur les envoie, chargées
 * ou non (voir {@code AtlasSites}) ; une boussole donne le nord.</li>
 * <li><b>Le ciel</b>, en deux calques de plus, selon les satellites de l'agence : la météo (les vrais nuages du
 * monde, vus d'en haut, avec leur ombre, et la pluie) si elle a un satellite météo ; les satellites (la couverture
 * d'observation autour des stations, la précision de la position selon la constellation de navigation). Une carte
 * résume ce ciel.</li>
 * <li><b>Les repères</b> de l'agence : un clic droit en pose un, que tous ses membres voient ; sa fiche permet de le
 * renommer ou de le retirer.</li>
 * <li><b>Ce que l'agence connaît</b> grandit avec ses satellites d'observation : le pointillé de la limite s'élargit.</li>
 * <li><b>Un clic</b> sur une installation, un repère ou le terrain ouvre sa fiche.</li>
 * <li><b>Le curseur</b> donne les coordonnées, l'altitude et la nature du terrain qu'il survole.</li>
 * </ul>
 * La vue est de trois quarts : un coin du monde au fond, un bloc deux fois plus large que haut.
 * <p>
 * L'atlas s'ouvre depuis le pupitre d'un centre de contrôle, ou depuis la tablette de mission, partout à la surface,
 * tant qu'un satellite de télécommunications de l'agence relaie : il est alors centré sur l'endroit où l'on est.
 */
public class AtlasScreen extends Screen implements BlockScreen {
    private static final float ZOOM_NEAR = 2.0F;
    private static final float ZOOM_FAR = 0.02F;
    /** Largeur à l'écran, en pixels, en dessous de laquelle une case est trop petite : on passe à l'échelle suivante. */
    private static final float CELL_PIXELS = 5.6F;
    /** Tuiles demandées au serveur par tick, au plus. */
    private static final int REQUESTS_PER_TICK = 8;
    private static final int BUTTON_HEIGHT = 16;
    /** Hauteur de la barre du bas : ce que survole le curseur à gauche, les commandes à droite. */
    private static final int BAR = 34;
    /** Largeur à l'écran en dessous de laquelle un pas de tir est remplacé par sa miniature, en pixels. */
    private static final int PAD_MINIATURE = 16;
    /** Durée du fondu d'une tuile neuve, et délai au bout duquel on change d'orientation même si tout n'est pas prêt, en ms. */
    private static final long FADE_MS = 180L;
    private static final long TURN_WAIT_MS = 1500L;
    /** Coin du monde qui est au fond, pour chaque orientation. */
    private static final String[] CORNERS = {"nw", "ne", "se", "sw"};

    private final Screen parent;
    /**
     * Le pupitre depuis lequel l'atlas est consulté, ou l'endroit où la tablette a été ouverte : c'est de là que le
     * serveur répond.
     */
    private final BlockPos source;
    /** Vrai pour un atlas ouvert depuis la tablette de mission, relayé par satellite. */
    private final boolean tablet;
    /** Le point du monde au centre de l'écran, et l'échelle : pixels pour un demi-bloc en travers de l'écran. */
    private double centreX;
    private double centreZ;
    private float zoom = 0.5F;
    /** L'échelle vers laquelle le zoom glisse, et le point de l'écran qui ne bouge pas pendant ce temps. */
    private float zoomTarget = 0.5F;
    private double zoomAnchorX;
    private double zoomAnchorY;
    /** Orientation de la vue, de 0 à 3 (voir {@link #CORNERS}), et celle que l'on a demandée, en préparation. */
    private int turn;
    private int turnWanted;
    private long turnAsked;
    /**
     * Altitude du terrain au centre de l'écran, en blocs au-dessus de la mer, suivie en douceur : la vue descend
     * d'autant, pour que ce terrain-là soit au milieu de l'écran, qu'il soit au bord de l'eau ou en haut d'un plateau.
     */
    private float centreHeight;
    /** Décalage de la vue vers le bas qui en découle, en pixels, pour l'image en cours. */
    private float viewLift;
    private final int[] budget = {REQUESTS_PER_TICK};
    private int pending;
    /** Délai entre deux demandes des installations au serveur, en ticks. */
    private static final int SITES_EVERY = 40;
    private int age;
    /** Les installations reçues du serveur, et vrai dès la première réponse. */
    private List<AtlasSitesPayload.Site> sites = List.of();
    private boolean sitesReceived;
    /** Les calques montrés : ils restent tels qu'on les a laissés d'une ouverture de l'atlas à l'autre. */
    private static boolean showSites = com.orbitalis.client.ViewPrefs.getBoolean("atlas.layer.sites", true);
    private static boolean showFlights = com.orbitalis.client.ViewPrefs.getBoolean("atlas.layer.flights", true);
    private static boolean showZones = com.orbitalis.client.ViewPrefs.getBoolean("atlas.layer.zones", true);
    private static boolean showWeather = com.orbitalis.client.ViewPrefs.getBoolean("atlas.layer.weather", true);
    private static boolean showSatellites = com.orbitalis.client.ViewPrefs.getBoolean("atlas.layer.satellites", true);
    private static boolean showPins = com.orbitalis.client.ViewPrefs.getBoolean("atlas.layer.pins", true);
    /** Vrai quand la carte du ciel est dépliée ; repliée, elle tient en une ligne. */
    private static boolean skyOpen = com.orbitalis.client.ViewPrefs.getBoolean("atlas.sky", false);
    /** La place de la carte du ciel dans l'image en cours : x, y, largeur, hauteur ; {@code null} si elle n'est pas montrée. */
    private int[] skyBox;
    /** Les repères de l'agence, reçus avec les installations. */
    private List<AtlasSitesPayload.MarkerInfo> markers = List.of();
    /** Le repère que l'on vient de poser, dont la fiche s'ouvrira à son arrivée : x puis z. */
    private int[] pendingPin;
    /** Le champ de texte où l'on renomme un repère, et le numéro de ce repère. */
    private EditBox renaming;
    private int renamingId;
    /** Les boutons de la fiche d'un repère : x, y, largeur. */
    private int[] renameButton;
    private int[] deleteButton;
    /** Le ciel de l'agence, reçu avec les installations. */
    private AtlasSitesPayload.Sky sky = AtlasSitesPayload.Sky.NONE;
    /** Fraction de tick de l'image en cours : les nuages et les satellites avancent sans à-coup. */
    private float framePartial;
    private boolean layersOpen;
    /** Les repères cliquables de l'image en cours. */
    private final List<Hit> hits = new ArrayList<>();
    /** Ce que l'on a cliqué, dont la fiche est ouverte ; {@code null} sans fiche. */
    private Selection selected;
    /** Le bouton est enfoncé sur la carte : là où, et vrai si l'on a fait glisser la carte depuis. */
    private boolean pressing;
    private boolean dragged;
    private double pressX;
    private double pressY;

    public AtlasScreen(Screen parent, BlockPos source, BlockPos focus) {
        this(parent, source, focus, false);
    }

    public AtlasScreen(Screen parent, BlockPos source, BlockPos focus, boolean tablet) {
        super(Component.translatable("atlas.orbitalis.title"));
        this.parent = parent;
        this.tablet = tablet;
        // L'orientation de l'atlas, gardée d'une partie à l'autre.
        this.turn = this.turnWanted = Math.floorMod(com.orbitalis.client.ViewPrefs.getInt("atlas.turn", 0), 4);
        this.source = source.immutable();
        this.centreX = focus.getX() + 0.5;
        this.centreZ = focus.getZ() + 0.5;
    }

    /** Place la vue : le point au centre de l'écran, et l'échelle. */
    public void setView(double x, double z, float scale) {
        centreX = x;
        centreZ = z;
        zoom = zoomTarget = Mth.clamp(scale, ZOOM_FAR, ZOOM_NEAR);
        clampCentre();
    }

    /** Tourne la vue : le coin du monde qui est au fond, de 0 à 3. */
    public void setTurn(int corner) {
        turnWanted = Math.floorMod(corner, 4);
        com.orbitalis.client.ViewPrefs.set("atlas.turn", turnWanted);
        turnAsked = Util.getMillis();
    }

    // ------------------------------------------------------------------
    // Géométrie de la vue
    // ------------------------------------------------------------------

    /** Échelle des tuiles montrées à ce zoom : la plus fine dont les cases restent lisibles. */
    private int lod() {
        for (int lod = 0; lod < AtlasTiles.CELL.length - 1; lod++) {
            if (4.0F * AtlasTiles.CELL[lod] * zoom >= CELL_PIXELS) {
                return lod;
            }
        }
        return AtlasTiles.CELL.length - 1;
    }

    /** Place d'un point du monde dans la vue tournée : vers la droite du fond, puis vers la gauche de devant. */
    private double viewX(double x, double z) {
        return turn == 0 ? x : turn == 1 ? z : turn == 2 ? -x : -z;
    }

    private double viewZ(double x, double z) {
        return turn == 0 ? z : turn == 1 ? -x : turn == 2 ? -z : x;
    }

    private float screenX(double x, double z) {
        return width / 2.0F + (float) (((viewX(x, z) - viewX(centreX, centreZ)) - (viewZ(x, z) - viewZ(centreX, centreZ))) * 2.0 * zoom);
    }

    /** Hauteur à l'écran d'un point du monde pris au niveau de la mer. */
    private float screenY(double x, double z) {
        return height / 2.0F + viewLift
                + (float) (((viewX(x, z) - viewX(centreX, centreZ)) + (viewZ(x, z) - viewZ(centreX, centreZ))) * zoom);
    }

    /** Le point du monde, au niveau de la mer, qui se trouve sous ce pixel : x puis z. */
    private double[] worldAt(double pixelX, double pixelY) {
        double across = (pixelX - width / 2.0) / (2.0 * zoom);
        double down = (pixelY - height / 2.0 - viewLift) / zoom;
        double vx = viewX(centreX, centreZ) + (across + down) / 2.0;
        double vz = viewZ(centreX, centreZ) + (down - across) / 2.0;
        return turn == 0 ? new double[] {vx, vz} : turn == 1 ? new double[] {-vz, vx} : turn == 2 ? new double[] {-vx, -vz} : new double[] {vz, -vx};
    }

    /** Pixels d'écran pour un pixel d'image, à cette échelle : une case fait quatre fois son nombre de blocs, à l'échelle 1. */
    private float scale(int lod) {
        return 4.0F * AtlasTiles.CELL[lod] * zoom / AtlasPainter.CELL;
    }

    /** De combien de pixels un point à cette altitude est dessiné au-dessus du niveau de la mer, à l'échelle montrée. */
    private float lift(int lod, double y) {
        int sea = minecraft.level == null ? 63 : minecraft.level.getSeaLevel();
        return Math.round((float) (y - sea) / AtlasPainter.RELIEF[lod]) * AtlasPainter.STEP * scale(lod);
    }

    /** Rayon de ce que l'agence connaît autour du pupitre, en blocs : il grandit avec ses satellites d'observation. */
    private int known() {
        return sky.known();
    }

    /** La vue ne s'écarte pas de ce que le centre de contrôle connaît. */
    private void clampCentre() {
        double dx = centreX - source.getX();
        double dz = centreZ - source.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        double limit = known() * 1.1;
        if (distance > limit) {
            centreX = source.getX() + dx * limit / distance;
            centreZ = source.getZ() + dz * limit / distance;
        }
    }

    /** Case du relevé sous ce point du monde, à cette échelle ; 0 si elle n'est pas connue. */
    private int cellAt(int lod, int x, int z) {
        int cell = AtlasTiles.CELL[lod];
        int column = Math.floorDiv(x, cell);
        int row = Math.floorDiv(z, cell);
        AtlasMap.Tile tile = AtlasMap.peek(lod, Math.floorDiv(column, AtlasTiles.TILE), Math.floorDiv(row, AtlasTiles.TILE), turn);
        return tile == null || tile.cells == null ? 0
                : tile.cells[(Math.floorMod(row, AtlasTiles.TILE) + AtlasTiles.MARGIN) * AtlasTiles.SIDE + Math.floorMod(column, AtlasTiles.TILE) + AtlasTiles.MARGIN];
    }

    /** La vue suit l'altitude du terrain qu'elle regarde, et le zoom glisse vers l'échelle demandée, sans à-coup. */
    private void follow(int lod) {
        if (Math.abs(zoomTarget - zoom) > zoom * 0.002F) {
            // Le point sous le curseur ne bouge pas pendant que l'échelle change.
            double[] before = worldAt(zoomAnchorX, zoomAnchorY);
            zoom = Mth.lerp(0.28F, zoom, zoomTarget);
            double[] after = worldAt(zoomAnchorX, zoomAnchorY);
            centreX += before[0] - after[0];
            centreZ += before[1] - after[1];
            clampCentre();
        } else {
            zoom = zoomTarget;
        }
        int packed = cellAt(lod, Mth.floor(centreX), Mth.floor(centreZ));
        if (packed != 0) {
            float target = (packed >>> 24) - LaunchTableBlockEntity.MAP_HEIGHT_BIAS;
            centreHeight += (target - centreHeight) * 0.18F;
        }
        viewLift = centreHeight / AtlasPainter.RELIEF[lod] * AtlasPainter.STEP * scale(lod);
    }

    // ------------------------------------------------------------------
    // Commandes
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        budget[0] = REQUESTS_PER_TICK;
        if (age++ % SITES_EVERY == 0) {
            ClientPacketDistributor.sendToServer(new AtlasSitesRequestPayload(source));
        }
    }

    /** Le serveur envoie les installations : elles vont à l'atlas ouvert depuis ce pupitre. */
    public static void acceptSites(AtlasSitesPayload payload) {
        if (Minecraft.getInstance().gui.screen() instanceof AtlasScreen atlas && atlas.source.equals(payload.source())) {
            atlas.sites = payload.sites();
            atlas.sky = payload.sky();
            atlas.markers = payload.markers();
            atlas.sitesReceived = true;
            if (atlas.pendingPin != null) {
                for (AtlasSitesPayload.MarkerInfo marker : atlas.markers) {
                    if (marker.x() == atlas.pendingPin[0] && marker.z() == atlas.pendingPin[1]) {
                        atlas.selected = new Selection(null, marker, marker.x() + 0.5, marker.z() + 0.5);
                        atlas.pendingPin = null;
                    }
                }
            }
        }
    }

    /** Ferme la fiche ouverte (sert aux captures de vérification). */
    public void clearSelection() {
        selected = null;
    }

    /** Ouvre la fiche du premier repère de l'agence (sert aux captures de vérification). */
    public void selectFirstPin() {
        if (!markers.isEmpty()) {
            AtlasSitesPayload.MarkerInfo marker = markers.get(0);
            selected = new Selection(null, marker, marker.x() + 0.5, marker.z() + 0.5);
        }
    }

    /** Ouvre ou ferme le panneau des calques (sert aux captures de vérification). */
    public void showLayers(boolean open) {
        layersOpen = open;
    }

    /** Ouvre la fiche d'une installation (sert aux captures de vérification). */
    public void select(BlockPos pos) {
        for (AtlasSitesPayload.Site site : shownSites()) {
            if (site.pos().equals(pos)) {
                selected = new Selection(site, null, pos.getX() + 0.5, pos.getZ() + 0.5);
            }
        }
    }

    /**
     * Un bouton de la barre : ce qu'il affiche, où il est, s'il est le bouton principal, ce qu'il fait, et vrai pour
     * celui des calques.
     */
    private record Button(Component label, int x, int y, int w, boolean primary, Runnable action, boolean layers) {
    }

    /** Les commandes de la barre du bas, de droite à gauche : retour, moi, centre, vue, zoom. */
    private List<Button> buttons() {
        List<Button> buttons = new ArrayList<>();
        int right = width - 14;
        int y = height - 8 - BAR + (BAR - BUTTON_HEIGHT) / 2;
        right = add(buttons, Component.translatable("atlas.orbitalis.back"), right, y, true, this::onClose);
        right = add(buttons, Component.translatable("atlas.orbitalis.goto.me"), right, y, false, () -> {
            if (minecraft.player != null) {
                setView(minecraft.player.getX(), minecraft.player.getZ(), Math.max(zoom, 0.5F));
            }
        });
        if (!tablet) {
            right = add(buttons, Component.translatable("atlas.orbitalis.goto.centre"), right, y, false,
                    () -> setView(source.getX() + 0.5, source.getZ() + 0.5, Math.max(zoom, 0.5F)));
        }
        right = add(buttons, Component.translatable("atlas.orbitalis.view", Component.translatable("atlas.orbitalis.corner." + CORNERS[turnWanted])), right, y,
                false, () -> setTurn(turnWanted + 1));
        right = add(buttons, Component.literal("+"), right, y, false, () -> zoomBy(1.6F));
        right = add(buttons, Component.literal("\u2212"), right, y, false, () -> zoomBy(1.0F / 1.6F));
        Component layers = Component.translatable("atlas.orbitalis.layers");
        int w = Math.max(BUTTON_HEIGHT, font.width(layers.getString().toUpperCase(java.util.Locale.ROOT)) + 12);
        buttons.add(new Button(layers, right - w, y, w, layersOpen, () -> layersOpen = !layersOpen, true));
        return buttons;
    }

    private int add(List<Button> buttons, Component label, int right, int y, boolean primary, Runnable action) {
        int w = Math.max(BUTTON_HEIGHT, font.width(label.getString().toUpperCase(java.util.Locale.ROOT)) + 12);
        buttons.add(new Button(label, right - w, y, w, primary, action, false));
        return right - w - 4;
    }

    /** Zoome ou recule d'un cran, autour du centre de l'écran. */
    private void zoomBy(float factor) {
        zoomTarget = Mth.clamp(zoomTarget * factor, ZOOM_FAR, ZOOM_NEAR);
        zoomAnchorX = width / 2.0;
        zoomAnchorY = height / 2.0;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (Button button : buttons()) {
            if (Ui.inside(event.x(), event.y(), button.x(), button.y(), button.w(), BUTTON_HEIGHT)) {
                Ui.click();
                button.action().run();
                return true;
            }
        }
        if (layersOpen) {
            int[] panel = layersPanel();
            if (Ui.inside(event.x(), event.y(), panel[0], panel[1], panel[2], panel[3])) {
                int row = (int) ((event.y() - panel[1] - 4) / LAYER_ROW);
                if (row >= 0 && row < LAYERS.length) {
                    Ui.click();
                    toggleLayer(row);
                }
                return true;
            }
        }
        if (skyBox != null && Ui.inside(event.x(), event.y(), skyBox[0], skyBox[1], skyBox[2], skyBox[3])) {
            Ui.click();
            skyOpen = !skyOpen;
            com.orbitalis.client.ViewPrefs.set("atlas.sky", skyOpen);
            return true;
        }
        if (renaming != null && renaming.isMouseOver(event.x(), event.y())) {
            return renaming.mouseClicked(event, doubleClick);
        }
        if (selected != null) {
            int[] box = cardBox();
            if (Ui.inside(event.x(), event.y(), box[0] + box[2] - 16, box[1] + 4, 12, 12)) {
                Ui.click();
                selected = null;
                stopRenaming();
                return true;
            }
            if (selected.marker() != null && renaming == null && renameButton != null
                    && Ui.inside(event.x(), event.y(), renameButton[0], renameButton[1], renameButton[2], BUTTON_HEIGHT)) {
                Ui.click();
                startRenaming(selected.marker(), renameButton[0], renameButton[1] + 1, deleteButton[0] + deleteButton[2] - renameButton[0]);
                return true;
            }
            if (selected.marker() != null && renaming == null && deleteButton != null
                    && Ui.inside(event.x(), event.y(), deleteButton[0], deleteButton[1], deleteButton[2], BUTTON_HEIGHT)) {
                Ui.click();
                ClientPacketDistributor.sendToServer(new AtlasMarkerPayload(source, AtlasMarkerPayload.REMOVE, selected.marker().id(), 0, 0, 0, ""));
                selected = null;
                return true;
            }
        }
        // Un clic droit sur la carte pose un repère de l'agence.
        if (event.button() == 1 && event.y() < height - 8 - BAR) {
            stopRenaming();
            placePin(event.x(), event.y());
            return true;
        }
        // Sur la carte : un glissement la déplace, un simple clic ouvre la fiche de ce qui est dessous.
        if (event.button() == 0 && event.y() < height - 8 - BAR) {
            pressing = true;
            dragged = false;
            pressX = event.x();
            pressY = event.y();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (pressing && !dragged) {
            query(event.x(), event.y());
        }
        pressing = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (pressing && (event.x() - pressX) * (event.x() - pressX) + (event.y() - pressY) * (event.y() - pressY) > 9.0) {
            dragged = true;
        }
        // Le terrain suit la main : le point saisi reste sous le curseur.
        double[] before = worldAt(event.x() - dx, event.y() - dy);
        double[] after = worldAt(event.x(), event.y());
        centreX += before[0] - after[0];
        centreZ += before[1] - after[1];
        clampCentre();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Le zoom se fait autour du curseur : ce qu'il désigne ne bouge pas.
        zoomTarget = Mth.clamp(zoomTarget * (float) Math.pow(1.25, scrollY), ZOOM_FAR, ZOOM_NEAR);
        zoomAnchorX = mouseX;
        zoomAnchorY = mouseY;
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------
    // Dessin
    // ------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, width, height, 0xFF0C1422, 0xFF05080E);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (minecraft.level == null) {
            return;
        }
        framePartial = partialTick;
        long now = Util.getMillis() / 50L;
        pending = AtlasMap.pump(now);
        int lod = lod();
        follow(lod);
        prepareTurn(lod, now);
        extractTerrain(graphics, lod, now);
        extractLimit(graphics);
        extractMarkers(graphics, lod);
        extractHeader(graphics);
        extractScale(graphics);
        extractCard(graphics, lod, mouseX, mouseY);
        extractSky(graphics, mouseX, mouseY);
        // La barre du bas : ce que survole le curseur à gauche, les commandes à droite.
        int barY = height - 8 - BAR;
        Ui.card(graphics, 8, barY, width - 16, BAR, false);
        int leftmost = width;
        for (Button button : buttons()) {
            Ui.button(graphics, font, button.label(), button.x(), button.y(), button.w(), BUTTON_HEIGHT,
                    Ui.inside(mouseX, mouseY, button.x(), button.y(), button.w(), BUTTON_HEIGHT), true, button.primary(), Ui.ACCENT);
            leftmost = Math.min(leftmost, button.x());
        }
        extractCursor(graphics, lod, mouseX, mouseY, barY, leftmost - 24);
        extractLayers(graphics, mouseX, mouseY);
    }

    /** Tuiles de cette échelle que l'écran laisse voir, dans l'orientation en cours : colonne puis ligne de chacune. */
    private List<int[]> visible(int lod) {
        int span = AtlasTiles.TILE * AtlasTiles.CELL[lod];
        // Le rectangle du monde que couvre l'écran, élargi : un relief dépasse vers le haut, un creux vers le bas.
        double margin = AtlasPainter.HEAD * scale(lod);
        double[][] corners = {worldAt(0, -margin), worldAt(width, -margin), worldAt(0, height + margin), worldAt(width, height + margin)};
        double minX = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (double[] corner : corners) {
            minX = Math.min(minX, corner[0]);
            maxX = Math.max(maxX, corner[0]);
            minZ = Math.min(minZ, corner[1]);
            maxZ = Math.max(maxZ, corner[1]);
        }
        List<int[]> tiles = new ArrayList<>();
        for (int tz = Math.floorDiv((int) Math.floor(minZ), span); tz <= Math.floorDiv((int) Math.floor(maxZ), span); tz++) {
            for (int tx = Math.floorDiv((int) Math.floor(minX), span); tx <= Math.floorDiv((int) Math.floor(maxX), span); tx++) {
                if (onScreen(lod, tx, tz)) {
                    tiles.add(new int[] {tx, tz});
                }
            }
        }
        return tiles;
    }

    /**
     * Prépare l'orientation demandée : ses tuiles sont voulues d'avance, et l'on n'y passe qu'une fois qu'elles sont
     * prêtes (ou au bout d'un moment, si le serveur tarde). D'ici là, la vue en cours reste à l'écran.
     */
    private void prepareTurn(int lod, long now) {
        if (turnWanted == turn) {
            return;
        }
        int current = turn;
        turn = turnWanted;
        boolean ready = true;
        for (int[] at : visible(lod)) {
            AtlasMap.Tile tile = AtlasMap.want(minecraft.level, source, lod, at[0], at[1], turn, now, budget);
            ready &= tile != null && (tile.state == AtlasMap.State.READY || tile.state == AtlasMap.State.UNKNOWN);
        }
        if (!ready && Util.getMillis() - turnAsked < TURN_WAIT_MS) {
            turn = current;
        }
    }

    /** Une image à dessiner : sa tuile, son plan (0 pour un fond plus grossier, 1 pour le reste), son rang du fond vers l'avant. */
    private record Placed(AtlasMap.Tile tile, int layer, double depth, int alpha) {
    }

    /**
     * Le terrain : les tuiles que l'écran laisse voir, du fond vers l'avant. Celles qui manquent sont demandées ; en
     * attendant, ce que l'on a déjà de l'endroit est dessiné à leur place, plus grossier ou plus fin.
     */
    private void extractTerrain(GuiGraphicsExtractor graphics, int lod, long now) {
        long clock = Util.getMillis();
        List<Placed> placed = new ArrayList<>();
        Set<AtlasMap.Tile> used = new HashSet<>();
        Set<Long> parents = new HashSet<>();
        for (int[] at : visible(lod)) {
            int tx = at[0];
            int tz = at[1];
            parents.add((long) Math.floorDiv(tx, 2) << 32 | Math.floorDiv(tz, 2) & 0xFFFFFFFFL);
            AtlasMap.Tile tile = AtlasMap.want(minecraft.level, source, lod, tx, tz, turn, now, budget);
            int alpha = 255;
            if (tile != null && tile.state == AtlasMap.State.READY && tile.id != null) {
                alpha = (int) Math.clamp((clock - tile.readyAt) * 255L / FADE_MS, 0L, 255L);
                placed.add(new Placed(tile, 1, depth(tile), alpha));
                if (alpha >= 255) {
                    continue;
                }
            } else if (tile != null && tile.state == AtlasMap.State.UNKNOWN) {
                continue;
            }
            // Pas encore là, ou en train d'apparaître : ce que l'on a déjà de l'endroit reste dessous.
            if (!below(placed, used, lod, tx, tz)) {
                above(placed, used, lod, tx, tz, 2);
            }
        }
        // L'échelle d'après est demandée d'avance, s'il reste de quoi : en reculant, la carte est déjà là.
        if (lod + 1 < AtlasTiles.CELL.length) {
            for (long parent : parents) {
                if (budget[0] <= 1) {
                    break;
                }
                AtlasMap.want(minecraft.level, source, lod + 1, (int) (parent >> 32), (int) parent, turn, now, budget);
            }
        }
        placed.sort((a, b) -> a.layer() != b.layer() ? Integer.compare(a.layer(), b.layer()) : Double.compare(a.depth(), b.depth()));
        for (Placed image : placed) {
            blit(graphics, image.tile(), image.alpha(), now);
        }
    }

    /** Rang d'une tuile du fond vers l'avant : la place, dans la vue, de son coin du fond. */
    private double depth(AtlasMap.Tile tile) {
        double[] corner = corner(tile.lod, tile.tx, tile.tz);
        return viewX(corner[0], corner[1]) + viewZ(corner[0], corner[1]);
    }

    /** Ajoute la tuile plus grossière qui couvre celle-ci, si elle est prête. */
    private boolean below(List<Placed> placed, Set<AtlasMap.Tile> used, int lod, int tx, int tz) {
        int px = tx;
        int pz = tz;
        for (int up = lod + 1; up < AtlasTiles.CELL.length; up++) {
            px = Math.floorDiv(px, 2);
            pz = Math.floorDiv(pz, 2);
            AtlasMap.Tile parent = AtlasMap.peek(up, px, pz, turn);
            if (parent != null && parent.state == AtlasMap.State.READY && parent.id != null) {
                if (used.add(parent)) {
                    placed.add(new Placed(parent, 0, depth(parent) - up * 1.0E7, 255));
                }
                return true;
            }
        }
        return false;
    }

    /** Ajoute les tuiles plus fines que couvre celle-ci, celles qui sont prêtes, sur quelques échelles. */
    private void above(List<Placed> placed, Set<AtlasMap.Tile> used, int lod, int tx, int tz, int levels) {
        if (lod == 0 || levels == 0) {
            return;
        }
        for (int k = 0; k < 4; k++) {
            int cx = tx * 2 + (k & 1);
            int cz = tz * 2 + (k >> 1);
            AtlasMap.Tile child = AtlasMap.peek(lod - 1, cx, cz, turn);
            if (child != null && child.state == AtlasMap.State.READY && child.id != null) {
                if (used.add(child)) {
                    placed.add(new Placed(child, 1, depth(child), 255));
                }
            } else {
                above(placed, used, lod - 1, cx, cz, levels - 1);
            }
        }
    }

    /** Le coin du fond d'une tuile dans la vue, débord compris : le point du monde où commence son image. */
    private double[] corner(int lod, int tx, int tz) {
        int cell = AtlasTiles.CELL[lod];
        double x0 = ((double) tx * AtlasTiles.TILE - AtlasPainter.APRON) * cell;
        double z0 = ((double) tz * AtlasTiles.TILE - AtlasPainter.APRON) * cell;
        double x1 = x0 + (AtlasTiles.TILE + 2 * AtlasPainter.APRON) * cell;
        double z1 = z0 + (AtlasTiles.TILE + 2 * AtlasPainter.APRON) * cell;
        return turn == 0 ? new double[] {x0, z0} : turn == 1 ? new double[] {x1, z0} : turn == 2 ? new double[] {x1, z1} : new double[] {x0, z1};
    }

    /**
     * Le rectangle de l'écran que couvre l'image d'une tuile : gauche, haut, droite, bas.
     *
     * @param base hauteur du point le plus bas de la tuile, en crans au-dessus de la mer : la ligne de repère de l'image
     */
    private float[] frame(int lod, int tx, int tz, int base) {
        float scale = scale(lod);
        double[] corner = corner(lod, tx, tz);
        float left = screenX(corner[0], corner[1]) - AtlasPainter.WIDTH / 2.0F * scale;
        float top = screenY(corner[0], corner[1]) - (AtlasPainter.HEAD + base * AtlasPainter.STEP) * scale;
        return new float[] {left, top, left + AtlasPainter.WIDTH * scale, top + AtlasPainter.HEIGHT * scale};
    }

    /** Vrai si cette tuile peut toucher l'écran, quel que soit son relief (on ne le connaît pas avant de l'avoir reçue). */
    private boolean onScreen(int lod, int tx, int tz) {
        float[] frame = frame(lod, tx, tz, 0);
        // Le terrain peut être bien plus haut ou plus bas que la mer : la marge couvre tout ce que le monde permet.
        float slack = 200.0F / AtlasPainter.RELIEF[lod] * AtlasPainter.STEP * scale(lod);
        return frame[2] > 0 && frame[0] < width && frame[3] + slack > 0 && frame[1] - slack < height;
    }

    private void blit(GuiGraphicsExtractor graphics, AtlasMap.Tile tile, int alpha, long now) {
        if (tile.id == null || alpha <= 0) {
            return;
        }
        AtlasMap.touch(tile, now);
        float[] frame = frame(tile.lod, tile.tx, tile.tz, tile.base);
        int x0 = Math.round(frame[0]);
        int y0 = Math.round(frame[1]);
        int x1 = Math.max(x0 + 1, Math.round(frame[2]));
        int y1 = Math.max(y0 + 1, Math.round(frame[3]));
        if (x1 <= 0 || y1 <= 0 || x0 >= width || y0 >= height) {
            return;
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, tile.id, x0, y0, 0.0F, 0.0F, x1 - x0, y1 - y0, AtlasPainter.WIDTH, AtlasPainter.HEIGHT,
                AtlasPainter.WIDTH, AtlasPainter.HEIGHT, alpha << 24 | 0xFFFFFF);
    }

    /** La limite de ce que le centre de contrôle connaît : un pointillé, au niveau de la mer. */
    private void extractLimit(GuiGraphicsExtractor graphics) {
        int dots = 180;
        for (int k = 0; k < dots; k++) {
            double angle = k * Math.PI * 2.0 / dots;
            double x = source.getX() + Math.cos(angle) * known();
            double z = source.getZ() + Math.sin(angle) * known();
            int px = Math.round(screenX(x, z));
            int py = Math.round(screenY(x, z));
            if (px >= 0 && py >= 0 && px < width && py < height) {
                graphics.fill(px, py, px + 2, py + 1, 0xFF5A6B80);
            }
        }
    }

    /** Étiquettes déjà posées pendant cette image : gauche, haut, droite, bas. */
    private final List<int[]> labels = new ArrayList<>();

    /**
     * Un pas de tir en miniature, posé là où il se trouve : son tablier, sa tour de service rayée de rouge, et sa
     * fusée s'il en porte une. Toujours à la même taille, pour qu'on le reconnaisse d'aussi loin qu'on regarde.
     */
    private void miniature(GuiGraphicsExtractor graphics, int x, int ground, boolean rocket) {
        int half = 13;
        // L'ombre, puis le tablier : un losange épais, bordé de rouge.
        for (int dy = -6; dy <= 6; dy++) {
            int reach = half * (6 - Math.abs(dy)) / 6;
            graphics.fill(x - reach - 1, ground + dy + 4, x + reach + 2, ground + dy + 5, 0x50000000);
        }
        for (int dy = -6; dy <= 6; dy++) {
            int reach = half * (6 - Math.abs(dy)) / 6;
            graphics.fill(x - reach, ground + dy, x + reach + 1, ground + dy + 1, 0xFFC4C9D1);
            graphics.fill(x - reach, ground + dy, x - reach + 1, ground + dy + 1, 0xFFFF5A52);
            graphics.fill(x + reach, ground + dy, x + reach + 1, ground + dy + 1, 0xFFFF5A52);
        }
        for (int dx = -half; dx <= half; dx++) {
            int edge = ground + 6 - Math.abs(dx) * 6 / half;
            graphics.fill(x + dx, edge + 1, x + dx + 1, edge + 4, dx < 0 ? 0xFF8F959E : 0xFF6D737C);
        }
        // Le carneau, devant ; la tour, derrière à gauche ; la fusée, au milieu.
        graphics.fill(x - 2, ground + 2, x + 3, ground + 5, 0xFF3A3F47);
        int towerX = x - 8;
        graphics.fill(towerX, ground - 21, towerX + 3, ground - 1, 0xFFE4E8EE);
        graphics.fill(towerX + 3, ground - 21, towerX + 4, ground - 1, 0xFF9AA1AB);
        for (int band = ground - 19; band < ground - 2; band += 6) {
            graphics.fill(towerX, band, towerX + 3, band + 2, 0xFFE0453A);
        }
        graphics.fill(towerX + 3, ground - 15, x - 1, ground - 14, 0xFF9AA1AB);
        if (rocket) {
            graphics.fill(x - 1, ground - 17, x + 2, ground, 0xFFF6F8FA);
            graphics.fill(x + 2, ground - 17, x + 3, ground, 0xFFB9C0C9);
            graphics.fill(x - 1, ground - 20, x + 2, ground - 17, 0xFFE0453A);
            graphics.fill(x, ground - 22, x + 1, ground - 20, 0xFFE0453A);
            graphics.fill(x - 2, ground - 3, x - 1, ground + 1, 0xFF2B3440);
            graphics.fill(x + 3, ground - 3, x + 4, ground + 1, 0xFF2B3440);
        }
    }

    /** Une étiquette, à cet endroit ou juste au-dessus si elle en recouvrirait une autre. */
    private void label(GuiGraphicsExtractor graphics, int x, int y, Component name, int color) {
        String text = name.getString();
        int w = font.width(text) + 6;
        int labelY = y;
        boolean free = false;
        for (int tries = 0; tries < 6 && !free; tries++) {
            free = true;
            for (int[] other : labels) {
                free &= x + w <= other[0] || x >= other[2] || labelY + 11 <= other[1] || labelY >= other[3];
            }
            if (!free) {
                labelY -= 12;
            }
        }
        // Pas de place libre tout près : l'étiquette n'est pas posée, plutôt que d'en recouvrir une autre. Son repère
        // reste, et un clic dessus ouvre sa fiche.
        if (!free) {
            return;
        }
        labels.add(new int[] {x, labelY, x + w, labelY + 11});
        Ui.round(graphics, x, labelY, w, 11, 0xE00A0E14);
        graphics.text(font, text, x + 3, labelY + 2, color, false);
    }

    /**
     * Un repère : un losange posé sur le terrain, au bout d'un trait, et son nom sur une étiquette. Rend la place du
     * losange, ou {@code null} s'il est hors de l'écran.
     */
    private int[] marker(GuiGraphicsExtractor graphics, int lod, double x, double y, double z, Component name, int color) {
        int px = Math.round(screenX(x, z));
        int ground = Math.round(screenY(x, z) - lift(lod, y));
        if (px < -40 || px > width + 40 || ground < -20 || ground > height + 40) {
            return null;
        }
        int top = ground - 12;
        graphics.fill(px, top + 3, px + 1, ground, color);
        for (int k = 0; k <= 3; k++) {
            graphics.fill(px - (3 - k), top - 3 + k, px + 1 + (3 - k), top - 2 + k, color);
            graphics.fill(px - (3 - k), top + 3 - k, px + 1 + (3 - k), top + 4 - k, color);
        }
        label(graphics, px + 7, top - 5, name, color);
        return new int[] {px, top};
    }

    // ------------------------------------------------------------------
    // Calques : installations, vols, zones de danger
    // ------------------------------------------------------------------

    /** Un repère cliquable posé pendant cette image : l'installation ou le repère de l'agence, et sa place. */
    private record Hit(AtlasSitesPayload.Site site, AtlasSitesPayload.MarkerInfo marker, int x, int y) {
    }

    /** Les repères, de l'arrière vers l'avant : les vols et les zones d'abord, au sol ; puis les installations ; puis le joueur. */
    private void extractMarkers(GuiGraphicsExtractor graphics, int lod) {
        labels.clear();
        hits.clear();
        List<AtlasSitesPayload.Site> shown = shownSites();
        boolean weather = showWeather && hasWeather();
        if (weather) {
            extractClouds(graphics, lod, true);
        }
        if (showZones) {
            for (AtlasSitesPayload.Site site : shown) {
                extractZone(graphics, lod, site);
            }
        }
        if (showSatellites && sitesReceived) {
            extractSatellites(graphics, lod);
        }
        if (weather) {
            extractClouds(graphics, lod, false);
            if (raining()) {
                extractRain(graphics);
            }
        }
        if (showSites) {
            for (AtlasSitesPayload.Site site : shown) {
                extractSite(graphics, lod, site);
            }
        } else if (!tablet) {
            // Sans le calque des installations, le centre de contrôle d'où l'on regarde reste marqué.
            marker(graphics, lod, source.getX() + 0.5, source.getY(), source.getZ() + 0.5, Component.translatable("atlas.orbitalis.marker.centre"), Ui.ACCENT);
        }
        if (showPins) {
            extractPins(graphics, lod);
        }
        if (minecraft.player != null) {
            marker(graphics, lod, minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ(),
                    Component.translatable("atlas.orbitalis.marker.you"), 0xFFFFFFFF);
        }        // Les vols par-dessus tout : une fusée qui part est ce qu'on cherche des yeux.
        if (showFlights) {
            for (AtlasSitesPayload.Site site : shown) {
                extractFlight(graphics, lod, site);
            }
        }
    }

    /**
     * Les installations à montrer : celles que le serveur a envoyées ; tant qu'il n'a rien répondu, les pas de tir
     * chargés autour du joueur et le centre de contrôle.
     */
    private List<AtlasSitesPayload.Site> shownSites() {
        if (sitesReceived) {
            return sites;
        }
        List<AtlasSitesPayload.Site> guess = new ArrayList<>();
        for (BlockPos pad : LaunchTableBlockEntity.loadedTables(minecraft.level)) {
            guess.add(AtlasSitesPayload.Site.bare(AtlasSitesPayload.PAD, pad));
        }
        if (!tablet) {
            guess.add(AtlasSitesPayload.Site.bare(AtlasSitesPayload.CONSOLE, source));
        }
        return guess;
    }

    /** Couleur d'un pas de tir, selon son état. */
    private static int padColor(AtlasSitesPayload.Site site) {
        return switch (site.phase()) {
            case AtlasSitesPayload.FLIGHT -> Ui.OK;
            case AtlasSitesPayload.COUNTDOWN -> Ui.WARN;
            case AtlasSitesPayload.IDLE -> site.rocket() ? Ui.ACCENT : 0xFFFF5A52;
            default -> 0xFFFF5A52;
        };
    }

    /** L'état d'un pas de tir, en quelques mots. */
    private static Component padState(AtlasSitesPayload.Site site) {
        return switch (site.phase()) {
            case AtlasSitesPayload.FLIGHT -> Component.translatable("atlas.orbitalis.pad.flight", site.ticks() / 20);
            case AtlasSitesPayload.COUNTDOWN -> Component.translatable("atlas.orbitalis.pad.countdown",
                    Math.max(0, (LaunchTableBlockEntity.COUNTDOWN_TICKS - site.ticks()) / 20));
            case AtlasSitesPayload.IDLE -> site.rocket() ? Component.translatable("atlas.orbitalis.pad.ready", site.fuel())
                    : Component.translatable("atlas.orbitalis.pad.empty");
            default -> Component.translatable("atlas.orbitalis.pad.unknown");
        };
    }

    /** Une installation : sa miniature ou son repère, et son étiquette, avec l'état d'un pas de tir. */
    private void extractSite(GuiGraphicsExtractor graphics, int lod, AtlasSitesPayload.Site site) {
        BlockPos pos = site.pos();
        if (site.kind() == AtlasSitesPayload.PAD) {
            Component name = site.phase() == AtlasSitesPayload.UNKNOWN ? Component.translatable("atlas.orbitalis.marker.pad")
                    : Component.translatable("atlas.orbitalis.marker.pad_state", Component.translatable("atlas.orbitalis.marker.pad"), padState(site));
            int color = padColor(site);
            LaunchTableBlockEntity table = minecraft.level.getBlockEntity(pos) instanceof LaunchTableBlockEntity found ? found : null;
            int size = site.size() > 0 ? site.size() : table == null ? 5 : 2 * table.getComplex().radius() + 1;
            // De près, on voit le pas de tir lui-même, bloc par bloc. De loin, il ne serait plus qu'un point : sa
            // miniature le remplace, toujours à la même taille.
            if (size * 4.0F * zoom < PAD_MINIATURE) {
                int px = Math.round(screenX(pos.getX() + 0.5, pos.getZ() + 0.5));
                int ground = Math.round(screenY(pos.getX() + 0.5, pos.getZ() + 0.5) - lift(lod, pos.getY()));
                if (px > -40 && px < width + 40 && ground > -40 && ground < height + 40) {
                    boolean rocket = site.rocket() && site.phase() != AtlasSitesPayload.FLIGHT || table != null && table.hasRocket()
                            && table.getPhase() != LaunchTableBlockEntity.Phase.FLIGHT;
                    miniature(graphics, px, ground, rocket);
                    label(graphics, px + 16, ground - 22, name, color);
                    hits.add(new Hit(site, null, px, ground - 6));
                }
            } else {
                int[] at = marker(graphics, lod, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, name, color);
                if (at != null) {
                    hits.add(new Hit(site, null, at[0], at[1]));
                }
            }
            return;
        }
        boolean here = !tablet && site.kind() == AtlasSitesPayload.CONSOLE && pos.equals(source);
        Component name = Component.translatable(site.kind() == AtlasSitesPayload.STATION ? "atlas.orbitalis.marker.station"
                : here ? "atlas.orbitalis.marker.centre" : "atlas.orbitalis.marker.console");
        int[] at = marker(graphics, lod, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, name, site.kind() == AtlasSitesPayload.STATION ? Ui.OK : Ui.ACCENT);
        if (at != null) {
            hits.add(new Hit(site, null, at[0], at[1]));
        }
    }

    /** Cap d'un vol : la route que la fusée suit dans le monde, ou, tant qu'elle monte droit, l'axe de son carneau. */
    private static double[] heading(AtlasSitesPayload.Site site) {
        double drift = Math.sqrt(site.driftX() * site.driftX() + site.driftZ() * site.driftZ());
        if (drift > 4.0) {
            return new double[] {site.driftX() / drift, site.driftZ() / drift};
        }
        Direction trench = Direction.from2DDataValue(site.heading());
        return new double[] {trench.getStepX(), trench.getStepZ()};
    }

    /**
     * Un vol en cours, vu de trois quarts : la trajectoire de la fusée dans le ciel. Elle part à la verticale du pas de
     * tir, bascule peu à peu vers son cap, et file vers l'espace en s'estompant ; un lanceur orbital finit presque à
     * plat, une fusée-sonde monte presque droit. Jusqu'à la fusée, la route suivie, d'un trait plein ; ensuite, la route
     * prévue, en tirets. Son ombre est au sol, en pointillés, et un fil relie la fusée à son ombre. Au bout, la
     * destination.
     */
    private void extractFlight(GuiGraphicsExtractor graphics, int lod, AtlasSitesPayload.Site site) {
        if (site.kind() != AtlasSitesPayload.PAD || site.phase() != AtlasSitesPayload.FLIGHT) {
            return;
        }
        BlockPos pad = site.pos();
        double[] head = heading(site);
        double padX = pad.getX() + 0.5;
        double padZ = pad.getZ() + 0.5;
        boolean orbital = site.reach() >= RocketStats.Reach.LOW_ORBIT.ordinal();
        // La forme de la courbe, en blocs : la distance au sol qu'elle couvre, et la hauteur qu'elle atteint, à l'échelle
        // de ce que l'on connaît. Un vol orbital s'en va loin vers l'horizon ; un vol suborbital reste au-dessus du site.
        double ground = known() * (orbital ? 0.4 : 0.04);
        double top = known() * (orbital ? 0.4 : 0.45);
        // La courbe tient dans le cadre, de près comme de loin : au besoin, elle est réduite tout entière, hauteur et
        // portée ensemble, pour garder sa forme (on la voit toujours monter, basculer et partir).
        double fit = Math.min(1.0, Math.min(height * 0.45 / (top * 2.0 * zoom), width * 0.35 / Math.max(1.0, ground * 2.3 * zoom)));
        ground *= fit;
        top *= fit;
        float rise = lift(lod, pad.getY());
        // La durée du vol de ce pas de tir arrive dans « extra ».
        float flown = Mth.clamp(site.ticks() / (float) (site.extra() > 0 ? site.extra() : LaunchTableBlockEntity.DEFAULT_FLIGHT_TICKS), 0.0F, 1.0F);
        int samples = 96;
        float[][] points = new float[samples + 1][];
        for (int k = 0; k <= samples; k++) {
            points[k] = arcPoint(padX, padZ, head, ground, top, k / (float) samples, rise);
        }
        // L'ombre au sol, en pointillés pâles.
        double endX = padX + head[0] * ground;
        double endZ = padZ + head[1] * ground;
        dashes(graphics, padX, padZ, endX, endZ, rise, 0x70DCE6F2, 2.0F);
        // La courbe : un liseré sombre d'abord, puis le trait, plein jusqu'à la fusée, en tirets ensuite, de plus en plus
        // pâle en montant.
        for (int pass = 0; pass < 2; pass++) {
            float run = 0.0F;
            for (int k = 1; k <= samples; k++) {
                float t = k / (float) samples;
                float[] a = points[k - 1];
                float[] b = points[k];
                float length = Mth.sqrt((b[0] - a[0]) * (b[0] - a[0]) + (b[1] - a[1]) * (b[1] - a[1]));
                int steps = Math.max(1, (int) Math.ceil(length));
                for (int s = 1; s <= steps; s++) {
                    float u = s / (float) steps;
                    run += length / steps;
                    boolean done = t - (1.0F - u) / samples <= flown;
                    if (!done && run % 8.0F >= 4.0F) {
                        continue;
                    }
                    int px = Math.round(Mth.lerp(u, a[0], b[0]));
                    int py = Math.round(Mth.lerp(u, a[1], b[1]));
                    if (px < 0 || py < 0 || px >= width || py >= height) {
                        continue;
                    }
                    if (pass == 0) {
                        graphics.fill(px - 1, py - 1, px + 3, py + 3, done ? 0x90142030 : Ui.alpha(0x142030, 0.6F * (1.0F - t * 0.5F)));
                    } else {
                        int color = done ? 0xFFFFC857 : Ui.alpha(0xE9F2FF, 1.0F - t * 0.5F);
                        graphics.fill(px, py, px + 2, py + 2, color);
                    }
                }
            }
        }
        // La fusée, sur sa courbe, et le fil qui la relie à son ombre.
        float[] rocket = arcPoint(padX, padZ, head, ground, top, flown, rise);
        double shadowX = padX + head[0] * ground * flown * flown;
        double shadowZ = padZ + head[1] * ground * flown * flown;
        int sx = Math.round(screenX(shadowX, shadowZ));
        int sy = Math.round(screenY(shadowX, shadowZ) - rise);
        int px = Math.round(rocket[0]);
        int py = Math.round(rocket[1]);
        for (int y = Math.min(py, sy) + 2; y < Math.max(py, sy); y += 3) {
            int x = Math.round(Mth.lerp((y - py) / (float) Math.max(1, sy - py), px, sx));
            if (x >= 0 && y >= 0 && x < width && y < height) {
                graphics.fill(x, y, x + 1, y + 1, 0x80DCE6F2);
            }
        }
        graphics.fill(sx - 1, sy, sx + 2, sy + 1, 0x80DCE6F2);
        graphics.fill(px - 3, py - 3, px + 4, py + 4, 0xFF1B2430);
        graphics.fill(px - 2, py - 2, px + 3, py + 3, 0xFFFFF4C8);
        graphics.fill(px - 1, py + 3, px + 2, py + 6, 0xFFFF9A3C);
        // L'étiquette de la fusée, à sa droite ; près du bord droit, à sa gauche.
        Component rocketName = Component.translatable("atlas.orbitalis.flight.rocket", String.format(java.util.Locale.ROOT, "%.0f", site.altitude()),
                String.format(java.util.Locale.ROOT, "%.0f", site.downrange()));
        int labelWidth = font.width(rocketName) + 6;
        int labelX = px + 7 + labelWidth > width - 8 - 160 && px - 7 - labelWidth > 8 ? px - 7 - labelWidth : px + 7;
        label(graphics, labelX, Math.max(44, py - 15), rocketName, Ui.OK);
        hits.add(new Hit(site, null, px, py));
        // Au bout de la courbe : la destination, là où la fusée quitte la carte pour l'espace.
        float[] end = points[samples];
        if (site.reach() >= 0 && site.reach() < RocketStats.Reach.values().length && end[0] > 0 && end[1] > 0 && end[0] < width && end[1] < height) {
            Component toward = Component.translatable("atlas.orbitalis.flight.toward", Component.translatable(RocketStats.Reach.values()[site.reach()].nameKey()));
            int towardWidth = font.width(toward) + 6;
            int towardX = Math.round(end[0]) + 6 + towardWidth > width - 8 ? Math.round(end[0]) - 6 - towardWidth : Math.round(end[0]) + 6;
            label(graphics, towardX, Math.round(end[1]) + 4, toward, Ui.alpha(0xE9F2FF, 0.8F));
        }
    }

    /**
     * Un point de la courbe d'un vol, à cet avancement (de 0 à 1), sur l'écran. Au sol, la fusée a parcouru
     * {@code ground × t²} ; en hauteur, elle est à {@code top × t × (2 - t)} : elle part à la verticale et finit à
     * l'horizontale, comme un vrai lanceur qui bascule. La hauteur est à l'échelle des distances au sol.
     */
    private float[] arcPoint(double padX, double padZ, double[] head, double ground, double top, float t, float rise) {
        double along = ground * t * t;
        double up = top * t * (2.0 - t);
        double x = padX + head[0] * along;
        double z = padZ + head[1] * along;
        return new float[] {screenX(x, z), screenY(x, z) - rise - (float) (up * 2.0 * zoom)};
    }

    /** Un trait au sol, d'un point du monde à l'autre : plein, ou en tirets de {@code dash} pixels. Rien hors de l'écran. */
    private void dashes(GuiGraphicsExtractor graphics, double fromX, double fromZ, double toX, double toZ, float rise, int color, float dash) {
        float x0 = screenX(fromX, fromZ);
        float y0 = screenY(fromX, fromZ) - rise;
        float x1 = screenX(toX, toZ);
        float y1 = screenY(toX, toZ) - rise;
        float length = Mth.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0));
        int steps = (int) Math.min(4000.0F, Math.ceil(length));
        // Le liseré sombre d'abord, tout du long, puis le trait : il se lit sur la mer comme sur la neige.
        for (int pass = 0; pass < 2; pass++) {
            for (int k = 0; k <= steps; k++) {
                float t = steps == 0 ? 0.0F : k / (float) steps;
                if (dash > 0.0F && (t * length) % (2.0F * dash) >= dash) {
                    continue;
                }
                int px = Math.round(Mth.lerp(t, x0, x1));
                int py = Math.round(Mth.lerp(t, y0, y1));
                if (px >= 0 && py >= 0 && px < width && py < height) {
                    if (pass == 0) {
                        graphics.fill(px - 1, py - 1, px + 3, py + 3, 0x60142030);
                    } else {
                        graphics.fill(px, py, px + 2, py + 2, color);
                    }
                }
            }
        }
    }

    /** Rayon de la zone d'exclusion d'une fusée, en blocs : l'onde de choc et la retombée des débris. */
    private static double exclusion(AtlasSitesPayload.Site site) {
        return Math.max(20.0, site.blast() * 2.5);
    }

    /**
     * La zone de danger d'un pas de tir. Une fusée posée a sa zone d'exclusion, un disque autour du pas de tir : si
     * elle explosait, l'onde de choc et les débris n'iraient pas plus loin. En vol, c'est le couloir de retombée : une
     * bande sous sa route, jusqu'où on la suit dans le monde, où tomberaient ses débris si elle se perdait.
     */
    private void extractZone(GuiGraphicsExtractor graphics, int lod, AtlasSitesPayload.Site site) {
        if (site.kind() != AtlasSitesPayload.PAD || !site.rocket() || site.phase() == AtlasSitesPayload.UNKNOWN) {
            return;
        }
        BlockPos pad = site.pos();
        double cx = pad.getX() + 0.5;
        double cz = pad.getZ() + 0.5;
        double radius = exclusion(site);
        float rise = lift(lod, pad.getY());
        List<float[]> outline = new ArrayList<>();
        if (site.phase() == AtlasSitesPayload.FLIGHT) {
            double[] head = heading(site);
            double length = RocketMotion.LIMIT;
            double sideX = -head[1] * radius;
            double sideZ = head[0] * radius;
            // Le couloir : un bout arrondi autour du pas de tir, puis une bande droite dans le cap.
            for (int k = 0; k <= 12; k++) {
                double angle = Math.PI / 2 + Math.PI * k / 12.0;
                double ax = Math.cos(angle) * head[0] - Math.sin(angle) * head[1];
                double az = Math.cos(angle) * head[1] + Math.sin(angle) * head[0];
                outline.add(new float[] {screenX(cx + ax * radius, cz + az * radius), screenY(cx + ax * radius, cz + az * radius) - rise});
            }
            double fx = cx + head[0] * length;
            double fz = cz + head[1] * length;
            outline.add(new float[] {screenX(fx - sideX, fz - sideZ), screenY(fx - sideX, fz - sideZ) - rise});
            outline.add(new float[] {screenX(fx + sideX, fz + sideZ), screenY(fx + sideX, fz + sideZ) - rise});
        } else {
            for (int k = 0; k < 48; k++) {
                double angle = Math.PI * 2.0 * k / 48.0;
                double x = cx + Math.cos(angle) * radius;
                double z = cz + Math.sin(angle) * radius;
                outline.add(new float[] {screenX(x, z), screenY(x, z) - rise});
            }
        }
        fillConvex(graphics, outline, 0x30FF5A52);
        // Le pourtour, en pointillés rouges.
        for (int k = 0; k < outline.size(); k++) {
            float[] a = outline.get(k);
            float[] b = outline.get((k + 1) % outline.size());
            float length = Mth.sqrt((b[0] - a[0]) * (b[0] - a[0]) + (b[1] - a[1]) * (b[1] - a[1]));
            int steps = (int) Math.min(2000.0F, Math.ceil(length));
            for (int s = 0; s < steps; s += 3) {
                float t = s / (float) Math.max(1, steps);
                int px = Math.round(Mth.lerp(t, a[0], b[0]));
                int py = Math.round(Mth.lerp(t, a[1], b[1]));
                if (px >= 0 && py >= 0 && px < width && py < height) {
                    graphics.fill(px, py, px + 2, py + 1, 0xC0FF5A52);
                }
            }
        }
    }

    /** Remplit un polygone convexe, ligne par ligne. */
    private void fillConvex(GuiGraphicsExtractor graphics, List<float[]> points, int color) {
        float top = Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        for (float[] point : points) {
            top = Math.min(top, point[1]);
            bottom = Math.max(bottom, point[1]);
        }
        int from = Math.max(0, (int) Math.ceil(top));
        int to = Math.min(height - 1, (int) Math.floor(bottom));
        for (int y = from; y <= to; y++) {
            float left = Float.MAX_VALUE;
            float right = -Float.MAX_VALUE;
            float row = y + 0.5F;
            for (int k = 0; k < points.size(); k++) {
                float[] a = points.get(k);
                float[] b = points.get((k + 1) % points.size());
                if ((a[1] <= row) != (b[1] <= row)) {
                    float x = a[0] + (row - a[1]) / (b[1] - a[1]) * (b[0] - a[0]);
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                }
            }
            if (right > left) {
                graphics.fill(Math.max(0, Math.round(left)), y, Math.min(width, Math.round(right)), y + 1, color);
            }
        }
    }

    // ------------------------------------------------------------------
    // Le panneau des calques, et la fiche de ce qu'on a cliqué
    // ------------------------------------------------------------------

    private static final String[] LAYERS = {"sites", "flights", "zones", "weather", "satellites", "pins"};
    private static final int LAYER_ROW = 14;

    private boolean layer(int index) {
        return switch (index) {
            case 0 -> showSites;
            case 1 -> showFlights;
            case 2 -> showZones;
            case 3 -> showWeather;
            case 4 -> showSatellites;
            default -> showPins;
        };
    }

    private void toggleLayer(int index) {
        switch (index) {
            case 0 -> showSites = !showSites;
            case 1 -> showFlights = !showFlights;
            case 2 -> showZones = !showZones;
            case 3 -> showWeather = !showWeather;
            case 4 -> showSatellites = !showSatellites;
            default -> showPins = !showPins;
        }
        com.orbitalis.client.ViewPrefs.set("atlas.layer." + LAYERS[index], layer(index));
    }

    /** Vrai si ce calque a de quoi montrer : la météo demande un satellite météo, les satellites au moins un satellite. */
    private boolean available(int index) {
        return index == 3 ? hasWeather() : index != 4 || sky.satellites().length > 0;
    }

    /** Le nom d'un calque, avec ce qu'il lui manque s'il n'a rien à montrer. */
    private Component layerName(int index) {
        Component name = Component.translatable("atlas.orbitalis.layer." + LAYERS[index]);
        return available(index) ? name : Component.translatable("atlas.orbitalis.layer.missing", name,
                Component.translatable(index == 3 ? "atlas.orbitalis.layer.needs_weather" : "atlas.orbitalis.layer.needs_satellite"));
    }

    /** Le panneau des calques, au-dessus de son bouton : x, y, largeur, hauteur. */
    private int[] layersPanel() {
        int w = 0;
        for (int k = 0; k < LAYERS.length; k++) {
            w = Math.max(w, font.width(layerName(k)) + 28);
        }
        w = Math.max(w, font.width(Component.translatable("atlas.orbitalis.pin.hint")) + 14);
        int h = LAYERS.length * LAYER_ROW + 8 + 13;
        Button anchor = null;
        for (Button button : buttons()) {
            if (button.layers()) {
                anchor = button;
            }
        }
        int x = anchor == null ? 14 : anchor.x();
        return new int[] {Math.min(x, width - 8 - w), height - 8 - BAR - 4 - h, w, h};
    }

    private void extractLayers(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!layersOpen) {
            return;
        }
        int[] panel = layersPanel();
        Ui.card(graphics, panel[0], panel[1], panel[2], panel[3], true);
        for (int k = 0; k < LAYERS.length; k++) {
            int y = panel[1] + 4 + k * LAYER_ROW;
            boolean hovered = Ui.inside(mouseX, mouseY, panel[0], y, panel[2], LAYER_ROW);
            if (hovered) {
                graphics.fill(panel[0] + 2, y, panel[0] + panel[2] - 2, y + LAYER_ROW, Ui.alpha(Ui.ACCENT, 0.12F));
            }
            // Une case : pleine quand le calque est montré.
            Ui.frame(graphics, panel[0] + 7, y + 3, 8, 8, Ui.ACCENT);
            if (layer(k)) {
                graphics.fill(panel[0] + 9, y + 5, panel[0] + 13, y + 9, Ui.ACCENT);
            }
            graphics.text(font, layerName(k), panel[0] + 21, y + 3, !available(k) ? Ui.DIM : layer(k) ? Ui.TEXT : Ui.MUTED, false);
        }
        // Comment poser un repère.
        int hintY = panel[1] + 4 + LAYERS.length * LAYER_ROW + 3;
        graphics.fill(panel[0] + 6, hintY - 2, panel[0] + panel[2] - 6, hintY - 1, Ui.LINE);
        graphics.text(font, Component.translatable("atlas.orbitalis.pin.hint"), panel[0] + 7, hintY + 2, Ui.DIM, false);
    }

    /** Ce que l'on a cliqué : une installation, ou un point du terrain. */
    private record Selection(AtlasSitesPayload.Site site, AtlasSitesPayload.MarkerInfo marker, double x, double z) {
    }

    /** Cherche ce qui est sous le curseur : le repère le plus proche, sinon le terrain. */
    private void query(double mouseX, double mouseY) {
        Hit best = null;
        double bestDistance = 12.0 * 12.0;
        for (Hit hit : hits) {
            double distance = (hit.x() - mouseX) * (hit.x() - mouseX) + (hit.y() - mouseY) * (hit.y() - mouseY);
            if ((hit.site() != null || hit.marker() != null) && distance < bestDistance) {
                best = hit;
                bestDistance = distance;
            }
        }
        stopRenaming();
        if (best != null && best.marker() != null) {
            selected = new Selection(null, best.marker(), best.marker().x() + 0.5, best.marker().z() + 0.5);
        } else if (best != null) {
            selected = new Selection(best.site(), null, best.site().pos().getX() + 0.5, best.site().pos().getZ() + 0.5);
        } else {
            double[] at = worldAt(mouseX, mouseY);
            selected = new Selection(null, null, at[0], at[1]);
        }
        Ui.click();
    }

    /**
     * La fiche : en haut à gauche, sous le titre, là où elle cache le moins (les routes de vol partent le plus souvent
     * vers l'est, à droite) ; ses lignes, et sa croix pour la fermer.
     */
    private int[] cardBox() {
        return new int[] {8, 32, CARD_WIDTH};
    }

    private static final int CARD_WIDTH = 196;

    /** La fiche de ce que l'on a cliqué. */
    private void extractCard(GuiGraphicsExtractor graphics, int lod, int mouseX, int mouseY) {
        renameButton = null;
        deleteButton = null;
        if (selected == null) {
            return;
        }
        if (selected.marker() != null) {
            extractPinCard(graphics, mouseX, mouseY);
            return;
        }
        // L'installation choisie, à jour : le serveur renvoie son état toutes les deux secondes.
        AtlasSitesPayload.Site site = selected.site();
        if (site != null) {
            for (AtlasSitesPayload.Site fresh : shownSites()) {
                if (fresh.pos().equals(site.pos()) && fresh.kind() == site.kind()) {
                    site = fresh;
                }
            }
        }
        List<String[]> rows = new ArrayList<>();
        Component title;
        int color;
        int x = Mth.floor(selected.x());
        int z = Mth.floor(selected.z());
        String where = Component.translatable("atlas.orbitalis.cursor", x, z).getString();
        if (site == null) {
            title = Component.translatable("atlas.orbitalis.card.terrain");
            color = Ui.TEXT;
            int packed = cellAt(lod, x, z);
            rows.add(new String[] {text("atlas.orbitalis.card.position"), where});
            if (packed != 0) {
                rows.add(new String[] {text("atlas.orbitalis.card.altitude"),
                        String.valueOf((packed >>> 24) - LaunchTableBlockEntity.MAP_HEIGHT_BIAS + minecraft.level.getSeaLevel())});
                rows.add(new String[] {text("atlas.orbitalis.card.ground"), text("atlas.orbitalis.terrain." + terrain(packed & 0xFFFFFF))});
            } else {
                rows.add(new String[] {text("atlas.orbitalis.card.ground"), text("atlas.orbitalis.unknown")});
            }
            rows.add(new String[] {text("atlas.orbitalis.card.distance"),
                    Component.translatable("atlas.orbitalis.scale", Math.round(Math.sqrt(Math.pow(x - source.getX(), 2) + Math.pow(z - source.getZ(), 2)))).getString()});
        } else if (site.kind() == AtlasSitesPayload.PAD) {
            title = Component.translatable("atlas.orbitalis.marker.pad");
            color = padColor(site);
            rows.add(new String[] {text("atlas.orbitalis.card.position"), where});
            if (site.phase() == AtlasSitesPayload.UNKNOWN) {
                rows.add(new String[] {text("atlas.orbitalis.card.state"), text("atlas.orbitalis.pad.unknown")});
            } else {
                rows.add(new String[] {text("atlas.orbitalis.card.state"), padState(site).getString()});
                if (site.size() > 0) {
                    rows.add(new String[] {text("atlas.orbitalis.card.deck"),
                            Component.translatable("atlas.orbitalis.card.deck_value", site.size(), site.size(), site.tower()).getString()});
                }
                if (site.rocket() && site.reach() >= 0 && site.reach() < RocketStats.Reach.values().length) {
                    rows.add(new String[] {text("atlas.orbitalis.card.mission"), text(RocketStats.Reach.values()[site.reach()].nameKey())});
                    rows.add(new String[] {text("atlas.orbitalis.card.fuel"), site.fuel() + " %"});
                }
                if (site.phase() == AtlasSitesPayload.FLIGHT) {
                    rows.add(new String[] {text("atlas.orbitalis.card.altitude"), String.format(java.util.Locale.ROOT, "%.1f km", site.altitude())});
                    rows.add(new String[] {text("atlas.orbitalis.card.downrange"), String.format(java.util.Locale.ROOT, "%.0f km", site.downrange())});
                }
                if (site.rocket()) {
                    rows.add(new String[] {text(site.phase() == AtlasSitesPayload.FLIGHT ? "atlas.orbitalis.card.corridor" : "atlas.orbitalis.card.exclusion"),
                            Component.translatable("atlas.orbitalis.scale", Math.round(exclusion(site))).getString()});
                }
            }
        } else if (site.kind() == AtlasSitesPayload.STATION) {
            title = Component.translatable("atlas.orbitalis.marker.station");
            color = Ui.OK;
            rows.add(new String[] {text("atlas.orbitalis.card.position"), where});
            rows.add(new String[] {text("atlas.orbitalis.card.satellites"), site.live() ? String.valueOf(site.extra()) : "—"});
        } else {
            title = Component.translatable(site.pos().equals(source) ? "atlas.orbitalis.marker.centre" : "atlas.orbitalis.marker.console");
            color = Ui.ACCENT;
            rows.add(new String[] {text("atlas.orbitalis.card.position"), where});
            rows.add(new String[] {text("atlas.orbitalis.card.link"), text(!site.live() ? "atlas.orbitalis.pad.unknown"
                    : site.extra() > 0 ? "atlas.orbitalis.card.linked" : "atlas.orbitalis.card.unlinked")});
        }
        boolean remembered = site != null && site.kind() == AtlasSitesPayload.PAD && !site.live() && site.phase() != AtlasSitesPayload.UNKNOWN;
        int[] box = cardBox();
        int h = 22 + rows.size() * 11 + (remembered ? 12 : 0) + 4;
        Ui.card(graphics, box[0], box[1], box[2], h, true);
        graphics.fill(box[0], box[1] + 3, box[0] + 2, box[1] + h - 3, color);
        Ui.heading(graphics, font, title, box[0] + 8, box[1] + 7);
        boolean closeHovered = Ui.inside(mouseX, mouseY, box[0] + box[2] - 16, box[1] + 4, 12, 12);
        graphics.text(font, "×", box[0] + box[2] - 13, box[1] + 6, closeHovered ? Ui.TEXT : Ui.MUTED, false);
        int y = box[1] + 22;
        for (String[] row : rows) {
            Ui.row(graphics, font, Component.literal(row[0]), Ui.fit(font, row[1], box[2] - 22 - Math.min(font.width(row[0]), 64)), box[0] + 8, y, box[2] - 16, Ui.TEXT);
            y += 11;
        }
        if (remembered) {
            graphics.text(font, Ui.fit(font, text("atlas.orbitalis.card.remembered"), box[2] - 16), box[0] + 8, y + 1, Ui.DIM, false);
        }
    }

    private static String text(String key) {
        return Component.translatable(key).getString();
    }

    /** La fiche d'un repère : sa place, qui l'a posé ; de quoi le renommer et le retirer. */
    private void extractPinCard(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        AtlasSitesPayload.MarkerInfo marker = null;
        for (AtlasSitesPayload.MarkerInfo fresh : markers) {
            if (fresh.id() == selected.marker().id()) {
                marker = fresh;
            }
        }
        if (marker == null) {
            // Retiré entre-temps, par soi ou par un autre membre de l'agence.
            selected = null;
            stopRenaming();
            return;
        }
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {text("atlas.orbitalis.card.position"), Component.translatable("atlas.orbitalis.cursor", marker.x(), marker.z()).getString()});
        rows.add(new String[] {text("atlas.orbitalis.card.altitude"), String.valueOf(marker.y())});
        rows.add(new String[] {text("atlas.orbitalis.pin.author"), marker.author()});
        int[] box = cardBox();
        int h = 22 + rows.size() * 11 + 4 + BUTTON_HEIGHT + 6;
        int color = markerColor(marker);
        Ui.card(graphics, box[0], box[1], box[2], h, true);
        graphics.fill(box[0], box[1] + 3, box[0] + 2, box[1] + h - 3, color);
        Ui.heading(graphics, font, Component.literal(Ui.fit(font, markerName(marker).getString(), box[2] - 30)), box[0] + 8, box[1] + 7);
        boolean closeHovered = Ui.inside(mouseX, mouseY, box[0] + box[2] - 16, box[1] + 4, 12, 12);
        graphics.text(font, "\u00D7", box[0] + box[2] - 13, box[1] + 6, closeHovered ? Ui.TEXT : Ui.MUTED, false);
        int y = box[1] + 22;
        for (String[] row : rows) {
            Ui.row(graphics, font, Component.literal(row[0]), Ui.fit(font, row[1], box[2] - 22 - Math.min(font.width(row[0]), 64)), box[0] + 8, y, box[2] - 16, Ui.TEXT);
            y += 11;
        }
        int half = (box[2] - 20) / 2;
        renameButton = new int[] {box[0] + 8, y + 3, half};
        deleteButton = new int[] {box[0] + 12 + half, y + 3, half};
        if (renaming == null) {
            Ui.button(graphics, font, Component.translatable("atlas.orbitalis.pin.rename"), renameButton[0], renameButton[1], half, BUTTON_HEIGHT,
                    Ui.inside(mouseX, mouseY, renameButton[0], renameButton[1], half, BUTTON_HEIGHT), true, false, Ui.ACCENT);
            Ui.button(graphics, font, Component.translatable("atlas.orbitalis.pin.remove"), deleteButton[0], deleteButton[1], half, BUTTON_HEIGHT,
                    Ui.inside(mouseX, mouseY, deleteButton[0], deleteButton[1], half, BUTTON_HEIGHT), true, false, Ui.BAD);
        } else {
            renaming.extractRenderState(graphics, mouseX, mouseY, framePartial);
        }
    }

    // ------------------------------------------------------------------
    // Le ciel : météo et satellites
    // ------------------------------------------------------------------

    /** Les nuages du monde : la même image que celle du jeu, une case pour douze blocs, à la même hauteur. */
    private static final Identifier CLOUDS = Identifier.withDefaultNamespace("textures/environment/clouds.png");
    private static final int CLOUD_TEXELS = 256;
    private static final double CLOUD_CELL = 12.0;
    private static final double CLOUD_HEIGHT = 192.33;

    /** Vrai si l'agence a un satellite météo en service : sans lui, pas de calque météo. */
    private boolean hasWeather() {
        return sky.forecast() != SatelliteStatusPayload.FORECAST_NONE;
    }

    /** Vrai s'il pleut, selon le satellite météo. */
    private boolean raining() {
        return sky.forecast() == SatelliteStatusPayload.FORECAST_RAIN || sky.forecast() == SatelliteStatusPayload.FORECAST_THUNDER;
    }

    /**
     * Les nuages, tels qu'ils sont dans le monde à cet instant, vus d'en haut : l'image des nuages du jeu, posée à leur
     * hauteur et qui dérive avec eux. Ou leur ombre, posée sur le terrain. Gris quand il pleut.
     */
    private void extractClouds(GuiGraphicsExtractor graphics, int lod, boolean shadow) {
        float time = (float) (minecraft.level.getGameTime() % (CLOUD_TEXELS * 400L)) + framePartial;
        // Comme dans le jeu : la case de l'image sous le point (x, z) est celle de (x + 0,03 × t, z + 3,96), à douze blocs la case.
        double offsetX = time * 0.030000001;
        double offsetZ = 3.96;
        double span = CLOUD_TEXELS * CLOUD_CELL;
        boolean rain = raining();
        // De loin, les nuages disent le temps qu'il fait sur la région ; de près, ils s'effacent pour laisser lire le terrain.
        float fade = Mth.clamp((0.5F - zoom) / 0.4F, 0.0F, 1.0F);
        if (fade <= 0.0F) {
            return;
        }
        // Les nuages adoucis (voir SoftClouds) portent eux-mêmes leur densité : franges légères, cœur plus dense.
        Identifier soft = com.orbitalis.client.render.SoftClouds.get();
        Identifier image = soft != null ? soft : CLOUDS;
        int texels = soft != null ? com.orbitalis.client.render.SoftClouds.SIZE : CLOUD_TEXELS;
        double cell = CLOUD_TEXELS * CLOUD_CELL / texels;
        int alpha = Math.round((soft != null ? (shadow ? (rain ? 0x60 : 0x44) : rain ? 0xE0 : 0xB4)
                : (shadow ? (rain ? 0x34 : 0x22) : rain ? 0x9C : 0x70)) * fade);
        int color = alpha << 24 | (shadow ? 0x000000 : rain ? 0x8E97A3 : 0xFFFFFF);
        int sea = minecraft.level.getSeaLevel();
        float rise = shadow ? lift(lod, sea + centreHeight) : lift(lod, CLOUD_HEIGHT);
        // Le rectangle du monde que l'on voit, élargi de la hauteur des nuages.
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (int k = 0; k < 4; k++) {
            double[] at = worldAt((k & 1) == 0 ? 0 : width, (k >> 1) == 0 ? -rise : height + rise);
            minX = Math.min(minX, at[0]);
            maxX = Math.max(maxX, at[0]);
            minZ = Math.min(minZ, at[1]);
            maxZ = Math.max(maxZ, at[1]);
        }
        int copies = 0;
        var pose = graphics.pose();
        for (long kx = Mth.floor((minX + offsetX) / span); kx <= Mth.floor((maxX + offsetX) / span) && copies < 36; kx++) {
            for (long kz = Mth.floor((minZ + offsetZ) / span); kz <= Mth.floor((maxZ + offsetZ) / span) && copies < 36; kz++, copies++) {
                double x0 = kx * span - offsetX;
                double z0 = kz * span - offsetZ;
                float ox = screenX(x0, z0);
                float oy = screenY(x0, z0) - rise;
                // Une case de l'image vers l'est, puis vers le sud : la vue de trois quarts est une simple transformation.
                float ux = (screenX(x0 + cell, z0) - ox);
                float uy = (screenY(x0 + cell, z0) - rise - oy);
                float vx = (screenX(x0, z0 + cell) - ox);
                float vy = (screenY(x0, z0 + cell) - rise - oy);
                pose.pushMatrix();
                pose.mul(new Matrix3x2f(ux, uy, vx, vy, ox, oy));
                graphics.blit(RenderPipelines.GUI_TEXTURED, image, 0, 0, 0.0F, 0.0F, texels, texels, texels, texels, texels, texels, color);
                pose.popMatrix();
            }
        }
    }

    /** La pluie, par-dessus la carte : de fins traits qui tombent ; et, par orage, un éclair de loin en loin. */
    private void extractRain(GuiGraphicsExtractor graphics) {
        float time = minecraft.level.getGameTime() + framePartial;
        int bottom = height - 8 - BAR;
        for (int i = 0; i < 160; i++) {
            int seed = i * 0x9E3779B1;
            float fall = (time * (6.0F + (seed >>> 28)) + (seed >>> 8 & 0xFFF)) % (bottom + 20);
            int x = Math.floorMod((seed >>> 4) + (int) (fall * 0.35F), width);
            int y = (int) fall - 10;
            graphics.fill(x, y, x + 1, y + 5, 0x60B9D4EE);
        }
        if (sky.forecast() == SatelliteStatusPayload.FORECAST_THUNDER && Math.floorMod((long) time, 240L) < 3) {
            graphics.fill(0, 0, width, bottom, 0x30FFFFFF);
        }
    }

    /**
     * Ce que les satellites de l'agence apportent à la carte : la couverture d'observation autour des stations de
     * poursuite, et la précision de la position du joueur, que donne la constellation de navigation.
     */
    private void extractSatellites(GuiGraphicsExtractor graphics, int lod) {
        // La couverture d'observation : le carré de sous-sol que les satellites d'observation relèvent autour de chaque station.
        if (sky.observers() > 0) {
            int radius = Math.min(4, 1 + sky.observers());
            for (AtlasSitesPayload.Site site : shownSites()) {
                if (site.kind() != AtlasSitesPayload.STATION) {
                    continue;
                }
                double x0 = ((site.pos().getX() >> 4) - radius) * 16.0;
                double z0 = ((site.pos().getZ() >> 4) - radius) * 16.0;
                double x1 = ((site.pos().getX() >> 4) + radius + 1) * 16.0;
                double z1 = ((site.pos().getZ() >> 4) + radius + 1) * 16.0;
                float ground = lift(lod, site.pos().getY());
                List<float[]> square = List.of(new float[] {screenX(x0, z0), screenY(x0, z0) - ground}, new float[] {screenX(x1, z0), screenY(x1, z0) - ground},
                        new float[] {screenX(x1, z1), screenY(x1, z1) - ground}, new float[] {screenX(x0, z1), screenY(x0, z1) - ground});
                fillConvex(graphics, square, 0x205BC27A);
                dashes(graphics, x0, z0, x1, z0, ground, 0xFF5BC27A, 3.0F);
                dashes(graphics, x1, z0, x1, z1, ground, 0xFF5BC27A, 3.0F);
                dashes(graphics, x1, z1, x0, z1, ground, 0xFF5BC27A, 3.0F);
                dashes(graphics, x0, z1, x0, z0, ground, 0xFF5BC27A, 3.0F);
            }
        }
        // La position du joueur : à quelques blocs près tant que la constellation de navigation est incomplète.
        if (minecraft.player != null && sky.navigation() < SatelliteType.NAVIGATION_CONSTELLATION) {
            double radius = navigationError();
            float ground = lift(lod, minecraft.player.getY());
            for (int k = 0; k < 64; k += 2) {
                double angle = Math.PI * 2.0 * k / 64.0;
                double x = minecraft.player.getX() + Math.cos(angle) * radius;
                double z = minecraft.player.getZ() + Math.sin(angle) * radius;
                int px = Math.round(screenX(x, z));
                int py = Math.round(screenY(x, z) - ground);
                if (px >= 0 && py >= 0 && px < width && py < height) {
                    graphics.fill(px, py, px + 2, py + 1, 0xC0E8872A);
                }
            }
        }
    }

    /** Incertitude sur la position du joueur, en blocs : plus il y a de satellites de navigation, plus elle est fine. */
    private double navigationError() {
        return 160.0 / (1 + sky.navigation());
    }

    /** Durée lisible : en secondes sous la minute, en minutes au-delà. */
    private static String duration(float ticks) {
        int seconds = Math.max(0, Math.round(ticks / 20.0F));
        return seconds < 60 ? Component.translatable("atlas.orbitalis.seconds", seconds).getString()
                : Component.translatable("atlas.orbitalis.minutes", (seconds + 59) / 60).getString();
    }

    /** La carte du ciel, en haut à droite sous l'échelle : ce que les satellites de l'agence lui donnent. */
    private void extractSky(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        skyBox = null;
        if (!sitesReceived || !showWeather && !showSatellites) {
            return;
        }
        List<String[]> rows = new ArrayList<>();
        String weather = switch (sky.forecast()) {
            case SatelliteStatusPayload.FORECAST_CLEAR -> Component.translatable("atlas.orbitalis.sky.clear", duration(sky.forecastTicks())).getString();
            case SatelliteStatusPayload.FORECAST_RAIN -> Component.translatable("atlas.orbitalis.sky.rain", duration(sky.forecastTicks())).getString();
            case SatelliteStatusPayload.FORECAST_THUNDER -> Component.translatable("atlas.orbitalis.sky.thunder", duration(sky.forecastTicks())).getString();
            default -> text("atlas.orbitalis.sky.no_weather");
        };
        rows.add(new String[] {text("atlas.orbitalis.sky.weather"), weather});
        rows.add(new String[] {text("atlas.orbitalis.sky.relay"), text(sky.relay() ? "atlas.orbitalis.sky.relay_on" : "atlas.orbitalis.sky.relay_off")});
        rows.add(new String[] {text("atlas.orbitalis.sky.navigation"), sky.navigation() >= SatelliteType.NAVIGATION_CONSTELLATION
                ? Component.translatable("atlas.orbitalis.sky.navigation_full", sky.navigation()).getString()
                : Component.translatable("atlas.orbitalis.sky.navigation_partial", sky.navigation(), Math.round(navigationError())).getString()});
        rows.add(new String[] {text("atlas.orbitalis.sky.observation"), String.valueOf(sky.observers())});
        // En bas à droite, au-dessus de la barre : le centre de la carte reste libre. Le panneau des calques, s'il est
        // ouvert et qu'il tomberait dessous, passe devant : la carte du ciel s'efface le temps de choisir.
        int w = SKY_WIDTH;
        int h = skyOpen ? 22 + rows.size() * 11 + 4 : 20;
        int x = width - 8 - w;
        int y = height - 8 - BAR - 4 - h;
        if (layersOpen) {
            int[] panel = layersPanel();
            if (panel[0] + panel[2] > x && panel[1] + panel[3] > y) {
                return;
            }
        }
        skyBox = new int[] {x, y, w, h};
        boolean hovered = Ui.inside(mouseX, mouseY, x, y, w, h);
        Ui.card(graphics, x, y, w, h, true);
        if (hovered) {
            Ui.frame(graphics, x, y, w, h, Ui.alpha(Ui.ACCENT, 0.6F));
        }
        // Une flèche dit qu'on la déplie ou la replie d'un clic.
        graphics.text(font, skyOpen ? "\u25BE" : "\u25B4", x + w - 13, y + 6, hovered ? Ui.TEXT : Ui.MUTED, false);
        if (!skyOpen) {
            // Repliée : le temps qu'il fait, et le relais.
            String line = rows.get(0)[1] + "  ·  " + text("atlas.orbitalis.sky.relay") + " " + rows.get(1)[1];
            graphics.text(font, Ui.fit(font, line, w - 26), x + 8, y + 6, Ui.TEXT, false);
            return;
        }
        Ui.heading(graphics, font, Component.translatable("atlas.orbitalis.sky.title"), x + 8, y + 7);
        int rowY = y + 22;
        for (String[] row : rows) {
            Ui.row(graphics, font, Component.literal(row[0]), Ui.fit(font, row[1], w - 22 - Math.min(font.width(row[0]), 64)), x + 8, rowY, w - 16, Ui.TEXT);
            rowY += 11;
        }
    }

    private static final int SKY_WIDTH = 226;

    // ------------------------------------------------------------------
    // Les repères de l'agence
    // ------------------------------------------------------------------

    /** Couleurs des repères, dans l'ordre de leurs numéros. */
    private static final int[] MARKER_COLORS = {0xFFFFC857, 0xFF7CC4FF, 0xFF4ADE80, 0xFFC79BF2, 0xFFFF8A5B, 0xFFF2F4F7};

    private static int markerColor(AtlasSitesPayload.MarkerInfo marker) {
        return MARKER_COLORS[Math.floorMod(marker.id() - 1, MARKER_COLORS.length)];
    }

    /** Le nom d'un repère : celui qu'on lui a donné, sinon « Repère n ». */
    private static Component markerName(AtlasSitesPayload.MarkerInfo marker) {
        return marker.name().isEmpty() ? Component.translatable("atlas.orbitalis.pin.unnamed", marker.id()) : Component.literal(marker.name());
    }

    /** Les repères : un fanion de leur couleur, planté sur le terrain, et leur nom. */
    private void extractPins(GuiGraphicsExtractor graphics, int lod) {
        for (AtlasSitesPayload.MarkerInfo marker : markers) {
            int px = Math.round(screenX(marker.x() + 0.5, marker.z() + 0.5));
            int ground = Math.round(screenY(marker.x() + 0.5, marker.z() + 0.5) - lift(lod, marker.y()));
            if (px < -40 || px > width + 40 || ground < -30 || ground > height + 40) {
                continue;
            }
            int color = markerColor(marker);
            // Le pied, la hampe, le fanion.
            graphics.fill(px - 2, ground, px + 3, ground + 1, 0x80000000);
            graphics.fill(px, ground - 14, px + 1, ground, 0xFFE4E8EE);
            graphics.fill(px + 1, ground - 14, px + 8, ground - 9, color);
            graphics.fill(px + 1, ground - 9, px + 5, ground - 8, color);
            label(graphics, px + 10, ground - 19, markerName(marker), color);
            hits.add(new Hit(null, marker, px + 3, ground - 10));
        }
    }

    /** Pose un repère là où l'on a cliqué : sa fiche s'ouvre dès que le serveur l'a enregistré. */
    private void placePin(double mouseX, double mouseY) {
        double[] at = worldAt(mouseX, mouseY);
        int x = Mth.floor(at[0]);
        int z = Mth.floor(at[1]);
        double dx = x - source.getX();
        double dz = z - source.getZ();
        if (dx * dx + dz * dz > (double) known() * known()) {
            return;
        }
        int packed = cellAt(lod(), x, z);
        int y = packed == 0 ? minecraft.level.getSeaLevel() : (packed >>> 24) - LaunchTableBlockEntity.MAP_HEIGHT_BIAS + minecraft.level.getSeaLevel();
        showPins = true;
        pendingPin = new int[] {x, z};
        ClientPacketDistributor.sendToServer(new AtlasMarkerPayload(source, AtlasMarkerPayload.ADD, 0, x, y, z, ""));
        Ui.click();
    }

    /** Commence à renommer le repère de la fiche : un champ de texte prend la place de ses boutons. */
    private void startRenaming(AtlasSitesPayload.MarkerInfo marker, int x, int y, int w) {
        stopRenaming();
        renaming = new EditBox(font, x, y, w, 14, Component.translatable("atlas.orbitalis.pin.rename"));
        renaming.setMaxLength(32);
        renaming.setValue(marker.name());
        renamingId = marker.id();
        addWidget(renaming);
        setFocused(renaming);
        renaming.setFocused(true);
    }

    private void stopRenaming() {
        if (renaming != null) {
            removeWidget(renaming);
            renaming = null;
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (renaming != null) {
            if (event.key() == 257 || event.key() == 335) {
                ClientPacketDistributor.sendToServer(new AtlasMarkerPayload(source, AtlasMarkerPayload.RENAME, renamingId, 0, 0, 0, renaming.getValue()));
                stopRenaming();
                return true;
            }
            if (event.key() == 256) {
                stopRenaming();
                return true;
            }
            return renaming.keyPressed(event) || true;
        }
        return super.keyPressed(event);
    }

    /** En haut à gauche : le titre, et le relevé en cours s'il y en a un. */
    private void extractHeader(GuiGraphicsExtractor graphics) {
        int w = font.width(title) + 16;
        Ui.card(graphics, 8, 8, w, 18, false);
        Ui.heading(graphics, font, title, 16, 13);
        int pillX = 8 + w + 6;
        if (tablet) {
            // Ouvert depuis la tablette : les données arrivent par le satellite relais.
            pillX += Ui.pill(graphics, font, Component.translatable("atlas.orbitalis.tablet").getString(), pillX, 11, Ui.OK) + 4;
        }
        if (pending > 0) {
            Ui.pill(graphics, font, Component.translatable("atlas.orbitalis.loading").getString(), pillX, 11, Ui.WARN);
        }
    }

    /** Ce que le curseur survole, à gauche de la barre : ses coordonnées, puis l'altitude et la nature du terrain. */
    private void extractCursor(GuiGraphicsExtractor graphics, int lod, int mouseX, int mouseY, int barY, int room) {
        double[] at = worldAt(mouseX, mouseY);
        int x = Mth.floor(at[0]);
        int z = Mth.floor(at[1]);
        int cell = AtlasTiles.CELL[lod];
        AtlasMap.Tile tile = AtlasMap.peek(lod, Math.floorDiv(Math.floorDiv(x, cell), AtlasTiles.TILE), Math.floorDiv(Math.floorDiv(z, cell), AtlasTiles.TILE), turn);
        int packed = cellAt(lod, x, z);
        String where = Component.translatable("atlas.orbitalis.cursor", x, z).getString();
        String what;
        if (packed == 0) {
            what = Component.translatable(tile != null && tile.state == AtlasMap.State.WAITING ? "atlas.orbitalis.loading" : "atlas.orbitalis.unknown").getString();
        } else {
            int altitude = (packed >>> 24) - LaunchTableBlockEntity.MAP_HEIGHT_BIAS + minecraft.level.getSeaLevel();
            what = Component.translatable("atlas.orbitalis.altitude", altitude).getString() + "  ·  "
                    + Component.translatable("atlas.orbitalis.terrain." + terrain(packed & 0xFFFFFF)).getString();
        }
        if (room < 40) {
            return;
        }
        graphics.text(font, Ui.fit(font, where, room), 16, barY + 7, Ui.TEXT, false);
        graphics.text(font, Ui.fit(font, what, room), 16, barY + 19, packed == 0 ? Ui.MUTED : Ui.ACCENT, false);
    }

    /** Nature du terrain d'une case, d'après sa couleur. */
    private static String terrain(int rgb) {
        if (LaunchTableBlockEntity.isFarWater(rgb)) {
            return "water";
        }
        if (rgb == LaunchTableBlockEntity.FAR_GRASS) {
            return "grass";
        }
        if (rgb == LaunchTableBlockEntity.FAR_FOREST || rgb == AtlasTiles.LEAVES) {
            return "forest";
        }
        if (rgb == LaunchTableBlockEntity.FAR_SAVANNA) {
            return "savanna";
        }
        if (rgb == LaunchTableBlockEntity.FAR_SAND) {
            return "sand";
        }
        if (rgb == LaunchTableBlockEntity.FAR_STONE) {
            return "rock";
        }
        if (rgb == LaunchTableBlockEntity.FAR_SNOW) {
            return "snow";
        }
        return rgb == LaunchTableBlockEntity.FAR_BADLANDS ? "badlands" : "other";
    }

    /** En haut à droite : l'échelle (une barre, et le nombre de blocs qu'elle représente) et la boussole, dans le même cadre. */
    private void extractScale(GuiGraphicsExtractor graphics) {
        // Un bloc avance de deux pixels de côté et d'un pixel vers le bas, à l'échelle 1.
        double perBlock = zoom * Math.sqrt(5.0);
        int blocks = 4;
        while (blocks * perBlock < 44 && blocks < 8192) {
            blocks *= 2;
        }
        int length = (int) Math.round(blocks * perBlock);
        String label = Component.translatable("atlas.orbitalis.scale", blocks).getString();
        int compass = 26;
        int w = Math.max(length, font.width(label)) + 16 + compass + 6;
        int x = width - 8 - w;
        Ui.card(graphics, x, 8, w, 30, false);
        graphics.text(font, label, x + 8, 14, Ui.TEXT, false);
        graphics.fill(x + 8, 28, x + 8 + length, 29, Ui.ACCENT);
        graphics.fill(x + 8, 26, x + 9, 31, Ui.ACCENT);
        graphics.fill(x + 7 + length, 26, x + 8 + length, 31, Ui.ACCENT);
        extractCompass(graphics, x + w - compass - 2, 10, compass);
    }

    /** La boussole : une aiguille vers le nord, tel qu'il est à l'écran. */
    private void extractCompass(GuiGraphicsExtractor graphics, int x, int y, int size) {
        // Le nord du monde est vers les z décroissants.
        float dx = screenX(centreX, centreZ - 1.0) - screenX(centreX, centreZ);
        float dy = screenY(centreX, centreZ - 1.0) - screenY(centreX, centreZ);
        float length = Mth.sqrt(dx * dx + dy * dy);
        if (length < 1.0E-4F) {
            return;
        }
        float cx = x + size / 2.0F;
        float cy = y + size / 2.0F;
        for (int k = -8; k <= 8; k++) {
            int px = Math.round(cx + dx / length * k);
            int py = Math.round(cy + dy / length * k);
            graphics.fill(px, py, px + 1, py + 1, k > 0 ? 0xFFFF5A52 : Ui.MUTED);
        }
        String north = Component.translatable("atlas.orbitalis.north").getString();
        int nx = Math.round(cx + dx / length * 10.0F) - font.width(north) / 2;
        int ny = Math.round(cy + dy / length * 10.0F) - 4;
        graphics.text(font, north, Mth.clamp(nx, x + 2, x + size - 2 - font.width(north)), Mth.clamp(ny, y + 2, y + size - 10), 0xFFFF5A52, false);
    }
}
