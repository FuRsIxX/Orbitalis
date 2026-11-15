package com.orbitalis.fabric.mixin.client;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.orbitalis.client.port.event.ClientEvents;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;

/**
 * Sous Fabric : les angles de la caméra (secousses, cabine qui tourne et roule) et sa distance à la troisième personne, revus
 * par le mod, comme le font les événements de NeoForge.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    @Final
    private Vector3f forwards;
    @Shadow
    @Final
    private Vector3f up;
    @Shadow
    @Final
    private Vector3f left;
    @Shadow
    @Final
    private Quaternionf rotation;

    @Shadow
    public abstract float getYRot();

    @Shadow
    public abstract float getXRot();

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void orbitalis$angles(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
        float yaw = getYRot();
        float pitch = getXRot();
        float[] angles = ClientEvents.cameraAngles((Camera) (Object) this, partialTick, yaw, pitch, 0.0F);
        if (angles[0] == yaw && angles[1] == pitch && angles[2] == 0.0F) {
            return;
        }
        setRotation(angles[0], angles[1]);
        if (angles[2] != 0.0F) {
            float degrees = (float) (Math.PI / 180.0);
            rotation.rotationYXZ((float) Math.PI - angles[0] * degrees, -angles[1] * degrees, -angles[2] * degrees);
            new Vector3f(0.0F, 0.0F, -1.0F).rotate(rotation, forwards);
            new Vector3f(0.0F, 1.0F, 0.0F).rotate(rotation, up);
            new Vector3f(-1.0F, 0.0F, 0.0F).rotate(rotation, left);
        }
    }

    @ModifyArg(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
    private float orbitalis$distance(float distance) {
        return ClientEvents.detachedCameraDistance(distance);
    }
}
