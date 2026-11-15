package com.orbitalis.client.port.event;

import com.orbitalis.client.port.GuiGraphicsExtractor;
import com.orbitalis.client.port.KeyEvent;

import dev.architectury.event.CompoundEventResult;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientRawInputEvent;
import dev.architectury.event.events.client.ClientScreenInputEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.event.events.client.ClientTooltipEvent;
import dev.architectury.event.events.common.InteractionEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * L'aiguillage des événements du client vers les méthodes du mod qui les attendent (sous NeoForge 26, elles étaient trouvées
 * par leurs annotations). Ce qu'offre Architectury est branché ici ; le reste (caméra, brouillard, champ de vision, couches
 * de l'interface, dessin du monde) est appelé par le chargeur, par les méthodes {@code fire…}.
 */
public final class ClientEvents {
    private ClientEvents() {
    }

    /** Branche tout ; une fois, au démarrage du client. */
    public static void register() {
        ClientHooks.RegisterKeyMappingsEvent keys = new ClientHooks.RegisterKeyMappingsEvent(KeyMappingRegistry::register);
        com.orbitalis.client.PilotControls.register(keys);
        com.orbitalis.client.SkillTreeKey.register(keys);
        com.orbitalis.client.UplinkKey.register(keys);

        ClientTickEvent.CLIENT_PRE.register(minecraft -> com.orbitalis.client.PilotControls.beforeTick(new ClientHooks.ClientTickEvent.Pre()));
        ClientTickEvent.CLIENT_POST.register(minecraft -> {
            ClientHooks.ClientTickEvent.Post event = new ClientHooks.ClientTickEvent.Post();
            com.orbitalis.client.BreakupFx.onTick(event);
            com.orbitalis.client.CapsuleSounds.onTick(event);
            com.orbitalis.client.CockpitHud.onTick(event);
            com.orbitalis.client.FlightCamera.onTick(event);
            com.orbitalis.client.ModuleKitPlacement.onTick(event);
            com.orbitalis.client.PilotControls.afterTick(event);
            com.orbitalis.client.ScreenGuard.onTick(event);
            com.orbitalis.client.SkillTreeKey.afterTick(event);
            com.orbitalis.client.StationSounds.onTick(event);
            com.orbitalis.client.SuitHud.onTick(event);
            com.orbitalis.client.UplinkKey.afterTick(event);
            com.orbitalis.client.WarpFx.onTick(event);
            com.orbitalis.client.WorkstationCracks.onClientTick(event);
            com.orbitalis.client.dev.GuidePreview.onClientTick(event);
            com.orbitalis.client.dev.WorldPreview.onClientTick(event);
            com.orbitalis.client.particle.PlanetParticle.onClientTick(event);
        });
        TickEvent.PLAYER_PRE.register(player -> {
            if (player.level().isClientSide()) {
                com.orbitalis.client.MicrogravityMotion.beforeTick(new ClientHooks.PlayerTick(player));
            }
        });
        TickEvent.PLAYER_POST.register(player -> {
            if (player.level().isClientSide()) {
                com.orbitalis.client.MicrogravityMotion.afterTick(new ClientHooks.PlayerTick(player));
            }
        });
        ClientGuiEvent.RENDER_HUD.register((graphics, tick) -> {
            GuiGraphicsExtractor extractor = new GuiGraphicsExtractor(graphics);
            ClientHooks.RenderGuiEvent event = new ClientHooks.RenderGuiEvent(extractor, tick);
            com.orbitalis.client.BreakupFx.onGui(event);
            com.orbitalis.client.CockpitHud.onGui(event);
            com.orbitalis.client.SuitHud.onGui(event);
            com.orbitalis.client.WarpFx.onGui(event);
            extractor.flushTooltips();
        });
        ClientRawInputEvent.MOUSE_SCROLLED.register((minecraft, amountX, amountY) -> {
            if (minecraft.screen != null) {
                return EventResult.pass();
            }
            ClientHooks.MouseScrollingEvent event = new ClientHooks.MouseScrollingEvent(amountY);
            com.orbitalis.client.PadKitPlacement.onScroll(event);
            return event.isCanceled() ? EventResult.interruptFalse() : EventResult.pass();
        });
        ClientTooltipEvent.ITEM.register((stack, lines, context, flag) -> {
            ClientHooks.ItemTooltipEvent event = new ClientHooks.ItemTooltipEvent(stack, lines);
            com.orbitalis.client.RocketPartTooltips.onItemTooltip(event);
            com.orbitalis.client.StoredContentTooltip.onTooltip(event);
        });
        ClientGuiEvent.SET_SCREEN.register(screen -> {
            ClientHooks.ScreenOpening event = new ClientHooks.ScreenOpening(screen);
            com.orbitalis.client.ScreenGuard.onOpening(event);
            return event.isCanceled() ? CompoundEventResult.interruptFalse(screen) : CompoundEventResult.pass();
        });
        ClientScreenInputEvent.KEY_PRESSED_PRE.register((minecraft, screen, keyCode, scanCode, modifiers) -> {
            ClientHooks.ScreenKeyPressed event = new ClientHooks.ScreenKeyPressed(screen, new KeyEvent(keyCode, scanCode, modifiers));
            com.orbitalis.client.ScreenGuard.onKey(event);
            return event.isCanceled() ? EventResult.interruptTrue() : EventResult.pass();
        });
        InteractionEvent.RIGHT_CLICK_BLOCK.register((player, hand, pos, face) -> {
            if (player.level().isClientSide()) {
                ClientHooks.RightClickBlock event = new ClientHooks.RightClickBlock(player, pos);
                com.orbitalis.client.ScreenGuard.onClickBlock(event);
                if (event.isCanceled()) {
                    return EventResult.interruptFalse();
                }
            }
            return EventResult.pass();
        });
    }

