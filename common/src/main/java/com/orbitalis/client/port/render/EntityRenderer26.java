package com.orbitalis.client.port.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Le rendu d'une entité à la façon de Minecraft 26 : on en extrait un état, puis on le soumet ; posé sur le rendu de 1.21.1,
 * qui dessine tout d'un coup.
 */
public abstract class EntityRenderer26<T extends Entity, S extends EntityRenderState> extends EntityRenderer<T> {
    protected EntityRenderer26(EntityRendererProvider.Context context) {
        super(context);
    }

    public abstract S createRenderState();

    /** L'état de l'entité à dessiner : sa place et son âge à cette image. */
    public void extractRenderState(T entity, S state, float partialTicks) {
        state.x = Mth.lerp(partialTicks, entity.xo, entity.getX());
        state.y = Mth.lerp(partialTicks, entity.yo, entity.getY());
        state.z = Mth.lerp(partialTicks, entity.zo, entity.getZ());
        state.ageInTicks = entity.tickCount + partialTicks;
        state.partialTick = partialTicks;
        state.yRot = entity.getViewYRot(partialTicks);
        state.distanceToCameraSq = entityRenderDispatcher.distanceToSqr(entity);
        state.id = entity.getId();
    }

    /** Dessine l'état (la pile de transformations part du pied de l'entité). */
    public void submit(S state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
    }

    @Override
    public final void render(T entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        S state = createRenderState();
        state.lightCoords = packedLight;
        extractRenderState(entity, state, partialTick);
        submit(state, poseStack, new SubmitNodeCollector(buffers), CameraRenderState.of(entityRenderDispatcher.camera));
        // Le nom au-dessus de l'entité, s'il y a lieu.
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    /** La boîte qui doit être à l'écran pour que l'entité soit dessinée. */
    protected net.minecraft.world.phys.AABB getBoundingBoxForCulling(T entity) {
        return entity.getBoundingBoxForCulling();
    }

    @Override
    public boolean shouldRender(T entity, net.minecraft.client.renderer.culling.Frustum frustum, double camX, double camY, double camZ) {
        if (!entity.shouldRender(camX, camY, camZ)) {
            return false;
        }
        if (entity.noCulling) {
            return true;
        }
        net.minecraft.world.phys.AABB box = getBoundingBoxForCulling(entity).inflate(0.5);
        if (box.hasNaN() || box.getSize() == 0.0) {
            box = new net.minecraft.world.phys.AABB(entity.getX() - 2.0, entity.getY() - 2.0, entity.getZ() - 2.0,
                    entity.getX() + 2.0, entity.getY() + 2.0, entity.getZ() + 2.0);
        }
        return frustum.isVisible(box);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
