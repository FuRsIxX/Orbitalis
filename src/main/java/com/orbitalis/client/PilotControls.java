package com.orbitalis.client;

import java.util.List;
import java.util.Set;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.orbitalis.Orbitalis;
import com.orbitalis.core.network.PilotInputPayload;
import com.orbitalis.phase2.flight.CapsuleEntity;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;

/**
 * Commandes du pilote, assis dans sa cabine.
 * <ul>
 * <li><b>Ses touches.</b> Tangage, lacet, roulis, étape du vol, séquence de lancement, retour sur Terre, départ vers
 * la Lune : chacune a sa touche, réglable dans les options du jeu, rubrique « Orbitalis : pilotage ». Au départ,
 * elles ne sont pas assignées, et chacune suit alors la touche du jeu qui lui ressemble : avancer et reculer pour le
 * tangage, gauche et droite pour le lacet, le saut pour les actions, la course pour la Lune. Elles suivent ainsi la
 * disposition du clavier du joueur, sans qu'aucune touche n'apparaisse en conflit dans les options. Seul le roulis
 * (Z et C) a ses propres touches. Ces touches ne valent que dans une cabine.</li>
 * <li><b>L'envoi.</b> Les commandes tenues partent au serveur quand elles changent, et sont confirmées quatre fois par
 * seconde.</li>
 * <li><b>Le regard.</b> La cabine tourne avec la fusée, et le pilote avec elle : sa vue s'incline d'autant. En vue
 * extérieure, la caméra recule assez pour montrer la fusée.</li>
 * <li><b>L'écran.</b> La barre d'objets, la vie et la faim s'effacent : leur place revient aux instruments de bord.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Orbitalis.MODID, value = Dist.CLIENT)
public final class PilotControls {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Orbitalis.id("piloting"));
    /** Une commande non assignée suit la touche du jeu qui lui ressemble. */
    private static final int UNBOUND = InputConstants.UNKNOWN.getValue();

    /** Ces touches ne valent qu'assis dans une cabine, et ne gênent donc aucune autre : le jeu ne les signale pas en conflit. */
    private static final IKeyConflictContext IN_CABIN = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            return cabin() != null;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return false;
        }
    };

    public static final KeyMapping PITCH_DOWN = key("pitch_down", UNBOUND);
    public static final KeyMapping PITCH_UP = key("pitch_up", UNBOUND);
    public static final KeyMapping YAW_LEFT = key("yaw_left", UNBOUND);
    public static final KeyMapping YAW_RIGHT = key("yaw_right", UNBOUND);
    public static final KeyMapping ROLL_LEFT = key("roll_left", GLFW.GLFW_KEY_Z);
    public static final KeyMapping ROLL_RIGHT = key("roll_right", GLFW.GLFW_KEY_C);
    public static final KeyMapping STEP = key("step", UNBOUND);
    public static final KeyMapping LAUNCH = key("launch", UNBOUND);
    public static final KeyMapping RETURN = key("return", UNBOUND);
    public static final KeyMapping MOON = key("moon", UNBOUND);
    public static final KeyMapping CHUTE = key("chute", UNBOUND);
    /** La vue carte : elle ne pilote rien, elle s'ouvre et se ferme sur l'écran du pilote. */
    public static final KeyMapping MAP = key("map", GLFW.GLFW_KEY_M);

    /** Chaque touche, et la commande qu'elle tient. */
    private static final List<KeyMapping> KEYS = List.of(PITCH_DOWN, PITCH_UP, YAW_LEFT, YAW_RIGHT, ROLL_LEFT, ROLL_RIGHT, STEP, LAUNCH, RETURN, MOON, CHUTE);
    private static final int[] BITS = {PilotInputPayload.PITCH_DOWN, PilotInputPayload.PITCH_UP, PilotInputPayload.YAW_LEFT,
            PilotInputPayload.YAW_RIGHT, PilotInputPayload.ROLL_LEFT, PilotInputPayload.ROLL_RIGHT, PilotInputPayload.STEP,
            PilotInputPayload.LAUNCH, PilotInputPayload.RETURN, PilotInputPayload.MOON, PilotInputPayload.CHUTE};
    /** Ce que le jeu ne dessine plus dans une cabine : la place est aux instruments. */
    private static final Set<Identifier> HIDDEN = Set.of(VanillaGuiLayers.HOTBAR, VanillaGuiLayers.PLAYER_HEALTH, VanillaGuiLayers.ARMOR_LEVEL,
            VanillaGuiLayers.FOOD_LEVEL, VanillaGuiLayers.AIR_LEVEL, VanillaGuiLayers.VEHICLE_HEALTH, VanillaGuiLayers.EXPERIENCE_LEVEL,
            VanillaGuiLayers.CONTEXTUAL_INFO_BAR, VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND, VanillaGuiLayers.SELECTED_ITEM_NAME,
            VanillaGuiLayers.CROSSHAIR);

    private static int sent;
    private static int sinceSent;

    private PilotControls() {
    }

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.orbitalis." + name, IN_CABIN, InputConstants.Type.KEYSYM, code, CATEGORY);
    }

    /** Les commandes que ce client tient en ce moment (voir {@code PilotInputPayload}) : le manche de la cabine les suit. */
    public static int heldBits() {
        return sent;
    }

    /** La cabine dans laquelle le joueur est assis, ou {@code null}. */
    public static CapsuleEntity cabin() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.getVehicle() instanceof CapsuleEntity capsule ? capsule : null;
    }

    /** La touche qui tient vraiment une commande : la sienne, ou, si elle n'est pas assignée, celle du jeu qui lui ressemble. */
    private static KeyMapping effective(KeyMapping key) {
        if (!key.isUnbound()) {
            return key;
        }
        net.minecraft.client.Options options = Minecraft.getInstance().options;
        return key == PITCH_DOWN ? options.keyUp : key == PITCH_UP ? options.keyDown : key == YAW_LEFT ? options.keyLeft
                : key == YAW_RIGHT ? options.keyRight : key == MOON ? options.keySprint
                : key == STEP || key == LAUNCH || key == RETURN || key == CHUTE ? options.keyJump : key;
    }

    /** Nom de la touche d'une commande, telle que le joueur l'a réglée : les instruments l'affichent. */
    public static Component name(KeyMapping key) {
        return effective(key).getTranslatedKeyMessage();
    }

    @SubscribeEvent
    static void register(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        KEYS.forEach(event::register);
        event.register(MAP);
    }

    /**
     * Vrai si la touche est enfoncée. On interroge le clavier lui-même : plusieurs commandes peuvent ainsi partager
     * une touche (elles sont toutes sur la barre d'espace au départ), entre elles comme avec celles du jeu.
     */
    static boolean held(KeyMapping command) {
        KeyMapping key = effective(command);
        InputConstants.Key bound = key.getKey();
        if (bound.getType() == InputConstants.Type.KEYSYM && bound.getValue() > 0) {
            return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), bound.getValue());
        }
        return key.isDown();
    }

    /**
     * Avant que le jeu ne lise le clavier : en montée, une touche de pilotage ne fait que piloter. Sans cela, le
     * roulis jetterait l'objet tenu en main ou ouvrirait l'inventaire.
     */
    @SubscribeEvent
    static void beforeTick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        CapsuleEntity capsule = cabin();
        if (capsule == null || !capsule.isFollowingRocket() || minecraft.gui.screen() != null) {
            return;
        }
        for (KeyMapping other : minecraft.options.keyMappings) {
            if (KEYS.contains(other)) {
                continue;
            }
            for (int k = 0; k < 6; k++) {
                if (!KEYS.get(k).isUnbound() && other.getKey().equals(KEYS.get(k).getKey())) {
                    while (other.consumeClick()) {
                        // Appui absorbé.
                    }
                }
            }
        }
    }

    @SubscribeEvent
    static void afterTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (cabin() == null) {
            sent = 0;
            sinceSent = 0;
            return;
        }
        int bits = 0;
        // Un écran ouvert (la séquence, le tchat, la pause) prend le clavier : le pilote ne tient alors plus rien.
        if (minecraft.gui.screen() == null) {
            for (int k = 0; k < BITS.length; k++) {
                if (held(KEYS.get(k))) {
                    bits |= BITS[k];
                }
            }
        }
        if (bits != sent || ++sinceSent >= 5) {
            sent = bits;
            sinceSent = 0;
            ClientPacketDistributor.sendToServer(new PilotInputPayload(bits));
        }
    }

    /**
     * La vue de l'équipage. Chacun est assis droit dans la cabine, qui tourne avec la fusée : son regard, donné par
     * rapport à son siège, est ramené dans le monde. La souris garde son usage habituel : regarder devant soi,
     * c'est regarder le pare-brise et l'écran de bord. En vue extérieure, la caméra reste d'aplomb.
     */
    @SubscribeEvent
    static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        CapsuleEntity capsule = cabin();
        if (capsule == null || !Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            return;
        }
        Quaternionf attitude = capsule.attitude((float) event.getPartialTick());
        // Le regard du joueur, tel que la souris vient de le placer : sa valeur du moment, sans l'interpolation que le
        // jeu applique à un passager (elle met la vue un tick en retard sur la souris).
        Minecraft minecraft = Minecraft.getInstance();
        float yaw = minecraft.player != null ? minecraft.player.getYRot() : event.getYaw();
        float pitch = minecraft.player != null ? minecraft.player.getXRot() : event.getPitch();
        // Le jeu compose sa caméra ainsi : lacet, puis tangage, puis roulis. On la tourne, et on relit ces trois angles.
        Quaternionf view = new Quaternionf().rotationYXZ(Mth.PI - yaw * Mth.DEG_TO_RAD, -pitch * Mth.DEG_TO_RAD,
                -event.getRoll() * Mth.DEG_TO_RAD);
        Vector3f angles = new Quaternionf(attitude).mul(view).getEulerAnglesYXZ(new Vector3f());
        event.setYaw(180.0F - angles.y * Mth.RAD_TO_DEG);
        event.setPitch(-angles.x * Mth.RAD_TO_DEG);
        event.setRoll(-angles.z * Mth.RAD_TO_DEG);
    }

    /**
     * L'équipage se voit assis dans la cabine tant qu'elle est d'aplomb. Dès qu'elle penche, le jeu, qui ne sait
     * asseoir un personnage que droit, le ferait passer à travers les cloisons : il n'est alors plus dessiné.
     */
    @SubscribeEvent
    static void onPlayer(net.neoforged.neoforge.client.event.RenderPlayerEvent.Pre<?> event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.level.getEntity(event.getRenderState().id) instanceof net.minecraft.world.entity.player.Player player
                && player.getVehicle() instanceof CapsuleEntity capsule && Math.abs(capsule.attitude(1.0F).w) < 0.985F) {
            event.setCanceled(true);
        }
    }

    /**
     * En vue extérieure, la caméra recule assez pour montrer la fusée entière : d'autant plus qu'elle est haute. Une
     * fois la cabine seule, elle revient à une distance ordinaire.
     */
    @SubscribeEvent
    static void onCameraDistance(CalculateDetachedCameraDistanceEvent event) {
        CapsuleEntity capsule = cabin();
        if (capsule == null) {
            return;
        }
        int tall = 0;
        if (capsule.isFollowingRocket() && capsule.flightShape() != null) {
            tall = capsule.flightShape().height();
        } else if (capsule.getPhase() == CapsuleEntity.Phase.PAD && capsule.getTable() != null
                && capsule.level().getBlockEntity(capsule.getTable()) instanceof com.orbitalis.phase1.spaceport.LaunchTableBlockEntity table && table.hasRocket()) {
            tall = table.rocketShape().height();
        }
        event.setDistance(Math.max(event.getDistance(), tall > 0 ? Math.clamp(tall * 1.5F + 8.0F, 14.0F, 72.0F) : 8.0F));
    }

    @SubscribeEvent
    static void onLayer(RenderGuiLayerEvent.Pre event) {
        if (HIDDEN.contains(event.getName()) && (cabin() != null || CockpitHud.forceShow)) {
            event.setCanceled(true);
        }
    }
}
