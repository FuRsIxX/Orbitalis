package com.orbitalis.client.screen;

import java.util.List;
import java.util.Locale;

import com.orbitalis.core.network.LaunchCommandPayload;
import com.orbitalis.core.network.LaunchStatusPayload;
import com.orbitalis.phase1.spaceport.LaunchStep;
import com.orbitalis.phase1.spaceport.LaunchTableBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Séquenceur de lancement, dans la cabine d'une fusée (touche de saut), ou sur le pupitre d'essai : la check-list
 * complète (voir {@link SequencePanel}), tour de table compris. Au centre de contrôle, la même liste est dans l'écran
 * lui-même.
 * <p>
 * En tête, l'horloge de mission en grand, la phase et le niveau d'aide de l'ordinateur de bord ; en pied, ce qu'il
 * faut savoir maintenant, et l'arrêt.
 */
public class SequenceScreen extends Screen implements BlockScreen {
    private static final int WIDTH = 330;
    private static final int HEADER = 46;
    private static final int FOOTER = 36;
    /** Intervalle entre deux demandes d'actualisation, en ticks : assez court pour suivre la montée en poussée. */
    private static final int REFRESH_TICKS = 4;

    private LaunchStatusPayload status;
    private final Screen parent;
    private SequencePanel panel;
    private int left;
    private int top;
    private int age;

    public SequenceScreen(LaunchStatusPayload status, Screen parent) {
        this(status, parent, false);
    }

    /** @param testConsole vrai si l'écran est celui d'un pupitre d'essai (il en porte le nom) */
    public SequenceScreen(LaunchStatusPayload status, Screen parent, boolean testConsole) {
        super(Component.translatable(testConsole ? "block.orbitalis.test_console" : "sequence.orbitalis.title"));
        this.status = status;
        this.parent = parent;
    }

    /** Reçoit un nouvel état du serveur, s'il concerne bien ce pas de tir. */
    public void update(LaunchStatusPayload newStatus) {
        if (newStatus.console().equals(status.console())) {
            status = newStatus;
            if (panel != null) {
                panel.update(newStatus);
            }
        }
    }

    /** Le serveur a refusé une étape : on dit pourquoi. */
    public void refuse(Component reason) {
        if (panel != null) {
            panel.refuse(reason);
        }
    }

    @Override
    protected void init() {
        if (panel == null) {
            panel = new SequencePanel(font, status, true);
        }
        left = (width - WIDTH) / 2;
        top = (height - panelHeight()) / 2;
    }

    @Override
    public void tick() {
        super.tick();
        panel.tick();
        if (++age % REFRESH_TICKS == 0) {
            ClientPacketDistributor.sendToServer(new LaunchCommandPayload(status.console(), LaunchCommandPayload.REFRESH));
        }
    }

    private LaunchTableBlockEntity.Phase phase() {
        LaunchTableBlockEntity.Phase[] phases = LaunchTableBlockEntity.Phase.values();
        return phases[Math.floorMod(status.phase(), phases.length)];
    }

    /** Largeur de la liste dans le panneau. */
    private static int listWidth() {
        return WIDTH - 12;
    }

    /**
     * Hauteur du panneau : celle de la liste, sans dépasser la fenêtre. Une fusée avancée a une longue séquence :
     * dans une petite fenêtre, la liste défile alors entre l'en-tête et le pied, qui restent toujours à l'écran.
     */
    private int panelHeight() {
        int full = HEADER + FOOTER + panel.fullHeight(listWidth());
        return Math.min(full, Math.max(HEADER + FOOTER + SequencePanel.ROW + 16, height - 8));
    }

    private int abortX() {
        return left + WIDTH - 10 - 96;
    }

    private int assistWidth() {
        return font.width(assistLabel().getString().toUpperCase(Locale.ROOT)) + 12;
    }

    private Component assistLabel() {
        return Component.translatable("sequence.orbitalis.assist.label", Component.translatable("sequence.orbitalis.assist." + Math.clamp(status.assist(), 0, 2)));
    }