    // ------------------------------------------------------------------
    // Appelés par le chargeur
    // ------------------------------------------------------------------

    /** Les angles de la caméra de cette image, revus par le mod : {yaw, pitch, roll}. */
    public static float[] cameraAngles(Camera camera, double partialTick, float yaw, float pitch, float roll) {
        ViewportEvent.ComputeCameraAngles event = new ViewportEvent.ComputeCameraAngles(camera, partialTick, yaw, pitch, roll);
        com.orbitalis.client.PilotControls.onCamera(event);
        com.orbitalis.client.RocketShake.onCamera(event);
        com.orbitalis.client.BreakupFx.onCamera(event);
        com.orbitalis.client.WarpFx.onCamera(event);
        return new float[] {event.getYaw(), event.getPitch(), event.getRoll()};
    }

    /** La couleur du brouillard de cette image, revue par le mod : {r, g, b}. */
    public static float[] fogColor(Camera camera, double partialTick, float red, float green, float blue) {
        ViewportEvent.ComputeFogColor event = new ViewportEvent.ComputeFogColor(camera, partialTick, red, green, blue);
        com.orbitalis.client.SpaceTransition.onFog(event);
        return new float[] {event.getRed(), event.getGreen(), event.getBlue()};
    }

    public static double fov(Camera camera, double partialTick, double fov) {
        ViewportEvent.ComputeFov event = new ViewportEvent.ComputeFov(camera, partialTick, fov);
        com.orbitalis.client.WarpFx.onFov(event);
        return event.getFOV();
    }

    public static float detachedCameraDistance(float distance) {
        ClientHooks.CalculateDetachedCameraDistanceEvent event = new ClientHooks.CalculateDetachedCameraDistanceEvent(distance);
        com.orbitalis.client.PilotControls.onCameraDistance(event);
        return event.getDistance();
    }

    /** Faux si cette couche de l'interface du jeu ne doit pas se dessiner. */
    public static boolean guiLayer(ResourceLocation name) {
        ClientHooks.RenderGuiLayerEvent event = new ClientHooks.RenderGuiLayerEvent(name);
        com.orbitalis.client.PilotControls.onLayer(event);
        return !event.isCanceled();
    }

    /** Faux si ce joueur ne doit pas être dessiné. */
    public static boolean renderPlayer(Player player) {
        ClientHooks.RenderPlayerEvent event = new ClientHooks.RenderPlayerEvent(player);
        com.orbitalis.client.PilotControls.onPlayer(event);
        return !event.isCanceled();
    }

    /** Après le dessin du monde : les formes du mod. */
    public static void levelRendered(ClientHooks.LevelRenderEvent event) {
        com.orbitalis.client.PadKitPlacement.onSubmit(event);
        com.orbitalis.client.ModuleKitPlacement.onSubmit(event);
        com.orbitalis.client.BreakupFx.onSubmit(event);
    }

    static Minecraft minecraft() {
        return Minecraft.getInstance();
    }
}
