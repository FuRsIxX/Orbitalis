package com.orbitalis.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.orbitalis.phase2.survival.SpaceRules;
import com.orbitalis.phase2.survival.SpaceSuits;
import com.orbitalis.phase2.survival.SuitModule;
import com.orbitalis.phase2.survival.SuitWorkbenchMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * L'écran de l'établi de combinaison, dans le style des salles de contrôle d'Orbitalis.
 * <p>
 * À gauche, en haut, la combinaison : ses quatre pièces en colonne, chacune avec son nom et une pastille par logement de
 * module (allumée quand il est occupé), et la réserve d'oxygène du torse ; dessous, l'inventaire. À droite, sur toute
 * la hauteur, les modules rangés par pièce : pour chacun, son état en clair et le bouton qui le fixe ou le retire. Au
 * pied de la liste, ce que fait le module survolé (ou, sans survol, comment se servir de l'établi).
 */
public class SuitWorkbenchScreen extends AbstractContainerScreen<SuitWorkbenchMenu> {
    private static final int WIDTH = 420;
    private static final int HEIGHT = 248;
    /** La carte de la combinaison, en haut à gauche. */
    private static final int SUIT_X = 8;
    private static final int SUIT_Y = 30;
    private static final int SUIT_W = 172;
    private static final int SUIT_H = 120;
    /** La carte des modules, à droite, sur toute la hauteur. */
    private static final int MODULES_X = 186;
    private static final int MODULES_W = 226;
    private static final int MODULES_H = 210;
    /** Hauteur d'une ligne de module, d'un titre de pièce, et taille du bouton. */
    private static final int ROW = 19;
    private static final int GROUP = 11;
    private static final int BUTTON_W = 50;
    private static final int BUTTON_H = 13;
    /** Le pied de la liste des modules : sa place dans la carte. */
    private static final int FOOTER = 169;
    /** Les pièces qui reçoivent des modules, dans l'ordre de la liste. */
    private static final EquipmentSlot[] FITTED = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS};

    /** Les boutons dessinés à cette image : leur place, et le bouton du menu qu'ils pressent. */
    private record Button(int x, int y, int w, int h, int id) {
    }

    private final List<Button> buttons = new ArrayList<>();

    public SuitWorkbenchScreen(SuitWorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        inventoryLabelX = SuitWorkbenchMenu.INVENTORY_X;
        inventoryLabelY = SuitWorkbenchMenu.INVENTORY_Y - 11;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        Ui.panel(graphics, x, y, WIDTH, HEIGHT);
        // L'en-tête : le nom de l'établi, ce qu'on y fait, et l'état de la combinaison posée.
        graphics.text(font, title, x + 10, y + 7, Ui.TEXT, false);
        graphics.text(font, Ui.fit(font, Component.translatable("suit.orbitalis.bench.subtitle").getString(), 280), x + 10, y + 17, Ui.MUTED, false);
        int count = 0;
        boolean advanced = true;
        for (EquipmentSlot slot : SpaceSuits.PIECES) {
            ItemStack piece = menu.piece(slot);
            if (!piece.isEmpty()) {
                count++;
                advanced &= SpaceSuits.isAdvanced(piece);
            }
        }
        String state;
        int stateColor;
        if (count == 0) {
            state = Component.translatable("suit.orbitalis.bench.empty").getString();
            stateColor = Ui.DIM;
        } else if (count == SpaceSuits.PIECES.length) {
            state = Component.translatable(advanced ? "suit.orbitalis.bench.complete" : "suit.orbitalis.bench.basic_suit").getString();
            stateColor = advanced ? Ui.OK : Ui.WARN;
        } else {
            state = Component.translatable("suit.orbitalis.bench.pieces", count).getString();
            stateColor = Ui.WARN;
        }
        Ui.pillRight(graphics, font, state.toUpperCase(Locale.ROOT), x + WIDTH - 10, y + 10, stateColor);
        Ui.hline(graphics, x + 8, y + 26, WIDTH - 16);

        extractSuit(graphics, x + SUIT_X, y + SUIT_Y);
        buttons.clear();
        extractModules(graphics, x + MODULES_X, y + SUIT_Y, mouseX, mouseY);

        // Les cases de l'inventaire.
        for (Slot slot : menu.slots) {
            if (slot.index >= SpaceSuits.PIECES.length) {
                slotFrame(graphics, x + slot.x, y + slot.y, false);
            }
        }
    }

    /** Une case : un carré sombre, un contour fin ; plus clair pour les pièces de la combinaison. */
    private static void slotFrame(GuiGraphicsExtractor graphics, int x, int y, boolean piece) {
        Ui.round(graphics, x - 1, y - 1, 18, 18, piece ? Ui.SURFACE_HI : Ui.SURFACE);
        Ui.frame(graphics, x - 1, y - 1, 18, 18, piece ? Ui.LINE_HI : Ui.LINE);
    }

    /**
     * La combinaison : une ligne par pièce (sa case, son nom, ses logements de modules) et, à droite, la réserve
     * d'oxygène du torse.
     */
    private void extractSuit(GuiGraphicsExtractor graphics, int x, int y) {
        Ui.card(graphics, x, y, SUIT_W, SUIT_H, false);
        Ui.heading(graphics, font, Component.translatable("suit.orbitalis.bench.suit"), x + 7, y + 6);
        int textX = leftPos + SuitWorkbenchMenu.PIECE_X + 21;
        int textW = 104 - (textX - x);
        for (int i = 0; i < SpaceSuits.PIECES.length; i++) {
            EquipmentSlot slot = SpaceSuits.PIECES[i];
            int sy = topPos + SuitWorkbenchMenu.PIECE_Y + i * SuitWorkbenchMenu.PIECE_STEP;
            slotFrame(graphics, leftPos + SuitWorkbenchMenu.PIECE_X, sy, true);
            ItemStack piece = menu.piece(slot);
            graphics.text(font, Ui.fit(font, piece(slot), textW), textX, sy, piece.isEmpty() ? Ui.DIM : Ui.TEXT, false);
            // Sous le nom : une pastille par module que la pièce peut recevoir, ou ce qui l'en empêche.
            int dotX = textX;
            boolean any = false;
            for (SuitModule module : SuitModule.values()) {
                if (module.slot() != slot) {
                    continue;
                }
                any = true;
                if (!piece.isEmpty() && !SpaceSuits.isAdvanced(piece)) {
                    continue;
                }
                int color = piece.isEmpty() ? Ui.LINE_HI : SpaceSuits.has(piece, module) ? Ui.OK : Ui.alpha(Ui.MUTED, 0.6F);
                Ui.dot(graphics, dotX, sy + 10, color);
                dotX += 8;
            }
            if (!any) {
                graphics.text(font, "—", textX, sy + 9, Ui.DIM, false);
            } else if (!piece.isEmpty() && !SpaceSuits.isAdvanced(piece)) {
                graphics.text(font, Ui.fit(font, Component.translatable("suit.orbitalis.bench.state.basic").getString(), textW), textX, sy + 9,
                        Ui.WARN, false);
            }
        }
        // La réserve d'oxygène du torse posé : le temps qu'il reste, en grand, et sa jauge.
        int ox = x + 110;
        int ow = SUIT_W - 110 - 7;
        Ui.card(graphics, ox - 4, y + 18, ow + 8, SUIT_H - 25, true);
        graphics.text(font, Ui.fit(font, Component.translatable("suit.orbitalis.bench.oxygen").getString().toUpperCase(Locale.ROOT), ow), ox,
                y + 24, Ui.MUTED, false);
        ItemStack chest = menu.piece(EquipmentSlot.CHEST);
        if (chest.isEmpty()) {
            Ui.big(graphics, font, "--:--", ox, y + 40, 1.5F, Ui.DIM);
            Ui.bar(graphics, ox, y + 62, ow, 3, 0.0F, Ui.ACCENT);
        } else {
            int left = SpaceRules.oxygen(chest);
            float share = chest.getMaxDamage() > 0 ? left / (float) chest.getMaxDamage() : 0.0F;
            int color = share > 0.5F ? Ui.ACCENT : share > 0.2F ? Ui.WARN : Ui.BAD;
            Ui.big(graphics, font, time(left), ox, y + 40, 1.5F, color);
            Ui.bar(graphics, ox, y + 62, ow, 3, share, color);
            graphics.text(font, Ui.fit(font, "/ " + time(chest.getMaxDamage()), ow), ox, y + 69, Ui.DIM, false);
        }
    }

    private static String time(int seconds) {
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    /** Les modules, rangés par pièce, chacun avec son état et son bouton ; au pied, le détail du module survolé. */
    private void extractModules(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
        Ui.card(graphics, x, y, MODULES_W, MODULES_H, false);
        Ui.heading(graphics, font, Component.translatable("suit.orbitalis.bench.modules"), x + 7, y + 6);
        int rowY = y + 19;
        Inventory inventory = minecraft.player.getInventory();
        SuitModule hovered = null;
        Component hoveredState = null;
        int hoveredColor = Ui.MUTED;
        for (EquipmentSlot slot : FITTED) {
            ItemStack piece = menu.piece(slot);
            graphics.text(font, piece(slot).toUpperCase(Locale.ROOT), x + 7, rowY + 1, Ui.DIM, false);
            Ui.hline(graphics, x + 9 + font.width(piece(slot).toUpperCase(Locale.ROOT)), rowY + 4, MODULES_W - 16 - font.width(piece(slot).toUpperCase(Locale.ROOT)));
            rowY += GROUP;
            for (SuitModule module : SuitModule.values()) {
                if (module.slot() != slot) {
                    continue;
                }
                boolean installed = SpaceSuits.has(piece, module);
                boolean inStock = !SuitWorkbenchMenu.moduleIn(inventory, module).isEmpty();
                String state;
                int color;
                if (installed) {
                    state = "suit.orbitalis.bench.state.installed";
                    color = Ui.OK;
                } else if (piece.isEmpty()) {
                    state = "suit.orbitalis.bench.state.no_piece";
                    color = Ui.DIM;
                } else if (!SpaceSuits.isAdvanced(piece)) {
                    state = "suit.orbitalis.bench.state.basic";
                    color = Ui.WARN;
                } else if (!inStock) {
                    state = "suit.orbitalis.bench.state.missing";
                    color = Ui.MUTED;
                } else {
                    state = "suit.orbitalis.bench.state.ready";
                    color = Ui.ACCENT;
                }
                boolean over = Ui.inside(mouseX, mouseY, x + 3, rowY - 1, MODULES_W - 6, ROW);
                if (over) {
                    Ui.round(graphics, x + 3, rowY - 1, MODULES_W - 6, ROW, Ui.SURFACE_HI);
                    hovered = module;
                    hoveredState = Component.translatable(state);
                    hoveredColor = color;
                }
                graphics.item(new ItemStack(module.item()), x + 7, rowY);
                int textW = MODULES_W - 27 - BUTTON_W - 12;
                graphics.text(font, Ui.fit(font, Component.translatable(module.nameKey()).getString(), textW), x + 27, rowY, Ui.TEXT, false);
                Ui.dot(graphics, x + 27, rowY + 10, color);
                graphics.text(font, Ui.fit(font, Component.translatable(state).getString(), textW - 8), x + 35, rowY + 9, color, false);
                // Le bouton : retirer ce qui est en place, sinon fixer, si c'est possible.
                int bx = x + MODULES_W - BUTTON_W - 7;
                int by = rowY + 2;
                boolean onButton = Ui.inside(mouseX, mouseY, bx, by, BUTTON_W, BUTTON_H);
                if (installed) {
                    Ui.button(graphics, font, Component.translatable("suit.orbitalis.bench.remove"), bx, by, BUTTON_W, BUTTON_H, onButton, true, false, Ui.BAD);
                    buttons.add(new Button(bx, by, BUTTON_W, BUTTON_H, SuitWorkbenchMenu.REMOVE + module.ordinal()));
                } else {
                    boolean possible = SpaceSuits.isAdvanced(piece) && inStock;
                    Ui.button(graphics, font, Component.translatable("suit.orbitalis.bench.install"), bx, by, BUTTON_W, BUTTON_H, onButton && possible,
                            possible, possible, Ui.ACCENT);
                    if (possible) {
                        buttons.add(new Button(bx, by, BUTTON_W, BUTTON_H, module.ordinal()));
                    }
                }
                rowY += ROW;
            }
        }
        // Le pied : ce que fait le module survolé, ou comment se servir de l'établi.
        Ui.hline(graphics, x + 7, y + FOOTER - 3, MODULES_W - 14);
        Component detail = hovered == null ? Component.translatable("suit.orbitalis.bench.hint")
                : Component.translatable(hovered.nameKey() + ".desc");
        int lineY = y + FOOTER + 1;
        for (String line : Ui.lines(font, detail, MODULES_W - 14, 3)) {
            graphics.text(font, line, x + 7, lineY, hovered == null ? Ui.DIM : Ui.MUTED, false);
            lineY += 10;
        }
        if (hovered != null && hoveredState != null) {
            Ui.right(graphics, font, hoveredState, x + MODULES_W - 7, y + 6, hoveredColor);
        }
    }

    /** Le nom d'une pièce, avec sa majuscule. */
    private static String piece(EquipmentSlot slot) {
        String name = Component.translatable("suit.orbitalis.piece." + slot.getName()).getString();
        return name.isEmpty() ? name : name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Ui.heading(graphics, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        // Une case de pièce vide : ce qu'elle attend.
        for (int i = 0; i < SpaceSuits.PIECES.length; i++) {
            Slot slot = menu.slots.get(i);
            if (!slot.hasItem() && menu.getCarried().isEmpty()
                    && Ui.inside(mouseX, mouseY, leftPos + slot.x - 1, topPos + slot.y - 1, 18, 18)) {
                graphics.setTooltipForNextFrame(font, Component.literal(piece(SpaceSuits.PIECES[i])), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (Button button : buttons) {
            if (Ui.inside(event.x(), event.y(), button.x(), button.y(), button.w(), button.h())) {
                Ui.click();
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button.id());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
