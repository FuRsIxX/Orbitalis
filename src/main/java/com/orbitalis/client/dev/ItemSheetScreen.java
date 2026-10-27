package com.orbitalis.client.dev;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

/**
 * Planche d'icônes, pour l'aperçu du mod : quelques objets dessinés comme dans une case d'inventaire, mais quatre
 * fois plus grands. Elle sert à juger de la miniature d'un bloc dans la barre d'objets.
 */
public class ItemSheetScreen extends Screen {
    private static final int SCALE = 4;
    private static final int CELL = 18 * SCALE;

    private final List<ItemStack> stacks;

    public ItemSheetScreen(List<ItemStack> stacks) {
        super(Component.literal("Icônes"));
        this.stacks = stacks;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int perRow = Math.max(1, (width - 20) / CELL);
        for (int i = 0; i < stacks.size(); i++) {
            int x = 10 + (i % perRow) * CELL;
            int y = 10 + (i / perRow) * CELL;
            // Une case d'inventaire : son fond gris, et l'objet au milieu.
            graphics.fill(x, y, x + CELL - 4, y + CELL - 4, 0xFF8B8B8B);
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(x + 2, y + 2);
            pose.scale(SCALE, SCALE);
            graphics.item(stacks.get(i), 0, 0);
            pose.popMatrix();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
