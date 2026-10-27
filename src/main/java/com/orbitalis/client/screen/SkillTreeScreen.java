package com.orbitalis.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.joml.Matrix3x2fStack;

import com.orbitalis.client.research.ClientResearch;
import com.orbitalis.core.network.UnlockResearchPayload;
import com.orbitalis.core.research.AgencyResearch;
import com.orbitalis.core.research.EngineeringStudies;
import com.orbitalis.core.research.ResearchGoals;
import com.orbitalis.core.research.ResearchNode;
import com.orbitalis.core.research.ResearchNodes;
import com.orbitalis.core.research.ResearchTree;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * L'arbre de compétences de l'agence : toute sa recherche, sur un seul écran. Il s'ouvre d'une touche (K au départ,
 * réglable), ou depuis un terminal de recherche.
 * <p>
 * Chaque domaine a sa ligne : propulsion, structures, avionique, sol, satellites, équipage, stations, exploration,
 * propulsion avancée. Sur une ligne, les technologies vont de gauche à droite, dans l'ordre de la progression, et un
 * trait relie chacune à celle dont elle dépend. Une case verte est acquise, une case bleue peut être recherchée, une
 * case grise attend un prérequis. On fait glisser la vue pour la parcourir, la molette la fait défiler.
 * <p>
 * À droite, la fiche de la technologie choisie : son coût, ce qu'elle apporte, ses prérequis (ceux d'un autre domaine
 * sont reliés à elle par un trait clair tant qu'elle est choisie), les études qu'elle ouvre à l'établi d'ingénieur,
 * et le bouton qui la recherche.
 * <p>
 * Un second onglet, « Objectifs », classe par palier tout ce qui rapporte des points de recherche, et montre ce qui
 * est déjà fait.
 */
public class SkillTreeScreen extends Screen {
    private static final int TOP = 40;
    private static final int LABEL_WIDTH = 116;
    private static final int DETAIL_WIDTH = 178;
    private static final int CELL_WIDTH = 78;
    private static final int CELL_HEIGHT = 48;
    private static final int NODE = 26;
    private static final int LANE_PAD = 9;
    private static final int GOAL_ROW = 15;

    /** Onglet affiché, et vue : conservés d'une ouverture à l'autre. */
    private static boolean goalsTab;
    private static int scrollX;
    private static int scrollY;
    private static int goalScroll;
    private static String selectedId = "";

    private boolean pressedInCanvas;
    private boolean dragged;

    public SkillTreeScreen() {
        super(Component.translatable("skilltree.orbitalis.title"));
    }

    /** Ouvre l'arbre sur une technologie, ou l'onglet des objectifs si elle est vide (sert aux captures de vérification). */
    public static SkillTreeScreen at(String node) {
        goalsTab = node.isEmpty();
        selectedId = node;
        scrollX = 0;
        scrollY = 0;
        goalScroll = 0;
        SkillTreeScreen screen = new SkillTreeScreen();
        return screen;
    }

    @Override
    protected void init() {
        clampScroll();
        // La technologie choisie vient à l'écran.
        ResearchNode node = ResearchNodes.get(selectedId);
        if (node != null && !goalsTab) {
            scrollY = Mth.clamp(laneTop(node.tree()) - 30, 0, maxScrollY());
            scrollX = Mth.clamp(node.column() * CELL_WIDTH - (canvasWidth() - LABEL_WIDTH) / 2, 0, maxScrollX());
        }
    }

    // ------------------------------------------------------------------
    // Mise en page
    // ------------------------------------------------------------------

    private int canvasWidth() {
        return width - DETAIL_WIDTH;
    }

    /** Nombre de lignes de cases d'un domaine. */
    private static int rows(ResearchTree tree) {
        int rows = 1;
        for (ResearchNode node : ResearchNodes.of(tree)) {
            rows = Math.max(rows, node.row() + 1);
        }
        return rows;
    }

    private static int laneHeight(ResearchTree tree) {
        return rows(tree) * CELL_HEIGHT + LANE_PAD;
    }

