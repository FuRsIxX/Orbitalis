package com.orbitalis.client.screen;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix3x2fStack;
import org.joml.Vector3f;

import com.mojang.blaze3d.platform.InputConstants;
import com.orbitalis.client.guide.GuideScene;
import com.orbitalis.client.guide.GuideSceneRenderState;
import com.orbitalis.client.guide.GuideSceneRenderer;
import com.orbitalis.client.guide.GuideScenes;
import com.orbitalis.core.guide.GuideChapter;
import com.orbitalis.core.guide.GuideChapters;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.phase1.conduit.ConduitBlock;
import com.orbitalis.phase1.machines.MachineBlock;
import com.orbitalis.phase1.spaceport.LaunchTableBlock;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * Le manuel d'Orbitalis : un lecteur de démonstrations, à la manière de celui de Create.
 * <p>
 * Il a deux vues, toutes deux en plein écran :
 * <ul>
 * <li>le sommaire : les chapitres rangés par rubriques, chacun sur sa tuile, avec une marque pour ceux qui ont une
 * démonstration animée ;</li>
 * <li>le lecteur : la construction en 3D occupe tout l'écran et se monte étape par étape. À chaque étape, une bulle
 * de texte pointe ce dont elle parle. En bas, une ligne de temps montre où l'on en est : ses repères sont les
 * étapes, on clique dessus pour y aller, et les commandes juste en dessous rejouent, reculent, mettent en pause et
 * avancent. Les notes du chapitre (son texte complet) s'ouvrent dans un volet à droite.</li>
 * </ul>
 * La scène se fait tourner en la faisant glisser de côté, monte et descend en la faisant glisser de haut en bas
 * (ou avec les flèches haut et bas), se zoome à la molette, et un clic sur un bloc dit ce que c'est.
 * Un chapitre sans démonstration s'ouvre directement sur son texte.
 * <p>
 * L'écran est entièrement dessiné par le code (aucune image) : il s'adapte à la taille de la fenêtre.
 */
public class GuideScreen extends Screen {
    private static final int TOP_BAR = 40;
    private static final int BOTTOM_BAR = 46;
    private static final int LINE_HEIGHT = 11;
    private static final int TILE_WIDTH = 122;
    private static final int TILE_HEIGHT = 24;
    private static final int TILE_GAP = 6;
    private static final int INDEX_TOP = 60;
    /** Hauteur réservée, en haut de la scène, à la bulle de l'étape. */
    private static final int CAPTION_BAND = 30;

    // Démonstrations : durée d'une étape, en ticks.
    private static final int STEP_TICKS = 100;
    // Caméra des démonstrations : vue depuis le sud-est, inclinée vers le bas. La scène oscille doucement
    // pour faire sentir le relief, et se fait tourner en la faisant glisser à la souris.
    private static final float BASE_YAW = 145.0F;
    private static final float PITCH = 28.0F;
    private static final float SWAY_DEGREES = 5.0F;
    // Zoom de la démonstration à la molette.
    private static final float MIN_ZOOM = 0.6F;
    private static final float MAX_ZOOM = 8.0F;

    /** Chapitre ouvert, et vue affichée ; conservés d'une ouverture à l'autre pour reprendre la lecture. */
    private static int chapterIndex;
    private static boolean atIndex = true;

    /** Tuile du sommaire : un chapitre, à sa place dans la page (hauteur comptée depuis le haut de la liste). */
    private record Tile(int chapter, int x, int y) {
    }

    /** Titre de rubrique du sommaire. */
    private record Heading(Component label, int y) {
    }

    private final List<Tile> tiles = new ArrayList<>();
    private final List<Heading> headings = new ArrayList<>();
    private int indexHeight;
    private int indexScroll;

    private GuideScene scene;
    private List<FormattedCharSequence> textLines = List.of();
    private int textScroll;
    /** Vrai si le volet des notes est ouvert à côté de la démonstration. */
    private boolean notes;

    // État de la démonstration
    private int sceneStep;
    private int stepTicks;
    private boolean paused;
    /** Vrai quand la démonstration est arrivée au bout : le bouton de lecture la rejoue. */
    private boolean finished;
    /** Rotation ajoutée par le joueur en faisant glisser la scène, en degrés. */
    private float userYaw;
    /** Zoom choisi par le joueur à la molette (1 = la construction entière tient dans le cadre). */
    private float zoom = 1.0F;
    /** Décalage vertical choisi par le joueur en faisant glisser la scène, en blocs, et sa limite pour cette scène. */
    private float userPan;
    private float panLimit;
    /** Ticks écoulés dans l'étape en cours, au moment du dessin : rythme les animations de l'étape. */
    private float sceneTime;
    // Cadrage de la démonstration au dernier dessin : sert à retrouver le bloc situé sous la souris.
    private float viewOriginX;
    private float viewOriginY;
    private float viewScale;
    private float viewYaw;
    private float viewCenterX;
    private float viewCenterY;
    private float viewCenterZ;
    /** Bloc désigné par un clic dans la démonstration : son nom est affiché au-dessus de lui. */
    private GuideScene.Piece selected;
    /** Vrai si le bouton a été enfoncé dans la démonstration, et si la souris a glissé depuis. */
    private boolean pressedInScene;
    private boolean draggedSincePress;
    /** Ticks écoulés depuis l'ouverture, pour les animations continues (oscillation, flux). */
    private int ageTicks;
    /** Vrai pour les captures de vérification : la vue reste fixe, sans oscillation ni rotation à la souris. */
    private boolean frozen;

    public GuideScreen() {
        super(Component.translatable("guide.orbitalis.title"));
    }

    /**
     * Ouvre directement un chapitre à une étape de sa démonstration (sert aux captures de vérification).
     *
     * @param page 0 pour la démonstration seule (ou le texte), 1 pour la démonstration avec ses notes
     */
    public static GuideScreen at(int chapter, int page, int step) {
        chapterIndex = chapter;
        atIndex = false;
        GuideScreen screen = new GuideScreen();
        screen.sceneStep = step;
        screen.stepTicks = STEP_TICKS / 2;
        screen.paused = true;
        screen.frozen = true;
        screen.notes = page > 0;
        return screen;
    }

    /** Ouvre le sommaire (sert aux captures de vérification). */
    public static GuideScreen index() {
        atIndex = true;
        return new GuideScreen();
    }

    // ------------------------------------------------------------------
    // Mise en page
    // ------------------------------------------------------------------

    @Override
    protected void init() {
        chapterIndex = Mth.clamp(chapterIndex, 0, GuideChapters.ALL.size() - 1);
        buildIndex();
        // Un redimensionnement de la fenêtre relance la mise en page : la démonstration reprend où elle en était.
        int keptStep = sceneStep;
        int keptTicks = stepTicks;
        boolean keptPause = paused;
        boolean keptNotes = notes;
        loadChapter();
        sceneStep = scene == null ? 0 : Mth.clamp(keptStep, 0, scene.stepCount() - 1);
        stepTicks = keptTicks;
        paused = keptPause;
        notes = keptNotes;
        layoutText();
    }

