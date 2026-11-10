package com.orbitalis.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.orbitalis.Orbitalis;
import com.orbitalis.client.render.GhostBlocks;
import com.orbitalis.phase2.station.ModuleType;
import com.orbitalis.phase2.station.StationModuleItem;
import com.orbitalis.phase2.station.StationModules;
import com.orbitalis.phase2.station.StationPortBlock;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * Le fantôme d'un module de station : un kit en main, on vise un port de jonction, et le module se dessine de l'autre
 * côté, tel qu'il se déploiera (le berceau d'un chantier naval en silhouette). Au-dessus de la barre d'objets, une ligne
 * dit si l'on peut le déployer, ou ce qui l'empêche.
 */
@EventBusSubscriber(modid = Orbitalis.MODID, value = Dist.CLIENT)
public final class ModuleKitPlacement {
    /** Intervalle entre deux rappels de la ligne d'aide, en ticks (elle s'efface d'elle-même au bout de trois secondes). */
    private static final int HINT_INTERVAL = 40;

    /** L'esquisse en cours : le port et le module dessinés, et leurs fantômes. Refaite seulement s'ils changent. */
    private static BlockPos sketchedPort;
    private static BlockState sketchedState;
    private static ModuleType sketchedType;
    private static final List<BlockPos> OFFSETS = new ArrayList<>();
    private static final List<BlockState> STATES = new ArrayList<>();
    private static int detailed;

    /** La ligne d'aide : pour quel port et quel module elle a été montrée, et quand. */
    private static BlockPos hintPort;
    private static ModuleType hintType;
    private static int hintAge;

    /** Pour les captures : un port et un module à esquisser, sans kit en main ni visée ({@code null} : comme en jeu). */
    private static BlockPos forcedPort;
    private static ModuleType forcedType;

    private ModuleKitPlacement() {
    }

    /** Captures : esquisse ce module sur ce port quoi qu'on vise ; {@code null} rend la main au jeu. */
    public static void force(BlockPos port, ModuleType type) {
        forcedPort = port;
        forcedType = type;
    }

    /** Le module que le joueur a en main (main droite d'abord), s'il s'accroche sur un port. */
    private static ModuleType held(LocalPlayer player) {
        for (ItemStack stack : new ItemStack[] {player.getMainHandItem(), player.getOffhandItem()}) {
            if (stack.getItem() instanceof StationModuleItem item && item.type().pressurized() && item.type() != ModuleType.CORE) {
                return item.type();
            }
        }
        return null;
    }

    /** La moitié haute du port visé, ou {@code null} si le joueur ne vise pas de port, kit en main. */
    private static BlockPos target(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null || minecraft.gui.screen() != null || held(minecraft.player) == null) {
            return null;
        }
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockState state = minecraft.level.getBlockState(hit.getBlockPos());
        return StationModules.isPort(state) ? StationPortBlock.upper(state, hit.getBlockPos()) : null;
    }

    @SubscribeEvent
    static void onSubmit(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockPos port = forcedPort != null ? forcedPort : target(minecraft);
        if (port == null || minecraft.level == null) {
            return;
        }
        ModuleType type = forcedPort != null ? forcedType : held(minecraft.player);
        BlockState state = minecraft.level.getBlockState(port);
        if (!StationModules.isPort(state)) {
            return;
        }
        if (!port.equals(sketchedPort) || type != sketchedType || state != sketchedState) {
            sketchedPort = port.immutable();
            sketchedType = type;
            sketchedState = state;
            OFFSETS.clear();
            STATES.clear();
            // Le module d'abord, en détail ; le berceau d'un chantier ensuite, en silhouette.
            List<BlockPos> cradle = new ArrayList<>();
            List<BlockState> cradleStates = new ArrayList<>();
            List<StationModules.Placed> plan = StationModules.plan(port, state, type);
            int moduleCells = StationModules.moduleCells(type);
            for (int i = 0; i < plan.size(); i++) {
                StationModules.Placed placed = plan.get(i);
                if (placed.state() == null || placed.state().isAir()) {
                    continue;
                }
                boolean inModule = i < moduleCells;
                (inModule ? OFFSETS : cradle).add(placed.pos().subtract(port));
                (inModule ? STATES : cradleStates).add(placed.state());
            }
            detailed = OFFSETS.size();
            OFFSETS.addAll(cradle);
            STATES.addAll(cradleStates);
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(port.getX() - camera.x, port.getY() - camera.y, port.getZ() - camera.z);
        GhostBlocks.submitFocused(OFFSETS, STATES, detailed, poseStack, event.getSubmitNodeCollector());
        poseStack.popPose();
    }

    /** La ligne d'aide, rappelée tant qu'on vise le même port avec le même kit. */
    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockPos port = target(minecraft);
        if (port == null) {
            hintPort = null;
            return;
        }
        ModuleType type = held(minecraft.player);
        if (port.equals(hintPort) && type == hintType && ++hintAge < HINT_INTERVAL) {
            return;
        }
        hintPort = port.immutable();
        hintType = type;
        hintAge = 0;
        Component problem = StationModules.blocked(minecraft.level, port, type);
        Component line = problem != null ? problem.copy().withStyle(ChatFormatting.GOLD)
                : Component.translatable("station.orbitalis.port.deploy", Component.translatable(type.nameKey())).withStyle(ChatFormatting.AQUA);
        minecraft.player.sendOverlayMessage(line);
    }
}
