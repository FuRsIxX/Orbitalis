package com.orbitalis.client.screen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.joml.Matrix3x2fStack;

import com.orbitalis.client.guide.GuideSceneRenderState;
import com.orbitalis.core.network.ConstructionActionPayload;
import com.orbitalis.core.network.ConstructionPayload;
import com.orbitalis.phase1.rocket.RocketBlueprintItem;
import com.orbitalis.phase1.rocket.RocketDesign;
import com.orbitalis.phase1.rocket.RocketPlan;
import com.orbitalis.phase1.spaceport.FuelPortBlock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Écran de chantier d'une fusée, ouvert par un clic droit sur la table d'un pas de tir formé où une fusée attend
 * d'être bâtie. À gauche, la fusée telle qu'elle est posée, en 3D, qui tourne lentement. À droite, l'avancement (blocs
 * posés, couche en cours), puis ce qu'il faut encore réunir, pièce par pièce, face à ce que le joueur a sur lui, les
 * raccords de ravitaillement qui manquent et la charge utile à bord. Deux boutons posent, avec ce que le joueur a sur
 * lui, la couche suivante ou tout ce qu'il peut.
 */
public class ConstructionScreen extends Screen implements BlockScreen {
    private static final int WIDTH = 420;
    private static final int HEIGHT = 236;
    private static final int LINE = 11;
    private static final int ROW = 13;
    private static final float PITCH = 18.0F;
    private static final long TURN_MILLIS = 20_000L;
    private static final int COLOR_GRID = 0xFF131A22;

    private record Zone(int x, int y, int width, int height, Runnable action) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private final List<Zone> zones = new ArrayList<>();
    private ConstructionPayload state;
    private List<RocketPlan.Piece> pieces;
    private RocketDesign design;
    private int left;
    private int top;

    public ConstructionScreen(ConstructionPayload state) {
        super(Component.translatable("construction.orbitalis.title"));
        update(state);
    }

    /** Nouvel état du chantier ; un plan vide ferme l'écran (fusée formée, ou plan repris). */
    public void update(ConstructionPayload payload) {
        if (payload.plan().isEmpty()) {
            onClose();
            return;
        }
        state = payload;
        design = RocketBlueprintItem.design(payload.plan());
        pieces = RocketPlan.pieces(design, Direction.from2DDataValue(payload.facing()));
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
    }

    private boolean placed(int index) {
        return index < state.placed().size() && state.placed().get(index);
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
        if (state == null) {
            return;
        }
        int done = 0;
        for (int i = 0; i < pieces.size(); i++) {
            done += placed(i) ? 1 : 0;
        }
        graphics.text(font, Ui.fit(font, title.getString() + " · " + state.plan().getHoverName().getString(), WIDTH - 110),
                left + 12, top + 9, Ui.TEXT, false);
        Ui.pillRight(graphics, font, done + " / " + pieces.size(), left + WIDTH - 12, top + 8, done == pieces.size() ? Ui.OK : Ui.ACCENT);
        Ui.hline(graphics, left + 1, top + 23, WIDTH - 2);
        extractRocket(graphics, left + 8, top + 28, 120, HEIGHT - 36);
        extractSheet(graphics, left + 136, top + 28, WIDTH - 144, mouseX, mouseY, done);
    }

    /** La fusée telle qu'elle est posée : seuls les blocs en place sont dessinés. */
    private void extractRocket(GuiGraphicsExtractor graphics, int x, int y, int areaWidth, int areaHeight) {
        Ui.card(graphics, x, y, areaWidth, areaHeight, false);
        for (int gx = x + 8; gx < x + areaWidth - 1; gx += 16) {
            graphics.fill(gx, y + 1, gx + 1, y + areaHeight - 1, COLOR_GRID);
        }
        for (int gy = y + 8; gy < y + areaHeight - 1; gy += 16) {
            graphics.fill(x + 1, gy, x + areaWidth - 1, gy + 1, COLOR_GRID);
        }
        int height = 1;
        boolean wide = false;
        List<GuideSceneRenderState.PlacedBlock> blocks = new ArrayList<>();
        for (int i = 0; i < pieces.size(); i++) {
            RocketPlan.Piece piece = pieces.get(i);
            height = Math.max(height, piece.offset().getY());
            wide |= piece.offset().getX() != 0 || piece.offset().getZ() != 0;
            if (placed(i)) {
                blocks.add(new GuideSceneRenderState.PlacedBlock(piece.block().defaultBlockState(), piece.offset().getX(),
                        piece.offset().getY() - 1, piece.offset().getZ()));
            }
        }
        float yaw = (Util.getMillis() % TURN_MILLIS) * 360.0F / TURN_MILLIS;
        float scale = Math.min(Math.min(16.0F, (areaWidth - 16) / (wide ? 4.5F : 2.0F)), (areaHeight - 16) / (float) (height + 1));
        if (!blocks.isEmpty()) {
            graphics.submitPictureInPictureRenderState(new GuideSceneRenderState(blocks, yaw, PITCH, 0.5F, height / 2.0F, 0.5F,
                    x, y, x + areaWidth, y + areaHeight, scale, graphics.peekScissorStack()));
            graphics.nextStratum();
        } else {
            String empty = Ui.fit(font, Component.translatable("construction.orbitalis.empty").getString(), areaWidth - 8);
            graphics.text(font, empty, x + (areaWidth - font.width(empty)) / 2, y + areaHeight / 2 - 4, Ui.MUTED, false);
        }
    }

