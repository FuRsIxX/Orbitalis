package com.orbitalis.client;

import com.orbitalis.Orbitalis;
import com.orbitalis.client.screen.BlockScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Deux règles communes aux écrans du mod.
 * <ul>
 * <li>Un écran ouvert depuis un bloc ({@link BlockScreen}) se ferme si ce bloc disparaît : en multijoueur, un autre
 * joueur peut casser le pupitre dont on se sert. Les écrans à inventaire (machines) le font déjà, par le serveur.</li>
 * <li>La touche d'inventaire (E par défaut) ferme un écran du mod, comme elle ferme un coffre, sauf pendant qu'on
 * tape dans un champ de texte.</li>
 * </ul>
 * L'écran ne dit pas de quel bloc il vient : c'est le dernier bloc cliqué, juste avant son ouverture. Un écran ouvert
 * depuis un autre (l'atlas depuis le pupitre) reste lié au même bloc.
 */
@EventBusSubscriber(modid = Orbitalis.MODID, value = Dist.CLIENT)
public final class ScreenGuard {
    /** Délai entre le clic et l'ouverture de l'écran (le serveur répond d'abord), en ticks. */
    private static final int CLICK_WINDOW = 60;

    private static BlockPos clicked;
    private static Block clickedBlock;
    private static long clickedAt = Long.MIN_VALUE;
    /** Le bloc dont dépend l'écran ouvert, ou {@code null}. */
    private static BlockPos bound;
    private static Block boundBlock;

    private ScreenGuard() {
    }

    @SubscribeEvent
    static void onClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) {
            clicked = event.getPos().immutable();
            clickedBlock = event.getLevel().getBlockState(clicked).getBlock();
            clickedAt = event.getLevel().getGameTime();
        }
    }

    @SubscribeEvent
    static void onOpening(ScreenEvent.Opening event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(event.getNewScreen() instanceof BlockScreen) || minecraft.level == null) {
            bound = null;
            return;
        }
        // Un écran ouvert depuis un autre garde son bloc ; sinon, c'est le bloc qu'on vient de cliquer. À bord d'une
        // capsule, le séquenceur ne dépend d'aucun bloc : la fusée qu'on vient de cliquer pour monter s'en va au décollage.
        boolean aboard = minecraft.player != null && minecraft.player.getVehicle() instanceof com.orbitalis.phase2.flight.CapsuleEntity;
        if (bound == null && !aboard && clicked != null && minecraft.level.getGameTime() - clickedAt <= CLICK_WINDOW) {
            bound = clicked;
            boundBlock = clickedBlock;
        }
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof BlockScreen) || minecraft.level == null) {
            bound = null;
            return;
        }
        if (bound != null && minecraft.level.isLoaded(bound) && !minecraft.level.getBlockState(bound).is(boundBlock)) {
            bound = null;
            minecraft.gui.setScreen(null);
        }
    }

    /** La touche d'inventaire ferme l'écran, comme Échap ; un écran à inventaire le fait déjà de lui-même. */
    @SubscribeEvent
    static void onKey(ScreenEvent.KeyPressed.Pre event) {
        Screen screen = event.getScreen();
        if (screen instanceof AbstractContainerScreen<?> || !screen.getClass().getName().startsWith("com.orbitalis.client.screen.")) {
            return;
        }
        if (screen.getFocused() instanceof EditBox box && box.canConsumeInput()) {
            return;
        }
        if (Minecraft.getInstance().options.keyInventory.matches(event.getKeyEvent())) {
            screen.onClose();
            event.setCanceled(true);
        }
    }
}
