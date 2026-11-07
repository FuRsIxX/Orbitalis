package com.orbitalis.client.screen;

import com.orbitalis.core.network.ShipHelmPayload;
import com.orbitalis.core.network.ShipJumpPayload;
import com.orbitalis.core.network.ShipShuttlePayload;
import com.orbitalis.phase3.interstellar.ShipDestination;
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

    private static final int ARROW = 14;

    private final ShipHelmPayload ship;
    /** La destination choisie pour le prochain départ du berceau. */
    private int selected;

    public ShipHelmScreen(ShipHelmPayload ship) {
        super(Component.translatable("ship.orbitalis.helm.title"));
        this.ship = ship;
        this.selected = ship.destination();
    }

    /** Choisit la destination de ce rang (outil de capture). */
    public void choose(int destination) {
        selected = Math.floorMod(destination, ShipDestination.values().length);
    }

    private ShipJump.Place place() {
        return ShipJump.Place.values()[Math.clamp(ship.place(), 0, ShipJump.Place.values().length - 1)];
    }

    /** Au berceau, sans saut en cours, on choisit où aller. */
    private boolean choosing() {
        return place() == ShipJump.Place.HOME && ship.countdown() < 0 && ShipDestination.values().length > 1;
    }

    /** Ce qui empêche le saut : vers la destination choisie, au berceau ; sinon, pour rentrer. */
    private ShipJump.Hold hold() {
        int hold = place() == ShipJump.Place.HOME && ship.countdown() < 0 ? ship.holdFor(selected) : ship.hold();
        return ShipJump.Hold.values()[Math.clamp(hold, 0, ShipJump.Hold.values().length - 1)];
    }

    private int arrowY() {
        return top() + 46 + 6 * ROW + 9 + 13;
    }

    private int arrowX(boolean next) {
        return left() + WIDTH - PAD - (next ? ARROW : 2 * ARROW + 4);
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return (height - HEIGHT) / 2;
    }

    private boolean canJump() {
        return hold() == ShipJump.Hold.NONE && ship.countdown() < 0;
    }

    private int buttonX() {
        return left() + WIDTH - PAD - BUTTON_W;
    }

    /** La navette ne part que du vaisseau en orbite de Proxima b, hors d'un saut en cours. */
    private boolean shuttleShown() {
        return ship.place() == ShipJump.Place.AWAY.ordinal();
    }

    private boolean canShuttle() {
        return shuttleShown() && ship.countdown() < 0;
    }

    private int shuttleX() {
        return left() + PAD;
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
        ShipJump.Place place = place();
        String where = Component.translatable(place == ShipJump.Place.AWAY ? ShipDestination.byIndex(ship.destination()).placeKey()
                : "ship.orbitalis.helm.place." + place.name().toLowerCase(java.util.Locale.ROOT)).getString();
        Ui.pillRight(graphics, font, where, x + WIDTH - PAD, y + 8, place == ShipJump.Place.ELSEWHERE ? Ui.WARN : Ui.ACCENT);
        Ui.hline(graphics, x + 1, y + 25, WIDTH - 2);

        // Les systèmes, un voyant chacun.
        Ui.heading(graphics, font, Component.translatable("ship.orbitalis.helm.systems"), x + PAD, y + 32);
        int rowY = y + 46;
        system(graphics, "hull", ship.hullPlaced() >= ship.hullTotal(), ship.hullPlaced() + " / " + ship.hullTotal(), x, rowY, inner);
        ShipDestination wanted = ShipDestination.byIndex(place() == ShipJump.Place.HOME && ship.countdown() < 0 ? selected : ship.destination());
        system(graphics, "core", ship.core() && ship.charge() >= wanted.cost(),
                ship.core() ? ship.charge() + " / " + WarpCoreBlock.MAX_CHARGE : missing(), x, rowY + ROW, inner);
        system(graphics, "ring", ship.ringDrive(), !ship.ringDrive() ? missing() : ship.ringSpinning()
                ? Component.translatable("ship.orbitalis.helm.spinning").getString() : ok(), x, rowY + 2 * ROW, inner);
        system(graphics, "pods", ship.podsPlaced() >= ship.podsTotal(), ship.podsPlaced() + " / " + ship.podsTotal(), x, rowY + 3 * ROW, inner);
        system(graphics, "trays", ship.traysPlaced() >= ship.traysTotal(), ship.traysPlaced() + " / " + ship.traysTotal(), x, rowY + 4 * ROW, inner);
        system(graphics, "helm", ship.helmPlaced(), ship.helmPlaced() ? ok() : missing(), x, rowY + 5 * ROW, inner);
        Ui.hline(graphics, x + 1, rowY + 6 * ROW + 2, WIDTH - 2);

        // La destination du prochain saut, en grand.
        int destY = rowY + 6 * ROW + 9;
        Ui.heading(graphics, font, Component.translatable("ship.orbitalis.helm.destination"), x + PAD, destY);
        String destination = Component.translatable(place == ShipJump.Place.AWAY ? "ship.orbitalis.helm.dest.home" : wanted.nameKey()).getString();
        int nameRoom = choosing() ? inner - 2 * ARROW - 10 : inner;
        // Un nom trop long pour le grand corps (le Grand Nuage de Magellan) s'écrit un peu plus petit, plutôt que coupé.
        float nameScale = font.width(destination) * 1.5F <= nameRoom ? 1.5F : 1.25F;
        Ui.big(graphics, font, Ui.fit(font, destination, (int) (nameRoom / nameScale)), x + PAD, destY + 13 + (nameScale < 1.5F ? 1 : 0),
                nameScale, Ui.TEXT);
        if (choosing()) {
            for (boolean next : new boolean[] {false, true}) {
                boolean over = Ui.inside(mouseX, mouseY, arrowX(next), arrowY(), ARROW, ARROW);
                Ui.button(graphics, font, Component.literal(next ? ">" : "<"), arrowX(next), arrowY(), ARROW, ARROW, over, true, false, Ui.ACCENT);
            }
        }

        // Le verdict, en une phrase : prêt (et ce que coûte le saut), en cours, ou ce qui l'empêche.
        int sayY = destY + 32;
        Component say;
        int color;
        if (ship.countdown() >= 0) {
            say = Component.translatable("ship.orbitalis.helm.counting", (ship.countdown() + 19) / 20);
            color = Ui.ACCENT;
        } else if (canJump()) {
            say = Component.translatable("ship.orbitalis.helm.ready", wanted.cost(), ship.charge());
            color = Ui.OK;
        } else {
            say = Component.translatable(hold().key(), ship.charge(), wanted.cost(), Component.translatable(wanted.nameKey()));
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
        if (shuttleShown()) {
            boolean shuttleHovered = Ui.inside(mouseX, mouseY, shuttleX(), buttonY(), BUTTON_W, BUTTON_H);
            Ui.button(graphics, font, Component.translatable("ship.orbitalis.helm.shuttle"), shuttleX(), buttonY(), BUTTON_W, BUTTON_H,
                    shuttleHovered && canShuttle(), canShuttle(), false, Ui.ACCENT);
        }
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
            ClientPacketDistributor.sendToServer(new ShipJumpPayload(ship.helm(), selected));
            Ui.click();
            onClose();
            return true;
        }
        if (event.button() == 0 && choosing()) {
            for (boolean next : new boolean[] {false, true}) {
                if (Ui.inside(event.x(), event.y(), arrowX(next), arrowY(), ARROW, ARROW)) {
                    selected = Math.floorMod(selected + (next ? 1 : -1), ShipDestination.values().length);
                    Ui.click();
                    return true;
                }
            }
        }
        if (event.button() == 0 && canShuttle() && Ui.inside(event.x(), event.y(), shuttleX(), buttonY(), BUTTON_W, BUTTON_H)) {
            ClientPacketDistributor.sendToServer(new ShipShuttlePayload(ship.helm()));
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
