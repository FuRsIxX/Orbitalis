package com.orbitalis.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.orbitalis.Orbitalis;
import com.orbitalis.client.SpaceEffects;
import com.orbitalis.client.port.event.ClientEvents;
import com.orbitalis.client.port.event.ClientHooks;
import com.orbitalis.client.render.TankParticles;
import com.orbitalis.core.registry.ModBlocks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Orbitalis sous NeoForge, côté client : ce que le code commun attend des événements de NeoForge. */
final class OrbitalisNeoForgeClient {
    private OrbitalisNeoForgeClient() {
    }

    static void init(IEventBus modBus) {
        // Le ciel de l'espace : orbites, Lune, Europe (« effects » de leur type de dimension).
        modBus.addListener(RegisterDimensionSpecialEffectsEvent.class, event -> event.register(Orbitalis.id("deep_space"), new SpaceEffects()));
        // Les éclats d'un réservoir qu'on casse ont la couleur de son contenu.
        modBus.addListener(RegisterClientExtensionsEvent.class, event -> event.registerBlock(new IClientBlockExtensions() {
            @Override
            public boolean addHitEffects(BlockState state, Level level, HitResult target, ParticleEngine engine) {
                return TankParticles.INSTANCE.addHitEffects(state, level, target, engine);
            }

            @Override
            public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos, ParticleEngine engine) {
                return TankParticles.INSTANCE.addDestroyEffects(state, level, pos, engine);
            }
        }, ModBlocks.FLUID_TANK.get()));

        NeoForge.EVENT_BUS.addListener(ViewportEvent.ComputeCameraAngles.class, event -> {
            float[] angles = ClientEvents.cameraAngles(event.getCamera(), event.getPartialTick(), event.getYaw(), event.getPitch(), event.getRoll());
            event.setYaw(angles[0]);
            event.setPitch(angles[1]);
            event.setRoll(angles[2]);
        });
        NeoForge.EVENT_BUS.addListener(ViewportEvent.ComputeFov.class,
                event -> event.setFOV(ClientEvents.fov(event.getCamera(), event.getPartialTick(), event.getFOV())));
        NeoForge.EVENT_BUS.addListener(CalculateDetachedCameraDistanceEvent.class,
                event -> event.setDistance(ClientEvents.detachedCameraDistance(event.getDistance())));
        NeoForge.EVENT_BUS.addListener(RenderGuiLayerEvent.Pre.class, event -> {
            if (!ClientEvents.guiLayer(event.getName())) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener(RenderPlayerEvent.Pre.class, event -> {
            if (!ClientEvents.renderPlayer(event.getEntity())) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, event -> {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                return;
            }
            // La pile part de la caméra ; la rotation de la caméra est dans les transformations du jeu, comme pour les entités.
            MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            ClientEvents.levelRendered(new ClientHooks.LevelRenderEvent(new PoseStack(), buffers, event.getCamera(),
                    event.getPartialTick().getGameTimeDeltaPartialTick(false)));
            buffers.endBatch();
        });
    }
}