    private boolean overAssist(double mouseX, double mouseY) {
        return Ui.inside(mouseX, mouseY, left + WIDTH - 10 - assistWidth(), top + 25, assistWidth(), 14);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        // La liste n'a pas la même longueur au sol et en vol : le panneau est recentré à chaque image.
        top = (this.height - panelHeight()) / 2;
        Ui.panel(graphics, left, top, WIDTH, panelHeight());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        LaunchTableBlockEntity.Phase phase = phase();
        // En-tête : le titre en petites capitales, l'horloge de mission en grand, la phase et l'aide à droite.
        Ui.heading(graphics, font, title, left + 12, top + 8);
        String clock = switch (phase) {
            case COUNTDOWN -> "T-00:%02d".formatted(Math.max(0, status.seconds()));
            case FLIGHT -> "T+%02d:%02d".formatted(status.seconds() / 60, status.seconds() % 60);
            default -> "T-00:00";
        };
        Ui.big(graphics, font, clock, left + 12, top + 21, 2.0F, phase == LaunchTableBlockEntity.Phase.IDLE ? Ui.TEXT
                : phase == LaunchTableBlockEntity.Phase.COUNTDOWN ? Ui.WARN : Ui.OK);
        String phaseName = Component.translatable("sequence.orbitalis.phase." + phase.name().toLowerCase(Locale.ROOT)).getString();
        Ui.pillRight(graphics, font, phaseName, left + WIDTH - 10, top + 7, phase == LaunchTableBlockEntity.Phase.IDLE ? Ui.ACCENT
                : phase == LaunchTableBlockEntity.Phase.COUNTDOWN ? Ui.WARN : Ui.OK);
        // Niveau d'aide de l'ordinateur de bord : un clic passe au suivant.
        Ui.button(graphics, font, assistLabel(), left + WIDTH - 10 - assistWidth(), top + 25, assistWidth(), 14, overAssist(mouseX, mouseY),
                phase == LaunchTableBlockEntity.Phase.IDLE, false, status.assist() > 0 ? Ui.OK : Ui.MUTED);
        Ui.hline(graphics, left + 1, top + HEADER - 2, WIDTH - 2);

        int footer = top + panelHeight() - FOOTER;
        panel.extract(graphics, left + 6, top + HEADER, listWidth(), footer - (top + HEADER), mouseX, mouseY);

        Ui.hline(graphics, left + 1, footer + 2, WIDTH - 2);
        int y = footer + 8;
        List<FormattedCharSequence> alert = font.split(panel.alert(), WIDTH - 130);
        for (int k = 0; k < Math.min(2, alert.size()); k++) {
            graphics.text(font, alert.get(k), left + 12, y, Ui.TEXT, false);
            y += 10;
        }
        // Arrêt : il coupe tout tant que la fusée est retenue.
        boolean canAbort = phase == LaunchTableBlockEntity.Phase.COUNTDOWN;
        Ui.button(graphics, font, Component.translatable(canAbort ? "sequence.orbitalis.abort" : "sequence.orbitalis.close"), abortX(), footer + 10, 96, 18,
                Ui.inside(mouseX, mouseY, abortX(), footer + 10, 96, 18), true, canAbort, canAbort ? Ui.BAD : Ui.MUTED);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        if (overAssist(mx, my)) {
            panel.send(LaunchStep.ASSIST);
            return true;
        }
        if (panel.click(mx, my)) {
            return true;
        }
        int by = top + panelHeight() - FOOTER + 10;
        if (Ui.inside(mx, my, abortX(), by, 96, 18)) {
            if (phase() == LaunchTableBlockEntity.Phase.COUNTDOWN) {
                panel.send(LaunchStep.ABORT);
            } else {
                onClose();
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** Entrée : l'étape à faire. */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if ((event.key() == 257 || event.key() == 335) && panel.runNext()) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return panel.scroll(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