    /** Place les tuiles du sommaire : un titre à chaque changement de rubrique, puis ses chapitres en rangées. */
    private void buildIndex() {
        tiles.clear();
        headings.clear();
        int columns = Math.max(1, (width - 48 + TILE_GAP) / (TILE_WIDTH + TILE_GAP));
        int x0 = (width - (columns * (TILE_WIDTH + TILE_GAP) - TILE_GAP)) / 2;
        int y = 0;
        int column = 0;
        String section = null;
        for (int i = 0; i < GuideChapters.ALL.size(); i++) {
            GuideChapter chapter = GuideChapters.ALL.get(i);
            if (!chapter.section().equals(section)) {
                if (section != null) {
                    y += TILE_HEIGHT + 12;
                }
                section = chapter.section();
                headings.add(new Heading(Component.translatable(chapter.sectionKey()), y));
                y += 14;
                column = 0;
            } else if (column == columns) {
                y += TILE_HEIGHT + TILE_GAP;
                column = 0;
            }
            tiles.add(new Tile(i, x0 + column * (TILE_WIDTH + TILE_GAP), y));
            column++;
        }
        indexHeight = y + TILE_HEIGHT + 8;
        indexScroll = Mth.clamp(indexScroll, 0, maxIndexScroll());
    }

    private int maxIndexScroll() {
        return Math.max(0, indexHeight - (height - INDEX_TOP - 8));
    }

    /** Charge la démonstration du chapitre ouvert. */
    private void loadChapter() {
        GuideChapter chapter = GuideChapters.ALL.get(chapterIndex);
        scene = GuideScenes.get(chapter.id());
        selected = null;
        sceneStep = 0;
        stepTicks = 0;
        paused = false;
        finished = false;
        notes = false;
        textScroll = 0;
    }

    /** Largeur du volet des notes, ou de la colonne de texte d'un chapitre sans démonstration. */
    private int textWidth() {
        return scene == null ? Math.min(430, width - 80) : Mth.clamp(width / 3, 150, 230);
    }

    /** Découpe le texte du chapitre selon la place qu'il a. */
    private void layoutText() {
        GuideChapter chapter = GuideChapters.ALL.get(chapterIndex);
        textLines = font.split(Component.translatable(chapter.textKey()), textWidth() - 24);
        textScroll = Mth.clamp(textScroll, 0, maxTextScroll());
    }

    private int textTop() {
        return TOP_BAR + (scene == null ? 16 : 30);
    }

    private int textVisibleLines() {
        return Math.max(1, (height - textTop() - (scene == null ? 26 : BOTTOM_BAR + 12)) / LINE_HEIGHT);
    }

    private int maxTextScroll() {
        return Math.max(0, textLines.size() - textVisibleLines());
    }

    /** Bord gauche du texte : le volet à droite de la démonstration, ou la colonne au milieu de l'écran. */
    private int textLeft() {
        return scene == null ? (width - textWidth()) / 2 : width - textWidth();
    }

    /** Vrai si la démonstration a peu de largeur : sa bulle s'étale alors au-dessus d'elle au lieu de la côtoyer. */
    private boolean narrow() {
        return sceneWidth() < 340;
    }

    /** Largeur de la bulle de l'étape. */
    private int captionWidth() {
        return narrow() ? sceneWidth() - 60 : Mth.clamp(sceneWidth() / 3, 130, 210);
    }

    /** Largeur laissée à la démonstration. */
    private int sceneWidth() {
        return notes ? width - textWidth() : width;
    }

    private int sceneHeight() {
        return height - TOP_BAR - BOTTOM_BAR;
    }

    private void openChapter(int index) {
        chapterIndex = Mth.clamp(index, 0, GuideChapters.ALL.size() - 1);
        atIndex = false;
        zoom = 1.0F;
        userYaw = 0.0F;
        userPan = 0.0F;
        loadChapter();
        layoutText();
        Ui.click();
    }

    /** Va à une étape de la démonstration, et la joue depuis son début. */
    private void goToStep(int step) {
        if (scene != null) {
            sceneStep = Mth.clamp(step, 0, scene.stepCount() - 1);
            stepTicks = 0;
            finished = false;
            Ui.click();
        }
    }

    private void togglePlay() {
        if (scene == null) {
            return;
        }
        if (finished) {
            // Arrivée au bout : la lecture reprend du début.
            sceneStep = 0;
            stepTicks = 0;
            finished = false;
            paused = false;
        } else {
            paused = !paused;
        }
        Ui.click();
    }

