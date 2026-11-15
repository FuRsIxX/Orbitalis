package com.orbitalis.client.port.event;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.orbitalis.client.port.GuiGraphicsExtractor;
import com.orbitalis.platform.event.Cancelable;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Les événements du client qu'attend le code du mod, sous la forme qu'ils avaient sous NeoForge 26 ; {@link ClientEvents} les
 * fait venir d'Architectury ou du chargeur.
 */
public final class ClientHooks {
    private ClientHooks() {
    }

    /** Le tick du client, avant ou après le reste du jeu. */
    public static final class ClientTickEvent {
        private ClientTickEvent() {
        }

        public static final class Pre {
        }

        public static final class Post {
        }
    }

    /** Après le dessin de l'interface du jeu (barre d'objets, vie…) : les instruments du mod se dessinent par-dessus. */
    public record RenderGuiEvent(GuiGraphicsExtractor getGuiGraphics, DeltaTracker getPartialTick) {
    }

    /** Une couche de l'interface du jeu va se dessiner : on peut l'en empêcher (voir {@link VanillaGuiLayers}). */
    public static final class RenderGuiLayerEvent extends Cancelable {
        private final ResourceLocation name;

        public RenderGuiLayerEvent(ResourceLocation name) {
            this.name = name;
        }

        public ResourceLocation getName() {
            return name;
        }
    }

    /** La distance de la caméra à la troisième personne, derrière le joueur. */
    public static final class CalculateDetachedCameraDistanceEvent {
        private float distance;

        public CalculateDetachedCameraDistanceEvent(float distance) {
            this.distance = distance;
        }

        public float getDistance() {
            return distance;
        }

        public void setDistance(float distance) {
            this.distance = distance;
        }
    }

    /** Un joueur va être dessiné : on peut l'en empêcher. */
    public static final class RenderPlayerEvent extends Cancelable {
        private final Player entity;

        public RenderPlayerEvent(Player entity) {
            this.entity = entity;
        }

        public Player getEntity() {
            return entity;
        }
    }

    /** La molette de la souris, hors des écrans. */
    public static final class MouseScrollingEvent extends Cancelable {
        private final double scrollDeltaY;

        public MouseScrollingEvent(double scrollDeltaY) {
            this.scrollDeltaY = scrollDeltaY;
        }

        public double getScrollDeltaY() {
            return scrollDeltaY;
        }
    }

    /** Les lignes de la bulle d'aide d'un objet, qu'on peut compléter. */
    public record ItemTooltipEvent(ItemStack getItemStack, List<Component> getToolTip) {
    }

    /** Un écran va s'ouvrir : on peut l'en empêcher. */
    public static final class ScreenOpening extends Cancelable {
        private final net.minecraft.client.gui.screens.Screen newScreen;

        public ScreenOpening(net.minecraft.client.gui.screens.Screen newScreen) {
            this.newScreen = newScreen;
        }

        public net.minecraft.client.gui.screens.Screen getNewScreen() {
            return newScreen;
        }
    }

    /** Une touche enfoncée dans un écran, avant qu'il ne la traite. */
    public static final class ScreenKeyPressed extends Cancelable {
        private final net.minecraft.client.gui.screens.Screen screen;
        private final com.orbitalis.client.port.KeyEvent keyEvent;

        public ScreenKeyPressed(net.minecraft.client.gui.screens.Screen screen, com.orbitalis.client.port.KeyEvent keyEvent) {
            this.screen = screen;
            this.keyEvent = keyEvent;
        }

        public net.minecraft.client.gui.screens.Screen getScreen() {
            return screen;
        }

        public com.orbitalis.client.port.KeyEvent getKeyEvent() {
            return keyEvent;
        }
    }

    /** Un clic droit sur un bloc, de ce côté-ci. */
    public static final class RightClickBlock extends Cancelable {
        private final Player entity;
        private final BlockPos pos;

        public RightClickBlock(Player entity, BlockPos pos) {
            this.entity = entity;
            this.pos = pos;
        }

        public Player getEntity() {
            return entity;
        }

        public Level getLevel() {
            return entity.level();
        }

        public BlockPos getPos() {
            return pos;
        }
    }

    /** Le tick d'un joueur, avant ou après son mouvement. */
    public record PlayerTick(Player getEntity) {
    }

    /** L'inscription des touches du mod. */
    public static final class RegisterKeyMappingsEvent {
        private final Consumer<KeyMapping> register;

        public RegisterKeyMappingsEvent(Consumer<KeyMapping> register) {
            this.register = register;
        }

        public void register(KeyMapping key) {
            register.accept(key);
        }

        /** Les catégories de touches sont de simples noms en 1.21.1 : rien à inscrire. */
        public void registerCategory(String category) {
        }
    }

    /**
     * Le monde vient d'être dessiné (blocs translucides compris) : le mod y ajoute ses formes (fantômes, débris). La pile de
     * transformations part de la caméra, tournée comme elle.
     */
    public record LevelRenderEvent(PoseStack getPoseStack, MultiBufferSource.BufferSource getBuffers, Camera getCamera,
            float getPartialTick) {
        public Vec3 cameraPosition() {
            return getCamera.getPosition();
        }
    }

    /** Les couches de l'interface du jeu qu'on sait cacher, sur les deux chargeurs. */
    public static final class VanillaGuiLayers {
        public static final ResourceLocation HOTBAR = id("hotbar");
        public static final ResourceLocation PLAYER_HEALTH = id("player_health");
        public static final ResourceLocation ARMOR_LEVEL = id("armor_level");
        public static final ResourceLocation FOOD_LEVEL = id("food_level");
        public static final ResourceLocation AIR_LEVEL = id("air_level");
        public static final ResourceLocation VEHICLE_HEALTH = id("vehicle_health");
        public static final ResourceLocation EXPERIENCE_LEVEL = id("experience_level");
        public static final ResourceLocation CONTEXTUAL_INFO_BAR = id("experience_bar");
        public static final ResourceLocation CONTEXTUAL_INFO_BAR_BACKGROUND = id("jump_meter");
        public static final ResourceLocation SELECTED_ITEM_NAME = id("selected_item_name");
        public static final ResourceLocation CROSSHAIR = id("crosshair");

        /** Les couches de la barre du bas, que le joueur ne voit plus dans la cabine. */
        public static final Set<ResourceLocation> ALL = Set.of(HOTBAR, PLAYER_HEALTH, ARMOR_LEVEL, FOOD_LEVEL, AIR_LEVEL, VEHICLE_HEALTH,
                EXPERIENCE_LEVEL, CONTEXTUAL_INFO_BAR, CONTEXTUAL_INFO_BAR_BACKGROUND, SELECTED_ITEM_NAME, CROSSHAIR);

        private VanillaGuiLayers() {
        }

        private static ResourceLocation id(String path) {
            return ResourceLocation.withDefaultNamespace(path);
        }
    }
}