    private void extractSheet(GuiGraphicsExtractor graphics, int x, int y, int columnWidth, int mouseX, int mouseY, int done) {
        // L'avancement : les blocs posés, et la couche en cours (la plus basse où il manque quelque chose).
        Ui.heading(graphics, font, Component.translatable("construction.orbitalis.progress"), x, y);
        y += LINE;
        Ui.bar(graphics, x, y, columnWidth, 4, pieces.isEmpty() ? 0.0F : done / (float) pieces.size(), done == pieces.size() ? Ui.OK : Ui.ACCENT);
        y += 7;
        TreeSet<Integer> layers = new TreeSet<>();
        int current = Integer.MAX_VALUE;
        for (int i = 0; i < pieces.size(); i++) {
            int layer = pieces.get(i).offset().getY();
            layers.add(layer);
            if (!placed(i)) {
                current = Math.min(current, layer);
            }
        }
        Ui.row(graphics, font, Component.translatable("construction.orbitalis.placed"), done + " / " + pieces.size(), x, y, columnWidth, Ui.TEXT);
        y += LINE - 1;
        String layerText = current == Integer.MAX_VALUE ? "—" : (layers.headSet(current, true).size()) + " / " + layers.size();
        Ui.row(graphics, font, Component.translatable("construction.orbitalis.layer"), layerText, x, y, columnWidth, Ui.TEXT);
        y += LINE + 2;

        // Ce qu'il faut encore réunir, face à ce que le joueur a sur lui.
        Ui.heading(graphics, font, Component.translatable("construction.orbitalis.gather"), x, y);
        y += LINE;
        Map<Item, Integer> needed = new LinkedHashMap<>();
        Map<Item, Integer> blocksLeft = new LinkedHashMap<>();
        for (int i = 0; i < pieces.size(); i++) {
            if (!placed(i)) {
                RocketPlan.Piece piece = pieces.get(i);
                needed.merge(piece.source(), piece.count(), Integer::sum);
                blocksLeft.merge(piece.source(), 1, Integer::sum);
            }
        }
        int bottom = top + HEIGHT - 26 - 2 * (LINE - 1) - 4;
        int rows = Math.max(1, (bottom - y) / ROW);
        int shown = 0;
        for (Map.Entry<Item, Integer> entry : needed.entrySet()) {
            if (shown == rows - 1 && needed.size() > rows) {
                graphics.text(font, Ui.fit(font, Component.translatable("construction.orbitalis.more", needed.size() - shown).getString(), columnWidth),
                        x + 16, y + 2, Ui.MUTED, false);
                y += ROW;
                break;
            }
            Item item = entry.getKey();
            int need = Math.max(entry.getValue(), 1);
            int have = count(item);
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(x, y);
            pose.scale(0.75F, 0.75F);
            graphics.item(new ItemStack(item), 0, 0);
            pose.popMatrix();
            String amount = Math.min(have, need) + " / " + need;
            int color = have >= need ? Ui.OK : Ui.BAD;
            Ui.right(graphics, font, amount, x + columnWidth, y + 2, color);
            String name = Ui.fit(font, new ItemStack(item).getHoverName().getString(), columnWidth - 18 - font.width(amount) - 6);
            graphics.text(font, name, x + 16, y + 2, Ui.TEXT, false);
            y += ROW;
            shown++;
        }
        if (needed.isEmpty()) {
            graphics.text(font, Ui.fit(font, Component.translatable("construction.orbitalis.all_placed").getString(), columnWidth), x, y + 2, Ui.OK, false);
        }

        // Les raccords qui manquent, et la charge utile à bord.
        int lineY = bottom + 2;
        if (!state.ports().isEmpty()) {
            List<String> names = new ArrayList<>();
            for (int port : state.ports()) {
                FuelPortBlock.Kind kind = FuelPortBlock.Kind.values()[Math.clamp(port, 0, FuelPortBlock.Kind.values().length - 1)];
                names.add(Component.translatable("spaceport.orbitalis.fuel_port." + kind.getSerializedName()).getString());
            }
            graphics.text(font, Ui.fit(font, Component.translatable("construction.orbitalis.ports", String.join(", ", names)).getString(), columnWidth),
                    x, lineY, Ui.BAD, false);
        }
        lineY += LINE - 1;
        ItemStack cargo = design.payload();
        Component cargoName = cargo.isEmpty() ? Component.translatable("design.orbitalis.no_payload") : cargo.getHoverName();
        graphics.text(font, Ui.fit(font, Component.translatable("construction.orbitalis.payload", cargoName).getString(), columnWidth),
                x, lineY, Ui.MUTED, false);

        // Les deux boutons de pose.
        int half = (columnWidth - 6) / 2;
        int buttonY = top + HEIGHT - 24;
        button(graphics, x, buttonY, half, 16, Component.translatable("construction.orbitalis.build_layer"), false, mouseX, mouseY,
                () -> send(ConstructionActionPayload.LAYER));
        button(graphics, x + half + 6, buttonY, half, 16, Component.translatable("construction.orbitalis.build_all"), true, mouseX, mouseY,
                () -> send(ConstructionActionPayload.ALL));
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
        ClientPacketDistributor.sendToServer(new ConstructionActionPayload(state.table(), action));
    }

    private void button(GuiGraphicsExtractor graphics, int x, int y, int w, int h, Component label, boolean main, int mouseX, int mouseY, Runnable action) {
        Zone zone = new Zone(x, y, w, h, action);
        zones.add(zone);
        Ui.button(graphics, font, Component.literal(Ui.fit(font, label.getString(), w - 8)), x, y, w, h, zone.contains(mouseX, mouseY), true, main,
                main ? Ui.ACCENT : Ui.MUTED);
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