    // ------------------------------------------------------------------
    // Animation
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        ageTicks++;
        if (atIndex || scene == null || paused || finished) {
            return;
        }
        if (++stepTicks >= STEP_TICKS) {
            if (sceneStep == scene.stepCount() - 1) {
                // La dernière étape reste à l'écran : tout est en place, on peut regarder.
                stepTicks = STEP_TICKS;
                finished = true;
            } else {
                sceneStep++;
                stepTicks = 0;
            }
        }
    }

    // ------------------------------------------------------------------
    // Ligne de temps et commandes
    // ------------------------------------------------------------------

    private int timelineLeft() {
        return sceneWidth() < 360 ? 30 : 64;
    }

    private int timelineRight() {
        return sceneWidth() - timelineLeft();
    }

    private int timelineY() {
        return height - BOTTOM_BAR + 10;
    }

    /** Position d'un repère d'étape sur la ligne de temps. */
    private int markX(int step) {
        return timelineLeft() + (timelineRight() - timelineLeft()) * step / scene.stepCount();
    }

    private int controlsY() {
        return height - 24;
    }

    /** Vrai si la ligne de temps est assez large pour porter aussi le numéro de l'étape, à gauche des commandes. */
    private boolean roomy() {
        return timelineRight() - timelineLeft() >= 300;
    }

    /** Bord gauche d'un bouton de commande : rejouer, étape précédente, lecture, étape suivante. */
    private int controlX(int index) {
        // À l'étroit, les commandes se rangent à gauche, et laissent la droite au bouton des notes.
        int start = roomy() ? (timelineLeft() + timelineRight()) / 2 - 41 : timelineLeft();
        return start + index * 21;
    }

    private int notesButtonWidth() {
        return font.width(Component.translatable("guide.orbitalis.notes").getString().toUpperCase(java.util.Locale.ROOT)) + 14;
    }

    private int indexButtonWidth() {
        return font.width(Component.translatable("guide.orbitalis.index").getString().toUpperCase(java.util.Locale.ROOT)) + 14;
    }

    private boolean hasNeighbour(int delta) {
        return chapterIndex + delta >= 0 && chapterIndex + delta < GuideChapters.ALL.size();
    }

    /** Bord gauche de la flèche qui mène au chapitre voisin. */
    private int arrowX(int delta) {
        return delta < 0 ? 8 : (scene == null ? width : sceneWidth()) - 26;
    }

    private int arrowY() {
        return TOP_BAR + (height - TOP_BAR - BOTTOM_BAR) / 2 - 16;
    }

    // ------------------------------------------------------------------
    // Souris et clavier
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (atIndex) {
            if (mouseY >= INDEX_TOP) {
                for (Tile tile : tiles) {
                    if (Ui.inside(mouseX, mouseY, tile.x(), INDEX_TOP + tile.y() - indexScroll, TILE_WIDTH, TILE_HEIGHT)) {
                        openChapter(tile.chapter());
                        return true;
                    }
                }
            }
            return super.mouseClicked(event, doubleClick);
        }
        // Barre du haut : retour au sommaire.
        if (Ui.inside(mouseX, mouseY, width - 12 - indexButtonWidth(), 12, indexButtonWidth(), 16)) {
            atIndex = true;
            Ui.click();
            return true;
        }
        // Chapitres voisins.
        for (int delta = -1; delta <= 1; delta += 2) {
            if (hasNeighbour(delta) && Ui.inside(mouseX, mouseY, arrowX(delta), arrowY(), 18, 32)) {
                openChapter(chapterIndex + delta);
                return true;
            }
        }
        if (scene == null) {
            return super.mouseClicked(event, doubleClick);
        }
        // Ligne de temps : un clic mène à l'étape visée.
        if (Ui.inside(mouseX, mouseY, timelineLeft() - 4, timelineY() - 6, timelineRight() - timelineLeft() + 8, 14)) {
            goToStep((int) ((mouseX - timelineLeft()) * scene.stepCount() / Math.max(1, timelineRight() - timelineLeft())));
            return true;
        }
        for (int k = 0; k < 4; k++) {
            if (Ui.inside(mouseX, mouseY, controlX(k), controlsY(), 18, 16)) {
                switch (k) {
                    case 0 -> goToStep(0);
                    case 1 -> goToStep(sceneStep - 1);
                    case 2 -> togglePlay();
                    default -> goToStep(sceneStep + 1);
                }
                return true;
            }
        }
        if (Ui.inside(mouseX, mouseY, timelineRight() - notesButtonWidth(), controlsY(), notesButtonWidth(), 16)) {
            notes = !notes;
            layoutText();
            Ui.click();
            return true;
        }
        if (!frozen && event.button() == 0 && Ui.inside(mouseX, mouseY, 0, TOP_BAR, sceneWidth(), sceneHeight())) {
            pressedInScene = true;
            draggedSincePress = false;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** Un clic gauche sans glisser désigne le bloc sous la souris ; un clic dans le vide retire l'étiquette. */
    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (pressedInScene && event.button() == 0) {
            pressedInScene = false;
            if (!draggedSincePress && scene != null && !atIndex) {
                GuideScene.Piece piece = pieceUnder(event.x(), event.y());
                if (piece != selected) {
                    selected = piece;
                    Ui.click();
                }
                return true;
            }
        }
        return super.mouseReleased(event);
    }

    /** Faire glisser la souris dans la démonstration la fait tourner. */
    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (pressedInScene && !frozen) {
            // De côté, la scène tourne ; de haut en bas, elle monte ou descend : on parcourt ainsi une grande tour.
            userYaw += (float) dx * 0.8F;
            userPan = Mth.clamp(userPan + (float) dy / Math.max(1.0F, viewScale), -panLimit, panLimit);
            draggedSincePress |= Math.abs(dx) > 0.5 || Math.abs(dy) > 0.5;
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (atIndex) {
            indexScroll = Mth.clamp(indexScroll - (int) (scrollY * (TILE_HEIGHT + TILE_GAP)), 0, maxIndexScroll());
        } else if (scene == null || (notes && mouseX >= textLeft())) {
            textScroll = Mth.clamp(textScroll - (int) Math.signum(scrollY) * 2, 0, maxTextScroll());
        } else if (!frozen && mouseY >= TOP_BAR && mouseY < height - BOTTOM_BAR) {
            // La molette zoome dans la démonstration.
            zoom = Mth.clamp(zoom * (float) Math.pow(1.15, scrollY), MIN_ZOOM, MAX_ZOOM);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (atIndex) {
            return super.keyPressed(event);
        }
        switch (event.key()) {
            case InputConstants.KEY_LEFT -> goToStep(sceneStep - 1);
            case InputConstants.KEY_RIGHT -> goToStep(sceneStep + 1);
            case InputConstants.KEY_SPACE -> togglePlay();
            case InputConstants.KEY_UP -> userPan = Mth.clamp(userPan + 2.0F, -panLimit, panLimit);
            case InputConstants.KEY_DOWN -> userPan = Mth.clamp(userPan - 2.0F, -panLimit, panLimit);
            case InputConstants.KEY_PAGEUP -> openChapter(chapterIndex - 1);
            case InputConstants.KEY_PAGEDOWN -> openChapter(chapterIndex + 1);
            case InputConstants.KEY_TAB, InputConstants.KEY_BACKSPACE -> atIndex = true;
            default -> {
                return super.keyPressed(event);
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Dessin
    // ------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        // Tout l'écran : le monde s'efface derrière le manuel.
        graphics.fill(0, 0, width, height, 0xF205070A);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (atIndex) {
            extractIndex(graphics, mouseX, mouseY);
            return;
        }
        if (scene != null) {
            float time = stepTicks + (paused || finished ? 0.0F : partialTick);
            sceneTime = time;
            graphics.enableScissor(0, TOP_BAR, sceneWidth(), height - BOTTOM_BAR);
            // Le haut de la zone est laissé à la bulle de l'étape : la construction est cadrée dessous. À l'étroit,
            // la bulle prend toute la largeur, et la scène lui laisse exactement sa hauteur.
            int band = CAPTION_BAND;
            if (narrow()) {
                int lines = font.split(Component.translatable(scene.captionKey(sceneStep)), captionWidth() - 16).size();
                band = Math.min(sceneHeight() / 2, lines * LINE_HEIGHT + 34);
            }
            extractSceneContent(graphics, 0, TOP_BAR + band, sceneWidth(), sceneHeight() - band, time, partialTick);
            extractCaption(graphics, time);
            graphics.disableScissor();
            extractTimeline(graphics, mouseX, mouseY, time);
        }
        if (scene == null || notes) {
            extractText(graphics);
        }
        extractTopBar(graphics, mouseX, mouseY);
        extractArrows(graphics, mouseX, mouseY);
    }

    /** Le sommaire : les rubriques, et une tuile par chapitre. */
    private void extractIndex(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x0 = tiles.isEmpty() ? 24 : tiles.get(0).x();
        Ui.big(graphics, font, title.getString().toUpperCase(java.util.Locale.ROOT), x0, 18, 2.0F, Ui.TEXT);
        graphics.text(font, Ui.fit(font, Component.translatable("guide.orbitalis.index.hint").getString(), width - 2 * x0), x0, 40, Ui.MUTED, false);
        Ui.hline(graphics, x0, INDEX_TOP - 8, width - 2 * x0);

        graphics.enableScissor(0, INDEX_TOP - 4, width, height - 4);
        for (Heading heading : headings) {
            Ui.heading(graphics, font, heading.label(), x0, INDEX_TOP + heading.y() - indexScroll);
        }
        for (Tile tile : tiles) {
            int y = INDEX_TOP + tile.y() - indexScroll;
            if (y + TILE_HEIGHT < INDEX_TOP - 4 || y > height) {
                continue;
            }
            GuideChapter chapter = GuideChapters.ALL.get(tile.chapter());
            boolean hovered = mouseY >= INDEX_TOP - 4 && Ui.inside(mouseX, mouseY, tile.x(), y, TILE_WIDTH, TILE_HEIGHT);
            Ui.card(graphics, tile.x(), y, TILE_WIDTH, TILE_HEIGHT, hovered);
            if (tile.chapter() == chapterIndex) {
                // Le chapitre qu'on lisait : un trait d'accent.
                graphics.fill(tile.x(), y + 3, tile.x() + 1, y + TILE_HEIGHT - 3, Ui.ACCENT);
            }
            graphics.item(new ItemStack(chapter.icon().get()), tile.x() + 4, y + 4);
            boolean animated = GuideScenes.get(chapter.id()) != null;
            String name = Ui.fit(font, Component.translatable(chapter.titleKey()).getString(), TILE_WIDTH - 28 - (animated ? 12 : 4));
            graphics.text(font, name, tile.x() + 24, y + 8, hovered ? Ui.TEXT : 0xFFD5DAE0, false);
            if (animated) {
                icon(graphics, 2, tile.x() + TILE_WIDTH - 12, y + 8, hovered ? Ui.ACCENT : Ui.DIM);
            }
        }
        graphics.disableScissor();
        if (maxIndexScroll() > 0) {
            int track = height - INDEX_TOP - 8;
            int thumb = Math.max(16, track * track / indexHeight);
            int thumbY = INDEX_TOP + (track - thumb) * indexScroll / maxIndexScroll();
            graphics.fill(width - 5, thumbY, width - 3, thumbY + thumb, Ui.LINE_HI);
        }
    }

    /** Barre du haut du lecteur : le chapitre ouvert, et le retour au sommaire. */
    private void extractTopBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        GuideChapter chapter = GuideChapters.ALL.get(chapterIndex);
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(12, 4);
        pose.scale(2.0F, 2.0F);
        graphics.item(new ItemStack(chapter.icon().get()), 0, 0);
        pose.popMatrix();
        Ui.heading(graphics, font, Component.translatable(chapter.sectionKey()), 52, 9);
        graphics.text(font, Component.translatable(chapter.titleKey()).withStyle(ChatFormatting.BOLD), 52, 21, Ui.TEXT, false);

        int x = width - 12 - indexButtonWidth();
        Ui.button(graphics, font, Component.translatable("guide.orbitalis.index"), x, 12, indexButtonWidth(), 16,
                Ui.inside(mouseX, mouseY, x, 12, indexButtonWidth(), 16), true, false, Ui.MUTED);
        String place = (chapterIndex + 1) + " / " + GuideChapters.ALL.size();
        Ui.right(graphics, font, place, x - 10, 16, Ui.DIM);
        Ui.hline(graphics, 0, TOP_BAR - 1, width);
    }

    /** Les deux flèches, aux bords de l'écran, qui mènent au chapitre d'avant et à celui d'après. */
    private void extractArrows(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (int delta = -1; delta <= 1; delta += 2) {
            if (!hasNeighbour(delta)) {
                continue;
            }
            int x = arrowX(delta);
            int y = arrowY();
            boolean hovered = Ui.inside(mouseX, mouseY, x, y, 18, 32);
            if (hovered) {
                Ui.round(graphics, x, y, 18, 32, Ui.SURFACE_HI);
            }
            // Un chevron : sept traits, de plus en plus décalés.
            for (int k = 0; k < 7; k++) {
                int px = x + 6 + (delta < 0 ? Math.abs(3 - k) : 3 - Math.abs(3 - k));
                graphics.fill(px, y + 9 + k * 2, px + 2, y + 11 + k * 2, hovered ? Ui.TEXT : Ui.DIM);
            }
            if (hovered) {
                // Le nom du chapitre où elle mène.
                GuideChapter neighbour = GuideChapters.ALL.get(chapterIndex + delta);
                Component name = Component.translatable(neighbour.titleKey());
                int w = font.width(name) + 12;
                int tx = delta < 0 ? x + 22 : x - 4 - w;
                Ui.card(graphics, tx, y + 8, w, 16, true);
                graphics.text(font, name, tx + 6, y + 12, Ui.TEXT, false);
            }
        }
    }

    /** Le texte du chapitre : dans un volet à droite de la démonstration, ou seul au milieu de l'écran. */
    private void extractText(GuiGraphicsExtractor graphics) {
        int x = textLeft();
        int w = textWidth();
        if (scene != null) {
            graphics.fill(x, TOP_BAR, width, height, 0xFF0A0D11);
            graphics.fill(x, TOP_BAR, x + 1, height, Ui.LINE);
            Ui.heading(graphics, font, Component.translatable("guide.orbitalis.notes"), x + 12, TOP_BAR + 12);
        }
        int y = textTop();
        int visible = textVisibleLines();
        for (int i = 0; i < visible && i + textScroll < textLines.size(); i++) {
            graphics.text(font, textLines.get(i + textScroll), x + 12, y + i * LINE_HEIGHT, 0xFFD5DAE0, false);
        }
        if (maxTextScroll() > 0) {
            int track = visible * LINE_HEIGHT;
            int thumb = Math.max(12, track * visible / textLines.size());
            int thumbY = y + (track - thumb) * textScroll / maxTextScroll();
            graphics.fill(x + w - 5, thumbY, x + w - 3, thumbY + thumb, Ui.LINE_HI);
        }
    }

    /**
     * Ligne de temps de la démonstration : elle se remplit à mesure que la lecture avance, et porte un repère par
     * étape. Dessous, le numéro de l'étape, les commandes, et le bouton des notes.
     */
    private void extractTimeline(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float time) {
        int x0 = timelineLeft();
        int x1 = timelineRight();
        int y = timelineY();
        graphics.fill(0, height - BOTTOM_BAR, sceneWidth(), height - BOTTOM_BAR + 1, Ui.LINE);
        float progress = (sceneStep + Mth.clamp(time / STEP_TICKS, 0.0F, 1.0F)) / scene.stepCount();
        graphics.fill(x0, y, x1, y + 2, Ui.LINE_HI);
        graphics.fill(x0, y, x0 + Math.round((x1 - x0) * progress), y + 2, Ui.ACCENT);
        boolean overLine = Ui.inside(mouseX, mouseY, x0 - 4, y - 6, x1 - x0 + 8, 14);
        int hoveredStep = overLine ? Mth.clamp((mouseX - x0) * scene.stepCount() / Math.max(1, x1 - x0), 0, scene.stepCount() - 1) : -1;
        for (int step = 0; step < scene.stepCount(); step++) {
            int x = markX(step);
            int color = step == sceneStep ? Ui.TEXT : step < sceneStep ? Ui.ACCENT : Ui.LINE_HI;
            if (step == hoveredStep || step == sceneStep) {
                graphics.fill(x - 1, y - 4, x + 2, y + 6, color);
            } else {
                graphics.fill(x, y - 2, x + 1, y + 4, color);
            }
        }
        // Le bout de la ligne : la fin de la démonstration.
        graphics.fill(x1, y - 2, x1 + 1, y + 4, finished ? Ui.ACCENT : Ui.LINE_HI);

        int cy = controlsY();
        if (roomy()) {
            graphics.text(font, Component.translatable("guide.orbitalis.step", (hoveredStep >= 0 ? hoveredStep : sceneStep) + 1, scene.stepCount()),
                    x0, cy + 4, hoveredStep >= 0 ? Ui.TEXT : Ui.MUTED, false);
        }
        for (int k = 0; k < 4; k++) {
            int x = controlX(k);
            boolean hovered = Ui.inside(mouseX, mouseY, x, cy, 18, 16);
            boolean main = k == 2;
            if (main) {
                Ui.round(graphics, x, cy, 18, 16, hovered ? Ui.TEXT : Ui.ACCENT);
            } else if (hovered) {
                Ui.round(graphics, x, cy, 18, 16, Ui.SURFACE_HI);
            }
            int kind = k == 2 ? (finished ? 0 : paused ? 2 : 4) : k;
            icon(graphics, kind, x + 5, cy + 4, main ? 0xFF05070A : hovered ? Ui.TEXT : Ui.MUTED);
        }
        int nx = x1 - notesButtonWidth();
        Ui.button(graphics, font, Component.translatable("guide.orbitalis.notes"), nx, cy, notesButtonWidth(), 16,
                Ui.inside(mouseX, mouseY, nx, cy, notesButtonWidth(), 16), true, false, notes ? Ui.ACCENT : Ui.MUTED);
    }

    /**
     * Petit pictogramme de commande, sur 8 pixels : 0 rejouer (retour au début), 1 étape précédente, 2 lecture,
     * 3 étape suivante, 4 pause.
     */
    private static void icon(GuiGraphicsExtractor graphics, int kind, int x, int y, int color) {
        switch (kind) {
            case 0 -> {
                graphics.fill(x, y, x + 2, y + 8, color);
                triangle(graphics, x + 3, y, color, true);
            }
            case 1 -> triangle(graphics, x + 1, y, color, true);
            case 2 -> triangle(graphics, x + 2, y, color, false);
            case 3 -> {
                triangle(graphics, x, y, color, false);
                graphics.fill(x + 6, y, x + 8, y + 8, color);
            }
            default -> {
                graphics.fill(x + 1, y, x + 3, y + 8, color);
                graphics.fill(x + 5, y, x + 7, y + 8, color);
            }
        }
    }

    /** Triangle plein de 4 pixels de large et 8 de haut, pointe à droite ou à gauche. */
    private static void triangle(GuiGraphicsExtractor graphics, int x, int y, int color, boolean pointsLeft) {
        for (int column = 0; column < 4; column++) {
            int px = pointsLeft ? x + 3 - column : x + column;
            graphics.fill(px, y + column, px + 1, y + 8 - column, color);
        }
    }

    /**
     * Ce dont parle l'étape en cours, dans la scène : le milieu de ce qui vient d'être posé, sinon de l'objet qui
     * flotte, sinon de la machine qui vient de s'allumer, sinon le haut de la construction.
     */
    private Vector3f focus() {
        float x = 0.0F;
        float y = 0.0F;
        float z = 0.0F;
        int count = 0;
        for (GuideScene.Piece piece : scene.pieces()) {
            if (piece.from() == sceneStep && piece.visibleAt(sceneStep)) {
                x += piece.x() + 0.5F;
                y = Math.max(y, piece.y() + 1.0F);
                z += piece.z() + 0.5F;
                count++;
            }
        }
        if (count == 0) {
            for (GuideScene.Marker marker : scene.markers()) {
                if (marker.step() == sceneStep) {
                    x += marker.x() + 0.5F;
                    y = Math.max(y, marker.y() + 0.9F);
                    z += marker.z() + 0.5F;
                    count++;
                }
            }
        }
        if (count == 0) {
            for (GuideScene.Piece piece : scene.pieces()) {
                if (piece.litFrom() == sceneStep && piece.visibleAt(sceneStep)) {
                    x += piece.x() + 0.5F;
                    y = Math.max(y, piece.y() + 1.0F);
                    z += piece.z() + 0.5F;
                    count++;
                }
            }
        }
        if (count == 0) {
            for (GuideScene.Piece piece : scene.pieces()) {
                if (piece.visibleAt(sceneStep)) {
                    x += piece.x() + 0.5F;
                    y = Math.max(y, piece.y() + 1.0F);
                    z += piece.z() + 0.5F;
                    count++;
                }
            }
        }
        return count == 0 ? new Vector3f(viewCenterX, viewCenterY, viewCenterZ) : new Vector3f(x / count, y, z / count);
    }

    /**
     * La bulle de l'étape : son texte, dans un cadre relié par un trait à ce dont il parle. Elle se place à droite
     * de son sujet, ou à gauche s'il n'y a pas la place, sans jamais sortir de la scène.
     */
    private void extractCaption(GuiGraphicsExtractor graphics, float time) {
        Vector3f focus = focus();
        Vector3f projected = GuideSceneRenderer.project(focus.x - viewCenterX, focus.y - viewCenterY, focus.z - viewCenterZ, viewYaw, PITCH);
        int anchorX = Math.round(viewOriginX + projected.x * viewScale);
        int anchorY = Math.round(viewOriginY + projected.y * viewScale);
        int areaWidth = sceneWidth();
        int boxWidth = captionWidth();
        List<FormattedCharSequence> lines = font.split(Component.translatable(scene.captionKey(sceneStep)), boxWidth - 16);
        int boxHeight = lines.size() * LINE_HEIGHT + 22;
        anchorX = Mth.clamp(anchorX, 30, areaWidth - 30);
        anchorY = Mth.clamp(anchorY, TOP_BAR + 20, height - BOTTOM_BAR - 20);
        boolean toRight = anchorX + 34 + boxWidth <= areaWidth - 30 || anchorX - 34 - boxWidth < 30;
        int boxX = narrow() ? 30 : Mth.clamp(toRight ? anchorX + 34 : anchorX - 34 - boxWidth, 30, Math.max(30, areaWidth - 30 - boxWidth));
        // La bulle se tient en haut de la scène, au-dessus de la construction, pour ne rien cacher.
        int boxY = TOP_BAR + 8;
        // La bulle arrive en glissant, au début de l'étape.
        float appear = frozen ? 1.0F : Mth.clamp(time / 8.0F, 0.0F, 1.0F);
        boxY += Math.round((1.0F - appear) * 6.0F);

        // Le trait : il monte du sujet jusqu'à la hauteur de la bulle, puis la rejoint.
        int joinY = boxY + boxHeight / 2;
        int joinX = narrow() ? Mth.clamp(anchorX, boxX, boxX + boxWidth) : toRight ? boxX : boxX + boxWidth;
        if (narrow()) {
            joinY = boxY + boxHeight;
        }
        graphics.fill(anchorX, Math.min(anchorY, joinY), anchorX + 1, Math.max(anchorY, joinY) + 1, Ui.ACCENT);
        graphics.fill(Math.min(anchorX, joinX), joinY, Math.max(anchorX, joinX) + 1, joinY + 1, Ui.ACCENT);
        Ui.dot(graphics, anchorX - 2, anchorY - 2, Ui.ACCENT);

        Ui.round(graphics, boxX, boxY, boxWidth, boxHeight, 0xF00B0E12);
        Ui.frame(graphics, boxX, boxY, boxWidth, boxHeight, Ui.LINE_HI);
        graphics.fill(boxX, boxY + 3, boxX + 1, boxY + boxHeight - 3, Ui.ACCENT);
        graphics.text(font, Component.translatable("guide.orbitalis.step", sceneStep + 1, scene.stepCount()).getString().toUpperCase(java.util.Locale.ROOT),
                boxX + 8, boxY + 6, Ui.ACCENT, false);
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(font, lines.get(i), boxX + 8, boxY + 18 + i * LINE_HEIGHT, Ui.TEXT, false);
        }
    }

    /** Blocs auxquels un tuyau se raccorde (ceux qui contiennent un fluide). */
    private static boolean holdsFluid(Block block) {
        return block == ModBlocks.ELECTROLYZER.get() || block == ModBlocks.CRYOGENIC_LIQUEFIER.get()
                || block == ModBlocks.REFINERY.get() || block == ModBlocks.SABATIER_REACTOR.get() || block == ModBlocks.HELIUM3_EXTRACTOR.get() || block == ModBlocks.CRYOGENIC_TANK.get()
                || block == ModBlocks.FUSION_REACTOR.get();
    }

    /** Angle de vue courant : l'angle de base, la rotation du joueur et une légère oscillation. */
    private float sceneYaw(float partialTick) {
        if (frozen) {
            return BASE_YAW;
        }
        return BASE_YAW + userYaw + Mth.sin((ageTicks + partialTick) * 0.035F) * SWAY_DEGREES;
    }

    /** État de bloc affiché pour une pièce : orientation, face allumée, raccordements des conduits. */
    private BlockState pieceState(GuideScene.Piece piece) {
        Block block = piece.block().get();
        BlockState state = block.defaultBlockState();
        if (block instanceof MachineBlock) {
            return state.setValue(MachineBlock.FACING, piece.facing()).setValue(MachineBlock.LIT, piece.litAt(sceneStep));
        }
        if (block instanceof ConduitBlock conduit) {
            for (Direction direction : Direction.values()) {
                GuideScene.Piece neighbour = pieceAt(piece.x() + direction.getStepX(), piece.y() + direction.getStepY(),
                        piece.z() + direction.getStepZ());
                state = state.setValue(ConduitBlock.LINKS.get(direction), neighbour == null || !connects(conduit, neighbour)
                        ? ConduitBlock.Link.NONE
                        : neighbour.block().get() == block ? ConduitBlock.Link.CONDUIT : ConduitBlock.Link.MACHINE);
            }
            if (state.hasProperty(ConduitBlock.GLOW)) {
                state = state.setValue(ConduitBlock.GLOW, conduitGlow(piece));
            }
        }
        // Table de lancement : ses voyants s'allument à la mise en service. Autres blocs orientables : face avant vers la caméra.
        if (block instanceof LaunchTableBlock) {
            state = state.setValue(LaunchTableBlock.FORMED, piece.litAt(sceneStep));
        }
        if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            state = state.setValue(HorizontalDirectionalBlock.FACING, piece.facing());
        }
        return state;
    }

    /**
     * Lueur d'un conduit de la démonstration, comme en jeu : il s'illumine progressivement
     * dès qu'un flux le traverse.
     */
    private int conduitGlow(GuideScene.Piece piece) {
        int glow = 0;
        for (GuideScene.Flow flow : scene.flows()) {
            if (!flow.visibleAt(sceneStep) || !passesThrough(flow, piece)) {
                continue;
            }
            int level = flow.from() < sceneStep ? ConduitBlock.MAX_GLOW : Mth.clamp((int) (sceneTime / 5.0F), 0, ConduitBlock.MAX_GLOW);
            glow = Math.max(glow, level);
        }
        return glow;
    }

    /** Vrai si le trajet du flux (fait de tronçons droits) passe par la case de cette pièce. */
    private static boolean passesThrough(GuideScene.Flow flow, GuideScene.Piece piece) {
        float[][] points = flow.points();
        for (int i = 0; i + 1 < points.length; i++) {
            float[] a = points[i];
            float[] b = points[i + 1];
            if (piece.x() >= Math.min(a[0], b[0]) && piece.x() <= Math.max(a[0], b[0])
                    && piece.y() >= Math.min(a[1], b[1]) && piece.y() <= Math.max(a[1], b[1])
                    && piece.z() >= Math.min(a[2], b[2]) && piece.z() <= Math.max(a[2], b[2])) {
                return true;
            }
        }
        return false;
    }

    /** Pièce visible à cette position à l'étape courante, ou {@code null}. */
    private GuideScene.Piece pieceAt(int x, int y, int z) {
        for (GuideScene.Piece piece : scene.pieces()) {
            if (piece.visibleAt(sceneStep) && piece.x() == x && piece.y() == y && piece.z() == z) {
                return piece;
            }
        }
        return null;
    }

    /** Mêmes règles qu'en jeu : un conduit se raccorde à ses semblables et aux machines qu'il peut desservir. */
    private static boolean connects(ConduitBlock conduit, GuideScene.Piece neighbour) {
        Block block = neighbour.block().get();
        if (block == conduit) {
            return true;
        }
        if (!(block instanceof MachineBlock)) {
            return false;
        }
        // Un câble dessert toutes les machines ; un tuyau seulement celles qui contiennent un fluide.
        return conduit.getCapability() == Capabilities.Energy.BLOCK || holdsFluid(block);
    }

    private void extractSceneContent(GuiGraphicsExtractor graphics, int x, int y, int areaWidth, int areaHeight, float time, float partialTick) {
        // Emprise de la construction entière (toutes étapes confondues), pour un cadrage stable.
        float minX = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = 1.0F;
        float maxZ = -Float.MAX_VALUE;
        for (GuideScene.Piece piece : scene.pieces()) {
            minX = Math.min(minX, piece.x());
            maxX = Math.max(maxX, piece.x() + 1);
            maxY = Math.max(maxY, piece.y() + 1);
            minZ = Math.min(minZ, piece.z());
            maxZ = Math.max(maxZ, piece.z() + 1);
        }
        // Volume à cadrer : la construction, une partie de son socle et la place des objets flottants.
        float boxMinX = minX - 0.5F;
        float boxMaxX = maxX + 0.5F;
        float boxMinY = -1.0F;
        float boxMaxY = maxY + 0.9F;
        float boxMinZ = minZ - 0.5F;
        float boxMaxZ = maxZ + 0.5F;
        float centerX = (boxMinX + boxMaxX) / 2.0F;
        // Le joueur peut faire monter ou descendre la scène : au plus jusqu'à amener son sommet ou son pied au milieu.
        panLimit = (boxMaxY - boxMinY) / 2.0F;
        userPan = Mth.clamp(userPan, -panLimit, panLimit);
        float centerY = (boxMinY + boxMaxY) / 2.0F + userPan;
        float centerZ = (boxMinZ + boxMaxZ) / 2.0F;

        // Échelle : la plus grande pour laquelle ce volume tient dans la zone quel que soit l'angle de vue.
        // On cadre le cylindre vertical qui contient le volume : la taille à l'écran ne dépend donc pas de
        // la rotation, et la scène ne se rapproche ni ne s'éloigne quand on la fait tourner.
        float radius = (float) Math.hypot(boxMaxX - boxMinX, boxMaxZ - boxMinZ) / 2.0F;
        float pitchRadians = PITCH * Mth.DEG_TO_RAD;
        float halfWidth = radius;
        float halfHeight = (boxMaxY - boxMinY) / 2.0F * Mth.cos(pitchRadians) + radius * Mth.sin(pitchRadians);
        float scale = Math.min((areaWidth - 12) / (2.0F * halfWidth), (areaHeight - 8) / (2.0F * halfHeight));
        // Le plancher est bas : un pas de tir 11×11 et sa tour de 64 segments doivent tenir en entier dans la zone.
        scale = Mth.clamp(scale, 1.2F, 56.0F) * zoom;

        float yaw = sceneYaw(partialTick);
        List<GuideSceneRenderState.PlacedBlock> blocks = new ArrayList<>();

        // Socle en damier sous la construction, pour donner un sol à la scène.
        for (int fx = (int) minX - 1; fx < (int) maxX + 1; fx++) {
            for (int fz = (int) minZ - 1; fz < (int) maxZ + 1; fz++) {
                Block tile = Math.floorMod(fx + fz, 2) == 0 ? Blocks.SMOOTH_QUARTZ : Blocks.CALCITE;
                blocks.add(new GuideSceneRenderState.PlacedBlock(tile.defaultBlockState(), fx, -1.0F, fz));
            }
        }

        for (GuideScene.Piece piece : scene.pieces()) {
            if (!piece.visibleAt(sceneStep)) {
                continue;
            }
            // Un bloc qui vient d'être posé descend à sa place, en ralentissant.
            float drop = 0.0F;
            if (piece.from() == sceneStep && sceneStep > 0) {
                float t = 1.0F - Mth.clamp(time / 10.0F, 0.0F, 1.0F);
                drop = t * t * t * 1.5F;
            }
            blocks.add(new GuideSceneRenderState.PlacedBlock(pieceState(piece), piece.x(), piece.y() + drop, piece.z()));
        }

        graphics.submitPictureInPictureRenderState(new GuideSceneRenderState(blocks, yaw, PITCH, centerX, centerY, centerZ,
                x, y, x + areaWidth, y + areaHeight, scale, graphics.peekScissorStack()));

        // Tout ce qui suit est dessiné par-dessus l'image 3D.
        graphics.nextStratum();
        float originX = x + areaWidth / 2.0F;
        float originY = y + areaHeight / 2.0F;
        viewOriginX = originX;
        viewOriginY = originY;
        viewScale = scale;
        viewYaw = yaw;
        viewCenterX = centerX;
        viewCenterY = centerY;
        viewCenterZ = centerZ;

        for (GuideScene.Flow flow : scene.flows()) {
            if (flow.visibleAt(sceneStep)) {
                extractFlow(graphics, flow, originX, originY, scale, yaw, centerX, centerY, centerZ, ageTicks + partialTick,
                        flow.from() == sceneStep ? time : Float.MAX_VALUE);
            }
        }

        // Objets qui flottent au-dessus des machines.
        Matrix3x2fStack pose = graphics.pose();
        for (GuideScene.Marker marker : scene.markers()) {
            if (marker.step() != sceneStep) {
                continue;
            }
            float bob = Mth.sin((ageTicks + partialTick) * 0.12F) * 0.06F;
            Vector3f projected = GuideSceneRenderer.project(marker.x() + 0.5F - centerX, marker.y() + 0.5F + bob - centerY,
                    marker.z() + 0.5F - centerZ, yaw, PITCH);
            float markerScale = Math.max(1.0F, scale / 16.0F * 0.8F);
            // L'objet apparaît en grandissant.
            float appear = Mth.clamp(time / 6.0F, 0.0F, 1.0F);
            markerScale *= 0.4F + 0.6F * appear;
            pose.pushMatrix();
            pose.translate(originX + projected.x * scale - 8 * markerScale, originY + projected.y * scale - 8 * markerScale);
            pose.scale(markerScale, markerScale);
            graphics.item(new ItemStack(marker.item()), 0, 0);
            pose.popMatrix();
        }

        extractSelection(graphics, x, y, areaWidth, areaHeight);
    }

    /**
     * Flux : un tube lumineux le long du trajet, parcouru par des ondes de lumière qui avancent dans le sens
     * du transfert.
     * <p>
     * Le tube est attaché à la scène : son épaisseur est toujours perpendiculaire à son axe, et les ondes sont
     * placées d'après la distance parcourue dans la scène (en blocs), pas à l'écran. Il garde donc le même aspect
     * sous tous les angles et tourne avec la construction. Il est dessiné par-dessus les blocs, pour rester
     * visible même à l'intérieur des machines.
     *
     * @param sinceStart ticks écoulés depuis l'apparition du flux : le tube se déroule depuis sa source
     */
    private void extractFlow(GuiGraphicsExtractor graphics, GuideScene.Flow flow, float originX, float originY, float scale, float yaw,
            float centerX, float centerY, float centerZ, float age, float sinceStart) {
        float guiScale = (float) minecraft.getWindow().getGuiScale();
        float[][] points = flow.points();

        // Trajet à l'écran, en pixels réels, et longueur de chaque tronçon dans la scène, en blocs.
        float[] px = new float[points.length];
        float[] py = new float[points.length];
        float[] lengths = new float[points.length];
        float total = 0.0F;
        for (int i = 0; i < points.length; i++) {
            Vector3f projected = GuideSceneRenderer.project(points[i][0] + 0.5F - centerX, points[i][1] + 0.5F - centerY,
                    points[i][2] + 0.5F - centerZ, yaw, PITCH);
            px[i] = (originX + projected.x * scale) * guiScale;
            py[i] = (originY + projected.y * scale) * guiScale;
            if (i > 0) {
                lengths[i] = (float) Math.sqrt(Mth.square(points[i][0] - points[i - 1][0])
                        + Mth.square(points[i][1] - points[i - 1][1]) + Mth.square(points[i][2] - points[i - 1][2]));
                total += lengths[i];
            }
        }

        float blockPixels = scale * guiScale;
        // Les ondes sont espacées de 0,9 bloc et avancent d'un peu moins d'un bloc par seconde.
        float wavelength = 0.9F;
        float travelled = age * 0.045F;
        // À son apparition, le tube se déroule depuis la source en une demi-seconde environ.
        float revealed = sinceStart == Float.MAX_VALUE ? total : total * Mth.clamp(sinceStart / 12.0F, 0.0F, 1.0F);

        int core = Math.max(2, Math.round(blockPixels * 0.07F));

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.scale(1.0F / guiScale, 1.0F / guiScale);

        float walked = 0.0F;
        for (int i = 0; i + 1 < points.length; i++) {
            float length = lengths[i + 1];
            if (length < 1.0E-4F || walked > revealed) {
                break;
            }
            float dx = px[i + 1] - px[i];
            float dy = py[i + 1] - py[i];
            float screenLength = (float) Math.hypot(dx, dy);

            // On se place sur l'axe du tronçon : x le long du tube, y en travers.
            pose.pushMatrix();
            pose.translate(px[i], py[i]);
            pose.rotate((float) Math.atan2(dy, dx));

            // Bout du tube au départ du tronçon : c'est lui qu'on voit quand le tube pointe vers la caméra.
            extractFlowSlice(graphics, flow.color(), -core, 0, core, flowWave(walked, travelled, wavelength));

            // Une tranche par pixel de longueur, chacune avec la luminosité de l'onde à cet endroit de la scène.
            int slices = Math.max(1, Mth.ceil(screenLength));
            for (int slice = 0; slice < slices; slice++) {
                float distance = walked + length * (slice + 0.5F) / slices;
                if (distance > revealed) {
                    break;
                }
                int x0 = Math.round(screenLength * slice / slices);
                int x1 = Math.max(x0 + 1, Math.round(screenLength * (slice + 1) / slices));
                extractFlowSlice(graphics, flow.color(), x0, x1, core, flowWave(distance, travelled, wavelength));
            }
            if (walked + length <= revealed) {
                int tip = Math.round(screenLength);
                extractFlowSlice(graphics, flow.color(), tip, tip + core, core, flowWave(walked + length, travelled, wavelength));
            }
            pose.popMatrix();
            walked += length;
        }
        pose.popMatrix();
    }

    /** Luminosité de l'onde (0 à 1) à une distance donnée le long du trajet : une crête douce par longueur d'onde. */
    private static float flowWave(float distance, float travelled, float wavelength) {
        float wave = 0.5F + 0.5F * Mth.cos((distance - travelled) / wavelength * Mth.TWO_PI);
        return wave * wave * wave;
    }

    /** Tranche du tube entre x0 et x1 : deux halos de plus en plus serrés, puis le cœur lumineux. */
    private static void extractFlowSlice(GuiGraphicsExtractor graphics, int color, int x0, int x1, int core, float wave) {
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        int rgb = color & 0xFFFFFF;
        graphics.fill(x0, -core * 3, x1, core * 3, ((int) (22 + 34 * wave) << 24) | rgb);
        graphics.fill(x0, -core * 2, x1, core * 2, ((int) (34 + 50 * wave) << 24) | rgb);
        graphics.fill(x0, -core, x1, core, 0xFF000000
                | (Mth.lerpInt(wave, (int) (red * 0.75F), 255) << 16)
                | (Mth.lerpInt(wave, (int) (green * 0.75F), 255) << 8)
                | Mth.lerpInt(wave, (int) (blue * 0.75F), 255));
    }

    // ------------------------------------------------------------------
    // Désignation d'un bloc de la démonstration
    // ------------------------------------------------------------------

    /** Volume occupé par une pièce dans la scène (un cube pour une machine, plus fin pour un conduit). */
    private AABB pieceBounds(GuideScene.Piece piece) {
        return pieceState(piece).getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).bounds().move(piece.x(), piece.y(), piece.z());
    }

    /**
     * Pièce visible sous la souris, ou {@code null}. On lance un rayon depuis le point cliqué, dans la direction
     * du regard, et on garde la pièce touchée la plus proche de la caméra.
     */
    private GuideScene.Piece pieceUnder(double mouseX, double mouseY) {
        if (scene == null || viewScale <= 0.0F) {
            return null;
        }
        float screenX = (float) (mouseX - viewOriginX) / viewScale;
        float screenY = (float) (mouseY - viewOriginY) / viewScale;
        Vector3f origin = GuideSceneRenderer.unproject(screenX, screenY, 0.0F, viewYaw, PITCH).add(viewCenterX, viewCenterY, viewCenterZ);
        // Direction qui va de la scène vers la caméra : la caméra regarde la scène d'en haut.
        Vector3f toCamera = GuideSceneRenderer.unproject(0.0F, 0.0F, 1.0F, viewYaw, PITCH);
        if (toCamera.y < 0.0F) {
            toCamera.negate();
        }

        GuideScene.Piece best = null;
        float bestDistance = -Float.MAX_VALUE;
        for (GuideScene.Piece piece : scene.pieces()) {
            if (!piece.visibleAt(sceneStep)) {
                continue;
            }
            AABB box = pieceBounds(piece);
            double[] min = {box.minX, box.minY, box.minZ};
            double[] max = {box.maxX, box.maxY, box.maxZ};
            float[] start = {origin.x, origin.y, origin.z};
            float[] direction = {toCamera.x, toCamera.y, toCamera.z};
            // Portion du rayon située dans le volume, axe par axe.
            double enter = -Double.MAX_VALUE;
            double exit = Double.MAX_VALUE;
            for (int axis = 0; axis < 3 && enter <= exit; axis++) {
                if (Math.abs(direction[axis]) < 1.0E-6F) {
                    if (start[axis] < min[axis] || start[axis] > max[axis]) {
                        enter = Double.MAX_VALUE;
                    }
                    continue;
                }
                double a = (min[axis] - start[axis]) / direction[axis];
                double b = (max[axis] - start[axis]) / direction[axis];
                enter = Math.max(enter, Math.min(a, b));
                exit = Math.min(exit, Math.max(a, b));
            }
            if (enter <= exit && exit > bestDistance) {
                bestDistance = (float) exit;
                best = piece;
            }
        }
        return best;
    }

    /** Étiquette au-dessus du bloc désigné : son icône et son nom. */
    private void extractSelection(GuiGraphicsExtractor graphics, int areaX, int areaY, int areaWidth, int areaHeight) {
        if (selected == null || !selected.visibleAt(sceneStep)) {
            return;
        }
        Block block = selected.block().get();
        Component name = block.getName();
        AABB box = pieceBounds(selected);
        Vector3f anchor = GuideSceneRenderer.project((float) box.getCenter().x - viewCenterX, (float) box.maxY - viewCenterY,
                (float) box.getCenter().z - viewCenterZ, viewYaw, PITCH);
        int anchorX = Math.round(viewOriginX + anchor.x * viewScale);
        int anchorY = Math.round(viewOriginY + anchor.y * viewScale);

        int labelWidth = font.width(name) + 30;
        int labelHeight = 20;
        int labelX = Mth.clamp(anchorX - labelWidth / 2, areaX + 2, Math.max(areaX + 2, areaX + areaWidth - labelWidth - 2));
        int labelY = Mth.clamp(anchorY - labelHeight - 8, areaY + 2, Math.max(areaY + 2, areaY + areaHeight - labelHeight - 2));

        // Trait qui relie l'étiquette au bloc.
        if (anchorY > labelY + labelHeight) {
            graphics.fill(anchorX, labelY + labelHeight, anchorX + 1, anchorY, Ui.TEXT);
        }
        Ui.card(graphics, labelX, labelY, labelWidth, labelHeight, true);
        graphics.item(new ItemStack(block), labelX + 3, labelY + 2);
        graphics.text(font, name, labelX + 23, labelY + 6, Ui.TEXT, false);
    }

    /** Le jeu continue pendant la lecture (les machines tournent). */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
