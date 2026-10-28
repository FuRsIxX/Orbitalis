package com.orbitalis.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.joml.Matrix3x2fStack;

import com.orbitalis.client.guide.GuideSceneRenderState;
import com.orbitalis.core.network.ShipyardActionPayload;
import com.orbitalis.phase3.interstellar.ShipPlan;
import com.orbitalis.phase3.interstellar.ShipyardConsoleBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Écran du chantier naval orbital, ouvert au pupitre de la salle de contrôle. En tête, l'état du chantier en un mot
 * et en une phrase. À gauche, le vaisseau tel qu'il est bâti, en 3D, qui tourne lentement. À droite, ses sections de
 * la poupe à la proue, chacune avec son avancement (le détail des blocs qui y manquent au survol), puis ce qu'il faut
 * réunir pour la section en cours, face à ce que le joueur a sur lui. Deux boutons posent, avec ce qu'il a sur lui, la
 * section en cours ou tout ce qu'il peut.
 * <p>
 * Tout se lit dans le monde, côté client : l'écran suit la construction à mesure, sans rien demander au serveur.
 */
public class ShipyardScreen extends Screen implements BlockScreen {
    private static final int WIDTH = 420;
    private static final int HEIGHT = 236;
    private static final int LINE = 11;
    private static final int ROW = 13;
    private static final int SECTION_ROW = 17;
    private static final float PITCH = 24.0F;
    private static final long TURN_MILLIS = 30_000L;
    private static final int COLOR_GRID = 0xFF131A22;

    private record Zone(int x, int y, int width, int height, Runnable action) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private final BlockPos consolePos;
    private final List<Zone> zones = new ArrayList<>();
    private List<FormattedCharSequence> tooltip;
    private int left;
    private int top;

    public ShipyardScreen(BlockPos console) {
        super(Component.translatable("shipyard.orbitalis.title"));
        this.consolePos = console;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
    }

