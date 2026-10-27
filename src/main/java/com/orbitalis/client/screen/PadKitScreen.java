package com.orbitalis.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.orbitalis.client.PadKitPlacement;
import com.orbitalis.core.network.PadKitPayload;
import com.orbitalis.phase1.spaceport.LaunchComplex;
import com.orbitalis.phase1.spaceport.PadPlan;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.joml.Matrix3x2fStack;

/**
 * Écran du kit de pas de tir. On y choisit le plan à poser : la taille du tablier et la tour (à escalier ou à
 * ascenseur). Le 3×3 est le pas de tir d'essai, qui n'a pas de tour. L'écran dit ce que ce pas de tir pourra lancer, et dresse la liste des blocs à réunir, avec ce que le
 * joueur a déjà sur lui. Le sens du carneau ne se choisit pas ici : le plan esquissé en jeu se tourne à la molette.
 * <p>
 * Ouvert sur une table déjà posée, il change son plan (en gardant son orientation). Sinon il arme le kit : le plan
 * s'esquisse en jeu, et se pose d'un clic sur le sol.
 */
public class PadKitScreen extends Screen {
    private static final int COLOR_ACCENT = Ui.ACCENT;
    private static final int COLOR_TEXT = Ui.TEXT;
    private static final int COLOR_MUTED = Ui.MUTED;
    private static final int COLOR_OK = Ui.OK;
    private static final int COLOR_BUTTON_ON = 0xFF2F5F8F;
    private static final int COLOR_CONFIRM = 0xFF2E7D4F;

    private static final int WIDTH = 392;
    private static final int HEIGHT = 240;
    /** Hauteur d'une ligne de la liste des blocs, et taille de leur icône par rapport à une icône d'inventaire. */
    private static final int MATERIAL_ROW = 10;
    private static final float ICON_SCALE = 0.7F;
    private static final int LINE = 12;

    /** Zone cliquable : son rectangle et ce qu'elle fait. */
    private record Zone(int x, int y, int width, int height, Runnable action) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private final BlockPos table;
    private final List<Zone> zones = new ArrayList<>();
    private int radius = 2;
    private boolean elevator;
    private Direction facing;
    private int left;
    private int top;

    /**
     * @param table    table dont on change le plan, ou {@code null} pour choisir le plan à poser
     * @param facing   sens du carneau proposé
     * @param radius   taille proposée
     * @param elevator tour proposée
     */
    public PadKitScreen(BlockPos table, Direction facing, int radius, boolean elevator) {
        super(Component.translatable("spaceport.orbitalis.kit.title"));
        this.table = table;
        this.radius = radius == 1 ? 1 : PadPlan.nearestSize(radius);
        this.elevator = elevator;
        this.facing = facing.getAxis().isHorizontal() ? facing : Direction.EAST;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
    }

