package com.orbitalis.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.joml.Matrix3x2fStack;

import com.orbitalis.core.network.StudyPayload;
import com.orbitalis.core.research.EngineeringStudies;
import com.orbitalis.core.research.EngineeringStudies.Category;
import com.orbitalis.core.research.EngineeringStudies.Problem;
import com.orbitalis.core.research.EngineeringStudies.Study;
import com.orbitalis.core.research.ResearchManager;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Écran de l'établi d'ingénieur.
 * <p>
 * À gauche, les quatre rubriques : moteurs, réservoirs, charges utiles, modèles de lanceurs. Au milieu, les études
 * de la rubrique choisie, avec une pastille qui dit où en est chacune : faite, faisable maintenant, ou pas encore.
 * À droite, la fiche de l'étude choisie : la technologie qu'elle demande, les études à faire d'abord, les matériaux
 * de son prototype (ce que le joueur a sur lui, sur ce qu'il faut), et le bouton qui la lance.
 */
public class EngineerScreen extends Screen {
    private static final int HEADER = 30;
    private static final int TAB_WIDTH = 96;
    private static final int TAB_HEIGHT = 20;
    private static final int LIST_WIDTH = 150;
    private static final int ROW = 18;

    /** Rubrique et étude choisies ; conservées d'une ouverture à l'autre. */
    private static Category category = Category.ENGINES;
    private static String selectedId = "";

    private int panelWidth;
    private int panelHeight;
    private int left;
    private int top;
    private int scroll;

    public EngineerScreen() {
        super(Component.translatable("engineering.orbitalis.title"));
    }

    /** Ouvre l'écran sur une rubrique et une étude données (sert aux captures de vérification). */
    public static EngineerScreen at(int categoryIndex, String study) {
        category = Category.values()[Math.floorMod(categoryIndex, Category.values().length)];
        selectedId = study;
        return new EngineerScreen();
    }

    @Override
    protected void init() {
        panelWidth = Math.clamp(width - 8, 380, 430);
        panelHeight = Math.clamp(height - 8, 220, 256);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
    }

    // ------------------------------------------------------------------
    // État
    // ------------------------------------------------------------------

    private Player player() {
        return minecraft.player;
    }

    private List<Study> shown() {
        List<Study> list = new ArrayList<>();
        for (Study study : EngineeringStudies.all()) {
            if (study.category() == category) {
                list.add(study);
            }
        }
        return list;
    }

    private Study selected() {
        List<Study> list = shown();
        for (Study study : list) {
            if (study.id().equals(selectedId)) {
                return study;
            }
        }
        return list.isEmpty() ? null : list.get(0);
    }

    private boolean done(Study study) {
        return player() != null && EngineeringStudies.isRecorded(player(), study.id());
    }

    /** Ce qui empêche l'étude, pour le joueur ; une étude déjà faite le dit. */
    private Problem problem(Study study) {
        if (player() == null) {
            return Problem.UNKNOWN;
        }
        return done(study) ? Problem.ALREADY_DONE : EngineeringStudies.check(player(), study);
    }

    private static int color(Problem problem) {
        return switch (problem) {
            case ALREADY_DONE -> Ui.OK;
            case NONE -> Ui.ACCENT;
            case MISSING_MATERIALS -> Ui.WARN;
            default -> Ui.DIM;
        };
    }

    private int listLeft() {
        return left + 10 + TAB_WIDTH + 8;
    }

    private int listTop() {
        return top + HEADER + 8;
    }

    private int visibleRows() {
        return Math.max(1, (panelHeight - HEADER - 18) / ROW);
    }

    private int maxScroll() {
        return Math.max(0, shown().size() - visibleRows());
    }

    private int detailLeft() {
        return listLeft() + LIST_WIDTH + 8;
    }

    private int detailWidth() {
        return left + panelWidth - 10 - detailLeft();
    }

    private int buttonY() {
        return top + panelHeight - 30;
    }

    // ------------------------------------------------------------------
    // Souris
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        for (Category tab : Category.values()) {
            if (Ui.inside(mouseX, mouseY, left + 10, top + HEADER + 8 + tab.ordinal() * (TAB_HEIGHT + 2), TAB_WIDTH, TAB_HEIGHT)) {
                category = tab;
                scroll = 0;
                Ui.click();
                return true;
            }
        }
        List<Study> list = shown();
        for (int row = 0; row < visibleRows() && row + scroll < list.size(); row++) {
            if (Ui.inside(mouseX, mouseY, listLeft(), listTop() + row * ROW, LIST_WIDTH, ROW)) {
                selectedId = list.get(row + scroll).id();
                Ui.click();
                return true;
            }
        }
        Study study = selected();
        if (study != null && problem(study) == Problem.NONE && Ui.inside(mouseX, mouseY, detailLeft() + 8, buttonY(), detailWidth() - 16, 18)) {
            ClientPacketDistributor.sendToServer(new StudyPayload(study.id()));
            Ui.click();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
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
        graphics.text(font, title.copy().withStyle(ChatFormatting.BOLD), left + 12, top + 11, Ui.TEXT, false);
        // Les archives ne comptent pas dans la progression : chaque plan trouvé se valide à son tour.
        int total = 0;
        int made = 0;
        for (Study study : EngineeringStudies.all()) {
            if (!EngineeringStudies.isArchive(study)) {
                total++;
                made += done(study) ? 1 : 0;
            }
        }
        Ui.pillRight(graphics, font, Component.translatable("engineering.orbitalis.progress", made, total).getString(), left + panelWidth - 12, top + 10,
                made == total ? Ui.OK : Ui.ACCENT);
        Ui.hline(graphics, left + 1, top + HEADER, panelWidth - 2);

        extractTabs(graphics, mouseX, mouseY);
        extractList(graphics, mouseX, mouseY);
        extractDetail(graphics, mouseX, mouseY);
    }

    /** Les rubriques, avec le nombre d'études faites dans chacune. */
    private void extractTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (Category tab : Category.values()) {
            int x = left + 10;
            int y = top + HEADER + 8 + tab.ordinal() * (TAB_HEIGHT + 2);
            boolean current = tab == category;
            if (current) {
                Ui.round(graphics, x, y, TAB_WIDTH, TAB_HEIGHT, Ui.SURFACE_HI);
                graphics.fill(x, y + 3, x + 1, y + TAB_HEIGHT - 3, Ui.ACCENT);
            } else if (Ui.inside(mouseX, mouseY, x, y, TAB_WIDTH, TAB_HEIGHT)) {
                Ui.round(graphics, x, y, TAB_WIDTH, TAB_HEIGHT, Ui.SURFACE);
            }
            int made = 0;
            int total = 0;
            for (Study study : EngineeringStudies.all()) {
                if (study.category() == tab) {
                    total++;
                    made += done(study) ? 1 : 0;
                }
            }
            int held = tab == Category.ARCHIVES && player() != null ? EngineeringStudies.heldArchives(player()) : 0;
            String count = tab == Category.ARCHIVES ? String.valueOf(held) : made + "/" + total;
            Ui.right(graphics, font, count, x + TAB_WIDTH - 6, y + 6, tab == Category.ARCHIVES ? (held > 0 ? Ui.WARN : Ui.DIM)
                    : made == total ? Ui.OK : Ui.DIM);
            String name = Ui.fit(font, Component.translatable(tab.nameKey()).getString(), TAB_WIDTH - 16 - font.width(count));
            graphics.text(font, name, x + 7, y + 6, current ? Ui.TEXT : Ui.MUTED, false);
        }
    }

    /** Les études de la rubrique : une pastille dit où en est chacune. */
    private void extractList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = listLeft();
        List<Study> list = shown();
        Study chosen = selected();
        for (int row = 0; row < visibleRows() && row + scroll < list.size(); row++) {
            Study study = list.get(row + scroll);
            int y = listTop() + row * ROW;
            Problem problem = problem(study);
            if (study == chosen) {
                Ui.round(graphics, x, y, LIST_WIDTH, ROW - 1, Ui.SURFACE_HI);
                graphics.fill(x, y + 2, x + 1, y + ROW - 3, Ui.ACCENT);
            } else if (Ui.inside(mouseX, mouseY, x, y, LIST_WIDTH, ROW)) {
                Ui.round(graphics, x, y, LIST_WIDTH, ROW - 1, Ui.SURFACE);
            }
            graphics.item(new ItemStack(study.icon().get()), x + 4, y);
            Ui.dot(graphics, x + LIST_WIDTH - 10, y + 6, color(problem));
            String name = Ui.fit(font, study.name().getString(), LIST_WIDTH - 40);
            graphics.text(font, name, x + 24, y + 5, problem == Problem.ALREADY_DONE || problem == Problem.NONE || study == chosen ? Ui.TEXT : Ui.MUTED, false);
        }
        if (maxScroll() > 0) {
            int track = visibleRows() * ROW;
            int thumb = Math.max(10, track * visibleRows() / list.size());
            int thumbY = listTop() + (track - thumb) * scroll / maxScroll();
            graphics.fill(x + LIST_WIDTH + 2, thumbY, x + LIST_WIDTH + 3, thumbY + thumb, Ui.LINE_HI);
        }
    }

    /** La fiche de l'étude choisie : ce qu'elle demande, et le bouton qui la lance. */
    private void extractDetail(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = detailLeft();
        int y = top + HEADER + 8;
        int w = detailWidth();
        int h = top + panelHeight - 8 - y;
        Ui.card(graphics, x, y, w, h, false);
        Study study = selected();
        if (study == null || player() == null) {
            return;
        }
        Problem problem = problem(study);
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x + 8, y + 6);
        pose.scale(1.5F, 1.5F);
        graphics.item(new ItemStack(study.icon().get()), 0, 0);
        pose.popMatrix();
        var nameLines = font.split(study.name().copy().withStyle(ChatFormatting.BOLD), w - 46);
        for (int i = 0; i < Math.min(2, nameLines.size()); i++) {
            graphics.text(font, nameLines.get(i), x + 38, y + 8 + i * 10, Ui.TEXT, false);
        }
        String state = Component.translatable("engineering.orbitalis.state." + problem.name().toLowerCase(Locale.ROOT)).getString();
        Ui.pill(graphics, font, state, x + 8, y + 34, color(problem));

        int lineY = y + 52;
        int bottom = buttonY() - 4;
        boolean creative = player().getAbilities().instabuild;
        // La technologie.
        if (study.research() != null && !study.research().isEmpty()) {
            Ui.heading(graphics, font, Component.translatable("engineering.orbitalis.needs.research"), x + 8, lineY);
            lineY += 11;
            boolean known = creative || ResearchManager.isUnlocked(player(), study.research());
            lineY = requirement(graphics, x, lineY, w, Component.translatable("research.orbitalis.node." + study.research()), "", known);
            lineY += 4;
        }
        // Les technologies des pièces d'un plan d'archive, et le plan lui-même.
        if (!study.technologies().isEmpty() && lineY + 22 < bottom) {
            Ui.heading(graphics, font, Component.translatable("engineering.orbitalis.needs.technologies"), x + 8, lineY);
            lineY += 11;
            for (String technology : study.technologies()) {
                if (lineY + 10 < bottom) {
                    boolean known = creative || ResearchManager.isUnlocked(player(), technology);
                    lineY = requirement(graphics, x, lineY, w, Component.translatable("research.orbitalis.node." + technology), "", known);
                }
            }
            lineY += 4;
        }
        if (EngineeringStudies.isArchive(study) && lineY + 10 < bottom) {
            boolean held = !EngineeringStudies.heldArchive(player(), study.id().substring(EngineeringStudies.ARCHIVE_PREFIX.length())).isEmpty();
            lineY = requirement(graphics, x, lineY, w, Component.translatable("engineering.orbitalis.needs.archive"), held ? "1 / 1" : "0 / 1", held);
            lineY += 4;
        }
        // Les études à faire d'abord.
        if (!study.requires().isEmpty() && lineY + 22 < bottom) {
            Ui.heading(graphics, font, Component.translatable("engineering.orbitalis.needs.studies"), x + 8, lineY);
            lineY += 11;
            for (String required : study.requires()) {
                Study other = EngineeringStudies.get(required);
                if (other != null && lineY + 10 < bottom) {
                    boolean made = creative || done(other);
                    lineY = requirement(graphics, x, lineY, w, other.name(), "", made);
                }
            }
            lineY += 4;
        }
        // Les matériaux du prototype : ce que le joueur a sur lui, sur ce qu'il faut.
        if (lineY + 22 < bottom) {
            Ui.heading(graphics, font, Component.translatable("engineering.orbitalis.needs.materials"), x + 8, lineY);
            lineY += 11;
            for (EngineeringStudies.Cost cost : study.costs()) {
                if (lineY + 10 >= bottom) {
                    break;
                }
                int have = EngineeringStudies.count(player().getInventory(), cost.item().get());
                boolean enough = creative || have >= cost.count() || problem == Problem.ALREADY_DONE;
                lineY = requirement(graphics, x, lineY, w, new ItemStack(cost.item().get()).getHoverName(),
                        problem == Problem.ALREADY_DONE ? String.valueOf(cost.count()) : Math.min(have, 999) + " / " + cost.count(), enough);
            }
        }

        boolean ready = problem == Problem.NONE;
        Component label = Component.translatable(problem == Problem.ALREADY_DONE ? "engineering.orbitalis.state.already_done"
                : EngineeringStudies.isArchive(study) ? "engineering.orbitalis.validate" : "engineering.orbitalis.study");
        Ui.button(graphics, font, label, x + 8, buttonY(), w - 16, 18, ready && Ui.inside(mouseX, mouseY, x + 8, buttonY(), w - 16, 18), ready, true, Ui.ACCENT);
    }

    /** Une ligne de ce que l'étude demande : son nom, et où l'on en est. Renvoie la hauteur de la ligne suivante. */
    private int requirement(GuiGraphicsExtractor graphics, int x, int y, int w, Component name, String value, boolean met) {
        if (value.isEmpty()) {
            Ui.dot(graphics, x + w - 13, y + 1, met ? Ui.OK : Ui.BAD);
        } else {
            Ui.right(graphics, font, value, x + w - 8, y, met ? Ui.OK : Ui.BAD);
        }
        graphics.text(font, Ui.fit(font, name.getString(), w - 22 - font.width(value)), x + 8, y, met ? Ui.TEXT : Ui.MUTED, false);
        return y + 10;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
