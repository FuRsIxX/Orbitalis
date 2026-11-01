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
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Dessine le contenu d'un réservoir modulable : un seul volume pour tout le réservoir, de la texture de l'eau de
 * Minecraft teintée à la couleur du fluide (posée bloc par bloc, comme dans un lac), qui monte et descend avec le
 * niveau. Un gaz, lui, occupe tout le réservoir, d'autant plus dense qu'il y en a.
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
        int surface = shade(state.color, 1.12F);
        int bright = shade(state.color, 1.0F);
        int deep = shade(state.color, 0.62F);
        int floor = shade(state.color, 0.5F);
        float size = hi - lo;
        float depth = top - lo;
        TextureAtlasSprite sprite = FluidSprites.still();
        collector.submitCustomGeometry(poseStack, RenderTypes.text(TextureAtlas.LOCATION_BLOCKS), (pose, buffer) -> {
            // Surface et fond, puis les quatre parois : chaque face porte la texture carreau par carreau, d'un bloc chacun.
            face(buffer, pose, light, sprite, surface, surface, depth, new float[] {lo, top, lo}, new float[] {1, 0, 0}, size, new float[] {0, 0, 1}, size);
            face(buffer, pose, light, sprite, floor, floor, depth, new float[] {lo, lo, lo}, new float[] {1, 0, 0}, size, new float[] {0, 0, 1}, size);
            face(buffer, pose, light, sprite, deep, bright, depth, new float[] {lo, lo, lo}, new float[] {1, 0, 0}, size, new float[] {0, 1, 0}, depth);
            face(buffer, pose, light, sprite, deep, bright, depth, new float[] {lo, lo, hi}, new float[] {1, 0, 0}, size, new float[] {0, 1, 0}, depth);
            face(buffer, pose, light, sprite, deep, bright, depth, new float[] {lo, lo, lo}, new float[] {0, 0, 1}, size, new float[] {0, 1, 0}, depth);
            face(buffer, pose, light, sprite, deep, bright, depth, new float[] {hi, lo, lo}, new float[] {0, 0, 1}, size, new float[] {0, 1, 0}, depth);
        });
    }

    /**
     * Une face, découpée en carreaux d'un bloc pour que la texture se répète sans s'étirer, et dessinée dans les deux
     * sens : on la voit de dehors comme de dedans. Le liquide s'assombrit vers le fond : la couleur va de {@code low}, en
     * bas du réservoir, à {@code high}, à la surface.
     */
    private static void face(VertexConsumer buffer, PoseStack.Pose pose, int light, TextureAtlasSprite sprite, int low, int high, float depth,
            float[] origin, float[] axisA, float lengthA, float[] axisB, float lengthB) {
        for (float a0 = 0; a0 < lengthA - 1.0E-4F; a0 = (float) Math.floor(a0) + 1) {
            float a1 = Math.min(lengthA, (float) Math.floor(a0) + 1);
            for (float b0 = 0; b0 < lengthB - 1.0E-4F; b0 = (float) Math.floor(b0) + 1) {
                float b1 = Math.min(lengthB, (float) Math.floor(b0) + 1);
                float[][] corners = {{a0, b0}, {a1, b0}, {a1, b1}, {a0, b1}};
                for (int pass = 0; pass < 2; pass++) {
                    for (int k = 0; k < 4; k++) {
                        float[] c = corners[pass == 0 ? k : 3 - k];
                        float px = origin[0] + axisA[0] * c[0] + axisB[0] * c[1];
                        float py = origin[1] + axisA[1] * c[0] + axisB[1] * c[1];
                        float pz = origin[2] + axisA[2] * c[0] + axisB[2] * c[1];
                        float u = sprite.getU(c[0] - (float) Math.floor(a0));
                        float v = sprite.getV(1 - (c[1] - (float) Math.floor(b0)));
                        float level = depth <= 0 ? 1 : Mth.clamp((py - origin[1]) / depth, 0, 1);
                        int color = axisB[1] == 0 ? low : lerp(low, high, level);
                        buffer.addVertex(pose, px, py, pz).setColor(color).setUv(u, v).setLight(light);
                    }
                }
            }
        }
    }

    private static int lerp(int from, int to, float t) {
        int a = (int) Mth.lerp(t, from >>> 24, to >>> 24);
        int r = (int) Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF);
        int g = (int) Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF);
        int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
        return a << 24 | r << 16 | g << 8 | b;
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