    private PadPlan plan() {
        return PadPlan.forKit(radius, elevator, facing);
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
        graphics.text(font, title.copy().withStyle(ChatFormatting.BOLD), left + 12, top + 10, COLOR_TEXT, false);
        Ui.pillRight(graphics, font, (2 * radius + 1) + " × " + (2 * radius + 1), left + WIDTH - 12, top + 9, COLOR_ACCENT);
        Ui.hline(graphics, left + 1, top + 24, WIDTH - 2);

        // Colonne de gauche : les deux choix, puis ce que le pas de tir pourra lancer.
        int x = left + 12;
        int column = 166;
        int y = top + 32;
        heading(graphics, x, y, "spaceport.orbitalis.kit.size");
        Component[] sizes = new Component[PadPlan.KIT_SIZES.length];
        int chosenSize = 0;
        for (int k = 0; k < sizes.length; k++) {
            // Quatre tailles sur une barre étroite : « 11×11 » doit tenir, sans espaces.
            sizes[k] = Component.literal((2 * PadPlan.KIT_SIZES[k] + 1) + "×" + (2 * PadPlan.KIT_SIZES[k] + 1));
            chosenSize = PadPlan.KIT_SIZES[k] == radius ? k : chosenSize;
        }
        segmented(graphics, x, y + 12, column, sizes, chosenSize, mouseX, mouseY, k -> radius = PadPlan.KIT_SIZES[k]);
        y += 38;
        PadPlan plan = plan();
        heading(graphics, x, y, "spaceport.orbitalis.kit.tower");
        if (plan.compact()) {
            // Le pas de tir d'essai n'a pas de tour : rien à choisir.
            segmented(graphics, x, y + 12, column, new Component[] {Component.translatable("spaceport.orbitalis.kit.tower.none")}, 0,
                    mouseX, mouseY, k -> {
                    });
        } else {
            segmented(graphics, x, y + 12, column, new Component[] {Component.translatable("spaceport.orbitalis.kit.tower.stairs"),
                    Component.translatable("spaceport.orbitalis.kit.tower.elevator")}, elevator ? 1 : 0, mouseX, mouseY, k -> elevator = k == 1);
        }
        y += 40;

        heading(graphics, x, y, "spaceport.orbitalis.kit.capacity");
        y += 12;
        int tileW = (column - 4) / 2;
        tile(graphics, x, y, tileW, Component.translatable("spaceport.orbitalis.kit.tonnes",
                String.format(Locale.ROOT, "%,d", (int) (LaunchComplex.maxRocketMass(radius) / 1000)).replace(',', ' ')).getString(),
                "spaceport.orbitalis.kit.mass_short");
        tile(graphics, x + tileW + 4, y, tileW, Component.translatable("spaceport.orbitalis.pad.blocks",
                plan.compact() ? LaunchComplex.TEST_PAD_HEIGHT : LaunchComplex.maxRocketHeight(plan.towerLevels())).getString(),
                "spaceport.orbitalis.kit.height_short");
        tile(graphics, x, y + 30, tileW, Integer.toString(plan.towerLevels()), "spaceport.orbitalis.kit.levels_short");
        // La tour ajoute cinq rangées derrière le tablier ; le pas de tir d'essai n'en a pas.
        tile(graphics, x + tileW + 4, y + 30, tileW, (plan.deckSize() + (plan.compact() ? 0 : 5)) + " × " + plan.deckSize(),
                "spaceport.orbitalis.kit.footprint_short");
        y += 64;
        for (String line : Ui.lines(font, Component.translatable("spaceport.orbitalis.kit.use." + radius), column, 3)) {
            graphics.text(font, line, x, y, COLOR_MUTED, false);
            y += LINE - 2;
        }

        // Colonne de droite : les blocs à réunir, et ce que le joueur a déjà.
        int cardX = left + 188;
        int cardY = top + 30;
        int cardW = left + WIDTH - 10 - cardX;
        int cardH = top + HEIGHT - 34 - cardY;
        Ui.card(graphics, cardX, cardY, cardW, cardH, false);
        int listX = cardX + 6;
        int listWidth = cardW - 12;
        heading(graphics, listX, cardY + 6, "spaceport.orbitalis.kit.materials");
        Inventory inventory = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getInventory();
        Map<Block, Integer> materials = plan.materials();
        int total = 0;
        int gathered = 0;
        for (Map.Entry<Block, Integer> entry : materials.entrySet()) {
            int owned = inventory == null ? 0 : count(inventory, entry.getKey().asItem());
            total += entry.getValue();
            gathered += Math.min(owned, entry.getValue());
        }
        String progress = Component.translatable("spaceport.orbitalis.kit.progress", gathered, total).getString();
        graphics.text(font, progress, listX + listWidth - font.width(progress), cardY + 6, gathered >= total ? COLOR_OK : COLOR_TEXT, false);
        Ui.bar(graphics, listX, cardY + 17, listWidth, 2, total == 0 ? 0.0F : gathered / (float) total, gathered >= total ? COLOR_OK : COLOR_ACCENT);
        int listY = cardY + 24;
        int rowH = Math.min(MATERIAL_ROW, Math.max(8, (cardY + cardH - 4 - listY) / Math.max(1, materials.size())));
        int index = 0;
        for (Map.Entry<Block, Integer> entry : materials.entrySet()) {
            Item item = entry.getKey().asItem();
            int need = entry.getValue();
            int owned = inventory == null ? 0 : count(inventory, item);
            if (index++ % 2 == 1) {
                graphics.fill(listX - 2, listY - 1, listX + listWidth + 2, listY + rowH - 1, 0x0CFFFFFF);
            }
            // L'icône est réduite pour que toute la liste tienne à l'écran.
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(listX, listY - 2);
            pose.scale(ICON_SCALE, ICON_SCALE);
            graphics.item(new ItemStack(item), 0, 0);
            pose.popMatrix();
            boolean done = owned >= need;
            String amount = done ? "✓ " + need : owned + " / " + need;
            String name = Ui.fit(font, entry.getKey().getName().getString(), listWidth - 22 - font.width(amount));
            graphics.text(font, name, listX + 14, listY, done ? COLOR_TEXT : COLOR_MUTED, false);
            graphics.text(font, amount, listX + listWidth - font.width(amount), listY, done ? COLOR_OK : owned > 0 ? Ui.WARN : Ui.DIM, false);
            listY += rowH;
        }

        // En bas : l'orientation se règle en jeu, à la molette ; puis le bouton qui valide.
        if (table == null) {
            Ui.dot(graphics, left + 12, top + HEIGHT - 21, COLOR_ACCENT);
            graphics.text(font, Ui.fit(font, Component.translatable("spaceport.orbitalis.kit.rotate_hint").getString(), WIDTH - 190),
                    left + 21, top + HEIGHT - 22, COLOR_MUTED, false);
        }
        button(graphics, left + WIDTH - 160, top + HEIGHT - 26, 150, 18,
                Component.translatable(table == null ? "spaceport.orbitalis.kit.choose" : "spaceport.orbitalis.kit.confirm"), true,
                COLOR_CONFIRM, mouseX, mouseY, this::confirm);
    }

