package com.orbitalis.client.port;

import org.joml.Matrix3x2fc;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/**
 * La pile de transformations 2D des interfaces de Minecraft 26, posée sur la {@link PoseStack} de 1.21.1 (dont on ne touche
 * que le plan de l'écran).
 */
public final class Matrix3x2fStack {
    private final PoseStack stack;

    public Matrix3x2fStack(PoseStack stack) {
        this.stack = stack;
    }

    public PoseStack vanilla() {
        return stack;
    }

    public Matrix3x2fStack pushMatrix() {
        stack.pushPose();
        return this;
    }

    public Matrix3x2fStack popMatrix() {
        stack.popPose();
        return this;
    }

    public Matrix3x2fStack translate(float x, float y) {
        stack.translate(x, y, 0.0F);
        return this;
    }

    public Matrix3x2fStack scale(float x, float y) {
        stack.scale(x, y, 1.0F);
        return this;
    }

    public Matrix3x2fStack scale(float factor) {
        return scale(factor, factor);
    }

    /** Une rotation dans le plan de l'écran, en radians. */
    public Matrix3x2fStack rotate(float radians) {
        stack.mulPose(Axis.ZP.rotation(radians));
        return this;
    }

    /** Une transformation affine du plan, quelconque. */
    public Matrix3x2fStack mul(Matrix3x2fc m) {
        Matrix4f affine = new Matrix4f(
                m.m00(), m.m01(), 0.0F, 0.0F,
                m.m10(), m.m11(), 0.0F, 0.0F,
                0.0F, 0.0F, 1.0F, 0.0F,
                m.m20(), m.m21(), 0.0F, 1.0F);
        stack.last().pose().mul(affine);
        return this;
    }
}
