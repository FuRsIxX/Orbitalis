package com.orbitalis.client;

import com.orbitalis.Orbitalis;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.phase1.spaceport.Workstation;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Les cases invisibles d'un grand meuble n'ont pas de modèle où dessiner les fissures : quand le joueur en casse une,
 * les fissures s'affichent sur le meuble lui-même, pour qu'il voie que le coup porte.
 */
@EventBusSubscriber(modid = Orbitalis.MODID, value = Dist.CLIENT)
public final class WorkstationCracks {
    /** Identifiant des fissures recopiées, distinct de celui de tout joueur. */
    private static final int BREAKER_ID = Integer.MIN_VALUE + 0x0B17;
    private static BlockPos shown;

    private WorkstationCracks() {
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.gameMode == null) {
            shown = null;
            return;
        }
        BlockPos host = null;
        int stage = -1;
        if (minecraft.gameMode.isDestroying() && minecraft.hitResult instanceof BlockHitResult hit
                && minecraft.level.getBlockState(hit.getBlockPos()).is(ModBlocks.WORKSTATION_PART.get())) {
            host = Workstation.findHost(minecraft.level, hit.getBlockPos());
            stage = minecraft.gameMode.getDestroyStage();
        }
        if (shown != null && !shown.equals(host)) {
            minecraft.level.destroyBlockProgress(BREAKER_ID, shown, -1);
        }
        shown = host;
        if (host != null) {
            minecraft.level.destroyBlockProgress(BREAKER_ID, host, stage);
        }
    }
}
