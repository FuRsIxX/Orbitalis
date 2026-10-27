package com.orbitalis.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.orbitalis.core.network.ElevatorPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Panneau de commande de l'ascenseur de la tour : la liste des arrêts, du plus haut au plus bas comme sur un vrai
 * tableau d'ascenseur. Un clic envoie la cabine à l'arrêt choisi et referme le panneau.
 */
public class ElevatorScreen extends Screen {

    private static final int WIDTH = 170;
    private static final int ROW = 22;

    private record Zone(int x, int y, int width, int height, int stop) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private final ElevatorPayload panel;
    private final List<Zone> zones = new ArrayList<>();

    public ElevatorScreen(ElevatorPayload panel) {
        super(Component.translatable("spaceport.orbitalis.elevator.title"));
        this.panel = panel;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int count = panel.stops().size();
        int panelHeight = 38 + count * ROW;
        int left = (width - WIDTH) / 2;
        int top = (height - panelHeight) / 2;
        Ui.panel(graphics, left, top, WIDTH, panelHeight);
        graphics.text(font, title.copy().withStyle(ChatFormatting.BOLD), left + 12, top + 10, Ui.TEXT, false);
        Ui.hline(graphics, left + 1, top + 25, WIDTH - 2);

        // La gaine de l'ascenseur : un trait vertical, un repère par arrêt, et la cabine là où elle se trouve.
        zones.clear();
        int ground = panel.stops().isEmpty() ? 0 : panel.stops().get(0);
        int shaft = left + 18;
        graphics.fill(shaft, top + 32 + ROW / 2 - 4, shaft + 1, top + 32 + (count - 1) * ROW + ROW / 2 + 4, Ui.LINE_HI);
        for (int row = 0; row < count; row++) {
            int stop = count - 1 - row;
            int y = top + 32 + row * ROW;
            Zone zone = new Zone(left + 8, y, WIDTH - 16, ROW - 2, stop);
            zones.add(zone);
            boolean here = stop == panel.current();
            boolean hovered = zone.contains(mouseX, mouseY);
            if (here) {
                Ui.round(graphics, zone.x(), y, zone.width(), zone.height(), Ui.SURFACE_HI);
                Ui.frame(graphics, zone.x(), y, zone.width(), zone.height(), Ui.LINE_HI);
            } else if (hovered) {
                Ui.round(graphics, zone.x(), y, zone.width(), zone.height(), Ui.SURFACE);
                Ui.frame(graphics, zone.x(), y, zone.width(), zone.height(), Ui.ACCENT);
            }
            Ui.dot(graphics, shaft - 2, y + zone.height() / 2 - 2, here ? Ui.OK : hovered ? Ui.ACCENT : Ui.MUTED);
            Component label = stop == 0 ? Component.translatable("spaceport.orbitalis.elevator.ground")
                    : Component.translatable("spaceport.orbitalis.elevator.gate", stop);
            graphics.text(font, label, left + 30, y + (zone.height() - 8) / 2, here || hovered ? Ui.TEXT : Ui.MUTED, false);
            String height = "+" + (panel.stops().get(stop) - ground) + " m";
            Ui.right(graphics, font, height, zone.x() + zone.width() - 8, y + (zone.height() - 8) / 2, here ? Ui.OK : Ui.DIM);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (Zone zone : List.copyOf(zones)) {
            if (zone.contains(event.x(), event.y())) {
                ClientPacketDistributor.sendToServer(new ElevatorPayload(panel.cabin(), panel.stops(), panel.current(), zone.stop()));
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                onClose();
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
