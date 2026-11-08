package com.orbitalis.client.dev;

import java.util.List;

import com.orbitalis.client.screen.Ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * Planche d'infobulles, pour l'aperçu du mod : l'infobulle de chaque objet, telle que le jeu la compose, dans une case
 * à côté des autres. On juge ainsi d'un coup d'œil ce qu'elles disent sans Maj.
 */
public class TooltipSheetScreen extends Screen {
    private static final int COLUMN = 200;

    private final List<ItemStack> stacks;

    public TooltipSheetScreen(List<ItemStack> stacks) {
        super(Component.literal("Infobulles"));
        this.stacks = stacks;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int perRow = Math.max(1, (width - 10) / (COLUMN + 6));
        int[] columnY = new int[perRow];
        java.util.Arrays.fill(columnY, 8);
        for (int i = 0; i < stacks.size(); i++) {
            int column = i % perRow;
            int x = 6 + column * (COLUMN + 6);
            int y = columnY[column];
            List<Component> lines = Screen.getTooltipFromItem(minecraft, stacks.get(i));
            int h = 6;
            for (Component line : lines) {
                h += font.split(line, COLUMN - 10).size() * 10;
            }
            Ui.card(graphics, x, y, COLUMN, h + 2, true);
            int lineY = y + 5;
            for (Component line : lines) {
                for (FormattedCharSequence piece : font.split(line, COLUMN - 10)) {
                    graphics.text(font, piece, x + 5, lineY, 0xFFFFFFFF, false);
                    lineY += 10;
                }
            }
            columnY[column] = y + h + 8;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
