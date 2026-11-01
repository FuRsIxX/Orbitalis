package com.orbitalis.client.render;

import com.orbitalis.core.registry.ModFluids;
import com.orbitalis.phase1.fuel.FluidTankBlockEntity;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Les éclats d'un réservoir modulable qu'on casse : s'il contient un fluide, ils en ont la texture (celle de l'eau,
 * teintée à sa couleur, comme dans le réservoir), pendant qu'on le frappe comme quand il cède. Vide, il garde les éclats
 * de ses parois. Rien d'autre ne change : le réservoir garde son contenu comme avant.
 */
public final class TankParticles implements IClientBlockExtensions {
    public static final TankParticles INSTANCE = new TankParticles();

    private TankParticles() {
    }

    /** Le fluide du réservoir dont ce bloc fait partie ; s'il vient de disparaître, celui d'un bloc voisin du réservoir. */
    private static Fluid fluidAt(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof FluidTankBlockEntity tank) {
            FluidStack content = tank.fluid();
            if (!content.isEmpty()) {
                return content.getFluid();
            }
        }
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(pos.relative(direction)) instanceof FluidTankBlockEntity tank) {
                FluidStack content = tank.fluid();
                if (!content.isEmpty()) {
                    return content.getFluid();
                }
            }
        }
        return null;
    }

    @Override
    public boolean addHitEffects(BlockState state, Level level, HitResult target, ParticleEngine engine) {
        if (!(target instanceof BlockHitResult hit) || !(level instanceof ClientLevel client)) {
            return false;
        }
        BlockPos pos = hit.getBlockPos();
        Fluid fluid = fluidAt(level, pos);
        if (fluid == null) {
            return false;
        }
        AABB shape = state.getShape(level, pos).bounds();
        Direction side = hit.getDirection();
        double x = pos.getX() + level.getRandom().nextDouble() * (shape.maxX - shape.minX - 0.2) + 0.1 + shape.minX;
        double y = pos.getY() + level.getRandom().nextDouble() * (shape.maxY - shape.minY - 0.2) + 0.1 + shape.minY;
        double z = pos.getZ() + level.getRandom().nextDouble() * (shape.maxZ - shape.minZ - 0.2) + 0.1 + shape.minZ;
        x = side == Direction.WEST ? pos.getX() + shape.minX - 0.1 : side == Direction.EAST ? pos.getX() + shape.maxX + 0.1 : x;
        y = side == Direction.DOWN ? pos.getY() + shape.minY - 0.1 : side == Direction.UP ? pos.getY() + shape.maxY + 0.1 : y;
        z = side == Direction.NORTH ? pos.getZ() + shape.minZ - 0.1 : side == Direction.SOUTH ? pos.getZ() + shape.maxZ + 0.1 : z;
        engine.add(new Drop(client, x, y, z, 0, 0, 0, state, pos, fluid).setPower(0.2F).scale(0.6F));
        return true;
    }

    @Override
    public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos, ParticleEngine engine) {
        if (!(level instanceof ClientLevel client)) {
            return false;
        }
        Fluid fluid = fluidAt(level, pos);
        if (fluid == null) {
            return false;
        }
        // Comme les éclats d'un bloc ordinaire : quatre sur chaque arête, qui partent du centre.
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                for (int k = 0; k < 4; k++) {
                    double rx = (i + 0.5) / 4.0;
                    double ry = (j + 0.5) / 4.0;
                    double rz = (k + 0.5) / 4.0;
                    engine.add(new Drop(client, pos.getX() + rx, pos.getY() + ry, pos.getZ() + rz, rx - 0.5, ry - 0.5, rz - 0.5, state, pos, fluid));
                }
            }
        }
        return true;
    }

    /** Un éclat du réservoir, de la texture et de la couleur de son fluide. */
    private static final class Drop extends TerrainParticle {
        Drop(ClientLevel level, double x, double y, double z, double xa, double ya, double za, BlockState state, BlockPos pos, Fluid fluid) {
            super(level, x, y, z, xa, ya, za, state, pos);
            TextureAtlasSprite water = FluidSprites.still();
            setSprite(water);
            int color = ModFluids.color(fluid);
            rCol = 0.9F * (color >> 16 & 0xFF) / 255.0F;
            gCol = 0.9F * (color >> 8 & 0xFF) / 255.0F;
            bCol = 0.9F * (color & 0xFF) / 255.0F;
        }

        @Override
        public SingleQuadParticle.Layer getLayer() {
            return SingleQuadParticle.Layer.bySprite(sprite);
        }
    }
}
