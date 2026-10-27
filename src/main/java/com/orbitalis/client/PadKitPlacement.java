package com.orbitalis.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.orbitalis.Orbitalis;
import com.orbitalis.client.render.GhostBlocks;
import com.orbitalis.client.screen.PadKitScreen;
import com.orbitalis.core.network.PadKitPayload;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.registry.ModItems;
import com.orbitalis.phase1.spaceport.PadPlan;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Pose du plan d'un pas de tir, kit en main.
 * <p>
 * Un premier clic droit ouvre l'écran du kit. Le plan choisi, le kit est « armé » : le pas de tir s'esquisse en
 * blocs fantômes là où le joueur regarde, centré sur sa table de lancement. La molette le fait tourner, et un clic
 * droit sur le sol le pose. Un clic droit dans le vide rouvre l'écran, pour changer de plan.
 */
@EventBusSubscriber(modid = Orbitalis.MODID, value = Dist.CLIENT)
public final class PadKitPlacement {
    private static boolean armed;
    private static int radius = 2;
    private static boolean elevator;
    private static Direction facing = Direction.EAST;

    /** Esquisse en cours : le plan dessiné, et ses blocs fantômes. Elle n'est refaite que si le plan change. */
    private static PadPlan sketched;
    private static final List<BlockPos> OFFSETS = new ArrayList<>();
    private static final List<BlockState> STATES = new ArrayList<>();

    private PadKitPlacement() {
    }

    /** Clic droit dans le vide : on ouvre l'écran du kit, pour choisir le plan ou en changer. */
    public static void use() {
        LocalPlayer player = Minecraft.getInstance().player;
        Direction look = player == null ? Direction.EAST : player.getDirection();
        Minecraft.getInstance().gui.setScreen(new PadKitScreen(null, armed ? facing : look, radius, elevator));
    }

    /** Clic droit sur un bloc : sur une table, on change son plan ; sinon on pose le plan choisi, s'il y en a un. */
    public static void useOn(BlockPos clicked, Direction face, Direction look) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.level.getBlockState(clicked).is(ModBlocks.LAUNCH_TABLE.get())) {
            minecraft.gui.setScreen(new PadKitScreen(clicked, look, radius, elevator));
            return;
        }
        if (!armed) {
            use();
            return;
        }
        ClientPacketDistributor.sendToServer(new PadKitPayload(tableAbove(clicked, face), radius, elevator, facing));
        armed = false;
    }

    /** Le joueur a choisi un plan dans l'écran du kit : il reste à viser le sol et à cliquer. */
    public static void arm(int chosenRadius, boolean chosenElevator, Direction chosenFacing) {
        radius = chosenRadius;
        elevator = chosenElevator;
        facing = chosenFacing;
        armed = true;
        overlay(Component.translatable("spaceport.orbitalis.kit.aim").withStyle(ChatFormatting.AQUA));
    }

    /** La table se pose un bloc au-dessus de la case visée, pour loger le déflecteur dessous. */
    private static BlockPos tableAbove(BlockPos clicked, Direction face) {
        return clicked.relative(face).above();
    }

    /** Message au-dessus de la barre d'objets. */
    private static void overlay(Component message) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendOverlayMessage(message);
        }
    }

    private static boolean holdingKit(LocalPlayer player) {
        return player.getMainHandItem().is(ModItems.PAD_KIT.get()) || player.getOffhandItem().is(ModItems.PAD_KIT.get());
    }

    /** Emplacement de la table si le joueur cliquait maintenant, ou {@code null} s'il n'y a rien à esquisser. */
    private static BlockPos target() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!armed || minecraft.player == null || minecraft.level == null || minecraft.gui.screen() != null || !holdingKit(minecraft.player)) {
            return null;
        }
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || minecraft.level.getBlockState(hit.getBlockPos()).is(ModBlocks.LAUNCH_TABLE.get())) {
            return null;
        }
        return tableAbove(hit.getBlockPos(), hit.getDirection());
    }

    /** La molette fait tourner l'esquisse. Accroupi, elle garde son rôle habituel. */
    @SubscribeEvent
    static void onScroll(InputEvent.MouseScrollingEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (event.getScrollDeltaY() == 0 || target() == null || player == null || player.isShiftKeyDown()) {
            return;
        }
        facing = event.getScrollDeltaY() > 0 ? facing.getClockWise() : facing.getCounterClockWise();
        overlay(Component.translatable("spaceport.orbitalis.kit.facing.now",
                Component.translatable("spaceport.orbitalis.kit.direction." + facing.getSerializedName())).withStyle(ChatFormatting.AQUA));
        event.setCanceled(true);
    }

    /** Esquisse du pas de tir : la table, le tablier et le pied de la tour. */
    @SubscribeEvent
    static void onSubmit(SubmitCustomGeometryEvent event) {
        BlockPos table = target();
        if (table == null) {
            return;
        }
        PadPlan plan = PadPlan.forKit(radius, elevator, facing);
        if (!plan.equals(sketched)) {
            sketched = plan;
            OFFSETS.clear();
            STATES.clear();
            OFFSETS.add(BlockPos.ZERO);
            STATES.add(ModBlocks.LAUNCH_TABLE.get().defaultBlockState());
            for (PadPlan.Piece piece : plan.pieces()) {
                int y = piece.offset().getY();
                // Le tablier et ce qui est à sa hauteur, et le premier niveau de la tour, posé au sol.
                if (y == 0 || (y == -1 && piece.role() == PadPlan.Role.TOWER)) {
                    OFFSETS.add(piece.offset());
                    STATES.add(piece.display());
                }
            }
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(table.getX() - camera.x, table.getY() - camera.y, table.getZ() - camera.z);
        GhostBlocks.submit(OFFSETS, STATES, poseStack, event.getSubmitNodeCollector());
        poseStack.popPose();
    }
}
