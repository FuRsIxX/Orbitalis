package com.orbitalis.client.screen;

import com.orbitalis.core.network.ShipHelmPayload;
import com.orbitalis.core.network.ShipJumpPayload;
import com.orbitalis.phase3.interstellar.ShipJump;
import com.orbitalis.phase3.interstellar.WarpCoreBlock;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Le poste de pilotage du vaisseau interstellaire : où il se trouve, le voyant de chacun de ses systèmes, la destination
 * du prochain saut, et en une phrase ce qui l'en empêche. Le bouton ordonne le saut ; le compte à rebours suit à bord.
 */
public class ShipHelmScreen extends Screen implements BlockScreen {
    private static final int WIDTH = 250;
    private static final int HEIGHT = 222;
    private static final int PAD = 12;
    private static final int ROW = 13;
    private static final int BUTTON_W = 110;
    private static final int BUTTON_H = 18;

    private final ShipHelmPayload ship;

    public ShipHelmScreen(ShipHelmPayload ship) {
        super(Component.translatable("ship.orbitalis.helm.title"));
        this.ship = ship;
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return (height - HEIGHT) / 2;
    }

    private boolean canJump() {
        return ship.hold() == ShipJump.Hold.NONE.ordinal() && ship.countdown() < 0;
    }

    private int buttonX() {
        return left() + WIDTH - PAD - BUTTON_W;
    }

    private int buttonY() {
        return top() + HEIGHT - PAD - BUTTON_H;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int x = left();
        int y = top();
        int inner = WIDTH - 2 * PAD;
        Ui.panel(graphics, x, y, WIDTH, HEIGHT);

        // En-tête : le titre, et où se trouve le vaisseau.
        graphics.text(font, title.copy().withStyle(ChatFormatting.BOLD), x + PAD, y + 10, Ui.TEXT, false);
        ShipJump.Place place = ShipJump.Place.values()[Math.clamp(ship.place(), 0, ShipJump.Place.values().length - 1)];
        String where = Component.translatable("ship.orbitalis.helm.place." + place.name().toLowerCase(java.util.Locale.ROOT)).getString();
        Ui.pillRight(graphics, font, where, x + WIDTH - PAD, y + 8, place == ShipJump.Place.ELSEWHERE ? Ui.WARN : Ui.ACCENT);
        Ui.hline(graphics, x + 1, y + 25, WIDTH - 2);

        // Les systèmes, un voyant chacun.
        Ui.heading(graphics, font, Component.translatable("ship.orbitalis.helm.systems"), x + PAD, y + 32);
        int rowY = y + 46;
        system(graphics, "hull", ship.hullPlaced() >= ship.hullTotal(), ship.hullPlaced() + " / " + ship.hullTotal(), x, rowY, inner);
        system(graphics, "core", ship.core() && ship.charge() >= ShipJump.JUMP_COST,
                ship.core() ? ship.charge() + " / " + WarpCoreBlock.MAX_CHARGE : missing(), x, rowY + ROW, inner);
        system(graphics, "ring", ship.ringDrive(), ship.ringDrive() ? ok() : missing(), x, rowY + 2 * ROW, inner);
        system(graphics, "pods", ship.podsPlaced() >= ship.podsTotal(), ship.podsPlaced() + " / " + ship.podsTotal(), x, rowY + 3 * ROW, inner);
        system(graphics, "trays", ship.traysPlaced() >= ship.traysTotal(), ship.traysPlaced() + " / " + ship.traysTotal(), x, rowY + 4 * ROW, inner);
        system(graphics, "helm", ship.helmPlaced(), ship.helmPlaced() ? ok() : missing(), x, rowY + 5 * ROW, inner);
        Ui.hline(graphics, x + 1, rowY + 6 * ROW + 2, WIDTH - 2);

        // La destination du prochain saut, en grand.
        int destY = rowY + 6 * ROW + 9;
        Ui.heading(graphics, font, Component.translatable("ship.orbitalis.helm.destination"), x + PAD, destY);
        String destination = Component.translatable(place == ShipJump.Place.AWAY ? "ship.orbitalis.helm.dest.home"
                : "ship.orbitalis.helm.dest.proxima").getString();
        Ui.big(graphics, font, Ui.fit(font, destination, (int) (inner / 1.5F)), x + PAD, destY + 13, 1.5F, Ui.TEXT);

        // Le verdict, en une phrase : prêt (et ce que coûte le saut), en cours, ou ce qui l'empêche.
        int sayY = destY + 32;
        Component say;
        int color;
        if (ship.countdown() >= 0) {
            say = Component.translatable("ship.orbitalis.helm.counting", (ship.countdown() + 19) / 20);
            color = Ui.ACCENT;
        } else if (canJump()) {
            say = Component.translatable("ship.orbitalis.helm.ready", ShipJump.JUMP_COST, ship.charge());
            color = Ui.OK;
        } else {
            ShipJump.Hold hold = ShipJump.Hold.values()[Math.clamp(ship.hold(), 0, ShipJump.Hold.values().length - 1)];
            say = Component.translatable(hold.key(), ship.charge(), ShipJump.JUMP_COST);
            color = Ui.WARN;
        }
        Ui.dot(graphics, x + PAD, sayY + 1, color);
        int lineY = sayY;
        for (String line : Ui.lines(font, say, inner - 10, 2)) {
            graphics.text(font, line, x + PAD + 9, lineY, Ui.TEXT, false);
            lineY += 10;
        }

        boolean hovered = Ui.inside(mouseX, mouseY, buttonX(), buttonY(), BUTTON_W, BUTTON_H);
        Ui.button(graphics, font, Component.translatable("ship.orbitalis.helm.jump"), buttonX(), buttonY(), BUTTON_W, BUTTON_H,
                hovered && canJump(), canJump(), true, Ui.ACCENT);
    }

    /** Une ligne de système : son voyant, son nom, et sa valeur à droite. */
    private void system(GuiGraphicsExtractor graphics, String key, boolean ready, String value, int x, int y, int inner) {
        Ui.dot(graphics, x + PAD, y + 1, ready ? Ui.OK : Ui.BAD);
        graphics.text(font, Ui.fit(font, Component.translatable("ship.orbitalis.helm.system." + key).getString(), inner - 80), x + PAD + 9, y,
                Ui.TEXT, false);
        Ui.right(graphics, font, value, x + PAD + inner, y, ready ? Ui.MUTED : Ui.BAD);
    }

    private static String ok() {
        return Component.translatable("ship.orbitalis.helm.ok").getString();
    }

    private static String missing() {
        return Component.translatable("ship.orbitalis.helm.missing").getString();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && canJump() && Ui.inside(event.x(), event.y(), buttonX(), buttonY(), BUTTON_W, BUTTON_H)) {
            ClientPacketDistributor.sendToServer(new ShipJumpPayload(ship.helm()));
            Ui.click();
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