    private ShipyardConsoleBlockEntity console() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && minecraft.level.getBlockEntity(consolePos) instanceof ShipyardConsoleBlockEntity console
                && console.linked() ? console : null;
    }

    @Override
    public void tick() {
        if (console() == null) {
            onClose();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        Ui.panel(graphics, left, top, WIDTH, HEIGHT);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        zones.clear();
        tooltip = null;
        ShipyardConsoleBlockEntity console = console();
        if (console == null) {
            return;
        }
        Map<ShipPlan.Section, ShipyardConsoleBlockEntity.Progress> progress = console.progress();
        int done = 0;
        int total = 0;
        for (ShipyardConsoleBlockEntity.Progress section : progress.values()) {
            done += section.placed();
            total += section.total();
        }
        ShipPlan.Section current = console.currentSection();

        // L'état, en un mot puis en une phrase.
        graphics.text(font, Ui.fit(font, title.getString(), WIDTH - 130), left + 12, top + 9, Ui.TEXT, false);
        String state = Component.translatable(current == null ? "shipyard.orbitalis.state.done" : done == 0
                ? "shipyard.orbitalis.state.empty" : "shipyard.orbitalis.state.building").getString();
        Ui.pillRight(graphics, font, state, left + WIDTH - 12, top + 8, current == null ? Ui.OK : done == 0 ? Ui.ACCENT : Ui.WARN);
        Component sentence = current == null ? Component.translatable("shipyard.orbitalis.sentence.done", total)
                : Component.translatable("shipyard.orbitalis.sentence.building", Component.translatable(current.nameKey()),
                        progress.get(current).total() - progress.get(current).placed());
        graphics.text(font, Ui.fit(font, sentence.getString(), WIDTH - 24), left + 12, top + 25, current == null ? Ui.OK : Ui.MUTED, false);
        Ui.hline(graphics, left + 1, top + 37, WIDTH - 2);

        extractShip(graphics, console, left + 8, top + 42, 150, HEIGHT - 50);
        extractSheet(graphics, console, progress, current, left + 166, top + 42, WIDTH - 174, mouseX, mouseY, done, total);
        if (tooltip != null) {
            graphics.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
        }
    }

    /** Le vaisseau tel qu'il est bâti : seuls les blocs en place sont dessinés. */
    private void extractShip(GuiGraphicsExtractor graphics, ShipyardConsoleBlockEntity console, int x, int y, int areaWidth, int areaHeight) {
        Ui.card(graphics, x, y, areaWidth, areaHeight, false);
        for (int gx = x + 8; gx < x + areaWidth - 1; gx += 16) {
            graphics.fill(gx, y + 1, gx + 1, y + areaHeight - 1, COLOR_GRID);
        }
        for (int gy = y + 8; gy < y + areaHeight - 1; gy += 16) {
            graphics.fill(x + 1, gy, x + areaWidth - 1, gy + 1, COLOR_GRID);
        }
        // Les pièces dans le repère du monde, et les mêmes dans celui de l'aperçu (proue vers l'est), dans le même ordre.
        List<ShipPlan.Piece> world = console.pieces();
        List<ShipPlan.Piece> shown = ShipPlan.pieces(Direction.EAST);
        BlockPos origin = console.shipOrigin();
        List<GuideSceneRenderState.PlacedBlock> blocks = new ArrayList<>();
        for (int i = 0; i < world.size(); i++) {
            ShipPlan.Piece piece = world.get(i);
            if (minecraft.level.getBlockState(origin.offset(piece.offset())).is(piece.state().getBlock())) {
                ShipPlan.Piece display = shown.get(i);
                blocks.add(new GuideSceneRenderState.PlacedBlock(display.state(), display.offset().getX(), display.offset().getY(),
                        display.offset().getZ()));
            }
        }
        if (blocks.isEmpty()) {
            List<String> lines = Ui.lines(font, Component.translatable("shipyard.orbitalis.empty"), areaWidth - 16, 3);
            int lineY = y + areaHeight / 2 - lines.size() * 5;
            for (String line : lines) {
                graphics.text(font, line, x + (areaWidth - font.width(line)) / 2, lineY, Ui.MUTED, false);
                lineY += 10;
            }
            return;
        }
        float yaw = (Util.getMillis() % TURN_MILLIS) * 360.0F / TURN_MILLIS;
        float scale = Math.min((areaWidth - 12) / (ShipPlan.LENGTH + 1.0F), (areaHeight - 12) / (2.0F * ShipPlan.RING_RADIUS + 6.0F));
        graphics.submitPictureInPictureRenderState(new GuideSceneRenderState(blocks, yaw, PITCH, ShipPlan.LENGTH / 2.0F, 0.5F, 0.5F,
                x, y, x + areaWidth, y + areaHeight, scale, graphics.peekScissorStack()));
        graphics.nextStratum();
    }

    private void extractSheet(GuiGraphicsExtractor graphics, ShipyardConsoleBlockEntity console,
            Map<ShipPlan.Section, ShipyardConsoleBlockEntity.Progress> progress, ShipPlan.Section current, int x, int y, int columnWidth,
            int mouseX, int mouseY, int done, int total) {
        // Les sections, de la poupe à la proue.
        Ui.heading(graphics, font, Component.translatable("shipyard.orbitalis.sections"), x, y);
        Ui.right(graphics, font, done + " / " + total, x + columnWidth, y, Ui.MUTED);
        y += LINE;
        for (Map.Entry<ShipPlan.Section, ShipyardConsoleBlockEntity.Progress> entry : progress.entrySet()) {
            ShipPlan.Section section = entry.getKey();
            ShipyardConsoleBlockEntity.Progress count = entry.getValue();
            boolean active = section == current;
            int color = count.done() ? Ui.OK : active ? Ui.ACCENT : Ui.DIM;
            boolean hovered = Ui.inside(mouseX, mouseY, x - 3, y - 2, columnWidth + 6, SECTION_ROW - 1);
            if (active || hovered) {
                Ui.card(graphics, x - 3, y - 2, columnWidth + 6, SECTION_ROW - 1, active);
            }
            Ui.dot(graphics, x, y + 2, color);
            String amount = count.placed() + " / " + count.total();
            graphics.text(font, Ui.fit(font, Component.translatable(section.nameKey()).getString(), columnWidth - 12 - font.width(amount) - 6),
                    x + 9, y + 1, active || count.done() ? Ui.TEXT : Ui.MUTED, false);
            Ui.right(graphics, font, amount, x + columnWidth, y + 1, color);
            Ui.bar(graphics, x + 9, y + 11, columnWidth - 9, 2, count.total() == 0 ? 0.0F : count.placed() / (float) count.total(), color);
            if (hovered) {
                tooltip = sectionDetail(console, section, count);
            }
            y += SECTION_ROW;
        }
        y += 3;

        // Ce qu'il faut encore réunir pour la section en cours, face à ce que le joueur a sur lui.
        int buttonY = top + HEIGHT - 24;
        if (current != null) {
            Ui.heading(graphics, font, Component.translatable("shipyard.orbitalis.gather", Component.translatable(current.nameKey())), x, y);
            y += LINE;
            Map<Item, Integer> needed = console.missing(current);
            int rows = Math.max(1, (buttonY - 4 - y) / ROW);
            int shown = 0;
            for (Map.Entry<Item, Integer> entry : needed.entrySet()) {
                if (shown == rows - 1 && needed.size() > rows) {
                    graphics.text(font, Ui.fit(font, Component.translatable("shipyard.orbitalis.more", needed.size() - shown).getString(), columnWidth),
                            x + 16, y + 2, Ui.MUTED, false);
                    break;
                }
                int need = entry.getValue();
                int have = count(entry.getKey());
                Matrix3x2fStack pose = graphics.pose();
                pose.pushMatrix();
                pose.translate(x, y);
                pose.scale(0.75F, 0.75F);
                graphics.item(new ItemStack(entry.getKey()), 0, 0);
                pose.popMatrix();
                String amount = Math.min(have, need) + " / " + need;
                Ui.right(graphics, font, amount, x + columnWidth, y + 2, have >= need ? Ui.OK : Ui.BAD);
                String name = Ui.fit(font, new ItemStack(entry.getKey()).getHoverName().getString(), columnWidth - 18 - font.width(amount) - 6);
                graphics.text(font, name, x + 16, y + 2, Ui.TEXT, false);
                y += ROW;
                shown++;
            }
        } else {
            for (String line : Ui.lines(font, Component.translatable("shipyard.orbitalis.next"), columnWidth, 3)) {
                graphics.text(font, line, x, y, Ui.MUTED, false);
                y += 10;
            }
        }

        // Les deux boutons de pose.
        int half = (columnWidth - 6) / 2;
        boolean enabled = current != null;
        button(graphics, x, buttonY, half, 16, Component.translatable("shipyard.orbitalis.build_section"), false, enabled, mouseX, mouseY,
                () -> send(ShipyardActionPayload.SECTION));
        button(graphics, x + half + 6, buttonY, half, 16, Component.translatable("shipyard.orbitalis.build_all"), true, enabled, mouseX, mouseY,
                () -> send(ShipyardActionPayload.ALL));
    }

    /** Le détail d'une section, au survol : ce qui y manque, bloc par bloc. */
    private List<FormattedCharSequence> sectionDetail(ShipyardConsoleBlockEntity console, ShipPlan.Section section,
            ShipyardConsoleBlockEntity.Progress count) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.translatable(section.nameKey()).getVisualOrderText());
        lines.add(Component.translatable("shipyard.orbitalis.section." + section.id() + ".desc").withStyle(ChatFormatting.GRAY).getVisualOrderText());
        if (count.done()) {
            lines.add(Component.translatable("shipyard.orbitalis.section_done").withStyle(ChatFormatting.GREEN).getVisualOrderText());
        } else {
            for (Map.Entry<Item, Integer> entry : console.missing(section).entrySet()) {
                lines.add(Component.translatable("shipyard.orbitalis.missing_line", entry.getValue(), new ItemStack(entry.getKey()).getHoverName())
                        .withStyle(ChatFormatting.GOLD).getVisualOrderText());
            }
        }
        return lines;
    }

    /** Ce que le joueur a de cet objet sur lui ; tout, en mode créatif. */
    private static int count(Item item) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return 0;
        }
        if (minecraft.player.getAbilities().instabuild) {
            return Integer.MAX_VALUE / 2;
        }
        Inventory inventory = minecraft.player.getInventory();
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(item)) {
                count += inventory.getItem(slot).getCount();
            }
        }
        return count;
    }

    private void send(int action) {
        ClientPacketDistributor.sendToServer(new ShipyardActionPayload(consolePos, action));
    }

    private void button(GuiGraphicsExtractor graphics, int x, int y, int w, int h, Component label, boolean main, boolean enabled, int mouseX,
            int mouseY, Runnable action) {
        Zone zone = new Zone(x, y, w, h, action);
        if (enabled) {
            zones.add(zone);
        }
        Ui.button(graphics, font, Component.literal(Ui.fit(font, label.getString(), w - 8)), x, y, w, h, enabled && zone.contains(mouseX, mouseY),
                enabled, main, main ? Ui.ACCENT : Ui.MUTED);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (Zone zone : List.copyOf(zones)) {
            if (zone.contains(event.x(), event.y())) {
                zone.action().run();
                Ui.click();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