    /** Hauteur du haut d'un domaine, comptée depuis le haut de l'arbre. */
    private static int laneTop(ResearchTree tree) {
        int y = 0;
        for (ResearchTree other : ResearchTree.values()) {
            if (other == tree) {
                break;
            }
            y += laneHeight(other);
        }
        return y;
    }

    private static int contentHeight() {
        int y = 0;
        for (ResearchTree tree : ResearchTree.values()) {
            y += laneHeight(tree);
        }
        return y + 8;
    }

    private static int contentWidth() {
        int columns = 1;
        for (ResearchNode node : ResearchNodes.all()) {
            columns = Math.max(columns, node.column() + 1);
        }
        return columns * CELL_WIDTH + 24;
    }

    private int maxScrollX() {
        return Math.max(0, contentWidth() - (canvasWidth() - LABEL_WIDTH));
    }

    private int maxScrollY() {
        return Math.max(0, contentHeight() - (height - TOP));
    }

    private int maxGoalScroll() {
        return Math.max(0, goalsHeight() - (height - TOP - 12));
    }

    private void clampScroll() {
        scrollX = Mth.clamp(scrollX, 0, maxScrollX());
        scrollY = Mth.clamp(scrollY, 0, maxScrollY());
        goalScroll = Mth.clamp(goalScroll, 0, maxGoalScroll());
    }

    private int nodeX(ResearchNode node) {
        return LABEL_WIDTH + 14 + node.column() * CELL_WIDTH - scrollX;
    }

    private int nodeY(ResearchNode node) {
        return TOP + laneTop(node.tree()) + LANE_PAD + node.row() * CELL_HEIGHT - scrollY;
    }

    private ResearchNode selected() {
        return ResearchNodes.get(selectedId);
    }

    private ResearchNode nodeAt(double mouseX, double mouseY) {
        if (mouseX < LABEL_WIDTH || mouseX >= canvasWidth() || mouseY < TOP) {
            return null;
        }
        for (ResearchNode node : ResearchNodes.all()) {
            if (Ui.inside(mouseX, mouseY, nodeX(node) - 2, nodeY(node) - 2, NODE + 4, NODE + 4)) {
                return node;
            }
        }
        return null;
    }

    private static boolean reached(String goal) {
        return ClientResearch.get().unlocked().contains(ResearchGoals.SAVED_PREFIX + goal);
    }

    private int tabX(int tab) {
        // Le titre est en gras : c'est sa largeur en gras qui compte.
        int x = 14 + font.width(title.copy().withStyle(ChatFormatting.BOLD)) + 14;
        return tab == 0 ? x : x + tabWidth(0) + 4;
    }

    private int tabWidth(int tab) {
        return font.width(tabName(tab)) + 14;
    }

    private static String tabName(int tab) {
        return Component.translatable(tab == 0 ? "skilltree.orbitalis.tab.tree" : "skilltree.orbitalis.tab.goals").getString().toUpperCase(Locale.ROOT);
    }

    private int buttonY() {
        return height - 30;
    }

    private int detailLeft() {
        return width - DETAIL_WIDTH;
    }

    // ------------------------------------------------------------------
    // Souris
    // ------------------------------------------------------------------

