package com.orbitalis.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.orbitalis.core.registry.ModFluids;
import com.orbitalis.phase1.fuel.FluidTankBlockEntity;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Dessine le contenu d'un réservoir modulable : un seul volume pour tout le réservoir, à la couleur du fluide, qui
 * monte et descend avec le niveau. Un gaz, lui, occupe tout le réservoir, d'autant plus dense qu'il y en a.
 * <p>
 * Seul le bloc maître du réservoir dessine. Le volume est en retrait d'un pixel derrière les parois de verre, et
 * s'assombrit de la surface vers le fond.
 */
public class FluidTankRenderer implements BlockEntityRenderer<FluidTankBlockEntity, FluidTankRenderer.State> {
    /** Retrait du contenu derrière les parois, en blocs. */
    private static final float INSET = 1.0F / 16.0F;

    public static class State extends BlockEntityRenderState {
        boolean visible;
        float width;
        float top;
        int color;
    }

    public FluidTankRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(FluidTankBlockEntity tank, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(tank, state, partialTicks, cameraPosition, breakProgress);
        FluidStack content = tank.isMaster() ? tank.fluid() : FluidStack.EMPTY;
        state.visible = !content.isEmpty() && tank.capacity() > 0;
        if (!state.visible) {
            return;
        }
        Fluid fluid = content.getFluid();
        float fill = Mth.clamp(content.getAmount() / (float) tank.capacity(), 0.0F, 1.0F);
        boolean gas = fluid == ModFluids.HYDROGEN.get() || fluid == ModFluids.OXYGEN.get();
        float inside = tank.height() - 2 * INSET;
        state.width = tank.width();
        // Un liquide se voit dès la première goutte : son niveau ne descend jamais sous un demi-pixel.
        state.top = INSET + (gas ? inside : Math.max(inside * fill, 0.5F / 16.0F));
        int rgb = ModFluids.color(fluid) & 0xFFFFFF;
        int alpha = gas ? 60 + (int) (150 * fill) : 255;
        state.color = alpha << 24 | rgb;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.visible) {
            return;
        }
        float lo = INSET;
        float hi = state.width - INSET;
        float top = state.top;
        int light = state.lightCoords;
        int surface = shade(state.color, 1.22F);
        int bright = shade(state.color, 1.04F);
        int deep = shade(state.color, 0.58F);
        int floor = shade(state.color, 0.45F);
        collector.submitCustomGeometry(poseStack, RenderTypes.textBackground(), (pose, buffer) -> {
            quad(buffer, pose, light, surface, surface, lo, top, lo, lo, top, hi, hi, top, hi, hi, top, lo);
            quad(buffer, pose, light, floor, floor, lo, lo, lo, hi, lo, lo, hi, lo, hi, lo, lo, hi);
            quad(buffer, pose, light, deep, bright, lo, lo, lo, lo, top, lo, hi, top, lo, hi, lo, lo);
            quad(buffer, pose, light, deep, bright, lo, lo, hi, hi, lo, hi, hi, top, hi, lo, top, hi);
            quad(buffer, pose, light, deep, bright, lo, lo, lo, lo, lo, hi, lo, top, hi, lo, top, lo);
            quad(buffer, pose, light, deep, bright, hi, lo, lo, hi, top, lo, hi, top, hi, hi, lo, hi);
        });
    }

    /**
     * Une face, dessinée dans les deux sens : on la voit de dehors comme de dedans. Ses sommets du bas prennent la
     * première couleur, ceux du haut la seconde : le liquide s'assombrit vers le fond.
     */
    private static void quad(VertexConsumer buffer, PoseStack.Pose pose, int light, int low, int high, float... points) {
        float bottom = Math.min(Math.min(points[1], points[4]), Math.min(points[7], points[10]));
        for (int pass = 0; pass < 2; pass++) {
            for (int k = 0; k < 4; k++) {
                int i = pass == 0 ? k : 3 - k;
                float y = points[i * 3 + 1];
                buffer.addVertex(pose, points[i * 3], y, points[i * 3 + 2]).setColor(y > bottom + 1.0E-4F ? high : low).setLight(light);
            }
        }
    }

    private static int shade(int color, float factor) {
        int r = Math.min(255, (int) ((color >> 16 & 0xFF) * factor));
        int g = Math.min(255, (int) ((color >> 8 & 0xFF) * factor));
        int b = Math.min(255, (int) ((color & 0xFF) * factor));
        return color & 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** Le contenu dépasse du bloc maître : il est dessiné même quand ce bloc est hors champ. */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public AABB getRenderBoundingBox(FluidTankBlockEntity tank) {
        BlockPos pos = tank.getBlockPos();
        return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + tank.width(), pos.getY() + tank.height(), pos.getZ() + tank.width());
    }
}