    /**
     * Sélecteur à segments : une barre, un segment par choix, celui qui est choisi plein.
     */
    private void segmented(GuiGraphicsExtractor graphics, int x, int y, int w, Component[] labels, int selected, int mouseX, int mouseY,
            java.util.function.IntConsumer pick) {
        Ui.round(graphics, x, y, w, 18, Ui.SURFACE);
        Ui.frame(graphics, x, y, w, 18, Ui.LINE);
        int segment = (w - 4) / labels.length;
        for (int k = 0; k < labels.length; k++) {
            int sx = x + 2 + k * segment;
            int index = k;
            Zone zone = new Zone(sx, y + 2, segment, 14, () -> pick.accept(index));
            zones.add(zone);
            boolean hovered = zone.contains(mouseX, mouseY);
            if (k == selected) {
                Ui.round(graphics, sx, y + 2, segment, 14, COLOR_ACCENT);
            } else if (hovered) {
                Ui.round(graphics, sx, y + 2, segment, 14, Ui.alpha(COLOR_ACCENT, 0.16F));
            }
            String text = Ui.fit(font, labels[k].getString(), segment - 6);
            graphics.text(font, text, sx + (segment - font.width(text)) / 2, y + 5, k == selected ? 0xFF05070A : hovered ? COLOR_TEXT : COLOR_MUTED, false);
        }
    }

    /** Une tuile de chiffre : la valeur en clair, son nom en petit dessous. */
    private void tile(GuiGraphicsExtractor graphics, int x, int y, int w, String value, String labelKey) {
        Ui.card(graphics, x, y, w, 26, false);
        graphics.text(font, Ui.fit(font, value, w - 10), x + 6, y + 5, COLOR_TEXT, false);
        graphics.text(font, Ui.fit(font, Component.translatable(labelKey).getString(), w - 10), x + 6, y + 15, COLOR_MUTED, false);
    }

    private static int count(Inventory inventory, Item item) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void confirm() {
        onClose();
        if (table == null) {
            PadKitPlacement.arm(radius, elevator, facing);
        } else {
            ClientPacketDistributor.sendToServer(new PadKitPayload(table, radius, elevator, facing));
        }
    }

    private void heading(GuiGraphicsExtractor graphics, int x, int y, String key) {
        // Titre de rubrique : petites capitales grises, comme sur tous les écrans du mod.
        Ui.heading(graphics, font, Component.translatable(key), x, y);
    }

    private void button(GuiGraphicsExtractor graphics, int x, int y, int buttonWidth, int buttonHeight, Component label, boolean selected,
            int mouseX, int mouseY, Runnable action) {
        button(graphics, x, y, buttonWidth, buttonHeight, label, selected, COLOR_BUTTON_ON, mouseX, mouseY, action);
    }

    /** Dessine un bouton et retient sa zone : le clic est traité dans {@link #mouseClicked}. */
    private void button(GuiGraphicsExtractor graphics, int x, int y, int buttonWidth, int buttonHeight, Component label, boolean selected,
            int selectedColor, int mouseX, int mouseY, Runnable action) {
        Zone zone = new Zone(x, y, buttonWidth, buttonHeight, action);
        zones.add(zone);
        boolean hovered = zone.contains(mouseX, mouseY);
        // Le choix en cours (et le bouton qui valide) est plein ; les autres ne sont qu'un contour.
        Ui.button(graphics, font, label, x, y, buttonWidth, buttonHeight, hovered, true, selected, selected ? COLOR_ACCENT : COLOR_MUTED);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (Zone zone : List.copyOf(zones)) {
            if (zone.contains(event.x(), event.y())) {
                zone.action().run();
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
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
