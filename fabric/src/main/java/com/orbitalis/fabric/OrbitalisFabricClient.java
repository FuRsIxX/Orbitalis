package com.orbitalis.fabric;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.orbitalis.Orbitalis;
import com.orbitalis.client.SpaceEffects;
import com.orbitalis.client.port.event.ClientEvents;
import com.orbitalis.client.port.event.ClientHooks;
import com.orbitalis.phase2.survival.SpaceRules;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;

/** Orbitalis sous Fabric, côté client : le démarrage commun, puis ce que NeoForge offrirait par ses événements. */
public final class OrbitalisFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Orbitalis.initClient();
        // Le ciel de l'espace : orbites, Lune, Europe. L'apparence (pas de nuages, pas de ciel du jeu), puis le ciel du mod.
        DimensionRenderingRegistry.registerDimensionEffects(Orbitalis.id("deep_space"), new SpaceEffects());
        for (var world : List.of(SpaceRules.ORBIT, SpaceRules.MOON, SpaceRules.EUROPA, SpaceRules.PROXIMA_ORBIT, SpaceRules.TRAPPIST_ORBIT,
                SpaceRules.KEPLER_ORBIT, SpaceRules.TARANTULA_ORBIT)) {
            DimensionRenderingRegistry.registerSkyRenderer(world, context -> SpaceEffects.SKY.render(context.positionMatrix()));
        }
        // Les formes du mod dans le monde (fantômes de plans, débris), une fois les blocs translucides dessinés.
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            ClientEvents.levelRendered(new ClientHooks.LevelRenderEvent(new PoseStack(), buffers, context.camera(),
                    context.tickCounter().getGameTimeDeltaPartialTick(false)));
            buffers.endBatch();
        });
    }
}