    private void research(ResearchNode node) {
        if (node != null && ClientResearch.get().canUnlock(node.id()) == AgencyResearch.UnlockResult.SUCCESS) {
            ClientPacketDistributor.sendToServer(new UnlockResearchPayload(node.id()));
            Ui.click();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        for (int tab = 0; tab < 2; tab++) {
            if (Ui.inside(mouseX, mouseY, tabX(tab), 12, tabWidth(tab), 16)) {
                goalsTab = tab == 1;
                Ui.click();
                return true;
            }
        }
        if (goalsTab) {
            return super.mouseClicked(event, doubleClick);
        }
        if (Ui.inside(mouseX, mouseY, detailLeft() + 10, buttonY(), DETAIL_WIDTH - 20, 18)) {
            research(selected());
            return true;
        }
        if (mouseX < canvasWidth() && mouseY >= TOP && event.button() == 0) {
            ResearchNode node = nodeAt(mouseX, mouseY);
            if (node != null && doubleClick && node.id().equals(selectedId)) {
                research(node);
                return true;
            }
            pressedInCanvas = true;
            dragged = false;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (pressedInCanvas && event.button() == 0) {
            pressedInCanvas = false;
            if (!dragged) {
                ResearchNode node = nodeAt(event.x(), event.y());
                if (node != null) {
                    selectedId = node.id();
                    Ui.click();
                }
            }
            return true;
        }
        return super.mouseReleased(event);
    }

    /** Faire glisser la souris parcourt l'arbre. */
    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (pressedInCanvas) {
            scrollX -= (int) Math.round(dx);
            scrollY -= (int) Math.round(dy);
            dragged |= Math.abs(dx) + Math.abs(dy) > 0.5;
            clampScroll();
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
        if (goalsTab) {
            goalScroll -= (int) (scrollDeltaY * GOAL_ROW * 2);
        } else {
            scrollY -= (int) (scrollDeltaY * 24);
            scrollX -= (int) (scrollDeltaX * 24);
        }
        clampScroll();
        return true;
    }

    // ------------------------------------------------------------------
    // Dessin
    // ------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0xF405070A);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        clampScroll();
        AgencyResearch research = ClientResearch.get();
        if (goalsTab) {
            extractGoals(graphics);
        } else {
            extractTree(graphics, research, mouseX, mouseY);
            extractDetail(graphics, research, mouseX, mouseY);
        }
        extractTopBar(graphics, research, mouseX, mouseY);
    }

    /** Barre du haut : le titre, les deux onglets, l'avancement et les points de l'agence, en grand. */
    private void extractTopBar(GuiGraphicsExtractor graphics, AgencyResearch research, int mouseX, int mouseY) {
        graphics.fill(0, 0, width, TOP, 0xFF05070A);
        Ui.hline(graphics, 0, TOP - 1, width);
        graphics.text(font, title.copy().withStyle(ChatFormatting.BOLD), 14, 16, Ui.TEXT, false);
        for (int tab = 0; tab < 2; tab++) {
            boolean current = goalsTab == (tab == 1);
            int x = tabX(tab);
            boolean hovered = Ui.inside(mouseX, mouseY, x, 12, tabWidth(tab), 16);
            Ui.round(graphics, x, 12, tabWidth(tab), 16, current ? Ui.SURFACE_HI : hovered ? Ui.SURFACE : 0xFF05070A);
            Ui.frame(graphics, x, 12, tabWidth(tab), 16, current ? Ui.ACCENT : Ui.LINE);
            graphics.text(font, tabName(tab), x + 7, 16, current || hovered ? Ui.TEXT : Ui.MUTED, false);
        }
        String points = Integer.toString(research.points());
        int right = width - 14;
        String unit = Component.translatable("skilltree.orbitalis.points").getString().toUpperCase(Locale.ROOT);
        Ui.right(graphics, font, unit, right, 20, Ui.MUTED);
        right -= font.width(unit) + 6;
        Ui.big(graphics, font, points, right - font.width(points) * 2, 10, 2.0F, Ui.ACCENT);
        right -= font.width(points) * 2 + 14;
        int known = 0;
        int total = 0;
        for (ResearchNode node : ResearchNodes.all()) {
            total++;
            known += research.isUnlocked(node.id()) ? 1 : 0;
        }
        // L'avancement ne s'affiche que s'il a sa place entre les onglets et les points.
        String progress = Component.translatable("skilltree.orbitalis.progress", known, total).getString();
        if (right - Ui.pillWidth(font, progress) > tabX(1) + tabWidth(1) + 8) {
            Ui.pillRight(graphics, font, progress, right, 15, known == total ? Ui.OK : Ui.ACCENT);
        }
    }

    /** L'arbre : une ligne par domaine, ses cases, leurs liaisons. */
    private void extractTree(GuiGraphicsExtractor graphics, AgencyResearch research, int mouseX, int mouseY) {
        int canvas = canvasWidth();
        ResearchNode chosen = selected();
        ResearchNode hovered = nodeAt(mouseX, mouseY);
        graphics.enableScissor(LABEL_WIDTH, TOP, canvas, height);
        // Les lignes des domaines, une sur deux un rien plus claire.
        for (ResearchTree tree : ResearchTree.values()) {
            int y = TOP + laneTop(tree) - scrollY;
            if (tree.ordinal() % 2 == 0) {
                graphics.fill(LABEL_WIDTH, y, canvas, y + laneHeight(tree), 0x0AFFFFFF);
            }
        }
        // Les liaisons d'un même domaine, dessinées d'abord pour passer sous les cases.
        for (ResearchNode node : ResearchNodes.all()) {
            for (String id : node.prerequisites()) {
                ResearchNode before = ResearchNodes.get(id);
                if (before != null && before.tree() == node.tree()) {
                    int color = research.isUnlocked(node.id()) ? Ui.alpha(Ui.OK, 0.7F) : research.isUnlocked(id) ? Ui.LINE_HI : Ui.LINE;
                    link(graphics, before, node, color);
                }
            }
        }
        // La technologie choisie (ou survolée) montre tous ses prérequis, même ceux d'un autre domaine.
        for (ResearchNode focus : new ResearchNode[] {chosen, hovered}) {
            if (focus != null) {
                for (String id : focus.prerequisites()) {
                    ResearchNode before = ResearchNodes.get(id);
                    if (before != null) {
                        link(graphics, before, focus, research.isUnlocked(id) ? Ui.OK : Ui.WARN);
                    }
                }
            }
        }
        for (ResearchNode node : ResearchNodes.all()) {
            int x = nodeX(node);
            int y = nodeY(node);
            if (x > canvas || x + CELL_WIDTH < LABEL_WIDTH || y > height || y + CELL_HEIGHT < TOP) {
                continue;
            }
            boolean unlocked = research.isUnlocked(node.id());
            boolean available = !unlocked && research.hasPrerequisites(node);
            boolean affordable = available && research.points() >= node.cost();
            // Ce qu'on peut étudier tout de suite palpite doucement : on le repère d'un coup d'œil.
            float pulse = 0.55F + 0.45F * (float) Math.sin(net.minecraft.util.Util.getMillis() / 260.0);
            int border = unlocked ? Ui.OK : affordable ? Ui.alpha(Ui.ACCENT, pulse) : available ? Ui.alpha(Ui.ACCENT, 0.5F) : Ui.LINE_HI;
            int fill = unlocked ? Ui.alpha(Ui.OK, 0.16F) : available ? Ui.alpha(Ui.ACCENT, 0.12F) : Ui.SURFACE;
            // Une ombre, la case, son liseré ; un trait plus clair en haut, comme une touche.
            Ui.round(graphics, x + 1, y + 2, NODE, NODE, 0x80000000);
            Ui.round(graphics, x, y, NODE, NODE, 0xFF07090C);
            Ui.round(graphics, x, y, NODE, NODE, node == hovered && !unlocked ? Ui.SURFACE_HI : fill);
            graphics.fill(x + 2, y + 1, x + NODE - 2, y + 2, Ui.alpha(unlocked ? Ui.OK : Ui.TEXT, unlocked ? 0.45F : 0.12F));
            Ui.frame(graphics, x, y, NODE, NODE, border);
            if (node == chosen) {
                Ui.frame(graphics, x - 2, y - 2, NODE + 4, NODE + 4, Ui.TEXT);
            }
            graphics.item(new ItemStack(node.icon().get()), x + 5, y + 5);
            if (unlocked) {
                // Acquise : une coche verte dans le coin.
                Ui.round(graphics, x + NODE - 8, y - 3, 10, 10, Ui.OK);
                graphics.fill(x + NODE - 6, y + 2, x + NODE - 5, y + 4, 0xFF05070A);
                graphics.fill(x + NODE - 5, y + 3, x + NODE - 4, y + 5, 0xFF05070A);
                graphics.fill(x + NODE - 4, y + 1, x + NODE - 3, y + 4, 0xFF05070A);
                graphics.fill(x + NODE - 3, y, x + NODE - 2, y + 2, 0xFF05070A);
            }
            // Le nom, sous la case, et le coût s'il reste à étudier.
            String name = Ui.fit(font, Component.translatable(node.nameKey()).getString(), CELL_WIDTH - 8);
            graphics.text(font, name, x, y + NODE + 3, unlocked ? Ui.TEXT : available ? Ui.MUTED : Ui.DIM, false);
            if (!unlocked) {
                String cost = Component.translatable("skilltree.orbitalis.cost_short", node.cost()).getString();
                graphics.text(font, cost, x, y + NODE + 12, affordable ? Ui.ACCENT : available ? Ui.WARN : Ui.DIM, false);
            }
        }
        // Un voile sur les technologies hors de portée, par-dessus leurs icônes.
        graphics.nextStratum();
        for (ResearchNode node : ResearchNodes.all()) {
            if (!research.isUnlocked(node.id()) && !research.hasPrerequisites(node)) {
                graphics.fill(nodeX(node) + 1, nodeY(node) + 1, nodeX(node) + NODE - 1, nodeY(node) + NODE - 1, 0xA007090C);
            }
        }
        graphics.disableScissor();

        // Les noms des domaines, à gauche : ils ne défilent que de haut en bas.
        graphics.fill(0, TOP, LABEL_WIDTH, height, 0xFF05070A);
        graphics.fill(LABEL_WIDTH - 1, TOP, LABEL_WIDTH, height, Ui.LINE);
        graphics.enableScissor(0, TOP, LABEL_WIDTH, height);
        for (ResearchTree tree : ResearchTree.values()) {
            int y = TOP + laneTop(tree) - scrollY;
            List<ResearchNode> nodes = ResearchNodes.of(tree);
            int known = 0;
            for (ResearchNode node : nodes) {
                known += research.isUnlocked(node.id()) ? 1 : 0;
            }
            graphics.item(new ItemStack(tree.icon()), 10, y + LANE_PAD + 4);
            String name = Ui.fit(font, Component.translatable(tree.nameKey()).getString(), LABEL_WIDTH - 40);
            graphics.text(font, name, 32, y + LANE_PAD + 4, Ui.TEXT, false);
            graphics.text(font, known + " / " + nodes.size(), 32, y + LANE_PAD + 15, known == nodes.size() ? Ui.OK : Ui.MUTED, false);
            Ui.bar(graphics, 32, y + LANE_PAD + 26, LABEL_WIDTH - 46, 1, known / (float) Math.max(1, nodes.size()), known == nodes.size() ? Ui.OK : Ui.ACCENT);
        }
        graphics.disableScissor();
    }

    /** Liaison coudée entre une technologie et celle qui en dépend : à l'horizontale, à la verticale, à l'horizontale. */
    private void link(GuiGraphicsExtractor graphics, ResearchNode from, ResearchNode to, int color) {
        int fromX = nodeX(from) + NODE;
        int fromY = nodeY(from) + NODE / 2;
        int toX = nodeX(to);
        int toY = nodeY(to) + NODE / 2;
        if (toX <= fromX) {
            // Le prérequis n'est pas à gauche (autre domaine, même colonne) : on passe par-dessus la case.
            int midY = Math.min(fromY, toY) + Math.abs(toY - fromY) / 2;
            int x = nodeX(from) + NODE / 2;
            int tx = nodeX(to) + NODE / 2;
            graphics.fill(x, Math.min(fromY, midY), x + 1, Math.max(fromY, midY) + 1, color);
            graphics.fill(Math.min(x, tx), midY, Math.max(x, tx) + 1, midY + 1, color);
            graphics.fill(tx, Math.min(midY, toY), tx + 1, Math.max(midY, toY) + 1, color);
            return;
        }
        // Deux pixels d'épaisseur : les liaisons se lisent mieux.
        int middleX = toX - 8;
        graphics.fill(fromX, fromY, middleX + 2, fromY + 2, color);
        graphics.fill(middleX, Math.min(fromY, toY), middleX + 2, Math.max(fromY, toY) + 2, color);
        graphics.fill(middleX, toY, toX, toY + 2, color);
    }

    /** La fiche de la technologie choisie. */
    private void extractDetail(GuiGraphicsExtractor graphics, AgencyResearch research, int mouseX, int mouseY) {
        int x = detailLeft();
        graphics.fill(x, TOP, width, height, 0xFF080A0E);
        graphics.fill(x, TOP, x + 1, height, Ui.LINE);
        ResearchNode node = selected();
        int w = DETAIL_WIDTH - 20;
        int left = x + 10;
        if (node == null) {
            int y = TOP + 14;
            for (FormattedCharSequence line : font.split(Component.translatable("skilltree.orbitalis.hint"), w)) {
                graphics.text(font, line, left, y, Ui.MUTED, false);
                y += 10;
            }
            return;
        }
        boolean unlocked = research.isUnlocked(node.id());
        boolean available = !unlocked && research.hasPrerequisites(node);
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(left, TOP + 10);
        pose.scale(1.5F, 1.5F);
        graphics.item(new ItemStack(node.icon().get()), 0, 0);
        pose.popMatrix();
        graphics.text(font, Ui.fit(font, Component.translatable(node.tree().nameKey()).getString().toUpperCase(Locale.ROOT), w - 30), left + 30, TOP + 12,
                Ui.MUTED, false);
        int y = TOP + 24;
        List<FormattedCharSequence> name = font.split(Component.translatable(node.nameKey()).withStyle(ChatFormatting.BOLD), w - 30);
        for (int i = 0; i < Math.min(2, name.size()); i++) {
            graphics.text(font, name.get(i), left + 30, y, Ui.TEXT, false);
            y += 10;
        }
        y = Math.max(y, TOP + 40) + 4;
        // L'état, en une étiquette.
        String state;
        int stateColor;
        if (unlocked) {
            state = Component.translatable("skilltree.orbitalis.state.known").getString();
            stateColor = Ui.OK;
        } else if (!available) {
            state = Component.translatable("skilltree.orbitalis.state.locked").getString();
            stateColor = Ui.DIM;
        } else if (research.points() >= node.cost()) {
            state = Component.translatable("skilltree.orbitalis.state.cost", node.cost()).getString();
            stateColor = Ui.ACCENT;
        } else {
            state = Component.translatable("skilltree.orbitalis.state.short", node.cost() - research.points()).getString();
            stateColor = Ui.WARN;
        }
        Ui.pill(graphics, font, state, left, y, stateColor);
        y += 18;
        int bottom = buttonY() - 6;
        // La description : autant de lignes que la fiche en laisse, la dernière terminée proprement.
        for (String line : Ui.lines(font, Component.translatable(node.descriptionKey()), w, Math.max(1, (bottom - y - 60) / 10))) {
            graphics.text(font, line, left, y, 0xFFD5DAE0, false);
            y += 10;
        }
        y += 6;
        // Les prérequis, chacun avec sa pastille.
        if (!node.prerequisites().isEmpty() && y + 22 < bottom) {
            Ui.heading(graphics, font, Component.translatable("skilltree.orbitalis.requires"), left, y);
            y += 11;
            for (String id : node.prerequisites()) {
                ResearchNode before = ResearchNodes.get(id);
                if (before != null && y + 9 < bottom) {
                    boolean known = research.isUnlocked(id);
                    Ui.dot(graphics, left, y + 1, known ? Ui.OK : Ui.BAD);
                    graphics.text(font, Ui.fit(font, Component.translatable(before.nameKey()).getString(), w - 10), left + 9, y,
                            known ? Ui.TEXT : Ui.MUTED, false);
                    y += 10;
                }
            }
            y += 6;
        }
        // Ce que la technologie ouvre à l'établi d'ingénieur.
        List<EngineeringStudies.Study> opened = new ArrayList<>();
        for (EngineeringStudies.Study study : EngineeringStudies.all()) {
            if (node.id().equals(study.research())) {
                opened.add(study);
            }
        }
        if (!opened.isEmpty() && y + 22 < bottom) {
            Ui.heading(graphics, font, Component.translatable("skilltree.orbitalis.opens"), left, y);
            y += 11;
            for (EngineeringStudies.Study study : opened) {
                if (y + 9 >= bottom) {
                    break;
                }
                Ui.dot(graphics, left, y + 1, Ui.ACCENT);
                graphics.text(font, Ui.fit(font, study.name().getString(), w - 10), left + 9, y, Ui.TEXT, false);
                y += 10;
            }
        }
        boolean can = research.canUnlock(node.id()) == AgencyResearch.UnlockResult.SUCCESS;
        Component label = Component.translatable(unlocked ? "skilltree.orbitalis.state.known" : "research.orbitalis.research");
        Ui.button(graphics, font, label, left, buttonY(), w, 18, can && Ui.inside(mouseX, mouseY, left, buttonY(), w, 18), can, true, Ui.ACCENT);
    }

    // ------------------------------------------------------------------
    // Objectifs
    // ------------------------------------------------------------------

    private static int goalsHeight() {
        return ResearchGoals.all().size() * GOAL_ROW + ResearchGoals.Tier.values().length * 30 + 40;
    }

    /** Les objectifs, palier par palier : ce qui rapporte des points, et ce qui est déjà fait. */
    private void extractGoals(GuiGraphicsExtractor graphics) {
        int w = Math.min(460, width - 40);
        int x = (width - w) / 2;
        graphics.enableScissor(0, TOP, width, height);
        int y = TOP + 12 - goalScroll;
        for (FormattedCharSequence line : font.split(Component.translatable("goals.orbitalis.intro"), w)) {
            graphics.text(font, line, x, y, Ui.MUTED, false);
            y += 10;
        }
        y += 8;
        ResearchGoals.Tier current = null;
        for (ResearchGoals.Goal goal : ResearchGoals.all()) {
            if (goal.tier() != current) {
                current = goal.tier();
                int earned = 0;
                int total = 0;
                for (ResearchGoals.Goal other : ResearchGoals.all()) {
                    if (other.tier() == current) {
                        total += other.points();
                        earned += reached(other.id()) ? other.points() : 0;
                    }
                }
                y += 8;
                graphics.text(font, Component.translatable(current.nameKey()).copy().withStyle(ChatFormatting.BOLD), x, y, Ui.TEXT, false);
                Ui.right(graphics, font, earned + " / " + total, x + w, y, earned == total ? Ui.OK : Ui.MUTED);
                Ui.bar(graphics, x, y + 12, w, 1, earned / (float) Math.max(1, total), earned == total ? Ui.OK : Ui.ACCENT);
                y += 20;
            }
            boolean done = reached(goal.id());
            if (y + GOAL_ROW > TOP && y < height) {
                Ui.dot(graphics, x + 2, y + 4, done ? Ui.OK : Ui.LINE_HI);
                Matrix3x2fStack pose = graphics.pose();
                pose.pushMatrix();
                pose.translate(x + 12, y);
                pose.scale(0.75F, 0.75F);
                graphics.item(new ItemStack(goal.icon().get()), 0, 0);
                pose.popMatrix();
                String points = "+" + goal.points();
                Ui.right(graphics, font, points, x + w, y + 3, done ? Ui.OK : Ui.ACCENT);
                graphics.text(font, Ui.fit(font, goal.name().get().getString(), w - 36 - font.width(points)), x + 28, y + 3,
                        done ? Ui.MUTED : Ui.TEXT, false);
            }
            y += GOAL_ROW;
        }
        graphics.disableScissor();
        if (maxGoalScroll() > 0) {
            int track = height - TOP - 12;
            int thumb = Math.max(16, track * track / goalsHeight());
            graphics.fill(width - 5, TOP + 6 + (track - thumb) * goalScroll / maxGoalScroll(), width - 3,
                    TOP + 6 + (track - thumb) * goalScroll / maxGoalScroll() + thumb, Ui.LINE_HI);
        }
    }

    /** Le jeu continue pendant la consultation (les machines tournent). */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
